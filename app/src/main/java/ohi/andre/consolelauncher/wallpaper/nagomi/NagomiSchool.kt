package ohi.andre.consolelauncher.wallpaper.nagomi

import kotlin.math.*
import kotlin.random.Random

/** Android adaptation of Nagomi's koi.ts, school.ts and math.ts.
 * Copyright 2026 Mayank Kadam. https://github.com/msk1039/nagomi
 * See docs/nagomi.md and LICENSES/Nagomi-PolyForm-Noncommercial.txt.
 * Changes: autonomous-only behavior, bounded notification fish, fixed-step clock.
 */
internal data class PondPoint(val x: Double = 0.0, val y: Double = 0.0) {
    operator fun plus(b: PondPoint) = PondPoint(x + b.x, y + b.y)
    operator fun minus(b: PondPoint) = PondPoint(x - b.x, y - b.y)
    operator fun times(s: Double) = PondPoint(x * s, y * s)
    fun normal() = PondPoint(-y, x)
    fun length() = hypot(x, y)
    fun unit(fallback: PondPoint = PondPoint(1.0, 0.0)): PondPoint =
        length().let { if (it > 0.0001) this * (1 / it) else fallback }
}

internal fun direction(angle: Double) = PondPoint(cos(angle), sin(angle))
private fun wrap(angle: Double) = atan2(sin(angle), cos(angle))

internal class NagomiSchool(seed: Int = 0xc0ffee) {
    companion object {
        const val MAX_FISH = 24
        const val BASE_FISH = 6
        const val NODES = 14
        const val LIFETIME = 180.0
        const val STEP = 1.0 / 60
    }
    private val random = Random(seed)
    private fun range(low: Double, high: Double) = low + random.nextDouble() * (high - low)
    var width = 270.0
        private set
    var height = 540.0
        private set
    var time = 0.0
        private set
    private var accumulated = 0.0
    private var lastMs: Long? = null
    private var serial = 0
    val fish = ArrayList<Koi>()
    val tiny = NagomiTinyFish()
    val ripples = ArrayList<Ripple>()

    internal class Koi(val id: Int, val accent: Int?, val marking: Int?, val ambient: Boolean) {
        var position = PondPoint()
        var velocity = PondPoint()
        val spine = Array(NODES) { PondPoint() }
        val renderSpine = Array(NODES) { PondPoint() }
        var heading = 0.0
        var angularVelocity = 0.0
        var speed = 0.0
        var cruiseSpeed = 20.0
        var maximumSpeed = 34.0
        var turnStrength = 5.0
        var bodyLength = 24.0
        var bodyWidth = 4.0
        var swimPhase = 0.0
        var phaseOffset = 0.0
        var wanderSeed = 0.0
        var state = 0 // Glide, Coast, Hover, Burst, Pivot
        var stateAge = 0.0
        var stateDuration = 2.0
        var pivotHeading = 0.0
        var tailEffort = 0.6
        var depth = 0.08
        var targetDepth = 0.08
        var depthRate = 1.0
        var depthAge = 0.0
        var depthDuration = 10.0
        var deep = false
        var age = 0.0
        var gulpCountdown = 3.0
        var gulpAnimation = 0.0
        var desired = PondPoint()
        val opacity get() = if (ambient) 1.0 else min(age / 1.5, (LIFETIME - age) / 4).coerceIn(0.0, 1.0)
    }
    internal data class Ripple(val point: PondPoint, var age: Double = 0.0, val mouth: Boolean = false)

    init { repeat(BASE_FISH) { spawn(ambient = true) } }

