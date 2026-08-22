package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.tuils.Tuils

class tutorial : CommandAbstraction {
    private val url = "https://github.com/DvilSpawn/Re-TUI/wiki"

    override fun exec(pack: ExecutePack): String? {
        val intent = Tuils.webPage(url)
        pack.context.startActivity(intent)
        return null
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_tutorial

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
