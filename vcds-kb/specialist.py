#!/usr/bin/env python3
"""
VCDS Knowledge Base Specialist (specialist.py)
AI Agent retrieval and evidence resolution assistant.
Provides structured answers citing verified database records, without hardcoding mutable facts.
"""

import json
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "vcds_kb.db"

def query_kb_for_agent(question_type: str, arg: str = None) -> dict:
    """
    Structured query interface for agentic reasoning and Pair Programming assistants.
    """
    if not DB_PATH.exists():
        return {"error": "Database not initialized. Run kb.py init & ingest."}
        
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    
    result = {}
    
    if question_type == "binary":
        cur.execute("SELECT path, sha256, version, architecture, image_base FROM reverse_binaries WHERE id = 1")
        row = cur.fetchone()
        result = dict(row) if row else {}
        
    elif question_type == "proven_claims":
        cur.execute("""
            SELECT claim_key, statement, evidence_status, function_address, callsite, provenance 
            FROM claims WHERE evidence_status IN ('PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH')
        """)
        result = {"claims": [dict(r) for r in cur.fetchall()]}
        
    elif question_type == "retractions":
        cur.execute("SELECT claim_key, prior_statement, reason, corrected_statement FROM retractions")
        result = {"retractions": [dict(r) for r in cur.fetchall()]}
        
    elif question_type == "gaps":
        cur.execute("SELECT priority, title, description, required_evidence FROM gaps WHERE status = 'OPEN'")
        result = {"open_gaps": [dict(r) for r in cur.fetchall()]}
        
    elif question_type == "address":
        clean_addr = f"0x{arg.replace('0x', '').replace('0X', '').upper()}"
        cur.execute("""
            SELECT f.address, f.rva, f.original_name, f.assigned_name, f.semantic_category, f.semantic_status,
                   d.decompiler_text, d.assembly_text
            FROM reverse_functions f
            LEFT JOIN reverse_decompiler_chunks d ON f.id = d.function_id
            WHERE f.address = ?
        """, (clean_addr,))
        row = cur.fetchone()
        result = dict(row) if row else {"error": f"Address {clean_addr} not found"}
        
    conn.close()
    return result

def main():
    if len(sys.argv) < 2:
        print("Usage: python specialist.py <binary|proven_claims|retractions|gaps|address> [addr]")
        sys.exit(1)
        
    q_type = sys.argv[1]
    arg = sys.argv[2] if len(sys.argv) > 2 else None
    res = query_kb_for_agent(q_type, arg)
    print(json.dumps(res, indent=2))

if __name__ == "__main__":
    main()
