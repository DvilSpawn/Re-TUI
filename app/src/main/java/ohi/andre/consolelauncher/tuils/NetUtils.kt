@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.tuils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URL
import java.util.Collections
import java.util.Locale

object NetUtils {
    @JvmStatic
    fun hasInternetAccess(): Boolean {
        return try {
            val urlc = URL("https://clients3.google.com/generate_204").openConnection() as HttpURLConnection
            // Offline/DNS stalls used to hang RSS/weather/changelog threads for minutes.
            urlc.connectTimeout = 2_000
            urlc.readTimeout = 2_000
            urlc.instanceFollowRedirects = false
            urlc.useCaches = false
            urlc.responseCode == 204
        } catch (e: IOException) {
            false
        }
    }

    @JvmStatic
    fun getNetworkType(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return "unknown"
        }
        return try {
            val mTelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            when (mTelephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN -> "2g"
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP -> "3g"
                TelephonyManager.NETWORK_TYPE_LTE -> "4g"
                TelephonyManager.NETWORK_TYPE_NR -> "5g"
                else -> "unknown"
            }
        } catch (e: SecurityException) {
            "unknown"
        }
    }

    @JvmStatic
    fun getIPAddress(useIPv4: Boolean): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr: InetAddress in addrs) {
                    if (!addr.isLoopbackAddress) {
                        val value = addr.hostAddress ?: continue
                        val isIPv4 = value.indexOf(':') < 0

                        if (useIPv4 && isIPv4) {
                            return value
                        }
                        if (!useIPv4 && !isIPv4) {
                            val delimiter = value.indexOf('%')
                            return if (delimiter < 0) {
                                value.uppercase(Locale.getDefault())
                            } else {
                                value.substring(0, delimiter).uppercase(Locale.getDefault())
                            }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        return ""
    }
}
