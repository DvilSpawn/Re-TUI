package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings

/** Shared visual contract for docked Launcher pills. */
object LauncherPillStyle {
    private const val SIZE_DP = 60

    fun apply(context: Context, pill: View) {
        pill.layoutParams?.let {
            it.width = Tuils.dpToPx(context, SIZE_DP).toInt()
            it.height = Tuils.dpToPx(context, SIZE_DP).toInt()
            pill.layoutParams = it
        }
        pill.background = TerminalBorderRuntime.panelDrawable(
            context,
            AppearanceSettings.terminalHeaderTabBackground(),
            AppearanceSettings.terminalHeaderTabBorderColor(),
            1.4f,
            3,
            AppearanceSettings.dashedBorders()
        )

        val contentColor = ColorStateList.valueOf(AppearanceSettings.moduleNameTextColor())
        when (pill) {
            is TextView -> {
                pill.setTextColor(contentColor)
                TextViewCompat.setCompoundDrawableTintList(pill, contentColor)
                pill.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
                pill.setPaddingDp(context, 4)
            }
            is ImageView -> {
                ImageViewCompat.setImageTintList(pill, contentColor)
                pill.setPaddingDp(context, 16)
            }
        }
    }

    private fun View.setPaddingDp(context: Context, paddingDp: Int) {
        val padding = Tuils.dpToPx(context, paddingDp).toInt()
        setPadding(padding, padding, padding, padding)
    }
}
