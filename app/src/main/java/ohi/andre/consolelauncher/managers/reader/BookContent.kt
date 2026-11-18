package ohi.andre.consolelauncher.managers.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.Layout
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/** A book ready to read: text that flows into pages, or pages that are pictures. */
sealed class BookContent : Closeable {
    abstract val title: String

    class Chapter(val title: String, val text: CharSequence)

    /** One line of the book's contents: where a named part starts, as a chapter and a character in it. */
    class Entry(val title: String, val chapter: Int, val offset: Int)

    /**
     * Text in [chapters], the pieces the reader lays out one at a time, and the [toc] readers see. A piece may hold
     * several of the book's chapters, or part of one.
     */
    class Flow(override val title: String, val chapters: List<Chapter>, toc: List<Entry> = emptyList()) : BookContent() {
        val toc: List<Entry> = toc.ifEmpty {
            chapters.mapIndexedNotNull { i, c -> if (c.title.isNotBlank() && (i == 0 || chapters[i - 1].title != c.title)) Entry(c.title, i, 0) else null }
        }

        /** The contents line [chapter], [offset] falls under; -1 before the first. */
        fun entryAt(chapter: Int, offset: Int): Int =
            toc.indexOfLast { it.chapter < chapter || (it.chapter == chapter && it.offset <= offset) }

        override fun close() = Unit
    }

    abstract class Pages(override val title: String) : BookContent() {
        abstract val count: Int
        /** Page [index] scaled to fit [width] × [height], in greys. */
        abstract fun render(index: Int, width: Int, height: Int): Bitmap?
    }

    companion object {
        /** Opens [item]; slow (copies and parses), so call it off the main thread. */
        fun open(context: Context, item: Books.Item): BookContent {
            val uri = Uri.parse(item.uri)
            return when (item.format) {
                Books.Format.EPUB -> Epub.read(Books.localCopy(context, uri), item.name)
                Books.Format.TEXT, Books.Format.MARKDOWN -> PlainText.read(context.contentResolver.openInputStream(uri)!!.use { it.readBytes() },
                    item.name, markdown = item.format == Books.Format.MARKDOWN)
                Books.Format.PDF -> Pdf(Books.localCopy(context, uri), item.name)
                Books.Format.COMIC -> Comic(Books.localCopy(context, uri), item.name)
            }
        }

        /** Long chapters are split so a page turn never waits on laying out a whole book. */
        internal const val MAX_CHAPTER = 60_000
    }
}

/** Bold and italic in book text; the reader picks the real face for them when it lays a page out. */
class FaceSpan(val style: Int) : android.text.style.MetricAffectingSpan() {
    override fun updateDrawState(paint: android.text.TextPaint) = apply(paint)
    override fun updateMeasureState(paint: android.text.TextPaint) = apply(paint)
    private fun apply(paint: android.text.TextPaint) {
        val base = paint.typeface ?: Typeface.DEFAULT
        val combined = (base.style or style)
        paint.typeface = BookFaces.styled(base, combined)
    }
}

/** The reading faces: Literata from the app's assets, with real semibold and italic. */
object BookFaces {
    @Volatile private var literata: Array<Typeface>? = null

    fun literata(context: Context): Typeface = faces(context)[0]

    private fun faces(context: Context): Array<Typeface> = literata ?: arrayOf(
        Typeface.createFromAsset(context.assets, "reader/Literata-Regular.ttf"),
        Typeface.createFromAsset(context.assets, "reader/Literata-SemiBold.ttf"),
        Typeface.createFromAsset(context.assets, "reader/Literata-Italic.ttf"),
    ).also { literata = it }

    fun styled(base: Typeface, style: Int): Typeface {
        val faces = literata
        if (faces != null && (base === faces[0] || base === faces[1] || base === faces[2])) {
            return when {
                style and Typeface.BOLD != 0 -> faces[1]
                style and Typeface.ITALIC != 0 -> faces[2]
                else -> faces[0]
            }
        }
        return Typeface.create(base, style)
    }
}

