package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Locale
import java.util.ArrayList

object SearchProviderManager {
    const val PATH: String = "search.txt"
    private const val ASSIGN = "="
    private const val FALLBACK = "=>"
    private const val COMMENT = "#"
    private const val APP_PREFIX = "@"

    /** Template prefix that sends Android's web search request instead of opening a URL. */
    const val WEB_SEARCH = "websearch:"

    /** Pinned app value that always opens the provider in the default browser. */
    const val BROWSER = "browser"

    /** Written once the version 2 defaults have been merged into an existing file. */
    internal const val DEFAULTS_MARKER = "# defaults: 2"

    private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
    private const val LEGACY_GOOGLE = "https://www.google.com/search?q={query}"
    private const val PROBE_URL = "https://www.example.com/"

    fun get(name: String?): Provider? {
        val normalized = normalizeName(name)
        if (normalized == null) return null

        for (provider in load()) {
            if (provider.name.equals(normalized, ignoreCase = true)) {
                return provider
            }
        }

        return null
    }

    fun load(): MutableList<Provider> {
        ensureFile()
        val lines = ArrayList<String>()

        try {
            val reader = BufferedReader(FileReader(file()))
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                lines.add(line!!)
            }
            reader.close()
        } catch (e: Exception) {
            Tuils.log(e)
        }

