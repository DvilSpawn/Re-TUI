package ohi.andre.consolelauncher.commands.main.specific

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.AbstractMap.SimpleEntry

abstract class ParamCommand : CommandAbstraction {
    final override fun argType(): IntArray = intArrayOf(CommandAbstraction.PARAM)

    @Throws(Exception::class)
    final override fun exec(pack: ExecutePack): String? {
        val output = doThings(pack)
        if (output != null) {
            return output
        }

        val param = pack.get(Param::class.java)
        if (param == null) {
            val o1 = pack.get(Any::class.java, 0)
            return if (o1 == null || o1.toString().isEmpty()) {
                pack.context.getString(helpRes())
            } else {
                pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + o1
            }
        }
        return param.exec(pack)
    }

    open fun getParam(pack: MainPack, param: String): SimpleEntry<Boolean, Param?> {
        val p = paramForString(pack, param)
        if (p == null && defaultParamReference() != null) {
            return SimpleEntry(true, paramForString(pack, defaultParam(pack)))
        }
        return SimpleEntry(false, p)
    }

    fun defaultParam(pack: MainPack): String {
        var def = pack.cmdPrefs[defaultParamReference()!!]!!
        if (!def.startsWith("-")) {
            def = "-$def"
        }
        return def
    }

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String = pack.context.getString(helpRes())

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String {
        Tuils.log("inf", indexNotFound)
        if (indexNotFound == 0) {
            try {
                Tuils.log("last")
                val param = pack.get(String::class.java, 0)
                return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + param
            } catch (e: Exception) {
            }
        }

        return pack.context.getString(helpRes())
    }

    abstract fun params(): Array<out String?>

    protected abstract fun paramForString(pack: MainPack, param: String): Param?

    protected abstract fun doThings(pack: ExecutePack): String?

    open fun defaultParamReference(): XMLPrefsSave? = null
}
