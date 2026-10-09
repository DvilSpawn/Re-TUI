# Re:T-UI Manual Testbook

This is the durable home for manual launcher validation notes. Do not place
runtime screenshots, XML dumps, traces, or temporary test notes under
`app/src/**`; those belong in ignored output folders such as `perf-results/`,
`build/`, or `app/build/`.

## Build Checks

Run these before release-candidate work:

```sh
./gradlew :app:validateSourceInputs
./gradlew :app:testFdroidDebugUnitTest :app:testPlaystoreDebugUnitTest --tests '*'
./gradlew :app:assembleFdroidDebug :app:assemblePlaystoreDebug
./gradlew :app:assembleFdroidDebugAndroidTest :app:assemblePlaystoreDebugAndroidTest
./gradlew :app:lintFdroidDebug :app:lintPlaystoreDebug --no-daemon --max-workers=1
./gradlew :app:assembleFdroidRelease :app:assemblePlaystoreRelease
```

## Source Guardrail Check

The app build validates packaging inputs before Android build tasks run.

1. Confirm the clean source tree passes:

   ```sh
   ./gradlew :app:validateSourceInputs
   ```

2. Create a temporary bad source artifact:

   ```sh
   touch app/src/main/assets/TESTBOOK.md
   ```

3. Confirm the guardrail fails with a clear message:

   ```sh
   ./gradlew :app:assemblePlaystoreDebug
   ```

4. Remove the temporary artifact:

   ```sh
   rm app/src/main/assets/TESTBOOK.md
   ```

## Settings Hub Smoke

Open the launcher and run:

```text
settings
```

In `System & Support`, verify:

- `GitHub` resolves to `https://github.com/DvilSpawn/Re-TUI.git`
- `Discord` resolves to `https://discord.gg/dF2QXWB3ee`
- `Reddit` resolves to `https://www.reddit.com/r/RE_TUI_launcher/`
- `Send Feedback` opens email to `DvilSpawn@gmail.com`
- `Backup`, `Create Shareable Configuration`, `Restore`, `Rate the App`,
  `Learn More`, and `View Crash Log` still open their existing flows.

## Spaces Smoke

Open the launcher and run:

```text
space -ls
```

Verify the default Space exists and is marked active:

```text
* Space 1 [space-1]
```

Create a second Space from the current launcher state:

```text
space -new Work
space -ls
```

Verify `Work [space-2]` appears. Then switch away and back:

```text
space -switch Work
space -switch space-1
space -ls
```

Verify `Space 1` is active again, and confirm the on-device Space manifests
include provider entries for XML config, launcher files, launcher preferences,
and module preferences:

```sh
adb shell 'for f in /storage/emulated/0/Android/data/com.dvil.tui_renewed/files/Re-T-UI/spaces/*/manifest.properties; do echo "$f"; cat "$f"; done'
```

Also verify no incomplete swap folders remain:

```sh
adb shell 'find /storage/emulated/0/Android/data/com.dvil.tui_renewed/files/Re-T-UI/spaces -maxdepth 1 \( -name ".*staging" -o -name ".*previous" \) -print'
```

## Termux Tmux Workspace Smoke

Prerequisites in Termux:

```sh
pkg install curl tmux socat -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui/install.sh | sh
```

Enable the workspace button in Re:T-UI:

```text
config -set show_tmux_workspace_button true
```

Open the tmux workspace from the toolbar and verify:

- `:status` shows Termux install state, `RUN_COMMAND`, socket state, geometry,
  and launcher counts.
- `:launch` lists built-in launchers plus any saved launchers.
- `:launch shell` opens a named shell tmux window.
- `:launch logs` opens `tail -f ~/.retui/bridge.log`.
- `:save test echo retui-workspace-ok` saves a launcher.
- `:launch test` creates a `test` tmux window and sends the command.
- `:rm test` removes the saved launcher.
- From the main launcher prompt, `tmux launch mc` opens the workspace and
  launches the `mc` workspace launcher.
- Run `tmux launch mc` again and verify it focuses the existing `mc` window
  instead of creating another `mc` window.
- From the main launcher prompt, `tmux switch 2:bash` opens the workspace and
  selects tmux window `2` when that window exists.
- `alias -add MC tmux launch mc`, then `MC`, opens the workspace launcher.
- `:reconnect` restarts the socket bootstrap and refreshes the pane.
- Existing `:new [name]`, `:prev`, `:next`, `:refresh`, `:home`, key tray,
  and horizontal window swipe behavior still work.

## Combined Release Acceptance

Builds and unit tests do not close the following device gates. Record an observed
outcome for each row, retaining failed runs and any untested hardware separately.
Use one device owner and one installer at a time. Preserve configuration, presets,
private preferences and existing Termux sessions before testing; never clear data
or uninstall a data-bearing target to resolve a deployment problem.

- **Workspace lifecycle:** enter through toolbar and terminal command; test single,
  top/bottom and left/right layouts, focused-tile input, swipe, Home/reopen and
  recreation without killing shells. An initial launch command must execute once,
  including after recreation and reopening. Verify input again after reopening.
- **Workspace appearance:** change colors and font/size, return to the existing
  workspace, and inspect terminal, key tray, input and chrome together. Preserve
  drafts, cursor/selection, chosen windows and running jobs.
- **Workspace input:** ordinary Unicode, latched modifiers with non-ASCII input,
  Enter/backspace, local colon commands and both key-tray modes. Report real
  physical-keyboard checks separately from IME or injected-key tests.
- **Breath out:** verify wallpaper-only rendered pixels after timeout, consumed
  wake touch/key/Back, foreground/focus handling, and live enable/timeout updates
  in the launcher and detached workspace. Refresh with an already populated
  launcher draft and verify its text and selection survive. Calculator, help and
  community are separate Activities, not additional idle-fade targets.
- **Calculator/help:** untouched initial floating layout in portrait and landscape;
  drag the title and all four corners, maximize/float/reset, then recreate. Check
  independent geometry, every keypad layer, prompt visibility, editing, `ans`,
  help/Back navigation, live appearance and retained draft/history/selection.
- **Portable presets:** export and inspect a `.retui` file, cancel and confirm
  import, verify unique preset saving without applying settings, reject personal
  backups even when renamed, and check recipient access before/after grant
  revocation. Do not infer messaging-provider coverage from a fixture provider.
- **Unlock counter:** observe a real lock/wake/authentication cycle and verify one
  increment; repeat across launcher pause/refresh. Do not count ordinary resume
  or waking an already unlocked device as an unlock test.
- **Community:** use controlled accounts to verify login/logout, literal message
  input, pagination/unread behavior, failed/ambiguous delivery and draft retention.
  Test restored/offline cache at the 24-hour boundary, moderation removal and
  account isolation. Live production fixtures require separate authorization and
  exact recorded cleanup; loopback tests do not prove production availability.

Before publishing, verify release APK package/version/signature and required native
libraries, review privacy/retention disclosures, and compare the installed candidate
with the intended artifact. Restore test-modified state and compare complete file
sets and bytes. An outstanding device gate is a release blocker, not a passing row.
