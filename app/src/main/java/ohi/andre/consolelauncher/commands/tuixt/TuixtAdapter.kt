package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.previewModuleButtonBackground
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleChoice
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleColorPreview
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.get
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.set
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Cmd
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import androidx.annotation.NonNull
import java.util.ArrayList
import java.util.HashMap
import java.util.Map
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver

internal class SectionAccordionState(initial: String? = null) {
    var active: String? = initial
        private set
    var searching = false
        private set

    fun toggle(section: String) { active = if (active == section) null else section }
    fun search(enabled: Boolean) { searching = enabled }
    fun collapsed(section: String): Boolean = !searching && active != section
}

class TuixtAdapter(
    rows: MutableList<SettingsRow>,
    private val file: File?,
    private val onButtonThemePreviewChanged: () -> Unit = {},
    private val onOpenSearchProviders: () -> Unit = {},
    private val accordionSections: Boolean = false,
    initialSection: String? = null
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: MutableList<SettingsRow>
    private val visibleRows: MutableList<SettingsRow> = ArrayList<SettingsRow>()
    private val collapsedSections: MutableSet<String> = HashSet<String>()
    private val accordionState = SectionAccordionState(initialSection)
    private val pendingChanges: MutableMap<XMLPrefsSave?, String?> =
        HashMap<XMLPrefsSave?, String?>()
    private var expandedColorItem: XMLPrefsSave? = null

    init {
        this.rows = ArrayList<SettingsRow>(rows)
        rebuildVisibleRows()
    }

    fun updateRows(newRows: MutableList<SettingsRow>, revealAll: Boolean = false) {
        this.rows = ArrayList<SettingsRow>(newRows)
        accordionState.search(revealAll)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    @JvmOverloads
    fun saveAll(context: Context? = null, recyclerView: RecyclerView? = null) {
        recyclerView?.let { captureVisibleInputs(it) }
        if (rows.any { StatusRowResolver.isStatusIndex(it.item) }) {
            val rawRows = StatusRowResolver.settings.associateWith { pendingChanges[it] ?: get(it) }
            for ((item, value) in StatusRowResolver.normalize(rawRows).values) {
                pendingChanges[item] = value
            }
        }
        for (entry in pendingChanges.entries) {
            val item = entry.key
            val value = entry.value
            set(context, item, value)
        }
        pendingChanges.clear()
    }

    private fun captureVisibleInputs(recyclerView: RecyclerView) {
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index))
            if (holder !is ViewHolder || holder.input.visibility != View.VISIBLE) continue

            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) continue
            val item = visibleRows[position].item ?: continue
            if (item.type() == XMLPrefsSave.COLOR || item.type() == XMLPrefsSave.AUTO_COLOR) continue
            val value = holder.input.text.toString()
            pendingChanges[item] = value
        }
    }

    fun hasPendingChanges(): Boolean {
        return !pendingChanges.isEmpty()
    }

    override fun getItemViewType(position: Int): Int =
        if (visibleRows[position].sectionHeader) VIEW_TYPE_SECTION else VIEW_TYPE_SETTING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_SECTION) {
            val title = TextView(parent.context)
            title.setGravity(Gravity.CENTER_VERTICAL)
            title.setPadding(dp(parent.context, 14f), dp(parent.context, 10f), dp(parent.context, 14f), dp(parent.context, 10f))
            title.setTypeface(Tuils.getTypeface(parent.context), Typeface.BOLD)
            title.setTextColor(accentColor())
            title.setBackground(rect(parent.context, surfaceColor(), borderColor(), 1.25f))
            return SectionHolder(title)
        }

        val view =
            LayoutInflater.from(parent.getContext()).inflate(R.layout.tuixt_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = visibleRows.get(position)
        if (holder is SectionHolder) {
            bindSection(holder, row)
            return
        }

        val settingHolder = holder as ViewHolder
        val item = row.item ?: return
        settingHolder.title.setText(displayLabel(item))
        settingHolder.description.setText(item.info())

        val currentValue = getCurrentValue(item)

        settingHolder.input.removeTextChangedListener(settingHolder.textWatcher)
        settingHolder.toggle.setOnClickListener(null)
        settingHolder.action.setOnClickListener(null)
        settingHolder.action.visibility = View.GONE
        settingHolder.colorPreview.setOnClickListener(null)
        settingHolder.colorPicker.removeAllViews()
        settingHolder.colorPicker.visibility = View.GONE
        settingHolder.options.removeAllViews()
        settingHolder.options.setVisibility(View.GONE)
        settingHolder.itemView.setBackground(
            rect(
                settingHolder.itemView.getContext(),
                surfaceColor(),
                borderColor(),
                1.25f
            )
        )
        settingHolder.title.setTextColor(accentColor())
        settingHolder.description.setTextColor(textColor())
        styleInput(settingHolder.itemView.getContext(), settingHolder.input)

        if (item === Cmd.default_search) {
            settingHolder.toggle.visibility = View.GONE
            settingHolder.colorPreview.visibility = View.GONE
            settingHolder.input.visibility = View.VISIBLE
            settingHolder.input.setText(currentValue)
            settingHolder.action.visibility = View.VISIBLE
            settingHolder.action.text = "PROVIDERS"
            styleButton(settingHolder.itemView.context, settingHolder.action, false)
            settingHolder.action.setOnClickListener { onOpenSearchProviders() }
            settingHolder.textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) { pendingChanges[item] = s.toString() }
            }
            settingHolder.input.addTextChangedListener(settingHolder.textWatcher)
        } else if (item === Behavior.output_tray_mode) {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.GONE)
            settingHolder.input.setVisibility(View.GONE)
            settingHolder.options.setVisibility(View.VISIBLE)
            bindOptionSwitch(settingHolder, item, arrayOf<String>("native", "auto", "toggled"))
        } else if (item === Behavior.output_header_mode) {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.GONE)
            settingHolder.input.setVisibility(View.GONE)
            settingHolder.options.setVisibility(View.VISIBLE)
            bindOptionSwitch(settingHolder, item, arrayOf<String>("normal", "arrows", "none"))
        } else if (item === Toolbar.shortcut_button_1_icon || item === Toolbar.shortcut_button_2_icon) {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.GONE)
            settingHolder.input.setVisibility(View.GONE)
            settingHolder.options.setVisibility(View.VISIBLE)
            bindToolbarIconPicker(settingHolder, item, currentValue)
        } else if (XMLPrefsSave.BOOLEAN == item.type()) {
            settingHolder.toggle.setVisibility(View.VISIBLE)
            settingHolder.colorPreview.setVisibility(View.GONE)
            settingHolder.input.setVisibility(View.GONE)
            val checked = currentValue.toBoolean()
            styleToggle(settingHolder.itemView.getContext(), settingHolder.toggle, checked)
            settingHolder.toggle.setOnClickListener(View.OnClickListener { v: View? ->
                val next = !getCurrentValue(item).toBoolean()
                pendingChanges.put(item, next.toString())
                styleToggle(settingHolder.itemView.getContext(), settingHolder.toggle, next)
            })
        } else if (XMLPrefsSave.COLOR == item.type() || XMLPrefsSave.AUTO_COLOR == item.type()) {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.VISIBLE)
            settingHolder.input.setVisibility(View.VISIBLE)
            settingHolder.input.setText(currentValue)
            updateColorPreview(settingHolder.colorPreview, item, currentValue)

            settingHolder.colorPreview.setOnClickListener(View.OnClickListener { v: View? ->
                expandedColorItem = if (expandedColorItem == item) null else item
                notifyDataSetChanged()
            })

            if (expandedColorItem == item) {
                showColorPicker(settingHolder, item, settingHolder.input.getText().toString())
            }

            settingHolder.textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    val `val` = s.toString()
                    if (`val`.matches("^#[0-9A-Fa-f]{6,8}$".toRegex()) ||
                        item.type() == XMLPrefsSave.AUTO_COLOR && `val`.equals("auto", true)
                    ) {
                        updateColorPreview(settingHolder.colorPreview, item, `val`)
                        pendingChanges.put(item, `val`)
                        if (item === Theme.module_button_background_color && `val`.startsWith("#")) {
                            previewModuleButtonBackground(Color.parseColor(`val`))
                            onButtonThemePreviewChanged()
                        }
                    }
                }
            }
            settingHolder.input.addTextChangedListener(settingHolder.textWatcher)
        } else {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.GONE)
            settingHolder.input.setVisibility(View.VISIBLE)
            settingHolder.input.setText(currentValue)
            settingHolder.textWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    pendingChanges.put(item, s.toString())
                }
            }
            settingHolder.input.addTextChangedListener(settingHolder.textWatcher)
        }
    }

    private fun bindSection(holder: SectionHolder, row: SettingsRow) {
        val title = row.section ?: "Unsectioned"
        val collapsed = if (accordionSections) accordionState.collapsed(title) else collapsedSections.contains(title)
        holder.title.text = (if (collapsed) "[+] " else "[-] ") + title.uppercase()
        holder.title.setTextColor(accentColor())
        holder.title.setOnClickListener {
            if (accordionSections) {
                accordionState.toggle(title)
            } else if (collapsed) {
                collapsedSections.remove(title)
            } else {
                collapsedSections.add(title)
            }
            rebuildVisibleRows()
            notifyDataSetChanged()
        }
    }

    fun restyleVisibleControls(recyclerView: RecyclerView?) {
        if (recyclerView == null) return
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index)) as? ViewHolder
                ?: continue
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) continue
            val item = visibleRows[position].item ?: continue
            val context = holder.itemView.context
            if (holder.toggle.visibility == View.VISIBLE) {
                styleToggle(context, holder.toggle, getCurrentValue(item).toBoolean())
            }
            if (holder.action.visibility == View.VISIBLE) styleButton(context, holder.action, false)
            if (holder.options.visibility == View.VISIBLE) {
                val current = getCurrentValue(item)?.trim()?.lowercase().orEmpty()
                for (childIndex in 0 until holder.options.childCount) {
                    val button = holder.options.getChildAt(childIndex) as? TextView ?: continue
                    val iconPicker = item === Toolbar.shortcut_button_1_icon || item === Toolbar.shortcut_button_2_icon
                    if (iconPicker) styleButton(context, button, true)
                    else styleChoice(context, button, button.tag == current)
                }
            }
        }
    }

    private fun displayLabel(item: XMLPrefsSave): String {
        return when (item.label()) {
            "unified_status_border" -> "Unified Bottom Console"
            "show_ascii" -> "Show ASCII TXT"
            "show_ascii_landscape" -> "Show ASCII In Landscape"
            "ascii_max_lines" -> "ASCII Viewport Rows"
            "ascii_pane_height_rows" -> "ASCII Pane Height"
            "ascii_animation" -> "Enable Animated ASCII"
            "ascii_animation_frame_delay_ms" -> "Animation Frame Delay"
            "ascii_animation_max_file_kb" -> "Max ASCII Import File Size"
            "ascii_index" -> "ASCII Position"
            "ascii_size" -> "Legacy ASCII Text Size"
            "ascii_status_alignment" -> "ASCII Alignment"
            "ascii_text_color" -> "ASCII Text Color"
            "ascii_status_background_color" -> "ASCII Background Color"
            "ascii_status_text_shadow_color" -> "ASCII Text Shadow Color"
            else -> item.label()
        } ?: ""
    }

    private fun rebuildVisibleRows() {
        visibleRows.clear()
        var hidden = false
        for (row in rows) {
            if (row.sectionHeader) {
                hidden = if (accordionSections) accordionState.collapsed(row.section ?: "")
                else collapsedSections.contains(row.section)
                visibleRows.add(row)
            } else if (!hidden) {
                visibleRows.add(row)
            }
        }
    }

    private fun bindOptionSwitch(holder: ViewHolder, item: XMLPrefsSave, options: Array<String>) {
        var current = getCurrentValue(item)
        if (current == null) {
            current = item.defaultValue()
        }
        current = current!!.trim { it <= ' ' }.lowercase()

        for (option in options) {
            val button = TextView(holder.itemView.getContext())
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (holder.options.getChildCount() > 0) {
                params.leftMargin = dp(holder.itemView.getContext(), 6f)
            }
            button.setLayoutParams(params)
            button.setText(option.uppercase())
            button.tag = option
            button.setGravity(Gravity.CENTER)
            button.setSingleLine(true)
            button.setPadding(
                dp(holder.itemView.getContext(), 6f),
                dp(holder.itemView.getContext(), 8f),
                dp(holder.itemView.getContext(), 6f),
                dp(holder.itemView.getContext(), 8f)
            )
            styleChoice(holder.itemView.getContext(), button, option == current)
            button.setOnClickListener(View.OnClickListener { v: View? ->
                pendingChanges.put(item, option)
                notifyDataSetChanged()
            })
            holder.options.addView(button)
        }
    }

    private fun bindToolbarIconPicker(holder: ViewHolder, item: XMLPrefsSave, currentValue: String?) {
        val context = holder.itemView.context
        val selected = ToolbarShortcutManager.normalizeIcon(currentValue)
        val choice = ToolbarShortcutManager.icons().first { it.key == selected }
        val button = TextView(context)
        button.text = choice.label.uppercase()
        button.gravity = Gravity.CENTER_VERTICAL
        button.setCompoundDrawablesWithIntrinsicBounds(choice.drawableRes, 0, 0, 0)
        button.compoundDrawablePadding = dp(context, 10f)
        styleButton(context, button, true)
        button.setOnClickListener {
            TuixtDialog.showCustom(context, "Toolbar Icon", TuixtDialog.ContentFactory { dialog ->
                val content = LinearLayout(context)
                content.orientation = LinearLayout.VERTICAL
                for (icon in ToolbarShortcutManager.icons()) {
                    val row = TextView(context)
                    row.text = icon.label.uppercase()
                    row.gravity = Gravity.CENTER_VERTICAL
                    row.setCompoundDrawablesWithIntrinsicBounds(icon.drawableRes, 0, 0, 0)
                    row.compoundDrawablePadding = dp(context, 12f)
                    TuixtTheme.styleListItem(context, row, icon.key == selected)
                    val params = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    params.bottomMargin = dp(context, 8f)
                    content.addView(row, params)
                    row.setOnClickListener {
                        pendingChanges[item] = icon.key
                        dialog?.dismiss()
                        notifyDataSetChanged()
                    }
                }
                content
            })
        }
        holder.options.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun updateColorPreview(view: View, item: XMLPrefsSave, hex: String?) {
        try {
            val color = if (hex.equals("auto", true)) inheritedColor() else Color.parseColor(hex)
            styleColorPreview(view.getContext(), view, color)
        } catch (e: Exception) {
            styleColorPreview(view.getContext(), view, inheritedColor())
        }
    }

    private fun inheritedColor(): Int = AppearanceSettings.terminalBorderColor()

    private fun getCurrentValue(item: XMLPrefsSave?): String? {
        return if (pendingChanges.containsKey(item)) pendingChanges.get(item) else get(item)
    }

    private fun showColorPicker(holder: ViewHolder, item: XMLPrefsSave, currentHex: String?) {
        val dialogView = LayoutInflater.from(holder.itemView.getContext())
            .inflate(R.layout.color_picker_dialog, holder.colorPicker, false)
        styleColorPicker(dialogView)
        val preview = dialogView.findViewById<View>(R.id.color_preview)
        val seekAlpha = dialogView.findViewById<SeekBar>(R.id.seek_alpha)
        val seekHue = dialogView.findViewById<SeekBar>(R.id.seek_hue)
        val seekSat = dialogView.findViewById<SeekBar>(R.id.seek_sat)
        val seekVal = dialogView.findViewById<SeekBar>(R.id.seek_val)
        val hexText = dialogView.findViewById<TextView>(R.id.hex_preview)
        val autoButton = dialogView.findViewById<TextView>(R.id.auto_color)

        var initialColor: Int
        try {
            initialColor = if (currentHex.equals("auto", true)) inheritedColor() else Color.parseColor(currentHex)
        } catch (e: Exception) {
            initialColor = Color.WHITE
        }

        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        val alpha = Color.alpha(initialColor)

        seekAlpha.setProgress(alpha)
        seekHue.setProgress(hsv[0].toInt())
        seekSat.setProgress((hsv[1] * 100).toInt())
        seekVal.setProgress((hsv[2] * 100).toInt())

        val listener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newHsv = floatArrayOf(
                    seekHue.getProgress().toFloat(),
                    seekSat.getProgress().toFloat() / 100f,
                    seekVal.getProgress().toFloat() / 100f
                )
                val newAlpha = seekAlpha.getProgress()
                val newColor = Color.HSVToColor(newAlpha, newHsv)
                preview.setBackgroundColor(newColor)
                val hex = String.format("#%08X", newColor)
                hexText.setText(hex)
                if (fromUser) {
                    holder.input.setText(hex)
                    holder.input.setSelection(hex.length)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekAlpha.setOnSeekBarChangeListener(listener)
        seekHue.setOnSeekBarChangeListener(listener)
        seekSat.setOnSeekBarChangeListener(listener)
        seekVal.setOnSeekBarChangeListener(listener)

        if (item.type() == XMLPrefsSave.AUTO_COLOR) {
            autoButton.visibility = View.VISIBLE
            styleChoice(holder.itemView.context, autoButton, currentHex.equals("auto", true))
            autoButton.setOnClickListener {
                holder.input.setText("auto")
                holder.input.setSelection(4)
                preview.setBackgroundColor(inheritedColor())
                hexText.text = "AUTO  ${String.format("#%08X", inheritedColor())}"
            }
        } else {
            autoButton.visibility = View.GONE
        }

        // Initial trigger
        listener.onProgressChanged(null, 0, false)

        holder.colorPicker.addView(dialogView)
        holder.colorPicker.visibility = View.VISIBLE
    }

    private fun styleColorPicker(dialogView: View) {
        val context = dialogView.getContext()
        dialogView.setBackgroundColor(Color.TRANSPARENT)

        val title = dialogView.findViewById<TextView?>(R.id.picker_title)
        if (title != null) {
            title.setVisibility(View.GONE)
        }

        val accent = borderColor()
        val text = textColor()
        tintSeekBar(dialogView.findViewById<SeekBar?>(R.id.seek_alpha), accent)
        tintSeekBar(dialogView.findViewById<SeekBar?>(R.id.seek_hue), accent)
        tintSeekBar(dialogView.findViewById<SeekBar?>(R.id.seek_sat), accent)
        tintSeekBar(dialogView.findViewById<SeekBar?>(R.id.seek_val), accent)

        stylePickerLabels(dialogView, text)
        val hexText = dialogView.findViewById<TextView?>(R.id.hex_preview)
        if (hexText != null) {
            hexText.setTextColor(text)
            hexText.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            hexText.setBackground(rect(context, surfaceColor(), borderColor(), 1.25f))
            hexText.setPadding(
                dp(context, 8f),
                dp(context, 8f),
                dp(context, 8f),
                dp(context, 8f)
            )
        }
        dialogView.findViewById<TextView?>(R.id.auto_color)?.let { styleButton(context, it, false) }
    }

    private fun stylePickerLabels(root: View?, textColor: Int) {
        if (root !is ViewGroup) {
            return
        }
        val group = root
        for (i in 0..<group.getChildCount()) {
            val child = group.getChildAt(i)
            if (child is TextView && child.getId() != R.id.hex_preview && child.getId() != R.id.picker_title && child.getId() != R.id.auto_color) {
                val label = child
                label.setTextColor(textColor)
                label.setTypeface(Tuils.getTypeface(root.getContext()), Typeface.BOLD)
            } else {
                stylePickerLabels(child, textColor)
            }
        }
    }

    private fun tintSeekBar(seekBar: SeekBar?, color: Int) {
        if (seekBar == null) {
            return
        }
        TuixtTheme.styleSlider(seekBar.context, seekBar, color)
    }

    override fun getItemCount(): Int {
        return visibleRows.size
    }

    class SectionHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView as TextView
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var title: TextView
        var description: TextView
        var toggle: TextView
        var action: TextView
        var options: LinearLayout
        var colorPicker: ViewGroup
        var colorPreview: View
        var input: EditText
        var textWatcher: TextWatcher? = null

        init {
            title = itemView.findViewById<TextView>(R.id.setting_title)
            description = itemView.findViewById<TextView>(R.id.setting_description)
            toggle = itemView.findViewById<TextView>(R.id.setting_switch)
            action = itemView.findViewById<TextView>(R.id.setting_action)
            options = itemView.findViewById<LinearLayout>(R.id.setting_options)
            colorPicker = itemView.findViewById<ViewGroup>(R.id.setting_color_picker)
            colorPreview = itemView.findViewById<View>(R.id.setting_color_preview)
            input = itemView.findViewById<EditText>(R.id.setting_input)
        }
    }

    data class SettingsRow(
        val item: XMLPrefsSave?,
        val section: String?,
        val sectionHeader: Boolean
    ) {
        companion object {
            fun section(title: String): SettingsRow = SettingsRow(null, title, true)

            fun setting(item: XMLPrefsSave, section: String): SettingsRow = SettingsRow(item, section, false)
        }
    }

    companion object {
        private const val VIEW_TYPE_SECTION = 0
        private const val VIEW_TYPE_SETTING = 1
    }
}
