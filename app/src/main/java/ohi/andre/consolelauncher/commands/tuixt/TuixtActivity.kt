package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleScreen
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.refreshFromLoadedPrefs
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import android.content.Intent
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getInt
import ohi.andre.consolelauncher.managers.SearchProviderManager
import ohi.andre.consolelauncher.managers.status.AsciiAnimationManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class TuixtActivity : ohi.andre.consolelauncher.localization.LocalizedActivity() {
    private var file: File? = null
    private var recyclerView: RecyclerView? = null
    private var adapter: TuixtAdapter? = null
    private var xmlRoot: XMLPrefsRoot? = null
    private var originalRows: MutableList<TuixtAdapter.SettingsRow>? = null
    private var plainTextEditor: EditText? = null
    private var originalRawText: String? = null
    private var asciiSettingsMode = false
    private val previewHandler = Handler(Looper.getMainLooper())
    private val previewRunnable = Runnable { LauncherActivity.previewIfRunning() }

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        ThemeColorResolver.clearPreview()
        overridePendingTransition(0, 0)
        applyFullscreen(this)

        val intent = getIntent()
        asciiSettingsMode = MODE_ASCII_SETTINGS == intent.getStringExtra(MODE)
        val onlySection = intent.getStringExtra(ONLY_SECTION)
        val excludeSection = intent.getStringExtra(EXCLUDE_SECTION)
        val path = intent.getStringExtra(PATH)
        if (path == null && !asciiSettingsMode) {
            finish()
            return
        }
        file = if (asciiSettingsMode) asciiFile else File(path!!)

        val screen = FrameLayout(this)
        styleScreen(this, screen)
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val root = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(this, 14f), dp(this, 50f), dp(this, 14f), dp(this, 14f))
        stylePanel(this, root)

        val panelLeft = dp(this, 28f)
        val panelTop = dp(this, 34f)
        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        panelParams.setMargins(panelLeft, panelTop, dp(this, 28f), dp(this, 28f))
        contentHost.addView(root, panelParams)

        val header = TextView(this)
        header.setText(
            when {
                asciiSettingsMode -> getString(R.string.editor_tuixtactivity_ascii_settings_4a299)
                onlySection != null -> getString(R.string.editor_tuixtactivity_behavior_70e30, onlySection)
                else -> getString(R.string.editor_tuixtactivity_themer_93fef, file!!.getName())
            }
        )
        styleHeader(this, header)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = panelLeft + dp(this, 38f)
        headerParams.topMargin = panelTop - dp(this, 11f)
        contentHost.addView(header, headerParams)

        // RecyclerView
        recyclerView = RecyclerView(this)
        recyclerView!!.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        recyclerView!!.setLayoutManager(LinearLayoutManager(this))
        if (asciiSettingsMode) {
            root.addView(buildAsciiActions())
        }
        root.addView(recyclerView)

        // Bottom Bar (Search + Buttons)
        val bottomBar = LinearLayout(this)
        bottomBar.setOrientation(LinearLayout.VERTICAL)
        bottomBar.setPadding(10, 10, 10, 10)
        bottomBar.setBackground(rect(this, surfaceColor(), borderColor(), 1.25f))

        // Search Box
        val searchBox = EditText(this)
        searchBox.setHint(getString(R.string.editor_tuixtactivity_search_settings_d25ad))
        styleInput(this, searchBox)
        bottomBar.addView(searchBox)

        // Action Buttons
        val btnLayout = LinearLayout(this)
        btnLayout.setOrientation(LinearLayout.HORIZONTAL)
        btnLayout.setPadding(0, 10, 0, 0)

        val btnCancel = TextView(this)
        btnCancel.setText(getString(R.string.editor_tuixtactivity_cancel_1507c))
        styleButton(this, btnCancel, false)
        btnCancel.setOnClickListener(View.OnClickListener { v: View? -> attemptClose() })
        btnLayout.addView(btnCancel)

        val spacer = View(this)
        spacer.setLayoutParams(LinearLayout.LayoutParams(0, 1, 1f))
        btnLayout.addView(spacer)

        val btnSave = TextView(this)
        btnSave.setText(getString(R.string.editor_tuixtactivity_save_50815))
        styleButton(this, btnSave, true)
        btnSave.setOnClickListener(View.OnClickListener { v: View? ->
            var saved = false
            if (adapter != null) {
                adapter!!.saveAll(this, recyclerView)
                saved = true
            } else if (plainTextEditor != null) {
                try {
                    val currentText = plainTextEditor!!.getText().toString()
                    Tuils.write(file, "", currentText)
                    XMLPrefsManager.dispose()
                    XMLPrefsManager.loadCommons(this)
                    refreshFromLoadedPrefs()
                    originalRawText = currentText
                    saved = true
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.editor_tuixtactivity_error_saving_540ec, e.message), Toast.LENGTH_LONG).show()
                }
            }
            if (saved) {
                ThemeColorResolver.clearPreview()
                Toast.makeText(this, getString(R.string.editor_tuixtactivity_changes_saved_previewing_launcher_8e180), Toast.LENGTH_SHORT)
                    .show()
                LauncherActivity.preview(this)
                styleScreen(this, screen)
                stylePanel(this, root)
                styleHeader(this, header)
                styleInput(this, searchBox)
                styleButton(this, btnCancel, false)
                styleButton(this, btnSave, true)
                adapter?.restyleVisibleControls(recyclerView)
            }
        })
        btnLayout.addView(btnSave)

        bottomBar.addView(btnLayout)
        root.addView(bottomBar)

        val refreshButtonTheme: () -> Unit = {
            styleScreen(this, screen)
            stylePanel(this, root)
            styleHeader(this, header)
            styleInput(this, searchBox)
            bottomBar.setBackground(rect(this, surfaceColor(), borderColor(), 1.25f))
            styleButton(this, btnCancel, false)
            styleButton(this, btnSave, true)
            adapter?.restyleVisibleControls(recyclerView)
            if (xmlRoot == XMLPrefsRoot.THEME) {
                previewHandler.removeCallbacks(previewRunnable)
                previewHandler.postDelayed(previewRunnable, THEME_PREVIEW_DELAY_MS)
            }
        }

        // Load data
        val fileName = file!!.getName().lowercase(Locale.ROOT)
        for (rootEnum in XMLPrefsRoot.entries.toTypedArray()) {
            if (fileName == rootEnum.path()) {
                xmlRoot = rootEnum
                break
            }
        }
        if (xmlRoot == XMLPrefsRoot.THEME) ThemeColorResolver.clearPreview()

        if (asciiSettingsMode) {
            originalRows = buildAsciiSettingsRows()
            adapter = TuixtAdapter(originalRows!!.toMutableList(), null, refreshButtonTheme)
            recyclerView!!.setAdapter(adapter)

            searchBox.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    filter(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        } else if (xmlRoot != null) {
            originalRows = buildRows(xmlRoot!!, file!!).filter {
                when {
                    onlySection != null -> it.section == onlySection
                    excludeSection != null -> it.section != excludeSection
                    else -> true
                }
            }.toMutableList()
            adapter = TuixtAdapter(
                originalRows!!.toMutableList(),
                file,
                refreshButtonTheme,
                ::openSearchProviders,
                accordionSections = true,
                initialSection = onlySection
            )
            recyclerView!!.setAdapter(adapter)
            if (xmlRoot == XMLPrefsRoot.THEME) {
                root.addView(buildResetAdvancedThemeAction(), 0)
            }

            searchBox.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    filter(s.toString())
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        } else {
            recyclerView!!.setVisibility(View.GONE)
            searchBox.setVisibility(View.GONE)

            plainTextEditor = EditText(this)
            plainTextEditor!!.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            plainTextEditor!!.setGravity(Gravity.TOP)
            TuixtTheme.styleInput(this, plainTextEditor!!)

            try {
                val fis = FileInputStream(file)
                originalRawText = Tuils.convertStreamToString(fis)
                plainTextEditor!!.setText(originalRawText)
                fis.close()
            } catch (e: Exception) {
                originalRawText = ""
                plainTextEditor!!.setText("")
            }

            root.addView(plainTextEditor, 2)
        }

        setContentView(screen)
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreen(this)
        }
    }

    private val asciiFile: File
        get() = File(Tuils.getFolder(), "ascii.txt")

    private fun buildAsciiActions(): View {
        val actions = LinearLayout(this)
        actions.setOrientation(LinearLayout.VERTICAL)
        actions.setPadding(0, 0, 0, dp(this, 10f))

        addAsciiAction(actions, getString(R.string.editor_tuixtactivity_ascii_txt_2739a)) {
            openAsciiTxt()
        }
        addAsciiAction(actions, getString(R.string.editor_tuixtactivity_import_ascii_txt_9b586)) {
            launchAsciiImportPicker()
        }

        return actions
    }

    private fun addAsciiAction(container: LinearLayout, label: String, action: () -> Unit) {
        val button = TextView(this)
        button.setText(label)
        button.setGravity(Gravity.CENTER)
        styleButton(this, button, false)
        button.setOnClickListener { action() }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 8f))
        container.addView(button, params)
    }

    private fun buildResetAdvancedThemeAction(): View {
        val button = TextView(this)
        button.text = getString(R.string.editor_tuixtactivity_reset_advanced_colors)
        styleButton(this, button, false)
        button.setOnClickListener {
            TuixtDialog.showConfirm(
                this,
                getString(R.string.editor_tuixtactivity_reset_advanced_colors),
                getString(R.string.editor_tuixtactivity_reset_advanced_colors_message),
                getString(R.string.editor_tuixtactivity_reset),
                getString(R.string.editor_tuixtactivity_keep_editing_ced7d),
                ConfirmAction { adapter?.resetAdvancedThemeColors() }
            )
        }
        button.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(this@TuixtActivity, 8f) }
        return button
    }

    private fun buildAsciiSettingsRows(): MutableList<TuixtAdapter.SettingsRow> {
        val rows: MutableList<TuixtAdapter.SettingsRow> = ArrayList()

        addSection(rows, getString(R.string.editor_tuixtactivity_ascii_txt_2739a))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.show_ascii, getString(R.string.editor_tuixtactivity_ascii_txt_2739a)))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.show_ascii_landscape, getString(R.string.editor_tuixtactivity_ascii_txt_2739a)))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.ascii_max_lines, getString(R.string.editor_tuixtactivity_ascii_txt_2739a)))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.ascii_pane_height_rows, getString(R.string.editor_tuixtactivity_ascii_txt_2739a)))

        addSection(rows, getString(R.string.editor_tuixtactivity_animation_62afd))
        rows.add(TuixtAdapter.SettingsRow.setting(Behavior.ascii_animation, getString(R.string.editor_tuixtactivity_animation_62afd)))
        rows.add(TuixtAdapter.SettingsRow.setting(Behavior.ascii_animation_frame_delay_ms, getString(R.string.editor_tuixtactivity_animation_62afd)))
        rows.add(TuixtAdapter.SettingsRow.setting(Behavior.ascii_animation_max_file_kb, getString(R.string.editor_tuixtactivity_animation_62afd)))

        addSection(rows, getString(R.string.editor_tuixtactivity_layout_972ad))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.ascii_index, getString(R.string.editor_tuixtactivity_layout_972ad)))
        rows.add(TuixtAdapter.SettingsRow.setting(Ui.ascii_status_alignment, getString(R.string.editor_tuixtactivity_layout_972ad)))

        addSection(rows, getString(R.string.editor_tuixtactivity_colors_88d5e))
        rows.add(TuixtAdapter.SettingsRow.setting(Theme.ascii_text_color, getString(R.string.editor_tuixtactivity_colors_88d5e)))
        rows.add(TuixtAdapter.SettingsRow.setting(Theme.ascii_status_background_color, getString(R.string.editor_tuixtactivity_colors_88d5e)))
        rows.add(TuixtAdapter.SettingsRow.setting(Theme.ascii_status_text_shadow_color, getString(R.string.editor_tuixtactivity_colors_88d5e)))

        return rows
    }

    private fun filter(query: String) {
        val rows = originalRows ?: return
        if (query.trim().isEmpty()) {
            adapter!!.updateRows(rows.toMutableList(), false)
            return
        }

        val filtered: MutableList<TuixtAdapter.SettingsRow> = ArrayList()
        var pendingSection: TuixtAdapter.SettingsRow? = null
        val lower = query.lowercase(Locale.getDefault())
        for (row in rows) {
            if (row.sectionHeader) {
                pendingSection = row
                continue
            }

            val item = row.item ?: continue
            val label = item.label()!!.lowercase(Locale.getDefault())
            val info = item.info(this).lowercase(Locale.getDefault())
            if (label.contains(lower) || info.contains(lower)) {
                if (pendingSection != null && (filtered.isEmpty() || filtered.last().section != pendingSection.section)) {
                    filtered.add(pendingSection)
                }
                filtered.add(row)
            }
        }
        adapter!!.updateRows(filtered, true)
    }

    private fun buildRows(root: XMLPrefsRoot, source: File): MutableList<TuixtAdapter.SettingsRow> {
        val remaining: LinkedHashMap<String, XMLPrefsSave> = LinkedHashMap<String, XMLPrefsSave>()
        for (save in root.enums) {
            if (save === Behavior.toggle_output_state) {
                continue
            }
            if (save === Ui.auto_color_pick) {
                continue
            }
            if (XMLPrefsManager.isAsciiArtSetting(save)) {
                continue
            }
            if (XMLPrefsManager.isAdvancedSuggestionSetting(save)) {
                continue
            }
            remaining[save.label()!!] = save
        }

        val groupedRows: LinkedHashMap<String, MutableList<XMLPrefsSave>> =
            LinkedHashMap<String, MutableList<XMLPrefsSave>>()
        try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source)
            val xmlRoot = doc.documentElement
            val children = xmlRoot.childNodes
            if (!hasSectionComments(children)) {
                val rows: MutableList<TuixtAdapter.SettingsRow> = ArrayList()
                addDefaultSectionRows(rows, remaining)
                return rows
            }

            var activeSection: String? = null
            for (index in 0..<children.length) {
                val node = children.item(index)
                if (node.nodeType == Node.COMMENT_NODE) {
                    val section = parseSectionComment(node.nodeValue)
                    if (section != null) {
                        activeSection = section
                    }
                } else if (node.nodeType == Node.ELEMENT_NODE) {
                    val element = node as Element
                    val save = remaining.remove(element.nodeName) ?: continue
                    val section = activeSection ?: XMLPrefsManager.sectionFor(save)
                    addGroupedRow(groupedRows, section, save)
                }
            }
        } catch (ignored: Exception) {
        }

        for (save in remaining.values) {
            val section = XMLPrefsManager.sectionFor(save)
            addGroupedRow(groupedRows, section, save)
        }

        return flattenGroupedRows(groupedRows)
    }

    private fun addDefaultSectionRows(
        rows: MutableList<TuixtAdapter.SettingsRow>,
        remaining: LinkedHashMap<String, XMLPrefsSave>
    ) {
        val groupedRows: LinkedHashMap<String, MutableList<XMLPrefsSave>> =
            LinkedHashMap<String, MutableList<XMLPrefsSave>>()
        val iterator = remaining.entries.iterator()
        while (iterator.hasNext()) {
            val save = iterator.next().value
            val section = XMLPrefsManager.sectionFor(save)
            addGroupedRow(groupedRows, section, save)
            iterator.remove()
        }
        rows.addAll(flattenGroupedRows(groupedRows))
    }

    private fun hasSectionComments(children: org.w3c.dom.NodeList): Boolean {
        for (index in 0..<children.length) {
            val node = children.item(index)
            if (node.nodeType == Node.COMMENT_NODE && parseSectionComment(node.nodeValue) != null) {
                return true
            }
        }
        return false
    }

    private fun parseSectionComment(raw: String?): String? {
        val value = raw?.trim() ?: return null
        if (!value.startsWith("#")) {
            return null
        }
        val label = value.removePrefix("#").trim()
        return if (label.isEmpty()) null else label
    }

    private fun addSection(rows: MutableList<TuixtAdapter.SettingsRow>, section: String) {
        if (rows.isNotEmpty() && rows.last().section == section) {
            return
        }
        rows.add(TuixtAdapter.SettingsRow.section(section))
    }

    private fun addGroupedRow(
        rows: LinkedHashMap<String, MutableList<XMLPrefsSave>>,
        section: String,
        save: XMLPrefsSave
    ) {
        var bucket = rows[section]
        if (bucket == null) {
            bucket = ArrayList<XMLPrefsSave>()
            rows[section] = bucket
        }
        bucket.add(save)
    }

    private fun flattenGroupedRows(
        groupedRows: LinkedHashMap<String, MutableList<XMLPrefsSave>>
    ): MutableList<TuixtAdapter.SettingsRow> {
        val rows: MutableList<TuixtAdapter.SettingsRow> = ArrayList()
        for (entry in groupedRows.entries) {
            val section = entry.key
            addSection(rows, section)
            for (save in entry.value) {
                rows.add(TuixtAdapter.SettingsRow.setting(save, section))
            }
        }
        return rows
    }

    private fun openAsciiTxt() {
        val file = asciiFile
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (!file.exists()) {
                Tuils.write(file, "", "")
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.editor_tuixtactivity_could_not_create_ascii_txt_62630, e.message), Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, TuixtActivity::class.java)
        intent.putExtra(PATH, file.absolutePath)
        startActivityForResult(intent, ASCII_TXT_REQUEST)
    }

    private fun openSearchProviders() {
        SearchProviderManager.load()
        val intent = Intent(this, TuixtActivity::class.java)
        intent.putExtra(PATH, SearchProviderManager.file().absolutePath)
        startActivityForResult(intent, LauncherActivity.TUIXT_REQUEST)
        overridePendingTransition(0, 0)
    }

    private fun launchAsciiImportPicker() {
        notifyAsciiImport(getString(R.string.editor_tuixtactivity_opening_ascii_file_picker_0304c), false)
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("*/*")
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf<String>("text/plain", "text/html", "text/*", "application/octet-stream")
        )
        try {
            startActivityForResult(intent, ASCII_IMPORT_REQUEST)
        } catch (e: ActivityNotFoundException) {
            notifyAsciiImport(getString(R.string.editor_tuixtactivity_ascii_file_picker_is_unavailable_on_this_d_f32c0), true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.i("TUI-ASCII", "onActivityResult request=" + requestCode + " result=" + resultCode + " data=" + (data != null))
        if (requestCode == ASCII_TXT_REQUEST) {
            if (resultCode == SAVE_PRESSED) {
                reloadLauncherForAscii(getString(R.string.editor_tuixtactivity_ascii_txt_saved_55025))
            }
        } else if (requestCode == ASCII_IMPORT_REQUEST) {
            handleAsciiImportResult(resultCode, data)
        }
    }

    private fun handleAsciiImportResult(resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK || data == null || data.data == null) {
            notifyAsciiImport(getString(R.string.editor_tuixtactivity_ascii_import_cancelled_1ca68), false)
            return
        }

        val uri = data.data ?: return
        var sourceName = getDisplayName(uri)
        if (sourceName == null || sourceName.trim { it <= ' ' }.isEmpty()) {
            sourceName = uri.lastPathSegment
        }
        if (!isAsciiImportFileName(sourceName)) {
            notifyAsciiImport(getString(R.string.editor_tuixtactivity_choose_a_txt_or_html_ascii_file_4714f), true)
            return
        }

        val maxBytes = maxOf(32, getInt(Behavior.ascii_animation_max_file_kb)) * 1024
        val htmlSource = sourceName?.lowercase(Locale.ROOT)?.endsWith(".html") == true ||
            sourceName?.lowercase(Locale.ROOT)?.endsWith(".htm") == true
        val sourceLimit = if (htmlSource) maxBytes.coerceAtMost(Int.MAX_VALUE / 16) * 16 else maxBytes
        val text = try {
            decodeUtf8(readUriBytes(uri, sourceLimit))
        } catch (e: Exception) {
            notifyAsciiImport(
                if (e.message == null) getString(R.string.editor_tuixtactivity_ascii_import_failed_73d26) else getString(R.string.editor_tuixtactivity_ascii_import_failed_4b2e0, e.message),
                true
            )
            return
        }

        if (text.trim { it <= ' ' }.isEmpty()) {
            notifyAsciiImport(getString(R.string.editor_tuixtactivity_ascii_import_failed_empty_file_65e17), true)
            return
        }

        val frameCount = AsciiAnimationManager.supportedFrameCount(text)
        try {
            Tuils.write(asciiFile, "", text)
        } catch (e: Exception) {
            notifyAsciiImport(getString(R.string.editor_tuixtactivity_could_not_write_ascii_txt_5d089, e.message), true)
            return
        }

        val message = if (frameCount < 2) {
            if (htmlSource) getString(R.string.editor_tuixtactivity_imported_colored_ascii_html_9668a) else getString(R.string.editor_tuixtactivity_imported_ascii_txt_e5ed8)
        } else if (LauncherSettings.getBoolean(Behavior.ascii_animation)) {
            getString(R.string.editor_tuixtactivity_imported_animated_ascii_frames_d5b9c, frameCount)
        } else {
            getString(R.string.editor_tuixtactivity_imported_frames_enable_animated_ascii_to_p_6960c, frameCount)
        }
        reloadLauncherForAscii(message)
    }

    private fun readUriBytes(uri: Uri, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        getContentResolver().openInputStream(uri).use { input ->
            checkNotNull(input) { getString(R.string.editor_tuixtactivity_unable_to_read_selected_file_8a911) }
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) {
                    break
                }
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException(getString(R.string.editor_tuixtactivity_file_exceeds_kb_limit_9bba2, (maxBytes / 1024)))
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun getDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        try {
            cursor = getContentResolver().query(
                uri,
                arrayOf<String>(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
        return null
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        return StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }

    private fun isAsciiImportFileName(name: String?): Boolean {
        if (name == null) {
            return false
        }
        val lower = name.lowercase(Locale.ROOT)
        return lower.endsWith(".txt") || lower.endsWith(".html") || lower.endsWith(".htm")
    }

    private fun reloadLauncherForAscii(message: String) {
        notifyAsciiImport(message, false)
        LauncherActivity.preview(this)
    }

    private fun notifyAsciiImport(message: String, error: Boolean) {
        if (error) {
            Log.w("TUI-ASCII", message)
        } else {
            Log.i("TUI-ASCII", message)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        attemptClose()
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            discardThemePreview()
            setResult(BACK_PRESSED)
            finish()
            overridePendingTransition(0, 0)
            return
        }

        TuixtDialog.showConfirm(
            this,
            getString(R.string.editor_tuixtactivity_discard_changes_f99ee),
            getString(R.string.editor_tuixtactivity_unsaved_settings_changes_will_be_lost_697a2),
            getString(R.string.editor_tuixtactivity_discard_36fff),
            getString(R.string.editor_tuixtactivity_keep_editing_ced7d),
            ConfirmAction {
                discardThemePreview()
                setResult(BACK_PRESSED)
                finish()
                overridePendingTransition(0, 0)
            })
    }

    override fun onDestroy() {
        previewHandler.removeCallbacks(previewRunnable)
        val hadPreview = ThemeColorResolver.hasPreview()
        ThemeColorResolver.clearPreview()
        if (hadPreview) LauncherActivity.previewIfRunning()
        super.onDestroy()
    }

    private fun discardThemePreview() {
        previewHandler.removeCallbacks(previewRunnable)
        ThemeColorResolver.clearPreview()
        LauncherActivity.previewIfRunning()
    }

    private fun hasUnsavedChanges(): Boolean {
        if (adapter != null && adapter!!.hasPendingChanges()) {
            return true
        }
        if (plainTextEditor != null) {
            return !TextUtils.equals(originalRawText, plainTextEditor!!.getText().toString())
        }
        return false
    }

    companion object {
        const val PATH: String = "path"
        const val MODE: String = "mode"
        const val MODE_ASCII_SETTINGS: String = "ascii_settings"
        const val ONLY_SECTION: String = "only_section"
        const val EXCLUDE_SECTION: String = "exclude_section"
        const val ERROR_KEY: String = "error"
        const val BACK_PRESSED: Int = 2
        const val SAVE_PRESSED: Int = 3
        private const val ASCII_TXT_REQUEST: Int = 11
        private const val ASCII_IMPORT_REQUEST: Int = 12
        private const val THEME_PREVIEW_DELAY_MS = 180L
    }
}
