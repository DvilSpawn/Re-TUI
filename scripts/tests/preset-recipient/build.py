#!/usr/bin/env python3
"""Build the independent URI-grant probe with the Android SDK; no Gradle project changes."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--sender', action='store_true', help='Build an independent, uninstrumented sender APK')
parser.add_argument('--output', type=Path, default=Path('/tmp/retui-preset-recipient'))
args = parser.parse_args()
source = Path(__file__).resolve().parent
sdk = Path(os.environ.get('ANDROID_HOME', str(Path.home() / 'Library/Android/sdk')))
java = Path(os.environ.get('JAVA_HOME') or subprocess.check_output(['/usr/libexec/java_home'], text=True).strip())
build_tools = sorted((sdk / 'build-tools').iterdir(), key=lambda p: tuple(int(x) for x in p.name.split('.')))[-1]
android = sdk / 'platforms/android-36/android.jar'
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=True)
classes = output / 'classes'
dex = output / 'dex'
classes.mkdir(exist_ok=True)
dex.mkdir(exist_ok=True)

def run(*command):
    subprocess.run([str(item) for item in command], check=True, env={**os.environ, 'JAVA_HOME': str(java)})

run(java / 'bin/javac', '-source', '8', '-target', '8', '-classpath', android, '-d', classes,
    *source.glob('src/**/*.java'))
run(build_tools / 'd8', '--min-api', '23', '--lib', android, '--output', dex, *classes.glob('**/*.class'))
unsigned = output / 'unsigned.apk'
manifest = source / 'AndroidManifest.xml'
if args.sender:
    manifest = output / 'sender-AndroidManifest.xml'
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.retui.test.presetsender" android:versionCode="1" android:versionName="1">
    <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36" />
    <queries><provider android:authorities="dev.retui.test.presetrecipient.read" /></queries>
    <application android:label="Re:TUI independent preset sender" android:allowBackup="false" android:debuggable="true">
        <provider android:name="dev.retui.test.presetrecipient.SenderProvider" android:authorities="dev.retui.test.presetsender.cycle" android:exported="true" />
        <provider android:name="dev.retui.test.presetrecipient.ShareProvider" android:authorities="dev.retui.test.presetsender.FILE_PROVIDER" android:exported="false" android:grantUriPermissions="true" />
    </application></manifest>''')
run(build_tools / 'aapt2', 'link', '-I', android, '--manifest', manifest, '-o', unsigned)
with zipfile.ZipFile(unsigned, 'a') as archive:
    archive.write(dex / 'classes.dex', 'classes.dex')
run(build_tools / 'zipalign', '-f', '4', unsigned, output / 'aligned.apk')
keystore = output / 'probe.keystore'
if not keystore.exists():
    run(java / 'bin/keytool', '-genkeypair', '-keystore', keystore, '-storepass', 'android', '-keypass', 'android',
        '-alias', 'probe', '-dname', 'CN=ReTUI Test Probe', '-keyalg', 'RSA', '-validity', '3650')
apk = output / 'preset-recipient.apk'
run(build_tools / 'apksigner', 'sign', '--ks', keystore, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
    '--ks-key-alias', 'probe', '--out', apk, output / 'aligned.apk')
run(build_tools / 'apksigner', 'verify', apk)
print(str(apk))
print('SHA256', hashlib.sha256(apk.read_bytes()).hexdigest())
