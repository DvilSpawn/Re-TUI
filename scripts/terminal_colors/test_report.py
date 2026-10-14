import tempfile
import unittest
from pathlib import Path
from report import status_for


class ReportEvidenceTest(unittest.TestCase):
    def test_missing_measurements_or_screenshots_never_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertEqual(status_for([], 'home', root), 'untested')
            row = {'surface': 'home', 'screenshot': 'view.png', 'samples': [{'status': 'pass'}]}
            self.assertEqual(status_for([row], 'home', root), 'blocked')
            (root / 'view.png').write_bytes(b'evidence')
            row['samples'] = []
            self.assertEqual(status_for([row], 'home', root), 'blocked')

    def test_exported_ratios_are_recomputed_and_missing_state_crops_block(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / 'view.png').write_bytes(b'evidence')
            row = {'surface': 'home', 'screenshot': 'view.png', 'samples': [
                {'status': 'pass', 'composited': '#ff777777', 'background': '#ffffffff', 'ratio': 7, 'threshold': 4.5}]}
            self.assertEqual(status_for([row], 'home', root), 'fail')
            row['samples'][0]['composited'] = '#ff000000'
            self.assertEqual(status_for([row], 'home', root), 'manual review required')
            row['controlStates'] = [{'crops': ['missing-state.png'], 'samples': []}]
            self.assertEqual(status_for([row], 'home', root), 'blocked')

    def test_disabled_and_authored_content_are_separate_from_required_contrast(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); (root / 'view.png').write_bytes(b'evidence')
            row = {'surface': 'termux', 'screenshot': 'view.png', 'samples': [
                {'status': 'manual review required', 'composited': '#ff000000', 'background': '#ff000000',
                 'ratio': 1, 'threshold': 4.5, 'reason': 'Content-authored truecolor/256 color'}]}
            self.assertEqual(status_for([row], 'termux', root), 'manual review required')
            row['samples'][0]['state'] = 'disabled'
            self.assertEqual(status_for([row], 'termux', root), 'manual review required')


if __name__ == '__main__':
    unittest.main()
