package ohi.andre.consolelauncher.community

import android.content.Intent
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.Tuils

/** Store-clearing fixture: run only on a disposable emulator, never a user's persistent debug app. */
class CommunityInlineComposerIntegrationTest {
    @get:org.junit.Rule val windowPreferences = ohi.andre.consolelauncher.help.WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun call(activity: CommunityActivity, name: String, vararg args: Any) {
        activity.javaClass.declaredMethods.single { it.name == name && it.parameterCount == args.size }.apply { isAccessible = true }.invoke(activity, *args)
    }
    private fun submit(activity: CommunityActivity, value: String) {
        activity.findViewById<EditText>(R.id.community_input).setText(value)
        activity.findViewById<View>(R.id.community_send).performClick()
    }
    private fun run(seed: CommunityState = CommunityState(), test: (CommunityActivity) -> Unit) {
        assumeTrue("Disposable emulator only", Build.MODEL.startsWith("sdk_gphone"))
        val fixture = CommunityHttpFixture(); CommunityActivity.debugBackend = fixture.url
        val store = CommunityStore(context); store.clear(); store.save(seed.toJson())
        val activity = instrumentation.startActivitySync(Intent(context, CommunityActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as CommunityActivity
        instrumentation.waitForIdleSync()
        try { test(activity) } finally {
            instrumentation.runOnMainSync { activity.finish() }; instrumentation.waitForIdleSync()
            fixture.close(); CommunityActivity.debugBackend = null; store.clear()
        }
    }
    @Test fun startsExpandedWithCompactComposerAndRestoresUserWindowChoice() {
        run { activity ->
            instrumentation.runOnMainSync {
                val header = activity.findViewById<TextView>(R.id.community_header)
                val panel = (activity.findViewById<View>(R.id.community_panel).parent as View).parent as View
                assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, panel.layoutParams.width)
                assertEquals(View.GONE, header.rootView.findViewWithTag<View>("community-resize-corner").visibility)
                assertEquals(activity.getString(R.string.community_window_title), header.text.toString())
                assertTrue(header.contentDescription.toString().contains("experimental"))
                assertNotNull(header.layout)
                assertEquals("Expanded header fits", 0, header.layout.getEllipsisCount(0))
                val root = header.rootView
                fun measure() {
                    repeat(3) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                }
                val input = activity.findViewById<EditText>(R.id.community_input)
                val prompt = activity.findViewById<View>(R.id.community_prompt_row)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.community_guidance_row).visibility)
                val actions = activity.findViewById<View>(R.id.community_actions).parent as View
                assertEquals("No empty guidance gap", actions.bottom, prompt.top)
                val compactHeight = prompt.height
                assertTrue(compactHeight >= Tuils.dpToPx(activity, 48))
                input.setText("first\nsecond\nthird"); measure()
                assertTrue("Multiline drafts aren't clipped", prompt.height >= maxOf(compactHeight,
                    input.layout.height + input.compoundPaddingTop + input.compoundPaddingBottom))
                assertEquals(3, input.layout.lineCount)
                input.setText(""); measure()
                assertEquals(compactHeight, prompt.height)
                submit(activity, "/login"); measure()
                assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.community_guidance_row).visibility)
                activity.findViewById<View>(R.id.community_auth_cancel).performClick(); measure()
                assertEquals(View.GONE, activity.findViewById<View>(R.id.community_guidance_row).visibility)
                header.rootView.findViewWithTag<View>("community-window-toggle").performClick()
                assertEquals(View.VISIBLE, header.rootView.findViewWithTag<View>("community-resize-corner").visibility)
            }
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            var replacement: CommunityActivity? = null
            try {
                instrumentation.runOnMainSync { activity.recreate() }
                replacement = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals("Recreation preserves the chosen floating mode", View.VISIBLE,
                        replacement!!.window.decorView.findViewWithTag<View>("community-resize-corner").visibility)
                }
            } finally {
                instrumentation.removeMonitor(monitor)
                replacement?.let { next -> instrumentation.runOnMainSync { next.finish() } }
            }
        }
    }
    @Test fun reservedDockAndViewportDoNotJumpAcrossGeneralForumsThreadsAndMentions() {
        run(CommunityState().apply { draft = "general draft" }) { activity ->
            instrumentation.runOnMainSync {
                val root = activity.findViewById<View>(R.id.community_panel).rootView
                fun measure() {
                    repeat(3) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                }
                measure()
                val viewport = activity.findViewById<View>(R.id.community_scroll)
                val prompt = activity.findViewById<View>(R.id.community_prompt_row)
                val input = activity.findViewById<EditText>(R.id.community_input)
                val height = viewport.height; val dockHeight = prompt.height
                assertTrue(height > 0)
                for (channel in listOf("bugs", "help", "features", "presets", "general")) {
                    call(activity, "selectChannel", channel); measure()
                    assertEquals(height, viewport.height); assertEquals(dockHeight, prompt.height)
                }
                call(activity, "showMentions"); measure()
                assertEquals(height, viewport.height); assertEquals(dockHeight, prompt.height)
                assertEquals(View.INVISIBLE, prompt.visibility); assertFalse(input.isEnabled); assertFalse(input.hasFocus())
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, prompt.importantForAccessibility)
                val now = android.os.SystemClock.uptimeMillis()
                val event = android.view.MotionEvent.obtain(now, now, android.view.MotionEvent.ACTION_DOWN, input.x + 2, input.y + 2, 0)
                try { prompt.dispatchTouchEvent(event) } finally { event.recycle() }
                assertFalse(input.hasFocus())
                call(activity, "back"); measure()
                assertEquals(height, viewport.height); assertEquals("general draft", input.text.toString())
            }
        }
    }
    @Test fun sameComposerMasksSecretsAndCancelRestoresContextWithoutSerialization() {
        run(CommunityState().apply { draft = "general kept"; pane.channel = "help"; pane.composing = true; pane.saveDraft("help", "title kept", "forum kept") }) { activity ->
            instrumentation.runOnMainSync {
                val input = activity.findViewById<EditText>(R.id.community_input)
                submit(activity, "/register"); submit(activity, "alice")
                assertSame(input, activity.findViewById<EditText>(R.id.community_input))
                assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                assertFalse(input.isSaveEnabled)
                assertTrue(input.imeOptions and android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                input.setText("/exit123")
                call(activity, "saveCurrentDraft"); call(activity, "persist")
                assertFalse(CommunityStore(context).load().toString().contains("/exit123"))
                activity.findViewById<View>(R.id.community_auth_cancel).performClick()
                assertEquals("forum kept", input.text.toString())
                assertEquals("title kept", activity.findViewById<TextView>(R.id.community_thread_summary).text.toString())
                assertNull(input.transformationMethod)
                assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
                assertEquals("general kept", CommunityState(CommunityStore(context).load()).draft)
            }
        }
    }
    @Test fun keyboardEnterAdvancesUsernameAndRecreationDropsPasswordNotDraft() {
        run(CommunityState().apply { draft = "draft kept" }) { activity ->
            instrumentation.runOnMainSync { submit(activity, "/login"); activity.findViewById<EditText>(R.id.community_input).apply { setText("alice"); requestFocus() } }
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_ENTER)
            instrumentation.waitForIdleSync()
            var replacement: CommunityActivity? = null
            val monitor = instrumentation.addMonitor(CommunityActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync {
                    val input = activity.findViewById<EditText>(R.id.community_input)
                    assertTrue(input.transformationMethod is android.text.method.PasswordTransformationMethod)
                    input.setText("rotation-secret"); activity.recreate()
                }
                replacement = instrumentation.waitForMonitorWithTimeout(monitor, 5000) as CommunityActivity
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertEquals("draft kept", replacement!!.findViewById<EditText>(R.id.community_input).text.toString())
                    assertFalse(CommunityStore(context).load().toString().contains("rotation-secret"))
                    assertEquals(View.INVISIBLE, replacement!!.findViewById<View>(R.id.community_auth_cancel).visibility)
                }
            } finally { instrumentation.removeMonitor(monitor); replacement?.let { next -> instrumentation.runOnMainSync { next.finish() } } }
        }
    }
    @Test fun commandChipOnlyInsertsAndUsernameErrorStaysInline() {
        run { activity ->
            instrumentation.runOnMainSync {
                activity.findViewById<View>(R.id.community_completions).findViewWithTag<View>("community_command_/register").performClick()
                val input = activity.findViewById<EditText>(R.id.community_input)
                assertEquals("/register", input.text.toString())
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
                activity.findViewById<View>(R.id.community_send).performClick(); submit(activity, "bad username")
                assertEquals("bad username", input.text.toString())
                assertTrue(activity.findViewById<TextView>(R.id.community_auth_guidance).text.contains("ASCII"))
                submit(activity, "/cancel"); assertFalse(activity.isFinishing)
                assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
            }
        }
    }
    @Test fun newThreadCancelsPasswordAndConfirmationWithoutSubmittingPreservedDraft() {
        for (registering in listOf(false, true)) {
            run(CommunityState().apply { pane.channel = "help"; pane.saveDraft("help", "title kept", "forum kept") }) { activity ->
                instrumentation.runOnMainSync {
                    val input = activity.findViewById<EditText>(R.id.community_input)
                    submit(activity, if (registering) "/register" else "/login")
                    val guidance = activity.findViewById<TextView>(R.id.community_auth_guidance)
                    assertTrue(guidance.text.contains("3–24")); assertTrue(guidance.text.contains("No email"))
                    submit(activity, "alice")
                    assertTrue(guidance.text.contains("8–71")); assertTrue(guidance.text.contains("recovery"))
                    if (registering) submit(activity, "secret123")
                    activity.findViewById<View>(R.id.community_actions).findViewWithTag<View>("community_new_thread").performClick()
                    assertNull(input.transformationMethod)
                    assertEquals("forum kept", input.text.toString())
                    assertEquals("title kept", activity.findViewById<TextView>(R.id.community_thread_summary).text.toString())
                    assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.community_auth_cancel).visibility)
                    activity.findViewById<View>(R.id.community_send).performClick()
                    call(activity, "persist")
                    val restored = CommunityState(CommunityStore(context).load())
                    assertNull(restored.account)
                    assertEquals("forum kept", restored.pane.draft("help").optString("body"))
                    assertFalse(restored.toJson().toString().contains("secret123"))
                }
            }
        }
    }
    @Test fun suggestionsMatchHomeScreenGeometryAndKeepTouchTargetsAcrossAuthentication() {
        run { activity ->
            instrumentation.runOnMainSync {
                val saved = listOf(Suggestions.suggestions_size, Suggestions.suggestions_spaces, Suggestions.transparent_suggestions)
                    .associateWith { XMLPrefsManager.get(it) }
                try {
                    for ((size, spacing, transparent) in listOf(Triple("18", "15,15,25,20", "false"), Triple("32", "9,7,19,30", "false"), Triple("12", "2,0,8,0", "true"))) {
                        LauncherSettings.set(activity, Suggestions.suggestions_size, size)
                        LauncherSettings.set(activity, Suggestions.suggestions_spaces, spacing)
                        LauncherSettings.set(activity, Suggestions.transparent_suggestions, transparent)
                        call(activity, "stylePane"); call(activity, "renderCommandSuggestions")
                        val root = activity.findViewById<View>(R.id.community_panel).rootView
                        repeat(3) {
                            root.measure(View.MeasureSpec.makeMeasureSpec(root.width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(root.height, View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                        }
                        val rows = activity.findViewById<android.widget.LinearLayout>(R.id.community_completions)
                        val target = rows.getChildAt(0) as android.widget.FrameLayout
                        val chip = target.getChildAt(0) as TextView
                        val home = SuggestionChipStyler.createView(activity).apply { text = chip.text }
                        SuggestionChipStyler(activity).apply(home, SuggestionsManager.Suggestion.TYPE_COMMAND, null)
                        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        home.measure(unspecified, unspecified)
                        assertEquals(home.measuredHeight, chip.height)
                        assertEquals(home.measuredWidth, chip.width)
                        assertEquals(home.textSize, chip.textSize, 0f)
                        assertEquals(home.typeface, chip.typeface)
                        assertEquals(home.currentTextColor, chip.currentTextColor)
                        assertEquals(home.background.javaClass, chip.background.javaClass)
                        val spaces = spacing.split(',').map(String::toInt)
                        assertEquals(spaces[2], chip.paddingLeft); assertEquals(spaces[3], chip.paddingTop)
                        val margins = target.layoutParams as android.widget.LinearLayout.LayoutParams
                        assertEquals(spaces[0], margins.leftMargin); assertEquals(spaces[1], margins.topMargin)
                        assertTrue(target.height >= Tuils.dpToPx(activity, 48))
                        assertTrue(target.width >= Tuils.dpToPx(activity, 48))
                        assertEquals(chip.text.toString(), target.contentDescription.toString())
                        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, chip.importantForAccessibility)
                        val strip = activity.findViewById<View>(R.id.community_completion_scroll)
                        val height = strip.layoutParams.height
                        assertTrue(height >= chip.height + spaces[1] * 2)
                        target.performClick()
                        assertEquals(chip.text.toString(), activity.findViewById<EditText>(R.id.community_input).text.toString())
                        submit(activity, "/login"); submit(activity, "alice")
                        assertEquals(height, strip.layoutParams.height)
                        activity.findViewById<View>(R.id.community_auth_cancel).performClick()
                        assertEquals(height, strip.layoutParams.height)
                        activity.findViewById<EditText>(R.id.community_input).setText("")
                    }
                } finally { saved.forEach { (key, value) -> LauncherSettings.set(activity, key, value) } }
            }
        }
    }
    @Test fun inputBorderUsesSharedDashSettingsAndInputBorderToggle() {
        run { activity ->
            instrumentation.runOnMainSync {
                val settings = listOf<ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave>(
                    ohi.andre.consolelauncher.managers.xml.options.Ui.enable_dashed_border,
                    ohi.andre.consolelauncher.managers.xml.options.Ui.dashed_border_dash_length,
                    ohi.andre.consolelauncher.managers.xml.options.Ui.dashed_border_gap_length,
                    ohi.andre.consolelauncher.managers.xml.options.Ui.dashed_border_stroke_width,
                    ohi.andre.consolelauncher.managers.xml.options.Ui.dashed_border_corner_radius,
                    ohi.andre.consolelauncher.managers.xml.options.Behavior.enable_cyberdeck_mode,
                    ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption.input_border_enabled,
                    ohi.andre.consolelauncher.managers.xml.options.SurfaceBorderOption.input_border_color)
                val saved = settings.associateWith { XMLPrefsManager.get(it) }
                fun set(index: Int, value: String) { LauncherSettings.set(activity, settings[index], value); call(activity, "stylePane") }
                fun border() = activity.findViewById<EditText>(R.id.community_input).background as ohi.andre.consolelauncher.tuils.TerminalBorderDrawable
                fun pixels(): IntArray {
                    val image = android.graphics.Bitmap.createBitmap(240, 80, android.graphics.Bitmap.Config.ARGB_8888)
                    return try {
                        border().apply { setBounds(0, 0, 240, 80); draw(android.graphics.Canvas(image)) }
                        IntArray(240 * 80).also { image.getPixels(it, 0, 240, 0, 0, 240, 80) }
                    } finally { image.recycle() }
                }
                try {
                    listOf("true", "5", "3", "1.5", "0", "false", "true", "#FF112233").forEachIndexed { index, value -> set(index, value) }
                    val thin = border().strokeWidthPx
                    assertTrue(thin > 0)
                    val initial = pixels()
                    set(2, "9"); assertFalse("Gap changes the rendered input border", initial.contentEquals(pixels()))
                    set(1, "12"); assertFalse("Dash length changes the rendered input border", initial.contentEquals(pixels()))
                    set(3, "3"); assertTrue(border().strokeWidthPx > thin)
                    set(6, "false"); assertEquals("Input border switch is respected", 0, border().strokeWidthPx)
                    set(6, "true"); set(0, "false"); assertEquals("Master dash switch is respected", 0, border().strokeWidthPx)
                } finally {
                    saved.forEach { (key, value) -> LauncherSettings.set(activity, key, value) }
                    call(activity, "stylePane")
                }
            }
        }
    }
}
