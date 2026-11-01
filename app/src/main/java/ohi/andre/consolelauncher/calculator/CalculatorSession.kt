package ohi.andre.consolelauncher.calculator

internal object CalculatorSession {
    private val ans = Regex("(?<![\\p{L}\\p{N}_])ans(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
    private val answerAssignment = Regex("^\\s*ans\\s*(?::=|=(?!=))\\s*(.*)$", RegexOption.IGNORE_CASE)
    const val GROUPING_INTERNATIONAL = "international"
    const val GROUPING_INDIAN = "indian"
    const val GROUPING_NONE = "none"
    val GROUPINGS = listOf(GROUPING_INTERNATIONAL, GROUPING_INDIAN, GROUPING_NONE)

    /** A result as shown: rounded to 16 digits, and with [grouping] separators in the whole part (1,400,000 or 14,00,000). */
    fun display(value: String, grouping: String = GROUPING_NONE): String {
        val match = Regex("^([+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(\\s+[^0-9].*)?$").matchEntire(value) ?: return value
        val number = match.groupValues[1].toBigDecimalOrNull() ?: return value
        val plain = number.round(java.math.MathContext(16, java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString()
        return group(plain, grouping) + match.groupValues[2]
    }

    private fun group(plain: String, grouping: String): String {
        if (grouping != GROUPING_INTERNATIONAL && grouping != GROUPING_INDIAN) return plain
        val sign = plain.takeWhile { it == '-' || it == '+' }
        val whole = plain.drop(sign.length).substringBefore('.')
        val fraction = plain.substringAfter('.', "").let { if (it.isEmpty()) "" else ".$it" }
        if (whole.length <= 3) return plain
        // International: groups of three. Indian: the last three digits, then pairs (lakh, crore).
        val head = whole.dropLast(3)
        val groups = if (grouping == GROUPING_INDIAN) head.reversed().chunked(2).joinToString(",").reversed()
            else head.reversed().chunked(3).joinToString(",").reversed()
        return sign + groups + "," + whole.takeLast(3) + fraction
    }
    fun expand(expression: String, answer: String?): String? {
        // Assigning ans seeds the next successful answer; only its RHS can reference the old answer.
        val assignment = answerAssignment.matchEntire(expression)
        val calculation = assignment?.groupValues?.get(1) ?: expression
        val expanded = if (ans.containsMatchIn(calculation)) {
            answer?.let { value -> ans.replace(calculation) { "($value)" } } ?: return null
        } else calculation
        // A leading operator continues the terminal result, but an assignment RHS starts fresh.
        return if (assignment == null && answer != null && expanded.trimStart().firstOrNull()?.let { it in "+-−*×/÷^" } == true) {
            "($answer)${expanded.trimStart()}"
        } else expanded
    }
}
