# Re:T-UI Phase Test Book

Use this as the phone test pass for the workstation rollout. Test on a clean install when possible, then repeat the focused regression checks on your daily setup.

## Phase 1 - Help, Identity, First Wow

### Clean Install

- Install a fresh build with no saved user presets.
- Open the launcher and do nothing for a moment.
- Confirm the module dock is visible but no module panel is open by default.
- Confirm blank input suggestions include `wallpaper -auto`.
- Confirm suggestions below the input are visible without needing to close notifications/music/timer/calendar first.
- Confirm it does not auto-run without user action.
- Tap/run `wallpaper -auto`.
- Confirm wallpaper-derived colors apply cleanly and the launcher still feels terminal-first.
- Confirm built-in shipped presets still exist where expected.
- Confirm built-in shipped presets do not suppress the `wallpaper -auto` suggestion on first install.

### Help Flow

- Type `help` but do not press enter yet.
- Confirm the suggestion row shows workstation quickstart actions:
  - `apps -l`
  - `alias -add`
  - `apps -hide`
  - `wallpaper -auto`
  - `preset -save`
  - `module -ls`
- Confirm random app/contact fuzzy matches do not replace these quickstart actions once the input is exactly `help`.
- Run `help`.
- Confirm the first visible section feels like a workstation quickstart, not onboarding hand-holding.
- Confirm it suggests practical actions:
  - launch app
  - create alias
  - hide app
  - apply theme / wallpaper auto
  - add or show module
- Run or inspect the linked commands from help:
  - `apps -l`
  - `alias`
  - `apps -hide`
  - `wallpaper -auto`
  - `module`
- Confirm `help` still lists normal commands after the quickstart.

### Regression

- Confirm old help command behavior still works for command-specific help.
- Confirm normal launch suggestions still appear when no module is active.
- Confirm no first-run modal or tutorial blocks the terminal.

### Guide Flow

- Run `guide`.
- Confirm it prints available paths and does not open a blocking overlay.
- Run `guide -start basics`.
- Confirm the output shows a progress bar, the current step, and controls:
  - `guide -next`
  - `guide -back`
  - `guide -off`
- With the input empty, confirm guide suggestions show the current step command and guide controls.
- Type a normal app or command prefix while the guide is active.
- Confirm the suggestion row still shows only the guide step command plus `guide -next`, `guide -back`, and `guide -off`.
- Tap/run the current step command.
- Confirm the guide advances to the next step in the empty-input suggestions.
- Run `guide -off`.
- Confirm normal blank input suggestions return.
- Run `guide -resume`.
- Confirm it returns to the saved path and step instead of restarting at 1/4.
- Run `guide -reset`.
- Confirm `guide -start basics` starts from 1/4 again.

## Phase 2 - Search And Suggestions 2.0

### App Search

- Type the first few letters of an installed app.
- Confirm app suggestions still launch directly.
- Tap an app suggestion.
- Confirm it opens the app, not an app-management action list.
- Run `apps -l`.
- Confirm package/app listing still works.
- Run or dry-check the existing app management commands:
  - `apps -hide`
  - `apps -show`
  - `apps -st`
  - `apps -ps`
  - `apps -mkgp`
  - `apps -addtogp`
  - `apps -lsgp`
- Confirm app management remains deliberate and command-led.

### Contact Search

- Type a contact name directly at root input.
- Confirm contacts do not appear before normal command/app suggestions.
- Type `contacts `.
- Confirm contact names appear in suggestions.
- Type `contacts <partial name>`.
- Confirm matching contact names appear in suggestions.
- Tap the contact suggestion.
- Confirm the prompt becomes `contacts <selected name>`.
- Confirm the next suggestions become contact actions:
  - `call <number>`
  - `contacts -l <number>`
  - `contacts -edit <number>`
- Tap `call <number>` only if you are ready to place a call.
- Confirm contacts with multiple numbers use the selected/default number.

### Suggestion Boundaries

- Type a normal command prefix such as `apps -`.
- Confirm app/contact root suggestions do not interfere with command parameter suggestions.
- Type an alias name.
- Confirm aliases still appear and execute as before.
- Type nonsense text.
- Confirm fuzzy results are helpful but not noisy.

## Tray Scroll Fix

- Generate enough terminal output to exceed the visible output area.
- Expand the output tray.
- Try scrolling upward slowly.
- Try a fast flick upward.
- Confirm the tray does not snap back to the bottom.
- Collapse and expand the tray again.
- Confirm it scrolls to the bottom only on intentional expand/refocus, not while manually scrolling.
- Rotate the phone if supported and repeat once.

