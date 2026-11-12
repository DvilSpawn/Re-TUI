package ohi.andre.consolelauncher.managers.modules.home

import android.content.Context
import android.text.TextUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.status.WeatherResponseParser
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import java.util.Calendar
import java.util.Locale

internal class WeatherModule(private val reading: () -> Reading, private val ensureUpdating: () -> Unit) : HomeModule {
    data class Reading(val text: CharSequence?, val symbol: String?, val updatedMillis: Long)

    override val id = ModuleManager.WEATHER_NATIVE
    override val refreshOn = setOf(HomeModuleEvent.WEATHER)

    override fun show(host: HomeModuleHost) {
        ensureUpdating()
        host.renderText(id, buildText(host.context, reading()))
    }

    private fun buildText(context: Context, latest: Reading): String {
        val weather = latest.text
        if (TextUtils.isEmpty(weather)) {
            return context.getString(R.string.surface_no_weather_yet_e6067)
        }

        val out = StringBuilder()
        val ascii = WeatherResponseParser.ascii(latest.symbol)
        if (ascii.isNotEmpty()) out.append(ascii).append('\n')
        XMLPrefsManager.get(Behavior.weather_location)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out.append(context.getString(R.string.surface_location_c47d5)).append(it).append('\n')
        }
        out.append(weather.toString().trim { it <= ' ' })
        if (latest.updatedMillis > 0) {
            val calendar = Calendar.getInstance()
            calendar.setTimeInMillis(latest.updatedMillis)
            out.append(context.getString(R.string.surface_updated_aea3f))
                .append(
                    String.format(
                        Locale.US, "%02d.%02d",
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE)
                    )
                )
        }
        out.append(context.getString(R.string.surface_data_met_norway_https_api_met_no_doc_license_6815e))
        return out.toString()
    }
}
