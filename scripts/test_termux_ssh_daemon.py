#!/usr/bin/env python3
"""Exercise PID recovery using isolated process-table fixtures, never a real user daemon."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class DaemonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='retui-daemon-')
        self.root = Path(self.temp.name)
        self.bin = self.root / 'prefix/bin'
        self.bin.mkdir(parents=True)
        self.home = self.root / 'home'
        self.state = self.home / '.retui/ssh'
        self.state.mkdir(parents=True)
        self.proc = self.root / 'proc'
        self.proc.mkdir()
        self.listener = subprocess.Popen(['sleep', '30'])
        self.pid = str(self.listener.pid)
        self.node = self.proc / self.pid
        self.node.mkdir()
        (self.node / 'exe').symlink_to(self.bin / 'sshd')
        (self.node / 'cmdline').write_bytes(('sshd\0-D\0-f\0' + str(self.state / 'sshd_config') + '\0').encode())
        (self.bin / 'sshd').write_text('#!/bin/sh\nprintf started > "$HOME/started"\n')
        (self.bin / 'sshd').chmod(0o700)
        source = (ROOT / 'app/src/main/assets/termux-ssh/daemon.sh').read_text()
        # Linux /proc is virtualized here so the same tests also run on macOS.
        source = source.replace('/proc/', str(self.proc) + '/')
        self.script = self.root / 'daemon.sh'
        self.script.write_text(source)
    def tearDown(self):
        self.listener.terminate()
        self.listener.wait(timeout=2)
        self.temp.cleanup()
    def run_daemon(self):
        result = subprocess.run(['sh', str(self.script)], env={**os.environ, 'HOME': str(self.home),
            'PREFIX': str(self.bin.parent)}, capture_output=True, text=True, timeout=3)
        self.assertEqual(0, result.returncode, result.stderr)
    def test_stale_pid_recovers_existing_owned_listener(self):
        (self.state / 'sshd.pid').write_text('99999999\n')
        self.run_daemon()
        self.assertEqual(self.pid, (self.state / 'sshd.pid').read_text().strip())
        self.assertFalse((self.home / 'started').exists())
    def test_current_pid_does_not_spawn_a_duplicate(self):
        (self.state / 'sshd.pid').write_text(self.pid)
        self.run_daemon()
        self.assertFalse((self.home / 'started').exists())
    def test_session_child_or_unrelated_config_is_not_a_listener(self):
        (self.state / 'sshd.pid').write_text(self.pid)
        (self.node / 'cmdline').write_bytes(b'sshd\0-f\0/other/sshd_config\0')
        self.run_daemon()
        self.assertTrue((self.home / 'started').exists())

if __name__ == '__main__': unittest.main()
