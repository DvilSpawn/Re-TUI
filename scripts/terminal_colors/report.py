"""Produce a fully offline HTML matrix, per-theme detail, JSON/CSV and marked crops."""
import argparse
import csv
import html
import json
import shutil
import os
import subprocess
import sys
import re
from pathlib import Path

from generate import HERE, contrast, compact_artifact

STYLE = """body{font:15px system-ui;background:#11151b;color:#e6edf3;margin:24px}a{color:#79c0ff}input,select,button{font:inherit;padding:9px;margin:6px;background:#202936;color:inherit;border:1px solid #718096}table{border-collapse:collapse;font-size:12px}td,th{border:1px solid #394658;padding:7px;min-width:65px}th{position:sticky;top:0;background:#202936}.matrix{overflow:auto;max-height:75vh}.fail{background:#681d29}.blocked{background:#6b4112}.untested{background:#283341}.manual{background:#164d61}.pass{background:#175f36}.shot{max-width:360px;max-height:650px}.crop{max-width:540px}details{margin:12px 0;padding:10px;border:1px solid #394658}.swatch{display:inline-block;width:24px;height:20px}.small{color:#b1bdcd}code{overflow-wrap:anywhere}h1{font-size:25px}p{max-width:1000px}td:first-child{position:sticky;left:0;background:#202936}pre{white-space:pre-wrap}label{display:inline-block}img{height:auto}section{margin:24px 0}"""


def copy_artifact(source, target):
    if target.exists():
        if os.path.samefile(source, target): return
        target.unlink()
    try: os.link(source, target)
    except OSError: shutil.copyfile(source, target)


def page(title, body, script=""):
    return f'<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{html.escape(title)}</title><style>{STYLE}</style><body>{body}<script>{script}</script></body></html>'


def esc(value):
    return html.escape(str(value))


def state_class(status):
    return 'manual' if status == 'manual review required' else status


def status_for(records, surface, evidence):
    found = [r for r in records if r.get('surface') == surface]
    if not found:
        return 'untested'
    if any(r.get('status') == 'blocked' for r in found):
        return 'blocked'
    if any(not r.get('samples') for r in found):
        return 'blocked'
    if any(not (evidence / r.get('screenshot', 'missing')).is_file() for r in found):
        return 'blocked'
    if any(not (evidence / crop).is_file() for r in found for state in r.get('controlStates', []) for crop in state.get('crops', [])):
        return 'blocked'
    samples = [s for r in found for s in r.get('samples', [])]
    samples += [s for r in found for states in r.get('controlStates', []) for s in states.get('samples', [])]
    for sample in samples:
        if sample.get('status') == 'fail':
            return 'fail'
        if sample.get('status') == 'pass' and sample.get('composited') and sample.get('background'):
            # Independent arithmetic check of exported actual-renderer colors.
            ratio = contrast('#' + sample['composited'][-6:], '#' + sample['background'][-6:])
            if ratio < sample['threshold']:
                return 'fail'
    return 'manual review required'


