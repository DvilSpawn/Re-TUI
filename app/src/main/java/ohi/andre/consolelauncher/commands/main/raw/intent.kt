package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.tuils.Tuils
import java.net.URISyntaxException
import java.util.ArrayList
import java.util.LinkedHashSet

class intent : CommandAbstraction {
    private enum class Mode {
        VIEW,
        ACTIVITY,
        BROADCAST,
        URI,
        CHECK
    }

    override fun exec(pack: ExecutePack): String? {
        val input = pack.getString()
        val args = Tuils.splitArgs(input)
        if (args.isEmpty()) {
            return pack.context.getString(helpRes())
        }

        val mode = modeFor(removeMode(args))
        if (mode == null) {
            return pack.context.getString(R.string.output_invalidarg)
        }

        try {
            if (mode == Mode.CHECK) {
                val packageName = packageCheckTarget(pack, args)
                if (packageName != null) {
                    return checkPackage(pack, packageName)
                }
            }

            val built = buildIntent(mode, args)
            if (built == null) {
                return pack.context.getString(helpRes())
            }

            if (mode == Mode.CHECK) {
                return checkIntent(pack, built)
            }

            if (mode == Mode.BROADCAST) {
                if (built.getAction() == null) {
                    return "intent -broadcast requires -a <action>"
                }
                if (built.getPackage() == null && built.getComponent() == null && !args.contains("--unsafe-implicit")) {
                    return "intent -broadcast requires -p or -n. Add --unsafe-implicit to send a broad implicit broadcast."
                }
                pack.context.sendBroadcast(built)
                return "Broadcast sent: " + built.getAction()
            }

            if (mode == Mode.ACTIVITY && built.getAction() == null && built.getComponent() == null && built.getData() == null && built.getType() == null) {
                return "intent -activity needs -a or -n. To inspect an app, use intent -check " + built.getPackage()
            }

            pack.context.startActivity(built)
            return Tuils.EMPTYSTRING
        } catch (e: ActivityNotFoundException) {
            return "No activity found for intent."
        } catch (e: SecurityException) {
            return "Intent blocked by Android security: " + e.message
        } catch (e: IllegalArgumentException) {
            return "Invalid intent: " + e.message
        } catch (e: URISyntaxException) {
            return "Invalid intent: " + e.message
        }
    }

    private fun removeMode(args: MutableList<String?>): String? {
        if (args.isEmpty()) return null
        val first = args.removeAt(0)
        if ("-" == first && !args.isEmpty()) {
            return "-" + args.removeAt(0)
        }
        return first
    }

    private fun modeFor(value: String?): Mode? {
        val normalized = value?.removePrefix("-")
        if ("view".equals(normalized, ignoreCase = true)) return Mode.VIEW
        if ("activity".equals(normalized, ignoreCase = true)) return Mode.ACTIVITY
        if ("broadcast".equals(normalized, ignoreCase = true)) return Mode.BROADCAST
        if ("uri".equals(normalized, ignoreCase = true)) return Mode.URI
        if ("check".equals(normalized, ignoreCase = true)) return Mode.CHECK
        return null
    }

    private fun packageCheckTarget(pack: ExecutePack, args: MutableList<String?>): String? {
        if (args.isEmpty()) {
            return null
        }

        if (args.size == 2 && "-p" == args[0]) {
            return resolvePackageName(pack, args[1])
        }

        for (arg in args) {
            if (arg != null && arg.startsWith("-")) {
                return null
            }
        }

        return resolvePackageName(pack, args.filterNotNull().joinToString(Tuils.SPACE))
    }

    private fun resolvePackageName(pack: ExecutePack, value: String?): String? {
        if (value == null || value.trim { it <= ' ' }.isEmpty()) {
            return null
        }

        val query = value.trim { it <= ' ' }
        if (pack is MainPack) {
            var info = pack.appsManager.findLaunchInfoWithLabel(query, AppsManager.SHOWN_APPS)
            if (info == null) {
                info = pack.appsManager.findLaunchInfoWithLabel(query, AppsManager.HIDDEN_APPS)
            }
            if (info != null) {
                return info.componentName!!.getPackageName()
            }
        }

        return query
    }

