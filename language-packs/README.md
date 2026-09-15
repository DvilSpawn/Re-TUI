# Launcher language packs

Launcher language packs are downloaded from GitHub and imported manually. They
translate Launcher and its integrated Notes interface without another app update.
Keyboard packs are separate and cannot be imported into Launcher.

Sources, translation PRs and pack releases all belong in **DvilSpawn/Re-TUI**.
A separate repository is unnecessary; use a separate tag for each pack version.
See [CONTRIBUTING.md](../CONTRIBUTING.md) for the PR entry point.

## For users

1. Download a reviewed `.retui-launcher-lang` release asset.
2. Open Launcher Settings → System & Support → Language packs.
3. Choose **Import language pack** and select the file. A successful import activates it.
4. Opened screens refresh when resumed. Notes save through their normal recreation lifecycle.

Import a newer version with the same ID to update it. Older versions are rejected;
the same version may be reimported while testing. An invalid import keeps the
installed pack. **Use English**, **Use system language**, and removal are always
available. Removing the active pack selects English. Packs are stored privately,
work offline after import, and are not fetched or updated in the background.

The `dist/english-example-v1.retui-launcher-lang` asset is a working English
reference pack, not a new translation. Human-language packs need review.

## For contributors

Export the English template from the app, or download the English template ZIP
from a release. The source template also lives under `language-packs/template/`.
Copy it into `language-packs/<locale>/` and follow its README. No Android build is
needed to edit translations or create the archive.

- `manifest.json`: keep `schema: 1`, `target: "retui-launcher"`; set a unique ID,
  BCP-47 language tag, English/native names, positive integer version and `ltr`/`rtl`.
- `strings.xml`: plain UTF-8 XML strings and plural items. Use literal newlines,
  apostrophes and quotes; this format does **not** use Android backslash/quote escaping.
- `LICENSE` / `NOTICE`: optional attribution, required if your source license requires it.

Keep resource names, placeholder numbers/types, and executable examples intact.
Translate complete phrases; delete untranslated entries to use English fallback.
Keep the `other` plural form and add the categories your language needs. Native
Android plural selection, dates and number formatting follow the chosen locale.
Do not translate settings keys, script commands, user notes, links or stored names.

```sh
python3 scripts/language_packs.py build language-packs/<locale> \
  --output language-packs/dist/<locale>-v1.retui-launcher-lang
```

The builder checks keys, types and placeholder consistency. Import also checks
locale, formatting validity, archive limits and schema. Test the file on a phone:
settings, Notes, dialogs, notifications, command output, counts, fallback and RTL.
Submit the sources in a PR with translation credits and testing notes.

## Maintainer release procedure

1. Review the translation and license. Keep resource keys and argument contracts
   stable between app versions; add a new key for an incompatible message change.
2. Run the builder and phone import checks. Pack files contain only `manifest.json`,
   `strings.xml` and optional `LICENSE`/`NOTICE`, at ZIP root.
3. Publish a separate tag `language-pack-<locale>-v<version>` with the generated
   `.retui-launcher-lang` asset. Keep old releases available. Create the tag from
   the reviewed, merged commit containing that pack version. Disable **Set as the
   latest release** so the Launcher app remains the latest app download (with the
   GitHub CLI, use `--latest=false`). Use a title such as **French language pack v1**.
   Include the locale, pack version, tested Launcher build, translation credits,
   known gaps and import instructions in the release notes. Attach only that
   pack; do not attach an APK or AAB to a translation-only release.
4. For the app release introducing this feature, also attach
   `app/src/main/assets/localization/english-template.zip` and the English example.
5. Update the list of reviewed languages below.

No GitHub releases are created automatically by the builder. A pack for newer app
keys may require an app update: unknown/protected keys and incompatible placeholders
are rejected. Older partial packs work with newer app versions while their keys
and placeholder contracts remain supported; missing new messages use English.

## Format and platform limits

Archives are data-only: no fonts, executable code, nested paths or arbitrary assets.
Import is bounded to 4 MiB compressed and expanded, with 64 KiB metadata/attribution
entries and 32 KiB per message. Replacement uses Android AtomicFile. Pack strings
cannot override protected resources. English remains bundled in the application.

The implementation supports the app's existing Android 6+ API baseline; it does not
require Android 11 resource loaders. Android-owned package labels in system settings,
third-party content, saved document names and externally generated text are outside
the downloaded translation layer. The physical phone checks do not replace review
on other OS versions, screen sizes and fonts.

## Reviewed languages

No human-language pack has been reviewed or published as part of this implementation.
