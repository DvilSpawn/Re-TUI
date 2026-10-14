# Terminal color presets and readability evidence

The active catalog contains only the 21 palettes in the Term gallery's featured
“Start here” section. The full 614-palette source snapshot remains archived for
provenance; other palettes are not packaged or offered in the marketplace.
The source JSON, original page checksums, source
links, upstream MIT license and author credits live in `scripts/terminal_colors/source`.
Source refresh is explicit; ordinary generation and testing are offline.

## Package and conversion contract

Every download is a schema-2 `.retui` ZIP with exactly `manifest.txt`, `theme.xml`
and `suggestions.xml`. It uses the existing shareable preset importer and apply
path. Import saves a uniquely named library entry without applying it. Apply
replaces theme/suggestion configuration and disables wallpaper auto-colour.
The existing importer fills suggestion defaults; these packages follow that
behavior. Fonts, layout, behavior, wallpaper and frame files are not packaged.

`coverage.json` lists every Theme role, its source palette color, background
relationship and contrast target. Missing/new roles or activities fail registry
validation. The registry ties surfaces to roles and rendering checks, and freezes
pane-opening methods until their new coverage is registered. Foreground adjustments
interpolate toward black or white and visit every quantized RGB value on each ray,
including values between fixed 1/255 steps. They choose the first passing color
on each path, then the smaller squared RGB
distance. Original and adjusted values are retained in generated evidence.
Normal, bright and semantic ANSI colors target 4.5:1 on the default terminal
background. Text targets 4.5:1; essential icons/controls target 3:1. Values are
compared without rounding. Shadows/scrims are decorative and disabled text is
reported separately.

These are Re:TUI adaptations rather than exact terminal palette reproductions.
Arbitrary application-supplied terminal background colors, truecolor and 256-color
content can still be unreadable. The suite preserves and labels that content.

## Rebuild and validate

From the launcher checkout:

```sh
python3 scripts/terminal_colors/snapshot.py --refresh  # only to refresh the pinned source
python3 scripts/terminal_colors/generate.py --site /Users/mako/StudioProjects/Re-TUI-site
python3 scripts/terminal_colors/test_catalog.py
```

Generation writes marketplace downloads/catalog/license/credits and identical
test APK assets. It does not publish. `generated/palettes.json` is reproducible
local data, including role checks and adjustment history.

Run the site tests using a working Node runtime:

```sh
node --test /Users/mako/StudioProjects/Re-TUI-site/tests/*.mjs
```

The terminal-colors test runs every actual download through the marketplace's
existing package validator and checks all sizes/checksums, filtering, safe asset
paths and MIME handling. Existing preset tests cover corrupt/unsafe files,
legacy packages and cancellation/name collision behavior.

Build the Play Store debug APK and instrumentation APK with `-PfetchDebug=true -PpaletteDebug=true`
to isolate tests from the installed launcher. The existing fetchDebug variant
filter disables release configurations, so run `:app:validateRuntimeNotices`
without that flag and exclude the already-validated task during the isolated
debug build. Do not change release notice policy.

## Device run and resume

```sh
python3 scripts/terminal_colors/run.py --serial emulator-5582 \
  --output /Users/mako/StudioProjects/TUI-ConsoleLauncher-artifacts/terminal-colors/baseline \
  --install
```

The host runs one palette per instrumentation process, explicitly force-stops
the app, then runs saved-color read-back in a new process. Resume requires both
APK checksums, the package checksum, requested surfaces and configuration to
match. Blocked or incomplete runs are retried on resume; completed readability
failures remain visible without repeating their captures. Per-theme logs and
partial JSON are written as scenes progress; missing captures are never passes.
Use `--app-apk` and `--test-apk` with frozen APKs and their adjacent
`output-metadata.json` files to resume a recorded build after later builds.
The host reads the catalog from the actual test APK. Local JSON/HTML evidence
uses transparent filesystem compression on macOS; its bytes remain unchanged.

