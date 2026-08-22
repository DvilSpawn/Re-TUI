package ohi.andre.consolelauncher.tuils

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.app.RemoteInput
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.tuils.interfaces.Inputable
import ohi.andre.consolelauncher.tuils.interfaces.Outputable

/**
 * Created by francescoandreuzzi on 18/08/2017.
 */
class PrivateIOReceiver(activity: Activity?, outputable: Outputable, inputable: Inputable) :
    BroadcastReceiver() {
    var outputable: Outputable
    var inputable: Inputable

    var activity: Activity?

    init {
        this.outputable = outputable
        this.inputable = inputable
        this.activity = activity
    }

    override fun onReceive(context: Context, intent: Intent) {
//        to avoid double onReceive calls
        val cId: Int = intent.getIntExtra(CURRENT_ID, -1)
        val isReplyAction = intent.getAction() != null && intent.getAction() == ACTION_REPLY
        if (!isReplyAction) {
            if (cId != -1 && cId != currentId) return
            currentId++
        }

        val remoteInput: Bundle? = RemoteInput.getResultsFromIntent(intent)
        if (remoteInput == null || remoteInput.size() == 0) {
            var text: CharSequence? = intent.getCharSequenceExtra(TEXT)
            if (text == null) text = intent.getStringExtra(TEXT)
            if (text == null) return

            if (intent.getAction() == ACTION_OUTPUT) {
                val infoArea: Boolean = intent.getBooleanExtra(INFO_AREA, false)
                val color: Int = intent.getIntExtra(COLOR, Int.Companion.MAX_VALUE)

                var singleClickExtraObject: Any? = null
                var longClickExtraObject: Any? = null
                val extras = intent.getExtras()
                if (extras != null) {
                    singleClickExtraObject = extras.get(ACTION)
                    longClickExtraObject = extras.get(LONG_ACTION)
                }

                if (singleClickExtraObject != null || longClickExtraObject != null) {
                    text = SpannableStringBuilder(text)
                    (text as SpannableStringBuilder).setSpan(
                        LongClickableSpan(
                            singleClickExtraObject,
                            longClickExtraObject
                        ), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }

                if (color != Int.Companion.MAX_VALUE) outputable.onOutput(color, text)
                else {
                    val type: Int = intent.getIntExtra(TYPE, -1)
                    if (type != -1) outputable.onOutput(text, type)
                    else outputable.onOutput(text, TerminalManager.CATEGORY_OUTPUT)
                }
            } else if (intent.getAction() == ACTION_INPUT) {
                inputable.`in`(text.toString())
            } else if (intent.getAction() == ACTION_REPLY) {
                val b: Bundle? = intent.getBundleExtra(BUNDLE)
                val ps: Array<Parcelable?>? = intent.getParcelableArrayExtra(REMOTE_INPUTS)
                val pi: PendingIntent? = intent.getParcelableExtra<PendingIntent?>(PENDING_INTENT)
                val id: Int = intent.getIntExtra(ID, 0)

                if (b == null) {
                    Tuils.sendOutput(Color.RED, context, "The bundle is null")
                    return
                }

                if (ps == null || ps.size == 0) {
                    Tuils.sendOutput(Color.RED, context, "No remote inputs")
                    return
                }

                if (pi == null) {
                    Tuils.sendOutput(Color.RED, context, "The pending intent couldn\'t be found")
                    return
                }

                val rms = Array(ps.size) { j -> ps[j] as android.app.RemoteInput }
                for (j in rms.indices) {
                    rms[j] = (ps[j] as android.app.RemoteInput?)!!
                }

                val localIntent: Intent = Intent()
                localIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)

                val results: Bundle = Bundle()
                for (remoteIn in rms) {
                    results.putCharSequence(remoteIn.getResultKey(), text)
                }

                android.app.RemoteInput.addResultsToIntent(rms, localIntent, results)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.app.RemoteInput.setResultsSource(
                        localIntent,
                        android.app.RemoteInput.SOURCE_FREE_FORM_INPUT
                    )
                }
                try {
                    Log.i(
                        "RetuiReplyDebug", ("sending reply pendingIntent=" + pi
                                + " text=" + text
                                + " remoteInputCount=" + rms.size
                                + " resultKeys=" + results.keySet())
                    )
                    pi.send(context.getApplicationContext(), 0, localIntent)
                    Log.i("RetuiReplyDebug", "pendingIntent send completed")
                } catch (e: PendingIntent.CanceledException) {
                    Log.e("RetuiReplyDebug", "pendingIntent send failed", e)
                    Tuils.sendOutput(Color.RED, context, e.toString())
                    Tuils.log(e)
                }
            }
        } else {
            val cmd: String? = remoteInput.getString(TEXT)
            val i: Intent = Intent(MainManager.ACTION_EXEC)
            i.putExtra(MainManager.CMD_COUNT, MainManager.commandCount)
            i.putExtra(MainManager.CMD, cmd)
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
        }
    }

    companion object {
        //    this class handles incoming intent to
        //    process input
        //    show custom output
        @JvmField
        val ACTION_OUTPUT: String = BuildConfig.APPLICATION_ID + ".action_output"
        @JvmField
        val ACTION_INPUT: String = BuildConfig.APPLICATION_ID + ".action_input"
        @JvmField
        val ACTION_REPLY: String = BuildConfig.APPLICATION_ID + ".action_reply"

        @JvmField
        val TEXT: String = BuildConfig.APPLICATION_ID + ".text"
        @JvmField
        val TYPE: String = BuildConfig.APPLICATION_ID + ".type"
        @JvmField
        val COLOR: String = BuildConfig.APPLICATION_ID + ".color"
        @JvmField
        val ACTION: String = BuildConfig.APPLICATION_ID + ".action"
        @JvmField
        val LONG_ACTION: String = BuildConfig.APPLICATION_ID + ".longaction"
        @JvmField
        val REMOTE_INPUTS: String = BuildConfig.APPLICATION_ID + ".remote_inputs"
        @JvmField
        val BUNDLE: String = BuildConfig.APPLICATION_ID + ".bundle"
        @JvmField
        val PENDING_INTENT: String = BuildConfig.APPLICATION_ID + ".pending_intent"
        @JvmField
        val ID: String = BuildConfig.APPLICATION_ID + ".id"
        @JvmField
        val CURRENT_ID: String = BuildConfig.APPLICATION_ID + ".current_id"
        @JvmField
        val INFO_AREA: String = BuildConfig.APPLICATION_ID + ".info_area"

        @JvmField
        var currentId: Int = 0
    }
}
