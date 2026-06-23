package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.set
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Locale

class apps : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        ls {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable {
                        if (LauncherActivity.instance!!.uiManager != null) {
                            LauncherActivity.instance!!.uiManager!!.showAppsDrawer()
                        }
                    })
                    return null
                }
                return (pack as MainPack).appsManager.printApps(
                    AppsManager.SHOWN_APPS,
                    pack.getString()
                )
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                if (LauncherActivity.instance != null) {
                    LauncherActivity.instance!!.runOnUiThread(Runnable {
                        if (LauncherActivity.instance!!.uiManager != null) {
                            LauncherActivity.instance!!.uiManager!!.showAppsDrawer()
                        }
                    })
                    return null
                }
                return (pack as MainPack).appsManager.printApps(AppsManager.SHOWN_APPS)
            }
        },
        lsh {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.PLAIN_TEXT)
            }

            override fun exec(pack: ExecutePack): String? {
                return (pack as MainPack).appsManager.printApps(
                    AppsManager.HIDDEN_APPS,
                    pack.getString()
                )
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                return (pack as MainPack).appsManager.printApps(AppsManager.HIDDEN_APPS)
            }
        },
        show {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.HIDDEN_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                val i = pack.getLaunchInfo()
                (pack as MainPack).appsManager.showActivity(i)
                return null
            }
        },
        hide {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                val i = pack.getLaunchInfo()
                (pack as MainPack).appsManager.hideActivity(i)
                return null
            }
        },
        l {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                try {
                    val i = pack.getLaunchInfo()

                    val info = pack.context.getPackageManager().getPackageInfo(
                        i.componentName!!.packageName,
                        PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
                    )
                    return AppsManager.AppUtils.format(i, info)
                } catch (e: PackageManager.NameNotFoundException) {
                    return e.toString()
                }
            }
        },
        ps {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                openPlaystore(pack.context, pack.getLaunchInfo().componentName!!.packageName)
                return null
            }
        },
        default_app {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.INT, CommandAbstraction.DEFAULT_APP)
            }

            override fun exec(pack: ExecutePack): String? {
                val index = pack.getInt()

                val o = pack.get()

                val marker: String?
                if (o is LaunchInfo) {
                    val i = o
                    marker = i.write()
                } else {
                    marker = o as String?
                }

                try {
                    val save: XMLPrefsSave = Apps.valueOf("default_app_n" + index)
                    set(pack.context, save, marker)
                    return null
                } catch (e: Exception) {
                    return pack.context.getString(R.string.invalid_integer)
                }
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                val res: Int
                if (index == 1) res = R.string.invalid_integer
                else res = R.string.output_appnotfound

                return pack.context.getString(res)
            }
        },
        st {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                openSettings(pack.context, pack.getLaunchInfo().componentName!!.packageName)
                return null
            }
        },
        frc {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.ALL_PACKAGES)
            }

            override fun exec(pack: ExecutePack): String? {
                (pack as MainPack).appsManager.launch(pack.context, pack.getLaunchInfo())
                return null
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
                        File(Tuils.getFolder(), AppsManager.PATH)
                    )
                )
                return null
            }
        },

        reset {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                val app = pack.getLaunchInfo()
                app!!.launchedTimes = 0
                (pack as MainPack).appsManager.writeLaunchTimes(app)

                return null
            }
        },
        mkgp {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.NO_SPACE_STRING)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                return (pack as MainPack).appsManager.createGroup(name)
            }
        },
        rmgp {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                return (pack as MainPack).appsManager.removeGroup(name)
            }
        },
        gp_bg_color {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP, CommandAbstraction.COLOR)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                val color = pack.getString()
                return (pack as MainPack).appsManager.groupBgColor(name, color)
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                if (n == 2) {
                    val name = pack.getString()
                    return (pack as MainPack).appsManager.groupBgColor(name, Tuils.EMPTYSTRING)
                }
                return super.onNotArgEnough(pack, n)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_invalidcolor)
            }
        },
        gp_fore_color {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP, CommandAbstraction.COLOR)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                val color = pack.getString()
                return (pack as MainPack).appsManager.groupForeColor(name, color)
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                if (n == 2) {
                    val name = pack.getString()
                    return (pack as MainPack).appsManager.groupForeColor(name, Tuils.EMPTYSTRING)
                }
                return super.onNotArgEnough(pack, n)
            }

            override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
                return pack.context.getString(R.string.output_invalidcolor)
            }
        },
        lsgp {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                return (pack as MainPack).appsManager.listGroup(name)
            }

            override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
                return (pack as MainPack).appsManager.listGroups()
            }
        },
        addtogp {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP, CommandAbstraction.VISIBLE_PACKAGE)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                val app = pack.getLaunchInfo()
                return (pack as MainPack).appsManager.addAppToGroup(name, app)
            }
        },
        rmfromgp {
            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.APP_GROUP, CommandAbstraction.APP_INSIDE_GROUP)
            }

            override fun exec(pack: ExecutePack): String? {
                val name = pack.getString()
                val app = pack.getLaunchInfo()
                return (pack as MainPack).appsManager.removeAppFromGroup(name, app)
            }
        },
        tutorial {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage(APPS_DOC_URL))
                return null
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_apps)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return pack.context.getString(R.string.output_appnotfound)
        }

        companion object {
            fun get(p: String): Param? {
                var p = p
                p = p.lowercase(Locale.getDefault())
                if ("-gpcolor" == p) {
                    return Param.gp_bg_color
                }
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

    override fun helpRes(): Int {
        return R.string.help_apps
    }

    override fun priority(): Int {
        return 4
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    companion object {
        private const val APPS_DOC_URL = "https://dvilspawn.github.io/Re-TUI/apps.html"

        private fun openSettings(context: Context, packageName: String?) {
            Tuils.openSettingsPage(context, packageName)
        }

        private fun openPlaystore(context: Context, packageName: String?) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + packageName)
                    )
                )
            } catch (e: Exception) {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)
                    )
                )
            }
        }
    }
}
