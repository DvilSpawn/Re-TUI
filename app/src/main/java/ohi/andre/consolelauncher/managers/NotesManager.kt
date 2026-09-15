package ohi.andre.consolelauncher.managers

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.TextUtils
import ohi.andre.consolelauncher.BuildConfig
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import android.annotation.TargetApi
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.annotation.NonNull
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.notes.*
import android.text.style.ClickableSpan
import android.text.TextPaint
import android.view.View
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.TextView
import org.w3c.dom.NodeList
import java.util.ArrayList
import java.util.HashSet
import java.util.Iterator
import java.util.Set
import java.util.regex.Matcher
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.raw.shortcut
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.Tuils
import android.content.Context.CLIPBOARD_SERVICE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.VALUE_ATTRIBUTE
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.resetFile

/**
 * Created by francescoandreuzzi on 12/02/2018.
 */
class NotesManager(var mContext: Context, noteView: TextView?) {
    var oldNotes: CharSequence? = null
    var hasChanged: Boolean = false

    var classes: MutableSet<Class>
    var notes: MutableList<Note>

    var optionalPattern: Pattern
    var footer: String
    var header: String
    var divider: String
    var color: Int
    var lockedColor: Int

    var allowLink: Boolean
    var linkColor: Int = 0

    var receiver: BroadcastReceiver

    var packageManager: PackageManager?

    private fun load(context: Context?, loadClasses: Boolean) {
        try {
            val library = NoteStore(mContext).load()
            notes.clear()
            notes.addAll(library.notes.map { Note(it.createdAt, it.preview(), it.locked).apply { id = it.id } })
            Collections.sort(notes)
            observedRevision = NoteStore.revision
            invalidateNotes()
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.sendOutput(mContext, R.string.output_error)
        }
    }

    private var observedRevision = -1L
    fun refresh() {
        if (observedRevision != NoteStore.revision) load(mContext, false)
    }

    var colorPattern: Pattern = Pattern.compile("(\\d+|#[\\da-zA-Z]{6,8})\\(([^)]*)\\)")
    var countPattern: Pattern = Pattern.compile("%c", Pattern.CASE_INSENSITIVE)
    var lockPattern: Pattern = Pattern.compile("%l", Pattern.CASE_INSENSITIVE)
    var rowPattern: Pattern = Pattern.compile("%r", Pattern.CASE_INSENSITIVE)
    var uriPattern: Pattern = Pattern.compile("(http[s]?:[^\\s]+|www\\.[^\\s]*)\\.[a-z]+")

