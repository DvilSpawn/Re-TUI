package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class username : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val newUser = pack.getString()
        val newDevice = pack.getString()

        LauncherSettings.setUi(Ui.username, newUser)
        LauncherSettings.setUi(Ui.deviceName, newDevice)

        try {
            if (pack.context is Reloadable) {
                (pack.context as Reloadable).reload()
            }
        } catch (e: Exception) {
        }

        return "Username and Device updated!"
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.NO_SPACE_STRING)

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_username

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = pack.context.getString(R.string.help_username)

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null
}
