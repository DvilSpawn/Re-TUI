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

/**
 * Status, Terminal and Modules are crops of one detached Home scene built by Home's own layout and binders
 * (LauncherHomeSnapshot.createDraft), restyled in place per edit. Help, Calculator and ANSI are samples.
 * The side-by-side landscape layout adds a first page showing that whole scene.
 */
internal class SettingsAppearancePreview(
    context: Context,
    private val draft: (XMLPrefsSave) -> String?,
    private val hasDraft: (XMLPrefsSave) -> Boolean,
    wholeHome: Boolean = false
) : LinearLayout(context) {
    private var saved = HomeAppearance.saved()
    private var live:LauncherHomeSnapshot?=null
    internal var scene:ViewGroup?=null
        private set
    private val pageLabel=TextView(context)
    private val pageNames=listOf("Status", "Terminal", "Modules", "Help", "Calculator", "ANSI", "Home")
    // Pages by kind, an index into pageNames; positions shift when the Home page is shown.
    private val pageKinds=(if (wholeHome) listOf(HOME) else emptyList()) + (0..5)
    private var filling=false
    private val pageOffsets=IntArray(pageKinds.size)
    private val stale=BooleanArray(pageKinds.size)
    internal val pager = ViewPager2(context)
    private val refreshTask = Runnable { render() }
    private val pages = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pageKinds.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                isFillViewport = true
            }) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            stale[position] = false
            val scroll = holder.itemView as ScrollView
            val offset = pageOffsets[position]
            scroll.setOnScrollChangeListener(null as View.OnScrollChangeListener?)
            scroll.removeAllViews()
            val appearance = saved.overlay(draft, hasDraft)
            val body = LinearLayout(context).apply {
                orientation = VERTICAL
                setBackgroundColor(ColorUtils.compositeColors(appearance.color(Theme.background_color), Color.BLACK))
            }
            scroll.addView(body, FrameLayout.LayoutParams(-1, -2))
            val kind = pageKinds[position]
            if ((kind <= 2 || kind == HOME) && scene == null) body.addView(TextView(context).apply {
                text = context.getString(R.string.settings_preview_no_launcher)
                setTextColor(appearance.color(Theme.output_text_color))
                typeface = appearance.font(context)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }) else when (kind) {
                0, 1, HOME -> body.addView(ScenePort(kind))
                2 -> if (live?.module != null) body.addView(ScenePort(2)) else {
                    body.setPadding(dp(8), dp(8), dp(8), dp(8))
                    ModulePresentationSnapshot.idleMusic(context).bind(body, appearance)
                    body.findViewById<View>(R.id.music_module)?.let(ModulePanelPresentation::sizeVisualizer)
                }
                3 -> workspace(body.padded(), appearance, false)
                4 -> workspace(body.padded(), appearance, true)
                5 -> ansi(body.padded(), appearance)
            }
            passive(body)
            scroll.post {
                scroll.scrollTo(0,offset)
                scroll.setOnScrollChangeListener { _, _, y, _, _ -> pageOffsets[position]=y }
            }
        }
    }

    private fun LinearLayout.padded() = apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }

    /** Draws one region of the shared scene, fitted to this page's width. The scene stays detached and passive. */
    private inner class ScenePort(private val page: Int) : View(context) {
        private fun region(): android.graphics.Rect? {
            val root = scene ?: return null
            val full = android.graphics.Rect(0, 0, root.width, root.height)
            val area = when (page) {
                0 -> bounds(root, R.id.header_container) ?: bounds(root, R.id.unified_status_host)
                1 -> bounds(root, R.id.terminal_tray_container)?.let { android.graphics.Rect(0, it.top, root.width, root.height) }
                HOME -> full
                else -> bounds(root, R.id.home_modules_root)
            }
            return area?.takeIf { it.intersect(full) && !it.isEmpty } ?: full
        }
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val width = MeasureSpec.getSize(widthSpec)
            val area = region()
            setMeasuredDimension(width, if (area == null) 0 else (area.height().toLong() * width / area.width()).toInt())
        }
        override fun onDraw(canvas: android.graphics.Canvas) {
            val root = scene ?: return
            val area = region() ?: return
            val scale = width.toFloat() / area.width()
            canvas.save()
            canvas.scale(scale, scale)
            canvas.translate(-area.left.toFloat(), -area.top.toFloat())
            canvas.clipRect(area)
            root.draw(canvas)
            canvas.restore()
        }
    }

    /** View bounds in scene coordinates; null when hidden or empty. */
    private fun bounds(root: ViewGroup, id: Int): android.graphics.Rect? {
        var view: View = root.findViewById(id) ?: return null
        if (view.width == 0 || view.height == 0) return null
        val rect = android.graphics.Rect(0, 0, view.width, view.height)
        while (view !== root) {
            if (view.visibility != View.VISIBLE) return null
            rect.offset(view.left + view.translationX.toInt(), view.top + view.translationY.toInt())
            val parent = view.parent as? View ?: return null
            rect.offset(-parent.scrollX, -parent.scrollY)
            view = parent
        }
        return rect
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
        // The default change crossfade makes every rebind flash.
        (pager.getChildAt(0) as? RecyclerView)?.itemAnimator = null
        pager.registerOnPageChangeCallback(object:ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position:Int) {
                updateLabel(position)
                if(stale[position]) pages.notifyItemChanged(position)
            }
        })
        updateLabel(0)
        addView(pager, LayoutParams(-1, dp(190)))
    }

    private fun dp(value: Int) = Tuils.dpToPx(context, value)
    fun onSaved() { saved = HomeAppearance.saved(); refresh() }
    fun refresh() { removeCallbacks(refreshTask); postDelayed(refreshTask, 60) }
    /** Lets the pager take all the height its parent gives it, for the side-by-side landscape layout. */
    fun fillHeight() {
        filling = true
        pager.layoutParams = LayoutParams(-1, 0, 1f)
    }
    fun setAvailableHeight(height: Int, keyboardVisible: Boolean = false) {
        if (filling) return
        val desired = dp(if (keyboardVisible || height < dp(500)) 120 else 190)
        if (pager.layoutParams.height != desired) pager.layoutParams = pager.layoutParams.apply { this.height = desired }
    }
    private fun updateLabel(position:Int) {
        val kind=pageKinds[position]
        pageLabel.text="${context.getString(if(kind==5) R.string.settings_preview_ansi_caption else if(kind in 3..4) R.string.settings_preview_sample_caption else R.string.settings_preview_draft)}\n${context.getString(R.string.settings_preview_page,pageNames[kind],position+1,pageKinds.size)}"
    }
    /** The selected page's kind, so a rotation between layouts keeps the same page. */
    fun selectedPage()=pageKinds[pager.currentItem]
    fun restorePage(kind:Int) { pager.setCurrentItem(pageKinds.indexOf(kind).coerceAtLeast(0),false) }
    private fun render() {
        if (pager.scrollState != ViewPager2.SCROLL_STATE_IDLE) {
            removeCallbacks(refreshTask)
            postDelayed(refreshTask,60)
            return
        }
        // Home is captured once per visit; each edit restyles that one frozen scene in place.
        val appearance = saved.overlay(draft, hasDraft)
        if (live == null) { live = LauncherActivity.captureHomePreview();scene = null }
        val snapshot = live
        val current = scene
        if (snapshot != null) {
            if (current == null) scene = snapshot.createDraft(context, appearance)
            else snapshot.bindDraft(current, appearance)
        }
        // Only the visible page redraws now; the rest catch up when selected.
        stale.fill(true)
        pages.notifyItemChanged(pager.currentItem)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();refresh() }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(isAttachedToWindow && visibility==View.VISIBLE) { live=null;refresh() }
    }
    override fun onDetachedFromWindow() { removeCallbacks(refreshTask);super.onDetachedFromWindow() }

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

    private companion object { const val HOME = 6 }
}
