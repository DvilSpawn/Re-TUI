package ohi.andre.consolelauncher.sidebar

import ohi.andre.consolelauncher.sidebar.EdgePills.Kind.PODCAST
import ohi.andre.consolelauncher.sidebar.EdgePills.Kind.STOPWATCH
import ohi.andre.consolelauncher.sidebar.EdgePills.Kind.TIMER
import ohi.andre.consolelauncher.sidebar.EdgeSegment.Body
import ohi.andre.consolelauncher.sidebar.EdgeSegment.Sidebar
import ohi.andre.consolelauncher.sidebar.EdgeSegment.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeStackTest {
    @Test fun sidebarInFocusLeavesRunningItemsAsTabs() =
        assertEquals(listOf(Sidebar, Tab(TIMER), Tab(PODCAST)), edgeStackSegments(true, listOf(TIMER, PODCAST), null))

    @Test fun anItemInFocusPutsTheHomeTabOnTop() =
        assertEquals(listOf(Tab(null), Body(STOPWATCH), Tab(TIMER), Tab(PODCAST)), edgeStackSegments(true, listOf(TIMER, STOPWATCH, PODCAST), STOPWATCH))

    @Test fun withTheSidebarOffThereIsNoHomeTab() =
        assertEquals(listOf(Body(TIMER), Tab(STOPWATCH)), edgeStackSegments(false, listOf(TIMER, STOPWATCH), TIMER))

    @Test fun nothingRunningIsJustTheSidebar() = assertEquals(listOf(Sidebar), edgeStackSegments(true, emptyList(), null))

    @Test fun focusStaysOnARunningItem() = assertEquals(TIMER, edgeStackFocus(true, listOf(TIMER), TIMER))

    @Test fun focusReturnsToTheSidebarWhenItsItemEnds() = assertNull(edgeStackFocus(true, listOf(PODCAST), TIMER))

    @Test fun withoutASidebarFocusFallsToTheFirstItem() = assertEquals(PODCAST, edgeStackFocus(false, listOf(PODCAST), TIMER))
}
