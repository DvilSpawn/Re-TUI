package ohi.andre.consolelauncher.tuils

import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import java.util.Locale

/** Presentation only. UIManager owns playback callbacks; previews bind no actions. */
object MusicModuleRenderer {
    fun style(root: View, values: AppearanceValues = AppearanceValues.saved, font: Typeface? = null) {
        val context = root.context
        val typeface = font ?: values.typeface(context)
        TuiWidgetDecorator.decorateWidget(root, R.id.music_module_border, R.id.music_module_label, R.id.music_module_close,
            values.color(Theme.module_panel_border_color), values.color(Theme.module_panel_text_color), FrameTarget.MUSIC, values, typeface)
        root.findViewById<TextView>(R.id.music_module_close)?.apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            values.applySize(this, Ui.module_header_text_size)
            setTextColor(values.color(Theme.module_panel_text_color))
            background = TerminalBorderRuntime.tabDrawable(context, values.color(Theme.module_header_control_background_color), FrameTarget.MUSIC, values)
        }
        listOf(R.id.music_song_title, R.id.music_singer, R.id.music_app_prefix, R.id.music_app_label,
            R.id.music_prev, R.id.music_next, R.id.music_play_pause).forEach { id ->
            root.findViewById<TextView>(id)?.apply {
                this.typeface = typeface
                values.applySize(this, Ui.module_body_text_size)
                setTextColor(values.color(Theme.module_panel_text_color))
                includeFontPadding = true
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        listOf(R.id.music_prev, R.id.music_next, R.id.music_play_pause).forEach { id ->
            root.findViewById<TextView>(id)?.background = TerminalBorderRuntime.panelDrawable(context,
                values.color(Theme.module_action_background_color), values.color(Theme.module_action_border_color), 1.2f,
                values.radius(Ui.module_corner_radius), values.bool(Ui.enable_dashed_border), target = FrameTarget.MUSIC, values = values)
        }
    }

    fun bind(root: View, song: String?, singer: String?, titlePrefix: String, subtitlePrefix: String, values: AppearanceValues = AppearanceValues.saved) {
        root.findViewById<TextView>(R.id.music_song_title)?.apply {
            text = titlePrefix + song?.takeIf { it.isNotEmpty() }?.uppercase(Locale.getDefault()).orEmpty().ifEmpty { "-" }
            setTextColor(values.color(Theme.module_panel_text_color))
        }
        root.findViewById<TextView>(R.id.music_singer)?.apply {
            text = subtitlePrefix + singer?.takeIf { it.isNotEmpty() }?.uppercase(Locale.getDefault()).orEmpty().ifEmpty { "-" }
            setTextColor(values.color(Theme.module_panel_text_color))
        }
    }
}
