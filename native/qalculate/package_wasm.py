#!/usr/bin/env python3
"""Create a reproducible, universal .retui.calc.engine pack and site catalogue."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
VERSION = '5.8.2'
FILES = ('engine.js', 'engine.wasm', 'LICENSE.txt', 'NOTICE.txt')


def digest(value):
    return hashlib.sha256(value).hexdigest()


def package(engine_dir, output_dir, base_url):
    engine = {name: (engine_dir / name).read_bytes() for name in ('engine.js', 'engine.wasm')}
    if not engine['engine.wasm'].startswith(b'\0asm\x01\0\0\0'):
        raise ValueError('Invalid WebAssembly module')
    if b'createQalculateModule' not in engine['engine.js']:
        raise ValueError('Missing engine factory')
    # Include complete upstream licence texts rather than replacing them with links.
    source = ROOT / 'app/build/qalculate/wasm'
    licenses = []
    for directory in ('libqalculate-5.8.2', 'gmp-6.3.0', 'mpfr-4.2.2', 'libxml2-2.15.1'):
        paths = sorted((source / directory).glob('COPYING*')) + [source / directory / 'Copyright']
        for path in paths:
            if path.is_file():
                licenses.append(f'===== {directory}/{path.name} =====\n' + path.read_text(errors='replace'))
    toolchain_license = source / 'emscripten-LICENSE.txt'
    if not toolchain_license.is_file():
        raise ValueError('Emscripten licence text is required; rerun build_wasm.py')
    licenses.append('===== Emscripten runtime =====\n' + toolchain_license.read_text())
    if len(licenses) < 5:
        raise ValueError('Build upstream sources first; licence texts are required')
    engine['LICENSE.txt'] = ('\n\n'.join(licenses) + '\n').encode()
    engine['NOTICE.txt'] = (f'Re:TUI optional Qalculate engine {VERSION}\n'
        'Re:TUI adapter: GPL-3.0-or-later. libqalculate: GPL-2.0-or-later.\n'
        'Includes GMP 6.3.0, MPFR 4.2.2 and libxml2 2.15.1; see LICENSE.txt.\n'
        'No separately installed application, Google service or publisher signing key is required.\n'
        'Complete corresponding source and reproducible instructions: native/qalculate in the matching Re:TUI source release.\n').encode()
    manifest = dict(schema=1, kind='qalculate', runtimeApi=1, version=VERSION,
                    files={name: digest(engine[name]) for name in FILES})
    output_dir.mkdir(parents=True, exist_ok=True)
    pack = output_dir / f'qalculate-{VERSION}.retui.calc.engine'
    with zipfile.ZipFile(pack, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in [('manifest.json', (json.dumps(manifest, indent=2) + '\n').encode())] + [(name, engine[name]) for name in FILES]:
            entry = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data, compresslevel=9)
    catalog = dict(schema=1, version=VERSION, url=base_url.rstrip('/') + '/' + pack.name,
                   sha256=digest(pack.read_bytes()))
    (output_dir / 'catalog.json').write_text(json.dumps(catalog, indent=2) + '\n')
    (output_dir / 'SHA256SUMS').write_text(f'{catalog["sha256"]}  {pack.name}\n')
    # Read-back prevents silently publishing a truncated or mismatched pack.
    with zipfile.ZipFile(pack) as archive:
        assert set(archive.namelist()) == {'manifest.json', *FILES}
        for name in FILES:
            assert digest(archive.read(name)) == manifest['files'][name]
    print(json.dumps(dict(pack=str(pack), bytes=pack.stat().st_size, **catalog), indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--engine-dir', type=Path, default=ROOT / 'app/build/qalculate/wasm/dist')
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'app/build/qalculate/wasm/site/calculator-packs')
    parser.add_argument('--base-url', default='https://re-tui.pages.dev/calculator-packs')
    args = parser.parse_args()
    package(args.engine_dir, args.output_dir, args.base_url)


if __name__ == '__main__':
    main()
