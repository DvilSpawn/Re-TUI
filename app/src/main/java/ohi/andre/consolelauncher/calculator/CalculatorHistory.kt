package ohi.andre.consolelauncher.calculator

/** Successful submissions only. Preview results never enter this list. */
class CalculatorHistory {
    data class Entry(val mode: String, val input: String, val result: String,
        val reusable: String?, val degrees: Boolean = true, val exact: Boolean = true,
        val category: Int = 0, val source: Int = 0, val target: Int = 1, val approximate: Boolean = false)
    val entries = mutableListOf<Entry>()
    fun add(entry: Entry) {
        if (entries.firstOrNull() == entry) return
        entries.add(0, entry)
        while (entries.size > 100) entries.removeAt(entries.lastIndex)
    }
}

object CalculatorEditing {
    data class Edit(val text: String, val cursor: Int)
    fun insert(text: String, start: Int, end: Int, token: String): Edit {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(a, text.length)
        return Edit(text.replaceRange(a, b, token), a + token.length)
    }
    fun backspace(text: String, start: Int, end: Int): Edit {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(a, text.length)
        return insert(text, if (a == b && a > 0) text.offsetByCodePoints(a, -1) else a, b, "")
    }
    fun wrap(text: String, start: Int, end: Int, prefix: String, suffix: String): Edit {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(a, text.length)
        val edit = insert(text, a, b, prefix + text.substring(a, b) + suffix)
        return edit.copy(cursor = if (a == b) a + prefix.length else edit.cursor)
    }
}
