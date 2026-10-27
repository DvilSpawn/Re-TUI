package ohi.andre.consolelauncher.managers.notifications.reply

import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Bundle

class NotificationWear {
    @JvmField var app: BoundApp? = null
    /** The source notification's key and package; replies don't need the app to be bound. */
    @JvmField var key: String? = null
    @JvmField var packageName: String? = null
    @JvmField var pendingIntent: PendingIntent? = null
    @JvmField var remoteInputs: Array<RemoteInput>? = null
    @JvmField var bundle: Bundle? = null
    @JvmField var id = 0
    @JvmField var actionTitle: CharSequence? = null
    @JvmField var semanticAction = 0
    @JvmField var text: CharSequence? = null

    override fun equals(other: Any?): Boolean {
        return try {
            val wear = other as NotificationWear
            if (key != null) wear.key == key else wear.packageName == packageName
        } catch (e: Exception) {
            false
        }
    }
}
