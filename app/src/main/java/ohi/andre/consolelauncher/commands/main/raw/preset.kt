package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
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
                        (pack.context as Reloadable).addMessage("preset", pack.context.getString(R.string.command_preset_saved_preset_4a339, name.trim()))
                    }
                    return pack.context.getString(R.string.command_preset_preset_saved_c599d, name.trim())
                } catch (e: IllegalArgumentException) {
                    return e.message!!
                } catch (e: Exception) {
                    return e.message ?: pack.context.getString(R.string.output_error)
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
                        (pack.context as Reloadable).addMessage("preset", pack.context.getString(R.string.command_preset_applied_preset_c132b, name.trim()))
                        LauncherActivity.preview(pack.context)
                    }

                    return pack.context.getString(R.string.command_preset_preset_applied_0611f, name.trim())
                } catch (e: IllegalArgumentException) {
                    return e.message!!
                } catch (e: Exception) {
                    return e.message ?: pack.context.getString(R.string.output_error)
                }
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.PRESET_NAME)
        },
        duplicate {
            override fun exec(pack: ExecutePack): String {
                val sourceName = pack.getString()!!
                TuixtDialog.showInput(
                    pack.context,
                    pack.context.getString(R.string.command_preset_duplicate_preset_76c16),
                    pack.context.getString(R.string.command_preset_new_preset_name_0ee29),
                    pack.context.getString(R.string.command_preset_duplicate_972d5),
                    pack.context.getString(R.string.command_preset_cancel_77dfd),
                    TuixtDialog.InputAction { value ->
                        val message = try {
                            val duplicated = PresetManager.duplicate(sourceName, value.orEmpty())
                            pack.context.getString(R.string.command_preset_duplicated_as_0b74a, sourceName, duplicated)
                        } catch (e: Exception) {
                            e.message ?: pack.context.getString(R.string.output_error)
                        }
                        if (pack.context is Reloadable) {
                            (pack.context as Reloadable).addMessage("preset", message)
                        }
                    }
                )
                return pack.context.getString(R.string.command_preset_enter_a_name_for_the_duplicate_e815f)
            }

            override fun args(): IntArray = intArrayOf(CommandAbstraction.SAVED_PRESET_NAME)
        },
        ls {
            override fun exec(pack: ExecutePack): String {
                val list = PresetManager.listAllPresetNames()
                if (list.isEmpty()) return pack.context.getString(R.string.command_preset_no_presets_found_a6efc)
                return Tuils.toPlanString(list, "\n")
            }

            override fun args(): IntArray = IntArray(0)
        },
        market {
            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage(MARKETPLACE_URL))
                return null
            }

            override fun args(): IntArray = IntArray(0)
        },
        rm {
            override fun exec(pack: ExecutePack): String {
                val name = pack.getString()!!
                return try {
                    PresetManager.remove(name)
                    pack.context.getString(R.string.command_preset_preset_removed_0fa34, name.trim())
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

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_preset

    override fun doThings(pack: ExecutePack): String? = null

    companion object {
        internal const val MARKETPLACE_URL = "https://re-tui.pages.dev/marketplace"
    }
}
