package ohi.andre.consolelauncher.mono

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.reader.ReaderLibraryActivity
import ohi.andre.consolelauncher.managers.reader.ReaderActivity
import ohi.andre.consolelauncher.managers.reader.Books
import ohi.andre.consolelauncher.managers.media.Channels
import ohi.andre.consolelauncher.managers.media.MediaLibrary
import ohi.andre.consolelauncher.managers.media.VideoMath
import ohi.andre.consolelauncher.managers.media.VideoActivity
import ohi.andre.consolelauncher.managers.media.VideoPlayerActivity
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager
import ohi.andre.consolelauncher.notes.QuickNoteStore
import ohi.andre.consolelauncher.notes.ordered
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Everything a mono panel may draw with: one ink, the paper under it and the ink dimmed, in the mono face. A
 * panel gets nothing else, so whatever is placed on the mono home is mono by construction.
 */
class MonoKit(val context: Context, val ink: Int, val paper: Int, val face: Typeface) {
    val dim: Int = ColorUtils.blendARGB(paper, ink, 0.55f)
    /** The empty cells of a meter: ink barely there. */
    val faint: Int = ColorUtils.blendARGB(paper, ink, 0.2f)
    val density = context.resources.displayMetrics.density
    val line = max(2, density.roundToInt())

    fun dp(value: Int) = (value * density).roundToInt()

    fun text(value: CharSequence, sp: Float = BASE_SP, color: Int = ink) = TextView(context).apply {
        text = value
        typeface = face
        LauncherFontScale.setBaseSize(this, sp, LauncherFontScale.offsetSp())
        setTextColor(color)
        includeFontPadding = false
        setLineSpacing(0f, 1.05f)
    }

    /** Cyberdeck mode keeps its cut corners here too, drawn in the one ink. */
    val cyberdeck: Boolean = AppearanceSettings.cyberdeckMode()

    fun box(fill: Int = paper): android.graphics.drawable.Drawable = if (cyberdeck)
        TerminalBorderDrawable(fill, ink, line, 0f, false, 0f, 0f, cyberdeck = true, cyberdeckNotch = false)
        else GradientDrawable().apply { setColor(fill); setStroke(line, ink) }

    /**
     * Frames a whole panel. In cyberdeck mode the frame is laid over the panel, so pictures and filled buttons
     * that reach its edges are cut to the notched shape as well.
     */
    fun frame(view: View) {
        if (!cyberdeck) { view.background = box(); return }
        view.setBackgroundColor(paper)
        view.foreground = TerminalBorderDrawable(paper, ink, line, 0f, false, 0f, 0f, cyberdeck = true, overlay = true)
    }

    /** Pressed shows as an inverted block; [framed] keeps the ink outline when at rest. */
    fun pressable(view: View, framed: Boolean) {
        view.isClickable = true
        view.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), box(ink))
            addState(intArrayOf(), if (framed) box() else GradientDrawable().apply { setColor(paper) })
        }
        val colors = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(paper, ink))
        fun tint(v: View) {
            if (v !== view) v.isDuplicateParentStateEnabled = true
            if (v is TextView) v.setTextColor(colors)
            if (v is ViewGroup) for (i in 0 until v.childCount) tint(v.getChildAt(i))
        }
        tint(view)
    }

    /** Ink while at rest, paper while the view (or the one it sits in) is pressed. */
    val pressedInk = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(paper, ink))

    /** A chip's frame with a dim shadow cast down and right; pressed, it drops onto the shadow, inverted. */
    fun shadowed(): StateListDrawable {
        val sh = max(2, dp(2))
        val rest = android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(dim) }, box())).apply {
            setLayerInset(0, sh, sh, 0, 0)
            setLayerInset(1, 0, 0, sh, sh)
        }
        val down = android.graphics.drawable.LayerDrawable(arrayOf(box(ink))).apply { setLayerInset(0, sh, sh, 0, 0) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), down)
            addState(intArrayOf(), rest)
        }
    }

    /** An inverted label, like the title tab of a handheld's form. */
    fun tab(label: String, sp: Float = BASE_SP) = text(label.uppercase(Locale.getDefault()), sp, paper).apply {
        setBackgroundColor(ink)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        setPadding(dp(8), 0, dp(8), 0)
    }

    /** Whether the system asks for no animation. */
    fun reducedMotion(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** A 7×7 pixel icon drawn in the ink of [colors] for its state; it follows its parent's pressed state. */
    fun icon(rects: IntArray, colors: ColorStateList = pressedInk) = PixelIcon(context, rects, colors).apply { isDuplicateParentStateEnabled = true }

    class PixelIcon(context: Context, private val rects: IntArray, var colors: ColorStateList) : View(context) {
        private val paint = Paint()
        override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
        override fun onDraw(canvas: Canvas) {
            val unit = minOf(width, height) / 7f
            val left = (width - unit * 7) / 2
            val top = (height - unit * 7) / 2
            paint.color = colors.getColorForState(drawableState, colors.defaultColor)
            var i = 0
            while (i + 3 < rects.size) {
                canvas.drawRect(left + rects[i] * unit, top + rects[i + 1] * unit,
                    left + (rects[i] + rects[i + 2]) * unit, top + (rects[i + 1] + rects[i + 3]) * unit, paint)
                i += 4
            }
        }
    }

    /** Ten cells filled to a level out of a hundred; -1 shows none. */
    inner class Meter : View(context) {
        var level = -1
            set(value) { field = value; invalidate() }
        private val paint = Paint()
        override fun onDraw(canvas: Canvas) {
            val count = 10
            val gap = dp(2).toFloat()
            val w = (width - gap * (count - 1)) / count
            if (w <= 0f) return
            val filled = if (level < 0) 0 else (level + 5) / 10
            for (i in 0 until count) {
                paint.color = if (i < filled) ink else faint
                val left = i * (w + gap)
                canvas.drawRect(left, 0f, left + w, height.toFloat(), paint)
            }
        }
    }

    /** The pixel icons, as x, y, width, height runs on a 7×7 grid. */
    object Icons {
        val SIGNAL = intArrayOf(0, 5, 1, 2, 2, 3, 1, 4, 4, 1, 1, 6, 6, 0, 1, 7)
        val APPS = intArrayOf(0, 0, 3, 3, 4, 0, 3, 3, 0, 4, 3, 3, 4, 4, 3, 3)
        val SEARCH = intArrayOf(1, 0, 3, 1, 0, 1, 1, 3, 4, 1, 1, 3, 1, 4, 3, 1, 4, 4, 1, 1, 5, 5, 1, 1, 6, 6, 1, 1)
        val WIDGETS = intArrayOf(0, 0, 7, 2, 0, 3, 3, 4, 4, 3, 3, 4)
        val SETTINGS = intArrayOf(0, 1, 7, 1, 2, 0, 1, 3, 0, 5, 7, 1, 4, 4, 1, 3)
        val PLUS = intArrayOf(3, 0, 1, 7, 0, 3, 7, 1)
        val TIMER = intArrayOf(2, 0, 3, 1, 1, 1, 1, 1, 5, 1, 1, 1, 0, 2, 1, 3, 6, 2, 1, 3, 1, 5, 1, 1, 5, 5, 1, 1, 2, 6, 3, 1, 3, 2, 1, 2, 4, 3, 1, 1)
        val CLIP = intArrayOf(2, 0, 3, 1, 0, 1, 2, 1, 5, 1, 2, 1, 0, 2, 1, 5, 6, 2, 1, 5, 1, 6, 5, 1, 2, 3, 3, 1, 2, 4, 2, 1)
        val BELL = intArrayOf(3, 0, 1, 1, 2, 1, 3, 1, 1, 2, 5, 3, 0, 5, 7, 1, 3, 6, 1, 1)
        val BOOK = intArrayOf(0, 1, 3, 5, 4, 1, 3, 5, 3, 5, 1, 1)
        val CALC = intArrayOf(0, 0, 7, 1, 0, 6, 7, 1, 0, 1, 1, 5, 6, 1, 1, 5, 2, 2, 3, 1, 2, 4, 1, 1, 4, 4, 1, 1)
    }

    companion object {
        /** Mono's font_size_offset carries its own +3, so at the user's default offset this draws at 19sp. */
        const val BASE_SP = 16f
        const val SMALL_SP = 13f
    }
}

