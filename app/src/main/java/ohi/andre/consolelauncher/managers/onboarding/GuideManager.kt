package ohi.andre.consolelauncher.managers.onboarding

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.tuils.Tuils

object GuideManager {
    const val PREFS = "retui_guide"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PATH = "path"
    private const val KEY_STEP = "step"
    private const val KEY_PENDING_RESUME_MESSAGE = "pending_resume_message"
    private const val DEFAULT_PATH = "basics"

    data class Suggestion(val command: String, val execute: Boolean = true)

    private data class Step(
        val title: Int,
        val body: Int,
        val command: String,
        val matchPrefix: String = command,
        val restoreOutputOnResume: Boolean = false
    )

    private data class Path(
        val id: String,
        val title: Int,
        val summary: Int,
        val steps: List<Step>
    )

    private val paths = listOf(
        Path(
            "basics",
            R.string.guide_basics,
            R.string.guide_learn_the_command_surface_app_list_settings_hub_and_module_dock,
            listOf(
                Step(
                    R.string.guide_print_the_map,
                    R.string.guide_help_starts_with_the_workstation_quickstart_and_then_lists_every_comma,
                    "help"
                ),
                Step(
                    R.string.guide_open_the_app_list,
                    R.string.guide_use_the_app_drawer_when_you_want_to_scan_installed_apps_instead_of_typ,
                    "apps -ls"
                ),
                Step(
                    R.string.guide_inspect_modules,
                    R.string.guide_modules_are_terminal_panels_for_status_controls_scripts_and_compact_wo,
                    "module -ls"
                ),
                Step(
                    R.string.guide_open_settings,
                    R.string.guide_the_settings_hub_is_still_available_when_a_visual_edit_surface_is_fast,
                    "settings",
                    restoreOutputOnResume = true
                )
            )
        ),
        Path(
            "customize",
            R.string.guide_customize,
            R.string.guide_try_wallpaper_color_presets_appearance_settings_and_config_discovery,
            listOf(
                Step(
                    R.string.guide_derive_colors,
                    R.string.guide_auto_color_reads_the_current_wallpaper_and_updates_the_terminal_palett,
                    "wallpaper -auto"
                ),
                Step(
                    R.string.guide_list_presets,
                    R.string.guide_presets_are_saved_theme_states_plus_built_in_looks_you_can_apply_later,
                    "preset -ls"
                ),
                Step(
                    R.string.guide_open_appearance_settings,
                    R.string.guide_the_settings_hub_is_the_main_route_for_precise_appearance_changes,
                    "settings",
                    restoreOutputOnResume = true
                ),
                Step(
                    R.string.guide_browse_config,
                    R.string.guide_config_listing_is_the_command_route_into_advanced_launcher_variables,
                    "config -ls"
                )
            )
        ),
        Path(
            "modules",
            R.string.guide_modules,
            R.string.guide_use_built_in_panels_and_the_lua_module_surface_without_leaving_the_ter,
            listOf(
                Step(
                    R.string.guide_list_modules,
                    R.string.guide_start_by_seeing_every_built_in_and_script_backed_module_the_launcher_k,
                    "module -ls"
                ),
                Step(
                    R.string.guide_show_notes,
                    R.string.guide_notes_is_a_small_local_panel_and_a_good_example_of_a_module_as_workspa,
                    "module -show notes"
                ),
                Step(
                    R.string.guide_show_timer,
                    R.string.guide_timer_demonstrates_a_module_with_actions_and_live_status,
                    "module -show timer"
                ),
                Step(
                    R.string.guide_read_lua_module_help,
                    R.string.guide_lua_modules_can_render_text_buttons_actions_app_intents_and_shortcuts,
                    "help module"
                )
            )
        )
    )

    fun overview(context: Context): String {
        val active = activePath(context)
        val output = StringBuilder()
        output.append(context.getString(R.string.guide_guide)).append(Tuils.NEWLINE)
        output.append(context.getString(R.string.guide_non_blocking_walkthroughs_that_use_commands_and_suggestion_chips)).append(Tuils.NEWLINE)
        output.append(Tuils.NEWLINE)
        output.append(context.getString(R.string.guide_paths)).append(Tuils.NEWLINE)
        for (path in paths) {
            output.append("  guide -start ").append(path.id)
                .append(" -> ").append(context.getString(path.title))
                .append(": ").append(context.getString(path.summary))
                .append(Tuils.NEWLINE)
        }
        output.append(Tuils.NEWLINE)
        if (active != null) {
            output.append(context.getString(R.string.guide_active_progress, context.getString(active.title), stepIndex(context) + 1, active.steps.size))
                .append(Tuils.NEWLINE)
            output.append(currentStepText(context, active))
        } else {
            val saved = savedPath(context)
            if (saved != null && savedStepIndex(context, saved) > 0) {
                output.append(context.getString(R.string.guide_resume_with_guide_resume)).append(Tuils.NEWLINE)
                output.append(context.getString(R.string.guide_restart_path, saved.id))
            } else {
                output.append(context.getString(R.string.guide_start_with_guide_start_basics))
            }
        }
        return output.toString()
    }

