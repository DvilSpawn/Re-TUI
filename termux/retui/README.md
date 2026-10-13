# Re:T-UI Termux Bridge

`retui` 0.4.4 is the Termux-side helper for the launcher's tmux workspace.
Termux owns the shell, tmux session and persistent jobs. Re:T-UI displays
captured cells and forwards input; this is not a second terminal runtime or a
claim of complete terminal-emulator fidelity.

## Install

Launcher now bundles the matching helper and checks it when opening the workspace.
Choose **Update now** to install/update it inside Termux and reconnect; **Later**
does not install anything. `:update` reopens the prompt. This is a user-approved
APK-matched update, not a background download of the GitHub branch.

Termux must already be installed with command permission and `allow-external-apps`
enabled by the user. If required tools are missing, the pane offers setup guidance;
install `tmux`, `socat` and `gawk` in Termux first. Existing shells/jobs are retained, and
only bridge listeners reconnect. Helper replacement uses bounded Base64 transport,
SHA-256 verification, an atomic rename and installation-failure rollback. The last
helper is retained at `$PREFIX/share/retui/retui.previous`; custom helpers at the
normal path are replaced only after confirmation. No credentials are included.

The short-lived update lock serializes installs. If Termux is forcibly killed
during installation, retry may report an interrupted lock; Launcher never removes
unknown locks automatically. Inspect `$PREFIX/share/retui/.update-lock` in Termux
only after verifying no updater is running. The pane times out without replaying
an installation; closing Launcher does not cancel a command already sent to Termux.

For manual installation:

```sh
pkg install curl tmux socat gawk -y
curl -fsSL https://raw.githubusercontent.com/DvilSpawn/Re-TUI/master/termux/retui/install.sh | sh
retui version
retui bridge status
```

For a development branch, replace `master` in the URL or set
`RETUI_INSTALL_BASE_URL` to its `termux/retui` directory before installation.
Termux must expose `RUN_COMMAND`, enable `allow-external-apps`, and grant the
launcher its RUN_COMMAND permission. The launcher still bootstraps through this
service. The socket is local IPC: an expected abstract Android socket, with
only `127.0.0.1` TCP fallback when cross-app abstract sockets are blocked.

## Workspace controls

- The launcher toolbar Termux icon, `tmux`, and `termux` open a dedicated Activity.
- The layout tab always offers Single, Top-bottom, and Left-right (maximum two tiles).
  Choose an existing window or a new shell for the second tile. Single expands the
  focused tile without terminating either shell. Layout, focus and window IDs persist.
- Tap a tile to focus it; its highlighted label identifies the input/IME target.
  Tap a window label to choose a window. Swipes affect only the focused tile.
- Swipe the terminal horizontally, or use `:prev` / `:next`, for adjacent windows.
- Add always asks where to place the new shell window: replace a tile (keeping
  its shell running), split a single page top-bottom/left-right, or open a new
  single page. Cancelling creates nothing. Pages remember their own pair and focus.
- Swipe the labelled page strip or tap its arrows to change pages; `:page next`
  and `:page prev` do the same. Terminal swipes still change only the focused tile.
- Exiting a shell removes its window from pages, collapses a surviving split and
  closes empty pages. The final exit leaves an empty workspace; resume/reconnect
  never silently recreates it. Add explicitly starts a new shell.
- `:switch <window>` selects an index/name inside the pane. `tmux switch <window>`
  does the same from Launcher Home. `:new [name]` creates a window.
- `:launch <id>` focuses/creates a saved launcher window; `:save <id> <command>`
  saves a custom launcher and `:rm <id>` removes that saved entry.
- `:status`, `:refresh`, `:reconnect`, `:update`, `:help` and `:home` remain local controls.
  Use the existing `termuxopen` command when the full Termux application is needed.
- Connecting/Ready/Disconnected belongs to the header, not the running TUI.
  Typing/key progress does not replace a captured editor or other application.
- Leaving the Activity or backgrounding closes its stream, not tmux jobs. Returning
  reconnects to the same session. A local colon-command draft is retained.

Ready requires the helper's successful HELLO response and a complete valid
frame from the expected session. It does **not** acknowledge each input command:
this helper has no per-command ACK. Writes are bounded, and rejected/ambiguous
input is never automatically retried through a different mutation path. Check
the terminal before manually resending after a disconnect.

## Helper commands

```text
retui bridge capabilities
retui bridge status
retui bridge capture
retui bridge socket-start
retui bridge socket-daemon
retui bridge tcp-daemon
retui bridge socket-client
retui bridge send
retui bridge type
retui bridge new
retui bridge launch
retui bridge switch
retui bridge select
retui bridge key
retui bridge kill
```

`capabilities` is read-only and lists supported operation names. The launcher
chooses helper or direct tmux **before** mutation; it exits with the selected
helper's result rather than replaying failed input. The dedicated Activity requires 0.4.4; it never silently falls back to a
session-global input/capture path. Updating the APK alone does not update a
Termux helper. Upgrade the helper separately; existing sessions/jobs are retained.
For continuous streaming and independent tile geometry, update to 0.4.4. The v4 targeted socket protocol is not compatible with
older launcher frame readers; update both sides rather than claiming transparent
backward compatibility.

Environment:

