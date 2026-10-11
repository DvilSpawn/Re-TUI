"""Bounded real-backend stress test. Always creates a fresh loopback-only database.

Never accepts a live base URL, credentials or existing data directory. Results contain
only aggregate metrics. Run on the VPS in a resource-limited transient systemd unit.
"""
import argparse
import concurrent.futures
import contextlib
import hashlib
import http.client
import json
import math
import os
from pathlib import Path
import queue
import re
import secrets
import shutil
import socket
import sqlite3
import subprocess
import tempfile
import threading
import time
import urllib.parse
import uuid

ROOT = Path(__file__).resolve().parent
FEED = '/api/collections/messages/records?sort=-created,-id&perPage=50'


def percentile(values, fraction):
    values = sorted(values)
    return round(values[max(0, math.ceil(len(values) * fraction) - 1)], 2) if values else None


def request(base, method, path, body=None, token='', ip='', timeout=15):
    url = urllib.parse.urlsplit(base)
    if url.scheme != 'http' or url.hostname != '127.0.0.1':
        raise ValueError('Load traffic is restricted to loopback')
    conn = http.client.HTTPConnection(url.hostname, url.port, timeout=timeout)
    headers = {'Content-Type': 'application/json', 'Authorization': token}
    if ip:
        headers['X-Load-Test-IP'] = ip
    started = time.monotonic()
    try:
        conn.request(method, url.path + path, None if body is None else json.dumps(body).encode(), headers)
        response = conn.getresponse()
        data = response.read()
        return response.status, json.loads(data or b'{}'), (time.monotonic() - started) * 1000, dict(response.getheaders())
    finally:
        conn.close()


class Metrics:
    def __init__(self):
        self.lock = threading.Lock()
        self.rows = []
        self.operations = []
        self.failures = {}

    def call(self, base, method, path, body=None, token='', ip=''):
        started = time.monotonic()
        failure = None
        try:
            result = request(base, method, path, body, token, ip)
        except (OSError, ValueError, http.client.HTTPException) as error:
            failure = type(error).__name__
            result = (0, {}, (time.monotonic() - started) * 1000, {})
        with self.lock:
            self.rows.append((method, result[0], result[2]))
            endpoint = path.split('?')[0].removeprefix('/api/community/').removeprefix('/api/collections/').removesuffix('/records')
            self.operations.append((method + ' ' + endpoint, result[0], result[2]))
            if failure:
                self.failures[failure] = self.failures.get(failure, 0) + 1
        return result

    def summary(self, seconds):
        def groups(rows):
            result = {}
            for name, status, elapsed in rows:
                group = result.setdefault(name, {'statuses': {}, 'latencies': [], 'success_latencies': []})
                group['statuses'][str(status)] = group['statuses'].get(str(status), 0) + 1
                group['latencies'].append(elapsed)
                if 200 <= status < 300:
                    group['success_latencies'].append(elapsed)
            for group in result.values():
                values = group.pop('latencies')
                successful = group.pop('success_latencies')
                group.update(count=len(values), p50_ms=percentile(values, .5), p95_ms=percentile(values, .95),
                             p99_ms=percentile(values, .99), successful_p95_ms=percentile(successful, .95))
            return result
        return {'requests': len(self.rows), 'requests_per_second': round(len(self.rows) / seconds, 2),
                'methods': groups(self.rows), 'operations': groups(self.operations), 'network_failures': self.failures,
                'unexpected_errors': sum(s == 0 or s >= 500 for _, s, _ in self.rows)}


