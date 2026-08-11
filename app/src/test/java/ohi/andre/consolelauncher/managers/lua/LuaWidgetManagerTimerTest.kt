package ohi.andre.consolelauncher.managers.lua

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaWidgetManagerTimerTest {
    @Test fun countdownBarUsesRemainingTime() {
        val method = LuaWidgetManager::class.java.getDeclaredMethod("systemTimerScript").apply { isAccessible = true }
        val script = method.invoke(LuaWidgetManager) as String
        assertTrue(script.contains("fmt.progress_bar(remaining, total, BAR_WIDTH)"))
        assertTrue(script.contains("pct(remaining, total)"))
        assertFalse(script.contains("fmt.progress_bar(elapsed, total, BAR_WIDTH)"))
        assertTrue(script.contains("Stopwatch: \" .. duration(elapsed)"))
        assertTrue(script.contains("countdown_line(label, state)"))
    }
}
