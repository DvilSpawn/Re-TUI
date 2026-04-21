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
*   **`bbman`**: The new BusyBox manager for installing and verifying Linux binaries.
*   **ASCII Art System**: A new header system that displays custom ASCII art on the dashboard. Controlled via `show_ascii`, `ascii_index`, and `ascii_size` in `Ui.xml`.

### ✨ Enhanced Features
*   **Built-in BusyBox Manager:** Gain access to 300+ Linux commands (ls, grep, awk, top, etc.) via the new `bbman -install` command.
*   **Theme Preset Shortcut Buttons:** Enhanced the `theme -preset` command to show interactive shortcut buttons for presets.
*   **Synchronized Theme UI:** Applying a preset now automatically colors the shortcut buttons (suggestions) to match the overall theme.
*   **One-Tap Application:** Shortcut buttons for theme presets execute immediately upon clicking.
*   **Expanded Status Bar:** Support for up to 10 status lines (tv0-tv9) for richer information display.

---

## 🐧 BusyBox Integration

To enable a full Linux environment, you can install BusyBox directly from the launcher:

1.  Type `bbman -install` in the terminal.
2.  The launcher will automatically detect your architecture, download the verified binary, and check its integrity.
3.  Once finished, you can run any Linux command directly (e.g., `ls`, `ping`, `vi`).
4.  To remove it at any time, use `bbman -remove`.

**Security Note:** Binaries are sourced from the trusted EXALAB repository and are verified against hardcoded SHA-256 hashes to ensure they have not been tampered with.

---

## 🛠 Modern Build System
*   **Target SDK:** Updated to **API 36**.
*   **Min SDK:** API 21 (Android 5.0).
*   **AndroidX Migration:** Fully migrated from legacy Support Libraries to **AndroidX**.
*   **Gradle & AGP:** Updated to Gradle 8.2 and Android Gradle Plugin 8.2.0.
*   **Java Compatibility:** Built with **Java 17** support.

---

## 🧪 Future Ideas
*   **Animated ASCII Art:** Explore a low-power animated ASCII header using an AsciiAnimator-style plain text format with `[frame]` separators, capped FPS, and lifecycle-aware playback.

---

## 🛡 Security Hardening (OWASP MASVS Compliance)

This project has been audited and hardened following the **OWASP Mobile Application Security Verification Standard (MASVS)**.

### 📦 MASVS-STORAGE: Data Storage and Privacy
*   **Storage Work In Progress:** Re:T-UI is being modernized for safer storage handling across recent Android versions, with active work around launcher config compatibility and recovery.
*   **Backup Protection:** `android:allowBackup` is set to `false` to prevent sensitive data extraction via ADB backups (MASVS-STORAGE-1).
*   **Secure File Sharing:** Uses `FileProvider` for secure, permission-based file sharing instead of vulnerable `file://` URIs.

### 🌐 MASVS-NETWORK: Network Communication
*   **Enforced TLS:** `android:usesCleartextTraffic` is disabled globally. All network communications are forced over **HTTPS** (TLS 1.2+).
*   **Hardened Service Endpoints:** Internal services (Weather API, Connectivity checks) have been upgraded to secure HTTPS endpoints (MASVS-NETWORK-1).

### ⚙️ MASVS-PLATFORM: Platform Interaction
*   **Signature-Level Protection:** Implemented a custom permission `ohi.andre.consolelauncher.permission.RECEIVE_CMD` with `protectionLevel="signature"`. This ensures only apps signed with the same developer key can programmatically send commands to the launcher.
*   **Intent Security:** All system-bound `PendingIntents` use the `FLAG_IMMUTABLE` flag to prevent intent redirection attacks (Android 12+ requirement).
*   **Receiver Security:** All Broadcast Receivers are registered with appropriate export flags (`RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`) to prevent unauthorized external triggers.

### 🛠 MASVS-CODE: Code Quality & Build Settings
*   **Minification & Obfuscation:** Release builds have R8/Proguard enabled (`minifyEnabled true`) to shrink resources and obfuscate code, making reverse engineering more difficult (MASVS-RESILIENCE-1).
*   **Foreground Service Security:** Updated to comply with Android 14's strict foreground service types (`specialUse`, `mediaPlayback`).

---

## 🔗 Useful Links

**Project repo**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[GitHub.com](https://github.com/DvilSpawn/Re-T-UI)**<br>
**Project wiki**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[GitHub Wiki](https://github.com/DvilSpawn/Re-T-UI/wiki)**<br>
**Community**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Reddit](https://www.reddit.com/r/tui_launcher/)**<br>
**Chat**&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;-->&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;**[Telegram](https://t.me/tuilauncher)**<br>

## 📚 Open Source Libraries
* [**CompareString2**](https://github.com/fAndreuzzi/CompareString2)
* [**OkHttp**](https://github.com/square/okhttp)
* [**HTML cleaner**](http://htmlcleaner.sourceforge.net/)
* [**JsonPath**](https://github.com/json-path/JsonPath)
* [**jsoup**](https://github.com/jhy/jsoup/)
