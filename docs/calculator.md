# Calculator

`calc` opens a floating, text-based calculator pane. Enter an expression after
`calc>` and press Enter or the submit button. Results appear in the scrollable,
selectable transcript. `calc <expression>` still prints a one-shot result in the
launcher terminal.

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

- `help`: show usage.
- `clear`: clear the transcript, retaining `ans`.
- `exit`: close the pane and return to the launcher.
- Cancel stops only this pane's active calculation; late responses are ignored.

The input scrolls horizontally and supports cursor editing and selection.
Transcript, answer and draft persist across reopening. Earlier calculator
history and mode drafts are retained in storage without destructive migration.
The pane uses the launcher font, theme and floating frame.

Calculations use the existing isolated Qalculate service and watchdog. The native
bridge is independently authored; see `native/qalculate/README.md` and
`THIRD_PARTY_NOTICES.md` for native sources and licensing.
