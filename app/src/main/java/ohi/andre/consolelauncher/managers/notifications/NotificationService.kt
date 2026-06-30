package ohi.andre.consolelauncher.managers.notifications

/**
 * Created by francescoandreuzzi on 27/04/2017.
 */

import android.app.Service
import android.content.Context
import android.graphics.Color
import android.media.session.MediaController
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import java.util.Collections
import java.util.Queue
import java.util.concurrent.ArrayBlockingQueue
import java.util.regex.Pattern
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import android.content.ComponentName
import java.util.ArrayList
import java.util.ConcurrentModificationException
import java.util.HashMap
import java.util.Iterator
import java.util.Map
import java.util.Comparator
import java.util.regex.Matcher
import ohi.andre.consolelauncher.managers.TerminalManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.MusicSettings
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils

class NotificationService : NotificationListenerService() {
    private val UPDATE_TIME = 2000
    private val LINES_LABEL = "Lines"
    private val ANDROID_LABEL_PREFIX = "android."
    private val NULL_LABEL = "null"

    var pastNotifications: HashMap<String?, MutableList<Notification>?>? = null
    private val serviceHandler: Handler = Handler()

    var format: String? = null
    var color: Int = 0
    var maxOptionalDepth: Int = 0
    var enabled: Boolean = false
    var click: Boolean = false
    var longClick: Boolean = false
    var active: Boolean = false
    var terminalNotifications: Boolean = false

    var queue: Queue<StatusBarNotification?>? = null
    private val queueLock = Any()

    val PKG: String = "%pkg"
    val APP: String = "%app"
    val NEWLINE: String = "%n"
    val timePattern: Pattern = Pattern.compile("^%t[0-9]*$")

    var manager: PackageManager? = null
    var replyManager: ReplyManager? = null
    var notificationManager: NotificationManager? = null

    private val formatPattern: Pattern =
        Pattern.compile("%(?:\\[(\\d+)\\])?(?:\\[([^]]+)\\])?(?:(?:\\{)([a-zA-Z\\.\\:\\s]+)(?:\\})|([a-zA-Z\\.\\:]+))")

