package ohi.andre.consolelauncher.managers.lua

import android.text.TextUtils
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import android.content.Context
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Map
import java.util.Set
import java.util.regex.Matcher

/**
 * Lua script store/runtime helpers for dock modules (and Lua apps/suggest scripts).
 * These are module backends, not Android AppWidgets. AppWidget hosting is
 * [ohi.andre.consolelauncher.managers.widgets.AndroidWidgetDrawerManager].
 * On-disk folder remains "widgets" for backup compatibility.
 */
object LuaWidgetManager {
    const val ENGINE: String = "lua"
    const val SOURCE_PREFIX: String = "lua:"
    const val WIDGETS_DIR: String = "widgets"
    const val SCRIPT_FILE: String = "main.lua"
    const val MANIFEST_FILE: String = "manifest.json"
    const val SYSTEM_TIMER_WIDGET_ID: String = "timer"
    private val BUNDLED_SAMPLE_IDS: Array<String?> = arrayOf<String?>(
        "aio_counter",
        "aio_progress",
        "aio_clock",
        "retui_counter",
        "retui_progress",
        "retui_clock",
        "retui_expandable",
        "retui_ticker",
        "retui_toolkit",
        "retui_suggest",
        "retui_prefs",
        "retui_files",
        "retui_platform",
        "retui_public_ip"
    )
    private val BUNDLED_SAMPLE_MARKERS: Array<String?> = arrayOf<String?>(
        "Persistent counter using Re:TUI prefs and dock actions",
        "Progress Lua module using Re:TUI prefs, system state, and progress APIs",
        "Clock Lua module using Re:TUI system helpers",
        "Compact and expanded rendering using Re:TUI Lua module state",
        "Active Lua module ticking lifecycle demo",
        "Small standard library demo",
        "Lua suggestion script demo",
        "Preferences helper demo",
        "Lua module-local file helper demo",
        "System/platform helper demo",
        "https://api.ipify.org?format=json"
    )
    private val SENSITIVE_CAPABILITIES: MutableSet<String?> = HashSet<String?>()

    init {
        Collections.addAll<String?>(
            SENSITIVE_CAPABILITIES,
            "network",
            "clipboard",
            "vibrate",
            "local-files",
            "active-tick",
            "notifications",
            "apps",
            "intents",
            "shortcuts"
        )
    }

    fun bundledSampleIds(): MutableList<String?> {
        val ids = ArrayList<String?>()
        Collections.addAll<String?>(ids, *BUNDLED_SAMPLE_IDS)
        return ids
    }

    fun isBundledSample(id: String?): Boolean {
        val normalized = normalizeId(id)
        for (sampleId in BUNDLED_SAMPLE_IDS) {
            if (TextUtils.equals(normalized, sampleId)) {
                return true
            }
        }
        val script = readScript(normalized)
        if (TextUtils.isEmpty(script)) {
            return false
        }
        for (marker in BUNDLED_SAMPLE_MARKERS) {
            if (script!!.contains(marker!!)) {
                return true
            }
        }
        return false
    }

