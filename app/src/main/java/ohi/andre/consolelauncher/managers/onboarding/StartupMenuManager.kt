package ohi.andre.consolelauncher.managers.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import ohi.andre.consolelauncher.managers.xml.AutoColorManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleChoice
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleScreen
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleSlider
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime

object StartupMenuManager {
    const val PREFS = "retui_startup"
    internal const val PREVIEW_PREFS = "retui_startup_preview"
    internal const val EXTRA_PREVIEW = "startup_preview"
    private const val KEY_ACTIVE = "active"
    private const val KEY_COMPLETE = "complete"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_PAGE = "page"
    private const val KEY_BUILDING = "building"
    private const val KEY_SCROLL = "config_scroll"
    private const val KEY_MENU_VERSION = "menu_version"
    private const val KEY_LEVEL = "level"
    private const val KEY_AUTO_THEME = "auto_theme"
    private const val KEY_BORDER = "border"
    private const val KEY_STROKE = "border_stroke"
    private const val KEY_GAP = "border_gap"
    private const val KEY_CYBERDECK = "cyberdeck"
    private const val KEY_CRT = "crt"
    private const val KEY_DEFAULT_LAUNCHER = "default_launcher"
    private const val CHOICE_PREFIX = "choice_"
    private const val MENU_VERSION = 4

    const val WELCOME = 0
    const val CONFIGURE = 1
    const val THANKS = 2

    const val BASIC = "basic"
    const val ADVANCED = "advanced"
    const val BORDER_DASHED = "dashed"
    const val BORDER_SOLID = "solid"
    const val BORDER_NONE = "none"

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

