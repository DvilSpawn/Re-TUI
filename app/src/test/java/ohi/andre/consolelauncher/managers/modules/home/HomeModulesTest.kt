package ohi.andre.consolelauncher.managers.modules.home

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class HomeModulesTest {
    private class Stub(override val id: String) : HomeModule {
        override fun show(host: HomeModuleHost) = Unit
    }

    @Test fun findsAModuleById() {
        val timer = Stub("timer")
        val modules = HomeModules(listOf(timer, Stub("rss")))
        assertSame(timer, modules["timer"])
        assertNull(modules["missing"])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateIds() {
        HomeModules(listOf(Stub("timer"), Stub("timer")))
    }

    @Test fun uiManagerDoesNotOpenMigratedModulesByName() {
        val source = SourceFiles.kotlin("UIManager.kt").readText()
        val show = source.substring(source.indexOf("private fun showHomeModule"), source.indexOf("private fun refreshLauncherModuleTextIfNeeded"))
        listOf("TIMER", "CALENDAR", "REMINDER", "NOTES", "RSS", "WEATHER_NATIVE", "NOTIFICATIONS").forEach {
            assertFalse("$it is opened by name", show.contains("ModuleManager.$it"))
        }
        assertEquals(1, Regex("""builtIn\.show\(moduleHost\)""").findAll(show).count())
        assertFalse("Lua module engines belong in LuaModules", source.contains("luaWidgetEngines"))
    }
}