/** What a panel may ask of the home: its data, a command run quietly, text put on the command line, saved state. */
class MonoPanelHost(
    val source: MonoHomeView.Source,
    val state: MutableMap<String, String>,
    val prefill: (String) -> Unit
)

/** One panel of the mono home. The home builds it when shown and releases it when leaving; it never runs on its own. */
abstract class MonoPanel(protected val kit: MonoKit, protected val host: MonoPanelHost) {
    abstract val view: View

    /** Reads its data again. */
    open fun refresh() = Unit

    /** The home's clock tick; true asks for the next one within a second. */
    open fun tick(): Boolean = false

    /** The home is going away: stop anything still running. */
    open fun release() = Unit

    protected fun dp(value: Int) = kit.dp(value)

    /**
     * A framed panel with an inverted title tab on its top rule and an optional note on the right; returns the
     * frame and the body to fill.
     */
    protected fun titled(label: String, note: View? = null, padH: Int = 8, padV: Int = 6): Pair<LinearLayout, LinearLayout> {
        val body = LinearLayout(kit.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(padH), dp(padV), dp(padH), dp(padV))
        }
        val frame = LinearLayout(kit.context).apply {
            orientation = LinearLayout.VERTICAL
            kit.frame(this)
            setPadding(kit.line, kit.line, kit.line, kit.line)
            addView(LinearLayout(kit.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(kit.tab(label), LinearLayout.LayoutParams(-2, -1))
                addView(View(kit.context), LinearLayout.LayoutParams(0, 0, 1f))
                if (note != null) addView(note, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            }, LinearLayout.LayoutParams(-1, dp(24)))
            addView(View(kit.context).apply { setBackgroundColor(kit.ink) }, LinearLayout.LayoutParams(-1, kit.line))
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        return frame to body
    }

    protected fun scrolling(content: View) = ScrollView(kit.context).apply {
        isVerticalScrollBarEnabled = false
        addView(content)
    }

    companion object {
        fun create(kind: MonoLayout.Kind, kit: MonoKit, host: MonoPanelHost): MonoPanel = when (kind) {
            MonoLayout.Kind.SCENE -> ScenePanel(kit, host)
            MonoLayout.Kind.CLOCK -> ClockPanel(kit, host)
            MonoLayout.Kind.APPS -> AppsPanel(kit, host)
            MonoLayout.Kind.MEDIA -> MediaPanel(kit, host)
            MonoLayout.Kind.QUICK -> QuickPanel(kit, host)
            MonoLayout.Kind.TASKS -> TasksPanel(kit, host)
            MonoLayout.Kind.NEXT -> NextPanel(kit, host)
            MonoLayout.Kind.ALERTS -> AlertsPanel(kit, host)
            MonoLayout.Kind.READ -> ReadPanel(kit, host)
            // A terminal needs its window and the shared connection; the home builds it as a MonoTermPanel.
            MonoLayout.Kind.TERM -> throw IllegalArgumentException("terminal panels are built by the home")
        }
    }
}

/** The dithered weather scene, square, with the weather in a corner. */
private class ScenePanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val banner = MonoBanner(kit.context, kit.ink, kit.paper)
    private val weather = kit.text("", MonoKit.SMALL_SP, kit.paper).apply {
        setBackgroundColor(kit.ink)
        setPadding(dp(6), dp(2), dp(6), dp(2))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    override val view: View = FrameLayout(kit.context).apply {
        kit.frame(this)
        setPadding(kit.line, kit.line, kit.line, kit.line)
        addView(banner, FrameLayout.LayoutParams(-1, -1))
        addView(weather, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.BOTTOM))
    }

