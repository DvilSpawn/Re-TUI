package ohi.andre.consolelauncher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.app.KeyguardManager
import android.service.wallpaper.WallpaperService
import android.app.WallpaperColors
import android.view.SurfaceHolder
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable

internal fun shouldScheduleWallpaperFrame(
    visible: Boolean,
    fullRedrawPending: Boolean,
    animated: Boolean
): Boolean = visible && (fullRedrawPending || animated)

internal fun shouldRenderWallpaper(
    visible: Boolean,
    interactionEnabled: Boolean,
    screenInteractive: Boolean,
    keyguardLocked: Boolean,
    wallpaperPreview: Boolean = false
): Boolean = visible && screenInteractive && (interactionEnabled || keyguardLocked || wallpaperPreview)

internal fun wallpaperFrameDelay(drawSucceeded: Boolean, normalDelayMs: Long): Long =
    if (drawSucceeded) normalDelayMs else maxOf(normalDelayMs, 1000L)

class RetuiWallpaperService : WallpaperService() {
    override fun getResources(): android.content.res.Resources =
        ohi.andre.consolelauncher.localization.LanguagePacks.resources(super.getResources())

    companion object {
        const val ACTION_REFRESH = "com.dvil.tui_renewed.action.REFRESH_WALLPAPER"
        const val ACTION_INTERACTION = "com.dvil.tui_renewed.action.WALLPAPER_INTERACTION"
        const val EXTRA_INTERACTION_ENABLED = "interaction_enabled"
    }

    override fun onCreate() {
        super.onCreate()
        XMLPrefsManager.loadCommons(this)
    }

    override fun onCreateEngine(): Engine = RetuiEngine()

