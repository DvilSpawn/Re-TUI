package ohi.andre.consolelauncher.managers.onboarding

import android.content.Context
import java.util.Locale
import ohi.andre.consolelauncher.tuils.Tuils

object GuideManager {
    private const val PREFS = "retui_guide"
    private const val KEY_ACTIVE = "active"
    private const val KEY_PATH = "path"
    private const val KEY_STEP = "step"
    private const val DEFAULT_PATH = "basics"

    data class Suggestion(val command: String, val execute: Boolean = true)

    private data class Step(
        val title: String,
        val body: String,
        val command: String,
        val matchPrefix: String = command
    )

    private data class Path(
        val id: String,
        val title: String,
        val summary: String,
        val steps: List<Step>
    )

    private val paths = listOf(
        Path(
            "basics",
            "Basics",
            "Learn the command surface, app list, settings hub, and module dock.",
            listOf(
                Step(
                    "Print the map",
                    "Help starts with the workstation quickstart and then lists every command.",
                    "help"
                ),
                Step(
                    "Open the app list",
                    "Use the app drawer when you want to scan installed apps instead of typing a name.",
                    "apps -ls"
                ),
                Step(
                    "Inspect modules",
                    "Modules are terminal panels for status, controls, scripts, and compact workflows.",
                    "module -ls"
                ),
                Step(
                    "Open settings",
                    "The settings hub is still available when a visual edit surface is faster than XML.",
                    "settings"
                )
            )
        ),
        Path(
            "customize",
            "Customize",
            "Try wallpaper color, presets, theme commands, and config discovery.",
            listOf(
                Step(
                    "Derive colors",
                    "Auto color reads the current wallpaper and updates the terminal palette.",
                    "wallpaper -auto"
                ),
                Step(
                    "List presets",
                    "Presets are saved theme states plus built-in looks you can apply later.",
                    "preset -ls"
                ),
                Step(
                    "Read theme controls",
                    "Theme command help shows the command route for precise color changes.",
                    "help theme"
                ),
                Step(
                    "Browse config",
                    "Config listing is the command route into advanced launcher variables.",
                    "config -ls"
                )
            )
        ),
        Path(
            "modules",
            "Modules",
            "Use built-in panels and the Lua module surface without leaving the terminal.",
            listOf(
                Step(
                    "List modules",
                    "Start by seeing every built-in and script-backed module the launcher knows.",
                    "module -ls"
                ),
                Step(
                    "Show notes",
                    "Notes is a small local panel and a good example of a module as workspace.",
                    "module -show notes"
                ),
                Step(
                    "Show timer",
                    "Timer demonstrates a module with actions and live status.",
                    "module -show timer"
                ),
                Step(
                    "Read Lua module help",
                    "Lua modules can render text, buttons, actions, app intents, and shortcuts.",
                    "help module"
                )
            )
        )
    )

    fun overview(context: Context): String {
        val active = activePath(context)
        val output = StringBuilder()
        output.append("Guide").append(Tuils.NEWLINE)
        output.append("Non-blocking walkthroughs that use commands and suggestion chips.").append(Tuils.NEWLINE)
        output.append(Tuils.NEWLINE)
        output.append("Paths:").append(Tuils.NEWLINE)
        for (path in paths) {
            output.append("  guide -start ").append(path.id)
                .append(" -> ").append(path.title)
                .append(": ").append(path.summary)
                .append(Tuils.NEWLINE)
        }
        output.append(Tuils.NEWLINE)
        if (active != null) {
            output.append("Active: ").append(active.title)
                .append(" step ").append(stepIndex(context) + 1)
                .append("/").append(active.steps.size)
                .append(Tuils.NEWLINE)
            output.append(currentStepText(context, active))
        } else {
            val saved = savedPath(context)
            if (saved != null && savedStepIndex(context, saved) > 0) {
                output.append("Resume with: guide -resume").append(Tuils.NEWLINE)
                output.append("Restart with: guide -reset, then guide -start ").append(saved.id)
            } else {
                output.append("Start with: guide -start basics")
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
            return "Unknown guide path: " + (requestedPath ?: "") + Tuils.NEWLINE + overview(context)
        }

        val step = if (reset) 0 else savedStepIndex(context, path)
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_PATH, path.id)
            .putInt(KEY_STEP, step)
            .putInt(stepKey(path.id), step)
            .commit()

        val action = if (step > 0 && !reset) "Resumed guide: " else "Started guide: "
        return action + path.title + Tuils.NEWLINE + currentStepText(context, path)
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
            stopInternal(context)
            return "Guide complete: " + path.title + Tuils.NEWLINE + "Start another path with guide -start customize or guide -start modules."
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
        return "Guide hidden. Run guide -start basics to resume."
    }

    fun reset(context: Context): String {
        prefs(context).edit().clear().commit()
        return "Guide reset." + Tuils.NEWLINE + overview(context)
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
        "-reset"
    )

    fun observeCommand(context: Context, rawCommand: String?) {
        val path = activePath(context) ?: return
        val command = rawCommand?.trim { it <= ' ' } ?: return
        if (command.length == 0 || command.lowercase(Locale.getDefault()).startsWith("guide")) {
            return
        }

        val step = currentStep(context, path) ?: return
        val expected = step.matchPrefix.lowercase(Locale.getDefault())
        val actual = command.lowercase(Locale.getDefault())
        if (actual == expected || actual.startsWith(expected + " ")) {
            val next = stepIndex(context) + 1
            if (next >= path.steps.size) {
                stopInternal(context)
            } else {
                saveStep(context, path, next)
            }
        }
    }

    fun isActive(context: Context): Boolean = activePath(context) != null

    private fun currentStepText(context: Context, path: Path): String {
        val index = stepIndex(context).coerceIn(0, path.steps.size - 1)
        val step = path.steps[index]
        val output = StringBuilder()
        output.append(path.title).append(" ")
            .append(progress(index, path.steps.size))
            .append(" ").append(index + 1).append("/").append(path.steps.size)
            .append(Tuils.NEWLINE)
        output.append(step.title).append(Tuils.NEWLINE)
        output.append(step.body).append(Tuils.NEWLINE)
        output.append("Run: ").append(step.command).append(Tuils.NEWLINE)
        output.append("Controls: guide -next, guide -back, guide -off")
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
            .commit()
    }

    private fun findPath(id: String?): Path? {
        val normalized = normalizePathId(id)
        return paths.firstOrNull { it.id == normalized }
    }

    private fun normalizePathId(id: String?): String {
        val normalized = (id ?: DEFAULT_PATH).trim { it <= ' ' }.lowercase(Locale.getDefault())
        return if (normalized == "basic") "basics" else normalized
    }

    private fun stepKey(pathId: String): String = KEY_STEP + "_" + pathId

    private fun stopInternal(context: Context) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, false).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
