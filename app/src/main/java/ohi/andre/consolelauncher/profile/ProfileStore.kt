package ohi.andre.consolelauncher.profile

import android.content.Context
import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class ProfileQr(val label: String, val value: String)

data class LocalProfile(
    val name: String,
    val phone: String,
    val photoUri: String,
    val codes: List<ProfileQr>
)

object ProfileStore {
    private const val PREFS = "retui_profile"
    private const val NAME = "name"
    private const val PHONE = "phone"
    private const val PHOTO = "photo"
    private const val CODES = "codes"

    fun load(context: Context): LocalProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val codes = ArrayList<ProfileQr>()
        val array = runCatching { JSONArray(prefs.getString(CODES, "[]")) }.getOrDefault(JSONArray())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val label = item.optString("label").trim()
            val value = item.optString("value").trim()
            if (label.isNotEmpty() && value.isNotEmpty()) codes.add(ProfileQr(label, value))
        }
        return LocalProfile(
            prefs.getString(NAME, "OPERATOR") ?: "OPERATOR",
            prefs.getString(PHONE, "") ?: "",
            prefs.getString(PHOTO, "") ?: "",
            codes
        )
    }

    fun save(context: Context, profile: LocalProfile) {
        val codes = JSONArray()
        profile.codes.forEach { code ->
            codes.put(JSONObject().put("label", code.label).put("value", code.value))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(NAME, profile.name.trim().ifEmpty { "OPERATOR" })
            .putString(PHONE, profile.phone.trim())
            .putString(PHOTO, profile.photoUri)
            .putString(CODES, codes.toString())
            .apply()
    }

    fun maskedPhone(phone: String): String {
        val visible = phone.takeLast(2)
        return if (phone.isEmpty()) "NOT SET" else "*".repeat((phone.length - visible.length).coerceAtLeast(5)) + visible
    }

    fun contactVCard(name: String, phone: String): String =
        "BEGIN:VCARD\r\n" +
            "VERSION:3.0\r\n" +
            "FN:${escapeVCard(name)}\r\n" +
            "TEL;TYPE=CELL:${escapeVCard(phone)}\r\n" +
            "END:VCARD"

    fun qrValueValidationError(value: String): String? {
        val host = urlHost(value) ?: return null
        if (YOUTUBE_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            return "YouTube links are not allowed in profile QR codes."
        }
        if (SHORTENER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
            return "Shortened links are not allowed. Use the full destination URL."
        }
        return null
    }

    private fun urlHost(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
        val candidate = if (SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        return runCatching { URI(candidate).host?.lowercase(Locale.ROOT)?.trimEnd('.') }.getOrNull()
    }

    private fun escapeVCard(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\r\n", "\\n")
        .replace('\r', '\n')
        .replace("\n", "\\n")
        .replace(";", "\\;")
        .replace(",", "\\,")

    private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)
    private val YOUTUBE_DOMAINS = setOf("youtube.com", "youtu.be", "youtube-nocookie.com")
    private val SHORTENER_DOMAINS = setOf(
        "aka.ms", "bit.ly", "bitly.com", "buff.ly", "cutt.ly", "goo.gl", "is.gd",
        "lnkd.in", "ow.ly", "rb.gy", "rebrand.ly", "s.id", "shorturl.at", "t.co",
        "tiny.cc", "tinyurl.com", "trib.al", "v.gd"
    )
}
