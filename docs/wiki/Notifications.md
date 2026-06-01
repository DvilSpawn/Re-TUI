# Notifications

Re:T-UI supports two different notification surfaces:

- the output terminal
- the notifications module

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

Re:T-UI now includes a dedicated notifications module on the home screen.

It is designed to:

- stay below the music module
- remain visually consistent with the terminal theme
- show one selected notification at a time
- open the selected notification
- support previous/next paging
- support inline reply when Android exposes a reply action
- compact when the keyboard opens

## Paging

The notification module is a pager. It shows the selected notification with an `index / total` header, app name, title/body text, and a `reply available` line when the selected app has a bound reply action.

Commands:

- `notifications -prev`
- `notifications -next`
- `notifications -open`

The module pane uses a vertical split:

- top 80%: notification content
- bottom 20%: previous and next controls, split 50/50

This makes navigation easier to see while keeping the notification content readable.

The pager controls are quiet actions. Tapping `prev` or `next` updates the module without printing repeated `$ notifications -next` or `$ notifications -prev` lines into the output tray. Manually typed notification commands still appear in terminal history.

## Reply

Use:

```text
notifications -reply
```

or the `reply` suggestion while the notifications module is active.

Re:T-UI starts a native input prompt, then sends the typed text through Android's inline reply path for the selected notification. This uses the notification action's `RemoteInput`, the same Android mechanism used by notification shade replies.

Reply behavior:

- the selected notification is locked while the reply prompt is active
- new incoming notifications do not change the reply target
- previous/next navigation is disabled until the reply is sent or cancelled
- non-replyable notifications show an error instead of opening a prompt

Before replying to an app, bind its reply action:

```text
reply -bind <app or package>
```

You can inspect bindings with:

```text
reply -ls
```

Some apps may expose multiple conversation notifications. Re:T-UI replies through the selected notification action, not just the package name, so replies go to the intended conversation.

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
