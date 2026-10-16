# Termux SSH release and oldest-API validation

**16 October follow-up:** the two calculator test failures and two AppsManager lint errors are fixed; the full suite and release builds now pass. [Current source/artifact evidence](PRODUCTION_BLOCKERS.md). The APK and API 23 results below describe the earlier SSH validation snapshot; do not attribute its API 23 execution to a different rebuilt binary.

The final minified SSH candidate passed **4 checks on the physical Android 16 phone**: platform crypto, production identity initialization, terminal streaming, and native Termux bootstrap followed by pinned SSH, two independent PTY streams and detach preservation. [The physical log](evidence/release-compat/physical-final-minified-e2e.log) records success. The **same APK passed 3 checks on Android 6/API 23**, covering the platform and production crypto/terminal compatibility entry points; see [the API 23 log](evidence/release-compat/api23-final-minified-smoke.log).

The final APK SHA-256 is `2d2465084ea333b370b070d9ea09180f1d10e9d08a53f2de9a04695f6e8c6ce2` (7,800,858 bytes). Its base APK was pulled from the phone after the test and matched this hash exactly; [installed-artifact provenance](evidence/release-compat/final-installed-apk-provenance.json) records the comparison. [App/test provenance](evidence/release-compat/final-minified-apk-provenance.json) records the original and processed test APK hashes. It includes JSch 2.28.8, the off-main-thread SSH disconnect fix, wide-glyph rendering, and narrow R8 package/factory access rules. Earlier snapshots remain recorded separately.

The checks execute Android P-256 signatures/ECDH, AES CTR and HMAC SHA-256; call the production setup and connection entry points to generate and load the private identity and initialize trusted host-key handling; and exercise incremental UTF-8, terminal queries, cursor-key modes and alternate-screen restoration through the production terminal wrapper. The connection smoke deliberately uses a closed loopback port and expects the bounded daemon-startup error after identity initialization. Full SSH execution requires the separate physical-device check; current Termux is not installed on the API 23 emulator.

Compatibility failures were found and corrected: JSch's P-256 initialization calls `Arrays.stream`, requiring Java library desugaring on API 23; and its release initialization reflectively constructs `HMACSHA1`, requiring an explicit constructor keep rule alongside the other selected implementations. The negotiated transport MAC remains HMAC SHA-256. Actual minified wire testing additionally showed that package-private core factories cannot be invoked when R8 moves Session to another package; the conditional rules retain only the core JSch package location and the package-private UserAuthPublicKey constructor, while allowing ordinary class renaming and removal. The earlier closed-port smoke could not exercise this key-exchange path. SSH keeps live in `app/proguard-termux-ssh.pro`. They applied only to the internal candidate at the recorded snapshot; the [standard-mode follow-up](SSH_STANDARD.md) applies them to every normal release. The original global manager keep rule also retained unused private SSH helpers in TermuxWorkspaceActivity. Only this Activity (including nested classes) and the new TermuxWorkspaceSsh classes are excluded from that blanket rule; identical full keeps apply to them in the enabled candidate. At that earlier disabled-default snapshot, normal Android manifest/lifecycle rules allowed unused SSH code to shrink. Current normal releases enable SSH and retain its required rules.

## Reproduce the minified checks

Run these commands from the analysis worktree, with an Android SDK and its build-tools available:

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew \
  :app:assemblePlaystoreRelease :app:assemblePlaystoreReleaseAndroidTest \
  -PtermuxSshReleaseCheck=true

python3 scripts/prepare_termux_ssh_release_tests.py \
  --app-apk app/build/outputs/apk/playstore/release/app-playstore-release.apk \
  --test-apk app/build/outputs/apk/androidTest/playstore/release/app-playstore-release-androidTest.apk \
  --output /tmp/retui-ssh-release-tests-app-runtime.apk \
  --build-tools "$HOME/Library/Android/sdk/build-tools/36.0.0"

