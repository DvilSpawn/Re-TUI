package ohi.andre.consolelauncher.managers.status

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

        assertEquals("Partly cloudy", snapshot.values["main"])
        assertEquals("29.7", snapshot.values["temp"])
        assertEquals("79.6", snapshot.values["humidity"])
        assertEquals("partlycloudy_night", snapshot.symbolCode)
        assertTrue(WeatherResponseParser.ascii(snapshot.symbolCode).contains(".-."))
    }

    @Test
    fun convertsTemperatureAndValidatesFixedCoordinates() {
        assertEquals("85.5", WeatherResponseParser.parse(response, "imperial")!!.values["temp"])
        assertEquals(13.0827 to 80.2707, WeatherResponseParser.parseCoordinates("13.0827, 80.2707"))
        assertNull(WeatherResponseParser.parseCoordinates("127954"))
        assertNull(WeatherResponseParser.parseCoordinates("91,0"))
    }
}
