package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.os.Build.VERSION_CODES
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.APICommand
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Arrays
import java.util.Locale
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import android.os.Build.VERSION_CODES.JELLY_BEAN_MR2

/**
 * Created by francescoandreuzzi on 29/04/2017.
 */
class notifications : ParamCommand(), APICommand {
    override fun willWorkOn(api: Int): Boolean {
        return api >= VERSION_CODES.JELLY_BEAN_MR2
    }

    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        inc {
            override fun exec(pack: ExecutePack): String? {
                val output = NotificationManager.setState(
                    pack.getLaunchInfo().componentName!!.packageName,
                    true
                )
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_appnotfound)
            }
        },
        exc {
            override fun exec(pack: ExecutePack): String? {
                val output = NotificationManager.setState(
                    pack.getLaunchInfo().componentName!!.packageName,
                    false
                )
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_appnotfound)
            }
        },
        color {
            override fun exec(pack: ExecutePack): String? {
                val color = pack.getString()
                val output = NotificationManager.setColor(
                    pack.getLaunchInfo().componentName!!.packageName,
                    color
                )
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.COLOR, CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                val res: Int
                if (index == 1) res = R.string.output_invalidcolor
                else res = R.string.output_appnotfound

                return pack.context.getString(res)
            }
        },
        format {
            override fun exec(pack: ExecutePack): String? {
                val s = pack.getString()
                val output = NotificationManager.setFormat(
                    pack.getLaunchInfo().componentName!!.packageName,
                    s
                )
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(
                    CommandAbstraction.NO_SPACE_STRING,
                    CommandAbstraction.VISIBLE_PACKAGE
                )
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        },
        add_filter {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val output = NotificationManager.addFilter(pack.getString(), id)
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        },
        add_format {
            override fun exec(pack: ExecutePack): String? {
                val id = pack.getInt()
                val output = NotificationManager.addFormat(pack.getString(), id)
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        },
        rm_filter {
            override fun exec(pack: ExecutePack): String? {
                val output = NotificationManager.rmFilter(pack.getInt())
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        },
        rm_format {
            override fun exec(pack: ExecutePack): String? {
                val output = NotificationManager.rmFormat(pack.getInt())
                NotificationService.requestReload(pack.context)
                if (output == null || output.length == 0) return null
                return output
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.invalid_integer)
            }
        },
        file {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(
                    Tuils.openFile(
                        pack.context,
                        File(Tuils.getFolder(), NotificationManager.PATH)
                    )
                )
                return null
            }
        },
        ls {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                val manager = NotificationManager.create(pack.context)
                return manager.describeRules()
            }
        },
        access {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                try {
                    pack.context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                } catch (e: Exception) {
                    return pack.context.getString(R.string.activity_not_found)
                }
                return null
            }
        },

        `open` {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null && LauncherActivity.instance!!.uiManager != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable { LauncherActivity.instance!!.uiManager!!.openNotificationShade() })
                    return null
                }

                try {
                    val sbservice = pack.context.getSystemService("statusbar")
                    val statusbarManager = Class.forName("android.app.StatusBarManager")
                    val expand = statusbarManager.getMethod("expandNotificationsPanel")
                    expand.invoke(sbservice)
                    return null
                } catch (e: Exception) {
                    return e.toString()
                }
            }
        },
        next {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null && LauncherActivity.instance!!.uiManager != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable { LauncherActivity.instance!!.uiManager!!.nextNotificationPage() })
                    return null
                }
                return "Notification module is not available."
            }
        },
        prev {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null && LauncherActivity.instance!!.uiManager != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable { LauncherActivity.instance!!.uiManager!!.previousNotificationPage() })
                    return null
                }
                return "Notification module is not available."
            }
        },
        reply {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null && LauncherActivity.instance!!.uiManager != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable { LauncherActivity.instance!!.uiManager!!.startCurrentNotificationReply() })
                    return null
                }
                return "Notification module is not available."
            }
        },
        clear {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null && LauncherActivity.instance!!.uiManager != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable { LauncherActivity.instance!!.uiManager!!.dismissCurrentNotification() })
                    return null
                }
                return "Notification module is not available."
            }
        },
        tutorial {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Notifications"))
                return null
            }
        },
        on {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String {
                return setNotificationTerminal(pack, true)
            }
        },
        off {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String {
                return setNotificationTerminal(pack, false)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_notifications)
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

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_notifications
    }

    companion object {
        private fun setNotificationTerminal(pack: ExecutePack, enabled: Boolean): String {
            if (enabled) {
                ModuleManager.addToDock(
                    pack.context,
                    Arrays.asList<String?>(ModuleManager.NOTIFICATIONS)
                )
            } else {
                ModuleManager.removeFromDock(
                    pack.context,
                    Arrays.asList<String?>(ModuleManager.NOTIFICATIONS)
                )
            }

            val rebuild = Intent(UIManager.ACTION_MODULE_COMMAND)
            rebuild.putExtra(UIManager.EXTRA_MODULE_COMMAND, "rebuild")
            LocalBroadcastManager.getInstance(pack.context.getApplicationContext())
                .sendBroadcast(rebuild)

            return if (enabled) "Notification module added to dock." else "Notification module removed from dock."
        }
    }
}
