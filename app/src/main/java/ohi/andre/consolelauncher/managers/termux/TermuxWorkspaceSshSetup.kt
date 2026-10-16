package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.util.Base64
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.Properties

/** Pair only through Termux's Android permission boundary, never through an untrusted SSH reply. */
object TermuxWorkspaceSshSetup {
    data class ConnectionInfo(val username: String, val port: Int, val hostKey: String, val privateKey: File)

    private fun directory(context: Context) = File(context.noBackupFilesDir, "termux-workspace-ssh").apply {
        check(isDirectory || mkdirs()) { "Could not create private Termux pairing storage" }
    }
    private fun keyFile(context: Context) = File(directory(context), "identity")
    private fun infoFile(context: Context) = File(directory(context), "connection.properties")

    @Synchronized
    fun isPaired(context: Context): Boolean = keyFile(context).let { it.isFile && it.length() > 0 } &&
        infoFile(context).let { it.isFile && it.length() > 0 }

    @Synchronized
    fun commandArguments(context: Context, install: Boolean): Array<String?> {
        val publicKey = ensureIdentity(directory(context))
        val setup = context.assets.open("termux-ssh/setup.sh").bufferedReader().use { it.readText() }
        val dispatcher = context.assets.open("termux-ssh/dispatch.sh").use { it.readBytes() }
        return arrayOf("-c", setup, "retui-ssh-setup", publicKey, if (install) "1" else "0",
            Base64.encodeToString(dispatcher, Base64.NO_WRAP))
    }

    fun daemonArguments(context: Context): Array<String?> = arrayOf("-c",
        context.assets.open("termux-ssh/daemon.sh").bufferedReader().use { it.readText() }, "retui-sshd")

    @Synchronized
    fun parse(context: Context, output: String): ConnectionInfo {
        val markers = readMarkers(output)
        fun marker(name: String) = markers[name]
        val username = requireNotNull(marker("__RETUI_SSH_USER__")) { "Termux did not return an SSH username" }
        require(username.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Termux username" }
        val port = marker("__RETUI_SSH_PORT__")?.toIntOrNull()
        require(port != null && port in 1024..65535) { "Invalid Termux SSH port" }
        val hostKey = String(Base64.decode(requireNotNull(marker("__RETUI_SSH_HOST_KEY__")), Base64.DEFAULT), Charsets.UTF_8).trim()
        require(hostKey.matches(Regex("ecdsa-sha2-nistp256 [A-Za-z0-9+/]+={0,2}"))) { "Invalid Termux host key" }
        val saved = Properties()
        val info = infoFile(context)
        if (info.isFile) {
            try { info.inputStream().use { saved.load(it) } }
            catch (_: Exception) { throw IllegalStateException("Termux pairing is damaged. Use Repair connection.") }
        }
        val previous = saved.getProperty("hostKey")
        if (info.isFile && (previous.isNullOrBlank() || saved.getProperty("username").isNullOrBlank() ||
                saved.getProperty("port")?.toIntOrNull()?.let { it in 1024..65535 } != true))
            throw IllegalStateException("Termux pairing is damaged. Use Repair connection.")
        if (previous != null && previous != hostKey)
            throw SecurityException("Termux host key changed. Use Repair connection to pair again.")
        require(keyFile(context).isFile) { "Missing Re:TUI SSH identity. Use Repair connection." }
        saved.setProperty("username", username); saved.setProperty("port", port.toString()); saved.setProperty("hostKey", hostKey)
        atomicWrite(info) { saved.store(it, null) }
        return ConnectionInfo(username, port, hostKey, keyFile(context))
    }

    /** Rebuild the public half from the durable private key after an interrupted first setup. */
    internal fun ensureIdentity(directory: File): String {
        val key = File(directory, "identity")
        if (!key.isFile) {
            val generated = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
            try { atomicWrite(key) { generated.writePrivateKey(it) } } finally { generated.dispose() }
        }
        val pair = try { KeyPair.load(JSch(), key.absolutePath) }
        catch (_: Exception) { throw IllegalStateException("Termux pairing is damaged. Use Repair connection.") }
        try {
            check(pair.keyType == KeyPair.ECDSA && pair.keySize == 256 && !pair.isEncrypted) {
                "Termux pairing is damaged. Use Repair connection."
            }
            return ByteArrayOutputStream().also { pair.writePublicKey(it, "retui-workspace") }
                .toString("UTF-8").trim()
        } finally { pair.dispose() }
    }

    internal fun atomicWrite(file: File, write: (FileOutputStream) -> Unit) {
        val pending = File(file.parentFile, file.name + ".pending")
        try {
            FileOutputStream(pending).use {
                check(pending.setReadable(false, false) && pending.setWritable(false, false) &&
                    pending.setReadable(true, true) && pending.setWritable(true, true)) {
                    "Could not protect Termux pairing storage"
                }
                write(it)
                it.fd.sync()
            }
            check(pending.renameTo(file)) { "Could not save Termux pairing" }
        } finally { pending.delete() }
    }

    internal fun readMarkers(output: String): Map<String, String> {
        require(output.length <= 1024 * 1024) { "Termux setup output is too large" }
        val values = linkedMapOf<String, String>()
        val expected = setOf("__RETUI_SSH_USER__", "__RETUI_SSH_PORT__", "__RETUI_SSH_HOST_KEY__")
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            val name = line.substringBefore(' ')
            if (name == "__RETUI_SSH_ERROR__") throw IllegalStateException(line.substringAfter(' ', "Termux setup failed"))
            if (name in expected) {
                require(name !in values && line.contains(' ') && line.substringAfter(' ').isNotBlank()) {
                    "Termux returned ambiguous pairing information"
                }
                values[name] = line.substringAfter(' ').trim()
            }
        }
        require(values.keys == expected) { "Termux returned incomplete pairing information" }
        return values
    }

    @Synchronized
    fun clearPairing(context: Context) {
        directory(context).listFiles()?.forEach { check(it.delete()) { "Could not remove Termux pairing" } }
    }
}
