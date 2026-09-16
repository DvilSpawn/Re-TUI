# Localization readiness audit

Date: 2026-09-14. Scope: Launcher, both Android flavors, integrated Re:Member-style Notes, datetime picker and contract resources. Prepared for Build 418; no human-language translation is included.

## Readiness work delivered

The app uses native Android string and plural resources. The catalog contains **2,912 default resources** across the audited modules. Setting descriptions, onboarding metadata, activity labels, dialogs, appearance/frame controls, search results, command responses, notifications, modules, native weather conditions, editors, Notes and bundled Lua display text have resource-backed presentation paths. Indexed placeholders preserve sentence reordering; executable identifiers remain separate from labels.

- Settings metadata stores resource IDs instead of cached English descriptions. Guide resume rerenders from stable progress IDs.
- Theme hub actions, wallpaper scene choices, frame sides, toolbar icons, Tasker actions and module controls retain stable internal values and resolve display labels separately.
- Frame and backup validation exceptions carry resource IDs/arguments, resolved at presentation. Parser exception types and validation remain intact.
- Command parsing uses locale-independent case normalization where it handles fixed vocabulary. Display/search case handling remains separate.
- Native dates/calendar headers follow locale and device time preferences. Real counts use plurals in notes, reminders, contacts, RSS overflow and frame lists. Numeric display includes locale-aware note font percentages and unlock counts.
- Weather condition phrases are resources, including complete light/heavy precipitation phrases; raw numeric parsing and symbol codes stay stable.
- Bundled Lua display calls use an allowlisted `strings.localize` bridge. Script commands, callbacks, values and previously generated user files remain unchanged. Tests compile bundled scripts and verify protected timer behavior.
- XML labels are externalized. Terminal glyphs and machine tokens are marked nontranslatable.

## Downloadable language packs

Launcher now imports data-only `.retui-launcher-lang` ZIP files from Settings → System & Support → Language packs. A native resource/context layer covers existing string/plural lookups, activity titles, XML text/hints/accessibility labels, services and receiver contexts, including integrated Notes. Missing pack entries use bundled English. The selected locale controls formatting and plural categories; the manifest controls layout direction. Screens refresh on resume and selection persists across process restarts.

The exported plain-XML English template contains **2,854 translatable keys**. Archive validation protects resource names, placeholder contracts and size limits; invalid imports preserve the installed pack, and atomic replacement supports equal/newer versions. Packs remain private and work offline. Contributors submit source files through Git, and maintainers can publish pack-only GitHub releases without rebuilding Launcher. The first app update introducing this feature is still required.

The contributor guide and versioned example live in [language-packs](../../language-packs/README.md). No human-language pack or GitHub release has been published. Android-owned package labels and third-party text remain outside this runtime layer.

## New Notes feature

The library, folders, editor, settings, new/duplicate-note dialogs, validation, save/export errors and accessibility labels use resources. Folder validation returns message IDs. Unicode export filenames retain letters, combining marks and numbers instead of discarding non-Latin names. Note content, Markdown and stored metadata are preserved.

Phone testing exposed an RTL inset issue in the Markdown toggle; it now uses an end margin and symmetric padding. New-note actions have a minimum touch height and can grow vertically for longer translations. Integration tests locate translated library controls and include Japanese, Hindi, Arabic and accented Latin content, editing, persistence and activity recreation.

## Verification

| Check | Result |
| --- | --- |
| Play Store debug assembly | Passed |
| F-Droid debug assembly | Passed |
| Play Store unit tests | 173 passed; zero failures/errors/skips |
| Android test APK assembly | Passed |
| Android lint, Play Store debug | Passed; zero errors, 500 warnings, 346 hints |
| Resource validator | Zero errors |
| Resource validator tests | 3 passed |
| Pack exporter/builder tests | 3 passed; template current |
| Physical phone, imported pack | 5 passed: template parity, import/update/removal, invalid imports, XML/plurals/fallback, Notes and document button callbacks |
| Physical phone, process restart | Install and fresh-process verification passed; prior selection restored |
| Optimized release resource retention | All 2,854 pack keys present after shrinking |
| Whitespace/diff check | Passed |
| Physical phone, expanded `en-XA` | All 4 Notes integration tests passed |
| Physical phone, RTL `ar-XB` | All 4 Notes integration tests passed |

