#!/usr/bin/env python3
"""Exercise packaged bridge clients against isolated real tmux windows."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import queue
import threading
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]

class TilesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=os.environ.get('TMPDIR'))
        self.home = Path(self.tmp.name)
        self.socket = 'retui-test-' + str(os.getpid())
        self.tmux_bin = shutil.which('tmux')
        assert self.tmux_bin is not None, 'real tmux is required'
        bin_dir = self.home / 'bin'
        bin_dir.mkdir()
        wrapper = bin_dir / 'tmux'
        wrapper.write_text('#!/bin/sh\nexec "' + self.tmux_bin + '" -L ' + self.socket + ' "$@"\n')
        wrapper.chmod(0o700)
        self.env = dict(os.environ, HOME=str(self.home), PREFIX=str(self.home), RETUI_SOCKET_TOKEN='fixture-token')
        self.tmux('new-session', '-d', '-s', 'retui_workspace', '-n', 'one', 'sh')
        self.tmux('new-window', '-d', '-t', 'retui_workspace', '-n', 'two', 'sh')
        self.ids = self.tmux('list-windows', '-t', 'retui_workspace', '-F', '#{window_id}').splitlines()

    def tmux(self, *args):
        return subprocess.check_output([self.tmux_bin, '-L', self.socket, *args], text=True).strip()

    def tearDown(self):
        subprocess.run([self.tmux_bin, '-L', self.socket, 'kill-server'], capture_output=True)
        self.tmp.cleanup()

    def client(self, target, cols, rows, commands=''):
        hello = f'HELLO fixture-token {cols} {rows} CAPTURE {base64.b64encode(target.encode()).decode()}\n'
        result = subprocess.run(['sh', str(ROOT / 'termux/retui/bin/retui'), 'bridge', 'socket-client'],
                                input=hello + commands + 'BYE\n', env=self.env, capture_output=True, text=True, timeout=10)
        self.assertEqual(0, result.returncode, result.stderr)
        return result.stdout

    def test_stream_reports_exit_and_other_window_survives(self):
        process = subprocess.Popen(['sh', str(ROOT / 'termux/retui/bin/retui'), 'bridge', 'socket-client'],
                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   env=self.env, text=True)
        output = queue.Queue()
        assert process.stdin is not None and process.stdout is not None and process.stderr is not None
        stream = process.stdout
        worker = threading.Thread(target=lambda: [output.put(line.strip()) for line in stream], daemon=True)
        worker.start()
        try:
            target = base64.b64encode(self.ids[0].encode()).decode()
            process.stdin.write(f'HELLO fixture-token 43 12 STREAM {target} NO_CREATE\n'); process.stdin.flush()
            deadline = time.monotonic() + 5
            while output.get(timeout=max(.01, deadline - time.monotonic())) != '__RETUI_FRAME_END__':
                pass
            self.tmux('send-keys', '-t', self.ids[0], 'C-u')
            self.tmux('send-keys', '-t', self.ids[0], '-l', 'exit')
            self.tmux('send-keys', '-t', self.ids[0], 'Enter')
            deadline = time.monotonic() + 3
            seen = []
            try:
                while time.monotonic() < deadline:
                    line = output.get(timeout=max(.01, deadline - time.monotonic()))
                    seen.append(line)
                    if line == '__RETUI_TARGET_GONE__ ' + self.ids[0]: break
            except queue.Empty:
                pass
            self.assertIn('__RETUI_TARGET_GONE__ ' + self.ids[0], seen)
            self.assertEqual(self.ids[1], self.tmux('list-windows', '-t', 'retui_workspace', '-F', '#{window_id}'))
        finally:
            process.stdin.write('BYE\n'); process.stdin.flush(); process.wait(timeout=5)
            process.stdin.close(); process.stdout.close(); process.stderr.close(); worker.join(timeout=1)

    def test_dead_retained_pane_is_reported_as_exited(self):
        self.tmux('set-option', '-w', '-t', self.ids[0], 'remain-on-exit', 'on')
        self.tmux('send-keys', '-t', self.ids[0], '-l', 'exit')
        self.tmux('send-keys', '-t', self.ids[0], 'Enter')
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline and self.tmux('display-message', '-p', '-t', self.ids[0], '#{pane_dead}') != '1':
            time.sleep(.02)
        result = self.client(self.ids[0], 43, 12)
        self.assertIn('__RETUI_TARGET_GONE__ ' + self.ids[0], result)
        self.assertIn(self.ids[1], self.tmux('list-windows', '-t', 'retui_workspace', '-F', '#{window_id}'))

    def test_two_clients_capture_resize_and_type_independently(self):
        first = self.client(self.ids[0], 43, 12, 'TYPE 43 12 ' + base64.b64encode(b'FIRST_TILE').decode() + '\n')
        second = self.client(self.ids[1], 67, 19, 'TYPE 67 19 ' + base64.b64encode(b'SECOND_TILE').decode() + '\n')
        self.assertIn('__RETUI_TARGET__ ' + self.ids[0], first)
        self.assertIn('__RETUI_TARGET__ ' + self.ids[1], second)
        self.assertEqual('43x12', self.tmux('display-message', '-p', '-t', self.ids[0], '#{window_width}x#{window_height}'))
        self.assertEqual('67x19', self.tmux('display-message', '-p', '-t', self.ids[1], '#{window_width}x#{window_height}'))
        pane1 = self.tmux('capture-pane', '-p', '-t', self.ids[0])
        pane2 = self.tmux('capture-pane', '-p', '-t', self.ids[1])
        self.assertIn('FIRST_TILE', pane1)
        self.assertNotIn('SECOND_TILE', pane1)
        self.assertIn('SECOND_TILE', pane2)
        self.assertNotIn('FIRST_TILE', pane2)
        self.assertIn('__RETUI_TARGET__ ' + self.ids[0], self.client(self.ids[0], 43, 12))

    def test_select_resizes_only_the_new_target_without_global_selection(self):
        self.client(self.ids[0], 43, 12)
        active = self.tmux('display-message', '-p', '-t', 'retui_workspace', '#{window_id}')
        encoded = base64.b64encode(self.ids[1].encode()).decode()
        self.client(self.ids[0], 43, 12, f'SELECT 67 19 {encoded}\n')
        self.assertEqual('43x12', self.tmux('display-message', '-p', '-t', self.ids[0], '#{window_width}x#{window_height}'))
        self.assertEqual('67x19', self.tmux('display-message', '-p', '-t', self.ids[1], '#{window_width}x#{window_height}'))
        self.assertEqual(active, self.tmux('display-message', '-p', '-t', 'retui_workspace', '#{window_id}'))

    def test_concurrent_streams_keep_distinct_content_and_resize_independently(self):
        processes = []
        workers = []
        queues = [queue.Queue(), queue.Queue()]

        def read_frames(process, output):
            frame = []
            assert process.stdout is not None
            for line in process.stdout:
                frame.append(line.rstrip('\n'))
                if line.strip() == '__RETUI_FRAME_END__':
                    output.put(frame)
                    frame = []

        def wait_for(tile, marker):
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                frame = queues[tile].get(timeout=max(.01, deadline - time.monotonic()))
                metadata = dict(line.split(' ', 1) for line in frame if ' ' in line)
                body = base64.b64decode(metadata['__RETUI_FRAME_BASE64__']).decode()
                if marker in body:
                    self.assertEqual(self.ids[tile], metadata['__RETUI_TARGET__'])
                    return body
            self.fail('Stream did not capture ' + marker)

        try:
            for tile, (cols, rows) in enumerate(((43, 12), (67, 19))):
                process = subprocess.Popen(['sh', str(ROOT / 'termux/retui/bin/retui'), 'bridge', 'socket-client'],
                                           stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                           env=self.env, text=True)
                processes.append(process)
                assert process.stdin is not None
                worker = threading.Thread(target=read_frames, args=(process, queues[tile]), daemon=True)
                worker.start(); workers.append(worker)
                encoded_target = base64.b64encode(self.ids[tile].encode()).decode()
                process.stdin.write(f'HELLO fixture-token {cols} {rows} STREAM {encoded_target}\n')
                process.stdin.flush()
            for tile, marker in enumerate(('FIRST_λ_TILE', 'SECOND_λ_TILE')):
                cols, rows = ((43, 12), (67, 19))[tile]
                stream = processes[tile].stdin
                assert stream is not None
                stream.write(f'TYPE {cols} {rows} ' + base64.b64encode(marker.encode()).decode() + '\n')
                stream.flush()
                body = wait_for(tile, marker)
                self.assertNotIn(('SECOND_λ_TILE', 'FIRST_λ_TILE')[tile], body)
            stream = processes[0].stdin
            assert stream is not None
            stream.write('CAPTURE 31 10\n'); stream.flush()
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline and self.tmux('display-message', '-p', '-t', self.ids[0], '#{window_width}') != '31':
                time.sleep(.02)
            self.assertEqual('31x10', self.tmux('display-message', '-p', '-t', self.ids[0], '#{window_width}x#{window_height}'))
            self.assertEqual('67x19', self.tmux('display-message', '-p', '-t', self.ids[1], '#{window_width}x#{window_height}'))
        finally:
            for process in processes:
                assert process.stdin is not None and process.stdout is not None and process.stderr is not None
                process.stdin.write('BYE\n'); process.stdin.flush()
                process.wait(timeout=5)
                process.stdin.close(); process.stdout.close(); process.stderr.close()
            for worker in workers: worker.join(timeout=1)
        self.assertEqual(2, len(self.tmux('list-windows', '-t', 'retui_workspace', '-F', '#{window_id}').splitlines()))

if __name__ == '__main__':
    unittest.main()
