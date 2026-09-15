package ohi.andre.consolelauncher.managers.modules

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ReminderRemovalTest {
    @Test fun removesSavedRemindersWithAndWithoutAlarmTokens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = ReminderManager.list(context).map { it.id }.toSet()
        for (scheduled in listOf(false, true)) {
            val id = "removal-test-${System.nanoTime()}"
            val at = if (scheduled) System.currentTimeMillis() + 86_400_000 else 1L
            val prefs = context.getSharedPreferences("retui_reminders", 0)
            fun token(): PendingIntent? = PendingIntent.getBroadcast(
                context, abs(id.hashCode()), Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                ReminderManager.save(context, ReminderManager.Reminder(id, "Removal test", at))
                assertNotNull(ReminderManager.get(context, id))
                if (scheduled) assertNotNull(token()) else assertNull(token())
                ReminderManager.remove(context, id)
                assertNull(ReminderManager.get(context, id))
                assertFalse(prefs.contains("title_$id"))
                assertFalse(prefs.contains("at_$id"))
                ReminderManager.remove(context, id) // Repeated removal must also be harmless.
                assertEquals(before, ReminderManager.list(context).map { it.id }.toSet())
            } finally {
                token()?.cancel()
                ReminderManager.remove(context, id)
            }
        }
    }
}
