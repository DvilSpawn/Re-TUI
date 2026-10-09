package ohi.andre.consolelauncher.help

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.*

internal object HelpText {
    private const val FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    fun title(text: SpannableStringBuilder, title: String) {
        val start = text.length
        text.append(title)
        text.setSpan(StyleSpan(Typeface.BOLD), start, text.length, FLAGS)
        text.setSpan(RelativeSizeSpan(1.4f), start, text.length, FLAGS)
        text.append("\n\n")
    }
    fun body(text: SpannableStringBuilder, body: String, background: Int, margin: Int) {
        HelpMarkup.parse(body).forEach { block ->
            val start = text.length
            if (block.kind == "bullet") text.append("• ")
            if (block.kind == "warning") text.append("⚠ ")
            if (block.kind == "code") text.append(block.text) else inline(text, block.text, background)
            val end = text.length
            if (end > start) {
                when (block.kind) {
                    "heading" -> {
                        text.setSpan(StyleSpan(Typeface.BOLD), start, end, FLAGS)
                        text.setSpan(RelativeSizeSpan(1.15f), start, end, FLAGS)
                    }
                    "code", "warning" -> {
                        text.setSpan(object : LineBackgroundSpan {
                            override fun drawBackground(canvas: android.graphics.Canvas, paint: android.graphics.Paint,
                                left: Int, right: Int, top: Int, baseline: Int, bottom: Int,
                                text: CharSequence, start: Int, end: Int, lineNumber: Int) {
                                val color = paint.color
                                paint.color = background
                                canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
                                paint.color = color
                            }
                        }, start, end, FLAGS)
                        text.setSpan(LeadingMarginSpan.Standard(margin), start, end, FLAGS)
                        if (block.kind == "code") text.setSpan(TypefaceSpan("monospace"), start, end, FLAGS)
                        else text.setSpan(StyleSpan(Typeface.BOLD), start, end, FLAGS)
                    }
                    "bullet", "number" -> text.setSpan(LeadingMarginSpan.Standard(0, margin), start, end, FLAGS)
                }
            }
            text.append("\n\n")
        }
    }
    private fun inline(text: SpannableStringBuilder, source: String, background: Int) {
        val pattern = Regex("`([^`\\n]+)`|\\*\\*([^*\\n]+)\\*\\*|\\*([^*\\n]+)\\*")
        var cursor = 0
        pattern.findAll(source).forEach { match ->
            text.append(source.substring(cursor, match.range.first))
            val start = text.length
            val code = match.groups[1]?.value
            text.append(code ?: match.groups[2]?.value ?: match.groups[3]!!.value)
            if (code != null) {
                text.setSpan(TypefaceSpan("monospace"), start, text.length, FLAGS)
                text.setSpan(BackgroundColorSpan(background), start, text.length, FLAGS)
            } else text.setSpan(StyleSpan(if (match.groups[2] != null) Typeface.BOLD else Typeface.ITALIC), start, text.length, FLAGS)
            cursor = match.range.last + 1
        }
        text.append(source.substring(cursor))
    }
}
