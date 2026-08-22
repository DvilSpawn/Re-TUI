package ohi.andre.consolelauncher.managers.lua

import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import ohi.andre.consolelauncher.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.compiler.LuaC
import org.luaj.vm2.lib.BaseLib
import org.luaj.vm2.lib.DebugLib
import org.luaj.vm2.lib.MathLib
import org.luaj.vm2.lib.StringLib
import org.luaj.vm2.lib.TableLib
import org.luaj.vm2.lib.VarArgFunction
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.ArrayList
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.Map
import java.util.Set
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import ohi.andre.consolelauncher.managers.ClockManager
import ohi.andre.consolelauncher.managers.PomodoroManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.tuils.Tuils

class LuaWidgetEngine(
    context: Context?,
    id: String?,
    script: String?,
    private val version: Long,
    private val updateListener: UpdateListener?
) {
    private val id: String
    private val script: String
    private val context: Context?
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private val approvedPermissions: MutableSet<String?>

    private var globals: Globals? = null
    private var prefsTable: LuaTable? = null
    private var loaded = false
    private var lastAlarmAt: Long = 0
    private var tickCount: Long = 0
    private var tickIntervalMs = -1L
    private var executionDeadlineMs: Long = 0
    private var executionInstructions = 0
    private var executionStage: String? = ""
    private var lastResult = RenderResult()
    private val httpHeaders: MutableMap<String?, String?> = LinkedHashMap<String?, String?>()
    private val debugLines = ArrayList<String?>()

    init {
        this.context = if (context == null) null else context.getApplicationContext()
        this.id = LuaWidgetManager.normalizeId(id)
        this.script = if (script == null) "" else script
        this.approvedPermissions = HashSet<String?>(LuaWidgetManager.approvedPermissions(this.id))
    }

    fun version(): Long {
        return version
    }

    @JvmOverloads
    fun render(forceAlarm: Boolean = false): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val runResume = hasFunction("on_resume")
                val runAlarm = hasFunction("on_alarm") && shouldRunAlarm(forceAlarm)
                if (runResume || runAlarm) {
                    newResult()
                    if (runResume) {
                        callIfPresent("on_resume")
                    }
                    if (runAlarm) {
                        lastAlarmAt = System.currentTimeMillis()
                        callIfPresent("on_alarm")
                    }
                }
                persistPrefs()
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun open(): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                if (!callIfPresent("on_open") && !callIfPresent("on_resume")) {
                    result.body = "No on_open handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun click(index: Int): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                if (!callIfPresent("on_click", LuaValue.valueOf(index))) {
                    result.body = "No on_click handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun input(value: String?): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                val payload: LuaValue? = LuaValue.valueOf(if (value == null) "" else value)
                if (!callIfPresent("on_input", payload) && !callIfPresent(
                        "on_submit",
                        payload
                    ) && !callIfPresent("on_command", payload)
                ) {
                    result.body = "No on_input handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun action(value: String?): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                val payload: LuaValue? = LuaValue.valueOf(if (value == null) "" else value)
                if (!callIfPresent("on_action", payload) && !callIfPresent(
                        "on_command",
                        payload
                    ) && !callIfPresent("on_submit", payload)
                ) {
                    result.body = "No on_action handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun close(): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                callIfPresent("on_close")
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun dialog(index: Int): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                if (!callIfPresent("on_dialog_action", LuaValue.valueOf(index))) {
                    result.body = "No on_dialog_action handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun config(): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                if (!callIfPresent("on_config")) {
                    result.body = "No on_config handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun submitConfig(action: String?, values: JSONObject?): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                val payload = if (values == null) JSONObject() else values
                if (!callIfPresent(
                        "on_config_submit",
                        LuaValue.valueOf(if (action == null) "" else action),
                        Companion.jsonToLua(payload)
                    )
                ) {
                    result.body = "No on_config_submit handler in " + id + "."
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun setExpanded(expanded: Boolean): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                this.isExpandableState = true
                this.isExpandedState = expanded
                persistPrefs()
                return render(false)
            } catch (e: Throwable) {
                lastResult = errorResult(e)
                return lastResult.copy()
            }
        }
    }

    fun toggleExpanded(): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                this.isExpandableState = true
                this.isExpandedState = !this.isExpandedState
                persistPrefs()
                return render(false)
            } catch (e: Throwable) {
                lastResult = errorResult(e)
                return lastResult.copy()
            }
        }
    }

    fun tick(): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                tickCount += 1
                if (!callIfPresent("on_tick", LuaValue.valueOf(tickCount.toDouble()))) {
                    tickIntervalMs = -1L
                    result.tickIntervalMs = -1L
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    fun suggest(query: String?): RenderResult {
        synchronized(this) {
            try {
                ensureLoaded()
                val result = newResult()
                if (!callIfPresent(
                        "on_suggest",
                        LuaValue.valueOf(if (query == null) "" else query)
                    )
                ) {
                    callIfPresent("on_query", LuaValue.valueOf(if (query == null) "" else query))
                }
                persistPrefs()
                lastResult = result
            } catch (e: Throwable) {
                lastResult = errorResult(e)
            }
            return lastResult.copy()
        }
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }

        val globals = safeGlobals()
        this.globals = globals
        val prefsTable = loadPrefs()
        this.prefsTable = prefsTable
        prefsTable.set(
            "show_dialog",
            UiFunction(UiAction { args: Varargs -> lastResult.body = prefsSummary() })
        )
        globals.set("io", LuaValue.NIL)
        globals.set("package", LuaValue.NIL)
        globals.set("luajava", LuaValue.NIL)
        globals.set("os", safeOsTable())
        globals.set("require", RequireFunction())
        globals.set("prefs", prefsTable)
        globals.set("ui", buildUiTable())
        globals.set("suggest", buildSuggestTable())
        globals.set("files", buildFilesTable())
        globals.set("json", buildJsonTable())
        globals.set("http", buildHttpTable())
        globals.set("system", buildSystemTable())
        globals.set("launcher", buildLauncherTable())
        globals.set("apps", buildAppsTable())
        globals.set("intents", buildIntentsTable())
        globals.set("shortcuts", buildShortcutsTable())
        globals.set("reminders", buildRemindersTable())
        globals.set("notify", globals.get("reminders"))
        globals.set("aio", buildAioTable())
        globals.set("debug", buildDebugTable())
        globals.set("date", buildDateTable())
        globals.set("fmt", buildFmtTable())
        globals.set("clock", buildClockTable())
        globals.set("strings", buildStringsTable())
        globals.set("colors", buildColorsTable())
        buildPrefsHelpers(prefsTable)
        runGuarded("load", Runnable { globals.load(script, id).call() })
        loaded = true

        val result = newResult()
        callIfPresent("on_load")
        persistPrefs()
        lastResult = result
    }

    private fun safeGlobals(): Globals {
        val safe: Globals = Globals()
        val packageTable: LuaTable = LuaTable()
        packageTable.set("loaded", LuaTable())
        safe.set("package", packageTable)
        safe.load(BaseLib())
        safe.load(TableLib())
        safe.load(StringLib())
        safe.load(MathLib())
        LoadState.install(safe)
        LuaC.install(safe)
        safe.load(GuardedDebugLib())
        safe.set("package", LuaValue.NIL)
        safe.set("dofile", LuaValue.NIL)
        safe.set("loadfile", LuaValue.NIL)
        safe.set("load", LuaValue.NIL)
        safe.set("loadstring", LuaValue.NIL)
        safe.set("collectgarbage", LuaValue.NIL)
        return safe
    }

    private fun hasFunction(name: String?): Boolean {
        val currentGlobals = globals ?: return false
        return currentGlobals.get(name).isfunction()
    }

    private fun shouldRunAlarm(forceAlarm: Boolean): Boolean {
        val now = System.currentTimeMillis()
        return forceAlarm || lastAlarmAt <= 0L || now - lastAlarmAt >= ALARM_INTERVAL_MS
    }

    private fun newResult(): RenderResult {
        val result = RenderResult()
        result.expandable = this.isExpandableState
        result.expanded = this.isExpandedState
        result.tickIntervalMs = tickIntervalMs
        lastResult = result
        return result
    }

    private fun callIfPresent(name: String?, vararg args: LuaValue?): Boolean {
        val function: LuaValue = globals?.get(name) ?: return false
        if (!function.isfunction()) {
            return false
        }
        runGuarded(name, Runnable {
            if (args == null || args.size == 0) {
                function.call()
            } else if (args.size == 1) {
                function.call(args[0])
            } else {
                val values: Array<LuaValue?> = kotlin.arrayOfNulls<LuaValue>(args.size)
                System.arraycopy(args, 0, values, 0, args.size)
                function.invoke(values)
            }
        })
        return true
    }

    private fun runGuarded(stage: String?, runnable: Runnable) {
        val previousDeadline = executionDeadlineMs
        val previousStage = executionStage
        val previousInstructions = executionInstructions
        executionDeadlineMs = System.currentTimeMillis() + EXECUTION_TIMEOUT_MS
        executionStage = if (stage == null) "lua" else stage
        executionInstructions = 0
        try {
            runnable.run()
        } finally {
            executionDeadlineMs = previousDeadline
            executionStage = previousStage
            executionInstructions = previousInstructions
        }
    }

    private fun checkTimeout() {
        if (executionDeadlineMs <= 0L) {
            return
        }
        executionInstructions += 1
        if ((executionInstructions % INSTRUCTION_CHECK_INTERVAL) != 0) {
            return
        }
        if (System.currentTimeMillis() > executionDeadlineMs) {
            throw LuaError("Lua runtime timeout in " + (if (TextUtils.isEmpty(executionStage)) "script" else executionStage))
        }
    }

    private fun buildPrefsHelpers(prefs: LuaTable) {
        prefs.set("get", ValueFunction(ValueAction { args: Varargs ->
            val key: String = stringAt(args, 1, "")
            val fallback: LuaValue = args.arg(rawIndex(args, 2))
            val value: LuaValue = prefs.get(key)
            if (value.isnil()) fallback else value
        }))
        prefs.set("set", ValueFunction(ValueAction { args: Varargs ->
            val key: String = stringAt(args, 1, "")
            if (TextUtils.isEmpty(key) || key.startsWith("_")) {
                return@ValueAction LuaValue.FALSE
            }
            prefs.set(key, args.arg(rawIndex(args, 2)))
            LuaValue.TRUE
        }))
        prefs.set(
            "has",
            ValueFunction(ValueAction { args: Varargs ->
                if (prefs.get(stringAt(args, 1, "")).isnil()) LuaValue.FALSE else LuaValue.TRUE
            })
        )
        prefs.set("unset", ValueFunction(ValueAction { args: Varargs ->
            val key: String = stringAt(args, 1, "")
            if (TextUtils.isEmpty(key) || key.startsWith("_")) {
                return@ValueAction LuaValue.FALSE
            }
            prefs.set(key, LuaValue.NIL)
            LuaValue.TRUE
        }))
        prefs.set("number", ValueFunction(ValueAction { args: Varargs ->
            val value: LuaValue = prefs.get(stringAt(args, 1, ""))
            if (value.isnumber()) {
                return@ValueAction LuaValue.valueOf(value.todouble())
            }
            if (value.isstring()) {
                try {
                    return@ValueAction LuaValue.valueOf(value.tojstring().toDouble())
                } catch (ignored: Exception) {
                }
            }
            LuaValue.valueOf(numberAt(args, 2, 0.0))
        }))
        prefs.set("bool", ValueFunction(ValueAction { args: Varargs ->
            val value: LuaValue = prefs.get(stringAt(args, 1, ""))
            if (value.isboolean()) {
                return@ValueAction if (value.toboolean()) LuaValue.TRUE else LuaValue.FALSE
            }
            if (value.isnumber()) {
                return@ValueAction if (value.todouble() != 0.0) LuaValue.TRUE else LuaValue.FALSE
            }
            if (value.isstring()) {
                val text = value.tojstring().trim { it <= ' ' }.lowercase()
                return@ValueAction if ("true" == text || "yes" == text || "1" == text || "on" == text) LuaValue.TRUE else LuaValue.FALSE
            }
            if (booleanAt(args, 2, false)) LuaValue.TRUE else LuaValue.FALSE
        }))
        prefs.set("inc", ValueFunction(ValueAction { args: Varargs ->
            val key: String = stringAt(args, 1, "")
            if (TextUtils.isEmpty(key) || key.startsWith("_")) {
                return@ValueAction LuaValue.NIL
            }
            val current: LuaValue = prefs.get(key)
            var base = 0.0
            if (current.isnumber()) {
                base = current.todouble()
            } else if (current.isstring()) {
                try {
                    base = current.tojstring().toDouble()
                } catch (ignored: Exception) {
                }
            }
            val next: Double = base + numberAt(args, 2, 1.0)
            prefs.set(key, LuaValue.valueOf(next))
            LuaValue.valueOf(next)
        }))
    }

    private fun buildUiTable(): LuaTable {
        val ui: LuaTable = LuaTable()
        ui.set(
            "set_title",
            UiFunction(UiAction { args: Varargs -> lastResult.title = stringArg(args) })
        )
        ui.set(
            "title",
            UiFunction(UiAction { args: Varargs -> lastResult.title = stringArg(args) })
        )
        ui.set(
            "default_title",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    LuaWidgetManager.getName(id)
                )
            })
        )
        ui.set(
            "get_default_title",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    LuaWidgetManager.getName(id)
                )
            })
        )
        ui.set(
            "show_text",
            UiFunction(UiAction { args: Varargs -> lastResult.body = stringArg(args) })
        )
        ui.set("clear", UiFunction(UiAction { args: Varargs ->
            lastResult.body = ""
            lastResult.layoutJson = ""
            lastResult.buttons.clear()
            lastResult.commands.clear()
            lastResult.valueActions.clear()
            lastResult.suggestions.clear()
            lastResult.dialogOpen = false
            lastResult.dialogTitle = ""
            lastResult.dialogItems.clear()
            lastResult.dialogSelected = -1
            lastResult.progress = -1.0
        }))
        ui.set("render", UiFunction(UiAction { args: Varargs -> this.setLayout(args) }))
        ui.set("layout", UiFunction(UiAction { args: Varargs -> this.setLayout(args) }))
        ui.set(
            "text",
            UiFunction(UiAction { args: Varargs ->
                lastResult.body = appendLine(lastResult.body, stringArg(args))
            })
        )
        ui.set(
            "body",
            UiFunction(UiAction { args: Varargs ->
                lastResult.body = appendLine(lastResult.body, stringArg(args))
            })
        )
        ui.set(
            "add_line",
            UiFunction(UiAction { args: Varargs ->
                lastResult.body = appendLine(lastResult.body, stringArg(args))
            })
        )
        ui.set("show_lines", UiFunction(UiAction { args: Varargs ->
            lastResult.body = tableLines(
                tableArg(args)
            )
        }))
        ui.set(
            "lines",
            UiFunction(UiAction { args: Varargs -> lastResult.body = tableLines(tableArg(args)) })
        )
        ui.set("show_table", UiFunction(UiAction { args: Varargs ->
            lastResult.body = tableRows(
                tableArg(args)
            )
        }))
        ui.set("show_kv", UiFunction(UiAction { args: Varargs ->
            lastResult.body = tableKeyValues(
                tableArg(args)
            )
        }))
        ui.set("kv", UiFunction(UiAction { args: Varargs ->
            lastResult.body = tableKeyValues(
                tableArg(args)
            )
        }))
        ui.set("show_buttons", UiFunction(UiAction { args: Varargs ->
            lastResult.buttons = tableStrings(
                tableArg(args)
            )
        }))
        ui.set("buttons", UiFunction(UiAction { args: Varargs ->
            lastResult.buttons = tableStrings(
                tableArg(args)
            )
        }))
        ui.set("add_button", UiFunction(UiAction { args: Varargs ->
            val label: String = stringArg(args)
            if (!TextUtils.isEmpty(label)) {
                lastResult.buttons.add(label)
            }
        }))
        ui.set("button", UiFunction(UiAction { args: Varargs -> this.addButton(args) }))
        ui.set("show_action", UiFunction(UiAction { args: Varargs -> this.addValueAction(args) }))
        ui.set("action", UiFunction(UiAction { args: Varargs -> this.addValueAction(args) }))
        ui.set("action_button", UiFunction(UiAction { args: Varargs -> this.addValueAction(args) }))
        ui.set("add_action", UiFunction(UiAction { args: Varargs -> this.addValueAction(args) }))
        ui.set("suggest_action", UiFunction(UiAction { args: Varargs -> this.addSuggestionAction(args) }))
        ui.set("suggest_command", UiFunction(UiAction { args: Varargs ->
            addSuggestionCommand(stringAt(args, 1, ""), stringAt(args, 2, ""))
        }))
        ui.set("suggest_module", UiFunction(UiAction { args: Varargs -> this.addSuggestionModule(args) }))
        ui.set("suggest_text", UiFunction(UiAction { args: Varargs -> this.addSuggestionText(args) }))
        ui.set("show_command", UiFunction(UiAction { args: Varargs ->
            val label: String = stringAt(args, 1, "")
            val command: String = stringAt(args, 2, "")
            if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(command)) {
                lastResult.commands.add(RenderAction(label, command))
            }
        }))
        ui.set("command", UiFunction(UiAction { args: Varargs ->
            val label: String = stringAt(args, 1, "")
            val command: String = stringAt(args, 2, "")
            if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(command)) {
                lastResult.commands.add(RenderAction(label, command))
            }
        }))
        ui.set("command_button", UiFunction(UiAction { args: Varargs ->
            addCommandButton(stringAt(args, 1, ""), stringAt(args, 2, ""))
        }))
        ui.set("show_module", UiFunction(UiAction { args: Varargs ->
            val module: String = stringAt(args, 1, "")
            val label: String = stringAt(args, 2, module)
            if (!TextUtils.isEmpty(module)) {
                lastResult.commands.add(RenderAction(label, "module -show " + module))
            }
        }))
        ui.set("module_button", UiFunction(UiAction { args: Varargs ->
            val label: String = stringAt(args, 1, "")
            val module: String = stringAt(args, 2, label)
            addCommandButton(label, "module -show " + module)
        }))
        ui.set("show_app", UiFunction(UiAction { args: Varargs -> this.addAppButton(args) }))
        ui.set("app_button", UiFunction(UiAction { args: Varargs -> this.addAppButton(args) }))
        ui.set("show_intent", UiFunction(UiAction { args: Varargs -> this.addIntentButton(args) }))
        ui.set("intent_button", UiFunction(UiAction { args: Varargs -> this.addIntentButton(args) }))
        ui.set("show_shortcut", UiFunction(UiAction { args: Varargs -> this.addShortcutButton(args) }))
        ui.set("shortcut_button", UiFunction(UiAction { args: Varargs -> this.addShortcutButton(args) }))
        ui.set("show_config", UiFunction(UiAction { args: Varargs ->
            val value: LuaValue = tableArg(args)
            if (!value.istable()) {
                lastResult.configJson = ""
                lastResult.body = appendLine(lastResult.body, "Invalid config schema.")
                return@UiAction
            }
            try {
                val jsonValue: Any? = luaToJson(value)
                if (jsonValue is JSONObject) {
                    lastResult.configJson = jsonValue.toString()
                } else {
                    lastResult.configJson = ""
                    lastResult.body = appendLine(lastResult.body, "Invalid config schema.")
                }
            } catch (e: Exception) {
                lastResult.configJson = ""
                lastResult.body = appendLine(lastResult.body, "Invalid config schema: " + e.message)
            }
        }))
        ui.set("config", UiFunction(UiAction { args: Varargs ->
            val value: LuaValue = tableArg(args)
            if (!value.istable()) {
                lastResult.configJson = ""
                lastResult.body = appendLine(lastResult.body, "Invalid config schema.")
                return@UiAction
            }
            try {
                val jsonValue: Any? = luaToJson(value)
                if (jsonValue is JSONObject) {
                    lastResult.configJson = jsonValue.toString()
                } else {
                    lastResult.configJson = ""
                    lastResult.body = appendLine(lastResult.body, "Invalid config schema.")
                }
            } catch (e: Exception) {
                lastResult.configJson = ""
                lastResult.body = appendLine(lastResult.body, "Invalid config schema: " + e.message)
            }
        }))
        ui.set("show_progress_bar", UiFunction(UiAction { args: Varargs ->
            val label: String = stringAt(args, 1, "Progress")
            val current: Double = numberAt(args, 2, 0.0)
            val max: Double = numberAt(args, 3, 100.0)
            val width = numberAt(args, 4, DEFAULT_PROGRESS_BAR_WIDTH.toDouble()).toInt()
            lastResult.body =
                appendLine(lastResult.body, formatProgressLine(label, current, max, width))
        }))
        ui.set("set_progress", UiFunction(UiAction { args: Varargs ->
            val pct = max(0.0, min(100.0, numberAt(args, 1, 0.0)))
            lastResult.progress = pct
        }))
        ui.set(
            "set_tick_interval",
            UiFunction(UiAction { args: Varargs -> setTickInterval(numberAt(args, 1, 1.0)) })
        )
        ui.set(
            "set_tick",
            UiFunction(UiAction { args: Varargs -> setTickInterval(numberAt(args, 1, 1.0)) })
        )
        ui.set("disable_tick", UiFunction(UiAction { args: Varargs -> setTickInterval(0.0) }))
        ui.set("show_toast", UiFunction(UiAction { args: Varargs ->
            val text: String = stringArg(args)
            showToast(text)
            lastResult.body = appendLine(lastResult.body, "[toast] " + text)
        }))
        ui.set(
            "is_folded",
            ValueFunction(ValueAction { args: Varargs -> if (this.isFoldedState) LuaValue.TRUE else LuaValue.FALSE })
        )
        ui.set(
            "is_expanded",
            ValueFunction(ValueAction { args: Varargs -> if (this.isExpandedState) LuaValue.TRUE else LuaValue.FALSE })
        )
        ui.set("set_expandable", UiFunction(UiAction { args: Varargs ->
            this.isExpandableState = booleanAt(args, 1, true)
        }))
        ui.set("expand", UiFunction(UiAction { args: Varargs -> this.isExpandedState = true }))
        ui.set("collapse", UiFunction(UiAction { args: Varargs -> this.isExpandedState = false }))
        ui.set("toggle", UiFunction(UiAction { args: Varargs ->
            this.isExpandedState = !this.isExpandedState
        }))
        ui.set(
            "show_radio_dialog",
            UiFunction(UiAction { args: Varargs -> this.showChoiceDialog(args) })
        )
        ui.set(
            "show_list_dialog",
            UiFunction(UiAction { args: Varargs -> this.showChoiceDialog(args) })
        )
        return ui
    }

    private fun buildSuggestTable(): LuaTable {
        val suggest: LuaTable = LuaTable()
        suggest.set("command", UiFunction(UiAction { args: Varargs ->
            val label: String = stringAt(args, 1, "")
            val command: String = stringAt(args, 2, "")
            addSuggestionCommand(label, command)
        }))
        suggest.set("module", UiFunction(UiAction { args: Varargs ->
            addSuggestionModule(args)
        }))
        suggest.set("text", UiFunction(UiAction { args: Varargs ->
            addSuggestionText(args)
        }))
        return suggest
    }

    private fun buildFilesTable(): LuaTable {
        val files: LuaTable = LuaTable()
        files.set("read", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val file = dataFile(stringAt(args, 1, ""))
            if (file == null || !file.isFile()) {
                return@ValueAction LuaValue.NIL
            }
            ensureReadableFile(file)
            try {
                FileInputStream(file).use { `in` ->
                    return@ValueAction LuaValue.valueOf(Tuils.convertStreamToString(`in`))
                }
            } catch (e: Exception) {
                return@ValueAction LuaValue.NIL
            }
        }))
        files.set("write", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val file = dataFile(stringAt(args, 1, ""))
            if (file == null) {
                return@ValueAction LuaValue.FALSE
            }
            try {
                val bytes = stringAt(args, 2, "").toByteArray(StandardCharsets.UTF_8)
                ensureFileWriteAllowed(file, bytes.size.toLong(), false)
                val parent = file.getParentFile()
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                FileOutputStream(file, false).use { out ->
                    out.write(bytes)
                }
                return@ValueAction LuaValue.TRUE
            } catch (e: Exception) {
                return@ValueAction LuaValue.FALSE
            }
        }))
        files.set("append", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val file = dataFile(stringAt(args, 1, ""))
            if (file == null) {
                return@ValueAction LuaValue.FALSE
            }
            try {
                val bytes = stringAt(args, 2, "").toByteArray(StandardCharsets.UTF_8)
                ensureFileWriteAllowed(file, bytes.size.toLong(), true)
                val parent = file.getParentFile()
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                FileOutputStream(file, true).use { out ->
                    out.write(bytes)
                }
                return@ValueAction LuaValue.TRUE
            } catch (e: Exception) {
                return@ValueAction LuaValue.FALSE
            }
        }))
        files.set("exists", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val file = dataFile(stringAt(args, 1, ""))
            if (file != null && file.exists()) LuaValue.TRUE else LuaValue.FALSE
        }))
        files.set("list", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val dir = File(LuaWidgetManager.widgetDir(id), "files")
            val table: LuaTable = LuaTable()
            val filesInDir = dir.listFiles()
            if (filesInDir == null) {
                return@ValueAction table
            }
            Arrays.sort<File?>(
                filesInDir,
                Comparator { a: File?, b: File? ->
                    a!!.getName().compareTo(b!!.getName(), ignoreCase = true)
                })
            var index = 1
            for (file in filesInDir) {
                if (file.isFile()) {
                    table.set(index++, LuaValue.valueOf(file.getName()))
                }
            }
            table
        }))
        files.set("delete", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("local-files")
            val file = dataFile(stringAt(args, 1, ""))
            if (file != null && (!file.exists() || file.delete())) LuaValue.TRUE else LuaValue.FALSE
        }))
        return files
    }

    private fun buildJsonTable(): LuaTable {
        val json: LuaTable = LuaTable()
        json.set("decode", ValueFunction(ValueAction { args: Varargs ->
            val value = stringArg(args).trim { it <= ' ' }
            if (value.length == 0) {
                return@ValueAction LuaValue.NIL
            }
            try {
                if (value.startsWith("[")) {
                    return@ValueAction Companion.jsonToLua(JSONArray(value))
                }
                return@ValueAction Companion.jsonToLua(JSONObject(value))
            } catch (e: Exception) {
                return@ValueAction LuaValue.NIL
            }
        }))
        json.set("encode", ValueFunction(ValueAction { args: Varargs ->
            try {
                val `object`: Any? = luaToJson(args.arg(rawIndex(args, 1)))
                if (`object` == null || `object` === JSONObject.NULL) {
                    return@ValueAction LuaValue.valueOf("null")
                }
                if (`object` is String) {
                    return@ValueAction LuaValue.valueOf(JSONObject.quote(`object`))
                }
                return@ValueAction LuaValue.valueOf(`object`.toString())
            } catch (e: Exception) {
                return@ValueAction LuaValue.NIL
            }
        }))
        return json
    }

    private fun buildHttpTable(): LuaTable {
        val http: LuaTable = LuaTable()
        http.set("set_headers", UiFunction(UiAction { args: Varargs ->
            httpHeaders.clear()
            val table: LuaValue = tableArg(args)
            if (table.istable()) {
                val length: Int = table.length()
                for (i in 1..length) {
                    val header: String = table.get(i).tojstring()
                    val split = header.indexOf(':')
                    if (split > 0) {
                        httpHeaders.put(
                            header.substring(0, split).trim { it <= ' ' },
                            header.substring(split + 1).trim { it <= ' ' })
                    }
                }
            }
        }))
        http.set("get", UiFunction(UiAction { args: Varargs ->
            requirePermission("network")
            enqueueHttp("GET", stringAt(args, 1, ""), null, null, stringAt(args, 2, ""))
        }))
        http.set("delete", UiFunction(UiAction { args: Varargs ->
            requirePermission("network")
            enqueueHttp("DELETE", stringAt(args, 1, ""), null, null, stringAt(args, 2, ""))
        }))
        http.set("post", UiFunction(UiAction { args: Varargs ->
            requirePermission("network")
            enqueueHttp(
                "POST",
                stringAt(args, 1, ""),
                stringAt(args, 2, ""),
                stringAt(args, 3, ""),
                stringAt(args, 4, "")
            )
        }))
        http.set("put", UiFunction(UiAction { args: Varargs ->
            requirePermission("network")
            enqueueHttp(
                "PUT",
                stringAt(args, 1, ""),
                stringAt(args, 2, ""),
                stringAt(args, 3, ""),
                stringAt(args, 4, "")
            )
        }))
        return http
    }

    private fun buildSystemTable(): LuaTable {
        val system: LuaTable = LuaTable()
        system.set("open_browser", UiFunction(UiAction { args: Varargs ->
            openUrl(stringArg(args))
        }))
        system.set("open_url", UiFunction(UiAction { args: Varargs ->
            openUrl(stringArg(args))
        }))
        system.set("to_clipboard", UiFunction(UiAction { args: Varargs ->
            requirePermission("clipboard")
            copyToClipboard(stringArg(args))
        }))
        system.set("copy_to_clipboard", UiFunction(UiAction { args: Varargs ->
            requirePermission("clipboard")
            copyToClipboard(stringArg(args))
        }))
        system.set("clipboard", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("clipboard")
            LuaValue.valueOf(readClipboard())
        }))
        system.set("vibrate", UiFunction(UiAction { args: Varargs ->
            requirePermission("vibrate")
            vibrate(numberAt(args, 1, 80.0).toLong())
        }))
        system.set(
            "lang",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    Locale.getDefault().getLanguage()
                )
            })
        )
        system.set(
            "tz",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    TimeZone.getDefault().getID()
                )
            })
        )
        system.set("tz_offset", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
            )
        }))
        system.set("battery_info", ValueFunction(ValueAction { args: Varargs -> batteryInfo() }))
        system.set("network_state", ValueFunction(ValueAction { args: Varargs -> networkState() }))
        system.set(
            "app_version",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(BuildConfig.VERSION_NAME) })
        )
        system.set(
            "app_version_code",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(BuildConfig.VERSION_CODE) })
        )
        system.set(
            "widget_id",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(id) })
        )
        system.set(
            "widget_name",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    LuaWidgetManager.getName(id)
                )
            })
        )
        return system
    }

    private fun buildLauncherTable(): LuaTable {
        val launcher = LuaTable()
        launcher.set("state", ValueFunction(ValueAction { args: Varargs ->
            val state = LuaTable()
            state.set("widget_id", LuaValue.valueOf(id))
            state.set("widget_name", LuaValue.valueOf(LuaWidgetManager.getName(id)))
            state.set("app_version", LuaValue.valueOf(BuildConfig.VERSION_NAME))
            state.set("app_version_code", LuaValue.valueOf(BuildConfig.VERSION_CODE))
            state.set("language", LuaValue.valueOf(Locale.getDefault().getLanguage()))
            state.set("timezone", LuaValue.valueOf(TimeZone.getDefault().getID()))
            state.set("battery", batteryInfo())
            state.set("network", networkState())
            state
        }))
        launcher.set("vars", ValueFunction(ValueAction { args: Varargs ->
            val vars = LuaTable()
            vars.set("launcher", LuaValue.valueOf("state, vars, command_button, module_button"))
            vars.set("apps", LuaValue.valueOf("list, find, launch_command, button"))
            vars.set("intents", LuaValue.valueOf("view, activity, button"))
            vars.set("shortcuts", LuaValue.valueOf("use_command, button"))
            vars.set("ui", LuaValue.valueOf("render, button, command_button, module_button, app_button, intent_button, shortcut_button, suggest_action, suggest_command, suggest_module, suggest_text"))
            vars
        }))
        launcher.set("command_button", UiFunction(UiAction { args: Varargs ->
            addCommandButton(stringAt(args, 1, ""), stringAt(args, 2, ""))
        }))
        launcher.set("module_button", UiFunction(UiAction { args: Varargs ->
            val label = stringAt(args, 1, "")
            val module = stringAt(args, 2, label)
            addCommandButton(label, "module -show " + module)
        }))
        return launcher
    }

    private fun buildAppsTable(): LuaTable {
        val apps = LuaTable()
        apps.set("list", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("apps")
            val limit = max(0, numberAt(args, 1, 50.0).toInt())
            val table = LuaTable()
            val entries = launchableApps()
            val count = if (limit == 0) entries.size else min(limit, entries.size)
            for (i in 0..<count) {
                table.set(i + 1, appEntryTable(entries[i]))
            }
            table
        }))
        apps.set("find", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("apps")
            val app = resolveLaunchableApp(stringArg(args))
            if (app == null) LuaValue.NIL else appEntryTable(app)
        }))
        apps.set("launch_command", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("apps")
            val app = resolveLaunchableApp(stringArg(args))
            LuaValue.valueOf(app?.command() ?: "")
        }))
        apps.set("button", UiFunction(UiAction { args: Varargs -> this.addAppButton(args) }))
        return apps
    }

    private fun appEntryTable(app: AppEntry): LuaTable {
        val table = LuaTable()
        table.set("name", LuaValue.valueOf(app.label))
        table.set("label", LuaValue.valueOf(app.label))
        table.set("package", LuaValue.valueOf(app.packageName))
        table.set("class", LuaValue.valueOf(app.component.className))
        table.set("component", LuaValue.valueOf(app.component.flattenToString()))
        table.set("command", LuaValue.valueOf(app.command()))
        return table
    }

    private fun buildIntentsTable(): LuaTable {
        val intents = LuaTable()
        intents.set("view", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("intents")
            LuaValue.valueOf("intent -view " + quoteCommandArg(stringArg(args)))
        }))
        intents.set("activity", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("intents")
            LuaValue.valueOf("intent -activity -n " + quoteCommandArg(stringArg(args)))
        }))
        intents.set("command", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("intents")
            LuaValue.valueOf(commandFromIntentSpec(args.arg(rawIndex(args, 1))) ?: "")
        }))
        intents.set("button", UiFunction(UiAction { args: Varargs -> this.addIntentButton(args) }))
        return intents
    }

    private fun buildShortcutsTable(): LuaTable {
        val shortcuts = LuaTable()
        shortcuts.set("use_command", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("shortcuts")
            val shortcutId = stringAt(args, 1, "")
            val app = stringAt(args, 2, "")
            val command = StringBuilder("shortcut -use ")
                .append(quoteCommandArg(shortcutId))
            if (!TextUtils.isEmpty(app)) {
                command.append(' ').append(quoteCommandArg(app))
            }
            LuaValue.valueOf(command.toString())
        }))
        shortcuts.set("button", UiFunction(UiAction { args: Varargs -> this.addShortcutButton(args) }))
        return shortcuts
    }

    private fun buildRemindersTable(): LuaTable {
        val reminders: LuaTable = LuaTable()
        reminders.set("daily", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("notifications")
            val reminderId: String = stringAt(args, 1, "")
            val title: String = stringAt(args, 2, "")
            val time: String = stringAt(args, 3, "")
            val atMillis = LuaWidgetReminderManager.nextDailyMillis(time)
            if (context == null || TextUtils.isEmpty(reminderId) || atMillis <= 0L) {
                return@ValueAction LuaValue.FALSE
            }
            LuaWidgetReminderManager.schedule(context, id, reminderId, title, atMillis, "daily")
            LuaValue.TRUE
        }))
        reminders.set("once", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("notifications")
            val reminderId: String = stringAt(args, 1, "")
            val title: String = stringAt(args, 2, "")
            val dateTime: String = stringAt(args, 3, "")
            val atMillis = LuaWidgetReminderManager.parseLocalDateTimeMillis(dateTime)
            if (context == null || TextUtils.isEmpty(reminderId) || atMillis <= 0L) {
                return@ValueAction LuaValue.FALSE
            }
            LuaWidgetReminderManager.schedule(context, id, reminderId, title, atMillis, "once")
            LuaValue.TRUE
        }))
        reminders.set("schedule", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("notifications")
            val reminderId: String = stringAt(args, 1, "")
            val title: String = stringAt(args, 2, "")
            val atSeconds = numberAt(args, 3, 0.0)
            val repeat: String = stringAt(args, 4, "once")
            val atMillis = if (atSeconds > 100000000000.0) atSeconds.toLong() else (atSeconds * 1000.0).toLong()
            if (context == null || TextUtils.isEmpty(reminderId) || atMillis <= 0L) {
                return@ValueAction LuaValue.FALSE
            }
            LuaWidgetReminderManager.schedule(context, id, reminderId, title, atMillis, repeat)
            LuaValue.TRUE
        }))
        reminders.set("cancel", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("notifications")
            if (context == null) {
                return@ValueAction LuaValue.FALSE
            }
            LuaWidgetReminderManager.cancel(context, id, stringAt(args, 1, ""))
            LuaValue.TRUE
        }))
        reminders.set("cancel_prefix", ValueFunction(ValueAction { args: Varargs ->
            requirePermission("notifications")
            if (context == null) {
                return@ValueAction LuaValue.FALSE
            }
            LuaWidgetReminderManager.cancelPrefix(context, id, stringAt(args, 1, ""))
            LuaValue.TRUE
        }))
        reminders.set("list", ValueFunction(ValueAction { args: Varargs ->
            if (context == null) {
                return@ValueAction LuaTable()
            }
            Companion.jsonToLua(LuaWidgetReminderManager.list(context, id))
        }))
        return reminders
    }

    private fun buildAioTable(): LuaTable {
        val aio: LuaTable = LuaTable()
        aio.set("self_name", ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(id) }))
        aio.set(
            "widget_name",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    LuaWidgetManager.getName(id)
                )
            })
        )
        aio.set(
            "retui_version",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(BuildConfig.VERSION_NAME) })
        )
        aio.set("show_toast", UiFunction(UiAction { args: Varargs ->
            showToast(stringArg(args))
        }))
        aio.set("colors", ValueFunction(ValueAction { args: Varargs ->
            val colors: LuaTable = LuaTable()
            colors.set("primary_text", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set(
                "secondary_text", hex(
                    Color.argb(
                        190,
                        Color.red(AppearanceSettings.moduleNameTextColor()),
                        Color.green(AppearanceSettings.moduleNameTextColor()),
                        Color.blue(AppearanceSettings.moduleNameTextColor())
                    )
                )
            )
            colors.set("button", hex(AppearanceSettings.moduleButtonBackgroundColor()))
            colors.set("button_text", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set("progress", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set("accent", hex(AppearanceSettings.moduleButtonBorderColor()))
            colors
        }))
        return aio
    }

    private fun buildDebugTable(): LuaTable {
        val debug: LuaTable = LuaTable()
        debug.set("log", UiFunction(UiAction { args: Varargs ->
            val text: String = stringArg(args)
            Log.d("ReTUI-Lua", id + ": " + text)
            debugLines.add(text)
            if (debugLines.size > 50) {
                debugLines.removeAt(0)
            }
            lastResult.debug = ArrayList<String?>(debugLines)
        }))
        debug.set("toast", UiFunction(UiAction { args: Varargs -> showToast(stringArg(args)) }))
        debug.set("show", UiFunction(UiAction { args: Varargs ->
            if (debugLines.isEmpty()) {
                lastResult.body = appendLine(lastResult.body, "No debug lines.")
            } else {
                lastResult.body = appendLine(lastResult.body, TextUtils.join("\n", debugLines))
            }
        }))
        debug.set("clear", UiFunction(UiAction { args: Varargs ->
            debugLines.clear()
            lastResult.debug.clear()
        }))
        return debug
    }

    private fun buildDateTable(): LuaTable {
        val date: LuaTable = LuaTable()
        date.set(
            "now",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    System.currentTimeMillis().toDouble()
                )
            })
        )
        date.set(
            "seconds",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf((System.currentTimeMillis() / 1000L).toDouble()) })
        )
        date.set("format", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                osDate(
                    stringAt(args, 1, "%Y-%m-%d %H:%M:%S"),
                    numberAt(args, 2, System.currentTimeMillis() / 1000.0)
                )
            )
        }))
        date.set("parts", ValueFunction(ValueAction { args: Varargs ->
            val calendar = Calendar.getInstance()
            val table: LuaTable = LuaTable()
            table.set("year", LuaValue.valueOf(calendar.get(Calendar.YEAR)))
            table.set("month", LuaValue.valueOf(calendar.get(Calendar.MONTH) + 1))
            table.set("day", LuaValue.valueOf(calendar.get(Calendar.DAY_OF_MONTH)))
            table.set("hour", LuaValue.valueOf(calendar.get(Calendar.HOUR_OF_DAY)))
            table.set("minute", LuaValue.valueOf(calendar.get(Calendar.MINUTE)))
            table.set("second", LuaValue.valueOf(calendar.get(Calendar.SECOND)))
            table.set("weekday", LuaValue.valueOf(calendar.get(Calendar.DAY_OF_WEEK)))
            table
        }))
        return date
    }

    private fun buildFmtTable(): LuaTable {
        val fmt: LuaTable = LuaTable()
        fmt.set(
            "upper",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(stringArg(args).uppercase()) })
        )
        fmt.set(
            "lower",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(stringArg(args).lowercase()) })
        )
        fmt.set("title", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                titleCase(
                    stringArg(args)
                )
            )
        }))
        fmt.set("percent", ValueFunction(ValueAction { args: Varargs ->
            val value: Double = numberAt(args, 1, 0.0)
            val max = max(1.0, numberAt(args, 2, 100.0))
            LuaValue.valueOf(Math.round((value * 100.0) / max).toString() + "%")
        }))
        fmt.set("progress_bar", ValueFunction(ValueAction { args: Varargs ->
            val value: Double = numberAt(args, 1, 0.0)
            val max: Double = numberAt(args, 2, 100.0)
            val width = numberAt(args, 3, DEFAULT_PROGRESS_BAR_WIDTH.toDouble()).toInt()
            LuaValue.valueOf(formatProgressBar(value, max, width))
        }))
        fmt.set("bytes", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                formatBytes(
                    numberAt(args, 1, 0.0)
                )
            )
        }))
        fmt.set("round", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                Math.round(
                    numberAt(args, 1, 0.0)
                ).toDouble()
            )
        }))
        fmt.set("fixed", ValueFunction(ValueAction { args: Varargs ->
            val value: Double = numberAt(args, 1, 0.0)
            val places = max(0, min(6, numberAt(args, 2, 1.0).toInt()))
            LuaValue.valueOf(String.format(Locale.US, "%." + places + "f", value))
        }))
        fmt.set("pad_left", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                pad(
                    stringAt(args, 1, ""), numberAt(args, 2, 0.0).toInt(), true
                )
            )
        }))
        fmt.set("pad_right", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                pad(
                    stringAt(args, 1, ""), numberAt(args, 2, 0.0).toInt(), false
                )
            )
        }))
        return fmt
    }

    private fun buildClockTable(): LuaTable {
        val clock: LuaTable = LuaTable()
        clock.set("timer", ValueFunction(ValueAction { args: Varargs -> timerState() }))
        clock.set("stopwatch", ValueFunction(ValueAction { args: Varargs -> stopwatchState() }))
        clock.set("pomodoro", ValueFunction(ValueAction { args: Varargs -> pomodoroState() }))
        clock.set("format_duration", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                ClockManager.formatDuration(
                    numberAt(args, 1, 0.0).toLong()
                )
            )
        }))
        clock.set("parse_duration", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                ClockManager.parseDurationMillis(
                    stringAt(args, 1, "")
                ).toDouble()
            )
        }))
        return clock
    }

    private fun buildStringsTable(): LuaTable {
        val strings: LuaTable = LuaTable()
        strings.set(
            "trim",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(stringArg(args).trim { it <= ' ' }) })
        )
        strings.set("contains", ValueFunction(ValueAction { args: Varargs ->
            if (stringAt(args, 1, "").contains(
                    stringAt(args, 2, "")
                )
            ) LuaValue.TRUE else LuaValue.FALSE
        }))
        strings.set("starts_with", ValueFunction(ValueAction { args: Varargs ->
            if (stringAt(args, 1, "").startsWith(
                    stringAt(args, 2, "")
                )
            ) LuaValue.TRUE else LuaValue.FALSE
        }))
        strings.set("ends_with", ValueFunction(ValueAction { args: Varargs ->
            if (stringAt(args, 1, "").endsWith(
                    stringAt(args, 2, "")
                )
            ) LuaValue.TRUE else LuaValue.FALSE
        }))
        strings.set("replace", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                stringAt(args, 1, "").replace(stringAt(args, 2, ""), stringAt(args, 3, ""))
            )
        }))
        strings.set("split", ValueFunction(ValueAction { args: Varargs ->
            val text: String = stringAt(args, 1, "")
            val delimiter: String = stringAt(args, 2, ",")
            val table: LuaTable = LuaTable()
            val parts: Array<String?> =
                text.split(Pattern.quote(delimiter).toRegex()).toTypedArray()
            for (i in parts.indices) {
                table.set(i + 1, LuaValue.valueOf(parts[i]))
            }
            table
        }))
        strings.set("join", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                TextUtils.join(
                    stringAt(args, 2, ","), tableStrings(tableArg(args))
                )
            )
        }))
        return strings
    }

    private fun buildColorsTable(): LuaTable {
        return colorsTable()
    }

    private fun timerState(): LuaTable {
        val table: LuaTable = LuaTable()
        if (context == null) {
            table.set("running", LuaValue.FALSE)
            table.set("remaining_ms", LuaValue.ZERO)
            table.set("total_ms", LuaValue.ZERO)
            table.set("elapsed_ms", LuaValue.ZERO)
            return table
        }
        val clockManager: ClockManager = ClockManager.getInstance(context)
        val remaining: Long = clockManager.timerRemainingMillis
        val total: Long = clockManager.timerTotalMillis
        table.set("running", if (clockManager.isTimerRunning) LuaValue.TRUE else LuaValue.FALSE)
        table.set("remaining_ms", LuaValue.valueOf(remaining.toDouble()))
        table.set("total_ms", LuaValue.valueOf(total.toDouble()))
        table.set("elapsed_ms", LuaValue.valueOf(max(0L, total - remaining).toDouble()))
        return table
    }

    private fun stopwatchState(): LuaTable {
        val table: LuaTable = LuaTable()
        if (context == null) {
            table.set("running", LuaValue.FALSE)
            table.set("elapsed_ms", LuaValue.ZERO)
            return table
        }
        val clockManager: ClockManager = ClockManager.getInstance(context)
        table.set("running", if (clockManager.isStopwatchRunning) LuaValue.TRUE else LuaValue.FALSE)
        table.set("elapsed_ms", LuaValue.valueOf(clockManager.stopwatchElapsedMillis.toDouble()))
        return table
    }

    private fun pomodoroState(): LuaTable {
        val table: LuaTable = LuaTable()
        if (context == null) {
            table.set("running", LuaValue.FALSE)
            table.set("remaining_ms", LuaValue.ZERO)
            table.set("total_ms", LuaValue.ZERO)
            table.set("elapsed_ms", LuaValue.ZERO)
            table.set("task", "")
            table.set("type", "idle")
            table.set("cycle", LuaValue.ZERO)
            return table
        }
        val pomodoro: PomodoroManager = PomodoroManager.getInstance(context)
        val remaining: Long = pomodoro.remainingMillis
        val total: Long = pomodoro.totalDuration
        val type = if (pomodoro.currentType == null)
            "idle"
        else
            pomodoro.currentType.name.lowercase()
        table.set("running", if (pomodoro.isRunning) LuaValue.TRUE else LuaValue.FALSE)
        table.set("remaining_ms", LuaValue.valueOf(remaining.toDouble()))
        table.set("total_ms", LuaValue.valueOf(total.toDouble()))
        table.set("elapsed_ms", LuaValue.valueOf(max(0L, total - remaining).toDouble()))
        table.set(
            "task",
            LuaValue.valueOf(if (pomodoro.taskName == null) "" else pomodoro.taskName)
        )
        table.set("type", LuaValue.valueOf(type))
        table.set("cycle", LuaValue.valueOf(pomodoro.completedFocuses))
        return table
    }

    private fun safeOsTable(): LuaTable {
        val safe: LuaTable = LuaTable()
        safe.set(
            "clock",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf(System.nanoTime() / 1000000000.0) })
        )
        safe.set(
            "time",
            ValueFunction(ValueAction { args: Varargs -> LuaValue.valueOf((System.currentTimeMillis() / 1000L).toDouble()) })
        )
        safe.set(
            "difftime",
            ValueFunction(ValueAction { args: Varargs ->
                LuaValue.valueOf(
                    numberAt(
                        args,
                        1,
                        0.0
                    ) - numberAt(args, 2, 0.0)
                )
            })
        )
        safe.set("date", ValueFunction(ValueAction { args: Varargs ->
            LuaValue.valueOf(
                osDate(
                    stringAt(args, 1, "%Y-%m-%d %H:%M:%S"),
                    numberAt(args, 2, System.currentTimeMillis() / 1000.0)
                )
            )
        }))
        return safe
    }

    private fun enqueueHttp(
        method: String?,
        url: String?,
        body: String?,
        mediaType: String?,
        callbackId: String?
    ) {
        if (TextUtils.isEmpty(url)) {
            handleNetworkError(callbackId, "empty url")
            return
        }
        try {
            val builder = Request.Builder().url(url!!)
            for (header in httpHeaders.entries) {
                builder.header(header.key!!, header.value!!)
            }
            if ("POST" == method || "PUT" == method) {
                val type: MediaType? =
                    if (TextUtils.isEmpty(mediaType)) TEXT_MEDIA_TYPE else mediaType!!.toMediaTypeOrNull()
                val requestBody: RequestBody = RequestBody.create(
                    if (type == null) TEXT_MEDIA_TYPE else type,
                    if (body == null) "" else body
                )
                if ("POST" == method) {
                    builder.post(requestBody)
                } else {
                    builder.put(requestBody)
                }
            } else if ("DELETE" == method) {
                builder.delete()
            } else {
                builder.get()
            }
            HTTP_CLIENT.newCall(builder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    handleNetworkError(callbackId, e.message)
                }

                @Throws(IOException::class)
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.body.use { responseBody ->
                            val text =
                                if (responseBody == null) "" else readLimitedResponse(responseBody)
                            handleNetworkResult(callbackId, text, response.code, response.headers)
                        }
                    } catch (e: IOException) {
                        handleNetworkError(callbackId, e.message)
                    }
                }
            })
        } catch (e: Exception) {
            handleNetworkError(callbackId, e.message)
        }
    }

    private fun handleNetworkResult(
        callbackId: String?,
        body: String,
        code: Int,
        headers: Headers?
    ) {
        mainHandler.post(Runnable {
            val result: RenderResult?
            synchronized(this@LuaWidgetEngine) {
                try {
                    ensureLoaded()
                    newResult()
                    val callback: String? = callbackName("on_network_result", callbackId)
                    val headerTable: LuaTable = headersToLua(headers)
                    if (!callIfPresent(
                            callback,
                            LuaValue.valueOf(body),
                            LuaValue.valueOf(code),
                            headerTable
                        )
                        && "on_network_result" != callback
                    ) {
                        callIfPresent(
                            "on_network_result",
                            LuaValue.valueOf(body),
                            LuaValue.valueOf(code),
                            headerTable
                        )
                    }
                    persistPrefs()
                } catch (e: Throwable) {
                    lastResult = errorResult(e)
                }
                result = lastResult.copy()
            }
            notifyUpdate(result!!)
        })
    }

    private fun handleNetworkError(callbackId: String?, error: String?) {
        mainHandler.post(Runnable {
            val result: RenderResult?
            synchronized(this@LuaWidgetEngine) {
                try {
                    ensureLoaded()
                    newResult()
                    val callback: String? = callbackName("on_network_error", callbackId)
                    if (!callIfPresent(
                            callback,
                            LuaValue.valueOf(if (error == null) "network error" else error)
                        )
                        && "on_network_error" != callback
                    ) {
                        callIfPresent(
                            "on_network_error",
                            LuaValue.valueOf(if (error == null) "network error" else error)
                        )
                    }
                    if (TextUtils.isEmpty(lastResult.body) && lastResult.buttons.isEmpty()) {
                        lastResult.body =
                            "Network error: " + (if (error == null) "unknown" else error)
                    }
                    persistPrefs()
                } catch (e: Throwable) {
                    lastResult = errorResult(e)
                }
                result = lastResult.copy()
            }
            notifyUpdate(result!!)
        })
    }

    private fun notifyUpdate(result: RenderResult) {
        if (updateListener != null) {
            updateListener.onUpdate(id, result.copy())
        }
    }

    private fun requirePermission(permission: String?) {
        val normalized = if (permission == null) "" else permission.trim { it <= ' ' }.lowercase()
        if (!approvedPermissions.contains(normalized)) {
            throw LuaError(
                ("Permission required: " + normalized
                        + ". Add -- permissions = \"" + normalized + "\" and run module -approve " + id + ".")
            )
        }
    }

    private fun addValueAction(args: Varargs) {
        val label: String = stringAt(args, 1, "")
        val value: String = stringAt(args, 2, label)
        if (!TextUtils.isEmpty(label)) {
            lastResult.valueActions.add(RenderValueAction(label, value))
        }
    }

    private fun addSuggestionAction(args: Varargs) {
        val label: String = stringAt(args, 1, "")
        val value: String = stringAt(args, 2, label)
        if (!TextUtils.isEmpty(label)) {
            addSuggestionCommand(label, "module -action " + id + " " + quoteCommandArg(value))
        }
    }

    private fun addSuggestionModule(args: Varargs) {
        val module: String = stringAt(args, 1, "")
        val label: String = stringAt(args, 2, module)
        if (!TextUtils.isEmpty(module)) {
            addSuggestionCommand(label, "module -show " + module)
        }
    }

    private fun addSuggestionText(args: Varargs) {
        val text: String = stringArg(args)
        if (!TextUtils.isEmpty(text)) {
            addSuggestionCommand(text, text)
        }
    }

    private fun addSuggestionCommand(label: String?, command: String?) {
        if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(command)) {
            lastResult.suggestions.add(RenderAction(label, command))
        }
    }

    private fun setLayout(args: Varargs) {
        val value: LuaValue = args.arg(rawIndex(args, 1))
        if (!value.istable()) {
            lastResult.layoutJson = ""
            lastResult.body = appendLine(lastResult.body, "Invalid layout schema.")
            return
        }
        try {
            val jsonValue: Any? = luaToJson(value)
            lastResult.layoutJson = if (jsonValue is JSONObject || jsonValue is JSONArray) {
                jsonValue.toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            lastResult.layoutJson = ""
            lastResult.body = appendLine(lastResult.body, "Invalid layout schema: " + e.message)
        }
    }

    private fun addButton(args: Varargs) {
        val label: String = stringAt(args, 1, "")
        if (TextUtils.isEmpty(label)) {
            return
        }
        val action = args.arg(rawIndex(args, 2))
        if (action.isnil()) {
            lastResult.buttons.add(label)
            return
        }
        val command = commandFromAction(action)
        if (!TextUtils.isEmpty(command)) {
            addCommandButton(label, command)
        } else {
            lastResult.buttons.add(label)
        }
    }

    private fun addCommandButton(label: String?, command: String?) {
        if (!TextUtils.isEmpty(label) && !TextUtils.isEmpty(command)) {
            lastResult.commands.add(RenderAction(label, command))
        }
    }

    private fun addAppButton(args: Varargs) {
        requirePermission("apps")
        val label = stringAt(args, 1, "")
        val target = stringAt(args, 2, label)
        val app = resolveLaunchableApp(target)
        if (app == null) {
            lastResult.body = appendLine(lastResult.body, "App not found: " + target)
            return
        }
        addCommandButton(if (TextUtils.isEmpty(label)) app.label else label, app.command())
    }

    private fun addIntentButton(args: Varargs) {
        requirePermission("intents")
        val label = stringAt(args, 1, "")
        val action = args.arg(rawIndex(args, 2))
        val command = commandFromIntentSpec(action)
        if (TextUtils.isEmpty(command)) {
            lastResult.body = appendLine(lastResult.body, "Invalid intent button: " + label)
            return
        }
        addCommandButton(label, command)
    }

    private fun addShortcutButton(args: Varargs) {
        requirePermission("shortcuts")
        val label = stringAt(args, 1, "")
        val shortcutId = stringAt(args, 2, "")
        val app = stringAt(args, 3, "")
        if (TextUtils.isEmpty(label) || TextUtils.isEmpty(shortcutId)) {
            return
        }
        val command = StringBuilder("shortcut -use ")
            .append(quoteCommandArg(shortcutId))
        if (!TextUtils.isEmpty(app)) {
            command.append(' ').append(quoteCommandArg(app))
        }
        addCommandButton(label, command.toString())
    }

    private fun commandFromAction(action: LuaValue): String? {
        if (action.isstring()) {
            return action.tojstring()
        }
        if (!action.istable()) {
            return ""
        }
        val type = action.get("type").optjstring("").trim { it <= ' ' }.lowercase()
        if ("command" == type) {
            return action.get("value").optjstring(action.get("command").optjstring(""))
        }
        if ("module" == type) {
            val module = action.get("value").optjstring(action.get("module").optjstring(""))
            return if (TextUtils.isEmpty(module)) "" else "module -show " + module
        }
        if ("app" == type) {
            requirePermission("apps")
            val target = action.get("value").optjstring(action.get("package").optjstring(""))
            val app = resolveLaunchableApp(target)
            return app?.command() ?: ""
        }
        if ("intent" == type) {
            requirePermission("intents")
            return commandFromIntentSpec(action)
        }
        if ("shortcut" == type) {
            requirePermission("shortcuts")
            val shortcutId = action.get("value").optjstring(action.get("id").optjstring(""))
            val app = action.get("app").optjstring("")
            if (TextUtils.isEmpty(shortcutId)) {
                return ""
            }
            val command = StringBuilder("shortcut -use ")
                .append(quoteCommandArg(shortcutId))
            if (!TextUtils.isEmpty(app)) {
                command.append(' ').append(quoteCommandArg(app))
            }
            return command.toString()
        }
        return action.get("command").optjstring("")
    }

    private fun commandFromIntentSpec(spec: LuaValue): String? {
        if (spec.isstring()) {
            return "intent -view " + quoteCommandArg(spec.tojstring())
        }
        if (!spec.istable()) {
            return ""
        }

        val uri = firstString(spec, "uri", "intent_uri")
        if (!TextUtils.isEmpty(uri)) {
            return "intent -uri " + quoteCommandArg(uri)
        }

        val view = firstString(spec, "view", "url", "data")
        val mode = firstString(spec, "mode", "type").lowercase()
        val action = firstString(spec, "action")
        val component = firstString(spec, "component", "name")
        val pkg = firstString(spec, "package", "pkg")
        val mime = firstString(spec, "mime", "mime_type")
        if (TextUtils.isEmpty(view)
            && TextUtils.isEmpty(action)
            && TextUtils.isEmpty(component)
            && TextUtils.isEmpty(pkg)
            && TextUtils.isEmpty(mime)
        ) {
            return ""
        }

        if (("view" == mode || TextUtils.isEmpty(mode))
            && !TextUtils.isEmpty(view)
            && TextUtils.isEmpty(action)
            && TextUtils.isEmpty(component)
            && TextUtils.isEmpty(pkg)
            && TextUtils.isEmpty(mime)
        ) {
            return "intent -view " + quoteCommandArg(view)
        }

        val command = StringBuilder("intent -activity")
        if (!TextUtils.isEmpty(action)) {
            command.append(" -a ").append(quoteCommandArg(action))
        }
        if (!TextUtils.isEmpty(view)) {
            command.append(" -d ").append(quoteCommandArg(view))
        }
        if (!TextUtils.isEmpty(mime)) {
            command.append(" -t ").append(quoteCommandArg(mime))
        }
        if (!TextUtils.isEmpty(pkg)) {
            command.append(" -p ").append(quoteCommandArg(pkg))
        }
        if (!TextUtils.isEmpty(component)) {
            command.append(" -n ").append(quoteCommandArg(component))
        }
        return command.toString()
    }

    private fun firstString(table: LuaValue, vararg names: String): String {
        for (name in names) {
            val value = table.get(name)
            if (!value.isnil()) {
                val text = value.optjstring("")
                if (!TextUtils.isEmpty(text)) {
                    return text
                }
            }
        }
        return ""
    }

    private fun quoteCommandArg(value: String?): String {
        var safe = if (value == null) "" else value
        safe = safe.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", " ")
            .replace("\n", " ")
        return "\"" + safe + "\""
    }

    private fun resolveLaunchableApp(target: String?): AppEntry? {
        val query = if (target == null) "" else target.trim { it <= ' ' }
        if (TextUtils.isEmpty(query) || context == null) {
            return null
        }
        val apps = launchableApps()
        val lower = query.lowercase(Locale.getDefault())
        var partial: AppEntry? = null
        for (app in apps) {
            if (TextUtils.equals(app.packageName, query)
                || TextUtils.equals(app.component.flattenToString(), query)
                || TextUtils.equals(app.label, query)
                || TextUtils.equals(app.label.lowercase(Locale.getDefault()), lower)
            ) {
                return app
            }
            if (partial == null
                && (app.label.lowercase(Locale.getDefault()).contains(lower)
                        || app.packageName.lowercase(Locale.getDefault()).contains(lower))
            ) {
                partial = app
            }
        }
        return partial
    }

    private fun launchableApps(): MutableList<AppEntry> {
        val out = ArrayList<AppEntry>()
        val c = context ?: return out
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val results: MutableList<ResolveInfo> = try {
            c.packageManager.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            ArrayList()
        }
        for (info in results) {
            val activity = info.activityInfo ?: continue
            val label = info.loadLabel(c.packageManager)?.toString() ?: activity.name
            out.add(AppEntry(label, activity.packageName, ComponentName(activity.packageName, activity.name)))
        }
        out.sortWith(Comparator { a, b ->
            a.label.compareTo(b.label, ignoreCase = true)
        })
        return out
    }

    private class AppEntry(
        val label: String,
        val packageName: String,
        val component: ComponentName
    ) {
        fun command(): String {
            return "intent -activity -n \"" + component.flattenToString().replace("\"", "") + "\""
        }
    }

    private fun showChoiceDialog(args: Varargs) {
        var title: String? = stringAt(args, 1, "Choose")
        var items: LuaValue = args.arg(rawIndex(args, 2))
        var selected = numberAt(args, 3, -1.0).toInt()
        if (items.isnil() && args.arg(rawIndex(args, 1)).istable()) {
            val table: LuaValue = args.arg(rawIndex(args, 1))
            title = if (table.get("title").isnil()) title else table.get("title").tojstring()
            items = table.get("items")
            if (items.isnil()) {
                items = table
            }
            if (table.get("selected").isnumber()) {
                selected = table.get("selected").toint()
            }
        }
        val values: MutableList<String?> = tableStrings(items)
        lastResult.dialogOpen = true
        lastResult.dialogTitle = title
        lastResult.dialogItems = values
        lastResult.dialogSelected = selected
        if (!TextUtils.isEmpty(title)) {
            lastResult.body = appendLine(lastResult.body, title)
        }
    }

    private fun errorResult(e: Throwable): RenderResult {
        val message: String? = errorMessage(e)
        val stage = if (TextUtils.isEmpty(executionStage)) "script" else executionStage
        val result: RenderResult = RenderResult.Companion.error(message, stage)
        LuaWidgetManager.saveLastError(id, stage, message)
        return result
    }

    private fun loadPrefs(): LuaTable {
        val file = prefsFile()
        if (!file.isFile()) {
            return LuaTable()
        }
        try {
            FileInputStream(file).use { `in` ->
                val text: String = Tuils.convertStreamToString(`in`) ?: "{}"
                return Companion.jsonToLua(JSONObject(text))
            }
        } catch (e: Exception) {
            return LuaTable()
        }
    }

    private fun persistPrefs() {
        if (prefsTable == null) {
            return
        }
        try {
            val table = prefsTable ?: return
            val `object`: JSONObject = luaTableToJsonObject(table)
            val file = prefsFile()
            val parent = file.getParentFile()
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            FileOutputStream(file, false).use { out ->
                out.write(`object`.toString(2).toByteArray(StandardCharsets.UTF_8))
                out.write('\n'.code)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun prefsFile(): File {
        return File(LuaWidgetManager.widgetDir(id), "prefs.json")
    }

    private var isExpandableState: Boolean
        get() {
            val table = prefsTable ?: return false
            return table.get(PREF_EXPANDABLE).toboolean()
        }
        private set(expandable) {
            val table = prefsTable ?: return
            table.set(
                PREF_EXPANDABLE,
                if (expandable) LuaValue.TRUE else LuaValue.FALSE
            )
            if (!expandable) {
                table.set(PREF_EXPANDED, LuaValue.TRUE)
            } else if (table.get(PREF_EXPANDED).isnil()) {
                table.set(PREF_EXPANDED, LuaValue.TRUE)
            }
            lastResult.expandable = expandable
            lastResult.expanded = this.isExpandedState
        }

    private var isExpandedState: Boolean
        get() {
            if (!this.isExpandableState) {
                return true
            }
            val value: LuaValue =
                prefsTable?.get(PREF_EXPANDED) ?: LuaValue.NIL
            return value.isnil() || value.toboolean()
        }
        private set(expanded) {
            val table = prefsTable ?: return
            table.set(
                PREF_EXPANDED,
                if (expanded) LuaValue.TRUE else LuaValue.FALSE
            )
            lastResult.expanded = expanded
        }

    private val isFoldedState: Boolean
        get() = this.isExpandableState && !this.isExpandedState

    private fun setTickInterval(seconds: Double) {
        if (seconds <= 0.0) {
            tickIntervalMs = -1L
        } else {
            requirePermission("active-tick")
            tickIntervalMs = max(1000L, min(60000L, Math.round(seconds * 1000.0)))
        }
        lastResult.tickIntervalMs = tickIntervalMs
    }

    private fun prefsSummary(): String? {
        val table = prefsTable ?: return "No editable prefs yet."
        val lines = ArrayList<String?>()
        var key: LuaValue = LuaValue.NIL
        while (true) {
            val next: Varargs = table.next(key)
            key = next.arg1()
            if (key.isnil()) {
                break
            }
            if (!key.isstring()) {
                continue
            }
            val name: String = key.tojstring()
            if (name.startsWith("_") || "show_dialog" == name) {
                continue
            }
            val value: LuaValue = next.arg(2)
            if (value.isstring() || value.isnumber() || value.isboolean()) {
                lines.add(name + " = " + value.tojstring())
            }
        }
        return if (lines.isEmpty()) "No editable prefs yet." else TextUtils.join("\n", lines)
    }

    private fun dataFile(name: String?): File? {
        val safe: String = safeFileName(name)
        if (TextUtils.isEmpty(safe)) {
            return null
        }
        return File(File(LuaWidgetManager.widgetDir(id), "files"), safe)
    }

    private fun ensureReadableFile(file: File) {
        if (file.length() > MAX_WIDGET_FILE_BYTES) {
            throw LuaError("Widget local file is too large: " + file.getName())
        }
    }

    private fun ensureFileWriteAllowed(file: File, incomingBytes: Long, append: Boolean) {
        val existing = if (file.isFile()) file.length() else 0L
        val nextSize = if (append) existing + incomingBytes else incomingBytes
        if (nextSize > MAX_WIDGET_FILE_BYTES) {
            throw LuaError("Widget local file limit exceeded: " + file.getName())
        }
        val total = widgetFilesSize() - existing + nextSize
        if (total > MAX_WIDGET_FILES_TOTAL_BYTES) {
            throw LuaError("Widget local storage limit exceeded")
        }
    }

    private fun widgetFilesSize(): Long {
        val dir = File(LuaWidgetManager.widgetDir(id), "files")
        val files = dir.listFiles()
        if (files == null) {
            return 0L
        }
        var total = 0L
        for (file in files) {
            if (file.isFile()) {
                total += file.length()
            }
        }
        return total
    }

    private fun headersToLua(headers: Headers?): LuaTable {
        val table: LuaTable = LuaTable()
        if (headers == null) {
            return table
        }
        for (name in headers.names()) {
            table.set(
                name.lowercase(), if (headers.values(name).isEmpty())
                    ""
                else
                    TextUtils.join(", ", headers.values(name))
            )
        }
        return table
    }

    private fun showToast(text: String?) {
        if (context == null || TextUtils.isEmpty(text)) {
            return
        }
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    private fun openUrl(url: String?) {
        if (context == null || TextUtils.isEmpty(url)) {
            return
        }
        try {
            val intent: Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("Cannot open URL")
        }
    }

    private fun copyToClipboard(text: String?) {
        if (context == null) {
            return
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (manager != null) {
            manager.setPrimaryClip(
                ClipData.newPlainText(
                    "Re:TUI widget",
                    if (text == null) "" else text
                )
            )
        }
    }

    private fun readClipboard(): String {
        if (context == null) {
            return ""
        }
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        if (manager == null || !manager.hasPrimaryClip() || manager.getPrimaryClip() == null || manager.getPrimaryClip()!!
                .getItemCount() == 0
        ) {
            return ""
        }
        val text = manager.getPrimaryClip()!!.getItemAt(0).coerceToText(context)
        return if (text == null) "" else text.toString()
    }

    private fun vibrate(millis: Long) {
        if (context == null || millis <= 0) {
            return
        }
        val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (vibrator == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    millis,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            vibrator.vibrate(millis)
        }
    }

    private fun batteryInfo(): LuaTable {
        val table: LuaTable = LuaTable()
        if (context == null) {
            return table
        }
        val battery: Intent? =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery == null) {
            return table
        }
        val level: Int = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale: Int = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status: Int = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged: Int = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        table.set("level", LuaValue.valueOf(level))
        table.set("scale", LuaValue.valueOf(scale))
        table.set(
            "percent",
            LuaValue.valueOf(if (scale <= 0) level else Math.round((level * 100f) / scale))
        )
        table.set(
            "charging", if (status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL
            ) LuaValue.TRUE else LuaValue.FALSE
        )
        table.set("plugged", LuaValue.valueOf(plugged))
        return table
    }

    private fun networkState(): LuaTable {
        val table: LuaTable = LuaTable()
        table.set("connected", LuaValue.FALSE)
        table.set("type", LuaValue.valueOf("none"))
        table.set("class", LuaValue.valueOf(""))
        table.set("ssid", LuaValue.valueOf(""))
        table.set("operator", LuaValue.valueOf(""))
        table.set("metered", LuaValue.FALSE)
        table.set("roaming", LuaValue.FALSE)
        if (context == null) {
            return table
        }
        val manager: ConnectivityManager? =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (manager == null) {
            return table
        }
        try {
            val network: Network? = manager.getActiveNetwork()
            val capabilities: NetworkCapabilities? =
                if (network == null) null else manager.getNetworkCapabilities(network)
            if (capabilities == null) {
                return table
            }
            table.set("connected", LuaValue.TRUE)
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                table.set("type", LuaValue.valueOf("wifi"))
                table.set("class", LuaValue.valueOf("WiFi"))
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                table.set("type", LuaValue.valueOf("mobile"))
                table.set("class", LuaValue.valueOf(""))
            }
            table.set(
                "metered",
                if (manager.isActiveNetworkMetered()) LuaValue.TRUE else LuaValue.FALSE
            )
        } catch (ignored: Exception) {
        }
        return table
    }

    class RenderResult {
        var title: String? = ""
        var body: String? = ""
        var layoutJson: String? = ""
        var error: String? = ""
        var errorStage: String? = ""
        var progress: Double = -1.0
        var expandable: Boolean = false
        var expanded: Boolean = true
        var tickIntervalMs: Long = -1L
        var configJson: String? = ""
        var buttons: MutableList<String?> = ArrayList<String?>()
        var commands: MutableList<RenderAction?> = ArrayList<RenderAction?>()
        var valueActions: MutableList<RenderValueAction?> = ArrayList<RenderValueAction?>()
        var suggestions: MutableList<RenderAction?> = ArrayList<RenderAction?>()
        var dialogOpen: Boolean = false
        var dialogTitle: String? = ""
        var dialogItems: MutableList<String?> = ArrayList<String?>()
        var dialogSelected: Int = -1
        var debug: MutableList<String?> = ArrayList<String?>()

        fun copy(): RenderResult {
            val copy = RenderResult()
            copy.title = title
            copy.body = body
            copy.layoutJson = layoutJson
            copy.error = error
            copy.errorStage = errorStage
            copy.progress = progress
            copy.expandable = expandable
            copy.expanded = expanded
            copy.tickIntervalMs = tickIntervalMs
            copy.configJson = configJson
            copy.buttons = ArrayList<String?>(buttons)
            copy.commands = ArrayList<RenderAction?>(commands)
            copy.valueActions = ArrayList<RenderValueAction?>(valueActions)
            copy.suggestions = ArrayList<RenderAction?>(suggestions)
            copy.dialogOpen = dialogOpen
            copy.dialogTitle = dialogTitle
            copy.dialogItems = ArrayList<String?>(dialogItems)
            copy.dialogSelected = dialogSelected
            copy.debug = ArrayList<String?>(debug)
            return copy
        }

        companion object {
            @JvmOverloads
            fun error(message: String?, stage: String? = ""): RenderResult {
                val result = RenderResult()
                result.error = if (message == null) "unknown error" else message
                result.errorStage = if (stage == null) "" else stage
                return result
            }
        }
    }

    class RenderAction internal constructor(val label: String?, val command: String?, val type: String? = "command")

    class RenderValueAction internal constructor(val label: String?, val value: String?)

    private fun interface UiAction {
        fun run(args: Varargs)
    }

    private fun interface ValueAction {
        fun run(args: Varargs): LuaValue?
    }

    private class UiFunction(private val action: UiAction) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs? {
            action.run(args)
            return LuaValue.NONE
        }
    }

    private class ValueFunction(private val action: ValueAction) : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs? {
            val result: LuaValue? = action.run(args)
            return if (result == null) LuaValue.NIL else result
        }
    }

    private inner class GuardedDebugLib : DebugLib() {
        override fun onInstruction(pc: Int, v: Varargs?, top: Int) {
            checkTimeout()
            super.onInstruction(pc, v, top)
        }
    }

    private inner class RequireFunction : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs? {
            val name: String = stringArg(args)
            if ("prefs" == name) {
                return prefsTable ?: LuaValue.NIL
            }
            if ("json" == name) {
                return globals?.get("json") ?: LuaValue.NIL
            }
            if ("date" == name || "fmt" == name || "strings" == name
                || "colors" == name || "debug" == name || "files" == name
                || "http" == name || "system" == name || "ui" == name
                || "suggest" == name || "aio" == name || "clock" == name
                || "reminders" == name || "notify" == name
                || "launcher" == name || "apps" == name || "intents" == name
                || "shortcuts" == name
            ) {
                return globals?.get(name) ?: LuaValue.NIL
            }
            return LuaValue.error("module not found: " + name)
        }
    }

    fun interface UpdateListener {
        fun onUpdate(widgetId: String?, result: RenderResult)
    }

    companion object {
        private val ALARM_INTERVAL_MS = 30L * 60L * 1000L
        private const val EXECUTION_TIMEOUT_MS = 1500L
        private const val INSTRUCTION_CHECK_INTERVAL = 2048
        private val MAX_HTTP_RESPONSE_BYTES = 256 * 1024
        private val MAX_WIDGET_FILE_BYTES = 256L * 1024L
        private val MAX_WIDGET_FILES_TOTAL_BYTES = 1024L * 1024L
        private const val DEFAULT_PROGRESS_BAR_WIDTH = 12
        private const val MAX_PROGRESS_BAR_WIDTH = 32
        private const val PROGRESS_EMPTY = "\u2591"
        private const val PROGRESS_LOW = "\u2592"
        private const val PROGRESS_HIGH = "\u2593"
        private const val PROGRESS_FULL = "\u2588"
        private val TEXT_MEDIA_TYPE: MediaType? = "text/plain; charset=utf-8".toMediaTypeOrNull()
        private val HTTP_CLIENT: OkHttpClient = OkHttpClient()
        private const val PREF_EXPANDABLE = "_retui_expandable"
        private const val PREF_EXPANDED = "_retui_expanded"

        @Throws(IOException::class)
        private fun readLimitedResponse(responseBody: ResponseBody): String {
            val declaredLength: Long = responseBody.contentLength()
            if (declaredLength > MAX_HTTP_RESPONSE_BYTES) {
                throw IOException("response too large: " + declaredLength + " bytes")
            }
            val out = ByteArrayOutputStream(
                max(
                    0,
                    min(declaredLength, MAX_HTTP_RESPONSE_BYTES.toLong())
                ).toInt()
            )
            val buffer = ByteArray(8192)
            var total = 0
            responseBody.byteStream().use { `in` ->
                var read: Int
                while ((`in`.read(buffer).also { read = it }) != -1) {
                    total += read
                    if (total > MAX_HTTP_RESPONSE_BYTES) {
                        throw IOException("response too large: " + total + " bytes")
                    }
                    out.write(buffer, 0, read)
                }
            }
            return out.toString(StandardCharsets.UTF_8.name())
        }

        private fun stringArg(args: Varargs): String {
            return stringAt(args, 1, "")
        }

        private fun stringAt(args: Varargs, logicalIndex: Int, fallback: String): String {
            val rawIndex: Int = rawIndex(args, logicalIndex)
            val value: LuaValue = args.arg(rawIndex)
            return if (value.isnil()) fallback else value.tojstring()
        }

        private fun numberAt(args: Varargs, logicalIndex: Int, fallback: Double): Double {
            val rawIndex: Int = rawIndex(args, logicalIndex)
            val value: LuaValue = args.arg(rawIndex)
            return if (value.isnumber()) value.todouble() else fallback
        }

        private fun booleanAt(args: Varargs, logicalIndex: Int, fallback: Boolean): Boolean {
            val rawIndex: Int = rawIndex(args, logicalIndex)
            val value: LuaValue = args.arg(rawIndex)
            if (value.isnil()) {
                return fallback
            }
            if (value.isboolean()) {
                return value.toboolean()
            }
            if (value.isnumber()) {
                return value.todouble() != 0.0
            }
            if (value.isstring()) {
                val text = value.tojstring().trim { it <= ' ' }.lowercase()
                return "true" == text || "yes" == text || "1" == text || "on" == text
            }
            return fallback
        }

        private fun tableArg(args: Varargs): LuaValue {
            return args.arg(rawIndex(args, 1))
        }

        private fun rawIndex(args: Varargs, logicalIndex: Int): Int {
            if (args.narg() > logicalIndex && args.arg1().istable()) {
                return logicalIndex + 1
            }
            return logicalIndex
        }

        private fun tableLines(table: LuaValue): String? {
            val out = StringBuilder()
            if (!table.istable()) {
                return if (table.isnil()) "" else table.tojstring()
            }
            val length: Int = table.length()
            for (i in 1..length) {
                val value: String = table.get(i).tojstring()
                if (out.length > 0) out.append('\n')
                out.append(value)
            }
            return out.toString()
        }

        private fun tableStrings(table: LuaValue): MutableList<String?> {
            val values = ArrayList<String?>()
            if (!table.istable()) {
                if (!table.isnil()) values.add(table.tojstring())
                return values
            }
            val length: Int = table.length()
            for (i in 1..length) {
                val value: LuaValue = table.get(i)
                if (!value.isnil()) {
                    values.add(value.tojstring())
                }
            }
            return values
        }

        private fun tableRows(table: LuaValue): String? {
            if (!table.istable()) {
                return if (table.isnil()) "" else table.tojstring()
            }
            val out = StringBuilder()
            val length: Int = table.length()
            for (i in 1..length) {
                val row: LuaValue = table.get(i)
                if (out.length > 0) out.append('\n')
                if (row.istable()) {
                    val cells = ArrayList<String?>()
                    for (j in 1..row.length()) {
                        cells.add(row.get(j).tojstring())
                    }
                    out.append(TextUtils.join(" | ", cells))
                } else {
                    out.append(row.tojstring())
                }
            }
            return out.toString()
        }

        private fun tableKeyValues(table: LuaValue): String? {
            if (!table.istable()) {
                return if (table.isnil()) "" else table.tojstring()
            }
            val out = StringBuilder()
            var key: LuaValue = LuaValue.NIL
            while (true) {
                val next: Varargs = table.next(key)
                key = next.arg1()
                if (key.isnil()) {
                    break
                }
                if (!key.isstring()) {
                    continue
                }
                if (out.length > 0) out.append('\n')
                out.append(key.tojstring()).append(": ").append(next.arg(2).tojstring())
            }
            return out.toString()
        }

        private fun osDate(pattern: String, seconds: Double): String {
            return SimpleDateFormat(convertLuaDatePattern(pattern), Locale.getDefault())
                .format(Date((seconds * 1000.0).toLong()))
        }

        private fun convertLuaDatePattern(pattern: String): String {
            return pattern
                .replace("%Y", "yyyy")
                .replace("%y", "yy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%M", "mm")
                .replace("%S", "ss")
        }

        private fun titleCase(text: String): String {
            val out = StringBuilder()
            var nextUpper = true
            for (i in 0..<text.length) {
                val c = text.get(i)
                if (Character.isWhitespace(c) || c == '_' || c == '-') {
                    out.append(' ')
                    nextUpper = true
                } else if (nextUpper) {
                    out.append(c.uppercaseChar())
                    nextUpper = false
                } else {
                    out.append(c.lowercaseChar())
                }
            }
            return out.toString().trim { it <= ' ' }
        }

        private fun pad(text: String?, width: Int, left: Boolean): String {
            var text = text
            if (text == null) text = ""
            if (text.length >= width) return text
            val spaces = StringBuilder()
            for (i in text.length..<width) spaces.append(' ')
            return if (left) spaces.toString() + text else text + spaces
        }

        private fun formatProgressLine(
            label: String?,
            current: Double,
            max: Double,
            width: Int
        ): String {
            val prefix = if (TextUtils.isEmpty(label)) "" else label + ": "
            return prefix + formatProgressBar(current, max, width) + " " + progressPercent(
                current,
                max
            ) + "%"
        }

        private fun formatProgressBar(current: Double, max: Double, width: Int): String {
            val cells = max(1, min(MAX_PROGRESS_BAR_WIDTH, width))
            val fill: Double = progressRatio(current, max) * cells
            val out = StringBuilder(cells)
            for (i in 0..<cells) {
                val cellFill = fill - i
                if (cellFill >= 1.0) {
                    out.append(PROGRESS_FULL)
                } else if (cellFill >= 0.67) {
                    out.append(PROGRESS_HIGH)
                } else if (cellFill > 0.0) {
                    out.append(PROGRESS_LOW)
                } else {
                    out.append(PROGRESS_EMPTY)
                }
            }
            return out.toString()
        }

        private fun progressPercent(current: Double, max: Double): Int {
            return Math.round(progressRatio(current, max) * 100.0).toInt()
        }

        private fun progressRatio(current: Double, max: Double): Double {
            var current = current
            var max = max
            if (current.isNaN() || current.isInfinite()) {
                current = 0.0
            }
            if (max.isNaN() || max.isInfinite() || max <= 0.0) {
                max = 100.0
            }
            return max(0.0, min(1.0, current / max))
        }

        private fun formatBytes(value: kotlin.Double): String {
            var value = value
            val units = arrayOf<String?>("B", "KB", "MB", "GB", "TB")
            var unit = 0
            while (value >= 1024.0 && unit < units.size - 1) {
                value /= 1024.0
                unit++
            }
            return String.format(
                Locale.US,
                if (unit == 0) "%.0f %s" else "%.1f %s",
                value,
                units[unit]
            )
        }

        private fun appendLine(body: String?, line: String?): String {
            if (TextUtils.isEmpty(body)) {
                return if (line == null) "" else line
            }
            return body + "\n" + (if (line == null) "" else line)
        }

        private fun errorMessage(e: Throwable): String? {
            if (e is LuaError && e.message != null) {
                return e.message
            }
            return e.javaClass.getSimpleName() + (if (e.message == null) "" else ": " + e.message)
        }

        private fun safeFileName(value: String?): String {
            if (value == null) return ""
            val trimmed = value.trim { it <= ' ' }
            if (trimmed.length == 0 || trimmed.contains("/") || trimmed.contains("\\") || trimmed == "." || trimmed == "..") {
                return ""
            }
            return trimmed.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
        }

        private fun jsonToLua(`object`: JSONObject): LuaTable {
            val table: LuaTable = LuaTable()
            val names: JSONArray? = `object`.names()
            if (names == null) {
                return table
            }
            for (i in 0..<names.length()) {
                val key: String = names.optString(i, "")
                if (key.length > 0) {
                    table.set(key, jsonToLuaValue(`object`.opt(key)))
                }
            }
            return table
        }

        private fun jsonToLua(array: JSONArray): LuaTable {
            val table: LuaTable = LuaTable()
            for (i in 0..<array.length()) {
                table.set(i + 1, jsonToLuaValue(array.opt(i)))
            }
            return table
        }

        private fun jsonToLuaValue(value: Any?): LuaValue? {
            if (value == null || value === JSONObject.NULL) return LuaValue.NIL
            if (value is JSONObject) return Companion.jsonToLua(value as JSONObject)
            if (value is JSONArray) return Companion.jsonToLua(value as JSONArray)
            if (value is Boolean) return if (value) LuaValue.TRUE else LuaValue.FALSE
            if (value is Number) return LuaValue.valueOf(value.toDouble())
            return LuaValue.valueOf(value.toString())
        }

        @Throws(Exception::class)
        private fun luaToJson(value: LuaValue?): Any? {
            if (value == null || value.isnil()) return JSONObject.NULL
            if (value.isboolean()) return value.toboolean()
            if (value.isnumber()) return value.todouble()
            if (value.isstring()) return value.tojstring()
            if (value.istable()) {
                val table: LuaTable = value as LuaTable
                if (isArrayTable(table)) {
                    val array: JSONArray = JSONArray()
                    val length: Int = table.length()
                    for (i in 1..length) {
                        array.put(luaToJson(table.get(i)))
                    }
                    return array
                }
                return luaTableToJsonObject(table)
            }
            return value.tojstring()
        }

        @Throws(Exception::class)
        private fun luaTableToJsonObject(table: LuaTable): JSONObject {
            val `object`: JSONObject = JSONObject()
            var key: LuaValue = LuaValue.NIL
            while (true) {
                val next: Varargs = table.next(key)
                key = next.arg1()
                if (key.isnil()) {
                    break
                }
                val value: LuaValue = next.arg(2)
                if (key.isstring() && !value.isfunction() && !value.isuserdata() && !value.isthread()) {
                    `object`.put(key.tojstring(), luaToJson(value))
                }
            }
            return `object`
        }

        private fun isArrayTable(table: LuaTable): Boolean {
            val length: Int = table.length()
            if (length <= 0) {
                return false
            }
            var key: LuaValue = LuaValue.NIL
            while (true) {
                val next: Varargs = table.next(key)
                key = next.arg1()
                if (key.isnil()) {
                    return true
                }
                if (!key.isnumber() || key.toint() < 1 || key.toint() > length || key.todouble() != key.toint()
                        .toDouble()
                ) {
                    return false
                }
            }
        }

        private fun callbackName(base: String?, callbackId: String?): String? {
            if (TextUtils.isEmpty(callbackId)) {
                return base
            }
            return base + "_" + callbackId!!.trim { it <= ' ' }
                .replace("[^A-Za-z0-9_]".toRegex(), "_")
        }

        private fun hex(color: Int): String {
            return String.format(Locale.US, "#%06X", 0xFFFFFF and color)
        }

        private fun colorsTable(): LuaTable {
            val colors: LuaTable = LuaTable()
            colors.set("primary_text", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set(
                "secondary_text", hex(
                    Color.argb(
                        190,
                        Color.red(AppearanceSettings.moduleNameTextColor()),
                        Color.green(AppearanceSettings.moduleNameTextColor()),
                        Color.blue(AppearanceSettings.moduleNameTextColor())
                    )
                )
            )
            colors.set("button", hex(AppearanceSettings.moduleButtonBackgroundColor()))
            colors.set("button_text", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set("progress", hex(AppearanceSettings.moduleNameTextColor()))
            colors.set("accent", hex(AppearanceSettings.moduleButtonBorderColor()))
            return colors
        }
    }
}
