#!/usr/bin/env python3
"""
generate_fixtures.py — Generates synthetic USBPcap .pcap files to test parse_usb_pcap.py
and diff_usb_captures.py without requiring physical hardware.
"""

import os
import struct

LINKTYPE_USBPCAP = 249


def create_pcap_global_header() -> bytes:
    # Standard little-endian pcap magic bytes
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
    status = 0  # USBD_STATUS_SUCCESS
    function = 0x001B  # URB_FUNCTION_CONTROL_TRANSFER
    info = 0x01  # Complete
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
    function = 0x0009  # URB_FUNCTION_BULK_OR_INTERRUPT_TRANSFER
    info = 0x00  # Submit
    bus = 1
    endpoint = ep & 0x7F  # bit 7 = 0 for OUT
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
    info = 0x01  # Complete
    bus = 1
    endpoint = (ep & 0x7F) | 0x80  # bit 7 = 1 for IN
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


def build_fixture_options_test(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960000
    t_us = 100000

    # 1. FTDI SIO_RESET
    p1 = make_usbpcap_control_setup(0x1001, dev, b_request=0x00, w_value=0, w_index=0)
    packets.append(create_pcap_record(t_sec, t_us, p1))

    # 2. FTDI SET_BAUDRATE: 115200 (div=26, sub=0) -> wValue=0x001A
    t_us += 5000
    p2 = make_usbpcap_control_setup(0x1002, dev, b_request=0x03, w_value=0x001A, w_index=0)
    packets.append(create_pcap_record(t_sec, t_us, p2))

    # 3. FTDI SET_LATENCY_TIMER: 1ms
    t_us += 5000
    p3 = make_usbpcap_control_setup(0x1003, dev, b_request=0x09, w_value=1, w_index=0)
    packets.append(create_pcap_record(t_sec, t_us, p3))

    # 4. FTDI MODEM_CTRL: DTR High, RTS High (0x0303)
    t_us += 5000
    p4 = make_usbpcap_control_setup(0x1004, dev, b_request=0x01, w_value=0x0303, w_index=0)
    packets.append(create_pcap_record(t_sec, t_us, p4))

    # 5. Handshake: Bulk OUT magic ping (Invariant)
    t_us += 10000
    ping_cmd = bytes.fromhex("55 AA 01 00 FE")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x2001, dev, 2, ping_cmd)))

    # 6. Handshake: Bulk IN response
    t_us += 15000
    ping_resp = bytes.fromhex("55 AA 01 01 00 FD")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x2002, dev, 1, ping_resp)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def build_fixture_engine_connect(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960010
    t_us = 100000

    # Same FTDI Setup invariants
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3001, dev, 0x00, 0, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3002, dev, 0x03, 0x001A, 0)))
    t_us += 5000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_control_setup(0x3003, dev, 0x09, 1, 0)))

    # Invariant Handshake
    t_us += 10000
    ping_cmd = bytes.fromhex("55 AA 01 00 FE")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3004, dev, 2, ping_cmd)))
    t_us += 15000
    ping_resp = bytes.fromhex("55 AA 01 01 00 FD")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3005, dev, 1, ping_resp)))

    # KWP2000 StartCommunication OUT: 81 01 F1 81 F4
    t_us += 20000
    kwp_start = bytes.fromhex("81 01 F1 81 F4")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3006, dev, 2, kwp_start)))

    # KWP2000 Pos Response IN: 83 F1 01 C1 EA 8F 00
    t_us += 35000
    kwp_resp = bytes.fromhex("83 F1 01 C1 EA 8F 00")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3007, dev, 1, kwp_resp)))

    # KWP2000 Read ECU ID: 02 1A 9A 00
    t_us += 20000
    ecu_id_req = bytes.fromhex("02 1A 9A 00")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x3008, dev, 2, ecu_id_req)))

    # Positive Response: 50 1A 30 33 47 39 30 36 30 31 36 41 42
    t_us += 25000
    ecu_id_resp = bytes.fromhex("50 1A 30 33 47 39 30 36 30 31 36 41 42")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x3009, dev, 1, ecu_id_resp)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def build_fixture_dtc_read(out_path: str):
    dev = 3
    packets = []
    t_sec = 1726960020
    t_us = 100000

    # Invariant Handshake
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x4001, dev, 2, bytes.fromhex("55 AA 01 00 FE"))))
    t_us += 15000
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x4002, dev, 1, bytes.fromhex("55 AA 01 01 00 FD"))))

    # Read DTCs: 03 18 00 00 1B
    t_us += 20000
    dtc_req = bytes.fromhex("03 18 00 00 1B")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_out(0x4003, dev, 2, dtc_req)))

    # DTC Response: 58 00 (0 faults)
    t_us += 25000
    dtc_resp = bytes.fromhex("58 00")
    packets.append(create_pcap_record(t_sec, t_us, make_usbpcap_bulk_in(0x4004, dev, 1, dtc_resp)))

    with open(out_path, "wb") as f:
        f.write(create_pcap_global_header())
        for pkt in packets:
            f.write(pkt)


def main():
    fixtures_dir = os.path.join(os.path.dirname(__file__), "fixtures")
    os.makedirs(fixtures_dir, exist_ok=True)

    f1 = os.path.join(fixtures_dir, "fixture_options_test.pcap")
    f2 = os.path.join(fixtures_dir, "fixture_engine_connect.pcap")
    f3 = os.path.join(fixtures_dir, "fixture_dtc_read.pcap")

    build_fixture_options_test(f1)
    build_fixture_engine_connect(f2)
    build_fixture_dtc_read(f3)

    print(f"Generated fixture: {f1} ({os.path.getsize(f1)} B)")
    print(f"Generated fixture: {f2} ({os.path.getsize(f2)} B)")
    print(f"Generated fixture: {f3} ({os.path.getsize(f3)} B)")


if __name__ == "__main__":
    main()
