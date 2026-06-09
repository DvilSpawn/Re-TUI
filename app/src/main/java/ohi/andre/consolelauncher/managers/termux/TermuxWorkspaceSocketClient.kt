package ohi.andre.consolelauncher.managers.termux

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Base64
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.max

class TermuxWorkspaceSocketClient(private val listener: Listener) {
    interface Listener {
        fun onFrame(frame: String)
        fun onStatus(status: String)
        fun onError(message: String)
        fun onClosed()
    }

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    private var running = false

    private val writeLock = Any()
    private var localSocket: LocalSocket? = null
    private var tcpSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private var readerThread: Thread? = null
    private val writerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "retui-workspace-socket-writer").apply { isDaemon = true }
    }

    fun connect(
        socketName: String,
        token: String,
        cols: Int,
        rows: Int,
        tcpHost: String?,
        tcpPort: Int?,
        stream: Boolean
    ) {
        close(false)
        running = true
        readerThread = Thread({
            var localSocket: LocalSocket? = null
            var tcpSocket: Socket? = null
            try {
                localSocket = connectLocal(socketName)
                if (localSocket == null && !tcpHost.isNullOrBlank() && tcpPort != null && tcpPort > 0) {
                    tcpSocket = connectTcp(tcpHost, tcpPort)
                }
                if (localSocket == null && tcpSocket == null) {
                    throw IllegalStateException("socket unavailable")
                }
                val inputStream = localSocket?.inputStream ?: tcpSocket!!.getInputStream()
                val outputStream = localSocket?.outputStream ?: tcpSocket!!.getOutputStream()
                val localWriter = BufferedWriter(
                    OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
                )
                val localReader = BufferedReader(
                    InputStreamReader(inputStream, StandardCharsets.UTF_8)
                )

                synchronized(writeLock) {
                    this.localSocket = localSocket
                    this.tcpSocket = tcpSocket
                    writer = localWriter
                    connected = true
                }

                val mode = if (stream) "STREAM" else "CAPTURE"
                sendRawBlocking("HELLO $token ${cleanCols(cols)} ${cleanRows(rows)} $mode")
                readFrames(localReader)
            } catch (e: Exception) {
                if (running) {
                    listener.onError(e.message ?: e.javaClass.simpleName)
                }
            } finally {
                synchronized(writeLock) {
                    connected = false
                    writer = null
                    this.localSocket = null
                    this.tcpSocket = null
                }
                try {
                    localSocket?.close()
                } catch (ignored: Exception) {
                }
                try {
                    tcpSocket?.close()
                } catch (ignored: Exception) {
                }
                if (running) {
                    listener.onClosed()
                }
                writerExecutor.shutdownNow()
            }
        }, "retui-workspace-socket")
        readerThread?.isDaemon = true
        readerThread?.start()
    }

    fun capture(cols: Int, rows: Int): Boolean =
        sendRaw("CAPTURE ${cleanCols(cols)} ${cleanRows(rows)}")

    fun sendInput(input: String, cols: Int, rows: Int): Boolean {
        if (input.isEmpty()) {
            return sendRaw("ENTER ${cleanCols(cols)} ${cleanRows(rows)}")
        }
        return sendRaw("SEND ${cleanCols(cols)} ${cleanRows(rows)} ${encode(input)}")
    }

    fun typeInput(input: String, cols: Int, rows: Int): Boolean {
        if (input.isEmpty()) {
            return true
        }
        return sendRaw("TYPE ${cleanCols(cols)} ${cleanRows(rows)} ${encode(input)}")
    }

    fun newWindow(name: String?, cols: Int, rows: Int): Boolean {
        val payload = if (name.isNullOrBlank()) "-" else encode(name)
        return sendRaw("NEW ${cleanCols(cols)} ${cleanRows(rows)} $payload")
    }

    fun switchWindow(direction: String, cols: Int, rows: Int): Boolean {
        val clean = when (direction.lowercase()) {
            "prev", "previous", "left" -> "prev"
            else -> "next"
        }
        return sendRaw("SWITCH ${cleanCols(cols)} ${cleanRows(rows)} $clean")
    }

    fun key(key: String, cols: Int, rows: Int): Boolean =
        sendRaw("KEY ${cleanCols(cols)} ${cleanRows(rows)} $key")

    fun close() {
        close(true)
    }

    private fun close(shutdownWriter: Boolean) {
        running = false
        val writerToClose: BufferedWriter?
        val localSocketToClose: LocalSocket?
        val tcpSocketToClose: Socket?
        synchronized(writeLock) {
            writerToClose = writer
            localSocketToClose = localSocket
            tcpSocketToClose = tcpSocket
            connected = false
            writer = null
            localSocket = null
            tcpSocket = null
        }
        Thread({
            try {
                writerToClose?.write("BYE")
                writerToClose?.newLine()
                writerToClose?.flush()
            } catch (ignored: Exception) {
            }
            try {
                localSocketToClose?.close()
            } catch (ignored: Exception) {
            }
            try {
                tcpSocketToClose?.close()
            } catch (ignored: Exception) {
            }
            if (shutdownWriter) {
                writerExecutor.shutdownNow()
            }
        }, "retui-workspace-socket-close").apply { isDaemon = true }.start()
    }

    private fun connectLocal(socketName: String): LocalSocket? {
        var lastError: Exception? = null
        for (attempt in 0 until CONNECT_ATTEMPTS) {
            var candidate: LocalSocket? = null
            try {
                candidate = LocalSocket()
                candidate.connect(
                    LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT)
                )
                return candidate
            } catch (e: Exception) {
                lastError = e
                try {
                    candidate?.close()
                } catch (ignored: Exception) {
                }
                if (running) {
                    Thread.sleep(CONNECT_RETRY_DELAY_MS)
                }
            }
        }
        listener.onStatus("local socket unavailable: " + (lastError?.message ?: "unavailable"))
        return null
    }

    private fun connectTcp(host: String, port: Int): Socket {
        var lastError: Exception? = null
        for (attempt in 0 until CONNECT_ATTEMPTS) {
            var candidate: Socket? = null
            try {
                candidate = Socket()
                candidate.connect(InetSocketAddress(host, port), TCP_CONNECT_TIMEOUT_MS)
                return candidate
            } catch (e: Exception) {
                lastError = e
                try {
                    candidate?.close()
                } catch (ignored: Exception) {
                }
                if (running) {
                    Thread.sleep(CONNECT_RETRY_DELAY_MS)
                }
            }
        }
        throw lastError ?: IllegalStateException("tcp socket unavailable")
    }

    private fun readFrames(reader: BufferedReader) {
        val frame = StringBuilder()
        var hasFrame = false
        while (running) {
            val line = reader.readLine() ?: break
            if (line.startsWith("__RETUI_SOCKET_STATUS__")) {
                listener.onStatus(line.substring("__RETUI_SOCKET_STATUS__".length).trim { it <= ' ' })
            } else if (line.startsWith("__RETUI_SOCKET_ERROR__")) {
                listener.onError(line.substring("__RETUI_SOCKET_ERROR__".length).trim { it <= ' ' })
                frame.setLength(0)
                hasFrame = false
                continue
            }

            if (frame.isNotEmpty()) {
                frame.append('\n')
            }
            frame.append(line)
            if (line.startsWith("__RETUI_FRAME_BEGIN__")) {
                hasFrame = true
            } else if (line.startsWith("__RETUI_FRAME_END__")) {
                listener.onFrame(frame.toString())
                frame.setLength(0)
                hasFrame = false
            } else if (!hasFrame && frame.length > MAX_PENDING_FRAME_CHARS) {
                frame.setLength(0)
            }
        }
    }

    private fun sendRaw(line: String): Boolean {
        val executor = synchronized(writeLock) {
            if (writer == null || writerExecutor.isShutdown) {
                return false
            }
            writerExecutor
        }
        try {
            executor.execute {
                synchronized(writeLock) {
                    val out = writer ?: return@synchronized
                    try {
                        out.write(line)
                        out.newLine()
                        out.flush()
                    } catch (e: Exception) {
                        listener.onError(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            return false
        }
        return true
    }

    private fun sendRawBlocking(line: String): Boolean {
        synchronized(writeLock) {
            val out = writer ?: return false
            try {
                out.write(line)
                out.newLine()
                out.flush()
            } catch (e: Exception) {
                listener.onError(e.message ?: e.javaClass.simpleName)
                return false
            }
            return true
        }
    }

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)

    private fun cleanCols(cols: Int): Int = max(20, cols)

    private fun cleanRows(rows: Int): Int = max(8, rows)

    companion object {
        private const val MAX_PENDING_FRAME_CHARS = 4096
        private const val CONNECT_ATTEMPTS = 6
        private const val CONNECT_RETRY_DELAY_MS = 120L
        private const val TCP_CONNECT_TIMEOUT_MS = 350
    }
}
