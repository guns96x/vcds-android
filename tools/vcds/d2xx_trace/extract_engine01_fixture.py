#!/usr/bin/env python3
"""Extract one clean VCDS D2XX session into a replay/analyse fixture.

Purpose
-------
M2 for the user's exact hardware is deliberately narrow:
Samsung S24 FE -> FA24 cable -> Golf 5 BLS -> 01-Engine.

This tool does NOT invent any protocol bytes. It converts one real
vcds_d2xx_trace.log session into a deterministic JSON fixture preserving:
- FTDI configuration calls,
- exact FT_Write / FT_Read buffers,
- timing deltas,
- parsed outer S/M frames when valid.

The resulting fixture is evidence for Android implementation/tests. It is not
automatically replayed to hardware.

Usage
-----
python extract_engine01_fixture.py vcds_d2xx_trace.log -o engine01_fixture.json
python extract_engine01_fixture.py vcds_d2xx_trace.log --session-index -1
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Dict, List

from analyze_trace import D2xxEvent, parse_trace_line, parse_wire_frame


OPEN_FUNCS = {"FT_Open", "FT_OpenEx"}
CLOSE_FUNCS = {"FT_Close"}
CONFIG_FUNCS = {
    "FT_ResetDevice",
    "FT_Purge",
    "FT_SetBaudRate",
    "FT_SetDivisor",
    "FT_SetDataCharacteristics",
    "FT_SetFlowControl",
    "FT_SetTimeouts",
    "FT_SetLatencyTimer",
    "FT_SetUSBParameters",
    "FT_SetDtr",
    "FT_ClrDtr",
    "FT_SetRts",
    "FT_ClrRts",
    "FT_SetBreakOn",
    "FT_SetBreakOff",
    "FT_SetBitMode",
}
IO_FUNCS = {"FT_Write", "FT_Read"}

# Same FA24 family identity already proven on the user's phone.
ROSSTECH_ASCII = b"ROSSTECH"


def load_events(path: Path) -> List[D2xxEvent]:
    events: List[D2xxEvent] = []
    with path.open("r", encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            evt = parse_trace_line(line)
            if evt is not None:
                events.append(evt)
    return events


def split_sessions(events: List[D2xxEvent]) -> List[List[D2xxEvent]]:
    """Split by FT_Open/OpenEx ... FT_Close.

    If the logger missed FT_Open but contains I/O, keep the whole stream as one
    fallback session rather than silently dropping evidence.
    """
    sessions: List[List[D2xxEvent]] = []
    current: List[D2xxEvent] = []

    for evt in events:
        if evt.func in OPEN_FUNCS:
            if current:
                sessions.append(current)
            current = [evt]
            continue

        if current:
            current.append(evt)
            if evt.func in CLOSE_FUNCS:
                sessions.append(current)
                current = []

    if current:
        sessions.append(current)

    if not sessions and events:
        sessions = [events]

    return sessions


def event_to_dict(evt: D2xxEvent, base_ms: int) -> Dict[str, Any]:
    out: Dict[str, Any] = {
        "dt_ms": evt.millis - base_ms,
        "seq": evt.seq,
        "tid": evt.tid,
        "func": evt.func,
        "args": evt.args,
    }
    if evt.returned_len is not None:
        out["returned_len"] = evt.returned_len
    if evt.hex_payload is not None:
        out["hex"] = evt.hex_payload.hex(" ").upper()

        if evt.func == "FT_Write":
            frame = parse_wire_frame(evt.hex_payload, "HOST_TO_MCU")
            direction = "HOST_TO_MCU"
        elif evt.func == "FT_Read":
            frame = parse_wire_frame(evt.hex_payload, "MCU_TO_HOST")
            direction = "MCU_TO_HOST"
        else:
            frame = None
            direction = None

        if direction is not None:
            out["direction"] = direction

        if frame is not None:
            out["frame"] = {
                "marker": f"0x{frame.marker:02X}",
                "length": frame.length,
                "opcode": f"0x{frame.opcode:02X}",
                "payload_hex": frame.payload.hex(" ").upper(),
                "xor": f"0x{frame.xor_checksum:02X}",
                "xor_valid": frame.xor_valid,
            }

    return out


def build_fixture(events: List[D2xxEvent], source: Path, session_index: int) -> Dict[str, Any]:
    if not events:
        raise ValueError("Selected session is empty")

    base_ms = events[0].millis
    selected = [
        evt for evt in events
        if evt.func in OPEN_FUNCS | CLOSE_FUNCS | CONFIG_FUNCS | IO_FUNCS
    ]

    write_frames = []
    read_frames = []
    raw_reads = []

    for evt in selected:
        if evt.func == "FT_Write" and evt.hex_payload:
            f = parse_wire_frame(evt.hex_payload, "HOST_TO_MCU")
            if f is not None:
                write_frames.append(f)
        elif evt.func == "FT_Read" and evt.hex_payload:
            raw_reads.append(evt.hex_payload)
            f = parse_wire_frame(evt.hex_payload, "MCU_TO_HOST")
            if f is not None:
                read_frames.append(f)

    identify_seen = any(
        f.opcode == 0x04 and ROSSTECH_ASCII in f.payload for f in read_frames
    )

    host_opcodes = [f.opcode for f in write_frames]
    cable_opcodes = [f.opcode for f in read_frames]

    # Diagnostic transport evidence only; no semantic claim about the inner ECU
    # protocol. B8/B7 is the already capture-grounded FA24 diagnostic envelope.
    diag_envelope_seen = 0xB8 in host_opcodes or 0xB7 in cable_opcodes

    fixture: Dict[str, Any] = {
        "schema": "vcds-d2xx-engine01-fixture-v1",
        "source_log": source.name,
        "session_index": session_index,
        "duration_ms": events[-1].millis - base_ms,
        "evidence": {
            "identify_rosstech_seen": identify_seen,
            "diagnostic_envelope_seen": diag_envelope_seen,
            "host_opcodes": [f"0x{x:02X}" for x in sorted(set(host_opcodes))],
            "cable_opcodes": [f"0x{x:02X}" for x in sorted(set(cable_opcodes))],
            "write_frame_count": len(write_frames),
            "read_frame_count": len(read_frames),
            "raw_read_count": len(raw_reads),
        },
        "events": [event_to_dict(evt, base_ms) for evt in selected],
    }

    return fixture


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("log", type=Path)
    ap.add_argument("-o", "--output", type=Path, default=Path("engine01_fixture.json"))
    ap.add_argument(
        "--session-index",
        type=int,
        default=-1,
        help="0-based D2XX session index; negative values count from the end (default: -1)",
    )
    ap.add_argument(
        "--require-engine-traffic",
        action="store_true",
        help="fail unless a ROSSTECH identify reply and B8/B7 diagnostic envelope are present",
    )
    args = ap.parse_args()

    events = load_events(args.log)
    sessions = split_sessions(events)
    if not sessions:
        raise SystemExit("No D2XX events found in log")

    idx = args.session_index
    if idx < 0:
        idx = len(sessions) + idx
    if idx < 0 or idx >= len(sessions):
        raise SystemExit(
            f"Session index out of range: requested {args.session_index}, found {len(sessions)} session(s)"
        )

    fixture = build_fixture(sessions[idx], args.log, idx)

    if args.require_engine_traffic:
        ev = fixture["evidence"]
        missing = []
        if not ev["identify_rosstech_seen"]:
            missing.append("ROSSTECH identify reply")
        if not ev["diagnostic_envelope_seen"]:
            missing.append("B8/B7 diagnostic envelope")
        if missing:
            raise SystemExit("Trace is not a complete Engine-01 candidate: missing " + ", ".join(missing))

    args.output.write_text(json.dumps(fixture, indent=2) + "\n", encoding="utf-8")

    ev = fixture["evidence"]
    print(f"Wrote {args.output}")
    print(f"Session duration: {fixture['duration_ms']} ms")
    print(f"ROSSTECH identify: {ev['identify_rosstech_seen']}")
    print(f"Diagnostic B8/B7 envelope: {ev['diagnostic_envelope_seen']}")
    print("Host opcodes:", ", ".join(ev["host_opcodes"]) or "(none)")
    print("Cable opcodes:", ", ".join(ev["cable_opcodes"]) or "(none)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
