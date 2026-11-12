package ohi.andre.consolelauncher.managers.modes

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeModeIsolationTest {
    private val ui = SourceFiles.kotlin("UIManager.kt").readText()

    @Test fun theLauncherDoesNotBranchOnNamedModes() {
        listOf("isDot(", "isMono(", "isTerminal(", "isSearch(", "usesSearch(", "managesOwnKeyboard()").forEach {
            assertFalse("UIManager checks LauncherMode.$it; give the mode a HomeMode hook", ui.contains("LauncherMode.$it"))
        }
    }

    @Test fun modeScreensBelongToTheirModes() {
        listOf("DotHomeView", "MonoHomeView", "TerminalHomeView", "SearchModuleCarousel").forEach {
            assertFalse("UIManager uses $it directly", ui.contains(it))
        }
    }
}
