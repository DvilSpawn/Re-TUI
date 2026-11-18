package ohi.andre.consolelauncher.managers.reader

import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderDrawable
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reads one book on e-ink paper. Text books flow into pages at the chosen size; PDFs and comics show their pages in
 * greys. Tap the left or right of a page to turn, the middle for the menu; the volume keys turn too.
 */
class ReaderActivity : LocalizedComponentActivity() {
    private class Laid(val layout: StaticLayout, val breaks: IntArray)

    private val settings by lazy { ReaderSettings(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val chrome by lazy { runCatching { Typeface.createFromAsset(assets, "JetBrainsMonoNerdFont-Regular.ttf") }.getOrDefault(Typeface.MONOSPACE) }

    private lateinit var item: Books.Item
    private lateinit var pageView: PageView
    private lateinit var menu: LinearLayout
    private lateinit var menuTop: LinearLayout
    private lateinit var menuBottom: LinearLayout
    private lateinit var status: TextView

    private var book: BookContent? = null
    private var place = Books.Place()
    private var chapter = 0
    private var page = 0

    // Flowing books: each chapter laid out once per set of text settings.
    private val laid = ConcurrentHashMap<Int, Laid>()
    @Volatile private var generation = 0
    private var paint = TextPaint()
    private var pageWidth = 0
    private var pageHeight = 0
    /** Pages before each chapter, once every chapter is laid out. */
    private var pagesBefore: IntArray? = null
    private var charsBefore = IntArray(0)

    private var tab = Tab.INK
    private enum class Tab { CONTENTS, PINS, TEXT, PAGES, INK }

    // Scrolling pictures: pages drawn so far near the reader, at the column's width, and their heights.
    private val pictures = HashMap<Int, android.graphics.Bitmap>()
    private val pictureHeights = HashMap<Int, Int>()
    private val picturesAsked = HashSet<Int>()
    private var pictureAspect = 1.4f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)
        val known = uri?.let { Books.find(this, it) } ?: uri?.let { Books.addFile(this, android.net.Uri.parse(it)) }
        if (known == null) { finish(); return }
        item = known
        place = Books.place(this, item.uri) ?: Books.Place()
        chapter = place.chapter

        WindowCompat.setDecorFitsSystemWindows(window, false)
        pageView = PageView(this, chrome).apply {
            onTap = ::tapped
            onSwipe = ::swiped
            onZoom = ::zoomed
            onScrolled = ::scrolled
            onFooter = ::nextFooter
            pins = linePins
        }
        status = TextView(this).apply { typeface = chrome; textSize = 12f; gravity = Gravity.CENTER }
        menuTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        menuBottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(menuTop, LinearLayout.LayoutParams(-1, -2))
            // The page between the bars closes the menu.
            addView(View(context).apply { setOnClickListener { closeMenu() } }, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(menuBottom, LinearLayout.LayoutParams(-1, -2))
        }
        val root = FrameLayout(this).apply {
            addView(pageView, FrameLayout.LayoutParams(-1, -1))
            addView(status, FrameLayout.LayoutParams(-1, -1))
            addView(menu, FrameLayout.LayoutParams(-1, -1))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val cut = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.statusBars())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            pageView.setPadding(0, cut.top, 0, insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom)
            menu.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        hideBars()
        applyLook()
        onBackPressedDispatcher.addCallback(this) { if (menu.visibility == View.VISIBLE) closeMenu() else finish() }

        status.text = getString(R.string.reader_opening, item.name)
        worker.execute {
            val opened = runCatching { BookContent.open(applicationContext, item) }
            main.post {
                if (isDestroyed) { opened.getOrNull()?.close(); return@post }
                opened.onSuccess(::opened).onFailure { status.text = getString(R.string.reader_cannot_open, item.name) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_URI) != item.uri) { setIntent(intent); recreate() }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        pageView.post {
            pageView.clearGhosts()
            when (book) {
                is BookContent.Flow -> relayout(place.offset)
                is BookContent.Pages -> showPicture(turned = false)
                null -> Unit
            }
        }
    }

    private fun opened(content: BookContent) {
        book = content
        status.text = ""
        place = place.copy(title = content.title)
        when (content) {
            is BookContent.Flow -> {
                chapter = place.chapter.coerceIn(0, content.chapters.lastIndex)
                var sum = 0
                charsBefore = IntArray(content.chapters.size + 1) { i -> if (i == 0) 0 else { sum += content.chapters[i - 1].text.length; sum } }
                pageView.post { relayout(keepOffset = place.offset) }
            }
            is BookContent.Pages -> {
                page = place.page.coerceIn(0, max(0, content.count - 1))
                pageView.post { showPicture(turned = false) }
            }
        }
    }

    // ---- flowing text ----

    private fun typeface(): Typeface = when (settings.font) {
        ReaderSettings.Font.LITERATA -> BookFaces.literata(this)
        ReaderSettings.Font.MONO -> chrome
        ReaderSettings.Font.SANS -> Typeface.SANS_SERIF
    }

    /** Lays the book out again for the current settings, keeping the reader on the same words. */
    private fun relayout(keepOffset: Int) {
        val flow = book as? BookContent.Flow ?: return
        pageView.marginPx = dp(settings.marginDp())
        pageWidth = pageView.content.width().toInt()
        pageHeight = pageView.content.height().toInt()
        if (pageWidth <= 0 || pageHeight <= 0) return
        paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface()
            textSize = settings.sizeSp * resources.displayMetrics.scaledDensity
            color = settings.paper.ink
        }
        IndentSpan.width = (paint.textSize * 1.4f).roundToInt()
        laid.clear()
        pagesBefore = null
        val run = ++generation
        val start = chapter
        lay(flow, start)
        page = pageAt(start, keepOffset)
        showText(turned = false)
        worker.execute {
            val order = (start + 1 until flow.chapters.size) + (0 until start)
            for (i in order) {
                if (generation != run) return@execute
                laid.getOrPut(i) { build(flow, i) }
            }
            val totals = IntArray(flow.chapters.size + 1)
            for (i in flow.chapters.indices) totals[i + 1] = totals[i] + (laid[i]?.breaks?.size ?: 1)
            main.post { if (generation == run) { pagesBefore = totals; showChrome() } }
        }
    }

