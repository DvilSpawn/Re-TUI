package ohi.andre.consolelauncher.managers

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import ohi.andre.consolelauncher.BuildConfig
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.max
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class ClockManager private constructor(context: Context) {
    private val appContext: Context?
    private val lbm: LocalBroadcastManager
    private val handler: Handler

    private var timerEndElapsedRealtime = -1L
    private var timerTotalDuration = 0L

    @get:Synchronized
    var isTimerRunning: Boolean = false
        private set

    private var stopwatchStartElapsedRealtime = -1L
    private var stopwatchBaseElapsed = 0L

    @get:Synchronized
    var isStopwatchRunning: Boolean = false
        private set

    private val ticker: Runnable = object : Runnable {
        override fun run() {
            var needsAnotherTick = false
            var message: String? = null

            if (this@ClockManager.isTimerRunning) {
                val remaining: Long = this@ClockManager.timerRemainingMillis
                if (remaining <= 0L) {
                    this@ClockManager.isTimerRunning = false
                    timerEndElapsedRealtime = -1L
                    timerTotalDuration = 0L
                    playCompletionTone()
                    message = "Timer finished."
                } else {
                    needsAnotherTick = true
                }
            }

            if (this@ClockManager.isStopwatchRunning) {
                needsAnotherTick = true
            }

            broadcastState(message)
            if (needsAnotherTick) {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        this.appContext = context
        this.lbm = LocalBroadcastManager.getInstance(context)
        this.handler = Handler(Looper.getMainLooper())
    }

    @Synchronized
    fun startTimer(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "Invalid duration. Use values like 30s, 5m, or 1h."
        }

        timerEndElapsedRealtime = SystemClock.elapsedRealtime() + durationMs
        timerTotalDuration = durationMs
        this.isTimerRunning = true
        scheduleTicker()
        broadcastState()
        return "Timer started for " + formatDuration(durationMs) + "."
    }

    @Synchronized
    fun addToTimer(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "Invalid duration. Use values like 30s, 5m, or 1h."
        }
        if (!this.isTimerRunning) {
            return "No running timer to extend."
        }

        timerEndElapsedRealtime += durationMs
        timerTotalDuration = max(timerTotalDuration, this.timerRemainingMillis) + durationMs
        scheduleTicker()
        broadcastState()
        return "Added " + formatDuration(durationMs) + " to timer."
    }

    @Synchronized
    fun stopTimer(): String {
        if (!this.isTimerRunning) {
            return "No timer is running."
        }

        this.isTimerRunning = false
        timerEndElapsedRealtime = -1L
        timerTotalDuration = 0L
        broadcastState()
        return "Timer stopped."
    }

    @get:Synchronized
    val timerStatus: String
        get() {
            if (!this.isTimerRunning) {
                return "No timer is running."
            }
            return "Timer remaining: " + formatDuration(this.timerRemainingMillis) + "."
        }

    @get:Synchronized
    val timerRemainingMillis: Long
        get() {
            if (!this.isTimerRunning) {
                return 0L
            }
            return max(
                0L,
                timerEndElapsedRealtime - SystemClock.elapsedRealtime()
            )
        }

    @get:Synchronized
    val timerTotalMillis: Long
        get() = if (this.isTimerRunning) max(0L, timerTotalDuration) else 0L

    @Synchronized
    fun startStopwatch(): String {
        if (this.isStopwatchRunning) {
            return "Stopwatch already running: " + formatDuration(this.stopwatchElapsedMillis) + "."
        }

        stopwatchStartElapsedRealtime = SystemClock.elapsedRealtime()
        this.isStopwatchRunning = true
        scheduleTicker()
        broadcastState()
        return "Stopwatch started."
    }

    @Synchronized
    fun stopStopwatch(): String {
        if (!this.isStopwatchRunning) {
            return "No stopwatch is running."
        }

        stopwatchBaseElapsed = this.stopwatchElapsedMillis
        this.isStopwatchRunning = false
        stopwatchStartElapsedRealtime = -1L
        broadcastState()
        return "Stopwatch stopped at " + formatDuration(stopwatchBaseElapsed) + "."
    }

    @Synchronized
    fun resetStopwatch(): String {
        this.isStopwatchRunning = false
        stopwatchStartElapsedRealtime = -1L
        stopwatchBaseElapsed = 0L
        broadcastState()
        return "Stopwatch reset."
    }

    @get:Synchronized
    val stopwatchStatus: String
        get() {
            if (!this.isStopwatchRunning && stopwatchBaseElapsed <= 0L) {
                return "No stopwatch is running."
            }
            return "Stopwatch: " + formatDuration(this.stopwatchElapsedMillis) + "."
        }

    @get:Synchronized
    val stopwatchElapsedMillis: Long
        get() {
            if (this.isStopwatchRunning && stopwatchStartElapsedRealtime > 0L) {
                return stopwatchBaseElapsed + (SystemClock.elapsedRealtime() - stopwatchStartElapsedRealtime)
            }
            return stopwatchBaseElapsed
        }

    @Synchronized
    fun broadcastState() {
        broadcastState(null)
    }

    @Synchronized
    private fun broadcastState(message: String?) {
        val intent: Intent = Intent(ACTION_CLOCK_STATE)
        intent.putExtra(EXTRA_TIMER_RUNNING, this.isTimerRunning)
        intent.putExtra(EXTRA_TIMER_REMAINING, this.timerRemainingMillis)
        intent.putExtra(EXTRA_STOPWATCH_RUNNING, this.isStopwatchRunning)
        intent.putExtra(EXTRA_STOPWATCH_ELAPSED, this.stopwatchElapsedMillis)
        if (message != null) {
            intent.putExtra(EXTRA_MESSAGE, message)
        }
        lbm.sendBroadcast(intent)
    }

    private fun scheduleTicker() {
        handler.removeCallbacks(ticker)
        if (this.isTimerRunning || this.isStopwatchRunning) {
            handler.post(ticker)
        }
    }

    private fun playCompletionTone() {
        try {
            val toneGenerator: ToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 70)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
            handler.postDelayed(Runnable { toneGenerator.release() }, 1300L)
        } catch (ignored: Exception) {
        }
    }

    companion object {
        val ACTION_CLOCK_STATE: String = BuildConfig.APPLICATION_ID + ".clock_state"
        const val EXTRA_TIMER_RUNNING: String = "timer_running"
        const val EXTRA_TIMER_REMAINING: String = "timer_remaining"
        const val EXTRA_STOPWATCH_RUNNING: String = "stopwatch_running"
        const val EXTRA_STOPWATCH_ELAPSED: String = "stopwatch_elapsed"
        const val EXTRA_MESSAGE: String = "message"

        private val DURATION_PATTERN: Pattern = Pattern.compile("(?i)(\\d+)\\s*([smh])")

        private var instance: ClockManager? = null
        private var durationSelfChecked = false

        @Synchronized
        fun getInstance(context: Context): ClockManager {
            if (instance == null) {
                instance = ClockManager(context.getApplicationContext())
            }
            return instance!!
        }

        fun parseDurationMillis(value: String?): Long {
            ensureDurationSelfCheck()
            return parseDurationMillisUnchecked(value)
        }

        private fun parseDurationMillisUnchecked(value: String?): Long {
            if (value == null) {
                return -1L
            }

            val input = value.trim()
            val matcher: Matcher = DURATION_PATTERN.matcher(input)
            var total = 0L
            var end = 0
            var found = false

            while (matcher.find()) {
                if (input.substring(end, matcher.start()).isNotBlank()) {
                    return -1L
                }
                val amount = matcher.group(1).toLong()
                val unit = matcher.group(2).lowercase(Locale.getDefault())
                total += when (unit) {
                    "s" -> amount * 1000L
                    "m" -> amount * 60000L
                    "h" -> amount * 3600000L
                    else -> return -1L
                }
                end = matcher.end()
                found = true
            }

            if (!found || input.substring(end).isNotBlank()) {
                return -1L
            }
            return total
        }

        private fun ensureDurationSelfCheck() {
            if (durationSelfChecked) return
            check(parseDurationMillisUnchecked("30m") == 1800000L)
            check(parseDurationMillisUnchecked("1h 30m") == 5400000L)
            check(parseDurationMillisUnchecked("00h 00m") == 0L)
            check(parseDurationMillisUnchecked("1h nope") == -1L)
            durationSelfChecked = true
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = max(0L, millis / 1000L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L

            if (hours > 0L) {
                return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
            }
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }
}
