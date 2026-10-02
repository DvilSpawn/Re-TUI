package ohi.andre.consolelauncher.calculator

import android.app.Service
import android.content.Intent
import android.os.*
import android.system.Os
import ohi.andre.consolelauncher.R
import java.io.File
import java.util.concurrent.Executors

/** Native failures and unbounded calculations cannot take down the launcher. */
class QalculateService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var busy = false
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        if (request.what == 1 && request.replyTo != null) calculate(request)
        true
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun reply(target: Messenger, text: String, error: Boolean) {
        try {
            target.send(Message.obtain(null, 1).apply {
                data = Bundle().apply { putString("text", text.take(16384)); putBoolean("error", error) }
            })
        } catch (_: RemoteException) { /* The requesting activity or launcher has gone away. */ }
    }

    private fun calculate(request: Message) {
        val target = request.replyTo
        val input = request.data.getString("expression").orEmpty()
        if (busy) { reply(target, getString(R.string.calculator_busy), true); return }
        if (input.isBlank() || input.length > CalculatorEngine.MAX_INPUT || !CalculatorEngine.supported()) {
            reply(target, getString(R.string.calculator_invalid), true); return
        }
        busy = true
        val degrees = request.data.getBoolean("degrees")
        val exact = request.data.getBoolean("exact")
        // Android has no pthread cancellation. The deadline kills only this service process.
        val watchdog = Runnable {
            reply(target, getString(R.string.calculator_timeout), true)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        main.postDelayed(watchdog, 4000)
        worker.execute {
            val result = try {
                val privateHome = File(filesDir, "qalculate").apply { mkdirs() }
                Os.setenv("HOME", privateHome.absolutePath, true)
                NativeQalculate.calculate(input, degrees, exact)
            } catch (_: Exception) {
                CalculatorEngine.Result(getString(R.string.calculator_engine_stopped), true)
            } catch (_: LinkageError) {
                CalculatorEngine.Result(getString(R.string.calculator_engine_stopped), true)
            }
            main.post {
                main.removeCallbacks(watchdog)
                busy = false
                reply(target, result.text, result.error)
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
        // The engine owns native globals and threads; discard them between bound sessions.
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
