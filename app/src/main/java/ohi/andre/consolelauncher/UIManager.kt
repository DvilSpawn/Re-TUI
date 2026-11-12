package ohi.andre.consolelauncher

import ohi.andre.consolelauncher.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.LruCache
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.TextClock
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.widget.TextViewCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import okhttp3.Request
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.raw.tbridge
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.commands.tuixt.BreachDialog
import ohi.andre.consolelauncher.commands.tuixt.ThemerActivity
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog.ConfirmAction
import ohi.andre.consolelauncher.managers.terminal.AliasManager
import ohi.andre.consolelauncher.managers.net.BreachManager
import ohi.andre.consolelauncher.managers.time.ClockManager
import ohi.andre.consolelauncher.managers.apps.FocusFrictionStyle
import ohi.andre.consolelauncher.managers.apps.LockdownManager
import ohi.andre.consolelauncher.managers.time.PomodoroManager
import ohi.andre.consolelauncher.managers.time.PomodoroManager.SessionType
import ohi.andre.consolelauncher.managers.RetuiCreditManager
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.managers.terminal.TerminalManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager
import ohi.andre.consolelauncher.managers.ToolbarShortcutManager.slot
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.modules.ModuleDockButtonFactory
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager
import ohi.andre.consolelauncher.managers.modules.ModuleVariableManager
import ohi.andre.consolelauncher.managers.modules.ReminderManager
import ohi.andre.consolelauncher.managers.modules.home.CalendarModule
import ohi.andre.consolelauncher.managers.modules.home.HomeModuleEvent
import ohi.andre.consolelauncher.managers.modules.home.HomeModuleHost
import ohi.andre.consolelauncher.managers.modules.home.HomeModules
import ohi.andre.consolelauncher.managers.modules.home.LuaModules
import ohi.andre.consolelauncher.managers.modules.home.MusicModule
import ohi.andre.consolelauncher.managers.modules.home.NotesModule
import ohi.andre.consolelauncher.managers.modules.home.NotificationsModule
import ohi.andre.consolelauncher.managers.modules.home.ReminderModule
import ohi.andre.consolelauncher.managers.modules.home.RssModule
import ohi.andre.consolelauncher.managers.modules.home.TimerModule
import ohi.andre.consolelauncher.managers.modules.home.WeatherModule
import ohi.andre.consolelauncher.managers.modules.UpcomingEventsManager
import ohi.andre.consolelauncher.managers.mediaplayer.MediaPlayerActivity
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.notifications.NotificationGroups
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.cyberdeckMode
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.dashedBorders
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleBodyTextSize
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBackgroundColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleButtonBorderColor
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleCornerRadius
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.moduleHeaderTextSize
import ohi.andre.consolelauncher.managers.settings.TextShadows
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
import ohi.andre.consolelauncher.managers.settings.LauncherMode
import ohi.andre.consolelauncher.managers.settings.MusicSettings.autoShowWidget
import ohi.andre.consolelauncher.managers.settings.MusicSettings.preferredPackage
import ohi.andre.consolelauncher.managers.settings.MusicSettings.showWidget
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showTerminal
import ohi.andre.consolelauncher.managers.status.AsciiAnimationManager
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
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.createResultPendingIntent
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeManager.requestRunCommandPermissionIfPossible
import ohi.andre.consolelauncher.managers.termux.TerminalGridView
import ohi.andre.consolelauncher.managers.termux.TermuxAnsiRenderer
import ohi.andre.consolelauncher.managers.termux.TermuxAppScripts
import ohi.andre.consolelauncher.managers.termux.TermuxAppScripts.shellQuote
import ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceLauncherManager
import ohi.andre.consolelauncher.managers.widgets.AndroidWidgetDrawerManager
import ohi.andre.consolelauncher.managers.ui.AppDrawerPaneManager
import ohi.andre.consolelauncher.managers.ui.ClockTabDock
import ohi.andre.consolelauncher.managers.ui.FileConsoleController
import ohi.andre.consolelauncher.managers.ui.FocusOverlayController
import ohi.andre.consolelauncher.managers.ui.HackOverlayController
import ohi.andre.consolelauncher.managers.ui.LuaSurfaceAction
import ohi.andre.consolelauncher.managers.ui.OverlayLayoutManager
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine
import ohi.andre.consolelauncher.managers.lua.LuaWidgetEngine.UpdateListener
import ohi.andre.consolelauncher.managers.lua.LuaWidgetManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.AsciiArtTextView
import ohi.andre.consolelauncher.tuils.asciifx.AsciiEffects
import ohi.andre.consolelauncher.tuils.frame.CrtOverlayDrawable
import ohi.andre.consolelauncher.tuils.frame.CyberpunkBackdropDrawable
import ohi.andre.consolelauncher.tuils.frame.CyberpunkIconFrameDrawable
import ohi.andre.consolelauncher.tuils.frame.FrameManager
import ohi.andre.consolelauncher.tuils.frame.FrameTarget
import ohi.andre.consolelauncher.tuils.LauncherFontScale
import ohi.andre.consolelauncher.tuils.MusicVisualizerView
import ohi.andre.consolelauncher.tuils.OutlineEditText
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.StableHorizontalScrollView
import ohi.andre.consolelauncher.tuils.frame.TerminalBorderRuntime
import ohi.andre.consolelauncher.tuils.TerminalTrayToggleView
import ohi.andre.consolelauncher.tuils.LongClickMovementMethod
import ohi.andre.consolelauncher.tuils.LauncherPillStyle
import ohi.andre.consolelauncher.tuils.frame.TuiWidgetDecorator
import ohi.andre.consolelauncher.tuils.frame.TuiWidgetDecorator.decorateWidget
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import ohi.andre.consolelauncher.wallpaper.RetuiWallpaperService
import java.io.File
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
import android.app.KeyguardManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.location.Location
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.widget.ProgressBar
import android.view.GestureDetector.OnDoubleTapListener
import android.view.ViewParent
import ohi.andre.consolelauncher.managers.file.FileManager
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Map
import java.lang.reflect.Method
import java.util.ArrayList
import ohi.andre.consolelauncher.managers.net.HTMLExtractManager
import ohi.andre.consolelauncher.managers.net.RssManager
import ohi.andre.consolelauncher.profile.ProfilePaneController
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.MusicSettings
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.settings.StatusRowResolver
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.termux.TermuxBridgeCache
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import androidx.annotation.NonNull
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate
import org.json.JSONArray
import org.json.JSONObject

