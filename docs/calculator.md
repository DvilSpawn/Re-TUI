# Calculator

`calc` opens the original compact framed calculator in a separate activity/task,
like Settings. `calc 2 * (3 + 4)` evaluates inline.
Bare launcher input is not intercepted. Expressions and the result survive activity
recreation; pausing saves them locally. Launcher reload leaves the calculator task open.

Qalculate is off by default. The `qalculate` switch in Settings → `behavior.xml`
(also `config -set qalculate true`) reveals advanced controls and enables the
same engine for `calc` expressions, including `calc 5 km to miles`. Degrees and
exact-result preferences apply to both entry points. Android 6 retains the basic
calculator; native evaluation requires Android 7 or newer.

The pinned Maven artifact is `com.jherkenhoff:libqalculate:5.8.2-2`. Its AAR contains
compiled definitions and four native ABIs, so no runtime download is needed. No
currency-rate update is performed. The wrapper's core version is 5.8.2; updating
the wrapper and its bundled native dependencies is our responsibility.

Native evaluation runs in a private service process. An eight-second client bound
and four-second process watchdog keep a stuck native calculation from blocking
the launcher. This is necessary because the Android port's cancellation patch
does not provide reliable pthread cancellation. Results are capped at 16 KiB and
input at 1,024 characters. One native request is accepted at a time; concurrent
requests get a busy message. Basic live previews use the existing Kotlin parser.

`CalculatorIntegrationTest` covers both command modes, the Behaviour setting, conversion,
separate tasks, launcher reload and expression restoration. Run on a device with
the existing AndroidJUnitRunner. See THIRD_PARTY_NOTICES.md before public distribution.

## Licensing and public releases

Project-owned code is GPL-3.0-or-later. The prior MIT notice is retained, and
third-party source headers and artwork terms continue to apply. The libqalculate
5.8.2 core grants GPL-2.0-or-later in its source headers, allowing GPLv3 use.

The published Android wrapper declares `GPL-2.0` and includes GPLv2 COPYING,
without a verified explicit later-version grant. The sample notice in the GPL
license appendix is not a grant by the wrapper author. Apache-2.0 dependencies
in the Launcher are compatible with GPLv3 but not GPLv2-only. Before publishing
a Qalculate-enabled binary, obtain compatible permission for the wrapper or
replace it with a compatible Android binding/build. The separate service process
and off-by-default toggle do not remove this issue: the native libraries are
still shipped in the APK.

Sources reviewed:
- [Core license grant](https://github.com/Qalculate/libqalculate/blob/v5.8.2/libqalculate/Calculator.h)
- [Wrapper metadata](https://repo.maven.apache.org/maven2/com/jherkenhoff/libqalculate/5.8.2-2/libqalculate-5.8.2-2.pom)
- [Wrapper COPYING](https://github.com/jherkenhoff/libqalculate-android/blob/75b95a97b2adcd7254512f5e1c575c776ec3be64/COPYING)
- [Apache compatibility guidance](https://www.apache.org/licenses/GPL-compatibility.html)

A public binary release also needs the matching complete corresponding source,
Android binding and patches, native dependency sources and build scripts, plus
applicable license/attribution materials. The Maven sources JAR contains generated
Java bindings and is not a complete native source bundle. The reviewed wrapper
checkout is `75b95a97b2adcd7254512f5e1c575c776ec3be64`; its relationship to the
published AAR must be verified before calling it the exact corresponding source.
Keep this integration local until those requirements and the existing artwork
terms have been checked for the intended distribution. A repository license
change alone is not a completed distribution audit.
