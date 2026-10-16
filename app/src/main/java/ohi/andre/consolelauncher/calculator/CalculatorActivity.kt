package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
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
    private lateinit var previewHint: TextView
    private lateinit var engineAction: TextView
    private lateinit var enginePanel: CalculatorPackPanel
    private var enhancementExpanded = true
    @Volatile private var previewGeneration = 0
    private var pendingPreview: Runnable? = null
    private var resumed = false
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

    private fun clearPreview() {
        previewGeneration++
        pendingPreview?.let { main.removeCallbacks(it) }; pendingPreview = null
        if (::previewHint.isInitialized) previewHint.text = ""
    }
    private fun schedulePreview() {
        clearPreview()
        if (request != null || !resumed) return
        val expression = input.text.toString().trim()
        if (expression.isEmpty()) return
        val action = when (expression.lowercase()) {
            "clear" -> R.string.calculator_preview_clear
            "help" -> R.string.calculator_preview_help
            "exit" -> R.string.calculator_preview_exit
            else -> null
        }
        if (action != null) { previewHint.setText(action); return }
        if ('=' in expression || ':' in expression) { previewHint.setText(R.string.calculator_preview_enter_only); return }
        val expanded = CalculatorSession.expand(expression, answer)
        if (expanded == null) { previewHint.setText(R.string.calculator_terminal_no_answer); return }
        if (Regex("[+\\-−*/×÷^]$|\\($").containsMatchIn(expanded)) { previewHint.setText(R.string.calculator_preview_incomplete); return }
        val generation = previewGeneration
        val options = CalculatorEngine.options(this).copy(exact = false)
        pendingPreview = Runnable {
            pendingPreview = null
            worker.execute {
                if (generation != previewGeneration) return@execute
                val result = CalculatorEngine.evaluate(this, expanded, options, preview = true)
                main.post {
                    if (generation != previewGeneration || isDestroyed || request != null) return@post
                    previewHint.text = when {
                        result.failure in listOf("preview_unavailable", "timeout", "cancelled") || result.parsed == null && !result.error -> getString(R.string.calculator_preview_unavailable)
                        result.error -> if (result.failure == "syntax") getString(R.string.calculator_preview_incomplete) else result.text.lineSequence().first()
                        else -> getString(R.string.calculator_preview_format, result.parsed,
                            (if (result.approximate) "≈ " else "") + CalculatorSession.display(result.text))
                    }
                    scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
                }
            }
        }.also { main.postDelayed(it, 300) }
    }

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
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val screen = FrameLayout(this).apply { isClickable = true }
        val safeHost = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val host = FrameLayout(this)
        safeHost.addView(host, ViewGroup.LayoutParams(-1, -1))
        root = layoutInflater.inflate(R.layout.calculator_surface, host, false)
        windowPanel = ohi.andre.consolelauncher.help.HelpWindowPanel(host, root, state,
            statePrefix = "calculator", header = root.findViewById(R.id.calculator_main_tab),
            row = root.findViewById(R.id.calculator_header),
            maximizeLabel = R.string.calculator_maximize, windowedLabel = R.string.calculator_windowed,
            floatingInLandscape = false)
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        input = root.findViewById(R.id.calculator_expression)
        transcript = root.findViewById(R.id.calculator_result)
        previewHint = root.findViewById(R.id.calculator_preview)
        scroll = root.findViewById(R.id.calculator_transcript_scroll)
        engineAction = TextView(this).apply {
            minHeight = Tuils.dpToPx(this@CalculatorActivity, 48)
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(Tuils.dpToPx(this@CalculatorActivity, 4), 0, Tuils.dpToPx(this@CalculatorActivity, 4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showEnhancement()
            }
        }
        root.findViewById<LinearLayout>(R.id.calculator_main_pane).addView(engineAction, 0,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        enhancementExpanded = state?.getBoolean("calculator_enhancement_expanded", true) ?: true
        enginePanel = CalculatorPackPanel(this, inline = true,
            onChanged = { updateEngineStatus(); schedulePreview() },
            onDismiss = {
                enhancementExpanded = false
                updateEngineStatus()
                scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
            })
        (scroll.getChildAt(0) as LinearLayout).addView(enginePanel.view, 0)
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        submit = root.findViewById(R.id.calculator_equals)
        cancelButton = root.findViewById(R.id.calculator_cancel_request)
        scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) scroll.post { scroll.scrollTo(0, scroll.getChildAt(0).height) }
        }
        val symbols = root.findViewById<GridLayout>(R.id.calculator_symbols)
        val suggestionSize = LauncherFontScale.terminalTextSizeSp()
        val modes = mapOf(
            "123" to listOf("7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", ".", "0", "⌫", "+", "(", ")", "="),
            "ABC" to "QWERTYUIOPASDFGHJKLZXCVBNM".map(Char::toString) + listOf("⌫", "_", "SPACE"),
            "FUNC" to listOf("sqrt", "sin", "cos", "tan", "log", "ln", "abs", "pi", "e", "ans", "to", "of", "(", ")", "^", "!", "%", "⌫", "=")
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
            (cancelButton.parent as? ViewGroup)?.removeView(cancelButton)
            symbols.removeAllViews()
            val letters = mode == "ABC"
            symbols.columnCount = if (letters) 20 else 4
            val lastRowStart = if (letters) 27 else modes.getValue(mode).size / 4 * 4
            val bottomRow = ohi.andre.consolelauncher.help.BottomCornerRow(this)
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
                    index == 26 -> Triple(2, 17, 3)
                    else -> Triple(2, 3 + (index - 19) * 2, 2)
                }
                return GridLayout.LayoutParams().apply {
                    width = 0
                    height = keyHeight * 5 * (row + 1) / 4 - keyHeight * 5 * row / 4
                    rowSpec = GridLayout.spec(row, GridLayout.CENTER)
                    columnSpec = GridLayout.spec(column, span, GridLayout.FILL, span.toFloat())
                }
            }
            modes.getValue(mode).forEachIndexed { index, label ->
                val key = Button(this).apply {
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
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        insertKey(label)
                    }
                }
                if (index >= lastRowStart) bottomRow.addKey(key, if (letters && label == "SPACE") 14f else if (letters) 3f else 1f)
                else symbols.addView(key, keyLayout(index))
            }
            bottomRow.addKey(submit, if (letters) 3f else 1f)
            bottomRow.addKey(cancelButton)
            symbols.addView(bottomRow, GridLayout.LayoutParams().apply {
                width = 0
                val keyHeight = Tuils.dpToPx(this@CalculatorActivity, 48)
                height = if (letters) keyHeight * 5 - keyHeight * 5 * 3 / 4 else keyHeight
                rowSpec = GridLayout.spec(if (letters) 3 else lastRowStart / 4, GridLayout.CENTER)
                columnSpec = GridLayout.spec(0, symbols.columnCount, 1f)
            })
            windowPanel.reserveBottomCorners(bottomRow)
            val border = root.findViewById<View>(R.id.calculator_window_border)
            val borderMargins = border.layoutParams as ViewGroup.MarginLayoutParams
            val keypadMargins = root.findViewById<View>(R.id.calculator_keypad_scroll).layoutParams as ViewGroup.MarginLayoutParams
            val pane = root.findViewById<View>(R.id.calculator_main_pane)
            val prompt = input.parent as View
            prompt.measure(View.MeasureSpec.makeMeasureSpec(Tuils.dpToPx(this, 336), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            previewHint.measure(View.MeasureSpec.makeMeasureSpec(Tuils.dpToPx(this, 336), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val minimumHeight = root.paddingTop + root.paddingBottom + borderMargins.topMargin + borderMargins.bottomMargin +
                border.paddingTop + border.paddingBottom + pane.paddingTop + pane.paddingBottom +
                maxOf(Tuils.dpToPx(this, 120), prompt.measuredHeight + previewHint.measuredHeight) + keypadMargins.topMargin + keypadMargins.bottomMargin +
                Tuils.dpToPx(this, 48) * 6
            // Five bottom actions (including Cancel) and both 48dp corners must fit without horizontal scrolling.
            windowPanel.setMinimumSize(Tuils.dpToPx(this, 336), minimumHeight)
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
                tab.setTextColor(if (tab.isSelected) textColor else ThemeColorResolver.color(Theme.muted_text_color))
                tab.background = null
            }
            input.requestFocus()
        }
        tabs.forEach { (tab, mode) -> tab.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showMode(mode)
        } }
        fun style(view: View) {
            if (view is TextView) {
                view.typeface = Tuils.getTypeface(this)
                view.textSize = suggestionSize
                view.setTextColor(ThemeColorResolver.color(if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color))
                if (view === previewHint) { view.textSize = suggestionSize * 0.85f; view.setTextColor(ThemeColorResolver.color(Theme.muted_text_color)) }
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
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = schedulePreview()
        })
        submit.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            execute()
        }
        cancelButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            cancel()
        }
        helpTab = root.findViewById(R.id.calculator_help_tab)
        mainTab = root.findViewById(R.id.calculator_main_tab)
        helpTab.setOnClickListener {
            ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.showOptions(this,
                getString(R.string.optional_calculator_help_title),
                listOf(getString(R.string.optional_calculator_help_title), getString(R.string.optional_calculator_title)),
                ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ItemAction { choice ->
                    if (choice == 0) selectHelp(true)
                    else showEnhancement()
                })
        }
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
        clearPreview()
        when (expression.lowercase()) {
            "exit" -> { input.setText(""); finish(); return }
            "clear" -> { history = ""; renderTerminal(); input.setText(""); persist(); return }
            "help" -> { input.setText(""); selectHelp(true); persist(); return }
        }
        val expanded = CalculatorSession.expand(expression, answer)
        if (expanded == null) { append("calc> $expression\n${getString(R.string.calculator_terminal_no_answer)}"); return }
        if (expanded.isBlank()) { append("calc> $expression\n${getString(R.string.calculator_invalid)}"); return }
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
                updateEngineStatus()
                val displayed = if (result.error) result.text else CalculatorSession.display(result.text)
                append((if (!result.error && (result.approximate || displayed != result.text)) "≈ " else "") + displayed)
                if (!result.error) {
                    answer = result.reusable
                    if (input.text.toString().trim() == expression) input.setText("")
                }
                persist()
                schedulePreview()
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
        schedulePreview()
    }
    private fun persist() {
        prefs.edit().putString("terminal_ans", answer).putString("terminal_transcript", history)
            .putString("terminal_draft", input.text.toString())
            .putInt("terminal_cursor", input.selectionStart)
            .putInt("terminal_selection_end", input.selectionEnd).apply()
    }
    override fun onSaveInstanceState(state: Bundle) {
        persist()
        state.putBoolean("calculator_enhancement_expanded", enhancementExpanded)
        windowPanel.save(state)
        super.onSaveInstanceState(state)
    }
    override fun onPause() { resumed = false; clearPreview(); windowPanel.persist(); persist(); super.onPause() }
    private fun updateEngineStatus() {
        engineAction.text = OptionalCalculatorEngine.statusText(this)
        enginePanel.view.visibility = if (enhancementExpanded && CalculatorEngine.supported() &&
            (enginePanel.isBusy || CalculatorPackStore.installedVersion(this) == null)) View.VISIBLE else View.GONE
        mainTab.setText(if (CalculatorEngine.enabled(this)) R.string.optional_calculator_header_enhanced else R.string.optional_calculator_header_basic)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!::root.isInitialized) return
        updateEngineStatus()
        fun refresh(view: View) {
            if (view is TextView) {
                view.typeface = Tuils.getTypeface(this)
                view.textSize = LauncherFontScale.terminalTextSizeSp()
                val role = if (view is Button) Theme.workspace_control_text_color else Theme.workspace_panel_text_color
                val color = ThemeColorResolver.color(role)
                view.setTextColor(if (view is Button && !view.isSelected && view.id in listOf(R.id.calculator_numbers_tab, R.id.calculator_letters_tab, R.id.calculator_functions_tab))
                    ThemeColorResolver.color(Theme.muted_text_color) else color)
                if (view === previewHint) { view.textSize = LauncherFontScale.terminalTextSizeSp() * 0.85f; view.setTextColor(ThemeColorResolver.color(Theme.muted_text_color)) }
                if (view is Button) {
                    view.background = null
                    view.setTypeface(Tuils.getTypeface(this), android.graphics.Typeface.BOLD)
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) refresh(view.getChildAt(index))
        }
        refresh(root)
        input.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
        input.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
        Tuils.setCursorDrawableColor(input, ThemeColorResolver.color(Theme.cursor_color))
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
        enginePanel.refreshTheme()
        root.findViewById<View>(R.id.calculator_main_pane).background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color),
            ThemeColorResolver.withMaxAlpha(ThemeColorResolver.color(Theme.workspace_panel_border_color), 210),
            1.2f, AppearanceSettings.outputCornerRadius(), AppearanceSettings.dashedBorders(), target = FrameTarget.OVERLAYS)
        schedulePreview()
    }
    private fun showEnhancement() {
        if (CalculatorPackStore.installedVersion(this) != null || !CalculatorEngine.supported()) {
            OptionalCalculatorEngine.showManagement(this) { updateEngineStatus(); schedulePreview() }
        } else {
            enhancementExpanded = true
            enginePanel.refresh()
            updateEngineStatus()
            scroll.post { scroll.scrollTo(0, 0) }
        }
    }

    @Deprecated("Platform document picker result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::enginePanel.isInitialized) enginePanel.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() { if (::enginePanel.isInitialized) enginePanel.destroy(); clearPreview(); request?.cancel(); request = null; worker.shutdownNow(); super.onDestroy() }
}
