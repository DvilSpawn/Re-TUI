#!/usr/bin/env python3
"""Isolated Termux comparison. No APK install, production session, or SSH config edits.

Requires the connected, debuggable Termux used for this analysis and its existing
Python, tmux, socat and OpenSSH packages. All listeners bind to device loopback.
Host measurements include USB/ADB forwarding and exclude Android rendering.
"""
import argparse
import base64
import json
import math
import os
from pathlib import Path
import queue
import random
import shlex
import socket
import statistics
import subprocess
import tempfile
import threading
import time
import uuid

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
PREFIX = '/data/data/com.termux/files/usr'
HOME = '/data/data/com.termux/files/home'


def summary(values):
    ordered = sorted(values)
    return {'n': len(values), 'median_ms': round(statistics.median(values), 2),
            'p95_ms': round(ordered[math.ceil(.95 * len(values)) - 1], 2),
            'min_ms': round(min(values), 2), 'max_ms': round(max(values), 2)}


class Reader:
    def __init__(self, stream, framed):
        self.stream, self.framed = stream, framed
        self.condition = threading.Condition()
        self.samples = []
        self.count = 0
        self.wire_bytes = 0
        self.eof = False
        self.error = None
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        try:
            if self.framed:
                body = b''
                while True:
                    line = self.stream.readline(262145)
                    if not line:
                        break
                    if len(line) > 262144:
                        raise ValueError('oversized frame line')
                    self.wire_bytes += len(line)
                    if line.startswith(b'__RETUI_SOCKET_ERROR__'):
                        raise ValueError(line.decode())
                    if line.startswith(b'__RETUI_FRAME_BASE64__ '):
                        body = base64.b64decode(line.split(b' ', 1)[1])
                    if line.rstrip() == b'__RETUI_FRAME_END__':
                        self.publish(body)
            else:
                recent = b''
                while True:
                    data = os.read(self.stream.fileno(), 65536)
                    if not data:
                        break
                    self.wire_bytes += len(data)
                    recent = (recent + data)[-262144:]
                    self.publish(recent)
        except Exception as exc:
            self.error = repr(exc)
        finally:
            with self.condition:
                self.eof = True
                self.condition.notify_all()

    def publish(self, body):
        with self.condition:
            self.count += 1
            self.samples.append((time.perf_counter(), body))
            self.samples = self.samples[-512:]
            self.condition.notify_all()

    def wait(self, marker, after=0, timeout=12):
        deadline = time.monotonic() + timeout
        with self.condition:
            while True:
                for timestamp, body in self.samples:
                    if timestamp >= after and marker in body:
                        return timestamp
                if self.eof or self.error:
                    raise RuntimeError(f'reader ended: {self.error}; last={self.samples[-1:]!r}')
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError(f'waiting for {marker!r}; last={self.samples[-1:]!r}')
                self.condition.wait(remaining)