    fun spawn(accent: Int? = null, marking: Int? = null, ambient: Boolean = false): Koi {
        if (fish.size == MAX_FISH) fish.removeAt(fish.indexOfFirst { !it.ambient }.coerceAtLeast(0))
        val koi = Koi(serial++, accent, marking, ambient).apply {
            position = PondPoint(range(45.0, width - 45), range(32.0, height - 32))
            heading = range(-PI, PI)
            cruiseSpeed = range(13.0, 21.0)
            maximumSpeed = cruiseSpeed * range(1.55, 1.9)
            speed = cruiseSpeed * range(0.72, 1.05)
            turnStrength = range(4.4, 6.8)
            val tiny = ambient && id % 2 == 1
            bodyLength = if (tiny) range(16.0, 22.0) else range(27.0, 40.0)
            bodyWidth = bodyLength * if (tiny) range(0.18, 0.22) else range(0.17, 0.2)
            phaseOffset = range(0.0, PI * 2)
            swimPhase = phaseOffset
            wanderSeed = range(0.0, 100.0)
            depth = range(0.05, 0.18)
            targetDepth = depth
            depthRate = 3 / range(2.0, 5.0)
            depthDuration = range(8.0, 22.0)
            depthAge = range(0.0, depthDuration * 0.7)
            gulpCountdown = range(1.0, 4.0)
            state = id % 5
            pivotHeading = heading
            velocity = direction(heading) * speed
            for (node in spine.indices) {
                spine[node] = position + direction(heading) * (-bodyLength * node / (NODES - 1))
                renderSpine[node] = spine[node]
            }
        }
        fish += koi
        if (!ambient) ripple(koi.position)
        return koi
    }

    private fun ripple(point: PondPoint, mouth: Boolean = false) {
        if (ripples.size == 8) ripples.removeAt(0)
        ripples += Ripple(point, mouth = mouth)
    }

    fun resize(w: Double, h: Double) {
        require(w >= 100 && h >= 100)
        val sx = w / width
        val sy = h / height
        tiny.resize(sx, sy)
        fish.forEach { koi ->
            val next = PondPoint(koi.position.x * sx, koi.position.y * sy)
            val shift = next - koi.position
            koi.position = next
            for (i in koi.spine.indices) koi.spine[i] = koi.spine[i] + shift
        }
        width = w
        height = h
        ripples.clear()
    }

    fun resetClock() { lastMs = null }

    fun advance(nowMs: Long) {
        val previous = lastMs
        lastMs = nowMs
        if (previous == null) return
        accumulated += ((nowMs - previous).coerceAtLeast(0) / 1000.0).coerceAtMost(0.1)
        while (accumulated + 1e-9 >= STEP) {
            update(STEP)
            accumulated -= STEP
        }
    }

    private fun enter(k: Koi, state: Int) {
        k.state = state
        k.stateAge = 0.0
        k.stateDuration = when (state) {
            0 -> range(1.7, 5.2)
            1 -> range(0.7, 2.1)
            2 -> range(0.65, 3.1)
            3 -> range(0.32, 0.92)
            else -> range(0.3, 0.78)
        }
        if (state == 4) k.pivotHeading = wrap(k.heading + (if (random.nextBoolean()) 1 else -1) * range(0.85, 2.35))
    }

    private fun update(dt: Double) {
        time += dt
        tiny.update(dt, time, width, height)
        fish.removeAll { !it.ambient && it.age >= LIFETIME }
        ripples.forEach { it.age += dt }
        ripples.removeAll { it.age >= if (it.mouth) 0.9 else 2.2 }
        fish.forEach { k ->
            k.age += dt
            k.stateAge += dt
            if (k.stateAge >= k.stateDuration) {
                val roll = random.nextDouble()
                enter(k, when (k.state) {
                    0 -> when { roll < .25 -> 1; roll < .43 -> 2; roll < .61 -> 4; roll < .72 -> 3; else -> 0 }
                    1 -> when { roll < .38 -> 2; roll < .72 -> 0; roll < .9 -> 4; else -> 3 }
                    2 -> when { roll < .34 -> 4; roll < .55 -> 3; else -> 0 }
                    3 -> 1
                    else -> if (roll < .38) 3 else 0
                })
            }
            k.depthAge += dt
            if (k.depthAge >= k.depthDuration) {
                k.depthAge = 0.0
                if (random.nextDouble() < .72) k.deep = !k.deep
                k.targetDepth = if (k.deep) range(.45, .75) else range(.04, .22)
                k.depthDuration = if (k.deep) range(4.0, 10.0) else range(8.0, 22.0)
                k.depthRate = 3 / range(2.0, 5.0)
            }
            k.depth += (k.targetDepth - k.depth) * (1 - exp(-k.depthRate * dt))
            k.gulpAnimation = max(0.0, k.gulpAnimation - dt)
            k.gulpCountdown -= dt
            if (k.gulpCountdown <= 0) {
                if (k.state <= 2 && k.depth <= .23 && k.speed <= k.cruiseSpeed * .62) {
                    ripple(k.position + direction(k.heading) * (k.bodyWidth * .66), true)
                    k.gulpAnimation = .24
                    k.gulpCountdown = range(1.0, 4.0)
                } else k.gulpCountdown = range(.55, 1.35)
            }
            k.desired = steering(k)
        }
        fish.forEach { integrate(it, dt) }
    }

