package ohi.andre.consolelauncher.community

/** Credential mode is deliberately separate from persisted conversation state. */
internal class CommunityAuthInput {
    enum class Stage { NORMAL, USERNAME, PASSWORD, CONFIRMATION, SUBMITTING }
    enum class Error { NONE, USERNAME, PASSWORD, CONFIRMATION }
    class Request(val registering: Boolean, val username: String, val password: String)
    var stage = Stage.NORMAL
        private set
    var error = Error.NONE
        private set
    var registering = false
        private set
    private var username = ""
    private var secret: CharArray? = null
    val active get() = stage != Stage.NORMAL
    val masked get() = stage == Stage.PASSWORD || stage == Stage.CONFIRMATION
    fun hasSecret() = secret != null
    fun begin(registering: Boolean) { cancel(); this.registering = registering; stage = Stage.USERNAME }
    fun cancel() { clearSecret(); username = ""; error = Error.NONE; stage = Stage.NORMAL }
    fun restart() { clearSecret(); username = ""; stage = Stage.USERNAME }
    private fun clearSecret() { secret?.fill('\u0000'); secret = null }
    fun submit(text: String): Request? {
        error = Error.NONE
        // Only nonsecret stages interpret commands. Passwords are always literal.
        if (stage == Stage.USERNAME && text == "/cancel") { cancel(); return null }
        when (stage) {
            Stage.USERNAME -> if (!text.matches(Regex("[-_a-zA-Z0-9]{3,24}"))) error = Error.USERNAME
                else { username = text; stage = Stage.PASSWORD }
            Stage.PASSWORD -> {
                val length = text.codePointCount(0, text.length)
                if (length !in 8..71) { restart(); error = Error.PASSWORD; return null }
                if (registering) { secret = text.toCharArray(); stage = Stage.CONFIRMATION }
                else { stage = Stage.SUBMITTING; return Request(false, username, text) }
            }
            Stage.CONFIRMATION -> {
                val matches = secret?.let { chars -> chars.size == text.length && chars.indices.all { chars[it] == text[it] } } == true
                if (!matches) { restart(); error = Error.CONFIRMATION; return null }
                val password = String(secret!!); clearSecret(); stage = Stage.SUBMITTING
                return Request(true, username, password)
            }
            else -> Unit
        }
        return null
    }
}
