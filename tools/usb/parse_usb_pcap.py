#!/usr/bin/env python3
"""
parse_usb_pcap.py — Pure-Python USBPcap capture parser for VCDS / B03-V2 reverse engineering.

Extracts USB Request Blocks (URBs), decodes FTDI control requests, extracts Bulk IN/OUT
payloads (stripping 2-byte FTDI modem status headers), and produces structured JSON / CSV transcripts.

Zero external dependencies (uses standard library struct, json, csv, argparse).
"""

import argparse
import csv
import json
import os
import struct
import sys
from typing import Any, Dict, List, Optional, Tuple

LINKTYPE_USB_LINUX = 189
LINKTYPE_USBPCAP = 249

FTDI_REQUESTS = {
    0x00: "FTDI_SIO_RESET",
    0x01: "FTDI_SIO_MODEM_CTRL",
    0x02: "FTDI_SIO_SET_FLOW_CTRL",
    0x03: "FTDI_SIO_SET_BAUDRATE",
    0x04: "FTDI_SIO_SET_DATA",
    0x05: "FTDI_SIO_POLL_MODEM_STATUS",
    0x06: "FTDI_SIO_SET_EVENT_CHAR",
    0x07: "FTDI_SIO_SET_ERROR_CHAR",
    0x09: "FTDI_SIO_SET_LATENCY_TIMER",
    0x0A: "FTDI_SIO_GET_LATENCY_TIMER",
    0x0B: "FTDI_SIO_SET_BITMODE",
    0x0C: "FTDI_SIO_READ_PINS",
    0x90: "FTDI_SIO_READ_EEPROM",
    0x91: "FTDI_SIO_WRITE_EEPROM",
    0x92: "FTDI_SIO_ERASE_EEPROM",
}

TRANSFER_TYPES = {
    0: "ISOCHRONOUS",
    1: "INTERRUPT",
    2: "CONTROL",
    3: "BULK",
}

CONTROL_STAGES = {
    0: "SETUP",
    1: "DATA",
    2: "STATUS",
    3: "COMPLETE",
}


def decode_ftdi_baudrate(w_value: int, w_index: int) -> Optional[int]:
    """Decode FTDI clock divisor from wValue and wIndex to approximate baud rate."""
    div = w_value & 0x3FFF
    sub = (w_value >> 14) & 0x03
    if (w_index & 0x01) and sub == 0:
        sub = 4  # special prescaler in some FTDI chips

    sub_fract = {0: 0.0, 1: 0.5, 2: 0.25, 3: 0.125, 4: 0.375, 5: 0.625, 6: 0.75, 7: 0.875}.get(sub, 0.0)
    total_div = div + sub_fract
    if total_div == 0:
        return 3000000
    if total_div == 1:
        return 2000000
    baud = int(3000000 / total_div)
    return baud


def decode_ftdi_modem_ctrl(w_value: int) -> str:
    parts = []
    # DTR: bit 8 = enable, bit 0 = state
    if w_value & 0x0100:
        dtr_state = bool(w_value & 0x0001)
        parts.append(f"DTR={'HIGH' if dtr_state else 'LOW'}")
    # RTS: bit 9 = enable, bit 1 = state
    if w_value & 0x0200:
        rts_state = bool(w_value & 0x0002)
        parts.append(f"RTS={'HIGH' if rts_state else 'LOW'}")
    return ", ".join(parts) if parts else f"0x{w_value:04X}"


def decode_ftdi_reset(w_value: int) -> str:
    resets = {
        0: "SIO_RESET (Purge buffers & reset UART)",
        1: "PURGE_RX",
        2: "PURGE_TX",
    }
    return resets.get(w_value, f"UNKNOWN_RESET(0x{w_value:04X})")


