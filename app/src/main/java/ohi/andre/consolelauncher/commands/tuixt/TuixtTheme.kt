package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils

object TuixtTheme {
    private var moduleButtonBackgroundPreview: Int? = null

    @JvmStatic
    fun borderColor(): Int = AppearanceSettings.terminalHeaderTabBorderColor()

    @JvmStatic
    fun accentColor(): Int = AppearanceSettings.moduleNameTextColor()

    @JvmStatic
    fun textColor(): Int = LauncherSettings.getColor(Theme.output_text_color)

    @JvmStatic
    fun surfaceColor(): Int = AppearanceSettings.terminalHeaderTabBackground()

    @JvmStatic
    fun overlayColor(): Int = LauncherSettings.getColor(Theme.settings_wallpaper_overlay_color)

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
        markSelection(view, selected)
        view.setTextColor(if (selected) selectionColor() else AppearanceSettings.moduleNameTextColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 15f
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        view.minHeight = dp(context, 48f)
        view.background = rect(
            context,
            moduleButtonBackgroundColor(),
            if (selected) selectionColor() else AppearanceSettings.moduleButtonBorderColor(),
            if (selected) 2f else 1.25f
        )
    }

    @JvmStatic
    fun styleInput(context: Context, view: EditText) {
        val text = textColor()
        val surface = surfaceColor()
        view.setTextColor(text)
        view.setHintTextColor(ColorUtils.setAlphaComponent(text, 150))
        view.highlightColor = ColorUtils.setAlphaComponent(ColorUtils.blendARGB(surface, text, 0.5f), 255)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.textCursorDrawable = GradientDrawable().apply {
                setColor(Color.rgb(255 - Color.red(surface), 255 - Color.green(surface), 255 - Color.blue(surface)))
                setSize(dp(context, 2f), dp(context, 24f))
            }
        }
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.setSingleLine(false)
        view.setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
        view.background = rect(context, ColorUtils.setAlphaComponent(surfaceColor(), 220), borderColor(), 1.25f)
    }

    @JvmStatic
    fun styleButton(context: Context, view: TextView, primary: Boolean) {
        view.setTextColor(if (primary) selectionColor() else AppearanceSettings.moduleNameTextColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 14f), dp(context, 8f), dp(context, 14f), dp(context, 8f))
        view.background = rect(
            context,
            moduleButtonBackgroundColor(),
            if (primary) selectionColor() else AppearanceSettings.moduleButtonBorderColor(),
            if (primary) 2f else 1.25f
        )
    }

    @JvmStatic
    fun styleChoice(context: Context, view: TextView, selected: Boolean) {
        styleButton(context, view, selected)
        markSelection(view, selected)
    }

    @JvmStatic
    fun styleToggle(context: Context, view: TextView, checked: Boolean) {
        view.text = if (checked) "ON" else "OFF"
        markSelection(view, checked)
        view.setTextColor(if (checked) selectionColor() else AppearanceSettings.moduleNameTextColor())
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 18f), dp(context, 9f), dp(context, 18f), dp(context, 9f))
        view.minWidth = dp(context, 76f)
        view.background = rect(
            context,
            moduleButtonBackgroundColor(),
            if (checked) selectionColor() else AppearanceSettings.moduleButtonBorderColor(),
            if (checked) 2f else 1.25f
        )
    }

    @JvmStatic
    fun styleIconButton(context: Context, view: View) {
        view.background = rect(
            context,
            moduleButtonBackgroundColor(),
            AppearanceSettings.moduleButtonBorderColor(),
            1.25f
        )
    }

    @JvmStatic
    fun previewModuleButtonBackground(color: Int) {
        moduleButtonBackgroundPreview = color
    }

    @JvmStatic
    fun clearModuleButtonBackgroundPreview() {
        moduleButtonBackgroundPreview = null
    }

    @JvmStatic
    fun styleColorPreview(context: Context, view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 4f).toFloat()
            setColor(color)
            setStroke(dp(context, 1.25f).coerceAtLeast(1), borderColor())
        }
    }

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float): Drawable =
        rect(context, fill, stroke, strokeDp, AppearanceSettings.dashedBorderCornerRadius())

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float, radiusDp: Int): Drawable {
        return TerminalBorderRuntime.panelDrawable(
            context,
            fill,
            stroke,
            strokeDp,
            radiusDp,
            AppearanceSettings.dashedBorders(),
            cyberdeckNotch = false,
            target = FrameTarget.SETTINGS
        )
    }

    @JvmStatic
    fun dp(context: Context, value: Float): Int = Tuils.dpToPx(context, value).toInt()

    private fun selectionColor(): Int =
        ColorUtils.blendARGB(accentColor(), -0x1, 0.42f)

    private fun moduleButtonBackgroundColor(): Int =
        moduleButtonBackgroundPreview ?: AppearanceSettings.moduleButtonBackgroundColor()

    private fun markSelection(view: TextView, selected: Boolean) {
        val label = view.text.toString().removePrefix("✓ ")
        view.text = if (selected) "✓ $label" else label
        view.isSelected = selected
    }
}
