package ohi.andre.consolelauncher.commands.main.raw

import android.app.Activity
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
                set(pack.context, save, value)

                (pack.context as Reloadable).addMessage(
                    save.parent()!!.path(),
                    save.label() + " -> " + value
                )

                if (save.label()!!.startsWith("default_app_n")) {
                    return pack.context.getString(R.string.output_usedefapp)
                } else if (save === Behavior.unlock_counter_cycle_start) {
                    val preferences = pack.context.getSharedPreferences(UIManager.PREFS_NAME, 0)
                    preferences.edit()
                        .putLong(UIManager.NEXT_UNLOCK_CYCLE_RESTART, 0)
                        .putInt(UIManager.UNLOCK_KEY, 0)
                        .apply()
                } else if (save === Behavior.show_module_dock) {
                    val intent = Intent(UIManager.ACTION_MODULE_COMMAND)
                    intent.putExtra(UIManager.EXTRA_MODULE_COMMAND, "rebuild")
                    LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                        .sendBroadcast(intent)
                } else if (save === Behavior.show_tmux_workspace_button
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshTermuxWorkspaceToolbarButton()
                } else if (save === Behavior.show_android_widget_drawer_button
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshAndroidWidgetDrawerToolbarButton()
                } else if ((save === Behavior.enable_cyberdeck_mode || save === Behavior.enable_crt_filter) && pack.context is Reloadable) {
                    (pack.context as Reloadable).reload()
                } else if (save === Behavior.duo_mode && !"true".equals(
                        value,
                        ignoreCase = true
                    ) && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
                } else if (save === Behavior.orientation && pack.context is LauncherActivity) {
                    (pack.context as LauncherActivity).applyOrientationPreference()
                } else if (isResponsiveLandscapeSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshResponsiveLandscapeLayout()
                } else if (isDisplayMarginSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshDisplayMargins()
                } else if (isAndroidWidgetGridSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshAndroidWidgetDrawerGrid()
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

                return ("Type:" + Tuils.SPACE + save!!.type() + Tuils.NEWLINE
                        + "Default:" + Tuils.SPACE + save.defaultValue() + Tuils.NEWLINE
                        + save.info())
            }
        },
        file {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.CONFIG_FILE)
            }

            override fun exec(pack: ExecutePack): String? {
                val file = File(Tuils.getFolder(), pack.getString())

                val intent = Intent(pack.context, WidgetEditorActivity::class.java)
                intent.putExtra(WidgetEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath())
                if (pack.context is Activity) {
                    (pack.context as Activity).startActivityForResult(
                        intent,
                        LauncherActivity.TUIXT_REQUEST
                    )
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    pack.context.startActivity(intent)
                }

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
                set(pack.context, save, Tuils.EMPTYSTRING)

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

                set(pack.context, Ui.device_size, size.toString())
                set(pack.context, Ui.ram_size, size.toString())
                set(pack.context, Ui.network_size, size.toString())
                set(pack.context, Ui.storage_size, size.toString())
                set(pack.context, Ui.battery_size, size.toString())
                set(pack.context, Ui.notes_size, size.toString())
                set(pack.context, Ui.time_size, size.toString())
                set(pack.context, Ui.weather_size, size.toString())
                set(pack.context, Ui.unlock_size, size.toString())
                set(pack.context, Ui.input_output_size, size.toString())

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
                if (save === Behavior.orientation && pack.context is LauncherActivity) {
                    (pack.context as LauncherActivity).applyOrientationPreference()
                } else if ((save === Behavior.enable_cyberdeck_mode || save === Behavior.enable_crt_filter) && pack.context is Reloadable) {
                    (pack.context as Reloadable).reload()
                } else if (save === Behavior.show_tmux_workspace_button
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshTermuxWorkspaceToolbarButton()
                } else if (save === Behavior.show_android_widget_drawer_button
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshAndroidWidgetDrawerToolbarButton()
                } else if (save === Behavior.duo_mode && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.setDuoLayoutMode(UIManager.DUO_LAYOUT_OFF)
                } else if (isResponsiveLandscapeSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshResponsiveLandscapeLayout()
                } else if (isDisplayMarginSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshDisplayMargins()
                } else if (isAndroidWidgetGridSetting(save)
                    && pack.context is LauncherActivity
                    && (pack.context as LauncherActivity).uiManager != null
                ) {
                    (pack.context as LauncherActivity).uiManager!!.refreshAndroidWidgetDrawerGrid()
                }

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
                            val name = f.getName().lowercase(Locale.getDefault())
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

                return "Path: " + dest.getAbsolutePath()
            }
        },
        tutorial {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Customize-T_UI"))
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
                p = p.lowercase(Locale.getDefault())
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
        private fun isDisplayMarginSetting(save: XMLPrefsSave?): Boolean {
            return save === Ui.display_margin_top_section || save === Ui.display_margin_bottom_section || save === Ui.display_margin_landscape_mm
        }

        private fun isResponsiveLandscapeSetting(save: XMLPrefsSave?): Boolean {
            return save === Ui.split_duo_launcher
                    || save === Ui.show_ascii_landscape
        }

        private fun isAndroidWidgetGridSetting(save: XMLPrefsSave?): Boolean {
            return save === Ui.android_widget_grid_columns
                    || save === Ui.android_widget_min_columns
                    || save === Ui.android_widget_min_rows
        }
    }
}
