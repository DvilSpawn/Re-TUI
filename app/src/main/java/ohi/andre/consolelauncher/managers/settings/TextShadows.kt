package ohi.andre.consolelauncher.managers.settings

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui

object TextShadows {
    internal fun parameters(raw: String?): FloatArray {
        val defaults = floatArrayOf(2f, 2f, 0.2f)
        val values = raw.orEmpty().split(',')
        return FloatArray(3) { index ->
            values.getOrNull(index)?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() && (index != 2 || it >= 0) } ?: defaults[index]
        }
    }

    fun apply(view: TextView, color: Theme) {
        val resolved = ThemeColorResolver.color(color)
        if (Color.alpha(resolved) == 0) {
            view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            val params = parameters(LauncherSettings.get(Ui.shadow_params))
            view.setShadowLayer(params[2], params[0], params[1], resolved)
        }
    }

    fun applyModule(view: View?) {
        if (view is TextView) apply(view, Theme.module_text_shadow_color)
        if (view is ViewGroup) for (index in 0 until view.childCount) applyModule(view.getChildAt(index))
    }
}
