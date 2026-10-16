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
