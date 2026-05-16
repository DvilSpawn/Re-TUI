package ohi.andre.consolelauncher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.widget.ProgressBar;
import android.widget.Button;
import ohi.andre.consolelauncher.managers.PomodoroManager;
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog;
import androidx.core.view.GestureDetectorCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.AliasManager;
import ohi.andre.consolelauncher.managers.ClockManager;
import ohi.andre.consolelauncher.managers.FileManager;
import ohi.andre.consolelauncher.managers.music.MusicService;
import ohi.andre.consolelauncher.managers.modules.ModuleManager;
import ohi.andre.consolelauncher.managers.modules.ModuleVariableManager;
import ohi.andre.consolelauncher.managers.modules.ReminderManager;
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine;
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.AbsListView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand;
import ohi.andre.consolelauncher.commands.main.raw.tbridge;
import ohi.andre.consolelauncher.managers.HTMLExtractManager;
import ohi.andre.consolelauncher.managers.NotesManager;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.managers.TuiLocationManager;
import ohi.andre.consolelauncher.managers.file.FileBackendManager;
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager;
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.settings.MusicSettings;
import ohi.andre.consolelauncher.managers.settings.NotificationSettings;
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher;
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager;
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache;
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.managers.xml.options.Suggestions;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Toolbar;

import androidx.annotation.NonNull;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.AllowEqualsSequence;
import ohi.andre.consolelauncher.tuils.MusicVisualizerView;
import ohi.andre.consolelauncher.tuils.NetworkUtils;
import ohi.andre.consolelauncher.tuils.OutlineEditText;
import ohi.andre.consolelauncher.tuils.OutlineTextView;
import ohi.andre.consolelauncher.tuils.StableHorizontalScrollView;
import ohi.andre.consolelauncher.tuils.UIUtils;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.managers.status.NetworkManager;
import ohi.andre.consolelauncher.managers.status.RamManager;
import ohi.andre.consolelauncher.managers.status.StatusUpdateListener;
import ohi.andre.consolelauncher.managers.status.StorageManager;
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter;
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate;
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener;
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged;
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver;

public class UIManager implements OnTouchListener {

    public static final String NEXT_UNLOCK_CYCLE_RESTART = "nextUnlockRestart";
    public static final String UNLOCK_KEY = "unlockTimes";
    public static final String PREFS_NAME = "ui";
    private static final String PREF_OUTPUT_TRAY_EXPANDED = "output_tray_expanded";
    private static final String CLOCK_EDGE_LEFT = "left";
    private static final String CLOCK_EDGE_RIGHT = "right";
    private static final String CLOCK_EDGE_TOP = "top";
    private static final String CLOCK_EDGE_BOTTOM = "bottom";
    private static final String PREF_TIMER_BADGE_EDGE = "timer_badge_edge";
    private static final String PREF_TIMER_BADGE_FRACTION = "timer_badge_fraction";
    private static final String PREF_STOPWATCH_BADGE_EDGE = "stopwatch_badge_edge";
    private static final String PREF_STOPWATCH_BADGE_FRACTION = "stopwatch_badge_fraction";
    public static final String ACTION_UPDATE_SUGGESTIONS = BuildConfig.APPLICATION_ID + ".ui_update_suggestions";
    public static final String ACTION_UPDATE_HINT = BuildConfig.APPLICATION_ID + ".ui_update_hint";
    public static String ACTION_ROOT = BuildConfig.APPLICATION_ID + ".ui_root";
    public static String ACTION_NOROOT = BuildConfig.APPLICATION_ID + ".ui_noroot";
    public static String ACTION_LOGTOFILE = BuildConfig.APPLICATION_ID + ".ui_log";
    public static String ACTION_CLEAR = BuildConfig.APPLICATION_ID + ".ui_clear";
    public static String ACTION_HACK = BuildConfig.APPLICATION_ID + ".ui_hack";
    public static String ACTION_WEATHER = BuildConfig.APPLICATION_ID + ".ui_weather";
    public static String ACTION_WEATHER_GOT_LOCATION = BuildConfig.APPLICATION_ID + ".ui_weather_location";
    public static String ACTION_WEATHER_DELAY = BuildConfig.APPLICATION_ID + ".ui_weather_delay";
    public static String ACTION_WEATHER_MANUAL_UPDATE = BuildConfig.APPLICATION_ID + ".ui_weather_update";

    public static final String ACTION_MUSIC_CHANGED = MusicService.ACTION_MUSIC_CHANGED;
    public static final String SONG_TITLE = MusicService.SONG_TITLE;
    public static final String SONG_SINGER = MusicService.SONG_SINGER;
    public static final String SONG_DURATION = MusicService.SONG_DURATION;
    public static final String SONG_POSITION = MusicService.SONG_POSITION;
    public static final String MUSIC_PLAYING = MusicService.MUSIC_PLAYING;
    public static final String ACTION_NOTIFICATION_FEED = NotificationService.ACTION_NOTIFICATION_FEED;
    public static final String EXTRA_NOTIFICATION_LIST = NotificationService.EXTRA_NOTIFICATION_LIST;
    public static final String ACTION_REQUEST_NOTIFICATION_FEED = NotificationService.ACTION_REQUEST_NOTIFICATION_FEED;
    public static final String ACTION_CLOCK_STATE = ClockManager.ACTION_CLOCK_STATE;
    public static final String ACTION_POMODORO_STATE = PomodoroManager.ACTION_POMODORO_STATE;
    public static final String ACTION_TERMUX_CONSOLE = BuildConfig.APPLICATION_ID + ".ui_termux_console";
    public static final String EXTRA_TERMUX_COMMAND = "termux_command";
    public static final String ACTION_FILE_CONSOLE = BuildConfig.APPLICATION_ID + ".ui_file_console";
    public static final String EXTRA_FILE_COMMAND = "file_command";
    public static final String ACTION_MODULE_COMMAND = BuildConfig.APPLICATION_ID + ".ui_module_command";
    public static final String EXTRA_MODULE_COMMAND = "module_command";
    public static final String EXTRA_MODULE_NAME = "module_name";
    public static final String EXTRA_WIDGET_ACTION_INDEX = "widget_action_index";
    public static final String EXTRA_WIDGET_ACTION_VALUE = "widget_action_value";
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND";
    private static final String TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService";
    private static final String TERMUX_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH";
    private static final String TERMUX_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS";
    private static final String TERMUX_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND";
    private static final String TERMUX_RUN_COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT";
    public static final String ACTION_TERMUX_RESULT = BuildConfig.APPLICATION_ID + ".ui_termux_result";
    public static final String EXTRA_TERMUX_RESULT_PATH = "termux_result_path";
    public static final String EXTRA_TERMUX_RESULT_STDOUT = "termux_result_stdout";
    public static final String EXTRA_TERMUX_RESULT_STDERR = "termux_result_stderr";
    public static final String EXTRA_TERMUX_RESULT_EXIT_CODE = "termux_result_exit_code";
    public static final String EXTRA_TERMUX_RESULT_ERROR = "termux_result_error";
    public static final String EXTRA_TERMUX_RESULT_DEBUG = "termux_result_debug";
    public static final String EXTRA_TERMUX_RESULT_MODULE = "termux_result_module";
    public static final String ACTION_NOTIFICATION_RECEIVED = BuildConfig.APPLICATION_ID + ".ui_notification_received";
    public static final String NOTIFICATION_TEXT = "notification_text";

    public static String FILE_NAME = "fileName";

    public enum Label {
        ram,
        device,
        time,
        battery,
        storage,
        network,
        notes,
        weather,
        unlock,
        ascii
    }

    private final int RAM_DELAY = 3000;
    private final int TIME_DELAY = 1000;
    private final int STORAGE_DELAY = 60 * 1000;

    private RamManager ramManager;
    private ohi.andre.consolelauncher.managers.status.BatteryManager batteryManager;
    private ohi.andre.consolelauncher.managers.status.StorageManager storageManager;
    private ohi.andre.consolelauncher.managers.status.NetworkManager networkManager;
    private ohi.andre.consolelauncher.managers.status.TimeManager tuiTimeManager;
    private ohi.andre.consolelauncher.managers.status.UnlockManager unlockManager;

    protected Context mContext;
    protected MainPack mainPack;

    private Handler handler;

    private DevicePolicyManager policy;
    private ComponentName component;
    private boolean swipeDownNotifications, swipeUpAppsDrawer;
    private GestureDetectorCompat gestureDetector;
    private View appsDrawerRoot;
    private ListView appsList;
    private LinearLayout appsGroupTabs;
    private LinearLayout appsAlphaTabs;
    private TextView appsDrawerHeader, appsDrawerFooter;
    private AppsDrawerAdapter appsDrawerAdapter;
    private final List<AppDrawerEntry> appsDrawerEntries = new ArrayList<>();
    private final LinkedHashMap<String, Integer> appsDrawerAlphaPositions = new LinkedHashMap<>();
    private final LinkedHashMap<String, TextView> appsDrawerAlphaViews = new LinkedHashMap<>();
    private final ArrayList<NotificationService.Notification> currentOverlayNotifications = new ArrayList<>();
    private int currentNotificationIndex = 0;
    private String notificationReplyFocusKey = null;
    private final StringBuilder termuxBuffer = new StringBuilder();
    private boolean notificationCompactForKeyboard = false;
    private boolean timerTabVisible = false;
    private boolean stopwatchTabVisible = false;
    private boolean timerTabDockReady = false;
    private boolean stopwatchTabDockReady = false;
    private boolean pomodoroOverlayVisible = false;
    public boolean isPomodoroOverlayVisible() {
        return pomodoroOverlayVisible;
    }
    private String selectedAppsDrawerGroup = null;
    private String selectedAppsDrawerAlpha = null;
    private View termuxOverlay;
    private View termuxWindowBorder;
    private TextView termuxWindowLabel;
    private TextView termuxClose;
    private TextView termuxOutput;
    private TextView termuxPrefix;
    private EditText termuxInput;
    private ScrollView termuxScroll;
    private View termuxInputGroup;
    private View termuxTools;
    private TextView termuxClear;
    private TextView termuxUp;
    private TextView termuxDown;
    private TextView termuxPaste;
    private View fileOverlay;
    private View fileWindowBorder;
    private TextView fileWindowLabel;
    private TextView fileClose;
    private TextView filePath;
    private TextView fileOutput;
    private TextView filePrefix;
    private EditText fileInput;
    private ScrollView fileScroll;
    private View fileInputGroup;
    private View fileTools;
    private TextView fileRefresh;
    private TextView fileUp;
    private TextView fileOpen;
    private TextView filePaste;
    private String lastFileListingPath = "";
    private View suggestionsContainer;
    private int suggestionsVisibilityBeforeTermux = View.VISIBLE;
    private boolean termuxConsoleOpen = false;
    private View terminalTrayContainer;
    private ViewGroup terminalContainer;
    private View terminalOutputBorder;
    private TextView terminalTrayToggle;
    private boolean terminalTrayExpanded = false;
    private static final String OUTPUT_TRAY_MODE_NATIVE = "native";
    private static final String OUTPUT_TRAY_MODE_AUTO = "auto";
    private static final String OUTPUT_TRAY_MODE_TOGGLED = "toggled";
    private static final String OUTPUT_HEADER_MODE_NORMAL = "normal";
    private static final String OUTPUT_HEADER_MODE_ARROWS = "arrows";
    private static final String OUTPUT_HEADER_MODE_NONE = "none";
    private static final long EVENTS_REFRESH_GRACE_MS = 1000;
    private static final long EVENTS_REFRESH_FALLBACK_MS = 60 * 1000;
    private boolean keyboardVisible = false;
    private boolean hasLastLayoutState = false;
    private int lastObservedRootHeight = -1;
    private View moduleDockScroll;
    private LinearLayout moduleDock;
    private final LinkedHashMap<String, TextView> moduleDockButtons = new LinkedHashMap<>();
    private String styledModuleDockSelection = null;
    private int pendingModuleDockScrollX = -1;
    private int lastModuleDockScrollX = 0;
    private final HashMap<String, LuaWidgetEngine> luaWidgetEngines = new HashMap<>();
    private boolean bundledLuaSamplesPruned = false;
    private String activeModule = "";
    private Intent lastClockStateIntent;
    private Intent lastPomodoroStateIntent;
    private String lastMusicSong;
    private String lastMusicSinger;
    private boolean lastMusicPlaying = false;

    SharedPreferences preferences;

    private InputMethodManager imm;
    private TerminalManager mTerminalAdapter;
    private List<String> pendingInputs = new ArrayList<>();
    private static class OutputHolder {
        CharSequence output;
        int category;
        Integer color;

        OutputHolder(CharSequence output, int category) {
            this.output = output;
            this.category = category;
        }

        OutputHolder(int color, CharSequence output) {
            this.color = color;
            this.output = output;
        }
    }
    private List<OutputHolder> pendingOutputs = new ArrayList<>();
    int mediumPercentage, lowPercentage;
    String batteryFormat;

    boolean hideToolbarNoInput;
    View toolbarView;

    //    never access this directly, use getLabelView
    private TextView[] labelViews = new TextView[Label.values().length];

    private float[] labelIndexes = new float[labelViews.length];
    private int[] labelSizes = new int[labelViews.length];
    private CharSequence[] labelTexts = new CharSequence[labelViews.length];

    private String asciiContent = null;
    private int asciiColor;

    private final StatusUpdateListener statusUpdateListener = this::updateText;

    private TextView getLabelView(Label l) {
        int index = (int) labelIndexes[l.ordinal()];
        if (index < 0 || index >= labelViews.length) {
            return null;
        }
        return labelViews[index];
    }

    private int notesMaxLines;
    private ohi.andre.consolelauncher.managers.status.NotesManager tuiNotesManager;
    private NotesManager notesManager;
//    private NotesRunnable notesRunnable;

    private String activeMusicSource = "internal";

    private final Runnable musicTimeRunnable = new Runnable() {
        @Override
        public void run() {
            boolean shouldContinue = false;
            if ("internal".equals(activeMusicSource)) {
                View musicWidget = mRootView.findViewById(R.id.music_widget);
                if (musicWidget != null && musicWidget.getVisibility() == View.VISIBLE) {
                    if (mainPack != null && mainPack.player != null && mainPack.player.isPlaying()) {
                        shouldContinue = true;
                        Intent intent = new Intent(ACTION_MUSIC_CHANGED);
                        int index = mainPack.player.getSongIndex();
                        if (index != -1) {
                            ohi.andre.consolelauncher.managers.music.Song song = mainPack.player.get(index);
                            if (song != null) {
                                intent.putExtra(SONG_TITLE, song.getTitle());
                                intent.putExtra(SONG_SINGER, song.getSinger());
                            }
                        }
                        intent.putExtra(SONG_DURATION, mainPack.player.getDuration());
                        intent.putExtra(SONG_POSITION, mainPack.player.getCurrentPosition());
                        intent.putExtra(MUSIC_PLAYING, mainPack.player.isPlaying());
                        intent.putExtra("source", "internal");
                        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                    }
                }
            }
            if (shouldContinue) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable eventsRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!ModuleManager.EVENTS.equals(activeModule)) {
                return;
            }
            String source = ModuleManager.getModuleSource(mContext, ModuleManager.EVENTS);
            if (!ModuleManager.isLauncherSource(source)) {
                return;
            }
            refreshLauncherModule(ModuleManager.EVENTS, source, false);
            scheduleEventsRefreshIfNeeded();
        }
    };
    private final Runnable luaWidgetTickRunnable = new Runnable() {
        @Override
        public void run() {
            tickActiveLuaWidget();
        }
    };

    private final Runnable fontRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshLauncherTypeface();
        }
    };

    private final Runnable hackHideRunnable = new Runnable() {
        @Override
        public void run() {
            View overlay = mRootView.findViewById(R.id.hack_overlay);
            if (overlay != null) {
                overlay.animate().cancel();
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
            }
        }
    };

    private final String[] hackLines = new String[] {
            "$ ./breach --target=localhost --mode=theatrical",
            "[BOOT] attaching remote shell...",
            "[BOOT] syncing fake intrusion assets...",
            "[AUTH] replaying cached credentials...",
            "[AUTH] probing token vault A1...",
            "[AUTH] probing token vault A2...",
            "[AUTH] probing token vault A3...",
            "[TRACE] walking local package graph...",
            "[TRACE] reading launcher aliases...",
            "[TRACE] reading launcher contacts...",
            "[TRACE] reading launcher app groups...",
            "[MEM ] dumping volatile session tokens...",
            "[MEM ] scanning keyboard buffer...",
            "[MEM ] scanning clipboard buffer...",
            "[NET ] tunneling through relay-07...",
            "[NET ] tunneling through relay-11...",
            "[NET ] handshaking with mirror node...",
            "[PROC] escalating pseudo-root privileges...",
            "[PROC] masking shell signature...",
            "[PROC] detaching watchdog threads...",
            "[I/O ] indexing aliases, apps, contacts...",
            "[I/O ] reading wallpaper palette cache...",
            "[I/O ] reading notification mirror...",
            "[CRYP] brute forcing theme entropy...",
            "[CRYP] brute forcing dashed border seed...",
            "[CRYP] deriving surface accent offsets...",
            "[SYNC] mirroring notification buffer...",
            "[SYNC] mirroring playback metadata...",
            "[SYNC] mirroring quick launch slots...",
            "[WARN] firewall politely ignored",
            "[WARN] device insists everything is fine",
            "[MESH] propagating into nearby terminals...",
            "[MESH] seeding ghost sessions...",
            "[MESH] flooding loopback channel...",
            "[DB  ] harvesting battery telemetry...",
            "[DB  ] harvesting session hints...",
            "[DB  ] harvesting stale command history...",
            "[VID ] spoofing viewport overlays...",
            "[VID ] injecting terminal rain...",
            "[VID ] pinning cinematic contrast...",
            "[AUX ] scrambling keyboard handshake...",
            "[AUX ] bouncing cursor driver...",
            "[AUX ] destabilizing glyph cache...",
            "[FS  ] mounting /storage/emulated/0/Re-T-UI",
            "[FS  ] enumerating ui.xml",
            "[FS  ] enumerating theme.xml",
            "[FS  ] enumerating suggestions.xml",
            "[FS  ] enumerating behavior.xml",
            "[MOD ] patching fake subsystem: notifications",
            "[MOD ] patching fake subsystem: music",
            "[MOD ] patching fake subsystem: wallpaper",
            "[MOD ] patching fake subsystem: battery",
            "[PING] 127.0.0.1 replied in 0ms",
            "[PING] 127.0.0.1 replied in 0ms",
            "[PING] 127.0.0.1 replied in 0ms",
            "[SCAN] port 22 open",
            "[SCAN] port 80 filtered",
            "[SCAN] port 443 open",
            "[SCAN] port 1337 aesthetically required",
            "[SEED] generating panic checksum 8f-2c-91",
            "[SEED] generating panic checksum 8f-2c-92",
            "[SEED] generating panic checksum 8f-2c-93",
            "[PIPE] rerouting stdout to dramatic overlay...",
            "[PIPE] rerouting stderr to dramatic overlay...",
            "[PIPE] rerouting common sense to /dev/null",
            "[OVRD] replacing launcher calmness with urgency",
            "[OVRD] amplifying green phosphor output",
            "[OVRD] preserving user music widget because priorities",
            "[HOOK] intercepting idle state...",
            "[HOOK] intercepting wallpaper refresh...",
            "[HOOK] intercepting harmless command execution...",
            "[TASK] assembling unauthorized vibes...",
            "[TASK] replaying synthetic intrusion frames...",
            "[TASK] marking sequence irreversible...",
            "[TASK] sequence actually reversible",
            "[LOCK] pretending to lock subsystems...",
            "[LOCK] pretending to exfiltrate secrets...",
            "[LOCK] pretending to know what any of this means...",
            "[NULL] dereferencing cinematic stakes...",
            "[NULL] recovering from fake catastrophe...",
            "[DONE] dramatic effect complete"
    };
    private final ArrayList<Runnable> hackSequenceRunnables = new ArrayList<>();

    private int weatherDelay;

    private double lastLatitude, lastLongitude;
    private String location;
    private boolean fixedLocation = false;

    private int weatherColor;
    boolean showWeatherUpdate;
    private ohi.andre.consolelauncher.managers.status.WeatherManager weatherManager;

