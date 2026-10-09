package ohi.andre.consolelauncher.community

/** Completion is an edit of one token, never a launcher command. */
object CommunityMentionInput {
    private fun start(text: String, caret: Int): Int? {
        if (caret !in 0..text.length) return null
        var start = caret
        while (start > 0 && text[start - 1] in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-") start--
        if (start == 0 || text[start - 1] != '@' || caret - start > 24) return null
        val at = start - 1
        if (at > 0 && text[at - 1] in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-@") return null
        return at
    }
    fun ranges(text: String, handle: String): List<IntRange> {
        if (!handle.matches(Regex("[-_a-z0-9]{3,24}"))) return emptyList()
        return Regex("(?<![-_a-zA-Z0-9@])@${Regex.escape(handle)}(?![-_a-zA-Z0-9])", RegexOption.IGNORE_CASE).findAll(text).map { it.range }.toList()
    }
    fun fragment(text: String, caret: Int): String? = start(text, caret)?.let { text.substring(it + 1, caret) }
    fun insert(text: String, caret: Int, handle: String): String {
        if (!handle.matches(Regex("[-_a-z0-9]{3,24}"))) return text
        val at = start(text, caret) ?: return text
        var end = caret
        while (end < text.length && text[end] in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-") end++
        return text.substring(0, at) + "@$handle" + text.substring(end)
    }
}
