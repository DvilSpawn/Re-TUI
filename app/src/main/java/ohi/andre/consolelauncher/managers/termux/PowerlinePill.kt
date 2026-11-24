package ohi.andre.consolelauncher.managers.termux

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * A powerline segment: a filled band with a chevron on its right; a segment that follows another has the matching
 * notch on its left, so a row of them reads as one prompt (`~/path ▶ branch ▶`).
 */
class PowerlinePill(color: Int, private val tip: Float, private val notched: Boolean, private val chevron: Boolean = true) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val l = b.left.toFloat(); val t = b.top.toFloat(); val r = b.right.toFloat(); val bottom = b.bottom.toFloat()
        val mid = (t + bottom) / 2f
        path.reset()
        path.moveTo(l, t)
        if (chevron) {
            path.lineTo(r - tip, t)
            path.lineTo(r, mid)
            path.lineTo(r - tip, bottom)
        } else {
            path.lineTo(r, t)
            path.lineTo(r, bottom)
        }
        path.lineTo(l, bottom)
        if (notched) path.lineTo(l + tip, mid)
        path.close()
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        /** Styles [view] as a segment in [color], with dark or light text, whichever reads on it. */
        fun style(view: TextView, color: Int, notched: Boolean) {
            val density = view.resources.displayMetrics.density
            val tip = 7f * density
            view.background = PowerlinePill(color or 0xFF000000.toInt(), tip, notched)
            view.setTextColor(if (luminance(color) > 0.5f) 0xFF15090C.toInt() else Color.WHITE)
            view.setShadowLayer(0f, 0f, 0f, 0)
            val side = (8 * density).toInt()
            view.setPadding(side + if (notched) tip.toInt() else 0, view.paddingTop, side + tip.toInt(), view.paddingBottom)
        }

        /** A flat band in [color] for a button (no chevron, it ends a row rather than leading on). */
        fun fill(view: TextView, color: Int) {
            val side = (8 * view.resources.displayMetrics.density).toInt()
            view.background = PowerlinePill(color or 0xFF000000.toInt(), 0f, notched = false, chevron = false)
            view.setTextColor(if (luminance(color) > 0.5f) 0xFF15090C.toInt() else Color.WHITE)
            view.setPadding(side, view.paddingTop, side, view.paddingBottom)
        }

        private fun luminance(color: Int) =
            (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)) / 255f
    }
}