    //    noteview can't be changed too much, it may be shared
    init {
        classes = HashSet()
        notes = ArrayList<Note>()

        packageManager = mContext.getPackageManager()

        val optionalSeparator = "\\" + XMLPrefsManager.get(Behavior.optional_values_separator)
        val optional = "%\\(([^" + optionalSeparator + "]*)" + optionalSeparator + "([^)]*)\\)"
        optionalPattern = Pattern.compile(optional, Pattern.CASE_INSENSITIVE)

        color = XMLPrefsManager.getColor(Theme.notes_text_color)
        lockedColor = XMLPrefsManager.getColor(Theme.locked_notes_text_color)

        footer = XMLPrefsManager.get(Ui.notes_footer)
        header = XMLPrefsManager.get(Ui.notes_header)
        divider = XMLPrefsManager.get(Ui.notes_divider)
        divider = Tuils.patternNewline.matcher(divider).replaceAll(Tuils.NEWLINE)

        allowLink = XMLPrefsManager.getBoolean(Behavior.notes_allow_link)
        if (allowLink && noteView != null) {
            noteView.setMovementMethod(LinkMovementMethod())
            linkColor = XMLPrefsManager.getColor(Theme.link_text_color)
        }

        Note.Companion.sorting = XMLPrefsManager.getInt(Behavior.notes_sorting)

        load(mContext, true)

        val filter: IntentFilter = IntentFilter()
        filter.addAction(ACTION_ADD)
        filter.addAction(ACTION_RM)
        filter.addAction(ACTION_CLEAR)
        filter.addAction(ACTION_LS)
        filter.addAction(ACTION_LOCK)
        filter.addAction(ACTION_CP)
        filter.addAction(ACTION_OPEN)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                load(mContext, false)
                if (intent.getIntExtra(BROADCAST_COUNT, 0) < broadcastCount) return
                broadcastCount++

                if (intent.action == ACTION_OPEN) {
                    val index = findNote(intent.getStringExtra(TEXT).orEmpty())
                    if (index >= 0) LauncherNotes.open(mContext, notes[index].id)
                    else Tuils.sendOutput(mContext, R.string.note_not_found)
                    return
                }
                if (intent.getAction() == ACTION_ADD) {
                    var text: String? = intent.getStringExtra(TEXT)
                    if (text == null) return

                    var lock = false
                    val split: Array<String?> =
                        text.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    var startAt = 0

                    val beforeSpace = if (split.size >= 2) split[0] else null
                    if (beforeSpace != null) {
                        if ((beforeSpace == "true" || beforeSpace == "false")) {
                            lock = beforeSpace.toBoolean()
                            startAt++
                        }

                        val ar = arrayOfNulls<String>(split.size - startAt)
                        System.arraycopy(split, startAt, ar, 0, ar.size)
                        text = Tuils.toPlanString(ar, Tuils.SPACE)
                    }

                    addNote(text!!, lock)
                } else if (intent.getAction() == ACTION_RM) {
                    val s: String? = intent.getStringExtra(TEXT)
                    if (s == null) return

                    rmNote(s)
                } else if (intent.getAction() == ACTION_CLEAR) {
                    clearNotes(context)
                } else if (intent.getAction() == ACTION_LS) {
                    lsNotes(context)
                } else if (intent.getAction() == ACTION_LOCK) {
                    val text: String? = intent.getStringExtra(TEXT)
                    val lock: Boolean = intent.getBooleanExtra(LOCK, false)

                    lockNote(context, text!!, lock)
                } else if (intent.getAction() == ACTION_CP) {
                    val s: String? = intent.getStringExtra(TEXT)
                    if (s == null) return

                    cpNote(s)
                }
            }
        }

        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .registerReceiver(receiver, filter)
    }

    private fun invalidateNotes() {
        var header = this.header
        val mh = optionalPattern.matcher(header)
        while (mh.find()) {
            header = header.replace(
                mh.group(0),
                if (mh.groupCount() == 2) mh.group(if (notes.size > 0) 1 else 2) else Tuils.EMPTYSTRING
            )
        }

        if (header.length > 0) {
            var h = countPattern.matcher(header).replaceAll(notes.size.toString())
            h = Tuils.patternNewline.matcher(h).replaceAll(Tuils.NEWLINE)
            oldNotes = Tuils.span(h, this.color)
        } else {
            oldNotes = Tuils.EMPTYSTRING
        }

        var ns: CharSequence = Tuils.EMPTYSTRING
        for (j in notes.indices) {
            val n: Note = notes.get(j)

            val t = SpannableString(n.text)
            t.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { LauncherNotes.open(widget.context, n.id) }
                override fun updateDrawState(ds: TextPaint) {
                    ds.color = if (n.lock) lockedColor else color
                    ds.isUnderlineText = false
                }
            }, 0, t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            ns = TextUtils.concat(ns, t, if (j != notes.size - 1) divider else Tuils.EMPTYSTRING)
        }

        oldNotes = TextUtils.concat(oldNotes, ns)

        var footer = this.footer
        val mf = optionalPattern.matcher(footer)
        while (mf.find()) {
            footer = footer.replace(
                mf.group(0),
                if (mf.groupCount() == 2) mf.group(if (notes.size > 0) 1 else 2) else Tuils.EMPTYSTRING
            )
        }

        if (footer.length > 0) {
            var h = countPattern.matcher(footer).replaceAll(notes.size.toString())
            h = Tuils.patternNewline.matcher(h).replaceAll(Tuils.NEWLINE)
            oldNotes = TextUtils.concat(oldNotes, Tuils.span(h, this.color))
        } else {
        }

        hasChanged = true
    }

    fun getNotes(): CharSequence {
        hasChanged = false
        return oldNotes!!
    }

    private fun mutate(action: (NoteStore, NoteLibrary) -> Unit) {
        try {
            synchronized(NoteStore.lock) {
                val store = NoteStore(mContext)
                val library = store.load()
                action(store, library)
            }
            load(mContext, false)
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.sendOutput(mContext, R.string.output_error)
        }
    }

    private fun addNote(s: String, lock: Boolean) = mutate { store, library ->
        library.notes.add(ohi.andre.consolelauncher.notes.Note(
            title = LauncherNotes.legacyTitle(s), markdown = s, locked = lock,
            folderId = library.ensureDefaultFolder().id
        ))
        store.save(library)
    }

    private fun rmNote(s: String) {
        val index = findNote(s)
        if (index < 0) { Tuils.sendOutput(mContext, R.string.note_not_found); return }
        mutate { store, library ->
            library.notes.firstOrNull { it.id == notes[index].id }?.let { store.delete(it, library) }
        }
    }

    private fun cpNote(s: String) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(mContext, R.string.note_not_found)
            return
        }

        val text = try {
            NoteStore(mContext).load().notes.first { it.id == notes[index].id }.let {
                RedactionFormat.hide(it.markdown)
            }
        } catch (e: Exception) {
            Tuils.sendOutput(mContext, R.string.output_error)
            return
        }

        (mContext as Activity).runOnUiThread(Runnable {
            val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("note", text)
            clipboard.setPrimaryClip(clip)
            Tuils.sendOutput(mContext, mContext.getString(R.string.copied) + Tuils.SPACE + text)
        })
    }

    private fun clearNotes(context: Context?) = mutate { store, library ->
        library.notes.filter { !it.locked }.toList().forEach { store.delete(it, library) }
    }

    private fun lsNotes(c: Context?) {
        val builder = StringBuilder()

        for (j in notes.indices) {
            val n: Note = notes.get(j)
            builder.append(" - ").append(j + 1)
                .append(if (n.lock) mContext.getString(R.string.manager_notesmanager_locked_4d8b4) else Tuils.EMPTYSTRING).append(" -> ")
                .append(n.text).append(Tuils.NEWLINE)
        }

        Tuils.sendOutput(c ?: mContext, builder.toString().trim { it <= ' ' })
    }

    private fun lockNote(context: Context?, s: String, lock: Boolean) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(context ?: mContext, R.string.note_not_found)
            return
        }

        mutate { store, library ->
            library.notes.firstOrNull { it.id == notes[index].id }?.locked = lock
            store.save(library)
        }
    }

    private fun findNote(s: String): Int {
        s.toIntOrNull()?.let { return (it - 1).takeIf { index -> index in notes.indices } ?: -1 }
        val prefix = s.trim()
        if (prefix.isEmpty()) return -1
        return notes.indexOfFirst { it.text.startsWith(prefix, ignoreCase = true) }
    }

    private fun findClass(id: Int): Class? {
        val classIterator = classes.iterator()
        while (classIterator.hasNext()) {
            val cl = classIterator.next()
            if (cl.id == id) return cl
        }

        return null
    }

    fun dispose(context: Context) {
        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .unregisterReceiver(receiver)
    }

    class Class(var id: Int, var color: Int)

    class Note(var creationTime: Long, var text: String, var lock: Boolean) :
        Comparable<Note> {
        var id: String = ""

        override fun compareTo(o: Note): Int {
            when (sorting) {
                SORTING_TIME_UPDOWN -> return creationTime.compareTo(o.creationTime)
                SORTING_TIME_DOWNUP -> return o.creationTime.compareTo(creationTime)
                SORTING_ALPHA_UPDOWN -> return Tuils.alphabeticCompare(text, o.text)
                SORTING_ALPHA_DOWNUP -> return Tuils.alphabeticCompare(o.text, text)
                SORTING_LOCK_BEFORE -> if (lock) {
                    if (o.lock) return 0
                    return -1
                } else {
                    if (o.lock) return 1
                    return 0
                }

                SORTING_UNLOCK_BEFORE -> if (lock) {
                    if (o.lock) return 0
                    return 1
                } else {
                    if (o.lock) return -1
                    return 0
                }

                else -> return 0
            }
        }

        override fun toString(): String {
            return creationTime.toString() + " : " + text
        }

        companion object {
            private const val SORTING_TIME_UPDOWN = 0
            private const val SORTING_TIME_DOWNUP = 1
            private const val SORTING_ALPHA_UPDOWN = 2
            private const val SORTING_ALPHA_DOWNUP = 3
            private const val SORTING_LOCK_BEFORE = 4
            private const val SORTING_UNLOCK_BEFORE = 5

            var sorting: Int = Int.Companion.MAX_VALUE
        }
    }

    class NoteRecord(var creationTime: Long, var text: String?, var lock: Boolean)
    companion object {
        var ACTION_RM: String = BuildConfig.APPLICATION_ID + ".rm_note"
        var ACTION_ADD: String = BuildConfig.APPLICATION_ID + ".add_note"
        var ACTION_CLEAR: String = BuildConfig.APPLICATION_ID + ".clear_notes"
        var ACTION_LS: String = BuildConfig.APPLICATION_ID + ".ls_notes"
        var ACTION_LOCK: String = BuildConfig.APPLICATION_ID + ".lock_notes"
        val ACTION_OPEN = BuildConfig.APPLICATION_ID + ".open_note"
        var ACTION_CP: String = BuildConfig.APPLICATION_ID + ".cp_notes"

        var BROADCAST_COUNT: String = "broadcastCount"
        var CREATION_TIME: String = "creationTime"
        var TEXT: String = "text"
        var LOCK: String = "lock"

        const val PATH: String = "notes.xml"
        const val NAME: String = "NOTES"
        const val NOTE_NODE: String = "note"

        var broadcastCount: Int = 0

        fun notesFile(): File {
            return File(Tuils.getFolder(), PATH)
        }

        fun loadRecords(context: Context?): MutableList<NoteRecord?> {
            if (context == null) return arrayListOf()
            return try {
                NoteStore(context).load().notes.map { NoteRecord(it.createdAt, it.preview(), it.locked) }.toMutableList()
            } catch (e: Exception) {
                Tuils.log(e)
                arrayListOf()
            }
        }

        @Throws(Exception::class)
        fun saveRecords(context: Context, records: MutableList<NoteRecord?>) {
            val file: File = notesFile()
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val o: Array<Any?>? = XMLPrefsManager.buildDocument(file, NAME)
            checkNotNull(o) { context.getString(R.string.output_error) }

            val document = o[0] as Document
            val root = o[1] as Element
            val oldNotes = root.getElementsByTagName(NOTE_NODE)
            val nodes = ArrayList<Node>()
            for (count in 0..<oldNotes.getLength()) {
                nodes.add(oldNotes.item(count))
            }
            for (node in nodes) {
                node.getParentNode().removeChild(node)
            }

            val createdAt = System.currentTimeMillis()
            for (count in records.indices) {
                val record = records.get(count)
                if (record == null || record.text == null || record.text!!.trim { it <= ' ' }.length == 0) {
                    continue
                }

                val time = if (record.creationTime > 0) record.creationTime else createdAt + count
                val element = document.createElement(NOTE_NODE)
                element.setAttribute(CREATION_TIME, time.toString())
                element.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, record.text)
                element.setAttribute(LOCK, record.lock.toString())
                root.appendChild(element)
            }

            XMLPrefsManager.writeTo(document, file)
        }
    }
}
