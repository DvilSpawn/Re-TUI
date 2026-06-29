package ohi.andre.consolelauncher.managers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import it.andreuzzi.comparestring2.StringableObject
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import android.content.pm.ActivityInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.ServiceInfo
import androidx.annotation.NonNull
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.Comparator
import java.util.Iterator
import java.util.LinkedHashMap
import java.util.Map
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.resetFile
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.set
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.writeTo

class AppsManager(context: Context) : XMLPrefsElement {
    private val NAME = "APPS"
    private val file: File

    private val SHOW_ATTRIBUTE = "show"
    private val APPS_ATTRIBUTE = "apps"
    private val BGCOLOR_ATTRIBUTE = "bgColor"
    private val FORECOLOR_ATTRIBUTE = "foreColor"
    private val context: Context

    private var appsHolder: AppsHolder? = null
    private var hiddenApps: MutableList<LaunchInfo>? = null

    private val PREFS = "apps"
    private val preferences: SharedPreferences
    private val editor: SharedPreferences.Editor

    private var prefsList: XMLPrefsList? = null

    var groups: MutableList<Group>

    private var pp: Pattern? = null
    private var pl: Pattern? = null
    private var appInstalledFormat: String?
    private val appUpdatedFormat: String?
    private var appUninstalledFormat: String?
    var appInstalledColor: Int = 0
    var appUninstalledColor: Int = 0

    private var lastInstalledPackage: String? = null
    private var lastInstallTime: Long = 0
    private var lastUpdatedPackage: String? = null
    private var lastUpdateTime: Long = 0
    private var lastUninstalledPackage: String? = null
    private var lastUninstallTime: Long = 0

    override fun delete(): Array<String?>? {
        return null
    }

    override fun write(save: XMLPrefsSave, value: String) {
        XMLPrefsManager.set(
            File(Tuils.getFolder(), PATH),
            save.label(),
            arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(value)
        )
        prefsList!!.add(save.label()!!, value)
    }

    override fun path(): String {
        return PATH
    }

    override fun getValues(): XMLPrefsList {
        return prefsList!!
    }

