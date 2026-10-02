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

The engine is upstream libqalculate **5.8.2**, built through Re:TUI's independently
authored JNI bridge. The source archives and checksums are pinned in
`native/qalculate/sources.json`; no Jost Herkenhoff Maven wrapper or SWIG bindings
are packaged. All four Android ABIs and compiled English definitions are included.
No runtime download or currency-rate update is performed.

Native evaluation runs synchronously in a private service process. An eight-second
client bound and four-second service watchdog keep a stuck native calculation from
blocking the launcher. Android does not support pthread cancellation: our small
upstream patch never pretends an active thread was cancelled, and unexpected
native cancellation terminates only the disposable service process. Normal timeouts
return the existing timeout message before the watchdog kills that process.

Results are capped at 16,384 Java characters and input at 1,024 characters.
One native request is accepted at a time; concurrent requests get a busy message.
Basic live previews use the existing Kotlin parser. The native build disables
external command/gnuplot execution and network currency updates.

`CalculatorIntegrationTest` covers both command modes, the Behaviour setting,
conversion, separate tasks, launcher reload, expression restoration, UTF-8 input,
angle/exact modes, native errors and timeout recovery. Run it with the existing
AndroidJUnitRunner. See [native build instructions](../native/qalculate/README.md).

## Licensing and public releases

Project-owned code and the new bridge are GPL-3.0-or-later. The prior MIT notice is
retained. Core libqalculate grants GPL-2.0-or-later in its source headers, allowing
the GPLv3 option. GMP, MPFR, libxml2 and the NDK C++ runtime retain their respective
notices; see [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).

The former Android wrapper's ambiguous GPLv2 declaration is no longer in the
shipped dependency chain. Its permission request may remain open for other users;
our independent implementation does not rely on a reply or relicense its code.

Before public distribution, build and verify the final APK, then create and publish
the matching full app/native source bundle using `scripts/qalculate_source_bundle.py`.
The archive includes exact upstream sources, our patch, JNI code, build recipe,
notices and a source/APK hash manifest. Provide a durable source link to recipients
of both paid and free binaries. Signing credentials are excluded.

This resolves the old wrapper dependency; it does not claim a whole-project
licensing audit, clear unrelated artwork terms, or authorize a public release.