    // Run before default XML files are created: existing configuration is migration evidence,
    // an APK update timestamp is not evidence that someone has used the launcher.
    fun initialize(context: Context, existingConfiguration: Boolean) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_INITIALIZED, false)) return
        val legacySetup = preferences.getInt(KEY_MENU_VERSION, 0) > 0 &&
            !preferences.getBoolean("dry_run", false)
        val complete = preferences.getBoolean(KEY_COMPLETE, false) || (existingConfiguration && !legacySetup)
        preferences.edit().putBoolean(KEY_INITIALIZED, true).putBoolean(KEY_COMPLETE, complete)
            .remove("dry_run").commit()
    }

    fun maybeOpen(context: Context): Boolean {
        if (!isFirstInstallPending(context)) return false
        prepare(prefs(context))
        open(context)
        return true
    }

    fun startTest(context: Context): String {
        val preferences = context.getSharedPreferences(PREVIEW_PREFS, Context.MODE_PRIVATE)
        preferences.edit().clear().apply()
        prepare(preferences)
        open(context, preview = true)
        return context.getString(R.string.startup_opening_startup_menu_in_dry_run_mode)
    }

    fun isActive(context: Context): Boolean = prefs(context).getBoolean(KEY_ACTIVE, false)
    fun isDryRun(context: Context): Boolean = (context as? Activity)?.intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
    fun page(context: Context): Int = prefs(context).getInt(KEY_PAGE, WELCOME).coerceIn(WELCOME, THANKS)
    fun setPage(context: Context, page: Int) {
        prefs(context).edit().putInt(KEY_PAGE, page.coerceIn(WELCOME, THANKS)).apply()
    }
    fun isBuilding(context: Context): Boolean = prefs(context).getBoolean(KEY_BUILDING, false)
    fun setBuilding(context: Context, building: Boolean) {
        prefs(context).edit().putBoolean(KEY_BUILDING, building).apply()
    }
    fun configScroll(context: Context): Int = prefs(context).getInt(KEY_SCROLL, 0).coerceAtLeast(0)
    fun setConfigScroll(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_SCROLL, value.coerceAtLeast(0)).apply()
    }
    fun level(context: Context): String = prefs(context).getString(KEY_LEVEL, BASIC) ?: BASIC
    fun autoTheme(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_THEME, true)
    fun border(context: Context): String = prefs(context).getString(KEY_BORDER, BORDER_SOLID) ?: BORDER_SOLID
    fun borderStroke(context: Context): Float = validStroke(prefs(context).getFloat(KEY_STROKE, 1.5f))
    fun borderGap(context: Context): Int = prefs(context).getInt(KEY_GAP, 4).coerceIn(1, 24)
    fun setBorderStroke(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_STROKE, validStroke(value)).apply()
    }
    fun setBorderGap(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_GAP, value.coerceIn(1, 24)).apply()
    }
    private fun validStroke(value: Float): Float = if (value.isFinite()) value.coerceIn(0.5f, 8f) else 1.5f
    fun cyberdeck(context: Context): Boolean = prefs(context).getBoolean(KEY_CYBERDECK, false)
    fun crt(context: Context): Boolean = prefs(context).getBoolean(KEY_CRT, false)
    fun defaultLauncher(context: Context): Boolean = prefs(context).getBoolean(KEY_DEFAULT_LAUNCHER, false)
    fun setDefaultLauncher(context: Context, selected: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEFAULT_LAUNCHER, selected).apply()
    }

    internal fun defaultLauncherRequest(context: Context): Intent? {
        if (isDryRun(context) || !defaultLauncher(context)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return if (roles.isRoleHeld(RoleManager.ROLE_HOME)) null
                    else roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return if (Tuils.isMyLauncherDefault(context.packageManager)) null else Intent(Settings.ACTION_HOME_SETTINGS)
    }

    private var closingPreparation: java.lang.ref.WeakReference<View>? = null
    private const val KEY_HOME_PENDING = "home_request_pending"

    internal fun queueDefaultLauncherAfterClose(activity: Activity) {
        if (isDryRun(activity) || !defaultLauncher(activity)) return
        closingPreparation = java.lang.ref.WeakReference(activity.window.decorView)
        prefs(activity).edit().putBoolean(KEY_HOME_PENDING, true).commit()
    }
    fun hasPendingDefaultLauncher(context: Context) = prefs(context).getBoolean(KEY_HOME_PENDING, false)
    fun preparationWindowDismissed() = closingPreparation?.get()?.isAttachedToWindow != true
    fun consumeDefaultLauncherRequest(context: Context): Intent? {
        if (!hasPendingDefaultLauncher(context) || !preparationWindowDismissed()) return null
        prefs(context).edit().remove(KEY_HOME_PENDING).commit()
        closingPreparation = null
        return defaultLauncherRequest(context)
    }

    fun isFirstInstallPending(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_COMPLETE, false)
    }

    fun toggles(context: Context): List<Toggle> = listOf(
        toggle(context, APP_DRAWER, context.getString(R.string.startup_app_drawer), context.getString(R.string.startup_toolbar_shortcut_for_browsing_installed_apps)),
        toggle(context, MODULE_DOCK, context.getString(R.string.startup_module_dock), context.getString(R.string.startup_quick_access_row_for_launcher_modules)),
        toggle(context, WIDGETS, context.getString(R.string.startup_android_widgets), context.getString(R.string.startup_toolbar_shortcut_for_the_android_widget_drawer)),
        toggle(context, NOTIFICATIONS, context.getString(R.string.startup_notifications_and_media), context.getString(R.string.startup_read_notifications_and_external_media_sessions)),
        toggle(context, STATUS, context.getString(R.string.startup_status_extras), context.getString(R.string.startup_weather_and_unlock_count_labels)),
        toggle(context, LOCK, context.getString(R.string.startup_double_tap_lock), context.getString(R.string.startup_lock_requires_access)),
        toggle(
            context,
            TERMUX,
            context.getString(R.string.startup_termux_and_tmux),
            if (TermuxBridgeManager.isTermuxInstalled(context)) context.getString(R.string.startup_termux_detected_show_the_tmux_workspace_shortcut)
            else context.getString(R.string.startup_termux_not_detected_keep_the_tmux_shortcut_available_for_later)
        ),
        toggle(
            context,
            TASKER,
            context.getString(R.string.startup_tasker),
            if (TaskerIntegrationManager.isTaskerInstalled(context)) context.getString(R.string.startup_tasker_detected_allow_re_t_ui_actions_and_tasks)
            else context.getString(R.string.startup_tasker_not_detected_enable_the_integration_for_later)
        ),
        toggle(context, SOUNDS, context.getString(R.string.startup_launcher_sounds), context.getString(R.string.startup_boot_action_timer_and_reminder_sounds))
    )

    fun toggle(context: Context, id: String) {
        if (id !in ids) return
        val preferences = prefs(context)
        preferences.edit().putBoolean(choiceKey(id), !preferences.getBoolean(choiceKey(id), false)).apply()
    }

    fun setLevel(context: Context, value: String) {
        if (value == BASIC || value == ADVANCED) prefs(context).edit().putString(KEY_LEVEL, value).apply()
    }

    fun toggleAutoTheme(context: Context) {
        prefs(context).edit().putBoolean(KEY_AUTO_THEME, !autoTheme(context)).apply()
    }

    fun setBorder(context: Context, value: String) {
        if (value in setOf(BORDER_DASHED, BORDER_SOLID, BORDER_NONE)) {
            prefs(context).edit().putString(KEY_BORDER, value).apply()
        }
    }

    fun toggleCyberdeck(context: Context) {
        prefs(context).edit().putBoolean(KEY_CYBERDECK, !cyberdeck(context)).apply()
    }

    fun toggleCrt(context: Context) {
        prefs(context).edit().putBoolean(KEY_CRT, !crt(context)).apply()
    }

    fun finish(context: Context, palette: AutoColorManager.Snapshot? = null): ActionResult {
        check(page(context) == THANKS && isBuilding(context)) { "Finish the startup pages before applying" }
        if (isDryRun(context)) {
            prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_BUILDING, false).apply()
            return ActionResult(context.getString(R.string.startup_startup_preview_complete_no_settings_were_changed))
        }

        val themeResult = applyChoices(context, palette)
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).putBoolean(KEY_COMPLETE, true)
            .putBoolean(KEY_BUILDING, false).apply()
        val followUp = mutableListOf<String>()
        if (level(context) == ADVANCED && enabled(context, TERMUX)) followUp.add(context.getString(R.string.startup_termux_run_tbridge_setup_then_tbridge_status))
        if (level(context) == ADVANCED && enabled(context, TASKER)) followUp.add(context.getString(R.string.startup_tasker_allow_external_access_in_tasker_if_prompted))
        return ActionResult(
            buildString {
                append(context.getString(R.string.startup_startup_setup_applied))
                themeResult?.let { append("\n").append(it.message(context)) }
                if (followUp.isNotEmpty()) append("\n").append(followUp.joinToString("\n"))
            },
            reload = true
        )
    }

    fun cancel(context: Context): ActionResult {
        if (!isDryRun(context)) return ActionResult(context.getString(R.string.startup_required))
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
        return ActionResult(context.getString(R.string.startup_startup_preview_closed_no_settings_were_changed))
    }

    internal fun borderValues(value: String, gap: Int = 4): Pair<Boolean, Int> = when (value) {
        BORDER_DASHED -> true to gap.coerceIn(1, 24)
        BORDER_NONE -> false to gap.coerceIn(1, 24)
        else -> true to 0
    }

    internal fun selectedFeature(level: String, id: String, enabled: Boolean): Boolean =
        if (level == ADVANCED) enabled else id == STATUS || id == LOCK

    internal fun effectValues(level: String, cyberdeck: Boolean, crt: Boolean): Pair<Boolean, Boolean> =
        if (level == ADVANCED) cyberdeck to crt else false to false

    internal fun prepare(preferences: SharedPreferences) {
        val editor = preferences.edit().putBoolean(KEY_ACTIVE, true)
        if (preferences.getInt(KEY_MENU_VERSION, 0) == 0) {
            ids.forEach { editor.putBoolean(choiceKey(it), it == STATUS || it == LOCK) }
            editor.putString(KEY_LEVEL, BASIC)
            editor.putBoolean(KEY_AUTO_THEME, true)
            editor.putString(KEY_BORDER, BORDER_SOLID)
            editor.putBoolean(KEY_CYBERDECK, false)
            editor.putBoolean(KEY_CRT, false)
        }
        if (preferences.getInt(KEY_MENU_VERSION, 0) != MENU_VERSION) {
            editor.putInt(KEY_PAGE, WELCOME).putBoolean(KEY_BUILDING, false)
        }
        editor.putInt(KEY_MENU_VERSION, MENU_VERSION)
        editor.apply()
    }

    private fun open(context: Context, preview: Boolean = false) {
        context.startActivity(Intent(context, StartupMenuActivity::class.java).apply {
            putExtra(EXTRA_PREVIEW, preview)
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun applyChoices(context: Context, palette: AutoColorManager.Snapshot?): AutoColorManager.Snapshot? {
        fun selected(id: String): Boolean = selectedFeature(level(context), id, enabled(context, id))

        LauncherSettings.set(context, Behavior.swipe_up_apps_drawer, selected(APP_DRAWER).toString())
        LauncherSettings.set(context, Behavior.show_module_dock, selected(MODULE_DOCK).toString())
        LauncherSettings.set(context, Behavior.show_android_widget_drawer_button, selected(WIDGETS).toString())
        // Startup can be restored without a live LauncherActivity owning notification settings.
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        val notifications = selected(NOTIFICATIONS).toString()
        LauncherSettings.set(context, Notifications.show_notifications, notifications)
        LauncherSettings.set(context, Notifications.terminal_notifications, notifications)
        val status = selected(STATUS).toString()
        LauncherSettings.set(context, Ui.show_weather, status)
        LauncherSettings.set(context, Ui.show_unlock_counter, status)
        LauncherSettings.set(context, Behavior.double_tap_lock, selected(LOCK).toString())
        LauncherSettings.set(context, Behavior.show_tmux_workspace_button, selected(TERMUX).toString())
        TaskerIntegrationManager.setEnabled(context, selected(TASKER))
        LauncherSettings.set(context, Behavior.launcher_sounds, selected(SOUNDS).toString())
        val effects = effectValues(level(context), cyberdeck(context), crt(context))
        LauncherSettings.set(context, Behavior.enable_cyberdeck_mode, effects.first.toString())
        LauncherSettings.set(context, Behavior.enable_crt_filter, effects.second.toString())

        val border = borderValues(border(context), borderGap(context))
        LauncherSettings.set(context, Ui.enable_dashed_border, border.first.toString())
        LauncherSettings.set(context, Ui.dashed_border_stroke_width, borderStroke(context).toString())
        LauncherSettings.set(context, Ui.dashed_border_gap_length, border.second.toString())
        if (autoTheme(context)) {
            return LauncherSettings.applyWallpaperSnapshot(context,
                palette ?: AutoColorManager.currentWallpaperColorSnapshot(context))
        } else {
            LauncherSettings.set(context, Ui.auto_color_pick, "false")
            LauncherSettings.set(context, Theme.wallpaper_overlay_color, "#00000000")
        }
        return null
    }

    private fun toggle(context: Context, id: String, label: String, description: String): Toggle =
        Toggle(id, label, description, enabled(context, id))

    private fun enabled(context: Context, id: String): Boolean = prefs(context).getBoolean(choiceKey(id), false)
    private fun choiceKey(id: String): String = CHOICE_PREFIX + id
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(
        if (isDryRun(context)) PREVIEW_PREFS else PREFS, Context.MODE_PRIVATE)
}

class StartupMenuActivity : ohi.andre.consolelauncher.localization.LocalizedComponentActivity() {
    private val main = Handler(Looper.getMainLooper())
    private var pageScroll: ScrollView? = null
    private var buildOutput: TextView? = null
    private var buildProgress: TextView? = null
    private var buildStarted = 0L
    private var palette: AutoColorManager.Snapshot? = null
    private var paletteReady = false
    private var buildResult: StartupMenuManager.ActionResult? = null
    private val buildTick = Runnable { advanceBuild() }
    private var previewPanel: View? = null
    private var previewHeader: View? = null
    private var dashControls: View? = null
    private data class BorderSurface(val fill: Theme, val outline: Theme, val pressed: Boolean = false)
    private val borderSurfaces = linkedMapOf<View, BorderSurface>()

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString("default_launcher_result")?.let {
            buildResult = StartupMenuManager.ActionResult(it, reload = savedInstanceState?.getBoolean("default_launcher_reload", true) ?: true)
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        applyFullscreen(this)
        if (!StartupMenuManager.isActive(this) && buildResult == null) {
            if (!StartupMenuManager.isDryRun(this) && !StartupMenuManager.isFirstInstallPending(this)) LauncherActivity.preview(this)
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    StartupMenuManager.isBuilding(this@StartupMenuActivity) || buildResult != null -> Unit
                    StartupMenuManager.page(this@StartupMenuActivity) > StartupMenuManager.WELCOME ->
                        navigate(StartupMenuManager.page(this@StartupMenuActivity) - 1)
                    StartupMenuManager.isDryRun(this@StartupMenuActivity) -> close(StartupMenuManager.cancel(this@StartupMenuActivity))
                    else -> Toast.makeText(this@StartupMenuActivity, R.string.startup_required, Toast.LENGTH_SHORT).show()
                }
            }
        })
        if (buildResult != null) { completeSetup(); return }
        render()
        if (StartupMenuManager.isBuilding(this)) prepareBuild()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        buildResult?.let {
            outState.putString("default_launcher_result", it.message)
            outState.putBoolean("default_launcher_reload", it.reload)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (!StartupMenuManager.isActive(this) && buildResult == null) {
            if (!StartupMenuManager.isDryRun(this) && !StartupMenuManager.isFirstInstallPending(this)) LauncherActivity.preview(this)
            finish()
            return
        }
        applyFullscreen(this)
        if (buildStarted != 0L) { main.removeCallbacks(buildTick); main.post(buildTick) }
    }

    override fun onPause() {
        saveScroll()
        main.removeCallbacks(buildTick)
        super.onPause()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyFullscreen(this)
    }

    private fun saveScroll() {
        if (StartupMenuManager.page(this) == StartupMenuManager.CONFIGURE) {
            pageScroll?.let { StartupMenuManager.setConfigScroll(this, it.scrollY) }
        }
    }

    private fun navigate(page: Int) {
        saveScroll()
        StartupMenuManager.setPage(this, page)
        render()
    }

    private fun render() {
        setContentView(buildContent())
        refreshBorderPreview()
    }

    private fun buildContent(): View {
        dashControls = null
        borderSurfaces.clear()
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
        panel.id = R.id.startup_preview_panel
        previewPanel = panel
        previewBorder(panel, Theme.settings_panel_background_color, Theme.settings_panel_border_color)
        shell.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
            topMargin = dp(this@StartupMenuActivity, 11f)
        })

        val header = TextView(this).apply {
            text = if (StartupMenuManager.isDryRun(this@StartupMenuActivity)) getString(R.string.startup_startup_dry_run) else getString(R.string.startup_startup)
        }
        styleHeader(this, header)
        previewHeader = header
        previewBorder(header, Theme.settings_header_background_color, Theme.settings_header_border_color)
        shell.addView(header, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(this@StartupMenuActivity, 30f)
        })

        val page = StartupMenuManager.page(this)
        val building = StartupMenuManager.isBuilding(this) || buildResult != null
        panel.addView(terminalText(getString(R.string.startup_page_progress, page + 1), 12f), blockParams(10f))
        val title = when {
            building -> R.string.startup_build_title
            page == StartupMenuManager.WELCOME -> R.string.startup_welcome_title
            page == StartupMenuManager.CONFIGURE -> R.string.startup_set_up_your_launcher
            else -> R.string.startup_thanks_title
        }
        panel.addView(terminalText(getString(title), 21f).apply {
            id = R.id.startup_page_title
            setTypeface(typeface, Typeface.BOLD)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }, blockParams(8f))
        if (StartupMenuManager.isDryRun(this)) {
            panel.addView(terminalText(getString(R.string.startup_preview_only_changing_switches_here_will_not_alter_your_launcher), 12f), blockParams(8f))
        }

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        when {
            building -> {
                list.addView(terminalText(getString(R.string.startup_build_description), 13f), blockParams(18f))
                buildProgress = terminalText("[░░░░░░░░░░]", 22f).apply {
                    id = R.id.startup_build_progress
                    typeface = Typeface.MONOSPACE
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                panel.addView(buildProgress, blockParams(16f))
                buildOutput = terminalText("", 14f).apply {
                    id = R.id.startup_build_output
                    typeface = Typeface.MONOSPACE
                    accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                }
                list.addView(buildOutput, blockParams(8f))
            }
            page == StartupMenuManager.WELCOME -> {
                list.addView(terminalText(getString(R.string.startup_welcome_body), 14f), blockParams(16f))
                list.addView(guideCard(R.string.startup_intro_launch_title, R.string.startup_intro_launch_body), blockParams(10f))
                list.addView(guideCard(R.string.startup_intro_chips_title, R.string.startup_intro_chips_body), blockParams(10f))
                list.addView(guideCard(R.string.startup_intro_tools_title, R.string.startup_intro_tools_body), blockParams(10f))
                list.addView(terminalText(getString(R.string.startup_intro_next), 12f), blockParams(8f))
            }
            page == StartupMenuManager.CONFIGURE -> buildConfiguration(list)
            else -> {
                list.addView(terminalText(getString(R.string.startup_thanks_body), 15f), blockParams(18f))
                list.addView(guideCard(R.string.startup_your_setup, R.string.startup_choices_reversible), blockParams(12f))
                list.addView(terminalText(selectionSummary(), 14f), blockParams(14f))
                list.addView(terminalText(getString(R.string.startup_thanks_feedback), 14f), blockParams(14f))
            }
        }
        pageScroll = ScrollView(this).apply { id = R.id.startup_scroll; addView(list) }
        panel.addView(pageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (page == StartupMenuManager.CONFIGURE) {
            pageScroll?.post { pageScroll?.scrollTo(0, StartupMenuManager.configScroll(this)) }
        }
        if (building) return screen

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(this@StartupMenuActivity, 10f), 0, 0)
        }
        if (page > StartupMenuManager.WELCOME || StartupMenuManager.isDryRun(this)) {
            val back = button(getString(if (page == StartupMenuManager.WELCOME) R.string.startup_end_preview else R.string.startup_back), false)
            back.id = R.id.startup_back
            back.setOnClickListener {
                if (page == StartupMenuManager.WELCOME) close(StartupMenuManager.cancel(this)) else navigate(page - 1)
            }
            controls.addView(back, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            controls.addView(View(this), LinearLayout.LayoutParams(dp(this, 10f), 1))
        }
        val next = button(getString(when {
            page < StartupMenuManager.THANKS -> R.string.startup_continue
            StartupMenuManager.isDryRun(this) -> R.string.startup_finish_preview
            else -> R.string.startup_build_button
        }), true).apply { id = R.id.startup_next }
        next.setOnClickListener {
            if (page < StartupMenuManager.THANKS) navigate(page + 1) else {
                StartupMenuManager.setBuilding(this, true)
                render()
                prepareBuild()
            }
        }
        controls.addView(next, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(controls)
        return screen
    }

    private fun buildConfiguration(list: LinearLayout) {
        list.addView(terminalText(getString(R.string.startup_configuration_description), 13f), blockParams(12f))
        list.addView(levelRow(), blockParams(10f))
        list.addView(choiceToggleRow(getString(R.string.startup_auto_theme), getString(R.string.startup_theme_snapshot_description),
            StartupMenuManager.autoTheme(this)) {
            StartupMenuManager.toggleAutoTheme(this)
            StartupMenuManager.autoTheme(this)
        }, blockParams(8f))
        list.addView(terminalText(getString(R.string.startup_border), 13f), blockParams(6f))
        list.addView(borderRow(), blockParams(10f))
        list.addView(terminalText(getString(R.string.startup_border_preview), 12f), blockParams(8f))
        dashControls = LinearLayout(this).apply {
            id = R.id.startup_dash_controls
            orientation = LinearLayout.VERTICAL
            addView(borderSlider(R.id.startup_border_stroke, R.string.startup_border_stroke, 15,
                (StartupMenuManager.borderStroke(this@StartupMenuActivity) * 2).toInt() - 1) {
                StartupMenuManager.setBorderStroke(this@StartupMenuActivity, (it + 1) / 2f)
                ((it + 1) / 2f).toString()
            }, blockParams(4f))
            addView(borderSlider(R.id.startup_border_gap, R.string.startup_border_gap, 23,
                StartupMenuManager.borderGap(this@StartupMenuActivity) - 1) {
                StartupMenuManager.setBorderGap(this@StartupMenuActivity, it + 1)
                (it + 1).toString()
            })
            visibility = if (StartupMenuManager.border(this@StartupMenuActivity) == StartupMenuManager.BORDER_DASHED) View.VISIBLE else View.GONE
        }
        list.addView(dashControls, blockParams(8f))
        list.addView(CheckBox(this).apply {
            id = R.id.startup_default_launcher
            text = getString(R.string.startup_default_launcher)
            typeface = Tuils.getTypeface(this@StartupMenuActivity)
            textSize = 14f
            setTextColor(textColor())
            buttonTintList = ColorStateList.valueOf(textColor())
            minHeight = dp(this@StartupMenuActivity, 48f)
            isChecked = StartupMenuManager.defaultLauncher(this@StartupMenuActivity)
            setOnCheckedChangeListener { _, checked -> StartupMenuManager.setDefaultLauncher(this@StartupMenuActivity, checked) }
        }, blockParams(4f))
        list.addView(terminalText(getString(R.string.startup_default_launcher_description), 12f), blockParams(12f))
        if (StartupMenuManager.level(this) == StartupMenuManager.ADVANCED) {
            list.addView(terminalText(getString(R.string.startup_features), 13f), blockParams(6f))
            StartupMenuManager.toggles(this).forEach { list.addView(toggleRow(it), blockParams(8f)) }
            list.addView(button(getString(R.string.optional_calculator_title), false).apply {
                setOnClickListener { ohi.andre.consolelauncher.calculator.OptionalCalculatorEngine.showManagement(this@StartupMenuActivity) }
            }, blockParams(8f))
            list.addView(terminalText(getString(R.string.startup_access_note), 12f), blockParams(10f))
            list.addView(terminalText(getString(R.string.startup_visual_effects), 13f), blockParams(6f))
            list.addView(visualEffects(), blockParams(4f))
        } else {
            list.addView(terminalText(getString(R.string.startup_basic_description), 12f), blockParams(8f))
        }
    }

    private fun borderSlider(viewId: Int, labelId: Int, maximum: Int, initial: Int, update: (Int) -> String): View {
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = if (viewId == R.id.startup_border_stroke) StartupMenuManager.borderStroke(this).toString()
            else StartupMenuManager.borderGap(this).toString()
        val label = terminalText(getString(labelId, value), 13f)
        group.addView(label)
        group.addView(SeekBar(this).apply {
            id = viewId
            max = maximum
            progress = initial
            minimumHeight = dp(context, 48f)
            contentDescription = label.text
            styleSlider(context, this)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                    label.text = getString(labelId, update(progress))
                    bar.contentDescription = label.text
                    refreshBorderPreview()
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }, blockParams(0f))
        return group
    }

    private fun previewBorder(view: View, fill: Theme, outline: Theme, pressed: Boolean = false) {
        borderSurfaces[view] = BorderSurface(fill, outline, pressed)
        applyPreviewBorder(view, checkNotNull(borderSurfaces[view]))
    }

    private fun applyPreviewBorder(view: View, surface: BorderSurface) {
        val border = StartupMenuManager.border(this)
        fun drawable(fill: Theme, outline: Theme) = TerminalBorderDrawable(
            ThemeColorResolver.color(fill), ThemeColorResolver.color(outline),
            if (border == StartupMenuManager.BORDER_NONE) 0 else dp(this, StartupMenuManager.borderStroke(this)).coerceAtLeast(1),
            dp(this, AppearanceSettings.dashedBorderCornerRadius().toFloat()).toFloat(),
            border == StartupMenuManager.BORDER_DASHED,
            dp(this, AppearanceSettings.dashLength().coerceAtLeast(1).toFloat()).toFloat(),
            dp(this, StartupMenuManager.borderGap(this).toFloat()).toFloat())
        val normal = drawable(surface.fill, surface.outline)
        view.background = if (surface.pressed) StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), drawable(Theme.pressed_background_color, Theme.pressed_border_color))
            addState(intArrayOf(), normal)
        } else normal
    }

    private fun refreshBorderPreview() {
        val border = StartupMenuManager.border(this)
        borderSurfaces.forEach { (view, surface) -> applyPreviewBorder(view, surface) }
        val headerPadding = dp(this, 3f + if (border == StartupMenuManager.BORDER_NONE) 0f else StartupMenuManager.borderStroke(this))
        previewHeader?.setPadding(dp(this, 12f), headerPadding, dp(this, 12f), headerPadding)
        TerminalBorderRuntime.bind(previewPanel, previewHeader)
        dashControls?.visibility = if (border == StartupMenuManager.BORDER_DASHED) View.VISIBLE else View.GONE
    }

    private fun guideCard(title: Int, body: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 12f), dp(context, 12f), dp(context, 12f), dp(context, 12f))
        stylePanel(context, this)
        previewBorder(this, Theme.settings_panel_background_color, Theme.settings_panel_border_color)
        addView(terminalText(getString(title), 15f).apply { setTypeface(typeface, Typeface.BOLD) }, blockParams(6f))
        addView(terminalText(getString(body), 13f))
    }

    private fun selectionSummary(): String {
        var borderName = getString(when (StartupMenuManager.border(this)) {
            StartupMenuManager.BORDER_DASHED -> R.string.startup_dashed
            StartupMenuManager.BORDER_NONE -> R.string.startup_none
            else -> R.string.startup_solid
        })
        if (StartupMenuManager.border(this) == StartupMenuManager.BORDER_DASHED) {
            borderName += getString(R.string.startup_border_summary, StartupMenuManager.borderStroke(this).toString(), StartupMenuManager.borderGap(this))
        }
        val features = StartupMenuManager.toggles(this).filter {
            StartupMenuManager.selectedFeature(StartupMenuManager.level(this), it.id, it.enabled)
        }.joinToString(", ") { it.label }.ifEmpty { getString(R.string.startup_none) }
        val effects = StartupMenuManager.effectValues(StartupMenuManager.level(this), StartupMenuManager.cyberdeck(this), StartupMenuManager.crt(this))
        val effectNames = listOfNotNull(
            getString(R.string.startup_cyberdeck).takeIf { effects.first },
            getString(R.string.startup_crt).takeIf { effects.second }
        ).joinToString(" + ").ifEmpty { getString(R.string.startup_none) }
        return getString(R.string.startup_selection_summary,
            getString(if (StartupMenuManager.level(this) == StartupMenuManager.BASIC) R.string.startup_basic else R.string.startup_advanced),
            getString(if (StartupMenuManager.autoTheme(this)) R.string.startup_on else R.string.startup_off), borderName, features, effectNames)
    }

    private fun prepareBuild() {
        buildStarted = SystemClock.uptimeMillis()
        paletteReady = false
        Thread({
            try {
                val snapshot = if (!StartupMenuManager.isDryRun(this) && StartupMenuManager.autoTheme(this)) {
                    AutoColorManager.currentWallpaperColorSnapshot(this)
                } else null
                main.post {
                    if (!isFinishing && !isDestroyed) { palette = snapshot; paletteReady = true }
                }
            } catch (error: Exception) {
                main.post { if (!isFinishing && !isDestroyed) buildFailed(error) }
            }
        }, "retui-startup-theme").start()
        main.post(buildTick)
    }

    private fun advanceBuild() {
        if (isFinishing || isDestroyed) return
        val elapsed = SystemClock.uptimeMillis() - buildStarted
        val duration = if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) 0L else 2400L
        val lines = configurationLines()
        val count = if (duration == 0L) lines.size else (1 + elapsed * lines.size / duration).toInt().coerceAtMost(lines.size)
        if (buildResult == null) {
            val output = lines.take(count).joinToString("\n")
            if (buildOutput?.text?.toString() != output) {
                buildOutput?.text = output
                pageScroll?.post { pageScroll?.fullScroll(View.FOCUS_DOWN) }
            }
            val progress = (count * 9 / lines.size).coerceAtLeast(1)
            buildProgress?.text = "[" + "█".repeat(progress) + "░".repeat(10 - progress) + "]"
            if (elapsed >= duration && paletteReady) {
                try {
                    buildResult = StartupMenuManager.finish(this, palette)
                    buildOutput?.append("\n\n" + getString(R.string.startup_build_ready))
                    buildProgress?.text = "[██████████]"
                    buildStarted = SystemClock.uptimeMillis()
                } catch (error: Exception) {
                    buildFailed(error)
                    return
                }
            }
        } else if (elapsed >= if (duration == 0L) 0L else 350L) {
            buildStarted = 0L
            completeSetup()
            return
        }
        main.postDelayed(buildTick, 100)
    }

    private fun completeSetup() {
        StartupMenuManager.queueDefaultLauncherAfterClose(this)
        close(checkNotNull(buildResult))
    }

    // Display-only pseudo-code; settings are applied exclusively by StartupMenuManager.finish.
    private fun configurationLines(): List<String> = buildList {
        add("launcher.configure {")
        add("  setup = ${StartupMenuManager.level(this@StartupMenuActivity)}")
        add("  palette = ${if (StartupMenuManager.autoTheme(this@StartupMenuActivity)) "wallpaper.current" else "theme.keep_current"}")
        val border = StartupMenuManager.border(this@StartupMenuActivity)
        add("  border = $border")
        if (border != StartupMenuManager.BORDER_NONE) {
            add("  stroke = ${StartupMenuManager.borderStroke(this@StartupMenuActivity)} dp")
            if (border == StartupMenuManager.BORDER_DASHED) add("  gap = ${StartupMenuManager.borderGap(this@StartupMenuActivity)} dp")
        }
        add("  features {")
        StartupMenuManager.toggles(this@StartupMenuActivity).filter {
            StartupMenuManager.selectedFeature(StartupMenuManager.level(this@StartupMenuActivity), it.id, it.enabled)
        }.forEach { add("    ${it.id} = on") }
        add("  }")
        val effects = StartupMenuManager.effectValues(StartupMenuManager.level(this@StartupMenuActivity),
            StartupMenuManager.cyberdeck(this@StartupMenuActivity), StartupMenuManager.crt(this@StartupMenuActivity))
        add("  cyberdeck = ${effects.first}")
        add("  crt = ${effects.second}")
        add("}")
        add(getString(if (StartupMenuManager.isDryRun(this@StartupMenuActivity)) R.string.startup_build_preview else R.string.startup_build_apply))
    }

    private fun buildFailed(error: Exception) {
        Tuils.log(error)
        main.removeCallbacks(buildTick)
        buildStarted = 0L
        StartupMenuManager.setBuilding(this, false)
        render()
        Toast.makeText(this, R.string.startup_build_failed, Toast.LENGTH_LONG).show()
    }

    private fun toggleRow(toggle: StartupMenuManager.Toggle): View {
        return choiceToggleRow(toggle.label, toggle.description, toggle.enabled) {
            StartupMenuManager.toggle(this, toggle.id)
            StartupMenuManager.toggles(this).first { it.id == toggle.id }.enabled
        }
    }

    private fun choiceToggleRow(label: String, description: String, checked: Boolean, action: () -> Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(this@StartupMenuActivity, 12f), dp(this@StartupMenuActivity, 9f), dp(this@StartupMenuActivity, 10f), dp(this@StartupMenuActivity, 9f))
            contentDescription = getString(R.string.startup_toggle_description, label, description, getString(if (checked) R.string.startup_on else R.string.startup_off))
            isClickable = true
            isFocusable = true
        }
        stylePanel(this, row)
        previewBorder(row, Theme.settings_panel_background_color, Theme.settings_panel_border_color)
        row.addView(terminalText(label + "\n" + description, 13f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val switch = TextView(this)
        styleStartupToggle(switch, checked)
        row.setOnClickListener {
            val enabled = action()
            styleStartupToggle(switch, enabled)
            row.contentDescription = getString(R.string.startup_toggle_description, label, description, getString(if (enabled) R.string.startup_on else R.string.startup_off))
        }
        row.addView(switch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(this@StartupMenuActivity, 10f)
        })
        return row
    }

    private fun levelRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(StartupMenuManager.BASIC, StartupMenuManager.ADVANCED).forEachIndexed { index, value ->
            val choice = button(getString(if (value == StartupMenuManager.BASIC) R.string.startup_basic else R.string.startup_advanced), StartupMenuManager.level(this) == value)
            styleStartupChoice(choice, StartupMenuManager.level(this) == value)
            choice.setOnClickListener { saveScroll(); StartupMenuManager.setLevel(this, value); render() }
            row.addView(choice, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(this@StartupMenuActivity, 8f)
            })
        }
        return row
    }

    private fun borderRow(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val values = listOf(StartupMenuManager.BORDER_DASHED, StartupMenuManager.BORDER_SOLID, StartupMenuManager.BORDER_NONE)
        values.forEachIndexed { index, value ->
            val choice = button(getString(when (value) {
                StartupMenuManager.BORDER_DASHED -> R.string.startup_dashed
                StartupMenuManager.BORDER_SOLID -> R.string.startup_solid
                else -> R.string.startup_none
            }), StartupMenuManager.border(this) == value)
            styleStartupChoice(choice, StartupMenuManager.border(this) == value)
            choice.setOnClickListener {
                StartupMenuManager.setBorder(this, value)
                values.forEachIndexed { child, option -> styleStartupChoice(row.getChildAt(child) as TextView, option == value) }
                refreshBorderPreview()
            }
            row.addView(choice, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) leftMargin = dp(this@StartupMenuActivity, 8f)
            })
        }
        return row
    }

    private fun visualEffects(): View {
        val group = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        group.addView(terminalText(getString(R.string.startup_the_standard_launcher_renderer_is_always_active), 12f), blockParams(8f))
        group.addView(choiceToggleRow(getString(R.string.startup_cyberdeck), getString(R.string.startup_layer_cyberdeck_chrome_over_the_classic_base), StartupMenuManager.cyberdeck(this)) {
            StartupMenuManager.toggleCyberdeck(this); StartupMenuManager.cyberdeck(this)
        }, blockParams(8f))
        group.addView(choiceToggleRow(getString(R.string.startup_crt), getString(R.string.startup_layer_scanline_and_crt_effects_over_the_classic_base), StartupMenuManager.crt(this)) {
            StartupMenuManager.toggleCrt(this); StartupMenuManager.crt(this)
        }, blockParams(8f))
        return group
    }

    private fun close(result: StartupMenuManager.ActionResult) {
        Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        finish()
        if (result.reload) {
            LauncherActivity.instance?.addMessage("startup", result.message)
            LauncherActivity.preview(this)
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
        previewButtonBorder(this, primary)
        minHeight = dp(this@StartupMenuActivity, 48f)
        isFocusable = true
    }

    private fun previewButtonBorder(view: TextView, primary: Boolean) = previewBorder(view,
        if (primary) Theme.primary_button_background_color else Theme.button_background_color,
        if (primary) Theme.primary_button_border_color else Theme.button_border_color, pressed = true)

    private fun styleStartupChoice(view: TextView, selected: Boolean) {
        styleChoice(this, view, selected)
        previewButtonBorder(view, selected)
    }

    private fun styleStartupToggle(view: TextView, enabled: Boolean) {
        styleToggle(this, view, enabled)
        previewBorder(view, if (enabled) Theme.toggle_on_background_color else Theme.toggle_off_background_color,
            if (enabled) Theme.toggle_on_border_color else Theme.toggle_off_border_color)
    }

    private fun blockParams(bottom: Float): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(this@StartupMenuActivity, bottom) }
}
