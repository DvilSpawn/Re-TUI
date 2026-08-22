# Re:T-UI UI Package Maker specification

Status: v1 implementation contract  
Target: Re:Live browser tile creator → Re:T-UI Launcher frame-pack schema 2

## 1. Goal

Add a **Re:T-UI Package Maker** workspace to Re:Live. A creator assigns PNG textures to named launcher roles, previews the same nine-slice behavior used on Android, and exports an importable UI-pack folder without hand-editing JSON.

The maker owns manifest generation and validation. Normal creators work with role names and visual controls. Advanced creators may unlock the exact manifest values.

## 2. Exported package

```text
my-ui-pack/
├── manifest.json
├── readme.md
└── frames/
    ├── output.png
    ├── header.png
    ├── suggestion_chip.png
    └── ...only the roles included by the creator
```

Rules:

- Export lowercase `manifest.json`, `readme.md`, and `frames/` at the selected folder root.
- Do not add thumbnails, project files, nested folders, or unused PNGs to the package.
- The maker should offer **Save Folder** when browser directory access is available.
- It may also offer **Download ZIP**. The user must extract that ZIP before choosing the folder in Launcher.
- `readme.md` is generated from the pack settings and lists every included role and filename.

## 3. Creator modes

### Easy mode

Easy mode is the default.

- Canvas is square.
- Side length is a 24-pixel step: `24`, `48`, `72`, `96`, and so on.
- The canvas is an exact `3 × 3` grid of equal square cells.
- Slice pixels are automatically `side ÷ 3` on all four sides.
- Corners remain fixed.
- All four edge cells repeat along their edge.
- The center cell stretches.
- Displayed border thickness defaults to `8dp` on all sides.
- Filtering is always nearest-neighbor.

The maker shows the grid and prevents an export that violates these rules.

### Advanced mode

Advanced mode unlocks:

- independent left, top, right, and bottom slice pixels;
- independent displayed border thickness in dp;
- `tile` or `stretch` for each edge;
- `stretch`, `tile`, or `none` for the center;
- non-square source PNGs.

Nearest-neighbor filtering remains mandatory for UI packages. Invalid values must be shown beside their visual control; creators should not need to inspect raw JSON.

## 4. Role catalog

Every role has a fixed manifest key and canonical filename.

### Status

| Manifest role | PNG filename | Label |
|---|---|---|
| `status_group` | `status_group.png` | Unified status group |
| `status_ram` | `status_ram.png` | RAM status |
| `status_device` | `status_device.png` | Device status |
| `status_time` | `status_time.png` | Time status |
| `status_battery` | `status_battery.png` | Battery status |
| `status_storage` | `status_storage.png` | Storage status |
| `status_network` | `status_network.png` | Network status |
| `status_notes` | `status_notes.png` | Notes status |
| `status_weather` | `status_weather.png` | Weather status |
| `status_unlock` | `status_unlock.png` | Unlock status |
| `status_ascii` | `status_ascii.png` | ASCII status |

### Terminal and launcher

| Manifest role | PNG filename | Label |
|---|---|---|
| `output` | `output.png` | Terminal output |
| `input` | `input.png` | Terminal input |
| `toolbar` | `toolbar.png` | Toolbar buttons |
| `suggestions` | `suggestion_chip.png` | Suggestion chips |
| `controls` | `controls.png` | Other launcher controls |

### Modules and apps

| Manifest role | PNG filename | Label |
|---|---|---|
| `music` | `music.png` | Music widget |
| `notifications` | `notifications.png` | Notification widget |
| `modules` | `modules.png` | Modules |
| `module_dock` | `module_dock.png` | Module dock |
| `app_drawer` | `app_drawer.png` | App drawer |
| `widget_drawer` | `widget_drawer.png` | Widget drawer |
| `keyboard` | `keyboard.png` | Re:T-UI Keyboard |
| `files` | `files.png` | Re:T-UI Files |
| `overlays` | `overlays.png` | Overlay windows |

### Settings and lists

| Manifest role | PNG filename | Label |
|---|---|---|
| `settings` | `settings.png` | Settings and dialogs |
| `dialog` | `dialog.png` | Dialogs |
| `header` | `header.png` | Headers |
| `list_item` | `list_item.png` | List items |
| `list_item_selected` | `list_item_selected.png` | Selected list items |
| `ui_input` | `ui_input.png` | Settings inputs |

### Buttons, toggles, and sliders

| Manifest role | PNG filename | Label |
|---|---|---|
| `button` | `button.png` | Buttons |
| `button_pressed` | `button_pressed.png` | Pressed buttons |
| `button_primary` | `button_primary.png` | Primary buttons |
| `icon_button` | `icon_button.png` | Icon buttons |
| `toggle_off` | `toggle_off.png` | Toggle off |
| `toggle_on` | `toggle_on.png` | Toggle on |
| `slider_track` | `slider_track.png` | Slider track |
| `slider_progress` | `slider_progress.png` | Slider progress |
| `slider_thumb` | `slider_thumb.png` | Slider thumb |

Missing roles are allowed. They retain the user's existing or default launcher appearance.

## 5. Manifest contract

The maker always exports schema 2:

