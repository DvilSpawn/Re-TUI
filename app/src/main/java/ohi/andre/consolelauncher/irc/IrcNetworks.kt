package ohi.andre.consolelauncher.irc

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * A saved IRC network. Passwords are never stored: an account reconnects without one only through a
 * non-exportable device key (IrcDeviceKey), recorded here as [device].
 */
data class IrcNetwork(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 6697,
    val nickname: String = "",
    val account: String = "",
    val device: Boolean = false,
    // Channels to rejoin, kept in step with the user's own joins and parts.
    val channels: List<String> = emptyList(),
    // Reconnect when the pane opens; cleared when the user disconnects.
    val connectOnOpen: Boolean = false
) {
    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("host", host).put("port", port)
        .put("nickname", nickname).put("account", account).put("device", device)
        .put("channels", JSONArray(channels)).put("connectOnOpen", connectOnOpen)

    companion object {
        const val MAX_CHANNELS = 20
        val PRESETS = listOf(
            IrcNetwork("libera", "Libera.Chat", "irc.libera.chat"),
            IrcNetwork("oftc", "OFTC", "irc.oftc.net"),
            IrcNetwork("rizon", "Rizon", "irc.rizon.net"),
            IrcNetwork("hackint", "hackint", "irc.hackint.org")
        )

        fun validHost(value: String) = value.length in 1..253 && value.matches(Regex("[A-Za-z0-9.:-]+"))

        /** Guest nicknames must differ between people who never chose one. */
        fun guestNickname() = "retui" + (1000..9999).random()

        fun parse(data: JSONObject): IrcNetwork? = runCatching {
            val channels = data.optJSONArray("channels")?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty()
            IrcNetwork(data.getString("id"), data.getString("name"), data.getString("host"), data.optInt("port", 6697),
                data.optString("nickname"), data.optString("account"), data.optBoolean("device"),
                channels.filter { IrcProtocol.target(it) && it.first() in "#&" }.take(MAX_CHANNELS), data.optBoolean("connectOnOpen"))
        }.getOrNull()?.takeIf {
            it.id.isNotBlank() && it.name.isNotBlank() && validHost(it.host) && it.port in 1..65535 &&
                (it.nickname.isEmpty() || IrcProtocol.nick(it.nickname)) && (it.account.isEmpty() || IrcProtocol.nick(it.account))
        }

        fun custom(host: String, port: Int, taken: Collection<String>): IrcNetwork {
            val base = host.lowercase(Locale.ROOT)
            var id = base; var n = 2
            while (id in taken) id = "$base-${n++}"
            return IrcNetwork(id, host, host, port)
        }
    }
}

/** Network list in no-backup storage: a restored device flag would point at a key the new phone lacks. */
class IrcNetworkStore(private val file: File) {
    private val record = AtomicFile(file)

    fun load(): List<IrcNetwork> = runCatching {
        val list = record.openRead().use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }.getJSONArray("networks")
        (0 until list.length()).mapNotNull { IrcNetwork.parse(list.getJSONObject(it)) }.distinctBy { it.id }
    }.getOrDefault(emptyList())

    fun save(networks: List<IrcNetwork>) {
        val bytes = JSONObject().put("networks", JSONArray(networks.map { it.json() })).toString().toByteArray(Charsets.UTF_8)
        val stream = record.startWrite()
        try { stream.write(bytes); record.finishWrite(stream) } catch (error: Exception) { record.failWrite(stream); throw error }
    }
}
