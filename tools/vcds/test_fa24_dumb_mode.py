#!/usr/bin/env python3
"""test_fa24_dumb_mode.py -- Test harness and validation for Ross-Tech FA24 dumb mode switch.

Validates the reverse-engineered wire frames for HC::SetBoot (0x0E) and HC::ReadBoot (0x0D),
verifies XOR checksum calculations, and exercises non-destructive cable communication.
"""

import ctypes
import os
import sys
import unittest

RTUS64_PATH = r"C:\Ross-Tech\VCDS\RTUS64.dll"


def calculate_xor_checksum(frame: bytes) -> int:
    xor_val = 0
    for b in frame:
        xor_val ^= b
    return xor_val


def encode_frame(marker: int, opcode: int, payload: bytes = b"") -> bytes:
    total_len = 4 + len(payload)
    buf = bytearray([marker, total_len, opcode]) + bytearray(payload)
    buf.append(calculate_xor_checksum(buf))
    return bytes(buf)


def decode_frame(data: bytes):
    if len(data) < 4:
        return None
    marker = data[0]
    total_len = data[1]
    if len(data) < total_len:
        return None
    checksum = data[total_len - 1]
    computed_xor = calculate_xor_checksum(data[:total_len - 1])
    if computed_xor != checksum:
        return None
    opcode = data[2]
    payload = data[3:total_len - 1]
    return {
        "marker": marker,
        "length": total_len,
        "opcode": opcode,
        "payload": bytes(payload),
        "valid": True,
    }


class TestFa24DumbModeFraming(unittest.TestCase):
    def test_set_boot_dumb_frame(self):
        # Opcode 0x0E, mode 0x00 -> total length 5
        # 0x53 ^ 0x05 ^ 0x0E ^ 0x00 = 0x58
        frame = encode_frame(0x53, 0x0E, bytes([0x00]))
        self.assertEqual(frame, bytes([0x53, 0x05, 0x0E, 0x00, 0x58]))
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["opcode"], 0x0E)
        self.assertEqual(decoded["payload"], bytes([0x00]))

    def test_set_boot_smart_frame(self):
        # Opcode 0x0E, mode 0x02 -> total length 5
        # 0x53 ^ 0x05 ^ 0x0E ^ 0x02 = 0x5A
        frame = encode_frame(0x53, 0x0E, bytes([0x02]))
        self.assertEqual(frame, bytes([0x53, 0x05, 0x0E, 0x02, 0x5A]))
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["opcode"], 0x0E)
        self.assertEqual(decoded["payload"], bytes([0x02]))

    def test_read_boot_frame(self):
        # Opcode 0x0D -> total length 4
        # 0x53 ^ 0x04 ^ 0x0D = 0x5A
        frame = encode_frame(0x53, 0x0D)
        self.assertEqual(frame, bytes([0x53, 0x04, 0x0D, 0x5A]))
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["opcode"], 0x0D)
        self.assertEqual(decoded["payload"], b"")

    def test_ack_response_frame(self):
        # Cable ACK: Marker 0x4D, len 0x04, opcode 0xFE
        # 0x4D ^ 0x04 ^ 0xFE = 0xB7
        frame = bytes([0x4D, 0x04, 0xFE, 0xB7])
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["marker"], 0x4D)
        self.assertEqual(decoded["opcode"], 0xFE)
        self.assertTrue(decoded["valid"])

    def test_read_boot_response_dumb(self):
        # Mode 0x00 response: 0x4D ^ 0x05 ^ 0x0D ^ 0x00 = 0x45
        frame = encode_frame(0x4D, 0x0D, bytes([0x00]))
        self.assertEqual(frame, bytes([0x4D, 0x05, 0x0D, 0x00, 0x45]))
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["opcode"], 0x0D)
        self.assertEqual(decoded["payload"], bytes([0x00]))

    def test_read_boot_response_smart(self):
        # Mode 0x02 response: 0x4D ^ 0x05 ^ 0x0D ^ 0x02 = 0x47
        frame = encode_frame(0x4D, 0x0D, bytes([0x02]))
        self.assertEqual(frame, bytes([0x4D, 0x05, 0x0D, 0x02, 0x47]))
        decoded = decode_frame(frame)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded["opcode"], 0x0D)
        self.assertEqual(decoded["payload"], bytes([0x02]))


class TestPhysicalD2xxConnectivity(unittest.TestCase):
    def test_d2xx_driver_enumerate(self):
        if not os.path.exists(RTUS64_PATH):
            self.skipTest(f"RTUS64.dll not found at {RTUS64_PATH}")

        d2xx = ctypes.windll.LoadLibrary(RTUS64_PATH)
        num_devs = ctypes.c_ulong()
        status = d2xx.FT_CreateDeviceInfoList(ctypes.byref(num_devs))
        self.assertEqual(status, 0, "FT_CreateDeviceInfoList failed")
        if num_devs.value == 0:
            self.skipTest("No FTDI device physically connected")

        serial_buf = ctypes.create_string_buffer(64)
        status = d2xx.FT_ListDevices(ctypes.c_ulong(0), serial_buf, 0x40000000)
        self.assertEqual(status, 0)
        self.assertEqual(serial_buf.value, b"RT000001")


if __name__ == "__main__":
    unittest.main()
