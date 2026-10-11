"""Apply the reviewed live-delivery upgrade to the existing isolated VPS service.

Run the staged script as root. Source hashes must match before touching the service.
Rollback restores code/config only, never overwrites newly accepted account data.
"""
import datetime
import hashlib
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import time
import urllib.request
from selective_backup import snapshot

STAGE=Path(__file__).resolve().parent
ROOT=Path('/opt/retui-community')
DATA=Path('/var/lib/retui-community/data')
SITE=Path('/etc/nginx/sites-available/irc.kokodoko.cloud')
PROXY=Path('/etc/nginx/snippets/retui-community-proxy.conf')
UNIT=Path('/etc/systemd/system/retui-community.service')


def run(*args):
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError('Deployment command failed: ' + result.stderr + result.stdout)
    return result.stdout
def identity():
    with sqlite3.connect((DATA/'data.db').as_uri()+'?mode=ro',uri=True) as db:
        return {table:[row[0] for row in db.execute('SELECT id FROM '+table+' ORDER BY id')] for table in ('members','threads','thread_replies')}


def main():
    if os.geteuid()!=0: raise RuntimeError('Root-only deployment')
    if run(str(ROOT/'pocketbase'),'--version').strip()!='pocketbase version 0.40.4': raise RuntimeError('Unexpected backend version')
    if run('systemctl','is-active','retui-community').strip()!='active': raise RuntimeError('Existing service is not active')
    for folder in ('pb_hooks','pb_migrations'):
        for current in (ROOT/folder).glob('*.js'):
            candidate=STAGE/folder/current.name
            if not candidate.is_file() or hashlib.sha256(candidate.read_bytes()).digest()!=hashlib.sha256(current.read_bytes()).digest():
                raise RuntimeError('Unreviewed deployed source drift: '+current.name)
    tag=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    rollback=Path('/root')/('retui-community-live-rollback-'+tag); rollback.mkdir(mode=0o700)
    before=identity()
    for folder in ('pb_hooks','pb_migrations'): shutil.copytree(ROOT/folder,rollback/folder)
    for path,name in ((SITE,'site.conf'),(PROXY,'proxy.conf'),(UNIT,'service.conf')): shutil.copy2(path,rollback/name)
    recovery=Path('/var/lib/retui-community/recovery'); recovery.mkdir(mode=0o700,exist_ok=True)
    if recovery.stat().st_mode & 0o077: raise RuntimeError('Recovery directory must be root-private')
    snapshot(DATA/'data.db',recovery/('persistent-'+tag+'.db'))
    prefix=SITE.read_text().split('limit_req_zone',1)[0]
    if 'listen 80;' not in prefix or '/.well-known/acme-challenge/' not in prefix: raise RuntimeError('Unexpected ACME configuration')
    changed=False
    try:
        run('systemctl','stop','retui-community'); changed=True
        for folder in ('pb_hooks','pb_migrations'):
            for source in (STAGE/folder).glob('*.js'):
                if source.name.startswith('._'): continue
                shutil.copy2(source,ROOT/folder/source.name); (ROOT/folder/source.name).chmod(0o644)
        shutil.copy2(STAGE/'retui-community.service',UNIT); UNIT.chmod(0o644)
        shutil.copy2(STAGE/'nginx-proxy.conf',PROXY); PROXY.chmod(0o644)
        SITE.write_text(prefix+(STAGE/'nginx-community.conf').read_text()); SITE.chmod(0o644)
        run('nginx','-t')
        run('runuser','-u','retui-community','--',str(ROOT/'pocketbase'),'migrate','up','--dir='+str(DATA),
            '--hooksDir='+str(ROOT/'pb_hooks'),'--migrationsDir='+str(ROOT/'pb_migrations'),'--automigrate=false','--hooksWatch=false')
        run('systemctl','daemon-reload'); run('systemctl','start','retui-community')
        for _ in range(100):
            try:
                with urllib.request.urlopen('http://127.0.0.1:8095/api/health',timeout=2) as response:
                    if response.status==200: break
            except OSError: pass
            time.sleep(.1)
        else: raise RuntimeError('Upgraded community did not become healthy')
        if before!=identity(): raise RuntimeError('Persistent identities changed during upgrade')
        run('systemctl','reload','nginx')
        for name in ('selective_backup.py','test_realtime.py','test_backend.py'):
            shutil.copy2(STAGE/name,ROOT/name); (ROOT/name).chmod(0o644)
        print('Live delivery deployed; persistent account/forum identities preserved; protected recovery snapshot prepared.')
        print('Code/config rollback directory: '+str(rollback))
    except Exception:
        if changed:
            run('systemctl','stop','retui-community')
            for folder in ('pb_hooks','pb_migrations'):
                for candidate in (ROOT/folder).glob('*.js'):
                    if not (rollback/folder/candidate.name).exists(): candidate.unlink()
                for original in (rollback/folder).glob('*.js'): shutil.copy2(original,ROOT/folder/original.name)
            for path,name in ((SITE,'site.conf'),(PROXY,'proxy.conf'),(UNIT,'service.conf')): shutil.copy2(rollback/name,path)
            run('systemctl','daemon-reload'); run('systemctl','start','retui-community'); run('nginx','-t'); run('systemctl','reload','nginx')
        raise


if __name__=='__main__':
    os.umask(0o077)
    main()
