package ohi.andre.consolelauncher.notes

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView

/** Consume a file-link tap before TextView can also dispatch its background click. */
object NotesPaneLinks {
    fun bind(view: TextView) {
        view.setOnClickListener { LauncherNotes.open(it.context) }
        var downX = 0f
        var downY = 0f
        var dragged = false
        val slop = android.view.ViewConfiguration.get(view.context).scaledTouchSlop
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragged = false }
                MotionEvent.ACTION_MOVE -> if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) dragged = true
                MotionEvent.ACTION_UP -> if (dragged) return@setOnTouchListener true
            }
            val layout = view.layout
            val text = view.text as? Spanned
            if (event.actionMasked != MotionEvent.ACTION_UP || layout == null || text == null) {
                false
            } else {
                val x = event.x - view.totalPaddingLeft + view.scrollX
                val y = event.y - view.totalPaddingTop + view.scrollY
                val line = layout.getLineForVertical(y.toInt())
                if (y < 0 || y >= layout.height || x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
                    false
                } else {
                    val offset = layout.getOffsetForHorizontal(line, x)
                    val link = text.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
                    link?.onClick(view)
                    link != null
                }
            }
        }
    }
}
