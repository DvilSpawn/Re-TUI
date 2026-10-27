#!/usr/bin/env python3
"""Exercise the bundled terminal renderer without network access or Termux."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/terminal-widgets/dashboard.sh'

class TerminalWidgetsTest(unittest.TestCase):
    def render(self, mode, width=40, location=''):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tput').write_text('#!/bin/sh\ncase "$1" in cols) echo ' + str(width) + ';; *) echo 12;; esac\n')
            (root / 'curl').write_text('#!/bin/sh\nexit 1\n')
            for name in ('tput', 'curl'):
                (root / name).chmod(0o755)
            env = dict(os.environ, PATH=str(root) + ':' + os.environ['PATH'], HOME=directory,
                       XDG_CACHE_HOME=str(root / 'cache'), RETUI_WIDGET_ONCE='1')
            return subprocess.run(['bash', str(SCRIPT), mode, location], env=env,
                                  capture_output=True, text=True, timeout=5, check=True).stdout

    def test_large_clock_and_cursor_restoration(self):
        result = self.render('clock')
        self.assertIn('███', result)
        self.assertNotIn('#', result)
        self.assertIn('\x1b[?25h', result)
        self.assertNotIn('Weather', result)
        self.assertIn('\x1b[?1049h', result)
        self.assertIn('\x1b[K', result)
        self.assertIn('\x1b[?1049l', result)
        self.assertNotIn('\x1b[2J', result, 'A redraw must never expose a blank screen')
        self.assertEqual(1, result.count('\x1b[H'))

    def test_small_clock_uses_compact_layout(self):
        result = self.render('clock', 20)
        self.assertNotIn('###', result)
        self.assertRegex(result, r'\d\d:\d\d:\d\d')

    def test_fetch_has_local_system_information(self):
        result = self.render('fetch', 80)
        for label in ('RE:TUI', 'Termux', 'Android:', 'Kernel:', 'Arch:', 'RAM:', 'Uptime:', 'Home:'):
            self.assertIn(label, result)
        # The spinning terminal starts face on: half blocks with a white prompt on a black body,
        # one art row beside each detail line and nothing after the last one.
        self.assertIn('\u2580', result)
        self.assertIn('\x1b[0;97;107m', result)
        self.assertRegex(result, r'\x1b\[0m\x1b\[0;36m  RE:TUI / Termux')
        self.assertIn('Shell: Bash', result.split('\x1b[K\n')[10])
        self.assertEqual(11, result.count('\x1b[K\n'))
        self.assertNotIn('Weather', result)
        self.assertRegex(result, r'\d\d:\d\d:\d\d')

    def test_compact_fetch_prioritizes_data(self):
        result = self.render('fetch', 40)
        self.assertIn('RE:TUI / Termux', result)
        self.assertIn('RAM:', result)
        self.assertNotIn('\u2580', result, 'A narrow tile shows the details instead of a squeezed skull')

    def test_weather_failure_remains_readable(self):
        result = self.render('weather', location="City /? & ' test")
        self.assertIn('Weather unavailable', result)
        self.assertIn('\x1b[?25h', result)

if __name__ == '__main__':
    unittest.main()
