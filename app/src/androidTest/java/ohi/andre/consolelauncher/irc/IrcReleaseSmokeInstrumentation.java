package ohi.andre.consolelauncher.irc;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** Platform-only, read-only smoke: works against the exact R8-optimized APK.
 * No app-class references, changed keep rules, password handoff or message sends.
 */
public class IrcReleaseSmokeInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); this.arguments = arguments; start(); }
    interface Check { boolean ok(); }
    private static void require(boolean value, String explanation) { if (!value) throw new AssertionError(explanation); }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 20000;
        while (SystemClock.elapsedRealtime() < end) {
            waitForIdleSync();
            if (check.ok()) return;
            SystemClock.sleep(50);
        }
        require(check.ok(), "Release UI did not reach expected state");
    }
    private String text(String name) {
        final String[] result = {""};
        runOnMainSync(() -> {
            int id = activity.getResources().getIdentifier(name, "id", getTargetContext().getPackageName());
            TextView view = activity.findViewById(id);
            require(view != null, "Release view missing"); result[0] = view.getText().toString();
        });
        return result[0];
    }
    private View find(View view, String label) {
        if (view instanceof TextView && ((TextView) view).getText().toString().replaceFirst("^> ", "").equals(label)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), label); if (found != null) return found; }
        }
        return null;
    }
    private void click(String label) {
        runOnMainSync(() -> {
            View target = find(activity.getWindow().getDecorView(), label);
            while (target != null && !target.isClickable()) target = target.getParent() instanceof View ? (View) target.getParent() : null;
            require(target != null, "Release action missing: " + label); target.performClick();
        });
        waitForIdleSync();
    }
    private void capture(String label) throws Exception {
        waitForIdleSync(); SystemClock.sleep(200);
        Bitmap shot = getUiAutomation().takeScreenshot();
        require(shot != null, "Release screenshot missing");
        try (FileOutputStream file = new FileOutputStream(new File(activity.getExternalFilesDir(null), "irc-release-" + label + ".png"))) {
            require(shot.compress(Bitmap.CompressFormat.PNG, 100, file), "Screenshot save failed");
        } finally { shot.recycle(); }
    }
    private byte[] bytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        }
    }
    private void restore(SharedPreferences prefs, Map<String, ?> original) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            String key = entry.getKey(); Object value = entry.getValue();
            if (value instanceof Integer) editor.putInt(key, (Integer) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Long) editor.putLong(key, (Long) value);
            else if (value instanceof Float) editor.putFloat(key, (Float) value);
            else if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Set) editor.putStringSet(key, (Set<String>) value);
            else throw new AssertionError("Unsupported fixture preference type");
        }
        require(editor.commit() && original.equals(prefs.getAll()), "Fixture preferences not restored");
    }
    private void changelogAutoSmoke(boolean realUpgrade) throws Exception {
        Context context = getTargetContext();
        require(context.getSharedPreferences("retui_startup", Context.MODE_PRIVATE).getBoolean("complete", false),
            "Finish existing startup before testing automatic update notes");
        SharedPreferences prefs = context.getSharedPreferences("changelogPrefs", Context.MODE_PRIVATE);
        SharedPreferences windows = context.getSharedPreferences("terminal_windows", Context.MODE_PRIVATE);
        Map<String, ?> original = prefs.getAll(), originalWindows = windows.getAll();
        int version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        File binding = new File(context.getNoBackupFilesDir(), "irc-device.json");
        byte[] originalBinding = binding.isFile() ? bytes(binding) : null;
        ActivityMonitor monitor = addMonitor("ohi.andre.consolelauncher.help.ChangelogActivity", null, false);
        Activity home = null;
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(new ComponentName(context.getPackageName(), "ohi.andre.consolelauncher.LauncherActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            // Only fixture state changes: Launcher itself must request the notice.
            if (realUpgrade) {
                require(!prefs.contains("last_seen_version_code") || prefs.getInt("last_seen_version_code", -1) < version,
                    "Actual installed upgrade must have an older acknowledgement");
            } else if ("true".equals(arguments.getString("changelog_first_install"))) {
                require(prefs.edit().remove("last_seen_version_code").commit(), "Cannot seed unseen installation");
            } else {
                require(prefs.edit().putInt("last_seen_version_code", version - 1).commit(), "Cannot seed older build");
            }
            home = startActivitySync(homeIntent);
            activity = waitForMonitorWithTimeout(monitor, 10000);
            require(activity != null, "Home did not automatically open update notes");
            await(() -> prefs.getInt("last_seen_version_code", -1) == version);
            require(text("offline_help_output").contains("Build " + version), "Automatic notice has wrong build");
            await(() -> activity.hasWindowFocus());
            capture("changelog-auto");
            int closeId = context.getResources().getIdentifier("changelog_close", "id", context.getPackageName());
            runOnMainSync(() -> activity.findViewById(closeId).performClick());
            await(() -> activity.isFinishing() || activity.isDestroyed());
            Activity firstHome = home;
            await(firstHome::hasWindowFocus);
            require(monitor.getHits() == 1, "Acknowledged notice reopened on return");
            runOnMainSync(firstHome::finish);
            waitForIdleSync();
            home = startActivitySync(homeIntent);
            Activity secondHome = home;
            await(secondHome::hasWindowFocus);
            SystemClock.sleep(1500); waitForIdleSync();
            require(monitor.getHits() == 1, "Acknowledged notice reopened on fresh Home launch");
            require(prefs.getInt("last_seen_version_code", -1) == version, "Acknowledgement was lost");
        } finally {
            if (activity != null && !activity.isFinishing()) runOnMainSync(() -> activity.finish());
            if (home != null) { Activity closing = home; runOnMainSync(closing::finish); }
            waitForIdleSync();
            removeMonitor(monitor);
            try {
                if (!realUpgrade) restore(prefs, original);
                else {
                    Map<String, Object> expected = new java.util.HashMap<>(original);
                    expected.put("last_seen_version_code", version);
                    require(expected.equals(prefs.getAll()), "Unexpected preferences changed during real upgrade");
                }
                require(originalBinding == null ? !binding.exists() : Arrays.equals(originalBinding, bytes(binding)),
                    "Remembered IRC binding changed during changelog test");
            } finally { restore(windows, originalWindows); }
        }
    }
    @Override public void onStart() {
        Bundle result = new Bundle(); int code = Activity.RESULT_CANCELED;
        try {
            if ("true".equals(arguments.getString("changelog_auto_smoke"))) {
                boolean realUpgrade = "true".equals(arguments.getString("changelog_real_upgrade"));
                changelogAutoSmoke(realUpgrade);
                result.putString("stream", "OK (1 optimized-release automatic changelog smoke); " +
                    (realUpgrade ? "actual installed upgrade" : "true".equals(arguments.getString("changelog_first_install")) ? "unseen installation after setup" : "simulated older build") +
                    " Home launch opened once, acknowledgement prevented repeat, window preferences restored, IRC binding unchanged\n");
                code = Activity.RESULT_OK;
                return;
            }
            require("true".equals(arguments.getString("irc_release_smoke")), "Explicit release-smoke opt-in required");
            try {
                getTargetContext().getPackageManager().getActivityInfo(new ComponentName(
                    getTargetContext().getPackageName(), "ohi.andre.consolelauncher.community.CommunityActivity"), 0);
                throw new AssertionError("Retired HTTP Activity still installed");
            } catch (android.content.pm.PackageManager.NameNotFoundException expected) { }
            require(getTargetContext().getResources().getIdentifier("community_title", "string",
                getTargetContext().getPackageName()) == 0, "Retired HTTP resources still packaged");
            try {
                Class.forName("ohi.andre.consolelauncher.commands.main.raw.community", false, getTargetContext().getClassLoader());
                throw new AssertionError("Retired HTTP command still packaged");
            } catch (ClassNotFoundException expected) { }
            require(!android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(),
                "Cleartext exception still enabled");
            File record = new File(getTargetContext().getNoBackupFilesDir(), "irc-device.json");
            byte[] original = bytes(record);
            Intent intent = new Intent().setComponent(new ComponentName(getTargetContext().getPackageName(),
                "ohi.andre.consolelauncher.irc.IrcActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(intent);
            await(() -> text("irc_status").contains("Connected") && text("irc_context").equals("#retui"));
            capture("connected");
            for (String label : new String[]{"Channels", "People", "Help", "More"}) {
                click(label); capture(label.toLowerCase(java.util.Locale.ROOT));
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync();
            }
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> activity.isFinishing() || activity.isDestroyed());
            require(Arrays.equals(original, bytes(record)), "Remembered account metadata changed");
            result.putString("stream", "OK (1 optimized-release IRC-only/navigation/reconnect smoke); remembered binding unchanged\n");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            // Assertion messages are fixed fixture labels; don't dump network errors or credentials.
            result.putString("stream", "Release smoke FAILED: " + error.getClass().getSimpleName() +
                (error instanceof AssertionError ? ": " + error.getMessage() : "") + "\n");
        } finally {
            if (activity != null && !activity.isFinishing()) runOnMainSync(() -> activity.finish());
            finish(code, result);
        }
    }
}
