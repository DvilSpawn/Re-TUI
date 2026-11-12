package ohi.andre.consolelauncher.commands.tuixt

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.format.Formatter
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedActivity
import ohi.andre.consolelauncher.managers.backup.BackupManager
import ohi.andre.consolelauncher.managers.preset.PresetManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** preset -market: terminal colour themes, and community presets people share (download and upload). */
class PresetMarketplaceActivity : LocalizedActivity() {
    private enum class Screen { LIST, DETAIL, SHARE }

    private val worker = Executors.newSingleThreadExecutor()
    private val images = Executors.newFixedThreadPool(2)
    private val shots = LruCache<String, Bitmap>(24)
    private var closed = false
    private var cancellation: AtomicBoolean? = null
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var terminalTab: Button
    private lateinit var communityTab: Button
    private val downloads = mutableListOf<Button>()
    private var community = false
    private var popular = false
    private var screen = Screen.LIST
    // Bumped whenever the list is rebuilt, so a late network answer never lands on another screen.
    private var generation = 0
    private var share: ShareState? = null

    private class ShareState {
        var screenshot: ByteArray? = null
        var wallpaper: ByteArray? = null
        var behaviour: Set<String>? = null
        lateinit var preview: ImageView
        lateinit var captureButton: Button
        lateinit var wallpaperBox: CheckBox
        lateinit var wallpaperNote: TextView
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        XMLPrefsManager.loadCommons(this); LauncherSettings.refreshFromLoadedPrefs()
        LauncherSystemUi.applyFullscreen(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; TuixtTheme.styleScreen(this@PresetMarketplaceActivity, this) }
        root.addView(label(getString(R.string.preset_market_title)).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        terminalTab = choice(getString(R.string.preset_market_tab_terminal), true) { showTab(false) }
        communityTab = choice(getString(R.string.preset_market_tab_community), false) { showTab(true) }
        root.addView(LinearLayout(this).apply {
            addView(terminalTab, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
            addView(communityTab, LinearLayout.LayoutParams(0, -2, 1f))
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button(getString(R.string.portable_preset_done)) { finish() })
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left + dp(12), bars.top + dp(12), bars.right + dp(12), bars.bottom + dp(12)); insets
        }
        ViewCompat.requestApplyInsets(root)
        load()
    }

    private fun showTab(communityTab: Boolean) {
        if (cancellation != null) return
        community = communityTab
        TuixtTheme.styleChoice(this, terminalTab, !communityTab)
        TuixtTheme.styleChoice(this, this.communityTab, communityTab)
        showList()
    }

    private fun showList() { if (community) loadCommunity() else load() }

    private fun reset(next: Screen): Int {
        screen = next; list.removeAllViews(); downloads.clear(); scroll.scrollTo(0, 0)
        return ++generation
    }

    private fun load() {
        val gen = reset(Screen.LIST)
        list.addView(label(getString(R.string.preset_market_intro)))
        val loading = label(getString(R.string.preset_market_loading)); list.addView(loading)
        worker.execute {
            val result = runCatching { PresetMarketplace.load() }
            runOnUiThread {
                if (closed || gen != generation) return@runOnUiThread
                list.removeView(loading)
                result.onSuccess { it.forEach(::card) }.onFailure {
                    list.addView(label(getString(R.string.preset_market_error, it.message.orEmpty())))
                    list.addView(button(getString(R.string.preset_market_retry)) { load() })
                }
            }
        }
    }

