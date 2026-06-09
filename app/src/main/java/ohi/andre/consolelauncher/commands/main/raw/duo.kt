package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior

class duo : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        if (!LauncherSettings.getBoolean(Behavior.duo_mode)) {
            return "Duo command is disabled. Enable it with: config -set duo_mode true"
        }

        var ui: UIManager? = null
        if (pack.context is LauncherActivity) {
            ui = (pack.context as LauncherActivity).uiManager
        }
        if (ui == null) {
            return "Duo layout is only available from the launcher screen."
        }

        val input = pack.getString()
        var mode = input?.trim()?.lowercase(Locale.US) ?: "status"
        if (mode.isEmpty()) {
            mode = "status"
        }

        if ("status" == mode || "-status" == mode) {
            return status(ui)
        }

        if ("off" == mode || "-off" == mode || "0" == mode) {
            ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
            return "Duo layout off. Normal landscape split restored."
        }

        if ("left" == mode || "-left" == mode) {
            ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_LEFT)
            return appliedMessage(pack, "left")
        }

        if ("right" == mode || "-right" == mode) {
            ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_RIGHT)
            return appliedMessage(pack, "right")
        }

        if ("on" == mode || "-on" == mode || "1" == mode) {
            val side = ui.enableLastDuoSide()
            return appliedMessage(pack, side)
        }

        if ("toggle" == mode || "-toggle" == mode) {
            if (UIManager.DUO_LAYOUT_OFF == ui.getDuoLayoutMode()) {
                val side = ui.enableLastDuoSide()
                return appliedMessage(pack, side)
            }
            ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
            return "Duo layout off. Normal landscape split restored."
        }

        return "Unknown duo option: $input\nUsage: " + CommandTuils.DUO_USAGE
    }

    private fun appliedMessage(pack: ExecutePack, side: String): String {
        val landscape = UIManager.isResponsiveLandscapeConfiguration(pack.context.resources.configuration)
        var message = "Duo layout active on the $side side."
        if (!landscape) {
            message += " Use a wide landscape window to see it."
        }
        return message
    }

    private fun status(ui: UIManager): String =
        "Duo command: enabled\nDuo layout: " + ui.getDuoLayoutMode() + "\nUsage: " + CommandTuils.DUO_USAGE

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_duo

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_duo)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        if (!LauncherSettings.getBoolean(Behavior.duo_mode)) {
            return "Duo command is disabled. Enable it with: config -set duo_mode true"
        }
        if (pack.context is LauncherActivity) {
            val ui = (pack.context as LauncherActivity).uiManager
            if (ui != null) {
                return status(ui)
            }
        }
        return pack.context.getString(R.string.help_duo)
    }
}
