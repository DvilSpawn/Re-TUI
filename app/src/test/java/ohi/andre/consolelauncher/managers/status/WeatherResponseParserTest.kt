package ohi.andre.consolelauncher.managers.status

import ohi.andre.consolelauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherResponseParserTest {
    private val response = """{
        "properties":{"timeseries":[{"data":{
          "instant":{"details":{"air_temperature":29.7,"air_pressure_at_sea_level":1003.6,"relative_humidity":79.6,"cloud_area_fraction":96.1,"wind_from_direction":204.5,"wind_speed":4.9}},
          "next_1_hours":{"summary":{"symbol_code":"partlycloudy_night"}}
        }}]}
    }"""

    @Test
    fun parsesMetForecastIntoLegacyWeatherFields() {
        val snapshot = WeatherResponseParser.parse(response, "metric")!!

        assertEquals(R.string.weather_condition_partlycloudy, snapshot.conditionResource)
        assertEquals("29.7", snapshot.values["temp"])
        assertEquals("79.6", snapshot.values["humidity"])
        assertEquals("partlycloudy_night", snapshot.symbolCode)
        assertEquals(
            "29.7°C · 79.6% · 4.9 m/s",
            WeatherResponseParser.compactDetails(snapshot.values, "metric")
        )
        assertTrue(WeatherResponseParser.ascii(snapshot.symbolCode).contains(".-."))
    }

    @Test
    fun convertsTemperatureAndValidatesFixedCoordinates() {
        assertEquals("85.5", WeatherResponseParser.parse(response, "imperial")!!.values["temp"])
        assertEquals(13.0827 to 80.2707, WeatherResponseParser.parseCoordinates("13.0827, 80.2707"))
        assertNull(WeatherResponseParser.parseCoordinates("127954"))
        assertNull(WeatherResponseParser.parseCoordinates("91,0"))
    }

    @Test
    fun nextDayRangeAndRainComeFromTheForecast() {
        val forecast = """{"properties":{"timeseries":[
            {"time":"2026-10-30T07:00:00Z","data":{"instant":{"details":{"air_temperature":7.9}},
              "next_1_hours":{"summary":{"symbol_code":"fog"},"details":{"precipitation_amount":0.4}},
              "next_6_hours":{"summary":{"symbol_code":"cloudy"},"details":{"precipitation_amount":1.2}}}},
            {"time":"2026-10-30T13:00:00Z","data":{"instant":{"details":{"air_temperature":11.5}}}},
            {"time":"2026-10-31T03:00:00Z","data":{"instant":{"details":{"air_temperature":4.2}}}},
            {"time":"2026-10-31T09:00:00Z","data":{"instant":{"details":{"air_temperature":-3.0}}}}
        ]}}"""
        val metric = WeatherResponseParser.parse(forecast, "metric")!!.values
        // The last entry is 26 hours ahead, outside the next day.
        assertEquals("4.2", metric["temp_min"])
        assertEquals("11.5", metric["temp_max"])
        assertEquals("0.4", metric["rain"])
        val imperial = WeatherResponseParser.parse(forecast, "imperial")!!.values
        assertEquals("39.6", imperial["temp_min"])
        assertEquals("52.7", imperial["temp_max"])
        // Without them the placeholders stay unset rather than wrong.
        assertNull(WeatherResponseParser.parse(response, "metric")!!.values["rain"])
    }
}
