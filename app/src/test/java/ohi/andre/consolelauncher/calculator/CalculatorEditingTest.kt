package ohi.andre.consolelauncher.calculator
import org.junit.Assert.*
import org.junit.Test
class CalculatorEditingTest {
    @Test fun cursorSelectionAndUnicode() {
        assertEquals(CalculatorEditing.Edit("12+3",3),CalculatorEditing.insert("123",2,2,"+"))
        assertEquals(CalculatorEditing.Edit("1π3",2),CalculatorEditing.insert("123",1,2,"π"))
        assertEquals(CalculatorEditing.Edit("ab",1),CalculatorEditing.backspace("a😀b",3,3))
        assertEquals(CalculatorEditing.Edit("14",1),CalculatorEditing.backspace("1234",3,1))
        assertEquals(CalculatorEditing.Edit("sin()",4),CalculatorEditing.wrap("",0,0,"sin(",")"))
        assertEquals(CalculatorEditing.Edit("sin(2+3)",8),CalculatorEditing.wrap("2+3",0,3,"sin(",")"))
    }
    @Test fun historyLimitAndDuplicateSubmissions() {
        val h = CalculatorHistory()
        repeat(110) { h.add(CalculatorHistory.Entry("standard",it.toString(),it.toString(),it.toString())) }
        assertEquals(100,h.entries.size); assertEquals("109",h.entries.first().input)
        h.add(h.entries.first()); assertEquals(100,h.entries.size)
        h.entries.removeAt(0); assertEquals("108",h.entries.first().input)
    }
}
