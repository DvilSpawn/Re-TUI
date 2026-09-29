package ohi.andre.consolelauncher.calculator

import android.app.Service
import android.content.Intent
import android.os.*
import android.system.Os
import com.jherkenhoff.libqalculate.*
import ohi.andre.consolelauncher.R
import java.io.File
import java.util.concurrent.Executors

/** Native failures and the wrapper's unreliable cancellation cannot take down the launcher. */
class QalculateService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var calculator: Calculator? = null
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
        // Do not rely on pthread cancellation in the Android wrapper. Only this process is killed.
        val watchdog = Runnable {
            reply(target, getString(R.string.calculator_timeout), true)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        main.postDelayed(watchdog, 4000)
        worker.execute {
            val result = try {
                val calc = calculator ?: run {
                    val home = File(filesDir, "qalculate").apply { mkdirs() }
                    Os.setenv("HOME", home.absolutePath, true)
                    System.loadLibrary("qalculate_swig")
                    Calculator(true).also {
                        check(it.loadGlobalDefinitions())
                        it.useDecimalPoint()
                        calculator = it
                    }
                }
                calc.clearMessages()
                val evaluation = EvaluationOptions()
                val print = PrintOptions()
                try {
                    evaluation.parse_options.angle_unit = if (degrees) AngleUnit.ANGLE_UNIT_DEGREES else AngleUnit.ANGLE_UNIT_RADIANS
                    evaluation.approximation = if (exact) ApproximationMode.APPROXIMATION_TRY_EXACT else ApproximationMode.APPROXIMATION_APPROXIMATE
                    evaluation.mixed_units_conversion = MixedUnitsConversion.MIXED_UNITS_CONVERSION_NONE
                    val text = calc.calculateAndPrint(input, 2000, evaluation, print)
                    var message = calc.message()
                    val errors = mutableListOf<String>()
                    while (message != null) {
                        if (message.type() == MessageType.MESSAGE_ERROR) errors += message.message()
                        message = calc.nextMessage()
                    }
                    if (calc.aborted()) CalculatorEngine.Result(getString(R.string.calculator_timeout), true)
                    else if (errors.isNotEmpty()) CalculatorEngine.Result(errors.joinToString("\n"), true)
                    else CalculatorEngine.Result(text)
                } finally { evaluation.delete(); print.delete() }
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
