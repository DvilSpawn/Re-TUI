# Re:TUI 6 — Build 807

Build 807 redraws Terminal mode as one terminal pane, gives Dot mode music, notifications and its own settings, lets you share your ASCII art with a community preset, and fixes the music widget, the Mono clock and the battery bar.

## New

### Terminal mode

- Terminal mode is now one pane. At the top, the launcher prints its own fetch: your `ascii.txt`, then your device, Android, uptime, battery, memory, storage, apps and font in two columns, the theme's colours, and live date and time, weather, music and next alarm lines.
- Your Termux sessions sit below the fetch, full width and without frames, each titled with powerline pills: the session's name, then its tmux window.
- The search bar's `$` is a pill too. Search results open above the bar and push the pane up instead of covering it.
- Tap a session to type in it and it fills the pane above the keyboard, so nothing scrolls under your typing. Tap done, or press Back twice, to put it back.
- Type `exit` in a session and it leaves the pane.
- Terminal mode has its own settings, from the ⚙ beside the search bar: the fetch, the colours, the sessions and the weather.
- `terminal_overlay_color` dims the wallpaper in Terminal mode only, in place of `wallpaper_overlay_color`.
- `terminal_pill_color` and `terminal_pill2_color` colour the first and second pill in each title.

### Dot mode

- While music plays, the dot becomes a play button ringed by the track's progress, with ripples pulsing out into the dark. Tap it to pause or play.
- Small pips orbit the dot, one for each app with notifications.
- Tapping the dot, or now anywhere on the black, shows the rings, which now also show the date and your next alarm.
- While you slide through the app rings, the app you are on is written large above them.
- Hold the black around the dot to open Dot settings: the apps, the ink colour, a blinking, steady or breathing dot, ripples, which rings show, the notification pips and the search hint. The hint hides itself after a few searches.
- The dot grid is twice as fine, so the rings are round.

### Presets

- When you share a preset from Community, you can include your `ascii.txt` as the preset's ASCII art. It is off until you tick it, because the art can hold text you may not mean to publish. On the website, the preset form takes a link to the art instead.

## Fixes

- Presets downloaded from Community or the website keep their name instead of showing up as an imported preset.
- In Mono, the lifted terminal's done is a filled button, without the ×.
- In Mono's scroll layout, the Apps, Books and other list panels scroll again. A drag inside a panel scrolls the panel, and once it reaches the end the page carries on.
- With the built-in player off, the Music widget's previous, play/pause and next buttons now control your other music app, the same way the `music` commands do. `preferred_music_app` picks which one.
- With the built-in player off, `music -open` opens `preferred_music_app`, or the app that is playing, instead of saying music is off.
- The Mono clock no longer jumps when you press home or swipe home while already on the home screen.
- `battery_size` now also sizes the battery progress bar. Before, the settings preview showed the new size but the home screen did not.
- `songs_folder` now plays the same formats as media folders: mp3, m4a, aac, flac, ogg, oga, opus, wav, mka, amr and 3ga. WMA is left out, as Android cannot play it.

## Removed

- The Re:TUI Fetch terminal widget. Terminal mode prints its own fetch at the top of the pane.
- `weather_key`. Weather comes from met.no and needs no key, so the setting did nothing. It is removed from your settings when you update, and presets that include it still import.
