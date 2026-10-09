package ohi.andre.consolelauncher.managers.termux

/** Pages remember at most two tiles; selections never change tmux's active window. */
class TermuxWorkspaceState {
    enum class Layout { SINGLE, TOP_BOTTOM, LEFT_RIGHT }
    enum class Placement { REPLACE_FOCUSED, TOP_BOTTOM, LEFT_RIGHT, REPLACE_FIRST, REPLACE_SECOND, NEW_PAGE }
    val placements get() = if (layout == Layout.SINGLE)
        listOf(Placement.REPLACE_FOCUSED, Placement.TOP_BOTTOM, Placement.LEFT_RIGHT, Placement.NEW_PAGE)
        else listOf(Placement.REPLACE_FIRST, Placement.REPLACE_SECOND, Placement.NEW_PAGE)
    fun place(placement: Placement, target: String?) {
        when (placement) {
            Placement.NEW_PAGE -> addPage(target)
            Placement.REPLACE_FOCUSED -> select(focused, target)
            Placement.REPLACE_FIRST -> { focus(0); select(0, target) }
            Placement.REPLACE_SECOND -> { focus(1); select(1, target) }
            Placement.TOP_BOTTOM, Placement.LEFT_RIGHT -> {
                val second = 1 - focused
                layout = if (placement == Placement.TOP_BOTTOM) Layout.TOP_BOTTOM else Layout.LEFT_RIGHT
                select(second, target); focus(second)
            }
        }
    }
    private class Page(var layout: Layout = Layout.SINGLE, var focused: Int = 0,
                       val targets: Array<String?> = arrayOf(null, null))
    private val pages = mutableListOf(Page())
    var pageIndex = 0
        private set
    val pageCount get() = pages.size
    private val page get() = pages[pageIndex]
    var layout: Layout
        get() = page.layout
        set(value) { page.layout = value }
    val targets get() = page.targets
    val focused get() = page.focused
    val focusedTarget get() = targets[focused]
    val visibleTiles get() = if (layout == Layout.SINGLE) listOf(focused) else listOf(0, 1)
    fun focus(tile: Int) { require(tile in 0..1); page.focused = tile }
    fun select(tile: Int, target: String?) {
        require(tile in 0..1)
        require(target == null || validTarget(target))
        targets[tile] = target
    }
    fun addPage(target: String?) {
        require(target == null || validTarget(target))
        pages.add(Page(targets = arrayOf(target, null)))
        pageIndex = pages.lastIndex
    }
    fun movePage(delta: Int) { pageIndex = Math.floorMod(pageIndex + delta, pageCount) }
    fun reconcile(alive: Set<String>) {
        val current = page
        for (entry in pages) {
            for (tile in 0..1) if (entry.targets[tile] !in alive) entry.targets[tile] = null
            if (entry.targets[entry.focused] == null && entry.targets[1 - entry.focused] != null) entry.focused = 1 - entry.focused
            if (entry.targets.any { it == null }) entry.layout = Layout.SINGLE
        }
        pages.removeAll { it.targets.all { target -> target == null } }
        if (pages.isEmpty()) pages.add(Page())
        pageIndex = pages.indexOf(current).takeIf { it >= 0 } ?: pageIndex.coerceAtMost(pages.lastIndex)
    }
    fun encode() = "pages:$pageIndex;" + pages.joinToString(";") {
        "${it.layout.name}|${it.focused}|${it.targets[0].orEmpty()}|${it.targets[1].orEmpty()}"
    }
    companion object {
        private fun validTarget(value: String) = value.matches(Regex("@[0-9]+"))
        fun restore(value: String?): TermuxWorkspaceState = TermuxWorkspaceState().apply {
            val encoded = value ?: return@apply
            val entries = if (encoded.startsWith("pages:")) encoded.substringAfter(';').split(';') else listOf(encoded)
            val restored = entries.mapNotNull {
                val fields = it.split('|')
                if (fields.size != 4) null else Page(
                    Layout.entries.firstOrNull { layout -> layout.name == fields[0] } ?: Layout.SINGLE,
                    fields[1].toIntOrNull()?.takeIf { tile -> tile in 0..1 } ?: 0,
                    arrayOf(fields[2].takeIf(::validTarget), fields[3].takeIf(::validTarget)))
            }
            if (restored.isNotEmpty()) { pages.clear(); pages.addAll(restored) }
            pageIndex = if (encoded.startsWith("pages:")) encoded.substringBefore(';').removePrefix("pages:")
                .toIntOrNull()?.coerceIn(0, pages.lastIndex) ?: 0 else 0
        }
    }
}