Use `--themes catppuccin-mocha,catppuccin-latte` for a pilot. `--surfaces` selects
registered surface ids. Settings scenes capture each section and setting type,
not only the first visible settings page. IRC is a synthetic offline session.
The embedded Termux/workspace scenes use terminal fixtures and never replace a
phone's Termux helper. Interactive control-state crops are drawn by the actual
Views without invoking their actions. Primary pane and error captures come from
UiAutomation. Settings row types, preview cards and other input-state captures
also include direct draws of the actual focused window, labeled `View.draw` in
the report. Transparent IME proxy fields are excluded from visible text checks.
Native cursors are measured only when shown with a collapsed selection.

Run selected palettes with `--configuration landscape`, `large-text`, `wallpaper`
or `frames` into separate output directories. Stress fixtures are emulator-only.
Large-text uses Android font scale 1.5; landscape uses the launcher's landscape
preference; wallpaper uses a black/white checkerboard; frames use the existing
bundled Sprout Lands artwork, whose colors are not adapted by these presets.
Baseline runs reset emulator font scale to 1.0. Frame state is restored after each
test. The disposable emulator owns the wallpaper fixture.

A phone sample requires `--phone`, explicit `--themes`, explicit `--surfaces`
and a discovered device serial. The separate `.palettedebug` package prevents
changing the installed launcher. Fixture XML and existing shared preferences
are restored; imported fixture presets and the synthetic widget are removed.
Select only safe launcher-owned surfaces; OS authentication/pickers and external
Android widgets are outside launcher control.

## Offline report

```sh
python3 scripts/terminal_colors/report.py \
  --evidence /Users/mako/StudioProjects/TUI-ConsoleLauncher-artifacts/terminal-colors/baseline \
  --output /Users/mako/StudioProjects/TUI-ConsoleLauncher-artifacts/terminal-colors/report \
  --site /Users/mako/StudioProjects/Re-TUI-site --sync-previews
```

Report generation requires Pillow. Open `report/index.html`: filter by name,
appearance or result, then select any matrix cell for screenshots, marked failure
crops, per-element colors/ratios and control-state evidence. JSON/CSV summaries
are included. The report works from local files without a server or network.
Source color checks and actual renderer evidence are separate. Report arithmetic
rechecks exported composite colors independently of Android.
Drawable backgrounds are sampled at their centers and composited through the
view hierarchy. Nonuniform artwork needs screenshot review; these measurements
do not certify every pixel behind text in wallpaper or frame images.

Statuses are `fail`, `blocked`, `untested`, `manual review required`, or per-element
`pass`. Completed captures still require visual review. The suite never labels a
palette verified merely because colors passed calculations. Untinted icons,
OS-rendered error popups, clipping/overlap, and external widgets require review.
Phone evidence is a separate run/report; it is never promoted to full-device
coverage. Marketplace previews are actual home-screen captures when available;
missing captures explicitly show a palette swatch preview and pending status.

The marketplace section is static and needs no D1 migration. Its download route
uses the existing Pages static asset service and sets `application/vnd.retui+zip`.
Public publishing requires separate authorization and download/hash read-back.

## Recorded local review, 14 October 2026

Only the 21 featured palettes are active in marketplace downloads and current
test assets. The earlier full-gallery report/evidence is archived separately.
All 21 packages pass the existing marketplace validator and actual local Pages
download checksums/MIME checks. Import without application, explicit application,
saved-value reload and process restart are checked for every palette.

The baseline records all 65 surfaces for every palette. Catppuccin Latte/Mocha
have full-surface stress runs; Material Dark/Gruvbox Dark add 12-surface stress
samples as the lowest-contrast featured adaptations. Physical-phone evidence
covers two palettes and six panes. Native phone cursor colors and existing frame
contrast failures remain visible. Screenshots still require visual review; no
palette is marked verified. The phone's separate preset URI-revocation regression
also failed, while all 29 emulator regressions passed. Full validation logs are
included with the offline report. Nothing has been publicly deployed.
