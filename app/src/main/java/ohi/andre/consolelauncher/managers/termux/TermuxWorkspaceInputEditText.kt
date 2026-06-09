package ohi.andre.consolelauncher.managers.termux

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText

class TermuxWorkspaceInputEditText : AppCompatEditText {
    private var backspaceListener: (() -> Unit)? = null
    private var textListener: ((String) -> Unit)? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun setBackspaceListener(listener: (() -> Unit)?) {
        backspaceListener = listener
    }

    fun setTextListener(listener: ((String) -> Unit)?) {
        textListener = listener
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!text.isNullOrEmpty()) {
                    textListener?.invoke(text.toString())
                    return true
                }
                return super.commitText(text, newCursorPosition)
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                dispatchBackspaceIfNeeded(beforeLength, afterLength)
                return super.deleteSurroundingText(beforeLength, afterLength)
            }

            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
                dispatchBackspaceIfNeeded(beforeLength, afterLength)
                return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    backspaceListener?.invoke()
                    return true
                }
                if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                    val unicodeChar = event.unicodeChar
                    if (unicodeChar > 0) {
                        textListener?.invoke(String(Character.toChars(unicodeChar)))
                        return true
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }

    private fun dispatchBackspaceIfNeeded(beforeLength: Int, afterLength: Int) {
        if (beforeLength > 0 || afterLength > 0) {
            backspaceListener?.invoke()
        }
    }
}
