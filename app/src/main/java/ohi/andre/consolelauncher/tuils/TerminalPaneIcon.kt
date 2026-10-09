package ohi.andre.consolelauncher.tuils

import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme

/** The embedded Termux chrome's 12dp tinted icons inside a 48dp action target. */
internal object TerminalPaneIcon {
    fun apply(view: TextView, drawableId: Int) {
        val icon = ContextCompat.getDrawable(view.context, drawableId) ?: return
        val wrapped = DrawableCompat.wrap(icon).mutate()
        DrawableCompat.setTint(wrapped, ThemeColorResolver.color(Theme.workspace_control_text_color))
        val size = Tuils.dpToPx(view.context, 12)
        wrapped.setBounds(0, 0, size, size)
        view.text = ""
        view.compoundDrawablePadding = 0
        view.setCompoundDrawables(wrapped, null, null, null)
        view.gravity = Gravity.CENTER
        val side = Tuils.dpToPx(view.context, 18)
        view.setPadding(side, 0, side, Tuils.dpToPx(view.context, 28))
        TooltipCompat.setTooltipText(view, view.contentDescription)
    }
}
