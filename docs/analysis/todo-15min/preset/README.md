# Preset import: actual browser journey

2026-10-16, physical Vivo I2220 Android 16, serial `10BE1C0FB40006A` (USB). Normal signed release build 503 already installed; preserved app data and active preset/wallpaper.

## Confirmed

From Chrome, opened `https://re-tui.pages.dev/marketplace`, selected **terminal colors**, searched **dracula**, tapped **Download preset**, opened the downloaded item in Chrome Downloads, selected **Import RetUI Preset** in Android's chooser and **Just once**, inspected the preview, then tapped **IMPORT**.

The actual incoming intent was `ACTION_VIEW`, URI `content://media/external/downloads/1000165831`, MIME `application/vnd.retui+zip`, flags `0x13400003`. The importer displayed Dracula's manifest and validation result, then **Saved preset: Dracula**. It explicitly reported active settings unchanged. No Apply action was used. This real browser/chooser path succeeded; the original user's earlier error was not reproduced.

The downloaded 1,979-byte `dracula.retui` matched the existing tested fixture SHA256 `d878459b5f2b6ed095a5f9673ddde9bcc2876efd2725a23bfda65a15d6150e9c`.

Screenshots `android-chooser.png`, `chrome-import-preview.png`, and `chrome-import-saved.png`; saved screen visually inspected for launcher font/panel appearance and header/system-bar separation. `import-result.xml` captures actual success text.

## Separate share-grant regression

Reproduced the historical `verifiedExportReturnsCachedShareUriWithMatchingBytes` failure on this phone against the previous test APK (`revocation-baseline.log`). Immediately after instrumentation completed, the same recipient UID was correctly denied access (`recipient-after-instrumentation.txt`), with no outstanding preset URI grants in ActivityManager's grant inventory. This points toward nested Binder/instrumentation identity in the test harness; it does not establish an enduring production URI-grant leak.

Test-only changes move recipient reads onto a fresh worker thread and add a pre-grant denial check, retaining positive byte-for-byte grant verification and post-revocation denial. Rebuild/run results are recorded separately by the build owner. Production provider/export logic was left unchanged pending those results.

No preset or wallpaper applied. Normal package never reset or uninstalled. The only additions to the user's preset library were the explicitly imported Dracula test preset and its fresh download.

## Rebuilt device checks

The first combined rebuilt run had seven checks: five passed (marketplace backend validation and deduplication/import-only behavior; existing browser-wrapper import, stale-intent handling, and themed error recovery), and two failed. The marketplace UI test initially treated the themed dialog's clickable TextView as a Button; corrected the test lookup. The URI regression still fails specifically after revocation: initial ungranted access is denied and granted bytes match, but immediate post-revocation access succeeds on this Vivo. Worker-thread isolation did not resolve it. An independent recipient app and platform permission investigation remain required; no claim is made that production URI revocation was fixed.

The corrected marketplace UI test passed against the real HTTPS catalog and package on the phone: confirmation, download, imported-library result, copy-command button, and byte-identical active XML files. Its newly added preset was removed afterward. `native-market-imported.png` was captured by instrumentation and visually inspected. The actual Classic/Search suggestion-dispatch test passed without granting READ_CONTACTS. All three legacy launcher migration tests passed. Together, the final new-feature checks are **5/5 passed**. An initial migration invocation used the wrong class package; the corrected `managers.termux` class run is recorded in `migration-device-tests.log`.

Foreground handed back to the parent at 08:39:49 UTC with the normal Launcher restored. Disposable debug target/test packages were left installed for the parent's remaining tests; parent owns final cleanup.
