package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior

class orientation : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val input = pack.getString()
        return apply(pack, input)
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_orientation

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_orientation)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String =
        pack.context.getString(R.string.command_orientation_usage_orientation_portrait_landscape_auto_fbc79, currentOrientation(pack.context))

    companion object {
        const val MODE_LANDSCAPE = "landscape"
        const val MODE_PORTRAIT = "portrait"
        const val MODE_AUTO = "auto"

        private const val VALUE_LANDSCAPE = "0"
        private const val VALUE_PORTRAIT = "1"
        private const val VALUE_AUTO = "2"

        @JvmStatic
        fun apply(pack: ExecutePack, input: String?): String {
            val mode = input?.trim()?.lowercase(Locale.US) ?: ""
            if (mode.isEmpty() || mode == "status" || mode == "-status") {
                return pack.context.getString(R.string.command_orientation_usage_orientation_portrait_landscape_auto_fbc79, currentOrientation(pack.context))
            }

            if (mode == MODE_LANDSCAPE || mode == VALUE_LANDSCAPE) {
                setOrientation(pack, VALUE_LANDSCAPE)
                return pack.context.getString(R.string.command_orientation_landscape_preference_saved_re_t_ui_will_us_4c485)
            }

            if (mode == MODE_PORTRAIT || mode == VALUE_PORTRAIT) {
                setOrientation(pack, VALUE_PORTRAIT)
                return pack.context.getString(R.string.command_orientation_portrait_preference_saved_re_t_ui_will_kee_d0e0b)
            }

            if (mode == MODE_AUTO || mode == VALUE_AUTO || mode == "autorotate" || mode == "auto-rotate") {
                setOrientation(pack, VALUE_AUTO)
                return pack.context.getString(R.string.command_orientation_auto_orientation_preference_saved_re_t_ui_218c8)
            }

            return pack.context.getString(R.string.command_orientation_unknown_orientation_usage_orientation_port_4d4ad, input)
        }

        private fun setOrientation(pack: ExecutePack, value: String) {
            LauncherSettings.set(pack.context, Behavior.orientation, value)

            if (pack.context is LauncherActivity) {
                (pack.context as LauncherActivity).applyOrientationPreference()
            }
        }

        private fun currentOrientation(context: android.content.Context): String {
            val value = LauncherSettings.getInt(Behavior.orientation)
            if (value == 0) {
                return context.getString(R.string.command_detail_orientation_orientation_landscape_eb4bf)
            }
            if (value == 1) {
                return context.getString(R.string.command_detail_orientation_orientation_portrait_52aca)
            }
            return if (value == 2) context.getString(R.string.command_detail_orientation_orientation_auto_4c066) else context.getString(R.string.command_detail_orientation_orientation_919b3, value)
        }
    }
}
