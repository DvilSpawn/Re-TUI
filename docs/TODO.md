# Re-TUI TODO

This is a working backlog for issues and user requests after the timer/pomodoro/widget-page branch was merged into `master`.

Phone UAT checklist: `docs/TESTBOOK.md`.

## Product Direction - Distraction-Free Workstation Surface

Re:T-UI is a distraction-free Android workstation surface for people who prefer commands, local control, and composable workflows over visual launcher clutter.

It should not chase casual launcher expectations. It should reward users who are willing to type `help`, read docs, configure aliases, and build their own phone workflow.

### Rollout Phase 1 - Help, Identity, And First Wow

- [x] Merge onboarding into `help`.
- [x] Keep first-run lightweight. No guided tour, no casual-user wizard.
- [x] No-arg `help` should suggest:
  - launch an app
  - create an alias
  - hide an app
  - apply a wallpaper-derived theme
  - save the theme as a preset
  - inspect/show modules
- [x] Link help output and wiki docs to the relevant reference pages.
- [x] Explore first-install `wallpaper -auto` as an immediate wow moment.
  - Implemented as a blank-input suggestion while the user has no saved presets.
  - Do not auto-run it; wallpaper-derived theming remains user-initiated.
- [x] Defer starter presets. Wallpaper auto is the preferred first personalization path.

### Rollout Phase 2 - Search And Suggestions 2.0

- [x] Audit current fuzzy search across apps, commands, aliases, contacts, and settings.
- [x] Define what Search 2.0 adds beyond current behavior before implementing new UI.
- [x] Favor a result-first flow:
  - typed intent surfaces relevant objects in suggestions
  - selecting an object can reveal contextual actions
- [x] Contact example:
  - typing a name surfaces the person
  - selecting the contact exposes call, contact details, and edit actions
- [x] App example:
  - selecting or long-selecting an app can expose open, hide, app info, group, or uninstall
  - decision: keep root app suggestions launch-first
  - app management stays under explicit `apps` commands: `apps -l`, `apps -hide`, `apps -st`, `apps -ps`, and group commands
  - revisit long-press app actions later only if direct launch remains untouched
- [x] Do not prioritize command history; aliases and toolbar up-arrow already cover frequent/recent command needs.

### Rollout Phase 3 - Re:T-UI Native Modules

- [x] Build on the current module system rather than replacing it.
- [x] Keep built-in modules as Re:T-UI-owned terminal surfaces.
- [x] Keep script-backed modules as the extensibility model.
- [x] Design a module callback contract that can update:
  - module body text
  - module title/status
  - suggestion entries
  - optional action payloads
- [x] When a module is active, show its suggestions in a separate module action strip.
  - First slice implemented for command-backed built-in modules.
  - Stock launcher suggestions stay available in the normal strip.
  - Module actions stay visually separate from normal typing suggestions.
- [x] For Termux-backed modules, define how suggestion clicks are sent back to the script or callback bridge.
  - First implementation supports script-provided `command` suggestions.
  - `termux-run` and `callback` modes remain parsed contract values, but are not executable suggestion modes yet.
- [x] Avoid arbitrary Java/Kotlin plugin loading. Scripts plus controlled Re:T-UI primitives are the boundary.
- Draft contract:
  - body: text rendered inside the active module
  - title: short module label/status line
  - suggest: one or more suggestion chips owned by the active module
  - action: command/script payload tied to a suggestion
  - mode: how action is dispatched, such as `command`, `termux-run`, or `callback`
- First design target:
  - [x] active module can expose action chips without replacing normal suggestions
  - [x] inactive modules do not hijack suggestions
  - [x] suggestion clicks are explicit and inspectable for command-mode built-ins
  - no arbitrary code is loaded into Re:T-UI
- Next implementation slice:
  - [x] parse script module metadata lines from Termux stdout
  - [x] cache script-provided suggestions with the module body
  - [x] support `command` mode first
  - design `termux-run` dispatch before enabling it
