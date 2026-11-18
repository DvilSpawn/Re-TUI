# Re:TUI 6 — Build 803

Build 803 fixes a crash when applying the new wallpapers and two problems reported after Build 802.

## Fixes

- Applying Touch Grass, Matrix Rain, Astro Chart or Clouds while another live wallpaper was showing could close the launcher, usually the next time the screen turned on. These wallpapers now start cleanly over any other.
- With `enable_music` off, the `music` commands (play, pause, next, previous, stop) now control the music app you are using, such as VLC or Spotify. Set `preferred_music_app` to pick which app they go to; left empty, they go to whichever app is playing. (#36)
- Removing a panel from the Mono home no longer brings it back when you add a page or save other settings. Settings now only saves the fields you actually changed, and reloads its values when you return to it.

## Wallpaper gallery

- The gallery can hold a wallpaper back until your launcher is new enough to run it, so you only see wallpapers that work on your build. Touch Grass now needs Build 803.
