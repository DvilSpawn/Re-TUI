@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack

class wifi : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openWifiSettings(info)
            return "Opening Wi-Fi settings. Android no longer allows third-party launchers to toggle Wi-Fi directly."
        }

        if (info.wifi == null) {
            info.wifi = info.context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        }
        val wifi = info.wifi!!
        val active = !wifi.isWifiEnabled
        wifi.isWifiEnabled = active
        return info.res.getString(R.string.output_wifi) + " " + active.toString()
    }

    override fun helpRes(): Int = R.string.help_wifi

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 2

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    private fun openWifiSettings(info: MainPack) {
        info.context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}
