# Production blocker follow-up — 16 October 2026

Completed within the requested 15-minute window. A later follow-up [makes SSH the standard transport](SSH_STANDARD.md); this page records the preceding blocker-fix checkpoint. Work is isolated to `dvil/termux-ssh-analysis`. This follow-up addresses the four failed release gates recorded in [the initial readiness report](PRODUCTION_READINESS.md).

## Changes

- Register the real calculator enhancement panel and calculator-pack management activity as required rendering surfaces. Add `showEnhancement` to the entry-point registry and keep host and packaged instrumentation registries identical. Actual visible panel text, themed actions and enabled/pressed/disabled states are asserted; captures use the existing contrast and drawable checks. No activity exception or test skip was added.
- Guard quiet-mode queries before Android 7/API 24 and quiet-mode requests before Android 9/API 28. Unsupported queries retain the locked fallback; unsupported requests return unavailable before accessing profiles or services. Successful supported requests retain their existing behaviour. Existing Private Space behaviour tests remain; Android-aware release lint verifies SDK guards. No exact-source-string test was retained.

The original checkout remains untouched. No calculator production rendering, terminal transport, production feature flag or release identity changed during this follow-up.

## Validation

- **Full final unit suite:** 438 tests, **433 passed, zero failures, five skipped**. The five skips are opt-in authenticated live IRC tests; no public messages were sent. All three palette registry checks and both existing Private Space behaviour checks pass. [Counts and skipped names](evidence/blockers/full-jvm-results.json), [final unit/debug build log](evidence/blockers/debug-build-and-final-jvm.log).
- **Release lint:** **zero errors**, 600 existing warnings, 311 hints. Both quiet-mode API errors are gone without suppression. [Report](evidence/blockers/release-lint.txt).
- **Release APK and Play AAB:** build successfully with normal SSH-disabled production settings. The default APK mapping contains zero JSch and zero SSH implementation classes. Both artifacts are unsigned; this is build evidence, not a signed upload or publication. [Build log](evidence/blockers/full-gates.log), [paths, sizes and SHA-256](evidence/blockers/default-artifacts.json).
- **Fresh enabled minified candidate:** all **four physical-device checks pass**, including native setup, strict pinned SSH, two independent PTYs and preservation after detach. The installed APK was pulled back and matches SHA-256 `c101fe38a864845cc3d92661a3fe17cfe13bfd28c75443881bf1b8c4766bcf43`. [Artifact and source provenance](evidence/blockers/ssh-artifacts.json), [physical log](evidence/blockers/minified-phone-e2e.log). This binary includes the two AppsManager guards. No new API 23 emulator run was performed; the earlier API 23 crypto/terminal proof belongs to its previously recorded APK. The new SDK guards are verified by release lint, not an old-device runtime test.
- **Original checkout:** all 34 snapshot paths match and its Git index remains untouched. [Preservation proof](evidence/blockers/original-checkout-preservation.json).

- **Physical palette rendering:** the final runner passed in **11.709 seconds**, covering the actual inline calculator enhancement panel and standalone calculator-pack manager on Catppuccin Latte and Mocha. Ten captures contain **353 passing automated samples and zero failing/blocked samples**. Twenty-six disabled-control/platform-popup samples retain their explicit manual-review classification. All four normal screenshots were visually inspected; they show the real UI with readable content. [Counts and manual classifications](evidence/blockers/palette-final/summary.json), [installed build hashes and exact arguments](evidence/blockers/palette-final/provenance.json), [device log](evidence/blockers/palette-final/device.log), [light management screen](evidence/blockers/palette-final/catppuccin-latte/calculator-pack/normal.png), [dark inline panel](evidence/blockers/palette-final/catppuccin-mocha/calculator-enhancement/normal.png).

The first palette pass exposed stale compositor screenshots of the previous app despite correct view-level assertions. That visual proof was invalidated. The test harness now puts a real help window beneath the floating calculator, waits for the focused window’s frame commit and activity transition, and verifies the same window retains focus before capture. It selects the actual focused launcher/dialog root so popup support remains intact. This is test-only; no product rendering changed. Only the corrected `palette-final` captures count as visual evidence.

The debug candidate was re-paired after the minified check; that integration passed. Only temporary release-check packages were uninstalled. [Cleanup proof](evidence/blockers/ssh-test-package-cleanup.json).


## Release boundary

The four failed gates from the initial report are fixed. At this checkpoint SSH was still opt-in. The later [standard-mode follow-up](SSH_STANDARD.md) enables its normal-build default and release rules together and records an isolated in-place upgrade check. Publishing an update under the production package/signing identity remains separate. No commit, push, install over the production launcher, or publication was performed. Overnight battery, other OEM behaviour, all 21 palettes on every surface and authenticated live IRC are outside this 15-minute follow-up.

