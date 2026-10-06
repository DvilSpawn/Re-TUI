package ohi.andre.consolelauncher.calculator
import org.junit.Assert.*
import org.junit.Test
class CalculatorSessionTest {
    @Test fun displayRoundsWithoutChangingReusableUnits() {
        assertEquals("10", CalculatorSession.display("1E+1"))
        assertEquals("0.000946969696969697 mi", CalculatorSession.display("0.000946969696969696969696969696969697 mi"))
        assertEquals("1/3", CalculatorSession.display("1/3"))
    }
    @Test fun substitutesWholeAnswerTokensWithGroupingAndUnits() {
        assertEquals("(5 ft) * 2", CalculatorSession.expand("ans * 2", "5 ft"))
        assertEquals("(1/3)+(1/3)", CalculatorSession.expand("ANS+ans", "1/3"))
        assertEquals("answer + ans2", CalculatorSession.expand("answer + ans2", null))
        assertNull(CalculatorSession.expand("ans * 2", null))
    }
}
