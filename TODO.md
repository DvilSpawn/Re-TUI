# Reliability follow-ups

## Reported 2026-09-15

- [ ] **Investigate text-drawing input-dispatch ANR.** Play report: 12 events,
  2 affected users over 28 days; builds 378, 401, 415 and 417; Android 14/16,
  HONOR and OPPO devices. Sample: HONOR X7a, Android 14, build 417.
  Main thread was Runnable in text rendering through `OutlineTextView.draw`.
  Suspects, not confirmed causes: uncapped `Ui.text_redraw_times` repeats full
  view drawing; `Behavior.max_lines = -1` permits unlimited terminal history.
  Inspect both OutlineTextView and OutlineEditText, output append/trim paths and
  styled spans. Reproduce large/long-line output with different redraw settings,
  capture frame/main-thread traces, then choose bounded work without breaking
  appearance or scrollback. Verify on a physical phone. A sampled stack alone
  does not establish which work consumed the timeout. Build 418 is not a
  confirmed fix.

- [x] **Harden KeeperService against null-intent restarts (local fix).** Report wraps an NPE
  in `ActivityThread.handleServiceArgs`; deepest app frame is
  `KeeperService.onStartCommand (KeeperService.kt:5)`, with `Object.getClass()`
  called on null. Reported build/device not supplied; obtain matching mapping
  if needed to resolve the optimized line number.
  Strong suspect: current override requires `Intent`, although Android can
  deliver null when restarting a sticky service. Current implementation returns
  `super.onStartCommand(...)` and directly reads intent extras on update.
  Accept a nullable intent, audit all dependent reads, explicitly choose restart
  behavior, and ensure preferences/notification initialization works after a
  fresh process (rather than relying only on startId). Confirm with a null-start
  regression and service restart test, including normal command updates.
  Reference: https://developer.android.com/reference/android/app/Service#onStartCommand(android.content.Intent,int,int)

- [x] **Harden NotificationService media-session callback (local fix).**
  Report: `Object.getClass()` on null in `updateActiveSessions` (reported line
  210), called by `sessionsChangedListener.onActiveSessionsChanged`. Build,
  device and Android version were not supplied; resolve against the matching
  release mapping before assigning the exact dereference.
  The current callback and helper already accept a null controller list.
  A concrete unsafe pattern exists in diagnostic logging: playbackState is read
  twice, and the second result is force-unwrapped after checking the first.
  State can change between reads. A null controller element is another candidate,
  but is not established by this report. Snapshot playback state once and use
  nullable access; inspect controller handling at the callback boundary and all
  activeControllers consumers. Test null/empty lists, missing playback state,
  session teardown and normal media controls. Do not assume a null list is the
  root cause just because Android permits one.
  Reference: https://developer.android.com/reference/android/media/session/MediaSessionManager.OnActiveSessionsChangedListener

### Reliability implementation and checks

KeeperService accepts nullable restart intents, skips missing command extras,
initializes preferences/time formatting per service instance rather than startId,
and explicitly returns START_STICKY. The phone regression exercises null input
on an initialized service with command history enabled. Full OS-driven process
reclamation/restart was not reproduced.

NotificationService no longer reads playback state twice in logging. Null
controller entries are removed before registration. Physical-phone tests cover
null/empty lists, a real MediaSession with absent state, and session release.
The exact Play crash is not reproduced; these fixes remove the identified unsafe
paths without claiming certainty about the optimized stack's original dereference.

Text drawing investigation: a synthetic bitmap-canvas layout/draw probe on the
I2220 measured 55 ms at 100,000 characters with one pass and 120 ms with eight
passes. These single samples are not a hardware-renderer trace or an ANR repro.
Both OutlineTextView and OutlineEditText now cap redraws to 1–8 at the draw entry
point, including extreme hand-edited settings. Bounds regression passed.
The original ANR remains open: investigate large styled history, very long lines,
update frequency and device resource pressure with a system trace. No scrollback
truncation was introduced.

Five phone checks (services, redraw bounds/probe and Back navigation) passed over
USB during development.

Wireless recheck on 2026-09-16 (I2220, Android 16, default Home): all 16 checks
passed, covering services, redraw bounds/probe, Back, language packs, Notes and
reminder removal. A separate edge-swipe Back test also passed. The first run
failed the drawer-dismissal assertion; after explicitly waking the phone,
isolated key/gesture checks and the full suite passed. This establishes the
awake-phone result, not a confirmed diagnosis of that initial failure.
Installed APK hash matched the current debug build. The test harness was removed
after testing; app data was retained. All 174 unit tests have no failures;
both debug flavors and lint passed. The original ANR and Samsung theme issue
remain open.

Optimized Play release APK and AAB also built successfully; signatures verified.
The APK was installed over wireless with the matching signer and existing app
data retained. Launch succeeded, and four system Back presses retained the same
process and resumed Home activity; the crash log buffer was empty. This optimized
candidate was installed on the phone for acceptance before the Build 419 release.

## Issue #9: Android 16 Back navigation

- [x] Final phone acceptance for the local Back callback fix. Launcher now uses
  AndroidX OnBackPressedDispatcher instead of legacy Back overrides/key handling.
  Existing panels/history behavior is retained and Home consumes Back during
  initialization. Test closing the app drawer, repeated Back without recreation,
  and keyboard dismissal using system Back and an edge gesture while default Home.
  Verified on the Android 16 I2220 via USB with Re:TUI as default Home:
  system Back key and actual edge-swipe runs both passed drawer dismissal,
  keyboard dismissal and repeated Back retaining the same activity/task.
  Both debug flavors and lint passed; all 174 unit tests passed. Included in
  Build 419.
  Source: https://github.com/DvilSpawn/Re-TUI/issues/9
- [ ] Investigate the separate Samsung Galaxy A16 Auto Theme failure from #9.
  Works on the reporter's CMF Phone 1; both run Android 16. Obtain wallpaper type,
  exact failure behavior and logs before choosing a fix.
