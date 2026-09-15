# Translating Re:T-UI

Launcher now supports **downloadable, manually imported language packs**. Contributors should start with [language-packs/README.md](../../language-packs/README.md), export the English template from the app, and submit pack sources through a PR. Reviewed packs can ship in independent GitHub releases.

The Android resources below remain the bundled English fallback and developer source catalog. They are also used to generate the downloadable template. Downloaded `strings.xml` uses plain XML text; the Android escaping rules in the resource-development section below apply only to bundled Android resources. No human-language pack is included in this implementation.

## Files to translate

| English source | Contents |
| --- | --- |
| `app/src/main/res/values/strings.xml` | Command help, existing messages, accessibility labels |
| `app/src/main/res/values/strings_ui.xml` | Layout labels, startup setup, reminder editor, shared controls |
| `app/src/main/res/values/strings_settings.xml` | Setting descriptions used by settings/search and `config -info` |
| `app/src/main/res/values/strings_guide.xml` | Walkthrough titles, instructions and progress |
| `app/src/main/res/values/strings_themer.xml` | Appearance settings, typography, frame editor and actions |
| `app/src/main/res/values/strings_surfaces.xml`, `strings_dispatch.xml` | Runtime launcher panels, dialogs and integration responses |
| `app/src/main/res/values/strings_editors.xml` | Editors, profile, wallpaper and Tasker configuration |
| `app/src/main/res/values/strings_commands.xml`, `strings_command_details.xml` | Command responses and validation errors |
| `app/src/main/res/values/strings_managers.xml`, `strings_integrations.xml` | Notifications, widgets, reminders, Spaces and integration messages |
| `app/src/main/res/values/strings_podcast.xml`, `strings_lua_runtime.xml` | Podcast controls and Lua runtime messages |
| `app/src/main/res/values/strings_notes.xml` | Notes library, folders, editor, dialogs, settings, storage and export errors |
| `app/src/main/res/values/strings_search.xml`, `strings_weather.xml` | Search results, module actions, toolbar icons, work-profile badge and native weather conditions |
| `app/src/main/res/values/strings_frame_errors.xml`, `strings_backup_errors.xml` | Frame validation and backup/restore failures |
| `app/src/main/res/values/strings_lua_builtins.xml`, `strings_lua_details.xml` | Built-in Lua display text and Lua controls |
| `app/src/main/res/values/strings_activity_labels.xml` | Android activity labels |
| `retui-datetime-picker/src/main/res/values/strings.xml` | Calendar time-control labels |