- Native prompt session slice:
  - [x] Add a first native module prompt loop.
  - [x] Let active module prompts intercept the normal input line until saved or cancelled.
  - [x] Ship the reminder module as the first conversational module.
  - [x] Support reminder add, edit, remove, and list flows.
  - [x] Schedule reminders through native Android notifications instead of Termux.
  - [ ] Generalize the prompt engine for third-party script modules after reminder UAT.
  - [ ] Decide whether script modules can request prompt sessions through a safe metadata contract.

### Rollout Phase 4 - Deliberate App Drawer

- Keep app groups deliberate through the existing group command flow.
- Do not make automatic grouping a headline feature.
- Polish the drawer around terminal identity:
  - terminal frame
  - alphabet navigation
  - user-created groups
  - hidden apps respected
  - wallpaper visible outside the drawer
- Revisit drawer ergonomics for one-handed use.
- [x] Move group tabs away from the top-left toward a bottom horizontal rail.
- Keep group creation/editing in `apps` commands; drawer tabs only switch views.

### Rollout Phase 5 - Workflow Aliases

- [x] Confirm aliases already support command chaining through `multiple_cmd_separator` (`;` by default).
- [x] Replace the proposed recipe system with workflow-alias documentation.
- Do not add `recipe -ls`, `recipe -preview`, `recipe -apply`, or `recipe -undo`; that would duplicate aliases.
- Treat aliases as the official inspectable workflow layer.
- Document chained alias examples for:
  - focus
  - dev
  - morning
  - privacy
  - commute
- Leave these alias polish ideas for later:
  - better `alias -ls` formatting
  - `alias -show <name>`
  - `alias -run <name>` only if direct alias execution becomes unclear
  - warnings if an alias chain contains destructive commands

### Rollout Phase 6 - Automation And Chaining

- Alias chaining audit complete: aliases can already expand into multi-command chains.
- [x] Audit existing automation surfaces before adding new primitives.
- Existing surfaces:
  - workflow aliases: manual multi-command chains through `;`
  - Termux: `termux -run` and script-backed modules
  - callbacks: token-gated `output`, `notify`, and `module_set`
  - webhooks: saved POST templates with argument substitution
  - shortcuts: Android app shortcut launch
  - notifications/reply: notification filters, terminal notification toggle, bound reply apps
  - clocks: timer, stopwatch, and pomodoro state broadcasts
- Do not add another basic chaining feature.
- Add new automation primitives only if they solve gaps aliases cannot express cleanly.
- Real gaps to discuss later:
  - conditions: run only if state matches
  - confirmations: ask before sensitive commands
  - scheduling: run command/alias at time or interval
  - failure handling: stop or continue when one command in a chain fails
  - external triggers: allow narrow trigger events without arbitrary external command execution
- Security boundary:
  - keep token-gated callbacks narrow
  - do not expose arbitrary external command execution through callbacks
  - prefer callbacks that update output/modules over callbacks that run commands

### Rollout Phase 7 - Focus Profiles

- Defer detailed design.
- Decide later whether focus profiles are workflow aliases, settings snapshots, module layouts, app visibility filters, or a mix.
- Avoid a complex profile manager unless it clearly reduces setup friction.
- Keep profiles inspectable and editable.

### Rollout Phase 8 - Trust, Distribution, And Community

- Privacy-first positioning:
  - local config
  - no ads
  - no tracking
  - clear permissions
- Keep Play Store stable and Firebase beta experimental.
- Maintain this roadmap in `docs/TODO.md`.
- Support community sharing for aliases, modules, workflow aliases, themes, and ASCII headers.
- Add migration docs for Nova-style power users, Niagara/minimal launcher users, Termux users, and original T-UI users.

## Next Strategic Route - Android Intent Router And Re:T-UI Script

Goal: let Re:T-UI users build native Android workflows from the launcher without requiring Termux, while keeping Termux as the deeper external scripting layer.

The product boundary:

- Re:T-UI should become a command-line router for Android actions.
- Aliases remain the simple fixed-command chaining layer.
- Re:T-UI Script becomes the interactive workflow layer for commands that need runtime user input.
- Termux remains the external shell/programming escape hatch.
- Do not turn Re:T-UI Script into arbitrary Android/Unix code execution.
- Keep every action inspectable, aliasable, and readable from plain text.

### Intent Route Phase 1 - Intent Command MVP

