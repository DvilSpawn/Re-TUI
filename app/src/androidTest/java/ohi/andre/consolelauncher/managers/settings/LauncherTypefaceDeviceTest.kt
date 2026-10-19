package ohi.andre.consolelauncher.managers.settings

import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.tuils.LauncherTypeface
import ohi.andre.consolelauncher.tuils.Tuils
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LauncherTypefaceDeviceTest {
    @Test fun systemConfiguredLegacyAndInvalidFontsUseSameReadOnlyFallbackChain() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.cacheDir,"font-preview-${System.nanoTime()}").apply { mkdirs() }
        val priorPath=Tuils.fontPath
        try {
            val legacy=File(folder,"legacy.ttf")
            context.assets.open(Tuils.BUNDLED_FONT_ASSET).use { input->legacy.outputStream().use { output->input.copyTo(output) } }
            val before=legacy.readBytes()
            val system=LauncherTypeface.load(context,folder,true,null)
            assertEquals(Typeface.DEFAULT,system.typeface)
            assertNull(system.path)
            val configured=LauncherTypeface.load(context,folder,false,legacy.name)
            assertEquals(legacy.absolutePath,configured.path)
            assertNull(configured.legacyFileName)
            val old=LauncherTypeface.load(context,folder,false,"")
            assertEquals(legacy.absolutePath,old.path)
            assertEquals(legacy.name,old.legacyFileName)
            assertArrayEquals(before,legacy.readBytes())
            val invalid=File(folder,"invalid.ttf").apply { writeText("not a font") }
            val fallback=LauncherTypeface.load(context,folder,false,invalid.name)
            assertEquals("asset://${Tuils.BUNDLED_FONT_ASSET}",fallback.path)
            assertNotNull(fallback.customFontError)
            assertNull(fallback.legacyFileName)
            assertEquals("not a font",invalid.readText())
            assertEquals("Pure font loading changed global font path",priorPath,Tuils.fontPath)
        } finally { folder.deleteRecursively() }
    }
}
