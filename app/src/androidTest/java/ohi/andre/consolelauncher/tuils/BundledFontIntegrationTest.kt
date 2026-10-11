package ohi.andre.consolelauncher.tuils

import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import ohi.andre.consolelauncher.notes.RetuiTheme

class BundledFontIntegrationTest {
    @Test fun regularFontAndNerdGlyphsLoadAndNoticesArePackagedWithoutLucida() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assets = context.assets
        val font = Typeface.createFromAsset(assets, Tuils.BUNDLED_FONT_ASSET)
        assertEquals(Typeface.NORMAL, font.style)
        val paint = Paint().apply { typeface = font }
        assertTrue(paint.hasGlyph("A"))
        assertTrue(paint.hasGlyph("\uE0B0"))
        paint.typeface = RetuiTheme(fontName = Tuils.BUNDLED_FONT_NAME).typeface(context)
        assertTrue("Notes resolve the named bundled font locally", paint.hasGlyph("\uE0B0"))
        assertFalse(assets.list("")!!.contains("lucida_console.ttf"))
        val license = assets.open("licenses/LICENSES/JetBrainsMonoNerdFont/JetBrainsMono-OFL.txt")
            .bufferedReader().use { it.readText() }
        assertTrue(license.contains("SIL OPEN FONT LICENSE Version 1.1"))
        assets.open("licenses/LICENSES/JetBrainsMonoNerdFont/Font-Logos.txt").use {
            assertTrue(it.read() >= 0)
        }
    }
}
