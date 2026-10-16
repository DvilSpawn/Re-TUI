package ohi.andre.consolelauncher.managers.termux

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput

/** Keeps parser state across SSH chunks; the legacy path can restore projected tmux cells. */
class TermuxWorkspaceTerminal(cols: Int, rows: Int, grid: TerminalGridView, private val reply: (ByteArray) -> Unit) {
    private var pasteBuffer: java.io.ByteArrayOutputStream? = null
    constructor(frame: TermuxWorkspaceFrame, grid: TerminalGridView) : this(frame.cols, frame.rows, grid, {}) { restore(frame) }
    val emulator = TerminalEmulator(object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {
            val paste = pasteBuffer
            if (paste != null) paste.write(data, offset, count)
            else reply(data.copyOfRange(offset, offset + count))
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }, cols, rows, grid.characterWidth().toInt().coerceAtLeast(1), grid.lineHeight(), 1000, null)

    private fun restore(frame: TermuxWorkspaceFrame) {
        val state = requireNotNull(frame.terminalState)
        fun paint(body: String?) {
            append("\u001b[2J\u001b[H")
            body.orEmpty().split('\n').take(frame.rows).forEachIndexed { row, text ->
                append("\u001b[${row + 1};1H\u001b[0m$text")
            }
        }
        if (state[4] != 0) {
            paint(frame.normalBody)
            append("\u001b[${state[6] + 1};${state[5] + 1}H\u001b[?1049h")
        }
        paint(frame.body)
        append(if (state[7] != 0) "\u001b[?25h" else "\u001b[?25l")
        append(if (state[8] != 0) "\u001b[?7h" else "\u001b[?7l")
        append(if (state[9] != 0) "\u001b[4h" else "\u001b[4l")
        append(if (state[12] != 0) "\u001b[?1h" else "\u001b[?1l")
        append("\u001b[${state[10] + 1};${state[11] + 1}r\u001b[${state[3] + 1};${state[2] + 1}H")
    }
    private fun append(text: String) = append(text.toByteArray(Charsets.UTF_8))
    fun append(bytes: ByteArray) { emulator.append(bytes, bytes.size) }
    fun paste(text: String) {
        val bytes = java.io.ByteArrayOutputStream()
        pasteBuffer = bytes
        try { emulator.paste(text) } finally { pasteBuffer = null }
        // One queued write keeps the paste delimiters together when input is rejected.
        reply(bytes.toByteArray())
    }
    fun resize(cols: Int, rows: Int, grid: TerminalGridView) = emulator.resize(cols, rows,
        grid.characterWidth().toInt().coerceAtLeast(1), grid.lineHeight())
    fun key(token: String): ByteArray {
        var name = token
        var modifiers = 0
        while (name.length > 2 && name[1] == '-' && name[0] in "CMS") {
            modifiers = modifiers or when (name[0]) {
                'C' -> com.termux.terminal.KeyHandler.KEYMOD_CTRL
                'M' -> com.termux.terminal.KeyHandler.KEYMOD_ALT
                else -> com.termux.terminal.KeyHandler.KEYMOD_SHIFT
            }
            name = name.drop(2)
        }
        val code = when (name) {
            "Up" -> android.view.KeyEvent.KEYCODE_DPAD_UP
            "Down" -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
            "Left" -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
            "Right" -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            "Home" -> android.view.KeyEvent.KEYCODE_MOVE_HOME
            "End" -> android.view.KeyEvent.KEYCODE_MOVE_END
            "PageUp" -> android.view.KeyEvent.KEYCODE_PAGE_UP
            "PageDown" -> android.view.KeyEvent.KEYCODE_PAGE_DOWN
            "Insert" -> android.view.KeyEvent.KEYCODE_INSERT
            "Delete" -> android.view.KeyEvent.KEYCODE_FORWARD_DEL
            "Enter" -> android.view.KeyEvent.KEYCODE_ENTER
            "Escape" -> android.view.KeyEvent.KEYCODE_ESCAPE
            "BSpace" -> android.view.KeyEvent.KEYCODE_DEL
            "Tab", "BTab" -> android.view.KeyEvent.KEYCODE_TAB
            else -> if (name.matches(Regex("F([1-9]|1[0-2])"))) android.view.KeyEvent.KEYCODE_F1 + name.drop(1).toInt() - 1 else null
        }
        if (name == "BTab") modifiers = modifiers or com.termux.terminal.KeyHandler.KEYMOD_SHIFT
        if (code != null) return com.termux.terminal.KeyHandler.getCode(code, modifiers,
            emulator.isCursorKeysApplicationMode, emulator.isKeypadApplicationMode).orEmpty().toByteArray(Charsets.UTF_8)
        var text = if (name == "Space") " " else name
        if (text.length != 1) return byteArrayOf()
        var character = text[0]
        if (modifiers and com.termux.terminal.KeyHandler.KEYMOD_SHIFT != 0) character = character.uppercaseChar()
        if (modifiers and com.termux.terminal.KeyHandler.KEYMOD_CTRL != 0) character = when (character) {
            ' ', '@', '2' -> 0.toChar()
            '[', '3' -> 27.toChar()
            '\\', '4' -> 28.toChar()
            ']', '5' -> 29.toChar()
            '^', '6' -> 30.toChar()
            '_', '7', '/', '-' -> 31.toChar()
            '?', '8' -> 127.toChar()
            in 'a'..'z' -> (character.code - 96).toChar()
            in 'A'..'_' -> (character.code - 64).toChar()
            else -> character
        }
        text = character.toString()
        if (modifiers and com.termux.terminal.KeyHandler.KEYMOD_ALT != 0) text = "\u001b$text"
        return text.toByteArray(Charsets.UTF_8)
    }
    fun text(): String = emulator.getSelectedText(0, 0, emulator.mColumns - 1, emulator.mRows - 1)
}
