package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.max

data class RetuiTheme(
    val bg: Int = Color.BLACK,
    val text: Int = Color.rgb(255, 247, 225),
    val border: Int = Color.rgb(255, 247, 225),
    val terminalBg: Int = Color.BLACK,
    val panel: Int = Color.BLACK,
    val panelText: Int = text,
    val panelBorder: Int = border,
    val header: Int = panel,
    val headerText: Int = panelText,
    val button: Int = panel,
    val buttonText: Int = panelText,
    val buttonBorder: Int = panelBorder,
    val inputBg: Int = panel,
    val inputText: Int = panelText,
    val outputBg: Int = panel,
    val outputText: Int = panelText,
    val outputBorder: Int = panelBorder,
    val directoryText: Int = panelText,
    val selectionBg: Int = button,
    val selectionText: Int = buttonText,
    val radius: Int = 4,
    val headerRadius: Int = radius,
    val outputRadius: Int = radius,
    val size: Int = 16,
    val inputSize: Int = size,
    val headerSize: Int = size + 4,
    val outputHeaderSize: Int = headerSize,
    val topMargin: Int = 8,
    val displayMargins: String = "0,0,0,0",
    val displayBottomMargins: String = "0,0,0,0",
    val dashed: Boolean = false,
    val dash: Int = 8,
    val gap: Int = 6,
    val stroke: Float = 2f,
    val cyberdeck: Boolean = false,
    val crt: Boolean = false,
    val vignette: Boolean = true,
    val fontName: String = "monospace",
    val fontPath: String? = null,
    val frame: LauncherFrameAsset? = null
) {
    companion object {
        private const val PREFS = "remember-theme"
        private const val USE_LAUNCHER = "useLauncherAppearance"
        private const val IMPORTED_FONT_LABEL = "importedFontLabel"
        private const val FONT_SCALE_PERCENT = "fontScalePercent"

        fun receive(activity: Activity, intent: Intent?): RetuiTheme {
            val accepted = shouldUseLauncherAppearance(
                launcherAppearanceEnabled(activity),
                launcherInstalled(activity)
            )
            if (accepted) {
                val extras = intent?.extras
                if (extras != null) {
                    val raw = PreviewContract.Visual.THEME_KEYS
                        .filter(extras::containsKey)
                        .associateWith { @Suppress("DEPRECATION") extras.get(it) }
                    persist(activity, CanonicalAppearance.valid(raw))
                }
                LauncherFrameStore(activity).process(intent, true)
            }
            return load(activity, accepted)
        }

        fun launcherAppearanceEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, 0).getBoolean(USE_LAUNCHER, true)

        fun setLauncherAppearanceEnabled(context: Context, enabled: Boolean): Boolean =
            context.getSharedPreferences(PREFS, 0).edit().putBoolean(USE_LAUNCHER, enabled).commit()

        fun fontScalePercent(context: Context): Int =
            context.getSharedPreferences(PREFS, 0).getInt(FONT_SCALE_PERCENT, 100).coerceIn(75, 150)

        fun setFontScalePercent(context: Context, percent: Int): Boolean =
            context.getSharedPreferences(PREFS, 0).edit()
                .putInt(FONT_SCALE_PERCENT, percent.coerceIn(75, 150)).commit()

        fun importedFontFile(context: Context) = File(context.filesDir, "fonts/imported-font")

        fun importedFontLabel(context: Context): String? = importedFontFile(context).takeIf(File::isFile)?.let {
            context.getSharedPreferences(PREFS, 0).getString(IMPORTED_FONT_LABEL, "CUSTOM FONT")
        }

        fun setImportedFontLabel(context: Context, label: String): Boolean =
            context.getSharedPreferences(PREFS, 0).edit().putString(IMPORTED_FONT_LABEL, label.take(120)).commit()

        fun clearImportedFont(context: Context) {
            importedFontFile(context).delete()
            context.getSharedPreferences(PREFS, 0).edit().remove(IMPORTED_FONT_LABEL).apply()
        }

        private fun launcherInstalled(context: Context): Boolean = runCatching {
            context.packageManager.getApplicationInfo(PreviewContract.LAUNCHER_PACKAGE, 0)
        }.isSuccess

        private fun persist(context: Context, values: Map<String, Any>) {
            if (values.isEmpty()) return
            val editor = context.getSharedPreferences(PREFS, 0).edit()
            values.forEach { (key, value) ->
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> if (key == PreviewContract.Visual.FONT_PATH) {
                        if (File(value).let { it.isFile && it.canRead() }) editor.putString(key, value) else editor.remove(key)
                    } else editor.putString(key, value)
                }
            }
            editor.commit()
        }

        private fun load(context: Context, accepted: Boolean): RetuiTheme {
            val launcherTheme = if (!accepted) RetuiTheme() else run {
                val prefs = context.getSharedPreferences(PREFS, 0)
                val defaultText = Color.rgb(255, 247, 225)
                fun color(key: String, fallback: Int) = prefs.getInt(key, fallback)
                fun integer(key: String, fallback: Int) = prefs.getInt(key, fallback)
                val bg = color(PreviewContract.Visual.BG, Color.BLACK)
                val text = color(PreviewContract.Visual.TEXT, defaultText)
                val border = color(PreviewContract.Visual.BORDER, defaultText)
                val terminal = color(PreviewContract.Visual.TERMINAL_BG, bg)
                val panel = color(PreviewContract.Visual.PANEL_BG, terminal)
                val panelText = color(PreviewContract.Visual.PANEL_TEXT, text)
                val panelBorder = color(PreviewContract.Visual.PANEL_BORDER, border)
                val button = color(PreviewContract.Visual.BUTTON_BG, panel)
                val buttonText = color(PreviewContract.Visual.BUTTON_TEXT, panelText)
                val radius = integer(PreviewContract.Visual.MODULE_RADIUS, 4)
                val bodySize = integer(PreviewContract.Visual.BODY_TEXT_SIZE, 16)
                val storedPath = prefs.getString(PreviewContract.Visual.FONT_PATH, null)
                val fontPath = storedPath?.takeIf { File(it).let { file -> file.isFile && file.canRead() } }
                if (storedPath != null && fontPath == null) prefs.edit().remove(PreviewContract.Visual.FONT_PATH).apply()
                RetuiTheme(
                    bg = bg,
                    text = text,
                    border = border,
                    terminalBg = terminal,
                    panel = panel,
                    panelText = panelText,
                    panelBorder = panelBorder,
                    header = color(PreviewContract.Visual.HEADER_BG, panel),
                    headerText = color(PreviewContract.Visual.HEADER_TEXT, panelText),
                    button = button,
                    buttonText = buttonText,
                    buttonBorder = color(PreviewContract.Visual.BUTTON_BORDER, panelBorder),
                    inputBg = color(PreviewContract.Visual.INPUT_BG, panel),
                    inputText = color(PreviewContract.Visual.INPUT_TEXT, panelText),
                    outputBg = color(PreviewContract.Visual.OUTPUT_BG, panel),
                    outputText = color(PreviewContract.Visual.OUTPUT_TEXT, panelText),
                    outputBorder = color(PreviewContract.Visual.OUTPUT_BORDER, panelBorder),
                    directoryText = color(PreviewContract.Visual.DIRECTORY_TEXT, panelText),
                    selectionBg = color(PreviewContract.Visual.SELECTION_BG, button),
                    selectionText = color(PreviewContract.Visual.SELECTION_TEXT, buttonText),
                    radius = radius,
                    headerRadius = integer(PreviewContract.Visual.HEADER_RADIUS, radius),
                    outputRadius = integer(PreviewContract.Visual.OUTPUT_RADIUS, radius),
                    size = bodySize,
                    inputSize = integer(PreviewContract.Visual.INPUT_TEXT_SIZE, bodySize),
                    headerSize = integer(PreviewContract.Visual.HEADER_TEXT_SIZE, bodySize + 4),
                    outputHeaderSize = integer(PreviewContract.Visual.OUTPUT_HEADER_TEXT_SIZE, bodySize + 4),
                    topMargin = integer(PreviewContract.Visual.TOP_MARGIN, 8),
                    displayMargins = prefs.getString(PreviewContract.Visual.DISPLAY_MARGIN_TOP, "0,0,0,0") ?: "0,0,0,0",
                    displayBottomMargins = prefs.getString(PreviewContract.Visual.DISPLAY_MARGIN_BOTTOM, "0,0,0,0") ?: "0,0,0,0",
                    dashed = prefs.getBoolean(PreviewContract.Visual.DASHED, false),
                    dash = integer(PreviewContract.Visual.DASH, 8),
                    gap = integer(PreviewContract.Visual.GAP, 6),
                    stroke = prefs.getFloat(PreviewContract.Visual.STROKE, 2f),
                    cyberdeck = prefs.getBoolean(PreviewContract.Visual.CYBERDECK, false),
                    crt = prefs.getBoolean(PreviewContract.Visual.CRT, false),
                    vignette = prefs.getBoolean(PreviewContract.Visual.VIGNETTE, true),
                    fontName = prefs.getString(PreviewContract.Visual.FONT_NAME, "monospace") ?: "monospace",
                    fontPath = fontPath,
                    frame = LauncherFrameStore(context).loadActive(true)
                )
            }
            val scale = fontScalePercent(context)
            val importedFont = importedFontFile(context).takeIf { it.isFile && it.canRead() }
            return launcherTheme.copy(
                size = scaledTextSize(launcherTheme.size, scale),
                inputSize = scaledTextSize(launcherTheme.inputSize, scale),
                headerSize = scaledTextSize(launcherTheme.headerSize, scale),
                outputHeaderSize = scaledTextSize(launcherTheme.outputHeaderSize, scale),
                fontPath = importedFont?.absolutePath ?: launcherTheme.fontPath
            )
        }

        internal fun parseMargins(raw: String): List<Float>? {
            val parts = raw.split(',', ';', ' ').map(String::trim).filter(String::isNotEmpty)
            if (parts.size != 4) return null
            return parts.map { it.toFloatOrNull() ?: return null }
                .takeIf { it.all(Float::isFinite) && it.all { value -> value in -100f..100f } }
        }
    }
}

