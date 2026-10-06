package ohi.andre.consolelauncher.calculator

/** Called only on QalculateService's single worker thread, inside its disposable process. */
internal object NativeQalculate {
    init { System.loadLibrary("retui_qalculate") }

    // First result byte is bit 0 for an engine error, bit 1 for approximation; remaining bytes are UTF-8.
    external fun evaluate(expression: ByteArray, degrees: Boolean, exact: Boolean): ByteArray

    fun calculate(expression: String, degrees: Boolean, exact: Boolean): CalculatorEngine.Result {
        val response = evaluate(expression.toByteArray(Charsets.UTF_8), degrees, exact)
        check(response.isNotEmpty() && response[0].toInt() in 0..2)
        val text = String(response, 1, response.size - 1, Charsets.UTF_8)
        val error = response[0].toInt() == 1
        val failure = if (!error) null else when {
            text.contains("zero", true) && (text.contains("div",true) || text.contains("denominator",true)) -> "division_by_zero"
            text.contains("domain",true) || text.contains("real number",true) || text.contains("positive",true) -> "domain"
            text.contains("not supported",true) || text.contains("unable to",true) -> "unsupported"
            else -> "syntax"
        }
        return CalculatorEngine.Result(text, error, response[0].toInt() == 2, if(error) null else text, failure)
    }
}
