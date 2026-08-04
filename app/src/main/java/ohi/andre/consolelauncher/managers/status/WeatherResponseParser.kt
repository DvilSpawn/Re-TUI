package ohi.andre.consolelauncher.managers.status

import com.jayway.jsonpath.JsonPath
import java.util.Locale

internal data class WeatherSnapshot(
    val values: Map<String, String>,
    val symbolCode: String
)

internal object WeatherResponseParser {
    fun parse(json: String, temperatureMeasure: String): WeatherSnapshot? {
        return try {
            val document = JsonPath.parse(json)
            val details = "$.properties.timeseries[0].data.instant.details"
            val symbol = readString(document, "$.properties.timeseries[0].data.next_1_hours.summary.symbol_code")
                ?: readString(document, "$.properties.timeseries[0].data.next_6_hours.summary.symbol_code")
                ?: return null
            val temperature = readNumber(document, "$details.air_temperature") ?: return null
            val values = linkedMapOf(
                "main" to conditionName(symbol),
                "description" to conditionName(symbol),
                "temp" to number(convertTemperature(temperature, temperatureMeasure)),
                "symbol_code" to symbol
            )
            addNumber(values, "pressure", document, "$details.air_pressure_at_sea_level")
            addNumber(values, "humidity", document, "$details.relative_humidity")
            addNumber(values, "deg", document, "$details.wind_from_direction")
            addNumber(values, "clouds", document, "$details.cloud_area_fraction")
            addNumber(values, "all", document, "$details.cloud_area_fraction")
            readNumber(document, "$details.wind_speed")?.let {
                values["speed"] = number(if (temperatureMeasure == "imperial") it * 2.236936 else it)
            }
            WeatherSnapshot(values, symbol)
        } catch (_: Exception) {
            null
        }
    }

    fun parseCoordinates(value: String?): Pair<Double, Double>? {
        val parts = value?.trim()?.split(',') ?: return null
        if (parts.size != 2) return null
        val latitude = parts[0].trim().toDoubleOrNull() ?: return null
        val longitude = parts[1].trim().toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return latitude to longitude
    }

    fun ascii(symbolCode: String?): String {
        val symbol = symbolCode.orEmpty()
        return when {
            "thunder" in symbol -> "    .-.\n   (   ).\n  (___(__)\n   /_/ /_/"
            "snow" in symbol -> "    .-.\n   (   ).\n  (___(__)\n   *  *  *"
            "rain" in symbol || "sleet" in symbol -> "    .-.\n   (   ).\n  (___(__)\n   / / / /"
            symbol == "fog" -> " _ - _ - _\n  _ - _ -\n _ - _ - _"
            symbol.startsWith("partlycloudy") -> "   \\  /\n _ /\"\".-.\n   \\_(   ).\n   /(___(__)"
            symbol == "cloudy" -> "    .--.\n .-(    ).\n(___.__)__)"
            symbol.startsWith("fair") || symbol.startsWith("clearsky") -> "   \\ | /\n    .-.\n --(   )--\n    `-'"
            else -> ""
        }
    }

    private fun conditionName(symbolCode: String): String {
        val symbol = symbolCode.removeSuffix("_day").removeSuffix("_night").removeSuffix("_polartwilight")
        val weight = when {
            symbol.startsWith("light") -> "Light "
            symbol.startsWith("heavy") -> "Heavy "
            else -> ""
        }
        return weight + when {
            "thunder" in symbol -> "thunderstorm"
            "snow" in symbol -> if ("showers" in symbol) "snow showers" else "snow"
            "sleet" in symbol -> if ("showers" in symbol) "sleet showers" else "sleet"
            "rain" in symbol -> if ("showers" in symbol) "rain showers" else "rain"
            symbol == "partlycloudy" -> "Partly cloudy"
            symbol == "cloudy" -> "Cloudy"
            symbol == "fog" -> "Fog"
            symbol == "fair" -> "Fair"
            symbol == "clearsky" -> "Clear"
            else -> symbol.replace('_', ' ')
        }
    }

    private fun convertTemperature(celsius: Double, measure: String): Double = when (measure) {
        "imperial" -> celsius * 9.0 / 5.0 + 32.0
        "standard" -> celsius + 273.15
        else -> celsius
    }

    private fun addNumber(
        values: MutableMap<String, String>,
        name: String,
        document: com.jayway.jsonpath.DocumentContext,
        path: String
    ) {
        readNumber(document, path)?.let { values[name] = number(it) }
    }

    private fun readNumber(document: com.jayway.jsonpath.DocumentContext, path: String): Double? =
        try {
            (document.read<Any>(path) as? Number)?.toDouble()
        } catch (_: Exception) {
            null
        }

    private fun readString(document: com.jayway.jsonpath.DocumentContext, path: String): String? =
        try {
            document.read<String>(path)
        } catch (_: Exception) {
            null
        }

    private fun number(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
