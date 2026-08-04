package ohi.andre.consolelauncher.integrations.tasker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.joaomgcd.taskerpluginlibrary.SimpleResult
import com.joaomgcd.taskerpluginlibrary.SimpleResultError
import com.joaomgcd.taskerpluginlibrary.SimpleResultSuccess
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerAction
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultError
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.managers.SpaceManager
import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import ohi.andre.consolelauncher.tuils.Tuils

@TaskerInputRoot
class RetuiTaskerInput @JvmOverloads constructor(
    @field:TaskerInputField("action", labelResIdName = "tasker_action") var action: String? = null,
    @field:TaskerInputField("preset", labelResIdName = "tasker_preset") var preset: String? = null,
    @field:TaskerInputField("theme_element", labelResIdName = "tasker_theme_element") var themeElement: String? = null,
    @field:TaskerInputField("value", labelResIdName = "tasker_value") var value: String? = null,
    @field:TaskerInputField("module", labelResIdName = "tasker_module") var module: String? = null,
    @field:TaskerInputField("text", labelResIdName = "tasker_text") var text: String? = null,
    @field:TaskerInputField("space", labelResIdName = "tasker_space") var space: String? = null
)

class RetuiTaskerRunner : TaskerPluginRunnerAction<RetuiTaskerInput, Unit>() {
    override val notificationProperties
        get() = NotificationProperties(iconResId = R.mipmap.ic_launcher)

    override fun run(context: Context, input: TaskerInput<RetuiTaskerInput>): TaskerPluginResult<Unit> {
        val value = input.regular
        val result = TaskerIntegrationManager.execute(
            context,
            TaskerIntegrationManager.Request(
                value.action, value.preset, value.themeElement, value.value, value.module, value.text, value.space
            )
        )
        return if (result.success) TaskerPluginResultSucess()
        else TaskerPluginResultError(1, result.message)
    }
}

class RetuiTaskerHelper(config: TaskerPluginConfig<RetuiTaskerInput>) :
    TaskerPluginConfigHelper<RetuiTaskerInput, Unit, RetuiTaskerRunner>(config) {
    override val runnerClass = RetuiTaskerRunner::class.java
    override val inputClass = RetuiTaskerInput::class.java
    override val outputClass = Unit::class.java
    override val defaultInput = RetuiTaskerInput(TaskerIntegrationManager.ACTION_APPLY_PRESET)
    override val addDefaultStringBlurb = false

    override fun isInputValid(input: TaskerInput<RetuiTaskerInput>): SimpleResult {
        val error = validationError(input.regular)
        return if (error == null) SimpleResultSuccess() else SimpleResultError(error)
    }

    override fun addToStringBlurb(input: TaskerInput<RetuiTaskerInput>, blurbBuilder: StringBuilder) {
        val regular = input.regular
        val target = when (regular.action) {
            TaskerIntegrationManager.ACTION_APPLY_PRESET -> regular.preset
            TaskerIntegrationManager.ACTION_SET_THEME -> "${regular.themeElement} = ${regular.value}"
            TaskerIntegrationManager.ACTION_SHOW_MODULE,
            TaskerIntegrationManager.ACTION_REFRESH_MODULE,
            TaskerIntegrationManager.ACTION_UPDATE_MODULE_TEXT -> regular.module
            TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT -> regular.text
            TaskerIntegrationManager.ACTION_SWITCH_SPACE -> regular.space
            else -> null
        }
        blurbBuilder.append(actionLabel(regular.action))
        if (!target.isNullOrBlank()) blurbBuilder.append(": ").append(target)
    }

    companion object {
        val ACTIONS = listOf(
            TaskerIntegrationManager.ACTION_APPLY_PRESET,
            TaskerIntegrationManager.ACTION_SET_THEME,
            TaskerIntegrationManager.ACTION_SHOW_MODULE,
            TaskerIntegrationManager.ACTION_REFRESH_MODULE,
            TaskerIntegrationManager.ACTION_UPDATE_MODULE_TEXT,
            TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT,
            TaskerIntegrationManager.ACTION_SWITCH_SPACE
        )

        fun actionLabel(action: String?): String = when (action) {
            TaskerIntegrationManager.ACTION_APPLY_PRESET -> "Apply preset"
            TaskerIntegrationManager.ACTION_SET_THEME -> "Set theme element"
            TaskerIntegrationManager.ACTION_SHOW_MODULE -> "Show module"
            TaskerIntegrationManager.ACTION_REFRESH_MODULE -> "Refresh module"
            TaskerIntegrationManager.ACTION_UPDATE_MODULE_TEXT -> "Update module text"
            TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT -> "Terminal output"
            TaskerIntegrationManager.ACTION_SWITCH_SPACE -> "Switch Space"
            else -> "RETUI action"
        }

        fun validationError(input: RetuiTaskerInput): String? = when {
            input.action !in ACTIONS -> "Choose a RETUI action."
            input.action == TaskerIntegrationManager.ACTION_SWITCH_SPACE && input.space.isNullOrBlank() ->
                "Choose a Space."
            else -> null
        }
    }
}

