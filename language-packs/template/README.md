# Translate Re:TUI Launcher

Edit manifest.json: choose a unique id, languageTag, English/native language names,
version (positive integer), and direction (ltr or rtl). Keep schema and target.
Translate strings.xml. Its contents are plain UTF-8 XML, NOT Android string escaping:
use literal apostrophes, quotes and line breaks; escape XML & and < as &amp; and &lt;.
Keep resource names and numbered format arguments (%1$s, %2$d) unchanged.
For plurals keep other and add your language's zero/one/two/few/many forms.
You may delete untranslated entries: they fall back to English.
Keep executable commands, flags, URLs, filenames and configuration tokens unchanged.

Do not include this README in the finished pack. Build it from the repository:
python3 scripts/language_packs.py build language-packs/YOUR-LOCALE --output YOUR-PACK.retui-launcher-lang

Import the file in Launcher Settings > System & Support > Language packs.
Test Notes, settings, command responses, notifications, plurals, dates and RTL.
Submit the sources under language-packs/YOUR-LOCALE in a pull request, including
translation credits and any required LICENSE/NOTICE. A maintainer publishes the
reviewed pack in a separate language-pack-LOCALE-vVERSION GitHub release.
Keyboard .retui-lang packs are a different format and cannot be imported here.
