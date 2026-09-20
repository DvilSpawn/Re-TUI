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

    fun normalize(raw: Map<Ui, String?>): Result {
        val resolved = settings.associateWith { setting ->
            var value = parse(raw[setting], setting.defaultValue())
            while (whole(value) < 1) value = value.add(BigDecimal.ONE)
            value.stripTrailingZeros().toPlainString()
        }.toMutableMap()
        val occupied = settings.asSequence()
            .filterNot { it == Ui.ascii_index }
            .mapTo(HashSet()) { whole(BigDecimal(resolved.getValue(it))) }
        var ascii = BigDecimal(resolved.getValue(Ui.ascii_index))
        while (!occupied.add(whole(ascii))) ascii = ascii.add(BigDecimal.ONE)
        resolved[Ui.ascii_index] = ascii.stripTrailingZeros().toPlainString()
        return Result(resolved, settings.any {
            format(parse(raw[it], it.defaultValue())) != resolved.getValue(it)
        })
    }

    fun isStatusIndex(value: Any?): Boolean = value in settings

    /** Explicit edits insert a row; loading a preset still preserves its existing groups. */
    fun reorder(raw: Map<Ui, String?>, edits: Map<Ui, String?>): Result {
        val original = normalize(raw).values
        val resolved = original.toMutableMap()
        for ((setting, text) in edits) {
            if (!isStatusIndex(setting)) continue
            val requested = text?.trim()?.toBigDecimalOrNull() ?: continue
            if (requested < BigDecimal.ONE) continue
            // Saving an unchanged field must not insert another row, even after a prior edit shifted it.
            if (requested.compareTo(BigDecimal(original.getValue(setting))) == 0) continue
            if (requested.compareTo(BigDecimal(resolved.getValue(setting))) == 0) continue
            if (setting == Ui.ascii_index || requested.stripTrailingZeros().scale() <= 0) {
                val row = whole(requested)
                for (other in settings) {
                    val value = BigDecimal(resolved.getValue(other))
                    if (other != setting && whole(value) >= row) {
                        resolved[other] = format(value.add(BigDecimal.ONE))
                    }
                }
            }
            resolved[setting] = format(requested)
        }
        val result = normalize(resolved).values
        return Result(result, result != original)
    }

    internal fun <T> groupVisible(items: List<Pair<Float, T>>): List<List<T>> {
        val rows = ArrayList<MutableList<T>>()
        var lastRow: Int? = null
        for ((value, item) in items.sortedBy { it.first }) {
            val row = value.toInt()
            if (row != lastRow) {
                rows.add(ArrayList())
                lastRow = row
            }
            rows.last().add(item)
        }
        return rows
    }

    private fun parse(value: String?, fallback: String?): BigDecimal =
        try { BigDecimal(value ?: fallback ?: "1") } catch (_: Exception) { BigDecimal(fallback ?: "1") }

    private fun whole(value: BigDecimal): Int = value.setScale(0, RoundingMode.FLOOR).toInt()
    private fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
