package ohi.andre.consolelauncher.managers.notifications

import ohi.andre.consolelauncher.BuildConfig
import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import android.text.SpannableString
import android.text.TextUtils
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.PublicIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils

class KeeperService : android.app.Service() {
    private var title: kotlin.String? = null
    private var subtitle: kotlin.String? = null
    private var clickCmd: kotlin.String? = null
    private var inputFormat: kotlin.String? = null
    private var prefix: kotlin.String? = null
    private var suPrefix: kotlin.String? = null
    private var showHome = false
    private var upDown = false
    private var inputColor = 0
    private var timeColor = 0
    private var priority = 0

    private var lastCommands: kotlin.Array<kotlin.CharSequence?>? = null

    private fun resolvePath(intent: Intent?): kotlin.String {
        val path: kotlin.String? =
            if (intent != null) intent.getStringExtra(KeeperService.Companion.PATH_KEY) else null
        if (!android.text.TextUtils.isEmpty(path)) {
            return path!!
        }

        try {
            val homeDir: java.io.File? = XMLPrefsManager.get(
                java.io.File::class.java,
                ohi.andre.consolelauncher.managers.xml.options.Behavior.home_path
            )
            if (homeDir != null) {
                return homeDir.getAbsolutePath()
            }
        } catch (ignored: java.lang.Exception) {
        }

        return Tuils.getFolder().getAbsolutePath()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (startId == 1 || startId == 0) {
            title =
                XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_title)
            subtitle =
                XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_subtitle)
            clickCmd =
                XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_click_cmd)
            inputFormat =
                XMLPrefsManager.get(ohi.andre.consolelauncher.managers.xml.options.Behavior.input_format)
            showHome =
                XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_click_showhome)
            inputColor =
                XMLPrefsManager.getColor(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_input_text_color)
            timeColor =
                XMLPrefsManager.getColor(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_time_text_color)
            prefix = XMLPrefsManager.get(Ui.input_prefix)
            upDown =
                XMLPrefsManager.getBoolean(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_lastcmds_updown)
            suPrefix = XMLPrefsManager.get(Ui.input_root_prefix)

            priority =
                XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_priority)
            if (priority > 2) priority = 2
            if (priority < -2) priority = -2

            val path = resolvePath(intent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    KeeperService.Companion.ONGOING_NOTIFICATION_ID,
                    KeeperService.Companion.buildNotification(
                        getApplicationContext(), title, subtitle, Tuils.getHint(path),
                        clickCmd, showHome, lastCommands, upDown, priority
                    ),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    KeeperService.Companion.ONGOING_NOTIFICATION_ID,
                    KeeperService.Companion.buildNotification(
                        getApplicationContext(), title, subtitle, Tuils.getHint(path),
                        clickCmd, showHome, lastCommands, upDown, priority
                    )
                )
            }

            val lastCmdSize: Int =
                XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Behavior.tui_notification_lastcmds_size)
            if (lastCmdSize > 0) {
                lastCommands = kotlin.arrayOfNulls<kotlin.CharSequence>(lastCmdSize)
            }
        } else {
//            new cmd
//            update the list

            if (lastCommands != null) updateCmds(intent.getStringExtra(KeeperService.Companion.CMD_KEY))

            val path = resolvePath(intent)

            if (canPostNotifications()) {
                NotificationManagerCompat.from(getApplicationContext()).notify(
                    KeeperService.Companion.ONGOING_NOTIFICATION_ID,
                    KeeperService.Companion.buildNotification(
                        getApplicationContext(), title, subtitle, Tuils.getHint(path),
                        clickCmd, showHome, lastCommands, upDown, priority
                    )
                )
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun canPostNotifications(): kotlin.Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    //    0 = most recent
    //    4 = oldest
    //    * = null
    //    3 cases
    //    1: |*|*|*|*|*| -> lastNull = 0
    //    2: |a|b|c|*|*| -> lastNull = n < length
    //    3: |a|b|c|d|e| -> lastNull = -1
    private fun updateCmds(cmd: kotlin.String?) {
        try {
            val lastNull = lastNull()
            val toCopy = if (lastNull == -1) lastCommands!!.size - 1 else lastNull
            java.lang.System.arraycopy(lastCommands, 0, lastCommands, 1, toCopy)
            lastCommands!![0] = KeeperService.Companion.formatInput(
                cmd,
                inputFormat,
                prefix,
                suPrefix,
                inputColor,
                timeColor
            )
        } catch (e: java.lang.Exception) {
            Tuils.log(e)
        }
    }

    private fun lastNull(): Int {
        for (c in lastCommands!!.indices) if (lastCommands!![c] == null) return c
        return -1
    }

    override fun onUnbind(intent: Intent?): kotlin.Boolean {
        lastCommands = null
        return true
    }

    companion object {
        //    private final String PATH = "reply.xml";
        //    public static final String BIND_NODE = "binding", ID_ATTRIBUTE = "id", APP_ATTRIBUTE = "pkg";
        const val ONGOING_NOTIFICATION_ID: Int = 1001
        const val CMD_KEY: kotlin.String = "cmd"
        const val PATH_KEY: kotlin.String = "path"

        private fun formatInput(
            cmd: kotlin.String?,
            inputFormat: kotlin.String?,
            prefix: kotlin.String?,
            suPrefix: kotlin.String?,
            inputColor: Int,
            timeColor: Int
        ): kotlin.CharSequence? {
            if (cmd == null) return null
            val su = cmd.startsWith("su ")

            val si: SpannableString = Tuils.span(inputFormat, inputColor) ?: SpannableString(inputFormat ?: Tuils.EMPTYSTRING)

            var s: kotlin.CharSequence? =
                ohi.andre.consolelauncher.managers.TimeManager.instance!!.replace(si, timeColor)
            s = android.text.TextUtils.replace(
                s,
                kotlin.arrayOf<kotlin.String>(
                    TerminalManager.Companion.FORMAT_INPUT,
                    TerminalManager.Companion.FORMAT_PREFIX,
                    TerminalManager.Companion.FORMAT_NEWLINE,
                    TerminalManager.Companion.FORMAT_INPUT.uppercase(java.util.Locale.getDefault()),
                    TerminalManager.Companion.FORMAT_PREFIX.uppercase(java.util.Locale.getDefault()),
                    TerminalManager.Companion.FORMAT_NEWLINE.uppercase(java.util.Locale.getDefault())
                ),
                kotlin.arrayOf<kotlin.CharSequence?>(
                    cmd,
                    if (su) suPrefix else prefix,
                    Tuils.NEWLINE,
                    cmd,
                    if (su) suPrefix else prefix,
                    Tuils.NEWLINE
                )
            )

            return s
        }

        fun buildNotification(
            c: android.content.Context,
            title: kotlin.String?,
            subtitle: kotlin.String?,
            cmdLabel: kotlin.String?,
            clickCmd: kotlin.String?,
            showHome: kotlin.Boolean,
            lastCommands: kotlin.Array<kotlin.CharSequence?>?,
            upDown: kotlin.Boolean,
            priority: Int
        ): android.app.Notification {
            var priority = priority
            if (priority < -2 || priority > 2) priority = NotificationCompat.PRIORITY_DEFAULT

            val pendingIntent: PendingIntent?
            if (showHome) {
                val startMain: Intent = Intent(Intent.ACTION_MAIN)
                startMain.addCategory(Intent.CATEGORY_HOME)
                startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                if (clickCmd != null && clickCmd.length > 0) {
                    startMain.putExtra(PrivateIOReceiver.TEXT, clickCmd)
                }

                pendingIntent = PendingIntent.getActivity(
                    c,
                    0,
                    startMain,
                    Tuils.pendingIntentFlags(PendingIntent.FLAG_CANCEL_CURRENT)
                )
            } else if (clickCmd != null && clickCmd.length > 0) {
                val cmdIntent: Intent = Intent(PublicIOReceiver.ACTION_CMD)
                cmdIntent.putExtra(PrivateIOReceiver.TEXT, clickCmd)

                pendingIntent = PendingIntent.getBroadcast(
                    c,
                    0,
                    cmdIntent,
                    Tuils.pendingIntentFlags(0)
                )
            } else {
                pendingIntent = null
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                var oPriority: Int =
                    Tuils.scale(kotlin.intArrayOf(0, 4), kotlin.intArrayOf(2, 4), priority + 2)
                if (oPriority < 2 || oPriority > 4) oPriority =
                    android.app.NotificationManager.IMPORTANCE_UNSPECIFIED

                val notificationChannel: NotificationChannel = NotificationChannel(
                    BuildConfig.APPLICATION_ID,
                    c.getString(R.string.app_name),
                    oPriority
                )
                (c.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).createNotificationChannel(
                    notificationChannel
                )
            }

            val builder: NotificationCompat.Builder =
                NotificationCompat.Builder(c, BuildConfig.APPLICATION_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker(c.getString(R.string.start_notification))
                    .setWhen(java.lang.System.currentTimeMillis())
                    .setPriority(priority)
                    .setContentTitle(title)
                    .setContentIntent(pendingIntent)

            var style: NotificationCompat.Style? = null
            if (lastCommands != null && lastCommands[0] != null) {
                val inboxStyle: NotificationCompat.InboxStyle = NotificationCompat.InboxStyle()

                if (upDown) {
                    for (lastCommand in lastCommands) {
                        if (lastCommand == null) break
                        inboxStyle.addLine(lastCommand)
                    }
                } else {
                    for (j in lastCommands.indices.reversed()) {
                        if (lastCommands[j] == null) continue
                        inboxStyle.addLine(lastCommands[j])
                    }
                }

                style = inboxStyle
            }

            if (style != null) builder.setStyle(style)
            else {
                builder.setContentTitle(title)
                builder.setContentText(subtitle)
            }

            val remoteInput = androidx.core.app.RemoteInput.Builder(PrivateIOReceiver.TEXT)
                .setLabel(cmdLabel)
                .build()

            val i: Intent = Intent(c, PublicIOReceiver::class.java)
            i.setAction(PublicIOReceiver.ACTION_CMD)

            val actionBuilder: androidx.core.app.NotificationCompat.Action.Builder =
                androidx.core.app.NotificationCompat.Action.Builder(
                    R.mipmap.ic_launcher,
                    cmdLabel,
                    PendingIntent.getBroadcast(
                        c.getApplicationContext(),
                        40,
                        i,
                        KeeperService.Companion.remoteInputPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                )
                    .addRemoteInput(remoteInput)

            builder.addAction(actionBuilder.build())

            return builder.build()
        }

        private fun remoteInputPendingIntentFlags(flags: Int): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return flags or PendingIntent.FLAG_MUTABLE
            }
            return flags
        }
    }
}
