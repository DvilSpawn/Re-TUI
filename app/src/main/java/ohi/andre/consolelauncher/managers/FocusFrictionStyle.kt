package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

object FocusFrictionStyle {
    fun buttonFill(): Int = LauncherSettings.getColor(Suggestions.cmd_background_color)

    fun buttonText(): Int {
        val configured = LauncherSettings.get(Suggestions.cmd_text_color)
        if (!configured.isNullOrEmpty()) {
            return LauncherSettings.getColor(Suggestions.cmd_text_color)
        }
        return LauncherSettings.getColor(Suggestions.default_text_color)
    }

    fun bodyText(): Int = LauncherSettings.getColor(Theme.output_text_color)

    fun overlayBackground(): Int {
        if (LauncherSettings.getBoolean(Ui.system_wallpaper)) {
            return LauncherSettings.getColor(Theme.wallpaper_overlay_color)
        }
        val terminal = LauncherSettings.getColor(Theme.terminal_window_background_color)
        if (Color.alpha(terminal) > 0) {
            return ColorUtils.setAlphaComponent(terminal, 255)
        }
        val background = LauncherSettings.getColor(Theme.background_color)
        return if (Color.alpha(background) > 0) ColorUtils.setAlphaComponent(background, 255) else Color.BLACK
    }

    fun styleSticker(context: Context, view: TextView, filled: Boolean = true) {
        val fill = if (filled) buttonFill() else ColorUtils.setAlphaComponent(buttonFill(), 45)
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
            false
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
