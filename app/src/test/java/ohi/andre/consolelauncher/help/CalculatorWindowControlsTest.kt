package ohi.andre.consolelauncher.help

import ohi.andre.consolelauncher.SourceFiles
import org.junit.Assert.*
import org.junit.Test

class CalculatorWindowControlsTest {

    @Test fun resizeControlsExposeLocalizedTooltips() {
        val panel = SourceFiles.kotlin("HelpWindowPanel.kt").readText()
        assertTrue("Corner targets also need localized tooltips", panel.contains("TooltipCompat.setTooltipText(this, contentDescription)"))
    }

    @Test fun calculatorActionsAreInRuntimeAndTranslatorCatalogs() {
        for (key in listOf("calculator_maximize", "calculator_windowed")) {
            assertTrue(SourceFiles.repo("app/src/main/assets/localization/catalog.json").readText().contains("\"$key\""))
            assertTrue(SourceFiles.repo("language-packs/template/strings.xml").readText().contains("name=\"$key\""))
            java.util.zip.ZipFile(SourceFiles.repo("app/src/main/assets/localization/english-template.zip")).use { zip ->
                assertTrue(zip.getInputStream(zip.getEntry("strings.xml")).bufferedReader().readText().contains("name=\"$key\""))
            }
        }
    }

    @Test fun maximizeAndFloatUseRequestedMaterialAssets() {
        val panel = SourceFiles.kotlin("HelpWindowPanel.kt").readText()
        assertTrue("Float uses pip and maximize uses open-in-full", panel.contains("if (expanded) R.drawable.pip_24 else R.drawable.open_in_full_24"))
        for (name in listOf("pip_24", "open_in_full_24")) {
            assertTrue(SourceFiles.repo("app/src/main/res/drawable/$name.xml").isFile)
        }
    }

    @Test fun sharedHeaderAnchorsAnEquallySpacedActionGroupAtTheEnd() {
        val panel = SourceFiles.kotlin("HelpWindowPanel.kt").readText()
        assertTrue("Actions need a dedicated end anchor", panel.contains("Gravity.END or Gravity.TOP"))
        assertTrue("Every action after the first gets the same gap", panel.contains("marginStart = if (actions.childCount == 0) 0 else dp(10)"))
        assertTrue("Title yields to the complete action group", panel.contains("marginEnd = actions.childCount * dp(48) + (actions.childCount - 1) * dp(10)"))
    }

    @Test fun calculatorHeaderReservesCornersAndKeepsTitleFlexible() {
        val layout = SourceFiles.repo("app/src/main/res/layout/calculator_surface.xml").readText()
        val header = layout.substringAfter("android:id=\"@+id/calculator_header\"")
        assertTrue("Header stays outside 48dp corner targets", header.contains("android:layout_marginStart=\"48dp\" android:layout_marginEnd=\"48dp\""))
        assertTrue("Title yields room to three 48dp actions", header.contains("<FrameLayout android:layout_width=\"match_parent\""))
        assertTrue(header.contains("android:ellipsize=\"end\""))
        assertTrue(SourceFiles.kotlin("CalculatorActivity.kt").readText().contains("maximizeLabel = R.string.calculator_maximize"))
    }

    @Test fun calculatorUsesHelpWindowControllerAndSavesIndependentGeometry() {
        val calc = SourceFiles.kotlin("CalculatorActivity.kt").readText()
        assertTrue("Calculator needs the same draggable/resizable controller as help", calc.contains("HelpWindowPanel(host, root, state,"))
        assertTrue(calc.contains("windowPanel.save(state)"))
        assertTrue(calc.contains("windowPanel.refreshTheme()"))
        assertTrue(calc.contains("statePrefix = \"calculator\""))
        val panel = SourceFiles.kotlin("HelpWindowPanel.kt").readText()
        assertTrue(panel.contains("statePrefix: String = \"help\""))
        assertTrue(panel.contains("tag = \"$" + "statePrefix-window-toggle\""))
        assertTrue(panel.contains("tag = \"$" + "statePrefix-resize-$" + "kind\""))
    }
}
