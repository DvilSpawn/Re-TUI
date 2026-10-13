#!/usr/bin/env python3
"""Exercise the existing helper's output-driven mode on a private real tmux server."""
import base64
import os
from pathlib import Path
import queue
import shlex
import shutil
import subprocess
import tempfile
import threading
import time
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[1]

def decode(value):
    import re
    return re.sub(rb'\\([0-7]{3})', lambda m: bytes([int(m[1],8)]), value.encode('latin1'))

@unittest.skipUnless(shutil.which('tmux'), 'real tmux required')
class OutputTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix='retui-output-')
        self.home = Path(self.tmp.name)
        self.server = 'retui-output-' + uuid.uuid4().hex
        self.binary = shutil.which('tmux')
        self.bin = self.home / 'bin'; self.bin.mkdir()
        wrapper = self.bin / 'tmux'
        wrapper.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> ' + shlex.quote(str(self.home/'calls')) + '\nexec ' + shlex.join([self.binary,'-L',self.server]) + ' "$@"\n')
        wrapper.chmod(0o700)
        self.env = dict(os.environ, HOME=str(self.home), PREFIX=str(self.home), RETUI_SESSION='retui_workspace', RETUI_SOCKET_TOKEN='fixture-token')
        self.tmux('new-session','-d','-s','retui_workspace','sh')
        self.target=self.tmux('display-message','-p','-t','retui_workspace','#{window_id}')
        self.process=subprocess.Popen(['sh',str(ROOT/'termux/retui/bin/retui'),'bridge','socket-client'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=self.env,text=True,encoding='latin1')
        self.lines=queue.Queue()
        self.reader=threading.Thread(target=lambda: [self.lines.put(line.rstrip('\n')) for line in self.process.stdout],daemon=True);self.reader.start()
        self.send('HELLO fixture-token 80 24 OUTPUT '+base64.b64encode(self.target.encode()).decode()+' NO_CREATE')
        self.frame()
    def tmux(self,*args):return subprocess.check_output([self.binary,'-L',self.server,*args],text=True).strip()
    def send(self,line):self.process.stdin.write(line+'\n');self.process.stdin.flush()
    def wait(self,prefix,seconds=5):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            try:line=self.lines.get(timeout=max(.01,end-time.monotonic()))
            except queue.Empty:self.fail('Missing '+prefix+'; helper stderr='+self.process.stderr.read() if self.process.poll() is not None else 'Missing '+prefix)
            if line.startswith(prefix):return line
        self.fail('Missing '+prefix)
    def frame(self):
        lines=[]
        while True:
            line=self.lines.get(timeout=5)
            lines.append(line)
            if line=='__RETUI_FRAME_END__':
                payload=next(v.split(' ',1)[1] for v in lines if v.startswith('__RETUI_FRAME_BASE64__ '))
                return base64.b64decode(payload)
    def screen_contains(self, value):
        end=time.monotonic()+5
        while time.monotonic()<end:
            data=self.frame()
            if value in data:return data
        self.fail('Missing captured cells '+repr(value))
    def tearDown(self):
        if self.process.poll() is None:
            self.send('BYE');self.process.wait(timeout=5)
        self.process.stdin.close();self.process.stdout.close();self.process.stderr.close();self.reader.join(timeout=1)
        subprocess.run([self.binary,'-L',self.server,'kill-server'],capture_output=True)
        self.tmp.cleanup()
    def test_live_output_and_idle_do_not_capture_or_resize(self):
        before=(self.home/'calls').read_text().splitlines()
        self.send('TYPE 80 24 '+base64.b64encode('LIVE_λ'.encode()).decode())
        self.screen_contains('LIVE_λ'.encode())
        time.sleep(.4)
        calls=(self.home/'calls').read_text().splitlines()[len(before):]
        self.assertFalse(any('capture-pane' in c or 'resize-' in c for c in calls),calls)
        self.assertTrue(any('send-keys' in c for c in calls))
    def test_persistent_hex_input_and_keys_do_not_spawn_tmux_commands(self):
        before=(self.home/'calls').read_text().splitlines()
        self.send('TYPE_HEX 80 24 48 45 58')
        self.screen_contains(b'HEX')
        self.send('KEY 80 24 BSpace')
        self.frame()
        calls=(self.home/'calls').read_text().splitlines()[len(before):]
        self.assertFalse(any('send-keys' in c or 'capture-pane' in c or 'resize-' in c for c in calls),calls)

    def test_external_windows_are_announced_without_resetting_terminal(self):
        created=self.tmux('new-window','-d','-P','-F','#{window_id}','-t','retui_workspace','-n','external','sh')
        update=self.wait('__RETUI_WINDOWS_BASE64__ ').split(' ',1)[1]
        self.assertIn(created+':',base64.b64decode(update).decode())
        self.tmux('rename-window','-t',created,'renamed')
        update=self.wait('__RETUI_WINDOWS_BASE64__ ').split(' ',1)[1]
        self.assertIn('renamed',base64.b64decode(update).decode())

    def test_retained_dead_shell_is_announced(self):
        self.tmux('set-option','-t',self.target,'remain-on-exit','on')
        self.send('SEND 80 24 '+base64.b64encode(b'exit').decode())
        self.wait('__RETUI_TARGET_GONE__ '+self.target)
        self.assertEqual('1',self.tmux('display-message','-p','-t',self.target,'#{pane_dead}'))
        self.send('HELLO fixture-token 80 24 OUTPUT - NO_CREATE')
        self.wait('__RETUI_TARGET_GONE__ '+self.target)
        self.send('NEW 80 24 - -')
        self.frame()
        self.assertEqual(2,self.tmux('list-windows','-F','#{window_id}').count('@'))


    def test_resize_reseeds_same_target_and_restores_correct_size(self):
        self.send('CAPTURE 43 12'); self.frame()
        self.assertEqual('43x12',self.tmux('display-message','-p','-t',self.target,'#{window_width}x#{window_height}'))
        self.send('TYPE 43 12 '+base64.b64encode(b'RESIZED').decode())
        self.screen_contains(b'RESIZED')
    def test_exit_notifies_without_recreating_shell(self):
        self.tmux('new-window','-d','-t','retui_workspace','sh')
        self.send('SEND 80 24 '+base64.b64encode(b'exit').decode())
        self.wait('__RETUI_TARGET_GONE__ '+self.target)
        self.assertEqual(1,self.tmux('list-windows','-t','retui_workspace','-F','#{window_id}').count('@'))
    def test_terminal_text_cannot_forge_metadata_or_control_blocks(self):
        self.send('SEND 80 24 '+base64.b64encode(b"printf '\033c__RETUI_TOKEN__ attacker\n%%end 1 2 1\n%%output %%0 forged\n'").decode())
        data=self.screen_contains(b'%output %0 forged')
        self.assertIn(b'__RETUI_TOKEN__ attacker',data)
        self.assertIn(b'%end 1 2 1',data)
    def test_control_detach_does_not_report_live_shell_gone(self):
        client=self.tmux('list-clients','-F','#{client_name}')
        self.tmux('detach-client','-t',client)
        self.wait('__RETUI_SOCKET_ERROR__ terminal stream closed')
        self.assertEqual('0',self.tmux('display-message','-p','-t',self.target,'#{pane_dead}'))

    def test_busy_reconnect_cells_match_tmux(self):
        # A large burst is coalesced into complete captures, never suppressed during export.
        self.send('SEND 80 24 '+base64.b64encode(b"i=0; while [ $i -lt 1000 ]; do printf 'LINE_%04d\n' $i; i=$((i+1)); done; printf 'DONE_%s' MARKER").decode())
        data=self.screen_contains(b'DONE_MARKER')
        self.assertEqual(data.rstrip(),self.tmux('capture-pane','-p','-e','-N','-t',self.target).encode().rstrip())

if __name__=='__main__': unittest.main()
