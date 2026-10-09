package ohi.andre.consolelauncher.community

import android.content.Context
import android.util.AttributeSet

/** Native editing/selection semantics with a caret notification for handle completion. */
class CommunityEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : android.widget.EditText(context, attrs) {
    var selectionChanged: (() -> Unit)? = null
    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end); selectionChanged?.invoke()
    }
}
