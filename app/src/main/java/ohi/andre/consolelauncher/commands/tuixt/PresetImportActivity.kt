package ohi.andre.consolelauncher.commands.tuixt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.displayMessage
import java.io.File
import java.util.concurrent.Executors

/** The exported boundary can only inspect and save shareable presets, never restore backups. */
class PresetImportActivity : LocalizedComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var staged: BackupManager.StagedPreset? = null
    @Volatile private var leaving = false
    private var saving = false
    private var pendingIntent: Intent? = null
    @Volatile private var inspection = 0
    private lateinit var preview: TextView
    private lateinit var confirm: Button
    private lateinit var chooseFile: Button

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        XMLPrefsManager.loadCommons(this)
        LauncherSettings.refreshFromLoadedPrefs()
        LauncherSystemUi.applyFullscreen(this)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!saving) finish() }
        })
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        TuixtTheme.styleScreen(this, layout)
        layout.addView(TextView(this).apply {
            setText(R.string.portable_preset_import)
            TuixtTheme.styleHeader(this@PresetImportActivity, this)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        preview = TextView(this).apply {
            setText(R.string.portable_preset_inspecting)
            setTextIsSelectable(true)
            typeface = Tuils.getTypeface(this@PresetImportActivity)
            textSize = 14f
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        preview.setTextColor(TuixtTheme.textColor())
        layout.addView(ScrollView(this).apply {
            addView(preview)
            TuixtTheme.stylePanel(this@PresetImportActivity, this)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(12) })
        confirm = Button(this).apply {
            setText(R.string.portable_preset_confirm)
            isEnabled = false
            TuixtTheme.styleButton(this@PresetImportActivity, this, true)
            setOnClickListener { savePreset() }
        }
        layout.addView(confirm)
        chooseFile = Button(this).apply {
            setText(R.string.portable_preset_choose_file)
            visibility = View.GONE
            TuixtTheme.styleButton(this@PresetImportActivity, this, true)
            setOnClickListener { startActivityForResult(pickerIntent(), PICK_PRESET) }
        }
        layout.addView(chooseFile)
        layout.addView(Button(this).apply {
            setText(R.string.portable_preset_cancel)
            TuixtTheme.styleButton(this@PresetImportActivity, this, false)
            setOnClickListener { if (!saving) finish() }
        })
        setContentView(layout)
        ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left + dp(12), bars.top + dp(12), bars.right + dp(12), bars.bottom + dp(12))
            insets
        }
        ViewCompat.requestApplyInsets(layout)
        readIntent(state)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (saving) { pendingIntent = intent; return }
        setIntent(intent)
        readIntent(null)
    }

    private fun readIntent(state: Bundle?) {
        ++inspection
        worker.execute { staged?.close(); staged = null }
        try {
            if (intent.action == Intent.ACTION_MAIN) {
                if (state == null) startActivityForResult(pickerIntent(), PICK_PRESET)
                else finish() // A recreation cancels inspection rather than retaining untrusted extras.
            } else {
                // Download providers sometimes replace the custom MIME type with ZIP or binary.
                // The private archive inspection remains the authority on package contents.
                val mime = intent.type?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
                require(mime == null || mime in setOf(BackupManager.PRESET_MIME, "application/zip", "application/octet-stream", "application/x-zip-compressed")) { "Unsupported preset MIME type" }
                val uri = when (intent.action) {
                    Intent.ACTION_VIEW -> intent.data ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
                    Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                        ?: intent.clipData?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.uri
                    else -> throw IllegalArgumentException("Unsupported preset action")
                }
                inspect(requireNotNull(uri) { "Preset URI is missing" })
            }
        } catch (error: Exception) { showError(error) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_PRESET) return
        if (resultCode != RESULT_OK) { finish(); return }
        try { inspect(requireNotNull(data?.data) { "Preset URI is missing" }) }
        catch (error: Exception) { showError(error) }
    }

    private fun inspect(uri: Uri) {
        val request = ++inspection
        confirm.visibility = View.VISIBLE
        confirm.isEnabled = false
        confirm.setText(R.string.portable_preset_confirm)
        confirm.setOnClickListener { savePreset() }
        chooseFile.visibility = View.GONE
        preview.setText(R.string.portable_preset_inspecting)
        worker.execute {
            try {
                staged?.close(); staged = null
                require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) { "Only readable content:// preset URIs are supported" }
                // Do not lend this app's private provider access to an untrusted caller.
                // Android strips user-id prefixes when opening URIs; reject them before authority checks.
                require(!uri.authority!!.contains('@')) { "Choose the exported preset from a document provider" }
                val provider = packageManager.resolveContentProvider(uri.authority!!, 0)
                val cachedShare = uri.authority == ohi.andre.consolelauncher.tuils.GenericFileProvider.PROVIDER_NAME &&
                    uri.path?.matches(Regex("""^/cache_files/preset-share/[a-f0-9-]{36}/[A-Za-z0-9_-]+\.retui$""")) == true &&
                    uri.query == null && uri.fragment == null
                require(provider?.applicationInfo?.uid != applicationInfo.uid || cachedShare) { "Choose the exported preset from a document provider" }
                val source = requireNotNull(contentResolver.openInputStream(uri)) { "Unable to read preset URI" }
                val result = BackupManager.inspectPreset(source, File(cacheDir, "preset-import"))
                if (leaving || request != inspection) { result.close(); return@execute }
                staged = result
                val behavior = File(result.folder, XMLPrefsRoot.BEHAVIOR.path)
                val behaviorPreview = if (!behavior.isFile) getString(R.string.portable_preset_no_behavior)
                    else if (result.behaviorLabels != null) PresetManager.sanitizeSelectedBehaviorXml(behavior, result.behaviorLabels!!)
                    else PresetManager.sanitizeShareableXml(behavior, XMLPrefsRoot.BEHAVIOR)
                val text = getString(R.string.portable_preset_preview, result.name, result.sections,
                    result.producerVersion.ifBlank { getString(R.string.portable_preset_unknown_version) }, result.schema, behaviorPreview)
                runOnUiThread {
                    if (!leaving && request == inspection) { preview.text = text; confirm.isEnabled = true }
                }
            } catch (error: Exception) {
                staged?.close(); staged = null
                runOnUiThread { if (!leaving && request == inspection) showError(error) }
            }
        }
    }

    private fun savePreset() {
        confirm.isEnabled = false
        saving = true
        worker.execute {
            try {
                ohi.andre.consolelauncher.tuils.Tuils.init(applicationContext)
                val saved = requireNotNull(staged).save()
                runOnUiThread {
                    if (!leaving) {
                        preview.text = getString(R.string.portable_preset_saved, saved)
                        confirm.setText(R.string.portable_preset_done)
                        confirm.isEnabled = true
                        confirm.setOnClickListener { finish() }
                    }
                    saving = false
                    if (!leaving) pendingIntent?.let { pendingIntent = null; onNewIntent(it) }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    saving = false
                    if (!leaving) {
                        showError(error)
                        pendingIntent?.let { pendingIntent = null; onNewIntent(it) }
                    }
                }
            } finally { staged?.close(); staged = null }
        }
    }

    private fun showError(error: Exception) {
        confirm.isEnabled = false
        confirm.visibility = View.GONE
        chooseFile.visibility = View.VISIBLE
        preview.text = getString(R.string.portable_preset_error, error.displayMessage(this) ?: error.javaClass.simpleName)
    }

    private fun dp(value: Int) = TuixtTheme.dp(this, value.toFloat())

    override fun onDestroy() {
        leaving = true
        worker.execute { staged?.close(); staged = null }
        worker.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val PICK_PRESET = 1
        fun pickerIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(BackupManager.PRESET_MIME, "application/zip", "application/octet-stream"))
        }
    }
}
