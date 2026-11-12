package ohi.andre.consolelauncher.managers.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import java.util.ArrayList
import java.util.Collections
import ohi.andre.consolelauncher.managers.terminal.TerminalManager
import ohi.andre.consolelauncher.tuils.Tuils

class DevReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val results = if (intent == null) null else RemoteInput.getResultsFromIntent(intent)
        val reply = results?.getCharSequence(RESULT_KEY)
        Log.i(
            "RetuiReplyDebug",
            "dev reply received action=" +
                (intent?.action ?: "null") +
                " reply=" + (reply ?: "<null>") +
                " remoteInputKeys=" + bundleKeys(results) +
                " intentExtraKeys=" + bundleKeys(intent?.extras)
        )

        val out = StringBuilder("DEVUTILS NOTIFY REPLY")
        out.append(Tuils.NEWLINE)
            .append("action: ")
            .append(intent?.action ?: "null")
            .append(Tuils.NEWLINE)
            .append("reply: ")
            .append(reply ?: "<null>")

        out.append(Tuils.NEWLINE)
            .append("remote_input_keys: ")
            .append(bundleKeys(results))

        val extras = intent?.extras
        out.append(Tuils.NEWLINE)
            .append("intent_extra_keys: ")
            .append(bundleKeys(extras))

        Tuils.sendOutput(context.applicationContext, out.toString(), TerminalManager.CATEGORY_OUTPUT)
        NotificationManagerCompat.from(context.applicationContext).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_DEV_REPLY: String = "ohi.andre.consolelauncher.DEV_REPLY"
        const val RESULT_KEY: String = "retui_dev_reply_text"
        const val NOTIFICATION_ID: Int = 201

        private fun bundleKeys(bundle: Bundle?): String {
            if (bundle == null || bundle.isEmpty) {
                return "[]"
            }
            val keys = ArrayList(bundle.keySet())
            Collections.sort(keys)
            return keys.toString()
        }
    }
}
