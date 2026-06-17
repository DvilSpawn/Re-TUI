package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import java.util.Collections
import java.util.ArrayList
import java.util.Locale

object PinnedShortcutManager {
    const val HANDLE_PREFIX: String = "@"

    const val PREFS = "pinned_shortcuts"
    private const val KEY_PREFIX = "shortcut."

    fun normalizeHandle(handle: String?): String {
        var handle = handle
        if (handle == null) return Tuils.EMPTYSTRING
        handle = handle.trim { it <= ' ' }
        if (handle.startsWith(HANDLE_PREFIX)) {
            handle = handle.substring(HANDLE_PREFIX.length)
        }
        val lower = handle.lowercase()
        val builder = StringBuilder()
        for (count in 0..<lower.length) {
            val c = lower.get(count)
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-') {
                builder.append(c)
            }
        }
        return builder.toString()
    }

    fun defaultHandle(label: CharSequence?): String {
        val value = if (label == null) Tuils.EMPTYSTRING else label.toString().trim { it <= ' ' }
            .lowercase()
        val builder = StringBuilder()
        var separatorPending = false
        for (count in 0..<value.length) {
            val c = value.get(count)
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (separatorPending && builder.length > 0) {
                    builder.append('-')
                }
                builder.append(c)
                separatorPending = false
            } else if (builder.length > 0) {
                separatorPending = true
            }
        }
        val handle = normalizeHandle(builder.toString())
        return if (handle.length == 0) "shortcut" else handle
    }

    @Throws(Exception::class)
    fun save(context: Context?, handle: String?, info: ShortcutInfo?, label: CharSequence?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        if (context == null || info == null) return
        val normalized = normalizeHandle(handle)
        if (normalized.length == 0) return

        val record = Record()
        record.handle = normalized
        record.packageName = info.getPackage()
        record.shortcutId = info.getId()
        record.profileSerial = profileSerial(context, info.getUserHandle())
        record.label = if (label == null) safeLabel(info) else label.toString()

        prefs(context)!!.edit()
            .putString(key(normalized), record.toJson().toString())
            .apply()
    }

    fun find(context: Context?, handle: String?): Record? {
        if (context == null) return null
        val normalized = normalizeHandle(handle)
        if (normalized.length == 0) return null
        val raw = prefs(context)!!.getString(key(normalized), null)
        return Record.Companion.fromJson(raw)
    }

    fun list(context: Context?): MutableList<Record?> {
        val records = ArrayList<Record?>()
        if (context == null) return records
        for (value in prefs(context)!!.getAll().values) {
            if (value !is String) continue
            val record: Record? = Record.Companion.fromJson(value)
            if (record != null) records.add(record)
        }
        Collections.sort<Record?>(
            records,
            Comparator { a: Record?, b: Record? -> a!!.handle!!.compareTo(b!!.handle!!) })
        return records
    }

    fun start(context: Context?, handle: String?): String? {
        val record = find(context, handle)
        if (record == null) return "Pinned shortcut not found: " + handle
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return "Pinned shortcuts require Android 7.1+"

        val apps = context!!.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps?
        if (apps == null) return "Shortcut service unavailable"

        val info = PinnedShortcutManager.resolve(context, apps, record)
        if (info == null) return "Pinned shortcut unavailable: @" + record.handle

        try {
            apps.startShortcut(info, null, null)
            return null
        } catch (e: Exception) {
            Tuils.log(e)
            return "Unable to launch @" + record.handle + ": " + e.message
        }
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun resolve(context: Context, apps: LauncherApps, record: Record): ShortcutInfo? {
        var profiles: MutableList<UserHandle>
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                profiles = apps.getProfiles()
            } else {
                profiles = mutableListOf(Process.myUserHandle())
            }
        } catch (e: Exception) {
            profiles = mutableListOf(Process.myUserHandle())
        }

        for (profile in profiles) {
            if (profileSerial(context, profile) != record.profileSerial) continue
            val info = findShortcut(apps, record, profile)
            if (info != null) return info
        }

        for (profile in profiles) {
            val info = findShortcut(apps, record, profile)
            if (info != null) return info
        }

        return null
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun findShortcut(
        apps: LauncherApps,
        record: Record,
        profile: UserHandle
    ): ShortcutInfo? {
        try {
            val query = ShortcutQuery()
            query.setPackage(record.packageName)
            query.setShortcutIds(mutableListOf<String?>(record.shortcutId))
            query.setQueryFlags(
                (ShortcutQuery.FLAG_MATCH_MANIFEST
                        or ShortcutQuery.FLAG_MATCH_DYNAMIC
                        or ShortcutQuery.FLAG_MATCH_PINNED)
            )
            val shortcuts = apps.getShortcuts(query, profile)
            if (shortcuts == null) return null
            for (info in shortcuts) {
                if (record.shortcutId == info.getId()) return info
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
        return null
    }

    private fun prefs(context: Context): SharedPreferences? {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun key(handle: String): String {
        return KEY_PREFIX + handle
    }

    private fun profileSerial(context: Context, profile: UserHandle?): Long {
        if (profile == null) return 0L
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager?
        if (userManager != null) {
            try {
                val serial = userManager.getSerialNumberForUser(profile)
                if (serial >= 0L) return serial
            } catch (e: Throwable) {
            }
        }
        return if (profile == Process.myUserHandle()) 0L else Integer.toUnsignedLong(profile.hashCode())
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun safeLabel(info: ShortcutInfo): String {
        val shortLabel = info.getShortLabel()
        if (shortLabel != null && shortLabel.length > 0) return shortLabel.toString()
        return info.getId()
    }

    class Record {
        var handle: String? = null
        var packageName: String? = null
        var shortcutId: String? = null
        var profileSerial: Long = 0
        var label: String? = null

        @Throws(Exception::class)
        fun toJson(): JSONObject {
            val `object` = JSONObject()
            `object`.put("handle", handle)
            `object`.put("package", packageName)
            `object`.put("shortcutId", shortcutId)
            `object`.put("profileSerial", profileSerial)
            `object`.put("label", label)
            return `object`
        }

        companion object {
            fun fromJson(raw: String?): Record? {
                if (raw == null || raw.trim { it <= ' ' }.length == 0) return null
                try {
                    val `object` = JSONObject(raw)
                    val record = Record()
                    record.handle = `object`.optString("handle", Tuils.EMPTYSTRING)
                    record.packageName = `object`.optString("package", Tuils.EMPTYSTRING)
                    record.shortcutId = `object`.optString("shortcutId", Tuils.EMPTYSTRING)
                    record.profileSerial = `object`.optLong("profileSerial", 0L)
                    record.label = `object`.optString("label", record.shortcutId)
                    if (record.handle!!.length == 0 || record.packageName!!.length == 0 || record.shortcutId!!.length == 0) {
                        return null
                    }
                    return record
                } catch (e: Exception) {
                    return null
                }
            }
        }
    }
}
