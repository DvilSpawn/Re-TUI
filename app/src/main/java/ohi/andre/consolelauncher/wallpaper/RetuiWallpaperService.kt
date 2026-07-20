package ohi.andre.consolelauncher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.app.WallpaperColors
import android.view.SurfaceHolder
import android.view.View
import androidx.core.content.ContextCompat

class RetuiWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = CsakuraEngine()

    private inner class CsakuraEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val powerManager = getSystemService(PowerManager::class.java)
        private val view = CsakuraView(this@RetuiWallpaperService).apply { loadPosition() }
        private var visible = false
        private var receiverRegistered = false
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(drawFrame)
                    Intent.ACTION_SCREEN_ON -> scheduleIfVisible()
                }
            }
        }
        private val drawFrame = object : Runnable {
            override fun run() {
                if (!visible || !powerManager.isInteractive) return
                draw()
                if (visible && powerManager.isInteractive) {
                    handler.postDelayed(this, 1000L / 24L)
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            ContextCompat.registerReceiver(
                this@RetuiWallpaperService,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawFrame)
            if (visible) {
                view.loadPosition()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    notifyColorsChanged()
                }
            }
            scheduleIfVisible()
        }

        override fun onComputeColors(): WallpaperColors = view.wallpaperColors()

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawFrame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawFrame)
            if (receiverRegistered) {
                unregisterReceiver(screenReceiver)
                receiverRegistered = false
            }
            super.onDestroy()
        }

        private fun scheduleIfVisible() {
            if (visible && powerManager.isInteractive) {
                handler.removeCallbacks(drawFrame)
                handler.post(drawFrame)
            }
        }

        private fun draw() {
            val canvas = try { surfaceHolder.lockCanvas() } catch (_: Exception) { null } ?: return
            try {
                view.advance()
                view.draw(canvas)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }
    }
}
