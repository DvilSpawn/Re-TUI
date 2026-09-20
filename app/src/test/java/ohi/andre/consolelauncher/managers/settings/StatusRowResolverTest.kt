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

    @Test fun insertingAsciiFirstShiftsWholeGroupsAndSurvivesReload() {
        val raw = defaults().toMutableMap().apply {
            put(Ui.time_index, "1")
            put(Ui.notes_index, "1.1")
            put(Ui.battery_index, "1.2")
        }
        val result = StatusRowResolver.reorder(raw, mapOf(Ui.ascii_index to "1"))
        assertEquals("1", result.values[Ui.ascii_index])
        assertEquals("2", result.values[Ui.time_index])
        assertEquals("2.1", result.values[Ui.notes_index])
        assertEquals("2.2", result.values[Ui.battery_index])
        assertEquals("5", result.values[Ui.storage_index])
        assertTrue(result.changed)
        assertEquals(result.values, StatusRowResolver.normalize(result.values).values)
        assertFalse(StatusRowResolver.reorder(result.values, mapOf(Ui.ascii_index to "1.0")).changed)
    }

    @Test fun insertingAnyWholeRowKeepsEarlierRowsAndDecimalParts() {
        val raw = defaults().toMutableMap().apply { put(Ui.notes_index, "4.125") }
        val result = StatusRowResolver.reorder(raw, mapOf(Ui.device_index to "4"))
        assertEquals("3", result.values[Ui.time_index])
        assertEquals("4", result.values[Ui.device_index])
        assertEquals("5", result.values[Ui.storage_index])
        assertEquals("5.125", result.values[Ui.notes_index])
        assertEquals("11", result.values[Ui.ascii_index])
    }

    @Test fun decimalsStillJoinRowsAndUnchangedVisibleInputsDoNotUndoShifts() {
        val raw = defaults()
        val grouped = StatusRowResolver.reorder(raw, mapOf(Ui.notes_index to "3.1"))
        assertEquals("3", grouped.values[Ui.time_index])
        assertEquals("3.1", grouped.values[Ui.notes_index])
        assertEquals("4", grouped.values[Ui.storage_index])
        val saved = StatusRowResolver.reorder(raw, linkedMapOf(
            Ui.ascii_index to "1", Ui.time_index to raw[Ui.time_index], Ui.notes_index to raw[Ui.notes_index]
        ))
        assertEquals("4", saved.values[Ui.time_index])
        assertEquals("7", saved.values[Ui.notes_index])
        assertFalse(StatusRowResolver.reorder(raw, raw).changed)
        assertFalse(StatusRowResolver.reorder(raw, mapOf(Ui.ascii_index to "invalid")).changed)
    }
}
