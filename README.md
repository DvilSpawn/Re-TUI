# Re:T-UI Console Launcher

Personal fork and continuation of the original T-UI launcher, updated for modern Android versions and ongoing device testing.

---

## 🚀 Recent Changes & Modernization

These updates ensure the launcher remains functional, secure, and performant on modern Android devices (Android 11 through Android 14+).

> **Pro Tip:** On the very first install, if background transparency does not take effect immediately, simply type \`restart\` in the terminal and press enter.

### ⌨️ New Commands
*   **`username [user] [device]`**: Instantly customize your terminal prompt. Changes both the username and device name and reloads the UI to apply.
*   **`theme -preset [name]`**: Rapidly switch between high-quality pre-configured themes.
    *   **Available Presets:** `blue`, `red`, `green`, `pink`, `bw`, `cyberpunk`.
    *   **Smart Suggestions:** Applying a preset automatically colors the suggestion bar and shortcut buttons to match the aesthetic.
*   **`webhook`**: A scalable Webhook system featuring template-based HTTP POST requests.
    *   **Substitution:** Supports `%n` parameter substitution (e.g., `%1` for the first argument).
    *   **History:** Automatically tracks the last 5 unique sets of arguments for each webhook.
    *   **Suggestions:** Provides history-based autocomplete for `webhook [name]` arguments.
*   **`post [url] [body]`**: Send raw HTTP POST requests directly from the terminal.
*   **`module`**: Native, Lua, and Termux-backed modules are the recommended way to add launcher panels and scripted workflows.
*   **`guide`**: A non-blocking command-first walkthrough that uses terminal output and the existing suggestion row instead of a first-run modal.
*   **`space`**: Create, duplicate, rename, save, list, switch, and remove launcher Spaces, each with its own snapshot of launcher settings, module state, and command-facing configuration.
*   **BusyBox manager removed**: Re:T-UI no longer downloads BusyBox; use `shell` for Android's built-in shell and Termux for maintained Linux tooling.
*   **ASCII Art System**: A new header system that displays custom ASCII art on the dashboard. Controlled via `show_ascii`, `show_ascii_landscape`, `ascii_index`, and `ascii_size` in `Ui.xml`.

### ✨ Enhanced Features
*   **Termux Execution Layer:** Keep Linux tooling, scripts, and custom modules in Termux while Re:T-UI stays focused on launcher UI and command routing.
*   **Termux Tmux Workspace:** Optional tmux workspace for persistent TUIs, quick launchers, saved launch commands, prompt aliases, reconnect, and status diagnostics.
*   **Theme Preset Shortcut Buttons:** Enhanced the `theme -preset` command to show interactive shortcut buttons for presets.
*   **Synchronized Theme UI:** Applying a preset now automatically colors the shortcut buttons (suggestions) to match the overall theme.
*   **One-Tap Application:** Shortcut buttons for theme presets execute immediately upon clicking.
*   **Expanded Status Bar:** Support for up to 10 status lines (tv0-tv9) for richer information display.

---

## 🐧 Termux Integration

For a full Linux environment, use Termux as the execution layer:

1.  Install Termux.
2.  Run `termux-setup-storage` in Termux.
3.  Enable `allow-external-apps=true` in Termux properties.
4.  Run `tbridge -doctor` in Re:T-UI to verify the bridge.
5.  Enable `show_tmux_workspace_button` when you want the optional persistent tmux workspace.
6.  Use `termux`, `module`, and `files` for scripts, modules, and file workflows.

This keeps the launcher lean for Play Store builds while preserving power-user Linux workflows through an app that is designed to own them. The old BusyBox manager has been scrapped in favor of this Termux-first model.

---

## 🛠 Modern Build System
*   **Target SDK:** Updated to **API 36**.
*   **Min SDK:** API 23 (Android 6.0).
*   **AndroidX Migration:** Fully migrated from legacy Support Libraries to **AndroidX**.
*   **Gradle & AGP:** Updated to Gradle 9.4.1 and Android Gradle Plugin 9.2.0.
*   **Java Compatibility:** Built with **Java 17** support.

---

## 🧪 Future Ideas
*   **Animated ASCII Art:** Explore a low-power animated ASCII header using an AsciiAnimator-style plain text format with `[frame]` separators, capped FPS, and lifecycle-aware playback.

---

## 📦 Release Channels and Support

Re:T-UI now has a clear channel split:

*   **Play Store:** Official stable release for normal users and the primary way to support development.
*   **Firebase App Distribution:** Official beta/testing channel for invited testers, preview builds, and rapid validation.
*   **GitHub:** Source code, docs, issue tracking, and self-built/community workflows.

Support expectations follow that split:

*   **Play Store builds:** Fully supported.
*   **Firebase builds:** Supported on a testing / best-effort basis.
*   **Self-built or forked builds:** Community / best-effort only.

The project stays public because Re:T-UI benefits from open development, but the Play Store build is the canonical polished release for everyday use.

For more detail, see **[docs/wiki/Support-and-Release-Channels.md](./docs/wiki/Support-and-Release-Channels.md)**.

---

## 🛡 Security Hardening (MASVS-Aligned)

This project uses the **OWASP Mobile Application Security Verification Standard (MASVS)** as a practical hardening checklist where it applies to a terminal-style launcher. This is an engineering posture, not a formal certification.

### 📦 MASVS-STORAGE: Data Storage and Privacy
*   **Storage Work In Progress:** Re:T-UI is being modernized for safer storage handling across recent Android versions, with active work around launcher config compatibility and recovery.
*   **Backup Protection:** `android:allowBackup` is set to `false`, with backup/data-extraction rules excluding app data from cloud backup and device transfer.
*   **Secure File Sharing:** Uses `FileProvider` for secure, permission-based file sharing instead of vulnerable `file://` URIs.

### 🌐 MASVS-NETWORK: Network Communication
*   **Enforced TLS:** `android:usesCleartextTraffic` is disabled globally. All network communications are forced over **HTTPS** (TLS 1.2+).
*   **Hardened Service Endpoints:** Internal services (Weather API, Connectivity checks) use secure HTTPS endpoints.

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
**Wiki in repo**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[docs/wiki/Home.md](./docs/wiki/Home.md)**<br>
**Community**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Reddit](https://www.reddit.com/r/RE_TUI_launcher/)**<br>
**Chat**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Discord](https://discord.gg/n6zsVYuV)**<br>
**Email**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[DvilSpawn@gmail.com](mailto:DvilSpawn@gmail.com)**<br>

## 📚 Open Source Libraries
* [**CompareString2**](https://github.com/fAndreuzzi/CompareString2)
* [**OkHttp**](https://github.com/square/okhttp)
* [**HTML cleaner**](http://htmlcleaner.sourceforge.net/)
* [**JsonPath**](https://github.com/json-path/JsonPath)
* [**jsoup**](https://github.com/jhy/jsoup/)
