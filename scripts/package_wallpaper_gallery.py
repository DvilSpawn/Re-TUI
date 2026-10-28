#!/usr/bin/env python3
"""Build data-only marketplace wallpaper packs and a gallery of recorded previews."""
import argparse
import hashlib
import html
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKS = [
    ('topo', 'topo noise', 'Topo Noise', 'Quiet contour lines, with adjustable relief, scale and colors. This wallpaper is still.'),
    ('pixel', 'pixel dream', 'Pixel Dream', 'A sparse pixel field with tap ripples, custom colors and optional music pulses.'),
    ('clouds', 'clouds', 'Clouds', 'Eight layered skies. Pixel clouds drift at different speeds while the stars stay still.'),
    ('nagomi', 'nagomi', 'Nagomi Koi Pond', 'Koi, tiny fish and drifting plants. Pick a pond photo or use the original background.'),
    ('astro', 'astro chart', 'Astro Chart', 'A pixel-art planet and busy station. Small craft dock and land while freighters cross at different depths.'),
    ('matrix', 'matrix rain', 'Matrix Rain', 'Falling glyph streams in six colour styles. Touch parts the rain and sends a ripple.'),
]
# catalog.json is read by builds that reject the whole file on any id they do not know, so it stays at these four.
# New wallpapers go only in catalog-v2.json, which every later build reads and which skips rows it cannot use.
LEGACY = ('topo', 'pixel', 'clouds', 'nagomi')


def prepare(output: Path):
    (output / 'downloads').mkdir(parents=True, exist_ok=True)
    (output / 'previews').mkdir(exist_ok=True)
    # Remove stale APK artifacts from the superseded local companion experiment only.
    for file in (output / 'downloads').iterdir():
        if file.suffix == '.apk' or file.name == 'wallpapers-source.tar.gz': file.unlink()
    (output / 'playstore.html').unlink(missing_ok=True)
    records = []
    notices = (ROOT / 'LICENSE').read_text() + '\n\n' + (ROOT / 'THIRD_PARTY_NOTICES.md').read_text()
    for ident, engine, title, description in PACKS:
        gif = ROOT / f'wallpaper-packs/gallery/previews/{ident}.gif'
        if not gif.is_file(): raise ValueError(f'Record {ident} preview first')
        assets = sorted((ROOT / f'wallpaper-packs/content/{ident}').glob('*.png'))
        target = output / f'downloads/{ident}.retui-wallpaper'
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr('wallpaper.json', json.dumps({'id': ident, 'engine': engine, 'api': 1, 'name': title}, indent=2))
            pack_notices = notices
            if ident == 'nagomi':
                pack_notices += '\n\n' + (ROOT / 'LICENSES/Nagomi-PolyForm-Noncommercial.txt').read_text()
                pack_notices += '\n\n' + (ROOT / 'docs/nagomi.md').read_text()
            if ident == 'clouds':
                pack_notices += '\n\n' + (ROOT / 'docs/clouds-licensing.md').read_text()
            archive.writestr('LICENSE.txt', pack_notices)
            for file in assets: archive.write(file, file.name)
        records.append(dict(id=ident, name=title, description=description, api=1,
                            preview=f'previews/{ident}.gif', download=f'downloads/{ident}.retui-wallpaper',
                            bytes=target.stat().st_size, sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
        shutil.copy2(gif, output / records[-1]['preview'])
    (output / 'catalog.json').write_text(json.dumps({'schema': 1, 'wallpapers': [row for row in records if row['id'] in LEGACY]}, indent=2) + '\n')
    (output / 'catalog-v2.json').write_text(json.dumps({'schema': 2, 'wallpapers': records}, indent=2) + '\n')
    template = (ROOT / 'wallpaper-packs/gallery/page.html').read_text()
    cards = []
    for item in records:
        name = html.escape(item['name'])
        cards.append(f'<article id="{item["id"]}"><img class="preview" src="{item["preview"]}" width="216" height="480" alt="{name} wallpaper preview, without interface controls" loading="lazy"><div class="body"><h2>{name}</h2><p>{html.escape(item["description"])}</p><p class="size">Wallpaper pack · {item["bytes"] / 1024:.0f} KiB</p><a class="button" href="{item["download"]}">Download</a><a class="use" href="retui-wallpaper://use/{item["id"]}">Use in Re:TUI</a></div></article>')
    (output / 'index.html').write_text(template.replace('{{cards}}', '\n'.join(cards)))
    print(f'Prepared {len(records)} data-only wallpaper packs in {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'wallpaper-packs/build/site/wallpaper-packs')
    args = parser.parse_args()
    prepare(args.output.resolve())
