"""External TLS, ingress allowlist and body-cap checks. No credentials."""
import ssl
import socket
import urllib.error
import urllib.request
import unittest

BASE = 'https://irc.kokodoko.cloud'

class Ingress(unittest.TestCase):
    def request(self, path, data=None, method='GET'):
        req=urllib.request.Request(BASE+path,data=data,method=method,headers={'Content-Type':'application/json'})
        try:
            with urllib.request.urlopen(req,timeout=15) as r: return r.status,r.headers,r.read()
        except urllib.error.HTTPError as e: return e.code,e.headers,e.read()

    def test_health_trusted_https(self):
        s,h,b=self.request('/community/api/health')
        self.assertEqual(s,200); self.assertIn(b'healthy',b)
        self.assertEqual(h['Cache-Control'],'no-store')

    def test_admin_and_encoded_paths_denied(self):
        for path in ('/_/','/community/_/','/community/%5f/','/community/%255f/',
                     '/community/api/collections/_superusers/auth-with-password','/community/api/settings',
                     '/community/api/backups','/community/api/crons','/community/api/batch','/community/api/sql',
                     '/community/api/collections/members/request-password-reset','/community/api/collections/members/auth-with-oauth2',
                     '/community/api/collections/messages/records/../../_superusers/auth-with-password',
                     '/community/api/collections/%5fsuperusers/records','/community//_/'):
            with self.subTest(path=path): self.assertEqual(self.request(path)[0],404)
        self.assertEqual(self.request('/community/api/collections/members/records')[0],403)
        self.assertEqual(self.request('/community/api/collections/reports/records')[0],401)
        for collection in ('threads','thread_replies','moderator_requests'):
            self.assertEqual(self.request('/community/api/collections/'+collection+'/records')[0],401)

    def test_no_unauthenticated_feed_and_body_cap(self):
        self.assertEqual(self.request('/community/api/collections/messages/records')[0],401)
        self.assertEqual(self.request('/community/api/collections/messages/records',b'{"body":"'+b'x'*9000+b'"}', 'POST')[0],413)

    def test_reads_do_not_spend_post_quota(self):
        path = '/community/api/collections/messages/records'
        # Two clients behind one NAT: five refresh requests each per 15 seconds.
        statuses = [self.request(path)[0] for _ in range(40)]
        self.assertEqual(statuses, [401] * 40)
        self.assertIn(self.request(path, b'{}', 'POST')[0], (400,401))

    def test_backend_port_not_public(self):
        with socket.socket() as s:
            s.settimeout(3)
            self.assertNotEqual(s.connect_ex(('72.61.124.153',8095)),0)

if __name__=='__main__': unittest.main(verbosity=2)
