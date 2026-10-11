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
        val identity = IrcDeviceKey.Identity("irc.kokodoko.cloud", 6697, "probe" + System.nanoTime().toString().takeLast(8))
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
    @Test fun keyIsNonExportableNetworkBoundPersistentAndForgettableWithoutPasswordStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val record = File(context.noBackupFilesDir, "irc-device.json")
        val original = record.takeIf { it.exists() }?.readBytes()
        val devices = IrcDeviceKey(context)
        val identity = IrcDeviceKey.Identity("irc.kokodoko.cloud", 16697, "fixture" + System.nanoTime().toString().takeLast(8))
        try {
            devices.ensure(identity); val fingerprint = devices.fingerprint(identity)
            assertEquals(64, fingerprint.length)
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(identity.alias, null) as PrivateKey
            assertNull("Private key bytes cannot be exported", key.encoded)
            devices.remember(identity)
            assertEquals(identity, IrcDeviceKey(context).remembered()); assertEquals(fingerprint, IrcDeviceKey(context).fingerprint(identity))
            assertNotEquals(identity.alias, identity.copy(port = 6697).alias); assertNotEquals(identity.alias, identity.copy(account = "other").alias)
            assertFalse(record.readText().contains("password")); assertFalse(record.readText().contains("PRIVATE KEY"))
            assertNotNull(devices.factory(identity))
            devices.forget(identity); assertNull(devices.remembered())
            assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(identity.alias))
        } finally {
            devices.forget(identity)
            if (original != null) record.writeBytes(original) else record.delete()
        }
    }
}
