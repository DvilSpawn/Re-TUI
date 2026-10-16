# Re:TUI bridge versus localhost SSH into Termux

Analysis and experiments: **15 October 2026**.

**Current status, 16 October:** SSH is now the standard interactive pane in normal builds; native Termux setup/modules remain. This document preserves the original analysis. See [Standard default and upgrade verification](SSH_STANDARD.md) for current behaviour and [Production readiness](PRODUCTION_READINESS.md) for the broader test record.

## Decision

**Keep the Android/Termux command integration. Prefer SSH + a continuous terminal-emulator session as the direction for interactive terminals. Keep the working 0.4.4 workspace available until an Android SSH pane proves feature parity.**

The custom screen-capture protocol is useful, working integration, but I would not keep expanding it to become a general terminal. It makes Re:TUI maintain capture/projection, input encoding, session synchronization and helper compatibility that a normal tmux terminal attachment already handles. **The newer release already incorporates Termux's terminal engine**; the SSH case is about changing how that engine receives terminal state, not introducing an engine for the first time. SSH does not remove all integration work; it changes that work into a smaller bootstrap/workspace adapter around a standard terminal connection.

This recommendation is based on source inspection, **three new physical-device backend runs**, a rerun of the existing host emulator experiment, and the current helper's real tmux tests. SSH had a consistent backend latency advantage over the newer output-triggered bridge, not just over the old polling implementation. It also passed an alternate-screen reconnect test. Independent tiles are feasible through grouped tmux sessions.

**The bridge is worth keeping as native integration and as a transition path. The complete bespoke terminal transport/grid is not the long-term investment I recommend if Re:TUI intends to provide a capable interactive terminal.** A deliberately limited captured-workspace viewer would be a valid reason to retain it permanently, but that would mean accepting its terminal feature limits rather than continuously filling them in.

## Exact scope and an important version difference

The new worktree is on `dvil/termux-ssh-analysis`, based on local commit `6627e8cdcacc22e75094e342b9da5dedadc5a93a`. All **16 locally changed tracked paths and 18 untracked files** were copied from the source checkout. The binary diff and copied file contents matched. Ignored build outputs and local configuration were not copied; the source index was not changed. The [snapshot manifest](evidence/local-work-snapshot.json) records this boundary.

There are two different implementations to assess:

| Evidence | Implementation |
|---|---|
| This checkout and its copied local work | Helper **0.4.2**, `STREAM` mode repeatedly captures tmux, compares frames, sleeps for 0.16 seconds after each iteration |
| Helper read directly from connected phone | **0.4.4**, adds `OUTPUT`: persistent tmux control connection, output-triggered authoritative captures, cached geometry, `TYPE_HEX` persistent input |
| Phone's installed launcher | Build **502**, versionName 3; its installed bytecode was not audited in this analysis |
| Local Build 502 release source | Commit `bce4a7115c666afde43947b00a7f228c6e304113`; its helper matches the phone byte-for-byte. The Activity negotiates `OUTPUT`, uses `TYPE_HEX` through its client, and projects frames through `TermuxWorkspaceTerminal` / upstream `TerminalEmulator` before copying cells to the themed grid |

The 0.4.4 helper still retains the polling `STREAM` mode. Its presence alone does not prove a particular installed APK connection negotiates `OUTPUT`; the experiments explicitly negotiated it, and the matching release source explicitly does so. The installed helper was copied read-only into [evidence](evidence/phone-installed-retui-0.4.4.sh), not substituted into the worktree's production helper. Its SHA-256 remained `2e21282cf95393ec8ce83c3fbf1d0860002979b6d163335cf472cdbb6351091a` after the tests. Relevant [release source and provenance](evidence/build-502-source/provenance.json) are saved separately for review.

Any implementation follow-up should first reconcile this source/device difference. Judging only 0.4.2 would exaggerate how much SSH improves upon the newer work.

## What the bridge currently does

There are three layers, and they should not be judged as one indivisible component:

1. **Android integration:** `TermuxBridgeManager` starts Termux's `RUN_COMMAND` service and receives results through a `PendingIntent`. It supports command dispatch, module results and suggestion requests as well as workspace startup.
2. **Workspace model:** Re:TUI owns pages, up to two independent tiles, focused input, saved launchers, window IDs, themes, a key tray and local colon commands. Closing a view leaves jobs running. Restoring an empty workspace does not silently create a shell.
3. **Interactive presentation:** a bearer-token socket connection carries custom input messages and Base64 screen frames. The checkout's grid interprets captured rows directly. In the newer release, `TermuxWorkspaceTerminal` creates a fresh upstream emulator from each authoritative frame, restores selected modes/cursor state, and copies its cells into the themed grid. Output-triggered captures and redraw coalescing improve this path substantially, but it remains capture-based.

