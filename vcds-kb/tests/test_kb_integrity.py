#!/usr/bin/env python3
"""
Unit tests for VCDS Knowledge Base Integrity (test_kb_integrity.py).
Executed by CI to ensure all epistemic and structural constraints pass.
"""

import json
import sqlite3
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
KB_DIR = ROOT / "vcds-kb"
DB_PATH = KB_DIR / "vcds_kb.db"
MANIFEST_PATH = KB_DIR / "remote" / "manifest.json"
COVERAGE_PATH = ROOT / "reverse" / "COVERAGE.json"
INVENTORY_PATH = ROOT / "reverse" / "installation_inventory.jsonl"

ORIGINAL_VCDS_SHA256 = "CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3"
UNPACKED_VCDS_SHA256 = "4F9BA9B39523512AA1F985FB4AFA77D21987D345BAEEF12AD9CED62D35A09AB5"

class TestVcdsKnowledgeBase(unittest.TestCase):
    
    @classmethod
    def setUpClass(cls):
        if not DB_PATH.exists():
            from ingest_all import run_pipeline
            run_pipeline()
        cls.conn = sqlite3.connect(str(DB_PATH))
        cls.conn.row_factory = sqlite3.Row

    @classmethod
    def tearDownClass(cls):
        cls.conn.close()

    def test_01_installation_inventory_counts(self):
        """Ensure full installation inventory exists with >= 20000 files and valid SHA256."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM installation_inventory")
        total = cur.fetchone()[0]
        self.assertGreaterEqual(total, 20000, "Installation inventory must contain all VCDS files")
        
        cur.execute("SELECT COUNT(*) FROM installation_inventory WHERE length(sha256) != 64")
        invalid_hashes = cur.fetchone()[0]
        self.assertEqual(invalid_hashes, 0, "All files must have a valid 64-char SHA256")

    def test_02_binary_provenance_and_lineage(self):
        """Ensure original VCDS.EXE and unpacked binary have distinct records and explicit lineage."""
        cur = self.conn.cursor()
        cur.execute("SELECT sha256, artifact_type, parent_sha256, address_equivalence_status FROM reverse_binaries")
        rows = {r["sha256"]: r for r in cur.fetchall()}
        
        self.assertIn(ORIGINAL_VCDS_SHA256, rows, "Original VCDS.EXE must be registered")
        self.assertEqual(rows[ORIGINAL_VCDS_SHA256]["artifact_type"], "ORIGINAL_INSTALLATION")
        
        self.assertIn(UNPACKED_VCDS_SHA256, rows, "Unpacked VCDS must be registered as separate binary")
        self.assertEqual(rows[UNPACKED_VCDS_SHA256]["artifact_type"], "DERIVED_UNPACKED")
        self.assertEqual(rows[UNPACKED_VCDS_SHA256]["parent_sha256"], ORIGINAL_VCDS_SHA256)
        self.assertEqual(rows[UNPACKED_VCDS_SHA256]["address_equivalence_status"], "PROVEN_EQUIVALENT")

    def test_03_function_accounting_completeness(self):
        """Ensure every function has an explicit accounting status and no generic UNKNOWN skips."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE accounting_status = 'UNKNOWN' OR accounting_status IS NULL")
        unaccounted = cur.fetchone()[0]
        self.assertEqual(unaccounted, 0, "No function should remain unaccounted")
        
        cur.execute("SELECT COUNT(*) FROM reverse_functions WHERE accounting_status = 'SKIPPED_WITH_REASON' AND (skip_reason IS NULL OR skip_reason = '')")
        bad_skips = cur.fetchone()[0]
        self.assertEqual(bad_skips, 0, "Skipped functions must document a specific skip_reason")

    def test_04_indirect_call_evidence_requirement(self):
        """Ensure no indirect call is marked resolved without explicit resolution method and evidence."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM reverse_call_edges WHERE is_resolved = 1 AND (resolution_evidence IS NULL OR resolution_method = 'UNRESOLVED')")
        invalid_resolved = cur.fetchone()[0]
        self.assertEqual(invalid_resolved, 0, "Resolved indirect calls must have documented proof")

    def test_05_cross_module_graph(self):
        """Ensure cross-module dependency edges connect VCDS.EXE to RTUS64.dll and other modules."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM cross_module_edges WHERE to_module LIKE '%RTUS%'")
        rtus_edges = cur.fetchone()[0]
        self.assertGreater(rtus_edges, 0, "Must have verified cross-module edges to RTUS64.dll")

    def test_06_retractions_and_p0_gaps(self):
        """Ensure the 5 historical retractions and 2 open P0 gaps are strictly maintained."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM retractions")
        self.assertGreaterEqual(cur.fetchone()[0], 5, "Must maintain all 5 retractions")
        
        cur.execute("SELECT COUNT(*) FROM gaps WHERE priority = 'P0' AND status = 'OPEN'")
        self.assertGreaterEqual(cur.fetchone()[0], 2, "Must quarantine open P0 gaps")

    def test_07_coverage_metrics_no_hardcoding(self):
        """Ensure COVERAGE.json metrics match live database counts exactly."""
        self.assertTrue(COVERAGE_PATH.exists(), "COVERAGE.json must exist")
        with open(COVERAGE_PATH, "r", encoding="utf-8") as f:
            cov = json.load(f)
            
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM reverse_functions")
        db_funcs = cur.fetchone()[0]
        self.assertEqual(cov["binary_functions_total"], db_funcs)

    def test_08_cross_module_and_non_pe_resource_counts(self):
        """Ensure all 3,977 cross-module edges and 23,198 non-PE resources are recorded in DB."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM cross_module_edges")
        n_edges = cur.fetchone()[0]
        self.assertGreaterEqual(n_edges, 3977, "All 3977 cross-module edges must be recorded")
        
        cur.execute("SELECT COUNT(*) FROM non_pe_resources")
        n_resources = cur.fetchone()[0]
        self.assertGreaterEqual(n_resources, 23198, "All 23198 non-PE resource bindings must be recorded")

    def test_09_full_vtable_discovery(self):
        """Ensure full vtable discovery found >= 100 vtables, >= 7000 slots, and verified transport slot 0x108."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(DISTINCT address) FROM reverse_vtables")
        n_vt = cur.fetchone()[0]
        self.assertGreaterEqual(n_vt, 100, "Must discover >= 100 vtables with constructor proof")
        
        cur.execute("SELECT COUNT(*) FROM reverse_vtables")
        n_slots = cur.fetchone()[0]
        self.assertGreaterEqual(n_slots, 7000, "Must discover >= 7000 vtable slots")
        
        cur.execute("SELECT target_function FROM reverse_vtables WHERE address = '0x1401AD3C0' AND slot = 264")
        row = cur.fetchone()
        self.assertIsNotNone(row, "Transport vtable slot 0x108 must exist")
        self.assertEqual(row[0], "0x14007E734", "Slot 0x108 must map strictly to vcds_adapter_send_frame")

    def test_10_all_pe_modules_analyzed(self):
        """Ensure all 14 PE modules have verified analysis status in installation inventory."""
        cur = self.conn.cursor()
        cur.execute("SELECT COUNT(*) FROM installation_inventory WHERE is_pe = 1 AND analysis_status = 'PENDING_GHIDRA_ANALYSIS'")
        pending = cur.fetchone()[0]
        self.assertEqual(pending, 0, "No PE module should remain pending analysis")
        
        cur.execute("SELECT COUNT(*) FROM installation_inventory WHERE is_pe = 1")
        total_pe = cur.fetchone()[0]
        self.assertEqual(total_pe, 14, "All 14 PE modules must be recorded in inventory")

if __name__ == "__main__":
    unittest.main()
