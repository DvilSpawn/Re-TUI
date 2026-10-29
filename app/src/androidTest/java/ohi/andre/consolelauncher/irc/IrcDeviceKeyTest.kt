package ohi.andre.consolelauncher.irc

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import javax.net.ssl.SSLSocket

/** Isolates only its fixture keys and restores the original binding byte-for-byte. */
class IrcDeviceKeyTest {
    @Test fun optInLiveTlsAcceptsDeviceCertificateAndAdvertisesExternalWithoutLogin() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("irc_tls_probe") == "true")
        val devices = IrcDeviceKey(InstrumentationRegistry.getInstrumentation().targetContext)
        // Any TLS network that offers SASL EXTERNAL, e.g. -e irc_tls_probe_host irc.libera.chat
        val host = InstrumentationRegistry.getArguments().getString("irc_tls_probe_host") ?: "irc.libera.chat"
        val identity = IrcDeviceKey.Identity(host, 6697, "probe" + System.nanoTime().toString().takeLast(8))
        try {
            devices.ensure(identity)
            (IrcClient.tlsSocket(identity.host, identity.port, devices.factory(identity)) as SSLSocket).use { socket ->
                assertTrue("Server requested and received the client certificate", !socket.session.localCertificates.isNullOrEmpty())
                socket.soTimeout = 10_000
                socket.getOutputStream().apply { write("CAP LS 302\r\n".toByteArray()); flush() }
                val input = socket.getInputStream()
                var external = false
                repeat(10) {
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) {
                        val byte = input.read(); check(byte >= 0)
                        if (byte == 10) break
                        if (byte != 13) bytes.write(byte)
                        check(bytes.size() <= 8192)
                    }
                    val message = IrcMessage.parse(bytes.toString("UTF-8"))
                    if (message.command == "CAP" && message.params.getOrNull(1) == "LS") {
                        external = external || message.params.last().split(' ').any { it.startsWith("sasl=") && "EXTERNAL" in it.substringAfter('=').split(',') }
                        if (message.params.getOrNull(2) != "*") { assertTrue("Native certificate login is advertised", external); return }
                    }
                }
                fail("Bounded capability reply missing")
            }
        } finally { devices.forget(identity) }
    }
    @Test fun keyIsNonExportableAccountBoundPersistentAndForgettable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val devices = IrcDeviceKey(context)
        val identity = IrcDeviceKey.Identity("irc.example.org", 16697, "fixture" + System.nanoTime().toString().takeLast(8))
        try {
            assertFalse(devices.has(identity))
            devices.ensure(identity); val fingerprint = devices.fingerprint(identity)
            assertEquals(64, fingerprint.length)
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(identity.alias, null) as PrivateKey
            assertNull("Private key bytes cannot be exported", key.encoded)
            assertTrue(IrcDeviceKey(context).has(identity)); assertEquals(fingerprint, IrcDeviceKey(context).fingerprint(identity))
            assertNotEquals(identity.alias, identity.copy(port = 6697).alias); assertNotEquals(identity.alias, identity.copy(account = "other").alias)
            assertNotNull(devices.factory(identity))
            devices.forget(identity); assertFalse(devices.has(identity))
        } finally { devices.forget(identity) }
    }
    @Test fun retiredServerKeyAndRecordAreRemovedOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val record = File(context.noBackupFilesDir, "irc-device.json")
        val original = record.takeIf { it.exists() }?.readBytes()
        val devices = IrcDeviceKey(context)
        val identity = IrcDeviceKey.Identity("irc.kokodoko.cloud", 16698, "fixture" + System.nanoTime().toString().takeLast(8))
        try {
            devices.ensure(identity)
            record.writeText("""{"host":"${identity.host}","port":${identity.port},"account":"${identity.account}"}""")
            assertTrue(devices.clearLegacy())
            assertFalse(record.exists()); assertFalse(devices.has(identity))
            assertFalse("Only once", devices.clearLegacy())
        } finally {
            devices.forget(identity)
            if (original != null) record.writeBytes(original) else record.delete()
        }
    }
}
