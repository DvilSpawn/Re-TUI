# Terminal mode

Use `mode terminal` for a persistent, vertically scrolling grid of Termux panes.
`mode classic` and `mode search` return to the other launcher modes.

## Existing SSH pane implementation

This worktree starts from master `7e19f7b2` (Hotfix 5 / Build 508). Terminal home uses the same
`TermuxWorkspaceSshSetup`, `TermuxWorkspaceSshConnection`,
`TermuxWorkspaceTerminal`, `TerminalGridView`, and invisible
`TermuxWorkspaceInputEditText` as the existing SSH pane. Text, paste and special
keys use the same SSH writes and terminal key handling; output is streamed and
rendered on the next animation frame. The connection's two-tile restriction was
removed so home can attach additional tiles with independent channel IDs.

Classic/split use `retui_workspace`; Terminal home uses independent `retui_home`
windows, with separate lists and geometry. Both use the same SSH implementation.
On upgrade, only exact saved home-owned windows named `retui-home-<tile ID>` move
from the old workspace to home. Targets and running processes are retained;
startup commands are not replayed. Foreign/shared Classic windows are not moved.

Home owns placement and saved tile state. One SSH connection serves disposable
PTY channels for visible tiles; offscreen viewers detach while tmux jobs keep
running. This has the existing SSH pane's per-visible-viewer resource cost. The
superseded home-only MULTI socket/capture implementation was removed. Classic's
socket client and legacy output-driven implementation remain intact; its bundled
helper is version 0.4.8. Home does not use that helper for rendering or input.

## Sessions, placement and Search

Use **+ → Termux setup** to enable the existing SSH pane connection when required.
**New shell** creates a tmux window in `retui_home` and saves its returned
target. **Existing session** attaches without restarting it. Clock and other
bundled scripts use the same new-window path with a startup command. Startup
commands do not rerun when reopening home, reconnecting or scrolling back.

Tap a terminal to type directly. Hold anywhere for the move/resize, explicit reconnect
or removal. Removing a tile detaches its viewer without killing its shell. Ended
windows stay disconnected until explicitly restarted. Tile IDs, positions,
commands and targets survive launcher restart.

The bottom Search bar retains the same styling as Search mode, with + and
settings beside it. Terminal content blends into a blurred copy toward the bottom
edge; the bar itself is not blurred. A real tap on Search transfers keyboard
focus and opens Search results over the blurred dashboard on Android 12+ (dim on
earlier versions). Back returns to the dashboard. Vertical drags scroll the grid.
System insets and configured margins apply.

When a terminal is focused and the keyboard is visible, the shared Classic
special-key rows appear above the keyboard while Search and its date/add/settings
row hide to free terminal space. Releasing terminal focus restores Search. Navigation,
Ctrl/Alt/Shift and the function-key swipe use the same layout and binding as the
Classic pane. Modifiers reset after use or when switching terminal focus; Search
focus and keyboard dismissal hide the rows. A phone shell check verified Ctrl+U
and Left Arrow through these buttons, and a focused binding test covers modifier
latches, refresh and the function-key swipe.

## Focused verification, 2026-10-18

- Standard Play Store debug app/instrumentation builds and 43 focused JVM checks
  passed. Thirty-nine helper, shell-widget and SSH setup/dispatch/daemon checks
  passed after retiring the MULTI implementation.
- Ten emulator home/Search/fade/protocol checks passed. The bottom-fade check
  verifies opaque content above the fade, partial alpha within it, and near-zero
  alpha at the bottom edge. Search is exercised through a touch gesture.
- The signed build is installed in place on the I2220 phone. Existing SSH
  integration and stress tests passed, including a viewer in slot 7, Unicode
  bulk input, independent resizing and five reconnects preserving one job.
- A phone home test displayed eight SSH tiles, rapidly changed focused tile while
  committing Unicode text, checked that other shells did not receive that text,
  then paused/resumed and verified all eight retained their output.