internal fun shouldUseLauncherAppearance(enabled: Boolean, launcherInstalled: Boolean): Boolean =
    enabled && launcherInstalled

internal fun scaledTextSize(size: Int, percent: Int): Int =
    (size * percent.coerceIn(75, 150) / 100f).roundToInt().coerceIn(8, 72)

internal object CanonicalAppearance {
    fun valid(raw: Map<String, Any?>): Map<String, Any> = buildMap {
        raw.forEach { (key, value) ->
            when {
                key in PreviewContract.Visual.COLORS && value is Int -> put(key, value)
                key in PreviewContract.Visual.INTS && value is Int && validInt(key, value) -> put(key, value)
                key in PreviewContract.Visual.FLOATS && value is Float -> value.takeIf { it.isFinite() && it in 0.5f..32f }?.let { put(key, it) }
                key in PreviewContract.Visual.BOOLEANS && value is Boolean -> put(key, value)
                key in PreviewContract.Visual.STRINGS && value is String && validString(key, value) -> put(key, value)
            }
        }
    }

    fun accepted(accept: Boolean, stored: Map<String, Any>, raw: Map<String, Any?>): Map<String, Any> =
        if (accept) stored + valid(raw) else stored

    private fun validInt(key: String, value: Int): Boolean = when (key) {
        PreviewContract.Visual.TOP_MARGIN -> value in 0..256
        PreviewContract.Visual.INPUT_TEXT_SIZE,
        PreviewContract.Visual.HEADER_TEXT_SIZE,
        PreviewContract.Visual.BODY_TEXT_SIZE,
        PreviewContract.Visual.OUTPUT_HEADER_TEXT_SIZE -> value in 8..72
        PreviewContract.Visual.DASH, PreviewContract.Visual.GAP -> value in 0..256
        else -> value in 0..256
    }