class Stream:
    def __init__(self, base, token, ip):
        url = urllib.parse.urlsplit(base)
        self.conn = http.client.HTTPConnection('127.0.0.1', url.port, timeout=10)
        self.conn.request('GET', url.path + '/api/realtime', headers={'Authorization': token, 'X-Load-Test-IP': ip})
        self.response = self.conn.getresponse()
        self.status = self.response.status
        self.events = queue.Queue(maxsize=1024)
        self.arrivals = {}
        self.closed = threading.Event()
        self.lock = threading.Lock()
        self.socket = self.conn.sock or getattr(getattr(self.response.fp, 'raw', None), '_sock', None)
        if self.status == 200:
            self.thread = threading.Thread(target=self.read, daemon=True)
            self.thread.start()

    def read(self):
        event, data = '', []
        try:
            while True:
                line = self.response.readline(8193)
                if not line or len(line) > 8192:
                    break
                line = line.decode().rstrip('\r\n')
                if not line:
                    if data:
                        payload = json.loads('\n'.join(data))
                        now = time.monotonic()
                        if event == 'community' and payload.get('id'):
                            with self.lock:
                                self.arrivals[payload['id']] = now
                        self.events.put_nowait((event, payload))
                    event, data = '', []
                elif line.startswith('event:'):
                    event = line[6:].strip()
                elif line.startswith('data:'):
                    data.append(line[5:].lstrip(' '))
        except (OSError, ValueError, http.client.HTTPException, queue.Full):
            pass
        finally:
            self.closed.set()

    def subscribe(self, base, token, ip):
        if self.status != 200:
            raise RuntimeError('SSE connection rejected: ' + str(self.status))
        event, payload = self.events.get(timeout=5)
        if event != 'PB_CONNECT':
            raise RuntimeError('Missing SSE handshake')
        status, _, _, _ = request(base, 'POST', '/api/realtime',
                                 {'clientId': payload['clientId'], 'subscriptions': ['community']}, token, ip)
        if status != 204:
            raise RuntimeError('SSE subscription rejected: ' + str(status))

    def close(self):
        if self.socket:
            with contextlib.suppress(OSError):
                self.socket.shutdown(socket.SHUT_RDWR)
        if hasattr(self, 'thread'):
            self.thread.join(timeout=2)
        self.response.close()
        self.conn.close()


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def nginx_config(directory, upstream, listener):
    source = (ROOT / 'nginx-community.conf').read_text()
    source = source.replace('listen 443 ssl;', f'listen 127.0.0.1:{listener};')
    source = re.sub(r'^\s*ssl_.*;\n', '', source, flags=re.MULTILINE)
    source = source.replace('/var/log/nginx/retui-community-timing.log', str(directory / 'timing.log'))
    source = source.replace('/etc/nginx/snippets/retui-community-proxy.conf', str(directory / 'proxy.conf'))
    source = source.replace('/opt/retui-community/public', str(ROOT / 'public'))
    # This trusted synthetic IP header exists ONLY in the fresh loopback fixture.
    source = source.replace('server {', 'server {\n set_real_ip_from 127.0.0.1;\n real_ip_header X-Load-Test-IP;', 1)
    (directory / 'proxy.conf').write_text((ROOT / 'nginx-proxy.conf').read_text()
                                        .replace('127.0.0.1:8095', f'127.0.0.1:{upstream}'))
    return (f'user root;\npid {directory}/nginx.pid;\nerror_log {directory}/nginx-error.log;\n'
            f'worker_processes 1;\nevents {{ worker_connections 2048; }}\n'
            f'http {{ access_log off; client_body_temp_path {directory}/body;\n{source}\n}}\n')


def seed(data, users):
    """Large synthetic history; HTTP workload still uses the actual authorization/hooks."""
    now = time.strftime('%Y-%m-%d %H:%M:%S.000Z', time.gmtime(time.time() - 60))
    ids = lambda: secrets.token_hex(8)[:15]
    with sqlite3.connect(data / 'data.db') as db:
        messages = [(ids(), u['id'], u['handle'], 'synthetic history ' + 'x' * 180,
                     str(uuid.uuid4()), now, now) for u in users for _ in range(20)]
        db.executemany('INSERT INTO messages (id,author,author_handle,body,client_id,created,updated) VALUES (?,?,?,?,?,?,?)', messages)
        threads = [(ids(), u['id'], u['handle'], 'help', 'synthetic discussion', 'synthetic root',
                    str(uuid.uuid4()), now, now) for u in users for _ in range(2)]
        db.executemany('INSERT INTO threads (id,author,author_handle,channel,title,body,client_id,created,updated) VALUES (?,?,?,?,?,?,?,?,?)', threads)
        replies = [(ids(), t[1], t[2], t[0], 'synthetic reply ' + 'x' * 180, str(uuid.uuid4()), now, now)
                   for t in threads for _ in range(5)]
        db.executemany('INSERT INTO thread_replies (id,author,author_handle,thread,body,client_id,created,updated) VALUES (?,?,?,?,?,?,?,?)', replies)
    return {'messages': len(messages), 'threads': len(threads), 'replies': len(replies), 'thread_id': threads[0][0]}


