package ohi.andre.consolelauncher.managers.callback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.modules.ModuleManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class RetuiCallbackReceiver extends BroadcastReceiver {

    public static final String ACTION_CALLBACK = BuildConfig.APPLICATION_ID + ".RETUI_CALLBACK";
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MODULE = "module";

    private static final String TAG = "TUI-Callback";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_CALLBACK.equals(intent.getAction())) {
            return;
        }

        String token = intent.getStringExtra(EXTRA_TOKEN);
        if (!CallbackAuthManager.isAuthorized(context, token)) {
            Log.w(TAG, "Rejected callback without valid token");
            return;
        }

        String action = lower(intent.getStringExtra(EXTRA_ACTION));
        if ("output".equals(action)) {
            output(context, "[callback] " + safe(intent.getStringExtra(EXTRA_TEXT)));
        } else if ("notify".equals(action)) {
            String title = safe(intent.getStringExtra(EXTRA_TITLE));
            String text = safe(intent.getStringExtra(EXTRA_TEXT));
            output(context, "[callback notify] " + joinTitle(title, text));
        } else if ("module_set".equals(action) || "module".equals(action)) {
            String module = safe(intent.getStringExtra(EXTRA_MODULE));
            String text = safe(intent.getStringExtra(EXTRA_TEXT));
            ModuleManager.setScriptText(context, module, text);
            Intent update = new Intent(UIManager.ACTION_MODULE_COMMAND);
            update.putExtra(UIManager.EXTRA_MODULE_COMMAND, "update");
            update.putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.normalize(module));
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(update);
            output(context, "[callback module] " + joinTitle(module, text));
        } else {
            Log.w(TAG, "Unsupported callback action: " + action);
            output(context, "[callback] unsupported action: " + safe(action));
        }
    }

    private static void output(Context context, String text) {
        Tuils.sendOutput(context.getApplicationContext(), text);
    }

    private static String joinTitle(String title, String text) {
        if (title.isEmpty()) {
            return text;
        }
        if (text.isEmpty()) {
            return title;
        }
        return title + ": " + text;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
