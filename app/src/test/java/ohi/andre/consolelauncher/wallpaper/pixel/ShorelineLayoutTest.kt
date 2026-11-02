package ohi.andre.consolelauncher.wallpaper.pixel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShorelineLayoutTest {
    private fun lit(dots: ShorelineView.Companion.Dots) = dots.ink.count { it }

    @Test
    fun theTimeIsOneLineOfFiveByEightDigits() {
        val dots = ShorelineView.layout("12:34", 44)
        // Four 5-wide digits, a 2-wide colon and a column between each character.
        assertEquals(4 * 5 + 2 + 4, dots.cols)
        assertEquals(7, dots.rows)
    }

    @Test
    fun longTextWrapsAtSpacesUpToThreeLines() {
        val one = ShorelineView.layout("HI", 44)
        assertEquals(7, one.rows)
        val two = ShorelineView.layout("GOOD MORNING", 44)
        assertEquals(7 + 3 + 7, two.rows)
        assertTrue(two.cols <= 44)
        val many = ShorelineView.layout("A B C D E F G H I J K L M N O P Q R S T U V W X Y Z", 12)
        assertEquals(3 * 7 + 2 * 3, many.rows)
    }

    @Test
    fun lowerCaseIsCapitalsAndUnknownCharactersAreSkipped() {
        assertEquals(lit(ShorelineView.layout("HELLO", 44)), lit(ShorelineView.layout("hello", 44)))
        assertEquals(lit(ShorelineView.layout("HI", 44)), lit(ShorelineView.layout("H~I", 44)))
    }

    @Test
    fun aWordWiderThanTheLineIsBroken() {
        val dots = ShorelineView.layout("WWWWWWWWWW", 20)
        assertTrue(dots.cols <= 20)
        assertTrue(dots.rows > 7)
    }
}
