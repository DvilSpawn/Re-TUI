package ohi.andre.consolelauncher.managers.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils

/** One publication produces one UI update, regardless of the command/editor that saved it. */
internal object SettingsRefreshCoordinator {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var pending: LauncherSettings.Change? = null
    private var context: Context? = null
    internal var refreshCount = 0L
        private set

    internal fun appearanceOnly(setting: XMLPrefsSave): Boolean =
        setting is Theme || (setting is SurfaceBorderOption && setting != SurfaceBorderOption.unified_status_border) ||
            (setting is Suggestions && (setting.type() == XMLPrefsSave.COLOR || setting == Suggestions.suggestions_size)) ||
            (setting is Ui && (setting.label().orEmpty().endsWith("_size") || setting in setOf(
                Ui.system_font, Ui.font_file, Ui.font_size_offset, Ui.system_wallpaper, Ui.auto_color_pick,
                Ui.module_corner_radius, Ui.header_corner_radius, Ui.output_corner_radius,
                Ui.enable_dashed_border, Ui.dashed_border_stroke_width, Ui.dashed_border_dash_length,
                Ui.dashed_border_gap_length, Ui.dashed_border_corner_radius, Ui.text_redraw_times, Ui.shadow_params)))

    internal enum class Effect { APPEARANCE, GEOMETRY, HOME, ORIENTATION, IDLE, SERVICES }
    private val margins = setOf(Ui.display_margin_top_section, Ui.display_margin_bottom_section,
        Ui.keyboard_visible_margin_override, Ui.display_margin_landscape_mm)
    private val landscape = setOf(Ui.split_duo_launcher, Behavior.duo_swap_top_panes,
        Behavior.swipe_up_apps_drawer, Ui.show_ascii_landscape)
    private val widgetGrid = setOf(Ui.android_widget_grid_columns, Ui.android_widget_min_columns, Ui.android_widget_min_rows, Ui.android_widget_spacing)
    private val geometry = margins + landscape + widgetGrid + setOf(
        Behavior.show_tmux_workspace_button, Behavior.show_android_widget_drawer_button)
    internal fun effect(setting: XMLPrefsSave): Effect = when {
        setting is Notifications || setting == Behavior.preferred_music_app -> Effect.SERVICES
        setting == Behavior.orientation -> Effect.ORIENTATION
        setting == Behavior.breath_out || setting == Behavior.breath_out_seconds -> Effect.IDLE
        setting in geometry -> Effect.GEOMETRY
        appearanceOnly(setting) -> Effect.APPEARANCE
        else -> Effect.HOME
    }

    fun request(owner: Context?, change: LauncherSettings.Change) = synchronized(this) {
        if (change.settings.isEmpty() && change.previous.revision == change.current.revision) return@synchronized
        context = owner ?: context
        pending = pending?.let { LauncherSettings.Change(it.previous, change.current, it.settings + change.settings) } ?: change
        handler.removeCallbacks(refresh)
        handler.post(refresh)
    }

    private val refresh = Runnable {
        val (change, owner) = synchronized(this) {
            val next = pending ?: return@Runnable
            pending = null
            next to context
        }
        refreshCount++
        val keys = change.settings
        if (keys.any { it == Ui.system_font || it == Ui.font_file || it == Ui.font_size_offset }) {
            Tuils.cancelFont(); UIUtils.cancelFont()
        }
        if (owner != null && keys.any { it is Notifications || it == Behavior.preferred_music_app })
            NotificationService.requestReload(owner)
        val effects = keys.map(::effect).toSet()
        val launcher = LauncherActivity.instance
        if (launcher != null && !launcher.isFinishing && !launcher.isDestroyed) {
            if (Effect.ORIENTATION in effects) launcher.applyOrientationPreference()
            if (Effect.HOME in effects) launcher.refreshUiInPlace()
            else {
                if (Effect.APPEARANCE in effects || keys.isEmpty())
                    launcher.applySettingsAppearance(change.previous, change.current)
                if (Effect.IDLE in effects) launcher.refreshBreathOutSettings()
                if (Effect.GEOMETRY in effects || Effect.ORIENTATION in effects) launcher.uiManager?.let { ui ->
                    if (keys.any { it in margins }) ui.refreshDisplayMargins()
                    if (Effect.ORIENTATION in effects || keys.any { it in landscape }) ui.refreshResponsiveLandscapeLayout()
                    if (keys.any { it in widgetGrid }) ui.refreshAndroidWidgetDrawerGrid()
                    if (Behavior.show_tmux_workspace_button in keys) ui.refreshTermuxWorkspaceToolbarButton()
                    if (Behavior.show_android_widget_drawer_button in keys) ui.refreshAndroidWidgetDrawerToolbarButton()
                }
            }
        }
        if (effects.any { it != Effect.SERVICES }) TermuxWorkspaceActivity.refreshIfRunning()
    }
}
