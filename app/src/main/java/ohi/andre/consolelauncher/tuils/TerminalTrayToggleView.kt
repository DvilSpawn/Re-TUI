package ohi.andre.consolelauncher.tuils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ReplacementSpan
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.presentation.HomeAppearance
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import kotlin.math.roundToInt

/**
 * Stateless chrome/text helper for the terminal output tray toggle.
 *
 * UIManager still owns tray mode, expansion state, landscape state, and the click
 * behavior. This helper only applies the visual state to the TextView.
 */
object TerminalTrayToggleView {
    internal data class TextState(val landscape:Boolean,val expanded:Boolean,val native:Boolean,val auto:Boolean,val autoHide:Boolean)
    internal data class Binding(val none:Boolean,val arrowsOnly:Boolean,val state:TextState?=null)

    fun style(
        context: Context,
        toggle: TextView?,
        terminalOutputBorder: View?,
        outputHeaderNone: Boolean,
        outputHeaderArrowsOnly: Boolean,
        outputColor: Int,
        outputHeaderTextSize: Int,
        tabBackgroundColor: Int,
        onClick: () -> Unit
    ) {
        if (toggle == null) return
        val source=toggle.getTag(R.id.home_output_header_binding) as? Binding
        toggle.setTag(R.id.home_output_header_binding,Binding(outputHeaderNone,outputHeaderArrowsOnly,source?.state))
        if (outputHeaderNone) { hide(toggle);return }
        renderStyle(context,toggle,outputHeaderNone,outputHeaderArrowsOnly,outputColor,
            outputHeaderTextSize.toFloat(),Tuils.getTypeface(context),tabBackgroundColor,
            AppearanceSettings.terminalHeaderTabBorderColor(),HomeAppearance.saved())
        TerminalBorderRuntime.bind(terminalOutputBorder,toggle)
        toggle.setOnClickListener { onClick() }
    }

    private fun renderStyle(context:Context,toggle:TextView,none:Boolean,arrows:Boolean,color:Int,
                            textSize:Float,font:Typeface?,background:Int,border:Int,appearance:HomeAppearance?=null) {
        if (none) { hide(toggle); return }
        toggle.visibility=View.VISIBLE
        toggle.setTextColor(color)
        toggle.setTypeface(font,Typeface.BOLD)
        LauncherFontScale.setBaseSize(toggle,textSize,appearance?.number(Ui.font_size_offset)?.toInt()?:0)
        toggle.gravity=Gravity.CENTER
        toggle.minWidth=Tuils.dpToPx(context,if(arrows) 48 else 130)
        val horizontal=Tuils.dpToPx(context,if(arrows) 9 else 12)
        val vertical=Tuils.dpToPx(context,2)
        toggle.setPadding(horizontal,vertical,horizontal,vertical)
        toggle.background=TerminalBorderRuntime.tabDrawable(context,background,border,target=FrameTarget.OUTPUT,
            style=appearance?.borderStyle(),draftDashed=appearance?.bool(Ui.enable_dashed_border)?:false)
    }

    internal fun styleDraft(toggle:TextView,root:View,appearance:HomeAppearance,saved:HomeAppearance) {
        val source=toggle.getTag(R.id.home_output_header_binding) as? Binding ?:return
        val mode=appearance.raw(Behavior.output_header_mode)?.trim()?.lowercase(java.util.Locale.US)
        val none=mode=="none"
        val arrows=mode=="arrows"
        val size=appearance.number(Ui.output_header_text_size,12f).toInt().coerceIn(8,32).toFloat()
        renderStyle(toggle.context,toggle,none,arrows,appearance.color(Theme.tab_text_color),size,
            appearance.font(toggle.context),appearance.color(Theme.terminal_header_background_color),
            appearance.color(Theme.terminal_header_border_color),appearance)
        toggle.setTag(R.id.home_output_header_binding,source.copy(none=none,arrowsOnly=arrows))
        if(appearance.changed(Behavior.output_header_mode,saved)) source.state?.let { state ->
            updateText(toggle.context,toggle,none,state.landscape,state.expanded,arrows,state.native,state.auto,
                !none && appearance.bool(Behavior.auto_hide_output),appearance.color(Theme.tab_text_color))
        } else if(arrows && toggle.foreground!=null) {
            source.state?.let { toggle.foreground=outputHeaderArrow(toggle.context,!it.expanded,appearance.color(Theme.tab_text_color)) }
        }
        TerminalBorderRuntime.bindPresentation(root.findViewById<View>(R.id.terminal_output_border),toggle)
    }

