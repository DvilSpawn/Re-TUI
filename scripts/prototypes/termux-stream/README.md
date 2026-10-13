The standalone Android probe has been retired from the Gradle build. Its tested
engine and cell adapter now live in the existing launcher workspace activity,
TerminalGridView and authenticated Termux helper. See [integration details](../../../termux/retui/README.md).
These scripts retain the earlier host/phone benchmark tooling; the old Android
probe source and private device evidence are archived in Downloads.

# Termux output streaming experiment

This is a host-only prototype for the Re:TUI Termux workspace. It proves that real
tmux output can update the upstream Termux emulator without repeatedly capturing
the screen. It does not change the launcher, its dependencies, the installed
Termux helper, or any real user session.

The emulator's buffer is examined after processing bytes; no Android Canvas view
is drawn in this experiment. The Android SDK jar is used to resolve upstream
types. No local Termux `TerminalSession`, JNI shell process or Android keyboard is
created. Clipboard OSC handling and other paths that call Android runtime APIs
are outside this host probe.

## Run

Requires Python 3.8+, Java 17 (`java` and `javac`), tmux, and an Android SDK jar.
It reuses the repository's isolated tmux test fixture, with personal tmux
configuration disabled. It uses two temporary raw-echo jobs rather than a real
editor. Only the temporary server is killed at cleanup.

```sh
PYTHONDONTWRITEBYTECODE=1 python3 scripts/prototypes/termux-stream/probe.py \
  --cache /Users/mako/Downloads/retui-termux-stream-prototype \
  --output /Users/mako/Downloads/retui-termux-stream-prototype/results.json
```

For another machine, change the output/cache paths. `--android-jar` can select an
SDK jar explicitly. The default is Android 36 under `$ANDROID_HOME`, or the
standard macOS SDK directory. Without `--cache`, generated files go into this
prototype's ignored `build/` directory.

Upstream is pinned to Termux v0.118.3, commit
`5b657c6adf4304e5198951ce815fe0205dcac29c`. The archive's SHA-256 is checked before
compilation. Upstream Java files and its license record are downloaded into the
cache, not vendored into the launcher. This pinned engine is an experiment input,
not a recommendation to ship an unreviewed library version.

## What passed

- Octal-escaped tmux output is decoded to bytes before interpreting UTF-8.
- The real emulator accepts escape sequences and UTF-8 split one byte at a time.
- Live output preserves wide Unicode cells and literal backslashes.
- One persistent control connection routes two windows to separate emulators.
- A 200-line output burst produces the same visible text as tmux.
- Alternate screen changes, cursor hiding and true-color cell attributes work.
- Resizing one window and its emulator leaves the other window unchanged.
- Terminal-query replies are intercepted rather than sent back to tmux twice.
- Twenty-four paired keystrokes reach both the old snapshot and live paths.
- Detaching the observer leaves both jobs running.

## Measurement

The JSON report records 24 samples per path. For the paired comparison, the exact
same input is sent through the packaged helper, and observed by both its snapshot
stream and the new control-mode/emulator path. A separate experiment sends bytes
through the persistent control connection after stopping snapshot polling.

The baseline stops at receipt of a complete snapshot. The streaming measurement
stops after the JVM emulator processes output and returns its buffer state. Both
exclude Android drawing; these are not input-to-visible-frame measurements on a
phone. The persistent-input experiment also excludes the eventual Android socket
relay. Read the saved JSON for the measured values from the latest run.

The final verified run on 2026-10-13 (macOS, tmux 3.6b, 24 samples) measured:

| Path | Median | 95th percentile |
|---|---:|---:|
| Existing helper input -> snapshot | 216.85 ms | 392.91 ms |
| Same helper input -> Termux emulator | 39.84 ms | 45.71 ms |
| Persistent control input -> Termux emulator | 1.33 ms | 1.46 ms |

The paired result supports replacing polling. The third result supports
eliminating per-key resize/decode/tmux process launches as a separate change.
Neither establishes phone latency or battery savings.

## Proposed Android integration

```text
Termux app: shell/TUI -> tmux server -> persistent control client
                                         |
                          authenticated existing loopback transport
                                         |
Re:TUI: ordered byte deltas -> TerminalEmulator per pane -> Canvas renderer
```