    override fun refresh() {
        banner.symbol = host.source.weatherSymbol()
        weather.text = host.source.weather()?.uppercase(Locale.getDefault()) ?: "--°"
    }
}

/** A date chip and the week, the time large, then battery, storage and memory as segmented meters. */
private class ClockPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val date = kit.tab("", MonoKit.SMALL_SP)
    private val week = kit.text("", MonoKit.SMALL_SP, kit.dim)
    private val time = kit.text("", 64f).apply { maxLines = 1 }
    private val timer = kit.text("", MonoKit.SMALL_SP).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; visibility = View.GONE }
    private val alarm = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; visibility = View.GONE }
    private val day = Row("DAY")
    private val battery = Row("BAT")
    private val disk = Row("DSK")
    private val memory = Row("RAM")
    override val view: View = LinearLayout(kit.context).apply {
        orientation = LinearLayout.VERTICAL
        kit.frame(this)
        setPadding(kit.line, kit.line, kit.line, kit.line)
        addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(date, LinearLayout.LayoutParams(-2, -1))
            addView(View(kit.context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(week, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }, LinearLayout.LayoutParams(-1, dp(22)))
        addView(View(kit.context).apply { setBackgroundColor(kit.ink) }, LinearLayout.LayoutParams(-1, kit.line))
        addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(6))
            // The time, then what is running or set, then the meters; the room left over sits above the meters.
            addView(time)
            addView(timer)
            addView(alarm)
            addView(View(kit.context), LinearLayout.LayoutParams(0, 0, 1f))
            listOf(day, battery, disk, memory).forEach { addView(it.view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }) }
        }, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    /** A label, ten cells and the value. */
    private inner class Row(label: String) {
        val meter = kit.Meter()
        val value = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { gravity = Gravity.END }
        val view = LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(kit.text(label, MonoKit.SMALL_SP), LinearLayout.LayoutParams(dp(30), -2))
            addView(meter, LinearLayout.LayoutParams(0, dp(8), 1f))
            addView(value, LinearLayout.LayoutParams(dp(36), -2))
        }
        fun set(level: Int) { meter.level = level; value.text = if (level < 0) "--" else "$level%" }
    }

    override fun refresh() {
        tick()
        battery.set(runCatching {
            val status = kit.context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val now = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (now < 0 || scale <= 0) -1 else now * 100 / scale
        }.getOrDefault(-1))
        disk.set(runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            ((stat.totalBytes - stat.availableBytes) * 100 / stat.totalBytes).toInt()
        }.getOrDefault(-1))
        memory.set(runCatching {
            val info = ActivityManager.MemoryInfo()
            (kit.context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            ((info.totalMem - info.availMem) * 100 / info.totalMem).toInt()
        }.getOrDefault(-1))
    }

    override fun tick(): Boolean {
        val now = Date()
        date.text = SimpleDateFormat("EEE dd MMM", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
        week.text = kit.context.getString(R.string.mono_week, java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR))
        val twentyFour = android.text.format.DateFormat.is24HourFormat(kit.context)
        val clockText = SimpleDateFormat(if (twentyFour) "HH:mm" else "h:mm", Locale.US).format(now)
        // On a 12-hour clock, AM or PM sits small after the time.
        time.text = if (twentyFour) clockText else android.text.SpannableString(clockText + " " + SimpleDateFormat("a", Locale.US).format(now)).apply {
            setSpan(android.text.style.RelativeSizeSpan(0.3f), clockText.length, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val calendar = java.util.Calendar.getInstance()
        day.set((calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)) * 100 / (24 * 60))
        // The next alarm the phone's clock app has set, if any.
        val next = runCatching { (kit.context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime }.getOrNull()
        alarm.text = next?.let { kit.context.getString(R.string.mono_alarm,
            SimpleDateFormat(if (twentyFour) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()).format(Date(it)).uppercase(Locale.getDefault())) }.orEmpty()
        alarm.visibility = if (next == null) View.GONE else View.VISIBLE
        // A running timer or stopwatch shows under the time.
        val clock = host.source.clock()
        timer.text = clock.joinToString("  ")
        timer.visibility = if (clock.isEmpty()) View.GONE else View.VISIBLE
        return clock.isNotEmpty()
    }
}

/** The apps in sections, the most used first and then each app group, as name chips; it scrolls. */
private class AppsPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    /** One section per tab, stacked: its name on a dim rule, then its chips. */
    private val sections = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View = titled(kit.context.getString(R.string.mono_bar_apps), padH = 6).let { (frame, body) ->
        body.addView(scrolling(sections), LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }
    private var shown: List<MonoHomeView.Tab> = emptyList()
    private var checkedAt = 0L

    override fun refresh() {
        // Refreshes come often (music, notifications); the apps change rarely, so look again only now and then,
        // or straight away while the list is still loading.
        val now = SystemClock.uptimeMillis()
        // The first section falls back to every app A to Z, so it is empty only while the list is still loading.
        val loaded = shown.firstOrNull()?.apps?.isNotEmpty() == true
        if (loaded && now - checkedAt < RECHECK_MS) return
        checkedAt = now
        val all = host.source.appTabs()
        fun signature(tabs: List<MonoHomeView.Tab>) = tabs.map { tab -> tab.name to tab.apps.map { it.label } }
        if (loaded && signature(all) == signature(shown)) return
        shown = all
        render()
    }

    // The app list may still be loading when the home is first shown, and apps come and go; the clock tick looks again.
    override fun tick(): Boolean {
        refresh()
        return false
    }

    private fun render() {
        sections.removeAllViews()
        val filled = shown.filter { it.apps.isNotEmpty() }
        if (filled.isEmpty()) sections.addView(kit.text(kit.context.getString(R.string.mono_no_apps), MonoKit.SMALL_SP, kit.dim))
        filled.forEachIndexed { index, tab ->
            sections.addView(LinearLayout(kit.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(kit.text(tab.name, MonoKit.SMALL_SP, kit.dim))
                addView(View(kit.context).apply { setBackgroundColor(kit.dim) }, LinearLayout.LayoutParams(0, kit.line, 1f).apply { marginStart = dp(6) })
            }, LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(10) })
            sections.addView(Flow(kit.context, dp(6)).apply {
                tab.apps.forEach { app -> addView(chip(app)) }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
    }

    /** An app as a chip with its name; it inverts while pressed. */
    private fun chip(app: MonoHomeView.Action) = kit.text(app.label).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = kit.context.resources.displayMetrics.widthPixels / 2
        gravity = Gravity.CENTER_VERTICAL
        val sh = max(2, dp(2))
        setPadding(dp(8), dp(4), dp(8) + sh, dp(4) + sh)
        background = kit.shadowed()
        setTextColor(kit.pressedInk)
        isClickable = true
        setOnClickListener { app.run() }
    }

    /** Lays its children out left to right, wrapping to a new line when one would not fit. */
    private class Flow(context: Context, private val gap: Int) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val inner = width - paddingLeft - paddingRight
            var x = 0
            var y = 0
            var line = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(MeasureSpec.makeMeasureSpec(inner, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
                if (x > 0 && x + child.measuredWidth > inner) { x = 0; y += line + gap; line = 0 }
                x += child.measuredWidth + gap
                line = max(line, child.measuredHeight)
            }
            setMeasuredDimension(width, y + line + paddingTop + paddingBottom)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val inner = r - l - paddingLeft - paddingRight
            var x = 0
            var y = 0
            var line = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (x > 0 && x + child.measuredWidth > inner) { x = 0; y += line + gap; line = 0 }
                child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + child.measuredWidth, paddingTop + y + child.measuredHeight)
                x += child.measuredWidth + gap
                line = max(line, child.measuredHeight)
            }
        }
    }

    companion object {
        const val RECHECK_MS = 5_000L
    }
}

