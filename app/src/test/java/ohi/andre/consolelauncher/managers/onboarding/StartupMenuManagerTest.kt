package ohi.andre.consolelauncher.managers.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class StartupMenuManagerTest {
    @Test
    fun visualStylesMapToTheExpectedChromeFlags() {
        assertEquals(false to false, StartupMenuManager.visualFlags("classic"))
        assertEquals(true to false, StartupMenuManager.visualFlags("cyberdeck"))
        assertEquals(true to true, StartupMenuManager.visualFlags("crt"))
        assertEquals(false to false, StartupMenuManager.visualFlags("unknown"))
    }
}
