package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.annotation.Nullable

class OutlineTextView : AppCompatTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun draw(canvas: Canvas) {
        val drawTimes = OutlineTextView.drawPasses(tag)

        for (c in 0 until drawTimes) {
            super.draw(canvas)
        }
    }

    companion object {
        @JvmField var SHADOW_TAG: String = "hasShadow"
        @JvmField var redrawTimes: Int = 1
        // Bound work even for hand-edited settings, at both rendering entry points.
        internal fun drawPasses(tag: Any?): Int = if (tag == null) 1 else redrawTimes.coerceIn(1, 8)
    }
}
