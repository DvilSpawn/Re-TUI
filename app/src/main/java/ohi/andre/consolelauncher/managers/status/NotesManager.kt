package ohi.andre.consolelauncher.managers.status

import android.content.Context
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.tuils.UIUtils

class NotesManager(
    context: Context,
    delay: Long,
    private var size: Int,
    private val notesManager: ohi.andre.consolelauncher.managers.NotesManager?,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    override fun refreshAppearance() {
        size = ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Ui.notes_size)
        super.refreshAppearance()
    }

    override fun update() {
        notesManager?.refresh()
        if (notesManager != null && notesManager.hasChanged) {
            listener?.onUpdate(UIManager.Label.notes, UIUtils.span(context, size, notesManager.getNotes()))
        }
    }
}
