package ohi.andre.consolelauncher.managers.status

import ohi.andre.consolelauncher.tuils.Tuils
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.settings.LauncherAppearanceState
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui

/** Polls the same formatters as the launcher once. Does not start services, receivers, or commands. */
class LiveStatusText(private val context: Context, private val values: AppearanceValues) {
    val text = mutableMapOf<UIManager.Label, CharSequence>()
    private val listener = object : StatusUpdateListener {
        override fun onUpdate(label: UIManager.Label?, text: CharSequence?) { if (label != null && text != null) this@LiveStatusText.text[label] = text }
    }
    val ram = RamManager(context, 0, values.int(Ui.ram_size), listener, values)
    val storage = StorageManager(context, 0, values.int(Ui.storage_size), listener, values)
    val network = NetworkManager(context, 0, values.int(Ui.network_size), listener, values)
    private val clock = ohi.andre.consolelauncher.managers.TimeManager(context, values, standalone = true)
    private val unlock = UnlockManager(context, values.int(Ui.unlock_size), listener, values, clock)
    val unlockCount get() = unlock.count()

    fun refresh(snapshot: LauncherAppearanceState) {
        text.putAll(snapshot.labels)
        snapshot.labels[UIManager.Label.weather]?.let {
            text[UIManager.Label.weather] = values.span(context, it, ohi.andre.consolelauncher.managers.xml.options.Theme.weather_text_color, values.int(Ui.weather_size))
        }
        if (values.bool(Ui.show_ram)) ram.refreshNow()
        if (values.bool(Ui.show_storage_info)) storage.refreshNow()
        if (values.bool(Ui.show_network_info)) network.refreshNow()
        text[UIManager.Label.device] = DeviceTextRenderer.render(context, values)
        text[UIManager.Label.time] = clock.getCharSequence(context, values.int(Ui.time_size), "%t") ?: ""
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery != null) {
            val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            text[UIManager.Label.battery] = BatteryTextRenderer.render(context, level * 100f / scale,
                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
                values.int(Ui.battery_size), values.int(Behavior.battery_medium), values.int(Behavior.battery_low), values)
        }
        if (!text.containsKey(UIManager.Label.ascii) && values.bool(Ui.show_ascii)) {
            text[UIManager.Label.ascii] = AsciiAnimationManager(1000, values.color(ohi.andre.consolelauncher.managers.xml.options.Theme.ascii_text_color), null)
                .load(java.io.File(Tuils.getFolder(), "ascii.txt"), values.bool(Behavior.ascii_animation))
        }
        if (values.bool(Ui.show_unlock_counter)) unlock.refreshNow(snapshot.unlock)
        if (values.bool(Ui.show_notes)) {
            val notes = ohi.andre.consolelauncher.managers.NotesManager(context, null, values, readOnly = true)
            text[UIManager.Label.notes] = values.sized(context, notes.getNotes(), values.int(Ui.notes_size))
        }
    }
}
