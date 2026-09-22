#!/usr/bin/env python3
"""
VCDS 26.3 Frame Builder & Response Parser Reference Model and Vector Generator.
Source Functions:
  - Frame Builder: FUN_14007e734 @ VA 0x14007E734
  - Response Parser: FUN_14007e824 @ VA 0x14007E824
"""

import json
import os
import sys

def build_vcds_request_frame(payload_bytes: bytes) -> bytes:
    """
    Exact implementation of FUN_14007e734.
    payload_bytes: raw payload (including opcode at byte 0).
    """
    payload_len = len(payload_bytes)
    if payload_len >= 0x51:
        raise ValueError(f"Payload length {payload_len} exceeds maximum allowed (80 bytes)")
        
    frame = bytearray()
    frame.append(0x53)  # 'S'
    total_len = payload_len + 3
    frame.append(total_len)
    
    xor_sum = 0x53 ^ total_len
    for b in payload_bytes:
        frame.append(b)
        xor_sum ^= b
        
    frame.append(xor_sum)
    return bytes(frame)

def parse_vcds_response_frame(frame: bytes) -> dict:
    """
    Exact implementation of FUN_14007e824.
    Returns dict with status, error code, and extracted payload.
    """
    if len(frame) == 0:
        return {"status": "ERROR", "code": -1, "error": "EMPTY_BUFFER"}
        
    sync = frame[0]
    if sync != 0x4D:  # 'M'
        return {"status": "ERROR", "code": -1, "error": "INVALID_SYNC", "actual_sync": f"0x{sync:02X}"}
        
    if len(frame) < 2:
        return {"status": "ERROR", "code": -4, "error": "TRUNCATED_AT_LENGTH"}
        
    length_byte = frame[1]
    if length_byte >= 0x31:
        return {"status": "ERROR", "code": -3, "error": "LENGTH_EXCEEDS_MAX_0x30", "length_byte": length_byte}
        
    if length_byte < 3:
        return {"status": "ERROR", "code": -2, "error": "LENGTH_LESS_THAN_MIN_3", "length_byte": length_byte}
        
    if len(frame) < length_byte:
        return {
            "status": "ERROR", 
            "code": -4, 
            "error": "TRUNCATED_FRAME", 
            "expected_len": length_byte, 
            "actual_len": len(frame)
        }
        
    # Checksum verification: cumulative XOR over all length_byte bytes must be 0
    xor_sum = 0
    for b in frame[:length_byte]:
        xor_sum ^= b
        
    if xor_sum != 0:
        return {
            "status": "ERROR", 
            "code": -5, 
            "error": "CHECKSUM_MISMATCH", 
            "xor_sum": f"0x{xor_sum:02X}"
        }
        
    # Extracted payload: frame[2 : length_byte - 1], size is length_byte - 3
    payload = frame[2 : length_byte - 1]
    return {
        "status": "SUCCESS",
        "code": 0,
        "total_length": length_byte,
        "payload_length": len(payload),
        "payload_hex": payload.hex().upper(),
        "opcode_hex": f"0x{payload[0]:02X}" if payload else None,
        "checksum_byte": f"0x{frame[length_byte - 1]:02X}"
    }

