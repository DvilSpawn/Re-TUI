package ohi.andre.consolelauncher.commands.tuixt

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PresetMarketplaceActivity : LocalizedActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var cancellation: AtomicBoolean? = null
    private lateinit var list: LinearLayout
    private val downloads = mutableListOf<Button>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        XMLPrefsManager.loadCommons(this); LauncherSettings.refreshFromLoadedPrefs()
        LauncherSystemUi.applyFullscreen(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; TuixtTheme.styleScreen(this@PresetMarketplaceActivity, this) }
        root.addView(label(getString(R.string.preset_market_title)).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        root.addView(label(getString(R.string.preset_market_intro)))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button(getString(R.string.portable_preset_done)) { finish() })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(12), bars.top + dp(12), bars.right + dp(12), bars.bottom + dp(12)); insets
        }
        ViewCompat.requestApplyInsets(root)
        load()
    }
    private fun load() {
        list.removeAllViews(); downloads.clear()
        list.addView(label(getString(R.string.preset_market_loading)))
        worker.execute {
            val result = runCatching { PresetMarketplace.load() }
            runOnUiThread {
                if (closed) return@runOnUiThread
                list.removeAllViews()
                result.onSuccess { it.forEach(::card) }.onFailure {
                    list.addView(label(getString(R.string.preset_market_error, it.message.orEmpty())))
                    list.addView(button(getString(R.string.preset_market_retry)) { load() })
                }
            }
        }
    }
    private fun card(entry: PresetMarketplace.Entry) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12))
            TuixtTheme.stylePanel(this@PresetMarketplaceActivity, this)
        }
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        card.addView(label(entry.name).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        card.addView(label(getString(R.string.preset_market_sample)).apply { setBackgroundColor(entry.background); setTextColor(entry.foreground) })
        card.addView(LinearLayout(this).apply { entry.swatches.forEach { color -> addView(View(this@PresetMarketplaceActivity).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, dp(22), 1f)) } })
        val status = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val cancel = button(getString(R.string.portable_preset_cancel)) { cancellation?.set(true) }.apply { visibility = View.GONE }
        val download = button(getString(R.string.preset_market_download)) {
            TuixtDialog.showConfirm(this, entry.name, getString(R.string.preset_market_intro), getString(R.string.preset_market_download),
                getString(R.string.portable_preset_cancel), TuixtDialog.ConfirmAction {
                    if (cancellation != null) return@ConfirmAction
                    val token = AtomicBoolean(); cancellation = token
                    downloads.forEach { it.isEnabled = false }; cancel.visibility = View.VISIBLE
                    status.setText(R.string.preset_market_loading)
                    worker.execute {
                        val result = runCatching { PresetMarketplace.install(this, entry, token::get) { bytes ->
                            runOnUiThread { if (!closed) status.text = getString(R.string.preset_market_progress, bytes, entry.bytes) }
                        } }
                        runOnUiThread {
                            if (closed) return@runOnUiThread
                            cancellation = null; cancel.visibility = View.GONE; downloads.forEach { it.isEnabled = true }
                            result.onSuccess { saved ->
                                status.text = getString(R.string.preset_market_saved, saved)
                                card.findViewWithTag<View>("copy-apply")?.let { card.removeView(it) }
                                card.addView(button(getString(R.string.preset_market_copy)) {
                                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Re:TUI", "preset -apply $saved"))
                                }.apply { tag = "copy-apply" })
                            }.onFailure { status.text = getString(if (it is java.util.concurrent.CancellationException) R.string.preset_market_cancelled else R.string.preset_market_error, it.message.orEmpty()) }
                        }
                    }
                })
        }
        downloads += download
        card.addView(status); card.addView(download); card.addView(cancel)
    }
    private fun dp(value: Int) = TuixtTheme.dp(this, value.toFloat())
    private fun label(value: String) = TextView(this).apply { text = value; typeface = Tuils.getTypeface(this@PresetMarketplaceActivity); textSize = 14f; setTextColor(TuixtTheme.textColor()); setPadding(dp(10), dp(10), dp(10), dp(10)) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; TuixtTheme.styleButton(this@PresetMarketplaceActivity, this, false); setOnClickListener { action() } }
    override fun onDestroy() { closed = true; cancellation?.set(true); worker.shutdown(); super.onDestroy() }
}
