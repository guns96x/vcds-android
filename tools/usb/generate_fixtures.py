#!/usr/bin/env python3
"""
generate_fixtures.py — Generates synthetic USBPcap .pcap files to unit-test parse_usb_pcap.py
and diff_usb_captures.py without requiring physical hardware.

==============================================================================
DISCLAIMER: SYNTHETIC TEST FIXTURE GENERATOR ONLY.
The payloads generated here are artificial mock sequences created exclusively
to unit-test the pcap parser, FTDI decoder, and differential comparison logic.
THEY DO NOT CONSTITUTE PROTOCOL EVIDENCE FOR B03-V2 OR ROSS-TECH HARDWARE.
==============================================================================
"""

import os
import struct

LINKTYPE_USBPCAP = 249

# Neutral synthetic test vectors (never mistake for reverse-engineered protocol)
SYNTHETIC_MOCK_CMD_PING = b"SYNTH_PING_001"
SYNTHETIC_MOCK_RESP_PONG = b"SYNTH_PONG_001"

# Standard ISO/KWP test vectors used ONLY to verify parser signature-matching logic
MOCK_KWP_START_COMM = bytes.fromhex("81 01 F1 81 F4")
MOCK_KWP_START_RESP = bytes.fromhex("83 F1 01 C1 EA 8F 00")
MOCK_KWP_ECU_ID_REQ = bytes.fromhex("02 1A 9A 00")
MOCK_KWP_ECU_ID_RESP = bytes.fromhex("50 1A 30 33 47 39 30 36 30 31 36 41 42")
MOCK_KWP_DTC_REQ = bytes.fromhex("03 18 00 00 1B")
MOCK_KWP_DTC_RESP = bytes.fromhex("58 00")


def create_pcap_global_header() -> bytes:
    magic_bytes = b"\xd4\xc3\xb2\xa1"
    v_major = 2
    v_minor = 4
    thiszone = 0
    sigfigs = 0
    snaplen = 65535
    network = LINKTYPE_USBPCAP
    return magic_bytes + struct.pack("<HHiIII", v_major, v_minor, thiszone, sigfigs, snaplen, network)


def create_pcap_record(ts_sec: int, ts_usec: int, packet_bytes: bytes) -> bytes:
    incl_len = len(packet_bytes)
    orig_len = incl_len
    hdr = struct.pack("<IIII", ts_sec, ts_usec, incl_len, orig_len)
    return hdr + packet_bytes


def make_usbpcap_control_setup(
    irp_id: int,
    device: int,
    b_request: int,
    w_value: int,
    w_index: int,
    w_length: int = 0,
    bm_request_type: int = 0x40,
) -> bytes:
    header_len = 36
    status = 0
    function = 0x001B
    info = 0x01
    bus = 1
    endpoint = 0x00
    transfer = 2  # CONTROL
    data_length = 0

    base = struct.pack(
        "<HQIHBHHBBI",
        header_len,
        irp_id,
        status,
        function,
        info,
        bus,
        device,
        endpoint,
        transfer,
        data_length,
    )
    stage = 0  # SETUP
    setup_pkt = struct.pack("<BBHHH", bm_request_type, b_request, w_value, w_index, w_length)
    return base + struct.pack("<B", stage) + setup_pkt


def make_usbpcap_bulk_out(irp_id: int, device: int, ep: int, payload: bytes) -> bytes:
    header_len = 27
    status = 0
    function = 0x0009
    info = 0x00
    bus = 1
    endpoint = ep & 0x7F
    transfer = 3  # BULK
    data_length = len(payload)

    base = struct.pack(
        "<HQIHBHHBBI",
        header_len,
        irp_id,
        status,
        function,
        info,
        bus,
        device,
        endpoint,
        transfer,
        data_length,
    )
    return base + payload


def make_usbpcap_bulk_in(irp_id: int, device: int, ep: int, uart_data: bytes, status0: int = 0x01, status1: int = 0x60) -> bytes:
    header_len = 27
    status = 0
    function = 0x0009
    info = 0x01
    bus = 1
    endpoint = (ep & 0x7F) | 0x80
    transfer = 3  # BULK
    ftdi_payload = bytes([status0, status1]) + uart_data
    data_length = len(ftdi_payload)

    base = struct.pack(
        "<HQIHBHHBBI",
        header_len,
        irp_id,
        status,
        function,
        info,
        bus,
        device,
        endpoint,
        transfer,
        data_length,
    )
    return base + ftdi_payload


def build_synthetic_fixture_options(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960000
    t_us = 100000

    # Synthetic FTDI setup sequence for testing parser control decoding
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x1001, dev, 0x00, 0, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x1002, dev, 0x03, 0x001A, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x1003, dev, 0x09, 1, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x1004, dev, 0x01, 0x0303, 0)))

    # Neutral synthetic ping/pong test payloads
    t_us += 10000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x2001, dev, 2, SYNTHETIC_MOCK_CMD_PING)))
    t_us += 15000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x2002, dev, 1, SYNTHETIC_MOCK_RESP_PONG)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def build_synthetic_fixture_engine(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960010
    t_us = 100000

    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3001, dev, 0x00, 0, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3002, dev, 0x03, 0x001A, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3003, dev, 0x09, 1, 0)))

    # Shared ping to test invariant isolation
    t_us += 10000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3004, dev, 2, SYNTHETIC_MOCK_CMD_PING)))
    t_us += 15000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3005, dev, 1, SYNTHETIC_MOCK_RESP_PONG)))

    # Synthetic KWP2000 start communication test vector
    t_us += 20000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3006, dev, 2, MOCK_KWP_START_COMM)))
    t_us += 35000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3007, dev, 1, MOCK_KWP_START_RESP)))

    # Synthetic KWP2000 read ECU ID test vector
    t_us += 20000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3008, dev, 2, MOCK_KWP_ECU_ID_REQ)))
    t_us += 25000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3009, dev, 1, MOCK_KWP_ECU_ID_RESP)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def build_synthetic_fixture_dtc(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960020
    t_us = 100000

    # Shared ping to test invariant isolation
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x4001, dev, 2, SYNTHETIC_MOCK_CMD_PING)))
    t_us += 15000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x4002, dev, 1, SYNTHETIC_MOCK_RESP_PONG)))

    # Synthetic DTC read test vector
    t_us += 20000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x4003, dev, 2, MOCK_KWP_DTC_REQ)))
    t_us += 25000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x4004, dev, 1, MOCK_KWP_DTC_RESP)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def main():
    fixtures_dir = os.path.join(os.path.dirname(__file__), "fixtures")
    os.makedirs(fixtures_dir, exist_ok=True)

    f1 = os.path.join(fixtures_dir, "synthetic_test_options.pcap")
    f2 = os.path.join(fixtures_dir, "synthetic_test_engine.pcap")
    f3 = os.path.join(fixtures_dir, "synthetic_test_dtc.pcap")

    build_synthetic_fixture_options(f1)
    build_synthetic_fixture_engine(f2)
    build_synthetic_fixture_dtc(f3)

    print(f"Generated synthetic fixture: {f1} ({os.path.getsize(f1)} B)")
    print(f"Generated synthetic fixture: {f2} ({os.path.getsize(f2)} B)")
    print(f"Generated synthetic fixture: {f3} ({os.path.getsize(f3)} B)")


if __name__ == "__main__":
    main()