class RetuiTaskerConfigActivity : Activity(), TaskerPluginConfig<RetuiTaskerInput> {
    override val context: Context get() = applicationContext
    private val helper by lazy { RetuiTaskerHelper(this) }
    private lateinit var actionSpinner: Spinner
    private lateinit var presetContainer: LinearLayout
    private lateinit var presetSpinner: Spinner
    private lateinit var customPreset: EditText
    private lateinit var presetOptions: List<String>
    private lateinit var themeElement: EditText
    private lateinit var value: EditText
    private lateinit var module: EditText
    private lateinit var text: EditText
    private lateinit var spaceContainer: LinearLayout
    private lateinit var spaceSpinner: Spinner
    private lateinit var customSpace: EditText
    private lateinit var spaceOptions: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Tuils.init(this)
        title = "RETUI Action"
        setContentView(buildContent())
        helper.onCreate()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        root.addView(TextView(this).apply { text = "Choose what Tasker should change in RETUI."; textSize = 16f })
        actionSpinner = Spinner(this)
        actionSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            RetuiTaskerHelper.ACTIONS.map { RetuiTaskerHelper.actionLabel(it) })
        root.addView(actionSpinner)
        presetOptions = PresetManager.listAllPresetNames() + CUSTOM_PRESET
        presetContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        presetContainer.addView(TextView(this).apply { text = "Preset name"; textSize = 13f })
        presetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@RetuiTaskerConfigActivity, android.R.layout.simple_spinner_dropdown_item, presetOptions)
            onItemSelectedListener = SimpleItemSelectedListener { updateCustomPresetVisibility() }
        }
        presetContainer.addView(presetSpinner)
        customPreset = EditText(this).apply {
            hint = "Custom name or Tasker variable, e.g. %preset"
            setSingleLine(false)
        }
        presetContainer.addView(customPreset, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(presetContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        spaceOptions = SpaceManager.listSpaces(this).map { it.name } + CUSTOM_SPACE
        spaceContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        spaceContainer.addView(TextView(this).apply { text = "Space"; textSize = 13f })
        spaceSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@RetuiTaskerConfigActivity, android.R.layout.simple_spinner_dropdown_item, spaceOptions)
            onItemSelectedListener = SimpleItemSelectedListener { updateCustomSpaceVisibility() }
        }
        spaceContainer.addView(spaceSpinner)
        customSpace = EditText(this).apply {
            hint = "Custom name or Tasker variable, e.g. %space"
            setSingleLine(false)
        }
        spaceContainer.addView(customSpace, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(spaceContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        themeElement = field(root, "Theme element, e.g. input_text_color")
        value = field(root, "Color value, e.g. #FF00FF or %color")
        module = field(root, "Module id")
        text = field(root, "Text")
        actionSpinner.setSelection(0)
        actionSpinner.onItemSelectedListener = SimpleItemSelectedListener { updateVisibleFields() }
        root.addView(Button(this).apply {
            text = "Save to Tasker"
            setOnClickListener { finishConfiguration() }
        })
        updateVisibleFields()
        return root
    }

    private fun field(root: LinearLayout, hintText: String): EditText = EditText(this).also {
        it.hint = hintText
        it.setSingleLine(false)
        root.addView(it, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun selectedAction(): String = RetuiTaskerHelper.ACTIONS[actionSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun selectedPreset(): String =
        if (presetSpinner.selectedItemPosition == presetOptions.lastIndex) customPreset.text.toString()
        else presetOptions[presetSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun updateCustomPresetVisibility() {
        customPreset.visibility = if (presetSpinner.selectedItemPosition == presetOptions.lastIndex) View.VISIBLE else View.GONE
    }

    private fun selectedSpace(): String =
        if (spaceSpinner.selectedItemPosition == spaceOptions.lastIndex) customSpace.text.toString()
        else spaceOptions[spaceSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun updateCustomSpaceVisibility() {
        customSpace.visibility = if (spaceSpinner.selectedItemPosition == spaceOptions.lastIndex) View.VISIBLE else View.GONE
    }

    private fun updateVisibleFields() {
        val action = selectedAction()
        presetContainer.visibility = if (action == TaskerIntegrationManager.ACTION_APPLY_PRESET) View.VISIBLE else View.GONE
        spaceContainer.visibility = if (action == TaskerIntegrationManager.ACTION_SWITCH_SPACE) View.VISIBLE else View.GONE
        themeElement.visibility = if (action == TaskerIntegrationManager.ACTION_SET_THEME) View.VISIBLE else View.GONE
        value.visibility = if (action == TaskerIntegrationManager.ACTION_SET_THEME) View.VISIBLE else View.GONE
        module.visibility = if (action in listOf(TaskerIntegrationManager.ACTION_SHOW_MODULE, TaskerIntegrationManager.ACTION_REFRESH_MODULE, TaskerIntegrationManager.ACTION_UPDATE_MODULE_TEXT)) View.VISIBLE else View.GONE
        text.visibility = if (action in listOf(TaskerIntegrationManager.ACTION_UPDATE_MODULE_TEXT, TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT)) View.VISIBLE else View.GONE
    }

    override fun assignFromInput(input: TaskerInput<RetuiTaskerInput>) {
        val regular = input.regular
        val index = RetuiTaskerHelper.ACTIONS.indexOf(regular.action).coerceAtLeast(0)
        actionSpinner.setSelection(index)
        val savedPreset = regular.preset.orEmpty()
        val presetIndex = presetOptions.indexOfFirst { it.equals(savedPreset, ignoreCase = true) }
        if (savedPreset.isBlank()) {
            presetSpinner.setSelection(0)
            customPreset.setText("")
        } else if (presetIndex >= 0 && presetIndex != presetOptions.lastIndex) {
            presetSpinner.setSelection(presetIndex)
            customPreset.setText("")
        } else {
            presetSpinner.setSelection(presetOptions.lastIndex)
            customPreset.setText(savedPreset)
        }
        themeElement.setText(regular.themeElement.orEmpty())
        value.setText(regular.value.orEmpty())
        module.setText(regular.module.orEmpty())
        text.setText(regular.text.orEmpty())
        val savedSpace = regular.space.orEmpty()
        val spaceIndex = spaceOptions.indexOfFirst { it.equals(savedSpace, ignoreCase = true) }
        if (savedSpace.isBlank()) {
            spaceSpinner.setSelection(0)
            customSpace.setText("")
        } else if (spaceIndex >= 0 && spaceIndex != spaceOptions.lastIndex) {
            spaceSpinner.setSelection(spaceIndex)
            customSpace.setText("")
        } else {
            spaceSpinner.setSelection(spaceOptions.lastIndex)
            customSpace.setText(savedSpace)
        }
        updateCustomPresetVisibility()
        updateCustomSpaceVisibility()
        updateVisibleFields()
    }

    override val inputForTasker: TaskerInput<RetuiTaskerInput>
        get() = TaskerInput(RetuiTaskerInput(selectedAction(), selectedPreset(), themeElement.text.toString(),
            value.text.toString(), module.text.toString(), text.text.toString(), selectedSpace()))

    private fun finishConfiguration() {
        val result = helper.finishForTasker()
        if (result is SimpleResultError) android.widget.Toast.makeText(this, result.message, android.widget.Toast.LENGTH_LONG).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            finishConfiguration()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val CUSTOM_PRESET = "Custom / Tasker variable"
        private const val CUSTOM_SPACE = "Custom / Tasker variable"
    }
}

private class SimpleItemSelectedListener(private val selected: () -> Unit) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = selected()
    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
}