    private fun steering(k: Koi): PondPoint {
        val forward = direction(k.heading)
        var steering = forward * .95
        if (k.state == 4) steering = direction(k.pivotHeading) * 4.7
        else if (k.state != 2) {
            val wander = sin(time * .29 + k.wanderSeed) * .7 + sin(time * .113 + k.wanderSeed * 1.73) * .45
            steering += direction(k.heading + wander) * .62
        }
        var separation = PondPoint()
        var alignment = PondPoint()
        var cohesion = PondPoint()
        var neighbours = 0
        // ponytail: upstream pairwise steering is bounded at 24 fish; spatial bins only if that cap grows.
        fish.forEach { other ->
            if (other !== k) {
                val offset = k.position - other.position
                val distance = offset.length()
                if (distance > .001 && distance < 37) {
                    neighbours++
                    cohesion += other.position
                    alignment += other.velocity.unit()
                    if (distance < 14) separation += offset.unit() * ((14 - distance) / 14)
                }
            }
        }
        if (neighbours > 0) {
            steering += (cohesion * (1.0 / neighbours) - k.position).unit(forward) * .25
            steering += alignment.unit(forward) * .42
            steering += separation * 2.8
        }
        val p = k.position
        val ex = if (p.x < 32) (32 - p.x) / 32 else if (p.x > width - 32) -(p.x - (width - 32)) / 32 else 0.0
        val ey = if (p.y < 32) (32 - p.y) / 32 else if (p.y > height - 32) -(p.y - (height - 32)) / 32 else 0.0
        return (steering + PondPoint(ex, ey) * 4.8).unit(forward)
    }

    private fun integrate(k: Koi, dt: Double) {
        val pivot = k.state == 4
        val error = wrap(atan2(k.desired.y, k.desired.x) - k.heading)
        k.angularVelocity += (error * k.turnStrength * (if (pivot) 2.65 else 1.0) - k.angularVelocity * (if (pivot) 2.15 else 3.8)) * dt
        val turn = if (pivot) 4.35 else 2.25
        k.angularVelocity = k.angularVelocity.coerceIn(-turn, turn)
        k.heading = wrap(k.heading + k.angularVelocity * dt)
        val speed = when (k.state) { 1 -> k.cruiseSpeed * .28; 2 -> 0.0; 3 -> k.maximumSpeed * 1.08; 4 -> k.cruiseSpeed * .16; else -> k.cruiseSpeed }
        val response = when (k.state) { 1 -> 1.05; 2 -> 3.6; 3 -> 6.4; 4 -> 4.2; else -> 1.65 }
        val effort = when (k.state) { 1 -> .16; 2 -> .05; 3 -> 1.22; 4 -> 1.0; else -> .62 }
        k.speed += (speed - k.speed) * (1 - exp(-response * dt))
        k.tailEffort += (effort - k.tailEffort) * (1 - exp(-4.5 * dt))
        k.velocity = direction(k.heading) * k.speed
        k.position += k.velocity * dt
        k.swimPhase += (.45 + k.speed / k.maximumSpeed * 4.6 + k.tailEffort * .9) * dt
        k.spine[0] = k.position
        val spacing = k.bodyLength / (NODES - 1)
        for (node in 1 until NODES) {
            val direction = (k.spine[node] - k.spine[node - 1]).unit(direction(k.heading) * -1.0)
            val constrained = k.spine[node - 1] + direction * spacing
            val stiffness = .94 - node.toDouble() / (NODES - 1) * .17
            k.spine[node] += (constrained - k.spine[node]) * stiffness
        }
    }
}
