"""Deterministic, offline gallery-to-.retui conversion; no new app format."""
import argparse
import hashlib
import json
import math
from fractions import Fraction
import re
import shutil
import subprocess
import zipfile
from functools import lru_cache
from pathlib import Path
from xml.etree import ElementTree as ET

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
JAVA = REPO / "app/src/main/java/ohi/andre/consolelauncher"
ANSI = "black red green yellow blue purple cyan white brightBlack brightRed brightGreen brightYellow brightBlue brightPurple brightCyan brightWhite".split()


def compact_artifact(path):
    """Transparent macOS filesystem compression; file bytes and offline access stay identical."""
    if not Path('/usr/bin/ditto').is_file() or path.stat().st_size < 32768:
        return
    temporary = path.with_name('.compact-' + path.name)
    try:
        subprocess.run(['/usr/bin/ditto', '--hfsCompression', str(path), str(temporary)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if temporary.read_bytes() != path.read_bytes():
            raise ValueError('Filesystem compression changed artifact bytes')
        if temporary.stat().st_blocks < path.stat().st_blocks:
            temporary.replace(path)
    finally:
        if temporary.exists(): temporary.unlink()


def rgb(raw):
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", raw):
        raise ValueError(f"Invalid opaque source color: {raw}")
    return tuple(int(raw[i:i + 2], 16) for i in (1, 3, 5))


def color(value):
    return "#" + "".join(f"{v:02x}" for v in value)


@lru_cache(maxsize=131072)
def luminance(raw):
    channels = [v / 255 for v in rgb(raw)]
    linear = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in channels]
    return sum(v * weight for v, weight in zip(linear, (.2126, .7152, .0722)))


def contrast(first, second):
    a, b = sorted((luminance(first), luminance(second)))
    return (b + .05) / (a + .05)


def readable(source, backgrounds, threshold=4.5):
    return _readable(source, tuple(backgrounds), threshold)


@lru_cache(maxsize=131072)
def _readable(source, backgrounds, threshold):
    if all(contrast(source, bg) >= threshold for bg in backgrounds):
        return source.lower()
    original = rgb(source)
    passing = []
    for end in (0, 255):
        # Visit every quantized RGB value on the ray, including between 1/255 steps.
        events = {Fraction(0), Fraction(1)}
        for value in original:
            distance = abs(end - value)
            events.update(Fraction(2 * index + 1, 2 * distance) for index in range(distance))
        events = sorted(events)
        for left, right in zip(events, events[1:]):
            amount = float((left + right) / 2)
            candidate = tuple(math.floor(v + (end - v) * amount + .5) for v in original)
            result = color(candidate)
            if all(contrast(result, bg) >= threshold for bg in backgrounds):
                passing.append((sum((a - b) ** 2 for a, b in zip(candidate, original)), result))
                break
    if not passing:
        raise ValueError(f"No readable adjustment for {source} on {backgrounds}")
    return min(passing)[1]


def roles():
    text = (JAVA / "managers/xml/options/Theme.kt").read_text()
    return re.findall(r"^    ([a-z][a-z0-9_]*)\(", text, re.M)


def validate_registry(registry):
    current = set(roles())
    mapped = set(registry["roles"])
    if current != mapped:
        raise ValueError(f"Theme registry drift: unmapped={current - mapped}, removed={mapped - current}")
    root = ET.parse(REPO / "app/src/main/AndroidManifest.xml").getroot()
    activities = {x.attrib['{http://schemas.android.com/apk/res/android}name'] for x in root.findall('application/activity')}
    registered = {x["activity"] for x in registry["surfaces"] if x.get("activity")}
    registered |= set(registry["activityExceptions"])
    if activities != registered:
        raise ValueError(f"Activity registry drift: {activities ^ registered}")
    assert len({s["id"] for s in registry["surfaces"]}) == len(registry["surfaces"])
    assigned = {role for surface in registry['surfaces'] for role in surface['roles']}
    if assigned != current or any(not surface.get('renderingChecks') for surface in registry['surfaces']):
        raise ValueError('Surface role/check registry drift')
    for filename, registered in registry['surfaceEntryPoints'].items():
        actual = set(re.findall(r'fun\s+((?:show|open)[A-Z]\w*)\s*\(', (JAVA / filename).read_text()))
        if actual != set(registered):
            raise ValueError(f'Surface entry point registry drift: {filename}: {actual ^ set(registered)}')


def map_palette(palette, registry):
    required = set(ANSI + ["name", "background", "foreground", "cursorColor", "selectionBackground"])
    if set(palette) != required or not isinstance(palette["name"], str) or not 0 < len(palette["name"]) <= 128:
        raise ValueError("Unexpected palette fields or name")
    for key in required - {"name"}:
        rgb(palette[key])
    bg = palette["background"].lower()
    fg = readable(palette["foreground"], [bg])
    selected = readable(palette["selectionBackground"], [fg])
    values, checks, changes = {}, [], []
    for role, spec in registry["roles"].items():
        source = spec["source"]
        original = palette[source] if source in palette else source
        if source == "background":
            value = bg
        elif source == "foreground":
            value = fg
        elif source == "selectionBackground":
            value = selected
        elif spec.get("threshold"):
            backgrounds = [selected if spec.get("on") == "selection" else bg]
            value = readable(original, backgrounds, spec["threshold"])
        else:
            value = original.lower()
        values[role] = value
        if value.lower() != original.lower():
            changes.append({"role": role, "source": source, "original": original, "adjusted": value})
        if spec.get("threshold"):
            on = selected if spec.get("on") == "selection" else bg
            ratio = contrast(value, on)
            checks.append({"role": role, "background": on, "foreground": value,
                           "ratio": ratio, "threshold": spec["threshold"], "status": "pass" if ratio >= spec["threshold"] else "fail"})
    suggestions = {}
    for prefix, source in zip("default apps alias cmd song contact file".split(), ["foreground", "blue", "purple", "green", "yellow", "cyan", "foreground"]):
        suggestions[prefix + "_background_color"] = bg
        suggestions[prefix + "_text_color"] = readable(palette[source], [bg])
        checks.append({"role": "suggestions." + prefix + "_text_color", "foreground": suggestions[prefix + "_text_color"],
                       "background": bg, "ratio": contrast(suggestions[prefix + "_text_color"], bg), "threshold": 4.5, "status": "pass"})
        if suggestions[prefix + "_text_color"].lower() != palette[source].lower():
            changes.append({"role": "suggestions." + prefix + "_text_color", "source": source,
                           "original": palette[source], "adjusted": suggestions[prefix + "_text_color"]})
    return values, suggestions, changes, checks


def xml(root, values):
    element = ET.Element(root)
    for key, value in values.items():
        ET.SubElement(element, key, value=value)
    return ET.tostring(element, encoding="utf-8", xml_declaration=True)


def package(path, name, theme, suggestions, version):
    manifest = f"type=retui-shareable-config\nschema=2\nprofile=shareable\nprivacy=pi-safe\nappVersion={version}\nsource=preset\npresetName={name.replace(chr(10), ' ').replace(chr(13), ' ')}\nsections=theme,suggestions\n"
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for filename, data in (("manifest.txt", manifest.encode()), ("theme.xml", xml("THEME", theme)), ("suggestions.xml", xml("SUGGESTIONS", suggestions))):
            info = zipfile.ZipInfo(filename, date_time=(2026, 10, 14, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)


def featured_ids():
    gallery = (HERE / 'source/gallery.html').read_text()
    section = gallery.split('Start here', 1)[1].split('id="alllbl"', 1)[0]
    ids = re.findall(r'href="/en/themes/([^/]+)/"', section)
    if len(ids) != 21 or len(set(ids)) != 21:
        raise ValueError('Featured gallery selection changed; review the pinned source')
    return ids


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--site", type=Path, required=True)
    args = parser.parse_args()
    registry = json.loads((HERE / "coverage.json").read_text())
    validate_registry(registry)
    source_index = json.loads((HERE / "source/index.json").read_text())
    assert source_index["count"] == len(source_index["ids"]) == 614
    output = args.site / "docs/terminal-colors"
    downloads = output / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    assets = REPO / "app/src/androidTest/assets/terminal-colors"
    (assets / "downloads").mkdir(parents=True, exist_ok=True)
    selected = featured_ids()
    assert set(selected) <= set(source_index['ids'])
    # Remove packages from the previous generated catalog when its scope shrinks.
    for folder in (output, assets):
        previous = folder / 'catalog.json'
        if previous.exists():
            for item in json.loads(previous.read_text())['themes']:
                if item['id'] not in selected and re.fullmatch(r'[a-z0-9-]+', item['id']):
                    (folder / 'downloads' / (item['id'] + '.retui')).unlink(missing_ok=True)
                    (folder / 'previews' / (item['id'] + '.png')).unlink(missing_ok=True)
    version = re.search(r'versionName = "([^"]+)"', (REPO / "app/build.gradle").read_text())[1]
    themes, numeric = [], []
    credits = re.split(r'\n\s*\n', (HERE / 'source/CREDITS.md').read_text())
    def attribution(name):
        base = re.sub(r'\s*\(edited\)\s*', '', name, flags=re.I)
        for candidate, basis in ((base, 'exact theme'), (base.split()[0], 'theme family')):
            if len(candidate) < 4:
                continue
            pattern = r'(?<![A-Za-z0-9])' + r'[^A-Za-z0-9]*'.join(re.escape(c) for c in candidate if c.isalnum()) + r'(?![A-Za-z0-9])'
            for paragraph in credits:
                head = re.split(r'\b(?:were|was|is|are|created|adapted|based)\b', paragraph, maxsplit=1, flags=re.I)[0]
                if re.search(pattern, head, re.I):
                    return {'text': paragraph.replace('\n', ' '), 'basis': basis, 'source': 'CREDITS.md'}
        return {'text': 'iTerm2-Color-Schemes contributors. The pinned credits do not identify the author of this exact variant.',
                'basis': 'unresolved individual author', 'source': 'CREDITS.md'}
    for slug in selected:
        record = json.loads((HERE / "source" / (slug + ".json")).read_text())
        palette = record["palette"]
        theme, suggestions, changes, checks = map_palette(palette, registry)
        assert all(c["status"] == "pass" for c in checks), slug
        path = downloads / (slug + ".retui")
        package(path, palette["name"], theme, suggestions, version)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        shutil.copyfile(path, assets / "downloads" / path.name)
        item = {"id": slug, "name": palette["name"], "mode": "dark" if luminance(palette["background"]) < .179 else "light",
                "download": "downloads/" + path.name, "bytes": path.stat().st_size, "sha256": digest,
                "background": theme["background_color"], "foreground": theme["output_text_color"],
                "swatches": [theme["ansi_" + k] for k in "red green yellow blue magenta cyan".split()],
                "sourceUrl": record["sourceUrl"], "upstreamUrl": record["upstreamUrl"],
                "attribution": attribution(palette['name']),
                "adjustmentCount": len(changes), "screenshot": None, "verification": "untested"}
        themes.append(item)
        numeric.append({**item, "source": record, "values": theme, "suggestions": suggestions,
                        "adjustments": changes, "checks": checks})
    catalog = {"schema": 1, "retrievedDate": source_index["retrievedDate"], "galleryUrl": source_index["galleryUrl"],
               "selection": "featured", "sourceSnapshotCount": source_index['count'],
               "description": "Re:TUI readability adaptations of the 21 featured Term gallery palettes.", "themes": themes}
    for path in (output / "catalog.json", assets / "catalog.json"):
        path.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    (HERE / "generated").mkdir(exist_ok=True)
    (HERE / "generated/palettes.json").write_text(json.dumps(numeric, ensure_ascii=False) + "\n")
    shutil.copyfile(HERE / "coverage.json", assets / "coverage.json")
    for filename in ("LICENSE", "CREDITS.md"):
        shutil.copyfile(HERE / "source" / filename, output / filename)
    print(f"Generated {len(themes)} packages; {len(registry['roles'])} theme roles mapped")


if __name__ == "__main__":
    main()
