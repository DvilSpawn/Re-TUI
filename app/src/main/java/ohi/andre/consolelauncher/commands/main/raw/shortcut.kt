package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import androidx.annotation.RequiresApi
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.APICommand
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.PinnedShortcutManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale
import ohi.andre.consolelauncher.managers.AppsManager

/**
 * Created by francescoandreuzzi on 24/03/2018.
 */
@RequiresApi(Build.VERSION_CODES.N_MR1)
class shortcut : ParamCommand(), APICommand {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        use {
            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.NO_SPACE_STRING,
                    CommandAbstraction.VISIBLE_PACKAGE
                )
            }

            override fun exec(pack: ExecutePack): String? {
                val id = pack.getString()
                val mapped = startMappedShortcut(id, pack.context)
                if (mapped == null || mapped.length > 0) return mapped

                val li = pack.getLaunchInfo()

                var shortcut: ShortcutInfo? = null
                val index: Int
                try {
                    index = id!!.toInt()

                    val shortcuts = li.shortcuts?.filterNotNull().orEmpty()
                    if (shortcuts.isEmpty()) return "[]"
                    if (index >= shortcuts.size) return pack.context.getString(R.string.shortcut_index_greater)
                    shortcut = shortcuts[index]
                } catch (e: Exception) {
                    val shortcuts = li.shortcuts?.filterNotNull().orEmpty()
                    if (shortcuts.isEmpty()) return pack.context.getString(
                        R.string.app_shortcut_not_found
                    )

                    for (i in shortcuts) {
                        if (i.id == id) {
                            shortcut = i
                            break
                        }
                    }
                }

                if (shortcut == null) return pack.context.getString(R.string.id_notfound)

                startShortcut(shortcut, pack.context)
                return null
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                if (n == 1) return pack.context.getString(R.string.help_shortcut)

                pack.get()
                val id = pack.getString()

                val mapped = startMappedShortcut(id, pack.context)
                if (mapped == null || mapped.length > 0) return mapped

                var info: ShortcutInfo? = null

                Out@ for (l in (pack as MainPack).appsManager.shownApps()) {
                    val shortcuts = l.shortcuts?.filterNotNull().orEmpty()
                    if (shortcuts.isEmpty()) continue

                    for (i in shortcuts) {
                        if (i.id == id) {
                            info = i

                            break@Out
                        }
                    }
                }

                return startShortcut(info, pack.context)
            }

            fun startShortcut(info: ShortcutInfo?, context: Context): String? {
                if (info == null) return context.getString(R.string.app_shortcut_not_found)

                val apps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                apps.startShortcut(info, null, null)

                return null
            }

            fun startMappedShortcut(id: String?, context: Context?): String? {
                if (id == null || !id.startsWith(PinnedShortcutManager.HANDLE_PREFIX)) return Tuils.EMPTYSTRING
                return PinnedShortcutManager.start(context, id)
            }
        },
        ls {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                val li = pack.getLaunchInfo()
                val shortcuts = li.shortcuts?.filterNotNull().orEmpty()
                if (shortcuts.isEmpty()) return "[]"

                val builder = StringBuilder()
                Param.Companion.append(builder, shortcuts, Tuils.EMPTYSTRING)

                return builder.toString()
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                val infos = (pack as MainPack).appsManager.shownApps()
                val builder = StringBuilder()

                for (l in infos) {
                    val shortcuts = l.shortcuts?.filterNotNull().orEmpty()
                    if (shortcuts.isEmpty()) continue

                    builder.append(l.publicLabel).append(Tuils.NEWLINE)
                    Param.Companion.append(builder, shortcuts, Tuils.DOUBLE_SPACE)
                }

                Param.Companion.appendPinnedAliases(pack.context, builder)

                val s = builder.toString().trim { it <= ' ' }
                if (s.length == 0) return "[]"

                return s
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_shortcut)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return pack.context.getString(R.string.output_appnotfound)
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

            private fun append(
                builder: StringBuilder,
                shortcuts: List<ShortcutInfo>,
                prefix: String?
            ) {
                for (i in shortcuts) {
                    builder.append(prefix).append("- ").append(i.getShortLabel()).append(" (ID: ")
                        .append(i.getId()).append(")")
                    if (i.isPinned()) builder.append(" [pinned]")
                    builder.append(Tuils.NEWLINE)
                }
            }

            private fun appendPinnedAliases(context: Context?, builder: StringBuilder) {
                val records = PinnedShortcutManager.list(context)
                if (records.size == 0) return

                if (builder.length > 0) builder.append(Tuils.NEWLINE)
                builder.append("Pinned aliases").append(Tuils.NEWLINE)
                for (record in records) {
                    if (record == null) continue
                    builder.append(Tuils.DOUBLE_SPACE)
                        .append("- @")
                        .append(record.handle)
                        .append(" -> ")
                        .append(record.label)
                        .append(" (")
                        .append(record.packageName)
                        .append(")")
                        .append(Tuils.NEWLINE)
                }
            }
        }
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun willWorkOn(api: Int): Boolean {
//        return false;
        return api >= Build.VERSION_CODES.N_MR1
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_shortcut
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }
}
