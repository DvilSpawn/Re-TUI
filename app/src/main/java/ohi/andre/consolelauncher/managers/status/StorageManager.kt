package ohi.andre.consolelauncher.managers.status

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.util.ArrayList
import java.util.regex.Matcher
import java.util.regex.Pattern
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.UIUtils

class StorageManager(
    context: Context,
    delay: Long,
    private val size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    private val intAv = "%iav"
    private val intTot = "%itot"
    private val extAv = "%eav"
    private val extTot = "%etot"

    private var storagePatterns: MutableList<Pattern>? = null
    private var storageFormat: String? = null
    private var color = 0
    private var compactInternal = ""
    private var compactExternal = ""

    fun compactInternal(): String = compactInternal

    fun compactExternal(): String = compactExternal

    override fun update() {
        if (storageFormat == null) {
            storageFormat = XMLPrefsManager.get(Behavior.storage_format)
            color = XMLPrefsManager.getColor(Theme.storage_text_color)
        }

        if (storagePatterns == null) {
            storagePatterns = ArrayList()

            storagePatterns!!.add(Pattern.compile(intAv + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intAv + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intAv + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intAv + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intAv + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intAv + "%", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            storagePatterns!!.add(Pattern.compile(intTot + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intTot + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intTot + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intTot + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intTot + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            storagePatterns!!.add(Pattern.compile(extAv + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv + "%", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            storagePatterns!!.add(Pattern.compile(extTot + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extTot + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extTot + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extTot + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extTot + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            storagePatterns!!.add(Tuils.patternNewline)

            storagePatterns!!.add(Pattern.compile(intAv, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(intTot, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extAv, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            storagePatterns!!.add(Pattern.compile(extTot, Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
        }

        val patterns = storagePatterns!!
        val iav = SystemUtils.getAvailableInternalMemorySize(SystemUtils.BYTE)
        val itot = SystemUtils.getTotalInternalMemorySize(SystemUtils.BYTE)
        val eav = SystemUtils.getAvailableExternalMemorySize(SystemUtils.BYTE)
        val etot = SystemUtils.getTotalExternalMemorySize(SystemUtils.BYTE)

        compactInternal = UIManager.compactUnifiedCapacity(
            SystemUtils.formatSize(iav.toLong(), SystemUtils.GIGA),
            SystemUtils.formatSize(itot.toLong(), SystemUtils.GIGA)
        )
        compactExternal = removableStorageCapacity()

        var copy = storageFormat!!

        copy = patterns[0].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[1].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[2].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[3].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[4].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.BYTE).toString()))
        copy = patterns[5].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.percentage(iav, itot).toString()))

        copy = patterns[6].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[7].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[8].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[9].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[10].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.BYTE).toString()))

        copy = patterns[11].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[12].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[13].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[14].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[15].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.BYTE).toString()))
        copy = patterns[16].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.percentage(eav, etot).toString()))

        copy = patterns[17].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[18].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[19].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[20].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[21].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.BYTE).toString()))

        copy = patterns[22].matcher(copy).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))

        copy = patterns[23].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(iav.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[24].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(itot.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[25].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(eav.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[26].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(etot.toLong(), SystemUtils.GIGA).toString()))

        listener?.onUpdate(UIManager.Label.storage, UIUtils.span(context, copy, color, size))
    }

    private fun removableStorageCapacity(): String {
        val volume = context.getExternalFilesDirs(null)
            .drop(1)
            .firstOrNull {
                it != null && Environment.isExternalStorageRemovable(it) &&
                    Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
            } ?: return "—"
        return try {
            val stat = StatFs(volume.path)
            UIManager.compactUnifiedCapacity(
                SystemUtils.formatSize(stat.availableBytes, SystemUtils.GIGA),
                SystemUtils.formatSize(stat.totalBytes, SystemUtils.GIGA)
            )
        } catch (_: Exception) {
            "—"
        }
    }
}
