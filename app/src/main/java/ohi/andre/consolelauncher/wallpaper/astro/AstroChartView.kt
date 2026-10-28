package ohi.andre.consolelauncher.wallpaper.astro

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperSettings
import java.util.concurrent.Executors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Astro Chart: a planet and a busy station at the centre of the screen, small craft docking and
 * landing, and big industrial ships crossing at different depths. Painted on a low-resolution grid
 * (about 270 chart pixels across a phone) and scaled up with nearest sampling so pixels stay crisp.
 *
 * A new world is prepared on a background thread behind a short boot screen: textures, the nebula,
 * the planet's first shading, every ship sprite at every depth and heading, and a minute of traffic.
 */
class AstroChartView(context: Context) : View(context) {
    companion object {
        const val SCENE = "astro chart"
        const val FRAME_DELAY_MS = 50L
        private const val STEP = 1f / 20f
        private const val GRID = 270f
        private const val WARM_UP_STEPS = 60 * 20
        private const val MIN_BOOT_MS = 1600L
        private const val ONLINE_MS = 500L
        private val preparer = Executors.newSingleThreadExecutor { r -> Thread(r, "astro-chart").apply { isDaemon = true } }
    }

    var seed = RetuiWallpaperSettings.astroSeed(context)
        private set
    val worldName get() = AstroWorld.nameOf(seed)

    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private var scene: Scene? = null
    @Volatile private var prepared: Scene? = null
    @Volatile private var stage = 0
    @Volatile private var stageProgress = 0f
    @Volatile private var generation = 0
    private var preparing: Triple<Int, Int, Int>? = null  // seed, grid width, grid height being prepared
    private var bootStarted = 0L
    private var readyAt = 0L
    private var boot: AstroFrame? = null
    private var bootBitmap: Bitmap? = null
    private var lastFrameMs = 0L
    private var pending = 0f
    private var running = false
    private val frameLoop = object : Runnable {
        override fun run() {
            if (!running) return
            advance()
            invalidate()
            postDelayed(this, FRAME_DELAY_MS)
        }
    }

    /** One world at one grid size. Built off the main thread; only rendered on it. */
    private class Scene(seed: Int, val cell: Int, val gw: Int, val gh: Int, report: (Int, Float) -> Unit) {
        val world: AstroWorld
        val base: IntArray
        val frame = AstroFrame(gw, gh)
        val bitmap: Bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        val station: AstroStation
        val traffic: SmallTraffic
        private val fleet: FlybyFleet
        private val flybyRng = Random(seed * 31 + 5)
        val flybys = ArrayList<Flyby>()
        private var nextFlyby = 0f
        var time = 0f
        var frames = 0

        init {
            report(0, 0f); world = AstroWorld(seed, gw, gh)
            report(1, 0f); base = world.background()
            report(2, 0f); world.preparePlanet(WARM_UP_STEPS * STEP)
            report(3, 0f); fleet = FlybyFleet(seed, world.neb[1]) { report(3, it) }
            station = AstroStation(world.px.toInt().toFloat(), world.py.toInt().toFloat())
            report(4, 0f); val hulls = SmallHullSet(seed) { report(4, it) }
            traffic = SmallTraffic(seed, station, world.px, world.py, AstroWorld.PLANET_RADIUS.toFloat(), gw, gh, hulls)
            report(5, 0f)
            for (i in 0 until WARM_UP_STEPS) {  // start with the lanes already busy
                step()
                if (i % 60 == 0) report(5, i / WARM_UP_STEPS.toFloat())
            }
            report(6, 1f)
        }

        fun step() {
            time += STEP
            traffic.step(STEP)
            if (time >= nextFlyby && flybys.size < 6) {
                flybys += Flyby(flybyRng, flybys, gw, gh, station.sy, fleet)
                flybys.sortBy { it.z }  // paint far to near
                nextFlyby = time + 2.5f + flybyRng.nextFloat() * 5.5f
            }
            flybys.forEach { it.step(STEP) }
            flybys.removeAll { it.gone }
        }

        fun render() {
            val f = frame
            System.arraycopy(base, 0, f.px, 0, base.size)
            val t = time
            frames++
            world.drawStars(f, t)
            flybys.forEach { if (it.far) it.draw(f, t, frames) }
            world.drawRings(f, t, front = false)
            world.drawPlanet(f, t)
            world.drawRings(f, t, front = true)
            traffic.ships.forEach { it.drawTrails(f) }
            station.draw(f, t)
            traffic.ships.forEach { it.draw(f, frames) }
            flybys.forEach { if (!it.far) it.draw(f, t, frames) }
            bitmap.setPixels(f.px, 0, gw, 0, 0, gw, gh)
        }
    }

    private fun grid(): Triple<Int, Int, Int>? {
        if (width <= 0 || height <= 0) return null
        val cell = max(1, (min(width, height) / GRID).roundToInt())
        return Triple(cell, ceil(width / cell.toFloat()).toInt(), ceil(height / cell.toFloat()).toInt())
    }