        val providers = lines.mapNotNullTo(ArrayList<Provider>()) { parse(it) }
        if (lines.none { it.trim() == DEFAULTS_MARKER }) {
            val upgraded = upgradeDefaults(providers)
            write(upgraded)
            return upgraded
        }
        return providers
    }

    @JvmOverloads
    fun add(name: String?, template: String, fallbackTemplate: String? = null, app: String? = null): Boolean {
        val normalized = normalizeName(name)
        if (normalized == null || !validTemplate(template) || !validApp(app)) {
            return false
        }

        val providers = load()
        for (i in providers.indices.reversed()) {
            if (providers.get(i).name.equals(normalized, ignoreCase = true)) {
                providers.removeAt(i)
            }
        }

        providers.add(Provider(normalized, template.trim { it <= ' ' }, clean(fallbackTemplate), clean(app)))
        return write(providers)
    }

    /** Pins [app] (a package or [BROWSER]) to a provider; null returns it to automatic detection. */
    fun setApp(name: String?, app: String?): Boolean {
        val normalized = normalizeName(name) ?: return false
        if (!validApp(app)) return false

        val providers = load()
        val index = providers.indexOfFirst { it.name.equals(normalized, ignoreCase = true) }
        if (index < 0) return false

        val provider = providers[index]
        providers[index] = Provider(provider.name, provider.template, provider.fallbackTemplate, clean(app))
        return write(providers)
    }

    fun remove(name: String?): Boolean {
        val normalized = normalizeName(name)
        if (normalized == null) return false

        val providers = load()
        var removed = false
        for (i in providers.indices.reversed()) {
            if (providers.get(i).name.equals(normalized, ignoreCase = true)) {
                providers.removeAt(i)
                removed = true
            }
        }

        return removed && write(providers)
    }

    fun reset(): Boolean {
        return write(defaultProviders())
    }

    fun file(): File {
        return File(Tuils.getFolder(), PATH)
    }

    fun labelsWith(baseLabels: Array<String?>): Array<String?> {
        val providers = load()
        val labels = arrayOfNulls<String>(baseLabels.size + providers.size)
        System.arraycopy(baseLabels, 0, labels, 0, baseLabels.size)

        for (i in providers.indices) {
            labels[baseLabels.size + i] = Tuils.MINUS + providers.get(i).name
        }

        return labels
    }

    /** Installed apps, other than browsers, that can open [url]. */
    fun appsFor(context: Context, url: String): List<String> {
        if (url.startsWith(WEB_SEARCH)) return emptyList()
        val uri = Uri.parse(url)
        if (uri.scheme == null) return emptyList()

        val browsers = handlers(context, Uri.parse(PROBE_URL))
        return handlers(context, uri).filter { it !in browsers && it != context.packageName }
    }

    /** The app a URL opens in when nothing is pinned: the only non-browser app that can open it. */
    fun detectedApp(context: Context, url: String): String? = appsFor(context, url).singleOrNull()

    /** The user's default browser, or null when Android would ask. */
    fun defaultBrowser(context: Context): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROBE_URL)).addCategory(Intent.CATEGORY_BROWSABLE)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName ?: return null
        return if (resolved == "android" || resolved.contains("resolver")) null else resolved
    }

    fun appLabel(context: Context, packageName: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    private fun handlers(context: Context, uri: Uri): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return try {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .mapTo(LinkedHashSet()) { it.activityInfo.packageName }
        } catch (e: Exception) {
            Tuils.log(e)
            emptySet()
        }
    }

    private fun ensureFile() {
        try {
            val file = file()
            if (!file.exists() || file.length() == 0L) {
                write(defaultProviders())
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    internal fun parse(rawLine: String?): Provider? {
        if (rawLine == null) return null

        val line = rawLine.trim { it <= ' ' }
        if (line.length == 0 || line.startsWith(COMMENT)) return null

        val pair: Array<String?> = line.split(ASSIGN.toRegex(), limit = 2).toTypedArray()
        if (pair.size != 2) return null

        val name = normalizeName(pair[0])
        var value = pair[1]!!.trim { it <= ' ' }
        var fallback: String? = null
        var app: String? = null

        if (value.startsWith(APP_PREFIX)) {
            val end = value.indexOfFirst { it.isWhitespace() }
            if (end < 0) return null
            app = value.substring(APP_PREFIX.length, end)
            value = value.substring(end).trim { it <= ' ' }
            if (!validApp(app)) return null
        }

        val fallbackIndex = value.indexOf(FALLBACK)
        if (fallbackIndex >= 0) {
            fallback = value.substring(fallbackIndex + FALLBACK.length).trim { it <= ' ' }
            value = value.substring(0, fallbackIndex).trim { it <= ' ' }
        }

        if (name == null || !validTemplate(value)) {
            return null
        }

        return Provider(name, value, clean(fallback), app)
    }

    internal fun serialize(provider: Provider): String {
        val builder = StringBuilder(provider.name).append(ASSIGN)
        if (provider.app != null) builder.append(APP_PREFIX).append(provider.app).append(' ')
        builder.append(provider.template)
        if (provider.fallbackTemplate != null) {
            builder.append(' ').append(FALLBACK).append(' ').append(provider.fallbackTemplate)
        }
        return builder.toString()
    }

    /**
     * Brings a pre-version-2 file up to date once: an untouched Google provider switches to the
     * Google app, and providers new in version 2 are added unless the name is already taken.
     */
    internal fun upgradeDefaults(providers: MutableList<Provider>): MutableList<Provider> {
        val upgraded = providers.mapTo(ArrayList<Provider>()) { provider ->
            if (provider.name == "gg" && provider.template == LEGACY_GOOGLE &&
                provider.fallbackTemplate == null && provider.app == null
            ) googleProvider() else provider
        }
        val names = upgraded.mapTo(HashSet()) { it.name }
        defaultProviders().filter { it.name in ADDED_IN_V2 && it.name !in names }.forEach(upgraded::add)
        return upgraded
    }

    private val ADDED_IN_V2 = setOf("mp", "wp", "rd", "gh", "tr", "ym")

    private fun normalizeName(name: String?): String? {
        var name = name
        if (name == null) return null

        name = name.trim { it <= ' ' }.lowercase(Locale.getDefault())
        while (name!!.startsWith(Tuils.MINUS)) {
            name = name.substring(1)
        }

        if (name.length == 0) return null

        for (i in 0..<name.length) {
            val c = name.get(i)
            val valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-'
            if (!valid) return null
        }

        return name
    }

    private fun validApp(app: String?): Boolean {
        val value = clean(app) ?: return true
        return value == BROWSER || value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))
    }

    private fun validTemplate(template: String?): Boolean {
        var template = template
        template = clean(template)
        return template != null && containsToken(template)
    }

    private fun containsToken(value: String): Boolean {
        return value.contains("{query}")
                || value.contains("{query+}")
                || value.contains("{slug}")
                || value.contains("{raw}")
                || value.contains("{url}")
    }

    private fun clean(value: String?): String? {
        var value = value
        if (value == null) return null
        value = value.trim { it <= ' ' }
        return if (value.length == 0) null else value
    }

    private fun write(providers: MutableList<Provider>): Boolean {
        try {
            val file = file()
            val writer = BufferedWriter(FileWriter(file, false))
            writer.write("# Re:T-UI search providers")
            writer.newLine()
            writer.write("# Format: name=url_template")
            writer.newLine()
            writer.write("# Optional fallback: name=primary_template => fallback_template")
            writer.newLine()
            writer.write("# Optional app: name=@package url_template (or @browser). Without it, the one app that can open the link is used.")
            writer.newLine()
            writer.write("# Tokens: {query}=URL encoded, {query+}=plus encoded, {slug}=spaces_to_underscores, {raw}=unchanged, {url}=open URL")
            writer.newLine()
            writer.write("# websearch:{raw} sends a web search to the app instead of opening a URL")
            writer.newLine()
            writer.write(DEFAULTS_MARKER)
            writer.newLine()
            for (provider in providers) {
                writer.write(serialize(provider))
                writer.newLine()
            }
            writer.close()
            return true
        } catch (e: Exception) {
            Tuils.log(e)
            return false
        }
    }

    private fun googleProvider() =
        Provider("gg", WEB_SEARCH + "{raw}", LEGACY_GOOGLE, GOOGLE_APP)

    internal fun defaultProviders(): MutableList<Provider> {
        val providers: MutableList<Provider> = ArrayList<Provider>()
        providers.add(googleProvider())
        providers.add(
            Provider(
                "ps",
                "market://search?q={query}",
                "https://play.google.com/store/search?q={query}&c=apps"
            )
        )
        providers.add(Provider("yt", "https://www.youtube.com/results?search_query={query+}", null))
        providers.add(Provider("dd", "https://duckduckgo.com/?q={query}", null))
        providers.add(Provider("u", "{url}", null))
        providers.add(Provider("mp", "https://www.google.com/maps/search/?api=1&query={query}", null))
        providers.add(Provider("wp", "https://en.wikipedia.org/w/index.php?search={query}", null))
        providers.add(Provider("rd", "https://www.reddit.com/search/?q={query}", null))
        providers.add(Provider("gh", "https://github.com/search?q={query}", null))
        providers.add(Provider("tr", "https://translate.google.com/?sl=auto&text={query}", null))
        providers.add(Provider("ym", "https://music.youtube.com/search?q={query}", null))
        return providers
    }

    class Provider @JvmOverloads constructor(
        val name: String,
        val template: String,
        val fallbackTemplate: String?,
        /** A package, [BROWSER], or null to detect the app from the link. */
        val app: String? = null
    ) {
        fun render(query: String?): String {
            return renderTemplate(template, query)
        }

        fun renderFallback(query: String?): String? {
            if (fallbackTemplate == null) return null
            return renderTemplate(fallbackTemplate, query)
        }

        companion object {
            private fun renderTemplate(template: String, query: String?): String {
                val raw = if (query == null) Tuils.EMPTYSTRING else query.trim { it <= ' ' }
                val encoded = Uri.encode(raw)
                val plus = encoded.replace("%20", "+")
                val slug = Uri.encode(raw.replace("\\s+".toRegex(), "_"))
                var url = raw

                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "https://" + url
                }

                return template
                    .replace("{query+}", plus)
                    .replace("{query}", encoded)
                    .replace("{slug}", slug)
                    .replace("{raw}", raw)
                    .replace("{url}", url)
            }
        }
    }
}
