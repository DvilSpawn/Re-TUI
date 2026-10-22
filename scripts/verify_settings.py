#!/usr/bin/env python3
"""Build/test Re:TUI and report coverage honestly, including gates and skipped tests."""
import argparse
from collections import Counter, defaultdict
import hashlib
import json
from pathlib import Path
import re
import os
import signal
import fcntl
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / 'build/validation/settings-reload'
SUFFIXES = ('base', 'palettedebug', 'termuxsshdebug', 'calculatorpackdebug', 'librarydebug', 'fetchdebug')
SAFE_GATES = ('timeReloadReview', 'wirelessReview', 'wallpaperGalleryTests', 'widgetOrientation',
              'termuxSshDevice', 'termuxPaneBenchmark', 'terminalHomeDeviceBenchmark',
              'headerScreens', 'backGesture')


def inventory():
    rows = []
    for kind, directory in [('unit', 'app/src/test'), ('device', 'app/src/androidTest')]:
        for path in sorted((ROOT / directory).rglob('*')):
            if path.suffix not in ('.kt', '.java'):
                continue
            source = path.read_text()
            package = re.search(r'^package\s+([\w.]+)', source, re.M)
            names = re.findall(r'\bclass\s+(\w+)', source)
            name = path.stem if path.stem in names else next((n for n in names if n.endswith('Test')), None)
            if not package or not name:
                continue
            tests = len(re.findall(r'@Test\b', source))
            release = 'extends Instrumentation' in source or ': Instrumentation()' in source
            if not tests and not release:
                continue
            guarded_lines = '\n'.join(line for line in source.splitlines()
                                      if re.search(r'(?:check|assertTrue)\s*\(', line))
            required = re.findall(r'endsWith\("\.(' + '|'.join(SUFFIXES) + r')"\)', guarded_lines)
            suffix = next((s for s in SUFFIXES if s in required), 'palettedebug')
            if re.search(r'packageName\s*==\s*"com\.dvil\.tui_renewed"', guarded_lines):
                suffix = 'base'
            if path.stem in ('FrameExportCacheTest', 'HomeResumePerformanceTest', 'OutputHomeParityIntegrationTest'):
                suffix = 'termuxsshdebug'
            if '.managers.termux' in package.group(1) and not required:
                suffix = 'termuxsshdebug'
            gates = [line.strip() for line in source.splitlines()
                     if any(key in line for key in ('assumeTrue', 'assumeFalse', 'getArguments()',
                                                   'args.getString(', 'arguments.getString('))]
            rows.append(dict(kind='release' if release else kind, source=str(path.relative_to(ROOT)),
                             className=package.group(1) + '.' + name, tests=tests,
                             suffix=suffix, emulatorOnly=bool(re.search(
                                 r'Disposable emulator|disposable emulator|Only change settings in a disposable emulator', source)),
                             staged=bool(re.search(r'"(?:packStage|termuxProcessDeathPhase|termuxUpgradePhase)"', source)),
                             gates=gates,
                             buildArguments={
                                 'IrcReleaseSmokeInstrumentation': ['-PircReleaseSmokeRunner=true'],
                                 'SettingsPreviewReleaseInstrumentation': ['-PsettingsPreviewReleaseCheck=true'],
                                 'TermuxWorkspaceSshReleaseInstrumentation': ['-PtermuxSshReleaseCheck=true']
                             }.get(name, []),
                             dedicatedRunner='scripts/terminal_colors/run.py' if name == 'PaletteReadabilityTest' else None))
    return rows


