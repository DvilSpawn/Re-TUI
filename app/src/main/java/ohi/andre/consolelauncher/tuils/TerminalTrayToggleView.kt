package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import ohi.andre.consolelauncher.R

/**
 * Stateless chrome/text helper for the terminal output tray toggle.
 *
 * UIManager still owns tray mode, expansion state, landscape state, and the click
 * behavior. This helper only applies the visual state to the TextView.
 */
object TerminalTrayToggleView {
    fun style(
        context: Context,
        toggle: TextView?,
        terminalOutputBorder: View?,
        outputHeaderNone: Boolean,
        outputHeaderArrowsOnly: Boolean,
        outputColor: Int,
        outputHeaderTextSize: Int,
        tabBackgroundColor: Int,
        onClick: () -> Unit
    ) {
        if (toggle == null) return
        if (outputHeaderNone) {
            hide(toggle)
            return
        }

        toggle.visibility = View.VISIBLE
        toggle.setTextColor(outputColor)
        toggle.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        toggle.textSize = outputHeaderTextSize.toFloat()
        toggle.gravity = Gravity.CENTER
        if (outputHeaderArrowsOnly) {
            toggle.minWidth = Tuils.dpToPx(context, 48)
            toggle.setPadding(
                Tuils.dpToPx(context, 9),
                Tuils.dpToPx(context, 2),
                Tuils.dpToPx(context, 9),
                Tuils.dpToPx(context, 2)
            )
        } else {
            toggle.minWidth = Tuils.dpToPx(context, 130)
            toggle.setPadding(
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 2),
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 2)
            )
        }
        toggle.background = TerminalBorderRuntime.tabDrawable(context, tabBackgroundColor, FrameTarget.OUTPUT)
        TerminalBorderRuntime.bind(terminalOutputBorder, toggle)
        toggle.setOnClickListener { onClick() }
    }

    fun updateText(
        context: Context,
        toggle: TextView?,
        outputHeaderNone: Boolean,
        landscapeLayoutActive: Boolean,
        terminalTrayExpanded: Boolean,
        outputHeaderArrowsOnly: Boolean,
        outputTrayNativeMode: Boolean,
        outputTrayAutoMode: Boolean,
        outputColor: Int
    ) {
        if (toggle == null) return
        if (outputHeaderNone) {
            toggle.text = ""
            hide(toggle)
            return
        }
        toggle.visibility = View.VISIBLE

        if (landscapeLayoutActive) {
            toggle.foreground = null
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            if (!TextUtils.equals(toggle.text, "OUTPUT")) {
                toggle.text = "OUTPUT"
            }
            return
        }

        val collapsed = !terminalTrayExpanded
        if (outputHeaderArrowsOnly) {
            toggle.text = ""
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            toggle.foregroundGravity = Gravity.CENTER
            toggle.foreground = outputHeaderArrow(context, collapsed, outputColor)
            return
        }

        toggle.foreground = null
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        val text = when {
            outputTrayNativeMode -> "OUTPUT"
            outputTrayAutoMode -> if (terminalTrayExpanded) "OUTPUT AUTO v" else "OUTPUT AUTO ^"
            else -> if (terminalTrayExpanded) "OUTPUT v" else "OUTPUT ^"
        }
        if (!TextUtils.equals(toggle.text, text)) {
            toggle.text = text
        }
    }

    private fun hide(toggle: TextView) {
        toggle.visibility = View.GONE
        toggle.setOnClickListener(null)
        toggle.foreground = null
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
    }

    private fun outputHeaderArrow(context: Context, collapsed: Boolean, color: Int): Drawable? {
        var drawable = ContextCompat.getDrawable(
            context,
            if (collapsed) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        ) ?: return null
        drawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(drawable, color)
        val size = Tuils.dpToPx(context, 18)
        drawable.setBounds(0, 0, size, size)
        return drawable
    }
}
