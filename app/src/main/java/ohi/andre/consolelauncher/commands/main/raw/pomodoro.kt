package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.managers.PomodoroManager

class pomodoro : CommandAbstraction {
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun exec(pack: ExecutePack): String {
        val input = pack.get(String::class.java, 0)
        val task = input?.trim()
        val manager = PomodoroManager.getInstance(pack.context)

        if (task == "-stop") {
            return if (manager.isRunning) {
                manager.stopSession()
                "Pomodoro session stopped."
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

        TuixtDialog.showInput(
            pack.context,
            "NEW POMODORO",
            "What task are we focusing on?",
            "START",
            "CANCEL"
        ) { value ->
            val taskName = value?.trim().orEmpty().ifEmpty { DEFAULT_TASK }
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
        private const val DEFAULT_TASK = "Focus"
    }
}
