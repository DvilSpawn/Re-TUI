package ohi.andre.consolelauncher.localization

import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class PackText(val forms: Map<String, String>)
data class LanguagePack(val id: String, val languageTag: String, val name: String,
    val nativeName: String, val version: Int, val rtl: Boolean, val texts: Map<String, PackText>)

/** Only text is accepted. Archives are read in memory; paths are never extracted. */
object LanguagePackArchive {
    const val TARGET = "retui-launcher"
    const val MAX_BYTES = 4 * 1024 * 1024
    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{1,47}")
    private val quantities = setOf("zero", "one", "two", "few", "many", "other")
    private val format = Regex("%(?:(\\d+)\\$)?([-#+ 0,(<]*)(?:\\d+)?(?:\\.\\d+)?([tT][a-zA-Z]|[a-zA-Z%])")

    fun bounded(input: InputStream, maximum: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(out.size() + size <= maximum) { "Pack exceeds size limit" }
            out.write(buffer, 0, size)
        }
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray, catalog: JSONObject): LanguagePack {
        require(bytes.size <= MAX_BYTES) { "Pack exceeds size limit" }
        val entries = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("manifest.json", "strings.xml", "LICENSE", "NOTICE")) { "Unexpected archive entry" }
                require(entry.name !in entries) { "Duplicate archive entry" }
                val limit = if (entry.name == "strings.xml") MAX_BYTES else 64 * 1024
                val data = bounded(zip, limit)
                total += data.size
                require(total <= MAX_BYTES) { "Expanded pack exceeds size limit" }
                entries[entry.name] = data
            }
        }
        val manifest = JSONObject(decode(requireNotNull(entries["manifest.json"]) { "Missing manifest.json" }))
        require(manifest.getInt("schema") == 1 && manifest.getString("target") == TARGET) { "Incompatible pack" }
        val id = manifest.getString("id")
        require(idPattern.matches(id) && id != "en") { "Invalid pack ID" }
        val tag = manifest.getString("languageTag")
        val locale = try { Locale.Builder().setLanguageTag(tag).build() } catch (_: Exception) { throw IllegalArgumentException("Invalid locale") }
        require(locale.language.isNotBlank() && locale.language != "und") { "Invalid locale" }
        val direction = manifest.getString("direction")
        require(direction == "ltr" || direction == "rtl") { "Invalid direction" }
        val version = manifest.getInt("version")
        require(version > 0) { "Invalid version" }
        fun label(key: String) = manifest.getString(key).trim().also {
            require(it.isNotEmpty() && it.length <= 80 && it.none(Char::isISOControl)) { "Invalid label" }
        }
        val texts = parseTexts(decode(requireNotNull(entries["strings.xml"]) { "Missing strings.xml" }), catalog)
        require(texts.isNotEmpty()) { "Empty translation" }
        return LanguagePack(id, tag, label("name"), label("nativeName"), version, direction == "rtl", texts)
    }

    private fun decode(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(bytes)).toString()

    private fun parseTexts(xml: String, catalog: JSONObject): Map<String, PackText> {
        require(!xml.contains("<!DOCTYPE", true) && !xml.contains("<!ENTITY", true)) { "XML declarations are not allowed" }
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())
        parser.nextTag()
        require(parser.name == "resources") { "Expected resources" }
        val result = linkedMapOf<String, PackText>()
        while (parser.nextTag() == XmlPullParser.START_TAG) {
            val kind = parser.name
            require(kind == "string" || kind == "plurals") { "Unsupported resource type" }
            val key = requireNotNull(parser.getAttributeValue(null, "name")) { "Missing resource name" }
            require(key !in result && catalog.has(key)) { "Unknown, protected or duplicate resource: $key" }
            val definition = catalog.getJSONObject(key)
            require(definition.getString("type") == kind) { "Resource type mismatch: $key" }
            val forms = linkedMapOf<String, String>()
            if (kind == "string") forms["other"] = parser.nextText()
            else {
                while (parser.nextTag() == XmlPullParser.START_TAG) {
                    require(parser.name == "item") { "Expected plural item" }
                    val quantity = parser.getAttributeValue(null, "quantity")
                    require(quantity in quantities && quantity !in forms) { "Invalid plural quantity" }
                    forms[quantity!!] = parser.nextText()
                }
                require(parser.name == "plurals" && "other" in forms) { "Missing plural fallback" }
            }
            require(forms.values.all { it.isNotBlank() && it.length <= 32768 }) { "Empty or oversized translation" }
            val defaults = definition.getJSONObject("forms")
            for ((quantity, text) in forms) {
                if (definition.optBoolean("formatted", true)) {
                    val source = defaults.optString(quantity, defaults.getString("other"))
                    val expected = arguments(source)
                    require(expected == arguments(text)) { "Format arguments differ: $key" }
                    val args = Array<Any>(expected.keys.maxOfOrNull { it.first } ?: 0) { index ->
                        val kinds = expected.keys.filter { it.first == index + 1 }.map { it.second.lowercase(Locale.ROOT) }
                        when {
                            kinds.any { it.startsWith("t") } -> 0L
                            kinds.any { it in setOf("f", "e", "g", "a") } -> 1.0
                            kinds.any { it in setOf("d", "o", "x") } -> 1L
                            "c" in kinds -> 'x'
                            else -> "text"
                        }
                    }
                    require(String.format(Locale.ROOT, text, *args).length <= 65536) { "Formatted text exceeds limit" }
                }
            }
            result[key] = PackText(forms)
        }
        require(parser.name == "resources") { "Invalid resources end" }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            require(parser.eventType == XmlPullParser.COMMENT || (parser.eventType == XmlPullParser.TEXT && parser.isWhitespace)) { "Unexpected trailing XML" }
        }
        return result
    }

    internal fun arguments(text: String): Map<Pair<Int, String>, Int> {
        val result = mutableMapOf<Pair<Int, String>, Int>()
        var next = 0
        var previous = 0
        var cursor = 0
        while (cursor < text.length) {
            val percent = text.indexOf('%', cursor)
            if (percent < 0) break
            val match = format.find(text, percent)
            require(match != null && match.range.first == percent) { "Invalid format token" }
            require(Regex("\\d+").findAll(match.value).all { (it.value.toLongOrNull() ?: Long.MAX_VALUE) <= 4096 }) { "Format width exceeds limit" }
            val kind = match.groupValues[3]
            require(kind in setOf("s", "S", "d", "o", "x", "X", "f", "e", "E", "g", "G", "a", "A", "b", "B", "c", "C", "h", "H", "%", "n") || kind.matches(Regex("[tT][HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc]"))) { "Unsupported format token" }
            if (kind == "%" || kind == "n") require(match.value == "%$kind") { "Invalid literal format" }
            if (kind != "%" && kind != "n") {
                val index = match.groupValues[1].toIntOrNull()
                    ?: if ('<' in match.groupValues[2]) previous else ++next
                require(index in 1..100) { "Invalid argument index" }
                previous = index
                val key = index to kind
                result[key] = (result[key] ?: 0) + 1
            }
            cursor = match.range.last + 1
        }
        return result
    }
}
