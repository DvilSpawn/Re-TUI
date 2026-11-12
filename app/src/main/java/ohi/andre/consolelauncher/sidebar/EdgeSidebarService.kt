package ohi.andre.consolelauncher.sidebar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.presentation.HomeAppearance
import kotlin.math.abs

/**
 * Draws over other apps as part of the screen bezel: the edge sidebar when it is on (tap the tab to open the
 * dock; hold it or swipe in to slide onto an action), and the timer, stopwatch and podcast pills while any
 * is running, whether or not the sidebar is on.
 */
class EdgeSidebarService : Service() {
    companion object {
        private const val CHANNEL = "edge_sidebar"
        private const val NOTIFICATION_ID = 7501
        private const val ACTION_TURN_OFF = "ohi.andre.consolelauncher.sidebar.TURN_OFF"
        private const val KEY_FRACTION = "fraction"
        private const val KEY_FOCUS = "stack_focus"
        private const val DEFAULT_FRACTION = 0.4f
        @Volatile private var running = false

        fun start(context: Context) {
            // Called again while running to pick up a changed side or theme.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, EdgeSidebarService::class.java)) }
        }

        fun stop(context: Context) {
            if (!running) return
            context.stopService(Intent(context, EdgeSidebarService::class.java))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    /** The resting stack: the sidebar's tab and everything running, in one window. */
    private var tab: EdgeStackView? = null
    /** The open dock: its own window over the tab, added when opening and removed once closed. */
    private var panel: EdgeDockView? = null
    private val open get() = panel != null
    private var tabParams: WindowManager.LayoutParams? = null
    private var centerY = 0
    /** The settings revision the windows were last built from. */
    private var builtRevision: Long? = null
    private var builtSidebar = false
    /** Tab and pill positions the windows were last built from; a restored backup changes them without a settings revision. */
    private var builtLayout: Map<String, *>? = null
    /** The drop being dragged and the window it was lifted from. */
    private var activeBlob: Pair<View, EdgeBlob>? = null
    private val prefs by lazy { getSharedPreferences("edge_sidebar", MODE_PRIVATE) }

    private val pillsChanged: () -> Unit = { main.post { syncStack() } }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        windows = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!XMLPrefsManager.commonsLoaded) XMLPrefsManager.loadCommons(this)
        EdgePills.addListener(pillsChanged)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, notification())
        if (intent?.action == ACTION_TURN_OFF) {
            // Saving the setting runs EdgeSidebar.sync, which stops the service or keeps it for running pills.
            LauncherSettings.set(this, Behavior.edge_sidebar, "false")
            return START_NOT_STICKY
        }
        val sidebar = EdgeSidebar.enabled()
        if ((!sidebar && !EdgePills.active()) || !EdgeSidebar.canDraw(this)) { stopSelf(); return START_NOT_STICKY }
        val revision = LauncherSettings.currentRevision()
        val layout = prefs.all
        if (edgeSidebarNeedsRebuild(sidebar, builtSidebar, builtRevision, revision) || layout != builtLayout) {
            builtRevision = revision
            builtSidebar = sidebar
            builtLayout = layout
            showStack()
        } else syncStack()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (tab != null) showStack()
    }

    override fun onDestroy() {
        running = false
        EdgePills.removeListener(pillsChanged)
        removeDock()
        super.onDestroy()
    }

    private fun removeDock() {
        panel?.let { runCatching { windows.removeView(it) } }
        tab?.let { releaseBlob(it); runCatching { windows.removeView(it) } }
        tab = null; panel = null; tabParams = null
    }

    /** A window removed mid-drag never gets its ACTION_UP, so its drop has to be told to settle or it never ends. */
    private fun releaseBlob(host: View) {
        activeBlob?.takeIf { it.first === host }?.let { it.second.release(); activeBlob = null }
    }

    private fun dockView() = EdgeDockView(this, EdgeSidebar.onRightEdge(this)).apply {
        val appearance = HomeAppearance.saved()
        iconColor = appearance.color(Theme.output_text_color)
        accentColor = appearance.color(Theme.terminal_border_color)
        items = dockItems(iconColor)
        contentDescription = getString(R.string.edge_sidebar_handle)
        setOnTouchListener(DockTouch())
    }

    /** With tabs under the sidebar the dock grows upward from them, so they stay where they are and in view. */
    private fun dockParams(view: EdgeDockView, width: Int, height: Int) = overlayParams(width, height).apply {
        gravity = Gravity.TOP or if (EdgeSidebar.onRightEdge(this@EdgeSidebarService)) Gravity.END else Gravity.START
        val below = tab?.sidebarBottom()
        view.growsUp = below != null
        y = if (below != null) ((tabParams?.y ?: 0) + below - height + view.bottomInset).toInt().coerceAtLeast(0) else centerY - height / 2
    }

    private fun stackView() = EdgeStackView(this, EdgeSidebar.onRightEdge(this)).apply {
        val appearance = HomeAppearance.saved()
        textColor = appearance.color(Theme.output_text_color)
        accentColor = appearance.color(Theme.terminal_border_color)
        typeface = ohi.andre.consolelauncher.tuils.Tuils.getTypeface(this@EdgeSidebarService)
        fun icon(res: Int) = ContextCompat.getDrawable(this@EdgeSidebarService, res)!!.mutate().apply { setTint(textColor) }
        icons = mapOf(null to icon(R.drawable.ic_edge_home), EdgePills.Kind.TIMER to icon(R.drawable.ic_edge_timer),
            EdgePills.Kind.STOPWATCH to icon(R.drawable.ic_edge_stopwatch), EdgePills.Kind.PODCAST to icon(R.drawable.podcast_24))
        contentDescription = getString(R.string.edge_sidebar_handle)
        setOnTouchListener(StackTouch())
    }

    private fun showStack() {
        removeDock()
        centerY = centerFor(prefs.getFloat(KEY_FRACTION, DEFAULT_FRACTION))
        val view = stackView()
        val pills = visiblePills()
        applyState(view, pills)
        if (pills.isEmpty() && !EdgeSidebar.enabled()) return
        val layout = overlayParams(view.windowWidth, view.windowHeight).apply {
            gravity = Gravity.TOP or if (view.rightEdge) Gravity.END else Gravity.START
            y = centerY - height / 2
        }
        runCatching { windows.addView(view, layout) }.onSuccess { tab = view; tabParams = layout }
    }

    private fun visiblePills() = if (EdgeSidebar.canDraw(this)) EdgePills.pills() else emptyList()

    private fun savedFocus() = prefs.getString(KEY_FOCUS, null)?.let { name -> EdgePills.Kind.entries.firstOrNull { it.name == name } }

    private fun applyState(view: EdgeStackView, pills: List<EdgePills.Pill>) {
        val sidebar = EdgeSidebar.enabled()
        val kinds = pills.map { it.kind }
        view.show(edgeStackSegments(sidebar, kinds, edgeStackFocus(sidebar, kinds, savedFocus())), pills)
    }

    /** Matches the stack to what is running; the window follows its size, keeping the stack's centre still. */
    private fun syncStack() {
        if (!running) return
        val pills = visiblePills()
        if (!EdgeSidebar.enabled() && pills.isEmpty()) { removeDock(); stopSelf(); return }
        val view = tab ?: return showStack()
        val layout = tabParams ?: return
        applyState(view, pills)
        if (layout.width != view.windowWidth || layout.height != view.windowHeight) {
            closeDock()
            layout.width = view.windowWidth
            layout.height = view.windowHeight
            layout.y = centerY - layout.height / 2
            runCatching { windows.updateViewLayout(view, layout) }
        }
    }

    private fun focus(kind: EdgePills.Kind?) {
        prefs.edit().putString(KEY_FOCUS, kind?.name).apply()
        builtLayout = prefs.all
        syncStack()
    }

    private fun screenHeight() = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds.height()
        else resources.displayMetrics.heightPixels
    private fun screenWidth() = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds.width()
        else resources.displayMetrics.widthPixels
    private val margin get() = (56 * resources.displayMetrics.density).toInt()

    /** The tab's centre for a position along the edge, 0 at the top and 1 at the bottom, like the timer pill. */
    private fun centerFor(fraction: Float) = margin + (fraction.coerceIn(0f, 1f) * (screenHeight() - 2 * margin)).toInt()

    /** The tab's rectangle on screen, where a drag lifts it from, and how far window y sits above screen y. */
    private fun screenRect(view: View, params: WindowManager.LayoutParams, width: Float, height: Float, right: Boolean): Pair<RectF, Int> {
        val at = IntArray(2).also(view::getLocationOnScreen)
        val centerY = at[1] + view.height / 2f
        val left = if (right) screenWidth() - width else 0f
        return RectF(left, centerY - height / 2, left + width, centerY + height / 2) to (at[1] - params.y)
    }

    /** Picks the stack up as a drop of tar; it settles on whichever edge is nearer where it is let go. */
    private fun liftStack(): EdgeBlob? {
        val view = tab ?: return null
        val layout = tabParams ?: return null
        val (rect, shift) = screenRect(view, layout, view.stackWidth, view.stackHeight, view.rightEdge)
        view.alpha = 0f
        return EdgeBlob(this, windows, rect, view.rightEdge, android.graphics.Color.BLACK, view.textColor, view.accentColor) { right, screenY ->
            if (tab !== view) return@EdgeBlob
            view.rightEdge = right
            layout.gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
            centerY = (screenY - shift - view.height / 2f + layout.height / 2f).toInt().coerceIn(margin, screenHeight() - margin)
            layout.y = centerY - layout.height / 2
            runCatching { windows.updateViewLayout(view, layout) }
            view.alpha = 1f
            prefs.edit().putString("side", if (right) "right" else "left")
                .putFloat(KEY_FRACTION, ((centerY - margin).toFloat() / (screenHeight() - 2 * margin)).coerceIn(0f, 1f)).apply()
        }.also { activeBlob = view to it }
    }

    private fun dockItems(tint: Int): List<EdgeDockView.Item> {
        fun icon(res: Int) = ContextCompat.getDrawable(this, res)!!.mutate().apply { setTint(tint) }
        val quick = EdgeSidebar.quickAppIcon(this) ?: icon(R.drawable.ic_toolbar_note_24)
        return listOf(
            EdgeDockView.Item(EdgeSidebar.Action.SEARCH, icon(R.drawable.ic_toolbar_search_24), getString(R.string.edge_sidebar_search)),
            EdgeDockView.Item(EdgeSidebar.Action.TERMINAL, icon(R.drawable.ic_toolbar_terminal_24), getString(R.string.edge_sidebar_terminal)),
            EdgeDockView.Item(EdgeSidebar.Action.QUICK_APP, quick, getString(R.string.edge_sidebar_quick_app))
        )
    }

    private fun openDock() {
        if (open || tab == null || !EdgeSidebar.enabled()) return
        val view = dockView()
        val layout = dockParams(view, view.windowWidth, view.windowHeight).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        // It starts as the resting tab's exact shape, so adding it over the tab shows no change until it grows.
        runCatching { windows.addView(view, layout) }.onSuccess {
            panel = view
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            view.animateTo(1f)
        }
    }

    private fun closeDock() {
        val view = panel ?: return
        panel = null
        view.highlighted = null
        view.animateTo(0f) { runCatching { windows.removeView(view) } }
    }

    /** The action under a screen point; a hold that opens the dock keeps reporting to the tab, so touches are mapped from raw coordinates. */
    private fun actionAt(rawX: Float, rawY: Float): EdgeSidebar.Action? {
        val view = panel ?: return null
        val at = IntArray(2).also(view::getLocationOnScreen)
        return view.actionAt(rawX - at[0], rawY - at[1])
    }

    /** On the open dock: tap an action, or anywhere else to close it. */
    private inner class DockTouch : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_OUTSIDE -> closeDock()
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> panel?.highlighted = actionAt(event.rawX, event.rawY)
                MotionEvent.ACTION_UP -> actionAt(event.rawX, event.rawY)?.let(::choose) ?: closeDock()
                MotionEvent.ACTION_CANCEL -> panel?.highlighted = null
            }
            return true
        }
    }

    /**
     * On the stack: tap the sidebar to open it, tap a small tab to focus it, and use the focused item's pause button
     * (hold it to end a timer or stopwatch). Holding anywhere else opens the sidebar and lets the finger slide onto an
     * action, whichever tab is in focus. Dragging moves the whole stack, even to the other edge.
     */
    private inner class StackTouch : View.OnTouchListener {
        private val slop = ViewConfiguration.get(this@EdgeSidebarService).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var hit: EdgeStackView.Hit? = null
        private var sliding = false
        private var dismissed = false
        private var ignored = false
        private var lifted: EdgeBlob? = null
        private val hold = Runnable {
            val button = hit as? EdgeStackView.Hit.Button
            if (button != null) {
                if (button.kind != EdgePills.Kind.PODCAST) {
                    dismissed = true
                    tab?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    EdgePills.dismiss(this@EdgeSidebarService, button.kind)
                }
            } else if (EdgeSidebar.enabled()) { sliding = true; openDock() }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; sliding = false; dismissed = false; lifted = null
                    ignored = open
                    if (ignored) { closeDock(); return true }
                    hit = (v as EdgeStackView).hitAt(event.y)
                    main.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (ignored) return true
                    if (!open && !dismissed && lifted == null && (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop)) {
                        main.removeCallbacks(hold); lifted = liftStack()
                    }
                    lifted?.move(event.rawX, event.rawY)
                    if (open) panel?.highlighted = actionAt(event.rawX, event.rawY)
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(hold)
                    if (ignored) return true
                    when {
                        lifted != null -> lifted?.release()
                        dismissed -> Unit
                        sliding -> actionAt(event.rawX, event.rawY)?.let(::choose) ?: run { panel?.highlighted = null }
                        else -> tap()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(hold)
                    lifted?.release()
                    panel?.highlighted = null
                }
            }
            return true
        }

        private fun tap() {
            when (val target = hit) {
                EdgeStackView.Hit.Sidebar -> openDock()
                is EdgeStackView.Hit.Tab -> focus(target.kind)
                is EdgeStackView.Hit.Button -> EdgePills.toggle(this@EdgeSidebarService, target.kind)
                is EdgeStackView.Hit.Body -> EdgePills.open(this@EdgeSidebarService, target.kind)
                null -> Unit
            }
        }
    }

    private fun choose(action: EdgeSidebar.Action) {
        panel?.highlighted = action
        closeDock()
        runCatching { EdgeSidebar.run(this, action) }
    }

    @Suppress("DEPRECATION")
    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun notification(): android.app.Notification {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.edge_sidebar_channel), NotificationManager.IMPORTANCE_MIN))
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val turnOff = PendingIntent.getService(this, 0,
            Intent(this, EdgeSidebarService::class.java).setAction(ACTION_TURN_OFF), immutable or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(if (EdgeSidebar.enabled()) R.string.edge_sidebar_notification else R.string.edge_pills_notification))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setShowWhen(false)
            .apply { if (EdgeSidebar.enabled()) addAction(0, getString(R.string.edge_sidebar_turn_off), turnOff) }
            .build()
    }

}

/** Whether the windows need building again: the tab is missing or unwanted, or the settings moved on since the last build. */
internal fun edgeSidebarNeedsRebuild(sidebarOn: Boolean, tabPresent: Boolean, builtRevision: Long?, revision: Long): Boolean =
    sidebarOn != tabPresent || builtRevision != revision
