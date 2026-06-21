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
                    "Saved Space: " + active.name
                }
            }
        },
        new {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val created = SpaceManager.createFromActive(pack.context, pack.getString())
                    "Created and switched to Space: " + created.name
                }
            }
        },
        rename {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val renamed = SpaceManager.renameActive(pack.context, pack.getString())
                    "Renamed active Space: " + renamed.name
                }
            }
        },
        switch {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val target = SpaceManager.switchTo(pack.context, pack.getString())
                    NotificationService.requestReload(pack.context)
                    if (pack.context is Reloadable) {
                        (pack.context as Reloadable).addMessage("space", "Switched to " + target.name)
                        (pack.context as Reloadable).reload()
                    }
                    "Switched to Space: " + target.name
                }
            }
        },
        rm {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                return safe(pack) {
                    val removed = SpaceManager.remove(pack.context, pack.getString())
                    "Removed Space: " + removed.name
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
                val clean = value.lowercase(Locale.getDefault())
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
