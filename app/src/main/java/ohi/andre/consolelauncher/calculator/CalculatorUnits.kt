package ohi.andre.consolelauncher.calculator
import ohi.andre.consolelauncher.R

/** Explicit identifiers from the bundled Qalculate definitions; factors stay in the engine. */
object CalculatorUnits {
    data class Unit(val label: Int, val id: String)
    data class Category(val label: Int, val units: List<Unit>)
    val categories = listOf(
        Category(R.string.calculator_category_length, listOf(Unit(R.string.calculator_unit_m, "m"),Unit(R.string.calculator_unit_km, "km"),Unit(R.string.calculator_unit_cm, "cm"),Unit(R.string.calculator_unit_mm, "mm"),Unit(R.string.calculator_unit_ft, "ft"),Unit(R.string.calculator_unit_in, "in"),Unit(R.string.calculator_unit_mi, "mi"))),
        Category(R.string.calculator_category_area, listOf(Unit(R.string.calculator_unit_m2, "m^2"),Unit(R.string.calculator_unit_km2, "km^2"),Unit(R.string.calculator_unit_ft2, "ft^2"),Unit(R.string.calculator_unit_ha, "ha"),Unit(R.string.calculator_unit_acre, "acre"))),
        Category(R.string.calculator_category_volume, listOf(Unit(R.string.calculator_unit_l, "L"),Unit(R.string.calculator_unit_ml, "mL"),Unit(R.string.calculator_unit_m3, "m^3"),Unit(R.string.calculator_unit_usgal, "gal"),Unit(R.string.calculator_unit_ukgal, "UK_gal"))),
        Category(R.string.calculator_category_mass, listOf(Unit(R.string.calculator_unit_kg, "kg"),Unit(R.string.calculator_unit_g, "g"),Unit(R.string.calculator_unit_lb, "lb"),Unit(R.string.calculator_unit_oz, "oz"))),
        Category(R.string.calculator_category_temperature, listOf(Unit(R.string.calculator_unit_c, "oC"),Unit(R.string.calculator_unit_f, "oF"),Unit(R.string.calculator_unit_k, "K"))),
        Category(R.string.calculator_category_time, listOf(Unit(R.string.calculator_unit_s, "s"),Unit(R.string.calculator_unit_min, "min"),Unit(R.string.calculator_unit_h, "h"),Unit(R.string.calculator_unit_day, "day"))),
        Category(R.string.calculator_category_speed, listOf(Unit(R.string.calculator_unit_ms, "m/s"),Unit(R.string.calculator_unit_kmh, "km/h"),Unit(R.string.calculator_unit_mph, "mph"))),
        Category(R.string.calculator_category_energy, listOf(Unit(R.string.calculator_unit_j, "J"),Unit(R.string.calculator_unit_kj, "kJ"),Unit(R.string.calculator_unit_kwh, "kW*h"))),
        Category(R.string.calculator_category_power, listOf(Unit(R.string.calculator_unit_w, "W"),Unit(R.string.calculator_unit_kw, "kW"))),
        Category(R.string.calculator_category_pressure, listOf(Unit(R.string.calculator_unit_pa, "Pa"),Unit(R.string.calculator_unit_kpa, "kPa"),Unit(R.string.calculator_unit_bar, "bar"),Unit(R.string.calculator_unit_psi, "psi"))),
        Category(R.string.calculator_category_data, listOf(Unit(R.string.calculator_unit_bit, "bit"),Unit(R.string.calculator_unit_byte, "byte"),Unit(R.string.calculator_unit_kb, "kbyte"),Unit(R.string.calculator_unit_mb, "Mbyte"),Unit(R.string.calculator_unit_gb, "Gbyte"),Unit(R.string.calculator_unit_kib, "Kibyte"),Unit(R.string.calculator_unit_mib, "Mibyte"),Unit(R.string.calculator_unit_gib, "Gibyte")))
    )
    fun expression(value: String, category: Int, source: Int, target: Int): String {
        StandardCalculator.number(value)
        val units = categories[category].units
        return "($value) ${units[source].id} to ${units[target].id}"
    }
}
