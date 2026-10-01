#!/usr/bin/env python3
"""
VCDS 26.3 5-Baud Address Parity Unit Test & Vector Generator.
Source Function: FUN_14007e3b4 @ VA 0x14007E3B4 (lines 20-33).
"""

import json
import os
import sys

def vcds_encode_5baud_address(addr: int) -> dict:
    """
    Exact replica of VCDS 26.3 5-baud parity encoding in FUN_14007e3b4.
    
    MSVC x64 C AST:
      bVar7 = false;
      lVar6 = 8;
      bVar4 = (byte)DAT_1401f6818;
      do {
        if ((bVar4 & 1) != 0) {
          bVar7 = (bool)(bVar7 ^ 1);
        }
        bVar4 = (char)bVar4 >> 1;
        lVar6 = lVar6 + -1;
      } while (lVar6 != 0);
      uVar2 = DAT_1401f6818;
      if ((DAT_1401f6818 != 0x33) && (!bVar7)) {
        uVar2 = (uint)(byte)((byte)DAT_1401f6818 | 0x80);
      }
    """
    raw_addr = addr & 0xFF
    bVar7 = False
    bVar4 = raw_addr
    
    # 8-bit parity bit-flip accumulator
    for _ in range(8):
        if (bVar4 & 1) != 0:
            bVar7 = not bVar7
        bVar4 >>= 1
    
    # Odd parity enforcement (MSB 0x80 set if initial set bits count was even)
    uVar2 = raw_addr
    msb_added = False
    if raw_addr != 0x33 and not bVar7:
        uVar2 = raw_addr | 0x80
        msb_added = True
        
    bit_count_raw = bin(raw_addr).count("1")
    bit_count_final = bin(uVar2).count("1")
    
    return {
        "input_address_hex": f"0x{raw_addr:02X}",
        "input_address_dec": raw_addr,
        "input_bit_count": bit_count_raw,
        "initial_parity": "ODD" if bit_count_raw % 2 != 0 else "EVEN",
        "msb_0x80_applied": msb_added,
        "output_byte_hex": f"0x{uVar2:02X}",
        "output_byte_dec": uVar2,
        "final_bit_count": bit_count_final,
        "final_parity": "ODD" if bit_count_final % 2 != 0 else "EVEN",
        "source_function": "FUN_14007e3b4",
        "source_address": "0x14007E3B4"
    }

def main():
    test_cases = [0x01, 0x02, 0x03, 0x17, 0x19, 0x33, 0x08, 0x09, 0x0F, 0x15, 0x46]
    results = []
    
    print("=" * 70)
    print("VCDS 26.3 5-BAUD PARITY VERIFICATION (ODD PARITY SCHEME)")
    print("=" * 70)
    
    for tc in test_cases:
        res = vcds_encode_5baud_address(tc)
        results.append(res)
        print(f"ECU Addr: {res['input_address_hex']} ({res['input_address_dec']:2d}) | "
              f"Raw 1-bits: {res['input_bit_count']} ({res['initial_parity']:4s}) | "
              f"MSB Set: {str(res['msb_0x80_applied']):5s} -> "
              f"Output: {res['output_byte_hex']} ({res['final_parity']})")
    
    # Assert critical findings
    assert results[0]["output_byte_hex"] == "0x01", "CRITICAL ERROR: ECU 0x01 must encode to 0x01!"
    assert results[1]["output_byte_hex"] == "0x02", "CRITICAL ERROR: ECU 0x02 must encode to 0x02!"
    assert results[2]["output_byte_hex"] == "0x83", "CRITICAL ERROR: ECU 0x03 must encode to 0x83!"
    assert results[3]["output_byte_hex"] == "0x97", "CRITICAL ERROR: ECU 0x17 must encode to 0x97!"
    assert results[4]["output_byte_hex"] == "0x19", "CRITICAL ERROR: ECU 0x19 must encode to 0x19!"
    assert results[5]["output_byte_hex"] == "0x33", "CRITICAL ERROR: ECU 0x33 must remain 0x33 (exempt)!"
    
    print("\nALL PARITY ASSERTIONS PASSED PERFECTLY.")
    
    # Save vectors to JSON
    script_dir = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.normpath(os.path.join(script_dir, "../../reverse/tests/parity_vectors.json"))
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2)
    print(f"Saved parity test vectors to: {out_path}")

if __name__ == "__main__":
    main()
