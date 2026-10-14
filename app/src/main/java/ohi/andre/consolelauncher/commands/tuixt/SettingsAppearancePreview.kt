package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.FramedDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

/** Owns sample views only; draft values never enter the launcher's settings or color cache. */
internal class SettingsAppearancePreview(
    context: Context,
    private val draft: (XMLPrefsSave) -> String?,
    private val hasDraft: (XMLPrefsSave) -> Boolean
) : LinearLayout(context) {
    private val pageIndicator = TextView(context)
    private val pager = RecyclerView(context)
    private val manager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val snap = PagerSnapHelper()
    private var fontKey: String? = null
    private var previewFont: Typeface? = null
    private val refreshTask = Runnable { pager.adapter?.notifyDataSetChanged() }
    private val titles = listOf(R.string.settings_preview_status, R.string.settings_preview_terminal, R.string.settings_preview_modules, R.string.settings_preview_ansi)

    init {
        orientation = VERTICAL
        addView(TextView(context).apply {
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
        pager.layoutManager = manager
        pager.adapter = object : RecyclerView.Adapter<Page>() {
            override fun getItemCount() = titles.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Page = Page(ScrollView(context).apply {
                layoutParams = RecyclerView.LayoutParams(pager.width.coerceAtLeast(1), -1)
                isFillViewport = true
            })
            override fun onBindViewHolder(holder: Page, position: Int) {
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
            }
        }
        snap.attachToRecyclerView(pager)
        pager.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

    private class Page(val scroll: ScrollView) : RecyclerView.ViewHolder(scroll)
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
    override fun onDetachedFromWindow() { removeCallbacks(refreshTask); super.onDetachedFromWindow() }
    private fun dp(value: Int) = TuixtTheme.dp(context, value.toFloat())
    private fun raw(item: XMLPrefsSave) = draft(item) ?: LauncherSettings.get(item) ?: item.defaultValue()
    private fun bool(item: XMLPrefsSave) = raw(item).toBoolean()
    private fun number(item: XMLPrefsSave, low: Float, high: Float): Float =
        (raw(item)?.toFloatOrNull()?.takeIf { it.isFinite() } ?: item.defaultValue()?.toFloatOrNull() ?: low).coerceIn(low, high)
    private fun color(role: Theme) = ThemeColorResolver.resolve(role, { raw(it) }, emptyMap())
    private fun radius(item: Ui): Int = if (raw(item) != item.defaultValue())
        number(item, 0f, 48f).toInt() else number(Ui.dashed_border_corner_radius, 0f, 48f).toInt()

    private fun updateIndicator(selected: Int) {
        pageIndicator.text = context.getString(R.string.settings_preview_page,
            context.getString(titles[selected]), selected + 1, titles.size)
    }

    private fun panel(fill: Int, border: Int, radius: Int, surface: SurfaceBorder? = null,
                      target: FrameTarget = FrameTarget.MODULES, allowBorder: Boolean = true, frameEnabled: Boolean = true): android.graphics.drawable.Drawable {
        val frame = if (frameEnabled) FrameManager.drawable(context, target) else null
        val cyber = frame == null && bool(Behavior.enable_cyberdeck_mode)
        val dashed = bool(Ui.enable_dashed_border)
        val enabled = allowBorder && (surface == null || (dashed && bool(surface.enabled)))
        val stroke = if (frame == null && enabled && (cyber || dashed))
            Tuils.dpToPx(context, 1f) * (if (cyber) 1.5f else number(Ui.dashed_border_stroke_width, .5f, 8f)) else 0f
        val resolvedBorder = surface?.let { SurfaceBorder.resolveColor(raw(it.color), color(Theme.terminal_border_color)) } ?: border
        val fallback = TerminalBorderDrawable(
            fill, resolvedBorder, stroke.toInt().coerceAtLeast(if (stroke > 0) 1 else 0),
            if (cyber) 0f else dp(radius).toFloat(), dashed && !cyber,
            dp(number(Ui.dashed_border_dash_length, 0f, 200f).toInt()).toFloat(),
            dp(number(Ui.dashed_border_gap_length, 0f, 200f).toInt()).toFloat(), cyber, true
        )
        return if (frame == null) fallback else FramedDrawable(fallback, frame)
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
        textSize = ((size?.let { number(it, 8f, 32f) } ?: 13f) + number(Ui.font_size_offset, -6f, 12f)).coerceAtLeast(8f)
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
        val savedOrder = StatusRowResolver.settings.associateWith { LauncherSettings.get(it) }
        val edits = StatusRowResolver.settings.filter { hasDraft(it) }.associateWith { raw(it) }
        val order = StatusRowResolver.reorder(savedOrder, edits).values
        val rows = listOf(
            Status("RAM  4.2 / 8 GB", Ui.show_ram, Ui.ram_size, Ui.ram_index, Ui.ram_status_alignment, Theme.ram_text_color, Theme.ram_status_background_color, SurfaceBorder.RAM),
            Status("Re:TUI · Android", Ui.show_device_name, Ui.device_size, Ui.device_index, Ui.device_status_alignment, Theme.device_text_color, Theme.device_status_background_color, SurfaceBorder.DEVICE),
            Status("14 OCT  ·  13:55", Ui.show_time, Ui.time_size, Ui.time_index, Ui.time_status_alignment, Theme.time_text_color, Theme.time_status_background_color, SurfaceBorder.TIME),
            Status("BATTERY  82%", Ui.show_battery, Ui.battery_size, Ui.battery_index, Ui.battery_status_alignment, Theme.battery_text_high, Theme.battery_status_background_color, SurfaceBorder.BATTERY),
            Status("STORAGE  64 / 128 GB", Ui.show_storage_info, Ui.storage_size, Ui.storage_index, Ui.storage_status_alignment, Theme.storage_text_color, Theme.storage_status_background_color, SurfaceBorder.STORAGE),
            Status("Wi-Fi  ·  Connected", Ui.show_network_info, Ui.network_size, Ui.network_index, Ui.network_status_alignment, Theme.network_info_text_color, Theme.network_status_background_color, SurfaceBorder.NETWORK),
            Status("NOTES  ·  Design something today", Ui.show_notes, Ui.notes_size, Ui.notes_index, Ui.notes_status_alignment, Theme.notes_text_color, Theme.notes_status_background_color, SurfaceBorder.NOTES),
            Status("WEATHER  24°C · Clear", Ui.show_weather, Ui.weather_size, Ui.weather_index, Ui.weather_status_alignment, Theme.weather_text_color, Theme.weather_status_background_color, SurfaceBorder.WEATHER),
            Status("UNLOCKS  12 today", Ui.show_unlock_counter, Ui.unlock_size, Ui.unlock_index, Ui.unlock_status_alignment, Theme.unlock_counter_text_color, Theme.unlock_status_background_color, SurfaceBorder.UNLOCK),
            Status("[ Re:TUI ]", Ui.show_ascii, Ui.ascii_size, Ui.ascii_index, Ui.ascii_status_alignment, Theme.ascii_text_color, Theme.ascii_status_background_color, SurfaceBorder.ASCII)
        ).filter { bool(it.show) }.sortedBy { order.getValue(it.index).toBigDecimal() }
        val unified = bool(SurfaceBorderOption.unified_status_border)
        val host = if (unified) LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(5), dp(5), dp(5), 0)
            addPanel(parent, this, Theme.terminal_window_background_color, Theme.terminal_border_color,
                radius(Ui.module_corner_radius), target = FrameTarget.STATUS_GROUP)
        } else parent
        rows.groupBy { order.getValue(it.index).toBigDecimal().toInt() }.values.forEach { group ->
            val line = LinearLayout(context)
            group.forEach { row ->
                line.addView(sample(row.text, row.textRole, row.size).apply {
                    gravity = when (raw(row.align)?.lowercase()) { "center" -> Gravity.CENTER; "right" -> Gravity.END; else -> Gravity.START }
                    background = panel(color(row.fill), color(Theme.terminal_border_color), radius(Ui.module_corner_radius), row.surface,
                        FrameTarget.fromSurface(row.surface), !unified, !unified)
                }, LayoutParams(0, -2, 1f).apply { marginEnd = dp(3) })
            }
            host.addView(line, LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
        }
        if (rows.isEmpty()) parent.addView(sample(context.getString(R.string.settings_preview_status_hidden), Theme.output_text_color))
    }

    private data class Status(val text: String, val show: Ui, val size: Ui, val index: Ui, val align: Ui,
                              val textRole: Theme, val fill: Theme, val surface: SurfaceBorder)

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
        val title = when (raw(Behavior.output_header_mode)) { "none" -> null; "arrows" -> "↑ OUTPUT ↓"; else -> "OUTPUT" }
        val output = titledPanel(parent, title, Theme.output_background_color, Theme.terminal_border_color,
            radius(Ui.output_corner_radius), SurfaceBorder.OUTPUT, FrameTarget.OUTPUT)
        output.addView(richSample(
            "$ ls -lah\n" to Theme.input_text_color,
            "Documents/  Music/  Downloads/\n" to Theme.output_text_color,
            "✓ Backup complete  ·  3 files\n" to Theme.success_text_color,
            "! Network unavailable" to Theme.error_text_color
        ))
        addPanel(parent, sample("${raw(Ui.input_prefix) ?: "$"} weather" + if (bool(Ui.show_enter_button)) "   ↵" else "", Theme.input_text_color, Ui.input_output_size),
            Theme.input_background_color, Theme.terminal_border_color, radius(Ui.module_corner_radius), SurfaceBorder.INPUT, FrameTarget.INPUT)
        addPanel(parent, sample("weather   ·   files   ·   settings", Theme.output_text_color), Theme.suggestions_background_color,
            Theme.terminal_border_color, radius(Ui.module_corner_radius), SurfaceBorder.SUGGESTIONS, FrameTarget.SUGGESTIONS)
        addPanel(parent, sample("×   ↑   ↓       ▣       ☰", Theme.toolbar_icon_color).apply { gravity = Gravity.CENTER },
            Theme.toolbar_background_color, Theme.terminal_border_color, radius(Ui.module_corner_radius), SurfaceBorder.TOOLBAR, FrameTarget.TOOLBAR)
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
        val module = titledPanel(parent, "MUSIC", Theme.module_panel_background_color, Theme.module_panel_border_color,
            radius(Ui.module_corner_radius))
        module.addView(sample("Night drive\nRe:TUI Radio · 02:14 / 04:08", Theme.module_panel_text_color, Ui.module_body_text_size))
        val actions = LinearLayout(context)
        listOf("◀", "▶ / Ⅱ", "▶▶").forEach { label ->
            actions.addView(sample(label, Theme.module_action_text_color).apply {
                gravity = Gravity.CENTER
                background = panel(color(Theme.module_action_background_color), color(Theme.module_action_border_color), radius(Ui.module_corner_radius))
            }, LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) })
        }
        module.addView(actions)
        val note = titledPanel(parent, "NOTES", Theme.module_panel_background_color, Theme.module_panel_border_color,
            radius(Ui.module_corner_radius))
        note.addView(sample("Pick up where you left off", Theme.module_panel_text_color, Ui.module_body_text_size))
        val states = LinearLayout(context)
        listOf(
            Triple(R.string.settings_preview_normal, Theme.button_text_color, Theme.button_background_color),
            Triple(R.string.settings_preview_selected, Theme.selection_text_color, Theme.selection_background_color),
            Triple(R.string.settings_preview_disabled, Theme.disabled_text_color, Theme.disabled_background_color)
        ).forEachIndexed { index, (label, text, fill) ->
            states.addView(sample(context.getString(label), text).apply {
                textSize = 10f
                gravity = Gravity.CENTER
                background = panel(color(fill), color(if (index == 1) Theme.selection_border_color else Theme.button_border_color), radius(Ui.module_corner_radius))
            }, LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        }
        parent.addView(states)
    }
}
