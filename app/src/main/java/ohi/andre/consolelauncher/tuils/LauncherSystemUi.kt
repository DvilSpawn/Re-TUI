@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.tuils

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Ui

object LauncherSystemUi {
    @JvmStatic
    fun fullscreenEnabled(): Boolean = LauncherSettings.getBoolean(Ui.fullscreen)

    @JvmStatic
    fun requestNoTitleIfFullscreen(activity: Activity?) {
        if (activity != null && fullscreenEnabled()) {
            activity.requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    @JvmStatic
    fun applyFullscreen(activity: Activity?) {
        if (activity == null || !fullscreenEnabled()) {
            return
        }
        hideStatusBar(activity.window)
    }

    @JvmStatic
    fun applyFullscreen(dialog: Dialog?) {
        if (dialog == null || !fullscreenEnabled()) {
            return
        }
        hideStatusBar(dialog.window)
    }

    private fun hideStatusBar(window: Window?) {
        if (window == null) {
            return
        }
        window.statusBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val decorView = window.decorView
            decorView.post {
                val controller = window.insetsController
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        } else {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            val decorView = window.decorView
            decorView.systemUiVisibility = decorView.systemUiVisibility or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
