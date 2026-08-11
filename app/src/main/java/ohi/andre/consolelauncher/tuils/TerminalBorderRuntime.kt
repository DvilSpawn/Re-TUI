package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings

object TerminalBorderRuntime {
    internal fun drawsBorder(borderEnabled: Boolean, cyberdeck: Boolean, dashed: Boolean): Boolean =
        borderEnabled && (cyberdeck || dashed)

    internal fun drawsTabBorder(alwaysBorder: Boolean, cyberdeck: Boolean, dashed: Boolean, alpha: Int): Boolean =
        alpha > 0 && (alwaysBorder || cyberdeck || dashed)

    fun panelDrawable(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        strokeDp: Float,
        radiusDp: Int,
        dashed: Boolean,
        cyberdeckNotch: Boolean = true,
        borderEnabled: Boolean = true
    ): TerminalBorderDrawable {
        return panelDrawablePx(
            context,
            fillColor,
            borderColor,
            strokeDp,
            Tuils.dpToPx(context, radiusDp.toFloat()).toFloat(),
            dashed,
            cyberdeckNotch,
            borderEnabled
        )
    }

    fun panelDrawablePx(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        strokeDp: Float,
        radiusPx: Float,
        dashed: Boolean,
        cyberdeckNotch: Boolean = true,
        borderEnabled: Boolean = true
    ): TerminalBorderDrawable {
        val cyberdeck = AppearanceSettings.cyberdeckMode()
        val stroke = if (!drawsBorder(borderEnabled, cyberdeck, dashed)) {
            0
        } else if (cyberdeck) {
            max(1, Tuils.dpToPx(context, strokeDp).toInt())
        } else {
            max(1, Tuils.dpToPx(context, AppearanceSettings.dashedBorderStrokeWidthDp(strokeDp / 1.5f)).toInt())
        }
        return TerminalBorderDrawable(
            fillColor,
            borderColor,
            stroke,
            if (cyberdeck) 0f else radiusPx,
            dashed && !cyberdeck,
            Tuils.dpToPx(context, AppearanceSettings.dashLength().toFloat()).toFloat(),
            Tuils.dpToPx(context, AppearanceSettings.dashGap().toFloat()).toFloat(),
            cyberdeck,
            cyberdeckNotch
        )
    }

    fun tabDrawable(context: Context, fillColor: Int): Drawable {
        return tabDrawable(context, fillColor, AppearanceSettings.terminalHeaderTabBorderColor())
    }

    fun tabDrawable(context: Context, fillColor: Int, borderColor: Int, alwaysBorder: Boolean = false): Drawable {
        if (AppearanceSettings.cyberdeckMode()) {
            return TerminalBorderDrawable(
                fillColor,
                borderColor,
                max(1, Tuils.dpToPx(context, 1.2f).toInt()),
                0f,
                false,
                0f,
                0f,
                true
            )
        }

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = Tuils.dpToPx(context, AppearanceSettings.headerCornerRadius().toFloat())
        bg.setColor(fillColor)
        val dashed = AppearanceSettings.dashedBorders()
        if (drawsTabBorder(alwaysBorder, false, dashed, Color.alpha(borderColor))) {
            val stroke = max(1, Tuils.dpToPx(context, AppearanceSettings.dashedBorderStrokeWidthDp()).toInt())
            val dashLength = AppearanceSettings.dashLength()
            val dashGap = AppearanceSettings.dashGap()
            if (dashed && dashLength > 0 && dashGap > 0) {
                bg.setStroke(
                    stroke,
                    borderColor,
                    Tuils.dpToPx(context, dashLength).toFloat(),
                    Tuils.dpToPx(context, dashGap).toFloat()
                )
            } else {
                bg.setStroke(stroke, borderColor)
            }
        } else {
            bg.setStroke(0, Color.TRANSPARENT)
        }
        return bg
    }

    fun bind(panel: View?, vararg cutoutViews: View?) {
        val border = panel ?: return
        val drawable = border.background as? TerminalBorderDrawable ?: return
        val views = cutoutViews.filterNotNull()
        if (views.isEmpty()) {
            drawable.setCutouts(emptyList(), emptyList())
            return
        }
        val runnable = CutoutRunnable(border, drawable, views)
        border.post(runnable)
        for (view in views) {
            view.post(runnable)
        }
    }

    private class CutoutRunnable(
        private val panel: View,
        private val drawable: TerminalBorderDrawable,
        private val cutoutViews: List<View>
    ) : Runnable {
        override fun run() {
            if (panel.width <= 0 || panel.height <= 0) {
                return
            }
            val panelLocation = IntArray(2)
            panel.getLocationOnScreen(panelLocation)
            val gutter = Tuils.dpToPx(panel.context, 6)
            val overlapSlop = Tuils.dpToPx(panel.context, 12)
            val cutoutHeight = max(drawable.strokeWidthPx * 4, Tuils.dpToPx(panel.context, 10)).toFloat()
            val topOut = ArrayList<RectF>()
            val bottomOut = ArrayList<RectF>()

            for (view in cutoutViews) {
                if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) {
                    continue
                }
                val localBounds = descendantBounds(panel, view)
                val childLocation = IntArray(2)
                if (localBounds == null) {
                    view.getLocationOnScreen(childLocation)
                }
                val relativeTop = localBounds?.top ?: (childLocation[1] - panelLocation[1])
                val relativeBottom = localBounds?.bottom ?: (relativeTop + view.height)
                val left = (localBounds?.left ?: (childLocation[0] - panelLocation[0])) - gutter
                val right = (localBounds?.right ?: (childLocation[0] - panelLocation[0] + view.width)) + gutter
                val cutout = RectF(
                    max(0, left).toFloat(),
                    0f,
                    min(panel.width, right).toFloat(),
                    cutoutHeight
                )
                if (relativeBottom >= -overlapSlop && relativeTop <= overlapSlop) {
                    topOut.add(cutout)
                } else if (relativeTop <= panel.height + overlapSlop && relativeBottom >= panel.height - overlapSlop) {
                    bottomOut.add(
                        RectF(
                            cutout.left,
                            0f,
                            cutout.right,
                            cutoutHeight
                        )
                    )
                }
            }
            drawable.setCutouts(
                topOut,
                bottomOut
            )
        }

        private fun descendantBounds(panel: View, child: View): Rect? {
            val group = panel as? ViewGroup ?: return null
            var parent = child.parent
            while (parent != null && parent !== group) {
                parent = parent.parent
            }
            if (parent !== group) return null
            return Rect(0, 0, child.width, child.height).also {
                group.offsetDescendantRectToMyCoords(child, it)
            }
        }
    }
}
