#!/usr/bin/env python3
"""
diff_usb_captures.py — Differential USB Capture Analyzer for VCDS / B03-V2 reverse engineering.

Compares multiple parsed USB captures (e.g. Capture A: Options/Test, Capture B: Engine Connect,
Capture C: Group 011, Capture D: DTC Read) to identify:
1. Invariant FTDI & MCU setup sequences
2. Phase-specific unique command opcodes
3. Bulk OUT -> Bulk IN request/response transactions and latencies
4. Known protocol signatures (KWP1281, KWP2000, TP2.0, CAN frames)

Outputs markdown analysis reports and JSON diff summaries.
"""

import argparse
import json
import os
import sys
from typing import Any, Dict, List, Optional, Set, Tuple

# Known VAG Protocol Signatures for correlation
PROTOCOL_SIGNATURES = {
    "KWP1281_SYNC": bytes([0x55]),
    "KWP1281_KEY_BYTES": bytes([0x01, 0x8A]),
    "KWP2000_START_COMM": bytes([0x81, 0x01, 0xF1, 0x81, 0xF4]),
    "KWP2000_START_COMM_ALT": bytes([0x81, 0x11, 0xF1, 0x81, 0x04]),
    "KWP2000_SID_START_DIAG": 0x10,
    "KWP2000_SID_ECU_ID": 0x1A,
    "KWP2000_SID_READ_DATA_LOCAL": 0x21,
    "KWP2000_SID_READ_DATA_COMMON": 0x22,
    "KWP2000_SID_READ_DTC": 0x18,
    "KWP2000_SID_CLEAR_DTC": 0x14,
    "KWP2000_POS_ACK": 0x50,
    "KWP2000_NEG_ACK": 0x7F,
    "TP20_SETUP_REQ": 0xC0,
    "TP20_SETUP_RESP": 0xD0,
    "TP20_ACK": 0xB0,
}


