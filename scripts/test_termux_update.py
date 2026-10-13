"""Pinned stdin update, rollback, concurrency and real tmux job preservation."""
import hashlib
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]
UPDATER = ROOT / 'termux/retui/update.sh'
HELPER = (ROOT / 'termux/retui/bin/retui').read_bytes()
VERSION = '0.4.4'


class HelperUpdateTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='retui-update-')
        self.prefix = Path(self.temporary.name)
        self.bin = self.prefix / 'bin'
        self.bin.mkdir()
        self.share = self.prefix / 'share/retui'
        self.share.mkdir(parents=True)
        self.old = b'#!/bin/sh\nprintf "0.3.4\\n"\n'
        self.target = self.bin / 'retui'
        self.target.write_bytes(self.old)
        self.target.chmod(0o755)
        (self.share / 'version').write_text('0.3.4\n')
        # Package-presence fixtures; the updater must not execute either tool.
        for name in ['tmux', 'socat']:
            path = self.bin / name
            path.write_text('#!/bin/sh\nexit 91\n')
            path.chmod(0o755)
        self.env = dict(os.environ, PREFIX=str(self.prefix), PATH=str(self.bin) + os.pathsep + os.environ['PATH'])

    def tearDown(self):
        self.temporary.cleanup()

    def update(self, data=HELPER, checksum=None, version=VERSION):
        return subprocess.run(['sh', str(UPDATER), version, checksum or hashlib.sha256(data).hexdigest()],
                              input=base64.b64encode(data) + b'\n', env=self.env, capture_output=True, timeout=10)

    def unchanged(self):
        self.assertEqual(self.old, self.target.read_bytes())
        self.assertEqual('0.3.4\n', (self.share / 'version').read_text())
        self.assertFalse((self.share / '.update-lock').exists())
        self.assertEqual([], list(self.bin.glob('.retui-*')))

    def test_success_installs_exact_bytes_keeps_previous_and_releases_lock(self):
        result = self.update()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(b'__RETUI_UPDATE_OK__ 0.4.4\n', result.stdout)
        self.assertEqual(HELPER, self.target.read_bytes())
        self.assertEqual(self.old, (self.share / 'retui.previous').read_bytes())
        self.assertEqual('0.4.4\n', (self.share / 'version').read_text())
        self.assertFalse((self.share / '.update-lock').exists())
        self.assertEqual([], list(self.bin.glob('.retui-*')))

    def test_checksum_version_syntax_and_size_fail_before_replacement(self):
        for data, checksum, version in [(HELPER, '0' * 64, VERSION),
            (HELPER, None, '9.9.9'), (b'#!/bin/sh\nif\n', None, VERSION),
            (b'x' * 131073, None, VERSION)]:
            with self.subTest(version=version, size=len(data)):
                self.assertNotEqual(0, self.update(data, checksum, version).returncode)
                self.unchanged()

    def test_promoted_helper_is_rolled_back_when_metadata_commit_fails(self):
        real_mv = shutil.which('mv')
        wrapper = self.bin / 'mv'
        wrapper.write_text('#!/bin/sh\ncase "$2" in */.update-lock/version-next) exit 23;; esac\nexec "' + real_mv + '" "$@"\n')
        wrapper.chmod(0o755)
        self.assertNotEqual(0, self.update().returncode)
        self.unchanged()
        self.assertEqual(self.old, (self.share / 'retui.previous').read_bytes())

    def test_missing_dependency_lock_or_symlink_is_not_overwritten(self):
        (self.bin / 'socat').unlink()
        # Remove system tools from PATH except required updater commands.
        for tool in ['sha256sum', 'mktemp', 'head', 'wc', 'chmod', 'cp', 'mv', 'sh', 'base64']:
            target = shutil.which(tool)
            if target:
                (self.bin / tool).symlink_to(target)
        self.env['PATH'] = str(self.bin)
        result = self.update()
        self.assertIn(b'__RETUI_UPDATE_ERROR__ dependencies', result.stdout)
        self.unchanged()
        self.env['PATH'] = str(self.bin) + os.pathsep + os.environ['PATH']
        (self.bin / 'socat').write_text('#!/bin/sh\nexit 91\n'); (self.bin / 'socat').chmod(0o755)
        (self.share / '.update-lock').mkdir()
        self.assertIn(b'__RETUI_UPDATE_ERROR__ busy', self.update().stdout)
        self.assertEqual(self.old, self.target.read_bytes())
        (self.share / '.update-lock').rmdir()
        self.target.unlink()
        protected = self.prefix / 'protected'
        protected.write_bytes(self.old)
        self.target.symlink_to(protected)
        self.assertIn(b'__RETUI_UPDATE_ERROR__ path', self.update().stdout)
        self.assertEqual(self.old, protected.read_bytes())

    @unittest.skipUnless(shutil.which('tmux'), 'real tmux required')
    def test_two_real_shells_and_running_job_survive_success_and_failure(self):
        server = 'retui-update-' + uuid.uuid4().hex
        tmux = shutil.which('tmux')
        def run(*args):
            return subprocess.check_output([tmux, '-L', server, *args], text=True).strip()
        try:
            run('new-session', '-d', '-s', 'retui-update', 'sleep 120')
            run('new-window', '-d', '-t', 'retui-update', 'sleep 120')
            before = run('list-panes', '-a', '-F', '#{window_id}:#{pane_pid}:#{pane_current_command}')
            pids = [int(line.split(':')[1]) for line in before.splitlines()]
            self.assertEqual(0, self.update().returncode)
            self.assertNotEqual(0, self.update(checksum='0' * 64).returncode)
            self.assertEqual(before, run('list-panes', '-a', '-F', '#{window_id}:#{pane_pid}:#{pane_current_command}'))
            for pid in pids: os.kill(pid, 0)
        finally:
            subprocess.run([tmux, '-L', server, 'kill-server'], capture_output=True)

    def test_bridge_restart_ignores_stale_pid_files_pointing_to_an_unrelated_job(self):
        job = subprocess.Popen(['sleep', '120'])
        try:
            runtime = self.prefix / 'runtime'
            runtime.mkdir()
            for name in ['bridge.socket.pid', 'bridge.tcp.pid']:
                (runtime / name).write_text(str(job.pid) + '\n')
            source = HELPER.decode().rsplit('main "$@"', 1)[0]
            source += '\nbridge_runtime_dir() { printf "%s\\n" "$PREFIX/runtime"; }\nstop_bridge_daemons\n'
            # A file keeps ps from mistaking the test's full helper source argv for a daemon.
            driver = self.prefix / 'restart-fixture.sh'
            driver.write_text(source)
            result = subprocess.run(['sh', str(driver)], env=self.env, capture_output=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIsNone(job.poll(), 'Bridge restart must not kill an unrelated recycled PID')
            self.assertEqual([], list(runtime.iterdir()))
        finally:
            job.terminate(); job.wait(timeout=5)


if __name__ == '__main__':
    unittest.main()