## Phase 3 - Native Modules

### Built-In Module Suggestions

- Show the timer module.
- With empty input, confirm module suggestions replace normal suggestions.
- Confirm timer suggestions include useful actions such as:
  - `+5m`
  - `+15m`
  - `25m`
  - `stop`
  - `status`
  - `pomodoro`
- With no timer running, tap `+5m`.
- Confirm it starts a 5 minute timer.
- With that timer running, tap `+15m`.
- Confirm it adds 15 minutes to the existing timer instead of prompting.
- Start typing any normal input.
- Confirm normal typing wins and module suggestions disappear.

- Show the music module.
- Confirm empty input shows music actions such as `prev`, `play`, `next`, `info`, `stop`.

- Show the notifications module.
- Confirm empty input shows notification actions such as `prev`, `next`, `reply`, `open`, `access`, `rules`, `filters`.

- Show the notes module.
- Confirm empty input shows actions such as `edit`, `list`, `todo`, `copy`, `clear`.
- Run `notes -add TODO: module smoke test`.
- Run `module -show notes`.
- Confirm the module shows the new TODO note without creating a separate task store.
- Run `notes -ls`, then remove the test note with `notes -rm <index>`.

- Show the RSS module with no feeds configured.
- Confirm the module shows setup guidance and does not attempt a network call by itself.
- Add a Reddit feed only when live network testing is acceptable:
  - `rss -add 1 900 https://www.reddit.com/r/android/.rss`
  - `rss -frc 1`
  - `module -show rss`
- Confirm the module shows cached item titles and empty-input suggestions include `list`, `latest`, `refresh`, `info`, `reddit`, `file`.

- Show the weather module.
- If weather is disabled, confirm the panel asks for `tuiweather -enable` or `tuiweather -tutorial`.
- If weather is enabled and configured, run `tuiweather -update` and confirm the module refreshes with the latest cached status.
- Confirm empty-input suggestions include `update`, `enable`, `disable`, `setup`, `key`.

- Close the active module.
- Confirm normal blank-input suggestions return.

### Script Module Basics

- Create a Termux script that prints plain text only.
- Add it as a module.
- Run `module -refresh <name>`.
- Run `module -show <name>`.
- Confirm plain stdout appears as the module body.
- Confirm the script source file remains owned by Termux/user storage.
- Remove the module with `module -rm <name>`.
- Confirm the source script is not deleted.

### Script Module Metadata

Create or update a script to output:

```text
::title Server
::body prod-api ONLINE
::suggest refresh | command | module -refresh server
::suggest logs | command | termux -run logs
```

- Refresh the module.
- Show the module.
- Confirm the title displays as `Server`.
- Confirm the body displays `prod-api ONLINE`.
- Confirm raw metadata lines are not shown in the body.
- With empty input and the module active, confirm `refresh` and `logs` appear only if they are command-mode suggestions.
- Tap `refresh`.
- Confirm it runs `module -refresh server`.
- Confirm `termux-run` and `callback` modes do not execute yet unless represented as a normal `command` action.

### Script Metadata Edge Cases

- Output only normal text and no metadata.
- Confirm body still renders.
- Output `::title` with no body.
- Confirm the module does not crash.
- Output malformed suggestions:
  - empty label
  - empty command
  - missing separators
- Confirm invalid suggestions are ignored or safely normalized.
- Output multiple `::body` lines.
- Confirm they render on separate lines.
- Output unknown `::metadata`.
- Confirm it does not show in the body.

## Cross-Phase Regression

- Confirm `help` still works while a module is active.
- Confirm typing a command while a module is active gives command suggestions, not module suggestions.
- Confirm app launching still works after using modules.
- Confirm contact suggestions still work after using modules.
- Confirm wallpaper/preset behavior still works after using modules.
- Confirm the output tray scroll fix still holds after module output is printed.

## Phase 5 - Workflow Aliases

- Create a simple chained alias:
  - `alias -add testflow module -show timer; timer -status`
- Run `testflow`.
- Confirm both commands execute in sequence.
- Run `alias -ls`.
- Confirm the alias is visible and inspectable.
- Remove it with `alias -rm testflow`.
- Confirm no `recipe` command is needed for this workflow.

## Phase 6 - Automation Gap Audit

- Open or inspect `docs/wiki/Automation-and-Chaining.md`.
- Confirm it lists current automation surfaces:
  - workflow aliases
  - Termux
  - callbacks
  - webhooks
  - Android shortcuts
  - notifications/reply
  - timer, stopwatch, pomodoro
