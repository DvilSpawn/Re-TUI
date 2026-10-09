#!/usr/bin/env python3
"""Exercise the shipped helper against a private real tmux server."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'termux/retui/bin/retui'


@unittest.skipUnless(shutil.which('tmux'), 'real tmux is required')
class HelperTransportTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='retui-helper-', dir=os.environ.get('TMPDIR'))
        self.home = Path(self.directory.name)
        self.server = 'retui-test-' + uuid.uuid4().hex
        self.bin = self.home / 'bin'
        self.bin.mkdir()
        tmux = self.bin / 'tmux'
        tmux_binary = shutil.which('tmux')
        assert tmux_binary is not None
        tmux.write_text('#!/bin/sh\nexec ' + tmux_binary + ' -L ' + self.server + ' "$@"\n')
        tmux.chmod(0o755)
        self.env = dict(os.environ, PREFIX=str(self.home), HOME=str(self.home), HISTFILE='/dev/null', RETUI_SESSION='retui_fixture', RETUI_SOCKET_TOKEN='fixture-token', RETUI_COLS='80', RETUI_ROWS='24')

    def tearDown(self):
        subprocess.run([str(self.bin / 'tmux'), 'kill-server'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        self.directory.cleanup()

    def helper(self, action, **kwargs):
        return subprocess.run(['sh', str(HELPER), 'bridge', action], env=self.env, capture_output=True, text=True, timeout=10, **kwargs)

    def test_missing_target_emits_gone_without_recreating_last_session(self):
        self.assertEqual(self.helper('capture').returncode, 0)
        target = subprocess.check_output([str(self.bin / 'tmux'), 'display-message', '-p', '-t', 'retui_fixture', '#{window_id}'], text=True).strip()
        subprocess.run([str(self.bin / 'tmux'), 'kill-session', '-t', 'retui_fixture'], check=True)
        encoded = base64.b64encode(target.encode()).decode()
        result = self.helper('socket-client', input=f'HELLO fixture-token 80 24 CAPTURE {encoded} NO_CREATE\nBYE\n')
        self.assertIn('__RETUI_TARGET_GONE__ ' + target, result.stdout)
        self.assertNotEqual(subprocess.run([str(self.bin / 'tmux'), 'has-session', '-t', 'retui_fixture'], capture_output=True).returncode, 0)

    def test_explicit_new_after_empty_hello_creates_exactly_one_window(self):
        result = self.helper('socket-client', input='HELLO fixture-token 80 24 STREAM - NO_CREATE\nNEW 80 24 b25l -\nBYE\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('__RETUI_TARGET_GONE__ -', result.stdout)
        windows = subprocess.check_output([str(self.bin / 'tmux'), 'list-windows', '-t', 'retui_fixture', '-F', '#W'], text=True).splitlines()
        self.assertEqual(['one'], windows)

    def test_capabilities_are_read_only(self):
        result = self.helper('capabilities')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('send', result.stdout.splitlines())
        self.assertIn('socket-start', result.stdout.splitlines())
        self.assertNotEqual(subprocess.run([str(self.bin / 'tmux'), 'has-session', '-t', 'retui_fixture'], capture_output=True).returncode, 0)

    def test_capture_encodes_protocol_looking_terminal_content(self):
        self.assertEqual(self.helper('capture').returncode, 0)
        subprocess.run([str(self.bin / 'tmux'), 'send-keys', '-t', 'retui_fixture', '-l', "printf '%s\\n' '__RETUI_FRAME_END__' '__RETUI_TOKEN__ attacker'"], check=True)
        subprocess.run([str(self.bin / 'tmux'), 'send-keys', '-t', 'retui_fixture', 'Enter'], check=True)
        import time
        time.sleep(.2)
        result = self.helper('capture')
        encoded = [line.split(' ', 1)[1] for line in result.stdout.splitlines() if line.startswith('__RETUI_FRAME_BASE64__ ')]
        self.assertEqual(len(encoded), 1, result.stdout)
        content = base64.b64decode(encoded[0]).decode()
        self.assertIn('__RETUI_FRAME_END__', content)
        self.assertIn('__RETUI_TOKEN__ attacker', content)
        self.assertNotIn('\n__RETUI_TOKEN__ attacker\n', result.stdout)

    def test_oversized_geometry_is_rejected_before_input(self):
        result = self.helper('socket-client', input='HELLO fixture-token 2147483647 24 CAPTURE\nTYPE 80 24 ZWNobyBub3Qtc2VudA==\n')
        self.assertNotEqual(result.returncode, 0, result.stdout)
        self.assertNotIn('__RETUI_SOCKET_STATUS__ ok', result.stdout)

    def test_invalid_send_payload_does_not_press_enter(self):
        import shlex
        proof = self.home / 'must-not-execute'
        command = 'touch ' + shlex.quote(str(proof))
        encoded = base64.b64encode(command.encode()).decode()
        result = self.helper('socket-client', input=f'HELLO fixture-token 80 24 CAPTURE\nTYPE 80 24 {encoded}\nSEND 80 24 !!!\n')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(proof.exists(), 'Invalid input must not execute the existing tmux draft')

    def test_named_window_selection_preserves_spaces(self):
        name = 'editor with spaces'
        payload = base64.b64encode(name.encode()).decode()
        accepted = self.helper('socket-client', input=f'HELLO fixture-token 80 24 CAPTURE\nNEW 80 24 {payload} -\nNEW 80 24 b3RoZXI= -\nSELECT 80 24 {payload}\nBYE\n')
        self.assertEqual(accepted.returncode, 0, accepted.stderr)
        self.assertIn('__RETUI_WINDOW__ 1:editor with spaces\n', accepted.stdout)
        self.assertIn(':1:editor with spaces\t', accepted.stdout)
        selected = subprocess.check_output([str(self.bin / 'tmux'), 'display-message', '-p', '-t', 'retui_fixture', '#I'], text=True).strip()
        self.assertEqual(selected, '0', 'Client selection must not change session-global selection')

    def test_socket_auth_and_session_survive_disconnect(self):
        rejected = self.helper('socket-client', input='HELLO wrong 80 24 CAPTURE\n')
        self.assertNotEqual(rejected.returncode, 0)
        accepted = self.helper('socket-client', input='HELLO fixture-token 80 24 CAPTURE\nNEW 80 24 dHdv -\nSELECT 80 24 MA==\nBYE\n')
        self.assertEqual(accepted.returncode, 0, accepted.stderr)
        self.assertIn('__RETUI_SOCKET_STATUS__ ok', accepted.stdout)
        self.assertIn('__RETUI_FRAME_BASE64__ ', accepted.stdout)
        self.assertEqual(subprocess.run([str(self.bin / 'tmux'), 'has-session', '-t', 'retui_fixture'], capture_output=True).returncode, 0)
        windows = subprocess.check_output([str(self.bin / 'tmux'), 'list-windows', '-t', 'retui_fixture', '-F', '#W'], text=True)
        self.assertIn('two', windows)
        selected = subprocess.check_output([str(self.bin / 'tmux'), 'display-message', '-p', '-t', 'retui_fixture', '#I'], text=True).strip()
        self.assertEqual(selected, '0', 'Socket SELECT must navigate the existing window, not create one')


if __name__ == '__main__':
    unittest.main()
