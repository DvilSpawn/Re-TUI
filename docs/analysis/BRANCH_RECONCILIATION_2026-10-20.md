# Local branch reconciliation — 20 October 2026

## Result

Started from local `master` at `681e7bbc`. Inspected all **12 local branches**, all
registered worktrees, four saved stashes and configured remote-tracking branches.
Fetched `origin` to refresh its branch list; no push or remote branch deletion.

**Seven local branches were pruned. Four branches remain beside `master` because
they contain unresolved changes or own uncommitted work.** They are deliberately
listed below for a keep/port/abandon decision. No unmerged change was silently
marked abandoned, no stash was dropped and no dirty worktree was reset.

Two branch histories contained exact patch equivalents already on master:

| Branch | Already-applied master commits | Reconciliation commit |
| --- | --- | --- |
| `dvil/ascii-world-wallpaper` | `014c0402`, `903222ea` | `d2aa33af` |
| `dvil/orchestration-builder` | `826ee38b`, `a4b4eee1` | `0ed54a2d` |

Both were merged with the `ours` strategy **only after every branch patch was
verified as already applied**. Each merge retained exactly the preceding master
tree. This records ancestry without replacing newer code with the old copies.
The application implementation remains the tested `681e7bbc` implementation.

## Pruned local branches

- `codex/live-settings-preview-current`
- `dvil/ascii-world-wallpaper`
- `dvil/irc-history-and-mentions`
- `dvil/orchestration-builder`
- `dvil/real-launcher-preview`
- `dvil/scalable-frames-widget-resizing-companion-sharing`
- `dvil/termux-ssh-analysis`

All seven tips are now ancestors of master. Clean worktrees attached to these
branches were detached at their existing commits before branch deletion; their
files, ignored build outputs and folders remain in place. One stale worktree
registration for the already-missing `/private/tmp/retui-irc-fix.qZiiwC` was pruned.

## Retained branches and work needing a decision

| Branch / checkout | What is already implemented | What remains outside master | Suggested disposition |
| --- | --- | --- | --- |
| `codex/terminal-mode` | The normal SSH pane and Classic/Search modes. | New `mode terminal`: persistent scrolling SSH tile dashboard, bottom Search with +/settings, blurred Search overlay, widget scripts, more than two SSH viewers, helper 0.4.8 and associated tests. Master has no `TerminalHomeView` or Terminal mode enum value. | Keep if Terminal Home is wanted; port onto the current scene/layout code and validate before merging. |
| `dvil/settings-preview-carousel` | Core live settings preview and shared current rendering, committed in `681e7bbc`. The optional WebAssembly calculator engine is also already on master. | The earlier alternative renderer architecture, including direct draft-aware clock/RAM/storage/network/notes/unlock collectors and a standalone clock. Those can preview non-battery behavior-format edits; the current cards use captured launcher output for those rows. Old tests/docs and earlier calculator copies are also present. | Do not replace current preview/rendering with the old implementation. Decide whether to port only the additional draft-format preview behavior; abandon duplicate infrastructure only after that decision. |
| `codex/downloadable-features` | Downloadable wallpaper packs, renderer assets, the wallpaper catalogue, and the newer portable Qalculate engine pack. | Older native companion APK (`:qalculate-runtime` plus `QalculateProtocol`), `feature -list` / `feature -get qalculate`, older hosted-WebView gallery, extra marketplace device tests and prototype documentation. | Recommend abandoning the old companion/gallery implementation in favor of current implementations. Retain or port any wanted tests and generic feature-discovery command separately. |
| `backups-frames` (`294fb864`) | Re:Member frame target, app-launch theme extras and their current test assertion. | An inline expand/collapse frame selector. The old code depends on the former asset-list API and does not match the current frame-pack/per-element UI. | Decide whether to reintroduce an inline selector; the Re:Member parts need no duplicate merge. |

The first three have **uncommitted** changes even though their committed branch
tips are already ancestors of master. A plain Git merge would miss those changes.
Their inventories contain 28, 66 and 126 changed/untracked files respectively
(Terminal Home, old settings, downloadable features). Many files are older copies,
not new features: 19 old-settings files and 87 downloadable-feature files already
match current master exactly, including deletions already made on master.

The old calculator copies would remove current functionality such as basic
arithmetic previews, archive retention, haptic feedback and backup exclusions.
The older wallpaper installer would remove cancellation, recovery and newer
validation. They were not copied over the newer implementations.

### Terminal Home validation limit

