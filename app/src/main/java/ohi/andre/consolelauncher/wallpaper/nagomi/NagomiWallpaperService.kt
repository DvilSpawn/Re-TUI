package ohi.andre.consolelauncher.wallpaper.nagomi

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperService
import ohi.andre.consolelauncher.wallpaper.shouldRenderWallpaper

/** Dedicated EGL surface: Android's Canvas wallpapers retain their existing service. */
class NagomiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PondEngine()

    private inner class PondEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = NagomiRenderer(this@NagomiWallpaperService)
        private val power = getSystemService(PowerManager::class.java)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private var visible = false
        private var interaction = false
        private var hasSurface = false
        private var registered = false
        private val frame = object : Runnable {
            override fun run() {
                if (!hasSurface || !visible || !power.isInteractive) return
                val rect = surfaceHolder.surfaceFrame
                val started = SystemClock.uptimeMillis()
                val success = renderer.draw(surfaceHolder.surface, rect.width(), rect.height())
                if (renderAllowed()) handler.postDelayed(this, nagomiFrameDelay(success, SystemClock.uptimeMillis() - started))
            }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> interaction = false
                    RetuiWallpaperService.ACTION_INTERACTION -> interaction = intent.getBooleanExtra(RetuiWallpaperService.EXTRA_INTERACTION_ENABLED, false)
                    RetuiWallpaperService.ACTION_REFRESH -> {
                        renderer.background = NagomiBackground.saved(this@NagomiWallpaperService).takeIf { it.isFile }
                        // The saved image can change in-place while its path stays the same.
                        renderer.releaseSurface()
                    }
                }
                schedule()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            ContextCompat.registerReceiver(this@NagomiWallpaperService, receiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(RetuiWallpaperService.ACTION_INTERACTION)
                addAction(RetuiWallpaperService.ACTION_REFRESH)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            NagomiNotifications.engines++
        }

        private fun renderAllowed() = hasSurface && shouldRenderWallpaper(visible, interaction, power.isInteractive, keyguard.isKeyguardLocked, isPreview)
        private fun schedule() {
            handler.removeCallbacks(frame)
            renderer.resetClock()
            // A visible, paused surface still needs its first image after creation/redraw.
            if (hasSurface && visible && power.isInteractive) handler.post(frame)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            // Binding can happen after the launcher's focus broadcast has already been sent.
            interaction = visible && ohi.andre.consolelauncher.LauncherActivity.instance?.hasWindowFocus() == true
            schedule()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            hasSurface = true
            schedule()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) = schedule()

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            hasSurface = false
            handler.removeCallbacks(frame)
            renderer.releaseSurface()
            super.onSurfaceDestroyed(holder)
        }

        @RequiresApi(27)
        override fun onComputeColors() = WallpaperColors(Color.valueOf(0xff345e52.toInt()), Color.valueOf(0xff91b5a6.toInt()), null)

        override fun onDestroy() {
            handler.removeCallbacks(frame)
            renderer.releaseSurface()
            if (registered) {
                unregisterReceiver(receiver)
                NagomiNotifications.engines = (NagomiNotifications.engines - 1).coerceAtLeast(0)
            }
            super.onDestroy()
        }
    }
}
