package ohi.andre.consolelauncher.commands.main.raw

import java.io.File
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.ArrayList

class alias : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        add {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String>()!!
                var scope = AliasManager.SCOPE_APP

                if (args.size > 0) {
                    val first = args[0]
                    if (("-" + AliasManager.SCOPE_APP).equals(first, ignoreCase = true) ||
                        ("-" + AliasManager.SCOPE_SCRIPT).equals(first, ignoreCase = true)
                    ) {
                        scope = first.substring(1).lowercase(Locale.getDefault())
                        args.removeAt(0)
                    }
                }

                if (args.size < 2) return pack.context.getString(R.string.output_lessarg)

                (pack as MainPack).aliasManager.add(pack.context, args.removeAt(0), Tuils.toPlanString(args, Tuils.SPACE), scope)
                return null
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.TEXTLIST)
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String>()!!
                if (args.size < 1) return pack.context.getString(R.string.output_lessarg)
                (pack as MainPack).aliasManager.remove(pack.context, args[0])
                return null
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.TEXTLIST)
        },
        file {
            override fun exec(pack: ExecutePack): String? {
                WidgetEditorActivity.openFile(pack.context, File(Tuils.getFolder(), AliasManager.PATH))
                return null
            }

            override fun args(): IntArray = IntArray(0)
        },
        ls {
            override fun exec(pack: ExecutePack): String = (pack as MainPack).aliasManager.printAliases()

            override fun args(): IntArray = IntArray(0)
        },
        tutorial {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Alias"))
                return null
            }
        };

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_alias)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? = null

        companion object {
            fun get(p: String): Param? {
                val value = p.lowercase(Locale.getDefault())
                for (p1 in entries) {
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

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun doThings(pack: ExecutePack): String? = null

    override fun helpRes(): Int = R.string.help_alias

    override fun priority(): Int = 2
}
