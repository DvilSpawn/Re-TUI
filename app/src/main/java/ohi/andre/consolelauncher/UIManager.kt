package ohi.andre.consolelauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent.CanceledException
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnScrollChangedListener
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.TextViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.raw.tbridge
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.ClockManager
import ohi.andre.consolelauncher.managers.PomodoroManager
import ohi.andre.consolelauncher.managers.PomodoroManager.SessionType
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.slot
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.file.FileBackendManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ModuleDockButtonFactory
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager
import ohi.andre.consolelauncher.managers.modules.ModuleVariableManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.cyberdeckMode
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorderCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleBodyTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBackgroundColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleHeaderTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleNameTextColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.musicWidgetBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.musicWidgetTextColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.notificationWidgetBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.notificationWidgetTextColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputHeaderTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputTrayMaxHeightDp
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalHeaderBackground
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalHeaderTabBackground
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalWindowBackground
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getBoolean
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getColor
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.getInt
import ohi.andre.consolelauncher.managers.settings.MusicSettings.autoShowWidget
import ohi.andre.consolelauncher.managers.settings.MusicSettings.preferredPackage
import ohi.andre.consolelauncher.managers.settings.MusicSettings.showWidget
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showTerminal
import ohi.andre.consolelauncher.managers.status.BatteryManager
import ohi.andre.consolelauncher.managers.status.NetworkManager
import ohi.andre.consolelauncher.managers.status.NotesManager
import ohi.andre.consolelauncher.managers.status.RamManager
import ohi.andre.consolelauncher.managers.status.StatusUpdateListener
import ohi.andre.consolelauncher.managers.status.StorageManager
import ohi.andre.consolelauncher.managers.status.TimeManager
import ohi.andre.consolelauncher.managers.status.UnlockManager
import ohi.andre.consolelauncher.managers.status.WeatherManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.dirs
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.files
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.putDirs
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.putFiles
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache.shouldRequest
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.createResultPendingIntent
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.requestRunCommandPermissionIfPossible
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceInputEditText
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceSocketClient
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetEngine.UpdateListener
import ohi.andre.consolelauncher.managers.widgets.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.AllowEqualsSequence
import ohi.andre.consolelauncher.tuils.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.CyberpunkBackdropDrawable
import ohi.andre.consolelauncher.tuils.CyberpunkIconFrameDrawable
import ohi.andre.consolelauncher.tuils.DuoSwitchButtonFactory
import ohi.andre.consolelauncher.tuils.MusicVisualizerView
import ohi.andre.consolelauncher.tuils.OutlineEditText
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.StableHorizontalScrollView
import ohi.andre.consolelauncher.tuils.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.TerminalTrayToggleView
import ohi.andre.consolelauncher.tuils.TuiWidgetDecorator
import ohi.andre.consolelauncher.tuils.TuiWidgetDecorator.decorateWidget
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Arrays
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.location.Location
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.widget.ProgressBar
import android.text.SpannableString
import android.view.GestureDetector.OnDoubleTapListener
import android.view.ViewParent
import ohi.andre.consolelauncher.managers.FileManager
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Map
import java.lang.reflect.Method
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.MusicSettings
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import androidx.annotation.NonNull
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate
import org.json.JSONArray
import org.json.JSONObject

class UIManager(
    context: Context,
    rootView: ViewGroup,
    mainPack: MainPack,
    canApplyTheme: Boolean,
    executer: CommandExecuter
) : OnTouchListener {
    enum class Label {
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

    private val RAM_DELAY = 3000
    private val TIME_DELAY = 1000
    private val STORAGE_DELAY = 60 * 1000

    private var ramManager: RamManager? = null
    private var batteryManager: BatteryManager? = null
    private var storageManager: StorageManager? = null
    private var networkManager: NetworkManager? = null
    private var tuiTimeManager: TimeManager? = null
    private var unlockManager: UnlockManager? = null

    protected var mContext: Context = context
    protected var mainPack: MainPack = mainPack

    private var handler: Handler?

    private var policy: DevicePolicyManager?
    private var component: ComponentName?
    private var swipeDownNotifications: Boolean
    private var swipeUpAppsDrawer: Boolean
    private var gestureDetector: GestureDetectorCompat? = null
    private var appsDrawerRoot: View?
    private val appsDrawerContainer: View?
    private val appsDrawerContainerBaseMargins = IntArray(4)
    private val appsList: ListView?
    private val appsGroupTabs: LinearLayout?
    private val appsAlphaTabs: LinearLayout?
    private val appsDrawerHeader: TextView
    private val appsDrawerFooter: TextView
    private var appsDrawerAdapter: AppsDrawerAdapter? = null
    private val appsDrawerEntries: MutableList<AppDrawerEntry> = ArrayList<AppDrawerEntry>()
    private val appsDrawerAlphaPositions = LinkedHashMap<String?, Int?>()
    private val appsDrawerAlphaViews = LinkedHashMap<String?, TextView?>()
    private val currentOverlayNotifications = ArrayList<NotificationService.Notification>()
    private var currentNotificationIndex = 0
    private var notificationReplyFocusKey: String? = null
    private val termuxBuffer = StringBuilder()
    private var notificationCompactForKeyboard = false
    private var timerTabVisible = false
    private var stopwatchTabVisible = false
    private var timerTabDockReady = false
    private var stopwatchTabDockReady = false
    var isPomodoroOverlayVisible: Boolean = false
        private set
    private var selectedAppsDrawerGroup: String? = null
    private var selectedAppsDrawerAlpha: String? = null
    private var termuxOverlay: View? = null
    private var termuxOverlayBasePaddingLeft = 0
    private var termuxOverlayBasePaddingTop = 0
    private var termuxOverlayBasePaddingRight = 0
    private var termuxOverlayBasePaddingBottom = 0
    private var overlayDisplayMarginLeft = 0
    private var overlayDisplayMarginTop = 0
    private var overlayDisplayMarginRight = 0
    private var overlayDisplayMarginBottom = 0
    private var termuxWindowBorder: View? = null
    private var termuxWindowLabel: TextView? = null
    private var termuxClose: TextView? = null
    private var termuxGrid: TerminalGridView? = null
    private var termuxOutput: TextView? = null
    private var termuxRichOutput: LinearLayout? = null
    private var termuxPrefix: TextView? = null
    private var termuxInput: EditText? = null
    private var termuxScroll: ScrollView? = null
    private var termuxInputGroup: View? = null
    private var termuxOutputPanel: View? = null
    private var termuxOutputLabel: TextView? = null
    private var termuxActionsScroll: HorizontalScrollView? = null
    private var termuxActions: LinearLayout? = null
    private var termuxTools: View? = null
    private var termuxKeysRowOne: ViewGroup? = null
    private var termuxKeysRowTwo: ViewGroup? = null
    private val termuxCommandHistory = ArrayList<String?>()
    private var termuxHistoryCursor = -1
    private var termuxHistoryDraft = ""
    private var termuxWorkingDirectory = TermuxBridgeManager.TERMUX_HOME
    private var termuxAppSession: TermuxAppManager.TermuxApp? = null
    private var termuxAppLastStatus: String? = null
    private var termuxAppRefreshGeneration = 0
    private var termuxAppDispatchSequence = 0
    private var termuxAppAcceptedSequence = 0
    private var termuxAppWatchUntilMs = 0L
    private var termuxAppLastFrameText: String? = null
    private var termuxAppLastCols = 80
    private var termuxAppLastRows = 24
    private var termuxAppMeasuredLineHeight = 0
    private val termuxKeySlots = ArrayList<TextView>()
    private var termuxCtrlKey: TextView? = null
    private var termuxAltKey: TextView? = null
    private var termuxShiftKey: TextView? = null
    private var termuxCtrlPending = false
    private var termuxAltPending = false
    private var termuxShiftPending = false
    private var termuxFnKeyMode = false
    private var termuxKeyModeAnimating = false
    private var termuxKeySwipeConsumed = false
    private var termuxKeyTouchStartX = 0f
    private var termuxKeyTouchStartY = 0f
    private var termuxSuppressInputWatcher = false
    private var termuxInsertedStart = -1
    private var termuxInsertedText: String? = null
    private var luaAppId: String? = null
    private var luaAppEngine: LuaWidgetEngine? = null
    private var luaAppLastResult: LuaWidgetEngine.RenderResult? = null
    private var luaAppLastStatus: String? = null
    private var luaAppTickGeneration = 0
    private val termuxAnsiPattern = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]")
    private val termuxWorkspaceBuffer = StringBuilder()
    private var termuxWorkspaceRoot: View? = null
    private var termuxWorkspaceBorder: View? = null
    private var termuxWorkspaceLabel: TextView? = null
    private var termuxWorkspaceGrid: TerminalGridView? = null
    private var termuxWorkspaceOutput: TextView? = null
    private var termuxWorkspacePrefix: TextView? = null
    private var termuxWorkspaceInput: EditText? = null
    private var termuxWorkspaceSend: TextView? = null
    private var termuxWorkspaceScroll: ScrollView? = null
    private var termuxWorkspaceInputGroup: View? = null
    private var termuxWorkspaceOutputPanel: View? = null
    private var termuxWorkspaceOutputLabel: TextView? = null
    private var termuxWorkspaceTools: View? = null
    private var termuxWorkspaceKeysRowOne: ViewGroup? = null
    private var termuxWorkspaceKeysRowTwo: ViewGroup? = null
    private var termuxWorkspaceBasePaddingLeft = 0
    private var termuxWorkspaceBasePaddingTop = 0
    private var termuxWorkspaceBasePaddingRight = 0
    private var termuxWorkspaceBasePaddingBottom = 0
    private var termuxWorkspaceSocketClient: TermuxWorkspaceSocketClient? = null
    private var termuxWorkspaceSocketName: String? = null
    private var termuxWorkspaceSocketToken: String? = null
    private var termuxWorkspaceLastCols = 80
    private var termuxWorkspaceLastRows = 24
    private var termuxWorkspaceLastRenderedFrameKey: String? = null
    private var termuxWorkspaceMeasuredLineHeight = 0
    private var termuxWorkspaceDispatchSequence = 0
    private var termuxWorkspaceAcceptedSequence = 0
    private var termuxWorkspaceImeResizeGeneration = 0
    private var termuxWorkspaceConfigurationRefreshGeneration = 0
    private var termuxWorkspaceLocalOutputHoldUntilMs = 0L
    private var termuxWorkspaceTouchStartX = 0f
    private var termuxWorkspaceTouchStartY = 0f
    private var termuxWorkspaceKeyTouchStartX = 0f
    private var termuxWorkspaceKeyTouchStartY = 0f
    private var termuxWorkspaceKeySwipeConsumed = false
    private var termuxWorkspaceKeyModeAnimating = false
    private val termuxWorkspaceKeySlots = ArrayList<TextView>()
    private var termuxWorkspaceCtrlKey: TextView? = null
    private var termuxWorkspaceAltKey: TextView? = null
    private var termuxWorkspaceShiftKey: TextView? = null
    private var termuxWorkspaceCtrlPending = false
    private var termuxWorkspaceAltPending = false
    private var termuxWorkspaceShiftPending = false
    private var termuxWorkspaceFnKeyMode = false
    private var termuxWorkspaceDirectInput = true
    private var termuxWorkspaceLocalCommandMode = false
    private val termuxWorkspaceLocalCommandBuffer = StringBuilder()
    private var termuxWorkspaceSuppressInputWatcher = false
    private var termuxWorkspaceInsertedStart = -1
    private var termuxWorkspaceInsertedText: String? = null
    private var fileOverlay: View? = null
    private var fileOverlayBasePaddingLeft = 0
    private var fileOverlayBasePaddingTop = 0
    private var fileOverlayBasePaddingRight = 0
    private var fileOverlayBasePaddingBottom = 0
    private var fileWindowBorder: View? = null
    private var fileWindowLabel: TextView? = null
    private var fileClose: TextView? = null
    private var filePath: TextView? = null
    private var fileOutput: TextView? = null
    private var filePrefix: TextView? = null
    private var fileInput: EditText? = null
    private var fileScroll: ScrollView? = null
    private var fileInputGroup: View? = null
    private var fileTools: View? = null
    private var fileRefresh: TextView? = null
    private var fileUp: TextView? = null
    private var fileOpen: TextView? = null
    private var filePaste: TextView? = null
    private val lastFileListingPath = ""
    private var suggestionsContainer: View? = null
    private var suggestionsVisibilityBeforeTermux = View.VISIBLE
    private var termuxConsoleOpen = false
    private var termuxFocusCapturePending = false
    private var launcherChromeHiddenForSurface = false
    private var restoreLauncherChromeOnResume = false
    private var launcherChromeMainVisibility = View.VISIBLE
    private var launcherChromeTrayVisibility = View.VISIBLE
    private var launcherChromeLandscapeVisibility = View.GONE
    private var termuxWorkspaceChromeActive = false
    private var termuxWorkspaceHeaderVisibility = View.VISIBLE
    private var termuxWorkspaceTrayVisibility = View.VISIBLE
    private var terminalTrayContainer: View? = null
    private var terminalContainer: ViewGroup? = null
    private var terminalOutputBorder: View? = null
    private var terminalTrayToggle: TextView? = null
    private var crtOverlayDrawable: CrtOverlayDrawable? = null
    private var hackOverlay: View? = null
    private var hackOverlayBasePaddingLeft = 0
    private var hackOverlayBasePaddingTop = 0
    private var hackOverlayBasePaddingRight = 0
    private var hackOverlayBasePaddingBottom = 0
    private var terminalTrayExpanded = false
    private var keyboardVisible = false
    private var hasLastLayoutState = false
    private var lastObservedRootHeight = -1
    private var moduleDockScroll: View? = null
    private var moduleDock: LinearLayout? = null
    private val moduleDockButtons = LinkedHashMap<String?, TextView?>()
    private var styledModuleDockSelection: String? = null
    private var pendingModuleDockScrollX = -1
    private var lastModuleDockScrollX = 0
    private var moduleSuggestionsScroll: HorizontalScrollView? = null
    private var moduleSuggestionsGroup: LinearLayout? = null
    private val luaWidgetEngines = HashMap<String?, LuaWidgetEngine?>()
    private var bundledLuaSamplesPruned = false
    private var activeModule: String? = ""
    private var lastClockStateIntent: Intent? = null
    private var lastPomodoroStateIntent: Intent? = null
    private var lastMusicSong: String? = null
    private var lastMusicSinger: String? = null
    private var lastMusicPlaying = false
    private var lastMusicAppPackage: String? = null

    var preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, 0)

    private val imm: InputMethodManager
    private var mTerminalAdapter: TerminalManager? = null
    private val pendingInputs: MutableList<String?> = ArrayList<String?>()

    private class OutputHolder {
        var output: CharSequence?
        var category: Int = 0
        var color: Int? = null

        internal constructor(output: CharSequence?, category: Int) {
            this.output = output
            this.category = category
        }

        internal constructor(color: Int, output: CharSequence?) {
            this.color = color
            this.output = output
        }
    }

    private val pendingOutputs: MutableList<OutputHolder> = ArrayList<OutputHolder>()
    var mediumPercentage: Int = 0
    var lowPercentage: Int = 0
    var batteryFormat: String? = null

    var hideToolbarNoInput: Boolean = false
    var toolbarView: View? = null

    //    never access this directly, use getLabelView
    private var labelViews = arrayOfNulls<TextView>(Label.entries.size)

    private val labelIndexes = FloatArray(labelViews.size)
    private val labelSizes = IntArray(labelViews.size)
    private val labelTexts = arrayOfNulls<CharSequence>(labelViews.size)

    private var asciiContent: String? = null
    private var asciiColor = 0

    private val statusUpdateListener: StatusUpdateListener =
        StatusUpdateListener { l: Label?, s: CharSequence? -> this.updateText(l!!, s) }

    private fun getLabelView(l: Label): TextView? {
        val index = labelIndexes[l.ordinal].toInt()
        if (index < 0 || index >= labelViews.size) {
            return null
        }
        return labelViews[index]
    }

    private var notesMaxLines = 0
    private var tuiNotesManager: NotesManager? = null
    private var notesManager: ohi.andre.consolelauncher.managers.NotesManager?

    private var activeMusicSource: String? = "internal"

    private val musicTimeRunnable: Runnable = object : Runnable {
        override fun run() {
            var shouldContinue = false
            if ("internal" == activeMusicSource) {
                val musicWidget = mRootView!!.findViewById<View?>(R.id.music_widget)
                if (musicWidget != null && musicWidget.getVisibility() == View.VISIBLE) {
                    if (mainPack != null && mainPack.player != null && mainPack.player!!.isPlaying()) {
                        shouldContinue = true
                        val intent: Intent = Intent(ACTION_MUSIC_CHANGED)
                        val index = mainPack.player!!.songIndex
                        if (index != -1) {
                            val song = mainPack.player!!.get(index)
                            if (song != null) {
                                intent.putExtra(SONG_TITLE, song.getTitle())
                                intent.putExtra(SONG_SINGER, song.getSinger())
                            }
                        }
                        intent.putExtra(SONG_DURATION, mainPack.player!!.getDuration())
                        intent.putExtra(SONG_POSITION, mainPack.player!!.getCurrentPosition())
                        intent.putExtra(MUSIC_PLAYING, mainPack.player!!.isPlaying())
                        intent.putExtra("source", "internal")
                        LocalBroadcastManager.getInstance(mContext!!).sendBroadcast(intent)
                    }
                }
            }
            if (shouldContinue) {
                handler!!.postDelayed(this, 1000)
            }
        }
    }

    private val eventsRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            if (ModuleManager.EVENTS != activeModule) {
                return
            }
            val source = ModuleManager.getModuleSource(mContext, ModuleManager.EVENTS)
            if (!ModuleManager.isLauncherSource(source)) {
                return
            }
            refreshLauncherModule(ModuleManager.EVENTS, source, false)
            scheduleEventsRefreshIfNeeded()
        }
    }
    private val luaWidgetTickRunnable: Runnable = object : Runnable {
        override fun run() {
            tickActiveLuaWidget()
        }
    }

    private val fontRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            refreshLauncherTypeface()
        }
    }

    private val hackHideRunnable: Runnable = object : Runnable {
        override fun run() {
            val overlay = mRootView!!.findViewById<View?>(R.id.hack_overlay)
            if (overlay != null) {
                overlay.animate().cancel()
                overlay.setVisibility(View.GONE)
                overlay.setAlpha(1f)
            }
        }
    }

    private val hackLines: Array<String> = arrayOf(
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
        "[OVRD] preserving user music module because priorities",
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
    )
    private val hackSequenceRunnables = ArrayList<Runnable>()

    private var weatherDelay = 0

    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var location: String? = null
    private val fixedLocation = false

    private var weatherColor = 0
    var showWeatherUpdate: Boolean = false
    private var weatherManager: WeatherManager? = null
    private var lastWeatherText: CharSequence? = null
    private var lastWeatherUpdateMillis: Long = 0

    //    you need to use labelIndexes[i]
    private fun updateText(l: Label, s: CharSequence?) {
        labelTexts[l.ordinal] = s

        val base = labelIndexes[l.ordinal].toInt()
        if (base < 0 || base >= labelViews.size || labelViews[base] == null) {
            return
        }

        val indexs: MutableList<Float> = ArrayList()
        for (count in Label.entries.toTypedArray().indices) {
            if (labelIndexes[count].toInt() == base && labelTexts[count] != null) indexs.add(
                labelIndexes[count]
            )
        }
        //        now I'm sorting the labels on the same line for decimals (2.1, 2.0, ...)
        Collections.sort(indexs)

        var sequence: CharSequence = Tuils.EMPTYSTRING

        for (c in indexs.indices) {
            val i: Float = indexs.get(c)!!

            for (a in Label.entries.toTypedArray().indices) {
                if (i == labelIndexes[a] && labelTexts[a] != null) sequence =
                    TextUtils.concat(sequence, labelTexts[a])
            }
        }

        if (sequence.length == 0) labelViews[base]!!.setVisibility(View.GONE)
        else {
            labelViews[base]!!.setVisibility(View.VISIBLE)
            labelViews[base]!!.setText(sequence)
        }
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PagerViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
            val inflater = LayoutInflater.from(parent.getContext())
            val view: View = if (viewType == TERMUX_WORKSPACE_PAGE_INDEX) {
                inflater.inflate(R.layout.termux_workspace_page, parent, false)
            } else {
                inflater.inflate(R.layout.home_widgets_page, parent, false)
            }
            if (viewType == TERMUX_WORKSPACE_PAGE_INDEX) {
                setupTermuxWorkspacePage(view)
            } else {
                setupHomeWidgetsPage(view)
            }
            // ViewPager2 requires match_parent for its children
            view.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return PagerViewHolder(view)
        }

        override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
            // Home module surfaces are created when the single pager page is inflated.
        }

        override fun getItemCount(): Int {
            return TERMUX_WORKSPACE_PAGE_COUNT
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }

    }

    private class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private var suggestionsManager: SuggestionsManager? = null

    private var terminalView: TextView? = null

    private var doubleTapCmd: String?
    private var lockOnDbTap: Boolean

    private val receiver: BroadcastReceiver

    var pack: MainPack? = null

    private val clearOnLock: Boolean

    private val mRootView: View?

    private val viewPager: ViewPager2
    private var homeWidgetsContainer: ViewGroup? = null
    private var mainContainer: View? = null
    private var headerContainer: ViewGroup? = null
    private var headerOriginalParent: ViewGroup? = null
    private var headerOriginalParams: ViewGroup.LayoutParams? = null
    private var headerOriginalIndex = -1
    private var landscapeSplitContainer: View? = null
    private var landscapeLeftPane: ViewGroup? = null
    private var landscapeRightPane: ViewGroup? = null
    private var landscapeFoldGutter: View? = null
    private var portraitMainParams: FrameLayout.LayoutParams? = null
    private var portraitTrayParams: FrameLayout.LayoutParams? = null
    private var landscapeLayoutActive = false
    private var duoLayoutActive = false
    private var splitDuoStatusActive = false
    private var duoLayoutMode: String? = DUO_LAYOUT_OFF
    private var activeDuoLayoutMode: String? = DUO_LAYOUT_OFF
    private var systemInsetLeft = 0
    private var systemInsetTop = 0
    private var systemInsetRight = 0
    private var systemInsetBottom = 0
    private var imeBottomOffset = 0
    private var imeInsetVisible = false
    private var termuxWorkspaceImeFrameRoot: View? = null

    private val genericBorderCornerRadius: Int
    private var bgColors: Array<String?>
    private var outlineColors: Array<String?>
    private var shadowXOffset: Int
    private var shadowYOffset: Int
    private var shadowRadius: Float
    private var useDashed: Boolean
    private var margins: Array<IntArray?>

    private val INPUT_BGCOLOR_INDEX = 10
    private val OUTPUT_BGCOLOR_INDEX = 11
    private val SUGGESTIONS_BGCOLOR_INDEX = 12
    private val TOOLBAR_BGCOLOR_INDEX = 13

    private val OUTPUT_MARGINS_INDEX = 1
    private val INPUTAREA_MARGINS_INDEX = 2
    private val INPUTFIELD_MARGINS_INDEX = 3
    private val TOOLBAR_MARGINS_INDEX = 4
    private val SUGGESTIONS_MARGINS_INDEX = 5

    private val mExecuter: CommandExecuter

    private fun setupTerminalPage(terminalPage: View) {
        terminalTrayContainer = mRootView!!.findViewById<View?>(R.id.terminal_tray_container)
        if (terminalTrayContainer != null && portraitTrayParams == null && terminalTrayContainer!!.getLayoutParams() is FrameLayout.LayoutParams) {
            portraitTrayParams =
                FrameLayout.LayoutParams((terminalTrayContainer!!.getLayoutParams() as FrameLayout.LayoutParams?)!!)
        }
        terminalContainer = terminalPage.findViewById<ViewGroup?>(R.id.terminal_container)
        terminalOutputBorder = terminalPage.findViewById<View>(R.id.terminal_output_border)
        terminalTrayToggle = terminalPage.findViewById<TextView?>(R.id.terminal_tray_toggle)

        terminalView = terminalPage.findViewById<View?>(R.id.terminal_view) as TextView?
        terminalView!!.setOnTouchListener(this)
        (terminalView!!.getParent().getParent() as View).setOnTouchListener(this)

        Companion.applyBgRect(
            mContext!!,
            terminalOutputBorder!!,
            bgColors[OUTPUT_BGCOLOR_INDEX],
            margins[OUTPUT_MARGINS_INDEX]!!,
            Tuils.dpToPx(mContext, outputCornerRadius()),
            useDashed,
            terminalBorderColor(),
            true
        )
        terminalView!!.setBackgroundColor(Color.TRANSPARENT)
        terminalView!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (terminalContainer != null) {
                    terminalContainer!!.post(Runnable { applyTerminalTrayState(false) })
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        Companion.applyShadow(
            terminalView!!,
            outlineColors[OUTPUT_BGCOLOR_INDEX]!!,
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )
        restoreTerminalTrayState()
        styleTerminalTrayToggle()
        applyTerminalTrayState(false)

        val inputView = mRootView.findViewById<View?>(R.id.input_view) as EditText
        val prefixView = mRootView.findViewById<View?>(R.id.prefix_view) as TextView
        inputView.setCursorVisible(false)
        inputView.setShowSoftInputOnFocus(false)
        if (inputView is OutlineEditText) {
            val outlineInput = inputView
            outlineInput.setIdleCursorColor(XMLPrefsManager.getColor(Theme.cursor_color))
            outlineInput.setIdleCursorVisible(true)
        }
        inputView.setOnClickListener(View.OnClickListener { v: View? ->
            if (inputView is OutlineEditText) {
                inputView.setIdleCursorVisible(false)
            }
            inputView.setCursorVisible(true)
            inputView.setShowSoftInputOnFocus(true)
            sendRetuiKeyboardTheme(inputView, "launcher")
            inputView.requestFocus()
            refreshStockSuggestions()
            imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT)
        })
        applyRetuiKeyboardTheme(inputView, "launcher")

        Companion.applyBgRect(
            mContext!!,
            mRootView.findViewById<View?>(R.id.input_group),
            bgColors[INPUT_BGCOLOR_INDEX],
            margins[INPUTAREA_MARGINS_INDEX]!!,
            genericBorderCornerRadius,
            useDashed,
            terminalBorderColor(),
            false
        )
        Companion.applyShadow(
            inputView,
            outlineColors[INPUT_BGCOLOR_INDEX]!!,
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )
        Companion.applyShadow(
            prefixView,
            outlineColors[INPUT_BGCOLOR_INDEX]!!,
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )

        Companion.applyMargins(inputView, margins[INPUTFIELD_MARGINS_INDEX]!!)
        Companion.applyMargins(prefixView, margins[INPUTFIELD_MARGINS_INDEX]!!)

        var submitView = mRootView.findViewById<View?>(R.id.submit_tv) as ImageView?
        val showSubmit = XMLPrefsManager.getBoolean(Ui.show_enter_button)
        if (!showSubmit) {
            submitView!!.setVisibility(View.GONE)
            submitView = null
        }

        val showToolbar = XMLPrefsManager.getBoolean(Toolbar.show_toolbar)
        var backView: ImageButton? = null
        var nextView: ImageButton? = null
        var deleteView: ImageButton? = null
        var pasteView: ImageButton? = null
        var appDrawerView: ImageButton? = null
        var termuxWorkspaceView: ImageButton? = null

        if (!showToolbar) {
            mRootView.findViewById<View?>(R.id.tools_view).setVisibility(View.GONE)
            toolbarView = null
        } else {
            backView = mRootView.findViewById<View?>(R.id.back_view) as ImageButton?
            nextView = mRootView.findViewById<View?>(R.id.next_view) as ImageButton?
            deleteView = mRootView.findViewById<View?>(R.id.delete_view) as ImageButton?
            pasteView = mRootView.findViewById<View?>(R.id.paste_view) as ImageButton?
            appDrawerView = mRootView.findViewById<View?>(R.id.app_drawer_view) as ImageButton?
            termuxWorkspaceView = mRootView.findViewById<View?>(R.id.termux_workspace_view) as ImageButton?

            toolbarView = mRootView.findViewById<View?>(R.id.tools_view)
            hideToolbarNoInput = XMLPrefsManager.getBoolean(Toolbar.hide_toolbar_no_input)

            Companion.applyBgRect(
                mContext!!,
                toolbarView!!,
                bgColors[TOOLBAR_BGCOLOR_INDEX],
                margins[TOOLBAR_MARGINS_INDEX]!!,
                genericBorderCornerRadius,
                useDashed,
                terminalBorderColor(),
                false
            )

            if (appDrawerView != null) {
                if (XMLPrefsManager.getBoolean(Behavior.swipe_up_apps_drawer)) {
                    appDrawerView.setVisibility(View.VISIBLE)
                    appDrawerView.setOnClickListener(View.OnClickListener { v: View? -> showAppsDrawer() })
                } else {
                    appDrawerView.setVisibility(View.GONE)
                }
            }
            if (termuxWorkspaceView != null) {
                configureTermuxWorkspaceToolbarButton(termuxWorkspaceView)
            }
            refreshToolbarWeightSum()
        }

        mTerminalAdapter = TerminalManager(
            terminalView!!,
            inputView,
            prefixView,
            submitView,
            backView,
            nextView,
            deleteView,
            pasteView,
            mContext,
            mainPack,
            mExecuter
        )
        styleToolbarButtonChrome(backView, nextView, deleteView, pasteView, appDrawerView, termuxWorkspaceView)
        if (showToolbar && toolbarView is LinearLayout) {
            addToolbarShortcutButtons(toolbarView as LinearLayout)
        }

        for (s in pendingInputs) {
            mTerminalAdapter!!.setInput(s, null)
        }
        pendingInputs.clear()

        for (oh in pendingOutputs) {
            if (oh.color != null) {
                mTerminalAdapter!!.setOutput(oh.color!!, oh.output)
            } else {
                mTerminalAdapter!!.setOutput(oh.output, oh.category)
            }
        }
        pendingOutputs.clear()

        mTerminalAdapter!!.focusInputEnd()

        setupModuleSuggestionsStrip()

        if (XMLPrefsManager.getBoolean(Suggestions.show_suggestions)) {
            val sv =
                mRootView.findViewById<View?>(R.id.suggestions_container) as HorizontalScrollView?
            if (sv != null) {
                sv.setFocusable(false)
                sv.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                    if (hasFocus) {
                        v!!.clearFocus()
                    }
                })
                Companion.applyBgRect(
                    mContext!!,
                    sv,
                    bgColors[SUGGESTIONS_BGCOLOR_INDEX],
                    margins[SUGGESTIONS_MARGINS_INDEX]!!,
                    genericBorderCornerRadius,
                    useDashed,
                    terminalBorderColor(),
                    true
                )

                val suggestionsView =
                    mRootView.findViewById<View?>(R.id.suggestions_group) as LinearLayout?
                suggestionsManager = SuggestionsManager(
                    suggestionsView!!,
                    mainPack,
                    mTerminalAdapter!!
                )

                inputView.addTextChangedListener(
                    SuggestionTextWatcher(
                        suggestionsManager!!,
                        OnTextChanged { currentText: String?, before: Int ->
                            if (!hideToolbarNoInput) return@OnTextChanged
                            if (currentText!!.length == 0) toolbarView!!.setVisibility(View.GONE)
                            else if (before == 0) toolbarView!!.setVisibility(View.VISIBLE)
                        })
                )
            }
        } else {
            val sugGroup = mRootView.findViewById<View?>(R.id.suggestions_group)
            if (sugGroup != null) sugGroup.setVisibility(View.GONE)
            hideModuleSuggestionsStrip()
        }


        scheduleTypefaceRefreshes()
    }

    private fun addToolbarShortcutButtons(toolbarLayout: LinearLayout?) {
        if (toolbarLayout == null) {
            return
        }

        var added = 0
        for (slotIndex in 1..ToolbarShortcutManager.MAX_SLOTS) {
            val slot = slot(slotIndex)
            if (!slot.enabled) {
                continue
            }

            val button = ImageButton(mContext)
            val padding = mContext!!.getResources().getDimensionPixelSize(R.dimen.tools_padding)
            button.setPadding(padding, padding, padding, padding)
            button.setScaleType(ImageView.ScaleType.FIT_CENTER)
            button.setBackgroundColor(0)
            button.setImageResource(slot.iconRes)
            button.setColorFilter(
                XMLPrefsManager.getColor(Theme.toolbar_icon_color),
                PorterDuff.Mode.SRC_IN
            )
            styleToolbarButtonChrome(button)
            button.setContentDescription("Toolbar shortcut " + slot.index + ": " + slot.command)
            button.setOnClickListener(View.OnClickListener { v: View? -> executeToolbarShortcut(slot.command) })
            button.setOnLongClickListener(OnLongClickListener { v: View? ->
                openToolbarShortcutSettings()
                true
            })

            toolbarLayout.addView(
                button, LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
                )
            )
            added++
        }

        if (added > 0) {
            toolbarLayout.setWeightSum(countVisibleWeightedChildren(toolbarLayout).toFloat())
        }
    }

    fun refreshTermuxWorkspaceToolbarButton() {
        val button = mRootView?.findViewById<ImageButton?>(R.id.termux_workspace_view)
        configureTermuxWorkspaceToolbarButton(button)
        refreshToolbarWeightSum()
    }

    private fun configureTermuxWorkspaceToolbarButton(button: ImageButton?) {
        if (button == null) {
            return
        }
        val enabled = XMLPrefsManager.getBoolean(Behavior.show_tmux_workspace_button)
        button.visibility = if (enabled) View.VISIBLE else View.GONE
        button.setColorFilter(
            XMLPrefsManager.getColor(Theme.toolbar_icon_color),
            PorterDuff.Mode.SRC_IN
        )
        button.setBackgroundColor(0)
        button.setOnClickListener(View.OnClickListener { v: View? ->
            openTermuxWorkspacePage(true)
        })
        styleToolbarButtonChrome(button)
    }

    private fun refreshToolbarWeightSum() {
        if (toolbarView is LinearLayout) {
            (toolbarView as LinearLayout).setWeightSum(
                countVisibleWeightedChildren(toolbarView as LinearLayout).toFloat()
            )
        }
    }

    private fun styleToolbarButtonChrome(vararg buttons: ImageButton?) {
        if (!cyberdeckMode()) {
            return
        }
        for (button in buttons) {
            if (button == null) {
                continue
            }
            button.setBackground(
                CyberpunkIconFrameDrawable(
                    ColorUtils.setAlphaComponent(terminalBorderColor(), 230),
                    Tuils.dpToPx(mContext!!, 1.6f),
                    Tuils.dpToPx(mContext!!, 9f),
                    Tuils.dpToPx(mContext!!, 9f)
                )
            )
        }
    }

    private fun countVisibleWeightedChildren(toolbarLayout: LinearLayout): Int {
        var count = 0
        for (i in 0..<toolbarLayout.getChildCount()) {
            val child = toolbarLayout.getChildAt(i)
            if (child.getVisibility() == View.GONE) {
                continue
            }
            val rawParams = child.getLayoutParams()
            if (rawParams is LinearLayout.LayoutParams
                && rawParams.weight > 0
            ) {
                count++
            }
        }
        return max(1, count)
    }

    private fun executeToolbarShortcut(command: String?) {
        val normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length == 0) {
            Toast.makeText(mContext, "Toolbar shortcut is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        if (mTerminalAdapter != null && mTerminalAdapter!!.executeInput(normalized)) {
            return
        }

        if (mExecuter != null) {
            mExecuter.execute(normalized, null)
        }
    }

    private fun openToolbarShortcutSettings() {
        openSettingsSurface(ThemerActivity.SECTION_PERSONALIZATION)
    }

    fun openSettingsSurface(section: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openSettingsSurface(section) }
            return
        }

        hideLauncherChromeForSurface()
        restoreLauncherChromeOnResume = true

        val intent = Intent(mContext, ThemerActivity::class.java)
        intent.putExtra(
            ThemerActivity.EXTRA_SECTION,
            if (TextUtils.isEmpty(section)) ThemerActivity.SECTION_HOME else section
        )
        if (mContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAfterChromeHidden(intent)
    }

    private fun startActivityAfterChromeHidden(intent: Intent) {
        val start = Runnable { mContext!!.startActivity(intent) }
        if (mRootView == null || !launcherChromeHiddenForSurface) {
            start.run()
            return
        }
        mRootView!!.postDelayed(start, 48L)
    }

    private fun setupResponsiveLandscapeLayout(rootView: ViewGroup) {
        mainContainer = rootView.findViewById<View?>(R.id.main_container)
        headerContainer = rootView.findViewById<ViewGroup?>(R.id.header_container)
        landscapeSplitContainer = rootView.findViewById<View?>(R.id.landscape_split_container)
        landscapeLeftPane = rootView.findViewById<ViewGroup?>(R.id.landscape_left_pane)
        landscapeRightPane = rootView.findViewById<ViewGroup?>(R.id.landscape_right_pane)
        landscapeFoldGutter = rootView.findViewById<View?>(R.id.landscape_fold_gutter)

        if (headerContainer != null && headerOriginalParent == null && headerContainer!!.getParent() is ViewGroup) {
            headerOriginalParent = headerContainer!!.getParent() as ViewGroup?
            headerOriginalIndex = headerOriginalParent!!.indexOfChild(headerContainer)
            headerOriginalParams = copyLayoutParams(headerContainer!!.getLayoutParams())
        }

        if (mainContainer != null && portraitMainParams == null && mainContainer!!.getLayoutParams() is FrameLayout.LayoutParams) {
            portraitMainParams =
                FrameLayout.LayoutParams((mainContainer!!.getLayoutParams() as FrameLayout.LayoutParams?)!!)
        }
    }

    private fun applyResponsiveLandscapeLayout(configuration: Configuration?) {
        if (mainContainer == null || terminalTrayContainer == null || landscapeSplitContainer == null || landscapeLeftPane == null || landscapeRightPane == null || (mRootView !is ViewGroup)) {
            applyDisplayMarginsForConfiguration(configuration)
            return
        }

        val shouldUseLandscape = shouldUseResponsiveLandscape(configuration)
        if (termuxWorkspaceChromeActive) {
            if (landscapeLayoutActive || mainContainer!!.getParent() !== mRootView) {
                restorePortraitLayout()
            }
            applyLandscapeFoldGutter(configuration)
            applyLandscapeStatusChrome(false)
            applyDisplayMarginsForConfiguration(configuration)
            applyTerminalTrayState(false)
            return
        }
        val shouldUseDuoLayout = shouldUseLandscape && shouldUseDuoLayout()
        val requestedDuoLayoutMode = if (shouldUseDuoLayout) getDuoLayoutMode() else DUO_LAYOUT_OFF
        val duoSideChanged = shouldUseDuoLayout && requestedDuoLayoutMode != activeDuoLayoutMode
        val splitDuoChanged =
            shouldUseDuoLayout && shouldUseSplitDuoLauncher() != splitDuoStatusActive
        if (shouldUseLandscape == landscapeLayoutActive && shouldUseDuoLayout == duoLayoutActive && !duoSideChanged && !splitDuoChanged) {
            applyLandscapeStatusChrome(shouldUseLandscape)
            applyDisplayMarginsForConfiguration(configuration)
            applyTerminalTrayState(false)
            return
        }

        if (shouldUseDuoLayout) {
            activateDuoLayout()
        } else if (shouldUseLandscape) {
            activateLandscapeLayout()
        } else {
            restorePortraitLayout()
        }
        applyLandscapeFoldGutter(configuration)
        applyLandscapeStatusChrome(shouldUseLandscape)
        applyDisplayMarginsForConfiguration(configuration)
        applyTerminalTrayState(false)
    }

    private fun shouldUseDuoLayout(): Boolean {
        return getBoolean(Behavior.duo_mode)
                && DUO_LAYOUT_OFF != getDuoLayoutMode()
    }

    private fun shouldUseResponsiveLandscape(configuration: Configuration?): Boolean {
        if (getInt(Behavior.orientation) == 1) {
            return false
        }
        return isResponsiveLandscapeConfiguration(configuration)
    }

    private fun shouldUseSplitDuoLauncher(): Boolean {
        return getBoolean(Ui.split_duo_launcher)
    }

    fun getDuoLayoutMode(): String {
        if (!getBoolean(Behavior.duo_mode)) {
            return DUO_LAYOUT_OFF
        }
        return normalizeDuoLayoutMode(duoLayoutMode)
    }

    fun enableLastDuoSide(): String {
        var side: String = (if (preferences != null)
            preferences.getString(
                ohi.andre.consolelauncher.UIManager.Companion.DUO_LAST_SIDE_PREF,
                ohi.andre.consolelauncher.UIManager.Companion.DUO_LAYOUT_RIGHT
            )
        else
            ohi.andre.consolelauncher.UIManager.Companion.DUO_LAYOUT_RIGHT)!!
        if (DUO_LAYOUT_OFF == normalizeDuoLayoutMode(side)) {
            side = DUO_LAYOUT_RIGHT
        }
        return setDuoLayoutMode(side)
    }

    fun setDuoLayoutMode(mode: String?): String {
        val normalized: String = normalizeDuoLayoutMode(mode)
        duoLayoutMode = normalized
        if (preferences != null) {
            val editor = preferences!!.edit().putString(DUO_LAYOUT_PREF, normalized)
            if (DUO_LAYOUT_OFF != normalized) {
                editor.putString(DUO_LAST_SIDE_PREF, normalized)
            }
            editor.apply()
        }
        applyResponsiveLandscapeLayoutOnMainThread()
        return normalized
    }

    private fun applyResponsiveLandscapeLayoutOnMainThread() {
        runOnMainThread { applyResponsiveLandscapeLayout(currentConfiguration) }
    }

    private fun applyDisplayMarginsForConfigurationOnMainThread() {
        runOnMainThread { applyDisplayMarginsForConfiguration(currentConfiguration) }
    }

    private val currentConfiguration: Configuration?
        get() = mContext?.resources?.configuration

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
            return
        }

        val mainHandler = handler ?: Handler(Looper.getMainLooper())
        mainHandler.post { action() }
    }

    private fun applyLandscapeStatusChrome(landscape: Boolean) {
        val asciiView = getLabelView(Label.ascii)
        if (asciiView == null) {
            return
        }
        val showAscii = !TextUtils.isEmpty(asciiView.getText())
                && (!landscape || getBoolean(Ui.show_ascii_landscape))
        asciiView.setVisibility(if (showAscii) View.VISIBLE else View.GONE)
    }

    private fun activateLandscapeLayout() {
        val root = mRootView as ViewGroup
        restoreSplitDuoStatusHeader()
        detachFromParent(mainContainer)
        detachFromParent(terminalTrayContainer)
        clearLandscapePanes()

        landscapeLayoutActive = true
        duoLayoutActive = false
        activeDuoLayoutMode = DUO_LAYOUT_OFF
        landscapeSplitContainer!!.setVisibility(View.VISIBLE)
        applyLandscapeFoldGutter(
            if (mContext != null) mContext!!.getResources().getConfiguration() else null
        )

        landscapeLeftPane!!.addView(
            mainContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        landscapeRightPane!!.addView(
            terminalTrayContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (root.indexOfChild(landscapeSplitContainer) < 0) {
            root.addView(landscapeSplitContainer, 0)
        }
    }

    private fun activateDuoLayout() {
        val root = mRootView as ViewGroup
        restoreSplitDuoStatusHeader()
        detachFromParent(mainContainer)
        detachFromParent(terminalTrayContainer)
        clearLandscapePanes()

        landscapeLayoutActive = true
        duoLayoutActive = true
        val activeMode = getDuoLayoutMode()
        activeDuoLayoutMode = activeMode
        landscapeSplitContainer!!.setVisibility(View.VISIBLE)
        applyLandscapeFoldGutter(
            if (mContext != null) mContext!!.getResources().getConfiguration() else null
        )

        val targetPane =
            (if (ohi.andre.consolelauncher.UIManager.Companion.DUO_LAYOUT_LEFT == activeMode) landscapeLeftPane else landscapeRightPane)!!
        targetPane.addView(
            mainContainer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val trayParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        targetPane.addView(terminalTrayContainer, trayParams)
        if (shouldUseSplitDuoLauncher()) {
            attachSplitDuoStatusHeader(activeMode)
        }
        attachDuoSwitchButton(activeMode)

        if (root.indexOfChild(landscapeSplitContainer) < 0) {
            root.addView(landscapeSplitContainer, 0)
        }
    }

    private fun restorePortraitLayout() {
        val root = mRootView as ViewGroup
        restoreSplitDuoStatusHeader()
        detachFromParent(mainContainer)
        detachFromParent(terminalTrayContainer)
        clearLandscapePanes()

        landscapeLayoutActive = false
        duoLayoutActive = false
        activeDuoLayoutMode = DUO_LAYOUT_OFF
        landscapeSplitContainer!!.setVisibility(View.GONE)

        val mainParams = if (portraitMainParams != null)
            FrameLayout.LayoutParams(portraitMainParams!!)
        else
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        val trayParams = if (portraitTrayParams != null)
            FrameLayout.LayoutParams(portraitTrayParams!!)
        else
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )

        root.addView(mainContainer, 0, mainParams)
        root.addView(terminalTrayContainer, min(1, root.getChildCount()), trayParams)
    }

    private fun clearLandscapePanes() {
        if (landscapeLeftPane != null) {
            landscapeLeftPane!!.removeAllViews()
        }
        if (landscapeRightPane != null) {
            landscapeRightPane!!.removeAllViews()
        }
    }

    private fun attachSplitDuoStatusHeader(activeMode: String?) {
        val emptyPane = getDuoEmptyPane(activeMode)
        if (emptyPane == null || headerContainer == null) {
            splitDuoStatusActive = false
            return
        }

        detachFromParent(headerContainer)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        )
        emptyPane.addView(headerContainer, params)
        splitDuoStatusActive = true
    }

    private fun restoreSplitDuoStatusHeader() {
        if (headerContainer == null || headerOriginalParent == null) {
            splitDuoStatusActive = false
            return
        }

        val parent = headerContainer!!.getParent()
        if (parent === headerOriginalParent) {
            splitDuoStatusActive = false
            return
        }

        detachFromParent(headerContainer)
        val index = if (headerOriginalIndex >= 0) min(
            headerOriginalIndex,
            headerOriginalParent!!.getChildCount()
        ) else
            headerOriginalParent!!.getChildCount()
        val params = if (headerOriginalParams != null)
            copyLayoutParams(headerOriginalParams)
        else
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        headerOriginalParent!!.addView(headerContainer, index, params)
        splitDuoStatusActive = false
    }

    private fun getDuoEmptyPane(activeMode: String?): ViewGroup? {
        return if (DUO_LAYOUT_RIGHT == activeMode) landscapeLeftPane else landscapeRightPane
    }

    private fun copyLayoutParams(params: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        if (params is LinearLayout.LayoutParams) {
            return LinearLayout.LayoutParams(params)
        }
        if (params is FrameLayout.LayoutParams) {
            return FrameLayout.LayoutParams(params)
        }
        if (params is MarginLayoutParams) {
            return MarginLayoutParams(params)
        }
        if (params != null) {
            return ViewGroup.LayoutParams(params)
        }
        return ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun attachDuoSwitchButton(activeMode: String?) {
        if (mContext == null) {
            return
        }

        val moveToLeft = DUO_LAYOUT_RIGHT == activeMode
        val emptyPane = getDuoEmptyPane(activeMode)
        if (emptyPane == null) {
            return
        }

        val targetMode: String = if (moveToLeft) DUO_LAYOUT_LEFT else DUO_LAYOUT_RIGHT
        val button = createDuoSwitchButton(targetMode, moveToLeft)
        val margin = Tuils.dpToPx(mContext, 18)
        val params = FrameLayout.LayoutParams(
            Tuils.dpToPx(mContext, 56),
            Tuils.dpToPx(mContext, 48),
            Gravity.BOTTOM or (if (moveToLeft) Gravity.START else Gravity.END)
        )
        params.setMargins(margin, margin, margin, margin)
        emptyPane.addView(button, params)
    }

    private fun createDuoSwitchButton(targetMode: String?, moveToLeft: Boolean): TextView {
        return DuoSwitchButtonFactory.create(
            mContext,
            targetMode,
            moveToLeft,
            terminalBorderColor(),
            terminalHeaderBackground(),
            max(genericBorderCornerRadius, Tuils.dpToPx(mContext, 6)),
            useDashed,
            onClick = { mode -> setDuoLayoutMode(mode) }
        )
    }

    private fun applyLandscapeFoldGutter(configuration: Configuration?) {
        if (landscapeFoldGutter == null || mContext == null) {
            return
        }

        val landscape = shouldUseResponsiveLandscape(configuration)
        val gutterWidth = if (landscape) this.landscapeFoldGutterWidth else 0
        val params = landscapeFoldGutter!!.getLayoutParams()
        if (params != null && params.width != gutterWidth) {
            params.width = gutterWidth
            landscapeFoldGutter!!.setLayoutParams(params)
        }
        landscapeFoldGutter!!.setVisibility(if (gutterWidth > 0) View.VISIBLE else View.GONE)
    }

    private val landscapeFoldGutterWidth: Int
        get() {
            val gutterMm = max(
                0,
                min(
                    getInt(Ui.landscape_fold_gutter_mm),
                    MAX_LANDSCAPE_FOLD_GUTTER_MM
                )
            )
            if (gutterMm == 0) {
                return 0
            }

            var gutterPx = Tuils.mmToPx(mContext!!.getResources().getDisplayMetrics(), gutterMm)
            val splitWidth =
                if (landscapeSplitContainer != null) landscapeSplitContainer!!.getWidth() else 0
            if (splitWidth > 0) {
                gutterPx = min(gutterPx, splitWidth / 3)
            }
            return gutterPx
        }

    private fun detachFromParent(view: View?) {
        if (view == null) {
            return
        }
        val parent = view.getParent() as ViewGroup?
        if (parent != null) {
            parent.removeView(view)
        }
    }

    fun applyImeBottomOffset(keyboardOffset: Int, imeVisible: Boolean) {
        applyWindowInsets(
            systemInsetLeft,
            systemInsetTop,
            systemInsetRight,
            systemInsetBottom,
            keyboardOffset,
            imeVisible
        )
    }

    fun applyWindowInsets(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        keyboardOffset: Int,
        imeVisible: Boolean
    ) {
        runOnMainThread {
            systemInsetLeft = max(0, left)
            systemInsetTop = max(0, top)
            systemInsetRight = max(0, right)
            systemInsetBottom = max(0, bottom)
            imeInsetVisible = imeVisible
            imeBottomOffset = if (imeInsetVisible) max(0, keyboardOffset) else 0
            applyDisplayMarginsForConfiguration(currentConfiguration)
            applyTermuxImeBottomPadding()
            applyTermuxWorkspaceImeBottomPadding()
            updateKeyboardLayoutState(
                imeVisible || imeBottomOffset > 0,
                if (mRootView != null) mRootView.getHeight() else 0
            )
        }
    }

    private fun applyTermuxImeBottomPadding() {
        if (termuxOverlay == null) {
            return
        }
        termuxOverlay!!.setPadding(
            termuxOverlayBasePaddingLeft + overlayDisplayMarginLeft,
            termuxOverlayBasePaddingTop + overlayDisplayMarginTop,
            termuxOverlayBasePaddingRight + overlayDisplayMarginRight,
            termuxOverlayBasePaddingBottom + overlayDisplayMarginBottom + imeBottomOffset
        )
    }

    private fun applyTermuxWorkspaceImeBottomPadding() {
        val root = termuxWorkspaceRoot ?: return
        val bottomPadding = termuxWorkspaceBasePaddingBottom + imeBottomOffset
        val changed = root.paddingBottom != bottomPadding
        root.setPadding(
            termuxWorkspaceBasePaddingLeft,
            termuxWorkspaceBasePaddingTop,
            termuxWorkspaceBasePaddingRight,
            bottomPadding
        )
        if (changed) {
            scheduleTermuxWorkspaceImeGeometryRefresh()
        }
    }

    private fun scheduleTermuxWorkspaceImeGeometryRefresh() {
        val root = termuxWorkspaceRoot ?: return
        if (!termuxWorkspaceChromeActive) {
            return
        }
        val generation = ++termuxWorkspaceImeResizeGeneration
        root.requestLayout()
        root.post(Runnable {
            refreshTermuxWorkspaceAfterImeResize(generation)
        })
        root.postDelayed(Runnable {
            refreshTermuxWorkspaceAfterImeResize(generation)
        }, TERMUX_WORKSPACE_IME_RESIZE_REFRESH_DELAY_MS)
    }

    private fun refreshTermuxWorkspaceAfterImeResize(generation: Int) {
        if (generation != termuxWorkspaceImeResizeGeneration || !termuxWorkspaceChromeActive) {
            return
        }
        termuxWorkspaceLastRenderedFrameKey = null
        refreshTermuxWorkspace(false)
    }

    private fun updateKeyboardLayoutState(newKeyboardVisible: Boolean, rootHeight: Int) {
        val layoutStateChanged =
            !hasLastLayoutState || keyboardVisible != newKeyboardVisible || lastObservedRootHeight != rootHeight
        keyboardVisible = newKeyboardVisible
        hasLastLayoutState = true
        lastObservedRootHeight = rootHeight
        if (!layoutStateChanged) {
            return
        }
        scheduleTermuxWorkspaceImeGeometryRefresh()
        if (mTerminalAdapter != null && mTerminalAdapter!!.inputView is EditText) {
            val terminalInput = mTerminalAdapter!!.inputView as EditText
            terminalInput.setCursorVisible(keyboardVisible)
            terminalInput.setShowSoftInputOnFocus(keyboardVisible)
            if (terminalInput is OutlineEditText) {
                terminalInput.setIdleCursorVisible(!keyboardVisible)
            }
            if (!keyboardVisible && terminalInput.hasFocus()) {
                terminalInput.clearFocus()
            }
        }
        setNotificationWidgetCompact(mRootView!!, keyboardVisible)
        applyTerminalTrayState(false)
        if (keyboardVisible && XMLPrefsManager.getBoolean(Behavior.auto_scroll)) {
            if (mTerminalAdapter != null) mTerminalAdapter!!.scrollToEnd()
        }
    }

    fun refreshDisplayMargins() {
        applyDisplayMarginsForConfigurationOnMainThread()
    }

    fun refreshResponsiveLandscapeLayout() {
        applyResponsiveLandscapeLayoutOnMainThread()
    }

    private fun applyDisplayMarginsForConfiguration(configuration: Configuration?) {
        if (mRootView == null || mContext == null) {
            return
        }

        val landscape = shouldUseResponsiveLandscape(configuration)
        applyLandscapeFoldGutter(configuration)
        var topMargins = getDisplayMargins(Ui.display_margin_top_section)
        var bottomMargins = getDisplayMargins(Ui.display_margin_bottom_section)
        // Split Duo can put header/status and terminal on different panes, so only non-Duo
        // landscape folds the two section margins into the legacy landscape margin.
        if (landscape && !duoLayoutActive && XMLPrefsManager.wasChanged(Ui.display_margin_landscape_mm, false)) {
            topMargins = getDisplayMargins(Ui.display_margin_landscape_mm)
            bottomMargins = topMargins
        }

        mRootView.setPadding(systemInsetLeft, systemInsetTop, systemInsetRight, systemInsetBottom)
        val metrics = mContext!!.getResources().getDisplayMetrics()
        applySectionDisplayMargins(mainContainer, topMargins, metrics, 0)
        if (splitDuoStatusActive) {
            applySectionDisplayMargins(headerContainer, topMargins, metrics, 0)
        }
        applySectionDisplayMargins(terminalTrayContainer, bottomMargins, metrics, imeBottomOffset)
        applyTerminalOverlayDisplayMargins(topMargins, bottomMargins, metrics)
    }

    private fun getDisplayMargins(save: Ui?): IntArray {
        return XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(save), 4, 0)
    }

    private fun applySectionDisplayMargins(
        view: View?,
        marginMm: IntArray,
        metrics: DisplayMetrics?,
        extraBottomPx: Int
    ) {
        if (view == null) {
            return
        }

        val left = Tuils.mmToPx(metrics, marginMm[0])
        val top = Tuils.mmToPx(metrics, marginMm[1])
        val right = Tuils.mmToPx(metrics, marginMm[2])
        val bottom = Tuils.mmToPx(metrics, marginMm[3]) + extraBottomPx

        val params = view.getLayoutParams()
        if (params is MarginLayoutParams) {
            val marginParams = params
            if (marginParams.leftMargin == left
                && marginParams.topMargin == top
                && marginParams.rightMargin == right
                && marginParams.bottomMargin == bottom
            ) {
                return
            }
            marginParams.setMargins(left, top, right, bottom)
            view.setLayoutParams(marginParams)
        } else {
            if (view.paddingLeft == left
                && view.paddingTop == top
                && view.paddingRight == right
                && view.paddingBottom == bottom
            ) {
                return
            }
            view.setPadding(left, top, right, bottom)
        }
    }

    private fun applyTerminalOverlayDisplayMargins(
        topMargins: IntArray,
        bottomMargins: IntArray,
        metrics: DisplayMetrics?
    ) {
        overlayDisplayMarginLeft = max(
            Tuils.mmToPx(metrics, topMargins[0]),
            Tuils.mmToPx(metrics, bottomMargins[0])
        )
        overlayDisplayMarginTop = Tuils.mmToPx(metrics, topMargins[1])
        overlayDisplayMarginRight = max(
            Tuils.mmToPx(metrics, topMargins[2]),
            Tuils.mmToPx(metrics, bottomMargins[2])
        )
        overlayDisplayMarginBottom = Tuils.mmToPx(metrics, bottomMargins[3])

        applyMarginsWithBase(
            appsDrawerContainer,
            appsDrawerContainerBaseMargins,
            overlayDisplayMarginLeft,
            overlayDisplayMarginTop,
            overlayDisplayMarginRight,
            overlayDisplayMarginBottom
        )
        applyTermuxImeBottomPadding()
        applyOverlayPaddingWithBase(
            fileOverlay,
            fileOverlayBasePaddingLeft,
            fileOverlayBasePaddingTop,
            fileOverlayBasePaddingRight,
            fileOverlayBasePaddingBottom,
            overlayDisplayMarginLeft,
            overlayDisplayMarginTop,
            overlayDisplayMarginRight,
            overlayDisplayMarginBottom
        )
        applyOverlayPaddingWithBase(
            hackOverlay,
            hackOverlayBasePaddingLeft,
            hackOverlayBasePaddingTop,
            hackOverlayBasePaddingRight,
            hackOverlayBasePaddingBottom,
            overlayDisplayMarginLeft,
            overlayDisplayMarginTop,
            overlayDisplayMarginRight,
            overlayDisplayMarginBottom
        )
    }

    private fun captureBaseMargins(view: View?, out: IntArray?) {
        if (view == null || out == null || out.size < 4) {
            return
        }

        val params = view.getLayoutParams()
        if (params is MarginLayoutParams) {
            val marginParams = params
            out[0] = marginParams.leftMargin
            out[1] = marginParams.topMargin
            out[2] = marginParams.rightMargin
            out[3] = marginParams.bottomMargin
        }
    }

    private fun applyMarginsWithBase(
        view: View?,
        baseMargins: IntArray?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (view == null || baseMargins == null || baseMargins.size < 4) {
            return
        }

        val params = view.getLayoutParams()
        if (params !is MarginLayoutParams) {
            return
        }

        val marginParams = params
        val newLeft = baseMargins[0] + left
        val newTop = baseMargins[1] + top
        val newRight = baseMargins[2] + right
        val newBottom = baseMargins[3] + bottom
        if (marginParams.leftMargin != newLeft || marginParams.topMargin != newTop || marginParams.rightMargin != newRight || marginParams.bottomMargin != newBottom) {
            marginParams.setMargins(newLeft, newTop, newRight, newBottom)
            view.setLayoutParams(marginParams)
        }
    }

    private fun applyOverlayPaddingWithBase(
        view: View?,
        baseLeft: Int,
        baseTop: Int,
        baseRight: Int,
        baseBottom: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        if (view == null) {
            return
        }

        val newLeft = baseLeft + left
        val newTop = baseTop + top
        val newRight = baseRight + right
        val newBottom = baseBottom + bottom
        if (view.getPaddingLeft() != newLeft || view.getPaddingTop() != newTop || view.getPaddingRight() != newRight || view.getPaddingBottom() != newBottom) {
            view.setPadding(newLeft, newTop, newRight, newBottom)
        }
    }

    private fun styleTerminalTrayToggle() {
        TerminalTrayToggleView.style(
            mContext,
            terminalTrayToggle,
            terminalOutputBorder,
            this.isOutputHeaderNone,
            this.isOutputHeaderArrowsOnly,
            moduleNameTextColor(),
            outputHeaderTextSize(),
            terminalHeaderTabBackground(),
            onClick = {
                if (!landscapeLayoutActive && this.isOutputTrayToggledMode) {
                    setTerminalTrayExpanded(!terminalTrayExpanded)
                }
            }
        )
        updateTerminalTrayToggleText()
    }

    private fun setTerminalTrayExpanded(expanded: Boolean) {
        if (!this.isOutputTrayToggledMode) {
            return
        }
        terminalTrayExpanded = expanded
        saveTerminalTrayState()
        applyTerminalTrayState(true)
    }

    private fun restoreTerminalTrayState() {
        if (this.isOutputTrayToggledMode && preferences != null) {
            terminalTrayExpanded = preferences!!.getBoolean(PREF_OUTPUT_TRAY_EXPANDED, false)
        } else if (this.isOutputTrayAutoMode) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule)
        } else {
            terminalTrayExpanded = false
            if (preferences != null) {
                preferences!!.edit().remove(PREF_OUTPUT_TRAY_EXPANDED).apply()
            }
        }
    }

    private fun saveTerminalTrayState() {
        if (this.isOutputTrayToggledMode && preferences != null) {
            preferences!!.edit().putBoolean(PREF_OUTPUT_TRAY_EXPANDED, terminalTrayExpanded).apply()
        }
    }

    private fun applyTerminalTrayState(refocusInput: Boolean) {
        if (terminalContainer == null) {
            return
        }

        if (landscapeLayoutActive && !duoLayoutActive) {
            val params = terminalContainer!!.getLayoutParams()
            if (params is LinearLayout.LayoutParams) {
                val lp = params
                if (lp.height != 0 || lp.weight != 1f) {
                    lp.height = 0
                    lp.weight = 1f
                    terminalContainer!!.setLayoutParams(lp)
                }
            } else if (params != null) {
                params.height = ViewGroup.LayoutParams.MATCH_PARENT
                terminalContainer!!.setLayoutParams(params)
            }
            updateTerminalTrayToggleText()
            if (refocusInput && mTerminalAdapter != null) {
                mTerminalAdapter!!.requestInputFocus()
                mTerminalAdapter!!.scrollToEnd()
            }
            return
        }

        val rootHeight = if (mRootView != null) mRootView.getHeight() else 0
        val collapsedHeight = calculateCollapsedTerminalTrayHeight()
        val expandedHeight = calculateExpandedTerminalTrayHeight(rootHeight, collapsedHeight)

        val params = terminalContainer!!.getLayoutParams()
        if (params is LinearLayout.LayoutParams) {
            params.weight = 0f
        }
        val targetHeight: Int
        if (this.isOutputTrayNativeMode) {
            targetHeight = if (TextUtils.isEmpty(activeModule)) {
                calculateNativeTerminalTrayHeight(expandedHeight)
            } else {
                collapsedHeight
            }
        } else if (this.isOutputTrayAutoMode) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule)
            targetHeight = if (terminalTrayExpanded) expandedHeight else collapsedHeight
        } else {
            targetHeight = if (terminalTrayExpanded) expandedHeight else collapsedHeight
        }
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            terminalContainer!!.setLayoutParams(params)
        }

        updateTerminalTrayToggleText()
        if (terminalTrayExpanded && refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter!!.scrollToEnd()
        }
        if (refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter!!.requestInputFocus()
        }
    }

    private fun calculateExpandedTerminalTrayHeight(rootHeight: Int, collapsedHeight: Int): Int {
        val expandedHeight: Int
        if (rootHeight <= 0) {
            expandedHeight =
                max(collapsedHeight, UIUtils.dpToPx(mContext!!, if (keyboardVisible) 220 else 320))
        } else {
            val trayPercent = if (keyboardVisible) 0.34f else 0.48f
            expandedHeight = max(collapsedHeight, Math.round(rootHeight * trayPercent))
        }
        return applyTerminalTrayMaxHeight(expandedHeight, collapsedHeight)
    }

    private fun applyTerminalTrayMaxHeight(expandedHeight: Int, collapsedHeight: Int): Int {
        val maxHeightDp = outputTrayMaxHeightDp()
        if (maxHeightDp <= 0) {
            return expandedHeight
        }
        val maxHeight = UIUtils.dpToPx(mContext!!, maxHeightDp)
        return max(collapsedHeight, min(expandedHeight, maxHeight))
    }

    private fun updateTerminalTrayToggleText() {
        TerminalTrayToggleView.updateText(
            mContext,
            terminalTrayToggle,
            this.isOutputHeaderNone,
            landscapeLayoutActive,
            terminalTrayExpanded,
            this.isOutputHeaderArrowsOnly,
            this.isOutputTrayNativeMode,
            this.isOutputTrayAutoMode,
            moduleNameTextColor()
        )
    }

    private val isOutputTrayNativeMode: Boolean
        get() = OUTPUT_TRAY_MODE_NATIVE == outputTrayMode()

    private val isOutputTrayAutoMode: Boolean
        get() = OUTPUT_TRAY_MODE_AUTO == outputTrayMode()

    private val isOutputTrayToggledMode: Boolean
        get() = OUTPUT_TRAY_MODE_TOGGLED == outputTrayMode()

    private val isOutputHeaderArrowsOnly: Boolean
        get() = OUTPUT_HEADER_MODE_ARROWS == outputHeaderMode()

    private val isOutputHeaderNone: Boolean
        get() = OUTPUT_HEADER_MODE_NONE == outputHeaderMode()

    private fun outputHeaderMode(): String {
        return AppearanceSettings.outputHeaderMode()
    }

    private fun outputTrayMode(): String {
        var mode = XMLPrefsManager.get(Behavior.output_tray_mode)
        if (mode != null) {
            mode = mode.trim { it <= ' ' }.lowercase()
        }
        if (OUTPUT_TRAY_MODE_TOGGLED == mode && this.isOutputHeaderNone) {
            return OUTPUT_TRAY_MODE_NATIVE
        }
        if (OUTPUT_TRAY_MODE_AUTO == mode
            || OUTPUT_TRAY_MODE_TOGGLED == mode
            || OUTPUT_TRAY_MODE_NATIVE == mode
        ) {
            return mode
        }
        if (!this.isOutputHeaderNone && XMLPrefsManager.getBoolean(Behavior.toggle_output_state)) {
            return OUTPUT_TRAY_MODE_TOGGLED
        }
        return OUTPUT_TRAY_MODE_NATIVE
    }

    private fun calculateCollapsedTerminalTrayHeight(): Int {
        val minHeight = UIUtils.dpToPx(mContext!!, 66)
        val maxHeight = UIUtils.dpToPx(mContext!!, if (keyboardVisible) 96 else 132)
        if (terminalView == null || TextUtils.isEmpty(terminalView!!.getText())) {
            return minHeight
        }

        var lineCount = max(1, terminalView!!.getLineCount())
        if (lineCount <= 0) {
            lineCount =
                terminalView!!.getText().toString().split("\\n".toRegex()).toTypedArray().size
        }
        val contentHeight =
            ((lineCount * max(terminalView!!.getLineHeight(), UIUtils.dpToPx(mContext!!, 18)))
                    + UIUtils.dpToPx(mContext!!, 38))
        return max(minHeight, min(maxHeight, contentHeight))
    }

    private fun calculateNativeTerminalTrayHeight(maxHeight: Int): Int {
        val minHeight = UIUtils.dpToPx(mContext!!, 66)
        if (terminalView == null || TextUtils.isEmpty(terminalView!!.getText())) {
            return minHeight
        }

        var lineCount = max(1, terminalView!!.getLineCount())
        if (lineCount <= 0) {
            lineCount =
                terminalView!!.getText().toString().split("\\n".toRegex()).toTypedArray().size
        }
        val contentHeight =
            ((lineCount * max(terminalView!!.getLineHeight(), UIUtils.dpToPx(mContext!!, 18)))
                    + UIUtils.dpToPx(mContext!!, 38))
        return max(minHeight, min(maxHeight, contentHeight))
    }

    private fun resolveTerminalWindowBgColor(bgColor: String?): Int {
        try {
            val color = Color.parseColor(bgColor)
            if (color != Color.TRANSPARENT) {
                return color
            }
        } catch (ignored: Exception) {
        }
        return terminalWindowBackground()
    }

    private fun setupHomeWidgetsPage(homePage: View) {
        moduleDockScroll = homePage.findViewById<View?>(R.id.module_dock_scroll)
        if (moduleDockScroll != null) {
            moduleDockScroll!!.getViewTreeObserver()
                .addOnScrollChangedListener(OnScrollChangedListener {
                    val scrollX = currentModuleDockScrollX()
                    if (scrollX > 0) {
                        lastModuleDockScrollX = scrollX
                    }
                })
        }
        moduleDock = homePage.findViewById<LinearLayout?>(R.id.module_dock)
        homeWidgetsContainer = homePage.findViewById<ViewGroup?>(R.id.home_widgets_container)
        if (homeWidgetsContainer == null) return

        ensureSystemLuaModules()
        pruneBundledLuaSamples()
        activeModule = ""
        ModuleManager.setActiveModule(mContext, "")
        homeWidgetsContainer!!.removeAllViews()
        rebuildModuleDock()
        refreshSuggestionsForActiveModule()
    }

    private fun setupTermuxWorkspacePage(workspacePage: View) {
        termuxWorkspaceRoot = workspacePage.findViewById<View?>(R.id.termux_workspace_root)
        termuxWorkspaceBorder = workspacePage.findViewById<View?>(R.id.termux_workspace_border)
        termuxWorkspaceLabel = workspacePage.findViewById<TextView?>(R.id.termux_workspace_label)
        termuxWorkspaceGrid = workspacePage.findViewById<TerminalGridView?>(R.id.termux_workspace_grid)
        termuxWorkspaceOutput = workspacePage.findViewById<TextView?>(R.id.termux_workspace_output)
        termuxWorkspacePrefix = workspacePage.findViewById<TextView?>(R.id.termux_workspace_prefix)
        termuxWorkspaceInput = workspacePage.findViewById<EditText?>(R.id.termux_workspace_input)
        termuxWorkspaceSend = workspacePage.findViewById<TextView?>(R.id.termux_workspace_send)
        termuxWorkspaceScroll = workspacePage.findViewById<ScrollView?>(R.id.termux_workspace_scroll)
        termuxWorkspaceInputGroup = workspacePage.findViewById<View?>(R.id.termux_workspace_input_group)
        termuxWorkspaceOutputPanel = workspacePage.findViewById<View?>(R.id.termux_workspace_output_panel)
        termuxWorkspaceOutputLabel = workspacePage.findViewById<TextView?>(R.id.termux_workspace_output_label)
        termuxWorkspaceTools = workspacePage.findViewById<View?>(R.id.termux_workspace_tools)
        collapseTermuxWorkspaceInputHost()
        termuxWorkspaceRoot?.let { root ->
            termuxWorkspaceBasePaddingLeft = root.paddingLeft
            termuxWorkspaceBasePaddingTop = root.paddingTop
            termuxWorkspaceBasePaddingRight = root.paddingRight
            termuxWorkspaceBasePaddingBottom = root.paddingBottom
            if (termuxWorkspaceImeFrameRoot !== root) {
                termuxWorkspaceImeFrameRoot = root
                root.viewTreeObserver.addOnGlobalLayoutListener {
                    syncTermuxWorkspaceImeFromVisibleFrame()
                }
            }
        }

        applyTermuxWorkspaceImeBottomPadding()
        styleTermuxWorkspace()
        renderTermuxWorkspaceStatus("Tap the terminal toolbar button or run :refresh to start.")

        val terminalSwipeListener = OnTouchListener { v, event ->
            handleTermuxWorkspaceOutputTouch(event)
        }
        termuxWorkspaceOutputPanel?.setOnTouchListener(terminalSwipeListener)
        termuxWorkspaceScroll?.setOnTouchListener(terminalSwipeListener)
        termuxWorkspaceGrid?.setOnTouchListener(terminalSwipeListener)
        termuxWorkspaceOutput?.setOnTouchListener(terminalSwipeListener)
        val terminalGeometryListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val sizeChanged = right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop
            if (sizeChanged) {
                termuxWorkspaceLastRenderedFrameKey = null
                refreshTermuxWorkspaceSocketGeometry(true)
            }
        }
        termuxWorkspaceOutputPanel?.addOnLayoutChangeListener(terminalGeometryListener)
        termuxWorkspaceScroll?.addOnLayoutChangeListener(terminalGeometryListener)

        termuxWorkspaceInput?.let { input ->
            applyRetuiKeyboardTheme(input, "termux")
            input.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            if (input is TermuxWorkspaceInputEditText) {
                input.setBackspaceListener {
                    if (termuxWorkspaceDirectInput) {
                        if (!handleTermuxWorkspaceLocalCommandBackspace()) {
                            sendTermuxWorkspaceKey("BSpace")
                        }
                    }
                }
                input.setTextListener { text ->
                    if (termuxWorkspaceDirectInput) {
                        handleTermuxWorkspaceDirectText(text)
                    }
                }
            }
            input.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                input.setCursorVisible(false)
                input.setShowSoftInputOnFocus(hasFocus)
            })
            input.setOnKeyListener(View.OnKeyListener { v: View?, keyCode: Int, event: KeyEvent? ->
                if (termuxWorkspaceDirectInput && event != null && event.action == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DEL
                ) {
                    if (!handleTermuxWorkspaceLocalCommandBackspace()) {
                        sendTermuxWorkspaceKey("BSpace")
                    }
                    true
                } else if (handleTermuxWorkspaceHardwareModifiedKey(keyCode, event)) {
                    true
                } else if (keyCode == KeyEvent.KEYCODE_ENTER && event != null && event.action == KeyEvent.ACTION_UP) {
                    if (termuxWorkspaceDirectInput) {
                        if (!submitTermuxWorkspaceLocalCommandIfActive()) {
                            sendTermuxWorkspaceKey("Enter")
                        }
                    } else {
                        submitTermuxWorkspaceInputFromField()
                    }
                    true
                } else {
                    false
                }
            })
            input.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() != KeyEvent.ACTION_UP) {
                        return@OnEditorActionListener true
                    }
                    if (termuxWorkspaceDirectInput) {
                        if (!submitTermuxWorkspaceLocalCommandIfActive()) {
                            sendTermuxWorkspaceKey("Enter")
                        }
                    } else {
                        submitTermuxWorkspaceInputFromField()
                    }
                    return@OnEditorActionListener true
                }
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    if (termuxWorkspaceDirectInput) {
                        if (!submitTermuxWorkspaceLocalCommandIfActive()) {
                            sendTermuxWorkspaceKey("Enter")
                        }
                    } else {
                        submitTermuxWorkspaceInputFromField()
                    }
                    true
                } else {
                    false
                }
            })
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    termuxWorkspaceInsertedStart = -1
                    termuxWorkspaceInsertedText = null
                    if (termuxWorkspaceDirectInput) {
                        return
                    }
                    if (termuxWorkspaceSuppressInputWatcher || !hasTermuxWorkspacePendingModifiers()) {
                        return
                    }
                    if (before == 0 && count == 1 && s != null && start >= 0 && start + count <= s.length) {
                        termuxWorkspaceInsertedStart = start
                        termuxWorkspaceInsertedText = s.subSequence(start, start + count).toString()
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (termuxWorkspaceDirectInput) {
                        clearTermuxWorkspaceDirectInput(s)
                    } else {
                        consumeTermuxWorkspaceInsertedCombo(s)
                    }
                }
            })
        }
        termuxWorkspaceSend?.let { send ->
            send.setOnClickListener(View.OnClickListener { v: View? -> submitTermuxWorkspaceInputFromField() })
            styleTermuxWorkspaceButton(send)
        }

        bindTermuxWorkspaceButton(workspacePage, R.id.termux_workspace_home, Runnable { openHomePage() })
        bindTermuxWorkspaceButton(workspacePage, R.id.termux_workspace_new, Runnable { newTermuxWorkspaceWindow(null) })
        bindTermuxWorkspaceButton(workspacePage, R.id.termux_workspace_ime, Runnable { focusTermuxWorkspaceInput(true) })
        termuxWorkspaceKeysRowOne = workspacePage.findViewById<ViewGroup?>(R.id.termux_workspace_keys_row_one)
        termuxWorkspaceKeysRowTwo = workspacePage.findViewById<ViewGroup?>(R.id.termux_workspace_keys_row_two)
        bindTermuxWorkspaceTerminalKeys(workspacePage)
        bindTermuxWorkspaceKeyModeSwipe(termuxWorkspaceTools)
        bindTermuxWorkspaceKeyModeSwipe(termuxWorkspaceKeysRowOne)
        bindTermuxWorkspaceKeyModeSwipe(termuxWorkspaceKeysRowTwo)
        styleTermuxWorkspaceChromeButtons()
    }

    private fun collapseTermuxWorkspaceInputHost() {
        val group = termuxWorkspaceInputGroup ?: return
        group.minimumHeight = 1
        group.alpha = 0f
        group.isClickable = false
        group.isFocusable = false
        group.setPadding(0, 0, 0, 0)
        group.background = null
        val params = group.layoutParams
        if (params != null) {
            params.height = 1
            group.layoutParams = params
        }
        termuxWorkspacePrefix?.visibility = View.GONE
        termuxWorkspaceSend?.visibility = View.GONE
        termuxWorkspaceInput?.let { input ->
            input.setText(Tuils.EMPTYSTRING)
            input.hint = Tuils.EMPTYSTRING
            input.minimumHeight = 1
            input.minHeight = 1
            input.height = 1
            input.alpha = 0f
            input.background = null
            input.setPadding(0, 0, 0, 0)
        }
    }

    private fun bindTermuxWorkspaceButton(root: View, id: Int, action: Runnable) {
        val key = root.findViewById<TextView?>(id)
        if (key == null) {
            return
        }
        key.setOnClickListener(View.OnClickListener { v: View? -> action.run() })
        styleTermuxWorkspaceChromeButton(key)
    }

    private fun bindTermuxWorkspaceTerminalKeys(root: View) {
        if (termuxWorkspaceKeysRowOne == null) {
            termuxWorkspaceKeysRowOne = root.findViewById<ViewGroup?>(R.id.termux_workspace_keys_row_one)
        }
        if (termuxWorkspaceKeysRowTwo == null) {
            termuxWorkspaceKeysRowTwo = root.findViewById<ViewGroup?>(R.id.termux_workspace_keys_row_two)
        }
        updateTermuxWorkspaceKeyMode()
    }

    private fun bindTermuxWorkspaceKeyModeSwipe(view: View?) {
        view?.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            handleTermuxWorkspaceKeyModeTouch(event)
        })
    }

    private fun updateTermuxWorkspaceKeyMode() {
        syncTermuxKeyRows(
            termuxWorkspaceKeysRowOne,
            termuxWorkspaceKeysRowTwo,
            termuxWorkspaceKeySlots,
            termuxWorkspaceCombinedKeyTray(),
            this::bindTermuxWorkspaceKeyModeSwipe
        )
        val specs = activeTermuxWorkspaceKeySpecs()
        termuxWorkspaceCtrlKey = null
        termuxWorkspaceAltKey = null
        termuxWorkspaceShiftKey = null
        val normalColor = notificationWidgetTextColor()
        for (i in termuxWorkspaceKeySlots.indices) {
            val key = termuxWorkspaceKeySlots[i]
            if (i >= specs.size) {
                key.visibility = View.INVISIBLE
                key.setOnClickListener(null)
                continue
            }
            val spec = specs[i]
            key.visibility = View.VISIBLE
            key.text = spec.label
            key.textSize = if (spec.label.length >= 5) 10f else 11f
            key.alpha = 0.82f
            key.setTextColor(normalColor)
            key.setBackgroundColor(Color.TRANSPARENT)
            styleTermuxToolButton(key, normalColor)
            when (spec.modifier) {
                TermuxWorkspaceModifier.CTRL -> termuxWorkspaceCtrlKey = key
                TermuxWorkspaceModifier.ALT -> termuxWorkspaceAltKey = key
                TermuxWorkspaceModifier.SHIFT -> termuxWorkspaceShiftKey = key
                null -> {}
            }
            key.setOnClickListener(View.OnClickListener { v: View? ->
                if (termuxWorkspaceKeySwipeConsumed) {
                    termuxWorkspaceKeySwipeConsumed = false
                    return@OnClickListener
                }
                handleTermuxWorkspaceKeySpec(spec)
            })
        }
        updateTermuxWorkspaceModifierButtons()
    }

    private fun isTermuxLandscapeKeyTray(): Boolean {
        return shouldUseResponsiveLandscape(currentConfiguration)
    }

    private fun termuxWorkspaceCombinedKeyTray(): Boolean {
        return isTermuxLandscapeKeyTray()
    }

    private fun termuxAppCombinedKeyTray(): Boolean {
        return termuxAppSession != null && isTermuxLandscapeKeyTray()
    }

    private fun activeTermuxWorkspaceKeySpecs(): Array<TermuxWorkspaceKeySpec> {
        return if (termuxWorkspaceCombinedKeyTray()) {
            termuxWorkspaceCombinedKeySpecs()
        } else if (termuxWorkspaceFnKeyMode) {
            termuxWorkspaceFnKeySpecs()
        } else {
            termuxWorkspaceNavKeySpecs()
        }
    }

    private fun activeTermuxAppKeySpecs(): Array<TermuxWorkspaceKeySpec> {
        return if (termuxAppCombinedKeyTray()) {
            termuxWorkspaceCombinedKeySpecs()
        } else if (termuxFnKeyMode) {
            termuxWorkspaceFnKeySpecs()
        } else {
            termuxWorkspaceNavKeySpecs()
        }
    }

    private fun termuxWorkspaceCombinedKeySpecs(): Array<TermuxWorkspaceKeySpec> {
        return arrayOf(
            termuxWorkspaceRefreshSpec("REFRESH"),
            termuxWorkspaceKeySpec("ESC", "Escape"),
            termuxWorkspaceKeySpec("F1", "F1"),
            termuxWorkspaceKeySpec("F2", "F2"),
            termuxWorkspaceKeySpec("F3", "F3"),
            termuxWorkspaceKeySpec("F4", "F4"),
            termuxWorkspaceKeySpec("F5", "F5"),
            termuxWorkspaceKeySpec("F6", "F6"),
            termuxWorkspaceKeySpec("F7", "F7"),
            termuxWorkspaceKeySpec("F8", "F8"),
            termuxWorkspaceKeySpec("F9", "F9"),
            termuxWorkspaceKeySpec("F10", "F10"),
            termuxWorkspaceKeySpec("↑", "Up"),
            termuxWorkspaceKeySpec("BTAB", "BTab"),
            termuxWorkspaceKeySpec("INS", "Insert"),
            termuxWorkspaceKeySpec("DEL", "Delete"),
            termuxWorkspaceRefreshSpec("REFRESH"),
            termuxWorkspaceKeySpec("/", "/"),
            termuxWorkspaceKeySpec("-", "-"),
            termuxWorkspaceKeySpec("TAB", "Tab"),
            termuxWorkspaceModifierSpec("CTRL", TermuxWorkspaceModifier.CTRL),
            termuxWorkspaceModifierSpec("ALT", TermuxWorkspaceModifier.ALT),
            termuxWorkspaceModifierSpec("SHIFT", TermuxWorkspaceModifier.SHIFT),
            termuxWorkspaceKeySpec("HOME", "Home"),
            termuxWorkspaceKeySpec("END", "End"),
            termuxWorkspaceKeySpec("PGUP", "PageUp"),
            termuxWorkspaceKeySpec("BKSP", "BSpace"),
            termuxWorkspaceKeySpec("←", "Left"),
            termuxWorkspaceKeySpec("↓", "Down"),
            termuxWorkspaceKeySpec("→", "Right"),
            termuxWorkspaceKeySpec("PGDN", "PageDown"),
            termuxWorkspaceKeySpec("ENTER", "Enter")
        )
    }

    private fun syncTermuxKeyRows(
        rowOne: ViewGroup?,
        rowTwo: ViewGroup?,
        slots: MutableList<TextView>,
        combined: Boolean,
        bindSwipe: (View?) -> Unit
    ) {
        slots.clear()
        val keysPerRow = if (combined) TERMUX_LANDSCAPE_KEYS_PER_ROW else TERMUX_PORTRAIT_KEYS_PER_ROW
        collectTermuxKeyRowSlots(rowOne, keysPerRow, slots, bindSwipe)
        collectTermuxKeyRowSlots(rowTwo, keysPerRow, slots, bindSwipe)
    }

    private fun collectTermuxKeyRowSlots(
        row: ViewGroup?,
        desiredCount: Int,
        slots: MutableList<TextView>,
        bindSwipe: (View?) -> Unit
    ) {
        if (row == null) {
            return
        }
        while (row.childCount < desiredCount) {
            val key = TextView(row.context)
            key.layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
            key.gravity = Gravity.CENTER
            key.includeFontPadding = false
            key.isAllCaps = false
            key.textSize = 11f
            row.addView(key)
        }
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child !is TextView) {
                child.visibility = if (i < desiredCount) View.VISIBLE else View.GONE
                continue
            }
            val params = child.layoutParams
            if (params is LinearLayout.LayoutParams) {
                if (params.width != 0 || params.height != ViewGroup.LayoutParams.MATCH_PARENT || params.weight != 1f) {
                    params.width = 0
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    params.weight = 1f
                    child.layoutParams = params
                }
            }
            if (i < desiredCount) {
                child.visibility = View.VISIBLE
                slots.add(child)
                bindSwipe(child)
            } else {
                child.visibility = View.GONE
                child.setOnClickListener(null)
            }
        }
    }

    private fun termuxWorkspaceNavKeySpecs(): Array<TermuxWorkspaceKeySpec> {
        return arrayOf(
            termuxWorkspaceRefreshSpec("REFRESH"),
            termuxWorkspaceKeySpec("ESC", "Escape"),
            termuxWorkspaceKeySpec("/", "/"),
            termuxWorkspaceKeySpec("-", "-"),
            termuxWorkspaceKeySpec("HOME", "Home"),
            termuxWorkspaceKeySpec("↑", "Up"),
            termuxWorkspaceKeySpec("END", "End"),
            termuxWorkspaceKeySpec("PGUP", "PageUp"),
            termuxWorkspaceKeySpec("TAB", "Tab"),
            termuxWorkspaceModifierSpec("CTRL", TermuxWorkspaceModifier.CTRL),
            termuxWorkspaceModifierSpec("ALT", TermuxWorkspaceModifier.ALT),
            termuxWorkspaceModifierSpec("SHIFT", TermuxWorkspaceModifier.SHIFT),
            termuxWorkspaceKeySpec("←", "Left"),
            termuxWorkspaceKeySpec("↓", "Down"),
            termuxWorkspaceKeySpec("→", "Right"),
            termuxWorkspaceKeySpec("PGDN", "PageDown")
        )
    }

    private fun termuxWorkspaceFnKeySpecs(): Array<TermuxWorkspaceKeySpec> {
        return arrayOf(
            termuxWorkspaceRefreshSpec("REFRESH"),
            termuxWorkspaceKeySpec("F1", "F1"),
            termuxWorkspaceKeySpec("F2", "F2"),
            termuxWorkspaceKeySpec("F3", "F3"),
            termuxWorkspaceKeySpec("F4", "F4"),
            termuxWorkspaceKeySpec("F5", "F5"),
            termuxWorkspaceKeySpec("F6", "F6"),
            termuxWorkspaceKeySpec("F7", "F7"),
            termuxWorkspaceKeySpec("INS", "Insert"),
            termuxWorkspaceKeySpec("DEL", "Delete"),
            termuxWorkspaceKeySpec("F8", "F8"),
            termuxWorkspaceKeySpec("F9", "F9"),
            termuxWorkspaceKeySpec("F10", "F10"),
            termuxWorkspaceKeySpec("BTAB", "BTab"),
            termuxWorkspaceKeySpec("ENTER", "Enter"),
            termuxWorkspaceKeySpec("BKSP", "BSpace")
        )
    }

    private fun termuxWorkspaceKeySpec(label: String, keyName: String): TermuxWorkspaceKeySpec {
        return TermuxWorkspaceKeySpec(label, keyName, null, false, false)
    }

    private fun termuxWorkspaceRefreshSpec(label: String): TermuxWorkspaceKeySpec {
        return TermuxWorkspaceKeySpec(label, null, null, false, true)
    }

    private fun termuxWorkspaceModifierSpec(
        label: String,
        modifier: TermuxWorkspaceModifier
    ): TermuxWorkspaceKeySpec {
        return TermuxWorkspaceKeySpec(label, null, modifier, false, false)
    }

    private fun handleTermuxWorkspaceKeySpec(spec: TermuxWorkspaceKeySpec) {
        if (spec.togglesMode) {
            setTermuxWorkspaceFnKeyMode(!termuxWorkspaceFnKeyMode)
            return
        }
        if (spec.refreshes) {
            clearTermuxWorkspaceModifiers()
            refreshTermuxWorkspace(true)
            return
        }
        when (spec.modifier) {
            TermuxWorkspaceModifier.CTRL -> termuxWorkspaceCtrlPending = !termuxWorkspaceCtrlPending
            TermuxWorkspaceModifier.ALT -> termuxWorkspaceAltPending = !termuxWorkspaceAltPending
            TermuxWorkspaceModifier.SHIFT -> termuxWorkspaceShiftPending = !termuxWorkspaceShiftPending
            null -> {
                val keyName = spec.keyName ?: return
                sendTermuxWorkspaceKey(applyTermuxWorkspaceModifiers(keyName))
                return
            }
        }
        updateTermuxWorkspaceModifierButtons()
    }

    private fun setTermuxWorkspaceFnKeyMode(enabled: Boolean): Boolean {
        if (termuxWorkspaceCombinedKeyTray()) {
            return false
        }
        if (termuxWorkspaceFnKeyMode == enabled) {
            return false
        }
        termuxWorkspaceTools?.animate()?.cancel()
        termuxWorkspaceKeyModeAnimating = false
        termuxWorkspaceTools?.translationX = 0f
        termuxWorkspaceTools?.alpha = 1f
        termuxWorkspaceFnKeyMode = enabled
        updateTermuxWorkspaceKeyMode()
        return true
    }

    private fun animateTermuxWorkspaceFnKeyMode(enabled: Boolean, direction: Int): Boolean {
        if (termuxWorkspaceCombinedKeyTray() || termuxWorkspaceFnKeyMode == enabled || termuxWorkspaceKeyModeAnimating) {
            return false
        }
        val tools = termuxWorkspaceTools
        val width = tools?.width ?: 0
        if (tools == null || width <= 0) {
            return setTermuxWorkspaceFnKeyMode(enabled)
        }
        val cleanDirection = if (direction < 0) -1 else 1
        termuxWorkspaceKeyModeAnimating = true
        tools.animate().cancel()
        tools.animate()
            .translationX((-cleanDirection * width).toFloat())
            .alpha(0.08f)
            .setDuration(TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_OUT_MS)
            .withEndAction(Runnable {
                termuxWorkspaceFnKeyMode = enabled
                updateTermuxWorkspaceKeyMode()
                tools.translationX = (cleanDirection * width).toFloat()
                tools.alpha = 0.08f
                tools.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_IN_MS)
                    .withEndAction(Runnable {
                        tools.translationX = 0f
                        tools.alpha = 1f
                        termuxWorkspaceKeyModeAnimating = false
                    })
                    .start()
            })
            .start()
        return true
    }

    private fun applyTermuxWorkspaceModifiers(keyName: String): String {
        val ctrl = termuxWorkspaceCtrlPending
        val alt = termuxWorkspaceAltPending
        val shift = termuxWorkspaceShiftPending
        if (!ctrl && !alt && !shift) {
            return keyName
        }
        termuxWorkspaceCtrlPending = false
        termuxWorkspaceAltPending = false
        termuxWorkspaceShiftPending = false
        updateTermuxWorkspaceModifierButtons()
        val prefix = StringBuilder()
        if (ctrl) {
            prefix.append("C-")
        }
        if (alt) {
            prefix.append("M-")
        }
        if (shift) {
            prefix.append("S-")
        }
        return prefix.append(keyName).toString()
    }

    private fun clearTermuxWorkspaceModifiers() {
        if (!termuxWorkspaceCtrlPending && !termuxWorkspaceAltPending && !termuxWorkspaceShiftPending) {
            return
        }
        termuxWorkspaceCtrlPending = false
        termuxWorkspaceAltPending = false
        termuxWorkspaceShiftPending = false
        updateTermuxWorkspaceModifierButtons()
    }

    private fun hasTermuxWorkspacePendingModifiers(): Boolean {
        return termuxWorkspaceCtrlPending || termuxWorkspaceAltPending || termuxWorkspaceShiftPending
    }

    private fun updateTermuxWorkspaceModifierButtons() {
        val normalColor = notificationWidgetTextColor()
        val activeColor = terminalBorderColor()
        termuxWorkspaceCtrlKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "CTRL", termuxWorkspaceCtrlPending, normalColor, activeColor)
        }
        termuxWorkspaceAltKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "ALT", termuxWorkspaceAltPending, normalColor, activeColor)
        }
        termuxWorkspaceShiftKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "SHIFT", termuxWorkspaceShiftPending, normalColor, activeColor)
        }
    }

    private fun updateTermuxWorkspaceModifierButton(
        key: TextView,
        label: String,
        active: Boolean,
        normalColor: Int,
        activeColor: Int
    ) {
        key.text = if (active) "$label*" else label
        key.alpha = if (active) 1f else 0.82f
        if (active && mContext != null) {
            key.setTextColor(terminalWindowBackground())
            key.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    activeColor,
                    notificationWidgetTextColor(),
                    1f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        } else {
            key.setTextColor(normalColor)
            key.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun styleTermuxWorkspace() {
        val borderColor = terminalBorderColor()
        val textColor = notificationWidgetTextColor()
        val bgColor = terminalWindowBackground()
        val labelBg = terminalHeaderTabBackground()

        termuxWorkspaceBorder?.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                bgColor,
                borderColor,
                1.5f,
                outputCornerRadius(),
                dashedBorders()
            )
        )
        termuxWorkspaceLabel?.let { label ->
            label.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            label.setTextSize(outputHeaderTextSize().toFloat())
            label.setTextColor(textColor)
            label.includeFontPadding = false
            label.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        styleTermuxWorkspaceChromeButtons()
        TerminalBorderRuntime.bind(termuxWorkspaceBorder, termuxWorkspaceLabel)

        termuxWorkspaceOutput?.let { output ->
            output.setTypeface(Tuils.getTypeface(mContext))
            output.setTextColor(textColor)
            output.setTextIsSelectable(false)
            output.setHorizontallyScrolling(true)
            output.isFocusable = false
            output.isFocusableInTouchMode = false
            output.includeFontPadding = false
            output.setLineSpacing(0f, 1f)
            output.visibility = View.GONE
        }
        termuxWorkspaceGrid?.let { grid ->
            grid.setTerminalTypeface(Tuils.getTypeface(mContext))
            grid.setTerminalTextSizeSp(12f)
            grid.updateThemeColors(textColor, bgColor, borderColor)
        }
        termuxWorkspaceOutputPanel?.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                ColorUtils.blendARGB(bgColor, Color.BLACK, 0.1f),
                ColorUtils.setAlphaComponent(borderColor, 210),
                1.2f,
                outputCornerRadius(),
                dashedBorders()
            )
        )
        termuxWorkspaceOutputLabel?.let { label ->
            label.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            label.setTextSize(max(10f, outputHeaderTextSize().toFloat() - 2f))
            label.setTextColor(textColor)
            label.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        TerminalBorderRuntime.bind(termuxWorkspaceOutputPanel, termuxWorkspaceOutputLabel)

        termuxWorkspacePrefix?.let { prefix ->
            prefix.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            prefix.setTextColor(textColor)
        }
        termuxWorkspaceInput?.let { input ->
            input.setTypeface(Tuils.getTypeface(mContext))
            input.setTextColor(textColor)
            input.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150))
        }
        termuxWorkspaceSend?.let { send ->
            send.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            send.setTextColor(textColor)
        }
        termuxWorkspaceInputGroup?.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                ColorUtils.blendARGB(bgColor, Color.BLACK, 0.16f),
                ColorUtils.setAlphaComponent(borderColor, 180),
                1.2f,
                outputCornerRadius(),
                dashedBorders()
            )
        )
        if (termuxWorkspaceTools != null) {
            termuxWorkspaceTools!!.setBackgroundColor(Color.TRANSPARENT)
            styleTermuxToolButtons(termuxWorkspaceTools, textColor)
            updateTermuxWorkspaceModifierButtons()
        }
        collapseTermuxWorkspaceInputHost()
    }

    private fun styleTermuxWorkspaceButton(button: TextView) {
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        button.setTextColor(notificationWidgetTextColor())
        button.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(terminalBorderColor(), 190),
                1f,
                outputCornerRadius(),
                dashedBorders()
            )
        )
    }

    private fun styleTermuxWorkspaceChromeButtons() {
        val root = termuxWorkspaceRoot ?: return
        styleTermuxWorkspaceChromeIcon(root.findViewById<TextView?>(R.id.termux_workspace_home), R.drawable.ic_toolbar_home_24)
        styleTermuxWorkspaceChromeIcon(root.findViewById<TextView?>(R.id.termux_workspace_new), R.drawable.ic_workspace_add_24)
        styleTermuxWorkspaceChromeIcon(root.findViewById<TextView?>(R.id.termux_workspace_ime), R.drawable.ic_workspace_keyboard_24)
    }

    private fun styleTermuxWorkspaceChromeButton(button: TextView?) {
        if (button == null || mContext == null) {
            return
        }
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        button.setTextColor(notificationWidgetTextColor())
        button.includeFontPadding = false
        button.setPadding(
            Tuils.dpToPx(mContext, 3),
            0,
            Tuils.dpToPx(mContext, 3),
            0
        )
        button.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, terminalHeaderTabBackground()))
    }

    private fun styleTermuxWorkspaceChromeIcon(button: TextView?, drawableId: Int) {
        styleTermuxWorkspaceChromeButton(button)
        if (button == null || mContext == null) {
            return
        }
        val icon = ContextCompat.getDrawable(mContext!!, drawableId) ?: return
        val wrapped = DrawableCompat.wrap(icon).mutate()
        DrawableCompat.setTint(wrapped, notificationWidgetTextColor())
        val size = Tuils.dpToPx(mContext, 12)
        wrapped.setBounds(0, 0, size, size)
        button.text = Tuils.EMPTYSTRING
        button.compoundDrawablePadding = 0
        button.setCompoundDrawables(wrapped, null, null, null)
    }

    private fun openTermuxWorkspacePage(startSession: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openTermuxWorkspacePage(startSession) }
            return
        }
        if (viewPager.currentItem != TERMUX_WORKSPACE_PAGE_INDEX) {
            viewPager.setCurrentItem(TERMUX_WORKSPACE_PAGE_INDEX, true)
        }
        setTermuxWorkspaceChromeActive(true)
        styleTermuxWorkspace()
        focusTermuxWorkspaceInput(false)
        if (startSession) {
            refreshTermuxWorkspace(true)
        }
    }

    private fun openHomePage() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openHomePage() }
            return
        }
        stopTermuxWorkspaceSocketClient()
        setTermuxWorkspaceChromeActive(false)
        viewPager.setCurrentItem(0, true)
    }

    private fun setTermuxWorkspaceChromeActive(active: Boolean) {
        if (termuxWorkspaceChromeActive == active) {
            return
        }
        termuxWorkspaceChromeActive = active
        applyResponsiveLandscapeLayout(currentConfiguration)
        if (active) {
            headerContainer?.let { header ->
                termuxWorkspaceHeaderVisibility = header.visibility
                header.visibility = View.GONE
            }
            terminalTrayContainer?.let { tray ->
                termuxWorkspaceTrayVisibility = tray.visibility
                tray.visibility = View.GONE
            }
            return
        }
        headerContainer?.visibility = termuxWorkspaceHeaderVisibility
        terminalTrayContainer?.visibility = termuxWorkspaceTrayVisibility
        applyResponsiveLandscapeLayout(currentConfiguration)
    }

    private fun focusTermuxWorkspaceInput(showKeyboard: Boolean) {
        val input = termuxWorkspaceInput ?: return
        input.setShowSoftInputOnFocus(showKeyboard)
        input.setCursorVisible(true)
        input.requestFocusFromTouch()
        input.requestFocus()
        if (showKeyboard) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideTermuxWorkspaceKeyboard() {
        val input = termuxWorkspaceInput
        if (input != null) {
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            input.setShowSoftInputOnFocus(false)
        }
        imeInsetVisible = false
        imeBottomOffset = 0
        applyTermuxWorkspaceImeBottomPadding()
        updateKeyboardLayoutState(false, if (mRootView != null) mRootView.getHeight() else 0)
        val root = termuxWorkspaceRoot ?: return
        root.post(Runnable {
            termuxWorkspaceLastRenderedFrameKey = null
            refreshTermuxWorkspaceSocketGeometry(true)
        })
        root.postDelayed(Runnable {
            termuxWorkspaceLastRenderedFrameKey = null
            refreshTermuxWorkspaceSocketGeometry(true)
        }, TERMUX_WORKSPACE_IME_RESIZE_REFRESH_DELAY_MS)
    }

    private fun scheduleTermuxWorkspaceConfigurationRecovery() {
        if (!termuxWorkspaceChromeActive) {
            return
        }
        val root = termuxWorkspaceRoot ?: return
        val generation = ++termuxWorkspaceConfigurationRefreshGeneration
        imeInsetVisible = false
        imeBottomOffset = 0
        termuxWorkspaceMeasuredLineHeight = 0
        applyTermuxWorkspaceImeBottomPadding()
        val delays = longArrayOf(0L, 90L, 220L, 420L, 760L, 1180L)
        for (delay in delays) {
            val action = Runnable {
                recoverTermuxWorkspaceAfterConfigurationChange(generation, delay >= 220L)
            }
            if (delay == 0L) {
                root.post(action)
            } else {
                root.postDelayed(action, delay)
            }
        }
    }

    private fun recoverTermuxWorkspaceAfterConfigurationChange(generation: Int, refocus: Boolean) {
        if (generation != termuxWorkspaceConfigurationRefreshGeneration || !termuxWorkspaceChromeActive) {
            return
        }
        val host = termuxWorkspaceScroll ?: termuxWorkspaceOutputPanel
        if ((host?.width ?: 0) <= 0 || visibleTermuxWorkspaceHostHeight(host) <= 0) {
            return
        }
        collapseTermuxWorkspaceInputHost()
        termuxWorkspaceLastRenderedFrameKey = null
        resetTermuxWorkspaceViewportOrigin()
        if (termuxWorkspaceSocketClient?.connected == true) {
            refreshTermuxWorkspaceSocketGeometry(true)
        } else {
            refreshTermuxWorkspace(false)
        }
        if (refocus) {
            focusTermuxWorkspaceInput(false)
        }
    }

    private fun syncTermuxWorkspaceImeFromVisibleFrame() {
        val root = termuxWorkspaceRoot ?: return
        if (!termuxWorkspaceChromeActive || root.height <= 0) {
            return
        }
        val visibleFrame = Rect()
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val rootLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        val hiddenBottom = max(0, rootLocation[1] + root.height - visibleFrame.bottom)
        val threshold = UIUtils.dpToPx(mContext!!, TERMUX_WORKSPACE_IME_VISIBLE_THRESHOLD_DP)
        val imeVisibleNow = hiddenBottom > threshold
        val bottomOffsetNow = if (imeVisibleNow) hiddenBottom else 0
        if (imeVisibleNow == imeInsetVisible && bottomOffsetNow == imeBottomOffset) {
            return
        }
        imeInsetVisible = imeVisibleNow
        imeBottomOffset = bottomOffsetNow
        applyTermuxWorkspaceImeBottomPadding()
        updateKeyboardLayoutState(imeVisibleNow, if (mRootView != null) mRootView.getHeight() else root.height)
        termuxWorkspaceLastRenderedFrameKey = null
        refreshTermuxWorkspaceSocketGeometry(true)
    }

    private fun handleTermuxWorkspaceBackPressed(): Boolean {
        if (!termuxWorkspaceChromeActive) {
            return false
        }
        if (keyboardVisible || imeInsetVisible || imeBottomOffset > 0) {
            hideTermuxWorkspaceKeyboard()
            return true
        }
        openHomePage()
        return true
    }

    private fun handleTermuxWorkspaceOutputTouch(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                termuxWorkspaceTouchStartX = event.rawX
                termuxWorkspaceTouchStartY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - termuxWorkspaceTouchStartX
                val dy = event.rawY - termuxWorkspaceTouchStartY
                if (abs(dx) > TERMUX_WORKSPACE_SWIPE_THRESHOLD_PX && abs(dx) > abs(dy) * 1.4f) {
                    switchTermuxWorkspaceWindow(if (dx < 0) "next" else "prev")
                    return true
                }
                focusTermuxWorkspaceInput(true)
                return true
            }
        }
        return true
    }

    private fun handleTermuxWorkspaceKeyModeTouch(event: MotionEvent?): Boolean {
        if (event == null) {
            return false
        }
        if (termuxWorkspaceKeyModeAnimating) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                termuxWorkspaceKeyTouchStartX = event.rawX
                termuxWorkspaceKeyTouchStartY = event.rawY
                termuxWorkspaceKeySwipeConsumed = false
                return false
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - termuxWorkspaceKeyTouchStartX
                val dy = event.rawY - termuxWorkspaceKeyTouchStartY
                if (!termuxWorkspaceCombinedKeyTray()
                    && abs(dx) > TERMUX_WORKSPACE_KEY_MODE_SWIPE_THRESHOLD_PX
                    && abs(dx) > abs(dy) * 1.35f
                ) {
                    termuxWorkspaceKeySwipeConsumed = true
                    animateTermuxWorkspaceFnKeyMode(!termuxWorkspaceFnKeyMode, if (dx < 0) 1 else -1)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                termuxWorkspaceKeyTouchStartX = 0f
                termuxWorkspaceKeyTouchStartY = 0f
                termuxWorkspaceKeySwipeConsumed = false
            }
        }
        return false
    }

    private fun handleTermuxWorkspaceHardwareModifiedKey(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null || event.action != KeyEvent.ACTION_DOWN || !hasTermuxWorkspacePendingModifiers()) {
            return false
        }
        val text = termuxWorkspaceHardwarePrintableText(keyCode, event) ?: return false
        val key = mapTermuxWorkspacePrintableCombo(text) ?: return false
        clearTermuxWorkspaceModifiers()
        sendTermuxWorkspaceKey(key)
        return true
    }

    private fun termuxWorkspaceHardwarePrintableText(keyCode: Int, event: KeyEvent): String? {
        val unicode = event.unicodeChar
        if (unicode > 0) {
            return unicode.toChar().toString()
        }
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return ('a'.code + keyCode - KeyEvent.KEYCODE_A).toChar().toString()
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> " "
            KeyEvent.KEYCODE_LEFT_BRACKET -> "["
            KeyEvent.KEYCODE_SLASH -> "/"
            KeyEvent.KEYCODE_MINUS -> "-"
            else -> null
        }
    }

    private fun consumeTermuxWorkspaceInsertedCombo(text: Editable?) {
        val inserted = termuxWorkspaceInsertedText
        val start = termuxWorkspaceInsertedStart
        termuxWorkspaceInsertedText = null
        termuxWorkspaceInsertedStart = -1
        if (text == null || inserted.isNullOrEmpty() || start < 0) {
            return
        }
        val key = mapTermuxWorkspacePrintableCombo(inserted) ?: return
        if (start + inserted.length > text.length ||
            text.subSequence(start, start + inserted.length).toString() != inserted
        ) {
            return
        }
        termuxWorkspaceSuppressInputWatcher = true
        try {
            text.delete(start, start + inserted.length)
        } finally {
            termuxWorkspaceSuppressInputWatcher = false
        }
        clearTermuxWorkspaceModifiers()
        sendTermuxWorkspaceKey(key)
    }

    private fun clearTermuxWorkspaceDirectInput(text: Editable?) {
        if (termuxWorkspaceSuppressInputWatcher || text == null || text.isEmpty()) {
            return
        }
        termuxWorkspaceSuppressInputWatcher = true
        try {
            text.clear()
        } finally {
            termuxWorkspaceSuppressInputWatcher = false
        }
    }

    private fun handleTermuxWorkspaceDirectText(text: String) {
        if (text.isEmpty()) {
            return
        }
        if (termuxWorkspaceLocalCommandMode) {
            termuxWorkspaceLocalCommandBuffer.append(text)
            renderTermuxWorkspaceLocalCommandDraft()
            return
        }
        if (text.startsWith(":")) {
            termuxWorkspaceLocalCommandMode = true
            termuxWorkspaceLocalCommandBuffer.setLength(0)
            if (text.length > 1) {
                termuxWorkspaceLocalCommandBuffer.append(text.substring(1))
            }
            renderTermuxWorkspaceLocalCommandDraft()
            return
        }
        sendTermuxWorkspaceText(text)
    }

    private fun handleTermuxWorkspaceLocalCommandBackspace(): Boolean {
        if (!termuxWorkspaceLocalCommandMode) {
            return false
        }
        if (termuxWorkspaceLocalCommandBuffer.isNotEmpty()) {
            termuxWorkspaceLocalCommandBuffer.deleteCharAt(termuxWorkspaceLocalCommandBuffer.length - 1)
            renderTermuxWorkspaceLocalCommandDraft()
        } else {
            termuxWorkspaceLocalCommandMode = false
            renderTermuxWorkspaceLocalStatus("command cancelled")
        }
        return true
    }

    private fun submitTermuxWorkspaceLocalCommandIfActive(): Boolean {
        if (!termuxWorkspaceLocalCommandMode) {
            return false
        }
        val command = termuxWorkspaceLocalCommandBuffer.toString()
        termuxWorkspaceLocalCommandMode = false
        termuxWorkspaceLocalCommandBuffer.setLength(0)
        handleTermuxWorkspaceCommand(command)
        return true
    }

    private fun renderTermuxWorkspaceLocalCommandDraft() {
        val out = StringBuilder()
        out.append("Re:T-UI tmux workspace").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        out.append("command: :").append(termuxWorkspaceLocalCommandBuffer).append('\n')
        out.append("press Enter to run, Backspace on empty command to cancel")
        updateTermuxWorkspaceLocalOutput(out.toString())
    }

    private fun mapTermuxWorkspacePrintableCombo(text: String): String? {
        if (text.length != 1 || !hasTermuxWorkspacePendingModifiers()) {
            return null
        }
        val ch = text[0]
        val ctrl = termuxWorkspaceCtrlPending
        val alt = termuxWorkspaceAltPending
        if (!ctrl && !alt) {
            return null
        }
        if (ctrl && !alt && ch == '[') {
            return "Escape"
        }
        if (ctrl) {
            val ctrlKey = when {
                ch in 'a'..'z' || ch in 'A'..'Z' -> ch.lowercaseChar().toString()
                ch == ' ' -> "Space"
                else -> null
            } ?: return null
            return if (alt) "C-M-$ctrlKey" else "C-$ctrlKey"
        }
        val altKey = tmuxWorkspacePrintableKeyName(ch) ?: return null
        return "M-$altKey"
    }

    private fun tmuxWorkspacePrintableKeyName(ch: Char): String? {
        if (Character.isISOControl(ch)) {
            return null
        }
        return if (ch == ' ') "Space" else ch.toString()
    }

    private fun submitTermuxWorkspaceInput(rawCommand: String?) {
        val command = rawCommand?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
        clearTermuxWorkspaceModifiers()
        if (command.startsWith(":")) {
            handleTermuxWorkspaceCommand(command.substring(1).trim { it <= ' ' })
            return
        }
        if (sendTermuxWorkspaceSocketInput(command)) {
            return
        }
        renderTermuxWorkspaceStatus(if (command.isEmpty()) "sent enter" else "sent: " + command)
        dispatchTermuxWorkspaceScript("send", buildTermuxWorkspaceSendScript(command), true)
    }

    private fun submitTermuxWorkspaceInputFromField() {
        val input = termuxWorkspaceInput ?: return
        val command = input.text?.toString() ?: Tuils.EMPTYSTRING
        input.setText(Tuils.EMPTYSTRING)
        submitTermuxWorkspaceInput(command)
    }

    private fun handleTermuxWorkspaceCommand(command: String) {
        val parsed = splitTermuxWorkspaceCommand(command)
        val verb = parsed.first.lowercase(Locale.getDefault())
        val rest = parsed.second
        if (verb.isEmpty() || "help" == verb) {
            renderTermuxWorkspaceHelp()
        } else if ("new" == verb) {
            newTermuxWorkspaceWindow(rest)
        } else if ("launch" == verb || "run" == verb) {
            launchTermuxWorkspaceLauncher(rest)
        } else if ("save" == verb) {
            saveTermuxWorkspaceLauncher(rest)
        } else if ("rm" == verb || "remove" == verb) {
            removeTermuxWorkspaceLauncher(rest)
        } else if ("status" == verb || "diagnostics" == verb) {
            renderTermuxWorkspaceStatusReport()
        } else if ("reconnect" == verb) {
            reconnectTermuxWorkspace()
        } else if ("prev" == verb || "left" == verb) {
            switchTermuxWorkspaceWindow("prev")
        } else if ("next" == verb || "right" == verb) {
            switchTermuxWorkspaceWindow("next")
        } else if ("refresh" == verb || "r" == verb) {
            refreshTermuxWorkspace(true)
        } else if ("home" == verb || "back" == verb) {
            openHomePage()
        } else {
            renderTermuxWorkspaceLocalStatus("Unknown workspace command: :" + command)
        }
    }

    private fun splitTermuxWorkspaceCommand(command: String): Pair<String, String> {
        val clean = command.trim { it <= ' ' }
        if (clean.isEmpty()) {
            return Pair(Tuils.EMPTYSTRING, Tuils.EMPTYSTRING)
        }
        val separator = clean.indexOf(' ')
        if (separator < 0) {
            return Pair(clean, Tuils.EMPTYSTRING)
        }
        return Pair(
            clean.substring(0, separator).trim { it <= ' ' },
            clean.substring(separator + 1).trim { it <= ' ' }
        )
    }

    private fun renderTermuxWorkspaceHelp() {
        val out = StringBuilder()
        out.append("Re:T-UI tmux workspace").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        out.append("local commands").append('\n')
        out.append(":launch [id] - list or open a launcher").append('\n')
        out.append(":save <id> <command> - save a launcher").append('\n')
        out.append(":rm <id> - remove a saved launcher").append('\n')
        out.append(":new [name] :prev :next :refresh :status :reconnect :home").append('\n')
        out.append("Normal input is sent to tmux.")
        updateTermuxWorkspaceLocalOutput(out.toString())
    }

    private fun launchTermuxWorkspaceLauncher(rawId: String) {
        val context = mContext ?: return
        val id = TermuxWorkspaceLauncherManager.normalizeId(rawId)
        if (id.isEmpty()) {
            renderTermuxWorkspaceLauncherList()
            return
        }
        val launcher = TermuxWorkspaceLauncherManager.resolve(context, id)
        if (launcher == null) {
            renderTermuxWorkspaceLocalStatus("Unknown launcher: " + rawId + ". Run :launch to list available launchers.")
            return
        }
        newTermuxWorkspaceWindow(launcher.id, launcher.command)
    }

    private fun renderTermuxWorkspaceLauncherList() {
        val context = mContext ?: return
        val launchers = TermuxWorkspaceLauncherManager.list(context)
        val out = StringBuilder()
        out.append("tmux launchers").append('\n')
        for (launcher in launchers) {
            out.append(if (launcher.builtIn) "* " else "+ ")
                .append(launcher.id)
                .append(" - ")
                .append(launcher.title)
            if (!TextUtils.isEmpty(launcher.command)) {
                out.append(" -> ").append(launcher.command)
            }
            out.append('\n')
        }
        out.append("----").append('\n')
        out.append(":launch <id>").append('\n')
        out.append(":save <id> <command>").append('\n')
        out.append(":rm <id>")
        updateTermuxWorkspaceLocalOutput(out.toString())
    }

    private fun saveTermuxWorkspaceLauncher(args: String) {
        val context = mContext ?: return
        val parsed = splitTermuxWorkspaceCommand(args)
        val id = parsed.first
        val command = parsed.second
        if (id.isEmpty() || command.isEmpty()) {
            renderTermuxWorkspaceLocalStatus("usage: :save <id> <command>")
            return
        }
        if (TermuxWorkspaceLauncherManager.save(context, id, command)) {
            renderTermuxWorkspaceLocalStatus("saved launcher: " + TermuxWorkspaceLauncherManager.normalizeId(id))
        } else {
            renderTermuxWorkspaceLocalStatus("Could not save launcher. Use a custom id and a non-empty command.")
        }
    }

    private fun removeTermuxWorkspaceLauncher(args: String) {
        val context = mContext ?: return
        val id = TermuxWorkspaceLauncherManager.normalizeId(args)
        if (id.isEmpty()) {
            renderTermuxWorkspaceLocalStatus("usage: :rm <id>")
            return
        }
        if (TermuxWorkspaceLauncherManager.remove(context, id)) {
            renderTermuxWorkspaceLocalStatus("removed launcher: $id")
        } else {
            renderTermuxWorkspaceLocalStatus("No saved launcher removed: $id")
        }
    }

    private fun renderTermuxWorkspaceStatusReport() {
        val context = mContext ?: return
        val status = TermuxBridgeManager.status(context)
        val geometry = calculateTermuxWorkspaceGeometry()
        val client = termuxWorkspaceSocketClient
        val launchers = TermuxWorkspaceLauncherManager.list(context)
        val savedCount = launchers.count { !it.builtIn }
        val out = StringBuilder()
        out.append("tmux workspace diagnostics").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        out.append("geometry: ").append(geometry.cols).append('x').append(geometry.rows).append('\n')
        out.append("termux installed: ").append(status.termuxInstalled).append('\n')
        out.append("RUN_COMMAND declared: ").append(status.runCommandDeclared).append('\n')
        out.append("RUN_COMMAND granted: ").append(status.runCommandGranted).append('\n')
        out.append("socket connected: ").append(client?.connected == true).append('\n')
        out.append("socket name cached: ").append(!TextUtils.isEmpty(termuxWorkspaceSocketName)).append('\n')
        out.append("launchers: ").append(launchers.size - savedCount).append(" built-in, ")
            .append(savedCount).append(" saved").append('\n')
        out.append("recovery: run :reconnect, then :refresh if the pane is stale")
        updateTermuxWorkspaceLocalOutput(out.toString())
    }

    private fun reconnectTermuxWorkspace() {
        stopTermuxWorkspaceSocketClient()
        renderTermuxWorkspaceStatus("reconnecting")
        refreshTermuxWorkspace(true)
    }

    private fun refreshTermuxWorkspace(announce: Boolean) {
        clearTermuxWorkspaceLocalOutputHold()
        if (captureTermuxWorkspaceSocket()) {
            return
        }
        if (announce) {
            renderTermuxWorkspaceStatus("refreshing")
        }
        dispatchTermuxWorkspaceScript("capture", buildTermuxWorkspaceCaptureScript(), true)
    }

    private fun newTermuxWorkspaceWindow(name: String?, initialCommand: String? = null) {
        clearTermuxWorkspaceLocalOutputHold()
        val clean = name?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
        val command = initialCommand?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING
        if (newTermuxWorkspaceSocketWindow(clean, command)) {
            return
        }
        val label = if (clean.isEmpty()) "new tmux window" else "new tmux window: " + clean
        renderTermuxWorkspaceStatus(if (command.isEmpty()) label else "$label -> $command")
        dispatchTermuxWorkspaceScript("new", buildTermuxWorkspaceNewWindowScript(clean, command), true)
    }

    private fun switchTermuxWorkspaceWindow(direction: String) {
        clearTermuxWorkspaceLocalOutputHold()
        if (switchTermuxWorkspaceSocketWindow(direction)) {
            return
        }
        renderTermuxWorkspaceStatus(direction + " tmux window")
        dispatchTermuxWorkspaceScript("switch", buildTermuxWorkspaceSwitchScript(direction), true)
    }

    private fun sendTermuxWorkspaceKey(key: String) {
        clearTermuxWorkspaceLocalOutputHold()
        val clean = key.trim { it <= ' ' }
        if (clean.isEmpty()) {
            return
        }
        if (sendTermuxWorkspaceSocketKey(clean)) {
            return
        }
        renderTermuxWorkspaceStatus("key: " + clean)
        dispatchTermuxWorkspaceScript("key", buildTermuxWorkspaceKeyScript(clean), true)
    }

    private fun sendTermuxWorkspaceText(text: String) {
        clearTermuxWorkspaceLocalOutputHold()
        if (text.isEmpty()) {
            return
        }
        if (sendTermuxWorkspaceSocketText(text)) {
            return
        }
        renderTermuxWorkspaceStatus("typing")
        dispatchTermuxWorkspaceScript("type", buildTermuxWorkspaceTypeScript(text), true)
    }

    private fun captureTermuxWorkspaceSocket(): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.capture(geometry.cols, geometry.rows)
    }

    private fun sendTermuxWorkspaceSocketInput(input: String): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.sendInput(input, geometry.cols, geometry.rows)
    }

    private fun sendTermuxWorkspaceSocketText(text: String): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.typeInput(text, geometry.cols, geometry.rows)
    }

    private fun sendTermuxWorkspaceSocketKey(key: String): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.key(key, geometry.cols, geometry.rows)
    }

    private fun newTermuxWorkspaceSocketWindow(name: String, initialCommand: String): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.newWindow(name, initialCommand, geometry.cols, geometry.rows)
    }

    private fun switchTermuxWorkspaceSocketWindow(direction: String): Boolean {
        val client = termuxWorkspaceSocketClient ?: return false
        if (!client.connected) {
            return false
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        return client.switchWindow(direction, geometry.cols, geometry.rows)
    }

    private fun refreshTermuxWorkspaceSocketGeometry(forceCapture: Boolean) {
        val client = termuxWorkspaceSocketClient ?: return
        if (!client.connected) {
            return
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        if (forceCapture || geometry.cols != termuxWorkspaceLastCols || geometry.rows != termuxWorkspaceLastRows) {
            termuxWorkspaceLastCols = geometry.cols
            termuxWorkspaceLastRows = geometry.rows
            client.capture(geometry.cols, geometry.rows)
        }
    }

    private fun calculateTermuxWorkspaceGeometry(): TermuxWorkspaceGeometry {
        val output = termuxWorkspaceOutput
        val grid = termuxWorkspaceGrid
        val host = termuxWorkspaceScroll ?: termuxWorkspaceOutputPanel
        val width = max(0, (host?.width ?: 0) - (host?.paddingLeft ?: 0) - (host?.paddingRight ?: 0))
        val height = max(0, visibleTermuxWorkspaceHostHeight(host) - (host?.paddingTop ?: 0) - (host?.paddingBottom ?: 0))
        val charWidth = grid?.characterWidth() ?: termuxWorkspaceCharWidth(output)
        val lineHeight = grid?.lineHeight() ?: termuxWorkspaceLineHeight(output)
        val cols = if (width > 0) (width / charWidth).toInt() else termuxWorkspaceLastCols
        val rows = if (height > 0) height / max(1, lineHeight) else termuxWorkspaceLastRows
        val geometry = TermuxWorkspaceGeometry(
            max(TERMUX_WORKSPACE_MIN_COLS, min(TERMUX_WORKSPACE_MAX_COLS, cols)),
            max(TERMUX_WORKSPACE_MIN_ROWS, min(TERMUX_WORKSPACE_MAX_ROWS, rows))
        )
        applyTermuxWorkspaceCellViewport(geometry, charWidth, lineHeight)
        return geometry
    }

    private fun visibleTermuxWorkspaceHostHeight(host: View?): Int {
        if (host == null) {
            return 0
        }
        val rawHeight = host.height
        if (rawHeight <= 0 || !imeInsetVisible || imeBottomOffset <= 0) {
            return max(0, rawHeight)
        }
        val root = termuxWorkspaceRoot ?: mRootView ?: return max(0, rawHeight)
        if (root.height <= 0) {
            return max(0, rawHeight)
        }
        val rootLocation = IntArray(2)
        val hostLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        host.getLocationInWindow(hostLocation)
        val visibleRootBottom = rootLocation[1] + root.height - imeBottomOffset
        val visibleHeight = visibleRootBottom - hostLocation[1]
        return max(0, min(rawHeight, visibleHeight))
    }

    private fun termuxWorkspaceCharWidth(output: TextView?): Float {
        return if (output != null) {
            max(1f, output.paint.measureText("M"))
        } else {
            max(1f, UIUtils.dpToPx(mContext!!, 8).toFloat())
        }
    }

    private fun termuxWorkspaceLineHeight(output: TextView?): Int {
        if (output == null) {
            return UIUtils.dpToPx(mContext!!, 18)
        }
        val baseLineHeight = max(1, output.lineHeight)
        val fallbackLineHeight = max(
            baseLineHeight,
            (baseLineHeight * TERMUX_WORKSPACE_LINE_HEIGHT_FALLBACK_MULTIPLIER).roundToInt()
        )
        val sampledLineHeight = try {
            val layout = StaticLayout.Builder.obtain(
                TERMUX_WORKSPACE_LINE_HEIGHT_SAMPLE,
                0,
                TERMUX_WORKSPACE_LINE_HEIGHT_SAMPLE.length,
                output.paint,
                max(1, output.paint.measureText("MMMM").roundToInt())
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            var previousBottom = 0
            var measuredLineHeight = baseLineHeight
            for (index in 0 until layout.lineCount) {
                val bottom = layout.getLineBottom(index)
                measuredLineHeight = max(measuredLineHeight, bottom - previousBottom)
                previousBottom = bottom
            }
            measuredLineHeight
        } catch (ignored: Exception) {
            baseLineHeight
        }
        return max(fallbackLineHeight, max(sampledLineHeight, termuxWorkspaceMeasuredLineHeight))
    }

    private fun applyTermuxWorkspaceCellViewport(
        geometry: TermuxWorkspaceGeometry,
        charWidth: Float,
        lineHeight: Int
    ) {
        if (termuxWorkspaceGrid != null) {
            return
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return
        }
        val output = termuxWorkspaceGrid ?: termuxWorkspaceOutput ?: return
        val targetWidth = max(1, (geometry.cols * charWidth).roundToInt())
        val targetHeight = max(1, geometry.rows * lineHeight)
        output.minimumWidth = targetWidth
        output.minimumHeight = targetHeight
        val params = output.layoutParams ?: return
        var changed = false
        if (params.width != targetWidth) {
            params.width = targetWidth
            changed = true
        }
        if (params.height != targetHeight) {
            params.height = targetHeight
            changed = true
        }
        if (changed) {
            output.layoutParams = params
        }
        output.requestLayout()
        termuxWorkspaceScroll?.requestLayout()
        termuxWorkspaceOutputPanel?.requestLayout()
    }

    private fun dispatchTermuxWorkspaceScript(action: String, script: String, echoFailure: Boolean): Boolean {
        if (!ensureTermuxWorkspaceBridgeReady(echoFailure)) {
            return false
        }
        try {
            val sequence = ++termuxWorkspaceDispatchSequence
            TermuxBridgeManager.startRunCommand(
                mContext!!,
                TermuxBridgeManager.TERMUX_SH,
                TermuxBridgeManager.TERMUX_HOME,
                createResultPendingIntent(
                    mContext,
                    TERMUX_WORKSPACE_RESULT_PREFIX + action + ":" + sequence,
                    null
                ),
                arrayOf<String?>("-c", script, "retui-workspace")
            )
            return true
        } catch (e: SecurityException) {
            renderTermuxWorkspaceStatus("Termux rejected the workspace command: permission denied.")
        } catch (e: Exception) {
            renderTermuxWorkspaceStatus("unable to dispatch workspace command: " + e.javaClass.getSimpleName())
        }
        return false
    }

    private fun ensureTermuxWorkspaceBridgeReady(echoFailure: Boolean): Boolean {
        val status = TermuxBridgeManager.status(mContext!!)
        if (!status.termuxInstalled) {
            if (echoFailure) renderTermuxWorkspaceStatus("Termux is not installed.")
            return false
        }
        if (!status.runCommandDeclared) {
            if (echoFailure) renderTermuxWorkspaceStatus("This Termux build does not expose RUN_COMMAND.")
            return false
        }
        if (!status.runCommandGranted) {
            requestRunCommandPermissionIfPossible(
                mContext,
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            if (echoFailure) {
                renderTermuxWorkspaceStatus("RunCommand permission is not granted yet. Allow Re:T-UI and retry.")
            }
            return false
        }
        return true
    }

    private fun buildTermuxWorkspaceCaptureScript(): String {
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript("socket-start") + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspaceSendScript(input: String): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        val send = if (input.isEmpty()) {
            "tmux send-keys -t " + session + " C-m"
        } else {
            "tmux send-keys -t " + session + " -l -- " + shellQuote(input) + "\n" +
                    "tmux send-keys -t " + session + " C-m"
        }
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript("send", "RETUI_INPUT", input) + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                send + "\nsleep 0.25\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspaceTypeScript(input: String): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript("type", "RETUI_INPUT", input) + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                "tmux send-keys -t " + session + " -l -- " + shellQuote(input) + "\n" +
                "sleep 0.08\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspaceNewWindowScript(name: String, initialCommand: String = Tuils.EMPTYSTRING): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        val home = shellQuote(TermuxBridgeManager.TERMUX_HOME)
        val nameArg = if (name.isEmpty()) "" else " -n " + shellQuote(name)
        val command = initialCommand.trim { it <= ' ' }
        val sendCommand = if (command.isEmpty()) {
            Tuils.EMPTYSTRING
        } else {
            "\nsleep 0.08\n" +
                    "tmux send-keys -t " + session + " -l -- " + shellQuote(command) + "\n" +
                    "tmux send-keys -t " + session + " C-m"
        }
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript(
                    "new",
                    listOf(Pair("RETUI_WINDOW_NAME", name), Pair("RETUI_COMMAND", command))
                ) + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                buildTermuxWorkspaceTmuxCommand("new-window -t " + session + nameArg + " -c " + home) +
                sendCommand + "\n" +
                "sleep 0.15\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspaceSwitchScript(direction: String): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        val flag = if ("prev" == direction) "-p" else "-n"
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript("switch", "RETUI_DIRECTION", direction) + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                "tmux select-window -t " + session + " " + flag + "\n" +
                "sleep 0.1\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspaceKeyScript(key: String): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        return buildTermuxWorkspacePreambleScript() + "\n" +
                buildTermuxWorkspaceBridgeScript("key", "RETUI_KEY", key) + "\n" +
                buildTermuxWorkspaceEnsureScript() + "\n" +
                buildTermuxWorkspaceResizeScript() + "\n" +
                "tmux send-keys -t " + session + " -- " + shellQuote(key) + "\n" +
                "sleep 0.08\n" +
                buildTermuxWorkspaceFrameScript()
    }

    private fun buildTermuxWorkspacePreambleScript(): String {
        val home = shellQuote(TermuxBridgeManager.TERMUX_HOME)
        val termuxBin = shellQuote(TermuxBridgeManager.TERMUX_SH.substringBeforeLast('/'))
        return ("export HOME=" + home + "\n"
                + "export PATH=" + termuxBin + ":\$PATH\n"
                + "cd " + home + " 2>/dev/null || true")
    }

    private fun buildTermuxWorkspaceBridgeScript(action: String): String {
        return buildTermuxWorkspaceBridgeScript(action, emptyList())
    }

    private fun buildTermuxWorkspaceBridgeScript(action: String, envName: String?, envValue: String?): String {
        if (envName == null) {
            return buildTermuxWorkspaceBridgeScript(action, emptyList())
        }
        return buildTermuxWorkspaceBridgeScript(action, listOf(Pair(envName, envValue)))
    }

    private fun buildTermuxWorkspaceBridgeScript(action: String, envValues: List<Pair<String, String?>>): String {
        val env = StringBuilder()
        val geometry = calculateTermuxWorkspaceGeometry()
        env.append("RETUI_COLS=").append(shellQuote(geometry.cols.toString())).append(' ')
        env.append("RETUI_ROWS=").append(shellQuote(geometry.rows.toString())).append(' ')
        env.append("RETUI_SOCKET_NAME=").append(shellQuote(TERMUX_WORKSPACE_SOCKET_NAME)).append(' ')
        for (entry in envValues) {
            if (!TextUtils.isEmpty(entry.first)) {
                env.append(entry.first).append('=').append(shellQuote(entry.second ?: Tuils.EMPTYSTRING)).append(' ')
            }
        }
        env.append("RETUI_TCP_PORT=").append(shellQuote(TERMUX_WORKSPACE_TCP_PORT.toString())).append(' ')
        return ("if command -v retui >/dev/null 2>&1; then\n"
                + "  RETUI_SESSION=" + shellQuote(TERMUX_WORKSPACE_SESSION) + " " + env + "retui bridge " + action + "\n"
                + "  retui_status=\$?\n"
                + "  if [ \"\$retui_status\" -eq 0 ]; then exit 0; fi\n"
                + "fi")
    }

    private fun buildTermuxWorkspaceEnsureScript(): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        val home = shellQuote(TermuxBridgeManager.TERMUX_HOME)
        val fallbackShell = shellQuote(TermuxBridgeManager.TERMUX_SH)
        return ("retui_workspace_shell=\"\$(command -v bash 2>/dev/null || printf '%s\\n' " + fallbackShell + ")\"\n"
                + "if ! command -v tmux >/dev/null 2>&1; then\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "  exit 127\n"
                + "fi\n"
                + "if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  " + buildTermuxWorkspaceTmuxCommand("new-session -d -s " + session + " -n 1 -c " + home) + "\n"
                + "  sleep 0.25\n"
                + "fi")
    }

    private fun buildTermuxWorkspaceResizeScript(): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        val geometry = calculateTermuxWorkspaceGeometry()
        return ("tmux resize-window -t " + session + " -x " + geometry.cols + " -y " + geometry.rows + " 2>/dev/null || true\n"
                + "tmux resize-pane -t " + session + " -x " + geometry.cols + " -y " + geometry.rows + " 2>/dev/null || true")
    }

    private fun buildTermuxWorkspaceTmuxCommand(tmuxArgs: String): String {
        return ("if [ \"\${retui_workspace_shell##*/}\" = \"bash\" ]; then\n"
                + "  tmux " + tmuxArgs + " \"\$retui_workspace_shell\" --noprofile --norc\n"
                + "else\n"
                + "  tmux " + tmuxArgs + " \"\$retui_workspace_shell\"\n"
                + "fi")
    }

    private fun buildTermuxWorkspaceFrameScript(): String {
        val session = shellQuote(TERMUX_WORKSPACE_SESSION)
        return ("printf '%s ' '__RETUI_WINDOW__'\n"
                + "tmux display-message -p -t " + session + " '#I:#W'\n"
                + "printf '%s ' '__RETUI_WINDOWS__'\n"
                + "tmux list-windows -t " + session + " -F '#I:#W#{?window_active,*,}' | tr '\\n' ' '\n"
                + "printf '\\n'\n"
                + "printf '%s %s\\n' '__RETUI_COLS__' '" + calculateTermuxWorkspaceGeometry().cols + "'\n"
                + "printf '%s %s\\n' '__RETUI_ROWS__' '" + calculateTermuxWorkspaceGeometry().rows + "'\n"
                + "printf '%s ' '__RETUI_CURSOR__'\n"
                + "tmux display-message -p -t " + session + " '#{cursor_x}:#{cursor_y}' 2>/dev/null || printf '%s\\n' '0:0'\n"
                + "printf '%s\\n' '__RETUI_FRAME_BEGIN__'\n"
                + "tmux capture-pane -t " + session + " -p -e -N\n"
                + "printf '%s\\n' '__RETUI_FRAME_END__'")
    }

    private fun renderTermuxWorkspaceStatus(status: String?) {
        val out = StringBuilder()
        out.append("Re:T-UI tmux workspace").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        if (!TextUtils.isEmpty(status)) {
            out.append("status: ").append(status).append('\n')
        }
        out.append("----")
        updateTermuxWorkspaceOutput(out.toString())
    }

    private fun renderTermuxWorkspaceLocalStatus(status: String?) {
        val out = StringBuilder()
        out.append("Re:T-UI tmux workspace").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        if (!TextUtils.isEmpty(status)) {
            out.append("status: ").append(status).append('\n')
        }
        out.append("----")
        updateTermuxWorkspaceLocalOutput(out.toString())
    }

    private fun appendTermuxWorkspaceResult(
        label: String,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        val sequence = termuxWorkspaceResultSequence(label)
        if (sequence > 0 && sequence < termuxWorkspaceAcceptedSequence) {
            return
        }
        if (sequence > 0) {
            termuxWorkspaceAcceptedSequence = max(termuxWorkspaceAcceptedSequence, sequence)
        }
        val parsed = parseTermuxWorkspaceFrame(stdout)
        if (!TextUtils.isEmpty(parsed.socketName) && !TextUtils.isEmpty(parsed.socketToken)) {
            startTermuxWorkspaceSocketClient(parsed)
        }
        updateTermuxWorkspaceChrome(parsed)
        val hasDiagnostics = (exitCode != Int.MIN_VALUE && exitCode != 0)
                || !TextUtils.isEmpty(stderr)
                || !TextUtils.isEmpty(error)
                || !TextUtils.isEmpty(debug)
        if (!hasDiagnostics && parsed.frame != null) {
            updateTermuxWorkspaceOutput(parsed.frame, parsed.cursorX, parsed.cursorY, parsed.cols, parsed.rows)
            return
        }
        val out = StringBuilder()
        out.append("Re:T-UI tmux workspace").append('\n')
        out.append("session: ").append(TERMUX_WORKSPACE_SESSION).append('\n')
        if (!TextUtils.isEmpty(parsed.bridge)) {
            out.append("bridge: ").append(parsed.bridge)
            if (!TextUtils.isEmpty(parsed.version)) {
                out.append(' ').append(parsed.version)
            }
            out.append('\n')
        }
        if (!TextUtils.isEmpty(parsed.window)) {
            out.append("window: ").append(parsed.window).append('\n')
        }
        if (!TextUtils.isEmpty(parsed.windows)) {
            out.append("windows: ").append(parsed.windows).append('\n')
        }
        if (!TextUtils.isEmpty(parsed.socketProtocol)) {
            out.append("socket: ").append(parsed.socketProtocol).append('\n')
        }
        if (exitCode != Int.MIN_VALUE && exitCode != 0) {
            out.append("exit: ").append(exitCode).append('\n')
        }
        if (!TextUtils.isEmpty(stderr)) {
            out.append("stderr: ").append(stderr!!.trim { it <= ' ' }).append('\n')
        }
        if (!TextUtils.isEmpty(error)) {
            out.append("error: ").append(error!!.trim { it <= ' ' }).append('\n')
        }
        if (!TextUtils.isEmpty(debug)) {
            out.append("debug: ").append(debug!!.trim { it <= ' ' }).append('\n')
        }
        out.append("----")
        if (!TextUtils.isEmpty(parsed.frame)) {
            out.append('\n').append(parsed.frame)
        }
        updateTermuxWorkspaceOutput(out.toString())
    }

    private fun startTermuxWorkspaceSocketClient(frame: TermuxWorkspaceFrame) {
        val socketName = frame.socketName ?: return
        val token = frame.socketToken ?: return
        val current = termuxWorkspaceSocketClient
        if (current != null && current.connected
            && socketName == termuxWorkspaceSocketName
            && token == termuxWorkspaceSocketToken
        ) {
            refreshTermuxWorkspaceSocketGeometry(true)
            return
        }

        stopTermuxWorkspaceSocketClient()
        termuxWorkspaceSocketName = socketName
        termuxWorkspaceSocketToken = token
        val geometry = calculateTermuxWorkspaceGeometry()
        termuxWorkspaceLastCols = geometry.cols
        termuxWorkspaceLastRows = geometry.rows
        val client = TermuxWorkspaceSocketClient(object : TermuxWorkspaceSocketClient.Listener {
            override fun onFrame(frame: String) {
                runOnUiThread(Runnable {
                    if (System.currentTimeMillis() < termuxWorkspaceLocalOutputHoldUntilMs) {
                        return@Runnable
                    }
                    val parsed = parseTermuxWorkspaceFrame(frame)
                    updateTermuxWorkspaceChrome(parsed)
                    updateTermuxWorkspaceOutput(
                        parsed.frame ?: Tuils.EMPTYSTRING,
                        parsed.cursorX,
                        parsed.cursorY,
                        parsed.cols,
                        parsed.rows
                    )
                })
            }

            override fun onStatus(status: String) {
                if (!TextUtils.isEmpty(status) && !"ok".equals(status, ignoreCase = true)) {
                    runOnUiThread(Runnable { renderTermuxWorkspaceStatus("socket: $status") })
                }
            }

            override fun onError(message: String) {
                runOnUiThread(Runnable {
                    renderTermuxWorkspaceStatus("socket bridge error: $message")
                })
            }

            override fun onClosed() {
                runOnUiThread(Runnable {
                    if (socketName == termuxWorkspaceSocketName && token == termuxWorkspaceSocketToken) {
                        termuxWorkspaceSocketClient = null
                    }
                })
            }
        })
        termuxWorkspaceSocketClient = client
        client.connect(socketName, token, geometry.cols, geometry.rows, frame.tcpHost, frame.tcpPort, true)
    }

    private fun stopTermuxWorkspaceSocketClient() {
        termuxWorkspaceSocketClient?.close()
        termuxWorkspaceSocketClient = null
        termuxWorkspaceSocketName = null
        termuxWorkspaceSocketToken = null
        termuxWorkspaceLastRenderedFrameKey = null
    }

    private fun termuxWorkspaceResultSequence(label: String): Int {
        if (!label.startsWith(TERMUX_WORKSPACE_RESULT_PREFIX)) {
            return -1
        }
        val rest = label.substring(TERMUX_WORKSPACE_RESULT_PREFIX.length)
        val separator = rest.indexOf(':')
        if (separator < 0 || separator == rest.length - 1) {
            return -1
        }
        return try {
            rest.substring(separator + 1).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun parseTermuxWorkspaceFrame(stdout: String?): TermuxWorkspaceFrame {
        if (TextUtils.isEmpty(stdout)) {
            return TermuxWorkspaceFrame(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
        }
        val frame = StringBuilder()
        var window: String? = null
        var windows: String? = null
        var bridge: String? = null
        var version: String? = null
        var socketProtocol: String? = null
        var socketName: String? = null
        var socketToken: String? = null
        var tcpHost: String? = null
        var tcpPort: Int? = null
        var cols: Int? = null
        var rows: Int? = null
        var cursorX: Int? = null
        var cursorY: Int? = null
        var hasFrameMarkers = false
        val lines = stdout!!.split("\n".toRegex()).dropLastWhile { it.isEmpty() }
        for (line in lines) {
            if (line.startsWith("__RETUI_WINDOW__")) {
                window = line.substring("__RETUI_WINDOW__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_WINDOWS__")) {
                windows = line.substring("__RETUI_WINDOWS__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_BRIDGE__")) {
                bridge = line.substring("__RETUI_BRIDGE__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_VERSION__")) {
                version = line.substring("__RETUI_VERSION__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_SOCKET__")) {
                socketProtocol = line.substring("__RETUI_SOCKET__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_SOCKET_NAME__")) {
                socketName = line.substring("__RETUI_SOCKET_NAME__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_TOKEN__")) {
                socketToken = line.substring("__RETUI_TOKEN__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_TCP_HOST__")) {
                tcpHost = line.substring("__RETUI_TCP_HOST__".length).trim { it <= ' ' }
            } else if (line.startsWith("__RETUI_TCP_PORT__")) {
                tcpPort = line.substring("__RETUI_TCP_PORT__".length).trim { it <= ' ' }.toIntOrNull()
            } else if (line.startsWith("__RETUI_COLS__")) {
                cols = line.substring("__RETUI_COLS__".length).trim { it <= ' ' }.toIntOrNull()
            } else if (line.startsWith("__RETUI_ROWS__")) {
                rows = line.substring("__RETUI_ROWS__".length).trim { it <= ' ' }.toIntOrNull()
            } else if (line.startsWith("__RETUI_CURSOR__")) {
                val cursor = parseTermuxWorkspaceCursor(
                    line.substring("__RETUI_CURSOR__".length).trim { it <= ' ' }
                )
                cursorX = cursor?.first
                cursorY = cursor?.second
            } else if (line.startsWith("__RETUI_FRAME_BEGIN__")) {
                hasFrameMarkers = true
            } else if (line.startsWith("__RETUI_FRAME_END__")) {
                break
            } else if (line.startsWith("__RETUI_") && !hasFrameMarkers) {
                // Bridge metadata line.
            } else {
                if (frame.isNotEmpty()) {
                    frame.append('\n')
                }
                frame.append(line)
            }
        }
        return TermuxWorkspaceFrame(
            bridge,
            version,
            window,
            windows,
            frame.toString(),
            socketProtocol,
            socketName,
            socketToken,
            tcpHost,
            tcpPort,
            cols,
            rows,
            cursorX,
            cursorY
        )
    }

    private fun parseTermuxWorkspaceCursor(value: String): Pair<Int, Int>? {
        val parts = value.replace(':', ' ').split(" ").filter { it.isNotEmpty() }
        if (parts.size < 2) {
            return null
        }
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return Pair(max(0, x), max(0, y))
    }

    private fun updateTermuxWorkspaceChrome(frame: TermuxWorkspaceFrame) {
        if (termuxWorkspaceLabel != null) {
            termuxWorkspaceLabel!!.text = if (TextUtils.isEmpty(frame.window))
                "TERMUX WORKSPACE"
            else
                ("TMUX " + frame.window).uppercase(Locale.getDefault())
        }
        if (termuxWorkspaceOutputLabel != null) {
            val windows = frame.windows
            termuxWorkspaceOutputLabel!!.text = if (TextUtils.isEmpty(windows))
                "WINDOW"
            else
                ellipsizeWorkspaceLabel(windows!!)
        }
    }

    private fun ellipsizeWorkspaceLabel(value: String): String {
        val clean = value.trim { it <= ' ' }
        return if (clean.length <= 42) clean else clean.substring(0, 39) + "..."
    }

    private fun updateTermuxWorkspaceOutput(
        value: String?,
        cursorX: Int? = null,
        cursorY: Int? = null,
        frameCols: Int? = null,
        frameRows: Int? = null
    ) {
        termuxWorkspaceBuffer.setLength(0)
        val raw = if (value != null && value.length > TERMUX_WORKSPACE_MAX_OUTPUT_CHARS) {
            value.substring(value.length - TERMUX_WORKSPACE_MAX_OUTPUT_CHARS)
        } else {
            value
        }
        val geometry = calculateTermuxWorkspaceGeometry()
        val cols = frameCols ?: geometry.cols
        val rows = frameRows ?: geometry.rows
        val frameKey = cols.toString() + "x" + rows + ":" + (cursorX?.toString() ?: "-") + ":" + (cursorY?.toString() ?: "-") + "\n" + (raw ?: Tuils.EMPTYSTRING)
        if (frameKey == termuxWorkspaceLastRenderedFrameKey) {
            return
        }
        termuxWorkspaceLastRenderedFrameKey = frameKey
        if (raw != null) {
            termuxWorkspaceBuffer.append(raw)
        }
        val grid = termuxWorkspaceGrid
        if (grid != null) {
            grid.visibility = View.VISIBLE
            termuxWorkspaceOutput?.visibility = View.GONE
            grid.setFrame(raw, cols, rows, cursorX, cursorY)
        } else {
            val rendered = renderTermuxWorkspaceAnsi(raw)
            applyTermuxWorkspaceCursor(rendered, cursorX, cursorY)
            termuxWorkspaceOutput?.visibility = View.VISIBLE
            termuxWorkspaceOutput?.setText(rendered)
        }
        updateTermuxWorkspaceMeasuredLineHeight()
        resetTermuxWorkspaceViewportOrigin()
        termuxWorkspaceScroll?.post(Runnable { resetTermuxWorkspaceViewportOrigin() })
    }

    private fun updateTermuxWorkspaceLocalOutput(value: String?) {
        termuxWorkspaceLocalOutputHoldUntilMs =
            System.currentTimeMillis() + TERMUX_WORKSPACE_LOCAL_OUTPUT_HOLD_MS
        updateTermuxWorkspaceOutput(value)
    }

    private fun clearTermuxWorkspaceLocalOutputHold() {
        termuxWorkspaceLocalOutputHoldUntilMs = 0L
    }

    private fun updateTermuxWorkspaceMeasuredLineHeight() {
        val grid = termuxWorkspaceGrid
        if (grid != null) {
            val lineHeight = grid.lineHeight()
            if (lineHeight > termuxWorkspaceMeasuredLineHeight) {
                termuxWorkspaceMeasuredLineHeight = lineHeight
                refreshTermuxWorkspaceSocketGeometry(true)
            }
            return
        }
        val output = termuxWorkspaceOutput ?: return
        output.post(Runnable {
            val layout = output.layout ?: return@Runnable
            if (layout.lineCount <= 0) {
                return@Runnable
            }
            var previousBottom = 0
            var maxLineHeight = 0
            val linesToCheck = min(layout.lineCount, TERMUX_WORKSPACE_LINE_HEIGHT_MAX_SAMPLE_LINES)
            for (index in 0 until linesToCheck) {
                val bottom = layout.getLineBottom(index)
                maxLineHeight = max(maxLineHeight, bottom - previousBottom)
                previousBottom = bottom
            }
            if (maxLineHeight > termuxWorkspaceMeasuredLineHeight) {
                termuxWorkspaceMeasuredLineHeight = maxLineHeight
                refreshTermuxWorkspaceSocketGeometry(true)
            }
        })
    }

    private fun resetTermuxWorkspaceViewportOrigin() {
        termuxWorkspaceOutput?.scrollTo(0, 0)
        termuxWorkspaceScroll?.scrollTo(0, 0)
    }

    private fun renderTermuxWorkspaceAnsi(value: String?): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        if (TextUtils.isEmpty(value)) {
            return out
        }
        val baseForeground = notificationWidgetTextColor()
        val baseBackground = terminalWindowBackground()
        val style = TermuxAnsiStyle()
        var spanStart = 0
        var index = 0
        val text = value!!
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\u001B' && index + 1 < text.length && text[index + 1] == '[') {
                val end = findTermuxAnsiSequenceEnd(text, index + 2)
                if (end > index) {
                    applyTermuxAnsiSpan(out, spanStart, out.length, style, baseForeground, baseBackground)
                    if (text[end] == 'm') {
                        updateTermuxAnsiStyle(style, text.substring(index + 2, end))
                    }
                    spanStart = out.length
                    index = end + 1
                    continue
                }
            }
            if (ch != '\r') {
                out.append(ch)
            }
            index++
        }
        applyTermuxAnsiSpan(out, spanStart, out.length, style, baseForeground, baseBackground)
        return out
    }

    private fun applyTermuxWorkspaceCursor(out: SpannableStringBuilder, cursorX: Int?, cursorY: Int?) {
        if (out.isEmpty() || cursorX == null || cursorY == null) {
            return
        }
        var x = 0
        var y = 0
        var index = 0
        while (index < out.length) {
            if (x == cursorX && y == cursorY && out[index] != '\n') {
                out.setSpan(
                    ForegroundColorSpan(Color.BLACK),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                out.setSpan(
                    BackgroundColorSpan(notificationWidgetTextColor()),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                return
            }
            if (out[index] == '\n') {
                y++
                x = 0
            } else {
                x++
            }
            index++
        }
    }

    private fun findTermuxAnsiSequenceEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val code = text[index].code
            if (code in 0x40..0x7e) {
                return index
            }
            index++
        }
        return -1
    }

    private fun updateTermuxAnsiStyle(style: TermuxAnsiStyle, params: String) {
        val codes = parseTermuxAnsiCodes(params)
        if (codes.isEmpty()) {
            style.reset()
            return
        }
        var index = 0
        while (index < codes.size) {
            when (val code = codes[index]) {
                0 -> style.reset()
                1 -> style.bold = true
                22 -> style.bold = false
                7 -> style.reverse = true
                27 -> style.reverse = false
                39 -> style.foreground = null
                49 -> style.background = null
                in 30..37 -> style.foreground = termuxAnsiColor(code - 30, false, false)
                in 90..97 -> style.foreground = termuxAnsiColor(code - 90, true, false)
                in 40..47 -> style.background = termuxAnsiColor(code - 40, false, true)
                in 100..107 -> style.background = termuxAnsiColor(code - 100, true, true)
                38, 48 -> {
                    val foreground = code == 38
                    val parsed = parseTermuxExtendedAnsiColor(codes, index + 1, !foreground)
                    if (parsed != null) {
                        if (foreground) {
                            style.foreground = parsed.first
                        } else {
                            style.background = parsed.first
                        }
                        index = parsed.second
                    }
                }
            }
            index++
        }
    }

    private fun parseTermuxAnsiCodes(params: String): List<Int> {
        if (params.isEmpty()) {
            return listOf(0)
        }
        val codes = ArrayList<Int>()
        val parts = params.replace(':', ';').split(";")
        for (part in parts) {
            codes.add(part.toIntOrNull() ?: 0)
        }
        return codes
    }

    private fun parseTermuxExtendedAnsiColor(codes: List<Int>, start: Int, background: Boolean): Pair<Int, Int>? {
        if (start >= codes.size) {
            return null
        }
        return when (codes[start]) {
            5 -> {
                if (start + 1 >= codes.size) null
                else Pair(termuxAnsi256Color(codes[start + 1], background), start + 1)
            }
            2 -> {
                if (start + 3 >= codes.size) null
                else Pair(
                    Color.rgb(
                        codes[start + 1].coerceIn(0, 255),
                        codes[start + 2].coerceIn(0, 255),
                        codes[start + 3].coerceIn(0, 255)
                    ),
                    start + 3
                )
            }
            else -> null
        }
    }

    private fun applyTermuxAnsiSpan(
        out: SpannableStringBuilder,
        start: Int,
        end: Int,
        style: TermuxAnsiStyle,
        baseForeground: Int,
        baseBackground: Int
    ) {
        if (start >= end) {
            return
        }
        var foreground = style.foreground ?: baseForeground
        var background = style.background
        if (style.reverse) {
            val originalForeground = foreground
            foreground = background ?: Color.BLACK
            background = originalForeground
        }
        if (foreground != baseForeground || style.reverse || style.foreground != null) {
            out.setSpan(ForegroundColorSpan(foreground), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (background != null && background != baseBackground) {
            out.setSpan(BackgroundColorSpan(background), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.bold) {
            out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun termuxAnsiColor(index: Int, bright: Boolean, background: Boolean): Int {
        val clean = index.coerceIn(0, 7)
        val bg = terminalWindowBackground()
        val fg = notificationWidgetTextColor()
        val accent = terminalBorderColor()
        if (background) {
            val panel = ColorUtils.blendARGB(bg, accent, if (bright) 0.34f else 0.22f)
            val active = ColorUtils.blendARGB(accent, fg, if (bright) 0.30f else 0.12f)
            return when (clean) {
                0 -> bg
                1 -> ColorUtils.blendARGB(bg, Color.rgb(190, 62, 62), if (bright) 0.42f else 0.26f)
                2 -> ColorUtils.blendARGB(bg, accent, if (bright) 0.42f else 0.28f)
                3 -> ColorUtils.blendARGB(bg, fg, if (bright) 0.32f else 0.18f)
                4 -> panel
                5 -> ColorUtils.blendARGB(bg, accent, if (bright) 0.48f else 0.32f)
                6 -> active
                else -> ColorUtils.blendARGB(bg, fg, if (bright) 0.38f else 0.24f)
            }
        }
        return when (clean) {
            0 -> Color.BLACK
            1 -> ColorUtils.blendARGB(fg, Color.rgb(255, 70, 70), if (bright) 0.55f else 0.34f)
            2 -> ColorUtils.blendARGB(accent, fg, if (bright) 0.28f else 0.10f)
            3 -> ColorUtils.blendARGB(fg, accent, if (bright) 0.42f else 0.24f)
            4 -> ColorUtils.blendARGB(fg, accent, if (bright) 0.42f else 0.25f)
            5 -> ColorUtils.blendARGB(accent, Color.rgb(220, 150, 255), if (bright) 0.36f else 0.20f)
            6 -> ColorUtils.blendARGB(accent, fg, if (bright) 0.32f else 0.16f)
            else -> fg
        }
    }

    private fun termuxAnsi256Color(code: Int, background: Boolean): Int {
        val clean = code.coerceIn(0, 255)
        if (clean < 16) {
            return termuxAnsiColor(clean % 8, clean >= 8, background)
        }
        if (clean in 16..231) {
            val value = clean - 16
            val red = value / 36
            val green = (value / 6) % 6
            val blue = value % 6
            return Color.rgb(termuxAnsiCubeValue(red), termuxAnsiCubeValue(green), termuxAnsiCubeValue(blue))
        }
        val gray = 8 + (clean - 232) * 10
        return Color.rgb(gray, gray, gray)
    }

    private fun termuxAnsiCubeValue(value: Int): Int {
        return if (value <= 0) 0 else 55 + value * 40
    }

    private class TermuxAnsiStyle {
        var foreground: Int? = null
        var background: Int? = null
        var bold: Boolean = false
        var reverse: Boolean = false

        fun reset() {
            foreground = null
            background = null
            bold = false
            reverse = false
        }
    }

    private class TermuxWorkspaceFrame(
        val bridge: String?,
        val version: String?,
        val window: String?,
        val windows: String?,
        val frame: String?,
        val socketProtocol: String?,
        val socketName: String?,
        val socketToken: String?,
        val tcpHost: String?,
        val tcpPort: Int?,
        val cols: Int?,
        val rows: Int?,
        val cursorX: Int?,
        val cursorY: Int?
    )

    private class TermuxWorkspaceGeometry(
        val cols: Int,
        val rows: Int
    )

    private enum class TermuxWorkspaceModifier {
        CTRL,
        ALT,
        SHIFT
    }

    private class TermuxWorkspaceKeySpec(
        val label: String,
        val keyName: String?,
        val modifier: TermuxWorkspaceModifier?,
        val togglesMode: Boolean,
        val refreshes: Boolean
    )

    private fun pruneBundledLuaSamples() {
        if (bundledLuaSamplesPruned || mContext == null) return
        bundledLuaSamplesPruned = true
        val ids = ArrayList<String?>(LuaWidgetManager.bundledSampleIds())
        for (id in LuaWidgetManager.listIds()) {
            if (LuaWidgetManager.isBundledSample(id) && !ids.contains(id)) {
                ids.add(id)
            }
        }
        for (id in ids) {
            LuaWidgetManager.delete(id)
            ModuleManager.removeScriptModule(mContext, id)
            luaWidgetEngines.remove(id)
        }
    }

    private fun rebuildModuleDock() {
        pruneBundledLuaSamples()
        val showDock = getBoolean(Behavior.show_module_dock)
        if (moduleDockScroll != null) {
            moduleDockScroll!!.setVisibility(if (showDock) View.VISIBLE else View.GONE)
        }
        if (moduleDock == null) return

        moduleDock!!.removeAllViews()
        moduleDockButtons.clear()
        styledModuleDockSelection = null
        if (!showDock) return

        for (module in ModuleManager.getDock(mContext)) {
            addModuleDockButton(module)
        }
        addModuleDockButton("close")
        restyleAllModuleDockButtons()
    }

    private fun setupModuleSuggestionsStrip() {
        moduleSuggestionsScroll = mRootView?.findViewById(R.id.module_suggestions_container)
        moduleSuggestionsGroup = mRootView?.findViewById(R.id.module_suggestions_group)
        if (moduleSuggestionsScroll == null || moduleSuggestionsGroup == null) {
            return
        }
        val stripBackground = ColorUtils.blendARGB(terminalWindowBackground(), Color.BLACK, 0.12f)
        moduleSuggestionsScroll!!.setFocusable(false)
        Companion.applyBgRect(
            mContext!!,
            moduleSuggestionsScroll!!,
            String.format("#%08X", stripBackground),
            margins[SUGGESTIONS_MARGINS_INDEX]!!,
            genericBorderCornerRadius,
            useDashed,
            ColorUtils.setAlphaComponent(terminalBorderColor(), 175),
            true
        )
        hideModuleSuggestionsStrip()
    }

    private fun refreshStockSuggestions() {
        if (suggestionsManager != null && mTerminalAdapter != null) {
            suggestionsManager!!.requestSuggestion(mTerminalAdapter!!.input)
        }
    }

    private fun refreshModuleSuggestionsStrip() {
        val scroll = moduleSuggestionsScroll
        val group = moduleSuggestionsGroup
        if (scroll == null || group == null) {
            return
        }
        val context = mContext ?: return
        group.removeAllViews()
        if (!XMLPrefsManager.getBoolean(Suggestions.show_suggestions) || TextUtils.isEmpty(activeModule)) {
            hideModuleSuggestionsStrip()
            return
        }
        val suggestions = ModuleManager.getActiveSuggestions(mContext).filterNotNull()
            .filter { suggestion ->
                ModuleManager.ModuleSuggestion.MODE_COMMAND == suggestion.mode
                        && !TextUtils.isEmpty(suggestion.label)
                        && !TextUtils.isEmpty(suggestion.action)
            }
        if (suggestions.isEmpty()) {
            hideModuleSuggestionsStrip()
            return
        }
        val suggestionSpaces = XMLPrefsManager.getListOfIntValues(
            XMLPrefsManager.get(Suggestions.suggestions_spaces),
            4,
            0
        )
        val horizontalPadding = suggestionSpaces[2]
        val verticalPadding = suggestionSpaces[3]
        val textSize = XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat()
        for (suggestion in suggestions) {
            val chip = TextView(context)
            chip.text = suggestion.label
            chip.gravity = Gravity.CENTER
            chip.setLines(1)
            chip.maxLines = 1
            chip.ellipsize = TextUtils.TruncateAt.END
            chip.minWidth = Tuils.dpToPx(context, 56)
            chip.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding
            )
            chip.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
            chip.setTextColor(notificationWidgetTextColor())
            chip.textSize = textSize
            chip.setBackground(
                TerminalBorderRuntime.tabDrawable(context, terminalHeaderTabBackground())
            )
            chip.setOnClickListener(View.OnClickListener { v: View? ->
                mTerminalAdapter?.executeQuietly(suggestion.action, suggestion.action)
                refreshSuggestionsForActiveModule()
            })
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(
                suggestionSpaces[0],
                suggestionSpaces[1],
                suggestionSpaces[0],
                suggestionSpaces[1]
            )
            params.gravity = Gravity.CENTER_VERTICAL
            group.addView(chip, params)
        }
        scroll.visibility = View.VISIBLE
        scroll.post(Runnable { scroll.fullScroll(View.FOCUS_LEFT) })
    }

    private fun hideModuleSuggestionsStrip() {
        moduleSuggestionsGroup?.removeAllViews()
        moduleSuggestionsScroll?.visibility = View.GONE
    }

    private fun addModuleDockButton(module: String?) {
        val button = ModuleDockButtonFactory.create(
            mContext,
            module,
            onClick = { clickedModule ->
                if ("close" == clickedModule) closeHomeModule()
                else showHomeModule(clickedModule)
            },
            onTouchDown = {
                val scrollX = preservedModuleDockScrollX()
                pendingModuleDockScrollX = if (scrollX > 0) scrollX else lastModuleDockScrollX
            }
        )

        moduleDock!!.addView(button)
        moduleDockButtons.put(module, button)
    }

    private fun styleModuleDockButton(button: TextView, selected: Boolean) {
        ModuleDockButtonFactory.style(
            mContext,
            button,
            selected,
            moduleButtonBackgroundColor(),
            moduleButtonBorderColor(),
            moduleNameTextColor(),
            moduleCornerRadius(),
            dashedBorders()
        )
    }

    private fun updateModuleDockSelection() {
        if (styledModuleDockSelection == null) {
            for (entry in moduleDockButtons.entries) {
                styleModuleDockButton(entry.value!!, entry.key == activeModule)
            }
            styledModuleDockSelection = activeModule
            return
        }
        if (TextUtils.equals(styledModuleDockSelection, activeModule)) {
            return
        }
        val previous = moduleDockButtons.get(styledModuleDockSelection)
        if (previous != null) {
            styleModuleDockButton(previous, false)
        }
        val current = moduleDockButtons.get(activeModule)
        if (current != null) {
            styleModuleDockButton(current, true)
        }
        styledModuleDockSelection = activeModule
    }

    private fun restyleAllModuleDockButtons() {
        styledModuleDockSelection = null
        for (entry in moduleDockButtons.entries) {
            styleModuleDockButton(entry.value!!, entry.key == activeModule)
        }
        styledModuleDockSelection = activeModule
    }

    private fun currentModuleDockScrollX(): Int {
        if (moduleDockScroll is HorizontalScrollView) {
            return (moduleDockScroll as HorizontalScrollView).getScrollX()
        }
        return 0
    }

    private fun preservedModuleDockScrollX(): Int {
        if (moduleDockScroll is StableHorizontalScrollView) {
            return (moduleDockScroll as StableHorizontalScrollView).getPreservedScrollX()
        }
        return currentModuleDockScrollX()
    }

    private fun consumeModuleDockScrollX(): Int {
        val scrollX =
            if (pendingModuleDockScrollX >= 0) pendingModuleDockScrollX else preservedModuleDockScrollX()
        pendingModuleDockScrollX = -1
        return scrollX
    }

    private fun preserveModuleDockScrollX(scrollX: Int) {
        if (scrollX > 0) {
            lastModuleDockScrollX = scrollX
        }
        if (moduleDockScroll is StableHorizontalScrollView) {
            (moduleDockScroll as StableHorizontalScrollView).preserveScrollX(scrollX)
        } else if (moduleDockScroll is HorizontalScrollView) {
            val scroll = moduleDockScroll as HorizontalScrollView
            val target = max(0, scrollX)
            scroll.scrollTo(target, 0)
            scroll.post(Runnable { scroll.scrollTo(target, 0) })
        }
    }

    private fun chooseDefaultModule(): String? {
        val dock = ModuleManager.getDock(mContext)
        if (dock.contains(ModuleManager.NOTIFICATIONS) && showTerminal()) {
            return ModuleManager.NOTIFICATIONS
        }
        if (dock.contains(ModuleManager.MUSIC) && showWidget()) {
            return ModuleManager.MUSIC
        }
        return if (dock.isEmpty()) ModuleManager.TIMER else dock.get(0)
    }

    private fun showHomeModule(module: String?) {
        if (homeWidgetsContainer == null) return

        val id = ModuleManager.normalize(module)
        if (!ModuleManager.isKnown(mContext, id)) {
            return
        }

        val dockScrollX = consumeModuleDockScrollX()
        if (handler != null) {
            handler!!.removeCallbacks(eventsRefreshRunnable)
            handler!!.removeCallbacks(luaWidgetTickRunnable)
        }
        activeModule = id
        ModuleManager.setActiveModule(mContext, id)
        updateModuleDockSelection()
        applyTerminalTrayState(false)
        homeWidgetsContainer!!.removeAllViews()

        if (showLuaModuleIfSource(id)) {
            // Rendered above.
        } else if (ModuleManager.MUSIC == id) {
            showMusicModule()
        } else if (ModuleManager.NOTIFICATIONS == id) {
            showNotificationsModule()
        } else if (ModuleManager.TIMER == id) {
            showTextModule(ModuleManager.TIMER, buildTimerModuleText())
        } else if (ModuleManager.CALENDAR == id) {
            showTextModule(ModuleManager.CALENDAR, buildCalendarModuleText())
        } else if (ModuleManager.REMINDER == id) {
            showTextModule(ModuleManager.REMINDER, buildReminderModuleText())
        } else if (ModuleManager.NOTES == id) {
            showTextModule(ModuleManager.NOTES, buildNotesModuleText())
        } else if (ModuleManager.RSS == id) {
            showTextModule(ModuleManager.RSS, buildRssModuleText())
        } else if (ModuleManager.WEATHER == id) {
            showTextModule(ModuleManager.WEATHER, buildWeatherModuleText())
        } else {
            val source = ModuleManager.getModuleSource(mContext, id)
            if (ModuleManager.isLuaSource(source)) {
                renderLuaWidgetModule(id, false, false)
            }
            refreshLauncherModuleTextIfNeeded(id)
            val text = ModuleManager.getScriptText(mContext, id)
            showTextModule(id, if (TextUtils.isEmpty(text)) "No module output yet." else text)
        }
        refreshSuggestionsForActiveModule()
        scheduleEventsRefreshIfNeeded()
        preserveModuleDockScrollX(dockScrollX)
    }

    private fun ensureSystemLuaModules() {
        if (mContext == null) {
            return
        }
        try {
            LuaWidgetManager.ensureSystemTimerWidget()
            val timerSource =
                LuaWidgetManager.SOURCE_PREFIX + LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID
            val currentSource = ModuleManager.getModuleSource(mContext, ModuleManager.TIMER)
            if (TextUtils.isEmpty(currentSource) || TextUtils.equals(currentSource, timerSource)) {
                ModuleManager.setScriptModule(mContext, ModuleManager.TIMER, timerSource)
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun showLuaModuleIfSource(id: String?): Boolean {
        val source = ModuleManager.getModuleSource(mContext, id)
        if (!ModuleManager.isLuaSource(source)) {
            return false
        }
        val result = renderLuaWidgetModule(id, false, false)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (result != null) {
            showLuaWidgetModule(id, widgetId, result)
        } else {
            val text = ModuleManager.getScriptText(mContext, id)
            showTextModule(id, if (TextUtils.isEmpty(text)) "No module output yet." else text)
        }
        return true
    }

    private fun refreshLauncherModuleTextIfNeeded(module: String?) {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(mContext, id)
        if (!ModuleManager.isLauncherSource(source)) {
            return
        }
        val provider = ModuleManager.launcherProvider(source)
        if (ModuleManager.EVENTS == provider) {
            ModuleManager.setScriptText(
                mContext,
                id,
                UpcomingEventsManager.formatModulePayload(mContext)
            )
        }
    }

    private fun showMusicModule() {
        val musicWidget = LayoutInflater.from(mContext)
            .inflate(R.layout.music_widget, homeWidgetsContainer, false)
        homeWidgetsContainer!!.addView(musicWidget)
        musicWidget.setVisibility(View.VISIBLE)
        val close = musicWidget.findViewById<TextView?>(R.id.music_widget_close)
        if (close != null) {
            close.setOnClickListener(View.OnClickListener { v: View? -> closeHomeModule() })
            close.setTextColor(moduleNameTextColor())
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            close.setTextSize(moduleHeaderTextSize().toFloat())
        }
        styleMusicWidget(musicWidget)
        updateMusicModuleText(musicWidget)
        scheduleInternalMusicTickerIfNeeded()
    }

    private fun showNotificationsModule() {
        ensureNotificationServiceForModule()
        val notificationWidget = LayoutInflater.from(mContext)
            .inflate(R.layout.notification_widget, homeWidgetsContainer, false)
        homeWidgetsContainer!!.addView(notificationWidget)
        notificationWidget.setVisibility(View.VISIBLE)
        notificationWidget.setClickable(true)
        notificationWidget.setFocusable(true)
        val notificationBorder =
            notificationWidget.findViewById<View?>(R.id.notification_widget_border)
        val notificationLabel =
            notificationWidget.findViewById<View?>(R.id.notification_widget_label)
        if (notificationLabel != null) {
            notificationLabel.setOnClickListener(View.OnClickListener { v: View? -> openNotificationShade() })
        }
        val prev = notificationWidget.findViewById<TextView?>(R.id.notification_widget_prev)
        if (prev != null) {
            prev.setOnClickListener(View.OnClickListener { v: View? -> previousNotificationPage() })
            prev.setTextColor(moduleNameTextColor())
            prev.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        }
        val next = notificationWidget.findViewById<TextView?>(R.id.notification_widget_next)
        if (next != null) {
            next.setOnClickListener(View.OnClickListener { v: View? -> nextNotificationPage() })
            next.setTextColor(moduleNameTextColor())
            next.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        }
        val close = notificationWidget.findViewById<TextView?>(R.id.notification_widget_close)
        if (close != null) {
            close.setOnClickListener(View.OnClickListener { v: View? -> closeHomeModule() })
            close.setTextColor(moduleNameTextColor())
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            close.setTextSize(moduleHeaderTextSize().toFloat())
        }
        styleNotificationWidget(notificationWidget)
        LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
            Intent(
                ACTION_REQUEST_NOTIFICATION_FEED
            )
        )
    }

    private fun ensureNotificationServiceForModule() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 || !Tuils.hasNotificationAccess(
                mContext
            )
        ) {
            return
        }
        try {
            NotificationService.requestListenerRebind(mContext)
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    private fun repaintActiveLuaWidgetModule(
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        if (homeWidgetsContainer == null) {
            return
        }
        homeWidgetsContainer!!.removeAllViews()
        showLuaWidgetModule(module, widgetId, result)
        refreshSuggestionsForActiveModule()
    }

    private fun showLuaWidgetModule(
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        val moduleView = LayoutInflater.from(mContext)
            .inflate(R.layout.module_text_widget, homeWidgetsContainer, false)
        homeWidgetsContainer!!.addView(moduleView)

        val label = moduleView.findViewById<TextView?>(R.id.module_text_label)
        val close = moduleView.findViewById<TextView?>(R.id.module_text_close)
        val scroll = moduleView.findViewById<ScrollView?>(R.id.module_text_scroll)
        val body = moduleView.findViewById<TextView?>(R.id.module_text_body)

        if (label != null) {
            val title = if (TextUtils.isEmpty(result.title)) ModuleManager.displayTitle(
                mContext,
                module
            ) else result.title
            label.setText(title)
            label.setOnClickListener(View.OnClickListener {
                renderLuaWidgetModule(module, true, true)
            })
        }
        if (close != null) {
            close.setOnClickListener(View.OnClickListener { v: View? -> closeHomeModule() })
            close.setTextColor(moduleNameTextColor())
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            close.setTextSize(moduleHeaderTextSize().toFloat())
        }

        if (scroll != null) {
            scroll.removeAllViews()
            val content = LinearLayout(mContext)
            content.setOrientation(LinearLayout.VERTICAL)
            content.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            scroll.addView(content)

            if (!TextUtils.isEmpty(result.error)) {
                addLuaText(content, "Lua error: " + result.error, module)
                if (!TextUtils.isEmpty(result.errorStage)) {
                    addLuaText(content, "Stage: " + result.errorStage, module)
                }
            } else if (!TextUtils.isEmpty(result.layoutJson)
                && renderLuaLayout(content, module, result.layoutJson)
            ) {
                // The declarative layout rendered itself.
            } else {
                addLuaBodyText(
                    content,
                    if (TextUtils.isEmpty(result.body)) "No widget output yet." else result.body,
                    module
                )
            }
            addLuaResultActions(content, module, widgetId, result)
        } else if (body != null) {
            body.setText(if (TextUtils.isEmpty(result.body)) "No widget output yet." else result.body)
            body.setTextColor(notificationWidgetTextColor())
            body.setTextSize(moduleBodyTextSize().toFloat())
            applyModuleBodyTypeface(body, module)
        }

        decorateWidget(
            moduleView,
            R.id.module_text_border,
            R.id.module_text_label,
            R.id.module_text_close,
            notificationWidgetBorderColor(),
            moduleNameTextColor()
        )
        styleModuleClose(close)
    }

    private fun addLuaBodyText(parent: LinearLayout, text: String?, module: String? = null) {
        if (TextUtils.isEmpty(text)) {
            addLuaText(parent, "", module)
            return
        }
        addLuaText(parent, text, module)
    }

    private fun addLuaText(
        parent: LinearLayout,
        text: String?,
        module: String? = null,
        fontMode: String? = null
    ) {
        val view = TextView(mContext)
        view.setText(text)
        view.setTextColor(notificationWidgetTextColor())
        view.setTextSize(moduleBodyTextSize().toFloat())
        view.setIncludeFontPadding(true)
        view.setLineSpacing(Tuils.dpToPx(mContext, 2).toFloat(), 1f)
        applyModuleBodyTypeface(view, module, fontMode)
        view.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(view)
    }

    private fun addLuaResultActions(
        parent: LinearLayout,
        module: String?,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult
    ) {
        val actions = ArrayList<LuaSurfaceAction>()
        var index = 1
        for (button in result.buttons) {
            val actionIndex = index
            if (!TextUtils.isEmpty(button)) {
                actions.add(
                    LuaSurfaceAction(button!!) {
                        clickLuaWidget(module, actionIndex)
                    }
                )
            }
            index += 1
        }
        for (action in result.valueActions) {
            if (action == null || TextUtils.isEmpty(action.label)) continue
            actions.add(
                LuaSurfaceAction(action.label!!) {
                    actionLuaWidget(module, action.value)
                }
            )
        }
        if (result.dialogOpen) {
            var dialogIndex = 1
            for (item in result.dialogItems) {
                val choiceIndex = dialogIndex
                if (!TextUtils.isEmpty(item)) {
                    val label = if (choiceIndex == result.dialogSelected) "* " + item else item
                    actions.add(
                        LuaSurfaceAction(label!!) {
                            dialogLuaWidget(module, choiceIndex)
                        }
                    )
                }
                dialogIndex += 1
            }
            actions.add(LuaSurfaceAction("cancel") { dialogLuaWidget(module, -1) })
        }
        for (action in result.commands) {
            if (action == null || TextUtils.isEmpty(action.label) || TextUtils.isEmpty(action.command)) {
                continue
            }
            actions.add(
                LuaSurfaceAction(action.label!!) {
                    executeLuaWidgetCommand(action.command)
                }
            )
        }
        if (result.expandable) {
            val expanded = result.expanded
            actions.add(
                LuaSurfaceAction(if (expanded) "collapse" else "expand") {
                    if (expanded) setLuaWidgetExpanded(module, false)
                    else setLuaWidgetExpanded(module, true)
                }
            )
        }
        addLuaButtonGrid(parent, actions)
    }

    private fun addLuaButtonGrid(parent: LinearLayout, actions: MutableList<LuaSurfaceAction>) {
        if (actions.isEmpty()) {
            return
        }
        val topSpacer = View(mContext)
        topSpacer.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Tuils.dpToPx(mContext, 8)
            )
        )
        parent.addView(topSpacer)

        var row: LinearLayout? = null
        for (i in actions.indices) {
            if (i % 2 == 0) {
                row = LinearLayout(mContext)
                row.setOrientation(LinearLayout.HORIZONTAL)
                row.setLayoutParams(
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                parent.addView(row)
            }
            val button = luaSurfaceButton(actions[i])
            row!!.addView(button)
        }
    }

    private fun luaSurfaceButton(action: LuaSurfaceAction): TextView {
        val button = TextView(mContext)
        button.setText(action.label)
        button.setSingleLine(false)
        button.setGravity(Gravity.CENTER)
        button.setMinHeight(Tuils.dpToPx(mContext, 36))
        button.setPadding(
            Tuils.dpToPx(mContext, 10),
            Tuils.dpToPx(mContext, 7),
            Tuils.dpToPx(mContext, 10),
            Tuils.dpToPx(mContext, 7)
        )
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.setTextColor(moduleNameTextColor())
        button.setBackground(luaSurfaceButtonBackground())
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val margin = Tuils.dpToPx(mContext, 4)
        lp.setMargins(margin, margin, margin, margin)
        button.setLayoutParams(lp)
        button.setOnClickListener(View.OnClickListener { action.run.run() })
        return button
    }

    private fun luaSurfaceButtonBackground(): Drawable {
        return TerminalBorderRuntime.panelDrawable(
            mContext!!,
            moduleButtonBackgroundColor(),
            moduleButtonBorderColor(),
            1.2f,
            moduleCornerRadius(),
            dashedBorders()
        )
    }

    private fun renderLuaLayout(
        parent: LinearLayout,
        module: String?,
        rawJson: String?,
        appSurface: Boolean = false
    ): Boolean {
        if (TextUtils.isEmpty(rawJson)) {
            return false
        }
        try {
            val trimmed = rawJson!!.trim { it <= ' ' }
            if (trimmed.startsWith("[")) {
                renderLuaLayoutArray(parent, module, JSONArray(trimmed), appSurface)
            } else {
                renderLuaLayoutObject(parent, module, JSONObject(trimmed), appSurface)
            }
            return true
        } catch (e: Exception) {
            addLuaText(parent, "Layout error: " + e.message, module)
            return true
        }
    }

    private fun renderLuaLayoutArray(
        parent: LinearLayout,
        module: String?,
        array: JSONArray,
        appSurface: Boolean
    ) {
        for (i in 0..<array.length()) {
            val item = array.opt(i)
            if (item is JSONObject) {
                renderLuaLayoutObject(parent, module, item, appSurface)
            } else if (item is JSONArray) {
                renderLuaLayoutCompact(parent, module, item, appSurface)
            } else if (item != null) {
                addLuaText(parent, item.toString(), module)
            }
        }
    }

    private fun renderLuaLayoutCompact(
        parent: LinearLayout,
        module: String?,
        array: JSONArray,
        appSurface: Boolean
    ) {
        if (array.length() == 0) {
            return
        }
        val type = array.optString(0, "text")
        val obj = JSONObject()
        obj.put("type", type)
        if ("button" == type) {
            obj.put("label", array.optString(1, ""))
            if (array.length() > 2) obj.put("command", array.optString(2, ""))
        } else {
            obj.put("text", array.optString(1, ""))
        }
        renderLuaLayoutObject(parent, module, obj, appSurface)
    }

    private fun renderLuaLayoutObject(
        parent: LinearLayout,
        module: String?,
        obj: JSONObject,
        appSurface: Boolean
    ) {
        val type = obj.optString("type", obj.optString("kind", "text")).lowercase(Locale.US)
        if ("column" == type || "container" == type) {
            val column = LinearLayout(mContext)
            column.setOrientation(LinearLayout.VERTICAL)
            column.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            parent.addView(column)
            renderLuaLayoutChildren(column, module, obj.optJSONArray("children"), appSurface)
            return
        }
        if ("row" == type) {
            val row = LinearLayout(mContext)
            row.setOrientation(LinearLayout.HORIZONTAL)
            row.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            parent.addView(row)
            val children = obj.optJSONArray("children")
            if (children != null) {
                for (i in 0..<children.length()) {
                    val child = children.optJSONObject(i) ?: continue
                    val childBox = LinearLayout(mContext)
                    childBox.setOrientation(LinearLayout.VERTICAL)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    val margin = Tuils.dpToPx(mContext, 3)
                    lp.setMargins(margin, margin, margin, margin)
                    childBox.setLayoutParams(lp)
                    row.addView(childBox)
                    renderLuaLayoutObject(childBox, module, child, appSurface)
                }
            }
            return
        }
        if ("button" == type || "action" == type || "command" == type || "module" == type) {
            addLuaButtonGrid(
                parent,
                mutableListOf(luaLayoutSurfaceAction(module, obj, appSurface))
            )
            return
        }
        if ("progress" == type) {
            addLuaText(parent, formatLuaLayoutProgress(obj), module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("divider" == type) {
            addLuaText(parent, "----------------", module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("pre" == type || "ascii" == type || "code" == type) {
            addLuaText(parent, obj.optString("text", obj.optString("label", "")), module, MODULE_TEXT_FONT_MONO)
            return
        }
        if ("spacer" == type) {
            val spacer = View(mContext)
            spacer.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Tuils.dpToPx(mContext, max(1, obj.optInt("size", 8)))
                )
            )
            parent.addView(spacer)
            return
        }
        val text = obj.optString("text", obj.optString("label", ""))
        addLuaText(parent, text, module)
    }

    private fun renderLuaLayoutChildren(
        parent: LinearLayout,
        module: String?,
        children: JSONArray?,
        appSurface: Boolean
    ) {
        if (children == null) {
            return
        }
        for (i in 0..<children.length()) {
            val child = children.opt(i)
            if (child is JSONObject) {
                renderLuaLayoutObject(parent, module, child, appSurface)
            } else if (child is JSONArray) {
                renderLuaLayoutCompact(parent, module, child, appSurface)
            }
        }
    }

    private fun luaLayoutSurfaceAction(
        module: String?,
        obj: JSONObject,
        appSurface: Boolean
    ): LuaSurfaceAction {
        val label = obj.optString("label", obj.optString("text", "button"))
        val localValue = obj.optString("action", obj.optString("value", label))
        val hasLocalAction = obj.has("action") || obj.has("value") || "action" == obj.optString(
            "type",
            obj.optString("kind", "")
        ).lowercase(Locale.US)
        val command = commandFromLuaLayoutObject(obj)
        return LuaSurfaceAction(label) {
            if (!TextUtils.isEmpty(command)) {
                executeLuaWidgetCommand(command)
            } else if (appSurface) {
                actionLuaApp(localValue)
            } else if (hasLocalAction) {
                actionLuaWidget(module, localValue)
            }
        }
    }

    private fun commandFromLuaLayoutObject(obj: JSONObject): String {
        if (!TextUtils.isEmpty(obj.optString("command", ""))) {
            return obj.optString("command")
        }
        if (!TextUtils.isEmpty(obj.optString("module", ""))) {
            return "module -show " + obj.optString("module")
        }
        return ""
    }

    private fun formatLuaLayoutProgress(obj: JSONObject): String {
        val label = obj.optString("label", obj.optString("text", "Progress"))
        val value = obj.optDouble("value", obj.optDouble("progress", 0.0))
        val maxValue = obj.optDouble("max", 1.0)
        val pct = if (maxValue <= 0.0) 0.0 else min(1.0, max(0.0, value / maxValue))
        val width = max(4, min(32, obj.optInt("width", 12)))
        val filled = Math.round(pct * width).toInt()
        val out = StringBuilder(label).append(" [")
        for (i in 0..<width) {
            out.append(if (i < filled) '█' else '░')
        }
        out.append("] ").append(Math.round(pct * 100.0)).append('%')
        return out.toString()
    }

    private fun executeLuaWidgetCommand(command: String?) {
        if (TextUtils.isEmpty(command) || mTerminalAdapter == null) {
            return
        }
        mTerminalAdapter!!.executeInput(command)
    }

    private fun showTextModule(module: String?, text: String?) {
        val moduleView = LayoutInflater.from(mContext)
            .inflate(R.layout.module_text_widget, homeWidgetsContainer, false)
        homeWidgetsContainer!!.addView(moduleView)

        val label = moduleView.findViewById<TextView?>(R.id.module_text_label)
        val body = moduleView.findViewById<TextView?>(R.id.module_text_body)
        val close = moduleView.findViewById<TextView?>(R.id.module_text_close)
        val scroll = moduleView.findViewById<ScrollView?>(R.id.module_text_scroll)
        if (label != null) {
            label.setText(ModuleManager.displayTitle(mContext, module))
        }
        if (ModuleManager.CALENDAR == ModuleManager.normalize(module) && scroll != null) {
            scroll.removeAllViews()
            body?.setVisibility(View.GONE)
            scroll.addView(buildCalendarModuleView())
        } else if (shouldRenderScriptSegments(module) && body != null && scroll != null) {
            renderScriptModuleSegments(module, text, body, scroll)
        } else if (body != null) {
            body.setText(text)
            body.setTextColor(notificationWidgetTextColor())
            body.setTextSize(moduleBodyTextSize().toFloat())
            applyModuleBodyTypeface(body, module)
            constrainEventModuleScroll(module, scroll, body)
        }
        if (close != null) {
            close.setOnClickListener(View.OnClickListener { v: View? -> closeHomeModule() })
            close.setTextColor(moduleNameTextColor())
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            close.setTextSize(moduleHeaderTextSize().toFloat())
        }

        decorateWidget(
            moduleView,
            R.id.module_text_border,
            R.id.module_text_label,
            R.id.module_text_close,
            notificationWidgetBorderColor(),
            moduleNameTextColor()
        )
        styleModuleClose(close)
    }

    private fun shouldRenderScriptSegments(module: String?): Boolean {
        return ModuleManager.isTermuxSource(ModuleManager.getModuleSource(mContext, module))
    }

    private fun renderScriptModuleSegments(
        module: String?,
        fallbackText: String?,
        body: TextView,
        scroll: ScrollView
    ) {
        val segments = ModuleManager.getScriptSegments(mContext, module)
        if (segments.isEmpty() && !TextUtils.isEmpty(fallbackText)) {
            segments.add(ModuleManager.ModuleTextSegment(fallbackText!!, false))
        }

        scroll.removeAllViews()
        body.setVisibility(View.GONE)

        val content = LinearLayout(mContext)
        content.setOrientation(LinearLayout.VERTICAL)
        content.setLayoutParams(
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        scroll.addView(content)

        for (segment in segments) {
            addScriptModuleSegmentText(
                content,
                segment.text,
                module,
                if (segment.mono) MODULE_TEXT_FONT_MONO else MODULE_TEXT_FONT_THEME
            )
        }
    }

    private fun addScriptModuleSegmentText(
        parent: LinearLayout,
        text: String?,
        module: String?,
        fontMode: String
    ) {
        val view = TextView(mContext)
        view.setText(text)
        view.setTextColor(notificationWidgetTextColor())
        view.setTextSize(moduleBodyTextSize().toFloat())
        view.setIncludeFontPadding(true)
        view.setLineSpacing(Tuils.dpToPx(mContext, 2).toFloat(), 1f)
        applyModuleBodyTypeface(view, module, fontMode)
        view.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(view)
    }

    private fun constrainEventModuleScroll(module: String?, scroll: ScrollView?, body: TextView?) {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(mContext, id)
        if ((ModuleManager.EVENTS != id) || !ModuleManager.isLauncherSource(source) || scroll == null || body == null) {
            return
        }

        scroll.setFillViewport(false)
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)
        scroll.getViewTreeObserver()
            .addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (scroll.getViewTreeObserver().isAlive()) {
                        scroll.getViewTreeObserver().removeOnPreDrawListener(this)
                    }

                    val maxHeight = calculateCalendarTextHeight(body)
                    val contentHeight = body.getHeight()
                    if (maxHeight <= 0 || contentHeight <= 0) {
                        return true
                    }

                    val targetHeight = min(contentHeight, maxHeight)
                    val params = scroll.getLayoutParams()
                    if (params != null && params.height != targetHeight) {
                        params.height = targetHeight
                        scroll.setLayoutParams(params)
                    }
                    scroll.setVerticalScrollBarEnabled(contentHeight > targetHeight)
                    return true
                }
            })
    }

    private fun applyModuleBodyTypeface(
        body: TextView?,
        module: String? = null,
        fontMode: String? = null
    ) {
        if (body == null) {
            return
        }
        val useThemeTypeface = if (MODULE_TEXT_FONT_MONO == fontMode) {
            false
        } else if (MODULE_TEXT_FONT_THEME == fontMode) {
            true
        } else {
            moduleBodyUsesThemeTypeface(module)
        }
        body.setTag(
            R.id.module_text_font_mode,
            if (useThemeTypeface) MODULE_TEXT_FONT_THEME else MODULE_TEXT_FONT_MONO
        )
        val style = if (body.getTypeface() != null) body.getTypeface().getStyle() else Typeface.NORMAL
        if (useThemeTypeface) {
            body.setTypeface(Tuils.getTypeface(mContext), style)
        } else {
            body.setTypeface(Typeface.MONOSPACE, style)
        }
    }

    private fun buildCalendarModuleView(): View {
        val calendar = Calendar.getInstance()
        val today = Calendar.getInstance()
        val months = arrayOf(
            "JAN",
            "FEB",
            "MAR",
            "APR",
            "MAY",
            "JUN",
            "JUL",
            "AUG",
            "SEP",
            "OCT",
            "NOV",
            "DEC"
        )
        val weekdays = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDay = calendar.get(Calendar.DAY_OF_WEEK)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay =
            if (today.get(Calendar.MONTH) == month && today.get(Calendar.YEAR) == year) today.get(Calendar.DAY_OF_MONTH) else -1

        val content = LinearLayout(mContext)
        content.setOrientation(LinearLayout.VERTICAL)
        val calendarInset = Tuils.dpToPx(mContext, 2)
        content.setPadding(calendarInset, 0, calendarInset, 0)
        content.setLayoutParams(
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val title = calendarTextView(months[month] + " " + year, true, false)
        title.setGravity(Gravity.START or Gravity.CENTER_VERTICAL)
        title.setPadding(0, 0, 0, 0)
        content.addView(title)

        val header = calendarRow()
        for (weekday in weekdays) {
            header.addView(calendarCell(weekday, true, false))
        }
        content.addView(header)

        var day = 1
        for (week in 0 until 6) {
            val row = calendarRow()
            for (dow in Calendar.SUNDAY..Calendar.SATURDAY) {
                if ((week == 0 && dow < firstDay) || day > daysInMonth) {
                    row.addView(calendarCell("", false, false))
                } else {
                    row.addView(
                        calendarCell(
                            String.format(Locale.US, "%02d", day),
                            false,
                            day == currentDay
                        )
                    )
                    day += 1
                }
            }
            content.addView(row)
            if (day > daysInMonth) {
                break
            }
        }
        addCalendarModuleButtons(content)
        return content
    }

    private fun addCalendarModuleButtons(parent: LinearLayout) {
        val actions = ArrayList<LuaSurfaceAction>()
        actions.add(
            LuaSurfaceAction("today") {
                executeLuaWidgetCommand("module -show calendar")
            }
        )
        actions.add(
            LuaSurfaceAction("timer") {
                executeLuaWidgetCommand("module -show timer")
            }
        )
        addLuaButtonGrid(parent, actions)
    }

    private fun calendarRow(): LinearLayout {
        val row = LinearLayout(mContext)
        row.setOrientation(LinearLayout.HORIZONTAL)
        row.setBaselineAligned(false)
        row.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return row
    }

    private fun calendarCell(text: String, bold: Boolean, today: Boolean): TextView {
        val cell = calendarTextView(if (today) "[$text]" else text, bold || today, today)
        cell.setGravity(Gravity.CENTER)
        cell.setMinHeight(0)
        cell.setPadding(0, 0, 0, 0)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        cell.setLayoutParams(lp)
        return cell
    }

    private fun calendarTextView(text: String, bold: Boolean, highlight: Boolean): TextView {
        val view = TextView(mContext)
        view.setText(text)
        view.setSingleLine(true)
        view.setIncludeFontPadding(false)
        view.setTextSize(moduleBodyTextSize().toFloat())
        view.setTextColor(if (highlight) moduleNameTextColor() else notificationWidgetTextColor())
        view.setTag(R.id.module_text_font_mode, MODULE_TEXT_FONT_THEME)
        view.setTypeface(
            Tuils.getTypeface(mContext),
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
        return view
    }

    private fun moduleBodyUsesThemeTypeface(module: String?): Boolean {
        val id = ModuleManager.normalize(module)
        if (ModuleManager.TIMER == id
            || ModuleManager.CALENDAR == id
            || ModuleManager.REMINDER == id
            || ModuleManager.EVENTS == id
            || ModuleManager.NOTES == id
            || ModuleManager.RSS == id
            || "focus_sprint" == id
        ) {
            return true
        }

        val source = ModuleManager.getModuleSource(mContext, id)
        if (ModuleManager.isLuaSource(source)) {
            val widgetId = ModuleManager.luaWidgetId(source)
            return LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID == widgetId || "focus_sprint" == widgetId
        }
        return false
    }

    private fun calculateCalendarTextHeight(body: TextView): Int {
        val lineHeight = body.getLineHeight()
        val lines = countVisibleLines(buildCalendarModuleText())
        val padding = body.getPaddingTop() + body.getPaddingBottom()
        return max(lineHeight, lineHeight * lines + padding)
    }

    private fun countVisibleLines(text: String?): Int {
        if (TextUtils.isEmpty(text)) {
            return 1
        }
        val lines: Array<String?> = text!!.split("\\r?\\n".toRegex()).toTypedArray()
        var count = lines.size
        while (count > 1 && TextUtils.isEmpty(lines[count - 1])) {
            count--
        }
        return count
    }

    private fun styleModuleClose(close: TextView?) {
        if (close == null) return
        val bgColor = terminalHeaderTabBackground()
        close.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, bgColor))
        close.setTextSize(moduleHeaderTextSize().toFloat())
    }

    private fun closeHomeModule() {
        val dockScrollX = consumeModuleDockScrollX()
        if (handler != null) {
            handler!!.removeCallbacks(eventsRefreshRunnable)
            handler!!.removeCallbacks(luaWidgetTickRunnable)
        }
        activeModule = ""
        ModuleManager.setActiveModule(mContext, "")
        if (homeWidgetsContainer != null) {
            homeWidgetsContainer!!.removeAllViews()
        }
        updateModuleDockSelection()
        applyTerminalTrayState(false)
        refreshSuggestionsForActiveModule()
        preserveModuleDockScrollX(dockScrollX)
    }

    private fun refreshSuggestionsForActiveModule() {
        refreshModuleSuggestionsStrip()
        if (suggestionsManager != null && mTerminalAdapter != null && TextUtils.isEmpty(
                mTerminalAdapter!!.input
            )
        ) {
            suggestionsManager!!.requestSuggestion(Tuils.EMPTYSTRING)
        }
    }

    private fun refreshActiveModuleIfNeeded() {
        if (ModuleManager.TIMER == activeModule) {
            showHomeModule(ModuleManager.TIMER)
        } else if (ModuleManager.REMINDER == activeModule) {
            showHomeModule(ModuleManager.REMINDER)
        }
    }

    private fun scheduleEventsRefreshIfNeeded() {
        if (handler == null) {
            return
        }
        handler!!.removeCallbacks(eventsRefreshRunnable)
        if (ModuleManager.EVENTS != activeModule) {
            return
        }
        val source = ModuleManager.getModuleSource(mContext, ModuleManager.EVENTS)
        if (!ModuleManager.isLauncherSource(source)) {
            return
        }

        val now = System.currentTimeMillis()
        val delay: Long =
            EVENTS_REFRESH_FALLBACK_MS - (now % EVENTS_REFRESH_FALLBACK_MS) + EVENTS_REFRESH_GRACE_MS
        handler!!.postDelayed(eventsRefreshRunnable, delay)
    }

    private fun scheduleLuaWidgetTickIfNeeded(
        module: String?,
        result: LuaWidgetEngine.RenderResult?
    ) {
        if (handler == null) {
            return
        }
        handler!!.removeCallbacks(luaWidgetTickRunnable)
        val id = ModuleManager.normalize(module)
        if ((id != activeModule) || result == null || result.tickIntervalMs <= 0L) {
            return
        }
        val source = ModuleManager.getModuleSource(mContext, id)
        if (!ModuleManager.isLuaSource(source)) {
            return
        }
        handler!!.postDelayed(luaWidgetTickRunnable, result.tickIntervalMs)
    }

    private fun tickActiveLuaWidget() {
        val id = ModuleManager.normalize(activeModule)
        if (TextUtils.isEmpty(id)) {
            return
        }
        val source = ModuleManager.getModuleSource(mContext, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            return
        }
        if (applyLuaWidgetUnavailablePayload(
                id,
                widgetId,
                true,
                id == activeModule,
                id == activeModule
            )
        ) {
            return
        }

        val result = getLuaWidgetEngine(widgetId).tick()
        val active = id == activeModule
        applyLuaWidgetResult(id, widgetId, result, active, active, false)
    }

    private fun updateMusicModuleText(musicWidget: View) {
        val title = musicWidget.findViewById<TextView?>(R.id.music_song_title)
        val singer = musicWidget.findViewById<TextView?>(R.id.music_singer)
        val visualizer = musicWidget.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
        val textColor = musicWidgetTextColor()
        if (title != null) {
            title.setText(
                if (!TextUtils.isEmpty(lastMusicSong)) "Title: " + lastMusicSong!!.uppercase(
                    Locale.getDefault()
                ) else "Title: -"
            )
            title.setTextColor(textColor)
        }
        if (singer != null) {
            singer.setText(
                if (!TextUtils.isEmpty(lastMusicSinger)) "Singer      : " + lastMusicSinger!!.uppercase(
                    Locale.getDefault()
                ) else "Singer      : -"
            )
            singer.setTextColor(textColor)
        }
        updateMusicAppRow(musicWidget, textColor)
        if (visualizer != null) {
            visualizer.setBarColor(textColor)
            visualizer.setPlaying(lastMusicPlaying)
        }
    }

    private fun buildTimerModuleText(): String {
        val out = StringBuilder()
        val clockManager = ClockManager.getInstance(mContext!!.getApplicationContext())
        out.append(clockManager.timerStatus).append('\n')
        out.append(clockManager.stopwatchStatus).append('\n')

        val pomodoro = PomodoroManager.getInstance(mContext!!.getApplicationContext())
        if (pomodoro.isRunning) {
            out.append("Pomodoro: ")
                .append(pomodoro.currentType.name.lowercase())
                .append(" ")
                .append(ClockManager.formatDuration(pomodoro.remainingMillis))
                .append('\n')
        } else {
            out.append("Pomodoro: idle\n")
        }
        out.append("Commands: timer, stopwatch, pomodoro")
        return out.toString()
    }

    private fun buildReminderModuleText(): String {
        return ReminderManager.formatList(mContext!!) + "\nCommands: -add, -edit, -rm"
    }

    private fun buildNotesModuleText(): String {
        val records = ohi.andre.consolelauncher.managers.NotesManager.loadRecords(mContext)
        if (records.size == 0) {
            return ("No notes."
                    + "\nAdd: notes -add TODO: follow up"
                    + "\nOpen editor: notes")
        }

        val out = StringBuilder()
        out.append(records.size).append(if (records.size == 1) " note" else " notes").append('\n')
        val limit = min(records.size, 6)
        for (count in 0..<limit) {
            val record = records.get(count)
            if (record == null || TextUtils.isEmpty(record.text)) continue
            out.append(count + 1).append(". ")
            if (record.lock) out.append("[locked] ")
            out.append(shortenModuleLine(record.text, 96)).append('\n')
        }
        val remaining = records.size - limit
        if (remaining > 0) {
            out.append("... ").append(remaining).append(" more\n")
        }
        out.append("Commands: notes, notes -add, notes -ls")
        return out.toString().trim { it <= ' ' }
    }

    private fun buildRssModuleText(): String? {
        val manager = if (mainPack != null) mainPack!!.rssManager else null
        if (manager == null) {
            return "RSS manager unavailable."
        }
        return manager.buildModuleText()
    }

    private fun buildWeatherModuleText(): String {
        if (!XMLPrefsManager.getBoolean(Ui.show_weather)) {
            return ("Weather is disabled."
                    + "\nEnable: tuiweather -enable"
                    + "\nSetup: tuiweather -tutorial")
        }

        var weather = lastWeatherText
        if (TextUtils.isEmpty(weather)) {
            weather = labelTexts[Label.weather.ordinal]
        }
        if (TextUtils.isEmpty(weather)) {
            return ("No weather yet."
                    + "\nUpdate: tuiweather -update"
                    + "\nSetup: tuiweather -tutorial")
        }

        val out = StringBuilder(weather.toString().trim { it <= ' ' })
        if (lastWeatherUpdateMillis > 0) {
            val calendar = Calendar.getInstance()
            calendar.setTimeInMillis(lastWeatherUpdateMillis)
            out.append("\nUpdated: ")
                .append(
                    String.format(
                        Locale.US, "%02d.%02d",
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE)
                    )
                )
        }
        out.append("\nCommands: tuiweather -update, tuiweather -tutorial")
        return out.toString()
    }

    private fun shortenModuleLine(value: String?, max: Int): String {
        if (value == null) return ""
        val cleaned = value.replace("\\s+".toRegex(), " ").trim { it <= ' ' }
        if (cleaned.length <= max) return cleaned
        return cleaned.substring(0, max(0, max - 3)).trim { it <= ' ' } + "..."
    }

    private fun buildCalendarModuleText(): String {
        val calendar = Calendar.getInstance()
        val months = arrayOf<String?>(
            "JAN",
            "FEB",
            "MAR",
            "APR",
            "MAY",
            "JUN",
            "JUL",
            "AUG",
            "SEP",
            "OCT",
            "NOV",
            "DEC"
        )
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val firstDay = calendar.get(Calendar.DAY_OF_WEEK)
        val max = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val out = StringBuilder()
        out.append(months[month]).append(' ').append(year).append('\n')
        out.append("SU MO TU WE TH FR SA\n")
        for (i in Calendar.SUNDAY..<firstDay) {
            out.append("   ")
        }
        for (day in 1..max) {
            if (day == today) {
                out.append('[').append(if (day < 10) "0" else "").append(day).append(']')
            } else {
                if (day < 10) out.append('0')
                out.append(day).append(' ')
            }
            val dow = calendar.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY) {
                out.append('\n')
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out.toString()
    }

    private fun handleModuleCommand(intent: Intent) {
        val command = intent.getStringExtra(EXTRA_MODULE_COMMAND)
        val module = intent.getStringExtra(EXTRA_MODULE_NAME)

        if ("rebuild" == command) {
            rebuildModuleDock()
            if (homeWidgetsContainer != null && !TextUtils.isEmpty(activeModule) && ModuleManager.getDock(
                    mContext
                ).contains(activeModule)
            ) {
                showHomeModule(activeModule)
            }
        } else if ("show" == command) {
            showHomeModule(module)
        } else if ("close" == command) {
            closeHomeModule()
        } else if ("update" == command) {
            if (!TextUtils.isEmpty(module) && module == activeModule) {
                showHomeModule(module)
            }
            rebuildModuleDock()
        } else if ("refresh" == command) {
            refreshScriptModule(module)
        } else if ("lua_click" == command) {
            val index = intent.getIntExtra(EXTRA_WIDGET_ACTION_INDEX, 0)
            clickLuaWidget(module, index)
        } else if ("lua_action" == command) {
            actionLuaWidget(module, intent.getStringExtra(EXTRA_WIDGET_ACTION_VALUE))
        } else if ("lua_dialog" == command) {
            val index = intent.getIntExtra(EXTRA_WIDGET_ACTION_INDEX, 0)
            dialogLuaWidget(module, index)
        } else if ("lua_expand" == command) {
            setLuaWidgetExpanded(module, true)
        } else if ("lua_collapse" == command) {
            setLuaWidgetExpanded(module, false)
        } else if ("lua_toggle" == command) {
            toggleLuaWidgetExpanded(module)
        } else if ("lua_reload_widget" == command) {
            reloadLuaWidget(module)
        }
    }

    private fun refreshScriptModule(module: String?) {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(mContext, id)
        if (TextUtils.isEmpty(source)) {
            Tuils.sendOutput(mContext, "Module has no source: " + id)
            return
        }
        if (ModuleManager.isLauncherSource(source)) {
            refreshLauncherModule(id, source)
            return
        }
        if (ModuleManager.isLuaSource(source)) {
            renderLuaWidgetModule(id, true, true)
            return
        }
        runTermuxScript(source, ArrayList<String?>(), id, false)
    }

    private fun refreshLauncherModule(module: String?, source: String?, announce: Boolean = true) {
        val provider = ModuleManager.launcherProvider(source)
        val payload: String?
        if (ModuleManager.EVENTS == provider) {
            payload = UpcomingEventsManager.formatModulePayload(mContext)
        } else {
            Tuils.sendOutput(mContext, "Unknown launcher module source: " + source)
            return
        }

        val id = ModuleManager.normalize(module)
        ModuleManager.setScriptText(mContext, id, payload)
        if (id == activeModule) {
            showHomeModule(id)
        }
        updateModuleDockSelection()
        if (announce) {
            Tuils.sendOutput(mContext, "Module refreshed: " + id)
        }
    }

    private fun renderLuaWidgetModule(
        module: String?,
        repaint: Boolean,
        announce: Boolean
    ): LuaWidgetEngine.RenderResult? {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(mContext, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            ModuleManager.setScriptText(
                mContext, id, ("::title " + ModuleManager.displayName(id)
                        + "\n::body Lua module source not found: " + widgetId)
            )
            return null
        } else if (!applyLuaWidgetUnavailablePayload(id, widgetId, true, false, false)) {
            val engine = getLuaWidgetEngine(widgetId)
            val result = engine.render(announce)
            applyLuaWidgetResult(id, widgetId, result, true, false, false)
            if (repaint && id == activeModule) {
                repaintActiveLuaWidgetModule(id, widgetId, result)
            }
            updateModuleDockSelection()
            if (announce) {
                Tuils.sendOutput(mContext, "Lua module refreshed: " + id)
            }
            return result
        }

        if (repaint && id == activeModule) {
            repaintActiveTextModule(id)
        }
        updateModuleDockSelection()
        if (announce) {
            Tuils.sendOutput(mContext, "Lua module refreshed: " + id)
        }
        return null
    }

    private fun clickLuaWidget(module: String?, index: Int) {
        if (index <= 0) {
            return
        }
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.click(index) })
    }

    private fun actionLuaWidget(module: String?, value: String?) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.action(value) })
    }

    private fun dialogLuaWidget(module: String?, index: Int) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.dialog(index) })
    }

    private fun setLuaWidgetExpanded(module: String?, expanded: Boolean) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { engine: LuaWidgetEngine? -> engine!!.setExpanded(expanded) })
    }

    private fun toggleLuaWidgetExpanded(module: String?) {
        runLuaWidgetOperation(
            module,
            LuaWidgetOperation { obj: LuaWidgetEngine? -> obj!!.toggleExpanded() })
    }

    private fun reloadLuaWidget(widgetId: String?) {
        val id = LuaWidgetManager.normalizeId(widgetId)
        if (TextUtils.isEmpty(id)) {
            return
        }
        luaWidgetEngines.remove(id)
        val modules = modulesForLuaWidget(id)
        if (modules.isEmpty()) {
            return
        }
        for (module in modules) {
            renderLuaWidgetModule(module, module == activeModule, false)
        }
        updateModuleDockSelection()
        refreshSuggestionsForActiveModule()
    }

    private fun runLuaWidgetOperation(module: String?, operation: LuaWidgetOperation) {
        val id = ModuleManager.normalize(module)
        if (TextUtils.isEmpty(id)) {
            return
        }
        val source = ModuleManager.getModuleSource(mContext, id)
        val widgetId = ModuleManager.luaWidgetId(source)
        if (TextUtils.isEmpty(widgetId) || !LuaWidgetManager.exists(widgetId)) {
            Tuils.sendOutput(mContext, "Lua module source not found: " + id)
            return
        }
        if (applyLuaWidgetUnavailablePayload(id, widgetId, false, true, true)) {
            return
        }

        val result = operation.run(getLuaWidgetEngine(widgetId))
        applyLuaWidgetResult(id, widgetId, result, true, true, true)
    }

    private fun applyLuaWidgetUnavailablePayload(
        module: String,
        widgetId: String?,
        stopTicks: Boolean,
        repaint: Boolean,
        updateDock: Boolean
    ): Boolean {
        if (LuaWidgetManager.isEnabled(widgetId) && LuaWidgetManager.isTrusted(widgetId)) {
            return false
        }

        if (stopTicks) {
            handler!!.removeCallbacks(luaWidgetTickRunnable)
        }
        val payload = if (LuaWidgetManager.isEnabled(widgetId))
            LuaWidgetManager.consentPayload(widgetId)
        else
            LuaWidgetManager.disabledPayload(widgetId)
        ModuleManager.setScriptText(mContext, module, payload)
        if (repaint && module == activeModule) {
            repaintActiveTextModule(module)
        }
        if (updateDock) {
            updateModuleDockSelection()
        }
        return true
    }

    private fun applyLuaWidgetResult(
        module: String,
        widgetId: String?,
        result: LuaWidgetEngine.RenderResult,
        scheduleTicks: Boolean,
        repaint: Boolean,
        updateDock: Boolean
    ) {
        ModuleManager.setScriptText(
            mContext,
            module,
            LuaWidgetManager.modulePayload(widgetId, result)
        )
        if (scheduleTicks) {
            scheduleLuaWidgetTickIfNeeded(module, result)
        }
        if (repaint && module == activeModule) {
            repaintActiveLuaWidgetModule(module, widgetId, result)
        }
        if (updateDock) {
            updateModuleDockSelection()
        }
    }

    private fun interface LuaWidgetOperation {
        fun run(engine: LuaWidgetEngine?): LuaWidgetEngine.RenderResult
    }

    private fun getLuaWidgetEngine(widgetId: String?): LuaWidgetEngine {
        val id = LuaWidgetManager.normalizeId(widgetId)
        val version = LuaWidgetManager.version(id)
        var engine = luaWidgetEngines.get(id)
        if (engine == null || engine.version() != version) {
            engine = LuaWidgetEngine(
                mContext,
                id,
                LuaWidgetManager.readScript(id),
                version,
                UpdateListener { updatedWidgetId: String?, result: LuaWidgetEngine.RenderResult ->
                    val modules = modulesForLuaWidget(updatedWidgetId)
                    if (modules.isEmpty()) {
                        return@UpdateListener
                    }
                    for (module in modules) {
                        if (!LuaWidgetManager.isEnabled(updatedWidgetId)) {
                            ModuleManager.setScriptText(
                                mContext,
                                module,
                                LuaWidgetManager.disabledPayload(updatedWidgetId)
                            )
                            handler!!.removeCallbacks(luaWidgetTickRunnable)
                        } else if (!LuaWidgetManager.isTrusted(updatedWidgetId)) {
                            ModuleManager.setScriptText(
                                mContext,
                                module,
                                LuaWidgetManager.consentPayload(updatedWidgetId)
                            )
                            handler!!.removeCallbacks(luaWidgetTickRunnable)
                        } else if (result != null) {
                            ModuleManager.setScriptText(
                                mContext,
                                module,
                                LuaWidgetManager.modulePayload(updatedWidgetId, result)
                            )
                        }
                        if (module == activeModule) {
                            if (result != null
                                && LuaWidgetManager.isEnabled(updatedWidgetId)
                                && LuaWidgetManager.isTrusted(updatedWidgetId)
                            ) {
                                repaintActiveLuaWidgetModule(module, updatedWidgetId, result)
                            } else {
                                repaintActiveTextModule(module)
                            }
                            scheduleLuaWidgetTickIfNeeded(module, result)
                        }
                    }
                    updateModuleDockSelection()
                })
            luaWidgetEngines.put(id, engine)
        }
        return engine
    }

    private fun modulesForLuaWidget(widgetId: String?): MutableList<String> {
        val modules = ArrayList<String>()
        val normalizedWidget = LuaWidgetManager.normalizeId(widgetId)
        for (module in ModuleManager.listAll(mContext)) {
            val source = ModuleManager.getModuleSource(mContext, module)
            if (ModuleManager.isLuaSource(source)
                && TextUtils.equals(normalizedWidget, ModuleManager.luaWidgetId(source))
            ) {
                modules.add(ModuleManager.normalize(module))
            }
        }
        return modules
    }

    private fun repaintActiveTextModule(id: String?) {
        if (homeWidgetsContainer == null) {
            return
        }
        homeWidgetsContainer!!.removeAllViews()
        val text = ModuleManager.getScriptText(mContext, id)
        showTextModule(id, if (TextUtils.isEmpty(text)) "No module output yet." else text)
        refreshSuggestionsForActiveModule()
        scheduleEventsRefreshIfNeeded()
    }

    private fun setupTermuxConsole(rootView: ViewGroup) {
        termuxOverlay = rootView.findViewById<View?>(R.id.termux_overlay)
        if (termuxOverlay == null) {
            return
        }
        termuxOverlayBasePaddingLeft = termuxOverlay!!.getPaddingLeft()
        termuxOverlayBasePaddingTop = termuxOverlay!!.getPaddingTop()
        termuxOverlayBasePaddingRight = termuxOverlay!!.getPaddingRight()
        termuxOverlayBasePaddingBottom = termuxOverlay!!.getPaddingBottom()

        termuxWindowBorder = rootView.findViewById<View?>(R.id.termux_window_border)
        termuxWindowLabel = rootView.findViewById<TextView?>(R.id.termux_window_label)
        termuxClose = rootView.findViewById<TextView?>(R.id.termux_close)
        termuxGrid = rootView.findViewById<TerminalGridView?>(R.id.termux_output_grid)
        termuxOutput = rootView.findViewById<TextView?>(R.id.termux_output)
        termuxRichOutput = rootView.findViewById<LinearLayout?>(R.id.termux_rich_output)
        termuxPrefix = rootView.findViewById<TextView?>(R.id.termux_prefix)
        termuxInput = rootView.findViewById<EditText?>(R.id.termux_input)
        termuxScroll = rootView.findViewById<ScrollView?>(R.id.termux_scroll)
        termuxInputGroup = rootView.findViewById<View?>(R.id.termux_input_group)
        termuxOutputPanel = rootView.findViewById<View?>(R.id.termux_output_panel)
        termuxOutputLabel = rootView.findViewById<TextView?>(R.id.termux_output_label)
        termuxActionsScroll = rootView.findViewById<HorizontalScrollView?>(R.id.termux_actions_scroll)
        termuxActions = rootView.findViewById<LinearLayout?>(R.id.termux_actions)
        termuxTools = rootView.findViewById<View?>(R.id.termux_tools)
        termuxKeysRowOne = rootView.findViewById<ViewGroup?>(R.id.termux_keys_row_one)
        termuxKeysRowTwo = rootView.findViewById<ViewGroup?>(R.id.termux_keys_row_two)
        suggestionsContainer = rootView.findViewById<View?>(R.id.suggestions_container)

        applyTermuxImeBottomPadding()
        styleTermuxConsole()

        termuxOverlay!!.setOnClickListener(View.OnClickListener { v: View? ->
            takeTermuxConsoleFocus(
                true
            )
        })
        if (termuxWindowBorder != null) {
            termuxWindowBorder!!.setOnClickListener(View.OnClickListener { v: View? ->
                takeTermuxConsoleFocus(
                    true
                )
            })
        }
        if (termuxClose != null) {
            termuxClose!!.setOnClickListener(View.OnClickListener { v: View? -> closeTermuxConsole() })
        }

        if (termuxInput != null) {
            applyRetuiKeyboardTheme(termuxInput, "termux")
            termuxInput!!.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                termuxInput!!.setCursorVisible(hasFocus)
                termuxInput!!.setShowSoftInputOnFocus(hasFocus)
            })
            termuxInput!!.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (event.getAction() != KeyEvent.ACTION_UP) {
                        return@OnEditorActionListener true
                    }
                    val command = termuxInput!!.getText().toString()
                    termuxInput!!.setText(Tuils.EMPTYSTRING)
                    submitTermuxConsoleCommand(command)
                    return@OnEditorActionListener true
                }
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    val command = termuxInput!!.getText().toString()
                    termuxInput!!.setText(Tuils.EMPTYSTRING)
                    submitTermuxConsoleCommand(command)
                    true
                } else {
                    false
                }
            })
            termuxInput!!.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    termuxInsertedStart = -1
                    termuxInsertedText = null
                    if (termuxSuppressInputWatcher || termuxAppSession == null || !hasTermuxAppPendingModifiers()) {
                        return
                    }
                    if (before == 0 && count == 1 && s != null && start >= 0 && start + count <= s.length) {
                        termuxInsertedStart = start
                        termuxInsertedText = s.subSequence(start, start + count).toString()
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    consumeTermuxAppInsertedCombo(s)
                }
            })
        }

        bindTermuxExtraKeys(rootView)
    }

    private fun bindTermuxExtraKeys(rootView: View) {
        if (termuxKeysRowOne == null) {
            termuxKeysRowOne = rootView.findViewById<ViewGroup?>(R.id.termux_keys_row_one)
        }
        if (termuxKeysRowTwo == null) {
            termuxKeysRowTwo = rootView.findViewById<ViewGroup?>(R.id.termux_keys_row_two)
        }
        bindTermuxAppKeyModeSwipe(termuxTools)
        bindTermuxAppKeyModeSwipe(termuxKeysRowOne)
        bindTermuxAppKeyModeSwipe(termuxKeysRowTwo)
        updateTermuxConsoleKeyMode()
    }

    private fun updateTermuxConsoleKeyMode() {
        if (termuxAppSession == null) {
            bindPlainTermuxConsoleKeys()
        } else {
            updateTermuxAppKeyMode()
        }
    }

    private fun bindPlainTermuxConsoleKeys() {
        clearTermuxAppModifiers(false)
        termuxFnKeyMode = false
        termuxKeyModeAnimating = false
        termuxKeySwipeConsumed = false
        syncTermuxKeyRows(
            termuxKeysRowOne,
            termuxKeysRowTwo,
            termuxKeySlots,
            false,
            this::bindTermuxAppKeyModeSwipe
        )
        val labels = arrayOf(
            "ESC", "/", "-", "HOME", "↑", "END", "PGUP", "CFG",
            "TAB", "CTRL", "ALT", "←", "↓", "→", "PGDN", "IME"
        )
        val actions = arrayOf(
            Runnable { this.handleTermuxEscapeKey() },
            Runnable { insertIntoTermuxInput("/") },
            Runnable { insertIntoTermuxInput("-") },
            Runnable { moveTermuxInputCursorToBoundary(true) },
            Runnable { recallTermuxHistory(-1) },
            Runnable { moveTermuxInputCursorToBoundary(false) },
            Runnable { scrollTermuxOutput(-1) },
            Runnable { submitTermuxConsoleCommand("setup") },
            Runnable { insertIntoTermuxInput("\t") },
            Runnable { this.interruptTermuxInput() },
            Runnable { focusTermuxInput(false) },
            Runnable { moveTermuxInputCursorBy(-1) },
            Runnable { recallTermuxHistory(1) },
            Runnable { moveTermuxInputCursorBy(1) },
            Runnable { scrollTermuxOutput(1) },
            Runnable { this.toggleTermuxKeyboard() }
        )
        val color = notificationWidgetTextColor()
        for (i in termuxKeySlots.indices) {
            val key = termuxKeySlots[i]
            val label = labels.getOrNull(i) ?: ""
            val action = actions.getOrNull(i)
            key.text = label
            key.textSize = if (label.length >= 5) 10f else 11f
            key.alpha = 0.82f
            styleTermuxToolButton(key, color)
            key.setOnClickListener(View.OnClickListener { v: View? ->
                if (action != null) {
                    action.run()
                }
            })
        }
    }

    private fun updateTermuxAppKeyMode() {
        syncTermuxKeyRows(
            termuxKeysRowOne,
            termuxKeysRowTwo,
            termuxKeySlots,
            termuxAppCombinedKeyTray(),
            this::bindTermuxAppKeyModeSwipe
        )
        if (termuxKeySlots.isEmpty()) {
            return
        }
        termuxCtrlKey = null
        termuxAltKey = null
        termuxShiftKey = null
        val specs = activeTermuxAppKeySpecs()
        val normalColor = notificationWidgetTextColor()
        for (i in termuxKeySlots.indices) {
            val key = termuxKeySlots[i]
            val spec = specs.getOrNull(i)
            if (spec == null) {
                key.visibility = View.INVISIBLE
                key.setOnClickListener(null)
                continue
            }
            key.visibility = View.VISIBLE
            key.text = spec.label
            key.textSize = if (spec.label.length >= 5) 10f else 11f
            key.alpha = 0.82f
            styleTermuxToolButton(key, normalColor)
            when (spec.modifier) {
                TermuxWorkspaceModifier.CTRL -> termuxCtrlKey = key
                TermuxWorkspaceModifier.ALT -> termuxAltKey = key
                TermuxWorkspaceModifier.SHIFT -> termuxShiftKey = key
                null -> {}
            }
            key.setOnClickListener(View.OnClickListener { v: View? ->
                if (termuxKeySwipeConsumed) {
                    termuxKeySwipeConsumed = false
                    return@OnClickListener
                }
                handleTermuxAppKeySpec(spec)
            })
        }
        updateTermuxAppModifierButtons()
    }

    private fun handleTermuxAppKeySpec(spec: TermuxWorkspaceKeySpec) {
        if (termuxAppSession == null) {
            bindPlainTermuxConsoleKeys()
            return
        }
        if (spec.togglesMode) {
            setTermuxAppFnKeyMode(!termuxFnKeyMode)
            return
        }
        if (spec.refreshes) {
            clearTermuxAppModifiers(true)
            refreshTermuxAppSession(true)
            scheduleTermuxAppRefreshBurst(termuxAppSession?.id, TERMUX_APP_MANUAL_REFRESH_WATCH_MS)
            return
        }
        when (spec.modifier) {
            TermuxWorkspaceModifier.CTRL -> termuxCtrlPending = !termuxCtrlPending
            TermuxWorkspaceModifier.ALT -> termuxAltPending = !termuxAltPending
            TermuxWorkspaceModifier.SHIFT -> termuxShiftPending = !termuxShiftPending
            null -> {
                val keyName = spec.keyName ?: return
                sendTermuxAppKey(applyTermuxAppModifiers(keyName))
                return
            }
        }
        updateTermuxAppModifierButtons()
    }

    private fun setTermuxAppFnKeyMode(enabled: Boolean): Boolean {
        if (termuxAppCombinedKeyTray()) {
            return false
        }
        if (termuxFnKeyMode == enabled) {
            return false
        }
        termuxTools?.animate()?.cancel()
        termuxKeyModeAnimating = false
        termuxTools?.translationX = 0f
        termuxTools?.alpha = 1f
        termuxFnKeyMode = enabled
        updateTermuxAppKeyMode()
        return true
    }

    private fun animateTermuxAppFnKeyMode(enabled: Boolean, direction: Int): Boolean {
        if (termuxAppCombinedKeyTray() || termuxFnKeyMode == enabled || termuxKeyModeAnimating) {
            return false
        }
        val tools = termuxTools
        val width = tools?.width ?: 0
        if (tools == null || width <= 0) {
            return setTermuxAppFnKeyMode(enabled)
        }
        val cleanDirection = if (direction < 0) -1 else 1
        termuxKeyModeAnimating = true
        tools.animate().cancel()
        tools.animate()
            .translationX((-cleanDirection * width).toFloat())
            .alpha(0.08f)
            .setDuration(TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_OUT_MS)
            .withEndAction(Runnable {
                termuxFnKeyMode = enabled
                updateTermuxAppKeyMode()
                tools.translationX = (cleanDirection * width).toFloat()
                tools.alpha = 0.08f
                tools.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_IN_MS)
                    .withEndAction(Runnable {
                        tools.translationX = 0f
                        tools.alpha = 1f
                        termuxKeyModeAnimating = false
                    })
                    .start()
            })
            .start()
        return true
    }

    private fun bindTermuxAppKeyModeSwipe(view: View?) {
        if (view == null) {
            return
        }
        view.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event == null || termuxAppSession == null) {
                return@OnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (termuxAppCombinedKeyTray()) {
                        return@OnTouchListener false
                    }
                    termuxKeyTouchStartX = event.rawX
                    termuxKeyTouchStartY = event.rawY
                    termuxKeySwipeConsumed = false
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - termuxKeyTouchStartX
                    val dy = event.rawY - termuxKeyTouchStartY
                    if (!termuxAppCombinedKeyTray()
                        && abs(dx) > TERMUX_WORKSPACE_KEY_MODE_SWIPE_THRESHOLD_PX
                        && abs(dx) > abs(dy) * 1.35f
                    ) {
                        termuxKeySwipeConsumed = true
                        animateTermuxAppFnKeyMode(!termuxFnKeyMode, if (dx < 0f) 1 else -1)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    termuxKeySwipeConsumed = false
                    false
                }
                else -> false
            }
        })
    }

    private fun applyTermuxAppModifiers(keyName: String): String {
        val ctrl = termuxCtrlPending
        val alt = termuxAltPending
        val shift = termuxShiftPending
        if (!ctrl && !alt && !shift) {
            return keyName
        }
        termuxCtrlPending = false
        termuxAltPending = false
        termuxShiftPending = false
        updateTermuxAppModifierButtons()
        val prefix = StringBuilder()
        if (ctrl) {
            prefix.append("C-")
        }
        if (alt) {
            prefix.append("M-")
        }
        if (shift) {
            prefix.append("S-")
        }
        return prefix.append(keyName).toString()
    }

    private fun clearTermuxAppModifiers(updateButtons: Boolean) {
        termuxCtrlPending = false
        termuxAltPending = false
        termuxShiftPending = false
        if (updateButtons) {
            updateTermuxAppModifierButtons()
        }
    }

    private fun hasTermuxAppPendingModifiers(): Boolean {
        return termuxCtrlPending || termuxAltPending || termuxShiftPending
    }

    private fun consumeTermuxAppInsertedCombo(text: Editable?) {
        val inserted = termuxInsertedText
        val start = termuxInsertedStart
        termuxInsertedText = null
        termuxInsertedStart = -1
        if (termuxAppSession == null || text == null || inserted.isNullOrEmpty() || start < 0) {
            return
        }
        val key = mapTermuxAppPrintableCombo(inserted) ?: return
        if (start + inserted.length > text.length ||
            text.subSequence(start, start + inserted.length).toString() != inserted
        ) {
            return
        }
        termuxSuppressInputWatcher = true
        try {
            text.delete(start, start + inserted.length)
        } finally {
            termuxSuppressInputWatcher = false
        }
        clearTermuxAppModifiers(true)
        sendTermuxAppKey(key)
    }

    private fun mapTermuxAppPrintableCombo(text: String): String? {
        if (text.length != 1 || !hasTermuxAppPendingModifiers()) {
            return null
        }
        val ch = text[0]
        val ctrl = termuxCtrlPending
        val alt = termuxAltPending
        if (!ctrl && !alt) {
            return null
        }
        if (ctrl && !alt && ch == '[') {
            return "Escape"
        }
        if (ctrl) {
            val ctrlKey = when {
                ch in 'a'..'z' || ch in 'A'..'Z' -> ch.lowercaseChar().toString()
                ch == ' ' -> "Space"
                else -> null
            } ?: return null
            return if (alt) "C-M-$ctrlKey" else "C-$ctrlKey"
        }
        val altKey = tmuxWorkspacePrintableKeyName(ch) ?: return null
        return "M-$altKey"
    }

    private fun updateTermuxAppModifierButtons() {
        val normalColor = notificationWidgetTextColor()
        val activeColor = terminalBorderColor()
        termuxCtrlKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "CTRL", termuxCtrlPending, normalColor, activeColor)
        }
        termuxAltKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "ALT", termuxAltPending, normalColor, activeColor)
        }
        termuxShiftKey?.let { key ->
            updateTermuxWorkspaceModifierButton(key, "SHIFT", termuxShiftPending, normalColor, activeColor)
        }
    }

    private fun sendTermuxAppKey(key: String) {
        val app = termuxAppSession ?: return
        val clean = key.trim { it <= ' ' }
        if (clean.length == 0) {
            return
        }
        termuxAppLastStatus = "key: " + clean
        dispatchTermuxAppScript("key", buildTermuxAppControlScript(app, clean), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
        scheduleTermuxConsoleFocusCapture(true)
    }

    private fun setupFileConsole(rootView: ViewGroup) {
        fileOverlay = rootView.findViewById<View?>(R.id.file_overlay)
        if (fileOverlay == null) {
            return
        }
        fileOverlayBasePaddingLeft = fileOverlay!!.getPaddingLeft()
        fileOverlayBasePaddingTop = fileOverlay!!.getPaddingTop()
        fileOverlayBasePaddingRight = fileOverlay!!.getPaddingRight()
        fileOverlayBasePaddingBottom = fileOverlay!!.getPaddingBottom()

        fileWindowBorder = rootView.findViewById<View?>(R.id.file_window_border)
        fileWindowLabel = rootView.findViewById<TextView?>(R.id.file_window_label)
        fileClose = rootView.findViewById<TextView?>(R.id.file_close)
        filePath = rootView.findViewById<TextView?>(R.id.file_path)
        fileOutput = rootView.findViewById<TextView?>(R.id.file_output)
        filePrefix = rootView.findViewById<TextView?>(R.id.file_prefix)
        fileInput = rootView.findViewById<EditText?>(R.id.file_input)
        fileScroll = rootView.findViewById<ScrollView?>(R.id.file_scroll)
        fileInputGroup = rootView.findViewById<View?>(R.id.file_input_group)
        fileTools = rootView.findViewById<View?>(R.id.file_tools)
        fileRefresh = rootView.findViewById<TextView?>(R.id.file_refresh)
        fileUp = rootView.findViewById<TextView?>(R.id.file_up)
        fileOpen = rootView.findViewById<TextView?>(R.id.file_open)
        filePaste = rootView.findViewById<TextView?>(R.id.file_paste)

        styleFileConsole()

        if (fileClose != null) {
            fileClose!!.setOnClickListener(View.OnClickListener { v: View? -> closeFileConsole() })
        }

        if (fileInput != null) {
            applyRetuiKeyboardTheme(fileInput, "files")
            fileInput!!.setOnEditorActionListener(OnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
                val enter =
                    event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                    val command = fileInput!!.getText().toString()
                    fileInput!!.setText(Tuils.EMPTYSTRING)
                    executeFileConsoleCommand(command)
                    true
                } else {
                    false
                }
            })
        }

        if (fileRefresh != null) {
            fileRefresh!!.setOnClickListener(View.OnClickListener { v: View? ->
                refreshFileConsole(
                    true
                )
            })
        }
        if (fileUp != null) {
            fileUp!!.setOnClickListener(View.OnClickListener { v: View? ->
                executeFileConsoleCommand(
                    "cd .."
                )
            })
        }
        if (fileOpen != null) {
            fileOpen!!.setOnClickListener(View.OnClickListener { v: View? ->
                if (fileInput != null) {
                    fileInput!!.setText("open ")
                    fileInput!!.setSelection(fileInput!!.getText().length)
                    fileInput!!.requestFocus()
                }
            })
        }
        if (filePaste != null) {
            filePaste!!.setOnClickListener(View.OnClickListener { v: View? ->
                val text = Tuils.getTextFromClipboard(mContext)
                if (text != null && text.length > 0 && fileInput != null) {
                    val start = max(fileInput!!.getSelectionStart(), 0)
                    val end = max(fileInput!!.getSelectionEnd(), 0)
                    fileInput!!.getText().replace(min(start, end), max(start, end), text)
                }
            })
        }
    }

    init {
        this.mRootView = rootView
        this.mainPack = mainPack
        this.mExecuter = executer

        val filter = IntentFilter()
        filter.addAction(ACTION_UPDATE_SUGGESTIONS)
        filter.addAction(ACTION_UPDATE_HINT)
        filter.addAction(ACTION_ROOT)
        filter.addAction(ACTION_NOROOT)
        filter.addAction(ACTION_LOGTOFILE)
        filter.addAction(ACTION_CLEAR)
        filter.addAction(ACTION_HACK)
        filter.addAction(ACTION_WEATHER)
        filter.addAction(ACTION_WEATHER_GOT_LOCATION)
        filter.addAction(ACTION_WEATHER_DELAY)
        filter.addAction(ACTION_WEATHER_MANUAL_UPDATE)
        filter.addAction(ACTION_MUSIC_CHANGED)
        filter.addAction(ACTION_NOTIFICATION_FEED)
        filter.addAction(ACTION_CLOCK_STATE)
        filter.addAction(ACTION_POMODORO_STATE)
        filter.addAction(ACTION_TERMUX_CONSOLE)
        filter.addAction(ACTION_LUA_APP)
        filter.addAction(ACTION_FILE_CONSOLE)
        filter.addAction(ACTION_TERMUX_RESULT)
        filter.addAction(ACTION_MODULE_COMMAND)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getAction()

                if (action == ACTION_UPDATE_SUGGESTIONS) {
                    if (suggestionsManager != null) suggestionsManager!!.requestSuggestion(Tuils.EMPTYSTRING)
                    refreshModuleSuggestionsStrip()
                } else if (action == ACTION_UPDATE_HINT) {
                    mTerminalAdapter!!.setDefaultHint()
                    refreshFileConsole(false)
                } else if (action == ACTION_ROOT) {
                    mTerminalAdapter!!.onRoot()
                } else if (action == ACTION_CLOCK_STATE) {
                    lastClockStateIntent = Intent(intent)
                    updateClockOverlay(intent)
                    refreshActiveModuleIfNeeded()
                } else if (action == ACTION_POMODORO_STATE) {
                    lastPomodoroStateIntent = Intent(intent)
                    updatePomodoroOverlay(intent)
                    refreshActiveModuleIfNeeded()
                } else if (action == ACTION_TERMUX_CONSOLE) {
                    openTermuxConsole(intent.getStringExtra(EXTRA_TERMUX_COMMAND))
                } else if (action == ACTION_LUA_APP) {
                    openLuaApp(intent.getStringExtra(EXTRA_LUA_APP_ID))
                } else if (action == ACTION_FILE_CONSOLE) {
                    openFileConsole(intent.getStringExtra(EXTRA_FILE_COMMAND))
                } else if (action == ACTION_TERMUX_RESULT) {
                    appendTermuxResult(intent)
                } else if (action == ACTION_MODULE_COMMAND) {
                    handleModuleCommand(intent)
                } else if (action == ACTION_NOROOT) {
                    mTerminalAdapter!!.onStandard()
                } else if (action == ACTION_LOGTOFILE) {
                    val fileName = intent.getStringExtra(FILE_NAME)
                    if (fileName == null || fileName.contains(File.separator)) return

                    val file = File(Tuils.getFolder(), fileName)
                    if (file.exists()) file.delete()

                    try {
                        file.createNewFile()

                        val fos = FileOutputStream(file)
                        fos.write(mTerminalAdapter!!.terminalText.toByteArray())

                        Tuils.sendOutput(context, "Logged to " + file.getAbsolutePath())
                    } catch (e: Exception) {
                        Tuils.sendOutput(Color.RED, context, e.toString())
                    }
                } else if (action == ACTION_CLEAR) {
                    mTerminalAdapter!!.clear()
                    if (suggestionsManager != null) suggestionsManager!!.requestSuggestion(Tuils.EMPTYSTRING)
                } else if (action == ACTION_HACK) {
                    playHackOverlay()
                } else if (action == ACTION_WEATHER) {
                    val c = Calendar.getInstance()

                    var s = intent.getCharSequenceExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) s = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) return

                    lastWeatherText = s
                    lastWeatherUpdateMillis = System.currentTimeMillis()
                    s = Tuils.span(context, s, weatherColor, labelSizes[Label.weather.ordinal])

                    updateText(Label.weather, s)

                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_updated) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE) + Tuils.SPACE + "(" + lastLatitude + ", " + lastLongitude + ")"
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }
                    if (ModuleManager.WEATHER == activeModule) {
                        showHomeModule(ModuleManager.WEATHER)
                    }
                } else if (action == WeatherManager.ACTION_WEATHER_GOT_LOCATION) {
                    if (intent.getBooleanExtra(TuiLocationManager.FAIL, false)) {
                        if (weatherManager != null) {
                            weatherManager!!.stop()
                            weatherManager = null
                        }

                        val raw: CharSequence = context.getString(R.string.location_error)
                        lastWeatherText = raw
                        lastWeatherUpdateMillis = System.currentTimeMillis()
                        val s: CharSequence = Tuils.span(
                            context,
                            raw,
                            weatherColor,
                            labelSizes[Label.weather.ordinal]
                        )

                        updateText(Label.weather, s)
                        if (ModuleManager.WEATHER == activeModule) {
                            showHomeModule(ModuleManager.WEATHER)
                        }
                    } else {
                        lastLatitude = intent.getDoubleExtra(TuiLocationManager.LATITUDE, 0.0)
                        lastLongitude = intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0.0)

                        location = Tuils.locationName(context, lastLatitude, lastLongitude)

                        if (weatherManager != null) {
                            weatherManager!!.setLocation(lastLatitude, lastLongitude)
                        }
                    }
                } else if (action == ACTION_WEATHER_DELAY) {
                    val c = Calendar.getInstance()
                    c.setTimeInMillis(System.currentTimeMillis() + 1000 * 10)

                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_error) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE)
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }

                    if (weatherManager != null) {
                        weatherManager!!.stop()
                        weatherManager!!.start()
                    }
                } else if (action == ACTION_WEATHER_MANUAL_UPDATE) {
                    if (weatherManager != null) {
                        weatherManager!!.stop()
                    }
                    weatherManager = WeatherManager(
                        mContext!!,
                        weatherDelay.toLong(),
                        labelSizes[Label.weather.ordinal],
                        statusUpdateListener
                    )
                    weatherManager!!.start()
                } else if (action == ACTION_MUSIC_CHANGED) {
                    Log.d("TUI-Music", "UIManager received music change broadcast")
                    var song = intent.getStringExtra(SONG_TITLE)
                    val singer = intent.getStringExtra(SONG_SINGER)
                    var isPlaying = intent.getBooleanExtra(MUSIC_PLAYING, false)
                    val source = intent.getStringExtra(MusicService.MUSIC_SOURCE)
                    val pkg = intent.getStringExtra("package")

                    val preferredPkg = preferredPackage()
                    val isPreferred = TextUtils.isEmpty(preferredPkg) || preferredPkg == pkg

                    // Source logic: external always wins if it is playing.
                    // Internal only wins if it's playing and external is not.
                    if (source != null) {
                        if (MusicService.SOURCE_EXTERNAL == source) {
                            // Strictly filter external source if a preferred app is set
                            if (!isPreferred) {
                                isPlaying = false
                                song = null
                            }
                            activeMusicSource = source
                        } else if (MusicService.SOURCE_INTERNAL == source && MusicService.SOURCE_EXTERNAL == activeMusicSource) {
                            // Don't let internal idle broadcast override external metadata
                            if (!isPlaying) return
                            activeMusicSource = source
                        } else {
                            activeMusicSource = source
                        }
                    }

                    Log.d(
                        "TUI-Music",
                        "UIManager update UI: " + song + ", isPlaying=" + isPlaying + " source=" + activeMusicSource + " pkg=" + pkg
                    )

                    val hasContent = (song != null && !song.isEmpty() && (song != "-"))
                    val showMusicWidget = isPlaying || hasContent
                    lastMusicSong = song
                    lastMusicSinger = singer
                    lastMusicPlaying = isPlaying
                    lastMusicAppPackage =
                        if (MusicService.SOURCE_EXTERNAL == activeMusicSource
                            && isPreferred
                            && showMusicWidget
                            && !TextUtils.isEmpty(pkg)
                        ) pkg else null

                    if (isPlaying && autoShowWidget() && ModuleManager.MUSIC != activeModule) {
                        showHomeModule(ModuleManager.MUSIC)
                    }

                    val musicWidget = rootView.findViewById<View?>(R.id.music_widget)
                    if (musicWidget != null) {
                        musicWidget.setVisibility(if (showMusicWidget) View.VISIBLE else View.GONE)
                    }
                    updateContextContainerVisibility(rootView)

                    val widgetBorderColor = musicWidgetBorderColor()
                    val widgetTextColor = musicWidgetTextColor()

                    val visualizerView =
                        rootView.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
                    if (visualizerView != null) {
                        visualizerView.setBarColor(widgetTextColor)
                        visualizerView.setPlaying(isPlaying)
                    }

                    val songTitleView = rootView.findViewById<TextView?>(R.id.music_song_title)
                    if (songTitleView != null) {
                        songTitleView.setText(if (song != null) "Title: " + song.uppercase(Locale.getDefault()) else "Title: -")
                        songTitleView.setTextColor(widgetTextColor)
                    }

                    val singerView = rootView.findViewById<TextView?>(R.id.music_singer)
                    if (singerView != null) {
                        singerView.setText(
                            if (singer != null) "Singer      : " + singer.uppercase(
                                Locale.getDefault()
                            ) else "Singer      : -"
                        )
                        singerView.setTextColor(widgetTextColor)
                    }

                    updateMusicAppRow(rootView, widgetTextColor)

                    decorateWidget(
                        rootView,
                        R.id.music_widget_border,
                        R.id.music_widget_label,
                        R.id.music_widget_close,
                        widgetBorderColor,
                        widgetTextColor
                    )
                    styleModuleClose(rootView.findViewById<TextView?>(R.id.music_widget_close))
                    sizeMusicVisualizer(rootView)
                    scheduleInternalMusicTickerIfNeeded()
                } else if (action == ACTION_NOTIFICATION_FEED) {
                    val notifications =
                        intent.getParcelableArrayListExtra<NotificationService.Notification?>(
                            EXTRA_NOTIFICATION_LIST
                        )
                    updateNotificationWidget(rootView, notifications)
                } else if (action == ACTION_CLOCK_STATE) {
                    updateClockOverlay(intent)
                    val message = intent.getStringExtra(ClockManager.EXTRA_MESSAGE)
                    if (!TextUtils.isEmpty(message)) {
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }
                }
            }
        }

        policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
        component = ComponentName(context, PolicyReceiver::class.java)

        mContext = context

        preferences = mContext!!.getSharedPreferences(PREFS_NAME, 0)
        duoLayoutMode = preferences!!.getString(DUO_LAYOUT_PREF, DUO_LAYOUT_OFF)

        handler = Handler(Looper.getMainLooper())

        imm = mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if (cyberdeckMode()) {
            val bgColor =
                if (!XMLPrefsManager.getBoolean(Ui.system_wallpaper) || !canApplyTheme)
                    getColor(Theme.background_color)
                else getColor(Theme.wallpaper_overlay_color)
            rootView.background = CyberpunkBackdropDrawable(
                bgColor,
                terminalBorderColor(),
                getColor(Theme.device_text_color)
            )
        } else if (!XMLPrefsManager.getBoolean(Ui.system_wallpaper) || !canApplyTheme) {
            rootView.setBackgroundColor(getColor(Theme.background_color))
        } else {
            rootView.setBackgroundColor(getColor(Theme.wallpaper_overlay_color))
        }

        styleHackOverlay(rootView)
        setupTermuxConsole(rootView)
        setupFileConsole(rootView)
        setupResponsiveLandscapeLayout(rootView)

        //        Recalculate tray sizing after real layout changes; IME visibility comes from WindowInsets.
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(OnGlobalLayoutListener {
            updateKeyboardLayoutState(keyboardVisible, rootView.getHeight())
        })

        clearOnLock = XMLPrefsManager.getBoolean(Behavior.clear_on_lock)

        lockOnDbTap = XMLPrefsManager.getBoolean(Behavior.double_tap_lock)
        doubleTapCmd = XMLPrefsManager.get(Behavior.double_tap_cmd)
        swipeDownNotifications = XMLPrefsManager.getBoolean(Behavior.swipe_down_notifications)
        swipeUpAppsDrawer = false

        if (!lockOnDbTap && doubleTapCmd == null && !swipeDownNotifications && !swipeUpAppsDrawer) {
            policy = null
            component = null
            gestureDetector = null
        } else {
            gestureDetector =
                GestureDetectorCompat(mContext!!, object : GestureDetector.OnGestureListener {
                    override fun onDown(e: MotionEvent): Boolean {
                        return false
                    }

                    override fun onShowPress(e: MotionEvent) {}

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        return false
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        return false
                    }

                    override fun onLongPress(e: MotionEvent) {}

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        if (swipeDownNotifications && velocityY > 100 && abs(velocityY) > abs(
                                velocityX
                            )
                        ) {
                            return openNotificationShade()
                        }
                        return false
                    }
                })

            gestureDetector!!.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    return false
                }

                override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val cmd = doubleTapCmd
                    if (!cmd.isNullOrEmpty()) {
                        val input = mTerminalAdapter!!.input
                        mTerminalAdapter!!.setInput(cmd, null)
                        mTerminalAdapter!!.simulateEnter()
                        mTerminalAdapter!!.setInput(input, null)
                    }

                    if (lockOnDbTap) {
                        val admin = policy!!.isAdminActive(component!!)

                        if (!admin) {
                            val i = Tuils.requestAdmin(
                                component,
                                mContext!!.getString(R.string.admin_permission)
                            )
                            mContext!!.startActivity(i)
                        } else {
                            policy!!.lockNow()
                        }
                    }

                    return true
                }
            })

            rootView.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
                if (gestureDetector != null) {
                    val handled = gestureDetector!!.onTouchEvent(event!!)
                    if (!handled && event.getAction() == MotionEvent.ACTION_UP) {
                        v!!.performClick()
                    }
                    handled
                } else {
                    false
                }
            })
        }

        appsDrawerRoot = rootView.findViewById<View?>(R.id.apps_drawer_root)
        appsDrawerContainer = rootView.findViewById<View?>(R.id.apps_drawer_container)
        captureBaseMargins(appsDrawerContainer, appsDrawerContainerBaseMargins)
        appsList = rootView.findViewById<ListView?>(R.id.apps_list)
        appsGroupTabs = rootView.findViewById<LinearLayout?>(R.id.apps_group_tabs)
        appsAlphaTabs = rootView.findViewById<LinearLayout?>(R.id.apps_alpha_tabs)
        appsDrawerHeader = rootView.findViewById<TextView>(R.id.apps_drawer_header)
        appsDrawerFooter = rootView.findViewById<TextView>(R.id.apps_drawer_footer)

        val dummyAnchor = rootView.findViewById<View?>(R.id.apps_drawer_dummy_input_anchor)
        if (dummyAnchor != null) {
            rootView.post(Runnable {
                val lp = dummyAnchor.getLayoutParams() as RelativeLayout.LayoutParams
                var height = 0
                val inputGroup = rootView.findViewById<View?>(R.id.input_group)
                if (inputGroup != null) height += inputGroup.getHeight()

                val toolsView = rootView.findViewById<View?>(R.id.tools_view)
                if (toolsView != null && toolsView.getVisibility() == View.VISIBLE) {
                    height += toolsView.getHeight()
                }
                val suggestions = rootView.findViewById<View?>(R.id.suggestions_container)
                if (suggestions != null && suggestions.getVisibility() == View.VISIBLE) {
                    height += suggestions.getHeight()
                }
                lp.height = height
                lp.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
                lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                dummyAnchor.setLayoutParams(lp)
            })
        }

        if (appsDrawerRoot != null) {
            appsDrawerRoot!!.setOnClickListener(View.OnClickListener { v: View? -> hideAppsDrawer() })
        }

        applyDisplayMarginsForConfiguration(mContext!!.getResources().getConfiguration())

        labelSizes[Label.time.ordinal] = XMLPrefsManager.getInt(Ui.time_size)
        labelSizes[Label.ram.ordinal] = XMLPrefsManager.getInt(Ui.ram_size)
        labelSizes[Label.battery.ordinal] = XMLPrefsManager.getInt(Ui.battery_size)
        labelSizes[Label.storage.ordinal] = XMLPrefsManager.getInt(Ui.storage_size)
        labelSizes[Label.network.ordinal] = XMLPrefsManager.getInt(Ui.network_size)
        labelSizes[Label.notes.ordinal] = XMLPrefsManager.getInt(Ui.notes_size)
        labelSizes[Label.device.ordinal] = XMLPrefsManager.getInt(Ui.device_size)
        labelSizes[Label.weather.ordinal] = XMLPrefsManager.getInt(Ui.weather_size)
        labelSizes[Label.unlock.ordinal] = XMLPrefsManager.getInt(Ui.unlock_size)
        labelSizes[Label.ascii.ordinal] = XMLPrefsManager.getInt(Ui.ascii_size)

        labelViews = arrayOf<TextView?>(
            rootView.findViewById<View?>(R.id.tv0) as TextView?,
            rootView.findViewById<View?>(R.id.tv1) as TextView?,
            rootView.findViewById<View?>(R.id.tv2) as TextView?,
            rootView.findViewById<View?>(R.id.tv3) as TextView?,
            rootView.findViewById<View?>(R.id.tv4) as TextView?,
            rootView.findViewById<View?>(R.id.tv5) as TextView?,
            rootView.findViewById<View?>(R.id.tv6) as TextView?,
            rootView.findViewById<View?>(R.id.tv7) as TextView?,
            rootView.findViewById<View?>(R.id.tv8) as TextView?,
            rootView.findViewById<View?>(R.id.tv9) as TextView?,
        )
        Arrays.fill(labelIndexes, LABEL_INDEX_UNMAPPED)
        Arrays.fill(labelTexts, null)

        val show = BooleanArray(Label.entries.size)
        show[Label.notes.ordinal] = XMLPrefsManager.getBoolean(Ui.show_notes)
        show[Label.ram.ordinal] = XMLPrefsManager.getBoolean(Ui.show_ram)
        show[Label.device.ordinal] = XMLPrefsManager.getBoolean(Ui.show_device_name)
        show[Label.time.ordinal] = XMLPrefsManager.getBoolean(Ui.show_time)
        show[Label.battery.ordinal] = XMLPrefsManager.getBoolean(Ui.show_battery)
        show[Label.network.ordinal] = XMLPrefsManager.getBoolean(Ui.show_network_info)
        show[Label.storage.ordinal] = XMLPrefsManager.getBoolean(Ui.show_storage_info)
        show[Label.weather.ordinal] = XMLPrefsManager.getBoolean(Ui.show_weather)
        show[Label.unlock.ordinal] = XMLPrefsManager.getBoolean(Ui.show_unlock_counter)
        show[Label.ascii.ordinal] = XMLPrefsManager.getBoolean(Ui.show_ascii)

        val indexes = FloatArray(Label.entries.size)
        indexes[Label.notes.ordinal] =
            if (show[Label.notes.ordinal]) XMLPrefsManager.getFloat(Ui.notes_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.ram.ordinal] =
            if (show[Label.ram.ordinal]) XMLPrefsManager.getFloat(Ui.ram_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.device.ordinal] =
            if (show[Label.device.ordinal]) XMLPrefsManager.getFloat(Ui.device_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.time.ordinal] =
            if (show[Label.time.ordinal]) XMLPrefsManager.getFloat(Ui.time_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.battery.ordinal] =
            if (show[Label.battery.ordinal]) XMLPrefsManager.getFloat(Ui.battery_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.network.ordinal] =
            if (show[Label.network.ordinal]) XMLPrefsManager.getFloat(Ui.network_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.storage.ordinal] =
            if (show[Label.storage.ordinal]) XMLPrefsManager.getFloat(Ui.storage_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.weather.ordinal] =
            if (show[Label.weather.ordinal]) XMLPrefsManager.getFloat(Ui.weather_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.unlock.ordinal] =
            if (show[Label.unlock.ordinal]) XMLPrefsManager.getFloat(Ui.unlock_index) else Int.Companion.MAX_VALUE.toFloat()
        indexes[Label.ascii.ordinal] =
            if (show[Label.ascii.ordinal]) XMLPrefsManager.getFloat(Ui.ascii_index) else Int.Companion.MAX_VALUE.toFloat()

        val statusLineAlignments = intArrayOf(
            getInt(Ui.ram_status_alignment),
            getInt(Ui.device_status_alignment),
            getInt(Ui.time_status_alignment),
            getInt(Ui.battery_status_alignment),
            getInt(Ui.storage_status_alignment),
            getInt(Ui.network_status_alignment),
            getInt(Ui.notes_status_alignment),
            getInt(Ui.weather_status_alignment),
            getInt(Ui.unlock_status_alignment),
            getInt(Ui.ascii_status_alignment)
        )

        fun themeColor(theme: Theme): String = String.format(
            Locale.US,
            "#%08X",
            XMLPrefsManager.getColor(theme)
        )

        val statusLineBgColors = arrayOf<String?>(
            themeColor(Theme.ram_status_background_color),
            themeColor(Theme.device_status_background_color),
            themeColor(Theme.time_status_background_color),
            themeColor(Theme.battery_status_background_color),
            themeColor(Theme.storage_status_background_color),
            themeColor(Theme.network_status_background_color),
            themeColor(Theme.notes_status_background_color),
            themeColor(Theme.weather_status_background_color),
            themeColor(Theme.unlock_status_background_color),
            themeColor(Theme.ascii_status_background_color)
        )
        val otherBgColors = arrayOf<String?>(
            themeColor(Theme.input_background_color),
            themeColor(Theme.output_background_color),
            themeColor(Theme.suggestions_background_color),
            themeColor(Theme.toolbar_background_color)
        )
        bgColors = arrayOfNulls<String>(statusLineBgColors.size + otherBgColors.size)
        System.arraycopy(statusLineBgColors, 0, bgColors, 0, statusLineBgColors.size)
        System.arraycopy(otherBgColors, 0, bgColors, statusLineBgColors.size, otherBgColors.size)

        val statusLineOutlineColors = arrayOf<String?>(
            themeColor(Theme.ram_status_text_shadow_color),
            themeColor(Theme.device_status_text_shadow_color),
            themeColor(Theme.time_status_text_shadow_color),
            themeColor(Theme.battery_status_text_shadow_color),
            themeColor(Theme.storage_status_text_shadow_color),
            themeColor(Theme.network_status_text_shadow_color),
            themeColor(Theme.notes_status_text_shadow_color),
            themeColor(Theme.weather_status_text_shadow_color),
            themeColor(Theme.unlock_status_text_shadow_color),
            themeColor(Theme.ascii_status_text_shadow_color)
        )
        val otherOutlineColors = arrayOf<String?>(
            themeColor(Theme.input_text_shadow_color),
            themeColor(Theme.output_text_shadow_color),
        )
        outlineColors = arrayOfNulls<String>(statusLineOutlineColors.size + otherOutlineColors.size)
        System.arraycopy(statusLineOutlineColors, 0, outlineColors, 0, statusLineOutlineColors.size)
        System.arraycopy(otherOutlineColors, 0, outlineColors, 10, otherOutlineColors.size)

        val shadowParams =
            XMLPrefsManager.getListOfStringValues(XMLPrefsManager.get(Ui.shadow_params), 3, "0")
        shadowXOffset = shadowParams[0]!!.toInt()
        shadowYOffset = shadowParams[1]!!.toInt()
        shadowRadius = shadowParams[2]!!.toFloat()

        genericBorderCornerRadius = Tuils.dpToPx(mContext, dashedBorderCornerRadius())

        useDashed = dashedBorders()

        margins = Array<IntArray?>(6) { IntArray(4) }
        margins[0] =
            XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(Ui.status_lines_margins), 4, 0)
        margins[1] =
            XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(Ui.output_field_margins), 4, 0)
        margins[2] =
            XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(Ui.input_area_margins), 4, 0)
        margins[3] =
            XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(Ui.input_field_margins), 4, 0)
        margins[4] =
            XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(Ui.toolbar_margins), 4, 0)
        margins[5] = XMLPrefsManager.getListOfIntValues(
            XMLPrefsManager.get(Ui.suggestions_area_margin),
            4,
            0
        )

        val sequence = AllowEqualsSequence(indexes, Label.entries.toTypedArray())

        if (show[Label.ascii.ordinal]) {
            asciiColor = XMLPrefsManager.getColor(Theme.ascii_text_color)
            val asciiFile = File(Tuils.getFolder(), "ascii.txt")
            if (!asciiFile.exists()) {
                try {
                    asciiFile.createNewFile()
                    val sample = " ____  _____  _____  _   _ ___ \n" +
                            "|  _ \\| ____||_   _|| | | |_ _|\n" +
                            "| |_) |  _|    | |  | | | || | \n" +
                            "|  _ <| |___   | |  | |_| || | \n" +
                            "|_| \\_\\_____|  |_|   \\___/|___|\n"
                    val fos = FileOutputStream(asciiFile)
                    fos.write(sample.toByteArray())
                    fos.close()
                } catch (e: Exception) {
                    Log.e("TUI-UI", "Error creating ascii.txt", e)
                }
            }

            try {
                if (asciiFile.exists()) {
                    val fis = FileInputStream(asciiFile)
                    val data = ByteArray(asciiFile.length().toInt())
                    fis.read(data)
                    fis.close()
                    asciiContent = Tuils.NEWLINE + String(data, charset("UTF-8"))
                } else {
                    asciiContent = "ascii.txt not found after creation attempt"
                }
            } catch (e: Exception) {
                asciiContent = "Error loading ascii.txt: " + e.message
                Log.e("TUI-UI", "Error loading ascii.txt", e)
            }

            updateText(
                Label.ascii,
                Tuils.span(mContext, asciiContent, asciiColor, labelSizes[Label.ascii.ordinal])
            )
            val asciiView = getLabelView(Label.ascii)
            if (asciiView != null) {
                asciiView.setTypeface(Typeface.MONOSPACE)
            }
        }

        val lViewsParent = labelViews[0]!!.getParent() as LinearLayout

        var effectiveCount = 0
        for (count in labelViews.indices) {
            labelViews[count]!!.setOnTouchListener(this)

            val os = sequence.get(count)

            //            views on the same line
            for (j in os.indices) {
//                i is the object gave to the constructor
                val i = (os[j] as Label).ordinal
                //                v is the adjusted index (2.0, 2.1, 2.2, ...)
                val v = count.toFloat() + (j.toFloat() * 0.1f)

                labelIndexes[i] = v
            }

            if (count >= sequence.getMinKey() && count <= sequence.getMaxKey() && os.size > 0) {
                labelViews[count]!!.setTypeface(Tuils.getTypeface(context))

                val ec = effectiveCount++

                //                -1 = left     0 = center     1 = right
                val p = statusLineAlignments[ec]
                if (p >= 0) labelViews[count]!!.setGravity(if (p == 0) Gravity.CENTER_HORIZONTAL else Gravity.RIGHT)

                if (count.toFloat() != labelIndexes[Label.notes.ordinal]) {
                    labelViews[count]!!.setVerticalScrollBarEnabled(false)
                }

                Companion.applyBgRect(
                    mContext!!,
                    labelViews[count]!!,
                    bgColors[count],
                    margins[0]!!,
                    Tuils.dpToPx(mContext, moduleCornerRadius()),
                    useDashed,
                    terminalBorderColor(),
                    false
                )
                Companion.applyShadow(
                    labelViews[count]!!,
                    outlineColors[count]!!,
                    shadowXOffset,
                    shadowYOffset,
                    shadowRadius
                )
            } else {
                lViewsParent.removeView(labelViews[count])
                labelViews[count] = null
            }
        }

        if (show[Label.ram.ordinal]) {
            ramManager = RamManager(
                mContext!!,
                RAM_DELAY.toLong(),
                labelSizes[Label.ram.ordinal],
                statusUpdateListener
            )
            ramManager!!.start()
        }

        if (show[Label.storage.ordinal]) {
            storageManager = StorageManager(
                mContext!!,
                STORAGE_DELAY.toLong(),
                labelSizes[Label.storage.ordinal],
                statusUpdateListener
            )
            storageManager!!.start()
        }

        if (show[Label.device.ordinal]) {
            val USERNAME = Pattern.compile("%u", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
            val DV = Pattern.compile("%d", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

            var deviceFormat = XMLPrefsManager.get(Behavior.device_format)

            val username = XMLPrefsManager.get(Ui.username)
            var deviceName = XMLPrefsManager.get(Ui.deviceName)
            if (deviceName == null || deviceName.length == 0) {
                deviceName = Build.DEVICE
            }

            deviceFormat = USERNAME.matcher(deviceFormat)
                .replaceAll(Matcher.quoteReplacement(if (username != null) username else "null"))
            deviceFormat = DV.matcher(deviceFormat).replaceAll(Matcher.quoteReplacement(deviceName))
            deviceFormat = Tuils.patternNewline.matcher(deviceFormat)
                .replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))

            updateText(
                Label.device, Tuils.span(
                    mContext, deviceFormat, XMLPrefsManager.getColor(
                        Theme.device_text_color
                    ), labelSizes[Label.device.ordinal]
                )
            )
        }

        if (show[Label.time.ordinal]) {
            tuiTimeManager = TimeManager(
                mContext!!,
                TIME_DELAY.toLong(),
                labelSizes[Label.time.ordinal],
                statusUpdateListener
            )
            tuiTimeManager!!.start()
        }

        if (show[Label.battery.ordinal]) {
            mediumPercentage = XMLPrefsManager.getInt(Behavior.battery_medium)
            lowPercentage = XMLPrefsManager.getInt(Behavior.battery_low)

            batteryManager = BatteryManager(
                mContext!!,
                labelSizes[Label.battery.ordinal],
                mediumPercentage,
                lowPercentage,
                statusUpdateListener
            )
            batteryManager!!.start()
        }

        if (show[Label.network.ordinal]) {
            networkManager = NetworkManager(
                mContext!!,
                3000,
                labelSizes[Label.network.ordinal],
                statusUpdateListener
            )
            networkManager!!.start()
        }

        val notesView = getLabelView(Label.notes)
        notesManager = ohi.andre.consolelauncher.managers.NotesManager(context, notesView)
        if (show[Label.notes.ordinal]) {
            tuiNotesManager = NotesManager(
                mContext!!,
                2000,
                labelSizes[Label.notes.ordinal],
                notesManager,
                statusUpdateListener
            )
            tuiNotesManager!!.start()

            notesView!!.setMovementMethod(LinkMovementMethod())

            notesMaxLines = XMLPrefsManager.getInt(Ui.notes_max_lines)
            if (notesMaxLines > 0) {
                notesView.setMaxLines(notesMaxLines)
                notesView.setEllipsize(TextUtils.TruncateAt.MARQUEE)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && XMLPrefsManager.getBoolean(
                        Ui.show_scroll_notes_message
                    )
                ) {
                    notesView.getViewTreeObserver()
                        .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                            var linesBefore: Int = Int.Companion.MIN_VALUE

                            override fun onGlobalLayout() {
                                if (notesView.getLineCount() > notesMaxLines && linesBefore <= notesMaxLines) {
                                    Tuils.sendOutput(Color.RED, context, R.string.note_max_reached)
                                }

                                linesBefore = notesView.getLineCount()
                            }
                        })
                }
            }
        }

        if (show[Label.weather.ordinal]) {
            weatherColor = XMLPrefsManager.getColor(Theme.weather_text_color)
            weatherDelay = XMLPrefsManager.getInt(Behavior.weather_update_time) * 1000

            weatherManager = WeatherManager(
                mContext!!,
                weatherDelay.toLong(),
                labelSizes[Label.weather.ordinal],
                statusUpdateListener
            )
            weatherManager!!.start()

            showWeatherUpdate = XMLPrefsManager.getBoolean(Behavior.show_weather_updates)
        }

        if (show[Label.ascii.ordinal]) {
            val asciiFile = File(Tuils.getFolder(), "ascii.txt")
            if (!asciiFile.exists()) {
                try {
                    Tuils.write(
                        asciiFile, "  _____         _______     _    _ _____ \n" +
                                " |  __ \\       |__   __|   | |  | |_   _|\n" +
                                " | |__) | ___     | |______| |  | | | |  \n" +
                                " |  _  / / _ \\    | |______| |  | | | |  \n" +
                                " | | \\ \\|  __/    | |      | |__| |_| |_ \n" +
                                " |_|  \\_\\\\___|    |_|       \\____/|_____|\n"
                    )
                } catch (e: Exception) {
                    Log.e("TUI-UI", "Error creating ascii.txt", e)
                }
            }

            try {
                asciiContent = Tuils.NEWLINE + Tuils.inputStreamToString(FileInputStream(asciiFile))
                asciiColor = XMLPrefsManager.getColor(Theme.ascii_text_color)

                updateText(
                    Label.ascii,
                    Tuils.span(mContext, asciiContent, asciiColor, labelSizes[Label.ascii.ordinal])
                )

                val asciiView = getLabelView(Label.ascii)
                if (asciiView != null) {
                    asciiView.setTypeface(Typeface.MONOSPACE)
                }
            } catch (e: Exception) {
                Log.e("TUI-UI", "Error loading ascii.txt", e)
            }
        }

        if (show[Label.unlock.ordinal]) {
            unlockManager =
                UnlockManager(mContext!!, labelSizes[Label.unlock.ordinal], statusUpdateListener)
            unlockManager!!.start()
        }

        // Setup ViewPager2
        viewPager = mRootView.findViewById<ViewPager2>(R.id.view_pager)
        viewPager.setAdapter(PagerAdapter())
        viewPager.setOffscreenPageLimit(1)
        viewPager.setUserInputEnabled(false)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == TERMUX_WORKSPACE_PAGE_INDEX) {
                    setTermuxWorkspaceChromeActive(true)
                    openTermuxWorkspacePage(false)
                } else {
                    setTermuxWorkspaceChromeActive(false)
                }
            }
        })
        setupTerminalPage(mRootView)
        applyResponsiveLandscapeLayout(mContext!!.getResources().getConfiguration())

        styleClockOverlay(rootView)
        installCrtOverlay(rootView)

        var drawTimes = XMLPrefsManager.getInt(Ui.text_redraw_times)
        if (drawTimes <= 0) drawTimes = 1
        OutlineTextView.redrawTimes = drawTimes

        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .registerReceiver(receiver, filter)
        if (showTerminal()) {
            val lbm = LocalBroadcastManager.getInstance(context.getApplicationContext())
            lbm.sendBroadcast(Intent(ACTION_REQUEST_NOTIFICATION_FEED))
            rootView.postDelayed(Runnable {
                lbm.sendBroadcast(
                    Intent(
                        ACTION_REQUEST_NOTIFICATION_FEED
                    )
                )
            }, 350)
            rootView.postDelayed(Runnable {
                lbm.sendBroadcast(
                    Intent(
                        ACTION_REQUEST_NOTIFICATION_FEED
                    )
                )
            }, 1100)
        }
        ClockManager.getInstance(context.getApplicationContext()).broadcastState()

        scheduleTypefaceRefreshes()
    }

    private fun installCrtOverlay(rootView: ViewGroup) {
        if (!AppearanceSettings.crtFilter()) {
            return
        }
        val overlay = CrtOverlayDrawable(rootView.getContext())
        overlay.setAccentColor(XMLPrefsManager.getColor(Theme.output_text_color))
        rootView.foreground = overlay
        crtOverlayDrawable = overlay
    }

    private fun styleMusicWidget(musicWidget: View?) {
        if (musicWidget == null) return
        decorateWidget(
            musicWidget,
            R.id.music_widget_border,
            R.id.music_widget_label,
            R.id.music_widget_close,
            musicWidgetBorderColor(),
            musicWidgetTextColor()
        )
        styleModuleClose(musicWidget.findViewById<TextView?>(R.id.music_widget_close))
        sizeMusicVisualizer(musicWidget)

        val titleView = musicWidget.findViewById<TextView?>(R.id.music_song_title)
        val singerView = musicWidget.findViewById<TextView?>(R.id.music_singer)
        val appPrefixView = musicWidget.findViewById<TextView?>(R.id.music_app_prefix)
        val appLabelView = musicWidget.findViewById<TextView?>(R.id.music_app_label)
        if (titleView != null) {
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, moduleBodyTextSize().toFloat())
            titleView.setIncludeFontPadding(true)
        }
        if (singerView != null) {
            singerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, moduleBodyTextSize().toFloat())
            singerView.setIncludeFontPadding(true)
        }
        if (appPrefixView != null) {
            appPrefixView.setTextColor(musicWidgetTextColor())
            appPrefixView.setTypeface(Tuils.getTypeface(mContext))
            appPrefixView.setTextSize(TypedValue.COMPLEX_UNIT_SP, moduleBodyTextSize().toFloat())
            appPrefixView.setIncludeFontPadding(true)
            appPrefixView.setSingleLine(true)
        }
        if (appLabelView != null) {
            appLabelView.setTextColor(musicWidgetTextColor())
            appLabelView.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            appLabelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, moduleBodyTextSize().toFloat())
            appLabelView.setIncludeFontPadding(true)
            appLabelView.setSingleLine(true)
            appLabelView.setEllipsize(TextUtils.TruncateAt.END)
            appLabelView.setOnClickListener(View.OnClickListener { v: View? -> launchCurrentMusicApp() })
        }
        updateMusicAppRow(musicWidget, musicWidgetTextColor())

        // Style control buttons
        val widgetColor = musicWidgetTextColor()
        val widgetBorderColor = moduleButtonBorderColor()
        val useDashed = dashedBorders()

        val prevBtn = musicWidget.findViewById<TextView?>(R.id.music_prev)
        val nextBtn = musicWidget.findViewById<TextView?>(R.id.music_next)
        val playPauseBtn = musicWidget.findViewById<TextView?>(R.id.music_play_pause)

        val buttons = arrayOf<View?>(prevBtn, nextBtn, playPauseBtn)
        for (b in buttons) {
            if (b is TextView) {
                val btn = b
                btn.setTextColor(widgetColor)
                btn.setTypeface(Tuils.getTypeface(mContext))
                btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, moduleBodyTextSize().toFloat())
                btn.setIncludeFontPadding(true)
                btn.setSingleLine(true)
                btn.setEllipsize(TextUtils.TruncateAt.END)

                btn.setBackgroundDrawable(
                    TerminalBorderRuntime.panelDrawable(
                        mContext!!,
                        moduleButtonBackgroundColor(),
                        widgetBorderColor,
                        1.2f,
                        moduleCornerRadius(),
                        useDashed
                    )
                )
            }
        }

        if (prevBtn != null) {
            prevBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if ("internal" == activeMusicSource) {
                    if (mainPack!!.player != null) mainPack!!.player!!.playPrev()
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_PREV_INT)
                    LocalBroadcastManager.getInstance(mContext!!).sendBroadcast(intent)
                }
            })
        }

        if (nextBtn != null) {
            nextBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if ("internal" == activeMusicSource) {
                    if (mainPack!!.player != null) mainPack!!.player!!.playNext()
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(MusicService.EXTRA_CONTROL_CMD, MusicService.CONTROL_NEXT_INT)
                    LocalBroadcastManager.getInstance(mContext!!).sendBroadcast(intent)
                }
            })
        }

        if (playPauseBtn != null) {
            playPauseBtn.setOnClickListener(View.OnClickListener { v: View? ->
                if ("internal" == activeMusicSource) {
                    if (mainPack!!.player != null) {
                        if (mainPack!!.player!!.isPlaying()) mainPack!!.player!!.pause()
                        else mainPack!!.player!!.play()
                    }
                } else {
                    val intent = Intent(MusicService.ACTION_MUSIC_CONTROL)
                    intent.putExtra(
                        MusicService.EXTRA_CONTROL_CMD,
                        MusicService.CONTROL_PLAY_PAUSE_INT
                    )
                    LocalBroadcastManager.getInstance(mContext!!).sendBroadcast(intent)
                }
            })
        }
    }

    private fun updateMusicAppRow(rootView: View?, textColor: Int) {
        if (rootView == null) return
        val row = rootView.findViewById<View?>(R.id.music_app_row)
        val prefix = rootView.findViewById<TextView?>(R.id.music_app_prefix)
        val label = rootView.findViewById<TextView?>(R.id.music_app_label)
        if (row == null || prefix == null || label == null) return

        val packageName = lastMusicAppPackage
        val launchIntent =
            if (!packageName.isNullOrEmpty()) mContext!!.getPackageManager()
                .getLaunchIntentForPackage(packageName)
            else null

        if (packageName.isNullOrEmpty() || launchIntent == null) {
            row.setVisibility(View.GONE)
            label.setText(Tuils.EMPTYSTRING)
            label.setOnClickListener(null)
            label.setEnabled(false)
            return
        }

        prefix.setTextColor(textColor)
        label.setTextColor(textColor)
        label.setText(resolveMusicAppLabel(packageName))
        label.setEnabled(true)
        label.setOnClickListener(View.OnClickListener { v: View? -> launchCurrentMusicApp() })
        row.setVisibility(View.VISIBLE)
    }

    private fun resolveMusicAppLabel(packageName: String?): String {
        val resolvedPackage = packageName ?: return Tuils.EMPTYSTRING
        if (TextUtils.isEmpty(resolvedPackage)) return Tuils.EMPTYSTRING
        return try {
            val packageManager = mContext!!.getPackageManager()
            val info = packageManager.getApplicationInfo(resolvedPackage, 0)
            val label = info.loadLabel(packageManager)
            if (label.length > 0) label.toString() else resolvedPackage
        } catch (e: Exception) {
            resolvedPackage
        }
    }

    private fun launchCurrentMusicApp() {
        val packageName = lastMusicAppPackage ?: return
        if (packageName.isEmpty()) return
        val intent = mContext!!.getPackageManager().getLaunchIntentForPackage(packageName)
            ?: return
        if (mContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAfterChromeHidden(intent)
    }

    private fun sizeMusicVisualizer(musicWidget: View) {
        val visualizer = musicWidget.findViewById<MusicVisualizerView?>(R.id.music_visualizer)
        val border = musicWidget.findViewById<View?>(R.id.music_widget_border)
        if (visualizer == null || border == null) return

        border.post(Runnable {
            val height = border.getHeight() - border.getPaddingTop() - border.getPaddingBottom()
            if (height > 0) {
                val params = visualizer.getLayoutParams()
                if (params != null && params.height != height) {
                    params.height = height
                    visualizer.setLayoutParams(params)
                }
            }
        })
    }

    private fun styleHackOverlay(rootView: View) {
        val overlay = rootView.findViewById<View?>(R.id.hack_overlay)
        val hackText = rootView.findViewById<TextView?>(R.id.hack_text)
        if (overlay == null || hackText == null) {
            return
        }
        hackOverlay = overlay
        hackOverlayBasePaddingLeft = overlay.getPaddingLeft()
        hackOverlayBasePaddingTop = overlay.getPaddingTop()
        hackOverlayBasePaddingRight = overlay.getPaddingRight()
        hackOverlayBasePaddingBottom = overlay.getPaddingBottom()

        val accent = moduleNameTextColor()
        val surface = ColorUtils.setAlphaComponent(terminalWindowBackground(), 238)
        val border = ColorUtils.setAlphaComponent(accent, 220)

        overlay.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                ColorUtils.setAlphaComponent(surface, 232),
                border,
                1.5f,
                0,
                dashedBorders()
            )
        )
        overlay.setOnClickListener(View.OnClickListener { v: View? -> dismissHackOverlay() })

        hackText.setTextColor(accent)
        hackText.setTypeface(Tuils.getTypeface(mContext))
        hackText.setTextSize(11f)
    }

    private fun styleTermuxConsole() {
        if (termuxOverlay == null) {
            return
        }

        val borderColor = terminalBorderColor()
        val textColor = notificationWidgetTextColor()
        val bgColor = terminalWindowBackground()
        val labelBg = terminalHeaderTabBackground()

        if (termuxWindowBorder != null) {
            termuxWindowBorder!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    bgColor,
                    borderColor,
                    1.5f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        }

        if (termuxWindowLabel != null) {
            termuxWindowLabel!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxWindowLabel!!.setTextSize(outputHeaderTextSize().toFloat())
            termuxWindowLabel!!.setTextColor(textColor)
            termuxWindowLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }

        if (termuxClose != null) {
            termuxClose!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxClose!!.setTextSize(outputHeaderTextSize().toFloat())
            termuxClose!!.setTextColor(textColor)
            termuxClose!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        TerminalBorderRuntime.bind(termuxWindowBorder, termuxWindowLabel, termuxClose)

        if (termuxOutput != null) {
            termuxOutput!!.setTypeface(Tuils.getTypeface(mContext))
            termuxOutput!!.setTextColor(textColor)
            termuxOutput!!.setTextIsSelectable(termuxAppSession == null)
            termuxOutput!!.setHorizontallyScrolling(termuxAppSession != null)
        }
        termuxGrid?.let { grid ->
            grid.setTerminalTypeface(Tuils.getTypeface(mContext))
            grid.setTerminalTextSizeSp(12f)
            grid.updateThemeColors(textColor, bgColor, borderColor)
        }

        if (termuxOutputPanel != null) {
            termuxOutputPanel!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    ColorUtils.blendARGB(bgColor, Color.BLACK, 0.1f),
                    ColorUtils.setAlphaComponent(borderColor, 210),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        }

        if (termuxOutputLabel != null) {
            termuxOutputLabel!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxOutputLabel!!.setTextSize(max(10f, outputHeaderTextSize().toFloat() - 2f))
            termuxOutputLabel!!.setTextColor(textColor)
            termuxOutputLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        TerminalBorderRuntime.bind(termuxOutputPanel, termuxOutputLabel)

        if (termuxPrefix != null) {
            termuxPrefix!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxPrefix!!.setTextColor(textColor)
        }

        if (termuxInput != null) {
            termuxInput!!.setTypeface(Tuils.getTypeface(mContext))
            termuxInput!!.setTextColor(textColor)
            termuxInput!!.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150))
        }

        if (termuxInputGroup != null) {
            termuxInputGroup!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    ColorUtils.blendARGB(bgColor, Color.BLACK, 0.16f),
                    ColorUtils.setAlphaComponent(borderColor, 180),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        }

        if (termuxTools != null) {
            termuxTools!!.setBackgroundColor(Color.TRANSPARENT)
            styleTermuxToolButtons(termuxTools, textColor)
        }
        updateTermuxConsoleLabels()
    }

    private fun updateTermuxConsoleLabels() {
        val app = termuxAppSession
        val luaId = luaAppId
        if (termuxWindowLabel != null) {
            termuxWindowLabel!!.text = if (luaId != null)
                luaAppTitle().uppercase(Locale.getDefault())
            else
                if (app == null) "TERMUX" else app.title.uppercase(Locale.getDefault())
        }
        if (termuxOutputLabel != null) {
            termuxOutputLabel!!.text = if (luaId != null) "APP" else if (app == null) "OUTPUT" else "SESSION"
        }
        if (termuxPrefix != null) {
            termuxPrefix!!.text = if (app == null && luaId == null) "\$" else ">"
        }
        if (termuxInput != null) {
            termuxInput!!.hint = if (app == null && luaId == null) "command" else "type input or :help"
        }
        updateTermuxConsoleKeyMode()
        updateTermuxAppActions()
    }

    private fun updateTermuxAppActions() {
        val scroll = termuxActionsScroll
        val row = termuxActions
        if (scroll == null || row == null) {
            return
        }

        row.removeAllViews()
        val luaId = luaAppId
        if (luaId != null) {
            val actions = luaAppSurfaceActions(luaAppLastResult)
            if (actions.isEmpty()) {
                scroll.visibility = View.GONE
                return
            }
            scroll.visibility = View.VISIBLE
            for (action in actions) {
                row.addView(overlayActionButton(action.label, action.run))
            }
            return
        }

        val app = termuxAppSession
        if (app == null || app.actions.isEmpty()) {
            scroll.visibility = View.GONE
            return
        }

        scroll.visibility = View.VISIBLE

        for (action in app.actions) {
            row.addView(overlayActionButton(action.label, Runnable {
                submitTermuxAppAction(action)
            }))
        }
    }

    private fun overlayActionButton(label: String?, action: Runnable): TextView {
        val textColor = notificationWidgetTextColor()
        val labelBg = terminalHeaderTabBackground()
        val margin = Tuils.dpToPx(mContext, 4)
        val minWidth = Tuils.dpToPx(mContext, 76)
        val button = TextView(mContext!!)
        button.text = (if (TextUtils.isEmpty(label)) "ACTION" else label!!).uppercase(Locale.getDefault())
        button.gravity = Gravity.CENTER
        button.maxLines = 1
        button.ellipsize = TextUtils.TruncateAt.END
        button.minWidth = minWidth
        button.setPadding(
            Tuils.dpToPx(mContext, 10),
            0,
            Tuils.dpToPx(mContext, 10),
            0
        )
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        button.setTextColor(textColor)
        button.setTextSize(10f)
        button.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        button.setOnClickListener(View.OnClickListener { action.run() })
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        params.setMarginEnd(margin)
        button.layoutParams = params
        return button
    }

    private fun styleTermuxToolButton(button: TextView?, color: Int) {
        if (button == null) {
            return
        }
        button.setBackgroundColor(Color.TRANSPARENT)
        button.setTextColor(color)
        button.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        button.setPadding(
            Tuils.dpToPx(mContext, 2), 0,
            Tuils.dpToPx(mContext, 2), 0
        )
    }

    private fun styleTermuxToolButtons(view: View?, color: Int) {
        if (view == null) {
            return
        }
        if (view is TextView) {
            styleTermuxToolButton(view, color)
            return
        }
        if (view is ViewGroup) {
            val group = view
            for (i in 0..<group.getChildCount()) {
                styleTermuxToolButtons(group.getChildAt(i), color)
            }
        }
    }

    private fun styleFileConsole() {
        if (fileOverlay == null) {
            return
        }

        val borderColor = terminalBorderColor()
        val textColor = notificationWidgetTextColor()
        val bgColor = terminalWindowBackground()
        val labelBg = terminalHeaderTabBackground()

        if (fileWindowBorder != null) {
            fileWindowBorder!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    bgColor,
                    borderColor,
                    1.5f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        }

        if (fileWindowLabel != null) {
            fileWindowLabel!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            fileWindowLabel!!.setTextSize(outputHeaderTextSize().toFloat())
            fileWindowLabel!!.setTextColor(textColor)
            fileWindowLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        if (fileClose != null) {
            fileClose!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            fileClose!!.setTextSize(outputHeaderTextSize().toFloat())
            fileClose!!.setTextColor(textColor)
            fileClose!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg))
        }
        TerminalBorderRuntime.bind(fileWindowBorder, fileWindowLabel, fileClose)
        if (filePath != null) {
            filePath!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            filePath!!.setTextColor(textColor)
        }
        if (fileOutput != null) {
            fileOutput!!.setTypeface(Tuils.getTypeface(mContext))
            fileOutput!!.setTextColor(textColor)
            fileOutput!!.setTextIsSelectable(true)
        }
        if (filePrefix != null) {
            filePrefix!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            filePrefix!!.setTextColor(textColor)
        }
        if (fileInput != null) {
            fileInput!!.setTypeface(Tuils.getTypeface(mContext))
            fileInput!!.setTextColor(textColor)
            fileInput!!.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 150))
        }
        if (fileInputGroup != null) {
            fileInputGroup!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    ColorUtils.blendARGB(bgColor, Color.BLACK, 0.16f),
                    ColorUtils.setAlphaComponent(borderColor, 180),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders()
                )
            )
        }
        if (fileTools != null) {
            fileTools!!.setBackgroundColor(Color.TRANSPARENT)
        }
        styleTermuxToolButton(fileRefresh, textColor)
        styleTermuxToolButton(fileUp, textColor)
        styleTermuxToolButton(fileOpen, textColor)
        styleTermuxToolButton(filePaste, textColor)
    }

    fun openTermuxConsole(command: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openTermuxConsole(command) }
            return
        }

        if (termuxOverlay == null) {
            return
        }

        closeFileConsole(false)
        closeLuaAppSession(true)
        termuxAppSession = null
        termuxAppLastStatus = null
        resetTermuxAppRuntimeState(true)
        resetTermuxAppCellViewport()
        styleTermuxConsole()
        termuxOverlay!!.setVisibility(View.VISIBLE)
        termuxOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()

        if (termuxBuffer.length == 0) {
            appendTermuxLine("Re:T-UI Termux console")
            appendTermuxLine("Type shell commands, help, status, open, run, clear, or exit.")
            appendTermuxLine("Non-interactive Termux commands run from here.")
        }

        var normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length > 0) {
            if (normalized.startsWith("-")) {
                normalized = normalized.substring(1)
            }
            executeTermuxConsoleCommand(normalized)
        }

        scheduleTermuxConsoleFocusCapture(true)
    }

    fun openLuaApp(appId: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openLuaApp(appId) }
            return
        }

        if (termuxOverlay == null) {
            return
        }
        val id = LuaWidgetManager.normalizeId(appId)
        if (TextUtils.isEmpty(id) || !LuaWidgetManager.exists(id)) {
            Toast.makeText(mContext, "Unknown Lua app: " + id, Toast.LENGTH_SHORT).show()
            return
        }
        if ("app" != LuaWidgetManager.getScriptType(id)) {
            Toast.makeText(mContext, "Script is not a Lua app: " + id, Toast.LENGTH_SHORT).show()
            return
        }

        closeFileConsole(false)
        closeLuaAppSession(true)
        termuxAppSession = null
        termuxAppLastStatus = null
        resetTermuxAppRuntimeState(true)
        resetTermuxAppCellViewport()

        luaAppId = id
        luaAppLastStatus = "opening"
        luaAppEngine = LuaWidgetEngine(
            mContext,
            id,
            LuaWidgetManager.readScript(id),
            LuaWidgetManager.version(id),
            UpdateListener { updatedWidgetId: String?, result: LuaWidgetEngine.RenderResult ->
                if (TextUtils.equals(LuaWidgetManager.normalizeId(updatedWidgetId), luaAppId)
                    && this.isTermuxConsoleVisible
                ) {
                    renderLuaAppResult(result, "updated")
                }
            })

        styleTermuxConsole()
        termuxOverlay!!.setVisibility(View.VISIBLE)
        termuxOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()
        renderLuaAppFrame(null, "opening " + luaAppTitle() + "...")

        val result = luaAppEngine!!.open()
        renderLuaAppResult(result, "open")
        scheduleTermuxConsoleFocusCapture(true)
    }

    fun openFileConsole(command: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openFileConsole(command) }
            return
        }

        if (fileOverlay == null) {
            return
        }

        closeTermuxConsole()
        styleFileConsole()
        fileOverlay!!.setVisibility(View.VISIBLE)
        fileOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()
        refreshFileConsole(true)

        val normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length > 0) {
            executeFileConsoleCommand(normalized)
        }

        if (fileInput != null) {
            sendRetuiKeyboardTheme(fileInput, "files")
            fileInput!!.requestFocus()
            fileInput!!.postDelayed(Runnable {
                val manager =
                    mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                if (manager != null) {
                    sendRetuiKeyboardTheme(fileInput, "files")
                    manager.showSoftInput(fileInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }, 120)
        }
    }

    private fun closeFileConsole(restoreSuggestions: Boolean = true) {
        if (fileOverlay != null) {
            fileOverlay!!.setVisibility(View.GONE)
        }
        if (restoreSuggestions) {
            restoreHomeSuggestionsAfterTermux()
        }
        if (fileInput != null) {
            val manager =
                mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                manager.hideSoftInputFromWindow(fileInput!!.getWindowToken(), 0)
            }
            fileInput!!.clearFocus()
        }
        if (mTerminalAdapter != null && restoreSuggestions) {
            mTerminalAdapter!!.focusInputEnd()
        }
    }

    private fun executeFileConsoleCommand(rawCommand: String?) {
        val command = if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (command.length == 0) {
            return
        }
        val lower = command.lowercase()

        if ("exit" == lower || "close" == lower) {
            closeFileConsole()
            return
        }
        if ("help" == lower) {
            renderFileConsole("Commands:\ncd [folder]\ncd ..\nls\npwd\nopen [file]\ntermux-open [file]\nshare [file]\nrefresh\nexit")
            return
        }
        if ("refresh" == lower || "reload" == lower || "ls" == lower) {
            refreshFileConsole(true)
            return
        }
        if ("pwd" == lower) {
            renderFileConsole(mainPack!!.currentDirectory.getAbsolutePath())
            return
        }

        if (mExecuter != null) {
            mExecuter.execute(command, null)
        }

        if (lower == "cd" || lower.startsWith("cd ")) {
            if (FileBackendManager.activeBackend(mContext!!) != FileBackendManager.Active.TERMUX) {
                refreshFileConsole(true)
            } else {
                renderFileConsole("Changing directory...")
            }
        } else if (lower.startsWith("open ") || lower.startsWith("termux-open ") || lower.startsWith(
                "share "
            )
        ) {
            renderFileConsole("Dispatched: " + command)
        } else {
            refreshFileConsole(true)
        }
    }

    private fun refreshFileConsole(forceTermuxRequest: Boolean) {
        if (fileOverlay == null || fileOverlay!!.getVisibility() != View.VISIBLE || mainPack == null || mainPack!!.currentDirectory == null) {
            return
        }

        val path = mainPack!!.currentDirectory.getAbsolutePath()
        if (filePath != null) {
            filePath!!.setText(path)
        }

        if (FileBackendManager.activeBackend(mContext!!) == FileBackendManager.Active.TERMUX) {
            val dirs: List<String?> = dirs(path)
            val files: List<String?> = files(path)
            if (dirs.isEmpty() && files.isEmpty()) {
                renderFileConsole("Loading " + path + "...")
            } else {
                renderFileConsole(buildFileListing(dirs, files, null))
            }
            requestFileConsoleTermuxListing(path, forceTermuxRequest)
            return
        }

        renderFileConsole(buildNativeFileListing(mainPack!!.currentDirectory))
    }

    private fun requestFileConsoleTermuxListing(path: String, force: Boolean) {
        if (force || shouldRequest("dirs", path)) {
            TermuxBridgeManager.dispatchShell(
                mContext!!,
                "fm-dirs " + path,
                tbridge.LIST_DIRS_SCRIPT,
                TermuxBridgeManager.TERMUX_HOME,
                path
            )
        }
        if (force || shouldRequest("files", path)) {
            TermuxBridgeManager.dispatchShell(
                mContext!!,
                "fm-files " + path,
                tbridge.LIST_FILES_SCRIPT,
                TermuxBridgeManager.TERMUX_HOME,
                path
            )
        }
    }

    private fun buildNativeFileListing(directory: File?): String {
        if (directory == null || !directory.exists()) {
            return "Path not found."
        }
        if (!directory.isDirectory()) {
            return directory.getName()
        }

        val children = directory.listFiles()
        if (children == null || children.size == 0) {
            return "[]"
        }
        Arrays.sort<File?>(
            children,
            Comparator { a: File?, b: File? ->
                a!!.getName().compareTo(b!!.getName(), ignoreCase = true)
            })
        val dirs: MutableList<String?> = ArrayList<String?>()
        val files: MutableList<String?> = ArrayList<String?>()
        for (child in children) {
            if (child.isDirectory()) {
                dirs.add(child.getName())
            } else {
                files.add(child.getName())
            }
        }
        return buildFileListing(dirs, files, null)
    }

    private fun buildFileListing(
        dirs: List<String?>,
        files: List<String?>,
        error: String?
    ): String {
        val out = StringBuilder()
        out.append("[..]")
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            out.append('\n').append("error: ").append(error.trim { it <= ' ' })
        }
        for (dir in dirs) {
            out.append('\n').append("[D] ").append(dir)
        }
        for (file in files) {
            out.append('\n').append("    ").append(file)
        }
        return out.toString()
    }

    private fun renderFileConsole(text: String?) {
        if (fileOutput != null) {
            fileOutput!!.setText(if (text == null) Tuils.EMPTYSTRING else text)
        }
        if (fileScroll != null) {
            fileScroll!!.post(Runnable { fileScroll!!.fullScroll(View.FOCUS_UP) })
        }
    }

    private fun closeTermuxConsole() {
        termuxFocusCapturePending = false
        if (termuxOverlay != null) {
            termuxOverlay!!.setVisibility(View.GONE)
        }
        closeLuaAppSession(true)
        termuxAppSession = null
        termuxAppLastStatus = null
        resetTermuxAppRuntimeState(true)
        resetTermuxAppCellViewport()
        updateTermuxConsoleLabels()
        restoreHomeSuggestionsAfterTermux()
        if (termuxInput != null) {
            val manager =
                mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                manager.hideSoftInputFromWindow(termuxInput!!.getWindowToken(), 0)
            }
            termuxInput!!.clearFocus()
        }
        if (mTerminalAdapter != null) {
            activateTerminalInput(false)
        }
    }

    private val isTermuxConsoleVisible: Boolean
        get() = termuxOverlay != null && termuxOverlay!!.getVisibility() == View.VISIBLE

    private fun takeTermuxConsoleFocus(showKeyboard: Boolean) {
        if (!this.isTermuxConsoleVisible) {
            return
        }
        if (termuxInput != null && termuxInput!!.hasFocus()) {
            if (showKeyboard) {
                focusTermuxInput(true)
            }
            return
        }
        releaseLauncherInputFocusForOverlay()
        if (termuxOverlay != null) {
            termuxOverlay!!.setFocusableInTouchMode(true)
        }
        focusTermuxInput(showKeyboard)
    }

    private fun scheduleTermuxConsoleFocusCapture(showKeyboard: Boolean) {
        if (!this.isTermuxConsoleVisible || termuxOverlay == null || termuxInput == null) {
            return
        }
        if (termuxInput!!.hasFocus()) {
            if (showKeyboard) {
                focusTermuxInput(true)
            }
            return
        }
        if (termuxFocusCapturePending) {
            return
        }
        termuxFocusCapturePending = true
        termuxOverlay!!.postDelayed(Runnable {
            termuxFocusCapturePending = false
            if (this.isTermuxConsoleVisible && termuxInput != null && !termuxInput!!.hasFocus()) {
                takeTermuxConsoleFocus(showKeyboard)
            }
        }, TERMUX_FOCUS_CAPTURE_DELAY_MS.toLong())
    }

    private fun releaseLauncherInputFocusForOverlay() {
        if (mTerminalAdapter == null) {
            return
        }
        val launcherInput = mTerminalAdapter!!.inputView
        if (launcherInput == null) {
            return
        }
        if (launcherInput is EditText) {
            val terminalInput = launcherInput
            terminalInput.setCursorVisible(false)
            terminalInput.setShowSoftInputOnFocus(false)
            if (terminalInput is OutlineEditText) {
                terminalInput.setIdleCursorVisible(true)
            }
        }
        launcherInput.clearFocus()
    }

    private fun handleTermuxBackPressed(): Boolean {
        if (!this.isTermuxConsoleVisible) {
            return false
        }
        if (keyboardVisible) {
            hideTermuxKeyboard()
            return true
        }
        closeTermuxConsole()
        return true
    }

    fun consumeBackPressed(): Boolean {
        return handleTermuxWorkspaceBackPressed() || handleTermuxBackPressed()
    }

    private fun focusTermuxInput(showKeyboard: Boolean) {
        if (termuxInput == null) {
            return
        }
        val hadFocus = termuxInput!!.hasFocus()
        termuxInput!!.setFocusableInTouchMode(true)
        termuxInput!!.setShowSoftInputOnFocus(true)
        termuxInput!!.setCursorVisible(true)
        sendRetuiKeyboardTheme(termuxInput, "termux")
        if (!hadFocus) {
            termuxInput!!.requestFocusFromTouch()
            termuxInput!!.requestFocus()
        }
        if (!showKeyboard) {
            return
        }
        val immediateManager =
            mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (immediateManager != null && !hadFocus) {
            immediateManager.restartInput(termuxInput)
        }
        termuxInput!!.post(Runnable {
            val manager =
                mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                sendRetuiKeyboardTheme(termuxInput, "termux")
                manager.showSoftInput(termuxInput, InputMethodManager.SHOW_IMPLICIT)
            }
        })
        termuxInput!!.postDelayed(Runnable {
            if (this.isTermuxConsoleVisible && termuxInput!!.hasFocus()) {
                val manager =
                    mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                if (manager != null) {
                    sendRetuiKeyboardTheme(termuxInput, "termux")
                    manager.showSoftInput(termuxInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }, 160)
    }

    private fun hideTermuxKeyboard() {
        if (termuxInput == null) {
            return
        }
        val manager =
            mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        if (manager != null) {
            manager.hideSoftInputFromWindow(termuxInput!!.getWindowToken(), 0)
        }
    }

    private fun toggleTermuxKeyboard() {
        if (keyboardVisible) {
            hideTermuxKeyboard()
        } else {
            focusTermuxInput(true)
        }
    }

    private fun insertIntoTermuxInput(text: String?) {
        if (termuxInput == null || text == null) {
            return
        }
        focusTermuxInput(false)
        val start = max(termuxInput!!.getSelectionStart(), 0)
        val end = max(termuxInput!!.getSelectionEnd(), 0)
        val left = min(start, end)
        val right = max(start, end)
        termuxInput!!.getText().replace(left, right, text)
    }

    private fun moveTermuxInputCursorBy(delta: Int) {
        if (termuxInput == null) {
            return
        }
        focusTermuxInput(false)
        val length = if (termuxInput!!.getText() == null) 0 else termuxInput!!.getText().length
        val current = max(0, termuxInput!!.getSelectionStart())
        termuxInput!!.setSelection(max(0, min(length, current + delta)))
    }

    private fun moveTermuxInputCursorToBoundary(start: Boolean) {
        if (termuxInput == null) {
            return
        }
        focusTermuxInput(false)
        val length = if (termuxInput!!.getText() == null) 0 else termuxInput!!.getText().length
        termuxInput!!.setSelection(if (start) 0 else length)
    }

    private fun handleTermuxEscapeKey() {
        if (termuxInput == null) {
            return
        }
        if (termuxInput!!.getText() != null && termuxInput!!.getText().length > 0) {
            termuxInput!!.setText(Tuils.EMPTYSTRING)
            return
        }
        hideTermuxKeyboard()
    }

    private fun interruptTermuxInput() {
        if (termuxInput == null) {
            return
        }
        if (termuxInput!!.getText() != null && termuxInput!!.getText().length > 0) {
            termuxInput!!.setText(Tuils.EMPTYSTRING)
            return
        }
        if (termuxAppSession != null) {
            val app = termuxAppSession!!
            termuxAppLastStatus = "sent interrupt"
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("interrupt", buildTermuxAppControlScript(app, "C-c"), true)
            scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
            return
        }
        appendTermuxLine("^C")
    }

    private fun scrollTermuxOutput(direction: Int) {
        if (termuxScroll == null) {
            return
        }
        val amount = Tuils.dpToPx(mContext, 220)
        termuxScroll!!.smoothScrollBy(0, if (direction < 0) -amount else amount)
    }

    private fun rememberTermuxCommand(command: String?) {
        val normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length == 0) {
            return
        }
        if (termuxCommandHistory.isEmpty()
            || normalized != termuxCommandHistory.get(termuxCommandHistory.size - 1)
        ) {
            termuxCommandHistory.add(normalized)
        }
        termuxHistoryCursor = termuxCommandHistory.size
        termuxHistoryDraft = Tuils.EMPTYSTRING
    }

    private fun recallTermuxHistory(direction: Int) {
        if (termuxInput == null || termuxCommandHistory.isEmpty()) {
            return
        }
        focusTermuxInput(false)
        if (termuxHistoryCursor < 0 || termuxHistoryCursor > termuxCommandHistory.size) {
            termuxHistoryCursor = termuxCommandHistory.size
        }
        if (termuxHistoryCursor == termuxCommandHistory.size) {
            termuxHistoryDraft = if (termuxInput!!.getText() == null)
                Tuils.EMPTYSTRING
            else
                termuxInput!!.getText().toString()
        }

        var nextCursor = termuxHistoryCursor + (if (direction < 0) -1 else 1)
        if (nextCursor < 0) {
            nextCursor = 0
        }
        if (nextCursor > termuxCommandHistory.size) {
            nextCursor = termuxCommandHistory.size
        }
        termuxHistoryCursor = nextCursor

        val value = if (termuxHistoryCursor == termuxCommandHistory.size)
            termuxHistoryDraft
        else
            termuxCommandHistory.get(termuxHistoryCursor)
        termuxInput!!.setText(value)
        termuxInput!!.setSelection(termuxInput!!.getText().length)
    }

    private fun hideHomeSuggestionsForTermux() {
        hideLauncherChromeForSurface()
        if (termuxConsoleOpen) {
            return
        }
        termuxConsoleOpen = true
        if (suggestionsContainer != null) {
            suggestionsVisibilityBeforeTermux = suggestionsContainer!!.getVisibility()
            suggestionsContainer!!.setVisibility(View.GONE)
        }
    }

    private fun restoreHomeSuggestionsAfterTermux() {
        if (!termuxConsoleOpen) {
            restoreLauncherChromeAfterSurface()
            return
        }
        termuxConsoleOpen = false
        if (suggestionsContainer != null) {
            suggestionsContainer!!.setVisibility(suggestionsVisibilityBeforeTermux)
        }
        restoreLauncherChromeAfterSurface()
    }

    private fun hideLauncherChromeForSurface() {
        if (launcherChromeHiddenForSurface) {
            return
        }
        launcherChromeHiddenForSurface = true

        if (mainContainer != null) {
            launcherChromeMainVisibility = mainContainer!!.getVisibility()
            mainContainer!!.setVisibility(View.GONE)
        }
        if (terminalTrayContainer != null) {
            launcherChromeTrayVisibility = terminalTrayContainer!!.getVisibility()
            terminalTrayContainer!!.setVisibility(View.GONE)
        }
        if (landscapeSplitContainer != null) {
            launcherChromeLandscapeVisibility = landscapeSplitContainer!!.getVisibility()
            landscapeSplitContainer!!.setVisibility(View.GONE)
        }
    }

    private fun restoreLauncherChromeAfterSurface() {
        if (!launcherChromeHiddenForSurface) {
            return
        }
        launcherChromeHiddenForSurface = false

        if (mainContainer != null) {
            mainContainer!!.setVisibility(launcherChromeMainVisibility)
        }
        if (terminalTrayContainer != null) {
            terminalTrayContainer!!.setVisibility(launcherChromeTrayVisibility)
        }
        if (landscapeSplitContainer != null) {
            landscapeSplitContainer!!.setVisibility(launcherChromeLandscapeVisibility)
        }
    }

    private fun submitTermuxConsoleCommand(rawCommand: String?) {
        if (luaAppId != null) {
            submitLuaAppInput(rawCommand)
            if (this.isTermuxConsoleVisible) {
                scheduleTermuxConsoleFocusCapture(true)
            }
            return
        }
        if (termuxAppSession != null) {
            submitTermuxAppInput(rawCommand)
            if (this.isTermuxConsoleVisible) {
                scheduleTermuxConsoleFocusCapture(true)
            }
            return
        }

        val normalized = normalizeTermuxConsoleCommand(
            if (rawCommand == null)
                Tuils.EMPTYSTRING
            else
                rawCommand.trim { it <= ' ' })
        executeTermuxConsoleCommand(rawCommand)
        if (!this.isTermuxConsoleVisible) {
            return
        }
        if ("open" == normalized.lowercase()) {
            return
        }
        scheduleTermuxConsoleFocusCapture(true)
    }

    private fun submitLuaAppInput(rawCommand: String?) {
        val command = if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (command.startsWith(":")) {
            handleLuaAppLocalCommand(command.substring(1).trim { it <= ' ' }.lowercase(Locale.getDefault()))
            return
        }
        val engine = luaAppEngine ?: return
        luaAppLastStatus = if (command.length == 0) "sent enter" else "input: " + command
        renderLuaAppResult(engine.input(command), luaAppLastStatus)
    }

    private fun handleLuaAppLocalCommand(command: String) {
        val id = luaAppId ?: return
        if ("help" == command || command.length == 0) {
            renderLuaAppFrame(
                luaAppLastResult,
                ":help, :refresh, :restart, :config, :edit, :clear, :close. Other input is sent to the app."
            )
        } else if ("refresh" == command || "r" == command) {
            renderLuaAppResult(luaAppEngine!!.render(true), "refreshed")
        } else if ("restart" == command || "reload" == command) {
            luaAppEngine = LuaWidgetEngine(
                mContext,
                id,
                LuaWidgetManager.readScript(id),
                LuaWidgetManager.version(id),
                UpdateListener { updatedWidgetId: String?, result: LuaWidgetEngine.RenderResult ->
                    if (TextUtils.equals(LuaWidgetManager.normalizeId(updatedWidgetId), luaAppId)
                        && this.isTermuxConsoleVisible
                    ) {
                        renderLuaAppResult(result, "updated")
                    }
                })
            renderLuaAppResult(luaAppEngine!!.open(), "restarted")
        } else if ("config" == command || "prefs" == command) {
            if (LuaWidgetManager.hasConfig(id)) {
                executeLuaWidgetCommand("lua -config " + id)
            } else {
                renderLuaAppFrame(luaAppLastResult, "No config surface: " + luaAppTitle())
            }
        } else if ("edit" == command) {
            executeLuaWidgetCommand("lua -edit " + id)
        } else if ("clear" == command) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else if ("close" == command || "exit" == command || "detach" == command) {
            closeTermuxConsole()
        } else {
            renderLuaAppFrame(luaAppLastResult, "Unknown app command: :" + command)
        }
    }

    private fun renderLuaAppResult(result: LuaWidgetEngine.RenderResult?, status: String?) {
        luaAppLastResult = result
        luaAppLastStatus = status
        renderLuaAppFrame(result, status)
        updateTermuxAppActions()
        scheduleLuaAppTickIfNeeded(result)
    }

    private fun renderLuaAppFrame(result: LuaWidgetEngine.RenderResult?, status: String?) {
        val id = luaAppId ?: return
        if (result != null
            && TextUtils.isEmpty(result.error)
            && !TextUtils.isEmpty(result.layoutJson)
            && renderLuaAppRichFrame(id, result, status)
        ) {
            return
        }
        showPlainTermuxOutput()
        val out = StringBuilder()
        out.append("Re:T-UI Lua app: ").append(luaAppTitle()).append('\n')
        out.append("script: ").append(id).append('\n')
        out.append("local commands: :help :refresh :restart :config :edit :clear :close").append('\n')
        if (!TextUtils.isEmpty(status)) {
            out.append("status: ").append(status).append('\n')
        }
        out.append("----")
        if (result == null) {
            out.append("\nLoading...")
        } else if (!TextUtils.isEmpty(result.error)) {
            out.append("\nLua error: ").append(result.error)
            if (!TextUtils.isEmpty(result.errorStage)) {
                out.append("\nStage: ").append(result.errorStage)
            }
        } else if (!TextUtils.isEmpty(result.body)) {
            out.append('\n').append(result.body!!.trimEnd { it <= ' ' })
        } else if (!TextUtils.isEmpty(result.layoutJson)) {
            out.append("\nLayout output is available in module panels; use ui:show_text for app body text.")
        } else {
            out.append("\nNo Lua app output yet.")
        }
        termuxBuffer.setLength(0)
        termuxBuffer.append(out.toString().trimEnd { it <= ' ' })
        updateTermuxOutput()
    }

    private fun renderLuaAppRichFrame(
        id: String,
        result: LuaWidgetEngine.RenderResult,
        status: String?
    ): Boolean {
        val container = termuxRichOutput ?: return false
        showRichTermuxOutput()
        termuxBuffer.setLength(0)
        if (termuxOutput != null) {
            termuxOutput!!.text = Tuils.EMPTYSTRING
        }

        addLuaText(container, "Re:T-UI Lua app: " + luaAppTitle(), id, MODULE_TEXT_FONT_MONO)
        addLuaText(container, "script: " + id, id, MODULE_TEXT_FONT_MONO)
        addLuaText(
            container,
            "local commands: :help :refresh :restart :config :edit :clear :close",
            id,
            MODULE_TEXT_FONT_MONO
        )
        if (!TextUtils.isEmpty(status)) {
            addLuaText(container, "status: " + status, id, MODULE_TEXT_FONT_MONO)
        }
        addLuaText(container, "----------------", id, MODULE_TEXT_FONT_MONO)

        renderLuaLayout(container, id, result.layoutJson, true)
        if (!TextUtils.isEmpty(result.body)) {
            val spacer = View(mContext)
            spacer.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Tuils.dpToPx(mContext, 8)
                )
            )
            container.addView(spacer)
            addLuaBodyText(container, result.body, id)
        }
        scrollTermuxOutputToTop(this.isTermuxConsoleVisible && termuxInput != null && termuxInput!!.hasFocus())
        return true
    }

    private fun luaAppTitle(): String {
        val result = luaAppLastResult
        if (result != null && !TextUtils.isEmpty(result.title)) {
            return result.title!!
        }
        val id = luaAppId
        return if (TextUtils.isEmpty(id)) "Lua App" else LuaWidgetManager.getName(id) ?: "Lua App"
    }

    private fun luaAppSurfaceActions(result: LuaWidgetEngine.RenderResult?): MutableList<LuaSurfaceAction> {
        val actions = ArrayList<LuaSurfaceAction>()
        if (result == null || !TextUtils.isEmpty(result.error)) {
            return actions
        }
        var index = 1
        for (button in result.buttons) {
            val actionIndex = index
            if (!TextUtils.isEmpty(button)) {
                actions.add(LuaSurfaceAction(button!!) { clickLuaApp(actionIndex) })
            }
            index += 1
        }
        for (action in result.valueActions) {
            if (action == null || TextUtils.isEmpty(action.label)) continue
            actions.add(LuaSurfaceAction(action.label!!) { actionLuaApp(action.value) })
        }
        if (result.dialogOpen) {
            var dialogIndex = 1
            for (item in result.dialogItems) {
                val choiceIndex = dialogIndex
                if (!TextUtils.isEmpty(item)) {
                    val label = if (choiceIndex == result.dialogSelected) "* " + item else item
                    actions.add(LuaSurfaceAction(label!!) { dialogLuaApp(choiceIndex) })
                }
                dialogIndex += 1
            }
            actions.add(LuaSurfaceAction("cancel") { dialogLuaApp(-1) })
        }
        for (action in result.commands) {
            if (action == null || TextUtils.isEmpty(action.label) || TextUtils.isEmpty(action.command)) {
                continue
            }
            actions.add(LuaSurfaceAction(action.label!!) { executeLuaWidgetCommand(action.command) })
        }
        if (result.expandable) {
            val expanded = result.expanded
            actions.add(
                LuaSurfaceAction(if (expanded) "collapse" else "expand") {
                    setLuaAppExpanded(!expanded)
                }
            )
        }
        return actions
    }

    private fun clickLuaApp(index: Int) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.click(index), "button " + index)
        restoreLuaAppInputFocusSoon()
    }

    private fun actionLuaApp(value: String?) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.action(value), if (TextUtils.isEmpty(value)) "action" else "action: " + value)
        restoreLuaAppInputFocusSoon()
    }

    private fun dialogLuaApp(index: Int) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.dialog(index), if (index < 0) "dialog canceled" else "dialog: " + index)
        restoreLuaAppInputFocusSoon()
    }

    private fun setLuaAppExpanded(expanded: Boolean) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.setExpanded(expanded), if (expanded) "expanded" else "collapsed")
        restoreLuaAppInputFocusSoon()
    }

    private fun restoreLuaAppInputFocusSoon() {
        if (luaAppId != null && this.isTermuxConsoleVisible) {
            scheduleTermuxConsoleFocusCapture(true)
        }
    }

    private fun scheduleLuaAppTickIfNeeded(result: LuaWidgetEngine.RenderResult?) {
        luaAppTickGeneration++
        val interval = result?.tickIntervalMs ?: -1L
        if (interval <= 0L) {
            return
        }
        val generation = luaAppTickGeneration
        val id = luaAppId
        handler?.postDelayed(Runnable {
            if (generation == luaAppTickGeneration
                && id == luaAppId
                && this.isTermuxConsoleVisible
            ) {
                val engine = luaAppEngine ?: return@Runnable
                renderLuaAppResult(engine.tick(), "tick")
            }
        }, interval)
    }

    private fun closeLuaAppSession(notifyScript: Boolean) {
        val engine = luaAppEngine
        if (notifyScript && engine != null) {
            try {
                engine.close()
            } catch (e: Exception) {
                Tuils.log(e)
            }
        }
        luaAppTickGeneration++
        luaAppId = null
        luaAppEngine = null
        luaAppLastResult = null
        luaAppLastStatus = null
    }

    private fun resetTermuxAppRuntimeState(clearFrame: Boolean) {
        termuxAppRefreshGeneration++
        termuxAppDispatchSequence = 0
        termuxAppAcceptedSequence = 0
        termuxAppWatchUntilMs = 0L
        clearTermuxAppModifiers(false)
        termuxSuppressInputWatcher = false
        termuxInsertedStart = -1
        termuxInsertedText = null
        termuxFnKeyMode = false
        termuxKeyModeAnimating = false
        if (clearFrame) {
            termuxAppLastFrameText = null
            termuxAppMeasuredLineHeight = 0
        }
    }

    private fun executeTermuxConsoleCommand(rawCommand: String?) {
        val displayCommand =
            if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (displayCommand.length == 0) {
            return
        }

        rememberTermuxCommand(displayCommand)
        appendTermuxLine("$ " + displayCommand)
        val command = normalizeTermuxConsoleCommand(displayCommand)
        if (command.length == 0) {
            appendTermuxLine("Termux console is already open. Type help for available commands.")
            return
        }

        val lower = command.lowercase()
        if ("exit" == lower || "close" == lower) {
            appendTermuxLine("closing termux console.")
            closeTermuxConsole()
        } else if ("clear" == lower) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else if ("help" == lower) {
            appendTermuxLine("help")
            appendTermuxLine("pwd / ls / whoami -> run shell commands in Termux")
            appendTermuxLine("cd [dir] -> change the Termux console working directory")
            appendTermuxLine("status  -> check Termux bridge readiness")
            appendTermuxLine("setup   -> show Termux bridge setup checklist")
            appendTermuxLine("open    -> launch Termux for interactive programs")
            appendTermuxLine("run <script|alias> [args...] -> dispatch a Termux script")
            appendTermuxLine("apps    -> list Re:T-UI Termux apps")
            appendTermuxLine("app <id> -> open a persistent tmux-backed app session")
            appendTermuxLine("app-add/add-app <id> <command> -> register a custom Termux app")
            appendTermuxLine("app-info <id> -> inspect a registered Termux app")
            appendTermuxLine("app-sync <id> -> write its manifest into Termux")
            appendTermuxLine("app-actions <id> -> list static app action chips")
            appendTermuxLine("app-action <id> <label> [input] -> add an app action chip")
            appendTermuxLine("app-action-rm <id> <label> -> remove a custom app action")
            appendTermuxLine("app-rm/rm-app <id> -> remove a custom Termux app")
            appendTermuxLine("clear   -> clear this console")
            appendTermuxLine("exit    -> close this console")
        } else if ("status" == lower) {
            appendTermuxStatus()
        } else if ("setup" == lower) {
            appendTermuxSetup()
        } else if ("open" == lower) {
            openTermuxApp()
        } else if ("run" == lower || lower.startsWith("run ")) {
            runTermuxCommand(command)
        } else if ("apps" == lower || "app-ls" == lower || "app -ls" == lower) {
            appendTermuxApps()
        } else if ("app-info" == lower || lower.startsWith("app-info ")) {
            appendTermuxAppInfo(command)
        } else if ("app-sync" == lower || lower.startsWith("app-sync ")) {
            syncTermuxCustomApp(command)
        } else if ("app" == lower || lower.startsWith("app ")) {
            openTermuxCustomApp(command)
        } else if ("app-add" == lower || lower.startsWith("app-add ")
            || "add-app" == lower || lower.startsWith("add-app ")
        ) {
            addTermuxCustomApp(command)
        } else if ("app-actions" == lower || lower.startsWith("app-actions ")) {
            appendTermuxAppActions(command)
        } else if ("app-action-rm" == lower || lower.startsWith("app-action-rm ")) {
            removeTermuxAppAction(command)
        } else if ("app-action" == lower || lower.startsWith("app-action ")) {
            addTermuxAppAction(command)
        } else if ("app-rm" == lower || lower.startsWith("app-rm ")
            || "app-remove" == lower || lower.startsWith("app-remove ")
            || "rm-app" == lower || lower.startsWith("rm-app ")
            || "remove-app" == lower || lower.startsWith("remove-app ")
        ) {
            removeTermuxCustomApp(command)
        } else if ("cd" == lower || lower.startsWith("cd ")) {
            changeTermuxDirectory(command)
        } else {
            runTermuxShellCommand(command)
        }
    }

    private fun appendTermuxApps() {
        val apps = TermuxAppManager.list(mContext!!)
        if (apps.isEmpty()) {
            appendTermuxLine("No Termux apps registered.")
            return
        }
        appendTermuxLine("Termux apps")
        for (app in apps) {
            val actionInfo = if (app.actions.isEmpty()) "" else " [" + app.actions.size + " actions]"
            appendTermuxLine(app.id + " -> " + app.title + actionInfo)
            appendTermuxLine("  home: " + app.homeDir)
        }
        appendTermuxLine("Open with: app <id>")
    }

    private fun appendTermuxAppActions(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine("usage: app-actions <id>")
            return
        }
        val app = TermuxAppManager.resolve(mContext!!, parts.get(1))
        if (app == null) {
            appendTermuxLine("Unknown Termux app: " + parts.get(1))
            return
        }
        appendTermuxLine("Actions for " + app.id)
        if (app.actions.isEmpty()) {
            appendTermuxLine("No actions registered.")
        } else {
            for (action in app.actions) {
                val sendLabel = if (action.send.isEmpty()) "[enter]" else action.send
                appendTermuxLine(action.label + " -> " + sendLabel)
            }
        }
        appendTermuxLine("Add with: app-action " + app.id + " \"label\" \"input\"")
    }

    private fun appendTermuxAppInfo(command: String?) {
        val app = resolveTermuxAppFromCommand(command, "app-info <id>") ?: return
        appendTermuxLine("Termux app: " + app.id)
        appendTermuxLine("Title: " + app.title)
        appendTermuxLine("Command: " + app.command)
        appendTermuxLine("Workdir: " + app.workDir)
        appendTermuxLine("Home: " + app.homeDir)
        appendTermuxLine("Manifest: " + app.manifestPath)
        appendTermuxLine("State: " + app.statePath)
        appendTermuxLine("Memory: " + app.memoryDir)
        appendTermuxLine("Logs: " + app.logsDir)
        appendTermuxLine("Session: " + TermuxAppManager.tmuxSessionName(app.id))
        appendTermuxLine("Actions: " + app.actions.size)
    }

    private fun syncTermuxCustomApp(command: String?) {
        val app = resolveTermuxAppFromCommand(command, "app-sync <id>") ?: return
        if (syncTermuxAppManifest(app, true)) {
            appendTermuxLine("Manifest sync dispatched: " + app.manifestPath)
        }
    }

    private fun resolveTermuxAppFromCommand(command: String?, usage: String): TermuxAppManager.TermuxApp? {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine("usage: " + usage)
            return null
        }
        val app = TermuxAppManager.resolve(mContext!!, parts.get(1))
        if (app == null) {
            appendTermuxLine("Unknown Termux app: " + parts.get(1))
            return null
        }
        return app
    }

    private fun openTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine("usage: app <id>")
            appendTermuxApps()
            return
        }
        val app = TermuxAppManager.resolve(mContext!!, parts.get(1))
        if (app == null) {
            appendTermuxLine("Unknown Termux app: " + parts.get(1))
            appendTermuxLine("Register one with: app-add <id> <command>")
            return
        }
        openTermuxAppSession(app)
    }

    private fun addTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine("usage: app-add <id> <command>")
            appendTermuxLine("example: app-add radio bash ~/retui/radio.sh")
            return
        }
        val id = parts.get(1)
        val appCommand = Tuils.toPlanString(parts.subList(2, parts.size), Tuils.SPACE)
        if (TermuxAppManager.add(mContext!!, id, id, appCommand, termuxWorkingDirectory)) {
            val normalized = TermuxAppManager.normalizeId(id)
            val app = TermuxAppManager.resolve(mContext!!, normalized)
            if (app != null) {
                syncTermuxAppManifest(app, true)
            }
            appendTermuxLine("Termux app registered: " + normalized)
            appendTermuxLine("Open with: app " + normalized)
        } else {
            appendTermuxLine("Unable to register Termux app.")
        }
    }

    private fun addTermuxAppAction(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine("usage: app-action <id> <label> [input]")
            appendTermuxLine("example: app-action myapp \"show status\" 6")
            return
        }
        val id = parts.get(1)
        val label = parts.get(2)
        val send = if (parts.size > 3)
            Tuils.toPlanString(parts.subList(3, parts.size), Tuils.SPACE)
        else
            Tuils.EMPTYSTRING
        if (TermuxAppManager.addAction(mContext!!, id, label, send)) {
            val app = TermuxAppManager.resolve(mContext!!, id)
            if (app != null) {
                syncTermuxAppManifest(app, true)
                if (termuxAppSession != null && termuxAppSession!!.id == app.id) {
                    termuxAppSession = app
                    updateTermuxConsoleLabels()
                }
            }
            appendTermuxLine("App action registered: " + TermuxAppManager.normalizeId(id) + " / " + label)
        } else {
            appendTermuxLine("Unable to register app action.")
        }
    }

    private fun removeTermuxAppAction(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine("usage: app-action-rm <id> <label>")
            return
        }
        val id = parts.get(1)
        val label = Tuils.toPlanString(parts.subList(2, parts.size), Tuils.SPACE)
        if (TermuxAppManager.removeAction(mContext!!, id, label)) {
            val app = TermuxAppManager.resolve(mContext!!, id)
            if (app != null) {
                syncTermuxAppManifest(app, true)
                if (termuxAppSession != null && termuxAppSession!!.id == app.id) {
                    termuxAppSession = app
                    updateTermuxConsoleLabels()
                }
            }
            appendTermuxLine("App action removed: " + TermuxAppManager.normalizeId(id) + " / " + label)
        } else {
            appendTermuxLine("App action not removed: " + label)
        }
    }

    private fun removeTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine("usage: app-rm <id>")
            appendTermuxLine("alias: rm-app <id>")
            return
        }
        val id = TermuxAppManager.normalizeId(parts.get(1))
        if (TermuxAppManager.remove(mContext!!, id)) {
            removeTermuxAppManifest(id)
            appendTermuxLine("Termux app removed: " + id)
        } else {
            appendTermuxLine("Termux app not removed: " + id)
        }
    }

    private fun openTermuxAppSession(app: TermuxAppManager.TermuxApp) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openTermuxAppSession(app) }
            return
        }

        if (termuxOverlay == null) {
            return
        }
        closeFileConsole(false)
        closeLuaAppSession(true)
        termuxAppSession = app
        termuxAppLastStatus = "starting"
        resetTermuxAppRuntimeState(true)
        styleTermuxConsole()
        updateTermuxConsoleLabels()
        termuxOverlay!!.setVisibility(View.VISIBLE)
        termuxOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()
        renderTermuxAppFrame(null, "starting " + app.title + "...")
        syncTermuxAppManifest(app, false)
        dispatchTermuxAppScript("start", buildTermuxAppStartScript(app), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_START_WATCH_MS)
        scheduleTermuxConsoleFocusCapture(true)
    }

    private fun submitTermuxAppInput(rawCommand: String?) {
        val app = termuxAppSession ?: return
        val command = if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (command.startsWith(":")) {
            handleTermuxAppLocalCommand(app, command.substring(1).trim { it <= ' ' }.lowercase(Locale.getDefault()))
            return
        }
        termuxAppLastStatus = if (command.length == 0) "sent enter" else "sent: " + command
        renderTermuxAppFrame(null, termuxAppLastStatus)
        dispatchTermuxAppScript("send", buildTermuxAppSendScript(app, command), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
    }

    private fun submitTermuxAppAction(action: TermuxAppManager.TermuxAppAction) {
        val app = termuxAppSession ?: return
        termuxAppLastStatus = "action: " + action.label
        renderTermuxAppFrame(null, termuxAppLastStatus)
        dispatchTermuxAppScript("action", buildTermuxAppSendScript(app, action.send), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
        scheduleTermuxConsoleFocusCapture(true)
    }

    private fun handleTermuxAppLocalCommand(app: TermuxAppManager.TermuxApp, command: String) {
        if ("help" == command || command.length == 0) {
            renderTermuxAppFrame(
                null,
                ":help, :refresh, :restart, :stop, :detach, :open, :clear. Other input is sent to the app."
            )
        } else if ("refresh" == command || "r" == command) {
            refreshTermuxAppSession(true)
            scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_MANUAL_REFRESH_WATCH_MS)
        } else if ("restart" == command) {
            termuxAppLastStatus = "restarting"
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("restart", buildTermuxAppKillScript(app) + "\n" + buildTermuxAppStartScript(app), true)
            scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_START_WATCH_MS)
        } else if ("stop" == command || "kill" == command) {
            termuxAppRefreshGeneration++
            termuxAppLastStatus = "stopping"
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("stop", buildTermuxAppKillScript(app), true)
        } else if ("detach" == command || "exit" == command || "close" == command) {
            closeTermuxConsole()
        } else if ("open" == command) {
            openTermuxApp()
        } else if ("clear" == command) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else {
            renderTermuxAppFrame(null, "Unknown app command: :" + command)
        }
    }

    private fun refreshTermuxAppSession(announce: Boolean) {
        val app = termuxAppSession ?: return
        if (announce) {
            termuxAppLastStatus = "refreshing"
            renderTermuxAppFrame(null, termuxAppLastStatus)
        }
        dispatchTermuxAppScript("capture", buildTermuxAppCaptureScript(app), false)
    }

    private fun scheduleTermuxAppRefreshBurst(appId: String?, watchMs: Long) {
        extendTermuxAppWatch(watchMs)
        val generation = ++termuxAppRefreshGeneration
        for (delay in TERMUX_APP_REFRESH_BURST_DELAYS_MS) {
            handler?.postDelayed(Runnable {
                val current = termuxAppSession
                if (generation == termuxAppRefreshGeneration
                    && current != null
                    && current.id == appId
                    && this.isTermuxConsoleVisible
                ) {
                    refreshTermuxAppSession(false)
                }
            }, delay.toLong())
        }
    }

    private fun extendTermuxAppWatch(watchMs: Long) {
        termuxAppWatchUntilMs = max(termuxAppWatchUntilMs, System.currentTimeMillis() + watchMs)
    }

    private fun shouldContinueTermuxAppWatch(): Boolean {
        return System.currentTimeMillis() < termuxAppWatchUntilMs
    }

    private fun scheduleTermuxAppAdaptiveRefresh(appId: String?) {
        val generation = termuxAppRefreshGeneration
        handler?.postDelayed(Runnable {
            val current = termuxAppSession
            if (generation == termuxAppRefreshGeneration
                && current != null
                && current.id == appId
                && this.isTermuxConsoleVisible
                && shouldContinueTermuxAppWatch()
            ) {
                refreshTermuxAppSession(false)
            }
        }, TERMUX_APP_ADAPTIVE_REFRESH_INTERVAL_MS.toLong())
    }

    private fun dispatchTermuxAppScript(action: String, script: String, echoFailure: Boolean): Boolean {
        val app = termuxAppSession ?: return false
        if (!ensureTermuxBridgeReady(echoFailure)) {
            if (!echoFailure) {
                renderTermuxAppFrame(null, "Termux bridge is not ready.")
            }
            return false
        }
        try {
            val sequence = ++termuxAppDispatchSequence
            TermuxBridgeManager.startRunCommand(
                mContext!!,
                TermuxBridgeManager.TERMUX_SH,
                app.workDir,
                createResultPendingIntent(
                    mContext,
                    TERMUX_APP_RESULT_PREFIX + action + ":" + sequence + ":" + app.id,
                    null
                ),
                arrayOf<String?>("-lc", script)
            )
            return true
        } catch (e: SecurityException) {
            renderTermuxAppFrame(null, "Termux rejected the app command: permission denied.")
        } catch (e: Exception) {
            renderTermuxAppFrame(null, "unable to dispatch app command: " + e.javaClass.getSimpleName())
        }
        return false
    }

    private fun syncTermuxAppManifest(app: TermuxAppManager.TermuxApp, echoToConsole: Boolean): Boolean {
        val dispatched = dispatchTermuxAppSideEffectScript(
            app,
            buildTermuxAppPrepareScript(app, echoToConsole),
            if (echoToConsole) TERMUX_APP_SYNC_RESULT_PREFIX + app.id else null
        )
        if (!dispatched && echoToConsole) {
            appendTermuxLine("Manifest sync pending: Termux bridge is not ready.")
        }
        return dispatched
    }

    private fun removeTermuxAppManifest(id: String?): Boolean {
        val normalized = TermuxAppManager.normalizeId(id)
        if (normalized.length == 0) {
            return false
        }
        val appHome = shellQuote(TermuxAppManager.appHomeDir(normalized))
        return dispatchTermuxAppSideEffectScript(
            null,
            "rm -f " + appHome + "/app.json\n"
                    + "printf '%s\\n' 'Re:T-UI app manifest removed; state directory kept.'",
            null
        )
    }

    private fun dispatchTermuxAppSideEffectScript(
        app: TermuxAppManager.TermuxApp?,
        script: String,
        resultLabel: String?
    ): Boolean {
        val context = mContext ?: return false
        val status = TermuxBridgeManager.status(context)
        if (!status.termuxInstalled || !status.runCommandDeclared || !status.runCommandGranted) {
            return false
        }
        try {
            TermuxBridgeManager.startRunCommand(
                context,
                TermuxBridgeManager.TERMUX_SH,
                app?.workDir ?: TermuxBridgeManager.TERMUX_HOME,
                if (TextUtils.isEmpty(resultLabel)) null else createResultPendingIntent(context, resultLabel, null),
                arrayOf<String?>("-lc", script)
            )
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun calculateTermuxAppGeometry(): TermuxWorkspaceGeometry {
        val output = termuxOutput
        val grid = if (termuxAppSession != null) termuxGrid else null
        val host = termuxScroll ?: termuxOutputPanel
        val width = max(0, (host?.width ?: 0) - (host?.paddingLeft ?: 0) - (host?.paddingRight ?: 0))
        val height = max(0, visibleTermuxAppHostHeight(host) - (host?.paddingTop ?: 0) - (host?.paddingBottom ?: 0))
        val charWidth = grid?.characterWidth() ?: termuxWorkspaceCharWidth(output)
        val lineHeight = grid?.lineHeight() ?: termuxAppLineHeight(output)
        val cols = if (width > 0) (width / charWidth).toInt() else termuxAppLastCols
        val rows = if (height > 0) height / max(1, lineHeight) else termuxAppLastRows
        val geometry = TermuxWorkspaceGeometry(
            max(TERMUX_WORKSPACE_MIN_COLS, min(TERMUX_WORKSPACE_MAX_COLS, cols)),
            max(TERMUX_WORKSPACE_MIN_ROWS, min(TERMUX_WORKSPACE_MAX_ROWS, rows))
        )
        termuxAppLastCols = geometry.cols
        termuxAppLastRows = geometry.rows
        applyTermuxAppCellViewport(geometry, charWidth, lineHeight)
        return geometry
    }

    private fun visibleTermuxAppHostHeight(host: View?): Int {
        if (host == null) {
            return 0
        }
        val rawHeight = host.height
        if (rawHeight <= 0 || !imeInsetVisible || imeBottomOffset <= 0) {
            return max(0, rawHeight)
        }
        val root = termuxOverlay ?: mRootView ?: return max(0, rawHeight)
        if (root.height <= 0) {
            return max(0, rawHeight)
        }
        val rootLocation = IntArray(2)
        val hostLocation = IntArray(2)
        root.getLocationInWindow(rootLocation)
        host.getLocationInWindow(hostLocation)
        val visibleRootBottom = rootLocation[1] + root.height - imeBottomOffset
        val visibleHeight = visibleRootBottom - hostLocation[1]
        return max(0, min(rawHeight, visibleHeight))
    }

    private fun termuxAppLineHeight(output: TextView?): Int {
        if (output == null) {
            return UIUtils.dpToPx(mContext!!, 18)
        }
        val baseLineHeight = max(1, output.lineHeight)
        val fallbackLineHeight = max(
            baseLineHeight,
            (baseLineHeight * TERMUX_WORKSPACE_LINE_HEIGHT_FALLBACK_MULTIPLIER).roundToInt()
        )
        val sampledLineHeight = try {
            val layout = StaticLayout.Builder.obtain(
                TERMUX_WORKSPACE_LINE_HEIGHT_SAMPLE,
                0,
                TERMUX_WORKSPACE_LINE_HEIGHT_SAMPLE.length,
                output.paint,
                max(1, output.paint.measureText("MMMM").roundToInt())
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
            var previousBottom = 0
            var measuredLineHeight = baseLineHeight
            for (index in 0 until layout.lineCount) {
                val bottom = layout.getLineBottom(index)
                measuredLineHeight = max(measuredLineHeight, bottom - previousBottom)
                previousBottom = bottom
            }
            measuredLineHeight
        } catch (ignored: Exception) {
            baseLineHeight
        }
        return max(fallbackLineHeight, max(sampledLineHeight, termuxAppMeasuredLineHeight))
    }

    private fun applyTermuxAppCellViewport(
        geometry: TermuxWorkspaceGeometry,
        charWidth: Float,
        lineHeight: Int
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return
        }
        val output = (if (termuxAppSession != null) termuxGrid else null) ?: termuxOutput ?: return
        val targetWidth = max(1, (geometry.cols * charWidth).roundToInt())
        val targetHeight = max(1, geometry.rows * lineHeight)
        output.minimumWidth = targetWidth
        output.minimumHeight = targetHeight
        val params = output.layoutParams ?: return
        var changed = false
        if (params.width != targetWidth) {
            params.width = targetWidth
            changed = true
        }
        if (params.height != targetHeight) {
            params.height = targetHeight
            changed = true
        }
        if (changed) {
            output.layoutParams = params
        }
        output.requestLayout()
        termuxScroll?.requestLayout()
        termuxOutputPanel?.requestLayout()
    }

    private fun resetTermuxAppCellViewport() {
        resetTermuxAppViewportTarget(termuxOutput, ViewGroup.LayoutParams.MATCH_PARENT)
        resetTermuxAppViewportTarget(termuxGrid, ViewGroup.LayoutParams.WRAP_CONTENT)
        termuxGrid?.visibility = View.GONE
    }

    private fun resetTermuxAppViewportTarget(target: View?, width: Int) {
        val output = target ?: return
        val params = output.layoutParams
        if (params != null) {
            params.width = width
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT
            output.layoutParams = params
        }
        output.minimumWidth = 0
        output.minimumHeight = 0
        output.requestLayout()
    }

    private fun buildTermuxAppResizeScript(session: String): String {
        val geometry = calculateTermuxAppGeometry()
        return ("tmux resize-window -t " + session + " -x " + geometry.cols + " -y " + geometry.rows + " 2>/dev/null || true\n"
                + "tmux resize-pane -t " + session + " -x " + geometry.cols + " -y " + geometry.rows + " 2>/dev/null || true")
    }

    private fun buildTermuxAppStartScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        val workDir = shellQuote(app.workDir)
        val command = shellQuote(app.command)
        val sh = shellQuote(TermuxBridgeManager.TERMUX_SH)
        val appId = shellQuote(app.id)
        val appHome = shellQuote(app.homeDir)
        val state = shellQuote(app.statePath)
        val manifest = shellQuote(app.manifestPath)
        return (buildTermuxAppPrepareScript(app, false) + "\n"
                + "if ! command -v tmux >/dev/null 2>&1; then\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "  exit 127\n"
                + "fi\n"
                + "if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  mkdir -p " + workDir + " 2>/dev/null || true\n"
                + "  tmux new-session -d -s " + session + " -c " + workDir
                + " env RETUI_APP_ID=" + appId
                + " RETUI_APP_HOME=" + appHome
                + " RETUI_APP_STATE=" + state
                + " RETUI_APP_MANIFEST=" + manifest
                + " " + sh + " -lc " + command + "\n"
                + "  sleep 0.35\n"
                + "fi\n"
                + buildTermuxAppResizeScript(session) + "\n"
                + "tmux capture-pane -t " + session + " -p -e -N")
    }

    private fun buildTermuxAppPrepareScript(app: TermuxAppManager.TermuxApp, echo: Boolean): String {
        val appHome = shellQuote(app.homeDir)
        val memoryDir = shellQuote(app.memoryDir)
        val logsDir = shellQuote(app.logsDir)
        val manifest = shellQuote(app.manifestPath)
        val state = shellQuote(app.statePath)
        val json = shellQuote(TermuxAppManager.manifestJson(app))
        val script = ("mkdir -p " + appHome + " " + memoryDir + " " + logsDir + "\n"
                + "if [ ! -f " + state + " ]; then printf '%s\\n' '{}' > " + state + "; fi\n"
                + "printf '%s\\n' " + json + " > " + manifest)
        if (!echo) {
            return script
        }
        return script + "\nprintf '%s\\n' 'manifest: " + app.manifestPath.replace("'", "'\"'\"'") + "'"
    }

    private fun buildTermuxAppSendScript(app: TermuxAppManager.TermuxApp, input: String): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        val send = if (input.length == 0) {
            "tmux send-keys -t " + session + " C-m"
        } else {
            "tmux send-keys -t " + session + " -- " + shellQuote(input) + "\n" +
                    "tmux send-keys -t " + session + " C-m"
        }
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session) + "\n" +
                send + "\nsleep 0.25\n" +
                buildTermuxAppPostInputCaptureScript(session)
    }

    private fun buildTermuxAppControlScript(app: TermuxAppManager.TermuxApp, key: String): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session) + "\n" +
                "tmux send-keys -t " + session + " -- " + shellQuote(key) + "\n" +
                "sleep 0.25\n" +
                buildTermuxAppPostInputCaptureScript(session)
    }

    private fun buildTermuxAppCaptureScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return buildTermuxAppEnsureScript(app) + "\n" +
                buildTermuxAppResizeScript(session) + "\n" +
                "tmux capture-pane -t " + session + " -p -e -N"
    }

    private fun buildTermuxAppPostInputCaptureScript(session: String): String {
        return ("if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  printf '%s\\n' 'session ended. Type :restart to start it.'\n"
                + "  exit 0\n"
                + "fi\n"
                + "tmux capture-pane -t " + session + " -p -e -N")
    }

    private fun buildTermuxAppKillScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return ("if command -v tmux >/dev/null 2>&1; then\n"
                + "  tmux kill-session -t " + session + " 2>/dev/null || true\n"
                + "  printf '%s\\n' 'session stopped.'\n"
                + "else\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "fi")
    }

    private fun buildTermuxAppEnsureScript(app: TermuxAppManager.TermuxApp): String {
        val session = shellQuote(TermuxAppManager.tmuxSessionName(app.id))
        return ("if ! command -v tmux >/dev/null 2>&1; then\n"
                + "  printf '%s\\n' 'tmux missing: pkg install tmux'\n"
                + "  exit 127\n"
                + "fi\n"
                + "if ! tmux has-session -t " + session + " 2>/dev/null; then\n"
                + "  printf '%s\\n' 'session not running. Type :restart to start it.'\n"
                + "  exit 1\n"
                + "fi")
    }

    private fun appendTermuxAppSyncResult(
        label: String,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        val id = label.substring(TERMUX_APP_SYNC_RESULT_PREFIX.length)
        if (exitCode == 0 && TextUtils.isEmpty(stderr) && TextUtils.isEmpty(error)) {
            appendTermuxLine("Manifest synced: " + id)
            if (!TextUtils.isEmpty(stdout)) {
                appendTermuxLine(stdout!!.trim { it <= ' ' })
            }
            return
        }

        appendTermuxLine("Manifest sync failed: " + id)
        if (exitCode != Int.Companion.MIN_VALUE) {
            appendTermuxLine("exit: " + exitCode)
        }
        if (!TextUtils.isEmpty(stderr)) {
            appendTermuxLine("stderr: " + stderr!!.trim { it <= ' ' })
        }
        if (!TextUtils.isEmpty(error)) {
            appendTermuxLine("error: " + error!!.trim { it <= ' ' })
        }
        if (!TextUtils.isEmpty(debug)) {
            appendTermuxLine("debug: " + debug!!.trim { it <= ' ' })
        }
    }

    private fun appendTermuxAppResult(
        label: String,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        val app = termuxAppSession
        if (app == null || termuxAppResultId(label) != app.id) {
            return
        }
        val sequence = termuxAppResultSequence(label)
        if (sequence > 0 && sequence < termuxAppAcceptedSequence) {
            return
        }
        if (sequence > 0) {
            termuxAppAcceptedSequence = max(termuxAppAcceptedSequence, sequence)
        }
        val action = termuxAppResultAction(label)
        if (isTermuxAppSessionEnded(stdout, stderr, error, exitCode)) {
            termuxAppRefreshGeneration++
            val alreadyEnded = "session ended" == termuxAppLastStatus
            termuxAppLastStatus = "session ended"
            if (!(alreadyEnded && "capture" == action)) {
                renderTermuxAppFrame("session ended. Type :restart to start it.", termuxAppLastStatus)
            }
            return
        }
        val status: String?
        val frame: String?
        if (!TextUtils.isEmpty(stdout)) {
            status = termuxAppLastStatus
            frame = stdout
        } else if (!TextUtils.isEmpty(stderr)) {
            status = "stderr"
            frame = stderr
        } else if (!TextUtils.isEmpty(error)) {
            status = "error: " + error!!.trim { it <= ' ' }
            frame = null
        } else if (!TextUtils.isEmpty(debug)) {
            status = "debug: " + debug!!.trim { it <= ' ' }
            frame = null
        } else if (exitCode != Int.Companion.MIN_VALUE && exitCode != 0) {
            status = "exit: " + exitCode
            frame = null
        } else {
            status = termuxAppLastStatus
            frame = null
        }
        val frameChanged = updateTermuxAppLastFrame(frame)
        renderTermuxAppFrame(frame, status)
        if (frameChanged && shouldContinueTermuxAppWatch()) {
            scheduleTermuxAppAdaptiveRefresh(app.id)
        }
    }

    private fun termuxAppResultAction(label: String): String {
        if (!label.startsWith(TERMUX_APP_RESULT_PREFIX)) {
            return Tuils.EMPTYSTRING
        }
        val rest = label.substring(TERMUX_APP_RESULT_PREFIX.length)
        val separator = rest.indexOf(':')
        if (separator <= 0) {
            return rest
        }
        return rest.substring(0, separator)
    }

    private fun termuxAppResultSequence(label: String): Int {
        if (!label.startsWith(TERMUX_APP_RESULT_PREFIX)) {
            return -1
        }
        val rest = label.substring(TERMUX_APP_RESULT_PREFIX.length)
        val first = rest.indexOf(':')
        if (first <= 0) {
            return -1
        }
        val second = rest.indexOf(':', first + 1)
        if (second <= first + 1) {
            return -1
        }
        return try {
            rest.substring(first + 1, second).toInt()
        } catch (e: Exception) {
            -1
        }
    }

    private fun termuxAppResultId(label: String): String {
        if (!label.startsWith(TERMUX_APP_RESULT_PREFIX)) {
            return Tuils.EMPTYSTRING
        }
        val rest = label.substring(TERMUX_APP_RESULT_PREFIX.length)
        val first = rest.indexOf(':')
        if (first < 0 || first == rest.length - 1) {
            return Tuils.EMPTYSTRING
        }
        val second = rest.indexOf(':', first + 1)
        return if (second > first) rest.substring(second + 1) else rest.substring(first + 1)
    }

    private fun updateTermuxAppLastFrame(frame: String?): Boolean {
        val clean = stripTermuxAnsi(frame)?.trimEnd { it <= ' ' }
        if (TextUtils.isEmpty(clean)) {
            return false
        }
        val changed = clean != termuxAppLastFrameText
        termuxAppLastFrameText = clean
        return changed
    }

    private fun isTermuxAppSessionEnded(
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int
    ): Boolean {
        val combined = StringBuilder()
        if (!TextUtils.isEmpty(stdout)) {
            combined.append(stdout).append('\n')
        }
        if (!TextUtils.isEmpty(stderr)) {
            combined.append(stderr).append('\n')
        }
        if (!TextUtils.isEmpty(error)) {
            combined.append(error).append('\n')
        }
        val text = combined.toString().lowercase(Locale.getDefault())
        if (text.contains("session ended. type :restart")
            || text.contains("session not running. type :restart")
            || text.contains("no server running on")
            || text.contains("can't find session")
            || text.contains("can't find pane")
        ) {
            return true
        }
        return exitCode != 0 && text.contains("tmux") && text.contains("server")
    }

    private fun renderTermuxAppFrame(frame: String?, status: String?) {
        val app = termuxAppSession ?: return
        val hasFrame = !TextUtils.isEmpty(frame)
        if (TextUtils.isEmpty(frame) && !TextUtils.isEmpty(termuxAppLastFrameText)
            && isTransientTermuxAppStatus(status)
        ) {
            return
        }
        if (hasFrame && termuxGrid != null) {
            showTermuxAppGridOutput()
        } else {
            showPlainTermuxOutput()
        }
        val raw = if (hasFrame) {
            frame!!
        } else {
            ("Re:T-UI app: " + app.title
                    + "\nsession: " + TermuxAppManager.tmuxSessionName(app.id)
                    + "\nlocal commands: :help :refresh :restart :stop :detach :open"
                    + if (TextUtils.isEmpty(status)) "\nstarting..." else "\nstatus: " + status)
        }
        termuxBuffer.setLength(0)
        termuxBuffer.append(stripTermuxAnsi(raw)?.trimEnd { it <= ' ' } ?: Tuils.EMPTYSTRING)
        if (hasFrame && termuxGrid != null) {
            val geometry = calculateTermuxAppGeometry()
            termuxGrid?.setFrame(raw, geometry.cols, geometry.rows, null, null)
        } else {
            val rendered = if (hasFrame) renderTermuxWorkspaceAnsi(raw) else SpannableStringBuilder(raw)
            termuxOutput?.setText(rendered)
        }
        updateTermuxAppMeasuredLineHeight()
        resetTermuxAppViewportOrigin()
        termuxScroll?.post(Runnable { resetTermuxAppViewportOrigin() })
    }

    private fun isTransientTermuxAppStatus(status: String?): Boolean {
        if (TextUtils.isEmpty(status)) {
            return true
        }
        val clean = status!!.lowercase(Locale.getDefault())
        return clean.startsWith("sent")
                || clean.startsWith("action:")
                || clean.startsWith("key:")
                || clean == "refreshing"
                || clean == "restarting"
                || clean == "starting"
    }

    private fun updateTermuxAppMeasuredLineHeight() {
        val grid = if (termuxAppSession != null) termuxGrid else null
        if (grid != null && grid.visibility == View.VISIBLE) {
            val lineHeight = grid.lineHeight()
            if (lineHeight > termuxAppMeasuredLineHeight) {
                termuxAppMeasuredLineHeight = lineHeight
            }
            return
        }
        val output = termuxOutput ?: return
        output.post(Runnable {
            val layout = output.layout ?: return@Runnable
            if (layout.lineCount <= 0) {
                return@Runnable
            }
            var previousBottom = 0
            var maxLineHeight = 0
            val linesToCheck = min(layout.lineCount, TERMUX_WORKSPACE_LINE_HEIGHT_MAX_SAMPLE_LINES)
            for (index in 0 until linesToCheck) {
                val bottom = layout.getLineBottom(index)
                maxLineHeight = max(maxLineHeight, bottom - previousBottom)
                previousBottom = bottom
            }
            if (maxLineHeight > termuxAppMeasuredLineHeight) {
                termuxAppMeasuredLineHeight = maxLineHeight
            }
        })
    }

    private fun resetTermuxAppViewportOrigin() {
        termuxOutput?.scrollTo(0, 0)
        termuxScroll?.scrollTo(0, 0)
    }

    private fun stripTermuxAnsi(text: String?): String? {
        if (text == null) {
            return null
        }
        return termuxAnsiPattern.matcher(text).replaceAll(Tuils.EMPTYSTRING)
    }

    private fun appendTermuxSetup() {
        appendTermuxLine("Termux bridge setup")
        appendTermuxLine("1. Install current Termux from F-Droid/GitHub.")
        appendTermuxLine("2. In Termux, enable external app commands:")
        appendTermuxLine("   mkdir -p ~/.termux")
        appendTermuxLine("   echo 'allow-external-apps = true' >> ~/.termux/termux.properties")
        appendTermuxLine("   termux-reload-settings")
        appendTermuxLine("3. Put scripts in a stable folder, for example:")
        appendTermuxLine("   mkdir -p ~/retui")
        appendTermuxLine("   nano ~/retui/test.sh")
        appendTermuxLine("   chmod +x ~/retui/test.sh")
        appendTermuxLine("4. Create a Re:T-UI script alias:")
        appendTermuxLine("   alias -add -s test /data/data/com.termux/files/home/retui/test.sh")
        appendTermuxLine("5. Run it from Re:T-UI:")
        appendTermuxLine("   termux -run test")
        appendTermuxLine("6. For callback modules, package-scope the broadcast:")
        appendTermuxLine("   am broadcast -p com.dvil.tui_renewed -a com.dvil.tui_renewed.RETUI_CALLBACK ...")
        appendTermuxLine("7. Optional helper:")
        appendTermuxLine("   retui-token -show")
        appendTermuxLine("   create ~/retui/retui-helper.sh with retui_module/retui_output helpers.")
        appendTermuxLine("8. Script-backed module:")
        appendTermuxLine("   module -add server termux:/data/data/com.termux/files/home/retui/server-health.sh")
        appendTermuxLine("   module -refresh server")
        appendTermuxLine("If Android asks for RUN_COMMAND permission, allow Re:T-UI and retry.")
        appendTermuxStatus()
    }

    private fun normalizeTermuxConsoleCommand(command: String?): String {
        var normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        val lower = normalized.lowercase()

        if ("termux" == lower) {
            return Tuils.EMPTYSTRING
        }

        if (lower.startsWith("termux ")) {
            normalized = normalized.substring("termux".length).trim { it <= ' ' }
        }

        if (normalized.startsWith("-")) {
            normalized = normalized.substring(1).trim { it <= ' ' }
        }

        return normalized
    }

    private fun appendTermuxStatus() {
        val status = TermuxBridgeManager.status(mContext!!)

        appendTermuxLine("Termux installed: " + status.termuxInstalled)
        appendTermuxLine("RunCommand bridge: " + (if (status.runCommandDeclared) "available" else "not available"))
        appendTermuxLine("RunCommand permission: " + (if (status.runCommandGranted) "granted" else "not granted"))
        appendTermuxLine("Console cwd: " + termuxWorkingDirectory)
        appendTermuxLine("Required Termux setting: allow-external-apps=true")
        if (!status.termuxInstalled) {
            appendTermuxLine("Install Termux before enabling script dispatch.")
        } else if (!status.runCommandDeclared) {
            appendTermuxLine("This Termux build does not expose RUN_COMMAND.")
            appendTermuxLine("Install the current Termux build from F-Droid/GitHub, not the old Play Store build.")
        } else if (!status.runCommandGranted) {
            appendTermuxLine("Grant Re:T-UI permission to run commands in Termux when prompted by Android/Termux.")
        } else {
            appendTermuxLine("Bridge prerequisites look ready for the next phase.")
        }
    }

    private fun runTermuxShellCommand(command: String?) {
        val trimmed = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (trimmed.length == 0) {
            return
        }
        if (isInteractiveTermuxCommand(trimmed)) {
            appendTermuxLine("interactive command: " + trimmed)
            appendTermuxLine("opening Termux for a live terminal session.")
            openTermuxApp()
            return
        }

        val shellCommand = "cd " + shellQuote(termuxWorkingDirectory) + " && " + trimmed
        dispatchTermuxShell(shellCommand, TERMUX_CONSOLE_SHELL_RESULT_PREFIX + trimmed, false)
    }

    private fun changeTermuxDirectory(command: String?) {
        var target: String? = if (command == null || command.trim { it <= ' ' }.length <= 2)
            TermuxBridgeManager.TERMUX_HOME
        else
            command.trim { it <= ' ' }.substring(2).trim { it <= ' ' }
        if (target.isNullOrEmpty()) {
            target = TermuxBridgeManager.TERMUX_HOME
        }
        target = expandTermuxPath(target) ?: TermuxBridgeManager.TERMUX_HOME
        val shellCommand = ("cd " + shellQuote(termuxWorkingDirectory)
                + " && cd " + shellQuote(target)
                + " && pwd")
        dispatchTermuxShell(shellCommand, TERMUX_CONSOLE_CD_RESULT_PREFIX + target, false)
    }

    private fun dispatchTermuxShell(
        shellCommand: String?,
        resultLabel: String?,
        echoDispatch: Boolean
    ): Boolean {
        if (!ensureTermuxBridgeReady(true)) {
            return false
        }

        try {
            TermuxBridgeManager.startRunCommand(
                mContext!!,
                TermuxBridgeManager.TERMUX_SH,
                termuxWorkingDirectory,
                createResultPendingIntent(mContext, resultLabel, null),
                arrayOf<String?>("-lc", shellCommand)
            )
            if (echoDispatch) {
                appendTermuxLine("shell: " + shellCommand)
            }
            return true
        } catch (e: SecurityException) {
            reportTermuxDispatch("Termux rejected the command: permission denied.", true)
            reportTermuxDispatch(
                "Check allow-external-apps=true and grant RUN_COMMAND permission.",
                true
            )
        } catch (e: Exception) {
            reportTermuxDispatch(
                "unable to dispatch Termux command: " + e.javaClass.getSimpleName(),
                true
            )
            reportTermuxDispatch("Open Termux once, then retry from this console.", true)
        }
        return false
    }

    private fun isInteractiveTermuxCommand(command: String?): Boolean {
        val parts = Tuils.splitArgs(command)
        if (parts.isEmpty()) {
            return false
        }
        val executable = parts.get(0)!!.lowercase()
        return "nano" == executable
                || "vim" == executable
                || "vi" == executable
                || "nvim" == executable
                || "top" == executable
                || "htop" == executable
                || "ssh" == executable
                || "tmux" == executable
                || "screen" == executable
                || "less" == executable
                || "more" == executable
                || "man" == executable
    }

    private fun runTermuxCommand(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine("usage: run <script_path> [args...]")
            appendTermuxLine("example: run /data/data/com.termux/files/home/retui/myscript.sh")
            return
        }

        var path = parts.get(1)
        val aliasName = path
        path = resolveTermuxRunnable(path)
        val args = ArrayList<String?>()
        if (parts.size > 2) {
            args.addAll(parts.subList(2, parts.size))
        }

        runTermuxScript(path, args, null, true, aliasName)
    }

    private fun runTermuxScript(
        path: String?,
        args: ArrayList<String?>,
        module: String?,
        echoToConsole: Boolean,
        aliasName: String? = path
    ): Boolean {
        var path = path
        path = expandTermuxPath(path)
        if (!ensureTermuxBridgeReady(echoToConsole)) {
            return false
        }

        var dispatchPath: String? = path
        val dispatchArgs = ArrayList<String?>(args)
        if (!TextUtils.isEmpty(module)) {
            val materialized: ModuleVariableManager.Materialized =
                ModuleVariableManager.materialize(mContext!!, module)
            dispatchPath = TermuxBridgeManager.TERMUX_SH
            dispatchArgs.clear()
            dispatchArgs.add("-c")
            dispatchArgs.add(buildModuleRuntimeCommand(path, module, materialized))
            dispatchArgs.add("retui-module")
            dispatchArgs.addAll(args)
        } else if (shouldRunTermuxPathWithShell(path)) {
            dispatchPath = TermuxBridgeManager.TERMUX_SH
            dispatchArgs.clear()
            dispatchArgs.add(path)
            dispatchArgs.addAll(args)
        }

        try {
            TermuxBridgeManager.startRunCommand(
                mContext!!,
                dispatchPath,
                termuxWorkingDirectory,
                createResultPendingIntent(
                    mContext,
                    path,
                    if (TextUtils.isEmpty(module)) null else ModuleManager.normalize(module)
                ),
                if (dispatchArgs.isEmpty()) null else dispatchArgs.toTypedArray<String?>()
            )
            if (echoToConsole) {
                appendTermuxLine("dispatched to Termux: " + path)
                if (aliasName != null && aliasName != path) {
                    appendTermuxLine("alias: " + aliasName + " -> " + path)
                }
                if (!args.isEmpty()) {
                    appendTermuxLine("args: " + Tuils.toPlanString(args, Tuils.SPACE))
                }
            }
            return true
        } catch (e: SecurityException) {
            reportTermuxDispatch("Termux rejected the command: permission denied.", echoToConsole)
            reportTermuxDispatch(
                "Check allow-external-apps=true and grant RUN_COMMAND permission.",
                echoToConsole
            )
        } catch (e: Exception) {
            reportTermuxDispatch(
                "unable to dispatch Termux command: " + e.javaClass.getSimpleName(),
                echoToConsole
            )
            reportTermuxDispatch("Open Termux once, then retry from this console.", echoToConsole)
        }
        return false
    }

    private fun ensureTermuxBridgeReady(echoToConsole: Boolean): Boolean {
        val status = TermuxBridgeManager.status(mContext!!)
        if (!status.termuxInstalled) {
            reportTermuxDispatch("Termux is not installed.", echoToConsole)
            return false
        }

        if (!status.runCommandDeclared) {
            reportTermuxDispatch("This Termux build does not expose RUN_COMMAND.", echoToConsole)
            reportTermuxDispatch(
                "Install/update Termux from F-Droid or GitHub, then retry.",
                echoToConsole
            )
            return false
        }

        if (!status.runCommandGranted) {
            requestRunCommandPermissionIfPossible(
                mContext,
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            reportTermuxDispatch("RunCommand permission is not granted yet.", echoToConsole)
            reportTermuxDispatch(
                "If Android shows a permission prompt, allow Re:T-UI and retry.",
                echoToConsole
            )
            reportTermuxDispatch("Termux must also have allow-external-apps=true.", echoToConsole)
            return false
        }

        return true
    }

    private fun shouldRunTermuxPathWithShell(path: String?): Boolean {
        if (TextUtils.isEmpty(path)) {
            return false
        }
        return path!!.lowercase().endsWith(".sh")
    }

    private fun buildModuleRuntimeCommand(
        path: String?,
        module: String?,
        materialized: ModuleVariableManager.Materialized
    ): String {
        val runtimeDir = TermuxBridgeManager.TERMUX_HOME + "/.retui/runtime"
        val runtimePath = runtimeDir + "/" + ModuleManager.normalize(module) + ".sh"
        val replacements = materialized.asMap() ?: emptyMap()
        val command = StringBuilder()
        command.append("mkdir -p ").append(shellQuote(runtimeDir)).append(" && ")
        command.append("cp ").append(shellQuote(path)).append(" ").append(shellQuote(runtimePath))
        for (entry in replacements.entries) {
            val key = entry.key ?: continue
            val value = entry.value ?: Tuils.EMPTYSTRING
            command.append(" && sed -i ")
                .append(shellQuote("s|" + key + "|" + value.replace("|", "\\|") + "|g"))
                .append(" ")
                .append(shellQuote(runtimePath))
        }
        command.append(" && chmod +x ").append(shellQuote(runtimePath))
        for (entry in replacements.entries) {
            val key = entry.key ?: continue
            val value = entry.value ?: Tuils.EMPTYSTRING
            if (key.startsWith("%RETUI_") && key.endsWith("_JSON")
                || ModuleVariableManager.TOKEN_CALENDAR_UPCOMING_MONTH == key
            ) {
                val name = key.substring(1)
                command.append(" && export ")
                    .append(name)
                    .append("=")
                    .append(shellQuote(value))
            }
        }
        command.append(" && export RETUI_NOW=")
            .append(shellQuote(replacements[ModuleVariableManager.TOKEN_NOW]))
        command.append(" && exec ").append(shellQuote(runtimePath)).append(" \"$@\"")
        return command.toString()
    }

    private fun shellQuote(value: String?): String {
        if (value == null) {
            return "''"
        }
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private fun reportTermuxDispatch(message: String?, echoToConsole: Boolean) {
        if (echoToConsole) {
            appendTermuxLine(message)
        } else {
            Tuils.sendOutput(mContext, message)
        }
    }

    private fun resolveTermuxRunnable(candidate: String?): String? {
        var resolved = resolveTermuxAlias(candidate)
        if (TextUtils.isEmpty(resolved)) {
            return resolved
        }
        resolved = expandTermuxPath(resolved.trim { it <= ' ' }) ?: return null
        if (resolved.contains("/")) {
            if (!resolved.startsWith("/")) {
                return termuxWorkingDirectory + "/" + resolved
            }
            return resolved
        }
        if (resolved.lowercase().endsWith(".sh")) {
            return TermuxBridgeManager.TERMUX_HOME + "/retui/" + resolved
        }
        return TermuxBridgeManager.TERMUX_HOME + "/retui/" + resolved + ".sh"
    }

    private fun expandTermuxPath(path: String?): String? {
        if (path == null) {
            return null
        }
        val trimmed = path.trim { it <= ' ' }
        if ("~" == trimmed) {
            return TermuxBridgeManager.TERMUX_HOME
        }
        if (trimmed.startsWith("~/")) {
            return TermuxBridgeManager.TERMUX_HOME + trimmed.substring(1)
        }
        if (trimmed.startsWith("\$HOME/")) {
            return TermuxBridgeManager.TERMUX_HOME + trimmed.substring("\$HOME".length)
        }
        return trimmed
    }

    private fun resolveTermuxAlias(candidate: String?): String {
        if (candidate == null || candidate.length == 0 || mainPack == null || mainPack!!.aliasManager == null) {
            return candidate!!
        }

        var alias = mainPack!!.aliasManager.getAlias(candidate, false, AliasManager.SCOPE_SCRIPT)
        if (alias == null || alias.size == 0 || alias[0] == null || alias[0]!!.trim { it <= ' ' }.length == 0) {
            alias = mainPack!!.aliasManager.getAlias(candidate, false)
        }

        if (alias == null || alias.size == 0 || alias[0] == null || alias[0]!!.trim { it <= ' ' }.length == 0) {
            return candidate
        }

        return alias[0]!!.trim { it <= ' ' }
    }

    private fun appendTermuxResult(intent: Intent?) {
        if (intent == null) {
            return
        }

        val path = intent.getStringExtra(EXTRA_TERMUX_RESULT_PATH)
        val stdout = intent.getStringExtra(EXTRA_TERMUX_RESULT_STDOUT)
        val stderr = intent.getStringExtra(EXTRA_TERMUX_RESULT_STDERR)
        val exitCode = intent.getIntExtra(EXTRA_TERMUX_RESULT_EXIT_CODE, Int.Companion.MIN_VALUE)
        val error = intent.getStringExtra(EXTRA_TERMUX_RESULT_ERROR)
        val debug = intent.getStringExtra(EXTRA_TERMUX_RESULT_DEBUG)
        val module = intent.getStringExtra(EXTRA_TERMUX_RESULT_MODULE)

        if (!TextUtils.isEmpty(path) && path!!.startsWith(TermuxBridgeManager.RESULT_PREFIX)) {
            sendTermuxBridgeResult(path, stdout, stderr, error, exitCode, debug)
            return
        }
        if (!TextUtils.isEmpty(path) && path!!.startsWith(TERMUX_APP_RESULT_PREFIX)) {
            appendTermuxAppResult(path, stdout, stderr, error, exitCode, debug)
            return
        }
        if (!TextUtils.isEmpty(path) && path!!.startsWith(TERMUX_APP_SYNC_RESULT_PREFIX)) {
            appendTermuxAppSyncResult(path, stdout, stderr, error, exitCode, debug)
            return
        }
        if (!TextUtils.isEmpty(path) && path!!.startsWith(TERMUX_WORKSPACE_RESULT_PREFIX)) {
            appendTermuxWorkspaceResult(path, stdout, stderr, error, exitCode, debug)
            return
        }
        if (!TextUtils.isEmpty(path) && path!!.startsWith(TERMUX_CONSOLE_RESULT_PREFIX)) {
            appendTermuxConsoleCommandResult(path, stdout, stderr, error, exitCode, debug)
            return
        }

        if (!TextUtils.isEmpty(module)) {
            updateModuleFromTermuxResult(module, stdout, stderr, error, exitCode)
            if (termuxOverlay == null || termuxOverlay!!.getVisibility() != View.VISIBLE) {
                return
            }
        }

        appendTermuxLine("result: " + (if (path == null) "termux command" else path))
        if (exitCode != Int.Companion.MIN_VALUE) {
            appendTermuxLine("exit: " + exitCode)
        }
        if (stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("stdout:")
            val trimmedStdout = stdout.trim { it <= ' ' }
            appendTermuxLine(trimmedStdout)
            appendTermuxCallbackHint(trimmedStdout)
        }
        if (stderr != null && stderr.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("stderr:")
            appendTermuxLine(stderr.trim { it <= ' ' })
        }
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("error: " + error.trim { it <= ' ' })
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("debug: " + debug.trim { it <= ' ' })
        }
        if ((stdout == null || stdout.trim { it <= ' ' }.length == 0)
            && (stderr == null || stderr.trim { it <= ' ' }.length == 0)
            && (error == null || error.trim { it <= ' ' }.length == 0)
        ) {
            appendTermuxLine("no output returned.")
        }
    }

    private fun appendTermuxConsoleCommandResult(
        label: String,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        if (label.startsWith(TERMUX_CONSOLE_CD_RESULT_PREFIX)) {
            appendTermuxCdResult(
                label.substring(TERMUX_CONSOLE_CD_RESULT_PREFIX.length),
                stdout,
                stderr,
                error,
                exitCode
            )
            return
        }
        if (label.startsWith(TERMUX_CONSOLE_SHELL_RESULT_PREFIX)) {
            appendTermuxShellResult(
                label.substring(TERMUX_CONSOLE_SHELL_RESULT_PREFIX.length),
                stdout,
                stderr,
                error,
                exitCode,
                debug
            )
            return
        }
        appendTermuxShellResult("command", stdout, stderr, error, exitCode, debug)
    }

    private fun appendTermuxCdResult(
        target: String?,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int
    ) {
        if (exitCode == 0 && stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            termuxWorkingDirectory = lastNonEmptyLine(stdout.trim { it <= ' ' })
            appendTermuxLine("cwd: " + termuxWorkingDirectory)
            return
        }
        appendTermuxLine("cd failed: " + target)
        appendTermuxCommandError(stdout, stderr, error, exitCode, null)
    }

    private fun appendTermuxShellResult(
        command: String?,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        var wrote = false
        if (stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(stdout.trim { it <= ' ' })
            wrote = true
        }
        if (stderr != null && stderr.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(stderr.trim { it <= ' ' })
            wrote = true
        }
        appendTermuxCommandError(null, null, error, exitCode, debug)
        if (!wrote && TextUtils.isEmpty(error) && TextUtils.isEmpty(debug) && exitCode == 0) {
            appendTermuxLine("done: " + command)
        }
    }

    private fun appendTermuxCommandError(
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        if (stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(stdout.trim { it <= ' ' })
        }
        if (stderr != null && stderr.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(stderr.trim { it <= ' ' })
        }
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("error: " + error.trim { it <= ' ' })
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            appendTermuxLine("debug: " + debug.trim { it <= ' ' })
        }
        if (exitCode != Int.Companion.MIN_VALUE && exitCode != 0) {
            appendTermuxLine("exit: " + exitCode)
        }
    }

    private fun lastNonEmptyLine(text: String?): String {
        if (TextUtils.isEmpty(text)) {
            return Tuils.EMPTYSTRING
        }
        val lines: Array<String?> =
            text!!.split("\\r?\\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in lines.indices.reversed()) {
            if (lines[i] != null && lines[i]!!.trim { it <= ' ' }.length > 0) {
                return lines[i]!!.trim { it <= ' ' }
            }
        }
        return Tuils.EMPTYSTRING
    }

    private fun sendTermuxBridgeResult(
        path: String,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int,
        debug: String?
    ) {
        val label = path.substring(TermuxBridgeManager.RESULT_PREFIX.length)
        if (label.startsWith("cd ") && exitCode == 0 && stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            val newPath =
                stdout.trim { it <= ' ' }.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }
                    .toTypedArray()[0].trim { it <= ' ' }
            val folder = File(newPath)
            mainPack!!.currentDirectory = folder
            if (MainManager.interactive != null) {
                MainManager.interactive.addCommand(
                    "cd '" + folder.getAbsolutePath().replace("'", "'\\''") + "'"
                )
            }
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_HINT
                )
            )
            refreshFileConsole(true)
        }
        if (label.startsWith("fm-dirs ")) {
            val target = label.substring(8)
            if (exitCode == 0) {
                putDirs(target, stdout)
                updateFileConsoleFromTermux(target, null)
            } else {
                updateFileConsoleFromTermux(target, stderr)
            }
            return
        } else if (label.startsWith("fm-files ")) {
            val target = label.substring(9)
            if (exitCode == 0) {
                putFiles(target, stdout)
                updateFileConsoleFromTermux(target, null)
            } else {
                updateFileConsoleFromTermux(target, stderr)
            }
            return
        } else if (label.startsWith("dirs ") && exitCode == 0) {
            val target = label.substring(5)
            putDirs(target, stdout)
            updateFileConsoleFromTermux(target, null)
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_SUGGESTIONS
                )
            )
            return
        } else if (label.startsWith("files ") && exitCode == 0) {
            val target = label.substring(6)
            putFiles(target, stdout)
            updateFileConsoleFromTermux(target, null)
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_SUGGESTIONS
                )
            )
            return
        }

        val builder = StringBuilder()
        builder.append("Termux bridge: ").append(label)
        if (exitCode != Int.Companion.MIN_VALUE) {
            builder.append("\nexit: ").append(exitCode)
        }
        if (stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            builder.append("\n").append(stdout.trim { it <= ' ' })
        }
        if (stderr != null && stderr.trim { it <= ' ' }.length > 0) {
            builder.append("\nstderr:\n").append(stderr.trim { it <= ' ' })
        }
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            builder.append("\nerror: ").append(error.trim { it <= ' ' })
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            builder.append("\ndebug: ").append(debug.trim { it <= ' ' })
        }
        Tuils.sendOutput(mContext, builder.toString(), TerminalManager.CATEGORY_OUTPUT)
    }

    private fun updateFileConsoleFromTermux(path: String?, error: String?) {
        if (fileOverlay == null || fileOverlay!!.getVisibility() != View.VISIBLE || mainPack == null || mainPack!!.currentDirectory == null) {
            return
        }
        val current = mainPack!!.currentDirectory.getAbsolutePath()
        if (current != path) {
            return
        }
        if (filePath != null) {
            filePath!!.setText(current)
        }
        renderFileConsole(buildFileListing(dirs(path), files(path), error))
    }

    private fun updateModuleFromTermuxResult(
        module: String?,
        stdout: String?,
        stderr: String?,
        error: String?,
        exitCode: Int
    ) {
        val text: String?
        if (!TextUtils.isEmpty(stdout) && stdout!!.trim { it <= ' ' }.length > 0) {
            text = stdout.trim { it <= ' ' }
        } else if (!TextUtils.isEmpty(stderr) && stderr!!.trim { it <= ' ' }.length > 0) {
            text = "stderr:\n" + stderr.trim { it <= ' ' }
        } else if (!TextUtils.isEmpty(error) && error!!.trim { it <= ' ' }.length > 0) {
            text = "error: " + error.trim { it <= ' ' }
        } else if (exitCode != Int.Companion.MIN_VALUE) {
            text = "exit: " + exitCode + "\nNo output returned."
        } else {
            text = "No output returned."
        }

        val id = ModuleManager.normalize(module)
        ModuleManager.setScriptText(mContext, id, text)
        if (id == activeModule) {
            showHomeModule(id)
        }
        updateModuleDockSelection()
        Tuils.sendOutput(mContext, "Module refreshed: " + id)
    }

    private fun appendTermuxCallbackHint(stdout: String?) {
        if (stdout == null) {
            return
        }

        val lower = stdout.lowercase()
        if (!lower.contains("retui_callback") && !lower.contains("broadcasting: intent")) {
            return
        }

        if (!lower.contains("-p com.dvil.tui_renewed") && !lower.contains("pkg=com.dvil.tui_renewed")) {
            appendTermuxLine("callback hint: if the module did not appear, add this to the script broadcast:")
            appendTermuxLine("  -p com.dvil.tui_renewed")
        }
    }

    private fun openTermuxApp() {
        val launchIntent = mContext!!.getPackageManager()
            .getLaunchIntentForPackage(TermuxBridgeManager.TERMUX_PACKAGE)
        if (launchIntent == null) {
            appendTermuxLine("Termux is not installed.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            mContext!!.startActivity(launchIntent)
            appendTermuxLine("opened Termux.")
        } catch (e: Exception) {
            appendTermuxLine("unable to open Termux: " + e.javaClass.getSimpleName())
        }
    }

    private fun appendTermuxLine(line: String?) {
        if (termuxBuffer.length > 0) {
            termuxBuffer.append(Tuils.NEWLINE)
        }
        termuxBuffer.append(line)
        updateTermuxOutput()
    }

    private fun showPlainTermuxOutput() {
        if (termuxRichOutput != null) {
            termuxRichOutput!!.removeAllViews()
            termuxRichOutput!!.visibility = View.GONE
        }
        if (termuxGrid != null) {
            termuxGrid!!.visibility = View.GONE
        }
        if (termuxOutput != null) {
            termuxOutput!!.visibility = View.VISIBLE
        }
    }

    private fun showTermuxAppGridOutput() {
        if (termuxRichOutput != null) {
            termuxRichOutput!!.removeAllViews()
            termuxRichOutput!!.visibility = View.GONE
        }
        if (termuxOutput != null) {
            termuxOutput!!.visibility = View.GONE
        }
        if (termuxGrid != null) {
            termuxGrid!!.visibility = View.VISIBLE
        }
    }

    private fun showRichTermuxOutput() {
        if (termuxOutput != null) {
            termuxOutput!!.visibility = View.GONE
        }
        if (termuxGrid != null) {
            termuxGrid!!.visibility = View.GONE
        }
        if (termuxRichOutput != null) {
            termuxRichOutput!!.removeAllViews()
            termuxRichOutput!!.visibility = View.VISIBLE
        }
    }

    private fun updateTermuxOutput() {
        val shouldKeepInputFocus = this.isTermuxConsoleVisible && termuxInput != null && termuxInput!!.hasFocus()
        showPlainTermuxOutput()
        if (termuxOutput != null) {
            termuxOutput!!.setText(termuxBuffer.toString())
        }
        scrollTermuxOutputToBottom(shouldKeepInputFocus)
    }

    private fun scrollTermuxOutputToBottom(shouldKeepInputFocus: Boolean) {
        if (termuxScroll != null) {
            termuxScroll!!.post(Runnable {
                if (termuxScroll == null) {
                    return@Runnable
                }
                val scrollChild = termuxScroll!!.getChildAt(0)
                if (scrollChild != null) {
                    termuxScroll!!.scrollTo(0, scrollChild.getBottom())
                }
                restoreTermuxInputFocusAfterOutputUpdate(shouldKeepInputFocus)
            })
        } else {
            restoreTermuxInputFocusAfterOutputUpdate(shouldKeepInputFocus)
        }
    }

    private fun scrollTermuxOutputToTop(shouldKeepInputFocus: Boolean) {
        if (termuxScroll != null) {
            termuxScroll!!.post(Runnable {
                if (termuxScroll == null) {
                    return@Runnable
                }
                termuxScroll!!.scrollTo(0, 0)
                restoreTermuxInputFocusAfterOutputUpdate(shouldKeepInputFocus)
            })
        } else {
            restoreTermuxInputFocusAfterOutputUpdate(shouldKeepInputFocus)
        }
    }

    private fun restoreTermuxInputFocusAfterOutputUpdate(shouldKeepInputFocus: Boolean) {
        if (!shouldKeepInputFocus || !this.isTermuxConsoleVisible || termuxInput == null || termuxInput!!.hasFocus()) {
            return
        }
        termuxInput!!.setShowSoftInputOnFocus(true)
        termuxInput!!.setCursorVisible(true)
        termuxInput!!.requestFocusFromTouch()
        termuxInput!!.requestFocus()
    }

    private fun styleClockOverlay(rootView: View) {
        val timerTab = rootView.findViewById<TextView?>(R.id.timer_tab)
        val stopwatchTab = rootView.findViewById<TextView?>(R.id.stopwatch_tab)

        styleClockTab(timerTab, View.OnClickListener { v: View? ->
            val message = ClockManager.getInstance(mContext).stopTimer()
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT)
        })
        styleClockTab(stopwatchTab, View.OnClickListener { v: View? ->
            val message = ClockManager.getInstance(mContext).stopStopwatch()
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT)
        })
    }

    private fun styleClockTab(tab: TextView?, listener: View.OnClickListener?) {
        if (tab == null) {
            return
        }

        val borderColor = terminalBorderColor()
        val bgColor = terminalHeaderBackground()
        val useDashed = dashedBorders()

        tab.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                bgColor,
                borderColor,
                1.4f,
                3,
                useDashed
            )
        )
        tab.setTextColor(borderColor)
        TextViewCompat.setCompoundDrawableTintList(tab, ColorStateList.valueOf(borderColor))
        tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        tab.setOnClickListener(listener)
    }

    private fun makeClockTabDockable(
        tab: TextView?,
        edgeKey: String?,
        fractionKey: String?,
        defaultEdge: String?,
        defaultFraction: Float
    ) {
        if (tab == null) {
            return
        }

        val touchSlop = ViewConfiguration.get(mContext!!).getScaledTouchSlop()
        val downRawX = FloatArray(1)
        val downRawY = FloatArray(1)
        val startX = FloatArray(1)
        val startY = FloatArray(1)
        val dragging = BooleanArray(1)

        tab.setOnTouchListener(OnTouchListener { view: View?, event: MotionEvent? ->
            val parent = view!!.getParent() as View?
            if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
                false
            } else when (event!!.getActionMasked()) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX[0] = event.getRawX()
                    downRawY[0] = event.getRawY()
                    startX[0] = view.getX()
                    startY[0] = view.getY()
                    dragging[0] = false
                    view.getParent().requestDisallowInterceptTouchEvent(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.getRawX() - downRawX[0]
                    val dy = event.getRawY() - downRawY[0]
                    if (!dragging[0] && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging[0] = true
                    }
                    if (dragging[0]) {
                        setClockTabPosition(view, startX[0] + dx, startY[0] + dy, parent)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    view.getParent().requestDisallowInterceptTouchEvent(false)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    view.getParent().requestDisallowInterceptTouchEvent(false)
                    if (dragging[0]) {
                        snapClockTabToNearestEdge(view, parent, edgeKey, fractionKey)
                    } else {
                        view.performClick()
                    }
                    true
                }

                else -> true
            }
        })

        tab.post(Runnable {
            applyClockTabDock(
                tab,
                edgeKey,
                fractionKey,
                defaultEdge,
                defaultFraction
            )
        })
    }

    private fun applyClockTabDock(
        view: View,
        edgeKey: String?,
        fractionKey: String?,
        defaultEdge: String?,
        defaultFraction: Float
    ) {
        val parent = view.getParent() as View?
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0 || view.getWidth() <= 0 || view.getHeight() <= 0) {
            view.post(Runnable {
                applyClockTabDock(
                    view,
                    edgeKey,
                    fractionKey,
                    defaultEdge,
                    defaultFraction
                )
            })
            return
        }

        val edge =
            if (preferences != null) preferences!!.getString(edgeKey, defaultEdge) else defaultEdge
        var fraction = if (preferences != null) preferences!!.getFloat(
            fractionKey,
            defaultFraction
        ) else defaultFraction
        fraction = max(0f, min(1f, fraction))

        val margin = Tuils.dpToPx(mContext, 6)
        val maxX = max(margin, parent.getWidth() - view.getWidth() - margin).toFloat()
        val maxY = max(margin, parent.getHeight() - view.getHeight() - margin).toFloat()
        val x: Float
        val y: Float

        if (CLOCK_EDGE_LEFT == edge) {
            x = margin.toFloat()
            y = margin + fraction * max(0f, maxY - margin)
        } else if (CLOCK_EDGE_TOP == edge) {
            x = margin + fraction * max(0f, maxX - margin)
            y = margin.toFloat()
        } else if (CLOCK_EDGE_BOTTOM == edge) {
            x = margin + fraction * max(0f, maxX - margin)
            y = maxY
        } else {
            x = maxX
            y = margin + fraction * max(0f, maxY - margin)
        }

        setClockTabPosition(view, x, y, parent)
    }

    private fun setClockTabPosition(view: View, x: Float, y: Float, parent: View) {
        val margin = Tuils.dpToPx(mContext, 6)
        val maxX = max(margin, parent.getWidth() - view.getWidth() - margin).toFloat()
        val maxY = max(margin, parent.getHeight() - view.getHeight() - margin).toFloat()
        view.setX(max(margin.toFloat(), min(x, maxX)))
        view.setY(max(margin.toFloat(), min(y, maxY)))
    }

    private fun snapClockTabToNearestEdge(
        view: View,
        parent: View,
        edgeKey: String?,
        fractionKey: String?
    ) {
        val margin = Tuils.dpToPx(mContext, 6)
        val leftDistance = view.getX()
        val topDistance = view.getY()
        val rightDistance = parent.getWidth() - (view.getX() + view.getWidth())
        val bottomDistance = parent.getHeight() - (view.getY() + view.getHeight())

        var edge: String = CLOCK_EDGE_LEFT
        var nearest = leftDistance
        if (rightDistance < nearest) {
            nearest = rightDistance
            edge = CLOCK_EDGE_RIGHT
        }
        if (topDistance < nearest) {
            nearest = topDistance
            edge = CLOCK_EDGE_TOP
        }
        if (bottomDistance < nearest) {
            edge = CLOCK_EDGE_BOTTOM
        }

        val maxX = max(margin, parent.getWidth() - view.getWidth() - margin).toFloat()
        val maxY = max(margin, parent.getHeight() - view.getHeight() - margin).toFloat()
        var fraction: Float
        if (CLOCK_EDGE_TOP == edge || CLOCK_EDGE_BOTTOM == edge) {
            fraction = (view.getX() - margin) / max(1f, maxX - margin)
        } else {
            fraction = (view.getY() - margin) / max(1f, maxY - margin)
        }
        fraction = max(0f, min(1f, fraction))

        if (preferences != null) {
            preferences!!.edit()
                .putString(edgeKey, edge)
                .putFloat(fractionKey, fraction)
                .apply()
        }
        applyClockTabDock(view, edgeKey, fractionKey, edge, fraction)
    }

    private fun updateClockOverlay(intent: Intent) {
        val timerTab = mRootView!!.findViewById<TextView?>(R.id.timer_tab)
        val stopwatchTab = mRootView.findViewById<TextView?>(R.id.stopwatch_tab)
        if (timerTab == null || stopwatchTab == null) {
            return
        }

        styleClockOverlay(mRootView)

        val timerRunning = intent.getBooleanExtra(ClockManager.EXTRA_TIMER_RUNNING, false)
        val timerRemaining = intent.getLongExtra(ClockManager.EXTRA_TIMER_REMAINING, 0L)
        val stopwatchRunning = intent.getBooleanExtra(ClockManager.EXTRA_STOPWATCH_RUNNING, false)
        val stopwatchElapsed = intent.getLongExtra(ClockManager.EXTRA_STOPWATCH_ELAPSED, 0L)

        timerTabVisible = timerRunning
        stopwatchTabVisible = stopwatchRunning

        if (timerRunning) {
            timerTab.setVisibility(View.VISIBLE)
            timerTab.setText(ClockManager.formatDuration(timerRemaining))
            if (!timerTabDockReady) {
                makeClockTabDockable(
                    timerTab,
                    PREF_TIMER_BADGE_EDGE,
                    PREF_TIMER_BADGE_FRACTION,
                    CLOCK_EDGE_RIGHT,
                    0.45f
                )
                timerTabDockReady = true
            }
        } else {
            timerTab.setVisibility(View.GONE)
        }

        if (stopwatchRunning) {
            stopwatchTab.setVisibility(View.VISIBLE)
            stopwatchTab.setText(ClockManager.formatDuration(stopwatchElapsed))
            if (!stopwatchTabDockReady) {
                makeClockTabDockable(
                    stopwatchTab,
                    PREF_STOPWATCH_BADGE_EDGE,
                    PREF_STOPWATCH_BADGE_FRACTION,
                    CLOCK_EDGE_RIGHT,
                    0.55f
                )
                stopwatchTabDockReady = true
            }
        } else {
            stopwatchTab.setVisibility(View.GONE)
        }
    }

    private fun updatePomodoroOverlay(intent: Intent) {
        val running = intent.getBooleanExtra(PomodoroManager.EXTRA_POMODORO_RUNNING, false)
        val remaining = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_REMAINING, 0L)
        val total = intent.getLongExtra(PomodoroManager.EXTRA_POMODORO_TOTAL, 0L)
        val task = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TASK)
        val typeStr = intent.getStringExtra(PomodoroManager.EXTRA_POMODORO_TYPE)
        val message = intent.getStringExtra(PomodoroManager.EXTRA_MESSAGE)

        var overlay = mRootView!!.findViewById<View?>(R.id.pomodoro_root)
        if (overlay == null) {
            if (!running) return
            overlay = View.inflate(mContext, R.layout.pomodoro_overlay, mRootView as ViewGroup)
            setupPomodoroOverlay(overlay)
        }

        if (!running) {
            (mRootView as ViewGroup).removeView(overlay)
            this.isPomodoroOverlayVisible = false
            mRootView.findViewById<View?>(R.id.main_container).setVisibility(View.VISIBLE)
            val terminalTray = mRootView.findViewById<View?>(R.id.terminal_tray_container)
            if (terminalTray != null) {
                terminalTray.setVisibility(View.VISIBLE)
            }
            if (message != null) {
                Tuils.sendOutput(mContext, message)
            }
            return
        }

        this.isPomodoroOverlayVisible = true
        closeKeyboard()
        mRootView.findViewById<View?>(R.id.main_container).setVisibility(View.GONE)
        val terminalTray = mRootView.findViewById<View?>(R.id.terminal_tray_container)
        if (terminalTray != null) {
            terminalTray.setVisibility(View.GONE)
        }
        overlay.bringToFront()
        overlay.setElevation(Tuils.dpToPx(mContext, 128).toFloat())

        val title = overlay.findViewById<TextView>(R.id.pomodoro_title)
        val countdown = overlay.findViewById<TextView>(R.id.pomodoro_countdown)
        val taskDisplay = overlay.findViewById<TextView>(R.id.pomodoro_task_display)
        val terminateBtn = overlay.findViewById<Button>(R.id.pomodoro_terminate)

        val type = SessionType.valueOf(typeStr!!)

        overlay.setKeepScreenOn(running && type == SessionType.FOCUS)

        if (type == SessionType.FINISHED) {
            title.setText("MISSION ACCOMPLISHED")
            title.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
            taskDisplay.setText("Good job! You did great!")
            countdown.setVisibility(View.GONE)
            terminateBtn.setText("EXIT SESSION")
        } else {
            countdown.setVisibility(View.VISIBLE)
            terminateBtn.setText("TERMINATE SESSION")
            if (type == SessionType.BREAK) {
                title.setText("TAKE A BREAK")
                title.setTextColor(XMLPrefsManager.getColor(Theme.input_text_color))
            } else {
                title.setText("FOCUS MODE ACTIVE")
                title.setTextColor(Color.RED)
            }
            taskDisplay.setText("Task: " + task)
            countdown.setText(ClockManager.formatDuration(remaining))
        }
    }

    private fun setupPomodoroOverlay(overlay: View) {
        val title = overlay.findViewById<TextView>(R.id.pomodoro_title)
        val countdown = overlay.findViewById<TextView>(R.id.pomodoro_countdown)
        val taskDisplay = overlay.findViewById<TextView>(R.id.pomodoro_task_display)
        val terminateBtn = overlay.findViewById<Button>(R.id.pomodoro_terminate)

        val color = XMLPrefsManager.getColor(Theme.input_text_color)
        val bgColor: Int
        val textBgColor = ColorUtils.setAlphaComponent(Color.BLACK, 160)
        if (XMLPrefsManager.getBoolean(Ui.system_wallpaper)) {
            bgColor = XMLPrefsManager.getColor(Theme.wallpaper_overlay_color)
        } else {
            bgColor = XMLPrefsManager.getColor(Theme.background_color)
        }
        overlay.setBackgroundColor(bgColor)

        title.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        countdown.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        taskDisplay.setTypeface(Tuils.getTypeface(mContext))
        terminateBtn.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)

        countdown.setTextColor(color)
        taskDisplay.setTextColor(color)
        terminateBtn.setTextColor(color)

        title.setBackgroundColor(textBgColor)
        countdown.setBackgroundColor(textBgColor)
        taskDisplay.setBackgroundColor(textBgColor)

        terminateBtn.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                textBgColor,
                color,
                1.4f,
                moduleCornerRadius(),
                dashedBorders()
            )
        )

        terminateBtn.setOnClickListener(View.OnClickListener { v: View? ->
            val manager = PomodoroManager.getInstance(mContext)
            if (manager.currentType == SessionType.FINISHED) {
                manager.stopSession()
            } else {
                TuixtDialog.showConfirm(
                    mContext,
                    "TERMINATE",
                    "Do you really want to stop the focus session?",
                    "YES",
                    "NO",
                    ConfirmAction {
                        manager.stopSession()
                    })
            }
        })
    }

    private fun playHackOverlay() {
        val overlay = mRootView!!.findViewById<View?>(R.id.hack_overlay)
        val hackText = mRootView.findViewById<TextView?>(R.id.hack_text)
        val hackScroll = mRootView.findViewById<ScrollView?>(R.id.hack_scroll)
        if (overlay == null || hackText == null || hackScroll == null || handler == null) {
            return
        }

        closeKeyboard()
        styleHackOverlay(mRootView)
        clearHackCallbacks()

        hackText.setText(":: breach protocol engaged ::\n\n")
        overlay.setAlpha(0f)
        overlay.setVisibility(View.VISIBLE)
        overlay.animate().alpha(1f).setDuration(120).start()

        for (i in hackLines.indices) {
            val line = hackLines[i]
            val delay = 120 + (i * 55)
            val lineRunnable = Runnable {
                hackText.append(line)
                hackText.append(Tuils.NEWLINE)
                hackScroll.post(Runnable { hackScroll.fullScroll(View.FOCUS_DOWN) })
            }
            hackSequenceRunnables.add(lineRunnable)
            handler!!.postDelayed(lineRunnable, delay.toLong())
        }

        val exitRunnable = Runnable {
            hackText.append(Tuils.NEWLINE + "[EXIT] connection severed")
            hackScroll.post(Runnable { hackScroll.fullScroll(View.FOCUS_DOWN) })
        }
        hackSequenceRunnables.add(exitRunnable)
        handler!!.postDelayed(exitRunnable, (120 + (hackLines.size * 55)).toLong())

        val fadeRunnable = Runnable {
            overlay.animate().alpha(0f).setDuration(180).withEndAction(
                Runnable {
                    overlay.setVisibility(View.GONE)
                    overlay.setAlpha(1f)
                }).start()
        }
        hackSequenceRunnables.add(fadeRunnable)
        handler!!.postDelayed(fadeRunnable, 5200)
    }

    private fun clearHackCallbacks() {
        if (handler == null) {
            return
        }

        for (runnable in hackSequenceRunnables) {
            handler!!.removeCallbacks(runnable)
        }
        hackSequenceRunnables.clear()
        handler!!.removeCallbacks(hackHideRunnable)
    }

    private fun dismissHackOverlay() {
        clearHackCallbacks()
        hackHideRunnable.run()
    }

    private fun styleNotificationWidget(notificationWidget: View) {
        decorateWidget(
            notificationWidget,
            R.id.notification_widget_border,
            R.id.notification_widget_label,
            R.id.notification_widget_close,
            notificationWidgetBorderColor(),
            notificationWidgetTextColor()
        )
        styleModuleClose(notificationWidget.findViewById<TextView?>(R.id.notification_widget_close))
        styleNotificationPagerButton(notificationWidget.findViewById<View?>(R.id.notification_widget_prev))
        styleNotificationPagerButton(notificationWidget.findViewById<View?>(R.id.notification_widget_next))
        applyNotificationWidgetSize(notificationWidget)
        renderNotificationRows(notificationWidget)
    }

    private fun updateNotificationWidget(
        rootView: View,
        notifications: MutableList<NotificationService.Notification?>?
    ) {
        var previousFocusKey = notificationReplyFocusKey
        if (ModulePromptManager.isNotificationReplyActive(mContext) && TextUtils.isEmpty(
                previousFocusKey
            )
        ) {
            val selected = currentNotification()
            previousFocusKey = notificationKey(selected)
        }

        currentOverlayNotifications.clear()
        if (notifications != null) {
            currentOverlayNotifications.addAll(notifications.filterNotNull())
        }
        preserveNotificationReplyFocus(previousFocusKey)
        clampNotificationIndex()

        val notificationWidget = rootView.findViewById<View?>(R.id.notification_widget)
        if (notificationWidget != null) {
            val visible = ModuleManager.NOTIFICATIONS == activeModule
            notificationWidget.setVisibility(if (visible) View.VISIBLE else View.GONE)
            if (visible) {
                styleNotificationWidget(notificationWidget)
            }
        }
        updateContextContainerVisibility(rootView)
    }

    private fun renderNotificationRows(notificationWidget: View) {
        val rows = notificationWidget.findViewById<LinearLayout?>(R.id.notification_rows)
        val scrollView = notificationWidget.findViewById<ScrollView?>(R.id.notification_scroll)
        if (rows == null) {
            return
        }

        rows.removeAllViews()
        val widgetTextColor = notificationWidgetTextColor()
        val widgetBorderColor = notificationWidgetBorderColor()

        val maxRows = if (notificationCompactForKeyboard) min(
            1,
            currentOverlayNotifications.size
        ) else currentOverlayNotifications.size
        if (maxRows == 0) {
            val row = buildNotificationRow("No notifications.", widgetTextColor, widgetBorderColor)
            rows.addView(row)
            updateNotificationPagerButtons(notificationWidget)
            constrainNotificationContentScroll(scrollView)
            if (scrollView != null) {
                scrollView.post(Runnable { scrollView.scrollTo(0, 0) })
            }
            return
        }
        if (ModuleManager.NOTIFICATIONS == activeModule) {
            clampNotificationIndex()
            val notification = currentOverlayNotifications.get(currentNotificationIndex)
            val row = buildNotificationDetailRow(notification, widgetTextColor, widgetBorderColor)
            wireNotificationOpen(row, notification)
            rows.addView(row)
        } else {
            for (i in 0..<maxRows) {
                val notification = currentOverlayNotifications.get(i)
                val row = buildNotificationRow(
                    buildNotificationLine(notification),
                    widgetTextColor,
                    widgetBorderColor
                )
                wireNotificationOpen(row, notification)
                rows.addView(row)
            }
        }
        updateNotificationPagerButtons(notificationWidget)
        constrainNotificationContentScroll(scrollView)

        if (scrollView != null) {
            scrollView.post(Runnable { scrollView.fullScroll(View.FOCUS_UP) })
        }
    }

    private fun wireNotificationOpen(
        row: TextView?,
        notification: NotificationService.Notification?
    ) {
        if (row == null || notification == null || notification.pendingIntent == null) {
            return
        }
        row.setClickable(true)
        row.setFocusable(true)
        row.setOnClickListener(View.OnClickListener { v: View? ->
            try {
                Tuils.sendPendingIntent(mContext, notification.pendingIntent)
            } catch (e: CanceledException) {
                Tuils.sendOutput(Color.RED, mContext, e.toString())
            }
        })
    }

    private fun buildNotificationRow(
        text: CharSequence?,
        widgetTextColor: Int,
        widgetBorderColor: Int
    ): TextView {
        val row = TextView(mContext)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = if (notificationCompactForKeyboard) 0 else Tuils.dpToPx(mContext, 6)
        row.setLayoutParams(lp)
        row.setTypeface(Tuils.getTypeface(mContext))
        row.setTextSize(moduleBodyTextSize().toFloat())
        row.setSingleLine(true)
        row.setEllipsize(TextUtils.TruncateAt.END)
        row.setGravity(Gravity.CENTER_VERTICAL)
        val verticalPadding = Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 5 else 8)
        row.setPadding(
            Tuils.dpToPx(mContext, 10),
            verticalPadding,
            Tuils.dpToPx(mContext, 10),
            verticalPadding
        )
        row.setTextColor(widgetTextColor)
        row.setText(text)
        row.setBackground(TuiWidgetDecorator.getRowBackground(mContext!!, widgetBorderColor))
        return row
    }

    private fun buildNotificationDetailRow(
        notification: NotificationService.Notification,
        widgetTextColor: Int,
        widgetBorderColor: Int
    ): TextView {
        val row = TextView(mContext)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        row.setLayoutParams(lp)
        row.setTypeface(Tuils.getTypeface(mContext))
        row.setTextSize(moduleBodyTextSize().toFloat())
        row.setTextColor(widgetTextColor)
        row.setText(buildNotificationDetail(notification))
        row.setSingleLine(false)
        row.setEllipsize(null)
        row.setGravity(Gravity.TOP)
        row.setMinLines(if (notificationCompactForKeyboard) 1 else 3)
        row.setPadding(
            Tuils.dpToPx(mContext, 6),
            Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 5 else 10),
            Tuils.dpToPx(mContext, 6),
            Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 5 else 10)
        )
        return row
    }

    private fun buildNotificationDetail(notification: NotificationService.Notification): CharSequence {
        var appName = notification.appName
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg
        }
        val title = cleanNotificationValue(notification.title)
        val body = cleanNotificationValue(notification.body)
        var fallback = cleanNotificationValue(notification.preview)
        if (TextUtils.isEmpty(fallback)) {
            fallback = cleanNotificationValue(notification.text)
        }

        val out = StringBuilder()
        out.append(currentNotificationIndex + 1)
            .append(" / ")
            .append(currentOverlayNotifications.size)
            .append("    ")
            .append(if (appName != null) appName else "Notification")
        if (!TextUtils.isEmpty(title)) {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append(title)
        }
        if (!TextUtils.isEmpty(body)) {
            out.append(Tuils.NEWLINE).append(body)
        } else if (!TextUtils.isEmpty(fallback)) {
            out.append(Tuils.NEWLINE).append(fallback)
        } else {
            out.append(Tuils.NEWLINE).append(Tuils.NEWLINE).append("No readable content")
        }
        if (this.isCurrentNotificationReplyable) {
            out.append(Tuils.NEWLINE).append("reply available")
        }
        return out.toString()
    }

    private fun cleanNotificationValue(value: String?): String {
        if (value == null) {
            return Tuils.EMPTYSTRING
        }
        var clean = value.trim { it <= ' ' }
        if (clean.length == 0 || "null".equals(clean, ignoreCase = true)) {
            return Tuils.EMPTYSTRING
        }
        if (clean.contains("%pkg") || clean.contains("%t") || clean.contains("--- null")) {
            return Tuils.EMPTYSTRING
        }
        clean = clean.replace("(?i)\\bnull\\b".toRegex(), "").replace("\\s+---\\s*$".toRegex(), "")
            .trim { it <= ' ' }
        return clean
    }

    private fun buildNotificationLine(notification: NotificationService.Notification): CharSequence {
        var appName = notification.appName
        if (TextUtils.isEmpty(appName)) {
            appName = notification.pkg
        }
        var preview = notification.preview
        if (TextUtils.isEmpty(preview)) {
            preview = notification.text
        }
        return (if (appName != null) appName else "Notification") + "  " + (if (preview != null) preview else Tuils.EMPTYSTRING)
    }

    private fun setNotificationWidgetCompact(rootView: View, compact: Boolean) {
        if (notificationCompactForKeyboard == compact) {
            return
        }

        notificationCompactForKeyboard = compact
        val notificationWidget = rootView.findViewById<View?>(R.id.notification_widget)
        if (notificationWidget != null && notificationWidget.getVisibility() == View.VISIBLE) {
            applyNotificationWidgetSize(notificationWidget)
            renderNotificationRows(notificationWidget)
        }
    }

    private fun applyNotificationWidgetSize(notificationWidget: View) {
        val border = notificationWidget.findViewById<View?>(R.id.notification_widget_border)
        if (border != null) {
            val lp = border.getLayoutParams()
            if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                border.setLayoutParams(lp)
            }
            border.setMinimumHeight(calculateNotificationWidgetMinHeight())
        }

        val scrollView = notificationWidget.findViewById<ScrollView?>(R.id.notification_scroll)
        if (scrollView != null) {
            val lp = scrollView.getLayoutParams()
            if (lp != null && lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                scrollView.setLayoutParams(lp)
            }
            scrollView.setFillViewport(false)
        }

        notificationWidget.setPadding(
            notificationWidget.getPaddingLeft(),
            notificationWidget.getPaddingTop(),
            notificationWidget.getPaddingRight(),
            Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 6 else 12)
        )
    }

    private fun calculateNotificationWidgetMinHeight(): Int {
        return Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 104 else 112)
    }

    private fun calculateNotificationContentMaxHeight(): Int {
        val rootHeight = if (mRootView != null) mRootView.getHeight() else 0
        val fallbackMax = Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 92 else 180)
        val floor = Tuils.dpToPx(mContext, if (notificationCompactForKeyboard) 58 else 96)
        if (rootHeight <= 0) {
            return fallbackMax
        }
        val modulePercent = if (notificationCompactForKeyboard) 0.16f else 0.28f
        val adaptiveMax = Math.round(rootHeight * modulePercent)
        return min(fallbackMax, max(floor, adaptiveMax))
    }

    private fun constrainNotificationContentScroll(scrollView: ScrollView?) {
        if (scrollView == null) {
            return
        }

        scrollView.post(Runnable {
            val content = scrollView.getChildAt(0)
            if (content != null) {
                val contentHeight =
                    content.getHeight() + scrollView.getPaddingTop() + scrollView.getPaddingBottom()
                if (contentHeight > 0) {
                    val maxHeight = calculateNotificationContentMaxHeight()
                    val targetHeight =
                        if (contentHeight > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
                    val lp = scrollView.getLayoutParams()
                    if (lp != null && lp.height != targetHeight) {
                        lp.height = targetHeight
                        scrollView.setLayoutParams(lp)
                    }
                    scrollView.setFillViewport(targetHeight != ViewGroup.LayoutParams.WRAP_CONTENT)
                    scrollView.setVerticalScrollBarEnabled(contentHeight > maxHeight)
                }
            }
        })
    }

    private fun styleNotificationPagerButton(button: View?) {
        if (button !is TextView) return
        val text = button
        text.setTextColor(moduleNameTextColor())
        text.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        text.setTextSize(moduleBodyTextSize().toFloat())
        text.setBackground(
            TuiWidgetDecorator.getRowBackground(
                mContext!!,
                notificationWidgetBorderColor()
            )
        )
    }

    private fun clampNotificationIndex() {
        if (currentOverlayNotifications.isEmpty()) {
            currentNotificationIndex = 0
            return
        }
        if (currentNotificationIndex < 0) {
            currentNotificationIndex = 0
        } else if (currentNotificationIndex >= currentOverlayNotifications.size) {
            currentNotificationIndex = currentOverlayNotifications.size - 1
        }
    }

    private fun preserveNotificationReplyFocus(focusKey: String?) {
        if (!ModulePromptManager.isNotificationReplyActive(mContext)) {
            notificationReplyFocusKey = null
            return
        }

        if (!TextUtils.isEmpty(focusKey)) {
            val index = findNotificationIndexByKey(focusKey)
            if (index >= 0) {
                currentNotificationIndex = index
                notificationReplyFocusKey = focusKey
                return
            }
        }

        val pkg = ModulePromptManager.getNotificationReplyPackage(mContext)
        val packageIndex = findNotificationIndexByPackage(pkg)
        if (packageIndex >= 0) {
            currentNotificationIndex = packageIndex
            notificationReplyFocusKey =
                notificationKey(currentOverlayNotifications.get(packageIndex))
        }
    }

    private fun findNotificationIndexByKey(key: String?): Int {
        if (TextUtils.isEmpty(key)) return -1
        for (i in currentOverlayNotifications.indices) {
            if (TextUtils.equals(key, notificationKey(currentOverlayNotifications.get(i)))) {
                return i
            }
        }
        return -1
    }

    private fun findNotificationIndexByPackage(pkg: String?): Int {
        if (TextUtils.isEmpty(pkg)) return -1
        for (i in currentOverlayNotifications.indices) {
            if (TextUtils.equals(pkg, currentOverlayNotifications.get(i).pkg)) {
                return i
            }
        }
        return -1
    }

    private fun notificationKey(notification: NotificationService.Notification?): String? {
        if (notification == null) return null
        return (safeNotificationPart(notification.pkg)
                + "|"
                + safeNotificationPart(notification.title)
                + "|"
                + safeNotificationPart(notification.body)
                + "|"
                + safeNotificationPart(notification.preview))
    }

    private fun safeNotificationPart(value: String?): String {
        return if (value == null) "" else value.trim { it <= ' ' }
    }

    private fun updateNotificationPagerButtons(notificationWidget: View?) {
        if (notificationWidget == null) return
        val replyActive = ModulePromptManager.isNotificationReplyActive(mContext)
        val enabled =
            ModuleManager.NOTIFICATIONS == activeModule && currentOverlayNotifications.size > 1 && !replyActive
        val prev = notificationWidget.findViewById<TextView?>(R.id.notification_widget_prev)
        val next = notificationWidget.findViewById<TextView?>(R.id.notification_widget_next)
        if (prev != null) {
            prev.setVisibility(if (ModuleManager.NOTIFICATIONS == activeModule) View.VISIBLE else View.GONE)
            prev.setEnabled(enabled)
            prev.setAlpha(if (enabled) 1f else 0.35f)
        }
        if (next != null) {
            next.setVisibility(if (ModuleManager.NOTIFICATIONS == activeModule) View.VISIBLE else View.GONE)
            next.setEnabled(enabled)
            next.setAlpha(if (enabled) 1f else 0.35f)
        }
    }

    fun nextNotificationPage() {
        if (currentOverlayNotifications.isEmpty()) return
        if (ModulePromptManager.isNotificationReplyActive(mContext)) return
        currentNotificationIndex = (currentNotificationIndex + 1) % currentOverlayNotifications.size
        refreshNotificationModuleView()
    }

    fun previousNotificationPage() {
        if (currentOverlayNotifications.isEmpty()) return
        if (ModulePromptManager.isNotificationReplyActive(mContext)) return
        currentNotificationIndex =
            (currentNotificationIndex - 1 + currentOverlayNotifications.size) % currentOverlayNotifications.size
        refreshNotificationModuleView()
    }

    fun startCurrentNotificationReply() {
        val notification = currentNotification()
        if (notification == null) {
            Tuils.sendOutput(mContext, "No notification selected.")
            return
        }
        if (!this.isCurrentNotificationReplyable) {
            Tuils.sendOutput(
                mContext,
                "Selected notification is not replyable. Bind the app with reply -bind first."
            )
            return
        }
        notificationReplyFocusKey = notificationKey(notification)
        ModulePromptManager.startNotificationReply(
            mContext,
            notification.pkg ?: return,
            notification.appName
        )
        refreshSuggestionsForActiveModule()
    }

    private val isCurrentNotificationReplyable: Boolean
        get() {
            val notification = currentNotification()
            val replyManager = ReplyManager.getInstance()
            return notification != null && replyManager != null && replyManager.canReplyTo(
                notification.pkg
            )
        }

    private fun currentNotification(): NotificationService.Notification? {
        if (currentOverlayNotifications.isEmpty()) return null
        clampNotificationIndex()
        return currentOverlayNotifications.get(currentNotificationIndex)
    }

    private fun refreshNotificationModuleView() {
        if (ModuleManager.NOTIFICATIONS == activeModule) {
            showHomeModule(ModuleManager.NOTIFICATIONS)
        }
    }

    private fun updateContextContainerVisibility(rootView: View?) {
        // Widgets are now inside terminalContainer in terminalPage
    }

    fun openNotificationShade(): Boolean {
        try {
            @SuppressLint("WrongConstant") val sbservice = mContext!!.getSystemService("statusbar")
            val statusbarManager = Class.forName("android.app.StatusBarManager")
            val expand = statusbarManager.getMethod("expandNotificationsPanel")
            expand.invoke(sbservice)
            return true
        } catch (e: Exception) {
            Tuils.sendOutput(Color.RED, mContext, e.toString())
            return false
        }
    }

    val isAppsDrawerOpen: Boolean
        get() = appsDrawerRoot != null && appsDrawerRoot!!.getVisibility() == View.VISIBLE

    fun hideAppsDrawer() {
        if (appsDrawerRoot != null) {
            appsDrawerRoot!!.setVisibility(View.GONE)
        }
    }

    fun showAppsDrawer() {
        if (appsDrawerRoot == null || appsList == null) return

        closeKeyboard()

        val mainPack = mTerminalAdapter!!.mainPack
        if (mainPack == null || mainPack.appsManager == null) return

        val drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val borderColor = terminalBorderColor()
        val widgetBgColor = terminalWindowBackground()
        val headerBgColor = terminalHeaderTabBackground()

        appsDrawerHeader.setTextColor(drawerColor)
        appsDrawerFooter.setTextColor(drawerColor)
        appsDrawerHeader.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        appsDrawerFooter.setTypeface(Tuils.getTypeface(mContext))

        val useDashed = dashedBorders()
        val drawerPanel = appsDrawerContainer
            ?: appsDrawerRoot!!.findViewById<View?>(R.id.apps_drawer_container)
        drawerPanel?.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                widgetBgColor,
                borderColor,
                1.5f,
                moduleCornerRadius(),
                useDashed
            )
        )
        appsDrawerHeader.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, headerBgColor))
        appsDrawerFooter.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, headerBgColor))
        TerminalBorderRuntime.bind(drawerPanel, appsDrawerHeader, appsDrawerFooter)

        if (appsDrawerAdapter == null) {
            appsDrawerAdapter = AppsDrawerAdapter(mContext!!, drawerColor, widgetBgColor)
            appsList.setAdapter(appsDrawerAdapter)
            appsList.setOnItemClickListener(OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val entry = appsDrawerEntries.get(position)
                if (entry is AppEntry) {
                    val app: LaunchInfo? = entry.app
                    mainPack.appsManager.launch(mContext, app)
                    hideAppsDrawer()
                }
            })
            appsList.setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                }

                override fun onScroll(
                    view: AbsListView?,
                    firstVisibleItem: Int,
                    visibleItemCount: Int,
                    totalItemCount: Int
                ) {
                    updateSelectedAlphaFromPosition(firstVisibleItem)
                }
            })
        } else {
            appsDrawerAdapter!!.setColors(drawerColor, widgetBgColor)
        }

        buildGroupTabs(mainPack.appsManager, drawerColor, borderColor, widgetBgColor)
        rebuildAppsDrawerContents(mainPack.appsManager, drawerColor, borderColor, widgetBgColor)
        appsDrawerRoot!!.setVisibility(View.VISIBLE)
    }

    private fun buildGroupTabs(
        appsManager: AppsManager,
        drawerColor: Int,
        borderColor: Int,
        widgetBgColor: Int
    ) {
        if (appsGroupTabs == null) return

        appsGroupTabs.removeAllViews()

        addGroupTab("ALL", null, drawerColor, borderColor, widgetBgColor, true)

        val groups: MutableList<AppsManager.Group> =
            ArrayList<AppsManager.Group>(appsManager.groups)
        Collections.sort<AppsManager.Group>(
            groups,
            Comparator { a: AppsManager.Group, b: AppsManager.Group ->
                Tuils.alphabeticCompare(
                    a.name(),
                    b.name()
                )
            })
        for (group in groups) {
            val groupName = group.name()
            val tabLabel = if (groupName.length <= 3)
                groupName.uppercase(Locale.getDefault())
            else
                groupName.substring(0, 3).uppercase(Locale.getDefault())
            addGroupTab(tabLabel, groupName, drawerColor, borderColor, widgetBgColor, false)
        }
    }

    private fun addGroupTab(
        label: String?,
        groupName: String?,
        drawerColor: Int,
        borderColor: Int,
        widgetBgColor: Int,
        isAll: Boolean
    ) {
        val tab = TextView(mContext)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Tuils.dpToPx(mContext, 34)
        )
        lp.bottomMargin = Tuils.dpToPx(mContext, 4)
        tab.setLayoutParams(lp)
        tab.setGravity(Gravity.CENTER)
        tab.setPadding(Tuils.dpToPx(mContext, 2), 0, Tuils.dpToPx(mContext, 2), 0)
        tab.setText(label)
        tab.setMaxLines(1)
        tab.setEllipsize(TextUtils.TruncateAt.END)
        tab.setTextSize(9.5f)
        tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
        tab.setMinWidth(0)
        tab.setMinimumWidth(0)

        val selected =
            (isAll && selectedAppsDrawerGroup == null) || (groupName != null && groupName == selectedAppsDrawerGroup)
        val selectedColor = getDrawerSelectionColor(drawerColor, widgetBgColor)
        var fgColor = drawerColor
        var bgColor = widgetBgColor
        if (groupName != null) {
            val group = findAppsGroup(groupName)
            if (group != null) {
                if (group.foreColor != Int.Companion.MAX_VALUE) {
                    fgColor = group.foreColor
                }
                if (group.bgColor != Int.Companion.MAX_VALUE) {
                    bgColor = group.bgColor
                }
            }
        }

        tab.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                if (selected) selectedColor else bgColor,
                borderColor,
                1.5f,
                2,
                dashedBorders()
            )
        )
        tab.setTextColor(if (selected) widgetBgColor else fgColor)
        tab.setAlpha(1f)

        tab.setOnClickListener(View.OnClickListener { v: View? ->
            selectedAppsDrawerGroup = groupName
            buildGroupTabs(mainPack!!.appsManager, drawerColor, borderColor, widgetBgColor)
            rebuildAppsDrawerContents(
                mainPack!!.appsManager,
                drawerColor,
                borderColor,
                widgetBgColor
            )
        })

        appsGroupTabs!!.addView(tab)
    }

    private fun findAppsGroup(name: String?): AppsManager.Group? {
        if (mainPack == null || mainPack!!.appsManager == null) return null
        for (group in mainPack!!.appsManager.groups) {
            if (group.name() == name) {
                return group
            }
        }
        return null
    }

    private fun rebuildAppsDrawerContents(
        appsManager: AppsManager,
        drawerColor: Int,
        borderColor: Int,
        widgetBgColor: Int
    ) {
        val visibleApps = getAppsForDrawer(appsManager)
        appsDrawerEntries.clear()
        appsDrawerAlphaPositions.clear()
        selectedAppsDrawerAlpha = null

        var currentSection: String? = null
        for (app in visibleApps) {
            val section = sectionForApp(app)
            if (section != currentSection) {
                appsDrawerAlphaPositions.put(section, appsDrawerEntries.size)
                appsDrawerEntries.add(SectionEntry(section))
                currentSection = section
            }
            appsDrawerEntries.add(AppEntry(app))
        }

        appsDrawerAdapter!!.notifyDataSetChanged()
        buildAlphabetTabs(drawerColor, borderColor, widgetBgColor)

        val scope = (if (selectedAppsDrawerGroup == null) "all" else selectedAppsDrawerGroup)!!
        appsDrawerHeader.setText("Applications/ [" + visibleApps.size + "] <" + scope + ">")
        appsDrawerFooter.setText("groups " + appsManager.groups.size + " | tabs " + appsDrawerAlphaPositions.size)
        appsList!!.setSelection(0)
        updateSelectedAlphaFromPosition(0)
    }

    private fun getAppsForDrawer(appsManager: AppsManager): MutableList<LaunchInfo> {
        val apps: MutableList<LaunchInfo> = ArrayList<LaunchInfo>()
        val shownApps: List<LaunchInfo> = appsManager.shownApps() ?: emptyList()

        if (selectedAppsDrawerGroup == null) {
            apps.addAll(shownApps)
        } else {
            val group = findAppsGroup(selectedAppsDrawerGroup)
            if (group != null) {
                val members = group.members()
                for (member in members) {
                    if (member is LaunchInfo && shownApps.contains(member)) {
                        apps.add(member)
                    }
                }
            }
        }

        Collections.sort<LaunchInfo>(
            apps,
            Comparator { a: LaunchInfo, b: LaunchInfo ->
                Tuils.alphabeticCompare(
                    a.publicLabel ?: Tuils.EMPTYSTRING,
                    b.publicLabel ?: Tuils.EMPTYSTRING
                )
            })
        return apps
    }

    private fun sectionForApp(app: LaunchInfo?): String {
        val publicLabel = app?.publicLabel
        if (publicLabel.isNullOrEmpty()) {
            return "#"
        }

        val first = publicLabel.get(0).uppercaseChar()
        if (first < 'A' || first > 'Z') {
            return "#"
        }
        return first.toString()
    }

    private fun buildAlphabetTabs(drawerColor: Int, borderColor: Int, widgetBgColor: Int) {
        if (appsAlphaTabs == null) return

        appsAlphaTabs.removeAllViews()
        appsDrawerAlphaViews.clear()
        for (entry in appsDrawerAlphaPositions.entries) {
            val tab = TextView(mContext)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            lp.bottomMargin = Tuils.dpToPx(mContext, 3)
            tab.setLayoutParams(lp)
            tab.setGravity(Gravity.CENTER)
            tab.setMinHeight(0)
            tab.setMinimumHeight(0)
            tab.setPadding(0, 0, 0, 0)
            tab.setText(entry.key)
            tab.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            tab.setTextSize(9.5f)
            styleAlphaTab(tab, entry.key, drawerColor, borderColor, widgetBgColor)
            tab.setOnClickListener(View.OnClickListener { v: View? ->
                appsList!!.setSelection(entry.value!!)
                updateSelectedAlpha(entry.key)
            })
            appsDrawerAlphaViews.put(entry.key, tab)
            appsAlphaTabs.addView(tab)
        }
    }

    private fun styleAlphaTab(
        tab: TextView,
        letter: String?,
        drawerColor: Int,
        borderColor: Int,
        widgetBgColor: Int
    ) {
        val selected = letter != null && letter == selectedAppsDrawerAlpha
        tab.setTextColor(if (selected) widgetBgColor else drawerColor)
        val selectedColor = getDrawerSelectionColor(drawerColor, widgetBgColor)

        tab.setBackground(
            TerminalBorderRuntime.panelDrawable(
                mContext!!,
                if (selected) selectedColor else widgetBgColor,
                borderColor,
                1.2f,
                2,
                dashedBorders()
            )
        )
    }

    private fun getDrawerSelectionColor(drawerColor: Int, widgetBgColor: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(drawerColor, hsv)
        hsv[1] = max(0f, hsv[1] * 0.55f)
        hsv[2] = min(1f, 0.88f + (0.12f * hsv[2]))
        val lightBase = Color.HSVToColor(hsv)
        return ColorUtils.blendARGB(lightBase, widgetBgColor, 0.18f)
    }

    private fun updateSelectedAlphaFromPosition(position: Int) {
        if (position < 0 || position >= appsDrawerEntries.size) {
            return
        }

        for (i in position..<appsDrawerEntries.size) {
            val entry = appsDrawerEntries.get(i)
            if (entry is SectionEntry) {
                updateSelectedAlpha(entry.title)
                return
            }
        }
    }

    private fun updateSelectedAlpha(letter: String?) {
        if (letter == null || letter == selectedAppsDrawerAlpha) {
            return
        }

        selectedAppsDrawerAlpha = letter
        val drawerColor = XMLPrefsManager.getColor(Theme.apps_drawer_text_color)
        val borderColor = terminalBorderColor()
        val widgetBgColor = terminalWindowBackground()
        for (entry in appsDrawerAlphaViews.entries) {
            styleAlphaTab(entry.value!!, entry.key, drawerColor, borderColor, widgetBgColor)
        }
    }

    private abstract class AppDrawerEntry {
        abstract val viewType: Int
    }

    private class SectionEntry(val title: String?) : AppDrawerEntry() {
        override val viewType: Int = 0
    }

    private class AppEntry(val app: LaunchInfo) : AppDrawerEntry() {
        override val viewType: Int = 1
    }

    private class LuaSurfaceAction(val label: String, val run: Runnable)

    private inner class AppsDrawerAdapter(
        private val context: Context,
        private var color: Int,
        private var bgColor: Int
    ) : BaseAdapter() {
        fun setColors(color: Int, bgColor: Int) {
            this.color = color
            this.bgColor = bgColor
        }

        override fun getCount(): Int {
            return appsDrawerEntries.size
        }

        override fun getItem(position: Int): Any? {
            return appsDrawerEntries.get(position)
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun getItemViewType(position: Int): Int {
            return appsDrawerEntries.get(position).viewType
        }

        override fun isEnabled(position: Int): Boolean {
            return getItemViewType(position) == 1
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val entry = appsDrawerEntries.get(position)
            val tv = if (convertView is TextView) convertView else TextView(context)

            if (entry is SectionEntry) {
                tv.setPadding(0, Tuils.dpToPx(context, 8), 0, Tuils.dpToPx(context, 6))
                tv.setTextColor(color)
                tv.setTextSize(12f)
                tv.setTypeface(Tuils.getTypeface(context), Typeface.BOLD)
                tv.setBackgroundColor(Color.TRANSPARENT)
                tv.setText("[" + entry.title + "]")
                return tv
            }

            val app: LaunchInfo = (entry as AppEntry).app
            tv.setPadding(
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12),
                Tuils.dpToPx(context, 6),
                Tuils.dpToPx(context, 12)
            )
            tv.setTextColor(color)
            tv.setTextSize(16f)
            tv.setTypeface(Tuils.getTypeface(context))
            tv.setBackgroundColor(Color.TRANSPARENT)
            tv.setText(app.publicLabel)
            return tv
        }
    }

    fun dispose() {
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }

        if (suggestionsManager != null) suggestionsManager!!.dispose()
        if (notesManager != null) notesManager!!.dispose(mContext)
        LocalBroadcastManager.getInstance(mContext!!.getApplicationContext())
            .unregisterReceiver(receiver)
        try {
            mContext!!.getApplicationContext().unregisterReceiver(receiver)
        } catch (ignored: Exception) {
        }
        Tuils.unregisterBatteryReceiver(mContext)

        Tuils.cancelFont()
    }

    private fun applyRetuiKeyboardTheme(input: EditText?, mode: String) {
        RetuiThemeBridge.applyToKeyboardInput(input, mainPack.currentDirectory.absolutePath, mode)
    }

    private fun sendRetuiKeyboardTheme(input: EditText?, mode: String) {
        RetuiThemeBridge.sendToKeyboard(mContext, input, mainPack.currentDirectory.absolutePath, mode)
    }

    fun openKeyboard() {
        activateTerminalInput(true)
        if (mTerminalAdapter != null) {
            sendRetuiKeyboardTheme(mTerminalAdapter!!.inputView as? EditText, "launcher")
            imm.showSoftInput(mTerminalAdapter!!.inputView, InputMethodManager.SHOW_FORCED)
        }
    }

    fun closeKeyboard() {
        imm.hideSoftInputFromWindow(mTerminalAdapter!!.inputWindowToken, 0)
        if (mTerminalAdapter!!.inputView is EditText) {
            val terminalInput = mTerminalAdapter!!.inputView as EditText
            terminalInput.setCursorVisible(false)
            terminalInput.setShowSoftInputOnFocus(false)
            if (terminalInput is OutlineEditText) {
                terminalInput.setIdleCursorVisible(true)
            }
            terminalInput.clearFocus()
        }
    }

    fun onStart(openKeyboardOnStart: Boolean) {
        activateTerminalInput(openKeyboardOnStart)
    }

    fun setInput(s: String?) {
        if (s == null) return

        if (mTerminalAdapter == null) {
            pendingInputs.add(s)
            return
        }

        mTerminalAdapter!!.setInput(s, null)
        mTerminalAdapter!!.focusInputEnd()
    }

    fun setHint(hint: String?) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.setHint(hint)
        }
    }

    fun resetHint() {
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.setDefaultHint()
        }
    }

    fun setOutput(s: CharSequence?, category: Int) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.setOutput(s, category)
        } else {
            pendingOutputs.add(OutputHolder(s, category))
        }
    }

    fun setOutput(color: Int, output: CharSequence?) {
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.setOutput(color, output)
        } else {
            pendingOutputs.add(OutputHolder(color, output))
        }
    }

    fun disableSuggestions() {
        if (suggestionsManager != null) suggestionsManager!!.disable()
    }

    fun enableSuggestions() {
        if (suggestionsManager != null) {
            suggestionsManager!!.enable()
            refreshSuggestionsSoon()
        }
    }

    fun refreshSuggestions() {
        runOnUiThread(Runnable { refreshSuggestionsNow() })
    }

    fun refreshSuggestionsSoon() {
        postOnUiThread(Runnable { refreshSuggestionsNow() })
    }

    fun scrollTerminalToEndSoon() {
        postOnUiThread(Runnable {
            if (mTerminalAdapter != null) {
                mTerminalAdapter!!.scrollToEnd()
            }
        })
    }

    private fun refreshSuggestionsNow() {
        if (suggestionsManager == null) {
            return
        }
        val input = if (mTerminalAdapter != null) mTerminalAdapter!!.input else Tuils.EMPTYSTRING
        suggestionsManager!!.requestSuggestion(input)
    }

    private fun runOnUiThread(action: Runnable) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            action.run()
            return
        }
        postOnUiThread(action)
    }

    private fun postOnUiThread(action: Runnable) {
        if (mRootView != null) {
            mRootView.post(action)
            return
        }
        if (mContext is Activity) {
            (mContext as Activity).runOnUiThread(action)
        } else {
            action.run()
        }
    }

    fun onBackPressed() {
        if (handleTermuxWorkspaceBackPressed()) {
            return
        }
        if (handleTermuxBackPressed()) {
            return
        }
        if (this.isPomodoroOverlayVisible) {
            return
        }
        if (this.isAppsDrawerOpen) {
            hideAppsDrawer()
            return
        }
        if (!landscapeLayoutActive && terminalTrayExpanded) {
            setTerminalTrayExpanded(false)
            return
        }
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.onBackPressed()
        }
    }

    fun focusTerminal() {
        activateTerminalInput(false)
    }

    fun activateTerminalInput(showSoftKeyboard: Boolean) {
        if (this.isTermuxConsoleVisible) {
            takeTermuxConsoleFocus(showSoftKeyboard)
            return
        }

        if (mTerminalAdapter == null) {
            return
        }

        val input = mTerminalAdapter!!.inputView
        if (input is EditText) {
            val terminalInput = input
            applyRetuiKeyboardTheme(terminalInput, "launcher")
            terminalInput.setShowSoftInputOnFocus(showSoftKeyboard)
            terminalInput.setCursorVisible(true)
            if (terminalInput is OutlineEditText) {
                terminalInput.setIdleCursorVisible(false)
            }
        }

        mTerminalAdapter!!.requestInputFocus()
        mTerminalAdapter!!.focusInputEnd()
        if (showSoftKeyboard) {
            sendRetuiKeyboardTheme(input as? EditText, "launcher")
            input?.post(Runnable {
                sendRetuiKeyboardTheme(input as? EditText, "launcher")
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            })
        }
    }

    fun pause() {
        if (mTerminalAdapter != null) {
            closeKeyboard()
        }
        if (handler != null) {
            handler!!.removeCallbacks(musicTimeRunnable)
            handler!!.removeCallbacks(eventsRefreshRunnable)
            handler!!.removeCallbacks(luaWidgetTickRunnable)
            handler!!.removeCallbacks(fontRefreshRunnable)
        }
        setMusicVisualizerPlaying(false)
        val pomodoroOverlay =
            if (mRootView != null) mRootView.findViewById<View?>(R.id.pomodoro_root) else null
        if (pomodoroOverlay != null) {
            pomodoroOverlay.setKeepScreenOn(false)
        }

        if (ramManager != null) ramManager!!.stop()
        if (batteryManager != null) batteryManager!!.stop()
        if (storageManager != null) storageManager!!.stop()
        if (networkManager != null) networkManager!!.stop()
        if (tuiTimeManager != null) tuiTimeManager!!.stop()
        if (unlockManager != null) unlockManager!!.stop()
    }

    private fun setMusicVisualizerPlaying(playing: Boolean) {
        val visualizer =
            if (mRootView != null) mRootView.findViewById<MusicVisualizerView?>(R.id.music_visualizer) else null
        if (visualizer != null) {
            visualizer.setPlaying(playing)
        }
    }

    private fun scheduleInternalMusicTickerIfNeeded() {
        if (handler == null) {
            return
        }
        handler!!.removeCallbacks(musicTimeRunnable)
        if ("internal" != activeMusicSource) {
            return
        }
        val musicWidget =
            if (mRootView != null) mRootView.findViewById<View?>(R.id.music_widget) else null
        if (musicWidget != null && musicWidget.getVisibility() == View.VISIBLE && mainPack != null && mainPack!!.player != null && mainPack!!.player!!.isPlaying()) {
            handler!!.post(musicTimeRunnable)
        }
    }

    fun resume() {
        if (handler == null) {
            return
        }
        if (restoreLauncherChromeOnResume) {
            restoreLauncherChromeOnResume = false
            restoreLauncherChromeAfterSurface()
        }
        setMusicVisualizerPlaying(lastMusicPlaying)
        scheduleInternalMusicTickerIfNeeded()
        scheduleEventsRefreshIfNeeded()
        scheduleTypefaceRefreshes()

        if (ramManager != null) ramManager!!.start()
        if (batteryManager != null) batteryManager!!.start()
        if (storageManager != null) storageManager!!.start()
        if (networkManager != null) networkManager!!.start()
        if (tuiTimeManager != null) tuiTimeManager!!.start()
        if (unlockManager != null) unlockManager!!.start()

        // Refresh Pomodoro overlay on resume
        val pomodoro = PomodoroManager.getInstance(mContext)
        if (pomodoro.isRunning) {
            val intent = Intent(PomodoroManager.ACTION_POMODORO_STATE)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_RUNNING, true)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_REMAINING, pomodoro.remainingMillis)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TOTAL, pomodoro.totalDuration)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TASK, pomodoro.taskName)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_TYPE, pomodoro.currentType.name)
            intent.putExtra(PomodoroManager.EXTRA_POMODORO_CYCLE, pomodoro.completedFocuses)
            updatePomodoroOverlay(intent)
        }
    }

    fun onConfigurationChanged(newConfig: Configuration?) {
        applyResponsiveLandscapeLayout(newConfig)
        updateTermuxWorkspaceKeyMode()
        updateTermuxConsoleKeyMode()
        scheduleTermuxWorkspaceConfigurationRecovery()
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.scrollToEnd()
            mTerminalAdapter!!.focusInputEnd()
        }
        if (mRootView != null) {
            mRootView.postDelayed(Runnable { this.scheduleTypefaceRefreshes() }, 48)
        }
    }

    fun scheduleTypefaceRefreshes() {
        if (mRootView == null) {
            return
        }

        mRootView.post(Runnable { this.refreshLauncherTypeface() })

        if (handler != null) {
            handler!!.removeCallbacks(fontRefreshRunnable)
            handler!!.postDelayed(fontRefreshRunnable, 120)
            handler!!.postDelayed(fontRefreshRunnable, 360)
            handler!!.postDelayed(fontRefreshRunnable, 900)
        }
    }

    private fun refreshLauncherTypeface() {
        val typeface = Tuils.getTypeface(mContext)
        if (typeface == null || mRootView == null) {
            return
        }

        applyTypefaceRecursively(mRootView, typeface)

        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.refreshTypeface()
        }

        val asciiView = getLabelView(Label.ascii)
        if (asciiView != null) {
            asciiView.setTypeface(Typeface.MONOSPACE)
        }
    }

    private fun applyTypefaceRecursively(view: View?, typeface: Typeface?) {
        if (view is TextView) {
            val textView = view
            val current = textView.getTypeface()
            val style = if (current != null) current.getStyle() else Typeface.NORMAL
            val fontMode = textView.getTag(R.id.module_text_font_mode)
            if (MODULE_TEXT_FONT_MONO == fontMode) {
                textView.setTypeface(Typeface.MONOSPACE, style)
            } else if (MODULE_TEXT_FONT_THEME == fontMode) {
                textView.setTypeface(typeface, style)
            } else if (textView.getId() == R.id.module_text_body) {
                textView.setTypeface(Typeface.MONOSPACE, style)
            } else {
                textView.setTypeface(typeface, style)
            }
        }

        if (view is ViewGroup) {
            val group = view
            for (i in 0..<group.getChildCount()) {
                applyTypefaceRecursively(group.getChildAt(i), typeface)
            }
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector!!.onTouchEvent(event)
        return v.onTouchEvent(event)
    }

    fun buildRedirectionListener(): OnRedirectionListener {
        return object : OnRedirectionListener {
            override fun onRedirectionRequest(cmd: RedirectCommand) {
                (mContext as Activity).runOnUiThread(Runnable {
                    mTerminalAdapter!!.setHint(mContext!!.getString(cmd.getHint()))
                    disableSuggestions()
                })
            }

            override fun onRedirectionEnd(cmd: RedirectCommand) {
                (mContext as Activity).runOnUiThread(Runnable {
                    mTerminalAdapter!!.setDefaultHint()
                    enableSuggestions()
                })
            }

            override fun onRedirection(name: String, value: String) {
                if (name == ACTION_CLEAR) {
                    mTerminalAdapter!!.clear()
                } else if (name == ACTION_HACK) {
                    playHackOverlay()
                } else if (name == ACTION_WEATHER) {
                    if (weatherManager != null) weatherManager!!.updateWeather()
                } else if (name == ACTION_WEATHER_GOT_LOCATION) {
                    if (weatherManager != null) weatherManager!!.setLocation(
                        value.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[0].toDouble(),
                        value.split(",".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[1].toDouble())
                } else if (name == ACTION_WEATHER_DELAY) {
                    if (weatherManager != null) weatherManager!!.setDelay(value.toInt())
                } else if (name == ACTION_WEATHER_MANUAL_UPDATE) {
                    if (weatherManager != null) weatherManager!!.updateWeather()
                }
            }
        }
    }

    private fun onLock() {
        if (clearOnLock) {
            mTerminalAdapter!!.clear()
        }
    }

    companion object {
        const val NEXT_UNLOCK_CYCLE_RESTART: String = "nextUnlockRestart"
        const val UNLOCK_KEY: String = "unlockTimes"
        const val PREFS_NAME: String = "ui"
        private const val PREF_OUTPUT_TRAY_EXPANDED = "output_tray_expanded"
        private const val CLOCK_EDGE_LEFT = "left"
        private const val CLOCK_EDGE_RIGHT = "right"
        private const val CLOCK_EDGE_TOP = "top"
        private const val CLOCK_EDGE_BOTTOM = "bottom"
        private const val PREF_TIMER_BADGE_EDGE = "timer_badge_edge"
        private const val PREF_TIMER_BADGE_FRACTION = "timer_badge_fraction"
        private const val PREF_STOPWATCH_BADGE_EDGE = "stopwatch_badge_edge"
        private const val PREF_STOPWATCH_BADGE_FRACTION = "stopwatch_badge_fraction"
        val ACTION_UPDATE_SUGGESTIONS: String =
            BuildConfig.APPLICATION_ID + ".ui_update_suggestions"
        val ACTION_UPDATE_HINT: String = BuildConfig.APPLICATION_ID + ".ui_update_hint"
        var ACTION_ROOT: String = BuildConfig.APPLICATION_ID + ".ui_root"
        var ACTION_NOROOT: String = BuildConfig.APPLICATION_ID + ".ui_noroot"
        var ACTION_LOGTOFILE: String = BuildConfig.APPLICATION_ID + ".ui_log"
        var ACTION_CLEAR: String = BuildConfig.APPLICATION_ID + ".ui_clear"
        var ACTION_HACK: String = BuildConfig.APPLICATION_ID + ".ui_hack"
        var ACTION_WEATHER: String = BuildConfig.APPLICATION_ID + ".ui_weather"
        var ACTION_WEATHER_GOT_LOCATION: String =
            BuildConfig.APPLICATION_ID + ".ui_weather_location"
        var ACTION_WEATHER_DELAY: String = BuildConfig.APPLICATION_ID + ".ui_weather_delay"
        var ACTION_WEATHER_MANUAL_UPDATE: String = BuildConfig.APPLICATION_ID + ".ui_weather_update"

        val ACTION_MUSIC_CHANGED: String = MusicService.ACTION_MUSIC_CHANGED
        val SONG_TITLE: String = MusicService.SONG_TITLE
        val SONG_SINGER: String = MusicService.SONG_SINGER
        val SONG_DURATION: String = MusicService.SONG_DURATION
        val SONG_POSITION: String = MusicService.SONG_POSITION
        val MUSIC_PLAYING: String = MusicService.MUSIC_PLAYING
        val ACTION_NOTIFICATION_FEED: String = NotificationService.ACTION_NOTIFICATION_FEED
        val EXTRA_NOTIFICATION_LIST: String = NotificationService.EXTRA_NOTIFICATION_LIST
        val ACTION_REQUEST_NOTIFICATION_FEED: String =
            NotificationService.ACTION_REQUEST_NOTIFICATION_FEED
        val ACTION_CLOCK_STATE: String = ClockManager.ACTION_CLOCK_STATE
        val ACTION_POMODORO_STATE: String = PomodoroManager.ACTION_POMODORO_STATE
        val ACTION_TERMUX_CONSOLE: String = BuildConfig.APPLICATION_ID + ".ui_termux_console"
        const val EXTRA_TERMUX_COMMAND: String = "termux_command"
        val ACTION_LUA_APP: String = BuildConfig.APPLICATION_ID + ".ui_lua_app"
        const val EXTRA_LUA_APP_ID: String = "lua_app_id"
        val ACTION_FILE_CONSOLE: String = BuildConfig.APPLICATION_ID + ".ui_file_console"
        const val EXTRA_FILE_COMMAND: String = "file_command"
        val ACTION_MODULE_COMMAND: String = BuildConfig.APPLICATION_ID + ".ui_module_command"
        const val EXTRA_MODULE_COMMAND: String = "module_command"
        const val EXTRA_MODULE_NAME: String = "module_name"
        const val EXTRA_WIDGET_ACTION_INDEX: String = "widget_action_index"
        const val EXTRA_WIDGET_ACTION_VALUE: String = "widget_action_value"
        private const val TERMUX_FOCUS_CAPTURE_DELAY_MS = 80
        private val TERMUX_APP_REFRESH_BURST_DELAYS_MS = intArrayOf(700, 1600, 3500, 8000, 15000, 30000)
        private const val TERMUX_APP_ADAPTIVE_REFRESH_INTERVAL_MS = 3000
        private const val TERMUX_APP_INPUT_WATCH_MS = 30000L
        private const val TERMUX_APP_MANUAL_REFRESH_WATCH_MS = 15000L
        private const val TERMUX_APP_START_WATCH_MS = 120000L
        private const val TERMUX_CONSOLE_RESULT_PREFIX = "retui-console:"
        private const val TERMUX_APP_RESULT_PREFIX = "retui-app:"
        private const val TERMUX_APP_SYNC_RESULT_PREFIX = "retui-app-sync:"
        private const val TERMUX_WORKSPACE_PAGE_INDEX = 1
        private const val TERMUX_WORKSPACE_PAGE_COUNT = 2
        private const val TERMUX_WORKSPACE_SESSION = "retui_workspace"
        private const val TERMUX_WORKSPACE_RESULT_PREFIX = "retui-workspace:"
        private const val TERMUX_WORKSPACE_SOCKET_NAME = "retui_bridge_com_dvil_tui_renewed_v2"
        private const val TERMUX_WORKSPACE_TCP_PORT = 8927
        private const val TERMUX_WORKSPACE_SWIPE_THRESHOLD_PX = 80f
        private const val TERMUX_WORKSPACE_KEY_MODE_SWIPE_THRESHOLD_PX = 70f
        private const val TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_OUT_MS = 92L
        private const val TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_IN_MS = 124L
        private const val TERMUX_PORTRAIT_KEYS_PER_ROW = 8
        private const val TERMUX_LANDSCAPE_KEYS_PER_ROW = 16
        private const val TERMUX_WORKSPACE_IME_VISIBLE_THRESHOLD_DP = 80
        private const val TERMUX_WORKSPACE_IME_RESIZE_REFRESH_DELAY_MS = 180L
        private const val TERMUX_WORKSPACE_LOCAL_OUTPUT_HOLD_MS = 7000L
        private const val TERMUX_WORKSPACE_MAX_OUTPUT_CHARS = 24000
        private const val TERMUX_WORKSPACE_LINE_HEIGHT_SAMPLE = "M\n\u2502\n\u2500\n\u2514\n\u2588"
        private const val TERMUX_WORKSPACE_LINE_HEIGHT_FALLBACK_MULTIPLIER = 1.36f
        private const val TERMUX_WORKSPACE_LINE_HEIGHT_MAX_SAMPLE_LINES = 16
        private const val TERMUX_WORKSPACE_MIN_COLS = 20
        private const val TERMUX_WORKSPACE_MAX_COLS = 240
        private const val TERMUX_WORKSPACE_MIN_ROWS = 8
        private const val TERMUX_WORKSPACE_MAX_ROWS = 120
        private val TERMUX_CONSOLE_SHELL_RESULT_PREFIX: String =
            TERMUX_CONSOLE_RESULT_PREFIX + "shell:"
        private val TERMUX_CONSOLE_CD_RESULT_PREFIX: String = TERMUX_CONSOLE_RESULT_PREFIX + "cd:"
        val ACTION_TERMUX_RESULT: String = BuildConfig.APPLICATION_ID + ".ui_termux_result"
        const val EXTRA_TERMUX_RESULT_PATH: String = "termux_result_path"
        const val EXTRA_TERMUX_RESULT_STDOUT: String = "termux_result_stdout"
        const val EXTRA_TERMUX_RESULT_STDERR: String = "termux_result_stderr"
        const val EXTRA_TERMUX_RESULT_EXIT_CODE: String = "termux_result_exit_code"
        const val EXTRA_TERMUX_RESULT_ERROR: String = "termux_result_error"
        const val EXTRA_TERMUX_RESULT_DEBUG: String = "termux_result_debug"
        const val EXTRA_TERMUX_RESULT_MODULE: String = "termux_result_module"
        val ACTION_NOTIFICATION_RECEIVED: String =
            BuildConfig.APPLICATION_ID + ".ui_notification_received"
        const val NOTIFICATION_TEXT: String = "notification_text"

        var FILE_NAME: String = "fileName"

        private const val OUTPUT_TRAY_MODE_NATIVE = "native"
        private const val OUTPUT_TRAY_MODE_AUTO = "auto"
        private const val OUTPUT_TRAY_MODE_TOGGLED = "toggled"
        private const val OUTPUT_HEADER_MODE_NORMAL = "normal"
        private const val OUTPUT_HEADER_MODE_ARROWS = "arrows"
        private const val OUTPUT_HEADER_MODE_NONE = "none"
        private const val MODULE_TEXT_FONT_THEME = "theme"
        private const val MODULE_TEXT_FONT_MONO = "mono"
        private const val EVENTS_REFRESH_GRACE_MS: Long = 1000
        private val EVENTS_REFRESH_FALLBACK_MS = (60 * 1000).toLong()
        private val LABEL_INDEX_UNMAPPED = -1f
        const val DUO_LAYOUT_OFF: String = "off"
        const val DUO_LAYOUT_LEFT: String = "left"
        const val DUO_LAYOUT_RIGHT: String = "right"
        const val MAX_LANDSCAPE_FOLD_GUTTER_MM: Int = 80
        private const val RESPONSIVE_LANDSCAPE_MIN_ASPECT = 1.15f
        private const val DUO_LAYOUT_PREF = "duo_layout"
        private const val DUO_LAST_SIDE_PREF = "duo_last_side"

        @JvmStatic
        fun isResponsiveLandscapeConfiguration(configuration: Configuration?): Boolean {
            if (configuration == null || configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                return false
            }

            val widthDp = configuration.screenWidthDp
            val heightDp = configuration.screenHeightDp
            if (widthDp > 0 && heightDp > 0
                && widthDp != Configuration.SCREEN_WIDTH_DP_UNDEFINED
                && heightDp != Configuration.SCREEN_HEIGHT_DP_UNDEFINED
            ) {
                return widthDp.toFloat() / heightDp.toFloat() >= RESPONSIVE_LANDSCAPE_MIN_ASPECT
            }

            return true
        }

        fun normalizeDuoLayoutMode(mode: String?): String {
            if (mode == null) {
                return DUO_LAYOUT_OFF
            }

            val normalized = mode.trim { it <= ' ' }.lowercase()
            if (DUO_LAYOUT_LEFT == normalized) {
                return DUO_LAYOUT_LEFT
            }
            if (DUO_LAYOUT_RIGHT == normalized) {
                return DUO_LAYOUT_RIGHT
            }
            return DUO_LAYOUT_OFF
        }

        fun resolveSavedDuoSide(context: Context?): String? {
            if (context == null) {
                return DUO_LAYOUT_RIGHT
            }

            val preferences = context.getSharedPreferences(PREFS_NAME, 0)
            val mode: String =
                normalizeDuoLayoutMode(preferences.getString(DUO_LAYOUT_PREF, DUO_LAYOUT_OFF))
            if (DUO_LAYOUT_OFF != mode) {
                return mode
            }

            val side: String =
                normalizeDuoLayoutMode(preferences.getString(DUO_LAST_SIDE_PREF, DUO_LAYOUT_RIGHT))
            return if (DUO_LAYOUT_OFF == side) DUO_LAYOUT_RIGHT else side
        }

        private fun applyBgRect(
            context: Context,
            v: View,
            bgColor: String?,
            spaces: IntArray,
            cornerRadius: Int,
            dashed: Boolean,
            borderColor: Int,
            cyberdeckNotch: Boolean
        ) {
            try {
                applyMargins(v, spaces)

                val color = try {
                    var color = Color.parseColor(bgColor)
                    if (color == Color.TRANSPARENT) {
                        color = terminalWindowBackground()
                    }
                    color
                } catch (e: Exception) {
                    terminalWindowBackground()
                }
                v.setBackgroundDrawable(
                    TerminalBorderRuntime.panelDrawablePx(
                        context,
                        color,
                        borderColor,
                        1.5f,
                        cornerRadius.toFloat(),
                        dashed,
                        cyberdeckNotch
                    )
                )
            } catch (e: Exception) {
                Tuils.toFile(e)
                Tuils.log(e)
            }
        }

        private fun applyMargins(v: View, margins: IntArray) {
            v.setPadding(margins[2], margins[3], margins[2], margins[3])

            val params = v.getLayoutParams()
            if (params is RelativeLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            } else if (params is LinearLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            }
        }

        private fun applyShadow(v: TextView, color: String, x: Int, y: Int, radius: Float) {
            if (!(color.startsWith("#00") && color.length == 9)) {
                try {
                    v.setShadowLayer(radius, x.toFloat(), y.toFloat(), Color.parseColor(color))
                    v.setTag(OutlineTextView.SHADOW_TAG)
                } catch (e: Exception) {
                    // Fallback to transparent if color is invalid
                    v.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                }
            }
        }
    }
}
