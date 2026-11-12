package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.tuils.PixelGrid

/**
 * Hidden: `pixelgrid on|off|snap|1bit|<cell size in dp>` draws the home screen on a coarse pixel grid. It is kept
 * out of suggestions, help and Settings on purpose.
 */
class pixelgrid : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val context = pack.context
        if (!PixelGrid.supported()) return context.getString(R.string.pixel_grid_unsupported)
        val arg = pack.getString().trim().lowercase()
        when {
            arg.isEmpty() || arg == "status" -> Unit
            arg == "on" -> PixelGrid.setEnabled(context, true)
            arg == "off" -> PixelGrid.setEnabled(context, false)
            arg == "snap" -> { PixelGrid.setMode(context, PixelGrid.Mode.SNAP); PixelGrid.setEnabled(context, true) }
            arg == "1bit" -> { PixelGrid.setMode(context, PixelGrid.Mode.ONE_BIT); PixelGrid.setEnabled(context, true) }
            arg.toFloatOrNull() != null -> { PixelGrid.setCell(context, arg.toFloat()); PixelGrid.setEnabled(context, true) }
            else -> return context.getString(R.string.pixel_grid_usage)
        }
        (context as? LauncherActivity ?: LauncherActivity.instance)?.let { launcher -> launcher.runOnUiThread { launcher.uiManager?.applyPixelGrid() } }
        return context.getString(R.string.pixel_grid_status,
            if (PixelGrid.enabled(context)) PixelGrid.mode(context).id else context.getString(R.string.pixel_grid_off), PixelGrid.cellDp(context))
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority(): Int = 1
    override fun helpRes(): Int = R.string.pixel_grid_usage
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = pack.context.getString(R.string.pixel_grid_usage)
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = exec(pack)
}
