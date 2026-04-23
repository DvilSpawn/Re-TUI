# Notifications

Re:T-UI supports two different notification surfaces:

- the output terminal
- the notification terminal widget

## Access

To enable Android notification access:

`notifications -access`

## Main Commands

- `notifications -on`
- `notifications -off`
- `notifications -inc <app>`
- `notifications -exc <app>`
- `notifications -color <color> <app>`
- `notifications -format <format> <app>`
- `notifications -add_filter <id> <pattern>`
- `notifications -rm_filter <id>`
- `notifications -file`

## Notification Terminal

Re:T-UI now includes a dedicated notification terminal widget on the home screen.

It is designed to:

- stay below the music widget
- remain visually consistent with the terminal theme
- open the originating app when tapped
- compact when the keyboard opens

## Output vs Widget

These are separate behaviors.

### Show notifications widget

Controlled by notification terminal settings.

### Print notifications into output

Controlled by `terminal_notifications` in `notifications.xml`.

If you want notifications visible only in the widget and not in the output history, disable output printing and keep the widget enabled.

## Rules and Filtering

Current notification rules are intended to control:

- which apps are allowed
- which apps are excluded
- which patterns are filtered out

That rule layer is the important part.

The widget is intentionally simple in presentation so it remains readable and thematically consistent.
