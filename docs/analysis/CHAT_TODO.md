# Re:TUI follow-up todo

Updated from the 15-minute implementation and verification pass on 16 October 2026. The user authorized the backlog implementation, including the preset marketplace. No commit, push, public app release, or change to the phone's active preset/wallpaper was made.

## 1. Termux Apps shortcut wiring

- [x] Include saved Termux Apps in Search mode suggestions.
- [x] Dispatch explicit `tmux launch <id>` actions while retaining friendly labels, avoiding collisions with launcher commands such as `shell` and `notes`.
- [x] Route remaining legacy add/remove operations through the current workspace-launcher registry.
- [x] Migrate legacy definitions once, retaining titles, commands and working directories, keeping newer definitions and preserving the old store. Legacy names matching built-ins receive an explicit `-legacy` name.
- [x] Pass actual Classic/Search suggestion dispatch, command replacement, add/remove and migration checks on the phone. Three migration checks and the suggestion-dispatch check passed; the three directory-quoting unit checks passed.
- [ ] Repeat the complete saved-shortcut → existing running window reuse journey against the final signed candidate. Earlier SSH journey evidence covered reuse; this pass does not replace that end-to-end proof.

Keep this feature: a saved `worknotes` shortcut can reopen a persistent Neovim notes session in the existing split-pane UI. Commands require the relevant Termux programs to be installed.

## 2. Preset-import investigation

- [x] Complete the actual Chrome download → Android Open with chooser → Re:TUI → Import journey for Dracula on the normal signed phone build. The importer saved it and reported active settings unchanged; no Apply action was used.
- [x] Record the real incoming URI, MIME type and permission flags, and capture the chooser, preview and saved screens. This browser path succeeded; the original earlier error was not reproduced.
- [x] Pass rebuilt device checks for browser-wrapper import, stale/repeated intents, and themed error recovery. TokyoNight Storm previously imported through Settings.
- [ ] Repeat the actual browser/chooser path for TokyoNight Storm on the final release candidate, including an error followed by a valid file and loss of file access before opening.
- [x] Resolve the separate Android 16 shared-file permission-revocation regression with an independent recipient app/platform investigation (see the superseding resumed report). During instrumentation, initial ungranted access is denied and granted bytes match, but immediate post-revocation access still succeeds. Outside instrumentation, the same recipient is denied. This is not a confirmed fixed regression or proof of an enduring production leak; production provider logic remains unchanged.

Already repaired: status-bar spacing, launcher theme/font initialization, ZIP/binary MIME compatibility, single-URI ClipData input, stale importer intents, and Choose preset file recovery. Preserve the import-only contract: applying remains a separate user action.

Evidence: [actual browser journey and device results](todo-15min/preset/README.md), [earlier importer repair](preset-import/README.md).

## 3. Release verification on the combined changes

- [x] Run the combined unit suite: **452 total, 447 passed, 5 opt-in live IRC checks skipped, zero failures**.
- [x] Pass five setup fixtures and five SSH dispatch fixture checks.
- [x] Build signed release APK and AAB from the combined source.
- [x] Finish and record release lint results: final release lint has zero errors (614 warnings, 311 hints).
- [ ] Finish the isolated fresh-setup/default-launcher journey rerun with system animations enabled. The first seven-flow emulator run had four passes and three failures; a timing-sensitive setup check is being rerun.
- [ ] Resolve the final-candidate calculator engine and wallpaper live-download emulator timeouts. Network availability versus fixture/test timing remains unresolved; previous successful phone download evidence is retained, not substituted for this rerun.
- [ ] Complete the combined candidate's SSH split-layout/font/keyboard/reconnect/persistent-session checks. Registry and suggestion checks pass, but the latest emulator pane appearance/fade geometry check still failed while waiting for both grids to lay out. Do not treat this as a clean combined UI pass.
- [x] Verify first-time Termux setup with missing OpenSSH/tmux packages on the disposable emulator; actual package installation and pairing passed.
- [x] Exercise wallpaper confirmation → real download → install → preview → explicit system Apply on the disposable emulator; Clouds Apply passed twice.
- [ ] Verify upgrade recovery with a selected optional wallpaper whose pack is absent, retaining its settings and restoring its appearance after download.
- [ ] Run longer SSH/OEM-background and battery checks. Short Vivo/API 23 checks do not establish overnight stability or other manufacturers' behavior.
- [ ] Complete the broad combined palette/readability sweep, including the new marketplace surface.
- [ ] Review and consolidate only this chat's intended changes on `dvil/termux-ssh-analysis`, preserving unrelated dirty work. Commit/push/release only when requested.

