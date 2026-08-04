package ohi.andre.consolelauncher.managers.modules

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import ohi.andre.consolelauncher.managers.RssManager.Companion.firstConfiguredFeedId
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import java.util.Arrays
import java.util.ArrayList
import java.util.LinkedHashSet
import java.util.Locale
import java.util.Set
import ohi.andre.consolelauncher.managers.RssManager
import org.json.JSONArray
import org.json.JSONObject

object ModuleManager {
    const val MUSIC: String = "music"
    const val NOTIFICATIONS: String = "notifications"
    const val TIMER: String = "timer"
    const val CALENDAR: String = "calendar"
    const val EVENTS: String = "events"
    const val REMINDER: String = "reminder"
    const val NOTES: String = "notes"
    const val RSS: String = "rss"
    const val WEATHER: String = "weather"
    const val WEATHER_NATIVE: String = "weather_native"
    const val SOURCE_LAUNCHER_PREFIX: String = "launcher:"
    const val SOURCE_TERMUX_PREFIX: String = "termux:"
    const val SOURCE_LUA_PREFIX: String = "lua:"

    private const val PREFS = "retui_modules"
    private const val KEY_DOCK = "dock"
    private const val KEY_ACTIVE_MODULE = "active_module"
    private const val KEY_SCRIPT_IDS = "script_ids"
    private const val KEY_SCRIPT_PREFIX = "script_"
    private const val KEY_SCRIPT_PATH_PREFIX = "script_path_"
    private const val KEY_SCRIPT_TITLE_PREFIX = "script_title_"
    private const val KEY_SCRIPT_SUGGESTIONS_PREFIX = "script_suggestions_"
    private const val KEY_SCRIPT_SEGMENTS_PREFIX = "script_segments_"
    private val DEFAULT_DOCK: MutableList<String?> =
        Arrays.asList<String?>(MUSIC, NOTIFICATIONS, TIMER, CALENDAR, REMINDER)
    private val BUILT_INS: MutableList<String?> =
        Arrays.asList<String?>(MUSIC, NOTIFICATIONS, TIMER, CALENDAR, REMINDER, NOTES, RSS, WEATHER, WEATHER_NATIVE)

    val builtIns: MutableList<String?>
        get() = ArrayList<String?>(BUILT_INS)

    fun getDock(context: Context): MutableList<String?> {
        val raw = prefs(context).getString(KEY_DOCK, null)
        if (raw == null) {
            return ArrayList<String?>(DEFAULT_DOCK)
        }
        if (raw.trim { it <= ' ' }.length == 0) return ArrayList<String?>()
        return parseList(raw)
    }

    fun setDock(context: Context, modules: MutableList<String?>) {
        val valid = LinkedHashSet<String?>()
        for (module in modules) {
            val id = normalize(module)
            if (isKnown(context, id)) {
                valid.add(id)
            }
        }
        prefs(context).edit().putString(KEY_DOCK, TextUtils.join(",", valid)).apply()
    }

    fun addToDock(context: Context, modules: MutableList<String?>) {
        val dock = LinkedHashSet<String?>(getDock(context))
        for (module in modules) {
            val id = normalize(module)
            if (isKnown(context, id)) {
                dock.add(id)
            }
        }
        setDock(context, ArrayList<String?>(dock))
    }

    fun removeFromDock(context: Context, modules: MutableList<String?>) {
        val dock = LinkedHashSet<String?>(getDock(context))
        for (module in modules) {
            dock.remove(normalize(module))
        }
        setDock(context, ArrayList<String?>(dock))
    }

    fun setActiveModule(context: Context, module: String?) {
        prefs(context).edit().putString(KEY_ACTIVE_MODULE, normalize(module)).apply()
    }

    fun getActiveModule(context: Context): String {
        return normalize(prefs(context).getString(KEY_ACTIVE_MODULE, ""))
    }

    fun getActiveSuggestions(context: Context): MutableList<ModuleSuggestion?> {
        return getSuggestionsForModule(context, getActiveModule(context))
    }

