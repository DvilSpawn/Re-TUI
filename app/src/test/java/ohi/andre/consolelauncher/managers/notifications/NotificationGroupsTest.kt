package ohi.andre.consolelauncher.managers.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupsTest {
    private data class N(val name: String, val group: String? = null, val summary: Boolean = false)

    private fun entries(vararg items: N) = NotificationGroups.entries(items.toList(), { it.group }, { it.summary })

    // WhatsApp posts a summary plus one notification per chat, all in one group.
    private val summary = N("8 messages from 2 chats", "wa", summary = true)
    private val family = N("Family", "wa")
    private val pay = N("Amazon Pay", "wa")
    private val usb = N("USB debugging")

    @Test fun aGroupBecomesOneRowHeadedByItsSummary() {
        val rows = entries(summary, family, usb, pay)

        assertEquals(listOf(summary, usb), rows.map { it.head })
        assertTrue(rows[0].isGroup)
        assertEquals(listOf(family, pay), rows[0].children)
        assertFalse(rows[1].isGroup)
    }

    @Test fun aGroupWithoutItsSummaryIsHeadedByItsNewestNotification() {
        val rows = entries(family, pay)

        assertEquals(family, rows.single().head)
        assertEquals(listOf(family, pay), rows.single().children)
    }

    @Test fun aGroupOfOneShowsThatNotificationAlone() {
        assertEquals(listOf(family), entries(summary, family).map { it.head })
        assertTrue(entries(summary, family).none { it.isGroup })
        assertEquals(listOf(summary), entries(summary).map { it.head })
    }

    @Test fun openingAGroupListsItsNotificationsUntilFewerThanTwoRemain() {
        val items = listOf(summary, family, usb, pay)
        assertEquals(listOf(family, pay), NotificationGroups.children(items, "wa", { it.group }, { it.summary }))
        assertTrue(NotificationGroups.children(listOf(summary, family), "wa", { it.group }, { it.summary }).isEmpty())
    }

    @Test fun pagingStepsThroughEveryNotificationButNotSummaries() {
        assertEquals(listOf(family, pay, usb), NotificationGroups.pageable(entries(summary, family, usb, pay)))
    }
}
