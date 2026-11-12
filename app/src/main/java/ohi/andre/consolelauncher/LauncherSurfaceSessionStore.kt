package ohi.andre.consolelauncher

internal data class TermuxSurfaceSession(
    val consoleBuffer: String = "",
    val inputDraft: String = "",
    val scrollY: Int = 0,
    val appId: String? = null,
    val appFnKeyMode: Boolean = false,
    val workspaceFnKeyMode: Boolean = false,
    val workspaceLocalCommandMode: Boolean = false,
    val workspaceLocalCommandDraft: String = ""
)

internal data class LauncherSurfaceSession(
    val termux: TermuxSurfaceSession = TermuxSurfaceSession()
)

/** Process-local state only. Android naturally clears it after process death. */
internal object LauncherSurfaceSessionStore {
    private var session = LauncherSurfaceSession()

    @Synchronized
    fun snapshot(): LauncherSurfaceSession = session.copy(
        termux = session.termux.copy()
    )

    @Synchronized
    fun saveTermux(termux: TermuxSurfaceSession) {
        session = session.copy(termux = termux)
    }

    @Synchronized
    fun resetForTests() {
        session = LauncherSurfaceSession()
    }
}
