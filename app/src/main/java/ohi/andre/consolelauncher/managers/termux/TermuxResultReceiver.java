package ohi.andre.consolelauncher.managers.termux;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.UIManager;

public class TermuxResultReceiver extends BroadcastReceiver {

    private static final String EXTRA_PLUGIN_RESULT_BUNDLE = "result";
    private static final String EXTRA_RUN_COMMAND_RESULT = "com.termux.RUN_COMMAND_RESULT";
    private static final String EXTRA_RUN_COMMAND_RESULT_BUNDLE = "com.termux.RUN_COMMAND_RESULT_BUNDLE";
    private static final String BUNDLE_STDOUT = "stdout";
    private static final String BUNDLE_STDERR = "stderr";
    private static final String BUNDLE_EXIT_CODE = "exitCode";
    private static final String BUNDLE_EXIT_CODE_ALT = "exit_code";
    private static final String BUNDLE_ERR = "err";
    private static final String BUNDLE_ERRMSG = "errmsg";

    @Override
    public void onReceive(Context context, Intent intent) {
        forwardResult(context, intent);
    }

    public static void forwardResult(Context context, Intent intent) {
        Intent result = new Intent(UIManager.ACTION_TERMUX_RESULT);
        if (intent != null) {
            result.putExtra(UIManager.EXTRA_TERMUX_RESULT_PATH,
                    intent.getStringExtra(UIManager.EXTRA_TERMUX_RESULT_PATH));

            Bundle bundle = findResultBundle(intent);
            if (bundle != null) {
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, bundle.getString(BUNDLE_STDOUT));
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR, bundle.getString(BUNDLE_STDERR));
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE,
                        bundle.containsKey(BUNDLE_EXIT_CODE)
                                ? bundle.getInt(BUNDLE_EXIT_CODE, Integer.MIN_VALUE)
                                : bundle.getInt(BUNDLE_EXIT_CODE_ALT, Integer.MIN_VALUE));

                String error = bundle.getString(BUNDLE_ERRMSG);
                if (error == null) {
                    error = bundle.getString(BUNDLE_ERR);
                }
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, error);
            } else {
                copyDirectExtras(intent, result);
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, "Termux returned no result bundle.");
                result.putExtra(UIManager.EXTRA_TERMUX_RESULT_DEBUG, describeExtras(intent.getExtras()));
            }
        }

        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(result);
    }

    private static Bundle findResultBundle(Intent intent) {
        Bundle bundle = intent.getBundleExtra(EXTRA_PLUGIN_RESULT_BUNDLE);
        if (bundle != null) {
            return bundle;
        }

        bundle = intent.getBundleExtra(EXTRA_RUN_COMMAND_RESULT);
        if (bundle != null) {
            return bundle;
        }

        bundle = intent.getBundleExtra(EXTRA_RUN_COMMAND_RESULT_BUNDLE);
        if (bundle != null) {
            return bundle;
        }

        Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }

        for (String key : extras.keySet()) {
            Object value = extras.get(key);
            if (value instanceof Bundle) {
                return (Bundle) value;
            }
        }

        return null;
    }

    private static void copyDirectExtras(Intent source, Intent result) {
        result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDOUT, source.getStringExtra(BUNDLE_STDOUT));
        result.putExtra(UIManager.EXTRA_TERMUX_RESULT_STDERR, source.getStringExtra(BUNDLE_STDERR));
        result.putExtra(UIManager.EXTRA_TERMUX_RESULT_EXIT_CODE,
                source.hasExtra(BUNDLE_EXIT_CODE)
                        ? source.getIntExtra(BUNDLE_EXIT_CODE, Integer.MIN_VALUE)
                        : source.getIntExtra(BUNDLE_EXIT_CODE_ALT, Integer.MIN_VALUE));

        String error = source.getStringExtra(BUNDLE_ERRMSG);
        if (error == null) {
            error = source.getStringExtra(BUNDLE_ERR);
        }
        result.putExtra(UIManager.EXTRA_TERMUX_RESULT_ERROR, error);
    }

    private static String describeExtras(Bundle extras) {
        if (extras == null || extras.isEmpty()) {
            return "extras=<none>";
        }

        StringBuilder builder = new StringBuilder("extras=");
        boolean first = true;
        for (String key : extras.keySet()) {
            if (!first) {
                builder.append(", ");
            }
            Object value = extras.get(key);
            builder.append(key).append("(")
                    .append(value == null ? "null" : value.getClass().getSimpleName())
                    .append(")");
            first = false;
        }
        return builder.toString();
    }
}
