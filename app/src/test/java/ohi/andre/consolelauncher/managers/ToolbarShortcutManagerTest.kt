package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarShortcutManagerTest {
    @Test
    fun iconPickerChoicesHaveUniqueKeysAndLabels() {
        val icons = ToolbarShortcutManager.icons()
        assertEquals(12, icons.size)
        assertEquals(icons.size, icons.map { it.key }.distinct().size)
        assertEquals(icons.size, icons.map { it.label }.distinct().size)
        assertEquals("star", ToolbarShortcutManager.normalizeIcon("not-supported"))
    }
}
