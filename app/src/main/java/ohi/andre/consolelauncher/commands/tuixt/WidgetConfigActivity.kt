package ohi.andre.consolelauncher.commands.tuixt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleToggle
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class WidgetConfigActivity : Activity() {
    private var widgetId: String? = null
    private var engine: LuaWidgetEngine? = null
    private var fieldsContainer: LinearLayout? = null
    private val bindings: MutableList<FieldBinding> = ArrayList()
    private var originalSnapshot = ""
    private var saveAction = "save"

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        widgetId = LuaWidgetManager.normalizeId(intent.getStringExtra(EXTRA_WIDGET_ID))
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            finish()
            return
        }

        engine = LuaWidgetEngine(
            this,
            widgetId,
            LuaWidgetManager.readScript(widgetId),
            LuaWidgetManager.version(widgetId),
            null
        )
        val result = engine!!.config()
        if (!TextUtils.isEmpty(result.error)) {
            Toast.makeText(this, "Config failed: " + result.error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        if (TextUtils.isEmpty(result.configJson)) {
            Toast.makeText(this, "No config surface: " + LuaWidgetManager.getName(widgetId), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val spec = try {
            JSONObject(result.configJson!!)
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid config schema: " + e.message, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val screen = FrameLayout(this)
        screen.setBackgroundColor(overlayColor())
        screen.setFitsSystemWindows(true)
        val contentHost = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)

        val panelShell = FrameLayout(this)
        panelShell.clipChildren = false
        panelShell.clipToPadding = false

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
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
        header.text = spec.optString("title", LuaWidgetManager.getName(widgetId) ?: "")
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
        scrollView.setMaxHeight(maxConfigScrollHeight())
        fieldsContainer = LinearLayout(this)
        fieldsContainer!!.orientation = LinearLayout.VERTICAL
        fieldsContainer!!.setPadding(0, 0, 0, dp(this, 8f))
        scrollView.addView(
            fieldsContainer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        bindFields(spec.optJSONArray("fields"))
        saveAction = resolveSaveAction(spec.optJSONArray("buttons"))
        originalSnapshot = currentValues(false).toString()

        val bottomBar = LinearLayout(this)
        bottomBar.orientation = LinearLayout.HORIZONTAL
        bottomBar.gravity = Gravity.CENTER_VERTICAL
        bottomBar.setPadding(0, dp(this, 10f), 0, 0)

        val cancel = button("CANCEL", false)
        cancel.setOnClickListener { attemptClose() }
        bottomBar.addView(cancel)

        val spacer = View(this)
        bottomBar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

        val save = button("SAVE", true)
        save.setOnClickListener { saveAndClose() }
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

    private fun bindFields(fields: JSONArray?) {
        if (fields == null || fields.length() == 0) {
            val empty = TextView(this)
            empty.text = "No editable fields."
            empty.setTextColor(textColor())
            empty.typeface = Tuils.getTypeface(this)
            empty.textSize = 14f
            empty.setPadding(dp(this, 10f), dp(this, 12f), dp(this, 10f), dp(this, 12f))
            fieldsContainer!!.addView(empty, rowParams())
            return
        }

        for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            val id = field.optString("id", "")
            if (TextUtils.isEmpty(id)) {
                continue
            }
            val type = field.optString("type", "text").lowercase(Locale.US)
            val label = field.optString("label", id)

            val wrapper = LinearLayout(this)
            wrapper.orientation = LinearLayout.VERTICAL
            wrapper.gravity = Gravity.START

            val labelView = TextView(this)
            labelView.text = label.uppercase(Locale.getDefault())
            labelView.setTextColor(textColor())
            labelView.typeface = Tuils.getTypeface(this)
            labelView.textSize = 12f
            labelView.setPadding(0, 0, 0, dp(this, 5f))
            wrapper.addView(
                labelView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val binding = when (type) {
                "toggle" -> addToggle(field, wrapper)
                "select" -> addSelect(field, wrapper)
                "number" -> addText(field, wrapper, true, false)
                "textarea", "multiline" -> addText(field, wrapper, false, true)
                else -> addText(field, wrapper, false, false)
            }
            bindings.add(binding)
            fieldsContainer!!.addView(wrapper, rowParams())
        }
    }

    private fun addText(field: JSONObject, wrapper: LinearLayout, number: Boolean, multiline: Boolean): FieldBinding {
        val editor = EditText(this)
        editor.setText(valueString(field.opt("value")))
        editor.hint = field.optString("hint", "")
        editor.setSingleLine(!multiline)
        if (multiline) {
            editor.gravity = Gravity.TOP or Gravity.START
            editor.minLines = max(3, field.optInt("lines", field.optInt("rows", 5)))
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else if (number) {
            editor.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        styleInput(this, editor)
        wrapper.addView(
            editor,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return FieldBinding(field, editor, null, null)
    }

    private fun addToggle(field: JSONObject, wrapper: LinearLayout): FieldBinding {
        val toggle = TextView(this)
        styleToggle(this, toggle, boolValue(field.opt("value")))
        toggle.setOnClickListener {
            val checked = !"true".equals(toggle.tag as? String, ignoreCase = true)
            toggle.tag = checked.toString()
            styleToggle(this, toggle, checked)
        }
        toggle.tag = boolValue(field.opt("value")).toString()
        wrapper.addView(
            toggle,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return FieldBinding(field, null, toggle, null)
    }

    private fun addSelect(field: JSONObject, wrapper: LinearLayout): FieldBinding {
        val options = options(field.optJSONArray("options"))
        val selected = SelectState(options, valueString(field.opt("value")))
        val button = TextView(this)
        styleButton(this, button, false)
        updateSelect(button, selected)
        button.setOnClickListener {
            selected.next()
            updateSelect(button, selected)
        }
        wrapper.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return FieldBinding(field, null, button, selected)
    }

    private fun saveAndClose() {
        val values = currentValues(true) ?: return
        val result = engine!!.submitConfig(saveAction, values)
        if (!TextUtils.isEmpty(result.error)) {
            Toast.makeText(this, "Save failed: " + result.error, Toast.LENGTH_LONG).show()
            return
        }
        sendReload()
        setResult(TuixtActivity.SAVE_PRESSED, Intent())
        finish()
    }

    private fun currentValues(validate: Boolean): JSONObject? {
        val values = JSONObject()
        for (binding in bindings) {
            val id = binding.id()
            val type = binding.type()
            val raw = binding.value()
            if (validate && binding.required() && raw.trim { it <= ' ' }.isEmpty()) {
                Toast.makeText(this, binding.label() + " is required.", Toast.LENGTH_SHORT).show()
                return null
            }
            if ("number" == type) {
                if (raw.trim { it <= ' ' }.isEmpty()) {
                    values.put(id, JSONObject.NULL)
                } else {
                    try {
                        var number = raw.toDouble()
                        if (validate && binding.hasMin() && number < binding.min()) {
                            Toast.makeText(this, binding.label() + " must be at least " + binding.minText() + ".", Toast.LENGTH_SHORT).show()
                            return null
                        }
                        if (validate && binding.hasMax() && number > binding.max()) {
                            Toast.makeText(this, binding.label() + " must be at most " + binding.maxText() + ".", Toast.LENGTH_SHORT).show()
                            return null
                        }
                        values.put(id, number)
                    } catch (e: Exception) {
                        Toast.makeText(this, binding.label() + " must be a number.", Toast.LENGTH_SHORT).show()
                        return null
                    }
                }
            } else if ("toggle" == type) {
                values.put(id, raw.toBoolean())
            } else {
                values.put(id, raw)
            }
        }
        return values
    }

    private fun sendReload() {
        val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
        intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, "lua_reload_widget")
        intent.putExtra(UIManager.EXTRA_MODULE_NAME, widgetId)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun attemptClose() {
        val current = currentValues(false)
        if (current == null || TextUtils.equals(originalSnapshot, current.toString())) {
            finish()
            return
        }
        TuixtDialog.showConfirm(
            this,
            "Discard Changes?",
            "Unsaved Lua module settings will be lost.",
            "Discard",
            "Keep Editing",
            ConfirmAction { this.finish() })
    }

    private fun button(label: String?, primary: Boolean): TextView {
        val view = TextView(this)
        view.text = label
        styleButton(this, view, primary)
        return view
    }

    private fun rowParams(): LinearLayout.LayoutParams {
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, dp(this, 12f))
        return params
    }

    private fun maxConfigScrollHeight(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return max(dp(this, 280f), screenHeight - dp(this, 220f))
    }

    private fun resolveSaveAction(buttons: JSONArray?): String {
        if (buttons == null) {
            return "save"
        }
        var fallback = "save"
        for (i in 0 until buttons.length()) {
            val button = buttons.optJSONObject(i) ?: continue
            val id = button.optString("id", "")
            if (!TextUtils.isEmpty(id)) {
                fallback = id
            }
            if (button.optBoolean("primary", false)) {
                return if (TextUtils.isEmpty(id)) "save" else id
            }
        }
        return fallback
    }

    private fun options(array: JSONArray?): MutableList<SelectOption> {
        val options = ArrayList<SelectOption>()
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                if (item is JSONObject) {
                    val value = item.optString("value", item.optString("label", ""))
                    options.add(SelectOption(value, item.optString("label", value)))
                } else if (item != null && item !== JSONObject.NULL) {
                    val value = item.toString()
                    options.add(SelectOption(value, value))
                }
            }
        }
        if (options.isEmpty()) {
            options.add(SelectOption("", "None"))
        }
        return options
    }

    private fun updateSelect(button: TextView, selected: SelectState) {
        button.text = selected.label().uppercase(Locale.getDefault())
        button.tag = selected.value()
    }

    private fun valueString(value: Any?): String {
        if (value == null || value === JSONObject.NULL) {
            return ""
        }
        return value.toString()
    }

    private fun boolValue(value: Any?): Boolean {
        if (value is Boolean) {
            return value
        }
        val text = valueString(value).lowercase(Locale.US)
        return "true" == text || "1" == text || "yes" == text || "on" == text
    }

    private class FieldBinding(
        val field: JSONObject,
        val editor: EditText?,
        val button: TextView?,
        val select: SelectState?
    ) {
        fun id(): String = field.optString("id", "")
        fun label(): String = field.optString("label", id())
        fun type(): String = field.optString("type", "text").lowercase(Locale.US)
        fun required(): Boolean = field.optBoolean("required", false)
        fun hasMin(): Boolean = field.has("min") && !field.isNull("min")
        fun hasMax(): Boolean = field.has("max") && !field.isNull("max")
        fun min(): Double = field.optDouble("min", Double.NEGATIVE_INFINITY)
        fun max(): Double = field.optDouble("max", Double.POSITIVE_INFINITY)
        fun minText(): String = trimDecimal(min())
        fun maxText(): String = trimDecimal(max())
        fun value(): String {
            if (editor != null) {
                return editor.text.toString()
            }
            if (select != null) {
                return select.value()
            }
            if (button != null) {
                return (button.tag as? String) ?: ""
            }
            return ""
        }

        private fun trimDecimal(value: Double): String {
            val longValue = value.toLong()
            return if (value == longValue.toDouble()) longValue.toString() else value.toString()
        }
    }

    private class SelectOption(val value: String, val label: String)

    private class SelectState(private val options: MutableList<SelectOption>, initialValue: String) {
        private var index = 0

        init {
            for (i in options.indices) {
                if (options[i].value == initialValue) {
                    index = i
                    break
                }
            }
        }

        fun next() {
            index = (index + 1) % max(1, options.size)
        }

        fun value(): String = options[min(index, options.size - 1)].value

        fun label(): String = options[min(index, options.size - 1)].label
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
            var targetHeightMeasureSpec = heightMeasureSpec
            if (maxHeight > 0) {
                val heightMode = MeasureSpec.getMode(heightMeasureSpec)
                val heightSize = MeasureSpec.getSize(heightMeasureSpec)
                val constrainedHeight = if (heightMode == MeasureSpec.UNSPECIFIED)
                    maxHeight
                else min(heightSize, maxHeight)
                targetHeightMeasureSpec =
                    MeasureSpec.makeMeasureSpec(constrainedHeight, MeasureSpec.AT_MOST)
            }
            super.onMeasure(widthMeasureSpec, targetHeightMeasureSpec)
        }
    }

    companion object {
        const val EXTRA_WIDGET_ID: String = "widget_id"
    }
}
