package ohi.andre.consolelauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class UIManagerUnifiedStatusTest {
    @Test fun compactsUnifiedStatusValues() {
        assertEquals("BAT: 96%", UIManager.compactUnifiedBattery("[########--] 96%"))
        assertEquals("Charging", UIManager.compactUnifiedBattery("Charging\nDetails"))
        assertEquals("23 C", UIManager.compactUnifiedLine("23 C\nCloudy"))
        assertEquals(
            "Partly cloudy",
            UIManager.compactUnifiedWeatherCondition("Weather: Partly cloudy, Temp: 29.7")
        )
        assertEquals(
            "Temp: 29.7",
            UIManager.compactUnifiedWeatherDetails("Weather: Partly cloudy, Temp: 29.7")
        )
        assertEquals("267.15/460.98 GB", UIManager.compactUnifiedCapacity(267.15, 460.98))
        assertEquals("", UIManager.compactUnifiedCapacity(0.0, 0.0))
        assertEquals(240, UIManager.boundedUnifiedModuleHeight(600, 240))
        assertEquals(120, UIManager.boundedUnifiedModuleHeight(120, 240))
        assertEquals(true, UIManager.shouldAutoHideUnifiedModules(""))
        assertEquals(false, UIManager.shouldAutoHideUnifiedModules("music"))
    }
}
