# Re:TUI 6 — Build 804

Build 804 fixes backups, makes Mono and Dot work across two screens, and shows the settings that matter for the mode you are in.

## Fixes

- Backups work again. Since Build 801, making a backup stopped with "duplicate entry: shared_prefs/retui_focus_friction.properties" and saved nothing. Backups made with this build restore as before.
- Dropping the edge sidebar after dragging it no longer flashes the tab at its old place, and the drop melts into the tab instead of swapping for it. (#35)
- `config -set` with a value a setting does not accept now says what it accepts, instead of printing an error trace.

## New

- **Mono and Dot on two screens.** On a wide window, a foldable opened like a book or a Surface Duo, Mono puts its pages on one screen and the command line with its answers on the other, and Dot keeps the dot on one screen with the search bar always showing on the other. A `<<` / `>>` key swaps the sides. With `duo_mode` on, `duo left` and `duo right` keep the whole phone layout on one screen, as in Classic.
- **Settings for your mode.** Settings pages list the settings that change the home mode you are using, such as Mono's own and not Classic's status lines. A button next to Save shows all modes, and search still finds every setting, marking the ones for another mode.
- **Sidebar pill colour.** `edge_sidebar_pill_color` (Behavior › Edge Sidebar) colours the pill inside the sidebar tab; auto keeps the terminal border colour. (#35)
- The Mono clock fits short and narrow panels.
