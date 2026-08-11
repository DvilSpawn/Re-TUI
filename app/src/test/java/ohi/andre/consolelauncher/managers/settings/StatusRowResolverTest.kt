package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StatusRowResolverTest {
    @Test fun preservesDecimalsAndCascadesWholeRowConflicts() {
        val raw = defaults().toMutableMap().apply {
            put(Ui.ram_index, "0.2")
            put(Ui.device_index, "0.3")
        }
        val values = StatusRowResolver.normalize(raw).values
        assertEquals("1.2", values[Ui.ram_index])
        assertEquals("2.3", values[Ui.device_index])
        assertEquals(10, values.values.map { it.substringBefore('.').toInt() }.toSet().size)
    }

    @Test fun editedItemKeepsRequestedRowAndExistingItemCascades() {
        val raw = defaults().toMutableMap().apply { put(Ui.ascii_index, "1.7") }
        val values = StatusRowResolver.normalize(raw, Ui.ascii_index).values
        assertEquals("1.7", values[Ui.ascii_index])
        assertNotEquals("1", values[Ui.ram_index])
    }

    @Test fun detectsOccupiedRowsIncludingHiddenItems() {
        assertEquals(Ui.ram_index, StatusRowResolver.occupiedRow(defaults(), Ui.ascii_index, "1.9"))
    }

    private fun defaults() = StatusRowResolver.settings.associateWith { it.defaultValue() }
}
