# Private Space upstream implementation research — 2026-10-17

This is a source/release review. No launcher code, phone settings, or installed apps were changed during this research pass. The reference launchers have not been tested on the Vivo.

## Findings

There are established implementations of standard Android Private Space. They use public launcher/profile APIs, including the same core `LauncherApps.startMainActivity` call already used by Re:TUI. This does not establish that any particular implementation works on this Vivo, and the evidence does not establish that Vivo makes third-party launching impossible.

| Project | Verified source/evidence | Approach |
| --- | --- | --- |
| Kvaesitso | Released [v1.41.0](https://github.com/MM2-0/Kvaesitso/releases/tag/v1.41.0), published 2026-09-15; both files below fetched from this tag | Unlock through `requestQuietModeEnabled(false, user)`; determine readiness from `isUserUnlocked(user)`; launch using the component and user from `LauncherActivityInfo` |
| KISS | [Release notes](https://github.com/Neamar/KISS/releases/tag/v3.24.2) document Private Space shortcut fixes; inspected master commit `708fae3212e1a96a00c231dce7ca04aa247895f3` | Launch through `LauncherApps.startMainActivity` with the stored real profile handle; optional animation/source bounds |
| Lawnchair / Launcher3-based code | Inspected 16-dev commit `85335d904a1c8b9790e40c581226684c59166297`; this is development/reference evidence, not a claim that this branch is a stable release | Cross-profile launch through `LauncherApps.startMainActivity`; item-specific user, launch options and source bounds; profile state controller plus Private Space UI tests |

Source links:

- [Kvaesitso released app launch path](https://github.com/MM2-0/Kvaesitso/blob/v1.41.0/data/applications/src/main/java/de/mm20/launcher2/applications/LauncherApp.kt#L147-L166)
- [Kvaesitso released profile lifecycle handling](https://github.com/MM2-0/Kvaesitso/blob/v1.41.0/core/profiles/src/main/java/de/mm20/launcher2/profiles/ProfileManager.kt#L73-L98)
- [Kvaesitso package availability callbacks](https://github.com/MM2-0/Kvaesitso/blob/30aa319c6d9d7d9c74cfe60874bbe74dd999bcaa/data/applications/src/main/java/de/mm20/launcher2/applications/AppRepository.kt#L60-L101)
- [KISS app launch path](https://github.com/Neamar/KISS/blob/708fae3212e1a96a00c231dce7ca04aa247895f3/app/src/main/java/fr/neamar/kiss/result/AppResult.java#L311-L342)
- [Lawnchair activity launch path](https://github.com/LawnchairLauncher/lawnchair/blob/85335d904a1c8b9790e40c581226684c59166297/src/com/android/launcher3/views/ActivityContext.java#L453-L509)
- [Lawnchair Private Space UI tests](https://github.com/LawnchairLauncher/lawnchair/blob/85335d904a1c8b9790e40c581226684c59166297/quickstep/tests/src/com/android/quickstep/TaplPrivateSpaceTest.java)

The inspected Lawnchair UI tests cover the container, app placement, uninstall menu and lock/unlock behavior. Their presence is not evidence that we ran them, nor proof of Vivo compatibility or a successful app-launch test on this device.

## Concrete differences worth verifying

1. Re:TUI currently refreshes its profile/app map on `ACTION_PROFILE_AVAILABLE` and `ACTION_PROFILE_UNAVAILABLE`. Kvaesitso also observes accessibility/inaccessibility and other profile lifecycle events, and refreshes app data through `LauncherApps.Callback`. A stale map after an OEM unlock is a plausible bug to test, not a confirmed diagnosis.
2. Kvaesitso stores and launches with `LauncherActivityInfo.user`. Re:TUI constructs launch information using the profile handle passed to `getActivityList`. These should agree on standard Android; compare the actual returned handles/components on this phone before changing anything.
3. Lawnchair and KISS pass activity launch options/source bounds where available. Re:TUI passes null for both. These are differences to isolate, not evidence that animation options fix Vivo launching.

## Vivo evidence boundary

GitHub issue searches and the inspected source did not reveal a verified Vivo/iQOO Private Space workaround. Search hits about Xiaomi XSpace/Second Space do not demonstrate Vivo support.

Earlier device diagnostics showed `quietMode=false` while the private profile was stopped. After the user entered Vivo's native Private Space and launched an app, Android reported user 10 as `RUNNING_UNLOCKED`. The user still reported that the same app would not launch from Re:TUI, and a subsequent device state read still showed the profile running/unlocked. Therefore the earlier stopped-profile finding does not fully explain the launch failure.

Recommended next experiment: compare Kvaesitso v1.41.0 and Re:TUI on the same unlocked profile, with each set as the default launcher during its test. Restore Re:TUI afterward. If Kvaesitso succeeds, compare exact activity/user handles, lifecycle refreshes and launch options. If it also fails, investigate the OEM path with that reproducible comparison. Do not substitute opening the personal-profile copy for opening the private-profile app.

## Subsequent device results

The user authorized the comparison. Kvaesitso was installed as HOME on the Vivo, with hidden-profile permission granted. After the user authenticated, the private profile stayed stopped, so its app-launch phase could not be reached. Re:TUI was restored as HOME. The same signed Re:TUI APK subsequently passed an automated Android 16 emulator flow with two authenticated PRI app launches, cancellation remaining locked, and relock visibility checks. See [emulator and phone comparison evidence](emulator-api36/README.md). Vivo compatibility remains unresolved; no third-party workaround has been established.
