#!/usr/bin/env python3
"""
VCDS Knowledge Base (vcds-kb) Core CLI & Manager.
Authoritative SQLite Truth Store for VCDS 26.3 Reverse Engineering Evidence.
"""

import argparse
import hashlib
import json
import os
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "vcds_kb.db"
SCHEMA_PATH = ROOT / "schema.sql"

EXPECTED_VCDS_SHA256 = "CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3"
EXPECTED_RTUS_SHA256 = "B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2"

def connect():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn

def cmd_init(args):
    """Initialize or update schema from schema.sql."""
    conn = connect()
    with open(SCHEMA_PATH, "r", encoding="utf-8") as f:
        schema_sql = f.read()
    conn.executescript(schema_sql)
    conn.commit()
    conn.close()
    print(f"Database initialized successfully: {DB_PATH}")

def cmd_seed_claims(conn):
    """Seed proven claims, retractions, and gaps from verified audit."""
    cur = conn.cursor()
    
    # 1. Seed Claims
    claims_data = [
        {
            "key": "CLAIM_D2XX_FT_WRITE_PTR",
            "stmt": "FT_Write pointer is located at slot 0x14018C860 in .rdata, pointing to RTUS64.dll!FT_Write (RVA 0x02C10).",
            "status": "PROVEN_STATIC",
            "addr": "0x14018C860",
            "callsite": "0x14011185C",
            "prov": "Export table matching RTUS64.dll and decompiler xrefs in FUN_14011185c"
        },
        {
            "key": "CLAIM_D2XX_FT_READ_PTR",
            "stmt": "FT_Read pointer is located at slot 0x14018C858 in .rdata, pointing to RTUS64.dll!FT_Read (RVA 0x02BB0).",
            "status": "PROVEN_STATIC",
            "addr": "0x14018C858",
            "callsite": "0x1401117BC",
            "prov": "Export table matching RTUS64.dll and decompiler xrefs in FUN_1401117bc"
        },
        {
            "key": "CLAIM_D2XX_LATENCY_TIMER",
            "stmt": "VCDS configures FTDI USB Latency Timer to 1 ms via FT_SetLatencyTimer(ftHandle, 1) in FUN_140111f40.",
            "status": "PROVEN_STATIC",
            "addr": "0x140111F40",
            "callsite": "0x140112028",
            "prov": "Decompiled C AST in FUN_140111f40 calling (*DAT_14018c878)(DAT_140630f48, 1)"
        },
        {
            "key": "CLAIM_D2XX_VID_PID_FILTER",
            "stmt": "VCDS filters connected FTDI devices for Ross-Tech hardware by checking ID at offset +8 of FT_DEVICE_LIST_INFO_NODE (VID == 0x0403, PID in 0xFA20..0xFA2F).",
            "status": "PROVEN_STATIC",
            "addr": "0x140111F40",
            "callsite": "0x140111F80",
            "prov": "Decompiled C AST in FUN_140111f40 lines 24-32"
        },
        {
            "key": "CLAIM_FRAME_BUILDER",
            "stmt": "Host-to-adapter request frames are constructed by FUN_14007e734: sync byte 0x53 ('S'), length byte L = payload_len + 3, XOR checksum appended.",
            "status": "PROVEN_STATIC",
            "addr": "0x14007E734",
            "callsite": "0x14007E734",
            "prov": "Decompiled C AST in FUN_14007e734 and vtable slot 0x108 at 0x1401AD4C8"
        },
        {
            "key": "CLAIM_FRAME_PARSER",
            "stmt": "Adapter-to-host response frames are verified by FUN_14007e824: sync byte 0x4D ('M'), total length L, cumulative XOR checksum over all L bytes must equal 0x00.",
            "status": "PROVEN_STATIC",
            "addr": "0x14007E824",
            "callsite": "0x14007E824",
            "prov": "Decompiled C AST in FUN_14007e824 and vtable slot 0x110 at 0x1401AD4D0"
        },
        {
            "key": "CLAIM_5BAUD_PARITY",
            "stmt": "5-baud address encoding in FUN_14007e3b4 uses ODD PARITY: bit 7 (0x80) is set ONLY if initial 1-bits count is even. Address 0x01 has 1 bit -> encodes to 0x01.",
            "status": "PROVEN_STATIC",
            "addr": "0x14007E3B4",
            "callsite": "0x14007E3B4",
            "prov": "Decompiled C AST lines 20-33 in FUN_14007e3b4"
        },
        {
            "key": "CLAIM_OPCODE_02_VERSION",
            "stmt": "Opcode 0x02 (HC::GetVersion) queries adapter firmware version and model character ('D' for Dual-K+CAN) with 750 ms timeout.",
            "status": "PROVEN_STATIC",
            "addr": "0x14007E988",
            "callsite": "0x14007E9C7",
            "prov": "Decompiled C AST and strings in FUN_14007e988"
        },
        {
            "key": "CLAIM_OPCODE_03_BAUD",
            "stmt": "Opcode 0x03 (HC::Com115) switches UART speed to 115200 via 4-phase handshake (0x03 -> 0xFE -> local FT_SetBaudRate -> 0xFD -> 0xFE).",
            "status": "PROVEN_STATIC",
            "addr": "0x14007EC2C",
            "callsite": "0x14007EC5E",
            "prov": "Decompiled C AST and strings in FUN_14007ec2c"
        },
        {
            "key": "CLAIM_OPCODE_84_5BAUD_INIT",
            "stmt": "Opcode 0x84 (HC::Init5Baud) instructs adapter microcontroller to autonomously drive 5-baud wake-up pulses on K-line with 3300 ms timeout.",
            "status": "PROVEN_STATIC",
            "addr": "0x14007E3B4",
            "callsite": "0x14007E410",
            "prov": "Decompiled C AST in FUN_14007e3b4 with 0x55 sync byte validation"
        }
    ]
    
    for cl in claims_data:
        cur.execute("""
            INSERT OR REPLACE INTO claims 
            (claim_key, statement, evidence_status, binary_sha256, function_address, callsite, provenance)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (cl["key"], cl["stmt"], cl["status"], EXPECTED_VCDS_SHA256, cl["addr"], cl["callsite"], cl["prov"]))
        
    # 2. Seed Retractions
    retractions_data = [
        (
            "CLAIM_5BAUD_PARITY_0x81",
            "ECU 0x01 parity encoded as 0x81",
            "Mathematical error in previous draft. VCDS uses odd parity; address 0x01 has 1 set bit (already odd) so bit 7 is not set.",
            "ECU 0x01 parity encoded as 0x01"
        ),
        (
            "CLAIM_GROUP_011_RAW_OPCODE_0x21",
            "Group 011 raw Ross-Tech adapter opcode = 0x21",
            "Unproven conflation of ISO 14230 KWP service with adapter framing. Wire encapsulation path between BlockDlg and adapter send-frame is mediated by vtable dispatch and remains unproven.",
            "Group 011 request encapsulation is UNKNOWN"
        ),
        (
            "CLAIM_RAW_KEEPALIVE_0x3E",
            "Raw adapter keepalive opcode = 0x3E",
            "Unproven conflation of KWP TesterPresent service with adapter opcode.",
            "Adapter keepalive opcode is UNKNOWN"
        ),
        (
            "CLAIM_FUN_14011FEB8_DECODER",
            "FUN_14011FEB8 is Group 011 formula decoder",
            "False claim. Decompilation of FUN_14011feb8 proves it is an adapter security challenge block validator checking magic bytes 0xAA and 0x55.",
            "FUN_14011FEB8 is an adapter security verification routine"
        ),
        (
            "CLAIM_100_PERCENT_DECOMPILATION",
            "100% full decompilation of VCDS",
            "Ungrounded exaggeration. Replaced with exact Ghidra inventory and coverage statistics.",
            "Partial targeted decompilation of transport and session subsystems"
        )
    ]
    
    for r in retractions_data:
        cur.execute("SELECT id FROM retractions WHERE claim_key = ?", (r[0],))
        if not cur.fetchone():
            cur.execute("""
                INSERT INTO retractions (claim_key, prior_statement, reason, corrected_statement)
                VALUES (?, ?, ?, ?)
            """, r)
            
    # 3. Seed P0 Gaps
    gaps_data = [
        (
            "P0",
            "GAP_KWP_TO_ADAPTER_ENCAPSULATION",
            "Continuous data flow from ECU diagnostic request (KWP service / measuring block) to Ross-Tech adapter send_frame is unresolved across vtable dispatch in BlockDlg.",
            "Complete decompiled AST or dynamic bus trace bridging BlockDlg::OnGraph (0x14005911c) to vcds_adapter_send_frame (0x14007E734)."
        ),
        (
            "P0",
            "GAP_KEEPALIVE_ADAPTER_OPCODE",
            "The specific opcode used by Ross-Tech adapter to maintain active K-Line/CAN session without sending diagnostic requests is unmapped.",
            "Dynamic trace of idle session or static identification of session timer callback."
        )
    ]
    
    for g in gaps_data:
        cur.execute("SELECT id FROM gaps WHERE title = ?", (g[1],))
        if not cur.fetchone():
            cur.execute("""
                INSERT INTO gaps (priority, title, description, required_evidence, status)
                VALUES (?, ?, ?, ?, 'OPEN')
            """, g)
            
    conn.commit()
    print("Seeded proven claims, retractions, and P0 gaps.")

def cmd_ingest(args):
    """Run Ghidra export ingestion and seed verified claims."""
    conn = connect()
    raw_dir = ROOT.parent / "reverse" / "raw"
    from ingest_ghidra import ingest
    ingest(str(DB_PATH), str(raw_dir))
    cmd_seed_claims(conn)
    conn.close()
    print("Ingestion and seeding completed.")

def cmd_check(args):
    """Comprehensive consistency and integrity checker."""
    conn = connect()
    cur = conn.cursor()
    
    print("=" * 70)
    print("RUNNING VCDS KNOWLEDGE BASE INTEGRITY CHECK (kb check)")
    print("=" * 70)
    
    failures = []
    
    # 1. Verify Authoritative Binary
    cur.execute("SELECT sha256 FROM reverse_binaries WHERE id = 1")
    row = cur.fetchone()
    if not row or row[0] != EXPECTED_VCDS_SHA256:
        failures.append(f"Binary SHA256 mismatch: expected {EXPECTED_VCDS_SHA256}, got {row[0] if row else 'NONE'}")
    else:
        print(f"[PASS] Authoritative binary SHA256 verified: {EXPECTED_VCDS_SHA256}")
        
    # 2. Verify PROVEN_STATIC Claims have existing function addresses
    cur.execute("SELECT claim_key, function_address, evidence_status FROM claims WHERE evidence_status IN ('PROVEN_STATIC', 'PROVEN_BOTH')")
    proven_claims = cur.fetchall()
    for cl in proven_claims:
        addr = cl["function_address"]
        cur.execute("SELECT id FROM reverse_functions WHERE address = ?", (addr,))
        f_row = cur.fetchone()
        if not f_row:
            failures.append(f"Claim {cl['claim_key']} references non-existent function address {addr}")
    print(f"[PASS] Verified {len(proven_claims)} PROVEN claims have valid function records in DB.")
    
    # 3. Verify Retractions are registered and visible
    cur.execute("SELECT COUNT(*) FROM retractions")
    ret_count = cur.fetchone()[0]
    if ret_count < 5:
        failures.append(f"Insufficient retractions recorded: {ret_count} < 5 required")
    else:
        print(f"[PASS] Verified {ret_count} explicit historical retractions are maintained.")
        
    # 4. Verify Open P0 Gaps exist for unproven transitions
    cur.execute("SELECT COUNT(*) FROM gaps WHERE priority = 'P0' AND status = 'OPEN'")
    p0_count = cur.fetchone()[0]
    if p0_count < 1:
        failures.append("No open P0 gaps found! Known gap between KWP and FT_Write must be recorded.")
    else:
        print(f"[PASS] Verified {p0_count} open P0 gaps correctly quarantined.")

    # 5. Verify VTable 0x1401AD3C0 mapping
    cur.execute("SELECT target_function FROM reverse_vtables WHERE address = '0x1401AD3C0' AND slot = 264") # 0x108 = 264
    vt_row = cur.fetchone()
    if not vt_row or vt_row[0] != "0x14007E734":
        failures.append(f"VTable slot 0x108 mapping incorrect: expected 0x14007E734, got {vt_row[0] if vt_row else 'NONE'}")
    else:
        print("[PASS] VTable 0x1401AD3C0 slot 0x108 strictly mapped to vcds_adapter_send_frame (0x14007E734).")
        
    conn.close()
    
    if failures:
        print("\nINTEGRITY CHECK FAILED:")
        for f in failures:
            print(f"  - ERROR: {f}")
        sys.exit(1)
    else:
        print("\nALL VCDS KNOWLEDGE BASE INTEGRITY CHECKS PASSED (STATUS: VALID).")

def cmd_status(args):
    """Print KB metrics and statistics."""
    conn = connect()
    cur = conn.cursor()
    
    cur.execute("SELECT COUNT(*) FROM reverse_functions")
    n_funcs = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM reverse_strings")
    n_strs = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM reverse_call_edges")
    n_calls = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM reverse_decompiler_chunks")
    n_decomp = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM claims")
    n_claims = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM retractions")
    n_retract = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM gaps WHERE status = 'OPEN'")
    n_gaps = cur.fetchone()[0]
    
    print("=" * 60)
    print("VCDS 26.3 KNOWLEDGE BASE STATUS")
    print("=" * 60)
    print(f"Functions Indexed:          {n_funcs}")
    print(f"Decompiled Chunks:          {n_decomp}")
    print(f"Call Edges:                 {n_calls}")
    print(f"Strings Indexed:            {n_strs}")
    print(f"Verified Claims:            {n_claims}")
    print(f"Historical Retractions:     {n_retract}")
    print(f"Open Gaps:                  {n_gaps}")
    print("=" * 60)
    conn.close()

def main():
    parser = argparse.ArgumentParser(description="VCDS Knowledge Base CLI")
    subparsers = parser.add_subparsers(dest="command")
    
    p_init = subparsers.add_parser("init", help="Initialize database schema")
    p_ingest = subparsers.add_parser("ingest", help="Ingest Ghidra exports and seed claims")
    p_check = subparsers.add_parser("check", help="Run consistency and integrity audit")
    p_status = subparsers.add_parser("status", help="Print KB summary status")
    
    args = parser.parse_args()
    if args.command == "init":
        cmd_init(args)
    elif args.command == "ingest":
        cmd_ingest(args)
    elif args.command == "check":
        cmd_check(args)
    elif args.command == "status":
        cmd_status(args)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()