/**
 * What is playing, as a dithered picture filling the panel. The first tap shows the name and the controls, a
 * tap on a control acts, and they hide again after a few seconds.
 */
private class MediaPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val disc = Disc()
    private val tv = MonoTv(kit.context, kit.ink, kit.paper, kit::reducedMotion) { refresh() }.apply { visibility = View.GONE }
    private val badge = kit.text("", MonoKit.SMALL_SP).apply { setPadding(dp(6), dp(1), dp(6), dp(1)) }
    private val label = kit.tab(kit.context.getString(R.string.mono_media_title), MonoKit.SMALL_SP).apply { setPadding(dp(8), dp(2), dp(8), dp(2)) }
    private val title = kit.text("").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val subtitle = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val buttons = LinearLayout(kit.context).apply { orientation = LinearLayout.HORIZONTAL }
    private val controls = LinearLayout(kit.context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(kit.paper)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
            addView(title)
            addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        })
        addView(View(kit.context).apply { setBackgroundColor(kit.ink) }, LinearLayout.LayoutParams(-1, kit.line))
        addView(View(kit.context), LinearLayout.LayoutParams(-1, 0, 1f))
        addView(View(kit.context).apply { setBackgroundColor(kit.ink) }, LinearLayout.LayoutParams(-1, kit.line))
        addView(buttons.apply {
            setBackgroundColor(kit.paper)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            isClickable = true
        }, LinearLayout.LayoutParams(-1, -2))
    }
    private val hide = Runnable { controls.visibility = View.GONE; badge.visibility = View.VISIBLE; label.visibility = View.VISIBLE }
    private val shelf = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(6)) }
    /** The library: channels, videos to carry on with, shows and music, over the picture until closed. */
    private val library = LinearLayout(kit.context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(kit.paper)
        visibility = View.GONE
        isClickable = true
        addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(kit.tab(kit.context.getString(R.string.mono_library_title)), LinearLayout.LayoutParams(-2, -1))
            addView(View(kit.context), LinearLayout.LayoutParams(0, 0, 1f))
            addView(kit.text("×").apply {
                gravity = Gravity.CENTER
                contentDescription = kit.context.getString(R.string.mono_library_close)
                kit.pressable(this, framed = false)
                setOnClickListener { closeLibrary() }
            }, LinearLayout.LayoutParams(dp(32), -1))
        }, LinearLayout.LayoutParams(-1, dp(24)))
        addView(View(kit.context).apply { setBackgroundColor(kit.ink) }, LinearLayout.LayoutParams(-1, kit.line))
        addView(scrolling(shelf), LinearLayout.LayoutParams(-1, 0, 1f))
    }
    override val view: View = FrameLayout(kit.context).apply {
        kit.frame(this)
        setPadding(kit.line, kit.line, kit.line, kit.line)
        addView(disc, FrameLayout.LayoutParams(-1, -1))
        addView(tv, FrameLayout.LayoutParams(-1, -1))
        addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.TOP))
        addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.TOP).apply { setMargins(0, dp(6), dp(6), 0) })
        addView(controls, FrameLayout.LayoutParams(-1, -1))
        addView(library, FrameLayout.LayoutParams(-1, -1))
        isClickable = true
        setOnClickListener { reveal() }
    }

    private fun reveal() {
        controls.visibility = View.VISIBLE
        badge.visibility = View.GONE
        label.visibility = View.GONE
        view.removeCallbacks(hide)
        view.postDelayed(hide, HIDE_MS)
    }

    override fun refresh() {
        if (inFullScreen && VideoActivity.nowPlaying == null) { takeBack(); return }
        val context = kit.context
        val music = host.source.music()
        val on = tv.channel?.takeIf { tv.live }
        val stopped = if (on == null) resumeUrl?.let { Channels.find(context, it) } else null
        val last = if (on == null && music == null && stopped == null) Channels.last(context) else null
        tv.visibility = if (on != null) View.VISIBLE else View.GONE
        disc.playing = on == null && music?.playing == true
        disc.idle = on != null || music == null
        buttons.removeAllViews()
        when {
            on != null -> {
                show(context.getString(when {
                    tv.waiting -> R.string.mono_media_tuning
                    tv.liveStream -> R.string.mono_media_live
                    else -> R.string.mono_media_video_playing
                }), !tv.waiting,
                    on.name, on.group.ifBlank { context.getString(R.string.mono_media_tv) })
                button("■", false) { resumeUrl = on.url; leftHome = false; tv.stop() }
                button("⛶ " + context.getString(R.string.mono_media_full), true) { fullScreen(on) }
                button("≡", false) { openLibrary() }
            }
            music != null -> {
                show(context.getString(if (music.playing) R.string.mono_media_playing else R.string.mono_media_paused), music.playing,
                    music.title, music.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.mono_media_music))
                button("|<", false) { host.source.run("music -previous") }
                button(if (music.playing) "||" else "> ", true) { host.source.run("music -play") }
                button(">|", false) { host.source.run("music -next") }
                button("≡", false) { openLibrary() }
            }
            stopped != null -> {
                show(context.getString(R.string.mono_media_stopped), false, stopped.name, context.getString(when {
                    tv.failed -> R.string.media_video_cannot_play
                    leftHome -> R.string.mono_media_stopped_title
                    else -> R.string.mono_media_stopped_by_you
                }))
                button("> " + context.getString(if (Channels.wasLive(stopped.url)) R.string.mono_media_resume_live else R.string.mono_media_resume), true) {
                    resumeUrl = null
                    tv.play(stopped)
                }
                button("≡", false) { openLibrary() }
            }
            last != null -> {
                show(context.getString(R.string.mono_media_idle), false, last.name, context.getString(R.string.mono_media_last_watched))
                button("> " + context.getString(R.string.mono_media_watch), true) { tv.play(last) }
                button("≡ " + context.getString(R.string.mono_media_library), false) { openLibrary() }
            }
            else -> {
                show(context.getString(R.string.mono_media_idle), false, context.getString(R.string.mono_no_music), context.getString(R.string.mono_media_music))
                button("> " + context.getString(R.string.mono_media_play), true) { host.source.run("music -play") }
                button("≡ " + context.getString(R.string.mono_media_library), false) { openLibrary() }
            }
        }
    }

    private fun show(tag: String, lit: Boolean, name: String, detail: String) {
        badge.text = tag
        badge.setTextColor(if (lit) kit.paper else kit.ink)
        badge.background = kit.box(if (lit) kit.ink else kit.paper)
        title.text = name
        subtitle.text = detail
    }

    /**
     * Full screen plays in colour in the video player, from where the panel was; the panel lets go so only one
     * player runs, and takes it back from where full screen left off once that closes.
     */
    private fun fullScreen(channel: Channels.Channel) {
        resumeUrl = channel.url
        tv.stop()
        // Marked once the home has gone, not now: until full screen opens this panel would take it straight back.
        goingFull = true
        kit.context.startActivity(Intent(kit.context, VideoPlayerActivity::class.java)
            .putExtra(VideoActivity.EXTRA_URI, channel.url)
            .putExtra(VideoActivity.EXTRA_START_AT, Channels.spot(channel.url))
            .putExtra(VideoActivity.EXTRA_FROM_HOME, true))
    }

    /** Full screen has closed: carry on in the panel with whatever channel it ended on. */
    private fun takeBack() {
        if (!inFullScreen || VideoActivity.nowPlaying != null) return
        inFullScreen = false
        val channel = Channels.last(kit.context) ?: return
        resumeUrl = null
        tv.play(channel)
        refresh()
    }

    private var goingFull = false

    override fun tick(): Boolean {
        takeBack()
        return inFullScreen
    }

    private fun openLibrary() {
        view.removeCallbacks(hide)
        hide.run()
        fillLibrary()
        library.visibility = View.VISIBLE
    }

    private fun closeLibrary() {
        library.visibility = View.GONE
    }

    private fun fillLibrary() {
        val context = kit.context
        shelf.removeAllViews()
        val channels = Channels.list(context)
        heading(R.string.mono_library_tv)
        val on = tv.channel?.takeIf { tv.live }?.url
        channels.forEach { channel ->
            entry(channel.name, if (channel.url == on) context.getString(R.string.mono_media_live) else channel.group) {
                resumeUrl = null
                tv.play(channel)
                closeLibrary()
            }
        }
        entry("+ " + context.getString(if (channels.isEmpty()) R.string.mono_library_add_channels else R.string.mono_library_more_channels), "") { openChannels() }

        val videos = MediaLibrary.items(context).filter { it.video }
        val resume = context.getSharedPreferences("video_resume", Context.MODE_PRIVATE)
        fun saved(uri: String): Pair<Long, Long> {
            val parts = resume.getString(uri, null)?.split('|') ?: return 0L to 0L
            return (parts.getOrNull(0)?.toLongOrNull() ?: 0L) to (parts.getOrNull(1)?.toLongOrNull() ?: 0L)
        }
        val last = resume.getString(VideoActivity.KEY_LAST, null)
        val going = videos.filter { saved(it.uri).let { (p, d) -> VideoMath.canResume(p, d) } }
            .sortedByDescending { it.uri == last }.take(CONTINUE_ROWS)
        if (going.isNotEmpty()) {
            heading(R.string.mono_library_continue)
            going.forEach { item ->
                val (p, d) = saved(item.uri)
                entry(item.name, if (d > 0) "${p * 100 / d}%" else "") { openVideo(item, VideoMath.resumeAt(p, d)) }
            }
        }
        val shows = videos.groupBy { it.folder }
        if (shows.isNotEmpty()) {
            heading(R.string.mono_library_video)
            shows.forEach { (folder, episodes) ->
                val states = episodes.map { saved(it.uri) }
                val left = states.count { (p, d) -> !VideoMath.isDone(p, d) }
                entry(folder.substringAfterLast('/'), "$left/${episodes.size}") {
                    val start = VideoMath.showStart(states)
                    val item = episodes.getOrNull(start.index) ?: return@entry
                    openVideo(item, states.getOrNull(start.index)?.let { (p, d) -> VideoMath.resumeAt(p, d) } ?: 0L)
                }
            }
        }
        heading(R.string.mono_library_music)
        entry(context.getString(R.string.mono_library_open_music), "") {
            context.startActivity(Intent(context, MediaPlayerActivity::class.java)
                .putExtra(MediaPlayerActivity.EXTRA_TAB, MediaPlayerActivity.Tab.MUSIC.name))
        }
    }

    private fun heading(name: Int) {
        shelf.addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(kit.text(kit.context.getString(name), MonoKit.SMALL_SP, kit.dim))
            addView(View(kit.context).apply { setBackgroundColor(kit.dim) }, LinearLayout.LayoutParams(0, kit.line, 1f).apply { marginStart = dp(6) })
        }, LinearLayout.LayoutParams(-1, -2).apply { if (shelf.childCount > 0) topMargin = dp(8) })
    }

    private fun entry(name: String, note: String, action: () -> Unit) {
        shelf.addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            addView(kit.text(name).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            if (note.isNotEmpty()) addView(kit.text(note, MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1 },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            kit.pressable(this, framed = false)
            setOnClickListener { action(); refresh() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    /** Library videos play full screen in colour; live TV in the panel gives way. */
    private fun openVideo(item: MediaLibrary.Item, startAt: Long) {
        resumeUrl = null
        tv.stop()
        closeLibrary()
        kit.context.startActivity(Intent(kit.context, VideoPlayerActivity::class.java)
            .putExtra(VideoActivity.EXTRA_URI, item.uri).putExtra(VideoActivity.EXTRA_START_AT, startAt))
    }

    private fun openChannels() {
        kit.context.startActivity(Intent(kit.context, MediaPlayerActivity::class.java)
            .putExtra(MediaPlayerActivity.EXTRA_TAB, MediaPlayerActivity.Tab.CHANNELS.name))
    }

    private fun button(label: String, primary: Boolean, action: () -> Unit) {
        buttons.addView(kit.text(label).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            kit.pressable(this, framed = true)
            if (primary) {
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), kit.box())
                    addState(intArrayOf(), kit.box(kit.ink))
                }
                setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(kit.ink, kit.paper)))
            }
            setOnClickListener {
                action()
                refresh()
                reveal()
            }
        }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
    }

    override fun release() {
        if (goingFull) { goingFull = false; inFullScreen = true }
        view.removeCallbacks(hide)
        hide.run()
        closeLibrary()
        // Leaving home stops live TV; coming back offers to resume it.
        tv.channel?.takeIf { tv.live }?.let { resumeUrl = it.url; leftHome = true }
        tv.stop()
    }

    /**
     * A record beside a level meter, ordered-dithered. While music plays the record turns and the bars move, at
     * a capped frame rate; paused they rest, and with nothing to play the record sits alone.
     */
    private inner class Disc : View(kit.context) {
        var playing = false
            set(value) { field = value; update() }
        var idle = true
            set(value) { field = value; invalidate() }
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private var bitmap: Bitmap? = null
        private var pixels = IntArray(0)
        private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        private val frame = object : Runnable {
            override fun run() { invalidate(); if (running) postDelayed(this, FRAME_MS) }
        }
        private var running = false

        override fun onDraw(canvas: Canvas) {
            val cell = max(2, (kit.density * 1.5f).roundToInt())
            val w = width / cell
            val h = height / cell
            if (w <= 0 || h <= 0) return
            if (bitmap?.width != w || bitmap?.height != h) {
                bitmap?.recycle()
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                pixels = IntArray(w * h)
            }
            val step = if (running) (SystemClock.uptimeMillis() / FRAME_MS).toInt() else 0
            val turn = step % 64 / 64.0 * Math.PI * 2
            val cx = if (idle) w / 2f else w * 0.32f
            val cy = h / 2f
            val r = h * 0.40f
            // The level meter: bars that jump while playing and settle when paused.
            val bars = 9
            val barLeft = (w * 0.58f).toInt()
            val barStep = max(2, ((w * 0.92f - barLeft) / bars).toInt())
            val barW = max(1, barStep / 2)
            val floor = (h * 0.82f).toInt()
            for (y in 0 until h) for (x in 0 until w) {
                val d = hypot(x - cx, y - cy)
                var v = when {
                    d > r -> 0.08f
                    d > r * 0.9f -> 1f
                    d > r * 0.34f -> {
                        val a = Math.atan2((y - cy).toDouble(), (x - cx).toDouble())
                        val sweep = ((a - turn + Math.PI * 4) % (Math.PI * 2))
                        if (sweep < 0.5) 1f else if ((d / (r * 0.08f)).toInt() % 2 == 0) 0.5f else 0.28f
                    }
                    d > r * 0.1f -> 0f
                    else -> 1f
                }
                if (!idle && x >= barLeft && (x - barLeft) % barStep < barW) {
                    val bar = (x - barLeft) / barStep
                    if (bar < bars) {
                        val seed = if (playing) (bar * 7 + step * 13) * 2654435761L.toInt() else bar * 40503
                        val height = (h * (0.18f + 0.5f * (((seed ushr 7) and 255) / 255f))).toInt()
                        if (y in floor - height until floor) v = if (bar % 2 == 0) 1f else 0.5f
                    }
                }
                val threshold = (bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
                pixels[y * w + x] = if (v > threshold) kit.ink else kit.paper
            }
            val b = bitmap ?: return
            b.setPixels(pixels, 0, w, 0, 0, w, h)
            val left = (width - w * cell) / 2
            val top = (height - h * cell) / 2
            canvas.drawBitmap(b, Rect(0, 0, w, h), Rect(left, top, left + w * cell, top + h * cell), paint)
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); update() }
        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); update() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); update() }

        private fun update() {
            val should = playing && isAttachedToWindow && windowVisibility == VISIBLE && !kit.reducedMotion()
            if (should == running) { invalidate(); return }
            running = should
            removeCallbacks(frame)
            if (running) post(frame) else invalidate()
        }
    }

    private companion object {
        /** The channel that was on when the home went away; kept across rebuilds of the panel. */
        var resumeUrl: String? = null
        /** A channel went full screen from the panel and comes back to it when that closes. */
        var inFullScreen = false
        /** The channel stopped because the home went away, rather than by the stop button. */
        var leftHome = false
        const val CONTINUE_ROWS = 5
        const val HIDE_MS = 3_500L
        /** Eight frames a second: enough for the bars, calm for the record. */
        const val FRAME_MS = 125L
    }
}

