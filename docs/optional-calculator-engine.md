# Optional Qalculate engine pack

Re:TUI keeps basic calculation in the launcher. Advanced calculation is an optional
`.retui.calc.engine` download saved under `Re-T-UI/calculator-engines/` and automatically
installed into the launcher's private storage. No second
APK, Google account, Play service, shared signing key or publisher key is required.
The same WebAssembly pack runs in Play, F-Droid, GitHub and independently built launchers.

Download retains the checksum-verified original at
`<launcher folder>/calculator-engines/qalculate-<version>.retui.calc.engine`, then
automatically installs and probes the engine. The active JavaScript/WebAssembly stays
in separate app-private storage; changing the retained archive cannot change an active
runtime. The launcher folder is Re-T-UI in the channel's normal storage location.
Cancelled or corrupt downloads leave any earlier saved archive and working runtime intact.

When the engine is missing, Calc shows a compact optional-Qalculate card above its
transcript. Download and Import run from that card; preparation, progress and Cancel
stay inside the calculator. The basic calculator and draft remain available. The ×
control hides the card for the current calculator session; the status row or Help →
Calculator enhancement reopens it. This choice survives screen recreation. A successful
installation hides the card and changes the calculator status to Qalculate active.

Installed-engine management and Startup → Advanced use the shared management page.
Android Open/Share still supports MIME `application/vnd.retui.calculator-engine+zip`,
and opens the same shared installer in its own activity. The document picker accepts
ZIP/octet-stream providers without claiming all ZIPs globally. Installation requires
an explicit user action; calculator imports cannot restore backups or alter presets.
History, draft, answers, preferences and the retained archive stay when the installed
engine is removed. Import engine file can reinstall that archive without another download.
Personal backups retain calculator preferences; runtime downloads must be installed
again after a restore. The calculator-engines archive directory is excluded from personal
backups so optional runtime files do not inflate them.

## Build and host

Use the pinned Emscripten version documented in `native/qalculate/README.md`:

```
python3 native/qalculate/build_wasm.py --emsdk /path/to/emsdk
python3 native/qalculate/package_wasm.py
./gradlew :app:assemblePlaystoreRelease :app:assembleFdroidRelease
```

The engine uses the same checksum-pinned libqalculate 5.8.2, GMP, MPFR and libxml2
sources as the native reference bridge. It has an independently authored GPL-3.0-or-later
adapter. XML definitions are compiled into the binary; networking, gnuplot, insecure
functions and a writable runtime filesystem are disabled. The portable archive contains
exactly manifest.json, engine.js, engine.wasm, LICENSE.txt and NOTICE.txt. The manifest
records schema 1, kind qalculate, runtimeApi 1, version and SHA-256 hashes for every payload.
An internal hash is an integrity check, not proof of a local file's publisher; users must
trust the source of manually imported packs.

The packager prepares the pack, catalog.json and SHA256SUMS for the existing static site
under `calculator-packs/`. Publish those together as a separate release operation, then
build the launcher with:

```
-PcalculatorEngineCatalogUrl=https://re-tui.pages.dev/calculator-packs/catalog.json
```

The official catalogue is the default. Forks can supply their own HTTPS catalogue and
build their own pack without our credentials. Catalogue downloads must stay on the same
HTTPS host/directory, without redirects; the full archive hash is checked against the
catalogue before installation. Engine downloads are excluded from personal backups;
history and preferences remain included. Publish matching corresponding source with the binaries:

```
python3 scripts/qalculate_source_bundle.py --apk /path/launcher.apk \
  --engine-pack /path/qalculate-5.8.2.retui.calc.engine --output /path/retui-source.tar.gz
```

## Runtime and recovery

The APK owns a private WebView bootstrap and Web Worker adapter. Validated JavaScript and
WebAssembly are loaded through a fixed virtual HTTPS origin into a worker. Only calculator
requests/results cross a WebMessagePort: there is no Android JavaScript interface,
filesystem bridge, remote navigation or network capability. Execution has a four-second
watchdog and requests have an eight-second client deadline. Cancel/timeout destroys the
runtime; the next calculation creates a fresh worker, resetting temporary variables.
Devices lacking a suitable WebView retain basic calculations.

ZIP imports have strict names, entry counts, sizes, manifest compatibility, hash and WASM
header checks. Installation writes a private staging version, fsyncs it, and changes an
atomic active pointer only after validation. A retained previous pointer recovers an
interrupted or corrupt activation. Failed/cancelled updates preserve the prior working
engine; removal leaves calculator data intact.

This interpreted-code architecture is intended to fit Play's interpreter exception.
It is not evidence of Play review approval. Wallpaper, preset and full-backup handling
are unchanged.

## Focused verification

Build disposable phone artifacts with `-PcalculatorPackDebug=true` (package suffix
`.calculatorpackdebug`). Copy the actual pack into that package's private
`files/engine-fixture.retui.calc.engine`, then run `PortableCalculatorDeviceTest`.
The suite covers actual current-version math, previews/errors, cancellation/timeout
recovery, failed import/basic fallback/history and the Downloads-provider Open/import
journey. Store JVM tests exercise malformed archives, hashes, cancellation, update
rollback and broken active-pointer recovery. Binary math assertions are provided with
the WASM build. Results from the earlier companion experiment do not validate this pack.
