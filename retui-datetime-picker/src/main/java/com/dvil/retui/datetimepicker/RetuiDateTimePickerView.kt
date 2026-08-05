package com.dvil.retui.datetimepicker

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

class RetuiDateTimePickerView(
    context: Context,
    initialTimeMillis: Long,
    minimumTimeMillis: Long,
    private val theme: Theme
) : LinearLayout(context) {
    interface Theme {
        fun styleLabel(view: TextView)
        fun styleControl(view: TextView, selected: Boolean)
        fun styleDropdown(view: TextView)
        fun styleDay(view: TextView, selected: Boolean, enabled: Boolean)
        fun dropdownBackground(): Drawable
    }

    private val value = Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
    private val minimum = Calendar.getInstance().apply {
        timeInMillis = minimumTimeMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    init { keepCurrentOrFuture() }
    private val monthNames = DateFormatSymbols(Locale.US).months.take(12)
    private val month = dropdown(validMonthNames(), monthNames[value.get(Calendar.MONTH)], false) {
        value.set(Calendar.DAY_OF_MONTH, 1)
        value.set(Calendar.MONTH, monthNames.indexOf(it))
        keepCurrentOrFuture()
        renderCalendar()
    }
    private val year = dropdown(
        (minimum.get(Calendar.YEAR)..minimum.get(Calendar.YEAR) + 20).map(Int::toString),
        value.get(Calendar.YEAR).toString(),
        true
    ) { applyYear(it) }
    private val calendarGrid = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(month, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))
            addView(year, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(calendarGrid)
        addView(timeRow())
        year.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = applyYear(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        renderCalendar()
    }

    fun selectedTimeMillis(): Long = value.timeInMillis

    private fun timeRow(): LinearLayout {
        val hour = dropdown((1..12).map(Int::toString), displayHour(), false) {
            value.set(Calendar.HOUR, (it.toInt() % 12))
        }
        val minute = dropdown((0..59).map { it.toString().padStart(2, '0') }, value.get(Calendar.MINUTE).toString().padStart(2, '0'), false) {
            value.set(Calendar.MINUTE, it.toInt())
        }
        val period = dropdown(listOf("AM", "PM"), if (value.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM", false) {
            value.set(Calendar.AM_PM, if (it == "AM") Calendar.AM else Calendar.PM)
        }
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, dp(8), 0, 0)
            addLabeled("HOUR", hour)
            addLabeled("MINUTE", minute)
            addLabeled("AM / PM", period)
        }
    }

    private fun LinearLayout.addLabeled(label: String, control: AutoCompleteTextView) {
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(TextView(context).apply { text = label; gravity = Gravity.CENTER; theme.styleLabel(this) })
            addView(control)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dropdown(items: List<String>, current: String, editable: Boolean, selected: (String) -> Unit): AutoCompleteTextView {
        val view = AutoCompleteTextView(context).apply {
            setText(current, false)
            threshold = 0
            dropDownHeight = dp(320)
            setDropDownBackgroundDrawable(theme.dropdownBackground())
            inputType = if (editable) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_NULL
            theme.styleControl(this, false)
            setAdapter(dropdownAdapter(items))
            setOnItemClickListener { parent, _, position, _ -> selected(parent.getItemAtPosition(position) as String) }
            setOnClickListener { showDropDown() }
        }
        return view
    }

    private fun applyYear(text: String) {
        val picked = text.toIntOrNull() ?: return
        if (picked !in minimum.get(Calendar.YEAR)..9999) return
        value.set(Calendar.DAY_OF_MONTH, 1)
        value.set(Calendar.YEAR, picked)
        keepCurrentOrFuture()
        refreshMonthDropdown()
        renderCalendar()
    }

    private fun validMonthNames(): List<String> = if (sameYear(value, minimum)) {
        monthNames.drop(minimum.get(Calendar.MONTH))
    } else {
        monthNames
    }

    private fun refreshMonthDropdown() {
        month.setAdapter(dropdownAdapter(validMonthNames()))
        month.setText(monthNames[value.get(Calendar.MONTH)], false)
    }

    private fun dropdownAdapter(items: List<String>) =
        object : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View =
                (convertView as? TextView ?: TextView(context)).apply {
                    text = getItem(position)
                    theme.styleDropdown(this)
                }
        }

    private fun keepCurrentOrFuture() {
        if (value.get(Calendar.YEAR) < minimum.get(Calendar.YEAR)) value.set(Calendar.YEAR, minimum.get(Calendar.YEAR))
        if (sameYear(value, minimum) && value.get(Calendar.MONTH) < minimum.get(Calendar.MONTH)) value.set(Calendar.MONTH, minimum.get(Calendar.MONTH))
        if (sameMonth(value, minimum) && value.get(Calendar.DAY_OF_MONTH) < minimum.get(Calendar.DAY_OF_MONTH)) value.set(Calendar.DAY_OF_MONTH, minimum.get(Calendar.DAY_OF_MONTH))
    }

    private fun renderCalendar() {
        calendarGrid.removeAllViews()
        val cursor = (value.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val cells = mutableListOf<Pair<String, Int?>>()
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { cells += it to null }
        repeat(cursor.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY) { cells += "" to null }
        repeat(cursor.getActualMaximum(Calendar.DAY_OF_MONTH)) { cells += (it + 1).toString() to it + 1 }
        while (cells.size % 7 != 0) cells += "" to null
        cells.chunked(7).forEach { week ->
            calendarGrid.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                week.forEach { (label, day) ->
                    val candidate = (cursor.clone() as Calendar).apply { if (day != null) set(Calendar.DAY_OF_MONTH, day) }
                    val enabled = day != null && candidate.timeInMillis >= minimum.timeInMillis
                    addView(TextView(context).apply {
                        text = label
                        gravity = Gravity.CENTER
                        isEnabled = enabled
                        theme.styleDay(this, day == value.get(Calendar.DAY_OF_MONTH) && enabled, enabled)
                        if (enabled) setOnClickListener { value.set(Calendar.DAY_OF_MONTH, day!!); renderCalendar() }
                    }, LayoutParams(0, dp(42), 1f))
                }
            })
        }
    }

    private fun displayHour(): String = value.get(Calendar.HOUR).let { if (it == 0) 12 else it }.toString()
    private fun sameYear(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
    private fun sameMonth(a: Calendar, b: Calendar) = sameYear(a, b) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
