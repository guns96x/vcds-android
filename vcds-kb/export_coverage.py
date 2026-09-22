#!/usr/bin/env python3
"""
Generate reverse/COVERAGE.json strictly from canonical vcds_kb.db evidence.
Zero hardcoding, zero fallback minimums, zero synthetic success.
"""

import json
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DB_PATH = ROOT / "vcds_kb.db"
OUTPUT_PATH = ROOT.parent / "reverse" / "COVERAGE.json"

def compute_coverage():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    cur = conn.cursor()
    
    # Installation Inventory
    cur.execute("SELECT COUNT(*) FROM installation_inventory")
    n_install_files = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM installation_inventory WHERE is_pe = 1")
    n_pe_modules = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM installation_inventory WHERE is_pe = 1 AND analysis_status LIKE '%ANALYZED%'")
    n_pe_analyzed = cur.fetchone()[0]
    
    # Functions & Accounting
    cur.execute("SELECT COUNT(*) FROM reverse_functions")
    n_funcs = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_decompiler_chunks")
    n_decomp = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM functions_failed")
    n_failed = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE accounting_status = 'SKIPPED_WITH_REASON'")
    n_skipped = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE accounting_status != 'UNKNOWN'")
    n_accounted = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE semantic_category != 'UNKNOWN'")
    n_classified = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE semantic_status IN ('PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH')")
    n_verified = cur.fetchone()[0]
    
    # Calls & Indirect Calls
    cur.execute("SELECT COUNT(*) FROM reverse_call_edges WHERE dispatch_type = 'DIRECT'")
    n_direct = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_call_edges WHERE dispatch_type IN ('INDIRECT', 'VTABLE', 'FUNCTION_POINTER')")
    n_indirect_total = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_call_edges WHERE dispatch_type IN ('INDIRECT', 'VTABLE', 'FUNCTION_POINTER') AND is_resolved = 1")
    n_indirect_resolved = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_call_edges WHERE dispatch_type IN ('INDIRECT', 'VTABLE', 'FUNCTION_POINTER') AND is_resolved = 0")
    n_indirect_unresolved = cur.fetchone()[0]
    
    # VTables
    cur.execute("SELECT COUNT(DISTINCT address) FROM reverse_vtables")
    n_vtables = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_vtables")
    n_vtable_slots = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_vtables WHERE target_function IS NOT NULL AND target_function != ''")
    n_vtable_slots_resolved = cur.fetchone()[0]
    
    # Protocols & Transport
    cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE semantic_category = 'TRANSPORT' AND semantic_status IN ('PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH')")
    n_transport = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_constants WHERE classification = 'OPCODE'")
    n_opcodes = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_protocol_frames WHERE layer = 'ADAPTER_PROTOCOL' AND evidence_status IN ('PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH')")
    n_opcodes_verified = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_constants WHERE classification = 'ECU_SERVICE_ID'")
    n_services = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM reverse_transform_edges WHERE evidence_status IN ('PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH')")
    n_transforms = cur.fetchone()[0]
    
    # Cross-Module & Resources
    cur.execute("SELECT COUNT(*) FROM cross_module_edges")
    n_cross_edges = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM non_pe_resources")
    n_resources = cur.fetchone()[0]
    
    # Runtime & Gaps
    cur.execute("SELECT COUNT(*) FROM reverse_runtime_events")
    n_runtime = cur.fetchone()[0]
    
    cur.execute("SELECT COUNT(*) FROM gaps WHERE priority = 'P0' AND status = 'OPEN'")
    n_p0 = cur.fetchone()[0]
    
    metrics = {
        "installation_files_total": n_install_files,
        "installation_pe_modules_total": n_pe_modules,
        "installation_pe_modules_analyzed": n_pe_analyzed,
        "binary_functions_total": n_funcs,
        "functions_accounted": n_accounted,
        "functions_decompiled": n_decomp,
        "functions_failed": n_failed,
        "functions_skipped_with_reason": n_skipped,
        "functions_classified": n_classified,
        "functions_semantically_verified": n_verified,
        "direct_calls_resolved": n_direct,
        "indirect_calls_total": n_indirect_total,
        "indirect_calls_resolved_with_evidence": n_indirect_resolved,
        "indirect_calls_unresolved": n_indirect_unresolved,
        "vtables_total": n_vtables,
        "vtable_slots_total": n_vtable_slots,
        "vtable_slots_resolved": n_vtable_slots_resolved,
        "cross_module_edges": n_cross_edges,
        "non_pe_resources_indexed": n_resources,
        "transport_functions_verified": n_transport,
        "adapter_opcodes_found": n_opcodes,
        "adapter_opcodes_verified": n_opcodes_verified,
        "diagnostic_services_found": n_services,
        "diagnostic_services_mapped_to_transport": n_transforms,
        "runtime_actions_traced": n_runtime,
        "open_P0_gaps": n_p0
    }
    
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
        json.dump(metrics, f, indent=2)
        
    print(f"Generated {OUTPUT_PATH} with {len(metrics)} strict evidence-backed metrics.")
    conn.close()
    return metrics

if __name__ == "__main__":
    compute_coverage()
