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
import ohi.andre.consolelauncher.managers.xml.options.*
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.presentation.*
import ohi.andre.consolelauncher.tuils.*

/** Passive Home components with captured launcher content and private draft appearance. */
internal class SettingsAppearancePreview(
    context: Context,
    private val draft: (XMLPrefsSave) -> String?,
    private val hasDraft: (XMLPrefsSave) -> Boolean
) : LinearLayout(context) {
    private var saved = HomeAppearance.saved()
    private var live:LauncherHomeSnapshot?=null
    private val pageLabel=TextView(context)
    private val pageNames=listOf("Status", "Terminal", "Modules", "Help", "Calculator", "ANSI")
    private val pageOffsets=IntArray(pageNames.size)
    private val liveTicker=object:Runnable {
        override fun run() {
            if(!isAttachedToWindow || windowVisibility!=View.VISIBLE) return
            refresh()
            postDelayed(this,2000)
        }
    }
    internal val pager = ViewPager2(context)
    private val refreshTask = Runnable { render() }
    private val pages = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pageNames.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                isFillViewport = true
            }) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val scroll = holder.itemView as ScrollView
            val offset = pageOffsets[position]
            scroll.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
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
                    (live?.module ?: ModulePresentationSnapshot.idleMusic(context)).bind(body, appearance)
                    body.findViewById<View>(R.id.music_module)?.let(ModulePanelPresentation::sizeVisualizer)
                }
                3 -> workspace(body, appearance, false)
                4 -> workspace(body, appearance, true)
                5 -> ansi(body, appearance)
            }
            passive(body)
            scroll.post {
                scroll.scrollTo(0,offset)
                scroll.setOnScrollChangeListener { _, _, y, _, _ -> pageOffsets[position]=y }
            }
        }
    }

    init {
        orientation = VERTICAL
        pageLabel.apply {
            textSize=11f
            typeface=Tuils.getTypeface(context)
            setTextColor(TuixtTheme.textColor())
            gravity=android.view.Gravity.CENTER
            setPadding(0,dp(4),0,dp(6))
        }
        addView(pageLabel,LayoutParams(-1,-2))
        pager.descendantFocusability=ViewGroup.FOCUS_BLOCK_DESCENDANTS
        pager.adapter = pages
        pager.registerOnPageChangeCallback(object:ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position:Int) { updateLabel(position) }
        })
        updateLabel(0)
        addView(pager, LayoutParams(-1, dp(190)))
    }

    private fun dp(value: Int) = Tuils.dpToPx(context, value)
    fun onSaved() { saved = HomeAppearance.saved(); refresh() }
    fun refresh() { removeCallbacks(refreshTask); postDelayed(refreshTask, 60) }
    fun setAvailableHeight(height: Int, keyboardVisible: Boolean = false) {
        val desired = dp(if (keyboardVisible || height < dp(500)) 120 else 190)
        if (pager.layoutParams.height != desired) pager.layoutParams = pager.layoutParams.apply { this.height = desired }
    }
    private fun updateLabel(position:Int) {
        pageLabel.text="${context.getString(if(position==5) R.string.settings_preview_ansi_caption else if(position>=3) R.string.settings_preview_sample_caption else R.string.settings_preview_draft)}\n${context.getString(R.string.settings_preview_page,pageNames[position],position+1,pageNames.size)}"
    }
    fun selectedPage()=pager.currentItem
    fun restorePage(position:Int) { pager.setCurrentItem(position.coerceIn(0,pageNames.lastIndex),false) }
    private fun render() {
        live=LauncherActivity.captureHomePreview()
        pages.notifyDataSetChanged()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();post(liveTicker) }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(isAttachedToWindow) { removeCallbacks(liveTicker);if(visibility==View.VISIBLE) post(liveTicker) }
    }
    override fun onDetachedFromWindow() { removeCallbacks(refreshTask);removeCallbacks(liveTicker);super.onDetachedFromWindow() }

    private fun status(parent: LinearLayout, appearance: HomeAppearance) {
        val batteryIntent=context.registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val battery=batteryIntent?.let {
            val level=it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL,0)*100f / it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE,100).coerceAtLeast(1)
            val charging=it.getIntExtra(android.os.BatteryManager.EXTRA_STATUS,-1) in listOf(android.os.BatteryManager.BATTERY_STATUS_CHARGING,android.os.BatteryManager.BATTERY_STATUS_FULL)
            BatteryPresentation.render(context,level,charging,appearance.number(Ui.battery_size,12f).toInt(),
                appearance.number(Behavior.battery_medium).toInt(),appearance.number(Behavior.battery_low).toInt(),appearance)
        }
        val source=live?.statuses
        val texts=source?.texts?.toMutableList() ?: MutableList<CharSequence?>(StatusPresentation.descriptors.size) { null }
        if(battery!=null) texts[3]=battery
        val content=StatusPresentation.Content(texts,source?.asciiColor,source?.asciiColors)
        if(appearance.bool(SurfaceBorderOption.unified_status_border) && live?.unified!=null) {
            val root=LayoutInflater.from(context).inflate(R.layout.about_device_view,parent,false)
            parent.addView(root)
            for(id in intArrayOf(R.id.tv0,R.id.tv1,R.id.tv2,R.id.tv3,R.id.tv4,R.id.tv5,R.id.tv6,R.id.tv7,R.id.tv8,R.id.tv9)) root.findViewById<View>(id).visibility=View.GONE
            root.findViewById<View>(R.id.unified_status_container).visibility=View.VISIBLE
            UnifiedStatusPresentation.style(root,appearance)
            val unified=checkNotNull(live?.unified)
            val bound=if(battery==null) unified else unified.copy(texts=unified.texts+(R.id.unified_status_battery to battery))
            UnifiedStatusPresentation.bindContent(root,bound,appearance,saved)
        } else StatusPresentation.rows(appearance,saved).forEachIndexed { index,members ->
            val template=LayoutInflater.from(context).inflate(R.layout.about_device_view,null,false)
            val id=intArrayOf(R.id.tv0,R.id.tv1,R.id.tv2,R.id.tv3,R.id.tv4,R.id.tv5,R.id.tv6,R.id.tv7,R.id.tv8,R.id.tv9)[index]
            val original=template.findViewById<TextView>(id)
            val base=LauncherFontScale.pixelsToSp(original)
            val row=if(members==listOf(9)) AsciiArtTextView(context) else original.also {(it.parent as ViewGroup).removeView(it)}
            row.id=id
            parent.addView(row,LayoutParams(-1,-2))
            StatusPresentation.bindStyle(row,members.first(),appearance)
            StatusPresentation.bindContent(row,members,content,appearance,saved)
            LauncherFontScale.setBaseSize(row,base,appearance.number(Ui.font_size_offset).toInt())
        }
    }

    private fun terminal(parent:LinearLayout,appearance:HomeAppearance) {
        val snapshot=live
        if(snapshot!=null) snapshot.bindTerminalCard(parent,appearance)
        else parent.addView(TextView(context).apply {
            text=context.getString(R.string.settings_preview_no_launcher)
            setTextColor(appearance.color(Theme.output_text_color))
            typeface=appearance.font(context)
        })
    }

    private fun ansi(parent:LinearLayout,appearance:HomeAppearance) {
        fun line(text:String,role:Theme) = TextView(context).apply {
            this.text=text
            typeface=appearance.font(context)
            setTextColor(appearance.color(role))
            textSize=13f
            setPadding(dp(6),dp(4),dp(6),dp(4))
        }
        parent.addView(line("$ ls --color",Theme.ansi_bright_white))
        parent.addView(line("Documents/  run.sh  latest → build/",Theme.ansi_blue))
        val sample=android.text.SpannableStringBuilder()
        listOf("SUCCESS  " to Theme.ansi_green,"INFO  " to Theme.ansi_cyan,"WARNING  " to Theme.ansi_yellow,"ERROR" to Theme.ansi_red).forEach { (text,role) ->
            val start=sample.length;sample.append(text)
            sample.setSpan(android.text.style.ForegroundColorSpan(appearance.color(role)),start,sample.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        parent.addView(line("",Theme.output_text_color).apply { text=sample })
        val roles=listOf(Theme.ansi_black,Theme.ansi_red,Theme.ansi_green,Theme.ansi_yellow,Theme.ansi_blue,Theme.ansi_magenta,Theme.ansi_cyan,Theme.ansi_white,
            Theme.ansi_bright_black,Theme.ansi_bright_red,Theme.ansi_bright_green,Theme.ansi_bright_yellow,Theme.ansi_bright_blue,Theme.ansi_bright_magenta,Theme.ansi_bright_cyan,Theme.ansi_bright_white)
        roles.chunked(4).forEachIndexed { group,colors ->
            val row=LinearLayout(context)
            colors.forEachIndexed { column,role ->
                val index=group*4+column
                row.addView(line((if(index<8) 30+index else 90+index-8).toString(),role).apply {
                    val color=appearance.color(role)
                    setBackgroundColor(color)
                    val solid=ColorUtils.compositeColors(color,Color.BLACK)
                    setTextColor(if(ColorUtils.calculateLuminance(solid)>.4) Color.BLACK else Color.WHITE)
                    gravity=android.view.Gravity.CENTER
                },LayoutParams(0,-2,1f).apply {setMargins(dp(2),dp(2),dp(2),dp(2))})
            }
            parent.addView(row)
        }
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