Replacing layer 3 does not require deleting layers 1 and 2. Replacing the socket with an SSH channel **while still carrying the same captured frames** retains nearly all of layer 3's costs. That is not the migration recommended here.

The useful alternative is:

```text
Android RUN_COMMAND bootstrap / command-result integration
                    |
Re:TUI workspace UI + terminal emulator
                    |
        SSH PTY channel to 127.0.0.1
                    |
     normal tmux terminal client in Termux
                    |
             persistent shell / TUI
```

## Physical-device measurements

Device: `10BE1C0FB40006A`, I2220, Android 16 / API 36; Termux 0.118.3, tmux 3.7b, OpenSSH 10.3p1. No packages or APKs were installed.

Each path ran the same raw-echo job inside a private tmux server at 80×24. Host writes reached loopback-only device listeners through USB/ADB forwarding. Each native-input path had three warmups and **40 measured samples**. The newer helper used `OUTPUT` plus `TYPE_HEX`; the old helper used `STREAM` plus `TYPE`; SSH used a normal allocated PTY attached to tmux. The timings end at a complete decoded helper frame or decrypted SSH stdout containing the marker.

The most useful run measures **one active observer at a time**, so the retired polling loop cannot compete with the newer bridge or SSH:

| Path | Median | p95 |
|---|---:|---:|
| Checkout helper 0.4.2, polling | **500.04 ms** | **527.96 ms** |
| Phone helper 0.4.4, output-triggered captures | **53.35 ms** | **58.37 ms** |
| SSH PTY → normal tmux attachment | **15.42 ms** | **29.56 ms** |

Source: [isolated run with all samples](evidence/phone-probe-isolated.json).

Two earlier runs kept all three observers attached and randomized which path sent each native-input sample. Those results are retained rather than discarded:

| Path | Run 1 median / p95 | Run 2 median / p95 |
|---|---:|---:|
| 0.4.2 polling | 506.00 / 851.52 ms | 496.82 / 839.78 ms |
| 0.4.4 output-triggered captures | 47.95 / 56.09 ms | 47.79 / 56.30 ms |
| SSH PTY | 18.55 / 28.76 ms | 17.15 / 26.99 ms |

Sources: [run 1](evidence/phone-probe-run1.json), [run 2 and functional checks](evidence/phone-probe.json).

In run 2, a separate **paired-output** experiment sent exactly the same input through 0.4.4 and timed all three observers. The output-triggered helper returned its complete capture at 46.08 ms median; SSH exposed terminal bytes at 12.22 ms. This supports the conclusion that capture/serialization still costs time after persistent input is available. It does **not** demonstrate that SSH encryption is faster than a local socket; the architectures carry different output representations.

Other observations:

- During ten idle seconds, 0.4.2 invoked the tmux executable **162 times in run 1 and 168 in run 2**. The output-triggered helper and SSH each invoked it **zero additional times**. This counts wrapper invocations, not total CPU, process count or battery use. Modern bridge idle efficiency is therefore not a demonstrated reason to switch to SSH.
- During run 2's 43 paired inputs including warmups, the newer helper emitted 32,181 application-protocol bytes versus 7,093 decrypted SSH PTY bytes. **SSH encryption/packet overhead is excluded**; this is evidence of full-frame amplification, not a network-bandwidth or energy benchmark.
- Initial attachments varied substantially: modern bridge 251.90–355.94 ms; SSH 227.37–362.30 ms. These are two individual observations per path after listeners were started, not a cold-start distribution. There is no established startup-time winner.

**Limits:** these are backend experiments on a real phone, not tap-to-pixel measurements. They exclude Android terminal parsing, Canvas rendering, IME behavior and the eventual Android SSH client library. A short raw-echo fixture does not prove Vim, htop, long paste, sustained bursts, overnight reliability or battery savings. Fixture listeners ran under debuggable `run-as`, not under the production Termux foreground-service lifecycle. The sequential isolated run also does not control thermal or scheduling variation. The two additional runs strengthen the direction of the result, not its applicability to every device.

## The tradeoffs that matter