    private fun lay(flow: BookContent.Flow, index: Int): Laid = laid.getOrPut(index) { build(flow, index) }

    private fun build(flow: BookContent.Flow, index: Int): Laid {
        val text = flow.chapters[index].text
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, pageWidth)
            .setLineSpacing(0f, settings.spacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
        if (settings.justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        val layout = builder.build()
        val breaks = ArrayList<Int>().apply { add(0) }
        // Each line of the contents starts on a fresh page, as in a printed book.
        val fresh = flow.toc.filter { it.chapter == index && it.offset > 0 }.map { layout.getLineForOffset(it.offset) }.toSet()
        var top = 0
        for (line in 0 until layout.lineCount) {
            if ((line in fresh || layout.getLineBottom(line) - top > pageHeight) && line > breaks.last()) {
                breaks += line
                top = layout.getLineTop(line)
            }
        }
        return Laid(layout, breaks.toIntArray())
    }

    private fun chapterTitle(flow: BookContent.Flow, index: Int, offset: Int) = flow.toc.getOrNull(flow.entryAt(index, offset))?.title.orEmpty()

    /** Characters from this page to the start of the next contents line, or to the end of the book. */
    private fun charsToNextEntry(flow: BookContent.Flow): Int {
        val here = charsBefore[chapter] + place.offset
        val next = flow.toc.getOrNull(flow.entryAt(chapter, place.offset) + 1)
        val end = next?.let { charsBefore[it.chapter] + it.offset } ?: charsBefore.last()
        return max(0, end - here)
    }

    private fun pageAt(index: Int, offset: Int): Int {
        val l = laid[index] ?: return 0
        val line = l.layout.getLineForOffset(offset.coerceIn(0, l.layout.text.length))
        return max(0, l.breaks.indexOfLast { it <= line })
    }

    // ---- reading speed ----

    /** Where the reader was in the book, in characters, and when; 0 when not timing (away, or just back). */
    private var timedChars = 0
    private var timedAt = 0L

    /**
     * Learns the reader's speed from moving through the book. A stretch only counts when it is forward, at least a
     * few seconds long and at a believable pace, so skimming, jumps and putting the book down are left out.
     */
    private fun timeReading() {
        val here = charsBefore.getOrElse(chapter) { 0 } + place.offset
        val now = android.os.SystemClock.elapsedRealtime()
        if (timedAt > 0) {
            val read = here - timedChars
            val minutes = (now - timedAt) / 60_000f
            if (read > 0 && minutes * 60 >= MIN_TIMED_SECONDS) {
                val pace = read / minutes
                if (pace in MIN_PACE..MAX_PACE) settings.speed = settings.speed + (pace - settings.speed) * LEARN
            }
        }
        timedChars = here
        timedAt = now
    }

    override fun onResume() {
        super.onResume()
        timedAt = 0L
        if (book is BookContent.Flow) timeReading()
    }

    private fun nextFooter() {
        val all = ReaderSettings.Footer.entries
        settings.footer = all[(settings.footer.ordinal + 1) % all.size]
        showChrome()
    }

    /** Minutes as "4 min", or "2 h 05 min" past an hour. */
    private fun duration(chars: Int): String {
        val minutes = max(1, (chars / settings.speed).roundToInt())
        return if (minutes < 60) getString(R.string.reader_time_min, minutes) else getString(R.string.reader_time_hours, minutes / 60, minutes % 60)
    }

    /** Pages from here to the next contents line, or to the end of the book; null until every chapter is laid out. */
    private fun pagesToNextEntry(flow: BookContent.Flow): Int? {
        val total = pagesBefore ?: return null
        val next = flow.toc.getOrNull(flow.entryAt(chapter, place.offset) + 1)
        val end = if (next == null) total.last() else if (laid[next.chapter] == null) return null else total[next.chapter] + pageAt(next.chapter, next.offset)
        return max(0, end - (total[chapter] + page))
    }

    private fun showText(turned: Boolean) {
        val flow = book as? BookContent.Flow ?: return
        val l = lay(flow, chapter)
        page = page.coerceIn(0, l.breaks.lastIndex)
        val bottom = l.breaks.getOrElse(page + 1) { l.layout.lineCount }
        if (direction() == ReaderSettings.Direction.SCROLL) pageView.showStrip(textStrip, chapter, l.layout.getLineTop(l.breaks[page]).toFloat())
        else pageView.show(PageView.Page.Text(l.layout, l.breaks[page], bottom), turned)
        val offset = l.layout.getLineStart(l.breaks[page])
        place = place.copy(chapter = chapter, offset = offset, chapterTitle = chapterTitle(flow, chapter, offset),
            fraction = (charsBefore[chapter] + offset).toFloat() / max(1, charsBefore.last()))
        timeReading()
        showChrome()
        save()
    }

    // ---- pictures ----

    private fun showPicture(turned: Boolean, scale: Float = 1f) {
        val pages = book as? BookContent.Pages ?: return
        if (pages.count == 0) { status.text = getString(R.string.reader_no_pages); return }
        pageView.marginPx = dp(PICTURE_MARGIN)
        if (direction() == ReaderSettings.Direction.SCROLL) {
            // Drawn again from scratch: the look or the width may have changed.
            generation++
            pictures.clear(); pictureHeights.clear(); picturesAsked.clear()
            pageView.showStrip(pictureStrip, page, 0f)
            place = place.copy(page = page, pages = pages.count, fraction = (page + 1f) / pages.count)
            showChrome()
            save()
            return
        }
        val w = (pageView.content.width() * scale).toInt()
        val h = (pageView.content.height() * scale).toInt()
        val index = page
        val run = ++generation
        val eInk = !settings.themeLook
        val paper = settings.paper
        worker.execute {
            val bitmap = pages.render(index, w, h)?.let { if (eInk) Greys.apply(it, paper.ink, paper.paper) else it }
            main.post {
                if (generation != run || bitmap == null) return@post
                if (scale > 1f) pageView.sharpen(bitmap) else pageView.show(PageView.Page.Picture(bitmap), turned)
            }
        }
        place = place.copy(page = index, pages = pages.count, fraction = (index + 1f) / pages.count)
        showChrome()
        save()
    }

    private fun zoomed(zoom: Float) {
        if (zoom > 1f) showPicture(turned = false, scale = zoom)
    }

    // ---- scrolling ----

    private fun direction() = if (book is BookContent.Pages) settings.directionPictures else settings.directionText

    /** The chapters one after another, with a little room between them. */
    private val textStrip = object : PageView.Strip {
        override val count: Int get() = (book as? BookContent.Flow)?.chapters?.size ?: 0
        override fun height(index: Int): Int = (book as? BookContent.Flow)?.let { lay(it, index).layout.height + dp(CHAPTER_GAP) } ?: 0
        override fun layout(index: Int): Layout? = (book as? BookContent.Flow)?.let { lay(it, index).layout }
        override fun bitmap(index: Int): android.graphics.Bitmap? = null
    }

    /** The pages one after another at the column's width; comics run on without a gap, as webtoons need. */
    private val pictureStrip = object : PageView.Strip {
        override val count: Int get() = (book as? BookContent.Pages)?.count ?: 0
        override fun height(index: Int): Int = (pictureHeights[index] ?: (pageView.content.width() * pictureAspect).toInt()) + pictureGap()
        override fun layout(index: Int): Layout? = null
        override fun bitmap(index: Int): android.graphics.Bitmap? = pictures[index] ?: null.also { askPicture(index) }
    }

    private fun pictureGap() = if (item.format == Books.Format.COMIC) 0 else dp(PICTURE_GAP)

    private fun askPicture(index: Int) {
        val pages = book as? BookContent.Pages ?: return
        if (index !in 0 until pages.count || !picturesAsked.add(index)) return
        val w = pageView.content.width().toInt()
        if (w <= 0) { picturesAsked.remove(index); return }
        val run = generation
        val eInk = !settings.themeLook
        val paper = settings.paper
        worker.execute {
            val bitmap = pages.render(index, w, w * TALLEST)?.let { if (eInk) Greys.apply(it, paper.ink, paper.paper) else it }
            main.post {
                if (generation != run) return@post
                if (bitmap == null) return@post
                pictures[index] = bitmap
                val h = (bitmap.height * pageView.content.width() / bitmap.width).toInt()
                pictureHeights[index] = h
                pictureAspect = h / max(1f, pageView.content.width())
                // Only the pages near the reader stay drawn.
                pictures.keys.filter { abs(it - page) > KEEP_PICTURES }.forEach { pictures.remove(it); picturesAsked.remove(it) }
                pageView.invalidate()
            }
        }
    }

    private fun scrolled(piece: Int, offset: Int, settled: Boolean) {
        when (val b = book) {
            is BookContent.Flow -> {
                chapter = piece
                val l = lay(b, chapter)
                val at = l.layout.getLineStart(l.layout.getLineForVertical(offset))
                page = pageAt(chapter, at)
                place = place.copy(chapter = chapter, offset = at, chapterTitle = chapterTitle(b, chapter, at),
                    fraction = (charsBefore[chapter] + at).toFloat() / max(1, charsBefore.last()))
            }
            is BookContent.Pages -> {
                page = piece
                askPicture(piece + 1)
                place = place.copy(page = page, pages = b.count, fraction = (page + 1f) / b.count)
            }
            null -> return
        }
        if (settled && book is BookContent.Flow) timeReading()
        showChrome()
        if (settled) save()
    }

    // ---- turning ----

    private fun tapped(side: Int) {
        if (side == 0) { if (menu.visibility == View.VISIBLE) closeMenu() else openMenu(); return }
        if (menu.visibility == View.VISIBLE) { closeMenu(); return }
        turn(forward(side))
    }

    private fun swiped(side: Int) {
        if (!settings.swipe) return
        if (menu.visibility == View.VISIBLE) { closeMenu(); return }
        turn(forward(side))
    }

    /** A tap or swipe toward [side], as a move through the book: right to left reads the other way. */
    private fun forward(side: Int) = if (direction() == ReaderSettings.Direction.RTL) -side else side

    private fun turn(direction: Int) {
        if (pageView.scrolling) { pageView.scrollPage(direction); return }
        when (val b = book) {
            is BookContent.Flow -> {
                val l = lay(b, chapter)
                when {
                    direction > 0 && page < l.breaks.lastIndex -> page++
                    direction > 0 && chapter < b.chapters.lastIndex -> { chapter++; page = 0 }
                    direction < 0 && page > 0 -> page--
                    direction < 0 && chapter > 0 -> { chapter--; page = lay(b, chapter).breaks.lastIndex }
                    else -> return
                }
                showText(turned = true)
            }
            is BookContent.Pages -> {
                val next = page + direction
                if (next !in 0 until b.count) return
                page = next
                showPicture(turned = true)
            }
            null -> Unit
        }
    }

    private fun goTo(chapterIndex: Int, offset: Int) {
        val flow = book as? BookContent.Flow ?: return
        chapter = chapterIndex.coerceIn(0, flow.chapters.lastIndex)
        lay(flow, chapter)
        page = pageAt(chapter, offset)
        pageView.clearGhosts()
        showText(turned = false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (settings.volumeKeys && menu.visibility != View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> { turn(1); return true }
                KeyEvent.KEYCODE_VOLUME_UP -> { turn(-1); return true }
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> { turn(1); return true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> { turn(-1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (settings.volumeKeys && menu.visibility != View.VISIBLE &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }

    // ---- chrome ----

    private fun showChrome() {
        pageView.head = place.title.ifEmpty { item.name }
        pageView.progress = place.fraction
        when (val b = book) {
            is BookContent.Flow -> {
                pageView.chapter = place.chapterTitle
                val total = pagesBefore
                pageView.footLeft = if (total != null) "${total[chapter] + page + 1} / ${total.last()}" else "${(place.fraction * 100).toInt()}%"
                pageView.footRight = when (settings.footer) {
                    ReaderSettings.Footer.CHAPTER_TIME -> getString(R.string.reader_left_chapter, duration(charsToNextEntry(b)))
                    ReaderSettings.Footer.BOOK_TIME -> getString(R.string.reader_left_book, duration(charsBefore.last() - charsBefore[chapter] - place.offset))
                    ReaderSettings.Footer.CHAPTER_PAGES -> pagesToNextEntry(b)?.let { resources.getQuantityString(R.plurals.reader_pages_left, it, it) }
                        ?: getString(R.string.reader_left_chapter, duration(charsToNextEntry(b)))
                    ReaderSettings.Footer.PERCENT -> getString(R.string.reader_percent_read, (place.fraction * 100).toInt())
                }
            }
            is BookContent.Pages -> {
                pageView.chapter = ""
                pageView.footLeft = "${page + 1} / ${b.count}"
                pageView.footRight = if (pageView.scrolling) "" else getString(R.string.reader_double_tap_zoom)
            }
            null -> Unit
        }
        pageView.invalidate()
    }

    private fun applyLook() {
        val (paperColour, inkColour) = colours()
        pageView.colours(paperColour, inkColour,
            if (settings.themeLook) 0 else ReaderSettings.grainAlpha(settings.grain), ReaderSettings.ghost(settings.ghosting))
        pageView.refreshEvery = settings.refresh.every
        status.setTextColor(inkColour)
        window.decorView.setBackgroundColor(paperColour)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = ColorUtils.calculateLuminance(paperColour) > 0.5
    }

    private fun colours(): Pair<Int, Int> = if (settings.themeLook)
        ThemeColorResolver.color(Theme.workspace_panel_background_color) to ThemeColorResolver.color(Theme.workspace_panel_text_color)
        else settings.paper.paper to settings.paper.ink

    private fun hideBars() = WindowInsetsControllerCompat(window, window.decorView).run {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun save() {
        place = place.copy(openedAt = System.currentTimeMillis())
        Books.keep(this, item.uri, place)
    }

    override fun onPause() {
        if (book != null) save()
        super.onPause()
    }

    override fun onDestroy() {
        generation++
        val b = book
        worker.execute { b?.close() }
        worker.shutdown()
        super.onDestroy()
    }

    // ---- menu ----

    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()

    private fun openMenu() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        buildMenu()
        menu.visibility = View.VISIBLE
    }

    private fun closeMenu() {
        menu.visibility = View.GONE
        hideBars()
    }

    private fun text(value: String, size: Float = 12f, bold: Boolean = false) = TextView(this).apply {
        text = value
        typeface = if (bold) Typeface.create(chrome, Typeface.BOLD) else chrome
        textSize = size
        setTextColor(colours().second)
        includeFontPadding = false
    }

    /** A control's outline; in the theme look it follows cyberdeck mode, while e-ink paper keeps plain lines. */
    private fun framed(fill: Int): android.graphics.drawable.Drawable = if (settings.themeLook && AppearanceSettings.cyberdeckMode())
        TerminalBorderDrawable(fill, colours().second, max(1, dp(1)) + 1, 0f, false, 0f, 0f, cyberdeck = true, cyberdeckNotch = false)
        else GradientDrawable().apply { setColor(fill); setStroke(max(1, dp(1)) + 1, colours().second) }

    /** A slim control: [visual]dp tall inside a 44dp touch target, filled when [on]. */
    private fun button(label: String, on: Boolean = false, desc: String = label, minWidth: Int = 0, action: () -> Unit) = text(label, 11f, bold = true).apply {
        val (paper, ink) = colours()
        gravity = Gravity.CENTER
        contentDescription = desc
        setTextColor(if (on) paper else ink)
        background = android.graphics.drawable.InsetDrawable(framed(if (on) ink else Color.TRANSPARENT), 0, dp(4), 0, dp(4))
        setPadding(dp(10), 0, dp(10), 0)
        minimumWidth = dp(minWidth)
        isClickable = true
        setOnClickListener { action() }
    }

    private fun bar(fill: Int, top: Boolean) = GradientDrawable().apply { setColor(fill) }.let { base ->
        android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(colours().second) }, base)).apply {
            if (top) setLayerInset(1, 0, 0, 0, dp(2)) else setLayerInset(1, 0, dp(2), 0, 0)
        }
    }

    private fun buildMenu() {
        val (paper, ink) = colours()
        menuTop.removeAllViews()
        menuTop.background = bar(paper, top = true)
        menuTop.setPadding(dp(12), dp(6), dp(12), dp(8))
        menuTop.addView(button(getString(R.string.reader_library)) { startActivity(Intent(this, ReaderLibraryActivity::class.java)); finish() },
            LinearLayout.LayoutParams(-2, dp(44)))
        menuTop.addView(text(place.title.ifEmpty { item.name }, 13f, bold = true).apply {
            gravity = Gravity.CENTER; isSingleLine = true; ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8); rightMargin = dp(8) })
        if (book is BookContent.Flow) menuTop.addView(button("", desc = getString(R.string.reader_search), minWidth = 44) { askSearch() }.apply {
            setCompoundDrawables(Magnifier(colours().second, dp(16)), null, null, null); setPadding(dp(14), 0, dp(14), 0)
        }, LinearLayout.LayoutParams(-2, dp(44)))
        val marked = currentMark() in place.marks
        menuTop.addView(button("", on = marked, desc = getString(if (marked) R.string.reader_unpin_page else R.string.reader_bookmark), minWidth = 44) { toggleMark() }.apply {
            setCompoundDrawables(PinIcon(if (marked) paper else ink, dp(16), filled = marked), null, null, null); setPadding(dp(14), 0, dp(14), 0)
        },
            LinearLayout.LayoutParams(-2, dp(44)).apply { leftMargin = dp(6) })

        menuBottom.removeAllViews()
        menuBottom.background = bar(paper, top = false)
        menuBottom.setPadding(dp(14), dp(12), dp(14), dp(16))
        menuBottom.addView(progressRow(), LinearLayout.LayoutParams(-1, -2))
        val tabs = LinearLayout(this)
        // Pictures have no contents to list and no words to pin; their pins are starred pages.
        val shown = if (book is BookContent.Pages) listOf(Tab.PINS, Tab.PAGES, Tab.INK) else Tab.entries
        if (tab !in shown) tab = Tab.INK
        shown.forEachIndexed { i, option ->
            val name = getString(when (option) {
                Tab.CONTENTS -> R.string.reader_tab_contents; Tab.PINS -> R.string.reader_tab_pins; Tab.TEXT -> R.string.reader_tab_text
                Tab.PAGES -> R.string.reader_tab_pages; Tab.INK -> R.string.reader_tab_eink
            })
            // Five tabs share the width, so their labels sit closer to their outlines.
            tabs.addView(button(name, on = tab == option) { tab = option; buildMenu() }.apply { textSize = 10f; setPadding(dp(2), 0, dp(2), 0); isSingleLine = true },
                LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) leftMargin = dp(4) })
        }
        menuBottom.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        when (tab) {
            Tab.CONTENTS -> contents(body)
            Tab.PINS -> pinsList(body)
            Tab.TEXT -> textOptions(body)
            Tab.PAGES -> pageOptions(body)
            Tab.INK -> inkOptions(body)
        }
        menuBottom.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(body) },
            LinearLayout.LayoutParams(-1, dp(MENU_BODY)).apply { topMargin = dp(8) })
        status.setTextColor(ink)
    }

    private fun progressRow(): View {
        val ink = colours().second
        val label = text("", 10f)
        val count = text("", 10f)
        val seek = SeekBar(this).apply {
            max = SEEK_MAX
            progress = (place.fraction * SEEK_MAX).toInt()
            contentDescription = getString(R.string.reader_position)
            progressDrawable = Hairline(ColorUtils.blendARGB(colours().first, ink, 0.35f), dp(2).toFloat())
            thumb = GradientDrawable().apply { setColor(ink); setSize(dp(4), dp(16)) }
            splitTrack = false
        }
        fun describe() {
            when (val b = book) {
                is BookContent.Flow -> {
                    val title = place.chapterTitle
                    label.text = title.ifEmpty { getString(R.string.reader_chapter_n, chapter + 1) }.let { if (it.length > 18) it.take(17) + "…" else it }
                    count.text = pageView.footLeft
                }
                is BookContent.Pages -> { label.text = ""; count.text = "${page + 1} / ${b.count}" }
                null -> Unit
            }
        }
        describe()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, user: Boolean) {}
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                val fraction = bar.progress / SEEK_MAX.toFloat()
                when (val b = book) {
                    is BookContent.Flow -> {
                        val target = (fraction * charsBefore.last()).toInt()
                        val index = (charsBefore.indexOfLast { it <= target }).coerceIn(0, b.chapters.lastIndex)
                        goTo(index, target - charsBefore[index])
                    }
                    is BookContent.Pages -> { page = (fraction * (b.count - 1)).roundToInt(); pageView.clearGhosts(); showPicture(turned = false) }
                    null -> Unit
                }
                describe()
            }
        })
        return LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label)
            addView(seek, LinearLayout.LayoutParams(0, dp(36), 1f))
            addView(count)
        }
    }

    private fun row(name: Int, vararg controls: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(text(getString(name), 11f), LinearLayout.LayoutParams(0, -2, 1f))
        controls.forEachIndexed { i, c -> addView(c, LinearLayout.LayoutParams(if (c is TextView) -2 else dp(SWATCH), dp(44)).apply { if (i > 0) leftMargin = dp(6) }) }
    }

    /** A setting changed: draw again, and lay the text out again when the change moves words between pages. */
    private fun changed(before: String) {
        applyLook()
        if (settings.layoutKey() != before) relayout(place.offset)
        else if (book is BookContent.Pages) showPicture(turned = false)
        buildMenu()
    }

    private fun textOptions(body: LinearLayout) {
        val s = settings
        fun set(edit: () -> Unit) { val key = s.layoutKey(); edit(); changed(key) }
        body.addView(row(R.string.reader_font,
            button("Literata", s.font == ReaderSettings.Font.LITERATA) { set { s.font = ReaderSettings.Font.LITERATA } },
            button(getString(R.string.reader_font_mono), s.font == ReaderSettings.Font.MONO) { set { s.font = ReaderSettings.Font.MONO } },
            button(getString(R.string.reader_font_sans), s.font == ReaderSettings.Font.SANS) { set { s.font = ReaderSettings.Font.SANS } }))
        body.addView(row(R.string.reader_size,
            button("A−", desc = getString(R.string.reader_smaller), minWidth = 44) { set { s.sizeSp -= 1 } },
            text(s.sizeSp.toString(), 12f).apply { gravity = Gravity.CENTER; minWidth = dp(30) },
            button("A+", desc = getString(R.string.reader_larger), minWidth = 44) { set { s.sizeSp += 1 } }))
        body.addView(row(R.string.reader_margins, *listOf("S", "M", "L").mapIndexed { i, name ->
            button(name, s.margin == i, minWidth = 40) { set { s.margin = i } }
        }.toTypedArray()))
        body.addView(row(R.string.reader_spacing, *ReaderSettings.SPACINGS.map { value ->
            button(value.toString(), s.spacing == value) { set { s.spacing = value } }
        }.toTypedArray()))
        body.addView(row(R.string.reader_justify,
            button(getString(R.string.reader_on), s.justify) { set { s.justify = true } },
            button(getString(R.string.reader_off), !s.justify) { set { s.justify = false } }))
    }

    private fun pageOptions(body: LinearLayout) {
        val s = settings
        val forPictures = book is BookContent.Pages
        val current = direction()
        body.addView(row(R.string.reader_direction, *ReaderSettings.Direction.entries.map { option ->
            button(getString(when (option) {
                ReaderSettings.Direction.LTR -> R.string.reader_dir_ltr; ReaderSettings.Direction.RTL -> R.string.reader_dir_rtl; else -> R.string.reader_dir_scroll
            }), current == option) {
                if (forPictures) s.directionPictures = option else s.directionText = option
                pageView.clearGhosts()
                when (book) {
                    is BookContent.Flow -> showText(turned = false)
                    is BookContent.Pages -> showPicture(turned = false)
                    null -> Unit
                }
                buildMenu()
            }
        }.toTypedArray()))
        body.addView(row(R.string.reader_swipe,
            button(getString(R.string.reader_on), s.swipe) { s.swipe = true; buildMenu() },
            button(getString(R.string.reader_off), !s.swipe) { s.swipe = false; buildMenu() }))
        body.addView(row(R.string.reader_volume_keys,
            button(getString(R.string.reader_on), s.volumeKeys) { s.volumeKeys = true; buildMenu() },
            button(getString(R.string.reader_off), !s.volumeKeys) { s.volumeKeys = false; buildMenu() }))
        body.addView(text(getString(R.string.reader_direction_note), 10f).apply {
            setTextColor(ColorUtils.blendARGB(colours().first, colours().second, 0.65f)); setPadding(0, dp(6), 0, 0)
        })
    }

    private fun inkOptions(body: LinearLayout) {
        val s = settings
        fun set(edit: () -> Unit) { val key = s.layoutKey(); edit(); changed(key) }
        body.addView(row(R.string.reader_look,
            button(getString(R.string.reader_look_eink), !s.themeLook) { set { s.themeLook = false } },
            button(getString(R.string.reader_look_theme), s.themeLook) { set { s.themeLook = true } }))
        if (!s.themeLook) {
            val names = mapOf(ReaderSettings.Paper.GREY to R.string.reader_paper_grey, ReaderSettings.Paper.WARM to R.string.reader_paper_warm,
                ReaderSettings.Paper.COOL to R.string.reader_paper_cool, ReaderSettings.Paper.NIGHT to R.string.reader_paper_night)
            body.addView(row(R.string.reader_paper, *ReaderSettings.Paper.entries.map { option ->
                View(this).apply {
                    contentDescription = getString(names.getValue(option))
                    background = android.graphics.drawable.InsetDrawable(GradientDrawable().apply {
                        setColor(option.paper); setStroke(if (s.paper == option) dp(3) else dp(1) + 1, colours().second)
                    }, 0, dp(4), 0, dp(4))
                    isClickable = true
                    setOnClickListener { set { s.paper = option } }
                }
            }.toTypedArray()))
            body.addView(row(R.string.reader_grain, *levels(s.grain, R.string.reader_strong) { level -> set { s.grain = level } }))
        }
        body.addView(row(R.string.reader_ghosting, *levels(s.ghosting, R.string.reader_real) { level -> set { s.ghosting = level } }))
        body.addView(row(R.string.reader_refresh, *ReaderSettings.Refresh.entries.map { option ->
            button(getString(when (option) { ReaderSettings.Refresh.OFF -> R.string.reader_off; ReaderSettings.Refresh.SIX -> R.string.reader_refresh_six; else -> R.string.reader_refresh_every }),
                s.refresh == option) { set { s.refresh = option } }
        }.toTypedArray()))
        body.addView(text(getString(R.string.reader_ghost_note), 10f).apply {
            setTextColor(ColorUtils.blendARGB(colours().first, colours().second, 0.65f)); setPadding(0, dp(6), 0, 0)
        })
    }

    private fun levels(current: ReaderSettings.Level, strongName: Int, pick: (ReaderSettings.Level) -> Unit) = ReaderSettings.Level.entries.map { level ->
        val name = getString(when (level) { ReaderSettings.Level.OFF -> R.string.reader_off; ReaderSettings.Level.FAINT -> R.string.reader_faint; else -> strongName })
        button(name, current == level) { pick(level) }
    }.toTypedArray()

    private fun contents(body: LinearLayout) {
        val ink = colours().second
        val dim = ColorUtils.blendARGB(colours().first, ink, 0.6f)
        fun line(left: String, right: String, on: Boolean, tap: () -> Unit) = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(40)
            background = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            addView(text(left, 12f, bold = on).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(right, 11f).apply { setTextColor(dim) })
            isClickable = true
            setOnClickListener { tap(); closeMenu() }
        }
        val flow = book as? BookContent.Flow ?: return
        body.addView(text(getString(R.string.reader_tab_contents), 10f).apply { setTextColor(dim); setPadding(0, dp(8), 0, dp(4)) })
        val here = flow.entryAt(chapter, place.offset)
        var current: View? = null
        flow.toc.forEachIndexed { i, entry ->
            val pageNumber = pagesBefore?.let { totals -> laid[entry.chapter]?.let { (totals[entry.chapter] + pageAt(entry.chapter, entry.offset) + 1).toString() } }.orEmpty()
            val view = line(entry.title, pageNumber, i == here) { goTo(entry.chapter, entry.offset) }
            if (i == here) current = view
            body.addView(view)
            body.addView(View(this).apply { setBackgroundColor(ColorUtils.blendARGB(colours().first, ink, 0.25f)) }, LinearLayout.LayoutParams(-1, 1))
        }
        current?.let { view -> body.post { (body.parent as? ScrollView)?.scrollTo(0, max(0, view.top - dp(80))) } }
    }

    // ---- pins ----

    private fun chapterOf(layout: Layout): Int? = laid.entries.firstOrNull { it.value.layout === layout }?.key

    /** Pinned passages, as the page draws and changes them. A selection over a pin unpins it, else it pins. */
    private val linePins = object : PageView.Pins {
        override fun ranges(layout: Layout): List<IntRange> {
            if (place.pins.isEmpty()) return emptyList()
            val c = chapterOf(layout) ?: return emptyList()
            return place.pins.filter { it.chapter == c }.map { it.start until it.end }
        }

        override fun toggle(layout: Layout, start: Int, end: Int, text: String) {
            val c = chapterOf(layout) ?: return
            val touched = place.pins.filter { it.chapter == c && it.start < end && it.end > start }
            val pins = if (touched.isNotEmpty()) place.pins - touched.toSet()
                else (place.pins + Books.Pin(c, start, end, text.trim().take(MAX_PIN_TEXT))).sortedWith(compareBy({ it.chapter }, { it.start }))
            place = place.copy(pins = pins)
            save()
            pageView.invalidate()
        }
    }

    /** Where a pin is, as the reader sees it: its part of the book and, once known, its page. */
    private fun pinPlace(pin: Books.Pin): String {
        val flow = book as? BookContent.Flow
        val title = flow?.let { chapterTitle(it, pin.chapter, pin.start) }.orEmpty().ifEmpty { getString(R.string.reader_chapter_n, pin.chapter + 1) }
        val number = pagesBefore?.let { totals -> laid[pin.chapter]?.let { totals[pin.chapter] + pageAt(pin.chapter, pin.start) + 1 } }
        return listOfNotNull(title.uppercase(), number?.let { getString(R.string.reader_page_n, it) }).joinToString(" · ")
    }

    private fun copy(value: String) {
        (getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager)?.setPrimaryClip(android.content.ClipData.newPlainText(item.name, value))
        if (Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(this, R.string.reader_copied, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun share(value: String) = runCatching {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value), null))
    }

    /** Every pin as plain text: the book's name, then each passage and where it is. */
    private fun pinsText(): String = buildString {
        append(place.title.ifEmpty { item.name })
        place.pins.forEach { pin -> append("\n\n“").append(pin.text).append("”\n— ").append(pinPlace(pin)) }
    }

    /** The PINS tab: pinned lines as quotes, then starred pages. Tap one to go there; hold it to copy or remove it. */
    private fun pinsList(body: LinearLayout) {
        val (paper, ink) = colours()
        val dim = ColorUtils.blendARGB(paper, ink, 0.6f)
        val rule = ColorUtils.blendARGB(paper, ink, 0.3f)
        fun header(label: String, action: View? = null) = body.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(36)
            addView(text(label, 10f).apply { setTextColor(dim) }, LinearLayout.LayoutParams(0, -2, 1f))
            action?.let { addView(it, LinearLayout.LayoutParams(-2, dp(40))) }
        })
        fun row(content: View, tap: () -> Unit, hold: () -> Unit) = body.addView(content.apply {
            background = android.graphics.drawable.LayerDrawable(arrayOf(GradientDrawable().apply { setColor(rule) },
                GradientDrawable().apply { setColor(paper) })).apply { setLayerInset(1, 0, 0, 0, max(1, dp(1))) }
            isClickable = true
            isLongClickable = true
            setOnClickListener { tap(); closeMenu() }
            setOnLongClickListener { hold(); true }
        }, LinearLayout.LayoutParams(-1, -2))
        fun ask(title: String, value: String, remove: () -> Unit) = TuixtDialog.showOptions(this, title,
            listOf(getString(android.R.string.copy), getString(R.string.reader_remove_pin))) { choice ->
            if (choice == 0) copy(value) else { remove(); save(); buildMenu(); pageView.invalidate() }
        }

        val flow = book as? BookContent.Flow
        if (flow != null) {
            header(getString(R.string.reader_pinned_lines, place.pins.size),
                if (place.pins.isEmpty()) null else button(getString(R.string.reader_share_all), desc = getString(R.string.reader_share_all)) { share(pinsText()) }.apply { textSize = 10f })
            if (place.pins.isEmpty()) body.addView(text(getString(R.string.reader_pins_empty), 11f).apply {
                setTextColor(dim); setLineSpacing(0f, 1.3f); setPadding(0, dp(2), 0, dp(10))
            })
            val face = Typeface.create(BookFaces.literata(this), Typeface.ITALIC)
            place.pins.forEach { pin ->
                row(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    addView(text("“${pin.text.replace('\n', ' ')}”", 14f).apply {
                        typeface = face; maxLines = 4; ellipsize = TextUtils.TruncateAt.END; setLineSpacing(0f, 1.25f)
                    })
                    addView(text(pinPlace(pin), 10f).apply { setTextColor(dim); setPadding(0, dp(5), 0, 0) })
                }, tap = { goTo(pin.chapter, pin.start) }, hold = { ask(getString(R.string.reader_tab_pins), pin.text) { place = place.copy(pins = place.pins - pin) } })
            }
        }

        header(getString(R.string.reader_pinned_pages, place.marks.size))
        if (place.marks.isEmpty()) body.addView(text(getString(R.string.reader_pages_empty), 11f).apply {
            setTextColor(dim); setLineSpacing(0f, 1.3f); setPadding(0, dp(2), 0, dp(10))
        })
        place.marks.forEach { (c, o) ->
            val label = when (val b = book) {
                is BookContent.Flow -> b.chapters.getOrNull(c)?.text?.let { t -> t.subSequence(o.coerceIn(0, t.length), (o + 60).coerceIn(0, t.length)).toString().replace('\n', ' ').trim() }.orEmpty()
                else -> getString(R.string.reader_page_n, c + 1)
            }
            val number = if (flow != null) pagesBefore?.let { totals -> laid[c]?.let { getString(R.string.reader_page_n, totals[c] + pageAt(c, o) + 1) } }.orEmpty() else ""
            row(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(44)
                addView(View(context).apply { background = PinIcon(ink, dp(13), filled = true) }, LinearLayout.LayoutParams(dp(13), dp(13)).apply { rightMargin = dp(9) })
                addView(text(label, 12f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(text(number, 11f).apply { setTextColor(dim); setPadding(dp(8), 0, 0, 0) })
            }, tap = { jumpToMark(c, o) }, hold = { ask(getString(R.string.reader_tab_pins), label) { place = place.copy(marks = place.marks - (c to o)) } })
        }
        if (place.pins.isNotEmpty() || place.marks.isNotEmpty())
            body.addView(text(getString(R.string.reader_pins_note), 10f).apply { setTextColor(dim); setPadding(0, dp(10), 0, 0) })
    }

    private fun currentMark(): Pair<Int, Int> = when (book) {
        is BookContent.Pages -> page to 0
        else -> chapter to place.offset
    }

    private fun toggleMark() {
        val mark = currentMark()
        place = place.copy(marks = if (mark in place.marks) place.marks - mark else (place.marks + mark).sortedWith(compareBy({ it.first }, { it.second })))
        save()
        buildMenu()
    }

    private fun jumpToMark(c: Int, o: Int) {
        when (book) {
            is BookContent.Flow -> goTo(c, o)
            is BookContent.Pages -> { page = c; pageView.clearGhosts(); showPicture(turned = false) }
            null -> Unit
        }
    }

    private fun askSearch() = TuixtDialog.showInput(this, getString(R.string.reader_search), getString(R.string.reader_search_hint),
        getString(R.string.reader_find), getString(android.R.string.cancel)) { query -> find(query.orEmpty().trim()) }

    /** The next place after this page where [query] appears, wrapping round to the start. */
    private fun find(query: String) {
        val flow = book as? BookContent.Flow ?: return
        if (query.isEmpty()) return
        val l = laid[chapter]
        val from = l?.let { shown -> shown.layout.getLineEnd(shown.breaks.getOrElse(page + 1) { shown.layout.lineCount } - 1) } ?: place.offset
        val n = flow.chapters.size
        for (step in 0..n) {
            val i = (chapter + step) % n
            val start = if (step == 0) from else 0
            val at = flow.chapters[i].text.indexOf(query, start, ignoreCase = true)
            if (at >= 0) { goTo(i, at); closeMenu(); return }
        }
        android.widget.Toast.makeText(this, getString(R.string.reader_not_found, query), android.widget.Toast.LENGTH_SHORT).show()
    }


    /** The slider's track: a thin line across the middle. */
    private class Hairline(color: Int, private val thickness: Float) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint().apply { this.color = color }
        override fun draw(canvas: android.graphics.Canvas) {
            val y = bounds.exactCenterY()
            canvas.drawRect(bounds.left.toFloat(), y - thickness / 2, bounds.right.toFloat(), y + thickness / 2, paint)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** A push pin, drawn in outline until it is pinned. */
    private class PinIcon(color: Int, size: Int, filled: Boolean) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = if (filled) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
            strokeWidth = 1.3f
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        private val glyph = android.graphics.Path().apply {
            moveTo(3f, 0f); lineTo(9f, 0f); lineTo(9f, 2f); lineTo(8f, 2f); lineTo(8f, 6f); lineTo(11f, 9f); lineTo(11f, 10f)
            lineTo(7f, 10f); lineTo(7f, 14f); lineTo(5f, 14f); lineTo(5f, 10f); lineTo(1f, 10f); lineTo(1f, 9f); lineTo(4f, 6f)
            lineTo(4f, 2f); lineTo(3f, 2f); close()
        }
        init { setBounds(0, 0, size, size) }
        override fun draw(canvas: android.graphics.Canvas) {
            val scale = bounds.height() / 15f
            canvas.save()
            canvas.translate(bounds.left + (bounds.width() - 12 * scale) / 2, bounds.top + scale / 2)
            canvas.scale(scale, scale)
            canvas.drawPath(glyph, paint)
            canvas.restore()
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** A search glass: a ring and a handle. */
    private class Magnifier(color: Int, size: Int) : android.graphics.drawable.Drawable() {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; style = android.graphics.Paint.Style.STROKE; strokeWidth = size / 9f
        }
        init { setBounds(0, 0, size, size) }
        override fun draw(canvas: android.graphics.Canvas) {
            val s = bounds.width().toFloat()
            canvas.drawCircle(s * 0.42f, s * 0.42f, s * 0.3f, paint)
            canvas.drawLine(s * 0.64f, s * 0.64f, s * 0.92f, s * 0.92f, paint)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(filter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    companion object {
        const val EXTRA_URI = "uri"
        private const val SWATCH = 36
        /** A stretch of reading counts toward the speed only if it lasts this long and lands in this pace. */
        private const val MIN_TIMED_SECONDS = 4f
        private const val MIN_PACE = 250f
        private const val MAX_PACE = 5000f
        /** How much each stretch moves the learned speed. */
        private const val LEARN = 0.08f
        private const val PICTURE_MARGIN = 8
        /** Room between chapters, and between PDF pages, when scrolling. */
        private const val CHAPTER_GAP = 48
        private const val PICTURE_GAP = 8
        /** A scrolled page may be this many times taller than wide, for long webtoon strips. */
        private const val TALLEST = 12
        private const val KEEP_PICTURES = 3
        private const val MENU_BODY = 290
        private const val SEEK_MAX = 1000
        /** A pin keeps at most this much of its passage to list and share. */
        private const val MAX_PIN_TEXT = 2000

        fun open(context: Context, uri: String) = context.startActivity(Intent(context, ReaderActivity::class.java)
            .putExtra(EXTRA_URI, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