//    you need to use labelIndexes[i]
    private void updateText(Label l, CharSequence s) {
        labelTexts[l.ordinal()] = s;

        int base = (int) labelIndexes[l.ordinal()];

        List<Float> indexs = new ArrayList<>();
        for(int count = 0; count < Label.values().length; count++) {
            if((int) labelIndexes[count] == base && labelTexts[count] != null) indexs.add(labelIndexes[count]);
        }
//        now I'm sorting the labels on the same line for decimals (2.1, 2.0, ...)
        Collections.sort(indexs);

        CharSequence sequence = Tuils.EMPTYSTRING;

        for(int c = 0; c < indexs.size(); c++) {
            float i = indexs.get(c);

            for(int a = 0; a < Label.values().length; a++) {
                if(i == labelIndexes[a] && labelTexts[a] != null) sequence = TextUtils.concat(sequence, labelTexts[a]);
            }
        }

        if(sequence.length() == 0) labelViews[base].setVisibility(View.GONE);
        else {
            labelViews[base].setVisibility(View.VISIBLE);
            labelViews[base].setText(sequence);
        }
    }

    private class PagerAdapter extends RecyclerView.Adapter<PagerAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View view;
            view = inflater.inflate(R.layout.home_widgets_page, parent, false);
            setupHomeWidgetsPage(view);
            // ViewPager2 requires match_parent for its children
            view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Home module surfaces are created when the single pager page is inflated.
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }

    private SuggestionsManager suggestionsManager;

    private TextView terminalView;

    private String doubleTapCmd;
    private boolean lockOnDbTap;

    private BroadcastReceiver receiver;

    public MainPack pack;

    private boolean clearOnLock;

    private final View mRootView;

    private ViewPager2 viewPager;
    private ViewGroup homeWidgetsContainer;
    private View mainContainer;
    private View landscapeSplitContainer;
    private ViewGroup landscapeLeftPane;
    private ViewGroup landscapeRightPane;
    private FrameLayout.LayoutParams portraitMainParams;
    private FrameLayout.LayoutParams portraitTrayParams;
    private boolean landscapeLayoutActive = false;
    private int imeBottomOffset = 0;

    private int strokeWidth, cornerRadius;
    private String[] bgRectColors;
    private String[] bgColors;
    private String[] outlineColors;
    private int shadowXOffset, shadowYOffset;
    private float shadowRadius;
    private boolean useDashed;
    private int[][] margins;

    private final int INPUT_BGCOLOR_INDEX = 10;
    private final int OUTPUT_BGCOLOR_INDEX = 11;
    private final int SUGGESTIONS_BGCOLOR_INDEX = 12;
    private final int TOOLBAR_BGCOLOR_INDEX = 13;

    private final int OUTPUT_MARGINS_INDEX = 1;
    private final int INPUTAREA_MARGINS_INDEX = 2;
    private final int INPUTFIELD_MARGINS_INDEX = 3;
    private final int TOOLBAR_MARGINS_INDEX = 4;
    private final int SUGGESTIONS_MARGINS_INDEX = 5;

    private CommandExecuter mExecuter;

    private void setupTerminalPage(View terminalPage) {
        terminalTrayContainer = mRootView.findViewById(R.id.terminal_tray_container);
        if (terminalTrayContainer != null && portraitTrayParams == null
                && terminalTrayContainer.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            portraitTrayParams = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) terminalTrayContainer.getLayoutParams());
        }
        terminalContainer = terminalPage.findViewById(R.id.terminal_container);
        terminalOutputBorder = terminalPage.findViewById(R.id.terminal_output_border);
        terminalTrayToggle = terminalPage.findViewById(R.id.terminal_tray_toggle);

        terminalView = (TextView) terminalPage.findViewById(R.id.terminal_view);
        terminalView.setOnTouchListener(this);
        ((View) terminalView.getParent().getParent()).setOnTouchListener(this);

        applyBgRect(mContext, terminalOutputBorder, bgRectColors[OUTPUT_BGCOLOR_INDEX], bgColors[OUTPUT_BGCOLOR_INDEX], margins[OUTPUT_MARGINS_INDEX], strokeWidth, (int) Tuils.dpToPx(mContext, AppearanceSettings.outputCornerRadius()), useDashed, XMLPrefsManager.getColor(Theme.output_color));
        terminalView.setBackgroundColor(Color.TRANSPARENT);
        terminalView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (terminalContainer != null) {
                    terminalContainer.post(() -> applyTerminalTrayState(false));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        applyShadow(terminalView, outlineColors[OUTPUT_BGCOLOR_INDEX], shadowXOffset, shadowYOffset, shadowRadius);
        restoreTerminalTrayState();
        styleTerminalTrayToggle();
        applyTerminalTrayState(false);

        final EditText inputView = (EditText) mRootView.findViewById(R.id.input_view);
        TextView prefixView = (TextView) mRootView.findViewById(R.id.prefix_view);
        inputView.setCursorVisible(false);
        inputView.setShowSoftInputOnFocus(false);
        if (inputView instanceof OutlineEditText) {
            OutlineEditText outlineInput = (OutlineEditText) inputView;
            outlineInput.setIdleCursorColor(XMLPrefsManager.getColor(Theme.cursor_color));
            outlineInput.setIdleCursorVisible(true);
        }
        inputView.setOnClickListener(v -> {
            if (inputView instanceof OutlineEditText) {
                ((OutlineEditText) inputView).setIdleCursorVisible(false);
            }
            inputView.setCursorVisible(true);
            inputView.setShowSoftInputOnFocus(true);
            inputView.requestFocus();
            imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
        });

        applyBgRect(mContext, mRootView.findViewById(R.id.input_group), bgRectColors[INPUT_BGCOLOR_INDEX], bgColors[INPUT_BGCOLOR_INDEX], margins[INPUTAREA_MARGINS_INDEX], strokeWidth, cornerRadius, useDashed, XMLPrefsManager.getColor(Theme.input_color));
        applyShadow(inputView, outlineColors[INPUT_BGCOLOR_INDEX], shadowXOffset, shadowYOffset, shadowRadius);
        applyShadow(prefixView, outlineColors[INPUT_BGCOLOR_INDEX], shadowXOffset, shadowYOffset, shadowRadius);

        applyMargins(inputView, margins[INPUTFIELD_MARGINS_INDEX]);
        applyMargins(prefixView, margins[INPUTFIELD_MARGINS_INDEX]);

        ImageView submitView = (ImageView) mRootView.findViewById(R.id.submit_tv);
        boolean showSubmit = XMLPrefsManager.getBoolean(Ui.show_enter_button);
        if (!showSubmit) {
            submitView.setVisibility(View.GONE);
            submitView = null;
        }

        boolean showToolbar = XMLPrefsManager.getBoolean(Toolbar.show_toolbar);
        ImageButton backView = null;
        ImageButton nextView = null;
        ImageButton deleteView = null;
        ImageButton pasteView = null;
        ImageButton appDrawerView = null;

        if(!showToolbar) {
            mRootView.findViewById(R.id.tools_view).setVisibility(View.GONE);
            toolbarView = null;
        } else {
            backView = (ImageButton) mRootView.findViewById(R.id.back_view);
            nextView = (ImageButton) mRootView.findViewById(R.id.next_view);
            deleteView = (ImageButton) mRootView.findViewById(R.id.delete_view);
            pasteView = (ImageButton) mRootView.findViewById(R.id.paste_view);
            appDrawerView = (ImageButton) mRootView.findViewById(R.id.app_drawer_view);

            toolbarView = mRootView.findViewById(R.id.tools_view);
            hideToolbarNoInput = XMLPrefsManager.getBoolean(Toolbar.hide_toolbar_no_input);

            applyBgRect(mContext, toolbarView, bgRectColors[TOOLBAR_BGCOLOR_INDEX], bgColors[TOOLBAR_BGCOLOR_INDEX], margins[TOOLBAR_MARGINS_INDEX], strokeWidth, cornerRadius, useDashed, XMLPrefsManager.getColor(Theme.toolbar_color));

            if (appDrawerView != null) {
                if (XMLPrefsManager.getBoolean(Behavior.swipe_up_apps_drawer)) {
                    appDrawerView.setVisibility(View.VISIBLE);
                    appDrawerView.setOnClickListener(v -> showAppsDrawer());
                } else {
                    appDrawerView.setVisibility(View.GONE);
                }
            }
        }

        mTerminalAdapter = new TerminalManager(terminalView, inputView, prefixView, submitView, backView, nextView, deleteView, pasteView, mContext, mainPack, mExecuter);

        for (String s : pendingInputs) {
            mTerminalAdapter.setInput(s);
        }
        pendingInputs.clear();

        for (OutputHolder oh : pendingOutputs) {
            if (oh.color != null) {
                mTerminalAdapter.setOutput(oh.color, oh.output);
            } else {
                mTerminalAdapter.setOutput(oh.output, oh.category);
            }
        }
        pendingOutputs.clear();

        mTerminalAdapter.focusInputEnd();

        if (XMLPrefsManager.getBoolean(Suggestions.show_suggestions)) {
            HorizontalScrollView sv = (HorizontalScrollView) mRootView.findViewById(R.id.suggestions_container);
            if (sv != null) {
                sv.setFocusable(false);
                sv.setOnFocusChangeListener((v, hasFocus) -> {
                    if (hasFocus) {
                        v.clearFocus();
                    }
                });
                applyBgRect(mContext, sv, bgRectColors[SUGGESTIONS_BGCOLOR_INDEX], bgColors[SUGGESTIONS_BGCOLOR_INDEX], margins[SUGGESTIONS_MARGINS_INDEX], strokeWidth, cornerRadius, useDashed, XMLPrefsManager.getColor(Theme.suggestions_bgrectcolor));

                LinearLayout suggestionsView = (LinearLayout) mRootView.findViewById(R.id.suggestions_group);
                suggestionsManager = new SuggestionsManager(suggestionsView, mainPack, mTerminalAdapter);

                inputView.addTextChangedListener(new SuggestionTextWatcher(suggestionsManager, (currentText, before) -> {
                    if (!hideToolbarNoInput) return;

                    if (currentText.length() == 0) toolbarView.setVisibility(View.GONE);
                    else if (before == 0) toolbarView.setVisibility(View.VISIBLE);
                }));
            }
        } else {
            View sugGroup = mRootView.findViewById(R.id.suggestions_group);
            if (sugGroup != null) sugGroup.setVisibility(View.GONE);
        }

        
        scheduleTypefaceRefreshes();
    }

    private void setupResponsiveLandscapeLayout(ViewGroup rootView) {
        mainContainer = rootView.findViewById(R.id.main_container);
        landscapeSplitContainer = rootView.findViewById(R.id.landscape_split_container);
        landscapeLeftPane = rootView.findViewById(R.id.landscape_left_pane);
        landscapeRightPane = rootView.findViewById(R.id.landscape_right_pane);

        if (mainContainer != null && portraitMainParams == null
                && mainContainer.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            portraitMainParams = new FrameLayout.LayoutParams((FrameLayout.LayoutParams) mainContainer.getLayoutParams());
        }
    }

    private void applyResponsiveLandscapeLayout(Configuration configuration) {
        applyDisplayMarginsForConfiguration(configuration);

        if (mainContainer == null || terminalTrayContainer == null || landscapeSplitContainer == null
                || landscapeLeftPane == null || landscapeRightPane == null || !(mRootView instanceof ViewGroup)) {
            return;
        }

        boolean shouldUseLandscape = configuration != null
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (shouldUseLandscape == landscapeLayoutActive) {
            applyLandscapeStatusChrome(shouldUseLandscape);
            applyTerminalTrayState(false);
            return;
        }

        if (shouldUseLandscape) {
            activateLandscapeLayout();
        } else {
            restorePortraitLayout();
        }
        applyLandscapeStatusChrome(shouldUseLandscape);
        applyTerminalTrayState(false);
    }

    private void applyLandscapeStatusChrome(boolean landscape) {
        TextView asciiView = getLabelView(Label.ascii);
        if (asciiView == null) {
            return;
        }
        if (landscape) {
            asciiView.setVisibility(View.GONE);
        } else if (!TextUtils.isEmpty(asciiView.getText())) {
            asciiView.setVisibility(View.VISIBLE);
        }
    }

    private void activateLandscapeLayout() {
        ViewGroup root = (ViewGroup) mRootView;
        detachFromParent(mainContainer);
        detachFromParent(terminalTrayContainer);

        landscapeLayoutActive = true;
        landscapeSplitContainer.setVisibility(View.VISIBLE);

        landscapeLeftPane.addView(mainContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        landscapeRightPane.addView(terminalTrayContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (root.indexOfChild(landscapeSplitContainer) < 0) {
            root.addView(landscapeSplitContainer, 0);
        }
    }

    private void restorePortraitLayout() {
        ViewGroup root = (ViewGroup) mRootView;
        detachFromParent(mainContainer);
        detachFromParent(terminalTrayContainer);

        landscapeLayoutActive = false;
        landscapeSplitContainer.setVisibility(View.GONE);

        FrameLayout.LayoutParams mainParams = portraitMainParams != null
                ? new FrameLayout.LayoutParams(portraitMainParams)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        FrameLayout.LayoutParams trayParams = portraitTrayParams != null
                ? new FrameLayout.LayoutParams(portraitTrayParams)
                : new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);

        root.addView(mainContainer, 0, mainParams);
        root.addView(terminalTrayContainer, Math.min(1, root.getChildCount()), trayParams);
    }

    private void detachFromParent(View view) {
        if (view == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
    }

    public void applyImeBottomOffset(int keyboardOffset) {
        imeBottomOffset = Math.max(0, keyboardOffset);
        applyDisplayMarginsForConfiguration(mContext != null ? mContext.getResources().getConfiguration() : null);
    }

    public void refreshDisplayMargins() {
        applyDisplayMarginsForConfiguration(mContext != null ? mContext.getResources().getConfiguration() : null);
    }

    private void applyDisplayMarginsForConfiguration(Configuration configuration) {
        if (mRootView == null || mContext == null) {
            return;
        }

        boolean landscape = configuration != null
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE;
        int[] displayMargins = getListOfIntValues(
                XMLPrefsManager.get(landscape ? Ui.display_margin_landscape_mm : Ui.display_margin_mm),
                4,
                0);
        DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        mRootView.setPadding(
                Tuils.mmToPx(metrics, displayMargins[0]),
                Tuils.mmToPx(metrics, displayMargins[1]),
                Tuils.mmToPx(metrics, displayMargins[2]),
                Tuils.mmToPx(metrics, displayMargins[3]) + imeBottomOffset);
    }

    private void styleTerminalTrayToggle() {
        if (terminalTrayToggle == null) {
            return;
        }
        if (isOutputHeaderNone()) {
            terminalTrayToggle.setVisibility(View.GONE);
            terminalTrayToggle.setOnClickListener(null);
            terminalTrayToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
            return;
        }

        terminalTrayToggle.setVisibility(View.VISIBLE);
        int outputColor = AppearanceSettings.moduleNameTextColor();
        terminalTrayToggle.setTextColor(outputColor);
        terminalTrayToggle.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        terminalTrayToggle.setTextSize(AppearanceSettings.outputHeaderTextSize());
        if (isOutputHeaderArrowsOnly()) {
            terminalTrayToggle.setMinWidth((int) Tuils.dpToPx(mContext, 48));
            terminalTrayToggle.setPadding(
                    (int) Tuils.dpToPx(mContext, 9),
                    (int) Tuils.dpToPx(mContext, 3),
                    (int) Tuils.dpToPx(mContext, 9),
                    (int) Tuils.dpToPx(mContext, 3));
        } else {
            terminalTrayToggle.setMinWidth((int) Tuils.dpToPx(mContext, 130));
            terminalTrayToggle.setPadding(
                    (int) Tuils.dpToPx(mContext, 12),
                    (int) Tuils.dpToPx(mContext, 2),
                    (int) Tuils.dpToPx(mContext, 12),
                    (int) Tuils.dpToPx(mContext, 2));
        }
        try {
            GradientDrawable gd = (GradientDrawable) androidx.core.content.res.ResourcesCompat.getDrawable(
                    mContext.getResources(), R.drawable.apps_drawer_header_border, null);
            if (gd != null) {
                gd = (GradientDrawable) gd.mutate();
                gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.headerCornerRadius()));
                if (AppearanceSettings.dashedBorders()) {
                    gd.setStroke((int) Tuils.dpToPx(mContext, 1.5f), outputColor,
                            Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                            Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
                } else {
                    gd.setStroke(0, Color.TRANSPARENT);
                }
                gd.setColor(resolveTerminalWindowBgColor(bgColors[OUTPUT_BGCOLOR_INDEX]));
                terminalTrayToggle.setBackground(gd);
            }
        } catch (Exception ignored) {}
        terminalTrayToggle.setOnClickListener(v -> {
            if (!landscapeLayoutActive && isOutputTrayToggledMode()) {
                setTerminalTrayExpanded(!terminalTrayExpanded);
            }
        });
        updateTerminalTrayToggleText();
    }

    private void setTerminalTrayExpanded(boolean expanded) {
        if (!isOutputTrayToggledMode()) {
            return;
        }
        terminalTrayExpanded = expanded;
        saveTerminalTrayState();
        applyTerminalTrayState(true);
    }

    private void restoreTerminalTrayState() {
        if (isOutputTrayToggledMode() && preferences != null) {
            terminalTrayExpanded = preferences.getBoolean(PREF_OUTPUT_TRAY_EXPANDED, false);
        } else if (isOutputTrayAutoMode()) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule);
        } else {
            terminalTrayExpanded = false;
            if (preferences != null) {
                preferences.edit().remove(PREF_OUTPUT_TRAY_EXPANDED).apply();
            }
        }
    }

    private void saveTerminalTrayState() {
        if (isOutputTrayToggledMode() && preferences != null) {
            preferences.edit().putBoolean(PREF_OUTPUT_TRAY_EXPANDED, terminalTrayExpanded).apply();
        }
    }

    private void applyTerminalTrayState(boolean refocusInput) {
        if (terminalContainer == null) {
            return;
        }

        if (landscapeLayoutActive) {
            ViewGroup.LayoutParams params = terminalContainer.getLayoutParams();
            if (params instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
                if (lp.height != 0 || lp.weight != 1f) {
                    lp.height = 0;
                    lp.weight = 1f;
                    terminalContainer.setLayoutParams(lp);
                }
            } else if (params != null) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                terminalContainer.setLayoutParams(params);
            }
            updateTerminalTrayToggleText();
            if (refocusInput && mTerminalAdapter != null) {
                mTerminalAdapter.requestInputFocus();
                mTerminalAdapter.scrollToEnd();
            }
            return;
        }

        int rootHeight = mRootView != null ? mRootView.getHeight() : 0;
        int collapsedHeight = calculateCollapsedTerminalTrayHeight();
        int expandedHeight = calculateExpandedTerminalTrayHeight(rootHeight, collapsedHeight);

        ViewGroup.LayoutParams params = terminalContainer.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) params).weight = 0f;
        }
        int targetHeight;
        if (isOutputTrayNativeMode()) {
            targetHeight = calculateNativeTerminalTrayHeight(expandedHeight);
        } else if (isOutputTrayAutoMode()) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule);
            targetHeight = terminalTrayExpanded ? expandedHeight : collapsedHeight;
        } else {
            targetHeight = terminalTrayExpanded ? expandedHeight : collapsedHeight;
        }
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight;
            terminalContainer.setLayoutParams(params);
        }

        updateTerminalTrayToggleText();
        if (terminalTrayExpanded && refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter.scrollToEnd();
        }
        if (refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter.requestInputFocus();
        }
    }

    private int calculateExpandedTerminalTrayHeight(int rootHeight, int collapsedHeight) {
        int expandedHeight;
        if (rootHeight <= 0) {
            expandedHeight = Math.max(collapsedHeight, UIUtils.dpToPx(mContext, keyboardVisible ? 220 : 320));
        } else {
            float trayPercent = keyboardVisible ? 0.34f : 0.48f;
            expandedHeight = Math.max(collapsedHeight, Math.round(rootHeight * trayPercent));
        }
        return applyTerminalTrayMaxHeight(expandedHeight, collapsedHeight);
    }

    private int applyTerminalTrayMaxHeight(int expandedHeight, int collapsedHeight) {
        int maxHeightDp = AppearanceSettings.outputTrayMaxHeightDp();
        if (maxHeightDp <= 0) {
            return expandedHeight;
        }
        int maxHeight = UIUtils.dpToPx(mContext, maxHeightDp);
        return Math.max(collapsedHeight, Math.min(expandedHeight, maxHeight));
    }

    private void updateTerminalTrayToggleText() {
        if (terminalTrayToggle != null) {
            if (isOutputHeaderNone()) {
                terminalTrayToggle.setText("");
                terminalTrayToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                terminalTrayToggle.setVisibility(View.GONE);
                return;
            }
            terminalTrayToggle.setVisibility(View.VISIBLE);

            if (landscapeLayoutActive) {
                terminalTrayToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
                if (!TextUtils.equals(terminalTrayToggle.getText(), "OUTPUT")) {
                    terminalTrayToggle.setText("OUTPUT");
                }
                return;
            }

            boolean collapsed = !terminalTrayExpanded;
            if (isOutputHeaderArrowsOnly()) {
                terminalTrayToggle.setText("");
                terminalTrayToggle.setCompoundDrawablePadding(0);
                Drawable arrow = outputHeaderArrow(collapsed);
                terminalTrayToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, arrow, null, null);
                return;
            }

            terminalTrayToggle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
            String text;
            if (isOutputTrayNativeMode()) {
                text = "OUTPUT";
            } else if (isOutputTrayAutoMode()) {
                text = terminalTrayExpanded ? "OUTPUT AUTO v" : "OUTPUT AUTO ^";
            } else {
                text = terminalTrayExpanded ? "OUTPUT v" : "OUTPUT ^";
            }
            if (!TextUtils.equals(terminalTrayToggle.getText(), text)) {
                terminalTrayToggle.setText(text);
            }
        }
    }

    private boolean isOutputTrayNativeMode() {
        return OUTPUT_TRAY_MODE_NATIVE.equals(outputTrayMode());
    }

    private boolean isOutputTrayAutoMode() {
        return OUTPUT_TRAY_MODE_AUTO.equals(outputTrayMode());
    }

    private boolean isOutputTrayToggledMode() {
        return OUTPUT_TRAY_MODE_TOGGLED.equals(outputTrayMode());
    }

    private boolean isOutputHeaderArrowsOnly() {
        return OUTPUT_HEADER_MODE_ARROWS.equals(outputHeaderMode());
    }

    private boolean isOutputHeaderNone() {
        return OUTPUT_HEADER_MODE_NONE.equals(outputHeaderMode());
    }

    private String outputHeaderMode() {
        return AppearanceSettings.outputHeaderMode();
    }

    private Drawable outputHeaderArrow(boolean collapsed) {
        Drawable drawable = ContextCompat.getDrawable(mContext,
                collapsed ? R.drawable.ic_chevron_up : R.drawable.ic_chevron_down);
        if (drawable == null) {
            return null;
        }
        drawable = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(drawable, AppearanceSettings.moduleNameTextColor());
        int size = (int) Tuils.dpToPx(mContext, 18);
        drawable.setBounds(0, 0, size, size);
        return drawable;
    }

    private String outputTrayMode() {
        String mode = XMLPrefsManager.get(Behavior.output_tray_mode);
        if (mode != null) {
            mode = mode.trim().toLowerCase(java.util.Locale.US);
        }
        if (OUTPUT_TRAY_MODE_TOGGLED.equals(mode) && isOutputHeaderNone()) {
            return OUTPUT_TRAY_MODE_NATIVE;
        }
        if (OUTPUT_TRAY_MODE_AUTO.equals(mode)
                || OUTPUT_TRAY_MODE_TOGGLED.equals(mode)
                || OUTPUT_TRAY_MODE_NATIVE.equals(mode)) {
            return mode;
        }
        if (!isOutputHeaderNone() && XMLPrefsManager.getBoolean(Behavior.toggle_output_state)) {
            return OUTPUT_TRAY_MODE_TOGGLED;
        }
        return OUTPUT_TRAY_MODE_NATIVE;
    }

    private int calculateCollapsedTerminalTrayHeight() {
        int minHeight = UIUtils.dpToPx(mContext, 66);
        int maxHeight = UIUtils.dpToPx(mContext, keyboardVisible ? 96 : 132);
        if (terminalView == null || TextUtils.isEmpty(terminalView.getText())) {
            return minHeight;
        }

        int lineCount = Math.max(1, terminalView.getLineCount());
        if (lineCount <= 0) {
            lineCount = terminalView.getText().toString().split("\\n", -1).length;
        }
        int contentHeight = (lineCount * Math.max(terminalView.getLineHeight(), UIUtils.dpToPx(mContext, 18)))
                + UIUtils.dpToPx(mContext, 38);
        return Math.max(minHeight, Math.min(maxHeight, contentHeight));
    }

    private int calculateNativeTerminalTrayHeight(int maxHeight) {
        int minHeight = UIUtils.dpToPx(mContext, 66);
        if (terminalView == null || TextUtils.isEmpty(terminalView.getText())) {
            return minHeight;
        }

        int lineCount = Math.max(1, terminalView.getLineCount());
        if (lineCount <= 0) {
            lineCount = terminalView.getText().toString().split("\\n", -1).length;
        }
        int contentHeight = (lineCount * Math.max(terminalView.getLineHeight(), UIUtils.dpToPx(mContext, 18)))
                + UIUtils.dpToPx(mContext, 38);
        return Math.max(minHeight, Math.min(maxHeight, contentHeight));
    }

    private int resolveTerminalWindowBgColor(String bgColor) {
        try {
            int color = Color.parseColor(bgColor);
            if (color != Color.TRANSPARENT) {
                return color;
            }
        } catch (Exception ignored) {}
        return AppearanceSettings.terminalWindowBackground();
    }

    private void setupHomeWidgetsPage(View homePage) {
        moduleDockScroll = homePage.findViewById(R.id.module_dock_scroll);
        if (moduleDockScroll != null) {
            moduleDockScroll.getViewTreeObserver().addOnScrollChangedListener(() -> {
                int scrollX = currentModuleDockScrollX();
                if (scrollX > 0) {
                    lastModuleDockScrollX = scrollX;
                }
            });
        }
        moduleDock = homePage.findViewById(R.id.module_dock);
        homeWidgetsContainer = homePage.findViewById(R.id.home_widgets_container);
        if (homeWidgetsContainer == null) return;

        ensureSystemLuaModules();
        pruneBundledLuaSamples();
        activeModule = "";
        ModuleManager.setActiveModule(mContext, "");
        homeWidgetsContainer.removeAllViews();
        rebuildModuleDock();
        refreshSuggestionsForActiveModule();
    }

    private void pruneBundledLuaSamples() {
        if (bundledLuaSamplesPruned || mContext == null) return;
        bundledLuaSamplesPruned = true;
        ArrayList<String> ids = new ArrayList<>(LuaWidgetManager.bundledSampleIds());
        for (String id : LuaWidgetManager.listIds()) {
            if (LuaWidgetManager.isBundledSample(id) && !ids.contains(id)) {
                ids.add(id);
            }
        }
        for (String id : ids) {
            LuaWidgetManager.delete(id);
            ModuleManager.removeScriptModule(mContext, id);
            luaWidgetEngines.remove(id);
        }
    }

    private void rebuildModuleDock() {
        pruneBundledLuaSamples();
        boolean showDock = LauncherSettings.getBoolean(Behavior.show_module_dock);
        if (moduleDockScroll != null) {
            moduleDockScroll.setVisibility(showDock ? View.VISIBLE : View.GONE);
        }
        if (moduleDock == null) return;

        moduleDock.removeAllViews();
        moduleDockButtons.clear();
        styledModuleDockSelection = null;
        if (!showDock) return;

        for (String module : ModuleManager.getDock(mContext)) {
            addModuleDockButton(module);
        }
        addModuleDockButton("close");
        restyleAllModuleDockButtons();
    }

    private void addModuleDockButton(String module) {
        TextView button = new TextView(mContext);
        button.setText("close".equals(module) ? "X" : ModuleManager.displayTitle(mContext, module));
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        int padX = (int) Tuils.dpToPx(mContext, "close".equals(module) ? 14 : 16);
        int padY = (int) Tuils.dpToPx(mContext, 7);
        button.setPadding(padX, padY, padX, padY);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) Tuils.dpToPx(mContext, 8), 0);
        button.setLayoutParams(lp);

        button.setOnClickListener(v -> {
            if ("close".equals(module)) closeHomeModule();
            else showHomeModule(module);
        });
        button.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                int scrollX = preservedModuleDockScrollX();
                pendingModuleDockScrollX = scrollX > 0 ? scrollX : lastModuleDockScrollX;
            }
            return false;
        });

        moduleDock.addView(button);
        moduleDockButtons.put(module, button);
    }

    private void styleModuleDockButton(TextView button, boolean selected) {
        int borderColor = AppearanceSettings.moduleButtonBorderColor();
        int textColor = AppearanceSettings.moduleNameTextColor();
        int bg = selected
                ? ColorUtils.blendARGB(AppearanceSettings.moduleButtonBackgroundColor(), textColor, 0.25f)
                : AppearanceSettings.moduleButtonBackgroundColor();

        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.moduleCornerRadius()));
        gd.setColor(bg);
        if (AppearanceSettings.dashedBorders()) {
            gd.setStroke((int) Tuils.dpToPx(mContext, 1.2f), borderColor,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        button.setTextColor(textColor);
        button.setBackground(gd);
    }

    private void updateModuleDockSelection() {
        if (styledModuleDockSelection == null) {
            for (Map.Entry<String, TextView> entry : moduleDockButtons.entrySet()) {
                styleModuleDockButton(entry.getValue(), entry.getKey().equals(activeModule));
            }
            styledModuleDockSelection = activeModule;
            return;
        }
        if (TextUtils.equals(styledModuleDockSelection, activeModule)) {
            return;
        }
        TextView previous = moduleDockButtons.get(styledModuleDockSelection);
        if (previous != null) {
            styleModuleDockButton(previous, false);
        }
        TextView current = moduleDockButtons.get(activeModule);
        if (current != null) {
            styleModuleDockButton(current, true);
        }
        styledModuleDockSelection = activeModule;
    }

    private void restyleAllModuleDockButtons() {
        styledModuleDockSelection = null;
        for (Map.Entry<String, TextView> entry : moduleDockButtons.entrySet()) {
            styleModuleDockButton(entry.getValue(), entry.getKey().equals(activeModule));
        }
        styledModuleDockSelection = activeModule;
    }

    private int currentModuleDockScrollX() {
        if (moduleDockScroll instanceof HorizontalScrollView) {
            return ((HorizontalScrollView) moduleDockScroll).getScrollX();
        }
        return 0;
    }

    private int preservedModuleDockScrollX() {
        if (moduleDockScroll instanceof StableHorizontalScrollView) {
            return ((StableHorizontalScrollView) moduleDockScroll).getPreservedScrollX();
        }
        return currentModuleDockScrollX();
    }

    private int consumeModuleDockScrollX() {
        int scrollX = pendingModuleDockScrollX >= 0 ? pendingModuleDockScrollX : preservedModuleDockScrollX();
        pendingModuleDockScrollX = -1;
        return scrollX;
    }

    private void preserveModuleDockScrollX(int scrollX) {
        if (scrollX > 0) {
            lastModuleDockScrollX = scrollX;
        }
        if (moduleDockScroll instanceof StableHorizontalScrollView) {
            ((StableHorizontalScrollView) moduleDockScroll).preserveScrollX(scrollX);
        } else if (moduleDockScroll instanceof HorizontalScrollView) {
            HorizontalScrollView scroll = (HorizontalScrollView) moduleDockScroll;
            int target = Math.max(0, scrollX);
            scroll.scrollTo(target, 0);
            scroll.post(() -> scroll.scrollTo(target, 0));
        }
    }

    private String chooseDefaultModule() {
        List<String> dock = ModuleManager.getDock(mContext);
        if (dock.contains(ModuleManager.NOTIFICATIONS) && NotificationSettings.showTerminal()) {
            return ModuleManager.NOTIFICATIONS;
        }
        if (dock.contains(ModuleManager.MUSIC) && MusicSettings.showWidget()) {
            return ModuleManager.MUSIC;
        }
        return dock.isEmpty() ? ModuleManager.TIMER : dock.get(0);
    }

    private void showHomeModule(String module) {
        if (homeWidgetsContainer == null) return;

        String id = ModuleManager.normalize(module);
        if (!ModuleManager.isKnown(mContext, id)) {
            return;
        }

        int dockScrollX = consumeModuleDockScrollX();
        if (handler != null) {
            handler.removeCallbacks(eventsRefreshRunnable);
            handler.removeCallbacks(luaWidgetTickRunnable);
        }
        activeModule = id;
        ModuleManager.setActiveModule(mContext, id);
        updateModuleDockSelection();
        applyTerminalTrayState(false);
        homeWidgetsContainer.removeAllViews();

        if (showLuaModuleIfSource(id)) {
            // Rendered above.
        } else if (ModuleManager.MUSIC.equals(id)) {
            showMusicModule();
        } else if (ModuleManager.NOTIFICATIONS.equals(id)) {
            showNotificationsModule();
        } else if (ModuleManager.TIMER.equals(id)) {
            showTextModule(ModuleManager.TIMER, buildTimerModuleText());
        } else if (ModuleManager.CALENDAR.equals(id)) {
            showTextModule(ModuleManager.CALENDAR, buildCalendarModuleText());
        } else if (ModuleManager.REMINDER.equals(id)) {
            showTextModule(ModuleManager.REMINDER, buildReminderModuleText());
        } else {
            String source = ModuleManager.getModuleSource(mContext, id);
            if (ModuleManager.isLuaSource(source)) {
                renderLuaWidgetModule(id, false, false);
            }
            refreshLauncherModuleTextIfNeeded(id);
            String text = ModuleManager.getScriptText(mContext, id);
            showTextModule(id, TextUtils.isEmpty(text) ? "No module output yet." : text);
        }
        refreshSuggestionsForActiveModule();
        scheduleEventsRefreshIfNeeded();
        preserveModuleDockScrollX(dockScrollX);
    }

    private void ensureSystemLuaModules() {
        if (mContext == null) {
            return;
        }
        try {
            LuaWidgetManager.ensureSystemTimerWidget();
            String timerSource = LuaWidgetManager.SOURCE_PREFIX + LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID;
            String currentSource = ModuleManager.getModuleSource(mContext, ModuleManager.TIMER);
            if (TextUtils.isEmpty(currentSource) || TextUtils.equals(currentSource, timerSource)) {
                ModuleManager.setScriptModule(mContext, ModuleManager.TIMER, timerSource);
            }
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private boolean showLuaModuleIfSource(String id) {
        String source = ModuleManager.getModuleSource(mContext, id);
        if (!ModuleManager.isLuaSource(source)) {
            return false;
        }
        renderLuaWidgetModule(id, false, false);
        String text = ModuleManager.getScriptText(mContext, id);
        showTextModule(id, TextUtils.isEmpty(text) ? "No module output yet." : text);
        return true;
    }

    private void refreshLauncherModuleTextIfNeeded(String module) {
        String id = ModuleManager.normalize(module);
        String source = ModuleManager.getModuleSource(mContext, id);
        if (!ModuleManager.isLauncherSource(source)) {
            return;
        }
        String provider = ModuleManager.launcherProvider(source);
        if (ModuleManager.EVENTS.equals(provider)) {
            ModuleManager.setScriptText(mContext, id, UpcomingEventsManager.formatModulePayload(mContext));
        }
    }

    private void showMusicModule() {
        View musicWidget = LayoutInflater.from(mContext).inflate(R.layout.music_widget, homeWidgetsContainer, false);
        homeWidgetsContainer.addView(musicWidget);
        musicWidget.setVisibility(View.VISIBLE);
        TextView close = musicWidget.findViewById(R.id.music_widget_close);
        if (close != null) {
            close.setOnClickListener(v -> closeHomeModule());
            close.setTextColor(AppearanceSettings.moduleNameTextColor());
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            close.setTextSize(AppearanceSettings.moduleHeaderTextSize());
        }
        styleMusicWidget(musicWidget);
        updateMusicModuleText(musicWidget);
        scheduleInternalMusicTickerIfNeeded();
    }

    private void showNotificationsModule() {
        ensureNotificationServiceForModule();
        View notificationWidget = LayoutInflater.from(mContext).inflate(R.layout.notification_widget, homeWidgetsContainer, false);
        homeWidgetsContainer.addView(notificationWidget);
        notificationWidget.setVisibility(View.VISIBLE);
        notificationWidget.setClickable(true);
        notificationWidget.setFocusable(true);
        View notificationBorder = notificationWidget.findViewById(R.id.notification_widget_border);
        View notificationLabel = notificationWidget.findViewById(R.id.notification_widget_label);
        if (notificationLabel != null) {
            notificationLabel.setOnClickListener(v -> openNotificationShade());
        }
        TextView prev = notificationWidget.findViewById(R.id.notification_widget_prev);
        if (prev != null) {
            prev.setOnClickListener(v -> previousNotificationPage());
            prev.setTextColor(AppearanceSettings.moduleNameTextColor());
            prev.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        }
        TextView next = notificationWidget.findViewById(R.id.notification_widget_next);
        if (next != null) {
            next.setOnClickListener(v -> nextNotificationPage());
            next.setTextColor(AppearanceSettings.moduleNameTextColor());
            next.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        }
        TextView close = notificationWidget.findViewById(R.id.notification_widget_close);
        if (close != null) {
            close.setOnClickListener(v -> closeHomeModule());
            close.setTextColor(AppearanceSettings.moduleNameTextColor());
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            close.setTextSize(AppearanceSettings.moduleHeaderTextSize());
        }
        styleNotificationWidget(notificationWidget);
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(ACTION_REQUEST_NOTIFICATION_FEED));
    }

    private void ensureNotificationServiceForModule() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Tuils.hasNotificationAccess(mContext)) {
            return;
        }
        try {
            NotificationService.requestListenerRebind(mContext);
        } catch (Exception e) {
            Tuils.log(e);
        }
    }

    private void showTextModule(String module, String text) {
        View moduleView = LayoutInflater.from(mContext).inflate(R.layout.module_text_widget, homeWidgetsContainer, false);
        homeWidgetsContainer.addView(moduleView);

        TextView label = moduleView.findViewById(R.id.module_text_label);
        TextView body = moduleView.findViewById(R.id.module_text_body);
        TextView close = moduleView.findViewById(R.id.module_text_close);
        ScrollView scroll = moduleView.findViewById(R.id.module_text_scroll);
        if (label != null) {
            label.setText(ModuleManager.displayTitle(mContext, module));
        }
        if (body != null) {
            body.setText(text);
            body.setTextColor(AppearanceSettings.notificationWidgetTextColor());
            applyModuleBodyTypeface(body, text);
            constrainEventModuleScroll(module, scroll, body);
        }
        if (close != null) {
            close.setOnClickListener(v -> closeHomeModule());
            close.setTextColor(AppearanceSettings.moduleNameTextColor());
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            close.setTextSize(AppearanceSettings.moduleHeaderTextSize());
        }

        ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.decorateWidget(
                moduleView,
                R.id.module_text_border,
                R.id.module_text_label,
                AppearanceSettings.notificationWidgetBorderColor(),
                AppearanceSettings.moduleNameTextColor());
        styleModuleClose(close);
    }

    private void constrainEventModuleScroll(String module, ScrollView scroll, TextView body) {
        String id = ModuleManager.normalize(module);
        String source = ModuleManager.getModuleSource(mContext, id);
        if (!ModuleManager.EVENTS.equals(id) || !ModuleManager.isLauncherSource(source)
                || scroll == null || body == null) {
            return;
        }

        scroll.setFillViewport(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (scroll.getViewTreeObserver().isAlive()) {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                }

                int maxHeight = calculateCalendarTextHeight(body);
                int contentHeight = body.getHeight();
                if (maxHeight <= 0 || contentHeight <= 0) {
                    return true;
                }

                int targetHeight = Math.min(contentHeight, maxHeight);
                ViewGroup.LayoutParams params = scroll.getLayoutParams();
                if (params != null && params.height != targetHeight) {
                    params.height = targetHeight;
                    scroll.setLayoutParams(params);
                }
                scroll.setVerticalScrollBarEnabled(contentHeight > targetHeight);
                return true;
            }
        });
    }

    private void applyModuleBodyTypeface(TextView body, CharSequence text) {
        if (body == null) {
            return;
        }
        if (containsProgressBlockGlyphs(text)) {
            body.setTypeface(Typeface.MONOSPACE);
        } else {
            body.setTypeface(Tuils.getTypeface(mContext));
        }
    }

    private static boolean containsProgressBlockGlyphs(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u2591' || c == '\u2592' || c == '\u2593' || c == '\u2588') {
                return true;
            }
        }
        return false;
    }

    private int calculateCalendarTextHeight(TextView body) {
        int lineHeight = body.getLineHeight();
        int lines = countVisibleLines(buildCalendarModuleText());
        int padding = body.getPaddingTop() + body.getPaddingBottom();
        return Math.max(lineHeight, lineHeight * lines + padding);
    }

    private int countVisibleLines(String text) {
        if (TextUtils.isEmpty(text)) {
            return 1;
        }
        String[] lines = text.split("\\r?\\n", -1);
        int count = lines.length;
        while (count > 1 && TextUtils.isEmpty(lines[count - 1])) {
            count--;
        }
        return count;
    }

    private void styleModuleClose(TextView close) {
        if (close == null) return;
        int borderColor = AppearanceSettings.moduleButtonBorderColor();
        int bgColor = AppearanceSettings.moduleButtonBackgroundColor();
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.headerCornerRadius()));
        gd.setColor(ColorUtils.setAlphaComponent(bgColor, 255));
        if (AppearanceSettings.dashedBorders()) {
            gd.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        close.setBackground(gd);
        close.setTextSize(AppearanceSettings.moduleHeaderTextSize());
    }

    private void closeHomeModule() {
        int dockScrollX = consumeModuleDockScrollX();
        if (handler != null) {
            handler.removeCallbacks(eventsRefreshRunnable);
            handler.removeCallbacks(luaWidgetTickRunnable);
        }
        activeModule = "";
        ModuleManager.setActiveModule(mContext, "");
        if (homeWidgetsContainer != null) {
            homeWidgetsContainer.removeAllViews();
        }
        updateModuleDockSelection();
        applyTerminalTrayState(false);
        refreshSuggestionsForActiveModule();
        preserveModuleDockScrollX(dockScrollX);
    }

    private void refreshSuggestionsForActiveModule() {
        if (suggestionsManager != null && mTerminalAdapter != null
                && TextUtils.isEmpty(mTerminalAdapter.getInput())) {
            suggestionsManager.requestSuggestion(Tuils.EMPTYSTRING);
        }
    }

    private void refreshActiveModuleIfNeeded() {
        if (ModuleManager.TIMER.equals(activeModule)) {
            showHomeModule(ModuleManager.TIMER);
        } else if (ModuleManager.REMINDER.equals(activeModule)) {
            showHomeModule(ModuleManager.REMINDER);
        }
    }

    private void scheduleEventsRefreshIfNeeded() {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(eventsRefreshRunnable);
        if (!ModuleManager.EVENTS.equals(activeModule)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, ModuleManager.EVENTS);
        if (!ModuleManager.isLauncherSource(source)) {
            return;
        }

        long now = System.currentTimeMillis();
        long delay = EVENTS_REFRESH_FALLBACK_MS - (now % EVENTS_REFRESH_FALLBACK_MS) + EVENTS_REFRESH_GRACE_MS;
        handler.postDelayed(eventsRefreshRunnable, delay);
    }

    private void scheduleLuaWidgetTickIfNeeded(String module, LuaWidgetEngine.RenderResult result) {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(luaWidgetTickRunnable);
        String id = ModuleManager.normalize(module);
        if (!id.equals(activeModule) || result == null || result.tickIntervalMs <= 0L) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        if (!ModuleManager.isLuaSource(source)) {
            return;
        }
        handler.postDelayed(luaWidgetTickRunnable, result.tickIntervalMs);
    }

    private void tickActiveLuaWidget() {
        String id = ModuleManager.normalize(activeModule);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            handler.removeCallbacks(luaWidgetTickRunnable);
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
                updateModuleDockSelection();
            }
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            handler.removeCallbacks(luaWidgetTickRunnable);
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
                updateModuleDockSelection();
            }
            return;
        }

        LuaWidgetEngine.RenderResult result = getLuaWidgetEngine(widgetId).tick();
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
            scheduleLuaWidgetTickIfNeeded(id, result);
        }
    }

    private void updateMusicModuleText(View musicWidget) {
        TextView title = musicWidget.findViewById(R.id.music_song_title);
        TextView singer = musicWidget.findViewById(R.id.music_singer);
        MusicVisualizerView visualizer = musicWidget.findViewById(R.id.music_visualizer);
        int textColor = AppearanceSettings.musicWidgetTextColor();
        if (title != null) {
            title.setText(!TextUtils.isEmpty(lastMusicSong) ? "Title: " + lastMusicSong.toUpperCase() : "Title: -");
            title.setTextColor(textColor);
        }
        if (singer != null) {
            singer.setText(!TextUtils.isEmpty(lastMusicSinger) ? "Singer      : " + lastMusicSinger.toUpperCase() : "Singer      : -");
            singer.setTextColor(textColor);
        }
        if (visualizer != null) {
            visualizer.setBarColor(textColor);
            visualizer.setPlaying(lastMusicPlaying);
        }
    }

    private String buildTimerModuleText() {
        StringBuilder out = new StringBuilder();
        ClockManager clockManager = ClockManager.getInstance(mContext.getApplicationContext());
        out.append(clockManager.getTimerStatus()).append('\n');
        out.append(clockManager.getStopwatchStatus()).append('\n');

        PomodoroManager pomodoro = PomodoroManager.getInstance(mContext.getApplicationContext());
        if (pomodoro.isRunning()) {
            out.append("Pomodoro: ")
                    .append(pomodoro.getCurrentType().name().toLowerCase(Locale.US))
                    .append(" ")
                    .append(ClockManager.formatDuration(pomodoro.getRemainingMillis()))
                    .append('\n');
        } else {
            out.append("Pomodoro: idle\n");
        }
        out.append("Commands: timer, stopwatch, pomodoro");
        return out.toString();
    }

    private String buildReminderModuleText() {
        return ReminderManager.formatList(mContext) + "\nCommands: -add, -edit, -rm";
    }

    private String buildCalendarModuleText() {
        Calendar calendar = Calendar.getInstance();
        String[] months = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        int firstDay = calendar.get(Calendar.DAY_OF_WEEK);
        int max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

        StringBuilder out = new StringBuilder();
        out.append(months[month]).append(' ').append(year).append('\n');
        out.append("SU MO TU WE TH FR SA\n");
        for (int i = Calendar.SUNDAY; i < firstDay; i++) {
            out.append("   ");
        }
        for (int day = 1; day <= max; day++) {
            if (day == today) {
                out.append('[').append(day < 10 ? "0" : "").append(day).append(']');
            } else {
                if (day < 10) out.append('0');
                out.append(day).append(' ');
            }
            int dow = calendar.get(Calendar.DAY_OF_WEEK);
            if (dow == Calendar.SATURDAY) {
                out.append('\n');
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out.toString();
    }

    private void handleModuleCommand(Intent intent) {
        String command = intent.getStringExtra(EXTRA_MODULE_COMMAND);
        String module = intent.getStringExtra(EXTRA_MODULE_NAME);

        if ("rebuild".equals(command)) {
            rebuildModuleDock();
            if (homeWidgetsContainer != null && !TextUtils.isEmpty(activeModule)
                    && ModuleManager.getDock(mContext).contains(activeModule)) {
                showHomeModule(activeModule);
            }
        } else if ("show".equals(command)) {
            showHomeModule(module);
        } else if ("close".equals(command)) {
            closeHomeModule();
        } else if ("update".equals(command)) {
            if (!TextUtils.isEmpty(module) && module.equals(activeModule)) {
                showHomeModule(module);
            }
            rebuildModuleDock();
        } else if ("refresh".equals(command)) {
            refreshScriptModule(module);
        } else if ("lua_click".equals(command)) {
            int index = intent.getIntExtra(EXTRA_WIDGET_ACTION_INDEX, 0);
            clickLuaWidget(module, index);
        } else if ("lua_action".equals(command)) {
            actionLuaWidget(module, intent.getStringExtra(EXTRA_WIDGET_ACTION_VALUE));
        } else if ("lua_dialog".equals(command)) {
            int index = intent.getIntExtra(EXTRA_WIDGET_ACTION_INDEX, 0);
            dialogLuaWidget(module, index);
        } else if ("lua_expand".equals(command)) {
            setLuaWidgetExpanded(module, true);
        } else if ("lua_collapse".equals(command)) {
            setLuaWidgetExpanded(module, false);
        } else if ("lua_toggle".equals(command)) {
            toggleLuaWidgetExpanded(module);
        }
    }

    private void refreshScriptModule(String module) {
        String id = ModuleManager.normalize(module);
        String source = ModuleManager.getModuleSource(mContext, id);
        if (TextUtils.isEmpty(source)) {
            Tuils.sendOutput(mContext, "Module has no source: " + id);
            return;
        }
        if (ModuleManager.isLauncherSource(source)) {
            refreshLauncherModule(id, source);
            return;
        }
        if (ModuleManager.isLuaSource(source)) {
            renderLuaWidgetModule(id, true, true);
            return;
        }
        runTermuxScript(source, new ArrayList<>(), id, false);
    }

    private void refreshLauncherModule(String module, String source) {
        refreshLauncherModule(module, source, true);
    }

    private void refreshLauncherModule(String module, String source, boolean announce) {
        String provider = ModuleManager.launcherProvider(source);
        String payload;
        if (ModuleManager.EVENTS.equals(provider)) {
            payload = UpcomingEventsManager.formatModulePayload(mContext);
        } else {
            Tuils.sendOutput(mContext, "Unknown launcher module source: " + source);
            return;
        }

        String id = ModuleManager.normalize(module);
        ModuleManager.setScriptText(mContext, id, payload);
        if (id.equals(activeModule)) {
            showHomeModule(id);
        }
        updateModuleDockSelection();
        if (announce) {
            Tuils.sendOutput(mContext, "Module refreshed: " + id);
        }
    }

    private void renderLuaWidgetModule(String module, boolean repaint, boolean announce) {
        String id = ModuleManager.normalize(module);
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            ModuleManager.setScriptText(mContext, id, "::title " + ModuleManager.displayName(id)
                    + "\n::body Lua widget source not found: " + widgetId);
        } else if (!LuaWidgetManager.isEnabled(widgetId)) {
            handler.removeCallbacks(luaWidgetTickRunnable);
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
        } else if (!LuaWidgetManager.isTrusted(widgetId)) {
            handler.removeCallbacks(luaWidgetTickRunnable);
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
        } else {
            LuaWidgetEngine engine = getLuaWidgetEngine(widgetId);
            LuaWidgetEngine.RenderResult result = engine.render(announce);
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
            scheduleLuaWidgetTickIfNeeded(id, result);
        }

        if (repaint && id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
        if (announce) {
            Tuils.sendOutput(mContext, "Widget refreshed: " + id);
        }
    }

    private void clickLuaWidget(String module, int index) {
        String id = ModuleManager.normalize(module);
        if (TextUtils.isEmpty(id) || index <= 0) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua widget source not found: " + id);
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }

        LuaWidgetEngine engine = getLuaWidgetEngine(widgetId);
        LuaWidgetEngine.RenderResult result = engine.click(index);
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        scheduleLuaWidgetTickIfNeeded(id, result);
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
    }

    private void actionLuaWidget(String module, String value) {
        String id = ModuleManager.normalize(module);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua widget source not found: " + id);
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }

        LuaWidgetEngine.RenderResult result = getLuaWidgetEngine(widgetId).action(value);
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        scheduleLuaWidgetTickIfNeeded(id, result);
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
    }

    private void dialogLuaWidget(String module, int index) {
        String id = ModuleManager.normalize(module);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua widget source not found: " + id);
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }

        LuaWidgetEngine.RenderResult result = getLuaWidgetEngine(widgetId).dialog(index);
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        scheduleLuaWidgetTickIfNeeded(id, result);
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
    }

    private void setLuaWidgetExpanded(String module, boolean expanded) {
        String id = ModuleManager.normalize(module);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua widget source not found: " + id);
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }

        LuaWidgetEngine.RenderResult result = getLuaWidgetEngine(widgetId).setExpanded(expanded);
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        scheduleLuaWidgetTickIfNeeded(id, result);
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
    }

    private void toggleLuaWidgetExpanded(String module) {
        String id = ModuleManager.normalize(module);
        if (TextUtils.isEmpty(id)) {
            return;
        }
        String source = ModuleManager.getModuleSource(mContext, id);
        String widgetId = ModuleManager.luaWidgetId(source);
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua widget source not found: " + id);
            return;
        }
        if (!LuaWidgetManager.isEnabled(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.disabledPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }
        if (!LuaWidgetManager.isTrusted(widgetId)) {
            ModuleManager.setScriptText(mContext, id, LuaWidgetManager.consentPayload(widgetId));
            if (id.equals(activeModule)) {
                repaintActiveTextModule(id);
            }
            updateModuleDockSelection();
            return;
        }

        LuaWidgetEngine.RenderResult result = getLuaWidgetEngine(widgetId).toggleExpanded();
        ModuleManager.setScriptText(mContext, id, LuaWidgetManager.modulePayload(widgetId, result));
        scheduleLuaWidgetTickIfNeeded(id, result);
        if (id.equals(activeModule)) {
            repaintActiveTextModule(id);
        }
        updateModuleDockSelection();
    }

    private LuaWidgetEngine getLuaWidgetEngine(String widgetId) {
        String id = LuaWidgetManager.normalizeId(widgetId);
        long version = LuaWidgetManager.version(id);
        LuaWidgetEngine engine = luaWidgetEngines.get(id);
        if (engine == null || engine.version() != version) {
            engine = new LuaWidgetEngine(mContext, id, LuaWidgetManager.readScript(id), version, (updatedWidgetId, result) -> {
                List<String> modules = modulesForLuaWidget(updatedWidgetId);
                if (modules.isEmpty()) {
                    return;
                }
                for (String module : modules) {
                    if (!LuaWidgetManager.isEnabled(updatedWidgetId)) {
                        ModuleManager.setScriptText(mContext, module, LuaWidgetManager.disabledPayload(updatedWidgetId));
                        handler.removeCallbacks(luaWidgetTickRunnable);
                    } else if (!LuaWidgetManager.isTrusted(updatedWidgetId)) {
                        ModuleManager.setScriptText(mContext, module, LuaWidgetManager.consentPayload(updatedWidgetId));
                        handler.removeCallbacks(luaWidgetTickRunnable);
                    } else {
                        ModuleManager.setScriptText(mContext, module, LuaWidgetManager.modulePayload(updatedWidgetId, result));
                    }
                    if (module.equals(activeModule)) {
                        repaintActiveTextModule(module);
                        scheduleLuaWidgetTickIfNeeded(module, result);
                    }
                }
                updateModuleDockSelection();
            });
            luaWidgetEngines.put(id, engine);
        }
        return engine;
    }

    private List<String> modulesForLuaWidget(String widgetId) {
        ArrayList<String> modules = new ArrayList<>();
        String normalizedWidget = LuaWidgetManager.normalizeId(widgetId);
        for (String module : ModuleManager.listAll(mContext)) {
            String source = ModuleManager.getModuleSource(mContext, module);
            if (ModuleManager.isLuaSource(source)
                    && TextUtils.equals(normalizedWidget, ModuleManager.luaWidgetId(source))) {
                modules.add(ModuleManager.normalize(module));
            }
        }
        return modules;
    }

    private void repaintActiveTextModule(String id) {
        if (homeWidgetsContainer == null) {
            return;
        }
        homeWidgetsContainer.removeAllViews();
        String text = ModuleManager.getScriptText(mContext, id);
        showTextModule(id, TextUtils.isEmpty(text) ? "No module output yet." : text);
        refreshSuggestionsForActiveModule();
        scheduleEventsRefreshIfNeeded();
    }

    private void setupTermuxConsole(ViewGroup rootView) {
        termuxOverlay = rootView.findViewById(R.id.termux_overlay);
        if (termuxOverlay == null) {
            return;
        }

        termuxWindowBorder = rootView.findViewById(R.id.termux_window_border);
        termuxWindowLabel = rootView.findViewById(R.id.termux_window_label);
        termuxClose = rootView.findViewById(R.id.termux_close);
        termuxOutput = rootView.findViewById(R.id.termux_output);
        termuxPrefix = rootView.findViewById(R.id.termux_prefix);
        termuxInput = rootView.findViewById(R.id.termux_input);
        termuxScroll = rootView.findViewById(R.id.termux_scroll);
        termuxInputGroup = rootView.findViewById(R.id.termux_input_group);
        termuxTools = rootView.findViewById(R.id.termux_tools);
        termuxClear = rootView.findViewById(R.id.termux_clear);
        termuxUp = rootView.findViewById(R.id.termux_up);
        termuxDown = rootView.findViewById(R.id.termux_down);
        termuxPaste = rootView.findViewById(R.id.termux_paste);
        suggestionsContainer = rootView.findViewById(R.id.suggestions_container);

        styleTermuxConsole();

        if (termuxClose != null) {
            termuxClose.setOnClickListener(v -> closeTermuxConsole());
        }

        if (termuxInput != null) {
            termuxInput.setOnEditorActionListener((v, actionId, event) -> {
                boolean enter = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP;
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    String command = termuxInput.getText().toString();
                    termuxInput.setText(Tuils.EMPTYSTRING);
                    executeTermuxConsoleCommand(command);
                    return true;
                }
                return false;
            });
        }

        if (termuxClear != null) {
            termuxClear.setOnClickListener(v -> {
                termuxBuffer.setLength(0);
                updateTermuxOutput();
            });
        }
        if (termuxUp != null) {
            termuxUp.setOnClickListener(v -> {
                if (termuxScroll != null) {
                    termuxScroll.smoothScrollBy(0, -(int) Tuils.dpToPx(mContext, 120));
                }
            });
        }
        if (termuxDown != null) {
            termuxDown.setOnClickListener(v -> {
                if (termuxScroll != null) {
                    termuxScroll.smoothScrollBy(0, (int) Tuils.dpToPx(mContext, 120));
                }
            });
        }
        if (termuxPaste != null) {
            termuxPaste.setOnClickListener(v -> {
                String text = Tuils.getTextFromClipboard(mContext);
                if (text != null && text.length() > 0 && termuxInput != null) {
                    int start = Math.max(termuxInput.getSelectionStart(), 0);
                    int end = Math.max(termuxInput.getSelectionEnd(), 0);
                    termuxInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
                }
            });
        }
    }

    private void setupFileConsole(ViewGroup rootView) {
        fileOverlay = rootView.findViewById(R.id.file_overlay);
        if (fileOverlay == null) {
            return;
        }

        fileWindowBorder = rootView.findViewById(R.id.file_window_border);
        fileWindowLabel = rootView.findViewById(R.id.file_window_label);
        fileClose = rootView.findViewById(R.id.file_close);
        filePath = rootView.findViewById(R.id.file_path);
        fileOutput = rootView.findViewById(R.id.file_output);
        filePrefix = rootView.findViewById(R.id.file_prefix);
        fileInput = rootView.findViewById(R.id.file_input);
        fileScroll = rootView.findViewById(R.id.file_scroll);
        fileInputGroup = rootView.findViewById(R.id.file_input_group);
        fileTools = rootView.findViewById(R.id.file_tools);
        fileRefresh = rootView.findViewById(R.id.file_refresh);
        fileUp = rootView.findViewById(R.id.file_up);
        fileOpen = rootView.findViewById(R.id.file_open);
        filePaste = rootView.findViewById(R.id.file_paste);

        styleFileConsole();

        if (fileClose != null) {
            fileClose.setOnClickListener(v -> closeFileConsole());
        }

        if (fileInput != null) {
            fileInput.setOnEditorActionListener((v, actionId, event) -> {
                boolean enter = event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && event.getAction() == KeyEvent.ACTION_UP;
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    String command = fileInput.getText().toString();
                    fileInput.setText(Tuils.EMPTYSTRING);
                    executeFileConsoleCommand(command);
                    return true;
                }
                return false;
            });
        }

        if (fileRefresh != null) {
            fileRefresh.setOnClickListener(v -> refreshFileConsole(true));
        }
        if (fileUp != null) {
            fileUp.setOnClickListener(v -> executeFileConsoleCommand("cd .."));
        }
        if (fileOpen != null) {
            fileOpen.setOnClickListener(v -> {
                if (fileInput != null) {
                    fileInput.setText("open ");
                    fileInput.setSelection(fileInput.getText().length());
                    fileInput.requestFocus();
                }
            });
        }
        if (filePaste != null) {
            filePaste.setOnClickListener(v -> {
                String text = Tuils.getTextFromClipboard(mContext);
                if (text != null && text.length() > 0 && fileInput != null) {
                    int start = Math.max(fileInput.getSelectionStart(), 0);
                    int end = Math.max(fileInput.getSelectionEnd(), 0);
                    fileInput.getText().replace(Math.min(start, end), Math.max(start, end), text);
                }
            });
        }
    }

    protected UIManager(final Context context, final ViewGroup rootView, MainPack mainPack, boolean canApplyTheme, CommandExecuter executer) {
        this.mRootView = rootView;
        this.mainPack = mainPack;
        this.mExecuter = executer;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATE_SUGGESTIONS);
        filter.addAction(ACTION_UPDATE_HINT);
        filter.addAction(ACTION_ROOT);
        filter.addAction(ACTION_NOROOT);
