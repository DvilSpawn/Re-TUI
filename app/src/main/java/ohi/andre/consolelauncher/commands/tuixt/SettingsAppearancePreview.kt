package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.presentation.*
import ohi.andre.consolelauncher.tuils.*

/** Readable component examples, using Home's styling with transient appearance values. */
internal class SettingsAppearancePreview(
    context: Context,
    private val draft: (XMLPrefsSave) -> String?,
    private val hasDraft: (XMLPrefsSave) -> Boolean
) : LinearLayout(context) {
    private var saved = HomeAppearance.saved()
    internal val pager = ViewPager2(context)
    private val refreshTask = Runnable { render() }
    private val pages = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 5
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                isFillViewport = true
            }) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val scroll = holder.itemView as ScrollView
            val offset = scroll.scrollY
            scroll.removeAllViews()
            val appearance = saved.overlay(draft, hasDraft)
            val body = LinearLayout(context).apply {
                orientation = VERTICAL
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundColor(ColorUtils.compositeColors(appearance.color(Theme.background_color), Color.BLACK))
            }
            scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
            when (position) {
                0 -> status(body, appearance)
                1 -> terminal(body, appearance)
                2 -> {
                    ModulePresentationSnapshot.idleMusic(context).bind(body, appearance)
                    body.findViewById<View>(R.id.music_module)?.let(ModulePanelPresentation::sizeVisualizer)
                }
                3 -> workspace(body, appearance, false)
                4 -> workspace(body, appearance, true)
            }
            passive(body)
            scroll.scrollTo(0, offset)
        }
    }

    init {
        orientation = VERTICAL
        pager.adapter = pages
        addView(pager, LayoutParams(-1, dp(190)))
    }

    private fun dp(value: Int) = Tuils.dpToPx(context, value)
    fun onSaved() { saved = HomeAppearance.saved(); refresh() }
    fun refresh() { removeCallbacks(refreshTask); postDelayed(refreshTask, 60) }
    fun setAvailableHeight(height: Int, keyboardVisible: Boolean = false) {
        val desired = dp(if (keyboardVisible || height < dp(500)) 120 else 190)
        if (pager.layoutParams.height != desired) pager.layoutParams = pager.layoutParams.apply { this.height = desired }
    }
    private fun render() { pages.notifyDataSetChanged() }
    override fun onDetachedFromWindow() { removeCallbacks(refreshTask); super.onDetachedFromWindow() }

    private fun status(parent: LinearLayout, appearance: HomeAppearance) {
        val samples = listOf("RAM  4.2 / 8 GB", "Re:TUI", "12:34", "Battery  82%", "Storage  64 / 128 GB", "Wi-Fi connected", "A note to remember", "Weather  24°C", "Unlocked 7 times", "Re:TUI")
        val content = StatusPresentation.capture(samples.mapIndexed { index, sample ->
            val role = StatusPresentation.descriptors[index]
            android.text.SpannableString("$sample  ").apply {
                setSpan(android.text.style.ForegroundColorSpan(appearance.color(role.color)), 0, length, 0)
                setSpan(android.text.style.AbsoluteSizeSpan(Tuils.convertSpToPixels(appearance.number(role.size, 12f), context)), 0, length, 0)
            } as CharSequence?
        }.toTypedArray())
        StatusPresentation.rows(appearance, saved).forEach { members ->
            val row = if (members == listOf(9)) AsciiArtTextView(context) else OutlineTextView(context)
            parent.addView(row, LayoutParams(-1, -2))
            StatusPresentation.bindStyle(row, members.first(), appearance)
            StatusPresentation.bindContent(row, members, content, appearance)
            LauncherFontScale.setBaseSize(row, appearance.number(StatusPresentation.descriptors[members.first()].size, 12f), appearance.number(Ui.font_size_offset).toInt())
        }
    }

    private fun terminal(parent: LinearLayout, appearance: HomeAppearance) {
        val output = LayoutInflater.from(context).inflate(R.layout.terminal_page, parent, false)
        parent.addView(output, LayoutParams(-1, dp(105)))
        HomeChromePresentation.styleSurface(output.findViewById(R.id.terminal_output_border), appearance)
        output.findViewById<TextView>(R.id.terminal_view).apply {
            text = "Re:TUI\nReady for your next command."
            HomeChromePresentation.styleTerminalText(this, appearance, false)
        }
        output.findViewById<TextView>(R.id.terminal_tray_toggle).apply {
            setTag(R.id.home_output_header_binding, TerminalTrayToggleView.Binding(false, false))
            TerminalTrayToggleView.styleDraft(this, output, appearance, saved)
        }
        val input = LinearLayout(context).apply { id = R.id.input_group; setPadding(dp(8), dp(6), dp(8), dp(6)) }
        parent.addView(input, LayoutParams(-1, -2))
        HomeChromePresentation.styleSurface(input, appearance)
        input.addView(OutlineTextView(context).apply {
            text = appearance.raw(Ui.input_prefix).orEmpty() + " help"
            HomeChromePresentation.styleTerminalText(this, appearance, true)
        }, LayoutParams(-1, -2))
    }

    /** Inflate the shipped panes without their Activities, engines or action listeners. */
    private fun workspace(parent: LinearLayout, appearance: HomeAppearance, calculator: Boolean) {
        val pane = LayoutInflater.from(context).inflate(
            if (calculator) R.layout.calculator_surface else R.layout.offline_help_surface, parent, false)
        parent.addView(pane, LayoutParams(-1, dp(250)))
        fun textStyle(view: View) {
            if (view is TextView) {
                view.typeface = appearance.font(context)
                view.textSize = LauncherFontScale.scaledSp(12f, appearance.number(Ui.font_size_offset).toInt())
                view.setTextColor(appearance.color(if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color))
                if (view is Button) {
                    view.background = null
                    view.setTypeface(appearance.font(context), android.graphics.Typeface.BOLD)
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) textStyle(view.getChildAt(i))
        }
        textStyle(pane)
        val border = pane.findViewById<View>(if (calculator) R.id.calculator_window_border else R.id.offline_help_panel)
        fun panel(fill: Theme, stroke: Float) = TerminalBorderRuntime.panelDrawablePx(context,
            appearance.color(fill), appearance.color(Theme.workspace_panel_border_color), stroke,
            dp(appearance.radius(Ui.output_corner_radius)).toFloat(), appearance.bool(Ui.enable_dashed_border),
            target = FrameTarget.OVERLAYS, style = appearance.borderStyle())
        border.background = panel(Theme.workspace_panel_background_color, 1.5f)
        val header = pane.findViewById<TextView>(if (calculator) R.id.calculator_main_tab else R.id.offline_help_header)
        header.setTypeface(appearance.font(context), android.graphics.Typeface.BOLD)
        header.textSize = appearance.number(Ui.output_header_text_size, 12f)
        header.background = TerminalBorderRuntime.tabDrawable(context,
            appearance.color(Theme.workspace_header_background_color), appearance.color(Theme.workspace_panel_border_color),
            target = FrameTarget.OVERLAYS, style = appearance.borderStyle(), draftDashed = appearance.bool(Ui.enable_dashed_border))
        TerminalBorderRuntime.bind(border, header)
        val input = pane.findViewById<EditText>(if (calculator) R.id.calculator_expression else R.id.offline_help_input)
        input.setText(if (calculator) "5 + 5" else "1")
        input.setTextColor(appearance.color(Theme.workspace_input_text_color))
        input.showSoftInputOnFocus = false
        if (calculator) {
            pane.findViewById<TextView>(R.id.calculator_result).text = "calc> 2 + 2\n4"
            pane.findViewById<TextView>(R.id.calculator_preview).apply {
                text = "= 10"
                setTextColor(appearance.color(Theme.muted_text_color))
                textSize = LauncherFontScale.scaledSp(12f, appearance.number(Ui.font_size_offset).toInt()) * 0.85f
            }
        } else {
            pane.findViewById<View>(R.id.offline_help_main_pane).background = panel(Theme.workspace_output_background_color, 1.2f)
            pane.findViewById<TextView>(R.id.offline_help_output).text =
                ohi.andre.consolelauncher.help.OfflineHelpCatalog.topics.filter { it.category == null }
                    .take(4).mapIndexed { index, topic -> "${index + 1} ${context.getString(topic.title)}" }.joinToString("\n")
            // The preview has no keyboard controller; show the real output and prompt at readable size.
            pane.findViewById<View>(R.id.offline_help_keys).visibility = View.GONE
        }
    }

    private fun passive(view: View) {
        view.setOnClickListener(null)
        view.isClickable = false
        view.isLongClickable = false
        view.isFocusable = false
        if (view is TextView) view.setTextIsSelectable(false)
        if (view is ViewGroup) for (i in 0 until view.childCount) passive(view.getChildAt(i))
    }
}
