package ohi.andre.consolelauncher.profile

import android.content.Context
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
}
