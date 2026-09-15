package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.RegexManager
import ohi.andre.consolelauncher.tuils.Tuils

class regex : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        add {
            override fun exec(pack: ExecutePack): String? {
                val output = RegexManager.instance!!.add(pack.getInt(), pack.getString())
                if (output == null) return null
                return if (output.isEmpty()) pack.context.getString(R.string.id_already) else output
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                val output = RegexManager.instance!!.rm(pack.getInt())
                if (output == null) return null
                return if (output.isEmpty()) pack.context.getString(R.string.id_notfound) else output
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.INT)
        },
        get {
            override fun exec(pack: ExecutePack): String {
                val regex = RegexManager.instance!!.get(pack.getInt())
                    ?: return pack.context.getString(R.string.id_notfound)

                val pattern = regex.regex
                return pattern?.pattern() ?: regex.literalPattern ?: Tuils.EMPTYSTRING
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.INT)
        },
        test {
            override fun exec(pack: ExecutePack): String? {
                val s = RegexManager.instance!!.test(pack.getInt(), pack.getString())
                if (s.isNullOrEmpty()) return pack.context.getString(R.string.id_notfound)

                Tuils.sendOutput(pack.context, s)
                return null
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
        };

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.invalid_integer)

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_regex)

        override fun label(): String = Tuils.MINUS + name

        companion object {
            fun get(p: String): Param? {
                val value = p.lowercase(Locale.ROOT)
                val ps = entries
                for (p1 in ps) {
                    if (value.endsWith(p1.label())) {
                        return p1
                    }
                }
                return null
            }

            fun labels(): Array<String> {
                val ps = entries
                val ss = Array(ps.size) { "" }

                for (count in ps.indices) {
                    ss[count] = ps[count].label()
                }

                return ss
            }
        }
    }

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun doThings(pack: ExecutePack): String? = null

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_regex

    override fun params(): Array<String> = Param.labels()
}
