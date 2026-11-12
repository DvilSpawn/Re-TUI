package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorAuditTest {
    @Test
    fun semanticSurfacesDoNotIntroduceHardcodedUiColors() {
        val files = listOf(
            "TuixtTheme.kt",
            "FocusFrictionStyle.kt",
            "RetuiThemeBridge.kt",
            "ModuleDockButtonFactory.kt",
            "AppDrawerPaneManager.kt",
            "AndroidWidgetDrawerManager.kt",
            "LauncherPillStyle.kt"
        )
        val forbidden = Regex(
            "Color\\.(?:RED|GREEN|BLUE|WHITE|BLACK|YELLOW|CYAN|MAGENTA|rgb|argb|parseColor)" +
                "|ColorUtils\\.(?:setAlphaComponent|blendARGB)|\"#[0-9A-Fa-f]{6,8}\""
        )

        val violations = files.flatMap { name ->
            val file = SourceFiles.kotlin(name)
            file.readLines().mapIndexedNotNull { index, line ->
                if (forbidden.containsMatchIn(line)) "$name:${index + 1}: ${line.trim()}" else null
            }
        }
        val layoutFiles = listOf(
            "android_widget_drawer.xml",
            "apps_drawer.xml",
            "clock_overlay.xml",
            "color_picker_dialog.xml",
            "module_text_panel.xml",
            "music_module.xml",
            "notification_module.xml",
            "pomodoro_overlay.xml",
            "tuixt_row.xml"
        )
        val layoutViolations = layoutFiles.flatMap { name ->
            val file = SourceFiles.repo("app/src/main/res/layout/$name")
            file.readLines().mapIndexedNotNull { index, line ->
                if (Regex("#[0-9A-Fa-f]{6,8}").containsMatchIn(line)) "$name:${index + 1}: ${line.trim()}" else null
            }
        }

        val allViolations = violations + layoutViolations
        assertTrue("Use a Theme role or add a documented exception:\n${allViolations.joinToString("\n")}", allViolations.isEmpty())
    }
}
