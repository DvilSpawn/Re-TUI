package ohi.andre.consolelauncher.managers.termux

import android.view.View
import android.view.Gravity
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.abs

/** Shared key rows for the workspace and home; callers own focus and modifier state. */
object TermuxSpecialKeys {
    fun bind(root: View, fnMode: Boolean, modifiers: MutableSet<String>, textColor: Int,
             changed: (Boolean) -> Unit, refresh: () -> Unit, sendKey: (String) -> Unit) {
        val nav = listOf("REFRESH", "ESC", "/", "-", "HOME", "↑", "END", "PGUP", "TAB", "CTRL", "ALT", "SHIFT", "←", "↓", "→", "PGDN")
        val fn = listOf("REFRESH", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "BTAB", "INS", "DEL", "ENTER", "BKSP")
        val names = mapOf("ESC" to "Escape", "HOME" to "Home", "END" to "End", "↑" to "Up", "↓" to "Down", "←" to "Left", "→" to "Right", "PGUP" to "PageUp", "PGDN" to "PageDown", "TAB" to "Tab", "BTAB" to "BTab", "INS" to "Insert", "DEL" to "Delete", "ENTER" to "Enter", "BKSP" to "BSpace")
        val modifierNames = mapOf("CTRL" to "C", "ALT" to "M", "SHIFT" to "S")
        val specs = if (root.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) fn + nav else if (fnMode) fn else nav
        val count = specs.size / 2
        for ((rowIndex, rowId) in listOf(R.id.termux_workspace_keys_row_one, R.id.termux_workspace_keys_row_two).withIndex()) {
            val row = root.findViewById<LinearLayout>(rowId); row.removeAllViews()
            for (index in 0 until count) {
                val name = specs[rowIndex * count + index]
                val key = TextView(root.context).apply {
                    text = name + if (modifierNames[name] in modifiers) "*" else ""
                    gravity = Gravity.CENTER; typeface = Tuils.getTypeface(root.context); textSize = 10f
                    setTextColor(textColor)
                    setOnClickListener {
                        val modifier = modifierNames[name]
                        if (modifier != null) { if (!modifiers.add(modifier)) modifiers.remove(modifier); changed(fnMode) }
                        else if (name == "REFRESH") { refresh() }
                        else sendKey(names[name] ?: name)
                    }
                }
                var down = 0f
                key.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) down = event.x
                    if (event.actionMasked == MotionEvent.ACTION_UP && abs(event.x - down) > (48 * root.resources.displayMetrics.density)) {
                        changed(!fnMode); true
                    } else false
                }
                row.addView(key, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
    }
}
