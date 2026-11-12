package ohi.andre.consolelauncher.tuils

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FigletFontTest {
    private fun font(name: String) = FigletFont.parse(SourceFiles.repo("app/src/main/assets/figlet/$name.flf").readText())

    @Test
    fun smallMatchesFiglet() {
        assertEquals(
            """
             _  _ ___ _    _    ___
            | || | __| |  | |  / _ \
            | __ | _|| |__| |_| (_) |
            |_||_|___|____|____\___/
            """.trimIndent(),
            font("small").render("HELLO")
        )
    }

    @Test
    fun standardSmushesLikeFiglet() {
        assertEquals(
            """
             _   _ _
            | | | (_)
            | |_| | |
            |  _  | |
            |_| |_|_|
            """.trimIndent(),
            font("standard").render("Hi")
        )
    }

    @Test
    fun everyBundledFontParsesAndWrapsWords() {
        for (name in FigletFont.BUNDLED) {
            val font = font(name)
            val art = font.render("Re:TUI banner test", 40)
            assertTrue(name, art.lines().all { it.length <= 40 })
            assertTrue(name, art.lines().size > font.height)
        }
    }

}
