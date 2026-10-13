#!/usr/bin/env python3
"""Read real discharge and launcher CPU without resetting battery history."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time


def parse(raw):
    battery = {key: int(value) for key, value in re.findall(
        r'^\s*(Charge counter|level|voltage|temperature|status):\s*(-?\d+)', raw, re.M)}
    processes = []
    for line in raw.splitlines():
        if re.match(r'^\d+ \(', line):
            fields = line[line.rfind(')') + 2:].split()
            processes.append(dict(pid=int(line.split()[0]), ticks=int(fields[11]) + int(fields[12]), start_ticks=int(fields[19])))
    return dict(battery=battery, processes=processes, charging=bool(re.search(r'(?:AC|USB|Wireless|Dock) powered: true', raw)),
                awake='mWakefulness=Awake' in raw,
                foreground=next((line.strip() for line in raw.splitlines() if 'topResumedActivity' in line), None))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    parser.add_argument('--duration', type=int, default=3600)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    command = 'dumpsys battery; pidof com.dvil.tui_renewed; for pid in $(pidof com.dvil.tui_renewed); do cat /proc/$pid/stat; done; dumpsys power | grep mWakefulness=; dumpsys activity activities | grep topResumedActivity'
    start = time.monotonic()
    deadline = start + args.duration
    with (args.output_dir / 'battery-samples.jsonl').open('a') as output:
        while time.monotonic() < deadline and not (args.output_dir / 'stop-telemetry').exists():
            record = dict(monotonic_s=time.monotonic(), unix_s=time.time(), elapsed_s=time.monotonic() - start,
                          phase=(args.output_dir / 'phase.txt').read_text().strip())
            try:
                result = subprocess.run(['adb', '-s', args.serial, 'shell', command], capture_output=True, text=True, timeout=20)
                # The final foreground grep legitimately exits 1 while locked.
                if 'Charge counter:' not in result.stdout:
                    raise RuntimeError(result.stderr.strip() or 'Incomplete battery sample')
                record.update(parse(result.stdout)); record['error'] = result.stderr.strip() or None
            except (subprocess.TimeoutExpired, RuntimeError) as failure:
                record.update(battery={}, processes=[], charging=None, awake=None, foreground=None, error=str(failure))
            output.write(json.dumps(record) + '\n'); output.flush()
            print(json.dumps({key: record[key] for key in ('phase', 'elapsed_s', 'battery', 'error')}), flush=True)
            time.sleep(30)


if __name__ == '__main__':
    assert parse('  Charge counter: 1234000\n  level: 50\nUSB powered: true\n')['charging']
    assert not parse('AC powered: false\nUSB powered: false\n')['charging']
    main()
