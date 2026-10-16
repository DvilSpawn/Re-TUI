#!/usr/bin/env python3
"""Exercise the SSH trust boundary with real tmux on an isolated server."""
import base64
import errno
import time
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
DISPATCH = ROOT / 'app/src/main/assets/termux-ssh/dispatch.sh'


@unittest.skipUnless(shutil.which('tmux'), 'real tmux is required')
class SshDispatchTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='retui-ssh-')
        self.home = Path(self.temporary.name)
        self.bin = self.home / 'bin'
        self.bin.mkdir()
        self.tmux = self.bin / 'tmux'
        self.tmux.write_text('#!/bin/sh\nexec ' + shutil.which('tmux') + ' -L retui-ssh-' + uuid.uuid4().hex + ' "$@"\n')
        self.tmux.chmod(0o700)
        self.env = dict(os.environ, HOME=str(self.home), PREFIX=str(self.home), HISTFILE='/dev/null',
                        PATH=str(self.bin) + os.pathsep + os.environ['PATH'], TERM='xterm-256color')

    def tearDown(self):
        subprocess.run([str(self.tmux), 'kill-server'], capture_output=True)
        # tmux exits before its shells finish handling SIGHUP and writing their history.
        deadline = time.monotonic() + 2
        while True:
            try:
                self.temporary.cleanup()
                break
            except OSError as error:
                if error.errno != errno.ENOTEMPTY or time.monotonic() >= deadline:
                    raise
                time.sleep(.02)

    def dispatch(self, command):
        return subprocess.run(['sh', str(DISPATCH)], env=dict(self.env, SSH_ORIGINAL_COMMAND=command),
                              capture_output=True, text=True, timeout=5)

    def windows(self):
        return subprocess.check_output([str(self.tmux), 'list-windows', '-t', '=retui_workspace',
                                        '-F', '#{window_id}:#{window_name}'], text=True).splitlines()

    def test_read_only_open_and_validation_never_create_shell(self):
        result = self.dispatch('list')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('', result.stdout)
        for command in ('attach ../../bad @0', 'new !!! -', 'new - !!!', 'list ; touch injected', 'mode @0', 'mode @0 extra', 'sh'):
            self.assertNotEqual(0, self.dispatch(command).returncode, command)
        self.assertNotEqual(0, subprocess.run([str(self.tmux), 'has-session', '-t', '=retui_workspace'],
                                            capture_output=True).returncode)

    def test_new_preserves_named_windows_and_literal_command(self):
        import time
        name = base64.b64encode('editor: with spaces'.encode()).decode()
        command = base64.b64encode("printf '%s\\n' 'ssh literal proof'".encode()).decode()
        first = self.dispatch(f'new {name} {command}')
        self.assertEqual(0, first.returncode, first.stderr)
        self.assertRegex(first.stdout.strip(), r'^@[0-9]+$')
        second = self.dispatch('new - -')
        self.assertEqual(0, second.returncode, second.stderr)
        self.assertEqual(2, len(self.windows()))
        self.assertIn(':editor: with spaces', '\n'.join(self.windows()))
        listed = self.dispatch('list')
        self.assertIn(first.stdout.strip() + ':0:editor: with spaces', listed.stdout)
        time.sleep(.15)
        screen = subprocess.check_output([str(self.tmux), 'capture-pane', '-p', '-t', first.stdout.strip()], text=True)
        self.assertIn('ssh literal proof', screen)

    def test_mode_reads_actual_pane_alternate_buffer_and_rejects_unrelated_windows(self):
        import time
        target = self.dispatch('new - -').stdout.strip()
        self.assertEqual('0', self.dispatch('mode ' + target).stdout.strip())
        for escape, expected in [('h', '1'), ('l', '0')]:
            subprocess.run([str(self.tmux), 'send-keys', '-t', target, '-l', "printf '\\033[?1049" + escape + "'"], check=True)
            subprocess.run([str(self.tmux), 'send-keys', '-t', target, 'Enter'], check=True)
            deadline = time.monotonic() + 2
            while time.monotonic() < deadline:
                result = self.dispatch('mode ' + target)
                if result.stdout.strip() == expected: break
                time.sleep(.02)
            self.assertEqual(expected, result.stdout.strip(), result.stderr)
        stranger = subprocess.check_output([str(self.tmux), 'new-session', '-d', '-P', '-F', '#{window_id}', '-s', 'unrelated'], text=True).strip()
        rejected = self.dispatch('mode ' + stranger)
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn('Workspace window no longer exists', rejected.stderr)

    def test_grouped_selection_and_cleanup_preserve_workspace_jobs(self):
        first = self.dispatch('new - -').stdout.strip()
        second = self.dispatch('new - -').stdout.strip()
        # Real grouped-session operations used by attach; no terminal or user server is needed.
        for viewer, target in [('retui_view_one', first), ('retui_view_two', second)]:
            subprocess.run([str(self.tmux), 'new-session', '-d', '-s', viewer, '-t', 'retui_workspace'], check=True)
            subprocess.run([str(self.tmux), 'select-window', '-t', viewer + ':' + target], check=True)
        for viewer, expected in [('retui_view_one', first), ('retui_view_two', second)]:
            actual = subprocess.check_output([str(self.tmux), 'display-message', '-p', '-t', viewer,
                                              '#{window_id}'], text=True).strip()
            self.assertEqual(expected, actual)
            subprocess.run([str(self.tmux), 'kill-session', '-t', '=' + viewer], check=True)
        self.assertEqual(2, len(self.windows()))

    def test_real_dispatch_pty_streams_independent_tiles_and_detach_preserves_jobs(self):
        import fcntl
        import pty
        import select
        import struct
        import termios
        import time
        targets = [self.dispatch('new - -').stdout.strip() for _ in range(2)]
        clients = []
        try:
            for tile, target in enumerate(targets):
                terminal, slave = pty.openpty()
                process = subprocess.Popen(['/bin/sh', str(DISPATCH)], stdin=slave, stdout=slave, stderr=slave,
                    start_new_session=True, env=dict(self.env, SSH_ORIGINAL_COMMAND=f'attach pty_proof_{tile} {target}'))
                os.close(slave)
                clients.append((process, terminal))
                fcntl.ioctl(terminal, termios.TIOCSWINSZ, struct.pack('HHHH', 20 + tile * 4, 60 + tile * 10, 0, 0))
                os.set_blocking(terminal, False)

            for tile, (process, terminal) in enumerate(clients):
                suffix = uuid.uuid4().hex
                marker = ('PTY_STREAM_' + suffix).encode()
                # The complete marker never appears in the echoed command: shell execution is required.
                os.write(terminal, f"printf '%s%s\\n' 'PTY_STREAM_' '{suffix}'\r".encode())
                output = bytearray()
                deadline = time.monotonic() + 5
                while marker not in output and time.monotonic() < deadline:
                    if select.select([terminal], [], [], .1)[0]:
                        try: output.extend(os.read(terminal, 65536))
                        except OSError: break
                self.assertIn(marker, output, f'Tile {tile} did not stream shell output: {output!r}')
                selected = subprocess.check_output([str(self.tmux), 'display-message', '-p',
                    '-t', f'retui_view_pty_proof_{tile}', '#{window_id}'], text=True).strip()
                self.assertEqual(targets[tile], selected)

            for tile, (process, terminal) in enumerate(clients):
                subprocess.run([str(self.tmux), 'detach-client', '-s', f'=retui_view_pty_proof_{tile}'], check=True)
                deadline = time.monotonic() + 3
                while process.poll() is None and time.monotonic() < deadline:
                    if select.select([terminal], [], [], .05)[0]:
                        try: os.read(terminal, 65536)
                        except OSError: pass
                self.assertEqual(0, process.wait(timeout=3), 'Dispatcher failed after detach')
                alive = subprocess.run([str(self.tmux), 'has-session', '-t', f'=retui_view_pty_proof_{tile}'], capture_output=True)
                self.assertNotEqual(0, alive.returncode, 'Detached viewer was not cleaned up')
            self.assertEqual(set(targets), {line.split(':', 1)[0] for line in self.windows()})
        finally:
            for process, terminal in clients:
                if process.poll() is None:
                    try: process.kill()
                    except ProcessLookupError: pass
                    os.close(terminal)
                    process.wait(timeout=3)
                else: os.close(terminal)



if __name__ == '__main__':
    unittest.main()
