package ohi.andre.consolelauncher.managers.modules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleManagerTest {
    @Test fun termuxWeatherDoesNotReceiveNativeWeatherControls() {
        assertFalse(ModuleManager.usesNativeWeatherControls(ModuleManager.WEATHER))
        assertTrue(ModuleManager.usesNativeWeatherControls(ModuleManager.WEATHER_NATIVE))
    }
}
