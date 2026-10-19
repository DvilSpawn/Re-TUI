# Hotfix #1 — Build 504

Release date: 2026-10-19. Version name remains 3; version code advances from 503 to 504.

## Changes

- Compact, swipeable Settings previews for Status, Terminal, Music, Help and Calculator. Draft appearance remains transient and preview controls are read-only.
- Composite transparent backgrounds onto an opaque backdrop before contrast calculations. Build 503's reproduced translucent-background exception motivated this fix.
- Resolve unlocked private apps from typed names; `private -open <app name>` explicitly selects the private copy. Private apps remain excluded from general suggestions and command-launch history.
- Sort private app copies before displaying them and insert alphabetical headings without indexing a list that is being expanded.
- Append Hotfix #1 to the existing in-app changelog; preserve the previous notes in the GitHub release body.

## Verification

- Full JVM suite: 477 tests, 472 passed, 5 skipped, zero failures or errors.
- Release lint: zero errors, 636 warnings.
- Debug-device checks: changelog fresh-install/upgrade journeys and preview lifecycle passed.
- Signed minified APK: appended changelog and preserved earlier notes passed.
- Signed minified APK: typed private-app name and explicit private command both launched the fixture in private user 11; launching after relock was blocked.
- Final signed minified APK: 18 third-pane swipe arrivals across Theme/UI/Behaviour with the reported translucent Kois background passed; preview controls stayed inactive and saved files were unchanged.
- Prior preview regression: 72 third-pane arrivals across Theme/UI/Behaviour and opaque/translucent/transparent backgrounds passed on Android 15, with saved XML unchanged.

## Limits

The combined debug-device run timed out once waiting for Private Space authentication while release compilation was running. The standalone private flow had passed; the final minified private launch/relock check also passed. This is targeted device validation, not a claim that the entire instrumentation suite was run. Pixel 10 / Android 17 confirmation remains pending. Issues #23 and #24 remain open for follow-up confirmation.

Local raw logs, artifact hashes and emulator screenshots are retained under `.local-context/releases/build504-hotfix1/`. The Play Store bundle is a local upload artifact; creating it does not upload it to Play Console.
