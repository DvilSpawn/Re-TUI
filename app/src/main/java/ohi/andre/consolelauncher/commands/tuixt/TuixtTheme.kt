package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.R
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.FrameManager
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.FrameTarget
import ohi.andre.consolelauncher.tuils.Tuils

object TuixtTheme {
    private fun color(role: Theme): Int = SettingsColorSafety.color(role, ThemeColorResolver::color)

    @JvmStatic
    fun rowTextColor(): Int = color(Theme.settings_row_text_color)

    @JvmStatic
    fun rowAccentColor(): Int = SettingsColorSafety.readable(accentColor(), color(Theme.settings_row_background_color))

    @JvmStatic
    fun borderColor(): Int = color(Theme.settings_panel_border_color)

    @JvmStatic
    fun accentColor(): Int = color(Theme.settings_header_text_color)

    @JvmStatic
    fun textColor(): Int = color(Theme.settings_panel_text_color)

    @JvmStatic
    fun surfaceColor(): Int = color(Theme.settings_panel_background_color)

    @JvmStatic
    fun overlayColor(): Int = color(Theme.settings_screen_color)

    @JvmStatic
    fun styleScreen(context: Context, view: View) {
        view.setBackgroundColor(overlayColor())
        if (AppearanceSettings.crtFilter()) {
            val overlay = CrtOverlayDrawable(context)
            overlay.setAccentColor(textColor())
            view.foreground = overlay
        }
    }

    @JvmStatic
    fun stylePanel(context: Context, view: View) {
        view.background = framedRect(context, FrameTarget.SETTINGS, surfaceColor(), borderColor(), 1.5f)
    }

    @JvmStatic
    fun styleDialogPanel(context: Context, view: View) {
        view.background = framedRect(
            context,
            FrameTarget.DIALOG,
            color(Theme.dialog_panel_background_color),
            color(Theme.dialog_panel_border_color),
            1.5f
        )
    }

