#!/usr/bin/env python3
"""Build Re:TUI's JNI engine from checksum-pinned upstream release sources (Python 3.12+)."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shlex
import shutil
import subprocess
import tarfile
import urllib.request

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
BUILD = ROOT / 'app/build/qalculate'
NDK_VERSION = '27.1.12297006'
TARGETS = {'arm64-v8a': 'aarch64-linux-android', 'armeabi-v7a': 'armv7a-linux-androideabi',
           'x86_64': 'x86_64-linux-android', 'x86': 'i686-linux-android'}
SOURCES = json.loads((HERE / 'sources.json').read_text())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fetch_sources():
    cache = BUILD / 'downloads'
    cache.mkdir(parents=True, exist_ok=True)
    for source in SOURCES:
        path = cache / source['archive']
        bundled = HERE / 'upstream' / source['archive']
        if not path.exists() and bundled.exists():
            shutil.copyfile(bundled, path)
        if not path.exists():
            print('Downloading ' + source['archive'], flush=True)
            temporary = path.with_suffix('.partial')
            try:
                with urllib.request.urlopen(source['url'], timeout=60) as response, temporary.open('wb') as output:
                    shutil.copyfileobj(response, output)
                if digest(temporary) != source['sha256']:
                    raise RuntimeError('Checksum mismatch: ' + source['archive'])
                temporary.replace(path)
            finally:
                temporary.unlink(missing_ok=True)
        if digest(path) != source['sha256']:
            raise RuntimeError('Checksum mismatch: ' + str(path))


def run(command, cwd, env, log):
    with log.open('a') as output:
        output.write('\n$ ' + shlex.join(map(str, command)) + '\n')
        output.flush()
        result = subprocess.run(list(map(str, command)), cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'Build failed; see {log}\n' + '\n'.join(log.read_text(errors='replace').splitlines()[-35:]))


def build(abi, ndk, jobs):
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    toolchain = ndk / 'toolchains/llvm/prebuilt' / host
    target = TARGETS[abi]
    compiler = toolchain / 'bin' / (target + '24-clang')
    cxx = toolchain / 'bin' / (target + '24-clang++')
    if not compiler.is_file():
        raise RuntimeError(f'Install Android NDK {NDK_VERSION}; missing {compiler}')
    if f'Pkg.Revision = {NDK_VERSION}' not in (ndk / 'source.properties').read_text():
        raise RuntimeError('Build requires pinned NDK ' + NDK_VERSION)
    # Changing build inputs invalidates all native objects; failed stages never receive a stamp.
    fingerprint = hashlib.sha256(b''.join((HERE / name).read_bytes() for name in
        ('build.py', 'sources.json', 'android-cancellation.patch'))).hexdigest()
    work = BUILD / abi
    stamp = work / 'inputs.sha256'
    if stamp.exists() and stamp.read_text() != fingerprint:
        shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    stamp.write_text(fingerprint)
    prefix = work / 'prefix'
    prefix.mkdir(exist_ok=True)
    output = BUILD / 'jniLibs' / abi
    output.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ)
    env.update(CC=str(compiler), CXX=str(cxx), AR=str(toolchain / 'bin/llvm-ar'),
               RANLIB=str(toolchain / 'bin/llvm-ranlib'), STRIP=str(toolchain / 'bin/llvm-strip'),
               CFLAGS='-O2 -fPIC', CXXFLAGS='-O2 -fPIC', CPPFLAGS=f'-I{prefix}/include',
               LDFLAGS=f'-L{prefix}/lib -Wl,-z,max-page-size=16384',
               PKG_CONFIG=shutil.which('pkg-config') or 'pkg-config',
               PKG_CONFIG_PATH='', PKG_CONFIG_LIBDIR=str(prefix / 'lib/pkgconfig'))
    # No host libraries or executable target test programs may leak into this cross build.
    host_triplet = 'arm-linux-androideabi' if abi == 'armeabi-v7a' else target
    for item in SOURCES:
        name = item['archive'].removesuffix('.tar.xz').removesuffix('.tar.gz')
        source = work / name
        done = work / (name + '.done')
        if done.exists():
            continue
        if not source.exists():
            with tarfile.open(BUILD / 'downloads' / item['archive']) as archive:
                archive.extractall(work, filter='data')
            if name.startswith('libqalculate'):
                run(['patch', '-p1', '-i', HERE / 'android-cancellation.patch'], source, env, work / 'patch.log')
        log = work / (name + '.log')
        common = [source / 'configure', '--host=' + host_triplet, '--prefix=' + str(prefix),
                  '--disable-shared', '--enable-static', '--with-pic']
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
        print(f'{abi}: building {name}', flush=True)
        run(common + options, source, env, log)
        if name.startswith('libqalculate'):
            # English definitions only; avoid host intltool/gettext and the unused qalc CLI.
            for xml in (source / 'data').glob('*.xml.in'):
                xml.with_suffix('').write_text(xml.read_text().replace('<_', '<').replace('</_', '</'))
            run(['make', '-C', 'libqalculate', f'-j{jobs}'], source, env, log)
            run(['make', '-C', 'libqalculate', 'install'], source, env, log)
        else:
            run(['make', f'-j{jobs}'], source, env, log)
            run(['make', 'install'], source, env, log)
        done.write_text('ok\n')
    native = output / 'libretui_qalculate.so'
    print(f'{abi}: linking JNI bridge', flush=True)
    run([cxx, '-std=c++17', '-O2', '-fPIC', '-shared', '-fvisibility=hidden',
         '-I' + str(prefix / 'include'), '-I' + str(prefix / 'include/libxml2'), HERE / 'bridge.cpp',
         '-Wl,--start-group', prefix / 'lib/libqalculate.a', prefix / 'lib/libmpfr.a',
         prefix / 'lib/libgmp.a', prefix / 'lib/libxml2.a', '-Wl,--end-group',
         '-Wl,--exclude-libs,ALL', '-Wl,-z,max-page-size=16384', '-Wl,--no-undefined',
         '-Wl,-soname,libretui_qalculate.so', '-lm', '-o', native], work, env, work / 'bridge.log')
    shutil.copy2(toolchain / 'sysroot/usr/lib' / host_triplet / 'libc++_shared.so', output)
    run([toolchain / 'bin/llvm-strip', '--strip-unneeded', native], work, env, work / 'bridge.log')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ndk', type=Path, required=True)
    parser.add_argument('--abis', nargs='+', choices=TARGETS, default=list(TARGETS))
    parser.add_argument('--jobs', type=int, default=min(os.cpu_count() or 2, 6))
    args = parser.parse_args()
    fetch_sources()
    for abi in args.abis:
        build(abi, args.ndk.resolve(), args.jobs)


if __name__ == '__main__':
    main()
