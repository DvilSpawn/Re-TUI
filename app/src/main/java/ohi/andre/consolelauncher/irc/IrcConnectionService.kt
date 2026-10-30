package ohi.andre.consolelauncher.irc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ohi.andre.consolelauncher.R

/**
 * Keeps IRC networks connected while the pane is closed ("stay connected"). The connections themselves live in
 * [IrcTerminalSession]; this service only holds the process in the foreground, shows which networks are online,
 * and turns mentions and private messages into notifications.
 */
class IrcConnectionService : Service() {
    companion object {
        const val EXTRA_NETWORK = "irc_network"
        const val EXTRA_BUFFER = "irc_buffer"
        private const val ACTION_DISCONNECT = "ohi.andre.consolelauncher.irc.DISCONNECT"
        private const val CHANNEL_ONGOING = "irc_connected"
        private const val CHANNEL_MENTIONS = "irc_mentions"
        private const val ONGOING_ID = 7301
        private const val MENTION_BASE_ID = 7400
        private const val MENTION_LIMIT = 20
        @Volatile private var running = false

        fun start(context: Context) {
            if (running) return
            // Android refuses to start a foreground service from the background; the pane starts it while visible,
            // and a running service is never restarted here.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, IrcConnectionService::class.java)) }
                .onSuccess { running = true }
        }
        fun stop(context: Context) {
            if (!running) return
            running = false
            context.stopService(Intent(context, IrcConnectionService::class.java))
        }
        fun clearMentions(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            for (id in MENTION_BASE_ID until MENTION_BASE_ID + MENTION_LIMIT) manager.cancel(id)
        }
        private fun channels(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.irc_service_channel), NotificationManager.IMPORTANCE_MIN))
            manager.createNotificationChannel(NotificationChannel(CHANNEL_MENTIONS, context.getString(R.string.irc_mentions_channel), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    private lateinit var session: IrcTerminalSession
    private val mentionIds = LinkedHashMap<String, Int>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        channels(this)
        session = IrcTerminalSession.get(this)
        session.service = { refresh() }
        session.mentioned = { connection, buffer, line -> mention(connection, buffer, line) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            session.disconnectAll()
            stopSelf(); return START_NOT_STICKY
        }
        val notification = ongoing()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ONGOING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ONGOING_ID, notification)
        // Restarted after Android stopped the app: reconnect the networks that were online.
        if (intent == null) session.restore()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        if (session.service != null) session.service = null
        session.mentioned = null
        super.onDestroy()
    }

    private fun refresh() {
        if (!running) return
        runCatching { NotificationManagerCompat.from(this).notify(ONGOING_ID, ongoing()) }
    }

    private fun openPane(requestCode: Int, connection: IrcConnection? = null, buffer: String? = null): PendingIntent =
        PendingIntent.getActivity(this, requestCode, Intent(this, IrcActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { connection?.let { putExtra(EXTRA_NETWORK, it.network.id) }; buffer?.let { putExtra(EXTRA_BUFFER, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun ongoing() = NotificationCompat.Builder(this, CHANNEL_ONGOING)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.irc_service_title))
        .setContentText(summary())
        .setOngoing(true).setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setContentIntent(openPane(0))
        .addAction(0, getString(R.string.irc_service_disconnect), PendingIntent.getService(this, 1,
            Intent(this, IrcConnectionService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun summary(): String {
        val online = session.connections.filter { it.ready }.map { it.network.name }
        val waiting = session.connections.count { it.busy && !it.ready }
        return when {
            online.isEmpty() -> getString(R.string.irc_service_reconnecting)
            waiting == 0 -> getString(R.string.irc_service_connected, online.joinToString(", "))
            else -> getString(R.string.irc_service_connected_some, online.joinToString(", "), waiting)
        }
    }

    private fun mention(connection: IrcConnection, buffer: IrcState.Buffer, line: IrcState.Line) {
        val key = connection.network.id + "\u0000" + IrcProtocol.fold(buffer.name, connection.state.mapping)
        val id = mentionIds.getOrPut(key) { MENTION_BASE_ID + mentionIds.size % MENTION_LIMIT }
        val private = !buffer.name.startsWith('#') && !buffer.name.startsWith('&')
        val title = if (private) "${line.sender} · ${connection.network.name}" else "${buffer.name} · ${connection.network.name}"
        val text = if (private) IrcProtocol.display(line.body) else "<${line.sender}> ${IrcProtocol.display(line.body)}"
        val notification = NotificationCompat.Builder(this, CHANNEL_MENTIONS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setNumber(buffer.unread)
            .setAutoCancel(true)
            .setContentIntent(openPane(id, connection, buffer.name))
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, notification) }
    }
}
