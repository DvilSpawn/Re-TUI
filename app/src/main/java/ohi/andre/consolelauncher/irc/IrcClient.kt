package ohi.andre.consolelauncher.irc

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
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
    private val external: Boolean = false, private val register: Boolean = false,
    // Networks without SASL (OFTC, for one) sign in through NickServ after the welcome instead.
    private val servicesFallback: Boolean = false) : AutoCloseable {
    companion object {
        private val hostnameVerifier = OkHttpClient().hostnameVerifier
        fun tlsSocket(host: String, port: Int, factory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory): Socket {
            // Networks publish IPv6 and IPv4 addresses; a phone without a working IPv6 route must still connect.
            // Try the system's first choice, then the other address family, then the rest.
            val resolved = InetAddress.getAllByName(host).toList()
            val first = resolved.first()
            val addresses = (listOf(first) + resolved.filter { it.javaClass != first.javaClass }.take(1) + resolved).distinct()
            var failure: Exception? = null
            for (address in addresses) {
                val raw = Socket()
                try {
                    raw.connect(InetSocketAddress(address, port), if (addresses.size > 1) 6_000 else 10_000); raw.soTimeout = 10_000
                    val tls = factory.createSocket(raw, host, port, true) as SSLSocket
                    tls.startHandshake()
                    // OkHttp's existing verifier also supports Android 6 (SSLParameters' equivalent needs API 24).
                    if (!hostnameVerifier.verify(host, tls.session)) {
                        tls.close(); throw SSLPeerUnverifiedException("IRC certificate hostname mismatch")
                    }
                    tls.soTimeout = 180_000
                    return tls
                } catch (error: SSLPeerUnverifiedException) { raw.close(); throw error }
                catch (error: Exception) { raw.close(); failure = error }
            }
            throw failure ?: java.net.UnknownHostException(host)
        }
    }
    private val secret = password.copyOf()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val outgoing = ArrayBlockingQueue<String>(64)
    @Volatile private var socket: Socket? = null
    @Volatile var ready = false; private set
    @Volatile var echoMessages = false; private set
    @Volatile var messageRedaction = false; private set
    private var worker: Thread? = null
    private var writer: Thread? = null
    // Ends the connection with a reason the user can act on.
    private class Failure(val explanation: String) : java.io.IOException()
    fun start() {
        require(host.isNotBlank() && port in 1..65535 && IrcProtocol.nick(nickname))
        require(account.isEmpty() || account.length <= 64 && account.none { it <= ' ' })
        require(!register || !external && account == nickname && secret.isNotEmpty() && secret.none { it == '\u0000' || it == '\r' || it == '\n' })
        if (register) IrcProtocol.line("REGISTER", listOf(account, "*"), String(secret))
        check(started.compareAndSet(false, true))
        check(!closed.get())
        worker = thread(name = "retui-irc-reader") {
            var negotiated = false; var authenticating = false; var authenticated = false
            var welcomed = false; var registrationSent = false; var registerBeforeConnect = false
            var currentNickname = nickname; var historyEnabled = false; var historyLimit = 100; var nicknameRetries = 0
            val historyCapabilities = listOf("batch", "server-time", "message-tags", "draft/chathistory")
            val registrationCapability = "draft/account-registration"
            var disconnectedText = "Disconnected · /connect to reconnect; unsent drafts stay local."
            val offered = linkedSetOf<String>()
            val mechanism = if (external) "EXTERNAL" else "PLAIN"
            try {
                connection(false, "Connecting with verified TLS…")
                val connected = connector(host, port)
                socket = connected
                if (closed.get()) { connected.close(); return@thread }
                if (register) connected.soTimeout = 30_000
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
                fun submitRegistration() {
                    check(!registrationSent)
                    registrationSent = true
                    queue(IrcProtocol.line("REGISTER", listOf(account, "*"), String(secret)))
                    secret.fill('\u0000')
                    connection(false, "Creating account…")
                }
                fun finishRegistration() {
                    if (welcomed && authenticated && !ready) {
                        connected.soTimeout = 180_000
                        ready = true; connection(true, "Connected · $host:$port · account registered")
                    }
                }
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
                                        check(register || account.isEmpty() || mechanism in mechanisms.split(',')) { "Required SASL mechanism unavailable" }
                                    }
                                    if (register) capabilities.firstOrNull { it.substringBefore('=') == registrationCapability }?.substringAfter('=', "")?.split(',')?.map { it.substringBefore('=') }?.let { options ->
                                        if ("email-required" in options) throw Failure("This network requires email registration. Use its registration instructions, then Sign in.")
                                        registerBeforeConnect = "before-connect" in options
                                    }
                                    offered.addAll(capabilities.map { it.substringBefore('=') })
                                    if (message.params.getOrNull(2) != "*") {
                                        if (register && registrationCapability !in offered) throw Failure("Registration is closed or guided signup is unsupported on this network. Use an existing account to Sign in.")
                                        check(register || account.isEmpty() || "sasl" in offered || servicesFallback) { "Server does not offer SASL" }
                                        val wanted = listOf("server-time", "echo-message", "multi-prefix") .filter { it in offered }.toMutableList()
                                        if (offered.containsAll(historyCapabilities)) wanted.addAll(historyCapabilities.filter { it !in wanted })
                                        if ("draft/message-redaction" in offered && "message-tags" in offered) {
                                            wanted.addAll(listOf("message-tags", "draft/message-redaction").filter { it !in wanted })
                                        }
                                        if (register) wanted.add(registrationCapability) else if (account.isNotEmpty() && "sasl" in offered) wanted.add("sasl")
                                        if (wanted.isEmpty()) endCapabilities() else queue(IrcProtocol.line("CAP", listOf("REQ"), wanted.joinToString(" ")))
                                    }
                                }
                                "ACK" -> {
                                    echoMessages = "echo-message" in capabilities
                                    historyEnabled = capabilities.containsAll(historyCapabilities)
                                    messageRedaction = "draft/message-redaction" in capabilities && "message-tags" in capabilities
                                    if (register) {
                                        if (registrationCapability !in capabilities) throw Failure("Guided registration was rejected by this network. Nothing was joined.")
                                        if (registerBeforeConnect) submitRegistration() else endCapabilities()
                                    } else if (account.isNotEmpty() && "sasl" in capabilities) {
                                        authenticating = true; queue(IrcProtocol.line("AUTHENTICATE", listOf(mechanism)))
                                    } else { check(account.isEmpty() || servicesFallback && "sasl" !in offered) { "SASL rejected" }; endCapabilities() }
                                }
                                "NAK" -> { if (register) throw Failure("Guided registration was rejected by this network. Nothing was joined."); check(account.isEmpty() || servicesFallback && "sasl" !in offered) { "SASL rejected" }; endCapabilities() }
                            }
                        }
                        "AUTHENTICATE" -> if (authenticating && message.params.firstOrNull() == "+") {
                            val bytesSecret = (if (external) account else "\u0000$account\u0000" + String(secret)).toByteArray(Charsets.UTF_8)
                            val encoded = IrcProtocol.base64(bytesSecret); bytesSecret.fill(0); secret.fill('\u0000')
                            encoded.chunked(400).forEach { queue(IrcProtocol.line("AUTHENTICATE", listOf(it))) }
                            if (encoded.length % 400 == 0) queue(IrcProtocol.line("AUTHENTICATE", listOf("+")))
                            authenticating = false
                        }
                        "903" -> if (!register) { authenticated = true; secret.fill('\u0000'); endCapabilities() }
                        "904", "905" -> throw Failure(if (external) "This network did not accept the device key. Sign in with your password."
                            else "Sign-in rejected: check the account name and password. The account is the name you registered, not always your current nickname.")
                        "902" -> throw Failure("That account is locked or unavailable on this network.")
                        "906", "907" -> throw Failure("Sign-in was interrupted. Try again.")
                        // A nickname in use (often your own session elsewhere) would otherwise stall registration until the server drops it.
                        "433" -> if (!ready) {
                            if (register || ++nicknameRetries > 3) throw Failure("The nickname $currentNickname is in use. Choose another nickname, or end your other session and try again.")
                            currentNickname = (nickname.take(12) + "_".repeat(nicknameRetries))
                            queue(IrcProtocol.line("NICK", listOf(currentNickname)))
                        }
                        "001" -> { currentNickname = message.params.firstOrNull() ?: nickname; if (register) {
                            check(negotiated && IrcProtocol.fold(message.params.firstOrNull().orEmpty(), "rfc1459") == IrcProtocol.fold(nickname, "rfc1459")) { "Unexpected registration welcome" }
                            welcomed = true
                            if (!registrationSent) submitRegistration()
                            finishRegistration()
                        } else {
                            // Without SASL on offer, a password goes to NickServ and a device key is matched by services.
                            val viaServices = servicesFallback && account.isNotEmpty() && "sasl" !in offered
                            check(account.isEmpty() || authenticated && negotiated || viaServices) { "Unauthenticated welcome rejected" }
                            if (viaServices && !external) queue(IrcProtocol.line("PRIVMSG", listOf("NickServ"), "IDENTIFY " + String(secret)))
                            if (viaServices) secret.fill('\u0000')
                            ready = true
                            connection(true, "Connected · $host:$port · ${when { account.isEmpty() -> "nickname only"; viaServices -> "signed in through NickServ"; else -> "SASL authenticated" }}")
                        } }
                        "005" -> message.params.firstOrNull { it.startsWith("CHATHISTORY=") }?.substringAfter('=')?.toIntOrNull()?.takeIf { it > 0 }?.let { historyLimit = it.coerceAtMost(100) }
                        "JOIN" -> if (ready && historyEnabled && IrcProtocol.fold(message.nick) == IrcProtocol.fold(currentNickname)) {
                            message.params.firstOrNull()?.takeIf { IrcProtocol.target(it) && it.first() in "#&" }?.let {
                                queue(IrcProtocol.line("CHATHISTORY", listOf("LATEST", it, "*", historyLimit.toString())))
                            }
                        }
                        "NICK" -> if (IrcProtocol.fold(message.nick) == IrcProtocol.fold(currentNickname)) currentNickname = message.params.firstOrNull() ?: currentNickname
                        "REGISTER" -> if (register && registrationSent && !authenticated) {
                            if (message.prefix.isEmpty() || message.prefix.contains('!') || message.prefix.contains('@')) continue
                            when (message.params.firstOrNull()) {
                                "SUCCESS" -> {
                                    check(message.params.getOrNull(1)?.equals(account, true) == true) { "Unexpected registration account" }
                                    authenticated = true; endCapabilities(); finishRegistration()
                                }
                                "VERIFICATION_REQUIRED" -> throw Failure("Account needs verification on this network. Complete its verification instructions, then Sign in; no device key was saved.")
                            }
                        }
                        "FAIL" -> if (register && !authenticated && message.params.firstOrNull() in listOf("REGISTER", "*")) throw Failure(when (message.params.getOrNull(1)) {
                            "ACCOUNT_EXISTS", "ALREADY_REGISTERED", "ALREADY_AUTHENTICATED" -> "That account name is unavailable. Sign in if it is yours, or choose another name."
                            "DISALLOWED" -> "New account registration is closed on this network. Use an existing account to Sign in."
                            "INVALID_PASSWORD", "WEAK_PASSWORD", "UNACCEPTABLE_PASSWORD" -> "The server rejected this password. Choose a stronger password and try again."
                            "INVALID_USERNAME", "BAD_ACCOUNT_NAME", "ACCOUNT_NAME_MUST_BE_NICK" -> "The server rejected this account name. Choose another name and try again."
                            "INVALID_EMAIL", "UNACCEPTABLE_EMAIL" -> "This network requires email registration. Use its registration instructions, then Sign in."
                            else -> "Account creation was not confirmed. Sign in if it succeeded; signup is never retried automatically."
                        })
                        "ERROR" -> throw Failure("The server closed the connection: " + IrcProtocol.display(message.params.lastOrNull().orEmpty()).take(200))
                    }
                    // Authentication frames are never exposed to transcript/logging callbacks.
                    if (message.command !in setOf("AUTHENTICATE", "CAP", "REGISTER", "900", "902", "903", "904", "905", "906", "907", "PING", "PONG") && !(register && message.command == "FAIL")) receive(message)
                }
            } catch (error: Exception) {
                if (!closed.get()) disconnectedText = "Disconnected · " + if (error is Failure) error.explanation else if (register && !authenticated) "Account creation was not confirmed. Sign in if it succeeded; signup is never retried automatically."
                    else if (error is IllegalStateException) (error.message ?: "Sign-in failed") + "."
                    else if (error is javax.net.ssl.SSLException) "The secure (TLS) connection failed. Check the address and port; only TLS ports work."
                    else "The network could not be reached, or the connection dropped. Nothing was resent."
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
            outgoing.clear(); writer?.interrupt()
            val connected = socket
            // TLS close_notify can write to the network. Back/Forget must never do
            // that on Android's main thread; logical disconnect is immediate.
            if (connected != null) thread(name = "retui-irc-close", isDaemon = true) { runCatching { connected.close() } }
        }
    }
}
