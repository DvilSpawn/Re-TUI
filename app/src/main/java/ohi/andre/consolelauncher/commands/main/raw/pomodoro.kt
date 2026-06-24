package ohi.andre.consolelauncher.commands.main.raw

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
                    "Pomodoro session closed."
                } else if (!RetuiCreditManager.isDystopiaEnabled(pack.context)) {
                    manager.stopSession()
                    "Pomodoro session stopped."
                } else if (RetuiCreditManager.spendCredits(pack.context)) {
                    manager.stopSession()
                    "Pomodoro stopped. -${RetuiCreditManager.ESCAPE_COST} credits."
                } else {
                    BreachDialog.show(pack.context, BreachManager.Mode.EMERGENCY) { won ->
                        if (won) {
                            PomodoroManager.getInstance(pack.context).stopSession()
                        }
                    }
                    "Not enough credits. Opening emergency breach..."
                }
            } else {
                "No Pomodoro session is running."
            }
        }

        if (manager.isRunning) {
            return "A Pomodoro session is already active."
        }

        if (!task.isNullOrEmpty()) {
            manager.startPomodoro(task)
            return "Pomodoro started: $task"
        }

        TuixtDialog.showValidatedForm(
            pack.context,
            "NEW POMODORO",
            listOf(FormField(FIELD_GOAL, "Goal", "What task are we focusing on?")),
            "START",
            "CANCEL",
            { values -> validatePomodoroForm(values) }
        ) { values ->
            val taskName = values[FIELD_GOAL].orEmpty()
            val currentManager = PomodoroManager.getInstance(pack.context)
            if (!currentManager.isRunning) {
                currentManager.startPomodoro(taskName)
            }
        }

        return "Opening Pomodoro setup..."
    }

    override fun helpRes(): Int = 0

    override fun priority(): Int = 2

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = exec(pack)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)

    companion object {
        private const val FIELD_GOAL = "goal"

        private fun validatePomodoroForm(values: Map<String, String>): String? =
            if (values[FIELD_GOAL].isNullOrBlank()) "Goal is missing." else null
    }
}
