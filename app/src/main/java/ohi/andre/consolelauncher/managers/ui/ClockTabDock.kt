package ohi.andre.consolelauncher.managers.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager.Companion.CLOCK_EDGE_BOTTOM
import ohi.andre.consolelauncher.UIManager.Companion.CLOCK_EDGE_LEFT
import ohi.andre.consolelauncher.UIManager.Companion.CLOCK_EDGE_RIGHT
import ohi.andre.consolelauncher.UIManager.Companion.CLOCK_EDGE_TOP
import ohi.andre.consolelauncher.UIManager.Companion.PREF_STOPWATCH_BADGE_EDGE
import ohi.andre.consolelauncher.UIManager.Companion.PREF_STOPWATCH_BADGE_FRACTION
import ohi.andre.consolelauncher.UIManager.Companion.PREF_TIMER_BADGE_EDGE
import ohi.andre.consolelauncher.UIManager.Companion.PREF_TIMER_BADGE_FRACTION
import ohi.andre.consolelauncher.managers.time.ClockManager
import ohi.andre.consolelauncher.tuils.LauncherPillStyle
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Timer, stopwatch and podcast pills: docking them to a screen edge and remembering where. */
class ClockTabDock(
    private val context: Context,
    private val rootView: View,
    private val preferencesProvider: () -> SharedPreferences,
    private val systemInsetLeft: () -> Int,
    private val systemInsetTop: () -> Int,
    private val systemInsetRight: () -> Int,
    private val systemInsetBottom: () -> Int,
    private val podcastLandscapePresentation: () -> Boolean,
    private val styleClockOverlay: (View) -> Unit
) {
    private var timerTabDockReady = false
    private var stopwatchTabDockReady = false

    fun styleClockTab(tab: TextView?, listener: View.OnClickListener?) {
        if (tab == null) {
            return
        }
        LauncherPillStyle.apply(context, tab)
        tab.setOnClickListener(listener)
    }

    fun makeClockTabDockable(
        tab: View?,
        edgeKey: String?,
        fractionKey: String?,
        defaultEdge: String?,
        defaultFraction: Float
    ) {
        if (tab == null) {
            return
        }

        val touchSlop = ViewConfiguration.get(context).getScaledTouchSlop()
        val downRawX = FloatArray(1)
        val downRawY = FloatArray(1)
        val startX = FloatArray(1)
        val startY = FloatArray(1)
        val dragging = BooleanArray(1)

        tab.setOnTouchListener(OnTouchListener { view: View?, event: MotionEvent? ->
            val parent = view!!.getParent() as View?
            if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
                false
            } else when (event!!.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX[0] = event.getRawX()
                    downRawY[0] = event.getRawY()
                    startX[0] = view.getX()
                    startY[0] = view.getY()
                    dragging[0] = false
                    view.getParent().requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.getRawX() - downRawX[0]
                    val dy = event.getRawY() - downRawY[0]
                    if (!dragging[0] && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging[0] = true
                    }
                    if (dragging[0]) {
                        setClockTabPosition(view, startX[0] + dx, startY[0] + dy, parent)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.getParent().requestDisallowInterceptTouchEvent(false)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.getParent().requestDisallowInterceptTouchEvent(false)
                    if (dragging[0]) {
                        snapClockTabToNearestEdge(view, parent, edgeKey, fractionKey)
                    } else {
                        view.performClick()
                    }
                    true
                }

                else -> true
            }
        })

        tab.post(Runnable {
            applyClockTabDock(
                tab,
                edgeKey,
                fractionKey,
                defaultEdge,
                defaultFraction
            )
        })
    }

    fun applyClockTabDock(
        view: View,
        edgeKey: String?,
        fractionKey: String?,
        defaultEdge: String?,
        defaultFraction: Float
    ) {
        val parent = view.getParent() as View?
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.post(Runnable {
                applyClockTabDock(
                    view,
                    edgeKey,
                    fractionKey,
                    defaultEdge,
                    defaultFraction
                )
            })
            return
        }

        val edge = preferencesProvider().getString(edgeKey, defaultEdge)
        var fraction = preferencesProvider().getFloat(
            fractionKey,
            defaultFraction
        )
        fraction = max(0f, min(1f, fraction))

        val margin = Tuils.dpToPx(context, 6)
        val minX = (parent.paddingLeft + margin).toFloat()
        val minY = (parent.paddingTop + margin).toFloat()
        val maxX = max(minX, (parent.width - parent.paddingRight - view.width - margin).toFloat())
        val maxY = max(minY, (parent.height - parent.paddingBottom - view.height - margin).toFloat())
        val x: Float
        val y: Float

        if (CLOCK_EDGE_LEFT == edge) {
            x = minX
            y = minY + fraction * max(0f, maxY - minY)
        } else if (CLOCK_EDGE_TOP == edge) {
            x = minX + fraction * max(0f, maxX - minX)
            y = minY
        } else if (CLOCK_EDGE_BOTTOM == edge) {
            x = minX + fraction * max(0f, maxX - minX)
            y = maxY
        } else {
            x = maxX
            y = minY + fraction * max(0f, maxY - minY)
        }

        setClockTabPosition(view, x, y, parent)
    }

    fun forceClockTabEdge(view: View, edge: String, fraction: Float) {
        val parent = view.parent as? View ?: return
        if (parent.width <= 0 || parent.height <= 0 || view.width <= 0 || view.height <= 0) {
            view.post { forceClockTabEdge(view, edge, fraction) }
            return
        }
        val margin = Tuils.dpToPx(context, if (podcastLandscapePresentation()) 14 else 6)
        // Prefer system insets — parent padding can lag one frame after rotate.
        val padL = max(parent.paddingLeft, systemInsetLeft())
        val padR = max(parent.paddingRight, systemInsetRight())
        val padT = max(parent.paddingTop, systemInsetTop())
        val padB = max(parent.paddingBottom, systemInsetBottom())
        val minX = (padL + margin).toFloat()
        val minY = (padT + margin).toFloat()
        val maxX = max(minX, (parent.width - padR - view.width - margin).toFloat())
        val maxY = max(minY, (parent.height - padB - view.height - margin).toFloat())
        val frac = fraction.coerceIn(0f, 1f)
        val x: Float
        val y: Float
        when (edge) {
            CLOCK_EDGE_LEFT -> {
                x = minX
                y = minY + frac * max(0f, maxY - minY)
            }
            CLOCK_EDGE_TOP -> {
                x = minX + frac * max(0f, maxX - minX)
                y = minY
            }
            CLOCK_EDGE_BOTTOM -> {
                x = minX + frac * max(0f, maxX - minX)
                y = maxY
            }
            else -> {
                x = maxX
                y = minY + frac * max(0f, maxY - minY)
            }
        }
        setClockTabPosition(view, x, y, parent)
    }

    private fun setClockTabPosition(view: View, x: Float, y: Float, parent: View) {
        val margin = Tuils.dpToPx(context, 6)
        val padL = max(parent.paddingLeft, systemInsetLeft())
        val padR = max(parent.paddingRight, systemInsetRight())
        val padT = max(parent.paddingTop, systemInsetTop())
        val padB = max(parent.paddingBottom, systemInsetBottom())
        val minX = (padL + margin).toFloat()
        val minY = (padT + margin).toFloat()
        val maxX = max(minX, (parent.width - padR - view.width - margin).toFloat())
        val maxY = max(minY, (parent.height - padB - view.height - margin).toFloat())
        view.setX(max(minX, min(x, maxX)))
        view.setY(max(minY, min(y, maxY)))
    }

    private fun snapClockTabToNearestEdge(
        view: View,
        parent: View,
        edgeKey: String?,
        fractionKey: String?
    ) {
        val margin = Tuils.dpToPx(context, 6)
        val minX = (parent.paddingLeft + margin).toFloat()
        val minY = (parent.paddingTop + margin).toFloat()
        val maxX = max(minX, (parent.width - parent.paddingRight - view.width - margin).toFloat())
        val maxY = max(minY, (parent.height - parent.paddingBottom - view.height - margin).toFloat())
        val leftDistance = view.getX() - minX
        val topDistance = view.getY() - minY
        val rightDistance = maxX - view.getX()
        val bottomDistance = maxY - view.getY()

        var edge: String = CLOCK_EDGE_LEFT
        var nearest = leftDistance
        if (rightDistance < nearest) {
            nearest = rightDistance
            edge = CLOCK_EDGE_RIGHT
        }
        if (topDistance < nearest) {
            nearest = topDistance
            edge = CLOCK_EDGE_TOP
        }
        if (bottomDistance < nearest) {
            edge = CLOCK_EDGE_BOTTOM
        }

        var fraction: Float
        if (CLOCK_EDGE_TOP == edge || CLOCK_EDGE_BOTTOM == edge) {
            fraction = (view.getX() - minX) / max(1f, maxX - minX)
        } else {
            fraction = (view.getY() - minY) / max(1f, maxY - minY)
        }
        fraction = max(0f, min(1f, fraction))

        preferencesProvider().edit()
            .putString(edgeKey, edge)
            .putFloat(fractionKey, fraction)
            .apply()
        applyClockTabDock(view, edgeKey, fractionKey, edge, fraction)
    }

    fun updateClockOverlay(intent: Intent) {
        val timerTab = rootView.findViewById<TextView?>(R.id.timer_tab)
        val stopwatchTab = rootView.findViewById<TextView?>(R.id.stopwatch_tab)
        if (timerTab == null || stopwatchTab == null) {
            return
        }
        // The timer and stopwatch show as pills on the screen edge, over every app, when those can be drawn.
        if (ohi.andre.consolelauncher.sidebar.EdgePills.onEdge(context)) {
            timerTab.visibility = View.GONE; stopwatchTab.visibility = View.GONE
            return
        }

        styleClockOverlay(rootView)

        val timerRunning = intent.getBooleanExtra(ClockManager.EXTRA_TIMER_RUNNING, false)
        val timerRemaining = intent.getLongExtra(ClockManager.EXTRA_TIMER_REMAINING, 0L)
        val stopwatchRunning = intent.getBooleanExtra(ClockManager.EXTRA_STOPWATCH_RUNNING, false)
        val stopwatchElapsed = intent.getLongExtra(ClockManager.EXTRA_STOPWATCH_ELAPSED, 0L)


        if (timerRunning) {
            timerTab.setVisibility(View.VISIBLE)
            timerTab.setText(ClockManager.formatDuration(timerRemaining))
            if (!timerTabDockReady) {
                makeClockTabDockable(
                    timerTab,
                    PREF_TIMER_BADGE_EDGE,
                    PREF_TIMER_BADGE_FRACTION,
                    CLOCK_EDGE_RIGHT,
                    0.45f
                )
                timerTabDockReady = true
            }
        } else {
            timerTab.setVisibility(View.GONE)
        }

        if (stopwatchRunning) {
            stopwatchTab.setVisibility(View.VISIBLE)
            stopwatchTab.setText(ClockManager.formatDuration(stopwatchElapsed))
            if (!stopwatchTabDockReady) {
                makeClockTabDockable(
                    stopwatchTab,
                    PREF_STOPWATCH_BADGE_EDGE,
                    PREF_STOPWATCH_BADGE_FRACTION,
                    CLOCK_EDGE_RIGHT,
                    0.55f
                )
                stopwatchTabDockReady = true
            }
        } else {
            stopwatchTab.setVisibility(View.GONE)
        }
    }

    /** Timer and stopwatch only; the podcast pill is re-applied by its owner. */
    fun reapplyVisibleTimerAndStopwatchDocks() {
        val root = rootView
        val timer = root.findViewById<TextView?>(R.id.timer_tab)
        if (timer != null && timer.visibility == View.VISIBLE) {
            applyClockTabDock(
                timer,
                PREF_TIMER_BADGE_EDGE,
                PREF_TIMER_BADGE_FRACTION,
                CLOCK_EDGE_RIGHT,
                0.45f
            )
        }
        val stopwatch = root.findViewById<TextView?>(R.id.stopwatch_tab)
        if (stopwatch != null && stopwatch.visibility == View.VISIBLE) {
            applyClockTabDock(
                stopwatch,
                PREF_STOPWATCH_BADGE_EDGE,
                PREF_STOPWATCH_BADGE_FRACTION,
                CLOCK_EDGE_RIGHT,
                0.55f
            )
        }
    }
}
