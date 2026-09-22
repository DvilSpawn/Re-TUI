package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStreamReader
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.math.abs
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.ArrayList
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.Tuils

class AliasManager(private val context: Context) {
    private var aliases: MutableList<Alias>? = null
    private val paramSeparator: String
    private val aliasLabelFormat: String
    private val replaceAllMarkers: Boolean

    private val paramMarker: String
    private val parameterPattern: Pattern

    private val receiver: BroadcastReceiver

    fun printAliases(): String {
        var output: String = Tuils.EMPTYSTRING
        for (a in aliases!!) {
            output = output + "[" + a.scope + "] " + a.name + " --> " + a.value + Tuils.NEWLINE
        }

        return output.trim { it <= ' ' }
    }

    //    [0] = aliasValue
    //    [1] = aliasName
    //    [2] = residualString
    fun getAlias(alias: String, supportSpaces: Boolean): Array<String?> {
        return getAlias(alias, supportSpaces, SCOPE_DEFAULT)
    }

    fun getAlias(alias: String, supportSpaces: Boolean, scope: String): Array<String?> {
        var alias = alias
        if (supportSpaces) {
            var args: String = Tuils.EMPTYSTRING

            var aliasValue: String? = null
            while (true) {
                aliasValue = getALias(alias, scope)
                if (aliasValue != null) break
                else {
                    val index: Int = alias.lastIndexOf(Tuils.SPACE)
                    if (index == -1) return arrayOf<String?>(null, null, alias)

                    args = alias.substring(index + 1) + Tuils.SPACE + args
                    args = args.trim { it <= ' ' }
                    alias = alias.substring(0, index)
                }
            }

            return arrayOf<String?>(aliasValue, alias, args)
        } else {
            return arrayOf<String?>(getALias(alias, scope), alias, Tuils.EMPTYSTRING)
        }
    }

    //    this prevents some errors related to the % sign
    private val SECURITY_REPLACEMENT = "{#@"
    private val securityPattern: Pattern = Pattern.compile(Pattern.quote(SECURITY_REPLACEMENT))

    fun format(aliasValue: String, params: String): String {
        var aliasValue = aliasValue
        var params = params
        params = params.trim { it <= ' ' }
        if (params.length == 0) return aliasValue

        val before = aliasValue.length
        aliasValue = parameterPattern.matcher(aliasValue).replaceAll(SECURITY_REPLACEMENT)
        val replaced =
            (aliasValue.length - before) / abs(SECURITY_REPLACEMENT.length - paramMarker.length)

        val split = params.split(Pattern.quote(paramSeparator).toRegex(), replaced.coerceAtLeast(0))
            .toTypedArray()

        for (s in split) {
            aliasValue = securityPattern.matcher(aliasValue).replaceFirst(s)
        }

        if (replaceAllMarkers) aliasValue = securityPattern.matcher(aliasValue).replaceAll(split[0])

        return aliasValue
    }

    private fun getALias(name: String): String? {
        return getALias(name, SCOPE_DEFAULT)
    }

    private fun getALias(name: String, scope: String): String? {
        var scope = scope
        scope = sanitizeScope(scope)
        for (a in aliases!!) {
            if (name == a.name && scope == a.scope) return a.value
        }

        return null
    }

    private fun removeAlias(name: String): Boolean {
        var removed = false
        for (c in aliases!!.indices.reversed()) {
            val a = aliases!!.get(c)
            if (name == a.name) {
                aliases!!.removeAt(c)
                removed = true
            }
        }

        return removed
    }

