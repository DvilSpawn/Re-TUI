package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.*
import org.junit.Test

class TerminalHomeLayoutTest {
    @Test fun addsWithoutOverlapAndRejectsCollisions() {
        val grid = TerminalHomeLayout()
        val a = grid.add("a")!!
        val b = grid.add("b")!!
        assertEquals(0, a.x)
        assertEquals(3, b.x)
        assertFalse(grid.update("b", 0, 0, 3, 4))
        assertEquals(3, b.x)
        assertTrue(grid.update("a", 0, 4, 6, 4))
        assertEquals(4, a.y)
    }
    @Test fun resizeAndMoveStayInsideGrid() {
        val grid = TerminalHomeLayout()
        grid.add("a")
        assertFalse(grid.update("a", -1, 0, 3, 4))
        assertFalse(grid.update("a", 4, 0, 3, 4))
        assertTrue(grid.update("a", 0, 23, 3, 4))
        assertFalse(grid.update("a", 0, 0, 1, 4))
        assertTrue(grid.update("a", 0, 0, 6, 12))
    }
    @Test fun addingMoreTilesExtendsTheVerticalCanvas() {
        val grid = TerminalHomeLayout()
        repeat(100) { assertNotNull(grid.add("$it")) }
        assertNotNull(grid.add("more"))
        assertEquals(101, grid.tiles.size)
        assertTrue(grid.tiles.last().y > 24)
        assertFalse(grid.update("more", 0, Int.MAX_VALUE, 3, 4))
    }
    @Test fun bottomClockCanMoveAndResizeIntoTopGapInOneUpdate() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("weather", 3, 0, 3, 2)
        grid.tiles += TerminalHomeLayout.Tile("shell", 0, 2, 6, 12)
        grid.tiles += TerminalHomeLayout.Tile("clock", 3, 14, 3, 4)
        assertFalse(grid.update("clock", 0, 0, 3, 4))
        assertTrue(grid.update("clock", 0, 0, 3, 2))
        assertEquals(TerminalHomeLayout.Tile("clock", 0, 0, 3, 2), grid.tiles.last())
    }
    @Test fun draggingKeepsSizesAndPushesObstructionsDown() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("clock",3,14,3,4)
        grid.tiles += TerminalHomeLayout.Tile("weather",0,0,3,2)
        grid.tiles += TerminalHomeLayout.Tile("shell",0,2,6,8)
        val plan = grid.reflow(grid.tiles[0].copy(x=0,y=0))!!
        assertEquals(14,grid.tiles[0].y) // Preview must not mutate saved coordinates.
        grid.applyPlacement(plan)
        assertEquals(TerminalHomeLayout.Tile("clock",0,0,3,4),grid.tiles[0])
        assertEquals(TerminalHomeLayout.Tile("weather",0,4,3,2),grid.tiles[1])
        assertEquals(TerminalHomeLayout.Tile("shell",0,6,6,8),grid.tiles[2])
        assertTrue(grid.tiles.all { grid.fits(it) })
    }
    @Test fun expandingPushesOnlyAffectedColumnAndCanReturnToOriginalLayout() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("a",0,0,3,2)
        grid.tiles += TerminalHomeLayout.Tile("b",0,2,3,4)
        grid.tiles += TerminalHomeLayout.Tile("c",0,6,3,4)
        grid.tiles += TerminalHomeLayout.Tile("side",3,0,3,8)
        val original = grid.tiles.map { it.copy() }
        grid.applyPlacement(grid.reflow(original[0].copy(height=6),original)!!)
        assertEquals(listOf(0,6,10,0),grid.tiles.map { it.y })
        grid.applyPlacement(grid.reflow(original[0],original)!!)
        assertEquals(original,grid.tiles)
        assertNull(grid.reflow(original[0].copy(x=5)))
        assertEquals(original,grid.tiles)
    }
    @Test fun fullCanvasGrowsAndOverflowDoesNotChangeLayout() {
        val grid = TerminalHomeLayout()
        repeat(100) { grid.tiles += TerminalHomeLayout.Tile("$it",0,it*2,6,2) }
        val original = grid.tiles.map { it.copy() }
        grid.applyPlacement(grid.reflow(grid.tiles.last().copy(y=0))!!)
        assertTrue(grid.tiles.all { grid.fits(it) })
        assertEquals(2,grid.tiles.first().height)
        assertEquals(2,grid.tiles.first().y)
        assertNull(grid.reflow(original[0].copy(y=Int.MAX_VALUE)))
    }
    @Test fun movingAwayFromOverlapRestoresPreviewWithoutRatchet() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("dragged",0,8,3,2)
        grid.tiles += TerminalHomeLayout.Tile("destination",0,0,3,4)
        grid.tiles += TerminalHomeLayout.Tile("below",0,4,3,4)
        val original = grid.tiles.map { it.copy() }
        val overlap = grid.reflow(original[0].copy(y=0),original)!!
        assertEquals(2,overlap[1].y)
        assertEquals(6,overlap[2].y)
        val away = grid.reflow(original[0].copy(x=3,y=0),original)!!
        assertEquals(original[1],away[1])
        assertEquals(original[2],away[2])
        assertEquals(overlap,grid.reflow(original[0].copy(y=0),original))
        assertEquals(original,grid.tiles)
    }
    @Test fun dropSettlesIntoGapsInEachColumnAndClosesEmptyRowsBelow() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("left",0,0,3,2)
        grid.tiles += TerminalHomeLayout.Tile("right",3,4,3,2)
        grid.tiles += TerminalHomeLayout.Tile("leftBelow",0,6,3,2)
        grid.tiles += TerminalHomeLayout.Tile("wideBelow",0,10,6,4)
        val original = grid.tiles.map { it.copy() }
        val compacted = grid.compact()
        assertEquals(listOf(0,0,2,4),compacted.map { it.y })
        assertEquals(original,grid.tiles)
        grid.applyPlacement(compacted)
        assertTrue(grid.tiles.all { grid.fits(it) })
        assertEquals(original.map { Triple(it.x,it.width,it.height) },grid.tiles.map { Triple(it.x,it.width,it.height) })
        assertEquals(compacted,grid.compact())
    }
    @Test fun droppedPaneMovesUpAndVacatedSpaceClosesWithoutChangingTmuxIdentity() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("a",0,0,6,2)
        grid.tiles += TerminalHomeLayout.Tile("b",0,2,6,4)
        grid.tiles += TerminalHomeLayout.Tile("c",0,6,6,2)
        val plan = grid.reflow(grid.tiles[0].copy(y=15))!!
        val settled = grid.compact(plan)
        assertEquals(listOf("a","b","c"),settled.map { it.id })
        assertEquals(listOf(6,0,4),settled.map { it.y })
        grid.applyPlacement(settled)
        assertTrue(grid.tiles.all { grid.fits(it) })
    }
    @Test fun compactionRespectsFullWidthBarrierAndDoesNotJumpOverOtherPanes() {
        val grid = TerminalHomeLayout()
        grid.tiles += TerminalHomeLayout.Tile("tall",0,0,3,8)
        grid.tiles += TerminalHomeLayout.Tile("barrier",0,8,6,2)
        grid.tiles += TerminalHomeLayout.Tile("below",3,20,3,2)
        val settled = grid.compact()
        assertEquals(listOf(0,8,10),settled.map { it.y })
        grid.applyPlacement(settled)
        assertTrue(grid.tiles.all { grid.fits(it) })
        assertTrue(grid.compact(emptyList()).isEmpty())
    }
}
