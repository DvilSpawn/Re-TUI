package ohi.andre.consolelauncher.sidebar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeSidebarRebuildTest {
    @Test
    fun resumingWithNothingChangedRebuildsNothing() {
        assertFalse(edgeSidebarNeedsRebuild(sidebarOn = true, tabPresent = true, builtRevision = 7, revision = 7))
        assertFalse(edgeSidebarNeedsRebuild(sidebarOn = false, tabPresent = false, builtRevision = 7, revision = 7))
    }

    @Test
    fun settingsChangeRebuilds() {
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = true, tabPresent = true, builtRevision = 7, revision = 8))
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = false, tabPresent = false, builtRevision = 7, revision = 8))
    }

    @Test
    fun missingTabRebuildsEvenAtSameRevision() {
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = true, tabPresent = false, builtRevision = 7, revision = 7))
    }

    @Test
    fun tabLeftBehindAfterTurningOffIsRemoved() {
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = false, tabPresent = true, builtRevision = 7, revision = 7))
    }

    @Test
    fun firstBuildAlwaysRuns() {
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = true, tabPresent = false, builtRevision = null, revision = 0))
        assertTrue(edgeSidebarNeedsRebuild(sidebarOn = false, tabPresent = false, builtRevision = null, revision = 0))
    }
}