/** Builds book text: paragraphs with a first-line indent, headings larger and centred. */
internal class TextBuilder {
    val out = SpannableStringBuilder()
    private var blockStart = -1
    /**
     * Paragraph spans are attached only once the text is complete: a paragraph span that ends at the end of the text
     * would otherwise stretch over everything appended after it.
     */
    private val paragraphs = ArrayList<Triple<Any, Int, Int>>()
    /** Where each element id falls in the text, for contents that point inside a file. */
    val anchors = HashMap<String, Int>()

    fun result(): CharSequence {
        paragraphs.forEach { (span, start, end) -> out.setSpan(span, start, minOf(end, out.length), Spanned.SPAN_PARAGRAPH) }
        paragraphs.clear()
        return out
    }

    fun startBlock() { if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append('\n'); blockStart = out.length }

    fun endParagraph() = finish(heading = false)
    fun endHeading() = finish(heading = true)

    private fun finish(heading: Boolean) {
        if (blockStart < 0) return
        // Collapse what HTML collapses: runs of space, and space at either end.
        var end = out.length
        while (end > blockStart && out[end - 1].isWhitespace()) end--
        out.delete(end, out.length)
        var start = blockStart
        while (start < out.length && out[start].isWhitespace() && out[start] != ' ') start++
        out.delete(blockStart, start)
        if (out.length == blockStart) { blockStart = -1; return }
        val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        if (heading) {
            out.setSpan(RelativeSizeSpan(1.3f), blockStart, out.length, flags)
            out.setSpan(FaceSpan(Typeface.BOLD), blockStart, out.length, flags)
        }
        out.append('\n')
        paragraphs += Triple(if (heading) AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER) else IndentSpan(), blockStart, out.length)
        if (heading) out.append('\n')
        blockStart = -1
    }

    fun text(value: String) {
        if (blockStart < 0) startBlock()
        val last = if (out.isEmpty()) '\n' else out[out.length - 1]
        val collapsed = value.replace(SPACES, " ")
        out.append(if (last == ' ' || last == '\n') collapsed.trimStart() else collapsed)
    }

    fun styled(style: Int, body: () -> Unit) {
        val start = out.length
        body()
        if (out.length > start) out.setSpan(FaceSpan(style), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun lineBreak() { if (blockStart < 0) startBlock(); out.append('\n') }

    companion object {
        private val SPACES = Regex("\\s+")
    }
}

/** A paragraph's first-line indent; its width follows the text size the reader lays pages out at. */
class IndentSpan : LeadingMarginSpan {
    override fun getLeadingMargin(first: Boolean) = if (first) width else 0
    override fun drawLeadingMargin(c: android.graphics.Canvas, p: android.graphics.Paint, x: Int, dir: Int, top: Int, baseline: Int,
        bottom: Int, text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?) = Unit

    companion object {
        /** Set by the reader before it lays pages out. */
        @Volatile var width = 0
    }
}

internal object Epub {
    private val BLOCKS = setOf("p", "div", "li", "blockquote", "section", "article", "dd", "dt", "tr", "pre", "figcaption", "center")
    private val HEADINGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val SKIPPED = setOf("script", "style", "head", "title", "svg", "math", "img", "image", "rt", "rp")

    fun read(file: File, fallback: String): BookContent.Flow = ZipFile(file).use { zip ->
        fun text(path: String) = zip.getEntry(path)?.let { e -> zip.getInputStream(e).use { it.readBytes().toString(Charsets.UTF_8) } }
        val container = Jsoup.parse(text("META-INF/container.xml") ?: error("not an epub"), "", Parser.xmlParser())
        val opfPath = container.selectFirst("rootfile")?.attr("full-path") ?: error("no package")
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val opf = Jsoup.parse(text(opfPath) ?: error("no package"), "", Parser.xmlParser())
        val title = opf.select("metadata > *").firstOrNull { it.tagName().endsWith("title") }?.text()?.takeIf { it.isNotBlank() } ?: fallback
        val manifest = opf.select("manifest > item").associate { it.attr("id") to it }
        val spine = opf.select("spine > itemref").mapNotNull { manifest[it.attr("idref")]?.attr("href") }.map { resolve(base, it) }
        val entries = tocEntries(opf, manifest, base, ::text)
        val chapters = ArrayList<BookContent.Chapter>()
        val toc = ArrayList<BookContent.Entry>()
        spine.forEach { path ->
            val html = text(path) ?: return@forEach
            val body = Jsoup.parse(html).body()
            val builder = TextBuilder().apply { walk(body, this) }
            val built = builder.result()
            if (built.isBlank()) return@forEach
            val here = entries.filter { it.first == path }
            val name = here.firstOrNull()?.third ?: body.selectFirst("h1, h2, h3")?.text().orEmpty()
            val parts = split(name, built)
            val starts = parts.runningFold(0) { at, part -> at + part.text.length }
            here.forEach { (_, fragment, label) ->
                val at = fragment?.let { builder.anchors[it] } ?: 0
                val part = (starts.indexOfLast { it <= at }).coerceIn(0, parts.lastIndex)
                toc += BookContent.Entry(label, chapters.size + part, at - starts[part])
            }
            chapters += parts
        }
        BookContent.Flow(title, chapters.ifEmpty { listOf(BookContent.Chapter("", "")) }, toc.sortedWith(compareBy({ it.chapter }, { it.offset })))
    }

    /** The book's contents from the EPUB 3 nav, or else the EPUB 2 NCX: each a file, an anchor in it, and a name. */
    private fun tocEntries(opf: org.jsoup.nodes.Document, manifest: Map<String, Element>, base: String, text: (String) -> String?): List<Triple<String, String?, String>> {
        val out = ArrayList<Triple<String, String?, String>>()
        fun add(dir: String, href: String, label: String) {
            if (label.isBlank()) return
            out += Triple(resolve(dir, href.substringBefore('#')), href.substringAfter('#', "").ifEmpty { null }, label.trim())
        }
        fun dirOf(path: String) = path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        manifest.values.firstOrNull { it.attr("properties").split(' ').contains("nav") }?.let { nav ->
            val path = resolve(base, nav.attr("href"))
            val doc = Jsoup.parse(text(path) ?: return@let)
            val toc = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" || it.attr("role") == "doc-toc" } ?: doc.selectFirst("nav")
            toc?.select("a[href]")?.forEach { a -> add(dirOf(path), a.attr("href"), a.text()) }
        }
        if (out.isEmpty()) {
            val ncxId = opf.selectFirst("spine")?.attr("toc").orEmpty()
            val ncx = manifest[ncxId] ?: manifest.values.firstOrNull { it.attr("media-type") == "application/x-dtbncx+xml" }
            ncx?.let { item ->
                val path = resolve(base, item.attr("href"))
                val doc = Jsoup.parse(text(path) ?: return@let, "", Parser.xmlParser())
                doc.select("navPoint").forEach { point ->
                    val src = point.selectFirst("content")?.attr("src") ?: return@forEach
                    add(dirOf(path), src, point.selectFirst("navLabel > text")?.text().orEmpty())
                }
            }
        }
        return out
    }

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        (base + Uri.decode(href)).split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return parts.joinToString("/")
    }

    private fun walk(node: Node, out: TextBuilder) {
        when (node) {
            is TextNode -> out.text(node.wholeText)
            is Element -> {
                node.id().takeIf { it.isNotEmpty() }?.let { out.anchors.putIfAbsent(it, out.out.length) }
                val tag = node.normalName()
                when {
                    tag in SKIPPED -> Unit
                    tag == "br" -> out.lineBreak()
                    tag in HEADINGS -> { out.endParagraph(); out.startBlock(); node.childNodes().forEach { walk(it, out) }; out.endHeading() }
                    tag in BLOCKS -> {
                        out.endParagraph()
                        if (node.children().any { it.normalName() in BLOCKS || it.normalName() in HEADINGS }) {
                            node.childNodes().forEach { walk(it, out) }
                        } else {
                            out.startBlock(); node.childNodes().forEach { walk(it, out) }
                        }
                        out.endParagraph()
                    }
                    tag == "b" || tag == "strong" -> out.styled(Typeface.BOLD) { node.childNodes().forEach { walk(it, out) } }
                    tag == "i" || tag == "em" || tag == "cite" -> out.styled(Typeface.ITALIC) { node.childNodes().forEach { walk(it, out) } }
                    else -> node.childNodes().forEach { walk(it, out) }
                }
            }
        }
    }

    /** Splits a long chapter at paragraph ends; the later parts keep its name. */
    fun split(name: String, text: CharSequence): List<BookContent.Chapter> {
        if (text.length <= BookContent.MAX_CHAPTER) return listOf(BookContent.Chapter(name, text))
        val out = ArrayList<BookContent.Chapter>()
        var start = 0
        while (start < text.length) {
            var end = min(text.length, start + BookContent.MAX_CHAPTER)
            if (end < text.length) {
                val nl = text.subSequence(start + BookContent.MAX_CHAPTER / 2, end).lastIndexOf('\n')
                if (nl >= 0) end = start + BookContent.MAX_CHAPTER / 2 + nl + 1
            }
            out += BookContent.Chapter(name, text.subSequence(start, end))
            start = end
        }
        return out
    }
}