def marked(evidence, record, output):
    from PIL import Image, ImageDraw
    source = evidence / record.get('screenshot', 'missing')
    if not source.is_file():
        return []
    failures = [s for s in record.get('samples', []) if s.get('status') == 'fail' and s.get('bounds')]
    if not failures:
        return []
    image = Image.open(source).convert('RGB')
    draw = ImageDraw.Draw(image)
    files = []
    for index, sample in enumerate(failures[:12]):
        x1, y1, x2, y2 = sample['bounds']
        origin_x, origin_y = record.get('screenshotOrigin', [0, 0])
        x1 -= origin_x; x2 -= origin_x; y1 -= origin_y; y2 -= origin_y
        x1 = max(0, x1); y1 = max(0, y1); x2 = min(image.width, x2); y2 = min(image.height, y2)
        if x2 <= x1 or y2 <= y1:
            continue
        box = (max(0, x1 - 8), max(0, y1 - 8), min(image.width, x2 + 8), min(image.height, y2 + 8))
        crop = image.crop(box)
        name = source.stem + f'-issue-{index}.png'
        path = output / name
        crop.save(path)
        files.append((name, sample))
        draw.rectangle((x1, y1, x2 - 1, y2 - 1), outline='#ff3355', width=3)
    image.save(output / (source.stem + '-marked.png'))
    return files


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--site', type=Path)
    parser.add_argument('--sync-previews', action='store_true')
    parser.add_argument('--related-run', type=Path, action='append', default=[], help='Include a separately labeled phone/stress run')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    registry = json.loads((HERE / 'coverage.json').read_text())
    palettes = json.loads((HERE / 'generated/palettes.json').read_text())
    definitions = registry['surfaces']
    rows, csv_rows = [], []
    build = json.loads((args.evidence / 'run.json').read_text()) if (args.evidence / 'run.json').exists() else {}
    if build.get('kind') == 'phone' or build.get('configuration', 'baseline') != 'baseline':
        palettes = [p for p in palettes if p['id'] in build.get('themes', [])]
    if build.get('surfaces'):
        selected = set((build.get('surfaces') or '').split(','))
        definitions = [s for s in definitions if s['id'] in selected]
    summaries = []
    for palette in palettes:
        slug = palette['id']
        file = args.evidence / (slug + '.json')
        record = json.loads(file.read_text()) if file.exists() else {'results': []}
        valid = record.get('packageSha256') == palette['sha256'] and record.get('build') == build.get('build')
        if not valid:
            record = {'results': []}
        records = record.get('results', []) if valid else []
        statuses = {s['id']: status_for(records, s['id'], args.evidence) for s in definitions}
        summary = {'id': slug, 'name': palette['name'], 'mode': palette['mode'], 'statuses': statuses,
                   'adjustmentCount': len(palette['adjustments']), 'packageSha256': palette['sha256'],
                   'numericChecks': len(palette['checks']), 'adjustedMinimum': min(c['ratio'] for c in palette['checks']),
                   'importWithoutApplication': record.get('importWithoutApplication', 'untested'),
                   'applicationReadback': record.get('applicationReadback', 'untested'),
                   'reloadReadback': record.get('reloadReadback', 'untested'), 'processRestart': record.get('processRestart', 'untested')}
        summary['executionComplete'] = record.get('executionComplete', False)
        summary['reason'] = record.get('reason', '')
        summaries.append(summary)
        cells = ''.join(f'<td class="{state_class(status)}" title="{esc(status)}"><a href="themes/{slug}.html#{surface}">{esc({"manual review required":"review","untested":"—"}.get(status,status))}</a></td>' for surface, status in statuses.items())
        rows.append(f'<tr data-name="{esc(palette["name"].lower())}" data-mode="{palette["mode"]}" data-status="{esc(" ".join(set(statuses.values())))}"><td><a href="themes/{slug}.html">{esc(palette["name"])}</a></td>{cells}</tr>')
        for surface, status in statuses.items():
            matches = [r for r in records if r.get('surface') == surface]
            samples = [sample for r in matches for sample in r.get('samples', []) if sample.get('ratio') is not None]
            samples += [sample for r in matches for state in r.get('controlStates', []) for sample in state.get('samples', []) if sample.get('ratio') is not None]
            csv_rows.append({'theme': slug, 'surface': surface, 'status': status,
                             'worstRequiredRatio': min((s['ratio'] for s in samples if s.get('status') in ['pass','fail']), default=''),
                             'worstDisabledRatio': min((s['ratio'] for s in samples if s.get('state') == 'disabled'), default=''),
                             'worstAuthoredContentRatio': min((s['ratio'] for s in samples if s.get('reason') == 'Content-authored truecolor/256 color'), default=''),
                             'packageSha256': palette['sha256'], 'build': record.get('build', ''), 'screenshots': len(matches)})
        detail = args.output / 'themes' / (slug + '.html'); detail.parent.mkdir(exist_ok=True)
        if valid:
            copy_artifact(file, detail.parent / (slug + '.json'))
        heading = f'<a href="../index.html">← Full matrix</a><h1>{esc(palette["name"])}</h1><p>{palette["mode"]} · {len(palette["adjustments"])} adjusted roles · <a href="{esc(palette["sourceUrl"])}">Original palette</a></p><p>Package checksum: <code>{palette["sha256"]}</code></p><p>Import: {summary["importWithoutApplication"]}; apply: {summary["applicationReadback"]}; reload: {summary["reloadReadback"]}; process restart: {summary["processRestart"]}.</p>'
        adjustment = '<details><summary>Original → adjusted colors</summary><table><tr><th>Role</th><th>Original</th><th>Adjusted</th></tr>' + ''.join(f'<tr><td>{esc(c["role"])}</td><td><span class="swatch" style="background:{esc(c["original"])}"></span>{esc(c["original"])}</td><td><span class="swatch" style="background:{esc(c["adjusted"])}"></span>{esc(c["adjusted"])}</td></tr>' for c in palette['adjustments']) + '</table></details>'
        attribution = palette.get('attribution', {}).get('text', palette['source'].get('attribution', 'See pinned credits'))
        heading += '<p>Author attribution: ' + esc(re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', attribution)) + '</p>'
        if valid:
            heading += f'<p><a href="{slug}.json">Complete renderer measurements (JSON)</a></p>'
        sections = []
        for surface, status in statuses.items():
            body = f'<section id="{surface}"><h2>{esc(surface)} <span class="{state_class(status)}">{esc(status)}</span></h2>'
            found = [r for r in records if r.get('surface') == surface]
            if not found:
                body += '<p>No matching capture for this package and build. This surface is untested.</p>'
            for result in found:
                if result.get('reason'):
                    body += f'<p>{esc(result["reason"])}</p>'
                shot = args.evidence / result.get('screenshot', 'missing')
                if shot.is_file():
                    relative = slug + '/' + result['surface'] + '/' + shot.name
                    target = detail.parent / relative; target.parent.mkdir(parents=True, exist_ok=True)
                    if not target.exists() or target.stat().st_size != shot.stat().st_size: copy_artifact(shot, target)
                    body += f'<details><summary>{esc(result.get("state","normal"))} · {len(result.get("samples",[]))} measured elements · {esc(result.get("captureMethod", "UiAutomation screenshot"))}</summary><a href="{relative}"><img class="shot" src="{relative}" alt="{esc(surface)} actual launcher screenshot"></a>'
                    for name, sample in marked(args.evidence, result, target.parent):
                        body += f'<p>{esc(sample["id"])}: {sample["ratio"]:.3f}:1; target {sample["threshold"]}:1 · {esc(sample["text"])}</p><img class="crop" src="{slug}/{surface}/{name}" alt="Readability issue crop">'
                    for state in result.get('controlStates', []):
                        body += f'<h3>{esc(state["state"])} control renders</h3>'
                        for relative_crop in state.get('crops', []):
                            crop_source = args.evidence / relative_crop
                            if crop_source.is_file():
                                crop_target = target.parent / crop_source.name
                                copy_artifact(crop_source, crop_target)
                                body += f'<img class="crop" src="{slug}/{surface}/{crop_source.name}" alt="{esc(state["state"])} actual control render">'
                    body += '<table><tr><th>Element / state</th><th>Text</th><th>Colors</th><th>Expected role / application</th><th>Ratio / target</th><th>Status</th></tr>'
                    samples = result.get('samples', []) + [s for state in result.get('controlStates', []) for s in state.get('samples', [])]
                    for s in sorted(samples, key=lambda s: (s.get('status') != 'fail', s.get('ratio', 99))):
                        body += f'<tr><td>{esc(s.get("id",""))} / {esc(s.get("state",""))}</td><td>{esc(s.get("text",""))}</td><td>{esc(s.get("foreground",""))} on {esc(s.get("background",""))}<br>Composited: {esc(s.get("composited", ""))}</td><td>{esc(s.get("expectedRole") or ", ".join(s.get("roles", [])))}<br>Expected: {esc(s.get("expectedColor") or "role ownership needs review")}<br>{esc(s.get("application", ""))}<br>{esc(s.get("reason", ""))}</td><td>{s.get("ratio",0):.3f} / {s.get("threshold",0)}</td><td class="{state_class(s.get("status","untested"))}">{esc(s.get("status",""))}</td></tr>'
                    body += '</table></details>'
            body += '</section>'; sections.append(body)
        detail.write_text(page(palette['name'], heading + adjustment + ''.join(sections)))
        compact_artifact(detail)
    counts = {status: sum(status in s['statuses'].values() for s in summaries) for status in ['fail','blocked','untested','manual review required','pass']}
    related = []
    related_failures = set()
    for evidence in args.related_run:
        target = args.output / 'runs' / evidence.name
        subprocess.run([sys.executable, __file__, '--evidence', str(evidence), '--output', str(target)], check=True)
        dataset = json.loads((target / 'results.json').read_text())
        related_failures.update(t['id'] for t in dataset['themes'] if 'fail' in t['statuses'].values())
        related.append({'name': evidence.name, 'url': 'runs/' + evidence.name + '/index.html', 'run': dataset['run'], 'counts': dataset['counts']})
    headers = ''.join(f'<th>{esc(s["id"])}</th>' for s in definitions)
    scope = 'Physical-device representative sample' if build.get('kind') == 'phone' else ('Emulator baseline matrix' if build.get('configuration', 'baseline') == 'baseline' else 'Emulator ' + build['configuration'] + ' representative sample')
    body = f'<h1>Re:TUI terminal palette readability report</h1><p>{scope}: {len(palettes)} palettes × {len(definitions)} registered surfaces. Numeric package checks and actual renderer evidence are separate. Screenshots require your visual review before a theme is verified.</p><p>{esc(str(counts))}</p><p class="small">Run: {esc(json.dumps(build))}</p><label>Theme <input id="q" type="search" placeholder="Search themes"></label><label>Appearance <select id="mode"><option value="all">All</option><option>dark</option><option>light</option></select></label><label>Result <select id="status"><option value="all">All</option><option>fail</option><option>blocked</option><option>untested</option><option>manual review required</option></select></label><p>Red: readability failure. Amber: blocked/missing evidence. Blue: measurements captured, visual review required. Grey: untested. Disabled controls and content-authored truecolor are reported separately.</p><p><a href="results.json">JSON summary</a> · <a href="results.csv">CSV matrix</a></p><div class="matrix"><table><thead><tr><th>Theme</th>{headers}</tr></thead><tbody>{"".join(rows)}</tbody></table></div>'
    if related:
        body = '<nav>Separate configurations: ' + ' · '.join(f'<a href="{esc(r["url"])}">{esc(r["name"])}</a>' for r in related) + '</nav>' + body
    body += '<p class="small">Background measurements composite drawable-center samples through the view hierarchy. Nonuniform artwork, native selection overlays, untinted controls, clipping and overlap require screenshot review.</p>'
    if (args.output / 'verification.json').exists():
        body = '<p><a href="verification.json">Validation results, logs and limitations</a></p>' + body
    script = """const q=document.getElementById('q'),mode=document.getElementById('mode'),status=document.getElementById('status');function filter(){for(const row of document.querySelectorAll('tbody tr'))row.hidden=!(row.dataset.name.includes(q.value.trim().toLowerCase())&&(mode.value==='all'||row.dataset.mode===mode.value)&&(status.value==='all'||row.dataset.status.includes(status.value)));}q.oninput=filter;mode.onchange=filter;status.onchange=filter;"""
    (args.output / 'index.html').write_text(page('Palette readability matrix', body, script))
    (args.output / 'results.json').write_text(json.dumps({'schema':1,'run':build,'counts':counts,'themes':summaries,'relatedRuns':related},indent=2)+'\n')
    with (args.output / 'results.csv').open('w',newline='') as file:
        writer=csv.DictWriter(file,fieldnames=list(csv_rows[0]));writer.writeheader();writer.writerows(csv_rows)
    if args.sync_previews:
        assert args.site
        folder=args.site/'docs/terminal-colors';previews=folder/'previews';previews.mkdir(exist_ok=True)
        catalog=json.loads((folder/'catalog.json').read_text())
        by_id={s['id']:s for s in summaries}
        for theme in catalog['themes']:
            shot=args.evidence/theme['id']/'home'/'normal.png'
            if shot.is_file() and by_id[theme['id']]['statuses']['home'] not in ['untested', 'blocked']:
                shutil.copyfile(shot,previews/(theme['id']+'.png'));theme['screenshot']='previews/'+theme['id']+'.png'
            else:
                theme['screenshot'] = None
            statuses=set(by_id[theme['id']]['statuses'].values())
            theme['verification']='fail' if 'fail' in statuses else 'blocked' if 'blocked' in statuses else 'untested' if 'untested' in statuses else 'manual review required'
            if theme['id'] in related_failures: theme['verification'] = 'fail'
        (folder/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(counts))


if __name__=='__main__':
    main()
