# Re:TUI Date/Time Picker

Reusable Android view for Re:TUI apps. It owns date/time selection and prevents dates before the supplied minimum; the host app supplies its own fonts, colors, borders, and dialog shell through `RetuiDateTimePickerView.Theme`.

Add the module as a Gradle dependency, construct `RetuiDateTimePickerView(context, initialMillis, minimumMillis, theme)`, and read `selectedTimeMillis()` when the host dialog is confirmed.
