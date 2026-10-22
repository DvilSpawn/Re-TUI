package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in phone measurement against two explicitly provisioned disposable tmux windows. */
class TerminalHomeDeviceBenchmarkTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun set(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private fun call(owner: Any, name: String, vararg args: Any?) = owner.javaClass.declaredMethods.single { it.name == name && it.parameterTypes.size == args.size }.apply { isAccessible = true }.invoke(owner, *args)
    private fun restore(prefs: SharedPreferences, saved: Map<String, *>) {
        val editor = prefs.edit().clear()
        saved.forEach { (key, value) -> when(value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
        } }
        editor.commit()
    }
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 15000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            Thread.sleep(25)
        }
        fail(message)
    }
    private fun renderedText(grid: TerminalGridView): String? {
        val emulator = field(grid, "streamEmulator") as? com.termux.terminal.TerminalEmulator
        return emulator?.getSelectedText(0, 0, emulator.mColumns - 1, emulator.mRows - 1)
            ?: field(grid, "frame") as? String
    }
    private fun measure(grid: TerminalGridView, input: TermuxWorkspaceInputEditText): List<Double> {
        data class Sample(val expected: String, val started: Long, val drawn: CountDownLatch)
        val pending = AtomicReference<Sample>()
        val timings = mutableListOf<Double>()
        val draw = ViewTreeObserver.OnDrawListener {
            val sample = pending.get()
            val body = renderedText(grid).orEmpty().replace(Regex("\\u001b\\[[0-?]*[ -/]*[@-~]"), "").replace(Regex("\\s+"), "")
            if (sample != null && body.contains(sample.expected) && pending.compareAndSet(sample, null)) {
                grid.post { timings += (System.nanoTime() - sample.started) / 1_000_000.0; sample.drawn.countDown() }
            }
        }
        instrumentation.runOnMainSync { grid.viewTreeObserver.addOnDrawListener(draw) }
        try {
            val letters = "abcdefghijklmnopqrstuvwx".repeat(4)
            letters.forEachIndexed { index, letter ->
                val latch = CountDownLatch(1)
                instrumentation.runOnMainSync {
                    pending.set(Sample(letters.take(index + 1), System.nanoTime(), latch))
                    assertTrue(input.onCreateInputConnection(EditorInfo())!!.commitText(letter.toString(), 1))
                }
                assertTrue("Committed text must reach the rendered terminal", latch.await(5, TimeUnit.SECONDS))
            }
        } finally { instrumentation.runOnMainSync { grid.viewTreeObserver.removeOnDrawListener(draw) } }
        return timings.drop(4)
    }
    @Test fun compareCommittedInputToDrawAtEqualGeometry() {
        assumeTrue("Explicit phone benchmark opt-in required", InstrumentationRegistry.getArguments().getString("terminalHomeDeviceBenchmark") == "true")
        val context = instrumentation.targetContext
        val targets = JSONObject(File(context.cacheDir, "terminal-pane-benchmark.json").readText()).getJSONArray("targets")
        val first = targets.getString(0); val second = targets.getString(1)
        require(first.matches(Regex("@[0-9]+")) && second.matches(Regex("@[0-9]+")) && first != second)
        val workspacePrefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val homePrefs = context.getSharedPreferences("terminal_home", Context.MODE_PRIVATE)
        val workspaceSaved = workspacePrefs.all.toMap(); val homeSaved = homePrefs.all.toMap()
        val result = JSONObject()
        var home: TerminalHomeView? = null
        try {
            workspacePrefs.edit().putString("selection", "SINGLE|0|$first|").remove("draft").putBoolean("helperDeferred", true).commit()
            homePrefs.edit().putString("tiles", "[]").commit()
            ActivityScenario.launch(TermuxWorkspaceActivity::class.java).use { scenario ->
                lateinit var activity: TermuxWorkspaceActivity
                lateinit var classicGrid: TerminalGridView
                lateinit var classicInput: TermuxWorkspaceInputEditText
                scenario.onActivity {
                    activity = it
                    classicGrid = (field(it, "grids") as Array<*>)[0] as TerminalGridView
                    classicInput = it.findViewById(R.id.termux_workspace_input)
                    classicGrid.layoutParams = LinearLayout.LayoutParams(640, 400)
                    call(it, "focusInput", false)
                }
                await("Classic pane must receive its initial frame") { classicGrid.width == 640 && classicGrid.height == 400 && renderedText(classicGrid) != null }
                instrumentation.runOnMainSync { call(activity, "capture", 0, false) }
                Thread.sleep(400)
                val classic = measure(classicGrid, classicInput)
                lateinit var homeGrid: TerminalGridView
                lateinit var homeInput: TermuxWorkspaceInputEditText
                instrumentation.runOnMainSync {
                    call(activity, "closeTile", 0); call(activity, "closeTile", 1)
                    home = TerminalHomeView(activity)
                    call(home!!, "add", "Validation", "", second)
                    activity.setContentView(home)
                    val runtime = (field(home!!, "runtimes") as Map<*, *>).values.single()!!
                    homeGrid = field(runtime, "grid") as TerminalGridView
                    homeGrid.layoutParams = LinearLayout.LayoutParams(640, 400)
                    homeInput = field(home!!, "input") as TermuxWorkspaceInputEditText
                    set(home!!, "active", runtime)
                    homeInput.visibility = View.VISIBLE; homeInput.showSoftInputOnFocus = false; homeInput.requestFocus()
                    home!!.resume()
                }
                await("Home pane must receive its initial frame") { homeGrid.width == 640 && homeGrid.height == 400 && renderedText(homeGrid) != null }
                val terminalHome = measure(homeGrid, homeInput)
                fun stats(values: List<Double>): JSONObject {
                    val sorted = values.sorted()
                    return JSONObject().put("samples", sorted.size).put("median_ms", sorted[sorted.size / 2]).put("p95_ms", sorted[kotlin.math.ceil(sorted.size * .95).toInt() - 1])
                }
                result.put("classic", stats(classic)).put("home", stats(terminalHome))
                result.put("geometry_px", "640x400").put("metric", "IME commit to draw-post; not physical tap-to-pixel")
                val classicStats = result.getJSONObject("classic"); val homeStats = result.getJSONObject("home")
                result.put("passed", homeStats.getDouble("median_ms") <= classicStats.getDouble("median_ms") * 1.2 && homeStats.getDouble("p95_ms") <= classicStats.getDouble("p95_ms") * 1.2)
                File(context.cacheDir, "terminal-pane-benchmark-results.json").writeText(result.toString(2))
                assertTrue("Home latency must be within 20% of Classic or better: $result", result.getBoolean("passed"))
            }
        } finally {
            instrumentation.runOnMainSync { home?.pause() }
            restore(workspacePrefs, workspaceSaved); restore(homePrefs, homeSaved)
        }
    }
    @Test fun eightHomeSshTilesRouteRapidFocusAndReattach() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("terminalHomeEightDevice") == "true")
        val context = instrumentation.targetContext
        val targets = JSONObject(File(context.cacheDir, "terminal-pane-benchmark.json").readText()).getJSONArray("targets")
        assertEquals(8, targets.length())
        val prefs = context.getSharedPreferences("terminal_home", Context.MODE_PRIVATE)
        val saved = prefs.all.toMap()
        val workspace = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val workspaceSaved = workspace.all.toMap()
        var home: TerminalHomeView? = null
        try {
            prefs.edit().putString("tiles", "[]").commit()
            ActivityScenario.launch(TermuxWorkspaceActivity::class.java).use { scenario ->
                lateinit var entries: List<Any>
                lateinit var input: TermuxWorkspaceInputEditText
                scenario.onActivity { activity ->
                    call(activity, "closeTile", 0); call(activity, "closeTile", 1)
                    home = TerminalHomeView(activity)
                    repeat(8) { call(home!!, "add", "Fixture $it", "", targets.getString(it)) }
                    val layout = field(home!!, "layout") as TerminalHomeLayout
                    layout.tiles.forEachIndexed { i, tile -> tile.y = (i / 2) * 2; tile.height = 2 }
                    call(home!!, "placeTiles")
                    activity.setContentView(home)
                    entries = (field(home!!, "runtimes") as Map<*, *>).values.map { it!! }
                    input = field(home!!, "input") as TermuxWorkspaceInputEditText
                    input.visibility = View.VISIBLE; input.showSoftInputOnFocus = false; input.requestFocus()
                    home!!.resume()
                }
                await("All eight SSH tiles must receive initial output") {
                    entries.all { (field(it, "terminal") as? TermuxWorkspaceTerminal)?.text()?.contains("$") == true }
                }
                instrumentation.runOnMainSync {
                    val keyboard = input.onCreateInputConnection(EditorInfo())!!
                    entries.forEachIndexed { index, entry ->
                        set(home!!, "active", entry)
                        assertTrue(keyboard.commitText("HOME_SLOT_${index}_é", 1))
                    }
                }
                await("Rapid focus changes must route to all eight targets") {
                    entries.withIndex().all { (index, entry) ->
                        (field(entry, "terminal") as? TermuxWorkspaceTerminal)?.text()?.contains("HOME_SLOT_${index}_é") == true
                    }
                }
                instrumentation.runOnMainSync {
                    entries.forEachIndexed { index, entry ->
                        val text = (field(entry, "terminal") as TermuxWorkspaceTerminal).text()
                        (0..7).filter { it != index }.forEach { assertFalse(text.contains("HOME_SLOT_${it}_é")) }
                    }
                    home!!.pause(); home!!.resume()
                }
                await("Reconnect must preserve all eight shells and their output") {
                    entries.withIndex().all { (index, entry) ->
                        (field(entry, "terminal") as? TermuxWorkspaceTerminal)?.text()?.contains("HOME_SLOT_${index}_é") == true
                    }
                }
                instrumentation.runOnMainSync {
                    set(home!!, "active", entries[0])
                    val keys = android.view.LayoutInflater.from(context).inflate(R.layout.termux_special_keys, null)
                    home!!.bindSpecialKeys(keys)
                    fun press(label: String) {
                        val buttons = listOf(R.id.termux_workspace_keys_row_one, R.id.termux_workspace_keys_row_two).flatMap { id ->
                            val row = keys.findViewById<LinearLayout>(id)
                            (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView }
                        }
                        buttons.single { it.text.toString() == label }.performClick()
                    }
                    press("CTRL")
                    input.onCreateInputConnection(EditorInfo())!!.commitText("u", 1)
                    input.onCreateInputConnection(EditorInfo())!!.commitText("KEYS_OK", 1)
                    press("←")
                    input.onCreateInputConnection(EditorInfo())!!.commitText("X", 1)
                }
                await("Shared Ctrl and arrow buttons must edit the focused shell") {
                    (field(entries[0], "terminal") as? TermuxWorkspaceTerminal)?.text()?.contains("KEYS_OXK") == true
                }
                lateinit var scroll: android.widget.ScrollView
                lateinit var panel: View
                instrumentation.runOnMainSync {
                    home!!.releaseInput()
                    home!!.updateKeyboardVisibility(false)
                    scroll = field(home!!, "scroll") as android.widget.ScrollView
                    panel = field(entries.last(), "panel") as View
                    call(home!!, "activate", entries.last())
                    home!!.updateKeyboardVisibility(true)
                }
                await("Bottom pane must sit above the special keys without an empty tail") {
                    val gap = scroll.scrollY + scroll.height - scroll.paddingBottom - panel.bottom
                    gap in 0..(8 * context.resources.displayMetrics.density).toInt()
                }
                instrumentation.runOnMainSync { assertTrue(scroll.scrollY > 0) }
                var heldPosition = 0
                instrumentation.runOnMainSync {
                    scroll.scrollTo(0, (panel.top - 80).coerceAtLeast(0))
                    heldPosition = scroll.scrollY
                    call(home!!, "activate", entries.last())
                }
                Thread.sleep(350)
                instrumentation.runOnMainSync { assertEquals("Tapping the focused pane must not scroll it again", heldPosition, scroll.scrollY) }
            }
        } finally {
            instrumentation.runOnMainSync { home?.pause() }
            restore(prefs, saved); restore(workspace, workspaceSaved)
        }
    }

}
