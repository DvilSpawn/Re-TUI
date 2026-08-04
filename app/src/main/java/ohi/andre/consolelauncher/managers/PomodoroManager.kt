package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.SystemClock
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import kotlin.math.max
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Looper
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager

class PomodoroManager private constructor(private val appContext: Context) {
    enum class SessionType {
        FOCUS, BREAK, FINISHED
    }

    private val lbm: LocalBroadcastManager
    private val handler: Handler

    private var sessionEndElapsedRealtime = -1L
    var totalDuration: Long = 0L
        private set
    var isRunning: Boolean = false
        private set
    var taskName: String? = ""
        private set
    var currentType: SessionType = SessionType.FOCUS
        private set
    var completedFocuses: Int = 0
        private set

    private val ticker: Runnable = object : Runnable {
        override fun run() {
            if (!this@PomodoroManager.isRunning) return

            val remaining: Long = this@PomodoroManager.remainingMillis
            if (remaining <= 0L) {
                handleTransition()
            } else {
                broadcastState(null)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        this.lbm = LocalBroadcastManager.getInstance(appContext)
        this.handler = Handler(Looper.getMainLooper())
        restoreState()
    }

    private fun saveState() {
        appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_RUNNING, this.isRunning)
            .putLong(KEY_END_TIME, sessionEndElapsedRealtime)
            .putLong(KEY_DURATION, totalDuration)
            .putString(KEY_TASK, taskName)
            .putString(KEY_TYPE, currentType.name)
            .putInt(KEY_COMPLETED, completedFocuses)
            .apply()
    }

    private fun restoreState() {
        val prefs: SharedPreferences =
            appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        this.isRunning = prefs.getBoolean(KEY_RUNNING, false)
        this.sessionEndElapsedRealtime = prefs.getLong(KEY_END_TIME, -1L)
        this.totalDuration = prefs.getLong(KEY_DURATION, 0L)
        this.taskName = prefs.getString(KEY_TASK, "")
        this.completedFocuses = prefs.getInt(KEY_COMPLETED, 0)
        try {
            this.currentType =
                SessionType.valueOf(prefs.getString(KEY_TYPE, SessionType.FOCUS.name) ?: SessionType.FOCUS.name)
        } catch (e: Exception) {
            this.currentType = SessionType.FOCUS
        }

        if (this.isRunning && sessionEndElapsedRealtime > SystemClock.elapsedRealtime()) {
            handler.post(ticker)
        } else if (this.isRunning && currentType != SessionType.FINISHED) {
            handleTransition()
        }
    }

    @Synchronized
    fun startPomodoro(task: String?) {
        this.taskName = task
        this.completedFocuses = 0
        this.isRunning = true
        startFocusSession()
    }

    private fun startFocusSession() {
        this.currentType = SessionType.FOCUS
        this.totalDuration =
            minutesToMillis(settingMinutes(Behavior.pomodoro_focus_minutes, DEFAULT_FOCUS_MINUTES))
        this.sessionEndElapsedRealtime = SystemClock.elapsedRealtime() + totalDuration

        saveState()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        broadcastState("Focus session started: " + taskName)
    }

    private fun startBreakSession() {
        this.currentType = SessionType.BREAK
        this.totalDuration =
            minutesToMillis(settingMinutes(Behavior.pomodoro_relax_minutes, DEFAULT_RELAX_MINUTES))
        this.sessionEndElapsedRealtime = SystemClock.elapsedRealtime() + totalDuration

        saveState()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        broadcastState("Take a break!")
    }

    private fun settingMinutes(key: Behavior?, fallback: Int): Int {
        try {
            return max(1, XMLPrefsManager.getInt(key))
        } catch (e: Exception) {
            return fallback
        }
    }

    private fun minutesToMillis(minutes: Int): Long {
        return minutes * 60 * 1000L
    }

    private fun handleTransition() {
        playTone()
        if (currentType == SessionType.FOCUS) {
            completedFocuses++
            if (completedFocuses >= TOTAL_CYCLES) {
                currentType = SessionType.FINISHED
                this.isRunning = true // Stay in finished state to show the message
                saveState()
                broadcastState("Good job! You did great!")
            } else {
                startBreakSession()
            }
        } else if (currentType == SessionType.BREAK) {
            startFocusSession()
        }
    }

    @Synchronized
    fun stopSession() {
        this.isRunning = false
        this.sessionEndElapsedRealtime = -1L
        handler.removeCallbacks(ticker)
        saveState()
        broadcastState("Session terminated.")
    }

    val remainingMillis: Long
        get() {
            if (!this.isRunning || currentType == SessionType.FINISHED) return 0L
            return max(
                0L,
                sessionEndElapsedRealtime - SystemClock.elapsedRealtime()
            )
        }

    private fun playTone() {
        if (LauncherSoundManager.isEnabled()) {
            LauncherSoundManager.play(appContext, LauncherSoundManager.Event.SUCCESS)
            return
        }
        try {
            val toneGenerator: ToneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 2000)
            handler.postDelayed(Runnable { toneGenerator.release() }, 2100L)
        } catch (ignored: Exception) {
        }
    }

    private fun broadcastState(message: String?) {
        val intent: Intent = Intent(ACTION_POMODORO_STATE)
        intent.putExtra(EXTRA_POMODORO_RUNNING, this.isRunning)
        intent.putExtra(EXTRA_POMODORO_REMAINING, this.remainingMillis)
        intent.putExtra(EXTRA_POMODORO_TOTAL, totalDuration)
        intent.putExtra(EXTRA_POMODORO_TASK, taskName)
        intent.putExtra(EXTRA_POMODORO_TYPE, currentType.name)
        intent.putExtra(EXTRA_POMODORO_CYCLE, completedFocuses)
        if (message != null) {
            intent.putExtra(EXTRA_MESSAGE, message)
        }
        lbm.sendBroadcast(intent)
    }

    companion object {
        val ACTION_POMODORO_STATE: String = BuildConfig.APPLICATION_ID + ".pomodoro_state"
        const val EXTRA_POMODORO_RUNNING: String = "pomodoro_running"
        const val EXTRA_POMODORO_REMAINING: String = "pomodoro_remaining"
        const val EXTRA_POMODORO_TOTAL: String = "pomodoro_total"
        const val EXTRA_POMODORO_TASK: String = "pomodoro_task"
        const val EXTRA_POMODORO_TYPE: String = "pomodoro_type"
        const val EXTRA_POMODORO_CYCLE: String = "pomodoro_cycle"
        const val EXTRA_MESSAGE: String = "message"

        private const val TOTAL_CYCLES = 4
        private const val DEFAULT_FOCUS_MINUTES = 25
        private const val DEFAULT_RELAX_MINUTES = 5

        private var instance: PomodoroManager? = null

        @Synchronized
        fun getInstance(context: Context): PomodoroManager {
            if (instance == null) {
                instance = PomodoroManager(context.getApplicationContext())
            }
            return instance!!
        }

        private const val PREF_NAME = "pomodoro_state"
        private const val KEY_RUNNING = "running"
        private const val KEY_END_TIME = "end_time"
        private const val KEY_DURATION = "duration"
        private const val KEY_TASK = "task"
        private const val KEY_TYPE = "type"
        private const val KEY_COMPLETED = "completed"
    }
}
