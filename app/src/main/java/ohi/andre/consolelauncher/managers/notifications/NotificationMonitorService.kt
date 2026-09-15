package ohi.andre.consolelauncher.managers.notifications

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import ohi.andre.consolelauncher.tuils.Tuils

class NotificationMonitorService : Service() {
    override fun getResources(): android.content.res.Resources =
        ohi.andre.consolelauncher.localization.LanguagePacks.resources(super.getResources())

    override fun onCreate() {
        super.onCreate()
        ensureCollectorRunning()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureCollectorRunning()
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private fun ensureCollectorRunning() {
        if (Tuils.notificationServiceIsRunning(this)) {
            toggleNotificationListenerService()
        }
    }

    private fun toggleNotificationListenerService() {
        val thisComponent = ComponentName(this, NotificationService::class.java)
        val pm = packageManager
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(thisComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
