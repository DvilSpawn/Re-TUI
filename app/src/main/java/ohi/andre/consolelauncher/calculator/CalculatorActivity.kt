package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.content.Intent
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
    private var history = ""
    private lateinit var helpTab: TextView
    private lateinit var mainTab: TextView
    private lateinit var root: View
    private lateinit var windowPanel: ohi.andre.consolelauncher.help.HelpWindowPanel

    private fun selectHelp(help: Boolean) {
        if (help) {
            ohi.andre.consolelauncher.help.OfflineHelpActivity.open(this, "calculator-help")
            return
        }
        renderTerminal()
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        listOf(helpTab to help, mainTab to !help).forEach { (tab, selected) ->
            tab.isSelected = selected
            tab.setTextColor(textColor)
            tab.background = TerminalBorderRuntime.tabDrawable(this,
                ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS).let {
                if (tab === helpTab) android.graphics.drawable.InsetDrawable(it, 0, 0, 0, Tuils.dpToPx(this, 28)) else it
            }
            tab.setPadding(Tuils.dpToPx(this, 10), 0, Tuils.dpToPx(this, 10), 0)
        }
        TerminalBorderRuntime.bind(root.findViewById(R.id.calculator_window_border), if (help) helpTab else mainTab)
        TerminalPaneIcon.apply(helpTab, R.drawable.ic_material_help_24)
    }

    private fun renderTerminal() {
        transcript.text = history.trimEnd()
        transcript.movementMethod = android.text.method.ArrowKeyMovementMethod.getInstance()
        root.findViewById<TextView>(R.id.calculator_prompt).text = "calc> "
        scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
    }

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this); XMLPrefsManager.loadCommons(this); LauncherSystemUi.applyFullscreen(this)
        if (CalculatorEngine.supported()) LauncherSettings.set(this, Behavior.qalculate, "true")
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this).apply { isClickable = true }
        val safeHost = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val host = FrameLayout(this)
        safeHost.addView(host, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.calculator_surface, host, false)
        windowPanel = ohi.andre.consolelauncher.help.HelpWindowPanel(host, root, state,
            statePrefix = "calculator", header = root.findViewById(R.id.calculator_main_tab),
            row = root.findViewById(R.id.calculator_header),
            maximizeLabel = R.string.calculator_maximize, windowedLabel = R.string.calculator_windowed)
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        input = root.findViewById(R.id.calculator_expression)
        transcript = root.findViewById(R.id.calculator_result)
        scroll = root.findViewById(R.id.calculator_transcript_scroll)
        submit = root.findViewById(R.id.calculator_equals)
        cancelButton = root.findViewById(R.id.calculator_cancel_request)
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
        }
        val symbols = root.findViewById<GridLayout>(R.id.calculator_symbols)
        val suggestionSize = LauncherFontScale.terminalTextSizeSp()
        val modes = mapOf(
            "123" to listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", ".", "0", "⌫", "+", "(", ")", "√"),
            "ABC" to "QWERTYUIOPASDFGHJKLZXCVBNM".map(Char::toString) + listOf("_", "SPACE", "⌫"),
            "FUNC" to listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs", "pi", "e", "ans", "to", "of", "(", ")", "^", "!", "%", "⌫")
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
            val text = input.text.toString()
            val start = input.selectionStart.coerceAtLeast(0)
            val end = input.selectionEnd.coerceAtLeast(0)
            val function = label in listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs")
            val token = when {
                label == "√" -> "sqrt("
                function -> "$label("
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
            (submit.parent as? ViewGroup)?.removeView(submit)
            symbols.removeAllViews()
            val letters = mode == "ABC"
            symbols.columnCount = if (letters) 20 else 4
            fun keyLayout(index: Int): GridLayout.LayoutParams {
                val keyHeight = Tuils.dpToPx(this@CalculatorActivity, 48)
                if (!letters) return GridLayout.LayoutParams().apply {
                    width = 0
                    height = keyHeight
                    // Text baselines must not enlarge the fixed-height key rows.
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                val (row, column, span) = when {
                    index < 10 -> Triple(0, index * 2, 2)
                    index < 19 -> Triple(1, 1 + (index - 10) * 2, 2)
                    index < 26 -> Triple(2, 3 + (index - 19) * 2, 2)
                    index == 26 -> Triple(3, 0, 3)
                    index == 27 -> Triple(3, 3, 11)
                    index == 28 -> Triple(3, 14, 3)
                    else -> Triple(3, 17, 3)
                }
                return GridLayout.LayoutParams().apply {
                    width = 0
                    height = keyHeight * 5 * (row + 1) / 4 - keyHeight * 5 * row / 4
                    rowSpec = GridLayout.spec(row, GridLayout.CENTER)
                    columnSpec = GridLayout.spec(column, span, GridLayout.FILL, span.toFloat())
                }
            }
            modes.getValue(mode).forEachIndexed { index, label ->
                symbols.addView(Button(this).apply {
                    text = label
                    isAllCaps = false
                    isFocusable = false
                    minWidth = Tuils.dpToPx(this@CalculatorActivity, 48)
                    minHeight = Tuils.dpToPx(this@CalculatorActivity, 48)
                    setPadding(0, 0, 0, 0)
                    includeFontPadding = false
                    typeface = Tuils.getTypeface(this@CalculatorActivity)
                    textSize = suggestionSize
                    setTextColor(ThemeColorResolver.color(Theme.workspace_control_text_color))
                    background = null
                    setTypeface(Tuils.getTypeface(this@CalculatorActivity), android.graphics.Typeface.BOLD)
                    if (label == "√") contentDescription = getString(R.string.surface_square_root_47a32)
                    if (label == "⌫") contentDescription = getString(R.string.surface_backspace_88d13)
                    setOnClickListener { insertKey(label) }
                }, keyLayout(index))
            }
            symbols.addView(submit, keyLayout(modes.getValue(mode).size))
            if (letters) {
                // Empty edge cells keep the staggered rows centered.
                listOf(Triple(1, 0, 1), Triple(1, 19, 1), Triple(2, 0, 3), Triple(2, 17, 3)).forEach { (row, column, span) ->
                    symbols.addView(TextView(this).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, GridLayout.LayoutParams().apply {
                        width = 0
                        height = 0
                        rowSpec = GridLayout.spec(row, GridLayout.CENTER)
                        columnSpec = GridLayout.spec(column, span, GridLayout.FILL, span.toFloat())
                    })
                }
            }
            val textColor = ThemeColorResolver.color(Theme.workspace_control_text_color)
            tabs.forEach { (tab, selected) ->
                tab.isSelected = selected == mode
                tab.setTextColor(if (tab.isSelected) textColor else ThemeColorResolver.withMaxAlpha(textColor, 150))
                tab.background = null
            }
            input.requestFocus()
        }
        tabs.forEach { (tab, mode) -> tab.setOnClickListener { showMode(mode) } }
        fun style(view: View) {
            if (view is TextView) {
                view.typeface = Tuils.getTypeface(this)
                view.textSize = suggestionSize
                view.setTextColor(ThemeColorResolver.color(if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color))
                if (view is Button) {
                    view.background = null
                    view.setTypeface(Tuils.getTypeface(this), android.graphics.Typeface.BOLD)
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
        }
        style(root)
        input.background = null
        root.findViewById<View>(R.id.calculator_header).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        root.findViewById<View>(R.id.calculator_keypad_dock).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        (submit as TextView).textSize = suggestionSize
        root.findViewById<View>(R.id.calculator_window_border).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        answer = prefs.getString("terminal_ans", null)
        history = prefs.getString("terminal_transcript", null) ?: getString(R.string.calculator_terminal_help)
        transcript.text = history.trimEnd()
        input.setText(prefs.getString("terminal_draft", ""))
        input.setSingleLine(true); input.setHorizontallyScrolling(true); input.ellipsize = null
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        input.showSoftInputOnFocus = false
        input.typeface = Tuils.getTypeface(this)
        transcript.typeface = Tuils.getTypeface(this)
        root.findViewById<TextView>(R.id.calculator_prompt).typeface = Tuils.getTypeface(this)
        showMode("123")
        input.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        input.setSelection(prefs.getInt("terminal_cursor", input.length()).coerceIn(0, input.length()),
            prefs.getInt("terminal_selection_end", prefs.getInt("terminal_cursor", input.length())).coerceIn(0, input.length()))
        input.setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_DONE || (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP)) {
                execute(); true
            } else false
        }
        submit.setOnClickListener { execute() }
        cancelButton.setOnClickListener { cancel() }
        helpTab = root.findViewById(R.id.calculator_help_tab)
        mainTab = root.findViewById(R.id.calculator_main_tab)
        helpTab.setOnClickListener { selectHelp(true) }
        TerminalPaneIcon.apply(helpTab, R.drawable.ic_material_help_24)
        mainTab.setOnClickListener { selectHelp(false) }
        selectHelp(false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(screen) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())
            val measuredImeOffset = (ime.bottom - bars.bottom).coerceAtLeast(0)
            CalculatorEngine.rememberImeBottomOffset(this, measuredImeOffset)
            val keyboardOffset = measuredImeOffset
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
            "clear" -> { history = ""; renderTerminal(); input.setText(""); persist(); return }
            "help" -> { input.setText(""); selectHelp(true); persist(); return }
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
        history = (history.trimEnd() + "\n" + text + "\n").trimStart().takeLast(65536)
        transcript.text = history.trimEnd()
        scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
    }
    private fun cancel() {
        request?.cancel(); request = null
        submit.isEnabled = true; cancelButton.visibility = View.GONE
        append(getString(R.string.calculator_cancelled)); persist()
    }
    private fun persist() {
        prefs.edit().putString("terminal_ans", answer).putString("terminal_transcript", history)
            .putString("terminal_draft", input.text.toString())
            .putInt("terminal_cursor", input.selectionStart)
            .putInt("terminal_selection_end", input.selectionEnd).apply()
    }
    override fun onSaveInstanceState(state: Bundle) {
        persist()
        windowPanel.save(state)
        super.onSaveInstanceState(state)
    }
    override fun onPause() { persist(); super.onPause() }
    override fun onResume() {
        super.onResume()
        if (!::root.isInitialized) return
        fun refresh(view: View) {
            if (view is TextView) {
                view.typeface = Tuils.getTypeface(this)
                view.textSize = LauncherFontScale.terminalTextSizeSp()
                val role = if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color
                val color = ThemeColorResolver.color(role)
                view.setTextColor(if (view is Button && !view.isSelected && view.id in listOf(R.id.calculator_numbers_tab, R.id.calculator_letters_tab, R.id.calculator_functions_tab))
                    ThemeColorResolver.withMaxAlpha(color, 150) else color)
                if (view is Button) {
                    view.background = null
                    view.setTypeface(Tuils.getTypeface(this), android.graphics.Typeface.BOLD)
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) refresh(view.getChildAt(index))
        }
        refresh(root)
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        listOf(R.id.calculator_header, R.id.calculator_keypad_dock).forEach {
            root.findViewById<View>(it).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        listOf(mainTab, helpTab).forEach {
            it.setTextColor(ThemeColorResolver.color(Theme.workspace_panel_text_color))
            it.includeFontPadding = false
            it.setTypeface(Tuils.getTypeface(this), android.graphics.Typeface.BOLD)
            it.textSize = AppearanceSettings.outputHeaderTextSize().toFloat()
            it.background = TerminalBorderRuntime.tabDrawable(this,
                ThemeColorResolver.color(Theme.workspace_header_background_color), FrameTarget.OVERLAYS).let { drawable ->
                if (it === helpTab) android.graphics.drawable.InsetDrawable(drawable, 0, 0, 0, Tuils.dpToPx(this, 28)) else drawable
            }
            it.setPadding(Tuils.dpToPx(this, 10), 0, Tuils.dpToPx(this, 10), 0)
        }
        TerminalPaneIcon.apply(helpTab, R.drawable.ic_material_help_24)
        root.findViewById<View>(R.id.calculator_window_border).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), ThemeColorResolver.color(Theme.workspace_panel_border_color),
            1.5f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(root.findViewById(R.id.calculator_window_border), mainTab)
        windowPanel.refreshTheme()
        root.findViewById<View>(R.id.calculator_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color),
            ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
    }
    override fun onDestroy() { request?.cancel(); request = null; worker.shutdownNow(); super.onDestroy() }
}
