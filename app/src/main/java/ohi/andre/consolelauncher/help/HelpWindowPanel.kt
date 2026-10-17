package ohi.andre.consolelauncher.help

import kotlin.math.roundToInt
import android.os.Bundle
import android.content.res.Configuration
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Geometry is relative to the safe, fold-aware host, never the physical display. */
internal class HelpWindowPanel(private val host: FrameLayout, private val content: View, state: Bundle?,
    private val statePrefix: String = "help",
    private val header: TextView = content.findViewById(R.id.offline_help_header),
    private val row: FrameLayout = (header.parent as View).parent as FrameLayout,
    private val maximizeLabel: Int = R.string.offline_help_maximize,
    private val windowedLabel: Int = R.string.offline_help_windowed,
    initiallyMaximized: Boolean = true,
    private val floatingInLandscape: Boolean = true,
    private val showHeaderReset: Boolean = true) {
    private val context = host.context
    private val preferences = context.getSharedPreferences("terminal_windows", android.content.Context.MODE_PRIVATE)
    private val panel = FrameLayout(context)
    private var maximized = preferences.getBoolean("${statePrefix}_maximized", initiallyMaximized).let {
        state?.getBoolean("${statePrefix}_maximized", it) ?: it
    }
    private fun restoredFraction(state: Bundle?, key: String, fallback: Float): Float {
        val saved = preferences.getFloat("${statePrefix}_$key", fallback)
        return (state?.getFloat("${statePrefix}_$key", saved) ?: saved).takeIf { it.isFinite() } ?: fallback
    }
    private var left = restoredFraction(state, "left", .05f)
    private var top = restoredFraction(state, "top", .05f)
    private var width = restoredFraction(state, "width", .9f)
    private var height = restoredFraction(state, "height", .9f)
    private val handles = mutableListOf<View>()
    private val controls = mutableListOf<Button>()
    private val toggle: Button
    private val reset: Button
    private val actions: LinearLayout
    private val expanded get() = maximized || landscapeOnlyExpanded
    private val landscapeOnlyExpanded get() = !floatingInLandscape &&
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private var bottomDock: View? = null
    private var bottomViewport: ScrollView? = null
    private val bottomLayoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> insetBottomDock() }
    private var minimumSize: Pair<Int, Int>? = null
    init {
        host.addView(panel)
        PanelBackdrop.install(host)
        panel.addView(content, FrameLayout.LayoutParams(-1, -1))
        actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            tag = "$statePrefix-header-actions"
        }
        fun addAction(action: View) {
            actions.addView(action, LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginStart = if (actions.childCount == 0) 0 else dp(10)
            })
        }
        while (row.childCount > 1) {
            val action = row.getChildAt(1)
            row.removeView(action)
            addAction(action)
        }
        toggle = control("", windowedLabel).apply {
            tag = "$statePrefix-window-toggle"
            setOnClickListener { maximized = !maximized; layout() }
        }
        toggle.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        addAction(toggle)
        reset = control("", R.string.offline_help_reset_window).apply {
            tag = "$statePrefix-window-reset"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setOnClickListener { resetWindow() }
        }
        if (showHeaderReset) addAction(reset)
        row.addView(actions, FrameLayout.LayoutParams(-2, dp(48), Gravity.END or Gravity.TOP))
        (header.parent as View).layoutParams = FrameLayout.LayoutParams(-1, dp(20)).apply {
            marginEnd = actions.childCount * dp(48) + (actions.childCount - 1) * dp(10)
        }
        drag(header, "move")
        listOf(Triple("corner", "◢", R.string.offline_help_resize_corner),
            Triple("top-left", "◤", R.string.offline_help_resize_corner),
            Triple("top-right", "◥", R.string.offline_help_resize_corner),
            Triple("bottom-left", "◣", R.string.offline_help_resize_corner)).forEach { (kind, glyph, label) ->
            val handle = control(glyph, label, resizeCorner = true).apply {
                tag = "$statePrefix-resize-$kind"
                setOnClickListener {
                    // A tap grows the selected axes; long press shrinks them for keyboard/TalkBack users.
                    resize(kind, if (kind.endsWith("left")) -dp(32) else dp(32),
                        if (kind.startsWith("top")) -dp(32) else dp(32))
                }
                setOnLongClickListener {
                    resize(kind, if (kind.endsWith("left")) dp(32) else -dp(32),
                        if (kind.startsWith("top")) dp(32) else -dp(32)); true
                }
            }
            val params = when (kind) {
                "top-left" -> FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT or Gravity.TOP)
                "top-right" -> FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT or Gravity.TOP)
                "bottom-left" -> FrameLayout.LayoutParams(dp(48), dp(48), Gravity.LEFT or Gravity.BOTTOM)
                else -> FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT or Gravity.BOTTOM)
            }
            panel.addView(handle, params); handles.add(handle); drag(handle, kind)
        }
        host.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) layout()
        }
        layout()
    }
    private fun dp(value: Int) = Tuils.dpToPx(context, value)
    fun setMinimumSize(width: Int, height: Int) {
        val size = width to height
        if (minimumSize == size) return
        minimumSize = size
        layout()
    }
    private fun minimumWidth() = minimumSize?.first?.coerceAtMost(host.width)
        ?: minOf(dp(320), (host.width * .9f).roundToInt())
    private fun minimumHeight() = minimumSize?.second?.coerceAtMost(host.height)
        ?: minOf(dp(360), (host.height * .9f).roundToInt())
    fun reserveBottomCorners(dock: View) {
        bottomDock = dock
        dock.tag = "$statePrefix-bottom-dock"
        var ancestor = dock.parent as? View
        var viewport: ScrollView? = null
        while (ancestor != null && ancestor !== panel) {
            if (ancestor is ScrollView) { viewport = ancestor; break }
            ancestor = ancestor.parent as? View
        }
        if (bottomViewport !== viewport) {
            bottomViewport?.removeOnLayoutChangeListener(bottomLayoutListener)
            bottomViewport = viewport
            viewport?.addOnLayoutChangeListener(bottomLayoutListener)
        }
        layout()
    }
    private fun insetBottomDock() {
        val dock = bottomDock ?: return
        val viewport = bottomViewport
        // A short scrolling keypad must not carry its upper rows under the resize handles either.
        val protected = if (viewport != null && viewport.height > 0 && viewport.getChildAt(0).height > viewport.height) viewport else dock
        for (view in listOfNotNull(dock, viewport)) {
            var start = 0; var end = 0
            var ancestor = view.parent as? View
            while (ancestor != null && ancestor !== panel) {
                start += ancestor.paddingStart; end += ancestor.paddingEnd
                ancestor = ancestor.parent as? View
            }
            val params = view.layoutParams as ViewGroup.MarginLayoutParams
            val insetStart = if (expanded || view !== protected) 0 else (dp(48) - start).coerceAtLeast(0)
            val insetEnd = if (expanded || view !== protected) 0 else (dp(48) - end).coerceAtLeast(0)
            if (params.marginStart != insetStart || params.marginEnd != insetEnd) {
                params.marginStart = insetStart; params.marginEnd = insetEnd
                view.layoutParams = params
            }
        }
    }
    fun refreshTheme() {
        for ((index, control) in controls.withIndex()) {
            control.setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
            control.typeface = Tuils.getTypeface(context)
            control.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
            control.includeFontPadding = false
            control.background = if (index < 2) android.graphics.drawable.InsetDrawable(
                TerminalBorderRuntime.tabDrawable(context,
                    ThemeColorResolver.color(Theme.workspace_header_background_color),
                    ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS), 0, 0, 0, dp(28)) else null
            control.setPadding(0, 0, 0, 0)
            if (index < 2) ohi.andre.consolelauncher.tuils.TerminalPaneIcon.apply(control,
                if (index == 0) toggleIcon() else R.drawable.ic_toolbar_refresh_24)
        }
    }
    private fun contentHit(view: View, x: Int, y: Int): Boolean {
        if (!view.isShown || !view.isEnabled) return false
        val bounds = android.graphics.Rect()
        if (!view.getGlobalVisibleRect(bounds)) return false
        val origin = IntArray(2)
        view.rootView.getLocationOnScreen(origin)
        bounds.offset(origin[0], origin[1])
        if (!bounds.contains(x, y)) return false
        if (view.isClickable || view.isLongClickable || view is android.widget.EditText) return true
        return view is android.view.ViewGroup && (0 until view.childCount).any {
            contentHit(view.getChildAt(it), x, y)
        }
    }
    private fun control(glyph: String, label: Int, resizeCorner: Boolean = false) = object : androidx.appcompat.widget.AppCompatButton(context) {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            // Content owns its complete measured target, even when a corner is drawn above it.
            if (resizeCorner && event.actionMasked == MotionEvent.ACTION_DOWN &&
                contentHit(content, event.rawX.toInt(), event.rawY.toInt())) return false
            return super.dispatchTouchEvent(event)
        }
    }.apply {
        text = glyph; contentDescription = context.getString(label); isFocusable = true
        androidx.appcompat.widget.TooltipCompat.setTooltipText(this, contentDescription)
        isAllCaps = false
        typeface = Tuils.getTypeface(context)
        textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0; setPadding(0, 0, 0, 0)
        setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
        background = null
        controls.add(this)
    }
    private fun resize(kind: String, dx: Int, dy: Int) {
        if (host.width == 0 || host.height == 0) return
        val x = (left * host.width).roundToInt()
        val y = (top * host.height).roundToInt()
        val right = x + (width * host.width).roundToInt()
        val bottom = y + (height * host.height).roundToInt()
        val minWidth = minimumWidth()
        val minHeight = minimumHeight()
        var newLeft = x; var newTop = y; var newRight = right; var newBottom = bottom
        if (kind != "height") {
            if (kind.endsWith("left")) newLeft = (x + dx).coerceIn(0, right - minWidth)
            else newRight = (right + dx).coerceIn(x + minWidth, host.width)
        }
        if (kind != "width") {
            if (kind.startsWith("top")) newTop = (y + dy).coerceIn(0, bottom - minHeight)
            else newBottom = (bottom + dy).coerceIn(y + minHeight, host.height)
        }
        left = newLeft.toFloat() / host.width; top = newTop.toFloat() / host.height
        width = (newRight - newLeft).toFloat() / host.width
        height = (newBottom - newTop).toFloat() / host.height
        layout()
    }
    private fun drag(view: View, kind: String) {
        var x = 0f; var y = 0f; var moved = false
        view.setOnTouchListener { target, event ->
            if (expanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { x = event.rawX; y = event.rawY; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - x; val dy = event.rawY - y
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(2)) moved = true
                    if (kind == "move") { left += dx / host.width; top += dy / host.height; layout() }
                    else resize(kind, dx.toInt(), dy.toInt())
                    x = event.rawX; y = event.rawY; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (event.eventTime - event.downTime >= android.view.ViewConfiguration.getLongPressTimeout() && kind != "move") target.performLongClick()
                        else target.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    fun resetWindow() {
        left = .05f; top = .05f; width = .9f; height = .9f
        maximized = false; layout()
    }
    private fun layout() {
        insetBottomDock()
        handles.forEach { it.visibility = if (expanded) View.GONE else View.VISIBLE }
        toggle.visibility = if (landscapeOnlyExpanded) View.GONE else View.VISIBLE
        reset.visibility = toggle.visibility
        var visibleActions = 0
        for (index in 0 until actions.childCount) {
            val action = actions.getChildAt(index)
            (action.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = if (visibleActions == 0) 0 else dp(10)
                action.layoutParams = this
            }
            if (action.visibility != View.GONE) visibleActions++
        }
        (header.parent as View).layoutParams = FrameLayout.LayoutParams(-1, dp(20)).apply {
            marginEnd = visibleActions * dp(48) + (visibleActions - 1).coerceAtLeast(0) * dp(10)
        }
        toggle.contentDescription = context.getString(if (expanded) windowedLabel else maximizeLabel)
        ohi.andre.consolelauncher.tuils.TerminalPaneIcon.apply(toggle, toggleIcon())
        (content.layoutParams as FrameLayout.LayoutParams).apply {
            rightMargin = 0
            bottomMargin = 0
            content.layoutParams = this
        }
        if (host.width == 0 || host.height == 0) return
        // Expansion and keyboard insets must not overwrite the remembered floating rectangle.
        if (!expanded) {
            width = width.coerceIn(minimumWidth().toFloat() / host.width, 1f)
            height = height.coerceIn(minimumHeight().toFloat() / host.height, 1f)
            left = left.coerceIn(0f, 1f - width); top = top.coerceIn(0f, 1f - height)
        }
        panel.layoutParams = FrameLayout.LayoutParams(if (expanded) -1 else (host.width * width).roundToInt(),
            if (expanded) -1 else (host.height * height).roundToInt()).apply {
            leftMargin = if (expanded) 0 else (host.width * left).roundToInt()
            topMargin = if (expanded) 0 else (host.height * top).roundToInt()
        }
        panel.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }
    fun save(state: Bundle) {
        state.putBoolean("${statePrefix}_maximized", maximized); state.putFloat("${statePrefix}_left", left); state.putFloat("${statePrefix}_top", top)
        state.putFloat("${statePrefix}_width", width); state.putFloat("${statePrefix}_height", height)
    }
    fun persist() {
        preferences.edit().putBoolean("${statePrefix}_maximized", maximized)
            .putFloat("${statePrefix}_left", left).putFloat("${statePrefix}_top", top)
            .putFloat("${statePrefix}_width", width).putFloat("${statePrefix}_height", height).apply()
    }
    private fun toggleIcon() = if (expanded) R.drawable.pip_24 else R.drawable.open_in_full_24
}
