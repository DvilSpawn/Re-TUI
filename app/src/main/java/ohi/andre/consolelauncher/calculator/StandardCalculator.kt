package ohi.andre.consolelauncher.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Keypad state only; command/scientific expressions keep their own precedence. */
class StandardCalculator {
    enum class Failure { INVALID, DIVISION_BY_ZERO, DOMAIN, LIMIT }
    class CalculationException(val failure: Failure) : IllegalArgumentException()
    var operand = "0"; private set
    var context = ""; private set
    var completed = false; private set
    var waiting = false; private set
    var approximate = false; private set
    var error: Failure? = null; private set
    var memory: BigDecimal? = null; private set
    private var accumulator: BigDecimal? = null
    private var pending: String? = null
    private var repeatOperator: String? = null
    private var repeatValue: BigDecimal? = null

    fun edit(text: String) {
        if (completed && pending == null) { accumulator = null; context = ""; approximate = false }
        operand = text
        completed = false
        waiting = false
        error = null
        repeatOperator = null
        repeatValue = null
    }

    fun beginEdit() { completed = false; waiting = false }
    fun startsNewEntry() = completed || waiting || error != null

    fun clear() {
        operand = "0"; context = ""; completed = false; waiting = false
        accumulator = null; pending = null; repeatOperator = null; repeatValue = null
        approximate = false; error = null
    }
    fun clearEntry() {
        operand = "0"; completed = false; waiting = false; error = null
        repeatOperator = null; repeatValue = null
        if (pending == null) { accumulator = null; context = ""; approximate = false }
    }

    fun operator(op: String) = guarded {
        require(op in listOf("+", "-", "*", "/"))
        val value = number(operand)
        if (pending != null && !waiting) accumulator = apply(accumulator!!, pending!!, value)
        else if (!waiting) accumulator = value
        pending = op
        operand = raw(accumulator ?: value)
        context = "$operand $op"
        waiting = true; completed = false; repeatOperator = null; repeatValue = null
    }

    fun equals() = guarded {
        val right = if (completed && repeatValue != null) repeatValue!! else number(operand)
        val op = pending ?: if (completed) repeatOperator else null
        val left = if (pending != null) accumulator!! else number(operand)
        val answer = if (op == null) left else apply(left, op, right)
        context = if (op == null) operand else "${raw(left)} $op ${raw(right)}"
        if (op != null) { repeatOperator = op; repeatValue = right }
        operand = raw(answer); accumulator = answer; pending = null
        completed = true; waiting = false
    }

    fun unary(op: String) = guarded {
        if (op == "±") {
            operand = if (waiting) "-0" else if (operand.startsWith("-")) operand.drop(1) else "-" + operand.removePrefix("+")
            waiting = false; completed = false; repeatOperator = null; repeatValue = null
            return@guarded
        }
        val value = number(operand)
        val answer = when (op) {
            "±" -> value.negate()
            "%" -> if (pending == "+" || pending == "-") (accumulator ?: BigDecimal.ZERO).multiply(value).movePointLeft(2)
                   else value.movePointLeft(2)
            "1/x" -> divide(BigDecimal.ONE, value)
            "x²" -> value.multiply(value)
            "√" -> root(value)
            else -> throw CalculationException(Failure.INVALID)
        }
        operand = raw(answer); waiting = false; completed = false
        repeatOperator = null; repeatValue = null
    }

    fun memory(action: String) = guarded {
        when (action) {
            "MC" -> memory = null
            "MR" -> memory?.let { edit(raw(it)) }
            "MS" -> memory = number(operand)
            "M+" -> memory = (memory ?: BigDecimal.ZERO).add(number(operand))
            "M−" -> memory = (memory ?: BigDecimal.ZERO).subtract(number(operand))
        }
    }

    private fun guarded(action: () -> Unit): Boolean = try {
        action(); error = null; true
    } catch (e: CalculationException) { error = e.failure; false
    } catch (_: ArithmeticException) { error = Failure.LIMIT; false
    } catch (_: IllegalArgumentException) { error = Failure.INVALID; false }

