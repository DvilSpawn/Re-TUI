package ohi.andre.consolelauncher.commands.tuixt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.localization.LocalizedComponentActivity
import ohi.andre.consolelauncher.managers.BackupManager
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.tuils.displayMessage
import java.io.File
import java.util.concurrent.Executors

/** The exported boundary can only inspect and save shareable presets, never restore backups. */
class PresetImportActivity : LocalizedComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private var staged: BackupManager.StagedPreset? = null
    @Volatile private var leaving = false
    private var saving = false
    private lateinit var preview: TextView
    private lateinit var confirm: Button

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!saving) finish() }
        })
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        preview = TextView(this).apply { setText(R.string.portable_preset_inspecting); setTextIsSelectable(true) }
        layout.addView(ScrollView(this).apply { addView(preview) }, LinearLayout.LayoutParams(-1, 0, 1f))
        confirm = Button(this).apply {
            setText(R.string.portable_preset_confirm)
            isEnabled = false
            setOnClickListener { savePreset() }
        }
        layout.addView(confirm)
        layout.addView(Button(this).apply {
            setText(R.string.portable_preset_cancel)
            setOnClickListener { if (!saving) finish() }
        })
        setContentView(layout)
        try {
            if (intent.action == Intent.ACTION_MAIN) {
                if (state == null) startActivityForResult(pickerIntent(), PICK_PRESET)
                else finish() // A recreation cancels inspection rather than retaining untrusted extras.
            } else {
                require(intent.type == BackupManager.PRESET_MIME) { "Unsupported preset MIME type" }
                val uri = when (intent.action) {
                    Intent.ACTION_VIEW -> intent.data
                    Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
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
        worker.execute {
            try {
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
                if (leaving) { result.close(); return@execute }
                staged = result
                val behavior = File(result.folder, XMLPrefsRoot.BEHAVIOR.path)
                val behaviorPreview = if (!behavior.isFile) getString(R.string.portable_preset_no_behavior)
                    else if (result.behaviorLabels != null) PresetManager.sanitizeSelectedBehaviorXml(behavior, result.behaviorLabels!!)
                    else PresetManager.sanitizeShareableXml(behavior, XMLPrefsRoot.BEHAVIOR)
                val text = getString(R.string.portable_preset_preview, result.name, result.sections,
                    result.producerVersion.ifBlank { getString(R.string.portable_preset_unknown_version) }, result.schema, behaviorPreview)
                runOnUiThread {
                    if (!leaving) { preview.text = text; confirm.isEnabled = true }
                }
            } catch (error: Exception) {
                staged?.close(); staged = null
                runOnUiThread { if (!leaving) showError(error) }
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
                }
            } catch (error: Exception) {
                runOnUiThread { saving = false; if (!leaving) showError(error) }
            } finally { staged?.close(); staged = null }
        }
    }

    private fun showError(error: Exception) {
        confirm.isEnabled = false
        preview.text = getString(R.string.portable_preset_error, error.displayMessage(this) ?: error.javaClass.simpleName)
    }

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
