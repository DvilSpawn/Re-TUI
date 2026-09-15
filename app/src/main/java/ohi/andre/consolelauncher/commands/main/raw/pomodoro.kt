package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.BreachDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.FormField
import ohi.andre.consolelauncher.managers.BreachManager
import ohi.andre.consolelauncher.managers.PomodoroManager
import ohi.andre.consolelauncher.managers.PomodoroManager.SessionType
import ohi.andre.consolelauncher.managers.RetuiCreditManager

class pomodoro : CommandAbstraction {
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun exec(pack: ExecutePack): String {
        val input = pack.get(String::class.java, 0)
        val task = input?.trim()
        val manager = PomodoroManager.getInstance(pack.context)

        if (task == "-stop") {
            return if (manager.isRunning) {
                if (manager.currentType == SessionType.FINISHED) {
                    manager.stopSession()
                    pack.context.getString(R.string.command_pomodoro_pomodoro_session_closed_c9cf3)
                } else if (!RetuiCreditManager.isDystopiaEnabled(pack.context)) {
                    manager.stopSession()
                    pack.context.getString(R.string.command_pomodoro_pomodoro_session_stopped_d2e19)
                } else if (RetuiCreditManager.spendCredits(pack.context)) {
                    manager.stopSession()
                    pack.context.getString(R.string.command_pomodoro_pomodoro_stopped_credits_cc0e4, RetuiCreditManager.ESCAPE_COST)
                } else {
                    BreachDialog.show(pack.context, BreachManager.Mode.EMERGENCY) { won ->
                        if (won) {
                            PomodoroManager.getInstance(pack.context).stopSession()
                        }
                    }
                    pack.context.getString(R.string.command_pomodoro_not_enough_credits_opening_emergency_breac_e8937)
                }
            } else {
                pack.context.getString(R.string.command_pomodoro_no_pomodoro_session_is_running_248bd)
            }
        }

        if (manager.isRunning) {
            return pack.context.getString(R.string.command_pomodoro_a_pomodoro_session_is_already_active_abd1a)
        }

        if (!task.isNullOrEmpty()) {
            manager.startPomodoro(task)
            return pack.context.getString(R.string.command_pomodoro_pomodoro_started_7a76d, task)
        }

        TuixtDialog.showValidatedForm(
            pack.context,
            pack.context.getString(R.string.command_pomodoro_new_pomodoro_c0b65),
            listOf(FormField(FIELD_GOAL, pack.context.getString(R.string.command_pomodoro_goal_9fe00), pack.context.getString(R.string.command_pomodoro_what_task_are_we_focusing_on_a99be))),
            pack.context.getString(R.string.command_pomodoro_start_7196e),
            pack.context.getString(R.string.command_pomodoro_cancel_1507c),
            { values -> validatePomodoroForm(values)?.let(pack.context::getString) }
        ) { values ->
            val taskName = values[FIELD_GOAL].orEmpty()
            val currentManager = PomodoroManager.getInstance(pack.context)
            if (!currentManager.isRunning) {
                currentManager.startPomodoro(taskName)
            }
        }

        return pack.context.getString(R.string.command_pomodoro_opening_pomodoro_setup_32cb0)
    }

    override fun helpRes(): Int = 0

    override fun priority(): Int = 2

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)

    companion object {
        private const val FIELD_GOAL = "goal"

        private fun validatePomodoroForm(values: Map<String, String>): Int? =
            if (values[FIELD_GOAL].isNullOrBlank()) R.string.validation_goal_missing else null
    }
}
