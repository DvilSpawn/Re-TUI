# First-run and calculator fixes — 2026-10-16

The signed Playstore release candidate, package `com.dvil.tui_renewed`, build 503,
was updated in place on the Vivo phone. Settings and first-run progress were preserved.
Installed APK SHA-256: `8d2a099a94277dd6d65e16db2c06af724e1b9ac6c654e20dfab592e84a34bc71`.

- Setup completes its progress/Ready display, closes its activity, and waits for
  a committed launcher frame before requesting Android's Home role. The pending
  request survives recreation and is consumed once; declining does not repeat it.
- Calculator keys and number/letter/function tabs use Android keyboard-tap haptics,
  respecting the system setting. Physical touches produced recorded 15–23 ms feedback.
- Download Qalculate now uses the published official catalogue by default, retains
  the checksum-verified archive under `Re-T-UI/calculator-engines/`, installs its
  verified bytes privately, and probes the engine automatically. Runtime archives
  are excluded from personal backups. Existing validation/cancellation/rollback remain.
- Numeric arithmetic previews work without the optional engine, supplying the parsed
  expression and result without changing Ans/history. Stateful and advanced input
  remain unavailable in the basic preview; unbalanced parentheses are rejected.

## Verification

- Full unit run: 441 total, 436 passed, five existing live IRC skips, zero failures/errors.
- Five focused physical-phone checks passed: basic arithmetic preview and unchanged
  Ans/history; setup progress/window/launcher ordering and chooser cancellation;
  dry-run recreation isolation; fixture download cancellation/retry/automatic install;
  real public HTTPS download, retained archive hash, installed Qalculate and physical
  keypad input/preview. Instrumentation used a disposable debug package, then removed it.
- Normal minified signed release assembled successfully; its certificate matches the
  existing public release. Installed phone bytes match the APK above. Release startup
  was checked; normal app onboarding was preserved, rather than reset for testing.
- Engine/catalogue/source/checksum publication was explicitly approved. The four
  public files were verified by HTTPS read-back, with existing pages/API also checked.

Evidence: [first-run-fixes](evidence/first-run-fixes/),
[publication](../calculator-download/publication-verified.json).

The four hosted files were also copied, without changing unrelated work, into
`/Users/mako/StudioProjects/Re-TUI-site/docs/calculator-packs/`. They remain uncommitted;
future website deployments must include them to preserve the download URLs.
No app release or source commit/push was performed.
