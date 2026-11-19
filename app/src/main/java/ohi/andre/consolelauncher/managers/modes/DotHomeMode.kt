package ohi.andre.consolelauncher.managers.modes

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import ohi.andre.consolelauncher.sidebar.GlobalSearchActivity
import ohi.andre.consolelauncher.wallpaper.pixel.DotHomeView
import java.util.Locale
import ohi.andre.consolelauncher.presentation.DualScreen

/** Dot mode: the dot covers the search layout until it is double-tapped. */
internal class DotHomeMode(private val host: HomeModeHost) : HomeMode {
    private var dot: DotHomeView? = null
    private var commandAt = NEVER
    private var swapKey: android.widget.TextView? = null
    private var layoutNow: DualScreen.Layout = DualScreen.Layout.Phone
    private var arrangedAs: Any? = null
    private val onScreensChanged: () -> Unit = { host.onMain { arrange() } }
    private val onRootSize = View.OnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ ->
        if (r - l != or_ - ol) host.onMain { arrange() }
    }

    override val usesSearchLayout get() = true
    override val managesOwnKeyboard get() = true

    override fun install() {
        val root = host.root ?: return
        val view = dot ?: DotHomeView(host.context).also(::wire)
        if (view.parent == null) root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.loadSettings()
        view.bringToFront()
        root.removeOnLayoutChangeListener(onRootSize)
        root.addOnLayoutChangeListener(onRootSize)
        DualScreen.listen(onScreensChanged)
        arrangedAs = null
        arrange()
        show()
    }

    override fun dispose() {
        DualScreen.unlisten(onScreensChanged)
        host.root?.removeOnLayoutChangeListener(onRootSize)
        place(DualScreen.Layout.Phone)
    }

    /**
     * One screen: the dot covers the search layout. Two screens: the dot takes one and the search layout, always
     * up, the other; with `duo left`/`duo right` both keep to that screen as on a phone. The swap key flips sides.
     */
    private fun arrange() {
        val root = host.root ?: return
        val layout = DualScreen.layout(root)
        val key = listOf(layout, root.width)
        if (key == arrangedAs) return
        arrangedAs = key
        place(layout)
    }

    private fun place(layout: DualScreen.Layout) {
        val root = host.root ?: return
        val view = dot ?: return
        layoutNow = layout
        view.showHint = layout !is DualScreen.Layout.Both
        val width = root.width
        (swapKey?.parent as? android.view.ViewGroup)?.removeView(swapKey)
        DualScreen.rootPadLeft = 0
        DualScreen.rootPadRight = 0
        if (view.parent !== root) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
        }
        val dotSide: String
        // The root is already padded by the cutout and system bars at its sides; panes are measured inside them.
        val (insetLeft, insetRight) = DualScreen.sideInsets(root)
        when (layout) {
            DualScreen.Layout.Phone -> { host.applyDisplayMargins(); show(); return }
            is DualScreen.Layout.OneSide -> {
                dotSide = layout.side
                if (layout.side == DualScreen.LEFT) DualScreen.rootPadRight = (width - insetRight - layout.split.start).coerceAtLeast(0)
                else DualScreen.rootPadLeft = (layout.split.end - insetLeft).coerceAtLeast(0)
            }
            is DualScreen.Layout.Both -> {
                dotSide = DualScreen.other(layout.inputSide)
                if (layout.inputSide == DualScreen.RIGHT) DualScreen.rootPadLeft = (layout.split.end - insetLeft).coerceAtLeast(0)
                else DualScreen.rootPadRight = (width - insetRight - layout.split.start).coerceAtLeast(0)
                // The dot leaves the launcher layout for its own screen, beside it.
                val content = root.parent as? FrameLayout
                if (content != null) {
                    root.removeView(view)
                    val left = dotSide == DualScreen.LEFT
                    val pane = if (left) layout.split.start - insetLeft else width - insetRight - layout.split.end
                    content.addView(view, FrameLayout.LayoutParams(pane.coerceAtLeast(0), -1,
                        android.view.Gravity.TOP or if (left) android.view.Gravity.START else android.view.Gravity.END).apply {
                        if (left) leftMargin = insetLeft else rightMargin = insetRight
                    })
                }
            }
        }
        val key = swapKey ?: android.widget.TextView(host.context).apply {
            gravity = android.view.Gravity.CENTER
            textSize = 16f
            typeface = runCatching { ohi.andre.consolelauncher.tuils.Tuils.getTypeface(host.context) }.getOrNull()
            setOnClickListener { DualScreen.swap(this, layoutNow) }
            swapKey = this
        }
        key.setTextColor(view.inkColor)
        // The key points where the search (or, with duo on, everything) will go.
        val towardsLeft = when (layout) { is DualScreen.Layout.Both -> layout.inputSide == DualScreen.RIGHT; else -> dotSide == DualScreen.RIGHT }
        key.text = if (towardsLeft) "<<" else ">>"
        key.contentDescription = key.text
        val margin = ohi.andre.consolelauncher.tuils.Tuils.dpToPx(host.context, 18)
        val size = ohi.andre.consolelauncher.tuils.Tuils.dpToPx(host.context, 48)
        (view.parent as? FrameLayout)?.addView(key, FrameLayout.LayoutParams(size, size,
            android.view.Gravity.BOTTOM or if (dotSide == DualScreen.LEFT) android.view.Gravity.START else android.view.Gravity.END).apply {
            setMargins(margin, margin, margin, margin)
        })
        host.applyDisplayMargins()
        show()
    }

    private fun wire(view: DotHomeView) {
        dot = view
        view.rings = {
            HomeApps.picks(host.pack.appsManager).map { app ->
                DotHomeView.Ring(app.publicLabel?.lowercase(Locale.getDefault()).orEmpty()) { host.pack.appsManager.launch(host.context, app) }
            }
        }
        view.temperature = { host.weatherDetails?.substringBefore(" · ")?.trim()?.takeIf { it.contains('°') || it.endsWith(" K") } }
        // Search floats over the dot, as it does from the sidebar, so the screen stays black behind it.
        view.onSearch = search@{
            if (layoutNow is DualScreen.Layout.Both) { host.activateInput(); host.raiseKeyboard(); return@search }
            runCatching { host.context.startActivity(Intent(host.context, GlobalSearchActivity::class.java)) }
                .onFailure { hide(keyboard = true) }
        }
    }

    private fun show() {
        val view = dot ?: return
        swapKey?.bringToFront()
        host.closeSearchModules()
        host.hideKeyboard()
        commandAt = NEVER
        view.translationZ = 0f
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    private fun hide(keyboard: Boolean) {
        val view = dot ?: return
        view.animate().alpha(0f).setDuration(150L).withEndAction { view.visibility = View.GONE }.start()
        if (keyboard) host.raiseKeyboard()
    }

    override fun onStart() = show()

    override fun handleBack(): Boolean {
        val view = dot ?: return false
        if (view.visibility != View.VISIBLE || view.translationZ != 0f) show()
        return true
    }

    override fun onHome() = show()

    override fun beforeSearchCommand(command: String) {
        commandAt = SystemClock.elapsedRealtime()
    }

    // A command's answer lands in the launcher's output tray, so the dot steps behind it and stays on screen.
    // Back or Home brings it forward again. Output nobody just asked for leaves the dot alone.
    override fun onOutput(text: CharSequence?) {
        if (commandAt == NEVER || SystemClock.elapsedRealtime() - commandAt > ANSWER_WINDOW_MS) return
        host.onMain { dot?.translationZ = BEHIND_OUTPUT }
    }

    private companion object {
        const val NEVER = -1L
        const val ANSWER_WINDOW_MS = 10_000L
        const val BEHIND_OUTPUT = -1f
    }
}
