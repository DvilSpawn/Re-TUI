package ohi.andre.consolelauncher.calculator

import android.content.Context
import android.content.Intent
import android.os.Build
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior

/** Engine data belongs to this launcher; no companion application or signing gate. */
object OptionalCalculatorEngine {
    enum class State { READY, MISSING, UNSUPPORTED }
    fun state(context: Context): State = when {
        Build.VERSION.SDK_INT < 24 -> State.UNSUPPORTED
        CalculatorPackStore.activeDirectory(context) == null -> State.MISSING
        !WasmCalculatorRuntime.available(context) -> State.UNSUPPORTED
        else -> State.READY
    }
    fun available(context: Context) = state(context) == State.READY
    fun statusText(context: Context): String = context.getString(when (state(context)) {
        State.READY -> if (LauncherSettings.getBoolean(Behavior.qalculate)) R.string.optional_calculator_status_enhanced
            else R.string.optional_calculator_status_installed
        State.MISSING -> R.string.optional_calculator_status_basic
        State.UNSUPPORTED -> R.string.optional_calculator_status_unsupported
    })
    fun showManagement(context: Context, onChanged: (() -> Unit)? = null) {
        context.startActivity(Intent(context, CalculatorPackActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        // The calculator refreshes its status when management closes and onResume runs.
        onChanged?.invoke()
    }
}
