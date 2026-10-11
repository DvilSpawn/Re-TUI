package ohi.andre.consolelauncher.calculator

internal object CalculatorSession {
    private val ans = Regex("(?<![\\p{L}\\p{N}_])ans(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
    private val answerAssignment = Regex("^\\s*ans\\s*(?::=|=(?!=))\\s*(.*)$", RegexOption.IGNORE_CASE)
    fun display(value: String): String {
        val match = Regex("^([+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(\\s+[^0-9].*)?$").matchEntire(value) ?: return value
        val number = match.groupValues[1].toBigDecimalOrNull() ?: return value
        return number.round(java.math.MathContext(16, java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString() + match.groupValues[2]
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
