package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleIconButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.NotesManager
import ohi.andre.consolelauncher.managers.NotesManager.NoteRecord
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.max
import kotlin.math.min
import java.util.ArrayList
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

class NotesEditorActivity : Activity() {
    private var notesContainer: LinearLayout? = null
    private var originalSnapshot = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        val records = NotesManager.loadRecords(this)
        originalSnapshot = snapshot(records)

        val screen = FrameLayout(this)
        screen.setBackgroundColor(overlayColor())
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val panelShell = FrameLayout(this)
        panelShell.setClipChildren(false)
        panelShell.setClipToPadding(false)

        val root = LinearLayout(this)
        root.setOrientation(LinearLayout.VERTICAL)
        root.setPadding(dp(this, 14f), dp(this, 50f), dp(this, 14f), dp(this, 14f))
        stylePanel(this, root)

        val panelParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        panelParams.gravity = Gravity.CENTER
        panelParams.setMargins(dp(this, 28f), dp(this, 28f), dp(this, 28f), dp(this, 28f))
        contentHost.addView(panelShell, panelParams)

        val rootParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootParams.topMargin = dp(this, 11f)
        panelShell.addView(root, rootParams)

        val header = TextView(this)
        header.setText("Notes")
        styleHeader(this, header)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = dp(this, 38f)
        panelShell.addView(header, headerParams)

        val scrollView = ContentSizedScrollView(this)
        scrollView.setFillViewport(false)
        scrollView.setMaxHeight(maxNotesScrollHeight())
        notesContainer = LinearLayout(this)
        notesContainer!!.setOrientation(LinearLayout.VERTICAL)
        notesContainer!!.setPadding(0, 0, 0, dp(this, 8f))
        scrollView.addView(
            notesContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scrollView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        if (records.isEmpty()) {
            addNoteRow(null, true)
        } else {
            for (record in records) {
                addNoteRow(record, false)
            }
        }

        val add = button("ADD NOTE", false)
        add.setOnClickListener(View.OnClickListener { v: View? -> addNoteRow(null, true) })
        notesContainer!!.addView(add, addButtonParams())

        val bottomBar = LinearLayout(this)
        bottomBar.setOrientation(LinearLayout.HORIZONTAL)
        bottomBar.setGravity(Gravity.CENTER_VERTICAL)
        bottomBar.setPadding(0, dp(this, 10f), 0, 0)

        val cancel = button("CANCEL", false)
        cancel.setOnClickListener(View.OnClickListener { v: View? -> attemptClose() })
        bottomBar.addView(cancel)

        val spacer = View(this)
        bottomBar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val save = button("SAVE", true)
        save.setOnClickListener(View.OnClickListener { v: View? -> saveAndClose() })
        bottomBar.addView(save)
        root.addView(bottomBar)

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

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        attemptClose()
    }

    private fun addNoteRow(record: NoteRecord?, requestFocus: Boolean) {
        val holder = NoteRow()
        holder.creationTime = if (record == null) 0 else record.creationTime
        holder.locked = record != null && record.lock

        val row = LinearLayout(this)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setGravity(Gravity.TOP)
        row.setPadding(0, 0, 0, 0)

        val editor = EditText(this)
        editor.setGravity(Gravity.TOP or Gravity.START)
        editor.setMinLines(2)
        editor.setHint("Note")
        editor.setText(if (record == null) "" else record.text)
        styleInput(this, editor)
        val editorParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
        row.addView(editor, editorParams)
        holder.editor = editor

        val lock = iconButton(
            if (holder.locked) R.drawable.ic_tuixt_lock_24 else R.drawable.ic_tuixt_lock_open_24,
            "Toggle lock"
        )
        lock.setOnClickListener(View.OnClickListener { v: View? ->
            holder.locked = !holder.locked
            updateLockButton(holder)
        })
        row.addView(lock, iconParams())
        holder.lockButton = lock

        val delete = iconButton(R.drawable.ic_tuixt_delete_24, "Delete note")
        delete.setOnClickListener(View.OnClickListener { v: View? -> deleteRow(holder) })
        row.addView(delete, iconParams())
        holder.deleteButton = delete

        holder.row = row
        row.setTag(holder)
        updateLockButton(holder)

        val insertAt = max(0, notesContainer!!.getChildCount() - 1)
        notesContainer!!.addView(row, insertAt, rowParams())

        if (requestFocus) {
            editor.requestFocus()
        }
    }