    private fun apply(a: BigDecimal, op: String, b: BigDecimal): BigDecimal = when (op) {
        "+" -> a.add(b)
        "-" -> a.subtract(b)
        "*" -> a.multiply(b)
        "/" -> divide(a, b)
        else -> throw CalculationException(Failure.INVALID)
    }.also { if (it.precision() > 10000 || kotlin.math.abs(it.scale().toLong()) > 10000) throw CalculationException(Failure.LIMIT) }

    private fun divide(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.signum() == 0) throw CalculationException(Failure.DIVISION_BY_ZERO)
        return try { a.divide(b) } catch (_: ArithmeticException) {
            approximate = true; a.divide(b, PRECISION)
        }
    }

    private fun root(value: BigDecimal): BigDecimal {
        if (value.signum() < 0) throw CalculationException(Failure.DOMAIN)
        if (value.signum() == 0) return BigDecimal.ZERO
        val exponent = value.precision() - value.scale()
        var guess = BigDecimal.ONE.scaleByPowerOfTen(((exponent + 1) / 2 - if (exponent + 1 < 0 && (exponent + 1) % 2 != 0) 1 else 0))
        val work = MathContext(40, RoundingMode.HALF_EVEN)
        for (iteration in 0 until 160) {
            val next = guess.add(value.divide(guess, work)).divide(BigDecimal(2), work)
            if (next.compareTo(guess) == 0) break
            guess = next
        }
        val answer = guess.round(PRECISION).stripTrailingZeros()
        if (answer.multiply(answer).compareTo(value) != 0) approximate = true
        return answer
    }

    fun snapshot(): Map<String, String> = mapOf(
        "operand" to operand, "context" to context, "completed" to completed.toString(),
        "waiting" to waiting.toString(), "approximate" to approximate.toString(),
        "accumulator" to accumulator?.toString().orEmpty(), "pending" to pending.orEmpty(),
        "repeatOperator" to repeatOperator.orEmpty(), "repeatValue" to repeatValue?.toString().orEmpty(),
        "memory" to memory?.toString().orEmpty(), "error" to error?.name.orEmpty()
    )

    fun restore(values: Map<String, String>) {
        clear()
        operand = values["operand"] ?: "0"; context = values["context"].orEmpty()
        completed = values["completed"].toBoolean(); waiting = values["waiting"].toBoolean()
        approximate = values["approximate"].toBoolean()
        accumulator = values["accumulator"]?.toBigDecimalOrNull()
        pending = values["pending"]?.takeIf { it in listOf("+", "-", "*", "/") && accumulator != null }
        repeatOperator = values["repeatOperator"]?.takeIf { it in listOf("+", "-", "*", "/") }
        repeatValue = values["repeatValue"]?.toBigDecimalOrNull()
        memory = values["memory"]?.toBigDecimalOrNull()
        error = Failure.entries.firstOrNull { it.name == values["error"] }
    }

    companion object {
        val PRECISION = MathContext(34, RoundingMode.HALF_EVEN)
        private val EDITABLE = Regex("[+-]?(?:[0-9]*(?:\\.[0-9]*)?)(?:[eE][+-]?[0-9]*)?")
        fun editable(value: String) = value.length <= 1024 && EDITABLE.matches(value)
        fun number(value: String): BigDecimal {
            val number = value.toBigDecimalOrNull() ?: throw CalculationException(Failure.INVALID)
            if (number.precision() > 10000 || kotlin.math.abs(number.scale().toLong()) > 10000) throw CalculationException(Failure.LIMIT)
            return number
        }
        fun raw(value: BigDecimal): String = value.stripTrailingZeros().let {
            if (it.scale() in -16..34 && it.precision() <= 100) it.toPlainString() else it.toString()
        }
        fun display(value: String, approximate: Boolean): String {
            val n = value.toBigDecimalOrNull() ?: return value
            val rounded = if (approximate) n.round(MathContext(16, RoundingMode.HALF_EVEN)) else n
            return (if (approximate) "≈ " else "") + raw(rounded)
        }
    }
}
