package ohi.andre.consolelauncher.wallpaper.nagomi

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import java.io.File

internal fun nagomiFrameDelay(success: Boolean, drawMs: Long): Long =
    if (success) (NagomiView.FRAME_MS - drawMs.coerceAtLeast(0)).coerceAtLeast(1) else 1000L

internal class NagomiView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    companion object { const val SCENE = "nagomi"; const val FRAME_MS = 33L }
    internal val renderer = NagomiRenderer(context)
    var pondBackground: File?
        get() = renderer.background
        set(value) { renderer.background = value }
    private var surface: Surface? = null
    private var resumed = false
    private val frame = object : Runnable {
        override fun run() {
            if (!resumed || !isAttachedToWindow || windowVisibility != View.VISIBLE) return
            val current = surface ?: return
            val started = SystemClock.uptimeMillis()
            val success = renderer.draw(current, width, height)
            postDelayed(this, nagomiFrameDelay(success, SystemClock.uptimeMillis() - started))
        }
    }

    init { surfaceTextureListener = this; isOpaque = true }

    fun setResumed(value: Boolean) {
        resumed = value
        restart()
    }

    private fun restart() {
        removeCallbacks(frame)
        renderer.resetClock()
        if (resumed && isAttachedToWindow && windowVisibility == View.VISIBLE && surface != null) post(frame)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NagomiNotifications.previews++
        restart()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        NagomiNotifications.previews = (NagomiNotifications.previews - 1).coerceAtLeast(0)
        renderer.releaseSurface()
        surface?.release(); surface = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        restart()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        restart()
    }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = restart()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        removeCallbacks(frame)
        renderer.releaseSurface()
        surface?.release(); surface = null
        return true
    }
}
