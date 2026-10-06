package ohi.andre.consolelauncher.calculator

internal object CalculatorSession {
    private val ans = Regex("(?<![\\p{L}\\p{N}_])ans(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)
    fun display(value: String): String {
        val match = Regex("^([+-]?[0-9]+(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?)(\\s+[^0-9].*)?$").matchEntire(value) ?: return value
        val number = match.groupValues[1].toBigDecimalOrNull() ?: return value
        return number.round(java.math.MathContext(16, java.math.RoundingMode.HALF_EVEN)).stripTrailingZeros().toPlainString() + match.groupValues[2]
    }
    fun expand(expression: String, answer: String?): String? {
        if (!ans.containsMatchIn(expression)) return expression
        return answer?.let { value -> ans.replace(expression) { "($value)" } }
    }
}
