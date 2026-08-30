# Re:TUI Console Launcher

Re:TUI is my continuation of the original T-UI Launcher: a terminal-style Android home screen that still expects you to type, experiment, and make the phone feel like your own.

The goal is not to turn T-UI into a conventional launcher with a terminal theme painted over it. I want to keep the command-first character that made the original special while making it practical on current Android versions. That means modern storage, safer integrations, touch-friendly panes where they genuinely help, and physical-device testing instead of assuming the emulator tells the whole story.

---

## 🚀 What Re:TUI Includes

Re:TUI supports Android 6.0 and newer and targets current Android APIs.

> **First-install note:** If background transparency does not take effect immediately, type `restart` and press Enter.

New users can follow the illustrated [Getting Started guide](docs/GETTING_STARTED.md)
for the Basic and Advanced startup choices.

### ⌨️ Commands and Launcher Tools
*   **`username [user] [device]`**: Instantly customize your terminal prompt. Changes both the username and device name and reloads the UI to apply.
*   **`preset`**: Save, apply, list, and remove appearance presets with `preset -save`, `preset -apply`, `preset -ls`, and `preset -rm`. The same controls are available from **Settings → Appearance → Presets**.
*   **`webhook`**: A scalable Webhook system featuring template-based HTTP POST requests.
    *   **Substitution:** Supports `%n` parameter substitution (e.g., `%1` for the first argument).
    *   **History:** Automatically tracks the last 5 unique sets of arguments for each webhook.
    *   **Suggestions:** Provides history-based autocomplete for `webhook [name]` arguments.
*   **`post [url] [body]`**: Send raw HTTP POST requests directly from the terminal.
*   **`module`**: Native, Lua, and Termux-backed modules are the recommended way to add launcher panels and scripted workflows.
*   **`guide`**: A non-blocking command-first walkthrough. Use `guide -startup-test` to preview the first-run startup pane without changing the current configuration.
*   **`space`**: Create, duplicate, rename, save, list, switch, and remove launcher Spaces, each with its own snapshot of launcher settings, module state, and command-facing configuration.
*   **`reminder`**: Open the reminder pane or manage reminders directly from the terminal.
    *   `reminder -add <task name> <dd/mm/yy> <HH:mm>` creates one using 24-hour time.
    *   `reminder -ls` lists reminders with their IDs.
    *   `reminder -rm <id>` removes one.
*   **`tuiweather`**: Configure and refresh the native Weather module without granting Re:TUI location permission. Use `tuiweather -set_location <place or lat,lon>` and Re:TUI will reuse that saved location.
*   **BusyBox manager removed**: Re:TUI no longer downloads BusyBox; use `shell` for Android's built-in shell and Termux for maintained Linux tooling.
*   **ASCII Art System**: Display static or animated ASCII on the dashboard. The settings hub handles text import, frame animation, timing, placement, and landscape visibility.

