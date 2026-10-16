package ohi.andre.consolelauncher.managers.termux

import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** One local SSH connection; tmux owns the shells, while channels are disposable viewers. */
class TermuxWorkspaceSshConnection(
    private val info: TermuxWorkspaceSshSetup.ConnectionInfo,
    private val listener: Listener
) {
    interface Listener {
        fun onReady(windows: List<Pair<String, String>>)
        fun onWindows(windows: List<Pair<String, String>>)
        fun onTargetGone(target: String)
        fun onError(message: String)
    }

    @Volatile var connected = false
        private set
    private val running = AtomicBoolean(true)
    private val started = AtomicBoolean(false)
    private val refreshPending = AtomicBoolean(false)
    private val pendingOutput = AtomicInteger()
    private val pendingInput = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    private val commands = executor("retui-ssh-control", 32)
    private val writes = executor("retui-ssh-input", 64)
    private val attachments = executor("retui-ssh-attach", 8, 2)
    private val tiles = HashMap<Int, Tile>()
    private val instance = UUID.randomUUID().toString().replace("-", "")
    @Volatile private var session: Session? = null
    @Volatile private var control: ChannelExec? = null

    private class Tile(val target: String, val identity: String, var cols: Int, var rows: Int,
                       val output: (ByteArray) -> Unit, val closed: () -> Unit) {
        @Volatile var channel: ChannelExec? = null
        @Volatile var readyForInput = false
        @Volatile var opened = false
        @Volatile var opening: Future<*>? = null
    }

    fun connect() {
        check(started.compareAndSet(false, true)) { "Create a new connection to reconnect" }
        execute {
            require(info.port in 1..65535) { "Invalid Termux SSH port" }
            require(info.username.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Termux user" }
            val ssh = JSch()
            // This key is provisioned through RUN_COMMAND, never learned from the network.
            ssh.setKnownHosts(ByteArrayInputStream(knownHosts(info.port, info.hostKey).toByteArray(Charsets.UTF_8)))
            ssh.addIdentity(info.privateKey.absolutePath)
            val connection = ssh.getSession(info.username, "127.0.0.1", info.port)
            session = connection
            connection.setConfig("StrictHostKeyChecking", "yes")
            connection.setConfig("PreferredAuthentications", "publickey")
            connection.setConfig("server_host_key", "ecdsa-sha2-nistp256")
            connection.setConfig("PubkeyAcceptedAlgorithms", "ecdsa-sha2-nistp256")
            connection.setConfig("kex", "ecdh-sha2-nistp256")
            connection.setConfig("cipher.c2s", "aes128-ctr")
            connection.setConfig("cipher.s2c", "aes128-ctr")
            connection.setConfig("mac.c2s", "hmac-sha2-256")
            connection.setConfig("mac.s2c", "hmac-sha2-256")
            connection.serverAliveInterval = 15000
            connection.serverAliveCountMax = 2
            connection.setSocketFactory(object : SocketFactory {
                override fun createSocket(host: String, port: Int) = connectSocket(host, port, 5000) { running.get() }
                override fun getInputStream(socket: Socket) = socket.getInputStream()
                override fun getOutputStream(socket: Socket) = socket.getOutputStream()
            })
            connection.connect(8000)
            if (!running.get()) { connection.disconnect(); return@execute }
            connected = true
            val windows = listWindows()
            if (windows.isNotEmpty()) startControl()
            post { listener.onReady(windows) }
        }
    }

    fun attach(tile: Int, target: String, cols: Int, rows: Int,
               onOutput: (ByteArray) -> Unit, onClosed: () -> Unit) {
        require(tile in 0..1 && validTarget(target)) { "Invalid terminal tile" }
        detach(tile)
        val viewer = Tile(target, "${instance}_${tile}_${UUID.randomUUID().toString().replace("-", "")}", cleanCols(cols), cleanRows(rows), onOutput, onClosed)
        synchronized(tiles) { tiles[tile] = viewer }
        viewer.opening = executeOn(attachments) {
            if (!isCurrent(tile, viewer)) return@executeOn
            var opening: ChannelExec? = null
            try {
                val channel = open("attach ${viewer.identity} $target")
                opening = channel
                viewer.channel = channel
                channel.setPty(true)
                channel.setPtyType("xterm-256color", viewer.cols, viewer.rows, 0, 0)
                val input = channel.inputStream
                val errors = ByteArrayOutputStream()
                channel.setErrStream(object : java.io.OutputStream() {
                    override fun write(value: Int) { if (errors.size() < 4096) errors.write(value) }
                })
                channel.connect(8000)
                viewer.opened = true
                if (!isCurrent(tile, viewer)) { channel.disconnect(); return@executeOn }
                Thread({
                    try {
                        val buffer = ByteArray(16384)
                        while (isCurrent(tile, viewer)) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            viewer.readyForInput = true
                            val bytes = buffer.copyOf(count)
                            check(pendingOutput.addAndGet(count) <= MAX_PENDING_BYTES) { "Terminal output queue overflow" }
                            main.post {
                                pendingOutput.addAndGet(-count)
                                if (isCurrent(tile, viewer)) viewer.output(bytes)
                            }
                        }
                        if (isCurrent(tile, viewer)) {
                            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                            while (!channel.isClosed && running.get() && System.nanoTime() < deadline) Thread.sleep(5)
                            if (channel.exitStatus > 0 || !viewer.readyForInput) {
                                val detail = errors.toString("UTF-8").trim()
                                throw IllegalStateException(detail.ifBlank { "Termux terminal attachment closed before it was ready" })
                            }
                            synchronized(tiles) { if (tiles[tile] === viewer) tiles.remove(tile) }
                            post { viewer.closed() }
                            refreshWindows(target)
                        }
                    } catch (e: Exception) {
                        if (isCurrent(tile, viewer)) fail(e)
                    } finally { channel.disconnect() }
                }, "retui-ssh-terminal-$tile").apply { isDaemon = true }.start()
            } catch (error: Exception) {
                opening?.disconnect()
                // Changing windows can cancel an outstanding SSH channel-open request.
                // That stale viewer must not tear down the shared connection or another tile.
                if (isCurrent(tile, viewer)) throw error
            }
        }
        if (!isCurrent(tile, viewer)) { viewer.opening?.cancel(false); attachments.purge() }
    }

    fun write(tile: Int, data: ByteArray): Boolean {
        if (data.isEmpty()) return true
        val viewer = synchronized(tiles) { tiles[tile] } ?: return false
        if (!connected || !running.get() || !viewer.readyForInput || viewer.channel?.isConnected != true) return false
        if (data.size > MAX_PENDING_BYTES || pendingInput.addAndGet(data.size) > MAX_PENDING_BYTES) {
            fail(IllegalStateException("Terminal input queue overflow")); return false
        }
        val bytes = data.copyOf()
        return try {
            writes.execute {
                try {
                    if (isCurrent(tile, viewer)) {
                        val output = viewer.channel?.outputStream ?: return@execute
                        output.write(bytes)
                        output.flush()
                    }
                } catch (e: Exception) { if (isCurrent(tile, viewer)) fail(e) }
                finally { pendingInput.addAndGet(-bytes.size) }
            }
            true
        } catch (e: RejectedExecutionException) {
            pendingInput.addAndGet(-bytes.size)
            if (running.get()) fail(IllegalStateException("Terminal input queue overflow", e))
            false
        }
    }

    fun resize(tile: Int, cols: Int, rows: Int) {
        val viewer = synchronized(tiles) { tiles[tile] } ?: return
        viewer.cols = cleanCols(cols); viewer.rows = cleanRows(rows)
        execute { if (isCurrent(tile, viewer)) viewer.channel?.setPtySize(viewer.cols, viewer.rows, 0, 0) }
    }

    fun detach(tile: Int) {
        synchronized(tiles) { tiles.remove(tile) }?.let { viewer ->
            viewer.opening?.cancel(false)
            attachments.purge()
            // JSch encrypts close packets before touching the socket. A main-thread
            // socket exception is swallowed by JSch and would desynchronize this session.
            executeOn(writes) { if (viewer.opened) viewer.channel?.disconnect() }
        }
    }

    fun newWindow(name: String?, command: String?, onCreated: (String) -> Unit) {
        require((name?.length ?: 0) <= 256 && (command?.length ?: 0) <= 16384) { "Launcher command too large" }
        execute {
            val target = runCommand("new ${encode(name)} ${encode(command)}").trim()
            check(validTarget(target)) { "Termux did not return a window" }
            val windows = listWindows()
            startControl()
            post { listener.onWindows(windows); onCreated(target) }
        }
    }

    fun queryAlternate(target: String, onResult: (Boolean) -> Unit) {
        require(validTarget(target)) { "Invalid terminal target" }
        execute {
            val mode = runCommand("mode $target").trim()
            check(mode == "0" || mode == "1") { "Invalid Termux terminal mode" }
            post { onResult(mode == "1") }
        }
    }

    fun close() {
        if (running.getAndSet(false)) disconnect()
    }

    private fun startControl() {
        if (control?.isConnected == true || !running.get()) return
        val channel = open("control")
        val input = channel.inputStream
        control = channel
        channel.connect(8000)
        Thread({
            try {
                val reader = input.bufferedReader(Charsets.UTF_8)
                while (running.get() && control === channel) {
                    val line = StringBuilder()
                    var ended = false
                    while (true) {
                        val ch = reader.read()
                        if (ch == -1) { ended = true; break }
                        if (ch == 10) break
                        check(line.length < 65536) { "Termux control message too large" }
                        line.append(ch.toChar())
                    }
                    if (ended) break
                    val event = line.toString().substringBefore(" ")
                    if (event in WINDOW_EVENTS) refreshWindows()
                    if (event == "%exit") break
                }
                if (running.get() && control === channel) {
                    control = null
                    execute {
                        val windows = listWindows()
                        check(windows.isEmpty()) { "Termux workspace control channel closed" }
                        post { listener.onWindows(windows) }
                    }
                }
            } catch (e: Exception) { if (running.get() && control === channel) fail(e) }
            finally { closeControl(channel) }
        }, "retui-ssh-events").apply { isDaemon = true }.start()
    }

    private fun refreshWindows(maybeGone: String? = null) {
        if (!refreshPending.compareAndSet(false, true)) return
        execute {
            refreshPending.set(false)
            val windows = listWindows()
            if (windows.isNotEmpty() && control == null) startControl()
            post {
                listener.onWindows(windows)
                if (maybeGone != null && windows.none { it.first == maybeGone }) listener.onTargetGone(maybeGone)
            }
        }
    }

    private fun listWindows() = parseWindows(runCommand("list"))
    private fun open(command: String): ChannelExec {
        check(running.get()) { "SSH connection closed" }
        val connection = session ?: error("SSH connection unavailable")
        check(connection.isConnected) { "Termux SSH disconnected" }
        return (connection.openChannel("exec") as ChannelExec).apply { setCommand(command) }
    }

    private fun runCommand(command: String): String {
        val channel = open(command)
        try {
            val input = channel.inputStream
            val errors = ByteArrayOutputStream()
            channel.setErrStream(object : java.io.OutputStream() {
                override fun write(value: Int) { if (errors.size() < 4096) errors.write(value) }
            })
            channel.connect(8000)
            val output = readBounded(input, 262144, 8000) { channel.isEOF || channel.isClosed || !running.get() }
            // EOF can precede the SSH exit-status packet.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (!channel.isClosed && running.get() && System.nanoTime() < deadline) Thread.sleep(5)
            check(channel.exitStatus == 0) { errors.toString("UTF-8").trim().ifEmpty { "Termux command failed" } }
            return output.toString(Charsets.UTF_8)
        } finally { channel.disconnect() }
    }

    private fun execute(action: () -> Unit) { executeOn(commands, action) }
    private fun executeOn(queue: ThreadPoolExecutor, action: () -> Unit): Future<*>? {
        if (!running.get()) return null
        return try { queue.submit { try { if (running.get()) action() } catch (e: Exception) { fail(e) } } }
        catch (e: RejectedExecutionException) { if (running.get()) fail(e); null }
    }
    private fun post(action: () -> Unit) { main.post { if (running.get()) action() } }
    private fun isCurrent(tile: Int, viewer: Tile) = running.get() && synchronized(tiles) { tiles[tile] === viewer }
    private fun fail(error: Exception) {
        if (!running.compareAndSet(true, false)) return
        disconnect()
        val message = if (error.message?.contains("HostKey", true) == true || error.message?.contains("host key", true) == true)
            "Termux identity changed. Repair connection to pair again." else error.message ?: "Termux SSH disconnected"
        main.post { listener.onError(message) }
    }
    private fun disconnect() {
        connected = false
        val detached = synchronized(tiles) { tiles.values.toList().also { tiles.clear() } }
        val oldControl = control; control = null
        val oldSession = session; session = null
        detached.forEach { it.opening?.cancel(false) }
        commands.shutdownNow(); writes.shutdownNow(); attachments.shutdownNow()
        // Activity pause/repair/close run on Android's main thread. Every JSch close
        // may send a packet, so disposal must also remain off that thread.
        Thread({
            detached.forEach { it.channel?.disconnect() }
            oldControl?.let { closeControl(it) }
            oldSession?.disconnect()
        }, "retui-ssh-close").apply { isDaemon = true }.start()
    }

    private fun closeControl(channel: ChannelExec) {
        try {
            // Closing an SSH channel without stdin EOF can orphan tmux's non-PTY control client.
            channel.outputStream.close()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (channel.isConnected && !channel.isClosed && System.nanoTime() < deadline) Thread.sleep(5)
        } catch (_: Exception) {
            // A broken connection must still release its channels and session.
        } finally { channel.disconnect() }
    }

    companion object {
        private const val MAX_PENDING_BYTES = 1024 * 1024
        private val WINDOW_EVENTS = setOf("%window-add", "%window-close", "%window-renamed", "%unlinked-window-add",
            "%unlinked-window-close", "%unlinked-window-renamed", "%session-window-changed", "%sessions-changed")
        internal fun connectSocket(host: String, port: Int, timeoutMillis: Long, running: () -> Boolean): Socket {
            require(host == "127.0.0.1" && port in 1..65535 && timeoutMillis > 0) { "Invalid Termux SSH endpoint" }
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var lastError: Exception? = null
            while (running() && System.nanoTime() < deadline) {
                val socket = Socket()
                try {
                    val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)
                    socket.connect(InetSocketAddress(host, port), minOf(200L, remaining).toInt())
                    check(running()) { "SSH connection closed" }
                    // JSch authenticates this socket; a separate readiness probe incurs OpenSSH noauth penalties.
                    return socket
                } catch (error: ConnectException) { lastError = error }
                catch (error: SocketTimeoutException) { lastError = error }
                catch (error: Exception) { socket.close(); throw error }
                socket.close()
                val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remaining > 0) Thread.sleep(minOf(100L, remaining))
            }
            check(running()) { "SSH connection closed" }
            throw IllegalStateException("Termux SSH did not start. Retry or open Termux.", lastError)
        }
        internal fun validTarget(target: String) = target.matches(Regex("@[0-9]+"))
        internal fun knownHosts(port: Int, key: String): String {
            require(port in 1..65535)
            val parts = key.trim().split(Regex("\\s+"))
            require(parts.size == 2 && parts[0] == "ecdsa-sha2-nistp256" &&
                parts[1].matches(Regex("[A-Za-z0-9+/]+={0,2}"))) { "Invalid pinned Termux identity" }
            return "[127.0.0.1]:$port ${parts[0]} ${parts[1]}\n"
        }
        internal fun parseWindows(value: String): List<Pair<String, String>> {
            val lines = value.lineSequence().filter { it.isNotBlank() }.toList()
            require(lines.size <= 1024) { "Too many Termux windows" }
            val windows = lines.map {
                val parts = it.split(':', limit = 3)
                require(parts.size == 3 && validTarget(parts[0]) && parts[1].toIntOrNull()?.let { n -> n >= 0 } == true) {
                    "Invalid Termux window list"
                }
                parts[0] to "${parts[1]}:${parts[2]}"
            }
            require(windows.map { it.first }.distinct().size == windows.size) { "Duplicate Termux windows" }
            return windows
        }
        private fun executor(name: String, capacity: Int, workers: Int = 1) = ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(capacity), { task -> Thread(task, name).apply { isDaemon = true } })
        private fun cleanCols(value: Int) = value.coerceIn(10, 240)
        private fun cleanRows(value: Int) = value.coerceIn(4, 120)
        private fun encode(value: String?) = if (value.isNullOrEmpty()) "-" else Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        internal fun readBounded(input: InputStream, limit: Int, timeoutMillis: Long, ended: () -> Boolean): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            while (true) {
                val available = input.available()
                if (available == 0) {
                    if (ended()) break
                    check(System.nanoTime() < deadline) { "Termux command timed out" }
                    Thread.sleep(5)
                    continue
                }
                check(System.nanoTime() < deadline) { "Termux command timed out" }
                val count = input.read(buffer, 0, minOf(buffer.size, available))
                if (count < 0) break
                check(output.size() + count <= limit) { "Termux response too large" }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}
