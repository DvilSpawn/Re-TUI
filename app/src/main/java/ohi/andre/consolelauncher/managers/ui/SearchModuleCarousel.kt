package ohi.andre.consolelauncher.managers.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.abs

/**
 * Search and Terminal modes' dock modules, in the results layer: the module you pick from the MODULES group opens
 * right under its chips, with the other groups and the search bar still there, over a blurred wallpaper (a dark
 * tint where Android cannot blur). Swiping sideways moves to the next or previous dock module. Modules are drawn by
 * the launcher's usual module code, into [content].
 */
class SearchModuleCarousel(
    private val activity: Activity,
    private val dock: () -> List<String>,
    private val render: (String, ViewGroup) -> Unit,
    // The results card redraws to place [view] under the MODULES chips and mark the open one.
    private val changed: () -> Unit,
    // [backToSearch]: return to the search the module was opened from (its input and keyboard).
    private val closed: (backToSearch: Boolean) -> Unit,
    // Launcher views drawn under the results (Terminal mode's tiles) that blur with the wallpaper.
    private val behind: () -> List<View>
) {
    private val context: Context = activity
    private fun dp(value: Int) = Tuils.dpToPx(context, value)
    /** Where the open module is drawn. */
    val content = FrameLayout(context)
    /** The module's place in the results card. */
    val view: View = SwipeFrame(context).apply {
        tag = "search-module"
        setPadding(0, dp(4), 0, dp(4))
        addView(content, FrameLayout.LayoutParams(-1, -2))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    var isOpen = false; private set
    var current: String? = null; private set
    private var blur: ValueAnimator? = null

    /** Opens [module] (or the last one shown); false when the dock is empty. */
    fun open(module: String? = null): Boolean {
        val ids = dock()
        if (ids.isEmpty()) return false
        val target = module?.takeIf { it in ids } ?: current?.takeIf { it in ids } ?: ids.first()
        if (!isOpen) {
            isOpen = true
            setBlur(true)
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(200).start()
        }
        show(target, 0)
        return true
    }

    /** Closes the module; the search stays, unless [backToSearch] is false because the search itself is going. */
    fun close(backToSearch: Boolean = true) {
        if (!isOpen) return
        isOpen = false
        setBlur(false)
        (view.parent as? ViewGroup)?.removeView(view)
        closed(backToSearch)
        changed()
    }

    /** Tapping the open module's chip closes it; another chip switches to that module. */
    fun toggle(module: String) { if (isOpen && module == current) close() else open(module) }

    /** The dock changed, or the open module asked to redraw. */
    fun refresh() {
        if (!isOpen) return
        val ids = dock()
        if (ids.isEmpty()) { close(); return }
        show(current?.takeIf { it in ids } ?: ids.first(), 0)
    }

    private fun step(direction: Int) {
        val ids = dock()
        if (ids.size < 2) { springBack(); return }
        val index = ids.indexOf(current).coerceAtLeast(0)
        show(ids[(index + direction + ids.size) % ids.size], direction)
    }

    private fun show(module: String, direction: Int) {
        current = module
        if (direction == 0) { render(module, content); content.translationX = 0f; content.alpha = 1f; changed(); return }
        val width = view.width.toFloat().coerceAtLeast(1f)
        content.animate().translationX(-direction * width).alpha(0f).setDuration(120).withEndAction {
            render(module, content); changed()
            content.translationX = direction * width
            content.animate().translationX(0f).alpha(1f).setDuration(160).start()
        }.start()
    }

    private fun springBack() { content.animate().translationX(0f).setDuration(140).start() }

    // Android 12+ blurs behind windows unless the phone turns it off (often in battery saver).
    private fun blurAvailable() = Build.VERSION.SDK_INT >= 31 && activity.windowManager.isCrossWindowBlurEnabled

    /** The wallpaper (and Terminal tiles) blur away while a module is open; a dim stands in where blur is unavailable. */
    private fun setBlur(on: Boolean) {
        val window = activity.window
        val canBlur = blurAvailable()
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or if (canBlur) WindowManager.LayoutParams.FLAG_BLUR_BEHIND else 0)
        val radius = dp(24)
        val dim = if (canBlur) 0.25f else 0.55f
        blur?.cancel()
        blur = ValueAnimator.ofFloat(if (on) 0f else 1f, if (on) 1f else 0f).setDuration(if (on) 240 else 180).apply {
            addUpdateListener { animation ->
                val f = animation.animatedValue as Float
                window.attributes = window.attributes.apply {
                    dimAmount = dim * f
                    if (canBlur && Build.VERSION.SDK_INT >= 31) blurBehindRadius = (radius * f).toInt()
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    val effect = if (f > 0.05f) RenderEffect.createBlurEffect(18f * f, 18f * f, Shader.TileMode.CLAMP) else null
                    behind().forEach { it.setRenderEffect(effect) }
                }
            }
            if (!on) addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isOpen) return
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            })
            start()
        }
    }

    /** Swiping sideways over the module moves to the next or previous one; vertical scrolling stays with the results. */
    private inner class SwipeFrame(context: Context) : FrameLayout(context) {
        private val slop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var swiping = false
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; swiping = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.x - downX; val dy = e.y - downY
                    if (abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.5f) {
                        swiping = true; parent?.requestDisallowInterceptTouchEvent(true); return true
                    }
                }
            }
            return false
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
                MotionEvent.ACTION_MOVE -> if (swiping) content.translationX = (e.x - downX) * 0.6f
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (swiping) {
                    swiping = false
                    val dx = e.x - downX
                    if (e.actionMasked == MotionEvent.ACTION_UP && abs(dx) > width / 5f) step(if (dx < 0) 1 else -1) else springBack()
                }
            }
            return true
        }
    }
}
