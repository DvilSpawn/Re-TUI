package ohi.andre.consolelauncher.managers.termux

/**
 * Grid coordinates stay independent of screen pixels and terminal connections.
 * Also arranges the widget pane, which supplies its own column count and per-widget size limits.
 */
class TerminalHomeLayout(
    private val columns: Int = 6,
    private val sizeAllowed: (Tile) -> Boolean = { it.width in 2..6 && it.height in 2..12 }
) {
    data class Tile(val id: String, var x: Int, var y: Int, var width: Int, var height: Int)
    val tiles = mutableListOf<Tile>()
    fun fits(tile: Tile, excluding: String? = tile.id): Boolean =
        tile.x >= 0 && tile.y >= 0 && tile.width >= 1 && tile.height >= 1 && sizeAllowed(tile) &&
            tile.x + tile.width <= columns && tile.y <= Int.MAX_VALUE - tile.height && tiles.none {
                it.id != excluding && tile.x < it.x + it.width && tile.x + tile.width > it.x &&
                    tile.y < it.y + it.height && tile.y + tile.height > it.y
            }
    fun add(id: String): Tile? {
        // A new tile can always start at the bottom edge of an existing tile.
        val rows = (listOf(0) + tiles.map { it.y + it.height }).distinct().sorted()
        for (y in rows) for (x in 0..3) {
            val candidate = Tile(id, x, y, 3, 4)
            if (fits(candidate)) { tiles += candidate; return candidate }
        }
        return null
    }
    /** Anchor the edited tile; only push obstructing tiles down, preserving their sizes. */
    fun reflow(proposed: Tile, original: List<Tile> = tiles): List<Tile>? {
        if (original.none { it.id == proposed.id }) return null
        val placed = TerminalHomeLayout(columns, sizeAllowed)
        if (!placed.fits(proposed)) return null
        placed.tiles += proposed.copy()
        for (tile in original.filter { it.id != proposed.id }.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
            val next = tile.copy()
            while (true) {
                val blockers = placed.tiles.filter { next.x < it.x + it.width && next.x + next.width > it.x &&
                    next.y < it.y + it.height && next.y + next.height > it.y }
                if (blockers.isEmpty()) break
                next.y = blockers.maxOf { it.y + it.height }
                if (next.y > Int.MAX_VALUE - next.height) return null
            }
            if (!placed.fits(next)) return null
            placed.tiles += next
        }
        return original.map { tile -> placed.tiles.first { it.id == tile.id } }
    }
    /** Settle upward without changing columns, sizes, or the vertical order of overlapping columns. */
    fun compact(placement: List<Tile> = tiles): List<Tile> {
        val settled = mutableListOf<Tile>()
        for (tile in placement.sortedWith(compareBy({ it.y }, { it.x }, { it.id }))) {
            val floor = settled.filter { tile.x < it.x + it.width && tile.x + tile.width > it.x }
                .maxOfOrNull { it.y + it.height } ?: 0
            settled += tile.copy(y = floor)
        }
        val byId = settled.associateBy { it.id }
        return placement.map { byId.getValue(it.id) }
    }
    fun applyPlacement(placement: List<Tile>) {
        require(placement.map { it.id }.toSet() == tiles.map { it.id }.toSet())
        val check = TerminalHomeLayout(columns, sizeAllowed).apply { tiles.addAll(placement) }
        require(placement.all { check.fits(it) })
        tiles.forEach { tile ->
            val next = placement.first { it.id == tile.id }
            tile.x = next.x; tile.y = next.y; tile.width = next.width; tile.height = next.height
        }
    }
    fun update(id: String, x: Int, y: Int, width: Int, height: Int): Boolean {
        val tile = tiles.firstOrNull { it.id == id } ?: return false
        val proposed = Tile(id, x, y, width, height)
        if (!fits(proposed)) return false
        tile.x = x; tile.y = y; tile.width = width; tile.height = height
        return true
    }
}