def parse_pcap(pcap_path: str, is_ftdi: bool = True) -> Dict[str, Any]:
    with open(pcap_path, "rb") as f:
        global_header = f.read(24)
        if len(global_header) < 24:
            raise ValueError(f"File {pcap_path} is too small to be a valid PCAP file.")

        magic = global_header[:4]
        if magic in (b"\xa1\xb2\xc3\xd4", b"\xa1\xb2\x3c\x4d"):
            endian = ">"
            is_nano = magic == b"\xa1\xb2\x3c\x4d"
        elif magic in (b"\xd4\xc3\xb2\xa1", b"\x4d\x3c\xb2\xa1"):
            endian = "<"
            is_nano = magic == b"\x4d\x3c\xb2\xa1"
        else:
            raise ValueError(f"Unknown PCAP magic number: {magic.hex()}")

        ver_major, ver_minor, thiszone, sigfigs, snaplen, linktype = struct.unpack(
            f"{endian}HHIIII", global_header[4:]
        )

        records: List[Dict[str, Any]] = []
        packet_idx = 0
        first_ts: Optional[float] = None

        while True:
            pkt_hdr_data = f.read(16)
            if len(pkt_hdr_data) < 16:
                break
            packet_idx += 1
            ts_sec, ts_usec, incl_len, orig_len = struct.unpack(f"{endian}IIII", pkt_hdr_data)
            ts = ts_sec + (ts_usec / 1e9 if is_nano else ts_usec / 1e6)
            if first_ts is None:
                first_ts = ts
            rel_ts_ms = round((ts - first_ts) * 1000.0, 3)

            pkt_body = f.read(incl_len)
            if len(pkt_body) < incl_len:
                break

            record: Dict[str, Any] = {
                "index": packet_idx,
                "timestamp_sec": ts,
                "relative_ms": rel_ts_ms,
                "linktype": linktype,
            }

            if linktype == LINKTYPE_USBPCAP:
                if len(pkt_body) < 27:
                    continue
                (
                    header_len,
                    irp_id,
                    status,
                    function,
                    info,
                    bus,
                    device,
                    endpoint,
                    transfer,
                    data_length,
                ) = struct.unpack(f"<HQIHBHHBBI", pkt_body[:27])

                is_complete = bool(info & 0x01)
                direction = "IN" if (endpoint & 0x80) else "OUT"
                ep_num = endpoint & 0x7F
                transfer_name = TRANSFER_TYPES.get(transfer, f"UNKNOWN_{transfer}")

                record.update(
                    {
                        "irp_id": f"0x{irp_id:016X}",
                        "status": f"0x{status:08X}",
                        "function": function,
                        "is_complete": is_complete,
                        "bus": bus,
                        "device": device,
                        "endpoint": ep_num,
                        "endpoint_raw": f"0x{endpoint:02X}",
                        "direction": direction,
                        "transfer_type": transfer_name,
                        "data_length": data_length,
                    }
                )

                # Extract payload
                raw_payload = b""
                if header_len < len(pkt_body):
                    raw_payload = pkt_body[header_len : header_len + data_length]

                # Control transfer parsing
                if transfer == 2:  # CONTROL
                    stage = pkt_body[27] if len(pkt_body) > 27 else None
                    record["control_stage"] = CONTROL_STAGES.get(stage, str(stage))

                    # Parse 8-byte setup packet if present (e.g. stage == 0 or header_len >= 36)
                    setup_bytes = b""
                    if stage == 0 and len(pkt_body) >= 36:
                        setup_bytes = pkt_body[28:36]
                    elif header_len >= 35:
                        setup_bytes = pkt_body[header_len - 8 : header_len]

                    if len(setup_bytes) == 8:
                        bmRequestType, bRequest, wValue, wIndex, wLength = struct.unpack(
                            "<BBHHH", setup_bytes
                        )
                        ftdi_cmd_name = FTDI_REQUESTS.get(bRequest, f"0x{bRequest:02X}")
                        cmd_detail = ""
                        if bRequest == 0x00:
                            cmd_detail = decode_ftdi_reset(wValue)
                        elif bRequest == 0x01:
                            cmd_detail = decode_ftdi_modem_ctrl(wValue)
                        elif bRequest == 0x03:
                            baud = decode_ftdi_baudrate(wValue, wIndex)
                            cmd_detail = f"Div=0x{wValue:04X} (~{baud} baud)"
                        elif bRequest == 0x09:
                            cmd_detail = f"{wValue} ms"

                        record["setup_packet"] = {
                            "bmRequestType": f"0x{bmRequestType:02X}",
                            "bRequest": f"0x{bRequest:02X}",
                            "wValue": f"0x{wValue:04X}",
                            "wIndex": f"0x{wIndex:04X}",
                            "wLength": wLength,
                            "ftdi_command": ftdi_cmd_name,
                            "detail": cmd_detail,
                        }
                        record["summary"] = f"CTRL [{ftdi_cmd_name}] {cmd_detail}".strip()
                    else:
                        record["summary"] = f"CTRL {record['control_stage']} ({data_length} B)"

                elif transfer == 3:  # BULK
                    if direction == "IN" and is_complete:
                        # For FTDI Bulk IN, every 64-byte USB packet starts with 2 modem/line status bytes
                        if is_ftdi and len(raw_payload) >= 2:
                            status_bytes = raw_payload[:2]
                            uart_payload = bytearray()
                            for block_idx in range(0, len(raw_payload), 64):
                                block = raw_payload[block_idx:block_idx + 64]
                                if len(block) >= 2:
                                    uart_payload.extend(block[2:])
                            uart_payload = bytes(uart_payload)
                            is_heartbeat = len(uart_payload) == 0
                            record["ftdi_status_hex"] = status_bytes.hex()
                            record["is_heartbeat"] = is_heartbeat
                            record["uart_payload_hex"] = uart_payload.hex()
                            record["uart_payload_len"] = len(uart_payload)
                            record["ascii"] = "".join(
                                chr(b) if 32 <= b <= 126 else "." for b in uart_payload
                            )
                            record["summary"] = (
                                f"BULK IN HEARTBEAT (Status=0x{status_bytes.hex()})"
                                if is_heartbeat
                                else f"BULK IN ({len(uart_payload)} B): {uart_payload.hex()}"
                            )
                        else:
                            record["uart_payload_hex"] = raw_payload.hex()
                            record["uart_payload_len"] = len(raw_payload)
                            record["ascii"] = "".join(
                                chr(b) if 32 <= b <= 126 else "." for b in raw_payload
                            )
                            record["summary"] = f"BULK IN ({len(raw_payload)} B): {raw_payload.hex()}"
                    else:
                        # BULK OUT
                        record["uart_payload_hex"] = raw_payload.hex()
                        record["uart_payload_len"] = len(raw_payload)
                        record["ascii"] = "".join(
                            chr(b) if 32 <= b <= 126 else "." for b in raw_payload
                        )
                        record["summary"] = f"BULK OUT ({len(raw_payload)} B): {raw_payload.hex()}"

                else:
                    record["summary"] = f"{transfer_name} {direction} ({data_length} B)"

                record["raw_payload_hex"] = raw_payload.hex()
            records.append(record)

        # Calculate statistics
        total_packets = len(records)
        bulk_out_records = [r for r in records if r.get("transfer_type") == "BULK" and r.get("direction") == "OUT"]
        bulk_in_records = [
            r for r in records
            if r.get("transfer_type") == "BULK" and r.get("direction") == "IN" and not r.get("is_heartbeat", False)
        ]
        heartbeat_count = sum(1 for r in records if r.get("is_heartbeat", False))
        control_records = [r for r in records if r.get("transfer_type") == "CONTROL"]

        stats = {
            "pcap_file": os.path.basename(pcap_path),
            "linktype": linktype,
            "total_packets": total_packets,
            "control_transfers": len(control_records),
            "bulk_out_transfers": len(bulk_out_records),
            "bulk_out_total_bytes": sum(r.get("uart_payload_len", 0) for r in bulk_out_records),
            "bulk_in_transfers_with_data": len(bulk_in_records),
            "bulk_in_total_bytes": sum(r.get("uart_payload_len", 0) for r in bulk_in_records),
            "ftdi_empty_heartbeats": heartbeat_count,
        }

        return {
            "metadata": stats,
            "transfers": records,
        }


