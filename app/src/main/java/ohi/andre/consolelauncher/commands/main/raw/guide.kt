package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.onboarding.GuideManager
import ohi.andre.consolelauncher.tuils.Tuils

class guide : CommandAbstraction, PermanentSuggestionCommand {
    override fun exec(pack: ExecutePack): String {
        val raw = pack.getString().trim { it <= ' ' }
        if (raw.length == 0) {
            return GuideManager.overview(pack.context)
        }

        val parts = Tuils.splitArgs(raw)
        if (parts.isEmpty()) {
            return GuideManager.overview(pack.context)
        }

        val command = parts[0]?.lowercase(Locale.getDefault()) ?: Tuils.EMPTYSTRING
        return when (command) {
            "-start", "start" -> GuideManager.start(pack.context, parts.getOrNull(1))
            "-resume", "resume" -> GuideManager.resume(pack.context)
            "-restart", "restart" -> GuideManager.restart(pack.context, parts.getOrNull(1))
            "-next", "next" -> GuideManager.next(pack.context)
            "-back", "back" -> GuideManager.back(pack.context)
            "-status", "status" -> GuideManager.status(pack.context)
            "-off", "off", "-skip", "skip" -> GuideManager.off(pack.context)
            "-reset", "reset" -> GuideManager.reset(pack.context)
            else -> GuideManager.overview(pack.context)
        }
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 5

    override fun helpRes(): Int = R.string.help_guide

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_guide)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = GuideManager.overview(pack.context)

    override fun permanentSuggestions(): Array<String> = GuideManager.subcommandSuggestions()
}
