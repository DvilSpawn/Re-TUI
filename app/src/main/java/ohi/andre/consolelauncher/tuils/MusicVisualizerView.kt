package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import androidx.annotation.Nullable

class MusicVisualizerView : View {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heights = FloatArray(BAR_COUNT)
    private val targets = FloatArray(BAR_COUNT)
    private val random = Random(1337L)

    private var playing = false
    private var lastFrameTime = 0L
    private var barColor = Color.parseColor("#66FF3B30")

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        paint.style = Paint.Style.FILL
        paint.color = barColor
        for (i in 0 until BAR_COUNT) {
            heights[i] = MIN_BAR
            targets[i] = MIN_BAR
        }
        alpha = 0.72f
    }

    fun setPlaying(playing: Boolean) {
        if (this.playing == playing) {
            return
        }

        this.playing = playing
        if (playing) {
            lastFrameTime = 0L
            postInvalidateDelayed(FRAME_DELAY_MS)
        } else {
            for (i in 0 until BAR_COUNT) {
                targets[i] = MIN_BAR
            }
            invalidate()
        }
    }

    fun setBarColor(color: Int) {
        barColor = Color.argb(140, Color.red(color), Color.green(color), Color.blue(color))
        paint.color = barColor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width
        val height = height
        if (width <= 0 || height <= 0) {
            return
        }

        val now = System.currentTimeMillis()
        if (lastFrameTime == 0L) {
            lastFrameTime = now
        }

        if (playing && now - lastFrameTime >= FRAME_DELAY_MS) {
            lastFrameTime = now
            updateTargets()
        }

        val spacing = width / BAR_COUNT.toFloat()
        val barWidth = max(4f, spacing * 0.72f)

        for (i in 0 until BAR_COUNT) {
            heights[i] += (targets[i] - heights[i]) * if (playing) 0.22f else 0.1f
            val left = i * spacing
            val right = left + barWidth
            val top = height * (1f - clamp(heights[i]))
            canvas.drawRect(left, top, right, height.toFloat(), paint)
        }

        if (playing && isShown) {
            postInvalidateDelayed(FRAME_DELAY_MS)
        } else if (!isCollapsed() && isCollapsing()) {
            postInvalidateDelayed(FRAME_DELAY_MS)
        }
    }

    private fun updateTargets() {
        for (i in 0 until BAR_COUNT) {
            var base = 0.18f + 0.64f * random.nextFloat()
            if (i % 7 == 0 || i % 11 == 0) {
                base += 0.15f * random.nextFloat()
            }
            targets[i] = clamp(base)
        }
    }

    private fun isCollapsed(): Boolean {
        for (height in heights) {
            if (height > MIN_BAR + 0.02f) {
                return false
            }
        }
        return true
    }

    private fun isCollapsing(): Boolean {
        for (target in targets) {
            if (target > MIN_BAR + 0.02f) {
                return false
            }
        }
        return true
    }

    private fun clamp(value: Float): Float =
        max(MIN_BAR, min(MAX_BAR, value))

    companion object {
        private const val BAR_COUNT = 36
        private const val MIN_BAR = 0.08f
        private const val MAX_BAR = 0.92f
        private const val FRAME_DELAY_MS = 66L
    }
}
