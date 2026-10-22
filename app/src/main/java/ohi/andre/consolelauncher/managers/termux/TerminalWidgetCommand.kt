package ohi.andre.consolelauncher.managers.termux

/** Only upgrade our bundled inline script; custom commands stay exactly as saved. */
object TerminalWidgetCommand {
    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    fun create(source: String, mode: String, location: String) =
        "bash -c ${quote(source)} retui-widget ${quote(mode)} ${quote(location)}"

    fun refreshed(command: String, source: String): String? {
        if (!command.startsWith("bash -c '#!/data/data/com.termux/files/usr/bin/bash\n# Bundled terminal widget.")) return null
        val separator = "' retui-widget "
        val end = command.lastIndexOf(separator)
        if (end < 0) return null
        val fresh = "bash -c ${quote(source)} retui-widget ${command.substring(end + separator.length)}"
        return fresh.takeIf { it != command }
    }
}
