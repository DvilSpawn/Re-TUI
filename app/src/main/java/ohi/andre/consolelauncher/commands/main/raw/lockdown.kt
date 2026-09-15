package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import android.text.InputType
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
                "-stop" -> pack.context.getString(R.string.command_lockdown_use_the_lockdown_screen_to_spend_credits_u_7424e)
                else -> pack.context.getString(R.string.command_lockdown_invalid_lockdown_option_716ed, split[0])
            }
        }

        if (manager.isRunning) {
            return pack.context.getString(R.string.command_lockdown_a_lockdown_is_already_active_68d6d)
        }

        if (input.isEmpty()) {
            TuixtDialog.showValidatedForm(
                pack.context,
                pack.context.getString(R.string.command_lockdown_new_lockdown_eb76f),
                listOf(
                    FormField(FIELD_HOURS, pack.context.getString(R.string.command_lockdown_hours_9e25a), "00", InputType.TYPE_CLASS_NUMBER),
                    FormField(FIELD_MINUTES, pack.context.getString(R.string.command_lockdown_minutes_092f9), "00", InputType.TYPE_CLASS_NUMBER),
                    FormField(FIELD_REASON, pack.context.getString(R.string.command_lockdown_reason_f219c), pack.context.getString(R.string.command_lockdown_reason_f219c))
                ),
                pack.context.getString(R.string.command_lockdown_start_7196e),
                pack.context.getString(R.string.command_lockdown_cancel_1507c),
                { values -> validateLockdownForm(values)?.let(pack.context::getString) }
            ) { values ->
                val result = manager.start(
                    durationFromParts(values),
                    values[FIELD_REASON]
                )
                Tuils.sendOutput(pack.context, result)
            }
            return pack.context.getString(R.string.command_lockdown_opening_lockdown_setup_25318)
        }

        return startFromInput(pack.context, manager, input)
    }

    override fun priority(): Int = 2

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)

    companion object {
        private const val FIELD_HOURS = "hours"
        private const val FIELD_MINUTES = "minutes"
        private const val FIELD_REASON = "reason"

        private fun startFromInput(context: android.content.Context, manager: LockdownManager, input: String): String {
            val split = input.split("\\s+".toRegex(), limit = 2)
            val durationText = split.getOrNull(0).orEmpty()
            val reason = if (split.size > 1) split[1] else ""
            val error = validateDurationReason(durationText, reason)
            if (error != null) return context.getString(error)
            val duration = ClockManager.parseDurationMillis(durationText)
            return manager.start(duration, reason)
        }

        private fun validateLockdownForm(values: Map<String, String>): Int? {
            val hours = values[FIELD_HOURS].orEmpty()
            val minutes = values[FIELD_MINUTES].orEmpty()
            if (hours.isBlank() && minutes.isBlank()) return R.string.validation_duration_missing
            if (durationFromParts(values) <= 0L) return R.string.validation_duration_invalid
            if (values[FIELD_REASON].isNullOrBlank()) return R.string.validation_reason_missing
            return null
        }

        private fun validateDurationReason(duration: String, reason: String): Int? {
            if (duration.isBlank()) return R.string.validation_duration_missing
            if (reason.isBlank()) return R.string.validation_reason_missing
            if (ClockManager.parseDurationMillis(duration) <= 0L) return R.string.validation_duration_invalid
            return null
        }

        private fun durationFromParts(values: Map<String, String>): Long {
            val hoursText = values[FIELD_HOURS].orEmpty()
            val minutesText = values[FIELD_MINUTES].orEmpty()
            val hours = if (hoursText.isBlank()) 0L else hoursText.toLongOrNull() ?: return 0L
            val minutes = if (minutesText.isBlank()) 0L else minutesText.toLongOrNull() ?: return 0L
            return ((hours * 60L) + minutes) * 60_000L
        }
    }
}
