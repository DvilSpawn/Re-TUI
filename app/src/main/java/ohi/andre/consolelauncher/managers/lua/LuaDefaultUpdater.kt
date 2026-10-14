package ohi.andre.consolelauncher.managers.lua

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Defaults are tracked independently of their editable scripts, including deliberate deletion. */
internal object LuaDefaultUpdater {
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun file() = AtomicFile(File(LuaWidgetManager.widgetsDir(), ".defaults.json"))
    private fun read(): JSONObject {
        val file = file()
        if (!file.baseFile.exists()) return JSONObject()
        return file.openRead().use { JSONObject(it.bufferedReader().readText()) }
    }
    private fun write(state: JSONObject) {
        val file = file()
        val out = file.startWrite()
        try { out.write(state.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch (error: Throwable) { file.failWrite(out); throw error }
    }
    @Synchronized fun deleted(id: String) {
        val state = read()
        if (state.has(id) || id == LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID || LuaWidgetManager.bundledSampleIds().contains(id)) {
            state.put(id, (state.optJSONObject(id) ?: JSONObject()).put("deleted", true))
            write(state)
        }
    }
    @Synchronized fun update(id: String, source: String, legacyHash: String? = null, save: (String) -> Unit) {
        val all = read()
        val state = all.optJSONObject(id)
        if (state?.optBoolean("deleted") == true) return
        val exists = LuaWidgetManager.exists(id)
        var current = LuaWidgetManager.readScript(id).orEmpty()
        val next = hash(source)
        var baseline = state?.optString("hash", "")?.takeIf { it.isNotEmpty() } ?: legacyHash
        val pending = state?.optString("pending", "")?.takeIf { it.isNotEmpty() }
        if (!exists && state != null && pending == null) {
            all.put(id, state.put("deleted", true)); write(all); return
        }
        if (pending != null && exists && hash(current) == pending) {
            // Complete a source write interrupted before manifest/tracking finalization.
            save(current)
            baseline = pending
            all.put(id, JSONObject().put("hash", pending)); write(all)
        }
        if (exists && baseline == null) {
            if (hash(current) == next) { all.put(id, JSONObject().put("hash", next)); write(all) }
            return // Untracked existing copies belong to the user.
        }
        if (exists && hash(current) != baseline) {
            if (pending != null) { all.put(id, JSONObject().put("hash", baseline)); write(all) }
            return
        }
        if (exists && baseline == next) return
        all.put(id, JSONObject().put("hash", baseline ?: "").put("pending", next)); write(all)
        save(source)
        all.put(id, JSONObject().put("hash", next)); write(all)
    }
}
