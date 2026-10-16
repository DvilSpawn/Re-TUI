package ohi.andre.consolelauncher.managers.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxWorkspaceLauncherManagerTest {
    private fun launcher(directory: String?) = TermuxWorkspaceLauncherManager.Launcher(
        "worknotes", "Work notes", "nvim inbox.md", false, directory)

    @Test fun ordinaryLaunchersKeepTheirExactCommand() {
        assertEquals("nvim inbox.md", launcher(null).launchCommand)
        assertEquals("nvim inbox.md", launcher(" ").launchCommand)
    }

    @Test fun migratedWorkingDirectoryIsQuotedAndCommandRunsOnlyAfterSuccessfulCd() {
        assertEquals("cd -- '/data/notes and tasks' && {\nnvim inbox.md\n}",
            launcher("/data/notes and tasks").launchCommand)
        assertEquals("cd -- '/data/it'\"'\"'s notes' && {\nnvim inbox.md\n}",
            launcher("/data/it's notes").launchCommand)
        assertEquals("cd -- '/data/\$(touch bad)' && {\nnvim inbox.md\n}",
            launcher("/data/\$(touch bad)").launchCommand)
    }

    @Test fun homeRelativeDirectoryExpandsOnlyHomeVariable() {
        assertEquals("cd -- \"\$HOME\" && {\nnvim inbox.md\n}", launcher("~").launchCommand)
        assertEquals("cd -- \"\$HOME\"/'notes/\$(touch bad)' && {\nnvim inbox.md\n}",
            launcher("~/notes/\$(touch bad)").launchCommand)
    }
}
