# Hotfix 2 — Build 505

Version name 3, version code 505. Based on Hotfix 1 (f445614d).

## Changes

- Notes reuses the Help pane layout and window controller, overlay frame rendering and backdrop blur. Its toolbar sits inside the pane, with unboxed icons and keyboard-aware layout. Notes storage, locking and automatic saving are unchanged.
- Offline Help offers `10 Help home` from articles, categories and generated command references. It returns to the first root page; Topics still returns to the immediate list.
- Updated Notes, appearance-preview, Private Space, Qalculate and Termux help; exported the matching language-pack catalog/template.
- Appended Hotfix 2 to the in-app changelog and GitHub notes without replacing previous entries.

## Verification

- JVM suite: 472 passed, five skipped, zero failures/errors.
- Android 16 emulator: shared Notes pane/backdrop, transparent transitions with keyboard fit, Home reload/recreation, locked-note preservation and interrupted-write recovery passed.
- Help navigation: direct article-to-home, tapped category-to-home and generated command-reference-to-home from the second root page passed.
- Signed, minified APK: appended changelog, article-to-Help-home and shared Notes pane/backdrop checks passed.
- The pre-release debug changes were installed in place on the Vivo over wireless debugging. This does not imply the production APK was installed there.

Build logs, signed-artifact checks and screenshots are retained locally in `.local-context/releases/build505-hotfix2/`. The Play Store AAB is a local upload artifact, not a Play Console publication. Full landscape/custom-frame visual coverage and the complete instrumentation suite were not run for this hotfix.