    /** The ready scene for the current seed and size, or null while one is being prepared (and the boot screen shows). */
    private fun currentScene(now: Long): Scene? {
        val (cell, gw, gh) = grid() ?: return null
        scene?.let { if (it.cell == cell && it.gw == gw && it.gh == gh) return it }
        if (preparing != Triple(seed, gw, gh)) startPreparing(cell, gw, gh, now)
        val done = prepared ?: return null
        // hold the boot screen for a moment so it reads as a boot, then show "system online" briefly
        if (readyAt == 0L) readyAt = max(now, bootStarted + MIN_BOOT_MS)
        if (now < readyAt + ONLINE_MS) return null
        scene?.bitmap?.recycle()
        scene = done
        prepared = null
        preparing = null
        resetClock()
        return done
    }

    private fun startPreparing(cell: Int, gw: Int, gh: Int, now: Long) {
        val job = ++generation
        val worldSeed = seed
        preparing = Triple(worldSeed, gw, gh)
        prepared?.bitmap?.recycle()
        prepared = null
        stage = 0; stageProgress = 0f
        bootStarted = now; readyAt = 0L
        preparer.execute {
            if (job != generation) return@execute
            val built = Scene(worldSeed, cell, gw, gh) { s, p -> if (job == generation) { stage = s; stageProgress = p } }
            if (job == generation) prepared = built else built.bitmap.recycle()
        }
    }

    /** Advance the simulation by real elapsed time in fixed steps. A long gap (hidden, screen off) is not replayed. */
    fun advance(nowMs: Long = SystemClock.uptimeMillis()) {
        val s = currentScene(nowMs) ?: return
        if (lastFrameMs != 0L) pending += (nowMs - lastFrameMs).coerceIn(0L, 250L) / 1000f
        lastFrameMs = nowMs
        var steps = 0
        while (pending >= STEP && steps < 5) { s.step(); pending -= STEP; steps++ }
        if (steps == 5) pending = 0f
    }

    fun resetClock() { lastFrameMs = 0L; pending = 0f }

    private fun dropScene() {
        scene?.bitmap?.recycle()
        scene = null
        preparing = null
        invalidate()
    }

    /** Step to the next kind of world: a new planet, nebula, stars and rings. */
    fun reseed() { seed += 1; dropScene() }

    /** Jump to a particular kind of world (rose sea, dune, ...). */
    fun showWorld(index: Int) {
        if (index < 0 || Math.floorMod(seed, AstroWorld.PALETTES.size) == index) return
        seed += Math.floorMod(index - seed, AstroWorld.PALETTES.size)
        dropScene()
    }

    fun loadSeed() {
        val saved = RetuiWallpaperSettings.astroSeed(context)
        if (saved != seed) { seed = saved; dropScene() }
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        val s = currentScene(now)
        canvas.drawColor(Color.BLACK)
        if (s != null) {
            s.render()
            blit(canvas, s.bitmap, s.gw, s.gh, s.cell)
            return
        }
        val (cell, gw, gh) = grid() ?: return
        val f = boot?.takeIf { it.w == gw && it.h == gh } ?: AstroFrame(gw, gh).also {
            boot = it
            bootBitmap?.recycle()
            bootBitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
        }
        val bitmap = bootBitmap ?: return
        val shownStage = if (prepared != null) AstroBoot.STAGES.size else stage
        AstroBoot.draw(f, (now - bootStarted) / 1000f, shownStage, stageProgress, worldName)
        bitmap.setPixels(f.px, 0, gw, 0, 0, gw, gh)
        blit(canvas, bitmap, gw, gh, cell)
    }

    private fun blit(canvas: Canvas, bitmap: Bitmap, gw: Int, gh: Int, cell: Int) {
        val w = gw * cell; val h = gh * cell
        val left = (width - w) / 2; val top = (height - h) / 2
        canvas.drawBitmap(bitmap, Rect(0, 0, gw, gh), Rect(left, top, left + w, top + h), paint)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updateAnimation() }

    /** Inside an activity (the picker preview) the view runs its own frame loop; the wallpaper service drives it otherwise. */
    private fun updateAnimation() {
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus()
        removeCallbacks(frameLoop)
        resetClock()
        if (running) post(frameLoop)
    }

    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }

    fun release() {
        running = false
        removeCallbacks(frameLoop)
        generation++  // abandon any world still being prepared
        scene?.bitmap?.recycle(); scene = null
        prepared?.bitmap?.recycle(); prepared = null
        preparing = null
        bootBitmap?.recycle(); bootBitmap = null; boot = null
    }

    @RequiresApi(27)
    fun wallpaperColors(): WallpaperColors {
        val colours = AstroWorld.signature(seed)
        return WallpaperColors(Color.valueOf(colours[0]), Color.valueOf(colours[1]), Color.valueOf(colours[2]))
    }
}