- [x] Add a new `intent` command for common Android intent dispatch.
- [x] Support the most useful safe primitives first:
  - [x] `intent -view <uri>`
  - [x] `intent -activity -a <action> [-d <data>] [-t <mime>] [-p <package>] [-n <package/class>]`
  - [x] `intent -broadcast -a <action> [-p <package>] [-n <package/class>]`
  - [x] `intent -uri <intent-uri>`
  - [x] `intent -check ...`
- Extras should be explicit and typed:
  - [x] `--es key value` for string
  - [x] `--ei key value` for int
  - [x] `--ez key true|false` for boolean
  - add other extra types only when needed
- Use cases to validate:
  - maps/search: `geo:`, `https:`
  - dial/SMS/email prefill
  - Android share sheet / `ACTION_SEND`
  - app-specific activity launch
  - Tasker/MacroDroid-style explicit broadcasts
- Safety rules:
  - [x] catch `ActivityNotFoundException` and print a clean terminal error
  - [x] require `-a` for broadcasts
  - [x] prefer explicit package/component for broadcasts
  - [x] do not support arbitrary implicit `startService()` in phase 1
  - if services are ever added, require explicit component and a separate design pass
- Alias examples:
  - `alias -add maps-home intent -view geo:0,0?q=home`
  - `alias -add share-note intent -activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "note"`
  - `alias -add tasker-work intent -broadcast -a net.dinglisch.android.tasker.ACTION_TASK -p net.dinglisch.android.taskerm --es task_name Work`

### Intent Route Phase 2 - Discovery And Inspectability

- Add `intent -check` before deeper workflow features.
- Print matching handlers when possible:
  - app label
  - package
  - activity/component
  - whether the target appears launchable from current package visibility rules
- Add useful error outputs:
  - no handler found
  - missing action/data
  - invalid component
  - invalid URI
  - package visibility may prevent inspection
- Consider a command for app activities only if needed:
  - `apps -activities <app>`
  - or keep this under `intent -check -p <package>`
- Do not add a visual intent builder yet. This is a technical launcher; command docs and examples are enough for phase 2.

## Next Strategic Route - Command-First File Manager Layer

Goal: make Re:T-UI a real workstation file surface, not just a launcher that can occasionally open files.

Distribution assumption:

- Re:T-UI will keep a Play Store-safe launcher surface.
- Re:T-UI Files and Termux own the heavier file/scripting workflows.
- The Play Store flavor removes `MANAGE_EXTERNAL_STORAGE`; GitHub/Firebase builds may keep broader compatibility where appropriate.

Product boundary:

- Re:T-UI owns navigation, suggestions, and terminal output.
- Android intents handle external viewing/sharing/editing.
- Termux remains optional for deeper scripting, not the default filesystem backend.
- File commands should feel like a small shell, but stay readable and launcher-native.

### File Manager Phase 1 - Stable Native Navigation

- [x] Restore native `cd`, `pwd`, `ls`, and `open`.
- [x] `cd` suggestions show directories in the current path.
- [x] `open` and `share` suggestions show files in the current path.
- [x] `open <file>` should open Android's chooser for files, not directories.
- [x] Prevent known Re:T-UI commands from leaking into the embedded shell fallback.
- [x] Add explicit `shell <command>` as the inspectable path for embedded shell execution.
- [x] Add `shell_requires_prefix` behavior toggle.
- [x] Default `shell_requires_prefix` to `true` so raw shell execution is intentional.
- [ ] Verify `open <file>` on device after shell-fallback hard stop.
- [ ] Add file-manager examples to `help cd`, `help ls`, `help open`, and the testbook.

### File Manager Phase 2 - Deliberate File Actions

- Add `file` as an inspectable command namespace only where it adds clarity.
- Candidate actions:
  - `file -info <file>`
  - `file -rename <from> <to>`
  - `file -copy <from> <to>`
  - `file -move <from> <to>`
  - `file -mkdir <name>`
  - `file -rm <path>`
- Sensitive actions should require a confirmation path before execution.
- Keep `cd`, `pwd`, `ls`, `open`, and `share` as first-class shortcut commands.

### File Manager Phase 3 - Suggestion Surfaces

