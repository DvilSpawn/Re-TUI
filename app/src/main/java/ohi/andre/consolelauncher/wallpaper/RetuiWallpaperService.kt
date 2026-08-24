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
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable

internal fun shouldScheduleWallpaperFrame(
    visible: Boolean,
    fullRedrawPending: Boolean,
    animated: Boolean
): Boolean = visible && (fullRedrawPending || animated)

class RetuiWallpaperService : WallpaperService() {
    companion object {
        const val ACTION_REFRESH = "com.dvil.tui_renewed.action.REFRESH_WALLPAPER"
    }

    override fun onCreate() {
        super.onCreate()
        XMLPrefsManager.loadCommons(this)
    }

    override fun onCreateEngine(): Engine = RetuiEngine()

    private inner class RetuiEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val powerManager = getSystemService(PowerManager::class.java)
        private val crtOverlay = CrtOverlayDrawable(this@RetuiWallpaperService).apply {
            setAccentColor(LauncherSettings.getColor(Theme.output_text_color))
        }
        private var view: View = createView()
        private var visible = false
        private var fullRedrawPending = true
        private var receiverRegistered = false
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> handler.removeCallbacks(drawFrame)
                    Intent.ACTION_SCREEN_ON -> scheduleIfVisible()
                    ACTION_REFRESH -> refreshView(recreate = true)
                }
            }
        }
        private val drawFrame = object : Runnable {
            override fun run() {
                if (!visible) return
                if (canDraw() && draw(fullSurface = fullRedrawPending)) fullRedrawPending = false
                if (shouldScheduleWallpaperFrame(visible, fullRedrawPending, view !is SolidColorView)) {
                    handler.postDelayed(this, frameDelayMs())
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
                    addAction(ACTION_REFRESH)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(drawFrame)
            if (visible) refreshView(recreate = false)
        }

        @RequiresApi(27)
        override fun onComputeColors(): WallpaperColors = when (val current = view) {
            is BlackHoleView -> current.wallpaperColors()
            is CsakuraView -> current.wallpaperColors()
            else -> (current as SolidColorView).wallpaperColors()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            layoutView(width, height)
            fullRedrawPending = !draw(fullSurface = true)
            scheduleIfVisible()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            fullRedrawPending = !draw(fullSurface = true)
            scheduleIfVisible()
        }

        private fun layoutView(width: Int, height: Int) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler.removeCallbacks(drawFrame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawFrame)
            releaseView()
            if (receiverRegistered) {
                unregisterReceiver(screenReceiver)
                receiverRegistered = false
            }
            super.onDestroy()
        }

        private fun scheduleIfVisible() {
            handler.removeCallbacks(drawFrame)
            if (shouldScheduleWallpaperFrame(visible, fullRedrawPending, view !is SolidColorView)) {
                handler.post(drawFrame)
            }
        }

        private fun refreshView(recreate: Boolean) {
            if (!visible) return
            val selected = RetuiWallpaperSettings.scene(this@RetuiWallpaperService)
            if (recreate || !viewMatchesScene(selected)) {
                releaseView()
                view = createView()
                layoutView(surfaceHolder.surfaceFrame.width(), surfaceHolder.surfaceFrame.height())
            } else {
                loadPosition()
            }
            fullRedrawPending = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
            scheduleIfVisible()
        }

        private fun canDraw(): Boolean = visible &&
            powerManager.isInteractive &&
            surfaceHolder.surface.isValid

        private fun draw(fullSurface: Boolean = false): Boolean {
            val current = view
            val canvas = try {
                if (current is CsakuraView && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    surfaceHolder.lockHardwareCanvas()
                } else if (!fullSurface && current is BlackHoleView) {
                    surfaceHolder.lockCanvas(current.animationBounds())
                } else {
                    surfaceHolder.lockCanvas()
                }
            } catch (_: Exception) { null } ?: return false
            try {
                when (current) {
                    is BlackHoleView -> current.advance()
                    is CsakuraView -> current.advance()
                }
                current.draw(canvas)
                if (AppearanceSettings.crtFilter()) {
                    crtOverlay.setBounds(0, 0, current.width, current.height)
                    crtOverlay.draw(canvas)
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
            return true
        }

        private fun frameDelayMs() = when (view) {
            is BlackHoleView -> BlackHoleView.FRAME_DELAY_MS
            else -> 1000L / CsakuraView.FPS
        }

        private fun releaseView() {
            when (val current = view) {
                is BlackHoleView -> current.release()
                is CsakuraView -> current.release()
            }
        }

        private fun createView(): View = when (RetuiWallpaperSettings.scene(this@RetuiWallpaperService)) {
            "black hole" -> BlackHoleView(this@RetuiWallpaperService).apply { loadPosition() }
            "solid" -> SolidColorView(this@RetuiWallpaperService)
            else -> CsakuraView(this@RetuiWallpaperService).apply { loadPosition() }
        }

        private fun viewMatchesScene(scene: String): Boolean = when (scene) {
            "black hole" -> view is BlackHoleView
            "solid" -> view is SolidColorView
            else -> view is CsakuraView
        }

        private fun loadPosition() = when (val current = view) {
            is BlackHoleView -> current.loadPosition()
            is CsakuraView -> current.loadPosition()
            else -> Unit
        }
    }
}
