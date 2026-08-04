package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.ArrayList
import java.util.LinkedHashSet

class inspect : CommandAbstraction {
    override fun exec(pack: ExecutePack): String {
        val query = pack.getString().trim()
        if (query.isEmpty()) return pack.context.getString(helpRes())
        return inspectPackage(pack, resolvePackageName(pack, query))
    }

    private fun resolvePackageName(pack: ExecutePack, query: String): String {
        if (pack is MainPack) {
            val info = pack.appsManager.findLaunchInfoWithLabel(query, AppsManager.SHOWN_APPS)
                ?: pack.appsManager.findLaunchInfoWithLabel(query, AppsManager.HIDDEN_APPS)
            val component = info?.componentName
            if (component != null) return component.packageName
        }
        return query
    }

    private fun inspectPackage(pack: ExecutePack, packageName: String): String {
        val pm = pack.context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or
            PackageManager.GET_DISABLED_COMPONENTS or PackageManager.GET_INTENT_FILTERS
        val packageInfo = try {
            pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            return "Package not found: $packageName"
        }

        val out = StringBuilder()
        out.append(packageInfo.applicationInfo?.loadLabel(pm) ?: packageName)
            .append(Tuils.NEWLINE).append(packageName).append(Tuils.NEWLINE)
        val common = commonHandlers(pm, packageName)
        if (common.isNotEmpty()) {
            out.append(Tuils.NEWLINE).append("Common intents:").append(Tuils.NEWLINE)
            common.forEach { out.append("- ").append(it).append(Tuils.NEWLINE) }
        }
        appendComponents(out, "Exported activities", packageInfo.activities, packageName)
        appendComponents(out, "Exported receivers", packageInfo.receivers, packageName)
        appendComponents(out, "Exported services", packageInfo.services, packageName)
        appendComponents(out, "Exported providers", packageInfo.providers, packageName)
        return out.toString().trim()
    }

    private fun commonHandlers(pm: PackageManager, packageName: String): List<String> {
        val lines = ArrayList<String>()
        addActivityHandler(pm, lines, "launch", Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName))
        addActivityHandler(pm, lines, "share text", Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(packageName))
        addActivityHandler(pm, lines, "view web", Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).setPackage(packageName))
        addActivityHandler(pm, lines, "process text", Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(packageName))
        return lines
    }

    private fun addActivityHandler(pm: PackageManager, lines: MutableList<String>, label: String, intent: Intent) {
        val seen = LinkedHashSet<String>()
        for (info in pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            val activity = info.activityInfo ?: continue
            val component = shortComponent(activity.packageName, activity.name)
            if (seen.add(component)) lines.add("$label -> $component")
        }
    }

    private fun appendComponents(out: StringBuilder, title: String, components: Array<out ComponentInfo>?, packageName: String) {
        if (components.isNullOrEmpty()) return
        var count = 0
        for (component in components) {
            if (!component.exported) continue
            if (count == 0) out.append(Tuils.NEWLINE).append(title).append(":").append(Tuils.NEWLINE)
            out.append("- ").append(shortComponent(packageName, component.name))
            componentPermission(component)?.let { out.append(" requires ").append(it) }
            out.append(Tuils.NEWLINE)
            if (++count == 12) {
                out.append("- ...").append(Tuils.NEWLINE)
                return
            }
        }
    }

    private fun componentPermission(component: ComponentInfo): String? = when (component) {
        is ActivityInfo -> component.permission
        is ServiceInfo -> component.permission
        is ProviderInfo -> component.readPermission ?: component.writePermission
        else -> null
    }

    private fun shortComponent(packageName: String, className: String): String =
        if (className.startsWith(packageName)) className.substring(packageName.length) else className

    override fun argType() = intArrayOf(CommandAbstraction.PLAIN_TEXT)
    override fun priority() = 3
    override fun helpRes() = R.string.help_inspect
    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int) = pack.context.getString(helpRes())
    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int) = pack.context.getString(helpRes())
}
