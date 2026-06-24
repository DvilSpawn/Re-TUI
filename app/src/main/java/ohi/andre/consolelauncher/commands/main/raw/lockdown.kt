package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormField
import ohi.andre.consolelauncher.managers.ClockManager
import ohi.andre.consolelauncher.managers.LockdownManager
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.tuils.Tuils

class lockdown : CommandAbstraction {
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun exec(pack: ExecutePack): String {
        if (!RetuiCreditManager.isDystopiaEnabled(pack.context)) {
            return RetuiCreditManager.status(pack.context)
        }
        val input = pack.get(String::class.java, 0)?.trim().orEmpty()
        val manager = LockdownManager.getInstance(pack.context)

        if (input.startsWith("-")) {
            val split = input.split("\\s+".toRegex(), limit = 2)
            return when (split[0].lowercase(Locale.US)) {
                "-status" -> manager.status
                "-stop" -> "Use the lockdown screen to spend credits, use a breach key, or run emergency breach."
                else -> "Invalid lockdown option: ${split[0]}"
            }
        }

        if (manager.isRunning) {
            return "A lockdown is already active."
        }

        if (input.isEmpty()) {
            TuixtDialog.showValidatedForm(
                pack.context,
                "NEW LOCKDOWN",
                listOf(
                    FormField(FIELD_DURATION, "Duration", "00h 00m"),
                    FormField(FIELD_REASON, "Reason", "Reason")
                ),
                "START",
                "CANCEL",
                { values -> validateLockdownForm(values) }
            ) { values ->
                val result = manager.start(
                    ClockManager.parseDurationMillis(values[FIELD_DURATION]),
                    values[FIELD_REASON]
                )
                Tuils.sendOutput(pack.context, result)
            }
            return "Opening Lockdown setup..."
        }

        return startFromInput(manager, input)
    }

    override fun priority(): Int = 2

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)

    companion object {
        private const val HELP = "Usage: lockdown [duration] [reason]\nExample: lockdown 30m deep work\nOptions: -status, -stop"
        private const val FIELD_DURATION = "duration"
        private const val FIELD_REASON = "reason"

        private fun startFromInput(manager: LockdownManager, input: String): String {
            val split = input.split("\\s+".toRegex(), limit = 2)
            val durationText = split.getOrNull(0).orEmpty()
            val reason = if (split.size > 1) split[1] else ""
            val error = validateDurationReason(durationText, reason)
            if (error != null) return error
            val duration = ClockManager.parseDurationMillis(durationText)
            return manager.start(duration, reason)
        }

        private fun validateLockdownForm(values: Map<String, String>): String? {
            return validateDurationReason(values[FIELD_DURATION].orEmpty(), values[FIELD_REASON].orEmpty())
        }

        private fun validateDurationReason(duration: String, reason: String): String? {
            if (duration.isBlank()) return "Duration is missing."
            if (reason.isBlank()) return "Reason is missing."
            if (ClockManager.parseDurationMillis(duration) <= 0L) return "Duration is invalid."
            return null
        }
    }
}
