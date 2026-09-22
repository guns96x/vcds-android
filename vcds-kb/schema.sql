-- VCDS Reverse Engineering Knowledge Base — Schema v2.0
-- Epistemic Evidence System for VCDS 26.3 Transport & Protocol Architecture

PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;

-- ── 1. BINARIES ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_binaries (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    path                TEXT NOT NULL,
    sha256              TEXT NOT NULL UNIQUE,
    version             TEXT,
    architecture        TEXT DEFAULT 'x64',
    image_base          TEXT,
    ghidra_project      TEXT,
    analysis_timestamp  TEXT DEFAULT (datetime('now'))
);

-- ── 2. FUNCTIONS ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_functions (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    binary_id           INTEGER NOT NULL REFERENCES reverse_binaries(id),
    address             TEXT NOT NULL,             -- VA (e.g. '0x14007E734')
    rva                 TEXT NOT NULL,             -- RVA (e.g. '0x0007E734')
    size                INTEGER,
    original_name       TEXT,                      -- Ghidra auto symbol (e.g. 'FUN_14007e734')
    assigned_name       TEXT,                      -- Semantic symbol (e.g. 'vcds_adapter_send_frame')
    namespace           TEXT,
    calling_convention  TEXT,
    signature           TEXT,
    decompiler_status   TEXT,                      -- 'DECOMPILED', 'FAILED', 'MANUAL_BOUNDS'
    semantic_category   TEXT,                      -- 'TRANSPORT', 'SESSION', 'SECURITY', 'UI', 'DIAGNOSTIC'
    semantic_status     TEXT DEFAULT 'UNKNOWN',    -- 'PROVEN_STATIC', 'INFERRED', 'UNKNOWN'
    pcode_hash          TEXT,
    assembly_hash       TEXT,
    bytes_hash          TEXT,
    UNIQUE (binary_id, address)
);
CREATE INDEX IF NOT EXISTS idx_func_addr ON reverse_functions(address);
CREATE INDEX IF NOT EXISTS idx_func_assigned ON reverse_functions(assigned_name);
CREATE INDEX IF NOT EXISTS idx_func_orig ON reverse_functions(original_name);

-- ── 3. STRINGS ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_strings (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    binary_id           INTEGER NOT NULL REFERENCES reverse_binaries(id),
    address             TEXT NOT NULL,
    encoding            TEXT DEFAULT 'ASCII',
    value               TEXT NOT NULL,
    function_id         INTEGER REFERENCES reverse_functions(id)
);
CREATE INDEX IF NOT EXISTS idx_str_addr ON reverse_strings(address);
CREATE INDEX IF NOT EXISTS idx_str_val ON reverse_strings(value);

-- ── 4. XREFS ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_xrefs (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    source_address      TEXT NOT NULL,
    target_address      TEXT NOT NULL,
    xref_type           TEXT                       -- 'CALL', 'DATA', 'READ', 'WRITE'
);
CREATE INDEX IF NOT EXISTS idx_xref_src ON reverse_xrefs(source_address);
CREATE INDEX IF NOT EXISTS idx_xref_dst ON reverse_xrefs(target_address);

-- ── 5. CALL EDGES ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_call_edges (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    caller              TEXT NOT NULL,             -- Caller function VA
    callsite            TEXT NOT NULL,             -- Instruction VA
    callee              TEXT NOT NULL,             -- Target function VA or semantic target
    dispatch_type       TEXT NOT NULL CHECK (dispatch_type IN ('DIRECT', 'INDIRECT', 'VTABLE', 'FUNCTION_POINTER', 'UNKNOWN')),
    vtable_address      TEXT,
    vtable_slot         TEXT,
    confidence          TEXT DEFAULT 'HIGH'
);
CREATE INDEX IF NOT EXISTS idx_call_caller ON reverse_call_edges(caller);
CREATE INDEX IF NOT EXISTS idx_call_callee ON reverse_call_edges(callee);
CREATE INDEX IF NOT EXISTS idx_call_site ON reverse_call_edges(callsite);