    private fun card(entry: PresetMarketplace.Entry) {
        val card = panel()
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        card.addView(label(entry.name).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        card.addView(label(getString(R.string.preset_market_sample)).apply { setBackgroundColor(entry.background); setTextColor(entry.foreground) })
        card.addView(LinearLayout(this).apply { entry.swatches.forEach { color -> addView(View(this@PresetMarketplaceActivity).apply { setBackgroundColor(color) }, LinearLayout.LayoutParams(0, dp(22), 1f)) } })
        val status = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val cancel = button(getString(R.string.portable_preset_cancel)) { cancellation?.set(true) }.apply { visibility = View.GONE }
        val download = button(getString(R.string.preset_market_download)) {
            TuixtDialog.showConfirm(this, entry.name, getString(R.string.preset_market_intro), getString(R.string.preset_market_download),
                getString(R.string.portable_preset_cancel), TuixtDialog.ConfirmAction {
                    runInstall(card, status, cancel, entry.bytes) { token, progress -> PresetMarketplace.install(this, entry, token::get, progress) }
                })
        }
        downloads += download
        card.addView(status); card.addView(download); card.addView(cancel)
    }

    /** Shared by both tabs: one download at a time, cancellable, ending in the copy-apply button. */
    private fun runInstall(card: LinearLayout, status: TextView, cancel: Button, total: Int,
                           install: (AtomicBoolean, (Int) -> Unit) -> String) {
        if (cancellation != null) return
        val token = AtomicBoolean(); cancellation = token
        downloads.forEach { it.isEnabled = false }; cancel.visibility = View.VISIBLE
        status.setText(R.string.preset_market_loading)
        worker.execute {
            val result = runCatching { install(token) { bytes ->
                runOnUiThread { if (!closed) status.text = getString(R.string.preset_market_progress, bytes, total) }
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
                }.onFailure { status.text = getString(if (it is CancellationException) R.string.preset_market_cancelled else R.string.preset_market_error, it.message.orEmpty()) }
            }
        }
    }

    // ---- Community list ----

    private fun loadCommunity() {
        val gen = reset(Screen.LIST)
        list.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(choice(getString(R.string.preset_community_sort_new), !popular) { popular = false; loadCommunity() })
            addView(choice(getString(R.string.preset_community_sort_popular), popular) { popular = true; loadCommunity() },
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(6) })
            addView(View(this@PresetMarketplaceActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(button(getString(R.string.preset_community_share_mine)) { showShare() })
        })
        list.addView(small(getString(R.string.preset_community_intro)))
        val loading = label(getString(R.string.preset_market_loading)); list.addView(loading)
        val sortPopular = popular
        worker.execute {
            val result = runCatching { CommunityPresets.load(sortPopular) }
            runOnUiThread {
                if (closed || gen != generation) return@runOnUiThread
                list.removeView(loading)
                result.onSuccess { items ->
                    if (items.isEmpty()) list.addView(label(getString(R.string.preset_community_empty)))
                    items.forEach(::communityCard)
                }.onFailure {
                    list.addView(label(getString(R.string.preset_market_error, it.message.orEmpty())))
                    list.addView(button(getString(R.string.preset_market_retry)) { loadCommunity() })
                }
            }
        }
    }

