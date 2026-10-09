package ohi.andre.consolelauncher.community

object CommunityInput {
    sealed class Result
    data class Message(val body: String) : Result()
    data class Command(val name: String, val argument: String = "") : Result()
    data class Rejected(val reason: String) : Result()
    fun message(text: String): Result {
        if (text.isBlank()) return Rejected("Message is empty.")
        if (text.codePointCount(0, text.length) > 500 || text.toByteArray(Charsets.UTF_8).size > 2000)
            return Rejected("Limit: 500 Unicode characters / 2000 UTF-8 bytes.")
        var index = 0
        while (index < text.length) {
            val c = text[index++]
            if (Character.isHighSurrogate(c)) {
                if (index == text.length || !Character.isLowSurrogate(text[index++])) return Rejected("Invalid Unicode.")
            } else if (Character.isLowSurrogate(c)) return Rejected("Invalid Unicode.")
        }
        return Message(text)
    }
    @JvmStatic fun parse(text: String): Result {
        if (!text.startsWith("/") && text.trimStart().startsWith("/"))
            return Rejected("Start slash commands at the beginning of input.")
        if (text.startsWith("//")) return message(text.substring(1))
        if (!text.startsWith("/")) return message(text)
        val parts = text.substring(1).split(" ", limit = 2)
        val name = parts[0]
        val argument = parts.getOrElse(1) { "" }.trim()
        if (name !in setOf("help", "refresh", "older", "report", "block", "login", "register", "logout", "exit"))
            return Rejected("Unknown slash command; use /help.")
        if ((name in setOf("report", "block")) != argument.isNotEmpty())
            return Rejected("Invalid command arguments; use /help.")
        return Command(name, argument)
    }
}
