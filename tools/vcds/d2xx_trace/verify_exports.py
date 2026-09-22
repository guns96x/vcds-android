#!/usr/bin/env python3
"""verify_exports.py -- Validates built RTUS64.dll against canonical pe_manifest.json."""

import json
import sys
from pathlib import Path
import pefile

BASE_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = BASE_DIR / "pe_manifest.json"
DLL_PATH = BASE_DIR / "target" / "release" / "RTUS64.dll"


def main():
    if not MANIFEST_PATH.exists():
        print(f"FAIL: Manifest not found: {MANIFEST_PATH}", file=sys.stderr)
        sys.exit(1)

    if not DLL_PATH.exists():
        print(f"FAIL: Built DLL not found: {DLL_PATH}", file=sys.stderr)
        sys.exit(1)

    with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
        manifest = json.load(f)

    if "RTUS64.dll" not in manifest or "exports" not in manifest["RTUS64.dll"]:
        print("FAIL: RTUS64.dll exports missing from manifest", file=sys.stderr)
        sys.exit(1)

    expected_exports = {e["ordinal"]: e["name"] for e in manifest["RTUS64.dll"]["exports"]}
    print(f"Loaded {len(expected_exports)} expected exports from manifest.")

    pe = pefile.PE(str(DLL_PATH))
    if not hasattr(pe, "DIRECTORY_ENTRY_EXPORT") or not pe.DIRECTORY_ENTRY_EXPORT.symbols:
        print("FAIL: Built DLL has no export directory!", file=sys.stderr)
        sys.exit(1)

    actual_exports = {
        exp.ordinal: (exp.name.decode("ascii", errors="ignore") if exp.name else None)
        for exp in pe.DIRECTORY_ENTRY_EXPORT.symbols
    }
    print(f"Found {len(actual_exports)} exports in built DLL.")

    errors = []
    if len(actual_exports) != len(expected_exports):
        errors.append(f"Export count mismatch: expected {len(expected_exports)}, got {len(actual_exports)}")

    for ord_num, exp_name in expected_exports.items():
        if ord_num not in actual_exports:
            errors.append(f"Missing export ordinal {ord_num}: {exp_name}")
        elif actual_exports[ord_num] != exp_name:
            errors.append(
                f"Ordinal {ord_num} name mismatch: expected '{exp_name}', got '{actual_exports[ord_num]}'"
            )

    for ord_num, act_name in actual_exports.items():
        if ord_num not in expected_exports:
            errors.append(f"Unexpected extra export ordinal {ord_num}: {act_name}")

    if errors:
        print("\n".join(errors), file=sys.stderr)
        sys.exit(1)

    print(f"SUCCESS: All {len(expected_exports)} exports verified with 100% ordinal and name fidelity.")


if __name__ == "__main__":
    main()