//        filter.addAction(ACTION_CLEAR_SUGGESTIONS);
        filter.addAction(ACTION_LOGTOFILE);
        filter.addAction(ACTION_CLEAR);
        filter.addAction(ACTION_HACK);
        filter.addAction(ACTION_WEATHER);
        filter.addAction(ACTION_WEATHER_GOT_LOCATION);
        filter.addAction(ACTION_WEATHER_DELAY);
        filter.addAction(ACTION_WEATHER_MANUAL_UPDATE);
        filter.addAction(ACTION_MUSIC_CHANGED);
        filter.addAction(ACTION_NOTIFICATION_FEED);
        filter.addAction(ACTION_CLOCK_STATE);
        filter.addAction(ACTION_POMODORO_STATE);
        filter.addAction(ACTION_TERMUX_CONSOLE);
        filter.addAction(ACTION_FILE_CONSOLE);
        filter.addAction(ACTION_TERMUX_RESULT);
        filter.addAction(ACTION_MODULE_COMMAND);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if(action.equals(ACTION_UPDATE_SUGGESTIONS)) {
                    if(suggestionsManager != null) suggestionsManager.requestSuggestion(Tuils.EMPTYSTRING);
                } else if(action.equals(ACTION_UPDATE_HINT)) {
                    mTerminalAdapter.setDefaultHint();
                    refreshFileConsole(false);
                } else if(action.equals(ACTION_ROOT)) {
                    mTerminalAdapter.onRoot();
                } else if(action.equals(ACTION_CLOCK_STATE)) {
                    lastClockStateIntent = new Intent(intent);
                    updateClockOverlay(intent);
                    refreshActiveModuleIfNeeded();
                } else if(action.equals(ACTION_POMODORO_STATE)) {
                    lastPomodoroStateIntent = new Intent(intent);
                    updatePomodoroOverlay(intent);
                    refreshActiveModuleIfNeeded();
                } else if(action.equals(ACTION_TERMUX_CONSOLE)) {
                    openTermuxConsole(intent.getStringExtra(EXTRA_TERMUX_COMMAND));
                } else if(action.equals(ACTION_FILE_CONSOLE)) {
                    openFileConsole(intent.getStringExtra(EXTRA_FILE_COMMAND));
                } else if(action.equals(ACTION_TERMUX_RESULT)) {
                    appendTermuxResult(intent);
                } else if(action.equals(ACTION_MODULE_COMMAND)) {
                    handleModuleCommand(intent);
                } else if(action.equals(ACTION_NOROOT)) {
                    mTerminalAdapter.onStandard();
//                } else if(action.equals(ACTION_CLEAR_SUGGESTIONS)) {
//                    if(suggestionsManager != null) suggestionsManager.clear();
                } else if(action.equals(ACTION_LOGTOFILE)) {
                    String fileName = intent.getStringExtra(FILE_NAME);
                    if(fileName == null || fileName.contains(File.separator)) return;

                    File file = new File(Tuils.getFolder(), fileName);
                    if(file.exists()) file.delete();

                    try {
                        file.createNewFile();

                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(mTerminalAdapter.getTerminalText().getBytes());

                        Tuils.sendOutput(context, "Logged to " + file.getAbsolutePath());
                    } catch (Exception e) {
                        Tuils.sendOutput(Color.RED, context, e.toString());
                    }
                } else if(action.equals(ACTION_CLEAR)) {
                    mTerminalAdapter.clear();
                    if (suggestionsManager != null)
                        suggestionsManager.requestSuggestion(Tuils.EMPTYSTRING);
                } else if (action.equals(ACTION_HACK)) {
                    playHackOverlay();
                } else if(action.equals(ACTION_WEATHER)) {
                    Calendar c = Calendar.getInstance();

                    CharSequence s = intent.getCharSequenceExtra(XMLPrefsManager.VALUE_ATTRIBUTE);
                    if(s == null) s = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE);
                    if(s == null) return;

                    s = Tuils.span(context, s, weatherColor, labelSizes[Label.weather.ordinal()]);

                    updateText(Label.weather, s);

                    if(showWeatherUpdate) {
                        String message = context.getString(R.string.weather_updated) + Tuils.SPACE + c.get(Calendar.HOUR_OF_DAY) + "." + c.get(Calendar.MINUTE) + Tuils.SPACE + "(" + lastLatitude + ", " + lastLongitude + ")";
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT);
                    }
                } else if(action.equals(ohi.andre.consolelauncher.managers.status.WeatherManager.ACTION_WEATHER_GOT_LOCATION)) {
                    if(intent.getBooleanExtra(TuiLocationManager.FAIL, false)) {
                        if (weatherManager != null) {
                            weatherManager.stop();
                            weatherManager = null;
                        }

                        CharSequence s = Tuils.span(context, context.getString(R.string.location_error), weatherColor, labelSizes[Label.weather.ordinal()]);

                        updateText(Label.weather, s);
                    } else {
                        lastLatitude = intent.getDoubleExtra(TuiLocationManager.LATITUDE, 0);
                        lastLongitude = intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0);

                        location = Tuils.locationName(context, lastLatitude, lastLongitude);

                        if(weatherManager != null) {
                            weatherManager.setLocation(lastLatitude, lastLongitude);
                        }
                    }
                } else if(action.equals(ACTION_WEATHER_DELAY)) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(System.currentTimeMillis() + 1000 * 10);

                    if(showWeatherUpdate) {
                        String message = context.getString(R.string.weather_error) + Tuils.SPACE + c.get(Calendar.HOUR_OF_DAY) + "." + c.get(Calendar.MINUTE);
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT);
                    }

                    if (weatherManager != null) {
                        weatherManager.stop();
                        weatherManager.start();
                    }
                } else if(action.equals(ACTION_WEATHER_MANUAL_UPDATE)) {
                    if (weatherManager != null) {
                        weatherManager.stop();
                    }
                    weatherManager = new ohi.andre.consolelauncher.managers.status.WeatherManager(mContext, weatherDelay, labelSizes[Label.weather.ordinal()], statusUpdateListener);
                    weatherManager.start();
                } else if (action.equals(ACTION_MUSIC_CHANGED)) {
                    Log.d("TUI-Music", "UIManager received music change broadcast");
                    String song = intent.getStringExtra(SONG_TITLE);
                    String singer = intent.getStringExtra(SONG_SINGER);
                    boolean isPlaying = intent.getBooleanExtra(MUSIC_PLAYING, false);
                    String source = intent.getStringExtra(MusicService.MUSIC_SOURCE);
                    String pkg = intent.getStringExtra("package");

                    String preferredPkg = MusicSettings.preferredPackage();
                    boolean isPreferred = TextUtils.isEmpty(preferredPkg) || preferredPkg.equals(pkg);

                    // Source logic: external always wins if it is playing.
                    // Internal only wins if it's playing and external is not.
                    if (source != null) {
                        if (MusicService.SOURCE_EXTERNAL.equals(source)) {
                            // Strictly filter external source if a preferred app is set
                            if (!isPreferred) {
                                isPlaying = false;
                                song = null;
                            }
                            activeMusicSource = source;
                        } else if (MusicService.SOURCE_INTERNAL.equals(source) && MusicService.SOURCE_EXTERNAL.equals(activeMusicSource)) {
                            // Don't let internal idle broadcast override external metadata
                            if (!isPlaying) return;
                            activeMusicSource = source;
                        } else {
                            activeMusicSource = source;
                        }
                    }

                    Log.d("TUI-Music", "UIManager update UI: " + song + ", isPlaying=" + isPlaying + " source=" + activeMusicSource + " pkg=" + pkg);

                    boolean hasContent = (song != null && !song.isEmpty() && !song.equals("-"));
                    boolean showMusicWidget = isPlaying || hasContent;
                    lastMusicSong = song;
                    lastMusicSinger = singer;
                    lastMusicPlaying = isPlaying;

                    View musicWidget = rootView.findViewById(R.id.music_widget);
                    if (musicWidget != null) {
                        musicWidget.setVisibility(showMusicWidget ? View.VISIBLE : View.GONE);
                    }
                    updateContextContainerVisibility(rootView);

                    int widgetBorderColor = AppearanceSettings.musicWidgetBorderColor();
                    int widgetTextColor = AppearanceSettings.musicWidgetTextColor();
                    int widgetBgColor = AppearanceSettings.terminalWindowBackground();

                    MusicVisualizerView visualizerView = rootView.findViewById(R.id.music_visualizer);
                    if (visualizerView != null) {
                        visualizerView.setBarColor(widgetTextColor);
                        visualizerView.setPlaying(isPlaying);
                    }

                    TextView songTitleView = rootView.findViewById(R.id.music_song_title);
                    if (songTitleView != null) {
                        songTitleView.setText(song != null ? "Title: " + song.toUpperCase() : "Title: -");
                        songTitleView.setTextColor(widgetTextColor);
                    }

                    TextView singerView = rootView.findViewById(R.id.music_singer);
                    if (singerView != null) {
                        singerView.setText(singer != null ? "Singer      : " + singer.toUpperCase() : "Singer      : -");
                        singerView.setTextColor(widgetTextColor);
                    }

                    View borderView = rootView.findViewById(R.id.music_widget_border);
                    if (borderView != null) {
                        GradientDrawable gd = new GradientDrawable();
                        gd.setShape(GradientDrawable.RECTANGLE);
                        gd.setCornerRadius(UIUtils.dpToPx(mContext, AppearanceSettings.moduleCornerRadius()));
                        if (AppearanceSettings.dashedBorders()) {
                            gd.setStroke((int) UIUtils.dpToPx(mContext, 1.5f), widgetBorderColor,
                                    UIUtils.dpToPx(mContext, AppearanceSettings.dashLength()),
                                    UIUtils.dpToPx(mContext, AppearanceSettings.dashGap()));
                        }
                        gd.setColor(widgetBgColor);
                        borderView.setBackgroundDrawable(gd);
                    }

                    TextView widgetLabel = rootView.findViewById(R.id.music_widget_label);
                    if (widgetLabel != null) {
                        widgetLabel.setTextColor(widgetTextColor);
                        try {
                            GradientDrawable gd = (GradientDrawable) androidx.core.content.res.ResourcesCompat.getDrawable(
                                    mContext.getResources(), R.drawable.apps_drawer_header_border, null).mutate();
                            if (gd != null) {
                                gd.setCornerRadius(UIUtils.dpToPx(mContext, AppearanceSettings.headerCornerRadius()));
                                if (AppearanceSettings.dashedBorders()) {
                                    gd.setStroke((int) UIUtils.dpToPx(mContext, 1.5f), widgetBorderColor,
                                            UIUtils.dpToPx(mContext, AppearanceSettings.dashLength()),
                                            UIUtils.dpToPx(mContext, AppearanceSettings.dashGap()));
                                } else {
                                    gd.setStroke(0, Color.TRANSPARENT);
                                }
                                gd.setColor(ColorUtils.setAlphaComponent(widgetBgColor, 255));
                                widgetLabel.setBackgroundDrawable(gd);
                            }
                        } catch (Exception ignored) {}
                    }
                    scheduleInternalMusicTickerIfNeeded();
                } else if (action.equals(ACTION_NOTIFICATION_FEED)) {
                    ArrayList<NotificationService.Notification> notifications = intent.getParcelableArrayListExtra(EXTRA_NOTIFICATION_LIST);
                    updateNotificationWidget(rootView, notifications);
                } else if (action.equals(ACTION_CLOCK_STATE)) {
                    updateClockOverlay(intent);
                    String message = intent.getStringExtra(ClockManager.EXTRA_MESSAGE);
                    if (!TextUtils.isEmpty(message)) {
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT);
                    }
                }
            }
        };

        policy = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        component = new ComponentName(context, PolicyReceiver.class);

        mContext = context;

        preferences = mContext.getSharedPreferences(PREFS_NAME, 0);

        handler = new Handler();

        imm = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);

        if (!XMLPrefsManager.getBoolean(Ui.system_wallpaper) || !canApplyTheme) {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.bg_color));
        } else {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.overlay_color));
        }

        styleHackOverlay(rootView);
        setupTermuxConsole(rootView);
        setupFileConsole(rootView);
        setupResponsiveLandscapeLayout(rootView);

