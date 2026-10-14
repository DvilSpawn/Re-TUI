"""Snapshot the gallery's Copy JSON payloads, including Term's edited palettes."""
import concurrent.futures
import hashlib
import json
import re
import time
import argparse
from datetime import datetime, timezone
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
BASE = "https://term.const.net"


def fetch(url):
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "ReTUI-palette-snapshot/1.0"})
            return urllib.request.urlopen(request, timeout=45).read()
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--refresh', action='store_true', help='Replace the pinned snapshot with a fresh gallery retrieval')
    args = parser.parse_args()
    folder = ROOT / 'source'
    if (folder / 'index.json').exists() and not args.refresh:
        index = json.loads((folder / 'index.json').read_text())
        assert all((folder / (slug + '.json')).is_file() for slug in index['ids'])
        print(f"Using pinned snapshot: {index['count']} palettes, retrieved {index['retrievedDate']}")
        return
    retrieval_date = datetime.now(timezone.utc).date().isoformat()
    index = fetch(BASE + "/en/themes/")
    slugs = sorted(set(re.findall(r'href="/en/themes/([^/]+)/"', index.decode())))
    assert len(slugs) == 614, f"Review gallery count change: {len(slugs)}"
    folder = ROOT / "source"
    folder.mkdir(exist_ok=True)
    (folder / "gallery.html").write_bytes(index)

    def snapshot(slug):
        path = folder / (slug + ".json")
        if path.exists() and not args.refresh:
            return json.loads(path.read_text())
        url = BASE + "/en/themes/" + slug + "/"
        raw = fetch(url)
        page = raw.decode()
        match = re.search(r'const [A-Za-z_$][\w$]*=`(\{\s*"name".*?\})`', page, re.S)
        assert match, f"Copy JSON payload missing: {slug}"
        palette = json.loads(match[1])
        upstream = re.search(r'href="([^"]+)"[^>]*>Upstream file', page)
        record = {"id": slug, "sourceUrl": url, "retrievedDate": retrieval_date,
                  "pageSha256": hashlib.sha256(raw).hexdigest(),
                  "paletteSha256": hashlib.sha256(match[1].encode()).hexdigest(),
                  "upstreamUrl": upstream[1] if upstream else None,
                  "attribution": "iTerm2-Color-Schemes contributors; individual theme authors listed in CREDITS.md",
                  "palette": palette}
        path.write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n")
        return record

    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        records = list(executor.map(snapshot, slugs))
    for name in ("LICENSE", "CREDITS.md"):
        (folder / name).write_bytes(fetch("https://raw.githubusercontent.com/mbadolato/iTerm2-Color-Schemes/1a3e1d298b0a102d53333f1a03cb5234e7f9dae8/" + name))
    (folder / "index.json").write_text(json.dumps({"schema": 1, "count": len(records),
        "galleryUrl": BASE + "/en/themes/", "gallerySha256": hashlib.sha256(index).hexdigest(),
        "retrievedDate": retrieval_date, "ids": slugs}, indent=2) + "\n")
    print(f"Snapshotted {len(records)} palettes")


if __name__ == "__main__":
    main()
