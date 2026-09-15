package ohi.andre.consolelauncher.commands.main.raw

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.notifications.DevReplyReceiver
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

/**
 * Created by francescoandreuzzi on 22/08/2017.
 */
class devutils : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        notify {
            @SuppressLint("MissingPermission")
            override fun exec(pack: ExecutePack): String? {
                val text: MutableList<String?>? = pack.getList<String?>()

                val title: String?
                var txt: String? = null
                if (text!!.size == 0) return null
                else {
                    title = text.removeAt(0)
                    if (text.size >= 2) txt = Tuils.toPlanString(text, Tuils.SPACE)
                }

                val channelId = "dev_utils_channel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel: NotificationChannel = NotificationChannel(
                        channelId,
                        pack.context.getString(R.string.command_devutils_dev_utils_325dc),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    val notificationManager =
                        pack.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                    if (notificationManager != null) {
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                if (!canPostNotifications(pack.context)) {
                    return pack.context.getString(R.string.command_devutils_notification_permission_is_not_granted_2fd2a)
                }

                NotificationManagerCompat.from(pack.context).notify(
                    200,
                    NotificationCompat.Builder(pack.context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(txt)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .build()
                )

                return null
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        notify_reply {
            @SuppressLint("MissingPermission")
            override fun exec(pack: ExecutePack): String? {
                val text: MutableList<String?>? = pack.getList<String?>()

                val title: String?
                var txt: String? = null
                if (text!!.size == 0) return pack.context.getString(R.string.command_devutils_usage_devutils_notify_reply_title_text_97505)
                else {
                    title = text.removeAt(0)
                    if (text.size > 0) txt = Tuils.toPlanString(text, Tuils.SPACE)
                }

                val channelId = "dev_utils_channel"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel: NotificationChannel = NotificationChannel(
                        channelId,
                        pack.context.getString(R.string.command_devutils_dev_utils_325dc),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    val notificationManager =
                        pack.context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
                    if (notificationManager != null) {
                        notificationManager.createNotificationChannel(channel)
                    }
                }

                val remoteInput = RemoteInput.Builder(DevReplyReceiver.RESULT_KEY)
                    .setLabel(pack.context.getString(R.string.command_devutils_reply_6c2bb))
                    .build()

                val replyIntent: Intent = Intent(pack.context, DevReplyReceiver::class.java)
                    .setAction(DevReplyReceiver.ACTION_DEV_REPLY)
                val replyPendingIntent: PendingIntent? = PendingIntent.getBroadcast(
                    pack.context,
                    DevReplyReceiver.NOTIFICATION_ID,
                    replyIntent,
                    pendingIntentFlags()
                )

                val replyAction: NotificationCompat.Action = NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher,
                    pack.context.getString(R.string.command_devutils_reply_6c2bb),
                    replyPendingIntent
                )
                    .addRemoteInput(remoteInput)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .setAllowGeneratedReplies(true)
                    .build()

                if (!canPostNotifications(pack.context)) {
                    return pack.context.getString(R.string.command_devutils_notification_permission_is_not_granted_2fd2a)
                }

                NotificationManagerCompat.from(pack.context).notify(
                    DevReplyReceiver.NOTIFICATION_ID,
                    NotificationCompat.Builder(pack.context, channelId)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(title)
                        .setContentText(txt)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .addAction(replyAction)
                        .build()
                )

                return pack.context.getString(R.string.command_devutils_dev_reply_notification_posted_3cb7a)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        check_notifications {
            override fun args(): IntArray? {
                return IntArray(0)
            }

            override fun exec(pack: ExecutePack): String? {
                return pack.context.getString(R.string.command_devutils_notification_access_notification_service_r_b8c8b, NotificationManagerCompat.getEnabledListenerPackages(
                    pack.context
                )
                    .contains(BuildConfig.APPLICATION_ID), Tuils.NEWLINE, Tuils.notificationServiceIsRunning(
                    pack.context
                ))
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String? {
            return pack.context.getString(R.string.help_devutils)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
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

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return Param.Companion.get(param)
    }

    override fun priority(): Int {
        return 2
    }

    override fun helpRes(): Int {
        return R.string.help_devutils
    }

    public override fun params(): Array<String?> {
        return Param.Companion.labels()
    }

    override fun doThings(pack: ExecutePack): String? {
        return null
    }

    companion object {
        private fun pendingIntentFlags(): Int {
            var flags: Int = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            return flags
        }

        private fun canPostNotifications(context: Context): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
