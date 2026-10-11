# Calculator

`calc` opens a terminal with an attached keypad dock. Enter an expression after
`calc>` and press Enter or ↵. The live prompt, expressions and results share one
scrollable monospace terminal; there is no input frame or separate result display.
`calc <expression>` still prints a one-shot result in the launcher terminal.

```
calc> 5 ft to miles
≈ 0.000946969696969697 mi
calc> *2
≈ 0.001893939393939394 mi
calc> ans to ft
10 ft
```

Use `to` for conversions: `in` means inches, not a conversion operator.
Specify the output unit explicitly: `61d to min` converts 61 days to `87840 min`.
Without a target, Qalculate automatically chooses an output unit. Use `min` for
minutes; `m` means meters. In the keypad, enter units with ABC and find `to` in FUNC.
Start with an operator to continue the last successful result without typing `ans`:

```
calc> 5 + 5
10
calc> +5
15
calc> *2
30
```

Continuation accepts `+`, `-`/`−`, `*`/`×`, `/`/`÷` and `^`, retaining units and
grouping. A complete expression starts fresh. After a result, `-5` subtracts 5;
use `(-5)` to enter a standalone negative value. With no previous answer,
signed numbers keep their ordinary meaning. Assignment right-hand sides also
start fresh: `ans = -5` sets the answer to negative 5, not the previous result minus 5.

`ans` is optional: it explicitly inserts the last successful result inside an
expression such as `sqrt(ans)`, `100 / ans` or `ans to ft`. Errors and
cancelled requests do not replace it. `ans = 5` seeds that answer, so `5 + ans`
then returns 10. `ans = ans + 5` evaluates against the previous answer and only
updates it on success. Named assignments such as `tax = 18%` go directly to
Qalculate; native temporary variables are process-local, not durable saved drafts.
The pane displays approximate decimal
results and retains the existing angle preference. Opening it enables the
Qalculate engine on Android 7+. Android 6 retains basic arithmetic support.

- `help` or the header question mark: open the terminal help browser.
- `clear`: clear the transcript, retaining `ans`.
- `exit`: close the pane and return to the launcher.
- Cancel stops only this pane's active calculation; late responses are ignored.

Help uses a separate `help>` session, not calculation history. The calculator
question mark opens the "Calculator" article, leading with entering expressions
and continuing results; reader navigation is explained at the end. Within an
article, 1 opens Topics, 2/3 move between articles when available, and 0 closes Help.
Choices are also tappable plain text.
Android Back closes Help immediately; it never walks through the previous levels.
The next bare `help` restores its category/article, page, scroll, draft and selection.
Explicit topic links still open the requested topic; reopening the bookmarked topic
also restores its reading position. Use numbered Topics/Categories
choices to navigate within Help (1 from an article; 10 from a category).
Entering help
preserves the calculation draft and selection; typed `help` is consumed as a
command. Help navigation never changes `ans` or calculation history.

The separate dock provides 123, ABC and FUNC layers with one ↵ key in every
layer. `=` on 123 and FUNC inserts an assignment operator; only ↵ evaluates.
ABC places Backspace immediately after M, with underscore, Space and ↵ below.
FUNC includes square root (`sqrt`), percentages, functions, constants and conversion words.
The smaller hint below the input previews Qalculate's interpretation and result
after a 300ms typing pause. For example, `cls` is shown as `centiliter*second →
10 mL*s`, not silently treated as a command. `+5` shows the previous answer in
the expanded expression before evaluation. `clear`, `help` and `exit` preview
their actions instead of invoking the engine.

Previews never append history, update the stored answer or execute assignments.
Assignments/comparisons, user functions, state-changing functions and expensive
expressions require Enter. Pure native preview work has a cooperative 200ms
budget and a 256-byte input cap; unsupported previews show an Enter-only hint.
The committed calculator still has its existing capabilities and watchdog.
Stale preview results are discarded rather than cancelling the variable-owning
native process. The two-line hint stays theme/font aware and is included in the
terminal's minimum size and landscape keypad measurement.

The input scrolls horizontally and supports cursor editing and selection.
Transcript, answer, draft and selection persist across reopening. Help page and
its own draft also survive activity recreation. Earlier calculator
history and mode drafts are retained in storage without destructive migration.
The pane uses the launcher theme and floating frame; controls retain the launcher
font while the transcript and prompt stay monospace. Calculator and help start
expanded unless a floating session was saved, and stay expanded in landscape.
Drag the title to move a window, or one of its four corner targets
to resize it. The header provides maximize/float and reset controls; floating
restores the retained rectangle, and reset returns to the default safe-host bounds.
Calculator and help keep independent window geometry across recreation.

Calculations use the existing isolated Qalculate service and watchdog. The native
bridge is independently authored; see `native/qalculate/README.md` and
`THIRD_PARTY_NOTICES.md` for native sources and licensing.

## Verification (2026-10-10)

The calculator/Help changes passed 33 focused JVM tests. Emulator coverage
included 33 existing regression checks and a final six-scenario run covering
native calculations, assignment insertion, answer retention after errors,
Help recreation, close/resume and the separate keypad dock. Two outdated
test expectations (Back navigating up and `=` evaluating) were corrected
and passed in that final run.

On the connected I2220 phone, all four focused instrumentation tests passed:
calculator session/assignments, native math/error/timeout recovery and two
Help bookmark/navigation scenarios. These tests preserve calculator, Help
and window preferences and do not clear account data.
The verified main APK was installed in place after matching signing
certificates; Build 426 and the original first-install timestamp were retained.

The same-day result-chaining follow-up passed 34 calculator/Help JVM tests,
17 emulator instrumentation scenarios and five focused physical-phone tests.
Coverage includes ASCII/Unicode operators, signed values, assignment RHS
isolation, retained units, error recovery, clearing history and reopening the
calculator. The Help button preserves the calculator draft/selection and a
fresh article opens at scroll zero. A phone screenshot was visually checked
after the activity transition: the title, introduction and continuation example
are visible at the beginning; existing Help bookmarks still resume normally.

The live-preview follow-up passed 35 calculator/Help JVM tests, two dedicated
preview emulator tests and 21 calculator/Help/window/native regression scenarios.
All seven focused I2220 phone tests passed, followed by a data-preserving live-hint
test against the installed main package. Screenshots from both phone packages
were visually checked. Coverage verifies interpreted units, chaining, native
functions/conversions, stale typing results, command hints, unchanged history and
answer until Enter, blocked save/assignment/loop previews and retained native
variables after those previews. All four native ABIs rebuilt; Playstore APK/test
assembly and Fdroid Kotlin compilation passed. The main Build 426 APK was updated
in place with matching signing certificates and its original first-install time.

The ABC Backspace-placement follow-up passed 21 focused JVM tests and six
emulator scenarios: staggered rows/editing, stable dock heights, initial
measurement, minimum floating size, live hints and native preview safety.
Both preview and committed evaluation return `87840 min` for `61d to min`
and `61 day to min`. A system-UI dialog blocked the first resize gesture;
the resize test and keypad screenshot passed after dismissing that dialog.
The normal Playstore debug APK built successfully. No physical phone was
connected for this follow-up, so that APK has not been installed on the phone.
