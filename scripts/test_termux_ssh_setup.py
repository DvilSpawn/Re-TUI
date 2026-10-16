#!/usr/bin/env python3
"""Fault/concurrency fixtures for setup; never reads or writes the user's Termux data."""
import base64
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import shutil
import sys
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SETUP = ROOT / 'app/src/main/assets/termux-ssh/setup.sh'
DISPATCH = ROOT / 'app/src/main/assets/termux-ssh/dispatch.sh'


@unittest.skipUnless(shutil.which('ssh-keygen'), 'OpenSSH key generation is required')
class SshSetupTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='retui-ssh-setup-')
        self.home = Path(self.temporary.name)
        self.prefix = self.home / 'prefix'
        self.bin = self.prefix / 'bin'
        self.bin.mkdir(parents=True)
        for command in ('base64', 'awk', 'tr', 'chmod', 'mkdir', 'mktemp', 'cat', 'rm', 'ln', 'mv', 'sh', 'grep', 'ssh-keygen', 'sleep', 'readlink'):
            (self.bin / command).symlink_to(shutil.which(command))
        # macOS has no flock command; exercise the same kernel advisory lock through fcntl.
        flock = self.bin / 'flock'
        flock.write_text('#!' + sys.executable + '\nimport fcntl, sys\ntry:\n fcntl.flock(int(sys.argv[-1]), fcntl.LOCK_UN if "-u" in sys.argv else fcntl.LOCK_EX | fcntl.LOCK_NB)\nexcept BlockingIOError:\n sys.exit(1)\n')
        flock.chmod(0o700)
        self.write_tool('whoami', "printf '%s\\n' u0_a1000")
        self.write_tool('tmux', 'exit 0')
        self.write_tool('sshd', '[ "${FIXTURE_CONFIG_FAIL:-0}" != 1 ]')
        self.write_tool('ssh-keyscan', 'printf scan >> "$HOME/scan.calls"; printf "%s" "${FIXTURE_SCAN:-}"')
        self.write_tool('pkg', '''printf '%s\\n' "$*" >> "$HOME/pkg.calls"
[ "${FIXTURE_PKG_FAIL:-0}" != 1 ] || exit 1
for package in "$@"; do
 case "$package" in
 tmux) printf '#!/bin/sh\\nexit 0\\n' > "$PREFIX/bin/tmux"; chmod 700 "$PREFIX/bin/tmux";;
 openssh) printf '#!/bin/sh\\nexit 0\\n' > "$PREFIX/bin/sshd"; chmod 700 "$PREFIX/bin/sshd";;
 esac
done''')
        key = self.home / 'app-key'
        subprocess.run([shutil.which('ssh-keygen'), '-q', '-t', 'ecdsa', '-b', '256', '-N', '', '-f', str(key)], check=True)
        self.public_key = key.with_suffix('.pub').read_text().strip()
        self.dispatcher = base64.b64encode(DISPATCH.read_bytes()).decode()
        self.env = dict(os.environ, HOME=str(self.home), PREFIX=str(self.prefix), PATH=str(self.bin))
        self.state = self.home / '.retui/ssh'
        self.setup_path = SETUP

    def write_tool(self, name, body):
        path = self.bin / name
        if path.is_symlink(): path.unlink()
        path.write_text('#!/bin/sh\nset -eu\n' + body + '\n')
        path.chmod(0o700)

    def tearDown(self):
        self.temporary.cleanup()

    def setup(self, install=False, public_key=None, dispatcher=None, **environment):
        return subprocess.run(['/bin/sh', str(self.setup_path), public_key or self.public_key, '1' if install else '0',
                               self.dispatcher if dispatcher is None else dispatcher],
                              env=dict(self.env, **environment), capture_output=True, text=True, timeout=10)

    def endpoint_files(self):
        return {name: (self.state / name).read_bytes() for name in ('authorized_keys', 'host_ecdsa', 'host_ecdsa.pub', 'dispatch.sh', 'sshd_config')}

    def test_setup_is_idempotent_private_and_does_not_touch_personal_ssh(self):
        personal = self.home / '.ssh'
        personal.mkdir()
        (personal / 'config').write_text('personal SSH config stays unchanged')
        first = self.setup()
        self.assertEqual(0, first.returncode, first.stderr + first.stdout)
        self.assertIn('__RETUI_SSH_USER__ u0_a1000', first.stdout)
        self.assertIn('__RETUI_SSH_PORT__ 8928', first.stdout)
        files = self.endpoint_files()
        second = self.setup()
        self.assertEqual(0, second.returncode, second.stderr + second.stdout)
        self.assertEqual(files, self.endpoint_files())
        self.assertEqual(first.stdout, second.stdout)
        self.assertEqual('personal SSH config stays unchanged', (personal / 'config').read_text())
        self.assertEqual(0o700, self.state.stat().st_mode & 0o777)
        for name in ('host_ecdsa', 'authorized_keys', 'sshd_config'):
            self.assertEqual(0o600, (self.state / name).stat().st_mode & 0o777)
        config = files['sshd_config'].decode()
        for directive in ('ListenAddress 127.0.0.1', 'AuthenticationMethods publickey', 'PasswordAuthentication no',
                          'KbdInteractiveAuthentication no', 'DisableForwarding yes', 'PermitUserRC no'):
            self.assertIn(directive, config)
        self.assertFalse(list(self.state.glob('.setup.*')))
        self.assertFalse((self.home / 'pkg.calls').exists())

    def test_missing_support_needs_explicit_enable_and_install_failure_is_actionable(self):
        (self.bin / 'tmux').unlink()
        (self.bin / 'sshd').unlink()
        rejected = self.setup()
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn('__RETUI_SSH_ERROR__ Enable Termux pane', rejected.stdout)
        self.assertFalse((self.home / 'pkg.calls').exists())
        failed = self.setup(install=True, FIXTURE_PKG_FAIL='1')
        self.assertNotEqual(0, failed.returncode)
        self.assertIn('__RETUI_SSH_ERROR__ Package installation failed', failed.stdout)
        installed = self.setup(install=True)
        self.assertEqual(0, installed.returncode, installed.stderr + installed.stdout)
        self.assertTrue(all(line == 'install -y tmux openssh' for line in (self.home / 'pkg.calls').read_text().splitlines()))

    def test_validation_failure_does_not_replace_a_working_endpoint(self):
        self.assertEqual(0, self.setup().returncode)
        previous = self.endpoint_files()
        for result in (self.setup(dispatcher='!!!'), self.setup(dispatcher=base64.b64encode(b'if broken').decode()),
                       self.setup(FIXTURE_CONFIG_FAIL='1'), self.setup(public_key='ecdsa-sha2-nistp256 invalid'),
                       self.setup(public_key=self.public_key + '\r')):
            self.assertNotEqual(0, result.returncode, result.stdout)
            self.assertIn('__RETUI_SSH_ERROR__', result.stdout)
            self.assertEqual(previous, self.endpoint_files())
            self.assertFalse(list(self.state.glob('.setup.*')))

    def fixture_listener(self, owned=True):
        pid = str(os.getpid())
        node = self.home / 'proc' / pid
        node.mkdir(parents=True)
        (node / 'exe').symlink_to(self.bin / 'sshd')
        (node / 'cmdline').write_bytes(('sshd\0-D\0-f\0' + str(self.state / ('sshd_config' if owned else 'other_config')) + '\0').encode())
        (self.state / 'sshd.pid').write_text(pid)
        self.setup_path = self.home / 'setup-fixture.sh'
        self.setup_path.write_text(SETUP.read_text().replace('/proc/', str(self.home / 'proc') + '/'))

    def test_owned_listener_reconnect_does_not_scan_without_authentication(self):
        self.assertEqual(0, self.setup().returncode)
        scans = (self.home / 'scan.calls').read_bytes()
        self.fixture_listener()
        result = self.setup()
        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertEqual(scans, (self.home / 'scan.calls').read_bytes())

    def test_other_live_process_does_not_bypass_foreign_listener_detection(self):
        self.assertEqual(0, self.setup().returncode)
        self.fixture_listener(owned=False)
        result = self.setup(FIXTURE_SCAN='[127.0.0.1]:8928 ssh-ed25519 AAAA\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Another SSH service', result.stdout)

    def test_foreign_ssh_port_conflict_preserves_endpoint_and_explains_recovery(self):
        self.assertEqual(0, self.setup().returncode)
        previous = self.endpoint_files()
        result = self.setup(FIXTURE_SCAN='[127.0.0.1]:8928 ssh-ed25519 AAAA\n')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Another SSH service', result.stdout)
        self.assertEqual(previous, self.endpoint_files())
        own_key = (self.state / 'host_ecdsa.pub').read_text().strip()
        accepted = self.setup(FIXTURE_SCAN='[127.0.0.1]:8928 ' + own_key + '\n')
        self.assertEqual(0, accepted.returncode, accepted.stderr + accepted.stdout)

    def test_killed_lock_owner_does_not_block_retry(self):
        self.state.mkdir(parents=True)
        holder = subprocess.Popen([sys.executable, '-u', '-c',
            'import fcntl,sys,time; f=open(sys.argv[1],"w"); fcntl.flock(f,fcntl.LOCK_EX); print("locked",flush=True); time.sleep(30)',
            str(self.state / 'host-key.lock')], stdout=subprocess.PIPE, text=True)
        try:
            self.assertEqual('locked', holder.stdout.readline().strip())
            holder.kill()
            holder.wait(timeout=2)
            result = self.setup()
            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertTrue((self.state / 'host_ecdsa').is_file())
        finally:
            if holder.poll() is None: holder.kill(); holder.wait()
            holder.stdout.close()

    def test_simultaneous_first_setup_publishes_one_stable_host_identity(self):
        self.write_tool('ln', "printf '%s\\n' 'Android app-data hard links are forbidden' >&2; exit 1")
        (self.state / '.host-key.lock').mkdir(parents=True)
        (self.state / '.host-key.lock/pid').write_text('99999999\n')
        with ThreadPoolExecutor(max_workers=4) as executor:
            results = list(executor.map(lambda _: self.setup(), range(4)))
        for result in results:
            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        host_markers = {line for result in results for line in result.stdout.splitlines() if line.startswith('__RETUI_SSH_HOST_KEY__ ')}
        self.assertEqual(1, len(host_markers))
        actual = subprocess.check_output([shutil.which('ssh-keygen'), '-y', '-P', '', '-f', str(self.state / 'host_ecdsa')], text=True).strip()
        self.assertEqual(' '.join(actual.split()[:2]), (self.state / 'host_ecdsa.pub').read_text().strip())
        self.assertFalse(list(self.state.glob('.setup.*')))


if __name__ == '__main__':
    unittest.main()
