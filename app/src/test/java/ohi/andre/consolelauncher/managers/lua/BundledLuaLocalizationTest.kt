package ohi.andre.consolelauncher.managers.lua

import org.junit.Assert.assertTrue
import org.junit.Test
import org.luaj.vm2.compiler.LuaC

class BundledLuaLocalizationTest {
    @Test fun translatedLabelsPreserveExecutableLua() {
        val type = LuaWidgetManager::class.java
        val timer = type.getDeclaredMethod("systemTimerScript").apply { isAccessible = true }
            .invoke(LuaWidgetManager) as String
        val samples = type.getDeclaredMethod("samples").apply { isAccessible = true }
            .invoke(LuaWidgetManager) as Map<*, *>
        val scripts = samples.values.filterNotNull().map { sample ->
            sample.javaClass.getDeclaredField("script").apply { isAccessible = true }.get(sample) as String
        } + timer
        scripts.forEachIndexed { index, script ->
            LuaC.instance.compile(script.byteInputStream(), "builtin-$index")
        }
        assertTrue(timer.contains("strings.localize("))
        assertTrue(timer.contains("timer -stop"))
        assertTrue(timer.contains("state.type == \"finished\""))
    }
}
