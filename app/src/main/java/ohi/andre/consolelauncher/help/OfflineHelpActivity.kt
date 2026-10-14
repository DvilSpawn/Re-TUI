package ohi.andre.consolelauncher.help

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.descendants
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

class OfflineHelpActivity : LocalizedComponentActivity() {
    companion object {
        fun open(context: Context, topicId: String? = null) {
            val intent = Intent(context, OfflineHelpActivity::class.java).putExtra("topic", topicId)
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
    private var category: String? = null
    private var article: String? = null
    private var page = 0
    private var rootPage = 0
    private lateinit var windowPanel: HelpWindowPanel
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var root: View
    private val reference by lazy { OfflineHelpReference.commands(this) }
    private val actions = mutableMapOf<String, () -> Unit>()
    private val readerPrefs by lazy { getSharedPreferences("offline_help_reader", Context.MODE_PRIVATE) }
    private var pendingScroll: Int? = null
    private var renderGeneration = 0

    private fun savedReader(): Bundle? = if (!readerPrefs.contains("page")) null else Bundle().apply {
        putString("category", readerPrefs.getString("category", null)); putString("article", readerPrefs.getString("article", null))
        for (key in listOf("page", "root_page", "start", "end", "scroll_y")) putInt(key, readerPrefs.getInt(key, 0))
        putString("draft", readerPrefs.getString("draft", ""))
    }
    private fun persistReader() {
        readerPrefs.edit().putString("category", category).putString("article", article)
            .putInt("page", page).putInt("root_page", rootPage).putString("draft", input.text.toString())
            .putInt("start", input.selectionStart).putInt("end", input.selectionEnd)
            .putInt("scroll_y", pendingScroll ?: scroll.scrollY).apply()
    }
    private fun closeHelp(consumeChoice: Boolean = false) {
        if (consumeChoice) input.setText("")
        persistReader(); finish()
    }

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this).apply { isClickable = true }
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        root = layoutInflater.inflate(R.layout.offline_help_surface, host, false)
        val viewport = FrameLayout(this)
        host.addView(viewport, ViewGroup.LayoutParams(-1, -1))
        windowPanel = HelpWindowPanel(viewport, root, state, floatingInLandscape = false)
        input = root.findViewById(R.id.offline_help_input)
        output = root.findViewById(R.id.offline_help_output)
        scroll = root.findViewById(R.id.offline_help_scroll)
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        listOf(output, input, root.findViewById<TextView>(R.id.offline_help_prompt), root.findViewById<TextView>(R.id.offline_help_header)).forEach {
            it.typeface = Tuils.getTypeface(this); it.setTextColor(textColor)
            it.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
        }
        output.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
        output.setLineSpacing(Tuils.dpToPx(this, 3).toFloat(), 1f)
        input.typeface = Tuils.getTypeface(this)
        input.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
        input.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
        Tuils.setCursorDrawableColor(input, ThemeColorResolver.color(Theme.cursor_color))
        output.setLinkTextColor(textColor)
        root.findViewById<View>(R.id.offline_help_panel).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius(),
            ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders(), target = ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.offline_help_panel), root.findViewById(R.id.offline_help_header))
        input.showSoftInputOnFocus = false
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) submit()
                true
            } else false
        }
        val keys = root.findViewById<GridLayout>(R.id.offline_help_keys)
        val bottomRow = BottomCornerRow(this)
        listOf("1", "2", "3", "4", "5", "⌫", "6", "7", "8", "9", "0", "↵").forEachIndexed { index, label ->
            val key = Button(this).apply {
                text = label; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0); setTextColor(textColor)
                typeface = Tuils.getTypeface(this@OfflineHelpActivity)
                textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
                background = null
                if (label == "↵") { id = R.id.offline_help_enter; contentDescription = getString(R.string.offline_help_enter) }
                if (label == "⌫") contentDescription = getString(R.string.offline_help_backspace)
                setOnClickListener {
                    val start = minOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
                    val end = maxOf(input.selectionStart, input.selectionEnd).coerceAtLeast(0)
                    when (label) {
                        "↵" -> submit()
                        "⌫" -> if (start != end) input.text.delete(start, end) else if (start > 0) input.text.delete(start - 1, start)
                        else -> input.text.replace(start, end, label)
                    }
                    input.requestFocus()
                }
            }
            if (index >= 6) bottomRow.addKey(key) else keys.addView(key, GridLayout.LayoutParams().apply {
                width = 0; height = Tuils.dpToPx(this@OfflineHelpActivity, 48)
                rowSpec = GridLayout.spec(index / 6)
                columnSpec = GridLayout.spec(index % 6, 1f)
            })
        }
        keys.addView(bottomRow, GridLayout.LayoutParams().apply {
            width = 0; height = Tuils.dpToPx(this@OfflineHelpActivity, 48)
            rowSpec = GridLayout.spec(1)
            columnSpec = GridLayout.spec(0, 6, 1f)
        })
        windowPanel.reserveBottomCorners(bottomRow)
        val requestedTopic = intent.getStringExtra("topic")
        val bookmark = savedReader()
        val bookmarkedTopic = bookmark?.getString("article") ?: bookmark?.getString("category")
        val readerState = state ?: if (requestedTopic.isNullOrEmpty() || requestedTopic == bookmarkedTopic) bookmark else null
        if (readerState != null) {
            category = readerState.getString("category")?.takeIf { id -> OfflineHelpCatalog.find(id)?.let { it.category == null } == true }
            article = readerState.getString("article")?.takeIf { category != null && it in ids() }
            page = readerState.getInt("page"); rootPage = readerState.getInt("root_page")
            pendingScroll = readerState.getInt("scroll_y").coerceAtLeast(0)
        } else {
            val topic = OfflineHelpCatalog.find(intent.getStringExtra("topic").orEmpty())
            category = topic?.category ?: topic?.id
            article = topic?.takeIf { it.category != null }?.id
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeHelp()
        })
        render(preserveScroll = true)
        if (readerState != null) {
            input.setText(readerState.getString("draft", ""))
            input.setSelection(readerState.getInt("start").coerceIn(0, input.length()), readerState.getInt("end").coerceIn(0, input.length()))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(screen)
        input.requestFocus()
    }
    override fun onResume() {
        super.onResume()
        if (!::output.isInitialized) return
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        listOf(output, input, root.findViewById<TextView>(R.id.offline_help_prompt), root.findViewById<TextView>(R.id.offline_help_header)).forEach {
            it.typeface = Tuils.getTypeface(this); it.setTextColor(textColor)
            it.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
        }
        input.typeface = Tuils.getTypeface(this)
        input.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
        input.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
        Tuils.setCursorDrawableColor(input, ThemeColorResolver.color(Theme.cursor_color))
        output.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
        output.setLinkTextColor(textColor)
        root.findViewById<View>(R.id.offline_help_panel).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius(),
            ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders(), target = ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS)
        val keys = root.findViewById<GridLayout>(R.id.offline_help_keys)
        keys.descendants.filterIsInstance<Button>().forEach { key ->
            key.setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
            key.typeface = Tuils.getTypeface(this@OfflineHelpActivity)
            key.textSize = ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp()
            key.background = null
        }
        root.findViewById<TextView>(R.id.offline_help_header).apply {
            includeFontPadding = false
            setTypeface(Tuils.getTypeface(this@OfflineHelpActivity), Typeface.BOLD)
            textSize = ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputHeaderTextSize().toFloat()
            background = TerminalBorderRuntime.tabDrawable(this@OfflineHelpActivity,
                ThemeColorResolver.color(Theme.workspace_header_background_color), ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS)
        }
        TerminalBorderRuntime.bind(root.findViewById(R.id.offline_help_panel), root.findViewById(R.id.offline_help_header))
        root.findViewById<View>(R.id.offline_help_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color),
            ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius(),
            ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders(), target = ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS)
        windowPanel.refreshTheme()
        render(preserveScroll = true)
    }

    private fun ids(): List<String> = when (category) {
        null -> OfflineHelpCatalog.topics.filter { it.category == null }.map { it.id }
        "reference" -> OfflineHelpCatalog.topics.filter { it.category == category }.map { it.id } +
            reference.map { "command:${it.javaClass.simpleName}" }
        else -> OfflineHelpCatalog.topics.filter { it.category == category }.map { it.id }
    }
    private fun title(id: String): String = if (id.startsWith("command:")) id.removePrefix("command:") else getString(OfflineHelpCatalog.find(id)!!.title)
    private fun submit() {
        val action = actions[input.text.toString().trim()]
        if (action == null) input.error = getString(R.string.offline_help_invalid_choice)
        else { input.error = null; action() }
    }
    private fun render(preserveScroll: Boolean = false) {
        if (!preserveScroll) pendingScroll = null
        val scrollY = if (preserveScroll) pendingScroll ?: scroll.scrollY else 0
        val epoch = ++renderGeneration
        actions.clear()
        val text = SpannableStringBuilder()
        fun choice(number: Int, label: String, action: () -> Unit) {
            if (text.isNotEmpty()) text.append("\n")
            val start = text.length
            text.append("$number $label")
            actions[number.toString()] = { action(); if (!isFinishing) { input.setText(""); render() } }
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { actions[number.toString()]?.invoke() }
                override fun updateDrawState(ds: TextPaint) { ds.color = output.currentTextColor; ds.isUnderlineText = false }
            }, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val entries = ids()
        val current = article
        if (current == null) {
            if (category != null) HelpText.title(text, title(category!!))
            val nav = HelpNavigation(entries, page); page = nav.page
            nav.visible().forEachIndexed { index, id -> choice(index + 1, title(id)) {
                if (category == null) { rootPage = page; category = id; page = 0 }
                else article = id
            } }
            if (nav.previous) choice(8, getString(R.string.offline_help_previous_page)) { page-- }
            if (nav.next) choice(9, getString(R.string.offline_help_next_page)) { page++ }
            if (category != null) choice(10, getString(R.string.offline_help_root)) { category = null; page = rootPage }
            choice(0, getString(R.string.offline_help_close)) { closeHelp(consumeChoice = true) }
        } else {
            val body = if (current.startsWith("command:")) reference.firstOrNull { "command:${it.javaClass.simpleName}" == current }
                ?.let { OfflineHelpReference.text(this, it) } ?: getString(R.string.offline_help_no_reference)
                else OfflineHelpCatalog.find(current)?.body?.let { getString(it) }.orEmpty()
            HelpText.title(text, title(current))
            val blockColor = ThemeColorResolver.color(Theme.workspace_output_background_color)
            HelpText.body(text, body, blockColor, Tuils.dpToPx(this, 12))
            val index = entries.indexOf(current)
            choice(1, getString(R.string.offline_help_topics)) { article = null; page = index.coerceAtLeast(0) / 7 }
            if (index > 0) choice(2, getString(R.string.offline_help_previous)) { article = entries[index - 1] }
            if (index >= 0 && index < entries.lastIndex) choice(3, getString(R.string.offline_help_next)) { article = entries[index + 1] }
            choice(0, getString(R.string.offline_help_close)) { closeHelp(consumeChoice = true) }
        }
        output.text = text
        output.movementMethod = LinkMovementMethod.getInstance()
        androidx.core.view.OneShotPreDrawListener.add(output) {
            if (epoch == renderGeneration) { scroll.scrollTo(0, scrollY); pendingScroll = null }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("category", category); outState.putString("article", article)
        outState.putInt("page", page); outState.putInt("root_page", rootPage)
        outState.putString("draft", input.text.toString()); outState.putInt("start", input.selectionStart); outState.putInt("end", input.selectionEnd)
        windowPanel.save(outState)
        outState.putInt("scroll_y", pendingScroll ?: scroll.scrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() { persistReader(); windowPanel.persist(); super.onPause() }
}