/** Shortcuts that start a command on the command line, ready to finish and run. */
private class QuickPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val rows = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View = titled(kit.context.getString(R.string.mono_quick_title), padH = 4, padV = 4).let { (frame, body) ->
        body.addView(scrolling(rows), LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }

    init {
        row(MonoKit.Icons.PLUS, R.string.mono_quick_note) { host.prefill("notes -add ") }
        row(MonoKit.Icons.TIMER, R.string.mono_quick_timer) { host.prefill("timer ") }
        row(MonoKit.Icons.CLIP, R.string.mono_quick_clipboard) {
            val clip = (kit.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip
            host.prefill(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(kit.context)?.toString().orEmpty())
        }
        row(MonoKit.Icons.BELL, R.string.mono_quick_reminder) { host.prefill("reminder -add ") }
        row(MonoKit.Icons.CALC, R.string.mono_quick_calc) { host.prefill("calc ") }
        row(MonoKit.Icons.BOOK, R.string.mono_quick_read) { openLastBook(kit.context, null) }
    }

    private fun row(icon: IntArray, label: Int, run: () -> Unit) {
        rows.addView(LinearLayout(kit.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(3), dp(6), dp(3))
            addView(kit.icon(icon), LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(8) })
            addView(kit.text(kit.context.getString(label)).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, -2, 1f))
            kit.pressable(this, framed = false)
            setOnClickListener { run() }
        }, LinearLayout.LayoutParams(-1, -2))
    }
}

