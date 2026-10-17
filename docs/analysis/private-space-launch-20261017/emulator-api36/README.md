# Android 16 Private Space flow — passed

Tested the same signed Re:TUI APK installed on the Vivo (`d2279958fc4556192ab8b1744282d7a4f118aa3e7d2d97cfcd93c4bf64023748`) on an isolated Android 16/API 36.1 Google Play ARM64 emulator.

The passing instrumentation test exercised:

1. Lock through the same AppsManager path used by `private -lock`; private app list and PRI entry become hidden.
2. Unlock through the `private -unlock` path, enter the disposable device PIN in Android's credential UI, and wait for Android's profile-unlocked state.
3. Select PRI and click the test app's actual drawer row. The foreground window must belong to the fixture package **in private user 11**.
4. Return home and relock. Start authentication again and cancel; quiet mode stays on and private apps stay hidden.
5. Repeat PIN authentication and launch from PRI, then return home and leave the profile locked.

Result: **one end-to-end test passed in 51.491 seconds**, including two successful unlock-and-launch cycles. See `end-to-end.txt` and `validation.json`.

The fixture app was installed only in user 11; the test asserts it is absent from the personal profile. Its activity contains only “PRIVATE PROFILE TEST” and a log of its user handle, so a personal-profile fallback cannot masquerade as success. The test is guarded by emulator hardware, API level and an explicit opt-in argument before it enters the fixture PIN. It cannot enter credentials on the Vivo.

The private profile was provisioned using emulator administration, with a known test device PIN configured before creating the final profile. The initial profile start was fixture installation/setup. Both measured unlocks used Android's credential UI; neither used an administrative user-start command. The native first-use Private Space setup wizard was not covered.

Earlier harness iterations were not acceptance results: initial credential provisioning was replaced, notification-access startup interruptions were removed through emulator-only fixture permissions, credential submission was synchronized to the focused PIN field, and returning to the single-task home activity was made nonblocking. A temporary settings-launch helper was removed after investigation. The final test method's behavior was unchanged by that removal, and the final source compiled again.

Google Play services logged background-service errors while the disposable profile stopped. No Re:TUI crash occurred during the passing test.

## Phone comparison

The official Kvaesitso v1.41.0 APK was checksum/signature verified, installed on the Vivo, and temporarily made HOME. `ACCESS_HIDDEN_PROFILES` was granted. The user entered the phone PIN; the phone returned to Kvaesitso home and its private profile remained stopped. A private-app launch from Kvaesitso was therefore not reached. See `../reference-phone/`.

Re:TUI was restored as the Vivo's default launcher; keyboard and rotation defaults were read back and matched their original values. The Kvaesitso reference installation remains available for follow-up. Emulator testing made no further phone changes.

These results establish that the tested Re:TUI flow works on standard Android 16. Together with the phone comparison, they point toward a Vivo-specific compatibility problem. They do not establish the exact vendor restriction or prove that every third-party launcher fails. The separate failure to launch from Re:TUI after Vivo's native launcher had started the private profile remains unresolved.

The test emulator was shut down and its disposable AVD retained for reproduction. No app release, commit or push was made.
