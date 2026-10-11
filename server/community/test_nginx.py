"""Run the shipped quota/allowlist config through an isolated loopback nginx.

Requires nginx on PATH. No production services, accounts or data are used.
"""
import http.server
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parent
MESSAGES = '/community/api/collections/messages/records'


class Unauthorized(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(401)
        self.end_headers()
        self.wfile.write(b'{"status":401}')

    do_POST = do_GET

    def log_message(self, format, *args):
        pass


class Quotas(unittest.TestCase):
    def test_reads_do_not_consume_posts(self):
        nginx = shutil.which('nginx')
        self.assertIsNotNone(nginx, 'install nginx to run the real ingress regression')
        with tempfile.TemporaryDirectory(prefix='community-nginx-', dir=os.environ.get('TMPDIR')) as tmp:
            root = Path(tmp)
            os.chmod(root, 0o755)
            upstream = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Unauthorized)
            thread = threading.Thread(target=upstream.serve_forever, daemon=True)
            thread.start()
            with socket.socket() as probe:
                probe.bind(('127.0.0.1', 0))
                port = probe.getsockname()[1]
            source = (ROOT / 'nginx-community.conf').read_text()
            shutil.copytree(ROOT / 'public', root / 'public')
            # Only substitute transport/filesystem paths; keep actual ingress policy.
            config = source.replace('listen 443 ssl;', f'listen 127.0.0.1:{port};')
            config = config.replace('/var/log/nginx/retui-community-timing.log',str(root/'timing.log'))
            config = re.sub(r'^\s*ssl_.*;\n', '', config, flags=re.MULTILINE)
            config = config.replace('/etc/nginx/snippets/retui-community-proxy.conf', str(root / 'proxy.conf'))
            config = config.replace('/opt/retui-community/public', str(root / 'public'))
            snippet = (ROOT / 'nginx-proxy.conf').read_text().replace('127.0.0.1:8095', f'127.0.0.1:{upstream.server_port}')
            (root / 'proxy.conf').write_text(snippet)
            (root / 'nginx.conf').write_text(
                f'pid {root}/nginx.pid;\nerror_log {root}/error.log;\n'
                f'events {{}}\nhttp {{ types {{ text/css css; application/javascript js; }} client_body_temp_path {root}/body;\n{config}\n}}\n')
            args = [nginx, '-p', str(root) + '/', '-c', str(root / 'nginx.conf')]
            process = None
            try:
                checked = subprocess.run(args + ['-t'], capture_output=True)
                self.assertEqual(checked.returncode,0,checked.stderr.decode())
                process = subprocess.Popen(args + ['-g', 'daemon off;'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
                for _ in range(100):
                    try:
                        with socket.create_connection(('127.0.0.1', port), timeout=.1):
                            break
                    except OSError:
                        if process.poll() is not None:
                            assert process.stderr is not None
                            self.fail(process.stderr.read().decode())
                        time.sleep(.02)
                else:
                    self.fail('isolated nginx did not listen')

                def request(method, path=MESSAGES):
                    req = urllib.request.Request(f'http://127.0.0.1:{port}' + path,
                                                 method=method, data=b'{}' if method == 'POST' else None)
                    try:
                        with urllib.request.urlopen(req, timeout=3) as response:
                            return response.status
                    except urllib.error.HTTPError as error:
                        error.read()
                        return error.code

                # Two NAT clients at five reads/15 seconds cost 40 reads/minute.
                self.assertEqual([request('GET') for _ in range(40)], [401] * 40)
                self.assertEqual([request('POST','/community/api/community/presence') for _ in range(22)],[401]*22,'presence must have its own posting quota')
                self.assertEqual(request('GET','/community/api/community/users'),401)
                for path in ('policy','report','account-delete','moderation/action'):
                    self.assertEqual(request('POST','/community/api/community/'+path),401)
                    self.assertEqual(request('GET','/community/api/community/'+path),404)
                self.assertEqual(request('GET','/community/api/community/moderation'),401)
                self.assertEqual(request('POST','/community/api/community/moderation'),404)
                for page in ('rules','account-deletion','account-deletion.js','community.css'):
                    self.assertEqual(request('GET','/community/'+page),200)
                    self.assertEqual(request('POST','/community/'+page),404)
                self.assertEqual(request('GET','/community/../pb_hooks/release-gates.pb.js'),404)
                self.assertEqual(request('GET','/community/api/community/mentions'),401)
                self.assertEqual(request('GET','/community/api/community/presence'),404)
                self.assertEqual(request('POST','/community/api/community/users'),404)
                self.assertEqual(request('POST','/community/api/community/mentions'),404)
                self.assertEqual(request('GET','/community/api/collections/mentions/records'),404)
                self.assertEqual(request('GET','/community/api/realtime'),401)
                self.assertEqual(request('POST','/community/api/realtime'),401)
                self.assertEqual(request('DELETE','/community/api/realtime'),404)
                self.assertEqual([request('POST') for _ in range(6)], [401] * 6)
                self.assertEqual(request('POST'), 429, 'posting quota must still be enforced')
                self.assertEqual(request('GET'), 401, 'posts must not consume read quota')
                self.assertEqual(request('DELETE'), 404, 'method allowlist must remain closed')
                for collection in ('threads','thread_replies','moderator_requests'):
                    path='/community/api/collections/'+collection+'/records'
                    self.assertEqual(request('GET',path),401,'named public read path must reach the backend')
                    self.assertEqual(request('DELETE',path),404)
                    self.assertEqual(request('GET',path+'/123456789012345'),401)
                self.assertEqual(request('GET', '/community/api/settings'), 404)
                self.assertIn('zone=retui_reads:1m rate=240r/m;', source)
                self.assertIn('zone=retui_posts:1m rate=20r/m;', source)
                timing = (root/'timing.log').read_text()
                self.assertIn('operation=messages method=GET',timing)
                self.assertIn('operation=realtime',timing)
                for private in ('127.0.0.1','/community/','123456789012345'):
                    self.assertNotIn(private,timing)
            finally:
                if process is not None:
                    process.terminate()
                    process.wait(timeout=5)
                    assert process.stderr is not None
                    process.stderr.close()
                upstream.shutdown()
                upstream.server_close()
                thread.join(timeout=5)


if __name__ == '__main__':
    unittest.main(verbosity=2)
