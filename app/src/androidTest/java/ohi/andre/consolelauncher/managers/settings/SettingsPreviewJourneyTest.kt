package ohi.andre.consolelauncher.managers.settings

import android.app.Dialog
import android.app.Instrumentation
import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.SettingsAppearancePreview
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPreviewJourneyTest {
    private fun expanded(preview: SettingsAppearancePreview) = SettingsAppearancePreview::class.java
        .getDeclaredField("expanded").apply { isAccessible = true }.get(preview) as? Dialog

    private fun waitForExpandedFocus(preview: SettingsAppearancePreview) {
        repeat(30) {
            var focused = false
            SettingsPreviewFixture.instrumentation.runOnMainSync {
                focused = expanded(preview)?.window?.decorView?.hasWindowFocus() == true
            }
            if (focused) return
            Thread.sleep(100)
        }
        fail("Expanded preview never received window focus")
    }

    private fun discardChanges() = with(SettingsPreviewFixture) {
        var discard: AccessibilityNodeInfo? = null
        repeat(30) {
            if (discard == null) {
                discard = instrumentation.uiAutomation.rootInActiveWindow
                    ?.findAccessibilityNodeInfosByText(context.getString(R.string.editor_tuixtactivity_discard_36fff))
                    ?.firstOrNull { it.isClickable }
                if (discard == null) Thread.sleep(100)
            }
        }
        checkNotNull(discard) { "Discard confirmation missing" }
        assertTrue(discard!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)); settle()
    }

    @Test fun expansionBackAndRecreationKeepDraftWithoutWriting() = with(SettingsPreviewFixture) {
        withHome(mapOf(Ui.enable_dashed_border to "true", Ui.dashed_border_stroke_width to "1.5", Behavior.enable_cyberdeck_mode to "false")) {
            var activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
            val before = source(XMLPrefsManager.XMLPrefsRoot.UI).readBytes()
            val savedState = appearanceState()
            try {
                search(activity, "dashed_border_stroke_width"); edit(activity, "2.5")
                hideKeyboard(activity)
                capture("editor-current-home")
                repeat(3) {
                    instrumentation.runOnMainSync {
                        thumbnail(activity).performClick()
                        val dialog = checkNotNull(expanded(preview(activity)))
                        assertTrue(dialog.isShowing)
                        val scene = dialog.window!!.decorView.findViewById<View>(R.id.mainview)
                        assertTrue("Expanded scene must contain current Home content", views(scene).any { it.id == R.id.terminal_view })
                        assertFalse("Preview must not create a live command input", views(scene).any { it is EditText })
                        assertFalse("Frozen launcher controls must not retain actions", views(scene).any { it.hasOnClickListeners() || it.isLongClickable })
                    }
                    waitForExpandedFocus(preview(activity))
                    if (it == 0) capture("expanded-current-home")
                    instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle()
                    savedState.assertUnchanged()
                    instrumentation.runOnMainSync {
                        assertNull(expanded(preview(activity)))
                        assertEquals("2.5", adapter(activity).draftValue(Ui.dashed_border_stroke_width))
                    }
                }
                val monitor = Instrumentation.ActivityMonitor(TuixtActivity::class.java.name, null, false)
                instrumentation.addMonitor(monitor)
                try {
                    instrumentation.runOnMainSync { activity.recreate() }
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? TuixtActivity ?: error("Recreated editor was not observed")
                    settle()
                } finally { instrumentation.removeMonitor(monitor) }
                instrumentation.runOnMainSync {
                    assertEquals("2.5", adapter(activity).draftValue(Ui.dashed_border_stroke_width))
                    assertEquals(View.VISIBLE, thumbnail(activity).visibility)
                    assertFalse(ThemeColorResolver.hasPreview())
                }
                savedState.assertUnchanged()
                assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.UI).readBytes())
                assertEquals("1.5", LauncherSettings.get(Ui.dashed_border_stroke_width))
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun transparentThemeDraftExpandsAndCancelDiscardsIt() = with(SettingsPreviewFixture) {
        withHome(mapOf(Theme.output_background_color to "#00000000")) {
            val activity = open(XMLPrefsManager.XMLPrefsRoot.THEME)
            val before = source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes()
            val savedState = appearanceState()
            try {
                search(activity, "output_background_color"); edit(activity, "#62123456")
                hideKeyboard(activity)
                instrumentation.runOnMainSync { thumbnail(activity).performClick(); assertTrue(checkNotNull(expanded(preview(activity))).isShowing) }
                waitForExpandedFocus(preview(activity))
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); settle()
                assertEquals("#00000000", LauncherSettings.get(Theme.output_background_color))
                assertFalse(ThemeColorResolver.hasPreview())
                savedState.assertUnchanged()
                assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes())
                click(activity, R.string.editor_tuixtactivity_cancel_1507c)
                discardChanges()
                assertTrue(activity.isFinishing || activity.isDestroyed)
                savedState.assertUnchanged()
                assertArrayEquals(before, source(XMLPrefsManager.XMLPrefsRoot.THEME).readBytes())
                assertEquals("#00000000", LauncherSettings.get(Theme.output_background_color))
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }

    @Test fun editorLifecyclePreservesAnotherSurfaceColorPreview() = with(SettingsPreviewFixture) {
        withHome {
            val valuesField = ThemeColorResolver::class.java.getDeclaredField("previewValues").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val shared = valuesField.get(null) as MutableMap<Theme, String>
            val original = synchronized(shared) { HashMap(shared) }
            var activity: TuixtActivity? = null
            val marker = 0xFFCB2A7E.toInt()
            try {
                assertTrue(ThemeColorResolver.preview(Theme.ansi_magenta, "#FFCB2A7E"))
                val seeded = synchronized(shared) { HashMap(shared) }
                fun assertCacheUnchanged(stage: String) {
                    assertEquals("$stage changed another surface's color draft", marker, ThemeColorResolver.color(Theme.ansi_magenta))
                    assertEquals("$stage changed the global color preview map", seeded, synchronized(shared) { HashMap(shared) })
                }
                activity = open(XMLPrefsManager.XMLPrefsRoot.THEME)
                val savedState = appearanceState()
                assertCacheUnchanged("Opening editor")
                search(activity!!, "output_background_color"); edit(activity!!, "#62123456")
                assertCacheUnchanged("Local edits")
                val monitor = Instrumentation.ActivityMonitor(TuixtActivity::class.java.name, null, false)
                instrumentation.addMonitor(monitor)
                try {
                    instrumentation.runOnMainSync { activity!!.recreate() }
                    activity = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as? TuixtActivity ?: error("Recreated editor was not observed")
                    settle()
                } finally { instrumentation.removeMonitor(monitor) }
                assertCacheUnchanged("Recreation")
                hideKeyboard(activity!!)
                click(activity!!, R.string.editor_tuixtactivity_cancel_1507c); discardChanges()
                assertCacheUnchanged("Cancel")
                savedState.assertUnchanged()
            } finally {
                instrumentation.runOnMainSync {
                    activity?.finish()
                    ThemeColorResolver.clearPreview()
                    original.forEach { (role, value) -> ThemeColorResolver.preview(role, value) }
                }
            }
        }
    }

    @Test fun keyboardKeepsPreviewAndSaveVisible() = with(SettingsPreviewFixture) {
        withHome(mapOf(Ui.enable_dashed_border to "true")) {
            val activity = open(XMLPrefsManager.XMLPrefsRoot.UI)
            try {
                search(activity, "dashed_border_stroke_width")
                var originalHeight = 0
                instrumentation.runOnMainSync {
                    originalHeight = thumbnail(activity).height
                    val input = settings(activity).findViewById<EditText>(R.id.setting_input)
                    input.requestFocus()
                    (activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                        .showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
                var imeVisible = false
                repeat(50) {
                    instrumentation.runOnMainSync {
                        imeVisible = activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                    }
                    if(!imeVisible) Thread.sleep(100)
                }
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertTrue("Keyboard must be visible for this check", activity.window.decorView.rootWindowInsets.isVisible(android.view.WindowInsets.Type.ime()))
                    assertTrue("Preview should compact when keyboard opens", thumbnail(activity).height < originalHeight)
                    assertTrue("Settings list must retain editing space", settings(activity).height > 0)
                    val save = all(activity).filterIsInstance<TextView>().first { it.text == context.getString(R.string.editor_tuixtactivity_save_50815) }
                    assertTrue(save.getGlobalVisibleRect(Rect()))
                }
            } finally { instrumentation.runOnMainSync { activity.finish() } }
        }
    }
}
