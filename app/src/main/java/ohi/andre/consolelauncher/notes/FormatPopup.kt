package ohi.andre.consolelauncher.notes

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.sidebar.EdgeSidebar
import ohi.andre.consolelauncher.sidebar.bezelPath
import ohi.andre.consolelauncher.sidebar.drawBezelRim
import kotlin.math.roundToInt

/**
 * The formatting rail that comes out of the screen edge while text is selected in a Markdown editor: heading,
 * link, redact, then the inline and line formats. It is drawn like the edge sidebar's dock and sits on the edge
 * the sidebar is not on, so it never covers the text being formatted.
 */
class FormatPopup(
    private val activity: Activity,
    private val theme: () -> RetuiTheme,
    private val editor: MarkdownEditText,
    private val onLink: () -> Unit,
    private val onRedact: () -> Unit
) {
    private class Item(val icon: Int, val label: Int, val action: () -> Unit)

    private val handler = Handler(Looper.getMainLooper())
    private val task = Runnable { show(main()) }
    private var popup: PopupWindow? = null

    /** Call from the editor's selection callback. */
    fun onSelection(start: Int, end: Int) {
        handler.removeCallbacks(task)
        if (start != end && editor.isFocused) handler.postDelayed(task, 80) else dismiss()
    }

    fun dismiss() {
        handler.removeCallbacks(task)
        popup?.dismiss(); popup = null
    }

    private fun format(format: MarkdownEditText.Format): () -> Unit = { editor.applyFormat(format); dismiss() }

    private fun main(): List<Item> = listOf(
        Item(R.drawable.remember_ic_format_title, R.string.notes_rememberactivity_heading_style_a29f1) { show(headings()) },
        Item(R.drawable.remember_ic_link, R.string.notes_rememberactivity_link_selected_text_2971b) { dismiss(); onLink() },
        Item(R.drawable.remember_ic_redact, R.string.notes_rememberactivity_redact_selected_text_bb190) { dismiss(); onRedact() },
        Item(R.drawable.remember_ic_format_bold, R.string.notes_rememberactivity_bold_19e07, format(MarkdownEditText.Format.BOLD)),
        Item(R.drawable.remember_ic_format_italic, R.string.notes_rememberactivity_italic_1616e, format(MarkdownEditText.Format.ITALIC)),
        Item(R.drawable.remember_ic_format_underlined, R.string.notes_rememberactivity_underline_39773, format(MarkdownEditText.Format.UNDERLINE)),
        Item(R.drawable.remember_ic_format_strikethrough, R.string.notes_rememberactivity_strikethrough_a93b9, format(MarkdownEditText.Format.STRIKE)),
        Item(R.drawable.remember_ic_format_code, R.string.notes_rememberactivity_code_adac6, format(MarkdownEditText.Format.CODE)),
        Item(R.drawable.remember_ic_format_checklist, R.string.notes_rememberactivity_checklist_61b29, format(MarkdownEditText.Format.CHECKLIST)),
        Item(R.drawable.remember_ic_format_bulleted, R.string.notes_rememberactivity_bulleted_list_dd870, format(MarkdownEditText.Format.BULLETS)),
        Item(R.drawable.remember_ic_format_numbered, R.string.notes_rememberactivity_numbered_list_8294e, format(MarkdownEditText.Format.NUMBERED)),
        Item(R.drawable.remember_ic_format_quote, R.string.notes_rememberactivity_quote_30902, format(MarkdownEditText.Format.QUOTE))
    )

    /** The heading button swaps the rail for the three levels, with a way back. */
    private fun headings(): List<Item> = listOf(
        Item(R.drawable.remember_ic_format_h1, R.string.notes_rememberactivity_heading_1_fdc7d, format(MarkdownEditText.Format.H1)),
        Item(R.drawable.remember_ic_format_h2, R.string.notes_rememberactivity_heading_2_6542d, format(MarkdownEditText.Format.H2)),
        Item(R.drawable.remember_ic_format_h3, R.string.notes_rememberactivity_heading_3_9694d, format(MarkdownEditText.Format.H3)),
        Item(R.drawable.ic_chevron_up, R.string.journal_format_back) { show(main()) }
    )

    private fun show(items: List<Item>) {
        if (editor.selectionStart == editor.selectionEnd || editor.windowToken == null) return
        dismiss()
        val t = theme()
        val right = !EdgeSidebar.onRightEdge(activity)
        // Fit the rail in the space above the keyboard: slots shrink before anything is cut off.
        val visible = Rect().also(editor::getWindowVisibleDisplayFrame)
        val shoulder = dp(14)
        val padding = dp(8)
        val slot = ((visible.height() - dp(24) - (shoulder + padding) * 2) / items.size).coerceIn(dp(34), dp(46))
        val width = dp(52)
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, shoulder + padding, 0, shoulder + padding)
            background = BezelDrawable(right, t.outputText, activity.resources.displayMetrics.density, shoulder.toFloat())
            items.forEach { item ->
                addView(ImageButton(activity).apply {
                    setImageResource(item.icon)
                    imageTintList = ColorStateList.valueOf(t.outputText)
                    scaleType = ImageView.ScaleType.CENTER
                    background = null
                    contentDescription = activity.getString(item.label)
                    setOnClickListener { item.action() }
                }, LinearLayout.LayoutParams(width - dp(8), slot))
            }
        }
        val height = slot * items.size + (shoulder + padding) * 2
        val y = visible.top + ((visible.height() - height) / 2).coerceAtLeast(0)
        popup = PopupWindow(rail, width, height, false).apply {
            animationStyle = 0
            isOutsideTouchable = false
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            showAtLocation(editor, Gravity.TOP or if (right) Gravity.END else Gravity.START, 0, y)
        }
    }

    /** The sidebar's bezel tab, the full height of the rail, growing out of the left or right screen edge. */
    private class BezelDrawable(private val right: Boolean, private val rim: Int, private val density: Float,
                                private val shoulder: Float) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val oval = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = b.width() - density * 4
            val corner = minOf(w / 2, density * 18)
            val f = minOf(shoulder, w - corner)
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            if (right) canvas.scale(-1f, 1f, b.width() / 2f, 0f)
            bezelPath(path, oval, w, f, b.height() - f, f, corner)
            paint.style = Paint.Style.FILL
            paint.color = Color.BLACK
            canvas.drawPath(path, paint)
            drawBezelRim(canvas, path, oval, paint, rim, density, w, f, b.height() - f, f, corner)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
}
