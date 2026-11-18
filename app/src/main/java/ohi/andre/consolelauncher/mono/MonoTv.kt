package ohi.andre.consolelauncher.mono

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import ohi.andre.consolelauncher.managers.media.Channels
import ohi.andre.consolelauncher.managers.mediaplayer.PlaybackEngine
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Live TV inside the media panel. The stream decodes at full rate into a texture under an opaque screen, and the
 * screen draws it ordered-dithered in ink, cropped to fill, at a capped frame rate. The player exists only while
 * something is on: [stop] releases it.
 */
@OptIn(UnstableApi::class)
internal class MonoTv(
    context: Context,
    private val ink: Int,
    private val paper: Int,
    private val reducedMotion: () -> Boolean,
    private val onChanged: () -> Unit,
) : FrameLayout(context) {
    private val texture = TextureView(context)
    private val screen = Screen()
    private var engine: PlaybackEngine? = null

    /** The channel on, or null when nothing plays. */
    var channel: Channels.Channel? = null
        private set
    var failed = false
        private set

    val live get() = engine != null
    /** A real live stream rather than a video at a link; unknown until it starts, so false while tuning. */
    val liveStream get() = engine?.player?.isCurrentMediaItemLive == true
    val waiting get() = engine?.player?.playbackState.let { it == Player.STATE_BUFFERING || it == Player.STATE_IDLE }

    init {
        addView(texture, LayoutParams(-1, -1))
        addView(screen, LayoutParams(-1, -1))
    }

    /** Plays [next] from where it was left, in the panel or full screen. */
    fun play(next: Channels.Channel) {
        stop()
        failed = false
        val created = PlaybackEngine(context, PlaybackEngine.Kind.VIDEO,
            onChanged = { post(onChanged) },
            onError = { post { failed = true; stop(); onChanged() } })
        created.player.setVideoTextureView(texture)
        created.player.setMediaItem(MediaItem.Builder().setUri(next.url).setMediaId(next.url).build(), Channels.spot(next.url))
        created.player.prepare()
        created.player.play()
        engine = created
        channel = next
        Channels.remember(context, next.url)
        screen.update()
    }

    fun stop() {
        val running = engine ?: return
        engine = null
        channel?.let { Channels.keepSpot(it.url, running.player) }
        running.player.clearVideoTextureView(texture)
        running.release()
        screen.update()
    }

    /** The picture, dithered. Draws nothing of its own between frames, so a paused stream holds its last one. */
    private inner class Screen : View(context) {
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        private var grab: Bitmap? = null
        private var out: Bitmap? = null
        private var pixels = IntArray(0)
        private var running = false
        private val frame = object : Runnable {
            override fun run() {
                invalidate()
                if (running) postDelayed(this, if (reducedMotion()) STILL_MS else FRAME_MS)
            }
        }

        init { setBackgroundColor(paper) }

        override fun onDraw(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val cell = max(2, (density * 1.5f).roundToInt())
            val w = width / cell
            val h = height / cell
            val size = engine?.player?.videoSize
            if (w <= 0 || h <= 0 || size == null || size.width == 0 || size.height == 0 || !texture.isAvailable) return
            // Cover: scale the frame so it fills the panel, then take the middle.
            val vw = size.width * size.pixelWidthHeightRatio
            val vh = size.height.toFloat()
            val scale = max(w / vw, h / vh)
            val gw = max(w, (vw * scale).roundToInt())
            val gh = max(h, (vh * scale).roundToInt())
            if (grab?.width != gw || grab?.height != gh) { grab?.recycle(); grab = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888) }
            if (out?.width != w || out?.height != h) { out?.recycle(); out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); pixels = IntArray(w * h) }
            val source = grab ?: return
            texture.getBitmap(source)
            val left = (gw - w) / 2
            val top = (gh - h) / 2
            source.getPixels(pixels, 0, w, left, top, w, h)
            for (i in pixels.indices) {
                val p = pixels[i]
                val luma = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
                pixels[i] = if (luma * 16 / 256 > BAYER[(i / w and 3) * 4 + (i % w and 3)]) ink else paper
            }
            val image = out ?: return
            image.setPixels(pixels, 0, w, 0, 0, w, h)
            val x = (width - w * cell) / 2
            val y = (height - h * cell) / 2
            canvas.drawBitmap(image, Rect(0, 0, w, h), Rect(x, y, x + w * cell, y + h * cell), paint)
        }

        override fun onAttachedToWindow() { super.onAttachedToWindow(); update() }
        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); update() }
        override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); update() }

        fun update() {
            val should = live && isAttachedToWindow && windowVisibility == VISIBLE
            if (should == running) return
            running = should
            removeCallbacks(frame)
            if (running) post(frame) else invalidate()
        }
    }

    private companion object {
        val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)
        /** Twelve frames a second: the decoder runs at full rate, the dither does not need to. */
        const val FRAME_MS = 83L
        /** Under reduced motion the picture still changes, once a second. */
        const val STILL_MS = 1_000L
    }
}