def stage(base, users, number, duration, index, thread_id, abort):
    users = users[:number]
    streams, metrics, deliveries, missing = [], Metrics(), [], 0
    started = time.monotonic()
    try:
        for n, user in enumerate(users):
            user['ip'] = f'198.18.{index}.{n + 1}'
            stream = Stream(base, user['token'], user['ip'])
            streams.append(stream)
            stream.subscribe(base, user['token'], user['ip'])
        started = time.monotonic()
        deadline = started + duration
        paths = [FEED, '/api/collections/threads/records?filter=channel%3D%22help%22&perPage=50',
                 '/api/collections/thread_replies/records?filter=thread%3D%22' + thread_id + '%22&perPage=50',
                 '/api/community/users?perPage=20', '/api/community/mentions?perPage=50']

        def reader(n):
            user, stream = users[n], streams[n]
            metrics.call(base, 'POST', '/api/community/presence', {'active': True}, user['token'], user['ip'])
            cycle = 0
            next_poll = time.monotonic() + (n % 10) * .05
            while time.monotonic() < deadline and not abort.is_set():
                refresh = False
                try:
                    while stream.events.get_nowait():
                        refresh = True
                except queue.Empty:
                    pass
                if refresh or time.monotonic() >= next_poll:
                    path = FEED if refresh else paths[cycle % len(paths)]
                    metrics.call(base, 'GET', path, token=user['token'], ip=user['ip'])
                    cycle += 1
                    next_poll = time.monotonic() + 2
                time.sleep(.05)

        with concurrent.futures.ThreadPoolExecutor(max_workers=number) as pool:
            workers = [pool.submit(reader, n) for n in range(number)]
            message = 0
            while time.monotonic() < deadline and not abort.is_set():
                user = users[message % number]
                sent = time.monotonic()
                status, record, _, _ = metrics.call(base, 'POST', '/api/collections/messages/records',
                    {'author': user['id'], 'body': 'synthetic live stress message', 'client_id': str(uuid.uuid4())},
                    user['token'], user['ip'])
                if status == 200:
                    wait_until = time.monotonic() + 15
                    while time.monotonic() < wait_until:
                        if all(record['id'] in s.arrivals for s in streams):
                            break
                        time.sleep(.01)
                    for stream in streams:
                        arrived = stream.arrivals.get(record['id'])
                        if arrived is None:
                            missing += 1
                        else:
                            deliveries.append((arrived - sent) * 1000)
                message += 1
                time.sleep(max(0, min(deadline - time.monotonic(), sent + 3 - time.monotonic())))
            for worker in workers:
                worker.result()
        result = metrics.summary(time.monotonic() - started)
        result.update(clients=number, scenario='paced_reads_presence_posts_and_live_refresh',
                      live_notifications=len(deliveries), missing_notifications=missing,
                      live_p95_ms=percentile(deliveries, .95), live_p99_ms=percentile(deliveries, .99),
                      closed_streams=sum(s.closed.is_set() for s in streams))
        return result
    finally:
        for stream in streams:
            stream.close()


def burst(base, users, abort, direct=False):
    metrics = Metrics()
    started = time.monotonic()
    def worker(n):
        user = users[n % len(users)]
        for _ in range(20):
            if abort.is_set():
                break
            metrics.call(base, 'GET', FEED, token=user['token'], ip='' if direct else '198.19.0.1')
    with concurrent.futures.ThreadPoolExecutor(max_workers=50) as pool:
        list(pool.map(worker, range(50)))
    return metrics.summary(time.monotonic() - started)


def write_burst(base, users, streams):
    metrics, accepted, attempts = Metrics(), [], []
    started = time.monotonic()
    def writer(n):
        user = users[n]
        identity = str(uuid.uuid4())
        payload = {'author': user['id'], 'body': 'concurrent retry fixture', 'client_id': identity}
        sent = time.monotonic()
        status, record, _, _ = metrics.call(base, 'POST', '/api/collections/messages/records', payload,
                                           user['token'], user['ip'])
        attempts.append((user['id'], identity))
        if status == 200:
            accepted.append((record['id'], sent))
            # Exercise same-UUID retry while other writers and fanout are active.
            metrics.call(base, 'POST', '/api/collections/messages/records', payload, user['token'], user['ip'])
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        list(pool.map(writer, range(10)))
    wait_until = time.monotonic() + 15
    while time.monotonic() < wait_until:
        if all(rid in s.arrivals for rid, _ in accepted for s in streams):
            break
        time.sleep(.02)
    result = metrics.summary(time.monotonic() - started)
    deliveries = [(s.arrivals[rid] - sent) * 1000 for rid, sent in accepted for s in streams if rid in s.arrivals]
    result.update(accepted_posts=len(accepted), retry_attempt_identities=len(attempts),
                  live_deliveries=len(deliveries), live_p95_ms=percentile(deliveries, .95),
                  missing_notifications=sum(rid not in s.arrivals for rid, _ in accepted for s in streams))
    return result


