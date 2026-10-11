package ohi.andre.consolelauncher.irc

import java.util.Locale

/** Android-free IRC framing. Prefixes are routing information, never message headers. */
data class IrcMessage(val tags: Map<String, String>, val prefix: String, val command: String, val params: List<String>) {
    val nick get() = prefix.substringBefore('!').substringBefore('@')
    companion object {
        fun parse(line: String): IrcMessage {
            require(line.length <= 8192 && line.none { it == '\r' || it == '\n' || it == '\u0000' })
            var rest = line
            val tags = linkedMapOf<String, String>()
            if (rest.startsWith('@')) {
                require(' ' in rest)
                rest.substringBefore(' ').drop(1).split(';').forEach {
                    val name = it.substringBefore('=')
                    require(name.isNotEmpty())
                    val value = if ('=' in it) it.substringAfter('=') else ""
                    val decoded = StringBuilder(); var escape = false
                    for (c in value) {
                        if (escape) { decoded.append(when (c) { ':' -> ';'; 's' -> ' '; 'r' -> '\r'; 'n' -> '\n'; else -> c }); escape = false }
                        else if (c == '\\') escape = true else decoded.append(c)
                    }
                    tags[name] = decoded.toString()
                }
                rest = rest.substringAfter(' ').trimStart(' ')
            }
            var prefix = ""
            if (rest.startsWith(':')) { require(' ' in rest); prefix = rest.substringBefore(' ').drop(1); rest = rest.substringAfter(' ').trimStart(' ') }
            val command = rest.substringBefore(' ').uppercase(Locale.ROOT)
            require(command.matches(Regex("[A-Z]+|[0-9]{3}")))
            rest = if (' ' in rest) rest.substringAfter(' ').trimStart(' ') else ""
            val params = mutableListOf<String>()
            while (rest.isNotEmpty()) {
                if (rest.startsWith(':')) { params.add(rest.drop(1)); break }
                params.add(rest.substringBefore(' ')); rest = if (' ' in rest) rest.substringAfter(' ').trimStart(' ') else ""
                require(params.size <= 15)
            }
            require(params.size <= 15)
            return IrcMessage(tags, prefix, command, params)
        }
    }
}

object IrcProtocol {
    fun line(command: String, params: List<String> = emptyList(), trailing: String? = null): String {
        require(command.matches(Regex("[A-Z]+")) && params.size + (if (trailing != null) 1 else 0) <= 15)
        params.forEach { require(validUnicode(it) && it.isNotEmpty() && !it.startsWith(':') && it.none { c -> c <= ' ' || c == '\u007f' }) }
        require(trailing == null || validUnicode(trailing) && trailing.none { it == '\r' || it == '\n' || it == '\u0000' })
        val result = command + params.joinToString("", transform = { " $it" }) + (trailing?.let { " :$it" } ?: "") + "\r\n"
        require(result.toByteArray(Charsets.UTF_8).size <= 512) { "IRC line exceeds 512 UTF-8 bytes; shorten the message." }
        return result
    }
    fun nick(value: String): Boolean = value.matches(Regex("[A-Za-z_\\[\\]\\\\`^{|}][A-Za-z0-9_\\[\\]\\\\`^{|}\\-]{0,29}"))
    fun validUnicode(value: String): Boolean {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) { if (i == value.length || !value[i++].isLowSurrogate()) return false }
            else if (c.isLowSurrogate()) return false
        }
        return true
    }
    fun target(value: String): Boolean = value.length in 1..64 && value.none { it <= ' ' || it in ",:\u007f" } && (value.startsWith('#') || value.startsWith('&') || nick(value))
    fun fold(value: String, mapping: String = "rfc1459"): String = value.lowercase(Locale.ROOT).map {
        if (mapping == "ascii") it else when (it) { '[' -> '{'; ']' -> '}'; '\\' -> '|'; '^' -> if (mapping == "strict-rfc1459") '^' else '~'; else -> it }
    }.joinToString("")
    fun display(value: String): String = value.replace(Regex("\u0003[0-9]{0,2}(,[0-9]{1,2})?"), "")
        .replace(Regex("\u001b\\[[0-?]*[ -/]*[@-~]"), "").filter { it >= ' ' && it != '\u007f' }
    fun base64(bytes: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val result = StringBuilder()
        for (i in bytes.indices step 3) {
            val a = bytes[i].toInt() and 255; val b = bytes.getOrNull(i + 1)?.toInt()?.and(255) ?: 0; val c = bytes.getOrNull(i + 2)?.toInt()?.and(255) ?: 0
            result.append(alphabet[a shr 2]).append(alphabet[((a and 3) shl 4) or (b shr 4)])
                .append(if (i + 1 < bytes.size) alphabet[((b and 15) shl 2) or (c shr 6)] else '=')
                .append(if (i + 2 < bytes.size) alphabet[c and 63] else '=')
        }
        return result.toString()
    }
}
