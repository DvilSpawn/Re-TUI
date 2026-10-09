"""Exercise the upgrade with existing records, using the pinned real executable."""
import argparse
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
import unittest
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
BINARY = ''

class Upgrade(unittest.TestCase):
    def test_existing_reports_are_backfilled_without_data_loss(self):
        with tempfile.TemporaryDirectory(prefix='community-upgrade-',dir=os.environ.get('TMPDIR')) as tmp:
            path=Path(tmp); migrations=path/'migrations'; migrations.mkdir(); hooks=path/'hooks'; hooks.mkdir()
            shutil.copyfile(ROOT/'pb_migrations/1791400000_community.js',migrations/'1791400000_community.js')
            password=secrets.token_urlsafe(40)
            token=''; proc=None
            def call(method,route,body=None):
                req=urllib.request.Request('http://127.0.0.1:18097'+route,method=method,
                    data=None if body is None else json.dumps(body).encode(),headers={'Content-Type':'application/json','Authorization':token})
                with urllib.request.urlopen(req,timeout=10) as response: return json.loads(response.read() or b'{}')
            def start(log):
                process=subprocess.Popen([BINARY,'serve','--http=127.0.0.1:18097','--dir='+str(path/'data'),
                    '--hooksDir='+str(hooks),'--migrationsDir='+str(migrations),'--automigrate=false'],
                    env=dict(os.environ,RETUI_BOOTSTRAP_PASSWORD=password),stdout=log,stderr=log)
                for _ in range(100):
                    if process.poll() is not None:
                        log.flush(); log.seek(0)
                        self.fail('Upgrade startup failed: '+log.read().replace(password,'[redacted]'))
                    try:
                        call('GET','/api/health'); return process
                    except OSError: time.sleep(.1)
                process.terminate(); process.wait(timeout=10)
                self.fail('Fixture did not become healthy')
            with open(path/'server.log','w+') as log:
                try:
                    proc=start(log)
                    token=call('POST','/api/collections/_superusers/auth-with-password',{'identity':'admin@localhost.invalid','password':password})['token']
                    member=call('POST','/api/collections/members/records',{'handle':'fixture','password':password,'passwordConfirm':password,'active':True})
                    records=[]
                    for body in ('evidence one','evidence two'):
                        message=call('POST','/api/collections/messages/records',{'author':member['id'],'author_handle':'fixture','body':body,'client_id':str(uuid.uuid4())})
                        report=call('POST','/api/collections/reports/records',{'reporter':member['id'],'message':message['id'],'reason':'retain'})
                        records.append((message,report))
                    proc.terminate(); proc.wait(timeout=10); proc=None
                    for source in (ROOT/'pb_migrations').glob('*.js'): shutil.copyfile(source,migrations/source.name)
                    for source in (ROOT/'pb_hooks').glob('*.js'): shutil.copyfile(source,hooks/source.name)
                    proc=start(log)
                    for message,report in records:
                        saved=call('GET','/api/collections/reports/records/'+report['id'])
                        self.assertEqual(saved['snapshot_body'],message['body'])
                        self.assertEqual(saved['source_message_id'],message['id'])
                        self.assertEqual(saved['source_reporter_id'],member['id'])
                        self.assertTrue(saved['expires_at'])
                    settings=call('GET','/api/settings')
                    self.assertEqual(settings['backups']['cron'],'')
                    self.assertEqual(call('GET','/api/collections/messages/records')['totalItems'],2)
                finally:
                    if proc: proc.terminate(); proc.wait(timeout=10)

if __name__=='__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--binary',required=True)
    BINARY=parser.parse_args().binary
    unittest.main(argv=['test_migration.py'],verbosity=2)
