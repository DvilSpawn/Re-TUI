package ohi.andre.consolelauncher.managers.status

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils.span
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate
import java.util.regex.Pattern
import java.util.regex.Matcher
import ohi.andre.consolelauncher.tuils.UIUtils

class BatteryManager(
    context: Context,
    private var size: Int,
    private val mediumPercentage: Int,
    private val lowPercentage: Int,
    private val listener: StatusUpdateListener?
) : OnBatteryUpdate {
    private val context: Context

    private var charging = false
    private var lastPercentage = -1f
    private var registered = false

    private val batteryReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percentage = level * 100 / scale.coerceAtLeast(1).toFloat()
            update(percentage)
        }
    }

    init {
        this.context = context.getApplicationContext()
    }

    fun refreshAppearance() {
        size = XMLPrefsManager.getInt(Ui.battery_size)
        if (lastPercentage >= 0) update(lastPercentage)
    }

    fun start() {
        if (registered) {
            return
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun stop() {
        if (!registered) {
            return
        }
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }
        registered = false
    }

    override fun update(p: Float) {
        var p = p
        if (p == -1f) p = lastPercentage
        lastPercentage = p

        listener?.onUpdate(UIManager.Label.battery,
            ohi.andre.consolelauncher.presentation.BatteryPresentation.render(context, p, charging, size,
                mediumPercentage, lowPercentage, ohi.andre.consolelauncher.presentation.HomeAppearance.saved()))
    }

    override fun onCharging() {
        charging = true
        update(-1f)
    }

    override fun onNotCharging() {
        charging = false
        update(-1f)
    }
}
