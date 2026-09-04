package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserManager
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.tuils.GenericFileProvider

object KeyboardShortcutManager {
    const val ACTION_RUN = "com.dvil.tui_renewed.action.RUN_KEYBOARD_SHORTCUT"
    const val EXTRA_ID = "keyboard_shortcut_id"
    const val EXTRA_TOKEN = "keyboard_shortcut_token"
    const val BUNDLE_KEY = "keyboard_shortcuts_json"
    const val MAX_PER_KEY = 2

    private const val PREFS = "retui_keyboard_shortcuts"
    private const val STATE = "state"
    private const val KEYBOARD_PACKAGE = "com.dvil.retui.keyboard"
    private const val ACTION_APP = "app"
    private const val VERSION = 1
    private const val ICON_SIZE = 64
    private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{16,256}")
    private val ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")

    internal data class Mapping(
        val id: String,
        val label: String,
        val appIdentity: String,
        val action: String = ACTION_APP
    )

    internal data class State(
        val token: String,
        val keys: LinkedHashMap<Char, MutableList<Mapping>> = linkedMapOf()
    )

    internal fun mappings(context: Context, key: Char): List<Mapping> =
        if (key in 'a'..'z') load(context).keys[key].orEmpty() else emptyList()

    fun save(context: Context, key: Char, slot: Int, app: LaunchInfo): Boolean {
        if (key !in 'a'..'z' || slot !in 0 until MAX_PER_KEY) return false
        val identity = runCatching { app.write() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
        val label = cleanLabel(app.publicLabel)
        val state = load(context)
        val items = state.keys.getOrPut(key) { mutableListOf() }
        val mapping = if (slot < items.size) {
            items[slot].copy(label = label, appIdentity = identity)
        } else {
            Mapping(newId(state), label, identity)
        }
        if (slot < items.size) items[slot] = mapping else items.add(mapping)
        iconFile(context, mapping.id).delete()
        persist(context, state)
        return true
    }

    fun clear(context: Context, key: Char, slot: Int): Boolean {
        if (key !in 'a'..'z' || slot !in 0 until MAX_PER_KEY) return false
        val state = load(context)
        val items = state.keys[key] ?: return false
        if (slot >= items.size) return false
        val removed = items[slot]
        if (!remove(state, key, slot)) return false
        iconFile(context, removed.id).delete()
        persist(context, state)
        return true
    }

    internal fun resolve(context: Context, token: String?, id: String?): Mapping? =
        authorize(load(context), token, id)

    fun keyboardJson(context: Context): String {
        val state = load(context)
        return keyboardJson(state) { mapping -> iconUri(context, mapping)?.toString() }
    }

    internal fun add(state: State, key: Char, mapping: Mapping): Boolean {
        if (key !in 'a'..'z' || !validMapping(mapping)) return false
        val items = state.keys.getOrPut(key) { mutableListOf() }
        if (items.size >= MAX_PER_KEY || state.keys.values.flatten().any { it.id == mapping.id }) return false
        items.add(mapping)
        return true
    }

    internal fun remove(state: State, key: Char, slot: Int): Boolean {
        val items = state.keys[key] ?: return false
        if (slot !in items.indices) return false
        items.removeAt(slot)
        if (items.isEmpty()) state.keys.remove(key)
        return true
    }

    internal fun authorize(state: State, token: String?, id: String?): Mapping? {
        if (token == null || id == null || !TOKEN_PATTERN.matches(token) || !ID_PATTERN.matches(id)) return null
        if (!MessageDigest.isEqual(
                state.token.toByteArray(StandardCharsets.US_ASCII),
                token.toByteArray(StandardCharsets.US_ASCII)
            )) return null
        return state.keys.values.asSequence().flatten().firstOrNull { it.id == id && it.action == ACTION_APP }
    }

    internal fun storedJson(state: State): String {
        val keys = JSONObject()
        state.keys.toSortedMap().forEach { (key, mappings) ->
            val array = JSONArray()
            mappings.forEach { mapping ->
                array.put(JSONObject().apply {
                    put("id", mapping.id)
                    put("label", mapping.label)
                    put("action", mapping.action)
                    put("app", mapping.appIdentity)
                })
            }
            if (array.length() > 0) keys.put(key.toString(), array)
        }
        return JSONObject().apply {
            put("version", VERSION)
            put("token", state.token)
            put("keys", keys)
        }.toString()
    }

    internal fun parseStoredJson(raw: String?): State? = runCatching {
        if (raw.isNullOrBlank() || raw.length > 64 * 1024) return@runCatching null
        val root = JSONObject(raw)
        require(root.getInt("version") == VERSION)
        val token = root.getString("token")
        require(TOKEN_PATTERN.matches(token))
        val state = State(token)
        val keys = root.getJSONObject("keys")
        val names = keys.keys()
        while (names.hasNext()) {
            val name = names.next()
            require(name.length == 1 && name[0] in 'a'..'z')
            val array = keys.getJSONArray(name)
            require(array.length() <= MAX_PER_KEY)
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                require(add(state, name[0], Mapping(
                    item.getString("id"),
                    item.getString("label"),
                    item.getString("app"),
                    item.getString("action")
                )))
            }
        }
        state
    }.getOrNull()

