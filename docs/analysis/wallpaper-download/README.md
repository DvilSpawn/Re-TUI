# In-launcher wallpaper downloads — 2026-10-16

Wallpaper settings → Get more wallpapers now opens a native, themed catalogue.
Each card loads its hosted animated preview, then offers Download → confirmation →
progress/cancel → automatic checksum-verified installation → Preview / Use.
Only the existing wallpaper editor's explicit Apply action changes the system wallpaper.

Fresh installs retain Solid, Sakura and Black Hole. Topo Noise, Pixel Dream, Clouds and
Nagomi become available after installing their existing data-only packs. The rendering
code remains in the launcher; all 38 optional stock PNGs were moved, without changing
their bytes, into `wallpaper-packs/content/`. Preview GIFs and pack archives are not
bundled in the APK. Downloads retain a copy in `Re-T-UI/wallpaper-downloads/`; rendering
uses a separate validated private copy. Personal backups exclude downloaded archives
and retain wallpaper preferences.

Saved optional selections and parameters survive upgrades/restores when their pack is
missing. The editor keeps the requested scene, shows a restore/download placeholder,
and blocks Apply/Save until installed. It does not save the service's temporary fallback
over the original selection. Returning after installation restores the same preview.
Missing artwork may temporarily render a safe fallback until the pack is restored.
Themer's direct system-picker route also passes through this restore flow when needed.

## Evidence

- Full unit suite: 445 total, 440 passed, five existing live IRC skips, zero failures/errors.
- Four physical-device flow checks: fresh picker has three entries; all four real hosted
  packs download/install and Clouds renders from downloaded artwork; missing selected
  scene/settings survive recreation and cannot be overwritten by Save; confirmation,
  cancellation, retry and explicit preview preserve the current selection.
- Gallery readability capture passed native-control checks in Catppuccin Latte/Mocha,
  with no failed/blocked samples. Both screenshots and GIF artwork were manually reviewed.
- Packager/APK checks passed. No optional image content hashes, stock filenames or GIFs
  are present in the final APK. All hosted pack/preview URLs and checksums were verified.
- Localization audit: zero resource errors. Normal signed/minified release build passed.

The missing-pack device test initially exposed an unsafe palette cast. It was fixed
with an explicit unavailable-preview case, then all four device flow checks passed.

Final APK SHA-256: `f4ff82b421e03b4e323a19f26b3550fa45d2537380d48340789f58172b9cc0d6`.
The build 503 update preserves the release signing identity and app data; phone read-back
confirms the installed bytes. Packs were tested in a disposable application and were
not preinstalled into the user's normal launcher. Evidence is under [evidence](evidence/).

No website deployment, source commit/push or app release was needed/performed. Existing
hosted downloads remain available; browsing and installation now happen inside Re:TUI.
Actual system wallpaper application and a broad device/palette sweep were not repeated.
