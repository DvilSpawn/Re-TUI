package ohi.andre.consolelauncher.wallpaper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.ByteArrayInputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Native catalogue and download controls. WebViews display individual preview assets only. */
class WallpaperGalleryActivity : LocalizedActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val previews = mutableListOf<WebView>()
    private val downloadButtons = mutableListOf<Button>()
    private lateinit var list: LinearLayout
    private var download: AtomicBoolean? = null
    private var closed = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        LauncherSystemUi.applyFullscreen(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            TuixtTheme.styleScreen(this@WallpaperGalleryActivity, this)
        }
        root.addView(label(getString(R.string.wallpaper_library_title)).apply {
            TuixtTheme.styleHeader(this@WallpaperGalleryActivity, this)
        })
        root.addView(label(getString(R.string.wallpaper_library_intro)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(action(getString(R.string.wallpaper_library_back)) { finish() })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(12), bars.top + dp(8), bars.right + dp(12), bars.bottom + dp(8))
            insets
        }
        ViewCompat.requestApplyInsets(root)
        loadCatalog()
    }

    private fun loadCatalog() {
        clearPreviews()
        list.removeAllViews()
        downloadButtons.clear()
        list.addView(label(getString(R.string.wallpaper_library_loading)))
        worker.execute {
            val result = runCatching { WallpaperLibrary.loadCatalog(applicationContext) }
            main.post {
                if (closed) return@post
                list.removeAllViews()
                result.onSuccess { entries ->
                    if (entries.isEmpty()) list.addView(label(getString(R.string.wallpaper_library_empty)))
                    entries.forEach(::addCard)
                }.onFailure {
                    list.addView(label(getString(R.string.wallpaper_library_unavailable)))
                    list.addView(action(getString(R.string.wallpaper_library_retry)) { loadCatalog() })
                }
            }
        }
    }

    private fun addCard(entry: WallpaperLibrary.Entry) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            TuixtTheme.stylePanel(this@WallpaperGalleryActivity, this)
        }
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        card.addView(label(entry.title).apply { TuixtTheme.styleHeader(this@WallpaperGalleryActivity, this) })
        card.addView(label(entry.description))
        val preview = WebView(this).apply {
            contentDescription = getString(R.string.wallpaper_library_preview_description, entry.title)
            setBackgroundColor(TuixtTheme.surfaceColor())
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                blockNetworkLoads = false
                builtInZoomControls = false
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    if (request.url.toString() == entry.preview && request.url.scheme == "https") null
                    else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
            }
        }
        card.addView(preview, LinearLayout.LayoutParams(-1, dp(190)))
        previews += preview
        // The parsed catalogue pins asset origins; no hosted page, scripts, or navigation is loaded.
        if (Uri.parse(entry.preview).scheme == "https") preview.loadUrl(entry.preview)
        val status = label("").apply { tag = "wallpaper-status-${entry.id}"; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE; max = 100
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(status)
        card.addView(progress, LinearLayout.LayoutParams(-1, dp(8)))
        card.addView(controls)
        fun ready() {
            controls.removeAllViews()
            progress.visibility = View.GONE
            if (WallpaperLibrary.store(this).installed(entry.id)) {
                status.setText(R.string.wallpaper_library_installed)
                controls.addView(action(getString(R.string.wallpaper_library_preview_use)) {
                    val scene = WallpaperPackStore.SCENES[entry.id] ?: return@action
                    startActivity(Intent(this, RetuiWallpaperActivity::class.java).putExtra("preview_scene", scene))
                }.apply { tag = "wallpaper-use-${entry.id}" })
            } else {
                if (status.text.isEmpty()) status.setText(R.string.wallpaper_library_ready)
                controls.addView(action(getString(R.string.wallpaper_library_download)) {
                    if (download != null) return@action
                    TuixtDialog.showConfirm(this, entry.title, getString(R.string.wallpaper_library_confirm),
                        getString(R.string.wallpaper_library_download), getString(R.string.wallpaper_library_cancel),
                        TuixtDialog.ConfirmAction {
                            if (!closed && download == null) {
                                val cancellation = AtomicBoolean(false)
                                download = cancellation
                                downloadButtons.forEach { it.isEnabled = false }
                                status.setText(R.string.wallpaper_library_downloading)
                                progress.visibility = View.VISIBLE
                                progress.isIndeterminate = true
                                controls.removeAllViews()
                                controls.addView(action(getString(R.string.wallpaper_library_cancel)) {
                                    cancellation.set(true)
                                    status.setText(R.string.wallpaper_library_cancelling)
                                    controls.getChildAt(0).isEnabled = false
                                }.apply { tag = "wallpaper-cancel-${entry.id}" })
                                worker.execute {
                                    val result = runCatching {
                                        WallpaperLibrary.install(applicationContext, entry, { cancellation.get() }) { bytes, total ->
                                            main.post {
                                                if (!closed && download === cancellation && !cancellation.get()) {
                                                    progress.isIndeterminate = total <= 0
                                                    if (total > 0) progress.progress = ((bytes * 100L / total).coerceIn(0, 100)).toInt()
                                                }
                                            }
                                        }
                                    }
                                    main.post {
                                        if (closed) return@post
                                        download = null
                                        downloadButtons.forEach { it.isEnabled = true }
                                        status.setText(when {
                                            result.isSuccess -> R.string.wallpaper_library_installed
                                            cancellation.get() -> R.string.wallpaper_library_cancelled
                                            else -> R.string.wallpaper_library_failed
                                        })
                                        ready()
                                    }
                                }
                            }
                        })
                }.apply { tag = "wallpaper-download-${entry.id}"; downloadButtons += this })
            }
        }
        ready()
    }

    private fun label(value: String) = TextView(this).apply {
        text = value; setTextColor(TuixtTheme.textColor()); typeface = Tuils.getTypeface(this@WallpaperGalleryActivity)
        textSize = 14f; setPadding(0, dp(8), 0, dp(8))
    }
    private fun action(value: String, onClick: () -> Unit) = Button(this).apply {
        text = value; TuixtTheme.styleButton(this@WallpaperGalleryActivity, this, false)
        minHeight = dp(48); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) }
        setOnClickListener { onClick() }
    }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun clearPreviews() { previews.forEach { it.stopLoading(); it.destroy() }; previews.clear() }
    override fun onPause() { previews.forEach { it.onPause() }; super.onPause() }
    override fun onResume() { super.onResume(); previews.forEach { it.onResume() } }
    override fun onDestroy() {
        closed = true
        download?.set(true)
        worker.shutdownNow()
        clearPreviews()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
