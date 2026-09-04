package ohi.andre.consolelauncher.managers.podcast

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object PodcastParser {
    private val DATE_FORMATS = arrayOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    fun parse(input: InputStream, feedUrl: String): PodcastShow {
        val feedBytes = LimitedInputStream(input, MAX_FEED_BYTES).readBytes()
        if (containsDoctype(feedBytes)) throw IllegalArgumentException("Podcast feeds cannot contain a DOCTYPE")

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val doc = builder.parse(ByteArrayInputStream(feedBytes))
        doc.documentElement.normalize()

        val showId = stableId(feedUrl)
        val root = doc.documentElement
        val channel = firstChild(root, "channel") ?: root
        val title = firstText(channel, "title").ifBlank { feedUrl }
        val description = firstText(channel, "description", "subtitle", "summary")
        val imageUrl = imageUrl(channel)
        val episodes = episodeNodes(doc).take(MAX_EPISODES).mapIndexedNotNull { index, item ->
            parseEpisode(showId, item, index)
        }.sortedWith(
            compareBy<PodcastEpisode> { it.publishedAt ?: Long.MAX_VALUE }
                .thenBy { it.feedOrder }
        )

        return PodcastShow(showId, feedUrl, title, description, imageUrl, episodes)
    }

    fun stableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun parseEpisode(showId: String, item: Element, feedOrder: Int): PodcastEpisode? {
        val audioUrl = enclosureUrl(item) ?: return null
        val title = firstText(item, "title").ifBlank { audioUrl }
        val link = firstText(item, "link").ifBlank { null }
        val guid = firstText(item, "guid", "id")
        val key = when {
            guid.isNotBlank() -> guid
            audioUrl.isNotBlank() -> audioUrl
            else -> (link ?: "") + "|" + title
        }
        return PodcastEpisode(
            showId = showId,
            key = key,
            title = title,
            audioUrl = audioUrl,
            link = link,
            description = firstText(item, "description", "summary", "subtitle"),
            duration = firstText(item, "duration").ifBlank { firstText(item, "itunes:duration").ifBlank { null } },
            publishedAt = parseDate(firstText(item, "pubDate", "published", "updated")),
            feedOrder = feedOrder
        )
    }

    private fun episodeNodes(doc: Document): List<Element> {
        val items = elements(doc.documentElement, "item")
        if (items.isNotEmpty()) return items
        return elements(doc.documentElement, "entry")
    }

    private fun enclosureUrl(item: Element): String? {
        for (element in elements(item, "enclosure")) {
            val url = element.getAttribute("url").trim()
            if (url.isNotEmpty()) return url
        }
        for (element in elements(item, "link")) {
            val rel = element.getAttribute("rel")
            val href = element.getAttribute("href").trim()
            if (href.isNotEmpty() && (rel.equals("enclosure", true) || element.getAttribute("type").startsWith("audio/"))) {
                return href
            }
        }
        return null
    }

    private fun imageUrl(channel: Element): String? {
        val image = firstChild(channel, "image")
        val url = image?.let { firstText(it, "url") }
        if (!url.isNullOrBlank()) return url

        for (element in elements(channel, "itunes:image")) {
            val href = element.getAttribute("href").trim()
            if (href.isNotEmpty()) return href
        }

        for (element in elements(channel, "link")) {
            val rel = element.getAttribute("rel")
            val href = element.getAttribute("href").trim()
            if (href.isNotEmpty() && rel.equals("image", true)) return href
        }

        return firstText(channel, "logo").ifBlank { null }
    }

    private fun firstChild(parent: Element, name: String): Element? =
        parent.childNodes.asSequence()
            .filterIsInstance<Element>()
            .firstOrNull { matches(it, name) }

    private fun elements(parent: Element, name: String): List<Element> =
        parent.getElementsByTagName("*").asSequence()
            .filterIsInstance<Element>()
            .filter { matches(it, name) }
            .toList()

    private fun firstText(parent: Element, vararg names: String): String {
        for (name in names) {
            val node = elements(parent, name).firstOrNull() ?: continue
            val value = node.textContent?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    private fun matches(element: Element, name: String): Boolean {
        val local = element.localName
        return element.tagName.equals(name, true) || local?.equals(name.substringAfter(':'), true) == true
    }

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        for (format in DATE_FORMATS) {
            try {
                return SimpleDateFormat(format, Locale.US).parse(value)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<Node> =
        (0 until length).asSequence().map { item(it) }

    private fun containsDoctype(bytes: ByteArray): Boolean {
        val marker = "<!DOCTYPE"
        var matched = 0
        for (byte in bytes) {
            val char = byte.toInt().and(0xff).toChar()
            if (char.code == 0) continue
            val expected = marker[matched]
            matched = when {
                char.equals(expected, ignoreCase = true) -> matched + 1
                char == marker[0] -> 1
                else -> 0
            }
            if (matched == marker.length) return true
        }
        return false
    }

    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var readCount = 0L

        override fun read(): Int {
            if (readCount >= limit) return checkForOverflow()
            return super.read().also { if (it >= 0) readCount++ }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (readCount >= limit) return checkForOverflow()
            val allowed = minOf(length.toLong(), limit - readCount).toInt()
            return super.read(buffer, offset, allowed).also { if (it > 0) readCount += it }
        }

        private fun checkForOverflow(): Int {
            if (super.read() == -1) return -1
            throw IOException("Podcast feed exceeds ${MAX_FEED_BYTES / (1024 * 1024)} MB")
        }
    }

    const val MAX_FEED_BYTES = 25L * 1024L * 1024L
    // ponytail: 400 covers recent browsing; raise if catch-up-from-episode-1 needs deeper archive
    const val MAX_EPISODES = 400
}