-- ── 6. CONSTANTS ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_constants (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    function_id         INTEGER REFERENCES reverse_functions(id),
    address             TEXT NOT NULL,
    value               TEXT NOT NULL,
    width               INTEGER,
    context             TEXT,
    classification      TEXT NOT NULL DEFAULT 'UNKNOWN' CHECK (classification IN (
                            'OPCODE', 'ECU_SERVICE_ID', 'TIMEOUT', 'BAUD',
                            'ADDRESS', 'MASK', 'LENGTH', 'STATE',
                            'CHECKSUM_CONSTANT', 'UNKNOWN'))
);
CREATE INDEX IF NOT EXISTS idx_const_val ON reverse_constants(value);
CREATE INDEX IF NOT EXISTS idx_const_class ON reverse_constants(classification);

-- ── 7. VTABLES ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_vtables (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    address             TEXT NOT NULL,             -- VTable address in memory
    slot                INTEGER NOT NULL,          -- Offset in bytes (e.g. 0x108)
    target_function     TEXT NOT NULL,             -- Target function VA
    class_candidate     TEXT,
    confidence          TEXT DEFAULT 'HIGH',
    UNIQUE (address, slot)
);
CREATE INDEX IF NOT EXISTS idx_vtable_addr ON reverse_vtables(address);
CREATE INDEX IF NOT EXISTS idx_vtable_tgt ON reverse_vtables(target_function);

-- ── 8. DECOMPILER CHUNKS ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_decompiler_chunks (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    function_id         INTEGER NOT NULL REFERENCES reverse_functions(id) UNIQUE,
    decompiler_text     TEXT,
    pcode_text          TEXT,
    assembly_text       TEXT,
    ghidra_version      TEXT,
    analysis_sha256     TEXT
);

-- ── 9. RUNTIME EVENTS ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_runtime_events (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp           TEXT NOT NULL,
    trace_id            TEXT NOT NULL,
    api                 TEXT NOT NULL,             -- 'FT_Write', 'FT_Read', 'FT_SetBaudRate', etc.
    direction           TEXT CHECK (direction IN ('TX', 'RX', 'INTERNAL')),
    buffer_hex          TEXT,
    length              INTEGER,
    return_value        INTEGER,
    thread_id           INTEGER,
    callsite            TEXT,
    mapped_function     TEXT
);
CREATE INDEX IF NOT EXISTS idx_runtime_trace ON reverse_runtime_events(trace_id);
CREATE INDEX IF NOT EXISTS idx_runtime_api ON reverse_runtime_events(api);

-- ── 10. PROTOCOL FRAMES ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_protocol_frames (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    layer               TEXT NOT NULL CHECK (layer IN ('ECU_PROTOCOL', 'ADAPTER_PROTOCOL', 'FTDI_TRANSPORT')),
    direction           TEXT CHECK (direction IN ('HOST_TO_ADAPTER', 'ADAPTER_TO_HOST', 'HOST_TO_ECU', 'ECU_TO_HOST')),
    frame_hex           TEXT NOT NULL,
    parser_function     TEXT,
    builder_function    TEXT,
    checksum_valid      INTEGER DEFAULT 1,
    runtime_trace_id    TEXT,
    evidence_status     TEXT NOT NULL CHECK (evidence_status IN (
                            'RAW', 'PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH',
                            'INFERRED', 'UNKNOWN', 'CONFLICT', 'RETRACTED', 'OUT_OF_SCOPE_SECURITY'))
);

-- ── 11. TRANSFORM EDGES ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reverse_transform_edges (
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
    from_function           TEXT NOT NULL,
    to_function             TEXT NOT NULL,
    input_representation    TEXT NOT NULL,
    output_representation   TEXT NOT NULL,
    transform_description   TEXT,
    evidence_status         TEXT NOT NULL CHECK (evidence_status IN (
                                'RAW', 'PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH',
                                'INFERRED', 'UNKNOWN', 'CONFLICT', 'RETRACTED', 'OUT_OF_SCOPE_SECURITY'))
);

