package ohi.andre.consolelauncher.managers.termux

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcraft.jsch.Cipher
import com.jcraft.jsch.ECDH
import com.jcraft.jsch.HASH
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.MAC
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** Runs on the oldest launcher API without installing a third-party crypto provider. */
@RunWith(AndroidJUnit4::class)
class TermuxWorkspaceSshCryptoTest {
    private fun implementation(name: String): Any = Class.forName(JSch.getConfig(name)).getDeclaredConstructor().newInstance()

    @Test fun platformP256IdentityRoundTripsAndRejectsUnpairedHosts() {
        val key = KeyPair.genKeyPair(JSch(), KeyPair.ECDSA, 256)
        try {
            val encoded = ByteArrayOutputStream().also { key.writePrivateKey(it) }.toByteArray()
            val restored = KeyPair.load(JSch(), encoded, null)
            try { assertArrayEquals(key.publicKeyBlob, restored.publicKeyBlob) } finally { restored.dispose() }
            val line = ByteArrayOutputStream().also { key.writePublicKey(it, "") }.toString("UTF-8").trim()
            val ssh = JSch()
            ssh.setKnownHosts(ByteArrayInputStream(TermuxWorkspaceSshConnection.knownHosts(8928, line).toByteArray()))
            assertEquals(HostKeyRepository.OK, ssh.hostKeyRepository.check("[127.0.0.1]:8928", key.publicKeyBlob))
            assertEquals(HostKeyRepository.NOT_INCLUDED, ssh.hostKeyRepository.check("[192.168.1.2]:8928", key.publicKeyBlob))
            val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            val payload = "Re:TUI platform signature".toByteArray()
            val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(payload) }.sign()
            assertTrue(Signature.getInstance("SHA256withECDSA").apply { initVerify(pair.public); update(payload) }.verify(signature))
        } finally { key.dispose() }
    }

    @Test fun configuredAlgorithmsWorkWithAndroidPlatformProviders() {
        val hash = implementation("sha-256") as HASH
        hash.init(); hash.update(byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(32, hash.digest().size)
        val cipher = implementation("aes128-ctr") as Cipher
        val plaintext = ByteArray(32) { it.toByte() }
        val encrypted = ByteArray(32)
        cipher.init(Cipher.ENCRYPT_MODE, ByteArray(16), ByteArray(16))
        cipher.update(plaintext, 0, plaintext.size, encrypted, 0)
        val decrypted = ByteArray(32)
        cipher.init(Cipher.DECRYPT_MODE, ByteArray(16), ByteArray(16))
        cipher.update(encrypted, 0, encrypted.size, decrypted, 0)
        assertArrayEquals(plaintext, decrypted)
        val mac = implementation("hmac-sha2-256") as MAC
        mac.init(ByteArray(32)); mac.update(plaintext, 0, plaintext.size)
        val tag = ByteArray(mac.blockSize); mac.doFinal(tag, 0)
        assertEquals(32, tag.size)
        val alice = (implementation("ecdh-sha2-nistp") as ECDH).apply { init(256) }
        val bob = (implementation("ecdh-sha2-nistp") as ECDH).apply { init(256) }
        fun secret(own: ECDH, peer: ECDH): ByteArray {
            val point = peer.q
            val x = point.copyOfRange(1, 33); val y = point.copyOfRange(33, 65)
            assertTrue(own.validate(x, y))
            return own.getSecret(x, y)
        }
        assertArrayEquals(secret(alice, bob), secret(bob, alice))
    }

    @Test fun gcmAuthenticatesConsecutivePacketsWithPlatformProvider() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(12) { (it + 17).toByte() }
        val encrypt = (implementation("aes128-gcm@openssh.com") as Cipher).apply {
            init(Cipher.ENCRYPT_MODE, key, iv.copyOf())
        }
        val decrypt = (implementation("aes128-gcm@openssh.com") as Cipher).apply {
            init(Cipher.DECRYPT_MODE, key, iv.copyOf())
        }
        val plain = ByteArray(32) { (it * 3).toByte() }
        val aad = byteArrayOf(0, 0, 0, plain.size.toByte())
        var previous: ByteArray? = null
        repeat(3) {
            // Match Session.encode/read: length is AAD; encryption/decryption overlap at offset4.
            val packet = ByteArray(4 + plain.size + encrypt.tagSize)
            aad.copyInto(packet)
            plain.copyInto(packet, 4)
            encrypt.updateAAD(packet, 0, 4)
            encrypt.doFinal(packet, 4, plain.size, packet, 4)
            previous?.let { old -> assertFalse(old.contentEquals(packet)) }
            previous = packet.copyOf()
            decrypt.updateAAD(packet, 0, 4)
            decrypt.doFinal(packet, 4, plain.size + decrypt.tagSize, packet, 4)
            assertArrayEquals(plain, packet.copyOfRange(4, 4 + plain.size))
        }
        val freshEncrypt = (implementation("aes128-gcm@openssh.com") as Cipher).apply {
            init(Cipher.ENCRYPT_MODE, key, iv.copyOf())
        }
        val encrypted = ByteArray(plain.size + freshEncrypt.tagSize)
        freshEncrypt.updateAAD(aad, 0, aad.size)
        freshEncrypt.doFinal(plain, 0, plain.size, encrypted, 0)
        encrypted[0] = (encrypted[0].toInt() xor 1).toByte()
        val reject = (implementation("aes128-gcm@openssh.com") as Cipher).apply {
            init(Cipher.DECRYPT_MODE, key, iv.copyOf())
        }
        reject.updateAAD(aad, 0, aad.size)
        try {
            reject.doFinal(encrypted, 0, encrypted.size, ByteArray(plain.size), 0)
            fail("Modified authenticated SSH packet was accepted")
        } catch (expected: javax.crypto.AEADBadTagException) {
            // Authentication failure must be surfaced, never decoded as terminal output.
        }
    }

}
