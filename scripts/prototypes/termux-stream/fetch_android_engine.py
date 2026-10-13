#!/usr/bin/env python3
"""Fetch the engine and reuse the launcher's grid for the isolated debug app."""
import hashlib
import io
from pathlib import Path
import sys
import tarfile
import urllib.request

COMMIT = '5b657c6adf4304e5198951ce815fe0205dcac29c'
SHA256 = 'a79a8ae388caab56d4d6df8f80aa213c21090e3a1f605c28d7f93fb466d6e7ca'
destination = Path(sys.argv[1])
destination.mkdir(parents=True, exist_ok=True)
archive = destination / 'upstream.tar.gz'
if not archive.exists():
    with urllib.request.urlopen(f'https://codeload.github.com/termux/termux-app/tar.gz/{COMMIT}', timeout=30) as response:
        archive.write_bytes(response.read())
data = archive.read_bytes()
assert hashlib.sha256(data).hexdigest() == SHA256, 'Termux source checksum mismatch'
with tarfile.open(fileobj=io.BytesIO(data), mode='r:gz') as bundle:
    for member in bundle.getmembers():
        name = '/'.join(member.name.split('/')[1:])
        prefix = 'terminal-emulator/src/main/java/'
        if name.startswith(prefix) and name.endswith('.java'):
            target = destination / 'java' / name[len(prefix):]
        elif name == 'LICENSE.md':
            target = destination / 'assets/licenses/termux/LICENSE.md'
        else:
            continue
        assert member.isfile() and '..' not in Path(name).parts
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(bundle.extractfile(member).read())
root = Path(__file__).resolve().parents[3]
grid = (root / 'app/src/main/java/ohi/andre/consolelauncher/managers/termux/TerminalGridView.kt').read_text()
grid = grid.replace('package ohi.andre.consolelauncher.managers.termux', 'package com.dvil.retui.streamprobe')
adapter = Path(__file__).with_name('grid_adapter.kt.txt').read_text()
if 'fun setEmulatorFrame(' not in grid:
    grid = grid.replace('    fun setFrame(', adapter + '\n    fun setFrame(', 1)
target = destination / 'java/com/dvil/retui/streamprobe/TerminalGridView.kt'
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(grid)
# Remove the renderer cached by an earlier experimental build.
renderer = destination / 'java/com/termux/view/TerminalRenderer.java'
if renderer.exists():
    renderer.unlink()
