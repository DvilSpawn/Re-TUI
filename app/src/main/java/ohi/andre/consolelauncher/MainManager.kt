package ohi.andre.consolelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Parcelable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandTuils.parse
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.raw.location
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.AppsManager.LaunchInfo
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.HistoryManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.ThemeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.WebhookManager
import ohi.andre.consolelauncher.managers.modules.ModulePromptManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.onboarding.GuideManager
import ohi.andre.consolelauncher.managers.settings.MusicSettings.enabled
import ohi.andre.consolelauncher.managers.termux.TermuxAppManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.Redirectator
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.Interactive
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.OnCommandResultListener
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import ohi.andre.consolelauncher.commands.Command
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.settings.MusicSettings

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
class MainManager(private val mContext: LauncherActivity) {
    private var redirect: RedirectCommand? = null
    private val redirectator: Redirectator = object : Redirectator {
        override fun prepareRedirection(cmd: RedirectCommand) {
            redirect = cmd

            if (redirectionListener != null) {
                redirectionListener!!.onRedirectionRequest(cmd)
            }
        }

        override fun cleanup() {
            val currentRedirect = redirect
            if (currentRedirect != null) {
                currentRedirect.beforeObjects.clear()
                currentRedirect.afterObjects.clear()

                if (redirectionListener != null) {
                    redirectionListener!!.onRedirectionEnd(currentRedirect)
                }

                redirect = null
            }
        }
    }
    private var redirectionListener: OnRedirectionListener? = null
    fun setRedirectionListener(redirectionListener: OnRedirectionListener?) {
        this.redirectionListener = redirectionListener
    }

    private val COMMANDS_PKG = "ohi.andre.consolelauncher.commands.main.raw"

    private val triggers = arrayOf<CmdTrigger>(
        ModulePromptTrigger(),
        GroupTrigger(),
        AliasTrigger(),
        TuiCommandTrigger(),
        TermuxAppTrigger(),
        AppTrigger(),
        ShellCommandTrigger()
    )
    val mainPack: MainPack

    private val showAliasValue: Boolean
    private val showAppHistory: Boolean
    private val aliasContentColor: Int

    private val multipleCmdSeparator: String

    private val aliasManager: AliasManager
    private val rssManager: RssManager?
    private val appsManager: AppsManager
    private var contactManager: ContactManager? = null
    private val musicManager2: MusicManager2?
    private val themeManager: ThemeManager
    private val htmlExtractManager: HTMLExtractManager
    private val webhookManager: WebhookManager
    private val historyManager: HistoryManager?

    private val receiver: BroadcastReceiver

    private val keeperServiceRunning: Boolean

    private fun updateServices(cmd: String?, wasMusicService: Boolean) {
        if (keeperServiceRunning) {
            val i = Intent(mContext, KeeperService::class.java)
            i.putExtra(KeeperService.CMD_KEY, cmd)
            i.putExtra(KeeperService.PATH_KEY, mainPack.currentDirectory.getAbsolutePath())
            mContext.startService(i)
        }

        if (wasMusicService) {
            val i = Intent(mContext, MusicService::class.java)
            mContext.startService(i)
        }
    }

    fun onCommand(input: String, launchInfo: LaunchInfo?, wasMusicService: Boolean) {
        if (launchInfo == null) {
            onCommand(input, null as String?, wasMusicService)
            return
        }

        updateServices(input, wasMusicService)

        if (launchInfo.unspacedLowercaseLabel == Tuils.removeSpaces(input.lowercase(Locale.getDefault()))) {
            performLaunch(mainPack, launchInfo, input)
        } else {
            onCommand(input, null as String?, wasMusicService)
        }
    }

    var colorExtractor: Pattern =
        Pattern.compile("(#[^(]{6})\\[([^\\)]*)\\]", Pattern.CASE_INSENSITIVE)

