package ohi.andre.consolelauncher.managers.settings

import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Theme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorResolverTest {
    @Test
    fun advancedRolesDefaultToAutoAndResolve() {
        val advanced = Theme.entries.filter { it.advanced }
        assertEquals("Every advanced color needs its own description", advanced.size, advanced.map { it.infoRes() }.toSet().size)
        Theme.entries.filter { it.advanced }.forEach { role ->
            assertEquals(XMLPrefsSave.AUTO_COLOR, role.type())
            assertEquals("auto", role.defaultValue())
            ThemeColorResolver.resolve(role, { null }, emptyMap())
        }
    }

    @Test
    fun explicitTransparentIsNotInheritance() {
        val values = mapOf(
            Theme.terminal_window_background_color to "#FF112233",
            Theme.button_background_color to "#00000000"
        )
        assertEquals(
            0x00000000,
            ThemeColorResolver.resolve(Theme.button_background_color, values::get, emptyMap())
        )
    }

    @Test
    fun autoInheritsAndPreviewWins() {
        val values = mapOf(
            Theme.terminal_window_background_color to "#FF112233",
            Theme.button_background_color to "auto"
        )
        assertEquals(
            0xFF112233.toInt(),
            ThemeColorResolver.resolve(Theme.button_background_color, values::get, emptyMap())
        )
        assertEquals(
            0x80445566.toInt(),
            ThemeColorResolver.resolve(
                Theme.button_background_color,
                values::get,
                mapOf(Theme.button_background_color to "#80445566")
            )
        )
    }

    @Test
    fun moduleHeaderControlsCanOverrideTerminalHeaderWithoutChangingIt() {
        val values = mapOf(Theme.terminal_header_background_color to "#FF112233")
        assertEquals(0xFF112233.toInt(), ThemeColorResolver.resolve(Theme.module_header_control_background_color, values::get, emptyMap()))
        assertEquals(0xFF445566.toInt(), ThemeColorResolver.resolve(Theme.module_header_control_background_color, values::get, mapOf(Theme.module_header_control_background_color to "#FF445566")))
        assertEquals(0xFF112233.toInt(), ThemeColorResolver.resolve(Theme.terminal_header_background_color, values::get, emptyMap()))
    }

    @Test
    fun derivedColorsNeverIncreaseAlpha() {
        val transparent = ThemeColorResolver.blendPreservingAlpha(0x00112233, 0xFFFFFFFF.toInt(), 0.8f)
        val translucent = ThemeColorResolver.blendPreservingAlpha(0x40112233, 0xFFFFFFFF.toInt(), 0.8f)
        assertEquals(0, transparent ushr 24)
        assertTrue(translucent ushr 24 <= 0x40)
    }

    @Test
    fun parserAcceptsSixAndEightDigitHexOnly() {
        assertEquals(0xFF09121C.toInt(), ThemeColorResolver.parse("#09121c"))
        assertEquals(0x8009121C.toInt(), ThemeColorResolver.parse("#8009121c"))
        assertEquals(null, ThemeColorResolver.parse("auto"))
        assertEquals(null, ThemeColorResolver.parse("#12345"))
    }

    @Test
    fun invalidDraftsNeverEnterPreviewAndClearDropsValidDrafts() {
        ThemeColorResolver.clearPreview()
        assertTrue(!ThemeColorResolver.preview(Theme.button_background_color, "#12345"))
        assertTrue(!ThemeColorResolver.hasPreview())
        assertTrue(ThemeColorResolver.preview(Theme.button_background_color, "#80112233"))
        assertTrue(ThemeColorResolver.hasPreview())
        ThemeColorResolver.clearPreview()
        assertTrue(!ThemeColorResolver.hasPreview())
    }
    @Test fun moduleAndAnsiOverridesStayInTheirSections() {
        val defaults = Theme.entries.associateWith { ThemeColorResolver.resolve(it, { null }, emptyMap()) }
        val moduleChanges = mapOf(
            Theme.module_button_background_color to "#FFEE1122",
            Theme.module_text_color to "#FF22CC55",
            Theme.module_panel_background_color to "#FF112233",
            Theme.module_header_text_color to "#FF334455"
        )
        Theme.entries.filterNot { it.name.startsWith("module_") }.forEach { role ->
            assertEquals("Module colors leaked into $role", defaults[role],
                ThemeColorResolver.resolve(role, moduleChanges::get, emptyMap()))
        }
        val ansiChanges = Theme.entries.filter { it.name.startsWith("ansi_") }.associateWith { "#FFCC0055" }
        Theme.entries.filterNot { it.name.startsWith("ansi_") }.forEach { role ->
            assertEquals("ANSI colors leaked into $role", defaults[role],
                ThemeColorResolver.resolve(role, ansiChanges::get, emptyMap()))
        }
        assertEquals(0xFFEE1122.toInt(), ThemeColorResolver.resolve(Theme.module_action_background_color, moduleChanges::get, emptyMap()))
        assertEquals(0xFF22CC55.toInt(), ThemeColorResolver.resolve(Theme.module_active_text_color, moduleChanges::get, emptyMap()))
    }

    @Test fun statusAndWorkspaceOverridesHavePredictableParents() {
        val changes = mapOf(
            Theme.battery_text_high to "#FF123456", Theme.battery_text_medium to "#FF123456",
            Theme.battery_text_low to "#FF123456", Theme.terminal_border_color to "#FF223344",
            Theme.workspace_panel_text_color to "#FF445566", Theme.widget_panel_text_color to "#FF778899"
        )
        assertEquals(0xFF4CAF50.toInt(), ThemeColorResolver.resolve(Theme.success_text_color, changes::get, emptyMap()))
        assertEquals(0xFFFFEB3B.toInt(), ThemeColorResolver.resolve(Theme.warning_text_color, changes::get, emptyMap()))
        assertEquals(0xFFFF5722.toInt(), ThemeColorResolver.resolve(Theme.error_text_color, changes::get, emptyMap()))
        assertEquals(0xFF223344.toInt(), ThemeColorResolver.resolve(Theme.accent_color, changes::get, emptyMap()))
        assertEquals(0xFF445566.toInt(), ThemeColorResolver.resolve(Theme.workspace_control_text_color, changes::get, emptyMap()))
        assertEquals(0xFF445566.toInt(), ThemeColorResolver.resolve(Theme.workspace_header_text_color, changes::get, emptyMap()))
        assertEquals(0xFF778899.toInt(), ThemeColorResolver.resolve(Theme.widget_control_text_color, changes::get, emptyMap()))
        assertEquals(0x96112233.toInt(), ThemeColorResolver.resolve(Theme.disabled_text_color,
            mapOf(Theme.button_text_color to "#FF112233")::get, emptyMap()))
    }

    @Test fun fixedDarkeningPreservesTransparentAndTranslucentPanels() {
        for (background in listOf("#00112233", "#40112233")) {
            val values = mapOf(Theme.workspace_panel_background_color to background, Theme.widget_panel_background_color to background)
            for (role in listOf(Theme.workspace_output_background_color, Theme.workspace_input_background_color,
                Theme.widget_row_background_color, Theme.widget_command_background_color)) {
                assertEquals(ThemeColorResolver.parse(background)!! ushr 24,
                    ThemeColorResolver.resolve(role, values::get, emptyMap()) ushr 24)
            }
        }
    }

    @Test fun drawerRailsFollowThePanelWithoutColoringAppRows() {
        val values = mapOf(
            Theme.terminal_window_background_color to "#FF112233",
            Theme.apps_drawer_text_color to "#FF445566",
            Theme.terminal_border_color to "#FF778899"
        )
        for ((role, expected) in listOf(
            Theme.app_drawer_group_background_color to 0xFF112233.toInt(),
            Theme.app_drawer_alphabet_background_color to 0xFF112233.toInt(),
            Theme.app_drawer_group_text_color to 0xFF445566.toInt(),
            Theme.app_drawer_alphabet_text_color to 0xFF445566.toInt(),
            Theme.app_drawer_group_border_color to 0xFF778899.toInt(),
            Theme.app_drawer_alphabet_border_color to 0xFF778899.toInt(),
            Theme.app_drawer_row_background_color to 0
        )) assertEquals(role.name, expected, ThemeColorResolver.resolve(role, values::get, emptyMap()))

        val rows = values + mapOf(Theme.app_drawer_row_background_color to "#FFABCDEF",
            Theme.app_drawer_row_text_color to "#FFABCDEF")
        for (role in listOf(Theme.app_drawer_group_background_color, Theme.app_drawer_group_text_color,
            Theme.app_drawer_alphabet_background_color, Theme.app_drawer_alphabet_text_color)) {
            assertEquals("App-row colors leaked into $role", ThemeColorResolver.resolve(role, values::get, emptyMap()),
                ThemeColorResolver.resolve(role, rows::get, emptyMap()))
        }
    }

    @Test fun drawerRailOverridesAreIndependentAndPreserveSharedHighlights() {
        val pairs = listOf(
            Theme.app_drawer_group_background_color to Theme.app_drawer_alphabet_background_color,
            Theme.app_drawer_group_text_color to Theme.app_drawer_alphabet_text_color,
            Theme.app_drawer_group_border_color to Theme.app_drawer_alphabet_border_color,
            Theme.app_drawer_group_selected_background_color to Theme.app_drawer_alphabet_selected_background_color,
            Theme.app_drawer_group_selected_text_color to Theme.app_drawer_alphabet_selected_text_color,
            Theme.app_drawer_group_selected_border_color to Theme.app_drawer_alphabet_selected_border_color
        )
        val shared = mapOf(
            Theme.app_drawer_selection_background_color to "#FF102030",
            Theme.app_drawer_selection_text_color to "#FF405060",
            Theme.app_drawer_selection_border_color to "#FF708090"
        )
        for ((category, alphabet) in pairs) {
            val before = ThemeColorResolver.resolve(alphabet, shared::get, emptyMap())
            assertEquals(before, ThemeColorResolver.resolve(category, shared::get, emptyMap()))
            for ((changed, untouched) in listOf(category to alphabet, alphabet to category)) {
                val overridden = shared + (changed to "#00000000")
                assertEquals(0, ThemeColorResolver.resolve(changed, overridden::get, emptyMap()))
                assertEquals(before, ThemeColorResolver.resolve(untouched, overridden::get, emptyMap()))
                assertEquals(0x80445566.toInt(), ThemeColorResolver.resolve(changed, overridden::get,
                    mapOf(changed to "#80445566")))
            }
        }
        assertEquals(0xFF102030.toInt(), ThemeColorResolver.resolve(
            Theme.app_drawer_alphabet_selected_background_color, shared::get, emptyMap()))
        assertEquals(0xFF405060.toInt(), ThemeColorResolver.resolve(
            Theme.app_drawer_group_selected_text_color, shared::get, emptyMap()))
        assertEquals(0xFF708090.toInt(), ThemeColorResolver.resolve(
            Theme.app_drawer_alphabet_selected_border_color, shared::get, emptyMap()))
    }

}
