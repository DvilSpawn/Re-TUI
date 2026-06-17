package ohi.andre.consolelauncher.managers

import android.content.Context
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.Tuils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.ArrayList

/**
 * Created by francescoandreuzzi on 26/07/2017.
 */
class TimeManager(context: Context) {
    private var outputDateFormatList: Array<FormatEntry?>?
    private var statusDateFormatList: Array<FormatEntry?>?

    init {
        instance = this
        val separator = XMLPrefsManager.get(Behavior.time_format_separator)

        outputDateFormatList =
            createList(context, XMLPrefsManager.get(Behavior.output_time_format), separator)
        statusDateFormatList =
            createList(context, XMLPrefsManager.get(Behavior.status_time_format), separator)
    }

    private fun createList(
        context: Context,
        format: String,
        separator: String
    ): Array<FormatEntry?> {
        val formats: Array<String?> =
            format.split(separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val list: Array<FormatEntry?> = arrayOfNulls<FormatEntry>(formats.size)

        for (c in list.indices) {
            try {
                formats[c] = Tuils.patternNewline.matcher(formats[c]).replaceAll(Tuils.NEWLINE)

                var color = XMLPrefsManager.getColor(Theme.time_text_color)
                val m: Matcher = COLOR_PATTERN.matcher(formats[c])
                if (m.find()) {
                    color = Color.parseColor(m.group())
                    formats[c] = m.replaceAll(Tuils.EMPTYSTRING)
                }

                list[c] = buildEntry(color, formats[c]!!)
            } catch (e: Exception) {
                Tuils.sendOutput(Color.RED, context, "Invalid time format: " + formats[c])
                if (c > 0) list[c] = list[0]
                else list[c] = buildFallbackEntry()
            }
        }
        return list
    }

    private fun buildEntry(color: Int, rawFormat: String): FormatEntry {
        val segments: MutableList<FormatSegment?> = ArrayList<FormatSegment?>()
        val matcher: Matcher = SIZE_PATTERN.matcher(rawFormat)
        var cursor = 0
        var foundSizedSegment = false

        while (matcher.find()) {
            foundSizedSegment = true

            if (matcher.start() > cursor) {
                addSegment(segments, rawFormat.substring(cursor, matcher.start()), null)
            }

            addSegment(segments, matcher.group(2), matcher.group(1).toInt())
            cursor = matcher.end()
        }

        if (cursor < rawFormat.length) {
            addSegment(segments, rawFormat.substring(cursor), null)
        }

        if (!foundSizedSegment || segments.isEmpty()) {
            segments.clear()
            addSegment(segments, rawFormat, null)
        }

        return FormatEntry(color, segments)
    }

    private fun addSegment(
        segments: MutableList<FormatSegment?>,
        pattern: String?,
        explicitSize: Int?
    ) {
        if (pattern == null || pattern.length == 0) {
            return
        }

        segments.add(FormatSegment(SimpleDateFormat(pattern, Locale.getDefault()), explicitSize))
    }

    private fun buildFallbackEntry(): FormatEntry {
        val segments: MutableList<FormatSegment?> = ArrayList<FormatSegment?>()
        segments.add(FormatSegment(SimpleDateFormat("HH:mm:ss", Locale.getDefault()), null))
        return FormatEntry(Color.RED, segments)
    }

    private fun get(index: Int, isStatus: Boolean): FormatEntry? {
        var index = index
        val list = if (isStatus) statusDateFormatList else outputDateFormatList
        if (list == null || list.size == 0) return null
        if (index < 0 || index >= list.size) index = 0

        return list[index]
    }

    fun replace(cs: CharSequence): CharSequence {
        return replace(null, Int.Companion.MAX_VALUE, cs, -1, TerminalManager.NO_COLOR, false)
    }

    fun replace(cs: CharSequence, color: Int): CharSequence {
        return replace(null, Int.Companion.MAX_VALUE, cs, -1, color, false)
    }

    fun replace(cs: CharSequence, tm: Long, color: Int): CharSequence {
        return replace(null, Int.Companion.MAX_VALUE, cs, tm, color, false)
    }

    fun replace(cs: CharSequence, tm: Long): CharSequence {
        return replace(null, Int.Companion.MAX_VALUE, cs, tm, TerminalManager.NO_COLOR, false)
    }

    fun replace(context: Context?, size: Int, cs: CharSequence, color: Int): CharSequence {
        return replace(context, size, cs, -1, color, false)
    }

    @JvmOverloads
    fun replace(
        context: Context?,
        size: Int,
        cs: CharSequence,
        tm: Long = -1,
        color: Int = TerminalManager.NO_COLOR,
        isStatus: Boolean = false
    ): CharSequence {
        var cs = cs
        var tm = tm
        if (tm == -1L) {
            tm = System.currentTimeMillis()
        }

        if (cs is String) {
            Tuils.log(Thread.currentThread().getStackTrace())
            Tuils.log("cant span a string!", cs.toString())
        }

        val date = Date(tm)

        val matcher: Matcher = extractor.matcher(cs)
        while (matcher.find()) {
            var number = matcher.group(1)
            if (number == null || number.length == 0) number = "0"

            val entry = get(number.toInt(), isStatus)
            if (entry == null) continue

            val s = span(context, entry, color, date, size)
            cs =
                TextUtils.replace(cs, arrayOf<String?>(matcher.group(0)), arrayOf<CharSequence?>(s))
        }

        val entry = get(0, isStatus)
        cs = TextUtils.replace(
            cs,
            arrayOf<String>("%t"),
            arrayOf<CharSequence?>(span(context, entry, color, date, size))
        )

        return cs
    }

    fun getCharSequence(s: String): CharSequence? {
        return getCharSequence(null, Int.Companion.MAX_VALUE, s, -1, TerminalManager.NO_COLOR, true)
    }

    fun getCharSequence(s: String, color: Int): CharSequence? {
        return getCharSequence(null, Int.Companion.MAX_VALUE, s, -1, color, true)
    }

    fun getCharSequence(s: String, tm: Long, color: Int): CharSequence? {
        return getCharSequence(null, Int.Companion.MAX_VALUE, s, tm, color, true)
    }

    fun getCharSequence(s: String, tm: Long): CharSequence? {
        return getCharSequence(null, Int.Companion.MAX_VALUE, s, tm, TerminalManager.NO_COLOR, true)
    }

    fun getCharSequence(context: Context?, size: Int, s: String): CharSequence? {
        return getCharSequence(context, size, s, -1, TerminalManager.NO_COLOR, true)
    }

    fun getCharSequence(context: Context?, size: Int, s: String, color: Int): CharSequence? {
        return getCharSequence(context, size, s, -1, color, true)
    }

    fun getCharSequence(
        context: Context?,
        size: Int,
        s: String,
        tm: Long,
        color: Int,
        isStatus: Boolean
    ): CharSequence? {
        var tm = tm
        if (tm == -1L) {
            tm = System.currentTimeMillis()
        }

        val date = Date(tm)

        val matcher: Matcher = extractor.matcher(s)
        if (matcher.find()) {
            var number = matcher.group(1)
            if (number == null || number.length == 0) number = "0"

            val entry = get(number.toInt(), isStatus)
            if (entry == null) {
                return null
            }

            return span(context, entry, color, date, size)
        } else return null
    }

    private fun span(
        context: Context?,
        entry: FormatEntry?,
        color: Int,
        date: Date,
        size: Int
    ): CharSequence {
        if (entry == null) return Tuils.EMPTYSTRING

        val builder = SpannableStringBuilder()
        for (segment in entry.segments) {
            if (segment == null || segment.formatter == null) {
                continue
            }

            val start = builder.length
            builder.append(segment.formatter.format(date))
            val end = builder.length

            if (end <= start) {
                continue
            }

            val segmentSize = if (segment.explicitSize != null) segment.explicitSize else size
            if (segmentSize != Int.Companion.MAX_VALUE && context != null) {
                builder.setSpan(
                    AbsoluteSizeSpan(
                        Tuils.convertSpToPixels(
                            segmentSize.toFloat(),
                            context
                        )
                    ), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (builder.length == 0) {
            return Tuils.EMPTYSTRING
        }

        val clr = if (color != TerminalManager.NO_COLOR) color else entry.color
        builder.setSpan(
            ForegroundColorSpan(clr),
            0,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    fun dispose() {
        outputDateFormatList = null
        statusDateFormatList = null

        instance = null
    }

    private class FormatEntry(val color: Int, val segments: MutableList<FormatSegment?>)

    private class FormatSegment(val formatter: SimpleDateFormat?, val explicitSize: Int?)
    companion object {
        private val COLOR_PATTERN: Pattern = Pattern.compile("#(?:\\d|[a-fA-F]){6}")
        private val SIZE_PATTERN: Pattern = Pattern.compile(
            "\\[size=(\\d+)](.*?)\\[/size]",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        )

        var extractor: Pattern = Pattern.compile("%t([0-9]*)", Pattern.CASE_INSENSITIVE)

        var instance: TimeManager? = null
    }
}