    fun updateText(
        context: Context,
        toggle: TextView?,
        outputHeaderNone: Boolean,
        landscapeLayoutActive: Boolean,
        terminalTrayExpanded: Boolean,
        outputHeaderArrowsOnly: Boolean,
        outputTrayNativeMode: Boolean,
        outputTrayAutoMode: Boolean,
        outputAutoHideEnabled: Boolean,
        outputColor: Int,
        unread: Boolean = false
    ) {
        if (toggle == null) return
        toggle.setTag(R.id.home_output_header_binding,Binding(outputHeaderNone,outputHeaderArrowsOnly,
            TextState(landscapeLayoutActive,terminalTrayExpanded,outputTrayNativeMode,outputTrayAutoMode,outputAutoHideEnabled)))
        if (outputHeaderNone) {
            toggle.text = ""
            hide(toggle)
            return
        }
        toggle.visibility = View.VISIBLE

        if (landscapeLayoutActive && !outputAutoHideEnabled && (outputTrayNativeMode || outputTrayAutoMode)) {
            toggle.foreground = null
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            if (!TextUtils.equals(toggle.text, context.getString(R.string.integration_terminaltraytoggleview_output_feb35))) {
                toggle.text = context.getString(R.string.integration_terminaltraytoggleview_output_feb35)
            }
            return
        }

        val collapsed = !terminalTrayExpanded
        if (outputHeaderArrowsOnly) {
            toggle.text = ""
            toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
            toggle.foregroundGravity = Gravity.CENTER
            toggle.foreground = outputHeaderArrow(context, collapsed, outputColor)
            return
        }

        toggle.foreground = null
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        val text = when {
            outputAutoHideEnabled -> if (terminalTrayExpanded) context.getString(R.string.integration_terminaltraytoggleview_output_v_93c86) else context.getString(R.string.integration_terminaltraytoggleview_output_9dd6b)
            outputTrayNativeMode -> context.getString(R.string.integration_terminaltraytoggleview_output_feb35)
            outputTrayAutoMode -> if (terminalTrayExpanded) context.getString(R.string.integration_terminaltraytoggleview_output_auto_v_69560) else context.getString(R.string.integration_terminaltraytoggleview_output_auto_b2829)
            else -> if (terminalTrayExpanded) context.getString(R.string.integration_terminaltraytoggleview_output_v_93c86) else context.getString(R.string.integration_terminaltraytoggleview_output_9dd6b)
        }
        // New output while the pane is closed: a boxed "!" after the label until it is opened.
        val shown: CharSequence = if (unread && collapsed) SpannableString("$text !").apply {
            setSpan(NoticeSpan(outputColor), length - 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else text
        if (!TextUtils.equals(toggle.text, shown) || (toggle.text is Spanned) != (shown is Spanned)) {
            toggle.text = shown
        }
        toggle.contentDescription = if (shown is Spanned) context.getString(R.string.output_header_unread, text) else null
    }

    /** Draws its character inside a small outlined square, the height of a capital letter. */
    internal class NoticeSpan(private val color: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            side(paint).roundToInt()

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val side = side(paint)
            val stroke = (paint.textSize / 10f).coerceAtLeast(1f)
            val box = RectF(x + stroke / 2, y - side + paint.descent() / 2 + stroke / 2, x + side - stroke / 2, y + paint.descent() / 2 - stroke / 2)
            val old = Triple(paint.color, paint.style, paint.strokeWidth)
            paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke
            canvas.drawRect(box, paint)
            paint.style = Paint.Style.FILL
            val mark = text?.subSequence(start, end)?.toString() ?: "!"
            canvas.drawText(mark, box.centerX() - paint.measureText(mark) / 2, box.centerY() - (paint.ascent() + paint.descent()) / 2, paint)
            paint.color = old.first; paint.style = old.second; paint.strokeWidth = old.third
        }

        private fun side(paint: Paint) = paint.textSize * 1.05f
    }

    private fun hide(toggle: TextView) {
        toggle.visibility = View.GONE
        toggle.setOnClickListener(null)
        toggle.foreground = null
        toggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
    }

    private fun outputHeaderArrow(context: Context, collapsed: Boolean, color: Int): Drawable? {
        var drawable = ContextCompat.getDrawable(
            context,
            if (collapsed) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
        ) ?: return null
        drawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTint(drawable, color)
        val size = Tuils.dpToPx(context, 18)
        drawable.setBounds(0, 0, size, size)
        return drawable
    }
}
