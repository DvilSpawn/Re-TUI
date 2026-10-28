package ohi.andre.consolelauncher.wallpaper.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class AstroTrafficTest {
    private val w = 270
    private val h = 480

    private fun traffic(seed: Int): SmallTraffic {
        val station = AstroStation(w / 2f, h / 2f)
        return SmallTraffic(seed, station, w / 2f, h / 2f, 92f, w, h)
    }

    @Test fun shipsDockLeaveAndNeverCrossTheStationBody() {
        val t = traffic(3)
        var docks = 0
        var overSpine = 0
        val wasDocked = HashSet<SmallShip>()
        repeat(30 * 60 * 20) {
            t.step(0.05f)
            for (s in t.ships) {
                if (s.state == SmallShip.State.DOCKED && wasDocked.add(s)) docks++
                val moving = s.state == SmallShip.State.FLY || s.state == SmallShip.State.EXIT || s.state == SmallShip.State.LAND
                if (moving && abs(s.x - w / 2f) < 44 && abs(s.y - h / 2f) < 3) overSpine++
            }
        }
        assertTrue("ships should dock regularly, got $docks", docks > 100)
        assertTrue("ships flew over the spine for $overSpine frames", overSpine < 20)
        assertTrue("traffic stays light", t.ships.size <= 16)
    }

    @Test fun portsAreReleasedWhenTheirShipLeaves() {
        val t = traffic(9)
        repeat(20 * 60 * 20) { t.step(0.05f) }
        for (p in t.station.ports) {
            val owner = p.owner ?: continue
            assertTrue("a port is held by a ship that has gone", owner in t.ships)
        }
    }

    @Test fun everyDesignPaintsAndShrinksWithoutLosingItsEngines() {
        for (design in 0..4) for (seed in 1..6) {
            val ship = Freighters.build(design, seed)
            assertTrue(ship.px.any { it != 0 })
            assertTrue(ship.nozzles.isNotEmpty())
            val far = ship.scaled(0.28f)
            assertTrue(far.w < ship.w && far.px.any { it != 0 })
            assertEquals(ship.nozzles.size, far.nozzles.size)
        }
    }

    @Test fun flybysCrossTheScreenAndLeave() {
        val rng = Random(4)
        val fleet = FlybyFleet(4, 0)
        val ships = ArrayList<Flyby>()
        repeat(6) { ships += Flyby(rng, ships, w, h, h / 2f, fleet) }
        repeat(20 * 200) { ships.forEach { it.step(0.05f) } }
        assertTrue(ships.all { it.gone })
    }

    @Test fun thePlanetIsReshadedOnlyWhenItsSurfaceOrCloudsStep() {
        val world = AstroWorld(2, w, h)
        val a = AstroFrame(w, h); val b = AstroFrame(w, h)
        world.drawPlanet(a, 101f)
        world.drawPlanet(b, 101.3f)  // inside one surface and one cloud step: only city lights may differ
        val cityColours = world.palette.city.toSet()
        val changed = a.px.indices.count { a.px[it] != b.px[it] && a.px[it] !in cityColours && b.px[it] !in cityColours }
        assertEquals(0, changed)
    }

    @Test fun everyWorldBootsThroughAllStagesWithoutAndroid() {
        for (seed in 0..4) {
            val world = AstroWorld(seed, w, h)
            val f = AstroFrame(w, h)
            AstroBoot.draw(f, 1f, 3, 0.5f, world.palette.name)
            assertTrue(f.px.toSet().size > 3)
        }
    }
}
