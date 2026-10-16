# Real SSH appearance and reconnect validation — 16 October 2026

The replacement fixture uses two actual SSH/tmux panes. It checks font/typeface/color changes, the geometry of both Android emulators and native tmux panes, retained attachments and processes, split selection, and a Unicode colon draft across pause/resume. It owns only uniquely named fixture windows and restores the dedicated Termux SSH state byte-for-byte; it never changes personal `.ssh` files.

The fixture now clears only the isolated debug application's pairing before launching its Activity. This prevents automatic setup from replacing the user's authorized key before the native-state backup and prevents an early empty window list before fixture creation.

## Findings

- The normal installed launcher was reconnected successfully before testing; see normal-pairing-restored.png.
- The real device runs OpenSSH 10.3p1. Its default noauth penalty is one second, accumulated to a 15-second enforcement threshold. The Termux `flock` binary belongs to `util-linux`; see runtime-config-before.log.
- Repeated foreground reconnects reproduce an actual connection-reset failure after 15 font cycles and four completed reconnects. Both panes, native geometry, and processes passed until that reconnect. See resume-run.log/resume-failure.json.
- The initial candidate disabling only the noauth duration still failed identically; see fixed-run.log/fixed-run-failure.json. Do not count that candidate as a full stability pass.
- The final candidate disables source penalties only in the dedicated loopback SSH service, keeps public-key-only authentication, strict host pinning, forwarding restrictions, and foreign listener checks, and reloads the verified dedicated daemon when its validated configuration changes. Unsupported older OpenSSH versions omit the unavailable directive. The final candidate also failed the real-device test identically after 15 font cycles and four completed reconnects (41.082 seconds); see final-run.log and final-run-failure.json. This mitigation does not close the production blocker. Further diagnosis must confirm the running daemon actually loaded the new configuration and identify the connection reset.

OpenSSH explicitly warns that noauth penalties can penalize ssh-keyscan: https://man.openbsd.org/sshd_config.5 . Existing accumulated penalty state is a suspected contributor to the initial narrow candidate's failure; no daemon penalty log was available to directly prove that cache behavior.

Long overnight/OEM battery testing remains outside this short run.

Both speculative source-penalty configuration changes and their reload logic were reverted after the failed device proof. The shipping candidate retains the prior strict SSH configuration and the independently tested kernel host-key lock fix. The real reconnect blocker remains open.
