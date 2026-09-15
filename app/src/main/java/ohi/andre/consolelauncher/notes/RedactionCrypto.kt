package ohi.andre.consolelauncher.notes


import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class RedactionCrypto {
    private val random = SecureRandom()

    fun encrypt(plaintext: String): String {
        val bytes = plaintext.toByteArray(StandardCharsets.UTF_8)
        var paddedSize = 32
        while (paddedSize < bytes.size + 4) paddedSize *= 2
        val padded = ByteArray(paddedSize).also(random::nextBytes)
        ByteBuffer.wrap(padded).putInt(bytes.size).put(bytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val payload = cipher.iv + cipher.doFinal(padded)
        val encoded = Base64.encodeToString(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return RedactionFormat.encoded(displayWidth(plaintext), encoded)
    }

    fun decryptMarkdown(markdown: String): String = RedactionFormat.token.replace(markdown) { match ->
        decryptPayload(match.groupValues[2])
    }

    private fun decryptPayload(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        require(payload.size > IV_SIZE + 16) { "Invalid redaction payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_SIZE))
        }
        val clear = cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE)
        val length = ByteBuffer.wrap(clear).int
        require(length in 0..clear.size - 4) { "Invalid redaction length" }
        return String(clear, 4, length, StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(
                60,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(60)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(builder.build()) }.generateKey()
    }

    private fun displayWidth(value: String) = when {
        value.length <= 8 -> 8
        value.length <= 16 -> 16
        value.length <= 24 -> 24
        value.length <= 48 -> 48
        else -> 64
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "remember-redaction-v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
