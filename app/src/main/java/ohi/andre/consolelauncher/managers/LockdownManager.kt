package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.BuildConfig
import kotlin.math.max

class LockdownManager private constructor(private val appContext: Context) {
    private val lbm = LocalBroadcastManager.getInstance(appContext)
    private val handler = Handler(Looper.getMainLooper())

    private var endElapsedRealtime = -1L
    var totalDuration: Long = 0L
        private set
    var reason: String = ""
        private set
    var isRunning: Boolean = false
        private set

    private val ticker: Runnable = object : Runnable {
        override fun run() {
            if (!this@LockdownManager.isRunning) return
            if (remainingMillis <= 0L) {
                stopInternal("Lockdown complete.")
            } else {
                broadcastState(null)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        restoreState()
    }

    @Synchronized
    fun start(durationMs: Long, label: String?): String {
        if (durationMs <= 0L) {
            return "Invalid duration. Use values like 30s, 5m, or 1h."
        }
        RetuiCreditManager.wallet(appContext)
        reason = label?.trim().orEmpty()
        totalDuration = durationMs
        endElapsedRealtime = SystemClock.elapsedRealtime() + durationMs
        isRunning = true
        saveState()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        broadcastState("Lockdown started.")
        return "Lockdown started for " + ClockManager.formatDuration(durationMs) + "."
    }

    @Synchronized
    fun stop(message: String = "Lockdown terminated."): String {
        if (!isRunning) return "No lockdown is running."
        stopInternal(message)
        return message
    }

    val remainingMillis: Long
        @Synchronized get() {
            if (!isRunning) return 0L
            return max(0L, endElapsedRealtime - SystemClock.elapsedRealtime())
        }

    val status: String
        @Synchronized get() {
            if (!isRunning) return "No lockdown is running."
            val label = if (reason.isBlank()) "" else "\nReason: $reason"
            return "Lockdown remaining: " + ClockManager.formatDuration(remainingMillis) + "." + label
        }

    private fun restoreState() {
        if (!RetuiCreditManager.isDystopiaEnabled(appContext)) {
            prefs().edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_TIME, -1L)
                .apply()
            return
        }

        val prefs = prefs()
        isRunning = prefs.getBoolean(KEY_RUNNING, false)
        endElapsedRealtime = prefs.getLong(KEY_END_TIME, -1L)
        totalDuration = prefs.getLong(KEY_DURATION, 0L)
        reason = prefs.getString(KEY_REASON, "") ?: ""

        if (isRunning && endElapsedRealtime > SystemClock.elapsedRealtime()) {
            handler.post(ticker)
        } else if (isRunning) {
            stopInternal("Lockdown complete.")
        }
    }

    private fun saveState() {
        prefs().edit()
            .putBoolean(KEY_RUNNING, isRunning)
            .putLong(KEY_END_TIME, endElapsedRealtime)
            .putLong(KEY_DURATION, totalDuration)
            .putString(KEY_REASON, reason)
            .apply()
    }

    @Synchronized
    private fun stopInternal(message: String?) {
        isRunning = false
        endElapsedRealtime = -1L
        totalDuration = 0L
        handler.removeCallbacks(ticker)
        saveState()
        broadcastState(message)
    }

    private fun broadcastState(message: String?) {
        val intent = Intent(ACTION_LOCKDOWN_STATE)
        intent.putExtra(EXTRA_LOCKDOWN_RUNNING, isRunning)
        intent.putExtra(EXTRA_LOCKDOWN_REMAINING, remainingMillis)
        intent.putExtra(EXTRA_LOCKDOWN_TOTAL, totalDuration)
        intent.putExtra(EXTRA_LOCKDOWN_REASON, reason)
        if (message != null) {
            intent.putExtra(EXTRA_MESSAGE, message)
        }
        lbm.sendBroadcast(intent)
    }

    private fun prefs(): SharedPreferences =
        appContext.getSharedPreferences(RetuiCreditManager.PREFS, Context.MODE_PRIVATE)

    companion object {
        val ACTION_LOCKDOWN_STATE: String = BuildConfig.APPLICATION_ID + ".lockdown_state"
        const val EXTRA_LOCKDOWN_RUNNING: String = "lockdown_running"
        const val EXTRA_LOCKDOWN_REMAINING: String = "lockdown_remaining"
        const val EXTRA_LOCKDOWN_TOTAL: String = "lockdown_total"
        const val EXTRA_LOCKDOWN_REASON: String = "lockdown_reason"
        const val EXTRA_MESSAGE: String = "message"

        private const val KEY_RUNNING = "lockdown_running"
        private const val KEY_END_TIME = "lockdown_end_elapsed"
        private const val KEY_DURATION = "lockdown_total_ms"
        private const val KEY_REASON = "lockdown_reason"

        private var instance: LockdownManager? = null

        @Synchronized
        fun getInstance(context: Context): LockdownManager {
            if (instance == null) {
                instance = LockdownManager(context.applicationContext)
            }
            return instance!!
        }
    }
}