    internal fun keyboardJson(state: State, iconUri: (Mapping) -> String?): String {
        val keys = JSONObject()
        state.keys.toSortedMap().forEach { (key, mappings) ->
            val array = JSONArray()
            mappings.take(MAX_PER_KEY).forEach { mapping ->
                val uri = iconUri(mapping) ?: return@forEach
                array.put(JSONObject().apply {
                    put("id", mapping.id)
                    put("label", mapping.label)
                    put("icon_uri", uri)
                })
            }
            if (array.length() > 0) keys.put(key.toString(), array)
        }
        return JSONObject().apply {
            put("version", VERSION)
            put("token", state.token)
            put("keys", keys)
        }.toString()
    }

    private fun load(context: Context): State {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        parseStoredJson(prefs.getString(STATE, null))?.let { return it }
        return State(randomToken()).also { prefs.edit().putString(STATE, storedJson(it)).apply() }
    }

    private fun persist(context: Context, state: State) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(STATE, storedJson(state)).apply()
    }

    private fun newId(state: State): String {
        val used = state.keys.values.flatten().mapTo(HashSet()) { it.id }
        while (true) {
            val id = "app:${randomBase64(18)}"
            if (id !in used) return id
        }
    }

    private fun randomToken(): String = randomBase64(32)

    private fun randomBase64(bytes: Int): String = ByteArray(bytes).also(SecureRandom()::nextBytes).let {
        Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun cleanLabel(label: String?): String = label.orEmpty().trim()
        .filterNot(Char::isISOControl).take(64).ifEmpty { "App" }

    private fun validMapping(mapping: Mapping): Boolean =
        ID_PATTERN.matches(mapping.id) && mapping.action == ACTION_APP &&
            mapping.label.isNotBlank() && mapping.label.length <= 64 && !mapping.label.any(Char::isISOControl) &&
            mapping.appIdentity.isNotBlank() && mapping.appIdentity.length <= 1024

    private fun iconUri(context: Context, mapping: Mapping): Uri? = runCatching {
        val file = iconFile(context, mapping.id)
        if (!file.isFile) {
            val drawable = loadIcon(context, mapping) ?: return null
            val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = minOf(ICON_SIZE.toFloat() / width, ICON_SIZE.toFloat() / height)
            val drawnWidth = (width * scale).toInt().coerceAtLeast(1)
            val drawnHeight = (height * scale).toInt().coerceAtLeast(1)
            val left = (ICON_SIZE - drawnWidth) / 2
            val top = (ICON_SIZE - drawnHeight) / 2
            drawable.setBounds(left, top, left + drawnWidth, top + drawnHeight)
            try {
                drawable.draw(Canvas(bitmap))
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
        val uri = FileProvider.getUriForFile(context, GenericFileProvider.PROVIDER_NAME, file)
        context.grantUriPermission(KEYBOARD_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uri
    }.getOrNull()

    private fun iconFile(context: Context, id: String): File =
        File(File(context.cacheDir, "keyboard-shortcuts"), id.substringAfter(':') + ".png")

    private fun loadIcon(context: Context, mapping: Mapping): Drawable? {
        val identity = LaunchInfo.identityInfo(mapping.appIdentity) ?: return null
        val users = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val profile = users?.userProfiles?.firstOrNull {
            runCatching { users.getSerialNumberForUser(it) }.getOrDefault(-1L) == identity.profileSerial
        }
        if (profile != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            launcherApps?.getActivityList(identity.componentName?.packageName, profile)
                ?.firstOrNull { it.componentName == identity.componentName }
                ?.getBadgedIcon(0)?.let { return it }
        }
        return runCatching { context.packageManager.getActivityIcon(identity.componentName!!) }.getOrNull()
    }
}
