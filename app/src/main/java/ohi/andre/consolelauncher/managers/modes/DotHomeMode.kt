package ohi.andre.consolelauncher.managers.modes

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import ohi.andre.consolelauncher.sidebar.GlobalSearchActivity
import ohi.andre.consolelauncher.wallpaper.pixel.DotHomeView
import java.util.Locale

/** Dot mode: the dot covers the search layout until it is double-tapped. */
internal class DotHomeMode(private val host: HomeModeHost) : HomeMode {
    private var dot: DotHomeView? = null
    private var commandAt = NEVER

    override val usesSearchLayout get() = true
    override val managesOwnKeyboard get() = true

    override fun install() {
        val root = host.root ?: return
        val view = dot ?: DotHomeView(host.context).also(::wire)
        if (view.parent == null) root.addView(view, FrameLayout.LayoutParams(-1, -1))
        view.loadSettings()
        view.bringToFront()
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
        view.onSearch = {
            runCatching { host.context.startActivity(Intent(host.context, GlobalSearchActivity::class.java)) }
                .onFailure { hide(keyboard = true) }
        }
    }

    private fun show() {
        val view = dot ?: return
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
