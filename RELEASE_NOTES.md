# Unreleased — combined local checkpoint

**Draft, not a published release.** No new release number or date is assigned.
The app still declares version name `2`, version code `426`. Building or locally
committing this work does not publish an APK, deploy the backend, or close release
gates. Keep the customer-facing sections below when preparing the next release;
resolve the maintainer checklist before making release claims.

## Added

### Detached Termux workspace

- Open a dedicated workspace from the toolbar, `tmux`, or `termux`, separate from
  launcher Home. Termux/tmux continues to own the shells and running jobs.
- Choose Single, Top-bottom, or Left-right layouts, with at most two visible
  tiles. Each tile targets its own tmux window and has independent capture,
  resize and input; focus determines the keyboard target.
- Keep multiple pages with their own window pair, layout and focus. The separate
  page strip/arrows and `:page next` / `:page prev` change pages; terminal swipes
  and `:prev` / `:next` change only the focused tile's window.
- Add asks where to place a shell: replace a tile without killing its old shell,
  split a single page, or open a new page. Cancelling creates nothing. Choosing
  Single expands the focused tile without terminating the other shell.
- Reconcile exited shells: remove dead windows from remembered pages, collapse
  surviving splits and close empty pages. The last exit leaves an empty workspace;
  reconnect/resume does not silently create a replacement. Add explicitly starts
  a new shell.

### Floating calculator and help

- Calculator and offline help start as floating windows. Drag the title to move,
  or any of four corner targets to resize within the safe, fold-aware host.
- Maximize/float retains the floating rectangle; Reset returns to default bounds.
  Calculator and help retain independent geometry across Activity recreation.
- Compact raised headers use right-aligned actions with 10dp gaps and 48dp touch
  targets; titles yield rather than overlapping controls or resize corners.
- Help renders headings, bullets, numbered items, code and warnings with distinct
  styling, while retaining terminal-style navigation, selectable text and a
  separate prompt. Expanded packaged articles cover launcher workflows, commands,
  themes, presets, calculator, Termux and community limitations offline.

### Optional idle fade

- `breath_out` fades launcher Home and the detached workspace to the wallpaper
  after inactivity. It is off by default; `breath_out_seconds` defaults to 900.
- The first touch/key/Back wakes the surface without performing its underlying
  action. IME editing restarts the timeout; pause/focus changes stop the active
  timer. Touch exploration prevents the idle fade. Calculator, help and community
  are not additional fade targets.
- The two settings are included in shareable presets and the settings UI.

### Experimental community

- `community` opens an opt-in general-room Activity. It does not connect on
  launcher startup. Both flavors use username/password registration and sign-in,
  with passwords entered only in masked fields, not terminal commands/history.
- Send literal text, load older messages, explicitly return to latest, track
  unread arrivals, block/unblock usernames locally and report a message by its
  full ID. Received messages never enter the launcher or shell dispatcher.
- Keep a bounded 200-body cache and up to 50 body-free arrival observations.
  Poll only while visible, cancel obsolete operations, avoid overlapping requests
  and back off on failures/rate limits. Older-page polling does not force a scroll.
- Store session/cache/draft state encrypted with AndroidKeyStore AES-GCM outside
  Android backup and launcher exports. Passwords are not persisted. Logout and
  account changes clear private account state; leaving a draft offers Keep,
  Discard or Cancel. Restoring a draft does not automatically send it.
- Persist the pending UUID and immutable sent body separately from the editable
  draft. Reconcile ambiguous responses and lifecycle cancellation before retry;
  duplicate rejection alone is not delivery confirmation. Completing an older
  attempt preserves a newer draft. Reports show success only on server acceptance.

## Changed

- The Termux helper is now **0.4.1**, using the targeted v4 socket protocol. The
  detached Activity requires the matching helper: updating the APK alone does not
  upgrade Termux. Update both sides; sessions/jobs are retained. There is no silent
  fallback to session-global capture/input. See [bridge instructions](termux/retui/README.md).
- The helper authenticates readiness with non-resizing `PROBE`; `NO_CREATE` and
  target-gone control events distinguish exited windows from terminal content.
- Workspace, calculator and help refresh launcher colors/font settings on return.
  Live terminal size changes recompute effective columns/rows after settled layout,
  reuse existing sockets and avoid duplicate geometry updates.
- Calculator help opens the separate offline reader rather than replacing the
  calculator session. Draft, selection, answer and transcript remain isolated
  from help navigation and pending calculation responses.
- Calculator/keypad/help chrome shares terminal-pane styling; keypad measurement
  preserves the live prompt and layer height through layout/orientation changes.
- English runtime catalog, source template, bundled template ZIP, localization
  inventories and retention/privacy text are synchronized. Material Symbols icon
  attribution and Apache-2.0 license text are included.
