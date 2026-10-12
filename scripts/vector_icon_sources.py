"""Emit a reviewed, pinned Material Symbols conversion plan; never edit files.

Android resource identifiers/intrinsic sizes are preserved. Callers apply the
emitted patch and remove only the listed obsolete PNGs. Full upstream paths stay
in the vector source. Generic social-link symbols replace unverified brand logos.
"""
import concurrent.futures
import hashlib
import json
import re
from pathlib import Path
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
REVISION = "49d4db35df873165d6bd6ba09b063c7dafbac2f4"
ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])
OUTLINED = {
    "hourglass_24": "hourglass_empty", "timer_24": "timer",
    "ic_chevron_down": "keyboard_arrow_down", "ic_chevron_up": "keyboard_arrow_up",
    "ic_dystopia_fingerprint_24": "fingerprint", "ic_menu": "menu",
    "ic_toolbar_bell_24": "notifications", "ic_toolbar_chat_24": "chat",
    "ic_toolbar_home_24": "home", "ic_toolbar_music_24": "music_note",
    "ic_toolbar_note_24": "edit_note", "ic_toolbar_refresh_24": "refresh",
    "ic_toolbar_search_24": "search", "ic_toolbar_star_24": "star",
    "ic_toolbar_terminal_24": "terminal", "ic_toolbar_widgets_24": "widgets",
    "ic_tuixt_delete_24": "delete", "ic_tuixt_discord_24": "chat",
    "ic_tuixt_github_24": "code", "ic_tuixt_lock_24": "lock",
    "ic_tuixt_lock_open_24": "lock_open", "ic_tuixt_reddit_24": "forum",
    "ic_tuixt_web_24": "language", "ic_widget_resize_arrow": "arrow_drop_up",
    "ic_workspace_add_24": "add", "ic_workspace_keyboard_24": "keyboard",
    "ic_workspace_layout_24": "view_quilt", "open_in_full_24": "open_in_full",
    "pip_24": "picture_in_picture", "remember_ic_add_notes": "note_add",
    "remember_ic_link": "link", "remember_ic_list": "format_list_bulleted",
    "remember_ic_photo_library": "photo_library", "remember_ic_pin": "push_pin",
    "remember_ic_redact": "blur_on", "remember_ic_settings": "settings",
    "remember_ic_tag": "label", "remember_ic_visibility": "visibility",
    "remember_ic_visibility_off": "visibility_off",
    "remember_ic_format_bold": "format_bold", "remember_ic_format_italic": "format_italic",
    "remember_ic_format_underlined": "format_underlined", "remember_ic_format_strikethrough": "strikethrough_s",
    "remember_ic_format_code": "code", "remember_ic_format_checklist": "checklist",
    "remember_ic_format_bulleted": "format_list_bulleted", "remember_ic_format_numbered": "format_list_numbered",
    "remember_ic_format_quote": "format_quote", "remember_ic_format_title": "title",
    "remember_ic_format_h1": "format_h1", "remember_ic_format_h2": "format_h2",
    "remember_ic_format_h3": "format_h3",
}
ROUNDED = {
    "ic_material_calculate_24": "calculate", "ic_material_help_24": "help",
    "ic_material_keyboard_arrow_up_24": "keyboard_arrow_up", "ic_material_partly_cloudy_day_24": "partly_cloudy_day",
    "ic_material_sd_card_24": "sd_card", "ic_material_signal_cellular_alt_24": "signal_cellular_alt",
    "ic_material_smartphone_24": "smartphone", "ic_status_code_40": "code",
    "ic_status_devices_40": "devices", "ic_status_edit_note_40": "edit_note",
    "ic_status_lock_open_40": "lock_open", "ic_status_memory_40": "memory",
    "ic_status_storage_40": "storage", "ic_status_wifi_40": "wifi",
}


def convert(svg, width, height):
    root = ET.fromstring(svg)
    assert root.tag == "{http://www.w3.org/2000/svg}svg"
    if "viewBox" in root.attrib:
        box = root.attrib["viewBox"].split()
    else:
        # With no viewBox, numeric/px SVG dimensions are the user-coordinate viewport.
        dimensions = [root.attrib[name] for name in ("width", "height")]
        assert all(re.fullmatch(r"[0-9]+(?:\.[0-9]+)?(?:px)?", n) for n in dimensions)
        box = ["0", "0"] + [n.removesuffix("px") for n in dimensions]
    assert len(box) == 4 and 0 < float(box[2]) <= 4096 and 0 < float(box[3]) <= 4096
    paths = list(root)
    assert paths and all(p.tag == "{http://www.w3.org/2000/svg}path" and set(p.attrib) == {"d"} for p in paths)
    vector = ET.Element("vector", {"xmlns:android": ANDROID[1:-1], "android:width": width,
        "android:height": height, "android:viewportWidth": box[2], "android:viewportHeight": box[3]})
    group = ET.SubElement(vector, "group", {"android:translateX": str(-float(box[0])), "android:translateY": str(-float(box[1]))})
    for path in paths:
        ET.SubElement(group, "path", {"android:fillColor": "#FFFFFFFF", "android:pathData": path.attrib["d"]})
    ET.indent(vector, space="    ")
    return ET.tostring(vector, encoding="unicode") + "\n"


def plan(resource, symbol, family):
    file = ROOT / "app/src/main/res/drawable" / (resource + ".xml")
    old = file.read_text() if file.exists() else ""
    existing = ET.fromstring(old) if old else None
    wrapped = existing is not None and existing.tag == "rotate"
    size = existing.find("vector") if wrapped else existing
    assert not wrapped or size is not None and len(existing) == 1
    width = size.attrib[ANDROID + "width"] if size is not None else "24dp"
    height = size.attrib[ANDROID + "height"] if size is not None else "24dp"
    url = f"https://raw.githubusercontent.com/google/material-design-icons/{REVISION}/symbols/web/{symbol}/{family}/{symbol}_24px.svg"
    with urllib.request.urlopen(url, timeout=30) as response:
        svg = response.read(128 * 1024 + 1)
    assert len(svg) <= 128 * 1024
    vector = convert(svg, width, height)
    if wrapped:
        existing.remove(size)
        existing.append(ET.fromstring(vector))
        ET.indent(existing, space="    ")
        vector = ET.tostring(existing, encoding="unicode") + "\n"
    new = f"<!-- Google Material Symbols, Apache-2.0. Converted viewport; paths unchanged.\nSource: {url}\nLicense: LICENSES/Material-Symbols-Apache-2.0.txt -->\n" + vector
    return {"path": str(file.relative_to(ROOT)), "old": old, "new": new,
            "source": url, "source_sha256": hashlib.sha256(svg).hexdigest(),
            "vector_sha256": hashlib.sha256(new.encode()).hexdigest()}


def main():
    tasks = [(r, s, "materialsymbolsoutlined") for r, s in OUTLINED.items()] + [(r, s, "materialsymbolsrounded") for r, s in ROUNDED.items()]
    assert len({r for r, _, _ in tasks}) == len(tasks)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        changes = list(pool.map(lambda args: plan(*args), tasks))
    print(json.dumps({"revision": REVISION, "license": "Apache-2.0", "changes": changes}))


if __name__ == "__main__":
    main()
