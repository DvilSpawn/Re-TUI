package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.ClockManager

class stopwatch : CommandAbstraction {
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun exec(pack: ExecutePack): String {
        val arg = pack.get(Any::class.java, 0)
        val input = arg?.toString()?.trim()?.lowercase(Locale.ROOT)

        val clockManager = ClockManager.getInstance(pack.context)

        if (input == null || input.isEmpty() || !input.startsWith("-")) {
            return clockManager.startStopwatch()
        }

        if ("-stop" == input) {
            return clockManager.stopStopwatch()
        }
        if ("-reset" == input) {
            return clockManager.resetStopwatch()
        }
        if ("-status" == input) {
            return clockManager.stopwatchStatus
        }

        return pack.context.getString(R.string.output_invalid_param) + " " + input
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        ClockManager.getInstance(pack.context).startStopwatch()

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String =
        ClockManager.getInstance(pack.context).startStopwatch()

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_stopwatch
}
