package ohi.andre.consolelauncher.commands.tuixt

import org.junit.Assert.assertEquals
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
}
