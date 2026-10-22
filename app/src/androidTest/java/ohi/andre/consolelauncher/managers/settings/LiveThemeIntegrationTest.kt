package ohi.andre.consolelauncher.managers.settings

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Test

class LiveThemeIntegrationTest {
    @get:org.junit.Rule val readerPreferences = ohi.andre.consolelauncher.help.HelpReaderPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun htmlFormatterColorsFollowTheCurrentTheme() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val keys = listOf(Theme.output_text_color, Theme.link_text_color)
        val original = keys.associateWith { LauncherSettings.get(it) }
        try {
            keys.forEach { LauncherSettings.set(it, "#FF445566") }
            assertEquals(0xff445566.toInt(), ohi.andre.consolelauncher.managers.HTMLExtractManager.outputColor)
            assertEquals(0xff445566.toInt(), ohi.andre.consolelauncher.managers.HTMLExtractManager.linkColor)
        } finally { original.forEach { (key, value) -> LauncherSettings.set(key, value) } }
    }
    @Test fun gridPaletteRefreshRetainsFrameStylesAndTruecolor() {
        instrumentation.runOnMainSync {
            val grid = ohi.andre.consolelauncher.managers.termux.TerminalGridView(instrumentation.targetContext)
            grid.setFrame("A\u001b[1;31mB\u001b[38;2;1;2;3mC", 80, 24, 2, 0)
            fun cells(): Array<*> = grid.javaClass.getDeclaredField("cells").apply { isAccessible = true }.get(grid) as Array<*>
            fun field(index: Int, name: String): Any? = cells()[index]!!.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(cells()[index])
            val ansi = ThemeColorResolver.ansi(1, false)
            grid.updateThemeColors(0xff445566.toInt(), 0xff000000.toInt(), 0xff778899.toInt())
            assertEquals("Theme updates must not erase the last terminal frame", "A", field(0, "text"))
            assertEquals(0xff445566.toInt(), field(0, "foreground"))
            assertEquals(ansi, field(1, "foreground")); assertEquals(true, field(1, "bold"))
            assertEquals(0xff010203.toInt(), field(2, "foreground"))
        }
    }
    @Test fun pausedHelpRefreshesInPlaceAndKeepsArticleDraftAndSelection() {
        resumedWorkspace(ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java, ohi.andre.consolelauncher.R.id.offline_help_input,
            ohi.andre.consolelauncher.R.id.offline_help_output)
    }
    @Test fun pausedCalculatorRefreshesInPlaceAndKeepsHistoryDraftAndSelection() {
        resumedWorkspace(ohi.andre.consolelauncher.calculator.CalculatorActivity::class.java, ohi.andre.consolelauncher.R.id.calculator_expression,
            ohi.andre.consolelauncher.R.id.calculator_result)
    }
    private fun resumedWorkspace(type: Class<out android.app.Activity>, inputId: Int, outputId: Int) {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val original = LauncherSettings.get(Theme.workspace_panel_text_color)
        LauncherSettings.set(Theme.workspace_panel_text_color, "#FF112233")
        val activity = instrumentation.startActivitySync(android.content.Intent(context, type).putExtra("topic", "terminal-basics")
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            var history = ""
            instrumentation.runOnMainSync {
                val input = activity.findViewById<android.widget.EditText>(inputId)
                input.setText("unfinished"); input.setSelection(2, 5)
                history = activity.findViewById<android.widget.TextView>(outputId).text.toString()
                instrumentation.callActivityOnPause(activity)
                LauncherSettings.set(Theme.workspace_panel_text_color, "#FF445566")
                instrumentation.callActivityOnResume(activity)
                assertFalse(activity.isFinishing)
                assertEquals("Resumed workspace resolves the new palette", 0xff445566.toInt(), activity.findViewById<android.widget.TextView>(outputId).currentTextColor)
                assertEquals(history, activity.findViewById<android.widget.TextView>(outputId).text.toString())
                assertEquals("unfinished", input.text.toString())
                assertEquals(2, input.selectionStart); assertEquals(5, input.selectionEnd)
            }
        } finally { instrumentation.runOnMainSync { activity.finish(); LauncherSettings.set(Theme.workspace_panel_text_color, original) } }
    }
    @Test fun helpBodyMatchesEmbeddedTerminalAtNondefaultFontOffset() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val key = ohi.andre.consolelauncher.managers.xml.options.Ui.font_size_offset
        val original = LauncherSettings.get(key)
        LauncherSettings.set(key, "-3")
        val activity = instrumentation.startActivitySync(android.content.Intent(context, ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java)
            .putExtra("topic", "terminal-basics").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val grid = ohi.andre.consolelauncher.managers.termux.TerminalGridView(activity)
                grid.setTerminalTextSizeSp(ohi.andre.consolelauncher.tuils.LauncherFontScale.scaledSp(12f))
                val paint = grid.javaClass.getDeclaredField("textPaint").apply { isAccessible = true }.get(grid) as android.graphics.Paint
                val body = activity.findViewById<android.widget.TextView>(ohi.andre.consolelauncher.R.id.offline_help_output)
                assertEquals("Body is exactly embedded terminal size, without a readability floor", paint.textSize, body.textSize, 0f)
                val spans = (body.text as Spanned).getSpans(0, body.length(), android.text.style.RelativeSizeSpan::class.java)
                assertTrue(spans.isNotEmpty()); assertTrue(spans.all { it.sizeChange > 1f })
            }
        } finally { instrumentation.runOnMainSync { activity.finish(); LauncherSettings.set(key, original) } }
    }
    @Test fun livePaletteRecolorsOwnedHistoryWithoutRestartOrLosingExplicitSpans() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val fixture: List<Pair<ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave, String>> = listOf(
            ohi.andre.consolelauncher.managers.xml.options.Ui.show_weather to "false",
            ohi.andre.consolelauncher.managers.xml.options.Ui.show_time to "true",
            ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption.unified_status_border to "false",
            ohi.andre.consolelauncher.managers.xml.options.Ui.show_app_installed to "true",
            ohi.andre.consolelauncher.managers.xml.options.Ui.show_app_uninstalled to "true",
            Behavior.tui_notification to "false", Behavior.auto_hide_output to "false",
            Behavior.output_tray_mode to "native", Behavior.enable_cyberdeck_mode to "false",
            Theme.output_text_color to "#FF112233", Theme.input_text_color to "#FF123456",
            Theme.app_installed_text_color to "#FF112233", Theme.app_uninstalled_text_color to "#FF112233",
            ohi.andre.consolelauncher.managers.xml.options.Suggestions.default_background_color to "#FF112233",
            Theme.time_text_color to "#FF102030", Behavior.status_time_format to "HH:mm",
            Behavior.input_format to "%t %i", Behavior.output_format to "%o")
        val original = fixture.associate { it.first to LauncherSettings.get(it.first) }
        fixture.forEach { (key, value) -> LauncherSettings.set(key, value) }
        val startup = context.getSharedPreferences("retui_startup", 0)
        val complete = startup.getBoolean("complete", false)
        val active = startup.getBoolean("active", false)
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: android.content.Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.action == "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") android.app.Instrumentation.ActivityResult(0, null) else null
        }
        instrumentation.addMonitor(monitor)
        val launcher = instrumentation.startActivitySync(android.content.Intent(context, ohi.andre.consolelauncher.LauncherActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as ohi.andre.consolelauncher.LauncherActivity
        try {
            instrumentation.waitForIdleSync()
            assertNotNull(launcher.uiManager)
            instrumentation.runOnMainSync {
                launcher.uiManager!!.restoreTerminalState(ohi.andre.consolelauncher.managers.TerminalManager.State("draft", "", listOf("pwd"), 0, false))
                launcher.uiManager!!.setOutput("owned", ohi.andre.consolelauncher.managers.TerminalManager.CATEGORY_OUTPUT)
                launcher.uiManager!!.setOutput(0xff112233.toInt(), "explicit")
                val inline = android.text.SpannableString("inline")
                inline.setSpan(ForegroundColorSpan(0xffabcdef.toInt()), 0, inline.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                launcher.uiManager!!.setOutput(inline, ohi.andre.consolelauncher.managers.TerminalManager.CATEGORY_NO_COLOR)
                launcher.findViewById<android.widget.EditText>(ohi.andre.consolelauncher.R.id.input_view).setSelection(1, 3)
                launcher.uiManager!!.openTermuxConsole(null)
                val termuxInput = launcher.uiManager!!.javaClass.getDeclaredField("termuxInput").apply { isAccessible = true }
                    .get(launcher.uiManager) as android.widget.EditText
                termuxInput.setText("retained shell draft")
            }
            instrumentation.waitForIdleSync()
            val before = launcher.uiManager!!.snapshotTerminalState()!!
            val manager = launcher.uiManager
            instrumentation.runOnMainSync {
                LauncherSettings.set(context, Theme.output_text_color, "#FF445566")
                LauncherSettings.set(context, Theme.time_text_color, "#FF445566")
                LauncherSettings.set(context, Theme.app_installed_text_color, "#FF445566")
                LauncherSettings.set(context, Theme.app_uninstalled_text_color, "#FF445566")
            }
            android.os.SystemClock.sleep(300)
            instrumentation.waitForIdleSync()
            assertSame("Palette changes must retain the live UI manager", manager, launcher.uiManager)
            assertSame(launcher, ohi.andre.consolelauncher.LauncherActivity.instance)
            assertEquals("Long-lived package receiver resolves new installed color", 0xff445566.toInt(), (ohi.andre.consolelauncher.managers.AppsManager.instance as ohi.andre.consolelauncher.managers.AppsManager).appInstalledColor)
            assertEquals(0xff445566.toInt(), (ohi.andre.consolelauncher.managers.AppsManager.instance as ohi.andre.consolelauncher.managers.AppsManager).appUninstalledColor)
            instrumentation.runOnMainSync {
                val after = launcher.uiManager!!.snapshotTerminalState()!!
                val clock = launcher.uiManager!!.javaClass.getDeclaredField("tuiTimeManager").apply { isAccessible = true }
                    .get(launcher.uiManager) as ohi.andre.consolelauncher.managers.status.TimeManager
                clock.javaClass.getDeclaredMethod("update").apply { isAccessible = true }.invoke(clock)
                val timeView = launcher.uiManager!!.javaClass.getDeclaredMethod("getLabelView", ohi.andre.consolelauncher.UIManager.Label::class.java)
                    .apply { isAccessible = true }.invoke(launcher.uiManager, ohi.andre.consolelauncher.UIManager.Label.time) as android.widget.TextView
                assertEquals(android.view.View.VISIBLE, timeView.visibility)
                val clockText = timeView.text as Spanned
                assertTrue("Rendered status clock follows the new palette", clockText.getSpans(0, clockText.length, ForegroundColorSpan::class.java)
                    .any { it.foregroundColor == 0xff445566.toInt() })
                assertEquals(before.input, after.input)
                assertEquals(before.commands, after.commands)
                assertEquals(before.selectionStart, after.selectionStart); assertEquals(before.selectionEnd, after.selectionEnd)
                assertEquals(before.output.toString(), after.output.toString())
                fun colors(word: String): List<Int> {
                    val text = after.output as Spanned
                    val start = text.toString().indexOf(word)
                    return text.getSpans(start, start + word.length, ForegroundColorSpan::class.java).map { it.foregroundColor }
                }
                assertTrue("Old ordinary output follows palette", colors("owned").contains(0xff445566.toInt()))
                assertEquals(listOf(0xff112233.toInt()), colors("explicit"))
                assertEquals(listOf(0xffabcdef.toInt()), colors("inline"))
                launcher.uiManager!!.setOutput("new", ohi.andre.consolelauncher.managers.TerminalManager.CATEGORY_OUTPUT)
                launcher.uiManager!!.openTermuxConsole(null)
                val termuxInput = launcher.uiManager!!.javaClass.getDeclaredField("termuxInput").apply { isAccessible = true }
                    .get(launcher.uiManager) as android.widget.EditText
                assertEquals("retained shell draft", termuxInput.text.toString())
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val current = launcher.uiManager!!.snapshotTerminalState()!!.output as Spanned
                val newStart = current.toString().lastIndexOf("new")
                assertTrue(current.getSpans(newStart, newStart + 3, ForegroundColorSpan::class.java).any { it.foregroundColor == 0xff445566.toInt() })
            }
            val beforeSuggestionChange = launcher.uiManager
            instrumentation.runOnMainSync {
                LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Suggestions.default_background_color, "#FF445566")
            }
            android.os.SystemClock.sleep(300); instrumentation.waitForIdleSync()
            assertSame("Suggestion colors must retain the live UI manager", beforeSuggestionChange, launcher.uiManager)
            assertSame(launcher, ohi.andre.consolelauncher.LauncherActivity.instance)
        } finally {
            instrumentation.runOnMainSync { launcher.finish(); original.forEach { (key, value) -> LauncherSettings.set(key, value) } }
            instrumentation.removeMonitor(monitor)
            startup.edit().putBoolean("complete", complete).putBoolean("active", active).commit()
        }
    }
    @Test fun cachedClockUsesCurrentThemeButRetainsInlineAndCallerColors() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val settings = listOf(Theme.time_text_color, Behavior.status_time_format)
        val original = settings.associateWith { LauncherSettings.get(it) }
        val previous = TimeManager.instance
        try {
            LauncherSettings.set(Theme.time_text_color, "#FF112233")
            LauncherSettings.set(Behavior.status_time_format, "HH:mm@#ABCDEFHH:mm")
            val clock = TimeManager(context)
            fun color(index: Int, override: Int = Int.MAX_VALUE): Int {
                val text = clock.getCharSequence("%t$index", 0L, override) as Spanned
                return text.getSpans(0, text.length, ForegroundColorSpan::class.java).single().foregroundColor
            }
            assertEquals(0xff112233.toInt(), color(0))
            LauncherSettings.set(Theme.time_text_color, "#FF445566")
            assertEquals("Existing clock must resolve the new palette", 0xff445566.toInt(), color(0))
            assertEquals(0xffabcdef.toInt(), color(1))
            assertEquals(0xff102030.toInt(), color(0, 0xff102030.toInt()))
        } finally {
            original.forEach { (key, value) -> LauncherSettings.set(key, value) }
            TimeManager.instance = previous
        }
    }
}
