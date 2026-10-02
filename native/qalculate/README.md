# Re:TUI Qalculate JNI engine

This is an independently authored bridge to upstream libqalculate, licensed with
Re:TUI under GPL-3.0-or-later. It contains no Jost Herkenhoff wrapper, SWIG input,
generated bindings, prebuilt Maven binaries, or copied Android patch.

The first version deliberately keeps core **5.8.2** to isolate the binding change
from an engine upgrade. `sources.json` pins the upstream archives and SHA-256
checksums for libqalculate, GMP, MPFR and libxml2. These projects keep their own
licenses; see `THIRD_PARTY_NOTICES.md` and `LICENSES/` in the repository root.

## Build

Host prerequisites: macOS or Linux, Python 3.12+, a host C/C++ compiler, make,
patch, pkg-config, intltool (including its Perl XML::Parser dependency), and
Android NDK **27.1.12297006**. The release archives already contain configure
scripts; regenerating them with autoconf/automake is unnecessary.

On macOS, `brew install intltool pkgconf` supplies the extra host tools. On Debian
or Ubuntu, install `build-essential intltool pkg-config python3` (Python must be
3.12 or newer). Install the pinned NDK through Android Studio's SDK Manager.

Normal APK builds invoke `:app:buildNativeQalculate` automatically. To build one
architecture during native development:

```sh
python3 native/qalculate/build.py --ndk /path/to/android-sdk/ndk/27.1.12297006 --abis arm64-v8a
```

The full build includes arm64-v8a, armeabi-v7a, x86_64 and x86 at Android API 24.
Android 6 continues using the basic Kotlin calculator. Each archive is verified
before extraction. Sources, logs and compiled intermediates live under ignored
`app/build/qalculate/`. Deleting that directory produces a clean rebuild.
The build script invalidates native intermediates when its recipe, source pins
or patch change. JNI source changes relink against the cached native libraries.

GMP, MPFR and libxml2 are statically linked into `libretui_qalculate.so`; the
NDK's `libc++_shared.so` is packaged alongside it. Linker segments use 16 KiB
alignment. No device-time downloads are needed. Network/currency updates,
external command/gnuplot execution, ICU and iconv support are disabled. UTF-8
XML definitions are compiled into the library. This build uses English core
messages/definitions; launcher-owned messages still use Android localization.

## Boundary and cancellation

Kotlin passes bounded standard UTF-8 bytes and two booleans (degrees and exact).
JNI returns one status byte followed by UTF-8 text; it does not expose upstream
objects or pointers. C++ exceptions become Java exceptions; the service converts
failures into its existing engine-stopped result. Qalculate error messages are
returned as errors. All calls run on the service's single worker thread.

`calculateAndPrint` runs synchronously without an internal threaded timeout.
The existing four-second Android watchdog bounds loading, evaluation and
formatting; the client has its own eight-second bound. The watchdog terminates
only the private calculator service process, which can be started again.

Android lacks pthread cancellation. Our patch disables unsupported cancellation
setup and exits the private process if upstream unexpectedly requests cancellation
of a running thread. It never reports that a still-running thread was stopped.
Engine globals are owned by the process and are reclaimed at process exit.

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
to record both binary hashes against the same source tree.

The bundle includes app source, our bridge/build recipe/patch, the original native
archives, notices, and a manifest with source hashes and the APK hash. It excludes
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