    private fun validString(key: String, value: String): Boolean = when (key) {
        PreviewContract.Visual.DISPLAY_MARGIN_TOP,
        PreviewContract.Visual.DISPLAY_MARGIN_BOTTOM -> RetuiTheme.parseMargins(value) != null
        PreviewContract.Visual.FONT_PATH -> value.startsWith('/') && value.length <= 500
        else -> value.isNotBlank() && value.length <= 120
    }
}

fun Activity.finishRetui(root: ViewGroup, theme: RetuiTheme) {
    val edgeToEdge = root.tag == "retui_edge_to_edge"
    root.setBackgroundColor(if (edgeToEdge) Color.TRANSPARENT else theme.bg)
    val topMargins = RetuiTheme.parseMargins(theme.displayMargins) ?: listOf(0f, 0f, 0f, 0f)
    val bottomMargins = RetuiTheme.parseMargins(theme.displayBottomMargins) ?: listOf(0f, 0f, 0f, 0f)
    val margins = listOf(topMargins[0], topMargins[1], topMargins[2], bottomMargins[3])
    fun mm(value: Float) = (value * resources.displayMetrics.xdpi / 25.4f).roundToInt()
    fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.BLACK

    styleTree(root, theme)
    val screen = FrameLayout(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
    }
    val params = FrameLayout.LayoutParams(-1, -1)
    screen.addView(root, params)
    if (theme.crt) screen.foreground = CrtOverlayDrawable(this, theme.vignette).apply {
        setAccentColor(theme.border)
    }
    screen.setOnApplyWindowInsetsListener { _, insets ->
        @Suppress("DEPRECATION")
        var left = max(0, insets.systemWindowInsetLeft)
        @Suppress("DEPRECATION")
        var top = max(0, insets.systemWindowInsetTop)
        @Suppress("DEPRECATION")
        var right = max(0, insets.systemWindowInsetRight)
        @Suppress("DEPRECATION")
        var bottom = max(0, insets.systemWindowInsetBottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val safe = insets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()
            )
            val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
            left = max(left, safe.left); top = max(top, safe.top)
            right = max(right, safe.right); bottom = max(bottom, ime.bottom)
        } else {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id != 0) top = max(top, resources.getDimensionPixelSize(id))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout?.let {
            left = max(left, it.safeInsetLeft); top = max(top, it.safeInsetTop)
            right = max(right, it.safeInsetRight); bottom = max(bottom, it.safeInsetBottom)
        }
        screen.setPadding(left, 0, right, bottom)
        params.setMargins(
            dp(if (edgeToEdge) 0 else 8) + mm(margins[0]),
            top + dp(theme.topMargin) + mm(margins[1]),
            dp(if (edgeToEdge) 0 else 8) + mm(margins[2]),
            dp(if (edgeToEdge) 0 else 8) + mm(margins[3])
        )
        root.layoutParams = params
        insets
    }
    setContentView(screen)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.apply {
        hide(android.view.WindowInsets.Type.statusBars())
        systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    screen.requestApplyInsets()
}

