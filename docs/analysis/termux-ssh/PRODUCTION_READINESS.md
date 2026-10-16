# Termux SSH pane validation

**16 October follow-up:** the four previously failing gates are fixed. The final full unit suite has 433 passes, zero failures and five preexisting opt-in live IRC skips; release lint has zero errors. APK and Play bundle builds pass. [Follow-up fixes and current evidence](PRODUCTION_BLOCKERS.md). The results below preserve the initial validation snapshot.

Worktree: `dvil/termux-ssh-analysis`. The original checkout and copied calculator work are preserved. SSH is now the default interactive transport in normal builds; see [current standard-mode and upgrade evidence](SSH_STANDARD.md). No commit, push, publication, or replacement of the installed production launcher is part of this work.

## Defects found and corrected by testing

- **Actual PTY attachment failed:** tmux `set-option` parses targets differently from `has-session`; the grouped viewer needs the `=session:` form. The dispatcher now uses that form and preserves failed attachment exit status. The regression opens two real PTYs through the actual dispatcher, rather than merely reproducing its individual tmux commands.
- **Release shrinking broke SSH initialization:** real minified connection tests exposed reflective construction of a package-private key-exchange factory from a moved caller, and a non-public authentication constructor. Conditional rules preserve the core package relationship and required constructors; the disabled build still discards JSch. A separate internal signed package exercises the actual two-PTY wire path.
- **Android 6 identity generation failed:** JSch uses a Java API added after API 23. Core library desugaring supplies that API. Both platform crypto and the application identity path are checked on API 23.
- **Wide characters were clipped in editors:** background fills from following cells overwrote CJK glyphs. The renderer now paints all backgrounds before glyphs, verified by a bitmap regression and physical editor screenshots.
- **Input could precede attachment:** a tile becomes writable after its first terminal output. Readiness now follows the focused tile, including a newly attached second tile.
- **Multiline paste could send only its opening delimiter:** paste delimiters and filtered body are now one bounded transport write, so queue rejection cannot leave the persistent shell inside an unfinished paste.
- **A control response could wait indefinitely:** bounded response size now also has an eight-second deadline, with cancellation on connection close. A stalled-channel regression checks that the code never calls a blocking read when there are no available bytes.

- **Closing a pane corrupted the next SSH packet:** JSch encrypts a channel-close packet before writing it. Android rejected that socket write on the UI thread; JSch swallowed the exception, leaving its cipher state ahead of the wire. Both CTR and an independent GCM experiment failed at that same boundary. All channel and connection disposal now runs off the UI thread. The UI regression records StrictMode network violations and requires zero through rapid window replacement, Add and lifecycle cleanup. Temporary cipher and logging experiments were removed.
- **Overlapping attachment cleanup could target a replacement:** each attachment now owns a unique remote viewer name. A bounded two-worker attachment queue keeps channel establishment separate from window commands, discards stale opens, and purges cancelled queued work.
- **Permission consent could be lost while paused:** the Enable action survives Android's permission callback arriving before resume and continues automatically after a grant.

## Evidence boundaries

Physical tests target Vivo I2220, Android 16/API 36, serial `10BE1C0FB40006A`, with Termux 0.118.3 and tmux 3.7b. Test application IDs are separate from the installed launcher. Vivo freezes background test processes, so the actual test screen is kept foreground; this is not an application workaround.

