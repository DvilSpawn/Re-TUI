package ohi.andre.consolelauncher.help

internal object HelpMarkup {
    data class Block(val kind: String, val text: String)
    fun parse(source: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var fenced = false
        fun flush() {
            if (paragraph.isNotEmpty()) { blocks.add(Block("paragraph", paragraph.joinToString("\n"))); paragraph.clear() }
        }
        source.lines().forEach { line ->
            when {
                line.trim().startsWith("```") -> {
                    flush()
                    if (fenced) { blocks.add(Block("code", code.joinToString("\n"))); code.clear() }
                    fenced = !fenced
                }
                fenced -> code.add(line)
                line.isBlank() -> flush()
                line.startsWith("## ") -> { flush(); blocks.add(Block("heading", line.drop(3))) }
                line.startsWith(">! ") -> { flush(); blocks.add(Block("warning", line.drop(3))) }
                line.startsWith("- ") -> { flush(); blocks.add(Block("bullet", line.drop(2))) }
                Regex("^\\d+\\. .*?").matches(line) -> { flush(); blocks.add(Block("number", line)) }
                else -> paragraph.add(line)
            }
        }
        flush()
        if (fenced) blocks.add(Block("code", code.joinToString("\n")))
        return blocks
    }
}
