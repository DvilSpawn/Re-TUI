package ohi.andre.consolelauncher.managers.reader

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.tuixt.TuixtDialog
import ohi.andre.consolelauncher.managers.media.WorkspaceActivity
import ohi.andre.consolelauncher.managers.settings.ThemeColorResolver
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.LauncherSystemUi
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.Locale

/** The books: what was being read, then everything in the chosen folders, filtered by kind or by name. */
class ReaderLibraryActivity : WorkspaceActivity() {
    private enum class Shelf(val formats: Set<Books.Format>?, val label: Int) {
        ALL(null, R.string.reader_shelf_all),
        EPUB(setOf(Books.Format.EPUB), R.string.reader_shelf_epub),
        PDF(setOf(Books.Format.PDF), R.string.reader_shelf_pdf),
        TEXT(setOf(Books.Format.TEXT, Books.Format.MARKDOWN), R.string.reader_shelf_text),
        COMIC(setOf(Books.Format.COMIC), R.string.reader_shelf_comic)
    }

    private var shelf = Shelf.ALL
    private var query = ""
    private var scanning = false
    private val shelfButtons = HashMap<Shelf, TextView>()
    private val lines = Lines()
    private lateinit var title: TextView

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) runCatching { Books.addFolder(this, uri) }.onSuccess { scan() }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val item = runCatching { Books.addFile(this, uri) }.getOrNull()
        if (item == null) Tuils.sendOutput(this, getString(R.string.reader_not_a_book)) else ReaderActivity.open(this, item.uri)
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LauncherSystemUi.requestNoTitleIfFullscreen(this)
        super.onCreate(savedInstanceState)
        LauncherSystemUi.applyFullscreen(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val search = EditText(this).apply {
            hint = getString(R.string.reader_search_books)
            contentDescription = hint
            typeface = font
            textSize = 13f
            setTextColor(ThemeColorResolver.color(Theme.workspace_input_text_color))
            setHintTextColor(ThemeColorResolver.color(Theme.field_hint_color))
            Tuils.setCursorDrawableColor(this, ThemeColorResolver.color(Theme.cursor_color))
            isSingleLine = true
            background = outline(border)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            doAfterTextChanged { query = it?.toString().orEmpty(); refresh() }
        }
        val close = button("X", getString(R.string.player_close), 44, BAR) { finish() }
        val shelves = evenRow(Shelf.entries.map { option ->
            button(getString(option.label), getString(option.label), 52, BAR) { shelf = option; refresh() }.also { shelfButtons[option] = it }
        })
        val actions = evenRow(listOf(
            button(getString(R.string.reader_add_folder), getString(R.string.reader_add_folder), 52, BAR) { runCatching { pickFolder.launch(null) } },
            button(getString(R.string.reader_open_file), getString(R.string.reader_open_file), 52, BAR) { runCatching { pickFile.launch(arrayOf("*/*")) } },
            button(getString(R.string.reader_rescan), getString(R.string.reader_rescan), 52, BAR) { scan() },
        ))
        title = titled("")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            addView(LinearLayout(context).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(search, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(GAP) })
                addView(close)
            })
            addView(shelves)
            addView(actions)
            addView(Space(context), LinearLayout.LayoutParams(1, dp(10)))
            addView(section(title, lineList(lines)), LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(buildWindow(getString(R.string.reader_title), content))
        onBackPressedDispatcher.addCallback(this) { finish() }
    }

    override fun onStart() {
        super.onStart()
        refresh()
    }

    private fun scan() {
        if (scanning) return
        scanning = true
        refresh()
        Thread {
            runCatching { Books.scan(applicationContext) }
            runOnUiThread { scanning = false; refresh() }
        }.start()
    }

    private fun refresh() {
        shelfButtons.forEach { (option, b) -> paint(b, option == shelf, if (option == shelf) sel else control) }
        val all = Books.items(this)
        val wanted = all.filter { book ->
            (shelf.formats?.contains(book.format) ?: true) &&
                (query.isBlank() || book.name.contains(query, ignoreCase = true) || book.folder.contains(query, ignoreCase = true))
        }
        title.text = if (scanning) getString(R.string.reader_scanning) else getString(R.string.reader_count, wanted.size)
        val out = ArrayList<Line>()
        val last = Books.last(this)?.takeIf { (book, _) -> book in wanted }
        if (last != null && query.isBlank()) {
            out += Line(getString(R.string.reader_continue), header = true)
            out += bookLine(last.first, current = true)
            out += Line(getString(R.string.reader_shelf_all), header = true)
        }
        if (all.isEmpty()) {
            out += Line(getString(if (Books.folders(this).isEmpty()) R.string.reader_empty else R.string.reader_empty_scanned), wrap = true)
        } else if (wanted.isEmpty()) {
            out += Line(getString(R.string.reader_none_match), wrap = true)
        }
        wanted.forEach { out += bookLine(it, current = false) }
        lines.set(out)
    }

    private fun bookLine(book: Books.Item, current: Boolean): Line {
        val place = Books.place(this, book.uri)
        val kind = book.format.name.let { if (it == "MARKDOWN") "MD" else it }
        val where = listOfNotNull(place?.chapterTitle?.takeIf { current && it.isNotBlank() }, book.folder.takeIf { it.isNotEmpty() && !current })
        val meta = when {
            place == null -> getString(R.string.reader_new)
            place.fraction >= DONE -> getString(R.string.reader_done)
            else -> "${(place.fraction * 100).toInt().coerceAtLeast(1)}%"
        }
        return Line(place?.title?.takeIf { it.isNotBlank() } ?: book.name, (listOf(kind) + where).joinToString(" · "), meta, on = current,
            tap = { ReaderActivity.open(this, book.uri) }, hold = { askRemove(book) })
    }

    private fun askRemove(book: Books.Item) = TuixtDialog.showConfirm(this, getString(R.string.reader_remove_title),
        getString(R.string.reader_remove_body, book.name), getString(R.string.reader_remove), getString(android.R.string.cancel)) {
        Books.remove(this, book.uri)
        refresh()
    }

    companion object {
        private const val DONE = 0.995f
    }
}