- Latency is measured at equal 640x400 geometry from IME commit to draw-post,
  rather than physical tap-to-pixel. The former socket home measured about
  54 ms median versus Classic SSH's 16.5 ms. With the shared SSH implementation,
  a 92-sample run measured Classic median 16.54 ms / p95 30.23 ms and home median
  17.06 ms / p95 34.19 ms, passing the within-20% requirement. A preceding short
  20-sample run matched median latency but narrowly failed p95 (22.02 vs 17.63 ms).
  The final 92-sample repeat measured Classic 16.51 ms median / 26.09 ms p95 and
  home 16.64 ms / 33.73 ms: median parity holds, but this repeat failed p95.
  A fade-strip rendering optimization was reverted when its build did not finish
  within the time limit; the installed build retains the verified fade behavior.

The earlier 15-minute socket bridge endurance result does not validate this SSH
implementation. A new 15-minute SSH-home soak was not run within the ten-minute
change window. Long-duration battery behavior is unverified. No commit, push or
release.

## Bundled terminal widgets

Clock and Dashboard use solid Unicode block digits at 23 columns / 8 rows or
larger, with a compact clock below that size. Re:TUI Fetch is available from +
and uses the same script and shell creation path. It combines an ASCII skull
with date/time and local Android, kernel, architecture, memory, storage and
uptime information. Layout follows pane geometry; compact panes show fewer
fields. Restricted Android data is shown as unavailable. No extra packages or
network access are needed for Fetch. Static properties are read once; live
system statistics refresh every 30 seconds and the clock every second.

Existing running widgets retain their original inline script. Re-add a Clock
or Dashboard tile to use the updated digits; removing a tile leaves its shell
running. Other shells and saved startup commands are not rewritten.

### Fluid arrangement

Moving and expanding a tile anchors the edited tile at its requested position.
Obstructing tiles move downward, cascading through later tiles as needed; each
retains its width and height. The six-column canvas grows vertically. Landing
previews show all displaced tiles. Drag calculations always use the layout at
gesture start so moving back does not repeatedly push tiles farther down.
Cancelled gestures restore their original layout. This replaces size-swapping
on drop; panes settle upward after a completed move or resize.

Holding anywhere opens Move/resize, Reconnect/restart and Remove. Move/resize
selects the pane and exposes its edge handles. Home attachments clear legacy manual window sizing and follow the active
viewer dimensions. Geometry changes use the existing PTY resize path directly;
the tmux target and shell process remain alive without reconnecting the viewer.

Resize correction verification: the phone's expanded shell @4 had a 77x16
viewer but remained locked to a 38x16 manual tmux window. Enabling home-only
automatic sizing produced 77x16 with the same shell PID (22888), and the dotted
unused region disappeared. The updated APK and bundled dispatcher are installed.
Build and syntax checks passed. The local tmux regression run stalled while
creating its isolated server and was stopped; it is not a passing suite result.

### Bottom blur and header gestures

The bottom transition preserves terminal opacity and blends a blurred copy over
the sharp content on Android 12+ hardware canvases. It records only the lower
strip with padding for the blur; older Android and software canvases retain
sharp content rather than fading to wallpaper. Search remains a separate sibling.
Header taps do nothing; holding either the header or terminal opens its menu.
The phone opacity check and header tap/hold checks passed; the signed app and
instrumentation builds are installed. Long-run rendering performance was not
retested for this visual change. Earlier fade checks above describe the former
implementation, superseded by this transition.

The bottom blur now records one source frame for both sharp and blurred draws.
Complementary alpha masks crossfade those layers with additive composition,
removing the sharp side borders beneath the blur without an opacity dip. A
signed build is installed; a six-second phone scroll recording was captured
for a focused visual check. Long-duration performance remains unmeasured.

### Upward compaction on drop

