# Settings always expanded

Removed `HelpWindowPanel` from both the Settings hub and its editor. Their surfaces now fill the existing safe, fold-aware viewport directly, with no resize grips, drag handler, maximize/reset buttons, floating geometry restoration, or geometry writes. The terminal border/title, theme transparency, backdrop blur, keyboard/system insets, and draft state remain. Removed unused header space reserved for the window controls.

Existing floating-window preferences are ignored rather than rewritten. Calc, Help, IRC, and widget panes keep their existing window behavior.

Validation: app/test APK builds passed; 458 unit cases passed, five opt-in network cases skipped. Six API 35 emulator checks passed, covering forced expansion despite old saved geometry, no window controls, header dragging/recreation, editor XML preservation, typography/navigation drafts, frame edit-session retention, and backdrop blur/cleanup. See `emulator-tests.txt`, `settings-expanded.png`, and `editor-expanded.png`.

The signed regular-package update was installed in place on the Vivo after wireless debugging reconnected. Installed APK bytes match the prepared artifact, the signing identity matches, and the original install time/app-data directory identities were retained. The updated launcher opens successfully. See `validation.json` and `install.txt`. No commit or release was made.