    private val appsBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.getAction()
            val data = intent.getData()!!.getSchemeSpecificPart()
            if (action == Intent.ACTION_PACKAGE_ADDED) {
                appInstalled(data, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
            } else {
                appUninstalled(data, intent.getBooleanExtra(Intent.EXTRA_REPLACING, false))
            }
        }
    }

    init {
        instance = this

        this.context = context

        appInstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_installed)) XMLPrefsManager.get(
                Behavior.app_installed_format
            ) else null
        appUpdatedFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_installed)) XMLPrefsManager.get(
                Behavior.app_updated_format
            ) else null
        appUninstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_uninstalled)) XMLPrefsManager.get(
                Behavior.app_uninstalled_format
            ) else null

        if (appInstalledFormat != null || appUninstalledFormat != null) {
            pp = Pattern.compile("%p", Pattern.CASE_INSENSITIVE)
            pl = Pattern.compile("%l", Pattern.CASE_INSENSITIVE)

            appInstalledColor = XMLPrefsManager.getColor(Theme.app_installed_text_color)
            appUninstalledColor = XMLPrefsManager.getColor(Theme.app_uninstalled_text_color)
        } else {
            pp = null
            pl = null
        }

        this.file = File(Tuils.getFolder(), PATH)

        this.preferences = context.getSharedPreferences(PREFS, 0)
        this.editor = preferences.edit()

        this.groups = ArrayList<Group>()

        initAppListener(context)

        object : StoppableThread() {
            override fun run() {
                super.run()

                fill()
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(
                    Intent(
                        UIManager.ACTION_UPDATE_SUGGESTIONS
                    )
                )
            }
        }.start()
    }

    private fun initAppListener(c: Context) {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilter.addDataScheme("package")

        ContextCompat.registerReceiver(
            c,
            appsBroadcast,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun fill() {
        val allApps = createAppMap(context!!.getPackageManager())
        hiddenApps = ArrayList<LaunchInfo>()

        groups.clear()

        try {
            prefsList = XMLPrefsList()

            if (file != null) {
                if (!file.exists()) {
                    XMLPrefsManager.resetFile(file, NAME)
                }

                val o: Array<Any?>?
                try {
                    o = XMLPrefsManager.buildDocument(file, NAME)
                    if (o == null) {
                        Tuils.sendXMLParseError(context, PATH)
                        return
                    }
                } catch (e: SAXParseException) {
                    Tuils.sendXMLParseError(context, PATH, e)
                    return
                } catch (e: Exception) {
                    Tuils.log(e)
                    return
                }

                val d = o[0] as Document
                val root = o[1] as Element

                val enums: MutableList<Apps> = ArrayList<Apps>(Arrays.asList<Apps>(*Apps.values()))
                val nodes = root.getElementsByTagName("*")

                for (count in 0..<nodes.getLength()) {
                    val node = nodes.item(count)

                    val nn = node.getNodeName()
                    val nodeIndex = Tuils.find(nn, enums as MutableList<*>)
                    if (nodeIndex != -1) {
//                        default_app...
                        if (nn.startsWith("d")) {
                            prefsList!!.add(
                                nn,
                                node.getAttributes().getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE)
                                    .getNodeValue()
                            )
                        } else {
                            prefsList!!.add(
                                nn,
                                XMLPrefsManager.getStringAttribute(
                                    node as Element,
                                    XMLPrefsManager.VALUE_ATTRIBUTE
                                )
                            )
                        }

                        for (en in enums.indices) {
                            if (enums.get(en).label() == nn) {
                                enums.removeAt(en)
                                break
                            }
                        }
                    } else {
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                            val e = node as Element

                            if (e.hasAttribute(APPS_ATTRIBUTE)) {
                                val name = e.getNodeName()
                                if (name.contains(Tuils.SPACE)) {
                                    Tuils.sendOutput(
                                        Color.RED,
                                        context,
                                        PATH + ": " + context.getString(R.string.output_groupspace) + ": " + name
                                    )
                                    continue
                                }

                                object : StoppableThread() {
                                    override fun run() {
                                        super.run()

                                        val g = Group(name)

                                        val apps = e.getAttribute(APPS_ATTRIBUTE)
                                        val split: Array<String> =
                                            apps.split(APPS_SEPARATOR.toRegex())
                                                .dropLastWhile { it.isEmpty() }.toTypedArray()

                                        val `as`: MutableList<LaunchInfo?> =
                                            ArrayList<LaunchInfo?>(allApps)

                                        External@ for (s in split) {
                                            for (c in `as`.indices) {
                                                if (`as`.get(c)?.`is`(s) == true) {
                                                    g.add(`as`.removeAt(c)!!, false)
                                                    continue@External
                                                }
                                            }
                                        }

                                        g.sort()

                                        if (e.hasAttribute(BGCOLOR_ATTRIBUTE)) {
                                            val c = e.getAttribute(BGCOLOR_ATTRIBUTE)
                                            if (c.length > 0) {
                                                try {
                                                    g.bgColor = Color.parseColor(c)
                                                } catch (e: Exception) {
                                                    Tuils.sendOutput(
                                                        Color.RED,
                                                        context,
                                                        PATH + ": " + context.getString(R.string.output_invalidcolor) + ": " + c
                                                    )
                                                }
                                            }
                                        }

                                        if (e.hasAttribute(FORECOLOR_ATTRIBUTE)) {
                                            val c = e.getAttribute(FORECOLOR_ATTRIBUTE)
                                            if (c.length > 0) {
                                                try {
                                                    g.foreColor =
                                                        Color.parseColor(c)
                                                } catch (e: Exception) {
                                                    Tuils.sendOutput(
                                                        Color.RED,
                                                        context,
                                                        PATH + ": " + context.getString(R.string.output_invalidcolor) + ": " + c
                                                    )
                                                }
                                            }
                                        }

                                        groups.add(g)
                                    }
                                }.start()
                            } else {
                                val shown =
                                    !e.hasAttribute(SHOW_ATTRIBUTE) || e.getAttribute(SHOW_ATTRIBUTE)
                                        .toBoolean()
                                if (!shown) {
                                    val identity = LaunchInfo.Companion.identityInfo(nn)
                                    if (identity == null) continue

                                    val removed: LaunchInfo? =
                                        AppUtils.findLaunchInfoWithIdentity(allApps, identity)
                                    if (removed != null) {
                                        allApps.remove(removed)
                                        hiddenApps!!.add(removed)
                                    }
                                }
                            }
                        }
                    }
                }

                if (enums.size > 0) {
                    for (s in enums) {
                        val value: String? = s.defaultValue()

                        val em = d.createElement(s.label())
                        em.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, value)
                        root.appendChild(em)

                        prefsList!!.add(s.label(), value!!)
                    }
                    XMLPrefsManager.writeTo(d, file)
                }
            } else {
                Tuils.sendOutput(Color.RED, context, R.string.tuinotfound_app)
            }

            for (entry in this.preferences.getAll().entries) {
                val value: Any? = entry.value
                if (value is Int) {
                    val identity = LaunchInfo.Companion.identityInfo(entry.key)
                    if (identity == null) continue

                    val info: LaunchInfo? = AppUtils.findLaunchInfoWithIdentity(allApps, identity)
                    if (info != null) info.launchedTimes = value
                }
            }
        } catch (e1: Exception) {
            Tuils.toFile(e1)
        }

        appsHolder = AppsHolder(allApps, prefsList!!)
        AppUtils.checkEquality(hiddenApps!!)

        Group.Companion.sorting = XMLPrefsManager.getInt(Apps.app_groups_sorting)
        for (g in groups) g.sort()
        Collections.sort<Group>(
            groups,
            Comparator { o1: Group, o2: Group ->
                Tuils.alphabeticCompare(
                    o1.name(),
                    o2.name()
                )
            })
    }

    private fun createAppMap(mgr: PackageManager): MutableList<LaunchInfo> {
        val deduped = LinkedHashMap<String?, LaunchInfo?>()
        var launcherApps: LauncherApps? = null
        val userManager = context!!.getSystemService(Context.USER_SERVICE) as UserManager?
        val currentProfile = Process.myUserHandle()
        val currentSerial = profileSerial(userManager, currentProfile)
        var canReadShortcuts = false

        try {
            launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps?
            canReadShortcuts =
                launcherApps != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && Tuils.isMyLauncherDefault(
                    context.getPackageManager()
                )

            var profiles = if (userManager != null) userManager.getUserProfiles() else null
            if (profiles == null || profiles.size == 0) {
                profiles = ArrayList<UserHandle>()
                profiles.add(currentProfile!!)
            }

            for (profile in profiles) {
                val activities = if (launcherApps != null)
                    launcherApps.getActivityList(null, profile)
                else
                    null
                if (activities == null) {
                    continue
                }

                val current = profile == currentProfile
                val serial = profileSerial(userManager, profile)
                for (activity in activities) {
                    val component = activity.getComponentName()
                    val label = if (activity.getLabel() != null) activity.getLabel()
                        .toString() else component.getClassName()
                    val li = LaunchInfo(
                        component.getPackageName(),
                        component.getClassName(),
                        label,
                        profile,
                        serial,
                        current
                    )
                    maybeLoadShortcuts(launcherApps, li, canReadShortcuts)
                    deduped.put(li.write(), li)
                }
            }
        } catch (e: Throwable) {
            Tuils.log(e)
        }

        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)

        val main: MutableList<ResolveInfo>?
        try {
            main = mgr.queryIntentActivities(i, 0)
        } catch (e: Exception) {
            return ArrayList<LaunchInfo>(deduped.values)
        }

        for (ri in main) {
            val li = LaunchInfo(
                ri.activityInfo.packageName,
                ri.activityInfo.name,
                ri.loadLabel(mgr).toString(),
                currentProfile,
                currentSerial,
                true
            )
            if (launcherApps != null) {
                maybeLoadShortcuts(launcherApps, li, canReadShortcuts)
            }
            deduped.put(li.write(), li)
        }

        Log.i("TUI-APPS", "Loaded " + deduped.size + " launchable activities")

        return ArrayList<LaunchInfo>(deduped.values)
    }

    private fun profileSerial(userManager: UserManager?, profile: UserHandle?): Long {
        if (userManager == null || profile == null) {
            return 0L
        }
        try {
            val serial = userManager.getSerialNumberForUser(profile)
            if (serial >= 0L) {
                return serial
            }
        } catch (e: Throwable) {
        }
        return if (profile == Process.myUserHandle()) 0L else Integer.toUnsignedLong(profile.hashCode())
    }

    private fun maybeLoadShortcuts(
        launcherApps: LauncherApps?,
        li: LaunchInfo,
        canReadShortcuts: Boolean
    ) {
        if (!canReadShortcuts || launcherApps == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return
        }

        try {
            val query = ShortcutQuery()
            query.setQueryFlags(
                (ShortcutQuery.FLAG_MATCH_MANIFEST
                        or ShortcutQuery.FLAG_MATCH_DYNAMIC
                        or ShortcutQuery.FLAG_MATCH_PINNED)
            )
            query.setPackage(li.componentName!!.getPackageName())
            li.setShortcuts(launcherApps.getShortcuts(query, li.userHandle!!))
        } catch (e: SecurityException) {
            Log.w("TUI-APPS", "Shortcut access denied for " + li.componentName!!.getPackageName())
        } catch (e: Throwable) {
            Tuils.log(e)
        }
    }

    private fun shouldSuppressPackageEvent(
        packageName: String,
        lastPackage: String?,
        lastTime: Long
    ): Boolean {
        return packageName == lastPackage && System.currentTimeMillis() - lastTime < 2000
    }

    private fun formatPackageEvent(
        format: String,
        packageName: String,
        manager: PackageManager,
        packageInfo: PackageInfo?,
        infos: MutableList<LaunchInfo?>?
    ): String {
        var cp = format

        cp = pp!!.matcher(cp).replaceAll(packageName)
        if (packageInfo != null) {
            val sequence = packageInfo.applicationInfo!!.loadLabel(manager)
            if (sequence != null) cp = pl!!.matcher(cp).replaceAll(sequence.toString())
        } else if (infos != null && infos.size > 0) {
            cp = pl!!.matcher(cp).replaceAll(infos.get(0)!!.publicLabel)
        } else {
            val index = packageName.lastIndexOf(Tuils.DOT)
            if (index == -1) cp = pl!!.matcher(cp).replaceAll(Tuils.EMPTYSTRING)
            else {
                cp = pl!!.matcher(cp).replaceAll(packageName.substring(index + 1))
            }
        }

        return Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)
    }

    private fun appInstalled(packageName: String, replacing: Boolean) {
        try {
            val manager = context!!.getPackageManager()

            val packageInfo = manager.getPackageInfo(packageName, 0)

            if (replacing) {
                if (appUpdatedFormat != null && !shouldSuppressPackageEvent(
                        packageName,
                        lastUpdatedPackage,
                        lastUpdateTime
                    )
                ) {
                    Tuils.sendOutput(
                        appInstalledColor,
                        context,
                        formatPackageEvent(
                            appUpdatedFormat,
                            packageName,
                            manager,
                            packageInfo,
                            null
                        )
                    )
                    lastUpdatedPackage = packageName
                    lastUpdateTime = System.currentTimeMillis()
                }
            } else if (appInstalledFormat != null && !shouldSuppressPackageEvent(
                    packageName,
                    lastInstalledPackage,
                    lastInstallTime
                )
            ) {
                Tuils.sendOutput(
                    appInstalledColor,
                    context,
                    formatPackageEvent(
                        appInstalledFormat!!,
                        packageName,
                        manager,
                        packageInfo,
                        null
                    )
                )
                lastInstalledPackage = packageName
                lastInstallTime = System.currentTimeMillis()
            }

            val i = manager.getLaunchIntentForPackage(packageName)
            if (i == null) return

            val name = i.getComponent()
            val activity = name!!.getClassName()
            val label = manager.getActivityInfo(name, 0).loadLabel(manager).toString()

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager?
            val currentProfile = Process.myUserHandle()
            val app = LaunchInfo(
                packageName,
                activity,
                label,
                currentProfile,
                profileSerial(userManager, currentProfile),
                true
            )
            appsHolder!!.add(app)
        } catch (e: Exception) {
        }
    }

    private fun appUninstalled(packageName: String, replacing: Boolean) {
        if (appsHolder == null || context == null) return

        if (replacing || shouldSuppressPackageEvent(
                packageName,
                lastUninstalledPackage,
                lastUninstallTime
            )
        ) {
            return
        }
        lastUninstalledPackage = packageName
        lastUninstallTime = System.currentTimeMillis()

        val infos: MutableList<LaunchInfo?> = AppUtils.findLaunchInfosWithPackage(
            packageName,
            appsHolder!!.apps
        )

        if (appUninstalledFormat != null) {
            Tuils.sendOutput(
                appUninstalledColor,
                context,
                formatPackageEvent(
                    appUninstalledFormat!!,
                    packageName,
                    context.getPackageManager(),
                    null,
                    infos
                )
            )
        }

        for (i in infos) appsHolder!!.remove(i)
    }

    fun findLaunchInfoWithLabel(label: String?, type: Int): LaunchInfo? {
        if (label == null) return null
        if (appsHolder == null) return null

        val appList: MutableList<LaunchInfo>?
        if (type == SHOWN_APPS) {
            appList = appsHolder!!.apps
        } else {
            appList = hiddenApps
        }

        if (appList == null) return null

        val i: LaunchInfo? = AppUtils.findLaunchInfoWithLabel(appList, label)
        if (i != null) {
            return i
        }

        val `is`: MutableList<LaunchInfo?> = AppUtils.findLaunchInfosWithPackage(label, appList)
        if (`is` == null || `is`.size == 0) return null
        return `is`.get(0)
    }

    fun writeLaunchTimes(info: LaunchInfo) {
        editor.putInt(info.write(), info.launchedTimes)
        editor.apply()

        if (appsHolder != null) appsHolder!!.update(true)
    }

    fun launch(launchContext: Context?, info: LaunchInfo?): Boolean {
        if (launchContext == null || info == null) {
            return false
        }

        info.launchedTimes++
        object : StoppableThread() {
            override fun run() {
                super.run()

                if (appsHolder != null) appsHolder!!.requestSuggestionUpdate(info)
                writeLaunchTimes(info)
            }
        }.start()

        if (!info.currentProfile) {
            try {
                val launcherApps =
                    launchContext.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps?
                if (launcherApps != null) {
                    launcherApps.startMainActivity(info.componentName, info.userHandle, null, null)
                    return true
                }
            } catch (e: Throwable) {
                Tuils.log(e)
                return false
            }
        }

        val intent = getIntent(info)
        if (intent == null) {
            return false
        }
        launchContext.startActivity(intent)
        return true
    }

    fun getIntent(info: LaunchInfo?): Intent? {
        if (info == null) {
            return null
        }

        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(info.componentName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun hideActivity(info: LaunchInfo?): String? {
        if (info == null) return null
        XMLPrefsManager.set(
            file,
            info.write(),
            arrayOf<String>(SHOW_ATTRIBUTE),
            arrayOf<String>(false.toString() + Tuils.EMPTYSTRING)
        )

        appsHolder!!.remove(info)
        appsHolder!!.update(true)
        hiddenApps!!.add(info)
        AppUtils.checkEquality(hiddenApps!!)

        return info.publicLabel
    }

    fun showActivity(info: LaunchInfo?): String? {
        if (info == null) return null
        XMLPrefsManager.set(
            file,
            info.write(),
            arrayOf<String>(SHOW_ATTRIBUTE),
            arrayOf<String>(true.toString() + Tuils.EMPTYSTRING)
        )

        hiddenApps!!.remove(info)
        appsHolder!!.add(info)
        appsHolder!!.update(false)

        return info.publicLabel
    }

    fun createGroup(name: String?): String? {
        if (name == null) return context.getString(R.string.output_groupnotfound)
        val index = Tuils.find(name, groups)
        if (index == -1) {
            groups.add(Group(name))
            return XMLPrefsManager.set(
                file,
                name,
                arrayOf<String>(APPS_ATTRIBUTE),
                arrayOf<String>(Tuils.EMPTYSTRING)
            )
        }

        return context!!.getString(R.string.output_groupexists)
    }

    fun groupBgColor(name: String?, color: String?): String? {
        val index = Tuils.find(name, groups)
        if (index == -1) {
            return context!!.getString(R.string.output_groupnotfound)
        }

        groups.get(index).bgColor = Color.parseColor(color)
        return XMLPrefsManager.set(
            file,
            name,
            arrayOf<String>(BGCOLOR_ATTRIBUTE),
            arrayOf<String?>(color)
        )
    }

    fun groupForeColor(name: String?, color: String?): String? {
        val index = Tuils.find(name, groups)
        if (index == -1) {
            return context!!.getString(R.string.output_groupnotfound)
        }

        groups.get(index).foreColor = Color.parseColor(color)
        return XMLPrefsManager.set(
            file,
            name,
            arrayOf<String>(FORECOLOR_ATTRIBUTE),
            arrayOf<String?>(color)
        )
    }

    fun removeGroup(name: String?): String? {
        val output = XMLPrefsManager.removeNode(file, name)

        if (output == null) return null
        if (output.length == 0) return context!!.getString(R.string.output_groupnotfound)

        val index = Tuils.find(name, groups)
        if (index != -1) groups.removeAt(index)

        return output
    }

    fun addAppToGroup(group: String?, app: LaunchInfo?): String? {
        if (app == null) return null
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element
        var apps = e.getAttribute(APPS_ATTRIBUTE)

        if (apps != null && app.isInside(apps)) return null

        apps = apps + APPS_SEPARATOR + app.write()
        if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)

        e.setAttribute(APPS_ATTRIBUTE, apps)

        XMLPrefsManager.writeTo(d, file)

        val index = Tuils.find(group, groups)
        if (index != -1) groups.get(index).add(app, true)

        return null
    }

    fun removeAppFromGroup(group: String?, app: LaunchInfo?): String? {
        if (app == null) return null
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element

        var apps = e.getAttribute(APPS_ATTRIBUTE)
        if (apps == null) return null

        if (!app.isInside(apps)) return null

        val temp = apps.replace(app.write(), Tuils.EMPTYSTRING)
        if (temp.length < apps.length) {
            apps = temp
            apps = apps.replace((APPS_SEPARATOR + APPS_SEPARATOR).toRegex(), APPS_SEPARATOR)
            if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)
            if (apps.endsWith(APPS_SEPARATOR)) apps = apps.substring(0, apps.length - 1)

            e.setAttribute(APPS_ATTRIBUTE, apps)

            XMLPrefsManager.writeTo(d, file)

            val index = Tuils.find(group, groups)
            if (index != -1) groups.get(index).remove(app)
        }

        return null
    }

    fun listGroup(group: String?): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element

        val apps = e.getAttribute(APPS_ATTRIBUTE)
        if (apps == null) return "[]"

        var labels = Tuils.EMPTYSTRING

        val split: Array<String> =
            apps.split(APPS_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (s in split) {
            if (s.length == 0) continue

            val identity = LaunchInfo.Companion.identityInfo(s)
            var info: LaunchInfo? = AppUtils.findLaunchInfoWithIdentity(appsHolder!!.apps, identity)
            if (info == null) {
                info = AppUtils.findLaunchInfoWithIdentity(hiddenApps!!, identity)
            }
            if (info == null) continue

            labels = labels + Tuils.NEWLINE + info.publicLabel
        }

        return labels.trim { it <= ' ' }
    }

    fun listGroups(): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val root = o[1] as Element

        var groups = Tuils.EMPTYSTRING

        val list = root.getElementsByTagName("*")
        for (count in 0..<list.getLength()) {
            val node = list.item(count)
            if (node !is Element) continue

            val e = node
            if (!e.hasAttribute(APPS_ATTRIBUTE)) continue

            groups = groups + Tuils.NEWLINE + e.getNodeName()
        }

        if (groups.length == 0) return "[]"
        return groups.trim { it <= ' ' }
    }

    fun shownApps(): MutableList<LaunchInfo> {
        if (appsHolder == null) return ArrayList<LaunchInfo>()
        return appsHolder!!.apps
    }

    fun hiddenApps(): MutableList<LaunchInfo> {
        return hiddenApps!!
    }

    val suggestedApps: Array<LaunchInfo?>
        get() {
            if (appsHolder == null) return arrayOfNulls<LaunchInfo>(0)
            return appsHolder!!.suggestedApps
        }

    fun printApps(type: Int): String? {
        return printNApps(type, -1)
    }

    fun printApps(type: Int, text: String?): String? {
        if (text == null) return printApps(type)
        var ok: Boolean
        var length = 0
        try {
            length = text.toInt()
            ok = true
        } catch (exc: NumberFormatException) {
            ok = false
        }

        if (ok) {
            return printNApps(type, length)
        } else {
            return printAppsThatBegins(type, text)
        }
    }

    private fun printNApps(type: Int, n: Int): String? {
        try {
            val labels: MutableList<String> = AppUtils.labelList(
                (if (type == AppsManager.Companion.SHOWN_APPS) appsHolder!!.apps else hiddenApps)!!,
                true
            )

            if (n >= 0) {
                val toRemove = labels.size - n
                if (toRemove <= 0) return "[]"

                for (c in 0..<toRemove) {
                    labels.removeAt(labels.size - 1)
                }
            }

            return AppUtils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    private fun printAppsThatBegins(type: Int, with: String?): String? {
        var with = with
        try {
            val labels: MutableList<String> = AppUtils.labelList(
                (if (type == AppsManager.Companion.SHOWN_APPS) appsHolder!!.apps else hiddenApps)!!,
                true
            )

            if (with != null && with.length > 0) {
                with = with.lowercase(Locale.getDefault())

                val it: MutableIterator<String?> = labels.iterator()
                while (it.hasNext()) {
                    if (!it.next()!!.lowercase(Locale.getDefault()).startsWith(with)) it.remove()
                }
            }

            return AppUtils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(appsBroadcast)
    }

    fun onDestroy() {
        unregisterReceiver(context!!)
    }

    class Group(var name: String) : MainManager.Group, StringableObject {
        var apps: MutableList<GroupLaunchInfo>

        var bgColor: Int = Int.Companion.MAX_VALUE
        var foreColor: Int = Int.Companion.MAX_VALUE

        var lowercaseName: String

        init {
            this.lowercaseName = name.lowercase(Locale.getDefault())

            apps = ArrayList<GroupLaunchInfo>()
        }

        fun add(info: LaunchInfo, sort: Boolean) {
            apps.add(GroupLaunchInfo(info, apps.size))

            if (sort) sort()
        }

        fun remove(info: LaunchInfo?) {
            val iterator = apps.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == info) {
                    iterator.remove()
                    return
                }
            }
        }

        fun remove(app: String?) {
            val iterator = apps.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().componentName!!.getPackageName() == app) {
                    iterator.remove()
                    return
                }
            }
        }

        fun sort() {
            Collections.sort<GroupLaunchInfo>(apps, comparator)
        }

        fun contains(info: LaunchInfo?): Boolean {
            return apps.contains(info)
        }

        override fun members(): MutableList<out Any> {
            return apps
        }

        override fun use(mainPack: MainPack, input: String): Boolean {
            val info: LaunchInfo? = AppUtils.findLaunchInfoWithLabel(apps, input)
            if (info == null) return false

            return mainPack.appsManager.launch(mainPack.context, info)
        }

        override fun name(): String {
            return name
        }

        override fun equals(obj: Any?): Boolean {
            if (obj is Group) {
                return name == obj.name()
            } else if (obj is String) {
                return obj == name
            }

            return false
        }

        override fun getLowercaseString(): String {
            return lowercaseName
        }

        override fun getString(): String {
            return name()
        }

        inner class GroupLaunchInfo(info: LaunchInfo, index: Int) : LaunchInfo(info) {
            var initialIndex: Int

            init {
                launchedTimes = info.launchedTimes
                unspacedLowercaseLabel = info.unspacedLowercaseLabel

                this.initialIndex = index
            }
        }

        companion object {
            const val ALPHABETIC_UP_DOWN: Int = 0
            const val ALPHABETIC_DOWN_UP: Int = 1
            const val TIME_UP_DOWN: Int = 2
            const val TIME_DOWN_UP: Int = 3
            const val MOSTUSED_UP_DOWN: Int = 4
            const val MOSTUSED_DOWN_UP: Int = 5

            var sorting: Int = 0

            var comparator: Comparator<GroupLaunchInfo> = object : Comparator<GroupLaunchInfo> {
                override fun compare(o1: GroupLaunchInfo, o2: GroupLaunchInfo): Int {
                    when (sorting) {
                        ALPHABETIC_UP_DOWN -> return Tuils.alphabeticCompare(
                            o1.publicLabel ?: Tuils.EMPTYSTRING,
                            o2.publicLabel ?: Tuils.EMPTYSTRING
                        )

                        ALPHABETIC_DOWN_UP -> return Tuils.alphabeticCompare(
                            o2.publicLabel ?: Tuils.EMPTYSTRING,
                            o1.publicLabel ?: Tuils.EMPTYSTRING
                        )

                        TIME_UP_DOWN -> return o1.initialIndex - o2.initialIndex
                        TIME_DOWN_UP -> return o2.initialIndex - o1.initialIndex
                        MOSTUSED_UP_DOWN -> return o2.launchedTimes - o1.launchedTimes
                        MOSTUSED_DOWN_UP -> return o1.launchedTimes - o2.launchedTimes
                    }

                    return 0
                }
            }
        }
    }

    open class LaunchInfo : Parcelable, StringableObject, Comparable<LaunchInfo> {
        var componentName: ComponentName?
        var userHandle: UserHandle?
        var profileSerial: Long
        var currentProfile: Boolean

        var publicLabel: String? = null
        var unspacedLowercaseLabel: String? = null
        var lowercaseLabel: String? = null
        var launchedTimes: Int = 0

        @get:JvmName("getShortcutList")
        @set:JvmName("setShortcutList")
        var shortcuts: MutableList<ShortcutInfo?>? = null

        @JvmOverloads
        constructor(
            packageName: String,
            activityName: String,
            label: String?,
            userHandle: UserHandle? = Process.myUserHandle(),
            profileSerial: Long = 0L,
            currentProfile: Boolean = true
        ) {
            this.componentName = ComponentName(packageName, activityName)
            this.userHandle = if (userHandle != null) userHandle else Process.myUserHandle()
            this.profileSerial = profileSerial
            this.currentProfile = currentProfile
            setLabel(displayLabel(label, currentProfile))
        }

        constructor(info: LaunchInfo) : this(
            info.componentName!!.getPackageName(),
            info.componentName!!.getClassName(),
            info.publicLabel,
            info.userHandle,
            info.profileSerial,
            info.currentProfile
        ) {
            launchedTimes = info.launchedTimes
            unspacedLowercaseLabel = info.unspacedLowercaseLabel
            lowercaseLabel = info.lowercaseLabel
            shortcuts = info.shortcuts
        }

        protected constructor(`in`: Parcel) {
            componentName =
                `in`.readParcelable<ComponentName?>(ComponentName::class.java.getClassLoader())
            userHandle = `in`.readParcelable<UserHandle?>(UserHandle::class.java.getClassLoader())
            profileSerial = `in`.readLong()
            currentProfile = `in`.readByte().toInt() != 0
            setLabel(`in`.readString()!!)
            launchedTimes = `in`.readInt()
        }

        fun setLabel(s: String) {
            this.publicLabel = s
            this.lowercaseLabel = s.lowercase(Locale.getDefault())
            this.unspacedLowercaseLabel = Tuils.removeSpaces(lowercaseLabel ?: Tuils.EMPTYSTRING)
        }

        fun isInside(apps: String): Boolean {
            val split: Array<String> =
                apps.split(APPS_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (s in split) {
                if (`is`(s)) return true
            }

            return false
        }

        fun `is`(app: String?): Boolean {
            val identity = identityInfo(app)
            return identity != null && profileSerial == identity.profileSerial && componentName == identity.componentName
        }

        override fun equals(o: Any?): Boolean {
            if (o == null) {
                return false
            }

            if (o is LaunchInfo) {
                val i = o
                try {
                    return this.profileSerial == i.profileSerial && this.componentName == i.componentName
                } catch (e: Exception) {
                    return false
                }
            } else if (o is ComponentName) {
                return this.componentName == o
            } else if (o is String) {
                return `is`(o) || this.componentName!!.getClassName() == o
            }

            return false
        }

        override fun toString(): String {
            return write() + " --> " + publicLabel + ", n=" + launchedTimes
        }

        override fun getLowercaseString(): String? {
            return lowercaseLabel
        }

        override fun getString(): String? {
            return publicLabel
        }

        fun write(): String {
            return PROFILE_PREFIX + profileSerial + COMPONENT_SEPARATOR + this.componentName!!.getPackageName() + COMPONENT_SEPARATOR + this.componentName!!.getClassName()
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeParcelable(componentName, flags)
            dest.writeParcelable(userHandle, flags)
            dest.writeLong(profileSerial)
            dest.writeByte((if (currentProfile) 1 else 0).toByte())
            dest.writeString(publicLabel)
            dest.writeInt(launchedTimes)
        }

        fun setShortcuts(s: MutableList<ShortcutInfo?>?) {
            this.shortcuts = s
        }

        override fun compareTo(o: LaunchInfo): Int {
            return o.launchedTimes - launchedTimes
        }

        class Identity internal constructor(
            val profileSerial: Long,
            val componentName: ComponentName?
        )

        companion object {
            private const val COMPONENT_SEPARATOR = "-"

            @JvmField
            val CREATOR: Parcelable.Creator<LaunchInfo?> =
                object : Parcelable.Creator<LaunchInfo?> {
                    override fun createFromParcel(`in`: Parcel): LaunchInfo {
                        return LaunchInfo(`in`)
                    }

                    override fun newArray(size: Int): Array<LaunchInfo?> {
                        return arrayOfNulls<LaunchInfo>(size)
                    }
                }

            private fun displayLabel(label: String?, currentProfile: Boolean): String {
                var label = label
                if (label == null) {
                    label = Tuils.EMPTYSTRING
                }
                if (currentProfile || label.endsWith(" (Work)")) {
                    return label
                }
                return label + " (Work)"
            }

            fun identityInfo(app: String?): Identity? {
                if (app == null) {
                    return null
                }

                val split: Array<String?> =
                    app.split(COMPONENT_SEPARATOR.toRegex(), limit = 3).toTypedArray()
                if (split.size != 3 || !split[0]!!.startsWith(PROFILE_PREFIX)) {
                    return null
                }

                try {
                    val serial = split[0]!!.substring(PROFILE_PREFIX.length).toLong()
                    return Identity(serial, ComponentName(split[1]!!, split[2]!!))
                } catch (e: Exception) {
                    return null
                }
            }
        }
    }

    private inner class AppsHolder(
        var apps: MutableList<LaunchInfo>,
        private val values: XMLPrefsList
    ) {
        val MOST_USED: Int = 10
        val NULL: Int = 11
        val USER_DEFINIED: Int = 12

        private var suggestedAppMgr: SuggestedAppMgr? = null

        private inner class SuggestedAppMgr(
            private val values: XMLPrefsList,
            private val apps: MutableList<LaunchInfo>
        ) {
            private var suggested: MutableList<SuggestedApp>
            private var lastWriteable = -1

            init {
                suggested = ArrayList<SuggestedApp>()

                val PREFIX = "default_app_n"
                for (count in 0..4) {
                    val vl = values.get(Apps.valueOf(PREFIX + (count + 1)))!!.value

                    if (vl == Apps.NULL) continue
                    if (vl == Apps.MOST_USED) suggested.add(SuggestedApp(MOST_USED, count + 1))
                    else {
                        val identity = LaunchInfo.Companion.identityInfo(vl)
                        var info: LaunchInfo? =
                            AppUtils.findLaunchInfoWithIdentity(this.apps, identity)
                        if (info == null) {
                            info = AppUtils.findLaunchInfosWithPackage(vl, this.apps).firstOrNull()
                        }
                        if (info == null) continue
                        suggested.add(SuggestedApp(info, USER_DEFINIED, count + 1))
                    }
                }

                sort()
            }

            fun size(): Int {
                return suggested.size
            }

            fun sort() {
                Collections.sort<SuggestedApp?>(suggested)
                for (count in suggested.indices) {
                    if (suggested.get(count).type != MOST_USED) {
                        lastWriteable = count - 1
                        return
                    }
                }
                lastWriteable = suggested.size - 1
            }

            fun get(index: Int): SuggestedApp {
                return suggested.get(index)
            }

            fun set(index: Int, info: LaunchInfo?) {
                suggested.get(index).change(info)
            }

            fun attemptInsertSuggestion(info: LaunchInfo) {
                if (info.launchedTimes == 0 || lastWriteable == -1) {
                    return
                }

                val index = Tuils.find(info, suggested)
                if (index == -1) {
                    for (count in 0..lastWriteable) {
                        val app = get(count)

                        if (app.app == null || info.launchedTimes > app.app!!.launchedTimes) {
                            val s = suggested.get(count)

                            val before = s.app
                            s.change(info)

                            if (before != null) {
                                attemptInsertSuggestion(before)
                            }

                            break
                        }
                    }
                }
                sort()
            }

            fun apps(): MutableList<LaunchInfo?> {
                val list: MutableList<LaunchInfo?> = ArrayList<LaunchInfo?>()

                val cp: MutableList<SuggestedApp> = ArrayList<SuggestedApp>(suggested)
                Collections.sort<SuggestedApp?>(
                    cp,
                    Comparator { o1: SuggestedApp?, o2: SuggestedApp? -> o1!!.index - o2!!.index })

                for (count in cp.indices) {
                    val app = cp.get(count)
                    if (app.type != NULL && app.app != null) list.add(app.app)
                }
                return list
            }

            private inner class SuggestedApp(var app: LaunchInfo?, var type: Int, var index: Int) :
                Comparable<Any?> {
                constructor(type: Int, index: Int) : this(null, type, index)

                fun change(info: LaunchInfo?): SuggestedApp {
                    this.app = info
                    return this
                }

                override fun equals(o: Any?): Boolean {
                    if (o is SuggestedApp) {
                        try {
                            return (app == null && o.app == null) || app == o.app
                        } catch (e: NullPointerException) {
                            return false
                        }
                    } else if (o is LaunchInfo) {
                        if (app == null) return false
                        return app == o
                    }
                    return false
                }

                override fun compareTo(o: Any?): Int {
                    val other: SuggestedApp = o as SuggestedApp

                    if (this.type == USER_DEFINIED || other.type == USER_DEFINIED) {
                        if (this.type == USER_DEFINIED && other.type == USER_DEFINIED) return other.app!!.launchedTimes - this.app!!.launchedTimes
                        if (this.type == USER_DEFINIED) return 1
                        return -1
                    }

                    //                    most_used
                    if (this.app == null || other.app == null) {
                        if (this.app == null && other.app == null) return 0
                        if (this.app == null) return 1
                        return -1
                    }
                    return this.app!!.launchedTimes - other.app!!.launchedTimes
                }
            }
        }

        var mostUsedComparator: Comparator<LaunchInfo> =
            Comparator { lhs: LaunchInfo, rhs: LaunchInfo -> if (rhs.launchedTimes > lhs.launchedTimes) -1 else if (rhs.launchedTimes == lhs.launchedTimes) 0 else 1 }

        init {
            update(true)
        }

        fun add(info: LaunchInfo?) {
            if (!apps.contains(info)) {
                apps.add(info!!)
                update(false)
            }
        }

        fun remove(info: LaunchInfo?) {
            apps.remove(info)
            update(true)
        }

        fun sort() {
            try {
                Collections.sort<LaunchInfo>(this.apps, mostUsedComparator)
            } catch (e: NullPointerException) {
            }
        }

        fun fillSuggestions() {
            suggestedAppMgr = SuggestedAppMgr(values, this.apps)
            for (info in this.apps) {
                suggestedAppMgr!!.attemptInsertSuggestion(info)
            }
        }

        fun requestSuggestionUpdate(info: LaunchInfo) {
            suggestedAppMgr!!.attemptInsertSuggestion(info)
        }

        fun update(refreshSuggestions: Boolean) {
            AppUtils.checkEquality(this.apps)
            sort()
            if (refreshSuggestions) {
                fillSuggestions()
            }
        }

        val suggestedApps: Array<LaunchInfo?>
            get() {
                val apps = suggestedAppMgr!!.apps()
                return apps.toTypedArray<LaunchInfo?>()
            }
    }

    object AppUtils {
        fun findLaunchInfoWithIdentity(
            appList: MutableList<LaunchInfo>,
            identity: LaunchInfo.Identity?
        ): LaunchInfo? {
            if (identity == null) return null

            for (i in appList) {
                if (i.profileSerial == identity.profileSerial && i.componentName == identity.componentName) return i
            }

            return null
        }

        fun findLaunchInfoWithLabel(
            appList: MutableList<out LaunchInfo>,
            label: String?
        ): LaunchInfo? {
            var label = label ?: return null
            label = Tuils.removeSpaces(label)
            for (i in appList) if (i.unspacedLowercaseLabel.equals(
                    label,
                    ignoreCase = true
                )
            ) return i
            return null
        }

        fun findLaunchInfosWithPackage(
            packageName: String?,
            infos: MutableList<LaunchInfo>
        ): MutableList<LaunchInfo?> {
            val result: MutableList<LaunchInfo?> = ArrayList<LaunchInfo?>()
            for (info in infos) if (info.componentName!!.getPackageName() == packageName) result.add(
                info
            )
            return result
        }

        fun checkEquality(list: MutableList<LaunchInfo>) {
            for (info in list) {
                if (info == null || info.publicLabel == null) {
                    continue
                }

                for (count in list.indices) {
                    val info2: LaunchInfo? = list.get(count)

                    if (info2 == null || info2.publicLabel == null) {
                        continue
                    }

                    if (info === info2) {
                        continue
                    }

                    if (info.unspacedLowercaseLabel == info2.unspacedLowercaseLabel) {
//                        there are two activities in the same app loadlabel gives the same result
                        if (info.componentName!!.getPackageName() == info2.componentName!!.getPackageName()) {
                            info.setLabel(
                                insertActivityName(
                                    info.publicLabel,
                                    info.componentName!!.getClassName()
                                )
                            )
                            info2.setLabel(
                                insertActivityName(
                                    info2.publicLabel,
                                    info2.componentName!!.getClassName()
                                )
                            )
                        } else {
                            info2.setLabel(
                                getNewLabel(
                                    info2.publicLabel,
                                    info2.componentName!!.getClassName()
                                )!!
                            )
                        }
                    }
                }
            }
        }

        var activityPattern: Pattern =
            Pattern.compile("activity", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

        fun insertActivityName(oldLabel: String?, activityName: String): String {
            var name: String?

            val lastDot = activityName.lastIndexOf(".")
            if (lastDot == -1) {
                name = activityName
            } else {
                name = activityName.substring(lastDot + 1)
            }

            name = activityPattern.matcher(name).replaceAll(Tuils.EMPTYSTRING)
            name = name.substring(0, 1).uppercase(Locale.getDefault()) + name.substring(1)
            return oldLabel + Tuils.SPACE + "-" + Tuils.SPACE + name
        }

        fun getNewLabel(oldLabel: String?, packageName: String): String? {
            try {
                var firstDot = packageName.indexOf(Tuils.DOT)
                if (firstDot == -1) {
//                    no dots in package name (nearly impossible)
                    return packageName
                }
                firstDot++

                val secondDot = packageName.substring(firstDot).indexOf(Tuils.DOT)
                var prefix: String?
                if (secondDot == -1) {
//                    only one dot, so two words. The first is most likely to be the company name
//                    facebook.messenger
//                    is better than
//                    messenger.facebook
                    prefix = packageName.substring(0, firstDot - 1)
                    prefix =
                        prefix.substring(0, 1).uppercase(Locale.getDefault()) + prefix.substring(1)
                            .lowercase(
                                Locale.getDefault()
                            )
                    return prefix + Tuils.SPACE + oldLabel
                } else {
//                    two dots or more, the second word is the company name
                    prefix = packageName.substring(firstDot, secondDot + firstDot)
                    prefix =
                        prefix.substring(0, 1).uppercase(Locale.getDefault()) + prefix.substring(1)
                            .lowercase(
                                Locale.getDefault()
                            )
                    return prefix + Tuils.SPACE + oldLabel
                }
            } catch (e: Exception) {
                return packageName
            }
        }

        fun format(app: LaunchInfo, info: PackageInfo): String {
            val builder = StringBuilder()

            builder.append(info.packageName).append(Tuils.NEWLINE)
            builder.append("vrs: ").append(info.versionCode).append(" - ").append(info.versionName)
                .append(Tuils.NEWLINE).append(Tuils.NEWLINE)
            builder.append("launched_times: ").append(app.launchedTimes).append(Tuils.NEWLINE)
                .append(Tuils.NEWLINE)

            builder.append("Install: ").append(
                TimeManager.instance!!.replace(
                    "%t0",
                    info.firstInstallTime,
                    Int.Companion.MAX_VALUE
                )
            ).append(Tuils.NEWLINE).append(Tuils.NEWLINE)

            val a = info.activities
            if (a != null && a.size > 0) {
                val `as`: MutableList<String?> = ArrayList<String?>()
                for (i in a) `as`.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
                builder.append("Activities: ").append(Tuils.NEWLINE)
                    .append(Tuils.toPlanString(`as`, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                    .append(Tuils.NEWLINE)
            }

            val s = info.services
            if (s != null && s.size > 0) {
                val ss: MutableList<String?> = ArrayList<String?>()
                for (i in s) ss.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
                builder.append("Services: ").append(Tuils.NEWLINE)
                    .append(Tuils.toPlanString(ss, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                    .append(Tuils.NEWLINE)
            }

            val r = info.receivers
            if (r != null && r.size > 0) {
                val rs: MutableList<String?> = ArrayList<String?>()
                for (i in r) rs.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
                builder.append("Receivers: ").append(Tuils.NEWLINE)
                    .append(Tuils.toPlanString(rs, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                    .append(Tuils.NEWLINE)
            }

            val p = info.requestedPermissions
            if (p != null && p.size > 0) {
                val ps: MutableList<String?> = ArrayList<String?>()
                for (i in p) ps.add(i.substring(i.lastIndexOf(".") + 1))
                builder.append("Permissions: ").append(Tuils.NEWLINE)
                    .append(Tuils.toPlanString(ps, ", "))
            }

            return builder.toString()
        }

        fun printApps(apps: MutableList<String>): String {
            if (apps.size == 0) {
                return apps.toString()
            }

            val list: MutableList<String?> = ArrayList<String?>(apps)

            Collections.sort<String?>(
                list,
                Comparator { s1: String?, s2: String? -> Tuils.alphabeticCompare(s1, s2) })

            Tuils.addPrefix(list, Tuils.DOUBLE_SPACE)
            Tuils.insertHeaders(list, false)
            return Tuils.toPlanString(list)
        }

        fun labelList(infos: MutableList<LaunchInfo>, sort: Boolean): MutableList<String> {
            val labels: MutableList<String> = ArrayList<String>()
            for (info in infos) {
                labels.add(info.publicLabel!!)
            }
            if (sort) Collections.sort<String>(labels)
            return labels
        }
    }

    companion object {
        const val SHOWN_APPS: Int = 10
        const val HIDDEN_APPS: Int = 11

        const val PATH: String = "apps.xml"
        private const val APPS_SEPARATOR = ";"

        private const val PROFILE_PREFIX = "profile"
        var instance: XMLPrefsElement? = null
    }
}
