package ohi.andre.consolelauncher.managers.modules

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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(rawContext: Context, intent: Intent) {
        val context = ohi.andre.consolelauncher.localization.LanguagePacks.wrap(rawContext)
        val id = intent.getStringExtra(ReminderManager.EXTRA_ID)
        var title = intent.getStringExtra(ReminderManager.EXTRA_TITLE)
        if (title == null || title.trim().isEmpty()) {
            title = context.getString(R.string.integration_reminderreceiver_reminder_b87a1)
        }

        createChannel(context)

        val launch = Intent(context, LauncherActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val content = PendingIntent.getActivity(
            context,
            if (id == null) 0 else abs(id.hashCode()),
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val soundPackEnabled = LauncherSoundManager.isEnabled()
        LauncherSoundManager.play(context, LauncherSoundManager.Event.REMINDER)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.integration_reminderreceiver_re_t_ui_reminder_825dc))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(if (soundPackEnabled) null else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setSilent(soundPackEnabled)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        manager?.notify(if (id == null) 4001 else abs(id.hashCode()), builder.build())
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
            context.getString(R.string.integration_reminderreceiver_re_t_ui_reminders_091c3),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = context.getString(R.string.integration_reminderreceiver_reminder_notifications_created_by_re_t_ui_f0999)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "retui_reminders"
    }
}
