package ohi.andre.consolelauncher.managers.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.widget.EditText
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.*
import ohi.andre.consolelauncher.irc.IrcActivity
import ohi.andre.consolelauncher.irc.IrcMessage
import ohi.andre.consolelauncher.irc.IrcNavigation
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetPreview
import ohi.andre.consolelauncher.managers.modules.ModuleLibrary
import ohi.andre.consolelauncher.managers.modules.ModuleLibraryPane
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

/** Opt-in suite. Every result is evidence from a real launcher window, never a UI mock. */
class PaletteReadabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val assets = instrumentation.context.assets
    private val registry by lazy { JSONObject(assets.open("terminal-colors/coverage.json").bufferedReader().readText()) }
    private val catalog by lazy { JSONObject(assets.open("terminal-colors/catalog.json").bufferedReader().readText()).getJSONArray("themes") }
    private val output by lazy { File(context.getExternalFilesDir(null), "palette-readability").apply { mkdirs() } }
    private val emulator get() = android.os.Build.HARDWARE in listOf("goldfish", "ranchu")
    private val widget = "readability_fixture"
    private var rolesAtColor: Map<Int, List<String>> = emptyMap()
    private var calculatorBackdrop: Activity? = null
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun settle() {
        val frames = java.util.concurrent.CountDownLatch(1)
        main { android.view.Choreographer.getInstance().postFrameCallback {
            android.view.Choreographer.getInstance().postFrameCallback { frames.countDown() }
        } }
        check(frames.await(3, java.util.concurrent.TimeUnit.SECONDS)) { "Launcher did not draw two frames" }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun roots(activity: Activity): List<View> = if (android.os.Build.VERSION.SDK_INT >= 29)
        WindowInspector.getGlobalWindowViews().filter { root ->
            root.isShown && root.hasWindowFocus() &&
                (root === activity.window.decorView || views(root).any { it.context === activity })
        } else listOf(activity.window.decorView)
    private fun all(activity: Activity) = roots(activity).flatMap(::views)
    private fun invoke(target: Any, name: String, vararg values: Any?): Any? =
        target.javaClass.declaredMethods.first { it.name == name && it.parameterCount == values.size }
            .apply { isAccessible = true }.invoke(target, *values)
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target)
    private fun hex(value: Int) = String.format("#%08x", value)
    private fun reloadPrefs() {
        XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs(); ThemeColorResolver.clearPreview()
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun visible(view: View): Boolean = view.isShown && view.width > 0 && view.height > 0 &&
        generateSequence(view) { it.parent as? View }.all { it.alpha > 0f } && view.getGlobalVisibleRect(Rect())
    private fun stressFixture() {
        when (args.getString("configuration", "baseline")) {
            "baseline" -> Unit
            "large-text" -> check(context.resources.configuration.fontScale >= 1.49f) { "Host must set font scale before launch" }
            "landscape" -> {
                LauncherSettings.set(Behavior.orientation, "0")
                instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_FREEZE_90)
            }
            "wallpaper" -> {
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                for (x in 0..63) for (y in 0..63) bitmap.setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) Color.WHITE else Color.BLACK)
                instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.SET_WALLPAPER)
                try { android.app.WallpaperManager.getInstance(context).setBitmap(bitmap) }
                finally { instrumentation.uiAutomation.dropShellPermissionIdentity(); bitmap.recycle() }
            }
            "frames" -> {
                val frames = ohi.andre.consolelauncher.tuils.FrameManager
                frames.beginEdit(context).also { it.applyPack(frames.SPROUT_LANDS_PACK_ID); it.save() }
                frames.setEnabled(context, true)
            }
            else -> error("Unknown fixture configuration")
        }
    }

    private fun expected(slug: String, filename: String = "theme.xml"): Map<String, Int> {
        val values = linkedMapOf<String, Int>()
        assets.open("terminal-colors/downloads/$slug.retui").use { stream ->
            java.util.zip.ZipInputStream(stream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == filename) {
                        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                            .parse(zip.readBytes().inputStream()).documentElement
                        for (i in 0 until root.childNodes.length) {
                            val node = root.childNodes.item(i)
                            node.attributes?.getNamedItem("value")?.nodeValue?.let { values[node.nodeName] = Color.parseColor(it) }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        if (filename == "theme.xml") assertEquals(Theme.entries.map { it.name }.toSet(), values.keys)
        return values
    }

    private fun readback(values: Map<String, Int>) {
        Theme.entries.forEach { role -> assertEquals(role.name, values[role.name], ThemeColorResolver.color(role)) }
        assertFalse("Applying a preset disables wallpaper-derived colors", LauncherSettings.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Ui.auto_color_pick))
    }

    private fun suggestionReadback(slug: String) {
        expected(slug, "suggestions.xml").forEach { (name, value) ->
            val role = ohi.andre.consolelauncher.managers.xml.options.Suggestions.valueOf(name)
            assertEquals(name, value, XMLPrefsManager.getColor(role))
        }
    }

    private fun importApply(theme: JSONObject): String {
        val slug = theme.getString("id")
        val before = Theme.entries.associateWith { LauncherSettings.get(it) }
        val untouched = Tuils.getFolder().listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }.associateWith { it.readBytes() }
        val bytes = assets.open("terminal-colors/downloads/$slug.retui").use { it.readBytes() }
        assertEquals(theme.getString("sha256"), digest(bytes))
        val name = BackupManager.inspectPreset(bytes.inputStream(), File(context.cacheDir, "palette-inspect")).use { staged ->
            val saved = staged.save()
            assertEquals("Import must not apply colors", before, Theme.entries.associateWith { LauncherSettings.get(it) })
            untouched.forEach { (file, original) -> assertArrayEquals("Import changed ${file.name}", original, file.readBytes()) }
            saved
        }
        PresetManager.apply(name)
        reloadPrefs()
        readback(expected(slug))
        suggestionReadback(slug)
        rolesAtColor = Theme.entries.groupBy { ThemeColorResolver.color(it) }.mapValues { (_, roles) -> roles.map { it.name } }
        return name
    }

    private fun open(surface: JSONObject, slug: String): Activity {
        val cls = Class.forName("ohi.andre.consolelauncher" + surface.getString("activity"))
        main {
            // A singleTask activity must finish before startActivitySync can await a new instance.
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filter { it.javaClass == cls }.forEach { it.finish() }
        }
        if (surface.getString("id") == "calculator-enhancement") {
            calculatorBackdrop = instrumentation.startActivitySync(Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
                .putExtra("topic", "calculator-help").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            main {
                calculatorBackdrop!!.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                calculatorBackdrop!!.window.decorView.setBackgroundColor(ThemeColorResolver.color(Theme.background_color))
            }
            settle()
        }
        val intent = Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val variant = surface.getString("variant")
        if (variant == "settings") intent.putExtra(TuixtActivity.PATH,
            File(Tuils.getFolder(), surface.getString("root").lowercase() + ".xml").absolutePath)
        if (surface.getString("activity").startsWith(".notes.")) RetuiThemeBridge.putLauncherThemeExtras(intent, context)
        if (surface.getString("id") == "widget-editor" || surface.getString("id") == "widget-config") intent.putExtra("widget_id", widget)
        if (surface.getString("id") == "preset-import") {
            intent.action = Intent.ACTION_VIEW
            intent.setDataAndType(Uri.parse("content://${instrumentation.context.packageName}.palette-fixtures/$slug.retui"), BackupManager.PRESET_MIME)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (surface.getString("activity") == ".irc.IrcActivity") intent.putExtra("irc_offline_fixture", true)
        if (surface.getString("activity") == ".managers.onboarding.StartupMenuActivity") {
            intent.putExtra("startup_preview", true)
            context.getSharedPreferences("retui_startup_preview", 0).edit().clear().putBoolean("active", true)
                .putInt("page", if (variant == "config") 1 else if (variant == "done") 2 else 0).commit()
        }
        if (variant == "document" || variant == "help") intent.putExtra("topic", "calculator-help")
        var activity: Activity? = null
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        main { context.startActivity(intent) }
        do {
            main { activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { it.javaClass == cls && !it.isFinishing } }
            if (activity == null) Thread.sleep(15)
        } while (activity == null && android.os.SystemClock.uptimeMillis() < deadline)
        val opened = checkNotNull(activity) { "Registered activity did not resume: ${surface.getString("id")}" }
        return prepare(opened, surface, slug)
    }

    private fun prepare(activity: Activity, surface: JSONObject, slug: String): Activity {
        val variant = surface.getString("variant")
        main { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        settle()
        check(!activity.isFinishing) { "Surface finished before rendering" }
        try {
        main {
            if (activity is LauncherActivity) {
                val ui = checkNotNull(activity.uiManager)
                when (variant) {
                    "normal" -> {
                        ui.setInput("palette sample")
                        ui.setOutput("Readability fixture\nPlain output · https://example.test\nUnicode: café → 😀", ohi.andre.consolelauncher.managers.TerminalManager.CATEGORY_OUTPUT)
                        for ((role, text) in listOf(Theme.error_text_color to "Synthetic error", Theme.warning_text_color to "Synthetic warning", Theme.success_text_color to "Synthetic success", Theme.link_text_color to "https://example.test"))
                            ui.setOutput(ThemeColorResolver.color(role), text)
                    }
                    "apps" -> ui.showAppsDrawer()
                    "widgets" -> ui.showAndroidWidgetDrawer()
                    "profile" -> ui.openProfileSurface()
                    "modules" -> invoke(ui, "showTextModule", "palette fixture", "Sample module\nWarning / error / success")
                    "termux" -> {
                        check(emulator) { "Embedded Termux fixture requires isolated emulator" }
                        ui.openTermuxConsole("")
                        val text = activity.findViewById<TextView>(R.id.termux_output)
                        text?.text = "Embedded terminal fixture\n\u001b[31mRed\u001b[0m · cached draft"
                    }
                    "music" -> invoke(ui, "showMusicModule")
                    "notifications" -> invoke(ui, "showNotificationsModule")
                    "lua" -> LuaWidgetPreview.show(activity, widget, LuaWidgetManager.readScript(widget).orEmpty())
                    "library" -> ModuleLibraryPane.show(activity, ModuleLibrary.Entry("fixture", widget, "Palette fixture", "Synthetic module", "https://example.test/fixture.lua", "0".repeat(64), "1", 1, "Sample module preview", "Readability fixture", "Re:TUI tests", "MIT", "None")) { _, _ -> }
                }
            }
            if (activity is IrcActivity) {
                activity.session.state.nickname = "fixture"
                activity.session.state.status("Synthetic status notice · https://example.test")
                activity.session.state.receive(IrcMessage.parse(":fixture!u@localhost JOIN #readability")!!)
                activity.session.state.switch("#readability")
                activity.session.state.receive(IrcMessage.parse(":alice!u@localhost PRIVMSG #readability :Hello fixture · café 😀")!!)
                invoke(activity, "render")
                when (variant) {
                    "menu" -> invoke(activity, "openNavigation", IrcNavigation.Page.HELP)
                    "account" -> invoke(activity, "openNavigation", IrcNavigation.Page.ACCOUNT)
                    "error" -> activity.findViewById<EditText>(R.id.irc_input).error = "Synthetic validation error"
                }
            }
            if (surface.getString("activity") == ".calculator.CalculatorActivity") {
                val input = activity.findViewById<EditText>(R.id.calculator_expression)
                input.setText(if (variant == "error") "2+" else "2 + 2")
                when (variant) {
                    "functions" -> activity.findViewById<View>(R.id.calculator_functions_tab).performClick()
                    "help" -> activity.findViewById<View>(R.id.calculator_help_tab).performClick()
                    "interpretation" -> Unit
                    "enhancement" -> {
                        check(ohi.andre.consolelauncher.calculator.CalculatorPackStore.installedVersion(activity) == null) { "Inline enhancement capture requires an uninstalled optional calculator pack" }
                        check(ohi.andre.consolelauncher.calculator.CalculatorEngine.supported()) { "Inline enhancement capture requires supported WebView" }
                        invoke(activity, "showEnhancement")
                    }
                    else -> activity.findViewById<View>(R.id.calculator_equals).performClick()
                }
            }
            if (surface.getString("activity") == ".managers.termux.TermuxWorkspaceActivity") {
                check(emulator) { "Workspace fixture requires isolated emulator" }
                runCatching { invoke(activity, "dismissHelperPrompt") }
                if (variant.startsWith("split-")) {
                    val selection = field(activity, "selection") as ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceState
                    selection.layout = if (variant == "split-horizontal") ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceState.Layout.LEFT_RIGHT
                        else ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceState.Layout.TOP_BOTTOM
                    invoke(activity, "applyLayout"); invoke(activity, "focusTile", 1)
                }
                val grids = field(activity, "grids") as Array<*>
                for (grid in grids.filterIsInstance<TerminalGridView>()) {
                    grid.setFrame(terminalFixture(), 64, 20, 2, 2)
                }
            }
            if (variant == "search") all(activity).filterIsInstance<EditText>().firstOrNull()?.setText("calc")
            if (activity is ThemerActivity) {
                if (surface.has("section")) invoke(activity, "openSection", surface.getString("section"), false)
                if (surface.has("dialog")) invoke(activity, surface.getString("dialog"))
            }
        }
        settle()
        if (surface.getString("id") == "wallpaper-gallery") {
            val deadline = android.os.SystemClock.uptimeMillis() + 15000
            var loaded = false
            do {
                main { loaded = all(activity).any { it.tag?.toString()?.startsWith("wallpaper-download-") == true || it.tag?.toString()?.startsWith("wallpaper-use-") == true } }
                if (!loaded) Thread.sleep(30)
            } while (!loaded && android.os.SystemClock.uptimeMillis() < deadline)
            check(loaded) { "Wallpaper gallery catalogue did not render native cards; loading/error alone is not coverage" }
            main {
                val actions = all(activity).filterIsInstance<TextView>().filter { it.tag?.toString()?.startsWith("wallpaper-download-") == true || it.tag?.toString()?.startsWith("wallpaper-use-") == true }
                assertTrue("Native gallery actions rendered", actions.isNotEmpty())
                actions.forEach { action ->
                    assertTrue("Accessible gallery action height", action.height >= (48 * context.resources.displayMetrics.density).toInt())
                    for ((state, role) in listOf(intArrayOf() to Theme.button_text_color, intArrayOf(android.R.attr.state_pressed) to Theme.pressed_text_color, intArrayOf(-android.R.attr.state_enabled) to Theme.disabled_text_color)) {
                        assertEquals("Gallery action state follows $role", SettingsColorSafety.color(role, ThemeColorResolver::color), action.textColors.getColorForState(state, 0))
                    }
                }
            }
            settle()
        }
        if (surface.getString("id") in listOf("calculator-enhancement", "calculator-pack")) main {
            val inline = surface.getString("id") == "calculator-enhancement"
            val panel = field(activity, if (inline) "enginePanel" else "panel")!!
            val panelView = field(panel, "view") as View
            check(panelView.isShown) { "The real calculator enhancement panel must be visible" }
            val expectedText = if (inline) ThemeColorResolver.color(Theme.workspace_panel_text_color)
                else ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor()
            for (name in listOf("title", "status")) {
                val text = field(panel, name) as TextView
                assertTrue("Calculator $name needs readable content", text.text.isNotBlank())
                assertEquals("Calculator $name follows the active palette", expectedText, text.currentTextColor)
            }
            val actions = all(activity).filterIsInstance<TextView>().filter { it.isShown && it.tag?.toString()?.startsWith("calculator-enhancement-action-") == true }
            assertTrue("Capture real pack actions", actions.isNotEmpty())
            for (action in actions) {
                val normal = if (action.tag == "calculator-enhancement-action-${R.string.optional_calculator_download}") Theme.primary_button_text_color else Theme.button_text_color
                for ((state, role) in listOf(intArrayOf(android.R.attr.state_enabled) to normal,
                    intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed) to Theme.pressed_text_color,
                    intArrayOf(-android.R.attr.state_enabled) to Theme.disabled_text_color)) {
                    assertEquals("Pack action state follows $role", SettingsColorSafety.color(role, ThemeColorResolver::color), action.textColors.getColorForState(state, 0))
                }
                assertNotNull("Pack action has its themed drawable", action.background)
            }
        }
        if (activity is ohi.andre.consolelauncher.calculator.CalculatorActivity && variant in listOf("normal", "error", "interpretation")) {
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            var rendered = false
            do {
                main {
                    rendered = when (variant) {
                        "interpretation" -> activity.findViewById<TextView>(R.id.calculator_preview).text.contains("4")
                        "error" -> field(activity, "request") == null && activity.findViewById<TextView>(R.id.calculator_result).text.contains("calc> 2+")
                        else -> field(activity, "request") == null && field(activity, "answer") == "4"
                    }
                }
                if (!rendered) Thread.sleep(15)
            } while (!rendered && android.os.SystemClock.uptimeMillis() < deadline)
            check(rendered) { "Calculator fixture result/interpretation did not render" }
            settle()
        }
        return activity
        } catch (error: Throwable) {
            main { activity.finish() }
            throw error
        }
    }

    private fun terminalFixture(): String = buildString {
        append("ANSI palette fixture\r\n")
        for (base in listOf(30, 90)) { for (i in 0..7) append("\u001b[${base + i}mC$i \u001b[0m"); append("\r\n") }
        append("\u001b[7mReverse\u001b[0m \u001b[1mBold\u001b[0m\r\n")
        append("\u001b[38;2;1;2;3mTruecolor\u001b[0m \u001b[38;5;196m256 color\u001b[0m")
    }

    private fun background(view: View): Int {
        val ancestors = generateSequence(view) { it.parent as? View }.toList().reversed()
        var result = ThemeColorResolver.color(Theme.background_color)
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        for (ancestor in ancestors) {
            val drawable = ancestor.background ?: continue
            if (ancestor.width == 0 || ancestor.height == 0) continue
            bitmap.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bitmap)
            canvas.translate(-ancestor.width / 2f, -ancestor.height / 2f)
            val bounds = Rect(drawable.bounds)
            drawable.setBounds(0, 0, ancestor.width, ancestor.height)
            drawable.draw(canvas); drawable.bounds = bounds
            result = ColorUtils.compositeColors(bitmap.getPixel(0, 0), result)
        }
        bitmap.recycle()
        return result
    }

    private fun sample(view: View, foreground: Int, background: Int, text: String, state: String, kind: String, threshold: Double): JSONObject {
        var opacity = 1f
        generateSequence(view) { it.parent as? View }.forEach { opacity *= it.alpha }
        val effective = ColorUtils.setAlphaComponent(foreground, (Color.alpha(foreground) * opacity).toInt().coerceIn(0, 255))
        val composited = ColorUtils.compositeColors(effective, background)
        val ratio = ColorUtils.calculateContrast(composited, ColorUtils.setAlphaComponent(background, 255))
        val bounds = Rect(); view.getGlobalVisibleRect(bounds)
        val id = runCatching { context.resources.getResourceEntryName(view.id) }.getOrDefault(view.tag?.toString() ?: view.javaClass.simpleName)
        val roleNames = rolesAtColor[foreground].orEmpty()
        val expectedRole = when {
            kind == "cursor" && id == "setting_input" -> Theme.focus_color
            kind == "cursor" && id in listOf("input_view", "calculator_expression", "offline_help_input", "irc_input", "android_widget_input", "termux_input", "file_input") -> Theme.cursor_color
            kind == "hint" && id in listOf("input_view", "irc_input", "android_widget_input") -> Theme.field_hint_color
            kind == "hint" && id == "setting_input" -> Theme.settings_input_hint_color
            kind == "text" && id == "calculator_expression" -> Theme.workspace_input_text_color
            kind == "text" && id == "irc_input" -> Theme.workspace_input_text_color
            kind == "text" && id == "setting_input" -> Theme.settings_input_text_color
            else -> null
        }
        val mismatch = expectedRole != null && foreground != ThemeColorResolver.color(expectedRole) && view.isEnabled && state !in listOf("selected", "pressed", "disabled")
        return JSONObject().put("id", id).put("text", text.take(140)).put("kind", kind).put("state", state)
            .put("foreground", hex(foreground)).put("background", hex(background)).put("composited", hex(composited))
            .put("ratio", ratio).put("threshold", threshold).put("roles", JSONArray(roleNames.take(4))).put("roleCandidateCount", roleNames.size)
            .put("application", if (roleNames.isEmpty()) "unmatched color; review ownership" else if (expectedRole != null) "matches assigned role" else "matches palette color; review role ownership")
            .put("expectedRole", expectedRole?.name ?: JSONObject.NULL)
            .put("expectedColor", expectedRole?.let { hex(ThemeColorResolver.color(it)) } ?: JSONObject.NULL)
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .put("status", if (!view.isEnabled || state == "disabled") "manual review required" else if (ratio < threshold || mismatch) "fail" else "pass")
            .put("reason", if (mismatch) "Rendered color differs from the assigned palette role" else "")
    }

    private fun samples(activity: Activity, state: String, only: List<View>? = null): JSONArray {
        val result = JSONArray()
        for (view in (only ?: all(activity)).distinct().filter(::visible)) {
            val bg = background(view)
            if (view is android.webkit.WebView && activity is ohi.andre.consolelauncher.wallpaper.WallpaperGalleryActivity) {
                result.put(JSONObject().put("id", view.contentDescription).put("kind", "animated-wallpaper-preview").put("status", "manual review required").put("reason", "Remote GIF artwork is content-authored; inspect the actual preview in the screenshot"))
            }
            if (view is TextView) {
                if (view.text.isNotEmpty()) {
                    val text = view.text
                    if (text is Spanned) {
                        val spans = text.getSpans(0, text.length, android.text.style.CharacterStyle::class.java)
                        val boundaries = (listOf(0, text.length) + spans.flatMap { listOf(text.getSpanStart(it), text.getSpanEnd(it)) }).distinct().sorted()
                        for ((start, end) in boundaries.zipWithNext()) {
                            if (start == end || text.subSequence(start, end).isBlank()) continue
                            val paint = android.text.TextPaint(view.paint).apply { color = view.currentTextColor; bgColor = 0 }
                            text.getSpans(start, end, android.text.style.CharacterStyle::class.java).forEach { it.updateDrawState(paint) }
                            result.put(sample(view, paint.color, if (paint.bgColor != 0) ColorUtils.compositeColors(paint.bgColor, bg) else bg,
                                text.subSequence(start, end).toString(), state, "span", 4.5))
                        }
                    } else result.put(sample(view, view.currentTextColor, bg, text.toString(), state, "text", 4.5))
                }
                if (view is EditText && !view.hint.isNullOrEmpty()) result.put(sample(view, view.currentHintTextColor, bg, view.hint.toString(), state, "hint", 4.5))
                if (view is EditText && view.hasFocus() && view.isCursorVisible && view.selectionStart == view.selectionEnd && android.os.Build.VERSION.SDK_INT >= 29) {
                    view.textCursorDrawable?.let { cursor ->
                        val width = cursor.intrinsicWidth.coerceAtLeast(16)
                        val height = cursor.intrinsicHeight.coerceAtLeast(32)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val original = Rect(cursor.bounds)
                        cursor.setBounds(0, 0, width, height); cursor.draw(Canvas(bitmap)); cursor.bounds = original
                        val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                        val color = pixels.maxBy { Color.alpha(it) }; bitmap.recycle()
                        if (Color.alpha(color) > 0) result.put(sample(view, color, bg, "Input cursor", state, "cursor", 3.0)
                            .put("drawableClass", cursor.javaClass.name)
                            .put("drawableFill", (cursor as? android.graphics.drawable.GradientDrawable)?.color?.defaultColor?.let(::hex) ?: JSONObject.NULL)
                            .put("drawableFilter", cursor.colorFilter?.javaClass?.name ?: JSONObject.NULL))
                        else result.put(JSONObject().put("id", view.id).put("kind", "cursor").put("status", "manual review required").put("reason", "Cursor drawable produced no raster sample"))
                    }
                }
                if (view is EditText && !view.error.isNullOrEmpty()) result.put(JSONObject().put("id", "${view.id}-error").put("text", view.error.toString()).put("kind", "platform-error-popup").put("status", "manual review required"))
            }
            if (view is ImageView) {
                val tint = view.imageTintList?.let { it.getColorForState(view.drawableState, it.defaultColor) }
                if (tint != null) result.put(sample(view, tint, bg, view.contentDescription?.toString().orEmpty(), state, "icon", 3.0))
                else if (view.isClickable) result.put(JSONObject().put("id", view.id).put("kind", "untinted-icon").put("status", "manual review required"))
            }
            if (view is SeekBar) {
                for ((kind, colors) in listOf("slider-progress" to view.progressTintList, "slider-thumb" to view.thumbTintList)) {
                    if (colors != null) result.put(sample(view, colors.getColorForState(view.drawableState, colors.defaultColor), bg, kind, state, kind, 3.0))
                }
            }
            if (view is TerminalGridView) {
                val cells = field(view, "cells") as Array<*>
                val cursor = (field(view, "cursorY") as Int?)?.let { row -> (field(view, "cursorX") as Int?)?.let { col -> row * (field(view, "cols") as Int) + col } }
                if (cursor != null) result.put(sample(view, field(view, "terminalForeground") as Int, bg, "Terminal cursor", state, "terminal-cursor", 3.0))
                for ((index, cell) in cells.withIndex()) {
                    if (cell == null) continue
                    val text = field(cell, "text").toString()
                    if (text.isBlank()) continue
                    val fg = if (index == cursor) field(view, "terminalBackground") as Int else field(cell, "foreground") as Int
                    val cellBackground = if (index == cursor) field(view, "terminalForeground") as Int else field(cell, "background") as Int?
                    val explicit = text.contains("Truecolor") || fg == Color.rgb(1, 2, 3) || fg == Color.rgb(255, 0, 0)
                    val s = sample(view, fg, cellBackground ?: bg, text, state, "terminal-cell", 4.5)
                    if (explicit) s.put("status", "manual review required").put("reason", "Content-authored truecolor/256 color")
                    result.put(s)
                }
            }
        }
        return result
    }

    private fun capture(activity: Activity, theme: JSONObject, surface: JSONObject, suffix: String = "normal", nativeView: Boolean = false): JSONObject {
        val started = android.os.SystemClock.uptimeMillis()
        var focused = false
        var measured = JSONArray()
        do {
            main { focused = roots(activity).isNotEmpty(); if (focused) measured = samples(activity, suffix) }
            if (!focused || measured.length() == 0) Thread.sleep(15)
        } while ((!focused || measured.length() == 0) && android.os.SystemClock.uptimeMillis() - started < 2000)
        check(focused) { "No focused launcher window; another app/system dialog obscures this surface" }
        val id = theme.getString("id") + "/" + surface.getString("id") + "/" + suffix
        val file = File(output, "$id.png").apply { parentFile!!.mkdirs() }
        var bitmap: Bitmap? = null
        val origin = IntArray(2)
        if (nativeView) main {
            val root = roots(activity).last()
            root.getLocationOnScreen(origin)
            bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap!!))
        } else {
            val committed = java.util.concurrent.CountDownLatch(1)
            var committedRoot: View? = null
            main {
                val decor = roots(activity).last()
                committedRoot = decor
                if (android.os.Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated) {
                    decor.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                    decor.invalidate()
                } else decor.postOnAnimation { committed.countDown() }
            }
            check(committed.await(3, java.util.concurrent.TimeUnit.SECONDS)) { "Target window did not submit a frame" }
            // Choreographer callbacks precede Surface visibility/Activity transitions on some OEMs.
            android.os.SystemClock.sleep(500)
            main { check(committedRoot?.hasWindowFocus() == true) { "Target window lost focus before screenshot" } }
            bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        }
        file.outputStream().use { assertTrue(bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it)) }; bitmap!!.recycle()
        val status = if (measured.length() == 0) "blocked" else if ((0 until measured.length()).any { measured.getJSONObject(it).optString("status") == "fail" }) "fail" else "manual review required"
        return JSONObject().put("theme", theme.getString("id")).put("surface", surface.getString("id")).put("state", suffix)
            .put("status", status).put("screenshot", "$id.png").put("samples", measured)
            .put("reason", if (measured.length() == 0) "No visible measured content in the focused launcher window" else "")
            .put("screenshotOrigin", JSONArray(origin.toList()))
            .put("device", android.os.Build.MODEL).put("deviceKind", if (emulator) "emulator" else "phone")
            .put("packageSha256", theme.getString("sha256")).put("build", args.getString("buildSha256", "unknown"))
            .put("configuration", args.getString("configuration", "baseline"))
            .put("fontScale", context.resources.configuration.fontScale)
            .put("orientation", context.resources.configuration.orientation)
            .put("captureMethod", if (nativeView) "Actual window View.draw" else "UiAutomation screenshot")
            .put("captureMillis", android.os.SystemClock.uptimeMillis() - started)
    }

    @Test fun renderPaletteBatch() {
        check(args.getString("readabilityEnabled") == "true") { "Run through scripts/terminal_colors/run.py" }
        check(emulator || args.getString("readabilityPhone") == "true") { "Phone requires explicit isolated-fixture selection" }
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val originals = Tuils.getFolder().listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }.associateWith { it.readBytes() }
        val prefs = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty().filter { it.extension == "xml" }
            .associate { it.nameWithoutExtension to context.getSharedPreferences(it.nameWithoutExtension, 0).all.toMap() }
        val imported = mutableListOf<String>()
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.action == "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") android.app.Instrumentation.ActivityResult(0, null) else null
        }
        instrumentation.addMonitor(monitor)
        val configuration = args.getString("configuration", "baseline")
        check(emulator || configuration == "baseline")
        val frameBackup = File(context.cacheDir, "readability-frame-backup")
        if (configuration == "frames") ohi.andre.consolelauncher.tuils.FrameManager.copyStateTo(context, frameBackup)
        val widgetFolder = LuaWidgetManager.widgetDir(widget)
        check(!widgetFolder.exists()) { "Fixture name already exists" }
        val selected = args.getString("themes")?.split(',')?.toSet()
        val surfaces = args.getString("surfaces")?.split(',')?.toSet()
        var activity: Activity? = null
        try {
            context.getSharedPreferences("retui_startup", 0).edit().putBoolean("complete", true).putBoolean("active", false).commit()
            ChangelogManager.markSeen(context)
            ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
            LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Notifications.show_notifications, "false")
            LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Notifications.terminal_notifications, "false")
            LauncherSettings.set(Behavior.tui_notification, "false")
            LuaWidgetManager.save(widget, "Readability fixture", """
                -- retui = "1"
                function on_load() ui:show_text("Sample Lua module · café 😀") end
                function on_config() ui:show_config({ title="Sample config", fields={ { id="sample", label="Sample setting", type="text", default="fixture" } } }) end
            """.trimIndent())
            LuaWidgetManager.approve(widget)
            LauncherSettings.set(Behavior.auto_show_keyboard, "false")
            LauncherSettings.set(Behavior.breath_out, "false")
            LauncherSettings.set(Behavior.auto_hide_output, "false")
            LauncherSettings.set(ohi.andre.consolelauncher.managers.xml.options.Ui.show_weather, "false")
            stressFixture()
            for (i in 0 until catalog.length()) {
                val theme = catalog.getJSONObject(i)
                if (selected != null && theme.getString("id") !in selected) continue
                imported.add(importApply(theme))
                val results = JSONArray()
                val definitions = registry.getJSONArray("surfaces")
                for (j in 0 until definitions.length()) {
                    val surface = definitions.getJSONObject(j)
                    if (surfaces != null && surface.getString("id") !in surfaces) continue
                    val sceneStart = android.os.SystemClock.uptimeMillis()
                    val firstResult = results.length()
                    instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Opening ${theme.getString("id")}/${surface.getString("id")}\n") })
                    try {
                        activity = open(surface, theme.getString("id"))
                        results.put(capture(activity!!, theme, surface))
                        if (surface.optString("variant") == "settings") {
                            val recycler = all(activity!!).filterIsInstance<RecyclerView>().first { it.adapter is TuixtAdapter }
                            val adapter = recycler.adapter as TuixtAdapter
                            main { adapter.updateRows((field(adapter, "rows") as List<*>).filterIsInstance<TuixtAdapter.SettingsRow>().toMutableList(), true) }
                            settle()
                            val targets = linkedMapOf<String, Int>()
                            main {
                                val rows = (field(adapter, "visibleRows") as List<*>).filterIsInstance<TuixtAdapter.SettingsRow>()
                                rows.forEachIndexed { index, row -> if (!row.sectionHeader) targets.putIfAbsent("${row.section}/${row.item?.type()}", index) }
                            }
                            for ((key, position) in targets) {
                                main { recycler.scrollToPosition(position) }; settle()
                                results.put(capture(activity!!, theme, surface, "rows-$position", nativeView = true).put("settingsSectionAndType", key))
                            }
                            if (surface.getString("id") == "settings-ui") {
                                val preview = all(activity!!).filterIsInstance<RecyclerView>().firstOrNull { it.adapter !is TuixtAdapter }
                                if (preview != null) for (page in 1..3) {
                                    main { preview.scrollToPosition(page) }; settle()
                                    results.put(capture(activity!!, theme, surface, "preview-$page", nativeView = true))
                                }
                            }
                        }
                        // Inspect real drawable/text states without invoking potentially destructive actions.
                        val controls = all(activity!!).filterIsInstance<TextView>().filter { visible(it) && it.isClickable }.take(8)
                        val states = JSONArray()
                        for (state in listOf("focused", "pressed", "selected", "disabled")) {
                            main {
                                val old = controls.map { Triple(it.isPressed, it.isSelected, it.isEnabled) }
                                controls.forEach { when (state) { "focused" -> it.requestFocus(); "pressed" -> it.isPressed = true; "selected" -> it.isSelected = true; "disabled" -> it.isEnabled = false } }
                                val measured = samples(activity!!, state, controls)
                                val crops = JSONArray()
                                controls.distinctBy { listOf(it.javaClass.name, it.currentTextColor, background(it), it.background?.current?.javaClass?.name) }.forEachIndexed { index, view ->
                                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap); canvas.drawColor(background(view)); view.draw(canvas)
                                    val relative = "${theme.getString("id")}/${surface.getString("id")}/control-$state-$index.png"
                                    File(output, relative).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                                    crops.put(relative)
                                }
                                states.put(JSONObject().put("state", state).put("samples", measured).put("crops", crops).put("status", if (controls.isEmpty()) "not applicable" else "measured"))
                                controls.zip(old).forEach { (view, previous) -> view.isPressed = previous.first; view.isSelected = previous.second; view.isEnabled = previous.third }
                            }
                        }
                        results.getJSONObject(results.length() - 1).put("controlStates", states)
                            .put("sceneMillis", android.os.SystemClock.uptimeMillis() - sceneStart)
                        val input = all(activity!!).filterIsInstance<EditText>().firstOrNull { visible(it) && it.isEnabled }
                        if (input != null && surface.optString("variant") != "settings") {
                            var text: CharSequence = ""; var error: CharSequence? = null; var selection = 0 to 0
                            main { text = android.text.SpannableString(input.text); error = input.error; selection = input.selectionStart to input.selectionEnd; input.showSoftInputOnFocus = false }
                            try {
                                main { input.requestFocus(); input.setText(""); input.error = null }; settle()
                                results.put(capture(activity!!, theme, surface, "empty-input", nativeView = true))
                                main { input.setText("Synthetic selection draft"); input.setSelection(2, 10) }; settle()
                                results.put(capture(activity!!, theme, surface, "selected-input", nativeView = true))
                                main { input.error = "Synthetic validation error" }; settle()
                                results.put(capture(activity!!, theme, surface, "error-input"))
                            } finally {
                                main { input.setText(text); input.setSelection(selection.first.coerceAtLeast(0), selection.second.coerceAtLeast(0)); input.error = error }
                            }
                        }
                    } catch (error: Throwable) {
                        results.put(JSONObject().put("theme", theme.getString("id")).put("surface", surface.getString("id"))
                            .put("build", args.getString("buildSha256", "unknown")).put("packageSha256", theme.getString("sha256"))
                            .put("status", "blocked").put("reason", error.stackTraceToString().take(5000)))
                    } finally {
                        activity?.let { a -> main { a.finish() } }; activity = null
                        calculatorBackdrop?.let { a -> main { a.finish() } }; calculatorBackdrop = null
                        val scene = JSONArray()
                        for (index in firstResult until results.length()) scene.put(results.getJSONObject(index))
                        File(output, "${theme.getString("id")}/${surface.getString("id")}/evidence.json")
                            .apply { parentFile!!.mkdirs() }.writeText(scene.toString())
                    }
                }
                reloadPrefs()
                readback(expected(theme.getString("id")))
                suggestionReadback(theme.getString("id"))
                val record = JSONObject().put("theme", theme.getString("id")).put("packageSha256", theme.getString("sha256"))
                    .put("build", args.getString("buildSha256", "unknown")).put("importWithoutApplication", "pass")
                    .put("applicationReadback", "pass").put("reloadReadback", "pass").put("processRestart", "untested")
                    .put("results", results).put("roleAliasesByColor", JSONObject(rolesAtColor.mapKeys { hex(it.key) }))
                File(output, theme.getString("id") + ".json").writeText(record.toString())
                instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "Palette ${theme.getString("id")}: ${results.length()} captures\n") })
            }
        } finally {
            activity?.let { main { it.finish() } }
            val keep = emulator && args.getString("keepPalette") == "true"
            originals.forEach { (file, bytes) -> if (!keep || file.name !in listOf("theme.xml", "suggestions.xml")) file.writeBytes(bytes) }
            prefs.forEach { (name, values) ->
                val edit = context.getSharedPreferences(name, 0).edit().clear()
                values.forEach { (key, value) -> when (value) {
                    is String -> edit.putString(key, value); is Int -> edit.putInt(key, value); is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value); is Boolean -> edit.putBoolean(key, value)
                    is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
                } }; edit.commit()
            }
            File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
                .filter { it.extension == "xml" && it.nameWithoutExtension !in prefs.keys }
                .forEach { context.deleteSharedPreferences(it.nameWithoutExtension) }
            imported.forEach { File(PresetManager.presetsDir, it).deleteRecursively() }
            widgetFolder.deleteRecursively()
            instrumentation.removeMonitor(monitor)
            if (configuration == "landscape") instrumentation.uiAutomation.setRotation(android.app.UiAutomation.ROTATION_UNFREEZE)
            if (configuration == "frames") { ohi.andre.consolelauncher.tuils.FrameManager.applyStateFrom(context, frameBackup); frameBackup.deleteRecursively() }
            reloadPrefs()
        }
    }

    @Test fun restartReadback() {
        check(args.getString("readabilityEnabled") == "true" && emulator)
        Tuils.init(context); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val slug = checkNotNull(args.getString("themes"))
        readback(expected(slug))
        suggestionReadback(slug)
        val path = File(output, "$slug.json")
        val record = JSONObject(path.readText()).put("processRestart", "pass")
        path.writeText(record.toString())
    }
}
