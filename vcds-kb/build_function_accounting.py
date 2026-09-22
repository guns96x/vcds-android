#!/usr/bin/env python3
"""
Full Function Accounting Engine for VCDS Knowledge Base.
Classifies all 4,593+ functions into explicit epistemic categories per KB-1R.
"""

import json
import sqlite3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "vcds-kb" / "vcds_kb.db"
FUNCS_JSONL = ROOT / "reverse" / "raw" / "functions.jsonl"
DECOMP_JSONL = ROOT / "reverse" / "raw" / "decompiler.jsonl"
OUTPUT_ACCOUNTING_JSONL = ROOT / "reverse" / "raw" / "functions_accounting.jsonl"

UNPACKED_SHA256 = "4F9BA9B39523512AA1F985FB4AFA77D21987D345BAEEF12AD9CED62D35A09AB5"

SECURITY_FUNCS = {
    "0x14011FEB8", "0x14007F1D8", "0x140113214", "0x14011AF34", 
    "0x14011A0D4", "0x1401215B8", "0x140083E2C"
}

CRT_PATTERNS = [
    r"^_", r"^__", r"^malloc", r"^free", r"^mem", r"^str", r"^wcs",
    r"Afx", r"^register_", r"^deregister_", r"^pre_c_init", r"^post_pgo_init",
    r"^terminate", r"^abort", r"handler", r"cookie", r"seh", r"unwind", r"GSHandler"
]

def classify_function(func_data, decompiled_addrs):
    addr = norm_addr(func_data["address"])
    name = func_data.get("name", "")

    ns = func_data.get("namespace", "")
    size = func_data.get("size", 0)
    asm_hash = func_data.get("assembly_hash", "")
    
    # 1. Decompiled
    if addr in decompiled_addrs:
        return "DECOMPILED", None, None
        
    # 2. Out of scope security
    if addr in SECURITY_FUNCS or "security" in name.lower() or "challenge" in name.lower():
        return "OUT_OF_SCOPE_SECURITY", "DONGLE_AUTHENTICATION_AND_CHALLENGE_LOGIC", None
        
    # 3. Non-code thunk
    if size <= 12 and ("thunk" in name.lower() or name.startswith("FUN_14015") or "jmp" in name.lower()):
        return "NON_CODE_THUNK", "IMPORT_OR_BRANCH_THUNK", None
        
    # 4. External
    if "ext_" in name.lower() or ns in ["EXTERNAL", "ADVAPI32.DLL", "KERNEL32.DLL", "USER32.DLL"]:
        return "EXTERNAL", "IMPORTED_OPERATING_SYSTEM_SYMBOL", None
        
    # 5. Skipped with reason
    for pat in CRT_PATTERNS:
        if re.search(pat, name, re.IGNORECASE) or re.search(pat, ns, re.IGNORECASE):
            return "SKIPPED_WITH_REASON", "MSVC_CRT_COMPILER_RUNTIME_LIBRARY", None
            
    if any(k in ns for k in ["CWnd", "CDialog", "CButton", "CStatic", "CEdit", "CMenu", "CWinApp"]):
        return "SKIPPED_WITH_REASON", "MFC_GUI_FRAMEWORK_INTERNALS", None
        
    if any(k in name for k in ["Dlg", "Btn", "Menu", "Window", "OnPaint", "OnInitDialog", "About"]):
        return "SKIPPED_WITH_REASON", "NON_DIAGNOSTIC_UI_EVENT_HANDLER", None
        
    if "format" in name.lower() or "convert" in name.lower() or "util" in name.lower():
        return "SKIPPED_WITH_REASON", "UTILITY_HELPER_OUT_OF_TRANSPORT_SCOPE", None
        
    # 6. Session / Diagnostic logic pending deeper pass
    return "SKIPPED_WITH_REASON", "DIAGNOSTIC_SUBSYSTEM_PENDING_PRIORITIZED_PASS", None

def norm_addr(a: str) -> str:
    if not a: return ""
    clean = a.replace("0x", "").replace("0X", "").strip()
    return f"0x{int(clean, 16):X}"

def run_accounting():
    print(f"Reading {FUNCS_JSONL}...")
    decompiled_addrs = set()
    if DECOMP_JSONL.exists():
        with open(DECOMP_JSONL, "r", encoding="utf-8") as df:
            for line in df:
                if line.strip():
                    decompiled_addrs.add(norm_addr(json.loads(line)["address"]))
    print(f"Loaded {len(decompiled_addrs)} decompiled chunks.")
    
    records = []
    failed_records = []
    
    with open(FUNCS_JSONL, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip(): continue
            fd = json.loads(line)
            st, skip_r, fail_r = classify_function(fd, decompiled_addrs)
            n_addr = norm_addr(fd["address"])
            rec = {
                "address": n_addr,
                "name": fd["name"],
                "namespace": fd["namespace"],
                "size": fd["size"],
                "accounting_status": st,
                "skip_reason": skip_r,
                "failure_reason": fail_r
            }

            records.append(rec)
            if st == "DECOMPILER_FAILED":
                failed_records.append(rec)
                
    with open(OUTPUT_ACCOUNTING_JSONL, "w", encoding="utf-8") as out:
        for r in records:
            out.write(json.dumps(r) + "\n")
            
    # Print summary counts
    from collections import Counter
    counts = Counter(r["accounting_status"] for r in records)
    print("Function Accounting Summary:")
    for k, v in sorted(counts.items()):
        print(f"  {k:25s}: {v:5d}")
        
    # If database exists, update reverse_functions and functions_failed
    if DB_PATH.exists():
        conn = sqlite3.connect(str(DB_PATH))
        cur = conn.cursor()
        for r in records:
            cur.execute("""
                UPDATE reverse_functions 
                SET accounting_status = ?, skip_reason = ?, failure_reason = ?
                WHERE address = ?
            """, (r["accounting_status"], r["skip_reason"], r["failure_reason"], r["address"]))
            
        cur.execute("DELETE FROM functions_failed")
        for f in failed_records:
            cur.execute("""
                INSERT INTO functions_failed 
                (binary_sha256, function_address, status, failure_reason, ghidra_version)
                VALUES (?, ?, ?, ?, '12.1.4')
            """, (UNPACKED_SHA256, f["address"], f["accounting_status"], f["failure_reason"]))
            
        conn.commit()
        conn.close()
        print(f"Updated SQLite database with {len(records)} accounted functions.")

    return counts

if __name__ == "__main__":
    run_accounting()