    fun start(context: Context, requestedPath: String?): String {
        return start(context, requestedPath, false)
    }

    fun restart(context: Context, requestedPath: String?): String {
        return start(context, requestedPath, true)
    }

    private fun start(context: Context, requestedPath: String?, reset: Boolean): String {
        val path = findPath(requestedPath)
        if (path == null) {
            return context.getString(R.string.guide_unknown_path, requestedPath.orEmpty()) + Tuils.NEWLINE + overview(context)
        }

        val step = if (reset) 0 else savedStepIndex(context, path)
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_PATH, path.id)
            .putInt(KEY_STEP, step)
            .putInt(stepKey(path.id), step)
            .apply()
        notifySuggestionsChanged(context)

        val message = if (step > 0 && !reset) R.string.guide_resumed else R.string.guide_started
        return context.getString(message, context.getString(path.title)) + Tuils.NEWLINE + currentStepText(context, path)
    }

    fun resume(context: Context): String {
        val path = savedPath(context) ?: findPath(DEFAULT_PATH) ?: return overview(context)
        return start(context, path.id, false)
    }

    fun status(context: Context): String {
        val path = activePath(context) ?: return overview(context)
        return currentStepText(context, path)
    }

    fun next(context: Context): String {
        val path = activePath(context) ?: return start(context, DEFAULT_PATH)
        val next = stepIndex(context) + 1
        if (next >= path.steps.size) {
            return complete(context, path)
        }
        saveStep(context, path, next)
        return currentStepText(context, path)
    }

    fun back(context: Context): String {
        val path = activePath(context) ?: return overview(context)
        val previous = (stepIndex(context) - 1).coerceAtLeast(0)
        saveStep(context, path, previous)
        return currentStepText(context, path)
    }

    fun off(context: Context): String {
        stopInternal(context)
        return context.getString(R.string.guide_guide_hidden_run_guide_start_basics_to_resume)
    }

    fun reset(context: Context): String {
        prefs(context).edit().clear().apply()
        notifySuggestionsChanged(context)
        return context.getString(R.string.guide_guide_reset) + Tuils.NEWLINE + overview(context)
    }

    fun consumePendingResumeMessage(context: Context): String? {
        val prefs = prefs(context)
        val message = prefs.getString(KEY_PENDING_RESUME_MESSAGE, null)
        if (!message.isNullOrEmpty()) {
            prefs.edit().remove(KEY_PENDING_RESUME_MESSAGE).apply()
        }
        if (message.isNullOrEmpty()) return null
        val path = savedPath(context) ?: return overview(context)
        return if (isActive(context)) currentStepText(context, path) else completionText(context, path)
    }

    fun activeSuggestions(context: Context): List<Suggestion> {
        val path = activePath(context) ?: return emptyList()
        val step = currentStep(context, path) ?: return emptyList()
        return listOf(
            Suggestion(step.command, true),
            Suggestion("guide -next", true),
            Suggestion("guide -back", true),
            Suggestion("guide -off", true)
        )
    }

    fun rootSuggestions(context: Context): List<Suggestion> {
        val suggestions = ArrayList<Suggestion>()
        val active = activePath(context)
        if (active == null) {
            suggestions.add(Suggestion("guide -start basics", true))
            suggestions.add(Suggestion("guide -start customize", true))
            suggestions.add(Suggestion("guide -start modules", true))
        } else {
            suggestions.add(Suggestion("guide -status", true))
            suggestions.add(Suggestion("guide -next", true))
            suggestions.add(Suggestion("guide -off", true))
        }
        return suggestions
    }

    fun subcommandSuggestions(): Array<String> = arrayOf(
        "-start basics",
        "-start customize",
        "-start modules",
        "-resume",
        "-restart basics",
        "-status",
        "-next",
        "-back",
        "-off",
        "-reset",
        "-startup-test"
    )

    fun observeCommand(context: Context, rawCommand: String?): String? {
        val path = activePath(context) ?: return null
        val command = rawCommand?.trim { it <= ' ' } ?: return null
        if (command.length == 0 || command.lowercase(Locale.ROOT).startsWith("guide")) {
            return null
        }

        val step = currentStep(context, path) ?: return null
        val expected = step.matchPrefix.lowercase(Locale.ROOT)
        val actual = command.lowercase(Locale.ROOT)
        if (actual == expected || actual.startsWith(expected + " ")) {
            val output: String
            val next = stepIndex(context) + 1
            if (next >= path.steps.size) {
                output = complete(context, path)
            } else {
                saveStep(context, path, next)
                output = currentStepText(context, path)
            }
            if (step.restoreOutputOnResume) {
                savePendingResumeMessage(context)
                return null
            }
            return output
        }

        return null
    }

    fun isActive(context: Context): Boolean = activePath(context) != null

    private fun currentStepText(context: Context, path: Path): String {
        val index = stepIndex(context).coerceIn(0, path.steps.size - 1)
        val step = path.steps[index]
        val output = StringBuilder()
        output.append(context.getString(path.title)).append(" ")
            .append(progress(index, path.steps.size))
            .append(" ").append(index + 1).append("/").append(path.steps.size)
            .append(Tuils.NEWLINE)
        output.append(context.getString(step.title)).append(Tuils.NEWLINE)
        output.append(context.getString(step.body)).append(Tuils.NEWLINE)
        output.append(context.getString(R.string.guide_run_command, step.command)).append(Tuils.NEWLINE)
        output.append(context.getString(R.string.guide_controls_guide_next_guide_back_guide_off))
        return output.toString()
    }

    private fun progress(index: Int, total: Int): String {
        val width = 8
        val filled = (((index + 1).toFloat() / total.toFloat()) * width).toInt().coerceIn(1, width)
        val output = StringBuilder("[")
        for (i in 0 until width) {
            output.append(if (i < filled) "#" else "-")
        }
        output.append("]")
        return output.toString()
    }

    private fun currentStep(context: Context, path: Path): Step? {
        if (path.steps.isEmpty()) {
            return null
        }
        return path.steps[stepIndex(context).coerceIn(0, path.steps.size - 1)]
    }

    private fun activePath(context: Context): Path? {
        if (!prefs(context).getBoolean(KEY_ACTIVE, false)) {
            return null
        }
        return findPath(prefs(context).getString(KEY_PATH, DEFAULT_PATH))
    }

    private fun stepIndex(context: Context): Int {
        val path = activePath(context)
        if (path != null) {
            return savedStepIndex(context, path)
        }
        return prefs(context).getInt(KEY_STEP, 0)
    }

    private fun savedPath(context: Context): Path? = findPath(prefs(context).getString(KEY_PATH, DEFAULT_PATH))

    private fun savedStepIndex(context: Context, path: Path): Int {
        val fallback = if (path.id == prefs(context).getString(KEY_PATH, DEFAULT_PATH)) {
            prefs(context).getInt(KEY_STEP, 0)
        } else {
            0
        }
        return prefs(context).getInt(stepKey(path.id), fallback).coerceIn(0, path.steps.size - 1)
    }

    private fun saveStep(context: Context, path: Path, index: Int) {
        val normalized = index.coerceIn(0, path.steps.size - 1)
        prefs(context).edit()
            .putString(KEY_PATH, path.id)
            .putInt(KEY_STEP, normalized)
            .putInt(stepKey(path.id), normalized)
            .apply()
        notifySuggestionsChanged(context)
    }

    private fun complete(context: Context, path: Path): String {
        stopInternal(context)
        return completionText(context, path)
    }

    private fun completionText(context: Context, path: Path): String =
        context.getString(R.string.guide_complete, context.getString(path.title)) + Tuils.NEWLINE +
            context.getString(R.string.guide_start_another_path_with_guide_start_customize_or_guide_start_modules)

    private fun savePendingResumeMessage(context: Context) {
        // Retain the existing preference type while regenerating display text in the current locale.
        prefs(context).edit().putString(KEY_PENDING_RESUME_MESSAGE, "pending").apply()
    }

    private fun findPath(id: String?): Path? {
        val normalized = normalizePathId(id)
        return paths.firstOrNull { it.id == normalized }
    }

    private fun normalizePathId(id: String?): String {
        val normalized = (id ?: DEFAULT_PATH).trim { it <= ' ' }.lowercase(Locale.ROOT)
        return if (normalized == "basic") "basics" else normalized
    }

    private fun stepKey(pathId: String): String = KEY_STEP + "_" + pathId

    private fun stopInternal(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).apply()
        notifySuggestionsChanged(context)
    }

    private fun notifySuggestionsChanged(context: Context) {
        LocalBroadcastManager.getInstance(context.applicationContext)
            .sendBroadcast(Intent(UIManager.ACTION_UPDATE_SUGGESTIONS))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