def run(command, log, timeout=1800):
    print('Running', ' '.join(map(str, command)), flush=True)
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open('w') as output:
        process = subprocess.Popen(list(map(str, command)), cwd=ROOT, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
        try:
            return process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
            output.write("\nVerification timed out; coverage is incomplete.\n")
            return 124



def parse_instrumentation(text):
    """Parse individual results; runner exit codes alone do not indicate test success."""
    results, current = [], {}
    continuation = None
    for line in text.splitlines():
        match = re.match(r'INSTRUMENTATION_STATUS: ([\w]+)=(.*)', line)
        if match:
            key, value = match.groups()
            current[key] = value
            continuation = key if key == 'stack' else None
        elif line.startswith('INSTRUMENTATION_STATUS_CODE:'):
            code = int(line.split(':', 1)[1])
            if code != 1:
                results.append(dict(className=current.get('class'), test=current.get('test'),
                                    status={0: 'passed', -3: 'skipped', -4: 'skipped'}.get(code, 'failed'),
                                    reason=current.get('stack', '')))
            current, continuation = {}, None
        elif continuation and not line.startswith('INSTRUMENTATION_'):
            current[continuation] += '\n' + line
    return results


def device(args, rows, output):
    adb = ['adb', '-s', args.serial]
    model = subprocess.check_output(adb + ['shell', 'getprop', 'ro.product.model'], text=True).strip()
    sdk = subprocess.check_output(adb + ['shell', 'getprop', 'ro.build.version.sdk'], text=True).strip()
    actual_emulator = subprocess.check_output(adb + ['shell', 'getprop', 'ro.kernel.qemu'], text=True).strip() == '1'
    if args.emulator != actual_emulator:
        raise SystemExit('Use --emulator exactly when targeting an emulator; serial was not accepted.')
    output = output / args.serial.replace(':', '_')
    output.mkdir(parents=True, exist_ok=True)
    groups = defaultdict(list)
    report = dict(serial=args.serial, model=model, sdk=sdk,
                  commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  dirty=bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT)),
                  tests=[], blocked=[], artifacts=[])
    for row in rows:
        if args.classes and row['className'] not in args.classes.split(','):
            continue
        if row['kind'] == 'release':
            report['blocked'].append(dict(**row, reason='Run with the release instrumentation build property; separate release proof required.'))
        elif row['kind'] == 'device':
            if args.package_suffix and row['suffix'] != args.package_suffix:
                continue
            if args.classes and row['className'] not in args.classes.split(','):
                continue
            if row['emulatorOnly'] and not args.emulator:
                report['blocked'].append(dict(**row, reason='Explicit emulator-only fixture; run on emulator.'))
            elif row.get('dedicatedRunner'):
                report['blocked'].append(dict(**row, reason='Run the dedicated matrix driver with explicit themes, surfaces and configurations.'))
            elif row['staged']:
                report['blocked'].append(dict(**row, reason='Requires staged install/process-death orchestration; run documented phases separately.'))
            else:
                groups[row['suffix']].append(row)
    failures = False
    for suffix, group in sorted(groups.items()):
        if suffix == 'base' and not args.emulator:
            report['blocked'] += [dict(**row,reason='Base-package fixture requires a disposable emulator.') for row in group]
            continue
        package = 'com.dvil.tui_renewed' + ('' if suffix == 'base' else '.' + suffix)
        properties = ['-PtestPackageSuffix=' + suffix]
        log = output / (suffix + '-build.log')
        with (DEFAULT_OUTPUT / 'apk-build.lock').open('a') as build_lock:
            fcntl.flock(build_lock, fcntl.LOCK_EX)
            if run(['./gradlew', ':app:assemblePlaystoreDebug', ':app:assemblePlaystoreDebugAndroidTest',
                    '--console=plain'] + properties, log):
                failures = True
                report['blocked'] += [dict(**row, reason='Build failed; see ' + str(log)) for row in group]
                continue
            app = ROOT / 'app/build/outputs/apk/playstore/debug/app-playstore-debug.apk'
            test = ROOT / 'app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk'
            for source, name in [(app, 'app'), (test, 'test')]:
                target = output / (suffix + '-' + name + '.apk')
                target.write_bytes(source.read_bytes())
                artifact = dict(path=str(target), sha256=hashlib.sha256(target.read_bytes()).hexdigest())
                sdk = next((line.split('=',1)[1] for line in (ROOT/'local.properties').read_text().splitlines()
                            if line.startswith('sdk.dir=')), '') if (ROOT/'local.properties').exists() else ''
                import os
                tools = sorted(Path(sdk or os.environ.get('ANDROID_HOME', '')).glob('build-tools/*/apksigner'))
                if tools:
                    signed = subprocess.check_output([str(tools[-1]),'verify','--print-certs',str(target)],text=True)
                    artifact['certificate'] = next((line for line in signed.splitlines() if 'certificate SHA-256 digest:' in line), '')
                report['artifacts'].append(artifact)
                install_log = output / (suffix + '-' + name + '-install.log')
                if run(adb + ['install', '-r', '-d', str(target)], install_log):
                    # The base ID is permitted only on a disposable emulator.
                    if suffix == 'base' and not args.emulator:
                        raise SystemExit('Refusing to reset the main launcher on a phone; use an isolated test package.')
                    run(adb + ['uninstall', package + ('.test' if name == 'test' else '')], install_log.with_suffix('.uninstall.log'))
                    if run(adb + ['install', str(target)], install_log):
                        raise SystemExit('Cannot install isolated test APK: ' + str(install_log))
        # Start from a clean isolated launcher between package groups.
        run(adb + ['shell', 'pm', 'clear', package], output / (suffix + '-clear.log'))
        if suffix == 'calculatorpackdebug' and args.engine_pack:
            fixture = args.engine_pack.resolve()
            remote = '/data/local/tmp/retui-test-engine.pack'
            run(adb + ['push', str(fixture), remote], output / (suffix + '-fixture-push.log'))
            run(adb + ['shell', 'run-as', package, 'mkdir', '-p', 'files'], output / (suffix + '-fixture-dir.log'))
            run(adb + ['shell', 'run-as', package, 'cp', remote, 'files/engine-fixture.retui.calc.engine'],
                output / (suffix + '-fixture-copy.log'))
        if suffix == 'termuxsshdebug' and args.enable_termux:
            run(adb + ['shell', 'pm', 'revoke', package, 'com.termux.permission.RUN_COMMAND'],
                output / 'termux-revoke-test-permission.log')
            log = output / 'termux-enable.txt'
            command = adb + ['shell','am','instrument','-w','-r','-e','class',
                'ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshEnableTest',
                '-e','termuxSshEnable','true',package+'.test/androidx.test.runner.AndroidJUnitRunner']
            run(command, log, timeout=240)
            report['tests'] += parse_instrumentation(log.read_text())
        command = adb + ['shell', 'am', 'instrument', '-w', '-r']
        for gate in SAFE_GATES:
            command += ['-e', gate, 'true']
        for argument in args.argument:
            key, value = argument.split('=', 1)
            command += ['-e', key, value]
        command += ['-e', 'class', ','.join(row['className'] for row in group),
                    package + '.test/androidx.test.runner.AndroidJUnitRunner']
        log = output / (suffix + '-tests.txt')
        code = run(command, log, timeout=3600)
        parsed = parse_instrumentation(log.read_text())
        report['tests'] += parsed
        if code or not parsed or any(row['status'] == 'failed' for row in parsed):
            failures = True
        for row in group:
            if not any(test['className'] == row['className'] for test in parsed):
                report['blocked'].append(dict(**row, reason='No terminal test result; runner may have crashed.'))
        report['counts'] = dict(Counter(test['status'] for test in report['tests']))
        (output / 'report.json').write_text(json.dumps(report, indent=2))
    print(json.dumps(report.get('counts', {})), 'blocked classes:', len(report['blocked']))
    if failures: return 1
    return 2 if report['blocked'] or any(test['status']=='skipped' for test in report['tests']) else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['inventory', 'host', 'device'])
    parser.add_argument('--serial')
    parser.add_argument('--package-suffix', choices=SUFFIXES)
    parser.add_argument('--argument', action='append', default=[], metavar='NAME=VALUE')
    parser.add_argument('--classes', help='Comma-separated class names for a focused rerun')
    parser.add_argument('--engine-pack', type=Path, help='Current Qalculate .retui.calc.engine fixture')
    parser.add_argument('--enable-termux', action='store_true', help='Exercise real permission and SSH pairing before tests')
    parser.add_argument('--emulator', action='store_true')
    parser.add_argument('--output', type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    DEFAULT_OUTPUT.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    rows = inventory()
    (args.output / 'inventory.json').write_text(json.dumps(rows, indent=2))
    if args.mode == 'inventory':
        print(json.dumps(dict(Counter(row['kind'] for row in rows)), indent=2))
        return 0
    if args.mode == 'device':
        if not args.serial:
            parser.error('device requires --serial; it can change isolated launcher test data')
        with (DEFAULT_OUTPUT / ('device-' + args.serial.replace(':','_') + '.lock')).open('a') as device_lock:
            fcntl.flock(device_lock, fcntl.LOCK_EX)
            return device(args, rows, args.output)
    commands = [('./gradlew', ':app:testPlaystoreDebugUnitTest', ':app:testFdroidDebugUnitTest', '--console=plain')]
    # Some test_*.py files are device drivers with required command-line arguments.
    # Do not import those as unittest modules (their argparse exits at import time).
    for directory in ('scripts','scripts/terminal_colors','scripts/tests'):
        for path in sorted((ROOT / directory).glob('test_*.py')):
            source = path.read_text()
            if re.search(r'class \w+\(unittest.TestCase\)', source):
                commands.append((sys.executable, '-m', 'unittest', 'discover', '-v', '-s', directory, '-p', path.name))
    results = [run(command, args.output / ('host-' + str(index) + '.log'), timeout=1800 if index == 0 else 180) for index, command in enumerate(commands)]
    host = dict(commands=[dict(command=list(command),exitCode=code,log=str(args.output/('host-'+str(index)+'.log')))
                          for index,(command,code) in enumerate(zip(commands,results))], junit=[])
    for variant in ('Playstore','Fdroid'):
        for path in sorted((ROOT/f'app/build/test-results/test{variant}DebugUnitTest').glob('TEST-*.xml')):
            suite=ET.parse(path).getroot()
            for case in suite.findall('testcase'):
                skipped=case.find('skipped');failure=case.find('failure');error=case.find('error')
                host['junit'].append(dict(variant=variant,className=suite.get('name'),test=case.get('name'),
                    status='failed' if failure is not None or error is not None else 'skipped' if skipped is not None else 'passed',
                    reason=skipped.get('message','') if skipped is not None else ''))
    host['counts']=dict(Counter(test['status'] for test in host['junit']))
    (args.output/'host-report.json').write_text(json.dumps(host,indent=2))
    return int(any(results))


if __name__ == '__main__':
    raise SystemExit(main())