    //    command manager
    fun onCommand(input: String, alias: String?, wasMusicService: Boolean) {
        var input = input
        input = Tuils.removeUnncesarySpaces(input) ?: Tuils.EMPTYSTRING

        if (alias == null) updateServices(input, wasMusicService)

        if (redirect != null) {
            val currentRedirect = redirect!!
            if (!currentRedirect.isWaitingPermission()) {
                currentRedirect.afterObjects.add(input)
            }
            val output = currentRedirect.onRedirect(mainPack)
            Tuils.sendOutput(mContext, output)
            if (redirect == null) {
                publishGuideProgress(currentRedirect.consumeCompletedCommand())
            }

            return
        }

        if (alias != null && showAliasValue) {
            Tuils.sendOutput(aliasContentColor, mContext, aliasManager.formatLabel(alias, input))
        }

        val cmds: Array<String?>?
        if (multipleCmdSeparator.length > 0) {
            cmds = input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
        } else {
            cmds = arrayOf<String?>(input)
        }

        val colors = IntArray(cmds.size)
        for (c in colors.indices) {
            val m = colorExtractor.matcher(cmds[c])
            if (m.matches()) {
                try {
                    colors[c] = Color.parseColor(m.group(1))
                    cmds[c] = m.group(2)
                } catch (e: Exception) {
                    colors[c] = TerminalManager.NO_COLOR
                }
            } else colors[c] = TerminalManager.NO_COLOR
        }

        for (c in cmds.indices) {
            mainPack.clear()
            mainPack.commandColor = colors[c]

            for (trigger in triggers) {
                val r: Boolean
                try {
                    r = trigger.trigger(mainPack, cmds[c])
                } catch (e: Exception) {
                    Tuils.sendOutput(mContext, Tuils.getStackTrace(e))
                    break
                }
                if (r) {
                    if (trigger !is TuiCommandTrigger && redirect == null) {
                        publishGuideProgress(cmds[c])
                    }
                    break
                }
            }
        }
    }

    private fun publishGuideProgress(command: String?) {
        val guideOutput = GuideManager.observeCommand(mContext, command)
        if (!guideOutput.isNullOrEmpty()) {
            Tuils.sendOutput(mContext, guideOutput)
            mContext.uiManager?.refreshSuggestionsSoon()
            mContext.uiManager?.scrollTerminalToEndSoon()
        }
    }

    fun onLongBack() {
        Tuils.sendInput(mContext, Tuils.EMPTYSTRING)
    }

    fun sendPermissionNotGrantedWarning() {
        redirectator.cleanup()
    }

    fun dispose() {
        mainPack.dispose()
    }

    fun destroy() {
        mainPack.destroy()
        TuiLocationManager.disposeStatic()

        themeManager.dispose()
        htmlExtractManager.dispose(mContext)
        aliasManager.dispose()
        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .unregisterReceiver(receiver)

        object : StoppableThread() {
            override fun run() {
                super.run()

                try {
                    interactive.kill()
                    interactive.close()
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                }
            }
        }.start()
    }

    fun executer(): CommandExecuter {
        return CommandExecuter { input: String?, obj: Any? ->
            val li = if (obj is LaunchInfo) obj else null
            onCommand(input!!, li, false)
        }
    }

    //
    var appFormat: String? = null
    var outputColor: Int = 0

