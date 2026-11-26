# Re:TUI 6 — Build 808

Build 808 fixes colour tags in Settings and the time format, two edge sidebar glitches, `music -info` with other music apps, and autofill pop-ups over the command line.

## Fixes

- Colour tags such as `[#FF0088]RAM [#00BFFF]%avgb GB` now show as colours in the Settings preview while you edit a format. Before, the preview showed the tags as text. ([#26](https://github.com/DvilSpawn/Re-TUI/issues/26))
- Colour tags work in `status_time_format`. Before, a tag there left `[]` in front of the time, and its letters could be read as date codes. A bare `#RRGGBB` outside a tag still colours the whole line, as before.
- With a see-through edge sidebar, the pill on the sidebar tab no longer shows through the open sidebar. ([#35](https://github.com/DvilSpawn/Re-TUI/issues/35))
- When you drag the edge sidebar to a new place, the drop now settles exactly where the tab appears, so there is no second bar beside the tab as it lands. ([#35](https://github.com/DvilSpawn/Re-TUI/issues/35))
- With the built-in player off or idle, `music -info` shows what your other music app is playing: title, artist and position. ([#36](https://github.com/DvilSpawn/Re-TUI/issues/36))
- Autofill pop-ups, such as saved phone numbers, no longer appear over the output while you type in the command line or a search bar.

## Good to know

- Track details from other music apps, in the Music widget, `music -info` and `auto_show_music_widget`, need notification access for Re:TUI (Android Settings › Notifications › Device & app notifications, or Notification access). Without it, the widget buttons and `music` commands still control playback, but the launcher cannot see what is playing.
