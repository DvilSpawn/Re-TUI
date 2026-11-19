# Re:TUI 6 — Build 806

Build 806 gives Mono a landscape layout for phones and fixes its landscape spacing.

## New

- On phones held sideways, Mono shows each page in one frame. The status strip runs across the top, the page's panels sit in a left and a right half, and the soft keys sit under one half with the command line under the other. The `<<` key swaps their sides. Tablets, foldables and the Duo keep the two-screen split.

## Fixes

- Mono in landscape left a gap beside the camera cutout twice as wide as the cutout, because the space was counted twice. It now leaves just the cutout's width.
- On wide screens, where Mono and Dot split into two halves, the left half is no longer cut off at its edge, and the halves are centred in the usable part of the screen instead of the whole width.