- Full JVM suite: **438 tests, 431 passed, two failed, five skipped**. All **27 focused Termux tests passed**. A source-scanning legacy socket regression initially matched the new SSH callback; its extraction now scopes to the actual socket listener and passes without relaxing the no-replay assertions. [Counts and failures](evidence/production/final-jvm-results.json), [full JVM log](evidence/production/retui-ssh-final-jvm-gates.log).
- Five setup fault/concurrency fixtures and five real-tmux dispatcher tests passed.
- Physical paired SSH integration, terminal engine, and platform crypto: four tests passed.
- Physical bulk/reconnect stress: 64 KiB Unicode input verified by a SHA computed in a raw-mode remote process; a 20 KiB UTF-8 burst; five full SSH disconnect/reconnect cycles; native PTY resize checks; same live job PID and screen after every reconnect. Passed in 4.75 seconds. tmux may coalesce burst output before SSH delivers it; the assertion does not claim every transient cell was drawn.
- Live negative security checks passed: foreign client key, arbitrary command, command injection, unrestricted shell, and port forwarding are rejected. No test file was created. Dedicated listener configuration and personal SSH settings were not changed by these negative checks.
- Final cleanup uninstalled the temporary legacy/release-check packages. An explicit audit using the tmux server environment found no sessions or windows; the three earlier owned fixture names were already absent, so final cleanup deleted no tmux windows. The debug candidate and tests remain paired with the normal dedicated listener; the original launcher package and personal SSH settings remain intact. [Cleanup proof](evidence/production/final-cleanup.json).

[Security results](evidence/production/ssh-security-negative-checks.json), [stress proof](evidence/production/stress-proof.json), [shell setup log](evidence/production/retui-ssh-final-setup-fixtures.log), [dispatcher log](evidence/production/retui-ssh-final-dispatch-fixtures.log).

## Performance method

The opt-in Android benchmark sends a character through the real pane input route to a raw-mode Python process in an owned tmux window. It waits for that program's distinct `FRAME_<character>` result and posts from the terminal view's draw callback, so the timing includes a completed Android draw traversal. Two warmups are discarded; each layout has 24 measured inputs. This is not a hardware display scan-out measurement or an IME typing study.

Each layout also records ten seconds of foreground idle application CPU and PSS. Native Termux sampling records stable PID/start-time CPU counters across the same interval and whole-UID PSS, including existing jobs held constant. It excludes the sampler and cannot include CPU from short-lived processes that exit between samples. Both application variants use the same instrumentation and phone; these are debug-build comparisons, not release RAM estimates.

## Measured comparison

| Layout / transport | Draw median | Draw p95 | App idle CPU | Termux idle CPU | App PSS | Termux PSS |
|---|---:|---:|---:|---:|---:|---:|
| Single / existing 0.4.4 bridge | 58.62 ms | 86.85 ms | 0.88% | 0.30% | 168.4 MiB | 66.6 MiB |
| Single / SSH | 18.45 ms | 36.34 ms | 1.02% | 0.29% | 173.5 MiB | 40.9 MiB |
| Left-right / existing bridge | 42.52 ms | 59.36 ms | 0.82% | 0.20% | 166.5 MiB | 42.6 MiB |
| Left-right / SSH | 16.56 ms | 21.21 ms | 1.11% | 0.59% | 181.1 MiB | 36.7 MiB |

CPU percentages are fractions of one core. P95 uses nearest rank. This is one successful debug run per variant, 24 inputs per layout and ten seconds idle; it supports a responsiveness improvement, not a battery-life or release-memory guarantee. The clean comparison shows SSH using about 0.13 percentage points more combined CPU in single mode and 0.69 more in split mode. App PSS is about 5–15 MiB higher with SSH; combined PSS is lower in single mode and about 9 MiB higher in split mode. This is a responsiveness tradeoff, not a consistent CPU/RAM saving. PSS includes existing native jobs and may drift with cache/reclamation. Dedicated SSH was stopped for the legacy run. The first legacy native bootstrap was killed with exit 137; foregrounding Termux restored execution. A first successful sample measured ~74 ms median but retained one idle fixture from the failed bootstrap; that file is retained as historical evidence. The table uses a clean repeat after removing that fixture, with the same preexisting job set. Neither transport bypasses OEM background restrictions.

[Raw legacy data](evidence/production/final-legacy-benchmark.json), [raw SSH data](evidence/production/final-ssh-benchmark.json), [calculated comparison](evidence/production/final-benchmark-comparison.json).

## Additional physical proof

