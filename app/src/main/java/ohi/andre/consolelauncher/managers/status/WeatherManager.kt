@file:Suppress("DEPRECATION")

package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.UIManager

class WeatherManager(
    context: Context,
    delay: Long,
    private var size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    private var url: String? = null
    private var configurationError: String? = null
    private var locationQuery: String? = null
    private var resolvingLocation = false

    init {
        val where = XMLPrefsManager.get(Behavior.weather_location)?.trim()
        val coordinates = WeatherResponseParser.parseCoordinates(where)
        if (where.isNullOrEmpty() || where == "null") {
            configurationError = context.getString(R.string.weather_location_required)
        } else if (coordinates != null) {
            setUrl(coordinates.first, coordinates.second)
        } else {
            locationQuery = where
        }
    }

    override fun refreshAppearance() {
        // The next scheduled response uses the new size; changing a font must not fetch weather.
        size = XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Ui.weather_size)
    }

    override fun update() {
        updateWeather()
    }

    fun updateWeather() {
        configurationError?.let {
            val intent = Intent(UIManager.ACTION_WEATHER)
            intent.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, it)
            LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(intent)
            return
        }
        if (url == null && !resolvingLocation) {
            val query = locationQuery ?: return
            val cached = cachedCoordinates(query)
            if (cached != null) {
                setUrl(cached.first, cached.second)
            } else {
                resolveLocation(query)
                return
            }
        }

        val currentUrl = url
        if (currentUrl != null) {
            val intent = Intent(HTMLExtractManager.ACTION_WEATHER)
            intent.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, currentUrl)
            intent.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
            LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(intent)
        }
    }

    private fun resolveLocation(query: String) {
        resolvingLocation = true
        Thread {
            try {
                if (!Geocoder.isPresent()) throw IllegalStateException()
                val address = Geocoder(context, Locale.getDefault())
                    .getFromLocationName(query, 1)
                    ?.firstOrNull()
                    ?: throw IllegalArgumentException()
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(CACHE_QUERY, query)
                    .putLong(CACHE_LATITUDE, java.lang.Double.doubleToRawLongBits(address.latitude))
                    .putLong(CACHE_LONGITUDE, java.lang.Double.doubleToRawLongBits(address.longitude))
                    .apply()
                setUrl(address.latitude, address.longitude)
                if (running) updateWeather()
            } catch (_: Exception) {
                sendOutput(context.getString(R.string.weather_location_not_found))
            } finally {
                resolvingLocation = false
            }
        }.start()
    }

    private fun cachedCoordinates(query: String): Pair<Double, Double>? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(CACHE_QUERY, null) != query ||
            !preferences.contains(CACHE_LATITUDE) || !preferences.contains(CACHE_LONGITUDE)
        ) return null
        return java.lang.Double.longBitsToDouble(preferences.getLong(CACHE_LATITUDE, 0)) to
            java.lang.Double.longBitsToDouble(preferences.getLong(CACHE_LONGITUDE, 0))
    }

    fun setLocation(latitude: Double, longitude: Double) {
        locationQuery = null
        configurationError = null
        setUrl(latitude, longitude)
        updateWeather()
    }

    private fun sendOutput(message: String) {
        val intent = Intent(UIManager.ACTION_WEATHER)
        intent.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, message)
        LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(intent)
    }

    private fun setUrl(latitude: Double, longitude: Double) {
        url = String.format(
            Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
            latitude,
            longitude
        )
        rememberPlace(latitude, longitude)
    }

    /** Looks up the town at these coordinates once, for %name (MET Norway's forecast carries no place name). */
    private fun rememberPlace(latitude: Double, longitude: Double) {
        val key = placeKey(latitude, longitude)
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences.edit().putString(CURRENT_PLACE_KEY, key).apply()
        if (preferences.getString(CACHE_PLACE_KEY, null) == key) return
        Thread {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching
                val address = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)?.firstOrNull() ?: return@runCatching
                val name = listOf(address.locality, address.subAdminArea, address.adminArea).firstOrNull { !it.isNullOrBlank() } ?: return@runCatching
                preferences.edit().putString(CACHE_PLACE_KEY, key).putString(CACHE_PLACE, name).apply()
            }
        }.start()
    }

    fun setDelay(delay: Int) {
        this.delay = delay.toLong()
    }

    companion object {
        /**
         * The weather location's name for %name: the town found for the forecast's coordinates, else the place
         * name as typed in weather_location (never coordinates).
         */
        fun placeName(context: Context): String? {
            val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val current = preferences.getString(CURRENT_PLACE_KEY, null)
            if (current != null && preferences.getString(CACHE_PLACE_KEY, null) == current) {
                preferences.getString(CACHE_PLACE, null)?.let { return it }
            }
            val typed = XMLPrefsManager.get(Behavior.weather_location)?.trim()
            return typed?.takeIf { it.isNotEmpty() && it != "null" && WeatherResponseParser.parseCoordinates(it) == null }
        }

        internal fun placeKey(latitude: Double, longitude: Double) = String.format(Locale.US, "%.3f,%.3f", latitude, longitude)

        private const val CURRENT_PLACE_KEY = "place_for"
        private const val CACHE_PLACE_KEY = "place_key"
        private const val CACHE_PLACE = "place_name"
        private const val PREFS_NAME = "retui_weather"
        private const val CACHE_QUERY = "location_query"
        private const val CACHE_LATITUDE = "latitude"
        private const val CACHE_LONGITUDE = "longitude"
    }
}