-- ── 12. EPISTEMIC MODEL: CLAIMS ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS claims (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_key           TEXT NOT NULL UNIQUE,
    statement           TEXT NOT NULL,
    evidence_status     TEXT NOT NULL CHECK (evidence_status IN (
                            'RAW', 'PROVEN_STATIC', 'PROVEN_DYNAMIC', 'PROVEN_BOTH',
                            'INFERRED', 'UNKNOWN', 'CONFLICT', 'RETRACTED', 'OUT_OF_SCOPE_SECURITY')),
    binary_sha256       TEXT NOT NULL,
    function_address    TEXT,
    callsite            TEXT,
    confidence          TEXT DEFAULT 'HIGH',
    missing_evidence    TEXT,
    provenance          TEXT,
    created_at          TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_claim_key ON claims(claim_key);
CREATE INDEX IF NOT EXISTS idx_claim_status ON claims(evidence_status);

-- ── 13. RETRACTIONS ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS retractions (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    claim_key           TEXT NOT NULL,
    prior_statement     TEXT NOT NULL,
    reason              TEXT NOT NULL,
    corrected_statement TEXT,
    retracted_at        TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_retract_key ON retractions(claim_key);

-- ── 14. GAPS (MISSING EVIDENCE / EDGES) ─────────────────────────────────────
CREATE TABLE IF NOT EXISTS gaps (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    priority            TEXT NOT NULL CHECK (priority IN ('P0', 'P1', 'P2')),
    title               TEXT NOT NULL,
    description         TEXT NOT NULL,
    required_evidence   TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'WONT_FIX')),
    created_at          TEXT DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_gap_prio ON gaps(priority);

-- ── 15. CONFLICTS ───────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conflicts (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    topic               TEXT NOT NULL,
    party_a             TEXT NOT NULL,
    party_b             TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'UNRESOLVED' CHECK (status IN ('UNRESOLVED', 'RESOLVED')),
    created_at          TEXT DEFAULT (datetime('now'))
);

-- ── 16. FULL-TEXT SEARCH (FTS5) ─────────────────────────────────────────────
CREATE VIRTUAL TABLE IF NOT EXISTS functions_fts USING fts5(
    address, assigned_name, original_name, namespace, signature, semantic_category,
    content='reverse_functions', content_rowid='id', tokenize='unicode61'
);

CREATE TRIGGER IF NOT EXISTS trg_funcs_ai AFTER INSERT ON reverse_functions BEGIN
    INSERT INTO functions_fts(rowid, address, assigned_name, original_name, namespace, signature, semantic_category)
    VALUES (new.id, new.address, new.assigned_name, new.original_name, new.namespace, new.signature, new.semantic_category);
END;

CREATE VIRTUAL TABLE IF NOT EXISTS strings_fts USING fts5(
    address, value,
    content='reverse_strings', content_rowid='id', tokenize='unicode61'
);

CREATE TRIGGER IF NOT EXISTS trg_strings_ai AFTER INSERT ON reverse_strings BEGIN
    INSERT INTO strings_fts(rowid, address, value)
    VALUES (new.id, new.address, new.value);
END;

CREATE VIRTUAL TABLE IF NOT EXISTS claims_fts USING fts5(
    claim_key, statement, provenance,
    content='claims', content_rowid='id', tokenize='unicode61'
);

CREATE TRIGGER IF NOT EXISTS trg_claims_ai AFTER INSERT ON claims BEGIN
    INSERT INTO claims_fts(rowid, claim_key, statement, provenance)
    VALUES (new.id, new.claim_key, new.statement, new.provenance);
END;

CREATE VIRTUAL TABLE IF NOT EXISTS decompiler_fts USING fts5(
    decompiler_text, assembly_text,
    content='reverse_decompiler_chunks', content_rowid='id', tokenize='unicode61'
);

CREATE TRIGGER IF NOT EXISTS trg_decomp_ai AFTER INSERT ON reverse_decompiler_chunks BEGIN
    INSERT INTO decompiler_fts(rowid, decompiler_text, assembly_text)
    VALUES (new.id, new.decompiler_text, new.assembly_text);
END;
