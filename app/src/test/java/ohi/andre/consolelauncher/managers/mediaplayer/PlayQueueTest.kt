package ohi.andre.consolelauncher.managers.mediaplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayQueueTest {
    private fun queue(vararg items: String, at: Int = 0) = PlayQueue<String>().apply { replace(items.toList(), at) }

    @Test fun nextWrapsWhenRepeatingAll() {
        val q = queue("a", "b", at = 1)
        assertTrue(q.next())
        assertEquals("a", q.current)
    }

    @Test fun nextStopsAtTheEndWithoutRepeat() {
        val q = queue("a", "b", at = 1).apply { repeat = PlayQueue.Repeat.OFF }
        assertFalse(q.next())
        assertEquals("b", q.current)
    }

    @Test fun previousWrapsWhenRepeatingAll() {
        val q = queue("a", "b")
        assertTrue(q.previous())
        assertEquals("b", q.current)
    }

    @Test fun addNextGoesRightAfterTheCurrentItem() {
        val q = queue("a", "b").apply { addNext("x") }
        assertEquals(listOf("a", "x", "b"), q.snapshot())
        assertEquals("a", q.current)
    }

    @Test fun removingBeforeTheCurrentItemKeepsItCurrent() {
        val q = queue("a", "b", "c", at = 2).apply { removeAt(0) }
        assertEquals("c", q.current)
    }

    @Test fun removingTheCurrentItemMovesToTheNextOne() {
        val q = queue("a", "b", "c", at = 1).apply { removeAt(1) }
        assertEquals("c", q.current)
    }

    @Test fun removingTheLastItemStaysInRange() {
        val q = queue("a", "b", at = 1).apply { removeAt(1) }
        assertEquals("a", q.current)
        q.removeAt(0)
        assertEquals(null, q.current)
    }

    @Test fun movingKeepsTheSameItemCurrent() {
        val q = queue("a", "b", "c", at = 1).apply { move(1, 2) }
        assertEquals(listOf("a", "c", "b"), q.snapshot())
        assertEquals("b", q.current)
        q.move(0, 2)
        assertEquals("b", q.current)
    }

    @Test fun jumpToOutOfRangeIsRefused() {
        val q = queue("a")
        assertFalse(q.jumpTo(3))
        assertEquals("a", q.current)
    }

    @Test fun shuffleKeepsTheCurrentItemFirstAndEveryItem() {
        val q = queue("a", "b", "c", "d", at = 2).apply { shuffle(Random(4)) }
        assertEquals("c", q.current)
        assertEquals(0, q.index)
        assertEquals(setOf("a", "b", "c", "d"), q.snapshot().toSet())
        assertTrue(q.shuffled)
    }

    @Test fun unshuffleRestoresOrderAndKeepsLaterAdditions() {
        val q = queue("a", "b", "c", at = 1).apply { shuffle(Random(9)); addLast("z"); unshuffle() }
        assertEquals(listOf("a", "b", "c", "z"), q.snapshot())
        assertEquals("b", q.current)
        assertFalse(q.shuffled)
    }

    @Test fun anEmptyQueueIsSafe() {
        val q = PlayQueue<String>()
        assertFalse(q.next())
        assertFalse(q.previous())
        q.shuffle()
        q.removeAt(0)
        assertEquals(null, q.current)
    }

    @Test fun peekNextMatchesWhereNextWouldGo() {
        val queue = PlayQueue<String>()
        queue.replace(listOf("a", "b"))
        org.junit.Assert.assertEquals("b", queue.peekNext())
        queue.jumpTo(1)
        queue.repeat = PlayQueue.Repeat.ALL
        org.junit.Assert.assertEquals("a", queue.peekNext())
        queue.repeat = PlayQueue.Repeat.OFF
        org.junit.Assert.assertNull(queue.peekNext())
        queue.repeat = PlayQueue.Repeat.ONE
        org.junit.Assert.assertNull(queue.peekNext())
    }
}
