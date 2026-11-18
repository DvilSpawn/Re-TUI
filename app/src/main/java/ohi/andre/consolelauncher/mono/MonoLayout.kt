package ohi.andre.consolelauncher.mono

import java.util.Locale

/**
 * Mono home's panels on a four-column grid, in pages. Each panel in turn takes the first free spot its size fits,
 * reading the grid left to right and top to bottom, so a tall panel can have two small ones stacked beside it.
 *
 * Written as panel names separated by commas, pages separated by `|`: `scene,clock,apps|term:4x4@2,read:2x4`. A
 * panel at other than its usual size adds `:<columns>x<rows>`; a terminal always does, then `@<window>` once it
 * shows one.
 */
object MonoLayout {
    const val COLUMNS = 4
    /** Rows of the grid one screen holds; a page with more is squeezed to fit. */
    const val PAGE_ROWS = 8

    /** Each kind's sizes as columns to rows, the usual one first; a kind drawn as a square keeps one. */
    enum class Kind(vararg sizes: Pair<Int, Int>) {
        SCENE(2 to 2), CLOCK(2 to 2),
        APPS(4 to 2, 4 to 4), MEDIA(4 to 2, 4 to 4),
        QUICK(2 to 2, 2 to 4),
        TASKS(2 to 2, 2 to 4, 4 to 2), NEXT(2 to 2, 2 to 4, 4 to 2), ALERTS(4 to 2, 2 to 2, 2 to 4),
        READ(2 to 2, 2 to 4, 4 to 2, 4 to 4),
        TERM(4 to 4, 4 to 2, 2 to 2);

        val sizes: List<Pair<Int, Int>> = sizes.toList()
        val columns: Int get() = sizes[0].first
        val rows: Int get() = sizes[0].second
        val key: String get() = name.lowercase(Locale.ROOT)
    }

    class Item(val kind: Kind, val columns: Int = kind.columns, val rows: Int = kind.rows, val window: String? = null) {
        fun write(): String {
            val sized = kind == Kind.TERM || columns != kind.columns || rows != kind.rows
            return kind.key + (if (sized) ":${columns}x$rows" else "") + (window?.let { "@" + it.removePrefix("@") } ?: "")
        }

        override fun equals(other: Any?) = other is Item && other.write() == write()
        override fun hashCode() = write().hashCode()
        override fun toString() = write()
    }

    val DEFAULT: List<List<Item>> = listOf(listOf(Kind.SCENE, Kind.CLOCK, Kind.APPS, Kind.MEDIA, Kind.QUICK, Kind.TASKS).map { Item(it) })

    private val TOKEN = Regex("([a-z]+)(?::([0-9]+)x([0-9]+))?(?:@([0-9]+))?")

    /**
     * The pages named in [raw]. Unknown names are skipped, and each panel other than a terminal shows once. Empty
     * pages are kept, so a page just added stays; nothing at all means the default.
     */
    fun parse(raw: String?): List<List<Item>> {
        val seen = HashSet<Kind>()
        val pages = raw.orEmpty().split('|').map { page ->
            page.split(',').mapNotNull { token -> item(token.trim().lowercase(Locale.ROOT)) }
                .filter { it.kind == Kind.TERM || seen.add(it.kind) }
        }
        return if (pages.all { it.isEmpty() }) DEFAULT else pages
    }

    /** A token such as `read`, `read:2x4` or `term:4x2@3`; a size the kind does not take falls back to its usual one. */
    private fun item(token: String): Item? {
        val match = TOKEN.matchEntire(token) ?: return null
        val kind = Kind.entries.firstOrNull { it.key == match.groupValues[1] } ?: return null
        val asked = match.groupValues[2].toIntOrNull()?.let { it to (match.groupValues[3].toIntOrNull() ?: 0) }
        val size = asked?.takeIf { it in kind.sizes } ?: kind.sizes[0]
        val window = if (kind == Kind.TERM) match.groupValues[4].takeIf { it.isNotEmpty() }?.let { "@$it" } else null
        return Item(kind, size.first, size.second, window)
    }

    fun write(pages: List<List<Item>>): String = pages.joinToString("|") { page -> page.joinToString(",") { it.write() } }

    /** Where a panel sits on the grid, in columns and rows from the top left. */
    class Spot(val item: Item, val column: Int, val row: Int)

