package ohi.andre.consolelauncher.managers.termux

import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput

/** Projects authoritative tmux cells; parser state and terminal replies remain in tmux. */
class TermuxWorkspaceTerminal(frame: TermuxWorkspaceFrame, grid: TerminalGridView) {
    val emulator = TerminalEmulator(object : TerminalOutput() {
        override fun write(data: ByteArray, offset: Int, count: Int) {}
        override fun titleChanged(oldTitle: String?, newTitle: String?) {}
        override fun onCopyTextToClipboard(text: String?) {}
        override fun onPasteTextFromClipboard() {}
        override fun onBell() {}
        override fun onColorsChanged() {}
    }, frame.cols, frame.rows, grid.characterWidth().toInt().coerceAtLeast(1), grid.lineHeight(), 1000, null)

    init {
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
    fun text(): String = emulator.getSelectedText(0, 0, emulator.mColumns - 1, emulator.mRows - 1)
}
