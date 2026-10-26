package ohi.andre.consolelauncher.commands.main.raw

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.SearchProviderManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Cmd
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.AbstractMap.SimpleEntry
import java.util.Locale
import java.io.File
import java.util.ArrayList

class search : ParamCommand() {
    private enum class Param : ohi.andre.consolelauncher.commands.main.Param {
        add {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String?>()
                if (args.size < 2) {
                    return usageAdd(pack.context)
                }

                val name = args.get(0) ?: return usageAdd(pack.context)
                if (isReserved(name)) {
                    return pack.context.getString(R.string.command_search_search_param_is_reserved_d1d4f, name)
                }

                val appIndex = args.indexOfFirst { APP_FLAG.equals(it, ignoreCase = true) }
                val appName = if (appIndex < 0) null else Tuils.toPlanString(args.subList(appIndex + 1, args.size), Tuils.SPACE)
                val templateArgs = if (appIndex < 0) args else args.subList(0, appIndex)
                if (templateArgs.size < 2) return usageAdd(pack.context)

                val template = templateArgs.get(1) ?: return usageAdd(pack.context)
                var fallback: String? = null
                if (templateArgs.size > 2) {
                    var rest = templateArgs.subList(2, templateArgs.size)
                    if (!rest.isEmpty() && "=>" == rest.get(0)) {
                        rest = rest.subList(1, rest.size)
                    }
                    fallback = Tuils.toPlanString(rest, Tuils.SPACE)
                }

                var app: String? = null
                if (appName != null) {
                    app = when (val match = matchApp(pack, appName)) {
                        is AppMatch.Found -> match.app
                        is AppMatch.Problem -> return match.message
                    }
                }

                val saved = SearchProviderManager.add(name, template, fallback, app)
                if (!saved) return usageAdd(pack.context)
                val provider = SearchProviderManager.get(name) ?: return usageAdd(pack.context)
                return savedMessage(pack.context, provider)
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        rm {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String?>()
                if (args.isEmpty()) return pack.context.getString(R.string.command_search_usage_search_rm_param_6eab8)

                val name = args.get(0) ?: return pack.context.getString(R.string.command_search_usage_search_rm_param_6eab8)
                if (isReserved(name)) {
                    return pack.context.getString(R.string.command_search_search_param_is_reserved_d1d4f, name)
                }

                val removed = SearchProviderManager.remove(name)
                return if (removed)
                    pack.context.getString(R.string.command_search_search_provider_removed_1f35a, stripParam(name))
                else
                    pack.context.getString(R.string.command_search_search_provider_not_found_7169f, stripParam(name))
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        app {
            override fun exec(pack: ExecutePack): String? {
                val args = pack.getList<String?>()
                val usage = pack.context.getString(R.string.command_search_usage_search_app)
                if (args.size < 2) return usage

                val provider = SearchProviderManager.get(args.get(0))
                    ?: return pack.context.getString(R.string.command_search_search_provider_not_found_7169f, stripParam(args.get(0)))
                val appName = Tuils.toPlanString(args.subList(1, args.size), Tuils.SPACE)

                if (AUTO.equals(appName.trim(), ignoreCase = true)) {
                    SearchProviderManager.setApp(provider.name, null)
                    return pack.context.getString(R.string.command_search_provider_app_auto, provider.name)
                }

                val app = when (val match = matchApp(pack, appName)) {
                    is AppMatch.Found -> match.app
                    is AppMatch.Problem -> return match.message
                }
                SearchProviderManager.setApp(provider.name, app)
                return pack.context.getString(
                    R.string.command_search_provider_app_set,
                    provider.name,
                    appDisplayName(pack.context, app)
                )
            }

            override fun args(): IntArray? {
                return intArrayOf(CommandAbstraction.TEXTLIST)
            }
        },
        ls {
            override fun exec(pack: ExecutePack): String {
                return listProviders(pack.context)
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        },
        file {
            override fun exec(pack: ExecutePack): String? {
                val file = SearchProviderManager.file()
                pack.context.startActivity(Tuils.openFile(pack.context, file))
                return null
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        },
        reset {
            override fun exec(pack: ExecutePack): String? {
                val reset = SearchProviderManager.reset()
                return if (reset) pack.context.getString(R.string.command_search_search_providers_reset_1120e) else pack.context.getString(R.string.command_search_unable_to_reset_search_providers_8d59c)
            }

            override fun args(): IntArray? {
                return IntArray(0)
            }
        };

        override fun label(): String? {
            return Tuils.MINUS + name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String {
            return pack.context.getString(R.string.help_search)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }

        companion object {
            fun get(p: String?): Param? {
                var p = p
                p = stripParam(p)
                if (p == null) return null

                for (param in entries) {
                    if (p == param.name) {
                        return param
                    }
                }

                return null
            }

            fun labels(): Array<String?> {
                val ps: Array<Param?> = entries.toTypedArray()
                val labels = arrayOfNulls<String>(ps.size)
                for (i in ps.indices) {
                    labels[i] = ps[i]!!.label()
                }
                return labels
            }
        }
    }

    private class SearchProviderParam(private val provider: SearchProviderManager.Provider) :
        ohi.andre.consolelauncher.commands.main.Param {
        override fun args(): IntArray {
            return intArrayOf(CommandAbstraction.TEXTLIST)
        }

        override fun exec(pack: ExecutePack): String {
            val args = pack.getList<String?>()
            val query = Tuils.toPlanString(args, Tuils.SPACE)
            return open(provider, query, pack)
        }

        override fun label(): String {
            return Tuils.MINUS + provider.name
        }

        override fun onNotArgEnough(pack: ExecutePack, n: Int): String {
            return pack.context.getString(R.string.help_search)
        }

        override fun onArgNotFound(pack: ExecutePack, index: Int): String? {
            return null
        }
    }

    public override fun getParam(
        pack: MainPack,
        param: String
    ): SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?> {
        val managementParam: ohi.andre.consolelauncher.commands.main.Param? =
            Param.Companion.get(param)
        if (managementParam != null) {
            return SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?>(
                false,
                managementParam
            )
        }

        val provider = SearchProviderManager.get(param)
        if (provider != null) {
            return SimpleEntry<Boolean, ohi.andre.consolelauncher.commands.main.Param?>(
                false,
                SearchProviderParam(provider)
            )
        }

        return super.getParam(pack, param)
    }

    override fun paramForString(
        pack: MainPack,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        val managementParam: ohi.andre.consolelauncher.commands.main.Param? =
            Param.Companion.get(param)
        if (managementParam != null) return managementParam

        val provider = SearchProviderManager.get(param)
        return if (provider == null) null else SearchProviderParam(provider)
    }

    public override fun defaultParamReference(): XMLPrefsSave? {
        return Cmd.default_search
    }

    override fun doThings(pack: ExecutePack): String? {
        if (pack !is MainPack) return null

        val input = pack.lastCommand
        val split = Tuils.splitArgs(input)
        if (split.size < 2) {
            return (pack.context.getString(R.string.help_search)
                    + Tuils.NEWLINE
                    + Tuils.NEWLINE
                    + listProviders(pack.context))
        }

        return null
    }

    public override fun params(): Array<String?> {
        return SearchProviderManager.labelsWith(Param.Companion.labels())
    }

    override fun helpRes(): Int {
        return R.string.help_search
    }

    override fun priority(): Int {
        return 4
    }

    companion object {
        fun playstoreSearch(query: String?, c: Context): String {
            var provider = SearchProviderManager.get("ps")
            if (provider == null) {
                provider = SearchProviderManager.Provider(
                    "ps",
                    "market://search?q={query}",
                    "https://play.google.com/store/search?q={query}&c=apps"
                )
            }

            return openProvider(provider, query, c)
        }

        private fun open(
            provider: SearchProviderManager.Provider,
            query: String?,
            pack: ExecutePack
        ): String {
            return openProvider(provider, query, pack.context)
        }

        internal fun openProvider(
            provider: SearchProviderManager.Provider,
            query: String?,
            context: Context
        ): String {
            try {
                if (start(provider.render(query), provider.app, context)) return Tuils.EMPTYSTRING
            } catch (firstError: Exception) {
                if (provider.fallbackTemplate == null) return firstError.toString()
            }

            val fallback = provider.renderFallback(query)
                ?: return context.getString(R.string.command_search_provider_unavailable, provider.name)
            try {
                if (start(fallback, provider.app, context)) return Tuils.EMPTYSTRING
            } catch (secondError: Exception) {
                return secondError.toString()
            }
            return context.getString(R.string.command_search_provider_unavailable, provider.name)
        }

        /**
         * Opens [rendered] in the pinned app, else the one app that can open it, else the browser.
         * Returns false when nothing can handle it, so the caller can try the fallback template.
         */
        private fun start(rendered: String, app: String?, context: Context): Boolean {
            val pm = context.packageManager
            val intents = if (rendered.startsWith(SearchProviderManager.WEB_SEARCH)) {
                val webSearch = Intent(Intent.ACTION_WEB_SEARCH)
                    .putExtra(SearchManager.QUERY, rendered.substring(SearchProviderManager.WEB_SEARCH.length))
                // Unpinned web searches would show a chooser; let the fallback URL run instead.
                if (app == null || app == SearchProviderManager.BROWSER) emptyList()
                else listOf(webSearch.setPackage(app))
            } else {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(rendered))
                val target = when (app) {
                    null -> SearchProviderManager.detectedApp(context, rendered)
                    SearchProviderManager.BROWSER -> SearchProviderManager.defaultBrowser(context)
                    else -> app
                }
                listOfNotNull(target?.let { Intent(view).setPackage(it) }, view)
            }

            val intent = intents.firstOrNull { it.resolveActivity(pm) != null } ?: return false
            context.startActivity(intent)
            return true
        }

        private sealed class AppMatch {
            class Found(val app: String) : AppMatch()
            class Problem(val message: String) : AppMatch()
        }

        /** Resolves a typed app name (or "browser") to a package, so users never type packages. */
        private fun matchApp(pack: ExecutePack, typed: String): AppMatch {
            val name = typed.trim()
            if (SearchProviderManager.BROWSER.equals(name, ignoreCase = true)) {
                return AppMatch.Found(SearchProviderManager.BROWSER)
            }

            val apps = (pack as? MainPack)?.appsManager?.shownApps().orEmpty()
                .filter { it.publicLabel != null && it.componentName != null }
            val exact = apps.filter { it.publicLabel.equals(name, ignoreCase = true) }
            val matches = exact.ifEmpty {
                apps.filter { it.publicLabel!!.startsWith(name, ignoreCase = true) }
            }.ifEmpty {
                apps.filter { it.publicLabel!!.contains(name, ignoreCase = true) }
            }.distinctBy { it.componentName!!.packageName }

            return when (matches.size) {
                1 -> AppMatch.Found(matches[0].componentName!!.packageName)
                0 -> AppMatch.Problem(pack.context.getString(R.string.command_search_app_not_found, name))
                else -> AppMatch.Problem(
                    pack.context.getString(
                        R.string.command_search_app_ambiguous,
                        name,
                        matches.joinToString(", ") { it.publicLabel!! }
                    )
                )
            }
        }

        private fun savedMessage(context: Context, provider: SearchProviderManager.Provider): String {
            provider.app?.let {
                return context.getString(R.string.command_search_provider_saved_app, provider.name, appDisplayName(context, it))
            }
            val apps = SearchProviderManager.appsFor(context, provider.render("test"))
            return when (apps.size) {
                0 -> context.getString(R.string.command_search_provider_saved_browser, provider.name)
                1 -> context.getString(R.string.command_search_provider_saved_app, provider.name, appDisplayName(context, apps[0]))
                else -> context.getString(
                    R.string.command_search_provider_saved_several,
                    provider.name,
                    apps.joinToString(", ") { appDisplayName(context, it) }
                )
            }
        }

        private fun appDisplayName(context: Context, app: String): String =
            if (app == SearchProviderManager.BROWSER) context.getString(R.string.command_search_your_browser)
            else SearchProviderManager.appLabel(context, app) ?: context.getString(R.string.command_search_app_not_installed, app)

        /** Where a provider opens on this phone, for -ls. */
        private fun opensIn(context: Context, provider: SearchProviderManager.Provider): String {
            val app = provider.app
                ?: SearchProviderManager.detectedApp(context, provider.render("test"))
                ?: return context.getString(R.string.command_search_your_browser)
            return appDisplayName(context, app)
        }

        /** Candidate apps for a provider's link, used by suggestions after "search -app [provider]". */
        internal fun appChoices(context: Context, providerName: String?): List<String> {
            val provider = SearchProviderManager.get(providerName) ?: return emptyList()
            return SearchProviderManager.appsFor(context, provider.render("test"))
                .mapNotNull { SearchProviderManager.appLabel(context, it) }
        }

        private const val APP_FLAG = "-app"
        internal const val AUTO = "auto"

        private fun listProviders(context: android.content.Context): String {
            val providers = SearchProviderManager.load()
            if (providers.isEmpty()) return context.getString(R.string.command_detail_search_no_search_providers_configured_f92f3)

            val output = StringBuilder(context.getString(R.string.command_detail_search_search_providers_a77f9))
            for (provider in providers) {
                output.append(Tuils.NEWLINE)
                    .append(Tuils.MINUS)
                    .append(provider.name)
                    .append(" -> ")
                    .append(provider.template)
                if (provider.fallbackTemplate != null) {
                    output.append(" => ").append(provider.fallbackTemplate)
                }
                output.append(context.getString(R.string.command_search_opens_in, opensIn(context, provider)))
            }

            return output.toString()
        }

        private fun usageAdd(context: android.content.Context): String {
            return (context.getString(R.string.command_detail_search_usage_search_add_param_url_template_exampl_59bc0, Tuils.NEWLINE))
        }

        private fun isReserved(param: String?): Boolean {
            return Param.Companion.get(param) != null
        }

        private fun stripParam(param: String?): String? {
            var param = param ?: return null

            param = param.trim { it <= ' ' }.lowercase(Locale.ROOT)
            while (param.startsWith(Tuils.MINUS)) {
                param = param.substring(1)
            }

            return if (param.length == 0) null else param
        }
    }
}
