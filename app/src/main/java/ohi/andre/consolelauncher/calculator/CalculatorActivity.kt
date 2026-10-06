package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.*
import java.util.concurrent.Executors

class CalculatorActivity : LocalizedActivity() {
    companion object {
        fun open(context: Context) = context.startActivity(Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private lateinit var input: EditText
    private lateinit var transcript: TextView
    private lateinit var scroll: ScrollView
    private lateinit var submit: View
    private lateinit var cancelButton: View
    private val prefs by lazy { CalculatorEngine.prefs(this) }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var request: CalculatorEngine.Request? = null
    private var answer: String? = null

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        if (CalculatorEngine.supported()) LauncherSettings.set(this, Behavior.qalculate, "true")
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this)
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val root = layoutInflater.inflate(R.layout.calculator_surface, host, false)
        host.addView(root)
        input = root.findViewById(R.id.calculator_expression)
        transcript = root.findViewById(R.id.calculator_result)
        scroll = root.findViewById(R.id.calculator_transcript_scroll)
        submit = root.findViewById(R.id.calculator_equals)
        cancelButton = root.findViewById(R.id.calculator_cancel_request)
        val symbols = root.findViewById<GridLayout>(R.id.calculator_symbols)
        val suggestionSize = XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat()
        val modes = mapOf(
            "123" to listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", ".", "0", "%", "+", "(", ")", "√", "⌫", "="),
            "ABC" to ('A'..'Z').map(Char::toString) + listOf("_", "SPACE", "⌫"),
            "FUNC" to listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs", "pi", "e", "ans", "to", "of", "(", ")", "^", "!", "⌫")
        )
        val tabs = listOf(
            root.findViewById<Button>(R.id.calculator_numbers_tab) to "123",
            root.findViewById<Button>(R.id.calculator_letters_tab) to "ABC",
            root.findViewById<Button>(R.id.calculator_functions_tab) to "FUNC"
        )
        fun insertKey(label: String) {
            if (label == "⌫") {
                val edit = CalculatorEditing.backspace(input.text.toString(), input.selectionStart, input.selectionEnd)
                input.setText(edit.text); input.setSelection(edit.cursor); persist(); return
            }
            if (label == "=") { execute(); return }
            val text = input.text.toString()
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            val function = label in listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs")
            val token = when {
                label == "√" -> "sqrt("
                label == "SPACE" -> " "
                label in listOf("to", "of") -> " $label "
                label.length == 1 && label[0].isLetter() -> label.lowercase()
                else -> label
            }
            val edit = if (label == "√" || function) CalculatorEditing.wrap(text, start, end, token, ")")
                else CalculatorEditing.insert(text, start, end, token)
            input.setText(edit.text); input.setSelection(edit.cursor)
            input.requestFocus(); input.bringPointIntoView(edit.cursor); persist()
        }
        fun showMode(mode: String) {
            symbols.removeAllViews()
            symbols.columnCount = if (mode == "ABC") 5 else 4
            modes.getValue(mode).forEach { label ->
                symbols.addView(Button(this).apply {
                    text = label
                    isAllCaps = false
                    isFocusable = false
                    minWidth = Tuils.dpToPx(this@CalculatorActivity, 48)
                    minHeight = Tuils.dpToPx(this@CalculatorActivity, 48)
                    setPadding(8, 4, 8, 4)
                    typeface = Tuils.getTypeface(this@CalculatorActivity)
                    textSize = suggestionSize
                    setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
                    background = TerminalBorderRuntime.tabDrawable(this@CalculatorActivity,
                        ThemeColorResolver.color(Theme.workspace_control_background_color), FrameTarget.OVERLAYS)
                    if (label == "√") contentDescription = getString(R.string.surface_square_root_47a32)
                    if (label == "⌫") contentDescription = getString(R.string.surface_backspace_88d13)
                    setOnClickListener { insertKey(label) }
                }, GridLayout.LayoutParams().apply {
                    width = 0
                    height = Tuils.dpToPx(this@CalculatorActivity, 48)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                })
            }
            val textColor = ThemeColorResolver.color(Theme.workspace_control_text_color)
            tabs.forEach { (tab, selected) ->
                tab.isSelected = selected == mode
                tab.setTextColor(if (tab.isSelected) textColor else ThemeColorResolver.withMaxAlpha(textColor, 150))
                tab.background = TerminalBorderRuntime.tabDrawable(this,
                    ThemeColorResolver.color(if (selected == mode) Theme.workspace_panel_background_color else Theme.workspace_header_background_color), FrameTarget.OVERLAYS)
            }
            input.requestFocus()
        }
        tabs.forEach { (tab, mode) -> tab.setOnClickListener { showMode(mode) } }
        fun style(view: View) {
            if (view is TextView) {
                view.typeface = Tuils.getTypeface(this)
                view.textSize = suggestionSize
                view.setTextColor(ThemeColorResolver.color(if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color))
                if (view is Button) view.background = TerminalBorderRuntime.tabDrawable(this,
                    ThemeColorResolver.color(Theme.workspace_control_background_color), FrameTarget.OVERLAYS)
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        style(root)
        root.findViewById<View>(R.id.calculator_window_border).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        answer = prefs.getString("terminal_ans", null)
        transcript.text = prefs.getString("terminal_transcript", null) ?: getString(R.string.calculator_terminal_help)
        input.setText(prefs.getString("terminal_draft", ""))
        input.setSingleLine(true); input.setHorizontallyScrolling(true); input.ellipsize = null
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.showSoftInputOnFocus = false
        showMode("123")
        input.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.setSelection(prefs.getInt("terminal_cursor", input.length()).coerceIn(0, input.length()))
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP)) {
                execute(); true
            } else false
        }
        submit.setOnClickListener { execute() }
        cancelButton.setOnClickListener { cancel() }
        val helpTab = root.findViewById<ImageButton>(R.id.calculator_help_tab)
        val mainTab = root.findViewById<ImageButton>(R.id.calculator_main_tab)
        fun topTab(fillColor: Int): GradientDrawable {
            val radius = Tuils.dpToPx(this, AppearanceSettings.headerCornerRadius().toFloat()).toFloat()
            return GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fillColor)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
        }
        fun selectHelp(help: Boolean) {
            root.findViewById<View>(R.id.calculator_main_pane).visibility = if (help) View.GONE else View.VISIBLE
            root.findViewById<View>(R.id.calculator_help_pane).visibility = if (help) View.VISIBLE else View.GONE
            val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
            listOf(helpTab to help, mainTab to !help).forEach { (tab, selected) ->
                tab.isSelected = selected
                tab.imageTintList = ColorStateList.valueOf(textColor)
                tab.imageAlpha = if (selected) 255 else 96
                tab.background = topTab(ThemeColorResolver.color(if (selected) Theme.workspace_panel_background_color else Theme.workspace_header_background_color))
            }
            TerminalBorderRuntime.bind(root.findViewById(R.id.calculator_window_border), if (help) helpTab else mainTab)
            if (help) (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        }
        helpTab.setOnClickListener { selectHelp(true) }
        mainTab.setOnClickListener { selectHelp(false) }
        selectHelp(false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val measuredImeOffset = (ime.bottom - bars.bottom).coerceAtLeast(0)
            CalculatorEngine.rememberImeBottomOffset(this, measuredImeOffset)
            val keyboardOffset = maxOf(measuredImeOffset, CalculatorEngine.lastImeBottomOffset(this))
            val keyboardMargin = if (XMLPrefsManager.wasChanged(Ui.keyboard_visible_margin_override, false)) Ui.keyboard_visible_margin_override else Ui.display_margin_bottom_section
            val bottomMargin = XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(keyboardMargin), 4, 0)[3]
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom + keyboardOffset + Tuils.mmToPx(resources.displayMetrics, bottomMargin))
            insets
        }
        setContentView(screen)
        root.requestFocus()
    }

    private fun execute() {
        if (request != null) return
        val expression = input.text.toString().trim()
        if (expression.isEmpty()) return
        when (expression.lowercase()) {
            "exit" -> { input.setText(""); finish(); return }
            "clear" -> { transcript.text = ""; input.setText(""); persist(); return }
            "help" -> { append("calc> help\n${getString(R.string.calculator_terminal_help)}"); input.setText(""); persist(); return }
        }
        val expanded = CalculatorSession.expand(expression, answer)
        if (expanded == null) { append("calc> $expression\n${getString(R.string.calculator_terminal_no_answer)}"); return }
        append("calc> $expression")
        val active = CalculatorEngine.Request()
        val options = CalculatorEngine.options(this).copy(exact = false)
        request = active
        submit.isEnabled = false
        cancelButton.visibility = View.VISIBLE
        worker.execute {
            val result = CalculatorEngine.evaluate(this, expanded, options, active)
            main.post {
                if (request !== active || isDestroyed) return@post
                request = null; submit.isEnabled = true; cancelButton.visibility = View.GONE
                val displayed = if (result.error) result.text else CalculatorSession.display(result.text)
                append((if (!result.error && (result.approximate || displayed != result.text)) "≈ " else "") + displayed)
                if (!result.error) {
                    answer = result.reusable
                    if (input.text.toString().trim() == expression) input.setText("")
                }
                persist()
            }
        }
    }
    private fun append(text: String) {
        transcript.text = (transcript.text.toString().trimEnd() + "\n" + text + "\n").trimStart().takeLast(65536)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
    private fun cancel() {
        request?.cancel(); request = null
        submit.isEnabled = true; cancelButton.visibility = View.GONE
        append(getString(R.string.calculator_cancelled)); persist()
    }
    private fun persist() {
        prefs.edit().putString("terminal_ans", answer).putString("terminal_transcript", transcript.text.toString())
            .putString("terminal_draft", input.text.toString()).putInt("terminal_cursor", input.selectionStart).apply()
    }
    override fun onPause() { persist(); super.onPause() }
    override fun onDestroy() { request?.cancel(); request = null; worker.shutdownNow(); super.onDestroy() }
}
