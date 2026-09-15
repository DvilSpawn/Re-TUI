package ohi.andre.consolelauncher.commands.main.raw

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.provider.Settings
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.tuils.Tuils
import android.content.ContentResolver
import java.lang.reflect.Method
import android.provider.Settings.System.SCREEN_BRIGHTNESS
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE
import android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC

@Suppress("DEPRECATION")
class status : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val info = pack as MainPack

        val connManager = info.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        val wifiConnected = wifi!!.isConnected

        val batteryIntent = info.context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))!!
        val rawlevel = batteryIntent.getIntExtra("level", -1)
        val scale = batteryIntent.getIntExtra("scale", -1).toDouble()
        var level = -1.0
        if (rawlevel >= 0 && scale > 0) {
            level = rawlevel / scale
        }
        level *= 100.0

        var mobileOn = false
        try {
            val cmClass = Class.forName(connManager::class.java.name)
            val method = cmClass.getDeclaredMethod("getMobileDataEnabled")
            method.isAccessible = true
            mobileOn = method.invoke(connManager) as Boolean
        } catch (ignored: Exception) {
        }

        val cResolver = pack.context.applicationContext.contentResolver
        var brightness = 0
        try {
            brightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (ignored: Settings.SettingNotFoundException) {
        }
        brightness = brightness * 100 / 255

        var autobrightnessState = Int.MIN_VALUE
        try {
            autobrightnessState = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        } catch (ignored: Exception) {
        }

        val locationManager = pack.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var gpsEnabled = false
        var networkEnabled = false

        try {
            gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (ignored: Exception) {
        }
        try {
            networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (ignored: Exception) {
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()
        val bluetoothOn = adapter?.isEnabled ?: false

        val builder = StringBuilder()
        builder
            .append(info.res.getString(R.string.battery_label)).append(Tuils.SPACE).append(level).append("%").append(Tuils.NEWLINE)
            .append(info.res.getString(R.string.wifi_label)).append(Tuils.SPACE).append(wifiConnected).append(Tuils.NEWLINE)
            .append(info.res.getString(R.string.mobile_data_label)).append(Tuils.SPACE).append(mobileOn).append(Tuils.NEWLINE)
            .append(info.res.getString(R.string.bluetooth_label)).append(Tuils.SPACE).append(bluetoothOn).append(Tuils.NEWLINE)
            .append(info.res.getString(R.string.location_label)).append(Tuils.SPACE).append(gpsEnabled || networkEnabled).append(Tuils.NEWLINE)
            .append(info.res.getString(R.string.brightness_label)).append(Tuils.SPACE)
            .append(if (autobrightnessState == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) pack.context.getString(R.string.command_status_auto_2130c) else Tuils.EMPTYSTRING)
            .append(brightness).append("%")

        return builder.toString()
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 1

    override fun helpRes(): Int = R.string.help_status

    override fun onArgNotFound(info: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(info: ExecutePack, nArgs: Int): String? = null
}
