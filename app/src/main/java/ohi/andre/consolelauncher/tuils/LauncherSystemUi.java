package ohi.andre.consolelauncher.tuils;

import android.app.Activity;
import android.app.Dialog;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Ui;

public final class LauncherSystemUi {

    private LauncherSystemUi() {}

    public static boolean fullscreenEnabled() {
        return LauncherSettings.getBoolean(Ui.fullscreen);
    }

    public static void requestNoTitleIfFullscreen(Activity activity) {
        if (activity != null && fullscreenEnabled()) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE);
        }
    }

    public static void applyFullscreen(Activity activity) {
        if (activity == null || !fullscreenEnabled()) {
            return;
        }
        hideStatusBar(activity.getWindow());
    }

    public static void applyFullscreen(Dialog dialog) {
        if (dialog == null || !fullscreenEnabled()) {
            return;
        }
        hideStatusBar(dialog.getWindow());
    }

    private static void hideStatusBar(Window window) {
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            View decorView = window.getDecorView();
            decorView.post(() -> {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            });
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            View decorView = window.getDecorView();
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
}
