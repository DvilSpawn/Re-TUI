package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.os.Build
import android.os.Looper
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.UUID

object CalculatorEngine {
    const val MAX_INPUT = 1024
    private const val LAST_IME_BOTTOM_OFFSET = "last_ime_bottom_offset"
    fun prefs(context: Context) = context.getSharedPreferences("calculator", Context.MODE_PRIVATE)
    fun rememberImeBottomOffset(context: Context, offset: Int) {
        if (offset > 0) prefs(context).edit().putInt(LAST_IME_BOTTOM_OFFSET, offset).apply()
    }
    fun lastImeBottomOffset(context: Context): Int = prefs(context).getInt(LAST_IME_BOTTOM_OFFSET, 0)
    fun supported() = Build.VERSION.SDK_INT >= 24
    fun enabled(context: Context) = supported() && LauncherSettings.getBoolean(Behavior.qalculate) && WasmCalculatorRuntime.available(context)

    data class Result(val text: String, val error: Boolean = false,
        val approximate: Boolean = false, val reusable: String? = if (error) null else text,
        val failure: String? = null, val parsed: String? = null)
    data class Options(val degrees: Boolean, val exact: Boolean)
    fun options(context: Context) = Options(prefs(context).getBoolean("degrees", true), prefs(context).getBoolean("exact", true))
    class Request {
        val id: String = UUID.randomUUID().toString()
        @Volatile var cancelled = false; private set
        private var onCancel: (() -> Unit)? = null
        @Synchronized fun cancel() {
            cancelled = true
            onCancel?.invoke()
            onCancel = null
        }
        @Synchronized internal fun attachCancellation(action: () -> Unit): Boolean {
            if (cancelled) return false
            onCancel = action
            return true
        }
        @Synchronized fun finished() { onCancel = null }
    }

    // Call on a worker thread. The same path serves the activity and `calc <expression>`.
    fun evaluate(context: Context, expression: String, options: Options = options(context), request: Request = Request(), preview: Boolean = false): Result {
        val input = expression.trim()
        if (input.isEmpty()) return Result("")
        if (input.indexOf('\u0000') >= 0) return Result(context.getString(R.string.calculator_invalid), true, failure = "invalid")
        if (input.length > MAX_INPUT) return Result(context.getString(R.string.calculator_too_long, MAX_INPUT), true)
        if (request.cancelled) return Result(context.getString(R.string.calculator_cancelled), true, failure = "cancelled")
        if (!enabled(context)) return if (preview) basicPreview(context, input, request) else basic(context, input)
        check(Looper.myLooper() != Looper.getMainLooper())
        val result = WasmCalculatorRuntime.evaluate(context.applicationContext, input, options, request, preview)
        // Devices without a working WebAssembly provider retain the built-in calculator.
        return if (result.failure == "unavailable") {
            if (preview) basicPreview(context, input, request) else basic(context, input)
        } else result
    }

    private fun basicPreview(context: Context, input: String, request: Request): Result {
        val normalized = input.replace('×', '*').replace('÷', '/').replace('−', '-')
        // The fallback has no variables or unit engine. Never interpret stateful or advanced input.
        if (input.length > 256 || normalized.any { it !in '0'..'9' && !it.isWhitespace() && it !in ".()+-*/^" }) {
            return Result("", true, failure = "preview_unavailable")
        }
        var depth = 0
        for (character in normalized) {
            if (character == '(') depth++
            if (character == ')' && --depth < 0) return Result("", true, failure = "syntax")
        }
        if (depth != 0) return Result("", true, failure = "syntax")
        val result = basic(context, normalized)
        if (request.cancelled) return Result(context.getString(R.string.calculator_cancelled), true, failure = "cancelled")
        return if (result.error) result
        else result.copy(parsed = input, reusable = null)
    }

    private fun basic(context: Context, input: String): Result = try {
        val value = Tuils.eval(input.replace('×', '*').replace('÷', '/').replace('−', '-'))
        if (!value.isFinite()) Result(context.getString(R.string.calculator_invalid), true)
        else Result(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
    } catch (_: Exception) { Result(context.getString(R.string.calculator_invalid), true) }
}
