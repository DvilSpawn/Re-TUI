# Portable preset sharing

## Scope

Shared launcher configurations use `.retui`. Personal backups keep `.retui-backup`.
Opening or sharing a preset to Re:T-UI starts an inspection and confirmation flow;
importing only adds it to the saved presets available through `preset -apply`.
It never applies a preset, changes active settings, or restores personal data.
The existing apply flow and save-current-preset prompt remain unchanged.
Rollback, remote hosting, App Links, dependency replacement, new configuration
schemas, and generated graphical previews are outside this change.

## File contract

- MIME type: `application/vnd.retui+zip`.
- Container: the existing ZIP archive containing `manifest.txt` and the existing
  sanitized configuration XML. No duplicate JSON representation or format migration.
- Export preserves the existing shareable schema and selected behavior fields.
- Named preset exports use a sanitized preset name with the `.retui` extension.
  Current-configuration exports use a timestamped name.
- Older supported shareable packages remain readable, including shared configs
  previously exported with `.retui-backup`. Contents, not filename, determine type.
- Personal backups are rejected by the public preset importer regardless of extension.
- No new assets, application dependencies, or scripts are bundled by this change.

## Export and sharing

Keep the existing save-document export, using the new MIME type and extension.
After successful export, offer sharing through Android's share sheet using a
private cached copy exposed by the existing FileProvider, a `content://` URI,
and temporary read permission. The provider reports the custom MIME for `.retui`
files without changing the typing of personal backups or unrelated files.
The marketplace should serve `.retui` downloads with the custom MIME type.
Third-party apps may discard that type; universal tap-to-open is not guaranteed.

## Import entry points

A narrowly scoped exported import activity handles `ACTION_VIEW` and `ACTION_SEND`
for the custom MIME. Do not register as a handler for all ZIP/binary files.
An explicit Import RetUI Preset action in settings opens `ACTION_OPEN_DOCUMENT`;
its picker accepts the custom MIME, ZIP, and generic binary files.
Both routes use the same preset-only inspection/import pipeline. Existing personal
backup restore remains a separate operation. No broad storage permission is needed
by the new flow. Only readable `content://` input is accepted at the public boundary.

## Inspection and confirmation

Copy input into a bounded private staging area and validate before enabling Import.
Show the preset name, supported sections, producer version when available, and a
compatibility summary grounded in the actual package validation. This first release
provides a textual contents preview, not a simulated launcher screenshot. Explain
that selected behavior settings may include commands and other user-provided values;
inspection never runs them. Show the included behavior fields when present.
Cancel or leave the activity without creating a saved preset or changing active state.
Confirm Import to save a uniquely named preset with the existing PresetManager.
Report the saved name and explain that it can be applied later through `preset -apply`.
Do not silently overwrite an existing saved preset.

## Validation and lifecycle

Reuse existing manifest/schema, allowed-entry, XML, and frame validation where
applicable. Enforce compressed input and extracted-byte bounds while reading,
archive-entry count limits, duplicate rejection, safe paths, and manifest bounds.
Validate configuration before committing a saved preset. Use unique private staging
per import and clean up on success, failure, and cancellation. Run blocking reads
and extraction off the UI thread. Missing/unreadable URIs and malformed packages
produce an actionable error, never a crash or personal restore. Revalidate staged
content on confirmation; do not re-open a mutable source URI after inspection.

## Verification

- New export names end in `.retui`; personal names remain `.retui-backup`.
- Saved and current configuration exports remain compatible with the existing schema.
- Correctly typed VIEW and SEND resolve to the import activity.
- Wrongly typed files remain selectable through the manual import picker.
- Personal backups renamed to `.retui` are rejected without changing live settings.
- Supported legacy shared packages import as saved presets.
- Cancel creates no preset; confirmation creates one without applying it.
- Duplicate saved names do not overwrite earlier presets.
- Corrupt, oversized, traversal, duplicate-entry, unsupported-schema, and unexpected
  entry packages are rejected; staging is cleaned up.
- Share URIs are readable by a recipient with a temporary grant and report the
  custom MIME. Both Android build flavors compile and relevant tests pass.
