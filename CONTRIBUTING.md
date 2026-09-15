# Contributing to Re:TUI

## Translate Launcher

Language packs live in this repository under `language-packs/<locale>/` and are
published as separate GitHub releases. You do not need Android Studio to contribute.

1. Fork this repository and copy `language-packs/template/` to your locale folder.
2. Follow the [language-pack guide](language-packs/README.md) to edit the manifest
   and translations, build your pack, and import it into Launcher for testing.
3. Open a pull request with the source files and translation credits. Use the
   [translation PR template](https://github.com/DvilSpawn/Re-TUI/compare?expand=1&template=language_pack.md).
4. A maintainer reviews the translation and publishes the downloadable pack.

Partial translations are welcome: omit untranslated entries to keep English
fallback. For updates, retain the pack ID and increment its version. Include
license/attribution files when required. Do not include personal notes or settings.

Translation PRs receive automatic catalog/template and pack-build checks. These
checks do not replace a native speaker's review or phone import testing.

## Other changes

Describe the problem, the resulting behavior, and how you tested the change.
For changes to app strings, follow the [localization maintenance guide](docs/localization/README.md)
and refresh the English pack template with `python3 scripts/language_packs.py export`.
