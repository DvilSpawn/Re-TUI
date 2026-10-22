import sys
from pathlib import Path
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_settings import parse_instrumentation, inventory

class VerificationReportTest(unittest.TestCase):
    def test_reports_failures_and_assumptions_instead_of_trusting_runner_exit_code(self):
        text='''INSTRUMENTATION_STATUS: class=Example
INSTRUMENTATION_STATUS: test=passed
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS: class=Example
INSTRUMENTATION_STATUS: test=failed
INSTRUMENTATION_STATUS: stack=AssertionError: actual differs
  at example:1
INSTRUMENTATION_STATUS_CODE: -2
INSTRUMENTATION_STATUS: class=Example
INSTRUMENTATION_STATUS: test=needsHuman
INSTRUMENTATION_STATUS: stack=AssumptionViolatedException: unlock required
INSTRUMENTATION_STATUS_CODE: -4
INSTRUMENTATION_CODE: -1
'''
        rows=parse_instrumentation(text)
        self.assertEqual(['passed','failed','skipped'],[r['status'] for r in rows])
        self.assertIn('at example:1',rows[1]['reason'])
        self.assertIn('unlock required',rows[2]['reason'])
    def test_inventory_keeps_release_and_staged_cases_visible(self):
        rows=inventory()
        self.assertTrue(any(r['kind']=='release' for r in rows))
        self.assertTrue(any(r['staged'] for r in rows))
        self.assertTrue(any(r['className'].endswith('SettingsReloadIntegrationTest') for r in rows))

    def test_inventory_selects_test_class_instead_of_fixture_helper(self):
        rows=inventory()
        self.assertFalse(any(r["className"].endswith("SavedAppearanceState") for r in rows))
        self.assertTrue(any(r["className"].endswith("SettingsPreviewIntegrationTest") for r in rows))