    internal fun usesNativeWeatherControls(module: String?): Boolean =
        normalize(module) == WEATHER_NATIVE

    fun hideFromDock(context: Context, module: String?) {
        val id = normalize(module)
        val dock = getDock(context)
        dock.remove(id)
        setDock(context, dock)
    }

    fun isKnown(context: Context, module: String?): Boolean {
        val id = normalize(module)
        return BUILT_INS.contains(id) || getScriptIds(context).contains(id)
    }

    fun listAll(context: Context): MutableList<String?> {
        val all = LinkedHashSet<String?>(BUILT_INS)
        all.addAll(getScriptIds(context))
        return ArrayList<String?>(all)
    }

    fun setScriptText(context: Context, module: String?, text: String?) {
        val id = normalize(module)
        if (TextUtils.isEmpty(id)) {
            return
        }
        val payload = parseScriptPayload(text)
        val ids = LinkedHashSet<String?>(getScriptIds(context))
        ids.add(id)
        val editor = prefs(context).edit()
            .putStringSet(KEY_SCRIPT_IDS, ids)
            .putString(KEY_SCRIPT_PREFIX + id, payload.body.toString())
        if (TextUtils.isEmpty(payload.title)) {
            editor.remove(KEY_SCRIPT_TITLE_PREFIX + id)
        } else {
            editor.putString(KEY_SCRIPT_TITLE_PREFIX + id, payload.title)
        }
        val serializedSegments = serializeSegments(payload.segments)
        if (TextUtils.isEmpty(serializedSegments)) {
            editor.remove(KEY_SCRIPT_SEGMENTS_PREFIX + id)
        } else {
            editor.putString(KEY_SCRIPT_SEGMENTS_PREFIX + id, serializedSegments)
        }
        if (payload.suggestions.isEmpty()) {
            editor.remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id)
        } else {
            editor.putString(
                KEY_SCRIPT_SUGGESTIONS_PREFIX + id,
                serializeSuggestions(payload.suggestions)
            )
        }
        editor.apply()
    }

    fun setScriptModule(context: Context, module: String?, path: String?) {
        val id = normalize(module)
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(path)) {
            return
        }
        val ids = LinkedHashSet<String?>(getScriptIds(context))
        ids.add(id)
        val body = "No module output yet. Run module -refresh " + id
        prefs(context).edit()
            .putStringSet(KEY_SCRIPT_IDS, ids)
            .putString(KEY_SCRIPT_PATH_PREFIX + id, normalizeModuleSource(path))
            .putString(KEY_SCRIPT_PREFIX + id, body)
            .putString(
                KEY_SCRIPT_SEGMENTS_PREFIX + id,
                serializeSegments(mutableListOf(ModuleTextSegment(body, false)))
            )
            .remove(KEY_SCRIPT_TITLE_PREFIX + id)
            .remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id)
            .apply()
    }

    fun removeScriptModule(context: Context, module: String?) {
        val id = normalize(module)
        if (TextUtils.isEmpty(id) || BUILT_INS.contains(id)) {
            return
        }
        val ids = LinkedHashSet<String?>(getScriptIds(context))
        ids.remove(id)
        val dock = LinkedHashSet<String?>(getDock(context))
        dock.remove(id)
        val editor = prefs(context).edit()
            .putStringSet(KEY_SCRIPT_IDS, ids)
            .putString(KEY_DOCK, TextUtils.join(",", dock))
            .remove(KEY_SCRIPT_PREFIX + id)
            .remove(KEY_SCRIPT_PATH_PREFIX + id)
            .remove(KEY_SCRIPT_TITLE_PREFIX + id)
            .remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + id)
            .remove(KEY_SCRIPT_SEGMENTS_PREFIX + id)
        if (TextUtils.equals(getActiveModule(context), id)) {
            editor.putString(KEY_ACTIVE_MODULE, "")
        }
        editor.apply()
    }

    fun renameScriptModule(
        context: Context,
        oldModule: String?,
        newModule: String?,
        newSource: String?
    ) {
        val oldId = normalize(oldModule)
        val newId = normalize(newModule)
        if (TextUtils.isEmpty(oldId) || TextUtils.isEmpty(newId)
            || TextUtils.equals(oldId, newId)
            || BUILT_INS.contains(oldId)
            || BUILT_INS.contains(newId)
        ) {
            return
        }

        val store = prefs(context)
        val ids = LinkedHashSet<String?>(getScriptIds(context))
        if (!ids.remove(oldId) || ids.contains(newId)) {
            return
        }
        ids.add(newId)

        val dock = ArrayList<String?>()
        for (module in getDock(context)) {
            var id = normalize(module)
            if (TextUtils.equals(id, oldId)) {
                id = newId
            }
            if (!dock.contains(id)) {
                dock.add(id)
            }
        }

        val path = if (TextUtils.isEmpty(newSource)) getScriptPath(context, oldId) else newSource
        val body = store.getString(KEY_SCRIPT_PREFIX + oldId, null)
        val title = store.getString(KEY_SCRIPT_TITLE_PREFIX + oldId, null)
        val suggestions = store.getString(KEY_SCRIPT_SUGGESTIONS_PREFIX + oldId, null)
        val segments = store.getString(KEY_SCRIPT_SEGMENTS_PREFIX + oldId, null)

        val editor = store.edit()
            .putStringSet(KEY_SCRIPT_IDS, ids)
            .putString(KEY_DOCK, TextUtils.join(",", dock))
            .remove(KEY_SCRIPT_PREFIX + oldId)
            .remove(KEY_SCRIPT_PATH_PREFIX + oldId)
            .remove(KEY_SCRIPT_TITLE_PREFIX + oldId)
            .remove(KEY_SCRIPT_SUGGESTIONS_PREFIX + oldId)
            .remove(KEY_SCRIPT_SEGMENTS_PREFIX + oldId)

        if (TextUtils.isEmpty(path)) {
            editor.remove(KEY_SCRIPT_PATH_PREFIX + newId)
        } else {
            editor.putString(KEY_SCRIPT_PATH_PREFIX + newId, normalizeModuleSource(path))
        }
        if (body != null) {
            editor.putString(KEY_SCRIPT_PREFIX + newId, body)
        }
        if (title != null) {
            editor.putString(KEY_SCRIPT_TITLE_PREFIX + newId, title)
        }
        if (suggestions != null) {
            editor.putString(KEY_SCRIPT_SUGGESTIONS_PREFIX + newId, suggestions)
        }
        if (segments != null) {
            editor.putString(KEY_SCRIPT_SEGMENTS_PREFIX + newId, segments)
        }
        if (TextUtils.equals(getActiveModule(context), oldId)) {
            editor.putString(KEY_ACTIVE_MODULE, newId)
        }
        editor.apply()
    }

    fun getScriptText(context: Context, module: String?): String? {
        val id = normalize(module)
        return prefs(context).getString(KEY_SCRIPT_PREFIX + id, null)
    }

    fun getScriptSegments(context: Context, module: String?): MutableList<ModuleTextSegment> {
        val id = normalize(module)
        val raw = prefs(context).getString(KEY_SCRIPT_SEGMENTS_PREFIX + id, null)
        val parsed = parseSegments(raw)
        if (!parsed.isEmpty()) {
            return parsed
        }

        val text = getScriptText(context, id)
        val fallback = ArrayList<ModuleTextSegment>()
        if (!TextUtils.isEmpty(text)) {
            fallback.add(ModuleTextSegment(text!!, false))
        }
        return fallback
    }

    fun getScriptTitle(context: Context, module: String?): String {
        val id = normalize(module)
        return prefs(context).getString(KEY_SCRIPT_TITLE_PREFIX + id, "")!!
    }

    fun getScriptPath(context: Context, module: String?): String {
        val id = normalize(module)
        return prefs(context).getString(KEY_SCRIPT_PATH_PREFIX + id, "")!!
    }

    fun getModuleSource(context: Context, module: String?): String {
        return getScriptPath(context, module)
    }

    fun isLauncherSource(source: String?): Boolean {
        return source != null && source.trim { it <= ' ' }.lowercase().startsWith(
            SOURCE_LAUNCHER_PREFIX
        )
    }

    fun isTermuxSource(source: String?): Boolean {
        return !TextUtils.isEmpty(source) && !isLauncherSource(source) && !isLuaSource(source)
    }

    fun isLuaSource(source: String?): Boolean {
        return source != null && source.trim { it <= ' ' }.lowercase().startsWith(SOURCE_LUA_PREFIX)
    }

    fun luaWidgetId(source: String?): String {
        if (!isLuaSource(source)) {
            return ""
        }
        return LuaWidgetManager.normalizeId(source!!.trim { it <= ' ' }
            .substring(SOURCE_LUA_PREFIX.length))
    }

    fun launcherProvider(source: String?): String {
        if (!isLauncherSource(source)) {
            return ""
        }
        return source!!.trim { it <= ' ' }.substring(SOURCE_LAUNCHER_PREFIX.length)
            .trim { it <= ' ' }.lowercase()
    }

    fun normalize(value: String?): String {
        if (value == null) {
            return ""
        }
        val lower = value.trim { it <= ' ' }.lowercase()
        val id = lower.replace("[^a-z0-9_-]".toRegex(), "")
        if ("notif" == id || "notification" == id) {
            return NOTIFICATIONS
        }
        if ("cal" == id) {
            return CALENDAR
        }
        if ("event" == id || "next_event" == id || "upcoming" == id || "upcomingevents" == id) {
            return EVENTS
        }
        if ("reminders" == id) {
            return REMINDER
        }
        if ("note" == id || "todo" == id || "todos" == id || "task" == id || "tasks" == id) {
            return NOTES
        }
        if ("feed" == id || "feeds" == id) {
            return RSS
        }
        if ("weath" == id || "wttr" == id) {
            return WEATHER
        }
        if ("nativeweather" == id || "weathernative" == id) {
            return WEATHER_NATIVE
        }
        return id
    }

    fun displayName(module: String?): String {
        val id = normalize(module)
        if (NOTIFICATIONS == id) {
            return "NOTIFICATIONS"
        }
        if (EVENTS == id) {
            return "EVENTS"
        }
        if (RSS == id) {
            return "RSS"
        }
        if (WEATHER_NATIVE == id) {
            return "WEATHER (NATIVE)"
        }
        return id.uppercase()
    }

    fun displayTitle(context: Context, module: String?): String? {
        val id = normalize(module)
        val source = getModuleSource(context, id)
        if (isLuaSource(source)) {
            return LuaWidgetManager.getName(luaWidgetId(source))
        }
        val title = getScriptTitle(context, id)
        if (!TextUtils.isEmpty(title)) {
            return title
        }
        return displayName(id)
    }

    private fun getSuggestionsForModule(
        context: Context,
        module: String?
    ): MutableList<ModuleSuggestion?> {
        val suggestions = ArrayList<ModuleSuggestion?>()
        val id = normalize(module)
        if (TextUtils.isEmpty(id)) {
            return suggestions
        }

        if (isLuaSource(getModuleSource(context, id))) {
            suggestions.addAll(getScriptSuggestions(context, id))
            return suggestions
        }

        if (TIMER == id) {
            suggestions.add(ModuleSuggestion.Companion.command("+5m", "timer -add 5m"))
            suggestions.add(ModuleSuggestion.Companion.command("+15m", "timer -add 15m"))
            suggestions.add(ModuleSuggestion.Companion.command("25m", "timer 25m"))
            suggestions.add(ModuleSuggestion.Companion.command("stop", "timer -stop"))
            suggestions.add(ModuleSuggestion.Companion.command("status", "timer -status"))
            suggestions.add(ModuleSuggestion.Companion.command("pomodoro", "pomodoro"))
        } else if (MUSIC == id) {
            suggestions.add(ModuleSuggestion.Companion.command("prev", "music -previous"))
            suggestions.add(ModuleSuggestion.Companion.command("play", "music -play"))
            suggestions.add(ModuleSuggestion.Companion.command("next", "music -next"))
            suggestions.add(ModuleSuggestion.Companion.command("info", "music -info"))
            suggestions.add(ModuleSuggestion.Companion.command("stop", "music -stop"))
        } else if (NOTIFICATIONS == id) {
            if (ModulePromptManager.isActive(context)) {
                suggestions.addAll(ModulePromptManager.getSuggestions(context))
                return suggestions
            }
            suggestions.add(ModuleSuggestion.Companion.command("prev", "notifications -prev"))
            suggestions.add(ModuleSuggestion.Companion.command("next", "notifications -next"))
            suggestions.add(ModuleSuggestion.Companion.command("reply", "notifications -reply"))
            suggestions.add(ModuleSuggestion.Companion.command("open", "notifications -open"))
            suggestions.add(ModuleSuggestion.Companion.command("access", "notifications -access"))
            suggestions.add(ModuleSuggestion.Companion.command("rules", "notifications -ls"))
            suggestions.add(ModuleSuggestion.Companion.command("filters", "notifications -file"))
        } else if (CALENDAR == id) {
            // Calendar is the built-in module-button example; its controls live in-panel.
        } else if (REMINDER == id) {
            suggestions.add(ModuleSuggestion.Companion.command("open", "reminder -open"))
        } else if (NOTES == id) {
            suggestions.add(ModuleSuggestion.Companion.command("edit", "notes"))
            suggestions.add(ModuleSuggestion.Companion.command("list", "notes -ls"))
            suggestions.add(ModuleSuggestion.Companion.command("todo", "notes -add TODO: "))
            suggestions.add(ModuleSuggestion.Companion.command("copy", "notes -cp 1"))
            suggestions.add(ModuleSuggestion.Companion.command("clear", "notes -clear"))
        } else if (RSS == id) {
            val firstFeed = firstConfiguredFeedId(context)
            suggestions.add(ModuleSuggestion.Companion.command("list", "rss -ls"))
            if (firstFeed != -1) {
                suggestions.add(ModuleSuggestion.Companion.command("latest", "rss -l " + firstFeed))
                suggestions.add(
                    ModuleSuggestion.Companion.command(
                        "refresh",
                        "rss -frc " + firstFeed
                    )
                )
                suggestions.add(
                    ModuleSuggestion.Companion.command(
                        "info",
                        "rss -info " + firstFeed
                    )
                )
            }
            suggestions.add(
                ModuleSuggestion.Companion.command(
                    "reddit",
                    "rss -add 1 900 https://www.reddit.com/r/android/.rss"
                )
            )
            suggestions.add(ModuleSuggestion.Companion.command("file", "rss -file"))
        } else if (usesNativeWeatherControls(id)) {
            suggestions.add(ModuleSuggestion.Companion.command("update", "tuiweather -update"))
            suggestions.add(ModuleSuggestion.Companion.command("location", "tuiweather -set_location "))
            suggestions.add(ModuleSuggestion.Companion.command("show status", "tuiweather -enable"))
            suggestions.add(ModuleSuggestion.Companion.command("hide status", "tuiweather -disable"))
            suggestions.add(ModuleSuggestion.Companion.command("setup", "tuiweather -tutorial"))
        } else {
            suggestions.addAll(getScriptSuggestions(context, id))
        }

        return suggestions
    }

    private fun parseScriptPayload(text: String?): ScriptPayload {
        val payload = ScriptPayload()
        if (text == null) {
            return payload
        }

        val lines = text.split("\\r?\\n".toRegex()).toTypedArray()
        var monoBlock = false
        for (i in lines.indices) {
            val rawLine = lines[i]
            if (i == lines.size - 1 && rawLine.length == 0) {
                continue
            }
            val line = rawLine.trim { it <= ' ' }
            if (monoBlock) {
                if ("::end" == line) {
                    monoBlock = false
                } else {
                    appendBodySegment(payload, rawLine, true)
                }
                continue
            }
            if (line.startsWith("::title ")) {
                payload.title = line.substring("::title ".length).trim { it <= ' ' }
            } else if (rawLine.startsWith("::body ")) {
                appendBodySegment(payload, rawLine.substring("::body ".length), false)
            } else if (line.startsWith("::body ")) {
                appendBodySegment(payload, line.substring("::body ".length), false)
            } else if (isPreformattedStart(line)) {
                monoBlock = true
            } else if (isPreformattedInline(rawLine, line)) {
                appendBodySegment(payload, preformattedInlineText(rawLine, line), true)
            } else if (line.startsWith("::suggest ")) {
                val suggestion =
                    parseSuggestion(line.substring("::suggest ".length).trim { it <= ' ' })
                if (suggestion != null) {
                    payload.suggestions.add(suggestion)
                }
            } else if (!line.startsWith("::")) {
                appendBodySegment(payload, rawLine, monoBlock)
            }
        }
        return payload
    }

    private fun isPreformattedStart(line: String): Boolean {
        return "::pre" == line || "::ascii" == line || "::code" == line
    }

    private fun isPreformattedInline(rawLine: String, line: String): Boolean {
        return rawLine.startsWith("::pre ")
                || rawLine.startsWith("::ascii ")
                || rawLine.startsWith("::code ")
                || line.startsWith("::pre ")
                || line.startsWith("::ascii ")
                || line.startsWith("::code ")
    }

    private fun preformattedInlineText(rawLine: String, line: String): String {
        val prefixes = arrayOf("::pre ", "::ascii ", "::code ")
        for (prefix in prefixes) {
            if (rawLine.startsWith(prefix)) {
                return rawLine.substring(prefix.length)
            }
        }
        for (prefix in prefixes) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length)
            }
        }
        return ""
    }

    private fun parseSuggestion(raw: String?): ModuleSuggestion? {
        if (TextUtils.isEmpty(raw)) {
            return null
        }

        val parts = raw!!.split("\\|".toRegex()).toTypedArray()
        val label: String?
        val mode: String?
        val action: String?
        if (parts.size >= 3) {
            label = parts[0].trim { it <= ' ' }
            mode = parts[1].trim { it <= ' ' }.lowercase()
            action = parts[2].trim { it <= ' ' }
        } else if (parts.size == 2) {
            label = parts[0].trim { it <= ' ' }
            mode = ModuleSuggestion.Companion.MODE_COMMAND
            action = parts[1].trim { it <= ' ' }
        } else {
            label = raw.trim { it <= ' ' }
            mode = ModuleSuggestion.Companion.MODE_COMMAND
            action = label
        }

        if (TextUtils.isEmpty(label) || TextUtils.isEmpty(action)) {
            return null
        }
        return ModuleSuggestion(label, action, normalizeMode(mode))
    }

    private fun normalizeMode(mode: String?): String {
        val normalized = if (mode == null) "" else mode.trim { it <= ' ' }.lowercase()
        if (ModuleSuggestion.Companion.MODE_TERMUX_RUN == normalized) {
            return ModuleSuggestion.Companion.MODE_TERMUX_RUN
        }
        if (ModuleSuggestion.Companion.MODE_CALLBACK == normalized) {
            return ModuleSuggestion.Companion.MODE_CALLBACK
        }
        return ModuleSuggestion.Companion.MODE_COMMAND
    }

    private fun appendBodySegment(payload: ScriptPayload, line: String?, mono: Boolean) {
        if (payload.body.length > 0) {
            payload.body.append('\n')
        }
        payload.body.append(line)
        appendSegment(payload.segments, line, mono)
    }

    private fun appendSegment(
        segments: MutableList<ModuleTextSegment>,
        line: String?,
        mono: Boolean
    ) {
        val value = line ?: ""
        if (!segments.isEmpty() && segments[segments.size - 1].mono == mono) {
            val previous = segments[segments.size - 1]
            previous.text = previous.text + "\n" + value
        } else {
            segments.add(ModuleTextSegment(value, mono))
        }
    }

    private fun serializeSegments(segments: MutableList<ModuleTextSegment>): String? {
        if (segments.isEmpty()) {
            return null
        }
        val array = JSONArray()
        for (segment in segments) {
            val obj = JSONObject()
            obj.put("text", segment.text)
            obj.put("mono", segment.mono)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseSegments(raw: String?): MutableList<ModuleTextSegment> {
        val segments = ArrayList<ModuleTextSegment>()
        if (TextUtils.isEmpty(raw)) {
            return segments
        }
        try {
            val array = JSONArray(raw)
            for (i in 0..<array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                segments.add(ModuleTextSegment(obj.optString("text", ""), obj.optBoolean("mono", false)))
            }
        } catch (ignored: Exception) {
        }
        return segments
    }

    private fun serializeSuggestions(suggestions: MutableList<ModuleSuggestion>): String? {
        val lines = ArrayList<String?>()
        for (suggestion in suggestions) {
            lines.add(suggestion.label + "\t" + suggestion.mode + "\t" + suggestion.action)
        }
        return TextUtils.join("\n", lines)
    }

    private fun getScriptSuggestions(
        context: Context,
        module: String?
    ): MutableList<ModuleSuggestion?> {
        val suggestions = ArrayList<ModuleSuggestion?>()
        val raw: String = prefs(context).getString(
            ModuleManager.KEY_SCRIPT_SUGGESTIONS_PREFIX + ModuleManager.normalize(module),
            ""
        )!!
        if (TextUtils.isEmpty(raw)) {
            return suggestions
        }

        for (line in raw.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val parts = line.split("\\t".toRegex(), limit = 3).toTypedArray()
            if (parts.size == 3 && !TextUtils.isEmpty(parts[0]) && !TextUtils.isEmpty(parts[2])) {
                suggestions.add(ModuleSuggestion(parts[0], parts[2], normalizeMode(parts[1])))
            }
        }
        return suggestions
    }

    private fun parseList(raw: String): MutableList<String?> {
        val out = ArrayList<String?>()
        for (part in raw.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val id = normalize(part)
            if (!TextUtils.isEmpty(id) && !out.contains(id)) {
                out.add(id)
            }
        }
        return out
    }

    private fun normalizeModuleSource(path: String?): String {
        val trimmed = if (path == null) "" else path.trim { it <= ' ' }
        val lower = trimmed.lowercase()
        if (lower.startsWith(SOURCE_TERMUX_PREFIX)) {
            return trimmed.substring(SOURCE_TERMUX_PREFIX.length).trim { it <= ' ' }
        }
        if (lower.startsWith(SOURCE_LAUNCHER_PREFIX)) {
            return SOURCE_LAUNCHER_PREFIX + trimmed.substring(SOURCE_LAUNCHER_PREFIX.length)
                .trim { it <= ' ' }.lowercase()
        }
        if (lower.startsWith(SOURCE_LUA_PREFIX)) {
            return SOURCE_LUA_PREFIX + LuaWidgetManager.normalizeId(
                trimmed.substring(
                    SOURCE_LUA_PREFIX.length
                )
            )
        }
        return trimmed
    }

    private fun getScriptIds(context: Context): MutableSet<String?> {
        return prefs(context)
            .getStringSet(ModuleManager.KEY_SCRIPT_IDS, java.util.LinkedHashSet<kotlin.String?>())!!
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    class ModuleSuggestion(
        val label: String?,
        val action: String?,
        val mode: String?
    ) {
        companion object {
            const val MODE_COMMAND: String = "command"
            const val MODE_TERMUX_RUN: String = "termux-run"
            const val MODE_CALLBACK: String = "callback"

            fun command(label: String?, command: String?): ModuleSuggestion {
                return ModuleSuggestion(label, command, MODE_COMMAND)
            }
        }
    }

    private class ScriptPayload {
        var title: String = ""
        val body = StringBuilder()
        val segments: MutableList<ModuleTextSegment> = ArrayList<ModuleTextSegment>()
        val suggestions: MutableList<ModuleSuggestion> = ArrayList<ModuleSuggestion>()
    }

    class ModuleTextSegment(
        var text: String,
        val mono: Boolean
    )
}
