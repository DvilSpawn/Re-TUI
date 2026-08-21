package ohi.andre.consolelauncher.managers.onboarding

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleScreen
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

object StartupMenuManager {
    private const val PREFS = "retui_startup"
    private const val KEY_ACTIVE = "active"
    private const val KEY_COMPLETE = "complete"
    private const val KEY_DRY_RUN = "dry_run"
    private const val KEY_MENU_VERSION = "menu_version"
    private const val KEY_VISUAL = "visual"
    private const val CHOICE_PREFIX = "choice_"
    private const val MENU_VERSION = 2

    const val APP_DRAWER = "app_drawer"
    const val MODULE_DOCK = "module_dock"
    const val WIDGETS = "widgets"
    const val NOTIFICATIONS = "notifications"
    const val STATUS = "status"
    const val LOCK = "lock"
    const val TERMUX = "termux"
    const val TASKER = "tasker"
    const val SOUNDS = "sounds"

    private val ids = listOf(APP_DRAWER, MODULE_DOCK, WIDGETS, NOTIFICATIONS, STATUS, LOCK, TERMUX, TASKER, SOUNDS)

    data class Toggle(val id: String, val label: String, val description: String, val enabled: Boolean)
    data class ActionResult(val message: String, val reload: Boolean = false)

    fun maybeOpen(context: Context): Boolean {
        if (!isActive(context)) {
            if (!isFirstInstallPending(context)) return false
            start(context, dryRun = false)
        } else if (prefs(context).getInt(KEY_MENU_VERSION, 0) != MENU_VERSION) {
            seedClassic(context)
        }
        open(context)
        return true
    }

