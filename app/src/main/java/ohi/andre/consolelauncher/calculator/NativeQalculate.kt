package ohi.andre.consolelauncher.calculator

/** Called only on QalculateService's single worker thread, inside its disposable process. */
internal object NativeQalculate {
    init { System.loadLibrary("retui_qalculate") }

    // First result byte is 0 for success or 1 for an engine error; remaining bytes are UTF-8.
    external fun evaluate(expression: ByteArray, degrees: Boolean, exact: Boolean): ByteArray

    fun calculate(expression: String, degrees: Boolean, exact: Boolean): CalculatorEngine.Result {
        val response = evaluate(expression.toByteArray(Charsets.UTF_8), degrees, exact)
        check(response.isNotEmpty() && response[0].toInt() in 0..1)
        return CalculatorEngine.Result(
            String(response, 1, response.size - 1, Charsets.UTF_8), response[0].toInt() != 0
        )
    }
}
