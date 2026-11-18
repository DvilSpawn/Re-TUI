package ohi.andre.consolelauncher.mono

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.MonoMode
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.presentation.DualScreen
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import kotlin.math.roundToInt

/**
 * Mono mode's home, laid out like a PDA: a header, the panels on a four-column grid (see [MonoLayout]), the
 * command line with its suggestions and answers opening above it, and a bar for the apps, search, widgets and
 * settings. Panels are built when the home is shown and released when it is left ([start], [stop]).
 */
@SuppressLint("ViewConstructor")
class MonoHomeView(context: Context, private val source: Source) : FrameLayout(context) {
    /** What the home shows and what its keys do, supplied by the launcher. */
    class Source(
        val directory: () -> String,
        val weather: () -> String?,
        /** The weather's symbol code, which picks the scene. */
        val weatherSymbol: () -> String?,
        val music: () -> Music?,
        val notifications: () -> List<Action>,
        val reminders: () -> List<String>,
        /** The running or paused timer and stopwatch, already worded; empty when neither is set. */
        val clock: () -> List<String>,
        /** The app tabs: the most used first, then one per app group. */
        val appTabs: () -> List<Tab>,
        /** Runs a command without opening the output panel. */
        val run: (String) -> Unit,
        val openApps: () -> Unit,
        val openWidgets: () -> Unit,
        val openSettings: () -> Unit
    )
    class Music(val title: String, val artist: String?, val playing: Boolean)
    class Action(val label: String, val run: () -> Unit)
    class Tab(val name: String, val apps: List<Action>)

    private companion object {
        /** Panel state that outlives a visit and the layout being rebuilt, such as the selected app tab. */
        val panelState = HashMap<String, String>()
        const val ANSWER_WINDOW_MS = 4_000L
        const val QUIET_CLOSE_MS = 2_500L
        const val OUTPUT_LINES = 10
    }

    private val ink = MonoMode.ink()
    private val paper = MonoMode.paper()
    private val face: Typeface = runCatching { Typeface.createFromAsset(context.assets, MonoMode.FONT_ASSET) }
        .getOrDefault(Typeface.MONOSPACE)
    private val kit = MonoKit(context, ink, paper, face)
    private val handler = Handler(Looper.getMainLooper())

    private val panelHost = MonoPanelHost(source, panelState) { text -> prefill(text) }
    private val panels = ArrayList<MonoPanel>()
    private var started = false

