# Settings preview implementation and test report

Date: 14 October 2026. Branch: `dvil/settings-preview-carousel`.

## Delivered

Appearance editing now includes four horizontally swipeable preview pages inside the existing settings editor: Status, Terminal, Modules, and ANSI. A page label replaces navigation buttons. Each sample page can scroll vertically when its content exceeds the compact preview area.

- Status: representative device, memory, clock, battery, storage, network, notes, weather, unlock, and ASCII rows. Visibility, text size, alignment, ordering, individual borders, and unified borders follow the edited settings.
- Terminal: an overlapping OUTPUT header, a directory listing, success/error output, input prompt, suggestions, and a toolbar sample.
- Modules: miniature music and notes panels with overlapping header tabs, playback controls, and normal/selected/disabled control samples.
- ANSI: colored directories, an executable and symlink, build messages, a small Lua-style code sample, and all 16 normal/bright palette swatches with their SGR numbers.

The samples reuse the launcher's `TerminalBorderDrawable`, header cutout binding, frame assets, and theme inheritance resolver. They do not instantiate active modules or run sample commands.

## Save, discard, and recreation

Draft values remain inside the settings adapter. Rendering uses those values directly without writing XML, replacing the launcher's settings snapshot, or populating the global theme preview cache. Save writes the values and refreshes an already-running launcher while retaining the settings page. Cancel presents the existing discard confirmation.

Draft values and the selected preview page survive activity recreation. Status ordering uses the same reorder operation as Save, including when search hides the other status settings. An individual corner radius of `0` retains the launcher's existing inherited-radius behavior before and after Save.

The preview compacts when the keyboard opens, and explicit system/keyboard inset handling keeps the editing controls and Save button above it.

## Validation

Final Play Store flavor debug build: **passed**. Installed by updating the existing `com.dvil.tui_renewed.fetchdebug` app on the connected I2220 phone running Android 16, serial `10BE1C0FB40006A`. The regular launcher package was not replaced.

**7 device tests passed, 0 failed:**

| Test | Verified behavior |
|---|---|
| Local draft rendering and Save | A border edit changes rendered pixels; launcher values and XML remain unchanged until Save; Save persists the edit. |
| Theme cache isolation | Valid theme input stays local; invalid color input is rejected; the launcher preview cache remains empty. |
| Real swipe and recreation | Injected touch swipes move forward/backward between pages; recreation retains the draft and selected page. |
| ANSI draft and Cancel | The ANSI ERROR text uses the edited color while saved values remain unchanged; confirmed Cancel/discard leaves XML unchanged. |
| Filtered status ordering | Changing a status position through search produces the same preview ordering after Save. |
| Keyboard layout | The real keyboard opens, the preview shrinks, the settings list retains space, and Save remains visible. |
| Inherited radius | Resetting an individual radius to `0` previews the same inherited radius used by the launcher after Save. |

**26 targeted unit tests passed, 0 failed:** theme color resolution (12), status row resolution (8), terminal border runtime (4), and surface borders (2). The normal unit build also ran the runtime notices validation successfully. The isolated fetch-debug APK build excludes that task because its variant filter removes the release configurations inspected by that existing task.

`git diff --check`: **passed**. Device test fixtures restore their original setting values after execution.

## Evidence and artifact

- [Status screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/status.png)
- [Terminal screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/terminal.png)
- [Modules screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/modules.png)
- [ANSI screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/ansi.png)
- [ANSI draft color screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/ansi-draft.png)
- [Keyboard screenshot](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/keyboard.png)
- [Device results](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/device-tests.txt)
- [Unit build results](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/unit-tests.txt)
- [Tested debug APK](/Users/mako/StudioProjects/TUI-ConsoleLauncher/build/settings-preview/refined/settings-preview-debug.apk)

## Scope and remaining limits

The previews use representative content, not live personal data. Custom Lua module layouts and arbitrary terminal application screens are not mirrored. The scenes cover the appearance controls listed above, not every setting across the launcher. Physical checks were limited to this phone; landscape and TalkBack were not tested on hardware.

Unrelated working-tree changes were preserved. No commit, push, release, or public publication was performed.

## Design references

- [KDE Colors](https://docs.kde.org/trunk_kf6/en/plasma-workspace/kcontrol/colors/index.html): preview alongside editing and explicit Apply/Cancel semantics.
- [KDE Window Decorations](https://docs.kde.org/stable_kf6/en/kwin/kcontrol/kwindecoration/index.html): miniature borders and header controls.
- [terminal.sexy templates](https://github.com/stayradiated/terminal.sexy#templates): representative terminal content for evaluating colors.
