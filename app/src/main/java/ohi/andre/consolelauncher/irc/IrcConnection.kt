package ohi.andre.consolelauncher.irc

import android.app.Application
import android.os.Handler
import android.os.Looper
import java.net.Socket
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLSocketFactory
import ohi.andre.consolelauncher.R

/**
 * One network: its connection, windows and device sign-in. Used on the main thread; client callbacks are
 * posted back to it. [stored] networks are written through [save] whenever their settings change.
 */
class IrcConnection(
    private val app: Application,
    network: IrcNetwork,
    private val devices: IrcDeviceKey,
    private val connector: () -> ((String, Int) -> Socket)?,
    private val changed: () -> Unit,
    private val save: () -> Unit
) {
    private enum class Mode { GUEST, PASSWORD, DEVICE }
    companion object {
        // Seconds between automatic reconnects after a connection that had been working drops.
        val RECONNECT_DELAYS = intArrayOf(5, 15, 30, 60, 120, 300)
    }
    var network = network; private set
    var stored = false
    val state = IrcState().apply { nickname = network.nickname.ifEmpty { IrcNetwork.guestNickname() } }
    val actions = IrcActions(state, ::send)
    var client: IrcClient? = null; private set
    var connectionText = "Offline"
    var deviceStatus = ""
    var rememberDevice = false
    private val ui = Handler(Looper.getMainLooper())
    private val incoming = ArrayBlockingQueue<Pair<Int, IrcMessage>>(256)
    private val drainScheduled = AtomicBoolean(false)
    private var epoch = 0
    private var pendingDevice: IrcDeviceKey.Identity? = null
    private var mode = Mode.GUEST
    private var reconnectAttempt = 0
    private var reconnectTask: Runnable? = null

    val ready get() = client?.ready == true
    /** Connected, connecting, or waiting to reconnect: something a background service must keep alive. */
    val busy get() = client != null || reconnectTask != null
    /** Marked away on the network while nobody is looking at the pane; re-sent after a reconnect. */
    var away = false
        set(value) {
            if (field == value) return
            field = value
            if (ready) runCatching { sendAway() }
        }
    private fun sendAway() = if (away) send("AWAY", emptyList(), "Away from Re:TUI") else send("AWAY")
    // Conversations only: server notices in the status window are not messages to read.
    val unread get() = state.buffers.filter { it.number != 1 }.sumOf { it.unread }
    val highlighted get() = state.buffers.any { it.number != 1 && it.highlight }
    val identity get() = IrcDeviceKey.Identity(network.host.lowercase(Locale.ROOT), network.port, network.account)
    /** Whether this account can sign in without a password on this phone. */
    val deviceReady get() = network.device && network.account.isNotEmpty() && devices.has(identity)

    fun update(next: IrcNetwork) { network = next; if (stored) save() }
    var host: String
        get() = network.host
        set(value) { update(network.copy(host = value, account = "", device = false)) }
    var port: Int
        get() = network.port
        set(value) { update(network.copy(port = value, account = "", device = false)) }
    var account: String
        get() = network.account
        set(value) { if (!value.equals(network.account, true)) update(network.copy(account = value, device = false)) }

    private fun scheduleDrain() { if (drainScheduled.compareAndSet(false, true)) ui.post { drain() } }
    private fun own(nick: String) = IrcProtocol.fold(nick, state.mapping) == IrcProtocol.fold(state.nickname, state.mapping)
    private fun drain() {
        var updated = false
        repeat(64) {
            val (generation, message) = incoming.poll() ?: return@repeat
            if (generation != epoch) return@repeat
            val identity = pendingDevice
            if (identity != null && message.command == "NOTICE" && message.nick.equals("NickServ", true) && message.params.firstOrNull()?.let(::own) == true) {
                // Services word this differently (Ergo, Atheme, OFTC); each confirms with "added" or "already".
                val notice = IrcProtocol.display(message.params.lastOrNull().orEmpty())
                if (Regex("(?i)\\b(added|already)\\b").containsMatchIn(notice)) {
                    update(network.copy(device = true)); pendingDevice = null
                    deviceStatus = app.getString(R.string.irc_device_saved)
                }
            }
            runCatching { state.receive(message) }.onFailure { state.status("Incoming event could not be displayed; window limit or malformed data.") }
            if (message.tags["batch"] == null) track(message)
            updated = true
        }
        drainScheduled.set(false)
        if (incoming.isNotEmpty()) scheduleDrain()
        if (updated) changed()
    }
    /** Keeps the rejoin list in step with the user's own joins, parts and kicks. */
    private fun track(message: IrcMessage) {
        val p = message.params
        when (message.command) {
            "JOIN" -> p.firstOrNull()?.takeIf { (it.startsWith('#') || it.startsWith('&')) && own(message.nick) }?.let { channel ->
                if (ready) runCatching { actions.names(channel) }
                remember(channel, true)
            }
            "PART" -> p.firstOrNull()?.takeIf { own(message.nick) }?.let { remember(it, false) }
            "KICK" -> if (p.size >= 2 && own(p[1])) remember(p[0], false)
        }
    }
    private fun remember(channel: String, joined: Boolean) {
        if (!IrcProtocol.target(channel)) return
        val others = network.channels.filterNot { IrcProtocol.fold(it, state.mapping) == IrcProtocol.fold(channel, state.mapping) }
        val next = if (joined) (others + channel).take(IrcNetwork.MAX_CHANNELS) else others
        if (next != network.channels) update(network.copy(channels = next))
    }

    fun connect(password: CharArray = charArrayOf(), external: Boolean = false, register: Boolean = false) {
        val authenticating = external || register || password.isNotEmpty()
        require(!authenticating || network.account.isNotEmpty()) { "Choose an account name first." }
        val identity = identity
        val enroll = rememberDevice && authenticating && !external
        if (external) check(devices.has(identity)) { "Device key unavailable. Sign in with a password." }
        stop()
        deviceStatus = ""
        mode = if (external) Mode.DEVICE else if (authenticating) Mode.PASSWORD else Mode.GUEST
        val generation = ++epoch
        state.status("Connecting to ${network.host}:${network.port} with verified TLS")
        var welcomed = false
        client = IrcClient(network.host, network.port, state.nickname, if (authenticating) network.account else "", password,
            // A busy server must not create an unbounded Android main-thread callback backlog.
            receive = { message -> check(incoming.offer(generation to message)) { "IRC UI backlog full" }; scheduleDrain() },
            connection = { connected, text -> ui.post { if (generation == epoch) {
                if (connected) { welcomed = true; onReady(enroll, register, identity) }
                else if (text.startsWith("Disconnected")) onDropped(text, welcomed, register, external)
                else connectionText = "Connecting…"
                changed()
            } } }, connector = connector() ?: { h, p ->
                if (enroll && !register) devices.ensure(identity)
                IrcClient.tlsSocket(h, p, if (external || enroll && !register) devices.factory(identity) else SSLSocketFactory.getDefault() as SSLSocketFactory)
            }, external = external, register = register, servicesFallback = true)
        client!!.start(); changed()
    }
    private fun onReady(enroll: Boolean, register: Boolean, identity: IrcDeviceKey.Identity) {
        connectionText = "Connected · ${network.host}"
        reconnectAttempt = 0
        update(network.copy(nickname = state.nickname, connectOnOpen = true))
        if (away) runCatching { sendAway() }
        if (enroll) runCatching {
            // A new account's connection carried no certificate, so name its fingerprint (Ergo's format);
            // a password sign-in presented the key, and every services package can add the current one.
            if (register) { devices.ensure(identity); send("PRIVMSG", listOf("NickServ"), "CERT ADD ${devices.fingerprint(identity)}") }
            else send("PRIVMSG", listOf("NickServ"), "CERT ADD")
            pendingDevice = identity; deviceStatus = app.getString(R.string.irc_device_pending)
            val generation = epoch
            ui.postDelayed({ if (generation == epoch && pendingDevice == identity) { pendingDevice = null; deviceStatus = app.getString(R.string.irc_device_failed); changed() } }, 10_000)
        }.onFailure { deviceStatus = app.getString(R.string.irc_device_failed) }
        rejoin()
    }
    private fun rejoin() {
        // JOIN takes a comma list; keep each line well inside the 512-byte limit.
        val channels = network.channels.filter { IrcProtocol.target(it) }
        var batch = mutableListOf<String>()
        fun flush() { if (batch.isNotEmpty()) runCatching { send("JOIN", listOf(batch.joinToString(","))) }; batch = mutableListOf() }
        for (channel in channels) { if ((batch + channel).joinToString(",").length > 400) flush(); batch.add(channel) }
        flush()
    }
    private fun onDropped(text: String, welcomed: Boolean, register: Boolean, external: Boolean) {
        state.disconnected(); pendingDevice = null
        state.status(text)
        if (external && !welcomed) deviceStatus = app.getString(R.string.irc_device_failed)
        val retrying = reconnectAttempt > 0
        // Only a working connection reconnects by itself, and only when it needs no password.
        if (!register && (welcomed || retrying) && mode != Mode.PASSWORD) scheduleReconnect()
        else connectionText = when {
            // Never got in: the client's reason (wrong password, nickname in use…) says what to change.
            register || !welcomed -> text
            mode == Mode.PASSWORD -> if (welcomed) "Disconnected · Sign in to reconnect" else "Disconnected · Sign in to retry"
            mode == Mode.DEVICE -> "Disconnected · Reconnect or sign in to retry"
            else -> "Disconnected · Connect to try again"
        }
    }
    private fun scheduleReconnect() {
        if (reconnectAttempt >= RECONNECT_DELAYS.size) { reconnectAttempt = 0; connectionText = "Disconnected · Reconnect to try again"; return }
        val delay = RECONNECT_DELAYS[reconnectAttempt++]
        connectionText = "Disconnected · reconnecting in ${delay}s"
        val generation = epoch
        val reconnectMode = mode
        reconnectTask = Runnable {
            reconnectTask = null
            if (generation == epoch && !ready) runCatching { connect(external = reconnectMode == Mode.DEVICE) }
                .onFailure { connectionText = "Disconnected · Reconnect to try again"; changed() }
        }.also { ui.postDelayed(it, delay * 1000L) }
    }
    /** The phone is online again: a network waiting out its reconnect delay tries now. */
    fun networkReturned() {
        val task = reconnectTask ?: return
        ui.removeCallbacks(task); reconnectAttempt = 0
        task.run()
    }
    /** Connects the way this network last connected without a password. */
    fun reconnect() {
        if (deviceReady) connect(external = true) else connect()
    }
    private fun stop() {
        reconnectTask?.let(ui::removeCallbacks); reconnectTask = null
        epoch++; pendingDevice = null; client?.close(); client = null; incoming.clear(); state.disconnected(); connectionText = "Offline"
    }
    /** The user disconnected: stay offline next time the pane opens. */
    fun disconnect() {
        stop(); reconnectAttempt = 0
        if (network.connectOnOpen) update(network.copy(connectOnOpen = false))
        changed()
    }
    /** The pane closed: reconnect when it opens again. */
    fun suspend() { stop(); reconnectAttempt = 0 }
    fun forgetDevice() {
        runCatching { devices.forget(identity) }
        update(network.copy(device = false)); rememberDevice = false
        deviceStatus = app.getString(R.string.irc_device_forgotten)
        changed()
    }
    fun send(command: String, parameters: List<String> = emptyList(), body: String? = null) {
        val current = client ?: throw IllegalStateException("Not connected. /connect first.")
        current.send(command, parameters, body)
    }
    fun say(target: String, body: String) {
        require(IrcProtocol.target(target) && body.isNotBlank() && target.lowercase() !in setOf("nickserv", "chanserv", "hostserv", "operserv")) { "Use masked /login, /register or /oper for service authentication." }
        require(IrcProtocol.validUnicode(body) && body.none { it < ' ' && it != '\u0001' })
        require(!body.contains('\r') && !body.contains('\n') && !body.contains('\u0000'))
        // Check buffer capacity before enqueueing; a UI failure must never encourage a duplicate send.
        state.buffer(target)
        send("PRIVMSG", listOf(target), body)
        state.local(target, body, client?.echoMessages == true); changed()
    }
}
