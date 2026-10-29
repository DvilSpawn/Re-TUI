package ohi.andre.consolelauncher.irc

/** Local presentation only. Search/account text never enters conversation drafts. */
class IrcNavigation(private val current: () -> IrcState) {
    constructor(state: IrcState) : this({ state })
    private val state get() = current()
    enum class Page { CHAT, CHANNELS, DIRECTORY, PEOPLE, HELP, COMMANDS, MORE, DELETE_MESSAGES, DELETE_CONFIRM, NETWORK, ACCOUNT, REGISTER, JOIN, LEAVE, DETAILS, DIAGNOSTICS, FORGET_DEVICE,
        NETWORKS, ADD_NETWORK, NICKNAME, REMOVE_NETWORK, CONVERSATIONS }
    var page = Page.CHAT; private set
    var text = ""
    var scroll = 0
    var limit = 50
    var deleteTarget = ""
    var deleteMessageId = ""
    fun open(next: Page) {
        page = next; text = ""; scroll = 0; limit = 50; state.conversationVisible = next == Page.CHAT
        if (next != Page.DELETE_CONFIRM) { deleteTarget = ""; deleteMessageId = "" }
    }
    fun close() { open(Page.CHAT); state.acknowledge() }
    fun matches(value: String) = IrcProtocol.fold(value, state.mapping).contains(IrcProtocol.fold(text.trim(), state.mapping))
}

/** Touch and slash commands use these same validated, bounded actions. */
class IrcActions(private val state: IrcState, private val send: (String, List<String>, String?) -> Unit) {
    fun join(name: String) {
        require(IrcProtocol.target(name) && name.first() in "#&") { "Enter a channel such as #retui." }
        val buffer = state.buffer(name)
        if (buffer.joined) state.switch(name) else { send("JOIN", listOf(name), null); state.beginJoin(name) }
    }
    fun query(name: String) {
        require(IrcProtocol.nick(name) && name.lowercase() !in setOf("nickserv", "chanserv", "hostserv", "operserv")) { "Choose a person's nickname; service login uses masked input." }
        state.buffer(name); state.switch(name)
    }
    fun directory() { if (!state.directoryLoading) { send("LIST", emptyList(), null); state.beginDirectory() } }
    fun names(channel: String = state.active.name) {
        val buffer = state.buffer(channel)
        require(buffer.joined) { "Join a channel to see its people." }
        if (!buffer.namesLoading) { send("NAMES", listOf(buffer.name), null); state.beginNames(buffer.name) }
    }
    fun part() { require(state.active.joined); send("PART", listOf(state.active.name), null) }
    fun deleteMessage(target: String, id: String) {
        require(IrcProtocol.target(target) && target.first() in "#&" && id.length in 1..512 && id.none { it <= ' ' || it == '\u007f' })
        val b = state.buffers.firstOrNull { IrcProtocol.fold(it.name, state.mapping) == IrcProtocol.fold(target, state.mapping) }
        check(b?.joined == true && b.lines.any { it.messageId == id && it.command in setOf("PRIVMSG", "NOTICE") && !it.pending }) { "This message is no longer available for deletion." }
        send("REDACT", listOf(target, id), null)
    }
}