private fun styleTree(
    view: View,
    theme: RetuiTheme,
    parentSelected: Boolean = false,
    parentOutput: Boolean = false,
    parentDirectory: Boolean = false
) {
    val document = view.tag == "retui_document"
    val selected = parentSelected || view.tag == "retui_selected"
    val output = parentOutput || view.tag == "retui_card"
    val directory = parentDirectory || view.tag == "retui_directory"
    fun chrome(color: Int, border: Int, radius: Int, interactive: Boolean = false) =
        panel(color, border, radius, theme, view.resources.displayMetrics.density, interactive)
    (view as? TextView)?.apply {
        val h1 = tag == "retui_h1"
        val h2 = tag == "retui_h2"
        val h3 = tag == "retui_h3"
        setTextColor(when {
            selected -> theme.selectionText
            h1 || h2 -> theme.headerText
            h3 || output -> theme.outputText
            directory -> theme.directoryText
            tag == "retui_accent" -> theme.buttonText
            view is Button -> theme.buttonText
            document -> theme.panelText
            view is EditText -> theme.inputText
            else -> theme.panelText
        })
        if (view is EditText) setHintTextColor(Color.argb(150, Color.red(theme.inputText), Color.green(theme.inputText), Color.blue(theme.inputText)))
        if (view is EditText && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textCursorDrawable = GradientDrawable().apply {
                setColor(if (Color.luminance(if (document) theme.panel else theme.inputBg) > 0.5) Color.BLACK else Color.WHITE)
                setSize((2 * resources.displayMetrics.density).roundToInt().coerceAtLeast(2), 1)
            }
        }
        textSize = when {
            h1 -> theme.headerSize.toFloat()
            h2 -> theme.outputHeaderSize.toFloat()
            h3 -> ((theme.size + theme.outputHeaderSize) / 2f)
            view is EditText -> theme.inputSize.toFloat()
            else -> theme.size.toFloat()
        }
        val baseTypeface = theme.fontPath?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
            ?: Typeface.create(theme.fontName, Typeface.NORMAL)
        typeface = Typeface.create(baseTypeface, if (tag == "retui_bold") Typeface.BOLD else Typeface.NORMAL)
        if (h1 || h2) background = chrome(theme.header, theme.panelBorder, theme.headerRadius)
    }
    when (view.tag) {
        "retui_shell", "retui_panel" -> view.background = chrome(theme.panel, theme.panelBorder, theme.radius)
        "retui_card" -> view.background = chrome(theme.outputBg, theme.outputBorder, theme.outputRadius, true)
        "retui_input" -> view.background = chrome(theme.inputBg, theme.panelBorder, theme.radius, true)
        "retui_toolbar" -> view.background = chrome(theme.header, theme.panelBorder, theme.headerRadius)
        "retui_accent" -> view.background = chrome(theme.button, theme.buttonBorder, theme.radius, true)
        "retui_selected" -> view.background = ColorDrawable(theme.selectionBg)
    }
    if (document) {
        // The document occupies the existing pane; only form fields get input chrome.
        view.background = null
    } else if (view is Button || view is EditText) {
        view.background = if (view is Button) chrome(theme.button, theme.buttonBorder, theme.radius, true)
        else chrome(theme.inputBg, theme.panelBorder, theme.radius, true)
    }
    if (view is ViewGroup) for (index in 0 until view.childCount) {
        styleTree(view.getChildAt(index), theme, selected, output, directory)
    }
}