- Make current-directory suggestions context-aware:
  - empty input can suggest common file actions when cwd is active
  - `cd` suggests folders only
  - `open` suggests openable files only
  - `share` suggests files only
  - `file -move` and `file -copy` can suggest destination folders
- Add lightweight file metadata to suggestions if it remains visually clean:
  - folder/file marker
  - extension
  - size for files
  - modified date only if it does not clutter the strip

### File Manager Phase 4 - Preview And Output Discipline

- Add terminal-native previews for common text-ish files:
  - `.txt`, `.md`, `.json`, `.csv`, `.log`, `.xml`
- Add bounded output:
  - first N lines
  - `--tail`
  - `--head`
  - clear "file too large" messaging
- Keep binary files routed through Android intents.

### File Manager Phase 5 - Aliases And Workflows

- Document file workflows as aliases:
  - jump to downloads
  - open latest PDF
  - share latest screenshot
  - clean temporary folder
- Later: allow Re:T-UI Script to prompt for filenames/paths.
- Keep destructive chained aliases inspectable before recommending them.

### File Manager Phase 6 - Optional Termux Power Layer

- Termux can be an advanced backend for explicit commands like grep/find/archive operations.
- Do not make Termux required for core file navigation.
- If added, keep it opt-in:
  - `file_backend = native`
  - `file_backend = termux`
  - `file_backend = auto`
- Re:T-UI should own cwd state even when Termux performs deeper work.
- Preferred product shape:
  - main Play Store Re:T-UI stays launcher/workstation focused
  - advanced file management is positioned as a Termux power-user feature
  - build a separate Re:T-UI Termux Bridge/Plugin rather than a headless file-manager permission proxy
  - the bridge should expose narrow filesystem operations, not arbitrary command execution
  - Re:T-UI owns command grammar, cwd state, suggestions, output, and confirmations
  - Termux owns shell execution and filesystem access after the user explicitly configures it

## Termux Bridge File Backend Rollout

Goal: make advanced file management a Termux power-user option while keeping the Play Store Re:T-UI app focused on launcher/workstation behavior.

### Termux Bridge Phase 1 - In-App Protocol Probe

- [x] Add a `tbridge` command as the first inspectable bridge surface.
- [x] Add `tbridge -status` for local readiness checks:
  - Termux installed
  - RUN_COMMAND declared by Termux
  - RUN_COMMAND granted to Re:T-UI
  - current Re:T-UI path
- [x] Add `tbridge -setup` with exact user setup steps.
- [x] Add `tbridge -probe` to run a lightweight Termux environment probe.
- [x] Added Termux-backed listing probes, then retired them from the public command surface:
  - `tbridge -ls [path]`
  - `tbridge -dirs [path]`
  - `tbridge -files [path]`
- [x] Route bridge results into the main output terminal rather than only the Termux console overlay.
- [x] Phone-test with installed Termux 0.118.3.
  - Fixed detection to check Termux's declared `RUN_COMMAND` permission/service rather than requested permissions.

### Termux Bridge Phase 2 - Backend Abstraction

- [x] Add `file_backend` behavior setting:
  - `auto`
  - `native`
  - `termux`
  - `off`
- [x] Add initial backend resolver/status helper.
- [x] Surface active backend state in `tbridge -status`.
- Add a `FileBackend` boundary:
  - native backend for GitHub/Firebase builds
  - Termux bridge backend for Play-safe advanced file mode
  - disabled/limited backend when neither is available
- Re:T-UI should continue owning:
  - cwd state
  - command grammar
  - suggestions
  - output formatting
  - confirmations

### Termux Bridge Phase 3 - Suggestions

- [x] Use Termux bridge output to power suggestions when active backend is Termux:
  - [x] `cd` -> directories only
  - [x] `open`/`share` -> files only
  - future `file -copy`/`file -move` -> destination folders
- [x] Cache recent directory/file listings briefly to avoid firing Termux on every keystroke.
- Keep native suggestions as fallback in GitHub/Firebase builds.

### Termux Bridge Phase 4 - File Actions

- Add guarded Termux-backed actions:
  - info/stat
  - mkdir
  - rename
  - copy
  - move
  - delete with confirmation
