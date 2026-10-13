#!/usr/bin/env python3
"""Summarize measured phone results; keep device drain and package CPU separate."""
import argparse
import json
import math
from pathlib import Path
import re
import statistics


def duration_seconds(value):
    units = {'d': 86400, 'h': 3600, 'm': 60, 's': 1, 'ms': .001}
    return sum(int(number) * units[unit] for number, unit in re.findall(r'(\d+)(ms|d|h|m|s)', value))


def uid_cpu(raw, uid='u0a1000'):
    section = raw.split('\n  ' + uid + ':\n', 1)[1]
    match = re.search(r'Total cpu time: u=(.*?) s=(.*?)\s*\n', section)
    return duration_seconds(match[1]) + duration_seconds(match[2])


def battery(raw):
    return {key: int(value) for key, value in re.findall(
        r'^\s*(Charge counter|level|temperature):\s*(-?\d+)', raw, re.M)}


def latency(samples):
    ordered = sorted(samples)
    return dict(samples=len(ordered), median_ms=round(statistics.median(ordered), 2),
                p95_ms=round(ordered[math.ceil(.95 * len(ordered)) - 1], 2), max_ms=round(max(ordered), 2))


def phase_summary(rows, name):
    selected = [row for row in rows if row['phase'] == name and row['battery'] and row['charging'] is False]
    if len(selected) < 2:
        return None
    first, last = selected[0], selected[-1]
    dt = last['monotonic_s'] - first['monotonic_s']
    # CPU only uses valid consecutive samples from the same process lifetime.
    cpu_seconds = cpu_wall = 0
    for left, right in zip(selected, selected[1:]):
        span = right['monotonic_s'] - left['monotonic_s']
        if span > 45:
            continue
        previous = {(proc['pid'], proc['start_ticks']): proc['ticks'] for proc in left['processes']}
        matched = [proc for proc in right['processes'] if (proc['pid'], proc['start_ticks']) in previous]
        if matched:
            cpu_seconds += sum(proc['ticks'] - previous[(proc['pid'], proc['start_ticks'])] for proc in matched) / 100
            cpu_wall += span
    intervals = [right['monotonic_s'] - left['monotonic_s'] for left, right in zip(selected, selected[1:])]
    return dict(duration_minutes=round(dt / 60, 2), samples=len(selected),
                device_counter_drop_mAh=round((first['battery']['Charge counter'] - last['battery']['Charge counter']) / 1000, 2),
                level_before=first['battery']['level'], level_after=last['battery']['level'],
                temperature_before_c=first['battery']['temperature'] / 10, temperature_after_c=last['battery']['temperature'] / 10,
                launcher_cpu_percent_one_core=round(cpu_seconds * 100 / cpu_wall, 3) if cpu_wall else None,
                cpu_matched_minutes=round(cpu_wall / 60, 2), sample_gap_seconds=round(max(intervals), 2),
                awake_samples=sum(row['awake'] is True for row in selected),
                foreground_components=sorted(set(row['foreground'].split(' u0 ', 1)[-1].split(' ', 1)[0]
                                               for row in selected if row['foreground'])))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    root = args.directory
    phone = json.loads((root / 'phone-results.json').read_text())
    result = dict(device='Vivo I2220, Android API 36', launcher_version_code=501,
                  branch='dvil/termux-streaming-prototype', checks=phone['checks'],
                  latency={mode: latency([row['latency_ns'] / 1e6 for row in phone['measurements'] if row['mode'] == mode])
                           for mode in ('snapshot', 'stream')}, idle={}, launcher_phases={})
    for mode in ('snapshot', 'stream'):
        idle = json.loads((root / ('idle-' + mode + '.json')).read_text())
        before, after = battery(idle['battery_before']), battery(idle['battery_after'])
        cpu = uid_cpu(idle['batterystats_after']) - uid_cpu(idle['batterystats_before'])
        result['idle'][mode] = dict(duration_seconds=idle['duration_ms'] / 1000, termux_cpu_seconds=round(cpu, 3),
            termux_cpu_percent_one_core=round(cpu * 100000 / idle['duration_ms'], 3),
            device_counter_drop_mAh=(before['Charge counter'] - after['Charge counter']) / 1000,
            level_before=before['level'], level_after=after['level'],
            temperature_before_c=before['temperature'] / 10, temperature_after_c=after['temperature'] / 10)
    rows = [json.loads(line) for line in (root / 'battery-samples.jsonl').read_text().splitlines()]
    for phase in ('launcher_home_controlled', 'launcher_home_clean_repeat', 'launcher_hidden_settings_controlled',
                  'launcher_hidden_settings_after_termux_started', 'launcher_locked_controlled'):
        summary = phase_summary(rows, phase)
        if summary:
            result['launcher_phases'][phase] = summary
    result['telemetry_errors'] = sum(bool(row.get('error')) for row in rows)
    start, end = root / 'battery-lock-start.txt', root / 'battery-lock-end.txt'
    if start.exists() and end.exists():
        before, after = battery(start.read_text()), battery(end.read_text())
        result['locked_endpoints'] = dict(
            duration_minutes=round((end.stat().st_mtime - start.stat().st_mtime) / 60, 2),
            device_counter_drop_mAh=(before['Charge counter'] - after['Charge counter']) / 1000,
            level_before=before['level'], level_after=after['level'])
    result['limits'] = [
        'Latency is committed input/send to completion of first grid draw, not physical tap to presented pixels.',
        'The same existing grid, phone font, font size and theme palette are used in both prototype modes.',
        'Idle transport runs last three minutes each; CPU is package user+kernel time from Android BatteryStats.',
        'Charge-counter changes are measured for the whole phone, not uniquely attributable to a package.',
        'Launcher PID CPU includes its live wallpaper; no static-wallpaper control was applied.',
        'Wireless debugging and telemetry remain active; locked phone may be dozing with AOD rather than deeply asleep.',
        'No overnight endurance claim, production integration or complete terminal-state recovery claim is made.']
    (root / 'phone-summary.json').write_text(json.dumps(result, indent=2) + '\n')
    text = ['# Phone prototype results — 13 October 2026', '',
        'The isolated prototype passed the real-device suite on Vivo I2220 (Android API 36). '
        'It uses the launcher’s existing TerminalGridView, the configured JetBrainsMonoNL Nerd Font and its effective 12 sp size. '
        'The installed launcher remains version 501, with its existing data and live wallpaper.', '',
        '| Input to first grid draw | Samples | Median | p95 | Maximum |', '|---|---:|---:|---:|---:|']
    for mode, data in result['latency'].items():
        text.append(f"| {mode} | {data['samples']} | {data['median_ms']} ms | {data['p95_ms']} ms | {data['max_ms']} ms |")
    text += ['', 'Two quiet panes were held for three minutes per transport, with the same screen brightness and content.', '',
             '| Transport | Termux CPU time | Average CPU, one core | Whole-phone counter drop |', '|---|---:|---:|---:|']
    for mode, data in result['idle'].items():
        text.append(f"| {mode} | {data['termux_cpu_seconds']} s | {data['termux_cpu_percent_one_core']}% | {data['device_counter_drop_mAh']} mAh |")
    text += ['', 'The package CPU comparison includes the relay and tmux/helper children. '
        '123% of one core means roughly 1.23 cores of work, not 123% of the whole eight-core phone. '
        'The direct counter comparison is a short whole-device observation; it is not a calibrated long-term battery-saving percentage.', '',
        '| Installed launcher state | Minutes | Launcher CPU, one core | Device counter drop | Battery level |', '|---|---:|---:|---:|---:|']
    for name, data in result['launcher_phases'].items():
        label = name.removeprefix('launcher_').replace('_', ' ')
        text.append(f"| {label} | {data['duration_minutes']} | {data['launcher_cpu_percent_one_core']}% | {data['device_counter_drop_mAh']} mAh | {data['level_before']} → {data['level_after']}% |")
    if 'locked_endpoints' in result:
        data = result['locked_endpoints']
        text += ['', f"The complete locked interval lasted {data['duration_minutes']} minutes, with a whole-phone "
                 f"counter drop of {data['device_counter_drop_mAh']} mAh and level {data['level_before']} → {data['level_after']}%. "
                 'Its valid CPU sampling window is shown separately in the table; no CPU value is inferred across the missing samples.']
    text += ['', 'The active home wallpaper belongs to com.dvil.tui_renewed, so its work is included in launcher process CPU. '
        'These discharge numbers include the display, radio, other apps and debugging. '
        'The repeat Home run corroborates the first Home CPU result. Short covered-screen samples characterize CPU and should not be extrapolated to daily discharge.', '',
        'Device checks that passed:', '']
    text += ['- ' + item for item in result['checks']]
    text += ['', 'Measurement limits:', ''] + ['- ' + item for item in result['limits']]
    text += ['', 'Raw evidence is in this Downloads directory: phone-results.json, idle-snapshot.json, idle-stream.json, '
        'battery-samples.jsonl, instrumentation-final.log and the rendered screenshots. '
        'One earlier wireless timeout stopped the initial sampler. The restarted sampler initially rejected locked readings because '
        'the foreground-activity lookup had no match; that collection bug was fixed and locked sampling restarted. '
        'Invalid rows and gaps are excluded from matched CPU calculations. The full locked battery interval uses saved endpoint snapshots.', '',
        'Cleanup: the original screen brightness, automatic-brightness mode and timeout were restored. '
        'The private test relay and its own tmux jobs were stopped. The original launcher and user Termux jobs were not replaced or cleared.', '']
    (root / 'PHONE_RESULTS.md').write_text('\n'.join(text))
    print(json.dumps({key: result[key] for key in ('latency', 'idle', 'launcher_phases')}, indent=2))


if __name__ == '__main__':
    assert abs(duration_seconds('1m 33s 659ms') - 93.659) < .0001
    assert abs(uid_cpu('\n  u0a1000:\n    Total cpu time: u=1s 500ms s=2s 250ms \n') - 3.75) < .0001
    main()
