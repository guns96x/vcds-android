#!/usr/bin/env python3
"""analyze_trace.py -- Parser and delta analyzer for vcds_d2xx_trace.log.

Analyzes D2XX call streams captured by the RTUS64 trace shim,
extracts FT_Write / FT_Read byte buffers, calculates XOR checksums,
pairs requests with responses, and isolates protocol deltas.
"""

import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional


@dataclass
class D2xxEvent:
    millis: int
    seq: int
    tid: int
    func: str
    args: str
    hex_payload: Optional[bytes] = None
    ascii_payload: Optional[str] = None
    returned_len: Optional[int] = None
    raw_line: str = ""


@dataclass
class WireFrame:
    marker: int  # 0x53 ('S') or 0x4D ('M')
    length: int
    opcode: int
    payload: bytes
    xor_checksum: int
    xor_valid: bool
    direction: str  # "HOST_TO_MCU" or "MCU_TO_HOST"


LINE_RE = re.compile(
    r"^\[(\d+)ms\]\[seq:(\d+)\]\[tid:(\d+)\]\s+([A-Za-z0-9_]+)\((.*?)\)(.*)$"
)
HEX_RE = re.compile(r"HEX:\s*\[([0-9A-Fa-f\s]*)\]")
ASCII_RE = re.compile(r'ASCII:\s*"(.*?)"')
RETURNED_RE = re.compile(r"returned:\s*(\d+)")


def parse_trace_line(line: str) -> Optional[D2xxEvent]:
    line = line.strip()
    if not line:
        return None

    m = LINE_RE.match(line)
    if not m:
        return None

    millis = int(m.group(1))
    seq = int(m.group(2))
    tid = int(m.group(3))
    func = m.group(4)
    args = m.group(5)
    rest = m.group(6)

    hex_payload = None
    hex_match = HEX_RE.search(rest)
    if hex_match:
        hex_str = hex_match.group(1).replace(" ", "")
        if hex_str:
            hex_payload = bytes.fromhex(hex_str)

    ascii_payload = None
    ascii_match = ASCII_RE.search(rest)
    if ascii_match:
        ascii_payload = ascii_match.group(1)

    returned_len = None
    ret_match = RETURNED_RE.search(rest)
    if ret_match:
        returned_len = int(ret_match.group(1))

    return D2xxEvent(
        millis=millis,
        seq=seq,
        tid=tid,
        func=func,
        args=args,
        hex_payload=hex_payload,
        ascii_payload=ascii_payload,
        returned_len=returned_len,
        raw_line=line,
    )


def parse_wire_frame(data: bytes, direction: str) -> Optional[WireFrame]:
    """Parse a single frame of format: [marker][length][opcode][payload...][xor]."""
    if len(data) < 3:
        return None
    marker = data[0]
    expected_marker = 0x53 if direction == "HOST_TO_MCU" else 0x4D
    if marker != expected_marker:
        return None

    length = data[1]
    if len(data) < length or length < 3:
        return None

    opcode = data[2]
    payload = data[3 : length - 1] if length > 3 else b""
    xor_byte = data[length - 1]

    # Calculate XOR checksum over all bytes preceding the checksum byte
    computed_xor = 0
    for b in data[: length - 1]:
        computed_xor ^= b

    return WireFrame(
        marker=marker,
        length=length,
        opcode=opcode,
        payload=payload,
        xor_checksum=xor_byte,
        xor_valid=(computed_xor == xor_byte),
        direction=direction,
    )


def analyze_trace_file(path: Path) -> dict:
    if not path.exists():
        return {"error": f"File not found: {path}"}

    events: List[D2xxEvent] = []
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            evt = parse_trace_line(line)
            if evt:
                events.append(evt)

    total_events = len(events)
    writes = [e for e in events if e.func == "FT_Write"]
    reads = [e for e in events if e.func == "FT_Read" and (e.returned_len or 0) > 0]
    bauds = [e for e in events if e.func in ("FT_SetBaudRate", "FT_SetDivisor")]
    control_lines = [e for e in events if "Dtr" in e.func or "Rts" in e.func]

    total_bytes_written = sum(len(w.hex_payload or b"") for w in writes)
    total_bytes_read = sum(len(r.hex_payload or b"") for r in reads)

    # Reconstruct frames from writes and reads
    write_frames = []
    for w in writes:
        if w.hex_payload:
            frame = parse_wire_frame(w.hex_payload, "HOST_TO_MCU")
            if frame:
                write_frames.append(frame)

    read_frames = []
    for r in reads:
        if r.hex_payload:
            frame = parse_wire_frame(r.hex_payload, "MCU_TO_HOST")
            if frame:
                read_frames.append(frame)

    return {
        "file": str(path),
        "total_events": total_events,
        "write_count": len(writes),
        "read_count": len(reads),
        "total_bytes_written": total_bytes_written,
        "total_bytes_read": total_bytes_read,
        "baud_events": [f"{b.func}({b.args})" for b in bauds],
        "control_line_events": [f"{c.func}({c.args})" for c in control_lines],
        "parsed_write_frames": len(write_frames),
        "parsed_read_frames": len(read_frames),
        "write_opcodes": sorted(list({f"0x{f.opcode:02X}" for f in write_frames})),
        "read_opcodes": sorted(list({f"0x{f.opcode:02X}" for f in read_frames})),
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python analyze_trace.py <path_to_vcds_d2xx_trace.log>")
        sys.exit(1)

    log_path = Path(sys.argv[1])
    results = analyze_trace_file(log_path)
    import json
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
