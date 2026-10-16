# Launcher worktree and branch checkpoint — 16 October 2026

Target: `/Users/mako/.codex/worktrees/termux-ssh-analysis/TUI-ConsoleLauncher` on `dvil/termux-ssh-analysis`. Initial HEAD: `6627e8cdcacc22e75094e342b9da5dedadc5a93a`.

Audited **7 registered worktrees, 19 local branches and 19 remote-tracking refs**. Refreshed origin before the audit. Compared each dirty tracked/untracked file against the target; distinguished ancestor commits, patch-equivalent cherry-picks, missing behavior and superseded alternatives. See `inventory.json` for every ref, worktree and dirty file.

## Reconciliation

| Source | Result |
| --- | --- |
| Primary `dvil/settings-preview-carousel` | All calculator WASM work already present; target contains subsequent haptics, arithmetic preview, downloads/archive retention, backup exclusions and delayed Home prompt fixes. Original dirty checkout preserved. |
| `master` and `dvil/irc-history-and-mentions` | Found missing channel-member refresh after self-JOIN, target-specific NAMES refresh, @ mention chips and three regression cases. Merged these from master while preserving the newer theme/cursor/readability changes. Existing history/redaction work already matched. Old build numbers/changelogs were not restored. |
| `backups-frames` | Restored Re:Member frame target and theme extras during app launch, adapted to current localization and launch-fallback code. Its old per-image selector belongs to the superseded frame-library workflow; current frame packs/settings retained. |
| `dvil/orchestration-builder` | Both unique commits are patch-equivalent to changes already in the target. |
| `dvil/ascii-world-wallpaper` | Both unique commits are patch-equivalent (preset behavior consent and decimal status indexes). |
| `dvil/termux-streaming-prototype` | Terminal engine/output work already adapted here; the prototype's older capture-driven interactive connection is superseded by the accepted SSH implementation. Old prototype scripts and historical tests remain recoverable from the branch bundle. |
| `codex/downloadable-features` | Wallpaper data and download capability already included in newer native gallery/store. Older companion APK calculator module, generic `feature` command, browser gallery and their tests are alternatives to the approved WASM/native flows and are not activated. All dirty alternatives preserved unchanged and snapshotted. |
| Frame worktree and all remaining local branches | Ancestors of the target, or patch-equivalent as detailed in the inventory. |
| Historical `origin/Dvil/develop` / `origin/Dvil/github-release` | Java-era preset rewrites, old website iterations and a historical direct-release flavor are superseded by current Kotlin presets, dedicated website repository and current distribution setup. Retained in Git history/bundle, not reapplied over current work. |
| `upstream/*` | Historical upstream fork references inventoried; no upstream fork replacement performed. |

This is a **content consolidation**, not a claim that every historical branch was merged into the target's ancestry. Superseded implementations remain recoverable without being enabled alongside their replacements. No branch or worktree was deleted, reset or switched. Three historical stashes (ASCII wallpaper draft, Lua widget configuration and a settings-save investigation) were inventoried and backed up separately; they were not automatically applied over current implementations.

## Recovery

Verified local recovery directory: `/Users/mako/StudioProjects/TUI-ConsoleLauncher/.git/worktree-audits/20261016-201516`.

- `branches.bundle`: all branch/ref history; `git bundle verify` passed.
- `manifest.json`: original worktree HEADs, statuses and per-file SHA-256 values.
- `<index>-tracked.patch`: binary-capable tracked patches against each dirty worktree's HEAD.
- `stash-<index>-tracked.patch` and any `stash-<index>-untracked.tar`: separate recovery of all three historical stashes.
- `<index>-untracked.tar.gz`: non-ignored untracked files; every archive entry checked against its original SHA-256.

Ignored builds, keystores/local configuration and other ignored files remain on disk. Recovery archives intentionally exclude those files. The other six worktrees and their indexes/branches remain untouched.

## Validation and checkpoint boundary

Validation results are recorded in `validation.json`: **457 total unit tests, 452 passed, five existing opt-in live-network skips, zero failures/errors**. Debug app and instrumentation APKs both built. Localization export check passed (3677 keys). Source whitespace check passed; exact upstream license text and raw evidence whitespace are preserved.

The restored Re:Member role initially exposed missing bundled-frame coverage; the mapping was corrected and the full unit suite passed on rerun. Release lint remained in Kotlin analysis when the combined run exceeded ten minutes and was stopped; it is explicitly **incomplete**, and this checkpoint does not reuse earlier lint results or claim fresh minified release packaging. The checkpoint includes the current source, tests, notices, wallpaper assets and analysis/evidence. Ignored build artifacts, local configuration and private credentials are excluded. The final SSH proof logs are explicitly retained with the checkpoint; older ignored logs remain on disk.

The phone still has the previously validated signed SSH build; this audit does not install the newly reconciled source or claim a new phone test of IRC/Re:Member. No push, publication or release is requested or performed.