    @Throws(URISyntaxException::class)
    private fun buildIntent(mode: Mode?, args: MutableList<String?>): Intent? {
        if (mode == Mode.VIEW) {
            if (args.isEmpty()) return null
            return Intent(Intent.ACTION_VIEW, Uri.parse(args.get(0)))
        }

        if (mode == Mode.URI) {
            if (args.isEmpty()) return null
            return Intent.parseUri(args.get(0), Intent.URI_INTENT_SCHEME)
        }

        val built = Intent()
        if (mode == Mode.CHECK && !args.isEmpty()) {
            val nested = modeFor(args.get(0))
            if (nested != null && nested != Mode.CHECK) {
                args.removeAt(0)
                return buildIntent(nested, args)
            }
        }

        var data: String? = null
        var type: String? = null
        var hasPayload = false

        var i = 0
        while (i < args.size) {
            val arg = args.get(i)
            if ("--unsafe-implicit" == arg) {
                i++
                continue
            }
            if ("-a" == arg) {
                built.setAction(requiredValue(args, ++i, "-a"))
                hasPayload = true
            } else if ("-d" == arg) {
                data = requiredValue(args, ++i, "-d")
                hasPayload = true
            } else if ("-t" == arg) {
                type = requiredValue(args, ++i, "-t")
                hasPayload = true
            } else if ("-p" == arg) {
                built.setPackage(requiredValue(args, ++i, "-p"))
                hasPayload = true
            } else if ("-n" == arg) {
                built.setComponent(parseComponent(requiredValue(args, ++i, "-n")!!))
                hasPayload = true
            } else if ("--es" == arg) {
                built.putExtra(
                    requiredValue(args, ++i, "--es key"),
                    requiredValue(args, ++i, "--es value")
                )
                hasPayload = true
            } else if ("--ei" == arg) {
                built.putExtra(
                    requiredValue(args, ++i, "--ei key"),
                    requiredValue(args, ++i, "--ei value")!!.toInt()
                )
                hasPayload = true
            } else if ("--ez" == arg) {
                built.putExtra(
                    requiredValue(args, ++i, "--ez key"),
                    requiredValue(args, ++i, "--ez value").toBoolean()
                )
                hasPayload = true
            } else {
                throw IllegalArgumentException("unknown option " + arg)
            }
            i++
        }

        if (data != null && type != null) {
            built.setDataAndType(Uri.parse(data), type)
        } else if (data != null) {
            built.setData(Uri.parse(data))
        } else if (type != null) {
            built.setType(type)
        }

        return if (hasPayload) built else null
    }

    private fun requiredValue(args: MutableList<String?>, index: Int, option: String?): String? {
        require(index < args.size) { option + " needs a value" }
        return args.get(index)
    }

    private fun parseComponent(value: String): ComponentName {
        val component: ComponentName = ComponentName.unflattenFromString(value)!!
        requireNotNull(component) { "invalid component " + value }
        return component
    }

    private fun checkIntent(pack: ExecutePack, built: Intent): String {
        val pm = pack.context.getPackageManager()
        val infos: MutableList<ResolveInfo> = ArrayList<ResolveInfo>()
        infos.addAll(pm.queryIntentActivities(built, PackageManager.MATCH_DEFAULT_ONLY))
        if (built.getAction() != null) {
            infos.addAll(pm.queryBroadcastReceivers(built, PackageManager.MATCH_DEFAULT_ONLY))
        }

        if (infos.isEmpty()) {
            return "No handlers found. Android package visibility may hide some targets."
        }

        val out = StringBuilder()
        for (info in infos) {
            val label = info.loadLabel(pm)
            val name = if (label == null) "Unknown" else label.toString()
            val pkg = if (info.activityInfo != null)
                info.activityInfo.packageName
            else
                if (info.serviceInfo != null) info.serviceInfo.packageName else null
            val cls = if (info.activityInfo != null)
                info.activityInfo.name
            else
                if (info.serviceInfo != null) info.serviceInfo.name else null
            out.append(name)
            if (pkg != null) out.append(" | ").append(pkg)
            if (cls != null) out.append("/").append(cls)
            out.append(Tuils.NEWLINE)
        }
        return out.toString().trim { it <= ' ' }
    }

