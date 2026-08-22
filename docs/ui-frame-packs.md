# UI frame packs

Import a normal folder from **Settings > Appearance > Frames > Import UI Pack Folder**. Re:TUI adds the supplied PNGs to the pack library, then asks whether to apply every supplied role or keep the pack for mixing.

## Ground rules

- A basic texture is square: `24 × 24`, `48 × 48`, `72 × 72`, and so on.
- Treat the texture as an exact `3 × 3` grid. Every cell is the same square size.
- Re:T-UI keeps the four corners fixed, repeats the four edge cells, and stretches the center cell.
- Every texture uses nearest-neighbor filtering.
- The PNG filename assigns its UI role. Replacing that PNG and reimporting the folder replaces the role.

For example, `output.png` at `144 × 144` is cut at 48 pixels from every side. Its top, right, bottom, and left cells repeat along the output frame.

## Basic pack: PNGs only

The easiest pack contains a `frames/` folder and correctly named PNGs. No JSON is required.

```text
my-ui-pack/
├── readme.md                  optional
└── frames/
    ├── header.png
    ├── suggestion_chip.png
    ├── settings.png
    └── button.png
```

The folder name becomes the pack name. Re:TUI applies the ground rules automatically. A PNG that is not square or not sized in a 24-pixel step is rejected with guidance to fix it or add a manifest. Missing roles keep their existing/default appearance.

Canonical filenames are the role name plus `.png`, with `suggestion_chip.png` used for suggestion chips:

```text
status_group.png status_ram.png status_device.png status_time.png
status_battery.png status_storage.png status_network.png status_notes.png
status_weather.png status_unlock.png status_ascii.png output.png input.png
toolbar.png suggestion_chip.png music.png notifications.png modules.png
module_dock.png app_drawer.png widget_drawer.png keyboard.png files.png
overlays.png settings.png dialog.png header.png list_item.png
list_item_selected.png ui_input.png button.png button_pressed.png
button_primary.png icon_button.png toggle_off.png toggle_on.png
slider_track.png slider_progress.png slider_thumb.png controls.png
```

Replacing a PNG while keeping its filename requires no JSON change; reimport the folder to register the replacement.

After importing, use the pencil beside a pack to edit the installed slice pixels, displayed border thickness, edge modes, and center mode. Applying the form rebuilds that role immediately; reimport is only needed when PNG pixels change.

## Advanced pack: optional manifest

Power users may add `manifest.json` to override slicing, rendered border size, stretch/tile behavior, and center rendering. Each `file` must exactly match a PNG in `frames/`.

```text
my-ui-pack/
├── manifest.json
├── readme.md
└── frames/
    └── output.png
```

```json
{
  "type": "retui-frame-pack",
  "schema": 2,
  "name": "My UI pack",
  "filtering": "nearest",
  "roles": {
    "output": {
      "file": "output.png",
      "slicePx": { "left": 48, "top": 48, "right": 48, "bottom": 48 },
      "borderDp": { "left": 8, "top": 8, "right": 8, "bottom": 8 },
      "modes": {
        "left": "tile",
        "top": "tile",
        "right": "tile",
        "bottom": "tile",
        "center": "stretch"
      }
    }
  }
}
```

## Image rules

- Pack filenames are lowercase and use letters, numbers, `_`, or `-`.
- `slicePx` must leave at least one center pixel.
- Edge modes are `stretch` or `tile`; center also accepts `none`.
- Filtering is `nearest` for UI packs so pixel art remains crisp.
- Each PNG is limited to 2048 × 2048 and 4 MiB; a pack is limited to 32 MiB.
- macOS `.DS_Store` and `._` metadata are ignored.
- Use plain square sprites for generic panels and buttons, not dialogue sprites with pointer arrows.

The repository script `scripts/build_sprout_ui_pack.py` creates the advanced Sprout Lands example from the locally purchased source pack without committing its premium PNGs.
