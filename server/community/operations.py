"""Root-only selective recovery rotation and privacy-safe local service monitoring."""
import argparse,datetime,json,os,re,shutil,sqlite3,subprocess,time,urllib.request
from pathlib import Path
from selective_backup import snapshot

DATA=Path('/var/lib/retui-community/data/data.db')
RECOVERY=Path('/var/lib/retui-community/recovery')
STATUS=Path('/var/lib/retui-community/operations.json')
NAME=re.compile(r'persistent-(?:verified-|final-)?\d{8}T\d{6}Z\.db')
def issued(path):
    return datetime.datetime.strptime(path.stem[-16:],'%Y%m%dT%H%M%SZ').replace(tzinfo=datetime.timezone.utc).timestamp()

def rotate(source=DATA,directory=RECOVERY,now=None):
    now=time.time() if now is None else now
    directory=Path(directory);directory.mkdir(mode=0o700,exist_ok=True)
    if directory.is_symlink() or directory.stat().st_mode & 0o077: raise ValueError('Recovery must be a private real directory')
    tag=datetime.datetime.fromtimestamp(now,datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    target=directory/('persistent-'+tag+'.db');temporary=directory/('.pending-'+tag+'.db')
    try:
        snapshot(source,temporary)
        os.link(temporary,target); temporary.unlink()
    except Exception:
        if temporary.is_file() and not temporary.is_symlink(): temporary.unlink()
        raise
    for p in directory.iterdir():
        if NAME.fullmatch(p.name) and p.is_file() and not p.is_symlink() and issued(p) < now-7*86400: p.unlink()
    # Respect account/content deletions when retaining an older recovery snapshot.
    with sqlite3.connect(Path(source).resolve().as_uri()+'?mode=ro',uri=True) as live:
        live.execute('BEGIN')
        ids={n:{r[0] for r in live.execute('SELECT id FROM '+n)} for n in ('members','threads','thread_replies')}
    for p in directory.iterdir():
        if not NAME.fullmatch(p.name) or p.is_symlink() or not p.is_file(): continue
        with sqlite3.connect(p) as db:
            for table in ('thread_replies','threads','members'):
                removed=[r[0] for r in db.execute('SELECT id FROM '+table) if r[0] not in ids[table]]
                db.executemany('DELETE FROM '+table+' WHERE id=?',[(r,) for r in removed])
            if db.execute('PRAGMA integrity_check').fetchone()[0]!='ok': raise RuntimeError('Invalid retained recovery snapshot')
            db.commit(); db.execute('VACUUM')
    return target

def check(source=DATA,directory=RECOVERY,probe=True):
    alerts=[];now=time.time()
    if probe:
        for url in ('http://127.0.0.1:8095/api/health','https://irc.kokodoko.cloud/community/api/health'):
            try:
                with urllib.request.urlopen(url,timeout=5) as r:
                    if r.status!=200: alerts.append('community_unhealthy')
            except Exception: alerts.append('community_unreachable')
    with sqlite3.connect(Path(source).resolve().as_uri()+'?mode=ro',uri=True) as db:
        db.execute('BEGIN')
        if db.execute("SELECT count(*) FROM messages WHERE created <= datetime('now','-24 hours','-3 minutes')").fetchone()[0]: alerts.append('general_retention_overdue')
        for n in ('reports','moderator_requests','presence'):
            if db.execute('SELECT count(*) FROM '+n+" WHERE expires_at <= datetime('now','-3 minutes')").fetchone()[0]: alerts.append(n+'_retention_overdue')
        mods=db.execute('SELECT handle FROM members WHERE active=1 AND moderator=1').fetchall()
        if mods!=[('dvil',)]: alerts.append('sole_moderator_missing_or_changed')
        for n in ('reports','moderator_requests'):
            if db.execute('SELECT count(*) FROM '+n+" WHERE resolved_at='' AND expires_at > datetime('now') AND created <= datetime('now','-24 hours')").fetchone()[0]: alerts.append('moderation_queue_overdue')
    files=[p for p in Path(directory).glob('persistent-*.db') if NAME.fullmatch(p.name) and p.is_file() and not p.is_symlink()]
    if not files or now-max(issued(p) for p in files)>7200: alerts.append('recovery_snapshot_overdue')
    if shutil.disk_usage(Path(source).parent).free<500_000_000: alerts.append('disk_space_low')
    return {'checked_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'healthy':not alerts,'alerts':sorted(set(alerts)),'offsite_configured':False}

if __name__=='__main__':
    if os.geteuid()!=0: raise SystemExit('Operations are root-only')
    os.umask(0o077)
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('action',choices=['backup','monitor']);args=parser.parse_args()
    if args.action=='backup':
        print('Selective recovery snapshot verified:',rotate().name)
    else:
        result=check();temporary=STATUS.with_suffix('.pending');temporary.write_text(json.dumps(result));temporary.chmod(0o640)
        import grp
        os.chown(temporary,0,grp.getgrnam('retui-community').gr_gid);os.replace(temporary,STATUS)
        print(json.dumps(result));raise SystemExit(0 if result['healthy'] else 1)
