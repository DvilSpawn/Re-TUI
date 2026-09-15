package ohi.andre.consolelauncher.notes


import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TtsSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import android.net.Uri
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import java.io.File
import kotlin.math.roundToInt

// This screen uses a native Material theme, not an AppCompat theme.
@android.annotation.SuppressLint("AppCompatCustomView")
class MarkdownEditText(context: Context) : EditText(context) {
    enum class Format { H1, H2, H3, BOLD, ITALIC, UNDERLINE, STRIKE, CODE, CHECKLIST, BULLETS, NUMBERED, QUOTE }

    private val ownedSpans = mutableListOf<Any>()
    private val imageCache = mutableMapOf<String, Drawable>()
    private var internalChange = false
    private var changeStart = 0
    private var changeBefore = 0
    private var changeCount = 0

    var onMarkdownChanged: ((String) -> Unit)? = null
    var onSelection: ((Int, Int) -> Unit)? = null
    var imageFile: ((String) -> File?)? = null
    var rawMode = false
        set(value) {
            field = value
            refreshMarkdown()
        }

    init {
        gravity = android.view.Gravity.TOP or android.view.Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        setHorizontallyScrolling(false)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (internalChange) return
                changeStart = start
                changeBefore = count
                changeCount = after
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(value: Editable) {
                if (internalChange) return
                removeEmptyVisualMarker(value)
                continueList(value)
                refreshMarkdown()
                onMarkdownChanged?.invoke(value.toString())
            }
        })
    }

    fun setMarkdown(markdown: String, focusEnd: Boolean = false) {
        internalChange = true
        setText(markdown)
        internalChange = false
        refreshMarkdown()
        setSelection(if (focusEnd) length() else 0)
    }

    fun markdown(): String = text?.toString().orEmpty()

    fun insertChecklist() = prefixSelectedLines("- [ ] ")

    fun insertImage(path: String, alt: String = "image") {
        replaceSelection("\n![${alt.ifBlank { "image" }}]($path)\n")
    }

    fun replaceRange(start: Int, end: Int, value: String) {
        val safeStart = start.coerceIn(0, length())
        val safeEnd = end.coerceIn(safeStart, length())
        internalEdit { text.replace(safeStart, safeEnd, value); setSelection((safeStart + value.length).coerceAtMost(length())) }
        changed()
    }

    fun applyFormat(format: Format) = when (format) {
        Format.H1 -> heading(1)
        Format.H2 -> heading(2)
        Format.H3 -> heading(3)
        Format.BOLD -> wrapSelection("**", "**")
        Format.ITALIC -> wrapSelection("*", "*")
        Format.UNDERLINE -> wrapSelection("<u>", "</u>")
        Format.STRIKE -> wrapSelection("~~", "~~")
        Format.CODE -> wrapSelection("`", "`")
        Format.CHECKLIST -> insertChecklist()
        Format.BULLETS -> prefixSelectedLines("- ")
        Format.NUMBERED -> prefixSelectedLines("1. ")
        Format.QUOTE -> prefixSelectedLines("> ")
    }

    override fun onSelectionChanged(start: Int, end: Int) {
        super.onSelectionChanged(start, end)
        onSelection?.invoke(start.coerceAtLeast(0), end.coerceAtLeast(0))
    }

    fun refreshMarkdown() {
        val value = text ?: return
        ownedSpans.forEach(value::removeSpan)
        ownedSpans.clear()
        if (rawMode) return

        styleRedactions(value)
        styleImages(value)
        styleLinks(value)
        Regex("(?m)^(#{1,6})[ \\t]+(.*)$").findAll(value).forEach { match ->
            hide(value, match.range.first, match.groups[2]!!.range.first)
            span(value, StyleSpan(Typeface.BOLD), match.groups[2]!!.range)
            val level = match.groups[1]!!.value.length
            span(value, RelativeSizeSpan(when (level) { 1 -> 1.55f; 2 -> 1.35f; 3 -> 1.2f; else -> 1.08f }), match.groups[2]!!.range)
        }
        Regex("(?m)^([ \\t]*- \\[([ xX])])(?:[ \\t]+|$)").findAll(value).forEach { match ->
            span(value, CheckboxSpan(match.groups[2]!!.value.equals("x", true), dp(24)), match.range.first until match.range.last + 1)
            val lineEnd = value.indexOf('\n', match.range.last + 1).let { if (it < 0) value.length else it }
            if (match.groups[2]!!.value.equals("x", true) && match.range.last + 1 < lineEnd) {
                span(value, StrikethroughSpan(), match.range.last + 1 until lineEnd)
            }
        }
        styleDelimited(value, Regex("\\*\\*([^\\n]*?)\\*\\*"), StyleSpan(Typeface.BOLD), 2, 2)
        styleDelimited(value, Regex("~~([^\\n]*?)~~"), StrikethroughSpan(), 2, 2)
        styleDelimited(value, Regex("<u>([^\\n]*?)</u>"), UnderlineSpan(), 3, 4)
        styleDelimited(value, Regex("`([^`\\n]*?)`"), StyleSpan(Typeface.BOLD), 1, 1)
        styleDelimited(value, Regex("(?<!\\*)\\*([^*\\n]*?)\\*(?!\\*)"), StyleSpan(Typeface.ITALIC), 1, 1)
        styleDelimited(value, Regex("(?<!_)_([^_\\n]*?)_(?!_)"), StyleSpan(Typeface.ITALIC), 1, 1)
    }

    private fun styleRedactions(value: Editable) {
        RedactionFormat.token.findAll(value).forEach { match ->
            val width = match.groupValues[1].toIntOrNull() ?: 8
            span(value, RedactionSpan(width), match.range)
            span(value, TtsSpan.TextBuilder("redacted").build(), match.range)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!rawMode && event.action == MotionEvent.ACTION_UP && event.eventTime - event.downTime < 350) {
            val line = layout?.getLineForVertical((event.y + scrollY - totalPaddingTop).roundToInt()) ?: -1
            if (line >= 0 && event.x <= totalPaddingLeft + dp(56)) {
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line).coerceAtMost(length())
                val match = Regex("^(\\s*- \\[)([ xX])(])").find(markdown().substring(start, end))
                if (match != null) {
                    val position = start + match.groups[2]!!.range.first
                    internalEdit { text.replace(position, position + 1, if (text[position].equals('x', true)) " " else "x") }
                    refreshMarkdown()
                    onMarkdownChanged?.invoke(markdown())
                    return true
                }
            }
            if (line >= 0) {
                val offset = layout.getOffsetForHorizontal(line, event.x + scrollX - totalPaddingLeft)
                val link = (text as Spanned).getSpans(offset, offset, URLSpan::class.java).firstOrNull()
                if (link != null && runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                }.isSuccess) return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun styleLinks(value: Editable) {
        Regex("(?<!!)\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)", RegexOption.IGNORE_CASE).findAll(value).forEach { match ->
            val label = match.groups[1]!!.range
            hide(value, match.range.first, label.first)
            hide(value, label.last + 1, match.range.last + 1)
            span(value, URLSpan(match.groups[2]!!.value), label)
        }
    }

    private fun styleImages(value: Editable) {
        Regex("!\\[([^]]*)]\\((images/[^)]+)\\)").findAll(value).forEach { match ->
            val maxWidth = (width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - totalPaddingLeft - totalPaddingRight
            val path = match.groups[2]!!.value
            val drawable = imageCache[path] ?: loadImage(path, maxWidth)?.also { imageCache[path] = it } ?: return@forEach
            span(value, ImageSpan(drawable, match.groups[1]!!.value.ifBlank { "image" }, ImageSpan.ALIGN_BOTTOM), match.range.first until match.range.last + 1)
        }
    }

    private fun loadImage(path: String, maxWidth: Int): Drawable? {
        val file = imageFile?.invoke(path) ?: return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = minOf(1f, maxWidth.toFloat() / bitmap.width.coerceAtLeast(1))
        return BitmapDrawable(resources, bitmap).apply {
            setBounds(0, 0, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt())
        }
    }

    private fun styleDelimited(value: Editable, regex: Regex, style: Any, open: Int, close: Int) {
        regex.findAll(value).forEach { match ->
            if (RedactionFormat.token.findAll(value).any { match.range.first in it.range }) return@forEach
            val contentStart = match.range.first + open
            val contentEnd = match.range.last + 1 - close
            hide(value, match.range.first, contentStart)
            hide(value, contentEnd, match.range.last + 1)
            span(value, cloneSpan(style), contentStart until contentEnd)
        }
    }

    private fun cloneSpan(style: Any): Any = when (style) {
        is StyleSpan -> StyleSpan(style.style)
        is StrikethroughSpan -> StrikethroughSpan()
        is UnderlineSpan -> UnderlineSpan()
        else -> style
    }

    private fun hide(value: Editable, start: Int, end: Int) {
        if (start < end) span(value, HiddenSpan(), start until end)
    }

    private fun span(value: Editable, what: Any, range: IntRange) {
        if (range.first < 0 || range.last >= value.length || range.first > range.last) return
        value.setSpan(what, range.first, range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ownedSpans.add(what)
    }

    private fun continueList(value: Editable) {
        if (changeBefore != 0 || changeCount != 1 || changeStart !in 0 until value.length || value[changeStart] != '\n') return
        val previousStart = value.lastIndexOf('\n', changeStart - 1).let { if (it < 0) 0 else it + 1 }
        val previous = value.substring(previousStart, changeStart)
        val continuation = when {
            Regex("^\\s*- \\[[ xX]] .+").matches(previous) -> previous.takeWhile(Char::isWhitespace) + "- [ ] "
            Regex("^\\s*[-*+] .+").matches(previous) -> previous.takeWhile(Char::isWhitespace) + previous.trimStart().take(2)
            Regex("^\\s*> .+").matches(previous) -> previous.takeWhile(Char::isWhitespace) + "> "
            Regex("^\\s*\\d+\\. .+").matches(previous) -> {
                val indent = previous.takeWhile(Char::isWhitespace)
                val number = Regex("\\d+").find(previous.trimStart())!!.value.toInt() + 1
                "$indent$number. "
            }
            else -> null
        }
        val emptyMarker = Regex("^\\s*(?:- \\[[ xX]] |[-*+] |> |\\d+\\. )$").matches(previous)
        internalEdit {
            if (emptyMarker) value.delete(previousStart, changeStart)
            else continuation?.let { value.insert(changeStart + 1, it) }
        }
    }

    private fun removeEmptyVisualMarker(value: Editable) {
        if (rawMode || changeBefore == 0 || changeCount != 0) return
        val cursor = changeStart.coerceIn(0, value.length)
        val lineStart = value.lastIndexOf('\n', cursor - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = value.indexOf('\n', cursor).let { if (it < 0) value.length else it }
        val line = value.substring(lineStart, lineEnd)
        val cleaned = MarkdownDocument.removeEmptyVisualMarker(line)
        if (cleaned == line) return
        internalEdit {
            value.replace(lineStart, lineEnd, cleaned)
            setSelection(lineStart + cleaned.length)
        }
    }

    private fun wrapSelection(open: String, close: String) {
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(start)
        internalEdit {
            if (start >= open.length && end + close.length <= text.length &&
                text.substring(start - open.length, start) == open && text.substring(end, end + close.length) == close) {
                text.delete(end, end + close.length)
                text.delete(start - open.length, start)
                setSelection(start - open.length, end - open.length)
            } else {
                text.insert(end, close)
                text.insert(start, open)
                setSelection(start + open.length, end + open.length)
            }
        }
        changed()
    }

    private fun prefixSelectedLines(prefix: String) {
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(start)
        val lineStart = markdown().lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = markdown().indexOf('\n', end).let { if (it < 0) length() else it }
        val replacement = markdown().substring(lineStart, lineEnd).lines().mapIndexed { index, line ->
            val marker = if (prefix == "1. ") "${index + 1}. " else prefix
            if (line.isBlank()) marker else marker + line.replace(Regex("^\\s*(?:#{1,6} |[-*+] |\\d+\\. |> |- \\[[ xX]] )"), "")
        }.joinToString("\n")
        internalEdit { text.replace(lineStart, lineEnd, replacement); setSelection((lineStart + replacement.length).coerceAtMost(length())) }
        changed()
    }

    private fun heading(level: Int) {
        val prefix = "#".repeat(level) + " "
        prefixSelectedLines(prefix)
    }

    private fun replaceSelection(value: String) {
        val start = minOf(selectionStart, selectionEnd).coerceAtLeast(0)
        val end = maxOf(selectionStart, selectionEnd).coerceAtLeast(start)
        internalEdit { text.replace(start, end, value); setSelection((start + value.length).coerceAtMost(length())) }
        changed()
    }

    private fun changed() {
        refreshMarkdown()
        onMarkdownChanged?.invoke(markdown())
    }

    private inline fun internalEdit(action: () -> Unit) {
        internalChange = true
        action()
        internalChange = false
    }

    private class HiddenSpan : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) = 0
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) = Unit
    }

    private class CheckboxSpan(private val checked: Boolean, private val width: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?) = width
        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val oldStyle = paint.style
            val oldWidth = paint.strokeWidth
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            val size = width * .58f
            val left = x + (width - size) / 2f
            val boxTop = y - size * .8f
            canvas.drawRect(left, boxTop, left + size, boxTop + size, paint)
            if (checked) {
                paint.style = Paint.Style.FILL
                paint.strokeWidth = 2.5f
                canvas.drawLine(left + size * .18f, boxTop + size * .52f, left + size * .42f, boxTop + size * .75f, paint)
                canvas.drawLine(left + size * .42f, boxTop + size * .75f, left + size * .84f, boxTop + size * .22f, paint)
            }
            paint.style = oldStyle
            paint.strokeWidth = oldWidth
        }
    }

    private class RedactionSpan(private val characters: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            (paint.measureText("M") * characters.coerceIn(4, 24)).roundToInt()

        override fun draw(canvas: Canvas, text: CharSequence, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val previous = paint.style
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, y + paint.fontMetrics.ascent * .88f, x + getSize(paint, text, start, end, null), y + paint.fontMetrics.descent * .2f, paint)
            paint.style = previous
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
