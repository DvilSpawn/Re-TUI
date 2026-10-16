# SSH is the standard Termux pane

SSH is now the default interactive transport in normal debug and release builds. Its release shrinking rules apply to ordinary releases too. The internal release-check switch changes only the package/signing/test runner; it no longer enables a different transport. Legacy transport overrides are explicit diagnostic builds, isolated from the installed production launcher.

The native Termux integration remains responsible for setup, modules, suggestions and structured command results. Existing Re:TUI split panes, pages, saved launchers, input controls and theme stay in place. Termux/OpenSSH/tmux own the terminal connection and persistent shells; there is no on-device transport selector.

## First use and upgrades

An existing legacy installation needs one local **Enable Termux pane** step to pair. Merely opening the pane does not install packages. Enable can install missing tmux/OpenSSH support through the already established Android Termux permission boundary. Paired installations connect directly on subsequent opens. Repair remains available when pairing is damaged or the host key changes.

Selection, pages, split focus, drafts and saved launchers use the same preferences. SSH attaches the same `retui_workspace` window IDs; setup, listing and attachment do not create replacement shells. The physical in-place upgrade check passed: a modern legacy-bridge build seeded three live shells, a left-right split focused on tile two, a second page and a saved launcher. The SSH build was installed over that exact same test package with `install -r`, retaining app UID 11160 and unchanged cache proof. After the guided Enable action, selections and the launcher were identical; live shell PIDs 21405, 21424 and 21443 stayed unchanged; both panes accepted output, SSH input worked and page navigation restored the correct terminals. No replacement or duplicate shell appeared. Fixtures were removed and prior preferences restored.

[Upgrade proof](evidence/standard/upgrade/termux-upgrade-result.json), [same-package artifact/UID proof](evidence/standard/upgrade/ssh-artifacts.json), [seed log](evidence/standard/upgrade/seed.log), [verify log](evidence/standard/upgrade/verify.log), [restored split screenshot](evidence/standard/upgrade/termux-upgrade-restored.png). The legacy phase uses the modern 0.4.4 bridge path compiled from this working source. This is a real update of an isolated test package, not an update of the user’s installed production APK or every historical Play build.

## Verification

- Normal default configuration: **433 unit tests passed, zero failures, five opt-in live IRC skips**; release lint has **zero errors**, with existing warnings retained. Ordinary APK and Play AAB builds pass. [Build log](evidence/standard/full-gates.log), [unit counts](evidence/standard/unit-results.json), [lint](evidence/standard/release-lint.txt).
- The ordinary release uses package `com.dvil.tui_renewed`, has compiled SSH=true, and includes JSch/SSH factory code under the required shrinking rules. Its actual DEX getter returns true and both APK/AAB contain all 16 required SSH entry points/key-exchange implementations, with core package-private reflection preserved. Its unsigned APK/AAB are archived separately from the internal test package. [DEX and factory verification](evidence/standard/ordinary-ssh-dex-verification.json). [Hashes and paths](evidence/standard/ordinary-release-artifacts.json), [configuration hashes](evidence/standard/source-defaults.json).

The fresh minified standard-mode internal package passed **four physical SSH checks**, including two streaming PTYs and shell preservation. It inherits the same true default and release rules as the ordinary build; the internal switch does not override transport enablement. Installed APK bytes were pulled back and verified. [Artifact and source provenance](evidence/standard/minified-standard-artifacts.json), [physical log](evidence/standard/minified-standard-wire.log).

For reproduction, build/install the isolated debug app and tests with `-PtermuxSshPane=false`, run `TermuxWorkspaceProcessDeathTest#bridgeWorkspaceSurvivesInPlaceSshUpgrade` with `-e termuxUpgradePhase seed`, then build/install with `-PtermuxSshPane=true` using `install -r` and run the same method with `-e termuxUpgradePhase verify`. Do not uninstall, clear data, or use the production package for this fixture.

After verification, native debug pairing was restored and only the temporary release-check packages were removed. [Cleanup proof](evidence/standard/cleanup.json).

No production package was replaced, and no commit, push or public release was made. Package/signing continuity for an actual published launcher update remains part of the release procedure. Physical checks use a separate package with the same retained data across the simulated app update.
