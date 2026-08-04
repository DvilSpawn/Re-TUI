package ohi.andre.consolelauncher.integrations.tasker

import ohi.andre.consolelauncher.managers.tasker.TaskerIntegrationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetuiTaskerInputTest {
    @Test
    fun serializedInputKeysRemainStable() {
        val keys = RetuiTaskerInput::class.java.declaredFields.mapNotNull { field ->
            field.getAnnotation(com.joaomgcd.taskerpluginlibrary.input.TaskerInputField::class.java)?.key
        }
        assertEquals(listOf("action", "preset", "theme_element", "value", "module", "text", "space"), keys)
    }

    @Test
    fun exposesOnlyFocusedSafeActions() {
        assertEquals(7, RetuiTaskerHelper.ACTIONS.size)
        assertTrue(RetuiTaskerHelper.ACTIONS.contains(TaskerIntegrationManager.ACTION_APPLY_PRESET))
        assertTrue(RetuiTaskerHelper.ACTIONS.contains(TaskerIntegrationManager.ACTION_TERMINAL_OUTPUT))
        assertTrue(RetuiTaskerHelper.ACTIONS.contains(TaskerIntegrationManager.ACTION_SWITCH_SPACE))
        assertTrue(RetuiTaskerHelper.ACTIONS.none { it.contains("command") || it.contains("shell") })
    }

    @Test
    fun switchSpaceRequiresTargetBeforeSavingToTasker() {
        assertEquals(
            "Choose a Space.",
            RetuiTaskerHelper.validationError(
                RetuiTaskerInput(TaskerIntegrationManager.ACTION_SWITCH_SPACE, space = " ")
            )
        )
        assertNull(
            RetuiTaskerHelper.validationError(
                RetuiTaskerInput(TaskerIntegrationManager.ACTION_SWITCH_SPACE, space = "%space")
            )
        )
    }
}
