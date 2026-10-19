# Issue 23 — shared launcher preview, without a view-cloning framework

Worktree: `dvil/real-launcher-preview`, based on master `e968667`.

## Outcome

Settings shows the configured launcher and its frozen current content. Appearance drafts update that scene without writing preferences, XML, frame selections or global theme/font caches. Save remains the only commit boundary. Compact and expanded inspection use one passive scene.

## Decisions

- Use the existing Android layouts, FrameLayout/ScrollView, Canvas scaling and shared launcher presentation helpers. Add no dependency, permission, public API or second launcher/controller.
- Replace LauncherHomeSnapshot.Node and its arbitrary View/property/drawable copier with explicit launcher content: status data, formatted input/output, suggestion descriptors, dock entries and visible module content.
- Build native content through the same component factories used by Home, with saved appearance on Home and immutable draft appearance in Settings.
- Snapshot only opaque third-party/provider content as a bitmap. Native Re:TUI module text, controls and canvas drawings must remain appearance-aware. Do not execute modules, bind providers, fetch media or retain source action callbacks.
- Keep the preview input read-only and unfocused. Do not reproduce Android's blinking input cursor or fading scrollbar phase. Test content/layout with those transient animations held steady; compare every pixel, with no masks or tolerances.
- Preserve current viewport, configuration, insets, custom margins and scrolling positions. Use Android's normal measure/layout/pre-draw lifecycle rather than copied text geometry.
- Retain the existing wallpaper capture path and OS-wallpaper notice/expanded inspection behavior.

## Agent ownership and order

1. **Module presentation agent** — explicit cached visible-module content and shared native module factories; include Music visualizer data so theme edits recolor the native drawing. Own module presentation files, MusicVisualizerView and the relevant UIManager module methods. No generic View copier or silent native-content bitmap fallback.
2. **Suggestions agent** — explicit classic/Search suggestion content using existing chip/card construction and style helpers. Own suggestion presentation files, SuggestionRunnable and SearchModeResultRenderer. Models contain formatted text and appearance metadata, never app/action/controller payloads.
3. **Preview host/test agent** — keep one scene and move it between compact and expanded hosts; preserve scrolling and drafts. Own SettingsAppearancePreview and focused host tests. Do not weaken visual assertions or mark unrun checks passed.
4. **Integrator** — replace LauncherHomeSnapshot, bind shared static scene/status/input/output/toolbar/dock/layout components, remove name-based restyling and obsolete cursor/scrollbar-copy machinery, integrate the agents and run builds serially.

All agents work in this worktree, preserve unrelated edits, and do not commit, publish, install on the physical phone or close issue 23. Agents report their exact changed files and required integration hooks. The integrator owns Gradle/device execution to avoid conflicting builds.

## Acceptance gates

- No Node/arbitrary-view cloning path, preview engine/provider bindings, action callbacks, or duplicate compact/expanded scenes.
- Source and preview use the same construction/styling for every native component; drafts cannot touch saved state or other surfaces' global preview caches.
- Repeat exact bounds, wrapping, font, color, background and frame comparisons for Classic/Search, portrait/landscape, grouped statuses, keyboard insets, custom margins, font scaling and transparency.
- Compare the draft with newly built Home after Save using frozen content, including native module canvas colors. Verify cancel, recreation, rapid edits and repeated expansion preserve drafts and saved files.
- Run the full unit suite and release lint without bypasses. Then run the key editing/expand/Save/cancel journeys on the signed minified Build 504 APK.
- Retain the reproduced Build 503 Kois exception and comparison screenshots. Deliver an installable APK plus an honest validation report. Keep issue 23 open while any required gate is failing or unverified.

## Completed implementation and validation

- [x] Explicit content models and shared native factories for status, ASCII, output, input, toolbar, dock, suggestions, native module content and Duo controls.
- [x] Generic View/property copier and resource-name restyling removed.
- [x] One passive scene across compact/expanded inspection; drafts and scroll position retained.
- [x] Final debug Android suite: 44 checks passed on the API 35 emulator.
- [x] Eight Classic/Search/Duo viewport comparisons have zero differing pixels. Newly built Home after actual Save matches the frozen draft scene and component pixels exactly.
- [x] Full JVM suite: 475 cases, 470 passed, 5 existing skips, zero failures/errors.
- [x] Signed minified Build 504 APK built; release lint passes without bypasses.
- [x] Final native minified APK edit/expand/Back/Save/recreate/cancel journey passed; signed candidate and comparison screenshots delivered locally.