    fun startTest(context: Context): String {
        start(context, dryRun = true)
        open(context)
        return "Opening startup menu in dry-run mode."
    }

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)
    fun isDryRun(context: Context): Boolean = prefs(context).getBoolean(KEY_DRY_RUN, false)
    fun visual(context: Context): String = prefs(context).getString(KEY_VISUAL, "classic") ?: "classic"

    fun isFirstInstallPending(context: Context): Boolean {
        val preferences = prefs(context)
        if (preferences.getBoolean(KEY_COMPLETE, false)) return false
        if (preferences.getBoolean(KEY_ACTIVE, false) && !preferences.getBoolean(KEY_DRY_RUN, false)) return true

        val existingInstall = try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime < info.lastUpdateTime
        } catch (_: Exception) {
            false
        }
        if (existingInstall) {
            preferences.edit().putBoolean(KEY_COMPLETE, true).apply()
            return false
        }
        return true
    }

    fun toggles(context: Context): List<Toggle> = listOf(
        toggle(context, APP_DRAWER, "App drawer", "Toolbar shortcut for browsing installed apps."),
        toggle(context, MODULE_DOCK, "Module dock", "Quick-access row for launcher modules."),
        toggle(context, WIDGETS, "Android widgets", "Toolbar shortcut for the Android widget drawer."),
        toggle(context, NOTIFICATIONS, "Notifications and media", "Read notifications and external media sessions."),
        toggle(context, STATUS, "Status extras", "Weather and unlock-count labels."),
        toggle(context, LOCK, "Double-tap lock", "Lock the phone with a double tap."),
        toggle(
            context,
            TERMUX,
            "Termux and tmux",
            if (TermuxBridgeManager.isTermuxInstalled(context)) "Termux detected; show the tmux workspace shortcut."
            else "Termux not detected; keep the tmux shortcut available for later."
        ),
        toggle(
            context,
            TASKER,
            "Tasker",
            if (TaskerIntegrationManager.isTaskerInstalled(context)) "Tasker detected; allow Re:T-UI actions and tasks."
            else "Tasker not detected; enable the integration for later."
        ),
        toggle(context, SOUNDS, "Launcher sounds", "Boot, action, timer, and reminder sounds.")
    )

    fun toggle(context: Context, id: String) {
        if (id !in ids) return
        val preferences = prefs(context)
        preferences.edit().putBoolean(choiceKey(id), !preferences.getBoolean(choiceKey(id), false)).apply()
    }

    fun setVisual(context: Context, value: String) {
        if (value in setOf("classic", "cyberdeck", "crt")) prefs(context).edit().putString(KEY_VISUAL, value).apply()
    }

    fun resetClassic(context: Context) {
        seedClassic(context)
    }

    fun finish(context: Context): ActionResult {
        if (isDryRun(context)) {
            prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
            return ActionResult("Startup preview complete. No settings were changed.")
        }

        applyChoices(context)
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_COMPLETE, true).apply()
        val followUp = mutableListOf<String>()
        if (enabled(context, TERMUX)) followUp.add("Termux: run tbridge -setup, then tbridge -status.")
        if (enabled(context, TASKER)) followUp.add("Tasker: allow external access in Tasker if prompted.")
        return ActionResult(
            buildString {
                append("Startup setup applied.")
                if (followUp.isNotEmpty()) append("\n").append(followUp.joinToString("\n"))
            },
            reload = true
        )
    }

    fun cancel(context: Context): ActionResult {
        val dryRun = isDryRun(context)
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
        return ActionResult(
            if (dryRun) "Startup preview closed. No settings were changed."
            else "Startup setup postponed until the next launch."
        )
    }

    internal fun visualFlags(value: String): Pair<Boolean, Boolean> = when (value) {
        "cyberdeck" -> true to false
        "crt" -> true to true
        else -> false to false
    }

    private fun start(context: Context, dryRun: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putBoolean(KEY_DRY_RUN, dryRun)
            .apply()
        seedClassic(context)
    }

    private fun seedClassic(context: Context) {
        val editor = prefs(context).edit()
        ids.forEach { editor.putBoolean(choiceKey(it), false) }
        editor.putBoolean(choiceKey(STATUS), true)
        editor.putBoolean(choiceKey(LOCK), true)
        editor.putString(KEY_VISUAL, "classic")
        editor.putInt(KEY_MENU_VERSION, MENU_VERSION)
        editor.apply()
    }

    private fun open(context: Context) {
        context.startActivity(Intent(context, StartupMenuActivity::class.java).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun applyChoices(context: Context) {
        LauncherSettings.set(context, Behavior.swipe_up_apps_drawer, enabled(context, APP_DRAWER).toString())
        LauncherSettings.set(context, Behavior.show_module_dock, enabled(context, MODULE_DOCK).toString())
        LauncherSettings.set(context, Behavior.show_android_widget_drawer_button, enabled(context, WIDGETS).toString())
        val notifications = enabled(context, NOTIFICATIONS).toString()
        LauncherSettings.set(context, Notifications.show_notifications, notifications)
        LauncherSettings.set(context, Notifications.terminal_notifications, notifications)
        val status = enabled(context, STATUS).toString()
        LauncherSettings.set(context, Ui.show_weather, status)
        LauncherSettings.set(context, Ui.show_unlock_counter, status)
        LauncherSettings.set(context, Behavior.double_tap_lock, enabled(context, LOCK).toString())
        LauncherSettings.set(context, Behavior.show_tmux_workspace_button, enabled(context, TERMUX).toString())
        TaskerIntegrationManager.setEnabled(context, enabled(context, TASKER))
        LauncherSettings.set(context, Behavior.launcher_sounds, enabled(context, SOUNDS).toString())
        val flags = visualFlags(visual(context))
        LauncherSettings.set(context, Behavior.enable_cyberdeck_mode, flags.first.toString())
        LauncherSettings.set(context, Behavior.enable_crt_filter, flags.second.toString())
    }

    private fun toggle(context: Context, id: String, label: String, description: String): Toggle =
        Toggle(id, label, description, enabled(context, id))

    private fun enabled(context: Context, id: String): Boolean = prefs(context).getBoolean(choiceKey(id), false)
    private fun choiceKey(id: String): String = CHOICE_PREFIX + id
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class StartupMenuActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        applyFullscreen(this)
        if (!StartupMenuManager.isActive(this)) {
            finish()
            return
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen(this)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        close(StartupMenuManager.cancel(this))
    }

    private fun render() {
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val screen = FrameLayout(this)
        styleScreen(this, screen)
        screen.fitsSystemWindows = true
        val host = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val shell = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        host.addView(shell, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            setMargins(dp(this@StartupMenuActivity, 14f), dp(this@StartupMenuActivity, 22f), dp(this@StartupMenuActivity, 14f), dp(this@StartupMenuActivity, 14f))
        })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@StartupMenuActivity, 14f), dp(this@StartupMenuActivity, 44f), dp(this@StartupMenuActivity, 14f), dp(this@StartupMenuActivity, 12f))
        }
        stylePanel(this, panel)
        shell.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(this@StartupMenuActivity, 11f)
        })

        val header = TextView(this).apply {
            text = if (StartupMenuManager.isDryRun(this@StartupMenuActivity)) "STARTUP · DRY RUN" else "STARTUP"
        }
        styleHeader(this, header)
        shell.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(this@StartupMenuActivity, 30f)
        })

        panel.addView(terminalText("Choose what you want", 21f).apply { setTypeface(typeface, Typeface.BOLD) }, blockParams(6f))
        panel.addView(terminalText(
            if (StartupMenuManager.isDryRun(this)) "Preview only — changing switches here will not alter your launcher."
            else "Review the switches, choose a visual style, then apply once.",
            13f
        ), blockParams(10f))

        val defaults = button("CLASSIC DEFAULTS", false)
        defaults.setOnClickListener { StartupMenuManager.resetClassic(this); render() }
        panel.addView(defaults, blockParams(10f))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        StartupMenuManager.toggles(this).forEach { list.addView(toggleRow(it), blockParams(8f)) }
        list.addView(terminalText("VISUAL STYLE", 13f).apply { setTypeface(typeface, Typeface.BOLD) }, blockParams(6f))
        list.addView(visualStyleRow(), blockParams(4f))
        panel.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(this@StartupMenuActivity, 10f), 0, 0)
        }
        val later = button(if (StartupMenuManager.isDryRun(this)) "END PREVIEW" else "LATER", false)
        later.setOnClickListener { close(StartupMenuManager.cancel(this)) }
        controls.addView(later, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(View(this), LinearLayout.LayoutParams(dp(this, 10f), 1))
        val apply = button(if (StartupMenuManager.isDryRun(this)) "FINISH PREVIEW" else "APPLY", true)
        apply.setOnClickListener { close(StartupMenuManager.finish(this)) }
        controls.addView(apply, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(controls)
        return screen
    }

    private fun toggleRow(toggle: StartupMenuManager.Toggle): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this@StartupMenuActivity, 12f), dp(this@StartupMenuActivity, 9f), dp(this@StartupMenuActivity, 10f), dp(this@StartupMenuActivity, 9f))
            contentDescription = toggle.label + ". " + toggle.description + ". " + if (toggle.enabled) "On" else "Off"
            isClickable = true
            isFocusable = true
            setOnClickListener { StartupMenuManager.toggle(this@StartupMenuActivity, toggle.id); render() }
        }
        stylePanel(this, row)
        row.addView(terminalText(toggle.label + "\n" + toggle.description, 13f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val switch = TextView(this)
        styleToggle(this, switch, toggle.enabled)
        row.addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(this@StartupMenuActivity, 10f)
        })
        return row
    }

    private fun visualStyleRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("classic", "cyberdeck", "crt").forEachIndexed { index, value ->
            val choice = button(value.uppercase(), StartupMenuManager.visual(this) == value)
            choice.setOnClickListener { StartupMenuManager.setVisual(this, value); render() }
            row.addView(choice, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(this@StartupMenuActivity, 8f)
            })
        }
        return row
    }

    private fun close(result: StartupMenuManager.ActionResult) {
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        finish()
        if (result.reload) {
            startActivity(Intent(this, LauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(Reloadable.MESSAGE, result.message)
            })
        }
    }

    private fun terminalText(value: String, size: Float): TextView = TextView(this).apply {
        text = value
        setTextColor(textColor())
        typeface = Tuils.getTypeface(this@StartupMenuActivity)
        textSize = size
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun button(label: String, primary: Boolean): TextView = TextView(this).apply {
        text = label
        styleButton(this@StartupMenuActivity, this, primary)
    }

    private fun blockParams(bottom: Float): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(this@StartupMenuActivity, bottom) }
}