```text
RETUI_SESSION         session name, default retui_workspace
RETUI_INPUT           literal input for send/type
RETUI_WINDOW_NAME     optional name for new; required for launch
RETUI_COMMAND         optional initial command for new/launch
RETUI_DIRECTION       next or prev for switch
RETUI_TARGET          existing window index/name for select
RETUI_KEY             tmux key such as Tab, PageDown or C-c
RETUI_COLS/RETUI_ROWS  optional geometry, maximum 240x120
RETUI_SOCKET_NAME     abstract socket name
RETUI_TCP_PORT        loopback fallback port, default 8927
RETUI_STREAM_INTERVAL changed-frame polling interval, default 0.16 seconds
```

`kill` explicitly kills the session; it is not used when merely hiding the pane.

## Frame and socket protocol

Metadata precedes exactly one base64 payload. Terminal output is never parsed
as metadata or as a frame delimiter. Window-list entries are tab-separated,
allowing spaces in names. Example (the payload here encodes `hello` plus newline):

```text
__RETUI_BRIDGE__ retui-bridge-v1
__RETUI_VERSION__ 0.4.4
__RETUI_SESSION__ retui_workspace
__RETUI_TARGET__ @0
__RETUI_WINDOW__ 0:1
__RETUI_WINDOWS__ @0:0:1
__RETUI_COLS__ 80
__RETUI_ROWS__ 24
__RETUI_CURSOR__ 0:0
__RETUI_FRAME_BASE64__ aGVsbG8K
__RETUI_FRAME_END__
```

`socket-start` also returns `__RETUI_SOCKET__ retui-bridge-socket-v4`, the socket
name, loopback host/port and private token in its bootstrap result. The launcher
opens one connection per visible tile and sends
`HELLO <token> <cols> <rows> OUTPUT <base64-window-id-or-dash> NO_CREATE`.
Each connection owns its target; capture, resize, text and keys address that
window, not tmux's session-global active window. SELECT and SWITCH change only
the connection target; NEW creates a detached window and targets it. Window
list entries are `@id:index:name`. Missing restored windows require reselection.
Displaying one window in both tiles is avoided because tmux sizes a window,
not individual viewers. The helper returns protocol and
`__RETUI_SOCKET_STATUS__ ok`, followed by changed frames. Omitting STREAM uses
request/response captures with the **same encoded** frame format. Messages
include CAPTURE, ENTER, SEND, TYPE, NEW, SWITCH, SELECT, KEY, PING and BYE; text/name/initial
command arguments use base64. Invalid payloads fail before sending Enter.

The 0.4.4 extension emits `__RETUI_TARGET_GONE__ @id` when a targeted window
disappears or its retained pane has exited (`pane_dead`), and `-` for an empty
session. It is a control event, not terminal output. `NO_CREATE` prevents HELLO
from bootstrapping an exited session; only explicit NEW may create it. The
Activity also uses `RETUI_NO_CREATE=1` on socket-start. Listener readiness uses
authenticated PROBE (no capture, resize or shell creation), not an artificial
20×8 attachment. Legacy non-Activity helper commands retain their bootstrap semantics.

In 0.4.4, OUTPUT uses a persistent tmux control client on this same authenticated
socket. Output notifications request coalesced, authoritative screen captures
through that client. Cursor metadata and cells are read by synchronous commands
in one tmux command sequence, with no shell export or suppressed-output window.
The captured body is base64 encoded and bounded; a fixed row count and matching
control-block terminator keep terminal text from becoming socket metadata.
Termux's engine projects those captured cells into the existing TerminalGridView,
once per animation frame. It does not replay raw PTY bytes or send terminal replies;
tmux owns parser state, alternate buffers, rendition and replies. This avoids
reconnect errors caused by trying to reconstruct an incomplete terminal state.

The control parser uses buffered awk reads. Each text command fits the pipe's
atomic-write limit, so capture commands cannot interleave inside a large paste.
Normal committed text uses bounded TYPE_HEX messages and common keys use the
same persistent control client, avoiding a new tmux process for each keystroke.
Input waits behind the initial capture barrier before it reaches the shell.
The TCP fallback disables Nagle buffering on both ends. Unchanged panes do not
capture or repeatedly resize. STREAM retains changed-frame polling for older
callers; CAPTURE is a one-shot snapshot. The original pane UI, input editor,
configured font, palette and layouts are retained.

Window-add/rename notifications update the existing chooser and labels without
resetting the terminal engine. A tmux format subscription detects retained dead
panes; this requires tmux format-subscription support (`refresh-client -B`). It is checked inside tmux, not by repeated
external captures or an Android redraw timer.

In a full-screen editor, colon text goes to the application (for example Vim's
`:q!`). At the shell it still starts a workspace command. The existing toolbar
remains available for Home, window selection and layouts.

Reconnection and resizing recover from tmux's current screen, rather than a
partial parser-state seed. Input is never replayed after a connection loss.
A backlog or oversized capture closes the transport with an error; the user can
reconnect after checking the terminal. Application-specific features such as
mouse reporting, OSC clipboard integration and clickable terminal links are not
added by this change.

Closing the client ends its control client or capture loop. The listener and tmux session remain
available for reconnect; backgrounded launchers do not continue frame polling.

## Regression checks

```sh
python3 scripts/test_termux_workspace.py
python3 scripts/test_termux_helper.py
python3 scripts/test_termux_output.py
python3 scripts/test_termux_tiles.py
python3 scripts/test_termux_activity.py
sh -n termux/retui/bin/retui
sh -n termux/retui/install.sh
```

Helper checks use a uniquely named real tmux server and temporary home, not the
user's tmux server. Android protocol checks additionally exercise bounded
loopback fixtures and the real launcher parser/renderer. Neither substitutes
for verifying interactive applications against an installed Termux backend.
