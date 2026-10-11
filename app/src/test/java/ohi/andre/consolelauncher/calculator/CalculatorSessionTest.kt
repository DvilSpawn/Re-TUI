package ohi.andre.consolelauncher.calculator
import org.junit.Assert.*
import org.junit.Test
class CalculatorSessionTest {
    @Test fun leadingOperatorsContinueThePreviousResultWithoutTypingAns() {
        for (operator in listOf("+", "-", "−", "*", "×", "/", "÷", "^")) {
            assertEquals("(10)$operator 5", CalculatorSession.expand("  $operator 5", "10"))
        }
        assertEquals("(1/3)*3", CalculatorSession.expand("*3", "1/3"))
        assertEquals("(5 ft)×2", CalculatorSession.expand("×2", "5 ft"))
        assertEquals("(10)+(10)", CalculatorSession.expand("+ans", "10"))
        assertEquals("5 + 5", CalculatorSession.expand("5 + 5", "99"))
        assertEquals("(-5)", CalculatorSession.expand("(-5)", "99"))
        assertEquals("-5", CalculatorSession.expand("-5", null))
        assertEquals("+5", CalculatorSession.expand("+5", null))
        assertEquals("-5", CalculatorSession.expand("ans = -5", "99"))
        assertEquals("+5", CalculatorSession.expand("ans := +5", "99"))
        assertEquals("tax = -5", CalculatorSession.expand("tax = -5", "99"))
    }
    @Test fun answerAssignmentSeedsOnlyTheRhsWithoutSubstitutingItsDestination() {
        assertEquals("5", CalculatorSession.expand("ans = 5", null))
        assertEquals("5", CalculatorSession.expand("ANS:=5", "99"))
        assertEquals("(5) + 5", CalculatorSession.expand("ans = ans + 5", "5"))
        assertNull(CalculatorSession.expand("ans = ans + 5", null))
        assertEquals("", CalculatorSession.expand("ans = ", "5"))
        assertEquals("(5) == 5", CalculatorSession.expand("ans == 5", "5"))
        assertEquals("tax = 18%", CalculatorSession.expand("tax = 18%", null))
        assertEquals("answer = (5)", CalculatorSession.expand("answer = ans", "5"))
    }
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