/** The quick notes as a checklist; a tap ticks one off. */
private class TasksPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val rows = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    private val count = kit.text("", MonoKit.SMALL_SP, kit.dim)
    override val view: View = titled(kit.context.getString(R.string.mono_tasks_title), count).let { (frame, body) ->
        body.addView(scrolling(rows), LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }

    override fun refresh() {
        val notes = runCatching { QuickNoteStore(kit.context).load().ordered() }.getOrDefault(emptyList())
        count.text = if (notes.isEmpty()) "" else "${notes.count { it.done }}/${notes.size}"
        rows.removeAllViews()
        if (notes.isEmpty()) rows.addView(kit.text(kit.context.getString(R.string.mono_no_notes), MonoKit.SMALL_SP, kit.dim))
        notes.forEach { note ->
            val color = if (note.done) kit.dim else kit.ink
            rows.addView(LinearLayout(kit.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
                addView(View(kit.context).apply {
                    background = GradientDrawable().apply { setColor(if (note.done) kit.dim else kit.paper); setStroke(kit.line, color) }
                }, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) })
                addView(kit.text(if (note.hidden) "▓▓▓▓▓▓" else note.text, color = color).apply {
                    if (note.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(0, -2, 1f))
                isClickable = true
                setOnClickListener {
                    QuickNoteStore(kit.context).mutate { all -> all.firstOrNull { it.id == note.id }?.let { it.done = !it.done } }
                    refresh()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }
}

/** What's next: upcoming calendar events, then reminders. */
private class NextPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val rows = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    private var events: List<String> = emptyList()
    private var loading: Thread? = null
    override val view: View = titled(kit.context.getString(R.string.mono_next_title)).let { (frame, body) ->
        body.addView(scrolling(rows), LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }

    override fun refresh() {
        render()
        if (loading?.isAlive == true) return
        loading = Thread {
            val tsv = runCatching { UpcomingEventsManager.formatUpcomingTsv(kit.context) }.getOrDefault("")
            val list = tsv.lines().filter { it.isNotBlank() }.take(4).map { row ->
                val cells = row.split('\t')
                listOfNotNull(cells.getOrNull(1)?.takeIf { it.isNotBlank() } ?: cells.getOrNull(0), cells.getOrNull(2)).joinToString("  ")
            }
            view.post { events = list; render() }
        }.also { it.start() }
    }

    private fun render() {
        rows.removeAllViews()
        val all = events + host.source.reminders().take(4)
        if (all.isEmpty()) rows.addView(kit.text(kit.context.getString(R.string.mono_no_events), MonoKit.SMALL_SP, kit.dim))
        all.forEach { rows.addView(kit.text(it, MonoKit.SMALL_SP).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, dp(2)) }) }
    }
}

/** The latest notifications; a tap opens one. */
private class AlertsPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val rows = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    override val view: View = titled(kit.context.getString(R.string.mono_alerts_title)).let { (frame, body) ->
        body.addView(scrolling(rows), LinearLayout.LayoutParams(-1, 0, 1f))
        frame
    }

    override fun refresh() {
        rows.removeAllViews()
        val items = host.source.notifications().take(8)
        if (items.isEmpty()) rows.addView(kit.text(kit.context.getString(R.string.mono_no_notifications), MonoKit.SMALL_SP, kit.dim))
        items.forEach { item ->
            rows.addView(kit.text(item.label, MonoKit.SMALL_SP).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, dp(3))
                kit.pressable(this, framed = false)
                setOnClickListener { item.run() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }
}

/** Carries on with the book read last, of one kind when [kind] is given, or opens the library when there is none. */
private fun openLastBook(context: Context, kind: Books.Format?) {
    val last = if (kind == null) Books.last(context) else Books.lastByKind(context).firstOrNull { Books.kindOf(it.first.format) == kind }
    if (last != null) ReaderActivity.open(context, last.first.uri)
    else context.startActivity(Intent(context, ReaderLibraryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/**
 * The books being read: the one read last by a dithered cover with its chapter and place, then the last of every
 * other kind, one line each, and a button to carry on.
 */
private class ReadPanel(kit: MonoKit, host: MonoPanelHost) : MonoPanel(kit, host) {
    private val name = kit.text("").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val chapter = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val where = kit.text("", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1 }
    private val meter = kit.Meter()
    private val others = LinearLayout(kit.context).apply { orientation = LinearLayout.VERTICAL }
    private val go = kit.text("").apply {
        gravity = Gravity.CENTER
        setTextColor(kit.paper)
        setBackgroundColor(kit.ink)
        isClickable = true
    }
    override val view: View = titled(kit.context.getString(R.string.mono_read_title)).let { (frame, body) ->
        body.addView(scrolling(LinearLayout(kit.context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(kit.context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Cover(), LinearLayout.LayoutParams(dp(30), dp(44)).apply { marginEnd = dp(8) })
                addView(LinearLayout(kit.context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(name)
                    addView(chapter)
                    addView(where)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            })
            addView(meter, LinearLayout.LayoutParams(-1, dp(6)).apply { topMargin = dp(6); bottomMargin = dp(4) })
            addView(others)
        }), LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(go, LinearLayout.LayoutParams(-1, dp(28)).apply { topMargin = dp(4) })
        frame
    }

    override fun refresh() {
        val context = kit.context
        val kinds = Books.lastByKind(context)
        others.removeAllViews()
        go.setOnLongClickListener { context.startActivity(Intent(context, ReaderLibraryActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
        val last = kinds.firstOrNull()
        if (last == null) {
            name.text = context.getString(R.string.mono_read_none)
            chapter.text = ""
            where.text = ""
            meter.level = -1
            go.text = context.getString(R.string.mono_read_library)
            go.setOnClickListener { openLastBook(context, null) }
            return
        }
        val (book, place) = last
        name.text = place.title.ifBlank { book.name }
        chapter.text = place.chapterTitle.ifBlank { if (place.pages > 0) context.getString(R.string.reader_page_n, place.page + 1) else "" }
        where.text = "${kindLabel(book.format)} · ${percent(place)}%"
        meter.level = percent(place)
        go.text = context.getString(R.string.mono_read_continue)
        go.setOnClickListener { ReaderActivity.open(context, book.uri) }
        kinds.drop(1).forEach { (other, otherPlace) ->
            others.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, dp(2))
                addView(kit.text(kindLabel(other.format), MonoKit.SMALL_SP).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(dp(44), -2))
                addView(kit.text(otherPlace.title.ifBlank { other.name }, MonoKit.SMALL_SP).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END },
                    LinearLayout.LayoutParams(0, -2, 1f))
                addView(kit.text("${percent(otherPlace)}%", MonoKit.SMALL_SP, kit.dim).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
                kit.pressable(this, framed = false)
                setOnClickListener { ReaderActivity.open(context, other.uri) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun percent(place: Books.Place) = (place.fraction * 100).toInt()

    private fun kindLabel(format: Books.Format) = kit.context.getString(when (Books.kindOf(format)) {
        Books.Format.PDF -> R.string.reader_shelf_pdf
        Books.Format.TEXT -> R.string.reader_shelf_text
        Books.Format.COMIC -> R.string.reader_shelf_comic
        else -> R.string.reader_shelf_epub
    })

    /** A book's cover as a dot pattern in ink. */
    private inner class Cover : View(kit.context) {
        private val paint = Paint()
        override fun onDraw(canvas: Canvas) {
            paint.color = kit.ink
            val step = maxOf(3, dp(3))
            var y = 0
            while (y < height) {
                var x = if ((y / step) % 2 == 0) 0 else step / 2
                while (x < width) { canvas.drawRect(x.toFloat(), y.toFloat(), (x + 1).toFloat() + kit.density / 2, (y + 1).toFloat() + kit.density / 2, paint); x += step }
                y += step
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = kit.line.toFloat()
            canvas.drawRect(kit.line / 2f, kit.line / 2f, width - kit.line / 2f, height - kit.line / 2f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
