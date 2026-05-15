package ohi.andre.consolelauncher.managers.status;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.text.TextUtils;
import androidx.core.content.ContextCompat;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.Tuils;

public class UnlockManager extends StatusManager {

    public static final String UNLOCK_KEY = "unlockTimes";
    public static final String NEXT_UNLOCK_CYCLE_RESTART = "nextUnlockRestart";
    private static final long A_DAY = (1000 * 60 * 60 * 24);
    private static final String PREFS_NAME = "ui";

    private final StatusUpdateListener listener;
    private final int size;
    private final SharedPreferences preferences;

    private int unlockTimes, unlockHour, unlockMinute;
    private final int cycleDuration = (int) A_DAY;
    private long lastUnlockTime = -1, nextUnlockCycleRestart;
    private String unlockFormat, notAvailableText, unlockTimeDivider;
    private int unlockColor, unlockTimeOrder;
    private long[] lastUnlocks;

    private final Pattern unlockCount = Pattern.compile("%c", Pattern.CASE_INSENSITIVE);
    private final Pattern advancement = Pattern.compile("%a(\\d+)(.)");
    private final Pattern timePattern = Pattern.compile("(%t\\d*)(?:\\(([^\\)]*)\\))?(\\d+)?");
    private final Pattern indexPattern = Pattern.compile("%i", Pattern.CASE_INSENSITIVE);
    private final String whenPattern = "%w";

    private BroadcastReceiver lockReceiver;

    public UnlockManager(Context context, int size, StatusUpdateListener listener) {
        super(context, 0); // delay is handled internally by runnable/receiver
        this.size = size;
        this.listener = listener;
        this.preferences = context.getSharedPreferences(PREFS_NAME, 0);

        init();
    }

    private void init() {
        unlockTimes = preferences.getInt(UNLOCK_KEY, 0);
        unlockColor = XMLPrefsManager.getColor(Theme.unlock_counter_color);
        unlockFormat = XMLPrefsManager.get(Behavior.unlock_counter_format);
        notAvailableText = XMLPrefsManager.get(Behavior.not_available_text);
        unlockTimeDivider = XMLPrefsManager.get(Behavior.unlock_time_divider);
        unlockTimeDivider = Tuils.patternNewline.matcher(unlockTimeDivider).replaceAll(Tuils.NEWLINE);

        String start = XMLPrefsManager.get(Behavior.unlock_counter_cycle_start);
        Pattern p = Pattern.compile("(\\d{1,2}).(\\d{1,2})");
        Matcher m = p.matcher(start);
        if (!m.find()) {
            m = p.matcher(Behavior.unlock_counter_cycle_start.defaultValue());
            m.find();
        }

        unlockHour = Integer.parseInt(m.group(1));
        unlockMinute = Integer.parseInt(m.group(2));
        unlockTimeOrder = XMLPrefsManager.getInt(Behavior.unlock_time_order);
        nextUnlockCycleRestart = preferences.getLong(NEXT_UNLOCK_CYCLE_RESTART, 0);

        m = timePattern.matcher(unlockFormat);
        if (m.find()) {
            String s = m.group(3);
            if (s == null || s.length() == 0) s = "1";
            lastUnlocks = new long[Integer.parseInt(s)];
            for (int c = 0; c < lastUnlocks.length; c++) {
                lastUnlocks[c] = -1;
            }
        } else {
            lastUnlocks = null;
        }
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        registerLockReceiver();
        handler.post(unlockTimeRunnable);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        unregisterLockReceiver();
        handler.removeCallbacks(unlockTimeRunnable);
    }

    @Override
    protected void update() {
        invalidateUnlockText();
    }

