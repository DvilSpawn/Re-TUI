package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class tuiweather : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        update {
            override fun exec(pack: ExecutePack): String? {
                LocalBroadcastManager.getInstance(pack.context.applicationContext)
                    .sendBroadcast(Intent(UIManager.ACTION_WEATHER_MANUAL_UPDATE))
                return null
            }
        },
        enable {
            override fun exec(pack: ExecutePack): String? {
                val save: XMLPrefsSave = Ui.show_weather

                LauncherSettings.set(pack.context, save, "true")
                val reloadable = pack.context as Reloadable
                reloadable.addMessage(save.parent()!!.path(), save.label() + " -> " + "true")
                reloadable.reload()

                return null
            }
        },
        disable {
            override fun exec(pack: ExecutePack): String? {
                val save: XMLPrefsSave = Ui.show_weather

                LauncherSettings.set(pack.context, save, "false")
                val reloadable = pack.context as Reloadable
                reloadable.addMessage(save.parent()!!.path(), save.label() + " -> " + "false")
                reloadable.reload()

                return null
            }
        },
        tutorial {
            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Weather"))
                return null
            }
        },
        set_location {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String {
                val location = pack.getString().trim()
                if (location.isEmpty()) return "Usage: tuiweather -set_location <place or lat,lon>"
                LauncherSettings.set(pack.context, Behavior.weather_location, location)
                LocalBroadcastManager.getInstance(pack.context.applicationContext)
                    .sendBroadcast(Intent(UIManager.ACTION_WEATHER_MANUAL_UPDATE))
                return "Weather location set: $location"
            }
        };

        override fun args(): IntArray = IntArray(0)

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_shortcut)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.output_appnotfound)

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

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_weather

    override fun params(): Array<String> = Param.labels()

    override fun doThings(pack: ExecutePack): String? = null
}