    private val pv: Pattern = Pattern.compile("%v", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pa: Pattern = Pattern.compile("%a", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    init {
        val filter: IntentFilter = IntentFilter()
        filter.addAction(ACTION_ADD)
        filter.addAction(ACTION_LS)
        filter.addAction(ACTION_RM)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val action: String? = intent.getAction()

                if (action == ACTION_ADD) {
                    add(
                        context,
                        intent.getStringExtra(NAME),
                        intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    )
                } else if (action == ACTION_RM) {
                    remove(context, intent.getStringExtra(NAME))
                } else if (action == ACTION_LS) {
                    Tuils.sendOutput(context ?: this@AliasManager.context, printAliases())
                }
            }
        }

        paramMarker = XMLPrefsManager.get(Behavior.alias_param_marker) ?: Tuils.EMPTYSTRING
        parameterPattern = Pattern.compile(Pattern.quote(paramMarker))
        paramSeparator = XMLPrefsManager.get(Behavior.alias_param_separator) ?: Tuils.EMPTYSTRING
        aliasLabelFormat = XMLPrefsManager.get(Behavior.alias_content_format) ?: Tuils.EMPTYSTRING
        replaceAllMarkers = XMLPrefsManager.getBoolean(Behavior.alias_replace_all_markers)

        reload()
    }

