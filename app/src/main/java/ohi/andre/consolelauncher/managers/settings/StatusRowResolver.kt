package ohi.andre.consolelauncher.managers.settings

import java.math.BigDecimal
import java.math.RoundingMode
import ohi.andre.consolelauncher.managers.xml.options.Ui

object StatusRowResolver {
    val settings = listOf(
        Ui.ram_index,
        Ui.device_index,
        Ui.time_index,
        Ui.battery_index,
        Ui.storage_index,
        Ui.network_index,
        Ui.notes_index,
        Ui.weather_index,
        Ui.unlock_index,
        Ui.ascii_index
    )

    data class Result(val values: Map<Ui, String>, val changed: Boolean)

    fun normalize(raw: Map<Ui, String?>, preferred: Ui? = null): Result {
        val requested = settings.associateWith { parse(raw[it], it.defaultValue()) }
        val order = settings.sortedWith(compareBy<Ui> { requested.getValue(it) }.thenBy { settings.indexOf(it) })
            .toMutableList()
        if (preferred in order) {
            order.remove(preferred)
            order.add(0, preferred!!)
        }

        val occupied = HashSet<Int>()
        val resolved = LinkedHashMap<Ui, String>()
        for (setting in order) {
            var value = requested.getValue(setting)
            while (whole(value) < 1 || !occupied.add(whole(value))) value = value.add(BigDecimal.ONE)
            resolved[setting] = value.stripTrailingZeros().toPlainString()
        }
        return Result(settings.associateWith { resolved.getValue(it) }, settings.any {
            format(parse(raw[it], it.defaultValue())) != resolved.getValue(it)
        })
    }

    fun occupiedRow(raw: Map<Ui, String?>, edited: Ui, value: String): Ui? {
        val row = whole(parse(value, edited.defaultValue()))
        return settings.firstOrNull { it != edited && whole(parse(raw[it], it.defaultValue())) == row }
    }

    fun isStatusIndex(value: Any?): Boolean = value in settings

    private fun parse(value: String?, fallback: String?): BigDecimal =
        try { BigDecimal(value ?: fallback ?: "1") } catch (_: Exception) { BigDecimal(fallback ?: "1") }

    private fun whole(value: BigDecimal): Int = value.setScale(0, RoundingMode.FLOOR).toInt()
    private fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