- Maintainer builds can opt into `-PfetchDebug=true` for the separate Play Store
  debug application ID; normal production-flavor identifiers remain unchanged.

## Fixed

- Supplementary Unicode backspace and non-ASCII workspace input no longer depend
  on single UTF-16 characters; IME control keys route to key dispatch instead of
  literal text. Modifier-aware input stays distinct from plain Unicode typing.
- Deferred workspace resize work is cancelled/reset on detach and pause, retried
  on attach/resume, and retried after an accepted new-window frame clears creation
  in flight. Stale transport callbacks do not revive obsolete work or replay
  consumed launch commands after recreation/reentry.
- In-place launcher appearance refresh preserves terminal draft, selection,
  history and scroll. Theme-owned spans are recolored by role without overwriting
  explicit inline/message/ANSI colors; clock, HTML/weather and package-event
  output resolve the current palette rather than old constructor values.
- Unlock tracking observes screen/keyguard transitions instead of launcher resume
  or a time-based debounce. The protected-system receiver remains registered
  while foreground rendering is paused and is disposed on manager replacement;
  overdue cycles reset before counting/persisting a new unlock.
- Offline help and preset import use lifecycle-aware Back dispatch. Parameter
  commands with a declared default action work with empty input, including the
  new community entry point.
- Community cached history and arrival metadata expire at server creation plus
  24 hours, even while a foreground request is busy/backed off. Restored, merged
  and serialized state reject expired, invalid and future-dated records; `updated`
  cannot extend history. Expiry preserves uncertain delivery identity/body and
  editable drafts. Moderation rechecks bounded cached IDs.

## Privacy and backend scope

- Community is server-readable, **not end-to-end encrypted**. No email, Google/
  OAuth, push, analytics or CAPTCHA service is introduced. There is no password
  recovery or automatic ownership transfer to a replacement username.
- Backend source includes pinned PocketBase 0.40.4 migrations/hooks, isolated
  service/ingress configuration and local contract/upgrade/quota tests. General
  messages expire individually at age >=24 hours; read rules and pre-query/view
  guards hide them immediately, with physical purge normally every minute.
- Accepted reports/private moderator requests retain private evidence for **six
  calendar months** in UTC with end-of-month clamping, not 180 days. Snapshots
  survive source-message/account deletion. Persistent `bugs`, `features` and
  `presets` threads/replies are backend-only in this checkpoint: Android channel,
  thread, `/moderate` and moderator-queue UI are deferred, not shipped features.
- Scheduled whole-database archives are disabled in the versioned migration;
  unresolved legacy archives and protected rollback copies remain policy
  exceptions. No offsite recovery is configured. This is not forensic erasure
  of WAL/free pages, memory, filesystem or provider snapshots, nor proof that
  production retention deployment is complete.
- Local expiry uses the device wall clock. A paused/unopened app prunes encrypted
  history on next access/save, not at an exact background deadline. Drafts and
  uncertain-delivery bodies are separate user-controlled state.
- See [community client notes](docs/community.md), [backend contract](server/community/README.md)
  and [privacy policy](docs/privacy.html) for operational limits.

## Earlier local work to carry into the next release

These items already exist in history after the last entry in this file (Build
419, `4087515`), not newly introduced by the combined diff from `2fb7b4a`:

- Font import/navigation, seeded Topo Noise, Pixel Dream, eight Clouds scenes and
  Nagomi koi wallpapers; live wallpaper/Duo layout and interaction refinements.
- Semantic theme controls, independent app-drawer rails and wallpaper color
  sliders, theme inheritance/auto-color fallback and clearer Termux ANSI palette
  descriptions; unused palette settings hidden.
- Offline launcher usability, input focus, Home routing, settings transparency,
  module navigation/control fixes, search modes and tray interactions.
- Standalone/terminal Qalculate calculator with keypad layers, unit-aware `ans`,
  cancellation/watchdog and independently authored native bridge (Builds 425/426).
- Portable `.retui` configuration export/share/import with bounded private
  inspection and confirmation. Imports save uniquely named presets for later
  `preset -apply`, never silently apply settings or restore personal backups;
  personal `.retui-backup` files remain distinct. See [preset contract](docs/PORTABLE_PRESETS.md).
- Packaged offline help, backup/Notes/localization/documentation and regression
  updates from `8d10c27` and `2fb7b4a`.

History anchors: `2237ddc`, `ce08c7a`, `6d2fe7d`, `c5764b0`, `4c52b5b`, `b900e25`,
`c12d9a6`, `acfe946`, `f350e1d`, `d889e6f`, `3920fb5`, `e824d3a`, `91bcdfe`,
`ce66625`, `3110fc1`, `9c941de`, `8d10c27`, `2fb7b4a`. This inventory is not a
claim that those local build numbers were publicly released. Confirm the actual
published baseline before selecting final release copy.

