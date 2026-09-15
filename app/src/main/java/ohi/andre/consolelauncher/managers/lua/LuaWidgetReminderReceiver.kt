package ohi.andre.consolelauncher.managers.lua

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import ohi.andre.consolelauncher.LauncherActivity
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.LauncherSoundManager

class LuaWidgetReminderReceiver : BroadcastReceiver() {
    override fun onReceive(rawContext: Context, intent: Intent) {
        val context = ohi.andre.consolelauncher.localization.LanguagePacks.wrap(rawContext)
        val record = LuaWidgetReminderManager.fire(
            context,
            intent.getStringExtra(LuaWidgetReminderManager.EXTRA_KEY)
        ) ?: return

        createChannel(context)

        val launch = Intent(context, LauncherActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = PendingIntent.getActivity(
            context,
            abs(record.key.hashCode()),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (record.title.trim { it <= ' ' }.isEmpty()) context.getString(R.string.integration_luawidgetreminderreceiver_habit_reminder_5378e) else record.title
        val soundPackEnabled = LauncherSoundManager.isEnabled()
        LauncherSoundManager.play(context, LauncherSoundManager.Event.NOTIFICATION)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.integration_luawidgetreminderreceiver_re_t_ui_module_52869))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(if (soundPackEnabled) null else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSilent(soundPackEnabled)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.notify(abs(record.key.hashCode()), builder.build())
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.integration_luawidgetreminderreceiver_re_t_ui_module_notifications_424d3),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = context.getString(R.string.integration_luawidgetreminderreceiver_notifications_scheduled_by_re_t_ui_lua_mod_55ae4)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "retui_lua_widget_reminders"
    }
}
