package ohi.andre.consolelauncher.managers.status

import android.os.Handler
import android.os.Looper
import ohi.andre.consolelauncher.UIManager
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

class AsciiAnimationManager(
    defaultDelayMillis: Long,
    private val color: Int,
    private val listener: StatusUpdateListener?
) {
    class AsciiFrameText(val text: String, private val color: Int) : CharSequence {
        override val length: Int
            get() = text.length

        override fun get(index: Int): Char {
            return text[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return text.subSequence(startIndex, endIndex)
        }

        override fun toString(): String {
            return text
        }

        fun color(): Int {
            return color
        }
    }

    private data class RawFrame(val text: String, val delayMillis: Long)
    private data class Frame(val text: CharSequence, val delayMillis: Long)

    private val handler = Handler(Looper.getMainLooper())
    private val defaultDelayMillis = clampDelay(defaultDelayMillis)
    private var frames: List<Frame> = emptyList()
    private var nextFrameIndex = 0
    private var running = false

    private val ticker: Runnable = object : Runnable {
        override fun run() {
            if (!running || frames.size < 2) {
                return
            }

            val frame = frames[nextFrameIndex]
            listener?.onUpdate(UIManager.Label.ascii, frame.text)
            nextFrameIndex = (nextFrameIndex + 1) % frames.size
            handler.postDelayed(this, frame.delayMillis)
        }
    }

    fun load(file: File, animationEnabled: Boolean): CharSequence {
        stop()
        frames = emptyList()
        nextFrameIndex = 0

        val text = readAsciiFile(file)
        if (!animationEnabled) {
            return span(text)
        }

        val minFrameDelay = minFrameDelayFor(text)
        val parsed = parseFrames(text, defaultDelayMillis)
        if (parsed.size < 2) {
            return span(text)
        }

        frames = parsed.map { Frame(span(it.text), max(it.delayMillis, minFrameDelay)) }
        nextFrameIndex = 1
        return frames[0].text
    }

    fun start() {
        if (running || frames.size < 2) {
            return
        }

        running = true
        handler.postDelayed(ticker, frames[0].delayMillis)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(ticker)
    }

    private fun readAsciiFile(file: File): String {
        if (!file.exists()) {
            return "ascii.txt not found"
        }

        return String(file.readBytes(), StandardCharsets.UTF_8)
    }

    private fun span(text: String): CharSequence {
        return AsciiFrameText(cleanFrameText(text), color)
    }

    companion object {
        private const val MIN_DELAY_MS = 250L
        private const val MAX_DELAY_MS = 10_000L
        private const val LARGE_ANIMATION_CHARS = 256 * 1024
        private const val LARGE_ANIMATION_MIN_DELAY_MS = 1000L
        private val FRAME_DIRECTIVE: Pattern =
            Pattern.compile("^\\s*::frame(?:\\s+(?:delay=)?(\\d{2,6}))?\\s*$", Pattern.CASE_INSENSITIVE)
        private val FRAME_LABEL: Pattern =
            Pattern.compile("^\\s*(?:-+\\s*)?[\\[\\{\\(]?\\s*frame\\s*\\d+\\s*[\\]\\}\\)]?\\s*:?\\s*(?:-+)?\\s*$", Pattern.CASE_INSENSITIVE)
        private val COMMA_FRAME_SEPARATOR: Pattern = Pattern.compile("^\\s*,\\s*$")

        fun clampDelay(delayMillis: Long): Long {
            return min(MAX_DELAY_MS, max(MIN_DELAY_MS, delayMillis))
        }

        fun supportedFrameCount(text: String): Int {
            return parseFrames(text, MIN_DELAY_MS).size
        }

        private fun minFrameDelayFor(text: String): Long {
            return if (text.length >= LARGE_ANIMATION_CHARS) LARGE_ANIMATION_MIN_DELAY_MS else MIN_DELAY_MS
        }

        private fun parseFrames(text: String, defaultDelayMillis: Long): List<RawFrame> {
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            if (normalized.indexOf('\u000C') >= 0) {
                return normalized.split('\u000C')
                    .map { cleanFrameText(it) }
                    .filter { it.trim().isNotEmpty() }
                    .map { RawFrame(it, defaultDelayMillis) }
            }

            if (!hasExplicitFrameMarkers(normalized)) {
                val commaSeparatedFrames = parseCommaSeparatedFrames(normalized, defaultDelayMillis)
                if (commaSeparatedFrames.size > 1) {
                    return commaSeparatedFrames
                }
            }

            val parsed = ArrayList<RawFrame>()
            val current = StringBuilder()
            var currentDelay = defaultDelayMillis

            fun flushFrame() {
                val frame = cleanFrameText(current.toString())
                current.setLength(0)
                if (frame.trim().isNotEmpty()) {
                    parsed.add(RawFrame(frame, currentDelay))
                }
            }

            for (line in normalized.split('\n')) {
                val directive = FRAME_DIRECTIVE.matcher(line)
                if (directive.matches()) {
                    flushFrame()
                    currentDelay = clampDelay(parseDelay(directive.group(1), defaultDelayMillis))
                    continue
                }

                if (FRAME_LABEL.matcher(line).matches()) {
                    flushFrame()
                    currentDelay = defaultDelayMillis
                    continue
                }

                current.append(line).append('\n')
            }

            flushFrame()
            return parsed
        }

        private fun hasExplicitFrameMarkers(text: String): Boolean {
            for (line in text.split('\n')) {
                if (FRAME_DIRECTIVE.matcher(line).matches() || FRAME_LABEL.matcher(line).matches()) {
                    return true
                }
            }
            return false
        }

        private fun parseCommaSeparatedFrames(text: String, defaultDelayMillis: Long): List<RawFrame> {
            val parsed = ArrayList<RawFrame>()
            val current = StringBuilder()

            fun flushFrame() {
                val frame = cleanFrameText(current.toString())
                current.setLength(0)
                if (frame.trim().isNotEmpty()) {
                    parsed.add(RawFrame(frame, defaultDelayMillis))
                }
            }

            for (line in text.split('\n')) {
                if (COMMA_FRAME_SEPARATOR.matcher(line).matches()) {
                    flushFrame()
                } else {
                    current.append(line).append('\n')
                }
            }

            flushFrame()
            return parsed
        }

        private fun cleanFrameText(text: String): String {
            val lines = text.split('\n').toMutableList()
            if (lines.size > 1 && lines.last().isEmpty()) {
                lines.removeAt(lines.size - 1)
            }
            return lines.joinToString("\n")
        }

        private fun parseDelay(value: String?, defaultDelayMillis: Long): Long {
            if (value.isNullOrBlank()) {
                return defaultDelayMillis
            }

            return try {
                value.toLong()
            } catch (e: NumberFormatException) {
                defaultDelayMillis
            }
        }
    }
}
