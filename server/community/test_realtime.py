"""Authenticated pointer-only realtime against pinned PocketBase. Never prints credentials."""
import http.client
import json
import queue
import socket
import threading
import urllib.parse
import uuid
import test_backend as backend


class Stream:
    def __init__(self, base, token):
        url = urllib.parse.urlsplit(base)
        cls = http.client.HTTPSConnection if url.scheme == 'https' else http.client.HTTPConnection
        self.connection = cls(url.hostname, url.port, timeout=10)
        self.connection.request('GET', url.path + '/api/realtime', headers={'Authorization': token})
        self.response = self.connection.getresponse()
        self.status = self.response.status
        self.events = queue.Queue()
        self.socket = self.connection.sock or getattr(getattr(self.response.fp, 'raw', None), '_sock', None)
        if self.status == 200:
            self.thread = threading.Thread(target=self.read, daemon=True)
            self.thread.start()

    def read(self):
        event, data = '', []
        try:
            while True:
                line = self.response.readline(8193)
                if not line or len(line) > 8192: break
                line = line.decode().rstrip('\r\n')
                if not line:
                    if data: self.events.put((event, json.loads('\n'.join(data))))
                    event, data = '', []
                elif line.startswith('event:'): event = line[6:].strip()
                elif line.startswith('data:'): data.append(line[5:].lstrip(' '))
        except (OSError, ValueError, http.client.HTTPException, AttributeError): pass
        finally: self.events.put(('closed', {}))

    def event(self, timeout=5):
        return self.events.get(timeout=timeout)

    def close(self):
        if self.socket:
            try: self.socket.shutdown(socket.SHUT_RDWR)
            except OSError: pass
        if hasattr(self, 'thread'): self.thread.join(timeout=2)
        self.response.close(); self.connection.close()


class Live(backend.Contract):
    def subscribe(self, token):
        stream = Stream(self.base, token)
        self.addCleanup(stream.close)
        self.assertEqual(stream.status, 200, stream.response.read().decode() if stream.status != 200 else '')
        kind, payload = stream.event()
        self.assertEqual(kind, 'PB_CONNECT')
        body = {'clientId': payload['clientId'], 'subscriptions': ['community']}
        self.assertEqual(self.call('POST', '/api/realtime', body, token)[0], 204)
        return stream, body

    def test_30_realtime_is_authenticated_and_only_public_pointer_topic_allowed(self):
        stream = Stream(self.base, '')
        self.assertEqual(stream.status, 401); stream.close()
        h,p,a = self.signup(); token = self.login(h,p)
        stream, body = self.subscribe(token)
        for topic in ('messages/*', 'members/*', 'reports/*', 'moderator_requests/*', 'community?options={}','other'):
            self.assertEqual(self.call('POST','/api/realtime',dict(body,subscriptions=[topic]),token)[0],400)
        _,_,other = self.signup()
        self.assertEqual(self.call('POST','/api/realtime',dict(body,subscriptions=['community','community']),token)[0],400)

    def test_31_post_arrives_live_and_delete_has_no_retained_body(self):
        h,p,a = self.signup(); token = self.login(h,p)
        stream,_ = self.subscribe(token)
        payload = {'author':a['id'],'body':'live private fixture body','client_id':str(uuid.uuid4())}
        status, record = self.call('POST','/api/collections/messages/records',payload,token)
        self.assertEqual(status,200)
        kind,event = stream.event()
        self.assertEqual(kind,'community')
        self.assertEqual(event,{'action':'create','collection':'messages','id':record['id']})
        self.assertEqual(self.call('DELETE','/api/collections/messages/records/'+record['id'],token=self.admin,admin=True)[0],204)
        self.assertEqual(stream.event(),('community',{'action':'invalidate'}))

    def test_32_revoked_connection_cannot_receive_new_events(self):
        h,p,a = self.signup(); token = self.login(h,p)
        stream,_ = self.subscribe(token)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'active':False},self.admin,True)[0],200)
        h2,p2,b = self.signup(); other = self.login(h2,p2)
        self.assertEqual(self.call('POST','/api/collections/messages/records',{'author':b['id'],'body':'revocation fixture','client_id':str(uuid.uuid4())},other)[0],200)
        self.assertEqual(stream.event(),('closed',{}))

    def test_33_password_rotation_invalidates_live_token(self):
        import secrets
        h,p,a = self.signup(); token = self.login(h,p)
        stream,_ = self.subscribe(token)
        password=secrets.token_urlsafe(30)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'password':password,'passwordConfirm':password},self.admin,True)[0],200)
        fresh=self.login(h,password)
        self.assertEqual(self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'rotated token fixture','client_id':str(uuid.uuid4())},fresh)[0],200)
        self.assertEqual(stream.event(),('closed',{}))

    def test_34_recovery_snapshot_reopens_with_accounts_and_forums_only(self):
        import os, shutil, subprocess, tempfile, time
        from pathlib import Path
        from selective_backup import snapshot
        if not self.binary: self.skipTest('local disposable restore only')
        h,p,a=self.signup(); token=self.login(h,p)
        _,root=self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':'Recovery fixture','body':'persistent fixture','client_id':str(uuid.uuid4())},token)
        self.assertEqual(self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'transient fixture','client_id':str(uuid.uuid4())},token)[0],200)
        with tempfile.TemporaryDirectory(prefix='community-restore-') as tmp:
            directory=Path(tmp); directory.chmod(0o700); data=directory/'data'; data.mkdir(mode=0o700)
            snapshot(Path(self.data_dir)/'data.db',data/'data.db')
            log=open(directory/'restore.log','w+')
            process=subprocess.Popen([self.binary,'serve','--http=127.0.0.1:18096','--dir='+str(data),'--hooksDir='+str(backend.ROOT/'pb_hooks'),'--migrationsDir='+str(backend.ROOT/'pb_migrations'),'--automigrate=false','--hooksWatch=false'],stdout=log,stderr=log)
            previous=self.base
            try:
                self.base='http://127.0.0.1:18096'
                for _ in range(100):
                    try:
                        if self.call('GET','/api/health')[0]==200: break
                    except OSError: pass
                    if process.poll() is not None: self.fail('Disposable restore could not start')
                    time.sleep(.1)
                restored=self.login(h,p)
                self.assertEqual(self.call('GET','/api/collections/threads/records/'+root['id'],token=restored)[0],200)
                self.assertEqual(self.call('GET','/api/collections/messages/records',token=restored)[1]['items'],[])
            finally:
                self.base=previous; process.terminate(); process.wait(timeout=10); log.close()

    def test_35_locked_source_does_not_publish_record_metadata(self):
        if not self.binary: self.skipTest('local-only schema permission fixture')
        h,p,a=self.signup(); token=self.login(h,p); stream,_=self.subscribe(token)
        _,schema=self.call('GET','/api/collections/threads',token=self.admin,admin=True)
        try:
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':None},self.admin,True)[0],200)
            self.assertEqual(self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':'Locked fixture','body':'hidden metadata','client_id':str(uuid.uuid4())},token)[0],200)
            with self.assertRaises(queue.Empty): stream.event(timeout=.5)
        finally: self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':schema['viewRule']},self.admin,True)[0],200)


if __name__ == '__main__':
    backend.Contract = Live
    backend.main()
