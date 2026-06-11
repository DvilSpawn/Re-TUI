# Re:T-UI Manual Testbook

This is the durable home for manual launcher validation notes. Do not place
runtime screenshots, XML dumps, traces, or temporary test notes under
`app/src/**`; those belong in ignored output folders such as `perf-results/`,
`build/`, or `app/build/`.

## Build Checks

Run these before release-candidate work:

```sh
./gradlew :app:assemblePlaystoreDebug
./gradlew :app:assembleFdroidDebug
./gradlew :app:lintPlaystoreDebug
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
- `Discord` resolves to `https://discord.gg/n6zsVYuV`
- `Reddit` resolves to `https://www.reddit.com/r/RE_TUI_launcher/`
- `Send Feedback` opens email to `DvilSpawn@gmail.com`
- `Backup`, `Create Shareable Configuration`, `Restore`, `Rate the App`,
  `Learn More`, and `View Crash Log` still open their existing flows.

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
- `:reconnect` restarts the socket bootstrap and refreshes the pane.
- Existing `:new [name]`, `:prev`, `:next`, `:refresh`, `:home`, key tray,
  and horizontal window swipe behavior still work.
