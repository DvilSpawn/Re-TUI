package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.TuiLocationManager

class location : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val context: Context = (pack as MainPack).context

        val location = TuiLocationManager.instance(context) ?: return null
        if (location.locationAvailable) {
            return "Lat: " + location.latitude + "; Long: " + location.longitude
        } else {
            location.add(ACTION_LOCATION_CMD_GOT)
        }

        return null
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 3

    override fun helpRes(): Int = R.string.help_location

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null

    companion object {
        @JvmField var ACTION_LOCATION_CMD_GOT: String = BuildConfig.APPLICATION_ID + ".loc_cmd_location"
    }
}
