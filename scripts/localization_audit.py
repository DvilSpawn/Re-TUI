#!/usr/bin/env python3
"""Inventory localization work and validate translator XML using only the stdlib.

The source inventory is a review queue, not an automatic extraction or coverage proof.
Run from any directory. Generated CSVs are for developers; translators edit Android XML.
"""
import argparse
import collections
import csv
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('app', 'retui-datetime-picker', 'retui-contract')
ANDROID = '{http://schemas.android.com/apk/res/android}'
# Skip comments; keep ordinary and raw strings. Nested Kotlin interpolation needs review.
TOKENS = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|\"\"\"[\s\S]*?\"\"\"|"(?:\\.|[^"\\])*"')
FORMATS = re.compile(r'%(?:(\d+)\$)?([-#+ 0,(<]*)(?:\d+)?(?:\.\d+)?([tT][a-zA-Z]|[a-zA-Z%])')


def placeholders(text):
    """Keep argument numbers and conversion types; allow reordered indexed arguments."""
    result = collections.Counter()
    implicit = 0
    previous = None
    for match in FORMATS.finditer(text):
        index, flags, kind = match.groups()
        if kind in ('%', 'n'):
            continue
        if index:
            index = int(index)
        elif '<' in flags:
            if previous is None:
                raise ValueError('format reuses an argument before defining one')
            index = previous
        else:
            implicit += 1
            index = implicit
        previous = index
        result[(index, kind)] += 1
    return result


def resources(directory):
    result = {}
    for path in sorted(directory.glob('*.xml')):
        for node in ET.parse(path).getroot():
            if node.tag not in ('string', 'plurals', 'string-array'):
                continue
            name = node.attrib['name']
            if name in result:
                raise ValueError(f'{directory}: duplicate resource {name}')
            result[name] = (path, node)
    return result


def text_of(node):
    return ''.join(node.itertext())


def validate_translation(base, translated):
    errors = []
    for name, (path, node) in translated.items():
        prefix = f'{path}: {name}'
        if name not in base:
            errors.append(f'{prefix}: missing default resource')
            continue
        original = base[name][1]
        if original.attrib.get('translatable') == 'false':
            errors.append(f'{prefix}: resource is not translatable')
        if original.tag != node.tag:
            errors.append(f'{prefix}: resource type changed')
            continue
        if node.tag == 'string-array' and len(node) != len(original):
            errors.append(f'{prefix}: array length/order must be preserved')
        if node.tag == 'plurals' and not any(n.get('quantity') == 'other' for n in node):
            errors.append(f'{prefix}: plurals need an other item')
        if node.tag == 'string':
            pairs = [(original, node)]
        elif node.tag == 'string-array':
            pairs = list(zip(original, node))
        else:
            defaults = {n.get('quantity'): n for n in original}
            pairs = [(defaults.get(n.get('quantity'), defaults.get('other')), n) for n in node]
        for source, target in pairs:
            if source is None:
                errors.append(f'{prefix}: no default plural fallback')
                continue
            if original.get('formatted') != 'false':
                if node.get('formatted') == 'false' and placeholders(text_of(source)):
                    errors.append(f'{prefix}: formatting was disabled')
                if placeholders(text_of(source)) != placeholders(text_of(target)):
                    errors.append(f'{prefix}: format arguments changed')
            if not text_of(target).strip() and text_of(source).strip():
                errors.append(f'{prefix}: empty translation')
    return errors


def inventory(root):
    rows = []
    counts = collections.Counter()
    for module in MODULES:
        for source_set in sorted((root / module / 'src').glob('*')):
            if source_set.name.startswith(('test', 'androidTest')):
                continue
            for path in sorted(source_set.rglob('*')):
                if path.suffix not in ('.kt', '.java', '.xml'):
                    continue
                counts[path.suffix] += 1
                source = path.read_text()
                if path.suffix == '.xml':
                    for match in re.finditer(r'android:(text|hint|title|contentDescription)="([^"@?][^"]*)"', source):
                        if re.search('[A-Za-z]', match[2]):
                            rows.append((str(path.relative_to(root)), source.count('\n', 0, match.start()) + 1,
                                         'xml-display', match[2], match[0]))
                    continue
                for match in TOKENS.finditer(source):
                    if not match[0].startswith('"'):
                        continue
                    literal = match[0][3:-3] if match[0].startswith('"""') else match[0][1:-1]
                    # ponytail: lexical review queue includes false positives and can miss nested interpolation;
                    # use Android Lint plus manual call-site review before claiming complete extraction.
                    if not re.search('[A-Za-z]', literal) or not (re.search(r'\s', literal) or re.match('[A-Z]', literal)):
                        continue
                    line = source.count('\n', 0, match.start()) + 1
                    context = source[source.rfind('\n', 0, match.start()) + 1:source.find('\n', match.end()) if '\n' in source[match.end():] else len(source)].strip()
                    category = 'review-literal'
                    if re.search(r'\b(text|hint|contentDescription)\s*=|set(Text|Title|Message|Hint)|Toast\.', context):
                        category = 'display-candidate'
                    elif '/commands/' in str(path):
                        category = 'command-review'
                    elif '/xml/options/' in str(path):
                        category = 'setting-schema-review'
                    rows.append((str(path.relative_to(root)), line, category, literal, context))
    return rows, counts


def write_csv(path, header, rows):
    with path.open('w', newline='') as stream:
        writer = csv.writer(stream)
        writer.writerow(header)
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--write', action='store_true', help='refresh docs/localization inventory files')
    args = parser.parse_args()
    errors, catalog = [], []
    for module in MODULES:
        res = ROOT / module / 'src/main/res'
        base = resources(res / 'values')
        for name, (path, node) in base.items():
            catalog.append((module, str(path.relative_to(ROOT)), name, node.tag,
                            node.get('translatable', 'true'), text_of(node)))
        for source_set in sorted((ROOT / module / 'src').iterdir()):
            if source_set.name in ('test', 'androidTest') or not source_set.is_dir():
                continue
            source_res = source_set / 'res'
            merged = dict(base)
            if source_set.name != 'main':
                merged.update(resources(source_res / 'values'))
            for folder in sorted(source_res.glob('values-*')):
                errors.extend(validate_translation(merged, resources(folder)))
    rows, counts = inventory(ROOT)
    by_file = collections.Counter(row[0] for row in rows)
    if args.write:
        out = ROOT / 'docs/localization'
        out.mkdir(parents=True, exist_ok=True)
        write_csv(out / 'resources.csv', ('module', 'file', 'key', 'type', 'translatable', 'english'), catalog)
        write_csv(out / 'source-review.csv', ('file', 'line', 'category', 'literal', 'context'), rows)
        lines = ['# Localization inventory', '',
                 'Generated by `python3 scripts/localization_audit.py --write`. These are lexical candidates, not confirmed untranslated strings or a coverage percentage.', '',
                 f'Scanned production sources in all three modules and both app flavors: {dict(counts)}.',
                 f'Default resources: {len(catalog)}; translatable: {sum(r[4] != "false" for r in catalog)}.',
                 f'Source review candidates: {len(rows)} across {len(by_file)} files.', '',
                 '| File | Candidates |', '| --- | ---: |']
        lines += [f'| `{path}` | {count} |' for path, count in by_file.most_common()]
        (out / 'INVENTORY.md').write_text('\n'.join(lines) + '\n')
    print(f'{len(catalog)} default resources; {len(rows)} source review candidates in {len(by_file)} files')
    for error in errors:
        print(error)
    print(f'Translation resource checks: {len(errors)} errors')
    return bool(errors)


if __name__ == '__main__':
    raise SystemExit(main())
