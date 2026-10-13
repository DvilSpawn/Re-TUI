#!/usr/bin/env python3
"""Isolated tmux -> upstream Termux emulator experiment; never touches app data."""
import argparse
import base64
import hashlib
import io
import json
import math
import os
from pathlib import Path
import queue
import random
import re
import shutil
import shlex
import statistics
import subprocess
import sys
import tarfile
import threading
import time
import tty
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
COMMIT = '5b657c6adf4304e5198951ce815fe0205dcac29c'  # v0.118.3
ARCHIVE_SHA256 = 'a79a8ae388caab56d4d6df8f80aa213c21090e3a1f605c28d7f93fb466d6e7ca'
MAX_LINE = 1_048_576


def unescape_output(value):
    """Decode tmux octal escapes as bytes, before any UTF-8 interpretation."""
    out = bytearray()
    index = 0
    while index < len(value):
        if value[index] == 92:
            octal = value[index + 1:index + 4]
            if len(octal) != 3 or any(ch not in b'01234567' for ch in octal):
                raise ValueError('Invalid tmux output escape')
            number = int(octal, 8)
            if number > 255:
                raise ValueError('Invalid tmux output byte')
            out.append(number)
            index += 4
        else:
            out.append(value[index])
            index += 1
    return bytes(out)


def prepare_engine(cache, android_jar):
    archive = cache / 'termux-v0.118.3.tar.gz'
    cache.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        with urllib.request.urlopen(f'https://codeload.github.com/termux/termux-app/tar.gz/{COMMIT}', timeout=30) as response:
            archive.write_bytes(response.read())
    data = archive.read_bytes()
    if hashlib.sha256(data).hexdigest() != ARCHIVE_SHA256:
        raise ValueError('Upstream archive checksum mismatch')
    source = cache / 'upstream'
    with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as bundle:
        for member in bundle.getmembers():
            relative = '/'.join(member.name.split('/')[1:])
            if (relative.startswith('terminal-emulator/src/main/java/') and relative.endswith('.java')) or relative == 'LICENSE.md':
                if not member.isfile() or '..' in Path(relative).parts:
                    raise ValueError('Invalid source archive member')
                destination = source / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_bytes(bundle.extractfile(member).read())
    classes = cache / 'classes'
    classes.mkdir(exist_ok=True)
    files = sorted(source.rglob('*.java')) + [HERE / 'EmulatorProbe.java']
    subprocess.run(['javac', '-cp', str(android_jar), '-d', str(classes), *map(str, files)], check=True)
    return os.pathsep.join((str(classes), str(android_jar)))


class Engine:
    def __init__(self, classpath):
        self.process = subprocess.Popen(['java', '-cp', classpath, 'EmulatorProbe'], stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=None, text=True)
        self.lock = threading.Lock()

    def call(self, line):
        with self.lock:
            self.process.stdin.write(line + '\n')
            self.process.stdin.flush()
            raw = self.process.stdout.readline().strip()
        if not raw:
            raise RuntimeError('Emulator probe exited')
        fields = raw.split(' ', 8)
        if fields[0] == 'STYLE':
            return tuple(map(int, fields[1:]))
        return dict(cols=int(fields[1]), rows=int(fields[2]), x=int(fields[3]), y=int(fields[4]),
                    alternate=fields[5] == 'true', cursor=fields[6] == 'true', replies=int(fields[7]),
                    text=base64.b64decode(fields[8] if len(fields) > 8 else '').decode())

    def create(self, pane, cols=80, rows=24):
        return self.call(f'CREATE {pane} {cols} {rows}')

    def feed(self, pane, data):
        return self.call(f'FEED {pane} ' + base64.b64encode(data).decode())

    def close(self):
        with self.lock:
            self.process.stdin.write('QUIT\n'); self.process.stdin.flush()
        self.process.wait(timeout=5)
        self.process.stdin.close(); self.process.stdout.close()


