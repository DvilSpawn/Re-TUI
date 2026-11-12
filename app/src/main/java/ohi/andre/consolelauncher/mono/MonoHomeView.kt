package ohi.andre.consolelauncher.mono

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager
import ohi.andre.consolelauncher.managers.settings.MonoMode
import ohi.andre.consolelauncher.notes.QuickNoteStore
import ohi.andre.consolelauncher.notes.ordered
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mono mode's home: a two-colour terminal on its own grid. A status strip, a dithered banner with the date and
 * weather, one session pane holding notes, upcoming events, music and the commands run here with their output,
 * then the input line with the launcher's own search suggestions opening above it, and the most-used apps as
 * chips. Everything comes from the launcher's own data.
 */
@SuppressLint("ViewConstructor")
class MonoHomeView(context: Context, private val source: Source) : FrameLayout(context) {
    /** What the home shows and what its keys do, supplied by the launcher. */
    class Source(
        val directory: () -> String,
        val weather: () -> String?,
        /** The weather's symbol code, which picks the banner's scene. */
        val weatherSymbol: () -> String?,
        val music: () -> Music?,
        val notifications: () -> List<Action>,
        val reminders: () -> List<String>,
        /** The running or paused timer and stopwatch, already worded; empty when neither is set. */
        val clock: () -> List<String>,
        val chips: () -> List<Action>
    )
    class Music(val title: String, val artist: String?, val playing: Boolean)
    class Action(val label: String, val run: () -> Unit)

    private companion object {
        const val ANSWER_WINDOW_MS = 4_000L
        /** Mono's font_size_offset carries its own +3, so at the user's default offset this draws at 19sp. */
        const val BASE_TEXT_SP = 16f
    }

    private val ink = MonoMode.ink()
    private val paper = MonoMode.paper()
    private val face: Typeface = runCatching { Typeface.createFromAsset(context.assets, MonoMode.FONT_ASSET) }
        .getOrDefault(Typeface.MONOSPACE)
    private val density = resources.displayMetrics.density
    private val line = maxOf(2, (density).roundToInt())
    private val handler = Handler(Looper.getMainLooper())

