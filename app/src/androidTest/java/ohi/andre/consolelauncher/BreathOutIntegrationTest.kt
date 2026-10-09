package ohi.andre.consolelauncher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test

class BreathOutIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun withLauncher(test: (LauncherActivity, FrameLayout) -> Unit) {
        val context = instrumentation.targetContext
        check(context.packageName == "com.dvil.tui_renewed")
        check(android.os.Build.MODEL.contains("sdk_gphone")) { "Disposable emulator fixture only" }
        if (BuildConfig.FLAVOR == "fdroid") {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation
                .executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow"))
                .use { it.readBytes() }
        }
        XMLPrefsManager.loadCommons(context)
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.refreshFromLoadedPrefs()
        val fixture: List<Pair<XMLPrefsSave, String>> = listOf(
            Ui.show_weather to "false", Behavior.tui_notification to "false",
            Notifications.show_notifications to "false", Notifications.terminal_notifications to "false",
            Behavior.auto_show_keyboard to "false", Ui.system_wallpaper to "false",
            Behavior.breath_out to "false", Behavior.breath_out_seconds to "900",
            Behavior.launcher_mode to "classic", Behavior.duo_mode to "false",
            Behavior.enable_cyberdeck_mode to "false"
        )
        val original = fixture.associate { it.first to LauncherSettings.get(it.first) }
        fixture.forEach { (key, value) -> LauncherSettings.set(key, value) }
        val startup = context.getSharedPreferences("retui_startup", 0)
        val complete = startup.getBoolean("complete", false)
        val active = startup.getBoolean("active", false)
        startup.edit().putBoolean("complete", true).putBoolean("active", false).commit()
        val launcher = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
        try {
            instrumentation.waitForIdleSync()
            assertNotNull("Initialized launcher", launcher.uiManager)
            val root = launcher.findViewById<FrameLayout>(R.id.mainview)
            val deadline = SystemClock.uptimeMillis() + 30000
            var focused = false
            while (!focused && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { focused = launcher.hasWindowFocus() && root.isShown && root.width > 0 }
                if (!focused) SystemClock.sleep(50)
            }
            assertTrue("Fixture requires a rendered, focused launcher without system dialogs", focused)
            assertEquals("All launcher modes need a shared fading root", "BreathOutLayout", root.javaClass.simpleName)
            test(launcher, root)
        } finally {
            instrumentation.runOnMainSync { launcher.finish() }
            instrumentation.waitForIdleSync()
            original.forEach { (key, value) -> LauncherSettings.set(key, value) }
            startup.edit().putBoolean("complete", complete).putBoolean("active", active).commit()
        }
    }

    private fun configure(root: FrameLayout, enabled: Boolean, seconds: Int) {
        LauncherSettings.set(Behavior.breath_out_seconds, seconds.toString())
        LauncherSettings.set(Behavior.breath_out, enabled.toString())
        (root.context as LauncherActivity).refreshBreathOutSettings()
    }

    private fun tap(root: FrameLayout) {
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, time, action, 20f, 20f, 0)
            root.dispatchTouchEvent(event)
            event.recycle()
        }
    }

    private fun awaitAlpha(root: View, expected: Float) {
        val deadline = SystemClock.uptimeMillis() + 8000
        var actual = -1f
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync { actual = root.alpha }
            if (kotlin.math.abs(actual - expected) < 0.001f) return
            SystemClock.sleep(50)
        }
        assertEquals("Fade did not finish", expected, actual, 0.001f)
    }

    private fun tapScreen(root: View) {
        val position = IntArray(2)
        instrumentation.runOnMainSync {
            root.getLocationOnScreen(position)
            position[0] += root.width / 2
            position[1] += root.height / 2
        }
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, position[0].toFloat(), position[1].toFloat(), 0).also {
                it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try {
                    assertTrue("Wake tap must be delivered through the window", instrumentation.uiAutomation.injectInputEvent(it, true))
                } finally {
                    it.recycle()
                }
            }
        }
    }

    @Test fun idleFadesEntireRootAndWakeTapDoesNotActivateChildren() = withLauncher { launcher, root ->
        var clicks = 0
        instrumentation.runOnMainSync {
            root.addView(View(launcher).apply { setOnClickListener { clicks++ } }, FrameLayout.LayoutParams(-1, -1))
            configure(root, true, 1)
            assertEquals("Wallpaper reveal needs a translucent render surface", android.graphics.PixelFormat.TRANSLUCENT, launcher.window.attributes.format)
        }
        awaitAlpha(root, 0f)
        instrumentation.runOnMainSync {
            assertEquals(0f, root.alpha, 0.001f)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, root.importantForAccessibility)
            assertTrue(launcher.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0)
            assertEquals(Color.TRANSPARENT, (launcher.window.decorView.background as ColorDrawable).color)
            tap(root)
        }
        awaitAlpha(root, 1f)
        instrumentation.runOnMainSync {
            assertEquals(1f, root.alpha, 0.001f)
            assertEquals(0, clicks)
            tap(root)
        }
        SystemClock.sleep(100)
        instrumentation.runOnMainSync {
            assertEquals(1, clicks)
            configure(root, false, 1)
        }
    }

    @Test fun typingRestartsTimeoutAndDisablingRestoresImmediately() = withLauncher { launcher, root ->
        lateinit var editor: EditText
        instrumentation.runOnMainSync {
            editor = EditText(launcher)
            root.addView(editor)
            configure(root, true, 1)
        }
        SystemClock.sleep(700)
        instrumentation.runOnMainSync {
            assertEquals("Still visible before typing", 1f, root.alpha, 0.001f)
            editor.setText("unfinished draft")
        }
        SystemClock.sleep(700)
        instrumentation.runOnMainSync { assertEquals(1f, root.alpha, 0.001f) }
        SystemClock.sleep(800)
        instrumentation.runOnMainSync {
            assertEquals(0f, root.alpha, 0.001f)
            configure(root, false, 1)
            assertEquals(1f, root.alpha, 0.001f)
            assertEquals("unfinished draft", editor.text.toString())
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, root.importantForAccessibility)
        }
        SystemClock.sleep(1200)
        instrumentation.runOnMainSync { assertEquals(1f, root.alpha, 0.001f) }
    }

    @Test fun everyModeUsesSameFadeBoundaryAndRetainsDraftThroughRefresh() = withLauncher { launcher, _ ->
        val configurations = ohi.andre.consolelauncher.managers.settings.LauncherMode.values.map {
            Triple(it, false, false)
        } + listOf(Triple("classic", true, false), Triple("classic", false, true))
        for ((mode, duo, cyberdeck) in configurations) {
            instrumentation.runOnMainSync {
                LauncherSettings.set(Behavior.breath_out, "false")
                LauncherSettings.set(Behavior.launcher_mode, mode)
                LauncherSettings.set(Behavior.duo_mode, duo.toString())
                LauncherSettings.set(Behavior.enable_cyberdeck_mode, cyberdeck.toString())
                launcher.findViewById<EditText>(R.id.input_view).apply {
                    setText("retained $mode draft")
                    setSelection(2, 5)
                }
                launcher.refreshUiInPlace()
            }
            instrumentation.waitForIdleSync()
            lateinit var root: FrameLayout
            instrumentation.runOnMainSync {
                root = launcher.findViewById(R.id.mainview)
                assertEquals("BreathOutLayout", root.javaClass.simpleName)
                launcher.findViewById<EditText>(R.id.input_view).let { editor ->
                    assertEquals("Populated draft must survive mode refresh", "retained $mode draft", editor.text.toString())
                    assertEquals(2, editor.selectionStart)
                    assertEquals(5, editor.selectionEnd)
                }
                configure(root, true, 1)
            }
            awaitAlpha(root, 0f)
            instrumentation.runOnMainSync {
                assertSame("Mode root must still be attached", root, launcher.findViewById(R.id.mainview))
                assertTrue("Root must own the focused window", root.isAttachedToWindow && launcher.hasWindowFocus())
                assertEquals("$mode duo=$duo cyberdeck=$cyberdeck", 0f, root.alpha, 0.001f)
                val content = launcher.findViewById<View>(android.R.id.content)
                val bitmap = android.graphics.Bitmap.createBitmap(content.width, content.height, android.graphics.Bitmap.Config.ARGB_8888)
                content.draw(android.graphics.Canvas(bitmap))
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                bitmap.recycle()
                assertEquals("Hidden content must draw no launcher pixels", 0, pixels.count { Color.alpha(it) != 0 })
            }
            if (mode == "search" && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(500)
                val copy = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                val copied = java.util.concurrent.CountDownLatch(1)
                var result = -1
                instrumentation.runOnMainSync {
                    android.view.PixelCopy.request(launcher.window, copy, { result = it; copied.countDown() }, android.os.Handler(android.os.Looper.getMainLooper()))
                }
                assertTrue(copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertEquals(android.view.PixelCopy.SUCCESS, result)
                java.io.File(launcher.cacheDir, "breath-out-window.png").outputStream().use {
                    copy.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                assertEquals("Hidden window must submit transparent launcher pixels", 0, Color.alpha(copy.getPixel(copy.width / 2, copy.height / 2)))
                copy.recycle()
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { image ->
                    java.io.File(launcher.cacheDir, "breath-out-hidden.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
                instrumentation.runOnMainSync { assertEquals("Screenshot must not wake launcher", 0f, root.alpha, 0.001f) }
            }
            tapScreen(root)
            awaitAlpha(root, 1f)
            instrumentation.runOnMainSync {
                assertEquals(1f, root.alpha, 0.001f)
                assertEquals("retained $mode draft", launcher.findViewById<EditText>(R.id.input_view).text.toString())
                assertEquals(2, launcher.findViewById<EditText>(R.id.input_view).selectionStart)
                assertEquals(5, launcher.findViewById<EditText>(R.id.input_view).selectionEnd)
                configure(root, false, 1)
            }
            if (mode == "search") {
                instrumentation.waitForIdleSync()
                SystemClock.sleep(500)
                checkNotNull(instrumentation.uiAutomation.takeScreenshot()).let { image ->
                    java.io.File(launcher.cacheDir, "breath-out-restored.png").outputStream().use {
                        image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image.recycle()
                }
            }
        }
    }

    @Test fun repeatedForegroundSignalDoesNotWakeHiddenLauncher() = withLauncher { _, root ->
        instrumentation.runOnMainSync { configure(root, true, 1) }
        awaitAlpha(root, 0f)
        instrumentation.runOnMainSync {
            (root as BreathOutLayout).setActive(true)
            assertEquals("Duplicate focus must not wake hidden UI", 0f, root.alpha, 0.001f)
            configure(root, false, 1)
        }
    }

    @Test fun foregroundPauseCancelsTimerAndWakeKeyIsConsumed() = withLauncher { _, root ->
        instrumentation.runOnMainSync {
            configure(root, true, 1)
            root.post { (root as BreathOutLayout).setActive(false) }
        }
        SystemClock.sleep(1600)
        instrumentation.runOnMainSync {
            assertEquals(1f, root.alpha, 0.001f)
            (root as BreathOutLayout).setActive(true)
        }
        SystemClock.sleep(1600)
        instrumentation.runOnMainSync {
            assertEquals(0f, root.alpha, 0.001f)
            assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
            assertTrue(root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)))
        }
        SystemClock.sleep(350)
        instrumentation.runOnMainSync {
            assertEquals(1f, root.alpha, 0.001f)
            configure(root, true, Int.MAX_VALUE)
        }
        SystemClock.sleep(1500)
        instrumentation.runOnMainSync {
            assertEquals("Long timeout must not overflow", 1f, root.alpha, 0.001f)
            configure(root, true, -1)
        }
        SystemClock.sleep(1500)
        instrumentation.runOnMainSync {
            assertEquals("Invalid timeout falls back to default", 1f, root.alpha, 0.001f)
            configure(root, false, 1)
        }
    }
}
