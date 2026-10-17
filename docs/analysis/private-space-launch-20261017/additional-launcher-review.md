# µLauncher and Olauncher: Private Space implementation review

Reviewed 2026-10-17 against the repositories requested by the user. This is default-branch source and issue review, not a claim about the latest shipped APK or phone compatibility. Neither launcher was installed on the phone during this review.

| Project | Pinned default-branch snapshot |
| --- | --- |
| jrpie/launcher (µLauncher) | `732a00f0206f58793fd663cec4296bdf0327e25b`, master, 2026-10-12 |
| tanujnotes/Olauncher | `f8e8d6497c457734c7a3171d36374157678e1f33`, master, 2026-10-17 |

## µLauncher

[PrivateSpace.kt](https://github.com/jrpie/launcher/blob/732a00f0206f58793fd663cec4296bdf0327e25b/app/src/main/java/de/jrpie/android/launcher/apps/PrivateSpace.kt): Android 15 version gate, `getLauncherUserInfo(...).userType` classification, `isQuietModeEnabled` state and `requestQuietModeEnabled` locking. Setup guidance differentiates missing default HOME from unavailable Private Space and can open Android privacy settings. The version gate establishes API availability, not successful operation on every manufacturer's implementation.

[AppAction.kt](https://github.com/jrpie/launcher/blob/732a00f0206f58793fd663cec4296bdf0327e25b/app/src/main/java/de/jrpie/android/launcher/actions/AppAction.kt#L27-L49) launches with the activity component and user returned by `LauncherActivityInfo`, using `LauncherApps.startMainActivity`. A security rejection produces a failure toast. [Application.kt](https://github.com/jrpie/launcher/blob/732a00f0206f58793fd663cec4296bdf0327e25b/app/src/main/java/de/jrpie/android/launcher/Application.kt) observes profile broadcasts and launcher package callbacks to reload state/apps.

A useful Android 16 detail: it reads the platform `PRIVATE_SPACE_ENTRYPOINT_HIDDEN` value from `LauncherUserInfo.userConfig`. This should be distinguished from a launcher's own show/hide preference; it does not fix profile startup.

A concrete prior UI issue, [#330](https://github.com/jrpie/launcher/issues/330), looked superficially like PIN → missing app list. The maintainer identified an app-list `onPause` dismissal and removed it in commit `37106df`. That was an app lifecycle bug. It does not explain our Vivo observation that the private profile remained stopped after authentication in two launchers.

## Olauncher

[Utils.kt](https://github.com/tanujnotes/Olauncher/blob/f8e8d6497c457734c7a3171d36374157678e1f33/app/src/main/java/app/olauncher/helper/Utils.kt#L201-L263) uses the same Android 15 profile-type classification, quiet-mode state, and profile-scoped activity enumeration. The drawer keeps its private header separate and only displays private app rows while its state is unlocked.

[MainViewModel.kt](https://github.com/tanujnotes/Olauncher/blob/f8e8d6497c457734c7a3171d36374157678e1f33/app/src/main/java/app/olauncher/MainViewModel.kt#L477-L519) toggles quiet mode through `UserManager.requestQuietModeEnabled`. It opens a Private Space settings intent with a security-settings fallback. Its `isPrivateSpaceToggling` flag suppresses normal home-navigation reset during authentication; [MainActivity.kt](https://github.com/tanujnotes/Olauncher/blob/f8e8d6497c457734c7a3171d36374157678e1f33/app/src/main/java/app/olauncher/MainActivity.kt) resets the flag/refreshes app state on resume or profile broadcasts.

Important difference to avoid copying: [the shared launch path](https://github.com/tanujnotes/Olauncher/blob/f8e8d6497c457734c7a3171d36374157678e1f33/app/src/main/java/app/olauncher/MainViewModel.kt#L388-L397) retries a `SecurityException` with `Process.myUserHandle()`. Inference from this code: when the same component exists in the owner profile, that fallback can open the personal copy instead of the requested private copy. That is not a successful private-profile launch. Re:TUI should preserve the selected user and report failure.

The [maintainer's setup guidance in #695](https://github.com/tanujnotes/Olauncher/issues/695) says Android 15+ is required and users must first enable Private Space in phone settings or the stock launcher. It does not claim a Vivo-specific workaround.

## Recommendation for Re:TUI

Both implementations confirm the standard API approach already used by Re:TUI. No OEM-specific unlock/start workaround was found in their inspected paths. Keep the approved opt-in toggle and compatibility notice; avoid a promise that every Android 15+ device works merely because the methods exist.

Useful follow-up checks are preserving the user's private-pane context across authentication and respecting Android 16's native hidden-entry preference. Neither justifies dropping Re:TUI's stricter readiness check (`!quietMode && isUserUnlocked`), adding a personal-profile fallback, or claiming the Vivo problem solved. No upstream source was copied into Re:TUI in this review.