internal object PlainText {
    private val CHAPTER = Regex("^\\s*(chapter|book|part|prologue|epilogue)\\b.{0,60}$", RegexOption.IGNORE_CASE)
    private val MD_HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val MD_STRONG = Regex("(\\*\\*|__)(.+?)\\1")
    private val MD_EM = Regex("(?<![*_\\w])([*_])(?!\\s)(.+?)(?<!\\s)\\1(?![*_\\w])")
    private val MD_LINK = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")

    fun read(bytes: ByteArray, name: String, markdown: Boolean): BookContent.Flow {
        val raw = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val chapters = ArrayList<BookContent.Chapter>()
        var title = ""
        var builder = TextBuilder()
        val paragraph = StringBuilder()
        fun flushParagraph() {
            if (paragraph.isBlank()) { paragraph.setLength(0); return }
            builder.startBlock()
            if (markdown) inline(paragraph.toString().trim(), builder) else builder.text(paragraph.toString().trim())
            builder.endParagraph()
            paragraph.setLength(0)
        }
        fun startChapter(heading: String) {
            flushParagraph()
            if (builder.out.isNotBlank()) chapters += Epub.split(title, builder.result())
            builder = TextBuilder()
            title = heading
            builder.startBlock(); builder.text(heading); builder.endHeading()
        }
        raw.lineSequence().forEach { line ->
            val heading = if (markdown) MD_HEADING.matchEntire(line.trim()) else null
            when {
                heading != null && heading.groupValues[1].length == 1 -> startChapter(clean(heading.groupValues[2]))
                heading != null -> { flushParagraph(); builder.startBlock(); inline(heading.groupValues[2], builder); builder.endHeading() }
                !markdown && CHAPTER.matches(line) && paragraph.isBlank() -> startChapter(line.trim())
                line.isBlank() -> flushParagraph()
                // Plain text often wraps lines by hand; Markdown lists and quotes keep theirs.
                markdown && (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") || line.trimStart().startsWith("> ")) -> {
                    flushParagraph(); paragraph.append(line.trim().removePrefix("> ").let { if (it.startsWith("- ") || it.startsWith("* ")) "• " + it.drop(2) else it }); flushParagraph()
                }
                else -> paragraph.append(if (paragraph.isEmpty()) "" else " ").append(line.trim())
            }
        }
        flushParagraph()
        if (builder.out.isNotBlank() || chapters.isEmpty()) chapters += Epub.split(title, builder.result())
        return BookContent.Flow(name, chapters)
    }

