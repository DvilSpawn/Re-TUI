package ohi.andre.consolelauncher.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.help.HelpWindowPanel
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Notes, split in two: quick notes (one-liners that also show on the status pane) and the journal, a jrnl-style
 * log where each day collects timestamped entries typed at an always-ready prompt.
 */
class JournalActivity : ohi.andre.consolelauncher.localization.LocalizedComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_NOTES = "notes"
        const val TAB_JOURNAL = "journal"
        /** Opens on today's journal with the keyboard up, ready to type. */
        const val EXTRA_WRITE = "write"
    }

    private lateinit var theme: RetuiTheme
    private lateinit var quickStore: QuickNoteStore
    private lateinit var journal: JournalStore
    private lateinit var windowPanel: HelpWindowPanel
    private lateinit var headerView: TextView
    private lateinit var tabsRow: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var bottom: LinearLayout

    private var tab = TAB_JOURNAL
    private var day = today()
    /** Search text while the prompt holds `/find …` or a lone #tag. */
    private var query: String? = null
    private var grid = false
    private var gridMonth: String? = null
    private var editing: JournalEntry? = null
    private var draft = ""
    private var prompt: EditText? = null
    private var journalRows: LinearLayout? = null
    private var journalScroll: ScrollView? = null
    private var promptSymbol: TextView? = null
    private var promptAction: TextView? = null
    private var chips: View? = null
    /** The log's view of what the prompt holds: the entry being edited, or the "now" block of a new one. */
    private var previewText: TextView? = null
    private var previewRow: View? = null

    private var quickNotes = mutableListOf<QuickNote>()
    private var selectedQuick: String? = null
    private var editingQuick: String? = null
    private val revealed = mutableSetOf<String>()
    private var focusOnWindow = false
    /** Entries whose hidden text was unlocked; hidden again when the screen is left. */
    private val unlocked = mutableSetOf<JournalEntry>()
    private val redactions by lazy(::RedactionCrypto)
    private var pendingAuthentication: (() -> Unit)? = null
    /** Text of the entry being edited, kept across the picker, camera or a rotation. */
    private var editDraft: String? = null
    /** The chips row swapped for photo / camera. */
    private var attachMode = false
    private var cameraFile: java.io.File? = null
    private var formatPopup: FormatPopup? = null
    private val thumbnails = android.util.LruCache<String, android.graphics.drawable.Drawable>(24)

    private val pickImages = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris -> attach(uris) }

    private val takePhoto = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { taken ->
        val file = cameraFile
        cameraFile = null
        if (taken && file != null) attach(listOf(android.net.Uri.fromFile(file))) { file.delete() } else file?.delete()
    }

    private val cameraPermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() else toast(getString(R.string.journal_camera_denied)) }

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this)
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(this)
        theme = RetuiTheme.receive(this, intent)
        quickStore = QuickNoteStore(this)
        journal = JournalStore(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    selectedQuick != null || editingQuick != null -> { selectedQuick = null; editingQuick = null; render() }
                    editing != null -> { editing = null; draft = ""; render() }
                    query != null -> { query = null; draft = ""; render() }
                    grid -> { grid = false; render() }
                    tab == TAB_JOURNAL && day != today() -> { day = today(); render() }
                    else -> finish()
                }
            }
        })
        state?.let {
            tab = it.getString("tab", tab)
            day = it.getString("day", day)
            draft = it.getString("draft", "")
        } ?: applyIntent(intent)
        quickNotes = runCatching { quickStore.load() }.getOrElse { mutableListOf() }
        editDraft = state?.getString("edit_draft")
        state?.getString("editing_day")?.let { d -> val t = state.getString("editing_time"); editing = journal.load(d).firstOrNull { it.time == t } }
        buildUi(state)
        if (state == null) pruneImages()
    }

    override fun onNewIntent(next: Intent) {
        super.onNewIntent(next)
        setIntent(next)
        applyIntent(next)
        reload()
    }

    private fun applyIntent(intent: Intent?) {
        tab = if (intent?.getStringExtra(EXTRA_TAB) == TAB_NOTES) TAB_NOTES else TAB_JOURNAL
        if (intent?.getBooleanExtra(EXTRA_WRITE, false) == true) {
            tab = TAB_JOURNAL
            day = today()
            query = null
            grid = false
            editing = null
            focusOnWindow = true
        }
    }

    private fun buildUi(state: Bundle? = null) {
        LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this).apply { isClickable = true }
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        // The same pane geometry as Help and the terminal panes.
        val root = layoutInflater.inflate(R.layout.offline_help_surface, host, false)
        val body = root.findViewById<LinearLayout>(R.id.offline_help_panel)
        body.removeAllViews()
        headerView = root.findViewById<TextView>(R.id.offline_help_header).apply {
            includeFontPadding = false
            typeface = Typeface.create(theme.typeface(this@JournalActivity), Typeface.BOLD)
            textSize = theme.outputHeaderSize.toFloat()
            setTextColor(theme.headerText)
            background = TerminalBorderRuntime.tabDrawable(this@JournalActivity, theme.header, FrameTarget.OVERLAYS)
        }
        body.background = TerminalBorderRuntime.panelDrawable(this, theme.panel, theme.panelBorder,
            1.5f, theme.outputRadius, theme.dashed, target = FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(body, headerView)
        tabsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        body.addView(tabsRow, LinearLayout.LayoutParams(-1, dp(40)).apply { setMargins(dp(8), dp(4), dp(8), 0) })
        content = FrameLayout(this)
        body.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(8)) }
        body.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        val viewport = FrameLayout(this)
        host.addView(viewport, ViewGroup.LayoutParams(-1, -1))
        windowPanel = HelpWindowPanel(viewport, root, state, statePrefix = "journal", floatingInLandscape = false)
        windowPanel.reserveBottomCorners(bottom)
        windowPanel.refreshTheme()
        val top = RetuiTheme.parseMargins(theme.displayMargins) ?: listOf(0f, 0f, 0f, 0f)
        val bottomMargins = RetuiTheme.parseMargins(theme.displayBottomMargins) ?: listOf(0f, 0f, 0f, 0f)
        fun mm(value: Float) = (value * resources.displayMetrics.xdpi / 25.4f).roundToInt()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left + mm(top[0]), bars.top + mm(top[1]),
                bars.right + mm(top[2]), maxOf(bars.bottom, ime.bottom) + mm(bottomMargins[3]))
            insets
        }
        setContentView(screen)
        render()
        androidx.core.view.ViewCompat.requestApplyInsets(screen)
    }

    // region shared pieces

    private val face get() = theme.typeface(this)
    private val dim get() = ColorUtils.setAlphaComponent(theme.outputText, 160)
    private val faint get() = ColorUtils.setAlphaComponent(theme.outputText, 90)
    private val textSp get() = (theme.size * 0.9f).coerceAtLeast(12f)
    private val smallSp get() = (textSp - 2f).coerceAtLeast(10f)
    private val stroke get() = (1.5f * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    private fun outline(fill: Int, bright: Boolean = true) = android.graphics.drawable.GradientDrawable().apply {
        setColor(fill)
        setStroke(stroke, if (bright) theme.panelBorder else ColorUtils.setAlphaComponent(theme.panelBorder, 90))
    }

    private fun text(value: CharSequence, size: Float = textSp, color: Int = theme.outputText, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            typeface = Typeface.create(face, if (bold) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(color)
            includeFontPadding = false
        }

    /** An F-key style key: the symbol in the accent, the label on a block, like the launcher's terminal keys. */
    private fun key(symbol: String, label: String, description: String = label, action: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(text(symbol, smallSp, theme.buttonText).apply { gravity = Gravity.CENTER; setPadding(dp(4), 0, dp(4), 0) },
            LinearLayout.LayoutParams(-2, -1))
        addView(text(label, smallSp, theme.buttonText).apply {
            gravity = Gravity.CENTER_VERTICAL; isSingleLine = true; setPadding(dp(6), 0, dp(6), 0)
            setBackgroundColor(ColorUtils.blendARGB(theme.panel, theme.buttonText, 0.16f))
        }, LinearLayout.LayoutParams(0, -1, 1f))
        contentDescription = description
        isClickable = true
        setOnClickListener { action() }
    }

    private fun keyRow(vararg keys: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        keys.forEachIndexed { index, view ->
            addView(view, LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (index > 0) marginStart = dp(4) })
        }
    }

    private fun scroll(rows: LinearLayout, toEnd: Boolean) = ScrollView(this).apply {
        isVerticalScrollBarEnabled = false
        isFillViewport = true
        addView(rows, FrameLayout.LayoutParams(-1, -2))
        if (toEnd) post { scrollToEnd(this) }
    }

    private fun rule(label: String, click: (() -> Unit)? = null) = text("── $label ──", smallSp, faint).apply {
        setPadding(dp(12), dp(10), dp(12), dp(6))
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        click?.let { action -> setOnClickListener { action() } }
    }

    // endregion

    private fun render() {
        if (!::content.isInitialized) return
        headerView.text = if (tab == TAB_NOTES) getString(R.string.journal_tab_notes) else getString(R.string.journal_tab_journal)
        renderTabs()
        content.removeAllViews()
        bottom.removeAllViews()
        formatPopup?.dismiss(); formatPopup = null
        formatPopup?.dismiss(); formatPopup = null
        prompt = null
        previewText = null; previewRow = null
        journalRows = null; journalScroll = null; promptSymbol = null; promptAction = null; chips = null
        if (tab == TAB_NOTES) renderQuick() else renderJournal()
    }

    private fun renderTabs() {
        tabsRow.removeAllViews()
        listOf(TAB_NOTES to R.string.journal_tab_notes, TAB_JOURNAL to R.string.journal_tab_journal).forEach { (id, label) ->
            val active = tab == id
            tabsRow.addView(text(getString(label), textSp, if (active) theme.panel else theme.outputText, bold = active).apply {
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                background = if (active) outline(theme.outputText) else outline(theme.panel, bright = false)
                contentDescription = getString(label)
                setOnClickListener { if (tab != id) { saveDraft(); tab = id; selectedQuick = null; editingQuick = null; render() } }
            }, LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) })
        }
        tabsRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val open = quickNotes.count { !it.done }
        val info = if (tab == TAB_NOTES) getString(R.string.journal_quick_count, open, quickNotes.size - open)
            else JournalFormat.label(day) + if (day == today()) " · " + getString(R.string.journal_today) else ""
        tabsRow.addView(text(info, smallSp, dim))
    }

    // region quick notes

    private fun renderQuick() {
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(4)) }
        val ordered = quickNotes.ordered()
        if (ordered.isEmpty()) rows.addView(text(getString(R.string.journal_quick_empty), smallSp, dim).apply {
            setPadding(dp(12), dp(16), dp(12), dp(8))
        })
        ordered.forEach { rows.addView(quickRow(it), LinearLayout.LayoutParams(-1, -2)) }
        if (ordered.isNotEmpty()) rows.addView(rule(getString(R.string.journal_quick_rule)))
        if (NoteStore(this).hasDatabase()) rows.addView(rule(getString(R.string.journal_archive)) { LauncherNotes.openArchive(this) })
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = outline(theme.outputBg)
            setPadding(stroke, stroke, stroke, stroke)
            addView(scroll(rows, toEnd = false), LinearLayout.LayoutParams(-1, 0, 1f))
        }, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(4), dp(8), 0) })

        val selected = quickNotes.firstOrNull { it.id == selectedQuick }
        if (selected != null && editingQuick == null) {
            bottom.addView(keyRow(
                key("▓", if (selected.hidden && selected.id !in revealed) getString(R.string.journal_show) else getString(R.string.journal_hide)) {
                    if (selected.hidden && selected.id !in revealed) revealed.add(selected.id)
                    else { revealed.remove(selected.id); updateQuick(selected.id) { it.hidden = !it.hidden } }
                    render()
                },
                key("✎", getString(R.string.journal_edit)) { editingQuick = selected.id; render() },
                key("★", if (selected.pinned) getString(R.string.journal_unpin) else getString(R.string.journal_pin)) {
                    updateQuick(selected.id) { it.pinned = !it.pinned }; render()
                },
                key("→", getString(R.string.journal_to_journal)) {
                    journal.append(System.currentTimeMillis(), selected.text)
                    quickNotes = quickStore.mutate { list -> list.removeAll { it.id == selected.id } }
                    selectedQuick = null
                    toast(getString(R.string.journal_moved))
                    render()
                },
                key("×", getString(R.string.journal_delete)) {
                    quickNotes = quickStore.mutate { list -> list.removeAll { it.id == selected.id } }
                    selectedQuick = null
                    render()
                }
            ), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }

        val editingNote = quickNotes.firstOrNull { it.id == editingQuick }
        val field = promptField(getString(if (editingNote != null) R.string.journal_quick_edit_hint else R.string.journal_quick_hint), single = true).apply {
            if (editingNote != null) setText(editingNote.text)
            setOnEditorActionListener { _, _, _ -> submitQuick(this); true }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(promptBox(if (editingNote != null) "✎" else "+", field), LinearLayout.LayoutParams(0, -2, 1f))
            val done = quickNotes.count { it.done }
            if (editingNote == null && done > 0) addView(key("×", getString(R.string.journal_clear_done)) {
                quickNotes = quickStore.mutate { list -> list.removeAll { it.done } }
                render()
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(6) })
        }
        bottom.addView(row)
        if (editingNote != null) focus(field)
    }

    private fun quickRow(note: QuickNote): View {
        val selected = note.id == selectedQuick
        val fg = if (selected) theme.outputBg else theme.outputText
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(46)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            if (selected) setBackgroundColor(theme.outputText)
            addView(text(when { note.pinned -> "★"; note.done -> "[x]"; else -> "[ ]" }, textSp,
                if (selected) fg else if (note.done) dim else theme.outputText), LinearLayout.LayoutParams(dp(34), -2))
            val shown = if (note.hidden && note.id !in revealed) "▓▓▓▓" else RedactionFormat.hide(note.text)
            addView(text(shown, textSp, if (note.done && !selected) dim else fg).apply {
                if (note.done) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(text(age(note.createdAt), smallSp, if (selected) fg else dim), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            contentDescription = shown
            setOnClickListener {
                if (selectedQuick != null) selectedQuick = if (selected) null else note.id
                else updateQuick(note.id) { it.done = !it.done }
                render()
            }
            setOnLongClickListener { selectedQuick = note.id; editingQuick = null; hideKeyboard(); render(); true }
        }
    }

    private fun submitQuick(field: EditText) {
        val line = QuickNoteFormat.clean(field.text.toString())
        val editingId = editingQuick
        if (editingId != null) {
            if (line.isEmpty()) quickNotes = quickStore.mutate { list -> list.removeAll { it.id == editingId } }
            else updateQuick(editingId) { it.text = line }
            editingQuick = null
            selectedQuick = null
            hideKeyboard()
            render()
            return
        }
        if (line.isEmpty()) return
        quickNotes = quickStore.add(line) ?: quickNotes
        render()
        prompt?.let(::focus)
    }

    private fun updateQuick(id: String, change: (QuickNote) -> Unit) {
        quickNotes = quickStore.mutate { list -> list.firstOrNull { it.id == id }?.let(change) }
    }

    // endregion

    // region journal

    private fun renderJournal() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = outline(theme.outputBg)
            // Rows and highlights stay inside the pane's border.
            setPadding(stroke, stroke, stroke, stroke)
        }
        content.addView(panel, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(8), dp(4), dp(8), 0) })
        panel.addView(dayBar(), LinearLayout.LayoutParams(-1, dp(40)))
        if (grid) panel.addView(monthGrid(), LinearLayout.LayoutParams(-1, -2))
        panel.addView(View(this).apply { setBackgroundColor(faint) }, LinearLayout.LayoutParams(-1, stroke))
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(6)) }
        journalRows = rows
        journalScroll = scroll(rows, toEnd = false).also { panel.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        fillRows()
        renderPrompt()
    }

    /** Refills the log (or the search results) without touching the prompt, so typing a search keeps the keyboard. */
    private fun fillRows() {
        val rows = journalRows ?: return
        rows.removeAllViews()
        val q = query
        if (q != null) renderResults(rows, q) else renderDay(rows)
        journalScroll?.let { view -> view.post { if (q == null) scrollToEnd(view) else view.scrollTo(0, 0) } }
        promptSymbol?.text = if (q != null) "/" else "›"
        promptAction?.text = getString(when {
            editing != null -> R.string.journal_save
            q != null -> R.string.journal_esc
            else -> R.string.journal_log
        })
        chips?.visibility = if (q == null) View.VISIBLE else View.GONE
    }

    private fun dayBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val atToday = day >= today()
        addView(text("‹", textSp + 2f).apply {
            gravity = Gravity.CENTER; contentDescription = getString(R.string.journal_previous_day)
            setOnClickListener { saveDraft(); day = JournalFormat.shift(day, -1); query = null; render() }
        }, LinearLayout.LayoutParams(dp(48), -1))
        val count = journal.load(day).size
        addView(text(SpannableStringBuilder().apply {
            append(JournalFormat.label(day), StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" · " + resources.getQuantityString(R.plurals.journal_entries, count, count) + if (grid) "  ▴" else "  ▾")
        }, smallSp + 1f).apply {
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.journal_pick_day)
            setOnClickListener { grid = !grid; gridMonth = day.take(7); render() }
        }, LinearLayout.LayoutParams(0, -1, 1f))
        addView(text("›", textSp + 2f, if (atToday) faint else theme.outputText).apply {
            gravity = Gravity.CENTER; contentDescription = getString(R.string.journal_next_day)
            setOnClickListener { if (!atToday) { saveDraft(); day = JournalFormat.shift(day, 1); query = null; render() } }
        }, LinearLayout.LayoutParams(dp(48), -1))
    }

    /** The month around the shown day; days with entries are filled, the shown day is inverted. */
    private fun monthGrid(): View {
        val month = gridMonth ?: day.take(7)
        val written = journal.days().toSet()
        val cal = Calendar.getInstance().apply {
            time = SimpleDateFormat("yyyy-MM", Locale.US).parse(month) ?: java.util.Date()
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val lead = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
        val length = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(6), dp(10), dp(8)) }
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(text("‹", textSp).apply { gravity = Gravity.CENTER; setOnClickListener { gridMonth = shiftMonth(month, -1); render() } },
                LinearLayout.LayoutParams(dp(40), dp(32)))
            addView(text(SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(cal.time).lowercase(Locale.getDefault()), smallSp, dim).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, dp(32), 1f))
            addView(text("›", textSp).apply { gravity = Gravity.CENTER; setOnClickListener { gridMonth = shiftMonth(month, 1); render() } },
                LinearLayout.LayoutParams(dp(40), dp(32)))
        })
        var row: LinearLayout? = null
        for (cell in 0 until ((lead + length + 6) / 7) * 7) {
            if (cell % 7 == 0) row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }.also {
                box.addView(it, LinearLayout.LayoutParams(-1, dp(30)).apply { topMargin = dp(3) })
            }
            val number = cell - lead + 1
            val view = if (number in 1..length) {
                val id = "$month-%02d".format(Locale.US, number)
                val shown = id == day
                val has = id in written
                text(number.toString(), smallSp, when { shown -> theme.outputBg; has -> theme.outputText; else -> faint }, bold = shown).apply {
                    gravity = Gravity.CENTER
                    when {
                        shown -> setBackgroundColor(theme.outputText)
                        has -> setBackgroundColor(ColorUtils.blendARGB(theme.outputBg, theme.outputText, 0.22f))
                    }
                    if (id <= today()) setOnClickListener { saveDraft(); day = id; grid = false; query = null; render() }
                }
            } else View(this)
            row!!.addView(view, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = dp(3) })
        }
        return box
    }

    private fun shiftMonth(month: String, by: Int): String = Calendar.getInstance().run {
        time = SimpleDateFormat("yyyy-MM", Locale.US).parse(month) ?: java.util.Date()
        add(Calendar.MONTH, by)
        SimpleDateFormat("yyyy-MM", Locale.US).format(time)
    }

    private fun renderDay(rows: LinearLayout) {
        // The end of the day before sits above, so the log reads on like terminal scrollback.
        val previous = journal.days().lastOrNull { it < day }
        if (previous != null) {
            val count = journal.load(previous).size
            rows.addView(rule(JournalFormat.label(previous) + " · " + resources.getQuantityString(R.plurals.journal_entries, count, count) + " ↑") {
                saveDraft(); day = previous; render()
            })
        }
        val entries = journal.load(day)
        if (entries.isEmpty()) rows.addView(text(getString(if (day == today()) R.string.journal_empty_today else R.string.journal_empty_day), smallSp, dim).apply {
            setPadding(dp(12), dp(12), dp(12), dp(6))
        })
        entries.forEach { rows.addView(entryRow(it, it.time, null)) }
        if (editing == null && day == today()) rows.addView(draftRow())
    }

    private fun renderResults(rows: LinearLayout, q: String) {
        val tagOnly = q.startsWith("#")
        val hits = if (tagOnly) journal.days().reversed().flatMap { d ->
            journal.load(d).filter { e -> JournalFormat.tags(e.text).any { it.equals(q.removePrefix("#"), true) } }.reversed()
        } else journal.search(q)
        if (q.isBlank()) {
            rows.addView(text(getString(R.string.journal_find_hint), smallSp, dim).apply { setPadding(dp(12), dp(8), dp(12), dp(4)) })
            return
        }
        rows.addView(text(resources.getQuantityString(R.plurals.journal_matches, hits.size, hits.size, q), smallSp, theme.outputText).apply {
            setPadding(dp(12), dp(8), dp(12), dp(4))
        })
        var lastDay: String? = null
        hits.forEach { entry ->
            if (entry.day != lastDay) {
                lastDay = entry.day
                rows.addView(text(JournalFormat.label(entry.day) + if (entry.day == today()) " · " + getString(R.string.journal_today) else "", smallSp, dim).apply {
                    setPadding(dp(12), dp(12), dp(12), dp(2))
                    setOnClickListener { day = entry.day; query = null; draft = ""; render() }
                })
            }
            rows.addView(entryRow(entry, entry.time, q.removePrefix("#")))
        }
    }

    /** A new entry as it will look once logged, shown while it's being typed. */
    private fun draftRow(): View {
        val body = text(styled(JournalEntry(day, "", draft), null, live = true), textSp).apply { setLineSpacing(0f, 1.15f) }
        previewText = body
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setPadding(dp(10), dp(6), dp(12), dp(6))
            setBackgroundColor(ColorUtils.blendARGB(theme.outputBg, theme.outputText, 0.1f))
            addView(text(getString(R.string.journal_age_now), smallSp, theme.outputText).apply { setPadding(0, dp(2), 0, 0) },
                LinearLayout.LayoutParams(dp(48), -2))
            addView(body, LinearLayout.LayoutParams(0, -2, 1f))
            visibility = if (draft.isBlank()) View.GONE else View.VISIBLE
            previewRow = this
        }
    }

    /** Scrolls to the bottom without fullScroll, which also moves focus off the prompt. */
    private fun scrollToEnd(view: android.widget.ScrollView) {
        view.scrollTo(0, maxOf(0, (view.getChildAt(0)?.height ?: 0) - view.height + view.paddingBottom))
    }

    private fun livePreview(value: String) {
        val view = previewText ?: return
        val entry = editing
        if (entry != null) view.text = styled(entry.copy(text = value), null, live = true)
        else {
            view.text = styled(JournalEntry(day, "", value), null, live = true)
            val wasHidden = previewRow?.visibility != View.VISIBLE
            previewRow?.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
            if (wasHidden || value.endsWith("\n")) journalScroll?.let { scroll -> scroll.post { scrollToEnd(scroll) } }
        }
    }

    private fun entryRow(entry: JournalEntry, time: String, highlight: String?): View {
        val active = editing == entry
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Times sit at the top of their entry; baseline alignment drops them below an image on the first line.
            isBaselineAligned = false
            setPadding(dp(10), dp(6), dp(12), dp(6))
            if (active) {
                setBackgroundColor(ColorUtils.blendARGB(theme.outputBg, theme.outputText, 0.1f))
            }
            addView(text(time, smallSp, if (active) theme.outputText else dim).apply { setPadding(0, dp(2), 0, 0) },
                LinearLayout.LayoutParams(dp(48), -2))
            addView(text(styled(entry, highlight), textSp).apply {
                if (active) previewText = this
                setLineSpacing(0f, 1.15f)
                // Hold to select text like any terminal pane; links, #tags and [ ] stay tappable.
                setTextIsSelectable(true)
                Linkify.addLinks(this, Linkify.WEB_URLS)
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(theme.outputText)
                highlightColor = ColorUtils.setAlphaComponent(theme.outputText, 90)
                setOnClickListener { if (!hasSelection()) startEdit(entry) }
                customSelectionActionModeCallback = selectionActions(this, entry)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            setOnClickListener { startEdit(entry) }
            setOnLongClickListener { copy(entry.text); true }
        }
    }

    /** Lists show as [ ]/[x] you can tap, #tags open their entries, hidden text shows as ▓. */
    private fun styled(entry: JournalEntry, highlight: String?, live: Boolean = false): CharSequence {
        val out = SpannableStringBuilder()
        val lines = tidy(entry.text).lines()
        lines.forEachIndexed { index, raw ->
            val line = if (entry in unlocked) runCatching { redactions.decryptMarkdown(raw) }.getOrDefault(raw)
                else RedactionFormat.token.replace(raw) { "▓".repeat(it.groupValues[1].toInt().coerceIn(4, 24)) }
            val check = Regex("^(\\s*)- \\[([ xX])] (.*)$").find(line)
            val image = JournalImages.line.find(line)
            if (image != null) {
                val start = out.length
                val drawable = thumbnail(image.groupValues[2])
                if (drawable == null) {
                    out.append(getString(R.string.journal_image_missing))
                    out.setSpan(ForegroundColorSpan(dim), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    out.append("\uFFFC")
                    out.setSpan(android.text.style.ImageSpan(drawable, android.text.style.ImageSpan.ALIGN_BOTTOM), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) { openImage(image.groupValues[2]) }
                        override fun updateDrawState(ds: TextPaint) {}
                    }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else if (check != null) {
                val checked = check.groupValues[2] != " "
                val start = out.length
                out.append(check.groupValues[1]).append(if (checked) "[x] " else "[ ] ")
                if (!live) out.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { toggleCheck(entry, index) }
                    override fun updateDrawState(ds: TextPaint) { ds.color = theme.outputText; ds.isUnderlineText = false }
                }, start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val bodyStart = out.length
                out.append(inline(check.groupValues[3]))
                if (checked) {
                    out.setSpan(StrikethroughSpan(), bodyStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(ForegroundColorSpan(dim), bodyStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                val quote = Regex("^> ?(.*)$").find(line)
                val heading = Regex("^(#{1,6})[ \\t]+(.*)$").find(line)
                val bullet = Regex("^(\\s*)[-*+] (.*)$").find(line)
                val start = out.length
                when {
                    heading != null -> {
                        out.append(inline(heading.groupValues[2]))
                        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val level = heading.groupValues[1].length
                        out.setSpan(android.text.style.RelativeSizeSpan(when (level) { 1 -> 1.4f; 2 -> 1.25f; 3 -> 1.12f; else -> 1.05f }),
                            start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    bullet != null -> out.append(bullet.groupValues[1]).append("• ").append(inline(bullet.groupValues[2]))
                    else -> out.append(inline(quote?.groupValues?.get(1) ?: line))
                }
                if (quote != null) {
                    out.insert(start, "│ ")
                    out.setSpan(ForegroundColorSpan(dim), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            if (index < lines.lastIndex) out.append('\n')
        }
        val plain = out.toString()
        // Hidden text: tap the ▓ to unlock it with the screen lock.
        Regex("▓+").findAll(plain).forEach { match ->
            if (!live) out.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { reveal(entry) }
                override fun updateDrawState(ds: TextPaint) { ds.color = theme.outputText; ds.isUnderlineText = false }
            }, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        JournalFormat.tag.findAll(plain).forEach { match ->
            val tag = match.value
            if (!live) out.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { query = tag; draft = ""; editing = null; render() }
                override fun updateDrawState(ds: TextPaint) { ds.color = theme.outputText; ds.isUnderlineText = true }
            }, match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (!highlight.isNullOrBlank()) {
            var at = plain.indexOf(highlight, ignoreCase = true)
            while (at >= 0) {
                out.setSpan(android.text.style.BackgroundColorSpan(theme.outputText), at, at + highlight.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(ForegroundColorSpan(theme.outputBg), at, at + highlight.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                at = plain.indexOf(highlight, at + highlight.length, ignoreCase = true)
            }
        }
        return out
    }

    /** **bold**, *italic*, ~~strike~~, `code` and [text](url) show styled, without their markers. */
    private fun inline(line: String): CharSequence {
        val out = SpannableStringBuilder(line)
        fun pass(regex: Regex, span: (MatchResult) -> Any) {
            var match = regex.find(out)
            while (match != null) {
                val inner = match.groupValues[1]
                val start = match.range.first
                val what = span(match)
                out.replace(start, match.range.last + 1, inner)
                out.setSpan(what, start, start + inner.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                match = regex.find(out, start + inner.length)
            }
        }
        pass(Regex("\\[([^\\]]+)]\\((\\S+)\\)")) { android.text.style.URLSpan(it.groupValues[2]) }
        pass(Regex("`([^`]+)`")) { android.text.style.BackgroundColorSpan(ColorUtils.blendARGB(theme.outputBg, theme.outputText, 0.16f)) }
        pass(Regex("\\*\\*(.+?)\\*\\*")) { StyleSpan(Typeface.BOLD) }
        pass(Regex("~~(.+?)~~")) { StrikethroughSpan() }
        pass(Regex("<u>(.+?)</u>")) { android.text.style.UnderlineSpan() }
        pass(Regex("(?<![*\\w])[*_](?![*\\s])(.+?)(?<![*\\s])[*_](?![*\\w])")) { StyleSpan(Typeface.ITALIC) }
        return out
    }

    /** Adds journal actions to the system text selection menu of an entry. */
    private fun selectionActions(view: TextView, entry: JournalEntry) = object : android.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
            menu.add(0, 3, 0, getString(R.string.journal_selection_hide))
            menu.add(0, 1, 1, getString(R.string.journal_selection_note))
            menu.add(0, 2, 2, getString(R.string.journal_selection_find))
            return true
        }
        override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
        override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
            val selected = view.text.subSequence(view.selectionStart.coerceAtLeast(0), view.selectionEnd.coerceAtLeast(0)).toString().trim()
            when (item.itemId) {
                1 -> if (selected.isNotEmpty()) { quickNotes = quickStore.add(selected) ?: quickNotes; toast(getString(R.string.journal_selection_noted)) }
                2 -> if (selected.isNotEmpty()) { query = selected; draft = ""; editing = null; render() }
                3 -> if (selected.isNotEmpty()) redactInEntry(entry, selected)
                else -> return false
            }
            mode.finish()
            return true
        }
        override fun onDestroyActionMode(mode: android.view.ActionMode) {}
    }

    private fun wrap(field: EditText, open: String, close: String) {
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val text = field.text
        val selected = text.substring(start, end)
        text.replace(start, end, open + selected + close)
        if (close.startsWith("](")) field.setSelection(start + open.length + selected.length + 2, start + open.length + selected.length + close.length - 1)
        else field.setSelection(start + open.length, start + open.length + selected.length)
    }

    /** Replaces the selection with encrypted text that shows as ▓ until unlocked, as the old library's redaction did. */
    private fun hideSelection(field: EditText) {
        val start = minOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val end = maxOf(field.selectionStart, field.selectionEnd).coerceAtLeast(0)
        val selected = field.text.substring(start, end)
        if (selected.isBlank()) return
        encrypted(selected) { hidden ->
            // The text may have changed while the lock screen was up; only replace what was selected.
            formatPopup?.dismiss()
            if (end > field.text.length || field.text.substring(start, end) != selected) return@encrypted
            if (field is MarkdownEditText) field.replaceRange(start, end, hidden)
            else {
                field.text.replace(start, end, hidden)
                field.setSelection((start + hidden.length).coerceAtMost(field.text.length))
            }
        }
    }

    /**
     * Encrypts [selected] for a redaction. The key only works shortly after the screen lock is confirmed, so when
     * it asks for that, confirm it (biometrics or screen lock) and try again.
     */
    private fun encrypted(selected: String, then: (String) -> Unit) {
        // Redactions are tied to the screen lock; without one there is no key to make.
        if (!(getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure) {
            toast(getString(R.string.notes_rememberactivity_set_a_device_pin_or_biometric_first_929bc)); return
        }
        fun attempt() = runCatching { RedactionFormat.manual(selected, redactions::encrypt) }
        attempt().onSuccess(then).onFailure { error ->
            if (generateSequence(error) { it.cause }.none { it is android.security.keystore.UserNotAuthenticatedException }) {
                toast(getString(R.string.journal_hide_failed)); return
            }
            authenticate(getString(R.string.notes_rememberactivity_redact_selected_text_bb190)) {
                attempt().onSuccess(then).onFailure { toast(getString(R.string.journal_hide_failed)) }
            }
        }
    }

    /**
     * Redacts text selected in a saved entry. The selection is matched back to the entry's Markdown; text whose
     * shown form differs from the source (styled or already hidden) has to be redacted while editing instead.
     */
    private fun redactInEntry(entry: JournalEntry, selected: String) {
        if (entry in unlocked || '▓' in selected) { toast(getString(R.string.journal_redact_unavailable)); return }
        val at = entry.text.indexOf(selected)
        if (at < 0) { toast(getString(R.string.journal_redact_unavailable)); return }
        encrypted(selected) { hidden ->
            journal.replace(entry, entry.text.replaceRange(at, at + selected.length, hidden))
            render()
        }
    }

    /** Draws an encrypted passage in the prompt as a solid bar the width of the hidden text. */
    private class HiddenSpan(private val characters: Int) : android.text.style.ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            (paint.measureText("M") * characters.coerceIn(4, 24)).roundToInt()

        override fun draw(canvas: android.graphics.Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val previous = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, y + paint.fontMetrics.ascent * .88f, x + getSize(paint, text, start, end, null), y + paint.fontMetrics.descent * .2f, paint)
            paint.style = previous
        }
    }

    private fun markHidden(value: android.text.Editable) {
        value.getSpans(0, value.length, HiddenSpan::class.java).forEach(value::removeSpan)
        RedactionFormat.token.findAll(value).forEach {
            value.setSpan(HiddenSpan(it.groupValues[1].toInt()), it.range.first, it.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun reveal(entry: JournalEntry) {
        if (entry in unlocked) { unlocked.remove(entry); render(); return }
        authenticate(getString(R.string.notes_rememberactivity_reveal_redacted_text_9dce3)) { unlocked.add(entry); render() }
    }

    private fun authenticate(title: String, success: () -> Unit) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!keyguard.isDeviceSecure) {
            toast(getString(R.string.notes_rememberactivity_set_a_device_pin_or_biometric_first_929bc)); return
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(title)
                .setSubtitle(getString(R.string.notes_rememberactivity_use_biometrics_or_screen_lock_5c36d))
                .setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
                .authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = success()
                })
        } else {
            pendingAuthentication = success
            @Suppress("DEPRECATION")
            startActivityForResult(keyguard.createConfirmDeviceCredentialIntent(title,
                getString(R.string.notes_rememberactivity_unlock_redacted_text_1039f)), 43)
        }
    }

    @Deprecated("Screen-lock confirmation before API 30")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 43) {
            val action = pendingAuthentication
            pendingAuthentication = null
            if (resultCode == RESULT_OK) action?.invoke()
        }
    }


    /** A thumbnail as wide as the log's text column, cached by path. */
    private fun thumbnail(path: String): android.graphics.drawable.Drawable? {
        thumbnails.get(path)?.let { return it }
        val file = JournalImages.file(this, path) ?: return null
        val maxWidth = resources.displayMetrics.widthPixels - dp(110)
        val maxHeight = (resources.displayMetrics.heightPixels * 0.45f).roundToInt()
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height, 1f)
        return android.graphics.drawable.BitmapDrawable(resources, bitmap).apply {
            setBounds(0, 0, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
        }.also { thumbnails.put(path, it) }
    }

    /** Opens an attached image full screen in the phone's viewer, which can also share it. */
    private fun openImage(path: String) {
        val file = JournalImages.file(this, path) ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.onFailure { toast(getString(R.string.journal_no_viewer)) }
    }

    private fun camera() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val dir = java.io.File(cacheDir, "journal-camera").apply { mkdirs() }
        val file = java.io.File(dir, "${java.util.UUID.randomUUID()}.jpg")
        cameraFile = file
        runCatching { takePhoto.launch(androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", file)) }
            .onFailure { cameraFile = null; toast(getString(R.string.journal_no_camera)) }
    }

    /** Imports images off the main thread, then puts each on its own line at the prompt's cursor. */
    private fun attach(uris: List<android.net.Uri>, done: () -> Unit = {}) {
        if (uris.isEmpty()) return
        val target = today()
        Thread {
            val paths = uris.mapNotNull { JournalImages.import(this, it, target) }
            done()
            runOnUiThread {
                if (paths.size < uris.size) toast(getString(R.string.journal_image_failed))
                val field = prompt ?: return@runOnUiThread
                paths.forEach { path ->
                    val text = field.text
                    val at = field.selectionStart.coerceIn(0, text.length)
                    val before = if (at > 0 && text[at - 1] != '\n') "\n" else ""
                    val after = if (at < text.length && text[at] == '\n') "" else "\n"
                    text.insert(at, "$before![]($path)$after")
                }
                focus(field)
            }
        }.start()
    }

    /** Drops images no saved entry or open draft uses. */
    private fun pruneImages() {
        val keep = JournalImages.paths(draft) + JournalImages.paths(editDraft.orEmpty()) + JournalImages.paths(prompt?.text?.toString().orEmpty())
        Thread { runCatching { JournalImages.prune(this, journal, keep) } }.start()
    }

    /** At most one blank line in a row, none at the ends. */
    private fun tidy(value: String) = value.trim('\n').replace(Regex("\n[ \t]*(\n[ \t]*)+\n"), "\n\n")

    private fun toggleCheck(entry: JournalEntry, line: Int) {
        val lines = tidy(entry.text).lines().toMutableList()
        lines[line] = Regex("- \\[([ xX])]").let { box ->
            box.find(lines[line])?.let { hit ->
                lines[line].replaceRange(hit.range, if (hit.groupValues[1] == " ") "- [x]" else "- [ ]")
            } ?: lines[line]
        }
        journal.replace(entry, lines.joinToString("\n"))
        render()
    }

    private fun renderPrompt() {
        val entry = editing
        // Chips insert syntax so nothing has to be remembered; the prompt also understands it typed.
        // One line of chips that scrolls sideways on narrow screens rather than wrapping a label.
        bottom.addView(android.widget.HorizontalScrollView(this).apply {
            chips = this
            visibility = if (query == null) View.VISIBLE else View.GONE
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(this@JournalActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            val actions: List<Pair<String, () -> Unit>> = if (attachMode) listOf(
                getString(R.string.journal_attach_photo) to { attachMode = false; saveDraft(); pickImages.launch(
                    androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                getString(R.string.journal_attach_camera) to { attachMode = false; saveDraft(); camera() },
                "×" to { attachMode = false; render() }
            ) else listOf("+ attach" to { saveDraft(); attachMode = true; render() }, "/find" to { insert("/find ") }, "#tag" to { insert("#") },
                "[ ] list" to { insertLine("- [ ] ") }, ":date" to { grid = true; gridMonth = day.take(7); render() })
            actions.forEach { (label, action) ->
                addView(text(label, smallSp, dim).apply {
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    setPadding(dp(8), 0, dp(8), 0)
                    background = outline(theme.panel, bright = false)
                    setOnClickListener { action() }
                }, LinearLayout.LayoutParams(-2, dp(32)).apply { marginEnd = dp(6) })
            }
            })
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        if (entry != null) bottom.addView(text(getString(R.string.journal_editing, JournalFormat.label(entry.day), entry.time), smallSp, dim).apply {
            setPadding(dp(4), 0, dp(4), dp(6))
        })

        val field = promptField(getString(when {
            entry != null -> R.string.journal_edit_hint
            day != today() -> R.string.journal_prompt_hint_past
            else -> R.string.journal_prompt_hint
        }), single = false).apply {
            setText(if (entry != null) editDraft ?: tidy(entry.text) else draft)
            if (this !is MarkdownEditText) markHidden(text)
            setSelection(text.length)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    if (prompt !is MarkdownEditText) s?.let(::markHidden)
                    if (query == null) livePreview(s?.toString().orEmpty())
                    if (editing != null) return
                    val value = s?.toString().orEmpty()
                    val search = searchQuery(value)
                    if (search != query) { draft = value; query = search; fillRows() }
                }
            })
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(promptBox(if (query != null) "/" else "›", field), LinearLayout.LayoutParams(0, -2, 1f))
            val label = when {
                entry != null -> getString(R.string.journal_save)
                query != null -> getString(R.string.journal_esc)
                else -> getString(R.string.journal_log)
            }
            addView(text(label, smallSp, theme.panel, bold = true).apply {
                promptAction = this
                gravity = Gravity.CENTER
                setPadding(dp(12), 0, dp(12), 0)
                setBackgroundColor(theme.outputText)
                setOnClickListener {
                    when {
                        editing != null -> saveEdit(field.text.toString())
                        query != null -> { query = null; draft = ""; field.setText(""); fillRows() }
                        else -> log(field.text.toString())
                    }
                }
            }, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(6) })
            if (entry != null) addView(text("×", textSp, theme.outputText).apply {
                gravity = Gravity.CENTER
                contentDescription = getString(R.string.journal_cancel)
                background = outline(theme.panel, bright = false)
                setOnClickListener { editing = null; editDraft = null; hideKeyboard(); pruneImages(); render() }
            }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginStart = dp(6) })
        }
        bottom.addView(row)
        if (focusOnWindow && hasWindowFocus()) { focusOnWindow = false; focus(field) }
        else if (entry != null || query != null || draft.isNotEmpty()) focus(field)
    }

    /** `/find words` or a lone `#tag` turns the prompt into a search. */
    private fun searchQuery(value: String): String? {
        val trimmed = value.trim()
        return when {
            value.startsWith("/find ") -> value.removePrefix("/find ").trim()
            Regex("^#[\\p{L}\\p{N}_-]+$").matches(trimmed) && !value.endsWith(" ") && !value.contains('\n') -> trimmed
            else -> null
        }
    }

    private fun log(raw: String) {
        val value = raw.trimEnd()
        if (value.isBlank()) return
        // :today, :yesterday or :2026-10-29 jumps instead of writing.
        Regex("^:(today|yesterday|\\d{4}-\\d{2}-\\d{2})$").find(value.trim())?.let { jump ->
            day = when (jump.groupValues[1]) {
                "today" -> today()
                "yesterday" -> JournalFormat.shift(today(), -1)
                else -> jump.groupValues[1].takeIf { it <= today() } ?: today()
            }
            draft = ""
            render()
            return
        }
        journal.append(System.currentTimeMillis(), tidy(value))
        draft = ""
        day = today()
        render()
        prompt?.let(::focus)
    }

    private fun saveEdit(value: String) {
        val entry = editing ?: return
        journal.replace(entry, tidy(value).ifBlank { null })
        editing = null
        editDraft = null
        pruneImages()
        hideKeyboard()
        render()
    }

    private fun startEdit(entry: JournalEntry) {
        saveDraft()
        editing = entry
        editDraft = null
        render()
    }

    private fun insert(value: String) {
        val field = prompt ?: return
        val at = field.selectionStart.coerceAtLeast(0)
        field.text.insert(at, value)
        focus(field)
    }

    private fun insertLine(prefix: String) {
        val field = prompt ?: return
        val text = field.text
        val at = field.selectionStart.coerceAtLeast(0)
        val lineStart = text.lastIndexOf('\n', (at - 1).coerceAtLeast(0)).let { if (at == 0) 0 else it + 1 }
        if (text.isNotEmpty() && at > 0 && text.substring(lineStart, at).isNotBlank()) text.insert(at, "\n$prefix")
        else text.insert(lineStart, prefix)
        focus(field)
    }

    private fun saveDraft() {
        if (editing != null) prompt?.let { editDraft = it.text.toString() }
        else if (query == null) prompt?.let { draft = it.text.toString() }
    }

    // endregion

    /**
     * The prompt. Writing an entry uses the notes editor itself: Markdown shows styled as it's typed, and
     * selecting text brings up the same formatting grid as in notes.
     */
    private fun promptField(hint: String, single: Boolean): EditText = (if (single) EditText(this) else MarkdownEditText(this).also { editor ->
        editor.imageFile = { JournalImages.file(this, it) }
        val popup = FormatPopup(this, { theme }, editor, onLink = { wrap(editor, "[", "](https://)") }, onRedact = { hideSelection(editor) })
        formatPopup = popup
        editor.onSelection = popup::onSelection
    }).apply {
        this.hint = hint
        background = null
        textSize = textSp
        typeface = face
        setTextColor(theme.inputText)
        setHintTextColor(ColorUtils.setAlphaComponent(theme.inputText, 130))
        setPadding(0, dp(10), 0, dp(10))
        if (single) {
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
        } else {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 6
        }
        prompt = this
    }

    private fun promptBox(symbol: String, field: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(44)
        background = outline(theme.inputBg)
        setPadding(dp(10), 0, dp(10), 0)
        addView(text(symbol, textSp, theme.inputText, bold = true).also { promptSymbol = it }, LinearLayout.LayoutParams(-2, -2).apply {
            marginEnd = dp(8); gravity = Gravity.TOP; topMargin = dp(12)
        })
        addView(field, LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun focus(field: EditText) {
        field.post {
            field.requestFocus()
            field.setSelection(field.text.length)
            androidx.core.view.WindowCompat.getInsetsController(window, field).show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && focusOnWindow) {
            focusOnWindow = false
            prompt?.let(::focus)
        }
    }

    private fun hideKeyboard() {
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(content.windowToken, 0)
    }

    private fun copy(value: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("journal", RedactionFormat.hide(value)))
        toast(getString(R.string.journal_copied))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun age(time: Long): String {
        val minutes = (System.currentTimeMillis() - time) / 60000
        return when {
            minutes < 1 -> getString(R.string.journal_age_now)
            minutes < 60 -> "${minutes}m"
            minutes < 60 * 24 -> "${minutes / 60}h"
            else -> "${minutes / (60 * 24)}d"
        }
    }

    private fun today() = JournalFormat.day(System.currentTimeMillis())

    private fun reload() {
        quickNotes = runCatching { quickStore.load() }.getOrElse { quickNotes }
        render()
    }

    override fun onResume() {
        super.onResume()
        LauncherSystemUi.applyFullscreen(this)
        reload()
    }

    override fun onPause() {
        super.onPause()
        formatPopup?.dismiss()
        if (unlocked.isNotEmpty()) { unlocked.clear(); render() }
        saveDraft()
        if (::windowPanel.isInitialized) windowPanel.persist()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        saveDraft()
        outState.putString("tab", tab)
        outState.putString("day", day)
        outState.putString("draft", draft)
        editing?.let { outState.putString("editing_day", it.day); outState.putString("editing_time", it.time) }
        editDraft?.let { outState.putString("edit_draft", it) }
        if (::windowPanel.isInitialized) windowPanel.save(outState)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