//        scrolllllll
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int heightDiff = rootView.getRootView().getHeight() - rootView.getHeight();
            boolean newKeyboardVisible = heightDiff > UIUtils.dpToPx(context, 200);
            int rootHeight = rootView.getHeight();
            boolean layoutStateChanged = !hasLastLayoutState
                    || keyboardVisible != newKeyboardVisible
                    || lastObservedRootHeight != rootHeight;
            keyboardVisible = newKeyboardVisible;
            hasLastLayoutState = true;
            lastObservedRootHeight = rootHeight;
            if (!layoutStateChanged) {
                return;
            }
            if (mTerminalAdapter != null && mTerminalAdapter.getInputView() instanceof EditText) {
                EditText terminalInput = (EditText) mTerminalAdapter.getInputView();
                terminalInput.setCursorVisible(keyboardVisible);
                terminalInput.setShowSoftInputOnFocus(keyboardVisible);
                if (terminalInput instanceof OutlineEditText) {
                    ((OutlineEditText) terminalInput).setIdleCursorVisible(!keyboardVisible);
                }
                if (!keyboardVisible && terminalInput.hasFocus()) {
                    terminalInput.clearFocus();
                }
            }
            setNotificationWidgetCompact(rootView, keyboardVisible);
            applyTerminalTrayState(false);
            if (keyboardVisible && XMLPrefsManager.getBoolean(Behavior.auto_scroll)) {
                if(mTerminalAdapter != null) mTerminalAdapter.scrollToEnd();
            }
        });

        clearOnLock = XMLPrefsManager.getBoolean(Behavior.clear_on_lock);

        lockOnDbTap = XMLPrefsManager.getBoolean(Behavior.double_tap_lock);
        doubleTapCmd = XMLPrefsManager.get(Behavior.double_tap_cmd);
        swipeDownNotifications = XMLPrefsManager.getBoolean(Behavior.swipe_down_notifications);
        swipeUpAppsDrawer = false;

        if(!lockOnDbTap && doubleTapCmd == null && !swipeDownNotifications && !swipeUpAppsDrawer) {
            policy = null;
            component = null;
            gestureDetector = null;
        } else {
            gestureDetector = new GestureDetectorCompat(mContext, new GestureDetector.OnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return false;
                }

                @Override
                public void onShowPress(MotionEvent e) {}

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return false;
                }

                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent e) {}

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                    if (swipeDownNotifications && velocityY > 100 && Math.abs(velocityY) > Math.abs(velocityX)) {
                        return openNotificationShade();
                    }
                    return false;
                }
            });

            gestureDetector.setOnDoubleTapListener(new OnDoubleTapListener() {

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    return false;
                }

                @Override
                public boolean onDoubleTapEvent(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {

                    if(doubleTapCmd != null && doubleTapCmd.length() > 0) {
                        String input = mTerminalAdapter.getInput();
                        mTerminalAdapter.setInput(doubleTapCmd);
                        mTerminalAdapter.simulateEnter();
                        mTerminalAdapter.setInput(input);
                    }

                    if(lockOnDbTap) {
                        boolean admin = policy.isAdminActive(component);

                        if (!admin) {
                            Intent i = Tuils.requestAdmin(component, mContext.getString(R.string.admin_permission));
                            mContext.startActivity(i);
                        } else {
                            policy.lockNow();
                        }
                    }

                    return true;
                }
            });

            rootView.setOnTouchListener((v, event) -> {
                if (gestureDetector != null) {
                    boolean handled = gestureDetector.onTouchEvent(event);
                    if (!handled && event.getAction() == MotionEvent.ACTION_UP) {
                        v.performClick();
                    }
                    return handled;
                }
                return false;
            });
        }

        appsDrawerRoot = rootView.findViewById(R.id.apps_drawer_root);
        appsList = rootView.findViewById(R.id.apps_list);
        appsGroupTabs = rootView.findViewById(R.id.apps_group_tabs);
        appsAlphaTabs = rootView.findViewById(R.id.apps_alpha_tabs);
        appsDrawerHeader = rootView.findViewById(R.id.apps_drawer_header);
        appsDrawerFooter = rootView.findViewById(R.id.apps_drawer_footer);

        View dummyAnchor = rootView.findViewById(R.id.apps_drawer_dummy_input_anchor);
        if (dummyAnchor != null) {
            rootView.post(() -> {
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) dummyAnchor.getLayoutParams();
                int height = 0;
                View inputGroup = rootView.findViewById(R.id.input_group);
                if (inputGroup != null) height += inputGroup.getHeight();

                View toolsView = rootView.findViewById(R.id.tools_view);
                if (toolsView != null && toolsView.getVisibility() == View.VISIBLE) {
                    height += toolsView.getHeight();
                }
                View suggestions = rootView.findViewById(R.id.suggestions_container);
                if (suggestions != null && suggestions.getVisibility() == View.VISIBLE) {
                    height += suggestions.getHeight();
                }
                lp.height = height;
                lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
                lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                dummyAnchor.setLayoutParams(lp);
            });
        }

        if (appsDrawerRoot != null) {
            appsDrawerRoot.setOnClickListener(v -> hideAppsDrawer());
        }

        applyDisplayMarginsForConfiguration(mContext.getResources().getConfiguration());

        labelSizes[Label.time.ordinal()] = XMLPrefsManager.getInt(Ui.time_size);
        labelSizes[Label.ram.ordinal()] = XMLPrefsManager.getInt(Ui.ram_size);
        labelSizes[Label.battery.ordinal()] = XMLPrefsManager.getInt(Ui.battery_size);
        labelSizes[Label.storage.ordinal()] = XMLPrefsManager.getInt(Ui.storage_size);
        labelSizes[Label.network.ordinal()] = XMLPrefsManager.getInt(Ui.network_size);
        labelSizes[Label.notes.ordinal()] = XMLPrefsManager.getInt(Ui.notes_size);
        labelSizes[Label.device.ordinal()] = XMLPrefsManager.getInt(Ui.device_size);
        labelSizes[Label.weather.ordinal()] = XMLPrefsManager.getInt(Ui.weather_size);
        labelSizes[Label.unlock.ordinal()] = XMLPrefsManager.getInt(Ui.unlock_size);
        labelSizes[Label.ascii.ordinal()] = XMLPrefsManager.getInt(Ui.ascii_size);

        labelViews = new TextView[] {
                (TextView) rootView.findViewById(R.id.tv0),
                (TextView) rootView.findViewById(R.id.tv1),
                (TextView) rootView.findViewById(R.id.tv2),
                (TextView) rootView.findViewById(R.id.tv3),
                (TextView) rootView.findViewById(R.id.tv4),
                (TextView) rootView.findViewById(R.id.tv5),
                (TextView) rootView.findViewById(R.id.tv6),
                (TextView) rootView.findViewById(R.id.tv7),
                (TextView) rootView.findViewById(R.id.tv8),
                (TextView) rootView.findViewById(R.id.tv9),
        };

        boolean[] show = new boolean[Label.values().length];
        show[Label.notes.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_notes);
        show[Label.ram.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_ram);
        show[Label.device.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_device_name);
        show[Label.time.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_time);
        show[Label.battery.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_battery);
        show[Label.network.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_network_info);
        show[Label.storage.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_storage_info);
        show[Label.weather.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_weather);
        show[Label.unlock.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_unlock_counter);
        show[Label.ascii.ordinal()] = XMLPrefsManager.getBoolean(Ui.show_ascii);

        float[] indexes = new float[Label.values().length];
        indexes[Label.notes.ordinal()] = show[Label.notes.ordinal()] ? XMLPrefsManager.getFloat(Ui.notes_index) : Integer.MAX_VALUE;
        indexes[Label.ram.ordinal()] = show[Label.ram.ordinal()] ? XMLPrefsManager.getFloat(Ui.ram_index) : Integer.MAX_VALUE;
        indexes[Label.device.ordinal()] = show[Label.device.ordinal()] ? XMLPrefsManager.getFloat(Ui.device_index) : Integer.MAX_VALUE;
        indexes[Label.time.ordinal()] = show[Label.time.ordinal()] ? XMLPrefsManager.getFloat(Ui.time_index) : Integer.MAX_VALUE;
        indexes[Label.battery.ordinal()] = show[Label.battery.ordinal()] ? XMLPrefsManager.getFloat(Ui.battery_index) : Integer.MAX_VALUE;
        indexes[Label.network.ordinal()] = show[Label.network.ordinal()] ? XMLPrefsManager.getFloat(Ui.network_index) : Integer.MAX_VALUE;
        indexes[Label.storage.ordinal()] = show[Label.storage.ordinal()] ? XMLPrefsManager.getFloat(Ui.storage_index) : Integer.MAX_VALUE;
        indexes[Label.weather.ordinal()] = show[Label.weather.ordinal()] ? XMLPrefsManager.getFloat(Ui.weather_index) : Integer.MAX_VALUE;
        indexes[Label.unlock.ordinal()] = show[Label.unlock.ordinal()] ? XMLPrefsManager.getFloat(Ui.unlock_index) : Integer.MAX_VALUE;
        indexes[Label.ascii.ordinal()] = show[Label.ascii.ordinal()] ? XMLPrefsManager.getFloat(Ui.ascii_index) : Integer.MAX_VALUE;

        int[] statusLineAlignments = getListOfIntValues(XMLPrefsManager.get(Ui.status_lines_alignment), 10, -1);

        String[] statusLinesBgRectColors = getListOfStringValues(XMLPrefsManager.get(Theme.status_lines_bgrectcolor), 10, "#ff000000");
        String[] otherBgRectColors = {
                XMLPrefsManager.get(Theme.input_bgrectcolor),
                XMLPrefsManager.get(Theme.output_bgrectcolor),
                XMLPrefsManager.get(Theme.suggestions_bgrectcolor),
                XMLPrefsManager.get(Theme.toolbar_bgrectcolor)
        };
        bgRectColors = new String[statusLinesBgRectColors.length + otherBgRectColors.length];
        System.arraycopy(statusLinesBgRectColors, 0, bgRectColors, 0, statusLinesBgRectColors.length);
        System.arraycopy(otherBgRectColors, 0, bgRectColors, statusLinesBgRectColors.length, otherBgRectColors.length);

        String[] statusLineBgColors = getListOfStringValues(XMLPrefsManager.get(Theme.status_lines_bg), 10, "#00000000");
        String[] otherBgColors = {
                XMLPrefsManager.get(Theme.input_bg),
                XMLPrefsManager.get(Theme.output_bg),
                XMLPrefsManager.get(Theme.suggestions_bg),
                XMLPrefsManager.get(Theme.toolbar_bg)
        };
        bgColors = new String[statusLineBgColors.length + otherBgColors.length];
        System.arraycopy(statusLineBgColors, 0, bgColors, 0, statusLineBgColors.length);
        System.arraycopy(otherBgColors, 0, bgColors, statusLineBgColors.length, otherBgColors.length);

        String[] statusLineOutlineColors = getListOfStringValues(XMLPrefsManager.get(Theme.status_lines_shadow_color), 10, "#00000000");
        String[] otherOutlineColors = {
                XMLPrefsManager.get(Theme.input_shadow_color),
                XMLPrefsManager.get(Theme.output_shadow_color),
        };
        outlineColors = new String[statusLineOutlineColors.length + otherOutlineColors.length];
        System.arraycopy(statusLineOutlineColors, 0, outlineColors, 0, statusLineOutlineColors.length);
        System.arraycopy(otherOutlineColors, 0, outlineColors, 10, otherOutlineColors.length);

        String[] shadowParams = getListOfStringValues(XMLPrefsManager.get(Ui.shadow_params), 3, "0");
        shadowXOffset = Integer.parseInt(shadowParams[0]);
        shadowYOffset = Integer.parseInt(shadowParams[1]);
        shadowRadius = Float.parseFloat(shadowParams[2]);

        String[] rectParams = getListOfStringValues(XMLPrefsManager.get(Ui.bgrect_params), 2, "0");
        strokeWidth = Integer.parseInt(rectParams[0]);
        cornerRadius = Integer.parseInt(rectParams[1]);

        useDashed = AppearanceSettings.dashedBorders();

        margins = new int[6][4];
        margins[0] = getListOfIntValues(XMLPrefsManager.get(Ui.status_lines_margins), 4, 0);
        margins[1] = getListOfIntValues(XMLPrefsManager.get(Ui.output_field_margins), 4, 0);
        margins[2] = getListOfIntValues(XMLPrefsManager.get(Ui.input_area_margins), 4, 0);
        margins[3] = getListOfIntValues(XMLPrefsManager.get(Ui.input_field_margins), 4, 0);
        margins[4] = getListOfIntValues(XMLPrefsManager.get(Ui.toolbar_margins), 4, 0);
        margins[5] = getListOfIntValues(XMLPrefsManager.get(Ui.suggestions_area_margin), 4, 0);

        AllowEqualsSequence sequence = new AllowEqualsSequence(indexes, Label.values());

        if (show[Label.ascii.ordinal()]) {
            asciiColor = XMLPrefsManager.getColor(Theme.ascii_color);
            File asciiFile = new File(Tuils.getFolder(), "ascii.txt");
            if (!asciiFile.exists()) {
                try {
                    asciiFile.createNewFile();
                    String sample = " ____  _____  _____  _   _ ___ \n" +
                                   "|  _ \\| ____||_   _|| | | |_ _|\n" +
                                   "| |_) |  _|    | |  | | | || | \n" +
                                   "|  _ <| |___   | |  | |_| || | \n" +
                                   "|_| \\_\\_____|  |_|   \\___/|___|\n";
                    FileOutputStream fos = new FileOutputStream(asciiFile);
                    fos.write(sample.getBytes());
                    fos.close();
                } catch (Exception e) {
                    Log.e("TUI-UI", "Error creating ascii.txt", e);
                }
            }

            try {
                if (asciiFile.exists()) {
                    FileInputStream fis = new FileInputStream(asciiFile);
                    byte[] data = new byte[(int) asciiFile.length()];
                    fis.read(data);
                    fis.close();
                    asciiContent = Tuils.NEWLINE + new String(data, "UTF-8");
                } else {
                    asciiContent = "ascii.txt not found after creation attempt";
                }
            } catch (Exception e) {
                asciiContent = "Error loading ascii.txt: " + e.getMessage();
                Log.e("TUI-UI", "Error loading ascii.txt", e);
            }

            updateText(Label.ascii, Tuils.span(mContext, asciiContent, asciiColor, labelSizes[Label.ascii.ordinal()]));
            TextView asciiView = getLabelView(Label.ascii);
            if (asciiView != null) {
                asciiView.setTypeface(Typeface.MONOSPACE);
            }
        }

        LinearLayout lViewsParent = (LinearLayout) labelViews[0].getParent();

        int effectiveCount = 0;
        for(int count = 0; count < labelViews.length; count++) {
            labelViews[count].setOnTouchListener(this);

            Object[] os = sequence.get(count);

//            views on the same line
            for(int j = 0; j < os.length; j++) {
//                i is the object gave to the constructor
                int i = ((Label) os[j]).ordinal();
//                v is the adjusted index (2.0, 2.1, 2.2, ...)
                float v = (float) count + ((float) j * 0.1f);

                labelIndexes[i] = v;
            }

            if(count >= sequence.getMinKey() && count <= sequence.getMaxKey() && os.length > 0) {
                labelViews[count].setTypeface(Tuils.getTypeface(context));

                int ec = effectiveCount++;

//                -1 = left     0 = center     1 = right
                int p = statusLineAlignments[ec];
                if(p >= 0) labelViews[count].setGravity(p == 0 ? Gravity.CENTER_HORIZONTAL : Gravity.RIGHT);

                if(count != labelIndexes[Label.notes.ordinal()]) {
                    labelViews[count].setVerticalScrollBarEnabled(false);
                }

                applyBgRect(mContext, labelViews[count], "#00000000", bgColors[count], margins[0], strokeWidth, (int) Tuils.dpToPx(mContext, AppearanceSettings.moduleCornerRadius()), useDashed, AppearanceSettings.dashedBorderColor());
                applyShadow(labelViews[count], outlineColors[count], shadowXOffset, shadowYOffset, shadowRadius);
            } else {
                lViewsParent.removeView(labelViews[count]);
                labelViews[count] = null;
            }
        }

        if (show[Label.ram.ordinal()]) {
            ramManager = new RamManager(mContext, RAM_DELAY, labelSizes[Label.ram.ordinal()], statusUpdateListener);
            ramManager.start();
        }

        if(show[Label.storage.ordinal()]) {
            storageManager = new StorageManager(mContext, STORAGE_DELAY, labelSizes[Label.storage.ordinal()], statusUpdateListener);
            storageManager.start();
        }

        if (show[Label.device.ordinal()]) {
            Pattern USERNAME = Pattern.compile("%u", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
            Pattern DV = Pattern.compile("%d", Pattern.CASE_INSENSITIVE | Pattern.LITERAL);

            String deviceFormat = XMLPrefsManager.get(Behavior.device_format);

            String username = XMLPrefsManager.get(Ui.username);
            String deviceName = XMLPrefsManager.get(Ui.deviceName);
            if (deviceName == null || deviceName.length() == 0) {
                deviceName = Build.DEVICE;
            }

            deviceFormat = USERNAME.matcher(deviceFormat).replaceAll(Matcher.quoteReplacement(username != null ? username : "null"));
            deviceFormat = DV.matcher(deviceFormat).replaceAll(Matcher.quoteReplacement(deviceName));
            deviceFormat = Tuils.patternNewline.matcher(deviceFormat).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE));

            updateText(Label.device, Tuils.span(mContext, deviceFormat, XMLPrefsManager.getColor(Theme.device_color), labelSizes[Label.device.ordinal()]));
        }

        if(show[Label.time.ordinal()]) {
            tuiTimeManager = new ohi.andre.consolelauncher.managers.status.TimeManager(mContext, TIME_DELAY, labelSizes[Label.time.ordinal()], statusUpdateListener);
            tuiTimeManager.start();
        }

        if(show[Label.battery.ordinal()]) {
            mediumPercentage = XMLPrefsManager.getInt(Behavior.battery_medium);
            lowPercentage = XMLPrefsManager.getInt(Behavior.battery_low);

            batteryManager = new ohi.andre.consolelauncher.managers.status.BatteryManager(mContext, labelSizes[Label.battery.ordinal()], mediumPercentage, lowPercentage, statusUpdateListener);
            batteryManager.start();
        }

        if(show[Label.network.ordinal()]) {
            networkManager = new NetworkManager(mContext, 3000, labelSizes[Label.network.ordinal()], statusUpdateListener);
            networkManager.start();
        }

        final TextView notesView = getLabelView(Label.notes);
        notesManager = new NotesManager(context, notesView);
        if(show[Label.notes.ordinal()]) {
            tuiNotesManager = new ohi.andre.consolelauncher.managers.status.NotesManager(mContext, 2000, labelSizes[Label.notes.ordinal()], notesManager, statusUpdateListener);
            tuiNotesManager.start();

            notesView.setMovementMethod(new LinkMovementMethod());

            notesMaxLines = XMLPrefsManager.getInt(Ui.notes_max_lines);
            if(notesMaxLines > 0) {
                notesView.setMaxLines(notesMaxLines);
                notesView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
//                notesView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
//                notesView.setVerticalScrollBarEnabled(true);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && XMLPrefsManager.getBoolean(Ui.show_scroll_notes_message)) {
                    notesView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

                        int linesBefore = Integer.MIN_VALUE;

                        @Override
                        public void onGlobalLayout() {
                            if(notesView.getLineCount() > notesMaxLines && linesBefore <= notesMaxLines) {
                                Tuils.sendOutput(Color.RED, context, R.string.note_max_reached);
                            }

                            linesBefore = notesView.getLineCount();
                        }
                    });
                }
            }
        }

        if(show[Label.weather.ordinal()]) {
            weatherColor = XMLPrefsManager.getColor(Theme.weather_color);
            weatherDelay = XMLPrefsManager.getInt(Behavior.weather_update_time) * 1000;

            weatherManager = new ohi.andre.consolelauncher.managers.status.WeatherManager(mContext, weatherDelay, labelSizes[Label.weather.ordinal()], statusUpdateListener);
            weatherManager.start();

            showWeatherUpdate = XMLPrefsManager.getBoolean(Behavior.show_weather_updates);
        }

        if (show[Label.ascii.ordinal()]) {
            File asciiFile = new File(Tuils.getFolder(), "ascii.txt");
            if (!asciiFile.exists()) {
                try {
                    Tuils.write(asciiFile, "  _____         _______     _    _ _____ \n" +
                            " |  __ \\       |__   __|   | |  | |_   _|\n" +
                            " | |__) | ___     | |______| |  | | | |  \n" +
                            " |  _  / / _ \\    | |______| |  | | | |  \n" +
                            " | | \\ \\|  __/    | |      | |__| |_| |_ \n" +
                            " |_|  \\_\\\\___|    |_|       \\____/|_____|\n");
                } catch (Exception e) {
                    Log.e("TUI-UI", "Error creating ascii.txt", e);
                }
            }

            try {
                asciiContent = Tuils.NEWLINE + Tuils.inputStreamToString(new FileInputStream(asciiFile));
                asciiColor = XMLPrefsManager.getColor(Theme.ascii_color);

                updateText(Label.ascii, Tuils.span(mContext, asciiContent, asciiColor, labelSizes[Label.ascii.ordinal()]));

                TextView asciiView = getLabelView(Label.ascii);
                if (asciiView != null) {
                    asciiView.setTypeface(Typeface.MONOSPACE);
                }
            } catch (Exception e) {
                Log.e("TUI-UI", "Error loading ascii.txt", e);
            }
        }

        if (show[Label.unlock.ordinal()]) {
            unlockManager = new ohi.andre.consolelauncher.managers.status.UnlockManager(mContext, labelSizes[Label.unlock.ordinal()], statusUpdateListener);
            unlockManager.start();
        }

        // Setup ViewPager2
        viewPager = mRootView.findViewById(R.id.view_pager);
        viewPager.setAdapter(new PagerAdapter());
        viewPager.setOffscreenPageLimit(1);
        setupTerminalPage(mRootView);
        applyResponsiveLandscapeLayout(mContext.getResources().getConfiguration());

        styleClockOverlay(rootView);

        int drawTimes = XMLPrefsManager.getInt(Ui.text_redraw_times);
        if(drawTimes <= 0) drawTimes = 1;
        OutlineTextView.redrawTimes = drawTimes;

        LocalBroadcastManager.getInstance(context.getApplicationContext()).registerReceiver(receiver, filter);
        if (NotificationSettings.showTerminal()) {
            final LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context.getApplicationContext());
            lbm.sendBroadcast(new Intent(ACTION_REQUEST_NOTIFICATION_FEED));
            rootView.postDelayed(() -> lbm.sendBroadcast(new Intent(ACTION_REQUEST_NOTIFICATION_FEED)), 350);
            rootView.postDelayed(() -> lbm.sendBroadcast(new Intent(ACTION_REQUEST_NOTIFICATION_FEED)), 1100);
        }
        ClockManager.getInstance(context.getApplicationContext()).broadcastState();

        scheduleTypefaceRefreshes();
    }

    private void styleMusicWidget(View musicWidget) {
        if (musicWidget == null) return;
        ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.decorateWidget(musicWidget, R.id.music_widget_border, R.id.music_widget_label);
        styleModuleClose(musicWidget.findViewById(R.id.music_widget_close));

        TextView titleView = musicWidget.findViewById(R.id.music_song_title);
        TextView singerView = musicWidget.findViewById(R.id.music_singer);
        if (titleView != null) {
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f);
            titleView.setIncludeFontPadding(false);
        }
        if (singerView != null) {
            singerView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11f);
            singerView.setIncludeFontPadding(false);
        }

        // Style control buttons
        int widgetColor = AppearanceSettings.musicWidgetTextColor();
        int widgetBorderColor = AppearanceSettings.moduleButtonBorderColor();
        boolean useDashed = AppearanceSettings.dashedBorders();

        TextView prevBtn = musicWidget.findViewById(R.id.music_prev);
        TextView nextBtn = musicWidget.findViewById(R.id.music_next);
        TextView playPauseBtn = musicWidget.findViewById(R.id.music_play_pause);

        View[] buttons = {prevBtn, nextBtn, playPauseBtn};
        for (View b : buttons) {
            if (b instanceof TextView) {
                TextView btn = (TextView) b;
                btn.setTextColor(widgetColor);
                btn.setTypeface(Tuils.getTypeface(mContext));
                btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
                btn.setIncludeFontPadding(false);
                btn.setSingleLine(true);
                btn.setEllipsize(TextUtils.TruncateAt.END);
                
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.RECTANGLE);
                gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.moduleCornerRadius()));
                if (useDashed) {
                    gd.setStroke((int) Tuils.dpToPx(mContext, 1.2f), widgetBorderColor,
                            Tuils.dpToPx(mContext, AppearanceSettings.dashLength() / 2),
                            Tuils.dpToPx(mContext, AppearanceSettings.dashGap() / 2));
                }
                gd.setColor(Color.TRANSPARENT);
                btn.setBackgroundDrawable(gd);
            }
        }

        if (prevBtn != null) {
            prevBtn.setOnClickListener(v -> {
                if ("internal".equals(activeMusicSource)) {
                    if (mainPack.player != null) mainPack.player.playPrev();
                } else {
                    Intent intent = new Intent(MusicService.ACTION_MUSIC_CONTROL);
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_PREV_INT);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                }
            });
        }

        if (nextBtn != null) {
            nextBtn.setOnClickListener(v -> {
                if ("internal".equals(activeMusicSource)) {
                    if (mainPack.player != null) mainPack.player.playNext();
                } else {
                    Intent intent = new Intent(MusicService.ACTION_MUSIC_CONTROL);
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_NEXT_INT);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                }
            });
        }

        if (playPauseBtn != null) {
            playPauseBtn.setOnClickListener(v -> {
                if ("internal".equals(activeMusicSource)) {
                    if (mainPack.player != null) {
                        if (mainPack.player.isPlaying()) mainPack.player.pause();
                        else mainPack.player.play();
                    }
                } else {
                    Intent intent = new Intent(MusicService.ACTION_MUSIC_CONTROL);
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_PLAY_PAUSE_INT);
                    LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
                }
            });
        }
    }

    private void styleHackOverlay(View rootView) {
        View overlay = rootView.findViewById(R.id.hack_overlay);
        TextView hackText = rootView.findViewById(R.id.hack_text);
        if (overlay == null || hackText == null) {
            return;
        }

        int accent = AppearanceSettings.moduleNameTextColor();
        int surface = ColorUtils.setAlphaComponent(AppearanceSettings.terminalWindowBackground(), 238);
        int border = ColorUtils.setAlphaComponent(accent, 220);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(ColorUtils.setAlphaComponent(surface, 232));
        if (AppearanceSettings.dashedBorders()) {
            bg.setStroke((int) Tuils.dpToPx(mContext, 1.5f), border,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        overlay.setBackground(bg);
        overlay.setOnClickListener(v -> dismissHackOverlay());

        hackText.setTextColor(accent);
        hackText.setTypeface(Tuils.getTypeface(mContext));
        hackText.setTextSize(11f);
    }

    private void styleTermuxConsole() {
        if (termuxOverlay == null) {
            return;
        }

        int borderColor = AppearanceSettings.notificationWidgetBorderColor();
        int textColor = AppearanceSettings.notificationWidgetTextColor();
        int bgColor = AppearanceSettings.terminalWindowBackground();
        int labelBg = ColorUtils.setAlphaComponent(bgColor, 255);

        if (termuxWindowBorder != null) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.outputCornerRadius()));
            border.setColor(bgColor);
            if (AppearanceSettings.dashedBorders()) {
                border.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor,
                        Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
            }
            termuxWindowBorder.setBackground(border);
        }

        if (termuxWindowLabel != null) {
            termuxWindowLabel.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            termuxWindowLabel.setTextSize(AppearanceSettings.outputHeaderTextSize());
            termuxWindowLabel.setTextColor(textColor);
            termuxWindowLabel.setBackground(termuxLabelBackground(labelBg, borderColor));
        }

        if (termuxClose != null) {
            termuxClose.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            termuxClose.setTextSize(AppearanceSettings.outputHeaderTextSize());
            termuxClose.setTextColor(textColor);
            termuxClose.setBackground(termuxLabelBackground(labelBg, borderColor));
        }

        if (termuxOutput != null) {
            termuxOutput.setTypeface(Tuils.getTypeface(mContext));
            termuxOutput.setTextColor(textColor);
            termuxOutput.setTextIsSelectable(true);
        }

        if (termuxPrefix != null) {
            termuxPrefix.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            termuxPrefix.setTextColor(textColor);
        }

        if (termuxInput != null) {
            termuxInput.setTypeface(Tuils.getTypeface(mContext));
            termuxInput.setTextColor(textColor);
            termuxInput.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150));
        }

        if (termuxInputGroup != null) {
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setShape(GradientDrawable.RECTANGLE);
            inputBg.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.outputCornerRadius()));
            inputBg.setColor(ColorUtils.blendARGB(bgColor, Color.BLACK, 0.16f));
            if (AppearanceSettings.dashedBorders()) {
                inputBg.setStroke((int) Tuils.dpToPx(mContext, 1.2f), ColorUtils.setAlphaComponent(borderColor, 180),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
            }
            termuxInputGroup.setBackground(inputBg);
        }

        if (termuxTools != null) {
            termuxTools.setBackgroundColor(Color.TRANSPARENT);
        }
        styleTermuxToolButton(termuxClear, textColor);
        styleTermuxToolButton(termuxUp, textColor);
        styleTermuxToolButton(termuxDown, textColor);
        styleTermuxToolButton(termuxPaste, textColor);
    }

    private GradientDrawable termuxLabelBackground(int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.headerCornerRadius()));
        bg.setColor(fill);
        if (AppearanceSettings.dashedBorders()) {
            bg.setStroke((int) Tuils.dpToPx(mContext, 1.5f), stroke,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        return bg;
    }

    private void styleTermuxToolButton(TextView button, int color) {
        if (button == null) {
            return;
        }
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setTextColor(color);
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        button.setPadding((int) Tuils.dpToPx(mContext, 2), 0,
                (int) Tuils.dpToPx(mContext, 2), 0);
    }

    private void styleFileConsole() {
        if (fileOverlay == null) {
            return;
        }

        int borderColor = AppearanceSettings.notificationWidgetBorderColor();
        int textColor = AppearanceSettings.notificationWidgetTextColor();
        int bgColor = AppearanceSettings.terminalWindowBackground();
        int labelBg = ColorUtils.setAlphaComponent(bgColor, 255);

        if (fileWindowBorder != null) {
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.outputCornerRadius()));
            border.setColor(bgColor);
            if (AppearanceSettings.dashedBorders()) {
                border.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor,
                        Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
            }
            fileWindowBorder.setBackground(border);
        }

        if (fileWindowLabel != null) {
            fileWindowLabel.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            fileWindowLabel.setTextSize(AppearanceSettings.outputHeaderTextSize());
            fileWindowLabel.setTextColor(textColor);
            fileWindowLabel.setBackground(termuxLabelBackground(labelBg, borderColor));
        }
        if (fileClose != null) {
            fileClose.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            fileClose.setTextSize(AppearanceSettings.outputHeaderTextSize());
            fileClose.setTextColor(textColor);
            fileClose.setBackground(termuxLabelBackground(labelBg, borderColor));
        }
        if (filePath != null) {
            filePath.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            filePath.setTextColor(textColor);
        }
        if (fileOutput != null) {
            fileOutput.setTypeface(Tuils.getTypeface(mContext));
            fileOutput.setTextColor(textColor);
            fileOutput.setTextIsSelectable(true);
        }
        if (filePrefix != null) {
            filePrefix.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            filePrefix.setTextColor(textColor);
        }
        if (fileInput != null) {
            fileInput.setTypeface(Tuils.getTypeface(mContext));
            fileInput.setTextColor(textColor);
            fileInput.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150));
        }
        if (fileInputGroup != null) {
            GradientDrawable inputBg = new GradientDrawable();
            inputBg.setShape(GradientDrawable.RECTANGLE);
            inputBg.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.outputCornerRadius()));
            inputBg.setColor(ColorUtils.blendARGB(bgColor, Color.BLACK, 0.16f));
            if (AppearanceSettings.dashedBorders()) {
                inputBg.setStroke((int) Tuils.dpToPx(mContext, 1.2f), ColorUtils.setAlphaComponent(borderColor, 180),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                        Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
            }
            fileInputGroup.setBackground(inputBg);
        }
        if (fileTools != null) {
            fileTools.setBackgroundColor(Color.TRANSPARENT);
        }
        styleTermuxToolButton(fileRefresh, textColor);
        styleTermuxToolButton(fileUp, textColor);
        styleTermuxToolButton(fileOpen, textColor);
        styleTermuxToolButton(filePaste, textColor);
    }

    public void openTermuxConsole(String command) {
        if (termuxOverlay == null) {
            return;
        }

        closeFileConsole(false);
        styleTermuxConsole();
        termuxOverlay.setVisibility(View.VISIBLE);
        termuxOverlay.bringToFront();
        hideHomeSuggestionsForTermux();

        if (termuxBuffer.length() == 0) {
            appendTermuxLine("Re:T-UI Termux console");
            appendTermuxLine("Type help, status, open, run, clear, or exit.");
            appendTermuxLine("Run non-interactive Termux scripts from here.");
        }

        String normalized = command == null ? Tuils.EMPTYSTRING : command.trim();
        if (normalized.length() > 0) {
            if (normalized.startsWith("-")) {
                normalized = normalized.substring(1);
            }
            executeTermuxConsoleCommand(normalized);
        }

        if (termuxInput != null) {
            termuxInput.requestFocus();
            termuxInput.postDelayed(() -> {
                InputMethodManager manager = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (manager != null) {
                    manager.showSoftInput(termuxInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120);
        }
    }

    public void openFileConsole(String command) {
        if (fileOverlay == null) {
            return;
        }

        closeTermuxConsole();
        styleFileConsole();
        fileOverlay.setVisibility(View.VISIBLE);
        fileOverlay.bringToFront();
        hideHomeSuggestionsForTermux();
        refreshFileConsole(true);

        String normalized = command == null ? Tuils.EMPTYSTRING : command.trim();
        if (normalized.length() > 0) {
            executeFileConsoleCommand(normalized);
        }

        if (fileInput != null) {
            fileInput.requestFocus();
            fileInput.postDelayed(() -> {
                InputMethodManager manager = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (manager != null) {
                    manager.showSoftInput(fileInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 120);
        }
    }

    private void closeFileConsole() {
        closeFileConsole(true);
    }

    private void closeFileConsole(boolean restoreSuggestions) {
        if (fileOverlay != null) {
            fileOverlay.setVisibility(View.GONE);
        }
        if (restoreSuggestions) {
            restoreHomeSuggestionsAfterTermux();
        }
        if (fileInput != null) {
            InputMethodManager manager = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.hideSoftInputFromWindow(fileInput.getWindowToken(), 0);
            }
            fileInput.clearFocus();
        }
        if (mTerminalAdapter != null && restoreSuggestions) {
            mTerminalAdapter.focusInputEnd();
        }
    }

    private void executeFileConsoleCommand(String rawCommand) {
        String command = rawCommand == null ? Tuils.EMPTYSTRING : rawCommand.trim();
        if (command.length() == 0) {
            return;
        }
        String lower = command.toLowerCase(Locale.US);

        if ("exit".equals(lower) || "close".equals(lower)) {
            closeFileConsole();
            return;
        }
        if ("help".equals(lower)) {
            renderFileConsole("Commands:\ncd [folder]\ncd ..\nls\npwd\nopen [file]\ntermux-open [file]\nshare [file]\nrefresh\nexit");
            return;
        }
        if ("refresh".equals(lower) || "reload".equals(lower) || "ls".equals(lower)) {
            refreshFileConsole(true);
            return;
        }
        if ("pwd".equals(lower)) {
            renderFileConsole(mainPack.currentDirectory.getAbsolutePath());
            return;
        }

        if (mExecuter != null) {
            mExecuter.execute(command, null);
        }

        if (lower.equals("cd") || lower.startsWith("cd ")) {
            if (FileBackendManager.activeBackend(mContext) != FileBackendManager.Active.TERMUX) {
                refreshFileConsole(true);
            } else {
                renderFileConsole("Changing directory...");
            }
        } else if (lower.startsWith("open ") || lower.startsWith("termux-open ") || lower.startsWith("share ")) {
            renderFileConsole("Dispatched: " + command);
        } else {
            refreshFileConsole(true);
        }
    }

    private void refreshFileConsole(boolean forceTermuxRequest) {
        if (fileOverlay == null || fileOverlay.getVisibility() != View.VISIBLE || mainPack == null || mainPack.currentDirectory == null) {
            return;
        }

        String path = mainPack.currentDirectory.getAbsolutePath();
        if (filePath != null) {
            filePath.setText(path);
        }

        if (FileBackendManager.activeBackend(mContext) == FileBackendManager.Active.TERMUX) {
            List<String> dirs = TermuxBridgeCache.dirs(path);
            List<String> files = TermuxBridgeCache.files(path);
            if (dirs.isEmpty() && files.isEmpty()) {
                renderFileConsole("Loading " + path + "...");
            } else {
                renderFileConsole(buildFileListing(dirs, files, null));
            }
            requestFileConsoleTermuxListing(path, forceTermuxRequest);
            return;
        }

        renderFileConsole(buildNativeFileListing(mainPack.currentDirectory));
    }

    private void requestFileConsoleTermuxListing(String path, boolean force) {
        if (force || TermuxBridgeCache.shouldRequest("dirs", path)) {
            TermuxBridgeManager.dispatchShell(mContext, "fm-dirs " + path, tbridge.LIST_DIRS_SCRIPT, TermuxBridgeManager.TERMUX_HOME, path);
        }
        if (force || TermuxBridgeCache.shouldRequest("files", path)) {
            TermuxBridgeManager.dispatchShell(mContext, "fm-files " + path, tbridge.LIST_FILES_SCRIPT, TermuxBridgeManager.TERMUX_HOME, path);
        }
    }

    private String buildNativeFileListing(File directory) {
        if (directory == null || !directory.exists()) {
            return "Path not found.";
        }
        if (!directory.isDirectory()) {
            return directory.getName();
        }

        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return "[]";
        }
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        List<String> dirs = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory()) {
                dirs.add(child.getName());
            } else {
                files.add(child.getName());
            }
        }
        return buildFileListing(dirs, files, null);
    }

    private String buildFileListing(List<String> dirs, List<String> files, String error) {
        StringBuilder out = new StringBuilder();
        out.append("[..]");
        if (error != null && error.trim().length() > 0) {
            out.append('\n').append("error: ").append(error.trim());
        }
        for (String dir : dirs) {
            out.append('\n').append("[D] ").append(dir);
        }
        for (String file : files) {
            out.append('\n').append("    ").append(file);
        }
        return out.toString();
    }

    private void renderFileConsole(String text) {
        if (fileOutput != null) {
            fileOutput.setText(text == null ? Tuils.EMPTYSTRING : text);
        }
        if (fileScroll != null) {
            fileScroll.post(() -> fileScroll.fullScroll(View.FOCUS_UP));
        }
    }

    private void closeTermuxConsole() {
        if (termuxOverlay != null) {
            termuxOverlay.setVisibility(View.GONE);
        }
        restoreHomeSuggestionsAfterTermux();
        if (termuxInput != null) {
            InputMethodManager manager = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) {
                manager.hideSoftInputFromWindow(termuxInput.getWindowToken(), 0);
            }
            termuxInput.clearFocus();
        }
        if (mTerminalAdapter != null) {
            mTerminalAdapter.focusInputEnd();
        }
    }

    private void hideHomeSuggestionsForTermux() {
        if (termuxConsoleOpen) {
            return;
        }
        termuxConsoleOpen = true;
        if (suggestionsContainer != null) {
            suggestionsVisibilityBeforeTermux = suggestionsContainer.getVisibility();
            suggestionsContainer.setVisibility(View.GONE);
        }
    }

    private void restoreHomeSuggestionsAfterTermux() {
        if (!termuxConsoleOpen) {
            return;
        }
        termuxConsoleOpen = false;
        if (suggestionsContainer != null) {
            suggestionsContainer.setVisibility(suggestionsVisibilityBeforeTermux);
        }
    }

    private void executeTermuxConsoleCommand(String rawCommand) {
        String displayCommand = rawCommand == null ? Tuils.EMPTYSTRING : rawCommand.trim();
        if (displayCommand.length() == 0) {
            return;
        }

        appendTermuxLine("$ " + displayCommand);
        String command = normalizeTermuxConsoleCommand(displayCommand);
        if (command.length() == 0) {
            appendTermuxLine("Termux console is already open. Type help for available commands.");
            return;
        }

        String lower = command.toLowerCase(Locale.US);
        if ("exit".equals(lower) || "close".equals(lower)) {
            appendTermuxLine("closing termux console.");
            closeTermuxConsole();
        } else if ("clear".equals(lower)) {
            termuxBuffer.setLength(0);
            updateTermuxOutput();
        } else if ("help".equals(lower)) {
            appendTermuxLine("help");
            appendTermuxLine("status  -> check Termux bridge readiness");
            appendTermuxLine("setup   -> show Termux bridge setup checklist");
            appendTermuxLine("open    -> launch Termux");
            appendTermuxLine("run <script> [args...] -> dispatch a Termux script");
            appendTermuxLine("clear   -> clear this console");
            appendTermuxLine("exit    -> close this console");
        } else if ("status".equals(lower)) {
            appendTermuxStatus();
        } else if ("setup".equals(lower)) {
            appendTermuxSetup();
        } else if ("open".equals(lower)) {
            openTermuxApp();
        } else if (lower.startsWith("run")) {
            runTermuxCommand(command);
        } else {
            appendTermuxLine("unknown termux console command: " + command);
            appendTermuxLine("type help for available commands.");
        }
    }

    private void appendTermuxSetup() {
        appendTermuxLine("Termux bridge setup");
        appendTermuxLine("1. Install current Termux from F-Droid/GitHub.");
        appendTermuxLine("2. In Termux, enable external app commands:");
        appendTermuxLine("   mkdir -p ~/.termux");
        appendTermuxLine("   echo 'allow-external-apps = true' >> ~/.termux/termux.properties");
        appendTermuxLine("   termux-reload-settings");
        appendTermuxLine("3. Put scripts in a stable folder, for example:");
        appendTermuxLine("   mkdir -p ~/retui");
        appendTermuxLine("   nano ~/retui/test.sh");
        appendTermuxLine("   chmod +x ~/retui/test.sh");
        appendTermuxLine("4. Create a Re:T-UI script alias:");
        appendTermuxLine("   alias -add -s test /data/data/com.termux/files/home/retui/test.sh");
        appendTermuxLine("5. Run it from Re:T-UI:");
        appendTermuxLine("   termux -run test");
        appendTermuxLine("6. For callback modules, package-scope the broadcast:");
        appendTermuxLine("   am broadcast -p com.dvil.tui_renewed -a com.dvil.tui_renewed.RETUI_CALLBACK ...");
        appendTermuxLine("7. Optional helper:");
        appendTermuxLine("   retui-token -show");
        appendTermuxLine("   create ~/retui/retui-helper.sh with retui_module/retui_output helpers.");
        appendTermuxLine("8. Script-backed module:");
        appendTermuxLine("   module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh");
        appendTermuxLine("   module -refresh server");
        appendTermuxLine("If Android asks for RUN_COMMAND permission, allow Re:T-UI and retry.");
        appendTermuxStatus();
    }

    private String normalizeTermuxConsoleCommand(String command) {
        String normalized = command == null ? Tuils.EMPTYSTRING : command.trim();
        String lower = normalized.toLowerCase(Locale.US);

        if ("termux".equals(lower)) {
            return Tuils.EMPTYSTRING;
        }

        if (lower.startsWith("termux ")) {
            normalized = normalized.substring("termux".length()).trim();
        }

        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1).trim();
        }

        return normalized;
    }

    private void appendTermuxStatus() {
        boolean installed = isPackageInstalled(TERMUX_PACKAGE);
        boolean bridgeAvailable = installed && termuxDeclaresRunCommandPermission();
        boolean permissionGranted = hasTermuxRunCommandPermission();

        appendTermuxLine("Termux installed: " + installed);
        appendTermuxLine("RunCommand bridge: " + (bridgeAvailable ? "available" : "not available"));
        appendTermuxLine("RunCommand permission: " + (permissionGranted ? "granted" : "not granted"));
        appendTermuxLine("Required Termux setting: allow-external-apps=true");
        if (!installed) {
            appendTermuxLine("Install Termux before enabling script dispatch.");
        } else if (!bridgeAvailable) {
            appendTermuxLine("This Termux build does not expose RUN_COMMAND.");
            appendTermuxLine("Install the current Termux build from F-Droid/GitHub, not the old Play Store build.");
        } else if (!permissionGranted) {
            appendTermuxLine("Grant Re:T-UI permission to run commands in Termux when prompted by Android/Termux.");
        } else {
            appendTermuxLine("Bridge prerequisites look ready for the next phase.");
        }
    }

    private void runTermuxCommand(String command) {
        List<String> parts = Tuils.splitArgs(command);
        if (parts.size() < 2) {
            appendTermuxLine("usage: run <script_path> [args...]");
            appendTermuxLine("example: run /data/data/com.termux/files/home/retui/myscript.sh");
            return;
        }

        String path = parts.get(1);
        String aliasName = path;
        path = resolveTermuxAlias(path);
        ArrayList<String> args = new ArrayList<>();
        if (parts.size() > 2) {
            args.addAll(parts.subList(2, parts.size()));
        }

        runTermuxScript(path, args, null, true, aliasName);
    }

    private boolean runTermuxScript(String path, ArrayList<String> args, String module, boolean echoToConsole) {
        return runTermuxScript(path, args, module, echoToConsole, path);
    }

    private boolean runTermuxScript(String path, ArrayList<String> args, String module, boolean echoToConsole, String aliasName) {
        if (!isPackageInstalled(TERMUX_PACKAGE)) {
            reportTermuxDispatch("Termux is not installed.", echoToConsole);
            return false;
        }

        if (!termuxDeclaresRunCommandPermission()) {
            reportTermuxDispatch("This Termux build does not expose RUN_COMMAND.", echoToConsole);
            reportTermuxDispatch("Install/update Termux from F-Droid or GitHub, then retry.", echoToConsole);
            return false;
        }

        if (!hasTermuxRunCommandPermission()) {
            requestTermuxRunCommandPermission();
            reportTermuxDispatch("RunCommand permission is not granted yet.", echoToConsole);
            reportTermuxDispatch("If Android shows a permission prompt, allow Re:T-UI and retry.", echoToConsole);
            reportTermuxDispatch("Termux must also have allow-external-apps=true.", echoToConsole);
            return false;
        }

        String dispatchPath = path;
        ArrayList<String> dispatchArgs = new ArrayList<>(args);
        if (!TextUtils.isEmpty(module)) {
            ModuleVariableManager.Materialized materialized = ModuleVariableManager.materialize(mContext, module);
            dispatchPath = TermuxBridgeManager.TERMUX_SH;
            dispatchArgs.clear();
            dispatchArgs.add("-c");
            dispatchArgs.add(buildModuleRuntimeCommand(path, module, materialized));
            dispatchArgs.add("retui-module");
            dispatchArgs.addAll(args);
        }

        Intent intent = new Intent(TERMUX_RUN_COMMAND_ACTION);
        intent.setClassName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE);
        intent.putExtra(TERMUX_RUN_COMMAND_PATH, dispatchPath);
        intent.putExtra(TERMUX_RUN_COMMAND_BACKGROUND, true);
        intent.putExtra(TERMUX_RUN_COMMAND_PENDING_INTENT, createTermuxResultPendingIntent(path, module));
        if (!dispatchArgs.isEmpty()) {
            intent.putExtra(TERMUX_RUN_COMMAND_ARGUMENTS, dispatchArgs.toArray(new String[0]));
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(mContext, intent);
            } else {
                mContext.startService(intent);
            }
            if (echoToConsole) {
                appendTermuxLine("dispatched to Termux: " + path);
                if (aliasName != null && !aliasName.equals(path)) {
                    appendTermuxLine("alias: " + aliasName + " -> " + path);
                }
                if (!args.isEmpty()) {
                    appendTermuxLine("args: " + Tuils.toPlanString(args, Tuils.SPACE));
                }
            }
            return true;
        } catch (SecurityException e) {
            reportTermuxDispatch("Termux rejected the command: permission denied.", echoToConsole);
            reportTermuxDispatch("Check allow-external-apps=true and grant RUN_COMMAND permission.", echoToConsole);
        } catch (Exception e) {
            reportTermuxDispatch("unable to dispatch Termux command: " + e.getClass().getSimpleName(), echoToConsole);
            reportTermuxDispatch("Open Termux once, then retry from this console.", echoToConsole);
        }
        return false;
    }

    private String buildModuleRuntimeCommand(String path, String module, ModuleVariableManager.Materialized materialized) {
        String runtimeDir = TermuxBridgeManager.TERMUX_HOME + "/.retui/runtime";
        String runtimePath = runtimeDir + "/" + ModuleManager.normalize(module) + ".sh";
        StringBuilder command = new StringBuilder();
        command.append("mkdir -p ").append(shellQuote(runtimeDir)).append(" && ");
        command.append("cp ").append(shellQuote(path)).append(" ").append(shellQuote(runtimePath));
        for (Map.Entry<String, String> entry : materialized.asMap().entrySet()) {
            command.append(" && sed -i ")
                    .append(shellQuote("s|" + entry.getKey() + "|" + entry.getValue().replace("|", "\\|") + "|g"))
                    .append(" ")
                    .append(shellQuote(runtimePath));
        }
        command.append(" && chmod +x ").append(shellQuote(runtimePath));
        for (Map.Entry<String, String> entry : materialized.asMap().entrySet()) {
            if (entry.getKey().startsWith("%RETUI_") && entry.getKey().endsWith("_JSON")
                    || ModuleVariableManager.TOKEN_CALENDAR_UPCOMING_MONTH.equals(entry.getKey())) {
                String name = entry.getKey().substring(1);
                command.append(" && export ")
                        .append(name)
                        .append("=")
                        .append(shellQuote(entry.getValue()));
            }
        }
        command.append(" && export RETUI_NOW=")
                .append(shellQuote(materialized.asMap().get(ModuleVariableManager.TOKEN_NOW)));
        command.append(" && exec ").append(shellQuote(runtimePath)).append(" \"$@\"");
        return command.toString();
    }

    private String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private void reportTermuxDispatch(String message, boolean echoToConsole) {
        if (echoToConsole) {
            appendTermuxLine(message);
        } else {
            Tuils.sendOutput(mContext, message);
        }
    }

    private String resolveTermuxAlias(String candidate) {
        if (candidate == null || candidate.length() == 0 || mainPack == null || mainPack.aliasManager == null) {
            return candidate;
        }

        String[] alias = mainPack.aliasManager.getAlias(candidate, false, AliasManager.SCOPE_SCRIPT);
        if (alias == null || alias.length == 0 || alias[0] == null || alias[0].trim().length() == 0) {
            alias = mainPack.aliasManager.getAlias(candidate, false);
        }

        if (alias == null || alias.length == 0 || alias[0] == null || alias[0].trim().length() == 0) {
            return candidate;
        }

        return alias[0].trim();
    }

    private PendingIntent createTermuxResultPendingIntent(String path, String module) {
        Intent resultIntent = new Intent(mContext, ohi.andre.consolelauncher.managers.termux.TermuxResultService.class);
        resultIntent.putExtra(EXTRA_TERMUX_RESULT_PATH, path);
        if (!TextUtils.isEmpty(module)) {
            resultIntent.putExtra(EXTRA_TERMUX_RESULT_MODULE, ModuleManager.normalize(module));
        }

        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        return PendingIntent.getService(mContext, (int) System.currentTimeMillis(), resultIntent, flags);
    }

    private void appendTermuxResult(Intent intent) {
        if (intent == null) {
            return;
        }

        String path = intent.getStringExtra(EXTRA_TERMUX_RESULT_PATH);
        String stdout = intent.getStringExtra(EXTRA_TERMUX_RESULT_STDOUT);
        String stderr = intent.getStringExtra(EXTRA_TERMUX_RESULT_STDERR);
        int exitCode = intent.getIntExtra(EXTRA_TERMUX_RESULT_EXIT_CODE, Integer.MIN_VALUE);
        String error = intent.getStringExtra(EXTRA_TERMUX_RESULT_ERROR);
        String debug = intent.getStringExtra(EXTRA_TERMUX_RESULT_DEBUG);
        String module = intent.getStringExtra(EXTRA_TERMUX_RESULT_MODULE);

        if (!TextUtils.isEmpty(path) && path.startsWith(TermuxBridgeManager.RESULT_PREFIX)) {
            sendTermuxBridgeResult(path, stdout, stderr, error, exitCode, debug);
            return;
        }

        if (!TextUtils.isEmpty(module)) {
            updateModuleFromTermuxResult(module, stdout, stderr, error, exitCode);
            if (termuxOverlay == null || termuxOverlay.getVisibility() != View.VISIBLE) {
                return;
            }
        }

        appendTermuxLine("result: " + (path == null ? "termux command" : path));
        if (exitCode != Integer.MIN_VALUE) {
            appendTermuxLine("exit: " + exitCode);
        }
        if (stdout != null && stdout.trim().length() > 0) {
            appendTermuxLine("stdout:");
            String trimmedStdout = stdout.trim();
            appendTermuxLine(trimmedStdout);
            appendTermuxCallbackHint(trimmedStdout);
        }
        if (stderr != null && stderr.trim().length() > 0) {
            appendTermuxLine("stderr:");
            appendTermuxLine(stderr.trim());
        }
        if (error != null && error.trim().length() > 0) {
            appendTermuxLine("error: " + error.trim());
        }
        if (debug != null && debug.trim().length() > 0) {
            appendTermuxLine("debug: " + debug.trim());
        }
        if ((stdout == null || stdout.trim().length() == 0)
                && (stderr == null || stderr.trim().length() == 0)
                && (error == null || error.trim().length() == 0)) {
            appendTermuxLine("no output returned.");
        }
    }

    private void sendTermuxBridgeResult(String path, String stdout, String stderr, String error, int exitCode, String debug) {
        String label = path.substring(TermuxBridgeManager.RESULT_PREFIX.length());
        if (label.startsWith("cd ") && exitCode == 0 && stdout != null && stdout.trim().length() > 0) {
            String newPath = stdout.trim().split("\\n")[0].trim();
            File folder = new File(newPath);
            mainPack.currentDirectory = folder;
            if (MainManager.interactive != null) {
                MainManager.interactive.addCommand("cd '" + folder.getAbsolutePath().replace("'", "'\\''") + "'");
            }
            LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_HINT));
            refreshFileConsole(true);
        }
        if (label.startsWith("fm-dirs ")) {
            String target = label.substring(8);
            if (exitCode == 0) {
                TermuxBridgeCache.putDirs(target, stdout);
                updateFileConsoleFromTermux(target, null);
            } else {
                updateFileConsoleFromTermux(target, stderr);
            }
            return;
        } else if (label.startsWith("fm-files ")) {
            String target = label.substring(9);
            if (exitCode == 0) {
                TermuxBridgeCache.putFiles(target, stdout);
                updateFileConsoleFromTermux(target, null);
            } else {
                updateFileConsoleFromTermux(target, stderr);
            }
            return;
        } else if (label.startsWith("dirs ") && exitCode == 0) {
            String target = label.substring(5);
            TermuxBridgeCache.putDirs(target, stdout);
            updateFileConsoleFromTermux(target, null);
            LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_SUGGESTIONS));
        } else if (label.startsWith("files ") && exitCode == 0) {
            String target = label.substring(6);
            TermuxBridgeCache.putFiles(target, stdout);
            updateFileConsoleFromTermux(target, null);
            LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(new Intent(UIManager.ACTION_UPDATE_SUGGESTIONS));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Termux bridge: ").append(label);
        if (exitCode != Integer.MIN_VALUE) {
            builder.append("\nexit: ").append(exitCode);
        }
        if (stdout != null && stdout.trim().length() > 0) {
            builder.append("\n").append(stdout.trim());
        }
        if (stderr != null && stderr.trim().length() > 0) {
            builder.append("\nstderr:\n").append(stderr.trim());
        }
        if (error != null && error.trim().length() > 0) {
            builder.append("\nerror: ").append(error.trim());
        }
        if (debug != null && debug.trim().length() > 0) {
            builder.append("\ndebug: ").append(debug.trim());
        }
        Tuils.sendOutput(mContext, builder.toString(), TerminalManager.CATEGORY_OUTPUT);
    }

    private void updateFileConsoleFromTermux(String path, String error) {
        if (fileOverlay == null || fileOverlay.getVisibility() != View.VISIBLE || mainPack == null || mainPack.currentDirectory == null) {
            return;
        }
        String current = mainPack.currentDirectory.getAbsolutePath();
        if (!current.equals(path)) {
            return;
        }
        if (filePath != null) {
            filePath.setText(current);
        }
        renderFileConsole(buildFileListing(TermuxBridgeCache.dirs(path), TermuxBridgeCache.files(path), error));
    }

    private void updateModuleFromTermuxResult(String module, String stdout, String stderr, String error, int exitCode) {
        String text;
        if (!TextUtils.isEmpty(stdout) && stdout.trim().length() > 0) {
            text = stdout.trim();
        } else if (!TextUtils.isEmpty(stderr) && stderr.trim().length() > 0) {
            text = "stderr:\n" + stderr.trim();
        } else if (!TextUtils.isEmpty(error) && error.trim().length() > 0) {
            text = "error: " + error.trim();
        } else if (exitCode != Integer.MIN_VALUE) {
            text = "exit: " + exitCode + "\nNo output returned.";
        } else {
            text = "No output returned.";
        }

        String id = ModuleManager.normalize(module);
        ModuleManager.setScriptText(mContext, id, text);
        if (id.equals(activeModule)) {
            showHomeModule(id);
        }
        updateModuleDockSelection();
        Tuils.sendOutput(mContext, "Module refreshed: " + id);
    }

    private void appendTermuxCallbackHint(String stdout) {
        if (stdout == null) {
            return;
        }

        String lower = stdout.toLowerCase(Locale.US);
        if (!lower.contains("retui_callback") && !lower.contains("broadcasting: intent")) {
            return;
        }

        if (!lower.contains("-p com.dvil.tui_renewed") && !lower.contains("pkg=com.dvil.tui_renewed")) {
            appendTermuxLine("callback hint: if the module did not appear, add this to the script broadcast:");
            appendTermuxLine("  -p com.dvil.tui_renewed");
        }
    }

    private boolean hasTermuxRunCommandPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || ContextCompat.checkSelfPermission(mContext, TERMUX_RUN_COMMAND_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean termuxDeclaresRunCommandPermission() {
        try {
            PackageInfo info;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                info = mContext.getPackageManager().getPackageInfo(TERMUX_PACKAGE,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            } else {
                info = mContext.getPackageManager().getPackageInfo(TERMUX_PACKAGE, PackageManager.GET_PERMISSIONS);
            }

            if (info.permissions == null) {
                return false;
            }

            for (android.content.pm.PermissionInfo permission : info.permissions) {
                if (permission != null && TERMUX_RUN_COMMAND_PERMISSION.equals(permission.name)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private void requestTermuxRunCommandPermission() {
        if (!(mContext instanceof Activity) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        ActivityCompat.requestPermissions((Activity) mContext,
                new String[] {TERMUX_RUN_COMMAND_PERMISSION},
                LauncherActivity.COMMAND_REQUEST_PERMISSION);
    }

    private void openTermuxApp() {
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launchIntent == null) {
            appendTermuxLine("Termux is not installed.");
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            mContext.startActivity(launchIntent);
            appendTermuxLine("opened Termux.");
        } catch (Exception e) {
            appendTermuxLine("unable to open Termux: " + e.getClass().getSimpleName());
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mContext.getPackageManager().getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                mContext.getPackageManager().getPackageInfo(packageName, 0);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void appendTermuxLine(String line) {
        if (termuxBuffer.length() > 0) {
            termuxBuffer.append(Tuils.NEWLINE);
        }
        termuxBuffer.append(line);
        updateTermuxOutput();
    }

    private void updateTermuxOutput() {
        if (termuxOutput != null) {
            termuxOutput.setText(termuxBuffer.toString());
        }
        if (termuxScroll != null) {
            termuxScroll.post(() -> termuxScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void styleClockOverlay(View rootView) {
        TextView timerTab = rootView.findViewById(R.id.timer_tab);
        TextView stopwatchTab = rootView.findViewById(R.id.stopwatch_tab);

        styleClockTab(timerTab, v -> {
            String message = ClockManager.getInstance(mContext).stopTimer();
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT);
        });
        styleClockTab(stopwatchTab, v -> {
            String message = ClockManager.getInstance(mContext).stopStopwatch();
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT);
        });
    }

    private void styleClockTab(TextView tab, View.OnClickListener listener) {
        if (tab == null) {
            return;
        }

        int borderColor = XMLPrefsManager.getColor(Theme.input_color);
        int bgColor = AppearanceSettings.terminalWindowBackground();
        boolean useDashed = AppearanceSettings.dashedBorders();

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Tuils.dpToPx(mContext, 3));
        if (useDashed) {
            bg.setStroke((int) Tuils.dpToPx(mContext, 1.4f), borderColor,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        bg.setColor(bgColor);

        tab.setBackground(bg);
        tab.setTextColor(borderColor);
        TextViewCompat.setCompoundDrawableTintList(tab, android.content.res.ColorStateList.valueOf(borderColor));
        tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        tab.setOnClickListener(listener);
    }

    private void makeClockTabDockable(TextView tab, String edgeKey, String fractionKey, String defaultEdge, float defaultFraction) {
        if (tab == null) {
            return;
        }

        final int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final float[] downRawX = new float[1];
        final float[] downRawY = new float[1];
        final float[] startX = new float[1];
        final float[] startY = new float[1];
        final boolean[] dragging = new boolean[1];

        tab.setOnTouchListener((view, event) -> {
            View parent = (View) view.getParent();
            if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
                return false;
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX[0] = event.getRawX();
                    downRawY[0] = event.getRawY();
                    startX[0] = view.getX();
                    startY[0] = view.getY();
                    dragging[0] = false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX[0];
                    float dy = event.getRawY() - downRawY[0];
                    if (!dragging[0] && Math.hypot(dx, dy) > touchSlop) {
                        dragging[0] = true;
                    }
                    if (dragging[0]) {
                        setClockTabPosition(view, startX[0] + dx, startY[0] + dy, parent);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                case MotionEvent.ACTION_UP:
                    view.getParent().requestDisallowInterceptTouchEvent(false);
                    if (dragging[0]) {
                        snapClockTabToNearestEdge(view, parent, edgeKey, fractionKey);
                    } else {
                        view.performClick();
                    }
                    return true;
                default:
                    return true;
            }
        });

        tab.post(() -> applyClockTabDock(tab, edgeKey, fractionKey, defaultEdge, defaultFraction));
    }

    private void applyClockTabDock(View view, String edgeKey, String fractionKey, String defaultEdge, float defaultFraction) {
        View parent = (View) view.getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.post(() -> applyClockTabDock(view, edgeKey, fractionKey, defaultEdge, defaultFraction));
            return;
        }

        String edge = preferences != null ? preferences.getString(edgeKey, defaultEdge) : defaultEdge;
        float fraction = preferences != null ? preferences.getFloat(fractionKey, defaultFraction) : defaultFraction;
        fraction = Math.max(0f, Math.min(1f, fraction));

        int margin = (int) Tuils.dpToPx(mContext, 6);
        float maxX = Math.max(margin, parent.getWidth() - view.getWidth() - margin);
        float maxY = Math.max(margin, parent.getHeight() - view.getHeight() - margin);
        float x;
        float y;

        if (CLOCK_EDGE_LEFT.equals(edge)) {
            x = margin;
            y = margin + fraction * Math.max(0, maxY - margin);
        } else if (CLOCK_EDGE_TOP.equals(edge)) {
            x = margin + fraction * Math.max(0, maxX - margin);
            y = margin;
        } else if (CLOCK_EDGE_BOTTOM.equals(edge)) {
            x = margin + fraction * Math.max(0, maxX - margin);
            y = maxY;
        } else {
            x = maxX;
            y = margin + fraction * Math.max(0, maxY - margin);
        }

        setClockTabPosition(view, x, y, parent);
    }

    private void setClockTabPosition(View view, float x, float y, View parent) {
        int margin = (int) Tuils.dpToPx(mContext, 6);
        float maxX = Math.max(margin, parent.getWidth() - view.getWidth() - margin);
        float maxY = Math.max(margin, parent.getHeight() - view.getHeight() - margin);
        view.setX(Math.max(margin, Math.min(x, maxX)));
        view.setY(Math.max(margin, Math.min(y, maxY)));
    }

    private void snapClockTabToNearestEdge(View view, View parent, String edgeKey, String fractionKey) {
        int margin = (int) Tuils.dpToPx(mContext, 6);
        float leftDistance = view.getX();
        float topDistance = view.getY();
        float rightDistance = parent.getWidth() - (view.getX() + view.getWidth());
        float bottomDistance = parent.getHeight() - (view.getY() + view.getHeight());

        String edge = CLOCK_EDGE_LEFT;
        float nearest = leftDistance;
        if (rightDistance < nearest) {
            nearest = rightDistance;
            edge = CLOCK_EDGE_RIGHT;
        }
        if (topDistance < nearest) {
            nearest = topDistance;
            edge = CLOCK_EDGE_TOP;
        }
        if (bottomDistance < nearest) {
            edge = CLOCK_EDGE_BOTTOM;
        }

        float maxX = Math.max(margin, parent.getWidth() - view.getWidth() - margin);
        float maxY = Math.max(margin, parent.getHeight() - view.getHeight() - margin);
        float fraction;
        if (CLOCK_EDGE_TOP.equals(edge) || CLOCK_EDGE_BOTTOM.equals(edge)) {
            fraction = (view.getX() - margin) / Math.max(1f, maxX - margin);
        } else {
            fraction = (view.getY() - margin) / Math.max(1f, maxY - margin);
        }
        fraction = Math.max(0f, Math.min(1f, fraction));

        if (preferences != null) {
            preferences.edit()
                    .putString(edgeKey, edge)
                    .putFloat(fractionKey, fraction)
                    .apply();
        }
        applyClockTabDock(view, edgeKey, fractionKey, edge, fraction);
    }

    private void updateClockOverlay(Intent intent) {
        TextView timerTab = mRootView.findViewById(R.id.timer_tab);
        TextView stopwatchTab = mRootView.findViewById(R.id.stopwatch_tab);
        if (timerTab == null || stopwatchTab == null) {
            return;
        }

        styleClockOverlay(mRootView);

        boolean timerRunning = intent.getBooleanExtra(ClockManager.EXTRA_TIMER_RUNNING, false);
        long timerRemaining = intent.getLongExtra(ClockManager.EXTRA_TIMER_REMAINING, 0L);
        boolean stopwatchRunning = intent.getBooleanExtra(ClockManager.EXTRA_STOPWATCH_RUNNING, false);
        long stopwatchElapsed = intent.getLongExtra(ClockManager.EXTRA_STOPWATCH_ELAPSED, 0L);

        timerTabVisible = timerRunning;
        stopwatchTabVisible = stopwatchRunning;

        if (timerRunning) {
            timerTab.setVisibility(View.VISIBLE);
            timerTab.setText(ClockManager.formatDuration(timerRemaining));
            if (!timerTabDockReady) {
                makeClockTabDockable(timerTab, PREF_TIMER_BADGE_EDGE, PREF_TIMER_BADGE_FRACTION, CLOCK_EDGE_RIGHT, 0.45f);
                timerTabDockReady = true;
            }
        } else {
            timerTab.setVisibility(View.GONE);
        }

        if (stopwatchRunning) {
            stopwatchTab.setVisibility(View.VISIBLE);
            stopwatchTab.setText(ClockManager.formatDuration(stopwatchElapsed));
            if (!stopwatchTabDockReady) {
                makeClockTabDockable(stopwatchTab, PREF_STOPWATCH_BADGE_EDGE, PREF_STOPWATCH_BADGE_FRACTION, CLOCK_EDGE_RIGHT, 0.55f);
                stopwatchTabDockReady = true;
            }
        } else {
            stopwatchTab.setVisibility(View.GONE);
        }
    }

    private void updatePomodoroOverlay(Intent intent) {
        boolean running = intent.getBooleanExtra(PomodoroManager.EXTRA_POMODORO_RUNNING, false);
        long remaining = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_REMAINING, 0L);
        long total = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_TOTAL, 0L);
        String task = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TASK);
        String typeStr = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TYPE);
        String message = intent.getStringExtra(PomodoroManager.EXTRA_MESSAGE);

        View overlay = mRootView.findViewById(R.id.pomodoro_root);
        if (overlay == null) {
            if (!running) return;
            overlay = View.inflate(mContext, R.layout.pomodoro_overlay, (ViewGroup) mRootView);
            setupPomodoroOverlay(overlay);
        }

        if (!running) {
            ((ViewGroup) mRootView).removeView(overlay);
            pomodoroOverlayVisible = false;
            mRootView.findViewById(R.id.main_container).setVisibility(View.VISIBLE);
            View terminalTray = mRootView.findViewById(R.id.terminal_tray_container);
            if (terminalTray != null) {
                terminalTray.setVisibility(View.VISIBLE);
            }
            if (message != null) {
                Tuils.sendOutput(mContext, message);
            }
            return;
        }

        pomodoroOverlayVisible = true;
        closeKeyboard();
        mRootView.findViewById(R.id.main_container).setVisibility(View.GONE);
        View terminalTray = mRootView.findViewById(R.id.terminal_tray_container);
        if (terminalTray != null) {
            terminalTray.setVisibility(View.GONE);
        }
        overlay.bringToFront();
        overlay.setElevation(Tuils.dpToPx(mContext, 128));

        TextView title = overlay.findViewById(R.id.pomodoro_title);
        TextView countdown = overlay.findViewById(R.id.pomodoro_countdown);
        TextView taskDisplay = overlay.findViewById(R.id.pomodoro_task_display);
        Button terminateBtn = overlay.findViewById(R.id.pomodoro_terminate);

        PomodoroManager.SessionType type = PomodoroManager.SessionType.valueOf(typeStr);

        overlay.setKeepScreenOn(running && type == PomodoroManager.SessionType.FOCUS);

        if (type == PomodoroManager.SessionType.FINISHED) {
            title.setText("MISSION ACCOMPLISHED");
            title.setTextColor(XMLPrefsManager.getColor(Theme.input_color));
            taskDisplay.setText("Good job! You did great!");
            countdown.setVisibility(View.GONE);
            terminateBtn.setText("EXIT SESSION");
        } else {
            countdown.setVisibility(View.VISIBLE);
            terminateBtn.setText("TERMINATE SESSION");
            if (type == PomodoroManager.SessionType.BREAK) {
                title.setText("TAKE A BREAK");
                title.setTextColor(XMLPrefsManager.getColor(Theme.input_color));
            } else {
                title.setText("FOCUS MODE ACTIVE");
                title.setTextColor(Color.RED);
            }
            taskDisplay.setText("Task: " + task);
            countdown.setText(ClockManager.formatDuration(remaining));
        }
    }

    private void setupPomodoroOverlay(View overlay) {
        TextView title = overlay.findViewById(R.id.pomodoro_title);
        TextView countdown = overlay.findViewById(R.id.pomodoro_countdown);
        TextView taskDisplay = overlay.findViewById(R.id.pomodoro_task_display);
        Button terminateBtn = overlay.findViewById(R.id.pomodoro_terminate);

        int color = XMLPrefsManager.getColor(Theme.input_color);
        int bgColor;
        int textBgColor = ColorUtils.setAlphaComponent(Color.BLACK, 160);
        if (XMLPrefsManager.getBoolean(Ui.system_wallpaper)) {
            bgColor = XMLPrefsManager.getColor(Theme.overlay_color);
        } else {
            bgColor = XMLPrefsManager.getColor(Theme.bg_color);
        }
        overlay.setBackgroundColor(bgColor);

        title.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        countdown.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        taskDisplay.setTypeface(Tuils.getTypeface(mContext));
        terminateBtn.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);

        countdown.setTextColor(color);
        taskDisplay.setTextColor(color);
        terminateBtn.setTextColor(color);

        title.setBackgroundColor(textBgColor);
        countdown.setBackgroundColor(textBgColor);
        taskDisplay.setBackgroundColor(textBgColor);

        GradientDrawable btnBg = new GradientDrawable();
        if (AppearanceSettings.dashedBorders()) {
            btnBg.setStroke((int) Tuils.dpToPx(mContext, 1.4f), color,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        btnBg.setColor(textBgColor);
        terminateBtn.setBackground(btnBg);

        terminateBtn.setOnClickListener(v -> {
            PomodoroManager manager = PomodoroManager.getInstance(mContext);
            if (manager.getCurrentType() == PomodoroManager.SessionType.FINISHED) {
                manager.stopSession();
            } else {
                TuixtDialog.showConfirm(mContext, "TERMINATE", "Do you really want to stop the focus session?", "YES", "NO", () -> {
                    manager.stopSession();
                });
            }
        });
    }

    private void playHackOverlay() {
        final View overlay = mRootView.findViewById(R.id.hack_overlay);
        final TextView hackText = mRootView.findViewById(R.id.hack_text);
        final ScrollView hackScroll = mRootView.findViewById(R.id.hack_scroll);
        if (overlay == null || hackText == null || hackScroll == null || handler == null) {
            return;
        }

        closeKeyboard();
        styleHackOverlay(mRootView);
        clearHackCallbacks();

        hackText.setText(":: breach protocol engaged ::\n\n");
        overlay.setAlpha(0f);
        overlay.setVisibility(View.VISIBLE);
        overlay.animate().alpha(1f).setDuration(120).start();

        for (int i = 0; i < hackLines.length; i++) {
            final String line = hackLines[i];
            final int delay = 120 + (i * 55);
            Runnable lineRunnable = () -> {
                hackText.append(line);
                hackText.append(Tuils.NEWLINE);
                hackScroll.post(() -> hackScroll.fullScroll(View.FOCUS_DOWN));
            };
            hackSequenceRunnables.add(lineRunnable);
            handler.postDelayed(lineRunnable, delay);
        }

        Runnable exitRunnable = () -> {
            hackText.append(Tuils.NEWLINE + "[EXIT] connection severed");
            hackScroll.post(() -> hackScroll.fullScroll(View.FOCUS_DOWN));
        };
        hackSequenceRunnables.add(exitRunnable);
        handler.postDelayed(exitRunnable, 120 + (hackLines.length * 55));

        Runnable fadeRunnable = () -> {
            overlay.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                overlay.setVisibility(View.GONE);
                overlay.setAlpha(1f);
            }).start();
        };
        hackSequenceRunnables.add(fadeRunnable);
        handler.postDelayed(fadeRunnable, 5200);
    }

    private void clearHackCallbacks() {
        if (handler == null) {
            return;
        }

        for (Runnable runnable : hackSequenceRunnables) {
            handler.removeCallbacks(runnable);
        }
        hackSequenceRunnables.clear();
        handler.removeCallbacks(hackHideRunnable);
    }

    private void dismissHackOverlay() {
        clearHackCallbacks();
        hackHideRunnable.run();
    }

    private void styleNotificationWidget(View notificationWidget) {
        ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.decorateWidget(
                notificationWidget,
                R.id.notification_widget_border,
                R.id.notification_widget_label,
                AppearanceSettings.notificationWidgetBorderColor(),
                AppearanceSettings.notificationWidgetTextColor());
        styleModuleClose(notificationWidget.findViewById(R.id.notification_widget_close));
        styleNotificationPagerButton(notificationWidget.findViewById(R.id.notification_widget_prev));
        styleNotificationPagerButton(notificationWidget.findViewById(R.id.notification_widget_next));
        applyNotificationWidgetSize(notificationWidget);
        renderNotificationRows(notificationWidget);
    }

    private void updateNotificationWidget(View rootView, List<NotificationService.Notification> notifications) {
        String previousFocusKey = notificationReplyFocusKey;
        if (ModulePromptManager.isNotificationReplyActive(mContext) && TextUtils.isEmpty(previousFocusKey)) {
            NotificationService.Notification selected = currentNotification();
            previousFocusKey = notificationKey(selected);
        }

        currentOverlayNotifications.clear();
        if (notifications != null) {
            currentOverlayNotifications.addAll(notifications);
        }
        preserveNotificationReplyFocus(previousFocusKey);
        clampNotificationIndex();

        View notificationWidget = rootView.findViewById(R.id.notification_widget);
        if (notificationWidget != null) {
            boolean visible = ModuleManager.NOTIFICATIONS.equals(activeModule);
            notificationWidget.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                styleNotificationWidget(notificationWidget);
            }
        }
        updateContextContainerVisibility(rootView);
    }

    private void renderNotificationRows(View notificationWidget) {
        LinearLayout rows = notificationWidget.findViewById(R.id.notification_rows);
        ScrollView scrollView = notificationWidget.findViewById(R.id.notification_scroll);
        if (rows == null) {
            return;
        }

        rows.removeAllViews();
        int widgetTextColor = AppearanceSettings.notificationWidgetTextColor();
        int widgetBorderColor = AppearanceSettings.notificationWidgetBorderColor();

        int maxRows = notificationCompactForKeyboard ? Math.min(1, currentOverlayNotifications.size()) : currentOverlayNotifications.size();
        if (maxRows == 0) {
            TextView row = buildNotificationRow("No notifications.", widgetTextColor, widgetBorderColor);
            rows.addView(row);
            updateNotificationPagerButtons(notificationWidget);
            if (scrollView != null) {
                scrollView.post(() -> scrollView.scrollTo(0, 0));
            }
            return;
        }
        if (ModuleManager.NOTIFICATIONS.equals(activeModule)) {
            clampNotificationIndex();
            NotificationService.Notification notification = currentOverlayNotifications.get(currentNotificationIndex);
            TextView row = buildNotificationDetailRow(notification, widgetTextColor, widgetBorderColor);
            wireNotificationOpen(row, notification);
            rows.addView(row);
        } else {
            for (int i = 0; i < maxRows; i++) {
                NotificationService.Notification notification = currentOverlayNotifications.get(i);
                TextView row = buildNotificationRow(buildNotificationLine(notification), widgetTextColor, widgetBorderColor);
                wireNotificationOpen(row, notification);
                rows.addView(row);
            }
        }
        updateNotificationPagerButtons(notificationWidget);

        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_UP));
        }
    }

    private void wireNotificationOpen(TextView row, NotificationService.Notification notification) {
        if (row == null || notification == null || notification.pendingIntent == null) {
            return;
        }
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> {
            try {
                Tuils.sendPendingIntent(mContext, notification.pendingIntent);
            } catch (PendingIntent.CanceledException e) {
                Tuils.sendOutput(Color.RED, mContext, e.toString());
            }
        });
    }

    private TextView buildNotificationRow(CharSequence text, int widgetTextColor, int widgetBorderColor) {
        TextView row = new TextView(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = notificationCompactForKeyboard ? 0 : (int) Tuils.dpToPx(mContext, 6);
        row.setLayoutParams(lp);
        row.setTypeface(Tuils.getTypeface(mContext));
        row.setTextSize(12);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int verticalPadding = (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 5 : 8);
        row.setPadding((int) Tuils.dpToPx(mContext, 10), verticalPadding, (int) Tuils.dpToPx(mContext, 10), verticalPadding);
        row.setTextColor(widgetTextColor);
        row.setText(text);
        row.setBackground(ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.getRowBackground(mContext, widgetBorderColor));
        return row;
    }

    private TextView buildNotificationDetailRow(NotificationService.Notification notification, int widgetTextColor, int widgetBorderColor) {
        TextView row = new TextView(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);
        row.setTypeface(Tuils.getTypeface(mContext));
        row.setTextSize(12);
        row.setTextColor(widgetTextColor);
        row.setText(buildNotificationDetail(notification));
        row.setSingleLine(false);
        row.setEllipsize(null);
        row.setGravity(Gravity.TOP);
        row.setMinLines(notificationCompactForKeyboard ? 1 : 4);
        row.setMaxLines(notificationCompactForKeyboard ? 2 : 6);
        row.setPadding((int) Tuils.dpToPx(mContext, 6),
                (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 5 : 10),
                (int) Tuils.dpToPx(mContext, 6),
                (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 5 : 10));
        return row;
    }

    private CharSequence buildNotificationDetail(NotificationService.Notification notification) {
        String appName = notification.appName;
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg;
        }
        String title = cleanNotificationValue(notification.title);
        String body = cleanNotificationValue(notification.body);
        String fallback = cleanNotificationValue(notification.preview);
        if (TextUtils.isEmpty(fallback)) {
            fallback = cleanNotificationValue(notification.text);
        }

        StringBuilder out = new StringBuilder();
        out.append(currentNotificationIndex + 1)
                .append(" / ")
                .append(currentOverlayNotifications.size())
                .append("    ")
                .append(appName != null ? appName : "Notification");
        if (!TextUtils.isEmpty(title)) {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append(title);
        }
        if (!TextUtils.isEmpty(body)) {
            out.append(Tuils.NEWLINE).append(body);
        } else if (!TextUtils.isEmpty(fallback)) {
            out.append(Tuils.NEWLINE).append(fallback);
        } else {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append("No readable content");
        }
        if (isCurrentNotificationReplyable()) {
            out.append(Tuils.NEWLINE).append("reply available");
        }
        return out.toString();
    }

    private String cleanNotificationValue(String value) {
        if (value == null) {
            return Tuils.EMPTYSTRING;
        }
        String clean = value.trim();
        if (clean.length() == 0 || "null".equalsIgnoreCase(clean)) {
            return Tuils.EMPTYSTRING;
        }
        if (clean.contains("%pkg") || clean.contains("%t") || clean.contains("--- null")) {
            return Tuils.EMPTYSTRING;
        }
        clean = clean.replaceAll("(?i)\\bnull\\b", "").replaceAll("\\s+---\\s*$", "").trim();
        return clean;
    }

    private CharSequence buildNotificationLine(NotificationService.Notification notification) {
        String appName = notification.appName;
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg;
        }
        String preview = notification.preview;
        if (TextUtils.isEmpty(preview)) {
            preview = notification.text;
        }
        return (appName != null ? appName : "Notification") + "  " + (preview != null ? preview : Tuils.EMPTYSTRING);
    }

    private void setNotificationWidgetCompact(View rootView, boolean compact) {
        if (notificationCompactForKeyboard == compact) {
            return;
        }

        notificationCompactForKeyboard = compact;
        View notificationWidget = rootView.findViewById(R.id.notification_widget);
        if (notificationWidget != null && notificationWidget.getVisibility() == View.VISIBLE) {
            applyNotificationWidgetSize(notificationWidget);
            renderNotificationRows(notificationWidget);
        }
    }

    private void applyNotificationWidgetSize(View notificationWidget) {
        View border = notificationWidget.findViewById(R.id.notification_widget_border);
        if (border != null) {
            ViewGroup.LayoutParams lp = border.getLayoutParams();
            lp.height = calculateNotificationWidgetHeight();
            border.setLayoutParams(lp);
        }

        ScrollView scrollView = notificationWidget.findViewById(R.id.notification_scroll);
        if (scrollView != null) {
            int topPadding = (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 12 : 14);
            scrollView.setPadding(scrollView.getPaddingLeft(), topPadding, scrollView.getPaddingRight(), scrollView.getPaddingBottom());
        }

        notificationWidget.setPadding(
                notificationWidget.getPaddingLeft(),
                notificationWidget.getPaddingTop(),
                notificationWidget.getPaddingRight(),
                (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 6 : 12)
        );
    }

    private int calculateNotificationWidgetHeight() {
        int rootHeight = mRootView != null ? mRootView.getHeight() : 0;
        if (rootHeight <= 0) {
            return (int) Tuils.dpToPx(mContext, notificationCompactForKeyboard ? 58 : 132);
        }
        float modulePercent = notificationCompactForKeyboard ? 0.08f : 0.18f;
        return Math.round(rootHeight * modulePercent);
    }

    private void styleNotificationPagerButton(View button) {
        if (!(button instanceof TextView)) return;
        TextView text = (TextView) button;
        text.setTextColor(AppearanceSettings.moduleNameTextColor());
        text.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        text.setBackground(ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.getRowBackground(
                mContext,
                AppearanceSettings.notificationWidgetBorderColor()));
    }

    private void clampNotificationIndex() {
        if (currentOverlayNotifications.isEmpty()) {
            currentNotificationIndex = 0;
            return;
        }
        if (currentNotificationIndex < 0) {
            currentNotificationIndex = 0;
        } else if (currentNotificationIndex >= currentOverlayNotifications.size()) {
            currentNotificationIndex = currentOverlayNotifications.size() - 1;
        }
    }

    private void preserveNotificationReplyFocus(String focusKey) {
        if (!ModulePromptManager.isNotificationReplyActive(mContext)) {
            notificationReplyFocusKey = null;
            return;
        }

        if (!TextUtils.isEmpty(focusKey)) {
            int index = findNotificationIndexByKey(focusKey);
            if (index >= 0) {
                currentNotificationIndex = index;
                notificationReplyFocusKey = focusKey;
                return;
            }
        }

        String pkg = ModulePromptManager.getNotificationReplyPackage(mContext);
        int packageIndex = findNotificationIndexByPackage(pkg);
        if (packageIndex >= 0) {
            currentNotificationIndex = packageIndex;
            notificationReplyFocusKey = notificationKey(currentOverlayNotifications.get(packageIndex));
        }
    }

    private int findNotificationIndexByKey(String key) {
        if (TextUtils.isEmpty(key)) return -1;
        for (int i = 0; i < currentOverlayNotifications.size(); i++) {
            if (TextUtils.equals(key, notificationKey(currentOverlayNotifications.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    private int findNotificationIndexByPackage(String pkg) {
        if (TextUtils.isEmpty(pkg)) return -1;
        for (int i = 0; i < currentOverlayNotifications.size(); i++) {
            if (TextUtils.equals(pkg, currentOverlayNotifications.get(i).pkg)) {
                return i;
            }
        }
        return -1;
    }

    private String notificationKey(NotificationService.Notification notification) {
        if (notification == null) return null;
        return safeNotificationPart(notification.pkg)
                + "|"
                + safeNotificationPart(notification.title)
                + "|"
                + safeNotificationPart(notification.body)
                + "|"
                + safeNotificationPart(notification.preview);
    }

    private String safeNotificationPart(String value) {
        return value == null ? "" : value.trim();
    }

    private void updateNotificationPagerButtons(View notificationWidget) {
        if (notificationWidget == null) return;
        boolean replyActive = ModulePromptManager.isNotificationReplyActive(mContext);
        boolean enabled = ModuleManager.NOTIFICATIONS.equals(activeModule) && currentOverlayNotifications.size() > 1 && !replyActive;
        TextView prev = notificationWidget.findViewById(R.id.notification_widget_prev);
        TextView next = notificationWidget.findViewById(R.id.notification_widget_next);
        if (prev != null) {
            prev.setVisibility(ModuleManager.NOTIFICATIONS.equals(activeModule) ? View.VISIBLE : View.GONE);
            prev.setEnabled(enabled);
            prev.setAlpha(enabled ? 1f : 0.35f);
        }
        if (next != null) {
            next.setVisibility(ModuleManager.NOTIFICATIONS.equals(activeModule) ? View.VISIBLE : View.GONE);
            next.setEnabled(enabled);
            next.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    public void nextNotificationPage() {
        if (currentOverlayNotifications.isEmpty()) return;
        if (ModulePromptManager.isNotificationReplyActive(mContext)) return;
        currentNotificationIndex = (currentNotificationIndex + 1) % currentOverlayNotifications.size();
        refreshNotificationModuleView();
    }

    public void previousNotificationPage() {
        if (currentOverlayNotifications.isEmpty()) return;
        if (ModulePromptManager.isNotificationReplyActive(mContext)) return;
        currentNotificationIndex = (currentNotificationIndex - 1 + currentOverlayNotifications.size()) % currentOverlayNotifications.size();
        refreshNotificationModuleView();
    }

    public void startCurrentNotificationReply() {
        NotificationService.Notification notification = currentNotification();
        if (notification == null) {
            Tuils.sendOutput(mContext, "No notification selected.");
            return;
        }
        if (!isCurrentNotificationReplyable()) {
            Tuils.sendOutput(mContext, "Selected notification is not replyable. Bind the app with reply -bind first.");
            return;
        }
        notificationReplyFocusKey = notificationKey(notification);
        ModulePromptManager.startNotificationReply(mContext, notification.pkg, notification.appName);
        refreshSuggestionsForActiveModule();
    }

    private boolean isCurrentNotificationReplyable() {
        NotificationService.Notification notification = currentNotification();
        ReplyManager replyManager = ReplyManager.getInstance();
        return notification != null
                && replyManager != null
                && replyManager.canReplyTo(notification.pkg);
    }

    private NotificationService.Notification currentNotification() {
        if (currentOverlayNotifications.isEmpty()) return null;
        clampNotificationIndex();
        return currentOverlayNotifications.get(currentNotificationIndex);
    }

    private void refreshNotificationModuleView() {
        if (ModuleManager.NOTIFICATIONS.equals(activeModule)) {
            showHomeModule(ModuleManager.NOTIFICATIONS);
        }
    }

    private void updateContextContainerVisibility(View rootView) {
        // Widgets are now inside terminalContainer in terminalPage
    }

    public boolean openNotificationShade() {
        try {
            @SuppressLint("WrongConstant")
            Object sbservice = mContext.getSystemService("statusbar");
            Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
            java.lang.reflect.Method expand = statusbarManager.getMethod("expandNotificationsPanel");
            expand.invoke(sbservice);
            return true;
        } catch (Exception e) {
            Tuils.sendOutput(Color.RED, mContext, e.toString());
            return false;
        }
    }

    public boolean isAppsDrawerOpen() {
        return appsDrawerRoot != null && appsDrawerRoot.getVisibility() == View.VISIBLE;
    }

    public void hideAppsDrawer() {
        if (appsDrawerRoot != null) {
            appsDrawerRoot.setVisibility(View.GONE);
        }
    }

    public void showAppsDrawer() {
        if (appsDrawerRoot == null || appsList == null) return;

        closeKeyboard();

        MainPack mainPack = mTerminalAdapter.getMainPack();
        if (mainPack == null || mainPack.appsManager == null) return;

        int drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_color);
        int borderColor = XMLPrefsManager.getColor(Theme.input_color);
        int widgetBgColor = AppearanceSettings.terminalWindowBackground();

        appsDrawerHeader.setTextColor(drawerColor);
        appsDrawerFooter.setTextColor(drawerColor);
        appsDrawerHeader.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        appsDrawerFooter.setTypeface(Tuils.getTypeface(mContext));
        appsDrawerHeader.setBackgroundColor(widgetBgColor);
        appsDrawerFooter.setBackgroundColor(widgetBgColor);

        boolean useDashed = AppearanceSettings.dashedBorders();
        int dash = AppearanceSettings.dashLength();
        int gap = AppearanceSettings.dashGap();

        try {
            GradientDrawable gd = (GradientDrawable) androidx.core.content.res.ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.apps_drawer_border, null).mutate();
            gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.moduleCornerRadius()));
            if (useDashed) {
                gd.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor, Tuils.dpToPx(mContext, dash), Tuils.dpToPx(mContext, gap));
            } else {
                gd.setStroke(0, Color.TRANSPARENT);
            }
            gd.setColor(widgetBgColor);
            appsDrawerRoot.findViewById(R.id.apps_drawer_container).setBackgroundDrawable(gd);
        } catch (Exception e) {}

        try {
            GradientDrawable gd = (GradientDrawable) androidx.core.content.res.ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.apps_drawer_header_border, null).mutate();
            if (gd != null) {
                gd.setCornerRadius(Tuils.dpToPx(mContext, AppearanceSettings.headerCornerRadius()));
                if (useDashed) {
                    gd.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor, Tuils.dpToPx(mContext, dash), Tuils.dpToPx(mContext, gap));
                } else {
                    gd.setStroke(0, Color.TRANSPARENT);
                }
                gd.setColor(widgetBgColor);
                appsDrawerHeader.setBackgroundDrawable(gd);
                appsDrawerFooter.setBackgroundDrawable(gd);
            }
        } catch (Exception e) {}

        if (appsDrawerAdapter == null) {
            appsDrawerAdapter = new AppsDrawerAdapter(mContext, drawerColor, widgetBgColor);
            appsList.setAdapter(appsDrawerAdapter);
            appsList.setOnItemClickListener((parent, view, position, id) -> {
                AppDrawerEntry entry = appsDrawerEntries.get(position);
                if (!(entry instanceof AppEntry)) {
                    return;
                }

                AppsManager.LaunchInfo app = ((AppEntry) entry).app;
                Intent intent = mainPack.appsManager.getIntent(app);
                if (intent != null) {
                    mContext.startActivity(intent);
                }
                hideAppsDrawer();
            });
            appsList.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    updateSelectedAlphaFromPosition(firstVisibleItem);
                }
            });
        } else {
            appsDrawerAdapter.setColors(drawerColor, widgetBgColor);
        }

        buildGroupTabs(mainPack.appsManager, drawerColor, borderColor, widgetBgColor);
        rebuildAppsDrawerContents(mainPack.appsManager, drawerColor, borderColor, widgetBgColor);
        appsDrawerRoot.setVisibility(View.VISIBLE);
    }

    private void buildGroupTabs(AppsManager appsManager, int drawerColor, int borderColor, int widgetBgColor) {
        if (appsGroupTabs == null) return;

        appsGroupTabs.removeAllViews();

        addGroupTab("ALL", null, drawerColor, borderColor, widgetBgColor, true);

        List<AppsManager.Group> groups = new ArrayList<>(appsManager.groups);
        Collections.sort(groups, (a, b) -> Tuils.alphabeticCompare(a.name(), b.name()));
        for (AppsManager.Group group : groups) {
            String tabLabel = group.name().length() <= 3
                    ? group.name().toUpperCase(Locale.getDefault())
                    : group.name().substring(0, 3).toUpperCase(Locale.getDefault());
            addGroupTab(tabLabel, group.name(), drawerColor, borderColor, widgetBgColor, false);
        }
    }

    private void addGroupTab(String label, String groupName, int drawerColor, int borderColor, int widgetBgColor, boolean isAll) {
        TextView tab = new TextView(mContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) Tuils.dpToPx(mContext, 34));
        lp.bottomMargin = (int) Tuils.dpToPx(mContext, 4);
        tab.setLayoutParams(lp);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding((int) Tuils.dpToPx(mContext, 2), 0, (int) Tuils.dpToPx(mContext, 2), 0);
        tab.setText(label);
        tab.setMaxLines(1);
        tab.setEllipsize(TextUtils.TruncateAt.END);
        tab.setTextSize(9.5f);
        tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
        tab.setMinWidth(0);
        tab.setMinimumWidth(0);

        boolean selected = (isAll && selectedAppsDrawerGroup == null) || (groupName != null && groupName.equals(selectedAppsDrawerGroup));
        int selectedColor = getDrawerSelectionColor(drawerColor, widgetBgColor);
        int fgColor = drawerColor;
        int bgColor = widgetBgColor;
        if (groupName != null) {
            AppsManager.Group group = findAppsGroup(groupName);
            if (group != null) {
                if (group.getForeColor() != Integer.MAX_VALUE) {
                    fgColor = group.getForeColor();
                }
                if (group.getBgColor() != Integer.MAX_VALUE) {
                    bgColor = group.getBgColor();
                }
            }
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Tuils.dpToPx(mContext, 2));
        if (AppearanceSettings.dashedBorders()) {
            bg.setStroke((int) Tuils.dpToPx(mContext, 1.5f), borderColor,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        bg.setColor(selected ? selectedColor : bgColor);
        tab.setBackground(bg);
        tab.setTextColor(selected ? widgetBgColor : fgColor);
        tab.setAlpha(1f);

        tab.setOnClickListener(v -> {
            selectedAppsDrawerGroup = groupName;
            buildGroupTabs(mainPack.appsManager, drawerColor, borderColor, widgetBgColor);
            rebuildAppsDrawerContents(mainPack.appsManager, drawerColor, borderColor, widgetBgColor);
        });

        appsGroupTabs.addView(tab);
    }

    private AppsManager.Group findAppsGroup(String name) {
        if (mainPack == null || mainPack.appsManager == null) return null;
        for (AppsManager.Group group : mainPack.appsManager.groups) {
            if (group.name().equals(name)) {
                return group;
            }
        }
        return null;
    }

    private void rebuildAppsDrawerContents(AppsManager appsManager, int drawerColor, int borderColor, int widgetBgColor) {
        List<AppsManager.LaunchInfo> visibleApps = getAppsForDrawer(appsManager);
        appsDrawerEntries.clear();
        appsDrawerAlphaPositions.clear();
        selectedAppsDrawerAlpha = null;

        String currentSection = null;
        for (AppsManager.LaunchInfo app : visibleApps) {
            String section = sectionForApp(app);
            if (!section.equals(currentSection)) {
                appsDrawerAlphaPositions.put(section, appsDrawerEntries.size());
                appsDrawerEntries.add(new SectionEntry(section));
                currentSection = section;
            }
            appsDrawerEntries.add(new AppEntry(app));
        }

        appsDrawerAdapter.notifyDataSetChanged();
        buildAlphabetTabs(drawerColor, borderColor, widgetBgColor);

        String scope = selectedAppsDrawerGroup == null ? "all" : selectedAppsDrawerGroup;
        appsDrawerHeader.setText("Applications/ [" + visibleApps.size() + "] <" + scope + ">");
        appsDrawerFooter.setText("groups " + appsManager.groups.size() + " | tabs " + appsDrawerAlphaPositions.size());
        appsList.setSelection(0);
        updateSelectedAlphaFromPosition(0);
    }

    private List<AppsManager.LaunchInfo> getAppsForDrawer(AppsManager appsManager) {
        List<AppsManager.LaunchInfo> apps = new ArrayList<>();
        List<AppsManager.LaunchInfo> shownApps = appsManager.shownApps();

        if (selectedAppsDrawerGroup == null) {
            apps.addAll(shownApps);
        } else {
            AppsManager.Group group = findAppsGroup(selectedAppsDrawerGroup);
            if (group != null) {
                List<? extends Object> members = group.members();
                for (Object member : members) {
                    if (member instanceof AppsManager.LaunchInfo && shownApps.contains(member)) {
                        apps.add((AppsManager.LaunchInfo) member);
                    }
                }
            }
        }

        Collections.sort(apps, (a, b) -> Tuils.alphabeticCompare(a.publicLabel, b.publicLabel));
        return apps;
    }

    private String sectionForApp(AppsManager.LaunchInfo app) {
        if (app == null || app.publicLabel == null || app.publicLabel.length() == 0) {
            return "#";
        }

        char first = Character.toUpperCase(app.publicLabel.charAt(0));
        if (first < 'A' || first > 'Z') {
            return "#";
        }
        return String.valueOf(first);
    }

    private void buildAlphabetTabs(int drawerColor, int borderColor, int widgetBgColor) {
        if (appsAlphaTabs == null) return;

        appsAlphaTabs.removeAllViews();
        appsDrawerAlphaViews.clear();
        for (Map.Entry<String, Integer> entry : appsDrawerAlphaPositions.entrySet()) {
            TextView tab = new TextView(mContext);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            lp.bottomMargin = (int) Tuils.dpToPx(mContext, 3);
            tab.setLayoutParams(lp);
            tab.setGravity(Gravity.CENTER);
            tab.setMinHeight(0);
            tab.setMinimumHeight(0);
            tab.setPadding(0, 0, 0, 0);
            tab.setText(entry.getKey());
            tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD);
            tab.setTextSize(9.5f);
            styleAlphaTab(tab, entry.getKey(), drawerColor, borderColor, widgetBgColor);
            tab.setOnClickListener(v -> {
                appsList.setSelection(entry.getValue());
                updateSelectedAlpha(entry.getKey());
            });
            appsDrawerAlphaViews.put(entry.getKey(), tab);
            appsAlphaTabs.addView(tab);
        }
    }

    private void styleAlphaTab(TextView tab, String letter, int drawerColor, int borderColor, int widgetBgColor) {
        boolean selected = letter != null && letter.equals(selectedAppsDrawerAlpha);
        tab.setTextColor(selected ? widgetBgColor : drawerColor);
        int selectedColor = getDrawerSelectionColor(drawerColor, widgetBgColor);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Tuils.dpToPx(mContext, 2));
        if (AppearanceSettings.dashedBorders()) {
            bg.setStroke((int) Tuils.dpToPx(mContext, 1.2f), borderColor,
                    Tuils.dpToPx(mContext, AppearanceSettings.dashLength()),
                    Tuils.dpToPx(mContext, AppearanceSettings.dashGap()));
        }
        bg.setColor(selected ? selectedColor : widgetBgColor);
        tab.setBackground(bg);
    }

    private int getDrawerSelectionColor(int drawerColor, int widgetBgColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(drawerColor, hsv);
        hsv[1] = Math.max(0f, hsv[1] * 0.55f);
        hsv[2] = Math.min(1f, 0.88f + (0.12f * hsv[2]));
        int lightBase = Color.HSVToColor(hsv);
        return ColorUtils.blendARGB(lightBase, widgetBgColor, 0.18f);
    }

    private void updateSelectedAlphaFromPosition(int position) {
        if (position < 0 || position >= appsDrawerEntries.size()) {
            return;
        }

        for (int i = position; i < appsDrawerEntries.size(); i++) {
            AppDrawerEntry entry = appsDrawerEntries.get(i);
            if (entry instanceof SectionEntry) {
                updateSelectedAlpha(((SectionEntry) entry).title);
                return;
            }
        }
    }

    private void updateSelectedAlpha(String letter) {
        if (letter == null || letter.equals(selectedAppsDrawerAlpha)) {
            return;
        }

        selectedAppsDrawerAlpha = letter;
        int drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_color);
        int borderColor = XMLPrefsManager.getColor(Theme.input_color);
        int widgetBgColor = AppearanceSettings.terminalWindowBackground();
        for (Map.Entry<String, TextView> entry : appsDrawerAlphaViews.entrySet()) {
            styleAlphaTab(entry.getValue(), entry.getKey(), drawerColor, borderColor, widgetBgColor);
        }
    }

    private abstract static class AppDrawerEntry {
        abstract int getViewType();
    }

    private static class SectionEntry extends AppDrawerEntry {
        final String title;

        SectionEntry(String title) {
            this.title = title;
        }

        @Override
        int getViewType() {
            return 0;
        }
    }

    private static class AppEntry extends AppDrawerEntry {
        final AppsManager.LaunchInfo app;

        AppEntry(AppsManager.LaunchInfo app) {
            this.app = app;
        }

        @Override
        int getViewType() {
            return 1;
        }
    }

    private class AppsDrawerAdapter extends android.widget.BaseAdapter {
        private final Context context;
        private int color;
        private int bgColor;

        AppsDrawerAdapter(Context context, int color, int bgColor) {
            this.context = context;
            this.color = color;
            this.bgColor = bgColor;
        }

        void setColors(int color, int bgColor) {
            this.color = color;
            this.bgColor = bgColor;
        }

        @Override public int getCount() { return appsDrawerEntries.size(); }
        @Override public Object getItem(int position) { return appsDrawerEntries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return appsDrawerEntries.get(position).getViewType(); }
        @Override public boolean isEnabled(int position) { return getItemViewType(position) == 1; }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            AppDrawerEntry entry = appsDrawerEntries.get(position);
            TextView tv = convertView instanceof TextView ? (TextView) convertView : new TextView(context);

            if (entry instanceof SectionEntry) {
                tv.setPadding(0, (int) Tuils.dpToPx(context, 8), 0, (int) Tuils.dpToPx(context, 6));
                tv.setTextColor(color);
                tv.setTextSize(12);
                tv.setTypeface(Tuils.getTypeface(context), Typeface.BOLD);
                tv.setBackgroundColor(Color.TRANSPARENT);
                tv.setText("[" + ((SectionEntry) entry).title + "]");
                return tv;
            }

            AppsManager.LaunchInfo app = ((AppEntry) entry).app;
            tv.setPadding((int) Tuils.dpToPx(context, 6), (int) Tuils.dpToPx(context, 12), (int) Tuils.dpToPx(context, 6), (int) Tuils.dpToPx(context, 12));
            tv.setTextColor(color);
            tv.setTextSize(16);
            tv.setTypeface(Tuils.getTypeface(context));
            tv.setBackgroundColor(Color.TRANSPARENT);
            tv.setText(app.publicLabel);
            return tv;
        }
    }

    public static int[] getListOfIntValues(String values, int length, int defaultValue) {
        int[] is = new int[length];
        values = removeSquareBrackets(values);
        String[] split = values.split(",");
        int c = 0;
        for(; c < split.length; c++) {
            try {
                is[c] = Integer.parseInt(split[c]);
            } catch (Exception e) {
                is[c] = defaultValue;
            }
        }
        while(c < split.length) is[c] = defaultValue;

        return is;
    }

    public static String[] getListOfStringValues(String values, int length, String defaultValue) {
        String[] is = new String[length];
        String[] split = values.split(",");

        int len = Math.min(split.length, is.length);
        System.arraycopy(split, 0, is, 0, len);

        while(len < is.length) is[len++] = defaultValue;

        return is;
    }

    private static Pattern sbPattern = Pattern.compile("[\\[\\]\\s]");
    private static String removeSquareBrackets(String s) {
        return sbPattern.matcher(s).replaceAll(Tuils.EMPTYSTRING);
    }