### ✨ Enhanced Features
*   **Termux Execution Layer:** Keep Linux tooling, scripts, and custom modules in Termux while Re:TUI stays focused on launcher UI and command routing.
*   **Termux Tmux Workspace:** Optional tmux workspace for persistent TUIs, quick launchers, saved launch commands, prompt aliases, reconnect, and status diagnostics.
*   **Expanded Status Bar:** Support for up to 10 status lines (tv0-tv9) for richer information display.
*   **Reminder Pane:** The dashboard module is a quick preview; the separate pane handles adding, editing, and removing reminders.
*   **Native Weather:** The native `weather_native` module uses the location you choose, remains separate from Termux/wttr.in weather modules, and does not depend on Android location permission. Forecast data is provided by [MET Norway Locationforecast](https://api.met.no/weatherapi/locationforecast/2.0/documentation).
*   **PI-safe Shareable Presets:** Shareable configurations include canonical colors, suggestion styling, layout, and typed behavior settings from `theme.xml`, `suggestions.xml`, `ui.xml`, and `behavior.xml`. Free-form text, names, commands, paths, locations, custom frame images, hidden XML content, and local preset-name metadata stay on the device. Imports enter the preset library instead of immediately overwriting the active launcher, and saved presets can be removed.
*   **Optional Launcher Sounds:** A bundled sound pack can be enabled from **Settings → Behavior → Sounds**. Boot, command input, success, failure, notification, reminder, and timer sounds can each be switched on or off independently.

---

## Tasker Integration

Re:TUI includes an optional native Tasker action plugin. Enable it in **Settings → Integrations → Tasker Integration**, then add **Plugin → RETUI Action** to a Tasker task.

Supported actions apply a preset, set a theme color, show or refresh a module, update an existing script module's text, print terminal output, and switch Re:TUI Spaces. Text fields accept Tasker variables, including the Space name. The integration is disabled by default and does not expose arbitrary Re:TUI commands, shell access, calls, messages, or destructive actions.

Re:TUI can also start an existing named Tasker task. Grant the Tasker run-task permission when enabling the integration, and enable **Allow External Access** in Tasker:

```text
tasker Work
tasker -run "Evening Setup"
```

Tasker is optional; Re:TUI works normally when it is not installed.

## 🐧 Termux Integration

For a full Linux environment, use Termux as the execution layer:

1.  Install Termux.
2.  Run `termux-setup-storage` in Termux.
3.  Enable `allow-external-apps=true` in Termux properties.
4.  Run `tbridge -doctor` in Re:TUI to verify the bridge.
5.  Enable `show_tmux_workspace_button` when you want the optional persistent tmux workspace.
6.  Use `termux`, `module`, and `files` for scripts, modules, and file workflows. `files -search <name> [type]` searches in Re:TUI FM, while `files -open <directory>` opens a directory relative to the launcher path.

This keeps the launcher lean for Play Store builds while preserving power-user Linux workflows through an app that is designed to own them. The old BusyBox manager has been scrapped in favor of this Termux-first model.

---

## 🛠 Modern Build System
*   **Target SDK:** Updated to **API 36**.
*   **Min SDK:** API 23 (Android 6.0).
*   **AndroidX Migration:** Fully migrated from legacy Support Libraries to **AndroidX**.
*   **Gradle & AGP:** Updated to Gradle 9.4.1 and Android Gradle Plugin 9.2.0.
*   **Build JDK:** Gradle builds run on **JDK 17+**; app bytecode remains compatible with Java 8.

---

## 📦 Release Channels and Support

Re:TUI has a clear channel split:

*   **Play Store:** Official stable release for normal users and the primary way to support development.
*   **Firebase App Distribution:** Official beta/testing channel for invited testers, preview builds, and rapid validation.
*   **GitHub:** Source code, docs, issue tracking, and self-built/community workflows.

Support expectations follow that split:

*   **Play Store builds:** Fully supported.
*   **Firebase builds:** Supported on a testing / best-effort basis.
*   **Self-built or forked builds:** Community / best-effort only.

The project stays public because Re:TUI benefits from open development, but the Play Store build is the canonical polished release for everyday use.

---

## 🛡 Security Hardening (MASVS-Aligned)

This project uses the **OWASP Mobile Application Security Verification Standard (MASVS)** as a practical hardening checklist where it applies to a terminal-style launcher. This is an engineering posture, not a formal certification.

### 📦 MASVS-STORAGE: Data Storage and Privacy
*   **Storage Work In Progress:** Re:TUI is being modernized for safer storage handling across recent Android versions, with active work around launcher config compatibility and recovery.
*   **Backup Protection:** `android:allowBackup` is set to `false`, with backup/data-extraction rules excluding app data from cloud backup and device transfer.
*   **Secure File Sharing:** Uses `FileProvider` for secure, permission-based file sharing instead of vulnerable `file://` URIs.

### 🌐 MASVS-NETWORK: Network Communication
*   **Enforced TLS:** `android:usesCleartextTraffic` is disabled globally. All network communications are forced over **HTTPS** (TLS 1.2+).
*   **Hardened Service Endpoints:** Internal services, including MET Norway weather and connectivity checks, use secure HTTPS endpoints.

### ⚙️ MASVS-PLATFORM: Platform Interaction
*   **Signature-Level Protection:** Implemented a custom permission `${applicationId}.permission.RECEIVE_CMD` (for the current package, `com.dvil.tui_renewed.permission.RECEIVE_CMD`) with `protectionLevel="signature"`. This ensures only apps signed with the same developer key can programmatically send commands to the launcher.
*   **Intent Security:** App-created `PendingIntents` are immutable by default to prevent intent redirection attacks; mutable flags are reserved for Android APIs that require caller-filled results, such as notification `RemoteInput` and Termux command callbacks.
*   **Receiver Security:** Broadcast Receivers use explicit export settings. Internal app events use in-process broadcasts; platform dynamic receivers are registered as `RECEIVER_NOT_EXPORTED`; externally callable command/callback surfaces are signature-permission protected or token-gated.

### 🛠 MASVS-CODE: Code Quality & Build Settings
*   **Minification & Obfuscation:** Release builds have R8/Proguard enabled (`minifyEnabled true`) to shrink resources and obfuscate code.
*   **Foreground Service Security:** Updated to comply with Android 14's strict foreground service types (`specialUse`, `mediaPlayback`).

---

## 🔗 Useful Links

**Project repo**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[GitHub.com](https://github.com/DvilSpawn/Re-TUI.git)**<br>
**Project wiki**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[GitHub Wiki](https://github.com/DvilSpawn/Re-TUI/wiki)**<br>
**Community**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Reddit](https://www.reddit.com/r/RE_TUI_launcher/)**<br>
**Chat**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Discord](https://discord.gg/n6zsVYuV)**<br>
**Email**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[DvilSpawn@gmail.com](mailto:DvilSpawn@gmail.com)**<br>

## 📚 Open Source Libraries
* [**CompareString2**](https://github.com/fAndreuzzi/CompareString2)
* [**OkHttp**](https://github.com/square/okhttp)
* [**HTML cleaner**](http://htmlcleaner.sourceforge.net/)
* [**JsonPath**](https://github.com/json-path/JsonPath)
* [**jsoup**](https://github.com/jhy/jsoup/)
