package ohi.andre.consolelauncher.commands.main.raw

import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.SpaceManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class space : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        ls {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String =
                safe(pack) {
                    SpaceManager.describeSpaces(pack.context)
                }
        },
        current {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val active = SpaceManager.activeSpace(pack.context)
                    active.name + " [" + active.id + "]"
                }
            }
        },
        save {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val active = SpaceManager.saveActive(pack.context)
                    pack.context.getString(R.string.command_space_saved_space_e5292, active.name)
                }
            }
        },
        new {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val created = SpaceManager.createFromActive(pack.context, pack.getString())
                    pack.context.getString(R.string.command_space_created_and_switched_to_space_d546d, created.name)
                }
            }
        },
        rename {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.SPACE_RENAME_NAME)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val renamed = SpaceManager.renameActive(pack.context, pack.getString())
                    pack.context.getString(R.string.command_space_renamed_active_space_4855e, renamed.name)
                }
            }
        },
        switch {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.SPACE_TARGET)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val target = SpaceManager.switchTo(pack.context, pack.getString())
                    NotificationService.requestReload(pack.context)
                    if (pack.context is Reloadable) {
                        (pack.context as Reloadable).addMessage("space", pack.context.getString(R.string.command_space_switched_to_c5720, target.name))
                        (pack.context as Reloadable).reload()
                    }
                    pack.context.getString(R.string.command_space_switched_to_space_c4781, target.name)
                }
            }
        },
        rm {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.SPACE_TARGET)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val removed = SpaceManager.remove(pack.context, pack.getString())
                    pack.context.getString(R.string.command_space_removed_space_24a7b, removed.name)
                }
            }
        };

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_space)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.help_space)

        fun safe(pack: ExecutePack, block: () -> String): String {
            return try {
                block()
            } catch (e: IllegalArgumentException) {
                e.message ?: pack.context.getString(R.string.output_error)
            } catch (e: IllegalStateException) {
                e.message ?: pack.context.getString(R.string.output_error)
            } catch (e: Exception) {
                Tuils.log(e)
                pack.context.getString(R.string.output_error)
            }
        }

        companion object {
            fun get(value: String): Param? {
                val clean = value.lowercase(Locale.ROOT)
                for (param in entries) {
                    if (clean.endsWith(param.label())) {
                        return param
                    }
                }
                return null
            }

            fun labels(): Array<String> {
                val values = entries
                return Array(values.size) { values[it].label() }
            }
        }
    }

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_space

    override fun doThings(pack: ExecutePack): String? = null
}
