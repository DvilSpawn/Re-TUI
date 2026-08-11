package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.file.RetuiFilesContract

open class `open` : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as MainPack
        val path = info.getString()?.trim().orEmpty()
        if (path.isEmpty()) return info.res.getString(helpRes())
        return RetuiFilesContract.launch(
            info.context,
            action = RetuiFilesContract.ACTION_OPEN,
            target = path
        )
    }

    override fun helpRes(): Int = R.string.help_open

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = pack.context.getString(helpRes())

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String =
        pack.context.getString(R.string.output_filenotfound)
}
