package ohi.andre.consolelauncher.presentation

import android.graphics.Typeface
import android.os.Build
import android.text.NoCopySpan
import android.text.SpanWatcher
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.TextPaint
import android.text.TextWatcher
import android.text.style.*
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.ThemeColorSpan

/** Copies presentation values rather than TextView watchers, callbacks or actionable spans. */
internal object ReadOnlyStyledText {
    fun freeze(source: CharSequence,paint: TextPaint?=null): CharSequence {
        if(source !is Spanned) return source.toString()
        val frozen=SpannableString(source.toString())
        source.getSpans(0,source.length,Any::class.java).forEach { span ->
            if(span is NoCopySpan || span is TextWatcher || span is SpanWatcher || span is LongClickableSpan) return@forEach
            val start=source.getSpanStart(span);val end=source.getSpanEnd(span);val flags=source.getSpanFlags(span)
            fun add(value: Any) { frozen.setSpan(value,start,end,flags) }
            when(span) {
                is ClickableSpan -> {
                    val appearance=TextPaint().apply { if(paint!=null) set(paint) }
                    val before=appearance.typeface?.style ?: Typeface.NORMAL
                    if(runCatching { span.updateDrawState(appearance) }.isSuccess) {
                        add(ForegroundColorSpan(appearance.color))
                        if(appearance.isUnderlineText) add(UnderlineSpan())
                        if(appearance.isStrikeThruText) add(StrikethroughSpan())
                        val style=(appearance.typeface?.style ?: before) or
                            (if(appearance.isFakeBoldText) Typeface.BOLD else Typeface.NORMAL) or
                            (if(appearance.textSkewX!=0f) Typeface.ITALIC else Typeface.NORMAL)
                        if(style!=before) add(StyleSpan(style))
                    }
                }
                is ThemeColorSpan -> add(span)
                is ForegroundColorSpan -> add(ForegroundColorSpan(span.foregroundColor))
                is BackgroundColorSpan -> add(BackgroundColorSpan(span.backgroundColor))
                is AbsoluteSizeSpan -> add(AbsoluteSizeSpan(span.size,span.dip))
                is RelativeSizeSpan -> add(RelativeSizeSpan(span.sizeChange))
                is ScaleXSpan -> add(ScaleXSpan(span.scaleX))
                is StyleSpan -> add(StyleSpan(span.style))
                is TypefaceSpan -> add(if(Build.VERSION.SDK_INT>=28 && span.typeface!=null) TypefaceSpan(span.typeface!!) else TypefaceSpan(span.family))
                is UnderlineSpan -> add(UnderlineSpan())
                is StrikethroughSpan -> add(StrikethroughSpan())
                is SuperscriptSpan -> add(SuperscriptSpan())
                is SubscriptSpan -> add(SubscriptSpan())
                is AlignmentSpan.Standard -> add(AlignmentSpan.Standard(span.alignment))
                is LeadingMarginSpan.Standard -> add(LeadingMarginSpan.Standard(span.getLeadingMargin(true),span.getLeadingMargin(false)))
                is TabStopSpan.Standard -> add(TabStopSpan.Standard(span.tabStop))
                else -> if(span.javaClass in setOf<Class<*>>(QuoteSpan::class.java,BulletSpan::class.java,LocaleSpan::class.java,TextAppearanceSpan::class.java)) add(span)
            }
        }
        return SpannedString(frozen)
    }
}
