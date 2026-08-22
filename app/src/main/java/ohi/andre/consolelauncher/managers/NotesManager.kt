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
        val outputContext = context ?: mContext
        if (loadClasses) classes.clear()
        notes.clear()

        val file: File = notesFile()
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, NAME)
            if (o == null) {
                Tuils.sendXMLParseError(outputContext, PATH)
                return
            }
        } catch (e: SAXParseException) {
            Tuils.sendXMLParseError(outputContext, PATH, e)
            return
        } catch (e: Exception) {
            Tuils.log(e)
            return
        }

        val root = o[1] as Element

        val nodes = root.getElementsByTagName("*")

        for (count in 0..<nodes.getLength()) {
            val node = nodes.item(count)

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                val e = node as Element
                val name = e.getNodeName()

                if (name == NOTE_NODE) {
                    val time: Long = XMLPrefsManager.getLongAttribute(e, CREATION_TIME)
                    val text: String? =
                        XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)
                    val lock: Boolean = XMLPrefsManager.getBooleanAttribute(e, LOCK)

                    notes.add(NotesManager.Note(time, text!!, lock))
                } else if (loadClasses) {
                    val id: Int
                    try {
                        id = name.toInt()
                    } catch (ex: Exception) {
                        continue
                    }

                    val color: Int
                    try {
                        color = Color.parseColor(
                            XMLPrefsManager.getStringAttribute(
                                e,
                                XMLPrefsManager.VALUE_ATTRIBUTE
                            )
                        )
                    } catch (ex: Exception) {
                        continue
                    }

                    classes.add(Class(id, color))
                }
            }
        }

        Collections.sort(notes)

        invalidateNotes()
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

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (intent.getIntExtra(BROADCAST_COUNT, 0) < broadcastCount) return
                broadcastCount++

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

            var t: CharSequence = n.text
            t = lockPattern.matcher(t).replaceAll(n.lock.toString())
            t = rowPattern.matcher(t).replaceAll((j + 1).toString())
            t = countPattern.matcher(t).replaceAll(notes.size.toString())

            t = Tuils.span(t, if (n.lock) lockedColor else this.color) ?: SpannableString(t)

            t = TimeManager.instance!!.replace(t, n.creationTime)

            if (allowLink) {
                val m = uriPattern.matcher(t)
                while (m.find()) {
                    var g = m.group()

                    //                    www.
                    if (g.startsWith("w")) {
                        g = "http://" + g
                    }

                    val u = Uri.parse(g)
                    if (u == null) continue

                    val sp: SpannableString = SpannableString(m.group())
                    sp.setSpan(LongClickableSpan(u), 0, sp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sp.setSpan(
                        ForegroundColorSpan(linkColor),
                        0,
                        sp.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    t = TextUtils.replace(t, arrayOf<String>(m.group()), arrayOf<CharSequence>(sp))
                }
            }

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

        val m = colorPattern.matcher(oldNotes)
        while (m.find()) {
            val match = m.group()
            val idColor = m.group(1)
            var t: CharSequence? = m.group(2)

            var color: Int
            if (idColor!!.startsWith("#")) {
//                    color
                try {
                    color = Color.parseColor(idColor)
                } catch (e: Exception) {
                    color = Color.RED
                }
            } else {
//                    id
                try {
                    val id = idColor.toInt()
                    val c = findClass(id)
                    color = c!!.color
                } catch (e: Exception) {
                    color = Color.RED
                }
            }

            t = Tuils.span(t.toString(), color)
            oldNotes =
                TextUtils.replace(oldNotes, arrayOf<String>(match), arrayOf<CharSequence?>(t))
        }

        hasChanged = true
    }

    fun getNotes(): CharSequence {
        hasChanged = false
        return oldNotes!!
    }

    private fun addNote(s: String, lock: Boolean) {
        val t = System.currentTimeMillis()

        notes.add(Note(t, s, lock))
        Collections.sort<Note?>(notes)

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val output: String? = XMLPrefsManager.add(
            file,
            NOTE_NODE,
            arrayOf<String?>(CREATION_TIME, XMLPrefsManager.VALUE_ATTRIBUTE, LOCK),
            arrayOf<String?>(t.toString(), s, lock.toString())
        )
        if (output != null) {
            if (output.length > 0) Tuils.sendOutput(mContext, output)
            else Tuils.sendOutput(mContext, R.string.output_error)
        }

        invalidateNotes()
    }

    private fun rmNote(s: String) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(mContext, R.string.note_not_found)
            return
        }

        val time: Long = notes.removeAt(index).creationTime

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val output: String? = XMLPrefsManager.removeNode(
            file,
            arrayOf<String?>(CREATION_TIME),
            arrayOf<String?>(time.toString())
        )
        if (output != null) {
            if (output.length > 0) Tuils.sendOutput(mContext, output)
        }

        invalidateNotes()
    }

    private fun cpNote(s: String) {
        val index = findNote(s)
        if (index == -1) {
            Tuils.sendOutput(mContext, R.string.note_not_found)
            return
        }

        val text: String? = notes.get(index).text

        (mContext as Activity).runOnUiThread(Runnable {
            val clipboard = mContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip: ClipData = ClipData.newPlainText("note", text)
            clipboard.setPrimaryClip(clip)
            Tuils.sendOutput(mContext, mContext.getString(R.string.copied) + Tuils.SPACE + text)
        })
    }

    private fun clearNotes(context: Context?) {
        val iterator: MutableIterator<Note> = notes.iterator()
        while (iterator.hasNext()) {
            val n = iterator.next()
            if (!n.lock) iterator.remove()
        }

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) XMLPrefsManager.resetFile(file, NAME)

        val output: String? = XMLPrefsManager.removeNode(
            file,
            arrayOf<String?>(LOCK),
            arrayOf<String?>(false.toString()),
            true,
            true
        )
        if (output != null && output.length > 0) Tuils.sendOutput(Color.RED, context ?: mContext, output)

        invalidateNotes()
    }

    private fun lsNotes(c: Context?) {
        val builder = StringBuilder()

        for (j in notes.indices) {
            val n: Note = notes.get(j)
            builder.append(" - ").append(j + 1)
                .append(if (n.lock) " [locked]" else Tuils.EMPTYSTRING).append(" -> ")
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

        val n: Note = notes.get(index)
        n.lock = lock
        Collections.sort(notes)

        val time = n.creationTime

        val file: File = File(Tuils.getFolder(), PATH)
        if (!file.exists()) {
            XMLPrefsManager.resetFile(file, NAME)
        }

        val output: String? = XMLPrefsManager.set(
            file,
            NOTE_NODE,
            arrayOf<String?>(CREATION_TIME),
            arrayOf<String?>(time.toString()),
            arrayOf<String?>(
                LOCK
            ),
            arrayOf<String?>(lock.toString()),
            true
        )
        if (output != null && output.length > 0) Tuils.sendOutput(context ?: mContext, output)

        invalidateNotes()
    }

    private fun findNote(s: String): Int {
        var s = s
        try {
            val index = s.toInt() - 1
            if (index < 0 || index >= notes.size) return -1
            return index
        } catch (e: Exception) {
        }

        s = s.lowercase(Locale.getDefault()).trim { it <= ' ' }

        var note: CharSequence
        var c = 0
        while (c < notes.size) {
            val n: Note = notes.get(c)

            var text = n.text

            text = lockPattern.matcher(text).replaceAll(n.lock.toString())
            text = rowPattern.matcher(text).replaceAll((c + 1).toString())
            text = countPattern.matcher(text).replaceAll(notes.size.toString())

            note = text

            val m = colorPattern.matcher(notes.get(c).text)
            while (m.find()) {
                val match = m.group()
                val idColor = m.group(1)
                var t: CharSequence? = m.group(2)

                var color: Int
                if (idColor!!.startsWith("#")) {
//                    color
                    try {
                        color = Color.parseColor(idColor)
                    } catch (e: Exception) {
                        color = Color.RED
                    }
                } else {
//                    id
                    try {
                        val id = idColor.toInt()
                        val cl = findClass(id)
                        color = cl!!.color
                    } catch (e: Exception) {
                        color = Color.RED
                    }
                }

                t = Tuils.span(t.toString(), color)
                note = TextUtils.replace(note, arrayOf<String>(match), arrayOf<CharSequence?>(t))
            }

            if (note.toString().lowercase(Locale.getDefault()).startsWith(s)) break
            c++
        }

        if (c == notes.size) {
            return -1
        }
        return c
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
        override fun compareTo(o: Note): Int {
            when (sorting) {
                SORTING_TIME_UPDOWN -> return (creationTime - o.creationTime).toInt()
                SORTING_TIME_DOWNUP -> return (o.creationTime - creationTime).toInt()
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

                else -> return 1
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
            val records = ArrayList<NoteRecord?>()
            val file: File = notesFile()
            if (!file.exists()) {
                XMLPrefsManager.resetFile(file, NAME)
            }

            val o: Array<Any?>?
            try {
                o = XMLPrefsManager.buildDocument(file, NAME)
                if (o == null) {
                    if (context != null) Tuils.sendXMLParseError(context, PATH)
                    return records
                }
            } catch (e: SAXParseException) {
                if (context != null) Tuils.sendXMLParseError(context, PATH, e)
                return records
            } catch (e: Exception) {
                Tuils.log(e)
                return records
            }

            val root = o[1] as Element
            val nodes = root.getElementsByTagName(NOTE_NODE)
            for (count in 0..<nodes.getLength()) {
                val node = nodes.item(count)
                if (node.getNodeType() != Node.ELEMENT_NODE) continue

                val e = node as Element
                val time: Long = XMLPrefsManager.getLongAttribute(e, CREATION_TIME)
                val text: String? =
                    XMLPrefsManager.getStringAttribute(e, XMLPrefsManager.VALUE_ATTRIBUTE)
                val lock: Boolean = XMLPrefsManager.getBooleanAttribute(e, LOCK)
                records.add(NoteRecord(time, text, lock))
            }
            return records
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
