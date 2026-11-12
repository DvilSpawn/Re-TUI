package ohi.andre.consolelauncher.managers.termux

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import ohi.andre.consolelauncher.managers.settings.AppearanceSettings.terminalWindowBackground
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme

/** Renders tmux ANSI output into styled text. Holds no state. */
internal object TermuxAnsiRenderer {
    fun renderTermuxWorkspaceAnsi(value: String?): SpannableStringBuilder {
        val out = SpannableStringBuilder()
        if (TextUtils.isEmpty(value)) {
            return out
        }
        val baseForeground = ThemeColorResolver.color(Theme.workspace_output_text_color)
        val baseBackground = terminalWindowBackground()
        val style = TermuxAnsiStyle()
        var spanStart = 0
        var index = 0
        val text = value!!
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\u001B' && index + 1 < text.length && text[index + 1] == '[') {
                val end = findTermuxAnsiSequenceEnd(text, index + 2)
                if (end > index) {
                    applyTermuxAnsiSpan(out, spanStart, out.length, style, baseForeground, baseBackground)
                    if (text[end] == 'm') {
                        updateTermuxAnsiStyle(style, text.substring(index + 2, end))
                    }
                    spanStart = out.length
                    index = end + 1
                    continue
                }
            }
            if (ch != '\r') {
                out.append(ch)
            }
            index++
        }
        applyTermuxAnsiSpan(out, spanStart, out.length, style, baseForeground, baseBackground)
        return out
    }

    private fun findTermuxAnsiSequenceEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val code = text[index].code
            if (code in 0x40..0x7e) {
                return index
            }
            index++
        }
        return -1
    }

    private fun updateTermuxAnsiStyle(style: TermuxAnsiStyle, params: String) {
        val codes = parseTermuxAnsiCodes(params)
        if (codes.isEmpty()) {
            style.reset()
            return
        }
        var index = 0
        while (index < codes.size) {
            when (val code = codes[index]) {
                0 -> style.reset()
                1 -> style.bold = true
                22 -> style.bold = false
                7 -> style.reverse = true
                27 -> style.reverse = false
                39 -> style.foreground = null
                49 -> style.background = null
                in 30..37 -> style.foreground = termuxAnsiColor(code - 30, false, false)
                in 90..97 -> style.foreground = termuxAnsiColor(code - 90, true, false)
                in 40..47 -> style.background = termuxAnsiColor(code - 40, false, true)
                in 100..107 -> style.background = termuxAnsiColor(code - 100, true, true)
                38, 48 -> {
                    val foreground = code == 38
                    val parsed = parseTermuxExtendedAnsiColor(codes, index + 1, !foreground)
                    if (parsed != null) {
                        if (foreground) {
                            style.foreground = parsed.first
                        } else {
                            style.background = parsed.first
                        }
                        index = parsed.second
                    }
                }
            }
            index++
        }
    }

    private fun parseTermuxAnsiCodes(params: String): List<Int> {
        if (params.isEmpty()) {
            return listOf(0)
        }
        val codes = ArrayList<Int>()
        val parts = params.replace(':', ';').split(";")
        for (part in parts) {
            codes.add(part.toIntOrNull() ?: 0)
        }
        return codes
    }

    private fun parseTermuxExtendedAnsiColor(codes: List<Int>, start: Int, background: Boolean): Pair<Int, Int>? {
        if (start >= codes.size) {
            return null
        }
        return when (codes[start]) {
            5 -> {
                if (start + 1 >= codes.size) null
                else Pair(termuxAnsi256Color(codes[start + 1], background), start + 1)
            }
            2 -> {
                if (start + 3 >= codes.size) null
                else Pair(
                    Color.rgb(
                        codes[start + 1].coerceIn(0, 255),
                        codes[start + 2].coerceIn(0, 255),
                        codes[start + 3].coerceIn(0, 255)
                    ),
                    start + 3
                )
            }
            else -> null
        }
    }

    private fun applyTermuxAnsiSpan(
        out: SpannableStringBuilder,
        start: Int,
        end: Int,
        style: TermuxAnsiStyle,
        baseForeground: Int,
        baseBackground: Int
    ) {
        if (start >= end) {
            return
        }
        var foreground = style.foreground ?: baseForeground
        var background = style.background
        if (style.reverse) {
            val originalForeground = foreground
            foreground = background ?: Color.BLACK
            background = originalForeground
        }
        if (foreground != baseForeground || style.reverse || style.foreground != null) {
            out.setSpan(ForegroundColorSpan(foreground), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (background != null && background != baseBackground) {
            out.setSpan(BackgroundColorSpan(background), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.bold) {
            out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun termuxAnsiColor(index: Int, bright: Boolean, background: Boolean): Int {
        return ThemeColorResolver.ansi(index, bright)
    }

    private fun termuxAnsi256Color(code: Int, background: Boolean): Int {
        val clean = code.coerceIn(0, 255)
        if (clean < 16) {
            return termuxAnsiColor(clean % 8, clean >= 8, background)
        }
        if (clean in 16..231) {
            val value = clean - 16
            val red = value / 36
            val green = (value / 6) % 6
            val blue = value % 6
            return Color.rgb(termuxAnsiCubeValue(red), termuxAnsiCubeValue(green), termuxAnsiCubeValue(blue))
        }
        val gray = 8 + (clean - 232) * 10
        return Color.rgb(gray, gray, gray)
    }

    private fun termuxAnsiCubeValue(value: Int): Int {
        return if (value <= 0) 0 else 55 + value * 40
    }

    private class TermuxAnsiStyle {
        var foreground: Int? = null
        var background: Int? = null
        var bold: Boolean = false
        var reverse: Boolean = false

        fun reset() {
            foreground = null
            background = null
            bold = false
            reverse = false
        }
    }
}
