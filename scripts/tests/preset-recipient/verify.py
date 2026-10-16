#!/usr/bin/env python3
"""Prove effective cross-UID access with grants/revokes outside nested Binder calls."""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default='adb')
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
uri = 'content://dev.retui.test.presetsender.FILE_PROVIDER/cache_files/preset-share/revoke.retui'

def call(authority, method, argument=None):
    command = [args.adb, '-s', args.serial, 'shell', 'content', 'call', '--uri', 'content://' + authority, '--method', method]
    if argument is not None:
        command += ['--arg', argument]
    result = subprocess.run(command, check=True, capture_output=True, text=True)
    return result.stdout + result.stderr

phases = {}
try:
    phases['prepare'] = call('dev.retui.test.presetsender.cycle', 'prepare')
    phases['before'] = call('dev.retui.test.presetrecipient.read', 'read-share', uri)
    phases['grant'] = call('dev.retui.test.presetsender.cycle', 'grant')
    phases['during'] = call('dev.retui.test.presetrecipient.read', 'read-share', uri)
finally:
    phases['revoke'] = call('dev.retui.test.presetsender.cycle', 'revoke')
    phases['after'] = call('dev.retui.test.presetrecipient.read', 'read-share', uri)
    (args.output / 'phase-output.json').write_text(json.dumps(phases, indent=2) + '\n')

for phase in ['before', 'after']:
    assert 'error=' in phases[phase] and 'bytes=[' not in phases[phase], f'{phase}: private share remained readable: {phases[phase]}'
assert 'SecurityException' in phases['before'] or 'FileNotFoundException: No content provider:' in phases['before'], phases['before']
assert 'SecurityException' in phases['after'], 'After revoke must be a permission denial, not a missing-file failure'
expected = 'bytes=[' + ', '.join(str(value) for value in b'independent-sender-preset') + ']'
assert expected in phases['during'], phases['during']
assert 'mime=application/vnd.retui+zip' in phases['during'] and 'error=' not in phases['during'], phases['during']
sender_uid = int(re.search(r'senderUid=(\d+)', phases['prepare']).group(1))
recipient_uid = int(re.search(r'uid=(\d+)', phases['during']).group(1))
assert sender_uid != recipient_uid, 'Sender and recipient must have distinct UIDs'
summary = {'passed': True, 'serial': args.serial, 'sender_uid': sender_uid, 'recipient_uid': recipient_uid,
           'before_read_denied': True, 'during_exact_bytes': True, 'after_read_denied': True,
           'permission_reporting': {phase: re.findall(r'(?:fullReadPermission|readPermission)=(-?\d+)', phases[phase]) for phase in ['before', 'during', 'after']}}
(args.output / 'result.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(summary, indent=2))
