package ohi.andre.consolelauncher.tuils

import android.graphics.Color
import android.view.View
import android.widget.TextView
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

object TerminalOutputRenderer {
    fun style(border: View, text: TextView, values: AppearanceValues = AppearanceValues.saved) {
        border.background = TerminalBorderRuntime.panelDrawable(border.context, values.color(Theme.output_background_color),
            SurfaceBorder.resolveColor(values.get(SurfaceBorder.OUTPUT.color), values.color(Theme.terminal_border_color)),
            1.5f, values.radius(Ui.output_corner_radius), values.bool(Ui.enable_dashed_border), true,
            values.bool(Ui.enable_dashed_border) && values.bool(SurfaceBorder.OUTPUT.enabled), target = FrameTarget.OUTPUT, values = values)
        text.setBackgroundColor(Color.TRANSPARENT)
        text.setTextColor(values.color(Theme.output_text_color))
        val shadow = ohi.andre.consolelauncher.managers.settings.TextShadows.parameters(values.get(Ui.shadow_params))
        text.setShadowLayer(shadow[2], shadow[0], shadow[1], values.color(Theme.output_text_shadow_color))
    }
}
