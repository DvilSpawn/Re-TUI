package ohi.andre.consolelauncher.help

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Isolates the reading bookmark and restores it without touching launcher settings. */
class HelpReaderPreferencesRule : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = instrumentation.targetContext.getSharedPreferences("offline_help_reader", Context.MODE_PRIVATE)
    private var saved: Map<String, *> = emptyMap<String, Any>()
    override fun before() {
        saved = preferences.all.toMap()
        preferences.edit().clear().commit()
    }
    override fun after() {
        instrumentation.waitForIdleSync()
        val editor = preferences.edit().clear()
        saved.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
        } }
        editor.commit()
    }
}
