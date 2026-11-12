package ohi.andre.consolelauncher.wallpaper.pixel

/** A 3×5 font for the tiny numbers around the dot: digits, °, -, and the C/F units. */
object DotDigits {
    const val WIDTH = 3
    const val HEIGHT = 5
    const val ADVANCE = 4

    private val glyphs: Map<Char, IntArray> = mapOf(
        '0' to "111 101 101 101 111", '1' to "010 110 010 010 111", '2' to "111 001 111 100 111",
        '3' to "111 001 111 001 111", '4' to "101 101 111 001 001", '5' to "111 100 111 001 111",
        '6' to "111 100 111 101 111", '7' to "111 001 010 010 010", '8' to "111 101 111 101 111",
        '9' to "111 101 111 001 111", '°' to "010 101 010 000 000", '-' to "000 000 111 000 000",
        'C' to "111 100 100 100 111", 'F' to "111 100 110 100 100", 'K' to "101 101 110 101 101",
        ' ' to "000 000 000 000 000"
    ).mapValues { (_, rows) -> rows.split(' ').map { it.toInt(2) }.toIntArray() }

    fun width(text: String): Int = if (text.isEmpty()) 0 else text.length * ADVANCE - 1

    /** Calls [dot] for every lit cell of [text] centred on (cx, cy) in grid cells. */
    inline fun drawCentred(text: String, cx: Int, cy: Int, dot: (Int, Int) -> Unit) {
        val left = cx - width(text) / 2
        val top = cy - HEIGHT / 2
        text.forEachIndexed { index, c ->
            val rows = rows(c)
            for (row in 0 until HEIGHT) for (col in 0 until WIDTH) {
                if (rows[row] shr (WIDTH - 1 - col) and 1 != 0) dot(left + index * ADVANCE + col, top + row)
            }
        }
    }

    fun rows(c: Char): IntArray = glyphs[c.uppercaseChar()] ?: glyphs.getValue(' ')

    /** A lightning bolt, 3×5, that stands in for the dot while charging. */
    val BOLT = intArrayOf(0b001, 0b010, 0b111, 0b010, 0b100)
}
