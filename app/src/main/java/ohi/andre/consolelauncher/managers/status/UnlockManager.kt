package ohi.andre.consolelauncher.managers.status

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.text.TextUtils
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Calendar
import java.util.regex.Pattern
import kotlin.math.min
import java.util.regex.Matcher

class UnlockManager(
    context: Context,
    private val size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, 0) {
    private val preferences: SharedPreferences

    private var unlockTimes = 0
    private var unlockHour = 0
    private var unlockMinute = 0
    private val cycleDuration = A_DAY.toInt()
    private var lastUnlockTime: Long = -1
    private var nextUnlockCycleRestart: Long = 0
    private var unlockFormat: String? = null
    private var notAvailableText: String? = null
    private var unlockTimeDivider: String? = null
    private var unlockColor = 0
    private var unlockTimeOrder = 0
    private var lastUnlocks: LongArray? = null

    private val unlockCount: Pattern = Pattern.compile("%c", Pattern.CASE_INSENSITIVE)
    private val advancement: Pattern = Pattern.compile("%a(\\d+)(.)")
    private val timePattern: Pattern = Pattern.compile("(%t\\d*)(?:\\(([^\\)]*)\\))?(\\d+)?")
    private val indexPattern: Pattern = Pattern.compile("%i", Pattern.CASE_INSENSITIVE)
    private val whenPattern = "%w"

    private var lockReceiver: BroadcastReceiver? = null

    private fun init() {
        unlockTimes = preferences.getInt(UNLOCK_KEY, 0)
        unlockColor = XMLPrefsManager.getColor(Theme.unlock_counter_text_color)
        unlockFormat = XMLPrefsManager.get(Behavior.unlock_counter_format)
        notAvailableText = XMLPrefsManager.get(Behavior.not_available_text)
        unlockTimeDivider = XMLPrefsManager.get(Behavior.unlock_time_divider)
        unlockTimeDivider =
            Tuils.patternNewline.matcher(unlockTimeDivider).replaceAll(Tuils.NEWLINE)

        val start = XMLPrefsManager.get(Behavior.unlock_counter_cycle_start)
        val p = Pattern.compile("(\\d{1,2}).(\\d{1,2})")
        var m = p.matcher(start)
        if (!m.find()) {
            m = p.matcher(Behavior.unlock_counter_cycle_start.defaultValue())
            m.find()
        }

        unlockHour = m.group(1).toInt()
        unlockMinute = m.group(2).toInt()
        unlockTimeOrder = XMLPrefsManager.getInt(Behavior.unlock_time_order)
        nextUnlockCycleRestart = preferences.getLong(NEXT_UNLOCK_CYCLE_RESTART, 0)

        m = timePattern.matcher(unlockFormat)
        if (m.find()) {
            var s = m.group(3)
            if (s == null || s.length == 0) s = "1"
            lastUnlocks = LongArray(s.toInt())
            for (c in lastUnlocks!!.indices) {
                lastUnlocks!![c] = -1
            }
        } else {
            lastUnlocks = null
        }
    }

    public override fun start() {
        if (running) return
        running = true
        registerLockReceiver()
        handler.post(unlockTimeRunnable)
    }

    public override fun stop() {
        if (!running) return
        running = false
        unregisterLockReceiver()
        handler.removeCallbacks(unlockTimeRunnable)
    }

    override fun update() {
        invalidateUnlockText()
    }

    fun count(): Int = unlockTimes

    private fun registerLockReceiver() {
        if (lockReceiver != null) return
        val theFilter = IntentFilter()
        theFilter.addAction(Intent.ACTION_SCREEN_ON)
        theFilter.addAction(Intent.ACTION_SCREEN_OFF)
        theFilter.addAction(Intent.ACTION_USER_PRESENT)

        lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val strAction = intent.getAction()
                val myKM = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (Intent.ACTION_USER_PRESENT == strAction || Intent.ACTION_SCREEN_OFF == strAction || Intent.ACTION_SCREEN_ON == strAction) {
                    if (myKM.inKeyguardRestrictedInputMode()) {
                        // onLock handled by UIManager if needed for clearing terminal
                    } else {
                        onUnlock()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context.getApplicationContext(),
            lockReceiver,
            theFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterLockReceiver() {
        if (lockReceiver != null) {
            context.getApplicationContext().unregisterReceiver(lockReceiver)
            lockReceiver = null
        }
    }

    private fun onUnlock() {
        if (System.currentTimeMillis() - lastUnlockTime < 1000 || lastUnlocks == null) return
        lastUnlockTime = System.currentTimeMillis()

        unlockTimes++
        System.arraycopy(lastUnlocks, 0, lastUnlocks, 1, lastUnlocks!!.size - 1)
        lastUnlocks!![0] = lastUnlockTime

        preferences.edit().putInt(UNLOCK_KEY, unlockTimes).apply()
        invalidateUnlockText()
    }

    private val unlockTimeRunnable: Runnable = object : Runnable {
        override fun run() {
            var delay = nextUnlockCycleRestart - System.currentTimeMillis()
            if (delay <= 0) {
                unlockTimes = 0
                if (lastUnlocks != null) {
                    for (c in lastUnlocks!!.indices) lastUnlocks!![c] = -1
                }

                val now = Calendar.getInstance()
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val minute = now.get(Calendar.MINUTE)
                if (unlockHour < hour || (unlockHour == hour && unlockMinute <= minute)) {
                    now.add(Calendar.DAY_OF_YEAR, 1)
                }
                now.set(Calendar.HOUR_OF_DAY, unlockHour)
                now.set(Calendar.MINUTE, unlockMinute)
                now.set(Calendar.SECOND, 0)

                nextUnlockCycleRestart = now.getTimeInMillis()
                preferences.edit()
                    .putLong(NEXT_UNLOCK_CYCLE_RESTART, nextUnlockCycleRestart)
                    .putInt(UNLOCK_KEY, 0)
                    .apply()

                delay = nextUnlockCycleRestart - System.currentTimeMillis()
                if (delay < 0) delay = 0
            }

            invalidateUnlockText()
            delay = min(delay, (cycleDuration / 24).toLong())
            handler.postDelayed(this, delay)
        }
    }

    init {
        this.preferences = context.getSharedPreferences(PREFS_NAME, 0)

        init()
    }

    private fun invalidateUnlockText() {
        var cp = unlockFormat!!
        cp = unlockCount.matcher(cp).replaceAll(unlockTimes.toString())
        cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)

        val m = advancement.matcher(cp)
        if (m.find()) {
            val denominator = m.group(1).toInt()
            val divider = m.group(2)
            val lastCycleStart = nextUnlockCycleRestart - cycleDuration
            val elapsed = (System.currentTimeMillis() - lastCycleStart).toInt()
            val numerator = denominator * elapsed / cycleDuration
            cp = m.replaceAll(numerator.toString() + divider + denominator)
        }

        var s: CharSequence? = Tuils.span(context, size, cp)
        s = Tuils.span(context, s, unlockColor, size)

        val timeMatcher = timePattern.matcher(cp)
        if (timeMatcher.find()) {
            val timeGroup = timeMatcher.group(1)
            var text = timeMatcher.group(2)
            if (text == null) text = whenPattern

            var cs: CharSequence? = Tuils.EMPTYSTRING
            var c: Int
            val change: Int
            if (unlockTimeOrder == 1) { // UP_DOWN
                c = 0
                change = 1
            } else {
                c = lastUnlocks!!.size - 1
                change = -1
            }

            var counter = 0
            while (counter < lastUnlocks!!.size) {
                var t: String? = text
                t = indexPattern.matcher(t).replaceAll((c + 1).toString())
                cs = TextUtils.concat(cs, t)

                val time: CharSequence?
                if (lastUnlocks!![c] > 0) {
                    time = TimeManager.instance!!.getCharSequence(timeGroup, lastUnlocks!![c])
                } else {
                    time = notAvailableText
                }

                if (time == null) {
                    counter++
                    c += change
                    continue
                }
                cs =
                    TextUtils.replace(cs, arrayOf<String>(whenPattern), arrayOf<CharSequence>(time))
                if (counter != lastUnlocks!!.size - 1) cs = TextUtils.concat(cs, unlockTimeDivider)
                counter++
                c += change
            }
            s = TextUtils.replace(
                s,
                arrayOf<String?>(timeMatcher.group(0)),
                arrayOf<CharSequence?>(cs)
            )
        }

        if (listener != null) {
            listener.onUpdate(UIManager.Label.unlock, s)
        }
    }

    companion object {
        const val UNLOCK_KEY: String = "unlockTimes"
        const val NEXT_UNLOCK_CYCLE_RESTART: String = "nextUnlockRestart"
        private val A_DAY = (1000 * 60 * 60 * 24).toLong()
        private const val PREFS_NAME = "ui"
    }
}
