#!/usr/bin/env python3
"""Force-stop only the isolated SSH test app between the two device-test phases."""
import argparse,json,shutil,subprocess,time
from pathlib import Path
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True, help='Exact adb device serial')
parser.add_argument('--adb', default=shutil.which('adb') or 'adb')
parser.add_argument('--package', default='com.dvil.tui_renewed.termuxsshdebug')
parser.add_argument('--output', default=str(Path(__file__).resolve().parents[1] / 'docs/analysis/termux-ssh/evidence/production'))
options=parser.parse_args()
pkg=options.package
if not pkg.endswith('.termuxsshdebug'):
    parser.error('Only an isolated .termuxsshdebug package may be force-stopped')
base=[options.adb,'-s',options.serial]
out=Path(options.output).resolve()
out.mkdir(parents=True,exist_ok=True)
args=['shell','am','instrument','-w','-e','class','ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceProcessDeathTest#liveShellAndSelectionSurviveExternalProcessDeath','-e','termuxProcessDeathPhase']
with (out/'process-death-seed.log').open('w') as log:
    proc=subprocess.Popen(base+args+['seed',pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=log,stderr=subprocess.STDOUT)
    ready=None
    for attempt in range(60):
        r=subprocess.run(base+['exec-out','run-as',pkg,'cat','cache/termux-process-death-proof.json'],capture_output=True,text=True)
        try:
            value=json.loads(r.stdout)
            if value.get('stage')=='READY_FOR_KILL':ready=value;break
        except ValueError:pass
        if proc.poll() is not None:break
        time.sleep(.5)
    if ready is None:
        raise RuntimeError('Seed did not become ready; inspect process-death-seed.log')
    before=subprocess.check_output(base+['shell','pidof',pkg],text=True).strip()
    subprocess.run(base+['shell','am','force-stop',pkg],check=True)
    proc.wait(timeout=15)
    assert not subprocess.run(base+['shell','pidof',pkg],capture_output=True,text=True).stdout.strip(), 'App process survived force-stop'
verify=subprocess.Popen(base+args+['verify',pkg+'.test/androidx.test.runner.AndroidJUnitRunner'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
after=None
for attempt in range(50):
    value=subprocess.run(base+['shell','pidof',pkg],capture_output=True,text=True).stdout.strip()
    if value and value!=before:
        after=value;break
    if verify.poll() is not None:break
    time.sleep(.1)
output=verify.communicate(timeout=45)[0]
(out/'process-death-verify.log').write_text(output)
assert 'OK (1 test)' in output, output[-2500:]
assert after and before!=after, (before,after)
ready.pop('prefs',None)
ready.update(app_pid_before=before,app_pid_after=after,seed_intentionally_force_stopped=True,verify_passed=True)
(out/'process-death-proof.json').write_text(json.dumps(ready,indent=2)+'\n')
image=subprocess.check_output(base+['exec-out','run-as',pkg,'cat','files/termux-process-death-restored.png'])
(out/'process-death-restored.png').write_bytes(image)
print(json.dumps({'passed':True,'old_app_pid':before,'new_app_pid':after,'same_shell_pid':ready['panePid']}))
