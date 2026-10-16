# SSH reconnect fix — final phone validation, 16 October 2026

The normal shipping SSH daemon passed a 120,203 ms appearance/reconnect loop on Vivo I2220, Android 16, serial `10BE1C0FB40006A`: **73 font-change cycles and 24 foreground reconnects**. The complete device test passed in 123.263 seconds. Diagnostic daemon mode was disabled. See `final-phone-run.log` and `ssh-appearance-proof.json`.

Both split tiles followed font size/typeface/color changes; Android emulator dimensions matched native tmux dimensions. Both remote shell PIDs, split selection and the Unicode colon draft survived. Each pause restored the exact pre-existing tmux control-client PID set; there were zero pre-existing control clients in this run. The fixture restored dedicated Termux SSH files byte-for-byte and preserved personal SSH files.

## Fix and evidence

The daemon trace in `DIAGNOSIS.md` proves that repeated unauthenticated keyscans and a throwaway readiness socket triggered OpenSSH source penalties. Setup now skips scanning a verified dedicated listener. JSch authenticates the actual readiness socket. Host-key pinning, public-key-only authentication and default OpenSSH penalties remain enabled. A stale daemon PID file is repaired by verifying the executable and exact dedicated configuration argument. Explicit SSH stdin EOF plus bounded off-main-thread disposal closes the owned tmux control client.

The first fixed-build attempt stopped after 16 font cycles / five reconnects with a null-terminal exception in the fixture's geometry predicate. It did not capture the underlying pane state and is not counted as a passing run (`first-fixed-run-fixture-error.log`). The predicate now waits for non-null terminals and records existing diagnostic state on timeout; all geometry, identity and reconnect assertions remain. An intervening invocation reported process crash and is not counted. The subsequent full default-daemon run above passed. This is short-run evidence, not an overnight stability claim.

## Build and installation

- Full unit suite: 454 total, 449 passed, five existing opt-in live IRC skips, zero failures/errors.
- Release lint: zero errors, 614 warnings and 311 hints. Signed Playstore release APK/AAB built successfully.
- Host checks: eight setup, three daemon PID recovery and five real-tmux dispatch tests passed.
- Normal signed build 503 installed in place on the Vivo; installed APK bytes match the candidate (`installed-release.json`). Its signing certificate matches the existing release identity.
- The normal launcher successfully re-paired/authenticated and listed the two fixture windows left by the interrupted invocation after installation. Those two owned fixture windows were subsequently removed by exact name through the authenticated normal pane; the final workspace has no chooser when no windows exist (`normal-final-pairing.png`). No new user window, preset or wallpaper was applied.
- Disposable debug/test applications removed. The owned emulator stopped without wiping its AVD. No commit, push, deployment or public app release performed.

Candidate artifacts: `app/build/ship-candidate/retui-3-build503-ssh-reconnect-fix.apk` and `.aab`. SHA-256 values are recorded in `installed-release.json`.

Overnight/OEM battery behavior, the broad combined palette matrix and the other explicitly open journeys in `CHAT_TODO.md` still require separate release coverage.
