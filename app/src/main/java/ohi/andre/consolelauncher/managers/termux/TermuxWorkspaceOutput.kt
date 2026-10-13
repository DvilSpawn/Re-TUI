package ohi.andre.consolelauncher.managers.termux

/** Decodes tmux control output from a byte-preserving ISO-8859-1 reader. */
object TermuxWorkspaceOutput {
    fun decode(value: String): ByteArray {
        require(value.length <= 262144) { "Output line too large" }
        val bytes = ByteArray(value.length.coerceAtMost(65536))
        var offset = 0
        var count = 0
        while (offset < value.length) {
            val byte = if (value[offset] == '\\') {
                require(offset + 3 < value.length) { "Incomplete output escape" }
                var decoded = 0
                for (index in 1..3) {
                    val digit = value[offset + index]
                    require(digit in '0'..'7') { "Invalid output escape" }
                    decoded = decoded * 8 + (digit - '0')
                }
                offset += 4
                decoded
            } else value[offset++].code
            require(byte <= 255 && count < bytes.size) { "Output too large or invalid byte" }
            bytes[count++] = byte.toByte()
        }
        return bytes.copyOf(count)
    }
}
