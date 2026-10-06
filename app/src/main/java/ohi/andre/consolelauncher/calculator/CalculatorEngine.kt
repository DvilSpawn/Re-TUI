package ohi.andre.consolelauncher.calculator

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun enabled(context: Context) = supported() && LauncherSettings.getBoolean(Behavior.qalculate)

    data class Result(val text: String, val error: Boolean = false,
        val approximate: Boolean = false, val reusable: String? = if (error) null else text,
        val failure: String? = null)
    data class Options(val degrees: Boolean, val exact: Boolean)
    fun options(context: Context) = Options(prefs(context).getBoolean("degrees", true), prefs(context).getBoolean("exact", true))
    class Request {
        val id: String = UUID.randomUUID().toString()
        @Volatile var cancelled = false; private set
        private var remote: Messenger? = null
        @Synchronized fun cancel() {
            cancelled = true
            try { remote?.send(Message.obtain(null, 2).apply { data = Bundle().apply { putString("id", id) } }) }
            catch (_: Exception) { }
        }
        @Synchronized fun submit(service: Messenger, message: Message): Boolean {
            if (cancelled) return false
            remote = service
            service.send(message)
            return true
        }
        @Synchronized fun finished() { remote = null }
    }

    // Call on a worker thread. The same path serves the activity and `calc <expression>`.
    fun evaluate(context: Context, expression: String, options: Options = options(context), request: Request = Request()): Result {
        val input = expression.trim()
        if (input.isEmpty()) return Result("")
        if (input.length > MAX_INPUT) return Result(context.getString(R.string.calculator_too_long, MAX_INPUT), true)
        if (!enabled(context)) return try {
            val value = Tuils.eval(input)
            if (!value.isFinite()) Result(context.getString(R.string.calculator_invalid), true)
            else Result(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString())
        } catch (_: Exception) { Result(context.getString(R.string.calculator_invalid), true) }
        check(Looper.myLooper() != Looper.getMainLooper())
        val app = context.applicationContext
        val completed = CountDownLatch(1)
        var result = Result(context.getString(R.string.calculator_engine_stopped), true)
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            result = Result(message.data.getString("text").orEmpty(), message.data.getBoolean("error"),
                message.data.getBoolean("approximate"), message.data.getString("reusable"), message.data.getString("failure"))
            completed.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    if (request.cancelled) { completed.countDown(); return }
                    val submitted = request.submit(Messenger(binder), Message.obtain(null, 1).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString("id", request.id)
                            putString("expression", input)
                            putBoolean("degrees", options.degrees)
                            putBoolean("exact", options.exact)
                        }
                    })
                    if (!submitted) completed.countDown()
                } catch (_: Exception) { completed.countDown() }
            }
            override fun onServiceDisconnected(name: ComponentName) { completed.countDown() }
            override fun onNullBinding(name: ComponentName) { completed.countDown() }
            override fun onBindingDied(name: ComponentName) { completed.countDown() }
        }
        var bound = false
        try {
            bound = app.bindService(Intent(app, QalculateService::class.java), connection, Context.BIND_AUTO_CREATE)
            if (bound && !completed.await(8, TimeUnit.SECONDS)) {
                request.cancel()
                result = Result(context.getString(R.string.calculator_timeout), true, failure = "timeout")
            }
        } catch (_: Exception) {
            return Result(context.getString(R.string.calculator_engine_stopped), true)
        } finally {
            request.finished()
            if (bound) app.unbindService(connection)
        }
        return if (request.cancelled && result.failure != "timeout") Result(context.getString(R.string.calculator_cancelled), true, failure = "cancelled") else result
    }
}
