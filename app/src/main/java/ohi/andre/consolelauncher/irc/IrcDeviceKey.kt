package ohi.andre.consolelauncher.irc

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Principal
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

/**
 * One non-exportable client certificate per network account (IRC "CertFP"). Which accounts use one is
 * recorded on the saved network (IrcNetwork.device); this class only holds the keys.
 */
class IrcDeviceKey(context: Context) {
    data class Identity(val host: String, val port: Int, val account: String) {
        val alias get() = "retui-irc-" + digest("${host.lowercase(Locale.ROOT)}:$port\u0000${account.lowercase(Locale.ROOT)}".toByteArray())
    }
    // Written by builds before multi-network IRC, for the retired Re:TUI server only.
    private val legacyRecord = AtomicFile(File(context.noBackupFilesDir, "irc-device.json"))
    companion object {
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun has(identity: Identity): Boolean = runCatching { store().containsAlias(identity.alias) }.getOrDefault(false)
    fun ensure(identity: Identity) {
        val keys = store()
        if (keys.containsAlias(identity.alias)) {
            if (keys.getCertificate(identity.alias).publicKey.algorithm == "EC") return
            // Replace only an unconfirmed draft key from the initial RSA implementation.
            keys.deleteEntry(identity.alias)
        }
        val now = System.currentTimeMillis()
        val specification = KeyGenParameterSpec.Builder(identity.alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            // Conscrypt supplies the already-hashed TLS handshake to NONEwithECDSA.
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=ReTUI IRC device"))
            .setCertificateSerialNumber(BigInteger(128, java.security.SecureRandom()))
            .setCertificateNotBefore(Date(now - 86_400_000)).setCertificateNotAfter(Date(now + 315_360_000_000L)).build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply { initialize(specification) }.generateKeyPair()
    }
    fun fingerprint(identity: Identity): String = digest(store().getCertificate(identity.alias).encoded)
    fun forget(identity: Identity) {
        val keys = store(); if (keys.containsAlias(identity.alias)) keys.deleteEntry(identity.alias)
    }
    /** Removes the retired server's device key and record. Returns whether one existed. */
    fun clearLegacy(): Boolean {
        val identity = runCatching {
            val data = legacyRecord.openRead().use { JSONObject(String(it.readBytes(), Charsets.UTF_8)) }
            Identity(data.getString("host"), data.getInt("port"), data.getString("account"))
        }.getOrNull() ?: return false
        runCatching { forget(identity) }
        legacyRecord.delete()
        return true
    }
    fun factory(identity: Identity): SSLSocketFactory {
        val keys = store(); check(keys.containsAlias(identity.alias))
        val algorithm = keys.getCertificate(identity.alias).publicKey.algorithm
        val manager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = if (keyType == algorithm) arrayOf(identity.alias) else null
            override fun chooseClientAlias(keyTypes: Array<String>?, issuers: Array<Principal>?, socket: Socket?): String? = if (keyTypes?.contains(algorithm) == true) identity.alias else null
            override fun getServerAliases(keyType: String?, issuers: Array<Principal>?): Array<String>? = null
            override fun chooseServerAlias(keyType: String?, issuers: Array<Principal>?, socket: Socket?): String? = null
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == identity.alias) keys.getCertificateChain(alias).map { it as X509Certificate }.toTypedArray() else null
            override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == identity.alias) keys.getKey(alias, null) as PrivateKey else null
        }
        // Default system trust managers remain in force; only the client identity changes.
        return SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, null) }.socketFactory
    }
}
