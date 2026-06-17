package ohi.andre.consolelauncher.managers.ui

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
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
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.cyberdeckMode
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalHeaderTabBackground
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalWindowBackground
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
    private val drawerContainerBaseMargins = IntArray(4)
    private val drawerScroll: ScrollView? = rootView.findViewById(R.id.android_widget_drawer_scroll)
    private val widgetGrid: GridLayout? = rootView.findViewById(R.id.android_widget_grid)
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
    private var cachedWidgetProviderOptions: List<WidgetProviderOption>? = null
    private var pickerLoadGeneration = 0
    private var editingWidgetId = INVALID_WIDGET_ID
    private var lastRenderedCellSize = 0
    private var listening = false
    private var surfaceOpen = false

    init {
        OverlayLayoutManager.captureBaseMargins(drawerContainer, drawerContainerBaseMargins)
        configureInputAnchor()
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
        configureInputAnchor()
        cleanupWidgetState()
        startListening()
        styleChrome()
        drawerRoot.visibility = View.VISIBLE
        drawerRoot.post { renderWidgets() }
    }

    fun hide() {
        hideWidgetPicker()
        editingWidgetId = INVALID_WIDGET_ID
        drawerRoot?.visibility = View.GONE
        closeSurface()
    }

    fun applyDisplayMargins(left: Int, top: Int, right: Int, bottom: Int) {
        OverlayLayoutManager.applyMarginsWithBase(
            drawerContainer,
            drawerContainerBaseMargins,
            left,
            top,
            right,
            bottom
        )
        configureInputAnchor()
        if (isOpen) {
            drawerContainer?.post { renderWidgets() }
        }
    }

    fun refreshGrid() {
        if (isOpen) {
            widgetGrid?.post { renderWidgets() }
        }
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
        return true
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
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK_WIDGET
            && requestCode != REQUEST_BIND_WIDGET
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

        if (requestCode == REQUEST_PICK_WIDGET) {
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (providerInfo?.configure != null) {
                configureWidget(appWidgetId, providerInfo)
            } else {
                finishAddWidget(appWidgetId)
            }
            return true
        }

        if (requestCode == REQUEST_BIND_WIDGET) {
            val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
            if (providerInfo?.configure != null) {
                configureWidget(appWidgetId, providerInfo)
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
            else -> showWidgetCommandStatus("Unknown widget command: ${parts.firstOrNull().orEmpty()}")
        }
        updateWidgetCommandSuggestions()
    }

    private fun executeAddWidgetCommand() {
        commandInput?.clearFocus()
        closeKeyboard()
        showWidgetPicker()
        showWidgetCommandStatus("Pick a widget.")
    }

    private fun executeShowWidgetCommand(args: List<String>) {
        val records = widgetCommandRecords()
        if (records.isEmpty()) {
            showWidgetCommandStatus("No widgets in pane.")
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
            showWidgetCommandStatus("No widget for [W_ID:${requestedId.uppercase(Locale.US)}].")
            updateWidgetCommandSuggestions("show ")
            return
        }

        showWidgetCommandStatus(widgetCommandSummary(record), Toast.LENGTH_LONG)
    }

    private fun executeResizeWidgetCommand(parts: List<String>) {
        if (parts.size < 4) {
            showWidgetCommandStatus("Usage: resize <W_ID> <height> <width>")
            updateWidgetCommandSuggestions("resize ")
            return
        }

        val records = loadRecords().filterValid()
        val record = findWidgetRecordByCode(records, parts[1])
        if (record == null) {
            showWidgetCommandStatus("No widget for [W_ID:${parts[1].uppercase(Locale.US)}].")
            updateWidgetCommandSuggestions("resize ")
            return
        }

        val requestedRows = parts[2].toIntOrNull()
        val requestedColumns = parts[3].toIntOrNull()
        if (requestedRows == null || requestedColumns == null) {
            showWidgetCommandStatus("Usage: resize <W_ID> <height> <width>")
            updateWidgetCommandSuggestions("resize ${record.wid} ")
            return
        }

        val baseColumns = baseColumnCount()
        val resizedRecord = normalizeRecord(
            record.copy(
                rowSpan = requestedRows.coerceAtLeast(1),
                colSpan = requestedColumns.coerceIn(1, baseColumns)
            ),
            baseColumns
        )
        saveRecords(compactRecordsWithLocked(resizedRecord, records))
        editingWidgetId = resizedRecord.appWidgetId
        renderWidgets()
        showWidgetCommandStatus(
            "${widgetIdBadgeText(resizedRecord)} resized to ${resizedRecord.rowSpan} x ${resizedRecord.colSpan}"
        )
    }

    private fun executeRemoveWidgetCommand(parts: List<String>) {
        if (parts.size < 2) {
            showWidgetCommandStatus("Usage: remove <W_ID>")
            updateWidgetCommandSuggestions("remove ")
            return
        }

        val records = loadRecords().filterValid()
        val record = findWidgetRecordByCode(records, parts[1])
        if (record == null) {
            showWidgetCommandStatus("No widget for [W_ID:${parts[1].uppercase(Locale.US)}].")
            updateWidgetCommandSuggestions("remove ")
            return
        }

        val removedLabel = widgetIdBadgeText(record)
        editingWidgetId = INVALID_WIDGET_ID
        removeWidget(record)
        showWidgetCommandStatus("$removedLabel removed")
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
                    suggestions.add(WidgetCommandSuggestion("no widgets", "show "))
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
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    marginEnd = Tuils.dpToPx(context, 6)
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
            suggestions.add(WidgetCommandSuggestion("no widgets", "resize "))
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

        val typedHeight = parts.getOrNull(2)?.toIntOrNull()
        if (typedHeight == null) {
            for (height in 1..MAX_COMMAND_SPAN_SUGGESTION) {
                suggestions.add(
                    WidgetCommandSuggestion(
                        "h:$height",
                        "resize ${targetRecord.wid} $height "
                    )
                )
            }
            return
        }

        for (width in 1..baseColumnCount()) {
            suggestions.add(
                WidgetCommandSuggestion(
                    "w:$width",
                    "resize ${targetRecord.wid} $typedHeight $width"
                )
            )
        }
    }

    private fun buildRemoveSuggestions(suggestions: MutableList<WidgetCommandSuggestion>) {
        val records = widgetCommandRecords()
        if (records.isEmpty()) {
            suggestions.add(WidgetCommandSuggestion("no widgets", "remove "))
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
        return TextView(context).apply {
            text = suggestion.label
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minWidth = Tuils.dpToPx(context, 62)
            setPadding(
                Tuils.dpToPx(context, 10),
                0,
                Tuils.dpToPx(context, 10),
                0
            )
            setTextColor(appSuggestionTextColor())
            textSize = 11f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
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
        if (cyberdeckMode()) {
            return TerminalBorderRuntime.panelDrawable(
                context,
                backgroundColor,
                terminalBorderColor(),
                1.0f,
                0,
                true
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
        return "${widgetIdBadgeText(record)} ${record.rowSpan}x${record.colSpan} ${widgetLabel(record)}"
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
        val root = drawerRoot as? ViewGroup ?: return
        hideWidgetPicker()
        cleanupWidgetState()

        val pickerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
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
                terminalWindowBackground(),
                terminalBorderColor(),
                1.5f,
                moduleCornerRadius(),
                dashedBorders()
            )
        }

        val headerRow = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        headerRow.addView(
            TextView(context).apply {
                text = "Widget picker"
                setTextColor(pickerColor)
                textSize = 14f
                setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(
            pickerButton("X").apply {
                contentDescription = "Close widget picker"
                setOnClickListener { hideWidgetPicker() }
            },
            LinearLayout.LayoutParams(
                Tuils.dpToPx(context, 40),
                Tuils.dpToPx(context, 34)
            )
        )
        panel.addView(headerRow)

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
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        pickerOverlay = overlay
        val pickerGeneration = ++pickerLoadGeneration
        populateWidgetPickerAsync(content, pickerGeneration)
    }

    private fun hideWidgetPicker() {
        val overlay = pickerOverlay ?: return
        pickerLoadGeneration++
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        pickerOverlay = null
    }

    private fun populateWidgetPickerAsync(content: LinearLayout, pickerGeneration: Int) {
        val cachedProviders = cachedWidgetProviderOptions
        content.removeAllViews()
        if (cachedProviders != null) {
            populateWidgetPicker(content, cachedProviders)
            return
        }

        content.addView(pickerStatusText("Loading widgets..."))
        Thread {
            val providers = loadWidgetProviderOptions()
            cachedWidgetProviderOptions = providers
            activity.runOnUiThread {
                if (pickerGeneration != pickerLoadGeneration
                    || pickerOverlay == null
                    || content.parent == null
                ) {
                    return@runOnUiThread
                }
                content.removeAllViews()
                populateWidgetPicker(content, providers)
            }
        }.start()
    }

    private fun populateWidgetPicker(content: LinearLayout, providers: List<WidgetProviderOption>) {
        if (providers.isEmpty()) {
            content.addView(
                pickerStatusText("No widgets found."),
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
            setTextColor(XMLPrefsManager.getColor(Theme.apps_drawer_text_color))
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
        val textColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val mutedTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()
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
                terminalBorderColor(),
                1.0f,
                moduleCornerRadius(),
                dashedBorders()
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
                text = "${max(1, option.providerInfo.minWidth)} x ${max(1, option.providerInfo.minHeight)}"
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
            setTextColor(XMLPrefsManager.getColor(Theme.apps_drawer_text_color))
            textSize = 11f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            setPadding(0, Tuils.dpToPx(context, 12), 0, Tuils.dpToPx(context, 4))
        }
    }

    private fun pickerButton(label: String): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(XMLPrefsManager.getColor(Theme.apps_drawer_text_color))
            textSize = 14f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            background = TerminalBorderRuntime.tabDrawable(context, terminalHeaderTabBackground())
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
            Toast.makeText(context, "Couldn't bind widget.", Toast.LENGTH_SHORT).show()
            Tuils.log(e)
        }
    }

    private fun finishBoundWidget(
        appWidgetId: Int,
        fallbackProviderInfo: AppWidgetProviderInfo
    ) {
        val providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId) ?: fallbackProviderInfo
        if (providerInfo.configure != null) {
            configureWidget(appWidgetId, providerInfo)
        } else {
            finishAddWidget(appWidgetId)
        }
    }

    private fun pickWidget() {
        cleanupWidgetState()
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        trackAllocatedWidgetId(appWidgetId)
        preferences.edit()
            .putInt(KEY_PENDING_WIDGET_ID, appWidgetId)
            .putLong(KEY_PENDING_WIDGET_STARTED_AT, System.currentTimeMillis())
            .apply()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        try {
            activity.startActivityForResult(intent, REQUEST_PICK_WIDGET)
        } catch (e: Exception) {
            deletePendingWidget(appWidgetId)
            Toast.makeText(context, "Widget picker unavailable.", Toast.LENGTH_SHORT).show()
            Tuils.log(e)
        }
    }

    private fun configureWidget(appWidgetId: Int, providerInfo: AppWidgetProviderInfo) {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        intent.component = providerInfo.configure
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        try {
            activity.startActivityForResult(intent, REQUEST_CONFIGURE_WIDGET)
        } catch (e: Exception) {
            finishAddWidget(appWidgetId)
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
        val span = spanFor(providerInfo, baseColumns)
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
            deleteWidgetId(appWidgetId)
            untrackAllocatedWidgetId(appWidgetId)
        }
    }

    private fun removeWidget(record: WidgetRecord) {
        deleteWidgetId(record.appWidgetId)
        untrackAllocatedWidgetId(record.appWidgetId)
        saveRecords(loadRecords().filter { it.appWidgetId != record.appWidgetId })
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
        if (grid.width <= 0) {
            grid.post { renderWidgets() }
            return
        }

        grid.removeAllViews()
        val records = loadRecords()
        val validRecords = cleanupWidgetState(records)
        val pendingWidgetId = activePendingWidgetId()
        if (validRecords != records) {
            saveRecords(validRecords)
        }
        if (validRecords.isEmpty() && pendingWidgetId == INVALID_WIDGET_ID) {
            deleteHostIfIdle()
        }

        val baseColumns = baseColumnCount()
        val cellSize = uniformCellSize(grid, baseColumns)
        lastRenderedCellSize = cellSize
        val displayColumns = displayColumnCount(grid, cellSize, baseColumns)
        val displayRecords = layoutRecordsForColumns(validRecords, displayColumns)
        val displayRows = displayRowCount(displayRecords, cellSize)
        grid.columnCount = displayColumns
        grid.rowCount = displayRows
        grid.minimumHeight = displayRows * cellSize

        val cellMargin = Tuils.dpToPx(context, 4)
        for (record in displayRecords) {
            val providerInfo = appWidgetManager.getAppWidgetInfo(record.appWidgetId) ?: continue
            val widgetWidth = max(1, (cellSize * record.colSpan) - (cellMargin * 2))
            val widgetHeight = max(1, (cellSize * record.rowSpan) - (cellMargin * 2))
            val sizeOptions = widgetSizeOptions(widgetWidth, widgetHeight)
            updateWidgetOptions(record.appWidgetId, sizeOptions)
            val hostView = appWidgetHost.createView(widgetHostContext, record.appWidgetId, providerInfo)
            updateHostWidgetSize(hostView, sizeOptions, widgetWidth, widgetHeight)

            val frame = WidgetFrame(context, record.appWidgetId)
            frame.background = TerminalBorderRuntime.panelDrawable(
                context,
                Color.TRANSPARENT,
                terminalBorderColor(),
                1.0f,
                moduleCornerRadius(),
                dashedBorders()
            )
            frame.setPadding(0, 0, 0, 0)
            frame.addView(
                hostView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val failureOverlay = buildWidgetFailureView(
                record.appWidgetId,
                providerInfo.loadLabel(context.packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() }
                    ?: providerInfo.provider.packageName
            ).apply {
                visibility = View.GONE
            }
            frame.addView(
                failureOverlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            frame.addView(
                widgetIdBadge(record),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(
                        Tuils.dpToPx(context, 8),
                        Tuils.dpToPx(context, 2),
                        0,
                        0
                    )
                }
            )
            frame.postDelayed({
                if (containsAndroidWidgetFailure(hostView)) {
                    failureOverlay.visibility = View.VISIBLE
                }
            }, WIDGET_FAILURE_SCAN_DELAY_MS)
            if (editingWidgetId == record.appWidgetId) {
                addEditControls(frame, record)
            }

            val params = GridLayout.LayoutParams(
                GridLayout.spec(record.row, record.rowSpan),
                GridLayout.spec(record.col, record.colSpan)
            )
            params.width = widgetWidth
            params.height = widgetHeight
            params.setMargins(cellMargin, cellMargin, cellMargin, cellMargin)
            frame.layoutParams = params
            grid.addView(frame)
        }

        updateLabels(validRecords.size, displayColumns, displayRows)
        updateWidgetCommandSuggestions()
    }

    private fun widgetIdBadge(record: WidgetRecord): TextView {
        return TextView(context).apply {
            text = widgetIdBadgeText(record)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(XMLPrefsManager.getColor(Theme.apps_drawer_text_color))
            textSize = 9f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            setPadding(
                Tuils.dpToPx(context, 7),
                Tuils.dpToPx(context, 2),
                Tuils.dpToPx(context, 7),
                Tuils.dpToPx(context, 2)
            )
            background = TerminalBorderRuntime.tabDrawable(context, terminalHeaderTabBackground())
        }
    }

    private fun addEditControls(frame: FrameLayout, record: WidgetRecord) {
        val controlSize = Tuils.dpToPx(context, 30)
        val controlGap = Tuils.dpToPx(context, 4)

        frame.addView(
            resizeControl(
                "↑",
                "Drag to resize the top edge.",
                record,
                ResizeEdge.TOP
            ),
            FrameLayout.LayoutParams(
                controlSize,
                controlSize,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply {
                setMargins(0, controlGap, 0, 0)
            }
        )
        frame.addView(
            resizeControl(
                "↓",
                "Drag to resize the bottom edge.",
                record,
                ResizeEdge.BOTTOM
            ),
            FrameLayout.LayoutParams(
                controlSize,
                controlSize,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            )
        )
        frame.addView(
            resizeControl(
                "←",
                "Drag to resize the left edge.",
                record,
                ResizeEdge.LEFT
            ),
            FrameLayout.LayoutParams(
                controlSize,
                controlSize,
                Gravity.START or Gravity.CENTER_VERTICAL
            )
        )
        frame.addView(
            resizeControl(
                "→",
                "Drag to resize the right edge.",
                record,
                ResizeEdge.RIGHT
            ),
            FrameLayout.LayoutParams(
                controlSize,
                controlSize,
                Gravity.END or Gravity.CENTER_VERTICAL
            )
        )

        val topActions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        topActions.addView(
            editControl("X", "Cancel widget edit", {
                editingWidgetId = INVALID_WIDGET_ID
                renderWidgets()
            }),
            LinearLayout.LayoutParams(
                controlSize,
                controlSize
            )
        )
        frame.addView(
            topActions,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
        )
    }

    private fun resizeControl(
        label: String,
        description: String,
        record: WidgetRecord,
        edge: ResizeEdge
    ): TextView {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var startRawX = 0f
        var startRawY = 0f
        var moved = false

        return baseEditControl(label, description).apply {
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        moved = false
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        view.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(event.rawX - startRawX) > touchSlop
                            || abs(event.rawY - startRawY) > touchSlop
                        ) {
                            moved = true
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        view.isPressed = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        if (moved) {
                            val deltaCells = dragDeltaCells(edge, event.rawX - startRawX, event.rawY - startRawY)
                            if (deltaCells != 0) {
                                resizeWidgetFromDrag(record, edge, deltaCells)
                            }
                        } else {
                            view.performClick()
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun editControl(
        label: String,
        description: String,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)? = null
    ): TextView {
        return baseEditControl(label, description).apply {
            setOnClickListener { onClick() }
            if (onLongClick != null) {
                setOnLongClickListener {
                    onLongClick()
                    true
                }
            }
        }
    }

    private fun baseEditControl(label: String, description: String): TextView {
        return TextView(context).apply {
            text = label
            contentDescription = description
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(XMLPrefsManager.getColor(Theme.apps_drawer_text_color))
            textSize = if (label.length > 1) 10f else 16f
            setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            background = TerminalBorderRuntime.tabDrawable(context, terminalHeaderTabBackground())
        }
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

    private fun resizeWidgetFromDrag(record: WidgetRecord, edge: ResizeEdge, deltaCells: Int) {
        val records = loadRecords().filterValid()
        val currentRecord = records.firstOrNull { it.appWidgetId == record.appWidgetId } ?: return
        val otherRecords = records.filter { it.appWidgetId != currentRecord.appWidgetId }
        val updatedRecord = when (edge) {
            ResizeEdge.LEFT,
            ResizeEdge.RIGHT -> resizeHorizontalEdgeByCells(currentRecord, otherRecords, edge, deltaCells)
            ResizeEdge.TOP,
            ResizeEdge.BOTTOM -> resizeVerticalEdgeByCells(currentRecord, edge, deltaCells)
        }

        if (updatedRecord.col == currentRecord.col
            && updatedRecord.row == currentRecord.row
            && updatedRecord.colSpan == currentRecord.colSpan
            && updatedRecord.rowSpan == currentRecord.rowSpan
        ) {
            return
        }

        val updatedRecords = compactRecordsWithLocked(updatedRecord, records)
        saveRecords(updatedRecords)
        editingWidgetId = updatedRecord.appWidgetId
        renderWidgets()
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
        val baseColumns = baseColumnCount()
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
        otherRecords: List<WidgetRecord>,
        edge: ResizeEdge,
        deltaCells: Int
    ): WidgetRecord {
        val baseColumns = baseColumnCount()
        val minimumColumns = minWidgetColumns(baseColumns)
        return if (deltaCells > 0) {
            expandHorizontalEdge(record, otherRecords, edge, baseColumns, deltaCells)
        } else {
            shrinkHorizontalEdge(record, edge, minimumColumns, -deltaCells)
        }
    }

    private fun resizeVerticalEdgeByCells(
        record: WidgetRecord,
        edge: ResizeEdge,
        deltaCells: Int
    ): WidgetRecord {
        val minimumRows = minWidgetRows()
        return if (deltaCells > 0) {
            expandVerticalEdge(record, edge, deltaCells)
        } else {
            shrinkVerticalEdge(record, edge, minimumRows, -deltaCells)
        }
    }

    private fun resizeWidgetFromEdge(record: WidgetRecord, edge: ResizeEdge, mode: ResizeMode) {
        val records = loadRecords().filterValid()
        val currentRecord = records.firstOrNull { it.appWidgetId == record.appWidgetId } ?: return
        val otherRecords = records.filter { it.appWidgetId != currentRecord.appWidgetId }
        val updatedRecord = when (edge) {
            ResizeEdge.LEFT,
            ResizeEdge.RIGHT -> resizeHorizontalEdge(currentRecord, otherRecords, edge, mode)
            ResizeEdge.TOP,
            ResizeEdge.BOTTOM -> resizeVerticalEdge(currentRecord, edge, mode)
        }

        if (updatedRecord.col == currentRecord.col
            && updatedRecord.row == currentRecord.row
            && updatedRecord.colSpan == currentRecord.colSpan
            && updatedRecord.rowSpan == currentRecord.rowSpan
        ) {
            return
        }

        val updatedRecords = compactRecordsWithLocked(updatedRecord, records)

        saveRecords(updatedRecords)
        editingWidgetId = updatedRecord.appWidgetId
        renderWidgets()
    }

    private fun resizeHorizontalEdge(
        record: WidgetRecord,
        otherRecords: List<WidgetRecord>,
        edge: ResizeEdge,
        mode: ResizeMode
    ): WidgetRecord {
        val baseColumns = baseColumnCount()
        val minimumColumns = minWidgetColumns(baseColumns)
        val resizeStep = resizeColumnStep(baseColumns)
        return if (mode == ResizeMode.SHRINK) {
            shrinkHorizontalEdge(record, edge, minimumColumns, resizeStep)
        } else {
            expandHorizontalEdge(record, otherRecords, edge, baseColumns, resizeStep)
        }
    }

    private fun shrinkHorizontalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        minimumColumns: Int,
        resizeStep: Int
    ): WidgetRecord {
        if (record.colSpan > minimumColumns) {
            val shrinkBy = minOf(resizeStep, record.colSpan - minimumColumns)
            val newColSpan = record.colSpan - shrinkBy
            return if (edge == ResizeEdge.RIGHT) {
                record.copy(col = record.col + shrinkBy, colSpan = newColSpan)
            } else {
                record.copy(colSpan = newColSpan)
            }
        }
        return record
    }

    private fun expandHorizontalEdge(
        record: WidgetRecord,
        otherRecords: List<WidgetRecord>,
        edge: ResizeEdge,
        baseColumns: Int,
        resizeStep: Int
    ): WidgetRecord {
        val newColSpan = (record.colSpan + resizeStep).coerceAtMost(baseColumns)
        if (newColSpan == record.colSpan) {
            return record
        }

        if (edge == ResizeEdge.LEFT) {
            val newCol = max(0, record.col - (newColSpan - record.colSpan))
            return record.copy(col = newCol, colSpan = record.col + record.colSpan - newCol)
        }

        val inPlaceRecord = record.copy(colSpan = newColSpan)
        if (record.col + newColSpan <= baseColumns) {
            return inPlaceRecord
        }

        return moveRecordBelow(record, otherRecords, newColSpan, record.rowSpan)
    }

    private fun resizeVerticalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        mode: ResizeMode
    ): WidgetRecord {
        val minimumRows = minWidgetRows()
        val resizeStep = resizeRowStep()
        return if (mode == ResizeMode.SHRINK) {
            shrinkVerticalEdge(record, edge, minimumRows, resizeStep)
        } else {
            expandVerticalEdge(record, edge, resizeStep)
        }
    }

    private fun shrinkVerticalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        minimumRows: Int,
        resizeStep: Int
    ): WidgetRecord {
        if (record.rowSpan > minimumRows) {
            val shrinkBy = minOf(resizeStep, record.rowSpan - minimumRows)
            val newRowSpan = record.rowSpan - shrinkBy
            return if (edge == ResizeEdge.BOTTOM) {
                record.copy(row = record.row + shrinkBy, rowSpan = newRowSpan)
            } else {
                record.copy(rowSpan = newRowSpan)
            }
        }
        return record
    }

    private fun expandVerticalEdge(
        record: WidgetRecord,
        edge: ResizeEdge,
        resizeStep: Int
    ): WidgetRecord {
        if (edge == ResizeEdge.TOP) {
            val newRow = max(0, record.row - resizeStep)
            return record.copy(row = newRow, rowSpan = record.row + record.rowSpan - newRow)
        }

        return record.copy(rowSpan = record.rowSpan + resizeStep)
    }

    private fun moveRecordBelow(
        record: WidgetRecord,
        otherRecords: List<WidgetRecord>,
        colSpan: Int,
        rowSpan: Int
    ): WidgetRecord {
        val position = nextOpenPosition(
            otherRecords,
            colSpan,
            rowSpan,
            record.row + record.rowSpan,
            baseColumnCount()
        )
        return record.copy(
            col = position.col,
            row = position.row,
            colSpan = colSpan,
            rowSpan = rowSpan
        )
    }

    private fun compactRecordsWithLocked(
        lockedRecord: WidgetRecord,
        records: List<WidgetRecord>
    ): List<WidgetRecord> {
        val baseColumns = baseColumnCount()
        val placed = mutableListOf(normalizeRecord(lockedRecord, baseColumns))
        records
            .filter { it.appWidgetId != lockedRecord.appWidgetId }
            .sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
            .forEach { record ->
                val normalizedRecord = normalizeRecord(record, baseColumns)
                val position = nextOpenPosition(
                    placed,
                    normalizedRecord.colSpan,
                    normalizedRecord.rowSpan,
                    columnCount = baseColumns
                )
                val placedRecord = normalizedRecord.copy(col = position.col, row = position.row)
                placed.add(placedRecord)
            }

        return placed.sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
    }

    private fun layoutRecordsForColumns(
        records: List<WidgetRecord>,
        columnCount: Int
    ): List<WidgetRecord> {
        val placed = mutableListOf<WidgetRecord>()
        records
            .sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
            .forEach { record ->
                val normalizedRecord = normalizeRecord(record, columnCount)
                val position = nextOpenPosition(
                    placed,
                    normalizedRecord.colSpan,
                    normalizedRecord.rowSpan,
                    columnCount = columnCount
                )
                placed.add(normalizedRecord.copy(col = position.col, row = position.row))
            }
        return placed.sortedWith(compareBy<WidgetRecord> { it.row }.thenBy { it.col })
    }

    private fun normalizeRecord(record: WidgetRecord, columnCount: Int = baseColumnCount()): WidgetRecord {
        val colSpan = record.colSpan.coerceIn(1, columnCount)
        return record.copy(
            col = record.col.coerceIn(0, columnCount - colSpan),
            row = max(0, record.row),
            colSpan = colSpan,
            rowSpan = max(1, record.rowSpan)
        )
    }

    private fun widgetSizeOptions(widthPx: Int, heightPx: Int): Bundle {
        val widthDp = pxToDp(widthPx)
        val heightDp = pxToDp(heightPx)
        return Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
    }

    private fun updateWidgetOptions(appWidgetId: Int, options: Bundle) {
        try {
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val currentOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val optionsChanged =
                currentOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, -1) != widthDp
                    || currentOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, -1) != maxWidthDp
                    || currentOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, -1) != heightDp
                    || currentOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, -1) != maxHeightDp
            if (optionsChanged) {
                appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
            }
        } catch (e: RuntimeException) {
            Tuils.log(e)
        }

    }

    private fun updateHostWidgetSize(
        hostView: AppWidgetHostView,
        options: Bundle,
        widthPx: Int,
        heightPx: Int
    ) {
        val widthDp = pxToDp(widthPx)
        val heightDp = pxToDp(heightPx)
        try {
            hostView.updateAppWidgetSize(options, widthDp, heightDp, widthDp, heightDp)
        } catch (e: RuntimeException) {
            Tuils.log(e)
        }
    }

    private fun pxToDp(px: Int): Int {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        return max(1, Math.round(px / density))
    }

    private fun buildWidgetFailureView(appWidgetId: Int, label: String): View {
        val textColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val mutedTextColor = (textColor and 0x00FFFFFF) or 0x99000000.toInt()
        val padding = Tuils.dpToPx(context, 10)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            isClickable = true
            isLongClickable = true
            background = TerminalBorderRuntime.panelDrawable(
                context,
                terminalWindowBackground(),
                terminalBorderColor(),
                1.0f,
                moduleCornerRadius(),
                dashedBorders()
            )
            contentDescription = "$label widget failed to render. Tap to remove."
            setOnClickListener { removeWidgetById(appWidgetId) }
            setOnLongClickListener {
                removeWidgetById(appWidgetId)
                true
            }

            addView(errorText("WIDGET FAILED TO RENDER", textColor, 11f, true))
            addView(errorText(label.uppercase(), mutedTextColor, 9f, false))
            addView(errorText("TAP TO REMOVE", textColor, 9f, false))
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

    private fun containsAndroidWidgetFailure(view: View): Boolean {
        if (view is TextView
            && view.text?.toString()?.contains("Couldn't add widget", ignoreCase = true) == true
        ) {
            return true
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (containsAndroidWidgetFailure(view.getChildAt(i))) {
                    return true
                }
            }
        }

        return false
    }

    private fun deleteHostIfIdle() {
        if (activePendingWidgetId() != INVALID_WIDGET_ID) {
            return
        }
        try {
            appWidgetHost.deleteHost()
            saveAllocatedWidgetIds(emptySet())
        } catch (e: RuntimeException) {
            Tuils.log(e)
        }
    }

    private fun cleanupWidgetState(records: List<WidgetRecord> = loadRecords()): List<WidgetRecord> {
        cleanupStalePendingWidget(records)
        val validRecords = records.filterValid()
        cleanupTrackedOrphans(validRecords)
        cleanupLegacyOrphans(validRecords)
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
        val savedIds = records.map { it.appWidgetId }.toSet()
        val pendingId = activePendingWidgetId()
        val retainedIds = savedIds.toMutableSet()
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

    private fun cleanupLegacyOrphans(records: List<WidgetRecord>) {
        if (preferences.getBoolean(KEY_LEGACY_ORPHAN_CLEANUP_DONE, false)) {
            return
        }

        val pendingId = activePendingWidgetId()
        val savedIds = records.map { it.appWidgetId }.toSet()
        val knownIds = savedIds.toMutableSet()
        knownIds.addAll(loadAllocatedWidgetIds())
        if (pendingId != INVALID_WIDGET_ID) {
            knownIds.add(pendingId)
        }
        if (knownIds.isEmpty()) {
            return
        }

        val protectedIds = savedIds.toMutableSet()
        if (pendingId != INVALID_WIDGET_ID) {
            protectedIds.add(pendingId)
        }

        val minId = max(1, knownIds.minOrNull()!! - LEGACY_CLEANUP_SCAN_RADIUS)
        val maxId = knownIds.maxOrNull()!! + LEGACY_CLEANUP_SCAN_RADIUS
        for (appWidgetId in minId..maxId) {
            if (appWidgetId !in protectedIds) {
                deleteWidgetId(appWidgetId, logErrors = false)
            }
        }
        preferences.edit().putBoolean(KEY_LEGACY_ORPHAN_CLEANUP_DONE, true).apply()
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
        val drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val borderColor = terminalBorderColor()
        val backgroundColor = terminalWindowBackground()
        val headerBackgroundColor = terminalHeaderTabBackground()

        drawerRoot?.setBackgroundColor(Color.TRANSPARENT)
        drawerContainer?.background = TerminalBorderRuntime.panelDrawable(
            context,
            backgroundColor,
            borderColor,
            1.5f,
            moduleCornerRadius(),
            dashedBorders()
        )
        header?.setTextColor(drawerColor)
        footer?.setTextColor(drawerColor)
        closeButton?.setTextColor(drawerColor)
        commandPrefix?.setTextColor(drawerColor)
        commandInput?.setTextColor(drawerColor)
        commandInput?.setHintTextColor(ColorUtils.setAlphaComponent(drawerColor, 150))
        commandSend?.setTextColor(drawerColor)
        header?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        footer?.typeface = Tuils.getTypeface(context)
        closeButton?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        commandPrefix?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        commandInput?.typeface = Tuils.getTypeface(context)
        commandSend?.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        header?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor)
        footer?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor)
        closeButton?.background = TerminalBorderRuntime.tabDrawable(context, headerBackgroundColor)
        commandInputGroup?.background = TerminalBorderRuntime.panelDrawable(
            context,
            ColorUtils.blendARGB(backgroundColor, Color.BLACK, 0.16f),
            ColorUtils.setAlphaComponent(borderColor, 180),
            1.2f,
            moduleCornerRadius(),
            dashedBorders()
        )
        commandSend?.background = TerminalBorderRuntime.panelDrawable(
            context,
            Color.TRANSPARENT,
            ColorUtils.setAlphaComponent(borderColor, 190),
            1f,
            moduleCornerRadius(),
            dashedBorders()
        )
        TerminalBorderRuntime.bind(drawerContainer, header, footer)
        updateWidgetCommandSuggestions()
    }

    private fun updateLabels(recordCount: Int, displayColumns: Int, displayRows: Int) {
        header?.text = "Widgets/ [$recordCount]"
        footer?.text = "grid $displayColumns x ${max(1, displayRows)}"
    }

    private fun configureInputAnchor() {
        val dummyAnchor =
            rootView.findViewById<View?>(R.id.android_widget_drawer_dummy_input_anchor) ?: return
        rootView.post {
            val params = dummyAnchor.layoutParams as? RelativeLayout.LayoutParams ?: return@post
            params.height = 0
            params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            dummyAnchor.layoutParams = params
            if (isOpen) {
                dummyAnchor.post { renderWidgets() }
            }
        }
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

    private fun resizeColumnStep(baseColumns: Int): Int {
        return minWidgetColumns(baseColumns).coerceAtMost(RESIZE_COLUMN_STEP)
    }

    private fun resizeRowStep(): Int {
        return minWidgetRows().coerceAtMost(RESIZE_ROW_STEP)
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
        return max(minimumCellSize, portraitEquivalentGridWidth / baseColumns)
    }

    private fun displayColumnCount(grid: GridLayout, cellSize: Int, baseColumns: Int): Int {
        return (grid.width / max(1, cellSize))
            .coerceAtLeast(baseColumns)
            .coerceAtMost(MAX_GRID_COLUMNS)
    }

    private fun displayRowCount(displayRecords: List<WidgetRecord>, cellSize: Int): Int {
        val contentRows = displayRecords.maxOfOrNull { it.row + it.rowSpan } ?: 0
        val viewportRows = ((drawerScroll?.height ?: drawerContainer?.height ?: 0) / max(1, cellSize))
            .coerceAtLeast(0)
        return max(1, max(contentRows, viewportRows))
    }

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

    private fun spanFor(providerInfo: AppWidgetProviderInfo, baseColumns: Int): WidgetSpan {
        val minWidth = max(1, providerInfo.minWidth)
        val minHeight = max(1, providerInfo.minHeight)
        val ratio = minWidth.toFloat() / minHeight.toFloat()
        val minColumns = minWidgetColumns(baseColumns)
        val minRows = minWidgetRows()
        return when {
            ratio >= 1.45f -> WidgetSpan(baseColumns, minRows)
            ratio <= 0.70f -> WidgetSpan(minColumns, max(minRows * 2, minRows))
            else -> WidgetSpan(minColumns, minRows)
        }
    }

    private fun nextOpenPosition(
        records: List<WidgetRecord>,
        colSpan: Int,
        rowSpan: Int,
        startRow: Int = 0,
        columnCount: Int = baseColumnCount()
    ): WidgetPosition {
        val boundedColSpan = colSpan.coerceIn(1, columnCount)
        for (row in max(0, startRow)..MAX_PLACEMENT_SCAN_ROWS) {
            for (col in 0..(columnCount - boundedColSpan)) {
                if (isOpenSlot(records, col, row, boundedColSpan, rowSpan)) {
                    return WidgetPosition(col, row)
                }
            }
        }
        val row = records.maxOfOrNull { it.row + it.rowSpan } ?: 0
        return WidgetPosition(0, row)
    }

    private fun isOpenSlot(
        records: List<WidgetRecord>,
        col: Int,
        row: Int,
        colSpan: Int,
        rowSpan: Int
    ): Boolean {
        val right = col + colSpan
        val bottom = row + rowSpan
        for (record in records) {
            val recordRight = record.col + record.colSpan
            val recordBottom = record.row + record.rowSpan
            val intersects = col < recordRight
                && right > record.col
                && row < recordBottom
                && bottom > record.row
            if (intersects) {
                return false
            }
        }
        return true
    }

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
                        obj.optInt("col", 0).coerceIn(0, baseColumns - 1),
                        max(0, obj.optInt("row", 0)),
                        obj.optInt("colSpan", 2).coerceIn(1, baseColumns),
                        max(1, obj.optInt("rowSpan", 2))
                    )
                )
            }
        } catch (e: Exception) {
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

    private data class WidgetRecord(
        val wid: String,
        val appWidgetId: Int,
        val provider: String,
        val col: Int,
        val row: Int,
        val colSpan: Int,
        val rowSpan: Int
    )

    private data class WidgetSpan(val colSpan: Int, val rowSpan: Int)

    private data class WidgetPosition(val col: Int, val row: Int)

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

    private enum class ResizeEdge {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }

    private enum class ResizeMode {
        EXPAND,
        SHRINK
    }

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
        private val editRunnable = Runnable {
            longPressTriggered = true
            dragMoved = false
            editingWidgetId = appWidgetId
            parent?.requestDisallowInterceptTouchEvent(true)
            bringToFront()
            alpha = 0.82f
        }

        init {
            isLongClickable = true
        }

        fun matches(widgetId: Int): Boolean = appWidgetId == widgetId

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (editingWidgetId != INVALID_WIDGET_ID && editingWidgetId != appWidgetId) {
                        return cancelWidgetEditForOutsideTouch(event.rawX, event.rawY)
                    }
                    downX = event.x
                    downY = event.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragMoved = false
                    longPressTriggered = false
                    removeCallbacks(editRunnable)
                    if (editingWidgetId != appWidgetId) {
                        postDelayed(editRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    }
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
        const val REQUEST_PICK_WIDGET = 7301
        const val REQUEST_BIND_WIDGET = 7303
        const val REQUEST_CONFIGURE_WIDGET = 7302
        private const val HOST_ID = 0x5744
        private const val PREFS = "android_widget_drawer"
        private const val KEY_WIDGETS = "widgets"
        private const val KEY_ALLOCATED_WIDGET_IDS = "allocated_widget_ids"
        private const val KEY_PENDING_WIDGET_ID = "pending_widget_id"
        private const val KEY_PENDING_WIDGET_STARTED_AT = "pending_widget_started_at"
        private const val KEY_LEGACY_ORPHAN_CLEANUP_DONE = "legacy_orphan_cleanup_done"
        private const val INVALID_WIDGET_ID = -1
        private const val MIN_GRID_COLUMNS = 2
        private const val MAX_GRID_COLUMNS = 12
        private const val MAX_MIN_WIDGET_SPAN = 2
        private const val RESIZE_COLUMN_STEP = 2
        private const val RESIZE_ROW_STEP = 2
        private const val MIN_WIDGET_CELL_DP = 72
        private const val MAX_COMMAND_SPAN_SUGGESTION = 8
        private const val MAX_PLACEMENT_SCAN_ROWS = 120
        private const val PENDING_WIDGET_TIMEOUT_MS = 10 * 60 * 1000L
        private const val LEGACY_CLEANUP_SCAN_RADIUS = 8
        private const val WIDGET_FAILURE_SCAN_DELAY_MS = 500L
    }
}