Evidence: [combined verification](todo-15min/README.md), [unit results](todo-15min/unit-results.json), [SSH standard](termux-ssh/SSH_STANDARD.md), [earlier production checks](termux-ssh/PRODUCTION_BLOCKERS.md), [first-run/calculator fixes](termux-ssh/FIRST_RUN_FIXES.md), [wallpaper downloads](wallpaper-download/README.md).

## 4. Downloadable-content durability

- [x] Verify all four published Qalculate files match their local bytes: catalogue, engine, matching source archive and checksums.
- [x] Stage only those four publication files in the dedicated website repository. No commit or push was made.
- [ ] Include those staged files in an explicitly authorized website commit before the next deployment. Staging alone does not protect them in repository history.
- [x] Retain wallpaper package verification; optional image artwork and preview GIFs remain outside the APK.
- [ ] Keep confirmation, cancellation, checksums, automatic installation and failed-download recovery in subsequent release checks; resolve the new emulator download timeouts above before claiming this snapshot passes all download journeys.

Qalculate is already published with automatic download enabled. Wallpaper packs are already hosted, and the native gallery is implemented. See [live Qalculate byte verification](todo-15min/qalculate-live.json).

## 5. Native preset marketplace

- [x] Implement the user-approved native marketplace, reachable through `preset -market` and Settings, with 21 curated terminal palettes.
- [x] Provide preview → confirmation → download → validated import → saved-name result and Copy of `preset -apply <saved-name>`.
- [x] Reuse the existing preset inspector/importer and preserve the import-only contract. No automatic application or Apply button is added to the download flow.
- [x] Keep repeat downloads of unchanged imports deduplicated; retain both different packages with the same name. Reject invalid downloads and honour cancellation.
- [x] Pass two URL trust-boundary unit checks and two backend device checks covering catalog validation, checksums, cancellation, deduplication, name collisions and byte-identical active settings.
- [x] Pass the corrected native marketplace phone UI test against the real HTTPS catalog/package: confirmation, download, saved result, Copy command and unchanged active XML files. Remove its temporary imported fixture afterward.
- [ ] Complete the final signed-release marketplace smoke test and broad palette/readability coverage before release.

Evidence: [native marketplace and device results](todo-15min/preset/README.md).

## Decisions and completed work to retain

- [x] SSH is the standard interactive pane transport. Termux/OpenSSH/tmux own shells and persistent jobs; preserve Re:TUI splits/pages and appearance.
- [x] Retain native Android RUN_COMMAND for setup/permission and module/structured-command integration.
- [x] Delay the default-launcher prompt until preparation closes.
- [x] Add calculator key haptics and basic arithmetic previews.
- [x] Publish the optional Qalculate engine and enable automatic download/install.
- [x] Hide the empty window chooser when no selectable windows exist.
- [x] Add in-launcher wallpaper previews, confirmation, download and automatic pack installation; move optional image assets out of the APK.
- [x] Use filled/shaded glyphs for setup progress.
- [x] Repair the themed preset importer with recovery and repeated-intent handling.
- [ ] After sufficient SSH rollout evidence, audit remaining bespoke interactive bridge code for removal. Preserve native integrations required by setup and modules.

The previously installed normal signed build 503 with preset-import fixes had SHA-256 `c77f08e13e8dd16e983a5caca4ec35433192be108fa9e993c03a06395b4a57a1`; that is historical installed-artifact evidence, not the hash of the newly built combined candidate. Final packaging/install hashes and any remaining verification outcomes belong in the combined verification report. No public app release has been made.

## Final checkpoint for the 15-minute run

See [run results and remaining blockers](todo-15min/README.md). Release lint completed with zero errors; APK/AAB and all 452 unit tests completed. Phone calculator downloads passed. Wallpaper all-pack flow passed on an unchanged rerun after an initial timeout. The unpaired legacy pane-appearance fixture and Android 16 grant-revocation regression remain unresolved. Latest installed candidate hash is recorded in the run report.

## Resumed readiness checkpoint

The [resumed report](readiness-resume/README.md) supersedes earlier open-item notes: real missing-package installation, system wallpaper Apply, independent grant/revocation enforcement, Android host-key concurrency/crash recovery, and emulator network/download checks are complete. The paired SSH stress test exposes a real fifth-reconnect reset and remains the production blocker. Full 120-second SSH stability and overnight/device/palette testing are not complete.

## Final SSH reconnect checkpoint

