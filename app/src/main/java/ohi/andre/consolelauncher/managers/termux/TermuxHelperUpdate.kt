package ohi.andre.consolelauncher.managers.termux

import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Only the helper shipped with this APK is offered, never a remote "latest". */
object TermuxHelperUpdate {
    const val VERSION = "0.4.8"
    const val PROTOCOL = "retui-bridge-socket-v4"
    const val MAX_BYTES = 131072
    val probeScript = "export PATH='/data/data/com.termux/files/usr/bin':\$PATH; " +
        "if [ -d /data/data/com.termux/files/usr/share/retui/.update-lock ]; then printf '%s\\n' '__RETUI_UPDATE_BUSY__'; " +
        "elif command -v retui >/dev/null 2>&1 && [ \"\$(retui version)\" = '$VERSION' ]; then RETUI_NO_CREATE=1 retui bridge socket-start; " +
        "else printf '%s\\n' '__RETUI_UPDATE_REQUIRED__'; fi"
    fun checksum(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Invalid bundled helper size" }
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    @OptIn(ExperimentalEncodingApi::class)
    fun payload(bytes: ByteArray): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Invalid bundled helper size" }
        return Base64.encode(bytes)
    }
    fun succeeded(output: String, exitCode: Int) = exitCode == 0 &&
        output.trim() == "__RETUI_UPDATE_OK__ $VERSION"
}
