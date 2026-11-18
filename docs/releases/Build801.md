# Re:TUI 6 — Build 801

Build 801 turns the Mono home into a handheld PDA with panels you arrange yourself, adds an e-ink eReader and live TV channels, lets you choose what a preset keeps, makes personal backups a full snapshot of the launcher, and adds the Touch Grass wallpaper.

## Mono home, PDA style

- Mono's home is now a set of panels on a four-column grid inside a handheld frame: a status strip with the name, signal, time and battery, panels with title tabs, an output panel over the command line, and soft keys for Apps, Search, Widgets and Settings.
- Panels: a pixel-art weather scene (a skyline with lit windows, sun or moon and stars, clouds, rain, snow, fog and lightning), a clock with date, week and battery, storage and memory meters, apps, media, quick commands, tasks, what's next and alerts.
- Apps shows each app as a name chip, in scrolling sections: the most used first, then each of your app groups.
- Hold a panel to change its size, move it up or down the page, swap it for another or remove it. Read, tasks, next, alerts, quick, apps and media come in more than one size, and small panels stack beside tall ones. The add panel slot at the end of a page adds one. The layout is kept in the `mono_layout` setting.
- Panels sit on pages you swipe between, with squares above the command line to show the page; hold them to add or remove a page. The command line stays put on every page. Set `mono_layout_style` to scroll for one page that scrolls down instead.
- Terminal panels: each one shows a window of the tmux workspace, drawn in ink. Tap one and it fills the screen above the keyboard, with the terminal keys under it; hold text to copy it or empty space to paste. Back or done puts it back in its place. Add as many as you like, in three sizes; hold one on the home to change its size or window.
- A swipe or scroll that starts on a panel no longer opens its menu by mistake.
- Panels run only while Home is in front and draw only in ink, paper and dimmed ink.
- Mono now defaults to blue ink on navy. If you never changed the old cream on black, you get the new pair. Colours you picked yourself are kept.
- The CRT filter and vignette now work in Mono when they are turned on.
- The Settings soft key opens the settings that change the Mono home: mode, ink and paper, panel layout, CRT filter and vignette, and weather. All settings still reaches the full hub.

## Live TV

- The media player has a TV tab. Import channels from an M3U playlist, from a file or an https link, and play them in the video player.
- In Mono, the media panel plays your last channel dithered in ink. It stops when you leave Home and offers to resume it when you come back.
- Full screen plays it in colour from where the panel was, and Back or Home returns it to the panel from where full screen was. A live broadcast always rejoins live; a video at a link keeps its place.
- The media panel's library button opens channels, videos to carry on with, shows and your music without leaving Home.
- Channels and playlists on plain http:// are not encrypted. Re:TUI explains the risk before adding them, and leaves them out if you skip.

## eReader

- `books` opens a book library in the launcher's terminal style. ADD FOLDER picks a folder through Android's folder picker, with no storage permission; OPEN FILE opens a single book.
- Reads EPUB, PDF, plain text, Markdown and CBZ comics. The library filters by kind and by name, and keeps the book you were reading at the top.
- Pages look like e-ink: dark ink on grey, warm, cool or night paper, with a faint grain, set in Literata. A THEME look uses your launcher colours instead.
- Turning a page leaves the last one faintly behind, fading over the next few turns, like an e-ink screen. Ghosting can be faint, strong or off.
- A refresh can wash the screen grey and back every 6 turns or every turn, clearing the ghosts, like an e-ink panel's full refresh.
- Tap the outer quarter of either side to turn, swipe, or use the volume keys; tap the middle for the menu. Page direction is left to right, right to left (for manga) or one long scrolling column, set separately for text books and for PDFs and comics.
- Hold a word to select it and drag the handles to stretch the selection. Copy, Share, Pin, or hand it to any app that works on text, such as a translator or dictionary.
- Pins: pin lines from the selection, or pin the page you are on from the menu. The PINS tab lists pinned lines as quotes with their chapter and page, then pinned pages. Tap to go there, hold to copy or remove; SHARE ALL sends every pinned line as text.
- The footer cycles on a tap: time left in the chapter, time left in the book, pages left in the chapter, or how much you have read. Time estimates learn how fast you read.
- The menu has CONTENTS, PINS, TEXT (font, size, margins, line spacing, justification), PAGES and E-INK tabs, plus search in the book.
- PDFs and comics show their pages in sixteen greys; double-tap to zoom in and drag to look around.
- Your place, pins and settings are kept. `books -last` carries on where you stopped; `books -last comic` (or epub, pdf, text) carries on with the last book of that kind.
- In Mono, a READ panel shows your book and the last one of each other kind, and Quick has a read row. In the other modes, the READ module does the same.

## Mono colours and clock

- Mono's settings have a Preset: e-ink lite (dark ink on grey paper), e-ink dark (light ink on dark paper) or custom, which shows the ink and paper pickers for your own pair.
- Cyberdeck frames now work in Mono.
- The clock panel shows a running timer under the time, then your next alarm from the clock app, a DAY meter for how much of the day has gone, and AM or PM on a 12-hour clock.
- The edge sidebar inverts to an ink bar when your text colour is too dark to read on black, so it stays visible on a light Mono home.

## Presets and backups

- `preset -save` asks what the preset keeps: colours, look, mode, Mono, behaviour and wallpaper. Anything left out stays as it is when the preset is applied.
- A preset saved while Mono is on keeps your own colours and fonts, not Mono's.
- A personal backup is now a full snapshot of the launcher: it adds the journal, quick notes, TV channels, the reader's places and pins, media folders, video positions, Terminal-mode tiles, keyboard shortcuts and the images behind Re:TUI wallpapers. An older backup only replaces what it held, so restoring one keeps newer data.
- Help explains what a backup cannot carry: Android widgets, folder permissions on a new phone, and a wallpaper set by another app.

## Touch Grass wallpaper

- A downloadable wallpaper pack: a lawn seen from just above. Drag a finger through it and the grass parts, then stands back up from the start of the path; tilt the phone and the tips lean the other way. Leave it alone and it reminds you to touch grass.
- Get it from Get more wallpapers in the wallpaper picker. Inspired by Dylearn's Stylised 3D Pixel Grass demo; drawn by Re:TUI's own code.

## Help

- New articles on the eReader, the Mono home and live TV. The modes article covers all five modes, and What's new lists this build.

## Modes

- `mode` on its own suggests every other mode.
- Dot mode shows a faint "double-tap the dot to search" hint at the bottom.

## Fixes

- Mono search results stay readable after presets with transparent suggestions or coloured app groups. The results no longer push the input line and app row under the keyboard.
