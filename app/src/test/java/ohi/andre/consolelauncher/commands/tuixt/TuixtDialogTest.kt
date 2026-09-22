package ohi.andre.consolelauncher.commands.tuixt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuixtDialogTest {
    @Test fun searchableOptionsKeepOriginalSelectionIndices() {
        assertEquals(
            listOf(1, 3),
            TuixtDialog.matchingOptionIndices(
                listOf("Clear slot", "Reddit (com.reddit.frontpage)", "Google", "RedReader (org.quantumbadger.redreader)"),
                "red"
            )
        )
    }

    @Test fun editorRowsDoNotStackThePanelBackground() {
        val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "app/src/main").isDirectory }
        val adapter = File(root, "app/src/main/java/ohi/andre/consolelauncher/commands/tuixt/TuixtAdapter.kt").readText()
        assertTrue(adapter.contains("Theme.settings_row_background_color"))
        assertFalse(adapter.contains("surfaceColor()"))
    }
}
