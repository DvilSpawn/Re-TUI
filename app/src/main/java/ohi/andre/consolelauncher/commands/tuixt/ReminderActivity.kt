package ohi.andre.consolelauncher.commands.tuixt

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dvil.retui.datetimepicker.RetuiDateTimePickerView
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.borderColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.rect
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleListItem
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.surfaceColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.textColor
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager.Reminder
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        applyFullscreen(this)

        val screen = FrameLayout(this).apply { setBackgroundColor(overlayColor()); fitsSystemWindows = true }
        val host = addFoldAwareHost(this, screen, ViewGroup.LayoutParams.MATCH_PARENT)
        val shell = FrameLayout(this)
        host.addView(shell, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            setMargins(dp(this@ReminderActivity, 28f), dp(this@ReminderActivity, 28f), dp(this@ReminderActivity, 28f), dp(this@ReminderActivity, 28f))
        })

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(this@ReminderActivity, 14f), dp(this@ReminderActivity, 50f), dp(this@ReminderActivity, 14f), dp(this@ReminderActivity, 14f))
            stylePanel(this@ReminderActivity, this)
        }
        shell.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(this@ReminderActivity, 11f) })
        shell.addView(TextView(this).apply { text = "Reminders"; styleHeader(this@ReminderActivity, this) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { leftMargin = dp(this@ReminderActivity, 38f) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 360f)))
        panel.addView(button("ADD REMINDER", true).apply { setOnClickListener { edit(null) } })
        panel.addView(button("CLOSE", false).apply { setOnClickListener { finish() } })
        setContentView(screen)
        render()
    }

    override fun onResume() { super.onResume(); applyFullscreen(this); render() }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val reminders = ReminderManager.list(this)
        if (reminders.isEmpty()) list.addView(TextView(this).apply { text = "No reminders."; setTextColor(accentColor()); typeface = Tuils.getTypeface(this@ReminderActivity) })
        reminders.forEachIndexed { index, reminder ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = "${index + 1}. ${reminder.title}\n${ReminderManager.formatWhen(reminder.atMillis)}"; setTextColor(accentColor()); typeface = Tuils.getTypeface(this@ReminderActivity) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button("EDIT", false).apply { setOnClickListener { edit(reminder) } })
            row.addView(button("REMOVE", false).apply { setOnClickListener { confirmRemove(reminder) } })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(this@ReminderActivity, 10f) })
        }
    }

    private fun edit(reminder: Reminder?) {
        val selected = Calendar.getInstance().apply { timeInMillis = reminder?.atMillis ?: System.currentTimeMillis() + 60 * 60 * 1000 }
        TuixtDialog.showCustom(this, if (reminder == null) "Add reminder" else "Edit reminder", TuixtDialog.ContentFactory { dialog: Dialog? ->
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = EditText(this).apply { hint = "Task name"; setText(reminder?.title.orEmpty()); styleInput(this@ReminderActivity, this) }
            val whenView = TextView(this).apply {
                styleListItem(this@ReminderActivity, this, false)
                textSize = 13f
                minHeight = title.minimumHeight
                setPadding(dp(this@ReminderActivity, 10f), dp(this@ReminderActivity, 8f), dp(this@ReminderActivity, 10f), dp(this@ReminderActivity, 8f))
                text = formatPickerValue(selected)
                setOnClickListener {
                    (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(title.windowToken, 0)
                    pickDateTime(selected) { text = formatPickerValue(selected) }
                }
            }
            val error = TextView(this).apply { setTextColor(textColor()); typeface = Tuils.getTypeface(this@ReminderActivity); visibility = View.GONE }
            content.addView(title)
            content.addView(TextView(this).apply { text = "DATE / TIME"; setTextColor(textColor()); typeface = Tuils.getTypeface(this@ReminderActivity) })
            content.addView(whenView)
            content.addView(error)
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(this@ReminderActivity, 12f), 0, 0)
                addView(button("CANCEL", false).apply { setOnClickListener { dialog?.dismiss() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(button("SAVE", true).apply { setOnClickListener {
                    val cleanTitle = title.text.toString().trim()
                    if (cleanTitle.isEmpty() || selected.timeInMillis <= System.currentTimeMillis()) {
                        error.text = if (cleanTitle.isEmpty()) "Task name cannot be empty." else "Reminder time must be in the future."
                        error.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    dialog?.dismiss()
                    if (reminder == null) ReminderManager.add(this@ReminderActivity, cleanTitle, selected.timeInMillis)
                    else ReminderManager.save(this@ReminderActivity, Reminder(reminder.id, cleanTitle, selected.timeInMillis))
                    changed()
                } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            })
            content
        })
    }

    private fun pickDateTime(selected: Calendar, done: () -> Unit) {
        val picker = RetuiDateTimePickerView(this, selected.timeInMillis, System.currentTimeMillis(), object : RetuiDateTimePickerView.Theme {
            override fun styleLabel(view: TextView) {
                view.setTextColor(textColor()); view.typeface = Tuils.getTypeface(this@ReminderActivity)
            }

            override fun styleControl(view: TextView, selected: Boolean) {
                styleButton(this@ReminderActivity, view, selected); view.gravity = Gravity.CENTER
            }

            override fun styleDropdown(view: TextView) {
                styleListItem(this@ReminderActivity, view, false)
            }

            override fun styleDay(view: TextView, selected: Boolean, enabled: Boolean) {
                styleListItem(this@ReminderActivity, view, selected)
                view.setTextColor(accentColor()); view.setPadding(0, 0, 0, 0); view.minHeight = 0; view.gravity = Gravity.CENTER
                view.background = if (selected) view.background else null
                view.alpha = if (enabled || view.text.singleOrNull()?.isLetter() == true) 1f else 0.35f
            }

            override fun dropdownBackground() = rect(this@ReminderActivity, surfaceColor(), borderColor(), 1.25f)
        })
        TuixtDialog.showContent(this, "Pick date / time", picker, "Use", "Cancel", TuixtDialog.ConfirmAction {
            selected.timeInMillis = picker.selectedTimeMillis()
            selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
            done()
        })
    }

    private fun formatPickerValue(value: Calendar): String = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(value.time)

    private fun confirmRemove(reminder: Reminder) {
        TuixtDialog.showConfirm(this, "Remove reminder?", reminder.title, "Remove", "Cancel",
            TuixtDialog.ConfirmAction { ReminderManager.remove(this, reminder.id); changed() })
    }

    private fun changed() {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(UIManager.ACTION_MODULE_COMMAND).putExtra(UIManager.EXTRA_MODULE_COMMAND, "update").putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.REMINDER))
        render()
    }

    private fun button(label: String, primary: Boolean) = TextView(this).apply { text = label; styleButton(this@ReminderActivity, this, primary) }
}
