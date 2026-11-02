package ohi.andre.consolelauncher.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.PowerManager
import android.app.WallpaperManager
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
import ohi.andre.consolelauncher.wallpaper.astro.AstroChartView
import ohi.andre.consolelauncher.wallpaper.pixel.DoomFireView
import ohi.andre.consolelauncher.wallpaper.pixel.PhotoDitherView
import ohi.andre.consolelauncher.wallpaper.pixel.PixelWallpaperView
import ohi.andre.consolelauncher.wallpaper.pixel.RipplesView
import ohi.andre.consolelauncher.wallpaper.pixel.ShorelineView

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

internal fun shouldHandleWallpaperTouch(
    visible: Boolean,
    interactionEnabled: Boolean,
    screenInteractive: Boolean,
    keyguardLocked: Boolean,
    touchEnabled: Boolean
): Boolean = shouldRenderWallpaper(visible, interactionEnabled, screenInteractive, keyguardLocked) &&
    (touchEnabled || keyguardLocked)

class RetuiWallpaperService : WallpaperService() {
    override fun getResources(): android.content.res.Resources =
        ohi.andre.consolelauncher.localization.LanguagePacks.resources(super.getResources())

    companion object {
        const val ACTION_REFRESH = "com.dvil.tui_renewed.action.REFRESH_WALLPAPER"
        const val ACTION_INTERACTION = "com.dvil.tui_renewed.action.WALLPAPER_INTERACTION"
        const val ACTION_TOUCH_INTERACTION = "com.dvil.tui_renewed.action.WALLPAPER_TOUCH_INTERACTION"
        const val EXTRA_INTERACTION_ENABLED = "interaction_enabled"
        const val EXTRA_TOUCH_ENABLED = "touch_enabled"

        /**
         * The launcher's last interaction state. An engine created after the launcher already has focus
         * (for example after the app restarts) would otherwise miss the broadcast and stay frozen.
         */
        @Volatile private var launcherInteraction = false

        /** The user applied the staged choice: it replaces the running wallpaper's settings and the wallpaper redraws. */
        fun applyStaged(context: Context) {
            RetuiWallpaperSettings.commitPending(context)
            PhotoDitherView.commitPending(context)
            context.sendBroadcast(Intent(ACTION_REFRESH).setPackage(context.packageName))
        }

        fun setInteraction(context: Context, enabled: Boolean) {
            launcherInteraction = enabled
            context.sendBroadcast(Intent(ACTION_INTERACTION).setPackage(context.packageName).putExtra(EXTRA_INTERACTION_ENABLED, enabled))
        }
    }