def checks(report):
    if 'failure' in report or not report.get('stages'):
        return {'complete': False}
    normal = report['stages']
    retry = report['concurrent_write_retry_burst']
    backup = report['selective_recovery']
    return {
        'normal_requests_successful': all(all(set(g['statuses']) == {'200'} for g in s['methods'].values()) for s in normal),
        'live_delivery_complete': all(s['missing_notifications'] == 0 and s['closed_streams'] == 0 for s in normal),
        'concurrent_retries_not_duplicated': retry['accepted_posts'] == 10 and
            retry['methods']['POST']['statuses'] == {'200': 10, '400': 10} and report['duplicate_message_ids'] == 0,
        'burst_live_delivery_complete': retry['missing_notifications'] == 0 and retry['live_deliveries'] == 1000,
        'concurrent_login_successful': report['concurrent_login_burst']['methods']['POST']['statuses'] == {'200': 30},
        'raw_read_burst_successful': report['backend_read_burst']['methods']['GET']['statuses'] == {'200': 1000},
        'abuse_cleanly_throttled': report['shared_ip_abuse_burst']['unexpected_errors'] == 0 and
            set(report['shared_ip_abuse_burst']['methods']['GET']['statuses']) == {'200', '429'},
        'ten_streams_per_ip_enforced': report['shared_ip_stream_limit'] == [200] * 10 + [429],
        'read_flood_does_not_block_posting_or_other_ips': all(v == 200 for v in report['rate_limit_isolation'].values()),
        'crash_recovery_preserves_data': report['crash_retained_message_count'] and report['post_restart_read_status'] == 200 and
            report['database_integrity'] == 'ok',
        'selective_backup_reopens_without_transient_data': backup['integrity'] == 'ok' and backup['reopened'] and
            backup['authenticated_forum_status'] == 200 and backup['authenticated_empty_general'] and
            all(v == 0 for v in backup['transient_counts'].values()) and
            backup['persistent_counts'] == {'members': 100, 'threads': 200, 'thread_replies': 1000},
        'production_guard_healthy': not report['production_health_guard']['aborted'] and
            report['production_health_guard']['unhealthy_samples'] == 0,
        'temporary_database_removed': report['synthetic_database_removed'],
    }


