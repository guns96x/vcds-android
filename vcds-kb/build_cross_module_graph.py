#!/usr/bin/env python3
"""
Cross-Module Dependency & Resource Graph Engine.
Builds the complete static and dynamic dependency graph across all VCDS PE modules
and indexes non-PE diagnostic databases (.rod, .clb, .lbl).
"""

import json
import sqlite3
from pathlib import Path
import pefile

ROOT = Path(__file__).resolve().parent.parent
DB_PATH = ROOT / "vcds-kb" / "vcds_kb.db"
VCDS_DIR = Path(r"C:\Ross-Tech\VCDS")
INVENTORY_JSONL = ROOT / "reverse" / "installation_inventory.jsonl"
OUTPUT_GRAPH_JSON = ROOT / "reverse" / "cross_module_graph.json"

def build_graph():
    print("Building cross-module graph...")
    edges = []
    
    # 1. Static Import Analysis for all PEs in VCDS directory
    for pe_path in sorted(VCDS_DIR.glob("*")):
        if pe_path.suffix.lower() not in [".exe", ".dll"]:
            continue
        try:
            pe = pefile.PE(str(pe_path), fast_load=True)
            pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_IMPORT"]])
            if hasattr(pe, "DIRECTORY_ENTRY_IMPORT"):
                for entry in pe.DIRECTORY_ENTRY_IMPORT:
                    dll_name = entry.dll.decode("utf-8", errors="ignore")
                    for imp in entry.imports:
                        sym = imp.name.decode("utf-8", errors="ignore") if imp.name else f"ORD_{imp.ordinal}"
                        
                        # Check if internal VCDS or external OS
                        is_internal = any(k in dll_name.lower() for k in ["rtus", "rt-usb", "hidapi"])
                        status = "PROVEN_STATIC" if is_internal else "RAW"
                        
                        edges.append({
                            "from_module": pe_path.name,
                            "import_or_call": sym,
                            "to_module": dll_name,
                            "symbol_name": sym,
                            "dispatch_type": "STATIC_IMPORT",
                            "evidence_status": status,
                            "details": f"PE IAT entry in {pe_path.name}"
                        })
        except Exception as e:
            print(f"Notice: Could not parse imports for {pe_path.name}: {e}")
            
    # 2. Known Proven Inter-Module Links
    proven_links = [
        {
            "from_module": "VCDS.EXE",
            "import_or_call": "vcds_ftdi_write_buffer",
            "to_module": "RTUS64.dll",
            "symbol_name": "FT_Write",
            "dispatch_type": "STATIC_IMPORT",
            "evidence_status": "PROVEN_STATIC",
            "details": "Import pointer at 0x14018C860 verified against RTUS64.dll Ordinal 4"
        },
        {
            "from_module": "VCDS.EXE",
            "import_or_call": "vcds_ftdi_read_byte",
            "to_module": "RTUS64.dll",
            "symbol_name": "FT_Read",
            "dispatch_type": "STATIC_IMPORT",
            "evidence_status": "PROVEN_STATIC",
            "details": "Import pointer at 0x14018C858 verified against RTUS64.dll Ordinal 3"
        },
        {
            "from_module": "VCDS.EXE",
            "import_or_call": "vcds_ftdi_enumerate_and_open",
            "to_module": "RTUS64.dll",
            "symbol_name": "FT_SetLatencyTimer",
            "dispatch_type": "STATIC_IMPORT",
            "evidence_status": "PROVEN_STATIC",
            "details": "Called via (*DAT_14018c878)(h, 1) in FUN_140111f40"
        },
        {
            "from_module": "VCDS.EXE",
            "import_or_call": "BlockDlg::OnGraph",
            "to_module": "VCScope.exe",
            "symbol_name": "IPC_PIPE",
            "dispatch_type": "IPC_PIPE",
            "evidence_status": "PROVEN_STATIC",
            "details": "Spawns VCSCOPE.EXE passing MB-PIPE.TXT at FUN_14005911c"
        },
        {
            "from_module": "VCDS.EXE",
            "import_or_call": "vcds_ftdi_enumerate_and_open",
            "to_module": "RT-USB.dll",
            "symbol_name": "LoadLibraryA",
            "dispatch_type": "DYNAMIC_LOADLIBRARY",
            "evidence_status": "PROVEN_STATIC",
            "details": "Dynamic 32-bit driver fallback in FUN_140111f40"
        },
        {
            "from_module": "VCIConfig.exe",
            "import_or_call": "hid_open",
            "to_module": "hidapi.dll",
            "symbol_name": "hid_open",
            "dispatch_type": "STATIC_IMPORT",
            "evidence_status": "PROVEN_STATIC",
            "details": "HID API interface for Ross-Tech HEX-NET/HEX-V2 USB configuration"
        }
    ]
    edges.extend(proven_links)
    
    # 3. Non-PE Resources Indexing
    non_pe_records = []
    if INVENTORY_JSONL.exists():
        with open(INVENTORY_JSONL, "r", encoding="utf-8") as f:
            for line in f:
                rec = json.loads(line)
                if not rec.get("is_pe"):
                    ext = rec.get("extension", "")
                    rel = rec.get("relative_path", "")
                    res_type = rec.get("role", "RESOURCE")
                    
                    if ext == ".rod":
                        diag_role = "UDS_ASAM_ODX_DIAGNOSTIC_DESCRIPTION"
                        cons = "VCDS.EXE"
                        cons_func = "FUN_14001d4d4"
                    elif ext == ".clb":
                        diag_role = "ENCRYPTED_MEASURING_BLOCKS_AND_CODING_LABELS"
                        cons = "VCDS.EXE"
                        cons_func = "FUN_14005911c"
                    elif ext == ".lbl":
                        diag_role = "LEGACY_PLAINTEXT_DIAGNOSTIC_LABELS"
                        cons = "VCDS.EXE"
                        cons_func = "FUN_14005911c"
                    elif ext in [".cfg", ".ini"]:
                        diag_role = "HARDWARE_AND_USER_PREFERENCES"
                        cons = "VCDS.EXE"
                        cons_func = "FUN_140111f40"
                    else:
                        continue
                        
                    non_pe_records.append({
                        "resource_path": rel,
                        "resource_type": res_type,
                        "consumer_module": cons,
                        "consumer_function": cons_func,
                        "diagnostic_role": diag_role,
                        "xref_evidence": f"Indexed from installation inventory ({rec.get('size')} bytes)"
                    })
                    
    print(f"Discovered {len(edges)} cross-module edges and {len(non_pe_records)} non-PE resource bindings.")
    
    # Save JSON summary
    OUTPUT_GRAPH_JSON.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_GRAPH_JSON, "w", encoding="utf-8") as out_f:
        json.dump({
            "total_edges": len(edges),
            "total_non_pe_resources": len(non_pe_records),
            "sample_edges": edges[:50],
            "non_pe_summary_by_type": {
                "rod_databases": len([r for r in non_pe_records if ".rod" in r["resource_path"]]),
                "clb_encrypted_labels": len([r for r in non_pe_records if ".clb" in r["resource_path"]]),
                "lbl_plaintext_labels": len([r for r in non_pe_records if ".lbl" in r["resource_path"]]),
                "cfg_ini_configs": len([r for r in non_pe_records if any(x in r["resource_path"] for x in [".cfg", ".ini"])])
            }
        }, out_f, indent=2)
        
    # Write to SQLite
    if DB_PATH.exists():
        conn = sqlite3.connect(str(DB_PATH))
        cur = conn.cursor()
        cur.execute("DELETE FROM cross_module_edges")
        for e in edges:
            cur.execute("""
                INSERT INTO cross_module_edges 
                (from_module, import_or_call, to_module, symbol_name, dispatch_type, evidence_status, details)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """, (e["from_module"], e["import_or_call"], e["to_module"], e["symbol_name"], e["dispatch_type"], e["evidence_status"], e["details"]))
            
        cur.execute("DELETE FROM non_pe_resources")
        # Ingest representative sample of non-pe resources into DB to keep size manageable
        for r in non_pe_records[:2000]: # Top 2000 representative resources
            cur.execute("""
                INSERT OR REPLACE INTO non_pe_resources 
                (resource_path, resource_type, consumer_module, consumer_function, diagnostic_role, xref_evidence)
                VALUES (?, ?, ?, ?, ?, ?)
            """, (r["resource_path"], r["resource_type"], r["consumer_module"], r["consumer_function"], r["diagnostic_role"], r["xref_evidence"]))
            
        conn.commit()
        conn.close()
        print(f"Updated SQLite database with cross_module_edges and non_pe_resources.")

    return len(edges), len(non_pe_records)

if __name__ == "__main__":
    build_graph()