//    0 = ext hor
//    1 = ext ver
//    2 = int hor
//    3 = int ver
    private static void applyBgRect(Context context, View v, String strokeColor, String bgColor, int[] spaces, int strokeWidth, int cornerRadius, boolean dashed, int fallbackColor) {
        try {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.RECTANGLE);
            d.setCornerRadius(cornerRadius);

            boolean isTransparent = (strokeColor.startsWith("#00") && strokeColor.length() == 9);
            if(dashed) {
                try {
                    int sColor = isTransparent ? fallbackColor : Color.parseColor(strokeColor);
                    d.setStroke((int) Tuils.dpToPx(context, 1.5f), sColor,
                            Tuils.dpToPx(context, AppearanceSettings.dashLength()),
                            Tuils.dpToPx(context, AppearanceSettings.dashGap()));
                } catch (Exception e) {
                    d.setStroke(strokeWidth, Color.TRANSPARENT);
                }
            }

            applyMargins(v, spaces);

            try {
                int color = Color.parseColor(bgColor);
                if (color == Color.TRANSPARENT) {
                    color = AppearanceSettings.terminalWindowBackground();
                }
                d.setColor(color);
            } catch (Exception e) {
                d.setColor(AppearanceSettings.terminalWindowBackground());
            }
            v.setBackgroundDrawable(d);
        } catch (Exception e) {
            Tuils.toFile(e);
            Tuils.log(e);
        }
    }

    private static void applyMargins(View v, int[] margins) {
        v.setPadding(margins[2], margins[3], margins[2], margins[3]);

        ViewGroup.LayoutParams params = v.getLayoutParams();
        if(params instanceof RelativeLayout.LayoutParams) {
            ((RelativeLayout.LayoutParams) params).setMargins(margins[0], margins[1], margins[0], margins[1]);
        } else if(params instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) params).setMargins(margins[0], margins[1], margins[0], margins[1]);
        }
    }

    private static void applyShadow(TextView v, String color, int x, int y, float radius) {
        if(!(color.startsWith("#00") && color.length() == 9)) {
            try {
                v.setShadowLayer(radius, x, y, Color.parseColor(color));
                v.setTag(OutlineTextView.SHADOW_TAG);
            } catch (Exception e) {
                // Fallback to transparent if color is invalid
                v.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            }
        }
    }

    public void dispose() {
        if(handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        if(suggestionsManager != null) suggestionsManager.dispose();
        if(notesManager != null) notesManager.dispose(mContext);
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).unregisterReceiver(receiver);
        try {
            mContext.getApplicationContext().unregisterReceiver(receiver);
        } catch (Exception ignored) {}
        Tuils.unregisterBatteryReceiver(mContext);

        Tuils.cancelFont();
    }

    public void openKeyboard() {
        activateTerminalInput(true);
        if (mTerminalAdapter != null) {
            imm.showSoftInput(mTerminalAdapter.getInputView(), InputMethodManager.SHOW_FORCED);
        }
    }

    public void closeKeyboard() {
        imm.hideSoftInputFromWindow(mTerminalAdapter.getInputWindowToken(), 0);
        if (mTerminalAdapter.getInputView() instanceof EditText) {
            EditText terminalInput = (EditText) mTerminalAdapter.getInputView();
            terminalInput.setCursorVisible(false);
            terminalInput.setShowSoftInputOnFocus(false);
            if (terminalInput instanceof OutlineEditText) {
                ((OutlineEditText) terminalInput).setIdleCursorVisible(true);
            }
            terminalInput.clearFocus();
        }
    }

    public void onStart(boolean openKeyboardOnStart) {
        activateTerminalInput(openKeyboardOnStart);
    }

    public void setInput(String s) {
        if (s == null)
            return;

        if (mTerminalAdapter == null) {
            pendingInputs.add(s);
            return;
        }

        mTerminalAdapter.setInput(s);
        mTerminalAdapter.focusInputEnd();
    }

    public void setHint(String hint) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter.setHint(hint);
        }
    }

    public void resetHint() {
        if (mTerminalAdapter != null) {
            mTerminalAdapter.setDefaultHint();
        }
    }

    public void setOutput(CharSequence s, int category) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter.setOutput(s, category);
        } else {
            pendingOutputs.add(new OutputHolder(s, category));
        }
    }

    public void setOutput(int color, CharSequence output) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter.setOutput(color, output);
        } else {
            pendingOutputs.add(new OutputHolder(color, output));
        }
    }

    public void disableSuggestions() {
        if(suggestionsManager != null) suggestionsManager.disable();
    }

    public void enableSuggestions() {
        if(suggestionsManager != null) suggestionsManager.enable();
    }

    public void onBackPressed() {
        if (pomodoroOverlayVisible) {
            return;
        }
        if (isAppsDrawerOpen()) {
            hideAppsDrawer();
            return;
        }
        if (!landscapeLayoutActive && terminalTrayExpanded) {
            setTerminalTrayExpanded(false);
            return;
        }
        if (mTerminalAdapter != null) {
            mTerminalAdapter.onBackPressed();
        }
    }

    public void focusTerminal() {
        activateTerminalInput(false);
    }

    public void activateTerminalInput(boolean showSoftKeyboard) {
        if (mTerminalAdapter == null) {
            return;
        }

        View input = mTerminalAdapter.getInputView();
        if (input instanceof EditText) {
            EditText terminalInput = (EditText) input;
            terminalInput.setShowSoftInputOnFocus(showSoftKeyboard);
            terminalInput.setCursorVisible(true);
            if (terminalInput instanceof OutlineEditText) {
                ((OutlineEditText) terminalInput).setIdleCursorVisible(false);
            }
        }

        mTerminalAdapter.requestInputFocus();
        mTerminalAdapter.focusInputEnd();
        if (showSoftKeyboard) {
            input.post(() -> imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    public void pause() {
        if (mTerminalAdapter != null) {
            closeKeyboard();
        }
        if (handler != null) {
            handler.removeCallbacks(musicTimeRunnable);
            handler.removeCallbacks(eventsRefreshRunnable);
            handler.removeCallbacks(luaWidgetTickRunnable);
            handler.removeCallbacks(fontRefreshRunnable);
        }
        setMusicVisualizerPlaying(false);
        View pomodoroOverlay = mRootView != null ? mRootView.findViewById(R.id.pomodoro_root) : null;
        if (pomodoroOverlay != null) {
            pomodoroOverlay.setKeepScreenOn(false);
        }

        if (ramManager != null) ramManager.stop();
        if (batteryManager != null) batteryManager.stop();
        if (storageManager != null) storageManager.stop();
        if (networkManager != null) networkManager.stop();
        if (tuiTimeManager != null) tuiTimeManager.stop();
        if (unlockManager != null) unlockManager.stop();
    }

    private void setMusicVisualizerPlaying(boolean playing) {
        MusicVisualizerView visualizer = mRootView != null ? mRootView.findViewById(R.id.music_visualizer) : null;
        if (visualizer != null) {
            visualizer.setPlaying(playing);
        }
    }

    private void scheduleInternalMusicTickerIfNeeded() {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(musicTimeRunnable);
        if (!"internal".equals(activeMusicSource)) {
            return;
        }
        View musicWidget = mRootView != null ? mRootView.findViewById(R.id.music_widget) : null;
        if (musicWidget != null
                && musicWidget.getVisibility() == View.VISIBLE
                && mainPack != null
                && mainPack.player != null
                && mainPack.player.isPlaying()) {
            handler.post(musicTimeRunnable);
        }
    }

    public void resume() {
        if (handler == null) {
            return;
        }
        setMusicVisualizerPlaying(lastMusicPlaying);
        scheduleInternalMusicTickerIfNeeded();
        scheduleEventsRefreshIfNeeded();
        scheduleTypefaceRefreshes();

        if (ramManager != null) ramManager.start();
        if (batteryManager != null) batteryManager.start();
        if (storageManager != null) storageManager.start();
        if (networkManager != null) networkManager.start();
        if (tuiTimeManager != null) tuiTimeManager.start();
        if (unlockManager != null) unlockManager.start();

        // Refresh Pomodoro overlay on resume
        PomodoroManager pomodoro = PomodoroManager.getInstance(mContext);
        if (pomodoro.isRunning()) {
            Intent intent = new Intent(PomodoroManager.ACTION_POMODORO_STATE);
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_RUNNING, true);
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_REMAINING, pomodoro.getRemainingMillis());
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TOTAL, pomodoro.getTotalDuration());
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TASK, pomodoro.getTaskName());
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TYPE, pomodoro.getCurrentType().name());
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_CYCLE, pomodoro.getCompletedFocuses());
            updatePomodoroOverlay(intent);
        }
    }

    public void onConfigurationChanged(Configuration newConfig) {
        applyResponsiveLandscapeLayout(newConfig);
        if (mTerminalAdapter != null) {
            mTerminalAdapter.scrollToEnd();
            mTerminalAdapter.focusInputEnd();
        }
    }

    public void scheduleTypefaceRefreshes() {
        if (mRootView == null) {
            return;
        }

        mRootView.post(this::refreshLauncherTypeface);

        if (handler != null) {
            handler.removeCallbacks(fontRefreshRunnable);
            handler.postDelayed(fontRefreshRunnable, 120);
            handler.postDelayed(fontRefreshRunnable, 360);
            handler.postDelayed(fontRefreshRunnable, 900);
        }
    }

    private void refreshLauncherTypeface() {
        Typeface typeface = Tuils.getTypeface(mContext);
        if (typeface == null || mRootView == null) {
            return;
        }

        applyTypefaceRecursively(mRootView, typeface);

        if (mTerminalAdapter != null) {
            mTerminalAdapter.refreshTypeface();
        }

        TextView asciiView = getLabelView(Label.ascii);
        if (asciiView != null) {
            asciiView.setTypeface(Typeface.MONOSPACE);
        }
    }

    private void applyTypefaceRecursively(View view, Typeface typeface) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface current = textView.getTypeface();
            int style = current != null ? current.getStyle() : Typeface.NORMAL;
            if (textView.getId() == R.id.module_text_body && containsProgressBlockGlyphs(textView.getText())) {
                textView.setTypeface(Typeface.MONOSPACE, style);
            } else {
                textView.setTypeface(typeface, style);
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTypefaceRecursively(group.getChildAt(i), typeface);
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return v.onTouchEvent(event);
    }

    public OnRedirectionListener buildRedirectionListener() {
        return new OnRedirectionListener() {
            @Override
            public void onRedirectionRequest(final RedirectCommand cmd) {
                ((Activity) mContext).runOnUiThread(() -> {
                    mTerminalAdapter.setHint(mContext.getString(cmd.getHint()));
                    disableSuggestions();
                });
            }

            @Override
            public void onRedirectionEnd(RedirectCommand cmd) {
                ((Activity) mContext).runOnUiThread(() -> {
                    mTerminalAdapter.setDefaultHint();
                    enableSuggestions();
                });
            }

            @Override
            public void onRedirection(String name, String value) {
                if (name.equals(ACTION_CLEAR)) {
                    mTerminalAdapter.clear();
                } else if (name.equals(ACTION_HACK)) {
                    playHackOverlay();
                } else if (name.equals(ACTION_WEATHER)) {
                    if (weatherManager != null) weatherManager.updateWeather();
                } else if (name.equals(ACTION_WEATHER_GOT_LOCATION)) {
                    if (weatherManager != null) weatherManager.setLocation(Double.parseDouble(value.split(",")[0]), Double.parseDouble(value.split(",")[1]));
                } else if (name.equals(ACTION_WEATHER_DELAY)) {
                    if (weatherManager != null) weatherManager.setDelay(Integer.parseInt(value));
                } else if (name.equals(ACTION_WEATHER_MANUAL_UPDATE)) {
                    if (weatherManager != null) weatherManager.updateWeather();
                }
            }
        };
    }

    private void onLock() {
        if (clearOnLock) {
            mTerminalAdapter.clear();
        }
    }
}
