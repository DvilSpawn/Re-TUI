package ohi.andre.consolelauncher.commands.main.raw

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

                val template = args.get(1) ?: return usageAdd(pack.context)
                var fallback: String? = null
                if (args.size > 2) {
                    var rest = args.subList(2, args.size)
                    if (!rest.isEmpty() && "=>" == rest.get(0)) {
                        rest = rest.subList(1, rest.size)
                    }
                    fallback = Tuils.toPlanString(rest, Tuils.SPACE)
                }

                val saved = SearchProviderManager.add(name, template, fallback)
                return if (saved)
                    pack.context.getString(R.string.command_search_search_provider_saved_c4c69, stripParam(name))
                else
                    usageAdd(pack.context)
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
                start(provider.render(query), context)
            } catch (firstError: Exception) {
                val fallback = provider.renderFallback(query)
                if (fallback == null) {
                    return firstError.toString()
                }

                try {
                    start(fallback, context)
                } catch (secondError: Exception) {
                    return secondError.toString()
                }
            }

            return Tuils.EMPTYSTRING
        }

        private fun start(rendered: String?, context: Context) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rendered))
            context.startActivity(intent)
        }

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
