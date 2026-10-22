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
    data class Style(val cyberdeck: Boolean, val stroke: Float, val dash: Float, val gap: Float, val headerRadius: Int)

    internal fun drawsBorder(frameActive: Boolean, borderEnabled: Boolean, cyberdeck: Boolean, dashed: Boolean): Boolean =
        !frameActive && borderEnabled && (cyberdeck || dashed)

    internal fun drawsFrame(frameAvailable: Boolean, frameEnabled: Boolean): Boolean =
        frameAvailable && frameEnabled

    internal fun effectiveFillColor(frameActive: Boolean, fillColor: Int): Int =
        fillColor

    internal fun drawsTabBorder(frameActive: Boolean, alwaysBorder: Boolean, cyberdeck: Boolean, dashed: Boolean, alpha: Int): Boolean =
        !frameActive && alpha > 0 && (alwaysBorder || cyberdeck || dashed)

    fun panelDrawable(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        strokeDp: Float,
        radiusDp: Int,
        dashed: Boolean,
        cyberdeckNotch: Boolean = true,
        borderEnabled: Boolean = true,
        frameEnabled: Boolean = true,
        target: FrameTarget = FrameTarget.CONTROLS
    ): Drawable {
        return panelDrawablePx(
            context,
            fillColor,
            borderColor,
            strokeDp,
            Tuils.dpToPx(context, radiusDp.toFloat()).toFloat(),
            dashed,
            cyberdeckNotch,
            borderEnabled,
            frameEnabled,
            target
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
        borderEnabled: Boolean = true,
        frameEnabled: Boolean = true,
        target: FrameTarget = FrameTarget.CONTROLS,
        style: Style? = null
    ): Drawable {
        val availableFrame = FrameManager.drawable(context, target)
        val frame = availableFrame.takeIf { drawsFrame(it != null, frameEnabled) }
        val cyberdeck = frame == null && (style?.cyberdeck ?: AppearanceSettings.cyberdeckMode())
        val stroke = if (!drawsBorder(frame != null, borderEnabled, cyberdeck, dashed)) {
            0
        } else if (cyberdeck) {
            max(1, Tuils.dpToPx(context, strokeDp).toInt())
        } else {
            max(1, Tuils.dpToPx(context, (style?.stroke ?: AppearanceSettings.dashedBorderStrokeWidthDp(strokeDp / 1.5f))).toInt())
        }
        val fallback = TerminalBorderDrawable(
            effectiveFillColor(frame != null, fillColor),
            borderColor,
            stroke,
            if (cyberdeck) 0f else radiusPx,
            dashed && !cyberdeck,
            Tuils.dpToPx(context, (style?.dash ?: AppearanceSettings.dashLength().toFloat())).toFloat(),
            Tuils.dpToPx(context, (style?.gap ?: AppearanceSettings.dashGap().toFloat())).toFloat(),
            cyberdeck,
            cyberdeckNotch
        )
        return if (frame == null) fallback else FramedDrawable(fallback, frame)
    }

    fun tabDrawable(
        context: Context,
        fillColor: Int,
        target: FrameTarget = FrameTarget.CONTROLS
    ): Drawable {
        return tabDrawable(context, fillColor, AppearanceSettings.terminalHeaderTabBorderColor(), target = target)
    }

    fun tabDrawable(
        context: Context,
        fillColor: Int,
        borderColor: Int,
        alwaysBorder: Boolean = false,
        target: FrameTarget = FrameTarget.CONTROLS,
        style: Style? = null,
        draftDashed: Boolean = false
    ): Drawable {
        val frame = FrameManager.drawable(context, target)
        if (frame == null && (style?.cyberdeck ?: AppearanceSettings.cyberdeckMode())) {
            return TerminalBorderDrawable(
                fillColor,
                borderColor,
                max(1, Tuils.dpToPx(context, 1.2f).toInt()),
                0f,
                false,
                0f,
                0f,
                true,
                flipVertically = target == FrameTarget.OUTPUT
            )
        }

        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = Tuils.dpToPx(context, (style?.headerRadius ?: AppearanceSettings.headerCornerRadius()).toFloat())
        bg.setColor(effectiveFillColor(frame != null, fillColor))
        val dashed = if (style==null) AppearanceSettings.dashedBorders() else draftDashed
        if (drawsTabBorder(frame != null, alwaysBorder, false, dashed, Color.alpha(borderColor))) {
            val stroke = max(1, Tuils.dpToPx(context, (style?.stroke ?: AppearanceSettings.dashedBorderStrokeWidthDp())).toInt())
            val dashLength = (style?.dash?.toInt() ?: AppearanceSettings.dashLength())
            val dashGap = (style?.gap?.toInt() ?: AppearanceSettings.dashGap())
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
        return if (frame == null) bg else FramedDrawable(bg, frame)
    }

    fun customFrame(
        context: Context,
        fallback: Drawable,
        target: FrameTarget = FrameTarget.CONTROLS
    ): Drawable? = FrameManager.wrap(context, fallback, target)

    fun customFrameActive(context: Context, target: FrameTarget = FrameTarget.CONTROLS): Boolean =
        FrameManager.isActive(context, target)

    /** A detached presentation has no message loop until attached; bind after real layout instead. */
    internal fun bindPresentation(panel: View?, vararg cutoutViews: View?) {
        val border=panel ?: return
        val tag=ohi.andre.consolelauncher.R.id.home_border_layout_binding
        (border.getTag(tag) as? PresentationBinding)?.let { previous ->
            previous.views.forEach { it.removeOnLayoutChangeListener(previous.listener) }
        }
        border.setTag(tag,null)
        val drawable=(border.foreground as? TerminalBorderDrawable) ?: border.background as? TerminalBorderDrawable ?: return
        val views=cutoutViews.filterNotNull()
        val all=listOf(border)+views
        lateinit var listener: View.OnLayoutChangeListener
        fun bindWhenReady() {
            if(border.width<=0 || views.any { it.visibility!=View.GONE && it.width<=0 }) return
            CutoutRunnable(border,drawable,views).run()
            all.forEach { it.removeOnLayoutChangeListener(listener) }
            border.setTag(tag,null)
        }
        listener=View.OnLayoutChangeListener { _,_,_,_,_,_,_,_,_->bindWhenReady() }
        border.setTag(tag,PresentationBinding(all,listener))
        all.forEach { it.addOnLayoutChangeListener(listener) }
        if(all.all { it.isLaidOut && !it.isLayoutRequested }) bindWhenReady()
    }

    private data class PresentationBinding(val views: List<View>,val listener: View.OnLayoutChangeListener)

    fun bind(panel: View?, vararg cutoutViews: View?) {
        val border = panel ?: return
        val drawable = (border.foreground as? TerminalBorderDrawable) ?: border.background as? TerminalBorderDrawable ?: return
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
            // Headers can be siblings of their border. Shared ancestor coordinates also work detached.
            var ancestor: ViewGroup? = panel as? ViewGroup ?: panel.parent as? ViewGroup
            while(ancestor!=null) {
                var parent=child.parent
                while(parent!=null && parent!==ancestor) parent=parent.parent
                if(parent===ancestor) {
                    val panelBounds=Rect(0,0,panel.width,panel.height)
                    if(panel!==ancestor) ancestor.offsetDescendantRectToMyCoords(panel,panelBounds)
                    val common=ancestor
                    return Rect(0,0,child.width,child.height).also {
                        common.offsetDescendantRectToMyCoords(child,it)
                        it.offset(-panelBounds.left,-panelBounds.top)
                    }
                }
                ancestor=ancestor.parent as? ViewGroup
            }
            return null
        }
    }
}
