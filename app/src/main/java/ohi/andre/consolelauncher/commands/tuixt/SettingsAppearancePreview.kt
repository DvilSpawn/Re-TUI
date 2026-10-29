package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorder
import ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LauncherFontScale

/** Passive launcher layouts with live data and private appearance values. ANSI remains a labeled specimen. */
internal class SettingsAppearancePreview(
    context: Context,
    private val draft: (XMLPrefsSave) -> String?,
    private val hasDraft: (XMLPrefsSave) -> Boolean
) : LinearLayout(context) {
    private val values = ohi.andre.consolelauncher.managers.settings.AppearanceValues { raw(it) }
    private var live = ohi.andre.consolelauncher.managers.settings.LauncherAppearanceState()
    private val liveTicker = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || windowVisibility != View.VISIBLE) return
            refresh()
            postDelayed(this, 2000)
        }
    }
    private val caption = TextView(context)
    private val pageScrolls = IntArray(4)
    private val pageIndicator = TextView(context)
    private val pager = RecyclerView(context)
    private val manager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val snap = PagerSnapHelper()
    private var fontKey: String? = null
    private var previewFont: Typeface? = null
    private val refreshTask = Runnable {
        live = ohi.andre.consolelauncher.LauncherActivity.instance?.uiManager?.snapshotAppearanceState()
            ?: ohi.andre.consolelauncher.managers.settings.LauncherAppearanceState()
        pager.adapter?.notifyDataSetChanged()
    }
    private val titles = listOf(R.string.settings_preview_status, R.string.settings_preview_terminal, R.string.settings_preview_modules, R.string.settings_preview_ansi)

    init {
        orientation = VERTICAL
        addView(caption.apply {
            text = context.getString(R.string.settings_preview_draft)
            setTextColor(TuixtTheme.textColor())
            typeface = Tuils.getTypeface(context)
            textSize = 11f
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        pageIndicator.apply {
            gravity = Gravity.CENTER
            typeface = Tuils.getTypeface(context)
            textSize = 12f
            setTextColor(TuixtTheme.textColor())
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        addView(pageIndicator, LayoutParams(-1, dp(30)))
        pager.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        pager.isFocusable = false
        pager.itemAnimator = null
        pager.layoutManager = manager
        pager.adapter = object : RecyclerView.Adapter<Page>() {
            override fun getItemCount() = titles.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page = Page(ScrollView(context).apply {
                layoutParams = RecyclerView.LayoutParams(pager.width.coerceAtLeast(1), -1)
                isFillViewport = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            })
            override fun onBindViewHolder(holder: Page, position: Int) {
                val scrollY = pageScrolls[position]
                holder.rebinding = true
                holder.page = position
                holder.scroll.setOnScrollChangeListener { _, _, y, _, _ ->
                    if (!holder.rebinding) pageScrolls[holder.page] = y
                }
                holder.scroll.layoutParams.width = pager.width.coerceAtLeast(1)
                holder.scroll.removeAllViews()
                val content = LinearLayout(context).apply {
                    orientation = VERTICAL
                    setPadding(dp(6), dp(8), dp(6), dp(8))
                    setBackgroundColor(color(Theme.background_color))
                }
                holder.scroll.addView(content)
                when (position) {
                    0 -> status(content)
                    1 -> terminal(content)
                    2 -> modules(content)
                    else -> ansi(content)
                }
                holder.scroll.post { holder.scroll.scrollTo(0, scrollY); holder.rebinding = false }
            }
        }
        snap.attachToRecyclerView(pager)
        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                if (view.scrollState == RecyclerView.SCROLL_STATE_IDLE) snap.findSnapView(manager)?.let { updateIndicator(manager.getPosition(it)) }
            }
            override fun onScrollStateChanged(view: RecyclerView, state: Int) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    snap.findSnapView(manager)?.let { updateIndicator(manager.getPosition(it)) }
                }
            }
        })
        pager.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) refresh()
        }
        addView(pager, LayoutParams(-1, dp(190)))
        updateIndicator(0)
    }

    private class Page(val scroll: ScrollView) : RecyclerView.ViewHolder(scroll) { var page = 0; var rebinding = false }
    fun setAvailableHeight(height: Int, keyboardVisible: Boolean = false) {
        val desired = dp(if (keyboardVisible || height < dp(500)) 100 else 190)
        if (pager.layoutParams.height != desired) {
            pager.layoutParams = pager.layoutParams.apply { this.height = desired }
        }
    }
    fun selectedPage(): Int = snap.findSnapView(manager)?.let { manager.getPosition(it) } ?: 0
    fun restorePage(page: Int) {
        val position = page.coerceIn(0, titles.lastIndex)
        pager.scrollToPosition(position)
        updateIndicator(position)
    }
    fun refresh() { removeCallbacks(refreshTask); postDelayed(refreshTask, 60) }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(liveTicker); post(liveTicker) }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // View's constructor can deliver this before the ticker is initialized.
        if (isAttachedToWindow) { removeCallbacks(liveTicker); if (visibility == View.VISIBLE) post(liveTicker) }
    }
    override fun onDetachedFromWindow() { removeCallbacks(refreshTask); removeCallbacks(liveTicker); super.onDetachedFromWindow() }
    private fun dp(value: Int) = TuixtTheme.dp(context, value.toFloat())
    private fun raw(item: XMLPrefsSave) = draft(item) ?: LauncherSettings.get(item) ?: item.defaultValue()
    private fun bool(item: XMLPrefsSave) = raw(item).toBoolean()
    private fun number(item: XMLPrefsSave, low: Float, high: Float): Float =
        (raw(item)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: item.defaultValue()?.toFloatOrNull() ?: low).coerceIn(low, high)
    private fun color(role: Theme) = ThemeColorResolver.resolve(role, { raw(it) }, emptyMap())
    private fun radius(item: Ui): Int = if (raw(item) != item.defaultValue())
        number(item, 0f, 48f).toInt() else number(Ui.dashed_border_corner_radius, 0f, 48f).toInt()

    private fun updateIndicator(selected: Int) {
        if (selected !in titles.indices) return
        caption.setText(if (selected == 3) R.string.settings_preview_ansi_caption else R.string.settings_preview_draft)
        pageIndicator.text = context.getString(R.string.settings_preview_page,
            context.getString(titles[selected]), selected + 1, titles.size)
    }

    private fun panel(fill: Int, border: Int, radius: Int, surface: SurfaceBorder? = null,
                      target: FrameTarget = FrameTarget.MODULES, allowBorder: Boolean = true, frameEnabled: Boolean = true): android.graphics.drawable.Drawable {
        return TerminalBorderRuntime.panelDrawable(context, fill,
            surface?.let { SurfaceBorder.resolveColor(raw(it.color), color(Theme.terminal_border_color)) } ?: border,
            1.5f, radius, bool(Ui.enable_dashed_border), surface == null,
            allowBorder && (surface == null || (bool(Ui.enable_dashed_border) && bool(surface.enabled))),
            frameEnabled, target, values)
    }

    private fun font(): Typeface? {
        val key = "${bool(Ui.system_font)}:${raw(Ui.font_file)}"
        if (key != fontKey) {
            fontKey = key
            previewFont = if (bool(Ui.system_font)) Typeface.DEFAULT else if (!hasDraft(Ui.system_font) && !hasDraft(Ui.font_file)) {
                Tuils.getTypeface(context)
            } else {
                val file = Tuils.resolveConfiguredFontFile(Tuils.getFolder(), raw(Ui.font_file))
                runCatching { if (file == null) Typeface.createFromAsset(context.assets, Tuils.BUNDLED_FONT_ASSET) else Typeface.createFromFile(file) }
                    .getOrElse { Typeface.MONOSPACE }
            }
        }
        return previewFont
    }

    private fun sample(text: String, textRole: Theme, size: Ui? = null) = TextView(context).apply {
        this.text = text
        typeface = font()
        textSize = LauncherFontScale.scaledSp(
            size?.let { raw(it)?.toFloatOrNull()?.takeIf(Float::isFinite) } ?: 13f,
            number(Ui.font_size_offset, LauncherFontScale.MIN_OFFSET.toFloat(), LauncherFontScale.MAX_OFFSET.toFloat()).toInt()
        )
        setTextColor(color(textRole))
        setPadding(dp(10), dp(7), dp(10), dp(7))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    private fun addPanel(parent: LinearLayout, view: View, fill: Theme, border: Theme, radius: Int,
                         surface: SurfaceBorder? = null, target: FrameTarget = FrameTarget.MODULES, allowBorder: Boolean = true) {
        view.background = panel(color(fill), color(border), radius, surface, target, allowBorder)
        parent.addView(view, LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
    }

    private fun status(parent: LinearLayout) {
        val data = ohi.andre.consolelauncher.managers.status.LiveStatusText(context, values)
        data.refresh(live)
        val renderer = ohi.andre.consolelauncher.managers.status.StatusSurfaceRenderer
        val order = StatusRowResolver.reorder(StatusRowResolver.settings.associateWith { LauncherSettings.get(it) },
            StatusRowResolver.settings.filter { hasDraft(it) }.associateWith { raw(it) }).values
        val rows = renderer.specs.filter { bool(it.show) }.sortedBy { order.getValue(it.index).toBigDecimal() }
        val unified = bool(SurfaceBorderOption.unified_status_border)
        val actual = LayoutInflater.from(context).inflate(R.layout.about_device_view, parent, false) as LinearLayout
        passive(actual)
        parent.addView(actual)
        val unifiedView = actual.findViewById<LinearLayout>(R.id.unified_status_container)
        val labelIds = listOf(R.id.tv0, R.id.tv1, R.id.tv2, R.id.tv3, R.id.tv4, R.id.tv5, R.id.tv6, R.id.tv7, R.id.tv8, R.id.tv9)
        val groups = rows.groupBy { order.getValue(it.index).toBigDecimal().toInt() }.values.toList()
        labelIds.forEachIndexed { index, id ->
            var view = actual.findViewById<TextView>(id)
            val group = groups.getOrNull(index)
            if (group == null || unified) { view.visibility = View.GONE; return@forEachIndexed }
            val text = android.text.SpannableStringBuilder()
            group.forEach { spec -> data.text[spec.label]?.let { text.append(it) } }
            if (group.size == 1 && group.first().label == ohi.andre.consolelauncher.UIManager.Label.ascii) {
                val original = view
                val ascii = ohi.andre.consolelauncher.tuils.AsciiArtTextView(context).apply { this.id = id; layoutParams = original.layoutParams }
                val position = actual.indexOfChild(original)
                actual.removeView(original); actual.addView(ascii, position)
                view = ascii
                renderer.style(view, group.first().label, values, font = Typeface.MONOSPACE)
                val frame = data.text[group.first().label]
                ascii.setAsciiFrame(frame?.toString(), color(Theme.ascii_text_color), values.int(Ui.ascii_max_lines),
                    values.int(Ui.ascii_pane_height_rows), (frame as? ohi.andre.consolelauncher.managers.status.AsciiAnimationManager.AsciiFrameText)?.colors())
            } else {
                view.text = text
                renderer.style(view, group.first().label, values, font = font())
            }
            if (group.any { it.label == ohi.andre.consolelauncher.UIManager.Label.notes }) {
                val limit = values.int(Ui.notes_max_lines)
                if (limit > 0) view.maxLines = limit
            }
            passive(view)
            view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        if (unified) {
            unifiedView.visibility = View.VISIBLE
            unifiedView.background = panel(color(Theme.unified_status_background_color), color(Theme.terminal_border_color), radius(Ui.module_corner_radius), target = FrameTarget.STATUS_GROUP)
            val show = BooleanArray(ohi.andre.consolelauncher.UIManager.Label.entries.size)
            rows.forEach { show[it.label.ordinal] = true }
            val battery = data.text[ohi.andre.consolelauncher.UIManager.Label.battery] ?: ""
            val weather = data.text[ohi.andre.consolelauncher.UIManager.Label.weather]
            ohi.andre.consolelauncher.managers.status.UnifiedStatusRenderer.bind(actual, values, show,
                ohi.andre.consolelauncher.managers.status.UnifiedStatusRenderer.State(
                    battery = if (bool(Behavior.battery_progress_bar)) battery else ohi.andre.consolelauncher.UIManager.compactUnifiedBattery(battery),
                    weather = ohi.andre.consolelauncher.UIManager.compactUnifiedWeatherCondition(weather),
                    location = values.get(Behavior.weather_location).takeUnless { it == "null" }.orEmpty(),
                    details = ohi.andre.consolelauncher.UIManager.compactUnifiedWeatherDetails(weather),
                    internalStorage = data.storage.compactInternal(), externalStorage = data.storage.compactExternal(),
                    connection = data.network.compactConnection(), wifi = data.network.compactConnectionUsesWifi(),
                    ram = data.ram.compactValue(), unlocks = java.text.NumberFormat.getIntegerInstance().format(data.unlockCount)
                ), font())
            actual.findViewById<View>(R.id.unified_modules_toggle).visibility = View.GONE
        }
        if (rows.isEmpty()) parent.addView(sample(context.getString(R.string.settings_preview_status_hidden), Theme.output_text_color))
    }

    private fun passive(view: View) {
        if (view is TextView) {
            view.movementMethod = null
            view.setTextIsSelectable(false)
            if (view.text is android.text.Spanned) view.text = android.text.SpannableString(view.text).apply {
                getSpans(0, length, android.text.style.ClickableSpan::class.java).forEach { removeSpan(it) }
            }
        }
        view.isClickable = false
        view.isLongClickable = false
        view.isFocusable = false
        if (view is ViewGroup) for (index in 0 until view.childCount) passive(view.getChildAt(index))
    }

    private fun titledPanel(parent: LinearLayout, title: String?, fill: Theme, border: Theme,
                           radius: Int, surface: SurfaceBorder? = null, target: FrameTarget = FrameTarget.MODULES): LinearLayout {
        val host = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(6), dp(if (title == null) 4 else 16), dp(6), dp(5))
            background = panel(color(fill), color(border), radius, surface, target)
        }
        host.addView(body, FrameLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (title == null) 0 else 10) })
        if (title != null) {
            val label = sample(title, if (target == FrameTarget.OUTPUT) Theme.output_text_color else Theme.module_panel_text_color,
                if (target == FrameTarget.OUTPUT) Ui.output_header_text_size else Ui.module_header_text_size).apply {
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = panel(color(Theme.terminal_header_background_color), color(Theme.terminal_header_border_color),
                    radius(Ui.header_corner_radius), target = FrameTarget.HEADER)
            }
            host.addView(label, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            TerminalBorderRuntime.bind(body, label)
        }
        parent.addView(host, LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return body
    }

    private fun richSample(vararg parts: Pair<String, Theme>): TextView {
        val output = android.text.SpannableStringBuilder()
        parts.forEach { (text, role) ->
            val start = output.length
            output.append(text)
            output.setSpan(android.text.style.ForegroundColorSpan(color(role)), start, output.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sample("", Theme.output_text_color, Ui.input_output_size).apply { text = output }
    }

    private fun terminal(parent: LinearLayout) {
        val output = LayoutInflater.from(context).inflate(R.layout.terminal_page, parent, false)
        output.layoutParams = LayoutParams(-1, dp(150))
        val border = output.findViewById<View>(R.id.terminal_output_border)
        val textView = output.findViewById<TextView>(R.id.terminal_view)
        val toggle = output.findViewById<TextView>(R.id.terminal_tray_toggle)
        ohi.andre.consolelauncher.tuils.TerminalOutputRenderer.style(border, textView, values)
        textView.typeface = font(); textView.textSize = values.size(Ui.input_output_size)
        ohi.andre.consolelauncher.tuils.TerminalTrayToggleView.style(context, toggle, border,
            values.get(Behavior.output_header_mode) == "none", values.get(Behavior.output_header_mode) == "arrows",
            color(Theme.tab_text_color), values.int(Ui.output_header_text_size), color(Theme.terminal_header_background_color), values, font()) {}
        ohi.andre.consolelauncher.tuils.TerminalTrayToggleView.updateText(context, toggle,
            values.get(Behavior.output_header_mode) == "none", false, false, values.get(Behavior.output_header_mode) == "arrows",
            true, false, false, color(Theme.tab_text_color))
        val history = android.text.SpannableString(live.terminal?.output ?: context.getString(R.string.settings_preview_no_output))
        history.getSpans(0, history.length, ohi.andre.consolelauncher.tuils.ThemeColorSpan::class.java).forEach { span ->
            val start = history.getSpanStart(span); val end = history.getSpanEnd(span)
            history.removeSpan(span)
            history.setSpan(android.text.style.ForegroundColorSpan(values.color(span.role)), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        textView.text = history
        passive(output)
        parent.addView(output)
        val controls = LinearLayout(context).apply { orientation = VERTICAL }
        LayoutInflater.from(context).inflate(R.layout.launcher_input_controls, controls, true)
        controls.findViewById<TextView>(R.id.prefix_view).text = values.get(if (live.terminal?.root == true) Ui.input_root_prefix else Ui.input_prefix).let { if (it.endsWith(" ")) it else "$it " }
        controls.findViewById<TextView>(R.id.input_view).text = live.terminal?.input.orEmpty()
        controls.findViewById<View>(R.id.submit_tv).visibility = if (bool(Ui.show_enter_button)) View.VISIBLE else View.GONE
        controls.findViewById<View>(R.id.tools_view).visibility = if (values.bool(ohi.andre.consolelauncher.managers.xml.options.Toolbar.show_toolbar)) View.VISIBLE else View.GONE
        listOf(R.id.app_drawer_view to Behavior.swipe_up_apps_drawer,
            R.id.android_widget_drawer_view to Behavior.show_android_widget_drawer_button,
            R.id.termux_workspace_view to Behavior.show_tmux_workspace_button).forEach { (id, setting) ->
            controls.findViewById<View>(id).visibility = if (values.bool(setting)) View.VISIBLE else View.GONE
        }
        controls.findViewById<LinearLayout>(R.id.tools_view).apply {
            weightSum = (0 until childCount).count { getChildAt(it).visibility != View.GONE }.coerceAtLeast(1).toFloat()
        }
        val chips = controls.findViewById<LinearLayout>(R.id.suggestions_group)
        val styler = ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler(context, values = values)
        live.suggestions.forEach { (text, type) ->
            val chip = ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler.createView(context)
            chip.text = text; chip.typeface = font()
            styler.apply(chip, type, null)
            chips.addView(chip)
        }
        controls.findViewById<View>(R.id.suggestions_container).visibility = if (values.bool(ohi.andre.consolelauncher.managers.xml.options.Suggestions.show_suggestions)) View.VISIBLE else View.GONE
        ohi.andre.consolelauncher.tuils.InputControlsRenderer.style(controls, values, font())
        passive(controls)
        parent.addView(controls)
    }

    private fun ansi(parent: LinearLayout) {
        val roles = listOf(
            Theme.ansi_black, Theme.ansi_red, Theme.ansi_green, Theme.ansi_yellow,
            Theme.ansi_blue, Theme.ansi_magenta, Theme.ansi_cyan, Theme.ansi_white,
            Theme.ansi_bright_black, Theme.ansi_bright_red, Theme.ansi_bright_green, Theme.ansi_bright_yellow,
            Theme.ansi_bright_blue, Theme.ansi_bright_magenta, Theme.ansi_bright_cyan, Theme.ansi_bright_white
        )
        val labels = listOf("Black", "Red", "Green", "Yellow", "Blue", "Magenta", "Cyan", "White")
        val scene = titledPanel(parent, "ANSI TERMINAL", Theme.output_background_color, Theme.terminal_border_color,
            radius(Ui.output_corner_radius), SurfaceBorder.OUTPUT, FrameTarget.OUTPUT)
        scene.addView(richSample(
            "$ ls --color\n" to Theme.ansi_bright_white,
            "Documents/  " to Theme.ansi_blue, "run.sh  " to Theme.ansi_green, "latest → build/\n" to Theme.ansi_cyan,
            "$ build\n" to Theme.ansi_bright_white,
            "✓ SUCCESS  " to Theme.ansi_green, "INFO  " to Theme.ansi_cyan,
            "WARNING  " to Theme.ansi_yellow, "ERROR\n" to Theme.ansi_red,
            "local " to Theme.ansi_magenta, "ready = " to Theme.ansi_white, "true\n" to Theme.ansi_yellow,
            "print" to Theme.ansi_blue, "(\"Hello, Re:TUI\")" to Theme.ansi_green
        ).apply { textSize = 11f; setPadding(dp(6), dp(4), dp(6), dp(4)) })
        parent.addView(sample(context.getString(R.string.settings_preview_palette), Theme.output_text_color).apply {
            textSize = 10f; setPadding(dp(4), dp(2), dp(4), dp(2))
        })
        roles.chunked(4).forEachIndexed { group, colors ->
            val line = LinearLayout(context)
            colors.forEachIndexed { column, role ->
                val index = group * 4 + column
                line.addView(sample("${if (index < 8) 30 + index else 90 + index - 8} ${labels[index % 8]}", role).apply {
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setPadding(dp(2), dp(4), dp(2), dp(4))
                    val swatch = color(role)
                    setBackgroundColor(swatch)
                    val background = androidx.core.graphics.ColorUtils.compositeColors(swatch, color(Theme.output_background_color))
                    setTextColor(if (androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK, background) >=
                        androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, background)) Color.BLACK else Color.WHITE)
                }, LayoutParams(0, -2, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            parent.addView(line)
        }
    }

    private fun modules(parent: LinearLayout) {
        val music = LayoutInflater.from(context).inflate(R.layout.music_module, parent, false)
        music.visibility = View.VISIBLE
        ohi.andre.consolelauncher.tuils.MusicModuleRenderer.style(music, values, font())
        ohi.andre.consolelauncher.tuils.MusicModuleRenderer.bind(music, live.song, live.singer,
            context.getString(R.string.surface_title_edd45), context.getString(R.string.surface_singer_e58b5), values)
        music.findViewById<View>(R.id.music_app_row).visibility = if (live.musicApp.isEmpty()) View.GONE else View.VISIBLE
        music.findViewById<TextView>(R.id.music_app_label).text = live.musicApp
        passive(music)
        parent.addView(music)
        val ui = ohi.andre.consolelauncher.LauncherActivity.instance?.uiManager
        val luaResult = live.luaResult
        val luaModule = live.luaModule
        if (ui != null && luaResult != null && luaModule != null) {
            val module = ui.buildLuaPreview(luaModule, luaResult, values)
            passive(module)
            parent.addView(module)
            return
        }
        val module = LayoutInflater.from(context).inflate(R.layout.module_text_panel, parent, false)
        val label = module.findViewById<TextView>(R.id.module_text_label)
        val body = module.findViewById<TextView>(R.id.module_text_body)
        label.text = live.moduleTitle.ifEmpty { context.getString(R.string.settings_preview_current_module) }
        body.text = live.moduleText.ifEmpty { context.getString(R.string.settings_preview_no_module) }
        body.typeface = font()
        body.textSize = values.size(Ui.module_body_text_size)
        body.setTextColor(color(Theme.module_panel_text_color))
        ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.decorateWidget(module, R.id.module_text_border, R.id.module_text_label, R.id.module_text_close,
            color(Theme.module_panel_border_color), color(Theme.module_panel_text_color), FrameTarget.MODULES, values, font())
        module.findViewById<TextView>(R.id.module_text_close).apply {
            typeface = font(); textSize = values.size(Ui.module_header_text_size)
            setTextColor(color(Theme.module_panel_text_color))
            background = TerminalBorderRuntime.tabDrawable(context, color(Theme.module_header_control_background_color), FrameTarget.MODULES, values)
        }
        passive(module)
        parent.addView(module)
    }
}
