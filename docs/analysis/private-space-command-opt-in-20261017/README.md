# Private command opt-in — 2026-10-17

Added **Enable private command** under Behaviour → behavior.xml → Commands. Defaults off. Enabling opens the existing terminal-themed confirmation dialog explaining Android 15+, default HOME, device/firmware compatibility and the Vivo/iQOO limitation. Cancel leaves it off; confirmation creates a draft and normal Save persists it. Disabling hides command discovery and the PRI entry and blocks all six command actions; it does not change Android profile lock state. Private profiles remain segregated from normal app surfaces regardless of the opt-in.

Validation: app and instrumentation builds passed; 473 unit cases, 468 passed, five existing network skips. The real Settings notice/cancel/confirm/Save/disable test passed on the isolated Android 16 emulator. Screenshots and exact evidence are retained here. Localization export/check and source whitespace checks passed. The phone was not updated by this task.

**Open regression:** the wider emulator flow passed its first PIN unlock → private fixture launch, including disabling/re-enabling Re:TUI access without locking the profile. It then failed to unlock the profile on retry after cancelling authentication, twice. Waiting for the cancelled PIN UI to disappear did not resolve it. This is retained in `post-cancel-retry.txt`; the earlier pre-toggle APK's two-cycle pass does not establish a pass for this candidate. No production-readiness or release claim is made.

The two requested upstream launchers were source-reviewed in [additional launcher review](../private-space-launch-20261017/additional-launcher-review.md). No upstream implementation was copied.

The open retry checkpoint above is superseded by the [subsequent retry investigation and release checks](../private-space-retry-20261017/README.md): corrected authentication/fixture synchronization passed three consecutive full flows and a minified release UI check.
