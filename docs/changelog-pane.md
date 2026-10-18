# Update changelog pane

`changelog` opens bundled release highlights offline, using Help's shared pane,
raised header, theme/font/border settings, floating controls and bottom-corner
clearance. Geometry is stored independently under `changelog_*`. Back and Continue
close the pane; no keyboard or network request is needed. Scroll survives rotation.

Launcher automatically opens it once per newer `versionCode`, after first-run
setup. Fresh installs and existing users without an acknowledgement also see it
once. Setup is not bypassed and does not mark the changelog seen. Package install
timestamps do not suppress the notice. Reinstalling the same version in place after acknowledgement and
downgrades do not create new notices. The existing `changelogPrefs` backup entry
also covers the new `last_seen_version_code` field.

Before each release, update `changelog_release_highlights` in
`app/src/main/res/values/strings_changelog.xml` to match the actual shipped features;
the displayed version/build come from the installed APK. These are concise highlights,
not the technical/audit ledger or a claim that default-network signup is open.
Release highlights read the bundled APK text rather than an installed language-pack override, so older packs cannot replace the current notes. Pane labels and controls still use the selected language. Refresh localization exports during release preparation. No version bump, release
publication or device install is part of adding this pane.

## Physical-phone verification

The standalone pane fixture covers acknowledgement, Back/Continue, scroll
restoration and real portrait/landscape rotation, restoring its preference state.
The platform-only release smoke also exercises Launcher-driven automatic opening
against the installed optimized APK, without app-class references or changed
production keep rules:

```
./gradlew :app:assemblePlaystoreDebugAndroidTest -PircReleaseSmokeRunner=true
```

After verifying the test APK's runner and matching signer and installing only
that test APK, run its explicit opt-in:

```
adb -s DEVICE shell am instrument -w -e changelog_auto_smoke true com.dvil.tui_renewed.test/ohi.andre.consolelauncher.irc.IrcReleaseSmokeInstrumentation
```

This requires completed setup, temporarily seeds an older last-seen build and
launches Home normally. It verifies one automatic notice, installed build text,
acknowledgement and no repeat on return/fresh Home launch, then restores and
compares changelog/window preferences. It captures a foreground screenshot for
visual inspection. It does not call IRC, send messages, replace the application
APK or simulate an actual package-version upgrade. Copy/verify the test artifact
before installing if another build is running in the shared checkout.

Add `-e changelog_first_install true` to that smoke command to exercise an unseen
installation after setup: only the changelog acknowledgement is temporarily
removed, not Launcher data or setup progress. The fixture restores it afterward.
