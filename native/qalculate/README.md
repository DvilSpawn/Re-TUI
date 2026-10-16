# Re:TUI Qalculate engine

This is an independently authored bridge to upstream libqalculate, licensed with
Re:TUI under GPL-3.0-or-later. It contains no Jost Herkenhoff wrapper, SWIG input,
generated bindings, prebuilt Maven binaries, or copied Android patch.

The first version deliberately keeps core **5.8.2** to isolate the binding change
from an engine upgrade. `sources.json` pins the upstream archives and SHA-256
checksums for libqalculate, GMP, MPFR and libxml2. These projects keep their own
licenses; see `THIRD_PARTY_NOTICES.md` and `LICENSES/` in the repository root.

## Portable optional pack (WebAssembly)

The launcher uses one downloadable `.retui.calc.engine` pack across Play, GitHub,
F-Droid and self-built distributions. The pack is imported into launcher-private
storage; it is not an Android application and needs no publisher signing key.
Its independently authored `wasm_bridge.cpp` preserves the JNI adapter's math and
preview semantics. The launcher hosts the engine in a disposable Web Worker. Filesystem support is
disabled; the adapter explicitly returns ENOSYS for the unused upstream async
thread IPC pipes. The worker performs synchronous calculations and is terminated
by the host for cancellation/timeouts.

Host prerequisites: Python 3.12+, make, pkg-config, a host C/C++ compiler, Node.js
and Emscripten **3.1.67**. Install the SDK in an ordinary writable directory:

```sh
git clone https://github.com/emscripten-core/emsdk.git "$HOME/.cache/retui-emsdk"
"$HOME/.cache/retui-emsdk/emsdk" install 3.1.67
"$HOME/.cache/retui-emsdk/emsdk" activate 3.1.67
. "$HOME/.cache/retui-emsdk/emsdk_env.sh"
python3 native/qalculate/build_wasm.py --emsdk "$HOME/.cache/retui-emsdk"
node native/qalculate/test_wasm.cjs
python3 native/qalculate/package_wasm.py
```

The build verifies every source archive using `sources.json`. Build intermediates
and complete logs are under ignored `app/build/qalculate/wasm`. The output is
`dist/engine.js` plus `dist/engine.wasm`; the packager writes the installable pack,
`catalog.json` and `SHA256SUMS` to `site/calculator-packs` for later publication.
Use `--base-url` with the packager to host a fork's catalogue without an owner key.
Publishing these generated files is a separate step from building the launcher.

The ZIP contains exactly `manifest.json`, `engine.js`, `engine.wasm`, `LICENSE.txt`
and `NOTICE.txt`. The schema-1 manifest declares `kind: "qalculate"`, `runtimeApi: 1`,
version `5.8.2`, and a `files` map of SHA-256 digests for all four payload files.
Catalogue downloads additionally verify the SHA-256 of the entire ZIP. Manual
imports are user-selected code, not publisher-authenticated merely by internal hashes.

GMP, MPFR and libxml2 are linked into the WASM engine. Network/currency updates,
external command/gnuplot execution, ICU and iconv support are disabled. Definitions
are compiled into the engine. Core messages/definitions use English.

## Native reference build

`build.py` and `bridge.cpp` remain for native parity/reference testing, not launcher
packaging. They require Android NDK **27.1.12297006** and build all four ABIs:

```sh
python3 native/qalculate/build.py --ndk /path/to/android-sdk/ndk/27.1.12297006 --abis arm64-v8a
```

## Historical native reference boundary

The following describes the retained JNI reference adapter. Launcher delivery uses
the portable worker and cancellation described in `docs/optional-calculator-engine.md`.

Kotlin passes bounded standard UTF-8 bytes and booleans (degrees, exact and preview).
JNI returns one status byte (error and approximation flags) followed by UTF-8 text; it does not expose upstream
objects or pointers. C++ exceptions become Java exceptions; the service converts
failures into its existing engine-stopped result. Qalculate error messages are
returned as errors. All calls run on the service's single worker thread.

Preview packets append a NUL-delimited parsed-expression string; status 3 means
the preview is deferred until Enter. Their reusable answer is always null.
Preview input is limited to 256 UTF-8 bytes. Assignments/quoted expressions are
deferred before evaluation; an AST whitelist admits only pure built-in math and
already-evaluated variable values, excluding user functions, deferred variable
expressions and save/load/export/random/loop functions. Preview powers must have
a literal exponent within ±1000. Native parsing/evaluation uses a cooperative
200ms control budget, not threaded cancellation. Ordinary evaluation keeps the
original result format, function support and watchdog. Android messages are
snapshotted before worker dispatch so recycled messages cannot lose preview mode.

Committed `calculateAndPrint` calls run synchronously without an internal threaded timeout.
The existing four-second Android watchdog bounds loading, evaluation and
formatting; the client has its own eight-second bound. The watchdog terminates
only the private calculator service process, which can be started again.

Android lacks pthread cancellation. Our patch disables unsupported cancellation
setup and exits the private process if upstream unexpectedly requests cancellation
of a running thread. It never reports that a still-running thread was stopped.
Engine globals are owned by the process and are reclaimed at process exit. Idle definitions can remain cached between bindings; normal unbinding does not kill a new caller racing to bind. Cancellation identifies the active request before terminating the isolated process. Exact output preserves fractions, and reusable output uses ASCII signs without digit grouping.

## Source delivery and upgrades

After building and checking the final APK, capture the exact working tree and
native inputs without changing any sources between build and packaging:

```sh
python3 scripts/qalculate_source_bundle.py \
  --apk app/build/outputs/apk/playstore/debug/app-playstore-debug.apk \
  --output app/build/outputs/qalculate/retui-source.tar.gz
```

For a Play Store release, use the release APK and also pass
`--aab app/build/outputs/bundle/playstoreRelease/app-playstore-release.aab`
to record both binary hashes against the same source tree. For the optional engine,
also pass `--engine-pack app/build/qalculate/wasm/site/calculator-packs/qalculate-5.8.2.retui.calc.engine`.
The resulting manifest ties the launcher and portable engine to the same source snapshot.

The bundle includes app source, our bridge/build recipe/patch, the original native
archives, matching Tasker Plugin Library source/build archives, notices, and a
manifest with source hashes and the APK hash. See `third_party/README.md` for its
source-match verification and metadata-only build patch. It excludes
ignored signing keys, local settings and build products. Bundled upstream archives
are reused by the build script, so native source downloads are unnecessary when
rebuilding the bundle. Android SDK/NDK and Gradle dependencies remain build
prerequisites. The manifest is provenance, not a claim of byte-identical APKs
across machines or signing keys.

Before public distribution, publish the matching source bundle with a durable
source link for recipients of both the free and paid binaries, retain all notices,
and verify the final release artifact. Do not substitute a link to an unrelated
upstream branch or a Java-only sources JAR.

For an upgrade, change the pinned archive/checksum, review dependency licenses and
the small Android patch, rebuild all four ABIs, then run `CalculatorIntegrationTest`
on emulator and device (including timeout/recovery). Update the source bundle and
notices with the binaries. This removes the old wrapper ambiguity; it is not a
whole-project distribution/legal audit.
