package com.dvil.retui.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiVisualContractTest {
    @Test
    fun keysAreUniqueAndCoverCoreSurfaces() {
        val keys = RetuiVisualContract.KEYS
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(keys.contains(RetuiVisualContract.BG))
        assertTrue(keys.contains(RetuiVisualContract.TEXT))
        assertTrue(keys.contains(RetuiVisualContract.BORDER))
        assertTrue(keys.contains(RetuiVisualContract.OUTPUT_BG))
        assertTrue(keys.contains(RetuiVisualContract.SELECTION_BG))
        assertTrue(keys.contains(RetuiVisualContract.FONT_PATH))
    }
}
