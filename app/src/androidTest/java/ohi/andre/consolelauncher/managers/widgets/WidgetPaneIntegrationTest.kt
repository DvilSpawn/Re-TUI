package ohi.andre.consolelauncher.managers.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridLayout
import android.widget.TextView
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.SpaceManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetPaneIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun main(run: () -> Unit) = instrumentation.runOnMainSync(run)
    private fun settle() { instrumentation.waitForIdleSync(); Thread.sleep(400) }
    private fun manager(home: LauncherActivity) = home.uiManager!!.let { ui ->
        ui.javaClass.getDeclaredField("androidWidgetDrawerManager").apply { isAccessible = true }.get(ui) as AndroidWidgetDrawerManager
    }
    private fun field(manager: AndroidWidgetDrawerManager, name: String): Any? =
        manager.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(manager)
    private fun call(manager: AndroidWidgetDrawerManager, name: String) =
        manager.javaClass.getDeclaredMethod(name).apply { isAccessible = true }.invoke(manager)
    private fun <T : View> widgetView(home: LauncherActivity, id: Int): T =
        (field(manager(home), "drawerRoot") as View).findViewById(id)
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun capture(name: String) {
        instrumentation.uiAutomation.takeScreenshot().let { image ->
            File(context.filesDir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }

    internal fun fixture(keyboardOnStart: Boolean = false, systemBarsVisible: Boolean = false,
        run: (LauncherActivity, AndroidWidgetDrawerManager) -> Unit) {
        check(context.packageName.endsWith(".termuxsshdebug")) { "Use the isolated test application" }
        Tuils.init(context)
        val original = Tuils.getFolder()
        val temporary = File(context.filesDir, "widget-pane-fixture").apply { deleteRecursively(); mkdirs() }
        original.listFiles().orEmpty().filter { it.isFile && it.extension == "xml" }
            .forEach { it.copyTo(File(temporary, it.name)) }
        val folder = Tuils::class.java.getDeclaredField("folder").apply { isAccessible = true }
        folder.set(null, temporary)
        val clearFrames = FrameManager::class.java.getDeclaredMethod("clearCache").apply { isAccessible = true }
        clearFrames.invoke(FrameManager)
        context.getSharedPreferences("retui_startup", Context.MODE_PRIVATE).edit()
            .putBoolean("initialized", true).putBoolean("complete", true).putBoolean("active", false)
            .remove("default_launcher").commit()
        context.getSharedPreferences("android_widget_drawer", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(SpaceManager.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("terminal_windows", Context.MODE_PRIVATE).edit().clear().commit()
        XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
        ohi.andre.consolelauncher.managers.notifications.NotificationManager.create(context)
        LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Notifications.show_notifications, "false")
        LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Notifications.terminal_notifications, "false")
        LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification, "false")
        LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Behavior.auto_show_keyboard, keyboardOnStart.toString())
        if (systemBarsVisible) LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Ui.fullscreen, "false")
        ohi.andre.consolelauncher.managers.ChangelogManager.markSeen(context)
        var home: LauncherActivity? = null
        try {
            home = instrumentation.startActivitySync(Intent(context, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as LauncherActivity
            val landscape = InstrumentationRegistry.getArguments().getString("widgetOrientation") == "landscape"
            main { home.requestedOrientation = if (landscape) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            val expectedOrientation = if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT
            val deadline = android.os.SystemClock.uptimeMillis() + 10000
            while (home.resources.configuration.orientation != expectedOrientation && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(100)
            settle()
            assertEquals("The fixture must actually enter the requested orientation",
                if (landscape) android.content.res.Configuration.ORIENTATION_LANDSCAPE else android.content.res.Configuration.ORIENTATION_PORTRAIT,
                home.resources.configuration.orientation)
            val drawer = manager(home)
            main { home.uiManager!!.showAndroidWidgetDrawer() }
            settle()
            run(home, drawer)
        } finally {
            home?.let { main { it.finish() }; settle() }
            AppWidgetHost(context, 0x5744).deleteHost()
            context.getSharedPreferences("android_widget_drawer", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(SpaceManager.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            folder.set(null, original); clearFrames.invoke(FrameManager)
            XMLPrefsManager.dispose(); XMLPrefsManager.loadCommons(context); LauncherSettings.refreshFromLoadedPrefs()
            temporary.deleteRecursively()
        }
    }

    private fun addFixture(drawer: AndroidWidgetDrawerManager): Int {
        val providers = AppWidgetManager.getInstance(context).installedProviders
        val provider = providers.first { it.provider.className == WidgetPaneFixtureProvider::class.java.name }
        main {
            drawer.javaClass.getDeclaredMethod("beginAddWidget", android.appwidget.AppWidgetProviderInfo::class.java)
                .apply { isAccessible = true }.invoke(drawer, provider)
        }
        settle()
        val grid = (field(drawer, "widgetGrid") as GridLayout)
        assertEquals("Binding permission must be granted to isolated app before this test", 1, grid.childCount)
        return ((grid.getChildAt(0) as ViewGroup).getChildAt(0) as AppWidgetHostView).appWidgetId
    }

    @Test fun boardScrollsAndChipsMatchLauncherHeight() = fixture { home, drawer ->
        addFixture(drawer)
        val provider = AppWidgetManager.getInstance(context).installedProviders.first {
            it.provider.className == WidgetPaneFixtureProvider::class.java.name
        }
        repeat(2) {
            main { drawer.javaClass.getDeclaredMethod("beginAddWidget", android.appwidget.AppWidgetProviderInfo::class.java)
                .apply { isAccessible = true }.invoke(drawer, provider) }
            settle()
        }
        val preferences = context.getSharedPreferences("android_widget_drawer", 0)
        val records = org.json.JSONArray(preferences.getString("widgets", "[]"))
        for (index in 0 until records.length()) records.getJSONObject(index).put("row", index * 7)
        preferences.edit().putString("widgets", records.toString()).commit()
        main { drawer.refreshGrid() }; settle()
        val scroll = widgetView<android.widget.ScrollView>(home, R.id.android_widget_drawer_scroll)
        val rect = android.graphics.Rect()
        main {
            assertTrue("A long widget board must scroll", scroll.canScrollVertically(1))
            val grid = widgetView<GridLayout>(home, R.id.android_widget_grid)
            val cell = field(drawer, "lastRenderedCellSize") as Int
            val margin = Tuils.dpToPx(context, 4)
            assertEquals("Empty rows must occupy uniform cells instead of collapsing", 14 * cell + margin, grid.getChildAt(2).top)
            assertEquals("Widgets must keep their requested height inside a tall board", records.getJSONObject(2).getInt("rowSpan") * cell - margin * 2, grid.getChildAt(2).height)
            scroll.getGlobalVisibleRect(rect)
        }
        val x = rect.exactCenterX()
        val from = (rect.bottom - 20).toFloat()
        val to = (rect.top + 30).toFloat()
        // Use a timed touchscreen swipe instead of injecting future-dated events back-to-back.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "input swipe ${x.toInt()} ${from.toInt()} ${x.toInt()} ${to.toInt()} 350")).use { it.readBytes() }
        settle()
        main {
            assertTrue("Swiping the board must move the viewport", scroll.scrollY > 0)
            widgetView<View>(home, R.id.android_widget_command_toggle).performClick()
        }
        settle()
        main {
            val chip = widgetView<ViewGroup>(home, R.id.android_widget_suggestions_group).getChildAt(0) as TextView
            val launcherChip = ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler.createView(home).apply { text = chip.text }
            ohi.andre.consolelauncher.tuils.LauncherFontScale.applyRecursively(launcherChip)
            launcherChip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            assertEquals("Widget chips use the launcher's natural chip height", launcherChip.measuredHeight, chip.height)
        }
        capture("widgets-scroll-chips")
    }

    @Test fun resizingShowsNativeContentBeforeReleaseAndCancelRestoresIt() = fixture { home, drawer ->
        addFixture(drawer)
        val grid = widgetView<GridLayout>(home, R.id.android_widget_grid)
        val frame = grid.getChildAt(0) as ViewGroup
        val host = frame.getChildAt(0) as AppWidgetHostView
        val preferences = context.getSharedPreferences("android_widget_drawer", 0)
        main {
            widgetView<View>(home, R.id.android_widget_edit).performClick()
            val time = android.os.SystemClock.uptimeMillis()
            for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                android.view.MotionEvent.obtain(time, time + 1, action, frame.width / 2f, frame.height / 2f, 0).also { frame.dispatchTouchEvent(it); it.recycle() }
            }
        }
        settle()
        val saved = preferences.getString("widgets", "[]")!!
        val originalWidth = frame.width; val originalHeight = frame.height
        val hostWidth = host.width
        val originalOptions = AppWidgetManager.getInstance(context).getAppWidgetOptions(host.appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val cell = field(drawer, "lastRenderedCellSize") as Int
        fun grip() = views(frame).first { it.tag == "widget-resize-bottom-right" }
        var gestureX = 0f; var gestureY = 0f; var gestureDown = 0L
        fun touch(view: View, action: Int, dx: Float, dy: Float) = main {
            val time = android.os.SystemClock.uptimeMillis()
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                val location = IntArray(2); view.getLocationOnScreen(location)
                gestureX = location[0] + view.width / 2f
                gestureY = location[1] + view.height / 2f
                gestureDown = time
            }
            val origin = IntArray(2); frame.getLocationOnScreen(origin)
            android.view.MotionEvent.obtain(gestureDown, time, action, gestureX + dx, gestureY + dy, 0).also {
                it.offsetLocation(-origin[0].toFloat(), -origin[1].toFloat())
                frame.dispatchTouchEvent(it)
                it.recycle()
            }
        }
        var handle = grip()
        touch(handle, android.view.MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(handle, android.view.MotionEvent.ACTION_MOVE, -cell * .35f, cell * .4f)
        settle()
        main {
            assertTrue("The frame follows sub-cell drag movement before release", frame.width < originalWidth && frame.height > originalHeight)
            assertTrue("The real native widget is remeasured during the drag", host.width < hostWidth)
            assertSame(host, frame.getChildAt(0))
            assertEquals("A preview must not persist layout", saved, preferences.getString("widgets", "[]"))
            assertTrue("Providers receive preview size options before release",
                AppWidgetManager.getInstance(context).getAppWidgetOptions(host.appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) < originalOptions)
        }
        capture("widgets-live-resize")
        touch(handle, android.view.MotionEvent.ACTION_CANCEL, -cell * .35f, cell * .4f)
        settle()
        main {
            assertEquals(originalWidth, frame.width)
            assertEquals(originalHeight, frame.height)
            assertEquals(saved, preferences.getString("widgets", "[]"))
            assertEquals("Cancel restores the provider's previous options", originalOptions,
                AppWidgetManager.getInstance(context).getAppWidgetOptions(host.appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
        }
        handle = grip()
        touch(handle, android.view.MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(handle, android.view.MotionEvent.ACTION_MOVE, -cell * .65f, cell * .65f)
        settle()
        main { assertEquals(saved, preferences.getString("widgets", "[]")) }
        touch(handle, android.view.MotionEvent.ACTION_UP, -cell * .65f, cell * .65f)
        settle()
        main {
            val before = org.json.JSONArray(saved).getJSONObject(0)
            val after = org.json.JSONArray(preferences.getString("widgets", "[]")).getJSONObject(0)
            assertEquals(before.getInt("colSpan") - 1, after.getInt("colSpan"))
            assertEquals(before.getInt("rowSpan") + 1, after.getInt("rowSpan"))
            assertSame(host, frame.getChildAt(0))
        }
        val committed = preferences.getString("widgets", "[]")
        val committedOptions = AppWidgetManager.getInstance(context).getAppWidgetOptions(host.appWidgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        handle = grip()
        touch(handle, android.view.MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(handle, android.view.MotionEvent.ACTION_MOVE, cell * .3f, cell * .3f)
        settle()
        main {
            drawer.hide()
            assertEquals("Closing the pane cancels provider preview options", committedOptions,
                AppWidgetManager.getInstance(context).getAppWidgetOptions(host.appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH))
            assertEquals(committed, preferences.getString("widgets", "[]"))
            drawer.show()
        }
        settle()
        main { assertSame(host, (grid.getChildAt(0) as ViewGroup).getChildAt(0)) }
    }

    @Test fun nativeWidgetSurvivesEditingCommandsRetryAndHide() = fixture { home, drawer ->
        val root = home.window.decorView
        val grid = widgetView<GridLayout>(home, R.id.android_widget_grid)
        main {
            assertTrue(widgetView<View>(home, R.id.android_widget_empty).isShown)
            assertEquals(View.GONE, widgetView<View>(home, R.id.android_widget_commands).visibility)
            assertEquals("Close widgets", widgetView<View>(home, R.id.android_widget_drawer_add).contentDescription)
        }
        capture("widgets-empty")
        val id = addFixture(drawer)
        val host = (grid.getChildAt(0) as ViewGroup).getChildAt(0)
        var detaches = 0
        main { host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) { detaches++ }
        }) }
        main {
            val nativeText = host.findViewById<TextView>(android.R.id.text1)
            val size = nativeText.textSize
            val face = nativeText.typeface
            LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Ui.font_size_offset, "8")
            home.uiManager!!.javaClass.getDeclaredMethod("refreshLauncherTypeface").apply { isAccessible = true }.invoke(home.uiManager)
            assertEquals(size, nativeText.textSize, .01f)
            assertEquals(face, nativeText.typeface)
            LauncherSettings.set(context, ohi.andre.consolelauncher.managers.xml.options.Ui.font_size_offset, "0")
            home.uiManager!!.javaClass.getDeclaredMethod("refreshLauncherTypeface").apply { isAccessible = true }.invoke(home.uiManager)
        }
        main {
            assertFalse(views(grid).filterIsInstance<TextView>().any { it.text.toString().startsWith("[W_ID:") })
            widgetView<View>(home, R.id.android_widget_edit).performClick()
        }
        settle()
        main {
            assertSame(host, (grid.getChildAt(0) as ViewGroup).getChildAt(0))
            assertFalse("Editing controls must not cover widgets with ID badges", views(grid).filterIsInstance<TextView>().any { it.text.toString().startsWith("[W_ID:") })
            widgetView<View>(home, R.id.android_widget_edit).performClick()
            widgetView<View>(home, R.id.android_widget_command_toggle).performClick()
        }
        settle()
        main {
            assertTrue(widgetView<View>(home, R.id.android_widget_commands).isShown)
            assertEquals("Editing and commands must keep the native view attached", 0, detaches)
            assertTrue(drawer.handleBack())
            assertEquals(View.GONE, widgetView<View>(home, R.id.android_widget_commands).visibility)
            host.findViewById<TextView>(android.R.id.text1).performClick()
        }
        settle()
        main { assertEquals("Widget tapped", host.findViewById<TextView>(android.R.id.text1).text.toString()) }
        capture("widgets-native")
        val frame = grid.getChildAt(0)
        val down = android.os.SystemClock.uptimeMillis()
        main { frame.dispatchTouchEvent(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, 10f, 10f, 0)) }
        Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 100)
        main {
            val cell = field(drawer, "lastRenderedCellSize") as Int
            val end = android.os.SystemClock.uptimeMillis()
            frame.dispatchTouchEvent(android.view.MotionEvent.obtain(down, end, android.view.MotionEvent.ACTION_MOVE, 10f, 10f + cell * 3, 0))
            frame.dispatchTouchEvent(android.view.MotionEvent.obtain(down, end + 1, android.view.MotionEvent.ACTION_UP, 10f, 10f + cell * 3, 0))
        }
        settle()
        main {
            val saved = org.json.JSONArray(context.getSharedPreferences("android_widget_drawer", 0).getString("widgets", "[]"))
            assertEquals("Long-press drag must retain the requested row", 3, saved.getJSONObject(0).getInt("row"))
            assertSame(host, (grid.getChildAt(0) as ViewGroup).getChildAt(0))
        }
        main {
            assertEquals("Selected widgets expose four corner grips", 4, views(grid).count { it.tag?.toString()?.startsWith("widget-resize-") == true })
            val cell = field(drawer, "lastRenderedCellSize") as Int
            val selected = grid.getChildAt(0)
            val time = android.os.SystemClock.uptimeMillis()
            val x = selected.width / 2f; val y = selected.height / 2f
            selected.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
            selected.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 20, android.view.MotionEvent.ACTION_MOVE, x, y + cell, 0))
            selected.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 40, android.view.MotionEvent.ACTION_UP, x, y + cell, 0))
            val saved = org.json.JSONArray(context.getSharedPreferences("android_widget_drawer", 0).getString("widgets", "[]"))
            assertEquals("Edit mode must drag immediately without another hold", 4, saved.getJSONObject(0).getInt("row"))
        }
        main {
            val before = org.json.JSONArray(context.getSharedPreferences("android_widget_drawer", 0).getString("widgets", "[]")).getJSONObject(0)
            val right = views(grid).first { it.tag == "widget-resize-bottom-right" }
            val cell = field(drawer, "lastRenderedCellSize") as Int
            val time = android.os.SystemClock.uptimeMillis()
            right.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time, android.view.MotionEvent.ACTION_DOWN, 24f, 24f, 0))
            right.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 20, android.view.MotionEvent.ACTION_MOVE, 24f - cell, 24f, 0))
            right.dispatchTouchEvent(android.view.MotionEvent.obtain(time, time + 40, android.view.MotionEvent.ACTION_UP, 24f - cell, 24f, 0))
            val after = org.json.JSONArray(context.getSharedPreferences("android_widget_drawer", 0).getString("widgets", "[]")).getJSONObject(0)
            assertEquals("Shrinking right must leave the left edge anchored", before.getInt("col"), after.getInt("col"))
            assertEquals(before.getInt("colSpan") - 1, after.getInt("colSpan"))
            assertSame(host, (grid.getChildAt(0) as ViewGroup).getChildAt(0))
        }
        settle()
        capture("widgets-edit")
        main { assertTrue(drawer.handleBack()) }
        main { (host as AppWidgetHostView).updateAppWidget(RemoteViews(instrumentation.context.packageName, 0)) }
        settle()
        main {
            val failure = views(host).filterIsInstance<TextView>().first { it.text.toString() == context.getString(R.string.widget_pane_retry) }
            (failure.parent as View).performClick()
            assertNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
            failure.performClick()
        }
        settle()
        main {
            assertNotSame(host, (grid.getChildAt(0) as ViewGroup).getChildAt(0))
            assertNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
            drawer.hide()
            assertEquals(false, field(drawer, "listening"))
            drawer.show()
        }
        settle()
        main { assertEquals(1, grid.childCount) }
    }

    @Test fun expandedPaneIgnoresFloatingGeometryAndRetainsBindingsAcrossRecreation() = fixture { home, drawer ->
        val id = addFixture(drawer)
        val windows = context.getSharedPreferences("terminal_windows", Context.MODE_PRIVATE)
        windows.edit().putBoolean("widgets_maximized", false).putFloat("widgets_width", .5f)
            .putFloat("widgets_height", .5f).putFloat("widgets_left", .25f).putFloat("widgets_top", .25f).commit()
        val savedGeometry = windows.all.toMap()
        fun checkPane(activity: LauncherActivity) {
            val surface = widgetView<View>(activity, R.id.android_widget_surface)
            val viewport = widgetView<View>(activity, R.id.android_widget_viewport)
            assertSame("The widget pane no longer has a floating wrapper", viewport, surface.parent)
            assertEquals(viewport.width, surface.width)
            assertEquals(viewport.height, surface.height)
            for (tag in listOf("window-toggle", "window-reset", "resize-corner", "resize-top-left", "resize-top-right", "resize-bottom-left", "header-actions")) {
                assertNull("No widget-pane window controls remain: $tag", activity.window.decorView.findViewWithTag<View>("widgets-$tag"))
            }
            val title = android.graphics.Rect(); val close = android.graphics.Rect()
            assertTrue(widgetView<View>(activity, R.id.android_widget_drawer_header).getGlobalVisibleRect(title))
            assertTrue(widgetView<View>(activity, R.id.android_widget_drawer_add).getGlobalVisibleRect(close))
            assertFalse("Close must not overlap the title", android.graphics.Rect.intersects(title, close))
        }
        main { checkPane(home); drawer.hide() }
        val monitor = android.app.Instrumentation.ActivityMonitor(LauncherActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        val replacement = try {
            main { home.recreate() }
            checkNotNull(instrumentation.waitForMonitorWithTimeout(monitor, 10000)) as LauncherActivity
        } finally { instrumentation.removeMonitor(monitor) }
        try {
            settle()
            val restored = manager(replacement)
            main { restored.show() }
            settle()
            main {
                checkPane(replacement)
                assertEquals(1, widgetView<GridLayout>(replacement, R.id.android_widget_grid).childCount)
                assertNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
                restored.hide()
            }
            assertEquals("Widget pane no longer writes floating geometry", savedGeometry, windows.all)
        } finally { main { replacement.finish() }; settle() }
    }

    @Test fun commandInputRemainsVisibleAboveTheKeyboard() = fixture { home, drawer ->
        main {
            widgetView<View>(home, R.id.android_widget_command_toggle).performClick()
            val input = widgetView<EditText>(home, R.id.android_widget_input)
            input.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        Thread.sleep(1000); settle()
        main {
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(home.window.decorView)!!
            assertTrue("Widget command keyboard should open", insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()))
            val bounds = android.graphics.Rect()
            assertTrue(widgetView<View>(home, R.id.android_widget_input).getGlobalVisibleRect(bounds))
            val keyboardTop = home.window.decorView.height - insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            assertTrue("Widget input is covered by the keyboard", bounds.bottom <= keyboardTop)
        }
        capture("widgets-keyboard")
        main { assertTrue(drawer.handleBack()); assertTrue(drawer.isOpen) }
    }

    @Test fun pickerSearchAndBackKeepThePaneOpen() = fixture { home, drawer ->
        main { widgetView<View>(home, R.id.android_widget_empty_add).performClick() }
        settle()
        val deadline = android.os.SystemClock.uptimeMillis() + 15000
        var loaded = false
        while (!loaded && android.os.SystemClock.uptimeMillis() < deadline) {
            main {
                val overlay = field(drawer, "pickerOverlay") as View
                loaded = views(overlay).any { it.contentDescription?.toString()?.contains("ReTUI Widget Fixture") == true }
            }
            if (!loaded) Thread.sleep(100)
        }
        assertTrue("The asynchronous provider list must finish loading before searching", loaded)
        main {
            val overlay = field(drawer, "pickerOverlay") as View
            val search = overlay.findViewWithTag<EditText>("widget-picker-search")
            search.setText("this widget cannot possibly exist")
            assertTrue(views(overlay).filterIsInstance<TextView>().any { it.text.toString() == context.getString(R.string.widget_pane_no_matches) })
            search.setText("ReTUI Widget Fixture")
            assertTrue(views(overlay).any { it.contentDescription?.toString()?.contains("ReTUI Widget Fixture") == true })
        }
        capture("widgets-picker")
        main { assertTrue(drawer.handleBack()); assertTrue(drawer.isOpen); assertNull(field(drawer, "pickerOverlay")) }
    }

    @Test fun emptyAndClonedSpacesKeepBindingsAndUnreadableSnapshotsBlockCleanup() = fixture { home, drawer ->
        val empty = SpaceManager.ensureInitialized(context)
        SpaceManager.createFromActive(context, "Populated")
        val id = addFixture(drawer)
        val populated = SpaceManager.saveActive(context)
        main { drawer.hide() }
        SpaceManager.switchTo(context, empty.id)
        main { drawer.show() }; settle()
        assertNotNull("Opening an empty Space must retain other Space bindings", AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
        val manifest = File(Tuils.getFolder(), "spaces/${populated.id}/manifest.properties")
        val originalManifest = manifest.readBytes()
        try {
            manifest.writeText("damaged manifest")
            assertTrue("A damaged Space manifest must not hide its widget ownership", SpaceManager.widgetIdsInOtherSpaces(context)!!.contains(id))
        } finally { manifest.writeBytes(originalManifest) }
        main { drawer.hide() }
        SpaceManager.switchTo(context, populated.id)
        SpaceManager.createFromActive(context, "Shared")
        main {
            drawer.show()
            drawer.javaClass.getDeclaredMethod("removeWidgetById", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }.invoke(drawer, id)
        }
        settle()
        assertNotNull("Removing a shared widget only removes the active placement", AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
        main { drawer.hide() }
        SpaceManager.switchTo(context, populated.id)
        main { drawer.show() }; settle()
        main { assertEquals(1, widgetView<GridLayout>(home, R.id.android_widget_grid).childCount) }
        File(Tuils.getFolder(), "spaces/${empty.id}/shared_prefs/android_widget_drawer.properties").writeText("corrupted snapshot")
        assertNull("Unreadable snapshots must prevent destructive cleanup", SpaceManager.widgetIdsInOtherSpaces(context))
        main { drawer.refreshGrid() }; settle()
        assertNotNull(AppWidgetManager.getInstance(context).getAppWidgetInfo(id))
    }
}
