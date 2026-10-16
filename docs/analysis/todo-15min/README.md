# Fifteen-minute todo run — 16 October 2026

Window: 08:30–08:45 UTC (14:00–14:15 IST). Three agents plus parent; no commit, push, app release, or website deployment.

## Implemented

- Termux Apps: Classic and Search suggestions dispatch explicit workspace launches, preserving friendly labels and command priority. One-time migration retains commands, titles, and working directories; newer definitions win; legacy data remains archived. Legacy removal now uses the current registry.
- Native preset marketplace: `preset -market` and Settings → Presets → Preset marketplace. Previews the 21 currently hosted terminal palettes, confirms downloads, reports progress, supports cancellation/retry, validates the trusted HTTPS path/size/SHA-256 and existing archive rules, and imports without applying. Identical packages reuse the existing saved copy while same-name changed packages retain both. Success offers Copy apply command.
- Qalculate publication: all four canonical files were verified against the dedicated website source and staged there for tracking. No source commit or deployment was made.

## Verified

- Complete unit suite: 452 tests, 447 passed, five existing opt-in live IRC skips, zero failures/errors.
- Release lint: zero errors, 614 warnings, 311 hints. Signed normal release APK and Play AAB built successfully. Localization catalogue check and whitespace check passed.
- Five native setup fault fixtures and five actual host tmux dispatcher tests passed. Wallpaper packager and final APK artwork exclusion checks passed.
- Physical Vivo: actual Chrome marketplace download → Android chooser → Re:TUI → Dracula import passed with active XML unchanged. New marketplace live HTTPS catalog/confirmation/import/copy flow passed. Two marketplace backend tests passed, including duplicate/collision, corrupt hash, cancellation and no-apply checks. Three migration tests and actual Classic/Search chip dispatch passed. Three importer checks passed.
- Physical calculator: basic arithmetic preview without engine and real published engine download/install passed. Fresh wallpaper picker and missing-pack preference preservation passed. The all-four-pack live gallery initially timed out, then passed on an unchanged rerun, including actual Clouds preview. Treat the first timeout as intermittent flow reliability evidence, not erase it.
- Isolated API 35 emulator: default-launcher cancellation/setup completion, basic calculator preview, fresh wallpaper picker, and missing-pack preservation passed. Progress glyph timing initially failed with system animation scale zero, then passed with animations enabled. Migration and suggestion checks also passed.
- Normal signed build 503 installed in place, official signing identity preserved, installed bytes read back and matched. APK SHA-256: `3ec93f5b5d2c3d71398ee39fd3bafafce5f5fed33c084ea21bbc2a4f571d0bda`. See installed-release.json for APK/AAB provenance.

## Still open

1. Android 16 shared-file revocation regression: initial denial and grant checks pass, but the recipient can still read immediately after revoke during instrumentation. The same recipient is denied after instrumentation exits. Test-only fresh-worker diagnostics were added; production provider unchanged. An independent recipient app is needed to distinguish OEM/instrumentation behavior from a provider issue.
2. Pane appearance fixture: the old test assumes the retired transport, uses fake windows, and unhides no SSH tiles. Its geometry assertion fails on the default SSH build. Replace it with font/geometry/attachment assertions in the real paired SSH journey; do not count legacy clients[] assertions as SSH proof. Production settings hooks reach both grids and SSH resize by source inspection, not a fresh paired font test here.
3. Live emulator engine/wallpaper downloads timed out although phone downloads passed. Actual system wallpaper Apply was not exercised.
4. Real first-time missing OpenSSH/tmux installation remains fixture-tested, not executed in a disposable Termux installation.
5. Overnight/OEM/battery testing, a broad palette/device sweep, and retiring the remaining bespoke interactive bridge remain follow-ups.
6. Review and commit the intended launcher/source changes and the staged website files only when requested.

## Evidence notes

Phone preset and new-feature logs/screenshots are in preset/. Root phone-downloads-final.log records the earlier 4/5 pass and wallpaper timeout; wallpaper-diagnostic-final.log records the unchanged gallery rerun pass. The latter command attempted to install a logging-only test rebuild with the default debug signer and was rejected; it then ran the previously installed correctly signed test APK. No production source or normal app signer changed as a result. The diagnostic-only test edit compiled but was not installed.

No active preset or system wallpaper was applied. The user's normal application data/default Home role were preserved.