def main():
    print("=" * 70)
    print("VCDS 26.3 FRAME BUILDER & RESPONSE PARSER AUDIT & TEST SUITE")
    print("=" * 70)
    
    # 1. Framing Test Vectors (Requests)
    framing_tests = [
        {
            "name": "GET_VERSION (Opcode 0x02)",
            "payload": bytes([0x02]),
            "expected_wire": bytes([0x53, 0x04, 0x02, 0x55]),
            "source_fn": "FUN_14007e988",
            "source_addr": "0x14007E988"
        },
        {
            "name": "SET_COM_BAUD_115200 (Opcode 0x03)",
            "payload": bytes([0x03, 0x00, 0xC2, 0x01, 0x00]),
            "expected_wire": bytes([0x53, 0x08, 0x03, 0x00, 0xC2, 0x01, 0x00, 0x9B]),
            "source_fn": "FUN_14007ec2c",
            "source_addr": "0x14007EC2C"
        },
        {
            "name": "5BAUD_INIT_ECU01 (Opcode 0x84)",
            "payload": bytes([0x84, 0x03, 0x01, 0x00]),
            "expected_wire": bytes([0x53, 0x07, 0x84, 0x03, 0x01, 0x00, 0xD2]),
            "source_fn": "FUN_14007e3b4",
            "source_addr": "0x14007E3B4"
        },
        {
            "name": "5BAUD_INIT_ECU03_ABS (Opcode 0x84)",
            "payload": bytes([0x84, 0x03, 0x83, 0x00]),
            "expected_wire": bytes([0x53, 0x07, 0x84, 0x03, 0x83, 0x00, 0x50]),
            "source_fn": "FUN_14007e3b4",
            "source_addr": "0x14007E3B4"
        },
        {
            "name": "KLINE_TEST (Opcode 0x82)",
            "payload": bytes([0x82]),
            "expected_wire": bytes([0x53, 0x04, 0x82, 0xD5]),
            "source_fn": "FUN_14007ed84",
            "source_addr": "0x14007ED84"
        },
        {
            "name": "RESET (Opcode 0x08)",
            "payload": bytes([0x08]),
            "expected_wire": bytes([0x53, 0x04, 0x08, 0x5F]),
            "source_fn": "FUN_14007f758",
            "source_addr": "0x14007F758"
        },
        {
            "name": "CONFIRM_ACK (Opcode 0xFE)",
            "payload": bytes([0xFE]),
            "expected_wire": bytes([0x53, 0x04, 0xFE, 0xA9]),
            "source_fn": "FUN_14007ec2c",
            "source_addr": "0x14007EC2C"
        }
    ]
    
    framing_results = []
    for test in framing_tests:
        built = build_vcds_request_frame(test["payload"])
        assert built == test["expected_wire"], f"Mismatch in {test['name']}: {built.hex()} != {test['expected_wire'].hex()}"
        res = {
            "test_name": test["name"],
            "payload_hex": test["payload"].hex().upper(),
            "payload_len": len(test["payload"]),
            "wire_hex": built.hex().upper(),
            "wire_physical_bytes": len(built),
            "length_byte_value": built[1],
            "checksum_byte": f"0x{built[-1]:02X}",
            "source_function": test["source_fn"],
            "source_address": test["source_addr"],
            "status": "PROVEN_STATIC"
        }
        framing_results.append(res)
        print(f"[BUILDER OK] {res['test_name']:35s} -> Wire: {res['wire_hex']} (Len byte: {res['length_byte_value']}, Physical: {res['wire_physical_bytes']})")

    # 2. Response Parser Test Vectors
    parser_tests = [
        {
            "name": "VALID_VERSION_RESPONSE",
            # 4D 07 02 01 60 44 Checksum
            # Checksum: 0x4D ^ 0x07 ^ 0x02 ^ 0x01 ^ 0x60 ^ 0x44 = 0x6D
            "frame": bytes([0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D]),
            "expected_status": "SUCCESS",
            "expected_payload": "02016044",
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "VALID_CONFIRM_ACK",
            # 4D 04 FE Checksum
            # Checksum: 0x4D ^ 0x04 ^ 0xFE = 0xB7
            "frame": bytes([0x4D, 0x04, 0xFE, 0xB7]),
            "expected_status": "SUCCESS",
            "expected_payload": "FE",
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "VALID_READY_RESP",
            # 4D 04 FD Checksum
            # Checksum: 0x4D ^ 0x04 ^ 0xFD = 0xB4
            "frame": bytes([0x4D, 0x04, 0xFD, 0xB4]),
            "expected_status": "SUCCESS",
            "expected_payload": "FD",
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "INVALID_SYNC_BYTE",
            "frame": bytes([0x53, 0x04, 0xFE, 0xA9]),
            "expected_status": "ERROR",
            "expected_code": -1,
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "INVALID_LENGTH_EXCEEDS_MAX",
            # Length byte = 0x35 > 0x30
            "frame": bytes([0x4D, 0x35] + [0x00]*52),
            "expected_status": "ERROR",
            "expected_code": -3,
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "INVALID_CHECKSUM",
            # Corrupted last byte: 0x72 instead of 0x73
            "frame": bytes([0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x72]),
            "expected_status": "ERROR",
            "expected_code": -5,
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        },
        {
            "name": "TRUNCATED_FRAME",
            # Length says 7, but only 5 bytes present
            "frame": bytes([0x4D, 0x07, 0x02, 0x01, 0x60]),
            "expected_status": "ERROR",
            "expected_code": -4,
            "source_fn": "FUN_14007e824",
            "source_addr": "0x14007E824"
        }
    ]

    parser_results = []
    for test in parser_tests:
        res = parse_vcds_response_frame(test["frame"])
        assert res["status"] == test["expected_status"], f"Status mismatch in {test['name']}: {res}"
        if test["expected_status"] == "SUCCESS":
            assert res["payload_hex"] == test["expected_payload"]
        else:
            assert res["code"] == test["expected_code"]
            
        entry = {
            "test_name": test["name"],
            "wire_hex": test["frame"].hex().upper(),
            "wire_bytes_count": len(test["frame"]),
            "parser_result": res,
            "source_function": test["source_fn"],
            "source_address": test["source_addr"]
        }
        parser_results.append(entry)
        print(f"[PARSER OK]   {test['name']:35s} -> Status: {res['status']} (Code: {res['code']})")

    # Save to disk
    script_dir = os.path.dirname(os.path.abspath(__file__))
    framing_path = os.path.normpath(os.path.join(script_dir, "../../reverse/tests/framing_vectors.json"))
    parser_path = os.path.normpath(os.path.join(script_dir, "../../reverse/tests/parser_vectors.json"))
    frame_parser_path = os.path.normpath(os.path.join(script_dir, "../../reverse/tests/frame_parser_vectors.json"))
    
    with open(framing_path, "w", encoding="utf-8") as f:
        json.dump(framing_results, f, indent=2)
    with open(parser_path, "w", encoding="utf-8") as f:
        json.dump(parser_results, f, indent=2)
    with open(frame_parser_path, "w", encoding="utf-8") as f:
        json.dump(parser_results, f, indent=2)
        
    print(f"\nSaved test vectors to:")
    print(f" - {framing_path}")
    print(f" - {parser_path}")
    print(f" - {frame_parser_path}")

if __name__ == "__main__":
    main()
