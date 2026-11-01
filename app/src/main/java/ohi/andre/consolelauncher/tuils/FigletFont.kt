package ohi.andre.consolelauncher.tuils

import android.content.Context

/**
 * A FIGlet (.flf) font: draws text as ASCII-art letters, fitting and smushing neighbours by the font's own layout
 * rules the way the figlet tool does, so typed text matches what figlet prints.
 */
class FigletFont private constructor(
    private val hardblank: Char,
    val height: Int,
    private val layout: Int,
    private val glyphs: Map<Int, List<String>>
) {
    companion object {
        const val DEFAULT = "small"
        // Bundled under assets/figlet; the first is the default.
        val BUNDLED = listOf("small", "standard", "slant", "mini", "shadow")
        private const val SMUSH_EQUAL = 1
        private const val SMUSH_LOWLINE = 2
        private const val SMUSH_HIERARCHY = 4
        private const val SMUSH_PAIR = 8
        private const val SMUSH_BIGX = 16
        private const val SMUSH_HARDBLANK = 32
        private const val FIT = 64
        private const val SMUSH = 128
        private val DEUTSCH = intArrayOf(196, 214, 220, 228, 246, 252, 223)
        private val HIERARCHY = listOf("|", "/\\", "[]", "{}", "()", "<>")
        private val cache = HashMap<String, FigletFont>()

        fun load(context: Context, name: String): FigletFont? {
            val key = name.lowercase()
            if (key !in BUNDLED) return null
            synchronized(cache) { cache[key]?.let { return it } }
            val font = runCatching { context.assets.open("figlet/$key.flf").bufferedReader().use { parse(it.readText()) } }.getOrNull()
            if (font != null) synchronized(cache) { cache[key] = font }
            return font
        }

        fun parse(source: String): FigletFont {
            val lines = source.replace("\r\n", "\n").split('\n')
            val header = lines.first().split(' ').filter { it.isNotEmpty() }
            require(header.first().startsWith("flf2a") && header.first().length >= 6) { "Not a FIGlet font" }
            val hardblank = header[0][5]
            val height = header[1].toInt()
            val oldLayout = header[4].toInt()
            val comments = header[5].toInt()
            // The full layout (header field 8) wins; older fonts only give the old one.
            val layout = header.getOrNull(7)?.toIntOrNull() ?: when {
                oldLayout < 0 -> 0
                oldLayout == 0 -> FIT
                else -> (oldLayout and 31) or SMUSH
            }
            val glyphs = HashMap<Int, List<String>>()
            var at = 1 + comments
            fun glyph(): List<String>? {
                if (at + height > lines.size) return null
                val rows = (0 until height).map { row ->
                    val line = lines[at + row].trimEnd()
                    val end = line.lastOrNull() ?: return@map ""
                    line.trimEnd(end)
                }
                at += height
                return rows
            }
            for (code in 32..126) glyphs[code] = glyph() ?: break
            for (code in DEUTSCH) glyphs[code] = glyph() ?: break
            // Code-tagged characters: a line starting with the code, then the glyph.
            while (at < lines.size) {
                val tag = lines[at].trim().substringBefore(' ')
                if (tag.isEmpty()) { at++; continue }
                val code = runCatching {
                    when {
                        tag.startsWith("0x", true) -> tag.substring(2).toInt(16)
                        tag.startsWith("-0x", true) -> -tag.substring(3).toInt(16)
                        tag.length > 1 && tag.startsWith("0") -> tag.toInt(8)
                        else -> tag.toInt()
                    }
                }.getOrNull() ?: break
                at++
                val rows = glyph() ?: break
                if (code >= 0) glyphs[code] = rows
            }
            return FigletFont(hardblank, height, layout, glyphs)
        }
    }

    /** [text] as FIGlet rows; words wrap so no block is wider than [width] columns (0 keeps one block per line). */
    fun render(text: String, width: Int = 0): String =
        text.split('\n').flatMap { line -> wrap(line, width) }.joinToString("\n") { it.joinToString("\n") }
            .lines().joinToString("\n") { it.trimEnd() }.trim('\n')

    private fun wrap(line: String, width: Int): List<List<String>> {
        if (width <= 0) return listOf(row(line))
        val blocks = ArrayList<List<String>>()
        var current = ""
        for (word in line.split(' ').filter { it.isNotEmpty() }) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && widthOf(row(next)) > width) { blocks += row(current); current = word } else current = next
        }
        if (current.isNotEmpty() || blocks.isEmpty()) blocks += row(current)
        return blocks
    }

    private fun widthOf(rows: List<String>) = rows.maxOfOrNull { it.length } ?: 0

    /** One line of text as [height] rows, still holding hardblanks until the end. */
    private fun row(text: String): List<String> {
        val out = Array(height) { StringBuilder() }
        var previousWidth = 0
        for (ch in text) {
            val glyph = glyphs[ch.code] ?: glyphs['?'.code] ?: continue
            val width = glyph.maxOf { it.length }
            val overlap = if (layout and (FIT or SMUSH) == 0) 0 else overlap(out, glyph, previousWidth, width)
            for (r in 0 until height) {
                val row = out[r]
                val part = glyph[r].padEnd(width)
                for (k in 0 until overlap) {
                    val column = row.length - overlap + k
                    if (column >= 0) row.setCharAt(column, smush(row[column], part[k], previousWidth, width) ?: part[k])
                }
                row.append(part.substring(overlap))
            }
            previousWidth = width
        }
        val total = out.maxOf { it.length }
        return out.map { it.toString().padEnd(total).replace(hardblank, ' ') }
    }

    /** How far the next glyph can slide left into the line so far (figlet's smushamt). */
    private fun overlap(out: Array<StringBuilder>, glyph: List<String>, previousWidth: Int, width: Int): Int {
        var amount = width
        for (r in 0 until height) {
            val line = out[r]
            // figlet scans from the line's terminator (index length) down to its last character, stopping at 0.
            var lineEnd = line.length
            while (lineEnd > 0 && (lineEnd == line.length || line[lineEnd] == ' ')) lineEnd--
            val row = glyph[r]
            var charStart = 0
            while (charStart < row.length && row[charStart] == ' ') charStart++
            var rowAmount = charStart + line.length - 1 - lineEnd
            val left = line.getOrNull(lineEnd)
            if (left == null || left == ' ') rowAmount++
            else if (charStart < row.length && smush(left, row[charStart], previousWidth, width) != null) rowAmount++
            amount = minOf(amount, rowAmount)
        }
        // Not capped by the line so far: like figlet, the first glyph loses its blank leading columns.
        return amount.coerceIn(0, width)
    }

    /** The character two overlapping ones become, or null where they cannot share a column (figlet's smushem). */
    private fun smush(left: Char, right: Char, previousWidth: Int, width: Int): Char? {
        if (left == ' ') return right
        if (right == ' ') return left
        if (previousWidth < 2 || width < 2) return null
        if (layout and SMUSH == 0) return null
        if (layout and 63 == 0) {
            if (left == hardblank) return right
            if (right == hardblank) return left
            return right
        }
        if (layout and SMUSH_HARDBLANK != 0 && left == hardblank && right == hardblank) return left
        if (left == hardblank || right == hardblank) return null
        if (layout and SMUSH_EQUAL != 0 && left == right) return left
        if (layout and SMUSH_LOWLINE != 0) {
            if (left == '_' && right in "|/\\[]{}()<>") return right
            if (right == '_' && left in "|/\\[]{}()<>") return left
        }
        if (layout and SMUSH_HIERARCHY != 0) {
            val l = HIERARCHY.indexOfFirst { left in it }
            val r = HIERARCHY.indexOfFirst { right in it }
            if (l >= 0 && r >= 0 && l != r) return if (r > l) right else left
        }
        if (layout and SMUSH_PAIR != 0 && "$left$right" in setOf("[]", "][", "{}", "}{", "()", ")(")) return '|'
        if (layout and SMUSH_BIGX != 0) when ("$left$right") {
            "/\\" -> return '|'
            "\\/" -> return 'Y'
            "><" -> return 'X'
        }
        return null
    }
}
