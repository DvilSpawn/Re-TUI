package ohi.andre.consolelauncher.integrations.tasker

import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiTaskerInputTest {
    @Test
    fun serializedInputKeysRemainStable() {
        val keys = RetuiTaskerInput::class.java.declaredFields.mapNotNull { field ->
            field.getAnnotation(com.joaomgcd.taskerpluginlibrary.input.TaskerInputField::class.java)?.key
        }
        assertEquals(listOf("action", "preset", "theme_element", "value", "module", "text"), keys)
    }

    @Test
    fun exposesOnlyFocusedSafeActions() {
        assertEquals(6, RetuiTaskerHelper.ACTIONS.size)
        assertTrue(RetuiTaskerHelper.ACTIONS.contains(TaskerIntegrationManager.ACTION_APPLY_PRESET))
        assertTrue(RetuiTaskerHelper.ACTIONS.contains(TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT))
        assertTrue(RetuiTaskerHelper.ACTIONS.none { it.contains("command") || it.contains("shell") })
    }
}