    fun widgetsDir(): File {
        val dir = File(Tuils.getFolder(), WIDGETS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun widgetDir(id: String?): File {
        return File(widgetsDir(), normalizeId(id))
    }

    fun scriptFile(id: String?): File {
        return File(widgetDir(id), SCRIPT_FILE)
    }

    fun manifestFile(id: String?): File {
        return File(widgetDir(id), MANIFEST_FILE)
    }

    fun exists(id: String?): Boolean {
        return scriptFile(id).isFile()
    }

    fun listIds(): MutableList<String?> {
        val ids = ArrayList<String?>()
        val files = widgetsDir().listFiles()
        if (files == null) {
            return ids
        }
        for (file in files) {
            if (file.isDirectory() && File(file, SCRIPT_FILE).isFile()) {
                ids.add(file.getName())
            }
        }
        Collections.sort<String?>(ids, String.CASE_INSENSITIVE_ORDER)
        return ids
    }

    fun readScript(id: kotlin.String?): kotlin.String? {
        val file = scriptFile(id)
        if (!file.isFile()) {
            return ""
        }
        try {
            FileInputStream(file).use { `in` ->
                return Tuils.convertStreamToString(`in`)
            }
        } catch (e: Exception) {
            return ""
        }
    }

    fun getName(id: kotlin.String?): kotlin.String? {
        val normalized = normalizeId(id)
        val manifest = manifestFile(normalized)
        if (manifest.isFile()) {
            try {
                FileInputStream(manifest).use { `in` ->
                    val `object` = JSONObject(Tuils.convertStreamToString(`in`))
                    val name = `object`.optString("name", "")
                    if (!TextUtils.isEmpty(name)) {
                        return name
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        val scriptName = metadata(readScript(normalized)).get("name")
        if (!TextUtils.isEmpty(scriptName)) {
            return scriptName
        }
        return displayName(normalized)
    }

    fun getScriptType(id: kotlin.String?): kotlin.String {
        return getScriptTypeFromScript(readScript(normalizeId(id)))
    }

    fun getScriptTypeFromScript(script: kotlin.String?): kotlin.String {
        val type = metadata(script).get("type")
        return if (TextUtils.isEmpty(type)) "module" else type!!.lowercase()
    }

    fun apiVersion(id: kotlin.String?): kotlin.String {
        return apiVersionFromScript(readScript(normalizeId(id)))
    }

    fun apiVersionFromScript(script: kotlin.String?): kotlin.String {
        val meta = metadata(script)
        var version = meta.get("retui")
        if (TextUtils.isEmpty(version)) {
            version = meta.get("retui_version")
        }
        return (if (android.text.TextUtils.isEmpty(version)) "1" else version)!!
    }

    fun isDockableScript(script: kotlin.String?): Boolean {
        val type = getScriptTypeFromScript(script)
        return "suggest" != type && "command" != type && "app" != type
    }

    fun isDockable(id: kotlin.String?): Boolean {
        return isDockableScript(readScript(normalizeId(id)))
    }

    fun hasConfig(id: kotlin.String?): Boolean {
        return hasConfigScript(readScript(normalizeId(id)))
    }

    fun hasConfigScript(script: kotlin.String?): Boolean {
        val code = if (script == null) "" else script.lowercase(Locale.ROOT)
        return code.contains("function on_config") || code.contains("on_config = function")
    }

    fun isEnabled(id: kotlin.String?): Boolean {
        val manifest = readManifest(normalizeId(id))
        return manifest == null || !manifest.optBoolean("disabled", false)
    }

    @Throws(Exception::class)
    fun setEnabled(id: kotlin.String?, enabled: Boolean) {
        val normalized = normalizeId(id)
        require(exists(normalized)) { "Unknown Lua module: " + normalized }
        var manifest = readManifest(normalized)
        if (manifest == null) {
            manifest = JSONObject()
            manifest.put("type", "retui-widget")
            manifest.put("schema", 1)
            manifest.put("id", normalized)
            manifest.put("name", getName(normalized))
            manifest.put("engine", ENGINE)
        }
        manifest.put("disabled", !enabled)
        write(manifestFile(normalized), manifest.toString(2) + "\n")
    }

    fun version(id: kotlin.String?): Long {
        val file = scriptFile(id)
        return if (file.isFile()) file.lastModified() else 0L
    }

    @Throws(Exception::class)
    fun save(id: kotlin.String?, name: kotlin.String?, script: kotlin.String?) {
        val normalized = normalizeId(id)
        require(!TextUtils.isEmpty(normalized)) { "Lua module id is required" }
        val code = if (script == null) "" else script
        val meta = metadata(code)
        val scriptName = meta.get("name")
        var effectiveName: kotlin.String? = if (name == null) "" else name.trim { it <= ' ' }
        if (!TextUtils.isEmpty(scriptName)
            && (TextUtils.isEmpty(effectiveName)
                    || TextUtils.equals(effectiveName, normalized)
                    || TextUtils.equals(effectiveName, displayName(normalized)))
        ) {
            effectiveName = scriptName
        }
        val dir = widgetDir(normalized)
        check(!(!dir.exists() && !dir.mkdirs())) { "Unable to create Lua module folder" }
        val previousManifest = readManifest(normalized)
        val previousApprovedHash = if (previousManifest == null) "" else previousManifest.optString(
            "approvedScriptHash",
            ""
        )
        val previousApprovedPermissions =
            if (previousManifest == null) "" else previousManifest.optString(
                "approvedPermissions",
                ""
            )
        val hash = scriptHash(code)
        val requiredPermissions = requiredPermissions(code)
        write(scriptFile(normalized), code)

        val manifest = JSONObject()
        manifest.put("type", "retui-widget")
        manifest.put("schema", 1)
        manifest.put("id", normalized)
        manifest.put(
            "name",
            if (TextUtils.isEmpty(effectiveName)) displayName(normalized) else effectiveName
        )
        manifest.put("engine", ENGINE)
        manifest.put("devOnly", true)
        val metadata = JSONObject()
        for (entry in meta.entries) {
            metadata.put(entry.key, entry.value)
        }
        manifest.put("metadata", metadata)
        manifest.put("scriptHash", hash)
        manifest.put("permissions", TextUtils.join(",", requiredPermissions))
        if (TextUtils.equals(previousApprovedHash, hash)
            && permissionList(previousApprovedPermissions).containsAll(requiredPermissions)
        ) {
            manifest.put("approvedScriptHash", previousApprovedHash)
            manifest.put("approvedPermissions", previousApprovedPermissions)
        }
        if (previousManifest != null && previousManifest.optBoolean("disabled", false)) {
            manifest.put("disabled", true)
        }
        write(manifestFile(normalized), manifest.toString(2) + "\n")
    }

    @Throws(Exception::class)
    fun ensureSystemTimerWidget() {
        ensureSystemWidget(SYSTEM_TIMER_WIDGET_ID, "Timer", systemTimerScript())
    }

    @Throws(Exception::class)
    private fun ensureSystemWidget(
        id: kotlin.String?,
        name: kotlin.String?,
        script: kotlin.String?
    ) {
        val normalized = normalizeId(id)
        val manifest = readManifest(normalized)
        if (exists(normalized) && (manifest == null || !manifest.optBoolean(
                "systemManaged",
                false
            ))
        ) {
            return
        }
        saveSystem(normalized, name, script)
    }

    @Throws(Exception::class)
    private fun saveSystem(id: kotlin.String?, name: kotlin.String?, script: kotlin.String?) {
        val normalized = normalizeId(id)
        require(!TextUtils.isEmpty(normalized)) { "Lua module id is required" }
        val code = if (script == null) "" else script
        val meta = metadata(code)
        val hash = scriptHash(code)
        val requiredPermissions = requiredPermissions(code)
        write(scriptFile(normalized), code)

        val manifest = JSONObject()
        manifest.put("type", "retui-widget")
        manifest.put("schema", 1)
        manifest.put("id", normalized)
        manifest.put("name", if (TextUtils.isEmpty(name)) displayName(normalized) else name)
        manifest.put("engine", ENGINE)
        manifest.put("devOnly", false)
        manifest.put("systemManaged", true)
        val metadata = JSONObject()
        for (entry in meta.entries) {
            metadata.put(entry.key, entry.value)
        }
        manifest.put("metadata", metadata)
        manifest.put("scriptHash", hash)
        manifest.put("permissions", TextUtils.join(",", requiredPermissions))
        manifest.put("approvedScriptHash", hash)
        manifest.put("approvedPermissions", TextUtils.join(",", requiredPermissions))
        write(manifestFile(normalized), manifest.toString(2) + "\n")
    }

    fun delete(id: kotlin.String?) {
        val dir = widgetDir(id)
        if (dir.exists()) {
            Tuils.delete(dir)
        }
    }

    @Throws(Exception::class)
    fun exportPackage(id: kotlin.String?): kotlin.String {
        val normalized = normalizeId(id)
        require(exists(normalized)) { "Unknown Lua module: " + normalized }
        val `object` = JSONObject()
        `object`.put("type", "retui-widget-package")
        `object`.put("schema", 1)
        `object`.put("id", normalized)
        `object`.put("name", getName(normalized))
        `object`.put("engine", ENGINE)
        `object`.put("script", readScript(normalized))
        return `object`.toString(2)
    }

    @Throws(Exception::class)
    fun rename(oldId: kotlin.String?, newId: kotlin.String?) {
        val oldNormalized = normalizeId(oldId)
        val newNormalized = normalizeId(newId)
        require(!(TextUtils.isEmpty(oldNormalized) || TextUtils.isEmpty(newNormalized))) { "Lua module id is required" }
        if (TextUtils.equals(oldNormalized, newNormalized)) {
            return
        }

        val oldDir = widgetDir(oldNormalized)
        require(File(oldDir, SCRIPT_FILE).isFile()) { "Unknown Lua module: " + oldNormalized }

        val newDir = widgetDir(newNormalized)
        require(!newDir.exists()) { "Lua module id already exists: " + newNormalized }

        check(oldDir.renameTo(newDir)) { "Unable to rename Lua module folder" }

        val name = getName(newNormalized)
        val script = readScript(newNormalized)
        save(newNormalized, name, script)
    }

    fun newWidgetTemplate(id: kotlin.String?): kotlin.String {
        val normalized = normalizeId(id)
        val title = displayName(normalized)
        return ("-- name = \"" + title + "\"\n"
                + "-- type = \"module\"\n"
                + "-- retui = \"1\"\n"
                + "\n"
                + "local count = 0\n"
                + "\n"
                + "local function render()\n"
                + "    ui:set_title(\"" + title + "\")\n"
                + "    ui:show_text(\"Hello from \" .. \"" + normalized + "\" .. \"\\nCount: \" .. count)\n"
                + "    ui:suggest_action(\"Increase\", \"increase\")\n"
                + "    ui:suggest_action(\"Reset\", \"reset\")\n"
                + "end\n"
                + "\n"
                + "function on_load()\n"
                + "    render()\n"
                + "end\n"
                + "\n"
                + "function on_action(action)\n"
                + "    if action == \"increase\" then count = count + 1 end\n"
                + "    if action == \"reset\" then count = 0 end\n"
                + "    render()\n"
                + "end\n")
    }

    fun newAppTemplate(id: kotlin.String?): kotlin.String {
        val normalized = normalizeId(id)
        val title = displayName(normalized)
        return ("-- name = \"" + title + "\"\n"
                + "-- type = \"app\"\n"
                + "-- retui = \"1\"\n"
                + "\n"
                + "local prefs = require \"prefs\"\n"
                + "\n"
                + "local function ensure()\n"
                + "    if prefs.items == nil then prefs.items = {} end\n"
                + "end\n"
                + "\n"
                + "local function trim(value)\n"
                + "    return (value or \"\"):gsub(\"^%s+\", \"\"):gsub(\"%s+$\", \"\")\n"
                + "end\n"
                + "\n"
                + "local function render(status)\n"
                + "    ensure()\n"
                + "    ui:set_title(\"" + title + "\")\n"
                + "    local lines = {}\n"
                + "    if status ~= nil and status ~= \"\" then table.insert(lines, status) end\n"
                + "    if #prefs.items == 0 then\n"
                + "        table.insert(lines, \"Type a line below and press enter.\")\n"
                + "    else\n"
                + "        for i, item in ipairs(prefs.items) do\n"
                + "            table.insert(lines, i .. \". \" .. item)\n"
                + "        end\n"
                + "    end\n"
                + "    ui:show_lines(lines)\n"
                + "    ui:show_action(\"Clear\", \"clear\")\n"
                + "end\n"
                + "\n"
                + "function on_open()\n"
                + "    render()\n"
                + "end\n"
                + "\n"
                + "function on_resume()\n"
                + "    render()\n"
                + "end\n"
                + "\n"
                + "function on_input(text)\n"
                + "    local value = trim(text)\n"
                + "    if value ~= \"\" then\n"
                + "        ensure()\n"
                + "        table.insert(prefs.items, value)\n"
                + "    end\n"
                + "    render(\"input: \" .. value)\n"
                + "end\n"
                + "\n"
                + "function on_action(action)\n"
                + "    if action == \"clear\" then prefs.items = {} end\n"
                + "    render()\n"
                + "end\n")
    }

    @Throws(Exception::class)
    fun installSamples(): MutableList<kotlin.String?> {
        val ids = ArrayList<kotlin.String?>()
        for (entry in samples().entries) {
            val id = entry.key
            val sample: Sample = entry.value!!
            save(id, sample.name, sample.script)
            ids.add(id)
        }
        return ids
    }

    fun modulePayload(id: kotlin.String?, result: LuaWidgetEngine.RenderResult): kotlin.String {
        val out = StringBuilder()
        var title = result.title
        if (TextUtils.isEmpty(title)) {
            title = getName(id)
        }
        appendDirective(out, "title", title)

        if (!TextUtils.isEmpty(result.error)) {
            appendDirective(out, "body", "Lua error: " + result.error)
            if (!TextUtils.isEmpty(result.errorStage)) {
                appendDirective(out, "body", "Stage: " + result.errorStage)
            }
        } else if (!TextUtils.isEmpty(result.body)) {
            for (line in result.body!!.split("\\r?\\n".toRegex()).toTypedArray()) {
                appendDirective(out, "body", line)
            }
        } else {
            appendDirective(out, "body", "No Lua module output yet.")
        }

        for (action in result.suggestions) {
            appendSuggest(out, action!!.label, action.command)
        }
        if (hasConfig(id)) {
            appendSuggest(out, "edit", "module -config " + normalizeId(id))
        }
        appendSuggest(out, "edit script", "module -edit " + normalizeId(id))
        if (!TextUtils.isEmpty(result.error)) {
            appendSuggest(out, "copy error", "module -copy-error " + normalizeId(id))
            appendSuggest(out, "check", "module -check " + normalizeId(id))
            appendSuggest(out, "disable", "module -disable " + normalizeId(id))
        }
        return out.toString().trim { it <= ' ' }
    }

    fun disabledPayload(id: kotlin.String?): kotlin.String {
        val normalized = normalizeId(id)
        val out = StringBuilder()
        appendDirective(out, "title", getName(normalized))
        appendDirective(out, "body", "Lua module disabled.")
        appendSuggest(out, "enable", "module -enable " + normalized)
        appendSuggest(out, "check", "module -check " + normalized)
        return out.toString().trim { it <= ' ' }
    }

    fun consentPayload(id: kotlin.String?): kotlin.String {
        val normalized = normalizeId(id)
        val status = trustStatus(normalized)
        val out = StringBuilder()
        appendDirective(out, "title", getName(normalized))
        appendDirective(out, "body", "Lua script approval required.")
        appendDirective(
            out,
            "body",
            "Permissions: " + (if (status.requiredPermissions.isEmpty()) "none" else TextUtils.join(
                ", ",
                status.requiredPermissions
            ))
        )
        if (!status.missingDeclarations.isEmpty()) {
            appendDirective(
                out,
                "body",
                "Missing metadata: " + TextUtils.join(", ", status.missingDeclarations)
            )
        }
        if (!status.unsupportedPermissions.isEmpty()) {
            appendDirective(
                out,
                "body",
                "Unsupported permissions: " + TextUtils.join(", ", status.unsupportedPermissions)
            )
        }
        if (status.scriptChanged) {
            appendDirective(out, "body", "Script changed since last approval.")
        }
        if (status.canApprove()) {
            appendSuggest(out, "approve", "module -approve " + normalized)
        }
        appendSuggest(out, "check", "module -check " + normalized)
        return out.toString().trim { it <= ' ' }
    }

    fun isTrusted(id: kotlin.String?): Boolean {
        return trustStatus(id).trusted
    }

    fun isPermissionApproved(id: kotlin.String?, permission: kotlin.String?): Boolean {
        return approvedPermissions(id).contains(normalizePermission(permission))
    }

    fun approvedPermissions(id: kotlin.String?): MutableList<kotlin.String?> {
        val normalized = normalizeId(id)
        val manifest = readManifest(normalized)
        if (manifest == null) {
            return ArrayList<kotlin.String?>()
        }
        val script = readScript(normalized)
        val approvedHash = manifest.optString("approvedScriptHash", "")
        if (!TextUtils.equals(approvedHash, scriptHash(script))) {
            return ArrayList<kotlin.String?>()
        }
        return permissionList(manifest.optString("approvedPermissions", ""))
    }

    fun trustStatus(id: kotlin.String?): TrustStatus {
        val normalized = normalizeId(id)
        val script = readScript(normalized)
        val status = TrustStatus()
        status.scriptHash = scriptHash(script)
        status.declaredPermissions = declaredPermissions(script)
        status.requiredPermissions = requiredPermissions(script)
        status.inferredCapabilities = inferCapabilities(script)
        status.missingDeclarations =
            subtract(status.requiredPermissions, status.declaredPermissions)
        status.unsupportedPermissions = unsupportedPermissions(status.declaredPermissions)

        val manifest = readManifest(normalized)
        val approvedHash =
            if (manifest == null) "" else manifest.optString("approvedScriptHash", "")
        val approvedPermissions = permissionList(
            if (manifest == null) "" else manifest.optString(
                "approvedPermissions",
                ""
            )
        )
        status.scriptChanged =
            !TextUtils.isEmpty(approvedHash) && !TextUtils.equals(approvedHash, status.scriptHash)
        status.trusted = status.missingDeclarations.isEmpty()
                && status.unsupportedPermissions.isEmpty()
                && (status.requiredPermissions.isEmpty()
                || (TextUtils.equals(approvedHash, status.scriptHash)
                && approvedPermissions.containsAll(status.requiredPermissions)))
        return status
    }

    @Throws(Exception::class)
    fun approve(id: kotlin.String?) {
        val normalized = normalizeId(id)
        require(exists(normalized)) { "Unknown Lua module: " + normalized }
        val status = trustStatus(normalized)
        require(status.unsupportedPermissions.isEmpty()) {
            "Unsupported permissions: " + TextUtils.join(
                ", ",
                status.unsupportedPermissions
            )
        }
        require(status.missingDeclarations.isEmpty()) {
            "Declare permissions first: " + TextUtils.join(
                ", ",
                status.missingDeclarations
            )
        }
        var manifest = readManifest(normalized)
        if (manifest == null) {
            manifest = JSONObject()
        }
        manifest.put("approvedScriptHash", status.scriptHash)
        manifest.put("approvedPermissions", TextUtils.join(",", status.requiredPermissions))
        manifest.put("scriptHash", status.scriptHash)
        manifest.put("permissions", TextUtils.join(",", status.requiredPermissions))
        write(manifestFile(normalized), manifest.toString(2) + "\n")
    }

    fun saveLastError(id: kotlin.String?, stage: kotlin.String?, error: kotlin.String?) {
        val normalized = normalizeId(id)
        if (TextUtils.isEmpty(normalized)) {
            return
        }
        try {
            var manifest = readManifest(normalized)
            if (manifest == null) {
                manifest = JSONObject()
                manifest.put("type", "retui-widget")
                manifest.put("schema", 1)
                manifest.put("id", normalized)
                manifest.put("name", getName(normalized))
                manifest.put("engine", ENGINE)
            }
            manifest.put("lastError", if (error == null) "" else error)
            manifest.put("lastErrorStage", if (stage == null) "" else stage)
            manifest.put("lastErrorAt", System.currentTimeMillis())
            write(manifestFile(normalized), manifest.toString(2) + "\n")
        } catch (ignored: Exception) {
        }
    }

    fun lastError(id: kotlin.String?): kotlin.String {
        val manifest = readManifest(normalizeId(id))
        if (manifest == null) {
            return ""
        }
        val error = manifest.optString("lastError", "")
        val stage = manifest.optString("lastErrorStage", "")
        if (TextUtils.isEmpty(error)) {
            return ""
        }
        return if (TextUtils.isEmpty(stage)) error else "Stage: " + stage + "\n" + error
    }

    fun normalizeId(value: kotlin.String?): kotlin.String {
        if (value == null) {
            return ""
        }
        return value.trim { it <= ' ' }
            .lowercase()
            .replace("[^a-z0-9_-]".toRegex(), "")
    }

    fun idFromName(value: kotlin.String?): kotlin.String {
        if (value == null) {
            return ""
        }
        return value.trim { it <= ' ' }
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .replace("^_|_$".toRegex(), "")
    }

    fun metadata(script: kotlin.String?): MutableMap<kotlin.String?, kotlin.String?> {
        val meta = LinkedHashMap<kotlin.String?, kotlin.String?>()
        if (script == null) {
            return meta
        }
        val pattern =
            Pattern.compile("^\\s*--\\s*([a-zA-Z0-9_]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\r\\n]+))\\s*$")
        val lines = script.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            val trimmed = line.trim { it <= ' ' }
            if (trimmed.length == 0) {
                continue
            }
            val matcher = pattern.matcher(line)
            if (!matcher.matches()) {
                if (!trimmed.startsWith("--")) {
                    break
                }
                continue
            }
            var value = matcher.group(2)
            if (value == null) value = matcher.group(3)
            if (value == null) value = matcher.group(4)
            meta.put(
                matcher.group(1).lowercase(),
                if (value == null) "" else value.trim { it <= ' ' })
        }
        return meta
    }

    fun describeCapabilities(script: kotlin.String?): kotlin.String? {
        val capabilities = inferCapabilities(script)
        return if (capabilities.isEmpty()) "none" else TextUtils.join(", ", capabilities)
    }

    fun describeRequiredPermissions(script: kotlin.String?): kotlin.String? {
        val permissions = requiredPermissions(script)
        return if (permissions.isEmpty()) "none" else TextUtils.join(", ", permissions)
    }

    fun inferCapabilities(script: kotlin.String?): MutableList<kotlin.String?> {
        val capabilities = ArrayList<kotlin.String?>()
        val code = if (script == null) "" else script.lowercase()
        addCapability(capabilities, code.contains("http:"), "network")
        addCapability(
            capabilities, code.contains("system:to_clipboard")
                    || code.contains("system:copy_to_clipboard")
                    || code.contains("system:clipboard"), "clipboard"
        )
        addCapability(capabilities, code.contains("system:vibrate"), "vibrate")
        addCapability(
            capabilities, code.contains("ui:set_tick")
                    || code.contains("ui:set_tick_interval")
                    || code.contains("function on_tick"), "active-tick"
        )
        addCapability(
            capabilities, code.contains("files:read")
                    || code.contains("files:write")
                    || code.contains("files:append")
                    || code.contains("files:delete")
                    || code.contains("files:list")
                    || code.contains("files:exists"), "local-files"
        )
        addCapability(
            capabilities, code.contains("require \"clock\"")
                    || code.contains("require 'clock'")
                    || code.contains("clock:"), "clock"
        )
        addCapability(
            capabilities, code.contains("reminders:")
                    || code.contains("notify:"), "notifications"
        )
        addCapability(
            capabilities, code.contains("apps:")
                    || code.contains("require \"apps\"")
                    || code.contains("require 'apps'")
                    || code.contains("ui:app_button")
                    || code.contains("ui:show_app"), "apps"
        )
        addCapability(
            capabilities, code.contains("intents:")
                    || code.contains("require \"intents\"")
                    || code.contains("require 'intents'")
                    || code.contains("ui:intent_button")
                    || code.contains("ui:show_intent")
                    || code.contains("intent -"), "intents"
        )
        addCapability(
            capabilities, code.contains("shortcuts:")
                    || code.contains("require \"shortcuts\"")
                    || code.contains("require 'shortcuts'")
                    || code.contains("ui:shortcut_button")
                    || code.contains("ui:show_shortcut")
                    || code.contains("shortcut -"), "shortcuts"
        )
        addCapability(
            capabilities,
            code.contains("suggest:")
                    || code.contains("ui:suggest_"), "suggestions"
        )
        return capabilities
    }

    fun requiredPermissions(script: kotlin.String?): MutableList<kotlin.String?> {
        val required = ArrayList<kotlin.String?>()
        for (capability in inferCapabilities(script)) {
            if (SENSITIVE_CAPABILITIES.contains(capability)) {
                addCapability(required, true, capability)
            }
        }
        for (declared in declaredPermissions(script)) {
            if (SENSITIVE_CAPABILITIES.contains(declared)) {
                addCapability(required, true, declared)
            }
        }
        return required
    }

    fun declaredPermissions(script: kotlin.String?): MutableList<kotlin.String?> {
        return permissionList(metadata(script).get("permissions"))
    }

    fun missingPermissionDeclarations(script: kotlin.String?): MutableList<kotlin.String?> {
        return subtract(requiredPermissions(script), declaredPermissions(script))
    }

    fun unsupportedPermissions(script: kotlin.String?): MutableList<kotlin.String?> {
        return unsupportedPermissions(declaredPermissions(script))
    }

    private fun unsupportedPermissions(declaredPermissions: MutableList<kotlin.String?>): MutableList<kotlin.String?> {
        val unsupported = ArrayList<kotlin.String?>()
        for (permission in declaredPermissions) {
            if (!SENSITIVE_CAPABILITIES.contains(permission) && "none" != permission) {
                unsupported.add(permission)
            }
        }
        return unsupported
    }

    private fun permissionList(raw: kotlin.String?): MutableList<kotlin.String?> {
        val values = LinkedHashSet<kotlin.String?>()
        if (raw == null) {
            return ArrayList<kotlin.String?>()
        }
        for (part in raw.split("[,\\s]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val permission = normalizePermission(part)
            if (permission.length == 0 || "none" == permission) {
                continue
            }
            values.add(permission)
        }
        return ArrayList<kotlin.String?>(values)
    }

    private fun normalizePermission(permission: kotlin.String?): kotlin.String {
        return if (permission == null) "" else permission.trim { it <= ' ' }.lowercase()
    }

    private fun subtract(
        source: MutableList<kotlin.String?>,
        remove: MutableList<kotlin.String?>
    ): MutableList<kotlin.String?> {
        val out = ArrayList<kotlin.String?>()
        for (item in source) {
            if (!remove.contains(item)) {
                out.add(item)
            }
        }
        return out
    }

    private fun readManifest(id: kotlin.String?): JSONObject? {
        val manifest = manifestFile(id)
        if (!manifest.isFile()) {
            return null
        }
        try {
            FileInputStream(manifest).use { `in` ->
                return JSONObject(Tuils.convertStreamToString(`in`))
            }
        } catch (ignored: Exception) {
            return null
        }
    }

    private fun scriptHash(script: kotlin.String?): kotlin.String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(
                (if (script == null) "" else script).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
            val out = StringBuilder()
            for (b in hash) {
                out.append(kotlin.String.format(Locale.US, "%02x", b))
            }
            return out.toString()
        } catch (e: Exception) {
            return (if (script == null) "" else script).hashCode().toString()
        }
    }

    private fun addCapability(
        capabilities: MutableList<kotlin.String?>,
        enabled: Boolean,
        capability: kotlin.String?
    ) {
        if (enabled && !capabilities.contains(capability)) {
            capabilities.add(capability)
        }
    }

    private fun displayName(id: kotlin.String): kotlin.String {
        if (TextUtils.isEmpty(id)) {
            return "Lua Module"
        }
        val out = StringBuilder()
        for (part in id.split("[_-]+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            if (part.length == 0) continue
            if (out.length > 0) out.append(' ')
            out.append(part.get(0).uppercaseChar())
            if (part.length > 1) out.append(part.substring(1))
        }
        return if (out.length == 0) id else out.toString()
    }

    private fun quoteArg(value: kotlin.String?): kotlin.String {
        var safe = if (value == null) "" else value
        safe = safe.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", " ")
            .replace("\n", " ")
        return "\"" + safe + "\""
    }

    private fun appendDirective(out: StringBuilder, key: kotlin.String?, value: kotlin.String?) {
        if (out.length > 0) out.append('\n')
        out.append("::").append(key).append(' ').append(if (value == null) "" else value)
    }

    private fun appendSuggest(out: StringBuilder, label: kotlin.String?, command: kotlin.String?) {
        if (TextUtils.isEmpty(label) || TextUtils.isEmpty(command)) {
            return
        }
        if (out.length > 0) out.append('\n')
        out.append("::suggest ")
            .append(label!!.replace("|", "/"))
            .append(" | command | ")
            .append(command)
    }

    @Throws(Exception::class)
    private fun write(file: File, text: kotlin.String?) {
        val parent = file.getParentFile()
        check(!(parent != null && !parent.exists() && !parent.mkdirs())) { "Unable to create Lua module folder" }
        FileOutputStream(file, false).use { out ->
            out.write(
                (if (text == null) "" else text).toByteArray(
                    StandardCharsets.UTF_8
                )
            )
        }
    }

    private fun systemTimerScript(): kotlin.String {
        return (""
                + "-- name = \"Timer\"\n"
                + "-- type = \"module\"\n"
                + "-- retui = \"1\"\n"
                + "-- description = \"System timer module backed by Re:T-UI clock state\"\n"
                + "\n"
                + "local clock = require \"clock\"\n"
                + "local fmt = require \"fmt\"\n"
                + "\n"
                + "local BAR_WIDTH = 14\n"
                + "\n"
                + "local function ms(value)\n"
                + "    local n = tonumber(value or 0) or 0\n"
                + "    return math.max(0, n)\n"
                + "end\n"
                + "\n"
                + "local function duration(value)\n"
                + "    return clock:format_duration(ms(value))\n"
                + "end\n"
                + "\n"
                + "local function pct(elapsed, total)\n"
                + "    if total <= 0 then return 0 end\n"
                + "    return math.floor((elapsed * 100 / total) + 0.5)\n"
                + "end\n"
                + "\n"
                + "local function countdown_line(label, state)\n"
                + "    local total = ms(state.total_ms)\n"
                + "    local remaining = ms(state.remaining_ms)\n"
                + "    if state.running and total > 0 then\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_b061094464b9\", label, fmt.progress_bar(remaining, total, BAR_WIDTH), pct(remaining, total), duration(remaining)))\n"
                + "    else\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_9c3d5197daac\", label))\n"
                + "    end\n"
                + "end\n"
                + "\n"
                + "local function stopwatch_line(state)\n"
                + "    local elapsed = ms(state.elapsed_ms)\n"
                + "    if state.running or elapsed > 0 then\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_f624afb9f371\", duration(elapsed)))\n"
                + "    else\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_0aa811285aa0\"))\n"
                + "    end\n"
                + "end\n"
                + "\n"
                + "local function pomodoro_line(state)\n"
                + "    if state.running and state.type == \"finished\" then\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_2a86ca29df01\"))\n"
                + "    elseif state.running then\n"
                + "        local label = strings.localize(state.type == \"break\" and \"lua_builtin_pomodoro_break\" or \"lua_builtin_pomodoro_focus\")\n"
                + "        countdown_line(label, state)\n"
                + "        if state.task and state.task ~= \"\" then\n"
                + "            ui:add_line(strings.localize(\"lua_builtin_c6f9a6dcc58b\", state.task))\n"
                + "        end\n"
                + "    else\n"
                + "        ui:add_line(strings.localize(\"lua_builtin_8362faf545e2\"))\n"
                + "    end\n"
                + "end\n"
                + "\n"
                + "local function render()\n"
                + "    ui:set_title(strings.localize(\"lua_builtin_9d9cec22f36f\"))\n"
                + "    countdown_line(strings.localize(\"lua_builtin_9d9cec22f36f\"), clock:timer())\n"
                + "    stopwatch_line(clock:stopwatch())\n"
                + "    pomodoro_line(clock:pomodoro())\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_8c1c89354410\"), \"timer 25m\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_c5c27a7c4ac7\"), \"timer -add 5m\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_edf830938e9f\"), \"timer -add 15m\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_1b480158e1f3\"), \"timer -stop\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_48a3661d8464\"), \"timer -status\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_292b0901993f\"), \"stopwatch\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_7995059422d9\"), \"stopwatch -reset\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_539b31b6587e\"), \"pomodoro\")\n"
                + "    ui:suggest_command(strings.localize(\"lua_builtin_792c48c79564\"), \"pomodoro -stop\")\n"
                + "end\n"
                + "\n"
                + "function on_load() render() end\n"
                + "function on_resume() render() end\n")
    }

    private fun samples(): MutableMap<kotlin.String?, Sample?> {
        val samples: LinkedHashMap<kotlin.String?, Sample?> =
            LinkedHashMap<kotlin.String?, Sample?>()
        samples.put(
            "retui_counter", Sample(
                "Re:TUI Counter", (""
                        + "-- name = \"Re:TUI Counter\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- description = \"Persistent counter using Re:TUI prefs and suggestion actions\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.count == nil then prefs.count = 0 end\n"
                        + "    if prefs.step == nil then prefs.step = 1 end\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_232f662d82ef\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_0eebb3417b5c\", prefs.count, prefs.step, aio:self_name()))\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_f168b719cf9f\", prefs.step), \"inc\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_16b6ffdf1340\", prefs.step), \"dec\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_44c57abd888a\"), \"reset\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_3d7f56ffea75\"), \"prefs\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"inc\" then prefs.count = prefs.count + prefs.step end\n"
                        + "    if action == \"dec\" then prefs.count = prefs.count - prefs.step end\n"
                        + "    if action == \"reset\" then prefs.count = 0 end\n"
                        + "    if action == \"prefs\" then prefs:show_dialog() else render() end\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_progress", Sample(
                "Re:TUI Progress", (""
                        + "-- name = \"Re:TUI Progress\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- description = \"Progress Lua module using Re:TUI prefs, system state, and progress APIs\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.progress == nil then prefs.progress = 50 end\n"
                        + "    if prefs.step == nil then prefs.step = 10 end\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    local battery = system:battery_info()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_30ee2b094f6a\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_b52af31ecd75\", prefs.progress, battery.percent))\n"
                        + "    ui:show_progress_bar(strings.localize(\"lua_builtin_df5493c10c23\"), prefs.progress, 100, 14)\n"
                        + "    ui:set_progress(prefs.progress)\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_f168b719cf9f\", prefs.step), \"inc\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_16b6ffdf1340\", prefs.step), \"dec\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_44c57abd888a\"), \"reset\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_3d7f56ffea75\"), \"prefs\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"inc\" then prefs.progress = math.min(100, prefs.progress + prefs.step) end\n"
                        + "    if action == \"dec\" then prefs.progress = math.max(0, prefs.progress - prefs.step) end\n"
                        + "    if action == \"reset\" then prefs.progress = 50 end\n"
                        + "    if action == \"prefs\" then prefs:show_dialog() else render() end\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_clock", Sample(
                "Re:TUI Clock", (""
                        + "-- name = \"Re:TUI Clock\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"clipboard,vibrate\"\n"
                        + "-- description = \"Clock Lua module using Re:TUI system helpers\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.refreshes == nil then prefs.refreshes = 0 end\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    prefs.refreshes = prefs.refreshes + 1\n"
                        + "    local battery = system:battery_info()\n"
                        + "    local network = system:network_state()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_eee5eb604781\"))\n"
                        + "    ui:show_kv({\n"
                        + "        time = os.date(\"%Y-%m-%d %H:%M:%S\"),\n"
                        + "        timezone = system:tz(),\n"
                        + "        battery = battery.percent .. \"%\" .. (battery.charging and \" charging\" or \"\"),\n"
                        + "        network = network.type .. (network.connected and \" connected\" or \" offline\"),\n"
                        + "        refreshes = prefs.refreshes,\n"
                        + "    })\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_af74f7c5362a\"), \"copy\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_9c13915092a7\"), \"vibrate\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"copy\" then system:to_clipboard(os.date(\"%Y-%m-%d %H:%M:%S\")) end\n"
                        + "    if action == \"vibrate\" then system:vibrate(60) end\n"
                        + "    render()\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_expandable", Sample(
                "Re:TUI Expandable", (""
                        + "-- name = \"Re:TUI Expandable\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- description = \"Compact and expanded rendering using Re:TUI Lua module state\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.opens == nil then prefs.opens = 0 end\n"
                        + "    if prefs.expanded == nil then prefs.expanded = false end\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_e9e0d8e560ea\"))\n"
                        + "    prefs.opens = prefs.opens + 1\n"
                        + "    if prefs.expanded then\n"
                        + "        ui:show_kv({\n"
                        + "            mode = \"expanded\",\n"
                        + "            opens = prefs.opens,\n"
                        + "            time = os.date(\"%H:%M:%S\"),\n"
                        + "        })\n"
                        + "        ui:suggest_command(strings.localize(\"lua_builtin_c7f73bb54d92\"), \"config -ls\")\n"
                        + "        ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "        ui:suggest_action(strings.localize(\"lua_builtin_3babbd03dec4\"), \"toast\")\n"
                        + "        ui:suggest_action(strings.localize(\"lua_builtin_1df39aa58ad4\"), \"compact\")\n"
                        + "    else\n"
                        + "        ui:show_text(strings.localize(\"lua_builtin_6a091eb355cc\"))\n"
                        + "        ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "        ui:suggest_action(strings.localize(\"lua_builtin_9869e506c38f\"), \"expand\")\n"
                        + "    end\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"expand\" then prefs.expanded = true end\n"
                        + "    if action == \"compact\" then prefs.expanded = false end\n"
                        + "    if action == \"toast\" then ui:show_toast(strings.localize(\"lua_builtin_2eedfba70ea0\")) end\n"
                        + "    render()\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_ticker", Sample(
                "Re:TUI Ticker", (""
                        + "-- name = \"Re:TUI Ticker\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"active-tick\"\n"
                        + "-- description = \"Active Lua module ticking lifecycle demo\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.ticks == nil then prefs.ticks = 0 end\n"
                        + "end\n"
                        + "\n"
                        + "local function render(label, running)\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_5586cf3db2af\"))\n"
                        + "    if running then ui:set_tick_interval(1) else ui:disable_tick() end\n"
                        + "    ui:show_kv({\n"
                        + "        state = label,\n"
                        + "        ticks = prefs.ticks,\n"
                        + "        time = os.date(\"%H:%M:%S\"),\n"
                        + "    })\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_44c57abd888a\"), \"reset\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_9e253470c876\"), \"stop\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render(\"active\", true) end\n"
                        + "\n"
                        + "function on_tick(n)\n"
                        + "    prefs.ticks = n\n"
                        + "    render(\"tick\", true)\n"
                        + "end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"reset\" then prefs.ticks = 0 end\n"
                        + "    render(action == \"stop\" and \"stopped\" or \"reset\", action ~= \"stop\")\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_toolkit", Sample(
                "Re:TUI Toolkit", (""
                        + "-- name = \"Re:TUI Toolkit\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"clipboard\"\n"
                        + "-- description = \"Small standard library demo\"\n"
                        + "\n"
                        + "local fmt = require \"fmt\"\n"
                        + "local date = require \"date\"\n"
                        + "local strings = require \"strings\"\n"
                        + "local colors = require \"colors\"\n"
                        + "local debug = require \"debug\"\n"
                        + "\n"
                        + "function on_resume()\n"
                        + "    local title = fmt.title(\"retui toolkit\")\n"
                        + "    debug:log(\"render \" .. date.format(\"%H:%M:%S\"))\n"
                        + "    ui:set_title(title)\n"
                        + "    ui:show_kv({\n"
                        + "        time = date.format(\"%Y-%m-%d %H:%M:%S\"),\n"
                        + "        storage = fmt.bytes(123456789),\n"
                        + "        progress = fmt.progress_bar(42, 100, 10) .. \" \" .. fmt.percent(42, 100),\n"
                        + "        has_tui = tostring(strings.contains(\"Re:TUI\", \"TUI\")),\n"
                        + "        accent = colors.accent,\n"
                        + "    })\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_bd604d99e75e\"), \"debug\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_af74f7c5362a\"), \"copy\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"debug\" then debug:show() end\n"
                        + "    if action == \"copy\" then system:to_clipboard(date.format(\"%H:%M:%S\")) end\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_suggest", Sample(
                "Re:TUI Suggest", (""
                        + "-- name = \"Re:TUI Suggest\"\n"
                        + "-- type = \"suggest\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- description = \"Lua-powered command suggestion demo\"\n"
                        + "\n"
                        + "local strings = require \"strings\"\n"
                        + "local fmt = require \"fmt\"\n"
                        + "\n"
                        + "function on_suggest(query)\n"
                        + "    local q = strings.trim(fmt.lower(query))\n"
                        + "    if strings.starts_with(q, \"cfg\") or strings.starts_with(q, \"conf\") then\n"
                        + "        suggest:command(strings.localize(\"lua_builtin_95992c12d846\"), \"config -ls\")\n"
                        + "        suggest:command(strings.localize(\"lua_builtin_5e03c82554bb\"), \"config -file behavior.xml\")\n"
                        + "    end\n"
                        + "    if strings.starts_with(q, \"mod\") then\n"
                        + "        suggest:command(strings.localize(\"lua_builtin_04e9462c0ff0\"), \"module -ls\")\n"
                        + "        suggest:module(\"notifications\",strings.localize(\"lua_builtin_ac4735e1d1d9\"))\n"
                        + "    end\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_prefs", Sample(
                "Re:TUI Prefs", (""
                        + "-- name = \"Re:TUI Prefs\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    if prefs.count == nil then prefs.count = 0 end\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_158f91934e68\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_55b12130ff65\", prefs.count))\n"
                        + "    ui:suggest_action(\"+1\", \"inc\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_44c57abd888a\"), \"reset\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_3d7f56ffea75\"), \"prefs\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"inc\" then prefs.count = prefs.count + 1 end\n"
                        + "    if action == \"reset\" then prefs.count = 0 end\n"
                        + "    if action == \"prefs\" then prefs:show_dialog() else render() end\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_files", Sample(
                "Re:TUI Files", (""
                        + "-- name = \"Re:TUI Files\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"local-files\"\n"
                        + "\n"
                        + "local function number()\n"
                        + "    return tonumber(files:read(\"count.txt\") or \"0\") or 0\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    local n = number()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_ef56d82ad7ac\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_bcc61965f271\", n))\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_0c483d574e48\"), \"write\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_f6fdbe48dc54\"), \"delete\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"write\" then files:write(\"count.txt\", tostring(number() + 1)); files:append(\"log.txt\", os.date(\"%H:%M:%S\") .. \" +1\\n\") end\n"
                        + "    if action == \"delete\" then files:delete(\"count.txt\") end\n"
                        + "    render()\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_platform", Sample(
                "Re:TUI Platform", (""
                        + "-- name = \"Re:TUI Platform\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"local-files,clipboard\"\n"
                        + "-- description = \"Platform helper API demo\"\n"
                        + "\n"
                        + "local prefs = require \"prefs\"\n"
                        + "local fmt = require \"fmt\"\n"
                        + "local strings = require \"strings\"\n"
                        + "\n"
                        + "function on_load()\n"
                        + "    prefs:set(\"opens\", prefs:number(\"opens\", 0))\n"
                        + "end\n"
                        + "\n"
                        + "local function render()\n"
                        + "    local opens = prefs:inc(\"opens\", 1)\n"
                        + "    local names = files:list()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_123a7f2fcc9a\"))\n"
                        + "    ui:show_kv({\n"
                        + "        widget = system:widget_name(),\n"
                        + "        id = system:widget_id(),\n"
                        + "        app = system:app_version(),\n"
                        + "        opens = fmt.fixed(opens, 0),\n"
                        + "        files = #names,\n"
                        + "        joined = strings.join(names, \", \"),\n"
                        + "    })\n"
                        + "    ui:suggest_command(strings.localize(\"lua_builtin_04e9462c0ff0\"), \"module -ls\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_549877f1bb31\"), \"write\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_c44623f31dda\"), \"copy\")\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_719ea396ad92\"), \"clear\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_resume() render() end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    if action == \"write\" then files:append(\"platform.log\", os.date(\"%H:%M:%S\") .. \" opened\\n\") end\n"
                        + "    if action == \"copy\" then system:to_clipboard(system:widget_id()) end\n"
                        + "    if action == \"clear\" then files:delete(\"platform.log\"); prefs:unset(\"opens\") end\n"
                        + "    render()\n"
                        + "end\n")
            )
        )
        samples.put(
            "retui_public_ip", Sample(
                "Re:TUI Public IP", (""
                        + "-- name = \"Re:TUI Public IP\"\n"
                        + "-- type = \"module\"\n"
                        + "-- retui = \"1\"\n"
                        + "-- permissions = \"network\"\n"
                        + "-- data_source = \"https://api.ipify.org\"\n"
                        + "\n"
                        + "local json = require \"json\"\n"
                        + "\n"
                        + "function on_alarm()\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_44d6538e2c52\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_b04ba49f8486\"))\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "    http:get(\"https://api.ipify.org?format=json\", \"ip\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_network_result_ip(body, code)\n"
                        + "    local data = json.decode(body)\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_44d6538e2c52\"))\n"
                        + "    if code == 200 and data and data.ip then\n"
                        + "        ui:show_text(data.ip)\n"
                        + "    else\n"
                        + "        ui:show_text(strings.localize(\"lua_builtin_4c7a8d14589c\", tostring(code)))\n"
                        + "    end\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_network_error_ip(error)\n"
                        + "    ui:set_title(strings.localize(\"lua_builtin_44d6538e2c52\"))\n"
                        + "    ui:show_text(strings.localize(\"lua_builtin_116ebc4830ab\", tostring(error)))\n"
                        + "    ui:suggest_action(strings.localize(\"lua_builtin_56e3badc4e6c\"), \"refresh\")\n"
                        + "end\n"
                        + "\n"
                        + "function on_action(action)\n"
                        + "    on_alarm()\n"
                        + "end\n")
            )
        )
        return samples
    }

    class TrustStatus {
        var scriptHash: kotlin.String = ""
        var trusted: Boolean = false
        var scriptChanged: Boolean = false
        var inferredCapabilities: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        var declaredPermissions: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        var requiredPermissions: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        var missingDeclarations: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()
        var unsupportedPermissions: MutableList<kotlin.String?> = ArrayList<kotlin.String?>()

        fun canApprove(): Boolean {
            return missingDeclarations.isEmpty() && unsupportedPermissions.isEmpty()
        }
    }

    private class Sample(val name: kotlin.String?, val script: kotlin.String?)
}
