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

object CalculatorEngine {
    const val MAX_INPUT = 1024
    fun prefs(context: Context) = context.getSharedPreferences("calculator", Context.MODE_PRIVATE)
    fun supported() = Build.VERSION.SDK_INT >= 24
    fun enabled(context: Context) = supported() && LauncherSettings.getBoolean(Behavior.qalculate)

    data class Result(val text: String, val error: Boolean = false)

    // Call on a worker thread. The same path serves the activity and `calc <expression>`.
    fun evaluate(context: Context, expression: String): Result {
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
            result = Result(message.data.getString("text").orEmpty(), message.data.getBoolean("error"))
            completed.countDown()
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    Messenger(binder).send(Message.obtain(null, 1).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putString("expression", input)
                            putBoolean("degrees", prefs(app).getBoolean("degrees", true))
                            putBoolean("exact", prefs(app).getBoolean("exact", true))
                        }
                    })
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
                result = Result(context.getString(R.string.calculator_timeout), true)
            }
        } catch (_: Exception) {
            return Result(context.getString(R.string.calculator_engine_stopped), true)
        } finally {
            if (bound) app.unbindService(connection)
        }
        return result
    }
}
