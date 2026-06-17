package ohi.andre.consolelauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.managers.RegexManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.onboarding.GuideManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.invalidate
import ohi.andre.consolelauncher.managers.settings.LauncherSettings.refreshFromLoadedPrefs
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.printToOutput
import ohi.andre.consolelauncher.managers.settings.NotificationSettings.showTerminal
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.applyFullscreen
import ohi.andre.consolelauncher.tuils.LauncherSystemUi.requestNoTitleIfFullscreen
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.PublicIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Inputable
import ohi.andre.consolelauncher.tuils.interfaces.Outputable
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import java.io.File
import kotlin.math.max
import android.content.BroadcastReceiver
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.NonNull
import java.lang.reflect.Field
import java.util.ArrayList
import java.util.HashSet
import java.util.Set
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.settings.LauncherSettings
import ohi.andre.consolelauncher.managers.settings.NotificationSettings
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.commands.main.MainPack

class LauncherActivity : AppCompatActivity(), Reloadable {
    @get:JvmName("getUIManager")
    var uiManager: UIManager? = null
        private set
    private var main: MainManager? = null

    private var privateIOReceiver: PrivateIOReceiver? = null
    private var publicIOReceiver: PublicIOReceiver? = null

    private var openKeyboardOnStart = false
    private val pendingReloadMessages = StringBuilder()
    private var canApplyTheme = false
    private var backButtonEnabled = false

    private var categories: MutableSet<ReloadMessageCategory?>? = null

    private val stopActivity = Runnable { finishAndRemoveTask() }

    private val `in`: Inputable = object : Inputable {
        override fun `in`(s: String?) {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setInput(s)
            }
        }

