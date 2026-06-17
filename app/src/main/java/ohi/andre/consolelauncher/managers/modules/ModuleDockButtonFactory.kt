package ohi.andre.consolelauncher.managers.modules

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Small UI helper for the home module dock buttons.
 *
 * UIManager owns module state, selection, and scrolling. This class only creates and
 * paints individual dock buttons so that the large manager does not carry all of the
 * button chrome details inline.
 */
object ModuleDockButtonFactory {
    fun create(
        context: Context,
        module: String?,
        onClick: (String?) -> Unit,
        onTouchDown: () -> Unit
    ): TextView {
        val button = TextView(context)
        button.text = if ("close" == module) "X" else ModuleManager.displayTitle(context, module)
        button.typeface = Tuils.getTypeface(context)
        button.setTypeface(button.typeface, Typeface.BOLD)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.isSingleLine = true
        button.gravity = Gravity.CENTER

        val padX = Tuils.dpToPx(context, if ("close" == module) 14 else 16)
        val padY = Tuils.dpToPx(context, 7)
        button.setPadding(padX, padY, padX, padY)

        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.setMargins(0, 0, Tuils.dpToPx(context, 8), 0)
        button.layoutParams = lp

        button.setOnClickListener { onClick(module) }
        button.setOnTouchListener { view: View, event: MotionEvent? ->
            when (event?.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    onTouchDown()
                    false
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                    true
                }
                else -> false
            }
        }

        return button
    }

    fun style(
        context: Context,
        button: TextView,
        selected: Boolean,
        backgroundColor: Int,
        borderColor: Int,
        textColor: Int,
        cornerRadius: Int,
        dashedBorders: Boolean
    ) {
        val bg = if (selected) {
            ColorUtils.blendARGB(backgroundColor, textColor, 0.25f)
        } else {
            backgroundColor
        }

        button.setTextColor(textColor)
        button.background = TerminalBorderRuntime.panelDrawable(
            context,
            bg,
            borderColor,
            1.2f,
            cornerRadius,
            dashedBorders
        )
    }
}
