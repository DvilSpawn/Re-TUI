package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.showConfirm
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.refreshFromLoadedPrefs
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.io.FileInputStream
import java.util.Arrays
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.tuils.LauncherSystemUi

class WidgetEditorActivity : Activity() {
    private var widgetMode = true
    private var widgetId: String? = null
    private var documentFile: File? = null
    private var header: TextView? = null
    private var documentNameEditor: EditText? = null
    private var codeEditor: EditText? = null
    private var capabilityView: TextView? = null
    private var originalDocumentName: String? = ""
    private var originalCode: String? = ""
    private var findHighlightBackground: BackgroundColorSpan? = null
    private var findHighlightForeground: ForegroundColorSpan? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        val intent = getIntent()
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        widgetMode = TextUtils.isEmpty(filePath)

        if (widgetMode) {
            widgetId = LuaWidgetManager.normalizeId(intent.getStringExtra(EXTRA_WIDGET_ID))
            if (TextUtils.isEmpty(widgetId)) {
                finish()
                return
            }

            originalDocumentName = LuaWidgetManager.getName(widgetId)
            originalCode = LuaWidgetManager.readScript(widgetId)
            if (TextUtils.isEmpty(originalCode)) {
                originalCode = LuaWidgetManager.newWidgetTemplate(widgetId)
            }
        } else {
            documentFile = File(filePath)
            if (documentFile!!.isDirectory()) {
                finish()
                return
            }
            originalDocumentName = documentFile!!.getName()
            try {
                FileInputStream(documentFile).use { `in` ->
                    originalCode = Tuils.convertStreamToString(`in`)
                }
            } catch (e: Exception) {
                originalCode = ""
            }
        }

        if (TextUtils.isEmpty(originalDocumentName)) {
            originalDocumentName = if (widgetMode) widgetId else "Document"
        }

        if (widgetMode && TextUtils.isEmpty(originalCode)) {
            originalCode = LuaWidgetManager.newWidgetTemplate(widgetId)
        }

        if (!widgetMode && documentFile == null) {
            finish()
            return
        }

        val screen = FrameLayout(this)
        screen.setBackgroundColor(overlayColor())
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

        header = TextView(this)
        updateHeader()
        TuixtTheme.styleHeader(this, header!!)
        val headerParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        headerParams.gravity = Gravity.TOP or Gravity.START
        headerParams.leftMargin = panelLeft + dp(this, 38f)
        headerParams.topMargin = panelTop - dp(this, 11f)
        contentHost.addView(header, headerParams)

        documentNameEditor = EditText(this)
        documentNameEditor!!.setSingleLine(true)
        documentNameEditor!!.setHint(if (widgetMode) "Document name" else "File name")
        documentNameEditor!!.setText(originalDocumentName)
        if (!widgetMode) {
            documentNameEditor!!.setFocusable(false)
            documentNameEditor!!.setFocusableInTouchMode(false)
            documentNameEditor!!.setCursorVisible(false)
        }
        TuixtTheme.styleInput(this, documentNameEditor!!)
        val nameParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        nameParams.setMargins(0, 0, 0, dp(this, 10f))
        root.addView(documentNameEditor, nameParams)

        capabilityView = TextView(this)
        capabilityView!!.setSingleLine(false)
        capabilityView!!.setTextSize(11f)
        capabilityView!!.setTypeface(Tuils.getTypeface(this))
        capabilityView!!.setTextColor(textColor())
        capabilityView!!.setAlpha(if (widgetMode) 0.82f else 0.55f)
        val capabilityParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        capabilityParams.setMargins(0, 0, 0, dp(this, 10f))
        root.addView(capabilityView, capabilityParams)

