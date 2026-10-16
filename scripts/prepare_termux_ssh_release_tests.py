#!/usr/bin/env python3
"""Make a separate smoke-test APK use the minified application's own Java backport runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def dex_descriptors(data):
    if len(data) < 112 or not data.startswith(b'dex\n'):
        raise ValueError('Not a supported DEX file')
    strings_count, strings_offset = struct.unpack_from('<II', data, 0x38)
    types_count, types_offset = struct.unpack_from('<II', data, 0x40)
    classes_count, classes_offset = struct.unpack_from('<II', data, 0x60)
    for count, offset, width in ((strings_count, strings_offset, 4), (types_count, types_offset, 4),
                                (classes_count, classes_offset, 32)):
        if offset + count * width > len(data):
            raise ValueError('DEX table exceeds file bounds')
    result = []
    for index in range(classes_count):
        type_index = struct.unpack_from('<I', data, classes_offset + index * 32)[0]
        if type_index >= types_count:
            raise ValueError('Invalid DEX type index')
        string_index = struct.unpack_from('<I', data, types_offset + type_index * 4)[0]
        if string_index >= strings_count:
            raise ValueError('Invalid DEX string index')
        offset = struct.unpack_from('<I', data, strings_offset + string_index * 4)[0]
        for _ in range(5):
            if offset >= len(data):
                raise ValueError('Invalid DEX string offset')
            value = data[offset]
            offset += 1
            if not value & 128:
                break
        else:
            raise ValueError('Invalid DEX string length')
        end = data.find(b'\0', offset)
        if end < 0:
            raise ValueError('Unterminated DEX descriptor')
        result.append(data[offset:end].decode('utf-8'))
    return result


def compat_classes(classes, application_classes):
    compat = [name for name in classes if name.startswith(('Lj$/', 'Ljava/util/function/'))]
    if not compat:
        return False
    if len(compat) != len(classes):
        raise ValueError('Backport classes share a DEX with app/test classes; refusing to remove it')
    missing = [name for name in compat if name.startswith('Ljava/util/function/') and name not in application_classes]
    if missing:
        raise ValueError('Application does not provide these compatibility interfaces: ' + ', '.join(missing))
    return True


def isolate_test_runtime(application, original_test, unsigned_output):
    application_classes = set()
    with zipfile.ZipFile(application) as archive:
        for entry in archive.namelist():
            if entry.endswith('.dex'):
                application_classes.update(dex_descriptors(archive.read(entry)))
    removed = []
    with zipfile.ZipFile(original_test) as source:
        for entry in source.infolist():
            if not entry.filename.endswith('.dex'):
                continue
            body = source.read(entry.filename)
            classes = dex_descriptors(body)
            if compat_classes(classes, application_classes):
                removed.append({'entry': entry.filename, 'class_count': len(classes), 'bytes': len(body),
                                'sha256': hashlib.sha256(body).hexdigest(),
                                'j_dollar_class_count': sum(name.startswith('Lj$/') for name in classes),
                                'java_function_descriptors': sorted(name for name in classes if name.startswith('Ljava/util/function/'))})
        if not removed:
            raise ValueError('No isolated compatibility DEX found; use the original Gradle-built test APK')
        removed_names = {entry['entry'] for entry in removed}
        with zipfile.ZipFile(unsigned_output, 'w') as output:
            for entry in source.infolist():
                if entry.filename in removed_names or re.fullmatch(r'META-INF/[^/]+\.(RSA|DSA|EC|SF|MF)', entry.filename, re.I):
                    continue
                output.writestr(entry, source.read(entry.filename))
    return removed


def command(args):
    return subprocess.run([str(arg) for arg in args], check=True, capture_output=True, text=True).stdout


def certificate(apksigner, apk):
    output = command([apksigner, 'verify', '--print-certs', apk])
    match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', output)
    if not match:
        raise ValueError('Could not verify APK signing certificate')
    return match.group(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--app-apk', required=True, type=Path)
    parser.add_argument('--test-apk', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--debug-keystore', type=Path, default=Path.home() / '.android/debug.keystore')
    args = parser.parse_args()
    app, original, output = (path.resolve() for path in (args.app_apk, args.test_apk, args.output))
    if len({app, original, output}) != 3:
        parser.error('Application, original test APK, and output must be different files')
    output.parent.mkdir(parents=True, exist_ok=True)
    before_app, before_test = sha256(app), sha256(original)
    apksigner, zipalign = args.build_tools / 'apksigner', args.build_tools / 'zipalign'
    app_certificate = certificate(apksigner, app)
    with tempfile.TemporaryDirectory(prefix='retui-ssh-tests-', dir=output.parent) as work:
        work = Path(work)
        unsigned, aligned, signed = (work / name for name in ('unsigned.apk', 'aligned.apk', 'signed.apk'))
        removed = isolate_test_runtime(app, original, unsigned)
        command([zipalign, '-f', '4', unsigned, aligned])
        command([apksigner, 'sign', '--ks', args.debug_keystore, '--ks-key-alias', 'androiddebugkey',
                 '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', signed, aligned])
        if certificate(apksigner, signed) != app_certificate:
            raise ValueError('Debug signing key does not match the application; build with -PtermuxSshReleaseCheck=true')
        if sha256(app) != before_app or sha256(original) != before_test:
            raise ValueError('An input APK changed during processing; rerun after the build finishes')
        os.replace(signed, output)
    report = {'application_apk': str(app), 'application_sha256': before_app,
              'original_test_apk': str(original), 'original_test_sha256': before_test,
              'processed_test_apk': str(output), 'processed_test_sha256': sha256(output),
              'signing_certificate_sha256': app_certificate, 'removed_compatibility_dex': removed,
              'application_modified': False, 'original_test_modified': False,
              'reason': 'Use the application L8 runtime; separately minimized test L8 classes otherwise shadow it'}
    report_path = output.with_suffix('.json')
    report_path.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'output': str(output), 'report': str(report_path), 'removed_classes': sum(entry['class_count'] for entry in removed)}))


if __name__ == '__main__':
    main()
