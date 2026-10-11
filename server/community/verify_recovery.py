"""Reopen the newest selective snapshot in an isolated private instance, never over live data."""
import contextlib, os, shutil, socket, sqlite3, subprocess, tempfile, time, urllib.request
from pathlib import Path
from operations import DATA, RECOVERY, NAME, issued
from selective_backup import PERSISTENT

def verify():
    if os.geteuid() != 0: raise RuntimeError('Root-only recovery verification')
    files = [p for p in RECOVERY.iterdir() if NAME.fullmatch(p.name) and p.is_file() and not p.is_symlink()]
    source = max(files, key=issued)
    if source.stat().st_mode & 0o077 or time.time()-issued(source)>7200: raise RuntimeError('Snapshot is not private and fresh')
    with contextlib.closing(sqlite3.connect(source.as_uri()+'?mode=ro', uri=True)) as db, contextlib.closing(sqlite3.connect(DATA.as_uri()+'?mode=ro', uri=True)) as live:
        if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok' or db.execute('PRAGMA foreign_key_check').fetchall(): raise RuntimeError('Snapshot failed integrity checks')
        tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")]
        for table in tables:
            if table not in PERSISTENT and db.execute('SELECT count(*) FROM "'+table.replace('"','""')+'"').fetchone()[0]: raise RuntimeError('Transient data retained')
        for table in ('members','threads','thread_replies'):
            if db.execute('SELECT id FROM '+table+' ORDER BY id').fetchall() != live.execute('SELECT id FROM '+table+' ORDER BY id').fetchall(): raise RuntimeError('Persistent identities differ')
    with tempfile.TemporaryDirectory(prefix='retui-selective-restore-', dir=Path(__file__).resolve().parent) as work:
        shutil.copy2(source, Path(work)/'data.db')
        with socket.socket() as probe:
            probe.bind(('127.0.0.1',0)); port=probe.getsockname()[1]
        process=subprocess.Popen(['/opt/retui-community/pocketbase','serve','--dir='+work,'--http=127.0.0.1:'+str(port),
            '--hooksDir=/opt/retui-community/pb_hooks','--migrationsDir=/opt/retui-community/pb_migrations','--automigrate=false','--hooksWatch=false'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        try:
            deadline=time.monotonic()+10
            while time.monotonic()<deadline:
                if process.poll() is not None: raise RuntimeError('Recovery instance exited')
                try:
                    with urllib.request.urlopen('http://127.0.0.1:'+str(port)+'/api/health',timeout=1) as response:
                        if response.status==200: break
                except OSError: time.sleep(.1)
            else: raise RuntimeError('Recovery instance did not become healthy')
        finally:
            process.terminate()
            try: process.wait(timeout=5)
            except subprocess.TimeoutExpired: process.kill();process.wait(timeout=5)
    print('Verified isolated reopen, integrity, persistent identities and zero transient rows:',source.name)

if __name__=='__main__': os.umask(0o077);verify()
