package ohi.andre.consolelauncher.notes

import ohi.andre.consolelauncher.R

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

fun Activity.showNewNotePrompt(
    theme: RetuiTheme,
    availableFolders: List<NoteFolder>,
    existingNotes: List<Note>,
    initialFolderId: String?,
    openExisting: (id: String) -> Unit,
    create: (name: String, folderId: String?) -> Unit
) {
    fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    val folders = availableFolders.sortedBy { it.name.lowercase() }
    val folderIds = listOf<String?>(null) + folders.map { it.id }
    val folderNames = listOf(this@showNewNotePrompt.getString(R.string.notes_newnotedialog_unfiled_c1aca)) + folders.map { it.name }
    val font = theme.fontPath?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
        ?: Typeface.create(theme.fontName, Typeface.NORMAL)
    val title = EditText(this).apply {
        hint = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_note_name_11bdf)
        isSingleLine = true
        setPadding(dp(12), dp(10), dp(12), dp(10))
        tag = "retui_input"
    }
    val directory = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@showNewNotePrompt, android.R.layout.simple_spinner_item, folderNames) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            private fun style(view: View, dropdown: Boolean): View = view.apply {
                (this as TextView).apply {
                    setTextColor(theme.inputText)
                    textSize = theme.inputSize.toFloat()
                    typeface = font
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    if (dropdown) setBackgroundColor(theme.inputBg)
                }
            }
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getView(position, convertView, parent), false)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                style(super.getDropDownView(position, convertView, parent), true)
        }
        setSelection(folderIds.indexOf(initialFolderId).coerceAtLeast(0))
        contentDescription = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_directory_4b892)
        tag = "retui_input"
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(28), dp(12), dp(12))
        tag = "retui_panel"
        addView(title, LinearLayout.LayoutParams(-1, dp(48)))
    }
    val warning = TextView(this).apply {
        visibility = View.GONE
        tag = "retui_card"
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }
    content.addView(warning, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    content.addView(TextView(this).apply {
        text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_directory_e084b)
        setPadding(dp(2), dp(12), dp(2), dp(4))
    })
    content.addView(directory, LinearLayout.LayoutParams(-1, dp(48)))
    lateinit var dialog: AlertDialog
    lateinit var showDuplicate: (Note) -> Unit
    val cancel = TextView(this).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        tag = "retui_accent"
    }
    val confirm = TextView(this).apply {
        gravity = Gravity.CENTER
        minHeight = dp(44)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        tag = "retui_accent"
    }
    fun editActions() {
        warning.visibility = View.GONE
        cancel.text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_cancel_1507c)
        cancel.setOnClickListener { dialog.dismiss() }
        confirm.text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_create_cabc2)
        confirm.setOnClickListener {
            val name = title.text.toString().trim()
            if (name.isEmpty()) {
                title.error = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_note_name_is_required_a8ad4)
                return@setOnClickListener
            }
            existingNotes.findTitleDuplicate(name)?.let { duplicate ->
                showDuplicate(duplicate)
                return@setOnClickListener
            }
            dialog.dismiss()
            create(name, folderIds[directory.selectedItemPosition])
        }
    }
    showDuplicate = { original ->
        warning.text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_a_note_named_already_exists_f9d40, original.title.trim())
        warning.visibility = View.VISIBLE
        cancel.text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_open_original_e3452)
        cancel.setOnClickListener {
            dialog.dismiss()
            openExisting(original.id)
        }
        confirm.text = this@showNewNotePrompt.getString(R.string.notes_newnotedialog_rename_47560)
        confirm.setOnClickListener {
            editActions()
            title.requestFocus()
            title.selectAll()
        }
    }
    editActions()
    content.addView(LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) })
        addView(confirm, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
    }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
        addView(retuiHeaderTab(this@showNewNotePrompt.getString(R.string.notes_newnotedialog_new_note_d5bab)).apply {
            (getChildAt(0) as? TextView)?.translationY = 0f
        }, LinearLayout.LayoutParams(-1, dp(34)))
        addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = -dp(17) })
    }
    applyRetuiStyle(shell, theme)
    dialog = AlertDialog.Builder(this).setView(shell).create()
    dialog.setOnShowListener {
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((resources.displayMetrics.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        title.requestFocus()
    }
    dialog.show()
}
