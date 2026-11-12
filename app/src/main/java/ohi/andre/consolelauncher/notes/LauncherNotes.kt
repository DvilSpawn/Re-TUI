package ohi.andre.consolelauncher.notes

import android.content.Context
import android.content.Intent
import ohi.andre.consolelauncher.managers.RetuiThemeBridge
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.UUID

object LauncherNotes {
    /** Today's journal with the keyboard up: the edge sidebar's write button. */
    fun openNew(context: Context) = launch(context, JournalActivity.TAB_JOURNAL, write = true)

    /** The quick notes list, the notes that show on the status pane. */
    fun open(context: Context) = launch(context, JournalActivity.TAB_NOTES)

    fun openJournal(context: Context) = launch(context, JournalActivity.TAB_JOURNAL)

    /** The old notes library, kept as a read-back copy after the split. */
    fun openArchive(context: Context, id: String? = null) {
        val intent = Intent(context, RememberActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("note_id", id)
            .putExtra("show_library", id == null)
        themed(context, intent)
        context.startActivity(intent)
    }

    private fun launch(context: Context, tab: String, write: Boolean = false) {
        val intent = Intent(context, JournalActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(JournalActivity.EXTRA_TAB, tab)
            .putExtra(JournalActivity.EXTRA_WRITE, write)
        themed(context, intent)
        context.startActivity(intent)
    }

    private fun themed(context: Context, intent: Intent) {
        RetuiThemeBridge.putLauncherThemeExtras(intent, context, ohi.andre.consolelauncher.tuils.frame.FrameTarget.OVERLAYS, context.packageName)
        // Notes is a terminal pane, not a module: use the same typography as Help and Calc.
        intent.putExtra(com.dvil.retui.contract.RetuiVisualContract.BODY_TEXT_SIZE,
            ohi.andre.consolelauncher.tuils.LauncherFontScale.terminalTextSizeSp().toInt())
        intent.putExtra(com.dvil.retui.contract.RetuiVisualContract.HEADER_TEXT_SIZE,
            ohi.andre.consolelauncher.managers.settings.AppearanceSettings.outputHeaderTextSize())
    }

    // The original XML is retained as a recovery copy; the private index marks migration complete.
    fun importLegacy(context: Context): NoteLibrary {
        Tuils.init(context)
        return importLegacyFile(File(Tuils.getFolder(), "notes.xml"))
    }

    internal fun importLegacyFile(file: File): NoteLibrary {
        val library = NoteLibrary()
        val folder = library.ensureDefaultFolder()
        if (!file.exists()) return library
        file.inputStream().use { input ->
            val parser = android.util.Xml.newPullParser().apply { setInput(input, "UTF-8") }
            var rootSeen = false
            var index = 0
            while (parser.nextToken() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                require(parser.eventType != org.xmlpull.v1.XmlPullParser.DOCDECL) { "Unsupported note document declaration" }
                if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) continue
                if (!rootSeen) {
                    require(parser.name == "NOTES") { "Invalid legacy notes root" }
                    rootSeen = true
                } else if (parser.name == "note") {
                    val text = parser.getAttributeValue(null, "value") ?: error("Missing note text")
                    val time = parser.getAttributeValue(null, "creationTime")?.toLongOrNull() ?: 0L
                    library.notes.add(Note(
                        id = UUID.nameUUIDFromBytes("launcher:$index:$time:$text".toByteArray(Charsets.UTF_8)).toString(),
                        createdAt = time,
                        title = legacyTitle(text),
                        markdown = text,
                        locked = parser.getAttributeValue(null, "lock").toBoolean(),
                        folderId = folder.id
                    ))
                    index++
                }
            }
            require(rootSeen) { "Empty legacy notes document" }
        }
        return library
    }

    fun legacyTitle(text: String): String = text.lineSequence().firstOrNull { it.isNotBlank() }
        ?.trim()?.take(120).orEmpty().ifBlank { "Untitled note" }
}