    @JvmStatic
    fun styleHeader(context: Context, view: TextView) {
        view.setTextColor(color(Theme.settings_header_text_color))
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 15f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 12f), dp(context, 3f), dp(context, 12f), dp(context, 3f))
        view.background = framedRect(
            context,
            FrameTarget.HEADER,
            color(Theme.settings_header_background_color),
            color(Theme.settings_header_border_color),
            1.5f,
            AppearanceSettings.headerCornerRadius()
        )
    }

    @JvmStatic
    fun styleListItem(context: Context, view: TextView, selected: Boolean) {
        markSelection(view, selected)
        view.setTextColor(
            color(
                if (selected) Theme.list_selected_text_color else Theme.settings_row_text_color
            )
        )
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 15f
        view.gravity = Gravity.CENTER_VERTICAL
        view.setPadding(dp(context, 14f), dp(context, 12f), dp(context, 14f), dp(context, 12f))
        view.minHeight = dp(context, 48f)
        view.background = framedRect(
            context,
            if (selected) FrameTarget.LIST_ITEM_SELECTED else FrameTarget.LIST_ITEM,
            color(
                if (selected) Theme.list_selected_background_color else Theme.settings_row_background_color
            ),
            color(
                if (selected) Theme.list_selected_border_color else Theme.settings_row_border_color
            ),
            if (selected) 2f else 1.25f
        )
    }

    @JvmStatic
    fun styleInput(context: Context, view: EditText) {
        val surface = color(Theme.settings_input_background_color)
        val text = color(Theme.settings_input_text_color)
        view.setTextColor(text)
        view.setHintTextColor(color(Theme.settings_input_hint_color))
        view.highlightColor = color(Theme.selection_background_color)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursor = (view.textCursorDrawable as? ohi.andre.consolelauncher.tuils.PaletteCursorDrawable)
                ?: ohi.andre.consolelauncher.tuils.PaletteCursorDrawable(dp(context, 2f))
            cursor.role = Theme.focus_color
            if (view.textCursorDrawable !== cursor) {
                view.textCursorDrawable = cursor
            }
        }
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.setSingleLine(false)
        view.setPadding(dp(context, 10f), dp(context, 8f), dp(context, 10f), dp(context, 8f))
        view.background = framedRect(
            context,
            FrameTarget.UI_INPUT,
            surface,
            color(Theme.settings_input_border_color),
            1.25f
        )
    }

    @JvmStatic
    fun styleButton(context: Context, view: TextView, primary: Boolean) {
        val backgroundRole = if (primary) Theme.primary_button_background_color else Theme.button_background_color
        val textRole = if (primary) Theme.primary_button_text_color else Theme.button_text_color
        val borderRole = if (primary) Theme.primary_button_border_color else Theme.button_border_color
        view.setTextColor(ColorStateList(
            arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                color(Theme.disabled_text_color),
                color(Theme.pressed_text_color),
                color(textRole)
            )
        ))
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 14f), dp(context, 8f), dp(context, 14f), dp(context, 8f))
        val normal = if (primary && FrameManager.isActive(context, FrameTarget.BUTTON_PRIMARY)) {
            FrameTarget.BUTTON_PRIMARY
        } else {
            FrameTarget.BUTTON
        }
        val base = framedRect(
            context,
            normal,
            color(backgroundRole),
            color(borderRole),
            if (primary) 2f else 1.25f
        )
        view.background = statefulFrame(
            context,
            base,
            FrameTarget.BUTTON_PRESSED,
            color(Theme.pressed_background_color),
            color(Theme.pressed_border_color)
        )
    }

    @JvmStatic
    fun styleChoice(context: Context, view: TextView, selected: Boolean) {
        styleButton(context, view, selected)
        markSelection(view, selected)
    }

    @JvmStatic
    fun styleToggle(context: Context, view: TextView, checked: Boolean) {
        view.setText(if (checked) R.string.common_on else R.string.common_off)
        markSelection(view, checked)
        val backgroundRole = if (checked) Theme.toggle_on_background_color else Theme.toggle_off_background_color
        val textRole = if (checked) Theme.toggle_on_text_color else Theme.toggle_off_text_color
        val borderRole = if (checked) Theme.toggle_on_border_color else Theme.toggle_off_border_color
        view.setTextColor(color(textRole))
        view.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
        view.textSize = 13f
        view.gravity = Gravity.CENTER
        view.setPadding(dp(context, 18f), dp(context, 9f), dp(context, 18f), dp(context, 9f))
        view.minWidth = dp(context, 76f)
        val role = if (checked) FrameTarget.TOGGLE_ON else FrameTarget.TOGGLE_OFF
        view.background = framedRect(
            context,
            role,
            color(backgroundRole),
            color(borderRole),
            if (checked) 2f else 1.25f
        )
    }

    @JvmStatic
    fun styleIconButton(context: Context, view: View) {
        view.background = framedRect(
            context,
            FrameTarget.ICON_BUTTON,
            color(Theme.icon_button_background_color),
            color(Theme.icon_button_border_color),
            1.25f
        )
    }

    @JvmStatic
    fun styleColorPreview(context: Context, view: View, color: Int) {
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 4f).toFloat()
            setColor(color)
            setStroke(dp(context, 1.25f).coerceAtLeast(1), borderColor())
        }
    }

    @JvmStatic
    fun styleSlider(context: Context, view: SeekBar, fallbackColor: Int = accentColor()) {
        val track = FrameManager.drawable(context, FrameTarget.SLIDER_TRACK)
        val progress = FrameManager.drawable(context, FrameTarget.SLIDER_PROGRESS)
        val thumb = FrameManager.drawable(context, FrameTarget.SLIDER_THUMB, 24f)
        view.progressTintList = if (progress == null) {
            ColorStateList.valueOf(color(Theme.slider_progress_color))
        } else null
        view.progressBackgroundTintList = if (track == null) {
            ColorStateList.valueOf(color(Theme.slider_track_color))
        } else null
        view.thumbTintList = if (thumb == null) {
            ColorStateList.valueOf(color(Theme.slider_thumb_color))
        } else null
        styleFrameSlider(view, track, progress, thumb)
    }

    private fun styleFrameSlider(
        view: SeekBar,
        track: Drawable?,
        progress: Drawable?,
        thumb: Drawable?
    ) {
        val layers = view.progressDrawable?.mutate() as? LayerDrawable
        if (layers != null) {
            track?.let { layers.setDrawableByLayerId(android.R.id.background, it) }
            progress?.let {
                layers.setDrawableByLayerId(
                    android.R.id.progress,
                    ClipDrawable(it, Gravity.START, ClipDrawable.HORIZONTAL)
                )
            }
            view.progressDrawable = layers
        } else if (track != null && progress != null) {
            view.progressDrawable = LayerDrawable(
                arrayOf(track, ClipDrawable(progress, Gravity.START, ClipDrawable.HORIZONTAL))
            ).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
        }
        thumb?.let { view.thumb = it }
        if (track != null || progress != null || thumb != null) view.splitTrack = false
    }

    private fun statefulFrame(
        context: Context,
        base: Drawable,
        pressed: FrameTarget,
        pressedFill: Int,
        pressedStroke: Int
    ): Drawable =
        FrameManager.drawable(context, pressed)?.let { pressedFrame ->
            StateListDrawable().apply {
                addState(
                    intArrayOf(-android.R.attr.state_enabled),
                    framedRect(
                        context,
                        FrameTarget.BUTTON,
                        color(Theme.disabled_background_color),
                        color(Theme.divider_color),
                        1.25f
                    )
                )
                addState(intArrayOf(android.R.attr.state_pressed), framedRect(context, pressed, pressedFill, pressedStroke, 1.25f))
                addState(intArrayOf(), base)
            }
        } ?: StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                framedRect(
                    context,
                    FrameTarget.BUTTON,
                    color(Theme.disabled_background_color),
                    color(Theme.divider_color),
                    1.25f
                )
            )
            addState(
                intArrayOf(android.R.attr.state_pressed),
                framedRect(context, pressed, pressedFill, pressedStroke, 1.25f)
            )
            addState(intArrayOf(), base)
        }

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float): Drawable =
        rect(context, fill, stroke, strokeDp, AppearanceSettings.dashedBorderCornerRadius())

    @JvmStatic
    fun rect(context: Context, fill: Int, stroke: Int, strokeDp: Float, radiusDp: Int): Drawable {
        return framedRect(context, FrameTarget.SETTINGS, fill, stroke, strokeDp, radiusDp)
    }

    private fun framedRect(
        context: Context,
        target: FrameTarget,
        fill: Int,
        stroke: Int,
        strokeDp: Float,
        radiusDp: Int = AppearanceSettings.dashedBorderCornerRadius()
    ): Drawable {
        return TerminalBorderRuntime.panelDrawable(
            context,
            fill,
            stroke,
            strokeDp,
            radiusDp,
            AppearanceSettings.dashedBorders(),
            cyberdeckNotch = false,
            target = target
        )
    }

    @JvmStatic
    fun dp(context: Context, value: Float): Int = Tuils.dpToPx(context, value).toInt()

    private fun markSelection(view: TextView, selected: Boolean) {
        val label = view.text.toString().removePrefix("✓ ")
        view.text = if (selected) "✓ $label" else label
        view.isSelected = selected
    }
}