    private fun clean(value: String) = MD_LINK.replace(MD_EM.replace(MD_STRONG.replace(value) { it.groupValues[2] }) { it.groupValues[2] }) { it.groupValues[1] }.replace("`", "")

    /** Markdown's bold, italic, links and code, as book text. */
    private fun inline(value: String, out: TextBuilder) {
        val text = MD_LINK.replace(value) { it.groupValues[1] }.replace("`", "")
        var at = 0
        val marks = (MD_STRONG.findAll(text).map { Triple(it.range, it.groupValues[2], Typeface.BOLD) } +
            MD_EM.findAll(text).map { Triple(it.range, it.groupValues[2], Typeface.ITALIC) })
            .sortedBy { it.first.first }
        marks.forEach { (range, inner, style) ->
            if (range.first < at) return@forEach
            out.text(text.substring(at, range.first))
            out.styled(style) { out.text(inner) }
            at = range.last + 1
        }
        out.text(text.substring(at))
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        val utf8 = Charsets.UTF_8.newDecoder()
        return runCatching { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
            .getOrElse { String(bytes, Charsets.ISO_8859_1) }
    }
}

/** Turns a page picture into e-ink greys: sixteen levels between the ink and the paper. */
internal object Greys {
    fun apply(bitmap: Bitmap, ink: Int, paper: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val levels = IntArray(16) { level -> androidx.core.graphics.ColorUtils.blendARGB(ink, paper, level / 15f) }
        for (i in pixels.indices) {
            val p = pixels[i]
            val luma = (Color.red(p) * 30 + Color.green(p) * 59 + Color.blue(p) * 11) / 100
            pixels[i] = levels[luma * 15 / 255]
        }
        val out = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }
}

internal class Pdf(file: File, name: String) : BookContent.Pages(name) {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(descriptor)
    override val count: Int get() = renderer.pageCount

