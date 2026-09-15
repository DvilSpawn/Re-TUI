package ohi.andre.consolelauncher.localization

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme

class LanguagePackActivity : LocalizedComponentActivity() {
    private var busy = false
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            render()
            Thread {
                val imported = runCatching { contentResolver.openInputStream(uri)!!.use(LanguagePacks::import) }
                runOnUiThread {
                    busy = false
                    if (isDestroyed) return@runOnUiThread
                    imported.onSuccess { LanguagePacks.select(it.id); recreate() }
                        .onFailure { toast(R.string.language_packs_invalid); render() }
                }
            }.start()
        }
    }
    private val exporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val success = runCatching {
                contentResolver.openOutputStream(uri)!!.use { out -> assets.open("localization/english-template.zip").use { it.copyTo(out) } }
            }.isSuccess
            toast(if (success) R.string.language_packs_exported else R.string.language_packs_failed)
        }
    }
    override fun onCreate(state: Bundle?) { super.onCreate(state); render() }
    private fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()
    private fun render() {
        title = getString(R.string.language_packs_title)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val inset = TuixtTheme.dp(this@LanguagePackActivity, 16f)
            setPadding(inset, inset, inset, inset)
        }
        TuixtTheme.styleScreen(this, content)
        fun label(text: String, header: Boolean = false) {
            content.addView(TextView(this).apply {
                this.text = text
                setTextColor(TuixtTheme.textColor())
                if (header) TuixtTheme.styleHeader(this@LanguagePackActivity, this)
                setPadding(0, 12, 0, 12)
            })
        }
        fun button(text: String, action: () -> Unit) {
            content.addView(TextView(this).apply {
                this.text = text
                minHeight = TuixtTheme.dp(this@LanguagePackActivity, 48f)
                TuixtTheme.styleButton(this@LanguagePackActivity, this, false)
                isEnabled = !busy
                setOnClickListener { runCatching(action).onFailure { toast(R.string.language_packs_failed) } }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
        }
        label(getString(R.string.language_packs_title), true)
        label(getString(R.string.language_packs_intro))
        val activeName = LanguagePacks.active?.nativeName ?: getString(if (LanguagePacks.selectedId() == "en") R.string.language_packs_english_name else R.string.language_packs_system_name)
        label(getString(R.string.language_packs_active, activeName))
        button(getString(R.string.language_packs_system)) { LanguagePacks.select(""); recreate() }
        button(getString(R.string.language_packs_english)) { LanguagePacks.select("en"); recreate() }
        button(getString(if (busy) R.string.language_packs_working else R.string.language_packs_import)) { picker.launch(arrayOf("*/*")) }
        button(getString(R.string.language_packs_browse)) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DvilSpawn/Re-TUI/releases"))) }
        button(getString(R.string.language_packs_template)) { exporter.launch("retui-launcher-english-template.zip") }
        for (pack in LanguagePacks.installed()) {
            label(getString(R.string.language_packs_version, pack.nativeName, pack.version, pack.texts.size))
            button(getString(R.string.language_packs_use, pack.nativeName)) { LanguagePacks.select(pack.id); recreate() }
            button(getString(R.string.language_packs_remove, pack.nativeName)) {
                AlertDialog.Builder(this).setMessage(getString(R.string.language_packs_remove_confirm, pack.nativeName))
                    .setNegativeButton(R.string.language_packs_cancel, null)
                    .setPositiveButton(getString(R.string.language_packs_remove, pack.nativeName)) { _, _ ->
                        runCatching { LanguagePacks.remove(pack.id); recreate() }.onFailure { toast(R.string.language_packs_failed) }
                    }.show()
            }
        }
        setContentView(ScrollView(this).apply {
            addView(content)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            androidx.core.view.ViewCompat.requestApplyInsets(this)
        })
    }
}
