package ohi.andre.consolelauncher.managers.status

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

/** Binds the same about_device_view.xml layout in the launcher and preview. */
object UnifiedStatusRenderer {
    data class State(val battery: CharSequence = "", val weather: String = "", val location: String = "", val details: String = "",
                     val internalStorage: String = "", val externalStorage: String = "", val connection: String = "",
                     val wifi: Boolean = false, val ram: String = "", val unlocks: String = "")

    fun bind(root: View, values: AppearanceValues, show: BooleanArray, state: State, font: Typeface? = null) {
        val context = root.context
        val typeface = font ?: values.typeface(context)
        val size = values.float(Ui.time_size).coerceIn(18f, 28f)
        val compact = (size * .65f).coerceAtLeast(11f)
        fun visible(label: ohi.andre.consolelauncher.UIManager.Label) = show.getOrElse(label.ordinal) { false }
        fun text(id: Int, value: CharSequence, role: Theme, enabled: Boolean, textSize: Float = compact) {
            root.findViewById<TextView>(id)?.apply {
                visibility = if (enabled && value.isNotEmpty()) View.VISIBLE else View.GONE
                text = value
                this.typeface = typeface
                setTextColor(values.color(role))
                values.applySize(this, textSize)
                includeFontPadding = false
            }
        }
        fun icon(id: Int, role: Theme, enabled: Boolean) {
            root.findViewById<ImageView>(id)?.apply {
                visibility = if (enabled) View.VISIBLE else View.GONE
                imageTintList = ColorStateList.valueOf(values.color(role))
            }
        }
        val now = java.util.Date()
        text(R.id.unified_status_date, android.text.format.DateFormat.getMediumDateFormat(context).format(now), Theme.time_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.time))
        text(R.id.unified_status_time, android.text.format.DateFormat.getTimeFormat(context).format(now), Theme.time_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.time), size)
        text(R.id.unified_status_battery, state.battery, Theme.battery_text_high, visible(ohi.andre.consolelauncher.UIManager.Label.battery), values.float(Ui.battery_size).coerceIn(11f, 16f))
        val weather = visible(ohi.andre.consolelauncher.UIManager.Label.weather) && state.weather.isNotEmpty()
        text(R.id.unified_status_weather, if (state.weather.isEmpty()) "" else ": ${state.weather}", Theme.weather_text_color, weather)
        text(R.id.unified_status_weather_location, state.location, Theme.weather_text_color, weather)
        text(R.id.unified_status_weather_details, state.details, Theme.weather_text_color, weather)
        icon(R.id.unified_status_weather_icon, Theme.weather_text_color, weather)
        val storage = visible(ohi.andre.consolelauncher.UIManager.Label.storage)
        text(R.id.unified_status_storage_internal, state.internalStorage, Theme.storage_text_color, storage)
        text(R.id.unified_status_storage_external, state.externalStorage, Theme.storage_text_color, storage)
        icon(R.id.unified_status_storage_internal_icon, Theme.storage_text_color, storage && state.internalStorage.isNotEmpty())
        icon(R.id.unified_status_storage_external_icon, Theme.storage_text_color, storage && state.externalStorage.isNotEmpty())
        text(R.id.unified_status_network, state.connection, Theme.network_info_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.network))
        root.findViewById<TextView>(R.id.unified_status_network)?.apply {
            val drawable = ContextCompat.getDrawable(context, if (state.wifi) R.drawable.ic_status_wifi_40 else R.drawable.ic_material_signal_cellular_alt_24)?.mutate()
            drawable?.setBounds(0, 0, Tuils.dpToPx(context, 16), Tuils.dpToPx(context, 16))
            drawable?.setTint(values.color(Theme.network_info_text_color))
            setCompoundDrawables(drawable, null, null, null)
        }
        text(R.id.unified_status_ram, state.ram, Theme.ram_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.ram))
        icon(R.id.unified_status_ram_icon, Theme.ram_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.ram) && state.ram.isNotEmpty())
        text(R.id.unified_status_unlock, state.unlocks, Theme.unlock_counter_text_color, visible(ohi.andre.consolelauncher.UIManager.Label.unlock))
    }
}
