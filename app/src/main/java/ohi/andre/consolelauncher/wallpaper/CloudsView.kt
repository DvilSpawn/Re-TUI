package ohi.andre.consolelauncher.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.R
import kotlin.math.ceil

/** Original full-canvas layers, drifting at different depths on a portrait crop. */
class CloudsView(context: Context) : View(context) {
    companion object {
        const val SCENE = "clouds"
        const val FRAME_DELAY_MS = 33L
        private data class Sky(val layers: IntArray, val speeds: FloatArray, val colors: IntArray)
        // Source pixels per second; sky, moons and stars stay still.
        private val SKIES = listOf(
            Sky(intArrayOf(R.drawable.clouds_1_1, R.drawable.clouds_1_2, R.drawable.clouds_1_3, R.drawable.clouds_1_4),
                floatArrayOf(0f, 0.65f, 1.3f, 3.2f), intArrayOf(0x229DF2, 0xDAE7F3, 0x4BC5F4)),
            Sky(intArrayOf(R.drawable.clouds_2_1, R.drawable.clouds_2_2, R.drawable.clouds_2_3, R.drawable.clouds_2_4),
                floatArrayOf(0f, 0.65f, 1.3f, 3.2f), intArrayOf(0xB5B6EE, 0xA6B1EF, 0xE2BAD4)),
            Sky(intArrayOf(R.drawable.clouds_3_1, R.drawable.clouds_3_2, R.drawable.clouds_3_3, R.drawable.clouds_3_4),
                floatArrayOf(0f, 0f, 0.65f, 3.2f), intArrayOf(0x171628, 0x171F43, 0x092E7F)),
            Sky(intArrayOf(R.drawable.clouds_4_1, R.drawable.clouds_4_2, R.drawable.clouds_4_3, R.drawable.clouds_4_4),
                floatArrayOf(0f, 0f, 0.65f, 3.2f), intArrayOf(0x6DADC8, 0x81ADC8, 0x5A95C0)),
            Sky(intArrayOf(R.drawable.clouds_5_1, R.drawable.clouds_5_2, R.drawable.clouds_5_3, R.drawable.clouds_5_4, R.drawable.clouds_5_5),
                floatArrayOf(0f, 0f, 0.65f, 1.3f, 3.2f), intArrayOf(0x9DC2F6, 0xDEE6F3, 0xBAD2F6)),
            Sky(intArrayOf(R.drawable.clouds_6_1, R.drawable.clouds_6_2, R.drawable.clouds_6_3, R.drawable.clouds_6_4, R.drawable.clouds_6_5, R.drawable.clouds_6_6),
                floatArrayOf(0f, 0.4f, 0.65f, 1.3f, 2f, 3.2f), intArrayOf(0x998B8B, 0x8A8687, 0xB78F9A)),
            Sky(intArrayOf(R.drawable.clouds_7_1, R.drawable.clouds_7_2, R.drawable.clouds_7_3, R.drawable.clouds_7_4),
                floatArrayOf(0f, 0.65f, 1.3f, 3.2f), intArrayOf(0x946084, 0xAB7EA7, 0xAC6B89)),
            Sky(intArrayOf(R.drawable.clouds_8_1, R.drawable.clouds_8_6, R.drawable.clouds_8_2, R.drawable.clouds_8_3, R.drawable.clouds_8_4, R.drawable.clouds_8_5),
                floatArrayOf(0f, 0f, 0.65f, 1.3f, 2f, 3.2f), intArrayOf(0x7951E8, 0x763CB5, 0xAF66EC))
        )

        fun sceneNames(context: Context): List<String> =
            (1..SKIES.size).map { context.getString(R.string.wallpaper_clouds_scene, it) }
    }

    var sceneNumber = RetuiWallpaperSettings.cloudScene(context)
        private set
    private val sky get() = SKIES[sceneNumber - 1]
    private val bitmaps = mutableListOf<Bitmap>()
    private val paints = mutableListOf<Paint>()
    private var elapsedMs = 0L
    private val matrix = Matrix()
    private var lastFrameMs = 0L
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, FRAME_DELAY_MS)
        }
    }

    fun advance(nowMs: Long = SystemClock.uptimeMillis()) {
        if (lastFrameMs != 0L) elapsedMs += (nowMs - lastFrameMs).coerceAtLeast(0L)
        lastFrameMs = nowMs
    }

    fun resetClock() { lastFrameMs = 0L }

    fun setScene(number: Int) {
        if (number !in 1..SKIES.size || number == sceneNumber) return
        release()
        sceneNumber = number
        elapsedMs = 0L
        updateAnimation()
        invalidate()
    }

    private fun loadLayers() {
        if (bitmaps.isNotEmpty()) return
        for (resource in sky.layers) {
            val bitmap = BitmapFactory.decodeResource(resources, resource,
                BitmapFactory.Options().apply { inScaled = false })
            bitmaps += bitmap
            paints += Paint().apply {
                isFilterBitmap = false
                isAntiAlias = false
                // The supplied edge pixels aren't identical. Mirroring joins them exactly.
                shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.CLAMP)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        loadLayers()
        val scale = ceil(maxOf(width / 576f, height / 324f)).coerceAtLeast(1f)
        val left = (width - 576f * scale) / 2f
        val top = (height - 324f * scale) / 2f
        for (i in paints.indices) {
            val offset = (elapsedMs / 1000.0 * sky.speeds[i] % 1152.0).toFloat()
            matrix.setScale(scale, scale)
            matrix.postTranslate(left + offset * scale, top)
            paints[i].shader.setLocalMatrix(matrix)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paints[i])
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updateAnimation()
    }

    private fun updateAnimation() {
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus()
        removeCallbacks(frame)
        resetClock()
        if (running) post(frame)
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    fun release() {
        running = false
        removeCallbacks(frame)
        resetClock()
        paints.clear()
        bitmaps.forEach { it.recycle() }
        bitmaps.clear()
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors = WallpaperColors(
        Color.valueOf(sky.colors[0] or Color.BLACK), Color.valueOf(sky.colors[1] or Color.BLACK),
        Color.valueOf(sky.colors[2] or Color.BLACK)
    )
}
