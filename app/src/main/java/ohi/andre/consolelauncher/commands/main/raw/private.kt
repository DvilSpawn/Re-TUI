package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior

class `private` : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        ls {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? = (pack as MainPack).appsManager.listPrivateApps()
        },
        `open` {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
            override fun exec(pack: ExecutePack): String? {
                val manager = (pack as MainPack).appsManager
                if (!manager.hasPrivateSpace()) return pack.context.getString(R.string.output_private_space_unavailable)
                if (manager.isPrivateSpaceLocked()) return pack.context.getString(R.string.output_private_space_locked)
                val app = manager.findPrivateApp(pack.getString())
                    ?: return pack.context.getString(R.string.output_appnotfound)
                return if (manager.launch(pack.context, app)) null
                else pack.context.getString(R.string.output_app_launch_failed)
            }
        },
        lock {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? =
                (pack as MainPack).appsManager.requestPrivateSpaceQuietMode(true)
        },
        unlock {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? =
                (pack as MainPack).appsManager.requestPrivateSpaceQuietMode(false)
        },
        settings {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? = (pack as MainPack).appsManager.openPrivateSpaceSettings()
        },
        show {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? =
                (pack as MainPack).appsManager.setPrivateSpaceEntrypointVisible(true)
        },
        hide {
            override fun args(): IntArray? = IntArray(0)
            override fun exec(pack: ExecutePack): String? =
                (pack as MainPack).appsManager.setPrivateSpaceEntrypointVisible(false)
        };

        override fun label(): String = "-$name"

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? =
            pack.context.getString(R.string.help_private)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? =
            pack.context.getString(R.string.output_invalid_param)

        companion object {
            fun get(value: String): Param? {
                val normalized = value.lowercase()
                return entries.firstOrNull { it.label() == normalized }
            }

            fun labels(): Array<String> = entries.map { it.label() }.toTypedArray()
        }
    }

    override fun params(): Array<out String?> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun doThings(pack: ExecutePack): String? =
        if (XMLPrefsManager.getBoolean(Behavior.enable_private_command)) null
        else pack.context.getString(R.string.output_private_command_disabled)

    override fun helpRes(): Int = R.string.help_private

    override fun priority(): Int = 3
}
