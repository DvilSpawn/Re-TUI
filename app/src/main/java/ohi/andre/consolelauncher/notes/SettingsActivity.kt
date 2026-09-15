package ohi.andre.consolelauncher.notes


import ohi.andre.consolelauncher.R
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

class SettingsActivity : ohi.andre.consolelauncher.localization.LocalizedActivity() {
    companion object {
        private const val REQ_FONT = 1
        private const val MAX_FONT_BYTES = 8L * 1024L * 1024L
    }

    private lateinit var theme: RetuiTheme

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setResult(RESULT_OK)
        rebuild()
    }

    private fun rebuild() {
        theme = RetuiTheme.receive(this, null)
        val root = FrameLayout(this).apply {
            tag = "retui_edge_to_edge"
            clipChildren = false
            clipToPadding = false
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        shell.addView(retuiHeaderTab(this@SettingsActivity.getString(R.string.notes_settingsactivity_re_member_settings_0aff3)).apply {
            (getChildAt(0) as? TextView)?.translationY = 0f
        }, LinearLayout.LayoutParams(-1, dp(34)))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(24), dp(12), dp(16))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content, FrameLayout.LayoutParams(-1, -2))
            tag = "retui_panel"
        }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(dp(16), -dp(17), dp(16), 0)
        })

        content.addView(appearancePane())
        content.addView(fontPane(), paneParams())
        content.addView(scalePane(), paneParams())
        content.addView(previewPane(), paneParams())

        val toolbar = LinearLayout(this).apply {
            tag = "retui_toolbar"
            gravity = Gravity.CENTER
            addView(command(this@SettingsActivity.getString(R.string.notes_settingsactivity_back_587ea)) { finish() }, LinearLayout.LayoutParams(dp(140), dp(40)))
        }
        shell.addView(toolbar, LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(dp(16), dp(8), dp(16), 0)
        })
        finishRetui(root, theme)
        shell.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun appearancePane() = pane(this@SettingsActivity.getString(R.string.notes_settingsactivity_appearance_13636)).apply {
        addView(Switch(this@SettingsActivity).apply {
            text = this@SettingsActivity.getString(R.string.notes_settingsactivity_use_launcher_appearance_9c22d)
            isChecked = RetuiTheme.launcherAppearanceEnabled(this@SettingsActivity)
            thumbTintList = ColorStateList.valueOf(theme.buttonText)
            trackTintList = ColorStateList.valueOf(theme.border)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            tag = "retui_input"
            setOnCheckedChangeListener { _, enabled ->
                RetuiTheme.setLauncherAppearanceEnabled(this@SettingsActivity, enabled)
                post(::rebuild)
            }
        }, LinearLayout.LayoutParams(-1, dp(52)))
    }

    private fun fontPane() = pane(this@SettingsActivity.getString(R.string.notes_settingsactivity_font_d42e4)).apply {
        addView(TextView(this@SettingsActivity).apply {
            text = RetuiTheme.importedFontLabel(this@SettingsActivity) ?: this@SettingsActivity.getString(R.string.notes_settingsactivity_launcher_default_monospace_fd9b4)
            tag = "retui_h3"
            setPadding(dp(8), dp(8), dp(8), dp(8))
        })
        addView(LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(command(this@SettingsActivity.getString(R.string.notes_settingsactivity_import_font_f6728), ::pickFont), LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(4) })
            addView(command(this@SettingsActivity.getString(R.string.notes_settingsactivity_use_default_8e95d)) {
                RetuiTheme.clearImportedFont(this@SettingsActivity)
                rebuild()
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(4) })
        }, LinearLayout.LayoutParams(-1, dp(44)))
        addView(TextView(this@SettingsActivity).apply {
            text = this@SettingsActivity.getString(R.string.notes_settingsactivity_imports_only_the_font_you_choose_into_re_m_90643)
            setPadding(dp(8), dp(10), dp(8), 0)
        })
    }

    private fun scalePane() = pane(this@SettingsActivity.getString(R.string.notes_settingsactivity_font_scale_43940)).apply {
        val label = TextView(this@SettingsActivity).apply {
            text = java.text.NumberFormat.getPercentInstance().format(RetuiTheme.fontScalePercent(this@SettingsActivity) / 100.0)
            tag = "retui_h3"
            gravity = Gravity.CENTER
        }
        addView(label, LinearLayout.LayoutParams(-1, dp(36)))
        addView(SeekBar(this@SettingsActivity).apply {
            max = 75
            progress = RetuiTheme.fontScalePercent(this@SettingsActivity) - 75
            progressTintList = ColorStateList.valueOf(theme.border)
            thumbTintList = ColorStateList.valueOf(theme.buttonText)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, value: Int, fromUser: Boolean) {
                    label.text = java.text.NumberFormat.getPercentInstance().format((value + 75) / 100.0)
                }
                override fun onStartTrackingTouch(view: SeekBar?) = Unit
                override fun onStopTrackingTouch(view: SeekBar) {
                    RetuiTheme.setFontScalePercent(this@SettingsActivity, view.progress + 75)
                    rebuild()
                }
            })
        }, LinearLayout.LayoutParams(-1, dp(48)))
    }

    private fun previewPane() = pane(this@SettingsActivity.getString(R.string.notes_settingsactivity_type_scale_f625d)).apply {
        listOf(
            this@SettingsActivity.getString(R.string.notes_settingsactivity_h1_note_title_08053) to "retui_h1",
            this@SettingsActivity.getString(R.string.notes_settingsactivity_h2_section_d9ffa) to "retui_h2",
            this@SettingsActivity.getString(R.string.notes_settingsactivity_h3_label_4d5f9) to "retui_h3",
            this@SettingsActivity.getString(R.string.notes_settingsactivity_body_the_quick_brown_fox_jumps_over_the_la_5c0c6) to "retui_body"
        ).forEach { (value, style) ->
            addView(TextView(this@SettingsActivity).apply {
                text = value
                tag = style
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun pane(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = "retui_card"
        setPadding(dp(10), dp(12), dp(10), dp(10))
        addView(retuiSectionHeader(title), LinearLayout.LayoutParams(-1, dp(40)))
    }

    private fun paneParams() = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }

    private fun command(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        contentDescription = label
        tag = "retui_accent"
        setOnClickListener { action() }
    }

    private fun pickFont() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, REQ_FONT)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FONT || resultCode != RESULT_OK) return
        data?.data?.let(::importFont)
    }

    private fun importFont(uri: Uri) {
        runCatching {
            val target = RetuiTheme.importedFontFile(this)
            target.parentFile?.mkdirs()
            val temporary = File.createTempFile("font-", ".tmp", target.parentFile)
            try {
                val input = contentResolver.openInputStream(uri) ?: error(this@SettingsActivity.getString(R.string.notes_settingsactivity_font_cannot_be_read_88b9c))
                input.use { source ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_FONT_BYTES) error(this@SettingsActivity.getString(R.string.notes_settingsactivity_font_is_larger_than_8_mib_29856))
                            output.write(buffer, 0, count)
                        }
                        output.fd.sync()
                    }
                }
                if (temporary.length() == 0L) error(this@SettingsActivity.getString(R.string.notes_settingsactivity_font_is_empty_02efe))
                Typeface.createFromFile(temporary)
                val atomic = AtomicFile(target)
                val output = atomic.startWrite()
                try {
                    FileInputStream(temporary).use { it.copyTo(output) }
                    output.fd.sync()
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
                RetuiTheme.setImportedFontLabel(this, displayName(uri))
            } finally {
                temporary.delete()
            }
        }.onSuccess {
            Toast.makeText(this, this@SettingsActivity.getString(R.string.notes_settingsactivity_font_imported_8891b), Toast.LENGTH_SHORT).show()
            rebuild()
        }.onFailure {
            Toast.makeText(this, it.message ?: this@SettingsActivity.getString(R.string.notes_settingsactivity_could_not_import_font_69b2e), Toast.LENGTH_LONG).show()
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: this@SettingsActivity.getString(R.string.notes_settingsactivity_custom_font_1100c)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
