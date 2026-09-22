#!/usr/bin/env python3
"""test_analyze_trace.py -- Unit tests for D2XX trace analyzer and wire frame parser."""

import unittest
from analyze_trace import parse_trace_line, parse_wire_frame, D2xxEvent


class TestAnalyzeTrace(unittest.TestCase):
    def test_parse_write_line(self):
        line = '[000001234ms][seq:000042][tid:01234] FT_Write(handle: 0x00000001, len: 4) -> HEX: [53 04 02 55] | ASCII: "S..U"'
        evt = parse_trace_line(line)
        self.assertIsNotNone(evt)
        self.assertEqual(evt.millis, 1234)
        self.assertEqual(evt.seq, 42)
        self.assertEqual(evt.tid, 1234)
        self.assertEqual(evt.func, "FT_Write")
        self.assertEqual(evt.hex_payload, bytes([0x53, 0x04, 0x02, 0x55]))
        self.assertEqual(evt.ascii_payload, "S..U")

    def test_parse_read_line(self):
        line = '[000001250ms][seq:000043][tid:01234] FT_Read(handle: 0x00000001, requested: 64) -> returned: 7, HEX: [4D 07 02 01 60 44 26] | ASCII: "M...`D&"'
        evt = parse_trace_line(line)
        self.assertIsNotNone(evt)
        self.assertEqual(evt.func, "FT_Read")
        self.assertEqual(evt.returned_len, 7)
        self.assertEqual(evt.hex_payload, bytes([0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x26]))

    def test_parse_control_lines(self):
        line = "[000000050ms][seq:000005][tid:01234] FT_SetBaudRate(handle: 0x00000001, baud: 115200)"
        evt = parse_trace_line(line)
        self.assertIsNotNone(evt)
        self.assertEqual(evt.func, "FT_SetBaudRate")
        self.assertIn("115200", evt.args)

    def test_wire_frame_host_ping(self):
        # 53 04 02 55 -> 0x53 ^ 0x04 ^ 0x02 = 0x55
        raw = bytes([0x53, 0x04, 0x02, 0x55])
        frame = parse_wire_frame(raw, "HOST_TO_MCU")
        self.assertIsNotNone(frame)
        self.assertEqual(frame.marker, 0x53)
        self.assertEqual(frame.length, 4)
        self.assertEqual(frame.opcode, 0x02)
        self.assertTrue(frame.xor_valid)

    def test_wire_frame_mcu_ping_response(self):
        # 4D 07 02 01 60 44 6D -> 0x4D ^ 0x07 ^ 0x02 ^ 0x01 ^ 0x60 ^ 0x44 = 0x6D
        raw = bytes([0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D])
        frame = parse_wire_frame(raw, "MCU_TO_HOST")
        self.assertIsNotNone(frame)
        self.assertEqual(frame.marker, 0x4D)
        self.assertEqual(frame.length, 7)
        self.assertEqual(frame.opcode, 0x02)
        self.assertEqual(frame.payload, bytes([0x01, 0x60, 0x44]))
        self.assertTrue(frame.xor_valid)

    def test_wire_frame_invalid_xor(self):
        raw = bytes([0x53, 0x04, 0x02, 0x00])  # Corrupted checksum
        frame = parse_wire_frame(raw, "HOST_TO_MCU")
        self.assertIsNotNone(frame)
        self.assertFalse(frame.xor_valid)


if __name__ == "__main__":
    unittest.main()
