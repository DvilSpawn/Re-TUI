# Re:TUI V.2 - Build 418

Build 418 adds downloadable Launcher language packs and a full-file Notes library.

## Language packs

- Import, update, select and remove language packs in Settings → System & Support → Language packs.
- Missing translations fall back to English. Translation updates can be released independently of app updates.
- Export the English translation template from the app. Contributors can submit source translations through GitHub PRs.
- Added resource-backed text, locale-aware dates/counts, RTL support and translation validation across Launcher and Notes.

The attached English template and example pack are reference files. No human-language pack is included in this release. Keyboard language packs use a different format.

## Notes

- Notes open as individual Markdown files, with a library, folders, rich editing and export.
- Tapping a note opens its editor; tapping the Notes pane background opens the library.
- Notes remain in their own Recent Apps task across Launcher reloads.
- Existing Launcher notes migrate into the new library while retaining the original XML file.
- Interrupted index and Markdown writes recover their atomic backup files.

## Downloads

- Install the APK to update Launcher.
- Translators: unzip `retui-launcher-english-template.zip` and follow its README.
- Import `english-example-v1.retui-launcher-lang` to try the pack workflow in English.

Version 2, version code 418. Language pack contributor instructions are in `CONTRIBUTING.md` and `language-packs/README.md`.
