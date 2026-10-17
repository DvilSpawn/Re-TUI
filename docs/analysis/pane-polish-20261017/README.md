# Widget editing, home return, and panel blur

Changes are in `dvil/termux-ssh-analysis`; no release or everyday-phone package update is implied by these checks.

## Behavior

- Widgets use uniform cell tracks, including deliberately empty rows. The previous GridLayout could collapse those rows while retaining extra height, leaving widgets and the scroll extent inconsistent. The board now scrolls to the widgets' actual positions and displays a scrollbar.
- Four small corner strokes replace the opaque arrow controls. Transparent touch targets retain accessible click/long-click resize actions. Editing allows direct dragging, and Remove lives in the toolbar. Native RemoteViews retain their own font and size.
- Provider minimums include the new outer frame inset. Provider maximums limit resizing rather than allowing up to 120 rows regardless of metadata. Missing maxima keep the existing free resize behavior.
- Widget command chips share the launcher chip factory, preference-derived spacing, font, and natural height.
- Warm resume no longer schedules repeated full-view font traversals or activates the input twice. Explicit UI/font changes still schedule one coalesced frame callback. Removed forced startup garbage collection. Immutable frame bundles use a bounded 4 MiB parsed-byte cache; unchanged exported PNGs are reused, saved changes invalidate the cache, deleted exports rebuild, and URI grants still refresh on every share.
- Settings keeps the launcher beneath its translucent window. Shared tool panes request native window blur behind their sharp content. Embedded panes blur only visible, overlapping predecessor layers; effects are reference-counted and removed on hide/detach. Side-by-side panes that do not overlap are excluded. Older/unsupported window blur uses dimming. Settings retains its chosen panel alpha without rewriting preset values; contrast safeguards remain on its text/rows.

## Test boundary

Vivo I2220, Android 16/API 36, hardware serial `10BE1C0FB40006A`, wireless debugging. All installs/tests use `com.dvil.tui_renewed.termuxsshdebug` and its test package. The everyday `com.dvil.tui_renewed` package and private data are untouched. Test AppWidget IDs and temporary configuration folders are cleaned up by each fixture.

The widget fixture is a real AppWidget provider from the test APK, not a launcher imitation. Screenshots show that fixture. It exercises native clicks, retention across edits, dragging, resize anchors, retry, picker search, keyboard insets, activity recreation, and Space ownership protection. The new checks additionally assert uniform empty-row geometry, real swipe scrolling, equal launcher/widget chip height, immediate Edit-mode movement, four corner grips, transparent Settings/window blur, and cleanup of embedded blur.

## Evidence

- Final source APK and Android test APK build successfully. Android lint passes with zero errors (620 warnings and 311 hints remain). The full unit suite has 463 cases: 458 passed, five opt-in live-network cases skipped, zero failures.
- `phone-portrait-release-check.txt`: 13 passing checks (widget board, blur lifecycle, frame cache, Settings/editor recreation).
- `phone-landscape-final.txt`: nine passing checks (widget board, blur lifecycle, frame cache).
- `phone-blur-disabled.txt`: one passing Settings check while the system reports `mBlurEnabled=false`; original `disable_window_blurs=0` restored afterward.
- `phone-picker-final-api-guard.txt`: the picker check passes again after guarding its API 26 autofill call for the app's API 23 minimum.
- Both controlled framed-keyboard benchmarks pass ten warm-home cycles and retain the same launcher UI instance.

| Framed keyboard measurement | Before | After |
| --- | ---: | ---: |
| Home first-draw median | 66.40 ms | 41.41 ms |
| Home first-draw p95 | 87.67 ms | 53.67 ms |
| Home frame p95 | 60.66 ms | 32.56 ms |
| Frames over 32 ms | 24 / 92 | 6 / 98 |
| Frames over 100 ms | 0 | 0 |

These ten-cycle results show a 37.6% lower first-draw median on this phone/configuration; they are not a population-wide latency guarantee. Frame counts cover the launcher window while leaving/returning from Android Settings, including the settling interval.

Screenshots: `widgets-edit.png` (small corner grips), `widgets-scroll-chips.png` (real widget board after scrolling), `settings-transparent-blur.png`, the `-landscape.png` variants, and `settings-dim-fallback.png`.

Earlier failure logs are retained only as iteration history; use the final acceptance logs for the result.

The initial keyboard-hidden timing data was saved before a test-only double-listener-removal cleanup failure; that cleanup is fixed in the test. The baseline was 36.99 ms median / 43.70 ms p95 over ten returns from Android Settings, with no frame over 32 ms. A later early candidate was 41.66 / 56.95 ms with two frames over 32 ms; these small runs do not establish an improvement for that lightweight path. The framed-keyboard comparison explicitly enables automatic keyboard opening and applies the bundled frame pack, where the redundant export work is exercised.

For the controlled framed-keyboard comparison, `before.apk` restores the original LauncherActivity, FrameManager and RetuiThemeBridge from the current commit and restores the old resume font scheduling/duplicate send in UIManager. Other pane changes and the same test fixture remain constant. The source files are saved and restored byte for byte by `/tmp/retui-home-before-build.py`; no Git reset or user-work deletion is used. The APK hashes and measured data are recorded in `validation.json`.

## Remaining checks

- Wider third-party widget/OEM compatibility, large accessibility font settings, and additional users' home-lag configurations.
- Physical API 23–30 fallback rendering; the API guards and dim path do not replace a device check.
- This is focused local/device evidence, not a production release.

The isolated app/test packages were removed after verification. The phone returned to its ordinary home screen; keyboard, rotation, and system blur preferences were preserved. No app release, push, or commit was made.
