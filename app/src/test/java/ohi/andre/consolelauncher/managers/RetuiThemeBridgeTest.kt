package ohi.andre.consolelauncher.managers

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetuiThemeBridgeTest {
    @Test
    fun resolvesConfiguredFontAndRejectsStaleCachedPath() {
        val root = Files.createTempDirectory("retui-fonts").toFile()
        try {
            val fonts = File(root, "fonts").apply { mkdirs() }
            val configured = File(fonts, "KHInterferenceTRIAL-Regular.otf").apply { writeText("font") }
            val stale = File(root, "stale.ttf").apply { writeText("font") }

            val extras = RetuiThemeBridge.resolveLauncherFontExtras(
                useSystemFont = false,
                configuredFont = configured.name,
                launcherRoot = root,
                cachedFontPath = stale.absolutePath
            )

            assertEquals(configured.absolutePath, extras.path)
            assertEquals(configured.name, extras.file)
            assertNull(extras.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun systemFontNeverLeaksCachedPath() {
        val extras = RetuiThemeBridge.resolveLauncherFontExtras(
            useSystemFont = true,
            configuredFont = "custom.ttf",
            launcherRoot = File("."),
            cachedFontPath = "/tmp/stale.ttf"
        )

        assertEquals("system", extras.name)
        assertNull(extras.path)
        assertNull(extras.file)
    }
}
