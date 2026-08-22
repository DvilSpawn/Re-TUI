#!/usr/bin/env python3
"""Build an importable Re:TUI frame pack from the purchased Sprout Lands sprites."""

import argparse
import json
from pathlib import Path
from PIL import Image

PACK_README = """# Re:T-UI frame pack

## Basic texture rules

- Use square PNGs sized 24x24, 48x48, 72x72, and so on.
- Build every PNG as an exact 3x3 grid of equal square cells.
- Re:T-UI fixes the corners, repeats all four edges, stretches the center, and uses nearest-neighbor filtering.
- Name each PNG for its role. Replace the PNG and reimport the folder to update it.

This Sprout pack includes manifest.json because its original sprites use custom shapes. Power users can override the automatic thirds, border thickness, and tile modes there.
"""


def tight(image: Image.Image) -> Image.Image:
    rgba = image.convert("RGBA")
    box = rgba.getbbox()
    if box is None:
        raise ValueError("source sprite is empty")
    return rgba.crop(box)


def source_image(source: Path, name: str) -> Image.Image:
    with Image.open(source / name) as image:
        return image.convert("RGBA")


def sheet_cell(source: Path, name: str, cell: tuple[int, int, int, int]) -> Image.Image:
    return source_image(source, name).crop(cell)


def slider_track(source: Path, prefix: str) -> Image.Image:
    pieces = [tight(source_image(source, f"Other UI sprites/Sliders/{prefix}_{part}.png")) for part in ("start", "mid", "end")]
    height = max(piece.height for piece in pieces)
    output = Image.new("RGBA", (sum(piece.width for piece in pieces), height))
    x = 0
    for piece in pieces:
        output.alpha_composite(piece, (x, (height - piece.height) // 2))
        x += piece.width
    return output


def role(file: str, slice_px: tuple[int, int, int, int], border_dp: tuple[float, float, float, float]) -> dict:
    left, top, right, bottom = slice_px
    left_dp, top_dp, right_dp, bottom_dp = border_dp
    return {
        "file": file,
        "slicePx": {"left": left, "top": top, "right": right, "bottom": bottom},
        "borderDp": {"left": left_dp, "top": top_dp, "right": right_dp, "bottom": bottom_dp},
        "modes": {"left": "tile", "top": "tile", "right": "tile", "bottom": "tile", "center": "stretch"},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path, help="Path to the pack's UI Sprites folder")
    parser.add_argument("output", type=Path, help="Output folder to import from Frames")
    args = parser.parse_args()

    source = args.source.expanduser().resolve()
    output = args.output.expanduser().resolve()
    required = source / "Other UI sprites/Setting menu.png"
    if not required.is_file():
        raise SystemExit(f"Sprout Lands UI Sprites folder not found: {source}")

    frames = output / "frames"
    frames.mkdir(parents=True, exist_ok=True)
    for old in frames.glob("*.png"):
        old.unlink()

    # The right-hand Settings menu sprite is the plain square panel. Dialogue sprites
    # intentionally have a speech-arrow edge and must not be used as generic frames.
    panel = source_image(source, "Other UI sprites/Setting menu.png").crop((139, 12, 245, 134))
    button_sheet = "buttons/square/Square Buttons 26x19.png"
    icon_sheet = "buttons/square/Square Buttons 26x26.png"
    images = {
        "panel_square.png": panel,
        "button_normal.png": sheet_cell(source, button_sheet, (0, 32, 48, 64)),
        "button_pressed.png": sheet_cell(source, button_sheet, (48, 32, 96, 64)),
        "button_primary.png": sheet_cell(source, button_sheet, (0, 64, 48, 96)),
        "icon_button.png": sheet_cell(source, icon_sheet, (0, 48, 48, 96)),
        "toggle_off.png": source_image(source, "Other UI sprites/Sliders/slider_a_3.png"),
        "toggle_on.png": source_image(source, "Other UI sprites/Sliders/slider_a_1.png"),
        "slider_track.png": slider_track(source, "slider_a"),
        "slider_progress.png": slider_track(source, "slider_b"),
        "slider_thumb.png": source_image(source, "Other UI sprites/Sliders/slider_a_1.png"),
    }
    panel_role = role("panel_square.png", (8, 8, 8, 8), (4, 4, 4, 4))
    button_role = role("button_normal.png", (6, 6, 6, 6), (3, 3, 3, 3))
    roles = {}
    panel_roles = (
        "status_group", "status_ram", "status_device", "status_time", "status_battery",
        "status_storage", "status_network", "status_notes", "status_weather", "status_unlock",
        "status_ascii", "output", "input", "music", "notifications", "modules", "module_dock",
        "app_drawer", "widget_drawer", "keyboard", "files", "overlays", "settings", "dialog",
        "header", "list_item", "list_item_selected", "ui_input",
    )
    for name in panel_roles:
        roles[name] = panel_role
    for name in ("toolbar", "suggestions", "controls", "button"):
        roles[name] = button_role
    roles["button_pressed"] = role("button_pressed.png", (6, 6, 6, 6), (3, 3, 3, 3))
    roles["button_primary"] = role("button_primary.png", (6, 6, 6, 6), (3, 3, 3, 3))
    roles["icon_button"] = role("icon_button.png", (6, 6, 6, 6), (3, 3, 3, 3))
    roles["toggle_off"] = role("toggle_off.png", (4, 4, 4, 4), (3, 3, 3, 3))
    roles["toggle_on"] = role("toggle_on.png", (4, 4, 4, 4), (3, 3, 3, 3))
    roles["slider_track"] = role("slider_track.png", (6, 2, 6, 2), (3, 1, 3, 1))
    roles["slider_progress"] = role("slider_progress.png", (6, 1, 6, 1), (3, 1, 3, 1))
    roles["slider_thumb"] = role("slider_thumb.png", (4, 4, 4, 4), (4, 4, 4, 4))

    canonical_roles = {}
    for role_name, definition in roles.items():
        file_name = "suggestion_chip.png" if role_name == "suggestions" else f"{role_name}.png"
        tight(images[definition["file"]]).save(frames / file_name)
        canonical_roles[role_name] = json.loads(json.dumps(definition))
        canonical_roles[role_name]["file"] = file_name
    roles = canonical_roles

    for definition in roles.values():
        with Image.open(frames / definition["file"]) as image:
            slices = definition["slicePx"]
            assert image.width > slices["left"] + slices["right"]
            assert image.height > slices["top"] + slices["bottom"]

    manifest = {
        "type": "retui-frame-pack",
        "schema": 2,
        "name": "Sprout Lands",
        "filtering": "nearest",
        "roles": roles,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "readme.md").write_text(PACK_README, encoding="utf-8")

    referenced = {value["file"] for value in roles.values()}
    assert referenced == {path.name for path in frames.glob("*.png")}
    assert all(value["file"].endswith(".png") for value in roles.values())
    print(f"Built {len(roles)} canonical PNGs in {output}")


if __name__ == "__main__":
    main()
