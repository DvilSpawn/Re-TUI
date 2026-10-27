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

    /** The query waiting for confirmation, or null. */
    @Volatile var pending: String? = null
        private set

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

    /**
     * Records [input] as pending and returns false the first time; returns true when the same
     * input is submitted again, which confirms the search.
     */
    fun confirm(input: String): Boolean {
        val query = input.trim()
        if (query == pending) {
            pending = null
            return true
        }
        pending = query
        return false
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
