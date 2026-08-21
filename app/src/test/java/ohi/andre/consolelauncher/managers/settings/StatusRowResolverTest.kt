package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.options.Ui
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusRowResolverTest {
    @Test fun preservesSharedRowsAndDecimalOrdering() {
        val raw = defaults().toMutableMap().apply {
            put(Ui.ram_index, "4")
            put(Ui.device_index, "4")
            put(Ui.storage_index, "4.1")
            put(Ui.network_index, "4.2")
        }
        val result = StatusRowResolver.normalize(raw)
        assertEquals("4", result.values[Ui.ram_index])
        assertEquals("4", result.values[Ui.device_index])
        assertEquals("4.1", result.values[Ui.storage_index])
        assertEquals("4.2", result.values[Ui.network_index])
        assertFalse(result.changed)
    }

    @Test fun repairsInvalidAndBelowMinimumValuesWithoutMovingOtherRows() {
        val raw = defaults().toMutableMap().apply {
            put(Ui.ram_index, "0.2")
            put(Ui.device_index, "invalid")
            put(Ui.storage_index, "4")
            put(Ui.network_index, "4")
        }
        val result = StatusRowResolver.normalize(raw)
        assertEquals("1.2", result.values[Ui.ram_index])
        assertEquals(Ui.device_index.defaultValue(), result.values[Ui.device_index])
        assertEquals("4", result.values[Ui.storage_index])
        assertEquals("4", result.values[Ui.network_index])
        assertTrue(result.changed)
    }

    @Test fun movesOnlyAsciiToTheNextFreeWholeRow() {
        val raw = defaults().toMutableMap().apply {
            put(Ui.ram_index, "4")
            put(Ui.storage_index, "4")
            put(Ui.ascii_index, "4.2")
        }
        val result = StatusRowResolver.normalize(raw)
        assertEquals("4", result.values[Ui.ram_index])
        assertEquals("4", result.values[Ui.storage_index])
        assertEquals("10.2", result.values[Ui.ascii_index])
        assertTrue(result.changed)
    }

    @Test fun recognizesOnlyStatusIndexSettings() {
        assertTrue(StatusRowResolver.isStatusIndex(Ui.ram_index))
        assertFalse(StatusRowResolver.isStatusIndex(Ui.input_output_size))
    }

    @Test fun keepsTheFirstVisibleItemAsSharedRowStyleOwner() {
        val row = StatusRowResolver.groupVisible(
            listOf(1f to "ram", 1f to "device", 1.1f to "time")
        ).single()
        assertEquals(listOf("ram", "device", "time"), row)

        val withoutRam = StatusRowResolver.groupVisible(
            listOf(1f to "device", 1.1f to "time")
        ).single()
        assertEquals("device", withoutRam.first())
    }

    private fun defaults() = StatusRowResolver.settings.associateWith { it.defaultValue() }
}
