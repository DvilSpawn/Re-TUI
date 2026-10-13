#!/usr/bin/env python3
"""Phone-only loopback relay on a private tmux server; production helper unchanged."""
import argparse
import base64
import json
import os
from pathlib import Path
import queue
import re
import shlex
import socketserver
import subprocess
import threading
import time
import tty

MAX_LINE = 1_048_576
PREFIX = '/data/data/com.termux/files/usr'
ROOT = Path(__file__).resolve().parent
TMUX = [PREFIX + '/bin/tmux', '-L', 'retui_stream_phone_probe', '-f', '/dev/null']
ENV = dict(os.environ, HOME=str(ROOT / 'home'), PATH=PREFIX + '/bin:' + os.environ.get('PATH', ''),
           LD_LIBRARY_PATH=PREFIX + '/lib', TERM='tmux-256color', PS1='$ ')


def tmux(*args):
    return subprocess.check_output(TMUX + list(args), env=ENV).decode().strip()


def payload(data):
    return base64.b64encode(data).decode()


def decode_octal(data):
    out = bytearray()
    index = 0
    while index < len(data):
        if data[index] == 92:
            part = data[index+1:index+4]
            if len(part) != 3 or any(c not in b'01234567' for c in part) or int(part, 8) > 255:
                raise ValueError('Bad output escape')
            out.append(int(part, 8)); index += 4
        else:
            out.append(data[index]); index += 1
    return bytes(out)


class Control:
    def __init__(self, output):
        self.output = output
        self.responses = queue.Queue(8)
        self.lock = threading.Lock()
        self.process = subprocess.Popen(TMUX + ['-C', 'attach-session', '-t', 'retui_workspace'],
                                        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=ENV)
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()
        self.responses.get(timeout=5)

    def read(self):
        block = None
        try:
            while line := self.process.stdout.readline(MAX_LINE + 1):
                if len(line) > MAX_LINE or not line.endswith(b'\n'):
                    raise ValueError('Output overflow; reconnect required')
                line = line[:-1]
                if line.startswith(b'%begin '):
                    block = []
                elif line.startswith((b'%end ', b'%error ')):
                    self.responses.put((line.startswith(b'%error '), block), timeout=5); block = None
                elif block is not None:
                    block.append(line)
                elif line.startswith(b'%output '):
                    _, pane, data = line.split(b' ', 2)
                    self.output(pane.decode(), decode_octal(data))
        except (OSError, ValueError, queue.Full):
            self.process.terminate()

    def command(self, command):
        assert '\n' not in command and '\r' not in command
        with self.lock:
            self.process.stdin.write(command.encode() + b'\n'); self.process.stdin.flush()
            failed, body = self.responses.get(timeout=5)
        if failed:
            raise RuntimeError(b'\n'.join(body).decode(errors='replace'))
        return b'\n'.join(body)

    def close(self):
        try:
            if self.process.poll() is None:
                self.process.stdin.write(b'detach-client\n'); self.process.stdin.flush()
                self.process.wait(timeout=5)
        finally:
            if self.process.poll() is None:
                self.process.terminate(); self.process.wait(timeout=5)
            self.reader.join(timeout=5)
            self.process.stdin.close(); self.process.stdout.close()


