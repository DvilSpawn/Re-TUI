# Re:TUI 3 — build 503 RC1

Local release candidate prepared on 17 October 2026. Signed APK and Play Store AAB, checksums, R8 mapping, base commit and the current application-source patch are retained under `.local-context/release-candidates/build503-rc1/`, outside Gradle's cleanable build output. This is a candidate for final user acceptance; no public release, store upload, Git tag, push or new commit occurred.

## Current gates

- Full unit suite: 473 cases, **468 passed**, five existing opt-in skips, zero failures/errors.
- Release APK, bundle and release lint: **BUILD SUCCESSFUL**, no bypasses; lint zero errors, 621 warnings and 311 hints.
- Both artifacts signed and signatures verified; APK passes 16 KB ZIP alignment verification.
- Physical Vivo/API 36: **11 checks passed in portrait and 11 in landscape** on the final implementation, including real edge Back gestures, visible system bars, Settings, native widget resizing/cancellation, keyboard layout, scrolling, chip height, picker, recreation and binding/Space safety.
- Exact minified, non-debuggable release APK on the isolated API 36 emulator: native widget retained across upgrade, full-window blur and blur radius verified, real Back gesture closes commands before closing the pane, and reopening retains the widget. Standard Android Private Space cancel → retry authentication → PRI tap launched the fixture in private user 11 (absent owner 0); relocking removed private discovery.
- The **exact release APK**, not the previous debug candidate, is now installed in place on the physical Vivo. Installed bytes match the RC artifact; signing identity, HOME, first-install time and both data inodes are unchanged. Package flags confirm it is non-debuggable.

## Issues closed during this checkpoint

Release lint detected `GestureBackNavigation`: Android 16 gestures can bypass the legacy `Dialog.onBackPressed` override. The widget pane now uses the already-installed AndroidX `ComponentDialog` and lifecycle-aware `OnBackPressedDispatcher`. No dependency was added and the lint rule was not suppressed. The new physical gesture check verifies that the first gesture closes the commands surface and the next closes the pane; the minified release also passes that sequence.

Two earlier complete portrait runs failed the board swipe assertion while the focused unchanged test and landscape passed. The fixture had injected back-to-back events with future timestamps and unspecified input source. It now performs a real timed touchscreen swipe through Android input, retaining the same scrolling and chip-height assertions. Both final complete orientation runs passed. Initial error logs are retained here rather than counted as passing results.

## Artifact identity

| Artifact | SHA-256 |
| --- | --- |
| APK | `76f407531f0f6699ea26e1f85ad4a24876f8f1099bb6d0b91e68515dbee855e8` |
| AAB | `06da326a5cc76ffd99b5c2829a6202268780059a72eb63c5e8e77eea4d0f2ae6` |

Source branch: `dvil/termux-ssh-analysis`. Base commit: `7703f3b2dbbd059ad6ae9f8910136089189782e0`, plus the captured uncommitted application-source patch whose hash is in `validation.json`. Production source did not change after the final successful gates; the subsequent swipe correction changed only the test fixture. New work should use this checkpoint's artifacts rather than older build-503 files.

## Acceptance and coverage boundary

The user still owns the final widget appearance/fluidity verdict. Vivo's native Private Space incompatibility remains explicitly disclosed behind a default-off opt-in; standard Android emulator success does not resolve that OEM behavior. Longer OEM/battery and broad third-party widget/palette coverage remain follow-ups. Earlier feature and network evidence is linked from `CHAT_TODO.md`; those journeys were not all repeated for this final UI/Back correction. The exact RC smoke checks and the current release gates above are the measured boundary.
