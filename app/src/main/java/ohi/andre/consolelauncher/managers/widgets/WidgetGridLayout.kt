package ohi.andre.consolelauncher.managers.widgets

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout

/** Uniform cell tracks, including empty rows left by dragging. GridLayout collapses those gaps. */
class WidgetGridLayout(context: Context, attrs: AttributeSet? = null) : GridLayout(context, attrs) {
    var cellSize: Int = 1
        set(value) {
            if (field != value) { field = value.coerceAtLeast(1); requestLayout() }
        }

    internal var resizePreview: Pair<View, WidgetResizeRect>? = null
        set(value) {
            if (field != value) { field = value; requestLayout() }
        }

    class LayoutParams(val row: Int, rows: Int, val column: Int, columns: Int) :
        GridLayout.LayoutParams(spec(row, rows), spec(column, columns))

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams
            val preview = resizePreview?.takeIf { it.first === child }?.second
            child.measure(View.MeasureSpec.makeMeasureSpec((preview?.width ?: params.width).coerceAtLeast(1), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((preview?.height ?: params.height).coerceAtLeast(1), View.MeasureSpec.EXACTLY))
        }
        val height = if (childCount == 0) 0 else maxOf(rowCount * cellSize, resizePreview?.second?.bottom ?: 0)
        setMeasuredDimension(resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(maxOf(suggestedMinimumHeight, height), heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val params = child.layoutParams as? LayoutParams ?: continue
            val preview = resizePreview?.takeIf { it.first === child }?.second
            val left = preview?.left ?: (paddingLeft + params.column * cellSize + params.leftMargin)
            val top = preview?.top ?: (paddingTop + params.row * cellSize + params.topMargin)
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }
}
