package ohi.andre.consolelauncher

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Fades the whole launcher, including backgrounds and surfaces added by new modes. */
class BreathOutLayout(context: Context) : FrameLayout(context) {
    private var enabled = false
    private var timeoutMs = 900_000L
    private var active = false
    private var hidden = false
    private var touching = false
    private var wakingTouch = false
    private var wakingKey: Int? = null
    private val editors = mutableMapOf<EditText, TextWatcher>()
    private val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    private val explorationListener = AccessibilityManager.TouchExplorationStateChangeListener {
        if (it) restore(false)
        restartTimeout()
    }
    private val editorLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener { observeEditors() }
    private val fade = Runnable {
        if (enabled && active && !touching && !accessibility.isTouchExplorationEnabled) {
            hidden = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            ViewCompat.getWindowInsetsController(this)?.hide(WindowInsetsCompat.Type.ime())
            animate().cancel()
            animate().alpha(0f).setDuration(300L).start()
        }
    }

    fun configure(enabled: Boolean, seconds: Int) {
        this.enabled = enabled
        // Invalid hand-edited values fall back to the default; widen before converting.
        timeoutMs = (if (seconds > 0) seconds else 900).toLong() * 1000L
        restore(false)
        observeEditors()
        restartTimeout()
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        restore(false)
        restartTimeout()
    }

    /** Returns true when this interaction only wakes the launcher. */
    fun wake(): Boolean {
        val wasHidden = hidden
        if (hidden) restore(true)
        restartTimeout()
        return wasHidden
    }

    private fun restore(animated: Boolean) {
        hidden = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_AUTO
        animate().cancel()
        if (animated) animate().alpha(1f).setDuration(250L).start() else alpha = 1f
    }

    private fun restartTimeout() {
        removeCallbacks(fade)
        if (enabled && active && !hidden && !touching && !accessibility.isTouchExplorationEnabled) {
            postDelayed(fade, timeoutMs)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            wakingTouch = hidden
            if (hidden) restore(true)
            touching = true
            removeCallbacks(fade)
        }
        val consume = wakingTouch || hidden
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            wakingTouch = false
            restartTimeout()
        }
        return if (consume) true else super.dispatchTouchEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && hidden) {
            wakingKey = event.keyCode
            wake()
        }
        if (event.keyCode == wakingKey) {
            if (event.action == KeyEvent.ACTION_UP) wakingKey = null
            return true
        }
        restartTimeout()
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (wake()) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun observeEditors() {
        val current = mutableSetOf<EditText>()
        fun visit(view: View) {
            if (view is EditText) current.add(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        if (enabled) visit(this)
        val iterator = editors.iterator()
        while (iterator.hasNext()) {
            val (editor, watcher) = iterator.next()
            if (editor !in current) {
                editor.removeTextChangedListener(watcher)
                iterator.remove()
            }
        }
        for (editor in current) {
            if (editor in editors) continue
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    // IME edits do not dispatch touch/key events through the activity.
                    if (editor.isShown) restartTimeout()
                }
            }
            editor.addTextChangedListener(watcher)
            editors[editor] = watcher
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(editorLayoutListener)
        accessibility.addTouchExplorationStateChangeListener(explorationListener)
        observeEditors()
        restartTimeout()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(fade)
        animate().cancel()
        viewTreeObserver.removeOnGlobalLayoutListener(editorLayoutListener)
        accessibility.removeTouchExplorationStateChangeListener(explorationListener)
        editors.forEach { (editor, watcher) -> editor.removeTextChangedListener(watcher) }
        editors.clear()
        super.onDetachedFromWindow()
    }
}