class Probe:
    def __init__(self, serial, work):
        self.adb = ['adb', '-s', serial]
        self.work = work
        self.remote = HOME + '/.retui-ssh-analysis-' + uuid.uuid4().hex[:12]
        self.forwarded = []
        self.connections = []
        self.ssh = []
        self.remote_process = None

    def run(self, script, data=None):
        command = 'run-as com.termux sh -c ' + shlex.quote(script)
        result = subprocess.run(self.adb + ['shell', '-T', command], input=data,
                                capture_output=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors='replace') + result.stdout.decode(errors='replace'))
        return result.stdout

    def put(self, name, data, mode=0o600):
        path = self.remote + '/' + name
        self.run('cat > ' + shlex.quote(path) + '\nchmod ' + oct(mode)[2:] + ' ' + shlex.quote(path), data)

    def forward(self, remote_port):
        result = subprocess.check_output(self.adb + ['forward', 'tcp:0', f'tcp:{remote_port}'], text=True)
        port = int(result.strip())
        self.forwarded.append(port)
        return port

    def setup(self):
        self.run('umask 077\nmkdir -p ' + shlex.quote(self.remote + '/bin'))
        for name in ('host', 'client'):
            subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(self.work / name)], check=True)
        self.put('host', (self.work / 'host').read_bytes())
        self.put('authorized_keys', (self.work / 'client.pub').read_bytes())
        self.put('retui-poll', (ROOT / 'termux/retui/bin/retui').read_bytes(), 0o700)
        self.put('retui-output', (HERE / 'evidence/phone-installed-retui-0.4.4.sh').read_bytes(), 0o700)
        wrapper = f'''#!{PREFIX}/bin/sh
if [ -n "${{RETUI_ANALYSIS_COUNTER:-}}" ]; then printf '%s\\n' "$1" >> "$RETUI_ANALYSIS_COUNTER"; fi
exec {PREFIX}/bin/tmux -S {self.remote}/tmux.sock -f /dev/null "$@"
'''
        self.put('bin/tmux', wrapper.encode(), 0o700)
        force = f'''#!{PREFIX}/bin/sh
export HOME={self.remote}
export PATH={self.remote}/bin:{PREFIX}/bin:/system/bin
export TERM=xterm-256color
export RETUI_ANALYSIS_COUNTER={self.remote}/ssh-count
exec tmux attach-session -t retui_analysis
'''
        self.put('force-command', force.encode(), 0o700)
        self.put('echo.py', b'import os,tty\ntty.setraw(0)\nos.write(1,b"READY")\nwhile True:\n data=os.read(0,4096)\n if not data: break\n os.write(1,data)\n', 0o700)
        # One controlling process owns all fixture listeners. EOF triggers cleanup.
        remote_script = '''import os,sys,subprocess,socket,json,signal,time
from pathlib import Path
root=Path(sys.argv[1]); prefix=sys.argv[2]
env=dict(os.environ,HOME=str(root),PREFIX=str(root),PATH=str(root/'bin')+':'+prefix+'/bin:/system/bin',TERM='xterm-256color',TMPDIR=prefix+'/tmp',RETUI_SESSION='retui_analysis',RETUI_SOCKET_TOKEN='isolated-analysis-token',RETUI_NO_CREATE='1')
def freeport():
 s=socket.socket();s.bind(('127.0.0.1',0));p=s.getsockname()[1];s.close();return p
ports=[freeport() for _ in range(3)]
config=f'Port {ports[2]}\\nListenAddress 127.0.0.1\\nHostKey {root}/host\\nPidFile {root}/sshd.pid\\nAuthorizedKeysFile {root}/authorized_keys\\nPasswordAuthentication no\\nKbdInteractiveAuthentication no\\nPubkeyAuthentication yes\\nStrictModes yes\\nAllowTcpForwarding no\\nAllowAgentForwarding no\\nX11Forwarding no\\nPermitTunnel no\\nForceCommand {root}/force-command\\nLogLevel ERROR\\n'
(root/'sshd_config').write_text(config)
children=[]
try:
 subprocess.run([str(root/'bin/tmux'),'new-session','-d','-s','retui_analysis','-x','80','-y','24',prefix+'/bin/python3 '+str(root/'echo.py')],env=env,check=True)
 target=subprocess.check_output([str(root/'bin/tmux'),'display-message','-p','-t','retui_analysis','#{window_id}'],env=env,text=True).strip()
 for name,port in zip(('poll','output'),ports):
  log=open(root/(name+'.log'),'wb')
  childenv=dict(env,RETUI_ANALYSIS_COUNTER=str(root/(name+'-count')))
  children.append(subprocess.Popen([prefix+'/bin/socat',f'TCP-LISTEN:{port},bind=127.0.0.1,fork,reuseaddr',f'EXEC:{prefix}/bin/sh {root}/retui-{name} bridge socket-client'],env=childenv,stdout=log,stderr=log,start_new_session=True))
 sshenv=dict(os.environ,HOME='/data/data/com.termux/files/home',PREFIX=prefix,PATH=prefix+'/bin:/system/bin',TMPDIR=prefix+'/tmp')
 log=open(root/'sshd.log','wb')
 children.append(subprocess.Popen([prefix+'/bin/sshd','-D','-e','-f',str(root/'sshd_config')],env=sshenv,stdout=log,stderr=log,start_new_session=True))
 time.sleep(.4)
 for child in children:
  if child.poll() is not None: raise RuntimeError('listener exited '+str(child.returncode))
 print(json.dumps(dict(ports=ports,target=target,pids=[c.pid for c in children])),flush=True)
 sys.stdin.buffer.read(1)
finally:
 for child in children:
  try: os.killpg(child.pid,signal.SIGTERM)
  except ProcessLookupError: pass
 for child in children:
  try: child.wait(timeout=3)
  except subprocess.TimeoutExpired: os.killpg(child.pid,signal.SIGKILL)
 subprocess.run([str(root/'bin/tmux'),'kill-server'],env=env,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
'''
        self.put('fixture.py', remote_script.encode())
        cmd = f'export PATH={PREFIX}/bin:/system/bin; exec {PREFIX}/bin/python3 {self.remote}/fixture.py {self.remote} {PREFIX}'
        self.remote_process = subprocess.Popen(self.adb + ['shell', '-T', 'run-as com.termux sh -c ' + shlex.quote(cmd)],
                                               stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        ready = queue.Queue()
        threading.Thread(target=lambda: ready.put(self.remote_process.stdout.readline()), daemon=True).start()
        line = ready.get(timeout=15)
        if not line:
            raise RuntimeError(self.remote_process.stderr.read().decode())
        self.info = json.loads(line)
        self.localports = [self.forward(p) for p in self.info['ports']]
        host_public = (self.work / 'host.pub').read_text().split()
        (self.work / 'known_hosts').write_text(f'[127.0.0.1]:{self.localports[2]} ' + ' '.join(host_public[:2]) + '\n')
        self.ssh_args = ['ssh', '-F', '/dev/null', '-o', 'BatchMode=yes', '-o', 'IdentitiesOnly=yes',
                         '-o', 'StrictHostKeyChecking=yes', '-o', 'UserKnownHostsFile=' + str(self.work / 'known_hosts'),
                         '-o', 'GlobalKnownHostsFile=/dev/null', '-o', 'ControlMaster=no', '-o', 'ConnectTimeout=5',
                         '-o', 'LogLevel=ERROR', '-i', str(self.work / 'client'), '-p', str(self.localports[2]), '-tt', 'u0_a1000@127.0.0.1']

    def connect_bridge(self, index, mode):
        connection = socket.create_connection(('127.0.0.1', self.localports[index]), timeout=10)
        connection.settimeout(None)
        self.connections.append(connection)
        reader = Reader(connection.makefile('rb'), True)
        target = base64.b64encode(self.info['target'].encode()).decode()
        start = time.perf_counter()
        connection.sendall(f'HELLO isolated-analysis-token 80 24 {mode} {target} NO_CREATE\n'.encode())
        reader.wait(b'READY', after=start)
        return connection, reader, (time.perf_counter() - start) * 1000

    def connect_ssh(self, marker=b'READY'):
        start = time.perf_counter()
        child = subprocess.Popen(self.ssh_args, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                 env=dict(os.environ, TERM='xterm-256color'), bufsize=0)
        self.ssh.append(child)
        reader = Reader(child.stdout, False)
        try:
            reader.wait(marker, after=start)
        except Exception:
            if child.poll() is not None:
                print(child.stderr.read().decode(), flush=True)
            raise
        return child, reader, (time.perf_counter() - start) * 1000

    def counters(self):
        script = f'for n in poll output ssh; do printf "%s " "$n"; if test -f {self.remote}/$n-count; then wc -l < {self.remote}/$n-count; else echo 0; fi; done'
        return {line.split()[0]: int(line.split()[1]) for line in self.run(script).decode().splitlines()}

    def tmux(self, *args):
        command = ' '.join(shlex.quote(arg) for arg in [self.remote + '/bin/tmux', *args])
        return self.run(f'export PATH={PREFIX}/bin:/system/bin; export HOME={self.remote}; ' + command).decode().strip()

    def functional_checks(self, output, ssh, sr, marker):
        checks = {}
        # Public-key authentication alone is insufficient without server identity.
        badargs = list(self.ssh_args)
        index = badargs.index('UserKnownHostsFile=' + str(self.work / 'known_hosts'))
        badargs[index] = 'UserKnownHostsFile=/dev/null'
        rejected = subprocess.run(badargs, input=b'', capture_output=True, timeout=10)
        assert rejected.returncode == 255 and b'Host key verification failed' in rejected.stderr
        checks['unknown_host_key_rejected'] = True
        def send(data):
            output.sendall(b'TYPE_HEX 80 24 ' + data.hex(' ').encode() + b'\n')
        start = time.perf_counter()
        send(b'\x1bcMAIN_BUFFER_PROOF')
        sr.wait(b'MAIN_BUFFER_PROOF', after=start)
        start = time.perf_counter()
        send(b'\x1b[?1049h\x1b[2J\x1b[HALT_BUFFER_PROOF')
        sr.wait(b'ALT_BUFFER_PROOF', after=start)
        assert self.tmux('display-message', '-p', '-t', self.info['target'], '#{alternate_on}') == '1'
        ssh.terminate(); ssh.wait(timeout=5)
        reattached, rer, elapsed = self.connect_ssh(b'ALT_BUFFER_PROOF')
        assert not any(b'MAIN_BUFFER_PROOF' in body for _, body in rer.samples)
        start = time.perf_counter()
        send(b'\x1b[?1049l')
        rer.wait(b'MAIN_BUFFER_PROOF', after=start)
        checks['ssh_reconnect_during_alternate_screen'] = {'alternate_redrawn': True,
            'main_buffer_redrawn_on_exit': True, 'attach_ms': round(elapsed, 2),
            'boundary': 'tmux redraw bytes observed; Android terminal rendering not tested'}
        second, rr, _ = self.connect_ssh(b'MAIN_BUFFER_PROOF')
        other = self.tmux('new-window', '-d', '-P', '-F', '#{window_id}', '-t', 'retui_analysis',
                          PREFIX + '/bin/python3 ' + self.remote + '/echo.py')
        self.tmux('send-keys', '-H', '-t', other, *b'SECOND_TILE_PROOF'.hex(' ').split())
        time.sleep(.1)
        start = time.perf_counter()
        self.tmux('select-window', '-t', other)
        rer.wait(b'SECOND_TILE_PROOF', after=start)
        rr.wait(b'SECOND_TILE_PROOF', after=start)
        checks['two_ssh_clients_same_session_follow_same_selected_window'] = True
        for name in ('analysis-left', 'analysis-right'):
            self.tmux('new-session', '-d', '-t', 'retui_analysis', '-s', name)
        self.tmux('select-window', '-t', 'analysis-left:' + self.info['target'])
        self.tmux('select-window', '-t', 'analysis-right:' + other)
        left = self.tmux('display-message', '-p', '-t', 'analysis-left', '#{window_id}')
        right = self.tmux('display-message', '-p', '-t', 'analysis-right', '#{window_id}')
        assert left == self.info['target'] and right == other
        self.tmux('kill-session', '-t', 'analysis-left')
        self.tmux('has-session', '-t', 'analysis-right')
        checks['grouped_sessions_keep_independent_selection_and_survive_other_group_member_close'] = True
        return checks

    def close(self):
        for connection in self.connections:
            try:
                connection.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            connection.close()
        for child in self.ssh:
            if child.poll() is None:
                child.terminate()
            try:
                child.wait(timeout=3)
            except subprocess.TimeoutExpired:
                child.kill()
        if self.remote_process:
            try:
                self.remote_process.communicate(input=b'x', timeout=12)
            except subprocess.TimeoutExpired:
                self.remote_process.kill()
        for port in self.forwarded:
            subprocess.run(self.adb + ['forward', '--remove', f'tcp:{port}'], capture_output=True)
        # Only the fresh UUID-named fixture directory is removed.
        self.run('rm -rf ' + shlex.quote(self.remote))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, default=HERE / 'evidence/phone-probe.json')
    parser.add_argument('--samples', type=int, default=40)
    parser.add_argument('--isolated', action='store_true', help='measure one active observer at a time')
    args = parser.parse_args()
    result = {'serial': args.serial, 'endpoint': 'host write -> complete decoded helper frame OR SSH stdout bytes',
              'limitations': ['USB/ADB forwarding included; not same-device Android socket latency',
                              'No Android emulator, Canvas, IME, or touch-to-pixel measurement',
                              'All observers connected to one isolated raw-echo job at 80x24',
                              'Competing observers add work; no isolated CPU or battery result',
                              'Test listeners started through debuggable run-as, not Termux foreground-service lifecycle'],
              'samples_per_path': args.samples}
    if args.isolated:
        result['observer_mode'] = 'one active observer at a time; output 0.4.4, SSH, then polling 0.4.2'
        result['limitations'][2] = 'One active observer on a freshly reset isolated raw-echo job per path, 80x24'
        result['limitations'][3] = 'Sequential backend measurements; no CPU or battery measurement'
    with tempfile.TemporaryDirectory(prefix='retui-ssh-analysis-') as directory:
        probe = Probe(args.serial, Path(directory))
        try:
            probe.setup()
            print('isolated listeners ready', flush=True)
            if args.isolated:
                measurements = {}
                raw = {}
                for path in ('output_0_4_4', 'ssh_pty', 'poll_0_4_2'):
                    probe.tmux('send-keys', '-H', '-t', probe.info['target'], *(b'\x1bcREADY').hex(' ').split())
                    time.sleep(.2)
                    if path == 'ssh_pty':
                        client, reader, attach = probe.connect_ssh()
                    else:
                        client, reader, attach = probe.connect_bridge(0 if path == 'poll_0_4_2' else 1,
                                                                      'STREAM' if path == 'poll_0_4_2' else 'OUTPUT')
                    values = []
                    for i in range(args.samples + 3):
                        marker = f'I{i:06d}|'.encode()
                        data = b'\r\n' + marker
                        start = time.perf_counter()
                        if path == 'ssh_pty':
                            client.stdin.write(data); client.stdin.flush()
                        elif path == 'poll_0_4_2':
                            client.sendall(b'TYPE 80 24 ' + base64.b64encode(data) + b'\n')
                        else:
                            client.sendall(b'TYPE_HEX 80 24 ' + data.hex(' ').encode() + b'\n')
                        elapsed = (reader.wait(marker, after=start) - start) * 1000
                        if i >= 3:
                            values.append(elapsed)
                        time.sleep(.013 + random.Random(i).random() * .04)
                    measurements[path] = summary(values)
                    raw[path] = values
                    if path == 'ssh_pty':
                        client.terminate(); client.wait(timeout=5)
                    else:
                        client.sendall(b'BYE\n')
                        client.shutdown(socket.SHUT_RDWR); client.close()
                    time.sleep(.5)
                result['native_roundtrip'] = measurements
                result['native_raw_ms'] = raw
                result['status'] = 'passed'
                return
            poll, pr, pt = probe.connect_bridge(0, 'STREAM')
            output, out, ot = probe.connect_bridge(1, 'OUTPUT')
            ssh, sr, st = probe.connect_ssh()
            result['initial_attach_ms'] = {'poll_0_4_2': round(pt, 2), 'output_0_4_4': round(ot, 2), 'ssh_pty': round(st, 2)}
            print('three paths connected', flush=True)
            before = probe.counters()
            time.sleep(10)
            after = probe.counters()
            result['idle_10s_tmux_process_invocations'] = {k: after[k] - before[k] for k in before}
            readers = {'poll_0_4_2': pr, 'output_0_4_4': out, 'ssh_pty': sr}
            byte_start = {name: reader.wire_bytes for name, reader in readers.items()}
            paired = {key: [] for key in readers}
            for i in range(args.samples + 3):
                marker = f'P{i:06d}|'.encode()
                start = time.perf_counter()
                output.sendall(b'TYPE_HEX 80 24 ' + (b'\r\n' + marker).hex(' ').encode() + b'\n')
                for name, reader in readers.items():
                    elapsed = (reader.wait(marker, after=start) - start) * 1000
                    if i >= 3:
                        paired[name].append(elapsed)
                time.sleep(.013 + random.Random(i).random() * .04)
            result['paired_output_same_input'] = {name: summary(values) for name, values in paired.items()}
            result['paired_raw_ms'] = paired
            result['paired_application_output_bytes_including_3_warmups'] = {
                name: reader.wire_bytes - byte_start[name] for name, reader in readers.items()}
            result['byte_count_boundary'] = 'helper protocol bytes vs decrypted SSH PTY stdout; SSH wire overhead excluded'
            native = {key: [] for key in readers}
            schedule = list(readers) * (args.samples + 3)
            random.Random(20261015).shuffle(schedule)
            counts = {key: 0 for key in readers}
            for i, name in enumerate(schedule):
                marker = f'N{i:06d}|'.encode()
                start = time.perf_counter()
                if name == 'poll_0_4_2':
                    poll.sendall(b'TYPE 80 24 ' + base64.b64encode(b'\r\n' + marker) + b'\n')
                elif name == 'output_0_4_4':
                    output.sendall(b'TYPE_HEX 80 24 ' + (b'\r\n' + marker).hex(' ').encode() + b'\n')
                else:
                    ssh.stdin.write(b'\r\n' + marker)
                    ssh.stdin.flush()
                elapsed = (readers[name].wait(marker, after=start) - start) * 1000
                if counts[name] >= 3:
                    native[name].append(elapsed)
                counts[name] += 1
                time.sleep(.013 + random.Random(i).random() * .04)
            result['native_roundtrip'] = {name: summary(values) for name, values in native.items()}
            result['native_raw_ms'] = native
            # Reattach the normal tmux client while the cat job and normal screen survive.
            ssh.terminate(); ssh.wait(timeout=5)
            reattached, rer, elapsed = probe.connect_ssh(marker)
            rer.wait(marker)
            result['ssh_reconnect'] = {'normal_screen_marker_restored': True, 'attach_ms': round(elapsed, 2)}
            result['functional_checks'] = probe.functional_checks(output, reattached, rer, marker)
            result['status'] = 'passed'
        except Exception as exc:
            result['status'] = 'failed'
            result['error'] = repr(exc)
            for name in ('poll.log', 'output.log', 'sshd.log'):
                try:
                    result[name] = probe.run('cat ' + shlex.quote(probe.remote + '/' + name)).decode(errors='replace')[-5000:]
                except Exception:
                    pass
            raise
        finally:
            try:
                probe.close()
                result['cleanup'] = 'fixture clients, isolated tmux server, listeners, ADB forwards, keys and device directory removed'
            finally:
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(json.dumps(result, indent=2) + '\n')
                print(json.dumps({k: v for k, v in result.items() if not k.endswith('_ms') and k not in ('paired_raw_ms', 'native_raw_ms')}, indent=2), flush=True)


if __name__ == '__main__':
    main()
