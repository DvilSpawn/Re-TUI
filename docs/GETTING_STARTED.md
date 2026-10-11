# Getting Started with Re:TUI

New installations use a required, three-page startup (menu version 4). Choices
stay staged until the final **Build my launcher** action. Existing completed
installations are not asked to configure again.

## 1. Welcome and quick guide

The first page explains Re:TUI as a terminal-style home screen, without assuming
terminal experience:

- Type an app name and choose its suggestion to launch it.
- Use suggestion chips to discover apps, commands, and options.
- Enter `calc`, `help`, or `settings` to open everyday tools.

Select **Continue** to configure the launcher. There is no Later or Skip action
for a new installation. Back on this page keeps startup open. Android Home and
task switching remain available; returning to the launcher resumes setup.

## 2. Configuration

Choose **Basic** or **Advanced**, wallpaper colors, and border style. Controls
save the draft without applying it. Back returns to the introduction. Closing
or restarting the app preserves the page, choices, and configuration scroll.

Basic enables weather/unlock-count labels and double-tap lock. Optional drawers,
module dock, integrations, notification display, sounds, and effects stay off.
Advanced exposes independent switches for those features. Android device-admin
access for locking is requested on first use. Integration switches do not
install Termux/Tasker or grant Android permissions.

Auto-theme applies the current wallpaper palette once, with a system fallback.
It does not continually change the theme with future wallpapers. The startup pane
and every card, button, switch and pressed state preview Dashed, Solid, or None
without writing launcher settings. None keeps selection fills/checkmarks but
removes all setup outlines. Dashed
reveals live sliders for stroke width (0.5–8 dp) and gap (1–24 dp), initially
1.5 dp and 4 dp. Switching away and back retains those values. Solid uses no gap,
and None disables generated borders. Classic
remains the renderer beneath independent Cyberdeck and CRT effects.

Select **Continue** to review the final page. Nothing has been applied yet.

## 3. Thank you and build

The last page thanks users for trying this independent project, welcomes
feedback and support, and summarizes the selected appearance, features, and
effects. Back lets users revise their choices.

**Build my launcher** prints display-only pseudo-code line by line alongside a
progress bar, using the chosen palette, border geometry, features, and effects.
The bar stays visible while the log scrolls. While the wallpaper palette is
prepared, the choices remain staged. The selected settings are applied before startup
is marked complete and the launcher is revealed. Build failures retain the
draft and allow retry. Recreating or restarting during the build resumes it.
System-disabled animations remove the decorative delay.

All startup choices remain editable through Settings afterward. Startup does
not select between Classic and Search launcher modes.

## Preview without changing your launcher

Run `guide -startup-test` to rehearse all three pages and the build animation.
The header says **STARTUP · DRY RUN**. Preview uses a separate draft: it cannot
replace real onboarding progress or apply launcher settings. **End Preview**
is available on the welcome page; **Finish Preview** rehearses the final build.

## Installation and recovery

- Completion is recorded explicitly, not inferred from APK update timestamps.
  An update before the first launch does not count as completed onboarding.
- Existing configuration without startup metadata is treated as a legacy
  installation, so upgrading does not overwrite a configured launcher.
- An incomplete older startup is upgraded to the welcome page while retaining
  its staged choices.
- Personal backups include real startup progress/completion, but not previews.

The older screenshots under `images/getting-started/` document version 3 and
are not examples of this three-page flow.
