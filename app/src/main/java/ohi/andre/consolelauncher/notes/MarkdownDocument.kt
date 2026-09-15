package ohi.andre.consolelauncher.notes


object MarkdownDocument {
    private val link = Regex("(?<!!)\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)", RegexOption.IGNORE_CASE)
    private val image = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
    private val check = Regex("(?m)^[ \\t]*- \\[([ xX])][ \\t]+(.*)$")
    private val emptyVisualMarker = Regex("^(?:#{1,6}|- \\[[ xX]]|[-*+]|\\d+\\.|>)\\s*$")

    fun fromLegacy(note: Note): String = buildString {
        append("# ").append(note.title.ifBlank { "Untitled note" }).append("\n\n")
        note.blocks.forEach { block ->
            when (block) {
                is Block.Text -> append(block.text)
                is Block.Checklist -> block.items.forEach { item ->
                    append("- [").append(if (item.checked) 'x' else ' ').append("] ").append(item.text).append('\n')
                }
                is Block.Image -> append("![").append(block.alt.ifBlank { "image" }).append("](").append(block.file).append(')')
            }
            append("\n\n")
        }
    }.trimEnd() + "\n"

    fun ensure(note: Note): String {
        if (note.markdown.isBlank()) note.markdown = fromLegacy(note)
        return note.markdown
    }

    fun title(markdown: String): String? = markdown.lineSequence()
        .firstOrNull { it.startsWith("# ") }
        ?.removePrefix("# ")
        ?.let(::stripInline)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    fun plainText(markdown: String): String = RedactionFormat.hide(markdown)
        .replace(link) { it.groupValues[1] }
        .replace(image) { it.groupValues[1] }
        .replace(Regex("(?m)^#{1,6}[ \\t]+"), "")
        .replace(Regex("(?m)^[ \\t]*(?:- \\[[ xX]] |[-*+] |\\d+\\. |> )"), "")
        .replace(Regex("\\*\\*|__|~~|`|<u>|</u>|\\*|_"), "")
        .lines()
        .let { lines -> if (markdown.startsWith("# ")) lines.drop(1) else lines }
        .joinToString("\n")
        .trim()

    fun imagePaths(markdown: String): List<String> = image.findAll(markdown)
        .map { it.groupValues[2].trim() }
        .filter { it.startsWith("images/") || it.startsWith("notes-library/.assets/") }
        .distinct()
        .toList()

    fun rewriteImages(markdown: String, path: (String) -> String?): String = image.replace(markdown) { match ->
        path(match.groupValues[2])?.let { "![${match.groupValues[1]}]($it)" } ?: match.value
    }

    fun checkCounts(markdown: String): Pair<Int, Int> = check.findAll(markdown).toList().let { matches ->
        matches.count { it.groupValues[1].equals("x", true) } to matches.size
    }

    fun removeEmptyVisualMarker(line: String): String {
        val indent = line.takeWhile(Char::isWhitespace)
        return if (emptyVisualMarker.matches(line.removePrefix(indent))) indent else line
    }

    fun normalizeWebUrl(value: String): String? = runCatching {
        val candidate = value.trim().let { if ("://" in it) it else "https://$it" }
        val parsed = java.net.URI(candidate)
        candidate.takeIf {
            parsed.scheme?.lowercase() in setOf("http", "https") && !parsed.host.isNullOrBlank() && ')' !in candidate
        }
    }.getOrNull()

    fun link(label: String, url: String) = "[$label]($url)"

    private fun stripInline(text: String): String = text
        .replace(Regex("\\*\\*|__|~~|`|<u>|</u>|\\*|_"), "")
}