Create the same files under a language directory beside `values`, for example `values-pt` for Portuguese, or `values-pt-rBR` for Brazilian Portuguese. Pick the contributor's actual language and region before starting. Keep the English files as the defaults. Translate only the entries you have reviewed; missing entries fall back to English. Do not copy entries marked `translatable="false"`. Android Studio's Translations Editor or a plain XML editor works. [Android resource localization](https://developer.android.com/guide/topics/resources/localization).

`resources.csv` is a reference catalog, not an import format. `source-review.csv` is a developer work queue, not a translation assignment: it includes technical strings and other false positives. Translating that CSV does not change the app.

## Translation contract

- Change the text, never the resource `name`, filename, XML tags, or attributes.
- Keep Re:T-UI, Re:TUI, Termux, tmux, Tasker, Lua, Android and other product names recognizable. Do not rewrite package names or links.
- Keep executable words and flags exactly as written: `guide -start basics`, `module -show notes`, `config -set`, `true`, `false`, `auto`, and setting keys such as `input_text_color`. Translate the explanation around them. Command vocabulary remains the same in every language.
- Keep command-format tokens such as `%t0`, `%pkg`, `%text`, `{query}`, `{slug}`, regular expressions, color codes, file extensions, and paths unchanged. These are not prose or Java format arguments.
- When a resource has Java format arguments, preserve their numbers and types, for example `%1$s` and `%2$d`. You can move indexed arguments to fit the language. For plurals, keep `other` and supply categories required by the language.
- Keep `\n`, `\t`, escaped quotes, and XML escaping intact. Use `&amp;` for `&`, `&lt;` for `<`, and `\'` for apostrophes. The outer double quotes in the new resources preserve whitespace; leave them in place. [Android string syntax](https://developer.android.com/guide/topics/resources/string-resource).
- Prefer short, natural labels. Capitalization is a style choice, not a requirement to shorten a translation unnaturally. Report labels that do not fit; do not silently remove meaning.
- User notes, app names, RSS/podcast content, script output, saved presets and user-defined commands belong to their authors. Do not translate their stored contents.

## Working glossary

| Term | Meaning |
| --- | --- |
| Launcher | The Android home-screen app |
| Module | A built-in or script-driven launcher panel |
| Module dock | Row of shortcuts for opening modules |
| Android widget | A widget supplied by another Android app |
| Preset | A saved launcher configuration/look |
| Frame pack | Graphics used for borders and controls |
| Space | A launcher workspace; not a blank character |
| Guide path | A walkthrough selected by a fixed command ID |
| Retui Credits | Fictional local app points; do not describe them as money |
| Breach key | An in-app focus/lockdown mechanism |

Agree on the language-specific equivalents once and reuse them. Preserve the distinction between Android widgets and launcher modules.

## Developer checks

Run from the repository root:

```sh
python3 scripts/localization_audit.py --write
python3 -m unittest discover -s scripts -p test_localization_audit.py
./gradlew app:assemblePlaystoreDebug app:assembleFdroidDebug app:testPlaystoreDebugUnitTest
./gradlew app:lintPlaystoreDebug
python3 scripts/language_packs.py export --check
python3 -m unittest discover -s scripts -p test_language_packs.py
```

The Python validator checks default-resource existence, duplicate keys, nontranslatable overrides, resource types, empty translations, array lengths, plural fallback and format-argument consistency. It permits partial translations. It does not prove linguistic accuracy, valid command examples, array semantics, Android escaping, or visual fit. Android's resource compiler and manual review remain required.

Debug builds enable generated `en-XA` (expanded/accented) and `ar-XB` (bidirectional) pseudolocales. Use a physical test phone over wireless debugging. Set only the app locale, record its original value first, and restore it after testing. Do not change the whole phone language or clear app data. These are test languages, not shipped human translations. [Android pseudolocale testing](https://developer.android.com/guide/topics/resources/pseudolocales).

Before accepting a language:

1. Check startup Basic/Advanced, appearance settings and search, every drawer/module, confirmations, errors, and `help`/`config -info` output. Include Notes library/folders, new-note and duplicate-name dialogs, rich/Markdown editing, settings, export filenames, and task recreation. Check that dynamic updates do not revert translated labels to English.
2. Check portrait and landscape, small screens, enlarged system font, long labels, dialogs and buttons. Verify non-Latin glyphs with the selected launcher font and custom font fallback.
3. Check RTL text mixed with filenames, commands, numbers and URLs. Readable command order and usable controls matter more than mirroring every terminal surface.
4. Check date selection, the locale's first weekday, month names, 12/24-hour preferences, midnight/noon, and reminder creation/editing. CLI date input remains `dd/mm/yy` and `HH:mm`.
5. Switch back to English and confirm saved settings, aliases, guide progress, presets and integration commands still work. Restart after changing languages to flush cached display text.
6. Have a native speaker review the translation in context. Record the language, device/API, app variant, screenshots and unresolved gaps in the contribution.

Do not advertise full language support while untranslated runtime surfaces or untested layouts remain. The Language packs screen selects an imported pack, English, or the system language. No human translation is declared as supported before review.

## Notes and Lua boundaries

Notes content, Markdown syntax, UUIDs, paths, folder names, encryption markers and exported metadata are document data. Preserve them across language changes. Blank/legacy saved-name fallbacks are not rewritten into a different language. Export filenames accept Unicode letters, combining marks and numbers. Use temporary notes for device tests and remove only those fixtures afterward.

Bundled Lua examples call `strings.localize("stable_resource_key", ...)` for built-in display text. The allowlisted keys live in `strings_lua_builtins.xml`; arguments are indexed string placeholders. Keep callback IDs, command arguments, script APIs and condition values unchanged. Existing user scripts and generated manifests are not rewritten when the app language changes.

`strings_tokens.xml` contains protected punctuation, terminal glyphs and other nontranslatable layout defaults. Do not copy it into a language directory. Resource extraction is not permission to translate literal shell syntax embedded in explanatory strings.
