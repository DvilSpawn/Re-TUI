# Themer color map

Re:TUI uses shared base colors and optional surface overrides. A surface set to
`AUTO` follows its documented parent. An explicit hex color overrides that parent;
`#00000000` means transparent, not AUTO. Each setting description names the parent
or blend used by AUTO.

## Shared colors

| Setting | Controls |
| --- | --- |
| `terminal_window_background_color` | Shared panel background and AUTO general buttons |
| `output_text_color` | Shared text and AUTO general buttons/list rows |
| `terminal_border_color` | Shared borders and AUTO accent/focus colors |
| `terminal_header_background_color`, `terminal_header_border_color` | Shared title/tab surfaces |
| `button_*`, `list_*`, `tab_*`, `field_*` | General controls; override these to customize controls independently |
| `selection_*`, `pressed_*`, `disabled_*` | General control states; AUTO uses general button colors and the shared accent |
| `success_text_color`, `warning_text_color`, `error_text_color` | Messages/states; AUTO uses built-in green, yellow, orange, independently of battery colors |

## Surface colors

| Section | What changes |
| --- | --- |
| Launcher backgrounds | `background_color` and wallpaper overlays tint the launcher or Settings wallpaper |
| Status lines | Per-line text/background/shadow colors affect that status line; battery thresholds affect battery text |
| Input/output | Launcher text, field backgrounds, cursor, link/system-event text and shadows |
| Suggestions | Container background plus per-type app, alias, command, contact, song, file colors; these do not set shared UI selection colors |
| Modules | `module_button_background_color` and `module_text_color` seed module actions, dock shortcuts, and active items. Advanced `module_*` colors override their own module surfaces |
| Widgets | `widget_*` colors seed widget panels, headers, rows, and controls, independently of module overrides |
| Workspaces | `workspace_*` colors affect workspace panels and calculator/shared Notes appearance. Controls inherit their workspace panel; headers inherit workspace text |
| App drawer | `app_drawer_*` colors affect its panel, headers and app-list rows. `app_drawer_group_*` controls category buttons; `app_drawer_alphabet_*` controls alphabet buttons. Each rail has separate normal and selected background, text and border colors |
| Settings/dialogs | `settings_*` colors affect Settings; `dialog_*` defaults follow Settings. Rows use general control colors |
| Floating overlays | `overlay_*` colors affect floating panels; AUTO follows shared terminal colors |
| ANSI palette | Normal/bright ANSI text in Termux panes. AUTO can follow the shared theme; changing ANSI does not recolor widgets or workspace backgrounds |

Widget rows and workspace input/output retain their original darkening with fixed
black blends (22%/16% for widget rows/commands, 16%/10% for workspace input/output).
These blends preserve transparency and ignore ANSI Black. Colors supply the fill
and tint within existing frames; changing a color does not replace custom frames.

App-drawer rail backgrounds set to AUTO follow `app_drawer_panel_background_color`,
not the transparent app-list rows. Their normal text and borders follow the drawer
panel too. Selected rail colors default to the shared `app_drawer_selection_*`
roles, but `app_drawer_group_selected_*` and `app_drawer_alphabet_selected_*` can
override each rail independently. Explicit transparent colors remain transparent.
Saved per-group background/text colors still override unselected category buttons;
they do not affect alphabet buttons. Active custom frames keep their existing fill
behavior; these color settings do not recolor frame artwork.

## Auto Theme

`wallpaper -auto` applies a one-time palette from the **home-screen wallpaper**.
On Android 12 and later, if wallpaper colors are missing or unavailable, Re:TUI uses
Android's public system accent/neutral palette and reports that fallback. Samsung
may supply its selected system palette rather than colors from the current image.
Older devices require the wallpaper-color API (Android 8.1+); if no source succeeds,
Re:TUI reports failure and retains the current theme and wallpaper settings.

Auto Theme updates base theme and suggestion colors. Explicit advanced surface
colors remain unchanged; AUTO surfaces follow the new base colors. To let a
customized advanced surface follow Auto Theme again, set it to AUTO. Wallpaper
changes do not continuously recolor the launcher: rerun the command when desired.
Startup Auto-theme follows the same rules and reports the result.

Android documents nullable [wallpaper colors](https://developer.android.com/reference/android/app/WallpaperManager#getWallpaperColors(int))
and public [system palette resources](https://developer.android.com/reference/android/R.color#system_accent1_500).

`debug -settings` includes `auto_theme_source` (`wallpaper`, `system`, or
`unavailable`) and `auto_theme_failure` for the last attempt. These diagnostics are
in-memory only; no wallpaper files, screenshots, or private content are recorded.

## Existing presets and backups

Setting names and explicit values remain compatible. AUTO now respects section
boundaries: old presets that relied on module colors to recolor unrelated controls
may look different. Override the appropriate general/widget/workspace role if you
want that previous color. Imports and upgrades do not rewrite saved colors.