- Avoid arbitrary command execution through the bridge actions.
- Keep `shell <command>` and `termux -run` as the explicit escape hatches for arbitrary commands.

### Termux Bridge Phase 5 - Play Store Flavor

- [x] Remove `MANAGE_EXTERNAL_STORAGE` from the Play Store flavor.
- Remove startup all-files permission gating from the Play Store flavor.
- Keep `tbridge` available in the Play Store flavor as an optional power-user integration.
- Present missing bridge setup as terminal output, not an onboarding modal.

### Termux Bridge Phase 6 - Separate Companion/Plugin

- Only after in-app protocol is stable, decide whether a separate Re:T-UI Termux Bridge APK is necessary.
- If built, it must be a real user-facing integration app, not a permission proxy.
- It should expose narrow, documented filesystem operations to Re:T-UI.

### Intent Route Phase 3 - Docs And Shareable Patterns

- Add wiki docs for intent command examples.
- Document common recipes:
  - open maps/search
  - compose SMS/email
  - share selected text
  - open Android settings panels
  - Tasker/MacroDroid broadcast trigger
  - open specific app activity
- Add warnings for app-specific intents:
  - they can break when target apps update
  - extras are often undocumented
  - Android package visibility may affect discovery
- Add testbook coverage for:
  - valid activity intent
  - valid broadcast intent
  - invalid target
  - alias calling an intent
  - intent command inside an alias chain

### Re:T-UI Script Phase 1 - Interactive Prompt Workflows

- Add a small native scripting layer for users who want interactive workflows without Termux.
- Treat it as a workflow macro language, not a general-purpose programming language.
- Minimal primitives:
  - `ask <name> "<prompt>"`
  - `set <name> "<value>"`
  - `run "<retui command>"`
  - `output "<text>"`
  - `confirm <name> "<prompt>"`
- Variable substitution:
  - `$name`
  - quote-safe substitution for command arguments
- Example target:
  - `send-standup` asks for the standup text in the normal input surface, then sends that text through an Android share/send intent.
- Example script:
  - `ask note "Standup update"`
  - `run "intent -activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT \"$note\""`
- Execution model:
  - scripts run one step at a time
  - `ask` pauses execution and changes the input prompt
  - user input resumes the script
  - cancellation should be possible with back/clear/ctrl-c equivalent
- Storage:
  - start with plain text files or an XML-backed registry, whichever fits existing command patterns best
  - scripts should be inspectable from the launcher

### Re:T-UI Script Phase 2 - Command Surface

- Proposed command:
  - `script -add <name>`
  - `script -edit <name>`
  - `script -show <name>`
  - `script -ls`
  - `script -rm <name>`
  - `script -run <name>`
- Decide whether scripts should register as direct commands:
  - Option A: user runs `script -run send-standup`
  - Option B: user runs `send-standup` directly after registration
  - Option C: aliases call scripts explicitly
- Preferred starting point:
  - keep direct execution explicit through `script -run`
  - allow aliases to hide that if the user wants a shorter command:
    - `alias -add send-standup script -run send-standup`
- Add suggestions:
  - `script`
  - script names after `script -run`
  - active prompt suggestions only if script asks for constrained choices later

### Re:T-UI Script Phase 3 - Control Flow, Carefully

- Do not add loops in the first scripting release.
- Consider only after phase 1 is stable:
  - `if <var> == <value>`
  - `else`
  - `end`
  - `choice <name> "Prompt" ["A","B","C"]`
  - `abort "<message>"`
- Keep failure behavior simple:
  - command failure stops the script by default
  - add `continue_on_error` only if users need it
- Sensitive actions should support confirmation:
  - uninstall
  - destructive aliases
  - broad broadcasts
  - future explicit service calls

### Re:T-UI Script Phase 4 - Modules And Suggestions Integration

- Allow scripts to update module text through existing module primitives.
- Allow scripts to emit output through the same terminal output path.
- Consider script-owned suggestion chips only after script execution is stable.
- Keep script modules and Re:T-UI Script separate concepts:
  - script modules render status/output surfaces
  - Re:T-UI Script orchestrates launcher commands and prompts
- Avoid adding arbitrary code loading. Re:T-UI Script should only call Re:T-UI commands and approved primitives.

