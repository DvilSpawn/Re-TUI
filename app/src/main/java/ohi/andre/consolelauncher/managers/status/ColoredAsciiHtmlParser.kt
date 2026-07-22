package ohi.andre.consolelauncher.managers.status

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object ColoredAsciiHtmlParser {
    data class Result(val text: String, val colors: IntArray)

    private val RGB = Regex("""rgb\s*\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)""", RegexOption.IGNORE_CASE)
    private val HEX = Regex("""#([0-9a-f]{6})\b""", RegexOption.IGNORE_CASE)

    fun parseOrNull(source: String): Result? {
        if (!source.contains("<pre", ignoreCase = true)) return null
        val pre = Jsoup.parse(source).selectFirst("pre") ?: return null
        val text = StringBuilder()
        val colors = ArrayList<Int>()
        append(pre, 0, text, colors)
        if (text.isBlank()) return null
        return Result(text.toString(), colors.toIntArray())
    }

    private fun append(node: Node, inheritedColor: Int, text: StringBuilder, colors: MutableList<Int>) {
        when (node) {
            is TextNode -> appendText(node.wholeText, inheritedColor, text, colors)
            is Element -> {
                if (node.normalName() == "script" || node.normalName() == "style") return
                if (node.normalName() == "br") {
                    appendText("\n", inheritedColor, text, colors)
                    return
                }
                val color = parseColor(node.attr("style")) ?: inheritedColor
                node.childNodes().forEach { append(it, color, text, colors) }
            }
        }
    }

    private fun appendText(value: String, color: Int, text: StringBuilder, colors: MutableList<Int>) {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        text.append(normalized)
        repeat(normalized.length) { colors.add(color) }
    }

    private fun parseColor(style: String): Int? {
        val declaration = style.split(';').firstOrNull { it.substringBefore(':').trim().equals("color", true) }
            ?.substringAfter(':', "")?.trim() ?: return null
        RGB.find(declaration)?.let { match ->
            val (red, green, blue) = match.destructured
            return argb(red.toInt(), green.toInt(), blue.toInt())
        }
        HEX.find(declaration)?.groupValues?.get(1)?.let { return (0xff000000L or it.toLong(16)).toInt() }
        return null
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xff000000L or
            (red.coerceIn(0, 255).toLong() shl 16) or
            (green.coerceIn(0, 255).toLong() shl 8) or
            blue.coerceIn(0, 255).toLong()).toInt()
}
