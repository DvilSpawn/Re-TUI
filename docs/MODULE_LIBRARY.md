# Online module library

Open `module -library` to fetch the website catalogue. Each row shows the name,
installed status and short description. Tap a row for a themed sample preview,
full description, version and attribution. The catalogue includes real phone-rendered screenshots and explicit requirements.
The text preview is inert sample text;
browsing it does not download or execute Lua. Choose **Install** or **Cancel**.
Install verifies the download, adds it to the dock and opens it; permission approval
is still required before a module with sensitive capabilities runs.

Installed entries offer **Open**, **Preview**, **Configure** (when available), **Remove** and, when the publisher checksum
changes, **Update**. Remove asks for confirmation and deletes the script, saved
settings/data and dock registration. Cancel keeps them. Update asks for confirmation,
replaces source edits and preserves saved settings/data. It never updates silently.
The catalogue refreshes whenever you open the library; an offline cached list cannot
know about newer publisher changes until the next successful refresh.

**Edited locally** compares the source script with its original installed checksum.
Task completion and configuration changes do not count as source edits.
**Update available** compares the website checksum with that installed baseline,
so a publisher edit is detected even if its version label is unchanged. Both flags
can appear together. Existing copies without a recorded baseline are marked
**local copy** when they differ, rather than guessing whether they are older or edited.

`module -library todo`, `habits`, `focus`, `bookmarks`, or `year_progress` opens an
installed module directly; for an uninstalled module it opens the details preview.
Installed IDs are `oma_todo`, `oma_habits`, `oma_focus`, `oma_bookmarks`, and
`oma_year_progress`. Use `module -config <id>` to edit settings, `module -edit <id>`
to edit the script, and `module -export <id>` to share it. `module -hide <id>` hides
it from the dock while keeping its files. Library **Remove** is the complete uninstall
path; the older `module -rm` command removes the registry entry only.
The list is fetched from `https://re-tui.pages.dev/modules/index.json`.

The last valid catalogue is saved for offline browsing. A first load needs a network
connection; installed modules can always be opened with `module -show <id>`.
No account, Linux installation, or new runtime dependency is needed. There is no
hardcoded catalogue or bundled production Lua module library in the APK.

| Module | Use | Permission |
| --- | --- | --- |
| Todo | Edit one task per line; Done chips remove completed tasks; Undo last restores the most recent one. | None |
| Daily Habits | Editable daily checklist; tap Done/Undo. Resets on the next open after the local date changes. | None |
| Focus Timer | Start/stop a 5–60 minute session; remaining time survives reopening. | Active ticking |
| Bookmarks | Edit one `Name \| https://url` per line; open a link through a suggestion chip. Invalid entries are shown without launch actions; domains and IPv4 hosts are supported. | Intents |
| Year Progress | Day, Monday-based week, month, and year progress in the phone's local time. | Active ticking |

Modules with permissions first show Re:TUI's approval surface. Inspect them there
or with `module -info <id>` and approve with `module -approve <id>`.
The focus timer has no background alarm; it shows completion when opened. Its
absolute deadline follows the phone clock. Changing the duration stops the session.
Habits deliberately keep today's checkmarks only; streak history is not included.
Bookmarks deliberately omit thumbnail/title fetching and accept HTTPS URLs only.

Real author previews and compatibility enforcement are described in [MODULE_PREVIEW.md](MODULE_PREVIEW.md).

Preservation of editable defaults is described in [MODULE_DEFAULT_UPDATES.md](MODULE_DEFAULT_UPDATES.md).

## Catalogue references

These modules are Lua implementations written for Re:TUI. The following catalogue
entries were used as feature references, reviewed at upstream commit `b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e`:

- [Todo](https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/todo)
- [Habits](https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/habits)
- [Focus](https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/focus)
- [Bookmarks](https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/hoangitk/bookmarks)
- [Year Progress](https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/year-progress)

The upstream catalogue is copyright (c) 2026 Simon Schubert and contributors. Its
MIT notice is retained at `docs/modules/LICENSE` in the website repository. The
module metadata identifies Re:TUI as the Lua implementation author and records
the upstream catalogue references separately. Existing `oma_` IDs are retained
so installed modules, settings and local edits keep their identities.

Version 1.0.1 updates descriptions and provenance metadata only. Executable Lua
bodies are unchanged; previous 1.0 download URLs remain available.

## Phone verification

`ModuleLibraryDeviceTest` uses a dedicated `.librarydebug` Play Store debug copy
and refuses to run against the everyday launcher. The deterministic HTTP fixture uses the same catalogue and scripts as the site;
it does not prove live hosting. It covers catalogue refresh/cache, checksum and
URL validation, verified downloads, Lua rendering,
reopening saved state, daily reset, leap/century years, invalid bookmark URLs,
permission approval chips, real settings Save and Cancel/Discard, Todo Done/Undo,
habit checkmarks, Focus Start/Stop, edited-script preservation, picker selection,
and opening modules with the dock hidden.

To build the dedicated test copy without changing project signing or versioning,
put this in a temporary Gradle init file:

```groovy
allprojects {
    plugins.withId('com.android.application') {
        android.buildTypes.debug.applicationIdSuffix = '.librarydebug'
        android.buildTypes.debug.manifestPlaceholders.applicationLabel = 'Re:TUI Library Test'
    }
}
```

Build with `./gradlew -I <init-file> :app:assemblePlaystoreDebug :app:assemblePlaystoreDebugAndroidTest`.
Install both APKs using `adb -s <serial> install -r`, then run:

```sh
adb -s <serial> shell am instrument -w -r \
  -e class ohi.andre.consolelauncher.modules.ModuleLibraryDeviceTest \
  com.dvil.tui_renewed.librarydebug.test/androidx.test.runner.AndroidJUnitRunner
```

Screenshots are written to the test copy's external files directory as
`module-library-*.png`. This verifies the implementation on the selected phone;
it is not a release or a claim about other devices.
