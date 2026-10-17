package ohi.andre.consolelauncher.managers.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler
import ohi.andre.consolelauncher.managers.SpaceManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.cyberdeckMode
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.help.PanelBackdrop
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Android AppWidget host for the widgets pane (toolbar drawer).
 * Not dock modules — dock items are [ohi.andre.consolelauncher.managers.modules.ModuleManager].
 */
class AndroidWidgetDrawerManager(
    private val activity: Activity,
    private val rootView: ViewGroup,
    private val closeKeyboard: () -> Unit,
    private val onSurfaceOpen: () -> Unit,
    private val onSurfaceClose: () -> Unit
) {
    private val context: Context = activity
    private val appWidgetManager = AppWidgetManager.getInstance(context)
    private val widgetHostContext: Context =
        ContextThemeWrapper(context.applicationContext, android.R.style.Theme_DeviceDefault)
    private val appWidgetHost = RetuiAppWidgetHost(widgetHostContext, HOST_ID)
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val drawerRoot: View? = rootView.findViewById(R.id.android_widget_drawer_root)
    private val drawerContainer: View? = rootView.findViewById(R.id.android_widget_drawer_container)
    private val viewport: FrameLayout? = rootView.findViewById(R.id.android_widget_viewport)
    private val drawerSurface: View? = rootView.findViewById(R.id.android_widget_surface)
    private val commands: View? = rootView.findViewById(R.id.android_widget_commands)
    private val addButton: TextView? = rootView.findViewById(R.id.android_widget_add)
    private val removeButton: TextView? = rootView.findViewById(R.id.android_widget_remove)
    private val editButton: TextView? = rootView.findViewById(R.id.android_widget_edit)
    private val commandToggle: TextView? = rootView.findViewById(R.id.android_widget_command_toggle)
    private val emptyView: View? = rootView.findViewById(R.id.android_widget_empty)
    private val emptyAdd: TextView? = rootView.findViewById(R.id.android_widget_empty_add)
    private val widgetFrames = mutableMapOf<Int, WidgetFrame>()
    private var editMode = false
    private var commandsVisible = false
    private val renderRunnable = Runnable { if (isOpen) renderWidgets() }
    private val drawerScroll: ScrollView? = rootView.findViewById(R.id.android_widget_drawer_scroll)
    private val widgetGrid: WidgetGridLayout? = rootView.findViewById(R.id.android_widget_grid)
    private val header: TextView? = rootView.findViewById(R.id.android_widget_drawer_header)
    private val footer: TextView? = rootView.findViewById(R.id.android_widget_drawer_footer)
    private val closeButton: TextView? = rootView.findViewById(R.id.android_widget_drawer_add)
    private val commandSuggestionsScroll: HorizontalScrollView? =
        rootView.findViewById(R.id.android_widget_suggestions_container)
    private val commandSuggestionsGroup: LinearLayout? =
        rootView.findViewById(R.id.android_widget_suggestions_group)
    private val commandInputGroup: View? = rootView.findViewById(R.id.android_widget_input_group)
    private val commandPrefix: TextView? = rootView.findViewById(R.id.android_widget_prefix)
    private val commandInput: EditText? = rootView.findViewById(R.id.android_widget_input)
    private val commandSend: TextView? = rootView.findViewById(R.id.android_widget_send)
    private var pickerOverlay: View? = null
    private var pickerLoadGeneration = 0
    private var editingWidgetId = INVALID_WIDGET_ID
    private var resizePreviewFrame: WidgetFrame? = null
    private var providerResizePosted = false
    private val providerResizeRunnable = Runnable {
        providerResizePosted = false
        val frame = resizePreviewFrame
        val preview = widgetGrid?.resizePreview?.second
        if (frame != null && preview != null) {
            val host = frame.getChildAt(0) as AppWidgetHostView
            updateHostWidgetSize(host, (preview.width - frame.paddingLeft - frame.paddingRight).coerceAtLeast(1),
                (preview.height - frame.paddingTop - frame.paddingBottom).coerceAtLeast(1))
            frame.tag = null
        }
    }
    private var lastRenderedCellSize = 0
    private var listening = false
    private var surfaceOpen = false
    private var recordsReadable = true

    init {
        viewport?.let(PanelBackdrop::install)
        widgetGrid?.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL) finishResizePreview(false)
            if (r - l != oldR - oldL || b - t != oldB - oldT) scheduleRender()
        }
        addButton?.setOnClickListener { executeAddWidgetCommand() }
        emptyAdd?.setOnClickListener { executeAddWidgetCommand() }
        removeButton?.setOnClickListener {
            loadRecords().firstOrNull { it.appWidgetId == editingWidgetId }?.let(::removeWidget)
            editingWidgetId = INVALID_WIDGET_ID
            renderWidgets()
        }
        editButton?.setOnClickListener {
            editMode = !editMode
            if (!editMode) editingWidgetId = INVALID_WIDGET_ID
            renderWidgets()
        }
        commandToggle?.setOnClickListener {
            commandsVisible = !commandsVisible
            if (!commandsVisible) { commandInput?.clearFocus(); closeKeyboard() }
            renderWidgets()
        }
        drawerSurface?.setOnClickListener { }
        configureWidgetCommandInput()
        configureOutsideEditCancellation()
        drawerRoot?.setOnClickListener { hide() }
        drawerContainer?.setOnClickListener { }
        closeButton?.setOnClickListener { hide() }
    }

    val isOpen: Boolean
        get() = drawerRoot != null && drawerRoot.visibility == View.VISIBLE

    fun show() {
        if (drawerRoot == null || widgetGrid == null) {
            return
        }

        closeKeyboard()
        openSurface()
        cleanupWidgetState()
        startListening()
        styleChrome()
        drawerRoot.visibility = View.VISIBLE
        scheduleRender()
    }

    fun hide() {
        if (!isOpen && !surfaceOpen) return
        finishResizePreview(false)
        hideWidgetPicker()
        editingWidgetId = INVALID_WIDGET_ID
        editMode = false
        commandsVisible = false
        commandInput?.clearFocus()
        closeKeyboard()
        stopListening()
        drawerRoot?.removeCallbacks(renderRunnable)
        drawerRoot?.visibility = View.GONE
        closeSurface()
    }

    fun applyDisplayMargins(left: Int, top: Int, right: Int, bottom: Int) {
        (viewport?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            if (params.leftMargin != left || params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
                finishResizePreview(false)
                params.setMargins(left, top, right, bottom)
                viewport.layoutParams = params
            }
        }
        scheduleRender()
    }

    fun refreshGrid() = scheduleRender()

    private fun scheduleRender() {
        drawerRoot?.removeCallbacks(renderRunnable)
        if (isOpen) drawerRoot?.post(renderRunnable)
    }

    fun handleBack(): Boolean {
        when {
            pickerOverlay != null -> hideWidgetPicker()
            editMode || editingWidgetId != INVALID_WIDGET_ID -> {
                editingWidgetId = INVALID_WIDGET_ID
                editMode = false
                renderWidgets()
            }
            commandsVisible -> {
                commandsVisible = false
                commandInput?.clearFocus()
                closeKeyboard()
                renderWidgets()
            }
            else -> return false
        }
        return true
    }

    private fun configureOutsideEditCancellation() {
        val dismissListener = View.OnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                cancelWidgetEditForOutsideTouch(event.rawX, event.rawY)
            } else {
                false
            }
        }

        drawerScroll?.setOnTouchListener(dismissListener)
        commandInputGroup?.setOnTouchListener(dismissListener)
        commandPrefix?.setOnTouchListener(dismissListener)
        commandInput?.setOnTouchListener(dismissListener)
        commandSend?.setOnTouchListener(dismissListener)
        commandSuggestionsScroll?.setOnTouchListener(dismissListener)
        header?.setOnTouchListener(dismissListener)
        footer?.setOnTouchListener(dismissListener)
    }

    private fun cancelWidgetEditForOutsideTouch(rawX: Float, rawY: Float): Boolean {
        if (editingWidgetId == INVALID_WIDGET_ID) {
            return false
        }

        val activeFrame = findEditingWidgetFrame()
        if (activeFrame != null && isPointInsideView(activeFrame, rawX, rawY)) {
            return false
        }

        editingWidgetId = INVALID_WIDGET_ID
        widgetGrid?.post {
            if (isOpen && editingWidgetId == INVALID_WIDGET_ID) {
                renderWidgets()
            }
        } ?: renderWidgets()
        return false
    }

    private fun findEditingWidgetFrame(): WidgetFrame? {
        val grid = widgetGrid ?: return null
        for (index in 0 until grid.childCount) {
            val child = grid.getChildAt(index)
            if (child is WidgetFrame && child.matches(editingWidgetId)) {
                return child
            }
        }
        return null
    }

    private fun isPointInsideView(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + view.width
        val bottom = top + view.height
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    fun startListening() {
        if (listening) {
            return
        }
        try {
            appWidgetHost.startListening()
            listening = true
        } catch (e: RuntimeException) {
            Tuils.log(e)
        }
    }

    fun stopListening() {
        finishResizePreview()
        if (!listening) {
            return
        }
        try {
            appWidgetHost.stopListening()
        } catch (e: RuntimeException) {
            Tuils.log(e)
        } finally {
            listening = false
        }
    }

    fun dispose() {
        stopListening()
        drawerRoot?.removeCallbacks(renderRunnable)
        hideWidgetPicker()
        widgetFrames.clear()
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_BIND_WIDGET
            && requestCode != REQUEST_CONFIGURE_WIDGET
        ) {
            return false
        }

        val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, INVALID_WIDGET_ID)
            ?.takeIf { it != INVALID_WIDGET_ID }
            ?: preferences.getInt(KEY_PENDING_WIDGET_ID, INVALID_WIDGET_ID)

        if (resultCode != Activity.RESULT_OK || appWidgetId == INVALID_WIDGET_ID) {
            deletePendingWidget(appWidgetId)
            return true
        }

        if (requestCode == REQUEST_BIND_WIDGET) {
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (providerInfo?.configure != null) {
                configureWidget(appWidgetId)
            } else {
                finishAddWidget(appWidgetId)
            }
            return true
        }

        finishAddWidget(appWidgetId)
        return true
    }

    private fun configureWidgetCommandInput() {
        commandInput?.let { input ->
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            input.setSingleLine(true)
            input.setOnEditorActionListener { _, actionId, event ->
                val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (isEnter && event.action != KeyEvent.ACTION_UP) {
                    return@setOnEditorActionListener true
                }
                if (isEnter || actionId == EditorInfo.IME_ACTION_GO) {
                    submitWidgetCommand()
                    true
                } else {
                    false
                }
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    updateWidgetCommandSuggestions(s?.toString().orEmpty())
                }
            })
        }
        commandSend?.setOnClickListener { submitWidgetCommand() }
        updateWidgetCommandSuggestions()
    }

    private fun submitWidgetCommand() {
        val input = commandInput ?: return
        val rawCommand = input.text?.toString()?.trim().orEmpty()
        if (rawCommand.isBlank()) {
            updateWidgetCommandSuggestions()
            return
        }

        input.setText(Tuils.EMPTYSTRING)
        val parts = rawCommand.split(Regex("\\s+")).filter { it.isNotBlank() }
        when (parts.firstOrNull()?.lowercase(Locale.US)) {
            "add" -> executeAddWidgetCommand()
            "show" -> executeShowWidgetCommand(parts.drop(1))
            "resize" -> executeResizeWidgetCommand(parts)
            "remove" -> executeRemoveWidgetCommand(parts)
            else -> showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_unknown_widget_command_155c9, parts.firstOrNull().orEmpty()))
        }
        updateWidgetCommandSuggestions()
    }

    private fun executeAddWidgetCommand() {
        commandInput?.clearFocus()
        closeKeyboard()
        showWidgetPicker()
        showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_pick_a_widget_0598a))
    }

    private fun executeShowWidgetCommand(args: List<String>) {
        val records = widgetCommandRecords()
        if (records.isEmpty()) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_no_widgets_in_pane_ef590))
            return
        }

        val requestedId = args.firstOrNull()
        if (requestedId.isNullOrBlank() || requestedId.equals("w_id", ignoreCase = true)) {
            showWidgetCommandStatus(records.joinToString("  ") { widgetCommandSummary(it) }, Toast.LENGTH_LONG)
            updateWidgetCommandSuggestions("show ")
            return
        }

        val record = findWidgetRecordByCode(records, requestedId)
        if (record == null) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_no_widget_for_w_id_0f5d6, requestedId.uppercase(Locale.US)))
            updateWidgetCommandSuggestions("show ")
            return
        }

        showWidgetCommandStatus(widgetCommandSummary(record), Toast.LENGTH_LONG)
    }

    private fun executeResizeWidgetCommand(parts: List<String>) {
        if (parts.size < 4) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_usage_resize_w_id_height_width_9def5))
            updateWidgetCommandSuggestions("resize ")
            return
        }

        val records = loadRecords().filterValid()
        val record = findWidgetRecordByCode(records, parts[1])
        if (record == null) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_no_widget_for_w_id_0f5d6, parts[1].uppercase(Locale.US)))
            updateWidgetCommandSuggestions("resize ")
            return
        }

        val requestedRows = parts[2].toIntOrNull()
        val requestedColumns = parts[3].toIntOrNull()
        if (requestedRows == null || requestedColumns == null) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_usage_resize_w_id_height_width_9def5))
            updateWidgetCommandSuggestions("resize ${record.wid} ")
            return
        }

        val resizedRecord = constrainRecordToProvider(
            record.copy(
                rowSpan = requestedRows.coerceAtLeast(1),
                colSpan = requestedColumns.coerceIn(1, baseColumnCount())
            )
        )
        saveRecords(compactRecordsWithLocked(resizedRecord, records))
        editingWidgetId = resizedRecord.appWidgetId
        renderWidgets()
        showWidgetCommandStatus(
            context.getString(R.string.manager_androidwidgetdrawermanager_resized_to_x_476c9, widgetIdBadgeText(resizedRecord), resizedRecord.rowSpan, resizedRecord.colSpan)
        )
    }

    private fun executeRemoveWidgetCommand(parts: List<String>) {
        if (parts.size < 2) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_usage_remove_w_id_13569))
            updateWidgetCommandSuggestions("remove ")
            return
        }

        val records = loadRecords().filterValid()
        val record = findWidgetRecordByCode(records, parts[1])
        if (record == null) {
            showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_no_widget_for_w_id_0f5d6, parts[1].uppercase(Locale.US)))
            updateWidgetCommandSuggestions("remove ")
            return
        }

        val removedLabel = widgetIdBadgeText(record)
        editingWidgetId = INVALID_WIDGET_ID
        removeWidget(record)
        showWidgetCommandStatus(context.getString(R.string.manager_androidwidgetdrawermanager_removed_80296, removedLabel))
    }

    private fun updateWidgetCommandSuggestions(rawInput: String = commandInput?.text?.toString().orEmpty()) {
        val group = commandSuggestionsGroup ?: return
        group.removeAllViews()

        val trimmedInput = rawInput.trimStart()
        val lowerInput = trimmedInput.lowercase(Locale.US)
        val parts = trimmedInput.split(Regex("\\s+")).filter { it.isNotBlank() }
        val suggestions = mutableListOf<WidgetCommandSuggestion>()

        when {
            lowerInput.isBlank() -> {
                suggestions.add(
                    WidgetCommandSuggestion(
                        label = "add",
                        inputText = "add",
                        executeOnTap = true
                    )
                )
                suggestions.add(WidgetCommandSuggestion("show", "show "))
                suggestions.add(WidgetCommandSuggestion("resize", "resize "))
                suggestions.add(WidgetCommandSuggestion("remove", "remove "))
            }
            lowerInput == "add" || lowerInput.startsWith("add ") -> {
                suggestions.add(
                    WidgetCommandSuggestion(
                        label = "add",
                        inputText = "add",
                        executeOnTap = true
                    )
                )
            }
            lowerInput == "show" || lowerInput.startsWith("show ") -> {
                val records = widgetCommandRecords()
                if (records.isEmpty()) {
                    suggestions.add(WidgetCommandSuggestion(context.getString(R.string.manager_androidwidgetdrawermanager_no_widgets_40266), "show "))
                } else {
                    records.forEach { record ->
                        suggestions.add(
                            WidgetCommandSuggestion(
                                widgetIdBadgeText(record),
                                "show ${record.wid}"
                            )
                        )
                    }
                }
            }
            lowerInput == "resize" || lowerInput.startsWith("resize ") -> {
                buildResizeSuggestions(parts, suggestions)
            }
            lowerInput == "remove" || lowerInput.startsWith("remove ") -> {
                buildRemoveSuggestions(suggestions)
            }
        }

        suggestions.forEach { suggestion ->
            group.addView(
                commandSuggestionChip(suggestion),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val spaces = SuggestionChipStyler.spacing()
                    setMargins(spaces[0], spaces[1], spaces[0], spaces[1])
                }
            )
        }
        commandSuggestionsScroll?.visibility = if (suggestions.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun buildResizeSuggestions(
        parts: List<String>,
        suggestions: MutableList<WidgetCommandSuggestion>
    ) {
        val records = widgetCommandRecords()
        if (records.isEmpty()) {
            suggestions.add(WidgetCommandSuggestion(context.getString(R.string.manager_androidwidgetdrawermanager_no_widgets_40266), "resize "))
            return
        }

        val targetRecord = parts.getOrNull(1)?.let { findWidgetRecordByCode(records, it) }
        if (targetRecord == null) {
            records.forEach { record ->
                suggestions.add(
                    WidgetCommandSuggestion(
                        widgetIdBadgeText(record),
                        "resize ${record.wid} "
                    )
                )
            }
            return
        }

        val providerInfo = appWidgetManager.getAppWidgetInfo(targetRecord.appWidgetId) ?: return
        val bounds = widgetSpanBounds(providerInfo)
        val typedHeight = parts.getOrNull(2)?.toIntOrNull()
        if (typedHeight == null) {
            for (height in bounds.minRows..min(bounds.maxRows, MAX_COMMAND_SPAN_SUGGESTION)) {
                suggestions.add(
                    WidgetCommandSuggestion(
                        "h:$height",
                        "resize ${targetRecord.wid} $height "
                    )
                )
            }
            return
        }

        val supportedHeight = typedHeight.coerceIn(bounds.minRows, bounds.maxRows)
        for (width in bounds.minColumns..bounds.maxColumns) {
            suggestions.add(
                WidgetCommandSuggestion(
                    "w:$width",
                    "resize ${targetRecord.wid} $supportedHeight $width"
                )
            )
        }
    }

    private fun buildRemoveSuggestions(suggestions: MutableList<WidgetCommandSuggestion>) {
        val records = widgetCommandRecords()
        if (records.isEmpty()) {
            suggestions.add(WidgetCommandSuggestion(context.getString(R.string.manager_androidwidgetdrawermanager_no_widgets_40266), "remove "))
            return
        }

        records.forEach { record ->
            suggestions.add(
                WidgetCommandSuggestion(
                    widgetIdBadgeText(record),
                    "remove ${record.wid}"
                )
            )
        }
    }

    private fun commandSuggestionChip(suggestion: WidgetCommandSuggestion): TextView {
        return SuggestionChipStyler.createView(context).apply {
            text = suggestion.label
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(appSuggestionTextColor())
            background = appSuggestionBackground()
            setOnClickListener {
                if (suggestion.executeOnTap) {
                    commandInput?.setText(suggestion.inputText)
                    submitWidgetCommand()
                } else {
                    commandInput?.setText(suggestion.inputText)
                    commandInput?.setSelection(suggestion.inputText.length)
                    commandInput?.requestFocus()
                }
            }
        }
    }

    private fun appSuggestionTextColor(): Int {
        val appTextColor = XMLPrefsManager.getColor(Suggestions.apps_text_color)
        return if (appTextColor == Int.MAX_VALUE) {
            XMLPrefsManager.getColor(Suggestions.default_text_color)
        } else {
            appTextColor
        }
    }

    private fun appSuggestionBackground(): Drawable {
        if (XMLPrefsManager.getBoolean(Suggestions.transparent_suggestions)) {
            return ColorDrawable(Color.TRANSPARENT)
        }

        val backgroundColor = XMLPrefsManager.getColor(Suggestions.apps_background_color)
        if (cyberdeckMode() || TerminalBorderRuntime.customFrameActive(context, FrameTarget.WIDGET_DRAWER)) {
            return TerminalBorderRuntime.panelDrawable(
                context,
                backgroundColor,
                ThemeColorResolver.color(Theme.widget_panel_border_color),
                1.0f,
                0,
                true,
                target = FrameTarget.WIDGET_DRAWER
            )
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = Tuils.dpToPx(context, moduleCornerRadius()).toFloat()
            setColor(backgroundColor)
        }
    }

    private fun showWidgetCommandStatus(message: String, toastDuration: Int = Toast.LENGTH_SHORT) {
        commandInput?.hint = message
        Toast.makeText(context, message, toastDuration).show()
    }

    private fun widgetCommandRecords(): List<WidgetRecord> {
        return loadRecords()
            .filterValid()
            .sortedWith(compareBy<WidgetRecord> { widgetCodeSortValue(it.wid) }.thenBy { it.row }.thenBy { it.col })
    }

    private fun widgetCommandSummary(record: WidgetRecord): String {
        return context.getString(R.string.manager_androidwidgetdrawermanager_x_97baa, widgetIdBadgeText(record), record.rowSpan, record.colSpan, widgetLabel(record))
    }

    private fun widgetLabel(record: WidgetRecord): String {
        return appWidgetManager.getAppWidgetInfo(record.appWidgetId)
            ?.loadLabel(context.packageManager)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: record.provider.substringAfterLast('/').substringAfterLast('.')
    }

    private fun findWidgetRecordByCode(records: List<WidgetRecord>, rawCode: String): WidgetRecord? {
        val code = sanitizeWidgetCode(rawCode) ?: return null
        return records.firstOrNull { it.wid == code }
    }

    private fun showWidgetPicker() {
        val root = viewport ?: return
        hideWidgetPicker()
        cleanupWidgetState()

        val pickerColor = ThemeColorResolver.color(Theme.widget_panel_text_color)
        val overlay = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            setBackgroundColor(Color.TRANSPARENT)
        }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 10),
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 12)
            )
            background = TerminalBorderRuntime.panelDrawable(
                context,
                ThemeColorResolver.color(Theme.widget_panel_background_color),
                ThemeColorResolver.color(Theme.widget_panel_border_color),
                1.5f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.WIDGET_DRAWER
            )
        }

        val headerRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        headerRow.addView(
            TextView(context).apply {
                text = context.getString(R.string.manager_androidwidgetdrawermanager_widget_picker_454a1)
                setTextColor(pickerColor)
                textSize = 14f
                setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(
            pickerButton("X").apply {
                contentDescription = context.getString(R.string.manager_androidwidgetdrawermanager_close_widget_picker_a0d0d)
                setOnClickListener { hideWidgetPicker() }
            },
            LinearLayout.LayoutParams(
                Tuils.dpToPx(context, 40),
                Tuils.dpToPx(context, 34)
            )
        )
        panel.addView(headerRow)
        val search = EditText(context).apply {
            hint = context.getString(R.string.widget_pane_search)
            setSingleLine(true)
            setTextColor(pickerColor)
            setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
            typeface = Tuils.getTypeface(context)
            textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            tag = "widget-picker-search"
        }
        panel.addView(search, LinearLayout.LayoutParams(-1, Tuils.dpToPx(context, 48)))

        val scrollView = ScrollView(context).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Tuils.dpToPx(context, 8), 0, Tuils.dpToPx(context, 8))
        }
        scrollView.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        panel.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        overlay.addView(
            panel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                val margin = Tuils.dpToPx(context, 18)
                setMargins(margin, margin, margin, margin)
            }
        )

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        pickerOverlay = overlay
        LauncherFontScale.applyRecursively(panel)
        val pickerGeneration = ++pickerLoadGeneration
        populateWidgetPickerAsync(content, search, pickerGeneration)
    }

    private fun hideWidgetPicker() {
        val overlay = pickerOverlay ?: return
        pickerLoadGeneration++
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        pickerOverlay = null
        closeKeyboard()
    }

    private fun populateWidgetPickerAsync(content: LinearLayout, search: EditText, pickerGeneration: Int) {
        content.removeAllViews()
        content.addView(pickerStatusText(context.getString(R.string.manager_androidwidgetdrawermanager_loading_widgets_af1a9)))
        Thread {
            val result = runCatching { loadWidgetProviderOptions() }
            activity.runOnUiThread {
                if (pickerGeneration != pickerLoadGeneration
                    || pickerOverlay == null
                    || content.parent == null
                ) {
                    return@runOnUiThread
                }
                val providers = result.getOrElse { error ->
                    Tuils.log(error)
                    content.removeAllViews()
                    content.addView(pickerButton(context.getString(R.string.widget_pane_retry)).apply {
                        setOnClickListener { showWidgetPicker() }
                    }, LinearLayout.LayoutParams(-1, Tuils.dpToPx(context, 48)))
                    return@runOnUiThread
                }
                fun filterProviders() {
                    val query = search.text.toString().trim()
                    content.removeAllViews()
                    val filtered = providers.filter { it.appLabel.contains(query, true) || it.widgetLabel.contains(query, true) }
                    populateWidgetPicker(content, filtered)
                    LauncherFontScale.applyRecursively(content)
                    if (filtered.isEmpty() && providers.isNotEmpty()) {
                        (content.getChildAt(0) as? TextView)?.setText(R.string.widget_pane_no_matches)
                    }
                }
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) = filterProviders()
                })
                filterProviders()
            }
        }.start()
    }

    private fun populateWidgetPicker(content: LinearLayout, providers: List<WidgetProviderOption>) {
        if (providers.isEmpty()) {
            content.addView(
                pickerStatusText(context.getString(R.string.manager_androidwidgetdrawermanager_no_widgets_found_8e343)),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        providers.groupBy { it.appLabel }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            .forEach { (appLabel, options) ->
                content.addView(sectionLabel(appLabel))
                options.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.widgetLabel })
                    .forEach { option ->
                        content.addView(widgetProviderRow(option))
                    }
            }
    }

    private fun pickerStatusText(message: String): TextView {
        return TextView(context).apply {
            text = message
            setTextColor(ThemeColorResolver.color(Theme.widget_panel_text_color))
            textSize = 12f
            typeface = Tuils.getTypeface(context)
            gravity = Gravity.CENTER
            setPadding(0, Tuils.dpToPx(context, 32), 0, Tuils.dpToPx(context, 32))
        }
    }

    private fun loadWidgetProviderOptions(): List<WidgetProviderOption> {
        val packageManager = context.packageManager
        return appWidgetManager.installedProviders
            .filter { providerInfo ->
                providerInfo.provider != null
                    && (providerInfo.widgetCategory == 0
                    || (providerInfo.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0)
            }
            .map { providerInfo ->
                val appLabel = appLabelFor(providerInfo, packageManager)
                val widgetLabel = providerInfo.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: providerInfo.provider.className.substringAfterLast('.')
                WidgetProviderOption(
                    providerInfo,
                    appLabel,
                    widgetLabel,
                    previewFor(providerInfo)
                )
            }
    }

    private fun widgetProviderRow(option: WidgetProviderOption): View {
        val textColor = ThemeColorResolver.color(Theme.widget_row_text_color)
        val mutedTextColor = ThemeColorResolver.withMaxAlpha(textColor, 153)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(
                Tuils.dpToPx(context, 8),
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 8),
                Tuils.dpToPx(context, 6)
            )
            background = TerminalBorderRuntime.panelDrawable(
                context,
                Color.TRANSPARENT,
                ThemeColorResolver.color(Theme.widget_panel_border_color),
                1.0f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.WIDGET_DRAWER
            )
            contentDescription = "${option.appLabel} ${option.widgetLabel}"
            setOnClickListener {
                hideWidgetPicker()
                beginAddWidget(option.providerInfo)
            }
        }

        row.addView(
            ImageView(context).apply {
                setImageDrawable(option.preview)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setBackgroundColor(Color.TRANSPARENT)
            },
            LinearLayout.LayoutParams(
                Tuils.dpToPx(context, 76),
                Tuils.dpToPx(context, 54)
            ).apply {
                marginEnd = Tuils.dpToPx(context, 10)
            }
        )

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labels.addView(
            TextView(context).apply {
                text = option.widgetLabel
                setTextColor(textColor)
                textSize = 12f
                maxLines = 2
                typeface = Tuils.getTypeface(context)
            }
        )
        labels.addView(
            TextView(context).apply {
                text = context.getString(R.string.manager_androidwidgetdrawermanager_x_96067, max(1, option.providerInfo.minWidth), max(1, option.providerInfo.minHeight))
                setTextColor(mutedTextColor)
                textSize = 9f
                maxLines = 1
                typeface = Tuils.getTypeface(context)
            }
        )
        row.addView(
            labels,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        return LinearLayout(context).apply {
            setPadding(0, 0, 0, Tuils.dpToPx(context, 6))
            addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun sectionLabel(label: String): TextView {
        return TextView(context).apply {
            text = label.uppercase()
            setTextColor(ThemeColorResolver.color(Theme.widget_header_text_color))
            textSize = 11f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            setPadding(0, Tuils.dpToPx(context, 12), 0, Tuils.dpToPx(context, 4))
        }
    }

    private fun pickerButton(label: String): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(ThemeColorResolver.color(Theme.widget_control_text_color))
            textSize = 14f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            background = TerminalBorderRuntime.panelDrawable(
                context,
                ThemeColorResolver.color(Theme.widget_control_background_color),
                ThemeColorResolver.color(Theme.widget_control_border_color),
                1f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.WIDGET_DRAWER
            )
        }
    }

    private fun appLabelFor(
        providerInfo: AppWidgetProviderInfo,
        packageManager: PackageManager
    ): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(providerInfo.provider.packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            providerInfo.provider.packageName
        }
    }

    private fun previewFor(providerInfo: AppWidgetProviderInfo): Drawable? {
        return try {
            providerInfo.loadPreviewImage(context, 0)
                ?: providerInfo.loadIcon(context, 0)
                ?: context.packageManager.getApplicationIcon(providerInfo.provider.packageName)
        } catch (e: Exception) {
            null
        }
    }

    private fun beginAddWidget(providerInfo: AppWidgetProviderInfo) {
        cleanupWidgetState()
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        trackAllocatedWidgetId(appWidgetId)
        preferences.edit()
            .putInt(KEY_PENDING_WIDGET_ID, appWidgetId)
            .putLong(KEY_PENDING_WIDGET_STARTED_AT, System.currentTimeMillis())
            .apply()

        try {
            if (appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)) {
                finishBoundWidget(appWidgetId, providerInfo)
            } else {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
                activity.startActivityForResult(intent, REQUEST_BIND_WIDGET)
            }
        } catch (e: Exception) {
            deletePendingWidget(appWidgetId)
            Toast.makeText(context, context.getString(R.string.manager_androidwidgetdrawermanager_couldn_t_bind_widget_804bd), Toast.LENGTH_SHORT).show()
            Tuils.log(e)
        }
    }

    private fun finishBoundWidget(
        appWidgetId: Int,
        fallbackProviderInfo: AppWidgetProviderInfo
    ) {
        val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: fallbackProviderInfo
        if (providerInfo.configure != null) {
            configureWidget(appWidgetId)
        } else {
            finishAddWidget(appWidgetId)
        }
    }

    private fun configureWidget(appWidgetId: Int) {
        try {
            appWidgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                appWidgetId,
                0,
                REQUEST_CONFIGURE_WIDGET,
                null
            )
        } catch (e: Exception) {
            deletePendingWidget(appWidgetId)
            Toast.makeText(context, context.getString(R.string.manager_androidwidgetdrawermanager_couldn_t_configure_widget_1cc6c), Toast.LENGTH_SHORT).show()
            Tuils.log(e)
        }
    }

    private fun finishAddWidget(appWidgetId: Int) {
        preferences.edit()
            .remove(KEY_PENDING_WIDGET_ID)
            .remove(KEY_PENDING_WIDGET_STARTED_AT)
            .apply()
        val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (providerInfo == null) {
            deletePendingWidget(appWidgetId)
            return
        }

        trackAllocatedWidgetId(appWidgetId)
        val records = loadRecords().filterValid().toMutableList()
        if (records.any { it.appWidgetId == appWidgetId }) {
            saveRecords(records)
            renderWidgets()
            return
        }

        val baseColumns = baseColumnCount()
        val span = widgetSpanBounds(providerInfo).initial
        val position = nextOpenPosition(records, span.colSpan, span.rowSpan, columnCount = baseColumns)
        records.add(
            WidgetRecord(
                nextWidgetCode(records),
                appWidgetId,
                providerInfo.provider.flattenToString(),
                position.col,
                position.row,
                span.colSpan,
                span.rowSpan
            )
        )
        saveRecords(records)
        renderWidgets()
        if (!isOpen) {
            show()
        }
    }

    private fun deletePendingWidget(appWidgetId: Int) {
        preferences.edit()
            .remove(KEY_PENDING_WIDGET_ID)
            .remove(KEY_PENDING_WIDGET_STARTED_AT)
            .apply()
        if (appWidgetId != INVALID_WIDGET_ID) {
            val otherIds = SpaceManager.widgetIdsInOtherSpaces(context)
            val currentRecords = loadRecords()
            if (recordsReadable && otherIds != null && appWidgetId !in otherIds && currentRecords.none { it.appWidgetId == appWidgetId }) {
                deleteWidgetId(appWidgetId)
                untrackAllocatedWidgetId(appWidgetId)
            }
        }
    }

    private fun removeWidget(record: WidgetRecord) {
        val remaining = loadRecords().filter { it.appWidgetId != record.appWidgetId }
        if (!recordsReadable) return
        saveRecords(remaining)
        cleanupTrackedOrphans(remaining)
        renderWidgets()
    }

    private fun removeWidgetById(appWidgetId: Int) {
        val record = loadRecords().firstOrNull { it.appWidgetId == appWidgetId }
        if (record != null) {
            removeWidget(record)
        } else {
            deletePendingWidget(appWidgetId)
        }
    }

    private fun renderWidgets() {
        val grid = widgetGrid ?: return
        if (grid.width <= 0 || !isOpen || resizePreviewFrame != null) return

        val records = loadRecords()
        val validRecords = cleanupWidgetState(records)
        if (validRecords != records) {
            saveRecords(validRecords)
        }

        val retained = validRecords.map { it.appWidgetId }.toSet()
        widgetFrames.keys.retainAll(retained)
        val baseColumns = baseColumnCount()
        val cellSize = uniformCellSize(grid, baseColumns)
        lastRenderedCellSize = cellSize
        grid.cellSize = cellSize
        val displayColumns = displayColumnCount(grid, cellSize, baseColumns)
        val cellMargin = Tuils.dpToPx(context, 4)
        val supportedRecords = validRecords.map { constrainRecordToProvider(it, cellSize, cellMargin) }
        if (supportedRecords != validRecords) {
            saveRecords(supportedRecords)
        }
        val displayRecords = layoutRecordsForColumns(supportedRecords, displayColumns)
        val displayRows = displayRowCount(displayRecords)
        val layoutChanged = grid.columnCount != displayColumns || grid.rowCount != displayRows ||
            grid.childCount != displayRecords.size || displayRecords.any { record ->
                val frame = widgetFrames[record.appWidgetId]
                val params = frame?.layoutParams as? GridLayout.LayoutParams
                params == null || params.rowSpec != GridLayout.spec(record.row, record.rowSpan) ||
                    params.columnSpec != GridLayout.spec(record.col, record.colSpan) ||
                    params.width != max(1, cellSize * record.colSpan - cellMargin * 2) ||
                    params.height != max(1, cellSize * record.rowSpan - cellMargin * 2)
            }
        if (layoutChanged) grid.removeAllViews()
        grid.columnCount = displayColumns
        grid.rowCount = displayRows
        grid.minimumHeight = if (displayRecords.isEmpty()) 0 else displayRows * cellSize

        for (record in displayRecords) {
            val providerInfo = appWidgetManager.getAppWidgetInfo(record.appWidgetId) ?: continue
            val widgetWidth = max(1, (cellSize * record.colSpan) - (cellMargin * 2))
            val widgetHeight = max(1, (cellSize * record.rowSpan) - (cellMargin * 2))
            val frame = widgetFrames.getOrPut(record.appWidgetId) {
                WidgetFrame(context, record.appWidgetId).apply {
                    val hostView = appWidgetHost.createView(widgetHostContext, record.appWidgetId, providerInfo)
                    addView(hostView, FrameLayout.LayoutParams(-1, -1))
                }
            }
            val hostView = frame.getChildAt(0) as AppWidgetHostView
            val inset = Tuils.dpToPx(context, 8)
            frame.setPadding(inset, inset, inset, inset)
            frame.clipToPadding = false
            val size = (widgetWidth - inset * 2).coerceAtLeast(1) to (widgetHeight - inset * 2).coerceAtLeast(1)
            if (frame.tag != size) {
                updateHostWidgetSize(hostView, size.first, size.second)
                frame.tag = size
            }
            while (frame.childCount > 1) frame.removeViewAt(1)
            frame.background = if (editMode || editingWidgetId == record.appWidgetId) TerminalBorderRuntime.panelDrawable(
                context, Color.TRANSPARENT, ThemeColorResolver.color(Theme.widget_panel_border_color),
                1f, moduleCornerRadius(), dashedBorders(), target = FrameTarget.WIDGET_DRAWER) else null
            if (commandsVisible) {
                frame.addView(widgetIdBadge(record), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
            }
            if (editingWidgetId == record.appWidgetId) addEditControls(frame, record)

            val params = WidgetGridLayout.LayoutParams(record.row, record.rowSpan, record.col, record.colSpan)
            params.width = widgetWidth
            params.height = widgetHeight
            params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
            if (frame.parent == null) grid.addView(frame, params)
        }

        updateLabels(supportedRecords.size)
        emptyView?.visibility = if (supportedRecords.isEmpty()) View.VISIBLE else View.GONE
        addButton?.visibility = if (supportedRecords.isEmpty()) View.GONE else View.VISIBLE
        editButton?.visibility = if (supportedRecords.isEmpty()) View.GONE else View.VISIBLE
        editButton?.setText(if (editMode) R.string.widget_pane_done else R.string.widget_pane_edit)
        removeButton?.visibility = if (editingWidgetId != INVALID_WIDGET_ID) View.VISIBLE else View.GONE
        footer?.visibility = if (editMode) View.VISIBLE else View.GONE
        commands?.visibility = if (commandsVisible) View.VISIBLE else View.GONE
        commandToggle?.isSelected = commandsVisible
        updateWidgetCommandSuggestions()
        LauncherFontScale.applyRecursively(drawerSurface)
    }

    private fun widgetIdBadge(record: WidgetRecord): TextView {
        return TextView(context).apply {
            text = widgetIdBadgeText(record)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(ThemeColorResolver.color(Theme.widget_control_text_color))
            textSize = 9f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            setPadding(
                Tuils.dpToPx(context, 7),
                Tuils.dpToPx(context, 2),
                Tuils.dpToPx(context, 7),
                Tuils.dpToPx(context, 2)
            )
            background = TerminalBorderRuntime.panelDrawable(
                context,
                ThemeColorResolver.color(Theme.widget_control_background_color),
                ThemeColorResolver.color(Theme.widget_control_border_color),
                1f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.WIDGET_DRAWER
            )
        }
    }

    private fun addEditControls(frame: FrameLayout, record: WidgetRecord) {
        val bounds = appWidgetManager.getAppWidgetInfo(record.appWidgetId)?.let(::widgetSpanBounds) ?: return
        if (bounds.minColumns == bounds.maxColumns && bounds.minRows == bounds.maxRows) return
        for (top in listOf(true, false)) for (left in listOf(true, false)) {
            val corner = (if (top) "top" else "bottom") + "-" + (if (left) "left" else "right")
            val handle = object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = ThemeColorResolver.color(Theme.widget_control_text_color)
                    strokeWidth = Tuils.dpToPx(context, 2).toFloat()
                    strokeCap = Paint.Cap.ROUND
                    alpha = 180
                }
                override fun onDraw(canvas: Canvas) {
                    val inset = Tuils.dpToPx(context, 2).toFloat()
                    val length = Tuils.dpToPx(context, 7).toFloat()
                    val x = if (left) inset else width - inset
                    val y = if (top) inset else height - inset
                    canvas.drawLine(x, y, x + if (left) length else -length, y, paint)
                    canvas.drawLine(x, y, x, y + if (top) length else -length, paint)
                }
            }.apply {
                tag = "widget-resize-$corner"
                contentDescription = context.getString(R.string.widget_pane_resize_corner, corner)
                isClickable = true
                isFocusable = true
                setOnClickListener { resizeWidgetCorner(record, left, top, if (left) -lastRenderedCellSize.toFloat() else lastRenderedCellSize.toFloat(), if (top) -lastRenderedCellSize.toFloat() else lastRenderedCellSize.toFloat()) }
                setOnLongClickListener {
                    resizeWidgetCorner(record, left, top, if (left) lastRenderedCellSize.toFloat() else -lastRenderedCellSize.toFloat(), if (top) lastRenderedCellSize.toFloat() else -lastRenderedCellSize.toFloat()); true
                }
                var startX = 0f; var startY = 0f
                var moved = false
                var startBounds = WidgetResizeRect(0, 0, 1, 1)
                setOnTouchListener { view, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.rawX; startY = event.rawY; moved = false
                            startBounds = WidgetResizeRect(frame.left, frame.top, frame.right, frame.bottom)
                            resizePreviewFrame = frame as WidgetFrame
                            parent?.requestDisallowInterceptTouchEvent(true)
                            view.isPressed = true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            moved = moved || abs(event.rawX - startX) > ViewConfiguration.get(context).scaledTouchSlop || abs(event.rawY - startY) > ViewConfiguration.get(context).scaledTouchSlop
                            if (moved) {
                                val cell = max(1, lastRenderedCellSize)
                                val margin = Tuils.dpToPx(context, 4)
                                val preview = previewWidgetCorner(startBounds, left, top, event.rawX - startX, event.rawY - startY,
                                    bounds.minColumns * cell - 2 * margin, bounds.maxColumns * cell - 2 * margin,
                                    bounds.minRows * cell - 2 * margin, bounds.maxRows * cell - 2 * margin,
                                    widgetGrid?.width ?: frame.width, margin)
                                frame.bringToFront()
                                widgetGrid?.resizePreview = frame to preview
                                // Native content reflows every frame; provider callbacks are coalesced.
                                if (!providerResizePosted) {
                                    providerResizePosted = true
                                    widgetGrid?.postDelayed(providerResizeRunnable, 80)
                                }
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            view.isPressed = false
                            parent?.requestDisallowInterceptTouchEvent(false)
                            finishResizePreview(false, restoreProvider = false)
                            if (moved) resizeWidgetCorner(record, left, top, event.rawX - startX, event.rawY - startY)
                            else view.performClick()
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.isPressed = false
                            parent?.requestDisallowInterceptTouchEvent(false)
                            finishResizePreview()
                        }
                    }
                    true
                }
            }
            // Keep the grip visually small while retaining an accessible touch target.
            val size = Tuils.dpToPx(context, 48).coerceAtMost(min(frame.width.takeIf { it > 0 } ?: Int.MAX_VALUE, frame.height.takeIf { it > 0 } ?: Int.MAX_VALUE) / 2)
            frame.addView(handle, FrameLayout.LayoutParams(size, size,
                (if (top) Gravity.TOP else Gravity.BOTTOM) or (if (left) Gravity.LEFT else Gravity.RIGHT)).apply {
                setMargins(-frame.paddingLeft, -frame.paddingTop, -frame.paddingRight, -frame.paddingBottom)
            })
        }
    }

    private fun finishResizePreview(render: Boolean = true, restoreProvider: Boolean = true) {
        val frame = resizePreviewFrame ?: return
        val hadPreview = widgetGrid?.resizePreview != null
        resizePreviewFrame = null
        widgetGrid?.removeCallbacks(providerResizeRunnable)
        providerResizePosted = false
        widgetGrid?.resizePreview = null
        if (restoreProvider && hadPreview) {
            val width = (frame.layoutParams.width - frame.paddingLeft - frame.paddingRight).coerceAtLeast(1)
            val height = (frame.layoutParams.height - frame.paddingTop - frame.paddingBottom).coerceAtLeast(1)
            updateHostWidgetSize(frame.getChildAt(0) as AppWidgetHostView, width, height)
            frame.tag = width to height
        } else frame.tag = null
        if (render) renderWidgets()
    }

    private fun resizeWidgetCorner(record: WidgetRecord, left: Boolean, top: Boolean, dx: Float, dy: Float) {
        val records = loadRecords().filterValid()
        val current = records.firstOrNull { it.appWidgetId == record.appWidgetId } ?: return
        val bounds = appWidgetManager.getAppWidgetInfo(record.appWidgetId)?.let(::widgetSpanBounds) ?: return
        val horizontal = if (left) ResizeEdge.LEFT else ResizeEdge.RIGHT
        val vertical = if (top) ResizeEdge.TOP else ResizeEdge.BOTTOM
        val width = resizeHorizontalEdgeByCells(current, horizontal, dragDeltaCells(horizontal, dx, dy), bounds)
        val resized = resizeVerticalEdgeByCells(width, vertical, dragDeltaCells(vertical, dx, dy), bounds)
        saveRecords(compactRecordsWithLocked(resized, records))
        editingWidgetId = resized.appWidgetId
        renderWidgets()
    }

    private fun dragDeltaCells(edge: ResizeEdge, deltaX: Float, deltaY: Float): Int {
        val cellSize = max(1, lastRenderedCellSize)
        val edgeDelta = when (edge) {
            ResizeEdge.LEFT -> -deltaX
            ResizeEdge.RIGHT -> deltaX
            ResizeEdge.TOP -> -deltaY
            ResizeEdge.BOTTOM -> deltaY
        }
        return (edgeDelta / cellSize.toFloat()).roundToInt()
    }

    private fun moveWidgetByDrag(appWidgetId: Int, deltaX: Float, deltaY: Float) {
        val cellSize = max(1, lastRenderedCellSize)
        val deltaColumns = (deltaX / cellSize.toFloat()).roundToInt()
        val deltaRows = (deltaY / cellSize.toFloat()).roundToInt()
        if (deltaColumns == 0 && deltaRows == 0) {
            editingWidgetId = appWidgetId
            renderWidgets()
            return
        }

        val records = loadRecords().filterValid()
        val currentRecord = records.firstOrNull { it.appWidgetId == appWidgetId } ?: return
        val baseColumns = widgetGrid?.columnCount?.takeIf { it > 0 } ?: baseColumnCount()
        val boundedRecord = normalizeRecord(currentRecord, baseColumns)
        val targetCol = (boundedRecord.col + deltaColumns)
            .coerceIn(0, baseColumns - boundedRecord.colSpan)
        val targetRow = max(0, boundedRecord.row + deltaRows)
        val movedRecord = boundedRecord.copy(col = targetCol, row = targetRow)

        if (movedRecord.col == boundedRecord.col && movedRecord.row == boundedRecord.row) {
            editingWidgetId = appWidgetId
            renderWidgets()
            return
        }

        saveRecords(compactRecordsWithLocked(movedRecord, records))
        editingWidgetId = appWidgetId
        renderWidgets()
    }

    private fun resizeHorizontalEdgeByCells(
        record: WidgetRecord,
        edge: ResizeEdge,
        deltaCells: Int,
        bounds: WidgetSpanBounds
    ): WidgetRecord {
        return if (deltaCells > 0) {
            AndroidWidgetLayout.growHorizontalEdge(record, edge, bounds.maxColumns,
                widgetGrid?.columnCount?.takeIf { it > 0 } ?: baseColumnCount(), deltaCells)
        } else {
            AndroidWidgetLayout.shrinkHorizontalEdge(record, edge, bounds.minColumns, -deltaCells)
        }
    }

    private fun resizeVerticalEdgeByCells(
        record: WidgetRecord,
        edge: ResizeEdge,
        deltaCells: Int,
        bounds: WidgetSpanBounds
    ): WidgetRecord {
        return if (deltaCells > 0) {
            AndroidWidgetLayout.growVerticalEdge(record, edge, bounds.maxRows, deltaCells)
        } else {
            AndroidWidgetLayout.shrinkVerticalEdge(record, edge, bounds.minRows, -deltaCells)
        }
    }

    private fun compactRecordsWithLocked(lockedRecord: WidgetRecord, records: List<WidgetRecord>) =
        AndroidWidgetLayout.place(lockedRecord, records, widgetGrid?.columnCount?.takeIf { it > 0 } ?: baseColumnCount())

    private fun layoutRecordsForColumns(records: List<WidgetRecord>, columnCount: Int) =
        AndroidWidgetLayout.layout(records, columnCount)

    private fun normalizeRecord(record: WidgetRecord, columnCount: Int = baseColumnCount()) =
        AndroidWidgetLayout.normalizeRecord(record, columnCount)

    private fun updateHostWidgetSize(
        hostView: AppWidgetHostView,
        widthPx: Int,
        heightPx: Int
    ) {
        val widthDp = pxToDp(widthPx)
        val heightDp = pxToDp(heightPx)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(Bundle(), widthDp, heightDp, widthDp, heightDp)
            }
        } catch (e: RuntimeException) {
            Tuils.log(e)
        }
    }

    private fun pxToDp(px: Int): Int {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        return max(1, Math.round(px / density))
    }

    private fun buildWidgetFailureView(appWidgetId: Int, label: String): View {
        val textColor = ThemeColorResolver.color(Theme.widget_panel_text_color)
        val mutedTextColor = ThemeColorResolver.withMaxAlpha(textColor, 153)
        val padding = Tuils.dpToPx(context, 10)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            background = TerminalBorderRuntime.panelDrawable(
                context,
                ThemeColorResolver.color(Theme.widget_panel_background_color),
                ThemeColorResolver.color(Theme.widget_panel_border_color),
                1.0f,
                moduleCornerRadius(),
                dashedBorders(),
                target = FrameTarget.WIDGET_DRAWER
            )
            addView(errorText(context.getString(R.string.manager_androidwidgetdrawermanager_widget_failed_to_render_6bf8a), textColor, 11f, true))
            addView(errorText(label.uppercase(), mutedTextColor, 9f, false))
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(pickerButton(context.getString(R.string.widget_pane_retry)).apply {
                    setOnClickListener {
                        widgetFrames.remove(appWidgetId)
                        renderWidgets()
                    }
                }, LinearLayout.LayoutParams(0, Tuils.dpToPx(context, 48), 1f))
                addView(pickerButton(context.getString(R.string.widget_pane_remove)).apply {
                    setOnClickListener { removeWidgetById(appWidgetId) }
                }, LinearLayout.LayoutParams(0, Tuils.dpToPx(context, 48), 1f))
            })
        }
    }

    private fun errorText(
        text: String,
        color: Int,
        sizeSp: Float,
        bold: Boolean
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 2
            typeface = if (bold) {
                Typeface.create(Tuils.getTypeface(context), Typeface.BOLD)
            } else {
                Tuils.getTypeface(context)
            }
        }
    }

    private fun cleanupWidgetState(records: List<WidgetRecord> = loadRecords()): List<WidgetRecord> {
        cleanupStalePendingWidget(records)
        val validRecords = records.filterValid()
        cleanupTrackedOrphans(validRecords)
        return validRecords
    }

    private fun cleanupStalePendingWidget(records: List<WidgetRecord>) {
        val appWidgetId = preferences.getInt(KEY_PENDING_WIDGET_ID, INVALID_WIDGET_ID)
        if (appWidgetId == INVALID_WIDGET_ID || records.any { it.appWidgetId == appWidgetId }) {
            preferences.edit()
                .remove(KEY_PENDING_WIDGET_ID)
                .remove(KEY_PENDING_WIDGET_STARTED_AT)
                .apply()
            return
        }

        val startedAt = preferences.getLong(KEY_PENDING_WIDGET_STARTED_AT, 0L)
        val missingTimestamp = startedAt <= 0L
        val expired = System.currentTimeMillis() - startedAt > PENDING_WIDGET_TIMEOUT_MS
        if (missingTimestamp || expired) {
            deletePendingWidget(appWidgetId)
        }
    }

    private fun cleanupTrackedOrphans(records: List<WidgetRecord>) {
        if (!recordsReadable) return
        val otherIds = SpaceManager.widgetIdsInOtherSpaces(context) ?: return
        val savedIds = records.map { it.appWidgetId }.toSet()
        val pendingId = activePendingWidgetId()
        val retainedIds = (savedIds + otherIds).toMutableSet()
        if (pendingId != INVALID_WIDGET_ID) {
            retainedIds.add(pendingId)
        }

        for (appWidgetId in loadAllocatedWidgetIds()) {
            if (appWidgetId !in retainedIds) {
                deleteWidgetId(appWidgetId)
            }
        }
        saveAllocatedWidgetIds(retainedIds)
    }

    private fun activePendingWidgetId(): Int {
        val appWidgetId = preferences.getInt(KEY_PENDING_WIDGET_ID, INVALID_WIDGET_ID)
        if (appWidgetId == INVALID_WIDGET_ID) {
            return INVALID_WIDGET_ID
        }

        val startedAt = preferences.getLong(KEY_PENDING_WIDGET_STARTED_AT, 0L)
        val missingTimestamp = startedAt <= 0L
        val expired = System.currentTimeMillis() - startedAt > PENDING_WIDGET_TIMEOUT_MS
        return if (missingTimestamp || expired) INVALID_WIDGET_ID else appWidgetId
    }

    private fun deleteWidgetId(appWidgetId: Int, logErrors: Boolean = true) {
        try {
            appWidgetHost.deleteAppWidgetId(appWidgetId)
        } catch (e: RuntimeException) {
            if (logErrors) {
                Tuils.log(e)
            }
        }
    }

    private fun styleChrome() {
        val drawerColor = ThemeColorResolver.color(Theme.widget_panel_text_color)
        val borderColor = ThemeColorResolver.color(Theme.widget_panel_border_color)
        val backgroundColor = ThemeColorResolver.color(Theme.widget_panel_background_color)
        val headerBackgroundColor = ThemeColorResolver.color(Theme.widget_header_background_color)

        drawerRoot?.setBackgroundColor(Color.TRANSPARENT)
        drawerContainer?.background = TerminalBorderRuntime.panelDrawable(
            context,
            backgroundColor,
            borderColor,
            1.5f,
            moduleCornerRadius(),
            dashedBorders(),
            target = FrameTarget.WIDGET_DRAWER
        )
        header?.setTextColor(ThemeColorResolver.color(Theme.widget_header_text_color))
        footer?.setTextColor(ThemeColorResolver.color(Theme.widget_header_text_color))
        closeButton?.setTextColor(drawerColor)
        commandPrefix?.setTextColor(drawerColor)
        commandInput?.setTextColor(ThemeColorResolver.color(Theme.widget_command_text_color))
        commandInput?.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
        commandInput?.let { Tuils.setCursorDrawableColor(it, ThemeColorResolver.color(Theme.cursor_color)) }
        commandSend?.setTextColor(drawerColor)
        header?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        footer?.typeface = Tuils.getTypeface(context)
        closeButton?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        commandPrefix?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        commandInput?.typeface = Tuils.getTypeface(context)
        commandSend?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        header?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor, FrameTarget.WIDGET_DRAWER)
        footer?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor, FrameTarget.WIDGET_DRAWER)
        closeButton?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor, FrameTarget.WIDGET_DRAWER)
        commandInputGroup?.background = TerminalBorderRuntime.panelDrawable(
            context,
            ThemeColorResolver.color(Theme.widget_command_background_color),
            ThemeColorResolver.color(Theme.widget_command_border_color),
            1.2f,
            moduleCornerRadius(),
            dashedBorders(),
            target = FrameTarget.WIDGET_DRAWER
        )
        commandSend?.background = TerminalBorderRuntime.panelDrawable(
            context,
            Color.TRANSPARENT,
            ThemeColorResolver.withMaxAlpha(borderColor, 190),
            1f,
            moduleCornerRadius(),
            dashedBorders(),
            target = FrameTarget.WIDGET_DRAWER
        )
        listOfNotNull(addButton, editButton, commandToggle, emptyAdd).forEach { button ->
            button.typeface = Tuils.getTypeface(context)
            button.textSize = 12f
            button.setTextColor(ThemeColorResolver.color(Theme.widget_control_text_color))
            button.background = TerminalBorderRuntime.panelDrawable(context,
                ThemeColorResolver.color(Theme.widget_control_background_color), borderColor,
                1f, moduleCornerRadius(), dashedBorders(), target = FrameTarget.WIDGET_DRAWER)
            button.isFocusable = true
        }
        (emptyView as? ViewGroup)?.getChildAt(0)?.let { view ->
            (view as? TextView)?.apply { setTextColor(drawerColor); typeface = Tuils.getTypeface(context) }
        }
        header?.textSize = 12f
        closeButton?.background = android.graphics.drawable.InsetDrawable(closeButton?.background, 0, 0, 0, Tuils.dpToPx(context, 28))
        TerminalBorderRuntime.bind(drawerContainer, header)
        updateWidgetCommandSuggestions()
    }

    private fun updateLabels(recordCount: Int) {
        header?.setText(R.string.widget_pane_title)
        header?.contentDescription = context.getString(R.string.manager_androidwidgetdrawermanager_widgets_9209f, recordCount)
        footer?.text = context.getString(R.string.widget_pane_edit_hint)
    }

    private fun baseColumnCount(): Int {
        return XMLPrefsManager.getInt(Ui.android_widget_grid_columns)
            .coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
    }

    private fun minWidgetColumns(baseColumns: Int = baseColumnCount()): Int {
        return XMLPrefsManager.getInt(Ui.android_widget_min_columns)
            .coerceIn(1, min(MAX_MIN_WIDGET_SPAN, baseColumns))
    }

    private fun minWidgetRows(): Int {
        return XMLPrefsManager.getInt(Ui.android_widget_min_rows)
            .coerceIn(1, MAX_MIN_WIDGET_SPAN)
    }

    private fun uniformCellSize(grid: GridLayout, baseColumns: Int): Int {
        val rootWidth = (drawerRoot?.width ?: rootView.width).takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val rootHeight = (drawerRoot?.height ?: rootView.height).takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val shortestRootSide = min(rootWidth, rootHeight)
        val contentRatio = grid.width.toFloat() / max(1f, rootWidth.toFloat())
        val portraitEquivalentGridWidth = max(1, (shortestRootSide * contentRatio).toInt())
        val minimumCellSize = Tuils.dpToPx(context, MIN_WIDGET_CELL_DP)
        return max(1, min(grid.width / baseColumns, max(minimumCellSize, portraitEquivalentGridWidth / baseColumns)))
    }

    private fun displayColumnCount(grid: GridLayout, cellSize: Int, baseColumns: Int): Int {
        return (grid.width / max(1, cellSize))
            .coerceAtLeast(baseColumns)
            .coerceAtMost(MAX_GRID_COLUMNS)
    }

    private fun displayRowCount(displayRecords: List<WidgetRecord>): Int =
        max(1, displayRecords.maxOfOrNull { it.row + it.rowSpan } ?: 0)

    private fun openSurface() {
        if (surfaceOpen) {
            return
        }
        surfaceOpen = true
        onSurfaceOpen()
    }

    private fun closeSurface() {
        if (!surfaceOpen) {
            return
        }
        surfaceOpen = false
        onSurfaceClose()
    }

    private fun widgetSpanBounds(
        providerInfo: AppWidgetProviderInfo,
        cellSize: Int = lastRenderedCellSize.takeIf { it > 0 }
            ?: Tuils.dpToPx(context, MIN_WIDGET_CELL_DP),
        cellMargin: Int = Tuils.dpToPx(context, 4)
    ): WidgetSpanBounds {
        val supportsModernSizing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return calculateWidgetSpanBounds(
            WidgetSizeSpec(
                minWidth = providerInfo.minWidth,
                minHeight = providerInfo.minHeight,
                minResizeWidth = providerInfo.minResizeWidth,
                minResizeHeight = providerInfo.minResizeHeight,
                maxResizeWidth = if (supportsModernSizing) providerInfo.maxResizeWidth else 0,
                maxResizeHeight = if (supportsModernSizing) providerInfo.maxResizeHeight else 0,
                targetColumns = if (supportsModernSizing) providerInfo.targetCellWidth else 0,
                targetRows = if (supportsModernSizing) providerInfo.targetCellHeight else 0,
                horizontalResizable = providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                verticalResizable = providerInfo.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
            ),
            cellSize,
            cellMargin + Tuils.dpToPx(context, 8),
            min(baseColumnCount(), MAX_WIDGET_COLUMNS),
            minWidgetColumns(),
            minWidgetRows(),
            MAX_PLACEMENT_SCAN_ROWS
        )
    }

    private fun constrainRecordToProvider(
        record: WidgetRecord,
        cellSize: Int = lastRenderedCellSize.takeIf { it > 0 }
            ?: Tuils.dpToPx(context, MIN_WIDGET_CELL_DP),
        cellMargin: Int = Tuils.dpToPx(context, 4)
    ): WidgetRecord {
        val providerInfo = appWidgetManager.getAppWidgetInfo(record.appWidgetId) ?: return record
        val span = widgetSpanBounds(providerInfo, cellSize, cellMargin)
            .constrain(record.colSpan, record.rowSpan)
        return record.copy(
            col = max(0, record.col),
            colSpan = span.colSpan,
            rowSpan = span.rowSpan
        )
    }

    private fun nextOpenPosition(records: List<WidgetRecord>, colSpan: Int, rowSpan: Int,
        startRow: Int = 0, columnCount: Int = baseColumnCount()) =
        AndroidWidgetLayout.nextOpenPosition(records, colSpan, rowSpan, startRow, columnCount)

    private fun List<WidgetRecord>.filterValid(): List<WidgetRecord> {
        return filter { appWidgetManager.getAppWidgetInfo(it.appWidgetId) != null }
    }

    private fun loadAllocatedWidgetIds(): Set<Int> {
        val raw = preferences.getString(KEY_ALLOCATED_WIDGET_IDS, "[]") ?: "[]"
        val ids = LinkedHashSet<Int>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val appWidgetId = array.optInt(i, INVALID_WIDGET_ID)
                if (appWidgetId != INVALID_WIDGET_ID) {
                    ids.add(appWidgetId)
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
        return ids
    }

    private fun saveAllocatedWidgetIds(appWidgetIds: Set<Int>) {
        val array = JSONArray()
        appWidgetIds.sorted().forEach { array.put(it) }
        preferences.edit().putString(KEY_ALLOCATED_WIDGET_IDS, array.toString()).apply()
    }

    private fun trackAllocatedWidgetId(appWidgetId: Int) {
        if (appWidgetId == INVALID_WIDGET_ID) {
            return
        }

        val ids = loadAllocatedWidgetIds().toMutableSet()
        ids.add(appWidgetId)
        saveAllocatedWidgetIds(ids)
    }

    private fun untrackAllocatedWidgetId(appWidgetId: Int) {
        if (appWidgetId == INVALID_WIDGET_ID) {
            return
        }

        val ids = loadAllocatedWidgetIds().toMutableSet()
        ids.remove(appWidgetId)
        saveAllocatedWidgetIds(ids)
    }

    private fun normalizeWidgetCodes(records: List<WidgetRecord>): List<WidgetRecord> {
        val usedCodes = LinkedHashSet<String>()
        return records.map { record ->
            val existingCode = sanitizeWidgetCode(record.wid)
            val code = if (existingCode != null && usedCodes.add(existingCode)) {
                existingCode
            } else {
                nextWidgetCode(usedCodes).also { usedCodes.add(it) }
            }
            record.copy(wid = code)
        }
    }

    private fun nextWidgetCode(records: List<WidgetRecord>): String {
        return nextWidgetCode(records.mapNotNull { sanitizeWidgetCode(it.wid) }.toSet())
    }

    private fun nextWidgetCode(usedCodes: Set<String>): String {
        var nextValue = 1
        while (true) {
            val candidate = widgetCodeForValue(nextValue)
            if (candidate !in usedCodes) {
                return candidate
            }
            nextValue++
        }
    }

    private fun sanitizeWidgetCode(rawCode: String?): String? {
        val digits = rawCode.orEmpty().filter { it.isDigit() }
        val value = digits.toIntOrNull() ?: return null
        if (value <= 0) {
            return null
        }
        return widgetCodeForValue(value)
    }

    private fun widgetCodeForValue(value: Int): String {
        return String.format(Locale.US, "%02d", value)
    }

    private fun widgetCodeSortValue(code: String): Int {
        return sanitizeWidgetCode(code)?.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun widgetIdBadgeText(record: WidgetRecord): String {
        return "[W_ID:${record.wid}]"
    }

    private fun loadRecords(): List<WidgetRecord> {
        val raw = preferences.getString(KEY_WIDGETS, "[]") ?: "[]"
        recordsReadable = true
        val records = ArrayList<WidgetRecord>()
        val baseColumns = baseColumnCount()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val appWidgetId = obj.optInt("appWidgetId", INVALID_WIDGET_ID)
                if (appWidgetId == INVALID_WIDGET_ID) {
                    continue
                }
                records.add(
                    WidgetRecord(
                        sanitizeWidgetCode(obj.optString("wid")) ?: Tuils.EMPTYSTRING,
                        appWidgetId,
                        obj.optString("provider"),
                        obj.optInt("col", 0).coerceIn(0, MAX_GRID_COLUMNS - 1),
                        max(0, obj.optInt("row", 0)),
                        obj.optInt("colSpan", 2).coerceIn(1, baseColumns),
                        max(1, obj.optInt("rowSpan", 2))
                    )
                )
            }
        } catch (e: Exception) {
            recordsReadable = false
            Tuils.log(e)
        }
        return normalizeWidgetCodes(records)
    }

    private fun saveRecords(records: List<WidgetRecord>) {
        val array = JSONArray()
        for (record in records) {
            val obj = JSONObject()
            obj.put("wid", record.wid)
            obj.put("appWidgetId", record.appWidgetId)
            obj.put("provider", record.provider)
            obj.put("col", record.col)
            obj.put("row", record.row)
            obj.put("colSpan", record.colSpan)
            obj.put("rowSpan", record.rowSpan)
            array.put(obj)
        }
        preferences.edit().putString(KEY_WIDGETS, array.toString()).apply()
    }

    private data class WidgetProviderOption(
        val providerInfo: AppWidgetProviderInfo,
        val appLabel: String,
        val widgetLabel: String,
        val preview: Drawable?
    )

    private data class WidgetCommandSuggestion(
        val label: String,
        val inputText: String,
        val executeOnTap: Boolean = false
    )

    private inner class RetuiAppWidgetHost(context: Context, hostId: Int) :
        AppWidgetHost(context, hostId) {

        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo
        ): AppWidgetHostView {
            return RetuiAppWidgetHostView(context, appWidgetId, appWidget)
        }
    }

    private inner class WidgetFrame(
        context: Context,
        private val appWidgetId: Int
    ) : FrameLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var downRawX = 0f
        private var downRawY = 0f
        private var dragMoved = false
        private var longPressTriggered = false
        private var resizeGesture = false
        private val editRunnable = Runnable {
            val cancel = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            longPressTriggered = true
            editMode = true
            dragMoved = false
            editingWidgetId = appWidgetId
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            parent?.requestDisallowInterceptTouchEvent(true)
            bringToFront()
            alpha = 0.82f
        }

        init {
            isLongClickable = true
        }

        fun matches(widgetId: Int): Boolean = appWidgetId == widgetId

        override fun onDetachedFromWindow() {
            if (resizePreviewFrame === this) finishResizePreview(false)
            removeCallbacks(editRunnable)
            super.onDetachedFromWindow()
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                resizeGesture = editingWidgetId == appWidgetId && (1 until childCount).any { index ->
                    val child = getChildAt(index)
                    child.tag?.toString()?.startsWith("widget-resize-") == true &&
                        event.x >= child.left && event.x < child.right && event.y >= child.top && event.y < child.bottom
                }
                if (resizeGesture) { removeCallbacks(editRunnable); longPressTriggered = false }
            }
            if (resizeGesture) {
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) resizeGesture = false
                return handled
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragMoved = false
                    longPressTriggered = false
                    removeCallbacks(editRunnable)
                    if (editMode) {
                        editingWidgetId = appWidgetId
                        longPressTriggered = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        alpha = .82f
                        bringToFront()
                        return true
                    }
                    postDelayed(editRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longPressTriggered) {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                            dragMoved = true
                            translationX = deltaX
                            translationY = deltaY
                        }
                        return true
                    }

                    if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        removeCallbacks(editRunnable)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(editRunnable)
                    if (longPressTriggered) {
                        val deltaX = event.rawX - downRawX
                        val deltaY = event.rawY - downRawY
                        alpha = 1f
                        translationX = 0f
                        translationY = 0f
                        parent?.requestDisallowInterceptTouchEvent(false)
                        if (event.actionMasked == MotionEvent.ACTION_UP && dragMoved) {
                            moveWidgetByDrag(appWidgetId, deltaX, deltaY)
                        } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                            editingWidgetId = appWidgetId
                            renderWidgets()
                        }
                        return true
                    }
                }
            }

            return if (longPressTriggered) true else super.dispatchTouchEvent(event)
        }
    }

    private inner class RetuiAppWidgetHostView(
        context: Context,
        private val appWidgetId: Int,
        private val providerInfo: AppWidgetProviderInfo
    ) : AppWidgetHostView(context) {

        override fun getErrorView(): View {
            val label = providerInfo.loadLabel(context.packageManager)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: providerInfo.provider.packageName
            return buildWidgetFailureView(appWidgetId, label)
        }
    }

    companion object {
        const val REQUEST_BIND_WIDGET = 7303
        const val REQUEST_CONFIGURE_WIDGET = 7302
        private const val HOST_ID = 0x5744
        private const val PREFS = "android_widget_drawer"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_ALLOCATED_WIDGET_IDS = "allocated_widget_ids"
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"
        private const val KEY_PENDING_WIDGET_STARTED_AT = "pending_widget_started_at"
        private const val INVALID_WIDGET_ID = -1
        private const val MIN_GRID_COLUMNS = 2
        private const val MAX_GRID_COLUMNS = 12
        private const val MAX_WIDGET_COLUMNS = 4
        private const val MAX_MIN_WIDGET_SPAN = 2
        private const val MIN_WIDGET_CELL_DP = 72
        private const val MAX_COMMAND_SPAN_SUGGESTION = 8
        private const val MAX_PLACEMENT_SCAN_ROWS = 120
        private const val PENDING_WIDGET_TIMEOUT_MS = 10 * 60 * 1000L
    }
}

