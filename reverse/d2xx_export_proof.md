# Ross-Tech RTUS64.dll D2XX Export Audit & Proof

## 1. Cryptographic Binary Identification
- **Path**: `C:\Ross-Tech\VCDS\RTUS64.dll`
- **Expected SHA256**: `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2`
- **Actual SHA256**: `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2`
- **ImageBase**: `0x180000000`
- **Status**: `PROVEN_STATIC_CONSENSUS`

## 2. Independent Dual-Method Verification
Per GitHub Issue #5 requirements, exports were extracted and verified across independent methods:
1. `pefile` Python library
2. Native binary PE struct offset parser
3. Ghidra Headless headless export analysis

## 3. Audited D2XX Export Table

| Export Name | Ordinal | RVA | VA (Base 0x180000000) | Forwarder | Status |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `FT_Open` | `1` | `0x00005830` | `0x180005830` | `None` | `PROVEN_STATIC` |
| `FT_Close` | `2` | `0x00002B40` | `0x180002B40` | `None` | `PROVEN_STATIC` |
| `FT_Read` | `3` | `0x00002BB0` | `0x180002BB0` | `None` | `PROVEN_STATIC` |
| `FT_Write` | `4` | `0x00002C10` | `0x180002C10` | `None` | `PROVEN_STATIC` |
| `FT_ResetDevice` | `6` | `0x00002CF0` | `0x180002CF0` | `None` | `PROVEN_STATIC` |
| `FT_SetBaudRate` | `7` | `0x00002D30` | `0x180002D30` | `None` | `PROVEN_STATIC` |
| `FT_SetDataCharacteristics` | `8` | `0x00002DC0` | `0x180002DC0` | `None` | `PROVEN_STATIC` |
| `FT_Purge` | `16` | `0x00002FE0` | `0x180002FE0` | `None` | `PROVEN_STATIC` |
| `FT_SetTimeouts` | `17` | `0x00003020` | `0x180003020` | `None` | `PROVEN_STATIC` |
| `FT_GetQueueStatus` | `18` | `0x00003060` | `0x180003060` | `None` | `PROVEN_STATIC` |
| `FT_SetLatencyTimer` | `29` | `0x00004320` | `0x180004320` | `None` | `PROVEN_STATIC` |

## 4. VCDS.EXE Integration Evidence
- FT_Write import pointer in VCDS.EXE: `0x14018C860` calling `RTUS64.dll!FT_Write` (Ordinal 4, RVA `0x00002C10`)
- FT_Read import pointer in VCDS.EXE: `0x14018C858` calling `RTUS64.dll!FT_Read` (Ordinal 3, RVA `0x00002BB0`)
- FT_SetLatencyTimer call in VCDS.EXE: `FUN_140111f40` line 44 via `(*DAT_14018c878)(DAT_140630f48, 1)`
- All pointers and ordinals match without conflict.
