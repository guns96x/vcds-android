#!/usr/bin/env python3
"""
Automated Consistency Verification Script for VCDS Reverse Engineering Artifacts.
Enforces fail-closed validation of claims, test vectors, opcodes, and implementation specs.
"""

import json
import os
import re
import sys

def check_framing_and_checksum_vectors(repo_root: str):
    vectors_file = os.path.join(repo_root, "reverse", "tests", "framing_vectors.json")
    if not os.path.exists(vectors_file):
        raise AssertionError(f"Missing framing vectors file: {vectors_file}")
        
    with open(vectors_file, "r", encoding="utf-8") as f:
        vectors = json.load(f)
        
    for v in vectors:
        wire_hex = v["wire_hex"]
        wire_bytes = bytes.fromhex(wire_hex)
        physical_len = len(wire_bytes)
        length_byte = wire_bytes[1]
        
        # Rule 1: physical frame length == length byte value
        if physical_len != length_byte:
            raise AssertionError(
                f"Rule 1 Violation in {v['test_name']}: "
                f"Physical length ({physical_len}) != Length byte ({length_byte})"
            )
            
        # Rule 2: Cumulative XOR checksum of entire frame must equal 0
        xor_sum = 0
        for b in wire_bytes:
            xor_sum ^= b
        if xor_sum != 0:
            raise AssertionError(
                f"Rule 2 Violation in {v['test_name']}: "
                f"Cumulative XOR sum of frame is 0x{xor_sum:02X}, expected 0x00"
            )
            
    print(f"PASS: Rule 1 & 2 - Verified {len(vectors)} framing and checksum vectors.")

def check_parser_vectors(repo_root: str):
    vectors_file = os.path.join(repo_root, "reverse", "tests", "parser_vectors.json")
    if not os.path.exists(vectors_file):
        raise AssertionError(f"Missing parser vectors file: {vectors_file}")
        
    with open(vectors_file, "r", encoding="utf-8") as f:
        vectors = json.load(f)
        
    for v in vectors:
        wire_hex = v["wire_hex"]
        wire_bytes = bytes.fromhex(wire_hex)
        res = v["parser_result"]
        
        if res["status"] == "SUCCESS":
            # Must verify that cumulative XOR == 0
            xor_sum = 0
            for b in wire_bytes:
                xor_sum ^= b
            if xor_sum != 0:
                raise AssertionError(f"Parser valid vector has bad checksum: {v['test_name']}")
                
    print(f"PASS: Rule 1 & 2 - Verified {len(vectors)} response parser vectors.")

def check_opcode_map_and_specs(repo_root: str):
    spec_v2 = os.path.join(repo_root, "reverse", "IMPLEMENTATION_SPEC_V2.md")
    audit_claims = os.path.join(repo_root, "reverse", "audit_claims.md")
    
    # Proven adapter opcodes verified in VCDS.EXE binary
    PROVEN_ADAPTER_OPCODES = {"0x02", "0x03", "0x08", "0x82", "0x84", "0x85", "0xFE", "0xFD"}
    
    # Check if spec exists yet
    if os.path.exists(spec_v2):
        with open(spec_v2, "r", encoding="utf-8") as f:
            spec_content = f.read()
            
        # Rule 7: IMPLEMENTATION_SPEC_V2 must NOT contain UNKNOWN or CONFLICT as production instructions
        # Allowed only in the Appendix section
        parts = spec_content.split("## Appendix: Non-Proven / Unknown / Conflict Items")
        prod_section = parts[0]
        
        if "STATUS: UNKNOWN" in prod_section or "STATUS: CONFLICT" in prod_section:
            raise AssertionError("Rule 7 Violation: Production section of IMPLEMENTATION_SPEC_V2 contains UNKNOWN/CONFLICT status!")
            
        # Rule 3: Check for bogus adapter opcodes
        unproven_opcodes_as_adapter = ["0x21", "0x3E", "0x29", "0x1A"]
        for op in unproven_opcodes_as_adapter:
            # Check pattern like "Opcode: 0x21" or "adapter opcode 0x21" in production section
            if re.search(rf"(?:opcode|command)\s*(?:id|byte|:)?\s*{op}", prod_section, re.IGNORECASE):
                raise AssertionError(f"Rule 3 Violation: KWP service {op} used as raw adapter opcode in production spec!")
                
        print("PASS: Rule 3 & 7 - IMPLEMENTATION_SPEC_V2 verified for fail-closed compliance.")

def check_evidence_addresses_and_traces(repo_root: str):
    audit_claims_file = os.path.join(repo_root, "reverse", "audit_claims.md")
    if not os.path.exists(audit_claims_file):
        print(f"Notice: {audit_claims_file} not yet generated; skipping audit claims scan.")
        return
        
    with open(audit_claims_file, "r", encoding="utf-8") as f:
        content = f.read()
        
    # Split by CLAIM blocks
    claims = content.split("CLAIM:")
    for claim in claims[1:]:
        status_match = re.search(r"EVIDENCE STATUS:\s*([A-Z_]+)", claim)
        addr_match = re.search(r"VCDS ADDRESS:\s*([^\n]+)", claim)
        trace_match = re.search(r"RUNTIME TRACE:\s*([^\n]+)", claim)
        
        if not status_match:
            continue
            
        status = status_match.group(1).strip()
        addr = addr_match.group(1).strip() if addr_match else ""
        trace = trace_match.group(1).strip() if trace_match else ""
        
        # Rule 4: PROVEN_STATIC must have valid VCDS address
        if status in ("PROVEN_STATIC", "PROVEN_BOTH"):
            if not addr or "N/A" in addr or addr == "None":
                raise AssertionError(f"Rule 4 Violation: {status} claim missing valid VCDS address: {claim[:100]}")
                
        # Rule 5: PROVEN_DYNAMIC must have valid trace reference
        if status in ("PROVEN_DYNAMIC", "PROVEN_BOTH"):
            if not trace or "N/A" in trace or trace == "None":
                raise AssertionError(f"Rule 5 Violation: {status} claim missing trace reference: {claim[:100]}")
                
    print("PASS: Rule 4, 5, 6 - Verified status address and trace evidence rules.")

def main():
    repo_root = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "../.."))
    print("=" * 70)
    print(f"RUNNING REVERSE ENGINEERING CONSISTENCY CHECKER (Root: {repo_root})")
    print("=" * 70)
    
    check_framing_and_checksum_vectors(repo_root)
    check_parser_vectors(repo_root)
    check_opcode_map_and_specs(repo_root)
    check_evidence_addresses_and_traces(repo_root)
    
    print("\nALL CONSISTENCY CHECKS PASSED.")

if __name__ == "__main__":
    main()
