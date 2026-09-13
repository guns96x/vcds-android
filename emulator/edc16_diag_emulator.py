#!/usr/bin/env python3
"""
Bosch EDC16U34 KWP2000 Diagnostic Simulator / Emulator
Simulates K-Line diagnostic responses (Groups 003, 008, 011, DTCs) for VCDS Mobile.
Can run over TCP socket or virtual serial port.
"""

import sys
import time
import math
import socket
import argparse

def calculate_checksum(data: bytes) -> int:
    return sum(data) & 0xFF

def build_response(payload: bytes) -> bytes:
    length = len(payload)
    fmt = 0x80 | (length & 0x3F)
    # Target: Tester 0xF1, Source: Engine 0x01
    header = bytes([fmt, 0xF1, 0x01])
    body = header + payload
    cs = calculate_checksum(body)
    return body + bytes([cs])

class Edc16Simulator:
    def __init__(self):
        self.rpm = 1400.0
        self.time_offset = 0.0
        self.wot = True
        self.dtcs = [
            (0x02, 0x99, 0xA2), # P0299 Boost control range not reached
            (0x01, 0x01, 0x20)  # P0101 MAF implausible signal
        ]

    def update_physics(self, dt: float = 0.05):
        self.time_offset += dt
        if self.wot:
            self.rpm += 45.0
            if self.rpm >= 4200.0:
                self.wot = False
        else:
            self.rpm -= 80.0
            if self.rpm <= 1400.0:
                self.rpm = 1400.0
                self.wot = True

    def get_group_11(self) -> bytes:
        rpm = self.rpm
        boost_target = 1350.0 + (rpm - 1400.0) * 3.5 if rpm < 1700.0 else 2350.0
        boost_actual = 1050.0 + (rpm - 1400.0) * 1.6 if rpm < 1850.0 else (2340.0 + math.sin(self.time_offset * 3) * 15.0)
        n75 = 80.0 if rpm < 2100.0 else (60.0 - math.sin(self.time_offset) * 3.0)

        # Encode VAG formulas:
        # Field 1: Formula 1 (0.2*a*b -> RPM)
        r_val = int(rpm / 0.2)
        # Field 2: Formula 8 (0.1*a*b -> mbar)
        bt_val = int(boost_target / 0.1)
        # Field 3: Formula 8
        ba_val = int(boost_actual / 0.1)
        # Field 4: Formula 2 (0.002*a*b -> %)
        n75_val = int(n75 / 0.002)

        payload = bytearray([0x61, 0x0B])
        payload.extend([1, (r_val >> 8) & 0xFF, r_val & 0xFF])
        payload.extend([8, (bt_val >> 8) & 0xFF, bt_val & 0xFF])
        payload.extend([8, (ba_val >> 8) & 0xFF, ba_val & 0xFF])
        payload.extend([2, (n75_val >> 8) & 0xFF, n75_val & 0xFF])
        return bytes(payload)

    def get_group_8(self) -> bytes:
        rpm = self.rpm
        driver_wish = 60.0
        trq_lim = 56.5
        smoke_lim = 36.0 + (rpm - 1400.0) * 0.02 if rpm < 2200.0 else 55.0

        r_val = int(rpm / 0.2)
        dw_val = int(driver_wish / 0.025)
        tl_val = int(trq_lim / 0.025)
        sl_val = int(smoke_lim / 0.025)

        payload = bytearray([0x61, 0x08])
        payload.extend([1, (r_val >> 8) & 0xFF, r_val & 0xFF])
        payload.extend([49, (dw_val >> 8) & 0xFF, dw_val & 0xFF])
        payload.extend([49, (tl_val >> 8) & 0xFF, tl_val & 0xFF])
        payload.extend([49, (sl_val >> 8) & 0xFF, sl_val & 0xFF])
        return bytes(payload)

    def get_group_3(self) -> bytes:
        rpm = self.rpm
        maf_req = 850.0
        maf_act = 420.0 + (rpm - 1400.0) * 0.22
        egr = 4.8

        r_val = int(rpm / 0.2)
        mr_val = int(maf_req * 256.0 / 100.0)
        ma_val = int(maf_act * 256.0 / 100.0)
        egr_val = int(egr / 0.002)

        payload = bytearray([0x61, 0x03])
        payload.extend([1, (r_val >> 8) & 0xFF, r_val & 0xFF])
        payload.extend([39, (mr_val >> 8) & 0xFF, mr_val & 0xFF])
        payload.extend([39, (ma_val >> 8) & 0xFF, ma_val & 0xFF])
        payload.extend([2, (egr_val >> 8) & 0xFF, egr_val & 0xFF])
        return bytes(payload)

    def handle_request(self, req: bytes) -> bytes:
        if len(req) < 4:
            return b""
        
        # Check payload
        fmt = req[0]
        length = fmt & 0x3F
        payload = req[3: 3 + length]
        if not payload:
            return b""

        sid = payload[0]
        self.update_physics()

        if sid == 0x81: # StartCommunication
            # Return positive response with KeyBytes 0x8F, 0xEF
            return build_response(bytes([0xC1, 0xEF, 0x8F]))

        elif sid == 0x10: # StartDiagnosticSession
            sub = payload[1] if len(payload) > 1 else 0x81
            return build_response(bytes([0x50, sub]))

        elif sid == 0x21: # ReadDataByLocalIdentifier
            grp = payload[1] if len(payload) > 1 else 11
            if grp == 11:
                return build_response(self.get_group_11())
            elif grp == 8:
                return build_response(self.get_group_8())
            elif grp == 3:
                return build_response(self.get_group_3())
            else:
                return build_response(self.get_group_11())

        elif sid == 0x18: # ReadDTC
            resp = bytearray([0x58, len(self.dtcs)])
            for high, low, status in self.dtcs:
                resp.extend([high, low, status])
            return build_response(bytes(resp))

        elif sid == 0x14: # ClearDTC
            self.dtcs.clear()
            return build_response(bytes([0x54]))

        else:
            # Negative response
            return build_response(bytes([0x7F, sid, 0x11]))

def run_tcp_server(port: int = 9999):
    sim = Edc16Simulator()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(1)
    print(f"[*] EDC16U34 KWP2000 Diagnostic Simulator running on 127.0.0.1:{port}")
    print("[*] Waiting for client connection...")

    while True:
        client, addr = server.accept()
        print(f"[+] Client connected: {addr}")
        try:
            while True:
                data = client.recv(256)
                if not data:
                    break
                resp = sim.handle_request(data)
                if resp:
                    client.sendall(resp)
        except Exception as e:
            print(f"[-] Disconnected: {e}")
        finally:
            client.close()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="EDC16 KWP2000 Simulator")
    parser.add_argument("--port", type=int, default=9999, help="TCP port (default 9999)")
    args = parser.parse_args()
    run_tcp_server(args.port)
