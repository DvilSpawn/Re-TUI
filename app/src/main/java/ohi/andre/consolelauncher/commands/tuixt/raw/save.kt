package ohi.andre.consolelauncher.commands.tuixt.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.tuixt.TuixtPack
import ohi.andre.consolelauncher.managers.file.FileManager

class save : CommandAbstraction {
    override fun exec(pack: ExecutePack): String? {
        val info = pack as TuixtPack
        val text = info.editText.text.toString()
        val error = FileManager.writeOn(info.editFile, text)
        return error ?: info.resources.getString(R.string.tuixt_saved)
    }

    override fun argType(): IntArray = intArrayOf()

    override fun priority(): Int = 5

    override fun helpRes(): Int = R.string.help_tuixt_save

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? = null

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = null
}
