package ohi.andre.consolelauncher.wallpaper

import ohi.andre.consolelauncher.wallpaper.nagomi.NagomiSchool
import ohi.andre.consolelauncher.wallpaper.nagomi.NagomiNotificationKeys
import ohi.andre.consolelauncher.wallpaper.nagomi.nagomiFrameDelay
import org.junit.Assert.*
import org.junit.Test

class NagomiSchoolTest {
    @Test fun frameBudgetIncludesDrawingAndFailuresBackOff() {
        assertEquals(23L, nagomiFrameDelay(true, 10))
        assertEquals(1L, nagomiFrameDelay(true, 45))
        assertEquals(1000L, nagomiFrameDelay(false, 10))
    }
    @Test fun fixedStepsMatchAcrossFrameRatesAndPauseDoesNotJump() {
        val fast = NagomiSchool(8)
        val slow = NagomiSchool(8)
        fast.advance(0)
        slow.advance(0)
        for (t in 10..6000 step 10) fast.advance(t.toLong())
        for (t in 50..6000 step 50) slow.advance(t.toLong())
        assertEquals(fast.time, slow.time, 1e-8)
        fast.fish.zip(slow.fish).forEach { (a, b) ->
            assertEquals(a.position.x, b.position.x, 1e-8)
            assertEquals(a.position.y, b.position.y, 1e-8)
        }
        assertEquals(listOf(24, 15, 34), (0..2).map { school -> slow.tiny.fish.count { it.school == school } })
        fast.tiny.fish.zip(slow.tiny.fish).forEach { (a, b) ->
            assertEquals(a.x, b.x, 1e-8)
            assertEquals(a.y, b.y, 1e-8)
        }
        val tinyX = slow.tiny.fish[0].x
        val tinyY = slow.tiny.fish[0].y
        val position = slow.fish[0].position
        slow.resetClock()
        slow.advance(600000)
        assertEquals(position, slow.fish[0].position)
        assertEquals(tinyX, slow.tiny.fish[0].x, 0.0)
        assertEquals(tinyY, slow.tiny.fish[0].y, 0.0)
        slow.resize(540.0, 270.0)
        assertEquals(tinyX * 2, slow.tiny.fish[0].x, 1e-8)
        assertEquals(tinyY / 2, slow.tiny.fish[0].y, 1e-8)
    }

    @Test fun notificationPopulationIsBoundedExpiresAndKeepsAmbientFish() {
        val pond = NagomiSchool()
        repeat(100) { pond.spawn(it, 123) }
        assertEquals(NagomiSchool.MAX_FISH, pond.fish.size)
        assertEquals(99, pond.fish.last().accent)
        assertEquals(NagomiSchool.BASE_FISH, pond.fish.count { it.ambient })
        pond.advance(0)
        for (t in 100..185000 step 100) pond.advance(t.toLong())
        assertEquals(NagomiSchool.BASE_FISH, pond.fish.size)
        assertEquals(73, pond.tiny.fish.size)
        pond.tiny.fish.forEach {
            assertTrue(it.x in 0.0..pond.width && it.y in 0.0..pond.height)
            assertTrue(it.vx.isFinite() && it.vy.isFinite())
        }
        pond.fish.forEach {
            assertTrue(it.position.x.isFinite() && it.position.y.isFinite())
            assertTrue(it.position.x in -20.0..pond.width + 20)
            assertTrue(it.position.y in -20.0..pond.height + 20)
            it.spine.forEach { p -> assertTrue(p.x.isFinite() && p.y.isFinite()) }
        }
    }

    @Test fun notificationUpdatesAndReconnectDoNotSpawnDuplicates() {
        val keys = NagomiNotificationKeys()
        keys.seed(listOf("existing"))
        assertFalse(keys.posted("existing"))
        assertTrue(keys.posted("new"))
        assertFalse(keys.posted("new"))
        keys.removed("new")
        assertTrue(keys.posted("new"))
        keys.seed(listOf("existing", "new"))
        assertFalse(keys.posted("new"))
    }
}
