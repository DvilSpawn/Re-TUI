package ohi.andre.consolelauncher.managers.modules.home

import android.text.Spanned
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.net.RssManager
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils

internal class RssModule(private val manager: () -> RssManager?) : HomeModule {
    override val id = ModuleManager.RSS
    override val usesThemeTypeface = true

    override fun show(host: HomeModuleHost) {
        host.renderText(id, manager()?.buildModuleText() ?: host.context.getString(R.string.surface_rss_manager_unavailable_7da59))
    }

    override fun renderBody(host: HomeModuleHost, text: CharSequence?, body: TextView, scroll: ScrollView): Boolean {
        host.renderRssModuleLines(text, body, scroll, id)
        return true
    }

    private fun HomeModuleHost.renderRssModuleLines(
        text: CharSequence?,
        body: TextView,
        scroll: ScrollView,
        module: String?
    ) {
        scroll.removeAllViews()
        body.setVisibility(View.GONE)

        val content = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.group(context)
        content.setOrientation(LinearLayout.VERTICAL)
        content.setLayoutParams(
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        scroll.addView(content)

        val value = text ?: Tuils.EMPTYSTRING
        val raw = value.toString()
        var start = 0
        while (start <= raw.length) {
            val next = raw.indexOf('\n', start)
            val end = if (next == -1) raw.length else next
            addRssModuleLine(content, value.subSequence(start, end), module)
            if (next == -1) {
                break
            }
            start = next + 1
        }

        constrainRssModuleScroll(scroll, content)
    }

    private fun HomeModuleHost.addRssModuleLine(
        parent: LinearLayout,
        text: CharSequence,
        module: String?
    ) {
        val view = ohi.andre.consolelauncher.presentation.ModuleBodyPresentation.paragraph(context)
        if (text is Spanned) {
            view.setText(text, TextView.BufferType.SPANNABLE)
        } else {
            view.setText(text)
        }
        view.setTextColor(bodyTextColor())
        view.setTextSize(bodyTextSize().toFloat())
        view.setIncludeFontPadding(true)
        view.setLineSpacing(Tuils.dpToPx(context, 2).toFloat(), 1f)
        applyBodyTypeface(view, module)

        val span = firstRssModuleSpan(text)
        if (span != null) {
            view.setOnClickListener { v -> span.onClick(v) }
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.performClick()
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    }
                    else -> true
                }
            }
            view.setClickable(true)
            view.setFocusable(true)
        }

        view.setLayoutParams(
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        parent.addView(view)
    }

    private fun HomeModuleHost.firstRssModuleSpan(text: CharSequence): LongClickableSpan? {
        if (text !is Spanned) {
            return null
        }
        val spans = text.getSpans(0, text.length, LongClickableSpan::class.java)
        return if (spans.isEmpty()) null else spans[0]
    }

    private fun HomeModuleHost.constrainRssModuleScroll(scroll: ScrollView, content: LinearLayout) {
        ohi.andre.consolelauncher.presentation.ModulePanelPresentation.limitTextScroll(scroll,
            ohi.andre.consolelauncher.presentation.ModulePanelPresentation.ScrollPolicy(VISIBLE_LINES,true))
    }

    private companion object {
        const val VISIBLE_LINES = 14
    }
}
