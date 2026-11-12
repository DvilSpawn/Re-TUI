package ohi.andre.consolelauncher.managers.modules.home

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.ConfigurationCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.tuils.CalendarLabels
import ohi.andre.consolelauncher.tuils.Tuils
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

internal class CalendarModule : HomeModule {
    override val id = ModuleManager.CALENDAR
    override val usesThemeTypeface = true

    override fun show(host: HomeModuleHost) = host.renderText(id, monthText(host.context))

    override fun renderBody(host: HomeModuleHost, text: CharSequence?, body: TextView, scroll: ScrollView): Boolean {
        scroll.removeAllViews()
        body.visibility = View.GONE
        scroll.addView(host.buildCalendarModuleView())
        return true
    }

    private fun HomeModuleHost.buildCalendarModuleView(): View {
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val calendar = Calendar.getInstance(locale)
        val today = Calendar.getInstance(locale)
        val months = DateFormatSymbols(locale).shortMonths
        val weekdays = CalendarLabels.weekdays(calendar, locale)
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDay = CalendarLabels.leadingCells(calendar)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay =
            if (today.get(Calendar.MONTH) == month && today.get(Calendar.YEAR) == year) today.get(Calendar.DAY_OF_MONTH) else -1

        val content = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.CALENDAR)
        content.setOrientation(LinearLayout.VERTICAL)
        val calendarInset = Tuils.dpToPx(context, 2)
        content.setPadding(calendarInset, 0, calendarInset, 0)
        content.setLayoutParams(
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val title = calendarTextView(months[month] + " " + year, true, false)
        title.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        title.setPadding(0, 0, 0, 0)
        content.addView(title)

        val header = calendarRow()
        for (weekday in weekdays) {
            header.addView(calendarCell(weekday, true, false))
        }
        content.addView(header)

        var day = 1
        for (week in 0 until 6) {
            val row = calendarRow()
            for (dow in 0..6) {
                if ((week == 0 && dow < firstDay) || day > daysInMonth) {
                    row.addView(calendarCell("", false, false))
                } else {
                    row.addView(
                        calendarCell(
                            String.format(locale, "%02d", day),
                            false,
                            day == currentDay
                        )
                    )
                    day += 1
                }
            }
            content.addView(row)
            if (day > daysInMonth) {
                break
            }
        }
        addCommandButtons(content, listOf("today" to "module -show calendar", "timer" to "module -show timer"))
        return content
    }

    private fun HomeModuleHost.calendarRow(): LinearLayout {
        val row = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context,ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.GroupKind.CALENDAR_ROW)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setBaselineAligned(false)
        row.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return row
    }

    private fun HomeModuleHost.calendarCell(text: String, bold: Boolean, today: Boolean): TextView {
        val cell = calendarTextView(if (today) "[$text]" else text, bold || today, today)
        ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.calendarCell(cell)
        cell.setGravity(Gravity.CENTER)
        cell.setMinHeight(0)
        cell.setPadding(0, 0, 0, 0)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cell.setLayoutParams(lp)
        return cell
    }

    private fun HomeModuleHost.calendarTextView(text: String, bold: Boolean, highlight: Boolean): TextView {
        val view = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.calendarText(context)
        view.setText(text)
        view.setSingleLine(true)
        view.setIncludeFontPadding(false)
        view.setTextSize(bodyTextSize().toFloat())
        view.setTextColor(if (highlight) accentTextColor() else bodyTextColor())
        view.setTag(R.id.module_text_font_mode, UIManager.MODULE_TEXT_FONT_THEME)
        view.setTypeface(
            Tuils.getTypeface(context),
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.body(view,
            appearance(),true)
        return view
    }

    companion object {
        fun monthText(context: Context): String {
            val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
            val calendar = Calendar.getInstance(locale)
            val months = DateFormatSymbols(locale).shortMonths
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val firstDay = CalendarLabels.leadingCells(calendar)
            val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance(locale).get(Calendar.DAY_OF_MONTH)

            val out = StringBuilder()
            out.append(months[month]).append(' ').append(year).append('\n')
            val weekdays = CalendarLabels.weekdays(calendar, locale)
            val width = max(4, weekdays.maxOf { it.length } + 1)
            out.append(weekdays.joinToString("") { it.padEnd(width) }).append('\n')
            repeat(firstDay) { out.append(" ".repeat(width)) }
            for (day in 1..max) {
                val number = String.format(locale, "%02d", day)
                out.append((if (day == today) "[$number]" else number).padEnd(width))
                if ((firstDay + day) % 7 == 0) out.append('\n')
            }
            return out.toString()
        }
    }
}
