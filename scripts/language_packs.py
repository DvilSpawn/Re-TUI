#!/usr/bin/env python3
"""Export the Launcher template/catalog or build a data-only language pack (stdlib only)."""
import argparse
import json
import re
import zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
from localization_audit import placeholders

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/localization'
GUIDE = '''# Translate Re:TUI Launcher

Edit manifest.json: choose a unique id, languageTag, English/native language names,
version (positive integer), and direction (ltr or rtl). Keep schema and target.
Translate strings.xml. Its contents are plain UTF-8 XML, NOT Android string escaping:
use literal apostrophes, quotes and line breaks; escape XML & and < as &amp; and &lt;.
Keep resource names and numbered format arguments (%1$s, %2$d) unchanged.
For plurals keep other and add your language's zero/one/two/few/many forms.
You may delete untranslated entries: they fall back to English.
Keep executable commands, flags, URLs, filenames and configuration tokens unchanged.

Do not include this README in the finished pack. Build it from the repository:
python3 scripts/language_packs.py build language-packs/YOUR-LOCALE --output YOUR-PACK.retui-launcher-lang

Import the file in Launcher Settings > System & Support > Language packs.
Test Notes, settings, command responses, notifications, plurals, dates and RTL.
Submit the sources under language-packs/YOUR-LOCALE in a pull request, including
translation credits and any required LICENSE/NOTICE. A maintainer publishes the
reviewed pack in a separate language-pack-LOCALE-vVERSION GitHub release.
Keyboard .retui-lang packs are a different format and cannot be imported here.
'''


def android_text(raw):
    out = []
    quoted = False
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == '\\' and i + 1 < len(raw):
            i += 1
            c = raw[i]
            if c == 'u' and re.fullmatch('[0-9a-fA-F]{4}', raw[i+1:i+5]):
                out.append(chr(int(raw[i+1:i+5], 16))); i += 4
            else:
                out.append({'n': '\n', 't': '\t', 'r': '\r'}.get(c, c))
        elif c == '"':
            quoted = not quoted
        elif c.isspace() and not quoted:
            if out and out[-1] != ' ': out.append(' ')
        else:
            out.append(c)
        i += 1
    return ''.join(out).rstrip(' ') if not raw.strip().startswith('"') else ''.join(out)


def catalog():
    result = {}
    for module in ['retui-contract', 'retui-datetime-picker', 'app']:
        for path in sorted((ROOT / module / 'src/main/res/values').glob('*.xml')):
            for item in ET.parse(path).getroot():
                if item.tag not in ('string', 'plurals') or item.get('translatable') == 'false': continue
                key = item.get('name')
                forms = {'other': android_text(''.join(item.itertext()))} if item.tag == 'string' else {
                    child.get('quantity'): android_text(''.join(child.itertext())) for child in item
                }
                result[key] = {'type': item.tag, 'formatted': item.get('formatted') != 'false', 'forms': forms}
    return result


def template(data):
    root = ET.Element('resources')
    root.append(ET.Comment('Plain XML text. Preserve command syntax and indexed format arguments. Missing entries use English.'))
    for key, spec in sorted(data.items()):
        item = ET.SubElement(root, spec['type'], name=key)
        if spec['type'] == 'string': item.text = spec['forms']['other']
        else:
            for quantity, value in spec['forms'].items(): ET.SubElement(item, 'item', quantity=quantity).text = value
    ET.indent(root, space='    ')
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def archive(path, files):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for name, content in sorted(files.items()):
            info = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, content)


def export(check=False):
    data = catalog()
    manifest = {'schema': 1, 'target': 'retui-launcher', 'id': 'example-en-US', 'languageTag': 'en-US', 'name': 'English template', 'nativeName': 'English template', 'version': 1, 'direction': 'ltr'}
    files = {'manifest.json': json.dumps(manifest, indent=2)+'\n', 'strings.xml': template(data), 'README.md': GUIDE}
    encoded = json.dumps(data, ensure_ascii=False, sort_keys=True, indent=2)+'\n'
    if check:
        assert (ASSETS/'catalog.json').read_text() == encoded, 'Run scripts/language_packs.py export after changing resources'
        with zipfile.ZipFile(ASSETS/'english-template.zip') as z:
            for name, value in files.items(): assert z.read(name) == (value.encode() if isinstance(value, str) else value), f'Stale template: {name}'
    else:
        ASSETS.mkdir(parents=True, exist_ok=True)
        (ASSETS/'catalog.json').write_text(encoded)
        archive(ASSETS/'english-template.zip', files)
        template_dir = ROOT/'language-packs/template'
        template_dir.mkdir(parents=True, exist_ok=True)
        for name, value in files.items(): (template_dir/name).write_bytes(value.encode() if isinstance(value, str) else value)
    print(f'{len(data)} translatable keys; catalog and template '+('current' if check else 'exported'))


def build(source, output):
    manifest = json.loads((source/'manifest.json').read_text())
    assert manifest['schema'] == 1 and manifest['target'] == 'retui-launcher', 'Wrong schema or target'
    assert re.fullmatch('[A-Za-z0-9][A-Za-z0-9_-]{1,47}', manifest['id']) and manifest['id'] != 'en', 'Invalid ID'
    assert type(manifest['version']) is int and manifest['version'] > 0, 'Invalid version'
    assert manifest['direction'] in ('ltr', 'rtl'), 'Invalid direction'
    xml = (source/'strings.xml').read_bytes()
    assert b'<!DOCTYPE' not in xml.upper() and b'<!ENTITY' not in xml.upper(), 'Declarations prohibited'
    data = catalog(); root = ET.fromstring(xml); seen = set()
    assert root.tag == 'resources' and len(root), 'Empty or invalid resources'
    for item in root:
        key = item.get('name')
        assert key in data and key not in seen and item.tag == data[key]['type'], f'Unknown, protected, duplicate or mismatched key: {key}'
        seen.add(key)
        if item.tag == 'string':
            assert not len(item) and (item.text or '').strip(), f'Invalid string: {key}'
            forms = {'other': item.text}
        else:
            qs = [child.get('quantity') for child in item]
            assert 'other' in qs and len(qs) == len(set(qs)) and set(qs) <= {'zero','one','two','few','many','other'}, f'Invalid plurals: {key}'
            assert all(child.tag == 'item' and not len(child) for child in item), f'Invalid plural item: {key}'
            forms = {child.get('quantity'): child.text or '' for child in item}
        for quantity, value in forms.items():
            assert value.strip() and len(value) <= 32768, f'Empty or oversized text: {key}'
            if data[key]['formatted']:
                source_text = data[key]['forms'].get(quantity, data[key]['forms']['other'])
                assert placeholders(value) == placeholders(source_text), f'Format arguments differ: {key}'
    # Android import additionally validates locale and format arguments against the shipped catalog.
    files = {name: (source/name).read_bytes() for name in ['manifest.json','strings.xml','LICENSE','NOTICE'] if (source/name).is_file()}
    assert sum(map(len, files.values())) <= 4*1024*1024, 'Pack too large'
    archive(output, files)
    print(output)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('export'); p.add_argument('--check', action='store_true')
    p = sub.add_parser('build'); p.add_argument('source', type=Path); p.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.command == 'export': export(args.check)
    else: build(args.source, args.output)