        override fun changeHint(s: String?) {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setHint(s)
            }
        }

        override fun resetHint() {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setHint(
                    Tuils.getHint(
                        if (main != null) main!!.mainPack.currentDirectory.getAbsolutePath() else Tuils.getFolder()
                            .getAbsolutePath()
                    )
                )
            }
        }

        fun onBack() {
            if (backButtonEnabled) {
                onBackPressed()
            }
        }
    }

    private val out: Outputable = object : Outputable {
        override fun onOutput(s: CharSequence?, category: Int) {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setOutput(s, category)
            }
        }

        override fun onOutput(color: Int, s: CharSequence?) {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setOutput(color, s)
            }
        }

        override fun onOutput(s: CharSequence?) {
            if (this@LauncherActivity.uiManager != null) {
                uiManager!!.setOutput(s, TerminalManager.CATEGORY_OUTPUT)
            }
        }

        override fun dispose() {}
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        instance = this

        // Special check for MANAGE_EXTERNAL_STORAGE (API 30+)
        if (shouldRequestAllFilesAccess()) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.setData(
                        Uri.parse(
                            String.format(
                                "package:%s",
                                getApplicationContext().getPackageName()
                            )
                        )
                    )
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST)
                }
                Toast.makeText(
                    this,
                    "Please grant storage permissions to Re:T-UI",
                    Toast.LENGTH_LONG
                ).show()
                enableEdgeToEdge()
                super.onCreate(savedInstanceState)
                return
            }
        }

        XMLPrefsManager.loadCommons(this)
        refreshFromLoadedPrefs()

        val useSystemWP = XMLPrefsManager.getBoolean(Ui.system_wallpaper)
        if (useSystemWP) {
            setTheme(R.style.Custom_SystemWP)
        } else {
            setTheme(R.style.Custom_Solid)
        }

        requestNoTitleIfFullscreen(this)
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        overridePendingTransition(0, 0)

        if (isFinishing()) {
            return
        }

        val permissionsToRequest: MutableList<String?> = ArrayList<String?>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (shouldRequestLegacyExternalStoragePermissions()) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray<String?>(),
                STARTING_PERMISSION
            )
        } else {
            canApplyTheme = true
            finishOnCreate()
        }
    }

    private fun finishOnCreate() {
        val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.currentThread()
            .setUncaughtExceptionHandler(Thread.UncaughtExceptionHandler { thread: Thread?, ex: Throwable? ->
                Tuils.toFile(ex)
                defaultExceptionHandler!!.uncaughtException(thread, ex)
            })

        RegexManager(this@LauncherActivity)
        TimeManager(this)

        val filter = IntentFilter()
        filter.addAction(PrivateIOReceiver.ACTION_INPUT)
        filter.addAction(PrivateIOReceiver.ACTION_OUTPUT)
        filter.addAction(PrivateIOReceiver.ACTION_REPLY)

        privateIOReceiver = PrivateIOReceiver(this, out, `in`)
        LocalBroadcastManager.getInstance(getApplicationContext())
            .registerReceiver(privateIOReceiver!!, filter)

        val filter1 = IntentFilter()
        filter1.addAction(PublicIOReceiver.ACTION_CMD)
        filter1.addAction(PublicIOReceiver.ACTION_OUTPUT)

        publicIOReceiver = PublicIOReceiver()
        ContextCompat.registerReceiver(
            getApplicationContext(),
            publicIOReceiver,
            filter1,
            BuildConfig.APPLICATION_ID + ".permission.RECEIVE_CMD",
            null,
            ContextCompat.RECEIVER_EXPORTED
        )

        backButtonEnabled = XMLPrefsManager.getBoolean(Behavior.back_button_enabled)

        val showNotification = XMLPrefsManager.getBoolean(Behavior.tui_notification)
        val keeperIntent = Intent(this, KeeperService::class.java)
        if (showNotification) {
            var homePath: String?
            try {
                val homeDir = XMLPrefsManager.get(File::class.java, Behavior.home_path)
                homePath = if (homeDir != null) homeDir.getAbsolutePath() else Tuils.getFolder()
                    .getAbsolutePath()
            } catch (e: Exception) {
                homePath = Tuils.getFolder().getAbsolutePath()
            }
            keeperIntent.putExtra(KeeperService.PATH_KEY, homePath)
            startKeeperServiceSafely(keeperIntent)
        } else {
            try {
                stopService(keeperIntent)
            } catch (e: Exception) {
            }
        }

        applyFullscreen(this)

        try {
            NotificationManager.create(this)
        } catch (e: Exception) {
            Tuils.toFile(e)
        }

        val notifications = showTerminal()
                || printToOutput()
                || ModuleManager.NOTIFICATIONS == ModuleManager.getActiveModule(this)
        if (notifications) {
            try {
                val notificationComponent = ComponentName(this, NotificationService::class.java)
                val pm = getPackageManager()
                pm.setComponentEnabledSetting(
                    notificationComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                if (!Tuils.hasNotificationAccess(this)) {
                    val i = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    if (i.resolveActivity(getPackageManager()) == null) {
                        Toast.makeText(this, R.string.no_notification_access, Toast.LENGTH_LONG)
                            .show()
                    } else {
                        startActivity(i)
                    }
                }

                NotificationService.requestListenerRebind(this)
            } catch (er: NoClassDefFoundError) {
                val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
                intent.putExtra(
                    PrivateIOReceiver.TEXT,
                    getString(R.string.output_notification_error) + Tuils.SPACE + er.toString()
                )
            } catch (er: RuntimeException) {
                Tuils.toFile(er)
            }
        }

        LongClickableSpan.longPressVibrateDuration =
            XMLPrefsManager.getInt(Behavior.long_click_vibration_duration)

        openKeyboardOnStart = XMLPrefsManager.getBoolean(Behavior.auto_show_keyboard)
        if (!openKeyboardOnStart) {
            this.getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        val mainView = FrameLayout(this)
        mainView.id = R.id.mainview
        mainView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutInflater.inflate(R.layout.base_view, mainView, true)
        setContentView(mainView)

        if (XMLPrefsManager.getBoolean(Ui.show_restart_message)) {
            val s = getIntent().getCharSequenceExtra(Reloadable.MESSAGE)
            if (s != null) out.onOutput(
                Tuils.span(
                    s,
                    XMLPrefsManager.getColor(Theme.restart_message_text_color)
                )
            )
        }

        categories = HashSet<ReloadMessageCategory?>()

        main = MainManager(this)

        val rootView = findViewById<View?>(R.id.mainview) as ViewGroup

        applySystemBarIconAppearance()

        this@LauncherActivity.uiManager =
            UIManager(this, rootView, main!!.mainPack, canApplyTheme, main!!.executer())
        uiManager!!.scheduleTypefaceRefreshes()
        installWindowInsetsHandler(rootView)

        main!!.setRedirectionListener(uiManager!!.buildRedirectionListener())
        uiManager!!.pack = main!!.mainPack

        `in`.`in`(Tuils.EMPTYSTRING)
        uiManager!!.activateTerminalInput(openKeyboardOnStart)
        restoreGuideSessionAfterReload()

        System.gc()
    }

    private fun startKeeperServiceSafely(keeperIntent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, keeperIntent)
            } else {
                startService(keeperIntent)
            }
        } catch (e: RuntimeException) {
            Tuils.toFile(e)
        }
    }

    private fun shouldRequestAllFilesAccess(): Boolean {
        return !this.isPlayStoreBuild && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private fun shouldRequestLegacyExternalStoragePermissions(): Boolean {
        return !this.isPlayStoreBuild
    }

    private val isPlayStoreBuild: Boolean
        get() = "playstore".equals(BuildConfig.FLAVOR, ignoreCase = true)

    private fun applySystemBarIconAppearance() {
        val lightIcons = XMLPrefsManager.getBoolean(Ui.statusbar_light_icons)
        WindowCompat.getInsetsController(window, window.decorView).run {
            isAppearanceLightStatusBars = !lightIcons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                isAppearanceLightNavigationBars = !lightIcons
            }
        }
    }

    private fun installWindowInsetsHandler(mainView: View) {
        val originalLeft = mainView.getPaddingLeft()
        val originalTop = mainView.getPaddingTop()
        val originalRight = mainView.getPaddingRight()
        val originalBottom = mainView.getPaddingBottom()

        ViewCompat.setOnApplyWindowInsetsListener(
            mainView,
            OnApplyWindowInsetsListener { view: View?, insets: WindowInsetsCompat? ->
                val safeInsets = insets!!.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val imeVisible = insets!!.isVisible(WindowInsetsCompat.Type.ime())
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val systemBottom = safeInsets.bottom
                val keyboardOffset = max(0, imeBottom - systemBottom)

                if (this@LauncherActivity.uiManager != null) {
                    uiManager!!.applyWindowInsets(
                        safeInsets.left,
                        safeInsets.top,
                        safeInsets.right,
                        safeInsets.bottom,
                        keyboardOffset,
                        imeVisible
                    )
                } else {
                    view!!.setPadding(
                        originalLeft + safeInsets.left,
                        originalTop + safeInsets.top,
                        originalRight + safeInsets.right,
                        originalBottom + safeInsets.bottom + keyboardOffset
                    )
                }
                insets
            })
        ViewCompat.requestApplyInsets(mainView)
    }

    override fun onResume() {
        super.onResume()
        applyFullscreen(this)
        if (this@LauncherActivity.uiManager != null) {
            uiManager!!.resume()
            uiManager!!.activateTerminalInput(openKeyboardOnStart)
            val guideMessage = GuideManager.consumePendingResumeMessage(this)
            if (!guideMessage.isNullOrEmpty()) {
                uiManager!!.setOutput(guideMessage, TerminalManager.CATEGORY_OUTPUT)
                uiManager!!.refreshSuggestionsSoon()
                uiManager!!.scrollTerminalToEndSoon()
            }
        }
    }

    fun applyOrientationPreference() {
        if (this@LauncherActivity.uiManager != null) {
            uiManager!!.refreshResponsiveLandscapeLayout()
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onRestart() {
        super.onRestart()
    }

    override fun onPause() {
        if (this@LauncherActivity.uiManager != null) {
            uiManager!!.pause()
        }
        super.onPause()
    }

    private var disposed = false

    private fun dispose() {
        if (disposed) return
        disposed = true

        if (main != null) {
            main!!.dispose()
            main!!.destroy()
        }
        if (this@LauncherActivity.uiManager != null) uiManager!!.dispose()
        if (privateIOReceiver != null) LocalBroadcastManager.getInstance(getApplicationContext())
            .unregisterReceiver(privateIOReceiver!!)
        if (publicIOReceiver != null) try {
            unregisterReceiver(publicIOReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        dispose()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (this@LauncherActivity.uiManager != null) {
            if (uiManager!!.consumeBackPressed()) {
                return
            }
            uiManager!!.onBackPressed()
        } else if (main == null) {
            super.onBackPressed()
        }
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && this@LauncherActivity.uiManager != null && uiManager!!.consumeBackPressed()) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    @SuppressLint("GestureBackNavigation")
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun reload() {
        XMLPrefsManager.dispose()
        invalidate()
        Tuils.cancelFont()
        val intent = Intent(this, LauncherActivity::class.java)
        val message = getIntent().getCharSequenceExtra(Reloadable.MESSAGE)
        val pending = pendingReloadMessages.toString()
        val reloadMessage = when {
            message != null && pending.isNotEmpty() -> message.toString() + Tuils.NEWLINE + pending
            message != null -> message.toString()
            pending.isNotEmpty() -> pending
            else -> null
        }
        if (reloadMessage != null) {
            intent.putExtra(Reloadable.MESSAGE, reloadMessage)
        }
        finish()
        startActivity(intent)
    }

    override fun addMessage(title: String?, message: String?) {
        val formatted = title + ": " + message
        if (pendingReloadMessages.isNotEmpty()) {
            pendingReloadMessages.append(Tuils.NEWLINE)
        }
        pendingReloadMessages.append(formatted)
        if (this@LauncherActivity.uiManager != null) {
            uiManager!!.setOutput(formatted, TerminalManager.CATEGORY_OUTPUT)
        }
    }

    private fun restoreGuideSessionAfterReload() {
        if (!GuideManager.isActive(this) || this@LauncherActivity.uiManager == null) {
            return
        }
        uiManager!!.setOutput(GuideManager.status(this), TerminalManager.CATEGORY_OUTPUT)
        uiManager!!.refreshSuggestionsSoon()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFullscreen(this)
        }
        if (hasFocus && this@LauncherActivity.uiManager != null) {
            uiManager!!.activateTerminalInput(openKeyboardOnStart)
            uiManager!!.scheduleTypefaceRefreshes()
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return super.onContextItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (uiManager?.handleAndroidWidgetActivityResult(requestCode, resultCode, data) == true) {
            return
        }

        if (requestCode == TUIXT_REQUEST) {
            if (resultCode == TuixtActivity.SAVE_PRESSED) {
                reload()
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    reload()
                } else {
                    Toast.makeText(
                        this,
                        "Storage permission is required for Re:T-UI to function.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STARTING_PERMISSION) {
            canApplyTheme = true
            finishOnCreate()
        } else if (requestCode == COMMAND_REQUEST_PERMISSION || requestCode == COMMAND_SUGGESTION_REQUEST_PERMISSION || requestCode == LOCATION_REQUEST_PERMISSION) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                reload()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(Reloadable.MESSAGE)) {
            reload()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (this@LauncherActivity.uiManager != null) {
            uiManager!!.onConfigurationChanged(newConfig)
        }
    }

    enum class ReloadMessageCategory {
        THEME,
        BEHAVIOR,
        UI,
        NOTIFICATIONS,
        APPS,
        RSS
    }

    companion object {
        @JvmField
        var instance: LauncherActivity? = null

        const val COMMAND_REQUEST_PERMISSION: Int = 10
        const val STARTING_PERMISSION: Int = 11
        const val COMMAND_SUGGESTION_REQUEST_PERMISSION: Int = 12
        const val LOCATION_REQUEST_PERMISSION: Int = 13

        const val TUIXT_REQUEST: Int = 110
        const val MANAGE_STORAGE_REQUEST: Int = 100
    }
}
