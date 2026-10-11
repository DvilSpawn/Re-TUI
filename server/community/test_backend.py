"""Real PocketBase contract tests; credentials stay inside the process."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent

class Contract(unittest.TestCase):
    base = ''
    data_dir = ''
    binary = ''
    admin_base = ''
    admin = ''
    users = []
    fixtures = []

    pace = {}

    def call(self, method, path, data=None, token='', admin=False, flood=False):
        if self.base.startswith('https:') and not admin and not flood:
            collection = path.split('/')[3] if path.startswith('/api/collections/') else ''
            if method == 'POST' and collection == 'members' and path.split('?')[0].endswith('/records'):
                key, interval = 'signup', 13
            elif method == 'POST' and collection in ('reports','moderator_requests'):
                key, interval = 'private-posts', 13
            elif method == 'POST':
                key, interval = 'public-posts', 3.1
            else:
                key, interval = 'reads', .26
            time.sleep(max(0, self.pace.get(key,0) + interval - time.monotonic()))
            self.pace[key] = time.monotonic()
        req = urllib.request.Request((self.admin_base if admin else self.base) + path,
              data=None if data is None else json.dumps(data).encode(), method=method,
              headers={'Content-Type': 'application/json', 'Authorization': token,
                       'X-Real-IP': '198.51.100.7' if flood else '127.0.0.1'})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                b = r.read()
                result = json.loads(b) if b else {}
                if method == 'POST' and r.status == 200 and '/records' in path and result.get('id'):
                    collection = path.split('/')[3]
                    self.fixtures.append((collection,result['id']))
                    journal = os.environ.get('RETUI_TEST_JOURNAL')
                    if journal:
                        Path(journal).write_text(json.dumps(self.fixtures))
                        Path(journal).chmod(0o600)
                return r.status, result
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read() or b'{}')

    def signup(self, handle=None):
        handle = handle or 't_' + secrets.token_hex(6)
        password = secrets.token_urlsafe(30)
        s, r = self.call('POST', '/api/collections/members/records',
                         {'handle': handle, 'password': password, 'passwordConfirm': password})
        self.assertEqual(s, 200, 'public signup must succeed: ' + json.dumps(r.get('data', {})))
        self.users.append(r['id'])
        return handle, password, r

    def test_01_open_registration_and_handle_login(self):
        h, p, r = self.signup()
        self.assertEqual(r['handle'], h)
        self.assertTrue(r['active'])
        s, a = self.call('POST', '/api/collections/members/auth-with-password', {'identity': h, 'password': p})
        self.assertEqual(s, 200)
        self.assertEqual(a['record']['id'], r['id'])
        self.assertTrue(a['token'])

    def login(self, h, p):
        s, a = self.call('POST', '/api/collections/members/auth-with-password', {'identity': h, 'password': p})
        self.assertEqual(s, 200)
        self.assertEqual(self.call('POST','/api/community/policy',{'version':'2026-10-10'},a['token'])[0],200)
        return a['token']

    def test_02_plain_post_ownership_and_idempotency(self):
        h, p, a = self.signup()
        _, _, b = self.signup()
        t = self.login(h, p)
        payload = {'author': a['id'], 'body': 'settings; reboot\nexit <b>literal</b>', 'client_id': str(uuid.uuid4())}
        s, msg = self.call('POST', '/api/collections/messages/records', payload, t)
        self.assertEqual(s, 200, 'native message creation')
        self.assertEqual(msg['body'], payload['body'])
        self.assertEqual(self.call('GET','/api/collections/messages/records/'+msg['id'],token=t)[0],200)
        self.assertEqual(msg['author_handle'], h)
        self.assertTrue(msg['created']); self.assertTrue(msg['updated'])
        s, _ = self.call('POST', '/api/collections/messages/records', payload, t)
        self.assertEqual(s, 400)
        query = urllib.parse.urlencode({'filter': 'author="' + a['id'] + '" && client_id="' + payload['client_id'] + '"'})
        s, page = self.call('GET', '/api/collections/messages/records?' + query, token=t)
        self.assertEqual(s, 200); self.assertEqual([r['id'] for r in page['items']], [msg['id']])
        for changes in ({'author': b['id']}, {'author_handle': 'forged'}, {'created': '2000-01-01 00:00:00.000Z'}, {'id': '123456789012345'}):
            forged = dict(payload, client_id=str(uuid.uuid4()), **changes)
            self.assertEqual(self.call('POST', '/api/collections/messages/records', forged, t)[0], 400)
        for method in ('PATCH', 'DELETE'):
            self.assertIn(self.call(method, '/api/collections/messages/records/' + msg['id'], {} if method == 'PATCH' else None, t)[0], (403,404))
        for body in (' ', '\n\t', 'a'*501, '😀'*501):
            self.assertEqual(self.call('POST', '/api/collections/messages/records', dict(payload, body=body, client_id=str(uuid.uuid4())), t)[0], 400)
        self.assertEqual(self.call('POST', '/api/collections/messages/records', dict(payload, body='😀'*500, client_id=str(uuid.uuid4())), t)[0], 200)
        self.assertEqual(self.call('POST', '/api/collections/messages/records', dict(payload, client_id='invalid'), t)[0], 400)

    def test_03_signup_validation_and_privilege_injection(self):
        h, p, a = self.signup()
        payload = {'handle': h.upper(), 'password': p, 'passwordConfirm': p}
        self.assertEqual(self.call('POST', '/api/collections/members/records', payload)[0], 400)
        payload['handle'] = 't_' + secrets.token_hex(6)
        self.assertEqual(self.call('POST', '/api/collections/members/records', dict(payload, passwordConfirm='different'))[0], 400)
        for field, value in {'active': True, 'verified': True, 'email': 'spoof@example.com', 'role': 'admin', 'moderator': True, 'id': '123456789012345', 'tokenKey': 'forged'}.items():
            s, r = self.call('POST', '/api/collections/members/records', dict(payload, **{field: value}))
            if s == 200: self.users.append(r['id'])
            if field == 'tokenKey':
                self.assertNotIn('tokenKey', r)  # PocketBase discards this system field before request hooks.
            else:
                self.assertEqual(s, 400, field + ' must not be client controlled')
        payload['handle'] = 't_' + secrets.token_hex(6)
        for handle in ('ab', 'a'*25, 'a b', '用户abc', '../admin'):
            self.assertEqual(self.call('POST', '/api/collections/members/records', dict(payload, handle=handle))[0], 400)
        s, r = self.call('POST', '/api/collections/members/records', dict(payload, handle=payload['handle'].upper()))
        self.assertEqual(s, 200); self.users.append(r['id'])
        self.assertEqual(r['handle'], payload['handle'])

    def test_04_reports_and_moderation_ban_boundaries(self):
        h, p, a = self.signup(); h2, p2, b = self.signup()
        t = self.login(h, p); t2 = self.login(h2, p2)
        payload = {'author': a['id'], 'body': 'moderation test', 'client_id': str(uuid.uuid4())}
        s, m = self.call('POST', '/api/collections/messages/records', payload, t)
        self.assertEqual(s, 200)
        report = {'reporter': b['id'], 'message': m['id'], 'reason': 'abuse'}
        s, r = self.call('POST', '/api/collections/reports/records', report, t2)
        self.assertEqual(s, 200, 'authenticated report delivery')
        self.assertEqual(self.call('POST', '/api/collections/reports/records', report, t2)[0], 400)
        self.assertEqual(self.call('POST', '/api/collections/reports/records', dict(report, reporter=a['id']), t2)[0], 400)
        for reason in (' ', 'a'*501):
            self.assertEqual(self.call('POST', '/api/collections/reports/records', dict(report, reporter=a['id'], reason=reason), t)[0], 400)
        for collection in ('members', 'reports'):
            self.assertIn(self.call('GET', '/api/collections/' + collection + '/records', token=t)[0], (403,404))
        self.assertIn(self.call('PATCH', '/api/collections/members/records/' + a['id'], {'handle':'other','active': True}, t)[0], (403,404))
        s, stored = self.call('GET', '/api/collections/reports/records/' + r['id'], token=self.admin, admin=True)
        self.assertEqual(s, 200); self.assertEqual(stored['reason'], 'abuse')
        self.assertEqual(self.call('PATCH', '/api/collections/members/records/' + a['id'], {'active': False}, self.admin, True)[0], 200)
        self.assertIn(self.call('POST', '/api/collections/members/auth-with-password', {'identity': h,'password':p})[0], (400,403))
        s, page = self.call('GET', '/api/collections/messages/records', token=t)
        self.assertTrue(s in (401,403,404) or (s == 200 and page['items'] == []))
        self.assertIn(self.call('GET', '/api/collections/messages/records/' + m['id'], token=t)[0], (401,403,404))
        self.assertIn(self.call('POST', '/api/collections/messages/records', dict(payload, client_id=str(uuid.uuid4())), t)[0], (400,401,403))
        for invalid in ('', t[:-10]+'invalid'):
            s, page = self.call('GET', '/api/collections/messages/records', token=invalid)
            self.assertTrue(s in (401,403,404) or (s == 200 and page['items'] == []))
        self.assertEqual(self.call('DELETE', '/api/collections/messages/records/' + m['id'], token=self.admin, admin=True)[0], 204)
        self.assertEqual(self.call('GET', '/api/collections/messages/records/' + m['id'], token=t2)[0], 404)
        s, saved = self.call('GET', '/api/collections/reports/records/' + r['id'], token=self.admin, admin=True)
        self.assertEqual(s, 200)
        self.assertEqual(saved['snapshot_body'], 'moderation test')
        self.assertEqual(saved['source_message_id'], m['id'])
        self.assertEqual(saved['message'], '')
        # Retained uniqueness must not collide when multiple reporters are deleted.
        s, own = self.call('POST','/api/collections/messages/records',dict(payload,author=b['id'],client_id=str(uuid.uuid4())),t2)
        self.assertEqual(s,200)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'active':True},self.admin,True)[0],200)
        retained=[]
        for reporter,token in ((a['id'],t),(b['id'],t2)):
            s, rr=self.call('POST','/api/collections/reports/records',{'reporter':reporter,'message':own['id'],'reason':'retain'},token)
            self.assertEqual(s,200); retained.append(rr['id'])
        for member in (a,b):
            self.assertEqual(self.call('DELETE','/api/collections/members/records/'+member['id'],token=self.admin,admin=True)[0],204)
            self.users.remove(member['id'])
        for rid in retained:
            self.assertEqual(self.call('GET','/api/collections/reports/records/'+rid,token=self.admin,admin=True)[0],200)

    def test_05_retention_job_and_cursor_order(self):
        h, p, a = self.signup(); t = self.login(h,p)
        records = []
        for text in ('old', 'current1', 'current2'):
            s, m = self.call('POST','/api/collections/messages/records', {'author':a['id'],'body':text,'client_id':str(uuid.uuid4())}, t)
            self.assertEqual(s,200); records.append(m)
        import sqlite3
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE messages SET created=? WHERE id=? AND author=?', ('2000-01-01 00:00:00.000Z', records[0]['id'],a['id']))
        # Expiry must be enforced before the physical deletion job, even for superusers.
        self.assertEqual(self.call('GET','/api/collections/messages/records/'+records[0]['id'],token=t)[0],404)
        self.assertEqual(self.call('GET','/api/collections/messages/records/'+records[0]['id'],token=self.admin,admin=True)[0],404)
        query = urllib.parse.urlencode({'filter':'id="'+records[0]['id']+'" || author="'+a['id']+'"'})
        for token, privileged in ((t,False),(self.admin,True)):
            s, page = self.call('GET','/api/collections/messages/records?'+query,token=token,admin=privileged)
            self.assertEqual(s,200)
            self.assertNotIn(records[0]['id'],[r['id'] for r in page['items']])
        s, jobs = self.call('GET','/api/crons',token=self.admin,admin=True)
        self.assertEqual(s,200); self.assertIn('community_retention', [j['id'] for j in jobs])
        self.assertEqual(self.call('POST','/api/crons/community_retention', {},self.admin,True)[0],204)
        for _ in range(50):
            if self.call('GET','/api/collections/messages/records/'+records[0]['id'],token=t)[0] == 404: break
            time.sleep(.1)
        self.assertEqual(self.call('GET','/api/collections/messages/records/'+records[0]['id'],token=t)[0],404)
        query = urllib.parse.urlencode({'sort':'-created,-id','perPage':50,'page':1,'filter':'author="'+a['id']+'"'})
        s, page = self.call('GET','/api/collections/messages/records?'+query,token=t)
        self.assertEqual(s,200,str(page.get('message',''))); self.assertEqual(len(page['items']),2)
        self.assertEqual([(r['created'],r['id']) for r in page['items']], sorted([(r['created'],r['id']) for r in page['items']],reverse=True))
        self.assertNotIn('author',page['items'][0].get('expand',{}))
        self.assertEqual(self.call('GET','/api/collections/messages/records?perPage=100',token=t)[0],400)
        cursor = page['items'][0]
        filt = '(created < "'+cursor['created']+'") || (created = "'+cursor['created']+'" && id < "'+cursor['id']+'")'
        query = urllib.parse.urlencode({'sort':'-created,-id','filter':'author="'+a['id']+'" && ('+filt+')'})
        s, older = self.call('GET','/api/collections/messages/records?'+query,token=t)
        self.assertEqual(s,200); self.assertEqual([r['id'] for r in older['items']], [page['items'][1]['id']])

    def test_06_scheduled_whole_database_backups_disabled(self):
        s, settings = self.call('GET','/api/settings',token=self.admin,admin=True)
        self.assertEqual(s,200)
        self.assertEqual(settings['backups']['cron'], '')

    def test_06b_expired_revoked_and_unauthenticated_reads(self):
        h,p,a = self.signup(); t = self.login(h,p)
        s, short = self.call('POST','/api/collections/members/impersonate/'+a['id'],{'duration':1},self.admin,True)
        self.assertEqual(s,200)
        time.sleep(2)
        self.assertEqual(self.call('GET','/api/collections/messages/records',token=short['token'])[0],401)
        self.assertEqual(self.call('GET','/api/collections/messages/records')[0],401)
        replacement=secrets.token_urlsafe(30)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'], {'password':replacement,'passwordConfirm':replacement},self.admin,True)[0],200)
        self.assertEqual(self.call('GET','/api/collections/messages/records',token=t)[0],401)

    def test_99_registration_flood_and_rate_policy(self):
        s, settings = self.call('GET','/api/settings',token=self.admin,admin=True)
        self.assertEqual(s,200); self.assertTrue(settings['rateLimits']['enabled'])
        rules = {r['label']:r for r in settings['rateLimits']['rules']}
        self.assertEqual(rules['POST /api/collections/members/records']['maxRequests'],5)
        self.assertEqual(rules['messages:create']['maxRequests'],20)
        self.assertEqual(rules['reports:create']['maxRequests'],5)
        statuses = []
        for _ in range(10):
            statuses.append(self.call('POST','/api/collections/members/records',{}, flood=True)[0])
        self.assertIn(429,statuses)

    def test_08_persistent_threads_and_replies(self):
        h,p,a = self.signup(); h2,p2,b = self.signup()
        t = self.login(h,p); t2 = self.login(h2,p2)
        path = '/api/collections/threads/records'
        payload = {'author':a['id'],'channel':'bugs','title':'literal title','body':'root; exit','client_id':str(uuid.uuid4())}
        s, root = self.call('POST',path,payload,t)
        self.assertEqual(s,200)
        self.assertEqual(root['author_handle'],h)
        self.assertEqual(self.call('POST',path,payload,t)[0],400)
        for changes in ({'channel':'general'},{'channel':'mods'},{'title':' '},{'title':'a'*121},{'body':'a'*501},{'author':b['id']},{'author_handle':'spoof'},{'created':'2099-01-01'},{'unknown':1}):
            self.assertEqual(self.call('POST',path,dict(payload,client_id=str(uuid.uuid4()),**changes),t)[0],400)
        for channel in ('features','presets'):
            self.assertEqual(self.call('POST',path,dict(payload,channel=channel,client_id=str(uuid.uuid4())),t)[0],200)
        rp = '/api/collections/thread_replies/records'
        reply = {'author':b['id'],'thread':root['id'],'body':'reply literal','client_id':str(uuid.uuid4())}
        s,r = self.call('POST',rp,reply,t2)
        self.assertEqual(s,200); self.assertEqual(r['author_handle'],h2)
        self.assertEqual(self.call('POST',rp,reply,t2)[0],400)
        self.assertEqual(self.call('POST',rp,dict(reply,author=a['id'],client_id=str(uuid.uuid4())),t2)[0],400)
        self.assertEqual(self.call('POST',rp,dict(reply,thread='',client_id=str(uuid.uuid4())),t2)[0],400)
        for collection,record in (('threads',root),('thread_replies',r)):
            q=urllib.parse.urlencode({'sort':'created,id','filter':'author="'+record['author']+'" && client_id="'+record['client_id']+'"'})
            s,page=self.call('GET','/api/collections/'+collection+'/records?'+q,token=t)
            self.assertEqual(s,200); self.assertEqual([x['id'] for x in page['items']],[record['id']])
            self.assertEqual(self.call('GET','/api/collections/'+collection+'/records/'+record['id'],token=t)[0],200)
            for method in ('PATCH','DELETE'):
                self.assertIn(self.call(method,'/api/collections/'+collection+'/records/'+record['id'],{} if method=='PATCH' else None,t)[0],(403,404))
        import sqlite3
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE threads SET created=? WHERE id=? AND author=?',('2000-01-01 00:00:00.000Z',root['id'],a['id']))
            db.execute('UPDATE thread_replies SET created=? WHERE id=? AND author=?',('2000-01-01 00:00:00.000Z',r['id'],b['id']))
        self.assertEqual(self.call('POST','/api/crons/community_retention',{},self.admin,True)[0],204)
        self.assertEqual(self.call('GET',rp+'/'+r['id'],token=t)[0],200)
        self.assertEqual(self.call('GET',path+'/'+root['id'],token=t)[0],200)
        self.assertEqual(self.call('GET',path)[0],401)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'active':False},self.admin,True)[0],200)
        self.assertIn(self.call('GET',rp,token=t)[0],(401,403))
        self.assertIn(self.call('POST',path,dict(payload,client_id=str(uuid.uuid4())),t)[0],(400,401,403))

    def test_09_private_moderator_queue(self):
        h,p,a=self.signup(); h2,p2,b=self.signup('dvil')
        t=self.login(h,p); t2=self.login(h2,p2)
        path='/api/collections/moderator_requests/records'
        payload={'reporter':a['id'],'kind':'moderate','body':'private request'}
        s,r=self.call('POST',path,payload,t)
        self.assertEqual(s,200)
        self.assertTrue(r['expires_at'])
        for changes in ({'reporter':b['id']},{'body':' '},{'body':'a'*501},{'kind':'other'},{'expires_at':'2099-01-01'},{'created':'2099-01-01'},{'snapshot_body':'forged'},{'unknown':True}):
            self.assertEqual(self.call('POST',path,dict(payload,**changes),t)[0],400)
        self.assertEqual(self.call('GET',path,token=t)[0],403)
        self.assertEqual(self.call('GET',path+'/'+r['id'],token=t)[0],404)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+b['id'],{'moderator':True},self.admin,True)[0],200)
        # Existing tokens must use the stored role, without trusting token claims.
        s,page=self.call('GET',path,token=t2)
        self.assertEqual(s,200); self.assertIn(r['id'],[x['id'] for x in page['items']])
        self.assertEqual(self.call('GET',path+'/'+r['id'],token=t2)[0],200)
        s,m=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'queue report','client_id':str(uuid.uuid4())},t)
        self.assertEqual(s,200)
        s,report=self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':m['id'],'reason':'moderator read'},t)
        self.assertEqual(s,200)
        self.assertEqual(self.call('GET','/api/collections/reports/records/'+report['id'],token=t2)[0],200)
        self.assertEqual(self.call('GET','/api/collections/reports/records',token=t2)[0],200)
        for method in ('PATCH','DELETE'):
            self.assertIn(self.call(method,path+'/'+r['id'],{} if method=='PATCH' else None,t2)[0],(403,404))
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+b['id'],{'moderator':False},self.admin,True)[0],200)
        self.assertEqual(self.call('GET',path,token=t2)[0],403)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+b['id'],{'moderator':True,'active':False},self.admin,True)[0],200)
        self.assertIn(self.call('GET',path,token=t2)[0],(401,403))
        self.assertIn(self.call('POST',path,dict(payload,reporter=b['id']),t2)[0],(400,401,403))
        # Deleting a disposable reporter must not delete private evidence before expiry.
        self.assertEqual(self.call('DELETE','/api/collections/members/records/'+a['id'],token=self.admin,admin=True)[0],204)
        self.users.remove(a['id'])
        self.assertEqual(self.call('GET',path+'/'+r['id'],token=self.admin,admin=True)[0],200)

    def test_10_calendar_retention_and_snapshot_purge(self):
        import sqlite3
        h,p,a=self.signup(); t=self.login(h,p)
        s,m=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'snapshot survives purge','client_id':str(uuid.uuid4())},t)
        self.assertEqual(s,200)
        s,report=self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':m['id'],'reason':'evidence'},t)
        self.assertEqual(s,200)
        s,request=self.call('POST','/api/collections/moderator_requests/records',{'reporter':a['id'],'kind':'report','message':m['id'],'body':'private evidence'},t)
        self.assertEqual(s,200)
        # Only fixture rows are aged; never mutate other records.
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',('2000-01-01 00:00:00.000Z',m['id'],a['id']))
        self.assertEqual(self.call('POST','/api/crons/community_retention',{},self.admin,True)[0],204)
        for _ in range(100):
            with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
                if db.execute('SELECT count(*) FROM messages WHERE id=?',(m['id'],)).fetchone()[0]==0: break
            time.sleep(.05)
        else: self.fail('expired message SQLite row remains')
        self.assertEqual(self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':m['id'],'reason':'late'},t)[0],400)
        for collection,record in (('reports',report),('moderator_requests',request)):
            s,saved=self.call('GET','/api/collections/'+collection+'/records/'+record['id'],token=self.admin,admin=True)
            self.assertEqual(s,200); self.assertEqual(saved['snapshot_body'],m['body']); self.assertEqual(saved['source_message_id'],m['id'])
        calendar=[('2024-01-31 12:34:56.789Z','2024-07-31 12:34:56.789Z'),('2023-08-31 12:34:56.789Z','2024-02-29 12:34:56.789Z'),('2024-08-31 12:34:56.789Z','2025-02-28 12:34:56.789Z'),('2024-12-31 12:34:56.789Z','2025-06-30 12:34:56.789Z')]
        aged=[]
        for created,expected in calendar:
            s,r=self.call('POST','/api/collections/moderator_requests/records',{'reporter':a['id'],'kind':'moderate','body':'calendar fixture'},self.admin,True)
            self.assertEqual(s,200)
            with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
                db.execute('UPDATE moderator_requests SET created=? WHERE id=? AND reporter=?',(created,r['id'],a['id']))
            s,r=self.call('PATCH','/api/collections/moderator_requests/records/'+r['id'],{'body':'calendar fixture'},self.admin,True)
            self.assertEqual(s,200,json.dumps(r)); self.assertEqual(r['expires_at'],expected)
            aged.append(r['id'])
        for rid in aged:
            self.assertEqual(self.call('GET','/api/collections/moderator_requests/records/'+rid,token=self.admin,admin=True)[0],404)
        q=urllib.parse.urlencode({'filter':'reporter="'+a['id']+'" || id="'+aged[0]+'"'})
        s,page=self.call('GET','/api/collections/moderator_requests/records?'+q,token=self.admin,admin=True)
        self.assertEqual(s,200); self.assertFalse(set(aged)&{r['id'] for r in page['items']})
        self.assertEqual(self.call('POST','/api/crons/community_retention',{},self.admin,True)[0],204)
        count = len(aged)
        for _ in range(100):
            with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
                count=db.execute('SELECT count(*) FROM moderator_requests WHERE id IN (?,?,?,?)',aged).fetchone()[0]
            if count==0: break
            time.sleep(.05)
        self.assertEqual(count,0)

    def test_11_timestamp_boundaries_and_collection_id_alias(self):
        import sqlite3
        from datetime import datetime,timedelta,timezone
        h,p,a=self.signup(); t=self.login(h,p)
        records=[]
        for body in ('fresh at 23h','expired at 24h','future'):
            s,r=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':body,'client_id':str(uuid.uuid4())},t)
            self.assertEqual(s,200); records.append(r)
        now=datetime.now(timezone.utc)
        dates=[now-timedelta(hours=23),now-timedelta(hours=24),now+timedelta(hours=1)]
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            for record,date in zip(records,dates):
                stamp=date.isoformat(timespec='milliseconds').replace('T',' ').replace('+00:00','Z')
                db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',(stamp,record['id'],a['id']))
        for record,expected in zip(records,(200,404,404)):
            self.assertEqual(self.call('GET','/api/collections/messages/records/'+record['id'],token=t)[0],expected)
        s,schema=self.call('GET','/api/collections/messages',token=self.admin,admin=True)
        self.assertEqual(s,200)
        q=urllib.parse.urlencode({'filter':'author="'+a['id']+'" || id="'+records[1]['id']+'"','sort':'created,id'})
        for alias in ('messages',schema['id']):
            s,page=self.call('GET','/api/collections/'+alias+'/records?'+q,token=self.admin,admin=True)
            self.assertEqual(s,200); self.assertEqual([x['id'] for x in page['items']],[records[0]['id']])

    def test_12_relation_expansion_disabled_before_retention_queries(self):
        import sqlite3
        h,p,a=self.signup(); t=self.login(h,p)
        s,m=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'expansion source','client_id':str(uuid.uuid4())},t)
        self.assertEqual(s,200)
        s,report=self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':m['id'],'reason':'expansion evidence'},t)
        self.assertEqual(s,200)
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',('2000-01-01 00:00:00.000Z',m['id'],a['id']))
        s,response=self.call('GET','/api/collections/reports/records/'+report['id']+'?expand=message',token=self.admin,admin=True)
        # A retained snapshot is allowed, but an expired live chat relation is not.
        if s == 200:
            self.assertEqual(response['expand']['message']['id'],m['id'])
            self.assertEqual(response['expand']['message']['body'],m['body'])
            print('RED evidence: superuser direct expansion returned expired live chat before purge.')
        self.assertEqual(s,400,'superuser expansion must be rejected before related rows are loaded')
        self.assertEqual(response.get('message'),'Relation expansion is disabled.')
        self.assertNotIn('expand',response)

    def test_13_nested_reverse_alias_and_mutation_expansion_denied(self):
        import sqlite3
        h,p,a=self.signup(); t=self.login(h,p)
        messages=[]; reports=[]
        for body in ('expired expansion chat','future expansion chat'):
            s,m=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':body,'client_id':str(uuid.uuid4())},t)
            self.assertEqual(s,200); messages.append(m)
            s,r=self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':m['id'],'reason':'expansion fixture'},t)
            self.assertEqual(s,200); reports.append(r)
        s,request=self.call('POST','/api/collections/moderator_requests/records',{'reporter':a['id'],'kind':'moderate','body':'expired private expansion'},t)
        self.assertEqual(s,200)
        with sqlite3.connect(str(Path(self.data_dir)/'data.db')) as db:
            for m,date in zip(messages,('2000-01-01 00:00:00.000Z','2099-01-01 00:00:00.000Z')):
                db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',(date,m['id'],a['id']))
            db.execute('UPDATE moderator_requests SET expires_at=? WHERE id=? AND reporter=?',('2000-01-01 00:00:00.000Z',request['id'],a['id']))
            db.execute('UPDATE reports SET expires_at=? WHERE id=? AND reporter=?',('2000-01-01 00:00:00.000Z',reports[1]['id'],a['id']))
        checks=0
        for collection,rid,expands in (
            ('reports',reports[0]['id'],('message','message.author.messages_via_author','reporter.moderator_requests_via_reporter')),
            ('members',a['id'],('messages_via_author','reports_via_reporter','moderator_requests_via_reporter','reports_via_reporter.message','messages_via_author,reports_via_reporter,moderator_requests_via_reporter')),
            ('messages',messages[0]['id'],('author','author.messages_via_author')),
            ('moderator_requests',request['id'],('reporter','reporter.messages_via_author')),
            ('threads','123456789012345',('author','author.messages_via_author')),
            ('thread_replies','123456789012345',('thread.author.messages_via_author',)),
            ('_superusers','123456789012345',('unknown',))):
            s,schema=self.call('GET','/api/collections/'+collection,token=self.admin,admin=True)
            self.assertEqual(s,200)
            for alias in (collection,schema['id']):
                for suffix in ('','/'+rid):
                    for expand in expands:
                        path='/api/collections/'+alias+'/records'+suffix+'?'+urllib.parse.urlencode({'expand':expand})
                        with self.subTest(alias=alias,suffix=suffix,expand=expand):
                            s,response=self.call('GET',path,token=self.admin,admin=True)
                            self.assertEqual(s,400)
                            self.assertEqual(response,{'status':400,'message':'Relation expansion is disabled.','data':{}})
                        checks+=1
        # Repeated/empty/encoded parameters, field selection, member auth, and native
        # auth/create/update responses cannot sidestep the global pre-query guard.
        path='/api/collections/members/records/'+a['id']
        for query in ('expand=','expand=&expand=messages_via_author','%65xpand=messages_via_author','expand=messages_via_author&fields=expand'):
            with self.subTest(query=query):
                self.assertEqual(self.call('GET',path+'?'+query,token=self.admin,admin=True)[0],400)
            checks+=1
        for method,path,payload,token,privileged in (
            ('GET','/api/collections/messages/records',None,t,False),
            ('POST','/api/collections/members/auth-with-password',{'identity':h,'password':p},'',False),
            ('POST','/api/collections/messages/records',{'author':a['id'],'body':'must not be created','client_id':str(uuid.uuid4())},t,False),
            ('PATCH','/api/collections/members/records/'+a['id'],{'active':False},self.admin,True)):
            with self.subTest(method=method,path=path):
                self.assertEqual(self.call(method,path+'?expand=author',payload,token,privileged)[0],400)
            checks+=1
        self.assertEqual(self.call('GET','/api/collections/members/records/'+a['id'],token=self.admin,admin=True)[1]['active'],True)
        for m in messages:
            self.assertEqual(self.call('GET','/api/collections/messages/records/'+m['id'],token=self.admin,admin=True)[0],404)
        self.assertEqual(self.call('GET','/api/collections/moderator_requests/records/'+request['id'],token=self.admin,admin=True)[0],404)
        s,saved=self.call('GET','/api/collections/reports/records/'+reports[0]['id'],token=self.admin,admin=True)
        self.assertEqual(s,200); self.assertEqual(saved['snapshot_body'],messages[0]['body'])
        print('Expansion rejection matrix verified: '+str(checks)+' requests; retained snapshot still readable.')

    @classmethod
    def tearDownClass(cls):
        obj = cls()
        import sqlite3
        for collection,rid in reversed(cls.fixtures):
            s,_ = obj.call('DELETE','/api/collections/'+collection+'/records/'+rid,token=cls.admin,admin=True)
            if s not in (204,404): raise AssertionError('exact fixture cleanup failed: '+collection+' '+rid)
        with sqlite3.connect(str(Path(cls.data_dir)/'data.db')) as db:
            for collection,rid in cls.fixtures:
                if db.execute('SELECT count(*) FROM '+collection+' WHERE id=?',(rid,)).fetchone()[0]:
                    raise AssertionError('SQLite cleanup readback failed')
        print('Exact fixture cleanup verified: '+str(len(cls.fixtures))+' records; no remaining fixture rows.')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--binary')
    parser.add_argument('--restore-binary')
    parser.add_argument('--base')
    parser.add_argument('--data-dir')
    parser.add_argument('--admin-file')
    parser.add_argument('--admin-base', default='http://127.0.0.1:8095')
    parser.add_argument('--tests', default='*', help='Comma-separated unittest method globs')
    args = parser.parse_args()
    Contract.binary = args.binary or args.restore_binary
    log = None
    password = ''
    process = None
    with tempfile.TemporaryDirectory(prefix='community-', dir=os.environ.get('TMPDIR')) as tmp:
        if args.binary:
            password = secrets.token_urlsafe(40)
            env = dict(os.environ, RETUI_BOOTSTRAP_PASSWORD=password)
            log = open(Path(tmp) / 'server.log', 'w+')
            process = subprocess.Popen([args.binary, 'serve', '--http=127.0.0.1:18095', '--dir=' + tmp + '/data',
                '--hooksDir=' + str(ROOT/'pb_hooks'), '--migrationsDir=' + str(ROOT/'pb_migrations'), '--automigrate=false', '--hooksWatch=false'],
                env=env, stdout=log, stderr=log)
            Contract.data_dir = tmp + '/data'
            Contract.base = Contract.admin_base = 'http://127.0.0.1:18095'
            auth = {'identity': 'admin@localhost.invalid', 'password': password}
            for _ in range(100):
                try:
                    if Contract().call('GET', '/api/health')[0] == 200: break
                except OSError: pass
                if process.poll() is not None:
                    log.seek(0)
                    print(log.read().replace(password, '[redacted]'))
                    raise RuntimeError('PocketBase startup failed')
                time.sleep(.1)
        else:
            Contract.data_dir = args.data_dir
            Contract.base = args.base.rstrip('/')
            Contract.admin_base = args.admin_base.rstrip('/')
            auth = json.loads(Path(args.admin_file).read_text())
        try:
            s, r = Contract().call('POST', '/api/collections/_superusers/auth-with-password', auth, admin=True)
            if s == 200: Contract.admin = r['token']
            import fnmatch
            names=unittest.defaultTestLoader.getTestCaseNames(Contract)
            selected=[name for name in names if any(fnmatch.fnmatchcase(name,pattern) for pattern in args.tests.split(','))]
            if not selected: raise ValueError('No tests matched')
            suite = unittest.TestSuite(Contract(name) for name in selected)
            result = unittest.TextTestRunner(verbosity=2).run(suite)
            if not result.wasSuccessful() and process:
                print('Fixture backend process exit:', process.poll())
                log.flush(); log.seek(0)
                for line in log:
                    if 'ERR' in line or 'error' in line.lower() or 'panic:' in line or 'runtime.' in line or 'github.com/pocketbase/' in line:
                        print(line.rstrip().replace(password, '[redacted]'))
        finally:
            if process:
                process.terminate(); process.wait(timeout=10)
        raise SystemExit(0 if result.wasSuccessful() else 1)

if __name__ == '__main__': main()
