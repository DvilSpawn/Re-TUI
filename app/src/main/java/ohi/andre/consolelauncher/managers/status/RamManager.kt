package ohi.andre.consolelauncher.managers.status

import android.app.ActivityManager
import android.content.Context
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
import ohi.andre.consolelauncher.managers.xml.options.Ui

class RamManager(
    context: Context,
    delay: Long,
    private val size: Int,
    private val listener: StatusUpdateListener?
) : StatusManager(context, delay) {
    private val av = "%av"
    private val tot = "%tot"

    private var ramPatterns: MutableList<Pattern>? = null
    private var ramFormat: String? = null
    private var color = 0
    private var compactValue = ""

    fun compactValue(): String = compactValue

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val memory = ActivityManager.MemoryInfo()

    override fun update() {
        if (ramFormat == null) {
            ramFormat = XMLPrefsManager.get(Behavior.ram_format)
            color = XMLPrefsManager.getColor(Theme.ram_text_color)
        }

        if (ramPatterns == null) {
            ramPatterns = ArrayList()
            ramPatterns!!.add(Pattern.compile(av + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(av + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(av + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(av + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(av + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(av + "%", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            ramPatterns!!.add(Pattern.compile(tot + "tb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(tot + "gb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(tot + "mb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(tot + "kb", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))
            ramPatterns!!.add(Pattern.compile(tot + "b", Pattern.CASE_INSENSITIVE or Pattern.LITERAL))

            ramPatterns!!.add(Tuils.patternNewline)
        }

        val patterns = ramPatterns!!
        var copy = ramFormat!!

        val available = SystemUtils.freeRam(activityManager, memory)
        val total = SystemUtils.totalRam() * 1024L.toDouble()
        compactValue = UIManager.compactUnifiedCapacity(
            SystemUtils.formatSize(available.toLong(), SystemUtils.GIGA),
            SystemUtils.formatSize(total.toLong(), SystemUtils.GIGA)
        )

        copy = patterns[0].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(available.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[1].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(available.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[2].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(available.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[3].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(available.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[4].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(available.toLong(), SystemUtils.BYTE).toString()))
        copy = patterns[5].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.percentage(available, total).toString()))

        copy = patterns[6].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(total.toLong(), SystemUtils.TERA).toString()))
        copy = patterns[7].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(total.toLong(), SystemUtils.GIGA).toString()))
        copy = patterns[8].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(total.toLong(), SystemUtils.MEGA).toString()))
        copy = patterns[9].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(total.toLong(), SystemUtils.KILO).toString()))
        copy = patterns[10].matcher(copy).replaceAll(Matcher.quoteReplacement(SystemUtils.formatSize(total.toLong(), SystemUtils.BYTE).toString()))

        copy = patterns[11].matcher(copy).replaceAll(Matcher.quoteReplacement(Tuils.NEWLINE))

        listener?.onUpdate(UIManager.Label.ram, UIUtils.span(context, copy, color, size))
    }
}
