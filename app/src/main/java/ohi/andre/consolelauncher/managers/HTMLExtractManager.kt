package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.SAXParseException
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.jayway.jsonpath.JsonPath
import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.TagNode
import org.jsoup.Jsoup
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicBoolean
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Response
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.resetFile
import ohi.andre.consolelauncher.managers.status.WeatherResponseParser

/**
 * Created by francescoandreuzzi on 29/03/2018.
 */
class HTMLExtractManager(context: Context, client: OkHttpClient) {
    private val xpaths: MutableList<StoreableValue>
    private val jsons: MutableList<StoreableValue>
    private val formats: MutableList<StoreableValue>

    private val client: OkHttpClient
    private val receiver: BroadcastReceiver

    var defaultFormat: String = Tuils.EMPTYSTRING
    var weatherFormat: String = Tuils.EMPTYSTRING
    var weatherColor: Int = Color.WHITE

    private fun getListFromType(t: StoreableValue.Type?): MutableList<StoreableValue> {
        if (t == StoreableValue.Type.xpath) return xpaths
        else if (t == StoreableValue.Type.json) return jsons
        else return formats
    }

    fun dispose(context: Context) {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
    }

    var weatherFormatPattern: Pattern =
        Pattern.compile("%([a-z_]+)(\\d)*(?:\\$\\(([\\.\\+\\-\\*\\/\\^\\d]+)\\))?")

