"""Existing-service-only release gates. Preserve data; rollback code/config, never replace the live DB."""
import datetime,hashlib,json,os,shutil,sqlite3,subprocess,time,urllib.request,zipfile
from pathlib import Path
from selective_backup import snapshot

STAGE=Path(__file__).resolve().parent
ROOT=Path('/opt/retui-community');DATA=Path('/var/lib/retui-community/data')
SITE=Path('/etc/nginx/sites-available/irc.kokodoko.cloud');PROXY=Path('/etc/nginx/snippets/retui-community-proxy.conf')
SHA='1dbea1b01eb3dfe64953513859d01e759c96650c4e3264fa4eecd54b769382e4'
def run(*args):
    r=subprocess.run(args,capture_output=True,text=True)
    if r.returncode: raise RuntimeError('Command failed: '+r.stderr)
    return r.stdout.strip()
def identities():
    with sqlite3.connect((DATA/'data.db').as_uri()+'?mode=ro',uri=True) as db:
        return {n:[r[0] for r in db.execute('SELECT id FROM '+n+' ORDER BY id')] for n in ('members','threads','thread_replies')}
def health():
    for _ in range(60):
        try:
            with urllib.request.urlopen('http://127.0.0.1:8095/api/health',timeout=2) as r:
                if r.status==200:return
        except OSError:pass
        time.sleep(.2)
    raise RuntimeError('Community health check failed')
def main():
    if os.geteuid()!=0:raise RuntimeError('Root-only upgrade')
    if run(str(ROOT/'pocketbase'),'--version')!='pocketbase version 0.40.4':raise RuntimeError('Unexpected deployed version')
    baseline=json.loads((STAGE/'baseline-hashes.json').read_text())
    current={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for folder in ('pb_hooks','pb_migrations') for p in (ROOT/folder).glob('*.js')}
    if current!=baseline:raise RuntimeError('Deployed source changed after inspection')
    archive=STAGE/'pocketbase-linux.zip'
    if hashlib.sha256(archive.read_bytes()).hexdigest()!=SHA:raise RuntimeError('Backend archive checksum mismatch')
    with sqlite3.connect((DATA/'data.db').as_uri()+'?mode=ro',uri=True) as db:
        if db.execute("SELECT id FROM members WHERE handle='dvil' AND active=1").fetchall()!=[('37s0d2m4zpd4ndx',)]:raise RuntimeError('Unexpected dvil identity')
    tag=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    rollback=Path('/root')/('retui-community-gates-code-'+tag);rollback.mkdir(mode=0o700)
    for folder in ('pb_hooks','pb_migrations'):shutil.copytree(ROOT/folder,rollback/folder)
    shutil.copy2(ROOT/'pocketbase',rollback/'pocketbase')
    shutil.copy2(SITE,rollback/'site.conf');shutil.copy2(PROXY,rollback/'proxy.conf')
    recovery=Path('/var/lib/retui-community/recovery');recovery.mkdir(mode=0o700,exist_ok=True)
    snapshot(DATA/'data.db',recovery/('persistent-verified-'+tag+'.db'))
    prefix=SITE.read_text().split('limit_req_zone',1)[0]
    if 'listen 80;' not in prefix or '/.well-known/acme-challenge/' not in prefix:raise RuntimeError('Unexpected ACME configuration')
    changed=False
    try:
        run('systemctl','stop','retui-community');changed=True
        before=identities()
        with zipfile.ZipFile(archive) as z:(ROOT/'pocketbase').write_bytes(z.read('pocketbase'))
        (ROOT/'pocketbase').chmod(0o755)
        for folder in ('pb_hooks','pb_migrations'):
            for p in (STAGE/folder).glob('*.js'):shutil.copy2(p,ROOT/folder/p.name);(ROOT/folder/p.name).chmod(0o644)
        for name in ('operations.py','selective_backup.py'):shutil.copy2(STAGE/name,ROOT/name);(ROOT/name).chmod(0o644)
        shutil.copytree(STAGE/'public',ROOT/'public',dirs_exist_ok=True)
        (ROOT/'public').chmod(0o755)
        for p in (ROOT/'public').iterdir():p.chmod(0o644)
        shutil.copy2(STAGE/'nginx-proxy.conf',PROXY);PROXY.chmod(0o644)
        SITE.write_text(prefix+(STAGE/'nginx-community.conf').read_text());SITE.chmod(0o644)
        run('nginx','-t')
        run('runuser','-u','retui-community','--',str(ROOT/'pocketbase'),'migrate','up','--dir='+str(DATA),
            '--hooksDir='+str(ROOT/'pb_hooks'),'--migrationsDir='+str(ROOT/'pb_migrations'),'--automigrate=false','--hooksWatch=false')
        run('systemctl','start','retui-community');health()
        if before!=identities():raise RuntimeError('Persistent identities changed')
        run('systemctl','reload','nginx')
        for name in ('retui-community-backup.service','retui-community-backup.timer','retui-community-monitor.service','retui-community-monitor.timer'):
            p=Path('/etc/systemd/system')/name;shutil.copy2(STAGE/name,p);p.chmod(0o644)
        run('systemctl','daemon-reload');run('systemctl','start','retui-community-backup.service')
        run('systemctl','start','retui-community-monitor.service')
        run('systemctl','enable','--now','retui-community-backup.timer','retui-community-monitor.timer')
        print('PocketBase 0.40.5 and release gates deployed; dvil identity and persistent forum IDs preserved; recovery and health timers active.')
        print('Code-only rollback:',rollback)
    except Exception:
        if changed:
            run('systemctl','stop','retui-community')
            shutil.copy2(rollback/'pocketbase',ROOT/'pocketbase')
            for folder in ('pb_hooks','pb_migrations'):
                for p in (ROOT/folder).glob('*.js'):
                    if not (rollback/folder/p.name).exists():p.unlink()
                for p in (rollback/folder).glob('*.js'):shutil.copy2(p,ROOT/folder/p.name)
            shutil.copy2(rollback/'site.conf',SITE);shutil.copy2(rollback/'proxy.conf',PROXY)
            run('systemctl','start','retui-community');health();run('nginx','-t');run('systemctl','reload','nginx')
        raise
if __name__=='__main__':os.umask(0o077);main()
