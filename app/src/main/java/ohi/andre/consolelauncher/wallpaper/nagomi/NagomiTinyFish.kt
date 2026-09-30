package ohi.andre.consolelauncher.wallpaper.nagomi

import kotlin.math.*
import kotlin.random.Random

/** Adapted from Nagomi tiny-fish.ts and settings/definition.ts, Copyright 2026 Mayank Kadam.
 * Retains autonomous schooling; omits pointer-triggered fleeing and the web editor.
 */
internal class NagomiTinyFish {
    class Fish(val school: Int, var x: Double, var y: Double, var vx: Double, var vy: Double,
               val length: Double, val width: Double, val cruise: Double, val phase: Double, var tail: Double)
    private val random = Random(0x51a7f15c)
    private fun range(a: Double, b: Double) = a + random.nextDouble() * (b - a)
    private val settings = arrayOf(
        doubleArrayOf(174.0,82.0,24.0,.35,32.0,15.0,.88,1.04,1.0),
        doubleArrayOf(343.0,174.0,15.0,2.75,22.0,11.0,1.08,.95,-1.0),
        doubleArrayOf(139.0,204.0,34.0,-.72,40.0,18.0,.82,1.12,1.0)
    )
    val fish = ArrayList<Fish>(73)
    private val phases = DoubleArray(3)
    init {
        settings.forEachIndexed { school, s ->
            repeat(s[2].toInt()) {
                val angle = range(0.0, PI * 2)
                val radius = sqrt(random.nextDouble())
                val heading = s[3] + range(-.34,.34)
                val length = range(5.8,8.2) * s[6]
                val cruise = range(19.0,27.0) * s[7]
                fish += Fish(school, s[0] / 480 * 270 + cos(angle) * s[4] * radius,
                    s[1] / 270 * 540 + sin(angle) * s[5] * radius,
                    cos(heading) * cruise, sin(heading) * cruise, length,
                    length * range(.1,.32), cruise, range(0.0,PI*2), range(0.0,PI*2))
            }
            phases[school] = range(0.0,PI*2)
        }
    }
    fun resize(sx: Double, sy: Double) { fish.forEach { it.x *= sx; it.y *= sy } }

    fun update(dt: Double, time: Double, width: Double, height: Double) {
        var start = 0
        settings.forEachIndexed { school, setting ->
            val end = start + setting[2].toInt()
            var cx = 0.0; var cy = 0.0; var avx = 0.0; var avy = 0.0
            for (i in start until end) { val f = fish[i]; cx += f.x; cy += f.y; avx += f.vx; avy += f.vy }
            cx /= end - start; cy /= end - start
            val av = hypot(avx,avy)
            val schoolX = if (av > .001) avx/av else cos(setting[3])
            val schoolY = if (av > .001) avy/av else sin(setting[3])
            // ponytail: at most 34 fish per school; use a spatial grid only if that cap grows.
            for (i in start until end) {
                val f = fish[i]
                val speed = hypot(f.vx,f.vy)
                val fx = if (speed > .001) f.vx/speed else schoolX
                val fy = if (speed > .001) f.vy/speed else schoolY
                var sx = 0.0; var sy = 0.0; var ax = 0.0; var ay = 0.0
                var hx = 0.0; var hy = 0.0; var count = 0
                for (j in start until end) {
                    if (i == j) continue
                    val other = fish[j]; val dx = f.x-other.x; val dy = f.y-other.y
                    val distance = hypot(dx,dy)
                    if (distance <= .001 || distance >= 25) continue
                    count++; hx += other.x; hy += other.y
                    val velocity = hypot(other.vx,other.vy).coerceAtLeast(.001)
                    ax += other.vx/velocity; ay += other.vy/velocity
                    if (distance < 6.2) { val force = (6.2-distance)/6.2/distance; sx += dx*force; sy += dy*force }
                }
                hx = (if (count > 0) hx/count else cx) - f.x
                hy = (if (count > 0) hy/count else cy) - f.y
                val cohesion = hypot(hx,hy)
                if (cohesion > .001) { hx /= cohesion; hy /= cohesion } else { hx=schoolX; hy=schoolY }
                val alignment = hypot(ax,ay)
                if (alignment > .001) { ax /= alignment; ay /= alignment } else { ax=schoolX; ay=schoolY }
                val dx = f.x-cx; val dy = f.y-cy; val centerDistance = hypot(dx,dy)
                val swirlX = -(if (centerDistance > .001) dy/centerDistance else fy) * setting[8]
                val swirlY = (if (centerDistance > .001) dx/centerDistance else fx) * setting[8]
                val heading = atan2(fy,fx)
                val wander = heading + sin(time*.62+f.phase+phases[school])*.58 + sin(time*.19+f.phase*1.7)*.31
                var steerX = fx*.82 + cos(wander)*.34 + hx*.62 + ax*.56 + sx*2.8 + swirlX*.46
                var steerY = fy*.82 + sin(wander)*.34 + hy*.62 + ay*.56 + sy*2.8 + swirlY*.46
                steerX += ((14-f.x).coerceAtLeast(0.0) - (f.x-width+14).coerceAtLeast(0.0))/14*4.8
                steerY += ((14-f.y).coerceAtLeast(0.0) - (f.y-height+14).coerceAtLeast(0.0))/14*4.8
                val turn = atan2(sin(atan2(steerY,steerX)-heading), cos(atan2(steerY,steerX)-heading)).coerceIn(-2.4*dt,2.4*dt)
                val target = f.cruise * (1+sin(time*.83+f.phase)*.46)
                val nextSpeed = speed + (target-speed)*(1-exp(-4.7*dt))
                f.vx=cos(heading+turn)*nextSpeed; f.vy=sin(heading+turn)*nextSpeed
                // Hard guard outside the soft steering boundary for narrow/rotated screens.
                f.x=(f.x+f.vx*dt).coerceIn(0.0,width); f.y=(f.y+f.vy*dt).coerceIn(0.0,height)
                f.tail += (4.4+nextSpeed*.16)*dt
            }
            start=end
        }
    }
}
