# Independent preset URI recipient

`PortablePresetIntegrationTest.independentUninstrumentedShareGrantEndsAfterRevocation` requires this separate, uninstrumented recipient APK plus the sender below. It fails if either helper is absent. The recipient has its own UID and test signing identity; it is not the AndroidTest/instrumentation source package.

Build with the local Android SDK (API 36/build-tools and JDK 17), then install on the same device used for instrumentation:

```sh
python3 scripts/tests/preset-recipient/build.py
adb -s DEVICE_SERIAL install -r /tmp/retui-preset-recipient/preset-recipient.apk
adb -s DEVICE_SERIAL shell am instrument -w -r \
  -e class ohi.andre.consolelauncher.managers.PortablePresetIntegrationTest#independentUninstrumentedShareGrantEndsAfterRevocation \
  com.dvil.tui_renewed.calculatorpackdebug.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL uninstall dev.retui.test.presetrecipient
```

Replace the target instrumentation package if using another build variant. The helper does not touch launcher settings or user files. The recipient probes a cached preset-share URI, returning actual byte content, MIME, its UID, and diagnostic URI grant status. The lifecycle test requires denial before the grant, byte-for-byte read during the grant, and denial immediately after revocation. Recipient reads run on their own thread. The lifecycle test additionally starts each phase in a separate shell process so neither the sending app nor instrumentation supplies a nested Binder scope.

The build uses `javac`, `d8`, `aapt2`, `zipalign`, and `apksigner`; no Gradle dependency or production APK change is introduced. Build output and a local test keystore remain in the chosen temporary output directory, so repeated `install -r` uses the same identity. Remove that directory to regenerate it (uninstall the old helper first).

## Android 16 Vivo: independent sender phases

The export bytes/MIME test remains against the launcher's real export. Permission lifecycle has a separate test, `independentUninstrumentedShareGrantEndsAfterRevocation`, requiring both helpers. Build/install the sender as well:

```sh
python3 scripts/tests/preset-recipient/build.py --sender --output /tmp/retui-preset-sender
adb -s DEVICE_SERIAL install -r /tmp/retui-preset-sender/preset-recipient.apk
python3 scripts/tests/preset-recipient/verify.py --serial DEVICE_SERIAL --output /tmp/retui-grant-result
adb -s DEVICE_SERIAL uninstall dev.retui.test.presetsender
```

The sender has its own private grantable provider and a distinct UID. Grant and revoke calls finish in independent shell processes before each recipient probe begins; this avoids testing inside the sender or instrumentation Binder scope. The regression strictly requires an unreadable share before grant, exact expected bytes and MIME while granted, and denied reading with no returned bytes after revoke. Both the Android regression and standalone verifier require a `SecurityException` after revoke. Before the first grant, Android may instead hide the unexported provider with `FileNotFoundException: No content provider`; this also counts as denied access, while the subsequent exact-byte read proves the fixture exists. Grant-check API results remain diagnostic: the Vivo reports `GRANTED` after revocation even when the provider correctly throws a permission-denial `SecurityException`. No production provider or permission policy is changed.
