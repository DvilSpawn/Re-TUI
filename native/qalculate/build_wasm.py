#!/usr/bin/env python3
"""Build the portable Qalculate 5.8.2 engine with pinned Emscripten 3.1.67."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import sys
import tarfile

import build as native

HERE = Path(__file__).resolve().parent
BUILD = native.BUILD / 'wasm'
EMSCRIPTEN_VERSION = '3.1.67'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--emsdk', type=Path, default=Path.home() / '.cache/retui-emsdk')
    parser.add_argument('--jobs', type=int, default=min(os.cpu_count() or 2, 8))
    args = parser.parse_args()
    emroot = args.emsdk.resolve() / 'upstream/emscripten'
    version = (emroot / 'emscripten-version.txt').read_text().strip().strip('"').removesuffix('-git')
    if version != EMSCRIPTEN_VERSION:
        raise RuntimeError('Requires Emscripten ' + EMSCRIPTEN_VERSION)
    native.fetch_sources()
    BUILD.mkdir(parents=True, exist_ok=True)
    fingerprint = hashlib.sha256(b''.join((HERE / name).read_bytes() for name in ('build_wasm.py', 'sources.json'))).hexdigest()
    stamp = BUILD / 'inputs.sha256'
    if stamp.exists() and stamp.read_text() != fingerprint:
        shutil.rmtree(BUILD)
        BUILD.mkdir(parents=True)
    stamp.write_text(fingerprint)
    shutil.copyfile(emroot / 'LICENSE', BUILD / 'emscripten-LICENSE.txt')
    prefix = BUILD / 'prefix'
    prefix.mkdir(exist_ok=True)
    env = dict(os.environ)
    node = shutil.which('node')
    if not node:
        raise RuntimeError('Node.js is required to run Emscripten')
    env.update(EMSDK=str(args.emsdk.resolve()), EM_CONFIG=str(args.emsdk.resolve() / '.emscripten'),
               PATH=str(emroot) + os.pathsep + env['PATH'],
               CC=str(emroot / 'emcc'), CXX=str(emroot / 'em++'),
               AR=str(emroot / 'emar'), RANLIB=str(emroot / 'emranlib'),
               CFLAGS='-O2', CXXFLAGS='-O2',
               CPPFLAGS=f'-I{prefix}/include', LDFLAGS=f'-L{prefix}/lib',
               PKG_CONFIG=shutil.which('pkg-config') or 'pkg-config',
               PKG_CONFIG_PATH='', PKG_CONFIG_LIBDIR=str(prefix / 'lib/pkgconfig'))
    for item in native.SOURCES:
        name = item['archive'].removesuffix('.tar.xz').removesuffix('.tar.gz')
        source = BUILD / name
        done = BUILD / (name + '.done')
        if done.exists():
            continue
        if not source.exists():
            with tarfile.open(native.BUILD / 'downloads' / item['archive']) as archive:
                archive.extractall(BUILD, filter='data')
        log = BUILD / (name + '.log')
        command = [emroot / 'emconfigure', source / 'configure', '--host=none',
                   '--prefix=' + str(prefix), '--disable-shared', '--enable-static',
                   'HOST_CC=' + ('/usr/bin/clang' if sys.platform == 'darwin' else (shutil.which('cc') or 'cc')),
                   'HOST_CXX=' + ('/usr/bin/clang++' if sys.platform == 'darwin' else (shutil.which('c++') or 'c++'))]
        if name.startswith('gmp'):
            options = ['--disable-assembly']
        elif name.startswith('mpfr'):
            options = ['--with-gmp=' + str(prefix)]
        elif name.startswith('libxml2'):
            options = ['--without-python', '--without-iconv', '--without-zlib', '--without-lzma', '--without-readline']
        else:
            options = ['--disable-nls', '--without-icu', '--without-libcurl', '--without-readline',
                       '--without-gnuplot-call', '--disable-insecure', '--enable-compiled-definitions',
                       '--without-libiconv-prefix', '--without-libintl-prefix']
            env.update(LIBXML_CFLAGS=f'-I{prefix}/include/libxml2', LIBXML_LIBS=f'-L{prefix}/lib -lxml2 -lm')
        print('WASM: building ' + name, flush=True)
        native.run(command + options, source, env, log)
        if name.startswith('libqalculate'):
            for xml in (source / 'data').glob('*.xml.in'):
                xml.with_suffix('').write_text(xml.read_text().replace('<_', '<').replace('</_', '</'))
            native.run(['make', '-C', 'libqalculate', f'-j{args.jobs}'], source, env, log)
            native.run(['make', '-C', 'libqalculate', 'install'], source, env, log)
        else:
            native.run(['make', f'-j{args.jobs}'], source, env, log)
            native.run(['make', 'install'], source, env, log)
        done.write_text('ok\n')
    out = BUILD / 'dist'
    out.mkdir(exist_ok=True)
    native.run([emroot / 'em++', '-std=c++17', '-O2', '-fexceptions',
                '-I' + str(prefix / 'include'), '-I' + str(prefix / 'include/libxml2'),
                HERE / 'wasm_bridge.cpp', '-L' + str(prefix / 'lib'),
                '-lqalculate', '-lmpfr', '-lgmp', '-lxml2',
                '-sMODULARIZE=1', '-sEXPORT_NAME=createQalculateModule', '-sENVIRONMENT=web,worker,node',
                '-sALLOW_MEMORY_GROWTH=1', '-sMAXIMUM_MEMORY=268435456', '-sINITIAL_MEMORY=33554432',
                '-sFILESYSTEM=0', '-sEXPORTED_FUNCTIONS=["_retui_evaluate"]',
                '-sEXPORTED_RUNTIME_METHODS=["cwrap"]',
                '-sDISABLE_EXCEPTION_CATCHING=0', '-o', out / 'engine.js'],
               BUILD, env, BUILD / 'bridge.log')
    print(out, flush=True)


if __name__ == '__main__':
    main()
