package ohi.andre.consolelauncher.managers

import ohi.andre.consolelauncher.managers.xml.options.Behavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSoundManagerTest {
    @Test
    fun soundPackMappingStaysCompleteAndOptIn() {
        val resources = LauncherSoundManager.Event.entries.map { it.resource }

        assertEquals(12, resources.size)
        assertEquals(resources.size, resources.distinct().size)
        assertFalse(Behavior.launcher_sounds.defaultValue().toBoolean())
        assertEquals(7, LauncherSoundManager.Event.entries.count { it.setting != null })
        assertTrue(LauncherSoundManager.Event.entries.filter { it.setting != null }
            .all { it.setting!!.defaultValue().toBoolean() })
    }
}
