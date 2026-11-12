package ohi.andre.consolelauncher.commands.tuixt

import ohi.andre.consolelauncher.SourceFiles
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
        val adapter = SourceFiles.kotlin("TuixtAdapter.kt").readText()
        assertTrue(adapter.contains("Theme.settings_row_background_color"))
        assertFalse(adapter.contains("surfaceColor()"))
    }
}
