# Re:TUI 6 — Build 800

Re:TUI 6 adds a full media player for music, podcasts and video, a new two-colour Mono home, community presets you can download and share from inside the launcher, and one tabbed edge stack in place of the sidebar and its pills.

## One media player

- Music, podcasts and video now live in one player with Library, Now playing, Queue, Podcasts and Video tabs. Open it with `music -open` or by tapping the title in the music panel.
- Music and podcasts share one queue that survives restarts and is included in backups. Add, remove, reorder, shuffle and repeat.
- Play your own files: `media -add` picks a folder through Android's folder picker, with no storage permission. `media` lists your folders, `media -rm` removes one and `media -scan` looks again.
- Sleep timer, equaliser with presets, gapless playback, saved lists and search across the whole library.
- Lyrics from a `.lrc` or `.txt` file beside the song, including `[offset:]`.
- Podcasts gain chapters, playback speed, skip silence, notes, downloads, and NEW and OFFLINE shelves.
- Streams are cached ahead (30 minutes, or 10 on metered networks) and recover from dropped connections by themselves.
- The system media controls, lock screen, headset and Bluetooth buttons control playback. Playback pauses when headphones are unplugged.
- Video keeps its own full-screen player with picture-in-picture, next-up, an unwatched filter and autoplay that skips watched episodes.
- Music keeps playing when the launcher restarts.

## Mono mode

- `mode mono` draws Home in two colours, ink and paper, with the VT323 pixel font. Your other settings are left alone, so switching back restores them exactly.
- A status strip, a dithered weather banner that animates with the weather and time of day, and one session pane with notes, events, reminders, notifications, music, timers and your commands with their answers.
- An input line under the session runs commands and searches apps, modules and the web.

## Community presets

- `preset -market` now has a COMMUNITY tab next to the terminal themes. Browse presets made by other Re:TUI users, sorted by newest or most popular.
- Open one to see its screenshot, what it changes (colours, suggestions, layout, behaviour) and whether it comes with a wallpaper or a font link.
- Download & import checks the file first and saves it beside your presets. Your look stays the same until you run `preset -apply`.
- A preset's wallpaper can come with it and is set when you apply the preset.
- + SHARE MINE sends your own setup for review: pick what to share, add a screenshot and an optional wallpaper. It appears for everyone once approved.

## Edge stack

- The edge sidebar and its timer, stopwatch and podcast pills are now one tabbed stack on the screen edge.
- Tap a small tab to bring it forward, tap the home tab to return to the sidebar, hold to open the sidebar, and drag to move the whole stack.

## Toolbar

- The app drawer button has its own setting, `show_apps_drawer_button` under Toolbar, separate from swipe-up. If you had swipe-up turned off, the button stays hidden after the update, as before.

## Wallpapers and looks

- Doom fire has a custom look. Pick your own background, ember, flame and tip colours.
- Settings › System & Support uses the official GitHub, Discord and Reddit logos.

## Modules

- Lua modules can make text tappable. Bookmarks 1.0.2 opens a link when you tap it.
- With a module open, the output shrinks to its tab before notification rows push anything off screen.

## Fixes

- Dot mode: a command's answer shows over the dot. Home or Back brings the dot back. Output nobody asked for no longer covers it.
- A running or paused timer comes back after Android closes the launcher. A timer that ran out meanwhile is not rung late.
- Tapping an ended or disconnected terminal tile reconnects it.
- Adding a podcast selects the new show.
- Mono's music line stays correct after switching modes.
- The video library highlight clears when the player closes.
- Fixed a crash in suggestions, kept the keyboard state across rotation, kept focus on search after a mode switch, and stopped showing unfilled notification formats.
- Bare parameter commands, such as `apps`, show their help. `breach`, `credits`, `lockdown` and `pomodoro` now have help too.
- Dot mode: closing the changelog or another screen no longer brings the keyboard up over the dot.
- Android no longer tints the dot and mono homes grey.
- The Hack overlay closes when you tap it.
