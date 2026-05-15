package ohi.andre.consolelauncher.managers.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.UIUtils;
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate;

public class BatteryManager implements OnBatteryUpdate {

    private final Context context;
    private final StatusUpdateListener listener;
    private final int size;
    private final int mediumPercentage;
    private final int lowPercentage;

    private Pattern optionalCharging;
    private final Pattern value = Pattern.compile("%v", Pattern.LITERAL | Pattern.CASE_INSENSITIVE);

    private boolean manyStatus, loaded;
    private int colorHigh, colorMedium, colorLow;
    private String batteryFormat;

    private boolean charging;
    private float lastPercentage = -1;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL;

            int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            float percentage = level * 100 / (float) scale;
            update(percentage);
        }
    };

    public BatteryManager(Context context, int size, int mediumPercentage, int lowPercentage, StatusUpdateListener listener) {
        this.context = context;
        this.size = size;
        this.mediumPercentage = mediumPercentage;
        this.lowPercentage = lowPercentage;
        this.listener = listener;
    }

    public void start() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        ContextCompat.registerReceiver(context, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    public void stop() {
        try {
            context.unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
    }

    @Override
    public void update(float p) {
        if (p == -1) p = lastPercentage;
        lastPercentage = p;

        if (batteryFormat == null) {
            batteryFormat = XMLPrefsManager.get(Behavior.battery_format);

            String sep = XMLPrefsManager.get(Behavior.optional_values_separator);
            String quotedSep = Pattern.quote(sep);
            String optional = "%\\(([^" + quotedSep + "]*)" + quotedSep + "([^)]*)\\)";
            optionalCharging = Pattern.compile(optional, Pattern.CASE_INSENSITIVE);
        }

        if (!loaded) {
            loaded = true;
            manyStatus = XMLPrefsManager.getBoolean(Ui.enable_battery_status);
            colorHigh = XMLPrefsManager.getColor(Theme.battery_color_high);
            colorMedium = XMLPrefsManager.getColor(Theme.battery_color_medium);
            colorLow = XMLPrefsManager.getColor(Theme.battery_color_low);
        }

        int percentage = (int) p;

        if (XMLPrefsManager.getBoolean(Behavior.battery_progress_bar)) {
            int length = XMLPrefsManager.getInt(Behavior.battery_progress_bar_length);
            String symbol = XMLPrefsManager.get(Behavior.battery_progress_bar_symbol);
            int fullColor = XMLPrefsManager.getColor(Theme.battery_progress_bar_full_color);
            int emptyColor = XMLPrefsManager.getColor(Theme.battery_progress_bar_empty_color);

            int fullCount = Math.round((p / 100f) * length);
            int emptyCount = length - fullCount;

            StringBuilder fullPart = new StringBuilder();
            for (int i = 0; i < fullCount; i++) fullPart.append(symbol);

            StringBuilder emptyPart = new StringBuilder();
            for (int i = 0; i < emptyCount; i++) emptyPart.append(symbol);

            SpannableStringBuilder ssb = new SpannableStringBuilder();
            ssb.append("[");
            if (fullPart.length() > 0) {
                int start = ssb.length();
                ssb.append(fullPart);
                ssb.setSpan(new ForegroundColorSpan(fullColor), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            if (emptyPart.length() > 0) {
                int start = ssb.length();
                ssb.append(emptyPart);
                ssb.setSpan(new ForegroundColorSpan(emptyColor), start, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ssb.append("] ").append(String.valueOf(percentage)).append("%");

            if (listener != null) {
                listener.onUpdate(UIManager.Label.battery, ssb);
            }
            return;
        }

        int color;
        if (manyStatus) {
            if (percentage > mediumPercentage) color = colorHigh;
            else if (percentage > lowPercentage) color = colorMedium;
            else color = colorLow;
        } else {
            color = colorHigh;
        }

        String cp = batteryFormat;
        Matcher m = optionalCharging.matcher(cp);
        while (m.find()) {
            cp = cp.replace(m.group(0), m.groupCount() == 2 ? m.group(charging ? 1 : 2) : Tuils.EMPTYSTRING);
        }

        cp = value.matcher(cp).replaceAll(String.valueOf(percentage));
        cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE);

        if (listener != null) {
            listener.onUpdate(UIManager.Label.battery, UIUtils.span(context, cp, color, size));
        }
    }

    @Override
    public void onCharging() {
        charging = true;
        update(-1);
    }

    @Override
    public void onNotCharging() {
        charging = false;
        update(-1);
    }
}
