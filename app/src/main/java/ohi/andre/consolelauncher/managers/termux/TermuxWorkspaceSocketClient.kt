package ohi.andre.consolelauncher.managers.termux

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class TermuxWorkspaceSocketClient(private val listener: Listener) {
    interface Listener {
        fun onFrame(frame: String)
        fun onStatus(status: String)
        fun onError(message: String)
        fun onClosed()
        fun onTargetGone(target: String?) {}
    }

    @Volatile var connected: Boolean = false
        private set
    @Volatile private var running = false
    private var started = false
    private var targeted = false
    private val writeLock = Any()
    private var localSocket: LocalSocket? = null
    private var tcpSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private val writerExecutor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(16), { task -> Thread(task, "retui-workspace-writer").apply { isDaemon = true } })

    fun connect(socketName: String, token: String, cols: Int, rows: Int,
                tcpHost: String?, tcpPort: Int?, stream: Boolean,
                target: String? = null, targeted: Boolean = false) {
        check(!started) { "Create a new client to reconnect" }
        started = true
        this.targeted = targeted
        require(target == null || target.matches(Regex("@[0-9]+"))) { "Invalid window target" }
        require(socketName == SOCKET_NAME) { "Unexpected bridge socket" }
        require(token.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid bridge token" }
        require(tcpHost == null || tcpHost == "127.0.0.1") { "Bridge must be loopback" }
        require(tcpPort == null || tcpPort in 1..65535) { "Invalid bridge port" }
        running = true
        Thread({
            try {
                val local = connectLocal(socketName)
                val tcp = if (local == null && tcpPort != null && tcpHost == "127.0.0.1") connectTcp(tcpPort) else null
                if (!running) return@Thread
                val input = local?.inputStream ?: tcp?.getInputStream() ?: error("socket unavailable")
                val output = local?.outputStream ?: tcp!!.getOutputStream()
                val out = BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8))
                synchronized(writeLock) {
                    if (!running) return@Thread
                    writer = out
                }
                out.write("HELLO $token ${cleanCols(cols)} ${cleanRows(rows)} ${if (stream) "STREAM" else "CAPTURE"} ${target?.let { encode(it) } ?: "-"}${if (targeted) " NO_CREATE" else ""}\n")
                out.flush()
                readFrames(BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)))
            } catch (e: Exception) {
                if (running) listener.onError(e.message ?: e.javaClass.simpleName)
            } finally {
                val notify = running
                close()
                if (notify) listener.onClosed()
            }
        }, "retui-workspace-reader").apply { isDaemon = true }.start()
    }

    fun capture(cols: Int, rows: Int) = sendRaw("CAPTURE ${cleanCols(cols)} ${cleanRows(rows)}")
    fun sendInput(input: String, cols: Int, rows: Int): Boolean =
        sendRaw(if (input.isEmpty()) "ENTER ${cleanCols(cols)} ${cleanRows(rows)}"
        else "SEND ${cleanCols(cols)} ${cleanRows(rows)} ${encode(input)}")
    fun typeInput(input: String, cols: Int, rows: Int): Boolean =
        input.isEmpty() || sendRaw("TYPE ${cleanCols(cols)} ${cleanRows(rows)} ${encode(input)}")
    fun newWindow(name: String?, command: String?, cols: Int, rows: Int) =
        sendRaw("NEW ${cleanCols(cols)} ${cleanRows(rows)} ${if (name.isNullOrBlank()) "-" else encode(name)} ${if (command.isNullOrBlank()) "-" else encode(command)}")
    fun switchWindow(direction: String, cols: Int, rows: Int) =
        sendRaw("SWITCH ${cleanCols(cols)} ${cleanRows(rows)} ${if (direction in listOf("prev", "previous", "left")) "prev" else "next"}")
    fun key(key: String, cols: Int, rows: Int): Boolean =
        key.isNotEmpty() && key.length <= 64 && key.all { it.code in 33..126 } &&
            sendRaw("KEY ${cleanCols(cols)} ${cleanRows(rows)} $key")
    fun selectWindow(target: String, cols: Int, rows: Int): Boolean =
        target.isNotEmpty() && sendRaw("SELECT ${cleanCols(cols)} ${cleanRows(rows)} ${encode(target)}")

    fun close() {
        running = false
        connected = false
        // Close the transport before taking the write lock: flush may be blocked.
        try { localSocket?.close() } catch (ignored: Exception) {}
        try { tcpSocket?.close() } catch (ignored: Exception) {}
        writerExecutor.shutdownNow()
        synchronized(writeLock) { writer = null }
    }

    private fun connectLocal(name: String): LocalSocket? {
        for (attempt in 0 until 6) {
            if (!running) return null
            val candidate = LocalSocket()
            localSocket = candidate
            try {
                candidate.soTimeout = 3000
                candidate.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                return candidate
            } catch (e: Exception) {
                try { candidate.close() } catch (ignored: Exception) {}
                if (localSocket === candidate) localSocket = null
                if (running) Thread.sleep(120)
            }
        }
        return null
    }

    private fun connectTcp(port: Int): Socket {
        for (attempt in 0 until 6) {
            if (!running) error("connection cancelled")
            val candidate = Socket()
            tcpSocket = candidate
            try {
                candidate.soTimeout = 3000
                candidate.connect(InetSocketAddress("127.0.0.1", port), 350)
                return candidate
            } catch (e: Exception) {
                try { candidate.close() } catch (ignored: Exception) {}
                if (attempt == 5) throw e
                if (running) Thread.sleep(120)
            }
        }
        error("socket unavailable")
    }

    private fun readFrames(reader: BufferedReader) {
        val frame = StringBuilder()
        var inBody = false
        var protocol = false
        var authenticated = false
        var session = false
        while (running) {
            val line = readBoundedLine(reader) ?: break
            if (!inBody) {
                when {
                    line.startsWith("__RETUI_SOCKET__ ") -> protocol = line.substringAfter(' ') == "retui-bridge-socket-v4" ||
                        (!targeted && line.substringAfter(' ') == "retui-bridge-socket-v3")
                    line == "__RETUI_SOCKET_STATUS__ ok" -> authenticated = true
                    line == "__RETUI_SESSION__ retui_workspace" -> session = true
                    line.startsWith("__RETUI_SOCKET_ERROR__ ") -> error(line.substringAfter(' '))
                    line.startsWith("__RETUI_TARGET_GONE__ ") -> {
                        check(protocol && authenticated && session) { "Invalid bridge handshake" }
                        val target = line.substringAfter(' ')
                        check(target == "-" || target.matches(Regex("@[0-9]+"))) { "Invalid missing target" }
                        if (!connected) {
                            connected = true
                            localSocket?.soTimeout = 0; tcpSocket?.soTimeout = 0
                        }
                        listener.onTargetGone(target.takeIf { it != "-" })
                        frame.setLength(0)
                        continue
                    }
                    line.startsWith("__RETUI_FRAME_BASE64__ ") -> {
                        check(protocol && authenticated && session) { "Invalid bridge handshake" }
                        Base64.decode(line.substringAfter(' '), Base64.DEFAULT)
                        inBody = true
                    }
                }
            }
            if (frame.isNotEmpty()) frame.append('\n')
            frame.append(line)
            check(frame.length <= MAX_FRAME_CHARS) { "Bridge frame too large" }
            if (inBody && line == "__RETUI_FRAME_END__") {
                if (!connected) {
                    connected = true
                    localSocket?.soTimeout = 0
                    tcpSocket?.soTimeout = 0
                    listener.onStatus("ok")
                }
                listener.onFrame(frame.toString())
                frame.setLength(0)
                inBody = false
            }
        }
    }

    private fun readBoundedLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (running) {
            val ch = reader.read()
            if (ch == -1) return if (line.isEmpty()) null else line.toString()
            if (ch == '\n'.code) return line.toString()
            check(line.length < MAX_FRAME_CHARS) { "Bridge line too large" }
            line.append(ch.toChar())
        }
        return null
    }

    private fun sendRaw(line: String): Boolean {
        if (!connected || !running || line.length > MAX_INPUT_CHARS) return false
        return try {
            writerExecutor.execute {
                try {
                    synchronized(writeLock) {
                        if (!connected || !running) return@execute
                        val out = writer ?: return@execute
                        out.write(line)
                        out.newLine()
                        out.flush()
                    }
                } catch (e: Exception) {
                    val notify = running
                    close()
                    if (notify) {
                        listener.onError(e.message ?: "write failed")
                        listener.onClosed()
                    }
                }
            }
            true
        } catch (e: RejectedExecutionException) { false }
    }

    private fun encode(value: String) = Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
    private fun cleanCols(value: Int) = value.coerceIn(20, 240)
    private fun cleanRows(value: Int) = value.coerceIn(8, 120)

    companion object {
        const val SOCKET_NAME = "retui_bridge_com_dvil_tui_renewed_v2"
        private const val MAX_FRAME_CHARS = 262144
        private const val MAX_INPUT_CHARS = 65536
    }
}
