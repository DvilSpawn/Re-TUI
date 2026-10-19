package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.Toast
import ohi.andre.consolelauncher.R
import kotlin.math.abs

internal enum class ToolbarGestureDirection {
    UP, DOWN, LEFT, RIGHT;

    companion object {
        fun fromDrag(dx: Float, dy: Float, threshold: Float): ToolbarGestureDirection? {
            if (maxOf(abs(dx), abs(dy)) < threshold) return null
            return if (abs(dx) > abs(dy)) {
                if (dx > 0) RIGHT else LEFT
            } else if (dy > 0) DOWN else UP
        }
    }
}

/** Owns its touch stream so dragging never scrolls the terminal underneath it. */
internal class ToolbarGestureButton(context: Context, private val execute: (ToolbarGestureDirection) -> Unit) : ImageButton(context) {
    private var downX = 0f
    private var downY = 0f
    private var armed = false
    private var direction: ToolbarGestureDirection? = null
    private val threshold = 24 * resources.displayMetrics.density
    private val arm = Runnable {
        armed = true
        isPressed = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    init {
        setImageResource(R.drawable.ic_toolbar_gesture_24)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        scaleType = ScaleType.FIT_CENTER
        val padding = resources.getDimensionPixelSize(R.dimen.tools_padding)
        setPadding(padding, padding, padding, padding)
        contentDescription = context.getString(R.string.toolbar_gesture_hint)
        isClickable = true
        isFocusable = true
        val labels = listOf(R.string.setting_toolbar_gesture_up, R.string.setting_toolbar_gesture_down,
            R.string.setting_toolbar_gesture_left, R.string.setting_toolbar_gesture_right)
        ToolbarGestureDirection.entries.forEachIndexed { index, direction ->
            androidx.core.view.ViewCompat.addAccessibilityAction(this, context.getString(labels[index])) { _, _ ->
                execute(direction)
                true
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                downX = event.x
                downY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                postDelayed(arm, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val next = ToolbarGestureDirection.fromDrag(event.x - downX, event.y - downY, threshold)
                if (!armed && next != null) removeCallbacks(arm)
                if (armed && next != direction) {
                    direction = next
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
            MotionEvent.ACTION_UP -> {
                val action = if (armed) ToolbarGestureDirection.fromDrag(event.x - downX, event.y - downY, threshold) else null
                val tap = !armed && ToolbarGestureDirection.fromDrag(event.x - downX, event.y - downY, threshold) == null
                reset()
                if (action != null) execute(action) else if (tap) performClick()
            }
            MotionEvent.ACTION_CANCEL -> reset()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        Toast.makeText(context, R.string.toolbar_gesture_hint, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun reset() {
        removeCallbacks(arm)
        armed = false
        direction = null
        isPressed = false
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
}