adb -s SERIAL install -r app/build/outputs/apk/playstore/release/app-playstore-release.apk
adb -s SERIAL install -r /tmp/retui-ssh-release-tests-app-runtime.apk
adb -s SERIAL shell am instrument -w -r \
  com.dvil.tui_renewed.termuxsshreleasecheck.test/ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshReleaseInstrumentation
```

The internal candidate has a separate package and uses the debug signing key while retaining production shrinking. The runner reports `checksPassed=3` and `OK (3 minified SSH checks)`. Its optional physical SSH test uses only Java/framework code and production entry points; it does not require Kotlin file helpers or UIManager Companion APIs that app R8 legitimately removes. The native result action is the package-derived protocol string defined in UIManager, and constant extras compile directly into the test. On the authorized physical device, grant its existing Termux run-command permission and add `-e termuxSshDevice true` before the component to include native setup, two independent streaming tiles and detach preservation.

## Why the test APK needs preparation

The separately minimized app and test APKs contain differently optimized L8 compatibility runtimes. Android loads test classes first, so the duplicate test runtime can shadow the app's implementation and cause `NoSuchMethodError` inside an otherwise valid application runtime. AndroidJUnitRunner also references app library APIs that production R8 legitimately removes; the check therefore uses a small platform Instrumentation runner and production wrapper entry points rather than preserving unused library APIs.

The preparation script changes only a separate copy of the test APK. It verifies that every removed DEX class belongs to `j$` or `java.util.function`, refuses a DEX mixed with application/test code, checks that the app supplies every removed Java function interface, preserves both input APKs, aligns/signs the output and verifies its certificate matches the app. In the recorded run, it removed `classes2.dex`: 502 `j$` classes and 36 Java function interfaces, with all descriptors listed in the JSON report. Application code, the application APK and the original test APK remain unchanged.

The guard and repack tests run with:

```sh
python3 -m unittest discover -s scripts/tests -p 'test_prepare_termux_ssh_release_tests.py'
```

## Initial repository validation failures — fixed in follow-up

The original checkout's pre-SSH `PaletteCoverageRegistryTest` report (2026-10-15 14:42 UTC) already fails `everyRoleBelongs...` because CalculatorActivity's actual role set includes `showEnhancement`, and `everyActivity...` because CalculatorPackActivity is absent from its palette registry. The copied calculator work is outside the SSH change; it was preserved. The final full run had 438 tests: 431 passed, 2 existing palette failures, 5 skipped. The focused Termux suite passed 27 tests.

Release lint's two errors are also present in the original checkout: AppsManager.kt uses UserManager.isQuietModeEnabled (API 24, around line 580) and requestQuietModeEnabled (API 28, around line 1146) with minSdk 23. The original lint output had 2 errors/597 warnings; the final SSH worktree run had 2 errors/600 warnings/311 hints. No new Termux NewApi error was reported. These failures blocked the initial checkpoint; the [follow-up](PRODUCTION_BLOCKERS.md) fixes them and records clean unit and release-lint gates. The source and line positions may shift with unrelated local edits.

## Historical APK size boundary before standard cutover

The same-source default disabled release contains **zero JSch and zero TermuxWorkspaceSsh implementation classes** in its R8 mapping. Its archived unsigned APK is 7,659,815 bytes; the enabled debug-signed internal candidate is 7,800,858 bytes: 141,043 bytes (1.841%) larger. [The size record](evidence/release-compat/final-apk-size.json) contains both hashes and DEX totals. Signing, package name and label differ, and this compares two modes of the final working source; it is not the total feature cost against the original checkout. Earlier default builds incorrectly retained unused SSH code through the blanket manager rule; the focused keep relocation removed that retention.

The temporary owned API 23 emulator, its AVD and its downloaded Android 23 image were removed after the final checks; [cleanup provenance](evidence/release-compat/api23-cleanup.json) lists only those owned paths. Existing SDK images and emulators were preserved.