    private fun query(
        context: Context,
        path: String?,
        pathType: StoreableValue.Type?,
        format: String?,
        url: String,
        weatherArea: Boolean
    ) {
        if (weatherArea && !weatherQueryInFlight.compareAndSet(false, true)) return
        object : Thread() {
            override fun run() {
                super.run()

                if (!Tuils.hasInternetAccess()) {
                    Companion.output(R.string.no_internet, context, weatherArea)
                    if (weatherArea) weatherQueryInFlight.set(false)
                    return
                }

                try {
                    val builder = Request.Builder()
                        .url(url)
                        .addHeader(
                            "User-Agent",
                            if (weatherArea) {
                                "ReTUI-Launcher/${BuildConfig.VERSION_NAME} github.com/DvilSpawn/Re-TUI"
                            } else {
                                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1"
                            }
                        )
                        .get()
                    if (!weatherArea) builder.cacheControl(CacheControl.Companion.FORCE_NETWORK)

                    client.newCall(builder.build()).execute().use { response ->
                        if (response.code == 429 && weatherArea) {
                            val i: Intent = Intent(UIManager.ACTION_WEATHER_DELAY)
                            LocalBroadcastManager.getInstance(context.getApplicationContext())
                                .sendBroadcast(i)

                            return
                        } else if (!response.isSuccessful) {
                            val message =
                                context.getString(R.string.internet_error) + Tuils.SPACE + response.code

                            if (weatherArea) {
                                val i: Intent = Intent(UIManager.ACTION_WEATHER)
                                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, message)
                                LocalBroadcastManager.getInstance(context.getApplicationContext())
                                    .sendBroadcast(i)
                            } else {
                                output(message, context, false)
                            }

                            return
                        }
                        val body = response.body
                        if (body == null) {
                            Companion.output(R.string.no_result, context, weatherArea)
                            return
                        }

                        val inputStream: InputStream = body.byteStream()

                        var output: CharSequence? = Tuils.span(Tuils.EMPTYSTRING, outputColor) ?: SpannableString(Tuils.EMPTYSTRING)
                        if (weatherArea) {
                            val json: String = Tuils.inputStreamToString(inputStream) ?: Tuils.EMPTYSTRING
                            val snapshot = WeatherResponseParser.parse(
                                json,
                                XMLPrefsManager.get(Behavior.weather_temperature_measure) ?: "metric"
                            ) ?: throw IllegalArgumentException(context.getString(R.string.weather_response_invalid))

                            var o: CharSequence = Tuils.span(weatherFormat, weatherColor) ?: SpannableString(weatherFormat)

                            val m = weatherFormatPattern.matcher(weatherFormat)
                            while (m.find()) {
                                val name = m.group(1)
                                var delay = m.group(2)
                                if (delay == null || delay.length == 0) delay = "1"
                                val converter = m.group(3)

                                if (delay.toInt() == 1) {
                                    var value = snapshot.values[name]
                                    if (value != null) {

                                        if (converter != null && converter.length > 0) {
                                            try {
                                                var d = value.toDouble()
                                                d = Tuils.textCalculus(d, converter)
                                                value = String.format(Locale.US, "%.2f", d)
                                            } catch (e: Exception) {
                                                Tuils.log(e)
                                            }
                                        }

                                        o = TextUtils.replace(
                                            o, arrayOf<String?>(m.group(0)), arrayOf<String>(
                                                delimiterStart + value + delimiterEnd
                                            )
                                        )
                                    }
                                }
                            }

                            o = replaceLinkColorReplace(context, o, url)
                            o = removeDelimiter(o)

                            val i: Intent = Intent(UIManager.ACTION_WEATHER)
                            i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, o)
                            i.putExtra(UIManager.WEATHER_SYMBOL, snapshot.symbolCode)
                            LocalBroadcastManager.getInstance(context.getApplicationContext())
                                .sendBroadcast(i)
                        } else if (pathType == StoreableValue.Type.xpath) {
                            val cleaner: HtmlCleaner = HtmlCleaner()
                            val props: CleanerProperties = cleaner.getProperties()
                            props.setOmitComments(true)

                            var node: TagNode = cleaner.clean(inputStream)
                            val nodes: Array<Any?> = node.evaluateXPath(path)
                            if (nodes.size == 0) {
                                Tuils.sendOutput(context, R.string.no_result)
                                return
                            }

                            for (c in nodes.indices) {
                                node = nodes[c] as TagNode

                                val f = format ?: defaultFormat
                                var copy: CharSequence = Tuils.span(f, outputColor) ?: SpannableString(f)

                                copy =
                                    replaceAllAttributesString(copy, node.getAttributes().entries)
                                copy =
                                    replaceTagNameString(copy, node.getName(), node.getAttributes())
                                copy = replaceNodeValue(copy, node.getText().toString())
                                copy = replaceNewline(copy)
                                copy = replaceLinkColorReplace(context, copy, url)
                                copy = removeDelimiter(copy)

                                if (copy.toString().trim { it <= ' ' }.length > 0) output =
                                    TextUtils.concat(
                                        output,
                                        (if (c != 0) Tuils.NEWLINE + Tuils.NEWLINE else Tuils.EMPTYSTRING),
                                        copy
                                    )
                            }

                            output(output, context, weatherArea, TerminalManager.CATEGORY_NO_COLOR)
                        } else {
                            val o: Any = JsonPath.read<Any>(inputStream, path)

                            if (o is MutableMap<*, *>) {
//                            this should be a single JSON object

                                val f = format ?: defaultFormat
                                var copy: CharSequence = Tuils.span(f, outputColor) ?: SpannableString(f)

                                copy = Companion.replaceAllAttributesObject(copy, o.entries)
                                copy = replaceTagNameObject(copy, null, o)
                                copy = replaceNewline(copy)
                                copy = replaceLinkColorReplace(context, copy, url)
                                copy = removeDelimiter(copy)

                                output = copy

                                output(
                                    output,
                                    context,
                                    weatherArea,
                                    TerminalManager.CATEGORY_NO_COLOR
                                )
                            } else if (o is MutableList<*>) {
//                            this is an array of JSON objects
                                val a = o

                                for (c in a.indices) {
                                    val f = format ?: defaultFormat
                                    var copy: CharSequence = Tuils.span(f, outputColor) ?: SpannableString(f)

                                    val m = a.get(c) as LinkedHashMap<String?, Any?>

                                    copy = Companion.replaceAllAttributesObject(copy, m.entries)
                                    copy = replaceTagNameObject(copy, null, m)
                                    copy = replaceNewline(copy)
                                    copy = replaceLinkColorReplace(context, copy, url)
                                    copy = removeDelimiter(copy)

                                    if (copy.toString().trim { it <= ' ' }.length > 0) output =
                                        TextUtils.concat(
                                            output,
                                            (if (c != 0) Tuils.NEWLINE + Tuils.NEWLINE else Tuils.EMPTYSTRING),
                                            copy
                                        )
                                }

                                output(
                                    output,
                                    context,
                                    weatherArea,
                                    TerminalManager.CATEGORY_NO_COLOR
                                )
                            } else if (o is String) {
                                output = Tuils.span(o.toString(), outputColor)
                                output(
                                    output,
                                    context,
                                    weatherArea,
                                    TerminalManager.CATEGORY_NO_COLOR
                                )
                            } else {
                                Tuils.sendOutput(outputColor, context, o.toString())
                            }
                        }
                    }
                } catch (e: Exception) {
                    output(e.toString(), context, weatherArea)
                    Tuils.toFile(e)
                    Tuils.log(e)
                } finally {
                    if (weatherArea) weatherQueryInFlight.set(false)
                }
            }
        }.start()
    }

    private fun query(context: Context, format: String?, url: String) {
        query(context, null, null, format, url, true)
    }

    private enum class What {
        COLOR,
        LINK,
        REPLACE
    }

    init {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra(BROADCAST_COUNT, 0) < broadcastCount) return
                broadcastCount++

                val action: String? = intent.getAction()

                if (action == ACTION_ADD) {
                    val id: Int = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)
                    val tag: String? = intent.getStringExtra(TAG_NAME)
                    val path: String? = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)

                    if (tag == StoreableValue.Type.format.name) {
                        for (c in formats.indices) {
                            if (formats.get(c).id == id) {
                                Tuils.sendOutput(context, R.string.id_already)
                                return
                            }
                        }
                    } else {
                        for (c in xpaths.indices) {
                            if (xpaths.get(c).id == id) {
                                Tuils.sendOutput(context, R.string.id_already)
                                return
                            }
                        }

                        for (c in jsons.indices) {
                            if (jsons.get(c).id == id) {
                                Tuils.sendOutput(context, R.string.id_already)
                                return
                            }
                        }
                    }

                    val values: MutableList<StoreableValue>
                    try {
                        val p = StoreableValue.Type.valueOf(tag!!)
                        values = getListFromType(p)
                    } catch (e: Exception) {
                        return
                    }

                    val v = StoreableValue.Companion.create(values, context, tag, path, id)
                    if (v != null) values.add(v)
                } else if (action == ACTION_RM) {
                    val id: Int = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)

                    var check = false
                    for (c in xpaths.indices) {
                        if (xpaths.get(c).id == id) {
                            xpaths.removeAt(c).remove(context)
                            check = true
                            break
                        }
                    }

                    for (c in jsons.indices) {
                        if (jsons.get(c).id == id) {
                            jsons.removeAt(c).remove(context)
                            check = true
                            break
                        }
                    }

                    for (c in formats.indices) {
                        if (formats.get(c).id == id) {
                            formats.removeAt(c).remove(context)
                            check = true
                            break
                        }
                    }

                    if (!check) Tuils.sendOutput(context, R.string.id_notfound)
                } else if (action == ACTION_EDIT) {
                    val id: Int = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)
                    val newExpression: String? =
                        intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (newExpression == null || newExpression.length == 0) return

                    for (c in xpaths.indices) {
                        if (xpaths.get(c).id == id) {
                            xpaths.get(c).edit(context, newExpression)
                            return
                        }
                    }

                    for (c in jsons.indices) {
                        if (jsons.get(c).id == id) {
                            jsons.get(c).edit(context, newExpression)
                            return
                        }
                    }

                    for (c in formats.indices) {
                        if (formats.get(c).id == id) {
                            formats.get(c).edit(context, newExpression)
                            return
                        }
                    }

                    Tuils.sendOutput(context, R.string.id_notfound)
                } else if (action == ACTION_LS) {
                    val tag: String? = intent.getStringExtra(TAG_NAME)

                    val values: MutableList<StoreableValue>
                    val builder = StringBuilder()
                    try {
                        val p = StoreableValue.Type.valueOf(tag!!)
                        values = getListFromType(p)

                        for (v in values) {
                            builder.append("- ID: ").append(v.id).append(" -> ").append(v.value)
                                .append(Tuils.NEWLINE)
                        }
                    } catch (e: Exception) {
                        builder.append("XPaths:").append(Tuils.NEWLINE)
                        if (xpaths.size == 0) builder.append("[]").append(Tuils.NEWLINE)
                        else {
                            for (v in xpaths) {
                                builder.append(Tuils.DOUBLE_SPACE).append("- ID: ").append(v.id)
                                    .append(" -> ").append(v.value).append(Tuils.NEWLINE)
                            }
                        }

                        builder.append("JsonPaths:").append(Tuils.NEWLINE)
                        if (jsons.size == 0) builder.append("[]").append(Tuils.NEWLINE)
                        else {
                            for (v in jsons) {
                                builder.append(Tuils.DOUBLE_SPACE).append("- ID: ").append(v.id)
                                    .append(" -> ").append(v.value).append(Tuils.NEWLINE)
                            }
                        }

                        builder.append("Formats:").append(Tuils.NEWLINE)
                        if (formats.size == 0) builder.append("[]").append(Tuils.NEWLINE)
                        else {
                            for (v in formats) {
                                builder.append(Tuils.DOUBLE_SPACE).append("- ID: ").append(v.id)
                                    .append(" -> ").append(v.value).append(Tuils.NEWLINE)
                            }
                        }
                    }

                    var text = builder.toString().trim { it <= ' ' }
                    if (text.length == 0) text = "[]"
                    Tuils.sendOutput(context, text)
                } else if (action == ACTION_QUERY) {
                    val website: String? = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    val weatherArea: Boolean = intent.getBooleanExtra(WEATHER_AREA, false)

                    var path: String? = intent.getStringExtra(ID)
                    var format: String? = intent.getStringExtra(FORMAT_ID)

                    if (format == null) {
                        val formatId: Int = intent.getIntExtra(FORMAT_ID, Int.Companion.MAX_VALUE)

                        if (formatId == Int.Companion.MAX_VALUE) {
//                            use the default format
                            format = null
                        } else {
                            for (f in formats) {
                                if (f.id == formatId) {
                                    format = f.value
                                    break
                                }
                            }

                            if (format == null) {
                                Tuils.sendOutput(
                                    context,
                                    context.getString(R.string.id_notfound) + ": " + formatId + "(" + StoreableValue.Type.format.name + ")"
                                )
                            }
                        }
                    }

                    var pathType: StoreableValue.Type? = StoreableValue.Type.json
                    if (path == null) {
                        val pathId: Int = intent.getIntExtra(ID, Int.Companion.MAX_VALUE)

                        for (p in xpaths) {
                            if (p.id == pathId) {
                                path = p.value
                                pathType = p.type
                                break
                            }
                        }

                        for (p in jsons) {
                            if (p.id == pathId) {
                                path = p.value
                                pathType = p.type
                                break
                            }
                        }

                        if (path == null) {
                            Tuils.sendOutput(
                                context,
                                context.getString(R.string.id_notfound) + ": " + pathId
                            )
                            return
                        }
                    }

                    query(context, path, pathType, format, website!!, weatherArea)
                } else if (action == ACTION_WEATHER) {
                    val url: String? = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    query(context, weatherFormat, url!!)
                }
            }
        }

        val filter: IntentFilter = IntentFilter()
        filter.addAction(ACTION_ADD)
        filter.addAction(ACTION_RM)
        filter.addAction(ACTION_EDIT)
        filter.addAction(ACTION_LS)
        filter.addAction(ACTION_QUERY)
        filter.addAction(ACTION_WEATHER)

        this.client = client

        linkColor = XMLPrefsManager.getColor(Theme.link_text_color)
        outputColor = XMLPrefsManager.getColor(Theme.output_text_color)
        weatherColor = XMLPrefsManager.getColor(Theme.weather_text_color)
        defaultFormat = XMLPrefsManager.get(Behavior.htmlextractor_default_format) ?: Tuils.EMPTYSTRING
        optionalValueSeparator = XMLPrefsManager.get(Behavior.optional_values_separator) ?: "|"
        weatherFormat = XMLPrefsManager.get(Behavior.weather_format) ?: Tuils.EMPTYSTRING

        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .registerReceiver(receiver, filter)
        broadcastCount = 0

        xpaths = ArrayList<StoreableValue>()
        jsons = ArrayList<StoreableValue>()
        formats = ArrayList<StoreableValue>()

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val o: Array<Any?>? = try {
            XMLPrefsManager.buildDocument(file, NAME)
        } catch (e: SAXParseException) {
            Tuils.sendXMLParseError(context, PATH, e)
            null
        } catch (e: Exception) {
            Tuils.log(e)
            null
        }
        if (o != null) {
            //        Document d = (Document) o[0];
            val root = o[1] as Element

            val nodes = root.getElementsByTagName("*")

            for (count in 0..<nodes.getLength()) {
                val n = nodes.item(count)

                try {
                    val v = StoreableValue.Companion.fromNode((n as org.w3c.dom.Element?)!!)
                    if (v != null) {
                        getListFromType(v.type).add(v)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    class StoreableValue {
        enum class Type {
            xpath,
            json,
            format
        }

        var id: Int
        var value: String?

        var type: Type

        constructor(id: Int, value: String?, type: Type) {
            this.id = id
            this.value = value
            this.type = type
        }

        private constructor(id: Int, value: String?, type: String?) {
            this.id = id
            this.value = value
            this.type = Type.valueOf(type!!)
        }

        fun remove(context: Context) {
            val file: File = File(Tuils.getFolder(), PATH)
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val output: String? = XMLPrefsManager.removeNode(
                file,
                type.name,
                arrayOf<String?>(ID),
                    arrayOf<String?>(id.toString())
            )
            if (output != null) {
                if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                else {
                    Tuils.sendOutput(Color.RED, context, R.string.id_notfound)
                }
            }
        }

        fun edit(context: Context, newExpression: String?) {
            val file: File = File(Tuils.getFolder(), PATH)
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val output: String? = XMLPrefsManager.set(
                file,
                type.name,
                arrayOf<String?>(ID),
                arrayOf<String?>(id.toString()),
                arrayOf<String?>(XMLPrefsManager.VALUE_ATTRIBUTE),
                arrayOf<String?>(newExpression),
                false
            )
            if (output != null) {
                if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                else {
                    Tuils.sendOutput(Color.RED, context, R.string.id_notfound)
                }
            } else {
                this.value = newExpression
            }
        }

        companion object {
            fun fromNode(e: Element): StoreableValue? {
                val nn = e.getNodeName()

                val id: Int = XMLPrefsManager.getIntAttribute(e, ID)
                val value: String? =
                    XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)

                try {
                    return StoreableValue(id, value, nn)
                } catch (e1: Exception) {
                    return null
                }
            }

            fun create(
                values: MutableList<StoreableValue>,
                context: Context,
                tag: String?,
                path: String?,
                id: Int
            ): StoreableValue? {
                for (c in values.indices) {
                    if (values.get(c).id == id) {
                        Tuils.sendOutput(context, R.string.id_already)
                        return null
                    }
                }

                val file: File = File(Tuils.getFolder(), PATH)
                if (!file.exists()) {
                    XMLPrefsManager.resetFile(file, NAME)
                }

                val output: String? = XMLPrefsManager.add(
                    file,
                    tag,
                    arrayOf<String?>(ID, XMLPrefsManager.VALUE_ATTRIBUTE),
                    arrayOf<String?>(id.toString(), path)
                )
                if (output != null) {
                    if (output.length > 0) Tuils.sendOutput(Color.RED, context, output)
                    else Tuils.sendOutput(Color.RED, context, R.string.output_error)
                    return null
                }

                return StoreableValue(id, path, tag)
            }
        }
    }

    companion object {
        var ACTION_ADD: String = BuildConfig.APPLICATION_ID + ".htmlextract_add"
        var ACTION_RM: String = BuildConfig.APPLICATION_ID + ".htmlextract_rm"
        var ACTION_EDIT: String = BuildConfig.APPLICATION_ID + ".htmlextract_edit"
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".htmlextract_ls"

        var ACTION_QUERY: String = BuildConfig.APPLICATION_ID + ".htmlextract_query"
        var ACTION_WEATHER: String = BuildConfig.APPLICATION_ID + ".htmlextract_weather"

        var ID: String = "id"
        var FORMAT_ID: String = "formatId"
        var TAG_NAME: String = "tag"
        var WEATHER_AREA: String = "wArea"

        var BROADCAST_COUNT: String = "broadcastCount"

        var PATH: String = "htmlextract.xml"
        var NAME: String = "HTMLEXTRACT"

        var broadcastCount: Int = 0
        private val weatherQueryInFlight = AtomicBoolean(false)

        private fun output(string: Int, context: Context, weatherArea: Boolean) {
            output(context.getString(string), context, weatherArea)
        }

        private fun output(
            s: CharSequence?,
            context: Context,
            weatherArea: Boolean,
            category: Int = Int.Companion.MAX_VALUE
        ) {
            if (weatherArea) {
                val i: Intent = Intent(UIManager.ACTION_WEATHER)
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, s)
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(i)
            } else {
                if (category != Int.Companion.MAX_VALUE) Tuils.sendOutput(context, s, category)
                else Tuils.sendOutput(context, s)
            }
        }

        private fun output(string: Int, context: Context, weatherArea: Boolean, category: Int) {
            output(context.getString(string), context, weatherArea, category)
        }

        var tagName: Pattern = Pattern.compile("%t(?:\\(([^)]*)\\))?", Pattern.CASE_INSENSITIVE)
        var nodeValuePattern: String = "%v"

        var allAttributes: Pattern =
            Pattern.compile("%a\\(([^\\)]*)\\)\\(([^\\)]*)\\)", Pattern.CASE_INSENSITIVE)
        var attributeName: Pattern = Pattern.compile("%an", Pattern.CASE_INSENSITIVE)
        var attributeValue: Pattern = Pattern.compile("%av", Pattern.CASE_INSENSITIVE)

        var linkColor: Int = Color.WHITE
        var outputColor: Int = Color.WHITE

        fun removeDelimiter(original: CharSequence): CharSequence {
            var original = original
            var newSequence = original
            do {
                original = newSequence
                newSequence = TextUtils.replace(original, delimiterArray, delimiterReplacementArray)
            } while (newSequence.length < original.length)

            return newSequence
        }

        fun replaceAllAttributesObject(
            original: CharSequence,
            set: Set<Map.Entry<*, *>>
        ): CharSequence {
            var original = original
            val allAttributesMatcher: Matcher = allAttributes.matcher(original)
            if (allAttributesMatcher.find()) {
                val l: List<Map.Entry<*, *>> = set.toList()

                val first = allAttributesMatcher.group(1)
                val separator = allAttributesMatcher.group(2)

                val b = StringBuilder()
                b.append(delimiterStart)
                for (c in l.indices) {
                    val e = l.get(c)

                    var temp = first
                    temp = attributeName.matcher(temp).replaceAll(e.key?.toString() ?: Tuils.EMPTYSTRING)
                    temp = attributeValue.matcher(temp).replaceAll(
                        Tuils.removeUnncesarySpaces(
                            e.value.toString().trim { it <= ' ' })
                    )

                    b.append(temp)
                    if (c != l.size - 1) b.append(separator)
                }
                b.append(delimiterEnd)

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(allAttributesMatcher.group()),
                    arrayOf<CharSequence>(b.toString().trim { it <= ' ' })
                )
            }

            return original
        }

        fun replaceTagNameObject(
            original: CharSequence,
            tag: String?,
            attributes: Map<*, *>?
        ): CharSequence {
            var tag = tag
            val tagMatcher: Matcher = tagName.matcher(original)
            while (tagMatcher.find()) {
                val attribute = tagMatcher.group(1)

                if (tag == null) tag = "null"

                var replace: String? = "null"
                if (attribute == null || attribute.length == 0) {
                    replace = tag
                } else if (attributes != null) {
                    replace = attributes[attribute].toString()
                    if (replace.length == 0) replace = "null"
                }

                return TextUtils.replace(
                    original, arrayOf<String>(tagMatcher.group()), arrayOf<CharSequence>(
                        delimiterStart + replace + delimiterEnd
                    )
                )
            }

            return original
        }

        fun replaceAllAttributesString(
            original: CharSequence,
            set: Set<Map.Entry<*, *>>
        ): CharSequence {
            var original = original
            val allAttributesMatcher: Matcher = allAttributes.matcher(original)
            if (allAttributesMatcher.find()) {
                val l: List<Map.Entry<*, *>> = set.toList()

                val first = allAttributesMatcher.group(1)
                val separator = allAttributesMatcher.group(2)

                val b = StringBuilder()
                b.append(delimiterStart)
                for (c in l.indices) {
                    val e = l.get(c)

                    var temp = first
                    temp = attributeName.matcher(temp).replaceAll(e.key?.toString() ?: Tuils.EMPTYSTRING)
                    temp = attributeValue.matcher(temp)
                        .replaceAll(Tuils.removeUnncesarySpaces(e.value?.toString()?.trim { it <= ' ' } ?: Tuils.EMPTYSTRING))

                    b.append(temp)
                    if (c != l.size - 1) b.append(separator)
                }
                b.append(delimiterEnd)

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(allAttributesMatcher.group()),
                    arrayOf<CharSequence>(b.toString().trim { it <= ' ' })
                )
            }

            return original
        }

        fun replaceTagNameString(
            original: CharSequence,
            tag: String?,
            attributes: Map<*, *>?
        ): CharSequence {
            var tag = tag
            val tagMatcher: Matcher = tagName.matcher(original)
            while (tagMatcher.find()) {
                val attribute = tagMatcher.group(1)

                if (tag == null) tag = "null"

                var replace: String? = "null"
                if (attribute == null || attribute.length == 0) {
                    replace = tag
                } else if (attributes != null) {
                    replace = attributes[attribute]?.toString()
                    if (replace == null || replace.length == 0) replace = "null"
                }

                return TextUtils.replace(
                    original, arrayOf<String>(tagMatcher.group()), arrayOf<CharSequence>(
                        delimiterStart + replace + delimiterEnd
                    )
                )
            }

            return original
        }

        fun replaceNodeValue(original: CharSequence?, nodeValue: String): CharSequence {
            var nodeValue = nodeValue
            nodeValue = Jsoup.parse(nodeValue).text()
            return TextUtils.replace(
                original,
                arrayOf<String?>(nodeValuePattern),
                arrayOf<CharSequence>(
                    delimiterStart + (Tuils.removeUnncesarySpaces(nodeValue) ?: Tuils.EMPTYSTRING)
                        .trim { it <= ' ' } + delimiterEnd))
        }

        fun replaceNewline(original: CharSequence): CharSequence {
            var original = original
            var before: Int
            do {
                before = original.length
                original = TextUtils.replace(
                    original,
                    arrayOf<String>(Tuils.patternNewline.pattern()),
                    arrayOf<CharSequence>(Tuils.NEWLINE)
                )
            } while (original.length < before)

            return original
        }

        //    static Pattern linkColorReplace = Pattern.compile("#([a-zA-Z0-9]{6})?(?:\\[([^\\]]*)\\](@#&.*@#&)|\\[([^\\]]+)\\])", Pattern.CASE_INSENSITIVE);
        var colorPattern: Pattern = Pattern.compile("(#[a-fA-F0-9]{6})\\[([^\\]]+)\\]")
        var linkPattern: Pattern = Pattern.compile("#\\[((?:(?:http(?:s)?)|(?:www\\.))[^\\]]+)\\]")
        var replacePattern: Pattern = Pattern.compile("#(\\[.+?\\])@#&(.+?)&#@")

        var extractUrl: Pattern = Pattern.compile("(.*\\.[^\\/]{2,})\\/", Pattern.CASE_INSENSITIVE)

        //    this is used to know where a group begins and when it ends
        var delimiterStart: String = "@#&"
        var delimiterEnd: String = StringBuilder(delimiterStart).reverse().toString()
        var optionalValueSeparator: String = "|"
        var delimiterArray: Array<String?> = arrayOf<String?>(delimiterStart, delimiterEnd)
        var delimiterReplacementArray: Array<String?> =
            arrayOf<String?>(Tuils.EMPTYSTRING, Tuils.EMPTYSTRING)

        fun replaceLinkColorReplace(
            context: Context,
            original: CharSequence,
            url: String
        ): CharSequence {
            var original = original
            var m: Matcher = colorPattern.matcher(original)
            while (m.find()) {
                try {
                    val cl = Color.parseColor(m.group(1))
                    original = TextUtils.replace(
                        original,
                        arrayOf<String>(m.group()),
                        arrayOf<CharSequence?>(Tuils.span(m.group(2), cl))
                    )
                } catch (e: Exception) {
                    Tuils.sendOutput(
                        context,
                        context.getString(R.string.output_invalidcolor) + ": " + m.group(1)
                    )
                }
            }

            m = linkPattern.matcher(original)
            while (m.find()) {
                var text = m.group(1)

                //            fix relative links
                if (text!!.startsWith("/")) {
                    val m1: Matcher = extractUrl.matcher(url)
                    if (m1.find()) {
                        text = m1.group(1) + text
                    }
                }

                val sp: SpannableString = SpannableString(text)
                sp.setSpan(
                    LongClickableSpan(Uri.parse(text)),
                    0,
                    sp.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sp.setSpan(
                    ForegroundColorSpan(linkColor),
                    0,
                    sp.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(m.group()),
                    arrayOf<CharSequence>(sp)
                )
            }

            m = replacePattern.matcher(original)
            while (m.find()) {
                val replaceGroups = m.group(1)
                var text = m.group(2)

                val groups: Array<String?> =
                    replaceGroups!!.split("]".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                for (c in groups.indices) {
                    groups[c] = groups[c]?.replace("[\\[\\]]".toRegex(), Tuils.EMPTYSTRING)

                    val split: Array<String?> = groups[c]!!.split(optionalValueSeparator.toRegex())
                        .dropLastWhile { it.isEmpty() }.toTypedArray()
                    if (split.size == 0) continue

                    text = text!!.replace(split[0]!!.toRegex(), split[1]!!)
                }

                original = TextUtils.replace(
                    original,
                    arrayOf<String>(m.group()),
                    arrayOf<CharSequence?>(text)
                )
            }

            return original
        }
    }
}
