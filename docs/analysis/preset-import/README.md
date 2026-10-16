# Preset import repair

Implemented in dvil/termux-ssh-analysis. No commit or publication.

- Load launcher settings before styling an externally opened importer; use the launcher font, header, panel, and density-aware spacing. Respect system bars and display cutouts in preview, error, and saved states.
- Accept ZIP/binary or absent MIME labels for explicitly delivered intents, and a single ClipData URI. Implicit discovery remains restricted to the Re:TUI preset MIME. Archive, content-provider, private-provider, and personal-backup validation remain intact.
- A new file delivered to an existing importer replaces the previous file/error. Superseded inspection results cannot enable Import; incoming files wait for an active save to finish.
- Error screens offer Choose preset file for recovery. Import continues to save only; it never applies.

## Evidence

49 focused unit tests passed (31 BackupManager, 18 PresetManager). Five focused Android 16 tests passed on the physical Vivo phone: downloaded palettes with custom/ZIP/binary/absent MIME and ClipData, import without applying, error recovery/font/insets, invalid URI rejection, personal-backup rejection, and reopening an existing importer. Release and test builds succeeded without bypassing runtime notices. Localization catalog check and diff checks passed.

The actual downloaded TokyoNight Storm and Dracula files on the phone match the tested fixtures byte-for-byte. The pre-fix TokyoNight download imported successfully through the Settings document picker, saving TokyoNight Storm without applying it. Therefore the original reported failure was not independently reproduced. The MIME and stale-intent defects are verified fixes, not proof of the original error's exact cause.

The signed normal release was installed as an in-place update and its bytes read back and matched. Preview/error screenshots were inspected on that release; error.png deliberately uses a nonexistent provider to exercise recovery, not the original user's failure. before.png records the old saved screen overlapping status icons.

Not run: a fresh Chrome download-to-Android-chooser journey, the full palette matrix, or the separate historical Android 16 share-grant revocation regression. No preset was applied and the normal app was never reset/uninstalled.
