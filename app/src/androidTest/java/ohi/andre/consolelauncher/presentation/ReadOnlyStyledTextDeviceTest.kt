package ohi.andre.consolelauncher.presentation

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.*
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadOnlyStyledTextDeviceTest {
    @Test fun frozenTextKeepsFormattingWithoutSourceViewOrActionSpans() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val sourceView=EditText(ApplicationProvider.getApplicationContext<Context>())
            val calls=IntArray(2)
            val action=object: ClickableSpan() {
                val retainedView=sourceView
                override fun onClick(widget: View) { calls[0]++;retainedView.performClick() }
                override fun updateDrawState(paint: TextPaint) { paint.color=Color.RED;paint.isUnderlineText=false }
            }
            val watcher=object: NoCopySpan,TextWatcher,SpanWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) { calls[1]++ }
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { calls[1]++ }
                override fun afterTextChanged(s: Editable?) { calls[1]++ }
                override fun onSpanAdded(text: Spannable,what: Any,start: Int,end: Int) { calls[1]++ }
                override fun onSpanRemoved(text: Spannable,what: Any,start: Int,end: Int) { calls[1]++ }
                override fun onSpanChanged(text: Spannable,what: Any,os: Int,oe: Int,ns: Int,ne: Int) { calls[1]++ }
            }
            val source=SpannableStringBuilder("read only")
            source.setSpan(action,0,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            source.setSpan(StyleSpan(Typeface.BOLD),0,4,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            source.setSpan(LongClickableSpan("do not execute"),5,9,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            source.setSpan(watcher,0,source.length,Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            val before=calls[1]
            val frozen=ReadOnlyStyledText.freeze(source,sourceView.paint) as Spanned
            val spans=frozen.getSpans(0,frozen.length,Any::class.java)
            assertEquals(source.toString(),frozen.toString())
            assertEquals(0,calls[0]);assertEquals(before,calls[1])
            assertTrue(spans.none { it is ClickableSpan || it is NoCopySpan || it is TextWatcher || it is SpanWatcher })
            assertTrue(spans.none { it===action || it===watcher })
            assertTrue(spans.none { span -> span.javaClass.declaredFields.any { View::class.java.isAssignableFrom(it.type) } })
            val color=frozen.getSpans(0,4,ForegroundColorSpan::class.java).single()
            assertEquals(Color.RED,color.foregroundColor)
            assertEquals(0,frozen.getSpanStart(color));assertEquals(4,frozen.getSpanEnd(color))
            assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,frozen.getSpanFlags(color))
            assertEquals(Typeface.BOLD,frozen.getSpans(0,4,StyleSpan::class.java).single().style)
        }
    }
}
