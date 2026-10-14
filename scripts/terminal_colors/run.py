"""Run and resume the actual package/pane matrix on an explicitly selected device."""
import argparse
import hashlib
import json
import subprocess
import sys
import time
import shutil
import zipfile
import fcntl
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from generate import compact_artifact, featured_ids

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
PACKAGE = "com.dvil.tui_renewed.palettedebug"
CLASS = "ohi.andre.consolelauncher.managers.settings.PaletteReadabilityTest"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--themes", help="Comma-separated palette ids; default the 21 featured palettes")
    parser.add_argument("--surfaces", help="Comma-separated surface ids; default all")
    parser.add_argument("--phone", action="store_true")
    parser.add_argument("--install", action="store_true")
    parser.add_argument('--app-apk', type=Path, help='Frozen app APK for resuming a recorded build')
    parser.add_argument('--test-apk', type=Path, help='Frozen instrumentation APK for resuming a recorded build')
    parser.add_argument("--configuration", choices=["baseline", "landscape", "large-text", "wallpaper", "frames"], default="baseline")
    args = parser.parse_args()
    lock = open(Path(tempfile.gettempdir()) / ('retui-readability-' + hashlib.sha256(args.serial.encode()).hexdigest()[:12] + '.lock'), 'a')
    try:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        parser.error('A readability run already owns this device; wait for it to finish before starting another')
    args.output.mkdir(parents=True, exist_ok=True)
    adb = ["adb", "-s", args.serial]
    def command(*parts, timeout=180):
        for attempt in range(4):
            result = subprocess.run(adb + list(parts), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
            if result.returncode == 0:
                return result.stdout
            safe_retry = parts[0] == 'pull' or (parts[0] == 'shell' and 'instrument' not in parts) or any('#restartReadback' in part for part in parts)
            if safe_retry and 'device offline' in result.stdout.lower() and attempt < 3:
                time.sleep(2 ** attempt)
                continue
            raise subprocess.CalledProcessError(result.returncode, adb + list(parts), output=result.stdout)
    emulator = command("shell", "getprop", "ro.hardware").strip() in ("ranchu", "goldfish")
    if not emulator and not args.phone:
        parser.error("A physical device requires --phone; use the dedicated emulator for the full matrix")
    if args.phone and (not args.themes or not args.surfaces or args.configuration != "baseline"):
        parser.error("Phone sample must specify themes and surfaces; stress fixtures are emulator-only")
    apk = args.app_apk or REPO / "app/build/outputs/apk/playstore/debug/app-playstore-debug.apk"
    test_apk = args.test_apk or REPO / "app/build/outputs/apk/androidTest/playstore/debug/app-playstore-debug-androidTest.apk"
    for path in (apk, test_apk):
        if not path.is_file():
            parser.error(f"Build APK missing: {path}")
        metadata = json.loads((path.parent / 'output-metadata.json').read_text())
        expected_package = PACKAGE + ('.test' if path == test_apk else '')
        if metadata['applicationId'] != expected_package:
            parser.error('Build with -PfetchDebug=true -PpaletteDebug=true before installing; package isolation is required')
    build = sha(apk) + ":" + sha(test_apk)
    if args.install:
        for path in (apk, test_apk):
            command("install", "-r", str(path), timeout=180)
    for name, path in ((PACKAGE, apk), (PACKAGE + '.test', test_apk)):
        installed = command('shell', 'pm', 'path', '--user', '0', name).strip().removeprefix('package:')
        if not installed.startswith('/data/app/') or command('shell', 'sha256sum', installed).split()[0] != sha(path):
            parser.error('Installed APK differs from the build; rerun with --install')
    with zipfile.ZipFile(test_apk) as archive:
        catalog = json.loads(archive.read('assets/terminal-colors/catalog.json'))['themes']
    selected = set(args.themes.split(',')) if args.themes else set(featured_ids())
    unknown = selected - {t["id"] for t in catalog}
    if unknown:
        parser.error(f"Unknown themes: {unknown}")
    metadata = {"serial": args.serial, "kind": "emulator" if emulator else "phone", "build": build,
                "model": command("shell", "getprop", "ro.product.model").strip(),
                "api": command("shell", "getprop", "ro.build.version.sdk").strip(),
                "configuration": args.configuration, "themes": sorted(selected), "surfaces": args.surfaces,
                "recordedAt": datetime.now(timezone.utc).isoformat(),
                "resolution": command('shell', 'wm', 'size').strip(), "density": command('shell', 'wm', 'density').strip(),
                "androidVersion": command('shell', 'getprop', 'ro.build.version.release').strip()}
    (args.output / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
    remote = f"/sdcard/Android/data/{PACKAGE}/files/palette-readability"
    if emulator:
        command("shell", "settings", "put", "system", "font_scale", "1.5" if args.configuration == "large-text" else "1.0")
    for index, theme in enumerate(t for t in catalog if t["id"] in selected):
        slug = theme["id"]
        record = args.output / (slug + ".json")
        if record.exists():
            previous = json.loads(record.read_text())
            if previous.get("build") == build and previous.get("packageSha256") == theme["sha256"] and previous.get("requestedSurfaces") == args.surfaces and previous.get("configuration") == args.configuration and previous.get("processRestart") == ("pass" if emulator else "untested") and previous.get("executionComplete") and not any(r.get('status') == 'blocked' for r in previous.get('results', [])):
                print(f"RESUME {slug}", flush=True)
                continue
        values = ["shell", "am", "instrument", "-w", "-r", "-e", "readabilityEnabled", "true",
                  "-e", "themes", slug, "-e", "buildSha256", build,
                  "-e", "configuration", args.configuration,
                  "-e", "class", CLASS + "#renderPaletteBatch"]
        if args.surfaces:
            values += ["-e", "surfaces", args.surfaces]
        values += ["-e", "readabilityPhone" if args.phone else "keepPalette", "true", PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"]
        start = time.monotonic()
        try:
            if shutil.disk_usage(args.output).free < 3 * 1024**3:
                raise RuntimeError('Less than 3 GiB free; free disk space before resuming the matrix')
            logfile = args.output / (slug + ".log")
            with logfile.open('w') as stream:
                subprocess.run(adb + values, stdout=stream, stderr=subprocess.STDOUT, timeout=900, check=True)
            log = logfile.read_text()
            if "FAILURES!!!" in log or "Process crashed" in log or "INSTRUMENTATION_FAILED" in log or "OK (1 test)" not in log:
                raise RuntimeError("Instrumentation failed; see theme log")
            if emulator:
                command("shell", "am", "force-stop", PACKAGE)
                restart = command("shell", "am", "instrument", "-w", "-r", "-e", "readabilityEnabled", "true", "-e", "themes", slug,
                                  "-e", "class", CLASS + "#restartReadback", PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner")
                (args.output / (slug + "-restart.log")).write_text(restart)
                if "OK (1 test)" not in restart:
                    raise RuntimeError("Process restart readback failed")
            command("pull", remote + "/" + slug + ".json", str(record))
            data = json.loads(record.read_text())
            if any(result.get('screenshot') for result in data.get('results', [])):
                temporary = args.output / ('.pull-' + slug)
                if temporary.exists(): shutil.rmtree(temporary)
                command("pull", remote + "/" + slug, str(temporary), timeout=300)
                folder = args.output / slug
                if folder.exists(): shutil.rmtree(folder)
                temporary.rename(folder)
                identical = {}
                for image in folder.rglob('*.png'):
                    checksum = sha(image)
                    if checksum in identical:
                        image.unlink(); image.hardlink_to(identical[checksum])
                    else:
                        identical[checksum] = image
                for partial_record in folder.rglob('evidence.json'):
                    partial_record.unlink()  # Complete theme JSON already contains every scene.
                command('shell', 'rm', '-rf', remote + '/' + slug)
            data.update({"requestedSurfaces": args.surfaces, "configuration": args.configuration,
                         "executionComplete": True, "elapsedSeconds": round(time.monotonic() - start, 2)})
            record.write_text(json.dumps(data) + "\n")
            compact_artifact(record)
            print(f"DONE {index + 1}/{len(selected)} {slug} {data['elapsedSeconds']}s", flush=True)
        except (subprocess.SubprocessError, RuntimeError, OSError) as error:
            partial = []
            try:
                command('pull', remote + '/' + slug, str(args.output / slug), timeout=300)
                for evidence in (args.output / slug).rglob('evidence.json'):
                    partial.extend(json.loads(evidence.read_text()))
                partial = [r for r in partial if r.get('build') == build and r.get('packageSha256') == theme['sha256']]
            except (subprocess.SubprocessError, OSError, ValueError):
                pass
            record.write_text(json.dumps({"theme": slug, "packageSha256": theme["sha256"], "build": build,
                "status": "blocked", "reason": str(error), "results": partial, "executionComplete": False,
                "requestedSurfaces": args.surfaces, "configuration": args.configuration}) + "\n")
            print(f"BLOCKED {slug}: {error}", flush=True)


if __name__ == "__main__":
    main()
