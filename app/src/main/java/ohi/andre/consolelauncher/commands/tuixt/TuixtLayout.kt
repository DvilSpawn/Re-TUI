package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.max
import kotlin.math.min
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

object TuixtLayout {
    @JvmStatic
    fun addFoldAwareHost(context: Context, screen: FrameLayout, height: Int): FrameLayout {
        val gutterWidth = getLandscapeGutterWidth(context)
        if (gutterWidth <= 0) {
            return screen
        }

        val splitHost = LinearLayout(context)
        splitHost.orientation = LinearLayout.HORIZONTAL
        splitHost.clipChildren = false
        splitHost.clipToPadding = false
        screen.addView(
            splitHost,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        )

        val childHeight = if (height == ViewGroup.LayoutParams.MATCH_PARENT) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            ViewGroup.LayoutParams.WRAP_CONTENT
        }
        val leftPane = createPane(context)
        val rightPane = createPane(context)
        val gutter = View(context)

        splitHost.addView(leftPane, LinearLayout.LayoutParams(0, childHeight, 1f))
        splitHost.addView(gutter, LinearLayout.LayoutParams(gutterWidth, childHeight))
        splitHost.addView(rightPane, LinearLayout.LayoutParams(0, childHeight, 1f))

        return if (UIManager.DUO_LAYOUT_LEFT == UIManager.resolveSavedDuoSide(context)) {
            leftPane
        } else {
            rightPane
        }
    }

    private fun createPane(context: Context): FrameLayout {
        val pane = FrameLayout(context)
        pane.clipChildren = false
        pane.clipToPadding = false
        return pane
    }

    private fun getLandscapeGutterWidth(context: Context?): Int {
        if (context == null || !UIManager.isResponsiveLandscapeConfiguration(context.resources.configuration)) {
            return 0
        }

        val gutterMm = max(
            0,
            min(LauncherSettings.getInt(Ui.landscape_fold_gutter_mm), UIManager.MAX_LANDSCAPE_FOLD_GUTTER_MM)
        )
        if (gutterMm == 0) {
            return 0
        }

        var gutterPx = Tuils.mmToPx(context.resources.displayMetrics, gutterMm)
        val screenWidth = context.resources.displayMetrics.widthPixels
        if (screenWidth > 0) {
            gutterPx = min(gutterPx, screenWidth / 3)
        }
        return gutterPx
    }
}
