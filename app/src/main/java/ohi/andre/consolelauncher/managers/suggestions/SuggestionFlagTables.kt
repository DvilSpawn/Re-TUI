package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.CommandTuils.duoOptions
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.apps.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getBoolean
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

/** Root and option suggestion tables for commands that take fixed flags. */
internal object SuggestionFlagTables {
    internal val timerQuickSuggestions: Array<String>
        get() = arrayOf<String>("5m", "15m", "30m", "60m")

    private val INTENT_BROADCAST_PARAMETERS = arrayOf(
        SuggestionsManager.INTENT_PARAMETERS[0], SuggestionsManager.INTENT_PARAMETERS[3], SuggestionsManager.INTENT_PARAMETERS[4]
    )

    fun suggestClockCommandRoots(pack: MainPack, suggestions: MutableList<Suggestion?>, lastWord: String?) {
        if (lastWord == null) {
            return
        }

        val lower = lastWord.lowercase(Locale.getDefault())
        if ("timer".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "timer -add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "timer -stop",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "timer -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (quick in timerQuickSuggestions) {
                suggestions.add(
                    Suggestion(
                        null,
                        "timer " + quick,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }

        if ("stopwatch".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "stopwatch",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in arrayOf<String>(
                "stopwatch -stop",
                "stopwatch -reset",
                "stopwatch -status"
            )) {
                suggestions.add(Suggestion(null, option, true, Suggestion.Companion.TYPE_PERMANENT))
            }
        }

        if ("termux".startsWith(lower)) {
            suggestions.add(Suggestion(null, "termux", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "termux -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -setup",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -open",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -apps",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -run",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -app",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tmux launch",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tmux status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "termux -rm-app",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        suggestTermuxAppRoots(pack, suggestions, lower)

        if ("lua".startsWith(lower)) {
            suggestions.add(Suggestion(null, "lua -apps", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -new app", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -app", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -edit", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(Suggestion(null, "lua -check", false, Suggestion.Companion.TYPE_PERMANENT))
        }

        if ("tbridge".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -setup",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "tbridge -probe",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("shell".startsWith(lower)) {
            suggestions.add(Suggestion(null, "shell", false, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "shell pwd",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(Suggestion(null, "shell ls", true, Suggestion.Companion.TYPE_PERMANENT))
            suggestions.add(
                Suggestion(
                    null,
                    "shell cd",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "shell cd ..",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("retui-token".startsWith(lower) || "retuitoken".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -status",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -show",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -rotate",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "retui-token -off",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("module".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "module -ls",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -refresh",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -rm",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -show",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -close",
                    true,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -dock add",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            suggestions.add(
                Suggestion(
                    null,
                    "module -dock remove",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
        }

        if ("orientation".startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    "orientation",
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in arrayOf<String>("portrait", "landscape", "auto")) {
                suggestions.add(
                    Suggestion(
                        null,
                        "orientation " + option,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }

        if (getBoolean(Behavior.duo_mode) && CommandTuils.DUO_COMMAND.startsWith(lower)) {
            suggestions.add(
                Suggestion(
                    null,
                    CommandTuils.DUO_COMMAND,
                    false,
                    Suggestion.Companion.TYPE_PERMANENT
                )
            )
            for (option in duoOptions()) {
                suggestions.add(
                    Suggestion(
                        null,
                        CommandTuils.DUO_COMMAND + " " + option,
                        true,
                        Suggestion.Companion.TYPE_PERMANENT
                    )
                )
            }
        }
    }

    fun suggestDuoOptions(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter = if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase()
        for (option in duoOptions()) {
            if (filter.length == 0 || option!!.startsWith(filter)) {
                suggestions.add(
                    SuggestionsManager.Suggestion(
                        beforeLastSpace,
                        option!!,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    fun suggestOrientationOptions(
        suggestions: MutableList<Suggestion?>,
        lastWord: String?,
        beforeLastSpace: String?
    ) {
        val filter =
            if (lastWord == null) Tuils.EMPTYSTRING else lastWord.lowercase(Locale.getDefault())
        for (option in arrayOf<String>("portrait", "landscape", "auto")) {
            if (filter.length == 0 || option.startsWith(filter)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        option,
                        true,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    fun suggestIntentCommandArgs(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?,
        normalized: String,
        appCount: () -> Int
    ): Boolean {
        if ("inspect" == normalized) {
            suggestIntentPackages(pack, suggestions, afterLastSpace, beforeLastSpace, appCount)
            return true
        }
        if (normalized.startsWith("inspect ")) {
            return true
        }
        if (!isIntentCommand(normalized)) {
            return false
        }

        val lastToken = normalized.split("\\s+".toRegex()).lastOrNull()
        if ("-p" == lastToken) {
            suggestIntentPackages(pack, suggestions, afterLastSpace, beforeLastSpace, appCount)
            return true
        }

        if (isIntentValueToken(lastToken)) {
            return true
        }

        if ("intent" == normalized) {
            addIntentActions(suggestions, afterLastSpace)
            return true
        }

        if ("intent -activity" == normalized || "intent activity" == normalized) {
            addIntentOptions(suggestions, beforeLastSpace, afterLastSpace, *SuggestionsManager.INTENT_PARAMETERS)
            return true
        }

        if ("intent -broadcast" == normalized || "intent broadcast" == normalized) {
            addIntentOptions(suggestions, beforeLastSpace, afterLastSpace, *INTENT_BROADCAST_PARAMETERS)
            return true
        }

        addIntentOptions(
            suggestions,
            beforeLastSpace,
            afterLastSpace,
            *SuggestionsManager.INTENT_PARAMETERS
        )
        return true
    }

    private fun isIntentCommand(normalized: String): Boolean {
        return "intent" == normalized || normalized.startsWith("intent ")
    }

    private fun isIntentValueToken(token: String?): Boolean {
        return "-a" == token || "-d" == token || "-t" == token || "-n" == token
            || "--es" == token || "--ei" == token || "--ez" == token
    }

    private fun addIntentOptions(
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        afterLastSpace: String?,
        vararg options: Pair<String, String>
    ) {
        val filter = afterLastSpace?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
        val normalizedFilter = filter.replace("-", Tuils.EMPTYSTRING)
        for ((option, label) in options) {
            val normalizedOption = option.replace("-", Tuils.EMPTYSTRING)
            if (filter.length == 0 || option.startsWith(filter) || normalizedOption.startsWith(normalizedFilter)
                || label.lowercase(Locale.getDefault()).startsWith(filter.lowercase(Locale.getDefault()))) {
                val prefix = beforeLastSpace?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
                val command = if (prefix.isEmpty()) option else prefix + Tuils.SPACE + option
                suggestions.add(Suggestion(null, label, false, Suggestion.Companion.TYPE_MODULE, command))
            }
        }
    }

    private fun addIntentActions(suggestions: MutableList<Suggestion?>, afterLastSpace: String?) {
        val filter = afterLastSpace?.trim()?.lowercase(Locale.getDefault()) ?: Tuils.EMPTYSTRING
        for ((label, command) in SuggestionsManager.INTENT_ROOT_ACTIONS) {
            if (filter.isEmpty() || label.lowercase(Locale.getDefault()).startsWith(filter)
                || command.substringAfterLast(' ').removePrefix("-").startsWith(filter.removePrefix("-"))) {
                suggestions.add(Suggestion(null, label, false, Suggestion.Companion.TYPE_MODULE, command))
            }
        }
    }

    private fun suggestIntentPackages(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?,
        appCount: () -> Int
    ) {
        val apps: MutableList<LaunchInfo> = ArrayList<LaunchInfo>(pack.appsManager.shownApps())
        try {
            apps.addAll(pack.appsManager.hiddenApps())
        } catch (e: Exception) {
            Tuils.log(e)
        }

        val filter = afterLastSpace?.trim { it <= ' ' }?.lowercase(Locale.getDefault()) ?: Tuils.EMPTYSTRING
        val added = LinkedHashSet<String?>()
        var canInsert = appCount()
        for (app in apps) {
            if (canInsert == 0) {
                return
            }

            val pkg = app.componentName?.getPackageName() ?: continue
            if (!added.add(pkg)) {
                continue
            }

            val label = app.publicLabel ?: pkg
            if (filter.length > 0
                && !pkg.lowercase(Locale.getDefault()).startsWith(filter)
                && !label.lowercase(Locale.getDefault()).startsWith(filter)
            ) {
                continue
            }

            val prefix = beforeLastSpace?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
            val command = if (prefix.length == 0) pkg else prefix + Tuils.SPACE + pkg
            suggestions.add(
                Suggestion(
                    null,
                    label,
                    prefix.equals("inspect", ignoreCase = true),
                    Suggestion.Companion.TYPE_MODULE,
                    command
                )
            )
            canInsert--
        }
    }

    private fun suggestTermuxAppRoots(pack: MainPack, suggestions: MutableList<Suggestion?>, lower: String) {
        if (lower.isEmpty()) return
        suggestions.addAll(SuggestionStatics.termuxLauncherSuggestions(TermuxWorkspaceLauncherManager.list(pack.context), lower))
    }
}
