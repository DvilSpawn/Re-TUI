package ohi.andre.consolelauncher.managers.notifications;

/**
 * Created by francescoandreuzzi on 27/04/2017.
 */

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.app.NotificationCompat;
import android.text.TextUtils;

import android.content.ComponentName;
import android.content.Context;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;
import ohi.andre.consolelauncher.BuildConfig;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import ohi.andre.consolelauncher.managers.music.MusicService;
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.tuils.StoppableThread;
import ohi.andre.consolelauncher.tuils.Tuils;


@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class NotificationService extends NotificationListenerService {

    public static final String DESTROY = "destroy";
    public static final String ACTION_NOTIFICATION_FEED = BuildConfig.APPLICATION_ID + ".notification_feed";
    public static final String EXTRA_NOTIFICATION_LIST = "notification_list";
    public static final String ACTION_REQUEST_NOTIFICATION_FEED = BuildConfig.APPLICATION_ID + ".notification_feed_request";
    public static final String ACTION_RELOAD_NOTIFICATION_CONFIG = BuildConfig.APPLICATION_ID + ".notification_reload";
    private static final long MEDIA_SESSION_EMPTY_GRACE_MS = 3000L;
    private static final int MAX_OVERLAY_NOTIFICATIONS = 12;

    private final int UPDATE_TIME = 2000;
    private String LINES_LABEL = "Lines";
    private String ANDROID_LABEL_PREFIX = "android.";
    private String NULL_LABEL = "null";

    HashMap<String, List<Notification>> pastNotifications;
    Handler handler = new Handler();

    String format;
    int color, maxOptionalDepth;
    boolean enabled, click, longClick, active, terminalNotifications;

    Queue<StatusBarNotification> queue;

    final String PKG = "%pkg", APP = "%app", NEWLINE = "%n";
    final Pattern timePattern = Pattern.compile("^%t[0-9]*$");

    PackageManager manager;
    ReplyManager replyManager;
    NotificationManager notificationManager;

    private final Pattern formatPattern = Pattern.compile("%(?:\\[(\\d+)\\])?(?:\\[([^]]+)\\])?(?:(?:\\{)([a-zA-Z\\.\\:\\s]+)(?:\\})|([a-zA-Z\\.\\:]+))");

    StoppableThread bgThread;

    private MediaSessionManager mediaSessionManager;
    private List<MediaController> activeControllers = new ArrayList<>();
    private final ArrayList<Notification> overlayNotifications = new ArrayList<>();
    private final android.content.BroadcastReceiver feedRequestReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_REQUEST_NOTIFICATION_FEED.equals(intent.getAction())) {
                broadcastOverlayNotifications();
            }
        }
    };
    private final android.content.BroadcastReceiver reloadReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_RELOAD_NOTIFICATION_CONFIG.equals(intent.getAction())) {
                reloadNotificationConfig();
            }
        }
    };
    private String lastMediaTitle;
    private String lastMediaArtist;
    private int lastMediaDuration;
    private int lastMediaPosition;
    private boolean hasLastMediaState;
    private final Runnable clearExternalMusicRunnable = new Runnable() {
        @Override
        public void run() {
            clearRememberedMediaState();
            sendMusicBroadcast(null, null, 0, 0, false);
        }
    };

    private MediaSessionManager.OnActiveSessionsChangedListener sessionsChangedListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            updateActiveSessions(controllers);
        }
    };

    private BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MusicService.ACTION_MUSIC_CONTROL.equals(intent.getAction())) {
                int cmd = intent.getIntExtra(MusicService.EXTRA_CONTROL_CMD, -1);
                handleControlCommand(cmd);
            }
        }
    };

    private void handleControlCommand(int cmd) {
        if (activeControllers.isEmpty()) return;

        MediaController activeController = null;
        for (MediaController controller : activeControllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                activeController = controller;
                break;
            }
        }
        if (activeController == null) activeController = activeControllers.get(0);

        Log.d("TUI-Music", "Handling control command: " + cmd + " for " + activeController.getPackageName());
        
        switch (cmd) {
            case MusicService.CONTROL_NEXT_INT:
                activeController.getTransportControls().skipToNext();
                break;
            case MusicService.CONTROL_PREV_INT:
                activeController.getTransportControls().skipToPrevious();
                break;
            case MusicService.CONTROL_PLAY_PAUSE_INT:
                PlaybackState state = activeController.getPlaybackState();
                if (state != null) {
                    if (state.getState() == PlaybackState.STATE_PLAYING) {
                        activeController.getTransportControls().pause();
                    } else {
                        activeController.getTransportControls().play();
                    }
                }
                break;
        }
    }

    private MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            broadcastMediaMetadata();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            broadcastMediaMetadata();
        }
    };

    public static void requestReload(Context context) {
        if (context == null) {
            return;
        }

        requestListenerRebind(context);

        Intent intent = new Intent(context, NotificationService.class);
        intent.setAction(ACTION_RELOAD_NOTIFICATION_CONFIG);
        context.startService(intent);

        LocalBroadcastManager.getInstance(context.getApplicationContext())
                .sendBroadcast(new Intent(ACTION_RELOAD_NOTIFICATION_CONFIG));
    }

    public static void requestListenerRebind(Context context) {
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        try {
            requestRebind(new ComponentName(context, NotificationService.class));
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private void updateActiveSessions(List<MediaController> controllers) {
        Log.d("TUI-Music", "updateActiveSessions: " + (controllers != null ? controllers.size() : 0) + " sessions");
        if (controllers != null) {
            for (MediaController mc : controllers) {
                Log.d("TUI-Music", "Session: " + mc.getPackageName() + " State: " + (mc.getPlaybackState() != null ? mc.getPlaybackState().getState() : "null"));
            }
        }
        synchronized (activeControllers) {
            for (MediaController controller : activeControllers) {
                controller.unregisterCallback(mediaCallback);
            }
            activeControllers.clear();

            if (controllers != null) {
                activeControllers.addAll(controllers);
                for (MediaController controller : activeControllers) {
                    controller.registerCallback(mediaCallback);
                    Log.d("TUI-Music", "Registered callback for: " + controller.getPackageName());
                }
            }
        }
        broadcastMediaMetadata();
    }

    private void broadcastMediaMetadata() {
        if (activeControllers.isEmpty()) {
            Log.d("TUI-Music", "No active controllers to broadcast");
            if (hasLastMediaState) {
                scheduleExternalMusicClear();
            } else {
                sendMusicBroadcast(null, null, 0, 0, false, MusicService.SOURCE_EXTERNAL, null);
            }
            return;
        }

        cancelExternalMusicClear();

        MediaController activeController = resolveActiveController();
        if (activeController == null) {
            return;
        }

        String preferredPkg = XMLPrefsManager.get(Behavior.preferred_music_app);
        if (!TextUtils.isEmpty(preferredPkg) && !preferredPkg.equals(activeController.getPackageName())) {
            Log.d("TUI-Music", "Active controller " + activeController.getPackageName() + " is not preferred. Hiding widget.");
            sendMusicBroadcast(null, null, 0, 0, false, MusicService.SOURCE_EXTERNAL, activeController.getPackageName());
            return;
        }

        MediaMetadata metadata = activeController.getMetadata();
        PlaybackState state = activeController.getPlaybackState();
        boolean isPlaying = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        int position = state != null ? (int) state.getPosition() : lastMediaPosition;

        if (metadata != null) {
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);

            int duration = (int) metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            Log.d("TUI-Music", "Broadcasting: " + title + " by " + artist + " (playing=" + isPlaying + ") pkg=" + activeController.getPackageName());
            rememberMediaState(title, artist, duration, position);
            sendMusicBroadcast(title, artist, duration, position, isPlaying, MusicService.SOURCE_EXTERNAL, activeController.getPackageName());
            return;
        }

        Log.d("TUI-Music", "No metadata for active controller: " + activeController.getPackageName());
        if (hasLastMediaState) {
            sendMusicBroadcast(lastMediaTitle, lastMediaArtist, lastMediaDuration, position, isPlaying, MusicService.SOURCE_EXTERNAL, activeController.getPackageName());
            lastMediaPosition = position;
        } else {
            sendMusicBroadcast(null, null, 0, 0, isPlaying, MusicService.SOURCE_EXTERNAL, activeController.getPackageName());
        }
    }

    private MediaController resolveActiveController() {
        if (activeControllers.isEmpty()) {
            return null;
        }

        List<MediaController> rankedControllers = new ArrayList<>(activeControllers);
        final String preferredPackage = XMLPrefsManager.get(Behavior.preferred_music_app);

        Collections.sort(rankedControllers, new Comparator<MediaController>() {
            @Override
            public int compare(MediaController left, MediaController right) {
                return controllerRank(right, preferredPackage) - controllerRank(left, preferredPackage);
            }
        });

        return rankedControllers.get(0);
    }

    private int controllerRank(MediaController controller, String preferredPackage) {
        int rank = 0;

        String packageName = controller != null && controller.getPackageName() != null
                ? controller.getPackageName()
                : Tuils.EMPTYSTRING;

        PlaybackState state = controller != null ? controller.getPlaybackState() : null;
        if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
            rank += 1000;
        }

        if (!TextUtils.isEmpty(preferredPackage) && preferredPackage.equals(packageName)) {
            rank += 100;
        }

        if (controller != null && controller.getMetadata() != null) {
            String title = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = controller.getMetadata().getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (!TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist)) {
                rank += 1;
            }
        }

        return rank;
    }

    private void rememberMediaState(String title, String artist, int duration, int position) {
        lastMediaTitle = title;
        lastMediaArtist = artist;
        lastMediaDuration = duration;
        lastMediaPosition = position;
        hasLastMediaState = !TextUtils.isEmpty(title) || !TextUtils.isEmpty(artist) || duration > 0;
    }

    private void clearRememberedMediaState() {
        lastMediaTitle = null;
        lastMediaArtist = null;
        lastMediaDuration = 0;
        lastMediaPosition = 0;
        hasLastMediaState = false;
    }

    private void scheduleExternalMusicClear() {
        handler.removeCallbacks(clearExternalMusicRunnable);
        handler.postDelayed(clearExternalMusicRunnable, MEDIA_SESSION_EMPTY_GRACE_MS);
    }

    private void cancelExternalMusicClear() {
        handler.removeCallbacks(clearExternalMusicRunnable);
    }

    private void sendMusicBroadcast(String title, String artist, int duration, int position, boolean isPlaying) {
        sendMusicBroadcast(title, artist, duration, position, isPlaying, MusicService.SOURCE_EXTERNAL, null);
    }

    private void sendMusicBroadcast(String title, String artist, int duration, int position, boolean isPlaying, String source, String packageName) {
        Intent intent = new Intent(MusicService.ACTION_MUSIC_CHANGED);
        if (title != null) {
            intent.putExtra(MusicService.SONG_TITLE, title);
        }
        if (artist != null) {
            intent.putExtra(MusicService.SONG_SINGER, artist);
        }
        intent.putExtra(MusicService.SONG_DURATION, duration);
        intent.putExtra(MusicService.SONG_POSITION, position);
        intent.putExtra(MusicService.MUSIC_PLAYING, isPlaying);
        intent.putExtra(MusicService.MUSIC_SOURCE, source);
        if (packageName != null) {
            intent.putExtra("package", packageName);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        sendBroadcast(intent);
    }

    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            boolean anyPlaying = false;
            for (MediaController controller : activeControllers) {
                PlaybackState state = controller.getPlaybackState();
                if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                    anyPlaying = true;
                    break;
                }
            }
            if (anyPlaying) {
                broadcastMediaMetadata();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(MusicService.ACTION_MUSIC_CONTROL);
        LocalBroadcastManager.getInstance(this).registerReceiver(controlReceiver, filter);

        init();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d("TUI-Music", "NotificationListener connected");
        if (!active) {
            init();
        } else {
            reloadNotificationConfig();
        }
        setupMediaSession();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d("TUI-Music", "NotificationListener disconnected");
    }

    private void setupMediaSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (mediaSessionManager == null) {
                mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            }
            ComponentName componentName = new ComponentName(this, NotificationService.class);
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName);
                List<MediaController> sessions = mediaSessionManager.getActiveSessions(componentName);
                Log.d("TUI-Music", "Initial active sessions count: " + (sessions != null ? sessions.size() : 0));
                updateActiveSessions(sessions);
            } catch (SecurityException e) {
                Log.e("TUI-Music", "MediaSession access denied: " + e.getMessage());
            } catch (Exception e) {
                Log.e("TUI-Music", "Error setting up MediaSession: " + e.getMessage());
            }
        }
    }

    private void init() {
        try {
            Tuils.init(this);
            notificationManager = NotificationManager.create(this);
            XMLPrefsManager.loadCommons(this);
        } catch (Exception e) {
            Tuils.log(e);
            return;
        }

        try {
            replyManager = new ReplyManager(this);
        } catch (VerifyError error) {
            replyManager = null;
        }

        bgThread = new StoppableThread() {
            @Override
            public void run() {
                super.run();

                if(!enabled) return;

                while(true) {
                    if(isInterrupted()) return;

                    if(queue != null) {

                        StatusBarNotification sbn;
                        while ((sbn = queue.poll()) != null) {
                            Log.d("TUI-Notif", "Processing notification from: " + sbn.getPackageName());
                            android.app.Notification notification = sbn.getNotification();
                            if (notification == null) {
                                continue;
                            }

                            String pack = sbn.getPackageName();

                            String appName;
                            try {
                                appName = manager.getApplicationInfo(pack, 0).loadLabel(manager).toString();
                            } catch (PackageManager.NameNotFoundException e) {
                                appName = "null";
                            }

                            NotificationManager.NotificatedApp nApp = notificationManager.getAppState(pack);
                            if ((nApp != null && !nApp.enabled)) {
                                continue;
                            }

                            if (nApp == null && !notificationManager.default_app_state) {
                                continue;
                            }

                            String f;
                            if(nApp != null && nApp.format != null) f = nApp.format;
                            else f = format;

                            int textColor;
                            if(nApp != null && nApp.color != null) textColor = Color.parseColor(nApp.color);
                            else textColor = color;

                            CharSequence s = Tuils.span(f, textColor);

                            Bundle bundle = NotificationCompat.getExtras(notification);

                            if(bundle != null) {
                                Matcher m = formatPattern.matcher(s);
                                String match;
                                while(m.find()) {
                                    match = m.group(0);
                                    if (!match.startsWith(PKG) && !match.startsWith(APP) && !match.startsWith(NEWLINE) && !timePattern.matcher(match).matches()) {
                                        String length = m.group(1);
                                        String color = m.group(2);
                                        String value = m.group(3);

                                        if(value == null || value.length() == 0) value = m.group(4);

                                        if(value != null) value = value.trim();
                                        else continue;

                                        if(value.length() == 0) continue;

                                        if(value.equals("ttl")) value = "title";
                                        else if(value.equals("txt")) value = "text";

                                        String[] temp = value.split(":"), split;
//                                    this is an other way to do what I did in NotesManager for footer/header
                                        if(value.endsWith(":")) {
                                            split = new String[temp.length + 1];
                                            System.arraycopy(temp, 0, split, 0, temp.length);
                                            split[split.length - 1] = Tuils.EMPTYSTRING;
                                        } else split = temp;

//                                    because the last one is the default text, but only if there is more than one label
                                        int stopAt = split.length;
                                        if(stopAt > 1) stopAt--;

                                        CharSequence text = null;
                                        for(int j = 0; j < stopAt; j++) {
                                            if(split[j].contains(LINES_LABEL)) {
                                                CharSequence[] array = bundle.getCharSequenceArray(ANDROID_LABEL_PREFIX + split[j]);
                                                if(array != null) {
                                                    for(CharSequence c : array) {
                                                        if(text == null) text = c;
                                                        else text = TextUtils.concat(text, Tuils.NEWLINE, c);
                                                    }
                                                }
                                            } else {
                                                text = bundle.getCharSequence(ANDROID_LABEL_PREFIX + split[j]);
                                            }

                                            if(text != null && text.length() > 0) break;
                                        }

                                        if(text == null || text.length() == 0) {
                                            text = split.length == 1 ? NULL_LABEL : split[split.length - 1];
                                        }

                                        String stringed = text.toString().trim();

                                        try {
                                            int l = Integer.parseInt(length);
                                            stringed = stringed.substring(0,l);
                                        } catch (Exception e) {}

                                        try {
                                            text = Tuils.span(stringed, Color.parseColor(color));
                                        } catch (Exception e) {
                                            text = stringed;
                                        }

                                        s = TextUtils.replace(s, new String[] {m.group(0)}, new CharSequence[] {text});
                                    }
                                }
                            }

                            String text = s.toString();

                            if(notificationManager.match(text)) continue;

                            int found = isInPastNotifications(pack, text);
//                        if(found == 0) {
//                            Tuils.log("app " + pack, pastNotifications.get(pack).toString());
//                        }

                            if(found == 2) continue;

//                        else
                            Notification n = new Notification(System.currentTimeMillis(), text, pack, notification.contentIntent);

                            if(found == 1) {
                                List<Notification> ns = new ArrayList<>();
                                ns.add(n);
                                pastNotifications.put(pack, ns);
                            } else if(found == 0) {
                                pastNotifications.get(pack).add(n);
                            }

                            n.appName = appName;
                            n.preview = buildNotificationPreview(bundle, text);
                            pushOverlayNotification(n);

                            s = TextUtils.replace(s, new String[]{PKG, APP, NEWLINE}, new CharSequence[]{pack, appName, Tuils.NEWLINE});
                            String st = s.toString();
                            while (st.contains(NEWLINE)) {
                                s = TextUtils.replace(s,
                                        new String[]{NEWLINE},
                                        new CharSequence[]{Tuils.NEWLINE});
                                st = s.toString();
                            }

                            try {
                                s = TimeManager.instance.replace(s);
                            } catch (Exception e) {
                                Tuils.log(e);
                            }

//                        Tuils.log("text", text);
//                        Tuils.log("--------");

                            if (terminalNotifications) {
                                Tuils.sendOutput(NotificationService.this.getApplicationContext(), s, TerminalManager.CATEGORY_NO_COLOR, click ? notification.contentIntent : null, longClick ? n : null);
                            }

                            Intent notifyIntent = new Intent(ohi.andre.consolelauncher.UIManager.ACTION_NOTIFICATION_RECEIVED);
                            notifyIntent.putExtra(ohi.andre.consolelauncher.UIManager.NOTIFICATION_TEXT, s);
                            LocalBroadcastManager.getInstance(NotificationService.this).sendBroadcast(notifyIntent);

                            if(replyManager != null) replyManager.onNotification(sbn, s);
                        }
                    }

                    try {
                        sleep(UPDATE_TIME);
                    } catch (InterruptedException e) {
                        Tuils.log(e);
                        return;
                    }
                }
            }
        };

        manager = getPackageManager();
        pastNotifications = new HashMap<>();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Map.Entry<String, List<Notification>> entry : pastNotifications.entrySet()) {
                    List<Notification> notifications = entry.getValue();

                    Iterator<Notification> it = notifications.iterator();
                    while (it.hasNext()) {
                        if (now - it.next().time >= UPDATE_TIME) it.remove();
                    }
                }

                handler.postDelayed(this, UPDATE_TIME);
            }
        });

        queue = new ArrayBlockingQueue<>(5);
        LocalBroadcastManager.getInstance(this).registerReceiver(feedRequestReceiver, new android.content.IntentFilter(ACTION_REQUEST_NOTIFICATION_FEED));
        LocalBroadcastManager.getInstance(this).registerReceiver(reloadReceiver, new android.content.IntentFilter(ACTION_RELOAD_NOTIFICATION_CONFIG));
        loadConfig();
        seedActiveNotifications();
        bgThread.start();

        setupMediaSession();
        handler.post(progressUpdateRunnable);

        active = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null) {
            if (ACTION_RELOAD_NOTIFICATION_CONFIG.equals(intent.getAction())) {
                if(!active) init();
                reloadNotificationConfig();
                return START_STICKY;
            }

            boolean destroy = intent.getBooleanExtra(DESTROY, false);
            if(destroy) dispose();
        }

        if(!active) init();

        return START_STICKY;
    }

    private void dispose() {
        cancelExternalMusicClear();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(controlReceiver);
        if (mediaSessionManager != null && sessionsChangedListener != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsChangedListener);
        }
        for (MediaController controller : activeControllers) {
            controller.unregisterCallback(mediaCallback);
        }
        activeControllers.clear();
        clearRememberedMediaState();

        if(replyManager != null) {
            replyManager.dispose(this);
            replyManager = null;
        }

        if(notificationManager != null) {
            notificationManager.dispose();
            notificationManager = null;
        }

        bgThread.interrupt();
        bgThread = null;

        if(pastNotifications != null) {
            pastNotifications.clear();
            pastNotifications = null;
        }

        overlayNotifications.clear();
        broadcastOverlayNotifications();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(feedRequestReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(reloadReceiver);

        if(queue != null) {
            queue.clear();
            queue = null;
        }

        active = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

//        ondestroy won't ever be called
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if(!enabled) return;

        Log.d("TUI-Music", "onNotificationPosted: " + sbn.getPackageName());
        
        // Try to extract media session from notification if we don't have it
        android.app.Notification notification = sbn.getNotification();
        if (notification != null && notification.extras != null) {
            Object tokenObj = notification.extras.get(android.app.Notification.EXTRA_MEDIA_SESSION);
            if (tokenObj instanceof android.media.session.MediaSession.Token) {
                android.media.session.MediaSession.Token token = (android.media.session.MediaSession.Token) tokenObj;
                handleMediaSessionToken(token, sbn.getPackageName());
            }
        }

        queue.offer(sbn);
    }

    private void handleMediaSessionToken(android.media.session.MediaSession.Token token, String packageName) {
        synchronized (activeControllers) {
            boolean exists = false;
            for (MediaController mc : activeControllers) {
                if (mc.getSessionToken().equals(token)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                try {
                    Log.d("TUI-Music", "Creating new MediaController from notification token for " + packageName);
                    MediaController controller = new MediaController(this, token);
                    activeControllers.add(controller);
                    controller.registerCallback(mediaCallback);
                    broadcastMediaMetadata();
                } catch (Exception e) {
                    Log.e("TUI-Music", "Error creating MediaController from token", e);
                }
            }
        }
    }

//    0 = not found
//    1 = the app wasnt found -> this is the first notification from this app
//    2 = found
    private int isInPastNotifications(String pkg, String text) {
        try {
            List<Notification> notifications = pastNotifications.get(pkg);
            if(notifications == null) return 1;
            for(Notification n : notifications) if(n.text.equals(text)) return 2;
        } catch (ConcurrentModificationException e) {}
        return 0;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null) {
            return;
        }

        removeOverlayNotification(sbn.getPackageName(), sbn.getNotification());
    }

    private void seedActiveNotifications() {
        if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || queue == null) {
            return;
        }

        try {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            if (activeNotifications == null) {
                return;
            }

            for (StatusBarNotification sbn : activeNotifications) {
                if (sbn != null) {
                    queue.offer(sbn);
                }
            }
        } catch (SecurityException e) {
            Tuils.log("Notification access denied while seeding overlay: " + e.getMessage());
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private void loadConfig() {
        enabled = XMLPrefsManager.getBoolean(Notifications.show_notifications) || XMLPrefsManager.get(Notifications.show_notifications).equalsIgnoreCase("enabled");
        terminalNotifications = XMLPrefsManager.getBoolean(Notifications.terminal_notifications);
        format = XMLPrefsManager.get(Notifications.notification_format);
        color = XMLPrefsManager.getColor(Notifications.default_notification_color);
        click = XMLPrefsManager.getBoolean(Notifications.click_notification);
        longClick = XMLPrefsManager.getBoolean(Notifications.long_click_notification);
        maxOptionalDepth = XMLPrefsManager.getInt(Behavior.max_optional_depth);

        if(notificationManager != null) {
            notificationManager.dispose();
        }
        notificationManager = NotificationManager.create(this);
    }

    private void reloadNotificationConfig() {
        loadConfig();

        if (queue != null) {
            queue.clear();
        }

        if (!enabled) {
            overlayNotifications.clear();
            broadcastOverlayNotifications();
            return;
        }

        seedActiveNotifications();
        broadcastOverlayNotifications();
    }

    private String buildNotificationPreview(Bundle bundle, String fallback) {
        if (bundle == null) {
            return fallback;
        }

        CharSequence title = bundle.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence text = bundle.getCharSequence(android.app.Notification.EXTRA_TEXT);
        CharSequence bigText = bundle.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT);

        String titleString = title != null ? title.toString().trim() : Tuils.EMPTYSTRING;
        String bodyString = text != null ? text.toString().trim() : Tuils.EMPTYSTRING;
        if (TextUtils.isEmpty(bodyString) && bigText != null) {
            bodyString = bigText.toString().trim();
        }

        if (!TextUtils.isEmpty(titleString) && !TextUtils.isEmpty(bodyString)) {
            return titleString + " - " + bodyString;
        }
        if (!TextUtils.isEmpty(titleString)) {
            return titleString;
        }
        if (!TextUtils.isEmpty(bodyString)) {
            return bodyString;
        }
        return fallback;
    }

    private void pushOverlayNotification(Notification notification) {
        if (notification == null) {
            return;
        }

        for (int i = 0; i < overlayNotifications.size(); i++) {
            Notification existing = overlayNotifications.get(i);
            if (TextUtils.equals(existing.pkg, notification.pkg) && TextUtils.equals(existing.preview, notification.preview)) {
                overlayNotifications.remove(i);
                break;
            }
        }

        overlayNotifications.add(0, notification);
        while (overlayNotifications.size() > MAX_OVERLAY_NOTIFICATIONS) {
            overlayNotifications.remove(overlayNotifications.size() - 1);
        }

        broadcastOverlayNotifications();
    }

    private void removeOverlayNotification(String pkg, android.app.Notification rawNotification) {
        String preview = buildNotificationPreview(NotificationCompat.getExtras(rawNotification), null);

        for (int i = 0; i < overlayNotifications.size(); i++) {
            Notification existing = overlayNotifications.get(i);
            boolean packageMatches = TextUtils.equals(existing.pkg, pkg);
            boolean previewMatches = TextUtils.equals(existing.preview, preview);
            if (packageMatches && (previewMatches || TextUtils.isEmpty(preview))) {
                overlayNotifications.remove(i);
                i--;
            }
        }

        broadcastOverlayNotifications();
    }

    private void broadcastOverlayNotifications() {
        Intent intent = new Intent(ACTION_NOTIFICATION_FEED);
        intent.putParcelableArrayListExtra(EXTRA_NOTIFICATION_LIST, new ArrayList<>(overlayNotifications));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public static class Notification implements Parcelable {
        public long time;
        public String text, pkg, appName, preview;
        public PendingIntent pendingIntent;

        public Notification(long time, String text, String pkg, PendingIntent pi) {
            this.time = time;
            this.text = text;
            this.pkg = pkg;
            this.pendingIntent = pi;
        }

        protected Notification(Parcel in) {
            time = in.readLong();
            text = in.readString();
            pkg = in.readString();
            appName = in.readString();
            preview = in.readString();
            pendingIntent = in.readParcelable(PendingIntent.class.getClassLoader());
        }

        public static final Creator<Notification> CREATOR = new Creator<Notification>() {
            @Override
            public Notification createFromParcel(Parcel in) {
                return new Notification(in);
            }

            @Override
            public Notification[] newArray(int size) {
                return new Notification[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(time);
            dest.writeString(text);
            dest.writeString(pkg);
            dest.writeString(appName);
            dest.writeString(preview);
            dest.writeParcelable(pendingIntent, flags);
        }
    }
}
