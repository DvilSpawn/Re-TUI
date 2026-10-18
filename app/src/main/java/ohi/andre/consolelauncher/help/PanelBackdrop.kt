package ohi.andre.consolelauncher.help

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import ohi.andre.consolelauncher.LauncherActivity
import java.util.function.Consumer

/** Blur only the layers behind a panel; the panel and its text stay sharp. */
internal object PanelBackdrop {
    fun install(host: FrameLayout) {
        var context: Context = host.context
        while (context is ContextWrapper && context !is Activity) context = context.baseContext
        val activity = context as? Activity ?: return
        if (activity is LauncherActivity) installEmbedded(host) else installWindow(host, activity)
    }

    internal fun installWindow(host: View, activity: Activity, window: android.view.Window = activity.window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setDimAmount(.38f)
        if (Build.VERSION.SDK_INT < 31) return
        val manager = activity.windowManager
        val listener = Consumer<Boolean> { enabled ->
            window.attributes = window.attributes.apply {
                blurBehindRadius = if (enabled) (18 * host.resources.displayMetrics.density).toInt() else 0
            }
            window.setDimAmount(if (enabled) .18f else .38f)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        var registered = false
        fun attach() {
            if (!registered) {
                manager.addCrossWindowBlurEnabledListener(activity.mainExecutor, listener)
                registered = true
            }
        }
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = attach()
            override fun onViewDetachedFromWindow(v: View) {
                if (registered) manager.removeCrossWindowBlurEnabledListener(listener)
                registered = false
            }
        })
        if (host.isAttachedToWindow) attach()
    }

    fun installEmbedded(host: ViewGroup) {
        val scrim = View(host.context).apply {
            setBackgroundColor(0x33000000)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
        }
        host.addView(scrim, 0, ViewGroup.LayoutParams(-1, -1))
        var active = false
        val blurred = mutableListOf<View>()
        fun clear() {
            if (Build.VERSION.SDK_INT >= 31) blurred.forEach { release(it) }
            blurred.clear()
            active = false
        }
        val observer = ViewTreeObserver.OnPreDrawListener {
            if (host.isShown != active) {
                if (!host.isShown) clear() else {
                    active = true
                    if (Build.VERSION.SDK_INT >= 31) {
                        var branch: View = host
                        var parent = branch.parent as? ViewGroup
                        while (parent != null) {
                            for (index in 0 until parent.indexOfChild(branch)) {
                                val sibling = parent.getChildAt(index)
                                val panelBounds = android.graphics.Rect()
                                val siblingBounds = android.graphics.Rect()
                                if (sibling.getGlobalVisibleRect(siblingBounds) && host.getGlobalVisibleRect(panelBounds) &&
                                    android.graphics.Rect.intersects(panelBounds, siblingBounds)) { acquire(sibling); blurred.add(sibling) }
                            }
                            branch = parent
                            parent = branch.parent as? ViewGroup
                        }
                    }
                }
            }
            true
        }
        host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { host.viewTreeObserver.addOnPreDrawListener(observer) }
            override fun onViewDetachedFromWindow(v: View) {
                if (host.viewTreeObserver.isAlive) host.viewTreeObserver.removeOnPreDrawListener(observer)
                clear()
            }
        })
        if (host.isAttachedToWindow) host.viewTreeObserver.addOnPreDrawListener(observer)
    }

    private val owners = java.util.WeakHashMap<View, Int>()
    @androidx.annotation.RequiresApi(31)
    private fun acquire(view: View) {
        val count = owners[view] ?: 0
        if (count == 0) {
            val radius = 18 * view.resources.displayMetrics.density
            view.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
        }
        owners[view] = count + 1
    }
    @androidx.annotation.RequiresApi(31)
    private fun release(view: View) {
        val count = owners[view] ?: return
        if (count == 1) { view.setRenderEffect(null); owners.remove(view) } else owners[view] = count - 1
    }
}
