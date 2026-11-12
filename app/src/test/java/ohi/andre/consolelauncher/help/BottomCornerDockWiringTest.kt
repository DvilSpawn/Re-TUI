package ohi.andre.consolelauncher.help

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.*
import org.junit.Test

class BottomCornerDockWiringTest {
    @Test fun allThreePanesUseCornerOnlyReservationsRatherThanABottomBand() {
        for ((surface, activity) in listOf("irc" to "IrcActivity", "calculator" to "CalculatorActivity", "offline_help" to "OfflineHelpActivity")) {
            val xml = SourceFiles.repo("app/src/main/res/layout/${surface}_surface.xml").readText()
            assertFalse(xml.contains("android:paddingBottom=\"48dp\""))
            assertFalse(xml.contains("android:paddingBottom=\"10dp\""))
            val source = SourceFiles.kotlin("$activity.kt").readText()
            assertTrue(source.contains("windowPanel.reserveBottomCorners("))
        }
        val panel = SourceFiles.kotlin("HelpWindowPanel.kt").readText()
        assertTrue(panel.contains("val insetStart = if (expanded || view !== protected) 0"))
        assertTrue(panel.contains("val insetEnd = if (expanded || view !== protected) 0"))
    }
}
