# Re:TUI 3 — Build 503

This update makes the launcher's panes more consistent, rebuilds widget editing, and brings presets, wallpapers, and optional tools into the launcher's download flow.

## A better widget pane

- Scroll through your widget board and find widgets through a searchable picker with previews.
- Move widgets directly and resize them using four subtle corner handles.
- See widgets resize while dragging, within the limits supported by each widget provider.
- Settings and Widgets now stay expanded, with no floating-window controls.
- Transparent pane backgrounds reveal the launcher through blur, including behind the status bar. Devices without blur support use dimming.

## Termux, connected through SSH

- The Termux pane now uses a local SSH connection, while Termux and tmux manage your shells and running jobs.
- Keep the familiar split panes, workspace pages, launcher fonts, and colors.
- Guided setup installs and configures the required Termux support.
- Improved reconnection and session restoration when returning to the workspace.
- Saved Termux Apps appear in launcher suggestions for quickly reopening your terminal tools.
- Empty workspaces no longer show an unnecessary window chooser.

## Download what you want

- Browse and preview 21 curated terminal color presets with preset -market.
- Downloaded presets are imported without changing your current appearance. Apply one when ready with preset -apply <saved-name>.
- Preview optional wallpaper packs inside the launcher, confirm the download, and install them automatically.
- Download and install the optional Qalculate engine directly from the calculator.
- Browse and install native launcher modules with module -library.

## Settings and theme improvements

- Preview appearance changes before saving them.
- Improved settings persistence and recovery of unfinished edits.
- Better readability for transparent and low-contrast themes, plus fixes for cursor colors and panel borders.
- More reliable preset imports from browsers and Android's file picker, with themed error screens and clearer recovery options.
- Improved backup and interrupted-restore recovery, including preservation of older modules during upgrades.

## Everyday fixes

- Fixed basic calculator expressions showing preview unavailable.
- Added haptic feedback to calculator keyboard controls.
- The default-launcher prompt now appears after the preparation animation finishes.
- Setup progress uses terminal glyphs.
- Reduced repeated work when returning Home from another app.
- Improved landscape pane alignment while respecting system and custom margins.
- Improved IRC history refresh and added message deletion where supported by the server.

## Optional Private Space support

Enable the private command under Settings → Behaviour to access Android Private Space on supported devices.

This feature is off by default. It requires Android 15 or later and a compatible implementation of Android's standard Private Space APIs. Some manufacturer implementations, including the Vivo device tested during development, may authenticate successfully but still prevent app launching.

Authentication cancellation and retry handling have also been corrected.
