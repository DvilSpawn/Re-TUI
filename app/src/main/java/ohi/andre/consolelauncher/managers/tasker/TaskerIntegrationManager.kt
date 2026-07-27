package ohi.andre.consolelauncher.managers.tasker

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils

object TaskerIntegrationManager {
    private const val PREFS = "tasker_integration"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SHOW_TASK_STATUSES = "show_task_statuses"
    const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"
    const val TASKER_ACTION_TASK = "net.dinglisch.android.tasker.ACTION_TASK"
    private const val TASKER_ACTION_TASK_COMPLETE = "net.dinglisch.android.tasker.ACTION_TASK_COMPLETE"
    const val TASKER_EXTRA_TASK_NAME = "task_name"
    const val TASKER_PERMISSION_RUN_TASKS = "net.dinglisch.android.tasker.PERMISSION_RUN_TASKS"
    private const val TASKER_PREFS_URI = "content://net.dinglisch.android.tasker/prefs"
    private const val TASKER_PREF_ENABLED = "enabled"
    private const val TASKER_PREF_EXTERNAL_ACCESS = "ext_access"
    private const val TASKER_EXTRA_VERSION = "version_number"
    private const val TASKER_INTENT_VERSION = "1.1"
    private const val TASKER_EXTRA_SUCCESS = "success"
    private const val TASKER_TASKS_URI = "content://net.dinglisch.android.tasker/tasks"

    const val ACTION_APPLY_PRESET = "apply_preset"
    const val ACTION_SET_THEME = "set_theme"
    const val ACTION_SHOW_MODULE = "show_module"
    const val ACTION_REFRESH_MODULE = "refresh_module"
    const val ACTION_UPDATE_MODULE_TEXT = "update_module_text"
    const val ACTION_TERMINAL_OUTPUT = "terminal_output"

    data class Request(
        val action: String?,
        val preset: String? = null,
        val themeElement: String? = null,
        val value: String? = null,
        val module: String? = null,
        val text: String? = null
    )

    data class Result(val success: Boolean, val message: String)

    fun isEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun showTaskStatuses(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_TASK_STATUSES, true)

