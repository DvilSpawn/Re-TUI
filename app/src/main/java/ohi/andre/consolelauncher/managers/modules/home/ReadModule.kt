package ohi.andre.consolelauncher.managers.modules.home

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.managers.modules.ModuleManager
import ohi.andre.consolelauncher.managers.reader.Books

/** The book being read, its chapter and how far along, then the last book of every other kind; each can be carried on. */
internal class ReadModule : HomeModule {
    override val id = ModuleManager.READ

    private var host: HomeModuleHost? = null

    override fun show(host: HomeModuleHost) {
        this.host = host
        host.renderText(id, summary(host))
    }

    /** Back from reading: draw the module again with the new place, in place of the one showing. */
    override fun onLauncherResumed() {
        host?.takeIf { it.activeModuleId == id }?.presentModule(id)
    }

    private fun summary(host: HomeModuleHost): String {
        val context = host.context
        val kinds = Books.lastByKind(context)
        val (book, place) = kinds.firstOrNull() ?: return context.getString(R.string.reader_module_none)
        val percent = (place.fraction * 100).toInt()
        val bar = (0 until BAR).joinToString("") { if (it < (place.fraction * BAR).toInt()) "█" else "░" }
        val head = listOfNotNull(place.title.ifBlank { book.name }, place.chapterTitle.takeIf { it.isNotBlank() }, "$bar $percent%")
        // The last of every other kind, one line each.
        val rest = kinds.drop(1).map { (other, at) -> "${kind(other.format).padEnd(6)}${at.title.ifBlank { other.name }} · ${(at.fraction * 100).toInt()}%" }
        return (head + (if (rest.isEmpty()) emptyList() else listOf("") + rest)).joinToString("\n")
    }

    /** The kind's short name, which is also the word `books -last` takes. */
    private fun kind(format: Books.Format) = when (Books.kindOf(format)) {
        Books.Format.PDF -> "PDF"
        Books.Format.TEXT -> "TEXT"
        Books.Format.COMIC -> "COMIC"
        else -> "EPUB"
    }

    override fun renderBody(host: HomeModuleHost, text: CharSequence?, body: TextView, scroll: ScrollView): Boolean {
        scroll.removeAllViews()
        body.visibility = View.GONE
        val content = LinearLayout(host.context).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(host.context).apply {
            this.text = text
            textSize = host.bodyTextSize().toFloat()
            setTextColor(host.bodyTextColor())
            host.applyBodyTypeface(this, this@ReadModule.id)
        })
        val kinds = Books.lastByKind(host.context)
        val buttons = ArrayList<Pair<String, String>>()
        if (kinds.isNotEmpty()) buttons += host.context.getString(R.string.reader_module_continue) to "books -last"
        // With more than one kind going, each gets its own way back in.
        if (kinds.size > 1) kinds.drop(1).forEach { (book, _) -> kind(book.format).let { buttons += it.lowercase() to "books -last ${it.lowercase()}" } }
        buttons += host.context.getString(R.string.reader_module_library) to "books"
        host.addCommandButtons(content, buttons)
        scroll.addView(content)
        return true
    }

    private companion object {
        const val BAR = 16
    }
}
