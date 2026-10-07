# Calculator

`calc` opens a terminal with an attached keypad dock. Enter an expression after
`calc>` and press Enter or ↵. The live prompt, expressions and results share one
scrollable monospace terminal; there is no input frame or separate result display.
`calc <expression>` still prints a one-shot result in the launcher terminal.

```
calc> 5 ft to miles
≈ 0.000946969696969697 mi
calc> ans * 2
≈ 0.001893939393939394 mi
calc> ans to ft
10 ft
```

Use `to` for conversions: `in` means inches, not a conversion operator.
`ans` inserts the last successful result with its units and grouping. Errors and
cancelled requests do not replace it. The pane displays approximate decimal
results and retains the existing angle preference. Opening it enables the
Qalculate engine on Android 7+. Android 6 retains basic arithmetic support.

- `help` or the header question mark: open the terminal help browser.
- `clear`: clear the transcript, retaining `ans`.
- `exit`: close the pane and return to the launcher.
- Cancel stops only this pane's active calculation; late responses are ignored.

Help uses a separate `help>` session, not calculation history. At its menu,
choose 1–5 for expressions/operators, functions/constants, conversions, `ans`,
or keypad/editing; 0 returns to the calculator. Within a topic, 1 opens the menu,
2 goes to the previous topic, 3 goes to the next, and 0 returns to the calculator.
Unavailable directions are omitted. Choices are also tappable plain text.
Back returns from topic to menu to calculator without closing it. Entering help
preserves the calculation draft and selection; typed `help` is consumed as a
command. Help navigation never changes `ans` or calculation history.

The separate dock provides 123, ABC and FUNC layers with one ↵ key in every
layer. FUNC includes percentages, functions, constants and conversion words.
The input scrolls horizontally and supports cursor editing and selection.
Transcript, answer, draft and selection persist across reopening. Help page and
its own draft also survive activity recreation. Earlier calculator
history and mode drafts are retained in storage without destructive migration.
The pane uses the launcher theme and floating frame; controls retain the launcher
font while the transcript and prompt stay monospace.

Calculations use the existing isolated Qalculate service and watchdog. The native
bridge is independently authored; see `native/qalculate/README.md` and
`THIRD_PARTY_NOTICES.md` for native sources and licensing.
