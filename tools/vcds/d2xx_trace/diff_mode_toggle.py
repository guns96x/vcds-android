#!/usr/bin/env python3
"""Diff three D2XX traces around the VCDS 'Boot in intelligent mode' transition.

No protocol semantics are assumed. The report preserves exact FT_* setup calls and
FT_Write/FT_Read buffers, then highlights signatures unique to the transition.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any

from analyze_trace import parse_trace_line

INTERESTING = {
    "FT_Open", "FT_OpenEx", "FT_Close", "FT_ResetDevice", "FT_Purge",
    "FT_SetBaudRate", "FT_SetDivisor", "FT_SetDataCharacteristics",
    "FT_SetFlowControl", "FT_SetTimeouts", "FT_SetLatencyTimer",
    "FT_SetUSBParameters", "FT_SetDtr", "FT_ClrDtr", "FT_SetRts",
    "FT_ClrRts", "FT_SetBreakOn", "FT_SetBreakOff", "FT_SetBitMode",
    "FT_Write", "FT_Read",
}


def load(path: Path) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8", errors="ignore") as fh:
        for line in fh:
            evt = parse_trace_line(line)
            if evt is None or evt.func not in INTERESTING:
                continue
            item: dict[str, Any] = {
                "ms": evt.millis,
                "seq": evt.seq,
                "func": evt.func,
                "args": evt.args,
            }
            if evt.hex_payload is not None:
                item["hex"] = evt.hex_payload.hex(" ").upper()
            if evt.returned_len is not None:
                item["returned_len"] = evt.returned_len
            out.append(item)
    return out


def signatures(events: list[dict[str, Any]]) -> Counter[str]:
    result: Counter[str] = Counter()
    for e in events:
        if e["func"] in {"FT_Write", "FT_Read"}:
            sig = f'{e["func"]}:{e.get("hex", "")}'
        else:
            sig = f'{e["func"]}:{e.get("args", "")}'
        result[sig] += 1
    return result


def writes(events: list[dict[str, Any]]) -> list[str]:
    return [e.get("hex", "") for e in events if e["func"] == "FT_Write"]


def unique_to(a: Counter[str], b: Counter[str], c: Counter[str]) -> list[dict[str, Any]]:
    rows = []
    for sig, count in a.items():
        if b.get(sig, 0) == 0 and c.get(sig, 0) == 0:
            rows.append({"signature": sig, "count": count})
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("smart", type=Path)
    ap.add_argument("transition", type=Path)
    ap.add_argument("dumb", type=Path)
    ap.add_argument("-o", "--output", type=Path, default=Path("mode_toggle_diff.json"))
    args = ap.parse_args()

    smart = load(args.smart)
    transition = load(args.transition)
    dumb = load(args.dumb)

    s0, s1, s2 = map(signatures, (smart, transition, dumb))
    report = {
        "schema": "vcds-fa24-smart-dumb-diff-v1",
        "inputs": {
            "smart": args.smart.name,
            "transition": args.transition.name,
            "dumb": args.dumb.name,
        },
        "counts": {
            "smart_events": len(smart),
            "transition_events": len(transition),
            "dumb_events": len(dumb),
        },
        "write_streams": {
            "smart": writes(smart),
            "transition": writes(transition),
            "dumb": writes(dumb),
        },
        "unique_to_transition": unique_to(s1, s0, s2),
        "unique_to_smart": unique_to(s0, s1, s2),
        "unique_to_dumb": unique_to(s2, s0, s1),
        "events": {
            "smart": smart,
            "transition": transition,
            "dumb": dumb,
        },
    }

    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output}")
    print("Smart FT_Write count:", len(report["write_streams"]["smart"]))
    print("Transition FT_Write count:", len(report["write_streams"]["transition"]))
    print("Dumb FT_Write count:", len(report["write_streams"]["dumb"]))
    print("Unique transition signatures:", len(report["unique_to_transition"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
