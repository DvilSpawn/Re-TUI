package ohi.andre.consolelauncher.managers.termux

import android.content.Intent
import android.os.Build
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Only the disposable emulator: never changes a phone's launcher settings. */
class TerminalHomeIntegrationTest {
    private fun blockNotificationPrompt(instrumentation: android.app.Instrumentation): android.app.Instrumentation.ActivityMonitor {
        val monitor = object : android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): android.app.Instrumentation.ActivityResult? =
                if (intent.action == "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") android.app.Instrumentation.ActivityResult(0, null) else null
        }
        instrumentation.addMonitor(monitor)
        return monitor
    }
    @Test fun searchAndTerminalBarsHaveCyberdeckForegroundWithoutDashedBorders() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (mode in listOf("search", "terminal")) {
                val appearance = ohi.andre.consolelauncher.presentation.HomeAppearance(mapOf(
                    Behavior.enable_cyberdeck_mode to "true",
                    Behavior.launcher_mode to mode,
                    ohi.andre.consolelauncher.managers.xml.options.Ui.enable_dashed_border to "false"))
                val root = android.widget.FrameLayout(context)
                android.view.LayoutInflater.from(context).inflate(ohi.andre.consolelauncher.R.layout.launcher_home_scene, root, true)
                ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(root.findViewById(ohi.andre.consolelauncher.R.id.input_group),appearance)
                ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.configureSearch(root,appearance)
                val bar = root.findViewById<View>(ohi.andre.consolelauncher.R.id.input_group)
                assertNotNull("$mode bar needs a foreground frame",bar.foreground)
                val border = bar.foreground as? ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
                if (border != null) assertTrue("$mode Cyberdeck border was gated off",border.strokeWidthPx > 0)
            }
        }
    }
    @Test fun sharedSpecialKeysKeepModifiersAndFunctionSwipe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val root = android.view.LayoutInflater.from(context).inflate(ohi.andre.consolelauncher.R.layout.termux_special_keys, null)
            val modifiers = linkedSetOf<String>()
            var fn = false
            var sent = ""
            var refreshed = false
            fun bind() {
                TermuxSpecialKeys.bind(root, fn, modifiers, android.graphics.Color.WHITE,
                    { fn = it }, { refreshed = true }, { sent = modifiers.joinToString("") { "$it-" } + it })
            }
            fun key(label: String): android.widget.TextView = listOf(ohi.andre.consolelauncher.R.id.termux_workspace_keys_row_one, ohi.andre.consolelauncher.R.id.termux_workspace_keys_row_two)
                .flatMap { id -> val row = root.findViewById<android.widget.LinearLayout>(id); (0 until row.childCount).map { row.getChildAt(it) as android.widget.TextView } }
                .single { it.text.toString() == label }
            bind()
            key("CTRL").performClick(); assertTrue("C" in modifiers)
            bind(); key("↑").performClick(); assertEquals("C-Up", sent)
            key("REFRESH").performClick(); assertTrue(refreshed)
            val target = key("TAB")
            val now = android.os.SystemClock.uptimeMillis()
            for ((action, x) in listOf(android.view.MotionEvent.ACTION_DOWN to 0f, android.view.MotionEvent.ACTION_UP to (80f * context.resources.displayMetrics.density))) {
                val event = android.view.MotionEvent.obtain(now, now, action, x, 0f, 0)
                try { target.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            assertTrue(fn)
            modifiers.clear(); bind(); key("F1").performClick(); assertEquals("F1", sent)
        }
    }

    @Test fun searchOverlaysHomeAndBackRestoresSameGrid() {
        assumeTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        XMLPrefsManager.loadCommons(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val previous = LauncherSettings.get(Behavior.launcher_mode)
        val startup = context.getSharedPreferences("retui_startup", 0)
        val savedStartup = startup.all
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        LauncherMode.set(context, "terminal")
        var activity: LauncherActivity? = null
        val monitor = blockNotificationPrompt(instrumentation)
        try {
            val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            activity = launcher
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val manager = launcher.uiManager!!
                fun field(name: String) = UIManager::class.java.getDeclaredField(name).apply { isAccessible = true }.get(manager)
                val home = field("terminalHome") as TerminalHomeView
                val keys = launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.termux_workspace_tools)
                assertNotNull("Home must install the shared key bar before opening Add", keys)
                assertEquals(View.GONE, keys.visibility)
                assertSame(launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.persistent_input_container), keys.parent)
                val searchGroup = launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.input_group)
                val tabs = launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.search_mode_tabs)
                UIManager::class.java.getDeclaredField("keyboardVisible").apply { isAccessible = true }.setBoolean(manager, true)
                home.onTerminalFocusChanged?.invoke(true)
                assertEquals(View.GONE, searchGroup.visibility)
                assertEquals(View.GONE, tabs.visibility)
                assertEquals(View.VISIBLE, keys.visibility)
                home.onTerminalFocusChanged?.invoke(false)
                assertEquals(View.VISIBLE, searchGroup.visibility)
                assertEquals(View.VISIBLE, tabs.visibility)
                assertEquals(View.GONE, keys.visibility)
                UIManager::class.java.getDeclaredField("keyboardVisible").apply { isAccessible = true }.setBoolean(manager, false)
                val tray = field("terminalTrayContainer") as View
                if (Build.VERSION.SDK_INT >= 31) {
                    val scroll = home.getChildAt(1) as android.widget.ScrollView
                    assertFalse(scroll.clipToPadding)
                    assertTrue(scroll.paddingBottom > 0)
                }
                assertEquals(View.VISIBLE, tray.visibility)
                assertEquals(false, field("terminalSearchOpen"))
                assertEquals(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, tray.layoutParams.height)
                assertEquals(View.GONE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.search_mode_results_scroll).visibility)
                assertEquals(View.VISIBLE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.search_mode_clock_tab).visibility)
                assertEquals(View.VISIBLE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.terminal_home_add_tab).visibility)
                assertEquals(View.VISIBLE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.terminal_home_settings_tab).visibility)
                val search = launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.input_view)
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val touch = android.view.MotionEvent.obtain(now, now, action, 8f, 8f, 0)
                    try { search.dispatchTouchEvent(touch) } finally { touch.recycle() }
                }
                assertEquals(View.VISIBLE, tray.visibility)
                assertSame(home, field("terminalHome"))
                assertEquals(View.VISIBLE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.search_mode_results_scroll).visibility)
                manager.onBackPressed()
                assertEquals(View.VISIBLE, tray.visibility)
                assertEquals(View.GONE, launcher.findViewById<View>(ohi.andre.consolelauncher.R.id.search_mode_results_scroll).visibility)
                assertSame(home, field("terminalHome"))
            }
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.removeMonitor(monitor)
            LauncherSettings.set(context, Behavior.launcher_mode, previous)
            val editor = startup.edit().clear()
            savedStartup.forEach { (key, value) -> when (value) { is Boolean -> editor.putBoolean(key, value); is String -> editor.putString(key, value); is Int -> editor.putInt(key, value); is Long -> editor.putLong(key, value) } }
            editor.commit()
        }
    }
    @Test fun bottomBlurStaysWithinChromeWhileScrolling() {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val home = TerminalHomeView(instrumentation.targetContext)
            home.getChildAt(0).visibility = View.GONE
            val scroll = home.getChildAt(1) as android.widget.ScrollView
            scroll.getChildAt(0).minimumHeight = 1200
            home.setSearchBarClearance(80, 48)
            home.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            home.layout(0, 0, 240, 600)
            val frame = android.graphics.RenderNode("blur-bounds-test")
            for (position in listOf(0, 400, 680)) {
                scroll.scrollTo(0, position)
                val canvas = frame.beginRecording(240, 600)
                try { home.draw(canvas) } finally { frame.endRecording() }
                fun bound(name: String) = TerminalHomeView::class.java.getDeclaredField(name).apply { isAccessible = true }.getFloat(home)
                assertEquals("Blur must begin at the controls, regardless of scroll", 520f, bound("edgeMaskStart"), 0f)
                assertEquals("Bottom margin must remain outside the blur", 568f, bound("edgeMaskEnd"), 0f)
            }
            frame.discardDisplayList()
        }
    }

    @Test fun bottomEdgePreservesTerminalOpacityWithoutAddingABarBackdrop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val home = TerminalHomeView(instrumentation.targetContext)
            home.getChildAt(0).visibility = View.GONE
            val scroll = home.getChildAt(1) as android.widget.ScrollView
            scroll.getChildAt(0).apply { minimumHeight = 600; setBackgroundColor(android.graphics.Color.BLUE) }
            home.setSearchBarClearance(80)
            home.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            home.layout(0, 0, 240, 600)
            val bitmap = android.graphics.Bitmap.createBitmap(240, 600, android.graphics.Bitmap.Config.ARGB_8888)
            home.draw(android.graphics.Canvas(bitmap))
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(120, 250)))
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(120, 500)))
            assertEquals(255, android.graphics.Color.alpha(bitmap.getPixel(120, 598)))
            bitmap.recycle()
        }
    }

}
