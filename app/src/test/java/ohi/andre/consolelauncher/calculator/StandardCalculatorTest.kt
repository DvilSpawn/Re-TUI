package ohi.andre.consolelauncher.calculator

import org.junit.Assert.*
import org.junit.Test

class StandardCalculatorTest {
    @Test fun runningOperationsAndRepeatEquals() {
        val c = StandardCalculator()
        c.edit("2"); c.operator("+"); c.edit("3"); c.operator("*"); c.edit("4"); c.equals()
        assertEquals("20", c.operand)
        c.clear(); c.edit("2"); c.operator("+"); c.edit("3"); c.equals(); c.equals()
        assertEquals("8", c.operand)
        c.operator("*"); c.edit("4"); c.equals(); assertEquals("32", c.operand)
        c.edit("7"); assertEquals("7", c.operand)
        c.clearEntry(); c.equals(); c.equals(); assertEquals("0",c.operand)
    }
    @Test fun decimalPrecisionAndPercentages() {
        for ((a, op, b, expected) in listOf(
            listOf("0.1", "+", "0.2", "0.3"), listOf("9007199254740992", "+", "1", "9007199254740993"))) {
            val c = StandardCalculator(); c.edit(a); c.operator(op); c.edit(b); c.equals()
            assertEquals(expected, c.operand)
        }
        for ((op, expected) in listOf("+" to "220", "-" to "180", "*" to "20", "/" to "2000")) {
            val c = StandardCalculator(); c.edit("200"); c.operator(op); c.edit("10"); c.unary("%"); c.equals()
            assertEquals(expected, c.operand)
        }
    }
    @Test fun correctionsErrorsRootsAndMemory() {
        val c = StandardCalculator()
        c.edit("8"); c.operator("+"); c.operator("*"); c.edit("2"); c.equals(); assertEquals("16", c.operand)
        c.clear(); c.edit("9"); c.unary("√"); assertEquals("3", c.operand)
        c.edit("2"); c.unary("√"); assertTrue(c.approximate); assertTrue(c.operand.startsWith("1.414213562373095"))
        c.clear(); c.edit("1"); c.operator("/"); c.edit("0"); assertFalse(c.equals())
        assertEquals(StandardCalculator.Failure.DIVISION_BY_ZERO, c.error); assertEquals("0", c.operand)
        c.edit("2"); c.equals(); assertEquals("0.5", c.operand)
        c.memory("MS"); c.clear(); c.memory("MR"); assertEquals("0.5", c.operand)
        c.edit("-1"); assertFalse(c.unary("√")); assertEquals(StandardCalculator.Failure.DOMAIN, c.error)
    }
    @Test fun signedPendingOperand() {
        val c = StandardCalculator(); c.edit("2"); c.operator("+"); c.unary("±")
        assertEquals("-0",c.operand); c.edit("-3"); c.equals(); assertEquals("-1",c.operand)
        c.clear(); c.unary("±"); assertEquals("-0",c.operand); c.unary("±"); assertEquals("0",c.operand)
    }
    @Test fun stateRestorationAndValidation() {
        val c = StandardCalculator(); c.edit("12"); c.memory("MS"); c.operator("+")
        val restored = StandardCalculator(); restored.restore(c.snapshot()); restored.edit("3"); restored.equals()
        assertEquals("15", restored.operand); restored.memory("MR"); assertEquals("12", restored.operand)
        assertTrue(StandardCalculator.editable("-1.2e-3")); assertTrue(StandardCalculator.editable("1e-"))
        assertFalse(StandardCalculator.editable("2+3")); assertFalse(StandardCalculator.editable("one"))
    }
    @Test fun clearEntryMemoryAndFullPrecision() {
        val c = StandardCalculator(); c.edit("10"); c.memory("MS"); c.edit("2"); c.memory("M+"); c.memory("M−")
        c.clear(); c.memory("MR"); assertEquals("10",c.operand)
        c.operator("+"); c.edit("999"); c.clearEntry(); c.edit("5"); c.equals(); assertEquals("15",c.operand)
        c.memory("MC"); assertNull(c.memory)
        c.clear(); c.edit("1"); c.operator("/"); c.edit("3"); c.equals()
        assertTrue(c.approximate); assertEquals(34,c.operand.count { it == '3' })
        assertEquals("≈ 0.3333333333333333",StandardCalculator.display(c.operand,c.approximate))
        c.edit("7"); assertFalse(c.approximate)
        c.clear(); c.edit("9"); c.unary("1/x"); assertTrue(c.approximate)
        c.clear(); c.edit("-3"); c.unary("x²"); assertEquals("9",c.operand)
    }

}
