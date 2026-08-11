package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack

/**
 * Retired. Dock Lua scripts are modules (`module …`).
 * Android AppWidgets live in the widgets pane (toolbar button), not this command.
 */
class widget : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val arg = pack.get(Any::class.java, 0)
        val input = arg?.toString()?.trim().orEmpty()
        val hint = if (input.isEmpty()) {
            "module -ls"
        } else if (input.startsWith("-")) {
            "module $input"
        } else {
            "module -$input"
        }
        return "widget is retired.\n" +
            "Dock Lua scripts: use module (try: $hint)\n" +
            "Android AppWidgets: open the widgets pane from the toolbar."
    }

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_widget

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.help_widget)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String =
        pack.context.getString(R.string.help_widget)
}
