#!/usr/bin/env python3
import json
import tempfile
import unittest
from pathlib import Path

from extract_engine01_fixture import build_fixture, load_events, split_sessions


def line(ms, seq, func, args="", suffix=""):
    return f"[{ms}ms][seq:{seq}][tid:7] {func}({args}){suffix}\n"


class ExtractEngine01FixtureTests(unittest.TestCase):
    def test_extracts_one_engine_candidate_session(self):
        log = "".join(
            [
                line(0, 1, "FT_OpenEx", "serial=RT000001"),
                line(1, 2, "FT_SetBaudRate", "115200"),
                line(2, 3, "FT_Write", "handle=1", ' HEX: [53 04 02 55]'),
                line(3, 4, "FT_Read", "handle=1", ' returned: 7 HEX: [4D 07 02 01 60 44 6D]'),
                line(4, 5, "FT_Write", "handle=1", ' HEX: [53 04 04 53]'),
                line(
                    5,
                    6,
                    "FT_Read",
                    "handle=1",
                    ' returned: 20 HEX: [4D 14 04 52 4F 53 53 54 45 43 48 00 00 00 A8 9D 01 00 09 31]',
                ),
                # A valid outer B8 frame is enough to mark diagnostic-envelope evidence.
                line(6, 7, "FT_Write", "handle=1", ' HEX: [53 04 B8 EB]'),
                line(7, 8, "FT_Close", "handle=1"),
            ]
        )

        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "trace.log"
            p.write_text(log, encoding="utf-8")
            events = load_events(p)
            sessions = split_sessions(events)
            self.assertEqual(1, len(sessions))
            fixture = build_fixture(sessions[0], p, 0)

        self.assertTrue(fixture["evidence"]["identify_rosstech_seen"])
        self.assertTrue(fixture["evidence"]["diagnostic_envelope_seen"])
        self.assertIn("0x02", fixture["evidence"]["host_opcodes"])
        self.assertIn("0x04", fixture["evidence"]["host_opcodes"])
        self.assertIn("0xB8", fixture["evidence"]["host_opcodes"])

    def test_splits_multiple_open_close_sessions(self):
        log = "".join(
            [
                line(0, 1, "FT_Open", "0"),
                line(1, 2, "FT_Close", "1"),
                line(10, 3, "FT_OpenEx", "serial=RT000001"),
                line(11, 4, "FT_Close", "2"),
            ]
        )
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "trace.log"
            p.write_text(log, encoding="utf-8")
            sessions = split_sessions(load_events(p))

        self.assertEqual(2, len(sessions))
        self.assertEqual("FT_Open", sessions[0][0].func)
        self.assertEqual("FT_OpenEx", sessions[1][0].func)


if __name__ == "__main__":
    unittest.main()
