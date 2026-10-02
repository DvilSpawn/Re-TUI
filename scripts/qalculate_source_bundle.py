#!/usr/bin/env python3
"""Package the current app source, exact native archives, and notices alongside a built APK."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent.parent


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


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
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    paths = [ROOT / name for name in names if name and (ROOT / name).is_file()]
    # No ignored signing credentials, caches, APKs, or generated object files are included.
    manifest = {
        'apk': args.apk.name, 'apk_sha256': sha256(args.apk),
        'base_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'working_tree': subprocess.check_output(['git', 'status', '--short'], cwd=ROOT, text=True),
        'files': {str(p.relative_to(ROOT)): sha256(p) for p in paths},
        'native_sources': sources,
    }
    if args.aab:
        manifest.update(aab=args.aab.name, aab_sha256=sha256(args.aab))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.output, 'w:gz') as archive:
        for path in paths:
            archive.add(path, arcname='retui-source/' + str(path.relative_to(ROOT)))
        for item in sources:
            archive.add(downloads / item['archive'], arcname='retui-source/native/qalculate/upstream/' + item['archive'])
        data = (json.dumps(manifest, indent=2) + '\n').encode()
        entry = tarfile.TarInfo('retui-source/SOURCE-MANIFEST.json')
        entry.size = len(data)
        archive.addfile(entry, io.BytesIO(data))
    print(f'{args.output}: SHA-256 {sha256(args.output)}')


if __name__ == '__main__':
    main()
