package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.file.RetuiFilesContract

class ls : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as MainPack
        return RetuiFilesContract.launch(info.context, action = RetuiFilesContract.ACTION_LIST)
    }

    override fun helpRes(): Int = R.string.help_ls

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

    override fun priority(): Int = 4

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String = (pack as MainPack).res.getString(R.string.output_filenotfound)

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = exec(pack)
}
