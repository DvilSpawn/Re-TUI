# Disposable emulator checks

16 October 2026. Existing `retui_palette_api35` AVD, `emulator-5554`, Android 15/API 35 arm64. Never wiped or deleted. Phone and its Termux installation were untouched. Only the isolated `calculatorpackdebug` app and emulator system wallpaper were changed.

## Actual wallpaper application: passed

`WallpaperSystemApplyIntegrationTest#realDownloadedCloudsApplyThroughAndroidPicker` passed in 4.3 seconds. It downloaded the real HTTPS Clouds pack, verified/stored its image assets, opened the real editor and confirmed its `CloudsView`, invoked the editor's Use on Phone action, and clicked Android's Set wallpaper confirmation. `WallpaperManager.wallpaperInfo` and independent `dumpsys wallpaper` both identify the isolated package's `RetuiWallpaperService`; a real bound engine is present. The screenshot was visually inspected and shows downloaded pixel-cloud artwork behind Pixel Launcher.

Evidence: `wallpaper-system-apply.log`, `.png`, `.txt`, and `wallpaper-system-state.txt`. This intentionally changed the disposable emulator wallpaper; the user's phone wallpaper was preserved.

## Emulator network diagnosis

Initially the emulator had no usable network route: both the hostname and numeric-address checks failed (`unknown host` / `Network is unreachable`). Enabling emulator Wi-Fi and cellular data restored default routes, DNS resolution, validated connectivity and actual HTTPS access. `connectivity-enabled.txt`, `network-enabled.txt`, and `termux-network.txt` record restored state. The Termux repository HTTPS request returned 200. These findings explain an environment cause for the preceding emulator-only download failures; they do not substitute for rerunning each failing flow.

## Actual fresh Termux setup: found a production blocker

Downloaded the ARM64 APK directly from the official `termux/termux-app` v0.118.3 GitHub release. Its SHA-256 matched that release's checksum file; Android signature verification passed. Exact provenance, APK hash and certificate are retained in `termux-source.json`, `termux-upstream-sha256sums.txt`, and `termux-apk-signature.txt`.

Termux was absent before installation. After its real first-run bootstrap, both `sshd` and `tmux` were absent (`termux-before.txt`). The emulator's Termux external-command option and the isolated launcher's RUN_COMMAND grant were prepared; the fresh-install check exercises the real launcher's Enable Termux pane action and actual package installation, rather than the Android permission-grant dialog itself.

The first real run installed tmux 3.8 and OpenSSH 10.6p1-2 plus dependencies through `apt install -y tmux openssh`. It then exposed a previously untested failure: `Could not save the Termux host key.` The hard-link publication in the production setup script fails with `Permission denied` under the actual Termux app UID on Android, even for its own files. `host-key-link-failure.txt` records an isolated reproduction. `termux-first-setup-error.png` and `.xml` capture the actual launcher error. Apt history and transaction output are recorded in `termux-apt-history.log` and `termux-apt-term.log`.

The first instrumentation process was intentionally force-stopped after the known error was captured, so its final `Process crashed` message is a host interruption, not evidence of another application defect. The parent replaced hard-link publication with a directory lock and validated rename, preserving concurrent host identity creation. Only tmux and openssh were removed with actual apt on this disposable emulator to recreate the missing-package baseline (`termux-recreate-missing-baseline.txt`); cached dependencies remained. On the newly built debug APK, the real Enable flow reinstalled both packages, saved the dedicated identity/configuration and reached a live SSH connection. **The fixed fresh-install test passed in 4.296 seconds.** `termux-fresh-install-fixed.log`, `termux-fresh-install-fixed.txt`, and `termux-after-fixed.txt` record the result and package versions. The original completely fresh transaction covered installation of all dependencies; the fixed rerun covered the two missing packages with those dependencies retained.

The system wallpaper application test also passed again on that fixed APK in 10.1 seconds (`wallpaper-system-apply-final.log`).

## Former download-timeout rerun

With emulator connectivity restored, the real hosted Qalculate engine download/install/checksum/exact-arithmetic check passed. The native wallpaper gallery also downloaded all four real hosted packs and opened the real Clouds preview successfully; the fresh built-in-only picker check passed. These three successful flows are recorded in `download-flows-restored-network.log`.

One additional missing-pack settings test failed after system wallpaper application: its strict full-preference-map comparison observed a newly generated `tree_seed`, while the selected scene, cloud scene and offset remained unchanged. The now-active emulator wallpaper service renders its missing-pack fallback and lazily creates that default seed during the test's preference reset. This is a test fixture interaction that was absent when no live service was active, not a demonstrated replacement of the user's selected scene. The fixture now seeds `tree_seed` before taking its strict preference snapshot; the complete equality assertion remains. On the rebuilt test APK this check **passed**, alongside the setup progress check with system animator duration scale explicitly zero (**2/2 passed**, 2.223 seconds; `final-fixture-startup-checks.log`). The animator scale was restored to 1 afterward. Thus all three former wallpaper/calculator download checks and the corrected missing-pack settings check passed in this pass. Attempts to disable/re-enable the component through Android shell were denied, so no component state changed.


## Final scope and retained state

No new production changes were made by this emulator agent. The parent owns the Android-compatible host-key publication fix and its fixtures/build. This agent added two opt-in emulator integration tests and seeded the existing missing-pack test fixture. Full signed-release source/build evidence belongs to the parent report; emulator tests run the isolated debug package. First-run RUN_COMMAND permission UI was not repeated here because the test package was granted beforehand; the actual package installation and SSH pairing were repeated. Overnight OEM/battery behavior remains outside this time box.

The emulator remains running with official Termux installed and paired to the isolated launcher; its normal phone counterpart was untouched. Test-created engine/wallpaper content remains only in the disposable emulator. The system wallpaper service remains selected on the emulator. An independently packaged URI-recipient helper was installed by the preset-device agent for a separate platform comparison; see that agent's report.
