package ohi.andre.consolelauncher.managers.modules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ReminderManagerTest {
    @Test fun cliDateTimeIsStrictAnd24Hour() {
        val parsed = ReminderManager.parseCliDateTime("29/02/28", "21:30")!!
        Calendar.getInstance().apply {
            timeInMillis = parsed
            assertEquals(2028, get(Calendar.YEAR))
            assertEquals(Calendar.FEBRUARY, get(Calendar.MONTH))
            assertEquals(29, get(Calendar.DAY_OF_MONTH))
            assertEquals(21, get(Calendar.HOUR_OF_DAY))
            assertEquals(30, get(Calendar.MINUTE))
        }
        assertNull(ReminderManager.parseCliDateTime("29/02/27", "21:30"))
        assertNull(ReminderManager.parseCliDateTime("01/08/26", "9:30"))
        assertNull(ReminderManager.parseCliDateTime("01/08/26", "24:00"))
    }
}
