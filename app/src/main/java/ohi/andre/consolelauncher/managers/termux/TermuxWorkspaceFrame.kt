package ohi.andre.consolelauncher.managers.termux

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Metadata ends at the encoded body; terminal output is never parsed as commands. */
data class TermuxWorkspaceFrame(
    val target: String?, val window: String?, val windows: List<Pair<String, String>>,
    val body: String?, val cols: Int, val rows: Int, val cursorX: Int?, val cursorY: Int?,
    val protocol: String?, val socket: String?, val token: String?, val host: String?, val port: Int?,
    val helperVersion: String? = null
) {
    companion object {
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(raw: String): TermuxWorkspaceFrame {
            require(raw.length <= 262144) { "Bridge frame too large" }
            val metadata = mutableMapOf<String, String>()
            var body: String? = null
            var plainBody: StringBuilder? = null
            for (line in raw.lineSequence()) {
                if (plainBody != null) {
                    if (line == "__RETUI_FRAME_END__") break
                    if (plainBody.isNotEmpty()) plainBody.append('\n')
                    plainBody.append(line)
                    continue
                }
                if (line == "__RETUI_FRAME_BEGIN__") { plainBody = StringBuilder(); continue }
                if (line.startsWith("__RETUI_FRAME_BASE64__ ")) {
                    body = Base64.decode(line.substringAfter(' ')).toString(Charsets.UTF_8)
                    break
                }
                if (line.startsWith("__RETUI_")) metadata[line.substringBefore(' ')] = line.substringAfter(' ', "").trim()
            }
            if (plainBody != null) body = plainBody.toString()
            fun value(key: String) = metadata["__RETUI_${key}__"]
            val cursor = value("CURSOR")?.split(':')
            return TermuxWorkspaceFrame(value("TARGET")?.takeIf { it.matches(Regex("@[0-9]+")) }, value("WINDOW"),
                value("WINDOWS").orEmpty().split('\t').mapNotNull {
                    val id = it.substringBefore(':')
                    if (id.matches(Regex("@[0-9]+"))) id to it.substringAfter(':') else null
                }, body, value("COLS")?.toIntOrNull()?.coerceIn(20, 240) ?: 80,
                value("ROWS")?.toIntOrNull()?.coerceIn(8, 120) ?: 24,
                cursor?.getOrNull(0)?.toIntOrNull(), cursor?.getOrNull(1)?.toIntOrNull(),
                value("SOCKET"), value("SOCKET_NAME"), value("TOKEN"), value("TCP_HOST"), value("TCP_PORT")?.toIntOrNull(), value("VERSION"))
        }
    }
}