internal fun moduleSuggestionScrollTarget(module: String?, previousModule: String?, currentX: Int, pendingX: Int): Int =
    if (module == previousModule) max(currentX, pendingX) else 0

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
    private var gestureStartedOnOutput = false
    private var swipeUpAppsDrawer: Boolean
    private var gestureDetector: GestureDetectorCompat? = null
    private var appDrawerPaneManager: AppDrawerPaneManager? = null
    private var hackOverlayController: HackOverlayController? = null
    private var focusOverlayController: FocusOverlayController? = null
    private var fileConsoleController: FileConsoleController? = null
    private var androidWidgetDrawerManager: AndroidWidgetDrawerManager? = null
    private var clockTabDock: ClockTabDock? = null
    private val currentOverlayNotifications: ArrayList<NotificationService.Notification>
        get() = notificationsModule.currentOverlayNotifications
    private val termuxBuffer = StringBuilder()
    private var podcastTab: ImageView? = null
    private var podcastTabDockReady = false
    private var podcastPillArmed = false
    private var podcastPillState: Pair<Boolean, Boolean>? = null
    val isPomodoroOverlayVisible: Boolean
        get() = focusOverlayController?.isPomodoroOverlayVisible == true
    val isLockdownOverlayVisible: Boolean
        get() = focusOverlayController?.isLockdownOverlayVisible == true
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
    private var retainedTermuxAppId: String? = null
    private var retainedTermuxInputDraft = ""
    private var retainedTermuxAppFnKeyMode = false
    private var pendingTermuxScrollRestore = -1
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
    private var profilePaneController: ProfilePaneController? = null
    private var suggestionsContainer: View? = null
    private var suggestionsVisibilityBeforeTermux = View.VISIBLE
    private var termuxConsoleOpen = false
    private var termuxFocusCapturePending = false
    private var launcherChromeHiddenForSurface = false
    private var restoreLauncherChromeOnResume = false
    private var launcherChromeMainVisibility = View.VISIBLE
    private var launcherChromeTrayVisibility = View.VISIBLE
    private var launcherChromeLandscapeVisibility = View.GONE
    private var terminalTrayContainer: View? = null
    private var terminalContainer: ViewGroup? = null
    private var persistentInputContainer: View? = null
    private var lastPersistentInputHeight = -1
    private var terminalOutputBorder: View? = null
    private var terminalTrayToggle: TextView? = null
    private var crtOverlayDrawable: CrtOverlayDrawable? = null
    private var terminalTrayExpanded = false
    private val terminalOutputAutoHideRunnable = Runnable {
        if (this.isOutputAutoHideEnabled && terminalTrayExpanded) {
            terminalTrayExpanded = false
            applyTerminalTrayState(false)
        }
    }
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
    private var moduleSuggestionsModule: String? = null
    private var pendingModuleSuggestionsScrollX = 0
    private var moduleSuggestionsOriginalParent: ViewGroup? = null
    private var moduleSuggestionsOriginalParams: ViewGroup.LayoutParams? = null
    private var moduleSuggestionsOriginalIndex = -1
    private var activeModule: String? = ""

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
    private var statusAppearance: ohi.andre.consolelauncher.presentation.HomeAppearance? = null
    private var unifiedStatusActive = false
    private var unifiedModulesToggle: ImageButton? = null
    private var unifiedModulesVisible = true
    private val unifiedModulesAutoHideRunnable = Runnable {
        if (shouldAutoHideUnifiedModules(activeModule)) setUnifiedModulesVisible(false, false)
    }

    private var asciiColor = 0
    private var asciiAnimationManager: AsciiAnimationManager? = null
    private var asciiIdlePaused = false
    private var launcherWindowFocused = true
    private var touchStartedOnAscii = false
    private val asciiIdlePauseRunnable = Runnable {
        asciiIdlePaused = true
        asciiAnimationManager?.stop()
        AsciiArtTextView.stopEffects()
    }

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
    private val fontRefreshRunnable: Runnable = object : Runnable {
        override fun run() {
            refreshLauncherTypeface()
        }
    }


    private var weatherDelay = 0

    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var location: String? = null

    private var weatherColor = 0
    var showWeatherUpdate: Boolean = false
    private var weatherManager: WeatherManager? = null
    private var lastWeatherText: CharSequence? = null
    private var lastWeatherSymbol: String? = null
    private var lastWeatherCondition: String? = null
    private var lastWeatherDetails: String? = null
    private var lastWeatherUpdateMillis: Long = 0

    //    you need to use labelIndexes[i]
    internal fun captureHomePreview(configuration:Configuration) = (mRootView as? ViewGroup)?.let { ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot.capture(it, ohi.andre.consolelauncher.presentation.StatusPresentation.capture(labelTexts),captureUnifiedContent(),captureTrayPresentationState(),ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot.Insets(systemInsetLeft,systemInsetTop,systemInsetRight,systemInsetBottom),configuration) }

    internal fun captureTrayPresentationState() = ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.RuntimeState(
        keyboardVisible,imeBottomOffset,systemInsetBottom,imeInsetVisible,terminalTrayExpanded,landscapeLayoutActive,duoLayoutActive,!TextUtils.isEmpty(activeModule),
        searchOnlyMode,Tuils.getHint(pack?.currentDirectory?.absolutePath),
        keyboardMarginsOverride=keyboardMarginOverrideActive(imeInsetVisible,imeBottomOffset,mTerminalAdapter?.inputView?.hasFocus()==true),duoSide=activeDuoLayoutMode?:DUO_LAYOUT_OFF)

    private fun updateText(l: Label, s: CharSequence?) {
        if (labelTexts[l.ordinal] === s) {
            return
        }
        labelTexts[l.ordinal] = s

        if (unifiedStatusActive) {
            renderUnifiedStatus()
            return
        }

        val base = labelIndexes[l.ordinal].toInt()
        if (base < 0 || base >= labelViews.size || labelViews[base] == null) {
            return
        }

        val members=labelIndexes.indices.filter { labelIndexes[it].toInt()==base && labelTexts[it]!=null }.sortedBy { labelIndexes[it] }
        ohi.andre.consolelauncher.presentation.StatusPresentation.bindLiveContent(
            labelViews[base]!!,members,labelTexts,statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved())
    }

    private fun setupUnifiedStatus() {
        val root = mRootView ?: return
        val container = root.findViewById<LinearLayout?>(R.id.unified_status_container) ?: return
        unifiedStatusActive = true
        unifiedModulesToggle = root.findViewById(R.id.unified_modules_toggle)

        container.visibility = View.VISIBLE
        ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.style(root,statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved())
        unifiedModulesToggle?.apply {
            setOnClickListener { setUnifiedModulesVisible(!unifiedModulesVisible, true) }
            container.post { ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.positionModulesToggle(root) }
        }
        renderUnifiedStatus()
        root.post {
            attachUnifiedBottomConsoleIfReady()
            setUnifiedModulesVisible(true, true)
        }
    }

    private fun captureUnifiedContent(): ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.Content {
        val appearance=statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        val show=ohi.andre.consolelauncher.presentation.StatusPresentation.descriptors.map { appearance.bool(it.visible) }
        val now=java.util.Date()
        val battery=if (XMLPrefsManager.getBoolean(Behavior.battery_progress_bar)) labelTexts[Label.battery.ordinal] ?: "" else compactUnifiedBattery(labelTexts[Label.battery.ordinal])
        val condition=lastWeatherCondition ?: compactUnifiedWeatherCondition(labelTexts[Label.weather.ordinal])
        val location=XMLPrefsManager.get(Behavior.weather_location)?.trim()?.takeUnless { it.isEmpty() || it=="null" }.orEmpty()
        val details=lastWeatherDetails ?: compactUnifiedWeatherDetails(labelTexts[Label.weather.ordinal])
        val internal=storageManager?.compactInternal().orEmpty()
        val external=storageManager?.compactExternal().orEmpty()
        val connection=networkManager?.compactConnection().orEmpty()
        val ram=ramManager?.compactValue().orEmpty()
        // A customised status_time_format replaces the system date/time pair (issue #27); the default keeps the compact look.
        val customTime=XMLPrefsManager.get(Behavior.status_time_format)!=Behavior.status_time_format.defaultValue()
        val texts=linkedMapOf<Int,CharSequence>(
            R.id.unified_status_date to if(customTime) "" else android.text.format.DateFormat.getMediumDateFormat(mContext).format(now),
            R.id.unified_status_time to if(customTime) labelTexts[Label.time.ordinal] ?: "" else android.text.format.DateFormat.getTimeFormat(mContext).format(now),
            R.id.unified_status_battery to battery,
            R.id.unified_status_weather to if(condition.isEmpty()) "" else ": $condition",
            R.id.unified_status_weather_location to location,
            R.id.unified_status_weather_details to details,
            R.id.unified_status_storage_internal to internal,
            R.id.unified_status_storage_external to external,
            R.id.unified_status_network to connection,
            R.id.unified_status_ram to ram,
            R.id.unified_status_unlock to unlockManager?.count()?.let { java.text.NumberFormat.getIntegerInstance().format(it) }.orEmpty()
        )
        fun visible(ordinal:Int,id:Int)=if(show[ordinal] && !texts[id].isNullOrEmpty()) View.VISIBLE else View.GONE
        val visibility=linkedMapOf(
            R.id.unified_status_date to if(show[Label.time.ordinal] && !customTime) View.VISIBLE else View.GONE,
            R.id.unified_status_time to if(show[Label.time.ordinal]) View.VISIBLE else View.GONE,
            R.id.unified_status_battery to visible(Label.battery.ordinal,R.id.unified_status_battery),
            R.id.unified_status_weather to visible(Label.weather.ordinal,R.id.unified_status_weather),
            R.id.unified_status_weather_icon to visible(Label.weather.ordinal,R.id.unified_status_weather),
            R.id.unified_status_weather_location to if(show[Label.weather.ordinal] && condition.isNotEmpty() && location.isNotEmpty()) View.VISIBLE else View.GONE,
            R.id.unified_status_weather_details to if(show[Label.weather.ordinal] && condition.isNotEmpty() && details.isNotEmpty()) View.VISIBLE else View.GONE,
            R.id.unified_status_storage_internal to visible(Label.storage.ordinal,R.id.unified_status_storage_internal),
            R.id.unified_status_storage_internal_icon to visible(Label.storage.ordinal,R.id.unified_status_storage_internal),
            R.id.unified_status_storage_external to visible(Label.storage.ordinal,R.id.unified_status_storage_external),
            R.id.unified_status_storage_external_icon to visible(Label.storage.ordinal,R.id.unified_status_storage_external),
            R.id.unified_status_network to visible(Label.network.ordinal,R.id.unified_status_network),
            R.id.unified_status_ram to visible(Label.ram.ordinal,R.id.unified_status_ram),
            R.id.unified_status_ram_icon to visible(Label.ram.ordinal,R.id.unified_status_ram),
            R.id.unified_status_unlock to if(show[Label.unlock.ordinal] && unlockManager!=null) View.VISIBLE else View.GONE
        )
        return ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.Content(texts,visibility,
            appearance.color(Theme.battery_text_high),if(networkManager?.compactConnectionUsesWifi()==true) R.drawable.ic_status_wifi_40 else R.drawable.ic_material_signal_cellular_alt_24)
    }

    private fun renderUnifiedStatus() {
        if (!unifiedStatusActive) return
        val root=mRootView ?: return
        ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.bindContent(root,captureUnifiedContent(),
            statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved())
    }

    private fun ensureAsciiViewportView(parent: LinearLayout, index: Int): TextView {
        val existing = labelViews[index] ?: return AsciiArtTextView(mContext!!)
        if (existing is AsciiArtTextView) {
            return existing
        }

        val asciiView = AsciiArtTextView(existing.context)
        asciiView.id = existing.id
        asciiView.layoutParams = existing.layoutParams
        asciiView.setPadding(
            existing.paddingLeft,
            existing.paddingTop,
            existing.paddingRight,
            existing.paddingBottom
        )
        asciiView.isFocusable = existing.isFocusable
        asciiView.isFocusableInTouchMode = existing.isFocusableInTouchMode
        asciiView.isClickable = existing.isClickable
        asciiView.setOnTouchListener(this)

        val childIndex = parent.indexOfChild(existing)
        if (childIndex >= 0) {
            parent.removeViewAt(childIndex)
            parent.addView(asciiView, childIndex)
        }
        labelViews[index] = asciiView
        return asciiView
    }

    private fun ensureAsciiFile(asciiFile: File) {
        if (asciiFile.exists()) {
            return
        }

        try {
            val parent = asciiFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }

            val sample = "  _____         _______     _    _ _____ \n" +
                    " |  __ \\       |__   __|   | |  | |_   _|\n" +
                    " | |__) | ___     | |______| |  | | | |  \n" +
                    " |  _  / / _ \\    | |______| |  | | | |  \n" +
                    " | | \\ \\|  __/    | |      | |__| |_| |_ \n" +
                    " |_|  \\_\\\\___|    |_|       \\____/|_____|\n"
            FileOutputStream(asciiFile).use { stream ->
                stream.write(sample.toByteArray(charset("UTF-8")))
                stream.flush()
            }
        } catch (e: Exception) {
            Log.e("TUI-UI", "Error creating ascii.txt", e)
        }
    }

    private fun applyAsciiDisplayLimits(asciiView: TextView?) {
        if (asciiView == null) {
            return
        }

        asciiView.setSingleLine(false)
        asciiView.setEllipsize(null)
        asciiView.setHorizontallyScrolling(false)
        asciiView.setIncludeFontPadding(false)

        val maxLines = XMLPrefsManager.getInt(Ui.ascii_max_lines)
        if (maxLines > 0) {
            asciiView.setMaxLines(maxLines)
            asciiView.setVerticalScrollBarEnabled(false)
        } else {
            asciiView.setMaxLines(Int.MAX_VALUE)
        }
    }

    private inner class PagerAdapter : RecyclerView.Adapter<PagerViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder {
            val view = ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.inflateModulesPage(parent.context,parent)
            setupHomeWidgetsPage(view)
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
            return 1
        }

        override fun getItemViewType(position: Int): Int {
            return position
        }

    }

    private class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private val modeHost = object : ohi.andre.consolelauncher.managers.modes.HomeModeHost {
        override val context: Context get() = mContext
        override val root: ViewGroup? get() = mRootView as? ViewGroup
        override val pack: MainPack get() = mainPack
        override val weatherDetails: String? get() = lastWeatherDetails
        override val weatherCondition: String? get() = lastWeatherCondition
        override val weatherSymbol: String? get() = lastWeatherSymbol
        override val notifications: List<NotificationService.Notification> get() = currentOverlayNotifications
        override val music: ohi.andre.consolelauncher.managers.modes.HomeModeHost.Music?
            get() = musicModule.song?.takeIf { it.isNotBlank() }?.let { ohi.andre.consolelauncher.managers.modes.HomeModeHost.Music(it, musicModule.singer, musicModule.playing) }
        override fun onMain(action: () -> Unit) = runOnMainThread(action)
        override fun executeResolved(command: String?, resolved: Any?) { mExecuter.execute(command, resolved) }
        override fun executeSearchCommand(command: String) = executeFromSearch(command)
        override fun hideKeyboard() = hideHomeKeyboard()
        override fun raiseKeyboard() = openKeyboard()
        override fun closeSearchModules() { searchSurface.closeModules(backToSearch = false) }
        override val tray: View? get() = terminalTrayContainer
        override val inputBar: View? get() = persistentInputContainer
        override val keyboardUp: Boolean get() = keyboardVisible
        override fun applyDisplayMargins() = applyDisplayMarginsForConfigurationOnMainThread()
        override fun launchSettings() = openSettingsSurface(null)
        override fun configureSearchLayout(closeDrawers: Boolean) = configureSearchOnlyLayout(closeDrawers)
        override fun activateInput() = activateTerminalInput(true)
        override fun refreshSuggestions() = refreshStockSuggestions()
        override fun dropKeyboard() = closeKeyboard()
        override fun applySectionMargins(view: View?, marginMm: IntArray, metrics: DisplayMetrics?, extraBottomPx: Int, extraTopPx: Int) =
            applySectionDisplayMargins(view, marginMm, metrics, extraBottomPx, extraTopPx)
    }

    /** The modes with a home of their own. A new one only needs its class and an entry here. */
    private val homeModes: ohi.andre.consolelauncher.managers.modes.HomeModes by lazy {
        ohi.andre.consolelauncher.managers.modes.HomeModes(mapOf(
            LauncherMode.SEARCH to ohi.andre.consolelauncher.managers.modes.SearchHomeMode(),
            LauncherMode.DOT to ohi.andre.consolelauncher.managers.modes.DotHomeMode(modeHost),
            LauncherMode.MONO to ohi.andre.consolelauncher.managers.modes.MonoHomeMode(modeHost),
            LauncherMode.TERMINAL to ohi.andre.consolelauncher.managers.modes.TerminalHomeMode(modeHost),
        ))
    }

    private val searchSurface: ohi.andre.consolelauncher.managers.modes.SearchSurface by lazy {
        ohi.andre.consolelauncher.managers.modes.SearchSurface(object : ohi.andre.consolelauncher.managers.modes.SearchSurfaceHost {
            override val context: Context get() = mContext
            override val root: ViewGroup? get() = mRootView as? ViewGroup
            override val tray: View? get() = terminalTrayContainer
            override val appearance get() = statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
            override val drawersOpen: Boolean get() = isAppsDrawerOpen || isAndroidWidgetDrawerOpen
            override val searchInput: View? get() = mTerminalAdapter?.inputView
            override val suggestions: SuggestionsManager? get() = suggestionsManager
            override fun hideDrawers() {
                appDrawerPaneManager?.hide()
                androidWidgetDrawerManager?.hide()
            }
            override fun renderModuleInto(moduleId: String, container: ViewGroup) {
                if (homeModulesContainer !== container) { classicModulesContainer = homeModulesContainer; homeModulesContainer = container }
                showHomeModule(moduleId)
            }
            override fun moduleCarouselClosed() {
                closeHomeModule()
                homeModulesContainer = classicModulesContainer
            }
            override fun activateInput() = activateTerminalInput(true)
            override fun dropKeyboard() = closeKeyboard()
        }) { homeModes.active }
    }

    private var suggestionsManager: SuggestionsManager? = null
    private val searchOnlyMode: Boolean
        get() = homeModes.active.usesSearchLayout

    /**
     * Puts the keyboard away for a home that manages its own (see [HomeMode.managesOwnKeyboard]). After
     * `mode` it can still belong to the old layout's input, so it goes by window as well. Mono types in its own input line.
     */
    private fun hideHomeKeyboard() {
        runCatching { closeKeyboard() }
        (mContext as? Activity)?.window?.let { window ->
            val hide = { androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.ime()) }
            hide(); window.decorView.post { hide() }
        }
    }

    private var terminalView: TextView? = null

    private var doubleTapCmd: String?
    private var lockOnDbTap: Boolean

    private val receiver: BroadcastReceiver

    var pack: MainPack? = null

    private val mRootView: View?

    private val viewPager: ViewPager2
    private var homeModulesContainer: ViewGroup? = null
    // Search and Terminal modes show dock modules in a carousel over the blurred wallpaper; while it is open,
    // module rendering targets the carousel instead of Classic's home page.
    private var classicModulesContainer: ViewGroup? = null
    val isSearchModulesOpen: Boolean get() = searchSurface.isModuleOpen
    private var mainContainer: View? = null
    private var headerContainer: ViewGroup? = null
    private var unifiedBottomConsole: ViewGroup? = null
    private var unifiedDockHost: ViewGroup? = null
    private var unifiedModuleScroll: ScrollView? = null
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
    private var duoTopPanesSwapped = false
    private var duoControls: View? = null
    private var duoLayoutMode: String? = DUO_LAYOUT_OFF
    private var activeDuoLayoutMode: String? = DUO_LAYOUT_OFF
    private var systemInsetLeft = 0
    private var systemInsetTop = 0
    private var systemInsetRight = 0
    private var systemInsetBottom = 0
    private var imeBottomOffset = 0
    private var imeInsetVisible = false

    private var margins: Array<IntArray?>

    private val mExecuter: CommandExecuter

    private fun setupTerminalPage(terminalPage: View) {
        val appearance=ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        terminalTrayContainer = mRootView!!.findViewById<View?>(R.id.terminal_tray_container)
        persistentInputContainer = mRootView!!.findViewById<View?>(R.id.persistent_input_container)
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

        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(terminalOutputBorder!!,appearance)
        terminalView!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (terminalContainer != null) {
                    val appended = if (s != null && count > 0 && start + count <= s.length) {
                        s.subSequence(start, start + count)
                    } else null
                    terminalContainer!!.post(Runnable { onTerminalOutputChanged(s, appended) })
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleOutputText(terminalView!!,appearance)
        restoreTerminalTrayState()
        styleTerminalTrayToggle()
        applyTerminalTrayState(false)

        val inputView = mRootView.findViewById<View?>(R.id.input_view) as EditText
        val prefixView = mRootView.findViewById<View?>(R.id.prefix_view) as TextView
        val inputGroup = mRootView.findViewById<View>(R.id.input_group)
        inputView.setCursorVisible(false)
        inputView.setShowSoftInputOnFocus(false)
        var inputTouchInProgress = false
        inputView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    inputTouchInProgress = true
                    inputView.setShowSoftInputOnFocus(true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    inputTouchInProgress = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) homeModes.active.onInputEngaged()
                    inputView.post { applyDisplayMarginsForConfigurationOnMainThread() }
                }
            }
            false
        }
        inputView.setOnFocusChangeListener { _, hasFocus ->
            suggestionsManager?.setSearchInputActive(keyboardVisible)
            if (hasFocus && inputTouchInProgress) homeModes.active.onInputEngaged()
            if (searchOnlyMode && hasFocus) refreshStockSuggestions()
            if (!inputTouchInProgress) applyDisplayMarginsForConfigurationOnMainThread()
        }
        // Focus can survive a return to Home with the keyboard up; typing must still open Terminal search.
        inputView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty() && inputView.hasFocus() && isSearchModulesOpen) searchSurface.closeModules(backToSearch = false)
                if (inputView.hasFocus() && !s.isNullOrEmpty()) homeModes.active.onInputEngaged()
            }
        })
        if (inputView is OutlineEditText) {
            val outlineInput = inputView
            outlineInput.setIdleCursorColor(XMLPrefsManager.getColor(Theme.cursor_color))
            outlineInput.setIdleCursorVisible(true)
        }
        val openTerminalInput = View.OnClickListener {
            homeModes.active.onInputEngaged()
            activateTerminalInput(true)
            refreshStockSuggestions()
        }
        inputView.setOnClickListener(openTerminalInput)
        inputGroup.setOnClickListener(openTerminalInput)
        applyRetuiKeyboardTheme(inputView, "launcher")

        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(inputGroup,appearance)
        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleInputText(inputView,appearance)
        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleInputText(prefixView,appearance)

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
        var androidWidgetDrawerView: ImageButton? = null
        var termuxWorkspaceView: ImageButton? = null

        if (!showToolbar) {
            mRootView.findViewById<View?>(R.id.tools_view)?.setVisibility(View.GONE)
            toolbarView = null
        } else {
            backView = mRootView.findViewById<View?>(R.id.back_view) as ImageButton?
            nextView = mRootView.findViewById<View?>(R.id.next_view) as ImageButton?
            deleteView = mRootView.findViewById<View?>(R.id.delete_view) as ImageButton?
            pasteView = mRootView.findViewById<View?>(R.id.paste_view) as ImageButton?
            appDrawerView = mRootView.findViewById<View?>(R.id.app_drawer_view) as ImageButton?
            androidWidgetDrawerView =
                mRootView.findViewById<View?>(R.id.android_widget_drawer_view) as ImageButton?
            termuxWorkspaceView = mRootView.findViewById<View?>(R.id.termux_workspace_view) as ImageButton?

            toolbarView = mRootView.findViewById<View?>(R.id.tools_view)
            hideToolbarNoInput = XMLPrefsManager.getBoolean(Toolbar.hide_toolbar_no_input)

            ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(toolbarView!!,appearance)

            configureAppsDrawerToolbarButton(appDrawerView)
            configureAndroidWidgetDrawerToolbarButton(androidWidgetDrawerView)
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
        styleToolbarButtonChrome(
            backView,
            nextView,
            deleteView,
            pasteView,
            appDrawerView,
            androidWidgetDrawerView,
            termuxWorkspaceView
        )
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

        val classicSuggestionsEnabled = XMLPrefsManager.getBoolean(Suggestions.show_suggestions)
        if (classicSuggestionsEnabled || searchOnlyMode) {
            val sv =
                mRootView.findViewById<View?>(R.id.suggestions_container) as HorizontalScrollView?
            if (sv != null) {
                sv.setFocusable(false)
                sv.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                    if (hasFocus) {
                        v!!.clearFocus()
                    }
                })
                ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(sv,appearance)

                val suggestionsView =
                    mRootView.findViewById<View?>(R.id.suggestions_group) as LinearLayout?
                suggestionsManager = SuggestionsManager(
                    suggestionsView!!,
                    mainPack,
                    mTerminalAdapter!!,
                    mRootView.findViewById(R.id.search_mode_results_group),
                    searchOnlyMode
                )

                inputView.addTextChangedListener(
                    SuggestionTextWatcher(
                        suggestionsManager!!,
                        OnTextChanged { currentText: String?, before: Int ->
                            if (searchOnlyMode) return@OnTextChanged
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

        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.suggestionsVisibility(mRootView,appearance)

        if (searchOnlyMode) {
            configureSearchOnlyLayout()
            suggestionsManager?.setSearchNotifications(currentOverlayNotifications)
            if (!TextUtils.isEmpty(terminalView?.text)) {
                suggestionsManager?.showInlineOutput(terminalView?.text)
            }
        }


        homeModes.active.install()
        applyPixelGrid()
        scheduleTypefaceRefreshes()
    }

    private fun addToolbarShortcutButtons(toolbarLayout: LinearLayout?) {
        if (toolbarLayout == null) {
            return
        }

        var added = 0
        if (XMLPrefsManager.getBoolean(Toolbar.gesture_button_enabled)) {
            val button = ohi.andre.consolelauncher.tuils.ToolbarGestureButton(mContext!!) { direction ->
                val option = when (direction) {
                    ohi.andre.consolelauncher.tuils.ToolbarGestureDirection.UP -> Toolbar.gesture_up
                    ohi.andre.consolelauncher.tuils.ToolbarGestureDirection.DOWN -> Toolbar.gesture_down
                    ohi.andre.consolelauncher.tuils.ToolbarGestureDirection.LEFT -> Toolbar.gesture_left
                    ohi.andre.consolelauncher.tuils.ToolbarGestureDirection.RIGHT -> Toolbar.gesture_right
                }
                XMLPrefsManager.get(option)?.takeIf { it.isNotBlank() }?.let { executeToolbarShortcut(it) }
            }
            button.setColorFilter(XMLPrefsManager.getColor(Theme.toolbar_icon_color), PorterDuff.Mode.SRC_IN)
            styleToolbarButtonChrome(button)
            toolbarLayout.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            added++
        }
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
            button.setContentDescription(mContext.getString(R.string.surface_toolbar_shortcut_2e0fd, slot.index, slot.command))
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

    fun refreshAppsDrawerToolbarButton() {
        configureAppsDrawerToolbarButton(mRootView?.findViewById<ImageButton?>(R.id.app_drawer_view))
        refreshToolbarWeightSum()
        if (duoLayoutActive) attachDuoSwitchButton(activeDuoLayoutMode)
    }

    private fun configureAppsDrawerToolbarButton(button: ImageButton?) {
        if (button == null) return
        button.setColorFilter(XMLPrefsManager.getColor(Theme.toolbar_icon_color), PorterDuff.Mode.SRC_IN)
        button.visibility = if (XMLPrefsManager.getBoolean(Toolbar.show_apps_drawer_button)) View.VISIBLE else View.GONE
        button.setOnClickListener { showAppsDrawer() }
    }

    fun refreshAndroidWidgetDrawerToolbarButton() {
        val button = mRootView?.findViewById<ImageButton?>(R.id.android_widget_drawer_view)
        configureAndroidWidgetDrawerToolbarButton(button)
        refreshToolbarWeightSum()
        if (duoLayoutActive) attachDuoSwitchButton(activeDuoLayoutMode)
    }

    private fun configureAndroidWidgetDrawerToolbarButton(button: ImageButton?) {
        if (button == null) {
            return
        }
        val enabled = XMLPrefsManager.getBoolean(Behavior.show_android_widget_drawer_button)
        button.visibility = if (enabled) View.VISIBLE else View.GONE
        button.setColorFilter(
            XMLPrefsManager.getColor(Theme.toolbar_icon_color),
            PorterDuff.Mode.SRC_IN
        )
        button.setBackgroundColor(0)
        button.setOnClickListener(View.OnClickListener { v: View? ->
            showAndroidWidgetDrawer()
        })
        styleToolbarButtonChrome(button)
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
            ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity.open(mContext)
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
        val appearance=ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        buttons.filterNotNull().forEach { ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleToolbarButton(it,appearance) }
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

    /** Runs a command typed in search opened over another app, as if it were typed here. */
    internal fun executeFromSearch(command: String) {
        homeModes.active.beforeSearchCommand(command)
        if (mTerminalAdapter?.executeInput(command) != true) mExecuter.execute(command, null)
        homeModes.active.afterSearchCommand()
    }

    private fun executeToolbarShortcut(command: String?) {
        val normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (normalized.length == 0) {
            Toast.makeText(mContext, mContext.getString(R.string.surface_toolbar_shortcut_is_empty_fdada), Toast.LENGTH_SHORT).show()
            return
        }

        if (mTerminalAdapter != null && mTerminalAdapter!!.executeInput(normalized)) {
            return
        }

        mExecuter.execute(normalized, null)
    }

    private fun openToolbarShortcutSettings() {
        openSettingsSurface(ThemerActivity.SECTION_PERSONALIZATION)
    }

    fun openSettingsSurface(section: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openSettingsSurface(section) }
            return
        }

        val intent = ThemerActivity.launchIntent(mContext!!, section)
        mContext!!.startActivity(intent)
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
        headerContainer?.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            if (duoTopPanesSwapped) applyDisplayMarginsForConfiguration(currentConfiguration)
            // The output fills the room below the header.
            headerContainer?.post { applyTerminalTrayState(false) }
        }
        unifiedBottomConsole = rootView.findViewById<ViewGroup?>(R.id.unified_bottom_console)
        unifiedDockHost = rootView.findViewById<ViewGroup?>(R.id.unified_dock_host)
        unifiedModuleScroll = rootView.findViewById<ScrollView?>(R.id.unified_module_scroll)
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

    private fun attachUnifiedBottomConsoleIfReady() {
        if (searchOnlyMode || !unifiedStatusActive) return
        val root=mRootView as? ViewGroup ?:return
        if(!ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.attachUnified(root)) return
        updateUnifiedBottomModuleBounds()
    }

    private fun updateUnifiedBottomModuleBounds() {
        if (!unifiedStatusActive) return
        val scroll = unifiedModuleScroll ?: return
        val content = homeModulesContainer ?: return
        if (!unifiedModulesVisible || TextUtils.isEmpty(activeModule) || content.childCount == 0) {
            scroll.visibility = View.GONE
            scroll.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            return
        }

        scroll.visibility = View.VISIBLE
        scroll.post {
            if (TextUtils.isEmpty(activeModule) || content.childCount == 0) return@post
            val rootHeight = mRootView?.height ?: 0
            val availableHeight = max(0, rootHeight - imeBottomOffset - systemInsetBottom)
            val width = max(1, scroll.width)
            content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.unifiedModuleHeight(
                content.measuredHeight,availableHeight,Tuils.dpToPx(mContext,240))
            if (scroll.layoutParams.height != targetHeight) {
                scroll.layoutParams.height = targetHeight
                scroll.requestLayout()
                applyTerminalTrayState(false)
            }
        }
    }

    private fun setUnifiedModulesVisible(visible: Boolean, autoHide: Boolean) {
        if (!unifiedStatusActive) return
        val enabled = getBoolean(Behavior.show_module_dock)
        unifiedModulesVisible = visible && enabled
        unifiedDockHost?.visibility = if (unifiedModulesVisible) View.VISIBLE else View.GONE
        unifiedModulesToggle?.apply {
            visibility = if (enabled) View.VISIBLE else View.GONE
            rotation = if (unifiedModulesVisible) 180f else 0f
            contentDescription = if (unifiedModulesVisible) mContext.getString(R.string.surface_hide_modules_ccff2) else mContext.getString(R.string.surface_show_modules_8f780)
        }
        handler?.removeCallbacks(unifiedModulesAutoHideRunnable)
        if (unifiedModulesVisible) {
            updateUnifiedBottomModuleBounds()
            refreshModuleSuggestionsStrip()
            if (autoHide) handler?.postDelayed(unifiedModulesAutoHideRunnable, UNIFIED_MODULE_AUTO_HIDE_MS)
        } else {
            unifiedModuleScroll?.visibility = View.GONE
            hideModuleSuggestionsStrip()
        }
        unifiedBottomConsole?.post { applyTerminalTrayState(false) }
    }

    private fun persistentBottomChromeHeight(): Int {
        val inputHeight = persistentInputContainer?.measuredHeight ?: 0
        val consoleHeight = if (unifiedStatusActive && unifiedBottomConsole?.visibility == View.VISIBLE) {
            unifiedBottomConsole?.measuredHeight ?: 0
        } else 0
        return inputHeight + consoleHeight
    }

    private fun applyResponsiveLandscapeLayout(configuration: Configuration?) {
        if (mainContainer == null || terminalTrayContainer == null || landscapeSplitContainer == null || landscapeLeftPane == null || landscapeRightPane == null || (mRootView !is ViewGroup)) {
            applyDisplayMarginsForConfiguration(configuration)
            return
        }

        if (searchOnlyMode) {
            if (landscapeLayoutActive || mainContainer!!.parent !== mRootView || terminalTrayContainer!!.parent !== mRootView) {
                restorePortraitLayout()
            }
            configureSearchOnlyLayout(closeDrawers = true)
            applyDisplayMarginsForConfiguration(configuration)
            return
        }

        if (unifiedStatusActive) {
            val root = mRootView as ViewGroup
            if (landscapeLayoutActive
                || mainContainer!!.parent !== root
                || terminalTrayContainer!!.parent !== root
            ) {
                restorePortraitLayout()
            }
            attachUnifiedBottomConsoleIfReady()
            applyDisplayMarginsForConfiguration(configuration)
            applyTerminalTrayState(false)
            return
        }

        val shouldUseLandscape = shouldUseResponsiveLandscape(configuration)
        val shouldUseDuoLayout = shouldUseLandscape && shouldUseDuoLayout()
        val requestedDuoLayoutMode = if (shouldUseDuoLayout) getDuoLayoutMode() else DUO_LAYOUT_OFF
        val duoSideChanged = shouldUseDuoLayout && requestedDuoLayoutMode != activeDuoLayoutMode
        val splitDuoChanged =
            shouldUseDuoLayout && (shouldUseSplitDuoLauncher() != splitDuoStatusActive
                    || getBoolean(Behavior.duo_swap_top_panes) != duoTopPanesSwapped)
        if (shouldUseLandscape == landscapeLayoutActive && shouldUseDuoLayout == duoLayoutActive && !duoSideChanged && !splitDuoChanged) {
            if (duoLayoutActive) attachDuoSwitchButton(activeDuoLayoutMode)
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
        return getBoolean(Ui.split_duo_launcher) || getBoolean(Behavior.duo_swap_top_panes)
    }

    fun getDuoLayoutMode(): String {
        if (!getBoolean(Behavior.duo_mode)) {
            return DUO_LAYOUT_OFF
        }
        return normalizeDuoLayoutMode(duoLayoutMode)
    }

    fun enableLastDuoSide(): String {
        var side: String = preferences.getString(
            ohi.andre.consolelauncher.UIManager.Companion.DUO_LAST_SIDE_PREF,
            ohi.andre.consolelauncher.UIManager.Companion.DUO_LAYOUT_RIGHT
        )!!
        if (DUO_LAYOUT_OFF == normalizeDuoLayoutMode(side)) {
            side = DUO_LAYOUT_RIGHT
        }
        return setDuoLayoutMode(side)
    }

    fun setDuoLayoutMode(mode: String?): String {
        val normalized: String = normalizeDuoLayoutMode(mode)
        duoLayoutMode = normalized
        val editor = preferences.edit().putString(DUO_LAYOUT_PREF, normalized)
        if (DUO_LAYOUT_OFF != normalized) {
            editor.putString(DUO_LAST_SIDE_PREF, normalized)
        }
        editor.apply()
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
        if (unifiedStatusActive) {
            renderUnifiedStatus()
            return
        }
        val asciiView = getLabelView(Label.ascii)
        if (asciiView == null) {
            return
        }
        val showAscii = !TextUtils.isEmpty(labelTexts[Label.ascii.ordinal])
                && (!landscape || getBoolean(Ui.show_ascii_landscape))
        asciiView.setVisibility(if (showAscii) View.VISIBLE else View.GONE)
    }

    private fun activateLandscapeLayout() {
        val root = mRootView as ViewGroup
        restoreSplitDuoStatusHeader()
        restoreModuleSuggestionsStrip()
        detachFromParent(mainContainer)
        detachFromParent(terminalTrayContainer)
        clearLandscapePanes()

        landscapeLayoutActive = true
        duoLayoutActive = false
        activeDuoLayoutMode = DUO_LAYOUT_OFF
        landscapeSplitContainer!!.setVisibility(View.VISIBLE)
        applyLandscapeFoldGutter(mContext.resources.configuration)

        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.attachLandscape(root,mainContainer,terminalTrayContainer)
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
        applyLandscapeFoldGutter(mContext.resources.configuration)

        duoTopPanesSwapped=getBoolean(Behavior.duo_swap_top_panes)
        splitDuoStatusActive=shouldUseSplitDuoLauncher()
        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.attachDuo(root,activeMode,
            ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),imeBottomOffset,mainContainer,terminalTrayContainer,moduleSuggestionsScroll)
        attachDuoSwitchButton(activeMode)
        attachDuoDrawers(activeMode)
    }

    private fun restorePortraitLayout() {
        val root = mRootView as ViewGroup
        restoreSplitDuoStatusHeader()
        restoreModuleSuggestionsStrip()
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

        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.attachPortrait(root,mainParams,trayParams,mainContainer,terminalTrayContainer)
    }

    private fun clearLandscapePanes() {
        // Drawer managers retain these view instances across layout changes.
        val root = mRootView as ViewGroup
        appDrawerPaneManager?.hide()
        androidWidgetDrawerManager?.hide()
        for (id in intArrayOf(R.id.apps_drawer_root, R.id.android_widget_drawer_root)) {
            val drawer = root.findViewById<View>(id) ?: continue
            if (drawer.parent !== root) {
                detachFromParent(drawer)
                root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
            }
        }
        duoControls = null
        duoTopPanesSwapped = false
        if (landscapeLeftPane != null) {
            landscapeLeftPane!!.removeAllViews()
        }
        if (landscapeRightPane != null) {
            landscapeRightPane!!.removeAllViews()
        }
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
        val pane=getDuoEmptyPane(activeMode)?:return
        detachFromParent(duoControls)
        val side=activeMode?:DUO_LAYOUT_RIGHT
        val controls=ohi.andre.consolelauncher.presentation.DuoControlsPresentation.attach(pane,side,
            ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),imeBottomOffset)
        duoControls=controls.root
        controls.switch.setOnClickListener { setDuoLayoutMode(if(side==DUO_LAYOUT_RIGHT) DUO_LAYOUT_LEFT else DUO_LAYOUT_RIGHT) }
        controls.apps?.setOnClickListener { if(isAppsDrawerOpen) hideAppsDrawer() else showAppsDrawer() }
        controls.widgets?.setOnClickListener { if(isAndroidWidgetDrawerOpen) hideAndroidWidgetDrawer() else showAndroidWidgetDrawer() }
    }

    private fun restoreModuleSuggestionsStrip() {
        val strip = moduleSuggestionsScroll ?: return
        val originalParent = moduleSuggestionsOriginalParent ?: return
        if (strip.parent === originalParent) return
        detachFromParent(strip)
        val index = if (moduleSuggestionsOriginalIndex >= 0) {
            min(moduleSuggestionsOriginalIndex, originalParent.childCount)
        } else {
            originalParent.childCount
        }
        originalParent.addView(
            strip,
            index,
            copyLayoutParams(moduleSuggestionsOriginalParams)
        )
    }

    private fun attachDuoDrawers(activeMode: String) {
        val pane = getDuoEmptyPane(activeMode) ?: return
        for (id in intArrayOf(R.id.apps_drawer_root, R.id.android_widget_drawer_root)) {
            val drawer = mRootView?.findViewById<View>(id) ?: continue
            detachFromParent(drawer)
            pane.addView(drawer, FrameLayout.LayoutParams(-1, -1).apply {
                bottomMargin = Tuils.dpToPx(mContext, 84) + imeBottomOffset
            })
        }
        duoControls?.bringToFront()
    }

    private fun applyLandscapeFoldGutter(configuration: Configuration?) {
        if (landscapeFoldGutter == null) {
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

            updateKeyboardLayoutState(
                imeInsetVisible || imeBottomOffset > 0,
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

    private fun updateKeyboardLayoutState(newKeyboardVisible: Boolean, rootHeight: Int) {
        val layoutStateChanged =
            !hasLastLayoutState || keyboardVisible != newKeyboardVisible || lastObservedRootHeight != rootHeight
        keyboardVisible = newKeyboardVisible
        homeModes.active.onKeyboardChanged(newKeyboardVisible)
        hasLastLayoutState = true
        lastObservedRootHeight = rootHeight
        if (!layoutStateChanged) {
            return
        }

        if (mTerminalAdapter != null && mTerminalAdapter!!.inputView is EditText) {
            val terminalInput = mTerminalAdapter!!.inputView as EditText
            terminalInput.setCursorVisible(keyboardVisible)
            if (terminalInput is OutlineEditText) {
                terminalInput.setIdleCursorVisible(!keyboardVisible)
            }
            if (!keyboardVisible && terminalInput.hasFocus()) {
                terminalInput.clearFocus()
            }
        }
        suggestionsManager?.setSearchInputActive(keyboardVisible)
        notificationsModule.setNotificationWidgetCompact(keyboardVisible)
        updateUnifiedBottomModuleBounds()
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
        if (mRootView == null) return

        val landscape = shouldUseResponsiveLandscape(configuration)
        applyTerminalOutputEdgeInsets(landscape)
        applyLandscapeFoldGutter(configuration)
        var topMargins = getDisplayMargins(Ui.display_margin_top_section)
        var bottomMargins = getDisplayMargins(Ui.display_margin_bottom_section)
        // Split Duo can put header/status and terminal on different panes, so only non-Duo
        // landscape folds the two section margins into the legacy landscape margin.
        if (landscape && !duoLayoutActive && XMLPrefsManager.wasChanged(Ui.display_margin_landscape_mm, false)) {
            topMargins = getDisplayMargins(Ui.display_margin_landscape_mm)
            bottomMargins = topMargins
        }
        if (keyboardMarginOverrideActive(
                imeInsetVisible,
                imeBottomOffset,
                mTerminalAdapter?.inputView?.hasFocus() == true
            ) && XMLPrefsManager.wasChanged(Ui.keyboard_visible_margin_override, false)
        ) {
            bottomMargins = getDisplayMargins(Ui.keyboard_visible_margin_override)
        }

        mRootView.setPadding(systemInsetLeft, 0, systemInsetRight, systemInsetBottom)
        val metrics = mContext!!.getResources().getDisplayMetrics()
        applySectionDisplayMargins(mainContainer, topMargins, metrics,
            if (duoTopPanesSwapped) Tuils.dpToPx(mContext, 84) + imeBottomOffset else 0, systemInsetTop)
        if (duoLayoutActive) {
            (duoControls?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val bottom = Tuils.dpToPx(mContext, 18) + imeBottomOffset
                if (params.bottomMargin != bottom) {
                    params.bottomMargin = bottom
                    duoControls?.layoutParams = params
                }
            }
            (moduleSuggestionsScroll?.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                val bottom = Tuils.dpToPx(mContext, 74) + imeBottomOffset
                if (params.bottomMargin != bottom) {
                    params.bottomMargin = bottom
                    moduleSuggestionsScroll?.layoutParams = params
                }
            }
            for (id in intArrayOf(R.id.apps_drawer_root, R.id.android_widget_drawer_root)) {
                val drawer = mRootView.findViewById<View>(id) ?: continue
                val params = drawer.layoutParams as? FrameLayout.LayoutParams ?: continue
                val bottom = Tuils.dpToPx(mContext, 84) + imeBottomOffset
                if (params.bottomMargin != bottom) {
                    params.bottomMargin = bottom
                    drawer.layoutParams = params
                }
            }
        }
        if (splitDuoStatusActive) {
            applySectionDisplayMargins(headerContainer, topMargins, metrics, 0, systemInsetTop)
        }
        val searchInset = ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.searchEdgeInset(mContext!!,searchOnlyMode)
        applySectionDisplayMargins(
            terminalTrayContainer,
            bottomMargins,
            metrics,
            imeBottomOffset + searchInset,
            extraTopPx = if (duoTopPanesSwapped) {
                systemInsetTop + Tuils.mmToPx(metrics, topMargins[1]) + (headerContainer?.height ?: 0)
            } else if (landscape) systemInsetTop else 0,
            extraHorizontalPx = searchInset
        )
        if(duoLayoutActive) ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.finishDuoLayout(mRootView as ViewGroup,
            ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),
            ohi.andre.consolelauncher.presentation.LauncherHomeSnapshot.Insets(systemInsetLeft,systemInsetTop,systemInsetRight,systemInsetBottom),captureTrayPresentationState())
        homeModes.active.onDisplayMargins(topMargins, bottomMargins, metrics, imeBottomOffset, searchInset, systemInsetTop)
        applyTerminalOverlayDisplayMargins(topMargins, bottomMargins, metrics)
    }

    private fun applyTerminalOutputEdgeInsets(landscape: Boolean) {
        mRootView?.let { ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.applyEdgeInsets(it,landscape,
            (statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved())) }
    }

    private fun getDisplayMargins(save: Ui?): IntArray {
        return XMLPrefsManager.getListOfIntValues(XMLPrefsManager.get(save), 4, 0)
    }

    private fun applySectionDisplayMargins(
        view: View?,
        marginMm: IntArray,
        metrics: DisplayMetrics?,
        extraBottomPx: Int,
        extraTopPx: Int = 0,
        extraHorizontalPx: Int = 0
    ) {
        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.applySectionMargins(view,marginMm,metrics,extraBottomPx,extraTopPx,extraHorizontalPx)
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
        overlayDisplayMarginTop = systemInsetTop + Tuils.mmToPx(metrics, topMargins[1])
        overlayDisplayMarginRight = max(
            Tuils.mmToPx(metrics, topMargins[2]),
            Tuils.mmToPx(metrics, bottomMargins[2])
        )
        overlayDisplayMarginBottom = Tuils.mmToPx(metrics, bottomMargins[3])

        appDrawerPaneManager?.applyDisplayMargins(
            overlayDisplayMarginLeft,
            overlayDisplayMarginTop,
            overlayDisplayMarginRight,
            overlayDisplayMarginBottom
        )
        androidWidgetDrawerManager?.applyDisplayMargins(
            overlayDisplayMarginLeft,
            overlayDisplayMarginTop,
            overlayDisplayMarginRight,
            overlayDisplayMarginBottom + if (duoLayoutActive) 0 else imeBottomOffset
        )
        applyTermuxImeBottomPadding()
        fileConsoleController?.applyDisplayMargins(overlayDisplayMarginLeft, overlayDisplayMarginTop, overlayDisplayMarginRight, overlayDisplayMarginBottom)
        hackOverlayController?.applyDisplayMargins(overlayDisplayMarginLeft, overlayDisplayMarginTop, overlayDisplayMarginRight, overlayDisplayMarginBottom)
    }

    private fun styleTerminalTrayToggle() {
        TerminalTrayToggleView.style(
            mContext,
            terminalTrayToggle,
            terminalOutputBorder,
            this.isOutputHeaderNone,
            this.isOutputHeaderArrowsOnly,
            getColor(Theme.tab_text_color),
            outputHeaderTextSize(),
            terminalHeaderTabBackground(),
            onClick = {
                if (this.isOutputTrayToggleEnabled) {
                    setTerminalTrayExpanded(!terminalTrayExpanded)
                }
            }
        )
        updateTerminalTrayToggleText()
        applyTerminalOutputContentInsets()
        terminalTrayToggle?.post(Runnable {
            applyTerminalOutputContentInsets()
            applyTerminalTrayState(false)
        })
    }

    private fun setTerminalTrayExpanded(expanded: Boolean) {
        if (!this.isOutputTrayToggleEnabled) {
            return
        }
        terminalTrayExpanded = expanded
        if (expanded) outputUnread = false
        saveTerminalTrayState()
        if (this.isOutputAutoHideEnabled && expanded) {
            scheduleTerminalOutputAutoHide()
        } else {
            cancelTerminalOutputAutoHide()
        }
        applyTerminalTrayState(true)
    }

    private fun onTerminalOutputChanged(output: CharSequence?, appended: CharSequence? = output) {
        if (searchOnlyMode) {
            suggestionsManager?.showInlineOutput(appended)
            return
        }
        if (this.isOutputAutoHideEnabled) {
            terminalTrayExpanded = !output.isNullOrEmpty()
            if (terminalTrayExpanded) scheduleTerminalOutputAutoHide() else cancelTerminalOutputAutoHide()
        }
        val unread = when {
            output.isNullOrEmpty() -> false
            this.isOutputTrayToggledMode && !this.isOutputAutoHideEnabled && !terminalTrayExpanded -> outputUnread || !appended.isNullOrBlank()
            else -> false
        }
        if (unread != outputUnread) {
            outputUnread = unread
            updateTerminalTrayToggleText()
        }
        applyTerminalTrayState(false)
    }

    private fun scheduleTerminalOutputAutoHide() {
        val container = terminalContainer ?: return
        container.removeCallbacks(terminalOutputAutoHideRunnable)
        container.postDelayed(
            terminalOutputAutoHideRunnable,
            outputAutoHideDelayMs(XMLPrefsManager.getInt(Behavior.output_auto_hide_seconds))
        )
    }

    private fun cancelTerminalOutputAutoHide() {
        terminalContainer?.removeCallbacks(terminalOutputAutoHideRunnable)
    }

    private fun restoreTerminalTrayState() {
        if (this.isOutputAutoHideEnabled) {
            terminalTrayExpanded = false
        } else if (this.isOutputTrayToggledMode) {
            terminalTrayExpanded = preferences.getBoolean(PREF_OUTPUT_TRAY_EXPANDED, false)
        } else if (this.isOutputTrayAutoMode) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule)
        } else {
            terminalTrayExpanded = false
            preferences.edit().remove(PREF_OUTPUT_TRAY_EXPANDED).apply()
        }
    }

    private fun saveTerminalTrayState() {
        if (this.isOutputTrayToggledMode && !this.isOutputAutoHideEnabled) {
            preferences.edit().putBoolean(PREF_OUTPUT_TRAY_EXPANDED, terminalTrayExpanded).apply()
        }
    }

    private fun moveTopSectionUp(distance: Int) {
        val header = headerContainer ?: return
        val params = header.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.topMargin != -distance) {
            params.topMargin = -distance
            header.layoutParams = params
        }
    }

    private fun applyTerminalTrayState(refocusInput: Boolean) {
        if (terminalContainer == null) {
            return
        }

        if (searchOnlyMode || landscapeLayoutActive) moveTopSectionUp(0)
        if (searchOnlyMode) {
            configureSearchOnlyLayout()
            if (refocusInput) mTerminalAdapter?.requestInputFocus()
            return
        }

        val headerOnlyCollapse = this.isOutputAutoHideEnabled || this.isOutputTrayToggledMode
        terminalOutputBorder?.visibility = outputPaneVisibility(
            headerOnlyCollapse,
            terminalTrayExpanded
        )

        if (landscapeLayoutActive && this.isOutputTrayToggleEnabled) {
            val trayParams = terminalTrayContainer?.layoutParams as? FrameLayout.LayoutParams
            if (trayParams != null) {
                trayParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                trayParams.height = if (terminalTrayExpanded) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                trayParams.gravity = Gravity.BOTTOM
                terminalTrayContainer?.layoutParams = trayParams
            }
            if (terminalTrayExpanded) {
                val params = terminalContainer!!.layoutParams
                if (params is LinearLayout.LayoutParams) {
                    params.height = 0
                    params.weight = 1f
                    terminalContainer!!.layoutParams = params
                } else if (params != null) {
                    params.height = ViewGroup.LayoutParams.MATCH_PARENT
                    terminalContainer!!.layoutParams = params
                }
                updateTerminalTrayToggleText()
                if (refocusInput && mTerminalAdapter != null) {
                    mTerminalAdapter!!.requestInputFocus()
                    mTerminalAdapter!!.scrollToEnd()
                }
                return
            }
        }

        if (landscapeLayoutActive && !duoLayoutActive && !this.isOutputTrayToggleEnabled) {
            val trayParams = terminalTrayContainer?.layoutParams as? FrameLayout.LayoutParams
            if (trayParams != null && trayParams.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                trayParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                terminalTrayContainer?.layoutParams = trayParams
            }
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
        val requestedHeight: Int
        if (this.isOutputAutoHideEnabled) {
            requestedHeight = if (terminalTrayExpanded) {
                calculateNativeTerminalTrayHeight(expandedHeight)
            } else {
                calculateHeaderOnlyTerminalTrayHeight()
            }
        } else if (this.isOutputTrayNativeMode) {
            requestedHeight = if (TextUtils.isEmpty(activeModule)) {
                calculateNativeTerminalTrayHeight(expandedHeight)
            } else {
                collapsedHeight
            }
        } else if (this.isOutputTrayAutoMode) {
            terminalTrayExpanded = TextUtils.isEmpty(activeModule)
            requestedHeight = if (terminalTrayExpanded) expandedHeight else collapsedHeight
        } else {
            requestedHeight = if (terminalTrayExpanded) expandedHeight else calculateHeaderOnlyTerminalTrayHeight()
        }
        val room = ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.roomBelowTopContent(
            mRootView!!, rootHeight, persistentBottomChromeHeight())
        val maxPx = UIUtils.dpToPx(mContext!!, outputTrayMaxHeightDp())
        val fittedHeight = ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.fitRoom(
            requestedHeight,
            if (maxPx > 0) min(room, maxPx) else room,
            room,
            calculateHeaderOnlyTerminalTrayHeight(),
            !this.isOutputAutoHideEnabled && (this.isOutputTrayNativeMode || this.isOutputTrayAutoMode),
            ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.minimumOpen(terminalOutputChromeHeight(),
                max(UIUtils.dpToPx(mContext!!, 18), terminalView?.lineHeight ?: 0), !TextUtils.isEmpty(activeModule))
        )
        val targetHeight = constrainTerminalOutputHeight(
            fittedHeight,
            rootHeight,
            imeBottomOffset,
            systemInsetBottom,
            persistentBottomChromeHeight(),
            imeInsetVisible || imeBottomOffset > 0
        )
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            terminalContainer!!.setLayoutParams(params)
        }
        // With the output down to its tab, notification rows that still don't fit push the status rows off screen.
        moveTopSectionUp(
            if (homeModules[activeModule]?.pushesStatusRowsUp != true) 0
            else ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.topSectionOverlap(
                mRootView!!, rootHeight, persistentBottomChromeHeight(), targetHeight)
        )

        updateTerminalTrayToggleText()
        if (terminalTrayExpanded && refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter!!.scrollToEnd()
        }
        if (refocusInput && mTerminalAdapter != null) {
            mTerminalAdapter!!.requestInputFocus()
        }
    }

    // Output, typing and theme refreshes re-apply this layout too; only a real layout change ([closeDrawers]) may close
    // a drawer the user opened, such as apps -ls in Search or Terminal mode.
    private fun configureSearchOnlyLayout(closeDrawers: Boolean = false) = searchSurface.configure(closeDrawers)

    /** Opens [module] (or the last one) under the MODULES chips in the results; says so when the dock has no modules. */
    fun openSearchModules(module: String?, toggle: Boolean = false): Boolean = searchSurface.openModule(module, toggle)

    private fun calculateExpandedTerminalTrayHeight(rootHeight: Int, collapsedHeight: Int): Int {
        val limit=ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.expandedLimit(rootHeight,collapsedHeight,keyboardVisible,
            UIUtils.dpToPx(mContext!!,if(keyboardVisible) 220 else 320),UIUtils.dpToPx(mContext!!,outputTrayMaxHeightDp()))
        return calculateNativeTerminalTrayHeight(limit)
    }

    // Output arrived while the toggled pane was closed; cleared when it opens.
    private var outputUnread = false

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
            this.isOutputAutoHideEnabled,
            getColor(Theme.tab_text_color),
            outputUnread
        )
    }

    private val isOutputTrayNativeMode: Boolean
        get() = OUTPUT_TRAY_MODE_NATIVE == outputTrayMode()

    private val isOutputTrayAutoMode: Boolean
        get() = OUTPUT_TRAY_MODE_AUTO == outputTrayMode()

    private val isOutputTrayToggledMode: Boolean
        get() = OUTPUT_TRAY_MODE_TOGGLED == outputTrayMode()

    private val isOutputAutoHideEnabled: Boolean
        get() = !this.isOutputHeaderNone && XMLPrefsManager.getBoolean(Behavior.auto_hide_output)

    private val isOutputTrayToggleEnabled: Boolean
        get() = this.isOutputTrayToggledMode || this.isOutputAutoHideEnabled

    private val isOutputHeaderArrowsOnly: Boolean
        get() = OUTPUT_HEADER_MODE_ARROWS == outputHeaderMode()

    private val isOutputHeaderNone: Boolean
        get() = OUTPUT_HEADER_MODE_NONE == outputHeaderMode()

    private fun outputHeaderMode(): String {
        return AppearanceSettings.outputHeaderMode()
    }

    private fun outputTrayMode(): String {
        val mode = XMLPrefsManager.get(Behavior.output_tray_mode).trim { it <= ' ' }.lowercase()
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
        return ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.contentHeight(terminalView,terminalOutputChromeHeight(),
            UIUtils.dpToPx(mContext!!,66),UIUtils.dpToPx(mContext!!,if(keyboardVisible) 96 else 132),UIUtils.dpToPx(mContext!!,18))
    }

    private fun calculateHeaderOnlyTerminalTrayHeight(): Int =
        headerOnlyTerminalTrayHeight(
            terminalTrayToggle?.bottom ?: 0,
            UIUtils.dpToPx(mContext!!, 32)
        )

    private fun calculateNativeTerminalTrayHeight(maxHeight: Int): Int {
        return ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.contentHeight(terminalView,terminalOutputChromeHeight(),
            UIUtils.dpToPx(mContext!!,66),maxHeight,UIUtils.dpToPx(mContext!!,18))
    }

    private fun applyTerminalOutputContentInsets() {
        mRootView?.let { ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.applyContentInsets(it,
            (statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved())) }
    }

    private fun terminalOutputChromeHeight(): Int {
        return mRootView?.let { ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.chromeHeight(it) }?:0
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
        homeModulesContainer = homePage.findViewById<ViewGroup?>(R.id.home_modules_container)
        if (homeModulesContainer == null) return
        // The output fills the room below the open module, so refit when it changes height.
        homeModulesContainer!!.addOnLayoutChangeListener { view, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) view.post { applyTerminalTrayState(false) }
        }

        luaModules.ensureSystemLuaModules()
        activeModule = ""
        ModuleManager.setActiveModule(mContext, "")
        homeModulesContainer!!.removeAllViews()
        rebuildModuleDock()
        refreshSuggestionsForActiveModule()
        attachUnifiedBottomConsoleIfReady()
    }

    private fun isTermuxLandscapeKeyTray(): Boolean {
        return shouldUseResponsiveLandscape(currentConfiguration)
    }

    private fun termuxAppCombinedKeyTray(): Boolean {
        return termuxAppSession != null && isTermuxLandscapeKeyTray()
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

    private fun updateTermuxWorkspaceModifierButton(
        key: TextView,
        label: String,
        active: Boolean,
        normalColor: Int,
        activeColor: Int
    ) {
        key.text = if (active) "$label*" else label
        key.alpha = if (active) 1f else 0.82f
        if (active) {
            key.setTextColor(terminalWindowBackground())
            key.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    activeColor,
                    ThemeColorResolver.color(Theme.workspace_control_text_color),
                    1f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
                )
            )
        } else {
            key.setTextColor(normalColor)
            key.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun tmuxWorkspacePrintableKeyName(ch: Char): String? {
        if (Character.isISOControl(ch)) {
            return null
        }
        return if (ch == ' ') "Space" else ch.toString()
    }

    private fun termuxWorkspaceCharWidth(output: TextView?): Float {
        return if (output != null) {
            max(1f, output.paint.measureText("M"))
        } else {
            max(1f, UIUtils.dpToPx(mContext!!, 8).toFloat())
        }
    }

    private fun handleTermuxWorkspaceExternalCommand(rawCommand: String?) {
        ohi.andre.consolelauncher.managers.termux.TermuxWorkspaceActivity.open(mContext, rawCommand)
    }

    internal class TermuxWorkspaceGeometry(
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

    private fun rebuildModuleDock() {
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
        if (moduleSuggestionsOriginalParent == null && moduleSuggestionsScroll!!.parent is ViewGroup) {
            moduleSuggestionsOriginalParent = moduleSuggestionsScroll!!.parent as ViewGroup
            moduleSuggestionsOriginalIndex = moduleSuggestionsOriginalParent!!.indexOfChild(moduleSuggestionsScroll)
            moduleSuggestionsOriginalParams = copyLayoutParams(moduleSuggestionsScroll!!.layoutParams)
        }
        moduleSuggestionsScroll!!.setFocusable(false)
        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(moduleSuggestionsScroll!!,
            ohi.andre.consolelauncher.presentation.HomeAppearance.saved())
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
        if (unifiedStatusActive && !unifiedModulesVisible) {
            hideModuleSuggestionsStrip()
            return
        }
        val context = mContext ?: return
        val module = activeModule
        val restoreX = moduleSuggestionScrollTarget(module, moduleSuggestionsModule, scroll.scrollX, pendingModuleSuggestionsScrollX)
        moduleSuggestionsModule = module
        pendingModuleSuggestionsScrollX = restoreX
        group.removeAllViews()
        if (homeModules[activeModule]?.showsSuggestions == false) {
            hideModuleSuggestionsStrip()
            return
        }
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
        val appearance=ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        for (suggestion in suggestions) {
            val chip=ohi.andre.consolelauncher.presentation.ModuleSuggestionPresentation.create(context,suggestion.label.orEmpty(),appearance)
            chip.setOnClickListener(View.OnClickListener { v: View? ->
                val action = suggestion.action.orEmpty()
                if (action.endsWith(" ")) {
                    mTerminalAdapter?.setInput(action, null)
                } else {
                    mTerminalAdapter?.executeQuietly(action, action)
                }
                refreshSuggestionsForActiveModule()
            })
            group.addView(chip,ohi.andre.consolelauncher.presentation.ModuleSuggestionPresentation.layoutParams(appearance))
        }
        scroll.visibility = View.VISIBLE
        scroll.post(Runnable {
            if (moduleSuggestionsModule == module) {
                scroll.scrollTo(restoreX, 0)
                pendingModuleSuggestionsScrollX = 0
            }
        })
    }

    private fun hideModuleSuggestionsStrip() {
        moduleSuggestionsModule = null
        pendingModuleSuggestionsScrollX = 0
        moduleSuggestionsGroup?.removeAllViews()
        moduleSuggestionsScroll?.visibility = View.GONE
    }

    private fun addModuleDockButton(module: String?) {
        val button = ModuleDockButtonFactory.create(
            mContext,
            module,
            LauncherSettings.getInt(Ui.module_dock_spacing_dp),
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

    private val moduleHost = object : HomeModuleHost {
        override val context: Context get() = mContext
        override fun renderText(moduleId: String?, text: CharSequence?) = showTextModule(moduleId, text)
        override fun bodyTextColor() = notificationWidgetTextColor()
        override fun accentTextColor() = moduleNameTextColor()
        override fun bodyTextSize() = moduleBodyTextSize()
        override fun appearance() = statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        override fun applyBodyTypeface(view: TextView, moduleId: String?, fontMode: String?) = applyModuleBodyTypeface(view, moduleId, fontMode)
        override val root: View? get() = mRootView
        override val moduleContainer: ViewGroup? get() = homeModulesContainer
        override val handler: Handler? get() = this@UIManager.handler
        override val activeModuleId: String? get() = activeModule
        override val windowFocused: Boolean get() = launcherWindowFocused
        override fun presentModule(moduleId: String) = showHomeModule(moduleId)
        override fun closeModule() = closeHomeModule()
        override fun refreshSuggestions() = refreshSuggestionsForActiveModule()
        override fun refreshContextVisibility() = updateContextContainerVisibility(mRootView)
        override fun refreshDockSelection() = updateModuleDockSelection()
        override fun repaintTextModule(moduleId: String?) = repaintActiveTextModule(moduleId)
        override fun executeCommand(command: String?) = executeLuaWidgetCommand(command)
        override fun launchActivity(intent: Intent) = startActivityAfterChromeHidden(intent)
        override fun bindCloseButton(close: TextView) {
            close.setOnClickListener { closeHomeModule() }
            close.setTextColor(moduleNameTextColor())
            close.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            close.setTextSize(moduleHeaderTextSize().toFloat())
        }
        override fun styleCloseButton(close: TextView?, target: FrameTarget) = styleModuleClose(close, target)
        override fun addCommandButtons(parent: LinearLayout, commands: List<Pair<String, String>>) = luaModules.layout.addLuaButtonGrid(
            parent, commands.mapTo(ArrayList()) { (label, command) -> LuaSurfaceAction(label) { executeLuaWidgetCommand(command) } })
    }

    private val musicModule: MusicModule by lazy {
        MusicModule(moduleHost, { mainPack }, { homeModes.active.onDataChanged() }, {
            syncPodcastPill()
        })
    }

    private val luaModules: LuaModules by lazy { LuaModules(moduleHost) { value -> actionLuaApp(value) } }

    private val notificationsModule by lazy {
        NotificationsModule(moduleHost) { notifications ->
            suggestionsManager?.setSearchNotifications(notifications)
            homeModes.active.onDataChanged()
        }
    }

    /** Built-in Home modules. A new one only needs its class and an entry here. */
    private val homeModules by lazy {
        HomeModules(listOf(
            musicModule, notificationsModule, TimerModule(), CalendarModule(), ReminderModule(), NotesModule(), RssModule { mainPack.rssManager },
            WeatherModule(
                reading = {
                    WeatherModule.Reading(lastWeatherText.takeUnless { TextUtils.isEmpty(it) } ?: labelTexts[Label.weather.ordinal],
                        lastWeatherSymbol, lastWeatherUpdateMillis)
                },
                ensureUpdating = {
                    if (weatherManager == null) {
                        weatherDelay = XMLPrefsManager.getInt(Behavior.weather_update_time) * 1000
                        weatherManager = WeatherManager(
                            mContext!!,
                            weatherDelay.toLong(),
                            labelSizes[Label.weather.ordinal],
                            statusUpdateListener
                        ).also { it.start() }
                    }
                },
            ),
        ))
    }

    private fun showHomeModule(module: String?) {
        if (homeModulesContainer == null) return

        val id = ModuleManager.normalize(module)
        if (!ModuleManager.isKnown(mContext, id)) {
            return
        }

        val dockScrollX = consumeModuleDockScrollX()
        if (handler != null) {
            handler!!.removeCallbacks(eventsRefreshRunnable)
            luaModules.cancelTick()
        }
        if (id != activeModule) homeModules[id]?.onOpening()
        activeModule = id
        ModuleManager.setActiveModule(mContext, id)
        updateModuleDockSelection()
        applyTerminalTrayState(false)
        homeModulesContainer!!.removeAllViews()

        val builtIn = homeModules[id]
        if (luaModules.showLuaModuleIfSource(id)) {
            // Rendered above.
        } else if (builtIn != null) {
            builtIn.show(moduleHost)
        } else {
            refreshLauncherModuleTextIfNeeded(id)
            val text = ModuleManager.getScriptText(mContext, id)
            showTextModule(id, if (TextUtils.isEmpty(text)) mContext.getString(R.string.surface_no_module_output_yet_ba2f9) else text)
        }
        refreshSuggestionsForActiveModule()
        scheduleEventsRefreshIfNeeded()
        preserveModuleDockScrollX(dockScrollX)
        scheduleTypefaceRefreshes()
        if (unifiedStatusActive) {
            setUnifiedModulesVisible(true, true)
        } else {
            updateUnifiedBottomModuleBounds()
        }
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

    fun buildLuaPreview(module: String, result: LuaWidgetEngine.RenderResult): View = luaModules.buildLuaPreview(module, result)

    private fun executeLuaWidgetCommand(command: String?) {
        if (TextUtils.isEmpty(command) || mTerminalAdapter == null) {
            return
        }
        mTerminalAdapter!!.executeInput(command)
    }

    private fun showTextModule(module: String?, text: CharSequence?) {
        val moduleView = ohi.andre.consolelauncher.presentation.ModulePanelPresentation.inflate(mContext,homeModulesContainer,ohi.andre.consolelauncher.presentation.ModulePanelPresentation.Kind.TEXT)
        homeModulesContainer!!.addView(moduleView)

        val label = moduleView.findViewById<TextView?>(R.id.module_text_label)
        val body = moduleView.findViewById<TextView?>(R.id.module_text_body)
        val close = moduleView.findViewById<TextView?>(R.id.module_text_close)
        val scroll = moduleView.findViewById<ScrollView?>(R.id.module_text_border)
        if (label != null) {
            label.setText(ModuleManager.displayTitle(mContext, module))
        }
        if (body != null && scroll != null && homeModules[ModuleManager.normalize(module)]?.renderBody(moduleHost, text, body, scroll) == true) {
            // Drawn by the module.
        } else if (shouldRenderScriptSegments(module) && body != null && scroll != null) {
            renderScriptModuleSegments(module, text?.toString(), body, scroll)
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
            moduleNameTextColor(),
            FrameTarget.MODULES
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

        val content = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(mContext)
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
        val view = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.paragraph(mContext)
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
        val id=ModuleManager.normalize(module)
        val source=ModuleManager.getModuleSource(mContext,id)
        val events=ModuleManager.EVENTS==id && ModuleManager.isLauncherSource(source)
        if(!events || scroll==null || body==null) return
        val lines=countVisibleLines(CalendarModule.monthText(mContext))
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.limitTextScroll(scroll,
            ohi.andre.consolelauncher.presentation.ModulePanelPresentation.ScrollPolicy(lines,false))
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
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.body(body,
            statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),useThemeTypeface)
    }

    private fun moduleBodyUsesThemeTypeface(module: String?): Boolean {
        val id = ModuleManager.normalize(module)
        if (homeModules[id]?.usesThemeTypeface == true || ModuleManager.EVENTS == id || "focus_sprint" == id) {
            return true
        }

        val source = ModuleManager.getModuleSource(mContext, id)
        if (ModuleManager.isLuaSource(source)) {
            val widgetId = ModuleManager.luaWidgetId(source)
            return LuaWidgetManager.SYSTEM_TIMER_WIDGET_ID == widgetId || "focus_sprint" == widgetId
        }
        return false
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

    private fun styleModuleClose(close: TextView?, target: FrameTarget = FrameTarget.MODULES) {
        if (close == null) return
        ohi.andre.consolelauncher.presentation.ModuleContentPresentation.close(close,
            statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),target)
    }

    private fun closeHomeModule() {
        // A module's own close button in the carousel closes the carousel (which then closes the module).
        if (isSearchModulesOpen) { searchSurface.closeModules(); return }
        val dockScrollX = consumeModuleDockScrollX()
        if (handler != null) {
            handler!!.removeCallbacks(eventsRefreshRunnable)
            luaModules.cancelTick()
        }
        activeModule = ""
        ModuleManager.setActiveModule(mContext, "")
        if (homeModulesContainer != null) {
            homeModulesContainer!!.removeAllViews()
        }
        updateModuleDockSelection()
        updateUnifiedBottomModuleBounds()
        applyTerminalTrayState(false)
        refreshSuggestionsForActiveModule()
        preserveModuleDockScrollX(dockScrollX)
        if (unifiedStatusActive && unifiedModulesVisible) setUnifiedModulesVisible(true, true)
    }

    private fun refreshSuggestionsForActiveModule() {
        TextShadows.applyModule(homeModulesContainer)
        refreshModuleSuggestionsStrip()
        // Always rebuild stock chips for current input. Empty-only skipped cases where
        // setText("") didn't fire the watcher (already empty) after module/music actions.
        if (suggestionsManager != null) {
            val input = mTerminalAdapter?.input ?: Tuils.EMPTYSTRING
            suggestionsManager!!.requestSuggestion(input)
        }
    }

    private fun redrawActiveModuleFor(event: HomeModuleEvent) {
        if (homeModules[activeModule]?.refreshOn?.contains(event) == true) showHomeModule(activeModule)
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

    private fun handleModuleCommand(intent: Intent) {
        val command = intent.getStringExtra(EXTRA_MODULE_COMMAND)
        val module = intent.getStringExtra(EXTRA_MODULE_NAME)

        if ("rebuild" == command) {
            searchSurface.refreshModules()
            rebuildModuleDock()
            if (homeModulesContainer != null && !TextUtils.isEmpty(activeModule) && ModuleManager.getDock(
                    mContext
                ).contains(activeModule)
            ) {
                showHomeModule(activeModule)
            }
        } else if ("show" == command) {
            if (searchOnlyMode) openSearchModules(module) else showHomeModule(module)
        } else if ("search_toggle" == command) {
            openSearchModules(module, toggle = true)
        } else if ("close" == command) {
            if (isSearchModulesOpen) searchSurface.closeModules() else closeHomeModule()
        } else if ("update" == command) {
            if (!TextUtils.isEmpty(module) && module == activeModule) {
                showHomeModule(module)
            }
            rebuildModuleDock()
        } else if ("refresh" == command) {
            refreshScriptModule(module)
        } else if ("library_remove" == command) {
            val id = ModuleManager.normalize(module)
            luaModules.disposeEngine(id)
            if (activeModule == id) closeHomeModule()
            runCatching { ohi.andre.consolelauncher.managers.modules.ModuleLibrary.remove(mContext, id) }
                .onFailure { Tuils.sendOutput(mContext, "Could not remove module: " + it.message) }
            rebuildModuleDock()
        } else {
            luaModules.handleCommand(command, module, intent)
        }
    }

    private fun refreshScriptModule(module: String?) {
        val id = ModuleManager.normalize(module)
        val source = ModuleManager.getModuleSource(mContext, id)
        if (TextUtils.isEmpty(source)) {
            Tuils.sendOutput(mContext, mContext.getString(R.string.surface_dispatch_uimanager_module_has_no_source_6c6aa, id))
            return
        }
        if (ModuleManager.isLauncherSource(source)) {
            refreshLauncherModule(id, source)
            return
        }
        if (ModuleManager.isLuaSource(source)) {
            luaModules.renderLuaWidgetModule(id, true, true)
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
            Tuils.sendOutput(mContext, mContext.getString(R.string.surface_unknown_launcher_module_source_3c041, source))
            return
        }

        val id = ModuleManager.normalize(module)
        ModuleManager.setScriptText(mContext, id, payload)
        if (id == activeModule) {
            showHomeModule(id)
        }
        updateModuleDockSelection()
        if (announce) {
            Tuils.sendOutput(mContext, mContext.getString(R.string.surface_module_refreshed_97f96, id))
        }
    }

    private fun repaintActiveTextModule(id: String?) {
        if (homeModulesContainer == null) {
            return
        }
        homeModulesContainer!!.removeAllViews()
        val text = ModuleManager.getScriptText(mContext, id)
        showTextModule(id, if (TextUtils.isEmpty(text)) mContext.getString(R.string.surface_no_module_output_yet_ba2f9) else text)
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
        termuxOutputLabel?.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            view.translationY = -(view.top + view.height / 2f)
        }
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
            termuxInput!!.setText(retainedTermuxInputDraft)
            termuxInput!!.setSelection(termuxInput!!.text.length)
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
        val color = ThemeColorResolver.color(Theme.workspace_control_text_color)
        for (i in termuxKeySlots.indices) {
            val key = termuxKeySlots[i]
            val label = labels.getOrNull(i) ?: ""
            val action = actions.getOrNull(i)
            key.text = label
            key.textSize = if (label.length >= 5) 10f else 11f
            key.alpha = 1f
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
        val normalColor = ThemeColorResolver.color(Theme.workspace_control_text_color)
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
            key.alpha = 1f
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
                        v?.performClick()
                        true
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
        val normalColor = ThemeColorResolver.color(Theme.workspace_control_text_color)
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
        termuxAppLastStatus = mContext.getString(R.string.surface_key_9a5bb, clean)
        dispatchTermuxAppScript("key", TermuxAppScripts.buildTermuxAppControlScript(app, clean, ::calculateTermuxAppGeometry), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
        scheduleTermuxConsoleFocusCapture(true)
    }

    init {
        clockTabDock = ClockTabDock(
            mContext,
            rootView,
            { preferences },
            { systemInsetLeft },
            { systemInsetTop },
            { systemInsetRight },
            { systemInsetBottom },
            { podcastLandscapePresentation() },
            { root -> styleClockOverlay(root) }
        )
        this.mRootView = rootView
        this.mainPack = mainPack
        this.mExecuter = executer

        val filter = IntentFilter()
        filter.addAction(ACTION_UPDATE_SUGGESTIONS)
        filter.addAction(ACTION_UPDATE_APPS)
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
        homeModules.broadcasts.forEach(filter::addAction)
        filter.addAction(ACTION_CLOCK_STATE)
        filter.addAction(ACTION_POMODORO_STATE)
        filter.addAction(ACTION_LOCKDOWN_STATE)
        filter.addAction(ACTION_TERMUX_CONSOLE)
        filter.addAction(ACTION_TMUX_WORKSPACE)
        filter.addAction(ACTION_LUA_APP)
        filter.addAction(ACTION_FILE_CONSOLE)
        filter.addAction(ACTION_PROFILE_SURFACE)
        filter.addAction(ACTION_TERMUX_RESULT)
        filter.addAction(ACTION_MODULE_COMMAND)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getAction()

                if (action == ACTION_UPDATE_SUGGESTIONS) {
                    refreshSuggestionsNow()
                    refreshModuleSuggestionsStrip()
                } else if (action == ACTION_UPDATE_APPS) {
                    refreshSuggestionsNow()
                    refreshModuleSuggestionsStrip()
                    appDrawerPaneManager?.refresh()
                } else if (action == ACTION_UPDATE_HINT) {
                    mTerminalAdapter!!.setDefaultHint()
                    fileConsoleController?.refreshFileConsole(false)
                } else if (action == ACTION_ROOT) {
                    mTerminalAdapter!!.onRoot()
                } else if (action == ACTION_CLOCK_STATE) {
                    clockTabDock?.updateClockOverlay(intent)
                    redrawActiveModuleFor(HomeModuleEvent.CLOCK)
                } else if (action == ACTION_POMODORO_STATE) {
                    focusOverlayController?.updatePomodoroOverlay(intent)
                    redrawActiveModuleFor(HomeModuleEvent.CLOCK)
                } else if (action == ACTION_LOCKDOWN_STATE) {
                    focusOverlayController?.updateLockdownOverlay(intent)
                    redrawActiveModuleFor(HomeModuleEvent.CLOCK)
                } else if (action == ACTION_TERMUX_CONSOLE) {
                    openTermuxConsole(intent.getStringExtra(EXTRA_TERMUX_COMMAND))
                } else if (action == ACTION_TMUX_WORKSPACE) {
                    handleTermuxWorkspaceExternalCommand(intent.getStringExtra(EXTRA_TMUX_WORKSPACE_COMMAND))
                } else if (action == ACTION_LUA_APP) {
                    openLuaApp(intent.getStringExtra(EXTRA_LUA_APP_ID))
                } else if (action == ACTION_FILE_CONSOLE) {
                    openFileConsole(intent.getStringExtra(EXTRA_FILE_COMMAND))
                } else if (action == ACTION_PROFILE_SURFACE) {
                    openProfileSurface()
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

                        Tuils.sendOutput(context, mContext.getString(R.string.surface_logged_to_ee37a, file.getAbsolutePath()))
                    } catch (e: Exception) {
                        Tuils.sendOutput(ThemeColorResolver.color(Theme.error_text_color), context, e.toString())
                    }
                } else if (action == ACTION_CLEAR) {
                    mTerminalAdapter!!.clear()
                    if (suggestionsManager != null) suggestionsManager!!.requestSuggestion(Tuils.EMPTYSTRING)
                    // Search and Terminal modes draw the "$ clear" echo into their output card from a queued update;
                    // clear that card after it lands.
                    if (searchOnlyMode) terminalContainer?.post { suggestionsManager?.requestSuggestion(Tuils.EMPTYSTRING) }
                } else if (action == ACTION_HACK) {
                    hackOverlayController?.playHackOverlay()
                } else if (action == ACTION_WEATHER) {
                    val c = Calendar.getInstance()

                    var s = intent.getCharSequenceExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) s = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) return

                    lastWeatherText = s
                    lastWeatherSymbol = intent.getStringExtra(WEATHER_SYMBOL)
                    lastWeatherCondition = intent.getStringExtra(WEATHER_CONDITION)
                    lastWeatherDetails = intent.getStringExtra(WEATHER_DETAILS)
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
                    redrawActiveModuleFor(HomeModuleEvent.WEATHER)
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
                } else if (homeModules.handlesBroadcast(action)) {
                    homeModules.onBroadcast(intent)
                } else if (action == ACTION_CLOCK_STATE) {
                    clockTabDock?.updateClockOverlay(intent)
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

        restoreLauncherSurfaceSession()

        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleRoot(rootView,
            ohi.andre.consolelauncher.presentation.HomeAppearance.saved(),canApplyTheme)

        focusOverlayController = FocusOverlayController(
            mContext!!,
            rootView,
            { closeKeyboard() },
            { launcherWindowFocused },
            { pauseAsciiAnimation() },
            { resumeAsciiAnimation() }
        )
        hackOverlayController = HackOverlayController(mContext!!, rootView, { handler }, { closeKeyboard() })
        hackOverlayController?.styleHackOverlay(rootView)
        setupTermuxConsole(rootView)
        fileConsoleController = FileConsoleController(
            mContext!!,
            rootView,
            { mainPack },
            { command -> mExecuter.execute(command, null) },
            { input, mode -> applyRetuiKeyboardTheme(input, mode) },
            { input, mode -> sendRetuiKeyboardTheme(input, mode) },
            { button, color -> styleTermuxToolButton(button, color) },
            { action -> runOnMainThread(action) },
            {
                closeTermuxConsole(false)
                profilePaneController?.hide()
            },
            { hideHomeSuggestionsForTermux() },
            { restoreHomeSuggestionsAfterTermux() },
            { mTerminalAdapter?.focusInputEnd() }
        )
        fileConsoleController?.setupFileConsole()
        profilePaneController = ProfilePaneController(mContext!!, rootView) { closeProfileSurface() }
        setupResponsiveLandscapeLayout(rootView)

        //        Recalculate tray sizing after real layout changes; IME visibility comes from WindowInsets.
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(OnGlobalLayoutListener {
            val inputHeight = persistentBottomChromeHeight()
            if (inputHeight != lastPersistentInputHeight) {
                lastPersistentInputHeight = inputHeight
                if (unifiedStatusActive || imeInsetVisible || imeBottomOffset > 0) {
                    applyTerminalTrayState(false)
                }
            }
            updateKeyboardLayoutState(keyboardVisible, rootView.getHeight())
        })

        lockOnDbTap = XMLPrefsManager.getBoolean(Behavior.double_tap_lock)
        doubleTapCmd = XMLPrefsManager.get(Behavior.double_tap_cmd)
        swipeDownNotifications = XMLPrefsManager.getBoolean(Behavior.swipe_down_notifications)
        swipeUpAppsDrawer = XMLPrefsManager.getBoolean(Behavior.swipe_up_apps_drawer)

        if (!lockOnDbTap && doubleTapCmd == null && !swipeDownNotifications && !swipeUpAppsDrawer) {
            policy = null
            component = null
            gestureDetector = null
        } else {
            gestureDetector =
                GestureDetectorCompat(mContext!!, object : GestureDetector.OnGestureListener {
                    override fun onDown(e: MotionEvent): Boolean {
                        return true
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
                        if (gestureStartedOnOutput) return false
                        if (swipeUpAppsDrawer && velocityY < -100 && abs(velocityY) > abs(velocityX)) {
                            showAppsDrawer()
                            return true
                        }
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
                    // Tapping the ASCII art replays its effect.
                    if (touchStartedOnAscii) { playAsciiEffect(); return true }
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

        }
        rootView.setOnTouchListener(OnTouchListener { v: View?, event: MotionEvent? ->
            if (event != null) {
                onLauncherInteraction()
            }
            if (gestureDetector != null && event != null) {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) gestureStartedOnOutput = false
                val handled = gestureDetector!!.onTouchEvent(event)
                if (!handled && event.action == MotionEvent.ACTION_UP) {
                    v!!.performClick()
                }
                handled
            } else {
                false
            }
        })

        for (id in intArrayOf(R.id.termux_overlay, R.id.file_overlay, R.id.apps_drawer_root)) {
            rootView.findViewById<ViewGroup>(id)?.let { ohi.andre.consolelauncher.help.PanelBackdrop.installEmbedded(it) }
        }

        appDrawerPaneManager = AppDrawerPaneManager(
            mContext!!,
            rootView,
            { if (mTerminalAdapter != null) mTerminalAdapter!!.mainPack else mainPack },
            { if (!duoLayoutActive) closeKeyboard() },
            {
                setWallpaperTouchEnabled(false)
                if (!duoLayoutActive) hideLauncherChromeForSurface()
            },
            {
                restoreLauncherChromeAfterSurface()
                restoreWallpaperTouchAfterDrawerClose()
            }
        )
        if (mContext is Activity) {
            androidWidgetDrawerManager = AndroidWidgetDrawerManager(
                mContext as Activity,
                rootView,
                { if (!duoLayoutActive) closeKeyboard() },
                {
                    setWallpaperTouchEnabled(false)
                },
                {
                    restoreWallpaperTouchAfterDrawerClose()
                }
            )
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

        statusAppearance=ohi.andre.consolelauncher.presentation.HomeAppearance.saved()
        val show=ohi.andre.consolelauncher.presentation.StatusPresentation.descriptors.map { statusAppearance!!.bool(it.visible) }.toBooleanArray()

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

        val labelRows=ohi.andre.consolelauncher.presentation.StatusPresentation.rows(statusAppearance!!).map { row -> row.map { Label.entries[it] } }

        val lViewsParent = labelViews[0]!!.getParent() as LinearLayout
        val unifiedStatusBorder = AppearanceSettings.unifiedStatusBorder()
        ohi.andre.consolelauncher.presentation.HomeLayoutPresentation.styleStatusGroup(rootView,
            (statusAppearance ?: ohi.andre.consolelauncher.presentation.HomeAppearance.saved()))
        for (count in labelViews.indices) {
            labelViews[count]!!.setOnTouchListener(this)

            val os = labelRows.getOrNull(count).orEmpty()

            //            views on the same line
            for (j in os.indices) {
//                i is the object gave to the constructor
                val i = os[j].ordinal
                //                v is the adjusted index (2.0, 2.1, 2.2, ...)
                val v = count.toFloat() + (j.toFloat() * 0.1f)

                labelIndexes[i] = v
            }

            if (os.isNotEmpty()) {
                if (os.size == 1 && os[0] == Label.ascii) {
                    ensureAsciiViewportView(lViewsParent, count)
                }

                ohi.andre.consolelauncher.presentation.StatusPresentation.bindStyle(
                    labelViews[count]!!,os[0].ordinal,statusAppearance!!,unifiedStatusBorder)
                if (!os.contains(Label.notes)) labelViews[count]!!.isVerticalScrollBarEnabled=false

            } else {
                lViewsParent.removeView(labelViews[count])
                labelViews[count] = null
            }
        }

        if (unifiedStatusBorder) {
            labelViews.forEach { it?.visibility = View.GONE }
            setupUnifiedStatus()
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
            updateText(
                Label.device, Tuils.span(
                    mContext, ohi.andre.consolelauncher.presentation.StatusPresentation.deviceText(XMLPrefsManager::get), XMLPrefsManager.getColor(
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
            ohi.andre.consolelauncher.notes.NotesPaneLinks.bind(notesView)

            notesMaxLines = XMLPrefsManager.getInt(Ui.notes_max_lines)
            if (notesMaxLines > 0) {
                notesView.setMaxLines(notesMaxLines)
                notesView.setEllipsize(TextUtils.TruncateAt.MARQUEE)

                if (XMLPrefsManager.getBoolean(Ui.show_scroll_notes_message)) {
                    notesView.getViewTreeObserver()
                        .addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                            var linesBefore: Int = Int.Companion.MIN_VALUE

                            override fun onGlobalLayout() {
                                if (notesView.getLineCount() > notesMaxLines && linesBefore <= notesMaxLines) {
                                    Tuils.sendOutput(ThemeColorResolver.color(Theme.error_text_color), context, R.string.note_max_reached)
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
            ensureAsciiFile(asciiFile)
            asciiColor = XMLPrefsManager.getColor(Theme.ascii_text_color)

            val asciiView = getLabelView(Label.ascii)
            if (asciiView != null) {
                asciiView.setTypeface(Typeface.MONOSPACE)
            }
            applyAsciiDisplayLimits(asciiView)

            asciiAnimationManager = AsciiAnimationManager(
                XMLPrefsManager.getInt(Behavior.ascii_animation_frame_delay_ms).toLong(),
                asciiColor,
                statusUpdateListener
            )

            updateText(
                Label.ascii,
                asciiAnimationManager!!.load(
                    asciiFile,
                    XMLPrefsManager.getBoolean(Behavior.ascii_animation)
                )
            )
            asciiAnimationManager!!.start()
            scheduleAsciiIdlePause()
            playAsciiEffect()
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

    /** The hidden pixel grid: the whole home, CRT included, re-sampled onto whole cells in one pass. */
    fun applyPixelGrid() {
        val root = mRootView ?: return
        ohi.andre.consolelauncher.tuils.PixelGrid.apply(mContext, listOf(root), pixelInk())
        // 1-bit is its own screen filter: CRT scanlines would be thresholded into gaps through the text.
        val oneBit = ohi.andre.consolelauncher.tuils.PixelGrid.supported() && ohi.andre.consolelauncher.tuils.PixelGrid.enabled(mContext) &&
            ohi.andre.consolelauncher.tuils.PixelGrid.mode(mContext) == ohi.andre.consolelauncher.tuils.PixelGrid.Mode.ONE_BIT
        crtOverlayDrawable?.let { crt -> root.foreground = if (oneBit) null else crt }
    }

    private fun pixelInk() = ohi.andre.consolelauncher.wallpaper.pixel.PixelLook.themeInk()

    private fun installCrtOverlay(rootView: ViewGroup) {
        if (!AppearanceSettings.crtFilter()) {
            return
        }
        val overlay = CrtOverlayDrawable(rootView.getContext())
        overlay.setAccentColor(XMLPrefsManager.getColor(Theme.output_text_color))
        rootView.foreground = overlay
        crtOverlayDrawable = overlay
    }

    private fun styleTermuxConsole() {
        if (termuxOverlay == null) {
            return
        }

        val borderColor = ThemeColorResolver.color(Theme.workspace_panel_border_color)
        val textColor = ThemeColorResolver.color(Theme.workspace_panel_text_color)
        val bgColor = ThemeColorResolver.color(Theme.workspace_panel_background_color)
        val labelBg = ThemeColorResolver.color(Theme.workspace_header_background_color)

        if (termuxWindowBorder != null) {
            termuxWindowBorder!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    bgColor,
                    borderColor,
                    1.5f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
                )
            )
        }

        if (termuxWindowLabel != null) {
            termuxWindowLabel!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxWindowLabel!!.setTextSize(outputHeaderTextSize().toFloat())
            termuxWindowLabel!!.setTextColor(textColor)
            termuxWindowLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg, FrameTarget.OVERLAYS))
        }

        if (termuxClose != null) {
            termuxClose!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxClose!!.setTextSize(outputHeaderTextSize().toFloat())
            termuxClose!!.setTextColor(textColor)
            termuxClose!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg, FrameTarget.OVERLAYS))
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
            grid.setTerminalTextSizeSp(LauncherFontScale.terminalTextSizeSp())
            grid.updateThemeColors(textColor, bgColor, borderColor)
        }

        if (termuxOutputPanel != null) {
            termuxOutputPanel!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    ThemeColorResolver.color(Theme.workspace_output_background_color),
                    ThemeColorResolver.withMaxAlpha(borderColor, 210),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
                )
            )
        }

        if (termuxOutputLabel != null) {
            termuxOutputLabel!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxOutputLabel!!.setTextSize(max(10f, outputHeaderTextSize().toFloat() - 2f))
            termuxOutputLabel!!.setTextColor(textColor)
            termuxOutputLabel!!.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg, FrameTarget.OVERLAYS))
        }
        TerminalBorderRuntime.bind(termuxOutputPanel, termuxOutputLabel)

        if (termuxPrefix != null) {
            termuxPrefix!!.setTypeface(Tuils.getTypeface(mContext), Typeface.BOLD)
            termuxPrefix!!.setTextColor(textColor)
        }

        if (termuxInput != null) {
            termuxInput!!.setTypeface(Tuils.getTypeface(mContext))
            termuxInput!!.setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
            termuxInput!!.setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
            Tuils.setCursorDrawableColor(termuxInput!!, ThemeColorResolver.color(Theme.cursor_color))
        }

        if (termuxInputGroup != null) {
            termuxInputGroup!!.setBackground(
                TerminalBorderRuntime.panelDrawable(
                    mContext!!,
                    ThemeColorResolver.color(Theme.workspace_input_background_color),
                    ThemeColorResolver.withMaxAlpha(borderColor, 180),
                    1.2f,
                    outputCornerRadius(),
                    dashedBorders(),
                    target = FrameTarget.OVERLAYS
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
                if (app == null) mContext.getString(R.string.surface_termux_26316) else app.title.uppercase(Locale.getDefault())
        }
        if (termuxOutputLabel != null) {
            termuxOutputLabel!!.text = if (luaId != null) mContext.getString(R.string.surface_app_aa160) else if (app == null) mContext.getString(R.string.surface_output_feb35) else mContext.getString(R.string.surface_session_c1085)
        }
        if (termuxPrefix != null) {
            termuxPrefix!!.text = if (app == null && luaId == null) "\$" else ">"
        }
        if (termuxInput != null) {
            termuxInput!!.hint = if (app == null && luaId == null) "command" else mContext.getString(R.string.surface_type_input_or_help_4d935)
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
        val textColor = ThemeColorResolver.color(Theme.workspace_control_text_color)
        val labelBg = terminalHeaderTabBackground()
        val margin = Tuils.dpToPx(mContext, 4)
        val minWidth = Tuils.dpToPx(mContext, 76)
        val button = TextView(mContext!!)
        button.text = (if (TextUtils.isEmpty(label)) mContext.getString(R.string.surface_action_2b9f1) else label!!).uppercase(Locale.getDefault())
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
        button.setBackground(TerminalBorderRuntime.tabDrawable(mContext!!, labelBg, FrameTarget.OVERLAYS))
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

    private fun podcastLandscapePresentation(): Boolean =
        shouldUseResponsiveLandscape(currentConfiguration)

    fun openTermuxConsole(command: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openTermuxConsole(command) }
            return
        }

        if (termuxOverlay == null) {
            return
        }

        fileConsoleController?.closeFileConsole(false)
        profilePaneController?.hide()
        closeLuaAppSession(true)
        var normalized = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        val restoringApp = normalized.isEmpty() && restoreRetainedTermuxApp()
        if (!restoringApp) {
            termuxAppSession = null
            retainedTermuxAppId = null
            termuxAppLastStatus = null
            resetTermuxAppRuntimeState(true)
            resetTermuxAppCellViewport()
        }
        styleTermuxConsole()
        termuxOverlay!!.setVisibility(View.VISIBLE)
        termuxOverlay!!.bringToFront()
        hideHomeSuggestionsForTermux()

        if (restoringApp) {
            renderTermuxAppFrame(
                termuxBuffer.toString().takeIf { it.isNotBlank() },
                "reattaching"
            )
            refreshTermuxAppSession(false)
            scheduleTermuxAppRefreshBurst(termuxAppSession?.id, TERMUX_APP_MANUAL_REFRESH_WATCH_MS)
        } else if (termuxBuffer.length == 0) {
            appendTermuxLine(mContext.getString(R.string.surface_re_t_ui_termux_console_341d4))
            appendTermuxLine(mContext.getString(R.string.surface_type_shell_commands_help_status_open_run_clear_or_exit_a8168))
            appendTermuxLine(mContext.getString(R.string.surface_non_interactive_termux_commands_run_from_here_80d90))
        } else {
            updateTermuxOutput()
        }

        if (normalized.length > 0) {
            if (normalized.startsWith("-")) {
                normalized = normalized.substring(1)
            }
            executeTermuxConsoleCommand(normalized)
        }

        restoreTermuxScrollIfPending()
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
            Toast.makeText(mContext, mContext.getString(R.string.surface_unknown_lua_app_e937e, id), Toast.LENGTH_SHORT).show()
            return
        }
        if ("app" != LuaWidgetManager.getScriptType(id)) {
            Toast.makeText(mContext, mContext.getString(R.string.surface_script_is_not_a_lua_app_62b97, id), Toast.LENGTH_SHORT).show()
            return
        }

        fileConsoleController?.closeFileConsole(false)
        profilePaneController?.hide()
        closeLuaAppSession(true)
        termuxAppSession = null
        retainedTermuxAppId = null
        retainedTermuxAppFnKeyMode = false
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
        renderLuaAppFrame(null, mContext.getString(R.string.surface_opening_9d1be, luaAppTitle()))

        val result = luaAppEngine!!.open()
        renderLuaAppResult(result, "open")
        scheduleTermuxConsoleFocusCapture(true)
    }

    fun openFileConsole(command: String?) {
        fileConsoleController?.openFileConsole(command)
    }

    fun openProfileSurface() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnMainThread { openProfileSurface() }
            return
        }
        closeTermuxConsole(false)
        fileConsoleController?.closeFileConsole(false)
        closeLuaAppSession(true)
        profilePaneController?.show()
        hideHomeSuggestionsForTermux()
    }

    private fun closeProfileSurface(restoreSuggestions: Boolean = true) {
        profilePaneController?.hide()
        if (restoreSuggestions) {
            val input = mTerminalAdapter?.input?.trim { it <= ' ' }.orEmpty()
            if (input.equals("profile", ignoreCase = true)) mTerminalAdapter?.input = Tuils.EMPTYSTRING
            restoreHomeSuggestionsAfterTermux()
            mTerminalAdapter?.focusInputEnd()
        }
    }

    private fun restoreLauncherSurfaceSession() {
        val session = LauncherSurfaceSessionStore.snapshot()
        val termux = session.termux
        termuxBuffer.setLength(0)
        termuxBuffer.append(termux.consoleBuffer)
        retainedTermuxInputDraft = termux.inputDraft
        retainedTermuxAppId = termux.appId
        retainedTermuxAppFnKeyMode = termux.appFnKeyMode
        pendingTermuxScrollRestore = if (
            termux.consoleBuffer.isNotEmpty() || termux.inputDraft.isNotEmpty() || termux.appId != null
        ) termux.scrollY.coerceAtLeast(0) else -1
    }

    private fun captureLauncherSurfaceSession() {
        captureTermuxSurfaceSession(termuxAppSession?.id ?: retainedTermuxAppId)
    }

    private fun captureTermuxSurfaceSession(appId: String?) {
        val inputDraft = termuxInput?.text?.toString() ?: retainedTermuxInputDraft
        val scrollY = if (isTermuxConsoleVisible) {
            termuxScroll?.scrollY ?: pendingTermuxScrollRestore.coerceAtLeast(0)
        } else {
            pendingTermuxScrollRestore.coerceAtLeast(0)
        }
        retainedTermuxInputDraft = inputDraft
        retainedTermuxAppId = appId
        retainedTermuxAppFnKeyMode = if (appId == null) false else termuxFnKeyMode
        pendingTermuxScrollRestore = scrollY.coerceAtLeast(0)
        LauncherSurfaceSessionStore.saveTermux(
            TermuxSurfaceSession(
                termuxBuffer.toString(),
                inputDraft,
                pendingTermuxScrollRestore,
                appId,
                retainedTermuxAppFnKeyMode,
                false,
                false,
                Tuils.EMPTYSTRING
            )
        )
    }

    private fun restoreRetainedTermuxApp(): Boolean {
        val id = termuxAppSession?.id ?: retainedTermuxAppId ?: return false
        val app = termuxAppSession ?: TermuxAppManager.resolve(mContext!!, id)
        if (app == null) {
            retainedTermuxAppId = null
            retainedTermuxAppFnKeyMode = false
            return false
        }
        termuxAppSession = app
        retainedTermuxAppId = app.id
        termuxAppLastStatus = null
        resetTermuxAppRuntimeState(true)
        termuxFnKeyMode = retainedTermuxAppFnKeyMode
        resetTermuxAppCellViewport()
        updateTermuxConsoleLabels()
        return true
    }

    private fun restoreTermuxScrollIfPending() {
        val scrollY = pendingTermuxScrollRestore
        if (scrollY < 0) return
        pendingTermuxScrollRestore = -1
        termuxScroll?.post(Runnable { termuxScroll?.scrollTo(0, scrollY) })
    }

    private fun closeTermuxConsole(restoreSuggestions: Boolean = true) {
        termuxFocusCapturePending = false
        captureTermuxSurfaceSession(
            if (restoreSuggestions) null else (termuxAppSession?.id ?: retainedTermuxAppId)
        )
        if (termuxOverlay != null) {
            termuxOverlay!!.setVisibility(View.GONE)
        }
        closeLuaAppSession(true)
        if (restoreSuggestions) {
            termuxAppSession = null
            retainedTermuxAppId = null
            termuxAppLastStatus = null
            resetTermuxAppRuntimeState(true)
            resetTermuxAppCellViewport()
        }
        updateTermuxConsoleLabels()
        if (restoreSuggestions) {
            restoreHomeSuggestionsAfterTermux()
        }
        if (termuxInput != null) {
            val manager =
                mContext!!.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            if (manager != null) {
                manager.hideSoftInputFromWindow(termuxInput!!.getWindowToken(), 0)
            }
            termuxInput!!.clearFocus()
        }
        if (mTerminalAdapter != null && restoreSuggestions) {
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
        return handleTermuxBackPressed()
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
            termuxAppLastStatus = mContext.getString(R.string.surface_sent_interrupt_e9bc2)
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("interrupt", TermuxAppScripts.buildTermuxAppControlScript(app, "C-c", ::calculateTermuxAppGeometry), true)
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
        refreshSuggestionsSoon()
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
        luaAppLastStatus = if (command.length == 0) mContext.getString(R.string.surface_sent_enter_df691) else mContext.getString(R.string.surface_input_53073, command)
        renderLuaAppResult(engine.input(command), luaAppLastStatus)
    }

    private fun handleLuaAppLocalCommand(command: String) {
        val id = luaAppId ?: return
        if ("help" == command || command.length == 0) {
            renderLuaAppFrame(
                luaAppLastResult,
                mContext.getString(R.string.surface_help_refresh_restart_config_edit_clear_close_other_input_i_e0b4c)
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
                renderLuaAppFrame(luaAppLastResult, mContext.getString(R.string.surface_no_config_surface_92c6f, luaAppTitle()))
            }
        } else if ("edit" == command) {
            executeLuaWidgetCommand("lua -edit " + id)
        } else if ("clear" == command) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else if ("close" == command || "exit" == command || "detach" == command) {
            closeTermuxConsole()
        } else {
            renderLuaAppFrame(luaAppLastResult, mContext.getString(R.string.surface_unknown_app_command_44753, command))
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
        out.append(mContext.getString(R.string.surface_re_t_ui_lua_app_2c557)).append(luaAppTitle()).append('\n')
        out.append(mContext.getString(R.string.surface_script_39362)).append(id).append('\n')
        out.append(mContext.getString(R.string.surface_local_commands_help_refresh_restart_config_edit_clear_clos_0f9a2)).append('\n')
        if (!TextUtils.isEmpty(status)) {
            out.append(mContext.getString(R.string.surface_status_8a876)).append(status).append('\n')
        }
        out.append("----")
        if (result == null) {
            out.append(mContext.getString(R.string.surface_loading_11b54))
        } else if (!TextUtils.isEmpty(result.error)) {
            out.append(mContext.getString(R.string.surface_lua_error_24e66)).append(result.error)
            if (!TextUtils.isEmpty(result.errorStage)) {
                out.append(mContext.getString(R.string.surface_stage_805fd)).append(result.errorStage)
            }
        } else if (!TextUtils.isEmpty(result.body)) {
            out.append('\n').append(result.body!!.trimEnd { it <= ' ' })
        } else if (!TextUtils.isEmpty(result.layoutJson)) {
            out.append(mContext.getString(R.string.surface_layout_output_is_available_in_module_panels_use_ui_show_te_5ac8c))
        } else {
            out.append(mContext.getString(R.string.surface_no_lua_app_output_yet_334b2))
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

        luaModules.layout.addLuaText(container, mContext.getString(R.string.surface_re_t_ui_lua_app_dfb07, luaAppTitle()), id, MODULE_TEXT_FONT_MONO)
        luaModules.layout.addLuaText(container, mContext.getString(R.string.surface_script_4bf42, id), id, MODULE_TEXT_FONT_MONO)
        luaModules.layout.addLuaText(
            container,
            mContext.getString(R.string.surface_local_commands_help_refresh_restart_config_edit_clear_clos_0f9a2),
            id,
            MODULE_TEXT_FONT_MONO
        )
        if (!TextUtils.isEmpty(status)) {
            luaModules.layout.addLuaText(container, mContext.getString(R.string.surface_status_573dd, status), id, MODULE_TEXT_FONT_MONO)
        }
        luaModules.layout.addLuaText(container, "----------------", id, MODULE_TEXT_FONT_MONO)

        luaModules.layout.renderLuaLayout(container, id, result.layoutJson, true)
        if (!TextUtils.isEmpty(result.body)) {
            val spacer = View(mContext)
            spacer.setLayoutParams(
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Tuils.dpToPx(mContext, 8)
                )
            )
            container.addView(spacer)
            luaModules.layout.addLuaBodyText(container, result.body, id)
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
        return if (TextUtils.isEmpty(id)) mContext.getString(R.string.surface_lua_app_34dd8) else LuaWidgetManager.getName(id) ?: mContext.getString(R.string.surface_lua_app_34dd8)
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
        renderLuaAppResult(engine.click(index), mContext.getString(R.string.surface_button_a4234, index))
        restoreLuaAppInputFocusSoon()
    }

    private fun actionLuaApp(value: String?) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.action(value), if (TextUtils.isEmpty(value)) "action" else mContext.getString(R.string.surface_action_a7fac, value))
        restoreLuaAppInputFocusSoon()
    }

    private fun dialogLuaApp(index: Int) {
        val engine = luaAppEngine ?: return
        renderLuaAppResult(engine.dialog(index), if (index < 0) mContext.getString(R.string.surface_dialog_canceled_9e0e5) else mContext.getString(R.string.surface_dialog_addd4, index))
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
        engine?.dispose()
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
            appendTermuxLine(mContext.getString(R.string.surface_termux_console_is_already_open_type_help_for_available_com_e6bd1))
            return
        }

        val lower = command.lowercase()
        if ("exit" == lower || "close" == lower) {
            appendTermuxLine(mContext.getString(R.string.surface_closing_termux_console_4825d))
            closeTermuxConsole()
        } else if ("clear" == lower) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else if ("help" == lower) {
            appendTermuxLine("help")
            appendTermuxLine(mContext.getString(R.string.surface_pwd_ls_whoami_run_shell_commands_in_termux_a6ede))
            appendTermuxLine(mContext.getString(R.string.surface_cd_dir_change_the_termux_console_working_directory_df2dc))
            appendTermuxLine(mContext.getString(R.string.surface_status_check_termux_bridge_readiness_e5c11))
            appendTermuxLine(mContext.getString(R.string.surface_setup_show_termux_bridge_setup_checklist_eeb78))
            appendTermuxLine(mContext.getString(R.string.surface_open_launch_the_termux_android_app_1789b))
            appendTermuxLine(mContext.getString(R.string.surface_run_script_alias_args_dispatch_a_termux_script_fe771))
            appendTermuxLine(mContext.getString(R.string.surface_apps_app_id_tmux_workspace_launchers_interactive_tuis_77e6a))
            appendTermuxLine(mContext.getString(R.string.surface_app_add_id_command_save_a_workspace_launcher_72135))
            appendTermuxLine(mContext.getString(R.string.surface_prefer_tmux_launch_mc_or_alias_mc_577ec))
            appendTermuxLine(mContext.getString(R.string.surface_clear_clear_this_console_9610a))
            appendTermuxLine(mContext.getString(R.string.surface_exit_close_this_console_b8e51))
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
        val launchers = TermuxWorkspaceLauncherManager.list(mContext!!)
        appendTermuxLine(mContext.getString(R.string.surface_workspace_launchers_use_tmux_launch_id_or_mc_7b114))
        for (launcher in launchers) {
            val cmd = if (launcher.command.isEmpty()) "(shell)" else launcher.command
            appendTermuxLine(launcher.id + " -> " + launcher.title + " [" + cmd + "]")
        }
    }

    private fun appendTermuxAppActions(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_actions_id_fcfd2))
            return
        }
        val app = TermuxAppManager.resolve(mContext!!, parts.get(1))
        if (app == null) {
            appendTermuxLine(mContext.getString(R.string.surface_unknown_termux_app_5f89c, parts.get(1)))
            return
        }
        appendTermuxLine(mContext.getString(R.string.surface_actions_for_cf6f1, app.id))
        if (app.actions.isEmpty()) {
            appendTermuxLine(mContext.getString(R.string.surface_no_actions_registered_112a4))
        } else {
            for (action in app.actions) {
                val sendLabel = if (action.send.isEmpty()) "[enter]" else action.send
                appendTermuxLine(action.label + " -> " + sendLabel)
            }
        }
        appendTermuxLine(mContext.getString(R.string.surface_add_with_app_action_label_input_bcb50, app.id))
    }

    private fun appendTermuxAppInfo(command: String?) {
        val app = resolveTermuxAppFromCommand(command, mContext.getString(R.string.surface_app_info_id_1ee02)) ?: return
        appendTermuxLine(mContext.getString(R.string.surface_termux_app_d1455, app.id))
        appendTermuxLine(mContext.getString(R.string.surface_title_e024d, app.title))
        appendTermuxLine(mContext.getString(R.string.surface_command_d42c7, app.command))
        appendTermuxLine(mContext.getString(R.string.surface_workdir_a61e2, app.workDir))
        appendTermuxLine(mContext.getString(R.string.surface_home_fb1d7, app.homeDir))
        appendTermuxLine(mContext.getString(R.string.surface_manifest_558ac, app.manifestPath))
        appendTermuxLine(mContext.getString(R.string.surface_state_88c10, app.statePath))
        appendTermuxLine(mContext.getString(R.string.surface_memory_13c03, app.memoryDir))
        appendTermuxLine(mContext.getString(R.string.surface_logs_bee4e, app.logsDir))
        appendTermuxLine(mContext.getString(R.string.surface_session_66981, TermuxAppManager.tmuxSessionName(app.id)))
        appendTermuxLine(mContext.getString(R.string.surface_actions_6dfd8, app.actions.size))
    }

    private fun syncTermuxCustomApp(command: String?) {
        val app = resolveTermuxAppFromCommand(command, mContext.getString(R.string.surface_app_sync_id_073c6)) ?: return
        if (syncTermuxAppManifest(app, true)) {
            appendTermuxLine(mContext.getString(R.string.surface_manifest_sync_dispatched_3453b, app.manifestPath))
        }
    }

    private fun resolveTermuxAppFromCommand(command: String?, usage: String): TermuxAppManager.TermuxApp? {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_84af2, usage))
            return null
        }
        val app = TermuxAppManager.resolve(mContext!!, parts.get(1))
        if (app == null) {
            appendTermuxLine(mContext.getString(R.string.surface_unknown_termux_app_5f89c, parts.get(1)))
            return null
        }
        return app
    }

    private fun openTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_id_opens_tmux_workspace_launcher_018ce))
            appendTermuxApps()
            return
        }
        handleTermuxWorkspaceExternalCommand("launch " + parts[1])
    }

    private fun addTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_add_id_command_92855))
            appendTermuxLine(mContext.getString(R.string.surface_example_app_add_radio_bash_retui_radio_sh_2f655))
            appendTermuxLine(mContext.getString(R.string.surface_saved_as_a_tmux_workspace_launcher_tmux_launch_id_e5513))
            return
        }
        val id = parts[1]
        val appCommand = Tuils.toPlanString(parts.subList(2, parts.size), Tuils.SPACE)
        if (TermuxWorkspaceLauncherManager.save(mContext!!, id, appCommand)) {
            val normalized = TermuxWorkspaceLauncherManager.normalizeId(id)
            appendTermuxLine(mContext.getString(R.string.surface_workspace_launcher_saved_71ec1, normalized))
            appendTermuxLine(mContext.getString(R.string.surface_open_with_tmux_launch_or_3a10f, normalized, normalized))
        } else {
            appendTermuxLine(mContext.getString(R.string.surface_unable_to_save_launcher_built_in_ids_cannot_be_overwritten_97283))
        }
    }

    private fun addTermuxAppAction(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_action_id_label_input_bf286))
            appendTermuxLine(mContext.getString(R.string.surface_example_app_action_myapp_show_status_6_37779))
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
            appendTermuxLine(mContext.getString(R.string.surface_app_action_registered_58cf0, TermuxAppManager.normalizeId(id), label))
        } else {
            appendTermuxLine(mContext.getString(R.string.surface_unable_to_register_app_action_ebff7))
        }
    }

    private fun removeTermuxAppAction(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 3) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_action_rm_id_label_5e8ec))
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
            appendTermuxLine(mContext.getString(R.string.surface_app_action_removed_13264, TermuxAppManager.normalizeId(id), label))
        } else {
            appendTermuxLine(mContext.getString(R.string.surface_app_action_not_removed_c8f3f, label))
        }
    }

    private fun removeTermuxCustomApp(command: String?) {
        val parts = Tuils.splitArgs(command)
        if (parts.size < 2) {
            appendTermuxLine(mContext.getString(R.string.surface_usage_app_rm_id_38cb8))
            appendTermuxLine(mContext.getString(R.string.surface_alias_rm_app_id_3df4f))
            return
        }
        val id = TermuxWorkspaceLauncherManager.normalizeId(parts.get(1))
        if (TermuxWorkspaceLauncherManager.remove(mContext!!, id)) {
            appendTermuxLine(mContext.getString(R.string.surface_termux_app_removed_4f6c4, id))
        } else {
            appendTermuxLine(mContext.getString(R.string.surface_termux_app_not_removed_bc850, id))
        }
    }

    private fun submitTermuxAppInput(rawCommand: String?) {
        val app = termuxAppSession ?: return
        val command = if (rawCommand == null) Tuils.EMPTYSTRING else rawCommand.trim { it <= ' ' }
        if (command.startsWith(":")) {
            handleTermuxAppLocalCommand(app, command.substring(1).trim { it <= ' ' }.lowercase(Locale.getDefault()))
            return
        }
        termuxAppLastStatus = if (command.length == 0) mContext.getString(R.string.surface_sent_enter_df691) else mContext.getString(R.string.surface_sent_faa06, command)
        renderTermuxAppFrame(null, termuxAppLastStatus)
        dispatchTermuxAppScript("send", TermuxAppScripts.buildTermuxAppSendScript(app, command, ::calculateTermuxAppGeometry), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
    }

    private fun submitTermuxAppAction(action: TermuxAppManager.TermuxAppAction) {
        val app = termuxAppSession ?: return
        termuxAppLastStatus = mContext.getString(R.string.surface_action_a7fac, action.label)
        renderTermuxAppFrame(null, termuxAppLastStatus)
        dispatchTermuxAppScript("action", TermuxAppScripts.buildTermuxAppSendScript(app, action.send, ::calculateTermuxAppGeometry), true)
        scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_INPUT_WATCH_MS)
        scheduleTermuxConsoleFocusCapture(true)
    }

    private fun handleTermuxAppLocalCommand(app: TermuxAppManager.TermuxApp, command: String) {
        if ("help" == command || command.length == 0) {
            renderTermuxAppFrame(
                null,
                mContext.getString(R.string.surface_help_refresh_restart_stop_detach_open_clear_other_input_is_a1d16)
            )
        } else if ("refresh" == command || "r" == command) {
            refreshTermuxAppSession(true)
            scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_MANUAL_REFRESH_WATCH_MS)
        } else if ("restart" == command) {
            termuxAppLastStatus = "restarting"
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("restart", TermuxAppScripts.buildTermuxAppKillScript(app) + "\n" + TermuxAppScripts.buildTermuxAppStartScript(app, ::calculateTermuxAppGeometry), true)
            scheduleTermuxAppRefreshBurst(app.id, TERMUX_APP_START_WATCH_MS)
        } else if ("stop" == command || "kill" == command) {
            termuxAppRefreshGeneration++
            termuxAppLastStatus = "stopping"
            renderTermuxAppFrame(null, termuxAppLastStatus)
            dispatchTermuxAppScript("stop", TermuxAppScripts.buildTermuxAppKillScript(app), true)
        } else if ("detach" == command || "exit" == command || "close" == command) {
            closeTermuxConsole()
        } else if ("open" == command) {
            openTermuxApp()
        } else if ("clear" == command) {
            termuxBuffer.setLength(0)
            updateTermuxOutput()
        } else {
            renderTermuxAppFrame(null, mContext.getString(R.string.surface_unknown_app_command_44753, command))
        }
    }

    private fun refreshTermuxAppSession(announce: Boolean) {
        val app = termuxAppSession ?: return
        if (announce) {
            termuxAppLastStatus = "refreshing"
            renderTermuxAppFrame(null, termuxAppLastStatus)
        }
        dispatchTermuxAppScript("capture", TermuxAppScripts.buildTermuxAppCaptureScript(app, ::calculateTermuxAppGeometry), false)
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
                renderTermuxAppFrame(null, mContext.getString(R.string.surface_dispatch_uimanager_termux_bridge_is_not_ready_86516))
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
            renderTermuxAppFrame(null, mContext.getString(R.string.surface_dispatch_uimanager_termux_rejected_the_app_command_permission_24c17))
        } catch (e: Exception) {
            renderTermuxAppFrame(null, mContext.getString(R.string.surface_dispatch_uimanager_unable_to_dispatch_app_command_44ccd, e.javaClass.getSimpleName()))
        }
        return false
    }

    private fun syncTermuxAppManifest(app: TermuxAppManager.TermuxApp, echoToConsole: Boolean): Boolean {
        val dispatched = dispatchTermuxAppSideEffectScript(
            app,
            TermuxAppScripts.buildTermuxAppPrepareScript(app, echoToConsole),
            if (echoToConsole) TERMUX_APP_SYNC_RESULT_PREFIX + app.id else null
        )
        if (!dispatched && echoToConsole) {
            appendTermuxLine(mContext.getString(R.string.surface_manifest_sync_pending_termux_bridge_is_not_ready_1d5d0))
        }
        return dispatched
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
            appendTermuxLine(mContext.getString(R.string.surface_manifest_synced_8a447, id))
            if (!TextUtils.isEmpty(stdout)) {
                appendTermuxLine(stdout!!.trim { it <= ' ' })
            }
            return
        }

        appendTermuxLine(mContext.getString(R.string.surface_manifest_sync_failed_fa58b, id))
        if (exitCode != Int.Companion.MIN_VALUE) {
            appendTermuxLine(mContext.getString(R.string.surface_exit_da8cd, exitCode))
        }
        if (!TextUtils.isEmpty(stderr)) {
            appendTermuxLine(mContext.getString(R.string.surface_stderr_9ffd5, stderr!!.trim { it <= ' ' }))
        }
        if (!TextUtils.isEmpty(error)) {
            appendTermuxLine(mContext.getString(R.string.surface_error_701e5, error!!.trim { it <= ' ' }))
        }
        if (!TextUtils.isEmpty(debug)) {
            appendTermuxLine(mContext.getString(R.string.surface_debug_cf37f, debug!!.trim { it <= ' ' }))
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
            val alreadyEnded = mContext.getString(R.string.surface_session_ended_e36c4) == termuxAppLastStatus
            termuxAppLastStatus = mContext.getString(R.string.surface_session_ended_e36c4)
            if (!(alreadyEnded && "capture" == action)) {
                renderTermuxAppFrame(mContext.getString(R.string.surface_session_ended_type_restart_to_start_it_1bc47), termuxAppLastStatus)
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
            status = mContext.getString(R.string.surface_error_701e5, error!!.trim { it <= ' ' })
            frame = null
        } else if (!TextUtils.isEmpty(debug)) {
            status = mContext.getString(R.string.surface_debug_cf37f, debug!!.trim { it <= ' ' })
            frame = null
        } else if (exitCode != Int.Companion.MIN_VALUE && exitCode != 0) {
            status = mContext.getString(R.string.surface_exit_da8cd, exitCode)
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
            (mContext.getString(R.string.surface_re_t_ui_app_session_local_commands_help_refresh_restart_st_d4cdf, app.title, TermuxAppManager.tmuxSessionName(app.id), if (TextUtils.isEmpty(status)) mContext.getString(R.string.surface_starting_d6fc5) else mContext.getString(R.string.surface_status_cb861, status)))
        }
        termuxBuffer.setLength(0)
        termuxBuffer.append(stripTermuxAnsi(raw)?.trimEnd { it <= ' ' } ?: Tuils.EMPTYSTRING)
        if (hasFrame && termuxGrid != null) {
            val geometry = calculateTermuxAppGeometry()
            termuxGrid?.setFrame(raw, geometry.cols, geometry.rows, null, null)
        } else {
            val rendered = if (hasFrame) TermuxAnsiRenderer.renderTermuxWorkspaceAnsi(raw) else SpannableStringBuilder(raw)
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
        appendTermuxLine(mContext.getString(R.string.surface_termux_bridge_setup_e45bb))
        appendTermuxLine(mContext.getString(R.string.surface_1_install_current_termux_from_f_droid_github_7baec))
        appendTermuxLine(mContext.getString(R.string.surface_2_in_termux_enable_external_app_commands_2fff2))
        appendTermuxLine(mContext.getString(R.string.surface_mkdir_p_termux_3670c))
        appendTermuxLine(mContext.getString(R.string.surface_echo_allow_external_apps_true_termux_termux_properties_8d906))
        appendTermuxLine(mContext.getString(R.string.surface_termux_reload_settings_f5a10))
        appendTermuxLine(mContext.getString(R.string.surface_3_put_scripts_in_a_stable_folder_for_example_b5179))
        appendTermuxLine(mContext.getString(R.string.surface_mkdir_p_retui_bd4b2))
        appendTermuxLine(mContext.getString(R.string.surface_nano_retui_test_sh_ff3a9))
        appendTermuxLine(mContext.getString(R.string.surface_chmod_x_retui_test_sh_21c3d))
        appendTermuxLine(mContext.getString(R.string.surface_4_create_a_re_t_ui_script_alias_5761e))
        appendTermuxLine(mContext.getString(R.string.surface_alias_add_s_test_data_data_com_termux_files_home_retui_tes_f498e))
        appendTermuxLine(mContext.getString(R.string.surface_5_run_it_from_re_t_ui_67a47))
        appendTermuxLine(mContext.getString(R.string.surface_termux_run_test_b9c68))
        appendTermuxLine(mContext.getString(R.string.surface_6_for_callback_modules_package_scope_the_broadcast_ff541))
        appendTermuxLine(mContext.getString(R.string.surface_am_broadcast_p_com_dvil_tui_renewed_a_com_dvil_tui_renewed_c1701))
        appendTermuxLine(mContext.getString(R.string.surface_7_optional_helper_85156))
        appendTermuxLine(mContext.getString(R.string.surface_retui_token_show_2e437))
        appendTermuxLine(mContext.getString(R.string.surface_create_retui_retui_helper_sh_with_retui_module_retui_outpu_1a75a))
        appendTermuxLine(mContext.getString(R.string.surface_8_script_backed_module_f9159))
        appendTermuxLine(mContext.getString(R.string.surface_module_add_server_termux_data_data_com_termux_files_home_r_226db))
        appendTermuxLine(mContext.getString(R.string.surface_module_refresh_server_e8d63))
        appendTermuxLine(mContext.getString(R.string.surface_if_android_asks_for_run_command_permission_allow_re_t_ui_a_ec158))
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

        appendTermuxLine(mContext.getString(R.string.surface_termux_installed_d7938, status.termuxInstalled))
        appendTermuxLine(mContext.getString(R.string.surface_runcommand_bridge_a8081, (if (status.runCommandDeclared) "available" else mContext.getString(R.string.surface_not_available_5f239))))
        appendTermuxLine(mContext.getString(R.string.surface_runcommand_permission_b2628, (if (status.runCommandGranted) "granted" else mContext.getString(R.string.surface_not_granted_1604a))))
        appendTermuxLine(mContext.getString(R.string.surface_console_cwd_be998, termuxWorkingDirectory))
        appendTermuxLine(mContext.getString(R.string.surface_required_termux_setting_allow_external_apps_true_da61f))
        if (!status.termuxInstalled) {
            appendTermuxLine(mContext.getString(R.string.surface_install_termux_before_enabling_script_dispatch_89dce))
        } else if (!status.runCommandDeclared) {
            appendTermuxLine(mContext.getString(R.string.surface_this_termux_build_does_not_expose_run_command_b2ba8))
            appendTermuxLine(mContext.getString(R.string.surface_install_the_current_termux_build_from_f_droid_github_not_t_ee9d3))
        } else if (!status.runCommandGranted) {
            appendTermuxLine(mContext.getString(R.string.surface_grant_re_t_ui_permission_to_run_commands_in_termux_when_pr_a9d96))
        } else {
            appendTermuxLine(mContext.getString(R.string.surface_bridge_prerequisites_look_ready_for_the_next_phase_66afa))
        }
    }

    private fun runTermuxShellCommand(command: String?) {
        val trimmed = if (command == null) Tuils.EMPTYSTRING else command.trim { it <= ' ' }
        if (trimmed.length == 0) {
            return
        }
        if (isInteractiveTermuxCommand(trimmed)) {
            appendTermuxLine(mContext.getString(R.string.surface_interactive_command_191ac, trimmed))
            appendTermuxLine(mContext.getString(R.string.surface_opening_termux_for_a_live_terminal_session_9bb39))
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
                appendTermuxLine(mContext.getString(R.string.surface_shell_d4208, shellCommand))
            }
            return true
        } catch (e: SecurityException) {
            reportTermuxDispatch(mContext.getString(R.string.surface_termux_rejected_the_command_permission_denied_5c116), true)
            reportTermuxDispatch(
                mContext.getString(R.string.surface_check_allow_external_apps_true_and_grant_run_command_permi_58131),
                true
            )
        } catch (e: Exception) {
            reportTermuxDispatch(
                mContext.getString(R.string.surface_unable_to_dispatch_termux_command_45f68, e.javaClass.getSimpleName()),
                true
            )
            reportTermuxDispatch(mContext.getString(R.string.surface_open_termux_once_then_retry_from_this_console_7349c), true)
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
            appendTermuxLine(mContext.getString(R.string.surface_usage_run_script_path_args_ed67b))
            appendTermuxLine(mContext.getString(R.string.surface_example_run_data_data_com_termux_files_home_retui_myscript_5e8ca))
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
                appendTermuxLine(mContext.getString(R.string.surface_dispatch_uimanager_dispatched_to_termux_f399f, path))
                if (aliasName != null && aliasName != path) {
                    appendTermuxLine(mContext.getString(R.string.surface_dispatch_uimanager_alias_25d3c, aliasName, path))
                }
                if (!args.isEmpty()) {
                    appendTermuxLine(mContext.getString(R.string.surface_dispatch_uimanager_args_42285, Tuils.toPlanString(args, Tuils.SPACE)))
                }
            }
            return true
        } catch (e: SecurityException) {
            reportTermuxDispatch(mContext.getString(R.string.surface_dispatch_uimanager_termux_rejected_the_command_permission_den_5c116), echoToConsole)
            reportTermuxDispatch(
                mContext.getString(R.string.surface_dispatch_uimanager_check_allow_external_apps_true_and_grant_r_58131),
                echoToConsole
            )
        } catch (e: Exception) {
            reportTermuxDispatch(
                mContext.getString(R.string.surface_dispatch_uimanager_unable_to_dispatch_termux_command_45f68, e.javaClass.getSimpleName()),
                echoToConsole
            )
            reportTermuxDispatch(mContext.getString(R.string.surface_dispatch_uimanager_open_termux_once_then_retry_from_this_cons_7349c), echoToConsole)
        }
        return false
    }

    private fun ensureTermuxBridgeReady(echoToConsole: Boolean): Boolean {
        val status = TermuxBridgeManager.status(mContext!!)
        if (!status.termuxInstalled) {
            reportTermuxDispatch(mContext.getString(R.string.surface_termux_is_not_installed_3aba0), echoToConsole)
            return false
        }

        if (!status.runCommandDeclared) {
            reportTermuxDispatch(mContext.getString(R.string.surface_this_termux_build_does_not_expose_run_command_b2ba8), echoToConsole)
            reportTermuxDispatch(
                mContext.getString(R.string.surface_install_update_termux_from_f_droid_or_github_then_retry_307ff),
                echoToConsole
            )
            return false
        }

        if (!status.runCommandGranted) {
            requestRunCommandPermissionIfPossible(
                mContext,
                LauncherActivity.COMMAND_REQUEST_PERMISSION
            )
            reportTermuxDispatch(mContext.getString(R.string.surface_runcommand_permission_is_not_granted_yet_29f35), echoToConsole)
            reportTermuxDispatch(
                mContext.getString(R.string.surface_if_android_shows_a_permission_prompt_allow_re_t_ui_and_ret_1eb39),
                echoToConsole
            )
            reportTermuxDispatch(mContext.getString(R.string.surface_termux_must_also_have_allow_external_apps_true_bd4a5), echoToConsole)
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
        if (candidate == null || candidate.length == 0) {
            return candidate ?: Tuils.EMPTYSTRING
        }

        var alias = mainPack.aliasManager.getAlias(candidate, false, AliasManager.SCOPE_SCRIPT)
        if (alias.size == 0 || alias[0] == null || alias[0]!!.trim { it <= ' ' }.length == 0) {
            alias = mainPack.aliasManager.getAlias(candidate, false)
        }

        if (alias.size == 0 || alias[0] == null || alias[0]!!.trim { it <= ' ' }.length == 0) {
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
        if (path?.startsWith("workspace-activity:") == true) return
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

        appendTermuxLine(mContext.getString(R.string.surface_result_d3272, (if (path == null) mContext.getString(R.string.surface_termux_command_b82dc) else path)))
        if (exitCode != Int.Companion.MIN_VALUE) {
            appendTermuxLine(mContext.getString(R.string.surface_exit_da8cd, exitCode))
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
            appendTermuxLine(mContext.getString(R.string.surface_error_701e5, error.trim { it <= ' ' }))
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(mContext.getString(R.string.surface_debug_cf37f, debug.trim { it <= ' ' }))
        }
        if ((stdout == null || stdout.trim { it <= ' ' }.length == 0)
            && (stderr == null || stderr.trim { it <= ' ' }.length == 0)
            && (error == null || error.trim { it <= ' ' }.length == 0)
        ) {
            appendTermuxLine(mContext.getString(R.string.surface_no_output_returned_05405))
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
            appendTermuxLine(mContext.getString(R.string.surface_cwd_2af21, termuxWorkingDirectory))
            return
        }
        appendTermuxLine(mContext.getString(R.string.surface_cd_failed_5abcd, target))
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
            appendTermuxLine(mContext.getString(R.string.surface_done_11556, command))
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
            appendTermuxLine(mContext.getString(R.string.surface_error_701e5, error.trim { it <= ' ' }))
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            appendTermuxLine(mContext.getString(R.string.surface_debug_cf37f, debug.trim { it <= ' ' }))
        }
        if (exitCode != Int.Companion.MIN_VALUE && exitCode != 0) {
            appendTermuxLine(mContext.getString(R.string.surface_exit_da8cd, exitCode))
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
            MainManager.interactive.addCommand(
                "cd '" + folder.getAbsolutePath().replace("'", "'\\''") + "'"
            )
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_HINT
                )
            )
            fileConsoleController?.refreshFileConsole(true)
        }
        if (label.startsWith("fm-dirs ")) {
            val target = label.substring(8)
            if (exitCode == 0) {
                putDirs(target, stdout)
                fileConsoleController?.updateFileConsoleFromTermux(target, null)
            } else {
                fileConsoleController?.updateFileConsoleFromTermux(target, stderr)
            }
            return
        } else if (label.startsWith("fm-files ")) {
            val target = label.substring(9)
            if (exitCode == 0) {
                putFiles(target, stdout)
                fileConsoleController?.updateFileConsoleFromTermux(target, null)
            } else {
                fileConsoleController?.updateFileConsoleFromTermux(target, stderr)
            }
            return
        } else if (label.startsWith("dirs ") && exitCode == 0) {
            val target = label.substring(5)
            putDirs(target, stdout)
            fileConsoleController?.updateFileConsoleFromTermux(target, null)
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_SUGGESTIONS
                )
            )
            return
        } else if (label.startsWith("files ") && exitCode == 0) {
            val target = label.substring(6)
            putFiles(target, stdout)
            fileConsoleController?.updateFileConsoleFromTermux(target, null)
            LocalBroadcastManager.getInstance(mContext!!.getApplicationContext()).sendBroadcast(
                Intent(
                    ACTION_UPDATE_SUGGESTIONS
                )
            )
            return
        }

        val builder = StringBuilder()
        builder.append(mContext.getString(R.string.surface_termux_bridge_3deda)).append(label)
        if (exitCode != Int.Companion.MIN_VALUE) {
            builder.append(mContext.getString(R.string.surface_exit_66ea3)).append(exitCode)
        }
        if (stdout != null && stdout.trim { it <= ' ' }.length > 0) {
            builder.append("\n").append(stdout.trim { it <= ' ' })
        }
        if (stderr != null && stderr.trim { it <= ' ' }.length > 0) {
            builder.append(mContext.getString(R.string.surface_stderr_59a49)).append(stderr.trim { it <= ' ' })
        }
        if (error != null && error.trim { it <= ' ' }.length > 0) {
            builder.append(mContext.getString(R.string.surface_error_efc3d)).append(error.trim { it <= ' ' })
        }
        if (debug != null && debug.trim { it <= ' ' }.length > 0) {
            builder.append(mContext.getString(R.string.surface_debug_7052a)).append(debug.trim { it <= ' ' })
        }
        Tuils.sendOutput(mContext, builder.toString(), TerminalManager.CATEGORY_OUTPUT)
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
            text = mContext.getString(R.string.surface_stderr_544b7, stderr.trim { it <= ' ' })
        } else if (!TextUtils.isEmpty(error) && error!!.trim { it <= ' ' }.length > 0) {
            text = mContext.getString(R.string.surface_error_701e5, error.trim { it <= ' ' })
        } else if (exitCode != Int.Companion.MIN_VALUE) {
            text = mContext.getString(R.string.surface_exit_no_output_returned_09cea, exitCode)
        } else {
            text = mContext.getString(R.string.surface_no_output_returned_a4db4)
        }

        val id = ModuleManager.normalize(module)
        ModuleManager.setScriptText(mContext, id, text)
        if (id == activeModule) {
            showHomeModule(id)
        }
        updateModuleDockSelection()
        Tuils.sendOutput(mContext, mContext.getString(R.string.surface_module_refreshed_97f96, id))
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
            appendTermuxLine(mContext.getString(R.string.surface_callback_hint_if_the_module_did_not_appear_add_this_to_the_eb234))
            appendTermuxLine(mContext.getString(R.string.surface_p_com_dvil_tui_renewed_e100d))
        }
    }

    private fun openTermuxApp() {
        val launchIntent = mContext!!.getPackageManager()
            .getLaunchIntentForPackage(TermuxBridgeManager.TERMUX_PACKAGE)
        if (launchIntent == null) {
            appendTermuxLine(mContext.getString(R.string.surface_termux_is_not_installed_3aba0))
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            mContext!!.startActivity(launchIntent)
            appendTermuxLine(mContext.getString(R.string.surface_opened_termux_4f527))
        } catch (e: Exception) {
            appendTermuxLine(mContext.getString(R.string.surface_unable_to_open_termux_1f222, e.javaClass.getSimpleName()))
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
        podcastTab = rootView.findViewById(R.id.podcast_tab)

        clockTabDock?.styleClockTab(timerTab, View.OnClickListener { v: View? ->
            val message = ClockManager.getInstance(mContext).stopTimer()
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT)
        })
        clockTabDock?.styleClockTab(stopwatchTab, View.OnClickListener { v: View? ->
            val message = ClockManager.getInstance(mContext).stopStopwatch()
            Tuils.sendOutput(mContext, message, TerminalManager.CATEGORY_OUTPUT)
        })
        stylePodcastPill(podcastTab)
    }

    private fun stylePodcastPill(tab: ImageView?) {
        if (tab == null) {
            return
        }
        LauncherPillStyle.apply(mContext!!, tab)
        tab.setOnClickListener { launchPodcastPlayer() }
    }

    private fun launchPodcastPlayer() {
        mContext.startActivity(Intent(mContext, MediaPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MediaPlayerActivity.EXTRA_NOW_PLAYING, true))
    }

    /**
     * The pill stands in for the player while a podcast is loaded and the player is off screen. It appears only
     * once an episode has played in this run, so a queue restored at start does not bring it back on its own.
     */
    fun syncPodcastPill() {
        val manager = mainPack.podcastManager
        if (manager.isPlaying() || manager.isPreparing()) podcastPillArmed = true
        else if (manager.currentSong() == null) podcastPillArmed = false
        val tab = podcastTab ?: mRootView?.findViewById(R.id.podcast_tab)
        podcastTab = tab
        if (tab == null) {
            return
        }
        val wanted = podcastPillArmed && !MediaPlayerActivity.visible
        val state = wanted to manager.isPlaying()
        if (state == podcastPillState) return
        podcastPillState = state
        if (!wanted) {
            hidePodcastPill()
            return
        }
        publishEdgePodcast()
        if (ohi.andre.consolelauncher.sidebar.EdgePills.onEdge(mContext)) {
            tab.visibility = View.GONE
            return
        }
        if (tab.visibility == View.VISIBLE) return
        stylePodcastPill(tab)
        tab.visibility = View.VISIBLE
        if (!podcastTabDockReady) {
            clockTabDock?.makeClockTabDockable(
                tab,
                PREF_PODCAST_BADGE_EDGE,
                PREF_PODCAST_BADGE_FRACTION,
                CLOCK_EDGE_RIGHT,
                0.35f
            )
            podcastTabDockReady = true
        }
        applyPodcastPillDock(tab)
        // Rotate→minimize can run before insets/layout settle — redock next frames.
        tab.post { applyPodcastPillDock(tab) }
        mRootView?.postDelayed({ applyPodcastPillDock(tab) }, 120)
    }

    private fun applyPodcastPillDock(tab: View) {
        val land = podcastLandscapePresentation()
        val frac = preferences.getFloat(
            PREF_PODCAST_BADGE_FRACTION,
            if (land) 0.42f else 0.35f
        ).coerceIn(0f, 1f)
        if (land) {
            // Portrait right-edge dock lands on the terminal pane / bezel — park left instead.
            clockTabDock?.forceClockTabEdge(tab, CLOCK_EDGE_LEFT, frac)
        } else {
            clockTabDock?.applyClockTabDock(
                tab,
                PREF_PODCAST_BADGE_EDGE,
                PREF_PODCAST_BADGE_FRACTION,
                CLOCK_EDGE_RIGHT,
                0.35f
            )
        }
        tab.elevation = Tuils.dpToPx(mContext, 16).toFloat()
        tab.bringToFront()
    }

    /** The podcast as a screen-edge pill: its button plays/pauses, tapping it opens the player. */
    private fun publishEdgePodcast() {
        val manager = mainPack.podcastManager
        ohi.andre.consolelauncher.sidebar.EdgePills.setPodcast(mContext, ohi.andre.consolelauncher.sidebar.EdgePills.Podcast(
            playing = { manager.isPlaying() },
            toggle = { manager.toggle() },
            open = { launchPodcastPlayer() }
        ))
    }

    private fun hidePodcastPill() {
        podcastTab?.visibility = View.GONE
        ohi.andre.consolelauncher.sidebar.EdgePills.setPodcast(mContext, null)
    }

    fun expandNotification(index: Int) {
        notificationsModule.expandNotification(index)
    }

    fun collapseNotification(): Boolean = notificationsModule.collapseNotification()

    fun nextNotificationPage() {
        notificationsModule.nextNotificationPage()
    }

    fun previousNotificationPage() {
        notificationsModule.previousNotificationPage()
    }

    fun startCurrentNotificationReply() {
        notificationsModule.startCurrentNotificationReply()
    }

    fun dismissCurrentNotification() {
        notificationsModule.dismissCurrentNotification()
    }

    fun openNotificationShade(): Boolean = notificationsModule.openNotificationShade()

    private fun updateContextContainerVisibility(rootView: View?) {
        // Widgets are now inside terminalContainer in terminalPage
    }

    val isAppsDrawerOpen: Boolean
        get() = appDrawerPaneManager?.isOpen == true

    val isAndroidWidgetDrawerOpen: Boolean
        get() = androidWidgetDrawerManager?.isOpen == true

    fun hideAppsDrawer() {
        appDrawerPaneManager?.hide()
    }

    fun hideAndroidWidgetDrawer() {
        androidWidgetDrawerManager?.hide()
    }

    @JvmOverloads
    fun showAppsDrawer(filter: String? = null, ranking: ohi.andre.consolelauncher.managers.apps.AppsManager.Ranking? = null, limit: Int = 500) {
        androidWidgetDrawerManager?.hide()
        appDrawerPaneManager?.show(filter, ranking, limit)
        scheduleTypefaceRefreshes()
    }

    fun showAndroidWidgetDrawer() {
        appDrawerPaneManager?.hide()
        androidWidgetDrawerManager?.show()
        scheduleTypefaceRefreshes()
    }

    fun returnToLauncherHome() {
        if (isAndroidWidgetDrawerOpen) hideAndroidWidgetDrawer()
        if (isAppsDrawerOpen) hideAppsDrawer()
        if (isTermuxConsoleVisible) closeTermuxConsole()
        homeModes.active.onHome()
    }

    private fun setWallpaperTouchEnabled(enabled: Boolean) {
        mContext.sendBroadcast(
            Intent(RetuiWallpaperService.ACTION_TOUCH_INTERACTION)
                .setPackage(mContext.packageName)
                .putExtra(RetuiWallpaperService.EXTRA_TOUCH_ENABLED, enabled)
        )
    }

    private fun restoreWallpaperTouchAfterDrawerClose() {
        if (!isAppsDrawerOpen && !isAndroidWidgetDrawerOpen) {
            setWallpaperTouchEnabled(true)
        }
    }

    fun refreshAndroidWidgetDrawerGrid() {
        androidWidgetDrawerManager?.refreshGrid()
    }

    fun handleAndroidWidgetActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        return androidWidgetDrawerManager?.handleActivityResult(requestCode, resultCode, data) == true
    }

    fun applyAppearance(previous: LauncherSettings.Snapshot, current: LauncherSettings.Snapshot) {
        val root = mRootView as? ViewGroup ?: return
        val old = ohi.andre.consolelauncher.presentation.HomeAppearance(previous.values)
        val next = ohi.andre.consolelauncher.presentation.HomeAppearance(current.values)
        statusAppearance = next
        ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleRoot(root, next, root.getTag(R.id.home_root_binding) as? Boolean ?: true)
        fun style(view: View) {
            ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleSurface(view, next, old)
            (view.getTag(R.id.home_widget_binding) as? TuiWidgetDecorator.Binding)?.let {
                TuiWidgetDecorator.decorateDraft(view, it, next)
            }
            if (view is ImageView) {
                ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleToolbarIcon(view, next)
                if (view.parent?.let { it is View && it.id == R.id.tools_view } == true)
                    ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleToolbarButton(view, next)
            }
            if (view is TextView) {
                if (view.id in intArrayOf(R.id.input_view, R.id.prefix_view, R.id.terminal_view)) {
                    ohi.andre.consolelauncher.presentation.HomeChromePresentation.styleTerminalText(view, next, view.id != R.id.terminal_view)
                    if (view.id == R.id.terminal_view) view.text = ohi.andre.consolelauncher.tuils.ThemeColorSpan.refresh(view.text, next::color)
                }
                (view.getTag(R.id.home_suggestion_binding) as? ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler.Binding)?.let {
                    ohi.andre.consolelauncher.managers.suggestions.SuggestionChipStyler.styleDraft(view, it, next, old)
                }
                ohi.andre.consolelauncher.presentation.ModuleContentPresentation.styleDraft(view, next, old)
                // Module dock buttons draw their own frame, so presets must restyle them too.
                if (view.getTag(R.id.home_dock_binding) is Boolean)
                    ohi.andre.consolelauncher.managers.modules.ModuleDockButtonFactory.styleDraft(view, next)
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) style(view.getChildAt(index))
        }
        style(root)
        ohi.andre.consolelauncher.presentation.StatusPresentation.rows(next).forEachIndexed { index, members ->
            labelViews?.getOrNull(index)?.let { view ->
                ohi.andre.consolelauncher.presentation.StatusPresentation.bindStyle(view, members.first(), next, unifiedStatusActive)
                ohi.andre.consolelauncher.presentation.StatusPresentation.bindContent(view, members,
                    ohi.andre.consolelauncher.presentation.StatusPresentation.capture(labelTexts), next, old)
            }
        }
        ohi.andre.consolelauncher.presentation.UnifiedStatusPresentation.style(root, next)
        if (searchOnlyMode) {
            configureSearchOnlyLayout()  // the search bar and clock tab are drawn from the theme, not the traversal above
            suggestionsManager?.refreshSearchAppearance()
        }
        for (id in intArrayOf(R.id.terminal_home_add_tab, R.id.terminal_home_settings_tab)) {
            root.findViewById<ImageButton>(id)?.let { button ->
                button.setColorFilter(next.color(Theme.time_text_color))
                button.background = root.findViewById<View>(R.id.search_mode_clock_tab)?.background?.constantState?.newDrawable()?.mutate()
            }
        }
        terminalTrayToggle?.let { TerminalTrayToggleView.styleDraft(it, root, next, old) }
        listOfNotNull(ramManager,storageManager,networkManager,tuiTimeManager,unlockManager,tuiNotesManager,weatherManager)
            .forEach { it.refreshAppearance() }
        batteryManager?.refreshAppearance()
        homeModes.active.onAppearanceChanged()
        val input = mTerminalAdapter?.inputView as? EditText
        applyRetuiKeyboardTheme(input, "launcher")
        if (keyboardVisible && input?.hasFocus() == true) sendRetuiKeyboardTheme(input, "launcher")
        root.invalidate()
    }

    fun preserveSurfaceSessionForReload() {
        captureLauncherSurfaceSession()
    }

    fun snapshotTerminalState(): TerminalManager.State? = mTerminalAdapter?.snapshotState()

    fun restoreTerminalState(state: TerminalManager.State?) {
        if (state != null) mTerminalAdapter?.restoreState(state)
    }

    fun dispose() {
        homeModes.active.dispose()
        launcherWindowFocused = false
        ramManager?.stop()
        batteryManager?.stop()
        storageManager?.stop()
        networkManager?.stop()
        tuiTimeManager?.stop()
        unlockManager?.dispose()
        tuiNotesManager?.stop()
        weatherManager?.stop()
        captureLauncherSurfaceSession()
        cancelTerminalOutputAutoHide()

        profilePaneController?.hide()
        closeLuaAppSession(false)
        luaModules.disposeEngines()
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }

        asciiAnimationManager?.stop()
        if (suggestionsManager != null) suggestionsManager!!.dispose()
        if (notesManager != null) notesManager!!.dispose(mContext)
        androidWidgetDrawerManager?.dispose()
        LocalBroadcastManager.getInstance(mContext!!.getApplicationContext())
            .unregisterReceiver(receiver)
        try {
            mContext!!.getApplicationContext().unregisterReceiver(receiver)
        } catch (ignored: Exception) {
        }
        Tuils.unregisterBatteryReceiver(mContext)

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
        if (!homeModes.active.managesOwnKeyboard) { activateTerminalInput(openKeyboardOnStart); return }
        homeModes.active.onStart()
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
        homeModes.active.onOutput(s)
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.setOutput(s, category)
        } else {
            pendingOutputs.add(OutputHolder(s, category))
        }
    }

    fun setOutput(color: Int, output: CharSequence?) {
        homeModes.active.onOutput(output)
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
        if (handleTermuxBackPressed()) {
            return
        }
        if (profilePaneController?.visible == true) {
            closeProfileSurface()
            return
        }
        if (this.isPomodoroOverlayVisible) {
            return
        }
        if (this.isAndroidWidgetDrawerOpen) {
            if (androidWidgetDrawerManager?.handleBack() != true) hideAndroidWidgetDrawer()
            return
        }
        if (this.isAppsDrawerOpen) {
            hideAppsDrawer()
            return
        }
        if (isSearchModulesOpen) {
            searchSurface.closeModules()
            return
        }
        // Surfaces opened by commands (podcast, profile, drawers) close first in every mode.
        if (homeModes.active.handleBack()) return
        if (this.isOutputTrayToggleEnabled && terminalTrayExpanded) {
            setTerminalTrayExpanded(false)
            return
        }
        if (collapseNotification()) {
            return
        }
        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.onBackPressed()
        }
    }

    fun activateTerminalInput(showSoftKeyboard: Boolean, preserveSelection: Boolean = false) {
        onLauncherInteraction()
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
        if (!preserveSelection) mTerminalAdapter!!.focusInputEnd()
        if (showSoftKeyboard) {
            input?.post(Runnable {
                sendRetuiKeyboardTheme(input as? EditText, "launcher")
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            })
        }
    }

    fun pause() {
        homeModes.active.onPause()
        launcherWindowFocused = false

        if (mTerminalAdapter != null) {
            closeKeyboard()
        }
        if (handler != null) {
            homeModules.onLauncherPaused()
            handler!!.removeCallbacks(eventsRefreshRunnable)
            luaModules.cancelTick()
            handler!!.removeCallbacks(fontRefreshRunnable)
            mRootView?.removeCallbacks(fontRefreshRunnable)
        }
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
        tuiNotesManager?.stop()
        pauseAsciiAnimation()
        androidWidgetDrawerManager?.stopListening()
    }

    fun resume() {
        homeModes.active.onResume()
        if (handler == null) {
            return
        }
        launcherWindowFocused = true
        refreshSuggestionsNow()
        if (restoreLauncherChromeOnResume) {
            restoreLauncherChromeOnResume = false
            restoreLauncherChromeAfterSurface()
        }
        homeModules.onLauncherResumed()
        scheduleEventsRefreshIfNeeded()

        if (ramManager != null) ramManager!!.start()
        if (batteryManager != null) batteryManager!!.start()
        if (storageManager != null) storageManager!!.start()
        if (networkManager != null) networkManager!!.start()
        if (tuiTimeManager != null) tuiTimeManager!!.start()
        if (unlockManager != null) unlockManager!!.start()
        tuiNotesManager?.start()
        resumeAsciiAnimation()
        playAsciiEffect()
        if (androidWidgetDrawerManager?.isOpen == true) {
            androidWidgetDrawerManager?.startListening()
        } else {
            androidWidgetDrawerManager?.stopListening()
        }

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
            focusOverlayController?.updatePomodoroOverlay(intent)
        }

        val lockdown = LockdownManager.getInstance(mContext)
        if (lockdown.isRunning) {
            val intent = Intent(LockdownManager.ACTION_LOCKDOWN_STATE)
            intent.putExtra(LockdownManager.EXTRA_LOCKDOWN_RUNNING, true)
            intent.putExtra(LockdownManager.EXTRA_LOCKDOWN_REMAINING, lockdown.remainingMillis)
            intent.putExtra(LockdownManager.EXTRA_LOCKDOWN_TOTAL, lockdown.totalDuration)
            intent.putExtra(LockdownManager.EXTRA_LOCKDOWN_REASON, lockdown.reason)
            focusOverlayController?.updateLockdownOverlay(intent)
        }
    }

    fun onLauncherWindowFocusChanged(hasFocus: Boolean) {
        launcherWindowFocused = hasFocus
        if (hasFocus) {
            resumeAsciiAnimation()
        } else {
            pauseAsciiAnimation()
        }
    }

    fun onConfigurationChanged(newConfig: Configuration?) {
        if (newConfig != null) profilePaneController?.onConfigurationChanged(newConfig)
        applyResponsiveLandscapeLayout(newConfig)

        updateTermuxConsoleKeyMode()

        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.scrollToEnd()
            mTerminalAdapter!!.focusInputEnd()
        }
        if (mRootView != null) {
            mRootView.post {
                // Docked pills keep portrait translation across layout — clamp after rotate.
                reapplyVisibleClockTabDocks()
            }
            mRootView.postDelayed(Runnable { this.scheduleTypefaceRefreshes() }, 48)
        }
    }

    private fun reapplyVisibleClockTabDocks() {
        val root = mRootView ?: return
        val podcast = podcastTab ?: root.findViewById(R.id.podcast_tab)
        if (podcast.visibility == View.VISIBLE
        ) {
            applyPodcastPillDock(podcast)
        }
        clockTabDock?.reapplyVisibleTimerAndStopwatchDocks()
    }

    fun scheduleTypefaceRefreshes() {
        if (mRootView == null) {
            return
        }

        mRootView.removeCallbacks(fontRefreshRunnable)
        handler?.removeCallbacks(fontRefreshRunnable)
        mRootView.postOnAnimation(fontRefreshRunnable)
    }

    private fun refreshLauncherTypeface() {
        val typeface = Tuils.getTypeface(mContext)
        if (mRootView == null) {
            return
        }

        if (typeface != null) {
            applyTypefaceRecursively(mRootView, typeface)
        }
        TextShadows.applyModule(homeModulesContainer)
        LauncherFontScale.applyRecursively(mRootView)

        if (mTerminalAdapter != null) {
            mTerminalAdapter!!.refreshTypeface()
        }

        val asciiView = getLabelView(Label.ascii)
        if (asciiView != null) {
            asciiView.setTypeface(Typeface.MONOSPACE)
        }
    }

    private fun applyTypefaceRecursively(view: View?, typeface: Typeface?) {
        if (view is android.appwidget.AppWidgetHostView) return
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
        onLauncherInteraction()
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureStartedOnOutput = v === terminalView || v === terminalView?.parent?.parent
            touchStartedOnAscii = v is AsciiArtTextView
        }
        val handled = gestureDetector?.onTouchEvent(event) == true
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            v.performClick()
        }
        return if (gestureStartedOnOutput) v.onTouchEvent(event) else handled || v.onTouchEvent(event)
    }

    private fun onLauncherInteraction() {
        if (!launcherWindowFocused || isLockdownOverlayVisible) {
            return
        }
        if (asciiIdlePaused) {
            asciiIdlePaused = false
            asciiAnimationManager?.start()
            // A looping effect picks up after its rest; a play-once effect stays finished.
            playAsciiEffect(delayed = true)
        }
        scheduleAsciiIdlePause()
    }

    private fun pauseAsciiAnimation() {
        handler?.removeCallbacks(asciiIdlePauseRunnable)
        asciiAnimationManager?.stop()
        AsciiArtTextView.stopEffects()
    }

    /** Home appeared (or the art changed): play the chosen intro effect, unless the art animates by itself. */
    private fun playAsciiEffect(delayed: Boolean = false) {
        val manager = asciiAnimationManager ?: return
        if (manager.animated || isLockdownOverlayVisible) return
        val name = XMLPrefsManager.get(Behavior.ascii_effect)
        val loop = XMLPrefsManager.getInt(Behavior.ascii_effect_loop_seconds).coerceAtLeast(0)
        if (delayed && loop == 0) return
        // After the art is bound to its view, which happens on the next pass.
        val speed = AsciiEffects.speed(XMLPrefsManager.get(Behavior.ascii_effect_speed))
        (handler ?: Handler(Looper.getMainLooper())).post { AsciiArtTextView.playEffects(name, loop, delayed, speed) }
    }

    private fun resumeAsciiAnimation() {
        if (isLockdownOverlayVisible) {
            return
        }
        asciiIdlePaused = false
        asciiAnimationManager?.start()
        scheduleAsciiIdlePause()
    }

    private fun scheduleAsciiIdlePause() {
        val currentHandler = handler ?: return
        currentHandler.removeCallbacks(asciiIdlePauseRunnable)
        if (launcherWindowFocused && asciiAnimationManager != null) {
            currentHandler.postDelayed(asciiIdlePauseRunnable, ASCII_IDLE_PAUSE_MS)
        }
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
                    hackOverlayController?.playHackOverlay()
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

    companion object {
        const val NEXT_UNLOCK_CYCLE_RESTART: String = "nextUnlockRestart"
        const val UNLOCK_KEY: String = "unlockTimes"
        const val PREFS_NAME: String = "ui"
        private const val PREF_OUTPUT_TRAY_EXPANDED = "output_tray_expanded"
        internal const val CLOCK_EDGE_LEFT = "left"
        internal const val CLOCK_EDGE_RIGHT = "right"
        internal const val CLOCK_EDGE_TOP = "top"
        internal const val CLOCK_EDGE_BOTTOM = "bottom"
        internal const val PREF_TIMER_BADGE_EDGE = "timer_badge_edge"
        internal const val PREF_TIMER_BADGE_FRACTION = "timer_badge_fraction"
        internal const val PREF_STOPWATCH_BADGE_EDGE = "stopwatch_badge_edge"
        internal const val PREF_STOPWATCH_BADGE_FRACTION = "stopwatch_badge_fraction"
        private const val PREF_PODCAST_BADGE_EDGE = "podcast_badge_edge"
        private const val PREF_PODCAST_BADGE_FRACTION = "podcast_badge_fraction"
        private const val ASCII_IDLE_PAUSE_MS = 120_000L
        private const val UNIFIED_MODULE_AUTO_HIDE_MS = 10_000L
        internal fun shouldAutoHideUnifiedModules(activeModule: String?): Boolean = activeModule.isNullOrEmpty()
        val ACTION_UPDATE_SUGGESTIONS: String =
            BuildConfig.APPLICATION_ID + ".ui_update_suggestions"
        val ACTION_UPDATE_APPS: String =
            BuildConfig.APPLICATION_ID + ".ui_update_apps"
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
        const val WEATHER_SYMBOL: String = "weatherSymbol"
        const val WEATHER_CONDITION: String = "weatherCondition"
        const val WEATHER_DETAILS: String = "weatherDetails"

        val ACTION_REQUEST_NOTIFICATION_FEED: String =
            NotificationService.ACTION_REQUEST_NOTIFICATION_FEED
        val ACTION_CLOCK_STATE: String = ClockManager.ACTION_CLOCK_STATE
        val ACTION_POMODORO_STATE: String = PomodoroManager.ACTION_POMODORO_STATE
        val ACTION_LOCKDOWN_STATE: String = LockdownManager.ACTION_LOCKDOWN_STATE
        val ACTION_TERMUX_CONSOLE: String = BuildConfig.APPLICATION_ID + ".ui_termux_console"
        const val EXTRA_TERMUX_COMMAND: String = "termux_command"
        val ACTION_TMUX_WORKSPACE: String = BuildConfig.APPLICATION_ID + ".ui_tmux_workspace"
        const val EXTRA_TMUX_WORKSPACE_COMMAND: String = "tmux_workspace_command"
        val ACTION_LUA_APP: String = BuildConfig.APPLICATION_ID + ".ui_lua_app"
        const val EXTRA_LUA_APP_ID: String = "lua_app_id"
        val ACTION_FILE_CONSOLE: String = BuildConfig.APPLICATION_ID + ".ui_file_console"
        const val EXTRA_FILE_COMMAND: String = "file_command"
        val ACTION_PROFILE_SURFACE: String = BuildConfig.APPLICATION_ID + ".ui_profile_surface"
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
        private const val TERMUX_WORKSPACE_KEY_MODE_SWIPE_THRESHOLD_PX = 70f
        private const val TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_OUT_MS = 92L
        private const val TERMUX_WORKSPACE_KEY_MODE_CAROUSEL_IN_MS = 124L
        private const val TERMUX_PORTRAIT_KEYS_PER_ROW = 8
        private const val TERMUX_LANDSCAPE_KEYS_PER_ROW = 16
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
        private const val OUTPUT_HEADER_MODE_ARROWS = "arrows"
        private const val OUTPUT_HEADER_MODE_NONE = "none"
        private const val OUTPUT_AUTO_HIDE_MIN_SECONDS = 1
        private const val OUTPUT_AUTO_HIDE_MAX_SECONDS = 3600
        internal const val MODULE_TEXT_FONT_THEME = "theme"
        internal const val MODULE_TEXT_FONT_MONO = "mono"
        private const val EVENTS_REFRESH_GRACE_MS: Long = 1000
        private val EVENTS_REFRESH_FALLBACK_MS = (60 * 1000).toLong()
        private val LABEL_INDEX_UNMAPPED = -1f
        private val BATTERY_PERCENT = Regex("\\b\\d{1,3}%")
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

        internal fun duoModulePaneIsLeft(activeMode: String?, topPanesSwapped: Boolean): Boolean =
            (activeMode == DUO_LAYOUT_LEFT) != topPanesSwapped

        internal fun constrainTerminalOutputHeight(
            requestedHeight: Int,
            rootHeight: Int,
            imeBottomOffset: Int,
            systemBottomInset: Int,
            persistentInputHeight: Int,
            imeVisible: Boolean
        ): Int {
            return ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.constrain(
                requestedHeight,rootHeight,imeBottomOffset,systemBottomInset,persistentInputHeight,imeVisible)
        }

        internal fun legacyImeBottomOffset(
            rootBottom: Int,
            visibleFrameBottom: Int,
            systemBottomInset: Int,
            visibilityThreshold: Int
        ): Int {
            val hiddenBottom = max(
                0,
                rootBottom - visibleFrameBottom - max(0, systemBottomInset)
            )
            return if (hiddenBottom > max(0, visibilityThreshold)) hiddenBottom else 0
        }

        internal fun keyboardMarginOverrideActive(
            imeVisible: Boolean,
            imeBottomOffset: Int,
            inputFocused: Boolean
        ): Boolean = imeVisible || imeBottomOffset > 0 || inputFocused

        internal fun outputAutoHideDelayMs(seconds: Int): Long =
            seconds.coerceIn(OUTPUT_AUTO_HIDE_MIN_SECONDS, OUTPUT_AUTO_HIDE_MAX_SECONDS) * 1000L

        internal fun outputPaneVisibility(autoHideEnabled: Boolean, expanded: Boolean): Int =
            if (autoHideEnabled && !expanded) View.INVISIBLE else View.VISIBLE

        internal fun headerOnlyTerminalTrayHeight(headerBottom: Int, fallback: Int): Int =
            ohi.andre.consolelauncher.presentation.TerminalTrayPresentation.headerOnly(headerBottom,fallback)

        internal fun compactUnifiedBattery(value: CharSequence?): String {
            val text = value?.toString().orEmpty()
            return BATTERY_PERCENT.find(text)?.value?.let { "BAT: $it" }
                ?: text.lineSequence().firstOrNull().orEmpty().trim()
        }

        internal fun compactUnifiedLine(value: CharSequence?): String =
            value?.toString()?.lineSequence()?.firstOrNull()?.trim().orEmpty()

        internal fun compactUnifiedWeatherCondition(value: CharSequence?): String {
            val line = compactUnifiedLine(value)
            return line.substringAfter("Weather:", line).substringBefore(',').trim()
        }

        internal fun compactUnifiedWeatherDetails(value: CharSequence?): String {
            val line = compactUnifiedLine(value)
            return line.substringAfter(',', "").trim()
        }

        internal fun compactUnifiedCapacity(availableGb: Double, totalGb: Double): String {
            if (totalGb <= 0.0) return ""
            fun number(value: Double): String =
                if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
            return "${number(availableGb)}/${number(totalGb)} GB"
        }

        internal fun boundedUnifiedModuleHeight(contentHeight: Int, capHeight: Int): Int =
            min(max(0, contentHeight), max(0, capHeight))

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
    }
}
