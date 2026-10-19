package ohi.andre.consolelauncher.help

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.ChangelogManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Standalone pane fixture; never launches account/network/setup workflows. */
@RunWith(AndroidJUnit4::class)
class ChangelogPaneIntegrationTest {
    @Test fun outdatedLanguagePackCannotReplaceInstalledReleaseNotes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val packs = ohi.andre.consolelauncher.localization.LanguagePacks
        val original = packs.selectedId()
        val id = "changelog-stale-${System.nanoTime()}"
        try {
            val archive = ohi.andre.consolelauncher.localization.LanguagePackTest().archive(id,
                xml = """<resources><string name="changelog_release_highlights">PREVIOUS BUILD NOTES</string></resources>""")
            packs.import(archive.inputStream())
            packs.select(id)
            ActivityScenario.launch<ChangelogActivity>(Intent(context, ChangelogActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    assertEquals("PREVIOUS BUILD NOTES", it.getString(R.string.changelog_release_highlights))
                    val output = it.findViewById<TextView>(R.id.offline_help_output).text.toString()
                    assertTrue(output.contains("Termux, connected through SSH"))
                    assertTrue(output.contains("Hotfix #1"))
                    assertFalse(output.contains("PREVIOUS BUILD NOTES"))
                }
            }
        } finally {
            packs.select(original)
            if (packs.installed().any { it.id == id }) packs.remove(id)
        }
    }

    @get:Rule val windows = WindowPreferencesRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun bundledPaneAcknowledgesVersionKeepsScrollAndClosesWithBack() {
        val prefs = context.getSharedPreferences("changelogPrefs", Context.MODE_PRIVATE)
        val before = prefs.all.toMap()
        try {
            prefs.edit().putInt("last_seen_version_code", BuildConfig.VERSION_CODE - 1).commit()
            ActivityScenario.launch<ChangelogActivity>(Intent(context, ChangelogActivity::class.java)).use { scenario ->
                instrumentation.waitForIdleSync()
                var offset = 0
                scenario.onActivity {
                    val output = it.findViewById<TextView>(R.id.offline_help_output)
                    assertTrue(output.text.contains("Build ${BuildConfig.VERSION_CODE}"))
                    assertTrue(output.text.contains("Termux, connected through SSH"))
                    assertTrue(output.text.contains("Hotfix #1"))
                    assertTrue(output.isTextSelectable)
                    assertFalse(it.findViewById<EditText>(R.id.offline_help_input).isShown)
                    assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("last_seen_version_code", 0))
                    assertFalse(ChangelogManager.maybeOpen(it))
                }
                instrumentation.waitForIdleSync()
                android.os.SystemClock.sleep(300)
                scenario.onActivity { assertTrue("Changelog must be foreground for capture", it.hasWindowFocus()) }
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    File(context.getExternalFilesDir(null), "changelog-phone.png").outputStream().use {
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                } finally { screenshot.recycle() }
                scenario.onActivity {
                    val scroll = it.findViewById<ScrollView>(R.id.offline_help_scroll)
                    scroll.scrollTo(0, 250); offset = scroll.scrollY
                    assertTrue(offset > 0)
                }
                scenario.recreate()
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals(offset, it.findViewById<ScrollView>(R.id.offline_help_scroll).scrollY)
                }
                for ((requested, expected) in listOf(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT
                )) {
                    scenario.onActivity { it.requestedOrientation = requested }
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5000
                    var oriented = false
                    do {
                        instrumentation.waitForIdleSync()
                        android.os.SystemClock.sleep(100)
                        scenario.onActivity { oriented = it.resources.configuration.orientation == expected }
                    } while (!oriented && android.os.SystemClock.elapsedRealtime() < deadline)
                    assertTrue("Requested orientation was not reached", oriented)
                    scenario.onActivity {
                        assertEquals(offset, it.findViewById<ScrollView>(R.id.offline_help_scroll).scrollY)
                        if (expected == Configuration.ORIENTATION_LANDSCAPE) {
                            assertEquals(View.GONE, it.window.decorView.findViewWithTag<View>("changelog-window-toggle").visibility)
                        }
                    }
                }
                scenario.onActivity {
                    it.onBackPressedDispatcher.onBackPressed()
                    assertTrue(it.isFinishing)
                }
            }
            ActivityScenario.launch<ChangelogActivity>(Intent(context, ChangelogActivity::class.java)).use { scenario ->
                scenario.onActivity {
                    it.findViewById<View>(R.id.changelog_close).performClick()
                    assertTrue(it.isFinishing)
                }
            }
        } finally {
            val editor = prefs.edit().clear()
            before.forEach { (key, value) -> when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            } }
            assertTrue(editor.commit()); assertEquals(before, prefs.all)
        }
    }
}