- [x] Fix the confirmed redundant unauthenticated probes and stale daemon PID recovery without disabling OpenSSH security checks.
- [x] Pass the real Vivo default-daemon 120-second loop: 73 font changes, 24 reconnects, both tiles resized, remote processes/draft preserved, and no added control-client leak.
- [x] Build the signed candidate, verify installed APK bytes, and confirm normal launcher SSH authentication/window listing.

See [final evidence and exact validation limits](termux-ssh/evidence/ssh-reconnect-fix-20261016/README.md). This closes the repeated reconnect blocker; the longer OEM/battery run and the other unchecked journeys above remain open. Final suite: 454 total, 449 passed, five opt-in skips, zero failures/errors. No public release was made.

## Widget pane improvement — 17 October 2026

Approved for implementation after the source review. Implemented and checked on the wireless Vivo phone and disposable emulator. Evidence: [widget pane results](widget-pane-20261017/README.md).

- [x] Protect Android widget bindings across Spaces; remove destructive whole-host and guessed-ID cleanup.
- [x] Preserve saved drag positions and anchor the correct edge during resizing.
- [x] Retain host views while editing/resizing, and stop listening while the pane is hidden.
- [x] Refresh installed widget providers when opening the picker.
- [x] Reuse Calc/Help/Settings pane controls and safe-area geometry.
- [x] Keep normal viewing clean, with badges/resize controls in edit mode and a collapsible command strip.
- [x] Add a clear empty-state Add widget action and searchable widget previews.
- [x] Replace tap-to-delete failure cards with Retry and explicit Remove actions.
- [x] Add focused regression checks and validate native widget interactions on the wireless phone, including portrait/landscape, Spaces, resize, recovery, and recreation.

Retain native Android widgets and existing commands. No new widget runtime, marketplace, or dependency is needed.

- [x] Correct the language-resource configuration handoff exposed by real landscape testing; retain installed translations across rotation/font changes.
- [x] Keep provider fonts and sizes intact during launcher typography refreshes.
- [ ] Broader third-party widget/configuration/collection compatibility, palette coverage, and release lint/minification remain follow-up validation.

Final focused validation: 458 unit tests passed, five existing live-network skips; 20 widget device checks and four language regressions passed across the Vivo/API 36 and emulator/API 35. No public release or everyday phone-app update.

### Widget controls, home return, and panel backdrop — 2026-10-17

- [x] Replace the widget arrows and overlaid edit buttons with a thin frame and four subtle corner grips; retain touch targets and accessible resize actions.
- [x] Drag directly in Edit mode; keep normal widget taps and scrolling outside Edit mode. Move Remove into the bottom toolbar.
- [x] Keep empty grid rows at their real cell positions, expose the scroll indicator, and honor provider resize maxima.
- [x] Use the launcher suggestion chip component, padding, typography, and natural height in widget commands.
- [x] Remove repeated resume/focus font traversals, duplicate keyboard activation/theme sends, and forced startup garbage collection. Cache immutable frame bytes and unchanged exported PNGs with invalidation on saved changes.
- [x] Blur the launcher beneath Settings/Help/Calc and embedded panels; preserve Settings panel transparency. Provide dimming when window blur is disabled or unsupported, and clear embedded effects on hide/detach.
- [x] Complete the recorded portrait/landscape acceptance checks, system-blur-disabled fallback check, and before/after framed-keyboard home timings. See `pane-polish-20261017/README.md` for the evidence.
- [ ] Validate reported lag on additional affected users' devices and configurations; a single phone benchmark cannot establish that all home-return stalls are fixed.

### Landscape output edge — 2026-10-17

- [x] Remove the fixed output top gap in landscape, retain configured output/section margins, honor the system top inset, and keep the OUTPUT tab inside the available edge.
- [x] Verify portrait → landscape → portrait restoration and user/system margin composition on API 35. Evidence: `output-landscape-edge-20261017/README.md`.

### Settings always expanded — 2026-10-17

- [x] Remove Settings hub/editor resizing, dragging, maximize/reset controls, and floating-window state handling. Keep the terminal frame, safe viewport, keyboard handling, blur, and drafts.
- [x] Verify six recreation/geometry/draft/blur checks on API 35, plus the unit suite. Evidence: `settings-expanded-20261017/README.md`.
- [x] Install the signed expanded-Settings update in place on the Vivo; verify APK bytes and retained app-data directories.

### Expanded widget pane and live resize — 2026-10-17

