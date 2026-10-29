package ohi.andre.consolelauncher.managers.status

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.TextView
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime

/** Shared row styling, with the launcher's numeric alignment and actual status padding. */
object StatusSurfaceRenderer {
    data class Spec(val label: UIManager.Label, val show: Ui, val size: Ui, val index: Ui, val align: Ui,
                    val text: Theme, val fill: Theme, val shadow: Theme, val surface: SurfaceBorder)
    val specs = listOf(
        Spec(UIManager.Label.ram, Ui.show_ram, Ui.ram_size, Ui.ram_index, Ui.ram_status_alignment, Theme.ram_text_color, Theme.ram_status_background_color, Theme.ram_status_text_shadow_color, SurfaceBorder.RAM),
        Spec(UIManager.Label.device, Ui.show_device_name, Ui.device_size, Ui.device_index, Ui.device_status_alignment, Theme.device_text_color, Theme.device_status_background_color, Theme.device_status_text_shadow_color, SurfaceBorder.DEVICE),
        Spec(UIManager.Label.time, Ui.show_time, Ui.time_size, Ui.time_index, Ui.time_status_alignment, Theme.time_text_color, Theme.time_status_background_color, Theme.time_status_text_shadow_color, SurfaceBorder.TIME),
        Spec(UIManager.Label.battery, Ui.show_battery, Ui.battery_size, Ui.battery_index, Ui.battery_status_alignment, Theme.battery_text_high, Theme.battery_status_background_color, Theme.battery_status_text_shadow_color, SurfaceBorder.BATTERY),
        Spec(UIManager.Label.storage, Ui.show_storage_info, Ui.storage_size, Ui.storage_index, Ui.storage_status_alignment, Theme.storage_text_color, Theme.storage_status_background_color, Theme.storage_status_text_shadow_color, SurfaceBorder.STORAGE),
        Spec(UIManager.Label.network, Ui.show_network_info, Ui.network_size, Ui.network_index, Ui.network_status_alignment, Theme.network_info_text_color, Theme.network_status_background_color, Theme.network_status_text_shadow_color, SurfaceBorder.NETWORK),
        Spec(UIManager.Label.notes, Ui.show_notes, Ui.notes_size, Ui.notes_index, Ui.notes_status_alignment, Theme.notes_text_color, Theme.notes_status_background_color, Theme.notes_status_text_shadow_color, SurfaceBorder.NOTES),
        Spec(UIManager.Label.weather, Ui.show_weather, Ui.weather_size, Ui.weather_index, Ui.weather_status_alignment, Theme.weather_text_color, Theme.weather_status_background_color, Theme.weather_status_text_shadow_color, SurfaceBorder.WEATHER),
        Spec(UIManager.Label.unlock, Ui.show_unlock_counter, Ui.unlock_size, Ui.unlock_index, Ui.unlock_status_alignment, Theme.unlock_counter_text_color, Theme.unlock_status_background_color, Theme.unlock_status_text_shadow_color, SurfaceBorder.UNLOCK),
        Spec(UIManager.Label.ascii, Ui.show_ascii, Ui.ascii_size, Ui.ascii_index, Ui.ascii_status_alignment, Theme.ascii_text_color, Theme.ascii_status_background_color, Theme.ascii_status_text_shadow_color, SurfaceBorder.ASCII)
    )
    fun style(view: TextView, label: UIManager.Label, values: AppearanceValues, unified: Boolean = false, font: Typeface? = null) {
        val spec = specs.first { it.label == label }
        view.typeface = font ?: values.typeface(view.context)
        values.applySize(view, spec.size)
        view.setTextColor(values.color(spec.text))
        view.gravity = when { values.int(spec.align) == 0 -> Gravity.CENTER_HORIZONTAL; values.int(spec.align) > 0 -> Gravity.END; else -> Gravity.START }
        view.background = TerminalBorderRuntime.panelDrawable(view.context, values.color(spec.fill),
            SurfaceBorder.resolveColor(values.get(spec.surface.color), values.color(Theme.terminal_border_color)),
            1.5f, if (unified) 0 else values.radius(Ui.module_corner_radius), values.bool(Ui.enable_dashed_border), false,
            !unified && values.bool(Ui.enable_dashed_border) && values.bool(spec.surface.enabled), !unified,
            FrameTarget.fromSurface(spec.surface), values)
        val padding = values.get(Ui.status_lines_margins).split(',').map { it.trim().toIntOrNull() ?: 0 }
        view.setPadding(padding.getOrElse(0) { 0 }, padding.getOrElse(1) { 0 }, padding.getOrElse(2) { 0 }, padding.getOrElse(3) { 0 })
        val shadow = ohi.andre.consolelauncher.managers.settings.TextShadows.parameters(values.get(Ui.shadow_params))
        view.setShadowLayer(shadow[2], shadow[0], shadow[1], values.color(spec.shadow))
        view.tag = if (android.graphics.Color.alpha(values.color(spec.shadow)) > 0) ohi.andre.consolelauncher.tuils.OutlineTextView.SHADOW_TAG else null
    }
}
