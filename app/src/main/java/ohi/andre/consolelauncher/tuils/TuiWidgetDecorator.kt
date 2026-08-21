package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings

object TuiWidgetDecorator {
    @JvmStatic
    fun decorateWidget(widgetRoot: View?, borderViewId: Int, labelViewId: Int) {
        decorateWidget(
            widgetRoot,
            borderViewId,
            labelViewId,
            0,
            AppearanceSettings.musicWidgetBorderColor(),
            AppearanceSettings.musicWidgetTextColor(),
            FrameTarget.MUSIC
        )
    }

    @JvmStatic
    fun decorateWidget(widgetRoot: View?, borderViewId: Int, labelViewId: Int, borderColor: Int, textColor: Int) {
        decorateWidget(widgetRoot, borderViewId, labelViewId, 0, borderColor, textColor, FrameTarget.MODULES)
    }

    @JvmStatic
    fun decorateWidget(
        widgetRoot: View?,
        borderViewId: Int,
        labelViewId: Int,
        closeViewId: Int,
        borderColor: Int,
        textColor: Int,
        target: FrameTarget = FrameTarget.MODULES
    ) {
        if (widgetRoot == null) {
            return
        }

        val context = widgetRoot.context
        val widgetBgColor = AppearanceSettings.terminalWindowBackground()
        val labelMaskColor = AppearanceSettings.terminalHeaderTabBackground()
        val useDashed = AppearanceSettings.dashedBorders()

        val borderView = widgetRoot.findViewById<View>(borderViewId)
        if (borderView != null) {
            borderView.background = panelDrawable(
                context,
                widgetBgColor,
                borderColor,
                1.5f,
                AppearanceSettings.moduleCornerRadius(),
                useDashed,
                target = target
            )
        }

        val widgetLabel = widgetRoot.findViewById<TextView>(labelViewId)
        if (widgetLabel != null) {
            widgetLabel.setTextColor(textColor)
            widgetLabel.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            widgetLabel.textSize = AppearanceSettings.moduleHeaderTextSize().toFloat()
            widgetLabel.background = TerminalBorderRuntime.tabDrawable(context, labelMaskColor, target)
        }

        if (borderView != null) {
            val closeView = if (closeViewId == 0) null else widgetRoot.findViewById<View?>(closeViewId)
            TerminalBorderRuntime.bind(borderView, widgetLabel, closeView)
        }
    }

    @JvmStatic
    fun getRowBackground(context: Context): Drawable =
        getRowBackground(context, AppearanceSettings.notificationWidgetBorderColor(), FrameTarget.NOTIFICATIONS)

    @JvmStatic
    fun getRowBackground(
        context: Context,
        borderColor: Int,
        target: FrameTarget = FrameTarget.NOTIFICATIONS
    ): Drawable {
        val widgetBgColor = AppearanceSettings.terminalWindowBackground()
        val rowBackground = ColorUtils.blendARGB(widgetBgColor, Color.BLACK, 0.22f)
        val strokeColor = ColorUtils.setAlphaComponent(borderColor, 140)

        return TerminalBorderRuntime.panelDrawable(
            context,
            rowBackground,
            strokeColor,
            1.2f,
            AppearanceSettings.moduleCornerRadius(),
            AppearanceSettings.dashedBorders(),
            target = target
        )
    }

    @JvmStatic
    fun panelDrawable(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        strokeDp: Float,
        radiusDp: Int,
        dashed: Boolean,
        target: FrameTarget = FrameTarget.MODULES
    ): Drawable = TerminalBorderRuntime.panelDrawable(
        context,
        fillColor,
        borderColor,
        strokeDp,
        radiusDp,
        dashed,
        target = target
    )

}
