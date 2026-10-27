package ohi.andre.consolelauncher.managers

import java.io.File

/**
 * Offers a web search for input that isn't a command, so "how tall is everest" can be searched
 * without typing "search". Nothing is searched until the user confirms, so a mistyped command
 * never reaches the web by accident.
 */
object WebSearchFallback {
    private val SHELL_SYNTAX = Regex("[|&;<>\$`\\\\/=*]")
    private val SHELL_BUILTINS = setOf(
        "alias", "cd", "echo", "eval", "exec", "exit", "export", "kill", "read", "set", "shift",
        "source", "test", "trap", "type", "ulimit", "umask", "unset", "wait"
    )

    /** A confirmation needs a deliberate second Enter, not a double tap or a doubled key event. */
    internal const val MIN_CONFIRM_DELAY_MS = 600L

    /** The query waiting for confirmation, or null. */
    @Volatile var pending: String? = null
        private set
    @Volatile private var pendingSince = 0L

    /**
     * True when [input] reads like a question or phrase rather than a shell command: two or more
     * words, no shell syntax, and (when the shell runs without a prefix) not an executable.
     */
    internal fun looksLikeSearch(input: String, shellWithoutPrefix: Boolean, isExecutable: (String) -> Boolean): Boolean {
        val words = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size < 2) return false
        if (SHELL_SYNTAX.containsMatchIn(input)) return false
        if (!shellWithoutPrefix) return true
        val first = words[0].lowercase()
        return first !in SHELL_BUILTINS && !isExecutable(first)
    }

    fun looksLikeSearch(input: String, shellWithoutPrefix: Boolean): Boolean =
        looksLikeSearch(input, shellWithoutPrefix, ::onPath)

    enum class Step { ASK, IGNORE, SEARCH }

    /**
     * ASK records [input] as pending the first time, SEARCH confirms when the same input comes
     * back, and IGNORE drops a repeat that arrives too quickly to be a deliberate second Enter.
     */
    fun next(input: String, now: Long = System.currentTimeMillis()): Step {
        val query = input.trim()
        if (query == pending) {
            if (now - pendingSince < MIN_CONFIRM_DELAY_MS) return Step.IGNORE
            pending = null
            return Step.SEARCH
        }
        pending = query
        pendingSince = now
        return Step.ASK
    }

    /** Any other command cancels a pending search. */
    fun cancelUnless(input: String) {
        if (input.trim() != pending) pending = null
    }

    private fun onPath(name: String): Boolean {
        val path = System.getenv("PATH") ?: "/system/bin:/system/xbin"
        return path.split(':').any { dir -> dir.isNotEmpty() && File(dir, name).exists() }
    }
}
