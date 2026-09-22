#!/usr/bin/env python3
"""
Full Installation Inventory Builder for C:\\Ross-Tech\\VCDS\\
Recursively hashes, inspects, and catalogs every file in the VCDS installation per KB-1R.
"""

import hashlib
import json
import os
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VCDS_DIR = Path(r"C:\Ross-Tech\VCDS")
INVENTORY_JSONL = ROOT / "reverse" / "installation_inventory.jsonl"
INVENTORY_SUMMARY = ROOT / "reverse" / "installation_summary.json"

def get_sha256(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest().upper()

def inspect_pe(filepath):
    try:
        with open(filepath, "rb") as f:
            header = f.read(1024)
        if len(header) < 64 or header[:2] != b"MZ":
            return False, None, None
        e_lfanew = struct.unpack("<I", header[0x3C:0x40])[0]
        with open(filepath, "rb") as f:
            f.seek(e_lfanew)
            pe_sig = f.read(4)
            if pe_sig != b"PE\x00\x00":
                return False, None, None
            coff = f.read(20)
            machine, num_sections = struct.unpack("<HH", coff[:4])
            
            arch = "UNKNOWN"
            if machine == 0x8664:
                arch = "x86_64"
            elif machine == 0x014C:
                arch = "x86"
            elif machine == 0xAA64:
                arch = "arm64"
                
            # Try to get PE version string from pefile if installed
            version = None
            try:
                import pefile
                pe = pefile.PE(filepath, fast_load=True)
                pe.parse_data_directories(directories=[pefile.DIRECTORY_ENTRY["IMAGE_DIRECTORY_ENTRY_RESOURCE"]])
                if hasattr(pe, "FileInfo"):
                    for file_info in pe.FileInfo[0]:
                        if file_info.Key == b"StringFileInfo":
                            for st in file_info.StringTable:
                                for k, v in st.entries.items():
                                    if k in (b"FileVersion", b"ProductVersion"):
                                        version = v.decode("utf-8", errors="ignore")
                                        break
            except Exception:
                pass
                
            return True, arch, version
    except Exception:
        return False, None, None

def classify_role(rel_path, is_pe):
    name = rel_path.name.lower()
    ext = rel_path.suffix.lower()
    parent = str(rel_path.parent).lower()
    
    if name == "vcds.exe":
        return "PRIMARY_DIAGNOSTIC_APPLICATION", "ORIGINAL_PACKED_BINARY"
    elif name == "rtus64.dll":
        return "D2XX_TRANSPORT_LIBRARY_64BIT", "D2XX_DRIVER_WRAPPER"
    elif name == "rt-usb.dll":
        return "D2XX_TRANSPORT_LIBRARY_32BIT", "D2XX_LEGACY_WRAPPER"
    elif name == "rt-usb64.sys":
        return "KERNEL_MODE_USB_DRIVER", "FTDI_SYS_DRIVER"
    elif name == "vcdsscan.exe":
        return "AUTOSCAN_UTILITY", "SUBSYSTEM_TOOL"
    elif name == "vciscope.exe" or name == "vcscope.exe":
        return "OSCILLOSCOPE_GRAPHING_TOOL", "SUBSYSTEM_TOOL"
    elif name == "tdigraph.exe":
        return "TDI_TIMING_GRAPH_TOOL", "SUBSYSTEM_TOOL"
    elif name == "lcode.exe" or name == "lcode-classic.exe":
        return "LONG_CODING_HELPER", "SUBSYSTEM_TOOL"
    elif name == "vciconfig.exe":
        return "VCI_FIRMWARE_CONFIGURATOR", "HARDWARE_CONFIG_TOOL"
    elif name == "dpinst.exe":
        return "DRIVER_INSTALLER_PACKAGE", "SETUP_SUPPORT"
    elif name == "uninstall.exe":
        return "UNINSTALLER", "MAINTENANCE_TOOL"
    elif name == "hidapi.dll":
        return "USB_HID_ACCESS_LIBRARY", "THIRD_PARTY_RUNTIME"
    elif name == "csvconv.exe":
        return "CSV_CONVERTER_UTILITY", "DATA_EXPORT_TOOL"
        
    if ext == ".rod":
        return "ASAM_ODX_UDS_DATABASE", "DIAGNOSTIC_DATA_ROD"
    elif ext == ".clb":
        return "ENCRYPTED_COMPILED_LABEL_FILE", "DIAGNOSTIC_LABELS_CLB"
    elif ext == ".lbl":
        return "PLAINTEXT_LABEL_FILE", "DIAGNOSTIC_LABELS_LBL"
    elif ext in [".cfg", ".ini"]:
        return "CONFIGURATION_FILE", "SYSTEM_SETTINGS"
    elif ext in [".chm", ".pdf"]:
        return "DOCUMENTATION_MANUAL", "HELP_DOC"
    elif ext in [".txt"]:
        return "DOCUMENTATION_OR_LOG", "TEXT_FILE"
    else:
        return "SUPPORT_RESOURCE", "MISC_RESOURCE"

def build_inventory():
    print(f"Scanning {VCDS_DIR}...")
    INVENTORY_JSONL.parent.mkdir(parents=True, exist_ok=True)
    
    records = []
    pe_modules = []
    ext_counts = {}
    
    all_files = sorted(list(VCDS_DIR.rglob("*")))
    total_files = len([p for p in all_files if p.is_file()])
    print(f"Found {total_files} total files. Calculating SHA256 and metadata...")
    
    count = 0
    with open(INVENTORY_JSONL, "w", encoding="utf-8") as out_f:
        for p in all_files:
            if not p.is_file():
                continue
            count += 1
            rel = p.relative_to(VCDS_DIR)
            sz = p.stat().st_size
            ext = p.suffix.lower()
            ext_counts[ext] = ext_counts.get(ext, 0) + 1
            
            sha = get_sha256(p)
            is_pe, arch, ver = inspect_pe(p)
            role, notes = classify_role(rel, is_pe)
            
            # Determine analysis status
            if is_pe:
                analysis_status = "PENDING_GHIDRA_ANALYSIS"
                if rel.name.lower() in ["rtus64.dll"]:
                    analysis_status = "PROVEN_STATIC_VERIFIED"
                elif rel.name.lower() in ["vcds.exe"]:
                    analysis_status = "ORIGINAL_TARGET_UNPACKED_ANALYZED"
            else:
                analysis_status = "INDEXED_DATA_RESOURCE"
                
            rec = {
                "relative_path": str(rel).replace("\\", "/"),
                "filename": p.name,
                "extension": ext,
                "size": sz,
                "sha256": sha,
                "mime_type": "application/x-dosexec" if is_pe else ("text/plain" if ext in [".txt", ".lbl", ".ini", ".cfg"] else "application/octet-stream"),
                "is_pe": is_pe,
                "architecture": arch,
                "version": ver,
                "role": role,
                "analysis_status": analysis_status,
                "referenced_by": "VCDS.EXE" if ext in [".rod", ".clb", ".lbl", ".cfg", ".ini", ".dll"] else "USER_MANUAL",
                "opened_by_runtime": True if rel.name.lower() in ["rtus64.dll", "vcds.exe"] else False,
                "notes": notes
            }
            
            out_f.write(json.dumps(rec) + "\n")
            records.append(rec)
            if is_pe:
                pe_modules.append(rec)
                
            if count % 1000 == 0 or count == total_files:
                print(f"Processed {count}/{total_files} files...")
                
    summary = {
        "installation_path": str(VCDS_DIR),
        "total_files": len(records),
        "total_pe_modules": len(pe_modules),
        "pe_modules": [m["relative_path"] for m in pe_modules],
        "extensions_summary": ext_counts
    }
    with open(INVENTORY_SUMMARY, "w", encoding="utf-8") as sf:
        json.dump(summary, sf, indent=2)
        
    print(f"Successfully generated {INVENTORY_JSONL} ({len(records)} files) and {INVENTORY_SUMMARY}.")
    return summary

if __name__ == "__main__":
    build_inventory()
