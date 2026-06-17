package ohi.andre.consolelauncher.managers.notifications.reply

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.util.Log
import ohi.andre.consolelauncher.BuildConfig
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import java.io.File
import java.lang.ref.WeakReference
import java.util.Arrays
import java.util.Locale
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.HashSet
import java.util.Set
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsEntry
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.set
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.writeTo

/**
 * Created by francescoandreuzzi on 17/01/2018.
 */
class ReplyManager(context: Context) : XMLPrefsElement {
    private var notificationWears: MutableSet<NotificationWear>? = null
    private var receiver: BroadcastReceiver? = null

    private var values: XMLPrefsList? = null

    private var enabled: Boolean = false

    private var context: Context? = null

    override fun path(): String {
        return PATH
    }

    init {
        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH
        this.context = context.getApplicationContext()

        if (enabled) {
            notificationWears = HashSet<NotificationWear>()
            values = XMLPrefsList()

            instance = WeakReference<ReplyManager?>(this)

            load(true)

            val enabledEntry: XMLPrefsEntry? = values?.get(Reply.reply_enabled)
            enabled = enabledEntry != null && enabledEntry.value.toBoolean()
            if (!enabled) {
                notificationWears = null
                boundApps = null
            } else {
                val filter: IntentFilter = IntentFilter()
                filter.addAction(ACTION)
                filter.addAction(ACTION_UPDATE)
                filter.addAction(ACTION_LS)

                receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.getAction() == ACTION) {
                            val app: String? = intent.getStringExtra(ID)
                            val what: String? = intent.getStringExtra(WHAT)
                            Log.i(
                                "RetuiReplyDebug", ("ReplyManager ACTION received id=" + app
                                        + " hasText=" + (what != null))
                            )

                            var id: Int
                            try {
                                id = app!!.toInt()
                            } catch (e: Exception) {
                                val bapp = findApp(app)
                                if (bapp == null) {
                                    Log.w("RetuiReplyDebug", "ReplyManager app not bound pkg=" + app)
                                    Tuils.sendOutput(
                                        context,
                                        context.getString(R.string.reply_app_not_found) + Tuils.SPACE + app
                                    )
                                    return
                                }

                                id = bapp.applicationId
                            }

                            if (what == null) {
                                check(id)
                            } else {
                                if (id == -1) return
                                Log.i(
                                    "RetuiReplyDebug", ("ReplyManager dispatching reply appId=" + id
                                            + " text=" + what)
                                )
                                replyTo(this@ReplyManager.context!!, id, what)
                            }
                        } else if (intent.getAction() == ACTION_UPDATE) {
                            if (notificationWears != null) {
                                notificationWears!!.clear()
                            }
                            load(false)
                        } else if (intent.getAction() == ACTION_LS) {
                            ls(context)
                        }
                    }
                }

