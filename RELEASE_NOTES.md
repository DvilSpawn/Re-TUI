# Re:TUI V.2 - Build 419

Build 419 is a reliability update for Android 16, notifications, media sessions,
outlined text rendering, and the preset marketplace.

## Fixes

- Back and edge-swipe gestures on Android 16 now close Launcher surfaces without
  restarting the default Home activity.
- Hardened the persistent notification service against Android restarting it
  without an Intent.
- Hardened media-session changes when playback state or controller entries
  disappear during a callback.
- Limited outlined text rendering to 1–8 passes, including hand-edited settings,
  to prevent unbounded redraw work.

## Preset marketplace

- Run `preset -market` to open `https://re-tui.pages.dev/marketplace`.
- `-market` appears with the other options when typing `preset`.

## Downloads

- Install the APK to update Launcher.
- Translators can use the refreshed English template and example language pack.
- The Play Store bundle is distributed through Google Play, not this GitHub release.

Version 2, version code 419.