    var bgThread: StoppableThread? = null

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers: MutableList<MediaController> = ArrayList<MediaController>()
    private val overlayNotifications = ArrayList<Notification>()
    private val feedRequestReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && ACTION_REQUEST_NOTIFICATION_FEED == intent.getAction()) {
                broadcastOverlayNotifications()
            }
        }
    }
    private val reloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && ACTION_RELOAD_NOTIFICATION_CONFIG == intent.getAction()) {
                reloadNotificationConfig()
            }
        }
    }
    private val dismissReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && ACTION_DISMISS_NOTIFICATION == intent.getAction()) {
                dismissNotification(intent.getStringExtra(EXTRA_NOTIFICATION_KEY))
            }
        }
    }
    private var lastMediaTitle: String? = null
    private var lastMediaArtist: String? = null
    private var lastMediaDuration = 0
    private var lastMediaPosition = 0
    private var hasLastMediaState = false
    private var controlReceiverRegistered = false
    private var feedRequestReceiverRegistered = false
    private var reloadReceiverRegistered = false
    private var dismissReceiverRegistered = false
    private val clearExternalMusicRunnable: Runnable = object : Runnable {
        override fun run() {
            clearRememberedMediaState()
            sendMusicBroadcast(null, null, 0, 0, false)
        }
    }

    private val sessionsChangedListener: OnActiveSessionsChangedListener =
        object : OnActiveSessionsChangedListener {
            override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
                updateActiveSessions(controllers)
            }
        }

    private val controlReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (MusicService.ACTION_MUSIC_CONTROL == intent.getAction()) {
                val cmd: Int = intent.getIntExtra(MusicService.EXTRA_CONTROL_CMD, -1)
                handleControlCommand(cmd)
            }
        }
    }

    private fun handleControlCommand(cmd: Int) {
        if (activeControllers.isEmpty()) return

        var activeController: MediaController? = null
        for (controller in activeControllers) {
            val state: PlaybackState? = controller.getPlaybackState()
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                activeController = controller
                break
            }
        }
        if (activeController == null) activeController = activeControllers.get(0)

        Log.d(
            "TUI-Music",
            "Handling control command: " + cmd + " for " + activeController.getPackageName()
        )

        when (cmd) {
            MusicService.CONTROL_NEXT_INT -> activeController.getTransportControls().skipToNext()
            MusicService.CONTROL_PREV_INT -> activeController.getTransportControls()
                .skipToPrevious()

            MusicService.CONTROL_PLAY_PAUSE_INT -> {
                val state: PlaybackState? = activeController.getPlaybackState()
                if (state != null) {
                    if (state.getState() == PlaybackState.STATE_PLAYING) {
                        activeController.getTransportControls().pause()
                    } else {
                        activeController.getTransportControls().play()
                    }
                }
            }
        }
    }

    private val mediaCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            broadcastMediaMetadata()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            broadcastMediaMetadata()
            scheduleMediaProgressUpdates()
        }
    }

    private fun updateActiveSessions(controllers: MutableList<MediaController>?) {
        Log.d(
            "TUI-Music",
            "updateActiveSessions: " + (if (controllers != null) controllers.size else 0) + " sessions"
        )
        if (controllers != null) {
            for (mc in controllers) {
                Log.d(
                    "TUI-Music",
                    "Session: " + mc.getPackageName() + " State: " + (if (mc.getPlaybackState() != null) mc.getPlaybackState()!!
                        .getState() else "null")
                )
            }
        }
        synchronized(activeControllers) {
            for (controller in activeControllers) {
                controller.unregisterCallback(mediaCallback)
            }
            activeControllers.clear()
            if (controllers != null) {
                activeControllers.addAll(controllers)
                for (controller in activeControllers) {
                    controller.registerCallback(mediaCallback)
                    Log.d("TUI-Music", "Registered callback for: " + controller.getPackageName())
                }
            }
        }
        broadcastMediaMetadata()
        scheduleMediaProgressUpdates()
    }

    private fun broadcastMediaMetadata() {
        if (activeControllers.isEmpty()) {
            Log.d("TUI-Music", "No active controllers to broadcast")
            if (hasLastMediaState) {
                scheduleExternalMusicClear()
            } else {
                sendMusicBroadcast(null, null, 0, 0, false, MusicService.SOURCE_EXTERNAL, null)
            }
            return
        }

        cancelExternalMusicClear()

        val activeController = resolveActiveController()
        if (activeController == null) {
            return
        }

        if (!MusicSettings.acceptsPackage(activeController.getPackageName())) {
            Log.d(
                "TUI-Music",
                "Active controller " + activeController.getPackageName() + " is not preferred. Hiding widget."
            )
            sendMusicBroadcast(
                null,
                null,
                0,
                0,
                false,
                MusicService.SOURCE_EXTERNAL,
                activeController.getPackageName()
            )
            return
        }

        val metadata: MediaMetadata? = activeController.getMetadata()
        val state: PlaybackState? = activeController.getPlaybackState()
        val isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING
        val position = if (state != null) state.getPosition().toInt() else lastMediaPosition

        if (metadata != null) {
            val title: String? = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            var artist: String? = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (artist == null) artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)

            val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).toInt()
            Log.d(
                "TUI-Music",
                "Broadcasting: " + title + " by " + artist + " (playing=" + isPlaying + ") pkg=" + activeController.getPackageName()
            )
            rememberMediaState(title, artist, duration, position)
            sendMusicBroadcast(
                title,
                artist,
                duration,
                position,
                isPlaying,
                MusicService.SOURCE_EXTERNAL,
                activeController.getPackageName()
            )
            return
        }

        Log.d(
            "TUI-Music",
            "No metadata for active controller: " + activeController.getPackageName()
        )
        if (hasLastMediaState) {
            sendMusicBroadcast(
                lastMediaTitle,
                lastMediaArtist,
                lastMediaDuration,
                position,
                isPlaying,
                MusicService.SOURCE_EXTERNAL,
                activeController.getPackageName()
            )
            lastMediaPosition = position
        } else {
            sendMusicBroadcast(
                null,
                null,
                0,
                0,
                isPlaying,
                MusicService.SOURCE_EXTERNAL,
                activeController.getPackageName()
            )
        }
    }

    private fun resolveActiveController(): MediaController? {
        if (activeControllers.isEmpty()) {
            return null
        }

        val rankedControllers: MutableList<MediaController?> =
            ArrayList<MediaController?>(activeControllers)
        val preferredPackage: String? = MusicSettings.preferredPackage()

        Collections.sort<MediaController?>(
            rankedControllers,
            object : Comparator<MediaController?> {
                override fun compare(left: MediaController?, right: MediaController?): Int {
                    return controllerRank(right, preferredPackage) - controllerRank(
                        left,
                        preferredPackage
                    )
                }
            })

        return rankedControllers.get(0)
    }

    private fun controllerRank(controller: MediaController?, preferredPackage: String?): Int {
        var rank = 0

        val packageName = if (controller != null && controller.getPackageName() != null)
            controller.getPackageName()
        else
            Tuils.EMPTYSTRING

        val state: PlaybackState? = if (controller != null) controller.getPlaybackState() else null
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            rank += 1000
        }

        if (!TextUtils.isEmpty(preferredPackage) && preferredPackage == packageName) {
            rank += 100
        }

        if (controller != null && controller.getMetadata() != null) {
            val title = controller.getMetadata()!!.getString(MediaMetadata.METADATA_KEY_TITLE)
            val artist = controller.getMetadata()!!.getString(MediaMetadata.METADATA_KEY_ARTIST)
            if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist)) {
                rank += 1
            }
        }

        return rank
    }

    private fun rememberMediaState(title: String?, artist: String?, duration: Int, position: Int) {
        lastMediaTitle = title
        lastMediaArtist = artist
        lastMediaDuration = duration
        lastMediaPosition = position
        hasLastMediaState = !TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist) || duration > 0
    }

    private fun clearRememberedMediaState() {
        lastMediaTitle = null
        lastMediaArtist = null
        lastMediaDuration = 0
        lastMediaPosition = 0
        hasLastMediaState = false
    }

    private fun scheduleExternalMusicClear() {
        serviceHandler.removeCallbacks(clearExternalMusicRunnable)
        serviceHandler.postDelayed(clearExternalMusicRunnable, MEDIA_SESSION_EMPTY_GRACE_MS)
    }

    private fun cancelExternalMusicClear() {
        serviceHandler.removeCallbacks(clearExternalMusicRunnable)
    }

    private fun sendMusicBroadcast(
        title: String?,
        artist: String?,
        duration: Int,
        position: Int,
        isPlaying: Boolean,
        source: String? = MusicService.SOURCE_EXTERNAL,
        packageName: String? = null
    ) {
        val intent: Intent = Intent(MusicService.ACTION_MUSIC_CHANGED)
        if (title != null) {
            intent.putExtra(MusicService.SONG_TITLE, title)
        }
        if (artist != null) {
            intent.putExtra(MusicService.SONG_SINGER, artist)
        }
        intent.putExtra(MusicService.SONG_DURATION, duration)
        intent.putExtra(MusicService.SONG_POSITION, position)
        intent.putExtra(MusicService.MUSIC_PLAYING, isPlaying)
        intent.putExtra(MusicService.MUSIC_SOURCE, source)
        if (packageName != null) {
            intent.putExtra("package", packageName)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private val progressUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            var anyPlaying = false
            synchronized(activeControllers) {
                for (controller in activeControllers) {
                    val state: PlaybackState? = controller.getPlaybackState()
                    if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                        anyPlaying = true
                        break
                    }
                }
            }
            if (anyPlaying) {
                broadcastMediaMetadata()
                serviceHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pastNotificationCleanupRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!active || pastNotifications == null || pastNotifications!!.isEmpty()) {
                return
            }

            val now = System.currentTimeMillis()
            val entries = pastNotifications!!.entries.iterator()
            while (entries.hasNext()) {
                val entry = entries.next()
                val notifications = entry.value ?: continue
                val it = notifications.iterator()
                while (it.hasNext()) {
                    if (now - it.next().time >= UPDATE_TIME) it.remove()
                }
                if (notifications.isEmpty()) {
                    entries.remove()
                }
            }

            if (!pastNotifications!!.isEmpty()) {
                serviceHandler.postDelayed(this, UPDATE_TIME.toLong())
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerControlReceiverIfNeeded()
        init()
    }

    private fun registerControlReceiverIfNeeded() {
        if (controlReceiverRegistered) {
            return
        }
        val filter: IntentFilter = IntentFilter(MusicService.ACTION_MUSIC_CONTROL)
        LocalBroadcastManager.getInstance(this).registerReceiver(controlReceiver, filter)
        controlReceiverRegistered = true
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("TUI-Music", "NotificationListener connected")
        if (!active) {
            init()
        } else {
            reloadNotificationConfig()
        }
        setupMediaSession()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d("TUI-Music", "NotificationListener disconnected")
    }

    private fun setupMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mediaSessionManager == null) {
                mediaSessionManager =
                    getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager?
            }
            val componentName: ComponentName = ComponentName(this, NotificationService::class.java)
            try {
                val sessionManager = mediaSessionManager ?: return
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener)
                sessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    componentName
                )
                val sessions = sessionManager.getActiveSessions(componentName)
                Log.d(
                    "TUI-Music",
                    "Initial active sessions count: " + (if (sessions != null) sessions.size else 0)
                )
                updateActiveSessions(sessions)
            } catch (e: SecurityException) {
                Log.e("TUI-Music", "MediaSession access denied: " + e.message)
            } catch (e: Exception) {
                Log.e("TUI-Music", "Error setting up MediaSession: " + e.message)
            }
        }
    }

    private fun init() {
        registerControlReceiverIfNeeded()
        try {
            Tuils.init(this)
            notificationManager = NotificationManager.create(this)
            XMLPrefsManager.loadCommons(this)
            LauncherSettings.refreshFromLoadedPrefs()
        } catch (e: Exception) {
            Tuils.log(e)
            return
        }

        try {
            replyManager = ReplyManager(this)
        } catch (error: VerifyError) {
            replyManager = null
        }

        bgThread = object : StoppableThread() {
            override fun run() {
                super.run()

                if (!enabled) return

                while (true) {
                    if (isInterrupted()) return

                    if (queue != null) {
                        var sbn: StatusBarNotification?
                        while ((queue!!.poll().also { sbn = it }) != null) {
                            val currentSbn = sbn ?: continue
                            Log.d(
                                "TUI-Notif",
                                "Processing notification from: " + currentSbn.getPackageName()
                            )
                            val notification: android.app.Notification? = currentSbn.getNotification()
                            if (notification == null) {
                                continue
                            }

                            val pack: String = currentSbn.getPackageName()

                            var appName: String?
                            try {
                                val packageManager = manager ?: this@NotificationService.packageManager
                                appName = packageManager.getApplicationInfo(pack, 0).loadLabel(packageManager)
                                    .toString()
                            } catch (e: PackageManager.NameNotFoundException) {
                                appName = "null"
                            }

                            val currentNotificationManager = notificationManager ?: continue
                            val nApp = currentNotificationManager.getAppState(pack)
                            if ((nApp != null && !nApp.enabled)) {
                                continue
                            }

                            if (nApp == null && !currentNotificationManager.default_app_state) {
                                continue
                            }

                            val f: String?
                            if (nApp != null) f = nApp.format
                            else f = format

                            val textColor: Int
                            if (nApp != null) textColor =
                                Color.parseColor(nApp.color)
                            else textColor = color

                            var s: CharSequence = Tuils.span(f, textColor) ?: Tuils.EMPTYSTRING

                            val bundle: Bundle? = NotificationCompat.getExtras(notification)

                            if (bundle != null) {
                                val m = formatPattern.matcher(s)
                                var match: String?
                                while (m.find()) {
                                    match = m.group(0)
                                    if (!match.startsWith(PKG) && !match.startsWith(APP) && !match.startsWith(
                                            NEWLINE
                                        ) && !timePattern.matcher(match).matches()
                                    ) {
                                        val length = m.group(1)
                                        val color = m.group(2)
                                        var value = m.group(3)

                                        if (value == null || value.length == 0) value = m.group(4)

                                        if (value != null) value = value.trim { it <= ' ' }
                                        else continue

                                        if (value.length == 0) continue

                                        if (value == "ttl") value = "title"
                                        else if (value == "txt") value = "text"

                                        val temp: Array<String?> = value.split(":".toRegex())
                                            .dropLastWhile { it.isEmpty() }.toTypedArray()
                                        val split: Array<String?>
                                        //                                    this is an other way to do what I did in NotesManager for footer/header
                                        if (value.endsWith(":")) {
                                            split = arrayOfNulls<String>(temp.size + 1)
                                            System.arraycopy(temp, 0, split, 0, temp.size)
                                            split[split.size - 1] = Tuils.EMPTYSTRING
                                        } else split = temp

                                        //                                    because the last one is the default text, but only if there is more than one label
                                        var stopAt = split.size
                                        if (stopAt > 1) stopAt--

                                        var text: CharSequence? = null
                                        for (j in 0..<stopAt) {
                                            if (split[j]!!.contains(LINES_LABEL)) {
                                                val array: Array<CharSequence?>? =
                                                    bundle.getCharSequenceArray(ANDROID_LABEL_PREFIX + split[j])
                                                if (array != null) {
                                                    for (c in array) {
                                                        if (text == null) text = c
                                                        else text =
                                                            TextUtils.concat(text, Tuils.NEWLINE, c)
                                                    }
                                                }
                                            } else {
                                                text =
                                                    bundle.getCharSequence(ANDROID_LABEL_PREFIX + split[j])
                                            }

                                            if (text != null && text.length > 0) break
                                        }

                                        if (text == null || text.length == 0) {
                                            text =
                                                if (split.size == 1) NULL_LABEL else split[split.size - 1]
                                        }

                                        var stringed = text.toString().trim { it <= ' ' }

                                        try {
                                            val l = length!!.toInt()
                                            stringed = stringed.substring(0, l)
                                        } catch (e: Exception) {
                                        }

                                        try {
                                            text = Tuils.span(stringed, Color.parseColor(color))
                                        } catch (e: Exception) {
                                            text = stringed
                                        }

                                        s = TextUtils.replace(
                                            s,
                                            arrayOf<String?>(m.group(0)),
                                            arrayOf<CharSequence?>(text)
                                        )
                                    }
                                }
                            }

                            val text = s.toString()

                            if (notificationManager!!.match(text)) continue

                            val found = isInPastNotifications(pack, text)

                            //                        if(found == 0) {
//                            Tuils.log("app " + pack, pastNotifications.get(pack).toString());
//                        }
                            if (found == 2) continue

                            //                        else
                            val n = Notification(
                                System.currentTimeMillis(),
                                text,
                                pack,
                                notification.contentIntent,
                                currentSbn.getKey()
                            )

                            if (found == 1) {
                                val ns: MutableList<Notification> = ArrayList<Notification>()
                                ns.add(n)
                                pastNotifications!!.put(pack, ns)
                                schedulePastNotificationCleanup()
                            } else if (found == 0) {
                                pastNotifications!!.get(pack)!!.add(n)
                                schedulePastNotificationCleanup()
                            }

                            n.appName = appName
                            n.preview = buildNotificationPreview(bundle, text)
                            n.title = extractNotificationTitle(bundle)
                            n.body = extractNotificationBody(bundle)
                            pushOverlayNotification(n)

                            s = TextUtils.replace(
                                s,
                                arrayOf<String>(PKG, APP, NEWLINE),
                                arrayOf<CharSequence>(pack, appName, Tuils.NEWLINE)
                            )
                            var st = s.toString()
                            while (st.contains(NEWLINE)) {
                                s = TextUtils.replace(
                                    s,
                                    arrayOf<String>(NEWLINE),
                                    arrayOf<CharSequence>(Tuils.NEWLINE)
                                )
                                st = s.toString()
                            }

                            val timeManager = TimeManager.instance
                            if (timeManager != null) {
                                try {
                                    s = timeManager.replace(s)
                                } catch (e: Exception) {
                                    Tuils.log(e)
                                }
                            }

                            //                        Tuils.log("text", text);
//                        Tuils.log("--------");
                            if (terminalNotifications) {
                                Tuils.sendOutput(
                                    this@NotificationService.getApplicationContext(),
                                    s,
                                    TerminalManager.CATEGORY_NO_COLOR,
                                    if (click) notification.contentIntent else null,
                                    if (longClick) n else null
                                )
                            }

                            val notifyIntent: Intent =
                                Intent(UIManager.ACTION_NOTIFICATION_RECEIVED)
                            notifyIntent.putExtra(UIManager.NOTIFICATION_TEXT, s)
                            LocalBroadcastManager.getInstance(this@NotificationService)
                                .sendBroadcast(notifyIntent)

                            replyManager?.onNotification(currentSbn, s)
                        }
                    }

                    synchronized(queueLock) {
                        while (!isInterrupted() && (queue == null || queue!!.isEmpty())) {
                            try {
                                (queueLock as Object).wait()
                            } catch (e: InterruptedException) {
                                return
                            }
                        }
                    }
                }
            }
        }

        manager = getPackageManager()
        pastNotifications = HashMap<String?, MutableList<Notification>?>()

        queue = ArrayBlockingQueue<StatusBarNotification?>(5)
        if (!feedRequestReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                feedRequestReceiver, IntentFilter(
                    ACTION_REQUEST_NOTIFICATION_FEED
                )
            )
            feedRequestReceiverRegistered = true
        }
        if (!reloadReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                reloadReceiver, IntentFilter(
                    ACTION_RELOAD_NOTIFICATION_CONFIG
                )
            )
            reloadReceiverRegistered = true
        }
        if (!dismissReceiverRegistered) {
            LocalBroadcastManager.getInstance(this).registerReceiver(
                dismissReceiver, IntentFilter(
                    ACTION_DISMISS_NOTIFICATION
                )
            )
            dismissReceiverRegistered = true
        }
        loadConfig()
        seedActiveNotifications()
        bgThread?.start()

        setupMediaSession()
        scheduleMediaProgressUpdates()

        active = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (ACTION_RELOAD_NOTIFICATION_CONFIG == intent.getAction()) {
                if (!active) init()
                reloadNotificationConfig()
                return Service.START_STICKY
            }

            val destroy: Boolean = intent.getBooleanExtra(DESTROY, false)
            if (destroy) {
                dispose()
                stopSelf(startId)
                return Service.START_NOT_STICKY
            }
        }

        if (!active) init()

        return Service.START_STICKY
    }

    private fun dispose() {
        cancelExternalMusicClear()
        serviceHandler.removeCallbacks(progressUpdateRunnable)
        serviceHandler.removeCallbacks(pastNotificationCleanupRunnable)
        if (controlReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(controlReceiver)
            } catch (ignored: Exception) {
            }
            controlReceiverRegistered = false
        }
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
        synchronized(activeControllers) {
            for (controller in activeControllers) {
                controller.unregisterCallback(mediaCallback)
            }
            activeControllers.clear()
        }
        clearRememberedMediaState()

        if (replyManager != null) {
            replyManager?.dispose(this)
            replyManager = null
        }

        if (notificationManager != null) {
            notificationManager!!.dispose()
            notificationManager = null
        }

        if (bgThread != null) {
            bgThread?.interrupt()
            synchronized(queueLock) {
                (queueLock as Object).notifyAll()
            }
            bgThread = null
        }

        if (pastNotifications != null) {
            pastNotifications!!.clear()
            pastNotifications = null
        }

        overlayNotifications.clear()
        broadcastOverlayNotifications()
        if (feedRequestReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(feedRequestReceiver)
            } catch (ignored: Exception) {
            }
            feedRequestReceiverRegistered = false
        }
        if (reloadReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(reloadReceiver)
            } catch (ignored: Exception) {
            }
            reloadReceiverRegistered = false
        }
        if (dismissReceiverRegistered) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(dismissReceiver)
            } catch (ignored: Exception) {
            }
            dismissReceiverRegistered = false
        }

        if (queue != null) {
            queue!!.clear()
            queue = null
        }

        active = false
    }

    override fun onDestroy() {
        dispose()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!enabled) return

        Log.d("TUI-Music", "onNotificationPosted: " + sbn.getPackageName())


        // Try to extract media session from notification if we don't have it
        val notification: android.app.Notification? = sbn.getNotification()
        if (notification != null && notification.extras != null) {
            val tokenObj = notification.extras.get(android.app.Notification.EXTRA_MEDIA_SESSION)
            if (tokenObj is MediaSession.Token) {
                val token: MediaSession.Token = tokenObj as MediaSession.Token
                handleMediaSessionToken(token, sbn.getPackageName())
            }
        }

        queue!!.offer(sbn)
        notifyNotificationWorker()
    }

    private fun handleMediaSessionToken(token: MediaSession.Token, packageName: String?) {
        synchronized(activeControllers) {
            var exists = false
            for (mc in activeControllers) {
                if (mc.getSessionToken() == token) {
                    exists = true
                    break
                }
            }
            if (!exists) {
                try {
                    Log.d(
                        "TUI-Music",
                        "Creating new MediaController from notification token for " + packageName
                    )
                    val controller = MediaController(this, token)
                    activeControllers.add(controller)
                    controller.registerCallback(mediaCallback)
                    broadcastMediaMetadata()
                } catch (e: Exception) {
                    Log.e("TUI-Music", "Error creating MediaController from token", e)
                }
            }
        }
    }

    //    0 = not found
    //    1 = the app wasnt found -> this is the first notification from this app
    //    2 = found
    private fun isInPastNotifications(pkg: String?, text: String?): Int {
        try {
            val notifications = pastNotifications!!.get(pkg)
            if (notifications == null) return 1
            for (n in notifications) if (n.text == text) return 2
        } catch (e: ConcurrentModificationException) {
        }
        return 0
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) {
            return
        }

        removeOverlayNotification(sbn.getKey(), sbn.getPackageName(), sbn.getNotification())
    }

    private fun seedActiveNotifications() {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || queue == null) {
            return
        }

        try {
            val activeNotifications: Array<StatusBarNotification?>? = getActiveNotifications()
            if (activeNotifications == null) {
                return
            }

            for (sbn in activeNotifications) {
                if (sbn != null) {
                    queue!!.offer(sbn)
                    notifyNotificationWorker()
                }
            }
        } catch (e: SecurityException) {
            Tuils.log("Notification access denied while seeding overlay: " + e.message)
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun loadConfig() {
        enabled = true
        terminalNotifications = NotificationSettings.printToOutput()
        format = NotificationSettings.format()
        color = NotificationSettings.defaultColor()
        click = NotificationSettings.clickOpensNotification()
        longClick = NotificationSettings.longClickOpensNotificationActions()
        maxOptionalDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth)

        if (notificationManager != null) {
            notificationManager!!.dispose()
        }
        notificationManager = NotificationManager.create(this)
    }

    private fun scheduleMediaProgressUpdates() {
        serviceHandler.removeCallbacks(progressUpdateRunnable)
        var anyPlaying = false
        synchronized(activeControllers) {
            for (controller in activeControllers) {
                val state: PlaybackState? = controller.getPlaybackState()
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    anyPlaying = true
                    break
                }
            }
        }
        if (anyPlaying) {
            serviceHandler.post(progressUpdateRunnable)
        }
    }

    private fun schedulePastNotificationCleanup() {
        serviceHandler.removeCallbacks(pastNotificationCleanupRunnable)
        serviceHandler.postDelayed(pastNotificationCleanupRunnable, UPDATE_TIME.toLong())
    }

    private fun notifyNotificationWorker() {
        synchronized(queueLock) {
            (queueLock as Object).notifyAll()
        }
    }

    private fun reloadNotificationConfig() {
        loadConfig()

        if (queue != null) {
            queue!!.clear()
        }

        overlayNotifications.clear()

        if (!enabled) {
            broadcastOverlayNotifications()
            return
        }

        seedActiveNotifications()
        broadcastOverlayNotifications()
    }

    private fun buildNotificationPreview(bundle: Bundle?, fallback: String?): String? {
        val titleString = extractNotificationTitle(bundle)
        val bodyString = extractNotificationBody(bundle)
        val fallbackString = cleanNotificationText(fallback)

        if (!TextUtils.isEmpty(titleString) && !TextUtils.isEmpty(bodyString)) {
            return titleString + " - " + bodyString
        }
        if (!TextUtils.isEmpty(titleString)) {
            return titleString
        }
        if (!TextUtils.isEmpty(bodyString)) {
            return bodyString
        }
        return fallbackString
    }

    private fun extractNotificationTitle(bundle: Bundle?): String {
        if (bundle == null) {
            return Tuils.EMPTYSTRING
        }

        val title: CharSequence? = bundle.getCharSequence(android.app.Notification.EXTRA_TITLE)
        return cleanNotificationText(if (title != null) title.toString() else null)
    }

    private fun extractNotificationBody(bundle: Bundle?): String {
        if (bundle == null) {
            return Tuils.EMPTYSTRING
        }

        val text: CharSequence? = bundle.getCharSequence(android.app.Notification.EXTRA_TEXT)
        val bigText: CharSequence? = bundle.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)

        var bodyString = if (text != null) text.toString().trim { it <= ' ' } else Tuils.EMPTYSTRING
        if (TextUtils.isEmpty(bodyString) && bigText != null) {
            bodyString = bigText.toString().trim { it <= ' ' }
        }

        return cleanNotificationText(bodyString)
    }

    private fun cleanNotificationText(value: String?): String {
        if (value == null) {
            return Tuils.EMPTYSTRING
        }
        val clean = value.trim { it <= ' ' }
        if (TextUtils.isEmpty(clean) || "null".equals(clean, ignoreCase = true)) {
            return Tuils.EMPTYSTRING
        }
        if (clean.contains("%pkg") || clean.contains("%t") || clean.contains("--- null")) {
            return Tuils.EMPTYSTRING
        }
        return clean.replace("(?i)\\bnull\\b".toRegex(), "").replace("\\s+---\\s*$".toRegex(), "")
            .trim { it <= ' ' }
    }

    private fun pushOverlayNotification(notification: Notification?) {
        if (notification == null) {
            return
        }

        for (i in overlayNotifications.indices) {
            val existing = overlayNotifications.get(i)
            val keyMatches = !TextUtils.isEmpty(notification.key) && TextUtils.equals(existing.key, notification.key)
            val fallbackMatches = TextUtils.equals(
                    existing.pkg,
                    notification.pkg
                ) && TextUtils.equals(existing.preview, notification.preview)
            if (keyMatches || fallbackMatches) {
                overlayNotifications.removeAt(i)
                break
            }
        }

        overlayNotifications.add(0, notification)
        while (overlayNotifications.size > MAX_OVERLAY_NOTIFICATIONS) {
            overlayNotifications.removeAt(overlayNotifications.size - 1)
        }

        broadcastOverlayNotifications()
    }

    private fun dismissNotification(key: String?) {
        if (TextUtils.isEmpty(key)) {
            return
        }

        try {
            cancelNotification(key)
            removeOverlayNotificationByKey(key)
        } catch (e: SecurityException) {
            Tuils.log("Unable to clear notification: " + e.message)
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun removeOverlayNotificationByKey(key: String?) {
        if (TextUtils.isEmpty(key)) {
            return
        }

        var changed = false
        var i = 0
        while (i < overlayNotifications.size) {
            if (TextUtils.equals(overlayNotifications.get(i).key, key)) {
                overlayNotifications.removeAt(i)
                changed = true
                i--
            }
            i++
        }

        if (changed) {
            broadcastOverlayNotifications()
        }
    }

    private fun removeOverlayNotification(key: String?, pkg: String?, rawNotification: android.app.Notification) {
        val preview = buildNotificationPreview(NotificationCompat.getExtras(rawNotification), null)

        var i = 0
        while (i < overlayNotifications.size) {
            val existing = overlayNotifications.get(i)
            val keyMatches = !TextUtils.isEmpty(key) && TextUtils.equals(existing.key, key)
            val packageMatches = TextUtils.equals(existing.pkg, pkg)
            val previewMatches = TextUtils.equals(existing.preview, preview)
            if (keyMatches || (packageMatches && (previewMatches || TextUtils.isEmpty(preview)))) {
                overlayNotifications.removeAt(i)
                i--
            }
            i++
        }

        broadcastOverlayNotifications()
    }

    private fun broadcastOverlayNotifications() {
        val intent: Intent = Intent(ACTION_NOTIFICATION_FEED)
        intent.putParcelableArrayListExtra(
            EXTRA_NOTIFICATION_LIST,
            ArrayList<Notification?>(overlayNotifications)
        )
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    class Notification : Parcelable {
        var time: Long
        var text: String?
        var pkg: String?
        var appName: String? = null
        var preview: String? = null
        var title: String? = null
        var body: String? = null
        var key: String? = null
        var pendingIntent: PendingIntent?

        constructor(time: Long, text: String?, pkg: String?, pi: PendingIntent?, key: String?) {
            this.time = time
            this.text = text
            this.pkg = pkg
            this.pendingIntent = pi
            this.key = key
        }

        protected constructor(`in`: Parcel) {
            time = `in`.readLong()
            text = `in`.readString()
            pkg = `in`.readString()
            appName = `in`.readString()
            preview = `in`.readString()
            title = `in`.readString()
            body = `in`.readString()
            key = `in`.readString()
            pendingIntent =
                `in`.readParcelable<PendingIntent?>(PendingIntent::class.java.getClassLoader())
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeLong(time)
            dest.writeString(text)
            dest.writeString(pkg)
            dest.writeString(appName)
            dest.writeString(preview)
            dest.writeString(title)
            dest.writeString(body)
            dest.writeString(key)
            dest.writeParcelable(pendingIntent, flags)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<Notification?> =
                object : Parcelable.Creator<Notification?> {
                    override fun createFromParcel(`in`: Parcel): Notification {
                        return Notification(`in`)
                    }

                    override fun newArray(size: Int): Array<Notification?> {
                        return arrayOfNulls<Notification>(size)
                    }
                }
        }
    }

    companion object {
        const val DESTROY: String = "destroy"
        val ACTION_NOTIFICATION_FEED: String = BuildConfig.APPLICATION_ID + ".notification_feed"
        const val EXTRA_NOTIFICATION_LIST: String = "notification_list"
        val ACTION_REQUEST_NOTIFICATION_FEED: String =
            BuildConfig.APPLICATION_ID + ".notification_feed_request"
        val ACTION_RELOAD_NOTIFICATION_CONFIG: String =
            BuildConfig.APPLICATION_ID + ".notification_reload"
        val ACTION_DISMISS_NOTIFICATION: String =
            BuildConfig.APPLICATION_ID + ".notification_dismiss"
        const val EXTRA_NOTIFICATION_KEY: String = "notification_key"
        private const val MEDIA_SESSION_EMPTY_GRACE_MS = 3000L
        private const val MAX_OVERLAY_NOTIFICATIONS = 12

        fun requestReload(context: Context?) {
            if (context == null) {
                return
            }

            requestListenerRebind(context)

            LocalBroadcastManager.getInstance(context.getApplicationContext())
                .sendBroadcast(Intent(ACTION_RELOAD_NOTIFICATION_CONFIG))
        }

        fun requestDismiss(context: Context?, key: String?) {
            if (context == null || TextUtils.isEmpty(key)) {
                return
            }

            val intent = Intent(ACTION_DISMISS_NOTIFICATION)
            intent.putExtra(EXTRA_NOTIFICATION_KEY, key)
            LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(intent)
        }

        fun requestListenerRebind(context: Context?) {
            if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                return
            }

            try {
                NotificationListenerService.requestRebind(
                    ComponentName(
                        context,
                        NotificationService::class.java
                    )
                )
            } catch (e: Exception) {
                Tuils.log(e)
            }
        }
    }
}