    fun setShowTaskStatuses(context: Context, show: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_TASK_STATUSES, show).apply()
    }

    fun isTaskerInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TASKER_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun hasRunTasksPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, TASKER_PERMISSION_RUN_TASKS) == PackageManager.PERMISSION_GRANTED

    fun execute(context: Context, request: Request, requireEnabled: Boolean = true): Result {
        if (requireEnabled && !isEnabled(context)) return Result(false, "Tasker integration is disabled in RETUI.")
        return try {
            when (request.action?.trim()?.lowercase()) {
                ACTION_APPLY_PRESET -> applyPreset(context, request.preset)
                ACTION_SET_THEME -> setTheme(context, request.themeElement, request.value)
                ACTION_SHOW_MODULE -> showModule(context, request.module)
                ACTION_REFRESH_MODULE -> refreshModule(context, request.module)
                ACTION_UPDATE_MODULE_TEXT -> updateModuleText(context, request.module, request.text)
                ACTION_TERMINAL_OUTPUT -> terminalOutput(context, request.text)
                else -> Result(false, "Unsupported RETUI action.")
            }
        } catch (e: IllegalArgumentException) {
            Result(false, e.message ?: "Invalid RETUI action input.")
        } catch (e: Exception) {
            Result(false, e.message ?: "RETUI action failed.")
        }
    }

    fun runTaskerTask(context: Context, taskName: String?): Result {
        val name = taskName?.trim().orEmpty()
        if (name.isEmpty()) return Result(false, "Task name is required.")
        if (!isTaskerInstalled(context)) return Result(false, "Tasker is not installed.")
        if (!hasRunTasksPermission(context)) {
            return Result(false, "Tasker run-task permission is not granted. Enable Tasker Integration in RETUI settings.")
        }
        when (taskerAvailability(context)) {
            TaskerAvailability.DISABLED -> return Result(false, "Tasker is disabled. Enable Tasker before running a task.")
            TaskerAvailability.EXTERNAL_ACCESS_BLOCKED -> return Result(
                false,
                "Tasker blocked external access. In Tasker, enable Settings > Misc > Allow External Access."
            )
            TaskerAvailability.NO_RECEIVER -> return Result(false, "Tasker is not accepting external task requests.")
            TaskerAvailability.AVAILABLE -> Unit
        }
        val knownTasks = taskerTaskNames(context)
        if (knownTasks.isNotEmpty() && name !in knownTasks) {
            val caseMatch = knownTasks.firstOrNull { it.equals(name, ignoreCase = true) }
            return if (caseMatch != null) {
                Result(false, "Task names are case-sensitive. Use: tasker \"$caseMatch\"")
            } else {
                Result(false, "Tasker task not found: $name")
            }
        }
        return try {
            val request = Intent(TASKER_ACTION_TASK)
                .setPackage(TASKER_PACKAGE)
                .setData(Uri.parse("id:${java.util.UUID.randomUUID()}"))
                .putExtra(TASKER_EXTRA_VERSION, TASKER_INTENT_VERSION)
                .putExtra(TASKER_EXTRA_TASK_NAME, name)
            val showStatuses = showTaskStatuses(context)
            if (showStatuses) listenForTaskCompletion(context.applicationContext, name)
            context.sendBroadcast(request)
            Result(true, if (showStatuses) "Tasker task started: $name" else "")
        } catch (e: SecurityException) {
            Result(false, "Tasker blocked the request: " + (e.message ?: "security error"))
        } catch (e: Exception) {
            Result(false, "Unable to run Tasker task: " + (e.message ?: "unknown error"))
        }
    }

    fun taskerTaskNames(context: Context): Set<String> = try {
        buildSet {
            context.contentResolver.query(Uri.parse(TASKER_TASKS_URI), arrayOf("name"), null, null, null)?.use { cursor ->
                val nameColumn = cursor.getColumnIndex("name")
                while (nameColumn >= 0 && cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
    } catch (_: Exception) {
        emptySet()
    }

    private fun listenForTaskCompletion(context: Context, taskName: String) {
        val filter = IntentFilter(TASKER_ACTION_TASK_COMPLETE).apply {
            addDataScheme("task")
            addDataPath(taskName, PatternMatcher.PATTERN_LITERAL)
        }
        val handler = Handler(Looper.getMainLooper())
        lateinit var receiver: BroadcastReceiver
        val timeout = Runnable { runCatching { context.unregisterReceiver(receiver) } }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(receiveContext: Context, intent: Intent) {
                handler.removeCallbacks(timeout)
                runCatching { receiveContext.unregisterReceiver(this) }
                val succeeded = intent.getBooleanExtra(TASKER_EXTRA_SUCCESS, false)
                Tuils.sendOutput(
                    receiveContext,
                    if (succeeded) "Tasker task completed: $taskName" else "Tasker task failed: $taskName"
                )
            }
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        handler.postDelayed(timeout, 30_000L)
    }

    private enum class TaskerAvailability { DISABLED, EXTERNAL_ACCESS_BLOCKED, NO_RECEIVER, AVAILABLE }

    private fun taskerAvailability(context: Context): TaskerAvailability {
        if (!taskerPreference(context, TASKER_PREF_ENABLED)) return TaskerAvailability.DISABLED
        if (!taskerPreference(context, TASKER_PREF_EXTERNAL_ACCESS)) return TaskerAvailability.EXTERNAL_ACCESS_BLOCKED
        val probe = Intent(TASKER_ACTION_TASK)
            .setPackage(TASKER_PACKAGE)
            .setData(Uri.parse("id:${java.util.UUID.randomUUID()}"))
            .putExtra(TASKER_EXTRA_VERSION, TASKER_INTENT_VERSION)
            .putExtra(TASKER_EXTRA_TASK_NAME, "")
        return if (context.packageManager.queryBroadcastReceivers(probe, 0).isEmpty()) {
            TaskerAvailability.NO_RECEIVER
        } else {
            TaskerAvailability.AVAILABLE
        }
    }

    private fun taskerPreference(context: Context, column: String): Boolean = try {
        context.contentResolver.query(Uri.parse(TASKER_PREFS_URI), arrayOf(column), null, null, null)?.use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("true", ignoreCase = true)
        } ?: false
    } catch (_: Exception) {
        false
    }

    private fun applyPreset(context: Context, preset: String?): Result {
        val name = required(preset, "Preset name is required.")
        PresetManager.apply(name)
        refreshLauncher()
        return Result(true, "Preset applied: $name")
    }

    private fun setTheme(context: Context, elementName: String?, value: String?): Result {
        val requested = required(elementName, "Theme element is required.")
        val color = required(value, "Theme color is required.")
        try { Color.parseColor(color) } catch (_: Exception) { return Result(false, "Invalid color. Use #RRGGBB or #AARRGGBB.") }
        val element = Theme.entries.firstOrNull { it.label().equals(requested, true) }
            ?: return Result(false, "Unknown theme element: $requested")
        LauncherSettings.set(context, element, color)
        refreshLauncher()
        return Result(true, "Theme updated: ${element.label()}")
    }

    private fun showModule(context: Context, module: String?): Result {
        val id = validModule(context, module) ?: return Result(false, "Unknown module: ${module?.trim().orEmpty()}")
        ModuleManager.setActiveModule(context, id)
        sendModule(context, "show", id)
        return Result(true, "Module opened: $id")
    }

    private fun refreshModule(context: Context, module: String?): Result {
        val id = validModule(context, module) ?: return Result(false, "Unknown module: ${module?.trim().orEmpty()}")
        if (ModuleManager.getModuleSource(context, id).isEmpty()) return Result(false, "Module has no source: $id")
        sendModule(context, "refresh", id)
        return Result(true, "Module refresh dispatched: $id")
    }

    private fun updateModuleText(context: Context, module: String?, text: String?): Result {
        val id = ModuleManager.normalize(required(module, "Module name is required."))
        if (id.isEmpty()) return Result(false, "Invalid module name.")
        if (!ModuleManager.isKnown(context, id) || ModuleManager.getModuleSource(context, id).isEmpty()) {
            return Result(false, "Module is not an existing script module: $id")
        }
        ModuleManager.setScriptText(context, id, text.orEmpty())
        sendModule(context, "update", id)
        return Result(true, "Module text updated: $id")
    }

    private fun terminalOutput(context: Context, text: String?): Result {
        val output = required(text, "Terminal text is required.")
        Tuils.sendOutput(context.applicationContext, output)
        return Result(true, "Terminal output sent.")
    }

    private fun validModule(context: Context, raw: String?): String? {
        val id = ModuleManager.normalize(raw)
        return if (id.isNotEmpty() && (ModuleManager.isKnown(context, id) || LuaWidgetManager.exists(id))) id else null
    }

    private fun sendModule(context: Context, command: String, module: String) {
        LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(
            Intent(UIManager.ACTION_MODULE_COMMAND)
                .putExtra(UIManager.EXTRA_MODULE_COMMAND, command)
                .putExtra(UIManager.EXTRA_MODULE_NAME, module)
        )
    }

    private fun refreshLauncher() {
        val launcher = LauncherActivity.instance ?: return
        launcher.runOnUiThread { if (!launcher.isFinishing) launcher.reload() }
    }

    private fun required(value: String?, message: String): String =
        value?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException(message)
}
