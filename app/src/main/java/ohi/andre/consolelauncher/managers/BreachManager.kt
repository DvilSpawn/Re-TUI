package ohi.andre.consolelauncher.managers

import kotlin.random.Random

object BreachManager {
    enum class Mode(val reward: Int, val gridSize: Int, val bufferSize: Int) {
        NORMAL(500, 4, 4),
        EMERGENCY(1000, 4, 5)
    }

    enum class Axis {
        ROW, COLUMN
    }

    data class Cell(val row: Int, val column: Int)
    data class PickResult(val message: String, val won: Boolean, val lost: Boolean)

    class Session(
        val mode: Mode,
        val grid: Array<Array<String>>,
        val targets: List<List<String>>,
        val bufferSize: Int
    ) {
        private val used = LinkedHashSet<Cell>()
        val buffer: MutableList<String> = ArrayList()
        var activeAxis: Axis = Axis.ROW
            private set
        var activeIndex: Int = 0
            private set
        var complete: Boolean = false
            private set

        fun availableCells(): List<Cell> {
            if (complete) return emptyList()
            val cells = ArrayList<Cell>()
            for (i in grid.indices) {
                val cell = if (activeAxis == Axis.ROW) Cell(activeIndex, i) else Cell(i, activeIndex)
                if (!used.contains(cell)) {
                    cells.add(cell)
                }
            }
            return cells
        }

        fun isUsed(cell: Cell): Boolean = used.contains(cell)

        fun isAvailable(cell: Cell): Boolean = availableCells().contains(cell)

        fun pick(cell: Cell): PickResult {
            if (!isAvailable(cell)) {
                return PickResult("Invalid breach node.", false, false)
            }

            used.add(cell)
            buffer.add(grid[cell.row][cell.column])

            if (targets.any { containsTarget(buffer, it) }) {
                complete = true
                return PickResult("Breach accepted.", true, false)
            }

            if (buffer.size >= bufferSize) {
                complete = true
                return PickResult("Buffer exhausted.", false, true)
            }

            if (activeAxis == Axis.ROW) {
                activeAxis = Axis.COLUMN
                activeIndex = cell.column
            } else {
                activeAxis = Axis.ROW
                activeIndex = cell.row
            }

            if (availableCells().isEmpty()) {
                complete = true
                return PickResult("No valid route remains.", false, true)
            }

            return PickResult("Node accepted.", false, false)
        }
    }

    fun newSession(mode: Mode): Session {
        ensureSelfCheck()

        repeat(80) {
            val grid = Array(mode.gridSize) { Array(mode.gridSize) { token() } }
            val path = randomPath(mode.gridSize, mode.bufferSize)
            if (path.size >= mode.bufferSize) {
                val targets = targetsFor(mode, path, grid)
                return Session(mode, grid, targets, mode.bufferSize)
            }
        }

        val grid = Array(mode.gridSize) { row -> Array(mode.gridSize) { col -> TOKEN_POOL[(row + col) % TOKEN_POOL.size] } }
        val path = listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1), Cell(2, 2))
        val targets = targetsFor(mode, path, grid)
        return Session(mode, grid, targets, mode.bufferSize)
    }

    fun containsTarget(buffer: List<String>, target: List<String>): Boolean {
        if (target.isEmpty() || buffer.size < target.size) return false
        for (start in 0..buffer.size - target.size) {
            var matches = true
            for (i in target.indices) {
                if (buffer[start + i] != target[i]) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    private fun targetsFor(mode: Mode, path: List<Cell>, grid: Array<Array<String>>): List<List<String>> {
        return listOf(writeTarget(path, grid, 0, mode.bufferSize))
    }

    private fun writeTarget(path: List<Cell>, grid: Array<Array<String>>, start: Int, length: Int): List<String> {
        val target = ArrayList<String>()
        for (i in 0 until length) {
            val token = TOKEN_POOL[Random.nextInt(TOKEN_POOL.size)]
            val cell = path[start + i]
            grid[cell.row][cell.column] = token
            target.add(token)
        }
        return target
    }

    private fun randomPath(size: Int, wanted: Int): List<Cell> {
        val path = ArrayList<Cell>()
        val used = LinkedHashSet<Cell>()
        var axis = Axis.ROW
        var index = 0

        repeat(wanted) {
            val options = ArrayList<Cell>()
            for (i in 0 until size) {
                val cell = if (axis == Axis.ROW) Cell(index, i) else Cell(i, index)
                if (!used.contains(cell)) options.add(cell)
            }
            if (options.isEmpty()) return path
            val cell = options[Random.nextInt(options.size)]
            used.add(cell)
            path.add(cell)
            if (axis == Axis.ROW) {
                axis = Axis.COLUMN
                index = cell.column
            } else {
                axis = Axis.ROW
                index = cell.row
            }
        }
        return path
    }

    private fun token(): String = TOKEN_POOL[Random.nextInt(TOKEN_POOL.size)]

    private fun ensureSelfCheck() {
        if (selfChecked) return
        check(containsTarget(listOf("1C", "55", "7A"), listOf("55", "7A")))
        check(!containsTarget(listOf("1C", "55", "7A"), listOf("7A", "55")))
        val session = Session(
            Mode.NORMAL,
            arrayOf(arrayOf("1C", "55"), arrayOf("7A", "BD")),
            listOf(listOf("1C", "7A", "BD", "55")),
            4
        )
        check(!session.pick(Cell(0, 0)).won)
        check(!session.pick(Cell(1, 0)).won)
        check(!session.pick(Cell(1, 1)).won)
        check(session.pick(Cell(0, 1)).won)
        selfChecked = true
    }

    private var selfChecked = false

    private val TOKEN_POOL = arrayOf("1C", "55", "7A", "BD", "E9", "FF", "A0", "C3")
}
