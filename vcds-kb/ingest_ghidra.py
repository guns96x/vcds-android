#!/usr/bin/env python3
"""
Ingest Ghidra Headless JSONL Export into VCDS Reverse Engineering SQLite Database.
"""

import json
import os
import sqlite3
import sys
from pathlib import Path

VCDS_EXE_PATH = r"C:\Ross-Tech\VCDS\VCDS.EXE"
RTUS_DLL_PATH = r"C:\Ross-Tech\VCDS\RTUS64.dll"
EXPECTED_VCDS_SHA256 = "CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3"
EXPECTED_RTUS_SHA256 = "B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2"

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
    "0x14005911C": ("BlockDlg::OnGraph", "UI", "PROVEN_STATIC")
}

def norm_addr(a: str) -> str:
    if not a: return ""
    clean = a.replace("0x", "").replace("0X", "").strip().upper()
    return f"0x{clean}"

def ingest(db_path: str, raw_dir: str):
    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    
    print(f"Connecting to {db_path}...")
    
    # 1. Register Binaries
    cur.execute("SELECT id FROM reverse_binaries WHERE sha256 = ?", (EXPECTED_VCDS_SHA256,))
    row = cur.fetchone()
    if row:
        binary_id = row[0]
    else:
        cur.execute("""
            INSERT INTO reverse_binaries (path, sha256, version, architecture, image_base, ghidra_project)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (VCDS_EXE_PATH, EXPECTED_VCDS_SHA256, "26.3.0.0", "x64", "0x140000000", "vcds_re"))
        binary_id = cur.lastrowid
        
    print(f"Authoritative binary ID: {binary_id} (SHA256: {EXPECTED_VCDS_SHA256})")
    
    # 2. Ingest Functions
    funcs_file = os.path.join(raw_dir, "functions.jsonl")
    func_count = 0
    if os.path.exists(funcs_file):
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
                    binary_id, addr, data["rva"], data["size"], data["name"],
                    assigned_name, data["namespace"], data["decompiler_status"], cat, status, data["assembly_hash"]
                ))
                func_count += 1
        print(f"Ingested {func_count} functions.")

    # Ingest D2XX imported pointer symbols as external functions
    d2xx_imports = [
        ("0x14018C860", "FT_Write", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C858", "FT_Read", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C890", "FT_GetQueueStatus", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C878", "FT_SetLatencyTimer", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C848", "FT_SetTimeouts", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C820", "FT_SetDataCharacteristics", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C8A8", "FT_Open", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C810", "FT_ResetDevice", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C8B8", "FT_SetBaudRate", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14018C840", "FT_Purge", "RTUS64.dll", "PROVEN_STATIC"),
        ("0x14007E734", "vcds_adapter_send_frame", "HC::Transport", "PROVEN_STATIC"),
        ("0x14007E824", "vcds_adapter_read_frame", "HC::Transport", "PROVEN_STATIC")
    ]
    for ptr_addr, name, ns, st in d2xx_imports:
        cur.execute("""
            INSERT OR REPLACE INTO reverse_functions
            (binary_id, address, rva, size, original_name, assigned_name, namespace, decompiler_status, semantic_category, semantic_status)
            VALUES (?, ?, ?, 8, ?, ?, ?, 'DECOMPILED', 'TRANSPORT', ?)
        """, (binary_id, ptr_addr, f"0x{int(ptr_addr, 16) - 0x140000000:08X}", name, name, ns, st))

        
    # 3. Ingest Strings
    str_file = os.path.join(raw_dir, "strings.jsonl")
    str_count = 0
    if os.path.exists(str_file):
        with open(str_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                cur.execute("""
                    INSERT INTO reverse_strings (binary_id, address, value)
                    VALUES (?, ?, ?)
                """, (binary_id, data["address"].upper(), data["value"]))
                str_count += 1
        print(f"Ingested {str_count} strings.")
        
    # 4. Ingest Calls
    calls_file = os.path.join(raw_dir, "calls.jsonl")
    call_count = 0
    if os.path.exists(calls_file):
        with open(calls_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                cur.execute("""
                    INSERT INTO reverse_call_edges (caller, callsite, callee, dispatch_type)
                    VALUES (?, ?, ?, ?)
                """, (norm_addr(data["caller"]), norm_addr(data["callsite"]), norm_addr(data["callee"]), data["dispatch_type"]))
                call_count += 1
        print(f"Ingested {call_count} call edges.")

    # 5. Ingest VTables
    vtables_file = os.path.join(raw_dir, "vtables.jsonl")
    vtable_count = 0
    if os.path.exists(vtables_file):
        with open(vtables_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                slot_int = int(data["slot"], 16)
                cur.execute("""
                    INSERT OR REPLACE INTO reverse_vtables (address, slot, target_function, class_candidate)
                    VALUES (?, ?, ?, ?)
                """, (norm_addr(data["vtable"]), slot_int, norm_addr(data["target"]), data["class"]))
                vtable_count += 1
        print(f"Ingested {vtable_count} vtable slots.")

    # 6. Ingest Decompiler Chunks
    decomp_file = os.path.join(raw_dir, "decompiler.jsonl")
    decomp_count = 0
    if os.path.exists(decomp_file):
        with open(decomp_file, "r", encoding="utf-8") as f:
            for line in f:
                if not line.strip(): continue
                data = json.loads(line)
                addr = norm_addr(data["address"])
                cur.execute("SELECT id FROM reverse_functions WHERE address = ?", (addr,))
                f_row = cur.fetchone()
                if f_row:
                    fid = f_row[0]
                    cur.execute("""
                        INSERT OR REPLACE INTO reverse_decompiler_chunks
                        (function_id, decompiler_text, assembly_text, ghidra_version, analysis_sha256)
                        VALUES (?, ?, ?, ?, ?)
                    """, (fid, data["decompiler_text"], data["assembly_text"], "12.1.4", EXPECTED_VCDS_SHA256))
                    decomp_count += 1
        print(f"Ingested {decomp_count} decompiler chunks.")
        
    conn.commit()
    conn.close()
    print("Ingestion complete.")

if __name__ == "__main__":
    kb_dir = Path(__file__).resolve().parent
    db_file = kb_dir / "vcds_kb.db"
    raw_path = kb_dir.parent / "reverse" / "raw"
    ingest(str(db_file), str(raw_path))
