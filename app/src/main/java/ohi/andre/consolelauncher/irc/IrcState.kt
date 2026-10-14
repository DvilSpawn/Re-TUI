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
    fun beginNames() { active.names.clear(); active.namesLoading = true; active.namesError = "" }
    fun beginDirectory() { directory.clear(); directoryLoading = true; directoryTruncated = false; directoryError = "" }
    fun failDirectory(text: String) { directoryLoading = false; directoryError = text }
    fun beginJoin(channel: String) { joining = channel; joinError = "" }
    fun acknowledge() { active.unread = 0; active.highlight = false }
    data class Line(val text: String, val body: String = "", val sender: String = "", var pending: Boolean = false,
        val messageId: String = "", val time: String = "", val command: String = "")
    data class Buffer(val number: Int, var name: String, var joined: Boolean = false, var topic: String = "",
        var draft: String = "", var unread: Int = 0, var highlight: Boolean = false, var scroll: Int = 0,
        val names: MutableSet<String> = linkedSetOf(), val lines: MutableList<Line> = mutableListOf(),
        var namesLoading: Boolean = false, var namesError: String = "",
        val deletedMessageIds: MutableSet<String> = linkedSetOf())
    val buffers = mutableListOf(Buffer(1, "status"))
    var current = 1; private set
    var nickname = "retui"; var mapping = "rfc1459"
    private var nextNumber = 2
    private class HistoryBatch(val reference: String, val target: String, val previousIds: Set<String>?) {
        val seenIds = mutableSetOf<String>()
    }
    private val historyBatches = mutableMapOf<String, HistoryBatch>()
    private val historySnapshots = mutableMapOf<String, Set<String>>()
    private val timePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{1,9})?Z")
    private fun serverTime(value: String): String {
        if (!timePattern.matches(value)) return ""
        val whole = value.substringBefore('.').removeSuffix("Z")
        val fraction = if ('.' in value) value.substringAfter('.').removeSuffix("Z") else ""
        return "$whole.${fraction.padEnd(9, '0')}Z"
    }
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
        historySnapshots.remove(IrcProtocol.fold(active.name, mapping))
        buffers.remove(active); current = 1
    }
    fun append(target: Buffer, line: Line, incoming: Boolean = false, historical: Boolean = false) {
        val before = if (historical && line.time.isNotEmpty()) target.lines.indexOfFirst { it.time.isNotEmpty() && it.time > line.time } else -1
        target.lines.add(if (before < 0) target.lines.size else before, line)
        if (target.lines.size > 200) target.lines.removeAt(0)
        if (incoming && (current != target.number || !conversationVisible)) {
            target.unread = (target.unread + 1).coerceAtMost(999)
            target.highlight = target.highlight || !target.name.startsWith('#') && !target.name.startsWith('&') ||
                Regex("(?i)(?<![A-Za-z0-9_])${Regex.escape(nickname)}(?![A-Za-z0-9_])").containsMatchIn(line.body)
        }
    }
    fun status(text: String, incoming: Boolean = false) { text.lines().take(200).forEach { append(buffers.first(), Line("-!- " + IrcProtocol.display(it)), incoming) } }
    fun disconnected() {
        historyBatches.clear()
        historySnapshots.clear()
        joining = ""
        if (directoryLoading) failDirectory("Disconnected. Browse again after reconnecting.")
        buffers.forEach { b ->
            b.joined = false; b.names.clear(); b.namesLoading = false
            for (i in b.lines.indices) if (b.lines[i].pending) b.lines[i] = b.lines[i].copy(text = b.lines[i].text.replace("[awaiting server echo]", "[delivery uncertain; not retried]"), pending = false)
        }
    }
    fun local(target: String, body: String, echoExpected: Boolean) {
        append(buffer(target), Line("<$nickname> " + IrcProtocol.display(body) + if (echoExpected) " [awaiting server echo]" else " [local echo; unconfirmed]", body, nickname, echoExpected))
    }
    fun receive(message: IrcMessage) {
        val p = message.params; val nick = IrcProtocol.display(message.nick)
        if (message.command == "BATCH") {
            val reference = p.firstOrNull() ?: return
            val parent = message.tags["batch"]?.let { historyBatches[it] }
            if (reference.startsWith('+') && reference.length > 1 && historyBatches.size < 32) {
                if (p.getOrNull(1) == "chathistory") {
                    val target = p.getOrNull(2).orEmpty()
                    historyBatches[reference.drop(1)] = HistoryBatch(reference.drop(1), target, historySnapshots.remove(IrcProtocol.fold(target, mapping)))
                }
                else if (parent != null) historyBatches[reference.drop(1)] = parent
            } else if (reference.startsWith('-')) {
                val batch = historyBatches.remove(reference.drop(1))
                if (batch != null && batch.reference == reference.drop(1) && batch.previousIds != null) {
                    // Only completed automatic LATEST replies replace the cached window; failed refreshes keep it.
                    buffers.firstOrNull { same(it.name, batch.target) }?.lines?.removeAll {
                        it.messageId in batch.previousIds && it.messageId !in batch.seenIds && it.command != "REDACT"
                    }
                }
            }
            return
        }
        val historyBatch = message.tags["batch"]?.let { historyBatches[it] }
        val historical = historyBatch != null
        // Historical membership and mode events must not change today's channel state.
        if (historical && message.command !in setOf("PRIVMSG", "NOTICE", "REDACT")) return
        fun event(channel: String, text: String) { append(buffer(channel), Line("-!- $text")) }
        when (message.command) {
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
                if (historical && (!channel || !same(target, historyBatch!!.target))) return
                val destination = if (channel || own) target else message.nick
                val body = p.last(); val action = body.startsWith("\u0001ACTION ") && body.endsWith('\u0001')
                if (body.startsWith('\u0001') && !action) return // No DCC, remote execution or automatic CTCP reply.
                val visible = IrcProtocol.display(if (action) body.drop(8).dropLast(1) else body)
                val b = if (message.command == "NOTICE" && !channel && !buffers.any { same(it.name, destination) }) buffers.first() else buffer(destination)
                val text = if (action) "* $nick $visible" else if (message.command == "NOTICE") "-$nick- $visible" else "<$nick> $visible"
                val id = message.tags["msgid"].orEmpty(); val time = serverTime(message.tags["time"].orEmpty())
                if (id.isNotEmpty()) {
                    if (historyBatch != null && historyBatch.seenIds.size < 200) historyBatch.seenIds.add(id)
                    if (id in b.deletedMessageIds) return
                }
                val pending = if (own && !historical) b.lines.firstOrNull { it.pending && it.body == body && same(it.sender, message.nick) } else null
                val duplicate = b.lines.any { line ->
                    id.isNotEmpty() && line.messageId == id || (id.isEmpty() || line.messageId.isEmpty()) && time.isNotEmpty() && line.time == time &&
                        line.command == message.command && line.body == body && same(line.sender, nick)
                }
                if (duplicate) { if (pending != null) b.lines.remove(pending); return }
                val line = Line(text, body, nick, messageId = id, time = time, command = message.command)
                if (pending != null) b.lines[b.lines.indexOf(pending)] = line
                else append(b, line, incoming = !own && !historical, historical = historical)
            }
            "REDACT" -> if (p.size >= 2 && (historyBatch == null || same(p[0], historyBatch.target))) {
                val b = buffers.firstOrNull { same(it.name, p[0]) } ?: return
                val id = p[1]
                if (id.isEmpty() || id.length > 512) return
                val index = b.lines.indexOfFirst { it.messageId == id }
                if (index < 0 && buffers.any { other -> other !== b && other.lines.any { it.messageId == id } }) return
                b.deletedMessageIds.add(id)
                if (b.deletedMessageIds.size > 200) b.deletedMessageIds.remove(b.deletedMessageIds.first())
                if (index >= 0) {
                    val removed = b.lines[index]
                    b.lines[index] = removed.copy(text = "-!- Message deleted", body = "", pending = false, command = "REDACT")
                }
            }
            "FAIL" -> when (p.firstOrNull()) {
                "CHATHISTORY" -> status("Could not load earlier messages: " + p.lastOrNull().orEmpty())
                "REDACT" -> {
                    val b = buffers.firstOrNull { same(it.name, p.getOrNull(2).orEmpty()) }
                    if (b != null) append(b, Line("-!- Could not delete message: " + IrcProtocol.display(p.lastOrNull().orEmpty())))
                    else status("Could not delete message: " + p.lastOrNull().orEmpty())
                }
            }
            "JOIN" -> p.firstOrNull()?.let { channel ->
                val b = buffer(channel)
                if (same(message.nick, nickname)) {
                    b.joined = true
                    historySnapshots[IrcProtocol.fold(channel, mapping)] = b.lines.map { it.messageId }.filter { it.isNotEmpty() }.toSet()
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
                val b = buffer(channel); b.names.removeAll { same(it, message.nick) }; if (same(message.nick, nickname)) { b.joined = false; historySnapshots.remove(IrcProtocol.fold(channel, mapping)) }
                event(channel, "$nick left $channel")
            }
            "KICK" -> if (p.size >= 2) { val b = buffer(p[0]); b.names.removeAll { same(it, p[1]) }; if (same(p[1], nickname)) { b.joined = false; historySnapshots.remove(IrcProtocol.fold(p[0], mapping)) }; event(p[0], "${IrcProtocol.display(p[1])} kicked by $nick") }
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
