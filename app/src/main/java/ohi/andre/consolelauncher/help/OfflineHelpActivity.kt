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
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils

class OfflineHelpActivity : LocalizedActivity() {
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
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private val reference by lazy { OfflineHelpReference.commands(this) }
    private val actions = mutableMapOf<String, () -> Unit>()

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this)
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val root = layoutInflater.inflate(R.layout.offline_help_surface, host, false)
        host.addView(root)
        input = root.findViewById(R.id.offline_help_input)
        output = root.findViewById(R.id.offline_help_output)
        scroll = root.findViewById(R.id.offline_help_scroll)
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        root.setBackgroundColor(ThemeColorResolver.color(Theme.workspace_panel_background_color))
        listOf(output, input, root.findViewById<TextView>(R.id.offline_help_prompt), root.findViewById<TextView>(R.id.offline_help_header)).forEach {
            it.typeface = Typeface.MONOSPACE; it.setTextColor(textColor)
        }
        output.setLinkTextColor(textColor)
        root.findViewById<View>(R.id.offline_help_panel).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius(),
            ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders(), target = ohi.andre.consolelauncher.tuils.FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.offline_help_panel))
        input.showSoftInputOnFocus = false
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER) {
                if (event.action == KeyEvent.ACTION_UP) submit()
                true
            } else false
        }
        val keys = root.findViewById<GridLayout>(R.id.offline_help_keys)
        listOf("1", "2", "3", "4", "5", "⌫", "6", "7", "8", "9", "0", "↵").forEachIndexed { index, label ->
            keys.addView(Button(this).apply {
                text = label; minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
                setPadding(0, 0, 0, 0); setTextColor(textColor)
                background = TerminalBorderRuntime.tabDrawable(this@OfflineHelpActivity, ThemeColorResolver.color(Theme.workspace_header_background_color))
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
            }, GridLayout.LayoutParams().apply {
                width = 0; height = Tuils.dpToPx(this@OfflineHelpActivity, 48)
                rowSpec = GridLayout.spec(index / 6)
                columnSpec = GridLayout.spec(index % 6, 1f)
            })
        }
        if (state != null) {
            category = state.getString("category"); article = state.getString("article")
            page = state.getInt("page"); rootPage = state.getInt("root_page")
        } else {
            val topic = OfflineHelpCatalog.find(intent.getStringExtra("topic").orEmpty())
            category = topic?.category ?: topic?.id
            article = topic?.takeIf { it.category != null }?.id
        }
        render()
        if (state != null) {
            input.setText(state.getString("draft", ""))
            input.setSelection(state.getInt("start").coerceIn(0, input.length()), state.getInt("end").coerceIn(0, input.length()))
        }
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(screen)
        input.requestFocus()
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
    private fun render() {
        actions.clear()
        val text = SpannableStringBuilder()
        fun choice(number: Int, label: String, action: () -> Unit) {
            if (text.isNotEmpty()) text.append("\n")
            val start = text.length
            text.append("$number $label")
            actions[number.toString()] = { action(); input.setText(""); render() }
            text.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { actions[number.toString()]?.invoke() }
                override fun updateDrawState(ds: TextPaint) { ds.color = output.currentTextColor; ds.isUnderlineText = false }
            }, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val entries = ids()
        val current = article
        if (current == null) {
            text.append(category?.let { title(it) } ?: getString(R.string.offline_help_title)).append("\n\n")
            val nav = HelpNavigation(entries, page); page = nav.page
            nav.visible().forEachIndexed { index, id -> choice(index + 1, title(id)) {
                if (category == null) { rootPage = page; category = id; page = 0 }
                else article = id
            } }
            if (nav.previous) choice(8, getString(R.string.offline_help_previous_page)) { page-- }
            if (nav.next) choice(9, getString(R.string.offline_help_next_page)) { page++ }
            choice(0, getString(R.string.offline_help_close)) { finish() }
        } else {
            val body = if (current.startsWith("command:")) reference.firstOrNull { "command:${it.javaClass.simpleName}" == current }
                ?.let { OfflineHelpReference.text(this, it) } ?: getString(R.string.offline_help_no_reference)
                else OfflineHelpCatalog.find(current)?.body?.let { getString(it) }.orEmpty()
            text.append(title(current)).append("\n\n").append(body).append("\n\n")
            val index = entries.indexOf(current)
            choice(1, getString(R.string.offline_help_topics)) { article = null; page = index.coerceAtLeast(0) / 7 }
            if (index > 0) choice(2, getString(R.string.offline_help_previous)) { article = entries[index - 1] }
            if (index >= 0 && index < entries.lastIndex) choice(3, getString(R.string.offline_help_next)) { article = entries[index + 1] }
            choice(0, getString(R.string.offline_help_close)) { finish() }
        }
        output.text = text
        output.movementMethod = LinkMovementMethod.getInstance()
        scroll.post { scroll.scrollTo(0, 0) }
    }
    @Deprecated("Manual hierarchy")
    override fun onBackPressed() {
        when {
            article != null -> { val index = ids().indexOf(article); article = null; page = index.coerceAtLeast(0) / 7 }
            category != null -> { category = null; page = rootPage }
            else -> { finish(); return }
        }
        input.setText(""); render()
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("category", category); state.putString("article", article)
        state.putInt("page", page); state.putInt("root_page", rootPage)
        state.putString("draft", input.text.toString()); state.putInt("start", input.selectionStart); state.putInt("end", input.selectionEnd)
        super.onSaveInstanceState(state)
    }
}
