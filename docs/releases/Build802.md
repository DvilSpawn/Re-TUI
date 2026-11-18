# Re:TUI 6 — Build 802

Build 802 answers the feedback on GitHub: colours inside status labels, a module that stays open on the home, a sidebar you can colour, search inside each Settings section, and two sidebar and Settings fixes.

## New

- Status labels can colour their parts. In a format such as `ram_format`, `[#FF0088]` starts a colour that runs until the next tag, and `[/]` returns to the label's own colour: `[#FF0088]RAM [#00BFFF]%avgb GB / %totgb GB`. Eight digits (`#AARRGGBB`) add transparency. Works in the RAM, storage, battery, network, time, device, weather and unlock formats.
- `home_default_module` (Behavior) opens a module by itself on the home, such as `notifications`: when the launcher starts, including after Android closes it, and when you press Home with no module open. The notifications module is a live list, so dismissed notifications leave it.
- The edge sidebar has its own bezel colour, text colour and opacity (Behavior › Edge Sidebar). Colours left on auto look as before. Opacity lets the home show through the bezel; its icons, and a running timer's numbers, keep a solid patch so they stay readable over anything.
- Appearance, Behavior, Personalization, Integrations and System & Support each have a search box that searches just that section.
- The document editor (`config -file`) has a text size button next to FIND and REPLACE: tap it to step through x1, x2 and x3.

## Fixes

- The edge sidebar no longer flashes at its old spot when it settles after being dragged.
- The Reddit button in System & Support is drawn in one colour like the GitHub and Discord buttons.
- The document editor's margins and buttons line up with the Settings hub.
