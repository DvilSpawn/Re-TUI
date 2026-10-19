package ohi.andre.consolelauncher.tuils

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme

/** Retains color ownership across an in-place terminal history restoration. */
class ThemeColorSpan private constructor(val role: Theme, color: Int) : ForegroundColorSpan(color) {
    constructor(role: Theme): this(role,ThemeColorResolver.color(role))
    companion object {
        fun text(text: CharSequence?, role: Theme): SpannableString {
            val result = SpannableString(text ?: "")
            result.setSpan(ThemeColorSpan(role), 0, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return result
        }

        fun refresh(text: CharSequence): SpannableString = refresh(text, ThemeColorResolver::color)

        internal fun refresh(text: CharSequence, color: (Theme)->Int): SpannableString {
            val result = SpannableString(text)
            for (span in result.getSpans(0, result.length, ThemeColorSpan::class.java)) {
                val start = result.getSpanStart(span)
                val end = result.getSpanEnd(span)
                val flags = result.getSpanFlags(span)
                result.removeSpan(span)
                result.setSpan(ThemeColorSpan(span.role,color(span.role)), start, end, flags)
            }
            return result
        }
    }
}