def program(kind):
    if kind == 'echo':
        return shlex.join([PREFIX + '/bin/python', str(ROOT / 'phone_bridge.py'), '--echo'])
    if kind == 'shell':
        return shlex.join([PREFIX + '/bin/bash', '--noprofile', '--norc'])
    raise ValueError('Unknown fixture')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--echo', action='store_true')
    args = parser.parse_args()
    if args.echo:
        tty.setraw(0); os.write(1, b'\x1bcREADY')
        while data := os.read(0, 4096):
            os.write(1, data)
        return
    (ROOT / 'home/bin').mkdir(parents=True, exist_ok=True)
    wrapper = ROOT / 'home/bin/tmux'
    wrapper.write_text('#!' + PREFIX + '/bin/sh\nexec ' + shlex.join(TMUX) + ' "$@"\n')
    wrapper.chmod(0o700)
    token = (ROOT / 'token').read_text().strip()
    assert re.fullmatch(r'[a-f0-9]{64}', token)
    tmux('new-session', '-d', '-s', 'retui_workspace', '-n', 'probe0', '-x', '80', '-y', '24', program('echo'))
    tmux('new-window', '-d', '-t', 'retui_workspace', '-n', 'probe1', program('echo'))
    windows = tmux('list-windows', '-t', 'retui_workspace', '-F', '#{window_id}').splitlines()
    panes = [tmux('display-message', '-p', '-t', w, '#{pane_id}') for w in windows]
    slots = threading.BoundedSemaphore(4)

    class Handler(socketserver.StreamRequestHandler):
        def handle(self):
            if not slots.acquire(blocking=False):
                return
            control = None
            captures = []
            write_lock = threading.Lock()
            geometry = [[80, 24], [80, 24]]
            active = False

            def send(value):
                with write_lock:
                    self.wfile.write(json.dumps(value, separators=(',', ':')).encode() + b'\n'); self.wfile.flush()

            def live(pane, data):
                if active and pane in panes:
                    send(dict(op='output', tile=panes.index(pane), data=payload(data)))

            def snapshot_reader(index, process):
                metadata = {}
                for line in process.stdout:
                    if len(line) > MAX_LINE:
                        self.request.close(); return
                    line = line.strip()
                    if line.startswith('__RETUI_') and ' ' in line:
                        key, value = line.split(' ', 1); metadata[key] = value
                    if line == '__RETUI_FRAME_END__':
                        cursor = metadata.get('__RETUI_CURSOR__', '0:0').split(':')
                        send(dict(op='frame', tile=index, cols=int(metadata['__RETUI_COLS__']), rows=int(metadata['__RETUI_ROWS__']),
                                  x=int(cursor[0]), y=int(cursor[1]), data=metadata['__RETUI_FRAME_BASE64__']))
                        metadata = {}

            def seed(index):
                pane = panes[index]
                fields = '#{pane_width}|#{pane_height}|#{cursor_x}|#{cursor_y}|#{alternate_on}|#{alternate_saved_x}|#{alternate_saved_y}|#{cursor_flag}|#{wrap_flag}|#{insert_flag}|#{scroll_region_upper}|#{scroll_region_lower}|#{keypad_cursor_flag}'
                values = control.command('display-message -p -t ' + pane + ' ' + shlex.quote(fields)).decode().split('|')
                values = [int(v or 0) for v in values]
                body = control.command('capture-pane -p -e -N -t ' + pane)
                normal = control.command('capture-pane -a -p -e -N -t ' + pane) if values[4] else b''
                send(dict(op='seed', tile=index, cols=values[0], rows=values[1], x=values[2], y=values[3], alternate=values[4],
                          saved_x=values[5], saved_y=values[6], cursor=values[7], wrap=values[8], insert=values[9],
                          scroll_top=values[10], scroll_bottom=values[11], keypad=values[12], normal=payload(normal), data=payload(body)))

            try:
                self.request.settimeout(5)
                hello = json.loads(self.rfile.readline(65537))
                if hello.get('op') != 'hello' or hello.get('token') != token:
                    return
                mode = hello['mode']
                if mode not in ('snapshot', 'stream'):
                    return
                for index, size in enumerate(hello['sizes']):
                    if index not in (0, 1) or not (20 <= size[0] <= 240 and 8 <= size[1] <= 120):
                        raise ValueError('Invalid geometry')
                    geometry[index] = size
                    tmux('resize-window', '-t', windows[index], '-x', str(size[0]), '-y', str(size[1]))
                self.request.settimeout(None)
                send(dict(op='ready', panes=panes))
                if mode == 'snapshot':
                    env = dict(ENV, PREFIX=str(ROOT / 'home'), RETUI_SESSION='retui_workspace', RETUI_SOCKET_TOKEN=token)
                    for index in (0, 1):
                        process = subprocess.Popen([PREFIX + '/bin/sh', str(ROOT / 'retui'), 'bridge', 'socket-client'],
                                                   stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=env, text=True)
                        captures.append(process)
                        process.stdin.write(f'HELLO {token} {geometry[index][0]} {geometry[index][1]} STREAM {payload(windows[index].encode())} NO_CREATE\n'); process.stdin.flush()
                        threading.Thread(target=snapshot_reader, args=(index, process), daemon=True).start()
                else:
                    control = Control(live)
                    seed(0); seed(1); active = True

                while line := self.rfile.readline(65537):
                    if len(line) > 65536:
                        raise ValueError('Input overflow')
                    message = json.loads(line)
                    index = message.get('tile', 0)
                    if index not in (0, 1):
                        raise ValueError('Invalid tile')
                    op = message['op']
                    if op == 'type':
                        data = base64.b64decode(message['data'], validate=True)
                        if not data or len(data) > 16384:
                            raise ValueError('Invalid input size')
                        if control:
                            control.command('send-keys -H -t ' + panes[index] + ' ' + ' '.join(f'{b:02x}' for b in data))
                        else:
                            c, r = geometry[index]; captures[index].stdin.write(f'TYPE {c} {r} {payload(data)}\n'); captures[index].stdin.flush()
                    elif op == 'key':
                        key = message['key']
                        if not re.fullmatch(r'[A-Za-z0-9_+.;:/-]{1,32}', key):
                            raise ValueError('Invalid key')
                        if control:
                            control.command('send-keys -t ' + panes[index] + ' ' + shlex.quote(key))
                        else:
                            c, r = geometry[index]; captures[index].stdin.write(f'KEY {c} {r} {key}\n'); captures[index].stdin.flush()
                    elif op == 'size':
                        c, r = message['cols'], message['rows']
                        if not (20 <= c <= 240 and 8 <= r <= 120):
                            raise ValueError('Invalid geometry')
                        geometry[index] = [c, r]
                        if control:
                            control.command(f'resize-window -t {windows[index]} -x {c} -y {r}')
                            seed(index)
                        else:
                            captures[index].stdin.write(f'CAPTURE {c} {r}\n'); captures[index].stdin.flush()
                    elif op == 'reset':
                        tmux('respawn-pane', '-k', '-t', panes[index], program(message['kind']))
                        if not control:
                            # Reset may reproduce an identical screen, which the shipped
                            # stream deduplicates. Supply a fresh initial frame for the test.
                            c, r = geometry[index]
                            cursor = tmux('display-message', '-p', '-t', panes[index], '#{cursor_x}:#{cursor_y}').split(':')
                            send(dict(op='frame', tile=index, cols=c, rows=r, x=int(cursor[0]), y=int(cursor[1]),
                                      data=payload(tmux('capture-pane', '-p', '-e', '-N', '-t', panes[index]).encode())))
                    else:
                        raise ValueError('Unknown input operation')
            except (OSError, ValueError, KeyError, RuntimeError, queue.Empty, subprocess.TimeoutExpired) as error:
                try:
                    send(dict(op='error', message=str(error)))
                except OSError:
                    pass
            finally:
                active = False
                if control:
                    try:
                        control.close()
                    except (OSError, subprocess.TimeoutExpired):
                        pass
                for process in captures:
                    process.terminate()
                    try:
                        process.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        process.kill(); process.wait(timeout=5)
                    process.stdin.close(); process.stdout.close()
                slots.release()

    class Server(socketserver.ThreadingTCPServer):
        daemon_threads = True
        allow_reuse_address = False

    try:
        with Server(('127.0.0.1', 0), Handler) as server:
            (ROOT / 'port').write_text(str(server.server_address[1]))
            server.timeout = 1
            deadline = time.monotonic() + 7200
            while time.monotonic() < deadline and not (ROOT / 'stop').exists():
                server.handle_request()
    finally:
        subprocess.run(TMUX + ['kill-server'], env=ENV, capture_output=True)


if __name__ == '__main__':
    main()