                LocalBroadcastManager.getInstance(this.context!!).registerReceiver(receiver!!, filter)
            }
        }
    }

    private fun load(loadPrefs: Boolean) {
        if (boundApps != null) boundApps!!.clear()
        else boundApps = ArrayList<BoundApp>()

        val enums: MutableList<Reply> = ArrayList<Reply>(Arrays.asList<Reply>(*Reply.values()))

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, NAME)
            if (o == null) {
                context?.let { Tuils.sendXMLParseError(it, PATH) }
                return
            }
        } catch (e: SAXParseException) {
            context?.let { Tuils.sendXMLParseError(it, PATH, e) }
            return
        } catch (e: Exception) {
            Tuils.log(e)
            return
        }

        val d = o[0] as Document
        val root = o[1] as Element

        val nodes = root.getElementsByTagName("*")

        val mgr: PackageManager = context!!.getPackageManager()

        try {
            for (count in 0..<nodes.getLength()) {
                val node = nodes.item(count)
                val nn = node.getNodeName()

                if (Tuils.find(nn, enums) != -1) {
                    if (loadPrefs) {
                            values!!.add(
                            nn,
                            node.getAttributes().getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)
                                .getNodeValue()
                        )

                        for (en in enums.indices) {
                            if (enums.get(en).label() == nn) {
                                enums.removeAt(en)
                                break
                            }
                        }
                    }
                } else {
                    val id: Int = XMLPrefsManager.getIntAttribute(node as Element, ID_ATTRIBUTE)

                    val info: ApplicationInfo?
                    try {
                        info = mgr.getApplicationInfo(nn, 0)
                    } catch (e: Exception) {
                        Tuils.log(e)
                        continue
                    }

                    val label = info.loadLabel(mgr).toString()
                    if (id != -1) boundApps!!.add(BoundApp(id, nn, label))
                }
            }

            if (loadPrefs && enums.size > 0) {
                for (s in enums) {
                    val value: String? = s.defaultValue()

                    val em = d.createElement(s.label())
                    em.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, value)
                    root.appendChild(em)

                    values!!.add(s.label(), value)
                }

                XMLPrefsManager.writeTo(d, file)
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }

        nextUsableId = nextUsableId()
    }

    fun onNotification(notification: StatusBarNotification, text: CharSequence?) {
        if (!enabled) return

        val app: BoundApp? = findApp(notification.getPackageName())
        if (app == null) return

        val w = extractWearNotification(notification)
        if (w == null) return

        val old = findNotificationWear(app)

        if (old != null && (w.pendingIntent == null || w.remoteInputs == null || w.remoteInputs!!.size == 0)) return
        if (old != null) notificationWears!!.remove(old)

        w.text = text
        w.app = app

        notificationWears!!.add(w)
    }

    private fun replyTo(context: Context, applicationId: Int, what: String?) {
        if (!enabled) return

        val app = findApp(applicationId)
        if (app == null) {
            Tuils.sendOutput(
                context,
                context.getString(R.string.reply_id_not_found) + Tuils.SPACE + applicationId
            )
            return
        }

        val wear = findNotificationWear(applicationId)
        if (wear != null) {
            Log.i(
                "RetuiReplyDebug", ("ReplyManager found notification wear appId=" + applicationId
                        + " remoteInputCount=" + (if (wear.remoteInputs == null) 0 else wear.remoteInputs!!.size)
                        + " actionTitle=" + wear.actionTitle)
            )
            replyTo(context, wear, what)
        } else {
            Log.w("RetuiReplyDebug", "ReplyManager no notification wear appId=" + applicationId)
            Tuils.sendOutput(context, R.string.reply_notification_not_found)
        }
    }

    private fun replyTo(context: Context, notificationWear: NotificationWear, what: String?) {
        val remoteInputs: Array<RemoteInput>? = notificationWear.remoteInputs

        val localBundle: Bundle? = notificationWear.bundle
        Log.i(
            "RetuiReplyDebug", ("ReplyManager sending to PrivateIO id=" + notificationWear.id
                    + " app=" + (if (notificationWear.app == null) "null" else notificationWear.app!!.packageName)
                    + " actionTitle=" + notificationWear.actionTitle
                    + " remoteInputCount=" + (if (remoteInputs == null) 0 else remoteInputs.size))
        )

        val i: Intent = Intent(PrivateIOReceiver.ACTION_REPLY)
        i.putExtra(PrivateIOReceiver.BUNDLE, localBundle)
        i.putExtra(PrivateIOReceiver.REMOTE_INPUTS, remoteInputs)
        i.putExtra(PrivateIOReceiver.TEXT, what)
        i.putExtra(PrivateIOReceiver.PENDING_INTENT, notificationWear.pendingIntent)
        i.putExtra(PrivateIOReceiver.ID, notificationWear.id)

        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
    }

    private fun extractWearNotification(statusBarNotification: StatusBarNotification?): NotificationWear? {
        if (statusBarNotification == null || statusBarNotification.getNotification() == null) {
            return null
        }

        var notificationWear: NotificationWear? = null
        val notification: Notification = statusBarNotification.getNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && notification.actions != null) {
            for (i in notification.actions.indices) {
                val action = notification.actions[i]
                if (action == null) continue
                notificationWear = betterReplyAction(notificationWear, action)

                val compatAction: NotificationCompat.Action? =
                    NotificationCompat.getAction(notification, i)
                notificationWear = betterReplyAction(notificationWear, compatAction)
            }
        }

        val wearableExtender = Notification.WearableExtender(notification)
        for (action in wearableExtender.getActions()) {
            if (action == null) continue
            notificationWear = betterReplyAction(notificationWear, action)
        }

        if (notificationWear == null || notificationWear.pendingIntent == null || notificationWear.remoteInputs == null || notificationWear.remoteInputs!!.size == 0) {
            Log.i(
                "RetuiReplyDebug",
                ("no reply action captured pkg=" + statusBarNotification.getPackageName()
                        + " actionCount=" + (if (notification.actions == null) 0 else notification.actions.size)
                        + " wearableActionCount=" + wearableExtender.getActions().size
                        + " hasWearableExtras=" + notification.extras.containsKey("android.wearable.EXTENSIONS"))
            )
            return null
        }

        notificationWear.bundle = notification.extras
        notificationWear.id = statusBarNotification.getId()
        Log.i(
            "RetuiReplyDebug",
            ("captured reply action pkg=" + statusBarNotification.getPackageName()
                    + " id=" + notificationWear.id
                    + " title=" + notificationWear.actionTitle
                    + " semantic=" + notificationWear.semanticAction
                    + " remoteInputCount=" + notificationWear.remoteInputs!!.size)
        )

        return notificationWear
    }

    private fun betterReplyAction(
        current: NotificationWear?,
        action: Notification.Action?
    ): NotificationWear? {
        if (action == null || action.actionIntent == null) return current
        val remoteInputs = action.getRemoteInputs()
        if (remoteInputs == null || remoteInputs.size == 0) return current

        val candidate = NotificationWear()
        candidate.remoteInputs = remoteInputs
        candidate.pendingIntent = action.actionIntent
        candidate.actionTitle = action.title
        candidate.semanticAction =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) action.getSemanticAction() else 0

        if (current == null || replyActionScore(candidate) > replyActionScore(current)) {
            return candidate
        }
        return current
    }

    private fun betterReplyAction(
        current: NotificationWear?,
        action: NotificationCompat.Action?
    ): NotificationWear? {
        if (action == null || action.getActionIntent() == null) return current
        val compatRemoteInputs: Array<androidx.core.app.RemoteInput?>? = action.getRemoteInputs()
        if (compatRemoteInputs == null || compatRemoteInputs.size == 0) return current

        val remoteInputs = toPlatformRemoteInputs(compatRemoteInputs)
        if (remoteInputs == null || remoteInputs.size == 0) return current

        val candidate = NotificationWear()
        candidate.remoteInputs = remoteInputs
        candidate.pendingIntent = action.getActionIntent()
        candidate.actionTitle = action.getTitle()
        candidate.semanticAction = action.getSemanticAction()

        if (current == null || replyActionScore(candidate) > replyActionScore(current)) {
            return candidate
        }
        return current
    }

    private fun toPlatformRemoteInputs(compatRemoteInputs: Array<androidx.core.app.RemoteInput?>?): Array<RemoteInput>? {
        if (compatRemoteInputs == null || compatRemoteInputs.size == 0) return null

        val out = ArrayList<RemoteInput>()
        for (i in compatRemoteInputs.indices) {
            val compat = compatRemoteInputs[i]
            if (compat == null || compat.getResultKey() == null) continue

            val builder = RemoteInput.Builder(compat.getResultKey())
                .setLabel(compat.getLabel())
                .setChoices(compat.getChoices())
                .setAllowFreeFormInput(compat.getAllowFreeFormInput())
                .addExtras(compat.getExtras())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && compat.getAllowedDataTypes() != null) {
                for (dataType in compat.getAllowedDataTypes()!!) {
                    builder.setAllowDataType(dataType, true)
                }
            }

            out.add(builder.build())
        }
        return out.toTypedArray()
    }

    private fun replyActionScore(wear: NotificationWear): Int {
        var score = 0
        val title = if (wear.actionTitle == null) "" else wear.actionTitle.toString().lowercase(
            Locale.getDefault()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            && wear.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY
        ) {
            score += 100
        }
        if (title.contains("reply") || title.contains("respond") || title.contains("message")) {
            score += 50
        }
        if (title.contains("mark") || title.contains("read") || title.contains("archive")
            || title.contains("delete") || title.contains("mute")
        ) {
            score -= 50
        }
        return score
    }

    private fun findApp(applicationId: Int): BoundApp? {
        if (boundApps != null) {
            for (a in boundApps) {
                if (a.applicationId == applicationId) return a
            }
        }

        return null
    }

    private fun findApp(pkg: String?): BoundApp? {
        if (boundApps != null) {
            for (a in boundApps) {
                if (a.packageName == pkg || a.label.equals(pkg, ignoreCase = true)) return a
            }
        }

        return null
    }

    private fun findNotificationWear(bapp: BoundApp): NotificationWear? {
        for (h in notificationWears!!) {
            if (h.app != null && h.app!!.packageName == bapp.packageName) return h
        }
        return null
    }

    private fun findNotificationWear(id: Int): NotificationWear? {
        for (h in notificationWears!!) {
            if (h.app != null && h.app!!.applicationId == id) return h
        }
        return null
    }

    fun dispose(context: Context) {
        try {
            val appContext =
                (if (this.context != null) this.context else context.getApplicationContext())!!
            if (receiver != null) {
                LocalBroadcastManager.getInstance(appContext).unregisterReceiver(receiver!!)
            }
        } catch (e: Exception) {
        }

        if (notificationWears != null) {
            notificationWears!!.clear()
            notificationWears = null
        }
        if (boundApps != null) {
            boundApps!!.clear()
            boundApps = null
        }
        if (values != null) {
            values!!.list.clear()
            values = null
        }

        val current: ReplyManager? = getInstance()
        if (current === this && instance != null) {
            instance!!.clear()
            instance = null
        }
        this.context = null
    }

    override fun getValues(): XMLPrefsList? {
        return values
    }

    override fun write(save: XMLPrefsSave, value: String) {
        XMLPrefsManager.set(
            File(Tuils.getFolder(), PATH),
            save.label(),
            arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(value)
        )
        values!!.add(save.label(), value)
    }

    override fun delete(): Array<String?>? {
        return null
    }

    fun check(id: Int) {
        if (!enabled) return

        val app = findApp(id)
        if (app == null) {
            Tuils.sendOutput(
                context!!,
                context!!.getString(R.string.reply_id_not_found) + Tuils.SPACE + id
            )
            return
        }

        val wear = findNotificationWear(app)
        if (wear == null) {
            Tuils.sendOutput(context!!, R.string.reply_notification_not_found)
            return
        }

        Tuils.sendOutput(context!!, wear.text)
    }

    fun canReplyTo(pkg: String?): Boolean {
        if (!enabled || pkg == null) return false

        val app = findApp(pkg)
        if (app == null) return false

        val wear = findNotificationWear(app)
        return wear != null && wear.pendingIntent != null && wear.remoteInputs != null && wear.remoteInputs!!.size > 0
    }

    private fun nextUsableId(): Int {
        var nextUsableID = 0
        while (true) {
            var shouldRestart = false

            for (b in boundApps!!) {
                if (b.applicationId == nextUsableID) {
                    shouldRestart = true
                    break
                }
            }

            if (!shouldRestart) return nextUsableID

            nextUsableID++
        }
    }

    fun ls(c: Context?) {
        if (!enabled) return

        val builder = StringBuilder()
        if (getInstance() != null) {
            for (a in boundApps!!) builder.append(a.packageName).append(" -> ")
                .append(a.applicationId).append(Tuils.NEWLINE)
        }
        var s = builder.toString()
        if (s.length == 0) s = "[]"

        Tuils.sendOutput(context!!, s)
    } //    private static class NotificationHolder {
    //        BindedApp app;
    //
    //        List<RemoteInput> remoteInputs;
    //        Bundle bundle;
    //        PendingIntent pendingIntent;
    //
    //        public NotificationHolder(BindedApp app, List<RemoteInput> remoteInputs, Bundle bundle, PendingIntent pendingIntent) {
    //            this.app = app;
    //            this.remoteInputs = remoteInputs;
    //            this.bundle = bundle;
    //            this.pendingIntent = pendingIntent;
    //        }
    //
    //        @Override
    //        public boolean equals(Object obj) {
    //            NotificationHolder h = (NotificationHolder) obj;
    //            return h.app.equals(app);
    //        }
    //    }

    companion object {
        var PATH: String = "reply.xml"
        var NAME: String = "REPLY"
        var ACTION: String = BuildConfig.APPLICATION_ID + ".reply"
        var ID: String = "id"
        var WHAT: String = "what"
        var ACTION_UPDATE: String = BuildConfig.APPLICATION_ID + ".update"
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".lsreplies"

        private const val ID_ATTRIBUTE = "id"

        var boundApps: MutableList<BoundApp>? = null

        private var instance: WeakReference<ReplyManager?>? = null
        var nextUsableId: Int = 0

        fun getInstance(): ReplyManager? {
            return if (instance != null) instance!!.get() else null
        }

        fun bind(pkg: String?): String? {
            return XMLPrefsManager.set(
                File(Tuils.getFolder(), PATH), pkg, arrayOf<String>(
                    ID_ATTRIBUTE
                ), arrayOf<String>(nextUsableId.toString())
            )
        }

        fun unbind(pkg: String?): String? {
            return XMLPrefsManager.removeNode(File(Tuils.getFolder(), PATH), pkg)
        }
    }
}
