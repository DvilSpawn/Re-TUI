# Vivo Private Space: online reports and device-code R&D

Date: 2026-10-17. Scope: read-only inspection of the connected I2220 phone's system APKs/framework jars, online issue research, and review of existing device/emulator evidence. No app build, installation, permission grant, authentication attempt, profile start/stop, or system setting write was performed in this pass. Re:TUI remains the HOME role holder.

## Result

The phone contains concrete OEM changes that make the standard Android quiet-mode flow unsuitable for controlling Vivo's private profile. The unlock failure is explained by the inspected framework's control flow and agrees with the observed phone state. No verified ordinary-app workaround for direct Vivo private-app launching was found. The separate failure to launch apps after the profile was running is not fully diagnosed.

This is an incompatibility on this inspected firmware, not proof that every Vivo device or every third-party launcher must fail.

## Public reports and existing implementations

- [iQOO 12 report, 2024-05-22](https://community.iqoo.com/in/thread/52449): work-profile launches after AppLock open the personal copy; the author explicitly reports third-party launcher impact. This concerns Funtouch 14 work profiles and app encryption, not the exact Android 16 Private Space PIN-returning-home failure. The retrieved report contains no verified fix.
- [OriginOS X300 Ultra user report, 2026-04-27](https://www.reddit.com/r/Vivo/comments/1sxb1pz/vivo_origin_os_issues/): exiting the native privacy system resets the selected third-party launcher. Related vendor integration evidence, not the same failure or a working fix.
- [Dhizuku issue #262](https://github.com/iamr0s/Dhizuku/issues/262): an iQOO OriginOS 6 user reports the native `com.vivo.xspace` profile exists before setup and blocks user-management operations. Its resolution resets/removes Private Space for device-owner provisioning; this is destructive to an established space and is not a solution for launching its apps.
- Targeted GitHub issue queries found 19 Kvaesitso issues mentioning Vivo, one mentioning iQOO, zero Lawnchair issues matching Vivo plus private, and zero issues matching the native XSpace package plus launcher. No confirmed fix for this exact failure was identified in the inspected results; this is not an exhaustive claim about the internet.
- [Android's launcher contract](https://developer.android.com/about/versions/15/behavior-changes-all#private-space) requires HOME, `ACCESS_HIDDEN_PROFILES`, a separate container, and quiet-mode lock/unlock. Re:TUI's permission was confirmed granted on this phone; simply adding the normal permission again cannot solve the OEM lifecycle mismatch.
- [Earlier upstream source review](../upstream-research.md) documents Kvaesitso, KISS and Lawnchair using the same public profile/launcher APIs. Official Kvaesitso v1.41.0 also failed to start this phone's private profile after user authentication. Re:TUI's same signed APK passed two authenticated private-user launches, cancellation, and relocking on the [Android 16 emulator](../emulator-api36/README.md).

## What the phone code shows

The system artifacts were pulled from the connected phone, not downloaded from an APK mirror. Fingerprint and SHA-256 values are in [inspection.json](inspection.json). Analysis copies remain under `/tmp/retui-vivo-private-rnd`; native XSpace APK is `/tmp/retui-vivo-xspace.apk`. These temporary copies are local investigation material, not bundled launcher dependencies or committed vendor sources.

| Finding | Inspected method / local evidence | Meaning |
| --- | --- | --- |
| Native profile identity | `UserManagerService.addUserDataLU`, `framework.jar` → `vivo.util.XSpaceUserUtils.isXSpaceUser` | The OEM XSPACE flag updates `UserHandle.XSPACE_USER_ID`; the standard PRIVATE type is also replaced by an XSpace profile definition. This is Vivo code, unrelated to Xiaomi's similarly named feature. |
| Standard lock is skipped | `UserManagerService.java:1543`, decompiled quiet-mode setter | For the XSpace user, enabling quiet mode returns immediately. It does not set quiet mode or stop the profile through this standard path. |
| Unlock can exit without starting | `UserManagerService.java:1562` | If quiet mode is already false, the setter exits before `startProfileWithListener`. A stopped profile with quiet mode false therefore stays stopped after the credential callback. This matches the observed PIN → home → stopped profile behavior. |
| Native app separately starts/stops profile | XSpace `d4/c.java:206-254`; `VerifyActivity.java:485`, also other auth/lifecycle paths | Native XSpace uses hidden `ActivityManager.startProfile` / `stopProfile`, rather than relying solely on quiet mode. The utility is called from its verification flow. |
| Native start is privileged | `UserController.java:1310-1312` | `startProfile` requires `MANAGE_USERS` or `INTERACT_ACROSS_USERS_FULL`. Reflection does not grant those permissions. The phone also explicitly rejects shell UID 2000 starting its XSpace user (`UserController.java:1383`). Shell-based starts are not a production workaround. |
| Direct authentication UI is protected | XSpace manifest: `FromSettingActivity`, `VerifyActivity`, `VerifyActivity2` | All require `com.vivo.xspace.permission.ENTER_VERIFY_PAGE`, declared signature/privileged. `exported=true` alone is insufficient. |
| Native private home is protected | PrivacyLauncher manifest: `VirtualSystemLauncher` | Requires signature-only `com.bbk.launcher2.permission.ENTER_VIRTUAL_SYSTEM`. Its HOME registration is not a public launch shortcut. |
| Additional OEM launch gate exists | `VivoXspaceBgStartController.isAllowXSpaceAppBgStart`, invoked by `VivoBackgroundActivityController` | On overseas firmware it can reject launches into the XSpace user when native private mode is off, with exemptions including the native private launcher. There are earlier allow paths, so this code alone does not prove it rejected Re:TUI's taps. A matched failure log is still needed. |
| Native widget delegation candidate | XSpace `SecuritySettingAppWidgetProvider` → `k3/a.java:389-401` | Native widgets provide their own PendingIntents to vendor UI. Provider metadata exists in owner user 0. Hosting an authentic user-added widget could offer a legitimate shorter native handoff; it is untested and does not establish integrated per-app launching. |

The decompiler produced partial-output warnings/errors elsewhere (16 for the vendor services jar, 99 for XSpace, and errors in large framework classes). The listed small guards, permission checks and native start/stop utility were readable; no full firmware correctness audit is claimed. Decompiled names/line numbers are local analysis locations, not OEM source line numbers.

Live read-back at the end of inspection: HOME = `com.dvil.tui_renewed`; native `xspace_launcher_status` = 0; private user 10 not started. No attempt was made to alter these states.

## Smallest viable product direction

1. Keep public Android Private Space integration on compatible implementations; the emulator already establishes that the basic flow works there.
2. For the confirmed Vivo native-profile variant, provide an explicit native-space handoff via Android security settings. The user completes Vivo authentication and selects apps in Vivo's own UI. Opening the settings page is not itself an unlock, and returning must not be reported as successful unlocking.
3. Do not label a native space locked merely because the quiet-mode call returned true: this firmware deliberately skips that operation. Delegate its lock/unlock controls to native UI until a genuine vendor-supported control is validated. Preserve hiding private apps while readiness is uncertain.
4. Before seeking an integrated direct-launch fix, capture one correlated `LauncherAppsService` / `ActivityTaskManager` / Vivo firewall failure while a harmless fixture exists only in the private user. This distinguishes profile access rejection, foreground/background policy, and native-mode gating. Do not fall back to the owner's copy.
5. Test the genuine native widget/authentication handoff only if it improves the flow. Do not extract or forge protected intents, modify native security state, or add root/Shizuku as an implicit launcher dependency.

No new production workaround was implemented in this research pass. Direct per-app Vivo integration and a seamless one-tap native handoff remain unverified. A firmware change or a vendor-supported delegated API may be required for full integration; the current evidence does not establish which.
