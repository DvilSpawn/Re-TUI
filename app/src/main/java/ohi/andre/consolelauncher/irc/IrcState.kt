package ohi.andre.consolelauncher.irc

/** In-memory, bounded terminal windows, deliberately independent of Android and PocketBase. */
class IrcState {
    data class Channel(val name: String, val users: Int, val topic: String)
    val directory = mutableListOf<Channel>()
    var directoryLoading = false; private set
    var directoryTruncated = false; private set
    var directoryError = ""; private set
    var conversationVisible = true
    var joining = ""; private set
    var joinError = ""; private set
    fun beginNames(channel: String = active.name) {
        val b = buffer(channel)
        b.names.clear(); b.namesLoading = true; b.namesError = ""
    }
    fun beginDirectory() { directory.clear(); directoryLoading = true; directoryTruncated = false; directoryError = "" }
    fun failDirectory(text: String) { directoryLoading = false; directoryError = text }
    fun beginJoin(channel: String) { joining = channel; joinError = "" }
    fun beginHistory(channel: String): Boolean {
        val b = buffer(channel)
        if (b.historyLoaded || b.historyLoading) return false
        b.historyLoading = true
        return true
    }
    fun acknowledge() { active.unread = 0; active.highlight = false }
    data class Line(val text: String, val body: String = "", val sender: String = "", var pending: Boolean = false,
        val messageId: String = "")
    data class Buffer(val number: Int, var name: String, var joined: Boolean = false, var topic: String = "",
        var draft: String = "", var unread: Int = 0, var highlight: Boolean = false, var scroll: Int = 0,
        val names: MutableSet<String> = linkedSetOf(), val lines: MutableList<Line> = mutableListOf(),
        var namesLoading: Boolean = false, var namesError: String = "",
        var historyLoading: Boolean = false, var historyLoaded: Boolean = false)
    val buffers = mutableListOf(Buffer(1, "status"))
    var current = 1; private set
    var nickname = "retui"; var mapping = "rfc1459"
    private var nextNumber = 2
    private val historyBatches = linkedMapOf<String, String>()
    val active get() = buffers.first { it.number == current }
    private fun same(a: String, b: String) = IrcProtocol.fold(a, mapping) == IrcProtocol.fold(b, mapping)
    fun buffer(name: String): Buffer = buffers.firstOrNull { same(it.name, name) } ?: run {
        check(buffers.size < 32) { "32-window limit; /window close unused windows." }
        Buffer(nextNumber++, name).also { buffers.add(it) }
    }
    fun switch(value: String) {
        val index = buffers.indexOf(active)
        val selected = when (value.lowercase()) {
            "next" -> buffers[(index + 1) % buffers.size]
            "prev", "previous" -> buffers[(index + buffers.size - 1) % buffers.size]
            else -> buffers.firstOrNull { it.number == value.toIntOrNull() || same(it.name, value) }
        } ?: throw IllegalArgumentException("No such window. /window lists open windows.")
        current = selected.number; if (conversationVisible) acknowledge()
    }
    fun closeActive() {
        check(current != 1 && !active.joined) { "Use /part before closing a joined channel; status cannot close." }
        buffers.remove(active); current = 1
    }
    fun append(target: Buffer, line: Line, incoming: Boolean = false) {
        target.lines.add(line)
        if (target.lines.size > 200) target.lines.removeAt(0)
        if (incoming && (current != target.number || !conversationVisible)) {
            target.unread = (target.unread + 1).coerceAtMost(999)
            target.highlight = target.highlight || !target.name.startsWith('#') && !target.name.startsWith('&') ||
                Regex("(?i)(?<![A-Za-z0-9_])${Regex.escape(nickname)}(?![A-Za-z0-9_])").containsMatchIn(line.body)
        }
    }
    fun status(text: String, incoming: Boolean = false) { text.lines().take(200).forEach { append(buffers.first(), Line("-!- " + IrcProtocol.display(it)), incoming) } }
    fun disconnected() {
        joining = ""
        if (directoryLoading) failDirectory("Disconnected. Browse again after reconnecting.")
        historyBatches.clear()
        buffers.forEach { b ->
            b.joined = false; b.names.clear(); b.namesLoading = false; b.historyLoading = false
            for (i in b.lines.indices) if (b.lines[i].pending) b.lines[i] = b.lines[i].copy(text = b.lines[i].text.replace("[awaiting server echo]", "[delivery uncertain; not retried]"), pending = false)
        }
    }
    fun local(target: String, body: String, echoExpected: Boolean) {
        append(buffer(target), Line("<$nickname> " + IrcProtocol.display(body) + if (echoExpected) " [awaiting server echo]" else " [local echo; unconfirmed]", body, nickname, echoExpected))
    }
    fun receive(message: IrcMessage) {
        val p = message.params; val nick = IrcProtocol.display(message.nick)
        fun event(channel: String, text: String) { append(buffer(channel), Line("-!- $text")) }
        when (message.command) {
            "BATCH" -> {
                val reference = p.firstOrNull().orEmpty()
                if (reference.startsWith('+') && p.getOrNull(1)?.contains("chathistory", true) == true) {
                    val id = reference.drop(1)
                    val target = p.getOrNull(2)?.takeIf { it.startsWith('#') || it.startsWith('&') }.orEmpty()
                    historyBatches[id] = target
                    if (target.isNotEmpty()) buffer(target).historyLoading = true
                } else if (reference.startsWith('-')) {
                    val id = reference.drop(1)
                    val target = historyBatches.remove(id).orEmpty()
                    if (target.isNotEmpty()) buffer(target).apply { historyLoading = false; historyLoaded = true }
                }
            }
            "001" -> { nickname = p.firstOrNull() ?: nickname; status("Welcome $nickname") }
            "321" -> if (!directoryLoading) beginDirectory()
            "322" -> if (directoryLoading && p.size >= 4 && IrcProtocol.target(p[1]) && p[1].first() in "#&") {
                val item = Channel(p[1], p[2].toIntOrNull()?.coerceAtLeast(0) ?: 0, IrcProtocol.display(p[3]))
                val index = directory.indexOfFirst { same(it.name, item.name) }
                if (index >= 0) directory[index] = item else if (directory.size < 1000) directory.add(item) else directoryTruncated = true
            }
            "323" -> directoryLoading = false
            "263" -> { val text = IrcProtocol.display(p.drop(1).joinToString(" ")); if (directoryLoading && p.any { it.equals("LIST", true) }) failDirectory(text); status(text, incoming = true) }
            "005" -> p.filter { it.startsWith("CASEMAPPING=") }.firstOrNull()?.substringAfter('=')?.let { if (it in setOf("ascii", "rfc1459", "strict-rfc1459")) mapping = it }
            "PRIVMSG", "NOTICE" -> {
                if (p.size < 2) return
                val target = p[0]; val own = same(message.nick, nickname)
                // echo-message may echo a NickServ REGISTER password. Never turn service commands into chat.
                if (own && target.lowercase(java.util.Locale.ROOT) in setOf("nickserv", "chanserv", "hostserv", "operserv")) return
                val channel = target.startsWith('#') || target.startsWith('&')
                val destination = if (channel || own) target else message.nick
                val body = p.last(); val action = body.startsWith("\u0001ACTION ") && body.endsWith('\u0001')
                if (body.startsWith('\u0001') && !action) return // No DCC, remote execution or automatic CTCP reply.
                val visible = IrcProtocol.display(if (action) body.drop(8).dropLast(1) else body)
                val b = if (message.command == "NOTICE" && !channel && !buffers.any { same(it.name, destination) }) buffers.first() else buffer(destination)
                val text = if (action) "* $nick $visible" else if (message.command == "NOTICE") "-$nick- $visible" else "<$nick> $visible"
                val messageId = message.tags["msgid"].orEmpty()
                if (messageId.isNotEmpty() && b.lines.any { it.messageId == messageId }) return
                val history = message.tags["batch"]?.let { it in historyBatches } == true
                message.tags["batch"]?.let { historyBatches[it] = destination }
                val pending = if (own) b.lines.firstOrNull { it.pending && it.body == body && same(it.sender, message.nick) } else null
                if (pending != null) b.lines[b.lines.indexOf(pending)] = Line(text, body, nick, messageId = messageId)
                else append(b, Line(text, body, nick, messageId = messageId), incoming = !own && !history)
            }
            "JOIN" -> p.firstOrNull()?.let { channel ->
                val b = buffer(channel)
                if (same(message.nick, nickname)) {
                    b.joined = true
                    // Bouncers may restore several channels after login. Only
                    // the requested JOIN (or first channel from status) takes focus.
                    if (same(joining, channel) || (joining.isEmpty() && current == 1)) {
                        joining = ""; switch(channel)
                    }
                }
                if (b.names.size < 1000) b.names.add(message.nick)
                event(channel, "$nick joined $channel")
            }
            "PART" -> p.firstOrNull()?.let { channel ->
                val b = buffer(channel); b.names.removeAll { same(it, message.nick) }; if (same(message.nick, nickname)) { b.joined = false; b.historyLoaded = false }
                event(channel, "$nick left $channel")
            }
            "KICK" -> if (p.size >= 2) { val b = buffer(p[0]); b.names.removeAll { same(it, p[1]) }; if (same(p[1], nickname)) { b.joined = false; b.historyLoaded = false }; event(p[0], "${IrcProtocol.display(p[1])} kicked by $nick") }
            "QUIT" -> buffers.filter { it.names.any { n -> same(n, message.nick) } }.forEach { b -> b.names.removeAll { same(it, message.nick) }; event(b.name, "$nick quit") }
            "NICK" -> p.firstOrNull()?.let { next ->
                buffers.forEach { b -> if (b.names.removeAll { same(it, message.nick) }) { b.names.add(next); event(b.name, "$nick is now ${IrcProtocol.display(next)}") } }
                buffers.firstOrNull { same(it.name, message.nick) }?.name = next
                if (same(message.nick, nickname)) nickname = next
            }
            "353" -> if (p.size >= 4) { val b = buffer(p[p.lastIndex - 1]); p.last().split(' ').map { it.trimStart('~', '&', '@', '%', '+') }.filter(IrcProtocol::nick).take((1000 - b.names.size).coerceAtLeast(0)).forEach { b.names.add(it) } }
            "366" -> if (p.size >= 2) buffer(p[1]).namesLoading = false
            "332" -> if (p.size >= 3) { buffer(p[1]).topic = IrcProtocol.display(p.last()); event(p[1], "Topic: ${IrcProtocol.display(p.last())}") }
            "TOPIC" -> if (p.size >= 2) { buffer(p[0]).topic = IrcProtocol.display(p.last()); event(p[0], "Topic: ${IrcProtocol.display(p.last())}") }
            "MODE" -> if (p.isNotEmpty()) { if (p[0].startsWith('#') || p[0].startsWith('&')) event(p[0], "Mode: ${IrcProtocol.display(p.drop(1).joinToString(" "))}") }
            else -> if (message.command.matches(Regex("[0-9]{3}"))) {
                val text = p.drop(1).joinToString(" ")
                status(text, incoming = true)
                if (message.command.startsWith('4') || message.command.startsWith('5')) {
                    if (directoryLoading && (p.any { it.equals("LIST", true) } || message.command in setOf("416", "263"))) failDirectory(IrcProtocol.display(text))
                    if (joining.isNotEmpty() && p.any { same(it, joining) }) { joinError = IrcProtocol.display(text); joining = "" }
                    val destination = buffers.firstOrNull { p.getOrNull(1)?.let { target -> same(it.name, target) } == true }
                    if (destination != null && destination.number != 1) {
                        if (destination.namesLoading) { destination.namesLoading = false; destination.namesError = IrcProtocol.display(text) }
                        append(destination, Line("-!- Server rejected command: ${IrcProtocol.display(text)}"), incoming = true)
                        for (i in destination.lines.indices) if (destination.lines[i].pending) destination.lines[i] = destination.lines[i].copy(text = destination.lines[i].text.replace("[awaiting server echo]", "[not confirmed; server rejected command]"), pending = false)
                    }
                }
            }
        }
    }
}
