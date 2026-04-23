# Music Visualizer

Re:T-UI includes a lightweight terminal-style music surface.

The current direction is intentionally not a full media widget. It is a terminal visualizer with useful metadata.

## What It Shows

- song title
- singer / artist
- animated visual bars
- terminal-styled border and label

## Behavior

- appears when music is active
- hides when playback stops
- can prioritize a preferred music app

## Preferred Music App

You can choose the preferred music app from:

- `settings -music`
- `themer > Integrations`

This helps the launcher decide which media session to trust first when multiple apps expose playback state.

## Philosophy

This feature used to drift toward a full transport-control widget.

Re:T-UI intentionally pulled it back toward something more honest:

- lightweight
- visually fun
- terminal-native
- not pretending to replace a real player UI
