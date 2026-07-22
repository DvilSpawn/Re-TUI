package ohi.andre.consolelauncher.commands.tuixt

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleColorPreview
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.get
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.set
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import androidx.annotation.NonNull
import java.util.ArrayList
import java.util.HashMap
import java.util.Map
import ohi.andre.consolelauncher.managers.settings.LauncherSettings

class TuixtAdapter(rows: MutableList<SettingsRow>, private val file: File?) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var rows: MutableList<SettingsRow>
    private val visibleRows: MutableList<SettingsRow> = ArrayList<SettingsRow>()
    private val collapsedSections: MutableSet<String> = HashSet<String>()
    private val pendingChanges: MutableMap<XMLPrefsSave?, String?> =
        HashMap<XMLPrefsSave?, String?>()

    init {
        this.rows = ArrayList<SettingsRow>(rows)
        rebuildVisibleRows()
    }

    fun updateRows(newRows: MutableList<SettingsRow>) {
        this.rows = ArrayList<SettingsRow>(newRows)
        rebuildVisibleRows()
        notifyDataSetChanged()
    }

    @JvmOverloads
    fun saveAll(context: Context? = null) {
        for (entry in pendingChanges.entries) {
            val item = entry.key
            val value = entry.value
            set(context, item, value)
        }
        pendingChanges.clear()
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
        settingHolder.colorPreview.setOnClickListener(null)
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

        if (item === Behavior.output_tray_mode) {
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
        } else if (XMLPrefsSave.COLOR == item.type()) {
            settingHolder.toggle.setVisibility(View.GONE)
            settingHolder.colorPreview.setVisibility(View.VISIBLE)
            settingHolder.input.setVisibility(View.VISIBLE)
            settingHolder.input.setText(currentValue)
            updateColorPreview(settingHolder.colorPreview, currentValue)

            settingHolder.colorPreview.setOnClickListener(View.OnClickListener { v: View? ->
                showColorPicker(
                    settingHolder,
                    item,
                    settingHolder.input.getText().toString()
                )
            })

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
                    if (`val`.matches("^#[0-9A-Fa-f]{6,8}$".toRegex())) {
                        updateColorPreview(settingHolder.colorPreview, `val`)
                        pendingChanges.put(item, `val`)
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
        val collapsed = collapsedSections.contains(title)
        holder.title.text = (if (collapsed) "[+] " else "[-] ") + title.uppercase()
        holder.title.setTextColor(accentColor())
        holder.title.setOnClickListener {
            if (collapsed) {
                collapsedSections.remove(title)
            } else {
                collapsedSections.add(title)
            }
            rebuildVisibleRows()
            notifyDataSetChanged()
        }
    }

    private fun displayLabel(item: XMLPrefsSave): String {
        return when (item.label()) {
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
                hidden = collapsedSections.contains(row.section)
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
            button.setGravity(Gravity.CENTER)
            button.setSingleLine(true)
            button.setPadding(
                dp(holder.itemView.getContext(), 6f),
                dp(holder.itemView.getContext(), 8f),
                dp(holder.itemView.getContext(), 6f),
                dp(holder.itemView.getContext(), 8f)
            )
            styleButton(holder.itemView.getContext(), button, option == current)
            button.setOnClickListener(View.OnClickListener { v: View? ->
                pendingChanges.put(item, option)
                notifyDataSetChanged()
            })
            holder.options.addView(button)
        }
    }

    private fun updateColorPreview(view: View, hex: String?) {
        try {
            styleColorPreview(view.getContext(), view, Color.parseColor(hex))
        } catch (e: Exception) {
            styleColorPreview(view.getContext(), view, Color.BLACK)
        }
    }

    private fun getCurrentValue(item: XMLPrefsSave?): String? {
        return if (pendingChanges.containsKey(item)) pendingChanges.get(item) else get(item)
    }

    private fun showColorPicker(holder: ViewHolder, item: XMLPrefsSave?, currentHex: String?) {
        val dialogView = LayoutInflater.from(holder.itemView.getContext())
            .inflate(
                R.layout.color_picker_dialog,
                FrameLayout(holder.itemView.getContext()),
                false
            )
        styleColorPicker(dialogView)
        val preview = dialogView.findViewById<View>(R.id.color_preview)
        val seekAlpha = dialogView.findViewById<SeekBar>(R.id.seek_alpha)
        val seekHue = dialogView.findViewById<SeekBar>(R.id.seek_hue)
        val seekSat = dialogView.findViewById<SeekBar>(R.id.seek_sat)
        val seekVal = dialogView.findViewById<SeekBar>(R.id.seek_val)
        val hexText = dialogView.findViewById<TextView>(R.id.hex_preview)

        var initialColor: Int
        try {
            initialColor = Color.parseColor(currentHex)
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
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekAlpha.setOnSeekBarChangeListener(listener)
        seekHue.setOnSeekBarChangeListener(listener)
        seekSat.setOnSeekBarChangeListener(listener)
        seekVal.setOnSeekBarChangeListener(listener)

        // Initial trigger
        listener.onProgressChanged(null, 0, false)

        TuixtDialog.showContent(
            holder.itemView.getContext(),
            "Pick Color",
            dialogView,
            "OK",
            "Cancel",
            ConfirmAction {
                val finalHex = hexText.getText().toString()
                holder.input.setText(finalHex)
                pendingChanges.put(item, finalHex)
            })
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
    }

    private fun stylePickerLabels(root: View?, textColor: Int) {
        if (root !is ViewGroup) {
            return
        }
        val group = root
        for (i in 0..<group.getChildCount()) {
            val child = group.getChildAt(i)
            if (child is TextView && child.getId() != R.id.hex_preview && child.getId() != R.id.picker_title) {
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
        val tint = ColorStateList.valueOf(color)
        seekBar.setProgressTintList(tint)
        seekBar.setThumbTintList(tint)
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(surfaceColor()))
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
        var options: LinearLayout
        var colorPreview: View
        var input: EditText
        var textWatcher: TextWatcher? = null

        init {
            title = itemView.findViewById<TextView>(R.id.setting_title)
            description = itemView.findViewById<TextView>(R.id.setting_description)
            toggle = itemView.findViewById<TextView>(R.id.setting_switch)
            options = itemView.findViewById<LinearLayout>(R.id.setting_options)
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
