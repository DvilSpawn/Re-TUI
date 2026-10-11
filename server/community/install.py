"""One-time additive VPS install. Run as root from /opt/retui-community.
Refuses existing data/service; use reviewed migrations for subsequent upgrades.
"""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import urllib.request
import zipfile

ROOT = Path('/opt/retui-community')
DATA = Path('/var/lib/retui-community')
CONFIG = Path('/etc/retui-community')
UNIT = Path('/etc/systemd/system/retui-community.service')
SITE = Path('/etc/nginx/sites-available/irc.kokodoko.cloud')
SHA256 = '1dbea1b01eb3dfe64953513859d01e759c96650c4e3264fa4eecd54b769382e4'
VERSION = '0.40.5'

if __name__ == '__main__':
    assert os.geteuid() == 0 and Path.cwd() == ROOT
    assert not DATA.exists() and not CONFIG.exists() and not UNIT.exists(), 'already installed; refusing overwrite'
    before = SITE.read_text()
    assert 'listen 80;' in before and 'listen 443' not in before and 'server_name irc.kokodoko.cloud;' in before
    assert Path('/etc/letsencrypt/live/irc.kokodoko.cloud/fullchain.pem').is_file()
    probe = socket.socket(); probe.bind(('127.0.0.1',8095)); probe.close()
    os.umask(0o077)
    evidence = Path('/root/retui-community-install'); evidence.mkdir(mode=0o700)
    for name, command in [('services-before.txt',['systemctl','show','nginx','docker','wings','valheim','ergo','mariadb','-p','Id','-p','MainPID','-p','ActiveState']),('listeners-before.txt',['ss','-lntup'])]:
        (evidence/name).write_bytes(subprocess.check_output(command))
    archive = ROOT / ('pocketbase_'+VERSION+'_linux_amd64.zip')
    if not archive.exists():
        archive.write_bytes(urllib.request.urlopen('https://github.com/pocketbase/pocketbase/releases/download/v'+VERSION+'/'+archive.name).read())
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == SHA256
    with zipfile.ZipFile(archive) as z: z.extract('pocketbase',ROOT)
    (ROOT/'pocketbase').chmod(0o755)
    subprocess.run(['useradd','--system','--user-group','--home-dir',str(DATA),'--shell','/usr/sbin/nologin','retui-community'],check=True)
    DATA.mkdir(mode=0o700); CONFIG.mkdir(mode=0o700)
    subprocess.run(['chown','retui-community:retui-community',str(DATA)],check=True)
    # The service can read source/binary but cannot modify either.
    for path in ROOT.rglob('*'):
        path.chmod(0o755 if path.is_dir() or path.name=='pocketbase' else 0o644)
    ROOT.chmod(0o755)
    password = secrets.token_urlsafe(48)
    (CONFIG/'admin.json').write_text(json.dumps({'identity':'admin@localhost.invalid','password':password}))
    (CONFIG/'admin.json').chmod(0o600)
    env = dict(os.environ,RETUI_BOOTSTRAP_PASSWORD=password)
    subprocess.run(['runuser','-u','retui-community','--',str(ROOT/'pocketbase'),'migrate','up','--dir='+str(DATA/'data'),
                    '--migrationsDir='+str(ROOT/'pb_migrations'),'--hooksDir='+str(ROOT/'pb_hooks'),'--automigrate=false'],
                   env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    shutil.copyfile(ROOT/'retui-community.service',UNIT); UNIT.chmod(0o644)
    subprocess.run(['systemctl','daemon-reload'],check=True)
    subprocess.run(['systemctl','enable','--now','retui-community'],check=True)
    import time
    for attempt in range(100):
        try:
            with urllib.request.urlopen('http://127.0.0.1:8095/api/health',timeout=2) as response:
                assert response.status == 200
                break
        except OSError:
            time.sleep(.1)
    else:
        raise RuntimeError('feed service did not become healthy; public ingress remains closed')
    # Ensure moderation/authorization tests passed before enabling public signup.
    subprocess.run(['python3',str(ROOT/'test_backend.py'),'--base','http://127.0.0.1:8095',
                    '--admin-file',str(CONFIG/'admin.json'),'--data-dir',str(DATA/'data'),
                    '--restore-binary',str(ROOT/'pocketbase')],check=True)
    (evidence/'nginx-site-before.conf').write_text(before)
    shutil.copyfile(ROOT/'nginx-proxy.conf','/etc/nginx/snippets/retui-community-proxy.conf')
    Path('/etc/nginx/snippets/retui-community-proxy.conf').chmod(0o644)
    SITE.write_text(before+'\n'+(ROOT/'nginx-community.conf').read_text())
    try:
        subprocess.run(['nginx','-t'],check=True)
    except Exception:
        SITE.write_text(before)
        raise
    subprocess.run(['systemctl','reload','nginx'],check=True)
    print('Installed PocketBase '+VERSION+' on 127.0.0.1:8095; HTTPS allowlist enabled.')
