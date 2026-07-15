package ohi.andre.consolelauncher.commands.main

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.io.File
import java.lang.reflect.Method
import okhttp3.OkHttpClient
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandsPreferences
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HistoryManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.WebhookManager
import ohi.andre.consolelauncher.managers.flashlight.TorchManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.podcast.PodcastManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Redirectator
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder

class MainPack(
    context: Context,
    commandGroup: CommandGroup,
    @JvmField var aliasManager: AliasManager,
    @JvmField var appsManager: AppsManager,
    @JvmField var player: MusicManager2?,
    @JvmField var contacts: ContactManager,
    @JvmField var redirectator: Redirectator?,
    @JvmField var rssManager: RssManager?,
    @JvmField var podcastManager: PodcastManager,
    @JvmField var client: OkHttpClient,
    @JvmField var webhookManager: WebhookManager,
    @JvmField var historyManager: HistoryManager
) : ExecutePack(commandGroup) {
    @JvmField var currentDirectory: File = resolveHomeDirectory(XMLPrefsManager.get(File::class.java, Behavior.home_path))
    @JvmField var res: Resources = context.resources
    @JvmField var wifi: WifiManager? = null
    @JvmField var setMobileDataEnabledMethod: Method? = null
    @JvmField var connectivityMgr: ConnectivityManager? = null
    @JvmField var connectMgr: Any? = null
    @JvmField var cmdPrefs: CommandsPreferences = CommandsPreferences()
    @JvmField var lastCommand: String? = null
    @JvmField var shellHolder: ShellHolder? = null
    @JvmField var commandColor: Int = TerminalManager.NO_COLOR

    init {
        this.context = context
    }

    private fun resolveHomeDirectory(preferred: File?): File {
        val fallback = Tuils.getFolder()
        if (preferred == null) {
            return fallback
        }
        val path = preferred.absolutePath
        val oldColonFolder = path.contains(File.separator + "Re:T-UI") ||
            path.endsWith(File.separator + "Re:T-UI")
        if (oldColonFolder || !preferred.exists()) {
            return fallback
        }
        return preferred
    }

    fun dispose() {
        val mgr = TorchManager.getInstance()
        if (mgr.isOn()) {
            mgr.turnOff()
        }
    }

    fun destroy() {
        player?.destroy()
        podcastManager.destroy()
        appsManager.onDestroy()
        rssManager?.dispose()
        contacts.destroy(context)
    }

    override fun clear() {
        super.clear()
        commandColor = TerminalManager.NO_COLOR
    }
}