internal data class WidgetSizeSpec(
    val minWidth: Int,
    val minHeight: Int,
    val minResizeWidth: Int,
    val minResizeHeight: Int,
    val maxResizeWidth: Int,
    val maxResizeHeight: Int,
    val targetColumns: Int,
    val targetRows: Int,
    val horizontalResizable: Boolean,
    val verticalResizable: Boolean
)

internal data class WidgetSpan(val colSpan: Int, val rowSpan: Int)

internal data class WidgetSpanBounds(
    val initial: WidgetSpan,
    val minColumns: Int,
    val maxColumns: Int,
    val minRows: Int,
    val maxRows: Int
) {
    fun constrain(columns: Int, rows: Int) = WidgetSpan(
        columns.coerceIn(minColumns, maxColumns),
        rows.coerceIn(minRows, maxRows)
    )
}

internal fun calculateWidgetSpanBounds(
    spec: WidgetSizeSpec,
    cellSize: Int,
    cellMargin: Int,
    maxColumns: Int,
    fallbackColumns: Int,
    fallbackRows: Int,
    maxRows: Int
): WidgetSpanBounds {
    val safeCellSize = max(1, cellSize)
    val safeMargin = max(0, cellMargin)

    fun minimumSpan(size: Int, fallback: Int, limit: Int): Int = if (size > 0) {
        ceil((size.toDouble() + safeMargin * 2.0) / safeCellSize).toInt().coerceIn(1, limit)
    } else {
        fallback.coerceIn(1, limit)
    }

    fun maximumSpan(size: Int, limit: Int): Int = if (size > 0) {
        kotlin.math.floor((size.toDouble() + safeMargin * 2.0) / safeCellSize).toInt().coerceIn(1, limit)
    } else limit

    fun resizeMinimum(resizeValue: Int, defaultValue: Int, resizable: Boolean): Int =
        resizeValue.takeIf { resizable && it > 0 && (defaultValue <= 0 || it <= defaultValue) }
            ?: defaultValue

    val defaultColumns = (spec.targetColumns.takeIf { it > 0 }
        ?: minimumSpan(spec.minWidth, fallbackColumns, maxColumns)).coerceIn(1, maxColumns)
    val defaultRows = (spec.targetRows.takeIf { it > 0 }
        ?: minimumSpan(spec.minHeight, fallbackRows, maxRows)).coerceIn(1, maxRows)

    val minColumns = if (spec.horizontalResizable) {
        minimumSpan(
            resizeMinimum(spec.minResizeWidth, spec.minWidth, true),
            fallbackColumns,
            maxColumns
        )
    } else defaultColumns
    val maxSupportedColumns = if (spec.horizontalResizable) maximumSpan(spec.maxResizeWidth, maxColumns).coerceAtLeast(minColumns) else defaultColumns

    val minRows = if (spec.verticalResizable) {
        minimumSpan(
            resizeMinimum(spec.minResizeHeight, spec.minHeight, true),
            fallbackRows,
            maxRows
        )
    } else defaultRows
    val maxSupportedRows = if (spec.verticalResizable) maximumSpan(spec.maxResizeHeight, maxRows).coerceAtLeast(minRows) else defaultRows

    return WidgetSpanBounds(
        WidgetSpan(
            defaultColumns.coerceIn(minColumns, maxSupportedColumns),
            defaultRows.coerceIn(minRows, maxSupportedRows)
        ),
        minColumns,
        maxSupportedColumns,
        minRows,
        maxSupportedRows
    )
}
