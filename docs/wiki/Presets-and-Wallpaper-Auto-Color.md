# Presets and Wallpaper Auto Color

Re:T-UI separates two ideas that are easy to confuse:

- automatic color generation from the current wallpaper
- saved manual theme states

## Wallpaper Auto Color

Run:

`wallpaper -auto`

This tells Re:T-UI to derive its colors from the current wallpaper.

The launcher tries to build a usable palette automatically:

- darker colors for the terminal background
- lighter colors for text and borders
- coordinated suggestion and widget colors

## Wallpaper Pickers

- `wallpaper` opens the normal wallpaper picker
- `wallpaper -live` opens the live wallpaper chooser

## Presets

Presets are the Re:T-UI replacement for the old theme-sharing workflow.

Commands:

- `preset -save <name>`
- `preset -apply <name>`
- `preset -ls`

## Recommended Workflow

1. Set a wallpaper
2. Run `wallpaper -auto`
3. Tweak any colors you want
4. Save the result with:
   `preset -save <name>`

That gives you a reusable snapshot of the look you actually want.

## Important Detail

If you do not save the result as a preset, auto color remains the active source of truth.

If you save a preset and later apply it, that preset becomes your stable theme state again.

## Suggestions

Preset names are included in command suggestions, so you do not need perfect memory to reapply or overwrite them.