The Build 503 Kois exception was captured before the old sample carousel was removed: `ColorUtils.calculateContrast` rejected translucent `#626b6056` from `SettingsAppearancePreview.ansi`. The replacement has no ANSI contrast-calculation path.

Native module visualizer recoloring, notes scrolling, custom-font fallback, grouped status roles, keyboard constraints, custom margins, transparent frames, draft isolation and host recreation are included in the device checks. Source and preview use the same layout/style factories. No native module execution, provider binding or source action callbacks are introduced by preview.

Local validation evidence: `.local-context/issue23-simplified-full-device.txt`, `.local-context/issue23-final-evidence-20261019/`, `.local-context/issue23-delivery/` and the final delivery report. Before-simplification safety copy remains under `.local-context/renderer-simplification/`.

## Window diagnostic boundary

The actual Save path refreshes Home in place and creates a new presentation root at the same real viewport. That is now the Save parity oracle: the test requires a new root, matching real viewport/insets and strict full-scene/component pixels. Editor recreation remains covered separately.

A forced full Home Activity recreation on the API 35 emulator changed the content root from 1600 to 1558 pixels under a 1600-pixel decor with identical reported navigation insets. The ineffective experimental window change was removed. This remains a separate follow-up; no preview was resized to hide the difference. Android has a related [preserved-window decor fitting fix](https://android.googlesource.com/platform/frameworks/base/+/6f304be5817ada1b435319edab9de3e055bb46d6), but the exact cause of this emulator observation is not confirmed. Raw diagnostics remain in `.local-context/issue23-save-window-diagnostic/`.

## Minified test harness

The native release instrumentation uses only platform APIs and resource names because R8 removes app-internal resource wrapper classes. The existing `prepare_termux_ssh_release_tests.py` runtime-isolation helper removes the separate test APK compatibility DEX so it uses the application's already-minified Java backport runtime. Only the test APK is repackaged and signed; the production APK SHA-256 is checked unchanged. No new production keep rules, dependencies or lint exclusions were added.

The final native journey passed with the exact signed candidate. Physical-phone verification and older Android versions were not repeated in this run. Issue 23 remains open pending review of this candidate and the separately recorded full-Home-recreation viewport observation.

## Accepted expanded-preview backdrop change

On October 19 the user accepted background blur behind expanded inspection. Reuse `PanelBackdrop.installWindow` for that dialog, including its existing dimming fallback and attach/detach listener cleanup. The physical Vivo confirms a sharp preview over a blurred editor, Back restores the editor, and saved XML remains unchanged. The updated minified candidate passed the editing/Save/recreation/cancel journey again. This replaces the wallpaper-only expanded-backdrop requirement for this delivery.

## Accepted idle module preview

Settings inspection shows the captured open module, or the real Music idle layout if no module is open, so module appearance edits have a visible target. No media controller is started. This is opt-in for Settings only; exact Home snapshot comparisons retain captured content by default. Physical-phone debug verification passed; minified release rebuild for this addition remains outstanding.

## Superseding scope: readable component cards

The user rejected the full-home thumbnail workflow. Settings now uses three inline, swipeable cards: Status, Terminal/Input, and Music Module. Shared Home presentation binders receive saved values plus pending edits. Representative content stays read-only. Remove full-scene capture from the editor, viewport scaling, wallpaper capture, scene transfer and the expanded dialog. Preserve Save/Cancel and draft recovery. The earlier full-screen expansion tests are historical and must be migrated before release; the new card regression checks draft coloring, passive module controls and saved-state isolation.

### Swipe-only Help and Calculator follow-up

Removed the top navigation tabs. The inline pager now has Status, Terminal, Music, Help and Calculator examples. Help and Calculator inflate their shipped layouts without Activities or engines; draft colors, font and border values use the existing appearance and border renderers. Tall panes scroll within the preview. No actions execute.

Debug build and the focused card/draft isolation instrumentation test pass. All five pages were swiped on the physical Vivo; saved XML hashes remain unchanged. Full-suite and minified-release validation of this revised preview remain outstanding.

### Targeted third-pane minified regression

On 2026-10-19, signed minified Build 504 passed 72 verified third-pane arrivals on Android 15/API 35. Real touchscreen swipes covered Theme, UI and Behavior editors, two opens each, with local Kois plus opaque, #626B6056 translucent and fully transparent backgrounds. Each case used a fresh process; saved XML remained unchanged and the crash buffer was empty. APK SHA256: `f6be623ca961c7fb34420f8330b57663c6a63f01867054062ac82921ff8340dc`. Evidence: `.local-context/issue24-phone/third-pane-report.md`. This closes the targeted swipe validation gap, not the remaining broad release gates or issue #24 root-cause attribution.