```json
{
  "type": "retui-frame-pack",
  "schema": 2,
  "name": "Leafy UI",
  "filtering": "nearest",
  "roles": {
    "output": {
      "file": "output.png",
      "slicePx": {
        "left": 48,
        "top": 48,
        "right": 48,
        "bottom": 48
      },
      "borderDp": {
        "left": 16,
        "top": 16,
        "right": 16,
        "bottom": 16
      },
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

Manifest rules:

- `type` is exactly `retui-frame-pack`.
- `schema` is exactly `2`.
- `name` is trimmed and contains 1–80 characters.
- `filtering` is exactly `nearest`.
- `roles` contains at least one known role.
- Every role object contains exactly `file`, `slicePx`, `borderDp`, and `modes`.
- `file` matches `[a-z0-9][a-z0-9_-]*\.png` and exists directly inside `frames/`.
- `slicePx` contains positive whole-number `left`, `top`, `right`, and `bottom` values.
- `left + right < image width` and `top + bottom < image height`.
- `borderDp` contains finite `left`, `top`, `right`, and `bottom` values from `0` through `256`.
- Each edge mode is `tile` or `stretch`.
- Center mode is `stretch`, `tile`, or `none`.
- With a manifest present, `frames/` contains exactly the referenced PNG filenames—no extras.

## 6. Browser project model

The browser may use an equivalent structure internally:

```ts
type EdgeMode = "tile" | "stretch";
type CenterMode = "stretch" | "tile" | "none";

type Sides<T> = {
  left: T;
  top: T;
  right: T;
  bottom: T;
};

type RetuiRoleDraft = {
  role: string;
  fileName: string;
  png: Blob;
  width: number;
  height: number;
  slicePx: Sides<number>;
  borderDp: Sides<number>;
  edgeModes: Sides<EdgeMode>;
  centerMode: CenterMode;
};

type RetuiPackDraft = {
  name: string;
  roles: Map<string, RetuiRoleDraft>;
};
```

The exported manifest is derived from this model. JSON is an output, not the source of truth for the editor.

## 7. Required maker flow

1. Enter pack name.
2. Choose a role from the catalog.
3. Draw a new tile or import a PNG.
4. In Easy mode, choose a 24-step canvas size and edit with a visible `3 × 3` overlay.
5. Preview corners, edges, and center at several target sizes.
6. Optionally unlock Advanced mode for the selected role.
7. Repeat for any number of roles.
8. Run package validation.
9. Save the folder or download a ZIP.

Useful role actions:

- duplicate one role's texture/settings into another role;
- replace PNG while preserving role settings;
- reset the selected role to Easy defaults;
- remove a role from the package;
- show canonical filename beside every role label.

## 8. Preview behavior

The browser preview must match Launcher:

- Disable canvas image smoothing before every draw.
- Use nearest/pixelated rendering in the surrounding UI.
- Divide the source using `slicePx` into nine regions.
- Scale the four corners to their `borderDp` destination rectangles.
- Tile an edge only along its long axis; size it from the destination edge thickness on the cross axis.
- Stretch an edge across its destination rectangle when its mode is `stretch`.
- Stretch, tile, or omit the center according to `centerMode`.
- If the preview is smaller than the combined borders, reduce all four displayed borders by the same fit factor so they do not overlap.

Recommended adjustable preview presets:

- Output: `320 × 120dp`
- Panel: `320 × 480dp`
- Button: `160 × 44dp`
- Suggestion chip: `120 × 36dp`
- Icon button: `44 × 44dp`

Treat one CSS pixel as one dp for preview sizing. Provide zoom separately; zoom must not change the logical preview dimensions.

## 9. Validation and limits

Block export when any rule fails.

- Maximum PNG dimensions: `2048 × 2048`.
- Maximum encoded PNG size: `4 MiB` each.
- Maximum combined imported package data: `32 MiB`.
- Maximum manifest size: `32 KiB` UTF-8.
- Filenames are lowercase and contain only letters, numbers, `_`, and `-` before `.png`.
- Reject duplicate filenames, unknown roles, empty PNGs, corrupt PNGs, nested folders, and slice values that remove the center.
- Ignore `.DS_Store` and macOS `._` metadata if encountered during project import, but never export them.

Validation messages should identify the role and the fix, for example:

> `output.png`: Easy textures must be square and sized in 24-pixel steps. Resize it or switch this role to Advanced mode.

## 10. Acceptance examples

### Leafy output

- PNG: `144 × 144`
- Grid cells: `48 × 48`
- Slice px: `48, 48, 48, 48`
- Border dp: start at `16, 16, 16, 16`
- Edge modes: `tile, tile, tile, tile`
- Center: `stretch`

The preview must show the leafy perimeter. Increasing border dp to `20` or `24` makes more edge detail visible without changing the source cuts.

### Easy 48-pixel button

- PNG: `48 × 48`
- Grid cells and slices: `16 × 16`
- Border dp: `8` on all sides
- Edges: tile
- Center: stretch

### Invalid Easy asset

`106 × 122` is rejected in Easy mode. It is allowed only after switching that role to Advanced mode and supplying valid custom slices.

## 11. Launcher handoff checklist

An exported package is complete when:

- the selected folder has only `manifest.json`, `readme.md`, and `frames/`;
- every included role uses its canonical filename;
- generated JSON passes all schema and image-bound checks;
- the preview remains crisp at 1×, 2×, 3×, and non-integer zoom;
- the package imports through **Settings → Appearance → Frames → Import UI Pack Folder**;
- applying the pack changes only included roles;
- the Launcher's pack pencil can subsequently adjust installed slice/border/mode values without reimporting PNGs.