| Area | Keep the modern bridge | SSH PTY + terminal emulator | Assessment |
|---|---|---|---|
| Interactive latency | Approximately 48–53 ms median in these backend runs | Approximately 15–19 ms median | SSH path wins here; drawing still unmeasured |
| Idle | No periodic capture in `OUTPUT` mode | No capture loop | No measured reason to prefer SSH for battery |
| Terminal compatibility | tmux interprets apps; newer release projects captures through an upstream emulator and themed grid | tmux emits continuous terminal updates to an emulator | SSH removes projection constraints; renderer improvements are a separate task |
| Reconnect | Latest captured screen restores easily | Normal tmux attachment redraws terminal state | Both viable; SSH avoids implementing a raw-observer state rebuild |
| Native tiles/pages | Already integrated and independently targeted | Retain the UI, add grouped tmux sessions and metadata handling | Real migration work, not a blocker |
| Bootstrap | Already starts through the Android API | Needs sshd running; can retain the same API to start it | A pure SSH connection cannot wake a dead daemon |
| Trust | Private bearer token; no cryptographic server identity in the inspected client | Client key plus pinned host key | SSH improves authentication if pairing is implemented correctly |
| Dependencies | tmux/socat/awk; modern release already vendors the terminal engine; custom protocol/grid | tmux/OpenSSH plus Android SSH client; reuse existing engine where suitable | Adds an SSH library, rather than an entirely new terminal engine |
| Other Termux features | Existing command/result and module integration | PTY stream does not replace structured command results | Keep the thin Android integration |

### Terminal fidelity is primarily a renderer/session decision

The old checkout's `TerminalGridView` processes captured rows and SGR styling directly. For example, it recognizes zero-width combining marks but skips writing them. **That particular limitation is already addressed in the newer release's emulator-cell path**, which appends combining characters to the previous glyph. It would be misleading to count that as a new SSH benefit.

The newer `TermuxWorkspaceTerminal` already uses upstream `TerminalEmulator`, but creates it afresh for each captured screen. It reconstructs selected screen/mode state and deliberately ignores terminal replies, title changes, clipboard and bell callbacks because tmux owns the live application-side terminal. The grid's emulator-cell path copies bold, inverse and color attributes; richer attributes still need renderer work.

A normal SSH terminal session can reuse that engine with a different owner: continuously append tmux's terminal-client output, send engine replies back to the SSH channel, and deliberately implement the relevant callbacks. **Reusing today's no-op output callback would break terminal query responses in the SSH design.** The existing grid's attribute limits do not disappear just because its input becomes SSH bytes; it must be extended or a fuller renderer integrated.

Re:TUI would still need input, paste, clipboard, selection, accessibility, theme and redraw integration. Capture-based projection does not itself deliver persistent local scrollback, terminal mouse input or bracketed-paste handling. Neither this analysis nor installing an SSH library establishes those features. The release-source review is stronger evidence than the old checkout alone, but is not a bytecode audit of the installed APK.

The existing host tmux-to-Termux-emulator experiment was rerun. It passed fragmented UTF-8/escape handling, wide cells, color, alternate screen, output bursts and per-window resize checks. It also reproduced the danger of seeding a raw `%output` observer from only one visible screen: hidden normal-buffer state can be lost after reconnect. [New host result](evidence/host-emulator-probe.json).

That weakness must not be incorrectly assigned to normal SSH tmux attachment. The latter uses tmux's terminal frontend, which redraws the display; it is not just a raw pane-output observer. On the phone, I entered an alternate screen, disconnected SSH, reattached and observed the alternate screen's redraw. Exiting that mode produced the retained normal-screen content. This verifies the redraw bytes, not Android visual fidelity.

### Two SSH panes need an adapter, not a replacement protocol

I opened two real SSH PTY clients on the isolated device server. Both attached to the same tmux session. Selecting a window changed what **both** clients displayed. A naive pair of identical `tmux attach` commands would regress Re:TUI's independent tiles.