    /** Gives each of [items], in order, the first free spot its size fits. */
    fun place(items: List<Item>): List<Spot> {
        val taken = ArrayList<BooleanArray>()
        fun free(column: Int, row: Int, item: Item): Boolean {
            for (r in row until row + item.rows) {
                val line = taken.getOrNull(r) ?: continue
                for (c in column until column + item.columns) if (line[c]) return false
            }
            return true
        }
        return items.map { item ->
            var row = 0
            var column: Int
            while (true) {
                column = (0..COLUMNS - item.columns).firstOrNull { free(it, row, item) } ?: -1
                if (column >= 0) break
                row++
            }
            while (taken.size < row + item.rows) taken += BooleanArray(COLUMNS)
            for (r in row until row + item.rows) for (c in column until column + item.columns) taken[r][c] = true
            Spot(item, column, row)
        }
    }

    /** Grid rows [items] take. */
    fun height(items: List<Item>): Int = place(items).maxOfOrNull { it.row + it.item.rows } ?: 0

    /** Panels not shown yet, which the add slot offers; terminals can always be added. */
    fun missing(pages: List<List<Item>>): List<Kind> {
        val shown = pages.flatten().map { it.kind }.toSet()
        return Kind.entries.filter { it == Kind.TERM || it !in shown }
    }

    /**
     * Puts [kind] at [slot] of [page]: a kind already shown, on any page, trades places with the one there, each
     * keeping its size; a new one replaces it at its usual size.
     */
    fun swap(pages: List<List<Item>>, page: Int, slot: Int, kind: Kind): List<List<Item>> {
        val next = pages.map { it.toMutableList() }
        if (page !in next.indices || slot !in next[page].indices) return next
        val current = next[page][slot]
        var moved: Item? = null
        if (kind != Kind.TERM) {
            next.forEach { other ->
                val at = other.indexOfFirst { it.kind == kind }
                if (at >= 0) { moved = other[at]; other[at] = current }
            }
        }
        next[page][slot] = moved ?: Item(kind)
        return next
    }

    fun replace(pages: List<List<Item>>, page: Int, slot: Int, item: Item): List<List<Item>> =
        pages.mapIndexed { p, items -> if (p == page) items.mapIndexed { s, it -> if (s == slot) item else it } else items }

    /**
     * Moves the panel at [slot] of [page] earlier ([by] -1) or later (1) in the page's order, past as many panels as
     * it takes to land somewhere else on screen: a smaller neighbour can drop back into the gap beside a big panel,
     * leaving it where it was. The layout comes back unchanged when it cannot move that way.
     */
    fun move(pages: List<List<Item>>, page: Int, slot: Int, by: Int): List<List<Item>> {
        val items = pages.getOrNull(page) ?: return pages
        if (slot !in items.indices || by == 0) return pages
        val before = place(items)[slot]
        var target = slot
        while (true) {
            target += if (by < 0) -1 else 1
            if (target !in items.indices) return pages
            val next = items.toMutableList().apply { add(target, removeAt(slot)) }
            val after = place(next)[target]
            if (after.column != before.column || after.row != before.row) return pages.mapIndexed { p, it -> if (p == page) next else it }
        }
    }

    /** Whether [move] would put the panel somewhere else on screen. */
    fun canMove(pages: List<List<Item>>, page: Int, slot: Int, by: Int): Boolean = move(pages, page, slot, by) != pages

    fun add(pages: List<List<Item>>, page: Int, item: Item): List<List<Item>> =
        pages.mapIndexed { p, items -> if (p == page) items + item else items }

    fun remove(pages: List<List<Item>>, page: Int, slot: Int): List<List<Item>> =
        pages.mapIndexed { p, items -> if (p == page) items.filterIndexed { s, _ -> s != slot } else items }

    /** A new empty page after [page]. */
    fun addPage(pages: List<List<Item>>, page: Int): List<List<Item>> =
        pages.toMutableList().apply { add((page + 1).coerceIn(0, size), emptyList()) }

    /** Takes [page] away with its panels; the last page left stays. */
    fun removePage(pages: List<List<Item>>, page: Int): List<List<Item>> =
        if (pages.size <= 1 || page !in pages.indices) pages else pages.filterIndexed { p, _ -> p != page }
}
