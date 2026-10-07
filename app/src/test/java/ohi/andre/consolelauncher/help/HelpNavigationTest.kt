package ohi.andre.consolelauncher.help

import org.junit.Assert.*
import org.junit.Test

class HelpNavigationTest {
    @Test fun catalogHasStableRootsAndUniqueArticleIds() {
        val topics = OfflineHelpCatalog.topics
        val roots = topics.filter { it.category == null }.map { it.id }
        assertEquals("getting-started", roots.first())
        assertTrue(roots.contains("reference"))
        assertEquals("terminal-basics", topics.first { it.category == "getting-started" }.id)
        assertEquals(topics.size, topics.map { it.id }.toSet().size)
        topics.filter { it.category != null }.forEach { assertTrue(it.category in roots); assertSame(it, OfflineHelpCatalog.find(it.id)) }
        assertNull(OfflineHelpCatalog.find("unknown"))
    }
    @Test fun pagesReserveNavigationAndNeverWrap() {
        val nav = HelpNavigation(List(23) { "topic-$it" })
        assertEquals((0..6).map { "topic-$it" }, nav.visible())
        assertFalse(nav.previous)
        assertTrue(nav.next)
        nav.move(-1)
        assertEquals(0, nav.page)
        repeat(3) { nav.move(1) }
        assertEquals(listOf("topic-21", "topic-22"), nav.visible())
        assertFalse(nav.next)
        nav.move(1)
        assertEquals(3, nav.page)
    }
}
