package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ScrollView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils

/** Reserve the live prompt before allocating the scrollable keypad, in the same measure pass. */
class CalculatorKeypadScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.AT_MOST) return
        val pane = (parent as? View)?.findViewById<View>(R.id.calculator_main_pane) ?: return
        val input = pane.findViewById<View>(R.id.calculator_expression) ?: return
        val prompt = input.parent as View
        prompt.measure(MeasureSpec.makeMeasureSpec((measuredWidth - pane.paddingLeft - pane.paddingRight).coerceAtLeast(0),
            MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val available = MeasureSpec.getSize(heightMeasureSpec)
        val promptHeight = prompt.measuredHeight + pane.paddingTop + pane.paddingBottom
        val tallestKey = (Tuils.dpToPx(context, 48) * 5 + 3) / 4
        val terminalHeight = minOf(maxOf(Tuils.dpToPx(context, 120) + pane.paddingTop + pane.paddingBottom, promptHeight),
            maxOf(promptHeight, available - tallestKey))
        val dockHeight = minOf(measuredHeight, (available - terminalHeight).coerceAtLeast(0))
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dockHeight, MeasureSpec.EXACTLY))
    }
}
