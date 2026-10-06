package ohi.andre.consolelauncher.managers.xml

import org.junit.Assert.*
import org.junit.Test

class AutoColorManagerTest {
    private val colors = intArrayOf(0xFF223344.toInt(), 0xFF336699.toInt(), 0xFF111111.toInt())

    @Test fun wallpaperWinsWithoutReadingFallback() {
        val result = AutoColorManager.selectSeedColors(36, { colors }, { error("Must not read fallback") })
        assertEquals(AutoColorManager.Source.WALLPAPER, result.source)
        assertArrayEquals(colors, result.colors)
        assertNull(result.failureReason)
    }

    @Test fun nullAndExceptionBothFallBackOnAndroid12AndLater() {
        for (wallpaper in listOf<() -> IntArray?>({ null }, { throw SecurityException("denied") })) {
            val result = AutoColorManager.selectSeedColors(36, wallpaper, { colors })
            assertEquals(AutoColorManager.Source.SYSTEM, result.source)
            assertArrayEquals(colors, result.colors)
            assertNotNull(result.failureReason)
        }
    }

    @Test fun unsupportedApisAndFailedFallbackReportUnavailable() {
        val old = AutoColorManager.selectSeedColors(23, { error("unsupported") }, { error("unsupported") })
        assertEquals(AutoColorManager.Source.UNAVAILABLE, old.source)
        val noSystem = AutoColorManager.selectSeedColors(30, { null }, { error("unsupported") })
        assertEquals(AutoColorManager.Source.UNAVAILABLE, noSystem.source)
        val failed = AutoColorManager.selectSeedColors(36, { throw SecurityException() }, { throw IllegalArgumentException() })
        assertEquals(AutoColorManager.Source.UNAVAILABLE, failed.source)
        assertNull(failed.colors)
        assertEquals("wallpaper_SecurityException;system_IllegalArgumentException", failed.failureReason)
        assertEquals(AutoColorManager.Source.UNAVAILABLE,
            AutoColorManager.selectSeedColors(36, { null }, { null }).source)
    }
}