    private val statusTime = text("", inverted = true)
    private val statusDir = text("", inverted = true).apply { gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.START }
    private val battery = BatteryGlyph()
    private val bannerDate = text("", inverted = false).apply { setBackgroundColor(paper); setPadding(dp(4), 0, dp(4), 0) }
    private val bannerWeather = text("", inverted = false).apply { setBackgroundColor(paper); setPadding(dp(4), 0, dp(4), 0) }
    private val session = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val sessionScroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(session) }
    private val banner = MonoBanner(context, ink, paper)
    private val sessionTitle = text("", inverted = false).apply { alpha = 0.55f }
    private val chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

    /** The input line; the launcher wires its suggestions and commands to these. */
    val input = android.widget.EditText(context).apply {
        background = null
        isSingleLine = true
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (android.os.Build.VERSION.SDK_INT >= 29) textCursorDrawable = GradientDrawable().apply { setColor(ink); setSize(dp(9), dp(18)) }
    }
    val prefix = text("$ ", inverted = false)

    /** Mono's look for the input line; call again after the terminal has styled it its own way. */
    fun styleInput() {
        input.typeface = face
        LauncherFontScale.setBaseSize(input, BASE_TEXT_SP, LauncherFontScale.offsetSp())
        input.setTextColor(ink)
        input.setHintTextColor(ink and 0x00FFFFFF or 0x88000000.toInt())
        input.setHint(R.string.mono_prompt_hint)
        input.setShadowLayer(0f, 0f, 0f, 0)
        RetuiThemeBridge.applyToKeyboardInput(input, source.directory(), "launcher")
        prefix.typeface = face
        LauncherFontScale.setBaseSize(prefix, BASE_TEXT_SP, LauncherFontScale.offsetSp())
        prefix.setTextColor(ink)
        prefix.setShadowLayer(0f, 0f, 0f, 0)
    }
    val submit = android.widget.ImageView(context).apply { visibility = GONE }
    /** Where the search suggestions go: between the session and the input line, only while typing. */
    val results = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM }
    private val resultsScroll = CappedScroll().apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        addView(results)
        visibility = GONE
    }

    /** Commands run here and what they printed, newest last. */
    private val transcript = ArrayDeque<Pair<Boolean, String>>()
    private var events: List<String> = emptyList()
    private val clockRows = ArrayList<TextView>()
    private var keyboardShown = false

    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            // A running timer or stopwatch counts in the session every second; otherwise a slow tick will do.
            val clock = source.clock()
            val counting = clock.isNotEmpty()
            // Only the timer rows' text changes each second; the session is rebuilt when the rows come or go.
            if (clock.size == clockRows.size && clock.isNotEmpty()) clock.forEachIndexed { i, value -> clockRows[i].text = value }
            else if (clock.size != clockRows.size) renderSession()
            handler.postDelayed(this, if (counting) 1_000L else 20_000L)
        }
    }

    init {
        setBackgroundColor(paper)
        isClickable = true
        // Home holds the focus itself, so the input line only takes it (and the keyboard) when tapped.
        isFocusable = true
        isFocusableInTouchMode = true
        // Focusable with no background of its own, so Android would otherwise wash it grey as "focused".
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }
        addView(column, LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // The keyboard lifts the input line and chips; the session gives up the room.
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            // Putting the keyboard away with nothing typed means done searching: close the suggestions too.
            val imeShown = ime.bottom > 0
            if (keyboardShown && !imeShown && input.text.isNullOrEmpty()) post { closeInput() }
            keyboardShown = imeShown
            insets
        }

        // Status strip: inverted, like the frame of a device.
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(6), dp(2), dp(6), dp(2))
            addView(text("RE:TUI", inverted = true))
            addView(statusDir, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8); marginEnd = dp(8) })
            addView(battery, LinearLayout.LayoutParams(dp(26), dp(12)).apply { marginEnd = dp(8) })
            addView(statusTime)
        }, LinearLayout.LayoutParams(-1, -2))

        // Banner: a dithered horizon carrying the date and weather.
        column.addView(FrameLayout(context).apply {
            background = box(paper)
            addView(banner, LayoutParams(-1, -1))
            addView(bannerDate, LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM).apply { setMargins(dp(8), 0, 0, dp(6)) })
            addView(bannerWeather, LayoutParams(-2, -2, Gravity.END or Gravity.BOTTOM).apply { setMargins(0, 0, dp(8), dp(6)) })
        }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(8) })

        // Session: notes, what's next, the modules, then the commands run here.
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = box(paper)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            addView(sessionTitle)
            addView(sessionScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })

        // Suggestions open here while typing, pushing the session's bottom edge up.
        column.addView(resultsScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // The input line.
        column.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = box(paper)
            setPadding(dp(8), 0, dp(8), 0)
            addView(prefix)
            addView(input, LinearLayout.LayoutParams(0, -2, 1f))
            addView(submit)
            setOnClickListener { input.requestFocus(); showKeyboard() }
        }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })

        column.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        styleInput()
        input.setOnFocusChangeListener { _, focused ->
            showResults(focused)
            if (focused) input.post { RetuiThemeBridge.sendToKeyboard(context, input, source.directory(), "launcher") }
        }
        refresh()
    }

    /** Re-reads everything shown; cheap enough to call whenever Home appears. */
    fun refresh() {
        refreshStatus()
        loadEvents()
        renderSession()
        renderChips()
    }

    private fun showResults(show: Boolean) {
        resultsScroll.visibility = if (show) VISIBLE else GONE
    }

    private fun showKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

    /** Leaves the input: clears it, closes the suggestions and puts the keyboard away. */
    fun closeInput() {
        input.text?.clear()
        input.clearFocus()
        requestFocus()
        input.setHint(R.string.mono_prompt_hint)
        showResults(false)
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
    }

    private var echoedAt = 0L

    /** A command run from the floating search, echoed into the session. */
    fun echo(command: String) {
        echoedAt = android.os.SystemClock.uptimeMillis()
        append(true, command)
        // Commands like notes -add print nothing but change what the session shows; look again once they ran.
        handler.postDelayed({ if (isAttachedToWindow) refresh() }, 700L)
    }

    /** Output the launcher printed for a command run here; background messages (weather updates) stay out. */
    fun output(text: CharSequence?) {
        val value = text?.toString()?.trim().orEmpty()
        if (value.isEmpty() || android.os.SystemClock.uptimeMillis() - echoedAt > ANSWER_WINDOW_MS) return
        value.lines().takeLast(12).forEach { append(false, it) }
    }

    /** Back leaves the input first; true when it did. */
    fun back(): Boolean {
        if (!input.hasFocus() && resultsScroll.visibility != VISIBLE) return false
        closeInput()
        return true
    }

    private fun append(command: Boolean, value: String) {
        transcript.addLast(command to value)
        while (transcript.size > 40) transcript.removeFirst()
        renderSession(scrollToEnd = true)
    }

    private fun refreshStatus() {
        statusTime.text = SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Locale.US).format(Date())
        statusDir.text = source.directory()
        battery.level = runCatching {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level < 0 || scale <= 0) -1 else level * 100 / scale
        }.getOrDefault(-1)
        bannerDate.text = SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(Date()).uppercase(Locale.getDefault())
        // The weather always has its place on the right, like a gauge reading nothing when there is no data.
        bannerWeather.text = source.weather()?.uppercase(Locale.getDefault()) ?: "--°"
        banner.symbol = source.weatherSymbol()
    }

    private fun loadEvents() {
        Thread {
            val tsv = runCatching { UpcomingEventsManager.formatUpcomingTsv(context) }.getOrDefault("")
            val list = tsv.lines().filter { it.isNotBlank() }.take(3).map { row ->
                val cells = row.split('\t')
                listOfNotNull(cells.getOrNull(1)?.takeIf { it.isNotBlank() } ?: cells.getOrNull(0), cells.getOrNull(2))
                    .joinToString("  ")
            }
            post { events = list; renderSession() }
        }.start()
    }

    private fun renderSession(scrollToEnd: Boolean = false) {
        session.removeAllViews()
        sessionTitle.text = context.getString(R.string.mono_session_title)
        session.addView(text("$ notes", inverted = false))
        val notes = runCatching { QuickNoteStore(context).load().ordered().take(4) }.getOrDefault(emptyList())
        if (notes.isEmpty()) session.addView(indent(context.getString(R.string.mono_no_notes)).apply { alpha = 0.6f })
        notes.forEach { note ->
            session.addView(indent((if (note.done) "[x] " else "[ ] ") + (if (note.hidden) "▓▓▓▓▓▓" else note.text)).apply {
                if (note.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                setOnClickListener {
                    QuickNoteStore(context).mutate { all -> all.firstOrNull { it.id == note.id }?.let { it.done = !it.done } }
                    renderSession()
                }
            })
        }
        gap()
        session.addView(text("$ next", inverted = false))
        if (events.isEmpty()) session.addView(indent(context.getString(R.string.mono_no_events)).apply { alpha = 0.6f })
        events.forEach { session.addView(indent(it)) }
        block("$ reminders", source.reminders().take(3).map { indent(it) }, R.string.mono_no_reminders)
        block("$ notifications", source.notifications().take(4).map { item ->
            indent(item.label).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setOnClickListener { item.run() } }
        }, R.string.mono_no_notifications)
        val music = source.music()
        block("$ music", listOfNotNull(music?.let { indent((if (it.playing) "► " else "▌▌ ") + listOfNotNull(it.title, it.artist).joinToString(" · ")) }),
            R.string.mono_no_music)
        clockRows.clear()
        block("$ timer", source.clock().map { indent(it).also(clockRows::add) }, R.string.mono_no_timer)
        if (transcript.isNotEmpty()) gap()
        transcript.forEach { (command, value) ->
            session.addView(if (command) text("$ $value", inverted = false) else indent(value).apply { alpha = 0.85f })
        }
        if (scrollToEnd) sessionScroll.post { sessionScroll.scrollTo(0, session.height) }
    }

    private fun renderChips() {
        chipsRow.removeAllViews()
        source.chips().take(6).forEachIndexed { index, chip ->
            chipsRow.addView(text(chip.label, inverted = index == 0).apply {
                background = if (index == 0) solid(ink) else box(paper)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setOnClickListener { chip.run() }
            }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
        }
    }

    private fun gap() = session.addView(View(context), LinearLayout.LayoutParams(-1, dp(8)))

    /** One module in the session: its command, then its rows or a dim line when it has none. */
    private fun block(command: String, rows: List<View>, empty: Int) {
        gap()
        session.addView(text(command, inverted = false))
        if (rows.isEmpty()) session.addView(indent(context.getString(empty)).apply { alpha = 0.6f })
        rows.forEach(session::addView)
    }

    private fun indent(value: String) = text(value, inverted = false).apply { setPadding(dp(16), 0, 0, 0) }

    private fun text(value: String, inverted: Boolean) = TextView(context).apply {
        text = value
        typeface = face
        LauncherFontScale.setBaseSize(this, BASE_TEXT_SP, LauncherFontScale.offsetSp())
        setTextColor(if (inverted) paper else ink)
        includeFontPadding = false
        setLineSpacing(0f, 1.05f)
    }

    private fun box(fill: Int) = GradientDrawable().apply { setColor(fill); setStroke(line, ink) }
    private fun solid(fill: Int) = GradientDrawable().apply { setColor(fill); setStroke(line, ink) }

    private fun dp(value: Int) = (value * density).roundToInt()

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Coming back to Home starts with the input closed and the keyboard away.
        if (hasWindowFocus && input.text.isNullOrEmpty()) { closeInput(); requestFocus() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(tick)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    /** Suggestions take what they need, up to about half the screen; the session keeps the rest. */
    private inner class CappedScroll : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = (resources.displayMetrics.heightPixels * 0.45f).toInt()
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }

    /** The battery as an outlined cell filled to the level, with a nub. */
    private inner class BatteryGlyph : View(context) {
        var level = -1
            set(value) { field = value; invalidate() }
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            paint.color = paper
            val nub = line.toFloat()
            val w = width - nub - line
            canvas.drawRect(0f, 0f, w, height.toFloat(), paint)
            paint.color = ink
            canvas.drawRect(line.toFloat(), line.toFloat(), w - line, height - line.toFloat(), paint)
            paint.color = paper
            val fill = if (level < 0) 0f else (w - line * 4) * level / 100f
            canvas.drawRect(line * 2f, line * 2f, line * 2f + fill, height - line * 2f, paint)
            canvas.drawRect(w + line, height * 0.3f, w + line + nub, height * 0.7f, paint)
        }
    }
}
