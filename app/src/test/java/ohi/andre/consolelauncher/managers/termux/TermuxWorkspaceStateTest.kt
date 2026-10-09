package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test

class TermuxWorkspaceStateTest {
    @Test fun standalonePageRetainsOriginalSplitAndFocus() {
        val state = TermuxWorkspaceState()
        state.select(0, "@1"); state.select(1, "@2")
        state.layout = TermuxWorkspaceState.Layout.TOP_BOTTOM; state.focus(1)
        state.addPage("@3")
        assertEquals(2, state.pageCount)
        assertEquals("@3", state.focusedTarget)
        val restored = TermuxWorkspaceState.restore(state.encode())
        restored.movePage(-1)
        assertEquals(TermuxWorkspaceState.Layout.TOP_BOTTOM, restored.layout)
        assertEquals(listOf("@1", "@2"), restored.targets.toList())
        assertEquals(1, restored.focused)
        restored.movePage(1)
        assertEquals("@3", restored.focusedTarget)
    }

    @Test fun exitingWindowCollapsesSplitAndClosesEmptyPagesWithoutReplacement() {
        val state = TermuxWorkspaceState()
        state.select(0, "@1"); state.select(1, "@2")
        state.layout = TermuxWorkspaceState.Layout.LEFT_RIGHT; state.focus(1)
        state.addPage("@3")
        state.reconcile(setOf("@1", "@2"))
        assertEquals(1, state.pageCount)
        assertEquals("@2", state.focusedTarget)
        state.reconcile(setOf("@1"))
        assertEquals(TermuxWorkspaceState.Layout.SINGLE, state.layout)
        assertEquals("@1", state.focusedTarget)
        state.reconcile(emptySet())
        assertNull(state.focusedTarget)
        assertEquals(1, state.pageCount)
        assertNull(TermuxWorkspaceState.restore(state.encode()).focusedTarget)
    }

    @Test fun explicitPlacementsKeepSingleAndSplitChoicesDistinct() {
        val state = TermuxWorkspaceState()
        state.select(0, "@1")
        assertEquals(listOf(TermuxWorkspaceState.Placement.REPLACE_FOCUSED, TermuxWorkspaceState.Placement.TOP_BOTTOM,
            TermuxWorkspaceState.Placement.LEFT_RIGHT, TermuxWorkspaceState.Placement.NEW_PAGE), state.placements)
        state.place(TermuxWorkspaceState.Placement.TOP_BOTTOM, "@2")
        assertEquals(listOf("@1", "@2"), state.targets.toList())
        assertEquals(TermuxWorkspaceState.Layout.TOP_BOTTOM, state.layout)
        state.place(TermuxWorkspaceState.Placement.REPLACE_FIRST, "@3")
        assertEquals(listOf("@3", "@2"), state.targets.toList())
        assertEquals(1, state.pageCount)
        state.place(TermuxWorkspaceState.Placement.NEW_PAGE, "@4")
        state.movePage(-1)
        assertEquals(listOf("@3", "@2"), state.targets.toList())
    }

    @Test fun invalidPersistedStateIsBoundedToTwoTiles() {
        val state = TermuxWorkspaceState.restore("UNKNOWN|9|@1|invalid")
        assertEquals(TermuxWorkspaceState.Layout.SINGLE, state.layout)
        assertEquals(0, state.focused)
        assertEquals(2, state.targets.size)
        assertNull(state.targets[1])
        try { state.focus(2); fail("A third tile must be rejected") } catch (expected: IllegalArgumentException) { }
    }

    @Test fun splitFocusSingleAndRestorationRetainBothWindows() {
        val state = TermuxWorkspaceState()
        state.select(0, "@1")
        state.select(1, "@2")
        state.layout = TermuxWorkspaceState.Layout.LEFT_RIGHT
        state.focus(1)
        val restored = TermuxWorkspaceState.restore(state.encode())
        assertEquals(state.encode(), restored.encode())
        restored.layout = TermuxWorkspaceState.Layout.SINGLE
        assertEquals("@2", restored.focusedTarget)
        assertEquals(listOf("@1", "@2"), restored.targets.toList())
        assertEquals(listOf(1), restored.visibleTiles)
        restored.layout = TermuxWorkspaceState.Layout.TOP_BOTTOM
        assertEquals(listOf(0, 1), restored.visibleTiles)
    }
}
