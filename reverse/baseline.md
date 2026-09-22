# Stage 1: Baseline Architecture & Binary Report

## 1. Ghidra Headless Environment
- **Ghidra Root:** `C:\Users\Admin\Desktop\ghidra_12.1.4_PUBLIC`
- **Headless Analyzer:** `C:\Users\Admin\Desktop\ghidra_12.1.4_PUBLIC\support\analyzeHeadless.bat`
- **Java Virtual Machine:** OpenJDK 64-Bit Server VM Temurin-25.0.4.1+1 (build 25.0.4.1+1-LTS)
- **Ghidra Project Path:** `C:\Users\Admin\Desktop\vcds_re_project\vcds_re`

## 2. Binaries Verification & Cryptographic Hashes
| File | Architecture | File Version | Product Version | SHA256 Hash |
| :--- | :--- | :--- | :--- | :--- |
| `C:\Ross-Tech\VCDS\VCDS.EXE` | PE32+ (x64, 64-bit) | 26.3.0.0 | 26.3.0.0 | `CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3` |
| `C:\Ross-Tech\VCDS\RTUS64.dll` | PE32+ (x64, 64-bit) | 3.02.08 | 2.10.00.1 | `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2` |
| `C:\Ross-Tech\VCDS\RT-USB.dll` | PE32 (x86, 32-bit) | 3.02.08 | 2.10.00.1 | `5A42313F5B7E4380E1A7B0FB8D1ABC97F9321CE383C2CADE85199892C550A9EB` |
| `C:\Ross-Tech\VCDS\VCDSScan.exe` | PE32 (x86, 32-bit) | 25.7.0.0 | 25.7.0.0 | Borland/C++Builder uncompressed helper utility |

## 3. PE Protection & Packing Analysis
`VCDS.EXE` on disk is protected by **The Enigma Protector (x64)**.
Evidence:
- Issuer string in payload: `"Enigma Protector CA"`.
- On-disk sections 0, 1, 2, 4, 6, 7 have stripped names, RWX flags (`0xE0000040`), and high Shannon entropy (~7.99), indicating encrypted/compressed payload.
- EntryPoint RVA is `0x1331F9DC` inside the Enigma stub section.
- `RTUS64.dll` on disk is **unpacked native code**, with standard sections (`.text`, `.rdata`, `.data`, `.pdata`, `.rsrc`, `.reloc`) and exports 87 FTDI D2XX functions.
- `VCDS.EXE` statically imports `RTUS64.DLL` via `Ordinal: 6` (`FT_ResetDevice`) at `IAT: 0x130DC048`. This ensures that Windows PE Loader automatically maps `RTUS64.DLL` into the process address space at launch.

## 4. In-Memory Runtime Unpacking
At runtime, Enigma decrypts and maps the native application image:
- **Base Address:** `0x0000000140000000`
- **`.text` (Code):** `0x140001000` - `0x14018C000` (Size: `0x18B000`, 1.62 MB, executable native x64).
- **`.rdata` (Constants/Vtables):** `0x14018C000` - `0x1401F0000` (Size: `0x64000`, 400 KB, read-only).
- **`.data` (Globals/State):** `0x1401F0000` - `0x140216000` (Size: `0x26000`, 152 KB, read-write).
- **PE Reconstitution:** A clean image of the decrypted sections was dumped to `scratch/vcds_re/bin/VCDS_unpacked.exe` and imported into Ghidra Headless, enabling 100% full decompilation and cross-referencing.
