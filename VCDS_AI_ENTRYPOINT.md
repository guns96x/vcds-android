# VCDS Reverse-Engineering Knowledge System: AI Entrypoint

> **ATTENTION ALL AI CODING ASSISTANTS & SUBAGENTS:**
> This document is the **single authoritative entry point** for all reverse-engineering knowledge concerning Ross-Tech VCDS 26.3 and hardware transport communication for `vcds-android`.
>
> **CRITICAL RULE:** Do NOT read old Markdown files under `reverse/` (`baseline.md`, `packet_builder.md`, `group011.md`, etc.) as ground truth. Those files were exploratory scratch drafts containing superseded errors. **The canonical truth store is `vcds-kb/vcds_kb.db`**.

---

## 1. Authoritative Target Binaries & Cryptographic Identity

Before performing or citing any reverse-engineering fact, verify against these exact binaries:

- **Primary Binary:** `C:\Ross-Tech\VCDS\VCDS.EXE`
  - **Version:** `26.3.0.0`
  - **Architecture:** x64 PE (AMD64)
  - **SHA256:** `CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3`
- **Driver DLL:** `C:\Ross-Tech\VCDS\RTUS64.dll`
  - **Architecture:** x64 PE (AMD64)
  - **SHA256:** `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2`

If analyzing a file with a differing hash, STOP and report an identity conflict.

---

## 2. Epistemic Evidence States & Rigor Levels

Every technical claim is assigned strictly one of the following states:

- **`PROVEN_STATIC`:** Directly proven from binary disassembly, assembly instructions, or decompiled C AST with verified address, caller, callee, and data flow.
- **`PROVEN_DYNAMIC`:** Supported by a physical runtime trace (timestamp, API, exact TX/RX bytes, return status).
- **`PROVEN_BOTH`:** Corroborated by both static code analysis and dynamic bus trace.
- **`INFERRED`:** Plausible interpretation supported by context or partial call graph, but lacking complete end-to-end provenance.
- **`UNKNOWN`:** Insufficient evidence. **No production Android code may transmit unproven packets.**
- **`CONFLICT`:** Two or more evidence sources disagree. Requires resolution before use.
- **`RETRACTED`:** Historically claimed but explicitly disproven. Must NEVER be revived.
- **`OUT_OF_SCOPE_SECURITY`:** DRM, licensing, activation, or serial generation. Indexed only by call edge boundaries; never reverse-engineered.

---

## 3. Authoritative Retrieval Order

Always retrieve technical facts in this exact priority sequence:

1. **`vcds-kb/vcds_kb.db`** (Canonical SQLite Database): Query via CLI or SQL.
2. **`vcds-kb/remote/*.json`** (Read-only exported snapshots): For fast JSON lookups.
3. **`reverse/IMPLEMENTATION_SPEC_V2.md`** (Audited specification): Strictly contains only `PROVEN_STATIC` items.
4. **Old scratch Markdown files:** QUARANTINED. Do not use for code generation.

---

## 4. Querying the Knowledge Base

Run the query tool from the repository root:

```bash
# Query exact function address (callers, callees, claims, decompiler)
python vcds-kb/query.py addr 0x14007E734

# Query function or API symbol
python vcds-kb/query.py symbol FT_Write
python vcds-kb/query.py symbol FT_Read

# Query Ross-Tech adapter hardware opcode
python vcds-kb/query.py opcode 0x84
python vcds-kb/query.py opcode 0x03

# Query diagnostic service ID
python vcds-kb/query.py service 0x21

# Query semantic feature
python vcds-kb/query.py semantic GROUP_011

# Trace data-flow path across callgraph
python vcds-kb/query.py path --from semantic:GROUP_011 --to symbol:FT_Write

# Check open research gaps
python vcds-kb/query.py gaps --priority P0

# Check historical retractions & conflicts
python vcds-kb/query.py conflicts
```

---

## 5. Explicit Historical Retractions (DO NOT RE-INTRODUCE)

The following historical draft mistakes were audited, disproven, and permanently retracted:

1. **ECU 01 Parity:** Draft claimed `0x81`. Disproven. VCDS uses **odd parity**; `0x01` has 1 set bit (already odd), so bit 7 is not set. Encoded byte is strictly **`0x01`**.
2. **Group 011 Raw Opcode:** Draft claimed `0x21` was a raw adapter opcode. Disproven. `0x21` is an ISO 14230 KWP service ID. Wire encapsulation is unproven (`UNKNOWN`).
3. **Keepalive Opcode:** Draft claimed `0x3E` was an adapter opcode. Disproven. `0x3E` is KWP `TesterPresent`. Adapter opcode is `UNKNOWN`.
4. **Formula Decoder `FUN_14011FEB8`:** Draft claimed `FUN_14011FEB8` decoded Group 011 formulas. Disproven. Decompilation proves it is an adapter security challenge block validator checking `0xAA` and `0x55`.
5. **Coverage:** Draft claimed "100% full decompilation". Disproven and replaced with exact metrics.

---

## 6. Current Knowledge Base Coverage & Metrics

- **Indexed Functions:** 4,605
- **Call Edges:** 31,247
- **Indexed Strings:** 1,936
- **Decompiled Chunks:** 113
- **Verified PROVEN Claims:** 10
- **Historical Retractions:** 5
- **Open P0 Gaps:** 2

---

## 7. Open P0 Gaps (Work in Progress)

- **`GAP_KWP_TO_ADAPTER_ENCAPSULATION` (P0):** Continuous data flow from ECU diagnostic request (KWP service / measuring block) to Ross-Tech adapter `send_frame` is unresolved across vtable dispatch in `BlockDlg::OnGraph` (`0x14005911C`).
- **`GAP_KEEPALIVE_ADAPTER_OPCODE` (P0):** The specific opcode used by Ross-Tech adapter to maintain an active K-Line/CAN session without sending diagnostic requests is unmapped.
