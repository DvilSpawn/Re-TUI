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
    fun keepsPodcastAndTermuxStateTogether() {
        LauncherSurfaceSessionStore.savePodcast(
            PodcastSurfaceSession(2, "science", "android", 480)
        )
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

        assertEquals(PodcastSurfaceSession(2, "science", "android", 480), session.podcast)
        assertEquals("output", session.termux.consoleBuffer)
        assertEquals("curl ", session.termux.inputDraft)
        assertEquals("retui", session.termux.appId)
        assertEquals("switch 2", session.termux.workspaceLocalCommandDraft)
    }

    @Test
    fun explicitPodcastResetDoesNotDiscardTermuxSession() {
        LauncherSurfaceSessionStore.savePodcast(PodcastSurfaceSession(3, "news", "daily", 90))
        LauncherSurfaceSessionStore.saveTermux(TermuxSurfaceSession(appId = "radio"))

        LauncherSurfaceSessionStore.resetPodcast()

        val session = LauncherSurfaceSessionStore.snapshot()
        assertEquals(PodcastSurfaceSession(), session.podcast)
        assertEquals("radio", session.termux.appId)
    }

    @Test
    fun resetClearsProcessLocalState() {
        LauncherSurfaceSessionStore.savePodcast(PodcastSurfaceSession(1, "tech", "", 30))
        LauncherSurfaceSessionStore.saveTermux(TermuxSurfaceSession(appId = "shell"))

        LauncherSurfaceSessionStore.resetForTests()

        val session = LauncherSurfaceSessionStore.snapshot()
        assertEquals(PodcastSurfaceSession(), session.podcast)
        assertEquals("", session.termux.consoleBuffer)
        assertNull(session.termux.appId)
    }
}
