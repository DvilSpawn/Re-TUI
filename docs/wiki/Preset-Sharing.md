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

Use Settings > System & Support > Create Shareable Configuration.

That flow lets you export either:

- the current active look
- one of your saved user presets

The exported file is visual-only. It contains `theme.xml`, `suggestions.xml`, and manifest metadata. It does not include modules, aliases, app lists, tokens, contacts, scripts, or other personal backup data.

## Suggested Community Flow

For the preset marketplace:

1. Create a shareable configuration.
2. Take a screenshot of the look applied in Re:T-UI.
3. Attach the wallpaper separately if the look depends on it.
4. Upload the shareable file and screenshot to the marketplace.

That keeps presets social without asking users to share full backups.

## Practical Advice

If you care about a look, save it before experimenting further.

The safest rule is simple:

- auto-color for discovery
- presets for permanence
