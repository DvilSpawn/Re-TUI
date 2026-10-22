# Settings changes and upgrade compatibility

Build requirements are listed in the root README. These checks use Gradle,
Python and Android SDK tools; no online account or assistant is required.

## Save and reload contract

- Validate **new edits** with `SettingsValidation`. Preserve unchanged existing
  values, unknown XML elements, explicit colors and supported legacy aliases.
  Invalid existing values appear in `XMLPrefsManager.validationWarnings`.
- Use `LauncherSettings.applyChanges` for related edits. A successful transaction
  publishes one immutable snapshot and one revision. Do not write live XML and
  then try to repair the cache, or request a second refresh from a caller.
- Settings and backup restores share the transaction lock. Stage the entire
  batch, including frame assets for presets, before replacing live files.
  Journal recovery precedes settings loading. Failures retain the old snapshot
  and retryable editor draft. This is transaction recovery, not an upgrade backup.
- Configuration loading parses all files before publishing any of them. A failed
  reload keeps the previous values. Startup errors preserve files and show Retry.
- The refresh coordinator decides which effects are required. Colors, typography,
  border styling and wallpaper visibility restyle existing Home views. Unknown
  layout/behavior effects reconstruct Home; explicit `restart` restarts it.
- Restyling must not replace controllers, actions, views, SSH connections or Lua
  sessions. Preserve input, selection, focus, keyboard state and scroll offsets.
  Detached settings previews reuse presentation functions; never copy a passive
  preview back into the live hierarchy.

## Required regression coverage

Changes to names, defaults, aliases, inheritance, imports or migrations must
include a provenance-labelled fixture or a regression test. Keep released fixture
bytes unchanged. Test load -> migration -> unrelated edit -> reload -> restart.
Current names take precedence over their legacy aliases; conflicting singleton
values are errors. Never replace an existing value just because a newer editor
would reject it. Manual backups remain available; upgrades do not force backups.

Run `python3 scripts/verify_settings.py host` for local checks and
`python3 scripts/verify_settings.py inventory` to list all device test sources,
package constraints and explicit gates. Device runs require an explicit serial:

```
python3 scripts/verify_settings.py device --serial YOUR_SERIAL
python3 scripts/verify_settings.py device --serial YOUR_EMULATOR --emulator
```

Device tests change launcher data in isolated test packages. Legacy tests that
require the base package run only on disposable emulators. They may change the
wallpaper and orientation. They never authorize deleting unrelated application
or phone data. Real IRC/account operations are excluded unless separately run
with explicit credentials. Missing infrastructure is blocked coverage, not a pass.

Unit tests run on the host. API/device-specific cases run on matching targets.
The runner groups device tests by required package and records all results and
skips under `build/validation/settings-reload`. Run release instrumentation
separately using the properties and prerequisites recorded in the inventory.
Every release needs unit, device, supported legacy-fixture and connected-session
results; a green subset must not be labelled a complete pass.

## Full coverage versus a focused check

A full device run returns exit code 1 for failed tests and 2 for incomplete coverage
(blocked cases or skips). Focused class runs report only the selected classes.
Protected-account IRC checks require separate authorization and credentials; they
must not be enabled just to make a coverage counter green.

Optional test setup arguments:

```
python3 scripts/verify_settings.py device --serial YOUR_SERIAL --engine-pack /path/to/qalculate.retui.calc.engine --enable-termux
python3 scripts/verify_settings.py device --serial YOUR_SERIAL --package-suffix palettedebug --classes ohi.andre.consolelauncher.wallpaper.WallpaperAutoReloadDeviceTest --argument wallpaperAutoReload=true
```

The second command clears its isolated package and changes the system wallpaper.
APK builds and device runs are serialized per checkout/device by the driver.
Release instrumentation needs matching signatures. If separate L8 runtimes in
application/test APKs conflict, use `scripts/prepare_termux_ssh_release_tests.py`;
its report verifies that the application and original test APK are unchanged.
The staged SSH process-death driver is `scripts/test_termux_ssh_process_death.py`;
always pass an explicit serial and an output directory under `build/`.

## Separate runners and prerequisites

| Coverage | Build or runner | Required setup |
| --- | --- | --- |
| Palette matrix | `python3 scripts/terminal_colors/run.py --help` | Isolated `.palettedebug` APKs, explicit device, palettes and surfaces; stress configurations and embedded Termux fixtures require an emulator. Review screenshots and role ownership; execution alone is not a visual pass. |
| Settings release | `-PsettingsPreviewReleaseCheck=true` | Build release app and instrumentation together; sign both with the same certificate. Runner: `ohi.andre.consolelauncher.managers.settings.SettingsPreviewReleaseInstrumentation`. |
| SSH release | `-PtermuxSshReleaseCheck=true` | Matching signed release/test APKs, real Termux with SSH/tmux, run-command permission and helper setup. Runner: `ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSshReleaseInstrumentation`. |
| IRC smoke | `-PircReleaseSmokeRunner=true` | Explicit credentials and authorization for real server/account/message operations; never part of the unattended safe run. |
| Language restart | `LanguagePackRestartTest`, `-e packStage install`, then `verify` | Keep package data between invocations; force-stop between phases. |
| SSH process death | `scripts/test_termux_ssh_process_death.py --help` | Paired isolated `.termuxsshdebug`, Termux external commands enabled; retain the seed fixture through the kill and verification. |
| In-place bridge upgrade | `TermuxWorkspaceProcessDeathTest`, `termuxUpgradePhase` | Seed with `-PtermuxPaneBaseline=true`; install the SSH build with the same package/certificate without clearing data; execute verification. |
| Optional calculator engine | `--engine-pack PATH` | A verified `.retui.calc.engine` pack; ordinary application builds and basic calculator tests do not require it. |
| Standalone share sender | `PortablePresetIntegrationTest` | Independently installed sender helper for the external URI-grant test. |
| Host PTY tests | Python unittest scripts | Real tmux, SSH binaries and working PTY support; process cleanup timeouts are failures, never silent skips. |

The inventory includes source paths, package constraints, opt-in argument lines,
staged cases and release build arguments. Raw XML saves use the same refresh
coordinator as typed saves. The established `config -erase` operation stores an
empty override intentionally; boolean/numeric presentation resolves it using the
same defaults as saved settings.
