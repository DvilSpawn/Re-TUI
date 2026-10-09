#!/usr/bin/env python3
"""Check the actual workspace shell template without Android or Termux."""
import json
from pathlib import Path
import shlex
import subprocess
import textwrap
import unittest


class TermuxWorkspaceTest(unittest.TestCase):
    def test_tmux_command_is_language_independent_and_preserves_arguments(self):
        root = Path(__file__).resolve().parents[1]
        helper = (root / 'termux/retui/bin/retui').read_text()
        method = helper.split('tmux_new_with_shell() {', 1)[1].split('\n}', 1)[0]
        self.assertNotIn('getString', method)
        template = 'tmux_new_with_shell() {' + method + '\n}'
        # Keep the unused catalog key accepted so existing packs still import.
        key = 'surface_if_retui_workspace_shell_bash_then_tmux_retui_workspace_sh_4a152'
        self.assertIn(key, json.loads((root / 'app/src/main/assets/localization/catalog.json').read_text()))
        for operation in ('new-session', 'new-window'):
            args = [operation, '-n', "window's name; $(exit 99)"]
            for shell in ('/termux/bin/bash', '/termux/bin/sh'):
                with self.subTest(operation=operation, shell=shell):
                    script = 'tmux() { printf "%s\\n" "$@"; }\n'
                    script += 'bridge_shell() { printf \"%s\\n\" ' + shlex.quote(shell) + '; }\n'
                    script += template + '\ntmux_new_with_shell ' + shlex.join(args)
                    checked = subprocess.run(['sh', '-n', '-c', script], capture_output=True, text=True)
                    self.assertEqual(checked.returncode, 0, checked.stderr)
                    result = subprocess.run(['sh', '-c', script], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    expected = args + [shell] + (['--noprofile', '--norc'] if shell.endswith('/bash') else [])
                    self.assertEqual(result.stdout.splitlines(), expected)


if __name__ == '__main__':
    unittest.main()