The phone prototype now uses `TerminalEmulator` with `TerminalOutput` callbacks
and the launcher's existing `TerminalGridView`. A cell adapter supplies the
engine's buffer to the existing drawing code, without parsing captured ANSI text
again. The original renderer's draw loop and font handling are unchanged.
The upstream renderer explored earlier is not used. The full `TerminalView`
assumes a locally owned `TerminalSession`, which this prototype does not create.

Map existing window IDs (`@n`) to pane IDs (`%n`); output notifications use pane
IDs. Keep the current workspace's layout, key tray, IME input capture, theme,
local command draft and selection behavior. Initially put the renderer behind a
debug-only entry so the shipped grid remains available.

In the helper, add an explicitly negotiated protocol mode for output deltas.
Keep the current snapshot mode for compatibility and recovery. A persistent
tmux control client can carry `send-keys` and resize commands without launching
new processes for each key. Resize only when geometry changes.

On Android, preserve every byte in order but combine redraw requests for the next
display frame. Apply queued bytes and draw the emulator on the same thread to
avoid concurrent mutation. Update workspace metadata and saved preferences only
when they change. This probe exports full text after each chunk for assertions;
that diagnostic export should not be in the Android hot path.

## Remaining correctness work

**Reconnect is the main gate.** A new emulator seeded from only the visible
screen loses the hidden normal buffer while a TUI is using its alternate screen.
The probe reproduces this: exiting the alternate screen loses `MAIN`. Capturing
both buffers and restoring the saved cursor recovers the tested transition.
That is a controlled example, not complete state synchronization.

Production recovery must also address terminal modes, scroll regions, pending
escape sequences, wrap state, current attributes and the ordering of snapshots
relative to live bytes. Backgrounded activities, process death, pane switching,
resize and flow-control pauses all need explicit recovery. Do not use a fake
local echo to hide this problem. Initially retain snapshot mode for sessions that
cannot be synchronized reliably; fresh sessions can start streaming immediately.

tmux-generated copy/chooser UI is not included in raw `%output` notifications.
Use local scrollback/selection, or keep a snapshot fallback for these tmux modes.
tmux already responds to terminal queries; the observing emulator must not emit
duplicate replies. Clipboard/title/bell callbacks need deliberate Android
behavior. The probe only counts/suppresses responses and does not access the
clipboard.

Bound protocol lines and pending bytes. Output deltas cannot be silently dropped
like old frames: a missing byte can corrupt subsequent terminal state. Handle
`%extended-output`, `%pause` and `%continue` explicitly when enabling tmux flow
control, and resynchronize after overflow/disconnect. The probe tests ordinary
`%output`; it does not implement production backpressure or recovery.

Closing the workspace must detach the control client without killing shells,
and changes to control-client attachment must not change other users' active
windows or interfere with jobs.

## Next concrete prototype

The separate debug Android surface has now been built and tested on the wireless
phone. See [the Android prototype](../../../termux/retui/README.md)
for build/test instructions and the measured results. Fresh shell, Bash editing,
Unicode paste, Vim/Nano, both layouts, IME/rotation resize, activity recreation
and Home/reconnect checks passed. `htop` was not installed on the test phone.
Complete recovery and production integration remain separate work.

The original host experiment remains a buffer-state test. The later Android
prototype supplies the build, installed-device and rendered-frame evidence.

## Sources

- [Pinned Termux emulator](https://github.com/termux/termux-app/blob/5b657c6adf4304e5198951ce815fe0205dcac29c/terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java)
- [Pinned Termux renderer](https://github.com/termux/termux-app/blob/5b657c6adf4304e5198951ce815fe0205dcac29c/terminal-view/src/main/java/com/termux/view/TerminalRenderer.java)
- [tmux control protocol, output and flow control](https://github.com/tmux/tmux/wiki/Control-Mode)
- [tmux snapshot commands and terminal-state formats](https://man.openbsd.org/tmux)
- [Termux license and component exceptions](https://github.com/termux/termux-app/blob/5b657c6adf4304e5198951ce815fe0205dcac29c/LICENSE.md)