    private fun deleteRow(holder: NoteRow) {
        if (holder.locked) {
            Toast.makeText(this, "Unlock note before deleting.", Toast.LENGTH_SHORT).show()
            return
        }

        notesContainer!!.removeView(holder.row)
        if (rows().isEmpty()) {
            addNoteRow(null, true)
        }
    }

    private fun updateLockButton(holder: NoteRow) {
        holder.lockButton!!.setImageResource(if (holder.locked) R.drawable.ic_tuixt_lock_24 else R.drawable.ic_tuixt_lock_open_24)
        holder.lockButton!!.setColorFilter(
            if (holder.locked) accentColor() else textColor(),
            PorterDuff.Mode.SRC_IN
        )
        holder.lockButton!!.setContentDescription(if (holder.locked) "Unlock note" else "Lock note")
    }

    private fun iconButton(imageRes: Int, description: String?): ImageButton {
        val button = ImageButton(this)
        button.setImageResource(imageRes)
        button.setContentDescription(description)
        styleIconButton(this, button)
        button.setPadding(dp(this, 8f), dp(this, 8f), dp(this, 8f), dp(this, 8f))
        button.setScaleType(android.widget.ImageView.ScaleType.CENTER)
        button.setColorFilter(textColor(), PorterDuff.Mode.SRC_IN)
        return button
    }

    private fun button(label: String?, primary: Boolean): TextView {
        val view = TextView(this)
        view.setText(label)
        styleButton(this, view, primary)
        return view
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 10f))
        return params
    }

    private fun iconParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            dp(this, 44f),
            dp(this, 44f)
        )
        params.setMargins(dp(this, 8f), 0, 0, 0)
        return params
    }

    private fun addButtonParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 10f))
        return params
    }

    private fun maxNotesScrollHeight(): Int {
        val screenHeight = getResources().getDisplayMetrics().heightPixels
        val reserved = dp(this, 220f)
        val fallback = dp(this, 280f)
        return max(fallback, screenHeight - reserved)
    }

    private fun saveAndClose() {
        try {
            NotesManager.saveRecords(this, currentRecords())
            setResult(TuixtActivity.SAVE_PRESSED, Intent())
            finish()
        } catch (e: Exception) {
            Tuils.log(e)
            Toast.makeText(this, "Save failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            finish()
            return
        }

        TuixtDialog.showConfirm(
            this,
            "Discard Changes?",
            "Unsaved notes will be lost.",
            "Discard",
            "Keep Editing",
            ConfirmAction { this.finish() })
    }

    private fun hasUnsavedChanges(): Boolean {
        return !TextUtils.equals(originalSnapshot, snapshot(currentRecords()))
    }

    private fun currentRecords(): MutableList<NoteRecord?> {
        val records = ArrayList<NoteRecord?>()
        for (row in rows()) {
            val text = row.editor!!.getText().toString()
            if (text.trim { it <= ' ' }.length == 0) {
                continue
            }
            records.add(NoteRecord(row.creationTime, text, row.locked))
        }
        return records
    }

    private fun rows(): MutableList<NoteRow> {
        val rows: ArrayList<NoteRow> = ArrayList<NoteRow>()
        if (notesContainer == null) {
            return rows
        }
        for (count in 0..<notesContainer!!.getChildCount()) {
            val tag = notesContainer!!.getChildAt(count).getTag()
            if (tag is NoteRow) {
                rows.add(tag)
            }
        }
        return rows
    }

    private fun snapshot(records: MutableList<NoteRecord?>): String {
        val builder = StringBuilder()
        for (record in records) {
            val text = record?.text
            if (text == null || text.trim { it <= ' ' }.length == 0) {
                continue
            }
            builder.append(record.creationTime)
                .append('|')
                .append(record.lock)
                .append('|')
                .append(text)
                .append('\n')
        }
        return builder.toString()
    }

    private class NoteRow {
        var creationTime: Long = 0
        var locked: Boolean = false
        var row: LinearLayout? = null
        var editor: EditText? = null
        var lockButton: ImageButton? = null
        var deleteButton: ImageButton? = null
    }

    private class ContentSizedScrollView : ScrollView {
        private var maxHeight = 0

        internal constructor(context: Context?) : super(context)

        constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

        fun setMaxHeight(maxHeight: Int) {
            this.maxHeight = maxHeight
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            var heightMeasureSpec = heightMeasureSpec
            if (maxHeight > 0) {
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val constrainedHeight = if (heightMode == MeasureSpec.UNSPECIFIED)
                    maxHeight
                else min(heightSize, maxHeight)
                heightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(constrainedHeight, MeasureSpec.AT_MOST)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
