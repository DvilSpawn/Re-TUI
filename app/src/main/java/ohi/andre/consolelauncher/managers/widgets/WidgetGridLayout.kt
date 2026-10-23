package ohi.andre.consolelauncher.managers.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
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

    /** Edit mode shows the cell corners, as Terminal mode does, and where the edited widget will land. */
    var gridColor: Int = 0
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    internal var landing: WidgetRecord? = null
        set(value) {
            if (field != value) { field = value; invalidate() }
        }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun dispatchDraw(canvas: Canvas) {
        if (gridColor != 0) {
            val half = resources.displayMetrics.density * 1.5f
            gridPaint.style = Paint.Style.FILL
            gridPaint.color = gridColor
            for (y in 0..height / cellSize) for (x in 0..columnCount) {
                val px = (paddingLeft + x * cellSize).toFloat().coerceIn(half, width - half)
                val py = (paddingTop + y * cellSize).toFloat().coerceIn(half, height - half)
                canvas.drawRect(px - half, py - half, px + half, py + half, gridPaint)
            }
            landing?.let {
                val inset = half * 2
                val left = paddingLeft + it.col * cellSize + inset
                val top = paddingTop + it.row * cellSize + inset
                val right = paddingLeft + (it.col + it.colSpan) * cellSize - inset
                val bottom = paddingTop + (it.row + it.rowSpan) * cellSize - inset
                gridPaint.color = (gridColor and 0x00ffffff) or 0x33000000
                canvas.drawRect(left, top, right, bottom, gridPaint)
                gridPaint.style = Paint.Style.STROKE
                gridPaint.strokeWidth = half
                gridPaint.color = gridColor or 0xff000000.toInt()
                canvas.drawRect(left, top, right, bottom, gridPaint)
            }
        }
        super.dispatchDraw(canvas)
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
