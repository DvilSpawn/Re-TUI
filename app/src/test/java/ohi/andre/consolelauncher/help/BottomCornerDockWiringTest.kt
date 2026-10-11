package ohi.andre.consolelauncher.help

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class BottomCornerDockWiringTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }
    @Test fun allThreePanesUseCornerOnlyReservationsRatherThanABottomBand() {
        for ((surface, activity) in listOf("community" to "community/CommunityActivity", "calculator" to "calculator/CalculatorActivity", "offline_help" to "help/OfflineHelpActivity")) {
            val xml = File(root, "app/src/main/res/layout/${surface}_surface.xml").readText()
            assertFalse(xml.contains("android:paddingBottom=\"48dp\""))
            assertFalse(xml.contains("android:paddingBottom=\"10dp\""))
            val source = File(root, "app/src/main/java/ohi/andre/consolelauncher/$activity.kt").readText()
            assertTrue(source.contains("windowPanel.reserveBottomCorners("))
        }
        val panel = File(root, "app/src/main/java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt").readText()
        assertTrue(panel.contains("val insetStart = if (expanded || view !== protected) 0"))
        assertTrue(panel.contains("val insetEnd = if (expanded || view !== protected) 0"))
    }
}
