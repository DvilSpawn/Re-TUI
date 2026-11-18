package ohi.andre.consolelauncher.managers.status

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

/**
 * Colours inside a status line's format: `[#FF0088]RAM [#00BFFF]%avgb GB` draws "RAM " in pink and the rest in blue.
 * A tag runs until the next one; `[/]` goes back to the line's own colour. Eight digits (`#AARRGGBB`) set a
 * transparency too. Anything else in square brackets is left as it is.
 */
object StatusColors {
    private val TAG = Regex("\\[(?:#([0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)|/)]")

    /** A stretch of the cleaned text, [start] to [end], drawn in [color]. */
    data class Run(val start: Int, val end: Int, val color: Int)

    /** [text] without its colour tags, and the stretches they colour. */
    fun parse(text: String): Pair<String, List<Run>> {
        val clean = StringBuilder()
        val runs = ArrayList<Run>()
        var color: Int? = null
        var from = 0
        var at = 0
        fun close() { color?.let { if (clean.length > from) runs += Run(from, clean.length, it) } }
        for (tag in TAG.findAll(text)) {
            clean.append(text, at, tag.range.first)
            close()
            color = tag.groupValues[1].takeIf { it.isNotEmpty() }?.let(::toColor)
            from = clean.length
            at = tag.range.last + 1
        }
        clean.append(text, at, text.length)
        close()
        return clean.toString() to runs
    }

    private fun toColor(hex: String): Int {
        val value = hex.toLong(16)
        return (if (hex.length == 6) value or 0xFF000000 else value).toInt()
    }

    /** [text] with its tags turned into colours, keeping the spans it already has; unchanged when it has none. */
    fun apply(text: CharSequence?): CharSequence? {
        if (text == null || !TAG.containsMatchIn(text)) return text
        val out = SpannableStringBuilder(text)
        TAG.findAll(text).toList().asReversed().forEach { out.delete(it.range.first, it.range.last + 1) }
        parse(text.toString()).second.forEach { out.setSpan(ForegroundColorSpan(it.color), it.start, it.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return out
    }
}
