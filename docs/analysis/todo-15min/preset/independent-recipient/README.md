# Independent recipient verification

2026-10-16 follow-up. Built a separate, independently signed APK `dev.retui.test.presetrecipient`, with no instrumentation declaration and no permissions. Its source and reproducible SDK-only build are versioned at `scripts/tests/preset-recipient/`. On this Vivo phone its distinct UID is 11183.

The regression now requires this helper, independently checks its actual URI permission status, verifies exported bytes and MIME during a grant, and requires both permission denial and blocked reads before the grant and after revocation. Missing helper fails with an installation hint; there is no skip or relaxed assertion.

`phone-test.log` records the first independent-helper run using the original broad `revokeUriPermission(uri, flags)` API: initial access denied, explicit grant allowed exact-byte access, but immediate post-revocation permission remained granted. The original instrumentation-source recipient was therefore not the sole cause. No matching grant remained once instrumentation ended (`phone-grants-after-test.txt`, with unrelated app grants omitted).

The test now matches its explicitly granted recipient with `revokeUriPermission(package, uri, flags)` on API26+, retaining the broad fallback for API23–25. [Android documents](https://developer.android.com/reference/android/content/Context#revokeUriPermission(java.lang.String,%20android.net.Uri,%20int)) this overload as revoking explicit grants for the named package without affecting unrelated intent grants. Strict post-revocation permission and read-denial checks are unchanged. Final rerun results are recorded separately.

Production FileProvider/export code was not changed. Do not treat the issue as resolved unless the final focused phone test passes.

**Final scoped-overload phone rerun also failed** (`phone-package-revoke-test.log`, 0.088 seconds). The independent helper still reported permission GRANTED immediately after the package-specific revocation. Initial denial and exact-byte granted reads passed. The independent recipient therefore improves the validity of the regression test but does not resolve the Vivo failure. No production fix or production-readiness claim follows from this result.

`phone-package-uids.json` records all three distinct app IDs at the test boundary: target11184, instrumentation source11185, independent recipient11183. Helper removal is owned by the parent during final test cleanup.

**Android 16 emulator comparison passes** (`emulator-package-revoke-test.log`, 0.164 seconds) using the same independent helper and strict package-revocation test code. The same test fails on the physical Vivo. This isolates a device/platform difference; it does not by itself identify the Vivo implementation's root cause or prove behavior for an uninstrumented production share activity. The regression remains a physical-device limitation, with no production provider modifications.

## Resumed investigation: independent sender and real access contract

A second independently signed, uninstrumented app `dev.retui.test.presetsender` now owns its own private grantable provider (UID11186 on this phone). A nested callback experiment reproduced the misleading grant state even with sender `Binder.getCallingUid()` equal to its own UID. Polling five times at250ms and a broad revoke did not change the nested result.

Separating sender grant/revoke calls from recipient read calls resolves the actual security test: before grant the recipient cannot open the private URI; after grant exact expected bytes and MIME are readable; after revoke the same URI returns `SecurityException`. No force-stop, file deletion, package reinstall, or URI substitution occurs between the successful read and the denied read. Sender and recipient have distinct UIDs. Both `checkUriPermission` and API35 `checkContentUriPermissionFull` still report `GRANTED` after effective read access ends on this Vivo; these values cannot substitute for an actual access attempt here.

`separate-phases/result.json` and `separate-phases/phase-output.json` record a passing physical-device run. `scripts/tests/preset-recipient/verify.py` reproduces these strict real-read assertions without instrumentation. The updated Android regression keeps the launcher's real export byte/MIME test and adds an uninstrumented-sender lifecycle test with denial before/after and exact-byte access during the grant. Each phase is invoked in its own shell process, because calling helpers directly through instrumentation still reproduced the nested-identity artifact. Before the first grant a hidden private provider may return `FileNotFoundException: No content provider`; after revoke a real `SecurityException` is required, and no bytes may be returned. Its final instrumented result must be recorded by the parent before claiming the combined suite passed.

This corrects the previous conclusion that the phone necessarily retains usable revoked access: the earlier nested-callback and permission-query checks did not model an independent recipient opening the URI after the originating sender call completed. Production `GenericFileProvider` and export behavior remain unchanged.

**Final phone validation passed, 2026-10-16 09:26 UTC:** `separate-phases/phone-instrumented-final.log` records `OK (2 tests)` in6.755s using the rebuilt final AndroidTest APK. The original launcher export byte/MIME check passes. The separate uninstrumented sender/recipient test passes strict before denial, exact bytes+MIME during the explicit grant, distinct app UIDs, and `SecurityException` with no returned bytes after revocation. Query-API values are diagnostic only. No production provider changes were made.
