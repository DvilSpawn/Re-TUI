package ohi.andre.consolelauncher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherSurfaceSessionStoreTest {
    @After
    fun reset() {
        LauncherSurfaceSessionStore.resetForTests()
    }

    @Test
    fun keepsTermuxState() {
        LauncherSurfaceSessionStore.saveTermux(
            TermuxSurfaceSession(
                consoleBuffer = "output",
                inputDraft = "curl ",
                scrollY = 120,
                appId = "retui",
                appFnKeyMode = true,
                workspaceFnKeyMode = true,
                workspaceLocalCommandMode = true,
                workspaceLocalCommandDraft = "switch 2"
            )
        )

        val session = LauncherSurfaceSessionStore.snapshot()

        assertEquals("output", session.termux.consoleBuffer)
        assertEquals("curl ", session.termux.inputDraft)
        assertEquals("retui", session.termux.appId)
        assertEquals("switch 2", session.termux.workspaceLocalCommandDraft)
    }

    @Test
    fun resetClearsProcessLocalState() {
        LauncherSurfaceSessionStore.saveTermux(TermuxSurfaceSession(appId = "shell"))

        LauncherSurfaceSessionStore.resetForTests()

        val session = LauncherSurfaceSessionStore.snapshot()
        assertEquals("", session.termux.consoleBuffer)
        assertNull(session.termux.appId)
    }
}
