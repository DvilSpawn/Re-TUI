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
            return pack.context.getString(R.string.command_duo_duo_command_is_disabled_enable_it_with_con_7aacc)
        }

        var ui: UIManager? = null
        if (pack.context is LauncherActivity) {
            ui = (pack.context as LauncherActivity).uiManager
        }
        if (ui == null) {
            return pack.context.getString(R.string.command_duo_duo_layout_is_only_available_from_the_laun_26934)
        }

        val input = pack.getString()
        var mode = input?.trim()?.lowercase(Locale.US) ?: "status"
        if (mode.isEmpty()) {
            mode = "status"
        }

        if ("status" == mode || "-status" == mode) {
            return status(pack, ui)
        }

        if ("off" == mode || "-off" == mode || "0" == mode) {
            ui.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
            return pack.context.getString(R.string.command_duo_duo_layout_off_normal_landscape_split_rest_ab679)
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
            return pack.context.getString(R.string.command_duo_duo_layout_off_normal_landscape_split_rest_ab679)
        }

        return pack.context.getString(R.string.command_duo_unknown_duo_option_usage_b3f91, input, CommandTuils.DUO_USAGE)
    }

    private fun appliedMessage(pack: ExecutePack, side: String): String {
        val landscape = UIManager.isResponsiveLandscapeConfiguration(pack.context.resources.configuration)
        var message = pack.context.getString(R.string.command_duo_duo_layout_active_on_the_side_ff742, side)
        if (!landscape) {
            message += pack.context.getString(R.string.command_duo_use_a_wide_landscape_window_to_see_it_770de)
        }
        return message
    }

    private fun status(pack: ExecutePack, ui: UIManager): String =
        pack.context.getString(R.string.duo_status, ui.getDuoLayoutMode(), CommandTuils.DUO_USAGE)

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_duo

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.help_duo)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String {
        if (!LauncherSettings.getBoolean(Behavior.duo_mode)) {
            return pack.context.getString(R.string.command_duo_duo_command_is_disabled_enable_it_with_con_7aacc)
        }
        if (pack.context is LauncherActivity) {
            val ui = (pack.context as LauncherActivity).uiManager
            if (ui != null) {
                return status(pack, ui)
            }
        }
        return pack.context.getString(R.string.help_duo)
    }
}
