package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import ohi.andre.consolelauncher.R
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in disposable-emulator fixture; never replaces a phone's Termux helper. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspacePaneIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    @Test fun emptyWorkspaceHidesWindowChooserUntilAWindowIsAvailable() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".termuxsshdebug")) { "Use the disposable Termux test application" }
        check(!TermuxWorkspaceSshSetup.isPaired(context)) { "This UI fixture must not connect to Termux" }
        ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val state = field(activity, "selection") as TermuxWorkspaceState
                state.select(0, null); state.select(1, null); state.focus(0)
                val labels = (field(activity, "labels") as Array<*>).map { it as android.widget.TextView }
                val windowField = TermuxWorkspaceActivity::class.java.getDeclaredField("windows").apply { isAccessible = true }
                fun refresh(windows: List<Pair<String, String>>) {
                    windowField.set(activity, windows)
                    call(activity, "updateLabels")
                }
                refresh(emptyList())
                assertTrue(labels.all { it.visibility == View.GONE && !it.isClickable })
                refresh(listOf("@1" to "0: shell"))
                assertTrue(labels.all { it.visibility == View.VISIBLE && it.isClickable })
                state.select(0, "@1")
                refresh(listOf("@1" to "0: shell"))
                assertEquals(View.VISIBLE, labels[0].visibility)
                assertEquals(View.GONE, labels[1].visibility)
                state.select(0, null)
                refresh(emptyList())
                assertTrue(labels.all { it.visibility == View.GONE && !it.isClickable })
                call(activity, "directText", ":help")
                assertEquals(View.VISIBLE, labels[0].visibility)
                assertEquals(":help", labels[0].text.toString())
                assertFalse(labels[0].isClickable)
            }
        }
    }

    private fun field(activity: TermuxWorkspaceActivity, name: String): Any? =
        TermuxWorkspaceActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(activity)
    private fun call(activity: TermuxWorkspaceActivity, name: String, vararg args: Any?) =
        TermuxWorkspaceActivity::class.java.declaredMethods.single { it.name == name && it.parameterTypes.size == args.size }
            .apply { isAccessible = true }.invoke(activity, *args)
    private fun await(scenario: ActivityScenario<TermuxWorkspaceActivity>, message: String, condition: (TermuxWorkspaceActivity) -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 10000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var passed = false
            scenario.onActivity { passed = condition(it) }
            if (passed) return
            Thread.sleep(50)
        }
        fail(message)
    }
    private fun attach(activity: TermuxWorkspaceActivity, port: Int) {
        val bootstrap = TermuxWorkspaceFrame.parse("__RETUI_SOCKET__ retui-bridge-socket-v4\n__RETUI_SOCKET_NAME__ retui_bridge_com_dvil_tui_renewed_v2\n__RETUI_TOKEN__ fixture-token\n__RETUI_TCP_HOST__ 127.0.0.1\n__RETUI_TCP_PORT__ $port\n")
        TermuxWorkspaceActivity::class.java.getDeclaredField("bootstrap").apply { isAccessible = true }.set(activity, bootstrap)
        val state = field(activity, "selection") as TermuxWorkspaceState
        state.visibleTiles.forEach { call(activity, "connectTile", it) }
    }
    private fun choosePlacement(label: String) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            val node = instrumentation.uiAutomation.rootInActiveWindow
                ?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString() == label }
            if (node != null) {
                if (node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                if (!bounds.isEmpty) {
                    val now = android.os.SystemClock.uptimeMillis()
                    for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                        android.view.MotionEvent.obtain(now, now, action, bounds.exactCenterX(), bounds.exactCenterY(), 0).useEvent {
                            instrumentation.uiAutomation.injectInputEvent(it, true)
                        }
                    }
                    return
                }
            }
            Thread.sleep(25)
        }
        val root = instrumentation.uiAutomation.rootInActiveWindow
        fun texts(node: android.view.accessibility.AccessibilityNodeInfo?): List<String> =
            if (node == null) emptyList() else listOfNotNull(node.text?.toString()) +
                (0 until node.childCount).flatMap { texts(node.getChild(it)) }
        fail("Shell placement choice was not visible: $label; fixture UI: ${texts(root)}")
    }
    private inline fun android.view.MotionEvent.useEvent(block: (android.view.MotionEvent) -> Unit) {
        try { block(this) } finally { recycle() }
    }

    @Test fun liveAppearanceAndFadeRetainBothTilesDraftAndInputSelection() {
        assumeTrue("Legacy socket appearance fixture; default SSH is covered by TermuxWorkspaceSshAppearanceTest",
            !ohi.andre.consolelauncher.BuildConfig.TERMUX_SSH_PANE)
        assumeTrue("Disposable emulator fixture only", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE in listOf("goldfish", "ranchu"))
        val context = instrumentation.targetContext
        assumeTrue("Appearance fixture must not bootstrap a shared Termux runtime", !TermuxBridgeManager.status(context).termuxInstalled)
        if (android.os.Build.VERSION.SDK_INT >= 30 && ohi.andre.consolelauncher.BuildConfig.FLAVOR == "fdroid")
            assumeTrue(android.os.Environment.isExternalStorageManager())
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        val size = ohi.andre.consolelauncher.managers.xml.options.Ui.font_size_offset
        val foreground = ohi.andre.consolelauncher.managers.xml.options.Theme.workspace_output_text_color
        val control = ohi.andre.consolelauncher.managers.xml.options.Theme.workspace_control_text_color
        val settings = ohi.andre.consolelauncher.managers.settings.LauncherSettings
        ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.loadCommons(context)
        settings.refreshFromLoadedPrefs()
        val originals = listOf(size, foreground, control,
            ohi.andre.consolelauncher.managers.xml.options.Ui.system_font,
            ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out,
            ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out_seconds).associateWith { settings.get(it) }
        try {
            settings.set(ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out, "false")
            settings.set(size, "0")
            settings.set(ohi.andre.consolelauncher.managers.xml.options.Ui.system_font, "true")
            ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java)).use { scenario ->
                await(scenario, "Workspace must own a rendered window") { it.hasWindowFocus() }
                scenario.onActivity { activity ->
                    call(activity, "dismissHelperPrompt")
                    val state = field(activity, "selection") as TermuxWorkspaceState
                    state.select(0, "@1"); state.select(1, "@2"); state.focus(1)
                    state.layout = TermuxWorkspaceState.Layout.LEFT_RIGHT
                    call(activity, "applyLayout")
                    call(activity, "directText", ":retained 😀 draft")
                    val input = activity.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    input.setText("editor draft"); input.setSelection(2, 5)
                    val grids = field(activity, "grids") as Array<*>
                    grids.forEach { (it as TerminalGridView).setFrame("retained frame", 80, 24, 2, 3) }
                }
                instrumentation.waitForIdleSync()
                await(scenario, "Both workspace grids must be laid out before comparing geometry") {
                    (field(it, "grids") as Array<*>).all { grid -> (grid as TerminalGridView).width > 0 && grid.height > 0 }
                }
                var beforeGeometry = emptyList<Any?>()
                scenario.onActivity { activity ->
                    val state = field(activity, "selection") as TermuxWorkspaceState
                    val input = activity.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    val grids = (field(activity, "grids") as Array<*>).map { it as TerminalGridView }
                    val beforeMetrics = grids.map { it.characterWidth() to it.lineHeight() }
                    val beforePixels = grids.map { it.width to it.height }
                    beforeGeometry = grids.indices.map { call(activity, "geometry", it) }
                    assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                        12f, activity.resources.displayMetrics), input.textSize, 0.01f)
                    val selected = state.encode()
                    val clients = (field(activity, "clients") as Array<*>).toList()
                    settings.set(size, "7"); settings.set(size, "7")
                    settings.set(foreground, "#FF123456"); settings.set(control, "#FF654321")
                    assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                        19f, activity.resources.displayMetrics), input.textSize, 0.01f)
                    assertEquals("Metric-only changes must not need a pixel-bounds event", beforePixels, grids.map { it.width to it.height })
                    grids.forEachIndexed { tile, grid ->
                        assertNotEquals("Grid $tile character width did not change through settings hook", beforeMetrics[tile].first, grid.characterWidth())
                        assertNotEquals("Grid $tile line height did not change through settings hook", beforeMetrics[tile].second, grid.lineHeight())
                        assertNotEquals("Grid $tile effective geometry stayed stale", beforeGeometry[tile], call(activity, "geometry", tile))
                    }
                    assertEquals(selected, state.encode())
                    assertEquals("retained 😀 draft", field(activity, "localDraft"))
                    assertEquals("editor draft", input.text.toString())
                    assertEquals(2, input.selectionStart); assertEquals(5, input.selectionEnd)
                    assertEquals(clients, (field(activity, "clients") as Array<*>).toList())
                    for (grid in grids) {
                        val type = TerminalGridView::class.java
                        assertEquals("retained frame", type.getDeclaredField("frame").apply { isAccessible = true }.get(grid))
                        assertEquals(0xff123456.toInt(), type.getDeclaredField("terminalForeground").apply { isAccessible = true }.getInt(grid))
                        val paint = type.getDeclaredField("textPaint").apply { isAccessible = true }.get(grid) as android.graphics.Paint
                        assertEquals(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                            ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp(), activity.resources.displayMetrics), paint.textSize, 0.01f)
                        assertEquals(ohi.andre.consolelauncher.tuils.Tuils.getTypeface(activity), paint.typeface)
                    }
                    val row = activity.findViewById<android.widget.LinearLayout>(R.id.termux_workspace_keys_row_one)
                    assertEquals(0xff654321.toInt(), (row.getChildAt(0) as android.widget.TextView).currentTextColor)
                    assertTrue(input.privateImeOptions.contains("123456", ignoreCase = true))
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    for (tile in 0..1) assertNotEquals("Grid $tile settled geometry must change", beforeGeometry[tile], call(activity, "geometry", tile))
                }
                scenario.onActivity {
                    assertEquals(android.graphics.PixelFormat.TRANSLUCENT, it.window.attributes.format)
                    assertEquals(android.graphics.Color.TRANSPARENT, (it.window.decorView.background as android.graphics.drawable.ColorDrawable).color)
                    settings.set(ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out_seconds, "1")
                    settings.set(ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out, "true")
                    it.refreshBreathOutSettings()
                }
                await(scenario, "Entire workspace must fade") { (field(it, "screen") as View).alpha == 0f }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val copied = java.util.concurrent.CountDownLatch(1)
                    lateinit var bitmap: android.graphics.Bitmap
                    var result = -1
                    scenario.onActivity {
                        val screen = field(it, "screen") as View
                        bitmap = android.graphics.Bitmap.createBitmap(screen.width, screen.height, android.graphics.Bitmap.Config.ARGB_8888)
                        android.view.PixelCopy.request(it.window, bitmap, { value -> result = value; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
                    }
                    try {
                        assertTrue(copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
                        assertEquals(android.view.PixelCopy.SUCCESS, result)
                        assertEquals("Hidden renderer must submit transparent workspace pixels", 0,
                            android.graphics.Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
                    } finally { bitmap.recycle() }
                }
                scenario.onActivity {
                    assertTrue(it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
                    val screen = field(it, "screen") as ohi.andre.consolelauncher.BreathOutLayout
                    screen.setActive(true)
                    assertEquals("Duplicate focus must not wake workspace", 0f, screen.alpha, 0.001f)
                    val bitmap = android.graphics.Bitmap.createBitmap(screen.width, screen.height, android.graphics.Bitmap.Config.ARGB_8888)
                    it.findViewById<View>(android.R.id.content).draw(android.graphics.Canvas(bitmap))
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    bitmap.recycle()
                    assertEquals("Hidden workspace must draw no pixels", 0, pixels.count { pixel -> android.graphics.Color.alpha(pixel) != 0 })
                    call(it, "directText", "wake must not edit")
                    assertEquals("retained 😀 draft", field(it, "localDraft"))
                }
                await(scenario, "IME wake must restore workspace") { (field(it, "screen") as View).alpha == 1f }
                scenario.onActivity {
                    settings.set(ohi.andre.consolelauncher.managers.xml.options.Behavior.breath_out, "false")
                    it.refreshBreathOutSettings()
                    assertEquals(1f, (field(it, "screen") as View).alpha, 0.001f)
                    val input = it.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    assertEquals("editor draft", input.text.toString())
                    assertEquals(2, input.selectionStart); assertEquals(5, input.selectionEnd)
                }
            }
        } finally {
            originals.forEach { (key, value) -> settings.set(key, value) }
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            edit.commit()
            assertEquals(original, prefs.all)
        }
    }

    @Test fun twoTilesRestoreFocusInputAndKeepFramesDuringTypingAndPause() {
        val port = InstrumentationRegistry.getArguments().getString("hostFixturePort")?.toIntOrNull()
        assumeTrue("Explicit private real-helper fixture is required", port != null)
        assumeTrue("Fixture must not run on a data-bearing phone", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.HARDWARE in listOf("goldfish", "ranchu"))
        val context = instrumentation.targetContext
        if (android.os.Build.VERSION.SDK_INT >= 30 && ohi.andre.consolelauncher.BuildConfig.FLAVOR == "fdroid")
            assumeTrue("Grant storage access to the disposable fixture before running", android.os.Environment.isExternalStorageManager())
        val prefs = context.getSharedPreferences("retui_workspace_surface", Context.MODE_PRIVATE)
        val original = prefs.all.toMap()
        prefs.edit().clear().commit()
        try {
            ActivityScenario.launch<TermuxWorkspaceActivity>(Intent(context, TermuxWorkspaceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).use { scenario ->
                scenario.onActivity {
                    // The private helper owns @0; an empty pane must not auto-select/create a shell.
                    call(it, "dismissHelperPrompt")
                    (field(it, "selection") as TermuxWorkspaceState).select(0, "@0")
                    attach(it, port!!)
                }
                await(scenario, "First tile must receive a real helper frame") {
                    ((field(it, "frames") as Array<*>)[0] as? TermuxWorkspaceFrame)?.target == "@0"
                }
                scenario.onActivity {
                    call(it, "newShell", 1, null, null)
                }
                choosePlacement(context.getString(R.string.workspace_split_top_bottom))
                await(scenario, "Second tile must own a distinct real tmux window") {
                    val state = field(it, "selection") as TermuxWorkspaceState
                    state.targets[1] != null && state.targets[1] != state.targets[0]
                }
                var selection = ""
                scenario.onActivity {
                    call(it, "focusTile", 1)
                    val input = it.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    val connection = input.onCreateInputConnection(EditorInfo())!!
                    assertTrue(connection.commitText("printf 'SECOND_TILE_INPUT'", 1))
                    call(it, "enter")
                    val state = field(it, "selection") as TermuxWorkspaceState
                    selection = state.encode()
                    assertEquals(1, state.focused)
                    assertTrue((field(it, "labels") as Array<*>)[1].let { label -> (label as android.widget.TextView).text.startsWith("▶") })
                }
                await(scenario, "Focused tile must render its own input") {
                    val frames = field(it, "frames") as Array<*>
                    (frames[1] as? TermuxWorkspaceFrame)?.body?.contains("SECOND_TILE_INPUT") == true &&
                        (frames[0] as? TermuxWorkspaceFrame)?.body?.contains("SECOND_TILE_INPUT") != true
                }
                scenario.onActivity {
                    val input = it.findViewById<TermuxWorkspaceInputEditText>(R.id.termux_workspace_input)
                    val connection = input.onCreateInputConnection(EditorInfo())!!
                    assertTrue(connection.commitText("printf '%s' '", 1))
                    @Suppress("UNCHECKED_CAST")
                    val modifiers = field(it, "modifiers") as MutableSet<String>
                    for ((modifier, text) in listOf("C" to "é", "M" to "😀", "S" to "e\u0301")) {
                        modifiers.add(modifier)
                        assertTrue(connection.commitText(text, 1))
                        assertTrue("Literal Unicode must consume unsupported modifier latch", modifiers.isEmpty())
                    }
                    assertTrue(connection.commitText("'", 1)); call(it, "enter")
                }
                await(scenario, "Modified Unicode must reach the focused real tmux window literally") {
                    ((field(it, "frames") as Array<*>)[1] as? TermuxWorkspaceFrame)?.body?.contains("é😀e\u0301") == true
                }
                scenario.onActivity {
                    call(it, "directText", ":switch 0😀")
                    call(it, "inputBackspace")
                    assertEquals("switch 0", field(it, "localDraft"))
                    assertNotNull((field(it, "frames") as Array<*>)[1])
                }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.onActivity { assertTrue((field(it, "clients") as Array<*>).all { client -> client == null }) }
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { attach(it, port!!) }
                await(scenario, "Resume must reconnect both selected windows") { (field(it, "clients") as Array<*>).all { client -> (client as? TermuxWorkspaceSocketClient)?.connected == true } }
                scenario.recreate()
                scenario.onActivity {
                    assertEquals(selection, (field(it, "selection") as TermuxWorkspaceState).encode())
                    assertEquals("switch 0", field(it, "localDraft"))
                    attach(it, port!!)
                }
                await(scenario, "Recreation must fetch fresh independent frames") { (field(it, "frames") as Array<*>).all { frame -> (frame as? TermuxWorkspaceFrame)?.body != null } }
                scenario.onActivity {
                    val state = field(it, "selection") as TermuxWorkspaceState
                    state.layout = TermuxWorkspaceState.Layout.SINGLE
                    call(it, "applyLayout")
                    assertEquals(listOf(1), state.visibleTiles)
                    assertEquals(2, state.targets.filterNotNull().distinct().size)
                    val panels = field(it, "panels") as Array<*>
                    assertEquals(View.GONE, (panels[0] as View).visibility)
                    assertEquals(View.VISIBLE, (panels[1] as View).visibility)
                }
            }
        } finally {
            instrumentation.waitForIdleSync()
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            edit.commit()
            assertEquals(original, prefs.all)
        }
    }
}
