# Re:TUI 6 — Build 807

Build 807 lets you share your ASCII art with a community preset and fixes the music widget, the Mono clock and the battery bar.

## New

- When you share a preset from Community, you can include your `ascii.txt` as the preset's ASCII art. It is off until you tick it, because the art can hold text you may not mean to publish. On the website, the preset form takes a link to the art instead.

## Fixes

- With the built-in player off, the Music widget's previous, play/pause and next buttons now control your other music app, the same way the `music` commands do. `preferred_music_app` picks which one.
- With the built-in player off, `music -open` opens `preferred_music_app`, or the app that is playing, instead of saying music is off.
- The Mono clock no longer jumps when you press home or swipe home while already on the home screen.
- `battery_size` now also sizes the battery progress bar. Before, the settings preview showed the new size but the home screen did not.
- `songs_folder` now plays the same formats as media folders: mp3, m4a, aac, flac, ogg, oga, opus, wav, mka, amr and 3ga. WMA is left out, as Android cannot play it.