    private fun communityCard(item: CommunityPresets.Item) {
        val card = panel(LinearLayout.HORIZONTAL)
        list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val shot = screenshotView(item)
        card.addView(shot, LinearLayout.LayoutParams(dp(72), dp(128)))
        loadScreenshot(item, shot)
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))
        details.addView(small(item.name).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(TuixtTheme.textColor()) })
        details.addView(small(getString(R.string.preset_community_by, item.submitter ?: getString(R.string.preset_community_anonymous), item.downloads)))
        if (item.tags.isNotEmpty()) details.addView(small(item.tags.joinToString(" · ")))
        val extras = listOfNotNull(
            if (item.wallpaperUrl != null) getString(R.string.preset_community_has_wallpaper) else null,
            if (item.fontUrl != null) getString(R.string.preset_community_has_font) else null)
        if (extras.isNotEmpty()) details.addView(small(extras.joinToString(" · ")).apply { setTextColor(TuixtTheme.accentColor()) })
        details.addView(button(getString(R.string.preset_community_view)) { showDetail(item) }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
    }

    private fun screenshotView(item: CommunityPresets.Item) = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = TuixtTheme.rect(this@PresetMarketplaceActivity, TuixtTheme.surfaceColor(), TuixtTheme.borderColor(), 1f)
        contentDescription = item.name
    }

    private fun loadScreenshot(item: CommunityPresets.Item, view: ImageView) {
        shots.get(item.id)?.let { view.setImageBitmap(it); return }
        images.execute {
            val bitmap = runCatching { CommunityPresets.screenshot(item, 720) }.getOrNull() ?: return@execute
            shots.put(item.id, bitmap)
            runOnUiThread { if (!closed) view.setImageBitmap(bitmap) }
        }
    }

    // ---- Before download ----

    private fun showDetail(item: CommunityPresets.Item) {
        val gen = reset(Screen.DETAIL)
        list.addView(label(item.name).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        val top = LinearLayout(this)
        list.addView(top)
        val shot = screenshotView(item)
        top.addView(shot, LinearLayout.LayoutParams(dp(128), dp(228)))
        loadScreenshot(item, shot)
        val about = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        top.addView(about, LinearLayout.LayoutParams(0, -2, 1f))
        about.addView(small(getString(R.string.preset_community_by, item.submitter ?: getString(R.string.preset_community_anonymous), item.downloads)))
        item.appVersion?.let { about.addView(small(getString(R.string.preset_community_made_on, it))) }
        if (item.description.isNotEmpty()) about.addView(small(item.description).apply { setTextColor(TuixtTheme.textColor()) })
        if (item.tags.isNotEmpty()) about.addView(small(item.tags.joinToString(" · ")))

        list.addView(section(getString(R.string.preset_community_what_changes)))
        val parts = small(getString(R.string.preset_community_checking)).apply { setTextColor(TuixtTheme.textColor()) }
        list.addView(parts)
        worker.execute {
            val result = runCatching { CommunityPresets.contents(this, item) }
            runOnUiThread {
                if (closed || gen != generation) return@runOnUiThread
                parts.text = result.fold({ contents ->
                    listOf("theme" to R.string.preset_community_part_colours, "suggestions" to R.string.preset_community_part_suggestions,
                        "ui" to R.string.preset_community_part_layout, "behavior" to R.string.preset_community_part_behaviour)
                        .joinToString("\n") { (section, label) -> (if (section in contents.sections) "✓ " else "– ") + getString(label) }
                }, { getString(R.string.preset_market_error, it.message.orEmpty()) })
            }
        }

        var wallpaperBox: CheckBox? = null
        if (item.wallpaperUrl != null || item.fontUrl != null) list.addView(section(getString(R.string.preset_community_extras)))
        if (item.wallpaperUrl != null) {
            val size = Formatter.formatShortFileSize(this, (item.wallpaperBytes ?: 0).toLong())
            wallpaperBox = checkBox(getString(R.string.preset_community_wallpaper_option, size), true)
            list.addView(wallpaperBox)
        }
        if (item.fontUrl != null) {
            list.addView(small(getString(R.string.preset_community_font_link)))
            list.addView(button(getString(R.string.preset_community_open_font)) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.fontUrl))) }
            })
        } else {
            list.addView(small(getString(R.string.preset_community_font_none)))
        }
        list.addView(small(getString(R.string.preset_community_detail_note)))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(card)
        val status = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val cancel = button(getString(R.string.portable_preset_cancel)) { cancellation?.set(true) }.apply { visibility = View.GONE }
        val back = button(getString(R.string.preset_community_back)) { if (cancellation == null) loadCommunity() }
        val download = button(getString(R.string.preset_market_download)) {
            val withWallpaper = wallpaperBox?.isChecked == true
            runInstall(card, status, cancel, item.presetBytes) { token, progress ->
                CommunityPresets.install(this, item, withWallpaper, token::get, progress)
            }
        }.apply { TuixtTheme.styleButton(this@PresetMarketplaceActivity, this, true) }
        downloads += download; downloads += back
        card.addView(pair(back, download)); card.addView(status); card.addView(cancel)
    }

    // ---- Share my setup ----

    private fun showShare() {
        reset(Screen.SHARE)
        val state = ShareState().also { share = it }
        list.addView(label(getString(R.string.share_title)).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        list.addView(small(getString(R.string.share_intro)))
        val name = field(getString(R.string.share_name), "")
        val description = field(getString(R.string.share_description), "").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2; isSingleLine = false
        }
        val tags = field(getString(R.string.share_tags), getString(R.string.share_tags_hint))
        val submitter = field(getString(R.string.share_your_name), getString(R.string.share_optional))

        list.addView(section(getString(R.string.share_what)))
        list.addView(checkBox(getString(R.string.share_colours), true).apply { isEnabled = false })
        val layout = checkBox(getString(R.string.share_layout), true)
        list.addView(layout)
        val behaviour = checkBox(getString(R.string.share_behaviour), false)
        behaviour.setOnClickListener {
            if (!behaviour.isChecked) { state.behaviour = null; return@setOnClickListener }
            behaviour.isChecked = false
            pickBehaviour { labels -> state.behaviour = labels; behaviour.isChecked = labels.isNotEmpty() }
        }
        list.addView(behaviour)
        state.wallpaperBox = checkBox(getString(R.string.share_wallpaper), false).apply { isEnabled = false }
        list.addView(state.wallpaperBox)
        state.wallpaperNote = small(getString(R.string.share_wallpaper_unreadable)).apply { visibility = View.GONE }
        list.addView(state.wallpaperNote)
        list.addView(button(getString(R.string.share_choose_wallpaper)) { pickImage(PICK_WALLPAPER) })

        list.addView(section(getString(R.string.share_screenshot)))
        val shotRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        state.preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = TuixtTheme.rect(this@PresetMarketplaceActivity, TuixtTheme.surfaceColor(), TuixtTheme.borderColor(), 1f)
            contentDescription = getString(R.string.share_screenshot)
        }
        shotRow.addView(state.preview, LinearLayout.LayoutParams(dp(44), dp(78)))
        val shotButtons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        state.captureButton = button(getString(R.string.share_capture)) { captureScreenshot(state) }
        shotButtons.addView(state.captureButton)
        shotButtons.addView(button(getString(R.string.share_choose_image)) { pickImage(PICK_SCREENSHOT) })
        shotRow.addView(shotButtons, LinearLayout.LayoutParams(0, -2, 1f))
        list.addView(shotRow)

        val font = field(getString(R.string.share_font), "https://").apply { inputType = InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT }
        val consent = checkBox(getString(R.string.share_consent), false)
        list.addView(consent)
        val status = label("").apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        val cancel = button(getString(R.string.portable_preset_cancel)) { if (cancellation == null) loadCommunity() }
        lateinit var send: Button
        send = button(getString(R.string.share_send)) {
            val shot = state.screenshot
            if (name.text.isBlank() || description.text.isBlank() || shot == null || !consent.isChecked) {
                status.setText(R.string.share_missing); return@button
            }
            val labels = if (behaviour.isChecked) state.behaviour else null
            val withLayout = layout.isChecked
            val wallpaper = if (state.wallpaperBox.isChecked) state.wallpaper else null
            val title = name.text.toString().trim()
            val submission = { preset: ByteArray -> CommunityPresets.Submission(title, description.text.toString().trim(),
                tags.text.toString().trim(), submitter.text.toString().trim(), font.text.toString().trim().takeUnless { it == "https://" }.orEmpty(),
                preset, shot, wallpaper) }
            send.isEnabled = false; cancel.isEnabled = false; status.setText(R.string.share_sending)
            val gen = generation
            worker.execute {
                val result = runCatching { CommunityPresets.submit(submission(BackupManager.shareablePackage(null, labels, withLayout))) }
                runOnUiThread {
                    if (closed || gen != generation) return@runOnUiThread
                    send.isEnabled = true; cancel.isEnabled = true
                    result.onSuccess { showSent(title, withLayout, labels != null, shot.size, wallpaper?.size) }
                        .onFailure { status.text = getString(R.string.share_failed, it.message.orEmpty()) }
                }
            }
        }.apply { TuixtTheme.styleButton(this@PresetMarketplaceActivity, this, true) }
        list.addView(status)
        list.addView(pair(cancel, send))

        captureScreenshot(state)
        worker.execute {
            val current = runCatching { currentWallpaper() }.getOrNull()
            runOnUiThread {
                if (closed || share !== state || state.wallpaper != null) return@runOnUiThread
                if (current != null) { state.wallpaper = current; state.wallpaperBox.isEnabled = true; state.wallpaperBox.isChecked = true }
                else state.wallpaperNote.visibility = View.VISIBLE
            }
        }
    }

    private fun showSent(name: String, layout: Boolean, behaviour: Boolean, screenshot: Int, wallpaper: Int?) {
        reset(Screen.SHARE)
        share = null
        list.addView(label(getString(R.string.share_title)).apply { TuixtTheme.styleHeader(this@PresetMarketplaceActivity, this) })
        val parts = listOfNotNull(getString(R.string.preset_community_part_colours), getString(R.string.preset_community_part_suggestions),
            if (layout) getString(R.string.preset_community_part_layout) else null, if (behaviour) getString(R.string.preset_community_part_behaviour) else null)
            .joinToString(", ") { it.lowercase(Locale.getDefault()) }
        val log = panel()
        log.addView(small(getString(R.string.share_log_exported, parts)).apply { setTextColor(TuixtTheme.textColor()) })
        log.addView(small(getString(R.string.share_log_screenshot, Formatter.formatShortFileSize(this, screenshot.toLong()))).apply { setTextColor(TuixtTheme.textColor()) })
        wallpaper?.let { log.addView(small(getString(R.string.share_log_wallpaper, Formatter.formatShortFileSize(this, it.toLong()))).apply { setTextColor(TuixtTheme.textColor()) }) }
        log.addView(small(getString(R.string.share_log_uploaded)).apply { setTextColor(TuixtTheme.textColor()) })
        list.addView(log, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        list.addView(label(getString(R.string.share_sent, name)).apply { setTextColor(TuixtTheme.accentColor()) })
        list.addView(button(getString(R.string.share_back_to_community)) { loadCommunity() })
    }

    private fun pickBehaviour(onDone: (Set<String>) -> Unit) {
        val values = runCatching { PresetManager.shareableBehaviorValues(null) }.getOrNull() ?: return
        val defaults = PresetManager.defaultShareableBehaviorLabels()
        val selected = LinkedHashSet<String>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(small(getString(R.string.themer_choose_which_behavior_values_to_include_the_existing_safe_56372)))
            values.forEach { (setting, value) ->
                val label = setting.label().orEmpty()
                if (label in defaults) selected.add(label)
                addView(CheckBox(this@PresetMarketplaceActivity).apply {
                    text = label.replace('_', ' ').uppercase(Locale.getDefault()) + "\n" + getString(R.string.themer_value_a89f9) + value
                    isChecked = label in defaults
                    setTextColor(TuixtTheme.textColor()); typeface = Tuils.getTypeface(this@PresetMarketplaceActivity); textSize = 12f
                    buttonTintList = ColorStateList.valueOf(TuixtTheme.accentColor())
                    setOnCheckedChangeListener { _, on -> if (on) selected.add(label) else selected.remove(label) }
                })
            }
        }
        TuixtDialog.showContent(this, getString(R.string.themer_behavior_sharing_441b1), content,
            getString(R.string.themer_continue_2e026), getString(R.string.themer_cancel_77dfd),
            TuixtDialog.ConfirmAction { onDone(selected.toSet()) }, fillHeight = true, heightFraction = 0.88f)
    }

    // ---- Images ----

    /** The launcher as it looks behind this screen, over the wallpaper when Android lets us read it. */
    private fun captureScreenshot(state: ShareState) {
        val bitmap = runCatching {
            val view = LauncherActivity.instance?.window?.decorView ?: return@runCatching null
            if (view.width <= 0 || view.height <= 0) return@runCatching null
            val scale = minOf(1f, 1080f / view.width)
            val out = Bitmap.createBitmap((view.width * scale).roundToInt().coerceAtLeast(1), (view.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            canvas.scale(scale, scale)
            canvas.drawColor(Color.BLACK)
            wallpaperDrawable()?.let { drawCropped(canvas, it, view.width, view.height) }
            view.draw(canvas)
            out
        }.getOrNull() ?: return
        state.preview.setImageBitmap(bitmap)
        state.captureButton.text = getString(R.string.share_captured)
        worker.execute { val bytes = jpeg(bitmap); runOnUiThread { if (share === state) state.screenshot = bytes } }
    }

    private fun wallpaperDrawable(): Drawable? = runCatching { WallpaperManager.getInstance(this).drawable }.getOrNull()

    private fun currentWallpaper(): ByteArray? {
        val drawable = wallpaperDrawable() ?: return null
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: run {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { drawable.setBounds(0, 0, w, h); drawable.draw(Canvas(it)) }
        }
        val scale = minOf(1f, 2400f / maxOf(bitmap.width, bitmap.height))
        return jpeg(if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true) else bitmap)
    }

    private fun drawCropped(canvas: Canvas, drawable: Drawable, width: Int, height: Int) {
        val iw = drawable.intrinsicWidth; val ih = drawable.intrinsicHeight
        if (iw <= 0 || ih <= 0) { drawable.setBounds(0, 0, width, height); drawable.draw(canvas); return }
        val scale = maxOf(width / iw.toFloat(), height / ih.toFloat())
        val dw = (iw * scale).roundToInt(); val dh = (ih * scale).roundToInt()
        val left = (width - dw) / 2; val top = (height - dh) / 2
        drawable.setBounds(left, top, left + dw, top + dh); drawable.draw(canvas)
    }

    private fun jpeg(bitmap: Bitmap): ByteArray {
        var quality = 88
        while (true) {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= CommunityPresets.MAX_IMAGE || quality <= 40) return out.toByteArray()
            quality -= 12
        }
    }

    private fun pickImage(request: Int) {
        runCatching { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), request) }
    }

    @Deprecated("Activity result API is not available to this plain Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val state = share ?: return
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null || (requestCode != PICK_SCREENSHOT && requestCode != PICK_WALLPAPER)) return
        worker.execute {
            val image = runCatching {
                val bytes = contentResolver.openInputStream(uri)!!.use { input -> input.readBytes().also { require(it.size <= 40 * 1024 * 1024) } }
                val bitmap = checkNotNull(CommunityPresets.decode(bytes, 2400))
                bitmap to jpeg(bitmap)
            }.getOrNull()
            runOnUiThread {
                if (closed || share !== state) return@runOnUiThread
                if (image == null) { TuixtDialog.showConfirm(this, getString(R.string.share_title), getString(R.string.share_image_error),
                    getString(R.string.portable_preset_done), getString(R.string.portable_preset_cancel), TuixtDialog.ConfirmAction { }); return@runOnUiThread }
                if (requestCode == PICK_SCREENSHOT) {
                    state.screenshot = image.second; state.preview.setImageBitmap(image.first)
                    state.captureButton.text = getString(R.string.share_capture)
                } else {
                    state.wallpaper = image.second; state.wallpaperBox.isEnabled = true; state.wallpaperBox.isChecked = true
                    state.wallpaperNote.visibility = View.GONE
                }
            }
        }
    }

    // ---- Small view helpers ----

    private fun dp(value: Int) = TuixtTheme.dp(this, value.toFloat())
    private fun label(value: String) = TextView(this).apply { text = value; typeface = Tuils.getTypeface(this@PresetMarketplaceActivity); textSize = 14f; setTextColor(TuixtTheme.textColor()); setPadding(dp(10), dp(10), dp(10), dp(10)) }
    private fun small(value: String) = TextView(this).apply {
        text = value; typeface = Tuils.getTypeface(this@PresetMarketplaceActivity); textSize = 12f
        setTextColor(ColorStateList.valueOf(TuixtTheme.textColor()).withAlpha(190)); setPadding(0, dp(2), 0, dp(2))
    }
    private fun section(value: String) = small(value).apply {
        setTextColor(TuixtTheme.accentColor()); setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(12), 0, dp(4))
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; TuixtTheme.styleButton(this@PresetMarketplaceActivity, this, false); setOnClickListener { action() } }
    private fun choice(value: String, selected: Boolean, action: () -> Unit) = Button(this).apply { text = value; TuixtTheme.styleChoice(this@PresetMarketplaceActivity, this, selected); setOnClickListener { action() } }
    private fun panel(orientation: Int = LinearLayout.VERTICAL) = LinearLayout(this).apply {
        this.orientation = orientation; setPadding(dp(10), dp(10), dp(10), dp(10)); TuixtTheme.stylePanel(this@PresetMarketplaceActivity, this)
    }
    private fun checkBox(value: String, checked: Boolean) = CheckBox(this).apply {
        text = value; isChecked = checked; typeface = Tuils.getTypeface(this@PresetMarketplaceActivity); textSize = 13f
        setTextColor(TuixtTheme.textColor()); buttonTintList = ColorStateList.valueOf(TuixtTheme.accentColor())
    }
    private fun field(title: String, hint: String): EditText {
        list.addView(section(title).apply { setPadding(0, dp(8), 0, dp(2)) })
        return EditText(this).apply {
            this.hint = hint; isSingleLine = true; typeface = Tuils.getTypeface(this@PresetMarketplaceActivity)
            TuixtTheme.styleInput(this@PresetMarketplaceActivity, this)
            setHintTextColor(ColorStateList.valueOf(TuixtTheme.textColor()).withAlpha(110))
            list.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
    }
    private fun pair(left: View, right: View) = LinearLayout(this).apply {
        addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
        addView(right, LinearLayout.LayoutParams(0, -2, 1f))
    }

    @Deprecated("Back returns to the list from a preset or the share form")
    override fun onBackPressed() {
        if (screen != Screen.LIST && cancellation == null) showList() else if (cancellation == null) super.onBackPressed()
    }

    override fun onDestroy() { closed = true; cancellation?.set(true); worker.shutdown(); images.shutdown(); super.onDestroy() }

    private companion object {
        const val PICK_SCREENSHOT = 41
        const val PICK_WALLPAPER = 42
    }
}
