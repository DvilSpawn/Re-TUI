package ohi.andre.consolelauncher.presentation

import android.app.Activity
import android.graphics.Rect
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.window.java.layout.WindowInfoTrackerCallbackAdapter
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import kotlin.math.max
import kotlin.math.min

/**
 * Two screens, or one folding screen, for the homes that draw themselves (Mono and Dot). They split the way
 * Classic's landscape layout does: in a wide window, or whenever the hinge runs top to bottom through the middle,
 * such as a book-style foldable held upright. The gap follows the hinge the system reports, else
 * `landscape_fold_gutter_mm`. Sides follow Classic's `duo` choices, so switching sides anywhere moves every mode.
 */
object DualScreen {
    /** The gap between the two panes, in a view's own coordinates: the panes are [0, start) and [end, width). */
    data class Split(val start: Int, val end: Int)

    sealed class Layout {
        /** One screen: the usual phone layout. */
        object Phone : Layout()
        /** Both panes in use; [inputSide] holds the command line or search. */
        data class Both(val split: Split, val inputSide: String) : Layout()
        /** Everything on [side], as `duo left` or `duo right` asked; the other pane stays empty. */
        data class OneSide(val split: Split, val side: String) : Layout()
    }

    const val LEFT = UIManager.DUO_LAYOUT_LEFT
    const val RIGHT = UIManager.DUO_LAYOUT_RIGHT
    private const val WIDE_ASPECT = 1.15f

    /** Applies a `duo left`/`duo right` change; the launcher sets it while its home is up. */
    var setDuoSide: ((String) -> Unit)? = null

    /** Extra room the launcher leaves at the root's sides, so a split home's search layout keeps to one screen. */
    var rootPadLeft = 0
    var rootPadRight = 0

    /** A vertical fold, in window coordinates, while one crosses the window. */
    private var hinge: Rect? = null
    private val listeners = LinkedHashSet<() -> Unit>()
    private var tracker: WindowInfoTrackerCallbackAdapter? = null
    private val consumer = Consumer<WindowLayoutInfo> { info ->
        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>()
            .firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
        val next = fold?.bounds?.let { Rect(it) }
        if (next != hinge) { hinge = next; changed() }
    }

    fun start(activity: Activity) {
        if (tracker != null) return
        tracker = runCatching { WindowInfoTrackerCallbackAdapter(WindowInfoTracker.getOrCreate(activity)) }.getOrNull()
        runCatching { tracker?.addWindowLayoutInfoListener(activity, ContextCompat.getMainExecutor(activity), consumer) }
    }

    fun stop() {
        runCatching { tracker?.removeWindowLayoutInfoListener(consumer) }
        tracker = null
        hinge = null
    }

    fun listen(listener: () -> Unit) { listeners += listener }
    fun unlisten(listener: () -> Unit) { listeners -= listener }
    fun changed() = listeners.toList().forEach { it() }

    fun layout(view: View): Layout {
        val split = split(view) ?: return Layout.Phone
        val oneSide = UIManager.savedDuoLayout(view.context)
        return if (oneSide != UIManager.DUO_LAYOUT_OFF) Layout.OneSide(split, oneSide)
        else Layout.Both(split, UIManager.savedInputSide(view.context))
    }

    /** Moves the input, or with `duo` on everything, to the other screen. */
    fun swap(view: View, layout: Layout) {
        when (layout) {
            is Layout.OneSide -> setDuoSide?.invoke(other(layout.side))
            is Layout.Both -> { UIManager.saveInputSide(view.context, other(layout.inputSide)); changed() }
            Layout.Phone -> Unit
        }
    }

    fun other(side: String) = if (side == LEFT) RIGHT else LEFT

    /** Below this smallest width a screen is a phone, as Android's own layout guidance has it. */
    private const val TABLET_SMALLEST_DP = 600

    /** A phone-sized screen with no hinge: Mono and Dot keep one screen on it, even held sideways. */
    fun isPhone(view: View): Boolean = hinge == null && view.resources.configuration.smallestScreenWidthDp < TABLET_SMALLEST_DP

    /** A phone held sideways: Mono shows each page's panels in two halves side by side. */
    fun splitsPages(view: View): Boolean {
        if (!isPhone(view)) return false
        val width = view.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
        val height = view.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels
        return height > 0 && width >= height * WIDE_ASPECT
    }

    /** The two panes of [view], or null when it should keep the phone layout. */
    fun split(view: View): Split? {
        val width = view.width
        if (width <= 0) return null
        val gutter = gutter(view)
        hinge?.let { fold ->
            val at = IntArray(2).also { view.getLocationInWindow(it) }
            val start = fold.left - at[0]
            val end = fold.right - at[0]
            // Only a hinge through the middle part of this view splits it.
            if (start > width / 4 && end < width * 3 / 4) {
                val middle = (start + end) / 2
                return Split(min(start, middle - gutter / 2), max(end, middle + (gutter + 1) / 2))
            }
        }
        if (LauncherSettings.getInt(Behavior.orientation) == 1) return null
        // A phone held sideways stays one screen; Mono splits its pages instead ([splitsPages]).
        if (isPhone(view)) return null
        // Classic's wide-window rule, read from the view itself: the configuration can lag a resize.
        if (view.height <= 0 || width < view.height * WIDE_ASPECT) return null
        // No hinge: the middle of what is usable, past the camera cutout and system bars at the sides.
        val (insetLeft, insetRight) = sideInsets(view)
        val middle = (insetLeft + width - insetRight) / 2
        return Split(middle - gutter / 2, middle + (gutter + 1) / 2)
    }

    /**
     * How far the camera cutout and system bars reach into [view] at its left and right, which no pane should run
     * under. Measured against where the view sits, so a view already inset by its parent gets only what is left.
     */
    fun sideInsets(view: View): Pair<Int, Int> {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            ?: return 0 to 0
        val at = IntArray(2).also(view::getLocationInWindow)
        val windowWidth = view.rootView.width.takeIf { it > 0 } ?: return insets.left to insets.right
        val left = (insets.left - at[0]).coerceAtLeast(0)
        val right = (insets.right - (windowWidth - at[0] - view.width)).coerceAtLeast(0)
        return left to right
    }

    private fun gutter(view: View): Int {
        val mm = LauncherSettings.getInt(Ui.landscape_fold_gutter_mm).coerceIn(0, UIManager.MAX_LANDSCAPE_FOLD_GUTTER_MM)
        return if (mm == 0) 0 else min(Tuils.mmToPx(view.resources.displayMetrics, mm), view.width / 3)
    }
}
