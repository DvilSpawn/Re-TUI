package ohi.andre.consolelauncher.tuils

import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

object InputControlsRenderer {
    fun style(root: View, values: AppearanceValues, font: Typeface? = null) {
        fun panel(id: Int, fill: Theme, surface: SurfaceBorder) {
            root.findViewById<View>(id)?.background = TerminalBorderRuntime.panelDrawable(root.context, values.color(fill),
                SurfaceBorder.resolveColor(values.get(surface.color), values.color(Theme.terminal_border_color)), 1.5f,
                values.int(Ui.dashed_border_corner_radius).coerceIn(0, 48), values.bool(Ui.enable_dashed_border), false,
                values.bool(Ui.enable_dashed_border) && values.bool(surface.enabled), target = FrameTarget.fromSurface(surface), values = values)
        }
        fun margins(id: Int, setting: Ui) {
            val view = root.findViewById<View>(id) ?: return
            val parts = values.get(setting).split(',').map { it.trim().toIntOrNull() ?: 0 }
            view.setPadding(parts.getOrElse(2) { 0 }, parts.getOrElse(3) { 0 }, parts.getOrElse(2) { 0 }, parts.getOrElse(3) { 0 })
            (view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { params ->
                params.setMargins(parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 }, parts.getOrElse(0) { 0 }, parts.getOrElse(1) { 0 })
                view.layoutParams = params
            }
        }
        margins(R.id.input_group, Ui.input_area_margins)
        margins(R.id.input_view, Ui.input_field_margins)
        margins(R.id.prefix_view, Ui.input_field_margins)
        margins(R.id.tools_view, Ui.toolbar_margins)
        margins(R.id.suggestions_container, Ui.suggestions_area_margin)
        panel(R.id.input_group, Theme.input_background_color, SurfaceBorder.INPUT)
        panel(R.id.tools_view, Theme.toolbar_background_color, SurfaceBorder.TOOLBAR)
        panel(R.id.suggestions_container, Theme.suggestions_background_color, SurfaceBorder.SUGGESTIONS)
        listOf(R.id.prefix_view, R.id.input_view).forEach { id ->
            root.findViewById<TextView>(id)?.apply {
                typeface = font ?: values.typeface(context)
                setTextColor(values.color(Theme.input_text_color))
                values.applySize(this, Ui.input_output_size)
                val shadow = ohi.andre.consolelauncher.managers.settings.TextShadows.parameters(values.get(Ui.shadow_params))
                setShadowLayer(shadow[2], shadow[0], shadow[1], values.color(Theme.input_text_shadow_color))
            }
        }
        root.findViewById<ImageView>(R.id.submit_tv)?.setColorFilter(values.color(Theme.enter_icon_color))
        listOf(R.id.delete_view, R.id.back_view, R.id.next_view, R.id.paste_view, R.id.app_drawer_view,
            R.id.android_widget_drawer_view, R.id.termux_workspace_view).forEach { id ->
            root.findViewById<ImageView>(id)?.let { button ->
                button.setColorFilter(values.color(Theme.toolbar_icon_color))
                styleToolbarButton(button, values)
            }
        }
    }
    fun styleToolbarButton(button: ImageView, values: AppearanceValues) {
        val context = button.context
        if (!values.bool(ohi.andre.consolelauncher.managers.xml.options.Behavior.enable_cyberdeck_mode) &&
            FrameManager.drawable(context, FrameTarget.TOOLBAR) == null) return
        button.background = TerminalBorderRuntime.customFrame(context, android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT), FrameTarget.TOOLBAR)
            ?: CyberpunkIconFrameDrawable(
                ohi.andre.consolelauncher.managers.settings.ThemeColorResolver.withMaxAlpha(values.color(Theme.terminal_border_color), 230),
                Tuils.dpToPx(context, 1.6f), Tuils.dpToPx(context, 9f), Tuils.dpToPx(context, 9f))
    }

}