- Confirm callback docs keep the boundary narrow and do not promise arbitrary external command execution.
- Confirm the remaining gaps are framed for later discussion:
  - conditions
  - confirmations
  - scheduling
  - failure handling
  - external triggers

## Toolbar Command Buttons

- Open `settings`.
- Go to Personalization.
- Open Toolbar Buttons.
- Configure slot 1 with an icon and a simple app name such as `whatsapp` or another installed app.
- Confirm the slot is off until enabled.
- Enable the slot and return to the launcher.
- Tap the toolbar button.
- Confirm it behaves the same as typing that app name into the prompt.
- Change the command to an alias or full command such as `notifications -open`.
- Confirm the toolbar button submits that text through normal command execution.
- Long-press the toolbar button.
- Confirm it opens the Personalization settings surface.
- Clear both slots.
- Confirm no custom toolbar buttons remain visible by default.

## Intent Router MVP

- Run `help intent`.
- Confirm usage covers `-view`, `-activity`, `-broadcast`, `-uri`, and `-check`.
- Run `intent -check -view https://example.com`.
- Confirm at least one browser/handler is listed, or a clean "No handlers found" message appears.
- Run `intent -view https://example.com`.
- Confirm Android opens a browser or chooser.
- Run `intent -check -activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "hello from retui"`.
- Confirm share-capable handlers are listed.
- Run `intent -activity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "hello from retui"`.
- Confirm Android opens a share target or chooser.
- Run `intent -broadcast -a com.example.TEST`.
- Confirm Re:T-UI refuses the broad implicit broadcast and asks for `-p`, `-n`, or `--unsafe-implicit`.
- Create an alias:
  - `alias -add intent-test intent -view https://example.com`
- Run `intent-test`.
- Confirm the alias dispatches the intent.
- Remove it:
  - `alias -rm intent-test`

## Native File Navigation

- Run `pwd`.
- Confirm it prints the current Re:T-UI file path.
- Type `cd ` and pause.
- Confirm suggestions show folders from the current path and include `..`.
- Tap a folder suggestion.
- Confirm the input fills the folder path after `cd`.
- Run the command.
- Confirm the input hint/path changes to the selected folder.
- Run `ls`.
- Confirm folders are listed with a trailing `/`.
- Run `cd ..`.
- Confirm the path moves up one level.
- Run `open <file>` or `share <file>` from a folder that contains a known file.
- Confirm existing file commands still resolve paths relative to the new `cd` location.
- Run `shell pwd`.
- Confirm shell output prints the embedded shell path.
- Run `shell cd ..`.
- Confirm the input hint/path updates after the shell changes directory.
- Type an unknown shell-like command without `shell`, such as `grep`.
- Confirm Re:T-UI prints command-not-found/help guidance instead of silently running the embedded shell.
- Run `config -set shell_requires_prefix false` only for compatibility testing.
- Confirm unknown shell-like input can fall through only when that behavior setting is off.

## Phase 4 - Deliberate App Drawer

- Open the app drawer.
- Confirm the drawer still reads as a terminal surface over the wallpaper.
- Confirm app rows are not squeezed by a left-side group rail.
- Confirm the alphabet rail remains on the right.
- Confirm group tabs are on the bottom horizontal rail.
- Tap `ALL`.
- Confirm all visible, non-hidden apps appear.
- Create or use an existing group made through `apps -mkgp` / `apps -addtogp`.
- Tap that group tab in the drawer.
- Confirm only apps in that group appear.
- Confirm hidden apps stay hidden.
- Confirm tapping an app still launches it directly.
- Confirm there is no automatic grouping or casual app-management UI in the drawer.

## Future Phase Watchlist

These are not done yet, but keep notes when testing:

- Phase 4: whether app drawer group controls feel reachable one-handed.
- Phase 5: which repeated workflows should become documented alias examples.
- Phase 6: which automation gaps matter enough to design.
- Phase 7: which profile/setup tasks feel too tedious from the input line.
- Phase 8: where docs, permissions, and trust messaging feel unclear.

## Pass Criteria

- The launcher remains terminal-first and direct.
- Suggestions help without feeling like a casual launcher overlay.
- Apps launch directly unless the user explicitly enters app-management commands.
- Modules can temporarily own suggestions only when active and input is empty.
- Script modules are useful without arbitrary plugin loading.
- No phase introduces surprise automation or hand-holding.
