# Preset Sharing

Presets are the current Re:T-UI way to keep, re-apply, and eventually share theme states.

## What A Preset Captures

Today, presets are built around the current visual state of:

- `theme.xml`
- `suggestions.xml`

That means a preset captures the colors you actually ended up with after:

- manual theming
- `wallpaper -auto`
- small follow-up tweaks

## Core Commands

- `preset -save <name>`
- `preset -apply <name>`
- `preset -ls`

## Recommended Creation Workflow

If you want a preset worth keeping:

1. Set wallpaper
2. Run `wallpaper -auto`
3. Confirm the palette looks good
4. Tweak any colors you want in the settings hub
5. Save the result with `preset -save <name>`

That sequence gives you a stable snapshot instead of a temporary auto-color state.

## Important Behavior

If you run `wallpaper -auto` and do not save a preset, auto-color remains in charge.

If you apply a preset, that preset becomes the stable theme state again and auto-color is no longer the active source of truth.

## Naming Advice

Preset names work best when they describe the vibe or wallpaper source clearly.

Examples:

- `forest-dusk`
- `mono-red`
- `pixel-night`
- `amoled-amber`

## Overwriting An Existing Preset

If you reuse the same preset name, you are effectively replacing the previous version.

That is useful when you treat a preset name as a living theme slot instead of a historical archive.

## Sharing Strategy

Re:T-UI is not yet using a full packaged preset export/import format as the main flow.

For now, the most realistic sharing approaches are:

- publish screenshots plus the preset name and setup notes
- share the related config files or preset folder contents manually
- maintain a community collection in GitHub or Reddit posts

## Suggested Community Flow

If the community starts actively trading themes, the cleanest pattern would be:

1. Screenshot
2. Wallpaper source note
3. Font name
4. Preset name
5. Optional config bundle or preset folder contents

That keeps presets social without pretending there is already a polished theme marketplace.

## Practical Advice

If you care about a look, save it before experimenting further.

The safest rule is simple:

- auto-color for discovery
- presets for permanence
