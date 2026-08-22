package ohi.andre.consolelauncher.managers.status

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.NetUtils.getIPAddress
import ohi.andre.consolelauncher.tuils.Tuils
import java.lang.reflect.Method
import java.util.Locale
import java.util.regex.Pattern
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import java.util.regex.Matcher
import ohi.andre.consolelauncher.tuils.NetUtils

class NetworkManager(
    context: Context,
    delay: Long,
    private val size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    private val zero = "0"
    private val one = "1"
    private val on = "on"
    private val off = "off"
    private val ON = on.uppercase(Locale.getDefault())
    private val OFF = off.uppercase(Locale.getDefault())
    private val _true = "true"
    private val _false = "false"
    private val TRUE = _true.uppercase(Locale.getDefault())
    private val FALSE = _false.uppercase(Locale.getDefault())

    private val w0: Pattern = Pattern.compile("%w0", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val w1: Pattern = Pattern.compile("%w1", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val w2: Pattern = Pattern.compile("%w2", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val w3: Pattern = Pattern.compile("%w3", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val w4: Pattern = Pattern.compile("%w4", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val wn: Pattern = Pattern.compile("%wn", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val d0: Pattern = Pattern.compile("%d0", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val d1: Pattern = Pattern.compile("%d1", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val d2: Pattern = Pattern.compile("%d2", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val d3: Pattern = Pattern.compile("%d3", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val d4: Pattern = Pattern.compile("%d4", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val b0: Pattern = Pattern.compile("%b0", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val b1: Pattern = Pattern.compile("%b1", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val b2: Pattern = Pattern.compile("%b2", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val b3: Pattern = Pattern.compile("%b3", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val b4: Pattern = Pattern.compile("%b4", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val ip4: Pattern = Pattern.compile("%ip4", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val ip6: Pattern = Pattern.compile("%ip6", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val dt: Pattern = Pattern.compile("%dt", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    private var optionalWifi: Pattern? = null
    private var optionalData: Pattern? = null
    private var optionalBluetooth: Pattern? = null

    private var format: String? = null
    private var optionalValueSeparator: String? = null
    private var color = 0

    private val wifiManager: WifiManager
    private val mBluetoothAdapter: BluetoothAdapter?
    private val connectivityManager: ConnectivityManager

    private var cmClass: Class<*>? = null
    private var method: Method? = null

    private var maxDepth = 0

    init {
        connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager =
            context.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        try {
            cmClass = Class.forName(connectivityManager.javaClass.getName())
            method = cmClass!!.getDeclaredMethod("getMobileDataEnabled")
            method!!.setAccessible(true)
        } catch (e: Exception) {
            cmClass = null
            method = null
        }
    }

    override fun update() {
        if (format == null) {
            format = XMLPrefsManager.get(Behavior.network_info_format)
            color = XMLPrefsManager.getColor(Theme.network_info_text_color)
            maxDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth)

            optionalValueSeparator = XMLPrefsManager.get(Behavior.optional_values_separator)
            val quotedSep = Pattern.quote(optionalValueSeparator)

            val wifiRegex = "%\\(([^" + quotedSep + "]*)" + quotedSep + "([^)]*)\\)"
            val dataRegex = "%\\[([^" + quotedSep + "]*)" + quotedSep + "([^\\]]*)\\]"
            val bluetoothRegex = "%\\{([^" + quotedSep + "]*)" + quotedSep + "([^}]*)\\}"

            optionalWifi = Pattern.compile(wifiRegex, Pattern.CASE_INSENSITIVE)
            optionalBluetooth = Pattern.compile(bluetoothRegex, Pattern.CASE_INSENSITIVE)
            optionalData = Pattern.compile(dataRegex, Pattern.CASE_INSENSITIVE)
        }

        var wifiOn = false
        val wifiInfo = connectivityManager!!.getNetworkInfo(ConnectivityManager.TYPE_WIFI)
        if (wifiInfo != null) {
            wifiOn = wifiInfo.isConnected()
        }

        var wifiName: String? = null
        if (wifiOn) {
            val connectionInfo = wifiManager.getConnectionInfo()
            wifiName = cleanWifiName(connectionInfo.getSSID())
        }

        var mobileOn = false
        try {
            mobileOn =
                method != null && method!!.invoke(connectivityManager) as? Boolean == true
        } catch (e: Exception) {
        }

        var mobileType: String? = null
        if (mobileOn) {
            mobileType = Tuils.getNetworkType(context)
        } else {
            mobileType = "unknown"
        }

        val bluetoothOn = mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()

        var copy = format

        if (maxDepth > 0) {
            copy = apply(
                1,
                copy!!,
                booleanArrayOf(wifiOn, mobileOn, bluetoothOn),
                optionalWifi,
                optionalData,
                optionalBluetooth
            )
            copy = apply(
                1,
                copy,
                booleanArrayOf(mobileOn, wifiOn, bluetoothOn),
                optionalData,
                optionalWifi,
                optionalBluetooth
            )
            copy = apply(
                1,
                copy,
                booleanArrayOf(bluetoothOn, wifiOn, mobileOn),
                optionalBluetooth,
                optionalWifi,
                optionalData
            )
        }

        copy = w0.matcher(copy).replaceAll(if (wifiOn) one else zero)
        copy = w1.matcher(copy).replaceAll(if (wifiOn) on else off)
        copy = w2.matcher(copy).replaceAll(if (wifiOn) ON else OFF)
        copy = w3.matcher(copy).replaceAll(if (wifiOn) _true else _false)
        copy = w4.matcher(copy).replaceAll(if (wifiOn) TRUE else FALSE)
        copy = wn.matcher(copy).replaceAll(
            Matcher.quoteReplacement(wifiName ?: if (wifiOn) "connected" else "null")
        )
        copy = d0.matcher(copy).replaceAll(if (mobileOn) one else zero)
        copy = d1.matcher(copy).replaceAll(if (mobileOn) on else off)
        copy = d2.matcher(copy).replaceAll(if (mobileOn) ON else OFF)
        copy = d3.matcher(copy).replaceAll(if (mobileOn) _true else _false)
        copy = d4.matcher(copy).replaceAll(if (mobileOn) TRUE else FALSE)
        copy = b0.matcher(copy).replaceAll(if (bluetoothOn) one else zero)
        copy = b1.matcher(copy).replaceAll(if (bluetoothOn) on else off)
        copy = b2.matcher(copy).replaceAll(if (bluetoothOn) ON else OFF)
        copy = b3.matcher(copy).replaceAll(if (bluetoothOn) _true else _false)
        copy = b4.matcher(copy).replaceAll(if (bluetoothOn) TRUE else FALSE)
        copy = ip4.matcher(copy).replaceAll(getIPAddress(true))
        copy = ip6.matcher(copy).replaceAll(getIPAddress(false))
        copy = dt.matcher(copy).replaceAll(mobileType)
        copy = Tuils.patternNewline.matcher(copy).replaceAll(Tuils.NEWLINE)

        if (listener != null) {
            listener.onUpdate(UIManager.Label.network, Tuils.span(context, copy, color, size))
        }
    }

    private fun cleanWifiName(rawSsid: String?): String? {
        val ssid = rawSsid
            ?.replace("\"", Tuils.EMPTYSTRING)
            ?.trim()
            ?: return null

        if (ssid.isEmpty() || ssid.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true)) {
            return null
        }

        return ssid
    }

    private fun apply(depth: Int, s: String, on: BooleanArray, vararg ps: Pattern?): String {
        var s = s
        if (ps.size == 0) return s

        val m = ps[0]!!.matcher(s)
        while (m.find()) {
            if (m.groupCount() < 2) {
                s = s.replace(m.group(0), Tuils.EMPTYSTRING)
                continue
            }

            var g1 = m.group(1)
            var g2 = m.group(2)

            if (depth < maxDepth) {
                for (c in 0..<ps.size - 1) {
                    val subOn = BooleanArray(on.size - 1)
                    subOn[0] = on[c + 1]

                    val subPs = arrayOfNulls<Pattern>(ps.size - 1)
                    subPs[0] = ps[c + 1]

                    var j = 1
                    var k = 1
                    while (j < subOn.size) {
                        if (k == c + 1) {
                            j--
                            j++
                            k++
                            continue
                        }

                        subOn[j] = on[k]
                        subPs[j] = ps[k]
                        j++
                        k++
                    }

                    g1 = apply(depth + 1, g1!!, subOn, *subPs)
                    g2 = apply(depth + 1, g2!!, subOn, *subPs)
                }
            }

            s = s.replace(m.group(0), (if (on[0]) g1 else g2)!!)
        }

        return s
    }
}
