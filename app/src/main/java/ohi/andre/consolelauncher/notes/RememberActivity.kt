package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.tuils.displayMessage
import ohi.andre.consolelauncher.R

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.ClipData
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class RememberActivity : ohi.andre.consolelauncher.localization.LocalizedComponentActivity() {
    companion object { private const val REQ_GALLERY = 42; private const val REQ_AUTH = 43; private const val REQ_SETTINGS = 44 }
    private enum class Surface { HOME, EDITOR }
    private enum class BarMode { DEFAULT, ADD, SELECT, RENAME, NEW_FOLDER, DELETE }
    private sealed class TreeItem {
        data class FolderItem(val folder: NoteFolder) : TreeItem()
        data class NoteItem(val note: Note) : TreeItem()
        object Unfiled : TreeItem()
    }

    private lateinit var theme: RetuiTheme
    private lateinit var store: NoteStore
    private lateinit var library: NoteLibrary
    private lateinit var surfaceHost: FrameLayout
    private lateinit var treeHost: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var homeScroll: ScrollView
    private lateinit var editorScroll: ScrollView
    private lateinit var editor: MarkdownEditText
    private lateinit var markdownToggle: CheckBox
    private lateinit var note: Note
    private val redactions by lazy(::RedactionCrypto)
    private val expandedFolders = mutableSetOf<String>()
    private var storageAvailable = true
    private var loadedRevision = -1L
    private var surface = Surface.HOME
    private var selected: TreeItem? = null
    private var mode = BarMode.DEFAULT
    private var input: EditText? = null
    private var isNew = false
    private var deleted = false
    private var revealed = false
    private var pendingAuthentication: (() -> Unit)? = null
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveTask = Runnable { persistEditor() }
    private val popupHandler = Handler(Looper.getMainLooper())
    private val popupTask = Runnable { showSelectionPopup() }
    private var selectionPopup: PopupWindow? = null
    private var headingPopup: PopupWindow? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!::library.isInitialized) finish()
                else if (surface == Surface.EDITOR) showHome()
                else if (mode != BarMode.DEFAULT || selected != null) clearSelection()
                else finish()
            }
        })
        theme = RetuiTheme.receive(this, intent)
        store = NoteStore(this)
        NoteExporter(this).clearExports()
        library = loadStarterLibrary() ?: return
        buildUi()
        (state?.getString("open_note") ?: intent.getStringExtra("note_id"))?.let(::openNote)
        state?.getStringArrayList("expanded_folders")?.let { expandedFolders.addAll(it) }
        if (surface == Surface.HOME) renderTree()
        else if (state != null) {
            markdownToggle.isChecked = state.getBoolean("raw_mode")
            editor.setSelection(state.getInt("cursor", 0).coerceIn(0, editor.length()))
            editorScroll.post { editorScroll.scrollTo(0, state.getInt("editor_scroll")) }
        }
    }

    override fun onNewIntent(next: Intent) {
        super.onNewIntent(next)
        refreshFromDisk()
        setIntent(next)
        val requestedNote = next?.getStringExtra("note_id")
        if (next?.extras?.keySet()?.any { it in PreviewContract.Visual.THEME_KEYS || it in PreviewContract.Visual.FRAME_KEYS } == true) {
            refreshAppearance(next)
        }
        if (next?.getBooleanExtra("show_library", false) == true) showHome()
        if (requestedNote != null && !(surface == Surface.EDITOR && ::note.isInitialized && note.id == requestedNote)) {
            openNote(requestedNote)
        }
    }

    private fun refreshAppearance(payload: Intent?) {
        val oldSurface = surface
        val oldSelected = selected
        val oldMode = mode
        val homeY = if (::homeScroll.isInitialized) homeScroll.scrollY else 0
        val editorY = if (::editorScroll.isInitialized) editorScroll.scrollY else 0
        val raw = oldSurface == Surface.EDITOR && ::editor.isInitialized && editor.rawMode
        val selectionStart = if (oldSurface == Surface.EDITOR && ::editor.isInitialized) editor.selectionStart else 0
        val selectionEnd = if (oldSurface == Surface.EDITOR && ::editor.isInitialized) editor.selectionEnd else 0
        if (oldSurface == Surface.EDITOR && ::editor.isInitialized && !revealed) {
            note.markdown = editor.markdown()
            MarkdownDocument.title(note.markdown)?.let { note.title = it }
        }
        revealed = false
        saveHandler.removeCallbacks(saveTask)
        dismissSelectionPopup()
        theme = RetuiTheme.receive(this, payload)
        surface = Surface.HOME
        buildUi()
        if (oldSurface == Surface.EDITOR && ::note.isInitialized) {
            showEditor()
            markdownToggle.isChecked = raw
            editor.post {
                val length = editor.length()
                editor.setSelection(selectionStart.coerceIn(0, length), selectionEnd.coerceIn(0, length))
                editorScroll.scrollTo(0, editorY)
            }
        } else {
            selected = oldSelected
            mode = oldMode
            renderTree()
            renderToolbar()
            homeScroll.post { homeScroll.scrollTo(0, homeY) }
        }
    }

    private fun buildUi() {
        val root = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            tag = "retui_edge_to_edge"
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        shell.addView(retuiIdentityTab().apply {
            (getChildAt(0) as? TextView)?.translationY = 0f
        }, LinearLayout.LayoutParams(-1, dp(34)))

        surfaceHost = FrameLayout(this).apply {
            tag = "retui_panel"
            setOnClickListener { clearSelection() }
        }
        shell.addView(surfaceHost, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            setMargins(dp(16), -dp(17), dp(16), 0)
        })

        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), 0, dp(6), 0)
            tag = "retui_toolbar"
        }
        shell.addView(toolbar, LinearLayout.LayoutParams(-1, dp(44)).apply {
            setMargins(dp(16), dp(8), dp(16), 0)
        })

        showHome()
        finishRetui(root, theme)
        shell.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun showHome() {
        saveHandler.removeCallbacks(saveTask)
        dismissSelectionPopup()
        if (surface == Surface.EDITOR && !persistEditor()) return
        surface = Surface.HOME
        selected = null
        mode = BarMode.DEFAULT
        hideKeyboard()
        surfaceHost.setOnClickListener { clearSelection() }
        surfaceHost.removeAllViews()
        homeScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        treeHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(24), dp(8), dp(8))
            setOnClickListener { clearSelection() }
        }
        homeScroll.addView(treeHost, FrameLayout.LayoutParams(-1, -2))
        surfaceHost.addView(homeScroll, FrameLayout.LayoutParams(-1, -1))
        renderTree()
        renderToolbar()
    }

    private fun renderTree() {
        treeHost.removeAllViews()
        treeHost.addView(treeHeader())
        library.folders.sortedBy { it.name.lowercase() }.forEach { folder ->
            val notes = sortedNotes(folder.id)
            treeHost.addView(folderRow(folder, folder.name, true, notes.maxOfOrNull(Note::updatedAt)) {
                if (!expandedFolders.add(folder.id)) expandedFolders.remove(folder.id)
                renderTree()
            })
            if (folder.id in expandedFolders) {
                notes.forEachIndexed { index, note -> treeHost.addView(noteRow(note, index == notes.lastIndex)) }
            }
        }
        val unfiled = sortedNotes(null)
        if (unfiled.isNotEmpty()) {
            treeHost.addView(folderRow(null, this@RememberActivity.getString(R.string.notes_rememberactivity_unfiled_c1aca), true, unfiled.maxOfOrNull(Note::updatedAt)) {
                if (!expandedFolders.add("unfiled")) expandedFolders.remove("unfiled")
                renderTree()
            })
            if ("unfiled" in expandedFolders) unfiled.forEachIndexed { index, note ->
                treeHost.addView(noteRow(note, index == unfiled.lastIndex))
            }
        }
        applyRetuiStyle(treeHost, theme)
    }

    private fun sortedNotes(folderId: String?) = library.notes.filter { it.folderId == folderId }
        .sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.title.lowercase() })

    private fun treeHeader() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(60), 0, dp(36), 0)
        addView(TextView(this@RememberActivity).apply {
            text = this@RememberActivity.getString(R.string.notes_rememberactivity_name_a9d37)
            tag = "retui_h3"
        }, LinearLayout.LayoutParams(0, dp(28), 1f))
        addView(TextView(this@RememberActivity).apply {
            text = this@RememberActivity.getString(R.string.notes_rememberactivity_last_modified_622cd)
            tag = "retui_h3"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(104), dp(28)))
    }

    private fun folderRow(folder: NoteFolder?, label: String, collapsible: Boolean, modifiedAt: Long?, action: () -> Unit): View {
        val item: TreeItem = folder?.let(TreeItem::FolderItem) ?: TreeItem.Unfiled
        val expanded = (folder?.id ?: "unfiled") in expandedFolders
        return treeRow(
            icon = R.drawable.remember_ic_tree_folder,
            label = "$label/",
            bold = true,
            connector = null,
            selected = selected == item,
            modifiedAt = modifiedAt,
            labelIcon = null,
            trailing = if (collapsible) if (expanded) R.drawable.remember_ic_tree_chevron_down else R.drawable.remember_ic_tree_chevron_right else null,
            click = action,
            longClick = if (collapsible) ({ select(item) }) else null
        )
    }

    private fun noteRow(note: Note, last: Boolean): View = treeRow(
        icon = R.drawable.remember_ic_tree_note,
        label = (if (note.pinned) "★ " else "") + note.title.ifBlank { "untitled" },
        bold = false,
        connector = if (last) R.drawable.remember_ic_tree_branch_last else R.drawable.remember_ic_tree_branch,
        selected = selected == TreeItem.NoteItem(note),
        modifiedAt = note.updatedAt,
        labelIcon = if (RedactionFormat.contains(note.markdown)) R.drawable.remember_ic_redact else null,
        trailing = null,
        click = { openNote(note.id) },
        longClick = { select(TreeItem.NoteItem(note)) }
    )

    private fun treeRow(
        icon: Int,
        label: String,
        bold: Boolean,
        connector: Int?,
        selected: Boolean,
        modifiedAt: Long?,
        labelIcon: Int?,
        trailing: Int?,
        click: () -> Unit,
        longClick: (() -> Unit)?
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)
        tag = if (selected) "retui_selected" else "retui_directory"
        val rowColor = if (selected) theme.selectionText else theme.directoryText
        connector?.let { drawable ->
            addView(ImageView(this@RememberActivity).apply {
                setImageResource(drawable)
                imageTintList = ColorStateList.valueOf(rowColor)
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(24), dp(44)))
        }
        addView(ImageView(this@RememberActivity).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(rowColor)
            scaleType = ImageView.ScaleType.CENTER
        }, LinearLayout.LayoutParams(dp(32), dp(44)))
        addView(LinearLayout(this@RememberActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@RememberActivity).apply {
                text = label
                tag = if (bold) "retui_bold" else "retui_body"
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(-2, dp(44)))
            labelIcon?.let { drawable ->
                addView(ImageView(this@RememberActivity).apply {
                    setImageResource(drawable)
                    imageTintList = ColorStateList.valueOf(rowColor)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    contentDescription = this@RememberActivity.getString(R.string.notes_rememberactivity_redacted_87807)
                }, LinearLayout.LayoutParams(dp(22), dp(44)).apply { marginStart = dp(4) })
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        addView(TextView(this@RememberActivity).apply {
            text = modifiedAt?.let(::modifiedLabel).orEmpty()
            tag = "retui_h3"
            includeFontPadding = false
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(dp(104), dp(44)))
        trailing?.let { drawable ->
            addView(ImageView(this@RememberActivity).apply {
                setImageResource(drawable)
                imageTintList = ColorStateList.valueOf(rowColor)
                scaleType = ImageView.ScaleType.CENTER
            }, LinearLayout.LayoutParams(dp(32), dp(44)))
        }
        contentDescription = if (labelIcon == null) label else this@RememberActivity.getString(R.string.notes_rememberactivity_redacted_af2c6, label)
        setOnClickListener { click() }
        longClick?.let { callback -> setOnLongClickListener { callback(); true } }
    }

    private fun modifiedLabel(time: Long) = DateUtils.getRelativeTimeSpanString(
        time,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    private fun renderToolbar() {
        toolbar.removeAllViews()
        input = null
        when (mode) {
            BarMode.DEFAULT -> {
                toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
                fixedTool(R.drawable.remember_ic_tree_plus, this@RememberActivity.getString(R.string.notes_rememberactivity_add_61cc5)) { mode = BarMode.ADD; renderToolbar() }
                fixedTool(R.drawable.remember_ic_settings, this@RememberActivity.getString(R.string.notes_rememberactivity_settings_c7f73)) {
                    startActivityForResult(Intent(this@RememberActivity, SettingsActivity::class.java), REQ_SETTINGS)
                }
                toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            BarMode.ADD -> {
                weightedTool(R.drawable.remember_ic_add_notes, this@RememberActivity.getString(R.string.notes_rememberactivity_new_note_2b7b0)) {
                    showNewNotePrompt(
                        theme,
                        library.folders,
                        library.notes,
                        null,
                        openExisting = ::openNote
                    ) { name, folderId -> createNote(name, folderId) }
                }
                weightedTool(R.drawable.remember_ic_tree_folder_plus, this@RememberActivity.getString(R.string.notes_rememberactivity_new_folder_a7119)) { mode = BarMode.NEW_FOLDER; renderToolbar() }
                weightedTool(R.drawable.remember_ic_tree_close, this@RememberActivity.getString(R.string.notes_rememberactivity_close_bbfa7)) { mode = BarMode.DEFAULT; renderToolbar() }
            }
            BarMode.SELECT -> renderSelectionTools()
            BarMode.RENAME -> renderInput(selectedName(), this@RememberActivity.getString(R.string.notes_rememberactivity_rename_d3f4c), ::confirmRename)
            BarMode.NEW_FOLDER -> renderInput("", this@RememberActivity.getString(R.string.notes_rememberactivity_folder_name_b2ce0), ::confirmNewFolder)
            BarMode.DELETE -> renderDeleteConfirmation()
        }
        applyRetuiStyle(toolbar, theme)
    }

    private fun renderSelectionTools() {
        if (selected !is TreeItem.Unfiled) {
            weightedTool(R.drawable.remember_ic_tree_rename, this@RememberActivity.getString(R.string.notes_rememberactivity_rename_d3f4c)) { mode = BarMode.RENAME; renderToolbar() }
        }
        weightedTool(R.drawable.remember_ic_tree_share, this@RememberActivity.getString(R.string.notes_rememberactivity_share_09ca5)) { shareSelected() }
        if (selected !is TreeItem.Unfiled) {
            weightedTool(R.drawable.remember_ic_tree_delete, this@RememberActivity.getString(R.string.notes_rememberactivity_delete_f6fdb)) { mode = BarMode.DELETE; renderToolbar() }
        }
    }

    private fun renderInput(value: String, hint: String, confirm: (String) -> Unit) {
        val field = EditText(this).apply {
            setText(value)
            this.hint = hint
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(10), 0, dp(10), 0)
            tag = "retui_input"
            setOnEditorActionListener { _, _, _ -> confirm(text.toString()); true }
        }
        input = field
        toolbar.addView(field, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        fixedTool(R.drawable.remember_ic_tree_close, this@RememberActivity.getString(R.string.notes_rememberactivity_cancel_77dfd)) { cancelInput() }
        fixedTool(R.drawable.remember_ic_tree_confirm, this@RememberActivity.getString(R.string.notes_rememberactivity_confirm_04a21)) { confirm(field.text.toString()) }
        toolbar.post {
            field.requestFocus()
            field.setSelection(field.text.length)
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun renderDeleteConfirmation() {
        toolbar.addView(status(this@RememberActivity.getString(R.string.notes_rememberactivity_delete_16fad, selectedName())), LinearLayout.LayoutParams(0, -1, 1f))
        fixedTool(R.drawable.remember_ic_tree_close, this@RememberActivity.getString(R.string.notes_rememberactivity_cancel_77dfd)) { mode = BarMode.SELECT; renderToolbar() }
        fixedTool(R.drawable.remember_ic_tree_confirm, this@RememberActivity.getString(R.string.notes_rememberactivity_confirm_delete_c9f28)) { confirmDelete() }
    }

    private fun centeredTool(icon: Int, description: String, action: () -> Unit) {
        toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        fixedTool(icon, description, action)
        toolbar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
    }

    private fun weightedTool(icon: Int, description: String, action: () -> Unit) {
        toolbar.addView(tool(icon, description, action), LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(1), 0, dp(1), 0) })
    }

    private fun fixedTool(icon: Int, description: String, action: () -> Unit) {
        toolbar.addView(tool(icon, description, action), LinearLayout.LayoutParams(dp(44), dp(36)).apply { setMargins(dp(1), 0, dp(1), 0) })
    }

    private fun tool(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(theme.buttonText)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        tag = "retui_accent"
        setOnClickListener { action() }
    }

    private fun status(value: String) = TextView(this).apply {
        text = value
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(6), 0)
        includeFontPadding = false
    }

    private fun select(item: TreeItem) {
        selected = item
        mode = BarMode.SELECT
        renderTree()
        renderToolbar()
    }

    private fun clearSelection() {
        if (selected == null && mode == BarMode.DEFAULT) return
        hideKeyboard()
        selected = null
        mode = BarMode.DEFAULT
        renderTree()
        renderToolbar()
    }

    private fun cancelInput() {
        hideKeyboard()
        mode = if (selected == null) BarMode.ADD else BarMode.SELECT
        renderToolbar()
    }

    private fun confirmRename(raw: String) {
        val clean = raw.trim()
        when (val item = selected) {
            is TreeItem.FolderItem -> FolderRules.error(clean, library.folders, item.folder.id)?.let { input?.error = getString(it); return }
            is TreeItem.NoteItem -> if (clean.isEmpty()) { input?.error = this@RememberActivity.getString(R.string.notes_rememberactivity_note_title_is_required_3e39e); return }
            else -> return
        }
        when (val item = selected) {
            is TreeItem.FolderItem -> item.folder.name = clean
            is TreeItem.NoteItem -> { item.note.title = clean; item.note.updatedAt = System.currentTimeMillis() }
            else -> Unit
        }
        store.save(library)
        clearSelection()
    }

    private fun confirmNewFolder(raw: String) {
        val clean = raw.trim()
        FolderRules.error(clean, library.folders)?.let { input?.error = getString(it); return }
        library.folders.add(NoteFolder(name = clean))
        store.save(library)
        hideKeyboard()
        mode = BarMode.DEFAULT
        renderTree()
        renderToolbar()
    }

    private fun confirmDelete() {
        when (val item = selected) {
            is TreeItem.FolderItem -> { library.deleteFolder(item.folder.id); store.save(library) }
            is TreeItem.NoteItem -> {
                if (item.note.locked) {
                    Toast.makeText(this, getString(R.string.editor_noteseditoractivity_unlock_note_before_deleting_43ce2), Toast.LENGTH_SHORT).show()
                    return
                }
                store.delete(item.note, library)
            }
            else -> return
        }
        selected = null
        mode = BarMode.DEFAULT
        renderTree()
        renderToolbar()
    }

    private fun selectedName(): String = when (val item = selected) {
        is TreeItem.FolderItem -> item.folder.name
        is TreeItem.NoteItem -> item.note.title.ifBlank { "untitled" }
        TreeItem.Unfiled -> this@RememberActivity.getString(R.string.notes_rememberactivity_unfiled_c1aca)
        null -> ""
    }

    private fun createNote(name: String, folderId: String?) {
        mode = BarMode.DEFAULT
        note = Note(title = name, markdown = "# $name\n\n", folderId = folderId)
        isNew = true
        deleted = false
        showEditor()
    }

    private fun openNote(id: String) {
        library.notes.firstOrNull { it.id == id }?.let {
            if (surface == Surface.EDITOR && !persistEditor()) return@let
            note = it
            isNew = false
            deleted = false
            showEditor()
        }
    }

    private fun showEditor() {
        dismissSelectionPopup()
        revealed = false
        surface = Surface.EDITOR
        surfaceHost.setOnClickListener(null)
        surfaceHost.removeAllViews()
        editorScroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        editor = MarkdownEditText(this).apply {
            tag = "retui_document"
            minHeight = resources.displayMetrics.heightPixels
            setPadding(dp(14), dp(28), dp(14), dp(64))
            background = null
            contentDescription = this@RememberActivity.getString(R.string.notes_rememberactivity_markdown_note_editor_0bd6e)
            imageFile = ::ownedImage
            onMarkdownChanged = ::editorChanged
            onSelection = { start, end ->
                popupHandler.removeCallbacks(popupTask)
                if (start != end && surface == Surface.EDITOR) popupHandler.postDelayed(popupTask, 80)
                else dismissSelectionPopup()
            }
            setMarkdown(MarkdownDocument.ensure(note), isNew)
        }
        editorScroll.addView(editor, FrameLayout.LayoutParams(-1, -2))
        surfaceHost.addView(editorScroll, FrameLayout.LayoutParams(-1, -1))
        markdownToggle = CheckBox(this).apply {
            text = this@RememberActivity.getString(R.string.notes_rememberactivity_markdown_30d86)
            setPaddingRelative(dp(8), 0, dp(8), 0)
            gravity = Gravity.CENTER
            buttonTintList = ColorStateList.valueOf(theme.border)
            contentDescription = this@RememberActivity.getString(R.string.notes_rememberactivity_show_markdown_syntax_70e59)
            setOnCheckedChangeListener { _, checked -> editor.rawMode = checked }
            tag = "retui_card"
        }
        surfaceHost.addView(markdownToggle, FrameLayout.LayoutParams(-2, dp(40), Gravity.END or Gravity.BOTTOM).apply {
            marginEnd = dp(8)
            bottomMargin = dp(8)
        })
        applyRetuiStyle(surfaceHost, theme)
        if (isNew) editor.post {
            editor.requestFocus()
            editor.setSelection(editor.length())
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        renderEditorToolbar()
    }

    private fun renderEditorToolbar() {
        toolbar.removeAllViews()
        if (revealed) {
            toolbar.addView(editorIcon(R.drawable.remember_ic_visibility_off, this@RememberActivity.getString(R.string.notes_rememberactivity_re_redact_57217), ::hideRedactions))
            applyRetuiStyle(toolbar, theme)
            return
        }
        toolbar.addView(editorIcon(R.drawable.remember_ic_add_notes, this@RememberActivity.getString(R.string.notes_rememberactivity_new_note_2b7b0)) { newNotePrompt() })
        toolbar.addView(editorIcon(R.drawable.remember_ic_list, this@RememberActivity.getString(R.string.notes_rememberactivity_insert_checklist_f1d30)) { editor.insertChecklist() })
        toolbar.addView(editorIcon(R.drawable.remember_ic_tag, this@RememberActivity.getString(R.string.notes_rememberactivity_edit_tags_d8a5f)) { tagPrompt() })
        toolbar.addView(editorIcon(R.drawable.remember_ic_photo_library, this@RememberActivity.getString(R.string.notes_rememberactivity_insert_images_f4a26)) { launchGallery() })
        toolbar.addView(editorIcon(R.drawable.remember_ic_link, this@RememberActivity.getString(R.string.notes_rememberactivity_insert_link_4d4ac)) { linkPrompt(false) })
        toolbar.addView(editorIcon(R.drawable.remember_ic_redact, this@RememberActivity.getString(R.string.notes_rememberactivity_smart_redact_72687)) { confirmSmartRedact() })
        if (RedactionFormat.contains(note.markdown)) {
            toolbar.addView(editorIcon(R.drawable.remember_ic_visibility, this@RememberActivity.getString(R.string.notes_rememberactivity_unredact_af440), ::revealRedactions))
        }
        toolbar.addView(editorIcon(R.drawable.remember_ic_pin, if (note.pinned) this@RememberActivity.getString(R.string.notes_rememberactivity_unpin_note_4857f) else this@RememberActivity.getString(R.string.notes_rememberactivity_pin_note_db7ee)) {
            note.pinned = !note.pinned
            persistEditor()
            renderEditorToolbar()
        })
        if (!isNew) toolbar.addView(editorIcon(R.drawable.remember_ic_tree_delete, this@RememberActivity.getString(R.string.notes_rememberactivity_delete_note_a8ed4)) { confirmEditorDelete() })
        applyRetuiStyle(toolbar, theme)
    }

    private fun showSelectionPopup() {
        if (!::editor.isInitialized || surface != Surface.EDITOR || editor.selectionStart == editor.selectionEnd) return
        dismissSelectionPopup()
        val actions = listOf(
            Triple(R.drawable.remember_ic_format_bold, this@RememberActivity.getString(R.string.notes_rememberactivity_bold_19e07), MarkdownEditText.Format.BOLD),
            Triple(R.drawable.remember_ic_format_italic, this@RememberActivity.getString(R.string.notes_rememberactivity_italic_1616e), MarkdownEditText.Format.ITALIC),
            Triple(R.drawable.remember_ic_format_underlined, this@RememberActivity.getString(R.string.notes_rememberactivity_underline_39773), MarkdownEditText.Format.UNDERLINE),
            Triple(R.drawable.remember_ic_format_strikethrough, this@RememberActivity.getString(R.string.notes_rememberactivity_strikethrough_a93b9), MarkdownEditText.Format.STRIKE),
            Triple(R.drawable.remember_ic_format_code, this@RememberActivity.getString(R.string.notes_rememberactivity_code_adac6), MarkdownEditText.Format.CODE),
            Triple(R.drawable.remember_ic_format_checklist, this@RememberActivity.getString(R.string.notes_rememberactivity_checklist_61b29), MarkdownEditText.Format.CHECKLIST),
            Triple(R.drawable.remember_ic_format_bulleted, this@RememberActivity.getString(R.string.notes_rememberactivity_bulleted_list_dd870), MarkdownEditText.Format.BULLETS),
            Triple(R.drawable.remember_ic_format_numbered, this@RememberActivity.getString(R.string.notes_rememberactivity_numbered_list_8294e), MarkdownEditText.Format.NUMBERED),
            Triple(R.drawable.remember_ic_format_quote, this@RememberActivity.getString(R.string.notes_rememberactivity_quote_30902), MarkdownEditText.Format.QUOTE)
        )
        val width = minOf(resources.displayMetrics.widthPixels - dp(24), dp(340))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            tag = "retui_toolbar"
        }
        val buttons = listOf(
            headingButton(),
            formatButton(R.drawable.remember_ic_link, this@RememberActivity.getString(R.string.notes_rememberactivity_link_selected_text_2971b)) { linkPrompt(true) },
            formatButton(R.drawable.remember_ic_redact, this@RememberActivity.getString(R.string.notes_rememberactivity_redact_selected_text_bb190), ::redactSelection)
        ) + actions.map { (drawable, description, format) ->
            formatButton(drawable, description) {
                editor.applyFormat(format)
                dismissSelectionPopup()
            }
        }
        buttons.chunked(5).forEach { rowButtons ->
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                rowButtons.forEach { button ->
                    addView(button, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
                }
            }, LinearLayout.LayoutParams(-1, dp(38)))
        }
        applyRetuiStyle(content, theme)
        val layout = editor.layout ?: return
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtMost(editor.length())
        val endOffset = (end - 1).coerceAtLeast(start)
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(endOffset)
        val editorLocation = IntArray(2).also(editor::getLocationOnScreen)
        val selectionX = if (startLine == endLine) {
            (layout.getPrimaryHorizontal(start) + layout.getPrimaryHorizontal(endOffset)) / 2f
        } else layout.getPrimaryHorizontal(endOffset)
        val x = (editorLocation[0] + editor.totalPaddingLeft + selectionX - width / 2f)
            .roundToInt()
            .coerceIn(dp(4), resources.displayMetrics.widthPixels - width - dp(4))
        val y = editorLocation[1] + editor.totalPaddingTop + layout.getLineBottom(endLine) + editor.lineHeight * 2
        selectionPopup = PopupWindow(content, width, -2, false).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            showAtLocation(editor, Gravity.TOP or Gravity.START, x, y)
        }
    }

    private fun headingButton() = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        tag = "retui_accent"
        contentDescription = this@RememberActivity.getString(R.string.notes_rememberactivity_heading_style_a29f1)
        addView(ImageView(this@RememberActivity).apply {
            setImageResource(R.drawable.remember_ic_format_title)
            imageTintList = ColorStateList.valueOf(theme.buttonText)
        }, LinearLayout.LayoutParams(dp(18), dp(18)))
        addView(ImageView(this@RememberActivity).apply {
            setImageResource(R.drawable.remember_ic_tree_chevron_down)
            imageTintList = ColorStateList.valueOf(theme.buttonText)
        }, LinearLayout.LayoutParams(dp(9), dp(9)))
        setOnClickListener { showHeadingPopup(this) }
    }

    private fun showHeadingPopup(anchor: View) {
        headingPopup?.dismiss()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(3), dp(3), dp(3), dp(3))
            tag = "retui_toolbar"
        }
        listOf(
            Triple(R.drawable.remember_ic_format_h1, this@RememberActivity.getString(R.string.notes_rememberactivity_heading_1_fdc7d), MarkdownEditText.Format.H1),
            Triple(R.drawable.remember_ic_format_h2, this@RememberActivity.getString(R.string.notes_rememberactivity_heading_2_6542d), MarkdownEditText.Format.H2),
            Triple(R.drawable.remember_ic_format_h3, this@RememberActivity.getString(R.string.notes_rememberactivity_heading_3_9694d), MarkdownEditText.Format.H3)
        ).forEach { (drawable, description, format) ->
            content.addView(formatButton(drawable, description) {
                editor.applyFormat(format)
                dismissSelectionPopup()
            }, LinearLayout.LayoutParams(dp(42), dp(36)).apply { setMargins(dp(1), dp(1), dp(1), dp(1)) })
        }
        applyRetuiStyle(content, theme)
        headingPopup = PopupWindow(content, dp(138), dp(44), false).apply {
            isOutsideTouchable = true
            isTouchable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 0f
            showAsDropDown(anchor, 0, dp(2))
        }
    }

    private fun formatButton(drawable: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(theme.buttonText)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        tag = "retui_accent"
        setPadding(dp(9), dp(9), dp(9), dp(9))
        setOnClickListener { action() }
    }

    private fun dismissSelectionPopup() {
        popupHandler.removeCallbacks(popupTask)
        headingPopup?.dismiss()
        headingPopup = null
        selectionPopup?.dismiss()
        selectionPopup = null
    }

    private fun editorChanged(markdown: String) {
        if (revealed) return
        note.markdown = markdown
        MarkdownDocument.title(markdown)?.let { note.title = it }
        queueSave()
    }

    private fun redactSelection() {
        dismissSelectionPopup()
        val markdown = editor.markdown()
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtMost(markdown.length)
        if (start == end) return
        val titleEnd = markdown.indexOf('\n').let { if (it < 0) markdown.length else it }
        if (start < titleEnd || RedactionFormat.contains(markdown.substring(start, end))) {
            Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_select_unredacted_body_text_d4bf2), Toast.LENGTH_SHORT).show()
            return
        }
        val selectedText = markdown.substring(start, end)
        authenticate(this@RememberActivity.getString(R.string.notes_rememberactivity_redact_selected_text_bb190)) {
            runCatching {
                if (editor.markdown().substring(start, end) != selectedText) error(this@RememberActivity.getString(R.string.notes_rememberactivity_selection_changed_77e2c))
                editor.replaceRange(start, end, RedactionFormat.manual(selectedText, redactions::encrypt))
                persistEditor()
                renderEditorToolbar()
            }.onFailure { redactionFailure() }
        }
    }

    private fun confirmSmartRedact() = framedConfirmation(
        this@RememberActivity.getString(R.string.notes_rememberactivity_smart_redact_4a9cf),
        this@RememberActivity.getString(R.string.notes_rememberactivity_all_non_mundane_body_text_will_be_encrypte_8fd63),
        this@RememberActivity.getString(R.string.notes_rememberactivity_redact_91089)
    ) {
            authenticate(this@RememberActivity.getString(R.string.notes_rememberactivity_smart_redact_note_9bdb5)) {
                runCatching {
                    val transformed = RedactionFormat.automatic(note.markdown, redactions::encrypt)
                    note.markdown = transformed
                    editor.setMarkdown(transformed)
                    persistEditor()
                    renderEditorToolbar()
                }.onFailure { redactionFailure() }
            }
        }

    private fun revealRedactions() = authenticate(this@RememberActivity.getString(R.string.notes_rememberactivity_reveal_redacted_text_9dce3)) {
        runCatching {
            val clear = redactions.decryptMarkdown(note.markdown)
            revealed = true
            markdownToggle.isChecked = false
            markdownToggle.isEnabled = false
            editor.onMarkdownChanged = null
            editor.setMarkdown(clear)
            editor.isFocusable = false
            editor.isFocusableInTouchMode = false
            editor.isCursorVisible = false
            editor.isLongClickable = false
            dismissSelectionPopup()
            renderEditorToolbar()
            Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_redactions_revealed_read_only_898bf), Toast.LENGTH_SHORT).show()
        }.onFailure { redactionFailure() }
    }

    private fun hideRedactions() {
        if (!revealed) return
        revealed = false
        editor.setMarkdown(note.markdown)
        editor.onMarkdownChanged = ::editorChanged
        editor.isFocusable = true
        editor.isFocusableInTouchMode = true
        editor.isCursorVisible = true
        editor.isLongClickable = true
        markdownToggle.isEnabled = true
        renderEditorToolbar()
    }

    private fun redactionFailure() {
        Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_redaction_key_unavailable_note_was_not_cha_e1d2e), Toast.LENGTH_LONG).show()
    }

    private fun authenticate(title: String, success: () -> Unit) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_set_a_device_pin_or_biometric_first_929bc), Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val builder = BiometricPrompt.Builder(this).setTitle(title).setSubtitle(this@RememberActivity.getString(R.string.notes_rememberactivity_use_biometrics_or_screen_lock_5c36d))
            if (Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            }
            builder.build().authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    success()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED) {
                        Toast.makeText(this@RememberActivity, errString ?: this@RememberActivity.getString(R.string.notes_rememberactivity_authentication_failed_2db8d), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } else {
            pendingAuthentication = success
            @Suppress("DEPRECATION")
            startActivityForResult(keyguard.createConfirmDeviceCredentialIntent(title, this@RememberActivity.getString(R.string.notes_rememberactivity_unlock_redacted_text_1039f)), REQ_AUTH)
        }
    }

    private fun editorIcon(drawable: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(theme.buttonText)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = description
        tag = "retui_accent"
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(1), 0, dp(1), 0) }
    }

    private fun newNotePrompt() = showNewNotePrompt(
        theme,
        library.folders,
        library.notes,
        note.folderId,
        openExisting = ::openNote
    ) { name, folderId ->
        persistEditor()
        createNote(name, folderId)
    }

    private fun tagPrompt() {
        val field = EditText(this).apply {
            hint = this@RememberActivity.getString(R.string.notes_rememberactivity_tags_comma_separated_9508e)
            setText(note.tags.joinToString(", "))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            tag = "retui_input"
        }
        framedPrompt(this@RememberActivity.getString(R.string.notes_rememberactivity_tags_a119e), LinearLayout(this).apply { addView(field, LinearLayout.LayoutParams(-1, dp(52))) }, this@RememberActivity.getString(R.string.notes_rememberactivity_save_50815), field) {
            note.tags.clear()
            note.tags.addAll(field.text.toString().split(',', ';').map(String::trim).filter(String::isNotEmpty))
            note.normalizeTags()
            persistEditor()
            true
        }
    }

    private fun linkPrompt(useSelection: Boolean) {
        dismissSelectionPopup()
        val start = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = maxOf(editor.selectionStart, editor.selectionEnd).coerceAtMost(editor.length())
        val selectedLabel = editor.markdown().substring(start, end).takeIf { useSelection && start < end }
        val label = EditText(this).apply {
            hint = this@RememberActivity.getString(R.string.notes_rememberactivity_link_label_ee38f)
            setText(selectedLabel.orEmpty())
            isEnabled = selectedLabel == null
            tag = "retui_input"
        }
        val url = EditText(this).apply {
            hint = "https://example.com"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            tag = "retui_input"
        }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label, LinearLayout.LayoutParams(-1, dp(52)))
            addView(url, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        }
        framedPrompt(this@RememberActivity.getString(R.string.notes_rememberactivity_insert_link_4b9f9), fields, this@RememberActivity.getString(R.string.notes_rememberactivity_insert_c20ea), url) {
            val cleanLabel = label.text.toString().trim()
            val cleanUrl = MarkdownDocument.normalizeWebUrl(url.text.toString())
            when {
                cleanLabel.isEmpty() || '\n' in cleanLabel || ']' in cleanLabel -> {
                    label.error = this@RememberActivity.getString(R.string.notes_rememberactivity_use_a_single_line_label_without_2884e)
                    false
                }
                cleanUrl == null -> {
                    url.error = this@RememberActivity.getString(R.string.notes_rememberactivity_enter_a_valid_http_or_https_link_15500)
                    false
                }
                else -> {
                    editor.replaceRange(start, end, MarkdownDocument.link(cleanLabel, cleanUrl))
                    true
                }
            }
        }
    }

    private fun framedPrompt(
        title: String,
        fields: LinearLayout,
        confirmLabel: String,
        focus: View?,
        confirm: () -> Boolean
    ) {
        lateinit var dialog: AlertDialog
        val cancel = TextView(this).apply {
            text = this@RememberActivity.getString(R.string.notes_rememberactivity_cancel_1507c)
            gravity = Gravity.CENTER
            tag = "retui_accent"
            setOnClickListener { dialog.dismiss() }
        }
        val accept = TextView(this).apply {
            text = confirmLabel
            gravity = Gravity.CENTER
            tag = "retui_accent"
            setOnClickListener { if (confirm()) dialog.dismiss() }
        }
        fields.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(28), dp(12), dp(12))
            tag = "retui_panel"
            addView(LinearLayout(this@RememberActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(cancel, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
                addView(accept, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
            }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            addView(retuiHeaderTab(title).apply {
                (getChildAt(0) as? TextView)?.translationY = 0f
            }, LinearLayout.LayoutParams(-1, dp(34)))
            addView(fields, LinearLayout.LayoutParams(-1, -2).apply { topMargin = -dp(17) })
        }
        applyRetuiStyle(shell, theme)
        dialog = AlertDialog.Builder(this).setView(shell).create()
        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.9f).roundToInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            focus?.requestFocus()
            if (focus is EditText) {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(focus, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun framedConfirmation(title: String, message: String, confirmLabel: String, confirm: () -> Unit) {
        val fields = LinearLayout(this).apply {
            addView(TextView(this@RememberActivity).apply {
                text = message
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })
        }
        framedPrompt(title, fields, confirmLabel, null) {
            confirm()
            true
        }
    }

    private fun launchGallery() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, REQ_GALLERY)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SETTINGS) {
            refreshAppearance(null)
            return
        }
        if (requestCode == REQ_AUTH) {
            val action = pendingAuthentication
            pendingAuthentication = null
            if (resultCode == RESULT_OK) action?.invoke()
            return
        }
        if (requestCode != REQ_GALLERY || resultCode != RESULT_OK || data == null) return
        val uris = buildList {
            data.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
            data.data?.let(::add)
        }.distinct()
        if (uris.isNotEmpty()) insertImages(uris)
    }

    private fun insertImages(uris: List<Uri>) {
        uris.forEach { uri ->
            val destination = imageDestination()
            try {
                contentResolver.openInputStream(uri)?.use { source -> destination.outputStream().use(source::copyTo) }
                    ?: return@forEach
                editor.insertImage(destination.relativeTo(filesDir).path)
            } catch (_: Exception) {
                destination.delete()
                Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_could_not_read_an_image_c9e6a), Toast.LENGTH_SHORT).show()
            }
        }
        persistEditor()
    }

    private fun imageDestination(): File {
        val dir = File(filesDir, "notes-library/.assets/${note.id}").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }

    private fun ownedImage(path: String): File? = runCatching {
        val roots = listOf(File(filesDir, "images"), File(filesDir, "notes-library/.assets")).map(File::getCanonicalFile)
        File(filesDir, path).canonicalFile.takeIf { candidate ->
            candidate.isFile && roots.any { root -> candidate.path.startsWith(root.path + File.separator) }
        }
    }.getOrNull()

    private fun queueSave() {
        saveHandler.removeCallbacks(saveTask)
        saveHandler.postDelayed(saveTask, 500)
    }

    private fun persistEditor(): Boolean {
        if (!storageAvailable) return false
        if (surface != Surface.EDITOR || deleted) return true
        if (::editor.isInitialized && !revealed) note.markdown = editor.markdown()
        MarkdownDocument.title(note.markdown)?.let { note.title = it }
        if (isNew && !note.hasMeaningfulContent()) return true
        val adding = isNew
        if (adding) library.notes.add(note)
        note.updatedAt = System.currentTimeMillis()
        try {
            store.save(library)
            isNew = false
            loadedRevision = NoteStore.revision
            return true
        } catch (e: Exception) {
            if (adding) library.notes.remove(note)
            Toast.makeText(this, getString(R.string.editor_noteseditoractivity_save_failed_8f75d, e.displayMessage(this)), Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun confirmEditorDelete() = framedConfirmation(
        this@RememberActivity.getString(R.string.notes_rememberactivity_delete_note_38d68),
        this@RememberActivity.getString(R.string.notes_rememberactivity_this_permanently_deletes_the_note_and_its_e287a),
        this@RememberActivity.getString(R.string.notes_rememberactivity_delete_d6f56)
    ) {
            if (note.locked) {
                Toast.makeText(this, getString(R.string.editor_noteseditoractivity_unlock_note_before_deleting_43ce2), Toast.LENGTH_SHORT).show()
            } else {
            store.delete(note, library)
            deleted = true
            showHome()
            }
        }

    private fun shareSelected() {
        val item = selected ?: return
        val notes = notesFor(item)
        framedConfirmation(
            this@RememberActivity.getString(R.string.notes_rememberactivity_export_unredacted_notes_8c54e),
            this@RememberActivity.getString(R.string.notes_rememberactivity_the_exported_markdown_and_attached_images_541c6),
            this@RememberActivity.getString(R.string.notes_rememberactivity_export_c0b96)
        ) {
                val export: () -> Unit = {
                    runCatching {
                        val exporter = NoteExporter(this, redactions::decryptMarkdown)
                        val label = when (item) {
                            is TreeItem.NoteItem -> item.note.title.ifBlank { "untitled" }
                            is TreeItem.FolderItem -> item.folder.name
                            TreeItem.Unfiled -> this@RememberActivity.getString(R.string.notes_rememberactivity_unfiled_c1aca)
                        }
                        shareZip(exporter.folderZip(label, notes))
                        clearSelection()
                    }.onFailure { Toast.makeText(this, this@RememberActivity.getString(R.string.notes_rememberactivity_could_not_prepare_export_e858c), Toast.LENGTH_SHORT).show() }
                    Unit
                }
                if (notes.any { RedactionFormat.contains(it.markdown) }) authenticate(this@RememberActivity.getString(R.string.notes_rememberactivity_export_unredacted_notes_4269f), export) else export()
            }
    }

    private fun notesFor(item: TreeItem): List<Note> = when (item) {
        is TreeItem.NoteItem -> listOf(item.note)
        is TreeItem.FolderItem -> library.notes.filter { it.folderId == item.folder.id }
        TreeItem.Unfiled -> library.notes.filter { it.folderId == null }
    }

    private fun shareFiles(files: List<File>) {
        val uris = ArrayList(files.map { FileProvider.getUriForFile(this, "$packageName.files", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, this@RememberActivity.getString(R.string.notes_rememberactivity_re_member_note_25d92), uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
        }
        startActivity(Intent.createChooser(intent, this@RememberActivity.getString(R.string.notes_rememberactivity_share_note_81fd7)))
    }

    private fun shareZip(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(contentResolver, file.name, uri)
        }, this@RememberActivity.getString(R.string.notes_rememberactivity_share_directory_c7dac)))
    }

    private fun loadStarterLibrary(): NoteLibrary? {
        return try {
            store.load().also { loadedRevision = NoteStore.revision; storageAvailable = true }
        } catch (_: NoteStore.ReadException) {
            storageAvailable = false
            finishRetui(nestedMessage(theme, this@RememberActivity.getString(R.string.notes_rememberactivity_notes_unavailable_e0386), this@RememberActivity.getString(R.string.notes_rememberactivity_the_local_notes_file_was_preserved_and_no_de1b1)) { finish() }, theme)
            null
        }
    }

    private fun hideKeyboard() {
        input?.clearFocus()
        currentFocus?.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(toolbar.windowToken, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        persistEditor()
        if (surface == Surface.EDITOR && ::note.isInitialized) {
            outState.putString("open_note", note.id)
            outState.putBoolean("raw_mode", editor.rawMode)
            outState.putInt("cursor", editor.selectionStart)
            outState.putInt("editor_scroll", editorScroll.scrollY)
        }
        outState.putStringArrayList("expanded_folders", ArrayList(expandedFolders))
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        refreshFromDisk()
    }

    private fun refreshFromDisk() {
        if (!::library.isInitialized || loadedRevision == NoteStore.revision) return
        val openId = if (surface == Surface.EDITOR && ::note.isInitialized) note.id else null
        val freshLibrary = loadStarterLibrary() ?: return
        library = freshLibrary
        if (openId != null && !isNew) {
            val fresh = library.notes.firstOrNull { it.id == openId }
            if (fresh != null) {
                note = fresh
                showEditor()
            } else {
                surface = Surface.HOME
                showHome()
            }
        } else if (surface == Surface.HOME) renderTree()
    }

    override fun onPause() {
        super.onPause()
        dismissSelectionPopup()
        saveHandler.removeCallbacks(saveTask)
        if (revealed) hideRedactions()
        if (::note.isInitialized && surface == Surface.EDITOR && !deleted) persistEditor()
        loadedRevision = NoteStore.revision
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