    private inner class RetuiEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val powerManager = getSystemService(PowerManager::class.java)
        private val keyguardManager = getSystemService(KeyguardManager::class.java)
        private val crtOverlay = CrtOverlayDrawable(this@RetuiWallpaperService).apply {
            setAccentColor(LauncherSettings.getColor(Theme.output_text_color))
        }
        private var view: View = createView()
        private var visible = false
        private var interactionEnabled = false
        private var fullRedrawPending = true
        private var receiverRegistered = false
        private var mediaReceiverRegistered = false
        private var mediaPlaying = false
        private val localBroadcastManager = LocalBroadcastManager.getInstance(applicationContext)
        private val mediaReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                mediaPlaying = intent?.getBooleanExtra(MusicService.MUSIC_PLAYING, false) ?: false
                (view as? PixelDreamView)?.setMediaPlaying(mediaPlaying)
            }
        }
        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> setInteractionEnabled(false)
                    Intent.ACTION_SCREEN_ON -> {
                        updateTouchHandling()
                        scheduleIfVisible()
                    }
                    ACTION_REFRESH -> refreshView(recreate = true)
                    ACTION_INTERACTION -> setInteractionEnabled(
                        intent.getBooleanExtra(EXTRA_INTERACTION_ENABLED, false)
                    )
                }
            }
        }
        private val drawFrame = object : Runnable {
            override fun run() {
                if (!shouldRender()) return
                val drawSucceeded = canDraw() && draw(fullSurface = fullRedrawPending)
                if (drawSucceeded) fullRedrawPending = false
                if (shouldScheduleWallpaperFrame(shouldRender(), fullRedrawPending, isAnimated())) {
                    handler.postDelayed(this, wallpaperFrameDelay(drawSucceeded, frameDelayMs()))
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Register once. Changing window touch flags from a visibility callback can
            // synchronously re-enter that callback; filter input in onTouchEvent instead.
            setTouchEventsEnabled(true)
            ContextCompat.registerReceiver(
                this@RetuiWallpaperService,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(ACTION_REFRESH)
                    addAction(ACTION_INTERACTION)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            localBroadcastManager.registerReceiver(
                mediaReceiver,
                IntentFilter(MusicService.ACTION_MUSIC_CHANGED)
            )
            mediaReceiverRegistered = true
            updateTouchHandling()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (!visible) interactionEnabled = false
            updateTouchHandling()
            handler.removeCallbacks(drawFrame)
            if (visible) refreshView(recreate = false)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!touchAllowed()) return
            val current = view
            if (current is PixelDreamView) current.handleTouch(event)
        }

        @RequiresApi(27)
        override fun onComputeColors(): WallpaperColors = when (val current = view) {
            is BlackHoleView -> current.wallpaperColors()
            is CsakuraView -> current.wallpaperColors()
            is TopoNoiseView -> current.wallpaperColors()
            is PixelDreamView -> current.wallpaperColors()
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
            updateTouchHandling()
            handler.removeCallbacks(drawFrame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(drawFrame)
            visible = false
            interactionEnabled = false
            updateTouchHandling()
            releaseView()
            if (receiverRegistered) {
                unregisterReceiver(screenReceiver)
                receiverRegistered = false
            }
            if (mediaReceiverRegistered) {
                localBroadcastManager.unregisterReceiver(mediaReceiver)
                mediaReceiverRegistered = false
            }
            super.onDestroy()
        }

        private fun scheduleIfVisible() {
            handler.removeCallbacks(drawFrame)
            if (shouldScheduleWallpaperFrame(shouldRender(), fullRedrawPending, isAnimated())) {
                handler.post(drawFrame)
            }
        }

        private fun shouldRender(): Boolean = shouldRenderWallpaper(
            visible, interactionEnabled, powerManager.isInteractive, keyguardManager.isKeyguardLocked,
            wallpaperPreview = isPreview
        )

        private fun touchAllowed(): Boolean = shouldRenderWallpaper(
            visible, interactionEnabled, powerManager.isInteractive, keyguardManager.isKeyguardLocked
        )

        private fun updateTouchHandling() {
            val enabled = touchAllowed() && view is PixelDreamView
            (view as? PixelDreamView)?.setInteractionEnabled(enabled)
        }

        private fun setInteractionEnabled(enabled: Boolean) {
            interactionEnabled = enabled
            updateTouchHandling()
            scheduleIfVisible()
        }

        private fun refreshView(recreate: Boolean) {
            val selected = RetuiWallpaperSettings.scene(this@RetuiWallpaperService)
            if (recreate || !viewMatchesScene(selected)) {
                releaseView()
                view = createView()
                updateTouchHandling()
                val frame = surfaceHolder.surfaceFrame
                if (frame.width() > 0 && frame.height() > 0) {
                    layoutView(frame.width(), frame.height())
                }
            } else {
                loadPosition()
            }
            fullRedrawPending = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) notifyColorsChanged()
            scheduleIfVisible()
        }

        private fun canDraw(): Boolean = shouldRender() && surfaceHolder.surface.isValid

        private fun draw(fullSurface: Boolean = false): Boolean {
            val current = view
            val lockSoftware = {
                if (!fullSurface && current is BlackHoleView) {
                    surfaceHolder.lockCanvas(current.animationBounds())
                } else {
                    surfaceHolder.lockCanvas()
                }
            }
            val canvas = try { lockSoftware() } catch (_: Exception) { null } ?: return false
            try {
                when (current) {
                    is BlackHoleView -> current.advance()
                    is CsakuraView -> current.advance()
                    is PixelDreamView -> current.advance()
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
            is PixelDreamView -> 50L
            else -> 1000L / CsakuraView.FPS
        }

        private fun releaseView() {
            when (val current = view) {
                is BlackHoleView -> current.release()
                is CsakuraView -> current.release()
                is TopoNoiseView -> current.release()
            }
        }

        private fun createView(): View = when (RetuiWallpaperSettings.scene(this@RetuiWallpaperService)) {
            "black hole" -> BlackHoleView(this@RetuiWallpaperService).apply { loadPosition() }
            "solid" -> SolidColorView(this@RetuiWallpaperService)
            TopoNoiseView.SCENE -> TopoNoiseView(this@RetuiWallpaperService).apply { loadPosition() }
            PixelDreamView.SCENE -> PixelDreamView(this@RetuiWallpaperService).apply {
                loadPosition()
                setMediaPlaying(mediaPlaying)
            }
            else -> CsakuraView(this@RetuiWallpaperService).apply { loadPosition() }
        }

        private fun viewMatchesScene(scene: String): Boolean = when (scene) {
            "black hole" -> view is BlackHoleView
            "solid" -> view is SolidColorView
            TopoNoiseView.SCENE -> view is TopoNoiseView
            PixelDreamView.SCENE -> view is PixelDreamView
            else -> view is CsakuraView
        }

        private fun isAnimated(): Boolean = view is BlackHoleView || view is CsakuraView || view is PixelDreamView

        private fun loadPosition() = when (val current = view) {
            is BlackHoleView -> current.loadPosition()
            is CsakuraView -> current.loadPosition()
            is TopoNoiseView -> current.loadPosition()
            is PixelDreamView -> current.loadPosition()
            else -> Unit
        }
    }
}