    @Synchronized
    override fun render(index: Int, width: Int, height: Int): Bitmap? = runCatching {
        renderer.openPage(index).use { page ->
            val scale = min(width / page.width.toFloat(), height / page.height.toFloat())
            val bitmap = Bitmap.createBitmap(max(1, (page.width * scale).toInt()), max(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }.getOrNull()

    @Synchronized
    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

internal class Comic(file: File, name: String) : BookContent.Pages(name) {
    private val zip = ZipFile(file)
    private val pages = zip.entries().toList().filter { e ->
        !e.isDirectory && e.name.substringAfterLast('/').let { !it.startsWith(".") && it.substringAfterLast('.').lowercase(Locale.ROOT) in IMAGES }
    }.sortedWith { a, b -> natural(a.name, b.name) }
    override val count: Int get() = pages.size

    @Synchronized
    override fun render(index: Int, width: Int, height: Int): Bitmap? = runCatching {
        val entry = pages[index]
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val decoded = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = min(width / decoded.width.toFloat(), height / decoded.height.toFloat())
        Bitmap.createScaledBitmap(decoded, max(1, (decoded.width * scale).toInt()), max(1, (decoded.height * scale).toInt()), true)
    }.getOrNull()

    @Synchronized
    override fun close() { runCatching { zip.close() } }

    companion object {
        private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        private val DIGITS = Regex("\\d+|\\D+")

        /** "page2" before "page10". */
        fun natural(a: String, b: String): Int {
            val x = DIGITS.findAll(a.lowercase(Locale.ROOT)).map { it.value }.toList()
            val y = DIGITS.findAll(b.lowercase(Locale.ROOT)).map { it.value }.toList()
            for (i in 0 until min(x.size, y.size)) {
                val p = x[i]; val q = y[i]
                val c = if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger()) else p.compareTo(q)
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }
}
