# FAQ and Troubleshooting

This page covers the most common “why did that happen?” moments in Re:T-UI.

## The launcher did not visually refresh after a theme change

Run:

`restart`

This is the fastest way to force a clean UI reload after major visual changes or direct file edits.

## `wallpaper -auto` did not seem to change anything

Check these first:

1. Make sure the wallpaper actually changed before running the command
2. Confirm `auto_color_pick` is enabled in runtime state with:
   `debug -settings`
3. Restart if the palette looks stale:
   `restart`

If you want to keep the new look, save it immediately:

`preset -save <name>`

## My font reverted after a restart or reload

Run:

`debug -settings`

Check:

- `system_font`
- `font_file`

If `system_font` is `true`, the launcher will prefer the system font.

If you want a custom font, confirm it again in:

- `themer`
- `Appearance > Fonts`

Then save and retest.

## Notifications are showing in the wrong place

There are two separate notification surfaces:

- notification terminal widget
- output terminal history

Use `notifications.xml` and runtime checks to separate them clearly.

Helpful command:

`debug -settings`

Look for:

- `notification_terminal`
- `notification_output`

If you want notifications only in the widget, keep the widget enabled and turn off output printing.

## Notification access is not working

Open Android’s notification access screen:

`notifications -access`

Then verify Re:T-UI is allowed.

## The app drawer is showing apps I hid

Refresh and check hidden state:

- `apps -lsh`
- `refresh`

If the app is not in hidden apps, hide it again:

`apps -hide <app>`

## My preset does not look like I expected

A preset is only as good as the state you saved.

Recommended recovery flow:

1. Apply the preset again:
   `preset -apply <name>`
2. Run `debug -theme`
3. Confirm effective colors
4. Re-save the preset if you intentionally changed the theme:
   `preset -save <name>`

## The settings hub looks wrong or opens strangely

Try:

`restart`

If the issue persists, use:

`debug -settings`

The current settings hub is designed to behave as an in-place terminal popup, with the wallpaper visible outside the panel. If that stops happening, it is worth checking whether a recent build changed the activity flow.

## Music widget behavior looks off

Useful checks:

- `settings -integrations`
- `debug -settings`

Look for:

- `music_enabled`
- `music_widget`
- `music_preferred_package`

The current music UI is intentionally more of a visualizer than a full Android widget clone.

## What is the best command when something feels haunted?

Use:

`debug -settings`

That command gives the quickest snapshot of:

- auto-color state
- font state
- notification state
- music state
- effective colors

If that output matches your expectation and the UI still does not, then the problem is usually in reload timing, not saved settings.

## I want the launcher to feel more stable while I experiment

Use this workflow:

1. change one thing
2. save
3. verify
4. save as preset if you like it

Re:T-UI rewards iterative changes much more than giant one-shot edits.