    private fun checkPackage(pack: ExecutePack, packageName: String): String {
        val pm = pack.context.getPackageManager()
        val flags = PackageManager.GET_ACTIVITIES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_SERVICES or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_DISABLED_COMPONENTS or
                PackageManager.GET_INTENT_FILTERS

        val packageInfo = try {
            pm.getPackageInfo(packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            return "Package not found: " + packageName
        }

        val label = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: packageName
        val out = StringBuilder()
        out.append(label).append(Tuils.NEWLINE)
        out.append(packageName).append(Tuils.NEWLINE)

        val common = commonHandlers(pm, packageName)
        if (common.isNotEmpty()) {
            out.append(Tuils.NEWLINE).append("Common intents:").append(Tuils.NEWLINE)
            for (line in common) {
                out.append("- ").append(line).append(Tuils.NEWLINE)
            }
        }

        appendComponents(out, "Exported activities", packageInfo.activities, packageName)
        appendComponents(out, "Exported receivers", packageInfo.receivers, packageName)
        appendComponents(out, "Exported services", packageInfo.services, packageName)
        appendComponents(out, "Exported providers", packageInfo.providers, packageName)

        return out.toString().trim { it <= ' ' }
    }

    private fun commonHandlers(pm: PackageManager, packageName: String): MutableList<String> {
        val lines = ArrayList<String>()
        addActivityHandler(
            pm,
            lines,
            "launch",
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        )
        addActivityHandler(
            pm,
            lines,
            "share text",
            Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(packageName)
        )
        addActivityHandler(
            pm,
            lines,
            "view web",
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).setPackage(packageName)
        )
        addActivityHandler(
            pm,
            lines,
            "process text",
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(packageName)
        )
        return lines
    }

    private fun addActivityHandler(
        pm: PackageManager,
        lines: MutableList<String>,
        label: String,
        intent: Intent
    ) {
        val seen = LinkedHashSet<String>()
        val infos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in infos) {
            val activity = info.activityInfo ?: continue
            val component = shortComponent(activity.packageName, activity.name)
            if (seen.add(component)) {
                lines.add(label + " -> " + component)
            }
        }
    }

    private fun appendComponents(
        out: StringBuilder,
        title: String,
        components: Array<out ComponentInfo>?,
        packageName: String
    ) {
        if (components == null || components.isEmpty()) {
            return
        }

        var count = 0
        for (component in components) {
            if (!component.exported) {
                continue
            }
            if (count == 0) {
                out.append(Tuils.NEWLINE).append(title).append(":").append(Tuils.NEWLINE)
            }
            out.append("- ").append(shortComponent(packageName, component.name))
            val permission = componentPermission(component)
            if (permission != null) {
                out.append(" requires ").append(permission)
            }
            out.append(Tuils.NEWLINE)
            count++
            if (count == 12) {
                out.append("- ...").append(Tuils.NEWLINE)
                return
            }
        }
    }

    private fun componentPermission(component: ComponentInfo): String? {
        return when (component) {
            is ActivityInfo -> component.permission
            is ServiceInfo -> component.permission
            is ProviderInfo -> component.readPermission ?: component.writePermission
            else -> null
        }
    }

    private fun shortComponent(packageName: String, className: String): String {
        return if (className.startsWith(packageName)) {
            className.substring(packageName.length)
        } else {
            className
        }
    }

    override fun argType(): IntArray? {
        return intArrayOf(CommandAbstraction.PLAIN_TEXT)
    }

    override fun priority(): Int {
        return 3
    }

    override fun helpRes(): Int {
        return R.string.help_intent
    }

    override fun onArgNotFound(pack: ExecutePack, indexNotFound: Int): String? {
        return pack.context.getString(helpRes())
    }

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? {
        return pack.context.getString(helpRes())
    }
}
