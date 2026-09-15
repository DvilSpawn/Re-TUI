package ohi.andre.consolelauncher.localization

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.lang.ref.WeakReference
import java.util.Locale
import java.util.WeakHashMap

/** Installed files and selection belong only to Launcher; packs contain no executable code. */
object LanguagePacks {
    private lateinit var storage: Context
    private lateinit var catalog: JSONObject
    private var selected = ""
    var active: LanguagePack? = null
        private set
    var generation = 0
        private set
    private val wrappers = WeakHashMap<Resources, WeakReference<PackResources>>()
    private val ready get() = ::storage.isInitialized && ::catalog.isInitialized
    private fun directory() = File(storage.filesDir, "launcher-language-packs")
    private fun file(id: String) = File(directory(), "$id.retui-launcher-lang")
    private fun preferences() = storage.getSharedPreferences("launcher-language", Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        if (ready) return
        storage = context
        catalog = JSONObject(context.assets.open("localization/catalog.json").bufferedReader().use { it.readText() })
        selected = preferences().getString("active", "").orEmpty()
        active = installed().firstOrNull { it.id == selected }
        if (selected !in setOf("", "en") && active == null) {
            selected = "en"
            preferences().edit().putString("active", selected).commit()
        }
        locale()?.let(Locale::setDefault)
    }

    fun selectedId() = selected
    fun locale(): Locale? = active?.let { Locale.forLanguageTag(it.languageTag) }
        ?: if (selected == "en") Locale.ENGLISH else null

    fun installed(): List<LanguagePack> = directory().list().orEmpty()
        .map { it.removeSuffix(".bak") }.filter { it.endsWith(".retui-launcher-lang") }.distinct()
        .mapNotNull { name -> runCatching {
            AtomicFile(File(directory(), name)).openRead().use { LanguagePackArchive.parse(LanguagePackArchive.bounded(it), catalog) }
        }.getOrNull() }.sortedBy { it.nativeName.lowercase(Locale.ROOT) }

    @Synchronized
    fun import(input: InputStream): LanguagePack {
        val bytes = LanguagePackArchive.bounded(input)
        val pack = LanguagePackArchive.parse(bytes, catalog)
        val existing = installed().firstOrNull { it.id == pack.id }
        require(existing == null || pack.version >= existing.version) { "Older pack version" }
        check(directory().isDirectory || directory().mkdirs())
        val target = AtomicFile(file(pack.id))
        val out = target.startWrite()
        try { out.write(bytes); target.finishWrite(out) }
        catch (error: Exception) { target.failWrite(out); throw error }
        if (selected == pack.id) select(pack.id)
        return pack
    }

    @Synchronized
    fun select(id: String) {
        val pack = if (id in setOf("", "en")) null else requireNotNull(installed().firstOrNull { it.id == id })
        check(preferences().edit().putString("active", id).commit())
        selected = id
        active = pack
        Locale.setDefault(locale() ?: Resources.getSystem().configuration.let {
            @Suppress("DEPRECATION") it.locale
        })
        generation++
        synchronized(wrappers) { wrappers.clear() }
    }

    @Synchronized
    fun remove(id: String) {
        require(installed().any { it.id == id })
        if (selected == id) select("en")
        check(file(id).delete())
    }

    private fun configure(configuration: Configuration) {
        locale()?.let { configuration.setLocale(it); configuration.setLayoutDirection(it) }
        active?.let {
            configuration.screenLayout = (configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK.inv()) or
                if (it.rtl) Configuration.SCREENLAYOUT_LAYOUTDIR_RTL else Configuration.SCREENLAYOUT_LAYOUTDIR_LTR
        }
    }

    fun wrap(context: Context): Context {
        if (!ready) return context
        val configuration = Configuration(context.resources.configuration)
        configure(configuration)
        val configured = context.createConfigurationContext(configuration)
        return object : ContextWrapper(configured) {
            override fun getResources(): Resources = resources(super.getResources())
        }
    }

    fun resources(base: Resources): Resources {
        if (!ready) return base
        if (base is PackResources) return if (base.createdGeneration == generation) base else resources(base.original)
        return synchronized(wrappers) {
            wrappers[base]?.get() ?: run {
                val config = Configuration(base.configuration)
                configure(config)
                val englishConfig = Configuration(config).apply { setLocale(Locale.ENGLISH) }
                val english = storage.createConfigurationContext(englishConfig).resources
                PackResources(base, config, english).also { wrappers[base] = WeakReference(it) }
            }
        }
    }

    fun definition(key: String): JSONObject? = if (ready) catalog.optJSONObject(key) else null
}

@Suppress("DEPRECATION")
private class PackResources(val original: Resources, config: Configuration, private val english: Resources) :
    Resources(original.assets, original.displayMetrics, config) {
    val createdGeneration = LanguagePacks.generation
    private fun key(id: Int) = runCatching { getResourceEntryName(id) }.getOrNull()
    private fun own(id: Int): String? = key(id)?.takeIf { LanguagePacks.definition(it) != null }
    override fun getText(id: Int): CharSequence {
        val key = own(id)
        if (key != null && LanguagePacks.active != null) {
            return LanguagePacks.active?.texts?.get(key)?.forms?.get("other") ?: english.getText(id)
        }
        return super.getText(id)
    }
    override fun getText(id: Int, def: CharSequence?): CharSequence = try { getText(id) } catch (_: NotFoundException) { def ?: "" }
    override fun getString(id: Int): String = getText(id).toString()
    override fun getString(id: Int, vararg formatArgs: Any?): String = String.format(configuration.locale, getString(id), *formatArgs)
    override fun getQuantityText(id: Int, quantity: Int): CharSequence {
        val key = own(id)
        if (key != null && LanguagePacks.active != null) {
            val forms = LanguagePacks.active?.texts?.get(key)?.forms
            if (forms != null) {
                val category = super.getQuantityText(ohi.andre.consolelauncher.R.plurals.language_pack_quantity, quantity).toString()
                return forms[category] ?: forms.getValue("other")
            }
            return english.getQuantityText(id, quantity)
        }
        return super.getQuantityText(id, quantity)
    }
    override fun getQuantityString(id: Int, quantity: Int): String = getQuantityText(id, quantity).toString()
    override fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String =
        String.format(configuration.locale, getQuantityString(id, quantity), *formatArgs)
}
