package ohi.andre.consolelauncher.managers.status;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.NetUtils;
import ohi.andre.consolelauncher.tuils.Tuils;

public class NetworkManager extends StatusManager {

    private final String zero = "0";
    private final String one = "1";
    private final String on = "on";
    private final String off = "off";
    private final String ON = on.toUpperCase();
    private final String OFF = off.toUpperCase();
    private final String _true = "true";
    private final String _false = "false";
    private final String TRUE = _true.toUpperCase();
    private final String FALSE = _false.toUpperCase();

    private final Pattern w0 = Pattern.compile("%w0", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern w1 = Pattern.compile("%w1", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern w2 = Pattern.compile("%w2", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern w3 = Pattern.compile("%w3", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern w4 = Pattern.compile("%w4", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern wn = Pattern.compile("%wn", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern d0 = Pattern.compile("%d0", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern d1 = Pattern.compile("%d1", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern d2 = Pattern.compile("%d2", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern d3 = Pattern.compile("%d3", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern d4 = Pattern.compile("%d4", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern b0 = Pattern.compile("%b0", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern b1 = Pattern.compile("%b1", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern b2 = Pattern.compile("%b2", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern b3 = Pattern.compile("%b3", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern b4 = Pattern.compile("%b4", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern ip4 = Pattern.compile("%ip4", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern ip6 = Pattern.compile("%ip6", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
    private final Pattern dt = Pattern.compile("%dt", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);

    private Pattern optionalWifi, optionalData, optionalBluetooth;

    private String format, optionalValueSeparator;
    private int color;
    private int size;
    private final StatusUpdateListener listener;

    private WifiManager wifiManager;
    private BluetoothAdapter mBluetoothAdapter;
    private ConnectivityManager connectivityManager;

    private Class cmClass;
    private Method method;

    private int maxDepth;

    public NetworkManager(Context context, long delay, int size, StatusUpdateListener listener) {
        super(context, delay);
        this.size = size;
        this.listener = listener;

        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        try {
            cmClass = Class.forName(connectivityManager.getClass().getName());
            method = cmClass.getDeclaredMethod("getMobileDataEnabled");
            method.setAccessible(true);
        } catch (Exception e) {
            cmClass = null;
            method = null;
        }
    }

    @Override
    protected void update() {
        if (format == null) {
            format = XMLPrefsManager.get(Behavior.network_info_format);
            color = XMLPrefsManager.getColor(Theme.network_info_color);
            maxDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth);

            optionalValueSeparator = XMLPrefsManager.get(Behavior.optional_values_separator);
            String quotedSep = Pattern.quote(optionalValueSeparator);

            String wifiRegex = "%\\(([^" + quotedSep + "]*)" + quotedSep + "([^)]*)\\)";
            String dataRegex = "%\\[([^" + quotedSep + "]*)" + quotedSep + "([^\\]]*)\\]";
            String bluetoothRegex = "%\\{([^" + quotedSep + "]*)" + quotedSep + "([^}]*)\\}";

            optionalWifi = Pattern.compile(wifiRegex, Pattern.CASE_INSENSITIVE);
            optionalBluetooth = Pattern.compile(bluetoothRegex, Pattern.CASE_INSENSITIVE);
            optionalData = Pattern.compile(dataRegex, Pattern.CASE_INSENSITIVE);
        }

        boolean wifiOn = false;
        NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (wifiInfo != null) {
            wifiOn = wifiInfo.isConnected();
        }

        String wifiName = null;
        if (wifiOn) {
            WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                wifiName = connectionInfo.getSSID();
            }
        }

        boolean mobileOn = false;
        try {
            mobileOn = method != null && connectivityManager != null && (Boolean) method.invoke(connectivityManager);
        } catch (Exception e) {
        }

        String mobileType = null;
        if (mobileOn) {
            mobileType = Tuils.getNetworkType(context);
        } else {
            mobileType = "unknown";
        }

        boolean bluetoothOn = mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();

        String copy = format;

        if (maxDepth > 0) {
            copy = apply(1, copy, new boolean[]{wifiOn, mobileOn, bluetoothOn}, optionalWifi, optionalData, optionalBluetooth);
            copy = apply(1, copy, new boolean[]{mobileOn, wifiOn, bluetoothOn}, optionalData, optionalWifi, optionalBluetooth);
            copy = apply(1, copy, new boolean[]{bluetoothOn, wifiOn, mobileOn}, optionalBluetooth, optionalWifi, optionalData);
        }

        copy = w0.matcher(copy).replaceAll(wifiOn ? one : zero);
        copy = w1.matcher(copy).replaceAll(wifiOn ? on : off);
        copy = w2.matcher(copy).replaceAll(wifiOn ? ON : OFF);
        copy = w3.matcher(copy).replaceAll(wifiOn ? _true : _false);
        copy = w4.matcher(copy).replaceAll(wifiOn ? TRUE : FALSE);
        copy = wn.matcher(copy).replaceAll(wifiName != null ? wifiName.replaceAll("\"", Tuils.EMPTYSTRING) : "null");
        copy = d0.matcher(copy).replaceAll(mobileOn ? one : zero);
        copy = d1.matcher(copy).replaceAll(mobileOn ? on : off);
        copy = d2.matcher(copy).replaceAll(mobileOn ? ON : OFF);
        copy = d3.matcher(copy).replaceAll(mobileOn ? _true : _false);
        copy = d4.matcher(copy).replaceAll(mobileOn ? TRUE : FALSE);
        copy = b0.matcher(copy).replaceAll(bluetoothOn ? one : zero);
        copy = b1.matcher(copy).replaceAll(bluetoothOn ? on : off);
        copy = b2.matcher(copy).replaceAll(bluetoothOn ? ON : OFF);
        copy = b3.matcher(copy).replaceAll(bluetoothOn ? _true : _false);
        copy = b4.matcher(copy).replaceAll(bluetoothOn ? TRUE : FALSE);
        copy = ip4.matcher(copy).replaceAll(NetUtils.getIPAddress(true));
        copy = ip6.matcher(copy).replaceAll(NetUtils.getIPAddress(false));
        copy = dt.matcher(copy).replaceAll(mobileType);
        copy = Tuils.patternNewline.matcher(copy).replaceAll(Tuils.NEWLINE);

        if (listener != null) {
            listener.onUpdate(UIManager.Label.network, Tuils.span(context, copy, color, size));
        }
    }

    private String apply(int depth, String s, boolean[] on, Pattern... ps) {

        if(ps.length == 0) return s;

        Matcher m = ps[0].matcher(s);
        while (m.find()) {
            if(m.groupCount() < 2) {
                s = s.replace(m.group(0), Tuils.EMPTYSTRING);
                continue;
            }

            String g1 = m.group(1);
            String g2 = m.group(2);

            if(depth < maxDepth) {
                for(int c = 0; c < ps.length - 1; c++) {

                    boolean[] subOn = new boolean[on.length - 1];
                    subOn[0] = on[c+1];

                    Pattern[] subPs = new Pattern[ps.length - 1];
                    subPs[0] = ps[c+1];

                    for(int j = 1, k = 1; j < subOn.length; j++, k++) {
                        if(k == c+1) {
                            j--;
                            continue;
                        }

                        subOn[j] = on[k];
                        subPs[j] = ps[k];
                    }

                    g1 = apply(depth + 1, g1, subOn, subPs);
                    g2 = apply(depth + 1, g2, subOn, subPs);
                }
            }

            s = s.replace(m.group(0), on[0] ? g1 : g2);
        }

        return s;
    }
}
