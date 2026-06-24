package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.ClockManager
import ohi.andre.consolelauncher.managers.LockdownManager
import ohi.andre.consolelauncher.managers.RetuiCreditManager

class lockdown : CommandAbstraction {
    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun exec(pack: ExecutePack): String {
        if (!RetuiCreditManager.isDystopiaEnabled(pack.context)) {
            return RetuiCreditManager.status(pack.context)
        }
        val input = pack.get(String::class.java, 0)?.trim().orEmpty()
        val manager = LockdownManager.getInstance(pack.context)

        if (input.isEmpty()) {
            return HELP
        }

        if (input.startsWith("-")) {
            val split = input.split("\\s+".toRegex(), limit = 2)
            return when (split[0].lowercase(Locale.US)) {
                "-status" -> manager.status
                "-stop" -> "Use the lockdown screen to spend credits, use a breach key, or run emergency breach."
                else -> "Invalid lockdown option: ${split[0]}"
            }
        }

        val split = input.split("\\s+".toRegex(), limit = 2)
        val duration = ClockManager.parseDurationMillis(split[0])
        val reason = if (split.size > 1) split[1] else ""
        return manager.start(duration, reason)
    }

    override fun priority(): Int = 2

    override fun helpRes(): Int = 0

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = HELP

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = HELP

    companion object {
        private const val HELP = "Usage: lockdown [duration] [reason]\nExample: lockdown 30m deep work\nOptions: -status, -stop"
    }
}
