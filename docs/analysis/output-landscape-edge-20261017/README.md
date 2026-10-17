# Landscape output edge

The output container now removes its fixed 8dp top padding in responsive landscape. Its OUTPUT tab uses a zero top offset there so it remains inside the available edge; text clearance is recalculated beneath the tab. Portrait restores the original 8dp padding and raised tab.

Configured output-field margins and display-section margins remain in force. The landscape output section also honors the system top inset; the existing swapped-Duo header reservation remains intact. No saved preference values are rewritten.

The XML's 10dp output-border margin is replaced at runtime by `output_field_margins` during theme styling; this change preserves that resolved margin rather than clearing it.

Validation: app/test APK builds passed; 458 unit cases passed with five opt-in network cases skipped. The Android 15/API 35 emulator test rotated portrait → landscape → portrait, verified a retained 13px output-field margin, checked 2mm section margins combined with a simulated 27px system inset, checked label/text clearance, and confirmed portrait restoration. See `emulator-api35.txt` (one test passed).

The phone was not connected during this change. No everyday-phone install, commit, or release was performed.