class Control:
    def __init__(self, fixture, engine):
        self.engine = engine
        self.changed = threading.Condition()
        self.latest = {}
        self.error = None
        self.replies = queue.Queue()
        self.commands = threading.Lock()
        self.process = subprocess.Popen([fixture.tmux_bin, '-L', fixture.socket, '-C', 'attach-session',
                                        '-t', 'retui_workspace'], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE)
        self.worker = threading.Thread(target=self.read, daemon=True)
        self.worker.start()
        self.replies.get(timeout=5)  # attach command completes before input is sent

    def read(self):
        block = None
        try:
            while True:
                line = self.process.stdout.readline(MAX_LINE + 1)
                if not line:
                    break
                if len(line) > MAX_LINE or not line.endswith(b'\n'):
                    raise ValueError('Oversized/incomplete control line; reconnect is required')
                line = line[:-1]
                if line.startswith(b'%begin '):
                    block = []
                elif line.startswith((b'%end ', b'%error ')):
                    if block is None:
                        raise ValueError('Unmatched tmux command response')
                    self.replies.put((line.startswith(b'%error '), block))
                    block = None
                elif block is not None:
                    block.append(line)
                elif line.startswith(b'%output '):
                    _, pane, escaped = line.split(b' ', 2)
                    pane = pane.decode('ascii')
                    if pane in self.latest:
                        state = self.engine.feed(pane, unescape_output(escaped))
                        with self.changed:
                            self.latest[pane] = (time.monotonic(), state)
                            self.changed.notify_all()
        except Exception as error:
            with self.changed:
                self.error = error
                self.changed.notify_all()

    def command(self, line):
        if '\n' in line or '\r' in line:
            raise ValueError('A command must fit on one line')
        with self.commands:
            self.process.stdin.write(line.encode() + b'\n'); self.process.stdin.flush()
            failed, lines = self.replies.get(timeout=5)
        if failed:
            raise RuntimeError(b'\n'.join(lines).decode(errors='replace'))
        return lines

    def send(self, pane, data):
        assert re.fullmatch(r'%[0-9]+', pane)
        self.command(f'send-keys -H -t {pane} ' + ' '.join(f'{value:02x}' for value in data))

    def wait(self, pane, predicate, timeout=5):
        deadline = time.monotonic() + timeout
        with self.changed:
            while True:
                if self.error:
                    raise self.error
                stamp, state = self.latest[pane]
                if predicate(state):
                    return stamp, state
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError(f'Pane {pane}: {state}')
                self.changed.wait(remaining)

    def close(self):
        try:
            self.process.stdin.write(b'detach-client\n'); self.process.stdin.flush()
            self.process.wait(timeout=5)
        finally:
            if self.process.poll() is None:
                self.process.terminate(); self.process.wait(timeout=5)
            self.worker.join(timeout=5)
            self.process.stdin.close(); self.process.stdout.close(); self.process.stderr.close()


class Snapshots:
    def __init__(self, fixture, target):
        self.frames = queue.Queue()
        self.process = subprocess.Popen(['sh', str(ROOT / 'termux/retui/bin/retui'), 'bridge', 'socket-client'],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                        env=fixture.env, text=True)
        self.worker = threading.Thread(target=self.read, daemon=True)
        self.worker.start()
        encoded = base64.b64encode(target.encode()).decode()
        self.process.stdin.write(f'HELLO fixture-token 80 24 STREAM {encoded} NO_CREATE\n'); self.process.stdin.flush()
        self.frames.get(timeout=5)

    def read(self):
        body = None
        for line in self.process.stdout:
            if line.startswith('__RETUI_FRAME_BASE64__ '):
                body = base64.b64decode(line.split(' ', 1)[1]).decode()
            elif line.strip() == '__RETUI_FRAME_END__' and body is not None:
                self.frames.put((time.monotonic(), body))
                body = None

    def type(self, text):
        self.process.stdin.write('TYPE 80 24 ' + base64.b64encode(text.encode()).decode() + '\n')
        self.process.stdin.flush()

    def wait(self, marker):
        deadline = time.monotonic() + 5
        while True:
            stamp, body = self.frames.get(timeout=max(.01, deadline - time.monotonic()))
            if marker in body:
                return stamp

    def close(self):
        self.process.stdin.write('BYE\n'); self.process.stdin.flush(); self.process.wait(timeout=5)
        self.worker.join(timeout=5)
        self.process.stdin.close(); self.process.stdout.close(); self.process.stderr.close()


def stats(samples):
    ordered = sorted(samples)
    return dict(samples=len(samples), median_ms=round(statistics.median(samples), 2),
                p95_ms=round(ordered[math.ceil(len(samples) * .95) - 1], 2), max_ms=round(max(samples), 2))