I then created grouped sessions. They shared windows but retained different selected windows; closing one group member left the other alive. This is the behavior documented by [tmux session groups](https://man.openbsd.org/tmux#new-session).

The migration would need one attachment session per visible tile, stable mapping to the underlying workspace windows, metadata updates for the page/window UI, independent PTY sizes and cleanup of attachment sessions. It must preserve explicit Add, final-shell exit, missing-window reconciliation and the existing rule against displaying the same window in both tiles. Two viewers of one tmux window still share its size under either transport.

This is manageable integration work, but the test did not build the complete Android adapter or verify two actual Android terminal views at different sizes.

### Bootstrap and Android lifetime remain necessary

The native bridge already uses Termux's supported `RUN_COMMAND` interface. That interface requires user authorization and `allow-external-apps`; it can also return structured command output. [Termux API documentation](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent).

An SSH-only setup could avoid that permission if the user provisions and starts the daemon separately. It would then depend on manual startup, Termux:Boot/services, or another startup arrangement. For Re:TUI's integrated flow, retaining `RUN_COMMAND` for provisioning/startup and ordinary command results is the smaller change.

SSH does not escape Android process restrictions or make Termux immortal. Termux recommends keeping relevant processes tied to its app lifecycle where possible, and describes the difference between daemonized sshd and `sshd -D`. [Termux execution environment](https://github.com/termux/termux-packages/wiki/Termux-execution-environment). A production implementation needs the service ownership tested; none of the backend timings establishes that reliability.

### SSH offers a real trust improvement, with provisioning work

The current client sends the reusable bearer token in `HELLO` before receiving a response and accepts protocol/status/session markers as the handshake. Those markers do not cryptographically prove which process owns the listener. A process able to impersonate the expected local listener could receive that token. This is an inference from the inspected handshake, not a demonstrated exploit or a claim that other apps can read Termux's private token file.

SSH can verify the server before user authentication, and a pinned host key makes local listener impersonation detectable. The phone test used a separate generated host key, separate authorized client key, strict host verification and loopback binding; an unknown host key was rejected. It never touched the user's `.ssh/authorized_keys` or normal sshd configuration. [OpenSSH authentication and host verification](https://man.openbsd.org/ssh.1#AUTHENTICATION).

Production needs trusted pairing, protected app-private key storage, exclusion from inappropriate backups, revocation/unpairing, and clear recovery after reinstall or host-key change. Revoking Android `RUN_COMMAND` permission alone would not revoke an SSH key. Bind the dedicated endpoint explicitly to loopback and disable unneeded access methods/forwarding; do not alter the user's existing general SSH server. These are requirements of this proposed endpoint, not changes made during the analysis. [sshd configuration](https://man.openbsd.org/sshd_config).

### Code that could go away, and code that stays

The checked-out helper has 738 lines, socket client 235, frame parser 47 and grid 496: **1,516 lines in the old replacement area**. The newer release has a 914-line helper, 262-line socket client, 50-line frame parser, 39-line terminal projector, 27-line output decoder and 549-line grid: **1,841 lines across those files**. These counts are maintenance inventory, not promised net deletion; the helper contains useful workspace operations, and the grid/theme work may be reused. [Checkout inventory](evidence/source-inventory.json), [release-source inventory](evidence/build-502-source/provenance.json).

The checkout's workspace Activity, state model, input view and saved-launcher manager total **1,073 lines** and largely remain relevant. So do existing module/command-result integrations elsewhere in `UIManager`, `SuggestionsManager` and the Termux result classes. Some of that code would change, but it is wrong to count it all as removable SSH savings.

SSH would let the terminal path retire its custom frame protocol, polling/capture logic, screen serialization and snapshot-to-emulator reconstruction. In exchange, Re:TUI must integrate an Android-compatible SSH library, wire the existing terminal engine for continuous bidirectional operation, maintain host-key/key lifecycle, and write the small tmux workspace adapter. Renderer work remains necessary for features beyond what the current themed grid displays. The old checkout has no terminal-engine integration; the newer release already compiles the vendored engine, so its entire engine cost must not be charged again to the migration. Neither declares an SSH client dependency. Android APK size, SSH-library compatibility and release-build behavior were not measured. The Android app cannot simply execute Termux's private `ssh` binary across the app sandbox.

Keeping the current transport and streaming raw tmux output to a full emulator is another feasible direction. The host prototype proves that it can be fast without SSH. However, it leaves Re:TUI responsible for reconnect synchronization, tmux output decoding, flow control and terminal-query ownership. Normal tmux-over-SSH attachment avoids more of that custom work. There is no evidence here that the local socket transport itself is the performance problem.

## Recommended next implementation boundary

Implement one separate Android SSH terminal pane as a replacement candidate, preserving the current workspace while it is evaluated. Reuse the newer release's upstream terminal engine with the required bidirectional callbacks, a dedicated loopback-only SSH endpoint and trusted pairing through the existing Android integration. Retain tmux for jobs and a small workspace adapter for metadata and grouped attachment sessions. Decide renderer scope explicitly rather than treating SSH as a renderer fix.

Before switching the default, that candidate should demonstrate:

1. Same-device input-to-drawn-frame median/p95 against the 0.4.4 implementation, plus output bursts and idle CPU/memory measurements.
2. Real Vim/nano/htop interaction, rapid Unicode typing/composition, combining characters, large/bracketed paste and selection/scrollback.
3. Existing top-bottom/left-right layouts, independent resize, page/window focus, saved launchers and explicit empty-workspace behavior.
4. Reconnect during editor alternate screens, IME/rotation resize, background/resume and Android process death, without killing or recreating jobs.
5. Correct host-key refusal, reinstall/re-pair/revoke behavior, startup when sshd is stopped, and a release-build-compatible Android client library.

That is an implementation follow-up, not work completed in this analysis. Until it passes, the modern bridge remains the fallback. Once it passes, keep the native command/bootstrap integration and retire the duplicate interactive capture path rather than maintaining two full terminal implementations indefinitely.

## Evidence and reproduction

- [Phone comparison and functional-check harness](phone_probe.py). It uses fresh UUID-named device storage, private tmux sockets, separate ephemeral keys/config and temporary ADB forwards. It requires the debuggable Termux and already installed tools on this test device.
- [Isolated phone run](evidence/phone-probe-isolated.json), [paired run 1](evidence/phone-probe-run1.json), [paired run 2 and functional checks](evidence/phone-probe.json).
- [Device inventory](evidence/phone-inventory.txt) and [installed helper source](evidence/phone-installed-retui-0.4.4.sh).
- [Matching Build 502 source](evidence/build-502-source/provenance.json), including its existing [terminal projector](evidence/build-502-source/TermuxWorkspaceTerminal.kt), [cell renderer](evidence/build-502-source/TerminalGridView.kt), [Activity](evidence/build-502-source/TermuxWorkspaceActivity.kt) and [socket client](evidence/build-502-source/TermuxWorkspaceSocketClient.kt).
- [Host emulator experiment result](evidence/host-emulator-probe.json); the existing `scripts/prototypes/termux-stream/README.md` explains its narrower host-only scope.
- Existing real-tmux helper checks: **8 passed**; tile checks: **5 passed**. No app build or Android instrumentation run was required for this documentation/experiment-only change.

From this worktree:

```sh
PYTHONDONTWRITEBYTECODE=1 python3 docs/analysis/termux-ssh/phone_probe.py \
  --serial 10BE1C0FB40006A --samples 40 --output /tmp/retui-phone-comparison.json

PYTHONDONTWRITEBYTECODE=1 python3 docs/analysis/termux-ssh/phone_probe.py \
  --serial 10BE1C0FB40006A --samples 40 --isolated --output /tmp/retui-phone-isolated.json
```

After all three runs, temporary device directories and ADB forwards were absent, and the installed helper's hash was unchanged. No production source behavior, personal tmux sessions, SSH keys/configuration, APK, commit or release was changed by this analysis.

## Source locations reviewed

Paths below are relative to the repository root; line numbers refer to this worktree snapshot.

| Source | Relevant behavior |
|---|---|
| `termux/retui/bin/retui:157` | Resize and per-operation geometry work |
| `termux/retui/bin/retui:177` | Authoritative captured screen and metadata |
| `termux/retui/bin/retui:217` | Polling stream loop in 0.4.2 |
| `termux/retui/bin/retui:438` | Listener startup, loopback fallback and helper bootstrap |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceSocketClient.kt:39` | HELLO, local socket/TCP and input protocol |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceSocketClient.kt:139` | Response-marker handshake, bounded frame reader |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TerminalGridView.kt:196` | Captured-row parsing and supported styling |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TerminalGridView.kt:402` | Combining-mark and width handling |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt:323` | Termux service bootstrap and helper-version coupling |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceActivity.kt:419` | Per-tile connections, frame application and focus |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxWorkspaceState.kt` | Persistent pages, targets and empty-workspace reconciliation |
| `app/src/main/java/ohi/andre/consolelauncher/managers/termux/TermuxBridgeManager.kt:124` | Android service/structured-result integration |
| `app/src/main/java/ohi/andre/consolelauncher/UIManager.kt:11321` | Ordinary Termux shell dispatch |
| `app/src/main/java/ohi/andre/consolelauncher/UIManager.kt:11612` | Result routing, including modules |
| `app/src/main/java/ohi/andre/consolelauncher/managers/suggestions/SuggestionsManager.kt:3570` | Termux listing requests via native integration |
| `docs/analysis/termux-ssh/evidence/phone-installed-retui-0.4.4.sh:222` | Newer output-triggered capture implementation |
| `docs/analysis/termux-ssh/evidence/phone-installed-retui-0.4.4.sh:738` | Persistent byte-input implementation |
