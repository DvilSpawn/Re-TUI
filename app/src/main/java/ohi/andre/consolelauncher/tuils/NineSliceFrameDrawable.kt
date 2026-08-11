package ohi.andre.consolelauncher.tuils

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable

class NineSliceFrameDrawable(frame: LoadedFrame, private val density: Float) : Drawable() {
    private val spec = frame.spec
    private val filter = spec.filtering == "linear"
    private val source = frame.bitmap
    private val slices = frame.slices
    private val x = intArrayOf(0, spec.leftPx, source.width - spec.rightPx, source.width)
    private val y = intArrayOf(0, spec.topPx, source.height - spec.bottomPx, source.height)
    private val paint = Paint().apply { isFilterBitmap = filter }
    private val shaders = arrayOfNulls<BitmapShader>(9)

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val horizontal = fitBorders(bounds.width().toFloat(), spec.leftDp * density, spec.rightDp * density)
        val vertical = fitBorders(bounds.height().toFloat(), spec.topDp * density, spec.bottomDp * density)
        val dx = floatArrayOf(bounds.left.toFloat(), bounds.left + horizontal.first, bounds.right - horizontal.second, bounds.right.toFloat())
        val dy = floatArrayOf(bounds.top.toFloat(), bounds.top + vertical.first, bounds.bottom - vertical.second, bounds.bottom.toFloat())

        drawStretch(canvas, 0, 0, dx[0], dy[0], dx[1], dy[1])
        drawStretch(canvas, 2, 0, dx[2], dy[0], dx[3], dy[1])
        drawStretch(canvas, 0, 2, dx[0], dy[2], dx[1], dy[3])
        drawStretch(canvas, 2, 2, dx[2], dy[2], dx[3], dy[3])
        drawEdge(canvas, 1, 0, dx[1], dy[0], dx[2], dy[1], spec.topMode, true)
        drawEdge(canvas, 2, 1, dx[2], dy[1], dx[3], dy[2], spec.rightMode, false)
        drawEdge(canvas, 1, 2, dx[1], dy[2], dx[2], dy[3], spec.bottomMode, true)
        drawEdge(canvas, 0, 1, dx[0], dy[1], dx[1], dy[2], spec.leftMode, false)
        if (spec.centerMode != "none") {
            drawRegion(canvas, 1, 1, RectF(dx[1], dy[1], dx[2], dy[2]), spec.centerMode, null)
        }
    }

    private fun drawEdge(canvas: Canvas, column: Int, row: Int, left: Float, top: Float, right: Float, bottom: Float, mode: String, horizontal: Boolean) {
        drawRegion(canvas, column, row, RectF(left, top, right, bottom), mode, horizontal)
    }

    private fun drawRegion(canvas: Canvas, column: Int, row: Int, destination: RectF, mode: String, horizontal: Boolean?) {
        if (destination.width() <= 0f || destination.height() <= 0f) return
        if (mode == "stretch") {
            drawStretch(canvas, column, row, destination.left, destination.top, destination.right, destination.bottom)
            return
        }

        val tile = slices[row][column]
        val scale = when (horizontal) {
            true -> destination.height() / tile.height
            false -> destination.width() / tile.width
            null -> tileScale()
        }.coerceAtLeast(0.01f)
        val scaleX = if (horizontal == false) destination.width() / tile.width else scale
        val scaleY = if (horizontal == true) destination.height() / tile.height else scale
        val shaderIndex = row * 3 + column
        val shader = shaders[shaderIndex] ?: BitmapShader(
            tile,
            if (horizontal == false) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT,
            if (horizontal == true) Shader.TileMode.CLAMP else Shader.TileMode.REPEAT
        ).also { shaders[shaderIndex] = it }
        shader.setLocalMatrix(Matrix().apply {
            setScale(scaleX, scaleY)
            postTranslate(destination.left, destination.top)
        })
        paint.shader = shader
        canvas.drawRect(destination, paint)
        paint.shader = null
    }

    private fun tileScale(): Float {
        val candidates = listOf(
            spec.leftDp * density / spec.leftPx,
            spec.topDp * density / spec.topPx,
            spec.rightDp * density / spec.rightPx,
            spec.bottomDp * density / spec.bottomPx
        ).filter { it > 0f }
        return candidates.firstOrNull() ?: 1f
    }

    private fun drawStretch(canvas: Canvas, column: Int, row: Int, left: Float, top: Float, right: Float, bottom: Float) {
        if (right <= left || bottom <= top) return
        canvas.drawBitmap(
            source,
            android.graphics.Rect(x[column], y[row], x[column + 1], y[row + 1]),
            RectF(left, top, right, bottom),
            paint
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha.coerceIn(0, 255)
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        internal fun fitBorders(available: Float, first: Float, second: Float): Pair<Float, Float> {
            val total = first + second
            if (total <= available || total <= 0f) return first to second
            val scale = available / total
            return first * scale to second * scale
        }
    }
}

class FramedDrawable(
    private val fallback: Drawable,
    frame: NineSliceFrameDrawable
) : LayerDrawable(arrayOf(fallback, frame)) {
    fun setCutouts(top: List<RectF>, bottom: List<RectF>) {
        (fallback as? TerminalBorderDrawable)?.setCutouts(top, bottom)
    }
}
