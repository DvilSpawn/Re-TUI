package ohi.andre.consolelauncher.managers.notifications

/**
 * Folds an app's grouped notifications (e.g. WhatsApp's "8 messages from 2 chats" plus one
 * notification per chat) into a single list row that opens onto the individual notifications.
 */
object NotificationGroups {
    /** A list row: one notification, or a group whose [children] are listed when it is opened. */
    class Entry<T>(val head: T, val children: List<T>) {
        val isGroup get() = children.isNotEmpty()
    }

    /**
     * Rows for the top-level list, in order of each group's most recent member. A group becomes one
     * row only when it holds two or more notifications; a lone child is shown on its own and a
     * summary without children stands in for the group.
     */
    fun <T> entries(items: List<T>, groupOf: (T) -> String?, isSummary: (T) -> Boolean): List<Entry<T>> {
        val seen = HashSet<String>()
        val out = ArrayList<Entry<T>>()
        for (item in items) {
            val group = groupOf(item)
            if (group == null) {
                out.add(Entry(item, emptyList()))
                continue
            }
            if (!seen.add(group)) continue
            val members = items.filter { groupOf(it) == group }
            val children = members.filterNot(isSummary)
            when {
                children.size >= 2 -> out.add(Entry(members.firstOrNull(isSummary) ?: children[0], children))
                children.size == 1 -> out.add(Entry(children[0], emptyList()))
                else -> members.forEach { out.add(Entry(it, emptyList())) }
            }
        }
        return out
    }

    /** The notifications inside [group], or empty once it no longer has two to list. */
    fun <T> children(items: List<T>, group: String, groupOf: (T) -> String?, isSummary: (T) -> Boolean): List<T> =
        items.filter { groupOf(it) == group && !isSummary(it) }.takeIf { it.size >= 2 } ?: emptyList()

    /** What Prev and Next step through: every notification a list row can expand to, in list order. */
    fun <T> pageable(entries: List<Entry<T>>): List<T> =
        entries.flatMap { if (it.isGroup) it.children else listOf(it.head) }
}
