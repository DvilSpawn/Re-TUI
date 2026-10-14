package ohi.andre.consolelauncher.managers.settings

import android.content.Intent
import android.graphics.Color
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.calculator.CalculatorActivity
import ohi.andre.consolelauncher.irc.IrcActivity
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.*
import org.junit.Test

class PaletteRenderingRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @Test fun cursorIgnoresOemTintAndReadsThemeChangesFromTheCachedDrawable() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val old = LauncherSettings.get(Theme.cursor_color)
        try {
            instrumentation.runOnMainSync {
                val input = EditText(android.view.ContextThemeWrapper(context, R.style.Custom_Solid))
                ohi.andre.consolelauncher.tuils.Tuils.setCursorDrawableColor(input, Color.RED)
                val drawable = input.textCursorDrawable
                assertTrue(drawable is ohi.andre.consolelauncher.tuils.PaletteCursorDrawable)
                val bitmap = android.graphics.Bitmap.createBitmap(8, 32, android.graphics.Bitmap.Config.ARGB_8888)
                try {
                    drawable!!.setBounds(0, 0, 8, 32)
                    for (color in listOf("#ffc07969", "#fff5e0dc")) {
                        LauncherSettings.set(Theme.cursor_color, color)
                        drawable.setTint(Color.MAGENTA)
                        drawable.setColorFilter(Color.MAGENTA, android.graphics.PorterDuff.Mode.SRC_IN)
                        drawable.state = intArrayOf(android.R.attr.state_focused)
                        drawable.draw(android.graphics.Canvas(bitmap))
                        assertEquals(Color.parseColor(color), bitmap.getPixel(4, 16))
                    }
                } finally { bitmap.recycle() }
            }
        } finally { LauncherSettings.set(Theme.cursor_color, old) }
    }
    @Test fun lightDarkPackagesRefreshPausedPanesAndRetainDraftsAndSelections() {
        val context = instrumentation.targetContext
        ohi.andre.consolelauncher.tuils.Tuils.init(context)
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val folder = ohi.andre.consolelauncher.tuils.Tuils.getFolder()
        val originals = folder.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }.associateWith { it.readBytes() }
        val names = mutableListOf<String>()
        fun apply(name: String) {
            ohi.andre.consolelauncher.managers.PresetManager.apply(name)
            XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        }
        try {
            for (slug in listOf("catppuccin-latte", "catppuccin-mocha")) {
                instrumentation.context.assets.open("terminal-colors/downloads/$slug.retui").use { bytes ->
                    ohi.andre.consolelauncher.managers.BackupManager.inspectPreset(bytes, java.io.File(context.cacheDir, "switch-inspect")).use { names.add(it.save()) }
                }
            }
            for ((type, inputId) in listOf(CalculatorActivity::class.java to R.id.calculator_expression,
                ohi.andre.consolelauncher.help.OfflineHelpActivity::class.java to R.id.offline_help_input,
                IrcActivity::class.java to R.id.irc_input)) {
                apply(names[0])
                val activity = instrumentation.startActivitySync(Intent(context, type).putExtra("irc_offline_fixture", true)
                    .putExtra("topic", "terminal-basics").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                try {
                    var cached = ""
                    val outputId = when (type) { CalculatorActivity::class.java -> R.id.calculator_result
                        IrcActivity::class.java -> R.id.irc_output; else -> R.id.offline_help_output }
                    instrumentation.runOnMainSync {
                        activity.findViewById<EditText>(inputId).apply { setText("unfinished draft"); setSelection(2, 8) }
                        cached = activity.findViewById<TextView>(outputId).text.toString()
                    }
                    for (name in listOf(names[1], names[0])) {
                        instrumentation.runOnMainSync { instrumentation.callActivityOnPause(activity) }
                        apply(name)
                        instrumentation.runOnMainSync {
                            instrumentation.callActivityOnResume(activity)
                            val input = activity.findViewById<EditText>(inputId)
                            assertEquals(type.simpleName, ThemeColorResolver.color(Theme.workspace_input_text_color), input.currentTextColor)
                            assertEquals("unfinished draft", input.text.toString())
                            assertEquals(2, input.selectionStart); assertEquals(8, input.selectionEnd)
                            assertEquals("Cached output survives both palette directions", cached, activity.findViewById<TextView>(outputId).text.toString())
                        }
                    }
                } finally { instrumentation.runOnMainSync { activity.finish() } }
            }
        } finally {
            originals.forEach { (file, bytes) -> file.writeBytes(bytes) }
            names.forEach { java.io.File(ohi.andre.consolelauncher.managers.PresetManager.presetsDir, it).deleteRecursively() }
            XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        }
    }
    @Test fun faintCalculatorTabsAndIrcHintsHonorThemeRoles() {
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        val roles = listOf(Theme.muted_text_color, Theme.field_hint_color)
        val old = roles.associateWith { LauncherSettings.get(it) }
        try {
            LauncherSettings.set(Theme.muted_text_color, "#FF334455")
            LauncherSettings.set(Theme.field_hint_color, "#FF445566")
            val calc = instrumentation.startActivitySync(Intent(context, CalculatorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CalculatorActivity
            try {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals(0xff334455.toInt(), calc.findViewById<TextView>(R.id.calculator_letters_tab).currentTextColor)
                    assertEquals(0xff334455.toInt(), calc.findViewById<TextView>(R.id.calculator_functions_tab).currentTextColor)
                }
            } finally { instrumentation.runOnMainSync { calc.finish() } }
            val irc = instrumentation.startActivitySync(Intent(context, IrcActivity::class.java).putExtra("irc_offline_fixture", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as IrcActivity
            try {
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { assertEquals(0xff445566.toInt(), irc.findViewById<EditText>(R.id.irc_input).currentHintTextColor) }
            } finally { instrumentation.runOnMainSync { irc.finish() } }
        } finally { old.forEach { (role, value) -> LauncherSettings.set(role, value) } }
    }

    @Test fun reverseVideoSwapsColorsAndPreservesTruecolor() {
        instrumentation.runOnMainSync {
            val grid = TerminalGridView(instrumentation.targetContext)
            val fg = 0xff4c4f69.toInt(); val bg = 0xffeff1f5.toInt()
            grid.updateThemeColors(fg, bg, fg)
            grid.setFrame("\u001b[7mR\u001b[0m\u001b[38;2;1;2;3mT", 20, 2, null, null)
            val cells = grid.javaClass.getDeclaredField("cells").apply { isAccessible = true }.get(grid) as Array<*>
            fun field(index: Int, name: String) = cells[index]!!.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(cells[index])
            assertEquals(bg, field(0, "foreground")); assertEquals(fg, field(0, "background"))
            assertEquals(Color.rgb(1, 2, 3), field(1, "foreground"))
            grid.updateThemeColors(0xffcdd6f4.toInt(), 0xff1e1e2e.toInt(), fg)
            assertEquals(0xff1e1e2e.toInt(), field(0, "foreground")); assertEquals(0xffcdd6f4.toInt(), field(0, "background"))
            assertEquals(Color.rgb(1, 2, 3), field(1, "foreground"))
        }
    }
}
