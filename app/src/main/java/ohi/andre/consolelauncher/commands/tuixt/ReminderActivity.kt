package ohi.andre.consolelauncher.commands.tuixt

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
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
import ohi.andre.consolelauncher.commands.tuixt.TuixtLayout.addFoldAwareHost
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.dp
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.accentColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.overlayColor
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleButton
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleHeader
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.styleInput
import ohi.andre.consolelauncher.commands.tuixt.TuixtTheme.stylePanel
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager.Reminder
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Calendar

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
        val input = EditText(this).apply { hint = "Task name"; setText(reminder?.title.orEmpty()); styleInput(this@ReminderActivity, this) }
        AlertDialog.Builder(this).setTitle(if (reminder == null) "Add reminder" else "Edit reminder").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Next") { _, _ ->
                val title = input.text.toString().trim()
                if (title.isEmpty()) Toast.makeText(this, "Task name cannot be empty.", Toast.LENGTH_SHORT).show()
                else pickDate(reminder, title)
            }.show()
    }

    private fun pickDate(reminder: Reminder?, title: String) {
        val selected = Calendar.getInstance().apply { reminder?.let { timeInMillis = it.atMillis } }
        DatePickerDialog(this, { _, year, month, day ->
            selected.set(year, month, day)
            TimePickerDialog(this, { _, hour, minute ->
                selected.set(Calendar.HOUR_OF_DAY, hour); selected.set(Calendar.MINUTE, minute); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0)
                if (selected.timeInMillis > System.currentTimeMillis()) {
                    if (reminder == null) ReminderManager.add(this, title, selected.timeInMillis)
                    else ReminderManager.save(this, Reminder(reminder.id, title, selected.timeInMillis))
                    changed()
                } else Toast.makeText(this, "Reminder time must be in the future.", Toast.LENGTH_SHORT).show()
            }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show()
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun confirmRemove(reminder: Reminder) {
        AlertDialog.Builder(this).setTitle("Remove reminder?").setMessage(reminder.title)
            .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ -> ReminderManager.remove(this, reminder.id); changed() }.show()
    }

    private fun changed() {
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(Intent(UIManager.ACTION_MODULE_COMMAND).putExtra(UIManager.EXTRA_MODULE_COMMAND, "update").putExtra(UIManager.EXTRA_MODULE_NAME, ModuleManager.REMINDER))
        render()
    }

    private fun button(label: String, primary: Boolean) = TextView(this).apply { text = label; styleButton(this@ReminderActivity, this, primary) }
}
