package ohi.andre.consolelauncher.managers.media

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.help.PanelBackdrop
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import java.util.Collections

/** A library screen in the launcher's workspace look: one framed window, rows of slim buttons and lists of lines. */
abstract class WorkspaceActivity : VideoActivity() {
    protected class Line(val title: String, val sub: String = "", val meta: String = "", val on: Boolean = false,
        val tap: (() -> Unit)? = null, val hold: (() -> Unit)? = null, val header: Boolean = false, val wrap: Boolean = false)

    override val font: Typeface by lazy { Tuils.getTypeface(this) ?: Typeface.DEFAULT }
    override val border by lazy { ThemeColorResolver.color(Theme.workspace_panel_border_color) }
    override val ink by lazy { ThemeColorResolver.color(Theme.workspace_panel_text_color) }
    override val sel by lazy { ThemeColorResolver.color(Theme.selection_background_color) }
    override val head by lazy { ThemeColorResolver.color(Theme.workspace_panel_text_color) }
    override val dim by lazy { ThemeColorResolver.color(Theme.muted_text_color) }
    override val ground by lazy { ThemeColorResolver.color(Theme.workspace_panel_background_color) }
    override val onSelected by lazy { ThemeColorResolver.color(Theme.selection_text_color) }
    override val control by lazy { ThemeColorResolver.color(Theme.workspace_control_text_color) }

    /** Controls follow the launcher's dashed-border and corner settings, but a custom frame image is for whole panels. */
    override fun outline(color: Int, fill: Int, radius: Int): Drawable = TerminalBorderRuntime.panelDrawable(this, fill, color, 1f,
        AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), cyberdeckNotch = false, frameEnabled = false)

    /** The same window as help and the Termux console: one framed panel with a title tab, the launcher dimmed behind. */
    protected fun buildWindow(title: String, content: View): View {
        val panel = FrameLayout(this).apply {
            clipChildren = false
            background = TerminalBorderRuntime.panelDrawable(context, ground, border, 1.5f,
                AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
            setPadding(dp(8), dp(22), dp(8), dp(8))
            addView(content, FrameLayout.LayoutParams(-1, -1))
        }
        val header = label(title, ThemeColorResolver.color(Theme.workspace_header_text_color), LauncherFontScale.terminalTextSizeSp()).apply {
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
            setPadding(dp(10), 0, dp(10), 0)
            background = TerminalBorderRuntime.tabDrawable(context, ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
        }
        val root = FrameLayout(this).apply {
            clipChildren = false
            setPadding(dp(8), 0, dp(8), dp(4))
            addView(panel, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(18); bottomMargin = dp(2) })
            addView(header, FrameLayout.LayoutParams(-2, dp(20)).apply { leftMargin = dp(48); topMargin = dp(8) })
        }
        val screen = FrameLayout(this).apply {
            isFocusableInTouchMode = true
            // It only holds focus to keep the keyboard away from the search box, so it must not look focused.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
        }
        val viewport = FrameLayout(this)
        TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT).addView(viewport, ViewGroup.LayoutParams(-1, -1))
        PanelBackdrop.install(viewport)
        viewport.addView(root, FrameLayout.LayoutParams(-1, -1))
        TerminalBorderRuntime.bind(panel, header)
        ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        screen.post { screen.requestFocus() }
        return screen
    }


    protected fun row(vararg views: View) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it) }
    }

    /** Equal-width buttons with the player's 6dp gaps. */
    protected fun evenRow(buttons: List<TextView>) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        buttons.forEachIndexed { i, b ->
            addView(b, LinearLayout.LayoutParams(0, dp(TOUCH), 1f).apply { if (i > 0) leftMargin = dp(GAP) })
        }
    }

    protected fun section(heading: TextView, list: View) = FrameLayout(this).apply {
        background = outline(border, ThemeColorResolver.color(Theme.workspace_output_background_color))
        clipChildren = false
        addView(FrameLayout(context).apply { addView(list, FrameLayout.LayoutParams(-1, -1)) }, FrameLayout.LayoutParams(-1, -1))
        addView(heading, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) })
    }

    protected fun lineList(adapter: Lines) = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
        setPadding(0, dp(12), 0, dp(4))
        clipToPadding = false
        itemAnimator = null
    }

    protected inner class Lines(private val editable: Boolean = false) : RecyclerView.Adapter<Lines.Holder>() {
        private var lines: List<Line> = emptyList()
        var startDrag: (RecyclerView.ViewHolder) -> Unit = {}
        var remove: (Int) -> Unit = {}

        @SuppressLint("NotifyDataSetChanged")
        fun set(next: List<Line>) {
            lines = next
            notifyDataSetChanged()
        }

        fun swap(a: Int, b: Int) {
            lines = ArrayList(lines).also { Collections.swap(it, a, b) }
            notifyItemMoved(a, b)
        }

        inner class Holder(val root: LinearLayout, val handle: TextView, val title: TextView, val sub: TextView, val meta: TextView, val x: TextView) : RecyclerView.ViewHolder(root)

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val handle = label("=").apply {
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.player_reorder)
                visibility = if (editable) View.VISIBLE else View.GONE
            }
            val title = label().apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            val sub = label(size = 11f).apply { isSingleLine = true; ellipsize = TextUtils.TruncateAt.END }
            val meta = label().apply { setPadding(dp(10), 0, 0, 0) }
            val x = label("x").apply {
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.player_remove)
                visibility = if (editable) View.VISIBLE else View.GONE
            }
            val texts = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            }
            val root = LinearLayout(parent.context).apply {
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(-1, dp(ROW))
                setPadding(dp(8), 0, if (editable) 0 else dp(8), 0)
                addView(handle, LinearLayout.LayoutParams(dp(32), -1))
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                addView(meta)
                addView(x, LinearLayout.LayoutParams(dp(TOUCH), -1))
            }
            return Holder(root, handle, title, sub, meta, x).also { holder ->
                handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) startDrag(holder)
                    false
                }
                x.setOnClickListener { holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(remove) }
            }
        }

        override fun getItemCount() = lines.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val line = lines[position]
            val fg = if (line.on) onSelected else if (line.header) head else ink
            holder.root.setBackgroundColor(if (line.on) sel else Color.TRANSPARENT)
            holder.root.layoutParams = holder.root.layoutParams.apply { height = if (line.wrap) -2 else dp(ROW) }
            holder.root.setPadding(holder.root.paddingLeft, if (line.wrap) dp(WRAP_PADDING) else 0, holder.root.paddingRight, if (line.wrap) dp(WRAP_PADDING) else 0)
            holder.title.isSingleLine = !line.wrap
            holder.title.ellipsize = if (line.wrap) null else TextUtils.TruncateAt.END
            holder.title.text = line.title
            holder.sub.text = line.sub
            holder.sub.visibility = if (line.sub.isEmpty()) View.GONE else View.VISIBLE
            holder.meta.text = line.meta
            holder.title.setTextColor(fg)
            holder.handle.setTextColor(fg)
            holder.x.setTextColor(fg)
            holder.meta.setTextColor(fg)
            holder.sub.setTextColor(if (line.on) onSelected else dim)
            holder.root.setOnClickListener(line.tap?.let { tap -> View.OnClickListener { tap() } })
            holder.root.isClickable = line.tap != null
            holder.root.setOnLongClickListener(line.hold?.let { hold -> View.OnLongClickListener { hold(); true } })
        }
    }


    companion object {
        const val GAP = 6
        const val ROW = 44
        private const val WRAP_PADDING = 6
    }
}