Earlier Kotlin lint analyzer crashes were resolved by subsequent completed runs. The final result above is a completed report, not a suppressed check or baseline.

Phone: I2220, accessed only over its wireless ADB connection. Package `com.dvil.tui_renewed`, Play Store debug, version code 417 / version name 2. The existing and new APK signing certificates matched; installation used an in-place update without clearing data. App locale was recorded as system default before tests and was restored afterward (verified empty app-locale list). The temporary test harness and device-side test screenshots/UI dump were removed. Test fixtures are removed in `finally` blocks; existing user notes are not edited by these tests. No emulator result is counted.

Rendered review covered the expanded-text home input and Notes editor/new-note dialog in expanded and RTL modes. Japanese, Hindi, Arabic and accented Latin text rendered in the user's selected font configuration. The RTL editor and dialog mirrored their chrome while preserving document text. Screenshots and phone test logs are in the local, build-output-only `app/build/reports/localization/phone/` directory; these are not shipped assets.

The downloadable-pack regression used a partial Arabic RTL fixture, including translated Notes Markdown controls and English fallback. The import/export button test returned test document URIs through instrumented document-picker results; it verifies the buttons, URI streams and callbacks, not manual navigation through every external file provider. The pack screen and synthetic multilingual Notes editor were inspected on the phone; the new pack screen now respects system-bar insets.

## Remaining literals and acceptance limits

The lexical inventory reports **2,014 candidates in 139 files**. This is not an untranslated-string count or a coverage percentage. It includes commands, shell/Lua source, regular expressions, annotations, logging, identifiers, branding, example manifests and author-supplied data. Keep this queue for maintenance review instead of bulk-translating it.

The remaining seven `SetTextI18n` findings are bracket/accordion decoration, message line joins, prefixes around external music metadata, and literal `auto` / `open ` values inserted into editable command/configuration fields. The four `RtlHardcoded` findings are physical ASCII-art alignment calculations; changing them to logical alignment would change the artwork contract. No XML `HardcodedText` finding remains in the completed lint report.

Stored names and legacy blank-name defaults, generated Lua example names/descriptions, Markdown export metadata, app/artist/feed names, external command output, external-library diagnostics and saved user configuration are not automatically translated. Do not use a translated string as a protocol or persistence key. A language contributor should translate the exported pack template, not the source-review CSV.

The device results are focused regression evidence, not a claim that every screen, permission state, custom font, screen size or language was visually inspected. Human-language releases still require native-speaker review and the full screen/font/date/RTL acceptance checklist in the translator guide. No human locale is declared as supported until its resources and review exist. Store listings, web documentation and third-party content remain separate translation deliverables.

## Contributor handoff

Start at [README.md](README.md). The generated [INVENTORY.md](INVENTORY.md), `resources.csv` and `source-review.csv` record the current sources. For app resource changes, refresh the pack template and run the validator and Android build/lint. For a translation-only contribution, run the pack builder and phone import checks; an Android rebuild is not required. Never infer readiness from a resource count alone.

## Build 418 release review — 2026-09-15

Reviewed the localization, language-pack and Notes changes together. Fixed Notes
recovery to respect atomic index/Markdown backups after interrupted writes.
Both debug flavors, optimized Play Store release, signed AAB, 173 unit tests and
lint passed. Eleven physical-phone regression tests passed over wireless ADB.
The release includes the template and English reference pack; language-specific
pack publication still requires translation review. Play Console submission is
separate from producing the signed bundle.

## Build 419 release review — 2026-09-16

The English template and example pack were regenerated after adding the localized
`preset -market` help entry. The catalog remains at 2,854 translatable keys.
Pack validation, localization audit, 174 unit tests and full-app lint passed.
The signed release APK and Play Store bundle were built from version code 419.