def explore(engine):
    sys.path.insert(0, str(ROOT / 'scripts'))
    from test_termux_tiles import TilesTest
    class Fixture(TilesTest):
        def tmux(self, *args):
            # The shared fixture isolates the server; also suppress personal hooks/config.
            return subprocess.check_output([self.tmux_bin, '-L', self.socket, '-f', '/dev/null', *args],
                                           text=True, env=dict(os.environ, HOME=str(self.home))).strip()
    fixture = Fixture()
    fixture.setUp()
    control = None
    snapshots = None
    checks = []
    try:
        # Both panes exist before attachment; output starts only after tracking is ready.
        panes = [fixture.tmux('display-message', '-p', '-t', target, '#{pane_id}') for target in fixture.ids]
        control = Control(fixture, engine)
        for pane, window in zip(panes, fixture.ids):
            engine.create(pane)
            control.latest[pane] = (time.monotonic(), engine.call(f'STATE {pane}'))
            echo = ' '.join(shlex.quote(part) for part in [sys.executable, str(HERE / 'probe.py'), '--echo'])
            fixture.tmux('respawn-window', '-k', '-t', window, echo)
            control.wait(pane, lambda state: 'READY' in state['text'])

        # Feed escape and UTF-8 sequences split at every byte boundary.
        engine.create('fragmented')
        for byte in '\x1b[2J\x1b[Hλ界🙂'.encode():
            state = engine.feed('fragmented', bytes([byte]))
        assert state['text'].rstrip() == 'λ界🙂' and state['x'] == 5
        assert unescape_output(b'\\033[H\\134' + 'λ'.encode()) == b'\x1b[H\\' + 'λ'.encode()
        for bad in (b'\\9', b'\\888', b'\\777'):
            try:
                unescape_output(bad)
            except ValueError:
                continue
            raise AssertionError('Malformed octal accepted')
        checks.append('Fragmented escape sequences, fragmented UTF-8, wide glyphs and strict octal decoding')

        first, second = panes
        control.send(first, b'\x1bcFIRST_TILE')
        control.send(second, b'\x1bcSECOND_TILE')
        control.wait(first, lambda state: 'FIRST_TILE' in state['text'])
        control.wait(second, lambda state: 'SECOND_TILE' in state['text'])
        assert 'SECOND_TILE' not in control.latest[first][1]['text']
        assert 'FIRST_TILE' not in control.latest[second][1]['text']
        checks.append('One control connection routes two windows to separate emulator buffers')

        control.send(first, b'\x1bc' + 'λ界🙂\\raw'.encode())
        _, unicode_state = control.wait(first, lambda state: 'λ界🙂\\raw' in state['text'])
        assert unicode_state['x'] == 9
        assert fixture.tmux('capture-pane', '-p', '-t', first).rstrip() == unicode_state['text'].rstrip()
        checks.append('Real tmux output preserves Unicode, wide cells and escaped backslash')

        burst = b'\x1bc' + b''.join(f'ROW_{index:04d}\r\n'.encode() for index in range(200)) + b'BURST_END'
        control.send(first, burst)
        _, burst_state = control.wait(first, lambda state: 'BURST_END' in state['text'])
        assert fixture.tmux('capture-pane', '-p', '-t', first).rstrip() == burst_state['text'].rstrip()
        checks.append('200-line output burst scrolls to the same visible state as tmux without dropped bytes')

        control.send(first, b'\x1bcMAIN\x1b[?1049h\x1b[2J\x1b[H\x1b[38;2;12;34;56mALT\x1b[0m\x1b[?25l')
        _, state = control.wait(first, lambda state: state['alternate'] and 'ALT' in state['text'] and not state['cursor'])
        foreground, _ = engine.call(f'STYLE {first} 0 0')
        assert foreground & 0xFFFFFF == 0x0C2238
        assert fixture.tmux('capture-pane', '-p', '-t', first).rstrip() == state['text'].rstrip()
        checks.append('Live alternate screen, hidden cursor and true color agree with tmux screen text')

        # A reconnect snapshot has text and position, but not the hidden normal buffer.
        captured = fixture.tmux('capture-pane', '-p', '-e', '-N', '-t', first)
        x, y = map(int, fixture.tmux('display-message', '-p', '-t', first, '#{cursor_x}:#{cursor_y}').split(':'))
        engine.create('reconnected')
        seed = b'\x1b[?1049h\x1b[2J\x1b[H' + captured.replace('\n', '\r\n').encode() + f'\x1b[{y+1};{x+1}H'.encode()
        engine.feed('reconnected', seed)
        restored = engine.feed('reconnected', b'\x1b[?1049l')

        # A second snapshot can recover this fixture's hidden normal buffer, but
        # this does not prove full terminal-mode recovery or concurrent ordering.
        hidden = fixture.tmux('capture-pane', '-a', '-p', '-e', '-N', '-t', first)
        saved_x, saved_y = map(int, fixture.tmux('display-message', '-p', '-t', first,
                                  '#{alternate_saved_x}:#{alternate_saved_y}').split(':'))
        engine.create('both_buffers')
        normal_seed = b'\x1b[2J\x1b[H' + hidden.replace('\n', '\r\n').encode() + f'\x1b[{saved_y+1};{saved_x+1}H'.encode()
        engine.feed('both_buffers', normal_seed + seed)
        recovered = engine.feed('both_buffers', b'\x1b[?1049l')
        control.send(first, b'\x1b[?1049l')
        _, retained = control.wait(first, lambda state: not state['alternate'] and 'MAIN' in state['text'])
        assert 'MAIN' not in restored['text'] and 'MAIN' in retained['text']
        assert 'MAIN' in recovered['text']
        checks.append('Reconnect: single-screen seed loses normal buffer; two-screen seed restores the tested transition')

        control.send(second, b'\x1bcRESIZE_SECOND')
        control.wait(second, lambda state: 'RESIZE_SECOND' in state['text'])
        control.command(f'resize-window -t {fixture.ids[1]} -x 43 -y 12')
        resized = engine.call(f'RESIZE {second} 43 12')
        assert resized['cols'] == 43 and resized['rows'] == 12 and 'RESIZE_SECOND' in resized['text']
        assert fixture.tmux('display-message', '-p', '-t', first, '#{window_width}x#{window_height}') == '80x24'
        assert fixture.tmux('display-message', '-p', '-t', second, '#{window_width}x#{window_height}') == '43x12'
        checks.append('Changed geometry affects only the intended window/emulator')

        # tmux answers queries itself; the observer must suppress duplicate replies.
        queried = engine.feed('fragmented', b'\x1b[6n')
        assert queried['replies'] > 0
        checks.append('Emulator terminal-query replies are intercepted, not injected into tmux input')

        control.send(first, b'\x1bcBENCH')
        control.wait(first, lambda state: state['text'].rstrip() == 'BENCH')
        snapshots = Snapshots(fixture, fixture.ids[0])
        randomizer = random.Random(7)
        existing, same_input_live = [], []
        expected = 'BENCH'
        for char in 'abcdefghijklmnopqrstuvwx':
            time.sleep(randomizer.uniform(0, .16))
            expected += char
            start = time.monotonic()
            snapshots.type(char)
            live_stamp, _ = control.wait(first, lambda state: expected in state['text'])
            old_stamp = snapshots.wait(expected)
            same_input_live.append((live_stamp - start) * 1000)
            existing.append((old_stamp - start) * 1000)
        snapshots.close(); snapshots = None
        control.send(first, b'\x1bcDIRECT')
        control.wait(first, lambda state: state['text'].rstrip() == 'DIRECT')
        direct = []
        expected = 'DIRECT'
        for char in 'abcdefghijklmnopqrstuvwx':
            time.sleep(randomizer.uniform(0, .16))
            expected += char
            start = time.monotonic()
            control.send(first, char.encode())
            stamp, _ = control.wait(first, lambda state: expected in state['text'])
            direct.append((stamp - start) * 1000)
        checks.append('24 paired keystrokes reach both snapshot and emulator paths without loss')

        # Detach only the observer; both real fixture jobs must survive.
        control.close(); control = None
        alive = fixture.tmux('list-panes', '-a', '-F', '#{pane_id}:#{pane_dead}').splitlines()
        assert all(pane + ':0' in alive for pane in panes)
        checks.append('Disconnecting the observer leaves both shells/jobs running')
        return dict(upstream_commit=COMMIT, tmux_version=subprocess.check_output(['tmux', '-V'], text=True).strip(),
                    checks=checks, benchmarks=dict(existing_helper_input_to_snapshot=stats(existing),
                    same_helper_input_to_emulator=stats(same_input_live), persistent_control_input_to_emulator=stats(direct)),
                    limits=['Host JVM buffer-state proof; no Android Canvas/IME/frame/battery measurement',
                            'Fresh raw-echo fixture, not a complete reconnect implementation or interactive editor test',
                            'No network/Android socket transport is included in the persistent control measurements'])
    finally:
        try:
            if snapshots is not None:
                snapshots.close()
        finally:
            try:
                if control is not None:
                    control.close()
            finally:
                fixture.tearDown()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--echo', action='store_true', help=argparse.SUPPRESS)
    parser.add_argument('--cache', type=Path, default=HERE / 'build')
    sdk = os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk'))
    parser.add_argument('--android-jar', type=Path, default=Path(sdk) / 'platforms/android-36/android.jar')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.echo:
        tty.setraw(0)
        os.write(1, b'\x1bcREADY')
        while data := os.read(0, 4096):
            os.write(1, data)
        return
    if not args.android_jar.is_file() or not all(shutil.which(tool) for tool in ('tmux', 'java', 'javac')):
        parser.error('Requires tmux, Java 17 javac/java, and an Android SDK android.jar')
    engine = Engine(prepare_engine(args.cache, args.android_jar))
    try:
        result = explore(engine)
    finally:
        engine.close()
    report = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report)
    print(report)


if __name__ == '__main__':
    main()
