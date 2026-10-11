package ohi.andre.consolelauncher.irc

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.SSLPeerUnverifiedException
import okhttp3.OkHttpClient
import kotlin.concurrent.thread

/** One network connection; no Android, HTTP bridge, global dispatcher or message retry. */
class IrcClient(private val host: String, private val port: Int, private val nickname: String,
    private val account: String = "", password: CharArray = charArrayOf(),
    private val receive: (IrcMessage) -> Unit, private val connection: (Boolean, String) -> Unit,
    private val connector: (String, Int) -> Socket = { h, p -> tlsSocket(h, p) },
    private val external: Boolean = false) : AutoCloseable {
    companion object {
        private val hostnameVerifier = OkHttpClient().hostnameVerifier
        fun tlsSocket(host: String, port: Int, factory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory): Socket {
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(host, port), 10_000); raw.soTimeout = 10_000
                val tls = factory.createSocket(raw, host, port, true) as SSLSocket
                tls.startHandshake()
                // OkHttp's existing verifier also supports Android 6 (SSLParameters' equivalent needs API 24).
                if (!hostnameVerifier.verify(host, tls.session)) {
                    tls.close(); throw SSLPeerUnverifiedException("IRC certificate hostname mismatch")
                }
                tls.soTimeout = 180_000
                return tls
            } catch (error: Exception) { raw.close(); throw error }
        }
    }
    private val secret = password.copyOf()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val outgoing = ArrayBlockingQueue<String>(64)
    @Volatile private var socket: Socket? = null
    @Volatile var ready = false; private set
    @Volatile var echoMessages = false; private set
    private var worker: Thread? = null
    private var writer: Thread? = null
    fun start() {
        require(host.isNotBlank() && port in 1..65535 && IrcProtocol.nick(nickname))
        require(account.isEmpty() || account.length <= 64 && account.none { it <= ' ' })
        check(started.compareAndSet(false, true))
        check(!closed.get())
        worker = thread(name = "retui-irc-reader") {
            var negotiated = false; var authenticating = false; var authenticated = false
            var disconnectedText = "Disconnected · /connect to reconnect; unsent drafts stay local."
            val offered = linkedSetOf<String>()
            val mechanism = if (external) "EXTERNAL" else "PLAIN"
            try {
                connection(false, "Connecting with verified TLS…")
                val connected = connector(host, port)
                socket = connected
                if (closed.get()) { connected.close(); return@thread }
                val output = BufferedOutputStream(connected.getOutputStream())
                writer = thread(name = "retui-irc-writer") {
                    try {
                        while (!closed.get()) {
                            val next = outgoing.poll(1, TimeUnit.SECONDS) ?: continue
                            output.write(next.toByteArray(Charsets.UTF_8)); output.flush()
                        }
                    } catch (_: Exception) { close() }
                }
                queue(IrcProtocol.line("CAP", listOf("LS", "302")))
                queue(IrcProtocol.line("NICK", listOf(nickname)))
                queue(IrcProtocol.line("USER", listOf(nickname, "0", "*"), "Re:TUI IRC"))
                val input = connected.getInputStream()
                fun endCapabilities() { if (!negotiated) { negotiated = true; queue(IrcProtocol.line("CAP", listOf("END"))) } }
                while (!closed.get()) {
                    val bytes = ByteArrayOutputStream()
                    while (true) {
                        val c = input.read()
                        if (c == -1) throw java.io.EOFException()
                        if (c == 10) break
                        if (c != 13) bytes.write(c)
                        require(bytes.size() <= 8192) { "Oversized IRC frame" }
                    }
                    val message = IrcMessage.parse(bytes.toString("UTF-8"))
                    when (message.command) {
                        "PING" -> queue(IrcProtocol.line("PONG", trailing = message.params.lastOrNull() ?: ""))
                        "CAP" -> {
                            val sub = message.params.getOrNull(1)
                            val capabilities = message.params.lastOrNull().orEmpty().split(' ').filter { it.isNotBlank() }
                            when (sub) {
                                "LS" -> {
                                    capabilities.firstOrNull { it.startsWith("sasl=") }?.substringAfter('=')?.let { mechanisms ->
                                        check(account.isEmpty() || mechanism in mechanisms.split(',')) { "Required SASL mechanism unavailable" }
                                    }
                                    offered.addAll(capabilities.map { it.substringBefore('=') })
                                    if (message.params.getOrNull(2) != "*") {
                                        check(account.isEmpty() || "sasl" in offered) { "Server does not offer SASL" }
                                        val wanted = listOf("server-time", "echo-message", "multi-prefix") .filter { it in offered }.toMutableList()
                                        if (account.isNotEmpty()) wanted.add("sasl")
                                        if (wanted.isEmpty()) endCapabilities() else queue(IrcProtocol.line("CAP", listOf("REQ"), wanted.joinToString(" ")))
                                    }
                                }
                                "ACK" -> {
                                    echoMessages = "echo-message" in capabilities
                                    if (account.isNotEmpty() && "sasl" in capabilities) {
                                        authenticating = true; queue(IrcProtocol.line("AUTHENTICATE", listOf(mechanism)))
                                    } else { check(account.isEmpty()) { "SASL rejected" }; endCapabilities() }
                                }
                                "NAK" -> { check(account.isEmpty()) { "SASL rejected" }; endCapabilities() }
                            }
                        }
                        "AUTHENTICATE" -> if (authenticating && message.params.firstOrNull() == "+") {
                            val bytesSecret = (if (external) account else "\u0000$account\u0000" + String(secret)).toByteArray(Charsets.UTF_8)
                            val encoded = IrcProtocol.base64(bytesSecret); bytesSecret.fill(0); secret.fill('\u0000')
                            encoded.chunked(400).forEach { queue(IrcProtocol.line("AUTHENTICATE", listOf(it))) }
                            if (encoded.length % 400 == 0) queue(IrcProtocol.line("AUTHENTICATE", listOf("+")))
                            authenticating = false
                        }
                        "903" -> { authenticated = true; secret.fill('\u0000'); endCapabilities() }
                        "902", "904", "905", "906", "907" -> throw java.io.IOException("SASL authentication failed")
                        "001" -> { check(account.isEmpty() || authenticated && negotiated) { "Unauthenticated welcome rejected" }; ready = true; connection(true, "Connected · $host:$port · ${if (account.isEmpty()) "nickname only" else "SASL authenticated"}") }
                        "ERROR" -> throw java.io.IOException("IRC server closed the session")
                    }
                    // Authentication frames are never exposed to transcript/logging callbacks.
                    if (message.command !in setOf("AUTHENTICATE", "CAP", "900", "902", "903", "904", "905", "906", "907", "PING", "PONG")) receive(message)
                }
            } catch (_: Exception) {
                if (!closed.get()) disconnectedText = "Disconnected · TLS, authentication or network failed. /connect to try again; nothing is resent."
            } finally {
                close(); connection(false, disconnectedText)
            }
        }
    }
    private fun queue(frame: String) { check(!closed.get() && outgoing.offer(frame)) { "IRC write queue full or connection closed; nothing was queued." } }
    fun send(command: String, params: List<String> = emptyList(), trailing: String? = null) {
        check(ready && !closed.get()) { "Not connected. Use /connect first." }
        queue(IrcProtocol.line(command, params, trailing))
    }
    override fun close() {
        ready = false; secret.fill('\u0000')
        if (closed.compareAndSet(false, true)) {
            socket?.close(); outgoing.clear(); writer?.interrupt()
        }
    }
}