    fun formatLabel(aliasName: String, aliasValue: String): String {
        var a = aliasLabelFormat
        a = Tuils.patternNewline.matcher(a).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))
        a = pv.matcher(a).replaceAll(Matcher.quoteReplacement(aliasValue))
        a = pa.matcher(a).replaceAll(Matcher.quoteReplacement(aliasName))
        return a
    }

    fun reload() {
        if (aliases != null) aliases!!.clear()
        else aliases = ArrayList<Alias>()

        val root: File? = Tuils.getFolder()
        if (root == null) return

        val file = File(root, PATH)

        try {
            if (!file.exists()) file.createNewFile()

            val reader = BufferedReader(InputStreamReader(FileInputStream(file)))

            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                val splatted = line!!.split("=".toRegex(), limit = 2).toTypedArray()
                if (splatted.size < 2) continue

                var name: String
                var value: String = Tuils.EMPTYSTRING
                name = splatted[0]
                value = splatted[1]

                name = name.trim { it <= ' ' }
                value = value.trim { it <= ' ' }
                var scope: String = SCOPE_DEFAULT
                val scopeIndex: Int = name.lastIndexOf(SCOPE_SEPARATOR)
                if (scopeIndex > 0 && scopeIndex < name.length - 1) {
                    scope = sanitizeScope(name.substring(scopeIndex + 1))
                    name = name.substring(0, scopeIndex).trim { it <= ' ' }
                }

                if (name.equals(value, ignoreCase = true)) {
                    Tuils.sendOutput(
                        AppearanceSettings.errorTextColor(), context,
                        context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(
                            R.string.output_notaddingalias2
                        )
                    )
                } else if (value.startsWith(name + Tuils.SPACE)) {
                    Tuils.sendOutput(
                        AppearanceSettings.errorTextColor(), context,
                        context.getString(R.string.output_notaddingalias1) + Tuils.SPACE + name + Tuils.SPACE + context.getString(
                            R.string.output_notaddingalias3
                        )
                    )
                } else {
                    aliases!!.add(Alias(name, value, scope, parameterPattern))
                }
            }
        } catch (e: Exception) {
            Tuils.log(e)
        }
    }

    fun dispose() {
        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .unregisterReceiver(receiver)
    }

    @JvmOverloads
    fun add(context: Context?, name: String?, value: String?, scope: String? = SCOPE_DEFAULT) {
        val outputContext = context ?: this.context
        val aliasName = name ?: return
        val aliasValue = value ?: Tuils.EMPTYSTRING
        var aliasScope = scope
        aliasScope = sanitizeScope(aliasScope)
        for (a in aliases!!) {
            if (aliasName == a.name && aliasScope == a.scope) {
                Tuils.sendOutput(outputContext, R.string.unavailable_name)
                return
            }
        }

        val fos: FileOutputStream?
        try {
            fos = FileOutputStream(File(Tuils.getFolder(), PATH), true)
            fos!!.write((Tuils.NEWLINE + serializeName(aliasName, aliasScope) + "=" + aliasValue).toByteArray())
            fos.close()

            aliases!!.add(Alias(aliasName, aliasValue, aliasScope, parameterPattern))
        } catch (e: Exception) {
            Tuils.sendOutput(outputContext, e.toString())
        }
    }

    fun remove(context: Context?, name: String?) {
        val outputContext = context ?: this.context
        if (name == null) return
        reload()

        if (!removeAlias(name)) {
            Tuils.sendOutput(outputContext, R.string.invalid_name)
            return
        }

        try {
            val inputFile: File = File(Tuils.getFolder(), PATH)
            val tempFile: File = File(Tuils.getFolder(), PATH + "2")

            val reader = BufferedReader(FileReader(inputFile))
            val writer = BufferedWriter(FileWriter(tempFile))

            val prefix = name + "="
            val scopedPrefix = name + SCOPE_SEPARATOR
            var line: String?
            while ((reader.readLine().also { line = it }) != null) {
                if (line!!.startsWith(prefix)) continue
                if (line.startsWith(scopedPrefix)) continue
                writer.write(line + Tuils.NEWLINE)
            }
            writer.close()
            reader.close()

            tempFile.renameTo(inputFile)
        } catch (e: Exception) {
            Tuils.sendOutput(outputContext, e.toString())
        }
    }

    fun getAliases(excludeEmtpy: Boolean): MutableList<Alias?> {
        return getAliases(excludeEmtpy, null)
    }

    fun getAliases(excludeEmtpy: Boolean, scope: String?): MutableList<Alias?> {
        var scope = scope
        scope = if (scope == null) null else sanitizeScope(scope)
        val l: MutableList<Alias?> = ArrayList<Alias?>(aliases)
        if (scope != null) {
            for (c in l.indices.reversed()) {
                if (scope != l.get(c)!!.scope) {
                    l.removeAt(c)
                }
            }
        }

        if (excludeEmtpy) {
            for (c in l.indices) {
                if (l.get(c)!!.name.length == 0) {
                    l.removeAt(c)
                    break
                }
            }
        }

        return l
    }

    private fun sanitizeScope(scope: String?): String {
        var scope = scope
        if (scope == null || scope.trim { it <= ' ' }.length == 0) {
            return SCOPE_DEFAULT
        }

        scope = scope.trim { it <= ' ' }.lowercase(Locale.getDefault())
        if (scope.startsWith(Tuils.MINUS)) {
            scope = scope.substring(1)
        }

        if (SCOPE_SCRIPT == scope) {
            return SCOPE_SCRIPT
        }

        return SCOPE_APP
    }

    private fun serializeName(name: String?, scope: String?): String? {
        var scope = scope
        scope = sanitizeScope(scope)
        if (SCOPE_DEFAULT == scope) {
            return name
        }

        return name + SCOPE_SEPARATOR + scope
    }

    class Alias(var name: String, var value: String, scope: String?, parameterPattern: Pattern) {
        var scope: String
        var isParametrized: Boolean

        constructor(name: String, value: String, parameterPattern: Pattern) : this(
            name,
            value,
            SCOPE_DEFAULT,
            parameterPattern
        )

        init {
            this.scope = if (scope == null) SCOPE_DEFAULT else scope

            isParametrized = parameterPattern.matcher(value).find()
        }

        override fun equals(obj: Any?): Boolean {
            return (obj is Alias && obj.name == name) || obj == name
        }
    }

    companion object {
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".alias_ls"
        var ACTION_ADD: String = BuildConfig.APPLICATION_ID + ".alias_add"
        var ACTION_RM: String = BuildConfig.APPLICATION_ID + ".alias_rm"

        var NAME: String = "name"

        const val PATH: String = "alias.txt"
        const val SCOPE_APP: String = "a"
        const val SCOPE_SCRIPT: String = "s"
        val SCOPE_DEFAULT: String = SCOPE_APP
        private const val SCOPE_SEPARATOR = "|"
    }
}