def load_capture_json(path: str) -> Dict[str, Any]:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def extract_bulk_out_commands(transfers: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    cmds = []
    for t in transfers:
        if t.get("transfer_type") == "BULK" and t.get("direction") == "OUT":
            payload_hex = t.get("uart_payload_hex", "")
            if payload_hex:
                cmds.append({
                    "index": t.get("index"),
                    "relative_ms": t.get("relative_ms"),
                    "payload_hex": payload_hex,
                    "payload_len": len(payload_hex) // 2,
                    "ascii": t.get("ascii", ""),
                })
    return cmds


def extract_ftdi_setup_sequence(transfers: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    setups = []
    for t in transfers:
        if t.get("transfer_type") == "CONTROL" and "setup_packet" in t:
            sp = t["setup_packet"]
            setups.append({
                "index": t.get("index"),
                "relative_ms": t.get("relative_ms"),
                "command": sp.get("ftdi_command"),
                "detail": sp.get("detail"),
                "bRequest": sp.get("bRequest"),
                "wValue": sp.get("wValue"),
                "wIndex": sp.get("wIndex"),
            })
    return setups


def pair_transactions(transfers: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    pairs = []
    pending_req: Optional[Dict[str, Any]] = None

    for t in transfers:
        if t.get("transfer_type") != "BULK":
            continue

        direction = t.get("direction")
        if direction == "OUT":
            pending_req = t
        elif direction == "IN" and not t.get("is_heartbeat", False) and pending_req is not None:
            req_hex = pending_req.get("uart_payload_hex", "")
            resp_hex = t.get("uart_payload_hex", "")
            delta_ms = round(t.get("relative_ms", 0) - pending_req.get("relative_ms", 0), 3)
            pairs.append({
                "req_index": pending_req.get("index"),
                "resp_index": t.get("index"),
                "req_ms": pending_req.get("relative_ms"),
                "resp_ms": t.get("relative_ms"),
                "latency_ms": delta_ms,
                "request_hex": req_hex,
                "response_hex": resp_hex,
                "request_len": len(req_hex) // 2,
                "response_len": len(resp_hex) // 2,
            })
            pending_req = None

    return pairs


def correlate_payload(hex_str: str) -> List[str]:
    matches = []
    try:
        data = bytes.fromhex(hex_str)
    except ValueError:
        return matches

    if len(data) == 0:
        return matches

    # Check KWP2000 Start Communication
    if PROTOCOL_SIGNATURES["KWP2000_START_COMM"] in data:
        matches.append("KWP2000 StartCommunication (0x81 0x01 0xF1 0x81 0xF4)")
    if PROTOCOL_SIGNATURES["KWP2000_START_COMM_ALT"] in data:
        matches.append("KWP2000 StartCommunication Alt (0x81 0x11 0xF1 0x81 0x04)")

    # Check KWP service IDs in first 3 bytes
    for i in range(min(len(data), 4)):
        b = data[i]
        if b == PROTOCOL_SIGNATURES["KWP2000_SID_ECU_ID"]:
            matches.append(f"KWP2000 ReadECUIdentification (0x1A) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_SID_READ_DATA_LOCAL"]:
            matches.append(f"KWP2000 ReadDataByLocalIdentifier (0x21) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_SID_READ_DATA_COMMON"]:
            matches.append(f"KWP2000 ReadDataByCommonIdentifier (0x22) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_SID_READ_DTC"]:
            matches.append(f"KWP2000 ReadDiagnosticTroubleCodes (0x18) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_SID_CLEAR_DTC"]:
            matches.append(f"KWP2000 ClearDiagnosticInformation (0x14) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_POS_ACK"]:
            matches.append(f"KWP2000 PositiveResponse (0x50) at byte {i}")
        elif b == PROTOCOL_SIGNATURES["KWP2000_NEG_ACK"]:
            matches.append(f"KWP2000 NegativeResponse (0x7F) at byte {i}")

    # Check TP2.0 setup
    if len(data) >= 7 and data[0] == PROTOCOL_SIGNATURES["TP20_SETUP_REQ"]:
        matches.append(f"TP2.0 Channel Setup Request (Dest=0x{data[1]:02X})")
    elif len(data) >= 7 and data[0] == PROTOCOL_SIGNATURES["TP20_SETUP_RESP"]:
        matches.append("TP2.0 Channel Setup Positive Response")

    return matches


def compare_captures(captures: Dict[str, Dict[str, Any]]) -> Dict[str, Any]:
    diff_report: Dict[str, Any] = {
        "captures_analyzed": list(captures.keys()),
        "ftdi_setup_comparison": {},
        "invariant_commands": [],
        "unique_commands": {},
        "correlated_operations": {},
        "transaction_pairs": {},
    }

    # 1. Compare FTDI setups
    for name, cap in captures.items():
        setups = extract_ftdi_setup_sequence(cap.get("transfers", []))
        diff_report["ftdi_setup_comparison"][name] = setups

    # 2. Extract bulk commands per capture
    all_cmds_by_cap: Dict[str, Set[str]] = {}
    for name, cap in captures.items():
        cmds = extract_bulk_out_commands(cap.get("transfers", []))
        cmd_hexes = {c["payload_hex"] for c in cmds}
        all_cmds_by_cap[name] = cmd_hexes

    # Invariant commands present in EVERY capture
    if all_cmds_by_cap:
        invariant = set.intersection(*all_cmds_by_cap.values())
        diff_report["invariant_commands"] = sorted(list(invariant))
    else:
        diff_report["invariant_commands"] = []

    # Unique commands present only in specific captures
    for name, hexes in all_cmds_by_cap.items():
        other_hexes = set().union(*(h for n, h in all_cmds_by_cap.items() if n != name))
        unique = hexes - other_hexes
        diff_report["unique_commands"][name] = sorted(list(unique))

    # 3. Transaction pairing & protocol correlations
    for name, cap in captures.items():
        pairs = pair_transactions(cap.get("transfers", []))
        diff_report["transaction_pairs"][name] = pairs

        correlations = []
        for p in pairs:
            req_matches = correlate_payload(p["request_hex"])
            resp_matches = correlate_payload(p["response_hex"])
            if req_matches or resp_matches:
                correlations.append({
                    "request_hex": p["request_hex"],
                    "request_matches": req_matches,
                    "response_hex": p["response_hex"],
                    "response_matches": resp_matches,
                    "latency_ms": p["latency_ms"],
                })
        diff_report["correlated_operations"][name] = correlations

    return diff_report


def generate_markdown_report(diff_report: Dict[str, Any]) -> str:
    md = []
    md.append("# Differential USB Capture Analysis Report")
    md.append("")
    md.append("> [!NOTE]")
    md.append("> **EVIDENCE STATUS NOTICE**: This report reflects comparative analysis of input capture transcripts.")
    md.append("> If generated against synthetic/mock fixtures, all payloads represent unit-test vectors only and MUST NOT")
    md.append("> be interpreted as confirmed Ross-Tech or B03-V2 protocol evidence.")
    md.append("")
    md.append(f"**Analyzed Captures**: {', '.join(diff_report['captures_analyzed'])}")
    md.append("")

    # Section 1: FTDI Setup Comparison
    md.append("## 1. FTDI Link Layer Setup Sequence")
    md.append("")
    md.append("| Capture | Step | Command | Details | Value / Index |")
    md.append("|---|---|---|---|---|")
    for cap_name, setups in diff_report["ftdi_setup_comparison"].items():
        if not setups:
            md.append(f"| {cap_name} | - | *None* | - | - |")
        for i, s in enumerate(setups):
            md.append(f"| {cap_name} | {i+1} | `{s['command']}` | {s.get('detail', '')} | `{s.get('wValue')}` / `{s.get('wIndex')}` |")
    md.append("")

    # Section 2: Invariant Commands
    md.append("## 2. Invariant Command Sequences (Common to ALL Captures)")
    md.append("Commands observed across all captures represent adapter handshake, reset, or protocol initialization:")
    md.append("")
    if diff_report["invariant_commands"]:
        md.append("| Hex Payload | Length | Probable Role |")
        md.append("|---|---|---|")
        for inv in diff_report["invariant_commands"]:
            md.append(f"| `{inv}` | {len(inv)//2} B | Handshake / Reset Invariant |")
    else:
        md.append("*No universal invariant commands found across compared captures.*")
    md.append("")

    # Section 3: Phase-Specific Unique Commands
    md.append("## 3. Phase-Specific Unique Commands (Differential Analysis)")
    md.append("")
    for cap_name, uniques in diff_report["unique_commands"].items():
        md.append(f"### Capture: `{cap_name}`")
        if uniques:
            md.append("| Unique Hex Command | Length | Protocol Correlation |")
            md.append("|---|---|---|")
            for u in uniques:
                corr = correlate_payload(u)
                corr_str = "; ".join(corr) if corr else "Candidate Coprocessor Command"
                md.append(f"| `{u}` | {len(u)//2} B | {corr_str} |")
        else:
            md.append("*No unique commands detected.*")
        md.append("")

    # Section 4: Protocol Signatures & Transaction Pairs
    md.append("## 4. Correlated Protocol Transactions (KWP / TP2 / CAN)")
    md.append("")
    has_corr = False
    for cap_name, corrs in diff_report["correlated_operations"].items():
        if corrs:
            has_corr = True
            md.append(f"### `{cap_name}`")
            md.append("| Request Hex | Request Protocol | Response Hex | Response Protocol | Latency |")
            md.append("|---|---|---|---|---|")
            for c in corrs:
                req_proto = "; ".join(c["request_matches"]) or "*Custom Framing*"
                resp_proto = "; ".join(c["response_matches"]) or "*Custom Framing*"
                md.append(f"| `{c['request_hex']}` | {req_proto} | `{c['response_hex']}` | {resp_proto} | {c['latency_ms']} ms |")
            md.append("")

    if not has_corr:
        md.append("*No recognized standard KWP/TP2 signatures found in current captures.*")
        md.append("")

    return "\n".join(md)


def main():
    parser = argparse.ArgumentParser(description="Differential analysis of parsed USB capture JSON files.")
    parser.add_argument("--capture-a", required=True, help="Path to parsed Capture A JSON")
    parser.add_argument("--capture-b", help="Path to parsed Capture B JSON")
    parser.add_argument("--capture-c", help="Path to parsed Capture C JSON")
    parser.add_argument("--capture-d", help="Path to parsed Capture D JSON")
    parser.add_argument("--capture-e", help="Path to parsed Capture E JSON")
    parser.add_argument("--output", help="Path to output markdown report (e.g. capture_diff_report.md)")
    parser.add_argument("--json-out", help="Path to output JSON summary")
    args = parser.parse_args()

    cap_paths = {
        "Capture_A": args.capture_a,
        "Capture_B": args.capture_b,
        "Capture_C": args.capture_c,
        "Capture_D": args.capture_d,
        "Capture_E": args.capture_e,
    }
    loaded = {}
    for name, path in cap_paths.items():
        if path and os.path.isfile(path):
            loaded[name] = load_capture_json(path)

    if not loaded:
        print("Error: No valid capture JSON files provided.", file=sys.stderr)
        sys.exit(1)

    diff_report = compare_captures(loaded)
    md_content = generate_markdown_report(diff_report)

    if args.output:
        with open(args.output, "w", encoding="utf-8") as f:
            f.write(md_content)
        print(f"Differential Markdown Report : {args.output}")
    else:
        print(md_content)

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(diff_report, f, indent=2)
        print(f"Differential JSON Summary    : {args.json_out}")


if __name__ == "__main__":
    main()
