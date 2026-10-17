# Private Space unlock response correction — 2026-10-17

`private -unlock` previously collapsed both `requestQuietModeEnabled(false, profile)` returning `false` and any thrown exception into “Private Space action failed.” Android documents `false` as credential confirmation being required; the two-argument call opens Android's confirmation screen. It also documents that profile state changes can be asynchronous.

Reference: [Android UserManager.requestQuietModeEnabled](https://developer.android.com/reference/android/os/UserManager#requestQuietModeEnabled(boolean,%20android.os.UserHandle)).

The shared AppsManager path used by commands and drawer controls now distinguishes authentication pending, accepted requests, security rejection, and other failures. Exceptions are logged instead of discarded. It reads the lock state from Android instead of assigning the requested state optimistically. Existing availability broadcasts still refresh the private app list, and locked-profile discovery filters remain in place. Android continues to own all credential entry and authentication.

Validation:

- Normal `playstoreDebug` build completed with runtime checks enabled.
- 466 unit tests passed, five existing network tests skipped, zero failures/errors. Six Private Space tests cover pending authentication, accepted lock/unlock, security rejection, other failures, profile classification and visibility.
- Localization catalog and English template export/check passed. The generated files also catch up the previously added widget strings.
- Vivo hardware serial `10BE1C0FB40006A`: Re:TUI holds HOME and `ACCESS_HIDDEN_PROFILES` is granted. Signed candidate matches the existing app's certificate. In-place install succeeded; pulled installed APK matches candidate SHA-256. User 0 app-data directories and first installation timestamp were retained.
- This candidate also includes the pending expanded widget pane and fluid resize source changes. Their earlier emulator results remain under `../widgets-expanded-20261017/`.

On-device authentication remains pending user verification. The phone had an existing launcher input draft, which was not replaced. No private apps were enumerated, profile credentials changed, authentication bypassed, or private app launched. No public release, commit or push was performed.

See `validation.json`, `private-space-tests.xml`, and `build.txt` for exact build/install evidence.