    // Some systems (vivo's theme app) go straight home after applying, so the picker never hears the result. The
    // system announces the change here instead: if Re:TUI is the wallpaper and a choice was just sent to the apply
    // page, the user applied it.
    private val wallpaperChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val ours = runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.component?.className }.getOrNull() ==
                RetuiWallpaperService::class.java.name
            val staged = RetuiWallpaperSettings.applyInProgress(context)
            android.util.Log.i("TUI-Wallpaper", "wallpaper changed ours=$ours staged=$staged")
            if (ours && staged) applyStaged(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        XMLPrefsManager.loadCommons(this)
        @Suppress("DEPRECATION")
        ContextCompat.registerReceiver(this, wallpaperChanged, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED), ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(wallpaperChanged) }
        super.onDestroy()
    }

    override fun onCreateEngine(): Engine = RetuiEngine()

    private inner class RetuiEngine : Engine(), WallpaperPreviewSource.Source {
        override fun capturePreview(result: (android.graphics.Bitmap?)->Unit) {
            if (Build.VERSION.SDK_INT>=24) {
                val rect=surfaceHolder.surfaceFrame
                if (!surfaceHolder.surface.isValid) { result(null);return }
                val bitmap=WallpaperPreviewSource.bitmap(rect.width(),rect.height())
                if (bitmap==null) { result(null);return }
                try {
                    android.view.PixelCopy.request(surfaceHolder.surface,bitmap,{ status ->
                        if (status==android.view.PixelCopy.SUCCESS) result(bitmap) else { bitmap.recycle();result(null) }
                    },handler)
                } catch (_: Exception) { bitmap.recycle();result(null) }
                return
            }
            // PixelCopy is unavailable on Android 6; draw the existing renderer without advancing it.
            val bitmap=WallpaperPreviewSource.bitmap(view.width,view.height)
            if (bitmap==null) { result(null);return }
            try {
                val canvas=android.graphics.Canvas(bitmap)
                canvas.scale(bitmap.width.toFloat()/view.width,bitmap.height.toFloat()/view.height)
                view.draw(canvas)
                if (AppearanceSettings.crtFilter()) {
                    crtOverlay.setBounds(0,0,view.width,view.height)
                    crtOverlay.draw(canvas)
                }
            } catch (_: Exception) { bitmap.recycle();result(null);return }
            result(bitmap)
        }

        private val handler = Handler(Looper.getMainLooper())
        private val powerManager = getSystemService(PowerManager::class.java)
        private val keyguardManager = getSystemService(KeyguardManager::class.java)
        private val crtOverlay = CrtOverlayDrawable(this@RetuiWallpaperService).apply {
            setAccentColor(LauncherSettings.getColor(Theme.output_text_color))
        }
        private var view: View = createView()
        private var visible = false
        private var interactionEnabled = false
        private var touchEnabled = true
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
                    ACTION_TOUCH_INTERACTION -> {
                        touchEnabled = intent.getBooleanExtra(EXTRA_TOUCH_ENABLED, true)
                        updateTouchHandling()
                    }
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
            android.util.Log.i("TUI-Wallpaper", "engine created preview=$isPreview")
            if (isPreview) { releaseView(); view = createView(); RetuiWallpaperSettings.markPreviewed(this@RetuiWallpaperService) }
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
                    addAction(ACTION_TOUCH_INTERACTION)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            receiverRegistered = true
            interactionEnabled = launcherInteraction
            localBroadcastManager.registerReceiver(
                mediaReceiver,
                IntentFilter(MusicService.ACTION_MUSIC_CHANGED)
            )
            mediaReceiverRegistered = true
            updateTouchHandling()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // Back on the home screen after the apply page showed the staged choice: the user applied it (backing out
            // returns to the picker, which hides the wallpaper and discards the choice). vivo applies this way without
            // telling anyone.
            if (visible && !isPreview && RetuiWallpaperSettings.previewedForApply(this@RetuiWallpaperService)) {
                android.util.Log.i("TUI-Wallpaper", "home wallpaper shown after apply preview")
                applyStaged(this@RetuiWallpaperService)
            }
            this.visible = visible
            interactionEnabled = visible && launcherInteraction
            updateTouchHandling()
            handler.removeCallbacks(drawFrame)
            if (visible) refreshView(recreate = false)
        }

        override fun onTouchEvent(event: MotionEvent) {
            if (!touchAllowed()) return
            val current = view
            if (current is PixelDreamView) current.handleTouch(event)
            if (current is MatrixRainView) current.handleTouch(event)
            if (current is PixelWallpaperView) current.handleTouch(event)
        }

        @RequiresApi(27)
        override fun onComputeColors(): WallpaperColors = when (val current = view) {
            is BlackHoleView -> current.wallpaperColors()
            is CsakuraView -> current.wallpaperColors()
            is TopoNoiseView -> current.wallpaperColors()
            is PixelDreamView -> current.wallpaperColors()
            is CloudsView -> current.wallpaperColors()
            is AstroChartView -> current.wallpaperColors()
            is MatrixRainView -> current.wallpaperColors()
            is PixelWallpaperView -> current.wallpaperColors()
            else -> (current as SolidColorView).wallpaperColors()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (!isPreview) WallpaperPreviewSource.register(this)
            layoutView(width, height)
            (view as? CloudsView)?.resetClock()
            (view as? AstroChartView)?.resetClock()
            (view as? MatrixRainView)?.resetClock()
            (view as? PixelWallpaperView)?.resetClock()
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
            WallpaperPreviewSource.unregister(this)
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
            (view as? CloudsView)?.resetClock()
            (view as? AstroChartView)?.resetClock()  // resume where it paused, without replaying hidden time
            (view as? MatrixRainView)?.resetClock()
            (view as? PixelWallpaperView)?.resetClock()
            if (shouldScheduleWallpaperFrame(shouldRender(), fullRedrawPending, isAnimated())) {
                handler.post(drawFrame)
            }
        }

        private fun shouldRender(): Boolean = shouldRenderWallpaper(
            visible, interactionEnabled, powerManager.isInteractive, keyguardManager.isKeyguardLocked,
            wallpaperPreview = isPreview
        )

        private fun touchAllowed(): Boolean = shouldHandleWallpaperTouch(
            visible,
            interactionEnabled,
            powerManager.isInteractive,
            keyguardManager.isKeyguardLocked,
            touchEnabled
        )

        private fun updateTouchHandling() {
            val enabled = touchAllowed() && view is PixelDreamView
            (view as? PixelDreamView)?.setInteractionEnabled(enabled)
            (view as? MatrixRainView)?.touchEffects = if (touchAllowed()) MatrixRainView.TOUCH_AVOID or MatrixRainView.TOUCH_FLICKER or MatrixRainView.TOUCH_SHOCKWAVE else 0
        }

        private fun setInteractionEnabled(enabled: Boolean) {
            interactionEnabled = enabled
            updateTouchHandling()
            scheduleIfVisible()
        }

        /** Apply-page previews read the staged (pending) choices; the real wallpaper reads the applied ones. */
        private val settingsContext: Context
            // isPreview is unknown while the engine is still being constructed; onCreate rebuilds a preview's view.
            get() = if (runCatching { isPreview }.getOrDefault(false)) RetuiWallpaperSettings.previewContext(this@RetuiWallpaperService) else this@RetuiWallpaperService

        private fun refreshView(recreate: Boolean) {
            val selected = WallpaperLibrary.effectiveScene(settingsContext, RetuiWallpaperSettings.scene(settingsContext))
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
            // Bitmap layers need the same GPU path as the picker; CPU compositing is costly.
            val hardware = if ((current is CloudsView || current is AstroChartView || current is MatrixRainView || current is PixelWallpaperView) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { surfaceHolder.lockHardwareCanvas() } catch (_: Exception) { null }
            } else null
            val canvas = hardware ?: (try { lockSoftware() } catch (_: Exception) { null }) ?: return false
            try {
                when (current) {
                    is BlackHoleView -> current.advance()
                    is CsakuraView -> current.advance()
                    is PixelDreamView -> current.advance()
                    is CloudsView -> current.advance()
                    is AstroChartView -> current.advance()
                    is MatrixRainView -> current.advance()
                    is PixelWallpaperView -> current.advance()
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
            is CloudsView -> CloudsView.FRAME_DELAY_MS
            is AstroChartView -> AstroChartView.FRAME_DELAY_MS
            is MatrixRainView -> MatrixRainView.FRAME_DELAY_MS
            is PixelWallpaperView -> (view as PixelWallpaperView).frameDelayMs()
            else -> 1000L / CsakuraView.FPS
        }

        private fun releaseView() {
            when (val current = view) {
                is BlackHoleView -> current.release()
                is CsakuraView -> current.release()
                is TopoNoiseView -> current.release()
                is CloudsView -> current.release()
                is AstroChartView -> current.release()
                is MatrixRainView -> current.release()
                is PixelWallpaperView -> current.release()
            }
        }

        private fun createView(): View = when (WallpaperLibrary.effectiveScene(settingsContext, RetuiWallpaperSettings.scene(settingsContext))) {
            "black hole" -> BlackHoleView(settingsContext).apply { loadPosition() }
            "solid" -> SolidColorView(settingsContext)
            CloudsView.SCENE -> CloudsView(settingsContext)
            AstroChartView.SCENE -> AstroChartView(settingsContext)
            MatrixRainView.SCENE -> MatrixRainView(settingsContext)
            PhotoDitherView.SCENE -> PhotoDitherView(settingsContext)
            RipplesView.SCENE -> RipplesView(settingsContext)
            DoomFireView.SCENE -> DoomFireView(settingsContext)
            ShorelineView.SCENE -> ShorelineView(settingsContext)
            TopoNoiseView.SCENE -> TopoNoiseView(settingsContext).apply { loadPosition() }
            PixelDreamView.SCENE -> PixelDreamView(settingsContext).apply {
                loadPosition()
                setMediaPlaying(mediaPlaying)
            }
            else -> CsakuraView(settingsContext).apply { loadPosition() }
        }

        private fun viewMatchesScene(scene: String): Boolean = when (scene) {
            "black hole" -> view is BlackHoleView
            "solid" -> view is SolidColorView
            CloudsView.SCENE -> view is CloudsView
            AstroChartView.SCENE -> view is AstroChartView
            MatrixRainView.SCENE -> view is MatrixRainView
            PhotoDitherView.SCENE -> view is PhotoDitherView
            RipplesView.SCENE -> view is RipplesView
            DoomFireView.SCENE -> view is DoomFireView
            ShorelineView.SCENE -> view is ShorelineView
            TopoNoiseView.SCENE -> view is TopoNoiseView
            PixelDreamView.SCENE -> view is PixelDreamView
            else -> view is CsakuraView
        }

        private fun isAnimated(): Boolean = view is BlackHoleView || view is CsakuraView || view is PixelDreamView || view is CloudsView || view is AstroChartView || view is MatrixRainView ||
            (view as? PixelWallpaperView)?.animated == true

        private fun loadPosition() = when (val current = view) {
            is BlackHoleView -> current.loadPosition()
            is CsakuraView -> current.loadPosition()
            is TopoNoiseView -> current.loadPosition()
            is PixelDreamView -> current.loadPosition()
            is CloudsView -> current.setScene(RetuiWallpaperSettings.cloudScene(settingsContext))
            is AstroChartView -> current.loadSeed()
            is MatrixRainView -> current.loadSettings()
            is PixelWallpaperView -> current.loadSettings()
            else -> Unit
        }
    }
}
