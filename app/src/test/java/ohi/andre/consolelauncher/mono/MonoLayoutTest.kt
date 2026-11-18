package ohi.andre.consolelauncher.mono

import ohi.andre.consolelauncher.mono.MonoLayout.Item
import ohi.andre.consolelauncher.mono.MonoLayout.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class MonoLayoutTest {
    private fun page(vararg kinds: Kind) = kinds.map { Item(it) }

    @Test
    fun emptyOrUnknownFallsBackToTheDefault() {
        assertEquals(MonoLayout.DEFAULT, MonoLayout.parse(""))
        assertEquals(MonoLayout.DEFAULT, MonoLayout.parse("nope, nothing"))
        assertEquals(MonoLayout.DEFAULT, MonoLayout.parse("|"))
    }

    @Test
    fun parseSkipsUnknownNamesAndRepeatsAcrossPages() {
        assertEquals(listOf(page(Kind.APPS, Kind.TASKS), page(Kind.READ)), MonoLayout.parse(" Apps ,bogus,tasks|apps,read"))
    }

    @Test
    fun aLayoutWithoutPagesIsOnePage() {
        assertEquals(1, MonoLayout.parse("scene,clock,apps,media,quick,tasks").size)
    }

    @Test
    fun emptyPagesAreKept() {
        assertEquals(listOf(page(Kind.APPS), emptyList()), MonoLayout.parse("apps|"))
    }

    @Test
    fun terminalsKeepTheirSizeAndWindowAndMayRepeat() {
        val pages = MonoLayout.parse("term:4x4@3,term:2x2,term,term:3x3@1")
        assertEquals(listOf(Item(Kind.TERM, 4, 4, "@3"), Item(Kind.TERM, 2, 2), Item(Kind.TERM, 4, 4), Item(Kind.TERM, 4, 4, "@1")), pages[0])
    }

    private fun spots(items: List<Item>) = MonoLayout.place(items).map { Triple(it.item.kind, it.column, it.row) }

    @Test
    fun theDefaultLaysOutAsBefore() {
        assertEquals(listOf(Triple(Kind.SCENE, 0, 0), Triple(Kind.CLOCK, 2, 0), Triple(Kind.APPS, 0, 2), Triple(Kind.MEDIA, 0, 4),
            Triple(Kind.QUICK, 0, 6), Triple(Kind.TASKS, 2, 6)), spots(MonoLayout.DEFAULT[0]))
    }

    @Test
    fun smallPanelsStackBesideATallOne() {
        val items = listOf(Item(Kind.TERM, 4, 2), Item(Kind.READ, 2, 4), Item(Kind.TERM, 2, 2), Item(Kind.TASKS))
        assertEquals(listOf(Triple(Kind.TERM, 0, 0), Triple(Kind.READ, 0, 2), Triple(Kind.TERM, 2, 2), Triple(Kind.TASKS, 2, 4)), spots(items))
        assertEquals(6, MonoLayout.height(items))
    }

    @Test
    fun aLaterSmallPanelFillsAGapLeftAbove() {
        assertEquals(listOf(Triple(Kind.QUICK, 0, 0), Triple(Kind.APPS, 0, 2), Triple(Kind.TASKS, 2, 0)), spots(page(Kind.QUICK, Kind.APPS, Kind.TASKS)))
    }

    @Test
    fun panelsKeepSizesTheyTakeAndDropOthers() {
        assertEquals(listOf(Item(Kind.READ, 2, 4), Item(Kind.SCENE), Item(Kind.APPS, 4, 4)), MonoLayout.parse("read:2x4,scene:4x4,apps:4x4")[0])
        assertEquals("read:2x4,scene,tasks", MonoLayout.write(listOf(listOf(Item(Kind.READ, 2, 4), Item(Kind.SCENE), Item(Kind.TASKS)))))
    }

    @Test
    fun swappingKeepsEachPanelsSize() {
        val pages = listOf(listOf(Item(Kind.READ, 2, 4), Item(Kind.TASKS)), listOf(Item(Kind.APPS, 4, 4)))
        assertEquals(listOf(listOf(Item(Kind.APPS, 4, 4), Item(Kind.TASKS)), listOf(Item(Kind.READ, 2, 4))), MonoLayout.swap(pages, 0, 0, Kind.APPS))
    }

    @Test
    fun movingStaysOnThePageAndStopsAtItsEnds() {
        val pages = listOf(page(Kind.SCENE, Kind.CLOCK, Kind.APPS), page(Kind.READ))
        assertEquals(listOf(page(Kind.CLOCK, Kind.SCENE, Kind.APPS), page(Kind.READ)), MonoLayout.move(pages, 0, 0, 1))
        assertEquals(pages, MonoLayout.move(pages, 1, 0, 1))
        assertEquals(false, MonoLayout.canMove(pages, 0, 0, -1))
    }

    @Test
    fun movingGoesPastPanelsThatWouldOnlyRefillAGap() {
        // A wide terminal under a tall read with small panels beside it: one step up would let the small terminal
        // drop back into the gap and leave the wide one where it was.
        val items = listOf(Item(Kind.TERM, 4, 2), Item(Kind.READ, 2, 4), Item(Kind.NEXT), Item(Kind.TERM, 2, 2), Item(Kind.TERM, 4, 2))
        val moved = MonoLayout.move(listOf(items), 0, 4, -1)[0]
        assertEquals(listOf(Item(Kind.TERM, 4, 2), Item(Kind.TERM, 4, 2), Item(Kind.READ, 2, 4), Item(Kind.NEXT), Item(Kind.TERM, 2, 2)), moved)
        assertEquals(Triple(Kind.TERM, 0, 2), spots(moved)[1])
        assertEquals(false, MonoLayout.canMove(listOf(items), 0, 4, 1))
    }

    @Test
    fun theDefaultFillsOnePage() {
        assertEquals(MonoLayout.PAGE_ROWS, MonoLayout.height(MonoLayout.DEFAULT[0]))
        assertEquals(6, MonoLayout.height(listOf(Item(Kind.TERM), Item(Kind.READ), Item(Kind.TERM, 2, 2))))
    }

    @Test
    fun swappingAShownPanelTradesPlacesAcrossPages() {
        val pages = listOf(page(Kind.SCENE, Kind.APPS), page(Kind.TASKS))
        assertEquals(listOf(page(Kind.TASKS, Kind.APPS), page(Kind.SCENE)), MonoLayout.swap(pages, 0, 0, Kind.TASKS))
    }

    @Test
    fun swappingInANewPanelReplacesTheSlot() {
        assertEquals(listOf(page(Kind.NEXT, Kind.APPS)), MonoLayout.swap(listOf(page(Kind.SCENE, Kind.APPS)), 0, 0, Kind.NEXT))
    }

    @Test
    fun theAddSlotOffersMissingPanelsAndAlwaysATerminal() {
        val missing = MonoLayout.missing(listOf(page(Kind.SCENE, Kind.CLOCK, Kind.APPS, Kind.MEDIA), listOf(Item(Kind.TERM), Item(Kind.READ))))
        assertEquals(listOf(Kind.QUICK, Kind.TASKS, Kind.NEXT, Kind.ALERTS, Kind.TERM), missing)
    }

    @Test
    fun pagesAreAddedAfterAndRemovedWithTheirPanels() {
        val one = listOf(page(Kind.APPS))
        val two = MonoLayout.addPage(one, 0)
        assertEquals(listOf(page(Kind.APPS), emptyList()), two)
        assertEquals(listOf(emptyList<Item>()), MonoLayout.removePage(two, 0))
        assertEquals(one, MonoLayout.removePage(one, 0))
    }

    @Test
    fun layoutRoundTrips() {
        assertEquals("scene,clock,apps,media,quick,tasks", MonoLayout.write(MonoLayout.DEFAULT))
        val raw = "scene,clock,apps,media,quick,tasks|term:4x4@2,read,term:2x2"
        assertEquals(raw, MonoLayout.write(MonoLayout.parse(raw)))
    }
}