### Re:T-UI Script Phase 5 - Sharing And Trust

- Add export/import only after the language is stable.
- Shared scripts must be plain-text inspectable before import.
- Import should show:
  - script name
  - commands used
  - intents/broadcasts used
  - sensitive actions detected
- Consider a trust warning for scripts that use:
  - broadcasts
  - uninstall/settings
  - callbacks/tokens
  - future service intents
- Community sharing target:
  - aliases
  - intent examples
  - Re:T-UI scripts
  - modules
  - themes/presets

## Completed In v325

- Brightness permission flow
  - Added `WRITE_SETTINGS` to the manifest.
  - Updated the brightness command to deep-link into Re-TUI's own "Modify system settings" permission screen.

- Package name / side-by-side install
  - Confirmed active app id is `com.dvil.tui_renewed`.
  - Namespaced the custom `RECEIVE_CMD` permission to `${applicationId}` to avoid collision with the original upstream launcher.

- Themer navigation stacking
  - The settings/Themer terminal surface now forces an opaque internal background.
  - The outside overlay can still let wallpaper bleed through without showing prior Themer screens under the current one.

- Module / terminal polish
  - Added separate theme keys for music and notification module border/text colors.
  - Added auto-color support for those new keys.
  - Module border labels now paint an opaque surface mask so border lines do not show through transparent themes.
  - Music song line now uses `Title:` instead of repeating `Now Playing:`.
  - Toolbar app drawer icon background was aligned with the other toolbar buttons.

- Branch/release
  - Merged `codex/timer-stopwatch` into `master`.
  - Published Firebase build `325`.
  - Created `codex/termux-integration` from the merged `master`.

## Completed On `codex/termux-integration`

- Removed the abandoned native Android widget/dashboard experiment.
  - Dropped the second ViewPager dashboard page.
  - Removed the `dashboard`/`widgets` command surface.
  - Kept page 0 focused on Re-TUI-owned music and notification terminals.

- Tightened the music terminal controls.
  - Stabilized the `PREV`, `PLAY/PAUSE`, and `NEXT` row with weighted button widths.
  - Reduced oversized title/singer text while keeping controls readable.

- Normalized terminal border behavior for current module surfaces.
  - `enable_dashed_border=false` now removes the terminal-style module border.
  - `enable_dashed_border=true` with gap `0` remains the solid-border path.

- Started the layered home terminal cutover.
  - Output/input/suggestions now live in a bottom overlay tray instead of pushing the home module layer.
  - Added an `[ OUTPUT ^ ]` tray handle for expanding and collapsing output history.
  - Back now collapses expanded output before normal launcher back behavior.

- Started Termux setup guidance.
  - Added `termux -setup` to the command surface and suggestions.
  - The Termux console now prints the required `allow-external-apps=true` setup, script folder convention, script alias example, and permission reminder.
  - Added first-pass wiki docs for Termux setup, script aliases, and safe run flow.

## High Priority

- Remove Android widget/dashboard experiment (done on `codex/termux-integration`; pending merge)
  - Drop the native Android `AppWidgetHost` surface and the current `widgets` command path.
  - Keep the idea of user-extensible panels, but rebuild it as Re-TUI-owned modules.
  - Do not support KWGT/native Android widgets for now; they break visual consistency and add provider/OEM instability.

- Fix music module control sizing (done on `codex/termux-integration`; pending wider device testing)
  - Restore readable `PREV`, `PLAY/PAUSE`, and `NEXT` text while keeping the compact widget height.
  - Make the control row stable across narrow devices and large user font settings.
  - Keep the visualizer as the background layer and avoid button squish when text scale changes.

- Normalize border behavior (partially done; continue applying to settings/app drawer/Termux/module surfaces as new surfaces change)
  - `enable_dashed_border=false` should mean no terminal-style border on all Re-TUI terminal surfaces.
  - `enable_dashed_border=true` with gap `0` should mean a solid terminal border.
  - `enable_dashed_border=true` with gap greater than `0` should mean dashed border.
  - Apply this consistently to music, notifications, input/output, settings, app drawer, Termux console, and future modules.

## Phase 1 - Layered Home Terminal