- [x] Keep the widget pane expanded within its available viewport; remove outer floating window controls and geometry persistence while retaining individual widget edit controls.
- [x] Preview the actual native widget's size continuously while dragging, respect provider limits/fixed axes, coalesce size hints, and commit the grid size only on release. Restore size/options on cancellation or pane close.
- [x] Verify native preview/cancel/close/snap behavior and expanded geometry with portrait/landscape emulator checks, plus four new pure bounds tests. Evidence: `widgets-expanded-20261017/README.md`.
- [x] Install this combined update on the Vivo in the signed Private Space correction build; APK/data preservation evidence: `private-space-unlock-20261017/validation.json`.
- [ ] Check physical-device resize fluidity.

### Private Space unlock response — 2026-10-17

- [x] Interpret Android's credential-required return value as pending authentication; distinguish real exceptions and security rejection. Read actual profile lock state instead of assuming the request already completed.
- [x] Pass six Private Space unit checks and the complete unit suite, export/check localization assets, build and install in place on the Vivo with app data retained. Evidence: `private-space-unlock-20261017/README.md`.
- [ ] Confirm Android credential prompt, successful unlock and cancellation remaining locked on the phone; user authentication is required for final confirmation.
- [x] Compare official Kvaesitso v1.41.0 on the Vivo: default HOME and hidden-profile permission verified; PIN authentication returned home with the private profile still stopped. Restore Re:TUI HOME, keyboard and rotation defaults.
- [x] Verify the same signed Re:TUI APK on an isolated Android 16 emulator: two PIN-authenticated PRI launches into the private user, cancellation remaining locked, and relocking hiding private apps. Evidence: `private-space-launch-20261017/emulator-api36/README.md`.
- [ ] Resolve Vivo-specific profile startup/app launch compatibility. Standard Android emulator success and the Kvaesitso phone failure do not establish a working Vivo integration.
- [x] Research online reports and inspect the phone framework read-only: Vivo skips standard quiet-mode locking and uses privileged profile lifecycle calls; record native-handoff direction and unresolved direct-launch diagnosis. Evidence: `private-space-launch-20261017/vivo-framework-rnd/README.md`.

### Private command opt-in — 2026-10-17

- [x] Add the default-off Behaviour command toggle, terminal-themed compatibility confirmation, draft/Save boundary, disabled execution message and hidden PRI/command discovery.
- [x] Pass the Settings confirmation/cancel/save/disable emulator test and 473-case unit suite (five existing skips). Evidence: `private-space-command-opt-in-20261017/README.md`.
- [x] Review requested µLauncher and Olauncher source and upstream issues; no verified Vivo workaround found. Evidence: `private-space-launch-20261017/additional-launcher-review.md`.
- [x] Resolve the emulator unlock retry verification: synchronize fixture initialization and Android authentication UI readiness. Three consecutive full flows passed, followed by a visible terminal cancel → unlock → private-user app launch → relock check on the minified release APK. See `private-space-retry-20261017/README.md`; no production workaround was required.
- [x] Refresh normal release APK/AAB and release lint without bypasses: 473 unit cases, 468 passed/five skipped; lint zero errors, 621 warnings/311 hints. Sign and verify local release artifacts.
- [x] Install the latest normal signed build 503 in place on the physical Vivo, verify installed APK hash and retained data/HOME evidence, and leave the phone available for widget testing.
- [ ] User acceptance of the widget pane on the physical Vivo. The phone has the debug candidate; the minified release APK was separately smoke-tested on the emulator.

### Widget activity backdrop — 2026-10-17

- [x] Preserve the actual widget board's configured fill; make the outer activity backing transparent and blur the screen behind it using the Settings window policy. Restore all background/window state on close.
- [x] Pass eight focused physical-phone checks across portrait/landscape, Settings, cleanup, native resize/cancel and keyboard layout. Install the correction in place with fresh signer, APK-byte and data preservation checks. Evidence: `widgets-activity-backdrop-20261017/README.md`.
- [x] Rebuild release APK/AAB and repeat release gates after the widget backdrop correction. Build 503 RC1 has signed artifacts, a fresh 473-case unit run, zero release-lint errors, 22 final phone checks and exact minified widget/Private Space smoke checks. Evidence: `release-candidate-503/README.md`.
- [x] Replace the remaining inset-view blur with the same full-window blur path as Settings, covering visible system bars. Keep the board fill and expanded geometry; verify 20 phone checks across both orientations and install in place. This supersedes the earlier temporary activity-backing approach: `widgets-window-blur-20261017/README.md`.
- [x] Replace the legacy widget-window Back handler with AndroidX dispatch for Android 16 gestures, test real edge gestures, and install the exact minified RC APK on the Vivo with fresh byte/signature/data checks. No public release has been made.
