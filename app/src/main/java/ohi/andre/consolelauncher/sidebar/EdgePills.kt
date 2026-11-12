package ohi.andre.consolelauncher.sidebar

import android.content.Context
import android.content.Intent
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.time.ClockManager

/**
 * The status pills drawn on the screen edge over every app: a running or paused timer or stopwatch, and the
 * minimised podcast. They show whether or not the edge sidebar is on, as long as Re:T-UI may display over
 * other apps; without that permission the launcher keeps its own home-screen pills.
 */
object EdgePills {
    enum class Kind { TIMER, STOPWATCH, PODCAST }

    /** What a pill shows: its text (empty for an icon-only pill) and whether its button reads pause or play. */
    data class Pill(val kind: Kind, val text: String, val running: Boolean)

    /** Set by the launcher while its podcast player is minimised. */
    class Podcast(val playing: () -> Boolean, val toggle: () -> Unit, val open: () -> Unit)

    @Volatile private var clock: List<Pill> = emptyList()
    @Volatile private var podcast: Podcast? = null
    private val listeners = LinkedHashSet<() -> Unit>()

    fun pills(): List<Pill> = clock + listOfNotNull(podcast?.let { Pill(Kind.PODCAST, "", it.playing()) })

    fun active() = pills().isNotEmpty()

    /** The edge pills replace the launcher's own pills whenever they can be drawn. */
    fun onEdge(context: Context) = EdgeSidebar.canDraw(context)

    fun addListener(listener: () -> Unit) { synchronized(listeners) { listeners += listener } }
    fun removeListener(listener: () -> Unit) { synchronized(listeners) { listeners -= listener } }

    private fun changed(context: Context, wasActive: Boolean) {
        synchronized(listeners) { listeners.toList() }.forEach { it() }
        if (wasActive != active()) EdgeSidebar.sync(context)
    }

    fun onClockState(context: Context, manager: ClockManager) {
        val wasActive = active()
        clock = buildList {
            if (manager.isTimerRunning || manager.isTimerPaused)
                add(Pill(Kind.TIMER, ClockManager.formatDuration(manager.timerRemainingMillis), manager.isTimerRunning))
            if (manager.isStopwatchRunning || manager.isStopwatchPaused)
                add(Pill(Kind.STOPWATCH, ClockManager.formatDuration(manager.stopwatchElapsedMillis), manager.isStopwatchRunning))
        }
        changed(context, wasActive)
    }

    fun setPodcast(context: Context, value: Podcast?) {
        val wasActive = active()
        podcast = value
        changed(context, wasActive)
    }

    /** The podcast button changed playback; redraw its pill. */
    fun podcastChanged(context: Context) = changed(context, active())

    /** The pill's button: pause or resume the timer or stopwatch, or play/pause the podcast. */
    fun toggle(context: Context, kind: Kind) {
        val clockManager = ClockManager.getInstance(context)
        when (kind) {
            Kind.TIMER -> if (clockManager.isTimerRunning) clockManager.pauseTimer() else clockManager.resumeTimer()
            Kind.STOPWATCH -> if (clockManager.isStopwatchRunning) clockManager.stopStopwatch() else clockManager.startStopwatch()
            Kind.PODCAST -> { podcast?.toggle?.invoke(); podcastChanged(context) }
        }
    }

    /** Holding the button ends a timer or stopwatch, which also removes its pill. */
    fun dismiss(context: Context, kind: Kind) {
        val clockManager = ClockManager.getInstance(context)
        when (kind) {
            Kind.TIMER -> clockManager.stopTimer()
            Kind.STOPWATCH -> clockManager.resetStopwatch()
            Kind.PODCAST -> Unit
        }
    }

    /** Tapping a pill opens the launcher, on the podcast player for the podcast pill. */
    fun open(context: Context, kind: Kind) {
        if (kind == Kind.PODCAST) podcast?.let { it.open(); return }
        context.startActivity(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