- Split the home screen into two conceptual layers:
  - Layer 1: status, ASCII art, module dock, active module, wallpaper.
  - Layer 2: output, input, suggestions, toolbar.
- Convert output/input/suggestions into a terminal tray overlay rather than a layout section that pushes other content.
- Add output expand/collapse behavior:
  - Collapsed: compact output history plus input and suggestions.
  - Expanded: scrollable output history for reviewing logs.
  - Back button collapses expanded output before normal back behavior.
- Keep input focus stable when expanding/collapsing output.
- Ensure expanded output intentionally blocks module interaction, while collapsed output leaves Layer 1 usable.

## Phase 2 - Re-TUI Native Modules

- Replace the native widget/dashboard idea with Re-TUI-owned modules.
- Add a slim module dock below the ASCII art.
  - Example dock items: `MUSIC`, `NOTIF`, `TIMER`, `CAL`.
  - Tapping one module closes the previous module and opens the selected one.
  - Active module item is highlighted.
- Add close control to module windows.
- Start with built-in modules:
  - Music visualizer/player module.
  - Notification terminal module.
  - Timer/stopwatch/pomodoro module.
  - Calendar module, monthly view first.
- Keep module command surface command-first:
  - `module -ls`
  - `module -show music`
  - `module -hide music`
  - `module -close`
  - `module -dock add server`
  - `module -dock remove music`
- Module visibility rules:
  - Hiding/removing from dock does not delete the module from the registry.
  - Deleting/removing from registry is a separate explicit command.

## Phase 3 - Script Modules

- Status: first implementation done; pending phone UAT.
- Add script-backed custom modules after built-in modules are stable.
- Start with a strict text-output contract:
  - Termux runs the script.
  - Re-TUI renders stdout inside a terminal module box.
  - No arbitrary Java/Kotlin/plugin code loading.
- Initial commands:
  - `module -add server termux:/data/data/com.termux/files/home/retui/server.sh`
  - `module -refresh server`
  - `module -show server`
  - `module -hide server`
  - `module -rm server`
- Dock commands are append/remove only:
  - `module -dock add server`
  - `module -dock remove music`
- Keep script files owned by Termux/user storage; Re-TUI should never delete the source script.
- Later enhancement: controlled Re-TUI markup primitives:
  - text color spans
  - progress bars
  - tables
  - action buttons
  - refresh interval

## Phase 4 - Termux Integration

- Keep the current `termux` console command and `-s` script alias scope.
- Add `termux -setup`.
  - First pass done: explain required Termux setting, script folder convention, script alias example, and Android permission prompt flow.
  - Helper docs done: `retui-helper.sh` with `retui_output` and `retui_module` examples.
- Callback authorization foundation is started.
  - `retui-token -status`
  - `retui-token -show`
  - `retui-token -rotate`
  - `retui-token -on`
  - `retui-token -off`
  - Callbacks remain disabled by default until a token is explicitly created/enabled.
- Callback receiver is token-gated and intentionally narrow.
  - Safe first actions: `output`, `notify`, `module_set`.
  - `module_set` now updates custom script modules.
  - Hold dangerous/general `command` execution for a later opt-in "danger mode"; do not add it without a design pass.
- Example helper API:
  - `retui output "Backup complete"`
  - `retui notify "Server down" "prod-api failed health check"`
  - `retui module set server "HTTP OK\nCPU 42%"`
  - `retui module refresh server`

## Phase 5 - Tasker Integration

- Reuse the same callback API built for Termux.
- Document Tasker "Send Intent" templates.
- Do not require Termux for Tasker integration.
- Useful Tasker-driven module updates:
  - Wi-Fi/VPN state
  - battery profile
  - location/home-away mode
  - Bluetooth device state
  - calendar/focus mode
  - smart-home shortcuts

## Phase 6 - Webhook Strengthening

- Revisit webhook storage and command UX.
- Add better inspection and test commands.
- Make webhook output/callback behavior consistent with modules.
- Consider module and Termux callback triggers for webhook dispatch.

## Deferred / Watchlist

