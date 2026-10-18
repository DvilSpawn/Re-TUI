# Widget activity backdrop — 2026-10-17

**Superseded:** this approach still clipped blur below visible system bars. The [subsequent full-window correction and 20 phone checks](../widgets-window-blur-20261017/README.md) replace its temporary launcher-background/window mutations with the same translucent-window blur path as Settings.

The opaque layer was the launcher's outer activity backing. The widget board retains its configured `widget_panel_background_color`, frame, header, controls, and native content. No theme XML or widget data is rewritten.

While widgets are visible, the outer root/window backing becomes transparent and uses the same window blur/dim policy as Settings. Launcher content stays visible beneath the existing embedded backdrop blur. Native window blur also covers the wallpaper below the activity. Widget content remains sharp. Closing or detaching the pane restores the original root drawable, window background, pixel format, blur radius, dim amount and owned flags, and releases blur listeners. The pane remains expanded with its existing margins, keyboard behavior and resize controls.

`PanelBackdrop.installWindow` now follows pane visibility and restores the window state. Widgets opt into its transparent-backing support; Settings uses the same blur policy without changing its panel rendering. Platform behavior follows [Android's window blur documentation](https://source.android.com/docs/core/display/window-blurs).

Eight focused checks passed on the physical Vivo/API 36: three backdrop/Settings/cleanup checks in portrait, the same three in landscape, and native live resize/cancellation plus keyboard clearance. The new check explicitly renders an **opaque** configured board fill, verifies that fill remains exact, and separately checks that the outer root is transparent, window blur is enabled, underlying launcher content is blurred, and all state is restored on close. In landscape the launcher is blurred through its split-container ancestor; the initial direct-child-only test assertion was corrected to account for that hierarchy.

The normal signed Play Store debug build 503 was installed in place on hardware serial `10BE1C0FB40006A`. Before/after APK signatures match the existing identity; installed bytes match the candidate SHA-256 `689c9d3d7c3199d97658f0d56c4605be7eed64f64288892c2ce4a6b99da71ed1`. HOME, first-install time and CE/DE data inodes were freshly checked before and after and remained unchanged. Focused tests ran in the separate `.termuxsshdebug` package.

This supersedes the local release artifact checkpoint for this UI change: the earlier minified APK/AAB do not contain this backdrop correction. No fresh full unit/lint/minified release pass, Git commit, push, public release, or store upload was performed for this focused fix.
