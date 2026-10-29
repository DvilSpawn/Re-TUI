package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.os.Build
import ohi.andre.consolelauncher.managers.settings.AppearanceValues
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils

object DeviceTextRenderer {
    fun render(context: Context, values: AppearanceValues): CharSequence {
        val text = values.get(Behavior.device_format)
            .replace("%u", values.get(Ui.username), ignoreCase = true)
            .replace("%d", values.get(Ui.deviceName).ifEmpty { Build.DEVICE }, ignoreCase = true)
        return values.span(context, Tuils.patternNewline.matcher(text).replaceAll(Tuils.NEWLINE),
            Theme.device_text_color, values.int(Ui.device_size))
    }
}