    private void registerLockReceiver() {
        if (lockReceiver != null) return;
        final IntentFilter theFilter = new IntentFilter();
        theFilter.addAction(Intent.ACTION_SCREEN_ON);
        theFilter.addAction(Intent.ACTION_SCREEN_OFF);
        theFilter.addAction(Intent.ACTION_USER_PRESENT);

        lockReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String strAction = intent.getAction();
                KeyguardManager myKM = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
                if (Intent.ACTION_USER_PRESENT.equals(strAction) || Intent.ACTION_SCREEN_OFF.equals(strAction) || Intent.ACTION_SCREEN_ON.equals(strAction)) {
                    if (myKM.inKeyguardRestrictedInputMode()) {
                        // onLock handled by UIManager if needed for clearing terminal
                    } else {
                        onUnlock();
                    }
                }
            }
        };
        ContextCompat.registerReceiver(context.getApplicationContext(), lockReceiver, theFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterLockReceiver() {
        if (lockReceiver != null) {
            context.getApplicationContext().unregisterReceiver(lockReceiver);
            lockReceiver = null;
        }
    }

    private void onUnlock() {
        if (System.currentTimeMillis() - lastUnlockTime < 1000 || lastUnlocks == null) return;
        lastUnlockTime = System.currentTimeMillis();

        unlockTimes++;
        System.arraycopy(lastUnlocks, 0, lastUnlocks, 1, lastUnlocks.length - 1);
        lastUnlocks[0] = lastUnlockTime;

        preferences.edit().putInt(UNLOCK_KEY, unlockTimes).apply();
        invalidateUnlockText();
    }

    private final Runnable unlockTimeRunnable = new Runnable() {
        @Override
        public void run() {
            long delay = nextUnlockCycleRestart - System.currentTimeMillis();
            if (delay <= 0) {
                unlockTimes = 0;
                if (lastUnlocks != null) {
                    for (int c = 0; c < lastUnlocks.length; c++) lastUnlocks[c] = -1;
                }

                Calendar now = Calendar.getInstance();
                int hour = now.get(Calendar.HOUR_OF_DAY), minute = now.get(Calendar.MINUTE);
                if (unlockHour < hour || (unlockHour == hour && unlockMinute <= minute)) {
                    now.add(Calendar.DAY_OF_YEAR, 1);
                }
                now.set(Calendar.HOUR_OF_DAY, unlockHour);
                now.set(Calendar.MINUTE, unlockMinute);
                now.set(Calendar.SECOND, 0);

                nextUnlockCycleRestart = now.getTimeInMillis();
                preferences.edit()
                        .putLong(NEXT_UNLOCK_CYCLE_RESTART, nextUnlockCycleRestart)
                        .putInt(UNLOCK_KEY, 0)
                        .apply();

                delay = nextUnlockCycleRestart - System.currentTimeMillis();
                if (delay < 0) delay = 0;
            }

            invalidateUnlockText();
            delay = Math.min(delay, cycleDuration / 24);
            handler.postDelayed(this, delay);
        }
    };

    private void invalidateUnlockText() {
        String cp = unlockFormat;
        cp = unlockCount.matcher(cp).replaceAll(String.valueOf(unlockTimes));
        cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE);

        Matcher m = advancement.matcher(cp);
        if (m.find()) {
            int denominator = Integer.parseInt(m.group(1));
            String divider = m.group(2);
            long lastCycleStart = nextUnlockCycleRestart - cycleDuration;
            int elapsed = (int) (System.currentTimeMillis() - lastCycleStart);
            int numerator = denominator * elapsed / cycleDuration;
            cp = m.replaceAll(numerator + divider + denominator);
        }

        CharSequence s = Tuils.span(context, size, cp);
        s = Tuils.span(context, s, unlockColor, size);

        Matcher timeMatcher = timePattern.matcher(cp);
        if (timeMatcher.find()) {
            String timeGroup = timeMatcher.group(1);
            String text = timeMatcher.group(2);
            if (text == null) text = whenPattern;

            CharSequence cs = Tuils.EMPTYSTRING;
            int c, change;
            if (unlockTimeOrder == 1) { // UP_DOWN
                c = 0;
                change = 1;
            } else {
                c = lastUnlocks.length - 1;
                change = -1;
            }

            for (int counter = 0; counter < lastUnlocks.length; counter++, c += change) {
                String t = text;
                t = indexPattern.matcher(t).replaceAll(String.valueOf(c + 1));
                cs = TextUtils.concat(cs, t);

                CharSequence time;
                if (lastUnlocks[c] > 0) {
                    time = TimeManager.instance.getCharSequence(timeGroup, lastUnlocks[c]);
                } else {
                    time = notAvailableText;
                }

                if (time == null) continue;
                cs = TextUtils.replace(cs, new String[]{whenPattern}, new CharSequence[]{time});
                if (counter != lastUnlocks.length - 1) cs = TextUtils.concat(cs, unlockTimeDivider);
            }
            s = TextUtils.replace(s, new String[]{timeMatcher.group(0)}, new CharSequence[]{cs});
        }

        if (listener != null) {
            listener.onUpdate(UIManager.Label.unlock, s);
        }
    }
}
