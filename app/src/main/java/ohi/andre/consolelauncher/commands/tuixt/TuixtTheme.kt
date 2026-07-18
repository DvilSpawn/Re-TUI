package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
import ohi.andre.consolelauncher.tuils.Tuils

object TuixtTheme {
    @JvmStatic
    fun borderColor(): Int = AppearanceSettings.terminalBorderColor()

    @JvmStatic
    fun accentColor(): Int = LauncherSettings.getColor(Theme.apps_drawer_text_color)

    @JvmStatic
    fun textColor(): Int = LauncherSettings.getColor(Theme.output_text_color)

    @JvmStatic
    fun surfaceColor(): Int = AppearanceSettings.terminalHeaderBackground()

    @JvmStatic
    fun overlayColor(): Int = LauncherSettings.getColor(Theme.wallpaper_overlay_color)

    @JvmStatic
    fun styleScreen(context: Context, view: View) {
        view.setBackgroundColor(overlayColor())
        if (AppearanceSettings.crtFilter()) {
            val overlay = CrtOverlayDrawable(context)
            overlay.setAccentColor(textColor())
            view.foreground = overlay
        }
    }

    @JvmStatic
    fun stylePanel(context: Context, view: View) {
        view.background = rect(context, surfaceColor(), borderColor(), 1.5f)
    }

    @JvmStatic
    fun styleHeader(context: Context, view: TextView) {
        view.setTextColor(accentColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 15f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 12f), dp(context, 3f), dp(context, 12f), dp(context, 3f))
        view.background = rect(context, surfaceColor(), borderColor(), 1.5f, AppearanceSettings.headerCornerRadius())
    }

    @JvmStatic
    fun styleListItem(context: Context, view: TextView, selected: Boolean) {
        val fill = if (selected) selectionColor() else ColorUtils.setAlphaComponent(surfaceColor(), 210)
        val text = if (selected) surfaceColor() else textColor()
        view.setTextColor(text)
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 15f
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        view.minHeight = dp(context, 48f)
        view.background = rect(context, fill, borderColor(), 1.25f)
    }

    @JvmStatic
    fun styleInput(context: Context, view: EditText) {
        view.setTextColor(textColor())
        view.setHintTextColor(ColorUtils.setAlphaComponent(textColor(), 150))
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.setSingleLine(false)
        view.setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
        view.background = rect(context, ColorUtils.setAlphaComponent(surfaceColor(), 220), borderColor(), 1.25f)
    }

    @JvmStatic
    fun styleButton(context: Context, view: TextView, primary: Boolean) {
        view.setTextColor(if (primary) surfaceColor() else textColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 14f), dp(context, 8f), dp(context, 14f), dp(context, 8f))
        view.background = rect(context, if (primary) selectionColor() else surfaceColor(), borderColor(), 1.25f)
    }

    @JvmStatic
    fun styleToggle(context: Context, view: TextView, checked: Boolean) {
        view.text = if (checked) "ON" else "OFF"
        view.setTextColor(if (checked) surfaceColor() else textColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 18f), dp(context, 9f), dp(context, 18f), dp(context, 9f))
        view.minWidth = dp(context, 76f)
        view.background = rect(context, if (checked) selectionColor() else surfaceColor(), borderColor(), 1.25f)
    }

    @JvmStatic
    fun styleColorPreview(context: Context, view: View, color: Int) {
        view.background = rect(context, color, borderColor(), 1.25f)
    }

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float): Drawable =
        rect(context, fill, stroke, strokeDp, AppearanceSettings.dashedBorderCornerRadius())

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float, radiusDp: Int): Drawable {
        if (AppearanceSettings.cyberdeckMode()) {
            return TerminalBorderDrawable(
                fill,
                stroke,
                max(1, dp(context, strokeDp)),
                0f,
                false,
                0f,
                0f,
                true,
                false
            )
        }

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = dp(context, radiusDp.toFloat()).toFloat()
        if (AppearanceSettings.dashedBorders()) {
            val strokePx = max(
                1,
                dp(context, AppearanceSettings.dashedBorderStrokeWidthDp(strokeDp / 1.5f).toFloat())
            )
            val dashLength = AppearanceSettings.dashLength()
            val dashGap = AppearanceSettings.dashGap()
            if (dashLength <= 0 || dashGap <= 0) {
                drawable.setStroke(strokePx, stroke)
            } else {
                drawable.setStroke(
                    strokePx,
                    stroke,
                    Tuils.dpToPx(context, dashLength.toFloat()),
                    Tuils.dpToPx(context, dashGap.toFloat())
                )
            }
        }
        drawable.setColor(fill)
        return drawable
    }

    @JvmStatic
    fun dp(context: Context, value: Float): Int = Tuils.dpToPx(context, value).toInt()

    private fun selectionColor(): Int =
        ColorUtils.blendARGB(accentColor(), -0x1, 0.42f)
}