        codeEditor = EditText(this)
        codeEditor!!.setGravity(Gravity.TOP or Gravity.START)
        codeEditor!!.setSingleLine(false)
        codeEditor!!.setHorizontallyScrolling(true)
        codeEditor!!.setTypeface(Typeface.MONOSPACE)
        codeEditor!!.setTextSize(13f)
        codeEditor!!.setText(originalCode)
        TuixtTheme.styleInput(this, codeEditor!!)
        root.addView(
            codeEditor, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        codeEditor!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCapabilityPreview()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        documentNameEditor!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCapabilityPreview()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        updateCapabilityPreview()

        val bottomBar = LinearLayout(this)
        bottomBar.setOrientation(LinearLayout.HORIZONTAL)
        bottomBar.setGravity(Gravity.CENTER_VERTICAL)
        bottomBar.setPadding(0, dp(this, 10f), 0, 0)

        val cancel = button("CANCEL", false)
        cancel.setOnClickListener(View.OnClickListener { v: View? -> attemptClose() })
        bottomBar.addView(cancel)

        val findReplace = button("FIND/REPLACE", false)
        findReplace.setOnClickListener { showFindReplaceDialog() }
        bottomBar.addView(findReplace)

        val spacer = View(this)
        bottomBar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val save = button("SAVE", false)
        save.setOnClickListener(View.OnClickListener { v: View? -> save(false) })
        bottomBar.addView(save)

        if (widgetMode) {
            val run = button("SAVE/RUN", true)
            run.setOnClickListener(View.OnClickListener { v: View? -> save(true) })
            bottomBar.addView(run)
        }

        root.addView(bottomBar)
        setContentView(screen)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (targetKey(getIntent()) == targetKey(intent)) {
            return
        }
        if (hasUnsavedChanges()) {
            Toast.makeText(
                this,
                "Save or discard the current changes before opening another document.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        setIntent(intent)
        recreate()
    }

    private fun showFindReplaceDialog() {
        TuixtDialog.showCustomCompact(this, "Find/Replace", TuixtDialog.ContentFactory { dialog: Dialog? ->
            dialog?.setCanceledOnTouchOutside(false)
            dialog?.setOnDismissListener { clearFindHighlight() }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                minimumWidth = dp(this@WidgetEditorActivity, 320f)
            }
            val closeRow = LinearLayout(this).apply {
                gravity = Gravity.END
            }
            closeRow.addView(button("X", false).apply {
                contentDescription = "Close find and replace"
                setOnClickListener { dialog?.dismiss() }
            })
            content.addView(closeRow)
            val find = EditText(this).apply {
                hint = "Find"
                setSingleLine(true)
                TuixtTheme.styleInput(this@WidgetEditorActivity, this)
            }
            val replacement = EditText(this).apply {
                hint = "Replace with"
                setSingleLine(true)
                TuixtTheme.styleInput(this@WidgetEditorActivity, this)
            }
            content.addView(find, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            content.addView(replacement, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(this@WidgetEditorActivity, 8f) })

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(this@WidgetEditorActivity, 12f), 0, 0)
            }
            val findActions = LinearLayout(this).apply { gravity = Gravity.CENTER }
            findActions.addView(button("FIND PREVIOUS", false).apply {
                setOnClickListener { findPrevious(find.text.toString()) }
            })
            findActions.addView(button("FIND NEXT", false).apply {
                setOnClickListener { findNext(find.text.toString()) }
            })
            actions.addView(findActions)
            val replaceActions = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(this@WidgetEditorActivity, 8f), 0, 0)
            }
            replaceActions.addView(button("REPLACE", true).apply {
                setOnClickListener {
                    replaceSelection(find.text.toString(), replacement.text.toString())
                }
            })
            replaceActions.addView(button("REPLACE ALL", false).apply {
                setOnClickListener {
                    replaceAll(find.text.toString(), replacement.text.toString())
                }
            })
            actions.addView(replaceActions)
            content.addView(actions)
            find.post { find.requestFocus() }
            content
        })
    }

    private fun findNext(query: String): Boolean {
        if (query.isEmpty()) return false
        val editor = codeEditor ?: return false
        val text = editor.text.toString()
        var match = text.indexOf(query, editor.selectionEnd.coerceAtLeast(0))
        if (match < 0) match = text.indexOf(query)
        if (match < 0) {
            Toast.makeText(this, "Not found: $query", Toast.LENGTH_SHORT).show()
            return false
        }
        showMatch(editor, match, query.length)
        return true
    }

    private fun findPrevious(query: String): Boolean {
        if (query.isEmpty()) return false
        val editor = codeEditor ?: return false
        val text = editor.text.toString()
        var match = text.lastIndexOf(query, editor.selectionStart - 1)
        if (match < 0) match = text.lastIndexOf(query)
        if (match < 0) {
            Toast.makeText(this, "Not found: $query", Toast.LENGTH_SHORT).show()
            return false
        }
        showMatch(editor, match, query.length)
        return true
    }

    private fun showMatch(editor: EditText, match: Int, length: Int) {
        editor.setSelection(match, match + length)
        clearFindHighlight()
        findHighlightBackground = BackgroundColorSpan(ColorUtils.setAlphaComponent(accentColor(), 220))
        findHighlightForeground = ForegroundColorSpan(surfaceColor())
        editor.text.setSpan(findHighlightBackground, match, match + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editor.text.setSpan(findHighlightForeground, match, match + length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        editor.post {
            val layout = editor.layout ?: return@post
            editor.scrollTo(editor.scrollX, layout.getLineTop(layout.getLineForOffset(match)))
        }
    }

    private fun clearFindHighlight() {
        codeEditor?.text?.removeSpan(findHighlightBackground)
        codeEditor?.text?.removeSpan(findHighlightForeground)
        findHighlightBackground = null
        findHighlightForeground = null
    }

    private fun replaceSelection(query: String, replacement: String) {
        if (query.isEmpty()) return
        val editor = codeEditor ?: return
        val selected = editor.text.substring(editor.selectionStart, editor.selectionEnd)
        if (selected != query) {
            findNext(query)
            return
        }
        editor.text.replace(editor.selectionStart, editor.selectionEnd, replacement)
        findNext(query)
    }

    private fun replaceAll(query: String, replacement: String) {
        if (query.isEmpty()) return
        val editor = codeEditor ?: return
        val text = editor.text.toString()
        var count = 0
        var index = text.indexOf(query)
        while (index >= 0) {
            count++
            index = text.indexOf(query, index + query.length)
        }
        if (count == 0) {
            Toast.makeText(this, "Not found: $query", Toast.LENGTH_SHORT).show()
            return
        }
        clearFindHighlight()
        editor.setText(text.replace(query, replacement))
        Toast.makeText(this, "Replaced $count matches", Toast.LENGTH_SHORT).show()
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

    private fun button(label: String?, primary: Boolean): TextView {
        val view = TextView(this)
        view.setText(label)
        styleButton(this, view, primary)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(dp(this, 6f), 0, 0, 0)
        view.setLayoutParams(params)
        return view
    }

    private fun save(run: Boolean) {
        try {
            val code = codeEditor!!.getText().toString()

            if (!widgetMode) {
                Tuils.write(documentFile, "", code)
                XMLPrefsManager.dispose()
                XMLPrefsManager.loadCommons(this)
                refreshFromLoadedPrefs()
                originalCode = code
                originalDocumentName = documentFile!!.name
                Toast.makeText(this, "Document saved: ${documentFile!!.name}", Toast.LENGTH_SHORT)
                    .show()
                LauncherActivity.preview(this)
                return
            }

            val name = documentNameEditor!!.getText().toString().trim { it <= ' ' }
            require(!TextUtils.isEmpty(name)) { "Document name is required" }
            if (!TextUtils.equals(originalDocumentName, name)) {
                val newId = LuaWidgetManager.idFromName(name)
                require(!TextUtils.isEmpty(newId)) { "Document name needs letters or numbers" }
                if (!TextUtils.equals(widgetId, newId)) {
                    require(
                        !(ModuleManager.isKnown(
                            this,
                            newId
                        ) || LuaWidgetManager.exists(newId))
                    ) { "Lua module id already exists: " + newId }
                    LuaWidgetManager.rename(widgetId, newId)
                    ModuleManager.renameScriptModule(
                        this,
                        widgetId,
                        newId,
                        LuaWidgetManager.SOURCE_PREFIX + newId
                    )
                    widgetId = newId
                }
            }

            LuaWidgetManager.save(widgetId, name, code)
            val dockable = LuaWidgetManager.isDockableScript(code)
            if (dockable) {
                ModuleManager.setScriptModule(
                    this,
                    widgetId,
                    LuaWidgetManager.SOURCE_PREFIX + widgetId
                )
                ModuleManager.addToDock(this, Arrays.asList<String?>(widgetId))
            } else {
                ModuleManager.removeScriptModule(this, widgetId)
            }
            sendModule("rebuild")
            if (run && dockable) {
                sendModule("show")
                sendModule("refresh")
                originalDocumentName = LuaWidgetManager.getName(widgetId)
                originalCode = code
                documentNameEditor!!.setText(originalDocumentName)
                updateHeader()
                LauncherActivity.preview(this)
                return
            } else if (run) {
                Toast.makeText(this, "Suggestion script saved: " + name, Toast.LENGTH_SHORT).show()
            }

            originalDocumentName = LuaWidgetManager.getName(widgetId)
            originalCode = code
            documentNameEditor!!.setText(originalDocumentName)
            updateHeader()
            Toast.makeText(this, "Document saved: " + originalDocumentName, Toast.LENGTH_SHORT)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendModule(command: String?) {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, command)
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, widgetId)
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent)
    }

    private fun updateHeader() {
        if (header != null) {
            header!!.setText(if (widgetMode) "Lua Modules" else "Documents")
        }
    }

    private fun updateCapabilityPreview() {
        if (capabilityView == null) {
            return
        }
        if (!widgetMode) {
            capabilityView!!.setText("Document editor")
            return
        }

        val code = if (codeEditor == null) "" else codeEditor!!.getText().toString()
        val meta = LuaWidgetManager.metadata(code)
        val type = LuaWidgetManager.getScriptTypeFromScript(code)
        val capabilities = LuaWidgetManager.describeCapabilities(code)
        val permissions = LuaWidgetManager.describeRequiredPermissions(code)
        val api = LuaWidgetManager.apiVersionFromScript(code)
        val name = if (documentNameEditor == null) "" else documentNameEditor!!.getText().toString()
            .trim { it <= ' ' }
        val id = LuaWidgetManager.idFromName(name)
        capabilityView!!.setText(
            ("Type: " + type
                    + "  |  ID: " + (if (TextUtils.isEmpty(id)) "n/a" else id)
                    + "  |  API: " + api
                    + "  |  Capabilities: " + capabilities
                    + "  |  Permissions: " + permissions
                    + capabilityWarning(meta, code))
        )
    }

    private fun capabilityWarning(meta: MutableMap<String?, String?>, code: String?): String {
        val declared: String = meta["permissions"] ?: ""
        val missing = LuaWidgetManager.missingPermissionDeclarations(code)
        val unsupported = LuaWidgetManager.unsupportedPermissions(code)
        if (!unsupported.isEmpty()) {
            return "  |  Unsupported: " + TextUtils.join(", ", unsupported)
        }
        if (!missing.isEmpty()) {
            return "  |  Declare: " + TextUtils.join(", ", missing)
        }
        if (TextUtils.isEmpty(declared)) {
            return "  |  Metadata: inferred"
        }
        return "  |  Metadata: " + declared
    }

    private fun attemptClose() {
        if (!hasUnsavedChanges()) {
            finishAndRemoveTask()
            return
        }
        showConfirm(
            this,
            "Discard Changes?",
            "Unsaved document changes will be lost.",
            "Discard",
            "Keep Editing",
            ConfirmAction { this.finishAndRemoveTask() })
    }

    private fun hasUnsavedChanges(): Boolean {
        return !TextUtils.equals(
            originalDocumentName,
            documentNameEditor!!.getText().toString().trim { it <= ' ' })
                || !TextUtils.equals(originalCode, codeEditor!!.getText().toString())
    }

    companion object {
        const val EXTRA_WIDGET_ID: String = "widget_id"
        const val EXTRA_FILE_PATH: String = "file_path"

        @JvmStatic
        fun openFile(context: Context, file: File) {
            context.startActivity(
                Intent(context, WidgetEditorActivity::class.java).apply {
                    putExtra(EXTRA_FILE_PATH, file.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        @JvmStatic
        fun openWidget(context: Context, widgetId: String?) {
            context.startActivity(
                Intent(context, WidgetEditorActivity::class.java).apply {
                    putExtra(EXTRA_WIDGET_ID, widgetId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        private fun targetKey(intent: Intent): String? {
            val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            if (!filePath.isNullOrEmpty()) {
                return "file:${File(filePath).absolutePath}"
            }
            val id = LuaWidgetManager.normalizeId(intent.getStringExtra(EXTRA_WIDGET_ID))
            return if (id.isNullOrEmpty()) null else "widget:$id"
        }
    }
}