    /** Where the pages or the scrolling column of panels go. */
    private val panelArea = FrameLayout(context)
    private var pages: List<List<MonoLayout.Item>> = MonoLayout.DEFAULT
    private var pager: Pager? = null
    private val dots = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        visibility = GONE
        // Holding the squares adds or removes a page.
        setOnLongClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); choosePages(); true }
    }
    private val terms = MonoTerms(context)
    /** The terminal lifted over the home to type into, and the frame it came from. */
    private var lifted: MonoTermPanel? = null
    private var liftedFrom: ViewGroup? = null
    /** Where each terminal sits in [pages], to keep its window there. */
    private val termSlots = HashMap<MonoTermPanel, Pair<Int, Int>>()
    private var fnKeys = false
    /** The terminal key band, under a lifted terminal. */
    private val keyBand: View = android.view.LayoutInflater.from(context).inflate(R.layout.termux_special_keys, null).apply {
        setBackgroundColor(paper)
    }
    private val liftSpot = FrameLayout(context)
    /** Covers the whole home, above the keyboard, while a terminal is lifted. */
    private val liftLayer = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(paper)
        isClickable = true
        visibility = GONE
        setPadding(dp(6), dp(6), dp(6), dp(6))
        addView(liftSpot, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(keyBand, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
    private val outputCommand = kit.text("").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val outputLines = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val outputPanel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = kit.box()
        setPadding(dp(10), dp(8), dp(10), dp(8))
        visibility = GONE
        isClickable = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(outputCommand, LinearLayout.LayoutParams(0, -2, 1f))
            addView(kit.text("[x]").apply {
                setPadding(dp(6), dp(2), dp(6), dp(2))
                contentDescription = context.getString(R.string.mono_output_close)
                kit.pressable(this, framed = true)
                setOnClickListener { closeOutput() }
            })
        })
        addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(outputLines) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
    }
    private val bottomBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; background = kit.box(); setPadding(kit.line, kit.line, kit.line, kit.line) }
    private val statusTime = kit.text("")
    private val statusSignal = kit.icon(MonoKit.Icons.SIGNAL, android.content.res.ColorStateList.valueOf(ink)).apply { isDuplicateParentStateEnabled = false }
    private val statusBattery = BatteryGlyph()
    private val chooser = FrameLayout(context).apply {
        visibility = GONE
        isClickable = true
        setBackgroundColor(paper and 0x00FFFFFF or 0xCC000000.toInt())
        setOnClickListener { visibility = GONE }
    }

    /** The input line; the launcher wires its suggestions and commands to these. */
    val input = android.widget.EditText(context).apply {
        background = null
        isSingleLine = true
        includeFontPadding = false
        setPadding(0, 0, 0, 0)
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
        if (android.os.Build.VERSION.SDK_INT >= 29) textCursorDrawable = GradientDrawable().apply { setColor(ink); setSize(dp(9), dp(18)) }
    }
    val prefix = kit.text("$")

    /** Mono's look for the input line; call again after the terminal has styled it its own way. */
    fun styleInput() {
        input.typeface = face
        LauncherFontScale.setBaseSize(input, MonoKit.BASE_SP, LauncherFontScale.offsetSp())
        input.setTextColor(ink)
        input.setHintTextColor(kit.dim)
        input.setHint(R.string.mono_prompt_hint)
        input.setShadowLayer(0f, 0f, 0f, 0)
        RetuiThemeBridge.applyToKeyboardInput(input, source.directory(), "launcher")
        prefix.typeface = face
        LauncherFontScale.setBaseSize(prefix, MonoKit.BASE_SP, LauncherFontScale.offsetSp())
        // The prompt is an inverted block at the start of the line.
        prefix.setTextColor(paper)
        prefix.setBackgroundColor(ink)
        prefix.gravity = Gravity.CENTER
        prefix.minWidth = dp(30)
        prefix.maxLines = 1
        prefix.setShadowLayer(0f, 0f, 0f, 0)
    }
    val submit = android.widget.ImageView(context).apply { visibility = GONE }
    /** Where the search suggestions go: above the command line, over the panels, only while typing. */
    val results = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM }
    private val resultsScroll = CappedScroll().apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        addView(results)
        visibility = GONE
    }
    private var keyboardShown = false
    private val statusStrip = LinearLayout(context)
    private val answers = LinearLayout(context)
    private val commandLine = LinearLayout(context)
    private var layoutNow: DualScreen.Layout = DualScreen.Layout.Phone
    /** Moves the command line, or with `duo` on the whole home, to the other screen. Only shown when split. */
    private val swapKey = kit.text(">>").apply {
        gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(8), 0)
        kit.pressable(this, framed = true)
        setOnClickListener { DualScreen.swap(this@MonoHomeView, layoutNow) }
    }
    private val mainBezel = bezel()
    private val pagesBezel = bezel()
    private val inputBezel = bezel()
    private var arrangedAs: Any? = null
    private val onScreensChanged: () -> Unit = { post { arrange() } }
    private val statusRule = View(context).apply { setBackgroundColor(ink) }
    private val panelsFrame = FrameLayout(context)

    private val tick = object : Runnable {
        override fun run() {
            var fast = false
            refreshStatus()
            panels.forEach { if (it.tick()) fast = true }
            handler.postDelayed(this, if (fast) 1_000L else 10_000L)
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
        addView(mainBezel, LayoutParams(-1, -1).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        addView(liftLayer, LayoutParams(-1, -1))
        addView(chooser, LayoutParams(-1, -1))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // The keyboard lifts the command line; the panels give up the room under the suggestions.
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            post { arrange() }
            // Putting the keyboard away with nothing typed means done searching: close the suggestions too.
            val imeShown = ime.bottom > 0
            if (keyboardShown && !imeShown && input.text.isNullOrEmpty()) post { closeInput() }
            keyboardShown = imeShown
            insets
        }

        // Status strip, like a handheld's: the name in an inverted block, then the signal, the time and the battery.
        statusStrip.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(kit.tab("Re:TUI", 22f).apply { isAllCaps = false; text = "Re:TUI" }, LinearLayout.LayoutParams(-2, -1))
            addView(kit.text("MONO", MonoKit.SMALL_SP, kit.dim), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            addView(statusSignal, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(10) })
            addView(statusTime, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(10) })
            addView(statusBattery, LinearLayout.LayoutParams(dp(26), dp(12)))
        }

        // Answers and suggestions open over the panels' bottom edge, or fill the input screen when split.
        answers.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            addView(outputPanel, LinearLayout.LayoutParams(-1, -2))
            addView(resultsScroll, LinearLayout.LayoutParams(-1, -2))
        }

        // The command line: one for every page, outside the pager.
        commandLine.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = kit.box()
            setPadding(kit.line, kit.line, dp(10), kit.line)
            addView(prefix, LinearLayout.LayoutParams(-2, -1))
            addView(input, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(10) })
            addView(submit)
            addView(swapKey, LinearLayout.LayoutParams(-2, -1).apply { marginStart = dp(8) })
            setOnClickListener { input.requestFocus(); showKeyboard() }
        }

        // Soft keys: one framed strip, an icon over each name, like the printed buttons under a handheld's screen.
        listOf(
            Triple(R.string.mono_bar_apps, MonoKit.Icons.APPS) { source.openApps() },
            Triple(R.string.mono_bar_search, MonoKit.Icons.SEARCH) { input.requestFocus(); showKeyboard() },
            Triple(R.string.mono_bar_widgets, MonoKit.Icons.WIDGETS) { source.openWidgets() },
            Triple(R.string.mono_bar_settings, MonoKit.Icons.SETTINGS) { source.openSettings() }
        ).forEachIndexed { index, (label, icon, run) ->
            if (index > 0) bottomBar.addView(View(context).apply { setBackgroundColor(ink) }, LinearLayout.LayoutParams(kit.line, -1))
            bottomBar.addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(kit.icon(icon), LinearLayout.LayoutParams(dp(16), dp(16)))
                addView(kit.text(context.getString(label), MonoKit.SMALL_SP).apply { maxLines = 1 },
                    LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(3) })
                kit.pressable(this, framed = false)
                setOnClickListener { run() }
            }, LinearLayout.LayoutParams(0, -1, 1f))
        }
        arrange()

        styleInput()
        input.setOnFocusChangeListener { _, focused ->
            showResults(focused)
            if (focused) input.post { RetuiThemeBridge.sendToKeyboard(context, input, source.directory(), "launcher") }
        }
    }


    /** The screen sits in a bezel: a heavy ink frame, a gap, then a fine dim one, holding a column. */
    private fun bezel() = FrameLayout(context).apply {
        kit.frame(this)
        setPadding(dp(3) + kit.line, dp(3) + kit.line, dp(3) + kit.line, dp(3) + kit.line)
        addView(FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(paper); setStroke(maxOf(1, dp(1)), kit.dim) }
            setPadding(dp(8), dp(6), dp(8), dp(8))
            addView(LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }, LayoutParams(-1, -1))
        }, LayoutParams(-1, -1))
    }
    private fun columnOf(bezel: FrameLayout) = (bezel.getChildAt(0) as FrameLayout).getChildAt(0) as LinearLayout

    override fun onAttachedToWindow() { super.onAttachedToWindow(); DualScreen.listen(onScreensChanged) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { arrange() }
    }

    /**
     * Lays the home out for one screen or two. Split, the pages (with the status strip and soft keys) take one
     * screen and the command line with its answers and suggestions the other; with `duo left`/`duo right` the
     * whole phone layout moves to that screen. The swap key flips either.
     */
    private fun arrange() {
        val layout = DualScreen.layout(this)
        val key = listOf(layout, width, paddingLeft, paddingRight)
        if (key == arrangedAs) return
        arrangedAs = key
        layoutNow = layout
        val pieces = listOf(statusStrip, statusRule, panelsFrame, panelArea, answers, dots, commandLine, bottomBar)
        pieces.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        listOf(mainBezel, pagesBezel, inputBezel).forEach { columnOf(it).removeAllViews(); (it.parent as? ViewGroup)?.removeView(it) }
        val margin = dp(6)
        // Pane edges in this view's content area, which starts after the system bar padding.
        fun pane(left: Int, right: Int, bezel: FrameLayout) {
            val from = left - paddingLeft + margin
            val to = right - paddingLeft - margin
            addView(bezel, 0, LayoutParams(maxOf(0, to - from), -1).apply { setMargins(from, margin, 0, margin) })
        }
        fun phoneColumn(column: LinearLayout) {
            column.addView(statusStrip, LinearLayout.LayoutParams(-1, dp(28)))
            column.addView(statusRule, LinearLayout.LayoutParams(-1, kit.line))
            panelsFrame.addView(panelArea, LayoutParams(-1, -1))
            panelsFrame.addView(answers, LayoutParams(-1, -1))
            column.addView(panelsFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
            column.addView(dots, LinearLayout.LayoutParams(-1, dp(18)).apply { topMargin = dp(4) })
            column.addView(commandLine, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            column.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        }
        when (layout) {
            DualScreen.Layout.Phone -> {
                swapKey.visibility = GONE
                phoneColumn(columnOf(mainBezel))
                addView(mainBezel, 0, LayoutParams(-1, -1).apply { setMargins(margin, margin, margin, margin) })
            }
            is DualScreen.Layout.OneSide -> {
                swapKey.visibility = VISIBLE
                swapKey.text = if (layout.side == DualScreen.LEFT) ">>" else "<<"
                phoneColumn(columnOf(mainBezel))
                if (layout.side == DualScreen.LEFT) pane(0, layout.split.start, mainBezel) else pane(layout.split.end, width, mainBezel)
            }
            is DualScreen.Layout.Both -> {
                swapKey.visibility = VISIBLE
                val inputLeft = layout.inputSide == DualScreen.LEFT
                swapKey.text = if (inputLeft) ">>" else "<<"
                val pages = columnOf(pagesBezel)
                pages.addView(statusStrip, LinearLayout.LayoutParams(-1, dp(28)))
                pages.addView(statusRule, LinearLayout.LayoutParams(-1, kit.line))
                panelsFrame.addView(panelArea, LayoutParams(-1, -1))
                pages.addView(panelsFrame, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
                pages.addView(dots, LinearLayout.LayoutParams(-1, dp(18)).apply { topMargin = dp(4) })
                pages.addView(bottomBar, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
                val inputs = columnOf(inputBezel)
                inputs.addView(answers, LinearLayout.LayoutParams(-1, 0, 1f))
                inputs.addView(commandLine, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
                if (inputLeft) { pane(0, layout.split.start, inputBezel); pane(layout.split.end, width, pagesBezel) }
                else { pane(0, layout.split.start, pagesBezel); pane(layout.split.end, width, inputBezel) }
            }
        }
    }

    private fun refreshStatus() {
        statusTime.text = java.text.SimpleDateFormat(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm",
            java.util.Locale.US).format(java.util.Date())
        statusBattery.level = runCatching {
            val status = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
            if (level < 0 || scale <= 0) -1 else level * 100 / scale
        }.getOrDefault(-1)
        // The bars are dim with no network.
        val online = runCatching {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).activeNetwork != null
        }.getOrDefault(false)
        statusSignal.colors = android.content.res.ColorStateList.valueOf(if (online) ink else kit.dim)
        statusSignal.invalidate()
    }

    /** The battery as an outlined cell filled to the level, with a nub. */
    private inner class BatteryGlyph : View(context) {
        var level = -1
            set(value) { field = value; invalidate() }
        private val paint = android.graphics.Paint()

        override fun onDraw(canvas: android.graphics.Canvas) {
            val l = kit.line.toFloat()
            val nub = l
            val w = width - nub
            paint.color = ink
            canvas.drawRect(0f, 0f, w, height.toFloat(), paint)
            canvas.drawRect(w, height * 0.3f, width.toFloat(), height * 0.7f, paint)
            paint.color = paper
            canvas.drawRect(l, l, w - l, height - l, paint)
            paint.color = ink
            val fill = if (level < 0) 0f else (w - l * 4) * level / 100f
            canvas.drawRect(l * 2, l * 2, l * 2 + fill, height - l * 2, paint)
        }
    }

    /** Builds the panels from the layout settings and starts them; the home is being shown. */
    fun start() {
        if (started) { refresh(); return }
        started = true
        pages = MonoLayout.parse(LauncherSettings.get(Behavior.mono_layout))
        val paged = LauncherSettings.get(Behavior.mono_layout_style)?.trim()?.lowercase(java.util.Locale.ROOT) != "scroll"
        var tile = 0
        /** Puts [items] of [page] on [grid], [rowOffset] rows down. */
        fun fill(grid: GridBox, page: Int, items: List<MonoLayout.Item>, rowOffset: Int) {
            MonoLayout.place(items).forEachIndexed { index, spot ->
                val item = spot.item
                val panel = if (item.kind == MonoLayout.Kind.TERM)
                    MonoTermPanel(kit, panelHost, terms, item, tile++, termEvents).also { termSlots[it] = page to index }
                else MonoPanel.create(item.kind, kit, panelHost)
                panels += panel
                grid.put(HoldFrame(panel.view) { hold(page, index, panel) }, spot.column, rowOffset + spot.row, item.columns, item.rows)
            }
        }
        /** The add slot goes in the first 2×2 gap beside the panels, or else below them. */
        fun slot(grid: GridBox, page: Int, items: List<MonoLayout.Item>, rowOffset: Int, below: Boolean) {
            val height = MonoLayout.height(items)
            val gap = MonoLayout.place(items + MonoLayout.Item(MonoLayout.Kind.QUICK)).last()
            if (gap.row + 2 <= height) grid.put(addSlot(page, gap = true), gap.column, rowOffset + gap.row, 2, 2)
            else if (below) grid.putSlot(addSlot(page))
        }
        if (paged) {
            val pagerView = Pager()
            pages.forEachIndexed { page, items ->
                val grid = GridBox(fill = true)
                fill(grid, page, items, 0)
                grid.rows = MonoLayout.height(items)
                slot(grid, page, items, 0, grid.rows < MonoLayout.PAGE_ROWS)
                pagerView.addView(grid)
            }
            pager = pagerView
            panelArea.addView(pagerView, LayoutParams(-1, -1))
            pagerView.go(panelState["page"]?.toIntOrNull() ?: 0, animate = false)
        } else {
            // One grid, each page's panels below the last page's.
            val all = GridBox(fill = false)
            pages.forEachIndexed { page, items ->
                fill(all, page, items, all.rows)
                if (page == pages.lastIndex) slot(all, page, items, all.rows, true)
                all.rows += MonoLayout.height(items)
            }
            panelArea.addView(ScrollView(context).apply {
                isVerticalScrollBarEnabled = false
                addView(all)
                addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ -> if (all.viewport != b - t) { all.viewport = b - t; all.requestLayout() } }
            }, LayoutParams(-1, -1))
        }
        showDots()
        if (termSlots.isNotEmpty()) terms.start()
        refresh()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Releases every panel; the home is no longer shown. They are built again from the settings next time. */
    fun stop() {
        if (!started) return
        lifted?.let { termEvents.drop(it) }
        started = false
        handler.removeCallbacks(tick)
        panels.forEach { it.release() }
        panels.clear()
        termSlots.clear()
        terms.stop()
        panelArea.removeAllViews()
        pager = null
        chooser.visibility = GONE
    }

    /** Re-reads what the panels show. */
    fun refresh() {
        panels.forEach { it.refresh() }
    }

    private fun save(next: List<List<MonoLayout.Item>>, rebuild: Boolean = true) {
        pages = next
        LauncherSettings.set(context, Behavior.mono_layout, MonoLayout.write(next))
        if (rebuild) { stop(); start() }
    }

    private val termEvents = object : MonoTermPanel.Events {
        override fun lift(panel: MonoTermPanel) {
            if (lifted === panel) return
            lifted?.let { drop(it) }
            val view = panel.view
            val from = view.parent as? ViewGroup ?: return
            if (input.hasFocus()) closeInput()
            from.removeView(view)
            liftSpot.addView(view, LayoutParams(-1, -1))
            lifted = panel
            liftedFrom = from
            panel.lifted(true)
            bindKeys()
            liftLayer.visibility = VISIBLE
        }

        override fun drop(panel: MonoTermPanel) {
            if (lifted !== panel) return
            panel.unfocus()
            panel.lifted(false)
            liftSpot.removeView(panel.view)
            liftedFrom?.addView(panel.view, LayoutParams(-1, -1))
            lifted = null
            liftedFrom = null
            liftLayer.visibility = GONE
        }

        override fun window(panel: MonoTermPanel, window: String) {
            val (page, slot) = termSlots[panel] ?: return
            val item = pages.getOrNull(page)?.getOrNull(slot) ?: return
            save(MonoLayout.replace(pages, page, slot, MonoLayout.Item(item.kind, item.columns, item.rows, window)), rebuild = false)
        }
    }

    private fun bindKeys() {
        ohi.andre.consolelauncher.managers.termux.TermuxSpecialKeys.bind(keyBand, fnKeys, terms.modifiers, ink,
            { fnKeys = it; bindKeys() }, { }, { lifted?.key(it) })
    }

    /** One square per page under the panels, the current one filled; a tap goes to that page. */
    private fun showDots() {
        dots.removeAllViews()
        val pagerView = pager
        dots.visibility = if (pagerView == null) GONE else VISIBLE
        if (pagerView == null) return
        val size = dp(9)
        pages.indices.forEach { page ->
            dots.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setStroke(kit.line, if (page == pagerView.page) ink else kit.dim)
                    setColor(if (page == pagerView.page) ink else paper)
                }
                setOnClickListener { pagerView.go(page) }
                setOnLongClickListener { dots.performLongClick() }
            }, LinearLayout.LayoutParams(size, size).apply { setMargins(dp(5), 0, dp(5), 0) })
        }
    }

    /** The dashed slot at the end of a page that adds a panel to it. */
    private fun addSlot(page: Int, gap: Boolean = false): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        tag = 0
        background = GradientDrawable().apply {
            setColor(paper)
            setStroke(kit.line, kit.dim, dp(6).toFloat(), dp(4).toFloat())
        }
        addView(kit.icon(MonoKit.Icons.PLUS, android.content.res.ColorStateList.valueOf(kit.dim)), LinearLayout.LayoutParams(dp(14), dp(14)))
        addView(kit.text(context.getString(R.string.mono_add_panel), color = kit.dim), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        setOnClickListener { chooseNew(page, gap) }
    }

    /**
     * Panels on the four-column grid, each at its spot, with an optional add slot below them. A page holds
     * [MonoLayout.PAGE_ROWS] grid rows: filling, they share the height given (more rows squeeze to fit) and the add
     * slot takes what is left; scrolling, a grid row is as tall as on a page of [viewport].
     */
    private inner class GridBox(private val fill: Boolean) : android.view.ViewGroup(context) {
        var viewport = 0
        /** Grid rows the panels take. */
        var rows = 0
        private var unit = 0f
        private var cell = 0f
        private val gap get() = dp(8)

        fun put(view: View, column: Int, row: Int, columns: Int, rows: Int) {
            view.tag = intArrayOf(column, row, columns, rows)
            addView(view)
        }

        /** The add slot, under the panels. */
        fun putSlot(view: View) {
            view.tag = null
            addView(view)
        }

        private fun slotTop() = (rows * unit).roundToInt()

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = if (fill) MeasureSpec.getSize(heightMeasureSpec) else viewport.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels / 2
            unit = (height + gap).toFloat() / (if (fill) maxOf(MonoLayout.PAGE_ROWS, rows) else MonoLayout.PAGE_ROWS)
            cell = (width + gap).toFloat() / MonoLayout.COLUMNS
            var bottom = if (rows > 0) slotTop() - gap else 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val spot = child.tag as? IntArray
                if (spot != null) {
                    child.measure(exactly(cell * spot[2] - gap), exactly(unit * spot[3] - gap))
                } else {
                    val slot = if (fill) maxOf(dp(44), height - slotTop()) else maxOf(dp(44), (unit - gap).roundToInt())
                    child.measure(exactly(width.toFloat()), exactly(slot.toFloat()))
                    bottom = slotTop() + slot
                }
            }
            setMeasuredDimension(width, if (fill) height else bottom)
        }

        private fun exactly(size: Float) = MeasureSpec.makeMeasureSpec(size.roundToInt().coerceAtLeast(0), MeasureSpec.EXACTLY)

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val spot = child.tag as? IntArray
                val left = if (spot != null) (spot[0] * cell).roundToInt() else 0
                val top = if (spot != null) (spot[1] * unit).roundToInt() else slotTop()
                child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            }
        }
    }

    /**
     * The pages side by side, one screen wide each. A sideways drag moves between them, unless it starts at the
     * screen's edges, which belong to the edge sidebar; up-and-down drags stay with the panels.
     */
    private inner class Pager : android.view.ViewGroup(context) {
        var page = 0
            private set
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private val fling = android.view.ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4
        private val edge = dp(24)
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var dragging = false
        private var ignore = false
        private var velocity: android.view.VelocityTracker? = null
        private var animator: android.animation.ValueAnimator? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = MeasureSpec.getSize(widthMeasureSpec)
            val h = MeasureSpec.getSize(heightMeasureSpec)
            for (i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
            setMeasuredDimension(w, h)
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val w = r - l
            for (i in 0 until childCount) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t)
            if (animator == null && !dragging) scrollTo(page * w, 0)
        }

        private fun sideways(event: android.view.MotionEvent): Boolean {
            val dx = kotlin.math.abs(event.x - downX)
            return dx > slop && dx > kotlin.math.abs(event.y - downY) * 1.5f
        }

        override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y; lastX = event.x
                    dragging = false
                    ignore = childCount < 2 || event.x < edge || event.x > width - edge
                    velocity?.clear()
                    track(event)
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    track(event)
                    if (!ignore && !dragging && sideways(event)) startDrag(event)
                }
            }
            return dragging
        }

        private fun startDrag(event: android.view.MotionEvent) {
            dragging = true
            lastX = event.x
            animator?.cancel()
            parent?.requestDisallowInterceptTouchEvent(true)
        }

        private fun track(event: android.view.MotionEvent) {
            val tracker = velocity ?: android.view.VelocityTracker.obtain().also { velocity = it }
            tracker.addMovement(event)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (ignore) return false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> track(event)
                android.view.MotionEvent.ACTION_MOVE -> {
                    track(event)
                    if (!dragging && sideways(event)) startDrag(event)
                    if (dragging) {
                        val max = (childCount - 1) * width
                        var next = scrollX + (lastX - event.x)
                        // Past the first or last page it gives a little, then stops.
                        if (next < 0 || next > max) next = scrollX + (lastX - event.x) / 3f
                        lastX = event.x
                        scrollTo(next.roundToInt().coerceIn(-width / 4, max + width / 4), 0)
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        track(event)
                        val tracker = velocity
                        tracker?.computeCurrentVelocity(1000)
                        val vx = tracker?.xVelocity ?: 0f
                        go(when {
                            vx < -fling -> page + 1
                            vx > fling -> page - 1
                            else -> ((scrollX.toFloat() / width.coerceAtLeast(1)) + 0.5f).toInt()
                        })
                    }
                    dragging = false
                    velocity?.recycle(); velocity = null
                }
            }
            return true
        }

        fun go(target: Int, animate: Boolean = true) {
            page = target.coerceIn(0, (childCount - 1).coerceAtLeast(0))
            panelState["page"] = page.toString()
            showDots()
            val to = page * width
            animator?.cancel()
            if (!animate || width == 0 || kit.reducedMotion()) { scrollTo(to, 0); return }
            animator = android.animation.ValueAnimator.ofInt(scrollX, to).setDuration(220).apply {
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { if (animator === animation) animator = null }
                })
                start()
            }
        }
    }

    /**
     * Holds a panel and offers its menu on a long press anywhere on it. It watches the touch before the panel does,
     * so scrolling lists and buttons inside cannot swallow the press; once it fires, the panel's own touch is
     * cancelled.
     */
    private inner class HoldFrame(panel: View, private val onHold: () -> Unit) : FrameLayout(context) {
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var held = false
        private val fire = Runnable {
            held = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onHold()
        }

        init { addView(panel, LayoutParams(-1, -1)) }

        /** Starts the hold on a press and calls it off once the finger moves, lifts or a scroll takes the touch. */
        private fun track(event: android.view.MotionEvent) {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    held = false
                    downX = event.x
                    downY = event.y
                    removeCallbacks(fire)
                    postDelayed(fire, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                android.view.MotionEvent.ACTION_MOVE ->
                    if (kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop) removeCallbacks(fire)
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> removeCallbacks(fire)
            }
        }

        override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
            track(event)
            return held
        }

        // A press on a part of the panel nothing else takes (its header, empty space) lands here, and then so does
        // the rest of the gesture: without taking it, a swipe that started there would never call the hold off.
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            if (!held) track(event)
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) held = false
            return true
        }

        override fun onDetachedFromWindow() { removeCallbacks(fire); super.onDetachedFromWindow() }
    }

    /** One line of a menu: a choice, or with no [run] a section heading. A [current] choice is marked and inert. */
    private class Choice(val label: String, val note: String? = null, val current: Boolean = false, val run: (() -> Unit)? = null)

    private fun heading(label: String) = Choice(label)
    private fun choice(label: String, note: String? = null, run: () -> Unit) = Choice(label, note, run = run)

    /** A menu over the home: a title, then sections of choices under dim headings with a rule above each. */
    private fun menu(title: String, choices: List<Choice>) {
        chooser.removeAllViews()
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = kit.box()
            setPadding(dp(10), dp(8), dp(10), dp(8))
            isClickable = true
            addView(kit.text(title), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        choices.forEach { choice ->
            if (choice.run == null && !choice.current) {
                list.addView(View(context).apply { setBackgroundColor(kit.dim) }, LinearLayout.LayoutParams(-1, maxOf(1, dp(1))).apply { topMargin = dp(8) })
                // A heading with no words is just the rule, setting remove apart.
                if (choice.label.isNotEmpty()) list.addView(kit.text(choice.label, MonoKit.SMALL_SP, kit.dim).apply { setPadding(dp(6), dp(4), dp(6), dp(2)) }, LinearLayout.LayoutParams(-1, -2))
                return@forEach
            }
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(6), dp(5), dp(6), dp(5))
                addView(kit.text((if (choice.current) "> " else "  ") + choice.label, color = if (choice.current) kit.dim else ink), LinearLayout.LayoutParams(0, -2, 1f))
                choice.note?.let { addView(kit.text(it, MonoKit.SMALL_SP, kit.dim)) }
                if (!choice.current) {
                    kit.pressable(this, framed = false)
                    setOnClickListener { chooser.visibility = GONE; choice.run?.invoke() }
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        chooser.addView(ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(list) },
            LayoutParams(-1, -2, Gravity.CENTER).apply { setMargins(dp(32), dp(32), dp(32), dp(32)) })
        chooser.visibility = VISIBLE
    }

    /**
     * A held panel's menu, in sections: its sizes, moving it up or down the page, the panels to swap it for (a panel
     * shown elsewhere trades places, since only terminals repeat), and remove. A terminal offers its window in place
     * of swapping.
     */
    private fun hold(page: Int, slot: Int, panel: MonoPanel) {
        val item = pages.getOrNull(page)?.getOrNull(slot) ?: return
        val window = (panel as? MonoTermPanel)?.window
        val choices = ArrayList<Choice>()
        if (item.kind.sizes.size > 1) {
            choices += heading(context.getString(R.string.mono_menu_size))
            item.kind.sizes.forEach { (columns, rows) ->
                val current = item.columns == columns && item.rows == rows
                choices += Choice("$columns×$rows", current = current, run = {
                    save(MonoLayout.replace(pages, page, slot, MonoLayout.Item(item.kind, columns, rows, window)))
                })
            }
        }
        // Up or down only when it would land somewhere else on screen.
        val up = MonoLayout.canMove(pages, page, slot, -1)
        val down = MonoLayout.canMove(pages, page, slot, 1)
        if (up || down) {
            choices += heading(context.getString(R.string.mono_menu_move))
            if (up) choices += choice(context.getString(R.string.mono_move_up)) { save(MonoLayout.move(pages, page, slot, -1)) }
            if (down) choices += choice(context.getString(R.string.mono_move_down)) { save(MonoLayout.move(pages, page, slot, 1)) }
        }
        if (panel is MonoTermPanel) {
            choices += heading(context.getString(R.string.mono_term_title).lowercase(java.util.Locale.getDefault()))
            choices += choice(context.getString(R.string.mono_term_window)) { panel.chooseWindow() }
        } else {
            choices += heading(context.getString(R.string.mono_menu_swap))
            MonoLayout.Kind.entries.filter { it != MonoLayout.Kind.TERM && it != item.kind }.forEach { kind ->
                val shownOn = pages.indexOfFirst { items -> items.any { it.kind == kind } }
                val note = when {
                    shownOn < 0 -> null
                    pager != null -> context.getString(R.string.mono_menu_on_page, shownOn + 1)
                    else -> context.getString(R.string.mono_menu_shown)
                }
                choices += choice(kind.key, note) { save(MonoLayout.swap(pages, page, slot, kind)) }
            }
        }
        choices += heading("")
        choices += choice(context.getString(R.string.mono_remove_panel)) { save(MonoLayout.remove(pages, page, slot)) }
        val title = if (panel is MonoTermPanel) context.getString(R.string.mono_term_title).lowercase(java.util.Locale.getDefault()) + (window?.let { w -> " " + (terms.windows.firstOrNull { it.first == w }?.second ?: w) } ?: "")
            else item.kind.key
        menu(title, choices)
    }

    /** The add slot: any panel not on the home yet, or another terminal. */
    private fun chooseNew(page: Int, gap: Boolean) {
        val room = MonoLayout.PAGE_ROWS - MonoLayout.height(pages.getOrNull(page).orEmpty())
        menu(context.getString(R.string.mono_add_title), MonoLayout.missing(pages).map { kind ->
            if (kind == MonoLayout.Kind.TERM) {
                choice(context.getString(R.string.mono_add_terminal)) {
                    // A new terminal takes the room the page has left, when it can.
                    // A gap beside other panels takes a small one.
                    val size = when { gap -> 2 to 2; pager == null || room >= 4 -> 4 to 4; else -> 4 to 2 }
                    save(MonoLayout.add(pages, page, MonoLayout.Item(kind, size.first, size.second)))
                }
            } else {
                // From a gap, a panel that also comes at 2×2 takes that size so it fits there.
                val size = (2 to 2).takeIf { gap && it in kind.sizes } ?: kind.sizes[0]
                choice(kind.key, "${size.first}×${size.second}") { save(MonoLayout.add(pages, page, MonoLayout.Item(kind, size.first, size.second))) }
            }
        })
    }

    /** Held page squares: add a page after this one, or remove this one. */
    private fun choosePages() {
        val page = pager?.page ?: return
        val rows = mutableListOf(choice(context.getString(R.string.mono_page_add)) {
            panelState["page"] = (page + 1).toString()
            save(MonoLayout.addPage(pages, page))
        })
        if (pages.size > 1) rows += choice(context.getString(R.string.mono_page_remove)) {
            panelState["page"] = (page - 1).coerceAtLeast(0).toString()
            save(MonoLayout.removePage(pages, page))
        }
        menu(context.getString(R.string.mono_pages_title), rows)
    }

    /** Puts [text] on the command line with the keyboard up, ready to finish. */
    private fun prefill(text: String) {
        input.requestFocus()
        input.setText(text)
        input.setSelection(input.text?.length ?: 0)
        showKeyboard()
    }

    private fun showResults(show: Boolean) {
        resultsScroll.visibility = if (show) VISIBLE else GONE
        bottomBar.visibility = if (show) GONE else VISIBLE
        if (show) outputPanel.visibility = GONE
        if (show) lifted?.let { termEvents.drop(it) }
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
    private val quietClose = Runnable { if (outputLines.childCount == 0) closeOutput() }

    /** A command run from the command line: the output panel opens for its answer. */
    fun echo(command: String) {
        echoedAt = android.os.SystemClock.uptimeMillis()
        outputCommand.text = "$ $command"
        outputLines.removeAllViews()
        // Opens once the input has closed, which hides it.
        post { outputPanel.visibility = VISIBLE }
        handler.removeCallbacks(quietClose)
        handler.postDelayed(quietClose, QUIET_CLOSE_MS)
        // Commands like notes -add print nothing but change what the panels show; look again once they ran.
        handler.postDelayed({ if (isAttachedToWindow) refresh() }, 700L)
    }

    /** Output the launcher printed for a command run here; background messages (weather updates) stay out. */
    fun output(text: CharSequence?) {
        val value = text?.toString()?.trim().orEmpty()
        if (value.isEmpty() || android.os.SystemClock.uptimeMillis() - echoedAt > ANSWER_WINDOW_MS) return
        value.lines().forEach { lineText ->
            outputLines.addView(kit.text(lineText, color = if (outputLines.childCount == 0) kit.dim else ink))
        }
        while (outputLines.childCount > OUTPUT_LINES) outputLines.removeViewAt(0)
        outputPanel.visibility = VISIBLE
    }

    private fun closeOutput() {
        handler.removeCallbacks(quietClose)
        outputPanel.visibility = GONE
    }

    /** Back closes the chooser, the input, then the answers; true when it did. */
    fun back(): Boolean {
        if (chooser.visibility == VISIBLE) { chooser.visibility = GONE; return true }
        lifted?.let { termEvents.drop(it); return true }
        if (input.hasFocus() || resultsScroll.visibility == VISIBLE) { closeInput(); return true }
        if (outputPanel.visibility == VISIBLE) { closeOutput(); return true }
        return false
    }

    private fun dp(value: Int) = (value * kit.density).roundToInt()

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        // Coming back to Home starts with the input closed and the keyboard away.
        if (hasWindowFocus && input.text.isNullOrEmpty()) { closeInput(); requestFocus() }
    }

    override fun onDetachedFromWindow() {
        DualScreen.unlisten(onScreensChanged)
        stop()
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    /** Suggestions take what they need, up to about half the screen and never more than the panels' room. */
    private inner class CappedScroll : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val room = MeasureSpec.getSize(heightMeasureSpec)
            val cap = minOf((resources.displayMetrics.heightPixels * 0.45f).toInt(), room).coerceAtLeast(0)
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
        }
    }
}
