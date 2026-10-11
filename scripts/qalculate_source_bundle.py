#!/usr/bin/env python3
"""Package app source, pinned native/Tasker sources, and notices alongside a built APK."""
import argparse
import hashlib
import io
import json
import shutil
from pathlib import Path
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch_pinned_source(item, downloads, bundled):
    downloads.mkdir(parents=True, exist_ok=True)
    path = downloads / item['archive']
    if not path.exists():
        if bundled.is_file():
            shutil.copyfile(bundled, path)
        else:
            temporary = path.with_suffix('.partial')
            try:
                with urllib.request.urlopen(item['url'], timeout=60) as response, temporary.open('wb') as output:
                    shutil.copyfileobj(response, output)
                if sha256(temporary) != item['sha256']:
                    raise ValueError('Third-party source checksum mismatch: ' + item['archive'])
                temporary.replace(path)
            finally:
                temporary.unlink(missing_ok=True)
    if sha256(path) != item['sha256']:
        raise ValueError('Third-party source checksum mismatch: ' + item['archive'])
    return path


def verify_tasker_sources(repository, published, revision):
    prefix = 'TaskerPluginSample-' + revision + '/'
    source_dir = prefix + 'taskerpluginlibrary/src/main/java/'
    required = ['LICENSE', 'build.gradle', 'settings.gradle',
                'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
                'scripts/publish-mavencentral.gradle', 'taskerpluginlibrary/build.gradle',
                'taskerpluginlibrary/proguard-rules.pro', 'taskerpluginlibrary/src/main/AndroidManifest.xml']
    with tarfile.open(repository) as archive:
        files = {member.name: member for member in archive.getmembers() if member.isfile()}
        if any(prefix + name not in files for name in required):
            raise ValueError('Tasker source archive lacks license/build materials')
        original = {name[len(source_dir):]: archive.extractfile(member).read().replace(b'\r\n', b'\n')
                    for name, member in files.items()
                    if name.startswith(source_dir) and name.endswith(('.kt', '.java'))}
    with zipfile.ZipFile(published) as archive:
        shipped = {name: archive.read(name).replace(b'\r\n', b'\n')
                   for name in archive.namelist() if name.endswith(('.kt', '.java'))}
    if not shipped or original != shipped:
        raise ValueError('Tasker revision does not match the published source files')
    return {'matched_source_files': len(shipped), 'comparison': 'exact bytes after CRLF-to-LF normalization'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--aab', type=Path, help='Also record the matching Play Store bundle')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    sources = json.loads((ROOT / 'native/qalculate/sources.json').read_text())
    downloads = ROOT / 'app/build/qalculate/downloads'
    for item in sources:
        if sha256(downloads / item['archive']) != item['sha256']:
            raise SystemExit('Native source checksum mismatch: ' + item['archive'])
    third_party = json.loads((ROOT / 'third_party/sources.json').read_text())
    third_party_archives = []
    source_checks = {}
    for item in third_party:
        archive = fetch_pinned_source(item, ROOT / 'app/build/third-party/downloads',
                                      ROOT / 'third_party/upstream' / item['archive'])
        published = item['published_sources']
        published_archive = fetch_pinned_source(published, ROOT / 'app/build/third-party/downloads',
                                                ROOT / 'third_party/upstream' / published['archive'])
        source_checks[item['coordinate']] = verify_tasker_sources(archive, published_archive, item['revision'])
        third_party_archives.extend([archive, published_archive])
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    paths = [ROOT / name for name in names if name and (ROOT / name).is_file()
             and not name.startswith(('native/qalculate/upstream/', 'third_party/upstream/'))]
    # No ignored signing credentials, caches, APKs, or generated object files are included.
    manifest = {
        'apk': args.apk.name, 'apk_sha256': sha256(args.apk),
        'base_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'working_tree': subprocess.check_output(['git', 'status', '--short'], cwd=ROOT, text=True),
        'files': {str(p.relative_to(ROOT)): sha256(p) for p in paths},
        'native_sources': sources,
        'third_party_sources': third_party,
        'third_party_source_checks': source_checks,
    }
    if args.aab:
        manifest.update(aab=args.aab.name, aab_sha256=sha256(args.aab))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, 'w:gz') as archive:
        for path in paths:
            archive.add(path, arcname='retui-source/' + str(path.relative_to(ROOT)))
        for item in sources:
            archive.add(downloads / item['archive'], arcname='retui-source/native/qalculate/upstream/' + item['archive'])
        for path in third_party_archives:
            archive.add(path, arcname='retui-source/third_party/upstream/' + path.name)
        data = (json.dumps(manifest, indent=2) + '\n').encode()
        entry = tarfile.TarInfo('retui-source/SOURCE-MANIFEST.json')
        entry.size = len(data)
        archive.addfile(entry, io.BytesIO(data))
    print(f'{args.output}: SHA-256 {sha256(args.output)}')


if __name__ == '__main__':
    main()