- True cutout borders for floating tabs
  - Current module tabs use an opaque surface mask to hide the parent border line.
  - Research found Material `TextInputLayout` has a similar outlined floating-label cutout behavior, but it is text-field-specific and not a good visual fit for Re-TUI.
  - Preferred future approach: build a small custom terminal border view/drawable that draws the border in segments and skips the tab rectangle.

## Next Module Roadmap - Interactive Terminal Tools

- Position modules as small terminal instruments, not passive panels.
- Add module inspection:
  - `module -inspect <name>`
  - show type, path, dock state, active state, last refresh time, last exit code, stderr summary, cached suggestions, refresh policy.
- Add module logs:
  - `module -logs <name>`
  - `module -logs <name> -clear`
  - keep last stdout, stderr, exit code, and timestamp for script-backed modules.
- Add refresh controls:
  - `module -refresh-all`
  - `module -refresh-dock`
  - `module -interval <name> 60s|5m|off`
- Extend script metadata carefully:
  - `::status ok|warn|error|idle`
  - `::badge <text>`
  - `::refresh 60s`
  - keep `::title`, `::body`, and `::suggest`.
- Make parsed suggestion modes real:
  - `command`: run a normal Re:T-UI command.
  - `termux-run`: dispatch a Termux script through TBridge.
  - `callback`: send a narrow callback-style action through the bridge.
- Add module prompt sessions:
  - modules can ask for values step by step while they temporarily own the input loop.
  - first prompt types: `text`, `date`, `time`, `choice`, `confirm`.
  - example flow: reminder add -> ask title -> ask date -> ask time -> confirm -> schedule.
  - while a prompt session is active, normal launcher commands should not steal the input unless user cancels.
- Add native reminder support as the first real prompt-backed module:
  - `reminder -add`
  - `reminder -edit <id>`
  - `reminder -rm <id>`
  - schedule Android notification/alarm natively; scripts should request scheduling, not own Android alarm reliability.
- Add module templates:
  - `module -new server`
  - `module -new reminder`
  - `module -new webhook`
  - write starter Termux scripts into a known Termux folder.
- Add module profiles:
  - `module -profile save work`
  - `module -profile apply work`
  - save/restore dock layouts for different workstation contexts.

## Next TBridge Roadmap - Termux Runtime Bridge

- Reposition TBridge away from file navigation. Re:T-UI Files owns file management.
- Position TBridge as:
  - Termux health diagnostics
  - script runtime
  - script-module transport
  - callback/token diagnostics
  - automation helper installer
- Keep and clarify:
  - `tbridge -status`
  - `tbridge -doctor`
  - `tbridge -setup`
  - `tbridge -probe`
- Retire TBridge file listing commands from the public command surface:
  - `tbridge -ls`
  - `tbridge -dirs`
  - `tbridge -files`
  - use `files` for browsing and `ls/open/share` with `file_backend=termux` for quick bridge-backed actions.
- Add helper installer later:
  - `tbridge -install-helper`
  - install a small `retui` helper into Termux.
  - helper examples:
    - `retui output "Backup complete"`
    - `retui notify "Build complete"`
    - `retui module set server "ONLINE"`
    - `retui module refresh server`
- Add diagnostics:
  - `tbridge -token`
  - `tbridge -test`
  - `tbridge -test-callback`
  - `tbridge -test-module <name>`
- Make TBridge the execution path for future module `termux-run` suggestions and prompt-backed script modules.
  - Estimate: roughly 1 focused day for one reusable custom view/drawable, plus another pass to wire and test it across music, notifications, output tray, app drawer, settings, and Termux.
  - Keep the mask approach for now unless transparent tab backgrounds become a real user pain point.

- Native Android widget hosting
  - Deferred intentionally.
  - Reconsider only if there is strong demand and a stable constrained UX.

- Arbitrary Java/Kotlin user modules
  - Avoid for now due to security, crash, Play policy, and support risk.
  - Prefer script modules with controlled Re-TUI-rendered primitives.

- Per-app notification coloring
  - Deferred unless community demand is strong.
  - Current notification terminal should remain visually consistent with the theme.

## Integrations

- Termux integration
  - Current active branch: `codex/termux-integration`.
  - Current near-term work should focus on safe `termux -setup` polish and script-run UX only.
  - Callback auth and callback intents are intentionally paused until design discussion.