def run(args):
    version = subprocess.check_output([args.binary, '--version'], text=True).strip()
    if version != 'pocketbase version 0.40.5':
        raise ValueError('Use the deployed pinned PocketBase 0.40.5 binary')
    report = {'backend': version, 'transport': 'isolated loopback nginx, no public TLS/network load',
              'max_clients': 100, 'http_timeout_seconds': 15,
              'harness_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
              'seed_mode': 'fresh synthetic SQL history, no production records',
              'source_sha256': {str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest()
                                for folder in ('pb_hooks', 'pb_migrations') for p in (ROOT / folder).glob('*.js')}}
    with tempfile.TemporaryDirectory(prefix='retui-load-fixture-') as temp:
        directory = Path(temp)
        # Isolated nginx workers run under this directory's owner, not the live nginx user.
        data, backend_port, proxy_port = directory / 'data', port(), port()
        password = secrets.token_urlsafe(40)
        processes, units, resources, health = [], [], [], []
        abort, monitor_done = threading.Event(), threading.Event()
        current_unit = ['']
        def start_backend(command, log):
            unit = 'retui-load-backend-' + secrets.token_hex(4)
            units.append(unit)
            current_unit[0] = unit
            limits = ['systemd-run', '--quiet', '--collect', '--wait', '--pipe', '--service-type=exec',
                      '--unit=' + unit, '-p', f'CPUQuota={args.backend_cpu}%', '-p', 'MemoryMax=256M', '-p', 'TasksMax=128',
                      '-p', 'LimitNOFILE=4096', '-p', 'RuntimeMaxSec=600',
                      '--setenv=RETUI_BOOTSTRAP_PASSWORD', '--setenv=GOMEMLIMIT=192MiB']
            process = subprocess.Popen(limits + command, env=dict(os.environ, RETUI_BOOTSTRAP_PASSWORD=password), stdout=log, stderr=log)
            processes.append(process)
            return process

        def monitor():
            while not monitor_done.wait(2):
                output = subprocess.run(['systemctl', 'show', current_unit[0], '-p', 'CPUUsageNSec',
                                         '-p', 'MemoryCurrent', '-p', 'MemoryPeak', '-p', 'MainPID', '-p', 'ControlGroup'],
                                        capture_output=True, text=True, timeout=3)
                sample = dict(line.split('=', 1) for line in output.stdout.splitlines() if '=' in line)
                sample['elapsed_s'] = round(time.monotonic() - monitoring_started, 2)
                sample['unit'] = current_unit[0]
                with contextlib.suppress(OSError):
                    stats = Path('/sys/fs/cgroup' + sample.get('ControlGroup', '') + '/cpu.stat').read_text()
                    sample['cpu_stat'] = dict(line.split() for line in stats.splitlines())
                pid = sample.get('MainPID', '0')
                if pid.isdigit() and pid != '0':
                    with contextlib.suppress(OSError):
                        sample['open_fds'] = len(list(Path('/proc', pid, 'fd').iterdir()))
                resources.append(sample)
                if args.guard_production:
                    try:
                        status, _, ms, _ = request('http://127.0.0.1:8095', 'GET', '/api/health', timeout=2)
                    except (OSError, ValueError, http.client.HTTPException):
                        status, ms = 0, 5000
                    health.append({'status': status, 'latency_ms': round(ms, 2)})
                    if status != 200 or ms > 1000:
                        abort.set()

        try:
            with (directory / 'backend.log').open('w+') as log, (directory / 'nginx.log').open('w+') as nginx_log:
                command = [args.binary, 'serve', '--http=127.0.0.1:' + str(backend_port), '--dir=' + str(data),
                           '--hooksDir=' + str(ROOT / 'pb_hooks'), '--migrationsDir=' + str(ROOT / 'pb_migrations'),
                           '--automigrate=false', '--hooksWatch=false']
                backend = start_backend(command, log)
                monitoring_started = time.monotonic()
                monitoring = threading.Thread(target=monitor, daemon=True)
                monitoring.start()
                direct = 'http://127.0.0.1:' + str(backend_port)
                for _ in range(150):
                    try:
                        if request(direct, 'GET', '/api/health')[0] == 200:
                            break
                    except OSError:
                        pass
                    if backend.poll() is not None:
                        raise RuntimeError('Fixture backend startup failed; no log secrets are printed')
                    time.sleep(.1)
                else:
                    raise RuntimeError('Fixture startup timed out')
                status, auth, _, _ = request(direct, 'POST', '/api/collections/_superusers/auth-with-password',
                                           {'identity': 'admin@localhost.invalid', 'password': password})
                if status != 200:
                    raise RuntimeError('Fixture bootstrap failed')
                users, setup_metrics = [], Metrics()
                for n in range(100):
                    if abort.is_set():
                        raise RuntimeError('Production health guard stopped setup')
                    handle, member_password = 'load_' + secrets.token_hex(6), secrets.token_urlsafe(30)
                    status, member, _, _ = setup_metrics.call(direct, 'POST', '/api/collections/members/records',
                         {'handle': handle, 'password': member_password, 'passwordConfirm': member_password})
                    if status != 200:
                        raise RuntimeError('Fixture registration failed: ' + str(status))
                    status, login, _, _ = setup_metrics.call(direct, 'POST', '/api/collections/members/auth-with-password',
                                                 {'identity': handle, 'password': member_password})
                    if status != 200 or request(direct, 'POST', '/api/community/policy',
                                               {'version': '2026-10-10'}, login['token'])[0] != 200:
                        raise RuntimeError('Fixture login/policy failed')
                    users.append({'id': member['id'], 'handle': handle, 'token': login['token'], 'password': member_password})
                report['sequential_signup_login'] = setup_metrics.summary(time.monotonic() - monitoring_started)
                history = seed(data, users)
                report['history'] = {k: v for k, v in history.items() if k != 'thread_id'}
                config = directory / 'nginx.conf'
                config.write_text(nginx_config(directory, backend_port, proxy_port))
                nginx_args = [args.nginx, '-p', str(directory) + '/', '-c', str(config)]
                checked = subprocess.run(nginx_args + ['-t'], capture_output=True)
                if checked.returncode:
                    raise RuntimeError('Isolated nginx configuration failed: ' + checked.stderr.decode())
                nginx = subprocess.Popen(nginx_args + ['-g', 'daemon off;'], stdout=nginx_log, stderr=nginx_log)
                processes.append(nginx)
                proxy = 'http://127.0.0.1:' + str(proxy_port) + '/community'
                for _ in range(100):
                    try:
                        if request(proxy, 'GET', '/api/health')[0] == 200:
                            break
                    except OSError:
                        pass
                    time.sleep(.05)
                else:
                    raise RuntimeError('Fixture ingress did not start')
                report['stages'] = []
                for i, clients in enumerate((10, 25, 50, 100), 1):
                    if abort.is_set():
                        raise RuntimeError('Production health guard stopped the load ramp')
                    result = stage(proxy, users, clients, args.duration, i, history['thread_id'], abort)
                    report['stages'].append(result)
                    args.output.write_text(json.dumps(dict(report, incomplete=True), indent=2) + '\n')
                    print(json.dumps({'stage': result}), flush=True)
                # A fresh stream IP avoids spending prior-stage connection rate budgets.
                streams = []
                try:
                    for n, user in enumerate(users):
                        user['ip'] = f'198.18.5.{n + 1}'
                        stream = Stream(proxy, user['token'], user['ip'])
                        streams.append(stream)
                        stream.subscribe(proxy, user['token'], user['ip'])
                    report['concurrent_write_retry_burst'] = write_burst(proxy, users, streams)
                    print(json.dumps({'concurrent_write_retry_burst': report['concurrent_write_retry_burst']}), flush=True)
                finally:
                    for stream in streams:
                        stream.close()
                login_metrics = Metrics()
                login_started = time.monotonic()
                def login_burst(n):
                    user = users[n]
                    return login_metrics.call(proxy, 'POST', '/api/collections/members/auth-with-password',
                        {'identity': user['handle'], 'password': user['password']}, ip=f'198.19.4.{n + 1}')
                with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
                    list(pool.map(login_burst, range(30)))
                report['concurrent_login_burst'] = login_metrics.summary(time.monotonic() - login_started)
                args.output.write_text(json.dumps(dict(report, incomplete=True), indent=2) + '\n')
                report['backend_read_burst'] = burst(direct, users, abort, direct=True)
                print(json.dumps({'backend_read_burst': report['backend_read_burst']}), flush=True)
                report['shared_ip_abuse_burst'] = burst(proxy, users, abort)
                sockets = []
                try:
                    for n in range(11):
                        stream = Stream(proxy, users[n]['token'], '198.19.1.1')
                        sockets.append(stream)
                    report['shared_ip_stream_limit'] = [s.status for s in sockets]
                finally:
                    for stream in sockets:
                        stream.close()
                user = users[0]
                report['rate_limit_isolation'] = {
                    'fresh_ip_read_status': request(proxy, 'GET', FEED, token=user['token'], ip='198.19.2.1')[0],
                    'abusive_ip_post_status': request(proxy, 'POST', '/api/collections/messages/records',
                        {'author': user['id'], 'body': 'quota isolation probe', 'client_id': str(uuid.uuid4())},
                        user['token'], '198.19.0.1')[0]}
                from selective_backup import snapshot
                recovery = directory / 'recovery'
                recovery.mkdir(mode=0o700)
                snapshot(data / 'data.db', recovery / 'selective.db')
                with sqlite3.connect(recovery / 'selective.db') as db:
                    report['selective_recovery'] = {'integrity': db.execute('PRAGMA integrity_check').fetchone()[0],
                        'persistent_counts': {t: db.execute('SELECT count(*) FROM ' + t).fetchone()[0]
                                              for t in ('members', 'threads', 'thread_replies')},
                        'transient_counts': {t: db.execute('SELECT count(*) FROM ' + t).fetchone()[0]
                                             for t in ('messages', 'reports', 'moderator_requests', 'presence', 'mentions')}}
                with sqlite3.connect((data / 'data.db').resolve().as_uri() + '?mode=ro', uri=True) as db:
                    before_crash = db.execute('SELECT count(*) FROM messages').fetchone()[0]
                # Crash only the synthetic backend. Never signal the production service.
                subprocess.run(['systemctl', 'kill', '--kill-whom=main', '--signal=KILL', current_unit[0]], check=True)
                backend.wait(timeout=10)
                report['crash_backend_exit_code'] = backend.returncode
                restarted = start_backend(command, log)
                recovered = time.monotonic()
                for _ in range(100):
                    try:
                        if request(direct, 'GET', '/api/health')[0] == 200:
                            break
                    except OSError:
                        pass
                    time.sleep(.05)
                report['restart_recovery_ms'] = round((time.monotonic() - recovered) * 1000, 2)
                report['post_restart_read_status'] = request(proxy, 'GET', FEED, token=user['token'], ip='198.19.3.1')[0]
                with sqlite3.connect(data.resolve().as_uri() + '/data.db?mode=ro', uri=True) as db:
                    report['database_integrity'] = db.execute('PRAGMA integrity_check').fetchone()[0]
                    report['crash_retained_message_count'] = db.execute('SELECT count(*) FROM messages').fetchone()[0] == before_crash
                    report['duplicate_message_ids'] = db.execute('SELECT count(*) FROM (SELECT author,client_id FROM messages GROUP BY author,client_id HAVING count(*)>1)').fetchone()[0]
                report['fixture_process_survived'] = restarted.poll() is None and nginx.poll() is None
                restore = directory / 'restore'
                restore.mkdir(mode=0o700)
                shutil.copy2(recovery / 'selective.db', restore / 'data.db')
                restore_port = port()
                restore_command = [arg.replace('--dir=' + str(data), '--dir=' + str(restore))
                                   .replace('--http=127.0.0.1:' + str(backend_port), '--http=127.0.0.1:' + str(restore_port))
                                   for arg in command]
                restored_process = start_backend(restore_command, log)
                restored_base = 'http://127.0.0.1:' + str(restore_port)
                for _ in range(100):
                    try:
                        if request(restored_base, 'GET', '/api/health')[0] == 200:
                            break
                    except OSError:
                        pass
                    time.sleep(.05)
                report['selective_recovery']['reopened'] = restored_process.poll() is None
                report['selective_recovery']['authenticated_forum_status'] = request(restored_base, 'GET',
                    '/api/collections/threads/records/' + history['thread_id'], token=user['token'])[0]
                status, page, _, _ = request(restored_base, 'GET', FEED, token=user['token'])
                report['selective_recovery']['authenticated_empty_general'] = status == 200 and page.get('items') == []
        except Exception as error:
            report['failure'] = {'type': type(error).__name__, 'message': str(error)}
            print(json.dumps({'failure': report['failure']}), flush=True)
        finally:
            monitor_done.set()
            if 'monitoring' in locals():
                monitoring.join(timeout=5)
            for unit in units:
                subprocess.run(['systemctl', 'stop', unit], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            for process in reversed(processes):
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=5)
    report['resource_limits'] = {'CPUQuota': f'{args.backend_cpu}%', 'MemoryMax': '256M', 'GOMEMLIMIT': '192MiB',
                                 'TasksMax': 128, 'LimitNOFILE': 4096}
    report['resources'] = resources
    report['production_health_guard'] = {'enabled': args.guard_production, 'samples': len(health),
        'unhealthy_samples': sum(row['status'] != 200 for row in health),
        'p95_ms': percentile([row['latency_ms'] for row in health], .95), 'aborted': abort.is_set()}
    report['synthetic_database_removed'] = not directory.exists()
    return report


if __name__ == '__main__':
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--binary', required=True)
    parser.add_argument('--nginx', default=shutil.which('nginx'))
    parser.add_argument('--duration', type=int, default=20)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--guard-production', action='store_true', help='Abort if local production health fails or exceeds 1s')
    parser.add_argument('--backend-cpu', type=int, choices=(50, 100), default=50,
                        help='50 matches production; 100 is a fixture-only CPU headroom experiment')
    args = parser.parse_args()
    if not args.nginx or not 10 <= args.duration <= 30 or args.output.exists():
        parser.error('nginx is required, stages must be 10–30 seconds, and the output must be a new file')
    result = run(args)
    result['correctness_checks'] = checks(result)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'report': str(args.output), 'synthetic_database_removed': result['synthetic_database_removed']}))
    raise SystemExit(0 if all(result['correctness_checks'].values()) else 1)
