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
    private var activeId: String? = null
    private var activeTarget: Messenger? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { request ->
        if (request.what == 1 && request.replyTo != null) calculate(request)
        if (request.what == 2 && busy && request.data.getString("id") == activeId) {
            activeTarget?.let { reply(it, getString(R.string.calculator_cancelled), true, failure = "cancelled") }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        true
    })

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun reply(target: Messenger, text: String, error: Boolean, approximate: Boolean = false, reusable: String? = null, failure: String? = null, parsed: String? = null) {
        try {
            target.send(Message.obtain(null, 1).apply {
                data = Bundle().apply { putString("text", text); putBoolean("error", error)
                    putBoolean("approximate", approximate); putString("reusable", reusable); putString("failure", failure); putString("parsed", parsed) }
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
        activeId = request.data.getString("id"); activeTarget = target
        val degrees = request.data.getBoolean("degrees")
        val exact = request.data.getBoolean("exact")
        val preview = request.data.getBoolean("preview") // Messages are recycled after the handler returns.
        // Android has no pthread cancellation. The deadline kills only this service process.
        val watchdog = Runnable {
            reply(target, getString(R.string.calculator_timeout), true, failure = "timeout")
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        main.postDelayed(watchdog, 4000)
        worker.execute {
            val result = try {
                val privateHome = File(filesDir, "qalculate").apply { mkdirs() }
                Os.setenv("HOME", privateHome.absolutePath, true)
                NativeQalculate.calculate(input, degrees, exact, preview)
            } catch (_: Exception) {
                CalculatorEngine.Result(getString(R.string.calculator_engine_stopped), true)
            } catch (_: LinkageError) {
                CalculatorEngine.Result(getString(R.string.calculator_engine_stopped), true)
            }
            main.post {
                main.removeCallbacks(watchdog)
                busy = false
                activeId = null; activeTarget = null
                val message = when(result.failure) {
                    "division_by_zero" -> getString(R.string.calculator_div_zero)
                    "domain" -> getString(R.string.calculator_domain)
                    "unsupported" -> getString(R.string.calculator_unsupported)
                    "syntax" -> getString(R.string.calculator_invalid) + "\n" + result.text
                    else -> result.text
                }
                reply(target, message, result.error, result.approximate, result.reusable, result.failure, result.parsed)
            }
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
        // Do not kill an idle bound-service process: an immediate new bind can race
        // teardown and Android then throttles restarts. Unfinished native work
        // still must be discarded; idle definitions may remain process-cached.
        if (busy) android.os.Process.killProcess(android.os.Process.myPid())
    }
}
