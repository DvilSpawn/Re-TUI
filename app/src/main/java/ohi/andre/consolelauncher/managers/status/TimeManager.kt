package ohi.andre.consolelauncher.managers.status

import android.content.Context
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui

class TimeManager(
    context: Context,
    delay: Long,
    private var size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    override fun refreshAppearance() {
        size = ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.getInt(ohi.andre.consolelauncher.managers.xml.options.Ui.time_size)
        super.refreshAppearance()
    }

    override fun update() {
        listener?.onUpdate(
            UIManager.Label.time,
            ohi.andre.consolelauncher.managers.TimeManager.instance!!.getCharSequence(context, size, "%t")
        )
    }
}
