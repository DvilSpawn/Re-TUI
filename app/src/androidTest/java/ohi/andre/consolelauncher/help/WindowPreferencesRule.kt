package ohi.andre.consolelauncher.help

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/** Isolates only window geometry; leaves conversation, calculator, and launcher data untouched. */
class WindowPreferencesRule(private val fresh: Boolean = true) : ExternalResource() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences = instrumentation.targetContext.getSharedPreferences("terminal_windows", Context.MODE_PRIVATE)
    private var saved: Map<String, *> = emptyMap<String, Any>()
    override fun before() {
        saved = preferences.all.toMap()
        if (fresh) preferences.edit().clear().commit()
    }
    override fun after() {
        instrumentation.waitForIdleSync()
        val editor = preferences.edit().clear()
        saved.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        check(editor.commit()) { "Window preferences could not be restored" }
        check(saved == preferences.all) { "Window preference values changed during the fixture" }
    }
}
