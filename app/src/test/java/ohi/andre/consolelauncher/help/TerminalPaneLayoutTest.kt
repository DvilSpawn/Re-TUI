package ohi.andre.consolelauncher.help

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class TerminalPaneLayoutTest {
    private val root = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "app/src/main").isDirectory }
    private fun source(path: String) = File(root, "app/src/main/$path").readText()
    private fun element(layout: String, id: String): Element {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(root, "app/src/main/res/layout/$layout.xml")).getElementsByTagName("*")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .first { it.getAttribute("android:id").substringAfter('/') == id }
    }

    @Test fun previewLivesBelowPromptWithoutChangingDockRows() {
        val preview = element("calculator_surface", "calculator_preview")
        val input = element("calculator_surface", "calculator_expression")
        assertEquals("@+id/calculator_transcript_scroll", (preview.parentNode.parentNode as Element).getAttribute("android:id"))
        assertEquals("@+id/calculator_transcript_scroll", (input.parentNode.parentNode.parentNode as Element).getAttribute("android:id"))
        assertEquals("2", preview.getAttribute("android:lines"))
        assertEquals("polite", preview.getAttribute("android:accessibilityLiveRegion"))
        val dock = source("java/ohi/andre/consolelauncher/calculator/CalculatorKeypadScrollView.kt")
        assertTrue(dock.contains("prompt.measuredHeight + preview.measuredHeight"))
        val activity = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        assertTrue(activity.contains("main.postDelayed(it, 300)"))
        assertTrue(activity.contains("generation != previewGeneration"))
        assertFalse("Discarding a preview must not kill the variable-owning native process", activity.contains("previewRequest?.cancel()"))
    }

    @Test fun outputAndPromptShareTightContinuousPane() {
        // Side padding keeps text off the inner pane's border; output and prompt still share one pane.
        val pane = element("calculator_surface", "calculator_main_pane")
        assertEquals("8dp", pane.getAttribute("android:paddingStart"))
        assertEquals("0dp", element("calculator_surface", "calculator_result").getAttribute("android:paddingBottom"))
        val helpPane = element("offline_help_surface", "offline_help_main_pane")
        assertEquals("8dp", helpPane.getAttribute("android:paddingStart"))
        val prompt = element("offline_help_surface", "offline_help_prompt")
        assertEquals(helpPane.getAttribute("android:id"), (prompt.parentNode.parentNode as Element).getAttribute("android:id"))
        assertEquals("0dp", element("offline_help_surface", "offline_help_scroll").getAttribute("android:paddingStart"))
        val calculator = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        assertFalse("A trailing transcript newline adds an empty line before the live prompt", calculator.contains("transcript.text = history\n"))
    }

    @Test fun chromeUsesCompactTitleAndRaisedActionsWithoutExtraDockGaps() {
        val title = element("calculator_surface", "calculator_main_tab")
        assertEquals("start|center_vertical", title.getAttribute("android:gravity"))
        assertEquals("", title.getAttribute("android:minWidth"))
        assertEquals("2dp", element("calculator_surface", "calculator_keypad_scroll").getAttribute("android:layout_marginTop"))
        assertEquals("2dp", element("offline_help_surface", "offline_help_keys").getAttribute("android:layout_marginTop"))
        val helpTitle = element("offline_help_surface", "offline_help_header")
        assertEquals("wrap_content", helpTitle.getAttribute("android:layout_width"))
        assertEquals("180dp", helpTitle.getAttribute("android:maxWidth"))
        val panel = source("java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt")
        assertTrue("Window actions reuse raised tabs within accessible targets", panel.contains("InsetDrawable"))
        val activity = source("java/ohi/andre/consolelauncher/help/OfflineHelpActivity.kt")
        assertTrue("Root manual title is already in the chrome", activity.contains("if (category != null) HelpText.title"))
        assertTrue(source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt").contains("InsetDrawable"))
    }

    @Test fun windowActionsAndCalculatorHelpUseExistingTintedIcons() {
        val panel = source("java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt")
        assertFalse(panel.contains("control(\"max\""))
        assertFalse(panel.contains("control(\"reset\""))
        assertFalse(panel.contains("\"float\" else \"max\""))
        assertTrue(panel.contains("R.drawable.ic_toolbar_refresh_24"))
        assertTrue(panel.contains("R.drawable.open_in_full_24"))
        assertTrue(panel.contains("R.drawable.pip_24"))
        val calculator = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        assertTrue(calculator.contains("R.drawable.ic_material_help_24"))
        val icon = source("java/ohi/andre/consolelauncher/tuils/TerminalPaneIcon.kt")
        assertTrue(icon.contains("dpToPx(view.context, 12)"))
        assertTrue(icon.contains("Theme.workspace_control_text_color"))
        assertTrue(icon.contains("TooltipCompat.setTooltipText"))
    }

    @Test fun calculatorDockBudgetIsResolvedDuringMeasurementNotFromPreviousLayout() {
        val activity = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        assertFalse("Previous dock bounds make the first viewport differ from settled mode switches",
            activity.contains("minOf(dock.height"))
        assertFalse("Layout listeners cannot correct an unchanged parent after an inset measure",
            activity.contains("val resizeDock = View.OnLayoutChangeListener"))
        val dock = source("java/ohi/andre/consolelauncher/calculator/CalculatorKeypadScrollView.kt")
        assertTrue(dock.contains("override fun onMeasure"))
        assertTrue(dock.contains("MeasureSpec.getSize(heightMeasureSpec)"))
        assertTrue(dock.contains("prompt.measure"))
        assertTrue(dock.contains("MeasureSpec.EXACTLY"))
        assertEquals("ohi.andre.consolelauncher.calculator.CalculatorKeypadScrollView",
            element("calculator_surface", "calculator_keypad_scroll").tagName)
    }

    @Test fun keypadRowsUseExplicitAlignmentRatherThanTextBaselines() {
        val activity = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        val keyLayout = activity.substringAfter("fun keyLayout(index: Int)").substringBefore("modes.getValue(mode).forEachIndexed")
        assertTrue("Numeric rows must not grow to reconcile evaluate and symbol baselines",
            keyLayout.contains("rowSpec = GridLayout.spec(GridLayout.UNDEFINED, GridLayout.CENTER)"))
        assertTrue("Letter rows must retain their exact split pixel budget",
            keyLayout.contains("rowSpec = GridLayout.spec(row, GridLayout.CENTER)"))
        assertTrue(keyLayout.contains("keyHeight * 5 * (row + 1) / 4 - keyHeight * 5 * row / 4"))
    }

    @Test fun helpKeepsOnlyFourAccessibleCornerHandles() {
        val panel = source("java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt")
        assertFalse("Standalone middle-right handle is removed", panel.contains("Triple(\"width\""))
        assertFalse("Standalone bottom handle is removed", panel.contains("Triple(\"height\""))
        for (kind in listOf("corner", "top-left", "top-right", "bottom-left")) {
            assertTrue("Keep $kind", panel.contains("Triple(\"$kind\""))
        }
        assertTrue(panel.contains("FrameLayout.LayoutParams(dp(48), dp(48)"))
    }

    @Test fun calculatorStartsExpandedWithSafeFloatingGeometryAvailable() {
        val activity = source("java/ohi/andre/consolelauncher/calculator/CalculatorActivity.kt")
        assertTrue("Calculator reuses help's safe-host geometry", activity.contains("HelpWindowPanel(host, root, state,"))
        val panel = source("java/ohi/andre/consolelauncher/help/HelpWindowPanel.kt")
        assertTrue(panel.contains("initiallyMaximized: Boolean = true"))
        assertTrue(panel.contains("restoredFraction(state, \"width\", .9f)"))
        assertTrue(panel.contains("restoredFraction(state, \"height\", .9f)"))
        assertTrue(panel.contains("restoredFraction(state, \"left\", .05f)"))
        assertTrue(panel.contains("restoredFraction(state, \"top\", .05f)"))
        val workspace = element("termux_workspace_page", "termux_workspace_root")
        val calculator = element("calculator_surface", "calculator_overlay")
        for (attribute in listOf("paddingStart", "paddingEnd", "paddingBottom")) {
            assertEquals(workspace.getAttribute("android:$attribute"), calculator.getAttribute("android:$attribute"))
        }
    }
}
