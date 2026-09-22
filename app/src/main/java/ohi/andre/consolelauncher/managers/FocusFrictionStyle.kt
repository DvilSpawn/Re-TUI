package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.TextView
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils

object FocusFrictionStyle {
    fun buttonFill(): Int = ThemeColorResolver.color(Theme.primary_button_background_color)

    fun buttonText(): Int = ThemeColorResolver.color(Theme.primary_button_text_color)

    fun bodyText(): Int = ThemeColorResolver.color(Theme.overlay_panel_text_color)

    fun overlayBackground(): Int = ThemeColorResolver.color(Theme.overlay_panel_background_color)

    fun styleSticker(context: Context, view: TextView, filled: Boolean = true) {
        val fill = if (filled) buttonFill() else ThemeColorResolver.withMaxAlpha(buttonFill(), 45)
        val text = if (filled) buttonText() else bodyText()
        view.setTextColor(text)
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.gravity = Gravity.CENTER
        view.textSize = 13f
        view.setPadding(dp(context, 14f), dp(context, 10f), dp(context, 14f), dp(context, 10f))
        view.background = TerminalBorderRuntime.panelDrawable(
            context,
            fill,
            if (filled) text else buttonFill(),
            1.5f,
            AppearanceSettings.moduleCornerRadius(),
            false,
            target = FrameTarget.SETTINGS
        )
    }

    fun vibrate(context: Context, pattern: LongArray) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    fun cancelVibration(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.cancel()
    }

    fun dp(context: Context, value: Float): Int = Tuils.dpToPx(context, value).toInt()
}
