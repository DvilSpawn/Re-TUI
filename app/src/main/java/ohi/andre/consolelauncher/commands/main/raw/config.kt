package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.tuixt.WidgetEditorActivity
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.set
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import java.io.File
import java.util.Locale
import android.content.SharedPreferences
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver

/**
 * Created by francescoandreuzzi on 11/06/2017.
 */
class config : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        set {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()
                val value = pack.getString()
                if (save is Ui && StatusRowResolver.isStatusIndex(save)) {
                    LauncherSettings.setStatusIndex(pack.context, save, value)
                } else {
                    if (value.isEmpty()) ohi.andre.consolelauncher.managers.settings.LauncherSettings.erase(pack.context, save)
                    else set(pack.context, save, value)
                }

                (pack.context as Reloadable).addMessage(
                    save.parent()!!.path(),
                    save.label() + " -> " + value
                )

                if (save.label()!!.startsWith("default_app_n")) return pack.context.getString(R.string.output_usedefapp)
                if (save === Behavior.unlock_counter_cycle_start) {
                    pack.context.getSharedPreferences(UIManager.PREFS_NAME, 0).edit()
                        .putLong(UIManager.NEXT_UNLOCK_CYCLE_RESTART, 0).putInt(UIManager.UNLOCK_KEY, 0).apply()
                }

                return null
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                pack.args = arrayOf<Any?>(pack.args!![1], Tuils.EMPTYSTRING)
                return Param.set.exec(pack)
            }
        },
        info {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()

                return (pack.context.getString(R.string.command_config_type_default_75261, Tuils.SPACE, save!!.type(), Tuils.NEWLINE, Tuils.SPACE, save.defaultValue(), Tuils.NEWLINE, save.info(pack.context)))
            }
        },
        file {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_FILE)
            }

            override fun exec(pack: ExecutePack): String? {
                val file = File(Tuils.getFolder(), pack.getString())

                WidgetEditorActivity.openFile(pack.context, file)

                return null
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_filenotfound)
            }
        },
        append {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY, CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()
                val value = XMLPrefsManager.get(save) + pack.getString()

                set(pack.context, save, value)

                (pack.context as Reloadable).addMessage(
                    save.parent()!!.path(),
                    save.label() + " -> " + value
                )

                return null
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                pack.args = arrayOf<Any?>(pack.args!![0], pack.args!![1], Tuils.EMPTYSTRING)
                return Param.set.exec(pack)
            }
        },
        erase {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()
                ohi.andre.consolelauncher.managers.settings.LauncherSettings.erase(pack.context, save)

                (pack.context as Reloadable).addMessage(
                    save.parent()!!.path(),
                    save.label() + " -> " + "\"\""
                )

                return null
            }
        },
        get {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()
                val s = XMLPrefsManager.get(String::class.java, save)
                if (s.isNullOrEmpty()) return "\"\""
                return s
            }
        },
        ls {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_FILE)
            }

            override fun exec(pack: ExecutePack): String? {
                val file = File(Tuils.getFolder(), pack.getString())
                val name = file.getName()

                for (r in XMLPrefsRoot.values()) {
                    if (name.equals(r.path, ignoreCase = true)) {
                        val strings: MutableList<String?> = ArrayList(r.getValues()!!.values())
                        Tuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                        strings.add(0, r.path)
                        return Tuils.toPlanString(strings, Tuils.NEWLINE)
                    }
                }

                if (name.equals(AppsManager.PATH, ignoreCase = true)) {
                    val strings: MutableList<String?> = ArrayList(AppsManager.instance!!.getValues()!!.values())
                    Tuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                    strings.add(0, AppsManager.PATH)
                    return Tuils.toPlanString(strings, Tuils.NEWLINE)
                }

                if (name.equals(NotificationManager.PATH, ignoreCase = true)) {
                    val strings: MutableList<String?> =
                        ArrayList(NotificationManager.instance!!.getValues()!!.values())
                    Tuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                    strings.add(0, NotificationManager.PATH)
                    return Tuils.toPlanString(strings, Tuils.NEWLINE)
                }

                if (name.equals(RssManager.PATH, ignoreCase = true)) {
                    val strings: MutableList<String?> =
                        ArrayList(NotificationManager.instance!!.getValues()!!.values())
                    Tuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                    strings.add(0, RssManager.PATH)
                    return Tuils.toPlanString(strings, Tuils.NEWLINE)
                }

                return "[]"
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_filenotfound)
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                val ss: MutableList<String?> = ArrayList<String?>()

                for (element in XMLPrefsRoot.values()) {
                    ss.add(element.path)
                    for (save in element.enums) {
                        ss.add(Tuils.DOUBLE_SPACE + save.label())
                    }
                }
                ss.add(AppsManager.PATH)
                for (save in Apps.values()) {
                    ss.add(Tuils.DOUBLE_SPACE + save.label())
                }
                ss.add(NotificationManager.PATH)
                for (save in Notifications.values()) {
                    ss.add(Tuils.DOUBLE_SPACE + save.label())
                }
                ss.add(RssManager.PATH)
                for (save in Rss.values()) {
                    ss.add(Tuils.DOUBLE_SPACE + save.label())
                }

                return Tuils.toPlanString(ss)
            }
        },
        fontsize {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun exec(pack: ExecutePack): String? {
                val size = pack.getInt()

                LauncherSettings.applyChanges(pack.context, listOf(Ui.device_size, Ui.ram_size, Ui.network_size,
                    Ui.storage_size, Ui.battery_size, Ui.notes_size, Ui.time_size, Ui.weather_size, Ui.unlock_size,
                    Ui.input_output_size).associateWith { size.toString() })

                return null
            }
        },
        reset {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_ENTRY)
            }

            override fun exec(pack: ExecutePack): String? {
                val save = pack.getPrefsSave()
                set(pack.context, save, save!!.defaultValue())

                (pack.context as Reloadable).addMessage(
                    save.parent()!!.path(),
                    save.label() + " -> " + save.defaultValue()
                )
                return null
            }
        },
        apply {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.FILE)
            }

            override fun exec(pack: ExecutePack): String? {
                val file = pack.get(File::class.java)

                if (!file!!.getName().endsWith(".xml")) {
                    // is font - remove existing fonts first
                    val tuiFolder = Tuils.getFolder()
                    val files = tuiFolder.listFiles()
                    if (files != null) {
                        for (f in files) {
                            val name = f.getName().lowercase(Locale.ROOT)
                            if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                                Tuils.insertOld(f)
                            }
                        }
                    }
                } else {
                    val toPutInsideOld = File(Tuils.getFolder(), file.getName())
                    Tuils.insertOld(toPutInsideOld)
                }

                val dest = File(Tuils.getFolder(), file.getName())
                file.renameTo(dest)

                return pack.context.getString(R.string.command_config_path_e8427, dest.getAbsolutePath())
            }
        },
        tutorial {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://re-tui.pages.dev/config"))
                return null
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_config)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return pack.context.getString(R.string.output_invalidarg)
        }

        companion object {
            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.ROOT)
                val ps = entries.toTypedArray()
                for (p1 in ps) if (p.endsWith(p1.label()!!)) return p1
                return null
            }

            fun labels(): Array<String?> {
                val ps = entries.toTypedArray()
                val ss = arrayOfNulls<String>(ps.size)

                for (count in ps.indices) {
                    ss[count] = ps[count].label()
                }

                return ss
            }
        }
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }

    override fun priority(): Int {
        return 4
    }

    override fun helpRes(): Int {
        return R.string.help_config
    }

    companion object {
    }
}