After a move or resize is released, the valid push-down layout settles upward.
Panes retain their IDs, columns and sizes, and keep their vertical order wherever
their columns overlap. Each settles above empty rows until supported by another
pane or the top of the dashboard. Preview positions remain provisional while
dragging; cancellation restores the original layout. Saved positions use the
settled layout, without changing tmux targets or restarting shells.

Compaction verification: all 11 focused layout tests passed. On the phone, the
clock was dragged down one row and settled back into the row above on drop.
Saved bounds for all six tiles, commands and tmux targets matched the pre-drag
state. The signed build is installed; broad Classic/split checks were not rerun
for this layout-only change.

Arrangement guides use tiny 3 dp squares at grid intersections, with a gentle
1.6-second opacity pulse. Cell outlines are removed; selection handles and
landing previews remain. Only the visible rows are drawn, and the pulse stops
when the editing overlay is hidden.

### Tile frames

Terminal home uses the existing OVERLAYS frame assignment, matching the Classic
Termux pane. Cyberdeck and dashed styles use TerminalBorderRuntime; active
custom frame packs take precedence through that same renderer. The fill remains
a background and the decorative frame is a transparent foreground, drawn after
terminal contents. Plain mode retains the former rounded outline. Seven shared
frame tests passed, and Cyberdeck frames are visible above content on the phone.
Individual custom frame packs were not separately exercised.

### Search bar Cyberdeck frame

Search and Terminal share the INPUT frame assignment. Their bar's fill remains
in the background while its frame is drawn in the foreground. Cyberdeck enables
that border independently of the dashed-border master switch; custom frames
retain precedence. Shared cutout binding supports foreground borders so the
clock and Terminal buttons retain their existing edge cutouts. Four border
unit tests and a phone check covering both mode configurations passed. No
launcher mode or user appearance setting was changed by the phone check.

### Focus alignment at the content edge

The dashboard now enforces its measured minimum height because ScrollView
measures its child with unspecified height. It uses the real content extent,
without adding an empty tail solely to bring the last pane to the top. Focus
scrolling clamps at that content edge, leaving the last pane above the special
keys. Search/date/+ controls remain hidden during focused terminal input even
when Search layout styling runs again. The pane benchmark assertion now checks
the bottom gap rather than requiring top alignment.

The initial content-edge build was installed and inspected on the phone. The
final control-strip correction and instrumentation build compiled, but the phone
disconnected before installation; that final device check is pending.

### Focused terminal blur

While a terminal has keyboard focus, the blur starts at the reserved special-key
area rather than above the pane's lower edge. Visible terminal content stays
sharp. Without terminal keyboard focus, the normal bottom transition remains.
Mask caching includes the transition bounds so changing focus cannot reuse the
previous gradient. The signed app builds successfully; installation and the
combined alignment/blur phone check await device reconnection.

Device reconnection verification: the combined alignment/control-strip/blur
build is now installed in place. On the I2220 phone at 432 dpi override, the
last four-row pane ended 17 px (about 6 dp) above the special keys, with Search
and date/+ controls hidden. A second focus tap kept the same pane position.
The screenshot confirms readable, unblurred content down to its lower edge;
blur is confined to the reserved control area.

### Blur constrained to bottom controls

The bottom terminal blur now starts at the top of the measured persistent
Search controls and ends at their bottom, excluding the outer bottom margin.
The old 72 dp transition above the controls is removed. The gradient is clear
at its upper edge and reaches full blur at its lower edge; the same bounds
follow the special keys when terminal input is focused. Scrolling does not move
the blur region. Android's separate system-wallpaper layer is not part of the
terminal RenderNode and remains unaffected by this terminal-content effect.

The app and instrumentation builds passed and were installed in place on the
wireless I2220. Two device checks passed: opacity preservation and hardware
RenderNode mask bounds at three scroll positions. Screenshots at the final pane
confirm its content stays sharp above the controls. No endurance run was repeated.
