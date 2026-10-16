# Remaining readiness work — resumed 16 October 2026

## Verified fixes and completed checks

- First-time Termux setup: Android denied hard-link publication of the host key. Complete keys now publish by rename under a kernel advisory lock. A killed setup cannot leave a blocking lock directory. Missing `flock` is handled through the existing explicit Enable installation path (`util-linux` only when missing).
- Six final host setup tests passed, including concurrent first setup with hard links forbidden and retry after a killed lock owner. Actual Android Termux also passed four simultaneous fresh HOME setups with one host identity and a SIGKILL lock-owner recovery check; see lock-check.md.
- Real missing OpenSSH/tmux installation and pairing passed on the disposable emulator. Actual downloaded Clouds system Apply passed twice; see ../readiness-final/emulator/README.md. Phone preset/wallpaper were preserved.
- Permission regression corrected: the instrumented/nested sender and Vivo permission-query API gave misleading results. Real launcher export bytes/MIME pass. Separate uninstrumented sender/recipient transactions prove denied reads before grant, exact bytes/MIME during grant, distinct UIDs, and SecurityException with no bytes after revocation. Both final Vivo tests passed; no production FileProvider permission changes were needed. Reproducible helpers and results are in ../todo-15min/preset/independent-recipient/.
- Reduced-motion setup and missing-pack fixture checks passed; former emulator download failures cleared after restoring its disabled networking.

## Historical blocker — superseded by the final reconnect fix

The real paired SSH appearance test validates both panes' typeface, size, colors, emulator/native tmux dimensions, attachment identity, shell PIDs and Unicode draft retention. It repeatedly passes 15 font-change cycles and four foreground reconnects, then fails on the fifth reconnect with Session.connect connection reset. A two-minute full run has NOT passed.

The obsolete unpaired socket-only appearance fixture is now scoped to its transport, with the actual SSH test replacing that coverage. The fixture also clears only debug pairing before launch so it cannot replace normal authorized keys before taking its backup. Dedicated Termux state was restored byte-for-byte in test cleanup; normal launcher re-provisions its original identity when reopened.

OpenSSH noauth/source penalties were investigated. Disabling the narrow penalty and then all source penalties did not resolve the actual device failure. Those unproven production configuration changes are removed from the final candidate. Do not label this as fixed or production-ready. See ../termux-ssh/evidence/ssh-appearance-20261016/README.md and final-run.log.

## Validation boundary

Full unit suite: 452 total, zero failures/errors, five opt-in live IRC skips. Release APK/AAB build and release lint passed at the recorded build snapshot. Final signed artifact/installed-byte evidence is recorded alongside this report. Six final setup fixtures and five real-tmux dispatcher tests pass. Dispatcher teardown now waits briefly for owned shells' late history writes after tmux kill-server; assertions are unchanged.

No commit, push, website deployment, or public app release was performed. Overnight/OEM battery testing, the broad palette matrix and retirement of the bespoke interactive bridge remain separate follow-ups.

Final phone cleanup: the normal signed build was installed in place and its bytes verified (installed-release.json). Its normal identity was re-provisioned and SSH authentication/window listing succeeded; see ../termux-ssh/evidence/ssh-appearance-20261016/normal-final-pairing.png. Disposable phone test applications and both independent permission helpers were removed. The owned emulator was stopped without wiping its AVD. No user preset or phone wallpaper was applied.

## Final reconnect checkpoint

The [final reconnect-fix validation](../termux-ssh/evidence/ssh-reconnect-fix-20261016/README.md) supersedes the historical blocker above. A real daemon trace proved unauthenticated probe penalties; the implementation removes redundant probes and repairs stale listener PID files, retaining default penalties and strict authentication. The Vivo passed 73 font cycles and 24 foreground reconnects in a 120,203 ms loop, including both panes' native geometry, retained processes/draft and exact control-client cleanup. Full final unit suite: 454 total, 449 passed, five skips, zero failures/errors; release lint zero errors. The signed normal build was installed in place, its bytes verified and normal SSH authentication/window listing checked. Disposable phone test packages were removed and the owned emulator stopped. Longer OEM/battery testing and broad palette coverage remain open.
