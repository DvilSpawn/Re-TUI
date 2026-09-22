package ohi.andre.consolelauncher.tuils

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import kotlin.math.max
import kotlin.math.min

class TerminalBorderDrawable(
    private val fillColor: Int,
    private val borderColor: Int,
    val strokeWidthPx: Int,
    private val radiusPx: Float,
    private val dashed: Boolean,
    private val dashLengthPx: Float,
    private val dashGapPx: Float,
    private val cyberdeck: Boolean = false,
    private val cyberdeckNotch: Boolean = true
) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColor
        strokeWidth = strokeWidthPx.toFloat()
        if (dashed && strokeWidthPx > 0 && dashLengthPx > 0f && dashGapPx > 0f) {
            pathEffect = DashPathEffect(
                floatArrayOf(max(1f, dashLengthPx), max(1f, dashGapPx)),
                0f
            )
        }
    }
    private val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(1f, strokeWidthPx / 2f)
        color = ThemeColorResolver.withMaxAlpha(borderColor, 95)
    }
    private val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val topCutouts = ArrayList<RectF>()
    private val bottomCutouts = ArrayList<RectF>()

    fun setCutouts(top: List<RectF>, bottom: List<RectF>) {
        topCutouts.clear()
        topCutouts.addAll(top)
        bottomCutouts.clear()
        bottomCutouts.addAll(bottom)
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        if (cyberdeck) {
            drawCyberpunk(canvas)
            return
        }

        val fillRect = RectF(b)
        canvas.drawRoundRect(fillRect, radiusPx, radiusPx, fillPaint)

        if (strokeWidthPx <= 0) {
            return
        }

        val inset = strokeWidthPx / 2f
        val strokeRect = RectF(
            b.left + inset,
            b.top + inset,
            b.right - inset,
            b.bottom - inset
        )
        canvas.drawRoundRect(strokeRect, radiusPx, radiusPx, strokePaint)

        if (topCutouts.isEmpty() && bottomCutouts.isEmpty()) {
            return
        }

        val cutoutHeight = max(strokeWidthPx * 3f, 6f)
        for (cutout in topCutouts) {
            canvas.drawRect(
                b.left + cutout.left,
                b.top.toFloat(),
                b.left + cutout.right,
                b.top + cutoutHeight,
                cutoutPaint
            )
        }
        for (cutout in bottomCutouts) {
            canvas.drawRect(
                b.left + cutout.left,
                b.bottom - cutoutHeight,
                b.left + cutout.right,
                b.bottom.toFloat(),
                cutoutPaint
            )
        }
    }

    private fun drawCyberpunk(canvas: Canvas) {
        val b = bounds
        val width = b.width().toFloat()
        val height = b.height().toFloat()
        if (width <= 0f || height <= 0f) {
            return
        }

        val left = b.left.toFloat()
        val top = b.top.toFloat()
        val right = b.right.toFloat()
        val bottom = b.bottom.toFloat()
        val maxCornerCut = max(20f, strokeWidthPx * 8f)
        val maxNotchDepth = max(12f, strokeWidthPx * 6f)
        val cornerCut = min(min(max(8f, height * 0.34f), width * 0.18f), maxCornerCut)
        val notchDepth = min(min(max(8f, height * 0.22f), width * 0.16f), maxNotchDepth)
        val notchHalfHeight = min(max(1.5f, height * 0.04f), 4f)
        val notchCenter = top + (height * 0.52f)

        val path = Path()
        path.moveTo(left, top)
        path.lineTo(right, top)
        path.lineTo(right, bottom - cornerCut)
        path.lineTo(right - cornerCut, bottom)
        path.lineTo(left, bottom)
        if (cyberdeckNotch) {
            path.lineTo(left, notchCenter + notchHalfHeight)
            path.lineTo(left + notchDepth, notchCenter + notchHalfHeight)
            path.lineTo(left + notchDepth, notchCenter - notchHalfHeight)
            path.lineTo(left, notchCenter - notchHalfHeight)
        }
        path.close()

        canvas.drawPath(path, fillPaint)
        if (strokeWidthPx > 0) {
            canvas.drawPath(path, strokePaint)
            drawCyberpunkDetails(canvas, left, top, right, bottom, width, height, cornerCut, if (cyberdeckNotch) notchDepth else 0f)
        }

        drawCutouts(canvas)
    }

    private fun drawCyberpunkDetails(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        width: Float,
        height: Float,
        cornerCut: Float,
        notchDepth: Float
    ) {
        if (width < 56f || height < 44f) {
            return
        }

        val inset = max(3f, strokeWidthPx * 2.2f)
        val topRailY = top + inset
        val bottomRailY = bottom - inset
        val leftRailStart = left + notchDepth + inset
        val leftRailEnd = min(right - cornerCut - inset, left + width * 0.42f)
        if (leftRailEnd > leftRailStart + 8f) {
            canvas.drawLine(leftRailStart, topRailY, leftRailEnd, topRailY, detailPaint)
        }

        val rightRailStart = max(left + width * 0.68f, right - cornerCut - width * 0.18f)
        val rightRailEnd = right - inset
        if (rightRailEnd > rightRailStart + 8f) {
            canvas.drawLine(rightRailStart, topRailY, rightRailEnd, topRailY, detailPaint)
        }

        if (height >= 34f) {
            val verticalX = right - inset
            canvas.drawLine(verticalX, top + height * 0.18f, verticalX, bottom - cornerCut - inset, detailPaint)
            canvas.drawLine(left + inset, bottomRailY, left + min(width * 0.22f, 76f), bottomRailY, detailPaint)
        }
    }

    private fun drawCutouts(canvas: Canvas) {
        val b = bounds
        if (topCutouts.isEmpty() && bottomCutouts.isEmpty()) {
            return
        }

        val cutoutHeight = max(strokeWidthPx * 4f, 10f)
        for (cutout in topCutouts) {
            canvas.drawRect(
                b.left + cutout.left,
                b.top.toFloat(),
                b.left + cutout.right,
                b.top + cutoutHeight,
                cutoutPaint
            )
        }
        for (cutout in bottomCutouts) {
            canvas.drawRect(
                b.left + cutout.left,
                b.bottom - cutoutHeight,
                b.left + cutout.right,
                b.bottom.toFloat(),
                cutoutPaint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        detailPaint.alpha = alpha
        cutoutPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        detailPaint.colorFilter = colorFilter
        cutoutPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
