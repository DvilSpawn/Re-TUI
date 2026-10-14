# Module checks and previews

Re:TUI supports widget API **1**. Declare `-- retui = "1"`; existing modules that
omit the declaration continue to use API 1. Other values are rejected before
saving/importing through the editor, before permission approval, and before Lua
loads in any engine execution path (module, config, suggestions, commands and
reminder callbacks). The explanation identifies the requested API, the supported
API and the update/use-compatible-module options. Existing files are preserved
when an incompatible draft is rejected.

Use `module -preview <id>` to inspect a saved module. The module editor's
**Preview draft** button renders the current text without saving. Installed
library entries also offer **Preview** and **Configure**. `module -check <id>`
remains a text diagnostic check; it now disposes its runtime after checking.

The preview uses the same text, layout and button renderer as the active module.
It shows partial output alongside runtime errors and the failing lifecycle stage.
Buttons/suggestion labels are present for inspection but do not execute actions.
Choose **Width: device / 240dp** to inspect wrapping, and **Data: saved / empty**
to render with existing or empty preferences. Empty mode leaves the saved data
unchanged. Previews render once; timers are snapshots rather than ticking sessions.

Rendering is isolated: preference changes remain in memory; preview errors do not
replace the module's saved error. Network, widget files, clipboard, vibration,
notifications, app launches, intents and shortcuts are blocked. Approved ticking
metadata is allowed but no ticker is scheduled. Modules requiring capabilities
must be saved and approved before preview; an edited draft cannot inherit the
old script's approval. Capability-dependent widgets may therefore show a blocked
capability diagnostic in the preview. Use the approved module for live data.

**Export preview + diagnostics** opens Android's share chooser with a ZIP containing
`preview.png` and `diagnostics.json`. The report includes the API, selected width,
data mode, output/layout, error stage and debug lines. Exports are placed in app
cache; Android grants read access only to the chosen receiver. Large previews
(over eight million pixels) show an export error rather than allocating unbounded
image memory.

## Physical-phone verification

`ModulePreviewDeviceTest` targets the existing `.librarydebug` app and refuses to
run on the everyday launcher. It verifies rejection without overwriting source,
blocked execution before on_load, version-1 execution, saved/empty prefs isolation,
partial output with runtime errors, permission approval, blocked external actions,
long-text wrapping at 240/360/600dp, inert controls, draft preview without saving,
ZIP image/report contents and the Android content-provider read path.

600dp is a simulated layout width on the phone, not a claim of tablet testing.
Image capture and test logs are under `app/build/reports/module-preview/`.