fun applyRetuiStyle(view: View, theme: RetuiTheme) = styleTree(view, theme)

internal fun panel(
    color: Int,
    border: Int,
    radius: Int,
    theme: RetuiTheme,
    density: Float,
    interactive: Boolean = false
): android.graphics.drawable.Drawable {
    theme.frame?.let { return it.drawable(ColorDrawable(color), interactive) }
    return GradientDrawable().apply {
        setColor(color)
        setStroke(
            ((if (theme.cyberdeck) 3f else theme.stroke) * density).roundToInt().coerceAtLeast(1),
            border,
            if (theme.dashed) theme.dash * density else 0f,
            if (theme.dashed) theme.gap * density else 0f
        )
        cornerRadius = if (theme.cyberdeck) 0f else radius * density
    }
}

fun Activity.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(20, 20, 20, 20)
}

fun Activity.retuiHeaderTab(label: String): LinearLayout {
    fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    return LinearLayout(this).apply {
        gravity = Gravity.TOP or Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        elevation = dp(2).toFloat()
        setPadding(dp(28), 0, dp(18), 0)
        addView(TextView(this@retuiHeaderTab).apply {
            text = label
            gravity = Gravity.CENTER
            isSingleLine = true
            minWidth = dp(156)
            setPadding(dp(12), 0, dp(12), 0)
            tag = "retui_h1"
            translationY = -dp(11).toFloat()
        }, LinearLayout.LayoutParams(-2, dp(34)))
    }
}

fun Activity.retuiIdentityTab(): LinearLayout = retuiHeaderTab("RE:MEMBER")

fun Activity.retuiSectionHeader(label: String): FrameLayout {
    fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    return FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false
        addView(TextView(this@retuiSectionHeader).apply {
            text = label
            tag = "retui_h2"
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }, FrameLayout.LayoutParams(-2, -1, Gravity.START))
    }
}

fun LinearLayout.button(label: String, action: () -> Unit) = Button(context).also {
    it.text = label
    it.contentDescription = label
    it.setOnClickListener { action() }
    addView(it, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 5, 0, 5) })
}

fun LinearLayout.label(text: String, size: Float = 16f) = TextView(context).also {
    it.text = text
    it.tag = if (size >= 20f) "retui_h1" else "retui_body"
    it.setPadding(12, 12, 12, 12)
    addView(it)
}

/** Compact nested-window message panel used by unavailable / read-error surfaces. */
fun Activity.nestedMessage(
    theme: RetuiTheme,
    title: String,
    detail: String,
    actionLabel: String = "CLOSE",
    action: () -> Unit
): ViewGroup {
    fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    val root = FrameLayout(this).apply {
        clipChildren = false
        clipToPadding = false
        tag = "retui_edge_to_edge"
        setBackgroundColor(Color.TRANSPARENT)
    }
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        clipChildren = false
        clipToPadding = false
        setPadding(dp(4), dp(8), dp(4), dp(8))
        setBackgroundColor(Color.TRANSPARENT)
    }
    root.addView(shell, FrameLayout.LayoutParams(-1, -1))
    shell.addView(retuiIdentityTab().apply {
        (getChildAt(0) as? TextView)?.translationY = 0f
    }, LinearLayout.LayoutParams(-1, dp(34)))
    val window = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(22), dp(14), dp(14))
        tag = "retui_panel"
    }
    shell.addView(window, LinearLayout.LayoutParams(-1, -2).apply { topMargin = -dp(17) })
    window.addView(retuiSectionHeader(title), LinearLayout.LayoutParams(-1, dp(40)))
    window.addView(TextView(this).apply {
        text = detail
        setPadding(dp(4), dp(4), dp(4), dp(16))
    }, LinearLayout.LayoutParams(-1, -2))
    window.addView(TextView(this).apply {
        text = actionLabel
        gravity = Gravity.CENTER
        tag = "retui_accent"
        setOnClickListener { action() }
    }, LinearLayout.LayoutParams(-1, dp(44)))
    return root
}
