package ohi.andre.consolelauncher.tuils

import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Vibrator
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showExcludeAppPopupAction
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showExcludeNotificationPopupAction
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showReplyPopupAction
import ohi.andre.consolelauncher.managers.settings.NotificationSettings

/**
 * Created by francescoandreuzzi on 22/10/2017.
 */
class LongClickableSpan(
    private val clickO: Any?,
    private val longClickO: Any?,
    private val longIntentKey: String?
) : ClickableSpan() {

    constructor(clickAction: Any?, longClickAction: Any?) : this(clickAction, longClickAction, null)

    constructor(clickAction: Any?) : this(clickAction, null, null)

    constructor(clickAction: Any?, longIntentKey: String?) : this(clickAction, null, longIntentKey)

    constructor(longIntentKey: String?) : this(null, null, longIntentKey)

    override fun updateDrawState(ds: TextPaint) {}

    override fun onClick(widget: View) {
        execute(widget, clickO)
    }

    fun onLongClick(widget: View) {
        if (execute(
                widget,
                longClickO,
                longIntentKey
            ) && longPressVibrateDuration > 0
        ) (widget.getContext().getApplicationContext().getSystemService(
            Context.VIBRATOR_SERVICE
        ) as Vibrator).vibrate(longPressVibrateDuration.toLong())
    }

    companion object {
        var longPressVibrateDuration: Int = -1

        private var set = false
        private var showMenu = false
        private var showExcludeApp = false
        private var showExcludeNotification = false
        private var showReply = false

        private fun execute(v: View, o: Any?, intentKey: String? = null): Boolean {
            if (o == null) return false

            if (!set) {
                set = true

                showExcludeApp = showExcludeAppPopupAction()
                showExcludeNotification = showExcludeNotificationPopupAction()
                showReply = showReplyPopupAction()

                showMenu =
                    (showExcludeApp && showExcludeNotification) || (showExcludeApp && showReply) || (showExcludeNotification && showReply)
            }

            if (o is String) {
                val intent = Intent(if (intentKey != null) intentKey else MainManager.ACTION_EXEC)
                intent.putExtra(PrivateIOReceiver.TEXT, o)

                if (intentKey == null || intentKey == MainManager.ACTION_EXEC) {
                    intent.putExtra(MainManager.NEED_WRITE_INPUT, false)
                    intent.putExtra(MainManager.CMD_COUNT, MainManager.commandCount)
                }

                LocalBroadcastManager.getInstance(v.getContext().getApplicationContext())
                    .sendBroadcast(intent)
            } else if (o is PendingIntent) {
                val pi = o

                try {
                    Tuils.sendPendingIntent(v.getContext(), pi)
                } catch (e: CanceledException) {
                    Tuils.log(e)
                }
            } else if (o is Uri) {
                val i = Intent(Intent.ACTION_VIEW, o)
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                try {
                    v.getContext().startActivity(i)
                } catch (e: Exception) {
                    Tuils.sendOutput(Color.RED, v.getContext(), e.toString())
                }
            } else if (o is NotificationService.Notification) {
                val n = o

                if (showMenu) {
                        val menu = PopupMenu(v.getContext().getApplicationContext(), v)
                        menu.getMenuInflater().inflate(R.menu.notification_menu, menu.getMenu())

                        menu.getMenu().findItem(R.id.exclude_app).setVisible(showExcludeApp)
                        menu.getMenu().findItem(R.id.exclude_notification).setVisible(
                            showExcludeNotification
                        )
                        menu.getMenu().findItem(R.id.reply_notification).setVisible(showReply)

                        menu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item: MenuItem? ->
                            val id = item!!.getItemId()
                            if (id == R.id.exclude_app) {
                                excludeApp(v.getContext(), n)
                            } else if (id == R.id.exclude_notification) {
                                excludeNotification(v.getContext(), n)
                            } else if (id == R.id.reply_notification) {
                                val intent = Intent(PrivateIOReceiver.ACTION_INPUT)
                                intent.putExtra(
                                    PrivateIOReceiver.TEXT,
                                    "reply -to " + n.pkg + Tuils.SPACE
                                )

                                LocalBroadcastManager.getInstance(
                                    v.getContext().getApplicationContext()
                                ).sendBroadcast(intent)
                            } else {
                                return@OnMenuItemClickListener false
                            }
                            true
                        })

                        menu.show()
                    } else {
                        if (showReply) {
                            val intent = Intent(PrivateIOReceiver.ACTION_INPUT)
                            intent.putExtra(
                                PrivateIOReceiver.TEXT,
                                "reply -to " + n.pkg + Tuils.SPACE
                            )

                            LocalBroadcastManager.getInstance(
                                v.getContext().getApplicationContext()
                            ).sendBroadcast(intent)
                        } else if (showExcludeNotification) excludeNotification(v.getContext(), n)
                        else if (showExcludeApp) excludeApp(v.getContext(), n)
                }
            }

            return true
        }

        private fun excludeApp(context: Context, notification: NotificationService.Notification) {
            NotificationManager.setState(notification.pkg, false)
            NotificationService.requestReload(context.getApplicationContext())
        }

        private fun excludeNotification(
            context: Context,
            notification: NotificationService.Notification
        ) {
            Tuils.log(notification.text)
            NotificationManager.addLiteralFilter(notification.text)
            NotificationService.requestReload(context.getApplicationContext())
        }
    }
}
