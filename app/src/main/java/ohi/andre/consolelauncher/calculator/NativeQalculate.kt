package ohi.andre.consolelauncher.calculator

/** Called only on QalculateService's single worker thread, inside its disposable process. */
internal object NativeQalculate {
    init { System.loadLibrary("retui_qalculate") }

    // First result byte is bit 0 for an engine error, bit 1 for approximation; remaining bytes are UTF-8.
    external fun evaluate(expression: ByteArray, degrees: Boolean, exact: Boolean, preview: Boolean): ByteArray

    fun calculate(expression: String, degrees: Boolean, exact: Boolean, preview: Boolean = false): CalculatorEngine.Result {
        val response = evaluate(expression.toByteArray(Charsets.UTF_8), degrees, exact, preview)
        check(response.isNotEmpty() && response[0].toInt() in 0..(if (preview) 3 else 2))
        if (response[0].toInt() == 3) return CalculatorEngine.Result("", true, failure = "preview_unavailable")
        val payload = String(response, 1, response.size - 1, Charsets.UTF_8)
        val text = if (preview) payload.substringBefore('\u0000') else payload
        val parsed = if (preview) payload.substringAfter('\u0000', "").takeIf { it.isNotBlank() } else null
        val error = response[0].toInt() == 1
        val failure = if (!error) null else when {
            text.contains("zero", true) && (text.contains("div",true) || text.contains("denominator",true)) -> "division_by_zero"
            text.contains("domain",true) || text.contains("real number",true) || text.contains("positive",true) -> "domain"
            text.contains("not supported",true) || text.contains("unable to",true) -> "unsupported"
            else -> "syntax"
        }
        return CalculatorEngine.Result(text, error, response[0].toInt() == 2, if(error || preview) null else text, failure, parsed)
    }
}
