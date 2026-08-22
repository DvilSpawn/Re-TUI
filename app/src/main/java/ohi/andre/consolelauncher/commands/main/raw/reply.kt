package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.Locale
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.tuils.Tuils

class reply : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        to {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.BOUND_REPLY_APP, CommandAbstraction.PLAIN_TEXT)

            override fun exec(pack: ExecutePack): String? {
                val intent = Intent(ReplyManager.ACTION)

                intent.putExtra(ReplyManager.ID, pack.getString())
                intent.putExtra(ReplyManager.WHAT, pack.getString())

                LocalBroadcastManager.getInstance(pack.context).sendBroadcast(intent)
                return null
            }
        },
        bind {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)

            override fun exec(pack: ExecutePack): String {
                val output = ReplyManager.bind(pack.getLaunchInfo().componentName!!.packageName)
                LocalBroadcastManager.getInstance(pack.context).sendBroadcast(Intent(ReplyManager.ACTION_UPDATE))
                NotificationService.requestReload(pack.context)
                return output ?: Tuils.EMPTYSTRING
            }
        },
        check {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.BOUND_REPLY_APP)

            override fun exec(pack: ExecutePack): String? {
                val intent = Intent(ReplyManager.ACTION)
                intent.putExtra(ReplyManager.ID, pack.getString())

                LocalBroadcastManager.getInstance(pack.context).sendBroadcast(intent)
                return null
            }
        },
        unbind {
            override fun args(): IntArray = intArrayOf(CommandAbstraction.VISIBLE_PACKAGE)

            override fun exec(pack: ExecutePack): String? {
                val launchInfo = pack.getLaunchInfo()
                val packageName = launchInfo.componentName!!.packageName
                val output = ReplyManager.unbind(packageName)
                LocalBroadcastManager.getInstance(pack.context).sendBroadcast(Intent(ReplyManager.ACTION_UPDATE))
                NotificationService.requestReload(pack.context)

                if (output != null && output.isEmpty()) {
                    return pack.context.getString(R.string.reply_app_not_found) + packageName
                }
                return output
            }
        },
        ls {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String? {
                val intent = Intent(ReplyManager.ACTION_LS)
                LocalBroadcastManager.getInstance(pack.context).sendBroadcast(intent)
                return null
            }
        },
        tutorial {
            override fun args(): IntArray = IntArray(0)

            override fun exec(pack: ExecutePack): String? {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Reply"))
                return null
            }
        };

        override fun label(): String = Tuils.MINUS + name

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String =
            pack.context.getString(R.string.help_reply)

        override fun onArgNotFound(pack: ExecutePack, index: Int): String =
            pack.context.getString(R.string.help_reply)

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

    override fun params(): Array<String> = Param.labels()

    override fun paramForString(pack: MainPack, param: String): ohi.andre.consolelauncher.commands.main.Param? =
        Param.get(param)

    override fun doThings(pack: ExecutePack): String? = null

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_reply

}
