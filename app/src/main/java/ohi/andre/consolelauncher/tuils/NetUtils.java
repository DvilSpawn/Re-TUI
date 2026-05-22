package ohi.andre.consolelauncher.tuils;

import android.content.Context;
import android.telephony.TelephonyManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class NetUtils {

    public static boolean hasInternetAccess() {
        try {
            HttpURLConnection urlc = (HttpURLConnection) (new URL("https://clients3.google.com/generate_204").openConnection());
            return (urlc.getResponseCode() == 204 && urlc.getContentLength() == 0);
        } catch (IOException e) {
            return false;
        }
    }

    public static String getNetworkType(Context context) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return "unknown";
        }
        try {
            TelephonyManager mTelephonyManager = (TelephonyManager)
                    context.getSystemService(Context.TELEPHONY_SERVICE);
            int networkType = mTelephonyManager.getNetworkType();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return "2g";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return "3g";
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return "4g";
                case TelephonyManager.NETWORK_TYPE_NR:
                    return "5g";
                default:
                    return "unknown";
            }
        } catch (SecurityException e) {
            return "unknown";
        }
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String value = addr.getHostAddress();
                        boolean isIPv4 = value.indexOf(':') < 0;

                        if (useIPv4 && isIPv4) {
                            return value;
                        }
                        if (!useIPv4 && !isIPv4) {
                            int delimiter = value.indexOf('%');
                            return delimiter < 0
                                    ? value.toUpperCase()
                                    : value.substring(0, delimiter).toUpperCase();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
