package ohi.andre.consolelauncher.help

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import ohi.andre.consolelauncher.tuils.Tuils

/** Only the lowest keypad row yields to corners; narrow windows scroll instead of shrinking keys. */
internal class BottomCornerRow(context: Context) : HorizontalScrollView(context) {
    private val keys = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        addView(keys, ViewGroup.LayoutParams(-1, -1))
    }
    fun addKey(key: View, weight: Float = 1f) {
        keys.addView(key, LinearLayout.LayoutParams(Tuils.dpToPx(context, 48), -1, weight))
    }
}
