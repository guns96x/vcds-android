#!/usr/bin/env python3
"""
test_pcap_tools.py — Unit tests for parse_usb_pcap.py and diff_usb_captures.py.
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(__file__))

from parse_usb_pcap import parse_pcap
from diff_usb_captures import compare_captures, correlate_payload
from generate_fixtures import main as generate_fixtures_main


class TestPcapTools(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tools_dir = os.path.dirname(__file__)
        cls.fixtures_dir = os.path.join(cls.tools_dir, "fixtures")
        # Ensure fixtures are generated
        generate_fixtures_main()

    def test_parse_options_test_fixture(self):
        pcap_path = os.path.join(self.fixtures_dir, "fixture_options_test.pcap")
        self.assertTrue(os.path.isfile(pcap_path))
        result = parse_pcap(pcap_path, is_ftdi=True)

        meta = result["metadata"]
        self.assertEqual(meta["linktype"], 249)
        self.assertEqual(meta["total_packets"], 6)
        self.assertEqual(meta["control_transfers"], 4)
        self.assertEqual(meta["bulk_out_transfers"], 1)
        self.assertEqual(meta["bulk_in_transfers_with_data"], 1)

        # Check FTDI baudrate decode
        transfers = result["transfers"]
        baud_pkt = transfers[1]
        self.assertEqual(baud_pkt["transfer_type"], "CONTROL")
        self.assertEqual(baud_pkt["setup_packet"]["ftdi_command"], "FTDI_SIO_SET_BAUDRATE")
        self.assertIn("115384", baud_pkt["setup_packet"]["detail"])

    def test_parse_engine_connect_fixture(self):
        pcap_path = os.path.join(self.fixtures_dir, "fixture_engine_connect.pcap")
        result = parse_pcap(pcap_path, is_ftdi=True)

        meta = result["metadata"]
        self.assertEqual(meta["total_packets"], 9)
        self.assertEqual(meta["bulk_out_transfers"], 3)
        self.assertEqual(meta["bulk_in_transfers_with_data"], 3)

    def test_payload_correlation(self):
        kwp_start = "8101f181f4"
        matches = correlate_payload(kwp_start)
        self.assertTrue(any("StartCommunication" in m for m in matches))

        ecu_id = "021a9a00"
        matches = correlate_payload(ecu_id)
        self.assertTrue(any("ReadECUIdentification" in m for m in matches))

    def test_differential_comparison(self):
        f1 = parse_pcap(os.path.join(self.fixtures_dir, "fixture_options_test.pcap"))
        f2 = parse_pcap(os.path.join(self.fixtures_dir, "fixture_engine_connect.pcap"))
        f3 = parse_pcap(os.path.join(self.fixtures_dir, "fixture_dtc_read.pcap"))

        diff = compare_captures({
            "Capture_A": f1,
            "Capture_B": f2,
            "Capture_C": f3,
        })

        # Invariant command present across all three
        self.assertIn("55aa0100fe", diff["invariant_commands"])

        # Capture B has unique KWP2000 start
        self.assertIn("8101f181f4", diff["unique_commands"]["Capture_B"])

        # Capture C has unique DTC read
        self.assertIn("031800001b", diff["unique_commands"]["Capture_C"])


if __name__ == "__main__":
    unittest.main()
