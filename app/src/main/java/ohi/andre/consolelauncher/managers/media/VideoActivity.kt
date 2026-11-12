package ohi.andre.consolelauncher.managers.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.presentation.HomeAppearance

/** What the video library and player share: the launcher's look, the list rows, and saved watch positions. */
abstract class VideoActivity : LocalizedComponentActivity() {
    protected class Row(val header: String?, val item: MediaLibrary.Item?, val n: Int, val folder: String? = null)

    protected open val font: Typeface by lazy { HomeAppearance.saved().font(this) }
    protected open val border by lazy { ThemeColorResolver.color(Theme.device_text_color) }
    protected open val ink by lazy { ThemeColorResolver.color(Theme.output_text_color) }
    protected open val sel by lazy { ThemeColorResolver.color(Theme.input_text_color) }
    protected open val head by lazy { ThemeColorResolver.color(Theme.time_text_color) }
    protected open val dim by lazy { ThemeColorResolver.color(Theme.session_info_text_color) }
    /** What sits behind the controls, and the colour of text on a highlighted control. Black on the video screens. */
    protected open val ground by lazy { Color.BLACK }
    protected open val onSelected by lazy { Color.BLACK }
    protected open val control by lazy { ink }
    protected val resume by lazy { getSharedPreferences("video_resume", MODE_PRIVATE) }

    /** Called after any control is used, so the player can restart its auto-hide. */
    protected open fun touched() {}

    protected fun videos() = MediaLibrary.items(this).filter { it.video }

    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    protected fun label(value: String = "", color: Int = ink, size: Float = 13f) = TextView(this).apply {
        text = value
        typeface = font
        textSize = size
        setTextColor(color)
        includeFontPadding = false
    }

    protected fun titled(value: String) = label(value, border).apply {
        setBackgroundColor(ground)
        setPadding(dp(6), 0, dp(6), 0)
        translationY = -dp(9).toFloat()
    }

    protected open fun outline(color: Int, fill: Int = Color.TRANSPARENT, radius: Int = 0): Drawable = GradientDrawable().apply {
        setColor(fill)
        setStroke(maxOf(1, dp(1)), color)
        cornerRadius = dp(radius).toFloat()
    }

    /** Looks [visual]dp tall but takes touches across the full 48dp, so the controls stay slim and easy to hit. */
    protected fun button(text: String, desc: String, width: Int, visual: Int, size: Float = 13f, action: () -> Unit) = label(text, control, size).apply {
        contentDescription = desc
        gravity = Gravity.CENTER
        tag = visual
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(TOUCH))
        setOnClickListener { action(); touched() }
        paint(this, false)
    }

    protected fun paint(b: TextView, on: Boolean, color: Int = control, outlined: Boolean = true) {
        val inset = (dp(TOUCH) - dp(b.tag as Int)) / 2
        b.setTextColor(if (on) onSelected else color)
        val shape: Drawable = when {
            on -> outline(color, color)
            outlined -> outline(color)
            else -> ColorDrawable(Color.TRANSPARENT)
        }
        b.background = InsetDrawable(shape, 0, inset, 0, inset)
    }

    /** The mockup's slider: a hairline and a small square handle, on the platform SeekBar for touch and TalkBack. */
    protected fun style(bar: SeekBar, color: Int) {
        bar.progressDrawable = object : Drawable() {
            private val paint = Paint().apply { this.color = color }
            override fun draw(canvas: Canvas) {
                val y = bounds.exactCenterY()
                canvas.drawRect(bounds.left.toFloat(), y, bounds.right.toFloat(), y + maxOf(1, dp(1)), paint)
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(filter: ColorFilter?) {}
            @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
        }
        bar.thumb = GradientDrawable().apply { setColor(color); setSize(dp(7), dp(7)) }
    }

    protected fun slider(desc: String) = SeekBar(this).apply {
        contentDescription = desc
        max = SLIDER_MAX
        setPadding(dp(4), 0, dp(4), 0)
        thumbOffset = 0
        splitTrack = false
        style(this, ink)
    }

    protected fun list() = ListView(this).apply {
        divider = null
        selector = ColorDrawable(Color.TRANSPARENT)
    }

    protected fun saved(uri: String): Pair<Long, Long> {
        val parts = resume.getString(uri, null)?.split('|') ?: return 0L to 0L
        return (parts.getOrNull(0)?.toLongOrNull() ?: 0L) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    protected inner class Rows : BaseAdapter() {
        var rows: List<Row> = emptyList()
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun isEnabled(position: Int) = rows[position].item != null || rows[position].folder != null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView as LinearLayout? ?: LinearLayout(parent.context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0)
                addView(label().apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(22), -2).apply { rightMargin = dp(10) })
                addView(label().apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label().apply { setPadding(dp(10), 0, 0, 0) })
                addView(label().apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(44), -2))
            }
            val row = rows[position]
            val cells = (0..3).map { view.getChildAt(it) as TextView }
            val item = row.item
            if (item == null) {
                view.layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(28))
                view.setBackgroundColor(Color.TRANSPARENT)
                cells[0].visibility = View.GONE
                cells[1].text = row.header
                cells[1].setTextColor(head)
                cells[2].setTextColor(head)
                cells[2].text = row.folder?.let { folder ->
                    val start = VideoMath.showStart(videos().filter { it.folder == folder }.map { saved(it.uri) })
                    getString(if (start.started) R.string.media_video_resume_show else R.string.media_video_start_show)
                }.orEmpty()
                cells[3].text = ""
                return view
            }
            val on = item.uri == nowPlaying
            val fg = if (on) Color.BLACK else ink
            view.layoutParams = android.widget.AbsListView.LayoutParams(-1, dp(44))
            view.setBackgroundColor(if (on) sel else Color.TRANSPARENT)
            val (pos, dur) = saved(item.uri)
            cells[0].visibility = View.VISIBLE
            cells[0].text = row.n.toString()
            cells[1].text = item.name
            cells[2].text = (if (dur > MARKED_DONE) dur else item.durationMs).let { if (it > 0) VideoMath.clock(it) else "" }
            cells[3].text = when {
                VideoMath.isDone(pos, dur) -> getString(R.string.media_video_done)
                dur > 0 && pos > 0 -> "${pos * 100 / dur}%"
                else -> ""
            }
            cells.forEach { it.setTextColor(fg) }
            return view
        }
    }

    companion object {
        /** The video loaded in the player, for marking it in the library; null when no player is open. */
        @Volatile var nowPlaying: String? = null
        const val EXTRA_URI = "uri"
        const val EXTRA_START_AT = "start_at"
        const val EXTRA_FORGET = "forget"
        const val KEY_LAST = "__last"
        /** Stored as both position and length when a never-opened video is marked watched. */
        const val MARKED_DONE = 1L
        const val SLIDER_MAX = 1000
        const val TOUCH = 48
        const val BAR = 29
        const val CENTER = 31
    }
}