    var pa: Pattern = Pattern.compile("%a", Pattern.CASE_INSENSITIVE)
    var pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE)
    var pl: Pattern = Pattern.compile("%l")
    var pL: Pattern = Pattern.compile("%L")

    init {
        cleanupLegacyBusyBox()

        keeperServiceRunning = XMLPrefsManager.getBoolean(Behavior.tui_notification)

        showAliasValue = XMLPrefsManager.getBoolean(Behavior.show_alias_content)
        showAppHistory = XMLPrefsManager.getBoolean(Behavior.show_launch_history)
        aliasContentColor = XMLPrefsManager.getColor(Theme.alias_content_text_color)

        multipleCmdSeparator = XMLPrefsManager.get(Behavior.multiple_cmd_separator)

        val group = CommandGroup(mContext, COMMANDS_PKG)

        try {
            contactManager = ContactManager(mContext)
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }

        appsManager = AppsManager(mContext)
        aliasManager = AliasManager(mContext)

        val client = OkHttpClient.Builder()
            .cache(Cache(mContext.getCacheDir(), (10 * 1024 * 1024).toLong()))
            .build()

        //        new Thread() {
//            @Override
//            public void run() {
//                super.run();
//
//                int lat = -90, lon = 0;
//
//                for(int j = 0; j < 120; j++) {
//                    Tuils.log("----------------" + j + "----------------");
//
//                    try {
//                        Request.Builder builder = new Request.Builder()
//                                .url("http://api.openweathermap.org/data/2.5/weather?lat=" + lat++ + "&lon=" + lon++ + "&appid=1f798f99228596c20ccfda51b9771a86&units=metric")
//                                .cacheControl(CacheControl.FORCE_NETWORK)
//                                .get();
//
//                        Response response = client.newCall(builder.build()).execute();
//
//                        Tuils.log("code", response.code());
//                        if (!response.isSuccessful()) {
//                            Tuils.log("not succesfull");
//                            return;
//                        }
//
//                        InputStream inputStream = response.body().byteStream();
//                        String json = Tuils.inputStreamToString(inputStream);
//                        Tuils.log(json);
//                    } catch (Exception e) {
//                        Tuils.log(e);
//                    }
//                }
//            }
//        }.start();
        rssManager = RssManager(mContext, client)
        themeManager = ThemeManager(client, mContext, mContext)
        musicManager2 = if (enabled()) MusicManager2(mContext) else null
        htmlExtractManager = HTMLExtractManager(mContext, client)
        webhookManager = WebhookManager(mContext)
        historyManager = HistoryManager()

        mainPack = MainPack(
            mContext,
            group,
            aliasManager,
            appsManager,
            musicManager2,
            contactManager!!,
            redirectator,
            rssManager,
            client,
            webhookManager,
            historyManager
        )

        val shellHolder = ShellHolder(mContext)
        interactive = shellHolder.build()
        mainPack.shellHolder = shellHolder

        val filter = IntentFilter()
        filter.addAction(ACTION_EXEC)
        filter.addAction(location.ACTION_LOCATION_CMD_GOT)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getAction()
                if (action == ACTION_EXEC) {
                    var cmd = intent.getStringExtra(CMD)
                    if (cmd == null) cmd = intent.getStringExtra(PrivateIOReceiver.TEXT)

                    if (cmd == null) {
                        return
                    }

                    val cmdCount = intent.getIntExtra(CMD_COUNT, -1)
                    if (cmdCount < commandCount) return
                    commandCount++

                    val aliasName = intent.getStringExtra(ALIAS_NAME)
                    val needWriteInput = intent.getBooleanExtra(NEED_WRITE_INPUT, false)
                    val p = intent.getParcelableExtra<Parcelable?>(PARCELABLE)

                    if (needWriteInput) {
                        val i = Intent(PrivateIOReceiver.ACTION_INPUT)
                        i.putExtra(PrivateIOReceiver.TEXT, cmd)
                        LocalBroadcastManager.getInstance(context.getApplicationContext())
                            .sendBroadcast(i)
                    }

                    if (p != null && p is LaunchInfo) {
                        onCommand(cmd, p, intent.getBooleanExtra(MUSIC_SERVICE, false))
                    } else {
                        onCommand(cmd, aliasName, intent.getBooleanExtra(MUSIC_SERVICE, false))
                    }
                } else if (action == location.ACTION_LOCATION_CMD_GOT) {
                    Tuils.sendOutput(
                        context,
                        "Lat: " + intent.getDoubleExtra(
                            TuiLocationManager.LATITUDE,
                            0.0
                        ) + "; Long: " + intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0.0)
                    )
                    TuiLocationManager.instance(context)!!.rm(location.ACTION_LOCATION_CMD_GOT)
                }
            }
        }

        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .registerReceiver(receiver, filter)
    }

    fun performLaunch(mainPack: MainPack, i: LaunchInfo, input: String?): Boolean {
        val intent = appsManager.getIntent(i)
        if (intent == null) {
            return false
        }

        if (showAppHistory) {
            appFormat = XMLPrefsManager.get(Behavior.app_launch_format)
            outputColor = XMLPrefsManager.getColor(Theme.output_text_color)

            var a = appFormat!!
            var className = ""
            var packageName = ""
            if (intent.getComponent() != null) {
                className = intent.getComponent()!!.getClassName()
                packageName = intent.getComponent()!!.getPackageName()
            }

            a = pa.matcher(a).replaceAll(Matcher.quoteReplacement(className))
            a = pp.matcher(a).replaceAll(Matcher.quoteReplacement(packageName))

            val publicLabel = i.publicLabel
            a = pl.matcher(a).replaceAll(
                Matcher.quoteReplacement(
                    if (publicLabel != null) publicLabel.lowercase(
                        Locale.getDefault()
                    ) else ""
                )
            )
            a = pL.matcher(a)
                .replaceAll(Matcher.quoteReplacement(publicLabel ?: ""))
            a = Tuils.patternNewline.matcher(a).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))

            val text = SpannableString(a)
            text.setSpan(
                ForegroundColorSpan(outputColor),
                0,
                text.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            Tuils.sendOutput(mainPack, text, TerminalManager.CATEGORY_NO_COLOR)
        }

        return appsManager.launch(mainPack.context, i)
    }

    //
    fun interface CmdTrigger {
        @Throws(Exception::class)
        fun trigger(info: MainPack?, input: String?): Boolean
    }

    private inner class ModulePromptTrigger : CmdTrigger {
        override fun trigger(info: MainPack?, input: String?): Boolean {
            if (info == null || info.context == null || !ModulePromptManager.isActive(info.context)) {
                return false
            }
            return ModulePromptManager.handleInput(info.context, input)
        }
    }

    private inner class AliasTrigger : CmdTrigger {
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val alias = aliasManager.getAlias(input ?: return false, true)

            var aliasValue: String = alias!![0] ?: return false

            val aliasName: String? = alias[1]
            val residual: String? = alias[2]

            aliasValue = aliasManager.format(aliasValue, residual ?: Tuils.EMPTYSTRING) ?: return false

            onCommand(aliasValue, aliasName, false)

            return true
        }
    }

    private inner class GroupTrigger : CmdTrigger {
        @Throws(Exception::class)
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val info = info ?: return false
            var input = input
            val index = input!!.indexOf(Tuils.SPACE)
            val name: String?

            if (index != -1) {
                name = input.substring(0, index)
                input = input.substring(index + 1)
            } else {
                name = input
                input = null
            }

            val appGroups: MutableList<out Group>? = info.appsManager.groups
            if (appGroups != null) {
                for (g in appGroups) {
                    if (name == g.name()) {
                        if (input == null) {
                            Tuils.sendOutput(
                                mContext,
                                AppsManager.AppUtils.printApps(
                                    AppsManager.AppUtils.labelList(
                                        g.members() as MutableList<LaunchInfo>,
                                        false
                                    )
                                )
                            )
                            return true
                        } else {
                            return g.use(mainPack, input)
                        }
                    }
                }
            }

            return false
        }
    }

    private inner class ShellCommandTrigger : CmdTrigger {
        @Throws(Exception::class)
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val info = info ?: return false
            val input = input ?: return false
            val trimmed = input.trim { it <= ' ' }
            val parts = Tuils.splitArgs(trimmed)
            if (!parts.isEmpty() && info.commandGroup.getCommandByName(parts.get(0)) != null) {
                Tuils.sendOutput(
                    mContext,
                    "Command did not execute: " + parts.get(0),
                    TerminalManager.CATEGORY_OUTPUT
                )
                return true
            }

            if (XMLPrefsManager.getBoolean(Behavior.shell_requires_prefix)) {
                Tuils.sendOutput(
                    mContext,
                    mContext.getString(R.string.output_commandnotfound),
                    TerminalManager.CATEGORY_OUTPUT
                )
                return true
            }

            val cmd: String? =
                trimmed.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]

            object : StoppableThread() {
                override fun run() {
                    runShellCommand(info, trimmed, false)
                }
            }.start()

            return true
        }
    }

    private fun cleanupLegacyBusyBox() {
        val legacyBinDir = File(mContext.getFilesDir(), "bin")
        val legacyBusyBox = File(legacyBinDir, "busybox.so")
        deleteLegacyBusyBoxFile(legacyBusyBox)

        val files = legacyBinDir.list()
        if (files != null && files.size == 0 && !legacyBinDir.delete()) {
            Tuils.log("Unable to remove empty legacy binary directory: " + legacyBinDir.getAbsolutePath())
        }

        mContext.getSharedPreferences("busybox_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        val prefsDir = File(mContext.getApplicationInfo().dataDir, "shared_prefs")
        deleteLegacyBusyBoxFile(File(prefsDir, "busybox_prefs.xml"))
        deleteLegacyBusyBoxFile(File(prefsDir, "busybox_prefs.xml.bak"))
    }

    private fun deleteLegacyBusyBoxFile(file: File) {
        if (file.exists() && !file.delete()) {
            Tuils.log("Unable to remove legacy BusyBox file: " + file.getAbsolutePath())
        }
    }

    private inner class AppTrigger : CmdTrigger {
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val info = info ?: return false
            if (input == null || input.isEmpty()) return false


            // Check if input exactly matches an app label or component
            val i = appsManager.findLaunchInfoWithLabel(input, AppsManager.SHOWN_APPS)
            if (i != null) {
                // If it matches exactly, and we have arguments, maybe it's NOT an app launch but a command with same name
                // But TuiCommandTrigger should have caught it first.
                // However, some apps have spaces in names. findLaunchInfoWithLabel handles that.
                return performLaunch(info, i, input)
            }
            return false
        }
    }

    private inner class TermuxAppTrigger : CmdTrigger {
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val info = info ?: return false
            val raw = input?.trim { it <= ' ' } ?: return false
            val id = TermuxAppManager.normalizeId(input)
            if (id.length == 0 || raw.contains(Tuils.SPACE) || !raw.equals(id, ignoreCase = true)) {
                return false
            }
            val app = TermuxAppManager.resolve(info.context, id) ?: return false
            val intent = Intent(UIManager.ACTION_TERMUX_CONSOLE)
            intent.putExtra(UIManager.EXTRA_TERMUX_COMMAND, "app " + app.id)
            LocalBroadcastManager.getInstance(info.context.applicationContext).sendBroadcast(intent)
            return true
        }
    }

    private inner class TuiCommandTrigger : CmdTrigger {
        @Throws(Exception::class)
        override fun trigger(info: MainPack?, input: String?): Boolean {
            val info = info ?: return false
            if (input == null || input.isEmpty()) return false

            val trimmed = input.trim { it <= ' ' }
            val command = parse(trimmed, info)
            if (command == null) {
                val parts = Tuils.splitArgs(trimmed)
                if (!parts.isEmpty() && info.commandGroup.getCommandByName(parts.get(0)) != null) {
                    Tuils.sendOutput(
                        mContext,
                        "Invalid command input: " + parts.get(0),
                        TerminalManager.CATEGORY_OUTPUT
                    )
                    return true
                }
                return false
            }

            // If it is a ParamCommand (like webhook) or has arguments, we definitely want to handle it here.
            // If it's just a command name (no args), it might conflict with an app.
            // But we already prioritized TuiCommandTrigger in the triggers array.
            mainPack.lastCommand = input

            object : StoppableThread() {
                override fun run() {
                    super.run()

                    try {
                        val output = command.exec(info)
                        if (output != null) {
                            Tuils.sendOutput(info, output, TerminalManager.CATEGORY_OUTPUT)
                        }
                        if (redirect == null) {
                            publishGuideProgress(input)
                        }
                    } catch (e: Exception) {
                        Tuils.sendOutput(mContext, Tuils.getStackTrace(e))
                        Tuils.log(e)
                    }
                }
            }.start()

            return true
        }
    }

    interface Group {
        fun members(): MutableList<out Any>
        fun use(mainPack: MainPack, input: String): Boolean
        fun name(): String
    }

    companion object {
        var ACTION_EXEC: String = BuildConfig.APPLICATION_ID + ".main_exec"
        var CMD: String = "cmd"
        var NEED_WRITE_INPUT: String = "writeInput"
        var ALIAS_NAME: String = "aliasName"
        var PARCELABLE: String = "parcelable"
        var CMD_COUNT: String = "cmdCount"
        var MUSIC_SERVICE: String = "musicService"

        lateinit var interactive: Interactive

        var commandCount: Int = 0

        private const val SHELL_CD_CODE = 20
        private const val SHELL_PWD_CODE = 21

        fun runShellCommand(info: MainPack, input: String?, echoCommand: Boolean) {
            if (input == null || input.trim { it <= ' ' }.length == 0) {
                return
            }

            val command = input.trim { it <= ' ' }
            val result: OnCommandResultListener = object : OnCommandResultListener {
                override fun onCommandResult(
                    commandCode: Int,
                    exitCode: Int,
                    output: MutableList<String>?
                ) {
                    if (commandCode == SHELL_CD_CODE) {
                        interactive.addCommand("pwd", SHELL_PWD_CODE, this)
                    } else if (commandCode == SHELL_PWD_CODE && output != null && output.size == 1) {
                        val f = File(output.get(0))
                        if (f.exists()) {
                            info.currentDirectory = f
                            LocalBroadcastManager.getInstance(info.context.getApplicationContext())
                                .sendBroadcast(
                                    Intent(
                                        UIManager.ACTION_UPDATE_HINT
                                    )
                                )
                        }
                    }
                }
            }

            if (echoCommand) {
                Tuils.sendOutput(info.context, "shell: " + command, TerminalManager.CATEGORY_OUTPUT)
            }

            if (command.equals("su", ignoreCase = true)) {
                if (Shell.SU.available()) {
                    LocalBroadcastManager.getInstance(info.context.getApplicationContext())
                        .sendBroadcast(
                            Intent(
                                UIManager.ACTION_ROOT
                            )
                        )
                }
                interactive.addCommand("su")
            } else if (command.equals(
                    "cd",
                    ignoreCase = true
                ) || command.lowercase(Locale.getDefault()).startsWith("cd ")
            ) {
                interactive.addCommand(command, SHELL_CD_CODE, result)
            } else {
                interactive.addCommand(command)
            }
        }
    }
}