- Final UI journey and terminal/bitmap regressions: **three passed**, including both split layouts, independent input, chooser replacement, Add/page navigation, saved launcher, Unicode saved through real Neovim, resume and activity recreation. StrictMode recorded **zero UI-thread network violations** through connection cleanup. [Log](evidence/ssh-ui/final-2.28.8-device-test.log), [left-right screenshot](evidence/ssh-ui/03-left-right.png), [editor after resume](evidence/ssh-ui/09-vim-after-resume.png).
- Extended final UI/IME run: **three passed in 15.525 seconds**, preceded by a fresh native debug re-pair/integration pass. Synthetic `InputConnection` composing updates, Unicode commits and the IME GO action save exact remote bytes, without provisional/duplicate text. The actual installed keyboard opens/closes; Android insets, engine rows and native tmux pane height shrink and recover together. This does not automate a third-party keyboard’s own composition engine. [Final log](evidence/ssh-ui/final-ime-ui-device-test.log), [installed APK hashes](evidence/ssh-ui/final-ime-provenance.json), [keyboard shown](evidence/ssh-ui/10-input-connection-ime-shown.png), [keyboard hidden](evidence/ssh-ui/11-input-connection-ime-hidden.png). Screenshots support layout and visible text; exact file readback proves all Unicode bytes, including emoji.
- Actual Enable button and Android permission dialog: **one passed**; granting continues automatically without a second Enable tap. [Log](evidence/production/enable-permission-device.log).
- Process death: external force-stop killed application PID 15170; restarted PID 15522 restored the same live shell PID 15257, selected window and output, then accepted new input. [Proof](evidence/production/process-death-proof.json), [verification log](evidence/production/process-death-verify.log), [reproduction script](../../../scripts/test_termux_ssh_process_death.py).
- Final stress plus benchmark: **two passed**, 33.18 seconds. [Stress proof](evidence/production/stress-proof.json).
- Android 6/API 23 terminal and wide-glyph regressions: **two passed**. Release compatibility and exact APK provenance are tracked in [Release validation](RELEASE_VALIDATION.md).

## Recommendation and rollout boundary

Keep native Android RUN_COMMAND integration for permission/setup, module commands, suggestions and structured results. Use the SSH PTY for continuous interactive pane traffic, with Termux/OpenSSH/tmux owning processes and persistent shells. Preserve the existing Re:TUI split-pane controls and appearance. After cutover is proven, remove the duplicate bespoke interactive transport rather than expanding both.

The final optimized internal APK passed **four physical-device checks**, including native setup, pinned SSH, two independent PTYs and shell preservation. The identical APK passed **three API 23 compatibility checks**. Its installed bytes were pulled back and SHA-256 verified. The enabled candidate is about **138 KiB (1.84%)** larger than the same-source disabled release; the disabled release retains zero JSch/SSH implementation classes. This comparison includes different package/signing metadata. [Exact results and provenance](RELEASE_VALIDATION.md), [internal test APK](evidence/release-compat/retui-termux-ssh-release-check.apk).

At the initial checkpoint, the working branch had two calculator palette-registry failures and two AppsManager API-level lint errors. The follow-up fixes all four, with no skipped palette check or lint suppression; [current results](PRODUCTION_BLOCKERS.md) supersede that release-blocker verdict. The [initial lint report](evidence/production/final-lint-results.txt) remains as before-fix evidence. The later [standard-default change](SSH_STANDARD.md) enables SSH in normal builds. No commit, push or release was made.

Short tests on one Vivo device and an API 23 emulator do not prove overnight stability, battery impact, other OEM background policies, physical rotation or every third-party keyboard. The phone already had tmux/OpenSSH installed; missing-package installation and failure recovery were fixture-tested, without uninstalling those tools from the user’s Termux. Port 8928 is fixed, so a conflicting listener requires user recovery. Revoking Android RUN_COMMAND blocks the app’s native setup path but does not delete the dedicated SSH authorized key; key lifecycle remains a separate pairing boundary. Existing terminal UI limits also remain: scrollback selection, mouse interaction, richer attributes and clipboard/title/bell callbacks are not added by switching transport.