## Maintainer verification and release gates

### Local checkpoint results

- Full unfiltered `testFdroidDebugUnitTest` and `testPlaystoreDebugUnitTest`
  suites: **346 tests per flavor**, zero failures/errors/skips. Shared
  `retui-contract:testDebugUnitTest`: **1 test passed**; datetime-picker's debug
  unit-test task has no sources. The two app counts are flavor executions of
  the same suite, not 692 distinct test cases.
- Both normal flavors' debug app, debug AndroidTest and optimized release APKs
  built successfully. Source-input validation passed. All six APK signatures
  were verified; all four app APKs contain the current localization catalog and
  expected four-ABI native libraries.
- Native libraries were reused from the previously validated JNI cache, **not
  freshly rebuilt**. Merged libraries matched that cache byte-for-byte; packaged
  libraries matched each variant's stripped outputs. Scratch Gradle configuration
  relocated module outputs/project cache and disabled only native rebuilding;
  no app or instrumentation source filtering was used.
- Debug/release lint has zero errors/fatal issues. Warnings remain: F-Droid debug
  **572**, Play Store debug **571**, F-Droid release **571**, Play Store release
  **570**. This is not a warning-clean claim.
- Python discovery: **28 tests passed**, including real isolated tmux/helper
  transport/tile fixtures. The separate production-body Kotlin/TCP harness passed
  geometry/lifecycle, creation-completion and shell-exit checks with Android/view
  spies; these are not rendered device tests. Helper/install shell syntax passed.
- Localization `export --check` passed with **3450 translatable keys**; the bundled
  template ZIP passed CRC and duplicate-entry checks. `git diff --check` passed.
- Real isolated PocketBase backend: **14 tests passed**, including a 72-request
  expansion-rejection matrix. The preexisting-data migration fixture passed its
  **1 test**. Exact backend cleanup verified no remaining fixture rows.
- The nginx ingress/quota fixture could **not run successfully**: nginx is not
  installed/available on this host. Its prerequisite assertion failed, not an
  observed quota regression. Public HTTPS tests were intentionally not run.
  Re-run the isolated nginx test on a provisioned host before release.
- Added-line secret/path screening and targeted source review found no credential
  or generated-data payload to include; fixture-token matches were test data,
  and `exec` matches were command interfaces/regex calls, not dynamic evaluation.
  This checkpoint did not obtain a fresh independent reviewer verdict. No stash,
  reset, user Git identity/configuration changes, device work, deployment, push,
  tag or release was performed.

No device installation/instrumentation or production/public-network mutations
are part of this checkpoint. Compiled instrumentation and host spies do not prove
rendered Android behavior. Earlier focused device passes do not clear every gate
for the final combined release candidate.

### Still required before publication

- Resolve the combined acceptance checklist in [TESTBOOK](docs/TESTBOOK.md),
  including rendered font/IME/orientation/lifecycle behavior, shell/job
  preservation, idle fade, unlock/status and calculator/help gestures.
- Keep physical-keyboard and prolonged reconnect/stress coverage explicitly open
  until exercised; distinguish injected-key/IME fixtures from real hardware.
- Recheck final release flavor, package/version/signature/native libraries and
  installed artifact identity. Preserve complete user configuration/preset sets
  and bytes; do not clear data/uninstall to bypass signing or fixture setup.
- Validate live two-account community behavior, deployed registration/TLS/rules/
  rate limits and retention jobs under separate authorization with exact fixture
  cleanup. Local backend tests do not establish production availability/compliance.
- Assign moderation ownership and monitoring; resolve legacy-copy exceptions and
  policy-compatible disaster recovery. Keep Android v2 UI work deferred explicitly.
- Complete native source/license delivery obligations in
  [third-party notices](THIRD_PARTY_NOTICES.md) before publication. This checkpoint
  is neither warning-clean certification nor full release acceptance.

---

# Re:TUI V.2 - Build 419

Build 419 is a reliability update for Android 16, notifications, media sessions,
outlined text rendering, and the preset marketplace.

## Fixes

- Back and edge-swipe gestures on Android 16 now close Launcher surfaces without
  restarting the default Home activity.
- Hardened the persistent notification service against Android restarting it
  without an Intent.
- Hardened media-session changes when playback state or controller entries
  disappear during a callback.
- Limited outlined text rendering to 1–8 passes, including hand-edited settings,
  to prevent unbounded redraw work.

## Preset marketplace

- Run `preset -market` to open `https://re-tui.pages.dev/marketplace`.
- `-market` appears with the other options when typing `preset`.

## Downloads

- Install the APK to update Launcher.
- Translators can use the refreshed English template and example language pack.
- The Play Store bundle is distributed through Google Play, not this GitHub release.

Version 2, version code 419.
