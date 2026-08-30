package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.PresetManager
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class preset : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        save {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                try {
                    PresetManager.save(pack.context, name)
                    if (pack.context is Reloadable) {
                        (pack.context as Reloadable).addMessage("preset", "Saved preset: " + name.trim())
                        LauncherActivity.preview(pack.context)
                    }
                    return "Preset '" + name.trim() + "' saved."
                } catch (e: IllegalArgumentException) {
                    return e.message!!
                } catch (e: Exception) {
                    return pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        },
        apply {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                try {
                    PresetManager.apply(name)

                    if (pack.context is Reloadable) {
                        (pack.context as Reloadable).addMessage("preset", "Applied preset: " + name.trim())
                        LauncherActivity.preview(pack.context)
                    }

                    return "Preset '" + name.trim() + "' applied."
                } catch (e: IllegalArgumentException) {
                    return e.message!!
                } catch (e: Exception) {
                    return pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        },
        ls {
            override fun exec(pack: ExecutePack): String {
                val list = PresetManager.listAllPresetNames()
                if (list.isEmpty()) return "No presets found."
                return Tuils.toPlanString(list, "\n")
            }

            override fun args(): IntArray = IntArray(0)
        },
        rm {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                return try {
                    PresetManager.remove(name)
                    "Preset '${name.trim()}' removed."
                } catch (e: Exception) {
                    e.message ?: pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        };

        override fun label(): String = Tuils.MINUS + name.replace("_", "")

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_preset)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.help_preset)

        companion object {
            fun get(p: String): Param? {
                val value = p.lowercase(Locale.getDefault())
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

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_preset

    override fun doThings(pack: ExecutePack): String? = null
}
