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
        currentOrientation() + "\nUsage: orientation portrait|landscape|auto"

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
                return currentOrientation() + "\nUsage: orientation portrait|landscape|auto"
            }

            if (mode == MODE_LANDSCAPE || mode == VALUE_LANDSCAPE) {
                setOrientation(pack, VALUE_LANDSCAPE)
                return "Landscape preference saved. Re:T-UI will use the split layout only when the window is wide enough."
            }

            if (mode == MODE_PORTRAIT || mode == VALUE_PORTRAIT) {
                setOrientation(pack, VALUE_PORTRAIT)
                return "Portrait preference saved. Re:T-UI will keep the portrait layout even if Android reports landscape."
            }

            if (mode == MODE_AUTO || mode == VALUE_AUTO || mode == "autorotate" || mode == "auto-rotate") {
                setOrientation(pack, VALUE_AUTO)
                return "Auto orientation preference saved. Re:T-UI will adapt to the current device or window orientation."
            }

            return "Unknown orientation: $input\nUsage: orientation portrait|landscape|auto"
        }

        private fun setOrientation(pack: ExecutePack, value: String) {
            LauncherSettings.set(pack.context, Behavior.orientation, value)

            if (pack.context is LauncherActivity) {
                (pack.context as LauncherActivity).applyOrientationPreference()
            }
        }

        private fun currentOrientation(): String {
            val value = LauncherSettings.getInt(Behavior.orientation)
            if (value == 0) {
                return "Orientation: landscape"
            }
            if (value == 1) {
                return "Orientation: portrait"
            }
            return if (value == 2) "Orientation: auto" else "Orientation: $value"
        }
    }
}
