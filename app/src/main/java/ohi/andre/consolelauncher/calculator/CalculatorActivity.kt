package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleNameTextColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBackgroundColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBorderColor
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.concurrent.Executors

class CalculatorActivity : LocalizedActivity() {
    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private lateinit var expression: EditText
    private lateinit var result: TextView
    private var qalculate = false
    private lateinit var advanced: LinearLayout
    private lateinit var equals: TextView
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var revision = 0
    private var evaluating = false
    private val preview = Runnable { if (!CalculatorEngine.enabled(this)) calculate(false) }
    private val prefs by lazy { CalculatorEngine.prefs(this) }

    override fun onCreate(state: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(state)
        Tuils.init(this)
        XMLPrefsManager.loadCommons(this)
        LauncherSystemUi.applyFullscreen(this)
        val screen = FrameLayout(this).apply { fitsSystemWindows = true }
        val host = TuixtLayout.addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val overlay = layoutInflater.inflate(R.layout.calculator_surface, host, false) as FrameLayout
        host.addView(overlay)
        val border = overlay.findViewById<View>(R.id.calculator_window_border)
        overlay.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val available = overlay.height - overlay.paddingTop - overlay.paddingBottom
            if (available > dp(16)) {
                val maximum = available - dp(if (landscape) 8 else 16)
                val minimum = dp(if (landscape) 260 else 620).coerceAtMost(maximum)
                val height = (available * if (landscape) 0.92f else 0.64f).toInt().coerceIn(minimum, maximum)
                val inset = if (landscape) dp(16) else 0
                val params = border.layoutParams as FrameLayout.LayoutParams
                if (params.height != height || params.leftMargin != inset) {
                    params.height = height
                    params.setMargins(inset, 0, inset, 0)
                    border.layoutParams = params
                }
            }
        }
        expression = overlay.findViewById(R.id.calculator_expression)
        expression.apply {
            hint = "0"
            contentDescription = getString(R.string.ui_calculator_surface_expression)
            filters = arrayOf(InputFilter.LengthFilter(CalculatorEngine.MAX_INPUT))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setText(state?.getString("expression") ?: prefs.getString("expression", ""))
            setSelection((state?.getInt("cursor") ?: prefs.getInt("cursor", length())).coerceIn(0, length()))
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { calculate(true); true } else false
            }
        }
        result = overlay.findViewById<TextView>(R.id.calculator_result).apply {
            text = state?.getString("result") ?: prefs.getString("result", "")
            setTextIsSelectable(true)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        advanced = overlay.findViewById(R.id.calculator_advanced)
        val options = LinearLayout(this)
        options.addView(option(R.string.calculator_degrees, "degrees"), LinearLayout.LayoutParams(0, dp(48), 1f))
        options.addView(option(R.string.calculator_exact, "exact"), LinearLayout.LayoutParams(0, dp(48), 1f))
        advanced.addView(options)
        val functions = LinearLayout(this)
        listOf("sin(", "cos(", "tan(", "ln(", "log(", "abs(", "pi", "e", "solve(", "diff(", "integrate(", " to ", "x", ",", "!", "%").forEach { token ->
            functions.addView(key(token.trim()) { insert(token) }, LinearLayout.LayoutParams(dp(96), dp(48)))
        }
        advanced.addView(HorizontalScrollView(this).apply { addView(functions) })
        val keypad = overlay.findViewById<GridLayout>(R.id.calculator_keypad)
        buildKeypad(keypad, listOf("C", "(", ")", "⌫", "√", "^", "%", "/", "7", "8", "9", "*", "4", "5", "6", "-", "1", "2", "3", "+", "", "0", ".", "="))
        overlay.findViewById<View>(R.id.calculator_close).setOnClickListener { finish() }
        styleWindow(overlay)
        expression.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateMode()
        setContentView(screen)
    }

    private fun option(label: Int, preference: String) = CheckBox(this).apply {
        text = getString(label); isChecked = prefs.getBoolean(preference, true)
        setTextColor(ThemeColorResolver.color(Theme.workspace_panel_text_color)); minHeight = dp(48)
        setOnCheckedChangeListener { _, value -> prefs.edit().putBoolean(preference, value).apply(); changed() }
    }

    override fun onResume() {
        super.onResume()
        val enabled = CalculatorEngine.enabled(this)
        if (qalculate != enabled) { changed(); updateMode() }
    }

    private fun updateMode() {
        qalculate = CalculatorEngine.enabled(this)
        advanced.visibility = if (qalculate) View.VISIBLE else View.GONE
        expression.hint = if (qalculate) getString(R.string.calculator_qalculate_hint) else "0"
        expression.showSoftInputOnFocus = qalculate
    }

    private fun styleWindow(root: View) {
        val text = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        val borderColor = ThemeColorResolver.color(Theme.workspace_panel_border_color)
        val labelBackground = ThemeColorResolver.color(Theme.workspace_header_background_color)
        val border = root.findViewById<View>(R.id.calculator_window_border)
        val title = root.findViewById<TextView>(R.id.calculator_window_label)
        val close = root.findViewById<TextView>(R.id.calculator_close)
        border.background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_panel_background_color), borderColor,
            1.5f, outputCornerRadius(), dashedBorders(), target = FrameTarget.OVERLAYS)
        listOf(title, close).forEach {
            it.setTypeface(Tuils.getTypeface(this), Typeface.BOLD); it.textSize = 15f; it.setTextColor(text)
        }
        title.background = TerminalBorderRuntime.tabDrawable(this, labelBackground, FrameTarget.OVERLAYS)
        close.background = TerminalBorderRuntime.tabDrawable(this, labelBackground, text, true, FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(border, title, close)
        val display = root.findViewById<View>(R.id.calculator_display_panel)
        val label = root.findViewById<TextView>(R.id.calculator_display_label)
        display.background = TerminalBorderRuntime.panelDrawable(this,
            ThemeColorResolver.color(Theme.workspace_output_background_color), ThemeColorResolver.withMaxAlpha(borderColor, 210),
            1.2f, outputCornerRadius(), dashedBorders(), target = FrameTarget.OVERLAYS)
        label.background = TerminalBorderRuntime.tabDrawable(this, labelBackground, FrameTarget.OVERLAYS)
        TerminalBorderRuntime.bind(display, label)
        listOf(label, expression, result).forEach {
            it.setTypeface(Tuils.getTypeface(this), Typeface.BOLD); it.setTextColor(text)
        }
        result.setTextColor(moduleNameTextColor())
    }

    private fun changed() {
        revision++
        result.text = ""
        main.removeCallbacks(preview)
        if (!CalculatorEngine.enabled(this)) main.postDelayed(preview, 180)
        persist()
    }

    private fun buildKeypad(keypad: GridLayout, labels: List<String>) = keypad.apply {
        columnCount = 4
        labels.forEachIndexed { index, label ->
            val button = key(label.trim(), when (label) {
                "⌫" -> getString(R.string.surface_backspace_88d13)
                "√" -> getString(R.string.surface_square_root_47a32)
                "C" -> getString(R.string.surface_clear_719ea)
                "=" -> getString(R.string.surface_equals_09b6a)
                else -> label.trim()
            }) {
                when (label) {
                    "C" -> expression.text.clear()
                    "⌫" -> {
                        val start = expression.selectionStart.coerceAtLeast(0)
                        val end = expression.selectionEnd.coerceAtLeast(start)
                        if (end > start) expression.text.delete(start, end)
                        else if (start > 0) expression.text.delete(expression.text.toString().offsetByCodePoints(start, -1), start)
                    }
                    "=" -> calculate(true)
                    "√" -> insert("sqrt(")
                    "%" -> insert(if (qalculate) "%" else "/100")
                    else -> insert(label)
                }
            }
            if (label == "=") { equals = button; equals.id = R.id.calculator_equals }
            if (label.isEmpty()) { button.isClickable = false; button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            addView(button, GridLayout.LayoutParams(GridLayout.spec(index / 4, 1f), GridLayout.spec(index % 4, 1f)).apply {
                width = 0; height = 0; setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun insert(token: String) {
        val start = expression.selectionStart.coerceAtLeast(0)
        expression.text.replace(start, expression.selectionEnd.coerceAtLeast(start), token)
    }

    private fun key(label: String, description: String = label, action: () -> Unit) = TextView(this).apply {
        text = label; contentDescription = description; gravity = Gravity.CENTER
        setTypeface(Tuils.getTypeface(this@CalculatorActivity), Typeface.BOLD)
        setTextColor(moduleNameTextColor()); textSize = 15f
        background = TerminalBorderRuntime.panelDrawable(this@CalculatorActivity,
            moduleButtonBackgroundColor(), moduleButtonBorderColor(), 1.2f, moduleCornerRadius(),
            dashedBorders(), false, target = FrameTarget.OVERLAYS)
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun calculate(explicit: Boolean) {
        main.removeCallbacks(preview)
        if (evaluating || expression.text.isBlank()) return
        val input = expression.text.toString()
        val current = revision
        evaluating = true; equals.isEnabled = false
        if (explicit) result.text = getString(R.string.calculator_working)
        worker.execute {
            val answer = CalculatorEngine.evaluate(applicationContext, input)
            main.post {
                evaluating = false
                if (isDestroyed) return@post
                equals.isEnabled = true
                if (current == revision) {
                    result.text = if (!explicit && answer.error) "" else answer.text
                    persist()
                } else if (!CalculatorEngine.enabled(this)) main.post(preview)
            }
        }
    }

    private fun persist() {
        if (!::expression.isInitialized || !::result.isInitialized) return
        prefs.edit().putString("expression", expression.text.toString())
            .putString("result", if (evaluating) "" else result.text.toString())
            .putInt("cursor", expression.selectionStart).apply()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("expression", expression.text.toString())
        out.putString("result", if (evaluating) "" else result.text.toString())
        out.putInt("cursor", expression.selectionStart)
        super.onSaveInstanceState(out)
    }
    override fun onPause() { persist(); super.onPause() }
    override fun onDestroy() { main.removeCallbacksAndMessages(null); worker.shutdownNow(); super.onDestroy() }
    private fun dp(value: Int) = TuixtTheme.dp(this, value.toFloat())
}
