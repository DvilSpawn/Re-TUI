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
import android.text.format.DateFormat
import ohi.andre.consolelauncher.R
import java.util.Calendar

class ReminderActivity : ohi.andre.consolelauncher.localization.LocalizedActivity() {
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
        shell.addView(TextView(this).apply { text = getString(R.string.reminder_reminders); styleHeader(this@ReminderActivity, this) }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { leftMargin = dp(this@ReminderActivity, 38f) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 360f)))
        panel.addView(button(getString(R.string.reminder_add_reminder), true).apply { setOnClickListener { edit(null) } })
        panel.addView(button(getString(R.string.reminder_close), false).apply { setOnClickListener { finish() } })
        setContentView(screen)
        render()
    }

    override fun onResume() { super.onResume(); applyFullscreen(this); render() }

    private fun render() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val reminders = ReminderManager.list(this)
        if (reminders.isEmpty()) list.addView(TextView(this).apply { text = getString(R.string.reminder_no_reminders); setTextColor(accentColor()); typeface = Tuils.getTypeface(this@ReminderActivity) })
        reminders.forEachIndexed { index, reminder ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = getString(R.string.reminder_list_entry, index + 1, reminder.title, ReminderManager.formatWhen(this@ReminderActivity, reminder.atMillis)); setTextColor(accentColor()); typeface = Tuils.getTypeface(this@ReminderActivity) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button(getString(R.string.reminder_edit), false).apply { setOnClickListener { edit(reminder) } })
            row.addView(button(getString(R.string.reminder_remove), false).apply { setOnClickListener { confirmRemove(reminder) } })
            list.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(this@ReminderActivity, 10f) })
        }
    }

    private fun edit(reminder: Reminder?) {
        val selected = Calendar.getInstance().apply { timeInMillis = reminder?.atMillis ?: System.currentTimeMillis() + 60 * 60 * 1000 }
        TuixtDialog.showCustom(this, if (reminder == null) getString(R.string.reminder_add_title) else getString(R.string.reminder_edit_reminder), TuixtDialog.ContentFactory { dialog: Dialog? ->
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = EditText(this).apply { hint = getString(R.string.reminder_task_name); setText(reminder?.title.orEmpty()); styleInput(this@ReminderActivity, this) }
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
            content.addView(TextView(this).apply { text = getString(R.string.reminder_date_time); setTextColor(textColor()); typeface = Tuils.getTypeface(this@ReminderActivity) })
            content.addView(whenView)
            content.addView(error)
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(this@ReminderActivity, 12f), 0, 0)
                addView(button(getString(R.string.reminder_cancel), false).apply { setOnClickListener { dialog?.dismiss() } }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(button(getString(R.string.reminder_save), true).apply { setOnClickListener {
                    val cleanTitle = title.text.toString().trim()
                    if (cleanTitle.isEmpty() || selected.timeInMillis <= System.currentTimeMillis()) {
                        error.text = if (cleanTitle.isEmpty()) getString(R.string.reminder_task_name_cannot_be_empty) else getString(R.string.reminder_reminder_time_must_be_in_the_future)
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
                view.alpha = if (enabled || view.text.any { it.isLetter() }) 1f else 0.35f
            }

            override fun dropdownBackground() = rect(this@ReminderActivity, surfaceColor(), borderColor(), 1.25f)
        })
        TuixtDialog.showContent(this, getString(R.string.reminder_pick_date_time), picker, getString(R.string.reminder_use), getString(R.string.reminder_cancel_label), TuixtDialog.ConfirmAction {
            selected.timeInMillis = picker.selectedTimeMillis()
            selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
            done()
        })
    }

    private fun formatPickerValue(value: Calendar): String = DateFormat.getDateFormat(this).format(value.time) + " " + DateFormat.getTimeFormat(this).format(value.time)

    private fun confirmRemove(reminder: Reminder) {
        TuixtDialog.showConfirm(this, getString(R.string.reminder_remove_reminder), reminder.title, getString(R.string.reminder_remove_label), getString(R.string.reminder_cancel_label),
            TuixtDialog.ConfirmAction { ReminderManager.remove(this, reminder.id); changed() })
    }

    private fun changed() {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(UIManager.ACTION_MODULE_COMMAND).putExtra(UIManager.EXTRA_MODULE_COMMAND, "update").putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.REMINDER))
        render()
    }

    private fun button(label: String, primary: Boolean) = TextView(this).apply { text = label; styleButton(this@ReminderActivity, this, primary) }
}
