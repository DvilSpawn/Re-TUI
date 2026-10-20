# Settings compatibility fix and release plan

Prepared 2026-10-20. Baseline: Hotfix 3 / Build 506 / 0c79802c.
Delivery: Hotfix 4, Build 507.
Status: implemented; signed APK and AAB built. Full unit suite, release lint and the focused minified emulator journey passed. Publication is separate from this build task.

## Scope

Preserve existing configuration across startup and preset import/export/application. Address issue 27, legacy status-list and margin migrations, duplicate aliases and valid black colors. Keep the existing preset privacy allowlists, Save/Cancel behavior and atomic XML writer. No new dependencies, permissions, general migration framework or new settings UI.

Inline text-color parser work is a separate change after this compatibility release. Do not fold it into this hotfix.

## 1. Define and implement clock compatibility

- Replace the provisional time_format -> status_time_format rename with explicit migration of the old shared list.
- When a new status/output setting is absent, copy the ENTIRE legacy value to that missing setting. Both historically used the same indexed list; do not split the string into one entry per consumer.
- Preserve the configured separator, index ordering, literal text, whitespace, newlines, colors and size markup without reparsing or rewriting the format.
- Never overwrite an existing status_time_format or output_time_format, including a value equal to a default.
- When old and new values coexist and differ, preserve the old value for recovery. Earlier versions inserted defaults, so the code cannot reliably infer user intent from equality to a default.
- Preserve the original XML before destructive migration using a dedicated local recovery copy that subsequent starts do not overwrite. Fail safely if that copy cannot be written. Retain existing AtomicFile handling for the updated XML.
- Already-deleted values cannot be recreated; document how to copy a format from an existing backup into the current setting.

## 2. Use one normalization path

Extend the existing DOM migration routine rather than creating another parser. Put the old margin rename and status-list expansion behind the same internal entry point.

Call it on the parsed document before startup reading, preset sanitization and saved-preset application. Preset paths operate on an in-memory copy: importing or inspecting a preset must not modify active settings, saved preset source files or recovery copies.

Keep privacy validation AFTER normalization. A renamed freeform field must not bypass the existing explicit-selection requirement for export/import. Preserve imported drafts until Save/Apply.

## 3. Make conflict handling consistent

- Current name plus its old alias: current value wins for active use; preserve the old value in the recovery copy for active-file migration.
- Exact duplicate current keys with conflicting values: fail with a useful message before applying anything.
- Validate all preset files before writing active files, including UI and Behavior, so a conflict does not leave Theme applied and the rest rejected.
- Repeated normalization is idempotent and does not change already-current configurations.
- Reuse existing validation and recovery mechanisms; broaden apply changes only as required to prevent partial writes caused by these migrations.

## 4. Preserve valid opaque black

Remove the exact-string invalidation of #ff000000 from the 28 background/shadow options. Keep actual color syntax validation. Confirm #ff000000, #FF000000 and #000000 remain valid opaque black; #00000000 remains transparent. Display contrast adjustments must not rewrite stored values.

## 5. Required verification

### Focused fixtures

Run representative old XML through startup normalization, preset import, export and application:

- Legacy clock only, current keys only, one current clock key missing, both legacy/current keys, default-looking current values, multiple indexed formats and custom separators.
- Status background/shadow/alignment lists: full, short, empty entries and existing per-field overrides.
- Old display margin and current margin combinations.
- All existing one-to-one rename mappings, not just the clock example.
- Old/new aliases together, conflicting duplicate current keys, and invalid preset input. Confirm no partial active writes.
- All 28 black-sensitive fields, opaque-black spellings and transparent colors.
- Second migration pass produces no changes; failure preserves the original file and recovery copy.
- Freeform Behavior fields remain excluded unless explicitly selected.

### Device and release checks

- Use an emulator with a backed-up legacy configuration to exercise upgrade, reopening Settings, restart, preset import/export/apply, and Save/Cancel.
- Compare values before/after and capture clock/layout screenshots; starting successfully is insufficient.
- Verify first install and upgrade display the appended changelog.
- Run the complete unit suite, release lint, signed APK and AAB builds.
- Repeat key legacy-clock, preset and black-color journeys on the minified APK. Verify signing identity and version codes.
- Physical phone smoke check if available, without replacing the user's settings with fixtures.

## 6. Release preparation and gate

- Candidate name: Re:TUI V.3 — Hotfix 4 (Build 507).
- Bump versionCode only during implementation/release preparation; versionName remains 3.
- Append notes to both the bundled changelog and GitHub release history; regenerate the language catalog/template.
- Commit tested fixes to local master without altering unrelated worktrees.
- Produce signed GitHub APK, matching source archive/checksums and a separate signed Play Store AAB.
- Publish only after validation passes and publication is authorized. Keep issue 27 open until the migration checks pass; do not claim recovery of already-deleted data.
- Download published GitHub assets and verify hashes. Supply the AAB for Play Console upload and explicitly distinguish bundle preparation from submission.

## Validation result and delivery boundary

- Full unit suite: 487 tests, 482 passed, 5 skipped, zero failures/errors.
- Release lint: zero errors; 637 warnings and 310 hints remain.
- Signed, minified Build 507 passed the Android 16 emulator journey: legacy clock values reach both consumers, original recovery bytes remain intact, legacy margins/status lists migrate, opaque black survives, saved presets apply, and conflicting duplicates leave active XML unchanged.
- Bundled changelog opens with Build 507 and retains previous release history. APK and AAB signatures verify.
- Evidence and candidate artifacts: `.local-context/releases/build507-hotfix4/`.
- Physical-phone validation was not performed. First-install/upgrade automatic changelog triggers and the broader Save/Cancel/recreation matrix were not separately exercised in this run.
- GitHub publication, issue closure and Play Console upload remain pending. This task prepares local candidates only.
- Previously deleted values require an existing backup; this update cannot recreate them.
