package ohi.andre.consolelauncher.managers.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupMenuManagerTest {
    @Test
    fun borderChoicesMapToTheExpectedStroke() {
        assertEquals(true to 4, StartupMenuManager.borderValues(StartupMenuManager.BORDER_DASHED))
        assertEquals(true to 0, StartupMenuManager.borderValues(StartupMenuManager.BORDER_SOLID))
        assertEquals(false to 4, StartupMenuManager.borderValues(StartupMenuManager.BORDER_NONE))
    }

    @Test
    fun basicUsesOnlyTheSimpleFeatureDefaults() {
        assertTrue(StartupMenuManager.selectedFeature(StartupMenuManager.BASIC, StartupMenuManager.STATUS, false))
        assertTrue(StartupMenuManager.selectedFeature(StartupMenuManager.BASIC, StartupMenuManager.LOCK, false))
        assertFalse(StartupMenuManager.selectedFeature(StartupMenuManager.BASIC, StartupMenuManager.APP_DRAWER, true))
        assertTrue(StartupMenuManager.selectedFeature(StartupMenuManager.ADVANCED, StartupMenuManager.APP_DRAWER, true))
    }

    @Test
    fun advancedVisualEffectsRemainIndependent() {
        assertEquals(true to true, StartupMenuManager.effectValues(StartupMenuManager.ADVANCED, true, true))
        assertEquals(false to true, StartupMenuManager.effectValues(StartupMenuManager.ADVANCED, false, true))
        assertEquals(false to false, StartupMenuManager.effectValues(StartupMenuManager.BASIC, true, true))
    }
}
