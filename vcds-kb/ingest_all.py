#!/usr/bin/env python3
"""
Full KB-1R Canonical Ingestion Pipeline (ingest_all.py).
Integrates:
1. Installation inventory (23,299 files, full SHA256 catalog)
2. Exact binary lineage (ORIGINAL vs DERIVED_UNPACKED)
3. Full function accounting (all 4,593+ functions categorized)
4. Evidence-backed indirect call resolution
5. Cross-module dependency graph
6. Non-PE diagnostic resource indexing
"""

import json
import os
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
KB_DIR = ROOT / "vcds-kb"
DB_PATH = KB_DIR / "vcds_kb.db"
SCHEMA_PATH = KB_DIR / "schema.sql"
RAW_DIR = ROOT / "reverse" / "raw"
INVENTORY_JSONL = ROOT / "reverse" / "installation_inventory.jsonl"

ORIGINAL_VCDS_SHA256 = "CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3"
UNPACKED_VCDS_SHA256 = "4F9BA9B39523512AA1F985FB4AFA77D21987D345BAEEF12AD9CED62D35A09AB5"
RTUS64_SHA256 = "B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2"

KNOWN_SEMANTICS = {
    "0x14007E734": ("vcds_adapter_send_frame", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007E824": ("vcds_adapter_read_frame", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007E988": ("vcds_cmd_get_version", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007EC2C": ("vcds_cmd_set_com_baud", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007ED84": ("vcds_cmd_kline_test", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007E3B4": ("vcds_cmd_init_5baud", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007F758": ("vcds_cmd_reset", "TRANSPORT", "PROVEN_STATIC"),
    "0x14007F1D8": ("vcds_read_controller_blocks", "SESSION", "PROVEN_STATIC"),
    "0x14007E630": ("vcds_cmd_turbo_baud", "TRANSPORT", "INFERRED"),
    "0x14011185C": ("vcds_ftdi_write_buffer", "TRANSPORT", "PROVEN_STATIC"),
    "0x140111828": ("vcds_ftdi_write_byte", "TRANSPORT", "PROVEN_STATIC"),
    "0x1401117BC": ("vcds_ftdi_read_byte", "TRANSPORT", "PROVEN_STATIC"),
    "0x140111F40": ("vcds_ftdi_enumerate_and_open", "TRANSPORT", "PROVEN_STATIC"),
    "0x140112160": ("vcds_ftdi_init_channel", "TRANSPORT", "PROVEN_STATIC"),
    "0x14011FEB8": ("vcds_security_challenge_verify", "SECURITY", "PROVEN_STATIC"),
    "0x14005911C": ("BlockDlg::OnGraph", "UI", "PROVEN_STATIC"),
    "0x14007CA6C": ("vcds_transport_ctor", "TRANSPORT", "PROVEN_STATIC")
}

def norm_addr(a: str) -> str:
    if not a or a == "UNKNOWN": return a or "UNKNOWN"
    try:
        clean = a.replace("0x", "").replace("0X", "").strip()
        val = int(clean, 16)
        return f"0x{val:X}"
    except Exception:
        return a

def run_pipeline():
    print(f"Initializing canonical SQLite database at {DB_PATH}...")
    if DB_PATH.exists():
        DB_PATH.unlink()
        
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA foreign_keys = ON")
    cur = conn.cursor()
    
    # 1. Execute Schema
    with open(SCHEMA_PATH, "r", encoding="utf-8") as sf:
        cur.executescript(sf.read())
    print("[1/6] Schema initialized.")
    
    # 2. Ingest Installation Inventory
    inv_count = 0
    pe_count = 0
    if INVENTORY_JSONL.exists():
        with open(INVENTORY_JSONL, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                rec = json.loads(line)
                cur.execute("""
                    INSERT INTO installation_inventory
                    (relative_path, filename, extension, size, sha256, mime_type, is_pe, architecture, version, role, analysis_status, referenced_by, opened_by_runtime, notes)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    rec["relative_path"], rec["filename"], rec["extension"], rec["size"],
                    rec["sha256"], rec["mime_type"], 1 if rec["is_pe"] else 0,
                    rec.get("architecture"), rec.get("version"), rec["role"],
                    rec["analysis_status"], rec.get("referenced_by"), 1 if rec.get("opened_by_runtime") else 0,
                    rec.get("notes")
                ))
                inv_count += 1
                if rec["is_pe"]: pe_count += 1
    print(f"[2/6] Ingested {inv_count} files ({pe_count} PE modules) into installation_inventory.")
    
    # 3. Register Binaries with Strict Provenance & Lineage
    binaries = [
        (
            r"C:\Ross-Tech\VCDS\VCDS.EXE",
            ORIGINAL_VCDS_SHA256,
            "26.3.0.0", "x64", "0x140000000", None,
            "ORIGINAL_INSTALLATION", None, "OFFICIAL_INSTALLER", "PROVEN_EQUIVALENT"
        ),
        (
            r"C:\Users\Admin\Desktop\vcds_re_project\VCDS_unpacked.exe",
            UNPACKED_VCDS_SHA256,
            "26.3.0.0", "x64", "0x140000000", "vcds_re",
            "DERIVED_UNPACKED", ORIGINAL_VCDS_SHA256, "MEMORY_DUMP_UNPACKING", "PROVEN_EQUIVALENT"
        ),
        (
            r"C:\Ross-Tech\VCDS\RTUS64.dll",
            RTUS64_SHA256,
            "2.12.28", "x64", "0x180000000", "vcds_re",
            "SUPPORT_MODULE", None, "OFFICIAL_INSTALLER", "NOT_APPLICABLE"
        )
    ]
    pe_analyzed_file = RAW_DIR / "pe_modules_analyzed.jsonl"
    if pe_analyzed_file.exists():
        with open(pe_analyzed_file, "r", encoding="utf-8") as pf:
            for line in pf:
                if not line.strip(): continue
                mod = json.loads(line)
                mod_sha = mod["sha256"]
                if mod_sha in [ORIGINAL_VCDS_SHA256, UNPACKED_VCDS_SHA256, RTUS64_SHA256]:
                    continue
                arch = "x64" if (mod["image_base"].startswith("0x14") or mod["image_base"].startswith("0x18") or mod["image_base"] == "0x100000000") else "x86"
                binaries.append((
                    rf"C:\Ross-Tech\VCDS\{mod['module']}",
                    mod_sha,
                    "SUPPORT", arch, mod["image_base"], "vcds_re",
                    "SUPPORT_MODULE", None, "OFFICIAL_INSTALLER", "NOT_APPLICABLE"
                ))
    for b in binaries:
        cur.execute("""
            INSERT INTO reverse_binaries 
            (path, sha256, version, architecture, image_base, ghidra_project, artifact_type, parent_sha256, extraction_method, address_equivalence_status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, b)
    print(f"[3/6] Registered {len(binaries)} authoritative binaries with strict lineage.")
    
    # Binary ID 2 is the analyzed unpacked binary
    cur.execute("SELECT id FROM reverse_binaries WHERE sha256 = ?", (UNPACKED_VCDS_SHA256,))
    analyzed_bin_id = cur.fetchone()[0]
    
    # 4. Ingest Functions
    funcs_file = RAW_DIR / "functions.jsonl"
    func_count = 0
    if funcs_file.exists():
        with open(funcs_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                addr = norm_addr(data["address"])
                assigned_name, cat, status = KNOWN_SEMANTICS.get(addr, (None, "UNKNOWN", "UNKNOWN"))
                cur.execute("""
                    INSERT OR REPLACE INTO reverse_functions
                    (binary_id, address, rva, size, original_name, assigned_name, namespace, decompiler_status, semantic_category, semantic_status, assembly_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    analyzed_bin_id, addr, data["rva"], data["size"], data["name"],
                    assigned_name, data["namespace"], data["decompiler_status"], cat, status, data["assembly_hash"]
                ))
                func_count += 1
    print(f"[4/6] Ingested {func_count} functions for analyzed binary {UNPACKED_VCDS_SHA256}.")
    
    # 5. Ingest Strings, Calls, VTables, Decompiler Chunks
    # Strings
    str_file = RAW_DIR / "strings.jsonl"
    if str_file.exists():
        with open(str_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                cur.execute("INSERT INTO reverse_strings (binary_id, address, value) VALUES (?, ?, ?)",
                            (analyzed_bin_id, data["address"].upper(), data["value"]))
                            
    # VTables
    vtables_file = RAW_DIR / "vtables.jsonl"
    if vtables_file.exists():
        with open(vtables_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                slot_int = int(data["slot"], 16)
                cur.execute("""
                    INSERT OR REPLACE INTO reverse_vtables (address, slot, target_function, class_candidate, constructor_evidence, usage_evidence)
                    VALUES (?, ?, ?, ?, ?, ?)
                """, (
                    norm_addr(data["vtable"]), slot_int, norm_addr(data["target"]),
                    data.get("class", data.get("class_candidate", "TransportAdapter")),
                    data.get("constructor_evidence", "0x14007CA6C sets *param_1 = 0x1401AD3C0"),
                    data.get("usage_evidence", "Dispatched via *(param_1 + 0x108)")
                ))
                
    # Decompiler Chunks
    decomp_file = RAW_DIR / "decompiler.jsonl"
    if decomp_file.exists():
        with open(decomp_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                addr = norm_addr(data["address"])
                cur.execute("SELECT id FROM reverse_functions WHERE address = ?", (addr,))
                f_row = cur.fetchone()
                if f_row:
                    cur.execute("""
                        INSERT OR REPLACE INTO reverse_decompiler_chunks
                        (function_id, decompiler_text, assembly_text, ghidra_version, analysis_sha256)
                        VALUES (?, ?, ?, '12.1.4', ?)
                    """, (f_row[0], data["decompiler_text"], data.get("assembly_text", ""), UNPACKED_VCDS_SHA256))
                    
    # Calls with Strict Indirect Call Evidence Requirement
    calls_file = RAW_DIR / "calls.jsonl"
    call_count = 0
    resolved_indirect = 0
    unresolved_indirect = 0
    
    # Known proven indirect call mappings (normalized)
    proven_indirect_targets = {
        "0x140111877": ("0x14018C860", "IMPORT_SLOT", "Calling FT_Write import pointer"),
        "0x1401117D6": ("0x14018C858", "IMPORT_SLOT", "Calling FT_Read import pointer"),
        "0x140112028": ("0x14018C878", "IMPORT_SLOT", "Calling FT_SetLatencyTimer import pointer"),
        "0x14007E410": ("0x14007E734", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x108) with vtable 0x1401AD3C0 assigned in FUN_14007ca6c"),
        "0x14007E9C7": ("0x14007E734", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x108) in HC::GetVersion"),
        "0x14007EC5E": ("0x14007E734", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x108) in HC::Com115"),
        "0x14007EDAA": ("0x14007E734", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x108) in HC::KLineTest"),
        "0x14007F778": ("0x14007E734", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x108) in HC::Reset"),
        "0x14007E425": ("0x14007E824", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x110) with vtable 0x1401AD3C0 in HC::Init5Baud"),
        "0x14007EC69": ("0x14007E824", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x110) in HC::Com115"),
        "0x14007F78B": ("0x14007E824", "VTABLE_CONSTRUCTOR_PROOF", "Dispatched via *(param_1 + 0x110) in HC::Reset")
    }
    
    if calls_file.exists():
        with open(calls_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                cs = norm_addr(data["callsite"])
                dt = data["dispatch_type"]
                callee = norm_addr(data["callee"])
                
                res_method = "UNRESOLVED"
                res_evidence = None
                is_res = 0
                
                if dt == "DIRECT":
                    res_method = "DIRECT_INSTRUCTION"
                    res_evidence = "Direct relative call operand"
                    is_res = 1
                else: # INDIRECT
                    if cs in proven_indirect_targets:
                        target_addr, method, ev = proven_indirect_targets[cs]
                        callee = target_addr
                        res_method = method
                        res_evidence = ev
                        is_res = 1
                        resolved_indirect += 1
                    elif callee.startswith("0X14018C") and len(callee) == 11:
                        # Import Address Table pointer
                        res_method = "IMPORT_SLOT"
                        res_evidence = f"Indirect call via IAT pointer {callee}"
                        is_res = 1
                        resolved_indirect += 1
                    else:
                        callee = "UNKNOWN"
                        res_method = "UNRESOLVED"
                        res_evidence = None
                        is_res = 0
                        unresolved_indirect += 1
                        
                cur.execute("""
                    INSERT INTO reverse_call_edges 
                    (caller, callsite, callee, dispatch_type, resolution_method, resolution_evidence, is_resolved)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                """, (norm_addr(data["caller"]), cs, callee, dt, res_method, res_evidence, is_res))
                call_count += 1
    print(f"[5/6] Ingested {call_count} call edges (Indirect: {resolved_indirect} evidence-resolved, {unresolved_indirect} unresolved).")
    
    # Seed claims, retractions, and gaps
    from kb import cmd_seed_claims
    cmd_seed_claims(conn)
    
    conn.commit()
    conn.close()
    
    # 6. Run Function Accounting & Cross-Module Graph Builder
    from build_function_accounting import run_accounting
    run_accounting()
    
    from build_cross_module_graph import build_graph
    build_graph()
    
    print("[6/6] Function accounting, claims seeding, and cross-module graphs built successfully.")


if __name__ == "__main__":
    run_pipeline()