Its existing `docs/terminal-mode.md` reports earlier focused emulator/phone checks,
but the final measured p95 comparison missed its 20% target and a new 15-minute
SSH Home soak was not completed. Those are historical results from that checkout,
not fresh validation against current master. No physical phone was used in this
reconciliation.

## Saved stashes: preserved, not applied or dropped

| Saved change | Commit | Finding / decision |
| --- | --- | --- |
| Terminal Home before reconciliation | `e6c001c6` | Older socket/MULTI dashboard implementation, including `TerminalSearchBackdrop` and socket protocol changes. The current Terminal Home worktree moved to the shared SSH implementation. Recommend keeping the newer SSH variant and retiring this older transport only after confirmation. |
| ASCII World / Fortress wallpaper | `38d110d9` | **Not implemented on master.** Procedural ASCII terrain/fortress renderer, progress persistence, tilesets and palette/import controls. `AsciiWorldView.kt`, `AsciiFortress.kt` and their tileset files are absent from master. Keep/port or abandon explicitly. The deleted branch's committed preset fixes were separate from this stash and are already merged. |
| Lua widget configuration | `0bb418d1` | Older Java rollout. Current Kotlin `WidgetConfigActivity`, `LuaWidgetEngine`, widget command and configuration fields already provide the feature, including `show_config`, `on_config_submit` and multiline fields. No product gap identified in this source review; old Java files/docs remain saved. Recommend retiring the duplicate implementation. |
| Settings save investigation | `cc7fa680` | Old parent-dispatch/cache-update fix and versionCode 323 bump. Current typed settings save path and Apps/RSS/Notifications/Reply writers already update their caches; the version bump is obsolete. Recommend retiring this duplicate investigation. |

Stash ordinals may change in future; the commit IDs above identify the exact
saved changes. All four remain in the original stash list.

## Remote-only historical work

Remote branches were **not deleted**. Besides ancestor branches and the retained
frame backup, the origin contains these historical differences:

- `origin/Dvil/develop`: eight commits outside master's ancestry, six exact patch
  equivalents already applied. Two older commits (`6d6888e8`, `9ba0f0a2`) expand
  and then simplify shareable snapshots, including automatic wallpaper-image
  capture/application in saved presets. Current master has newer curated,
  PI-safe UI/behavior/frame sharing, but does not implement that same automatic
  wallpaper-image payload. Decide whether that capability is wanted; do not
  replace current sharing wholesale with these older Java implementations.
- `origin/Dvil/github-release`: old separate `.github` package/build flavor,
  package-specific callback routing/diagnostics and an old website direct-APK
  link (`30f7f2ed`, `27ccad8d`, plus merge commits). Current master has Play Store
  and F-Droid flavors rather than this separate application ID. Decide whether
  an additional application identity is still wanted.
- `upstream/dev`: 29 upstream-only commits with an old RxJava/manager rewrite,
  including removal of changelog/messages and replacement of app managers.
  This is the original upstream development experiment, not a Re:TUI feature
  branch. It was not merged over the current Kotlin implementation.

`origin/master` was refreshed and has no commits missing from local master.
`upstream/master`, `origin/Dvil/playstore`, `origin/Dvil/web`, and the old origin
IRC/frame release branches are already ancestors. No new remote local branch
was created just for this reconciliation.

## Preservation and verification

Recovery artifacts are in
`/Users/mako/.codex/worktrees/termux-streaming-prototype/TUI-ConsoleLauncher/build/branch-reconciliation/2026-10-20/`:

- `refs-before.bundle`: pre-cleanup refs and reachable history.
- `stashes-before.bundle`: also includes every older stash commit and untracked
  tree. All four stash commits and their third-parent trees were recovered and
  verified in a temporary empty repository.
- `old-settings.patch`, `downloadable-features.patch`, `terminal-home.patch`:
  binary patches against each recorded base.
- Matching `*-untracked.tar.gz` archives: non-ignored untracked files.
- `dirty-worktree-inventory.json`: exact paths, original branch/base and SHA-256.
- `stash-inventory.json`, `pruned-local-branches.json`, `refs-before.txt`,
  `worktrees-before.txt`: detailed audit and recovery identities.

All original dirty file hashes and file sets are checked again after cleanup.
No application code changed in this reconciliation; the merge tree IDs were
identical before/after both history merges. Only this audit report is added to
the application tree. Builds/device tests were not repeated for a history-only
merge. The prior preview validation remains 9 focused emulator tests and 49
focused unit tests; that does not validate the retained experiments.

Nothing was pushed, published or released. Unresolved branches and saved stashes
remain available for the user's next keep/port/abandon decision.
