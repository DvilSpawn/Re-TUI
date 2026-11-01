package ohi.andre.consolelauncher.tuils.asciifx

import java.io.File
import ohi.andre.consolelauncher.tuils.FigletFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsciiEffectsTest {
    private val green = 0xFF00FF00.toInt()
    private val red = 0xFFFF0000.toInt()

    private fun art(): AsciiArt {
        val lines = FigletFont.parse(File("src/main/assets/figlet/small.flf").readText()).render("Re:TUI").lines()
        // One coloured cell, as a colour-HTML import would give.
        val colors = lines.map { IntArray(it.length) }.also { it[1][lines[1].indexOfFirst { c -> !c.isWhitespace() }] = red }
        return AsciiArt(lines, colors, green)
    }

    private fun grid(art: AsciiArt) = AsciiGrid(art.rows, art.cols).also { g ->
        for (r in 0 until art.rows) for (c in 0 until art.cols) art.symbol(r, c).takeIf { !it.isWhitespace() }?.let { g.put(r, c, it, art.color(r, c)) }
    }

    @Test
    fun everyEffectEndsOnTheArtWithinTwentySeconds() {
        for (name in AsciiEffects.NAMES - AsciiEffects.NONE) {
            val art = art()
            val effect = AsciiEffects.create(name, art)!!
            var ticks = 0
            while (effect.step()) { ticks++; check(ticks < 20 * TICKS_PER_SECOND) { "$name never finished" } }
            val out = AsciiGrid(art.rows, art.cols).also(effect::compose)
            val expected = grid(art)
            for (r in 0 until art.rows) for (c in 0 until art.cols) {
                assertEquals("$name symbol at $r,$c", expected.symbol(r, c), out.symbol(r, c))
                if (!expected.symbol(r, c).isWhitespace()) assertEquals("$name colour at $r,$c", expected.color(r, c), out.color(r, c))
            }
            assertTrue("$name should animate", ticks > 10)
        }
    }

    @Test
    fun midwayFramesDifferFromTheArt() {
        for (name in AsciiEffects.NAMES - AsciiEffects.NONE) {
            val art = art()
            val effect = AsciiEffects.create(name, art)!!
            repeat(20) { effect.step() }
            val out = AsciiGrid(art.rows, art.cols).also(effect::compose)
            val expected = grid(art)
            val same = (0 until art.rows).all { r -> (0 until art.cols).all { c -> out.symbol(r, c) == expected.symbol(r, c) && out.color(r, c) == expected.color(r, c) } }
            assertTrue("$name looks finished after 20 ticks", !same)
        }
    }

    @Test
    fun finishSettlesAtOnceAndNoneIsNoEffect() {
        val art = art()
        val effect = AsciiEffects.create("decrypt", art)!!
        repeat(5) { effect.step() }
        effect.finish()
        val out = AsciiGrid(art.rows, art.cols).also(effect::compose)
        val col = art.lines[1].indexOfFirst { !it.isWhitespace() }
        assertEquals(art.symbol(1, col), out.symbol(1, col))
        assertEquals(red, out.color(1, col))
        assertNull(AsciiEffects.create(AsciiEffects.NONE, art))
    }
}