def export_csv(transfers: List[Dict[str, Any]], csv_path: str, include_heartbeats: bool = False):
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "index",
            "relative_ms",
            "transfer_type",
            "direction",
            "endpoint",
            "is_complete",
            "summary",
            "payload_len",
            "payload_hex",
            "ascii",
        ])
        for r in transfers:
            if not include_heartbeats and r.get("is_heartbeat", False):
                continue
            writer.writerow([
                r.get("index"),
                r.get("relative_ms"),
                r.get("transfer_type"),
                r.get("direction"),
                r.get("endpoint"),
                r.get("is_complete"),
                r.get("summary"),
                r.get("uart_payload_len", r.get("data_length", 0)),
                r.get("uart_payload_hex", r.get("raw_payload_hex", "")),
                r.get("ascii", ""),
            ])


def main():
    parser = argparse.ArgumentParser(description="Parse USBPcap file into structured JSON and CSV.")
    parser.add_argument("pcap_path", help="Path to input .pcap file")
    parser.add_argument("--export-csv", action="store_true", help="Also export a chronological CSV file")
    parser.add_argument("--include-heartbeats", action="store_true", help="Include empty FTDI poll heartbeats in output")
    parser.add_argument("--json-out", help="Optional output JSON path (defaults to same name with .json)")
    args = parser.parse_args()

    if not os.path.isfile(args.pcap_path):
        print(f"Error: File not found: {args.pcap_path}", file=sys.stderr)
        sys.exit(1)

    result = parse_pcap(args.pcap_path)
    meta = result["metadata"]

    print("=" * 60)
    print(f" PCAP Analysis: {meta['pcap_file']}")
    print("=" * 60)
    print(f"LinkType                    : {meta['linktype']} ({'USBPCAP' if meta['linktype'] == 249 else 'OTHER'})")
    print(f"Total Captured Packets      : {meta['total_packets']}")
    print(f"Control Transfers           : {meta['control_transfers']}")
    print(f"Bulk OUT Transfers          : {meta['bulk_out_transfers']} ({meta['bulk_out_total_bytes']} bytes payload)")
    print(f"Bulk IN Transfers (with data): {meta['bulk_in_transfers_with_data']} ({meta['bulk_in_total_bytes']} bytes payload)")
    print(f"FTDI Status Heartbeats (2B) : {meta['ftdi_empty_heartbeats']}")
    print("-" * 60)

    json_path = args.json_out or os.path.splitext(args.pcap_path)[0] + ".json"
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)
    print(f"Exported JSON transcript    : {json_path}")

    if args.export_csv:
        csv_path = os.path.splitext(args.pcap_path)[0] + ".csv"
        export_csv(result["transfers"], csv_path, include_heartbeats=args.include_heartbeats)
        print(f"Exported CSV transcript     : {csv_path}")

    print("=" * 60)


if __name__ == "__main__":
    main()
