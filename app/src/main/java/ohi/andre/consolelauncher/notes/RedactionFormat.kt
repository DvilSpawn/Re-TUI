package ohi.andre.consolelauncher.notes


object RedactionFormat {
    val token = Regex("\\{\\{remember-redact:v1:(\\d{1,3}):([A-Za-z0-9_-]+)\\}\\}")

    private val word = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’-]*")
    private val markdownPrefix = Regex("^\\s*(?:#{1,6}\\s+|- \\[[ xX]]\\s+|[-*+]\\s+|\\d+\\.\\s+|>\\s+)?")
    private val mundane = setOf(
        "a", "an", "the", "and", "or", "but", "if", "then", "of", "to", "in", "on", "at", "by", "for", "with", "from", "as",
        "is", "am", "are", "was", "were", "be", "been", "being", "do", "does", "did", "have", "has", "had", "it", "this", "that"
    )

    fun encoded(width: Int, payload: String) = "{{remember-redact:v1:${width.coerceIn(4, 96)}:$payload}}"

    fun contains(markdown: String) = token.containsMatchIn(markdown)

    fun hide(markdown: String) = token.replace(markdown, "[REDACTED]")

    fun manual(text: String, encrypt: (String) -> String): String =
        text.split('\n').joinToString("\n") { if (word.containsMatchIn(it)) encrypt(it) else it }

    fun automatic(markdown: String, encrypt: (String) -> String): String = markdown.lines().mapIndexed { index, line ->
        if (index == 0 && line.startsWith("# ")) line else redactLine(line, encrypt)
    }.joinToString("\n")

    private fun redactLine(line: String, encrypt: (String) -> String): String = buildString {
        var cursor = 0
        token.findAll(line).forEach { match ->
            append(redactPlain(line.substring(cursor, match.range.first), cursor == 0, encrypt))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(redactPlain(line.substring(cursor), cursor == 0, encrypt))
    }

    private fun redactPlain(value: String, keepPrefix: Boolean, encrypt: (String) -> String): String {
        if (value.isBlank() || value.startsWith("![")) return value
        val prefixEnd = if (keepPrefix) markdownPrefix.find(value)?.range?.last?.plus(1) ?: 0 else 0
        val prefix = value.substring(0, prefixEnd)
        val content = value.substring(prefixEnd)
        val allowed = word.findAll(content).filter { it.value.lowercase() in mundane }.toList()
        if (word.findAll(content).all { it.value.lowercase() in mundane }) return value
        if (allowed.isEmpty()) return prefix + encrypt(content)
        return prefix + buildString {
            var cursor = 0
            allowed.forEach { match ->
                append(redactIfNeeded(content.substring(cursor, match.range.first), encrypt))
                append(match.value)
                cursor = match.range.last + 1
            }
            append(redactIfNeeded(content.substring(cursor), encrypt))
        }
    }

    private fun redactIfNeeded(value: String, encrypt: (String) -> String) =
        if (word.containsMatchIn(value)) encrypt(value) else value
}
